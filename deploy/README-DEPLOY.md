# PulseProbe 融合服务 — 离线部署手册

## 零、现场条件（已实测）

| 项 | 实测结果 | 对部署的约束 |
|---|---|---|
| 系统 | Debian 11 (bullseye), x86_64, glibc **2.31** | 决定了不能直接搬 Debian 12 的 JDK |
| Java | **宿主机未安装**，无 `/usr/lib/jvm` | 必须自带运行时，或借容器 |
| nginx | **未安装** | 前端由后端进程内置托管，不用反向代理 |
| Docker | 20.10.9 可用，**无 compose** | 用 `docker run`，不依赖 compose |
| 出网 | **完全离线**（GitHub / Docker Hub / Maven / npmmirror 全不可达） | 依赖只能在别处备好再搬 |
| apt | 主源指向 **cdrom**，`apt-get update` 卡死 | 不可 `apt install` |
| 内网仓库 | `docker.cnb.cool` **不可达** | 无法拉取任何新镜像 |
| 已有镜像 | **`docker.cnb.cool/dbxio.com/dbx:latest`**，内含 **OpenJDK 17.0.20**（Debian 12） | 这是可复用的唯一 Java 运行时来源 |
| 资源 | 16 核 / 31 GB / 678 GB 空闲 | 本服务仅需约 300 MB～1 GB |
| 端口 | 80、443、8080 全部空闲 | 用 8080 |
| 传输工具 | `scp`、`sftp` 可用，sftp 子系统已启用 | 由本机推送 |

## 一、方案选型

服务器上没有任何可用的 Java，且离线。有两条路：

| | **方案 A：容器复用已有镜像**（推荐） | 方案 B：裸装自带 JRE |
|---|---|---|
| 运行时来源 | 复用 `zing-dbx` 镜像内的 JDK 17，**零额外下载** | 传输 Adoptium JRE 21 的 Linux 包 |
| **需传输量** | **仅 24 MB（一个 jar）** | 24 MB jar + 52 MB JRE = **76 MB** |
| glibc 风险 | 无（容器内是 Debian 12，自洽） | 需 JRE 兼容 glibc 2.31（Temurin 21 要求 ≥2.17，满足） |
| 运维方式 | `docker run` + `--restart unless-stopped` | systemd 单元 |
| 前置条件 | 需 `zing-dbx` 镜像存在（已确认） | 需成功传入 JRE 包 |

**推荐方案 A**：传输量最小（省 52 MB），且完全避开跨发行版 glibc 风险。

本方案的关键改动：**融合服务的编译目标已从 Java 21 降到 Java 17**，因为服务器上唯一可用的运行时是 JDK 17。已核实代码未使用任何 18+ 独占特性（仅 `switch` 箭头语法，属 Java 14 特性），36 个源文件的 import 全部落在 `java.util` 与 `java.time`。构建产物字节码 `major_version=61`，即 Java 17。

---

## 二、交付物

| 文件 | 大小 | 用途 |
|---|---|---|
| `fusion-backend.jar` | 24,278,083 bytes | 后端 + **已内置前端页面**，单文件运行 |
| `fusion-docker.sh` | 2.9 KB | 方案 A 的启停脚本 |
| `fusion.sh` | 3.5 KB | 方案 B 的启停脚本 |
| `pulseprobe.service` | 0.9 KB | 方案 B 的 systemd 单元 |

jar 的 SHA256（传完在服务器上校验）：

```
13f6bdf0bd2fd9544dcf2e779e2d98c0592fad5a105b25bbe18e6af998de1bb4
```

---

## 三、传输（在本机执行）

```bash
scp fusion-backend.jar   root@199.199.5.151:/tmp/
scp fusion-docker.sh     root@199.199.5.151:/tmp/
```

方案 B 追加：

```bash
scp OpenJDK21U-jre_x64_linux.tar.gz root@199.199.5.151:/tmp/
scp fusion.sh pulseprobe.service    root@199.199.5.151:/tmp/
```

服务器无法出网，**只能由本机推送，不要反向在服务器上 `curl`/`wget`**。

---

## 四、方案 A 安装（容器复用已有镜像）

### 4.1 安装

```bash
# 目录与 jar
mkdir -p /opt/pulseprobe
mv /tmp/fusion-backend.jar /opt/pulseprobe/
mv /tmp/fusion-docker.sh /opt/pulseprobe/
chmod +x /opt/pulseprobe/fusion-docker.sh

# 校验完整性
cd /opt/pulseprobe
sha256sum fusion-backend.jar
# 期望：13f6bdf0bd2fd9544dcf2e779e2d98c0592fad5a105b25bbe18e6af998de1bb4
```

### 4.2 启动

```bash
cd /opt/pulseprobe
./fusion-docker.sh start
```

预期输出：

```
容器已启动，等待就绪...
健康检查通过：http://127.0.0.1:8080
pulseprobe | Up x seconds | 0.0.0.0:8080->8080/tcp
```

### 4.3 验证

```bash
# 页面
curl -s -o /dev/null -w "index http=%{http_code}\n" http://127.0.0.1:8080/
# 健康
curl -s http://127.0.0.1:8080/actuator/health
# 业务接口
curl -s http://127.0.0.1:8080/api/v1/providers | head -c 300
curl -s http://127.0.0.1:8080/api/v1/assets | head -c 300
```

浏览器访问 **http://199.199.5.151:8080**

### 4.4 常用运维

```bash
./fusion-docker.sh status     # 状态 + 健康
./fusion-docker.sh logs       # 跟踪日志
./fusion-docker.sh restart
./fusion-docker.sh stop       # 移除容器

# 容器自带重启策略，宿主重启后自动恢复：
docker inspect pulseprobe --format '{{.HostConfig.RestartPolicy.Name}}'   # 应输出 unless-stopped
```

---

## 五、方案 B 安装（裸装，仅在方案 A 不可用时使用）

```bash
mkdir -p /opt/pulseprobe/logs /opt/pulseprobe/run
mv /tmp/fusion-backend.jar /opt/pulseprobe/

# 解压 JRE（strip-components=1，直接解到 runtime/）
mkdir -p /opt/pulseprobe/runtime
tar -xzf /tmp/OpenJDK21U-jre_x64_linux.tar.gz -C /opt/pulseprobe/runtime --strip-components=1

# 关键验证：glibc 不匹配会在这里暴露
/opt/pulseprobe/runtime/bin/java -version

mv /tmp/fusion.sh /opt/pulseprobe/
chmod +x /opt/pulseprobe/fusion.sh
cd /opt/pulseprobe && ./fusion.sh start
```

`java -version` 应输出 `openjdk version "21.0.x"`。若报 `GLIBC_2.xx not found`，说明 JRE 与 glibc 2.31 不兼容，退回方案 A。

systemd 托管（脚本验证通过后）：

```bash
cp /tmp/pulseprobe.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now pulseprobe
journalctl -u pulseprobe -f
```

**方案 A 与方案 B 不要同时开启**，会争用 8080。

---

## 六、运行参数

| 参数 | 默认 | 调整 |
|---|---|---|
| 端口 | 8080 | 方案 A：`FUSION_PORT=9090 ./fusion-docker.sh start`；方案 B：`FUSION_PORT=9090 ./fusion.sh start` |
| 堆内存 | 256–768 MB | 改脚本里的 `-Xms/-Xmx` |
| 时区 | Asia/Shanghai | 方案 A 已注入 `TZ`；方案 B 用 `-Duser.timezone=Asia/Shanghai` |
| 镜像 | `docker.cnb.cool/dbxio.com/dbx:latest` | 方案 A 可用 `FUSION_IMAGE=... ` 覆盖 |

**端口坑**：Spring 的宽松绑定会把环境变量 `SERVER__PORT` 当作 `server.port`，静默覆盖配置。本机验证时实际踩到（配置 8080 却起在 38075）。两个脚本与 systemd 单元都**显式传** `--server.port`，因此不受影响。若你手敲 `java -jar`，务必带上 `--server.port=8080`。

---

## 七、必须知情的部署边界

这套服务**能跑起来**，但它是「契约骨架 + 合成数据」，不是可用的监控系统。

1. **数据全为合成**。`MockHertzBeatAdapter` / `MockSkyWalkingAdapter` 按固定种子生成指标，页面顶部显示"合成数据、上游能力 unverified"。不具备真实监控能力。
2. **无持久化**。资产与关系存在内存 `AssetRegistry` 中，重启回到种子状态。无 PostgreSQL。
3. **无认证授权**。所有接口完全开放。任何能访问 8080 的人都可读取全部内容。**不要暴露到不可信网络。**
4. **契约 14 路径只实现 5 条**。`traces`、`incidents`、`reports`、`ingest`、`diagnosis` 未实现。前端只有一个总览页，无图表层。
5. **与业务同机**。`zing-mysql`、`zing-sci-*` 正在此机能跑。本服务占用约 300 MB～1 GB，压测时注意不要与业务争抢。原设计建议独立监控机。

**定位应为：内部技术验证 / 界面与接口走查。** 若目标是真实业务监控，需先完成设计文档的阶段 0（上游兼容性验证），并补齐持久化与认证。

---

## 八、故障排查

| 现象 | 原因 | 处理 |
|---|---|---|
| `找不到 Java 运行时` | runtime 未解压或路径不对（方案 B） | 确认 `/opt/pulseprobe/runtime/bin/java` 存在且可执行 |
| `GLIBC_2.xx not found` | JRE 与 glibc 2.31 不兼容（方案 B） | 改用方案 A |
| `容器已退出` | 镜像无 `/usr/bin/java` 或参数错误 | `docker logs pulseprobe` 看启动异常 |
| `invalid reference format`（方案 A） | 镜像名含非法字符 | 确认 `FUSION_IMAGE` 与 `docker images` 中的名字完全一致 |
| 页面 404，接口正常 | 前端未打进 jar | 本机重新构建（见第九节） |
| 端口被占用 | 已有进程或环境变量覆盖 | `ss -tlnp \| grep 8080` 定位 |
| 本机 curl 通、外部不通 | 上游网络设备未放行 | 本机 iptables 已实测 ACCEPT，需查安全组 |

---

## 九、本机重新构建

```bash
cd fusion-frontend && npm install && npm run build          # 产出 dist/
cd ../fusion-backend && mvn -o clean package -DskipTests    # 自动把 dist 打进 jar
```

`mvn package` 已配置在 `prepare-package` 阶段把 `fusion-frontend/dist` 复制进
`BOOT-INF/classes/static/`。只打纯 API 包（不含前端）加 `-Dskip.frontend=true`。

编译目标为 **Java 17**（`pom.xml` 的 `<java.version>17</java.version>`）。本机用
JDK 21 交叉编译，产物字节码 `major_version=61`，可在 JDK 17 及以上运行。

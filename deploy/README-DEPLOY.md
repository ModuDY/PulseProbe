# PulseProbe 融合服务 — 离线部署手册（私有 Java 21 方案）

## 零、现场条件（已实测）

| 项 | 实测结果 | 对部署的约束 |
|---|---|---|
| 系统 | Debian 11 (bullseye), x86_64, glibc **2.31** | 足够运行 Temurin 21（最高需 GLIBC_2.15） |
| 宿主机 Java | **未安装**，无 `/usr/lib/jvm`，`/usr/bin/java` 不存在 | 本服务自带私有 JRE，不触碰系统 |
| nginx | **未安装** | 前端由后端进程内置托管 |
| Docker | 20.10.9 可用，无 compose | 本方案**不使用容器**，避免影响现有容器 |
| 出网 | **完全离线** | 依赖只能由本机推送 |
| apt | 主源指向 cdrom，`apt-get update` 卡死 | 不可 `apt install`，本方案不需要 |
| 资源 | 16 核 / 31 GB / 678 GB 空闲 | 本服务仅需约 300 MB |
| 端口 | 80、443、8080 空闲 | 用 8080 |
| 传输工具 | `scp`、`sftp` 可用 | 由本机推送 |

## 一、核心设计：运行时完全隔离

服务器上已有两个不同版本的 Java 在跑别人的项目：

| 使用者 | 运行时 | 位置 |
|---|---|---|
| `zing-sci-server`（业务） | **JDK 8** (1.8.0_202) | 容器内 `/opt/jdk` |
| `zing-dbx` | **JDK 17** (17.0.20) | 容器内 `/usr/lib/jvm`（Debian 12） |
| **PulseProbe（本服务）** | **JRE 21** (21.0.13) | **`/opt/pulseprobe/runtime`（私有）** |

**隔离保证**：`install-private-jre21.sh` 刻意不做以下任何事：

- × 不修改 `/usr/bin/java`、`/usr/lib/jvm`、`update-alternatives`
- × 不修改 `JAVA_HOME`、`PATH`（不写 `/etc/profile`、`/etc/environment`）
- × 不安装 Debian 包
- × 不启动/修改任何容器
- × 不占用镜像，不下载任何东西

`fusion.sh` 的运行时解析顺序被收紧为：**优先且默认只使用 `runtime/bin/java`**。
未安装私有运行时会**直接报错退出**，而不是悄悄回退到系统 Java——这是刻意的，
避免哪天系统装了别的 JDK 后本服务被静默换成错误版本。

若确实需要临时借用系统 Java（例如排障），显式设置：

```bash
FUSION_ALLOW_SYSTEM_JAVA=1 FUSION_JAVA=/path/to/java ./fusion.sh start
```

## 二、交付物

| 文件 | 大小 | 用途 |
|---|---|---|
| `fusion-backend.jar` | 24 MB | 后端 + 已内置前端页面 |
| `OpenJDK21U-jre_x64_linux.tar.gz` | 50 MB | Temurin **21.0.13+9** 私有 JRE |
| `install-private-jre21.sh` | 4 KB | 隔离安装脚本（含 `--check` 干跑模式） |
| `fusion.sh` | 3.6 KB | 启停脚本 |
| `pulseprobe.service` | 0.9 KB | systemd 单元（可选） |
| `SHA256SUMS` | — | 校验清单 |

**前端已内置**：`mvn package` 在 `prepare-package` 阶段把 `fusion-frontend/dist`
复制进 `BOOT-INF/classes/static/`，因此单个 jar 同时提供页面与接口，不需要 nginx。

---

## 三、传输（在本机执行）

```bash
cd <交付包目录>
scp fusion-backend.jar OpenJDK21U-jre_x64_linux.tar.gz \
    install-private-jre21.sh fusion.sh pulseprobe.service SHA256SUMS \
    root@199.199.5.151:/tmp/
```

服务器无法出网，**只能由本机推送**。

---

## 四、安装（在服务器上执行）

### 4.1 先干跑检查（不改动任何东西）

```bash
cd /tmp && sh install-private-jre21.sh --check
```

会打印系统信息、**当前已有的 Java 分布**、目录与包就位情况。确认无误再继续。

### 4.2 校验并放置文件

```bash
cd /tmp && sha256sum -c SHA256SUMS

mkdir -p /opt/pulseprobe
mv /tmp/fusion-backend.jar /tmp/install-private-jre21.sh /tmp/fusion.sh /tmp/pulseprobe.service /opt/pulseprobe/
chmod +x /opt/pulseprobe/*.sh
```

### 4.3 执行隔离安装

```bash
cd /opt/pulseprobe
sh install-private-jre21.sh
```

脚本会依次：校验 gzip 完整性 → 解压到 `runtime/` → 验证 `java -version` 为 21
→ **打印系统 Java 是否被改动**（预期为"未被改动"）→ 创建 `logs/`、`run/`。

预期输出关键片段：

```
== 解压私有 JRE 21 到 /opt/pulseprobe/runtime ==
解压完成
== 验证私有运行时 ==
openjdk version "21.0.13" 2026-10-20 LTS
私有 JRE 版本 21 校验通过
== 确认未污染系统环境 ==
/usr/bin/java 仍不存在，系统 Java 未被改动（预期）
```

**若 `/usr/bin/java` 一行出现"符号链接"警告**，说明系统原本就有 Java；这不影响本服务（它用绝对路径），但要确认那个链接不是你刚改的。

### 4.4 启动

```bash
cd /opt/pulseprobe
./fusion.sh start
```

预期：

```
使用 JRE: /opt/pulseprobe/runtime/bin/java
  openjdk version "21.0.13" 2026-10-20 LTS
监听端口: 8080
已启动，PID=xxxx
健康检查通过：http://127.0.0.1:8080/actuator/health
```

### 4.5 验证

```bash
curl -s -o /dev/null -w "页面 http=%{http_code}\n" http://127.0.0.1:8080/
curl -s http://127.0.0.1:8080/actuator/health
curl -s http://127.0.0.1:8080/api/v1/providers | head -c 300
curl -s http://127.0.0.1:8080/api/v1/assets    | head -c 300
```

浏览器打开 **http://199.199.5.151:8080**

---

## 五、确认隔离生效（安装后自查）

```bash
# 1) 私有运行时是 21
/opt/pulseprobe/runtime/bin/java -version

# 2) 系统 Java 未被改动（应仍为不存在，或保持原状）
ls -l /usr/bin/java 2>/dev/null || echo "系统未安装 java（未被本服务改动）"
echo "JAVA_HOME=${JAVA_HOME:-<未设置>}"

# 3) 本服务进程确实用的是私有路径
PID=$(cat /opt/pulseprobe/run/fusion-backend.pid)
ls -l /proc/$PID/exe 2>/dev/null
# 期望显示 -> /opt/pulseprobe/runtime/bin/java

# 4) 现有业务容器未受影响
docker ps --format '{{.Names}} | {{.Status}}'
```

第 3 条是关键证据：`/proc/<pid>/exe` 指向 `runtime/bin/java`，即证明进程用的是私有运行时。

---

## 六、可选：systemd 开机自启

```bash
./fusion.sh stop
cp /opt/pulseprobe/pulseprobe.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now pulseprobe
systemctl status pulseprobe --no-pager
journalctl -u pulseprobe -f
```

systemd 单元同样以 **绝对路径** `/opt/pulseprobe/runtime/bin/java` 启动，
不设 `Environment=JAVA_HOME`，因此与系统环境无关。

**脚本方式与 systemd 方式不要同时启用**，会争用 8080。

---

## 七、运行参数

| 参数 | 默认 | 调整 |
|---|---|---|
| 端口 | 8080 | `FUSION_PORT=9090 ./fusion.sh start` |
| 堆内存 | 256–768 MB | 改 `fusion.sh` 或 unit 里的 `-Xms/-Xmx` |
| 时区 | 继承系统 | 加 `-Duser.timezone=Asia/Shanghai` |
| 运行时 | `runtime/bin/java` | `FUSION_JAVA=/path ./fusion.sh start` |
| 允许系统 Java | 禁止 | `FUSION_ALLOW_SYSTEM_JAVA=1` |

**端口坑**：Spring 的宽松绑定会把环境变量 `SERVER__PORT` 当作 `server.port`
并静默覆盖配置。本机验证时实际踩到（配置 8080 却起在 38075）。脚本与 systemd
单元都显式传 `--server.port`，不受影响。若手敲 `java -jar`，务必带上 `--server.port=8080`。

---

## 八、必须知情的部署边界

这套服务**能跑起来**，但它是「契约骨架 + 合成数据」，不是可用的监控系统。

1. **数据全为合成**。`MockHertzBeatAdapter` / `MockSkyWalkingAdapter` 按固定种子生成，
   页面顶部显示"合成数据、上游能力 unverified"。不具备真实监控能力。
2. **无持久化**。资产与关系在内存 `AssetRegistry`，重启回到种子状态。无 PostgreSQL。
3. **无认证授权**。所有接口开放。**不要暴露到不可信网络。**
4. **契约 14 路径只实现 5 条**。`traces`、`incidents`、`reports`、`ingest`、`diagnosis`
   未实现。前端只有一个总览页，无图表层。
5. **与业务同机**。`zing-mysql`、`zing-sci-*` 在此机运行。本服务约占 300 MB，
   压测时注意不与业务争抢。原设计建议独立监控机。

**定位应为：内部技术验证 / 界面与接口走查。**

---

## 九、故障排查

| 现象 | 原因 | 处理 |
|---|---|---|
| `未找到私有 Java 运行时` | 未执行 4.3 安装步骤 | 执行 `sh install-private-jre21.sh` |
| `GLIBC_2.xx not found` | 极少见，Temurin 21 只需 2.15 | 确认包是官方 Adoptium 构建 |
| `解压后未找到 runtime/bin/java` | tar 包结构异常 | 确认传输完整（`gzip -t` 通过） |
| `私有运行时版本不是 21` | 包放错了 | 检查 `JRE_TGZ` 指向的文件 |
| 页面 404，接口正常 | 前端未打进 jar | 本机重新构建（见第十节） |
| 端口被占用 | 已有进程或环境变量覆盖 | `ss -tlnp \| grep 8080` |
| 启动即退出 | 内存不足/参数错误 | 看 `logs/fusion-backend.out` 末尾 |

---

## 十、本机重新构建

```bash
cd fusion-frontend && npm install && npm run build          # 产出 dist/
cd ../fusion-backend && mvn -o clean package -DskipTests    # 自动把 dist 打进 jar
```

- 编译目标 **Java 21**（`<java.version>21</java.version>`），产物字节码 `major_version=65`
- 只打纯 API 包（不含前端）：加 `-Dskip.frontend=true`
- 构建后 SHA256 会变，需同步更新 `SHA256SUMS`

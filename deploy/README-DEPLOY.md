# PulseProbe 融合服务 — 离线部署手册

> 交付包：`pulseprobe-1.0.0.tar.gz`（约 70 MB，单文件）
> 目标机：Debian 11 / x86_64 / glibc 2.31 / 完全离线 / 已装 JDK8+JDK17 的其他项目在跑

---

## 一、这个方案解决什么问题

服务器上已有两个**不同版本**的 Java 在跑别人的项目，且**宿主机本身没有 Java**：

| 使用者 | 运行时 | 位置 |
|---|---|---|
| `zing-sci-server`（业务） | JDK 8 (1.8.0_202) | 容器内 `/opt/jdk` |
| `zing-dbx` | JDK 17 (17.0.20) | 容器内 `/usr/lib/jvm`（Debian 12） |
| **PulseProbe（本服务）** | **JRE 21 (21.0.13)** | **`/opt/pulseprobe/runtime`（私有）** |

因此本服务**自带一套 JRE 21**，只用绝对路径调用，**不碰系统 Java**：

- × 不修改 `/usr/bin/java`、`/usr/lib/jvm`、`update-alternatives`
- × 不修改 `JAVA_HOME`、`PATH`（不写 `/etc/profile`、`/etc/environment`）
- × 不安装 Debian 包（apt 主源指向 cdrom，本来就不可用）
- × 不启动/修改任何容器，不占用镜像，不下载任何东西

`fusion.sh` 的运行时解析顺序被**刻意收紧**：只用 `runtime/bin/java`，未安装时**直接报错退出**，
不会悄悄回退到系统 Java（避免哪天系统装了别的 JDK 后本服务被静默换成错误版本）。

---

## 二、包内文件

| 文件 | 大小 | 用途 |
|---|---|---|
| `fusion-backend.jar` | 24 MB | 后端 + **已内置前端页面** |
| `OpenJDK21U-jre_x64_linux.tar.gz` | 50 MB | Temurin **21.0.13+9** 私有 JRE |
| `install-all.sh` | 3.8 KB | **一键安装**（清点 → 校验 → 落盘 → 装 JRE） |
| `install-private-jre21.sh` | 7 KB | 私有 JRE 安装（含 `--check` 干跑） |
| `fusion.sh` | 4.2 KB | 启停脚本（start/stop/restart/status） |
| `pulseprobe.service` | 0.9 KB | systemd 单元（可选开机自启） |
| `SHA256SUMS` | — | 校验清单 |
| `README-DEPLOY.md` | — | 本文档 |

**前端已内置**：构建时 `mvn package` 在 `prepare-package` 阶段把 `fusion-frontend/dist`
复制进 `BOOT-INF/classes/static/`，所以**单个 jar 同时提供页面和接口，不需要 nginx**。

---

## 三、传输（在本机执行）

服务器完全离线，**只能由本机推送**。

```bash
cd <本机交付包目录>
scp pulseprobe-1.0.0.tar.gz root@199.199.5.151:/tmp/
```

约 70 MB，视链路 1~5 分钟。

---

## 四、安装（在服务器上执行）

### 4.1 解包

```bash
cd /tmp
tar -xzf pulseprobe-1.0.0.tar.gz
cd /tmp/pulseprobe
ls -l
```

应看到 8 个文件，其中 3 个 `.sh` 带回 `rwx` 权限（包内已保留执行位）。

### 4.2 干跑检查（不改动任何东西）

```bash
sh install-all.sh --check
```

输出会先做**逐文件 SHA256 校验**，再打印目标目录、磁盘、当前用户、8080 占用情况。
确认无误再继续。

### 4.3 正式安装

```bash
sh install-all.sh
```

一键完成：校验完整性 → 创建 `/opt/pulseprobe` → 复制 jar 与脚本 → 调用私有 JRE 安装
→ 验证 `java -version` 为 21 → 打印系统 Java 是否被改动（预期"未被改动"）。

预期输出关键片段：

```
[信息] 全部文件校验通过
[信息] == 创建 /opt/pulseprobe ==
[信息] == 安装私有 JRE 21（隔离，仅落 /opt/pulseprobe/runtime） ==
[信息] 压缩包完整
[信息] 解压完成
== 验证私有运行时 ==
openjdk version "21.0.13" 2026-10-20 LTS
私有 JRE 版本 21 校验通过
== 确认未污染系统环境 ==
/usr/bin/java 仍不存在，系统 Java 未被改动（预期）
[信息] ============ 安装完成 ============
```

> **若解压阶段出现 `legal/... Cannot create symlink` 的警告**，属正常现象（该 JRE 包里
> `legal/` 下有 145 个相对符号链接，某些文件系统下创建失败），**不影响运行**。
> 脚本以 `runtime/bin/java` 和 `lib/server/libjvm.so` 是否就位为成功判据，会自动继续。

安装后占用约 **182 MB**（jar 24 MB + runtime 159 MB）。

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

首次启动约 3 秒就绪（脚本最多等 60 秒）。

---

## 五、验证

### 5.1 基础连通性

```bash
curl -s -o /dev/null -w "健康 %{http_code}\n" http://127.0.0.1:8080/actuator/health
curl -s -o /dev/null -w "页面 %{http_code}\n" http://127.0.0.1:8080/
```

浏览器打开 **http://199.199.5.151:8080**

### 5.2 五个已实现接口（需带正确参数）

`overview` 有 4 个**必填**参数，`relations` 挂在资产子路径下。直接裸访问会返回
`500 服务内部错误`，那不是服务坏了，是参数缺失。

```bash
SITE=11111111-1111-4111-8111-111111111111
AID=22222222-2222-4222-8222-222222222222

curl -s http://127.0.0.1:8080/api/v1/providers | head -c 300; echo
curl -s http://127.0.0.1:8080/api/v1/assets    | head -c 300; echo

curl -s "http://127.0.0.1:8080/api/v1/assets/$AID/relations?at=2026-11-07T05:00:00Z" | head -c 300; echo

curl -s "http://127.0.0.1:8080/api/v1/overview?siteId=$SITE&environment=pilot&start=2026-11-07T04:00:00Z&end=2026-11-07T05:00:00Z" | head -c 300; echo
```

全部应返回 `200` 与 JSON 正文。

---

## 六、确认隔离真的生效（关键自查）

```bash
# 1) 私有运行时确实是 21
/opt/pulseprobe/runtime/bin/java -version

# 2) 系统 Java 未被改动
ls -l /usr/bin/java 2>/dev/null || echo "系统未安装 java（未被本服务改动）"
echo "JAVA_HOME=${JAVA_HOME:-<未设置>}"

# 3) 进程确实用私有运行时 ← 最硬的证据
PID=$(cat /opt/pulseprobe/run/fusion-backend.pid)
ls -l /proc/$PID/exe
# 期望: -> /opt/pulseprobe/runtime/bin/java

# 4) 别人的容器都还活着
docker ps --format '{{.Names}} | {{.Status}}'
```

第 3 条最关键：`/proc/<pid>/exe` 指向 `runtime/bin/java`，即证明进程运行在私有 JRE 里，
与系统及容器的 JDK 完全无关。

---

## 七、日常运维

```bash
cd /opt/pulseprobe
./fusion.sh status      # 状态 + 健康检查
./fusion.sh restart     # 重启
./fusion.sh stop        # 停止
tail -f logs/fusion-backend.out   # 实时日志
```

### 可选：开机自启

```bash
cd /opt/pulseprobe
./fusion.sh stop                                    # 先停，避免抢 8080
cp pulseprobe.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now pulseprobe
systemctl status pulseprobe --no-pager
journalctl -u pulseprobe -f
```

systemd 单元同样用**绝对路径** `/opt/pulseprobe/runtime/bin/java`，不设 `JAVA_HOME`。

> **两种方式不要同时启用**，会争用 8080。

### 运行参数

| 参数 | 默认 | 调整方式 |
|---|---|---|
| 端口 | 8080 | `FUSION_PORT=9090 ./fusion.sh start` |
| 堆内存 | 256–768 MB | 改 `fusion.sh` 或 unit 的 `-Xms/-Xmx` |
| 时区 | 继承系统 | 加 `-Duser.timezone=Asia/Shanghai` |
| 运行时 | `runtime/bin/java` | `FUSION_JAVA=/path ./fusion.sh start` |
| 借用系统 Java | 禁止 | `FUSION_ALLOW_SYSTEM_JAVA=1` |

> **端口坑（已实测踩到）**：Spring 的宽松绑定会把环境变量 `SERVER__PORT` 当成
> `server.port` 并静默覆盖配置。脚本与 unit 都显式传 `--server.port`，不受影响；
> 若手敲 `java -jar`，**务必带上 `--server.port=8080`**。

---

## 八、故障排查

| 现象 | 原因 | 处理 |
|---|---|---|
| `[错误] 需要 root 权限` | 非 root 执行 | 用 root 或 `sudo sh install-all.sh` |
| `校验失败` | 传输中断 | 重新传整个 tar.gz，再解包 |
| `未找到私有 Java 运行时` | 未执行 4.3 | 执行 `sh install-all.sh` |
| `解压后未找到 runtime/bin/java` | 包结构异常 | 确认 `gzip -t` 通过 |
| `私有运行时版本不是 21` | 放错包 | 检查 `JRE_TGZ` 指向的文件 |
| 解压时 `Cannot create symlink` | legal/ 符号链接受限 | **可忽略**，不影响运行 |
| `GLIBC_2.xx not found` | 极少见（Temurin 21 只需 2.15） | 确认是官方 Adoptium 构建 |
| 页面 404、接口正常 | 前端未打进 jar | 本机重新构建（见第九节） |
| `500 服务内部错误` | `overview`/`relations` 参数缺失 | 见 5.2，带齐参数 |
| 端口被占用 | 已有进程或 `SERVER__PORT` | `ss -tlnp \| grep 8080` |
| 启动即退出 | 内存不足/参数错误 | 看 `logs/fusion-backend.out` 末尾 |

---

## 九、本机重新构建

```bash
cd fusion-frontend && npm install && npm run build            # 产出 dist/
cd ../fusion-backend && mvn -o clean package -DskipTests      # 自动把 dist 打进 jar
```

- 编译目标 **Java 21**（`<java.version>21</java.version>`），产物字节码 `major_version=65`
- 只打纯 API 包（不含前端）：加 `-Dskip.frontend=true`
- 产物 `target/fusion-backend-0.1.0-SNAPSHOT.jar` 改名为 `fusion-backend.jar`
- **重新构建后 SHA256 会变，必须重新生成 `SHA256SUMS` 并重新打包**

```bash
cd <包目录>
sha256sum fusion-backend.jar OpenJDK21U-jre_x64_linux.tar.gz \
  install-all.sh install-private-jre21.sh fusion.sh pulseprobe.service README-DEPLOY.md \
  > SHA256SUMS
cd .. && tar --owner=0 --group=0 --numeric-owner -cf - pulseprobe | gzip -9 > pulseprobe-1.0.0.tar.gz
```

---

## 十、必须知情的部署边界

这套服务**能跑起来**，但它是「契约骨架 + 合成数据」，**不是可用的监控系统**。

1. **数据全为合成**。`MockHertzBeatAdapter` / `MockSkyWalkingAdapter` 按固定种子生成，
   页面显示"合成数据、上游能力 unverified"。**不具备真实监控能力。**
2. **无持久化**。资产与关系在内存 `AssetRegistry`，重启回到种子状态，无 PostgreSQL。
3. **无认证授权**。所有接口开放。**不要暴露到不可信网络。**
4. **契约 14 路径只实现 5 条**。`traces`、`incidents`、`reports`、`ingest`、`diagnosis`
   未实现；前端只有一个总览页，无图表层。
5. **与业务同机**。`zing-mysql`、`zing-sci-*` 在此机运行。本服务约占 180 MB 磁盘、
   300 MB 内存，压测时注意不与业务争抢。

**定位：内部技术验证 / 界面与接口走查。**

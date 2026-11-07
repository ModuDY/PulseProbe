# HertzBeat 完整版 — 离线部署手册

> 交付包：`hertzbeat-2.0.0.tar.gz`（约 400 MB）
> **就地安装**：解压在哪就装在哪，不创建 `/opt/hertzbeat`
> 目标机：Debian 11 / x86_64 / glibc 2.31 / 完全离线

---

## 一、这次的构建说明

这是**完整版 Apache HertzBeat**，不是之前那个只有一页的融合服务骨架。

| 项目 | 说明 |
|---|---|
| 后端 | 30 个 Maven 模块全部编译通过（`BUILD SUCCESS`） |
| 前端 | `web-app` 完整构建，**282 个前端文件**，19 个功能模块 |
| 监控模板 | **141 个** `define/` 模板（MySQL、Redis、Kafka、K8s 等） |
| 依赖库 | **471 个** jar |
| Java | **JDK 25**（Spring Boot 4.0.3 要求，JRE 21 不够） |
| 数据库 | **H2 嵌入式**，无需装 PostgreSQL/MySQL |
| 时序库 | **DuckDB 嵌入式**，无需装 TDengine/VictoriaMetrics |

**关键点：没有任何外部数据库依赖。** H2 和 DuckDB 都是嵌入式的，首次启动自动建库。

---

## 二、目录约定

```
你解压的目录/hertzbeat/     ← 程序目录，就地安装
├── apache-hertzbeat-2.0.0.jar   主程序（164 KB，很薄）
├── lib/         471 个依赖 jar
├── dist/        282 个前端文件（完整界面）
├── define/      141 个监控模板
├── config/      application.yml、logback、sureness
├── bin/         官方启停脚本
├── jdk25/       ★ 私有 JDK 25（只在这里，不碰系统 Java）
├── install.sh   ★ 安装脚本
│   —— 安装后新增，全在本目录内 ——
├── data/        H2 数据库 + DuckDB 时序库
├── logs/        运行日志
├── run/         pid 文件
├── hb.sh        ★ 启停脚本（自动生成）
└── hertzbeat.service  按实际路径生成
```

**不会创建 `/opt/hertzbeat`，不会往别处写文件。** 删掉这个目录即彻底卸载。

---

## 三、服务器操作

### 3.1 传包

```bash
# 本机
scp hertzbeat-2.0.0.tar.gz root@199.199.5.151:/zing/
```

### 3.2 解压

```bash
cd /zing
tar -xzf hertzbeat-2.0.0.tar.gz
cd /zing/hertzbeat
ls -l
```

### 3.3 先停掉旧的融合服务（端口 8080）

你之前选的是「替换」，所以先停掉它：

```bash
cd /zing/pulseprobe && ./fusion.sh stop
```

暂时不用删目录，等 HertzBeat 验证通过再清理。

### 3.4 检查

```bash
cd /zing/hertzbeat
sh install.sh --check
```

输出会打印 jar 名、lib 数量、前端文件数、JDK 路径、1157 端口占用情况。

### 3.5 安装

```bash
sh install.sh
```

会做：清点 → 建 `data/logs/run` → 校验私有 JDK 25 → 确认系统 Java 未被改动 →
生成 `hb.sh` 与 `hertzbeat.service`。

预期关键片段：

```
[信息] jdk25 已随包提供，无需解压
  openjdk version "25.0.5" 2026-10-20 LTS
[信息]   私有 JDK 25 校验通过
[信息]   /usr/bin/java 仍不存在，系统 Java 未被改动（预期）
[信息] ============ 安装完成 ============
```

### 3.6 启动

```bash
cd /zing/hertzbeat
./hb.sh start
```

**首次启动较慢**（要建 H2 库 + DuckDB 文件），脚本最多等 240 秒。

预期：

```
使用 JDK: /zing/hertzbeat/jdk25/bin/java
  openjdk version "25.0.5" 2026-10-20 LTS
监听端口: 1157
已启动，PID=xxxx
健康检查通过：http://127.0.0.1:1157/api/setup/status
```

### 3.7 访问

浏览器打开 **http://199.199.5.151:1157**

首次进入会自动跳转到 **setup 引导页**，完成初始化配置（管理员账号等）。
`/api/setup/status` 返回的 `phase` 会告诉你当前阶段：

- `configuration_required` → 需要配置
- `administrator_required` → 需要创建管理员
- `complete` → 已完成

---

## 四、验证

```bash
# 1) 进程用的是私有 JDK 25 ← 最硬的证据
PID=$(cat /zing/hertzbeat/run/hertzbeat.pid)
ls -l /proc/$PID/exe
# 期望: -> /zing/hertzbeat/jdk25/bin/java

# 2) setup 状态
curl -s http://127.0.0.1:1157/api/setup/status

# 3) 前端页面
curl -s -o /dev/null -w "页面 %{http_code}\n" http://127.0.0.1:1157/

# 4) 别人的容器都还活着
docker ps --format '{{.Names}} | {{.Status}}'

# 5) 系统 Java 未被改动
ls -l /usr/bin/java 2>/dev/null || echo "系统未安装 java（未被改动）"
```

---

## 五、日常运维

```bash
cd /zing/hertzbeat
./hb.sh status     # 状态 + setup 检查
./hb.sh restart    # 重启
./hb.sh stop       # 停止
tail -f logs/startup.out   # 实时日志
```

### 开机自启（可选）

```bash
cd /zing/hertzbeat
./hb.sh stop                                    # 先停，避免抢 1157
cp hertzbeat.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now hertzbeat
systemctl status hertzbeat --no-pager
journalctl -u hertzbeat -f
```

> 与 `hb.sh` **二选一**，不要同时启用。

### 参数

| 参数 | 默认 | 调整 |
|---|---|---|
| 端口 | 1157 | `HB_PORT=9091 ./hb.sh start` |
| 堆内存 | 512–1536 MB | 改 `hb.sh` 或 unit 的 `-Xms/-Xmx` |
| 数据目录 | `./data` | 改 `config/application.yml` |

**内存建议**：31GB 可用内存，默认 1.5G 堆够用。如果要监控大量目标，可调到 `-Xmx3072m`。

---

## 六、必须知道的事

1. **首次启动会引导 setup**，不是直接进主界面。这是 HertzBeat 的正常设计。
2. **H2 + DuckDB 都是文件型数据库**，数据在 `data/` 下。备份就是备份这个目录。
3. **改动配置需重启**：`config/application.yml` 改完要 `./hb.sh restart`。
4. **之前融合服务的 8080 要停掉**，否则两套都占资源。确认 HertzBeat 可用后再清理 `/zing/pulseprobe`。
5. **磁盘占用**：解压后约 662 MB，运行后 `data/` 会随监控数据增长。DuckDB 默认保留 90 天，见 `application.yml` 的 `expire-time`。

---

## 七、故障排查

| 现象 | 原因 | 处理 |
|---|---|---|
| `未找到主 jar` | 包不完整 | 重新传包 |
| `jdk25/bin/java` 缺失 | 包不完整 | 重新传包 |
| `私有 JDK 版本不是 25` | 包被换过 | 确认 `jdk25/release` 里 `JAVA_VERSION="25.0.5"` |
| 启动很慢（>240s） | 首次建库 | 看 `logs/startup.out`，通常 1-3 分钟 |
| 启动即退出 | 内存不足/端口占用 | 看 `logs/startup.out` 末尾 |
| 页面 404 | 前端未打进包 | 检查 `dist/index.html` 是否存在 |
| `Port 1157 already in use` | 端口占用 | `ss -tlnp \| grep 1157` |
| setup 页打不开 | 未就绪 | 等健康检查通过再访问 |

---

## 八、与旧包的关系

| 包 | 端口 | 状态 |
|---|---|---|
| `pulseprobe-1.0.0.tar.gz`（融合服务） | 8080 | **按你的选择，停用** |
| `hertzbeat-2.0.0.tar.gz`（本包，完整版） | 1157 | **启用** |

两套可以共存（端口不同），但你说要替换，所以装好后停掉 8080 那套即可。

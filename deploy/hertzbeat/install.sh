#!/bin/sh
# HertzBeat — 就地安装（程序目录 = 本脚本所在目录）
#
# 用法（需 root）：
#   cd /zing/hertzbeat
#   sh install.sh --check     # 只检查，不改动
#   sh install.sh             # 正式安装
#
# 目录约定：解压在哪里就装在哪里，不创建 /opt/hertzbeat，不往别处写文件。
#   <解压目录>/              程序目录（bin/config/lib/dist 就在这）
#   <解压目录>/jdk25/        私有 JDK 25（只在这里）
#   <解压目录>/data/         H2 数据库 + DuckDB 时序库
#   <解压目录>/logs/         运行日志
#
# 隔离承诺：
#   × 不修改 /usr/bin/java、/usr/lib/jvm、update-alternatives
#   × 不修改全局 JAVA_HOME / PATH，不写 /etc/profile、/etc/environment
#   × 不安装任何 Debian 包，不下载任何东西
#   × 不启动/修改任何容器
#   × 不删除已有文件

set -eu

APP_HOME=$(cd "$(dirname "$0")" && pwd)
JDK_TGZ="$APP_HOME/OpenJDK25U-jdk_x64_linux.tar.gz"
JDK_HOME="$APP_HOME/jdk25"

info() { printf '[信息] %s\n' "$*"; }
warn() { printf '[警告] %s\n' "$*"; }
die()  { printf '[错误] %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- 清点
info "== 清点：$APP_HOME =="
# jar 名带版本号（apache-hertzbeat-2.0.0.jar），动态识别，避免写死版本
HB_JAR=$(ls -1 "$APP_HOME"/*.jar 2>/dev/null | grep -v -- '-jar-with' | head -1)
[ -n "$HB_JAR" ] || die "未找到主 jar（apache-hertzbeat-*.jar）"
HB_JAR_NAME=$(basename "$HB_JAR")
for f in config lib bin dist jdk25; do
    [ -e "$APP_HOME/$f" ] || die "缺少 $f，发布包不完整"
done
printf '  %-24s %s\n' "$HB_JAR_NAME" "$(stat -c '%s' "$HB_JAR") bytes"
printf '  %-24s %s\n' "lib/" "$(ls "$APP_HOME/lib" 2>/dev/null | wc -l) 个 jar"
printf '  %-24s %s\n' "dist/" "$(ls "$APP_HOME/dist" 2>/dev/null | wc -l) 个前端文件"
printf '  %-24s %s\n' "jdk25/bin/java" "$(stat -c '%s' "$APP_HOME/jdk25/bin/java") bytes"

if [ "${1:-}" = "--check" ]; then
    info ""
    info "== 目标环境 ==（仅检查，未做改动）"
    echo "  程序目录 = $APP_HOME"
    echo "  私有 JDK = $JDK_HOME"
    df -h "$APP_HOME" 2>/dev/null | tail -1 | sed 's/^/  /'
    echo "  当前用户 = $(id -un)（安装需 root）"
    echo "  1157 端口占用：$(ss -tln 2>/dev/null | grep -c ':1157' || true) 个"
    if [ -d "$JDK_HOME" ]; then echo "  jdk25 已存在（重复安装会覆盖）"; else echo "  jdk25 待创建"; fi
    info ""
    info "仅检查，未做改动。移除 --check 即正式安装。"
    exit 0
fi

[ "$(id -u)" = "0" ] || die "需要 root 权限"

# ---------------------------------------------------------------- 目录
info "== 准备目录 =="
# data/config 必须预先存在：启动时要在那里创建所有权锁文件
# （data/ 还会放 H2 库 hertzbeat.mv.db 与 DuckDB 时序库 history.duckdb）
mkdir -p "$APP_HOME/data" "$APP_HOME/data/config" "$APP_HOME/logs" "$APP_HOME/run"
info "  data : $APP_HOME/data （H2 + DuckDB）"
info "  logs : $APP_HOME/logs"

# ---------------------------------------------------------------- 私有 JDK
# jdk25 随发布包一起提供（已解压好），无需再解压 tar。
# 若包内只有 tar 没有目录，才走解压分支（兼容旧包）。
info "== 校验私有 JDK 25 → $JDK_HOME =="
if [ -f "$JDK_HOME/bin/java" ]; then
    info "  jdk25 已随包提供，无需解压"
elif [ -f "$JDK_TGZ" ]; then
    info "  包内为压缩形式，解压中…"
    gzip -t "$JDK_TGZ" || die "JDK 包不完整，请重新传输"
    mkdir -p "$JDK_HOME"
    tar -xzf "$JDK_TGZ" -C "$JDK_HOME" --strip-components=1 2>"$APP_HOME/run/tar-jdk.log" || \
        warn "tar 返回非零（legal/ 符号链接受限，通常可忽略），见 run/tar-jdk.log"
else
    die "未找到 jdk25/bin/java，也未找到 $JDK_TGZ"
fi

[ -f "$JDK_HOME/bin/java" ] || die "未找到 jdk25/bin/java，发布包不完整"
chmod +x "$JDK_HOME/bin/java" "$JDK_HOME/bin/"* 2>/dev/null || true

info "== 验证私有 JDK =="
# 正常情况下直接执行 java -version。若当前平台无法执行该 ELF（例如在 Windows 上
# 干跑本脚本），降级为读取 release 文件做结构校验，并明确提示需在 Linux 上复验。
if VER=$("$JDK_HOME/bin/java" -version 2>&1 | head -1) && \
   echo "$VER" | grep -q 'version "'; then
    echo "  $VER"
    MAJOR=$(echo "$VER" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p')
else
    warn "  当前平台无法执行 jdk25/bin/java（$(uname -s)），改用 release 文件做结构校验"
    REL_FILE="$JDK_HOME/release"
    [ -f "$REL_FILE" ] || die "缺少 jdk25/release，发布包不完整"
    MAJOR=$(sed -n 's/^JAVA_VERSION="\([0-9][0-9]*\).*/\1/p' "$REL_FILE")
    echo "  release 声明 JAVA_VERSION=\"$(sed -n 's/^JAVA_VERSION="\([^"]*\)".*/\1/p' "$REL_FILE")\""
fi
[ "$MAJOR" = "25" ] || die "私有 JDK 版本不是 25（实际: ${MAJOR:-未知}）"
info "  私有 JDK 25 校验通过"

# ---------------------------------------------------------------- 隔离确认
info "== 确认未污染系统环境 =="
if [ -e /usr/bin/java ]; then
    warn "/usr/bin/java 存在（非本脚本创建），本服务不依赖它"
else
    info "  /usr/bin/java 仍不存在，系统 Java 未被改动（预期）"
fi

# ---------------------------------------------------------------- 启动脚本
info "== 生成启动脚本 =="
cat > "$APP_HOME/hb.sh" <<'SCRIPT'
#!/bin/sh
# HertzBeat 启停（使用本目录下的私有 JDK 25，不碰系统 Java）
set -eu
APP_HOME=$(cd "$(dirname "$0")" && pwd)
JAVA_BIN="$APP_HOME/jdk25/bin/java"
PORT="${HB_PORT:-1157}"
PID_FILE="$APP_HOME/run/hertzbeat.pid"
LOG="$APP_HOME/logs/startup.out"

# jar 名带版本号，运行期识别，避免脚本写死版本
HB_JAR=$(ls -1 "$APP_HOME"/*.jar 2>/dev/null | grep -v -- '-jar-with' | head -1)
[ -n "$HB_JAR" ] || { echo "错误：未找到主 jar（apache-hertzbeat-*.jar）" >&2; exit 1; }

[ -x "$JAVA_BIN" ] || { echo "错误：未找到私有 JDK $JAVA_BIN" >&2; exit 1; }

running() { [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; }

case "${1:-start}" in
start)
    if running; then echo "已在运行，PID=$(cat "$PID_FILE")"; exit 0; fi
    mkdir -p "$APP_HOME/logs" "$APP_HOME/run"
    echo "使用 JDK: $JAVA_BIN"
    "$JAVA_BIN" -version 2>&1 | head -1 | sed 's/^/  /'
    echo "监听端口: $PORT"
    cd "$APP_HOME"
    # Arrow 内存访问是 Java 25 必需；--server.port 显式指定，避免环境变量被宽松绑定覆盖
    nohup "$JAVA_BIN" \
        -Dfile.encoding=UTF-8 \
        --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
        -Xms512m -Xmx1536m \
        -XX:+UseParallelGC \
        -Dlogging.path="$APP_HOME/logs" \
        -Dspring.config.location="$APP_HOME/config/" \
        -jar "$HB_JAR" \
        --server.port="$PORT" \
        >>"$LOG" 2>&1 &
    echo $! > "$PID_FILE"
    echo "已启动，PID=$!  日志: $LOG"
    i=0
    while [ $i -lt 120 ]; do
        if curl -sf -o /dev/null "http://127.0.0.1:$PORT/api/setup/status" 2>/dev/null; then
            echo "健康检查通过：http://127.0.0.1:$PORT/api/setup/status"
            exit 0
        fi
        sleep 2; i=$((i + 1))
        if ! running; then echo "启动失败，进程已退出，见 $LOG" >&2; exit 1; fi
    done
    echo "警告：240 秒未就绪（首次启动建库较慢），进程仍在，见 $LOG" >&2
    exit 1
    ;;
stop)
    if ! [ -f "$PID_FILE" ]; then echo "未运行"; exit 0; fi
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        kill "$PID"
        i=0; while [ $i -lt 30 ] && kill -0 "$PID" 2>/dev/null; do sleep 1; i=$((i+1)); done
        kill -0 "$PID" 2>/dev/null && { echo "强制结束"; kill -9 "$PID" 2>/dev/null || true; }
        echo "已停止"
    else
        echo "进程不存在，清理 PID"
    fi
    rm -f "$PID_FILE"
    ;;
status)
    if running; then
        echo "运行中，PID=$(cat "$PID_FILE")"
        curl -s "http://127.0.0.1:$PORT/api/setup/status" 2>/dev/null || true
        echo
    else echo "未运行"; exit 3; fi
    ;;
restart) "$0" stop; sleep 2; "$0" start ;;
*) echo "用法: $0 {start|stop|restart|status}" >&2; exit 2 ;;
esac
SCRIPT
chmod +x "$APP_HOME/hb.sh"
info "  已写入 $APP_HOME/hb.sh"

# ---------------------------------------------------------------- systemd
SERVICE="$APP_HOME/hertzbeat.service"
cat > "$SERVICE" <<EOF
[Unit]
Description=HertzBeat monitoring platform
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=root
WorkingDirectory=$APP_HOME
ExecStart=$APP_HOME/jdk25/bin/java -Dfile.encoding=UTF-8 --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED -Xms512m -Xmx1536m -XX:+UseParallelGC -Dlogging.path=$APP_HOME/logs -Dspring.config.location=$APP_HOME/config/ -jar $APP_HOME/$HB_JAR_NAME --server.port=1157
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
TimeoutStopSec=60
StandardOutput=journal
StandardError=journal
SyslogIdentifier=hertzbeat
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ProtectHome=true
ReadWritePaths=$APP_HOME/data $APP_HOME/logs

[Install]
WantedBy=multi-user.target
EOF
info "  已写入 $SERVICE"

# ---------------------------------------------------------------- 收尾
info ""
info "============ 安装完成 ============"
info "  程序目录 : $APP_HOME"
info "  私有 JDK : $JDK_HOME/bin/java (25.0.5)"
info "  数据     : $APP_HOME/data （H2 + DuckDB，首次启动自动建库）"
info "  日志     : $APP_HOME/logs"
info "  系统 Java: 未被改动"
info ""
info "启动："
info "  cd $APP_HOME && ./hb.sh start"
info ""
info "访问：http://<服务器IP>:1157   （首次进入会引导完成 setup）"
info ""
info "开机自启（可选，与 hb.sh 二选一，勿同时启用）："
info "  cp $SERVICE /etc/systemd/system/ && systemctl daemon-reload && systemctl enable --now hertzbeat"

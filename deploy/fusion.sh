#!/bin/sh
# PulseProbe 融合服务启动脚本（离线裸装形态）
#
# 部署形态：单进程，后端 jar 内已打包前端页面，无需 nginx。
# 运行时：本服务私有的 JRE 21，位于 $APP_HOME/runtime，与系统 Java 完全隔离。
#
# 隔离约定：
#   - 优先且默认只使用 $APP_HOME/runtime/bin/java（绝对路径调用）
#   - 不回退到系统 java，除非显式设置 FUSION_ALLOW_SYSTEM_JAVA=1
#   - 不修改 JAVA_HOME / PATH，不影响其他项目

set -eu

APP_HOME=$(cd "$(dirname "$0")" && pwd)
APP_NAME="fusion-backend"
JAR="$APP_HOME/fusion-backend.jar"
PID_FILE="$APP_HOME/run/$APP_NAME.pid"
LOG_DIR="$APP_HOME/logs"

# 端口可用 FUSION_PORT 覆盖，默认 8080
PORT="${FUSION_PORT:-8080}"

# 运行时解析：
#   1) FUSION_JAVA 显式指定
#   2) 私有 runtime/（默认路径，保证隔离）
#   3) 仅当 FUSION_ALLOW_SYSTEM_JAVA=1 时才回退系统 java
resolve_java() {
    if [ -n "${FUSION_JAVA:-}" ] && [ -x "$FUSION_JAVA" ]; then
        echo "$FUSION_JAVA"; return 0
    fi
    if [ -x "$APP_HOME/runtime/bin/java" ]; then
        echo "$APP_HOME/runtime/bin/java"; return 0
    fi
    if [ "${FUSION_ALLOW_SYSTEM_JAVA:-0}" = "1" ]; then
        if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
            echo "$JAVA_HOME/bin/java"; return 0
        fi
        if command -v java >/dev/null 2>&1; then
            command -v java; return 0
        fi
    fi
    echo "错误：未找到私有 Java 运行时 $APP_HOME/runtime/bin/java。" >&2
    echo "      请先执行 install-private-jre21.sh 安装本服务专用的 JRE 21。" >&2
    echo "      本服务刻意不使用系统 Java，以避免与其他项目的 JDK 版本冲突。" >&2
    return 1
}

mkdir -p "$APP_HOME/run" "$LOG_DIR"

case "${1:-start}" in
start)
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        echo "服务已在运行，PID=$(cat "$PID_FILE")"
        exit 0
    fi
    JAVA_BIN=$(resolve_java)
    echo "使用 JRE: $JAVA_BIN"
    "$JAVA_BIN" -version 2>&1 | head -1 | sed 's/^/  /'
    echo "监听端口: $PORT"

    # 显式指定 --server.port，规避环境变量被 Spring 宽松绑定意外覆盖
    # shellcheck disable=SC2086
    nohup "$JAVA_BIN" \
        -Xms256m -Xmx768m \
        -XX:+UseSerialGC \
        -Dfile.encoding=UTF-8 \
        -jar "$JAR" \
        --server.port="$PORT" \
        >>"$LOG_DIR/$APP_NAME.out" 2>&1 &

    echo $! > "$PID_FILE"
    echo "已启动，PID=$!  日志: $LOG_DIR/$APP_NAME.out"

    # 等待就绪（最多 60 秒）
    i=0
    while [ $i -lt 60 ]; do
        if curl -sf -o /dev/null "http://127.0.0.1:$PORT/actuator/health" 2>/dev/null; then
            echo "健康检查通过：http://127.0.0.1:$PORT/actuator/health"
            exit 0
        fi
        sleep 1
        i=$((i + 1))
        if ! kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
            echo "启动失败，进程已退出。请查看 $LOG_DIR/$APP_NAME.out" >&2
            exit 1
        fi
    done
    echo "警告：60 秒内健康检查未通过，进程仍在运行，请查看日志。" >&2
    exit 1
    ;;
stop)
    if [ ! -f "$PID_FILE" ]; then
        echo "未找到 PID 文件，服务可能未运行。"
        exit 0
    fi
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        kill "$PID"
        i=0
        while [ $i -lt 30 ] && kill -0 "$PID" 2>/dev/null; do
            sleep 1; i=$((i + 1))
        done
        if kill -0 "$PID" 2>/dev/null; then
            echo "优雅停止超时，强制结束。"
            kill -9 "$PID" 2>/dev/null || true
        fi
        echo "已停止。"
    else
        echo "进程不存在（PID=$PID），清理 PID 文件。"
    fi
    rm -f "$PID_FILE"
    ;;
status)
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        echo "运行中，PID=$(cat "$PID_FILE")"
        curl -s "http://127.0.0.1:$PORT/actuator/health" 2>/dev/null || true
        echo
    else
        echo "未运行"
        exit 3
    fi
    ;;
restart)
    "$0" stop
    sleep 1
    "$0" start
    ;;
*)
    echo "用法: $0 {start|stop|restart|status}" >&2
    exit 2
    ;;
esac

#!/bin/sh
# PulseProbe 融合服务 — 容器方式启停脚本
#
# 为什么用容器：宿主机是 Debian 11 (glibc 2.31)，而本机可复用的 JDK 17 位于
# zing-dbx 镜像内、基于 Debian 12 (glibc 2.36)。把该 JDK 复制到宿主机运行会报
# `GLIBC_2.36 not found`。直接在原镜像里跑，可完全绕开 glibc 差异，
# 且无需下载任何新镜像（服务器完全离线）。

set -eu

APP_HOME=$(cd "$(dirname "$0")" && pwd)
APP_NAME="pulseprobe"
PORT="${FUSION_PORT:-8080}"
# 复用本机已有的 JDK 17 镜像，避免离线环境下拉取镜像
IMAGE="${FUSION_IMAGE:-docker.cnb.cool/dbxio.com/dbx:latest}"
JAR="$APP_HOME/fusion-backend.jar"

case "${1:-start}" in
start)
    if [ ! -f "$JAR" ]; then
        echo "错误：未找到 $JAR" >&2
        exit 1
    fi
    if docker ps -a --format '{{.Names}}' | grep -qx "$APP_NAME"; then
        echo "容器已存在，先清理旧容器..."
        docker rm -f "$APP_NAME" >/dev/null
    fi

    # 关键：--entrypoint 覆盖镜像自带启动命令，直接执行 java
    # 挂载 jar 所在目录，容器内以只读方式读取
    docker run -d \
        --name "$APP_NAME" \
        --restart unless-stopped \
        --entrypoint /usr/bin/java \
        -p "$PORT:8080" \
        -v "$APP_HOME:/app:ro" \
        -w /app \
        -e TZ=Asia/Shanghai \
        "$IMAGE" \
        -Xms256m -Xmx768m \
        -XX:+UseSerialGC \
        -Dfile.encoding=UTF-8 \
        -jar /app/fusion-backend.jar \
        --server.port=8080

    echo "容器已启动，等待就绪..."
    i=0
    while [ $i -lt 60 ]; do
        if curl -sf -o /dev/null "http://127.0.0.1:$PORT/actuator/health" 2>/dev/null; then
            echo "健康检查通过：http://127.0.0.1:$PORT"
            docker ps --filter "name=$APP_NAME" --format '{{.Names}} | {{.Status}} | {{.Ports}}'
            exit 0
        fi
        sleep 1
        i=$((i + 1))
        if ! docker ps --format '{{.Names}}' | grep -qx "$APP_NAME"; then
            echo "容器已退出，日志如下：" >&2
            docker logs "$APP_NAME" 2>&1 | tail -20 >&2 || true
            exit 1
        fi
    done
    echo "警告：60 秒内健康检查未通过，查看日志：docker logs $APP_NAME" >&2
    exit 1
    ;;
stop)
    docker rm -f "$APP_NAME" >/dev/null 2>&1 && echo "已停止并移除容器。" || echo "容器不存在。"
    ;;
restart)
    "$0" stop
    sleep 1
    "$0" start
    ;;
status)
    if docker ps --format '{{.Names}}' | grep -qx "$APP_NAME"; then
        docker ps --filter "name=$APP_NAME" --format '{{.Names}} | {{.Status}} | {{.Ports}}'
        curl -s "http://127.0.0.1:$PORT/actuator/health" 2>/dev/null || true
        echo
    else
        echo "未运行"
        exit 3
    fi
    ;;
logs)
    docker logs -f --tail 100 "$APP_NAME"
    ;;
*)
    echo "用法: $0 {start|stop|restart|status|logs}" >&2
    exit 2
    ;;
esac

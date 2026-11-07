#!/bin/sh
# PulseProbe 融合服务 — 就地安装（不需要拷到 /opt）
#
# 用法：在解压出来的目录里直接执行（需 root）：
#   cd /zing/pulseprobe
#   sh install-all.sh
#   sh install-all.sh --check     # 仅检查，不改动任何东西
#
# ★ 目录约定：你解压在哪里，服务就装在哪里。
#   /zing/pulseprobe/                 程序目录（jar + 脚本，就是你现在看到的这些文件）
#   /zing/pulseprobe/runtime/         私有 JRE 21（只在这个目录里）
#   /zing/pulseprobe/logs/            运行日志
#   /zing/pulseprobe/run/             pid 与临时记录
#   不会创建 /opt/pulseprobe，也不会往别的目录写任何东西。
#
# 如需装到别处，显式指定：
#   APP_HOME=/other/path sh install-all.sh
#
# 隔离承诺：
#   × 不修改 /usr/bin/java、/usr/lib/jvm、update-alternatives
#   × 不修改 JAVA_HOME、PATH，不写 /etc/profile、/etc/environment
#   × 不安装任何 Debian 包，不下载任何东西
#   × 不启动/修改任何容器
#   × 不删除你目录里的任何已有文件

set -eu

PKG_DIR=$(cd "$(dirname "$0")" && pwd)
# 默认就地安装：程序目录 = 本脚本所在目录
APP_HOME="${APP_HOME:-$PKG_DIR}"
JRE_TGZ="$PKG_DIR/OpenJDK21U-jre_x64_linux.tar.gz"

JAR="fusion-backend.jar"
INSTALLER="install-private-jre21.sh"
RUNNER="fusion.sh"
SUMS="SHA256SUMS"

info() { printf '[信息] %s\n' "$*"; }
warn() { printf '[警告] %s\n' "$*"; }
die()  { printf '[错误] %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- 包内清点
info "== 清点发布包：$PKG_DIR =="
for f in "$JAR" "$JRE_TGZ" "$INSTALLER" "$RUNNER" "$SUMS"; do
    [ -f "$f" ] || die "发布包不完整，缺少: $(basename "$f")"
    printf '  %-42s %s bytes\n' "$(basename "$f")" "$(stat -c '%s' "$f" 2>/dev/null || echo '?')"
done

# ---------------------------------------------------------------- 校验和
info "== 校验文件完整性 =="
( cd "$PKG_DIR" && sha256sum -c "$SUMS" ) || die "校验失败。传输可能中断，请重新传输整个发布包。"
info "全部文件校验通过"

if [ "${1:-}" = "--check" ]; then
    info ""
    info "== 目标环境 ==（仅检查，未做任何改动）"
    echo "  程序目录(APP_HOME) = $APP_HOME"
    echo "  私有运行时         = $APP_HOME/runtime"
    echo "  日志               = $APP_HOME/logs"
    df -h "$APP_HOME" 2>/dev/null | tail -1 | sed 's/^/  /'
    if [ -d "$APP_HOME/runtime" ]; then
        echo "  runtime 已存在（重复安装会覆盖其中的 JRE 文件）"
    else
        echo "  runtime 尚不存在（安装时创建）"
    fi
    echo "  当前用户 = $(id -un)（安装需 root）"
    echo "  8080 监听情况："
    ss -tln 2>/dev/null | grep ':8080' | sed 's/^/    /' || echo "    空闲"
    info ""
    info "仅检查，未做任何改动。移除 --check 即可正式安装。"
    exit 0
fi

# ---------------------------------------------------------------- 权限
[ "$(id -u)" = "0" ] || die "需要 root 权限。请用 root 执行。"

# ---------------------------------------------------------------- 目录
# 只建这四个必要子目录，不往上层或别处写
[ -d "$APP_HOME" ] || die "程序目录不存在: $APP_HOME"
info "== 准备目录（就地，不搬迁）=="
mkdir -p "$APP_HOME/logs" "$APP_HOME/run"
info "  程序目录 : $APP_HOME"
info "  日志     : $APP_HOME/logs"
info "  运行态   : $APP_HOME/run"

# ---------------------------------------------------------------- 程序文件
if [ "$APP_HOME" = "$PKG_DIR" ]; then
    # 就地安装：文件已经在这里了，不需要也不应该再复制（cp 同文件会报错）
    info "== 程序文件已就位（就地安装，无需复制）=="
else
    info "== 复制程序文件到 $APP_HOME =="
    cp -f "$PKG_DIR/$JAR"    "$APP_HOME/$JAR"
    cp -f "$PKG_DIR/$RUNNER" "$APP_HOME/$RUNNER"
    cp -f "$PKG_DIR/$INSTALLER" "$APP_HOME/$INSTALLER"
fi
chmod +x "$APP_HOME/$RUNNER" "$APP_HOME/$INSTALLER"

# ---------------------------------------------------------------- 私有 JRE
info "== 安装私有 JRE 21 → $APP_HOME/runtime =="
# 把实际目录与包内 JRE 路径传下去，保证和本体完全一致
APP_HOME="$APP_HOME" JRE_TGZ="$JRE_TGZ" sh "$APP_HOME/$INSTALLER"

# ---------------------------------------------------------------- systemd 单元
info "== 生成 systemd 单元（按实际路径）=="
# 不再用写死 /opt/pulseprobe 的模板，避免路径与实际部署位置不符
SERVICE_FILE="$APP_HOME/pulseprobe.service"
cat > "$SERVICE_FILE" <<EOF
[Unit]
Description=PulseProbe Fusion Service (monitoring fusion layer)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=root
WorkingDirectory=$APP_HOME
ExecStart=$APP_HOME/runtime/bin/java -Xms256m -Xmx768m -XX:+UseSerialGC -Dfile.encoding=UTF-8 -jar $APP_HOME/fusion-backend.jar --server.port=8080
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
TimeoutStopSec=30

StandardOutput=journal
StandardError=journal
SyslogIdentifier=pulseprobe

NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ProtectHome=true
ReadWritePaths=$APP_HOME/logs

[Install]
WantedBy=multi-user.target
EOF
info "  已写入 $SERVICE_FILE"

# ---------------------------------------------------------------- 收尾
info ""
info "============ 安装完成 ============"
info "  程序目录  : $APP_HOME"
info "  私有运行时: $APP_HOME/runtime/bin/java (Java 21)"
info "  日志      : $APP_HOME/logs/fusion-backend.out"
info "  系统 Java : 未被改动"
info ""
info "启动："
info "  cd $APP_HOME && ./fusion.sh start"
info ""
info "开机自启（可选，与 fusion.sh 二选一，勿同时启用）："
info "  cp $SERVICE_FILE /etc/systemd/system/ && systemctl daemon-reload && systemctl enable --now pulseprobe"

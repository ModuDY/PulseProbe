#!/bin/sh
# PulseProbe 融合服务 — 整包一键安装
#
# 在发布包根目录执行（需 root）：
#   sh install-all.sh
#   sh install-all.sh --check     # 仅检查，不改动任何东西
#
# 本脚本只做「搬运 + 校验 + 调用私有 JRE 安装」，实际解压与隔离验证
# 委托给同包的 install-private-jre21.sh，避免两处逻辑分叉。
#
# 隔离承诺（与 install-private-jre21.sh 一致）：
#   × 不修改 /usr/bin/java、/usr/lib/jvm、update-alternatives
#   × 不修改 JAVA_HOME、PATH，不写 /etc/profile、/etc/environment
#   × 不安装任何 Debian 包，不下载任何东西
#   × 不启动/修改任何容器

set -eu

PKG_DIR=$(cd "$(dirname "$0")" && pwd)
APP_HOME="${APP_HOME:-/opt/pulseprobe}"
JRE_TGZ="$PKG_DIR/OpenJDK21U-jre_x64_linux.tar.gz"

JAR="fusion-backend.jar"
INSTALLER="install-private-jre21.sh"
RUNNER="fusion.sh"
UNIT="pulseprobe.service"
SUMS="SHA256SUMS"

info() { printf '[信息] %s\n' "$*"; }
warn() { printf '[警告] %s\n' "$*"; }
die()  { printf '[错误] %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- 包内清点
info "== 清点发布包：$PKG_DIR =="
for f in "$JAR" "$JRE_TGZ" "$INSTALLER" "$RUNNER" "$UNIT" "$SUMS"; do
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
    echo "  APP_HOME = $APP_HOME"
    df -h / 2>/dev/null | tail -1 | sed 's/^/  /'
    if [ -d "$APP_HOME" ]; then
        echo "  $APP_HOME 已存在，内容如下："
        ls -1 "$APP_HOME" 2>/dev/null | sed 's/^/    /'
    else
        echo "  $APP_HOME 尚不存在（安装时将创建）"
    fi
    echo "  当前用户 = $(id -un)（安装需 root）"
    echo "  占用 8080 的进程：$(ss -tlnp 2>/dev/null | grep -c ':8080' || true) 个"
    info ""
    info "仅检查，未做任何改动。移除 --check 即可正式安装。"
    exit 0
fi

# ---------------------------------------------------------------- 权限
[ "$(id -u)" = "0" ] || die "需要 root 权限。请用 root 执行，或 sudo sh install-all.sh"

# ---------------------------------------------------------------- 落盘
info "== 创建 $APP_HOME =="
mkdir -p "$APP_HOME/logs" "$APP_HOME/run"

info "== 复制程序与脚本 =="
cp -f "$PKG_DIR/$JAR" "$APP_HOME/$JAR"
cp -f "$PKG_DIR/$RUNNER" "$APP_HOME/$RUNNER"
cp -f "$PKG_DIR/$INSTALLER" "$APP_HOME/$INSTALLER"
cp -f "$PKG_DIR/$UNIT" "$APP_HOME/$UNIT"
chmod +x "$APP_HOME/$RUNNER" "$APP_HOME/$INSTALLER"

# ---------------------------------------------------------------- 私有 JRE
info "== 安装私有 JRE 21（隔离，仅落 $APP_HOME/runtime） =="
# 通过环境变量把包内 JRE 路径和安装目录交给安装脚本，
# 避免依赖 /tmp 硬编码路径，也避免两套目录配置不一致
APP_HOME="$APP_HOME" JRE_TGZ="$JRE_TGZ" sh "$APP_HOME/$INSTALLER"

# ---------------------------------------------------------------- 收尾
info ""
info "============ 安装完成 ============"
info "  程序目录 : $APP_HOME"
info "  私有运行时: $APP_HOME/runtime/bin/java (Java 21)"
info "  系统 Java : 未被改动"
info ""
info "启动："
info "  cd $APP_HOME && ./fusion.sh start"
info ""
info "开机自启（可选，二选一，勿与 fusion.sh 同时用）："
info "  cp $APP_HOME/$UNIT /etc/systemd/system/ && systemctl daemon-reload && systemctl enable --now pulseprobe"

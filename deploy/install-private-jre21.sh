#!/bin/sh
# PulseProbe 融合服务 — 私有 Java 21 隔离安装脚本
#
# 设计原则：本服务自带一套 JRE 21，只安装在 /opt/pulseprobe/runtime 下，
# 仅通过绝对路径调用。绝不执行以下任何会波及其他项目的动作：
#   × 不修改 /usr/bin/java、/usr/lib/jvm、update-alternatives
#   × 不修改系统环境变量 JAVA_HOME、PATH（不写 /etc/profile、/etc/environment）
#   × 不安装任何 Debian 包（apt 主源指向 cdrom，本来就不可用）
#   × 不启动容器（避免占用镜像与影响现有容器网络）
#
# 现有运行时互不干扰：
#   zing-sci-server  → 容器内 JDK 8  (/opt/jdk)
#   zing-dbx         → 容器内 JDK 17 (Debian 12)
#   PulseProbe       → /opt/pulseprobe/runtime（私有 JRE 21）
#
# 用法（在服务器上，需 root）：
#   sh install-private-jre21.sh
#   sh install-private-jre21.sh --check    # 只做环境检查，不安装

set -eu

APP_HOME="/opt/pulseprobe"
RUNTIME="$APP_HOME/runtime"
JAR="$APP_HOME/fusion-backend.jar"
JRE_TGZ="${JRE_TGZ:-/tmp/OpenJDK21U-jre_x64_linux.tar.gz}"

info() { printf '[信息] %s\n' "$*"; }
warn() { printf '[警告] %s\n' "$*"; }
die()  { printf '[错误] %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- 环境检查
check_env() {
    info "== 系统信息 =="
    uname -m
    ldd --version 2>/dev/null | head -1 || true

    info "== 现有 Java 分布（本脚本不会改动它们） =="
    if command -v java >/dev/null 2>&1; then
        echo "  宿主机 PATH 中的 java: $(command -v java)"
        java -version 2>&1 | head -1 | sed 's/^/    /'
    else
        echo "  宿主机 PATH 中无 java（预期如此）"
    fi
    echo "  JAVA_HOME=${JAVA_HOME:-<未设置>}"
    ls -d /usr/lib/jvm/* 2>/dev/null | sed 's/^/    /' || echo "    /usr/lib/jvm 下无 JDK"
    ls -l /usr/bin/java 2>/dev/null | sed 's/^/    /' || echo "    /usr/bin/java 不存在"

    info "== 目标目录 =="
    df -h "$APP_HOME" 2>/dev/null | tail -1 || df -h / | tail -1
    [ -f "$JAR" ] && info "jar 已就位: $(stat -c '%s' "$JAR") bytes" || warn "jar 未就位: $JAR"
    [ -f "$JRE_TGZ" ] && info "JRE 包已就位: $(stat -c '%s' "$JRE_TGZ") bytes" || warn "JRE 包未就位: $JRE_TGZ"
}

if [ "${1:-}" = "--check" ]; then
    check_env
    info "仅检查，未做任何改动。"
    exit 0
fi

# ---------------------------------------------------------------- 前置校验
[ -f "$JAR" ] || die "未找到 $JAR，请先传输 fusion-backend.jar 到 $APP_HOME/"
[ -f "$JRE_TGZ" ] || die "未找到 $JRE_TGZ，请先传输 JRE 包到 /tmp/"

# 所有运行期目录先建好，后续日志重定向才可写
mkdir -p "$APP_HOME/logs" "$APP_HOME/run"
TAR_LOG="$APP_HOME/run/tar-jre.log"

info "== 校验 JRE 包完整性 =="
gzip -t "$JRE_TGZ" || die "JRE 包不完整（可能传输中断），请重新传输"
info "压缩包完整"

# ---------------------------------------------------------------- 解压 JRE
info "== 解压私有 JRE 21 到 $RUNTIME =="
mkdir -p "$RUNTIME"

# --strip-components=1 去掉顶层的 jdk-21.0.x-jre/ 目录，使 java 落在 runtime/bin/java。
# 注意：包内 legal/ 下有 145 个符号链接，某些文件系统或权限设置下可能创建失败，
# 导致 tar 返回非零。这属非致命情况，因此不以 tar 的退出码判定成败，
# 而以解压后关键文件是否就位为准（见下方检查）。
if ! tar -xzf "$JRE_TGZ" -C "$RUNTIME" --strip-components=1 2>"$TAR_LOG"; then
    warn "tar 返回非零，查看 $TAR_LOG"
    tail -5 "$TAR_LOG" 2>/dev/null | sed 's/^/    /' || true
    warn "若仅 legal/ 下的符号链接失败，可忽略；将以关键文件为准继续校验。"
fi
info "解压完成"

[ -f "$RUNTIME/bin/java" ] || die "解压后未找到 $RUNTIME/bin/java，包结构异常"
[ -x "$RUNTIME/bin/java" ] || { chmod +x "$RUNTIME/bin/java"; info "已补加执行权限"; }
[ -f "$RUNTIME/lib/server/libjvm.so" ] || die "解压后未找到 lib/server/libjvm.so，包不完整"

# ---------------------------------------------------------------- 验证运行时
info "== 验证私有运行时 =="

# 先确认是 Linux x86_64 的 ELF，避免拿错包时给出误导性报错
if [ "$(uname -s)" = "Linux" ]; then
    if ! JAVA_VER_OUT=$("$RUNTIME/bin/java" -version 2>&1); then
        warn "私有 JRE 执行失败，原始输出如下："
        echo "$JAVA_VER_OUT" | head -5 | sed 's/^/    /'
        if echo "$JAVA_VER_OUT" | grep -q "GLIBC"; then
            die "glibc 版本过低。本包为 Temurin 21（最低需 glibc 2.15），请确认系统版本。"
        fi
        die "私有 JRE 无法执行，请检查上面输出。"
    fi
    echo "$JAVA_VER_OUT" | head -2 | sed 's/^/    /'

    # 从形如 openjdk version "21.0.13" 2026-10-20 LTS 的行里取主版本号。
    # 用 BRE 转义的圆括号，避免依赖 GNU sed 的 ERE 扩展。
    JAVA_MAJOR=$(echo "$JAVA_VER_OUT" | head -1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p')
    [ -n "$JAVA_MAJOR" ] || die "无法解析运行时版本号，输出为: $(echo "$JAVA_VER_OUT" | head -1)"
    [ "$JAVA_MAJOR" = "21" ] || die "私有运行时版本不是 21（实际: $JAVA_MAJOR）"
    info "私有 JRE 版本 21 校验通过"
else
    # 非 Linux（例如在 Windows 上干跑本脚本）无法执行 Linux ELF，
    # 改为校验文件结构与版本元数据，不做执行验证。
    warn "当前系统为 $(uname -s)，非 Linux 环境，跳过执行验证（仅校验结构）。"
    JAVA_MAJOR=$(sed -n 's/^JAVA_VERSION="\([0-9][0-9]*\).*/\1/p' "$RUNTIME/release")
    [ -n "$JAVA_MAJOR" ] || die "无法从 runtime/release 解析版本号"
    [ "$JAVA_MAJOR" = "21" ] || die "私有运行时版本不是 21（实际: $JAVA_MAJOR）"
    info "runtime/release 声明版本为 21（结构校验通过）"
    info "注意：执行验证需在 Linux 服务器上完成。"
fi

# ---------------------------------------------------------------- 确认隔离
info "== 确认未污染系统环境 =="
if [ -L /usr/bin/java ]; then
    warn "/usr/bin/java 是符号链接，指向: $(readlink -f /usr/bin/java)"
    warn "本脚本不修改它；本服务以绝对路径调用，不受其影响。"
elif [ -f /usr/bin/java ]; then
    warn "/usr/bin/java 存在（非本脚本创建）。本服务不依赖它。"
else
    info "/usr/bin/java 仍不存在，系统 Java 未被改动（预期）"
fi

# ---------------------------------------------------------------- 启动脚本
if [ -f "$APP_HOME/fusion.sh" ]; then
    chmod +x "$APP_HOME/fusion.sh"
    info "启动脚本已就绪"
else
    warn "未找到 $APP_HOME/fusion.sh，请一并传输"
fi

mkdir -p "$APP_HOME/logs" "$APP_HOME/run"

info ""
info "安装完成。启动方式："
info "  cd $APP_HOME && ./fusion.sh start"
info ""
info "启动后验证："
info "  curl -s http://127.0.0.1:8080/actuator/health"
info "  浏览器访问 http://199.199.5.151:8080"
info ""
info "本服务使用 $RUNTIME/bin/java，与系统 Java 及其他项目的 JDK 完全隔离。"

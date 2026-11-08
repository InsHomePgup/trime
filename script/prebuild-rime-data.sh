#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later
#
# 在电脑上预先编译 Rime 词库，打包进 APK，装完不用等部署就能打字。
#
# 做的事情：
#   1. 用 app/src/main/jni/librime（和 APK 里同一个版本）编译一个电脑上能跑的 rime_deployer。
#      编译在 build/host-librime/ 里进行，不碰子模块本身，避免产物混进 Android 的 CMake 构建。
#      只第一次需要编译（约 5~10 分钟），之后复用。
#   2. 用 rime_deployer --build 编译 app/src/main/assets/shared 里的方案和词库。
#   3. 把生成的 *.bin 复制到 app/src/main/assets/shared/build/。
#      librime 会把「共享目录/build」当作预编译目录：手机上部署时发现词库没变，就直接用，不再编译。
#
# 依赖：cmake、C++ 编译器、curl；Linux 上还需要 libboost-regex-dev（macOS 自动下载 boost 头文件）。
#
# 用法：script/prebuild-rime-data.sh
#   环境变量 RIME_DEPLOYER=/path/to/rime_deployer 可以直接指定已有的 rime_deployer，跳过第 1 步。

set -euo pipefail
# 让 $(...) 里的命令出错时也能中止脚本
shopt -s inherit_errexit

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIBRIME_SRC="$ROOT/app/src/main/jni/librime"
HOST_DIR="$ROOT/build/host-librime"
SHARED_DIR="$ROOT/app/src/main/assets/shared"
OUT_DIR="$SHARED_DIR/build"
WORK_DIR="$ROOT/build/prebuild-rime-data"

log() { echo "[prebuild-rime-data] $*" >&2; }

jobs() { getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4; }

build_host_deployer() {
    local deployer="$HOST_DIR/build/bin/rime_deployer"
    # 源码版本变了（子模块更新）就重新编译
    # 先给 librime 打上项目里的补丁（和 APK 里的 librime 保持一致，见 Makefile 的 patch-apply）
    make -C "$ROOT" patch-apply >&2
    # 版本号 = 子模块提交 + 本地补丁内容，任一变化都重新编译
    local rev
    rev="$(git -C "$LIBRIME_SRC" rev-parse HEAD)-$(git -C "$LIBRIME_SRC" diff HEAD -- src | shasum | cut -c1-12)"
    if [[ -x "$deployer" && "$(cat "$HOST_DIR/.source-rev" 2>/dev/null)" == "$rev" ]]; then
        echo "$deployer"
        return
    fi

    log "编译电脑版 librime ${rev}（只需一次）..."
    # boost 源码包有 180 MB，留一份缓存，重新编译时不用再下载
    local cache_dir="$ROOT/build/cache"
    mkdir -p "$cache_dir"
    mv "$HOST_DIR"/deps/boost_*.tar.gz "$cache_dir/" 2>/dev/null || true
    rm -rf "$HOST_DIR"
    mkdir -p "$HOST_DIR"
    # 复制源码（包括 deps 下的嵌套子模块），不带 .git
    (cd "$LIBRIME_SRC" && tar --exclude='.git' -cf - .) | (cd "$HOST_DIR" && tar -xf -)
    cp "$cache_dir"/boost_*.tar.gz "$HOST_DIR/deps/" 2>/dev/null || true

    local boost_args=()
    if [[ "$(uname)" == "Darwin" ]]; then
        # macOS 上 librime 只需要 boost 头文件
        (cd "$HOST_DIR" && ./install-boost.sh --download >&2)
        boost_args+=("-DBOOST_ROOT=$(ls -d "$HOST_DIR"/deps/boost-*)" -DBoost_NO_BOOST_CMAKE=TRUE)
    fi

    # 用 librime 自带的子模块编译依赖（静态库，装到 $HOST_DIR 下的 include/ lib/），
    # 不依赖、也不受系统里已安装的 glog/leveldb/snappy 等影响，保证本机和 CI 结果一致
    build_dep() {
        local name="$1"
        shift
        log "  依赖 $name"
        (cd "$HOST_DIR/deps/$name" \
            && cmake . -Bbuild \
                -DCMAKE_BUILD_TYPE=Release \
                -DCMAKE_INSTALL_PREFIX="$HOST_DIR" \
                -DBUILD_SHARED_LIBS=OFF \
                -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
                "$@" >&2 \
            && cmake --build build --target install -j"$(jobs)" >&2)
    }
    build_dep glog -DBUILD_TESTING=OFF -DWITH_GFLAGS=OFF -DWITH_GTEST=OFF -DWITH_UNWIND=OFF
    # leveldb 会自动检测并链接系统里的 snappy/crc32c/tcmalloc，这里显式关掉
    build_dep leveldb -DLEVELDB_BUILD_BENCHMARKS=OFF -DLEVELDB_BUILD_TESTS=OFF \
        -DHAVE_SNAPPY=OFF -DHAVE_CRC32C=OFF -DHAVE_TCMALLOC=OFF
    build_dep marisa-trie -DBUILD_TESTING=OFF -DENABLE_TOOLS=OFF
    build_dep opencc -DBUILD_DOCUMENTATION=OFF -DENABLE_GTEST=OFF -DENABLE_BENCHMARK=OFF
    build_dep yaml-cpp -DYAML_CPP_BUILD_CONTRIB=OFF -DYAML_CPP_BUILD_TESTS=OFF -DYAML_CPP_BUILD_TOOLS=OFF

    # 只要 rime_deployer，不编测试和插件。
    # plugins/ 下是 Android 构建时建的插件软链接（lua 等），编词库用不到，删掉
    rm -rf "$HOST_DIR"/plugins/librime-*
    # librime 的 tools（含 rime_deployer）只在编动态库时才会生成；依赖仍是静态链接
    log "  librime"
    (cd "$HOST_DIR" && cmake . -Bbuild \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_SHARED_LIBS=ON \
        -DBUILD_STATIC=ON \
        -DBUILD_TEST=OFF \
        -DENABLE_EXTERNAL_PLUGINS=OFF \
        "${boost_args[@]}" >&2 \
        && cmake --build build --target rime_deployer -j"$(jobs)" >&2)

    [[ -x "$deployer" ]] || { log "rime_deployer 编译失败"; exit 1; }
    echo "$rev" > "$HOST_DIR/.source-rev"
    echo "$deployer"
}

deployer="${RIME_DEPLOYER:-}"
if [[ -z "$deployer" ]]; then
    deployer="$(build_host_deployer)"
fi
log "使用 $deployer"

# 用一个干净的用户目录编译，避免本机的个人配置（*.custom.yaml）混进去
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/user" "$WORK_DIR/staging"
# 先删掉上次生成的结果：shared/build 正是 librime 的预编译目录，
# 留着的话 rime_deployer 会直接复用旧文件，新结果就不完整了
rm -rf "$OUT_DIR"

log "编译方案和词库..."
# rime_deployer --build <user_data_dir> <shared_data_dir> <staging_dir>
(cd "$WORK_DIR" && "$deployer" --build "$WORK_DIR/user" "$SHARED_DIR" "$WORK_DIR/staging" >&2)

shopt -s nullglob
bins=("$WORK_DIR"/staging/*.bin)
if [[ ${#bins[@]} -eq 0 ]]; then
    log "没有生成任何 .bin，编译失败"
    exit 1
fi

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
cp "${bins[@]}" "$OUT_DIR/"
log "已生成 ${#bins[@]} 个文件到 ${OUT_DIR}（$(du -sh "$OUT_DIR" | cut -f1)）"

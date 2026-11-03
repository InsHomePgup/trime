#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# 把 app/data/rime/myrime（MyRimeConfig submodule）里的顶层文件/目录，
# 逐一在 app/src/main/assets/shared/ 下建同名符号链接，作为 Trime 内置的
# sharedDataDir 内容。目录整体软链，子目录内容自动生效。
#
# MyRimeConfig 顶层新增文件/目录后，重新运行本脚本即可补建链接
# （已有文件内容更新不需要重跑，submodule 更新后自动生效）。

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DATA_DIR="$REPO_ROOT/app/data/rime/myrime"
SHARED_DIR="$REPO_ROOT/app/src/main/assets/shared"

# 非 Rime 运行时文件，不建链接；opencc 由构建任务 installOpenCCData 合并拷贝，不能软链
EXCLUDE_PATTERN='^(\.git|deploy\.sh|deploy\.ps1|LICENSE|README\.md|CLAUDE\.md|recipe\.yaml|opencc)$'

if [ ! -d "$DATA_DIR" ]; then
    echo "找不到 $DATA_DIR，请先执行 git submodule update --init app/data/rime/myrime" >&2
    exit 1
fi

mkdir -p "$SHARED_DIR"

# 清理旧的、指向 myrime 的符号链接（不动 trime.yaml / tongwenfeng.trime.yaml 等实体文件）
find "$SHARED_DIR" -maxdepth 1 -type l | while read -r link; do
    target="$(readlink "$link")"
    if [[ "$target" == *"data/rime/myrime"* ]] || [[ "$target" == *"data/rime/prelude"* ]] || \
       [[ "$target" == *"data/rime/luna-pinyin"* ]] || [[ "$target" == *"data/rime/stroke"* ]] || \
       [[ "$target" == *"data/rime/essay"* ]]; then
        rm -f "$link"
    fi
done

count=0
for entry in "$DATA_DIR"/* "$DATA_DIR"/.[!.]*; do
    [ -e "$entry" ] || continue
    name="$(basename "$entry")"
    if [[ "$name" =~ $EXCLUDE_PATTERN ]]; then
        continue
    fi
    ln -sfn "../../../../data/rime/myrime/$name" "$SHARED_DIR/$name"
    count=$((count + 1))
done

echo "已在 $SHARED_DIR 下建立 $count 个指向 app/data/rime/myrime 的符号链接"

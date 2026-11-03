<!--
SPDX-FileCopyrightText: 2015 - 2024 Rime community

SPDX-License-Identifier: GPL-3.0-or-later
-->

# Trime 自定义（个人版）

**仅供个人使用**的 Trime（同文输入法）定制版，fork 自 [osfans/trime]，内嵌了我自己的 Rime 配置仓库
[InsHomePgup/MyRimeConfig] 作为默认方案数据。装完 APK 即可直接使用，不需要再手动导入配置。

[osfans/trime]: https://github.com/osfans/trime
[InsHomePgup/MyRimeConfig]: https://github.com/InsHomePgup/MyRimeConfig

## 这是什么

这个仓库唯一的目的，是把我自己的 Rime 配置（[MyRimeConfig][InsHomePgup/MyRimeConfig]）打包进
Trime 的 Android APK，做成一个"装完即用"的输入法安装包，让手机上的输入体验和 macOS / Windows /
iOS 上保持一致，不用再手动同步词库、方案、Lua 脚本。

**不是**面向公众发布的通用输入法应用，没有为其他人的使用场景做适配，只根据我自己的需要维护。

## 内置输入方案

默认启用（见 `MyRimeConfig` 的 `default.yaml` 中 `schema_list`）：

| 方案 | 说明 |
|------|------|
| 虎码单字 (`tiger`) | 虎码形码，纯单字模式，候选旁显示拆分提示；按 `` ` `` 可切换自然码双拼反查（词组逐字显示虎码编码）；支持无前缀直接打英文 |
| 自然码双拼 (`double_pinyin`) | 自然码双拼方案，词库基于 [雾凇拼音](https://github.com/iDvel/rime-ice)，带 emoji 候选 |

仓库里还带了其他几个方案（`rime_ice`、`PY_c`、`radical_pinyin`、`stroke`、`easy_english`、
`core2022`、`melt_eng`），默认没有启用；如果需要，可以在 App 内「方案」设置里手动勾选启用。

方案本身的详细说明见 [MyRimeConfig 的 README][InsHomePgup/MyRimeConfig]。

## 下载安装

Release 页面提供已签名的正式版 APK：

👉 [Releases](https://github.com/InsHomePgup/trime/releases)

每个版本按 CPU 架构（ABI）分成几个 APK，选一个装：

- **`arm64-v8a`**：绝大多数近几年的手机，不确定就选这个
- `armeabi-v7a`：很老的 32 位手机
- `x86_64` / `x86`：电脑上的安卓模拟器

装完后的设置步骤：

1. 打开「Trime 自定义」，按引导在系统设置里**启用**输入法，并把它**切换**为当前输入法
2. 第一次启动会自动部署方案，需要等十几秒到一分钟，期间候选可能不出现
3. 部署完成后，在任意输入框里就能打字

`applicationId` 已改为 `com.osfans.trime.myconfig`，签名也和官方 Trime（`com.osfans.trime`）
不同，所以可以和官方版同时安装。

## 和官方 Trime 的区别

- `app/data/rime/myrime` 用 git submodule 指向 [MyRimeConfig][InsHomePgup/MyRimeConfig]，替换了
  官方默认的 `prelude`/`luna-pinyin`/`stroke`/`essay` 四个方案仓库
- `app/src/main/assets/shared/` 下的符号链接改为指向 `myrime`（由 `script/link-custom-data.sh`
  生成）
- `myrime/opencc/` 里的 OpenCC 词典（虎码拆分 `hu_cf`、`emoji` 等）由构建任务
  `installOpenCCData` 和官方 OpenCC 数据一起拷进 `assets/shared/opencc`
  （见 `build-logic/convention/src/main/kotlin/OpenCCDataPlugin.kt`）。这个目录**不能**用符号链接，
  否则构建时官方数据会写进 submodule
- `applicationId` 改为 `com.osfans.trime.myconfig`，App 名称改为「Trime 自定义」，用于和官方版共存
- `.github/workflows/release-ci.yml` 增加了 `permissions: contents: write`，让 fork 上默认的
  `GITHUB_TOKEN` 有权限在打 tag 时创建 Release
- 版本号用自己的编号（`v1.0.0`、`v1.0.1`…），不跟随上游的 `v3.3.x`。tag 名会通过
  `git describe` 成为 App 里显示的版本号
- 其余代码、构建方式、Trime 本身的功能均未改动，完整开发文档见官方仓库 [osfans/trime]

## 更新配置后如何出新 APK

1. 照常修改 [MyRimeConfig][InsHomePgup/MyRimeConfig] 并 push（那边不需要为安卓做任何改动）
2. 在本仓库执行：`git submodule update --init --remote app/data/rime/myrime`
3. 如果这次改动在 MyRimeConfig **顶层**新增了文件/目录，重新跑一次
   `script/link-custom-data.sh` 补建符号链接（已有文件内容的更新、子目录如 `lua/`、`opencc/`
   里的新增文件都不需要这步）。`.git`、`deploy.*`、`LICENSE`、`README.md`、`CLAUDE.md`、
   `recipe.yaml`、`opencc` 会被跳过
4. `git add -A && git commit -m "update myrime" && git push`
5. 打一个新 tag 触发 Release 构建，版本号接着上一个 Release 往下编：
   `git tag v1.0.X && git push origin v1.0.X`
6. 等 GitHub Actions 的 Release 工作流跑完（约 15 分钟），新 APK 会自动出现在
   [Releases](https://github.com/InsHomePgup/trime/releases) 页面

每次 push 还会触发 Commit 工作流，在 Actions 页面的 artifact 里能下载 debug 版 APK
（包名带 `.debug`，可以和正式版同时安装），适合发正式版之前先试一下。

## 没有安卓手机怎么测试

- **方案逻辑**（Lua、反查、词库）是 librime 跑的，桌面和安卓是同一套，可以先在小狼毫/鼠须管上验证
- **安卓上的效果**：把 Release 里的 `arm64-v8a` APK 发给有安卓手机的人安装；或者在电脑上用
  Android Studio 自带的模拟器（建 x86_64 镜像）安装 `x86_64` 的 APK，把 APK 拖进模拟器窗口即可

## 上游项目

Trime 本体的介绍、开发环境搭建、贡献指南等请参考官方仓库文档：
[简体中文](README_sc.md) | [繁體中文](README_tc.md) | [官方仓库 osfans/trime](https://github.com/osfans/trime)

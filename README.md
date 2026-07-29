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
| 虎码单字 (`tiger`) | 虎码形码，纯单字模式；按 `` ` `` 可切换自然码双拼反查编码 |
| 自然码双拼 (`double_pinyin`) | 自然码双拼方案，词库基于 [雾凇拼音](https://github.com/iDvel/rime-ice) |

仓库里还带了其他几个方案（`rime_ice`、`PY_c`、`radical_pinyin`、`stroke`、`easy_english`、
`core2022`、`melt_eng`），默认没有启用；如果需要，可以在 App 内「方案」设置里手动勾选启用。

方案本身的详细说明见 [MyRimeConfig 的 README][InsHomePgup/MyRimeConfig]。

## 下载安装

Release 页面提供已签名的正式版 APK（按机型选一个 ABI 装，不确定就选 `arm64-v8a`）：

👉 [Releases](https://github.com/InsHomePgup/trime/releases)

因为改了 `applicationId`（`com.osfans.trime.myconfig`），和官方 Trime（`com.osfans.trime`）
签名不同，可以和官方版共存安装，互不冲突。

## 和官方 Trime 的区别

- `app/data/rime/myrime` 用 git submodule 指向 [MyRimeConfig][InsHomePgup/MyRimeConfig]，替换了
  官方默认的 `prelude`/`luna-pinyin`/`stroke`/`essay` 四个方案仓库
- `app/src/main/assets/shared/` 下的符号链接相应改为指向 `myrime`（见
  `script/link-custom-data.sh`）
- `applicationId` 改为 `com.osfans.trime.myconfig`，App 名称改为「Trime 自定义」，用于和官方版共存
- `.github/workflows/release-ci.yml` 增加了 `permissions: contents: write`，让 fork 上默认的
  `GITHUB_TOKEN` 有权限在打 tag 时创建 Release
- 其余代码、构建方式、Trime 本身的功能均未改动，完整开发文档见官方仓库 [osfans/trime]

## 更新配置后如何出新 APK

1. 照常修改 [MyRimeConfig][InsHomePgup/MyRimeConfig] 并 push
2. 在本仓库执行：`git submodule update --remote app/data/rime/myrime`
3. 如果这次改动在 MyRimeConfig **顶层**新增了文件/目录，重新跑一次
   `script/link-custom-data.sh` 补建符号链接（已有文件内容更新不需要这步）
4. `git add -A && git commit -m "update myrime" && git push`
5. 打一个新 tag 触发 Release 构建：`git tag vX.Y.Z && git push origin vX.Y.Z`
6. 等 GitHub Actions 跑完，新 APK 会自动出现在 [Releases](https://github.com/InsHomePgup/trime/releases) 页面

## 上游项目

Trime 本体的介绍、开发环境搭建、贡献指南等请参考官方仓库文档：
[简体中文](README_sc.md) | [繁體中文](README_tc.md) | [官方仓库 osfans/trime](https://github.com/osfans/trime)

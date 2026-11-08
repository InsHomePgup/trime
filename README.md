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

只提供 `arm64-v8a` 一个 APK，适用于近几年的安卓手机（很老的 32 位手机不支持）。电脑上的
Android 模拟器请用 Android 11 及以上的镜像，它们自带 ARM 转译，可以直接装这个 APK。

`applicationId` 已改为 `com.osfans.trime.myconfig`，签名也和官方 Trime（`com.osfans.trime`）
不同，所以可以和官方版同时安装。

## 使用方法

### 首次设置

打开「Trime 自定义」，会出现三步设置向导，**三步都要完成**：

1. **请求存储权限**：点「授予权限」并允许。部署结果和个人词库存放在手机存储的 `rime/` 目录
   （`/sdcard/rime`）里，**没有这个权限就无法部署，打字会直接上屏英文、没有候选**
2. **启用输入法**：跳到系统设置后，打开「Trime 自定义」的开关
3. **选择输入法**：在弹出的列表里选「Trime 自定义」作为当前输入法

完成后回到 App 主界面，点一次 **「部署」**，等提示「部署完成」。第一次部署要编译词库，
可能需要一分钟左右，期间打字没有候选是正常的。

### 打字

- 默认方案是**虎码单字**，另一个内置方案是**自然码双拼**
- **切换方案**：长按键盘底排空格左边的「中文/西文」键，弹出方案选单后选择
- **中英文切换**：单击同一个「中文/西文」键
- 虎码里按 `` ` ``（长按 `z` 键）进入自然码双拼反查，用拼音查虎码编码
- 拆分提示、emoji 候选等开关也在方案选单里

### App 里的其他设置

主界面上的入口：

| 入口 | 用途 |
|---|---|
| 部署 | 改了配置或装了新版本后，点这里重新部署 |
| 方案 | 勾选要启用的方案（除虎码、双拼外，`rime_ice`、`PY_c` 等方案也在这里启用） |
| 用户词典 | 导入/导出/备份个人词库 |
| 虚拟键盘、候选窗口、主题 | 调整键盘高度、按键音和振动、配色等 |

### 遇到问题

**打字直接上屏英文、没有候选**：

1. 检查存储权限：系统设置 → 应用 → Trime 自定义 → 权限
2. 在 App 里点「部署」，看提示是「部署完成」还是「部署失败」。失败的话，点那条提示就能看到错误日志
3. 单击「中文/西文」键，确认当前不是西文模式
4. 切到自然码双拼试试，判断是所有方案都不行，还是只有某个方案不行

**要查看详细日志**：App 主界面右上角菜单 → 开发者 → 实时日志。先点一次「部署」，再看日志里
带 `E` 或 `rime` 字样的行。

**装新版本后配置没有更新**：内置的方案和词库在装新版本时会自动同步，在 App 里点一次「部署」
即可生效。如果还是旧的，检查 `/sdcard/rime` 里有没有和方案同名的 `.yaml` 文件（比如
`default.yaml`、`tiger.schema.yaml`）：这个目录里的同名文件会**覆盖**内置配置。官方 Trime
默认也用这个目录，**以前装过官方 Trime 的手机**尤其要检查，把这些遗留文件删掉再部署（不要删
`*.userdb` 目录，那是个人词库）。

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
- CI 工作流全部改为只手动触发、只跑 ubuntu；Release 工作流改为手动填版本号，自动打 tag 并发布
  （`permissions: contents: write` 让 fork 上默认的 `GITHUB_TOKEN` 有权限推 tag、建 Release）
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
5. 到 GitHub 的 [Actions → Release](https://github.com/InsHomePgup/trime/actions/workflows/release-ci.yml)
   页面点 **Run workflow**，分支选 `main`，填版本号（接着上一个 Release 往下编，如 `v1.0.2`）。
   工作流会自动在该分支最新提交上打 tag；如果填的是已存在的 tag，则重新构建那个版本并更新 Release
6. 等工作流跑完（约 15–20 分钟），新 APK 会出现在
   [Releases](https://github.com/InsHomePgup/trime/releases) 页面

所有工作流都只在 ubuntu 上跑，并且**只能手动触发**，push 不会自动构建。想在发正式版之前先试一下，可以
在 [Actions → Commit](https://github.com/InsHomePgup/trime/actions/workflows/commit-ci.yml) 页面点
**Run workflow** 构建 debug 版，跑完后在该次运行页面底部的 Artifacts 下载 `trime-ubuntu-24.04`（包名带 `.debug`，可以和
正式版同时安装）。

## 没有安卓手机怎么测试

- **方案逻辑**（Lua、反查、词库）是 librime 跑的，桌面和安卓是同一套，可以先在小狼毫/鼠须管上验证
- **安卓上的效果**：把 Release 里的 APK 发给有安卓手机的人安装；或者在电脑上用
  Android Studio 自带的模拟器（Android 11+ 镜像）安装，把 APK 拖进模拟器窗口即可

## 上游项目

Trime 本体的介绍、开发环境搭建、贡献指南等请参考官方仓库文档：
[简体中文](README_sc.md) | [繁體中文](README_tc.md) | [官方仓库 osfans/trime](https://github.com/osfans/trime)

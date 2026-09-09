<div align="center">

# blbl-android

### 哔哩哔哩第三方安卓客户端（二次开发版）

原生 Android 开发 · 触摸 / 遥控双操作 · 适用于平板、TV、车机

<p>
  <img alt="Android 5.0+" src="https://img.shields.io/badge/Android-5.0%2B-3DDC84?logo=android">
  <img alt="Version 0.1.33" src="https://img.shields.io/badge/version-0.1.33-blue">
  <img alt="ABI armeabi-v7a" src="https://img.shields.io/badge/ABI-armeabi--v7a-orange">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Fork of cat3399/blbl" src="https://img.shields.io/badge/fork%20of-cat3399%2Fblbl-lightgrey">
</p>

</div>

---

## 项目简介

一个纯原生 Android 开发的哔哩哔哩第三方客户端，界面针对**大屏与遥控器**设计，同时完整支持**触摸屏**操作，最低可运行在 Android 5.0（API 21）。

继承自 [cat3399/blbl](https://github.com/cat3399/blbl)，在此基础上新增了**三级无痕模式**、**本地观看历史**和**手机扫码输入**三项功能。

核心能力：

- 侧边栏导航：搜索 / 推荐 / 分类 / 动态 / 直播 / 我的
- 视频播放：Media3（ExoPlayer）与 IjkPlayer 双内核，支持清晰度、编码、倍速、字幕、弹幕
- 直播观看：弹幕与 SC（Super Chat）显示
- 扫码登录、多账号切换
- 播放进度同步（可按需关闭，见无痕模式）

---

## 关于本仓库

本项目是 [cat3399/blbl](https://github.com/cat3399/blbl) 的**二次开发版本**，并非从零编写。上游项目的作者与贡献者保留了绝大部分实现，本仓库仅在其基础上做增量修改：

| 项目 | 说明 |
| --- | --- |
| 上游仓库 | [cat3399/blbl](https://github.com/cat3399/blbl) |
| 本仓库 | [lihuom/blbl](https://github.com/lihuom/blbl) |
| 上游同步 | 基于上游 `cd09bd9` 继续开发，可继续合并上游更新 |
| 包名（release） | `blbl.cat3399` |
| 包名（debug） | `blbl.cat3399.debug` — 可与正式版共存安装 |

上游的通用功能、播放器改进与缺陷修复绝大部分在此版本中同样可用。如果你只需要原始版本，请直接使用上游仓库。

---

## 本分支新增功能

| 功能 | 说明 |
| --- | --- |
| **三级无痕模式** | 在设置中切换三档：<br>· **关** — 正常记录并上报观看历史<br>· **本地有痕 · 网络无痕** — 不向服务器上报播放进度，仅保存在本机，可在「本地历史」中查看并续播<br>· **完全无痕** — 既不上报也不写本地记录，搜索历史同样不再保存 |
| **本地观看历史** | 独立于哔哩哔哩服务端的本地记录页，按最近观看倒序排列，同一视频只保留最新一条，上限 200 条；支持从侧边栏直接进入，可单条删除与清空。点击记录会**自动从上次观看位置续播**，无需重新拖动进度条 |
| **手机扫码输入** | TV 端一键开启局域网服务（`9529` 端口），用手机扫码打开网页输入关键词，可「填入搜索框」或「直接搜索」，解决遥控器打字痛苦的问题 |

---

## 界面预览

<details open>
<summary><b>推荐 · 分类 · 动态</b></summary>

| 推荐页 | 分类页 | 动态页 |
| :---: | :---: | :---: |
| <img src="example-pic/%E6%8E%A8%E8%8D%90%E9%A1%B5.png" width="240" alt="推荐页"> | <img src="example-pic/%E5%88%86%E7%B1%BB%E9%A1%B5.png" width="240" alt="分类页"> | <img src="example-pic/%E5%8A%A8%E6%80%81%E9%A1%B5.png" width="240" alt="动态页"> |

</details>

<details>
<summary><b>直播 · 我的 · 搜索</b></summary>

| 直播页 | 我的页 | 搜索页 |
| :---: | :---: | :---: |
| <img src="example-pic/%E7%9B%B4%E6%92%AD%E9%A1%B5.png" width="240" alt="直播页"> | <img src="example-pic/%E6%88%91%E7%9A%84%E9%A1%B5.png" width="240" alt="我的页"> | <img src="example-pic/%E6%90%9C%E7%B4%A2%E9%A1%B5.png" width="240" alt="搜索页"> |

</details>

<details>
<summary><b>追番 · 播放</b></summary>

| 追番 | 视频播放页 |
| :---: | :---: |
| <img src="example-pic/%E8%BF%BD%E7%95%AA.png" width="240" alt="追番"> | <img src="example-pic/%E8%A7%86%E9%A2%91%E6%92%AD%E6%94%BE%E9%A1%B5.png" width="240" alt="视频播放页"> |

</details>

> 截图取自上游 example-pic 目录，实际界面以最新版本为准。

---

## 下载

安装包不随 Git 仓库分发（`.gitignore` 已忽略 `*.apk`），请通过以下任一方式获取：

1. **[Releases](https://github.com/lihuom/blbl/releases)** — 正式版本发布处
2. **[Actions](https://github.com/lihuom/blbl/actions)** — 手动触发构建，产物在对应任务的 Artifacts 中下载

| 工作流 | 产物 |
| --- | --- |
| `Android Release` | 正式签名版（R8 混淆 + 资源压缩），并自动发布到 Releases |
| `Android Debug` | 调试版，包名 `.debug` 后缀，可与正式版共存 |
| `Android API19 Release` | 面向更低系统版本的变体 |

设备兼容性：

- **架构**：当前仅提供 `armeabi-v7a`（32 位）。不支持 arm64-v8a / x86_64
- **系统**：Android 5.0（API 21）及以上

---

## 构建

环境要求：**JDK 17**、Android SDK（compileSdk 36）。

```bash
# 调试包
./gradlew assembleDebug

# 正式包
./gradlew assembleRelease -PversionName=0.1.33 -PversionCode=33
```

可选：-PversionName 与 -PversionCode 用于指定版本号，不传则回落到 `1` / `0.1.0`。

正式签名需要以下环境变量或 Gradle 属性（仓库本身不含签名文件）：

```
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

在 GitHub Actions 中，请于仓库 Secrets 额外配置 `RELEASE_KEYSTORE_BASE64`。

---

## 技术栈

- Kotlin + AndroidX + ViewBinding
- Media3（ExoPlayer）/ [Ijkplayer](https://github.com/cat3399/ijkplayer)
- OkHttp
- Protobuf-lite
- Material Components / RecyclerView / ViewPager2
- ZXing（生成手机扫码输入所需的二维码）

---

## 常见问题

**手机扫描二维码后打不开页面？**
确认手机与设备处于同一局域网，且 TV 端已开启「手机扫码输入」。服务端口为 `9529`，需确保未被路由器隔离（AP 隔离 / 访客网络会阻断）。

**安装时提示覆盖已有应用？**
正式版与调试版包名不同（`blbl.cat3399` 与 `blbl.cat3399.debug`），可共存。若提示覆盖，说明安装的是同一渠道的不同签名版本，请先卸载旧版。

**升级后登录失效？**
切换 Web / App 接口后需要重新登录。

---

## 免责声明

1. 本项目是**非官方**的第三方开源客户端，与哔哩哔哩（bilibili）官方无任何隶属、授权或合作关系。软件内所有视频、弹幕、评论等内容的著作权均归哔哩哔哩及其原作者所有。
2. 本项目通过公开接口获取展示网络数据，**不提供、不存储、不转发任何受版权保护的内容**，所有播放内容均由哔哩哔哩服务器提供。
3. **禁止利用本项目从事营利活动**。本项目不存在任何形式的收费、捐赠或广告变现，第三方基于本项目的盈利行为与本项目无关。
4. 本项目仅供学习与技术交流使用，请勿用于任何违反当地法律法规或违反哔哩哔哩用户协议的场景。下载后请在 24 小时内删除；因使用本项目产生的任何责任由使用者自行承担。
5. 本仓库基于 [cat3399/blbl](https://github.com/cat3399/blbl) 二次开发，遵循上游项目的分发条款；上游未附带独立开源许可文件，本仓库亦沿用此状态。二次分发或修改请同时保留原作者与本仓库的来源署名。
6. 若您是哔哩哔哩官方人员且认为本项目侵犯了您的合法权益，请通过 [Issues](https://github.com/lihuom/blbl/issues) 联系，相关内容将在第一时间处理。

---

<div align="center">

感谢 [cat3399](https://github.com/cat3399) 及上游所有贡献者的开源工作，也感谢参与测试反馈的用户们。

</div>

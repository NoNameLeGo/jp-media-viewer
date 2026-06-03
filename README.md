# JP Media Viewer

一个简洁的 Android 随机媒体浏览器。添加本地文件夹后自动扫描图片和视频，支持随机浏览、手势切换、收藏列表和文件详情查看。

## 功能

- **文件夹选择**：通过系统文件选择器（SAF）添加一个或多个图片/视频文件夹。
- **自动扫描**：添加文件夹或修改 `.nomedia` 设置后自动扫描，不需要等到开始浏览时再扫描。
- **扫描进度**：首页显示已检查文件数、已找到媒体数和扫描完成提示。
- **空结果提示**：没有找到媒体时提示可能原因，包括目录无媒体、`.nomedia` 过滤、文件夹授权失效等。
- **.nomedia 支持**：可选择是否遵守 `.nomedia` 文件，灵活控制扫描范围。
- **随机浏览**：扫描完成后随机排序媒体文件，每次浏览都有不同顺序。
- **手势浏览**：上滑下一张，下滑上一张，单击显示或隐藏控制栏。
- **收藏功能**：双击当前图片或视频即可收藏/取消收藏，收藏状态会持久保存。
- **收藏列表**：首页提供“查看收藏”入口，可只浏览已收藏媒体；收藏为空或收藏文件失效时会显示提示。
- **文件信息**：底部显示文件大小和所属文件夹；长按媒体可打开详情弹窗。
- **图片 & 视频**：图片使用 Coil 加载，视频使用 Media3 / ExoPlayer 自动循环播放。
- **Material You**：Android 12+ 支持莫奈动态取色；低版本 Android 使用普通 Material3 主题以保持兼容。

## 下载

从 [Releases](https://github.com/NoNameLeGo/jp-media-viewer/releases) 页面下载最新 APK。

当前自动发布的 APK 是 debug 签名版本，适合个人安装和测试。如果需要公开分发，建议改用正式 keystore 签名。

## 使用方式

1. 打开应用，点击“添加文件夹”。
2. 选择包含图片或视频的目录，并授予读取权限。
3. 等待首页自动扫描完成。
4. 点击“开始浏览”进入随机浏览。
5. 浏览时可使用以下操作：

| 操作 | 效果 |
|---|---|
| 单击 | 显示或隐藏顶部/底部控制栏 |
| 上滑 | 下一张 |
| 下滑 | 上一张 |
| 双击 | 收藏或取消收藏当前媒体 |
| 长按 | 查看文件详情 |

## 收藏

- 双击媒体后会保存收藏状态。
- 首页“查看收藏”按钮会显示当前保存的收藏数量。
- 收藏浏览模式只显示当前已扫描目录中仍然可访问的收藏媒体。
- 如果收藏文件已删除、所在文件夹未添加，或文件夹授权失效，应用会给出提示。

## 构建

在 Android Studio 中打开 `android/` 目录，或通过命令行构建：

```bash
cd android
./gradlew assembleDebug
```

Windows：

```powershell
cd android
.\gradlew.bat assembleDebug
```

APK 输出路径：

`android/app/build/outputs/apk/debug/app-debug.apk`

## 技术栈

| 层 | 技术 |
|---|---|
| UI | Jetpack Compose + Material3 |
| 图片 | Coil 2.5 |
| 视频 | Media3 / ExoPlayer 1.2 |
| 文件访问 | Storage Access Framework |
| 设置存储 | SharedPreferences |
| 构建 | Gradle + Kotlin DSL |

## 许可

本项目使用 [GNU Affero General Public License v3.0](LICENSE) 许可证发布。

SPDX-License-Identifier: AGPL-3.0-only

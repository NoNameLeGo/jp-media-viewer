# JP Media Viewer

一个简洁的 Android 随机媒体浏览器——选择文件夹，上划切换，随心浏览。

## 功能

- **文件夹选择**：通过系统文件选择器（SAF）自由选择一个或多个包含图片/视频的文件夹
- **.nomedia 支持**：可选择是否遵守 `.nomedia` 文件，灵活控制扫描范围
- **随机浏览**：自动随机排序，每次打开都有新体验
- **上划切换**：上滑手势快速切换到下一个媒体项，单手操作流畅自然
- **图片 & 视频**：图片使用 Coil 加载，视频使用 ExoPlayer 自动循环播放
- **Material You**：遵循 Android 12+ 莫奈取色规范，主题随壁纸自动适配

## 截图

（待补充）

## 下载

从 [Releases](https://github.com/NoNameLeGo/jp-media-viewer/releases) 页面下载最新 APK。

## 构建

在 Android Studio 中打开 `android/` 目录，或通过命令行：

```bash
cd android
./gradlew assembleDebug
```

APK 输出路径：`android/app/build/outputs/apk/debug/app-debug.apk`

## 技术栈

| 层 | 技术 |
|---|---|
| UI | Jetpack Compose + Material3 |
| 图片 | Coil 2.5 |
| 视频 | Media3 / ExoPlayer 1.2 |
| 文件访问 | Storage Access Framework |
| 构建 | Gradle + Kotlin DSL |

## 许可

[MIT](LICENSE)

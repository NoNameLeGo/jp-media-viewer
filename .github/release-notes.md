APK 自动构建版本 beta0.0.8。此版本开始改为稳定 release 签名，后续同签名版本可直接覆盖安装。

### 变更内容
- 发布签名：Actions 使用 GitHub Secrets 还原 release keystore 并构建签名 release APK
- 覆盖安装：从 beta0.0.8 开始，后续 release 签名版本可直接覆盖安装
- 输出命名：Release 附件改为 `jp-media-viewer-beta0.0.8-release.apk`
- 版本元数据：应用版本更新为 beta0.0.8

### 注意
- 之前的 debug 签名版本无法被 release 签名 APK 直接覆盖；需要先卸载旧版再安装 beta0.0.8。

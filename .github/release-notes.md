APK 自动构建版本 beta0.7.0。本次发布启用 R8 代码混淆和资源压缩。

### 优化内容
- **启用 R8 混淆**：release 构建开启 `isMinifyEnabled` 和 `isShrinkResources`，大幅减小 APK 体积
- **ProGuard 规则**：为 Compose、Coil、Media3、Coroutines、DocumentsContract 添加保留规则，确保运行时不被误删

### 预期效果
- APK 体积显著减小（R8 移除未使用代码和资源）
- 运行时性能微幅提升（R8 代码优化）

### 注意
- 本版本不新增媒体权限，仍只访问用户通过系统文件夹选择器授权的目录。

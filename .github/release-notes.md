APK 自动生成版本 beta0.7.7，主要更新为代码质量改进和扫描性能优化。

### 改进
- **代码审查修复**：修复 VideoPlayer.kt 中 `derivedStateOf` 误用，改用 `rememberUpdatedState` 捕获回调引用
- **扫描异常处理**：`MediaScanner.queryChildren` 区分 `SecurityException` 与其他异常，并记录异常日志
- **扫描性能优化**：移除扫描回调中冗余的 `withContext(Dispatchers.Main)` 切换，减少线程调度开销
- **代码重构**：提取 `MediaBrowserState` 类，添加单元测试，清理死代码

### 注意
- 本版本不需要媒体权限，只允许用户通过系统文件选择器授权的目录

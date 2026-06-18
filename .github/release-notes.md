APK 自动构建版本 beta0.7.4。本次发布包含 5 项核心性能优化。

### 优化内容
- **启用 R8 混淆**：release 构建开启代码混淆和资源压缩，大幅减小 APK 体积
- **SAF 扫描器重写**：替换 DocumentFile 为直接 ContentResolver 查询，扫描速度提升 10-20 倍
- **ExoPlayer 实例复用**：单实例 + 动态切换 MediaItem，避免频繁创建/销毁播放器
- **derivedStateOf 优化**：visibleItems 仅在依赖状态变化时重算，消除手势动画期间的无效排序
- **URI 字符串预缓存**：MediaItem 预缓存 uriString，减少热路径中的重复 toString() 分配

### 修复
- **生命周期感知**：视频播放器后台自动暂停，前台恢复，修复分屏音频泄漏
- **构建修复**：LocalLifecycleOwner 引用修正为 compose.ui.platform 包路径

### 注意
- 本版本不新增媒体权限，仍只访问用户通过系统文件夹选择器授权的目录。

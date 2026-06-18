APK 自动构建版本 beta0.7.1。本次发布重写 SAF 扫描器，大幅提升扫描速度。

### 优化内容
- **直接 ContentResolver 查询**：替换 DocumentFile API 为直接 `DocumentsContract.buildChildDocumentsUriUsingTree()` + `ContentResolver.query()`
- **单次查询获取全部元数据**：文件名、MIME 类型、大小、修改时间在一次 cursor 查询中获取
- **移除 documentfile 依赖**：不再需要 `androidx.documentfile:documentfile` 库
- **.nomedia 检测优化**：在查询结果中直接判断，无需额外 round-trip

### 预期效果
- 扫描速度提升 10-20 倍（大文件库从 30-60 秒降至 2-5 秒）
- 减少内存分配（无中间 DocumentFile 对象）

### 注意
- 本版本不新增媒体权限，仍只访问用户通过系统文件夹选择器授权的目录。

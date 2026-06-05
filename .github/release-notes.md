APK 自动构建版本 beta0.1.5-crashfix-test。此版本用于验证覆盖升级启动闪退和添加文件夹扫描闪退修复，并继续使用稳定 release 签名。

### 修复内容
- 扫描稳定性：将 SAF 扫描改为显式栈遍历，避免深层目录递归导致闪退
- 元数据保护：为 `fromTreeUri`、`uri`、`name`、`type`、`length`、`lastModified`、`isFile`、`isDirectory`、`listFiles`、`.nomedia` 检查增加异常保护，单个异常文件或目录只会被跳过并计入失败目录
- 版本元数据：应用版本更新为 beta0.1.5-crashfix-test，versionCode 更新为 17

### 版本信息
- Release APK 继续使用稳定签名，支持从 beta0.0.8 及之后 release 签名版本覆盖安装

### 注意
- 如果设备上安装的是 beta0.0.7 或更早 debug 签名版本，仍需先卸载旧版再安装 release 签名 APK。

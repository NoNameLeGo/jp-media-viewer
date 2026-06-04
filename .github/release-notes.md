APK 自动构建版本 beta0.1.5-fix。此版本用于验证添加文件夹后闪退修复，并继续使用稳定 release 签名。

### 修复内容
- 扫描稳定性：为 SAF 文件元数据读取增加保护，避免部分文件/目录读取 `name`、`type`、`length`、`lastModified`、`isFile`、`isDirectory` 时抛异常导致闪退
- 文件日期：无法读取修改时间时显示“未知”，不再中断扫描
- 版本元数据：应用版本更新为 beta0.1.5-fix，versionCode 更新为 16

### 版本信息
- Release APK 继续使用稳定签名，支持从 beta0.0.8 及之后 release 签名版本覆盖安装

### 注意
- 如果设备上安装的是 beta0.0.7 或更早 debug 签名版本，仍需先卸载旧版再安装 release 签名 APK。

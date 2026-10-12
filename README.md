# 1.21.1 NeoForge 模板

这是一个无 Mixin、无自定义内容的最小 NeoForge 工作区，使用 NeoForge 21.1.233 与 Java 21。

```powershell
$env:JAVA_HOME = 'C:\Users\30819\.jdks\ms-21.0.11'
.\gradlew.bat build
.\gradlew.bat runClient
```

复制项目和批量改名请在上一级目录运行 `new-mod-from-template.ps1`。

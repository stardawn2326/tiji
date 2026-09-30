# 题迹开发与验证

产品功能介绍见仓库根目录 [README](../README.md)。

## 环境

- JDK 17。
- Android SDK：compileSdk / targetSdk 34，最低 API26。
- Gradle 使用仓库提供的 Wrapper。
- Kotlin、Jetpack Compose、Material 3、Room、DataStore、Android Keystore、KaTeX 与本地 PaddleOCR 组件。

配置本机 `local.properties` 的 `sdk.dir`，或使用已配置的 Android SDK 环境。`local.properties`、密钥、构建输出和用户附件不提交到 Git。

## 常规检查

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Linux / macOS 使用 `./gradlew`。GitHub Actions 同时执行编译、JVM、Lint、debug 打包和 API30 设备检查。

## 使用隔离应用进行设备回归

设备测试包含会清空数据的夹具。使用审计 init 脚本，将测试安装到 `com.tiji.mistakes.audit`，保留设备上原应用的数据。

```powershell
./gradlew.bat -I scripts/architecture-audit.init.gradle :app:compileDebugUnitTestKotlin :app:exportAuditClasspath :app:connectedDebugAndroidTest --no-parallel --max-workers=1
```

Windows 中文路径下，若 Gradle 测试工作进程出现类加载失败，可在编译后使用导出的测试 classpath：

```powershell
python scripts/run-audit-unit-tests.py --java C:/path/to/jdk17/bin/java.exe
```

此执行器运行同一批已编译的 JUnit 类；它不代替设备、Lint 或 release 检查。已有离线依赖时可添加 `--offline`。

## Release

```powershell
./gradlew.bat :app:assembleRelease
```

当前安装包使用历史证书保持覆盖安装连续性；正式发布签名通过 `TIJI_SIGNING_STORE_FILE`、`TIJI_SIGNING_STORE_PASSWORD`、`TIJI_SIGNING_KEY_ALIAS`、`TIJI_SIGNING_KEY_PASSWORD` 配置，并用 `TIJI_REQUIRE_RELEASE_SIGNING=true` 启用缺失签名配置即失败的检查。具体门槛和制品验证见 `scripts/verify-release.sh`。

## 源码

| 路径 | 内容 |
| --- | --- |
| `app/src/main/java/com/tiji/mistakes/data/` | Room、查询、Repository、偏好 |
| `app/src/main/java/com/tiji/mistakes/domain/` | 复习、知识点、计划、备份和资产规则 |
| `app/src/main/java/com/tiji/mistakes/service/` | AI、OCR、持久化任务、PDF、备份、图片处理 |
| `app/src/main/java/com/tiji/mistakes/ui/` | Compose 页面、导航、显示状态与公式缓存 |
| `app/src/test/`、`app/src/androidTest/` | JVM 与设备回归 |
| `tools/` | 使用的 OCR 组件源码 |

详细架构检查、变更与验证记录保存在本目录对应日期的文档中。

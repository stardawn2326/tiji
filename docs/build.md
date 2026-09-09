# 题迹构建说明

## 环境

- JDK：工程内 Temurin 17，位于 `code/.jdk17/`。
- Android SDK：工程内 `code/.android-sdk/`，已安装 API 35、Build Tools 35.0.0、Build Tools 34.0.0 和 Platform Tools 37.0.1。
- Gradle：标准 Gradle Wrapper 8.9；Windows 使用 `gradlew.bat`，macOS/Linux 使用 `./gradlew`。首次运行会按 `gradle-wrapper.properties` 下载发行包。
- Android Studio 不是构建必需项，可用 Android Studio 打开项目进行图形化调试。

## 构建 Debug APK

在 PowerShell 中从项目根目录执行：

```powershell
$projectRoot = (Get-Location).Path
$env:JAVA_HOME = Join-Path $projectRoot 'code/.jdk17'
$env:ANDROID_SDK_ROOT = Join-Path $projectRoot 'code/.android-sdk'
$env:ANDROID_USER_HOME = Join-Path $projectRoot 'code/.android-user'
$env:GRADLE_USER_HOME = Join-Path $projectRoot 'code/.gradle'
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + (Join-Path $env:ANDROID_SDK_ROOT 'platform-tools') + ';' + $env:Path
.\gradlew.bat :app:assembleDebug --no-daemon
```

从 GitHub 克隆时可以使用系统 JDK 17 与 Android SDK，不要求存在 `code/` 下的本地工具链。仓库内 `.github/workflows/android.yml` 会在每次分支推送和 PR 上运行测试、lint 与 Debug APK 构建。

产物为 `app/build/outputs/apk/debug/app-debug.apk`。交付时复制到 `outputs/apk/tiji-debug-v0.2.0.apk`。

## 验证

```powershell
$apk = 'outputs/apk/tiji-debug-v0.2.0.apk'
& 'code/.android-sdk/build-tools/35.0.0/apksigner.bat' verify --verbose $apk
Get-FileHash $apk -Algorithm SHA256
.\gradlew.bat :app:lintDebug --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

若 Gradle 测试工作进程在中文路径下报告 `GradleWorkerMain` 找不到，可临时使用 `subst T: <项目绝对路径>` 映射无中文盘符后执行测试，结束后运行 `subst T: /D` 移除映射。

当前 Debug APK 使用 Android 默认 debug 签名，仅用于开发测试与侧载，不用于正式发布。

# 题迹 UI v1.2 改动详情

日期：2026-09-10

分支：`codex/ui-soft-academic-v2`

基线：`origin/main`
范围：按《Tiji_UI_当前验收与下一轮更改方案_2026-09-10.md》完成 UI v1.2 的稳定性与结构重构。

## 1. 本轮结果

- 将 UI 重构迁移到与 `origin/main` 有共同历史的新分支，避免把两个无关历史强行合并。
- 底部五个入口保持一级页面：首页、错题、AI 解题、复习、我的。
- `我的` 不再复用旧设置首页，而是提供产品化的设置入口页；具体设置继续从二级设置页进入。
- `TijiApp.kt` 从单体页面文件拆分为按业务域组织的页面与导航文件，保留现有业务能力并降低后续迭代耦合。

## 2. 复习调度与到期状态

文件：

- `app/src/main/java/com/tiji/mistakes/domain/ReviewScheduler.kt`
- `app/src/main/java/com/tiji/mistakes/ui/MistakeViewModel.kt`
- `app/src/main/java/com/tiji/mistakes/ui/TijiApp.kt`

改动：

- 新增 `ReviewPreview(grade, intervalDays, nextReviewAt, masteryAfter)`。
- `preview()` 成为间隔与掌握度计算的唯一来源；`schedule()` 直接复用预览结果，避免按钮文案和实际排程不一致。
- 复习卡片的“明天 / N 天后”由预览结果计算，不再使用固定等级文案。
- 复习日期统一落到本地午夜，并以本地日期差计算既有间隔，减少夏令时或时区变化造成的偏差。
- ViewModel 增加 30 秒复习时钟流；应用恢复前台、设置复习计划、完成复习、重置数据时主动刷新到期列表。

## 3. UI 结构与一级导航

新增或拆分文件：

- `ui/home/HomeScreen.kt`
- `ui/library/LibraryScreen.kt`
- `ui/library/ConceptMistakeCard.kt`
- `ui/detail/MistakeDetailScreen.kt`
- `ui/capture/CaptureScreen.kt`
- `ui/solve/AiSolveScreen.kt`
- `ui/review/ReviewScreen.kt`
- `ui/review/ReviewQuestionScreen.kt`
- `ui/settings/SettingsScreen.kt`
- `ui/settings/MyScreen.kt`
- `ui/navigation/TijiNavGraph.kt`

稳定性与可测性：

- 五个底部入口加入稳定测试标签：`nav_home`、`nav_library`、`nav_solve`、`nav_review`、`nav_profile`。
- 选中态覆盖“我的”二级设置页，返回和深层路由不会错误地丢失一级入口状态。
- 页面拆分后为跨域共享的图片编辑、PDF 预览、内容块图片、AI 历史等组件补充显式依赖，避免隐式依赖单体文件。
- `MyScreen` 使用统一的页面标题、分组卡片和至少 56dp 的设置行，行点击具备语义信息。
- 保留并延续现有主题令牌与 Compose 动效约束；没有新增无意义的持续动画或缩放到 0 的进入动画。

## 4. Room 与备份格式

文件：

- `app/src/main/java/com/tiji/mistakes/data/AppDatabase.kt`
- `app/src/main/java/com/tiji/mistakes/service/BackupService.kt`
- `app/build.gradle.kts`
- `app/schemas/com.tiji.mistakes.data.AppDatabase/7.json`
- `app/schemas/com.tiji.mistakes.data.AppDatabase/8.json`
- `app/schemas/com.tiji.mistakes.data.AppDatabase/9.json`

改动：

- Room 数据库版本提升到 v9，并启用 `exportSchema = true` 与 KSP schema 输出。
- 暴露可测试的 v7→v9、v8→v9 迁移路径；新增迁移测试验证稳定 ID、作答内容、错误原因、复习次数与下次复习时间。
- 备份格式提升到 schema 2，`minReaderSchemaVersion = 2`，继续保留旧格式读取分支。
- 备份数据继续包含 `userAnswer` 与 `errorReason`，避免 UI 重构造成学习记录丢失。

## 5. CI

新增：`.github/workflows/android.yml`

CI 在 JDK 17 和 Android API 35 环境执行：

```text
:app:compileDebugKotlin
:app:compileDebugAndroidTestKotlin
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
```

触发条件为推送到 `main` / `codex/**` 分支或针对 `main` 的 Pull Request。

## 6. 验证记录

已完成：

- Debug Kotlin 编译：通过。
- 单元测试：通过；12 个测试类均正常执行。
- Android 测试源码编译：通过。
- Lint：通过。
- Debug APK 构建：通过。
- 模拟器 `Tiji_API_35` / `emulator-5554` instrumented tests：22 个完成，0 个失败，4 个既有条件性测试跳过；新增导航、字体可访问性与 Room 迁移测试通过。
- `git diff --check`：通过。

Windows 注意事项：项目目录的中文长路径会使 Gradle 测试执行器误报测试类 `ClassNotFoundException`；使用系统提供的临时无中文盘符运行，同一份源码的单元测试全部通过。该路径问题不改动源码，也不会影响 GitHub Actions 的 Linux 环境。

APK 输出：


`app/build/outputs/apk/debug/tiji-v1.0.0-debug.apk`

大小：21,281,367 bytes
SHA-256：`F8D4338C1C0C63DBF84998ACC563C0CFEF3A37FC779717328C49C52EB2DC38D2`

本文件只记录源码与验证详情；APK 构建产物不纳入源码提交。

## 7. 提交边界

- 本轮提交只包含 v2 分支上的源码、测试、Room schema、CI 与本变更说明。
- 之前工作区中未纳入本轮的用户改动已保存在本地 Git stash，没有覆盖或删除。
- 推送目标为 `origin` 的 `codex/ui-soft-academic-v2` 分支，不直接改写 `main`。

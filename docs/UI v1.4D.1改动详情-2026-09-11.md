# Tiji UI v1.4D.1 改动详情

日期：2026-09-11
依据：`C:\Users\23260\Downloads\Tiji_UI_v1.4D_验收与v1.4E下一步方案_2026-09-11.md`

## 1. 本轮范围

本轮只执行方案中 v1.4D.1 的 P0「专项复习会话重建与状态正确性」热修复，目标是让专项复习在 Activity 重建后保持题目位置、已选反馈和本轮总结的一致性。

v1.4E 的新规划器、统计面板、导航重构和其他新增功能未在本轮提前实现。Room 版本保持 10，备份格式保持 3，没有新增数据表、没有新增迁移脚本。

## 2. 修复的问题

旧实现把专项复习的当前位置、反馈选择、总结显示状态和待写入任务部分保存在 Compose 层。Activity 重建后，这些短生命周期状态可能重新初始化；总结同时按时间窗口查询记录，还可能把同一时间段的其他记录混入本轮统计。

本轮将会话状态收敛到 `MistakeViewModel`，并用 `SavedStateHandle` 保存可重建的轻量数据；总结改为按本轮实际写入的 `ReviewRecord` ID 精确恢复。

## 3. 具体改动

### 3.1 会话状态持久化

新增 `ReviewSessionUiState`，保存以下字段：

- `sessionKey`：当前专项复习路由实例的唯一标识；
- `startedAt`：会话开始时间；
- `reviewIds`：本轮题目顺序；
- `currentIndex`：当前题目下标；
- `gradesByMistake`：题目到反馈等级的映射；
- `recordedReviewIds`：本轮成功写入的复习记录 ID；
- `summaryVisible`：是否已经进入本轮总结。

`MistakeViewModel` 通过 `SavedStateHandle` 保存和恢复上述数据。重建后 UI 从恢复后的 `currentIndex` 和 `reviewIds` 推导当前题，不再依赖 Compose 中的临时 `remember` 状态。

### 3.2 总结按精确记录恢复

- `ReviewRecordDao` 新增 `listByIds` 查询；
- `MistakeRepository` 新增按记录 ID 批量读取接口；
- `MistakeViewModel.review` 回传实际写入的 `ReviewRecordEntity`；
- 点击「查看总结」时，ViewModel 等待本轮写入任务完成，再按 `recordedReviewIds` 读取记录；
- 重建总结页时，如果内存总结缓存不存在，重新按同一组记录 ID 加载；
- 总结页在恢复期间显示明确的加载态，不回退到题目页，也不使用时间窗口推断本轮记录。

### 3.3 同题重复点击保护

同一 `sessionKey` 下，题目第一次提交反馈时会先在 ViewModel 中预占该题；后续快速重复点击直接忽略。数据库写入成功后记录真实 ID；写入失败时释放预占并移除暂存反馈，允许用户重试。

### 3.4 路由实例隔离

专项复习路由新增 UUID `sessionId`。每次从知识点详情重新进入专项复习都会生成新的会话键，避免旧的 `SavedStateHandle` 状态被错误复用到新一轮复习。

### 3.5 既有每日复习兼容

本轮状态收敛仅作用于知识点专项复习。每日复习继续使用原有题目流转和计划状态逻辑；公共题目页面仍保留原有返回、移出计划和反馈回调行为。

## 4. 修改文件

- `app/src/main/java/com/tiji/mistakes/domain/ReviewSession.kt`：新增可重建会话状态模型；
- `app/src/main/java/com/tiji/mistakes/data/ReviewRecordDao.kt`：新增按记录 ID 查询；
- `app/src/main/java/com/tiji/mistakes/data/MistakeRepository.kt`：新增按记录 ID 读取仓储接口；
- `app/src/main/java/com/tiji/mistakes/ui/MistakeViewModel.kt`：集中管理会话状态、保存恢复、写入屏障和重复点击保护；
- `app/src/main/java/com/tiji/mistakes/ui/navigation/Routes.kt`：专项复习路由增加会话实例 ID；
- `app/src/main/java/com/tiji/mistakes/ui/navigation/TijiNavGraph.kt`：解析并传递会话键；
- `app/src/main/java/com/tiji/mistakes/ui/review/ReviewQuestionScreen.kt`：改为消费 ViewModel 会话状态，增加总结恢复加载态；
- `app/src/androidTest/java/com/tiji/mistakes/FocusedReviewRecreationTest.kt`：新增 v1.4D.1 四项重建与防重复仪器测试。

## 5. 验收覆盖

新增 `FocusedReviewRecreationTest` 覆盖方案要求的四类场景：

1. 中途完成第一题后重建：已记录反馈仍存在，继续完成第二题后总结为 2 条；
2. 切换到第二题后重建：仍停留在第 2 / 2 题；
3. 进入总结后重建：总结从本轮记录 ID 恢复，Good/Hard 等统计不变化；
4. 同一反馈连续点击 3 次：每道题最多写入 1 条复习记录。

## 6. 本地验证结果

以下命令使用 JDK 17、项目配置的 Android SDK 和 API 35 模拟器 `Tiji_API_35 (emulator-5554)` 执行：

- `:app:testDebugUnitTest`：通过；
- `:app:lintDebug`：通过；0 error，保留项目原有 2 个 warning（OCR 动态加载、旧 SDK 判断）；
- `:app:assembleDebug`：通过；
- `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tiji.mistakes.FocusedReviewRecreationTest`：4 / 4 通过；
- `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tiji.mistakes.FocusedReviewUiTest`：1 / 1 通过；
- `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tiji.mistakes.ReviewQuestionUiTest`：1 / 1 通过；
- 完整 `:app:connectedDebugAndroidTest`：构建成功，0 failed，4 个既有条件测试 skipped。

本轮只提交源码和改动详情文档，APK 仅作为本地构建验证产物，不纳入 Git 提交。

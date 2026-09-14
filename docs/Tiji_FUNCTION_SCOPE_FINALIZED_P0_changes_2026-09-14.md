# Tiji FUNCTION_SCOPE_FINALIZED P0 收口改动详情

## 文档信息

- 版本：1.1
- 日期：2026-09-14
- 对照方案：`C:\Users\23260\Downloads\Tiji_FUNCTION_SCOPE_FINALIZED验收与下一步_P0最终收口方案_2026-09-14.md`
- 目标仓库：[stardawn2326/tiji](https://github.com/stardawn2326/tiji)

## 本轮目标

完成 P0 最终收口：让未来复习预测与今日正式计划共用同一选题规则，保留逾期题的跨日滚动；让未来日期使用对应工作日的科目偏好；移除产品中的薄弱度计算和“薄弱优先”入口；让首页与知识点页使用同一份结构化掌握统计；在既有 API30 模拟器上完成构建、安装和自动化回归。

## 代码改动

### 1. 复习计划和未来预测

- `DailyStudyPlanner` 新增内部共享选择器 `selectDueForDay(eligible, dailyLimit, subjectPreferences, latestRecords)`。
- 今日正式计划和 `FutureReviewPlan` 均使用该选择器，排序、科目轮转、每日上限和稳定排序规则保持一致。
- 恢复同一排序键下的复习记录优先级：最近一次记录为 `FORGOT` 的题先于 `GOOD`/空记录；等级相同时按最近复习时间倒序。今日计划和未来预测均传入同一份 `latestRecords`，避免首页计划与复习页展示顺序回归。
- 未来预测先排除今天正式计划已经选中的 ID，再按目标日期截止时刻收集所有到期候选；当天未选中的逾期题会进入后续日期的候选池。
- 每个 ID 在三日预测窗口内最多出现一次，不会为预测结果虚构未来等级或下一次复习时间。
- 预测不再复用今天的科目配置；`reviewSubjectsRaw` 会按每个目标日期的 `dayOfWeek.value` 重新解析。
- `DailyStudyPlan` 删除已废弃的 `weakBoost`、`optional` 字段，计划只保留到期队列和解释文本。

### 2. 知识点产品边界

- 从 `TijiApp`、导航状态和复习会话上下文中移除 `WeaknessCalculator` 与 `weaknessInsights`。
- 知识点列表只保留名称、科目、错题数、已掌握数和掌握率；排序仅保留“错题数”和“名称”。
- 知识点详情移除薄弱度进度条、风险/原因文案和忘记次数指标，保留掌握统计、近 30 天复习记录、最近复习历史、关联错题、专项复习和 PDF 导出。
- 删除无产品消费者的 `WeaknessCalculator`、`KnowledgeAnalyticsIndex` 及其对应 JVM 测试。

### 3. 统一统计口径

- 首页已有的 `MistakeProgressCalculator` 产出 `KnowledgePointProgress(stableId, name, total, mastered, masteryRate)`。
- 知识点列表和详情直接消费 `progressSummary.bySubject[*].knowledgePoints`，不再维护另一套知识点分析模型。
- Android 测试宿主也改用相同计算器和 `KnowledgePointProgress`，确保验收数据路径与正式 UI 一致。

## 验证结果

### 静态与构建

- `app/src/main` 旧链路扫描：`WeaknessCalculator|weaknessInsights|薄弱优先|薄弱补强|WEAK_BOOST` 命中数为 0。
- `:app:testDebugUnitTest`：通过。
- `:app:compileDebugAndroidTestKotlin`：通过。
- `:app:lintDebug`、`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`:app:assembleRelease`：通过。
- 新增并通过未来计划用例：逾期滚动、今天已选排除、目标工作日偏好、归档/删除/退出计划过滤、跨日去重、输入顺序确定性。
- 新增并通过排序回归用例：相同到期时间/科目/掌握度时 `FORGOT` 优先；相同等级时最近复习优先；未来预测复用同一排序规则。

### API30 设备回归

- 唯一验证设备：`emulator-5554`，Android 11，API 30。
- `:app:connectedDebugAndroidTest`：Starting 71 tests，4 skipped，0 failed；任务 `BUILD SUCCESSFUL`。
- 本地测试记录与 CI 记录分开维护；本文件中的数字仅表示本地 API30 回归。
- 未创建、启动或运行 API35 模拟器；未进行视觉检查。

### 安装产物

- 已安装包：`app/build/outputs/apk/debug/tiji-v1.0.0-debug.apk`
- 包名：`com.tiji.mistakes`
- API30 安装命令使用 `adb install -r -d -g`，设备返回 `Success`。
- Debug APK SHA-256：`7BE36F8DF6B161675BA8EFEB4CAF49242C57B915AA5BAE5A01A5AD1A82468F5C`

## AI 外部能力边界

本轮只收口本地计划、知识点统计和 Android UI 状态；外部 AI provider 未在本轮进行联网或真实服务验收，`AI_EXTERNAL_PROVIDER_GATE = NOT VERIFIED`。

## GitHub 发布记录

以下为本轮分支推送和 PR 检查记录；本轮方案明确要求 P0 回归通过后合并 PR11，并对合并后的 exact `main` SHA 执行 post-merge CI。

- 工作分支：`codex/function-final-p0-close`
- 功能提交（恢复复习记录排序）：`999685d420914e6f6d6adbf605b6518c3bd75f3a`
- PR：[ #11 · feat: close function scope P0 review flow](https://github.com/stardawn2326/tiji/pull/11)
- 本轮功能提交对应的 push CI：通过；run [`34814934577`](https://github.com/stardawn2326/tiji/actions/runs/34814934577)。
- 本轮功能提交对应的 PR CI：通过；run [`34814936231`](https://github.com/stardawn2326/tiji/actions/runs/34814936231)。两次均通过编译/package 与 API30 instrumentation。
- 本地文档提交后，PR11 会再次执行同一组检查；合并前以 PR11 最新 head 的两项检查为准。
- 合并前 `main` SHA：`cf39d7a87bc5463ea116c31f432cc355335b8cff`；P0 回归已通过，按方案继续合并 PR11。
- 合并后的 `main` 提交和 post-merge CI 将在合并完成后回填到交付记录。

## 范围说明

本轮没有修改版本号、发布标签或 release；没有引入新的 API35 验证环境。验收以代码结构、自动化测试、API30 安装结果和远端提交校验为准。

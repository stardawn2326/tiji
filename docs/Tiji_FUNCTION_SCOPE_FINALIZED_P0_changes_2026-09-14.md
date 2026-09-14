# Tiji FUNCTION_SCOPE_FINALIZED P0 收口改动详情

## 文档信息

- 版本：1.0
- 日期：2026-09-14
- 对照方案：`C:\Users\23260\Downloads\Tiji_FUNCTION_SCOPE_FINALIZED验收与下一步_P0最终收口方案_2026-09-14.md`
- 目标仓库：[stardawn2326/tiji](https://github.com/stardawn2326/tiji)

## 本轮目标

完成 P0 最终收口：让未来复习预测与今日正式计划共用同一选题规则，保留逾期题的跨日滚动；让未来日期使用对应工作日的科目偏好；移除产品中的薄弱度计算和“薄弱优先”入口；让首页与知识点页使用同一份结构化掌握统计；在既有 API30 模拟器上完成构建、安装和自动化回归。

## 代码改动

### 1. 复习计划和未来预测

- `DailyStudyPlanner` 新增内部共享选择器 `selectDueForDay(eligible, dailyLimit, subjectPreferences)`。
- 今日正式计划和 `FutureReviewPlan` 均使用该选择器，排序、科目轮转、每日上限和稳定排序规则保持一致。
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

### API30 设备回归

- 唯一验证设备：`emulator-5554`，Android 11，API 30。
- `:app:connectedDebugAndroidTest`：Starting 71 tests，4 skipped，0 failed；任务 `BUILD SUCCESSFUL`。
- 本地测试记录与 CI 记录分开维护；本文件中的数字仅表示本地 API30 回归。
- 未创建、启动或运行 API35 模拟器；未进行视觉检查。

### 安装产物

- 已安装包：`app/build/outputs/apk/debug/tiji-v1.0.0-debug.apk`
- 包名：`com.tiji.mistakes`
- API30 安装命令使用 `adb install -r -d -g`，设备返回 `Success`。
- Debug APK SHA-256：`FAEA1289C5FBE28E51123FF87B07188E049D40BD79BE28AE0358A5BEABAAD0D5`

## AI 外部能力边界

本轮只收口本地计划、知识点统计和 Android UI 状态；外部 AI provider 未在本轮进行联网或真实服务验收，`AI_EXTERNAL_PROVIDER_GATE = NOT VERIFIED`。

## GitHub 发布记录

以下为本轮分支推送和 PR 检查记录；共享 `main` 的合并需要单独授权。

- 工作分支：`codex/function-final-p0-close`
- 功能提交：`95fb280c1bdac3091d1c432962e67ff9d7b771d8`
- PR：[ #11 · feat: close function scope P0 review flow](https://github.com/stardawn2326/tiji/pull/11)
- PR CI：通过；push run [`34811064733`](https://github.com/stardawn2326/tiji/actions/runs/34811064733)，PR run [`34811094057`](https://github.com/stardawn2326/tiji/actions/runs/34811094057)，两次均通过编译/package 与 API30 instrumentation。
- 最新文档回填 head 的 CI：通过；push run [`34811603094`](https://github.com/stardawn2326/tiji/actions/runs/34811603094)，PR run [`34811606279`](https://github.com/stardawn2326/tiji/actions/runs/34811606279)，两次均通过编译/package 与 API30 instrumentation。
- `main` 合并提交：尚未执行；当前请求已完成分支推送和 PR 创建，未直接改写共享 `main`。
- 合并后 `main` CI：待合并后执行。
- 远端文件树和文件内容校验：PASS；分支 ref 为 `6592f7831d77f665d2b56bb362035e5f1dd775ff`，文档 blob 为 `76d36116f8d12512a1e53b7c38bb26154a8ac122`，均与本地提交一致。

## 范围说明

本轮没有修改版本号、发布标签或 release；没有引入新的 API35 验证环境。验收以代码结构、自动化测试、API30 安装结果和远端提交校验为准。

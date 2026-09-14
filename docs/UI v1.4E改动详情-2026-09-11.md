# 题迹 Tiji UI v1.4E 改动详情

日期：2026-09-11
依据：`C:\Users\23260\Downloads\Tiji_UI_v1.4D.1_验收与v1.4E下一步方案_2026-09-11.md`
Repository：`stardawn2326/tiji`
分支：`codex/v1.4e-study-planning`

## 1. 本轮结论

已按 v1.4E 方案完成“统一复习会话 + 可解释每日学习计划”的实现，并完成 API 35 模拟器回归。

本轮交付包括：

- TODAY_PLAN、KNOWLEDGE_POINT、LIBRARY_SELECTION 三个入口统一使用同一套 Review Session；
- Review Center 展示到期、薄弱补强、可选巩固三组今日计划；
- 计划使用 `dailyReviewLimit`，固定按 Due → Weak Boost → Optional 装载；
- 未来 7 天按 `LearningCalendar.localDate()` 统计本地自然日负载；
- 错题库多选可直接开始统一复习，并在总结后返回错题库；
- 三个入口共用同一总结页面，统计只使用本轮真实 `ReviewRecord` ID；
- 增加会话状态机、规划器、自然日负载和搜索/规划性能观察测试。

## 2. 统一 Session Engine

`app/src/main/java/com/tiji/mistakes/domain/ReviewSession.kt` 新增并统一了以下模型：

```text
ReviewSessionPlan
ReviewSessionProgress
ReviewSessionSummary
ReviewSessionUiState
ReviewSessionController
```

会话来源仍可区分入口，但不再维护三套页面状态：

```text
TODAY_PLAN        今日计划
KNOWLEDGE_POINT  专项复习
LIBRARY_SELECTION 错题选择
```

状态转换为：

```text
CREATED → IN_PROGRESS → COMPLETING → SUMMARY → FINISHED
```

Controller 负责纯状态转换：

- 创建时清理无效 ID 并去重；
- 只允许当前题预占反馈；
- 同题已有反馈或重复记录直接拒绝；
- 写入成功后只追加真实 `ReviewRecordEntity.id`；
- 总结只携带本轮记录 ID，不按时间窗口猜测记录。

### 2.1 状态恢复与导航线程

`MistakeViewModel` 继续用 `SavedStateHandle` 保存轻量可恢复信息：

- `sessionKey`、来源、知识点上下文和返回入口；
- 题目 ID 顺序、当前位置；
- 反馈等级、真实 ReviewRecord ID；
- 当前状态和总结是否可见。

三个入口现在都导航到：

```text
review-session/{sessionId}
```

路由只携带会话 ID，不再把题目列表、知识点 ID、知识点名称拼进 URL。会话计划由 ViewModel 恢复。

回归中发现知识点队列查询完成后会在 Room 线程恢复协程；本轮已将会话创建、SavedState 写入和 `NavController.navigate` 统一切换到 `Dispatchers.Main.immediate`，避免导航条目停留在 `INITIALIZED` 并在 Activity 销毁时崩溃。

本轮保留页面原有的淡入/横向滑动过渡，不以关闭动效规避生命周期问题。

## 3. Review Center

`app/src/main/java/com/tiji/mistakes/ui/review/ReviewScreen.kt` 改为轻量复习中心：

- 顶部显示今日复习完成数和到期优先排序；
- 计划概览显示“到期复习 / 薄弱补强 / 可选巩固”数量；
- 首题卡片显示真实计划原因，并从统一会话开始或继续；
- 后续题目按计划顺序展示，不再让页面维护单独的题目详情路由；
- 未来 7 天显示今天、明天及后续本地日期的到期数量；
- 空计划仍保持原有设置引导，不生成虚假任务。

页面保留底部五个一级页面：

```text
首页 / 错题 / AI解题 / 复习 / 我的
```

进入会话后仍正确高亮“复习”一级入口。

## 4. DailyStudyPlanner 规则

`app/src/main/java/com/tiji/mistakes/domain/DailyStudyPlanner.kt` 是确定性规则规划器，不使用 AI 分数、记忆概率或考试预测。

### 4.1 输入

- 当前未归档、未删除的错题；
- 到期错题；
- 最近复习记录；
- Knowledge Point Insight 和错题-知识点关系；
- `dailyReviewLimit`；
- 当前星期对应的科目偏好；
- 当前时间和时区。

### 4.2 优先级和预算

```text
1. Due
2. Weak Boost
3. Optional
```

- Due 按 `nextReviewAt`、掌握度、最近反馈和稳定 ID 排序；
- Weak Boost 关注低掌握度、最近选择“忘记”以及高 weakness 知识点；
- Optional 优先近期新增且掌握度较低的错题；
- 先装满 Due，再使用剩余容量补强，最后才加入 Optional；
- 科目偏好只作为补强/可选项的软排序，不会挤掉到期题；
- 所有排序都有稳定 tie-break，输入列表顺序变化不会造成随机跳动。

每道已选题都会生成可解释原因，例如：

```text
今天到期
函数 · 需加强 · 上次选择困难 · 距离上次复习 3 天
近期错题，适合巩固
```

## 5. 未来 7 天负载

`FutureReviewLoad` 只统计当前仍在复习计划中的有效错题，并使用：

```text
nextReviewAt → LearningCalendar.localDate(timestamp, zoneId)
```

因此不会用固定 24 小时换算日期，能够覆盖：

- Asia/Tokyo；
- America/New_York 春季和秋季 DST 边界；
- Europe/Berlin；
- 跨月和跨年。

## 6. 错题库多选复习

`app/src/main/java/com/tiji/mistakes/ui/library/LibraryScreen.kt` 增加“开始复习”批量操作：

```text
批量选择 → 开始复习 → LIBRARY_SELECTION Session → Summary → 返回错题库
```

创建会话前会再次依据当前有效错题列表过滤 ID，排除已经删除、归档或不存在的题目；因此旧的选择状态不会把失效数据带入新会话。

总结页根据来源显示返回文案：

```text
今日计划   → 返回复习中心
专项复习   → 查看知识点
错题选择   → 返回错题库
```

## 7. 总结页统一

`app/src/main/java/com/tiji/mistakes/ui/review/ReviewQuestionScreen.kt` 现在由三个来源共用：

```text
完成 / 忘记 / 困难 / 会了 / 简单
```

页面上下文来自 `ReviewSessionContext`，统计来自 `recordedReviewIds` 对应的真实数据库记录；不再维护 Daily、Focused、Library 三套总结 UI。

## 8. 修改文件

### 生产代码

- `app/src/main/java/com/tiji/mistakes/domain/ReviewSession.kt`：统一 Session 模型、来源和状态机；
- `app/src/main/java/com/tiji/mistakes/domain/DailyStudyPlanner.kt`：每日计划、解释原因和未来 7 天负载；
- `app/src/main/java/com/tiji/mistakes/ui/MistakeViewModel.kt`：统一会话创建、恢复、评分预占、总结加载；
- `app/src/main/java/com/tiji/mistakes/ui/TijiApp.kt`：计算每日计划并将其注入导航状态；
- `app/src/main/java/com/tiji/mistakes/ui/navigation/Routes.kt`：统一 `review-session/{sessionId}` 路由；
- `app/src/main/java/com/tiji/mistakes/ui/navigation/TijiNavGraph.kt`：三个入口生成 Plan，统一进入会话页，并修复 Room 线程回主线程导航；
- `app/src/main/java/com/tiji/mistakes/ui/navigation/TijiNavGraphState.kt`：增加 DailyStudyPlan；
- `app/src/main/java/com/tiji/mistakes/ui/review/ReviewScreen.kt`：重构为 Review Center；
- `app/src/main/java/com/tiji/mistakes/ui/review/ReviewQuestionScreen.kt`：统一题目页、总结页和来源上下文；
- `app/src/main/java/com/tiji/mistakes/ui/library/LibraryScreen.kt`：增加错题库多选开始复习和边界重过滤。

### 测试代码

- `app/src/test/java/com/tiji/mistakes/ReviewSessionControllerTest.kt`；
- `app/src/test/java/com/tiji/mistakes/DailyStudyPlannerTest.kt`；
- `app/src/test/java/com/tiji/mistakes/FutureReviewLoadTest.kt`；
- `app/src/test/java/com/tiji/mistakes/MistakeSearchPerformanceBaselineTest.kt`；
- `app/src/test/java/com/tiji/mistakes/StudyPerformanceBaselineTest.kt`；
- `app/src/androidTest/java/com/tiji/mistakes/LibraryFilterTest.kt`：增加多选复习闭环用例。

## 9. 数据和范围边界

本轮不升级数据格式：

```text
Room version  = 10
Backup Schema  = 3
```

本轮只承诺 Activity recreation、配置变化和当前任务内的轻量恢复；未承诺 OS 杀进程、冷启动或重启手机后恢复未完成会话，因此没有引入 `ReviewSessionEntity`、Room 11 或新的迁移。

同理，本轮没有提前引入账号、云同步、排行榜、成就、知识图谱画布、大型 Dashboard、考试分数预测或 FTS。搜索只增加观察性 benchmark，保留现有 substring 语义。

## 10. 验证结果

环境：JDK 17、项目 Android SDK、API 35 模拟器 `Tiji_API_35`（`emulator-5554`）。

### 构建和静态检查

- `:app:compileDebugKotlin`：通过；
- `:app:compileDebugAndroidTestKotlin`：通过；
- `:app:assembleDebug`：通过；
- `:app:lintDebug`：通过，0 errors、2 个既有 warnings（动态加载 OCR native library、项目已有 SDK_INT 判断）。

### JVM 单元测试

使用项目 `docs/build.md` 中的无中文盘符映射方式执行 `:app:testDebugUnitTest --no-daemon`：

```text
137 tests
0 failures
0 errors
0 skipped
```

新增测试覆盖：

- Session 创建、移动、预占、重复记录、总结和完成状态；
- Due-first 预算、有效数据过滤、解释原因、科目偏好解析；
- 时区、DST、跨月、跨年自然日负载；
- 1k / 5k / 10k / 20k 搜索 query 构造观察；
- 10k 错题 daily planner 观察。

### Android 模拟器测试

完整命令：

```text
:app:connectedDebugAndroidTest --no-daemon
```

最终报告：

```text
42 test cases
0 failures
0 errors
4 condition-based skipped
Finished 46 tests
```

重点闭环：

- `FocusedReviewRecreationTest`：4 / 4 通过；
- `FocusedReviewUiTest`：1 / 1 通过；
- `LibraryFilterTest`：2 / 2 通过；
- 既有 Review、Navigation、Accessibility、Backup、Migration、OCR/AI 相关测试均无新增失败。

### 性能观察值

这些值是 host JVM 的观察性样本，不是 flaky CI 硬阈值：

| 场景 | p50 | p95 | worst |
| --- | ---: | ---: | ---: |
| Search query construction / 1k | 26 us | 80 us | 2714 us |
| Search query construction / 5k | 12 us | 70 us | 88 us |
| Search query construction / 10k | 13 us | 30 us | 36 us |
| Search query construction / 20k | 14 us | 41 us | 47 us |
| DailyStudyPlanner / 10k | 11919 us | 20431 us | 20431 us |

## 11. APK 验证产物

Debug APK 已由 connected test 任务构建并安装到 API 35 模拟器，源码交付不把 APK 纳入 Git：

`app/build/outputs/apk/debug/tiji-v1.0.0-debug.apk`

该 APK 仅用于本地验证，不代表正式签名发布包。

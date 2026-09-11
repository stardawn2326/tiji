# 题迹 Tiji UI v1.4D 改动详情与验收记录

日期：2026-09-11

分支：`codex/v1.4d-focused-practice`

依据：`Tiji_UI_v1.4C_验收与v1.4D下一步方案_2026-09-11.md`

## 1. 本轮目标

将 v1.4C 的知识点洞察接成可执行的学习闭环：

```text
知识点详情 → 开始专项复习 → 按确定性优先级出题
           → 写入真实 ReviewRecord → 查看本轮总结 → 返回知识点
```

同时把错题多关键词搜索从 Kotlin 全表过滤迁移到参数化 SQL，并让知识点统计、详情历史保持明确的数据边界。

本轮不新增 Room 表、不新增学习历史副本、不升级 Room 或备份格式，也不上传 APK。

## 2. 界面与交互改动

### 2.1 Knowledge Detail

- 增加唯一主 CTA：`开始专项复习`。
- CTA 只对当前知识点的结构化 `CrossRef` 关联错题生效；没有可练习错题时显示禁用态。
- CTA 下方明确说明排序规则：按到期、掌握度和最近“忘记”优先安排题目。
- 详情页继续只显示当前知识点关联的错题和最近 5 条真实复习记录。
- `近30天复习` 使用已经按自然日期边界计算的 `resolvedInsight.recentReviewCount`，不再把全历史记录当作近 30 天数量。

### 2.2 Focused Review

- 新增临时 `ReviewSessionContext`，当前支持 `TODAY_PLAN`、`KNOWLEDGE_POINT`、`LIBRARY_SELECTION` 三类来源。
- 知识点专项复习顶部显示 `知识点名称 · 专项复习` 与 `当前题号 / 总题数`；日常复习仍显示 `今日复习`。
- 专项复习隐藏“移出复习计划”，避免把一次性专项上下文误写成计划操作。
- 反馈按钮仍复用 `ReviewScheduler.preview()` 的间隔预览；实际点击仍复用现有 `MistakeRepository.recordReview()`，因此 mastery、nextReviewAt、reviewCount 和 ReviewRecord 保持同一套学习状态。
- Room 查询完成后显式切回 `Dispatchers.Main.immediate` 再导航，避免从数据库线程操作 NavController 造成生命周期崩溃。

### 2.3 Session Summary

专项复习最后一题改为 `查看总结`，总结页显示：

- 完成
- 忘记
- 困难
- 会了
- 简单

所有数字直接由本轮产生的真实 `ReviewRecordEntity` 统计；不创建临时成绩表或第二套统计状态。当前知识点卡片同时显示知识点名称与最新薄弱度标签，例如 `函数单调性 · 较弱`。提供 `查看知识点` 与 `完成` 两个返回入口，均回到进入专项复习前的知识点详情。

## 3. 专项复习队列契约

实现文件：`app/src/main/java/com/tiji/mistakes/domain/FocusedReviewQueue.kt`

队列链路固定为：

```text
KnowledgePoint.stableId
  → mistake_knowledge_points
  → 当前知识点的 active MistakeEntity
  → 当前错题的 ReviewRecordEntity
  → FocusedReviewQueue.order()
```

不再使用 `tags contains(...)` 作为回退匹配。排序为稳定的确定性比较器：

1. 已到期优先（`nextReviewAt <= now`）。
2. `mastery` 较低优先。
3. 最近一条复习记录为“忘记”优先；判断使用该题最新记录，而不是历史上任意一条记录。
4. `nextReviewAt` 更早优先。
5. `updatedAt` 更新近的优先。
6. 以本地 id、stableId 作为最终稳定排序键。

删除或归档的数据不会进入专项队列。

## 4. 搜索查询改造

实现文件：`app/src/main/java/com/tiji/mistakes/data/MistakeSearchQuery.kt`

- `MistakeRepository.observe()` 继续按逗号/中文逗号拆分关键词，并保留空查询的 active 列表行为。
- 每个关键词构造一组字段 OR：`title`、`questionText`、`userAnswer`、`answerText`、`explanation`、`note`、`subject`、`questionType`、`tags`、`errorReason`、`ocrText`。
- 多关键词组之间使用 AND，语义为“每个关键词都必须命中任一字段”。
- 使用 Room `@RawQuery` + `SupportSQLiteQuery` + bindArgs；用户输入不会拼接进 SQL。
- 保留 substring 语义，并转义 `\`、`%`、`_`；SQL 使用 `ESCAPE '\\'`。
- 查询固定排除 `deletedAt IS NOT NULL` 与 `archived = 1`。
- 按原有 `uploadedAt DESC, updatedAt DESC` 返回顺序。
- 未引入 FTS，未升级 Room schema；后续只有在基准证明 LIKE 不足时才重新评估。

## 5. 查询边界与统计性能

- `Mistake Detail` 通过数据库 `ORDER BY reviewedAt DESC, id DESC LIMIT :limit` 获取展示历史。
- `Knowledge Detail` 通过知识点关联查询获取 route-local 错题与最新 5 条记录。
- Root 不再收集完整 `ReviewRecord` 表；首页使用自然日期边界内的 bounded recent records。
- 新增 `KnowledgeAnalyticsIndex`，一次建立 `mistakesById`、`mistakeIdsByPoint`、`recentRecordsByMistake`，供 `WeaknessCalculator` 重用，避免每个知识点重复扫描全量输入。
- 大数据 JVM 回归 fixture：10,000 Mistakes、50,000 ReviewRecords、1,000 KnowledgePoints、20,000 CrossRefs；统计断言在 10 秒内完成。

数据契约保持：

```text
Room version = 10
Backup Schema = 3
```

## 6. 变更文件

### 生产代码

- `app/src/main/java/com/tiji/mistakes/domain/ReviewSession.kt`
- `app/src/main/java/com/tiji/mistakes/domain/FocusedReviewQueue.kt`
- `app/src/main/java/com/tiji/mistakes/domain/KnowledgeAnalyticsIndex.kt`
- `app/src/main/java/com/tiji/mistakes/domain/WeaknessCalculator.kt`
- `app/src/main/java/com/tiji/mistakes/data/MistakeSearchQuery.kt`
- `app/src/main/java/com/tiji/mistakes/data/MistakeDao.kt`
- `app/src/main/java/com/tiji/mistakes/data/MistakeRepository.kt`
- `app/src/main/java/com/tiji/mistakes/ui/MistakeViewModel.kt`
- `app/src/main/java/com/tiji/mistakes/ui/knowledge/KnowledgeExplorerScreen.kt`
- `app/src/main/java/com/tiji/mistakes/ui/navigation/Routes.kt`
- `app/src/main/java/com/tiji/mistakes/ui/navigation/TijiNavGraph.kt`
- `app/src/main/java/com/tiji/mistakes/ui/review/ReviewQuestionScreen.kt`

### 测试代码

- `app/src/test/java/com/tiji/mistakes/FocusedReviewQueueTest.kt`
- `app/src/test/java/com/tiji/mistakes/ReviewSessionAnalyticsTest.kt`
- `app/src/test/java/com/tiji/mistakes/MistakeSearchQueryTest.kt`
- `app/src/test/java/com/tiji/mistakes/KnowledgeAnalyticsPerformanceTest.kt`
- `app/src/androidTest/java/com/tiji/mistakes/FocusedReviewUiTest.kt`
- `app/src/androidTest/java/com/tiji/mistakes/SearchQueryTest.kt`
- `app/src/androidTest/java/com/tiji/mistakes/ReviewQuestionUiTest.kt`

## 7. 本地验收证据

执行环境：Windows、JDK 17、API 35 模拟器 `Tiji_API_35 (AVD) - 15`，设备 serial `emulator-5554`。

| 检查 | 结果 |
| --- | --- |
| `:app:testDebugUnitTest` | PASS，127 tests，0 failures，0 errors |
| `:app:compileDebugAndroidTestKotlin` | PASS |
| `:app:lintDebug` | PASS，0 errors，2 个既有 warning |
| `:app:assembleDebug` | PASS |
| `:app:connectedDebugAndroidTest` | PASS，41 tests，4 skipped，0 failures |
| API 35 专项复习端到端 | PASS：结构化知识点隔离、两题反馈、真实总结、返回知识点 |
| API 35 搜索回归 | PASS：中文、英文、公式、OCR、legacy tags、`%`、`_`、`\\`、apostrophe、删除/归档排除 |
| 大数据统计基准 | PASS：10k/50k/1k+/20k+ fixture，断言 `< 10,000 ms` |

lint 的 2 个 warning 均来自既有代码：`OcrNativeRuntime` 的动态原生库加载，以及 `AiMistakeClassificationService` 的 minSdk 版本判断；本轮没有新增 lint warning。

## 8. Debug APK 产物

本轮仅提交源码，APK 不进入 Git。构建产物：

```text
app/build/outputs/apk/debug/tiji-v1.0.0-debug.apk
size = 21,954,251 bytes
SHA-256 = 7D8A62B79A27EC959952578DBC2C68DCAED1EDAE5A8D60B1522EDA377300D154
```

## 9. 交付边界

- 不包含账号、云同步、社交、考试预测、大型 Dashboard、知识图谱画布、复杂 AI 推荐或未经校准的记忆概率。
- 不新增 Room migration，不改变 Backup Schema 3，不提交 APK 或本地测试数据库。
- GitHub 交付以本分支源码提交、推送和 Required Checks 为准；最终 PR 状态以远端页面为准。

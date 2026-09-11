# 题迹 UI v1.4F 改动详情

日期：2026-09-11
依据：`Tiji_UI_v1.4E_验收与v1.4F_AI解题核心方案_2026-09-11.md`
范围：v1.4E.1 验收问题收口、AI 解题核心链路、AI 解题页 UI 与保存流程

## 1. 本轮目标

- 在保留 V2/旧四分区兼容的前提下，引入 V3 结构化解答协议。
- 将“识别—解题—独立校验—必要时一次修正—保存”变成可观察、可恢复的状态链路。
- 识别不确定时明确提示用户核对原图，不展示“100%正确”类误导性结论。
- AI 解题结果保存时立即使用结构化学习信息，同时保留用户的复习计划选择和手工元数据。
- 修复复习计划退出项仍被纳入队列、跨午夜日期停留在旧日期两个验收问题。

## 2. v1.4E.1 问题收口

### 复习计划资格

`DailyStudyPlanner` 现在对到期、薄弱补强、可选巩固三个 bucket 使用同一条资格条件：记录必须有效、未归档、未删除、ID 有效，并且 `inReviewPlan=true`。因此用户明确退出复习计划后，不会从另一类 bucket 重新进入今日队列。

### 跨午夜日期

`MistakeViewModel.reviewNow` 作为公开可观察时钟，首页、导航、复习页、日历页和未来负载统一从同一个时间点推导本地日期。应用在 23:59 到 00:01 跨午夜时会切换到新的一天和新的星期，而不是继续使用页面首次创建时的日期。

## 3. AI 解题协议与状态

### V3 结构化协议

新增 `AiSolutionV3.kt`：

- `recognition.segments`：完整题目、识别疑点和识别提示。
- `solution.approach`：解题方法。
- `solution.steps[]`：每一步的内容、为什么成立、对应知识点。
- `solution.finalAnswer`：最终结论。
- `learning`：科目、题型、知识点、难度和易错提醒。
- `verification`：独立校验结果，由校验器写入，不由原解题模型伪造。

V3 支持协议标记、代码围栏和服务商前后包装文本；无法解析完整 V3 时回退 V2，再回退旧版四分区文本。V2 的严格 sections/segments 规则、公式片段、矩阵/方程组和语义换行约束均保留。

### 独立校验与一次修正

新增 `AiSolutionVerifier.kt`，使用现有 AI 配置发起独立非流式文本检查：

```text
Candidate -> VERIFYING -> PASS / WARNING / FAILED / UNAVAILABLE
                         FAILED -> REPAIRING（最多一次）-> 再校验
```

- 校验检查条件使用、计算/推导一致性、定义域、漏解、答案一致性和识别风险。
- `FAILED` 最多触发一次修复，不会无限重试。
- 校验服务超时、返回非法 JSON 或不可用时，原解答仍保留，状态为 `UNAVAILABLE`。
- 修复结果为空、非法或无法展示时，继续展示原候选解答。
- 校验、修复阶段均持久化为可观察状态，页面会显示当前阶段。

### 状态持久化

`AiSolveStateStore`、`AiSolveHistoryStore` 和 `MistakeViewModel` 新增：

- `VERIFYING`、`REPAIRING` 状态。
- `uncertainItems`。
- `AiVerificationResult` 及问题明细。
- `solutionProtocolVersion`。

旧版本本地 JSON 没有这些字段时使用安全默认值，不影响历史记录恢复。

## 4. AI 解题页 UI

`AiSolveScreen` 新增以下入口和反馈：

- “识别结果需确认”卡片：展示疑点、识别题目、原图/题目内容，并提供展开核对路径。
- “解答一致性检查”卡片：展示通过、需核对、失败或不可用，不使用“100%正确”文案；失败时可重新解题。
- V3 学习信息：科目、题型、难度、知识点和易错提醒。
- 快捷追问：“为什么这样做”“换一种解法”“讲简单一点”“检查我的答案”。前 3 项复用现有追问服务，“检查我的答案”先收集用户答案，再复用同一追问链路。
- 保存前的“保存后加入复习计划”开关，默认开启，允许明确选择暂不加入。
- 高置信重复题提示：可以打开已有错题，也可以仍然保存新记录；低置信相似内容不会阻断保存。

## 5. 保存与分类

### 结构化保存映射

新增 `AiSolvedMistakeDraftMapper`，一键保存时优先从 V3 读取：

- 题目使用 `recognition`。
- 答案使用 `finalAnswer`。
- 解析合并 `approach`、步骤及其原因/知识点。
- 立即写入 learning 元数据和易错提醒。
- 保留用户答案、错误原因、图片、内容块和 PDF 图片选择。
- 根据开关写入 `inReviewPlan`；加入计划时安排到下一次本地午夜，退出计划时不强制打开。

### 非破坏分类

`mergeClassificationMetadata` 现在只补全空白/“未分类”的科目和题型，只追加知识点标签，只在原难度未填写时补充难度。后台分类不会覆盖用户已经修改的字段，也不会把退出复习计划的记录强制重新加入计划。

### 重复检测

`AiDuplicateDetector` 仅使用两类高置信条件：规范化题目全文精确一致（且长度达到阈值），或原图 SHA-256 一致。它不根据标题、短文本或模糊关键词阻断保存。

## 6. 关键文件

| 区域 | 文件 |
| --- | --- |
| V3 协议 | `app/src/main/java/com/tiji/mistakes/service/AiSolutionV3.kt` |
| 校验/修复 | `app/src/main/java/com/tiji/mistakes/service/AiSolutionVerifier.kt` |
| AI 后台链路 | `app/src/main/java/com/tiji/mistakes/service/AiSolveService.kt` |
| 状态/历史 | `AiSolveStateStore.kt`、`AiSolveHistoryStore.kt`、`MistakeViewModel.kt` |
| 保存映射/重复检测 | `app/src/main/java/com/tiji/mistakes/domain/ai/` |
| 解题 UI | `app/src/main/java/com/tiji/mistakes/ui/solve/AiSolveScreen.kt` |
| 复习日期与计划 | `DailyStudyPlanner.kt`、`TijiApp.kt`、`ReviewScreen.kt` |

## 7. 验证记录

命令均使用 `D:\android\jdk17`、`D:\android\user-home\.gradle`；JVM 测试因 Windows 中文工作区路径问题从临时 `T:` 映射盘执行，未修改项目路径。

- `:app:compileDebugKotlin`：通过。
- `:app:testDebugUnitTest`：149 tests，0 failures，0 errors。
- `:app:lintDebug`：通过，0 errors；仅有 2 个已有 warning：`OcrNativeRuntime.kt` 的动态 native 加载、`AiMistakeClassificationService.kt` 的过时 SDK 判断。
- `:app:assembleDebug`：通过。
- `:app:connectedDebugAndroidTest`：API 35 `Tiji_API_35` 模拟器通过，0 failed，4 条条件跳过；Gradle 最终输出 `Finished 46 tests`。
- debug APK 仅作为本地构建产物，未加入源码提交：`app/build/outputs/apk/debug/tiji-v1.0.0-debug.apk`。

## 8. 边界与未覆盖项

- 未使用真实第三方 API Key 做在线解题/校验；provider 兼容性通过共用 OpenAI-compatible transport、提示词和结构化解析测试验证。
- 校验器不可用时不会阻塞本地保存，也不会把不可用误报为通过。
- 本轮没有增加账号、云同步、后台额度购买、模型大规模改名或其他方案外功能。

## 9. Git 发布

- 工作分支：`codex/v1.4f-ai-solve-core`
- 提交：待提交后回填
- Pull Request：待推送后回填
- 仅推送源码、测试和本变更文档；不合并 PR，不提交 APK。

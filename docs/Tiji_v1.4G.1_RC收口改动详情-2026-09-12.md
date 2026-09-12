# Tiji v1.4G.1 Release Candidate 收口改动详情

日期：2026-09-12
依据：`Tiji_v1.4G验收与下一步Release_Candidate收口方案_2026-09-12.md`
目标：在 v1.4G 的基础上完成小范围 RC 稳定性收口，不引入新的大型 UI、架构或 AI 协议。

## 执行边界

- 本轮只处理验收方案列出的复习提交竞态、详情页重复打卡、AI 检查状态语义和 PDF 字段语义。
- 未重新安装 Android 系统，未创建新的 AVD，也未修改现有模拟器配置。
- 只提交源码、测试和本变更详情文档；Gradle 生成的 APK、报告和缓存不提交到 GitHub。
- 真实 AI provider smoke test 已使用用户在模拟器中保存的 DeepSeek 配置执行并记录；长图 PDF 的人工视觉验收仍需专用长图样本，connected instrumentation 仍以 CI 结果为准。

## 具体改动

### 1. 复习页提交与自动跳题竞态收口

文件：`app/src/main/java/com/tiji/mistakes/ui/review/ReviewQuestionScreen.kt`

- 增加 `reviewSubmitting` 提交锁：复习等级写入期间禁用四个等级卡片及上一题/下一题，避免重复评分或评分过程中手动跳题。
- 增加 `autoAdvanceJob` 与 `autoAdvancePending`：同一题的自动跳题只允许排队一次，520ms 延迟期间再次触发不会重复跳转。
- 上一题、下一题、完成/退出操作会取消待执行的自动跳题；自动任务自身推进时不取消当前任务，避免“评分一次却跳两题”。
- 保留原有 focused review 会话记录和恢复逻辑，不改变复习记录的持久化协议。

覆盖测试：

- `FocusedReviewRecreationTest.repeatedGradeTapsRecordAtMostOneReviewPerQuestion`
- `FocusedReviewUiTest`、`FocusedReviewRecreationTest`、`LibraryFilterTest` 的自动流转与总结适配
- `ReviewQuestionUiTest` 的复习记录、间隔预览和数据库持久化检查

### 2. AI 检查状态语义修正

文件：`app/src/main/java/com/tiji/mistakes/ui/solve/AiSolveScreen.kt`

- 新增 `aiVerificationUiCopy()`，将可靠性模式与检查状态统一转换为界面文案。
- PASS 显示“已完成检查”，仅在确实完成独立检查时使用。
- FAST 显示“未启用独立检查 / 本次仅完成解题”，不再误标为已完成检查。
- UNAVAILABLE 显示“本次未完成检查”，并保留原因或使用明确的兜底原因。
- WARNING 显示“建议核对”，FAILED 显示“解答存在疑点”，继续展示问题明细。
- 结果页继续保持收敛后的“题目 / 解析 / 答案”结构，移除已废弃的重复纠正/追问入口引用。

新增测试：

- `app/src/test/java/com/tiji/mistakes/AiVerificationPresentationTest.kt`
- 覆盖 FAST、UNAVAILABLE、PASS、WARNING、FAILED 五种语义组合。

### 3. 错题详情页复习打卡防重复提交

文件：`app/src/main/java/com/tiji/mistakes/ui/detail/MistakeDetailScreen.kt`

- 增加 `reviewSubmitting` 状态。
- 用户选择等级后立即锁定等级按钮并关闭弹窗；数据库回调成功或任务结束后解除提交锁。
- 底部“复习打卡”入口在提交期间禁用，降低快速重复点击写入多条 review record 的风险。
- 成功回调继续刷新 mastery、reviewCount、lastReviewedAt、nextReviewAt，并显示本次等级结果。

### 4. PDF 字段语义注释

文件：`app/src/main/java/com/tiji/mistakes/data/MistakeEntity.kt`、`app/src/main/java/com/tiji/mistakes/service/HtmlPdfExportService.kt`

- 明确 `MistakeEntity.includeSourceImageInPdf` 是错题保存时的兼容/默认偏好。
- 明确当前一次导出的唯一生效开关是 `PdfExportOptions.includeSourceImages`。
- 保留 Room 字段和既有迁移，不删除字段、不扩大数据库变更范围。

### 5. 测试与文档同步

- 复习自动流转、重建恢复、详情页打卡、PDF 练习/答案边界等现有测试与 v1.4G 交互保持一致。
- 本文件记录本轮 RC 的实现边界和真实验证结果；v1.4G 的完整改动仍见 [`Tiji_最终验收后收口改动详情-2026-09-12.md`](Tiji_最终验收后收口改动详情-2026-09-12.md)。

### 6. 最终验收补充：真实 Provider 与模拟器链路

本节记录 2026-09-12 在现有 `Tiji_API_35` 模拟器上的实际验收，不包含 API Key 或其他敏感配置值。

- 模拟器：`Tiji_API_35`，设备序列号 `emulator-5554`，API 35；安装并启动 `app/build/outputs/apk/debug/tiji-v1.0.0-debug.apk`。
- 五个底部导航入口均作为一级页面可达：`首页`、`错题`、`AI解题`、`复习`、`设置`。
- 离线链路通过：开启飞行模式并关闭 Wi-Fi/移动数据后，应用仍可启动；手动录入、保存到错题库、列表、详情、复习打卡和今日复习均可完成。
- PDF 链路通过：在离线状态生成练习版 PDF，预览、Android 文件保存和系统打印预览均可打开；随后删除本轮生成的临时 PDF。
- 真实 AI Provider Smoke Test 通过：默认配置显示模型 `deepseek-v4-flash-vision-exp`，以无图片文本题执行一次解题请求；日志记录 `provider=DEEPSEEK`、`images=0`、响应成功，界面显示“解题完成”和“一致性检查通过”。本次测试请求未保存为错题，临时验收错题已删除，错题库恢复为 0 道。
- 恢复网络后再次确认模拟器在线；未重装 Android 系统，也未创建第二个 AVD。

## 验证记录

执行环境：项目指定 JDK 17（`D:\android\jdk17`）、Gradle 8.9、项目 SDK（`D:\android\sdk`）。

| 检查 | 结果 |
| --- | --- |
| `:app:compileDebugKotlin` | 通过 |
| `:app:compileDebugUnitTestKotlin` | 通过 |
| `:app:compileDebugAndroidTestKotlin` | 通过 |
| `:app:assembleDebug` | 通过；本地 APK 为 `app/build/outputs/apk/debug/tiji-v1.0.0-debug.apk`，未提交 |
| `:app:lintDebug` | 通过；0 个错误、5 个既有警告 |
| `:app:testDebugUnitTest` | 未通过；34 个测试均在初始化阶段报 `ClassNotFoundException`，报告未出现本轮新增断言失败 |
| `adb devices -l` | 通过；`emulator-5554`（`Tiji_API_35`）在线 |
| 模拟器手工 UI smoke test | 通过；五个一级页面、离线录入/复习/列表/详情链路通过 |
| PDF 预览、保存与系统打印预览 | 通过；完成 1 页练习版 PDF 的实际链路并清理临时文件 |
| 真实 AI provider smoke test | 通过；DeepSeek `deepseek-v4-flash-vision-exp` 文本请求成功返回并展示结果 |
| 长图 PDF 人工视觉验收 | 未完成；本轮验证了 1 页 PDF 链路，尚未覆盖方案要求的长图尺寸、跨页几何一致性和手写内容样本 |

## GitHub 交付

- 分支：`codex/v1.4f-ai-solve-core`
- 目标仓库：`stardawn2326/tiji`
- 目标 PR：[#7](https://github.com/stardawn2326/tiji/pull/7)
- 本文档随本轮 RC 源码一起提交；推送完成后以远端分支最新提交为准。

# 题迹 UI v1.4G 改动详情

日期：2026-09-11

依据方案：`Tiji_UI_v1.4F_验收与v1.4G下一步方案_2026-09-11.md`

覆盖范围：v1.4F.1 稳定性修复 + v1.4G AI 生产验证与教学体验

工作分支：`codex/v1.4f-ai-solve-core`

## 1. 本次交付结论

本轮已完成方案中可在本地离线验证的 P0/P1 项，并补齐了可复现的测试夹具、Room 数据链路验证和 UI 反馈。真实第三方 Provider 的在线 smoke 需要实际 API Key，因此没有把密钥写入代码、测试或 CI，也没有伪造在线成功结果。

## 2. 方案对照

| 方案项 | 状态 | 实现或验证位置 |
| --- | --- | --- |
| 分类结果写回最新数据库行 | 已完成 | `MistakeRepository.applyAiClassification` 在事务中重新读取最新行、合并分类元数据并同步知识点；`AiMistakeClassificationService` 不再写回过期快照 |
| `difficulty=0` 回退学习者输入 | 已完成 | `AiSolvedMistakeDraftMapper` 仅在学习者难度为 1..5 时覆盖，否则保留输入难度 |
| Fake Provider SSE / 非流式 / 异常契约 | 已完成 | `AiProviderContractHarnessTest` 覆盖 SSE、非流式 content 数组、截断、畸形 JSON、超时、401/429/500 |
| V3 solver → verifier PASS | 已完成 | `AiSolveProviderIntegrationTest` |
| FAILED → repair → PASS | 已完成 | `AiSolveProviderIntegrationTest`，验证请求顺序和请求次数 |
| verifier 不可用时保留候选答案 | 已完成 | `AiSolveProviderIntegrationTest` |
| 输出达到长度上限时保留部分内容 | 已完成 | `AiProviderContractHarnessTest`；`AiSolveService` 不把输出上限异常误判为普通失败并覆盖部分答案 |
| solve → Room → KnowledgePoint / CrossRef | 已完成 | `AiSolveSaveKnowledgeE2ETest` |
| 用户答案诊断与学习者确认错因 | 已完成 | `AiAnswerDiagnosis`、`AiAnswerDiagnosisService`、`AiAnswerDiagnosisCard`；只有用户确认后的错因才写入保存草稿 |
| 分步教学卡片 | 已完成 | `AiTeachingStepCards` 展示 V3 solution steps、步骤标题、推理和检查结果 |
| 编辑识别题目后重新解题 | 已完成 | `AiSolveScreen` 识别编辑弹窗；修正内容作为新请求参数，重新生成新的 `solveRunId` |
| 可靠 / 快速模式 | 已完成 | `AiSolveReliabilityMode`；可靠模式执行 verifier 与一次 repair，快速模式明确展示未执行独立一致性检查 |
| 本地诊断信息 | 已完成 | `AiSolveDiagnostics`；展示解题、验证、修复耗时、请求数和 V3/V2/legacy 路径 |
| 重复错题提醒 | 已保留并验证现有交互 | 现有高置信重复检测 UI 提供“打开已有记录 / 取消 / 仍然保存”分支，检测器测试继续覆盖该行为 |
| Provider smoke 矩阵文档 | 已补齐模板 | 见本文第 6 节；真实密钥验证留给具备 Provider 凭据的环境 |

## 3. 代码改动

### 3.1 数据一致性与保存链路

- `MistakeRepository.kt`
  - 新增事务化 `applyAiClassification`。
  - 在分类异步完成后重新读取数据库最新记录，再合并 AI 分类字段。
  - 保留用户刚编辑的问题、答案、难度、备注、标签和复习计划开关。
  - 完成分类标签、AI 知识点与用户标签的知识点同步，并清理孤立关联。
- `AiMistakeClassificationService.kt`
  - 将旧的“基于启动时快照保存”改为调用最新行事务写回。
- `AiSolvedMistakeDraftMapper.kt`
  - 学习结果中的未知难度不再把有效的学习者输入覆盖为 0。
- `AiSolveSaveKnowledgeE2ETest.kt`
  - 验证结构化 V3 结果保存后，错题主表、知识点和交叉引用在同一条链路中成立。
- `AiClassificationRaceTest.kt`
  - 模拟 AI 分类运行期间用户编辑同一错题，确认用户字段不丢失且分类关联仍同步。

### 3.2 Provider 与解题管线

- `AiVisionService.kt`
  - 抽出 `AiProviderTransport` 接口，生产实现仍使用 `HttpURLConnection`。
  - 支持测试注入 fake transport，覆盖流式 SSE 和普通响应。
  - 统一 Provider HTTP 错误提示，保留 401、429、500 等状态上下文。
  - 修正输出上限异常：保留已经收到的部分文本，不再错误触发非流式二次请求。
  - 增加识别修正内容注入；请求体使用固定长度写入，便于兼容严格的 HTTP Provider。
- `AiSolveService.kt`
  - 记录 solver、verifier、repair、reverify 的耗时和请求次数。
  - 可靠模式执行 V3 验证与一次修复闭环；快速模式跳过独立 verifier，并在 UI 中显式说明。
  - 在成功、失败、超时状态中均保存可用的本地诊断快照。
- `AiProviderContractHarnessTest.kt`
  - 通过进程内 fake HTTP server 验证生产 transport 的 SSE 传输行为。
- `AiSolveProviderIntegrationTest.kt`
  - 验证 PASS、FAILED→repair→PASS 和 verifier 不可用三条核心路径。

### 3.3 教学体验与交互反馈

- `AiAnswerDiagnosis.kt`
  - 增加 `CORRECT / PARTIALLY_CORRECT / INCORRECT / UNCERTAIN` 结构化答案诊断。
  - 对 JSON、代码围栏和 wrapper 响应做受控解析；畸形响应转为可恢复失败。
  - 诊断提示要求 AI 区分“答案判断”和“错因建议”，避免未经用户确认直接改变错因。
- `AiSolveScreen.kt`
  - 增加学习者答案诊断卡、错因确认、重试和状态反馈。
  - 增加 V3 分步教学卡片、验证状态卡、识别内容编辑入口。
  - 增加可靠 / 快速模式选择及当前模式说明。
  - 增加本地诊断面板，注明耗时和请求数不代表服务端指标。
- `MistakeViewModel.kt`
  - 管理答案诊断状态、超时、重试和请求生命周期。
  - 识别内容修正会启动新的解题运行，避免复用旧的 `solveRunId`。
- `AiSolveReliability.kt`、`AiSolveStateStore.kt`、`AiSolveHistoryStore.kt`、`AppPreferences.kt`
  - 可靠性模式和诊断信息可进入当前状态、历史记录及偏好备份链路。
- `TijiApp.kt`、`TijiNavGraph.kt`、`TijiNavGraphState.kt`
  - 将可靠性模式从应用偏好传递到解题页。

## 4. 测试新增与回归覆盖

新增或扩展以下测试：

- `AiClassificationRaceTest`：分类写回竞态与知识点关联。
- `AiSolveSaveKnowledgeE2ETest`：V3 解题结果到 Room、KnowledgePoint、CrossRef 的保存链路。
- `AiAnswerDiagnosisTest`：诊断 codec、wrapper、畸形响应和错因确认边界。
- `AiSolveReliabilityTest`：可靠 / 快速模式解析、标签和说明。
- `AiProviderContractHarnessTest`：Provider HTTP/SSE/异常契约。
- `AiSolveProviderIntegrationTest`：solver、verifier、repair 集成路径。
- `AiMistakeSaveStateTest`：未知难度回退。
- `AiVisionServiceTest`：识别修正提示构造。

## 5. 验收结果

### JVM / Debug 构建

执行环境：JDK `D:\android\jdk17`、Gradle 8.9、Android SDK `D:\android\sdk`。为规避 Windows 下 Gradle 原路径锁定，构建使用临时盘符映射，但没有改动源码路径。

```text
:app:testDebugUnitTest   PASS   166 tests, 0 failures, 0 errors
:app:lintDebug           PASS   0 errors
:app:assembleDebug       PASS
```

生成的本地 APK：

```text
app/build/outputs/apk/debug/tiji-v1.0.0-debug.apk
SHA-256: 30461DA096C15D77D051D3D3C56804BCE030E3044E222DF76D0F0CF7AE95982D
```

APK 仅用于本地验收，没有加入源码提交，也没有推送到 GitHub。

### API 35 模拟器

设备：`emulator-5554` / `Tiji_API_35(AVD) - 15`。

```text
:app:connectedDebugAndroidTest   PASS   48 tests, 0 failures, 4 conditional skips
```

本轮曾出现一次 Android SystemUI `HardwareRenderer` finalizer watchdog 和后续 `DeadSystemException`，属于模拟器系统进程故障，不是应用断言失败。重启模拟器后先单独执行 `FocusedReviewRecreationTest`（4/4 通过），再执行完整 API 35 connected suite（48 项通过）。

## 6. Provider smoke matrix（真实凭据环境执行）

下表是后续具备真实凭据时的统一记录格式。本轮使用 fake provider 和离线 transport 完成契约验证，未使用真实 API Key。

| Provider profile | 文本 | 图片 / 多图 | V3 结构化结果 | verifier | follow-up / repair | 保存链路 | 延迟与结果 | 密钥记录 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| OpenAI-compatible | 待真实环境填写 | 待真实环境填写 | 待真实环境填写 | 待真实环境填写 | 待真实环境填写 | 待真实环境填写 | 待真实环境填写 | 不写入仓库 |
| Gemini / gateway | 待真实环境填写 | 待真实环境填写 | 待真实环境填写 | 待真实环境填写 | 待真实环境填写 | 待真实环境填写 | 待真实环境填写 | 不写入仓库 |
| Custom provider | 待真实环境填写 | 待真实环境填写 | 待真实环境填写 | 待真实环境填写 | 待真实环境填写 | 待真实环境填写 | 待真实环境填写 | 不写入仓库 |

离线契约已覆盖：SSE、非流式响应、超长输出、畸形 JSON、超时、401、429、500，以及 solver/verifier/repair 的请求顺序。

## 7. 提交边界

- 提交内容：源码、测试和本改动详情 Markdown。
- 不提交：APK、真实 API Key、机器本地配置、构建缓存。
- 本轮只推送源码分支，不自动合并 Pull Request。

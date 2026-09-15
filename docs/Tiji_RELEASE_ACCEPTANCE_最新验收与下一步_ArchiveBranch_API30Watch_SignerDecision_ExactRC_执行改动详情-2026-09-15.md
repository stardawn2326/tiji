# Tiji RELEASE_ACCEPTANCE ArchiveBranch API30 Watch 执行改动详情

## 文档信息

- 日期：2026-09-15
- 对照方案：`C:\Users\23260\Downloads\Tiji_RELEASE_ACCEPTANCE_最新验收与下一步_ArchiveBranch_API30Watch_SignerDecision_ExactRC_2026-09-15.md`
- 仓库：[stardawn2326/tiji](https://github.com/stardawn2326/tiji)
- 发布源：`main@483d441020d83beffa9a96bd048dc426354f7f5a`
- 本次证据分支：`codex/release-evidence-watch-20260915`（从上述 main 建立，仅 docs）
- 唯一验证环境：`tiji-api30` / `emulator-5554` / Android API 30
- API35：`OUT_OF_SCOPE`

## 执行结论

```text
ARCHIVE_EXECUTION_RECORD = PASS_WITH_ONE_STATUS_CORRECTION
MAIN_RELEASE_BASELINE = PASS
FOCUSED_REVIEW_FLAKE_HARDENING = PASS
GENERAL_API30_UI_STABILITY = OPEN_P1_NON_BLOCKING
REVIEW_QUESTION_UI_TEST_FAILURE = NEEDS_CLASSIFICATION
```

本轮没有修改生产代码、UI、Room、Backup、复习逻辑、AI/OCR 或签名实现。旧 evidence 分支和原 archive 分支均保持冻结；本分支只记录方案要求的状态修正、API30 定向复验和安装证据。

## 冻结分支与主线证据

- `main` 仍为 `483d441020d83beffa9a96bd048dc426354f7f5a`，没有新的 release code 合并。
- exact-main CI [34914942125](https://github.com/stardawn2326/tiji/actions/runs/34914942125) 为 attempt 1 success；编译、测试、lint、打包和 API30 instrumentation 均通过。
- 旧 evidence 分支 `codex/release-acceptance-api30@05b0bd50fb445fd2144b8300b1dafd9241c792a5` 保持冻结，未 push、rebase、merge 或重跑。
- 原 archive 分支 `codex/release-evidence-20260915@fe5f8d89b1a973441d30935a5e58b32345b41e98` 保持冻结，未为绿色而继续提交。
- 本分支相对 `main` 只有本 MD 文件，生产代码漂移为 `0`。

## ArchiveBranch API30 Watch

原 archive push run [34920523530](https://github.com/stardawn2326/tiji/actions/runs/34920523530) 的 compile/test/lint/package job 通过，API30 instrumentation 仅在 `ReviewQuestionUiTest.reviewFeedbackMatchesPreviewAndPersistsSchedule` 的反馈文本断言处失败，缺少 `已记录：掌握`。SDK bootstrap、API30 镜像启动和测试 harness 均成功。

按方案执行同一源码提交的定向复验：

```text
./gradlew.bat :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tiji.mistakes.ReviewQuestionUiTest \
  --offline --no-daemon --console=plain
```

结果：`BUILD SUCCESSFUL`，API30 `ReviewQuestionUiTest` 3/3 通过。该结果说明失败未在本次同提交定向复验中重现，但单次归档全量失败仍保留为待分类的 P1 稳定性观察；不修改 production behavior，也不把 archive CI 失败升级为 main 回归。

## API30 Debug 构建、安装与启动

- 构建：`app:connectedDebugAndroidTest` 在项目 JDK17、离线模式下成功完成。
- APK：`app/build/outputs/apk/debug/tiji-v1.0.0-debug.apk`
- SHA-256：`7BE36F8DF6B161675BA8EFEB4CAF49242C57B915AA5BAE5A01A5AD1A82468F5C`
- applicationId：`com.tiji.mistakes`
- versionCode / versionName：`114 / 1.0.0`
- minSdk / targetSdk / compileSdk：`26 / 34 / 34`
- 安装：`adb -s emulator-5554 install -r -d -g` → `Success`
- 启动：`monkey -p com.tiji.mistakes 1` → `Events injected: 1`
- 设备 SDK：`30`
- 启动后进程：`com.tiji.mistakes` PID `5136`
- 最近日志：未发现该包匹配的 `FATAL EXCEPTION` 或 `ANR`

APK 使用 Android Debug certificate，仅用于 API30 安装和启动 smoke，不能作为正式签名 RC 或 Fresh Install/Runtime Gate 证据。

## Signer Decision 状态

正式签名输入仍为空，状态保持：

```text
PREVIOUS_ACCEPTED_APK = NO_EVIDENCE
SIGNER_LINEAGE = UNPROVEN
PATH_A_CONTINUITY = NOT_ENTERED
PATH_B_LOST_SIGNER = NOT_ENTERED
PATH_C_FIRST_FORMAL_RELEASE = NOT_AUTHORIZED
FORMAL_SIGNER_READY = NO
FORMAL_EXACT_RC = NOT_CREATED
RELEASE_SIGNING_GATE = FAIL
ARTIFACT_PROVENANCE_GATE = FAIL
RELEASE_ACCEPTANCE = NOT_COMPLETE
```

没有把 `NO_EVIDENCE` 改写为 `NO_PREVIOUS_ACCEPTED_APK = TRUE`，没有生成正式签名 RC、tag、GitHub Release 或公开 APK。UI redesign 继续保持 `HOLD`。

## GitHub 交付边界

- 远端固定为 `https://github.com/stardawn2326/tiji.git`。
- 本次只推送独立 docs-only watch 分支，不触碰冻结的 archive/evidence 分支，也不合并回 `main`。
- GitHub Releases 和正式 workflow artifact 仍为 `0`。
- 未进行截图、视觉比对或 API35 验证。


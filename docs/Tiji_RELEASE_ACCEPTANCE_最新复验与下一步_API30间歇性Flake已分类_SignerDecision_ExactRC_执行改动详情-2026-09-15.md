# Tiji RELEASE_ACCEPTANCE API30 间歇性 Flake 分类执行改动详情

## 文档信息

- 日期：2026-09-15
- 对照方案：`C:\Users\23260\Downloads\Tiji_RELEASE_ACCEPTANCE_最新复验与下一步_API30间歇性Flake已分类_SignerDecision_ExactRC_2026-09-15 (1).md`
- 仓库：[stardawn2326/tiji](https://github.com/stardawn2326/tiji)
- 当前 Release source：`main@483d441020d83beffa9a96bd048dc426354f7f5a`
- 本次状态快照分支：`codex/release-acceptance-flake-classified-20260915`（从 main 建立，仅 docs）
- 唯一验证环境：`tiji-api30` / `emulator-5554` / Android API 30
- API35：`OUT_OF_SCOPE`

## 执行结论

```text
ARCHIVE_WATCH_EXECUTION_RECORD = PASS
MAIN_RELEASE_BASELINE = PASS
MAIN_API30 = PASS
WATCH_BRANCH_CI = PASS
WATCH_BRANCH_API30 = PASS
REVIEW_QUESTION_UI_TEST_FAILURE = CLASSIFIED_INTERMITTENT_UI_TIMING_FLAKE
GENERAL_API30_UI_STABILITY = OPEN_P1_NON_BLOCKING_WATCH
```

前一次归档全量 CI 的单次 Compose/UI 反馈展示断言失败，在同一生产代码和同一 API30 环境的后续定向复验、watch 分支全量 GitHub CI 中均未重现。当前定性为间歇性 UI 时序 Flake，仍保留 P1 观察，不把它误报为“永久关闭”或生产回归。

本轮没有修改生产代码、测试代码、UI、Room、Backup、复习逻辑、AI/OCR 或签名实现。

## main 与冻结分支证据

- `main` 当前为 `483d441020d83beffa9a96bd048dc426354f7f5a`，没有新的 release code 合并。
- exact-main CI [34914942125](https://github.com/stardawn2326/tiji/actions/runs/34914942125) 为 attempt 1 success，编译、测试、lint、打包和 API30 instrumentation 均通过。
- `codex/release-evidence-20260915@fe5f8d89b1a973441d30935a5e58b32345b41e98` 已冻结，未继续 push、rebase、merge 或修 test。
- `codex/release-acceptance-api30@05b0bd50fb445fd2144b8300b1dafd9241c792a5` 已冻结，未触碰。
- `codex/release-evidence-watch-20260915@b9c029fad2e11d1a9f29cd9dac34fb2ba67dd463` 已冻结，本轮没有在其上追加 docs commit。

## Watch branch CI 与 Flake 分类

watch 分支 [34921953217](https://github.com/stardawn2326/tiji/actions/runs/34921953217) 为 attempt 1 success：

```text
Compile, test, lint and package = PASS
Release harness self-check = PASS
Android instrumentation (API 30) = PASS
Starting 71 tests
Skipped 4 known conditional tests
Failed 0
Finished 75 tests
BUILD SUCCESSFUL
```

此前 archive run [34920523530](https://github.com/stardawn2326/tiji/actions/runs/34920523530) 唯一失败为：

```text
ReviewQuestionUiTest.reviewFeedbackMatchesPreviewAndPersistsSchedule
assertExists: missing “已记录：掌握”
```

该 archive 分支相对 main 只有文档差异，production/test source 均未改变。随后同一源码的本地 API30 定向 `ReviewQuestionUiTest` 复验 3/3 通过，watch 分支全量 CI 也通过，因此分类为 `CLASSIFIED_INTERMITTENT_UI_TIMING_FLAKE`。P1 watch 仍保留，后续只有在真实 release-code PR 或同一 exact-main 再次失败时才升级为 test-only hardening。

## API30 Debug APK 安装复验

- APK：`app/build/outputs/apk/debug/tiji-v1.0.0-debug.apk`
- SHA-256：`7BE36F8DF6B161675BA8EFEB4CAF49242C57B915AA5BAE5A01A5AD1A82468F5C`
- applicationId：`com.tiji.mistakes`
- versionCode / versionName：`114 / 1.0.0`
- minSdk / targetSdk / compileSdk：`26 / 34 / 34`
- 安装命令：`adb -s emulator-5554 install -r -d -g` → `Success`
- 启动命令：`monkey -p com.tiji.mistakes 1` → `Events injected: 1`
- 设备 SDK：`30`
- 启动进程：`com.tiji.mistakes` PID `2096`
- 近期日志：未发现该包匹配的 `FATAL EXCEPTION` 或 `ANR`

该 APK 使用 Android Debug certificate，只证明 API30 安装和启动 smoke，不能冒充正式签名 RC，也不能代替 Fresh Install、Upgrade、Runtime 或数据留存门禁。

## Signer Decision 状态

正式签名输入和历史 accepted APK 证据仍未提供，状态继续保持：

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
FUNCTION_FINAL_FREEZE = HOLD
UI_REDESIGN = HOLD
```

没有把 `NO_EVIDENCE` 改写为 `NO_PREVIOUS_ACCEPTED_APK = TRUE`，没有伪造 signer、创建正式签名 RC、tag、GitHub Release 或公开 APK。只有发布负责人形成真实的 Path A、Path B 或 Path C 事实后，才能从当前 main 进入正式签名流程。

## GitHub 交付边界

- 远端固定为 `https://github.com/stardawn2326/tiji.git`。
- 本次状态快照分支只新增本 MD 文件，生产代码漂移为 `0`；推送后立即冻结，不作为 Release source。
- GitHub Releases 数量为 `0`，workflow artifacts 数量为 `0`。
- 未启动或验证 API35，未进行截图、视觉比对或人工视觉验收。


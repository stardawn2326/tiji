# Tiji RELEASE_ACCEPTANCE Evidence Branch 冻结与 SignerDecision 执行改动详情

## 文档信息

- 日期：2026-09-15
- 对照方案：`C:\Users\23260\Downloads\Tiji_RELEASE_ACCEPTANCE_复验与下一步_EvidenceBranch冻结_SignerDecision_ExactSignedRC_2026-09-15.md`
- 仓库：[stardawn2326/tiji](https://github.com/stardawn2326/tiji)
- 发布源冻结：`main@483d441020d83beffa9a96bd048dc426354f7f5a`
- GitHub 证据分支：`codex/release-evidence-20260915`（从上述 main 建立，仅 docs）
- 唯一验证设备：既有 `tiji-api30` AVD，serial `emulator-5554`，Android API 30
- API35：`OUT_OF_SCOPE`

## 执行结论

最新方案确认代码、CI 稳定性和 API30 测试门禁已经完成；当前唯一 Release P0 是 `SIGNER_LINEAGE_DECISION` 与正式签名材料。本轮没有修改产品代码、UI、复习逻辑、Room、Backup、AI/OCR 或签名实现，只完成冻结基线的安装复验和 docs-only 证据归档。

旧 evidence 分支 `codex/release-acceptance-api30` 已冻结在 `05b0bd50fb445fd2144b8300b1dafd9241c792a5`，本轮没有向其 push、rebase、合并或重跑 CI。

## Signer Decision 状态

当前受保护签名输入均为空，未发现本地正式 keystore：

```text
TIJI_SIGNING_STORE_FILE       = empty
TIJI_SIGNING_STORE_PASSWORD   = empty
TIJI_SIGNING_KEY_ALIAS        = empty
TIJI_SIGNING_KEY_PASSWORD     = empty
TIJI_PREVIOUS_SIGNER_SHA256   = empty
TIJI_FIRST_FORMAL_RELEASE     = empty
```

因此保持方案中的事实边界：

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

没有把 `NO_EVIDENCE` 改写成 `NO_PREVIOUS_ACCEPTED_APK = TRUE`，没有创建 First Formal Release 模式，没有伪造 previous signer，也没有生成正式签名 RC。

## 冻结 main 验收证据

- `main` 当前仍为 `483d441020d83beffa9a96bd048dc426354f7f5a`，没有新的 release code 合并。
- exact-main CI [34914942125](https://github.com/stardawn2326/tiji/actions/runs/34914942125) 为 attempt 1 success；compile/test/lint/package 与 API30 instrumentation 均 PASS。
- API30 exact-main 日志为 71 项启动、4 项已知条件 skip、0 失败、75 项结束。
- 旧 evidence 分支的 run `34919697459` 因旧提交仍请求已移除的 SDK `tools` 包而失败；该失败是 `STALE_BRANCH_INFRA_FAILURE`，不覆盖当前 main 结论，按方案不再重跑。
- GitHub Releases 数量为 `0`，当前 workflow artifacts 数量为 `0`。

## API30 Debug 构建与安装

构建命令：

```text
./gradlew.bat :app:assembleDebug --offline --no-daemon --console=plain
```

结果：`BUILD SUCCESSFUL`。

安装产物：

```text
app/build/outputs/apk/debug/tiji-v1.0.0-debug.apk
SHA-256 = 7BE36F8DF6B161675BA8EFEB4CAF49242C57B915AA5BAE5A01A5AD1A82468F5C
size = 22361588 bytes
applicationId = com.tiji.mistakes
versionCode = 114
versionName = 1.0.0
minSdk = 26
targetSdk = 34
compileSdk = 34
signer = Android Debug
signer SHA-256 = 24300ED6EC1BA17E7004CD3156FDE97E5568D09B8C6D921D9C9BB24C597EC9F1
```

在唯一 API30 设备上的技术 smoke：

```text
adb -s emulator-5554 install -r -d -g -> Success
monkey -p com.tiji.mistakes 1 -> Events injected: 1
ro.build.version.sdk -> 30
PID -> 4587
crash buffer -> 未发现该包 FATAL EXCEPTION
ANR 检索 -> 未发现该包匹配项
```

该产物使用 Android Debug certificate，只证明 API30 安装和启动技术路径，不能升级为正式 signed RC 的 Fresh Install 或 Runtime Gate 通过。

## GitHub 归档与范围边界

- 本文只提交到从 `main@483d4410...` 创建的 `codex/release-evidence-20260915` docs-only 分支，不作为 Release source，也不合并回 main。
- 没有向已冻结的 `codex/release-acceptance-api30` 继续推送。
- 没有创建、启动或运行 API35 模拟器或 API35 CI。
- 没有截图、视觉比对或人工视觉验收。
- 没有创建 tag、GitHub Release 或公开 APK。
- 后续只有在发布负责人明确形成 Path A、Path B 或 Path C 的真实事实后，才可从 `main@483d4410...` 进入正式签名流程。


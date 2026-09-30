# Tiji RELEASE_ACCEPTANCE SignerDecision / First Formal Release 执行改动详情

## 文档信息

- 日期：2026-09-15
- 对照方案：`C:\Users\23260\Downloads\Tiji_RELEASE_ACCEPTANCE_验收结果与下一步_SignerDecision_FirstFormalRelease_ExactRC_2026-09-15.md`
- 仓库：[stardawn2326/tiji](https://github.com/stardawn2326/tiji)
- 当前 `main`：`483d441020d83beffa9a96bd048dc426354f7f5a`
- 唯一验证环境：既有 `tiji-api30` AVD，serial `emulator-5554`，Android API 30
- API35：`OUT_OF_SCOPE`

## 本轮执行结论

最新方案确认 API30 CI 稳定性和代码门禁已经通过，当前唯一 P0 是 `SIGNER_LINEAGE_DECISION` 与正式签名材料。本轮没有继续修改产品功能、UI、复习逻辑、数据库、备份、AI/OCR 或发布签名实现；只完成 API30 Debug 技术安装复验和证据文档整理。

## Signer Decision 状态

本机受保护签名输入均为空：

```text
TIJI_SIGNING_STORE_FILE       = empty
TIJI_SIGNING_STORE_PASSWORD   = empty
TIJI_SIGNING_KEY_ALIAS        = empty
TIJI_SIGNING_KEY_PASSWORD     = empty
TIJI_PREVIOUS_SIGNER_SHA256   = empty
TIJI_FIRST_FORMAL_RELEASE     = empty
```

仓库与 Downloads 扫描范围未发现 `.jks`、`.keystore`、`.p12` 或 `.pfx`。当前只能保持：

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

没有把“未找到证据”改写成“历史上绝无正式 APK”，没有创建 First Formal Release 模式，没有伪造 previous signer，也没有生成正式签名 RC。

## API30 构建与安装

执行环境使用项目已有 JDK 17 与 Android SDK，构建命令：

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

API30 技术 smoke：

```text
adb -s emulator-5554 install -r -d -g -> Success
monkey -p com.tiji.mistakes 1 -> Events injected: 1
ro.build.version.sdk -> 30
PID -> 4494
crash buffer -> 未发现该包 FATAL EXCEPTION
ANR 检索 -> 未发现该包匹配项
```

该安装使用 Debug certificate，只证明 API30 安装和启动技术路径，不能升级为正式 signed RC 的 Fresh Install 或 Runtime Gate 通过。

## GitHub 与 CI 证据

- PR [#13](https://github.com/stardawn2326/tiji/pull/13) 已合并，合并提交为 `483d441020d83beffa9a96bd048dc426354f7f5a`。
- 合并后 `main` exact CI：[34914942125](https://github.com/stardawn2326/tiji/actions/runs/34914942125)，compile/package 与 API30 instrumentation 均 PASS。
- API30 exact-main 日志：71 项启动、4 项已知条件 skip、0 失败、75 项结束。
- 当前 GitHub Releases：`0`；Actions artifacts：`0`。
- 改动详情文档提交在只读 evidence 分支 `codex/release-acceptance-api30`，不改变 `main` 的冻结 SHA。

## 范围边界

- 没有创建、启动或运行 API35 模拟器或 API35 CI。
- 没有截图、视觉比对或人工视觉验收。
- 没有创建 tag、GitHub Release 或公开 APK。
- 后续只有在发布负责人明确确认 Path A、Path B 或 Path C 的真实事实后，才可从 `main@483d4410...` 进入对应正式签名流程。


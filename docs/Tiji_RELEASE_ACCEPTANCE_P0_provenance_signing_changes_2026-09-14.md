# Tiji RELEASE_ACCEPTANCE 发布工具链 P0 修复与正式签名 RC 收口记录

## 文档信息

- 日期：2026-09-14
- 对照方案：`C:\Users\23260\Downloads\Tiji_RELEASE_ACCEPTANCE验收与下一步_发布工具链P0修复_正式签名_RC收口方案_2026-09-14.md`
- 仓库：[stardawn2326/tiji](https://github.com/stardawn2326/tiji)
- 验证环境：既有 Android 11 / API 30 `emulator-5554`
- API35：`OUT_OF_SCOPE`

## 执行范围与边界

本轮执行用户要求的方案，完成发布工具链 P0 修复、GitHub 提交/PR/合并、API30 安装 smoke 和改动记录。方案中的 API30 是唯一验证环境；没有创建、启动或运行 API35，也没有做截图、视觉比对或像素级验收。

本轮只处理 Release signing/provenance harness 和 CI 自检。没有修改业务功能、UI、Room schema、Backup schema、复习算法、AI/OCR 业务实现或版本号。按照方案顺序，正式 `RELEASE_ACCEPTANCE` 未通过前，UI-00 至 UI-09 保持 HOLD。

## 主线集成证据

PR11 的功能范围已先合并并通过 exact-main 验证：

- PR11 merge commit：`36406bb3ef21c41181501580b8718cadfce34d27`
- PR11 exact-main CI：run `34823237910`，compile/package 与 API30 instrumentation 均 PASS
- PR11 应用源码漂移：`APP_CODE_DRIFT = 0`

本轮 Release hardening PR：

- PR：[ #12 · fix: make release artifact provenance fail closed](https://github.com/stardawn2326/tiji/pull/12)
- 分支：`codex/release-acceptance-api30`
- PR head：`0f400638f7638da42b3fd9980d65a65d87f01930`
- merge commit：`087710bcbb9d45ba0fcd3e6535ef5e0246edd48b`
- merge parents：`36406bb3ef21c41181501580b8718cadfce34d27`、`0f400638f7638da42b3fd9980d65a65d87f01930`
- merged_at：`2026-09-14T10:09:46Z`
- `RELEASE_MAIN_SHA`：`087710bcbb9d45ba0fcd3e6535ef5e0246edd48b`

## 本轮改动详情

### `scripts/verify-release.sh`

- 继续强制要求四项正式签名变量：`TIJI_SIGNING_STORE_FILE`、`TIJI_SIGNING_STORE_PASSWORD`、`TIJI_SIGNING_KEY_ALIAS`、`TIJI_SIGNING_KEY_PASSWORD`。
- 增加 `aapt`/`aapt2` 解析器查找，支持显式环境变量和 Android SDK build-tools；工具不存在、命令失败或没有输出时，以非零退出并输出 `ARTIFACT_PROVENANCE_GATE=FAIL`。
- 从 APK badging 读取并校验 `applicationId`、`versionCode`、`versionName`、`minSdk`、`targetSdk`、`compileSdk`。
- 固定确认 `applicationId = com.tiji.mistakes`，避免拿错 APK 仍被当作正式产物。
- 对 APK SHA256、signer SHA256、source SHA、APK size、JDK version、Gradle version 做非空、格式或数值校验；任何 `unknown` 或无法读取值都 fail closed。
- 保留 `apksigner verify --verbose --print-certs` 和 `TIJI_PREVIOUS_SIGNER_SHA256` 连续性检查；签名连续性失败仍归入 `RELEASE_SIGNING_GATE=FAIL`。
- 最终只在全部 provenance 字段有效且签名连续时输出 `RELEASE_SIGNING_GATE=PASS` 与 `ARTIFACT_PROVENANCE_GATE=PASS`。

### `scripts/verify-release-test.sh`

新增可在无真实 keystore 的 CI 环境运行的负向自检：

- 缺少四项 `TIJI_SIGNING_*` 时必须非零退出并标记 signing FAIL。
- 元数据工具失败时必须非零退出并标记 provenance FAIL。
- 任一字段为 `unknown` 时必须非零退出并标记 provenance FAIL。
- 同时执行两个脚本的 `bash -n` 语法检查。

### `.github/workflows/android.yml`

在 compile/package job 中加入 `Self-check release harness` 步骤，先执行上述负向自检，再运行原有 Gradle 编译、单测、lint 和 package。instrumentation job 仍固定：

    system-images;android-30;default;x86_64

没有加入 API35 job 或 API35 兼容性门禁。

## CI 验证

代码分支与 PR required checks：

- 分支 push run [34830742913](https://github.com/stardawn2326/tiji/actions/runs/34830742913)：compile/package PASS，API30 instrumentation PASS；自检步骤 PASS。
- PR required run [34831222530](https://github.com/stardawn2326/tiji/actions/runs/34831222530)：compile/package PASS，API30 instrumentation PASS；自检步骤 PASS。

合并后的 exact-main run：

- run [34831763104](https://github.com/stardawn2326/tiji/actions/runs/34831763104)，head `087710bcbb9d45ba0fcd3e6535ef5e0246edd48b`
- compile/package job `103938336739`：PASS
- API30 instrumentation 重跑 job `103938335018`：PASS（attempt 2）
- API30 日志：`Starting 71 tests`、`Finished 75 tests`、4 skipped、0 failed、`BUILD SUCCESSFUL`
- 第一次 attempt 因 `FocusedReviewRecreationTest.repeatedGradeTapsRecordAtMostOneReviewPerQuestion` 出现一次 `review_grade_good` 注入失败而失败；此前分支与 PR run 相同测试均通过，因此按基础设施/测试注入波动重跑失败 job，没有修改业务代码。

## API30 安装与启动 smoke

使用本机既有 SDK 的 `D:\android\sdk\platform-tools\adb.exe` 与 `emulator-5554`，设备 API 为 30。安装目标是当前 R8 技术产物：

- APK：`app/build/outputs/apk/release/tiji-v1.0.0-release.apk`
- SHA-256：`9D8E2F0E4079431765ECF438C02F64722437A27A221D31653E112C969A42B0E5`
- 包名：`com.tiji.mistakes`
- `versionCode = 114`
- `versionName = 1.0.0`
- `minSdk = 26`
- `targetSdk = 34`
- `compileSdk = 34`
- 当前证书：Android Debug，SHA-256 `24300ED6EC1BA17E7004CD3156FDE97E5568D09B8C6D921D9C9BB24C597EC9F1`

已完成的设备技术路径：

    adb uninstall com.tiji.mistakes -> Success
    adb install tiji-v1.0.0-release.apk -> Success
    am start -W com.tiji.mistakes/.MainActivity -> Status: ok
    crash buffer after clear -> empty
    ANR check -> empty

本轮在 exact-main 合并后再次执行保留数据安装：

    adb install -r -d -g tiji-v1.0.0-release.apk -> Success
    am start -W com.tiji.mistakes/.MainActivity -> Status: ok
    API30 = 30
    PID = 15805
    crash/ANR check = empty

这证明 API30 的安装和启动技术路径可用，但当前产物是 debug certificate，不能把技术 smoke 升级为正式 signed RC 的 `FRESH_INSTALL_GATE` 或 `RELEASE_APK_RUNTIME_GATE` PASS。

## 当前 Release Acceptance 门禁

| 门禁 | 结果 | 说明 |
| --- | --- | --- |
| `RELEASE_CODE_GATE` | PASS | PR12 required checks 与 exact-main compile/package、API30 instrumentation 重跑均通过。 |
| `RELEASE_HARDENING_CODE` | PASS | provenance fail-closed、包名校验、字段校验和 CI 自检已实现。 |
| `PROVENANCE_METADATA_FAIL_CLOSED` | PASS | 缺工具、工具失败、空输出、`unknown` 或格式错误均非零退出。 |
| `SIGNER_CONTINUITY_LOGIC` | PASS | 保留历史 signer SHA256 比对，秘密不写入仓库或文档。 |
| `RELEASE_SIGNING_GATE` | FAIL | 当前环境没有四项正式签名变量与历史 accepted signer。 |
| `ARTIFACT_PROVENANCE_GATE` | FAIL | 当前可安装 R8 产物使用 Android Debug certificate，尚未生成正式签名 RC。 |
| `FRESH_INSTALL_GATE` | NOT VERIFIED | API30 技术 Fresh Install 已完成，但未使用正式 signed RC。 |
| `UPGRADE_GATE` | NOT VERIFIED | 缺少 previous accepted APK 与正式 signer 连续性证明。 |
| `DATA_RETENTION_GATE` | NOT VERIFIED | 未在正式 signed RC 上做原地升级后的 Room/DataStore/图片数据实测。 |
| `CROSS_VERSION_BACKUP_GATE` | NOT VERIFIED | 未在正式 signed RC 上完成旧备份 MERGE/REPLACE 实测。 |
| `BACKUP_CRASH_RECOVERY_GATE` | NOT VERIFIED | 未在正式 signed RC 上做崩溃注入、marker/journal 清理和重启一致性实测。 |
| `OCR_OFFLINE_RELEASE_GATE` | NOT VERIFIED | 缺少正式 Release 的模型、真实图片和断网全链路实测。 |
| `AI_EXTERNAL_PROVIDER_GATE` | NOT VERIFIED | 当前没有真实 provider endpoint、model 和 API key。 |
| `AI_PRIVACY_GATE` | STATIC REVIEW ONLY | `SecureKeyStore` 与备份排除逻辑已静态检查，未进行真实 provider 网络验收。 |
| `RELEASE_APK_RUNTIME_GATE` | NOT VERIFIED | 已完成 debug-signed R8 启动 smoke，未完成正式 RC 全页面 runtime gate。 |
| `PDF_RELEASE_GATE` | NOT VERIFIED | 未完成正式 signed Release 的题目、解析、知识点、复习 PDF 全量实测。 |
| `PRIVACY_PERMISSION_GATE` | STATIC REVIEW ONLY | foreground AI/OCR 通知调用存在；未把 `POST_NOTIFICATIONS` 误判为复习提醒。 |

当前总状态：

    RELEASE_ACCEPTANCE = NOT COMPLETE
    FUNCTION_FINAL_FREEZE = HOLD
    UI_REDESIGN = HOLD

## 正式签名 RC 的外部输入

正式 RC 只能从 `main@RELEASE_MAIN_SHA` 生成，并且必须通过受保护环境变量提供：

    TIJI_SIGNING_STORE_FILE
    TIJI_SIGNING_STORE_PASSWORD
    TIJI_SIGNING_KEY_ALIAS
    TIJI_SIGNING_KEY_PASSWORD
    TIJI_PREVIOUS_SIGNER_SHA256

keystore、密码、API key 和真实 provider 数据没有写入 GitHub 或本 MD。没有这些输入时，脚本会 fail closed，不会把 debug certificate 伪装成正式 RC。

本轮没有创建 tag、GitHub Release，也没有发布 APK。API35 继续保持 `OUT_OF_SCOPE`；UI 结构重设计要等 `RELEASE_ACCEPTANCE = PASS` 后再按 UI-00 至 UI-09 顺序启动。

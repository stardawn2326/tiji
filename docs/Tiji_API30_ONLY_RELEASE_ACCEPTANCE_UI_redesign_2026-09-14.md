# Tiji API30-only Release Acceptance 与 UI 大改执行记录

## 文档信息

- 版本：1.0
- 日期：2026-09-14
- 对照方案：`C:\Users\23260\Downloads\Tiji_API30_ONLY_功能冻结_RELEASE_ACCEPTANCE_UI大改总方案_2026-09-14.md`
- 目标仓库：[stardawn2326/tiji](https://github.com/stardawn2326/tiji)

## 执行边界

- `SUPPORTED_VALIDATION_API = 30`。
- 唯一设备为既有 `emulator-5554`，Android 11 / API 30。
- 未创建、启动或运行 API35 模拟器；API35 validation 保持 `OUT_OF_SCOPE`。
- 按用户要求未做截图、视觉比对或像素级验收。
- 当前方案要求先完成 main 集成和 Release Acceptance，再开始 UI-00；Release Acceptance 尚未满足，因此 UI 大改保持 HOLD。

## 第一阶段：PR11 集成

- PR：[ #11 · feat: close function scope P0 review flow](https://github.com/stardawn2326/tiji/pull/11)
- PR accepted head：`51ce970fe0e2fd071c248c73e67fa35834a171c2`
- Merge commit：`36406bb3ef21c41181501580b8718cadfce34d27`
- Merge parents：`cf39d7a87bc5463ea116c31f432cc355335b8cff`、`51ce970fe0e2fd071c248c73e67fa35834a171c2`
- `merged_at`：`2026-09-14T08:32:23Z`
- `MAIN_AFTER_PR11`：`36406bb3ef21c41181501580b8718cadfce34d27`
- PR11 head 与 merge tree 的应用源码对比：`APP_CODE_DRIFT = 0`。

## exact-main CI

针对 `main@36406bb3ef21c41181501580b8718cadfce34d27` 的 post-merge run：

- [Actions run 34823237910](https://github.com/stardawn2326/tiji/actions/runs/34823237910)：`success`
- `Compile, test, lint and package`：job `103909370971`，`BUILD SUCCESSFUL`。
- `Android instrumentation (API 30)`：job `103909370802`，`BUILD SUCCESSFUL`。
- API30 日志：`Starting 71 tests`；`Finished 75 tests`；`4 skipped`；`0 failed`。
- 结论：`PR11_MERGED = YES`、`MAIN_COMPILE_TEST_LINT_PACKAGE = PASS`、`MAIN_API30 = PASS`、`FUNCTION_SCOPE_FINALIZED = PASS`。

## 本轮代码改动

### Release 签名 fail-closed

- `app/build.gradle.kts` 增加 `TIJI_REQUIRE_RELEASE_SIGNING=true` 模式。
- 正式验收模式缺少任一 `TIJI_SIGNING_STORE_FILE`、`TIJI_SIGNING_STORE_PASSWORD`、`TIJI_SIGNING_KEY_ALIAS`、`TIJI_SIGNING_KEY_PASSWORD` 时，Gradle 在配置阶段失败。
- 普通本地 debug/API30 测试仍保留 debug keystore fallback，避免影响既有自动化测试。
- 新增 `scripts/verify-release.sh`，构建 R8 release、执行 `apksigner verify --print-certs`，输出 source SHA、版本、APK 大小与 SHA256、签名证书 SHA256、SDK 元数据和构建命令，并要求 `TIJI_PREVIOUS_SIGNER_SHA256` 进行签名连续性比较。

## Release Acceptance 门禁

| 门禁 | 当前结果 | 证据或原因 |
| --- | --- | --- |
| `RELEASE_CODE_GATE` | PASS | exact-main CI 的编译、单测、lint、package 与 API30 instrumentation 通过。 |
| `RELEASE_SIGNING_GATE` | FAIL | 当前环境缺少四项 `TIJI_SIGNING_*`；正式脚本按设计 fail closed。 |
| `FRESH_INSTALL_GATE` | NOT VERIFIED | 自动审批拦截了会清除模拟器数据的 `adb uninstall`；仅执行了保留数据的 `adb install -r -d -g` 技术 smoke。 |
| `UPGRADE_GATE` | NOT VERIFIED | 没有可证明签名连续性的正式 RC 与历史 accepted APK 组合。 |
| `DATA_RETENTION_GATE` | NOT VERIFIED | 现有 Room/DataStore 测例不等同于 signed Release 原地升级后的实测。 |
| `CROSS_VERSION_BACKUP_GATE` | NOT VERIFIED | 现有备份兼容测例通过，但未在正式 RC 上完成旧备份 MERGE/REPLACE 实测。 |
| `BACKUP_CRASH_RECOVERY_GATE` | NOT VERIFIED | 已有 API30 事务/marker 测例通过，未在正式 signed Release 上完成崩溃注入。 |
| `OCR_OFFLINE_RELEASE_GATE` | NOT VERIFIED | 未执行正式 Release 的模型下载、断网、重启和真实图片 OCR 全链路。 |
| `AI_EXTERNAL_PROVIDER_GATE` | NOT VERIFIED | 没有真实 provider endpoint、model 和 API key；不写成 PASS。 |
| `AI_PRIVACY_GATE` | STATIC REVIEW ONLY | API key 使用 `SecureKeyStore`，备份注释明确排除密钥；未做真实 provider 网络验收。 |
| `RELEASE_APK_RUNTIME_GATE` | NOT VERIFIED | 已完成 debug-signed R8 release 启动 smoke，正式签名门禁仍失败。 |
| `PDF_RELEASE_GATE` | NOT VERIFIED | 未完成 signed Release/R8 的题目、解析、知识点、复习 PDF 全量实测。 |
| `PRIVACY_PERMISSION_GATE` | STATIC REVIEW ONLY | `POST_NOTIFICATIONS` 与 data-sync foreground service 有实际通知调用；未完成最终人工权限审计。 |
| `ARTIFACT_PROVENANCE_GATE` | FAIL | 当前 release 产物使用 Android Debug 证书，不能作为正式 RC。 |

总状态：

```text
RELEASE_ACCEPTANCE = NOT COMPLETE
FUNCTION_FINAL_FREEZE = HOLD
UI_REDESIGN = HOLD
```

## API30 Release/R8 技术 smoke

当前可复现产物：

- APK：`app/build/outputs/apk/release/tiji-v1.0.0-release.apk`
- SHA-256：`9D8E2F0E4079431765ECF438C02F64722437A27A221D31653E112C969A42B0E5`
- 包名：`com.tiji.mistakes`
- `versionCode = 114`、`versionName = 1.0.0`
- `minSdk = 26`、`targetSdk = 34`、`compileSdk = 34`
- `apksigner` 证书 SHA-256：`24300ED6EC1BA17E7004CD3156FDE97E5568D09B8C6D921D9C9BB24C597EC9F1`
- 证书 DN：`C=US, O=Android, CN=Android Debug`

在既有 API30 设备上执行了保留数据安装与启动检查：

```text
adb install -r -d -g -> Success
am start -W com.tiji.mistakes/.MainActivity -> Status: ok
launch state -> COLD
crash buffer after clear -> empty
ANR check -> empty
```

该结果是技术 smoke，不升级为正式 `FRESH_INSTALL_GATE` 或 `RELEASE_APK_RUNTIME_GATE` PASS。

## UI 阶段

按照方案顺序，以下阶段尚未启动：

```text
UI-00 Design System / App Shell
UI-01 Home
UI-02 Library
UI-03 Mistake Detail
UI-04 Capture / Edit
UI-05 AI Solve
UI-06 Review
UI-07 Knowledge
UI-08 Settings
UI-09 PDF / Dialog / Sheet / Auxiliary
```

原因是方案要求 `RELEASE_ACCEPTANCE = PASS` 后才能进入 `UI_ARCH_FREEZE`。当前缺少正式签名材料、签名连续性证明、历史版本升级数据、真实 OCR/AI provider 条件，因此不能提前声称 UI 冻结或 Release 通过。

## GitHub 发布记录

- 本轮分支：`codex/release-acceptance-api30`。
- 分支基线：`MAIN_AFTER_PR11 = 36406bb3ef21c41181501580b8718cadfce34d27`。
- 不创建 tag、不创建 GitHub Release、不发布 APK。
- API35 不进入分支、CI 或验收门禁。

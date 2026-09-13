# Tiji FUNCTION_FREEZE 最终收口修复执行改动详情

日期：2026-09-13
依据：`C:\Users\23260\Downloads\Tiji_FUNCTION_FREEZE_最终收口修复方案_2026-09-13.md`
仓库：`https://github.com/stardawn2326/tiji`
分支：`codex/ui-2.0-paper-blue`
PR：[#8](https://github.com/stardawn2326/tiji/pull/8)，保持 OPEN，不合并

## 1. 本轮边界

附件是本轮 FUNCTION_FREEZE 的实施规范；用户请求是直接执行、安装到模拟器、推送指定 GitHub 仓库并提供改动详情。实现集中在功能冻结、数据一致性、图片资产、AI 修复门禁、持久化和自动化回归，不追加视觉大改。

验证环境只有既有 API30 模拟器 `emulator-5554`（`ro.build.version.sdk=30`）。工程继续使用 compile/target SDK 34；没有创建、安装或运行 API35 模拟器，也没有新增 API35 CI 验收路径。用户明确不需要视觉检查，因此不把截图或人工视觉流程写成已完成验收。

## 2. 代码改动

### P0-1：备份导入跨存储恢复边界

- 新增 `BackupImportCoordinator`、`BackupImportJournal` 和阶段枚举：`VALIDATED → STAGED → SNAPSHOT_CREATED → FILES_PUBLISHED → ROOM_COMMITTED → PREFERENCES_COMMITTED → COMMITTED`。
- 导入前写入持久化 journal、暂存目录、回滚目录、旧图片引用和便携设置快照；图片先校验并暂存，再以稳定且带内容摘要的目标名发布。
- 应用启动和下一次导入前都会检查未完成 journal：未发布阶段清理暂存，发布后根据 Room 当前引用和设置快照执行补偿，已提交阶段只清理临时目录。
- Room 事务失败会删除本次新文件并补偿 DataStore 设置；成功后回收旧记录和新产生但未被引用的文件。
- `BackupImportCoordinatorTest` 覆盖 journal 在新的 coordinator 实例中恢复阶段和快照；`BackupService` 保留原有 MERGE/REPLACE 稳定 ID 与关系处理。

### P0-2：AI 修复只能在二次验证通过后发布

- 新增 `AiRepairPolicy`，把“修复候选”和“已发布解答”分开。
- 首次 V2 校验失败时保留原解答；修复结果必须再次通过完整 V2 校验且状态为 `PASS` 才能替换正文。
- 修复为空、协议不完整、二次验证失败、超时或服务不可用时继续显示原解答，并保留复核状态和诊断信息。
- `AiSolutionVerifierTest` 增加 PASS、warning、failed、unavailable、malformed 和 empty candidate 覆盖。

### P0-3/P0-4：图片资产与重复题更新闭环

- 新增 `MistakeAssetManager`，统一收集题目图、来源图、答案图、解析图、content block 的 `path/sourcePath`，以及 AI 识别/解题/追问状态中的图片引用。
- 重复题“更新已有”先完成 Room 写入，再按旧/新引用差集回收；更新失败时录题页仍持有新图片，交由重试或离开录题页时的全局引用检查回收，避免用户重试时出现失效图片。
- 录题模式切换、识别取消、内容块删除、退出录题和保存失败都在清理前保留旧 ownership 集合。
- 答案图和解析图改用 `GetContent` 单选；题目图仍支持多选，避免单图槽位复制并丢弃多余文件。

### P0-5：MistakeDraft 成为录题状态事实源

- `MistakeDraftState`、`MistakeDraftController`、`MistakeDraftAction` 覆盖文本、科目、题型、tags、五档难度、复习计划、采集模式、AI 来源、题目图片列表、答案图、解析图、content blocks 和识别关联状态。
- `CaptureDraftBindings` 将 `CaptureScreen` 的可保存状态绑定到 controller reducer；Compose 字段通过 action 更新 Draft，保存时直接从同一 Draft 转换为 `MistakeEntity`。
- 新增 Draft JSON saver，进程重建时恢复文本、元数据和图片引用；模式切换先计算释放集合，再由 `MistakeAssetManager` 做全局引用检查。

### P1：复习、日期、格式和存储收口

- `ReviewSessionController.removeMistake` 只移除当前题目、调整队列索引并保留其他题和已写入历史；最后一题移除时进入 `COMPLETING`，不清空整场。
- `LearningCalendar` 统一本地日历起始日、跨日期间隔、下一学习日和 DST 计算；`ReviewScheduler` 不再使用固定毫秒天数。
- 备份导出/导入保留 `.heic`、`.heif` 扩展名，图片 round-trip 不再强制改成 JPG。
- `DurableTextStore` 使用 tmp 写入、flush/sync、bak 备份和启动恢复；旧目标缺失时恢复 `.bak`，陈旧 `.tmp` 不会被当成已提交正文。
- `DataResetCoordinator` 明确“清除学习数据”和“恢复出厂设置”的数据集合；`masteryLabel`、`LIBRARY_SELECTION` 和旧备份统计入口仅保留兼容读取并标记弃用。

## 3. 验证结果

### 编译、静态检查和打包

- Java 17 下 `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin --offline --no-daemon`：通过。
- `:app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease --offline --no-daemon`：通过；仅有既有弃用提示和 Android SDK XML 版本提示，没有 lint error。
- `git diff --check`：通过。

### API30 instrumentation

- 设备：`tiji-api30 (AVD) - 11`，serial `emulator-5554`，SDK 30。
- 最终源码重新安装后执行 `:app:connectedDebugAndroidTest --offline --no-daemon`。
- Gradle 运行报告：54 个 instrumentation 用例，4 个既有条件跳过，0 失败；原始 test log 以 `OK (54 tests)` 结束。
- 日志文件：`app/build/outputs/androidTest-results/connected/debug/tiji-api30(AVD) - 11/testlog/test-results.log`。

### 本机 JVM 测试边界

当前 Windows 中文项目路径下，Gradle JVM 测试执行器曾在测试类已生成时统一报 `ClassNotFoundException`，属于本机 worker 类路径问题。按工程记录使用临时 ASCII 驱动器映射重新执行 `:app:testDebugUnitTest`，结果为 36 个 XML suite、191 tests、0 failures/errors、0 skipped；映射已在命令结束后移除。这个结果证明源码单测通过，但仍不替代人工视觉验收。

## 4. 安装产物

- Debug APK：`app/build/outputs/apk/debug/tiji-v1.0.0-debug.apk`
  - 大小：22,534,431 bytes
  - SHA-256：`CBEA2D6357843FD9CCAC14454D12E1AE258C98F58F148D7BD2E4451526D746E4`
- Release APK：`app/build/outputs/apk/release/tiji-v1.0.0-release.apk`
  - 大小：4,281,621 bytes
  - SHA-256：`2FE70F66C2C02C076881F99230D2EAA02C44BABEB2ED3FFC8AC328B04EC66046`
- 已执行 `adb -s emulator-5554 install -r`，结果为 `Success`。
- 设备侧包信息：`com.tiji.mistakes`，`versionCode=114`，`versionName=1.0.0`，`minSdk=26`，`targetSdk=34`。

## 5. GitHub 发布记录

目标 remote 已核对为：

`https://github.com/stardawn2326/tiji.git`

本地提交和远程 head、GitHub Actions run、PR 状态将在提交推送后回填到本节。推送只更新 `codex/ui-2.0-paper-blue`，不执行 PR 合并。

## 6. 验收边界

自动化编译、lint、Debug/Release 打包和 API30 instrumentation 已完成；人工 FUNCTION_FREEZE E2E 和视觉验收由用户在已安装模拟器上验收。本轮没有做截图、视觉比对或 API35 验证。

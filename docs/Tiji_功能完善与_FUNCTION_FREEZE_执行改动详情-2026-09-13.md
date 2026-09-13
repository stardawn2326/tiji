# 题迹功能完善与 FUNCTION FREEZE 执行改动详情

日期：2026-09-13  
依据：`Tiji_功能完善与_FUNCTION_FREEZE_下一步实施方案_2026-09-13.md`  
仓库：`https://github.com/stardawn2326/tiji`  
分支：`codex/ui-2.0-paper-blue`

## 交付边界

附件是本轮的功能实现规范；用户请求是按规范直接实施、安装到模拟器、推送到指定 GitHub 仓库并提供本说明。实现范围集中在功能底座、数据语义、持久化、恢复和测试契约，不新增视觉验收步骤。验证设备始终是现有的 API30 模拟器 `emulator-5554`；本轮没有启用其他 Android 验证环境。

## 已完成的功能改动

### 复习记录与复习计划

- `ReviewRecordEntity` 成为复习结果的唯一事实来源；首页、错题库、日历、知识点和复习页都从 Room 记录读取最近评价。
- “熟练”是唯一自动退出复习计划的评价；“掌握”仍留在计划中。
- 评价为“熟练”时保留历史、写入记录并设置 `inReviewPlan = false`；从错题库重新加入时只恢复计划和下一学习日时间，不删除或重置历史。
- 相同题目、相同评价在短时间内重复点击只保留一次记录，避免重复写入。
- 兼容保留旧 DataStore 读写 API，但旧 `reviewMastery`/`reviewProgress` 不再参与产品逻辑，也不再写入新备份。

### 五档难度

- 新增 `domain/Difficulty.kt`，统一定义 `1..5`：极简、简单、一般、困难、极难。
- 编辑、批量编辑、AI 分类、识别提示、筛选、卡片、备份导入和恢复都使用同一映射。
- `0` 仅作为旧数据的未设置 sentinel；旧值 `5` 原样保留，不再降级为 `4`。

### 数据库写入与历史保护

- `MistakeDao` 的错题写入改为 `@Upsert`，关系表和复习记录继续使用明确的插入策略；没有对错题主表使用破坏历史的 `REPLACE` 路径。
- 重复题更新保留原 `stableId`、复习字段和历史关系，只替换允许更新的内容。
- 备份导入按 stableId，再按 fingerprint 匹配；MERGE 和 REPLACE 均在 Room 事务中处理错题、复习记录、知识点和交叉引用。

### 图片与文件资产

- 引用扫描覆盖题目图、来源图、答案图、解析图、内容块 `path/sourcePath`，以及 AI 解题、追问、历史和识别状态中的图片。
- 删除、取消识别、退出录题、删除解题内容块时都先做引用检查，再回收未被其他状态持有的文件。
- `ImageStorage` 按真实 MIME/扩展名保存 JPG、PNG、WEBP、HEIC/HEIF，并在复制失败时清理半成品。
- 备份图片先写入 `filesDir/staging/import_*`，Room 提交后再迁移到正式目录；迁移过程保留回滚副本，失败时恢复已触及的目标并清理暂存文件。

### 备份与恢复

- 备份格式继续使用 schema 3，包含错题、复习记录、知识点、交叉引用和非敏感设置；API Key 不进入备份。
- 导入前严格校验归档、条目数量、大小和图片引用；缺图不会进入数据库事务。
- MERGE 保留本地较新的记录，REPLACE 清理旧关系后按稳定引用恢复；重复导入不会重复复习记录。
- DataStore 设置导入放入 Room 事务边界内，避免设置写入成功而数据库回滚的分裂状态。

### AI 协议与长文本

- 新解题发布前必须解析为完整且按顺序的 `TIJI_SOLUTION_V2` 四段：`recognition → approach → derivation → finalAnswer`。
- 校验失败最多执行一次 V2 修复；修复为空、协议不合法或复核失败时保留原解答并显示校验状态，不静默替换为普通文本。
- 解题、校正、追问、历史和流式状态改用应用私有的原子文本文件存储，不再使用固定长度截断或固定条数裁剪。
- 识别警告、诊断和模型元数据仍可按界面需要限长；题目、答案、解析和追问正文不截断。

### 录题、重复题、PDF 与设置

- 新增显式 `MistakeDraft`、`MistakeDraftMetadata`、`MistakeDraftAssets` 边界；草稿包含采集模式和 AI 识别来源，保存时再映射到 Room 实体。
- 录题取消、模式切换、识别结果删除、内容块删除和退出页面统一走资产回收链；重复题更新不改变原题历史。
- PDF 仅保留题目/练习和答案/解析两种模板，移除解析文本的静默裁剪。
- 设置页拆分“清除学习数据”和“恢复出厂设置”：前者保留外观、AI 配置和密钥，后者额外清理配置和 Android Keystore；Profile 删除支持恢复文本配置、视觉配置和绑定关系。

## 第二遍反查结论

| 检查项 | 结论 |
| --- | --- |
| 四档难度、`coerceIn(0, 4)`、`5 → 4` | 产品代码无命中；统一入口为 `Difficulty`，保留 `0` sentinel |
| 复习退出数值阈值 | 已改为 `ReviewGrade.EASY` 的明确语义 |
| 错题主表 `OnConflictStrategy.REPLACE` | 主表已移除；Room 关系表的历史 SQL/迁移仅作兼容记录 |
| 旧 `reviewMastery`/`reviewProgress` | 仅保留旧安装/旧备份读取所需符号，不作为新事实源 |
| 图片删除与 `contentBlocks.sourcePath` | 引用集合已覆盖，删除前统一检查 |
| 解题/追问/历史固定长度截断 | 正文截断已移除；剩余 `take` 仅用于诊断、候选数量、标题元数据或算法窗口 |
| 普通文本降级解题 | 新解题发布要求完整 V2；一次修复也要求 V2 |
| PDF 模板数量与解析裁剪 | 两种模板，完整解析内容保留 |
| API 验证环境 | 仅 API30 `emulator-5554`，项目配置 compile/target 为 34 |

## 验证记录

### 静态与构建

- `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin --offline`：通过。
- `git diff --check`：通过。
- `:app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease --offline`：通过。Lint 无 error；报告中的 warning 为既有兼容/第三方或 Android API 提示。
- Release 构建只出现原有 native 库无法 strip 的 packaging warning，未阻断产物生成。

### 单元测试

- 本机 Gradle 测试任务曾在执行测试类时统一报 `ClassNotFoundException`，即使 Kotlin 测试 class 已生成；失败报告：`app/build/reports/tests/testDebugUnitTest/index.html`。
- 该结果是本机 Gradle 测试运行器类路径问题，不能等同于源码断言失败；CI 任务仍作为干净环境的单测裁决。

### API30 instrumentation

- 设备：`tiji-api30 (AVD) - 11`，serial `emulator-5554`，`ro.build.version.sdk=30`。
- 全量 `:app:connectedDebugAndroidTest --offline`：52 项测试启动，报告 XML 为 52 tests、4 skipped、0 failures；Gradle 日志因参数化用例显示完成 56 tests。
- 备份回归、复习确认、错题库筛选和详细页回归均在全量前单独重跑通过。
- 本轮没有做截图、视觉比对或人工视觉检查。

## 安装产物

- 安装文件：`app/build/outputs/apk/debug/tiji-v1.0.0-debug.apk`
- SHA-256：`F989234CF2475C4E2647F46DEEC9EEE4EE25528CA61BFE6FA7F35572257065BB`
- 已执行 `adb -s emulator-5554 install -r`，结果为 `Success`。
- 已启动 `com.tiji.mistakes`；设备侧核验 `versionCode=114`、`versionName=1.0.0`、`targetSdk=34`、`primaryCpuAbi=x86_64`。

## GitHub 发布

代码和本说明会一起提交到 `codex/ui-2.0-paper-blue`，推送目标固定为：

`https://github.com/stardawn2326/tiji.git`

提交、远端分支 SHA、GitHub Actions run 和现有 PR #8 的最终状态在推送完成后补写到本节，并以远端 API/tree 核验为准。

## 验收边界

本轮交付的是功能冻结底座和 API30 可复现证据。人工端到端操作和视觉验收仍由用户在已安装模拟器上完成；本说明不把本机 Gradle 类路径异常或未执行的视觉检查记为通过。

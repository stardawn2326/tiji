# 题迹架构优化与产品 README

日期：2026-09-30。基线：main `9eb5a60208c3bfc9078a5e97c0f51a6d1d8a8746`。

## 检查范围

沿用前一轮完整审查与合并后的源码，继续检查 Room/Repository、领域计算、AI/OCR 后台任务、图片与公式缓存、导航状态、PDF、备份恢复、密钥及构建发布。本轮生产 Kotlin 共 167 文件、35,659 行：data 19、domain 16、service 43、ui 88、MainActivity 1。源码清单与 SHA-256 保存在本机 `outputs/architecture-20260930-inventory.json`。第三方 OCR 原生二进制不属于逐行源码审查范围。

## 已实施的优化

### 1. 复习派生数据由后台计算并统一发布

原根页面在 Compose 组合时生成每日计划、计算复习统计，并分别订阅四项复习设置。统计的 remember 只依赖记录，时间窗口发生变化而记录未变化时可能不更新。

- `AppPreferences.reviewPlanningSettings` 从同一次 DataStore 快照读取计划开关、每日题量、随机开关与科目安排，并去除相同值；修改主题或 AI 配置不会重建计划。
- 新增 `StudyOverviewCoordinator`，继续调用原 `DailyStudyPlanner` 与 `ReviewAnalytics`，计算在 `Dispatchers.Default` 执行。
- 时钟、设置、每日计划、统计与当日复习状态作为一个 `StudyOverviewState` 发布，避免跨午夜或切换设置时把新日期/设置与旧派生结果混用。
- 知识点进度计算也移至 Default。根页面只消费结果，原来的计划计算与分散订阅代码被删除。

抽题、排序、评分和会话队列规则保持原实现，未增加可靠/快速模式或模型限制。

### 2. 追问增量只持久化变化正文

原追问每收到一段文本，先读取四个正文槽、反序列化历史，再写回当前提问、累计回复、上次提问与历史消息。

- 新增 `AiChatStateStore.appendStream`：只读写累计回复槽，并更新进度/错误字段。
- 请求 ID、运行状态检查与写入共用原来的跨实例共享锁，已停止或已替换请求的迟到片段不会写入。
- 写盘成功后才更新进度并发送任务通知；写盘失败保留已提交回复和原进度。
- 每个增量仍立即持久化，没有通过减少保存次数来牺牲中断恢复内容。启动、完成、停止等完整状态提交保持原逻辑。

设备回归确认连续三个增量仅写三次 `streamed.tmp`，没有重复写入提问和历史。回复槽仍保存完整累计正文；这不是追加日志或延迟批量写入方案。

### 3. 缓存失效记录有容量上限

原缓存位图有上限，但按题目保存的失效代数使用无界 Map，删除和复习的题目增多后记录仍持续增长。

- 新增 `MathSnapshotGenerations`，使用按访问顺序淘汰的 Map，默认最多 512 条记录。
- 每个 owner 获得单调递增 token；淘汰后再次使用、或清空后再次使用，都不会复用旧 token。
- 保留按题、按区域失效，旧渲染回调无法在删除、完成复习或清空后恢复过期缓存。
- 20 MiB 内存位图缓存、128 MiB 磁盘缓存与现有渲染方式保持不变。

回归覆盖 10,000 个 owner 的容量压力（测试容量 8）、淘汰重用、不同区域隔离与清空后的旧回调拒绝。

### 4. 完整复习历史仅由日历订阅

原根页面一直订阅完整复习记录，导航图再为所有日期分组，实际普通页面只需要当天状态。

- 普通页面使用近 30 天记录派生的当日状态。
- 完整历史读取和按日期整理移到日历专用状态流；只在日历目的地订阅。
- 离开日历 5 秒后停止订阅并释放保留的完整状态 Map。
- 同题同日多次复习选择最后一次反馈，用时间与记录 ID 判定，不依赖输入列表顺序。日期继续采用应用原有本地日历规则。

数据库仍是事实来源，没有新增持久化副本或数据库版本迁移。

## README 与开发文档

根目录 `README.md` 重写为产品功能介绍：三种录题方式、AI 解题与追问、科目/知识点、每日复习、题目 PDF、本机备份、八种主题与开始使用。

旧 README 的内部排版参数与实现细节移出首页；构建环境、验证命令、隔离设备测试、Windows JUnit 回退执行器、签名配置和源码目录说明集中到 `docs/DEVELOPMENT.md`。未加入尚未实现的产品承诺。

## 各模块复核与后续方向

| 模块 | 本轮结果 | 仍有价值的后续工作 |
| --- | --- | --- |
| Room/Repository | 查询、事务、迁移与知识点关系继续使用现有实现；普通页面不再读完整复习历史 | 大题库仍有全量错题订阅；根据设备内存和帧耗时决定进一步查询下沉或分页 |
| 领域规则 | 计划、统计、进度计算离开组合阶段；日期与结果统一发布 | 主 ViewModel 仍集中管理多个领域，可以按稳定边界逐步拆分 |
| AI/OCR 与持久化 | 减少追问增量重复文件操作；保留共享锁、通知与中断恢复 | 回复正文仍随长度增长而整槽重写；如改为日志或批量发布，需要专项恢复测试 |
| 导航/页面 | 减少根页面派生逻辑与全历史输入；保留现有滚动和会话状态 | 大量数据加载与快速导航交错需要真实手机 trace 验证 |
| 图片/公式缓存 | 失效记录限制容量，旧 token 不复用 | 不改变现有照片预览与实时渲染选择；极端图片/公式仍需设备性能测量 |
| PDF | 现有分页、图片尺寸、自适应与作答区回归继续覆盖 | WebView 绘制仍需主线程，大批量输出另做测量 |
| 备份恢复 | 保留现有恢复 Mutex、journal、资产限制和提交协议 | 大备份当前仍整包/多 ByteArray 驻留；流式暂存需要大备份和故障注入验证 |
| 密钥/构建发布 | 不更换证书，不读取或提交用户密钥；维持受保护 main 的 PR/CI 流程 | 当前版本号沿用旧值；正式分发应建立独立版本编号与制品追踪 |

## 验证

设备复跑发现 `LargeFontAccessibilityTest` 在修改系统配置后立即手动 recreate，与系统自动重建竞争，报 Activity DESTROYED。调整夹具：等 shell 命令结束，等待实际字体比例/深色模式和 RESUMED 状态，再验证五个导航入口；保留原导航断言。失败日志与对应 logcat 保存在 `outputs/architecture-20260930-regression-final.log` 和 `outputs/architecture-20260930-accessibility-failure-logcat.txt`。

随后本机设备测试在库搜索输入时被打断，同一时段 logcat 记录 Trime 输入法 `_activeTheme` 未初始化崩溃。复核暂时使用系统 LatinIME，并在结束后恢复原输入法；清理的仅是独立审计包 `com.tiji.mistakes.audit`。该次中断日志保存在 `outputs/architecture-20260930-regression-verified.log`、`outputs/architecture-20260930-inputmethod-failure-logcat.txt` 和 `outputs/architecture-20260930-lastanr.txt`。

| 检查 | 最终结果 | 本机证据 |
| --- | --- | --- |
| JVM 单元测试 | 227 项全部通过；使用本轮 Gradle 导出的 classpath 执行同一批 JUnit 类 | `outputs/architecture-20260930-junit-verified.log` |
| API30 设备测试 | XML 统计 141 项：134 通过、0 失败、0 错误、7 跳过；包括大字体和库搜索用例 | `outputs/architecture-20260930-regression-clean-ime.log` 与审计构建目录下测试 XML |
| 完整 Lint | 0 errors、5 原有 warnings、2 information；没有新增 baseline 隐藏报告 | `app/build/isolated-release-20260925/reports/lint-results-debug.xml` |
| Release / R8 / 资源压缩 | BUILD SUCCESSFUL | `outputs/architecture-20260930-release-final.log` |
| 签名 / 覆盖安装 / 冷启动 | apksigner 验证通过；API30 `adb install -r` 为 Success，MainActivity 冷启动 Status: ok | 下方安装包与证书信息 |
| 补丁 / 文档 | `git diff --check` 通过；README/开发文档的本地链接目标存在 | Git 差异与文档链接检查 |

7 个跳过项中，3 项需要真实 API 测试参数，4 项缺少相应 OCR 模型或图像资产；不计入通过数。控制台末行的 148 tests 包含跳过计数重复，统计以 XML 的 tests=141 / skipped=7 为准。GitHub 的标准 JUnit 与 API30 验证结果以本轮 PR/提交的检查记录为准。

本机安装包：`outputs/apk/releases/tiji-v1.0.0-architecture-20260930.apk`。

- APK SHA-256：`E1FDEAFC4C37FAEFFB38EAB4336B202562D572B334A408B1BAC293D34A479C87`。
- 沿用现有 Android Debug 证书以保持覆盖安装连续性；不是新建正式发布证书。证书 SHA-256：`24300ed6ec1ba17e7004cd3156fde97e5568d09b8c6d921d9c9bb24c597ec9f1`。

自动化通过不等同于手机帧率、真实模型、所有并发时序或实体打印效果已经得到穷尽验证。剩余结构优化方向列在上表，未将其描述为已经实施。

未新增依赖或更改数据库 schema；未删除功能、替换渲染方案、清空模拟器原应用数据或提交安装包与用户附件。

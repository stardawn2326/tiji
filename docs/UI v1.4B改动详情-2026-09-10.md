# Tiji UI v1.4B 改动详情

日期：2026-09-10
依据：`Tiji_UI_v1.4A_验收与v1.4B下一步方案_2026-09-10.md`
分支：`codex/v1.4b-learning-data`
基线：`origin/main` / `c69921f7bf5165589904d4aea3bf32c03ad0427b`
远程仓库：`stardawn2326/tiji`

## 一、本轮范围

本轮执行 v1.4B 方案中的学习数据基础、复习事件记录、知识点关系、备份 Schema 3、学习分析、首页轻量洞察和 P1 组件职责拆分。目标是让 UI 上的“复习完成”“知识点薄弱”和“本周学习”都来自真实结构化数据，不再根据旧标签或临时状态拼接伪统计。

保留了现有五个底部一级页面和既有 DataStore 兼容键；没有删除旧的 `Mistake.tags`，没有把历史 DataStore 汇总伪造为历史复习事件，也没有提交构建缓存、临时签名文件或 APK 二进制。

## 二、数据层与 Room v10

### 1. 新增结构化实体

| 文件 | 内容 |
| --- | --- |
| `app/src/main/java/com/tiji/mistakes/data/ReviewRecordEntity.kt` | 一次真实复习反馈，保存题目、时间、等级、掌握度前后值、间隔前后值和下次复习时间 |
| `app/src/main/java/com/tiji/mistakes/data/KnowledgePointEntity.kt` | 科目、知识点名称、规范化名称、稳定 ID、父知识点和时间字段 |
| `app/src/main/java/com/tiji/mistakes/data/MistakeKnowledgePointCrossRef.kt` | 错题与知识点的多对多关系，两个外键均使用级联删除 |
| `app/src/main/java/com/tiji/mistakes/data/ReviewRecordDao.kt` | 复习历史查询、按题目查询和导入去重 |
| `app/src/main/java/com/tiji/mistakes/data/KnowledgePointDao.kt` | 知识点稳定 ID/科目+规范名查找、幂等插入和更新 |
| `app/src/main/java/com/tiji/mistakes/data/MistakeKnowledgePointDao.kt` | 关系查询、批量插入、按题目清理和级联数据维护 |

`AppDatabase` 升级到 Room v10，并新增生成的 schema 文件：

`app/schemas/com.tiji.mistakes.data.AppDatabase/10.json`

### 2. 迁移路径

新增确定性的 `9 → 10` DDL，并保留组合迁移：

- `MIGRATION_9_10`
- `MIGRATIONS_7_10`
- `MIGRATIONS_8_10`

迁移只创建表、索引和外键，不填充虚构的复习记录。旧错题 ID、标签、复习字段和现有 DataStore 状态保持不变。

### 3. 原子复习事务

`MistakeRepository.recordReview()` 将以下动作放入同一个 Room transaction：

1. 读取当前错题状态；
2. 使用 `ReviewScheduler.preview()` 计算间隔和掌握度；
3. 更新 `Mistake` 的 `mastery`、`reviewCount`、`lastReviewedAt`、`nextReviewAt` 和 `updatedAt`；
4. 写入一条 `ReviewRecordEntity`。

支持真实反馈等级：`FORGOT`、`HARD`、`GOOD`、`EASY`。答题页反馈完成后才同步旧的 DataStore 复习状态；详情页“已掌握”仍是状态操作，不会凭空写入一条 `GOOD` 复习事件。更新错题时使用 `UPDATE`，避免 Room `REPLACE` 触发外键级联而误删复习历史或知识点关系。

## 三、旧标签回填与兼容

新增 `KnowledgePointNormalizer` 和 `MistakeRepository.backfillLegacyTags()`：

- 支持中文/英文逗号、分号、竖线、顿号和多余空格；
- 空标签被忽略，重复标签按规范化名称去重；
- 规范化名称使用稳定的大小写规则；
- 知识点 stable ID 由科目和规范化名称确定性生成；
- 回填先创建/复用知识点，再重建错题关系；
- 每次同步先清理当前题目的旧关系，再按规范化标签重建，因此可重试、可重复执行；
- `AppPreferences` 增加回填版本 marker，只有整批回填成功后才标记完成；失败时下次启动仍可重试；
- 不根据 `reviewCheckIns`、`reviewProgress`、`reviewMastery` 或 `reviewPlanSnapshots` 生成历史 `ReviewRecordEntity`。

新建或保存错题时也会同步知识点关系，确保回填完成后新增数据继续保持结构化。

## 四、备份 Schema 3

`BackupService` 升级到 Schema 3，新增以下归档文件：

```text
data/mistakes.json
data/preferences.json
data/review_records.json
data/knowledge_points.json
data/mistake_knowledge_points.json
```

其中学习数据只使用 stable ID，不导出本地自增 ID：

- `review_records.json` 通过 `mistakeStableId` 指向错题；
- `knowledge_points.json` 通过 `parentStableId` 表达父子知识点；
- `mistake_knowledge_points.json` 通过错题和知识点 stable ID 表达关系。

导入顺序为错题、知识点、父子关系、错题关系、复习记录；Merge 模式支持 stable ID/指纹去重，并在本地 ID 变化后重新映射外键；Replace 模式按外键安全顺序清理并重建全部学习数据。Schema 2 仍可导入，缺少 Schema 3 文件时按空学习数据处理；超过当前支持范围的版本会被拒绝。

## 五、学习分析与 UI 接入

### 1. 复习分析

`ReviewAnalytics` 提供：

- 近 7 天复习次数；
- 近 30 天复习次数；
- 近 30 天忘记次数；
- 平均掌握度变化；
- 本周完成次数；
- 连续复习天数；
- 四种反馈等级分布。

### 2. 知识点薄弱度

`WeaknessCalculator` 按方案计算：

```text
Base = (3 - mastery) / 3
ForgetPenalty = recentForgot / recentReviews
RepeatPenalty = min(1, mistakesForPoint / 5)
score = Base * 0.45 + ForgetPenalty * 0.35 + RepeatPenalty * 0.20
```

结果标签固定为：`需加强`、`较弱`、`一般`、`稳定`。孤立知识点不会伪装成薄弱项展示。

### 3. 页面变化

- `TijiApp` 汇总 Room 流并计算 `ReviewAnalyticsSummary`、`KnowledgePointInsight`，通过导航状态传入页面；
- 首页增加轻量“本周学习”卡片，展示真实本周复习数、近 30 天忘记数和知识点数；
- 首页“薄弱知识点”改为结构化知识点、标签、关联错题数和薄弱度进度；
- 错题库保留旧标签筛选兼容，同时增加知识点筛选入口；
- 空状态文案改为引导录入、整理和复习，避免暗示不存在的历史统计；
- 五个底部导航一级页面和原有二级页面路由保持不变。

## 六、P1 组件职责拆分

从 `TijiApp.kt` 移出仍被多个页面复用的 UI 辅助实现：

| 新文件 | 负责内容 |
| --- | --- |
| `ui/review/components/ReviewAllocationRow.kt` | 复习计划分配行 |
| `ui/components/BatchBarAction.kt` | 批量操作栏动作 |
| `ui/components/QuickButton.kt` | 快捷操作按钮 |
| `ui/settings/components/SettingCard.kt` | 设置卡片容器 |
| `ui/settings/components/OcrFrameBadgeIcon.kt` | OCR 框选徽标图标 |
| `ui/settings/components/OcrSettingsCard.kt` | OCR 设置卡片 |
| `ui/image/ClickableImageThumbnail.kt` | 可点击图片缩略图 |
| `ui/image/ExpandedImageDialog.kt` | 放大图片对话框 |
| `ui/TijiApp.kt` | 删除对应根文件实现，仅保留宿主状态、主题、一级导航和全局业务回调 |

组件抽离保持原有参数、交互和视觉行为，不引入与 v1.4B 无关的视觉重做或重量级动画。

## 七、测试与验收

执行环境：JDK 17、Android SDK 35、`Tiji_API_35`（API 35，`emulator-5554`，320×640）。

- `:app:compileDebugKotlin`：通过；
- `:app:compileDebugAndroidTestKotlin`：通过；
- `:app:testDebugUnitTest`：通过；
- `RoomMigrationTest`：5 项通过，覆盖 v9→10、v7→10、v8→10 及级联删除；
- `:app:connectedDebugAndroidTest`：本地全量回归 31 项完成，4 项按既有环境能力约定跳过，0 失败；新增备份往返用例另行单测通过；
- `LegacyTagBackfillTest`：通过，验证标签解析、去重、稳定 ID、关系回填和重复执行；
- `ReviewQuestionUiTest`：通过，验证预览与实际事务结果一致，并且只生成一条真实复习记录；
- `BackupRoundTripTest`：通过，验证 Schema 3 归档实际写入、删除后导入、stable ID 重映射、知识点关系和复习记录恢复；
- `:app:lintDebug`：通过；报告中的 2 个警告为既有动态加载/SDK 兼容性警告，不是本轮新增错误；
- `:app:assembleDebug`：通过。

本地 Debug APK 仅用于构建验证，路径为：

`app/build/outputs/apk/debug/tiji-v1.0.0-debug.apk`

该 APK 未纳入源码提交。

## 八、交付内容

- 源码、Room schema、单元测试、instrumentation 测试和本文件均纳入 v1.4B 分支；
- GitHub 推送目标为 `stardawn2326/tiji`；
- 本轮不提交 APK 二进制；
- 后续 GitHub 分支提交 SHA 和 Pull Request 地址以远端推送结果为准。

## 九、GitHub 远端结果

- 初始 v1.4B 实现提交：`1a773afe0f8198d2439580de385322e72c5bdbef`；
- CI fixture 隔离修复提交：`7280a1ebc430f08cde108217227cecbb9fe22dc9`；
- 功能代码分支：`codex/v1.4b-learning-data`，已核验远端包含 `7280a1ebc430f08cde108217227cecbb9fe22dc9`；
- 远端结果记录更新提交：`9bd449f`；
- Pull Request：[stardawn2326/tiji#3](https://github.com/stardawn2326/tiji/pull/3)；
- 修复后的 push workflow：[run 34492686468](https://github.com/stardawn2326/tiji/actions/runs/34492686468)，Compile、Lint/打包和 API 35 instrumentation 均通过；
- 修复后的 Pull Request workflow：[run 34492693312](https://github.com/stardawn2326/tiji/actions/runs/34492693312)，Compile、Lint/打包和 API 35 instrumentation 均通过；
- 首轮 workflow 暴露的唯一失败是测试 fixture 固定前缀造成的孤立知识点计数污染，已由 `7280a1e` 修复并在本地和远端重跑通过。

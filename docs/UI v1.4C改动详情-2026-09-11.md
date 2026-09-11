# 题迹 Tiji UI v1.4C 改动详情

> 执行日期：2026-09-11
> 依据：`Tiji_UI_v1.4B.1_验收与v1.4C下一步方案_2026-09-11.md`
> Repository：`stardawn2326/tiji`
> 工作分支：`codex/v1.4c-learning-experience`

## 1. 本轮目标

本轮按 v1.4C 的 “Learning Experience & Data Hardening” 范围执行：让知识点成为真实可浏览、可解释、可回链的学习单元；将近期学习统计改为设备本地日历语义；收紧关系和备份数据边界；完成 `TijiApp` 根组件的机械拆分。

五个底部导航仍是一级页面：`首页`、`错题`、`AI解题`、`复习`、`我的`。知识点列表是“我的”下的真实二级学习页面，不改变底部导航的信息架构。

## 2. 页面与交互改动

### 2.1 知识点学习页

- 新增 `knowledge` 路由，入口为“我的 → 科目与知识点”。
- 新增 `knowledge-detail/{stableId}` 路由，使用 `stableId` 而不是显示名称定位知识点。
- 知识点列表支持“全部 / 科目”筛选，以及“薄弱优先 / 错题数 / 最近复习 / 名称”排序。
- 知识点卡片显示科目、状态、关联错题数、近 30 天复习次数和薄弱度进度。
- 详情页显示学习概览、可读的薄弱原因、最近 5 次真实复习记录和最多 20 道关联错题。
- 详情页可回到错题库，并自动使用当前知识点的结构化筛选。

涉及文件：

- `app/src/main/java/com/tiji/mistakes/ui/knowledge/KnowledgeExplorerScreen.kt`
- `app/src/main/java/com/tiji/mistakes/ui/navigation/Routes.kt`
- `app/src/main/java/com/tiji/mistakes/ui/navigation/TijiNavGraph.kt`
- `app/src/main/java/com/tiji/mistakes/ui/navigation/TijiNavGraphState.kt`
- `app/src/main/java/com/tiji/mistakes/ui/settings/MyScreen.kt`

### 2.2 页面联动

- 首页“薄弱知识点”点击后进入对应知识点详情。
- 错题库“重点知识点”和知识点筛选使用 `KnowledgePoint.stableId`。
- “我的 → 科目与知识点”不再停留在设置占位页。
- 知识点详情的“相关错题”进入错题详情；“查看相关错题”进入已筛选的错题库。

涉及文件：

- `app/src/main/java/com/tiji/mistakes/ui/home/HomeScreen.kt`
- `app/src/main/java/com/tiji/mistakes/ui/library/LibraryScreen.kt`
- `app/src/main/java/com/tiji/mistakes/ui/detail/MistakeDetailScreen.kt`

### 2.3 复习历史与可解释性

- 错题详情新增“复习记录”，只显示该错题真实保存的最近 5 次 `ReviewRecord`。
- 复习记录显示本地日期时间、掌握度变化、间隔天数和用户可读的反馈标签。
- 知识点详情把薄弱度拆成行动提示：平均掌握度偏低、近 30 天忘记次数、关联错题规模等。
- UI 不展示内部浮点薄弱度，也不包装成未经校准的掌握概率。

## 3. 时间与统计语义

新增 `app/src/main/java/com/tiji/mistakes/domain/time/LearningCalendar.kt`，统一处理：

- 近期 7 / 30 天按设备本地自然日计算，窗口从本地零点开始。
- 周统计从周一开始。
- 连续复习按 `LocalDate` 判断：今天有记录从今天开始，否则从昨天开始。
- 使用 `Instant`、`ZoneId`、`LocalDate`，不再用固定 `86_400_000` 毫秒推算自然日。
- 覆盖东京、纽约夏令时切换、柏林跨月边界等场景。

统计调用点：

- `app/src/main/java/com/tiji/mistakes/domain/ReviewAnalytics.kt`
- `app/src/main/java/com/tiji/mistakes/domain/WeaknessCalculator.kt`
- `app/src/main/java/com/tiji/mistakes/ui/MistakeViewModel.kt`

## 4. 数据查询与完整性

### 4.1 Bounded query

`ReviewRecordDao` 新增：

- `observeSince(from)`：首页近期统计只订阅时间范围内的记录。
- `observeForMistake(mistakeId)`：错题详情只订阅当前错题的复习历史。

知识点关系查询新增按知识点 / 按错题的关联 ID 查询，供结构化筛选和学习页使用。

### 4.2 结构化知识点筛选

筛选链路固定为：

```text
KnowledgePoint.stableId
    -> MistakeKnowledgePointCrossRef
    -> Mistake IDs
    -> Library Result
```

旧 `tags` 仍作为兼容显示和旧数据入口，但不再作为结构化知识点的唯一键。数学 / 函数与物理 / 函数使用不同 `stableId`，不会串题。

### 4.3 orphan 清理与 parent 完整性

- 保存、批量保存、关系回填、关系变更、hard delete、Schema 2 同步和 Backup REPLACE 后执行 orphan 清理。
- 共享知识点只有在最后一个关联错题删除后才被清理。
- 新增 `KnowledgePointIntegrity`，在 Repository 层解除缺失父级、自环和明显环路；不升级 Room 版本，不改变当前数据库结构。
- Backup 导入在父级关系写入后再次执行安全校验。

涉及文件：

- `app/src/main/java/com/tiji/mistakes/data/MistakeRepository.kt`
- `app/src/main/java/com/tiji/mistakes/data/KnowledgePointDao.kt`
- `app/src/main/java/com/tiji/mistakes/data/MistakeKnowledgePointDao.kt`
- `app/src/main/java/com/tiji/mistakes/data/KnowledgePointIntegrity.kt`
- `app/src/main/java/com/tiji/mistakes/service/BackupService.kt`

## 5. UI 根组件拆分

按 mechanical refactor 移动既有实现，没有重写数学渲染器：

- 图片预览移至 `ui/image/ImagePreview.kt`。
- 数学渲染移至 `ui/math/MathText.kt`、`FormulaPreview.kt`、`MathWebViewGuard.kt`、`MathHtmlBuilder.kt`。
- 各业务页面改用新的包路径。
- `TijiApp.kt` 现在只负责偏好与 ViewModel 收集、应用级状态、主题、Scaffold、底部导航和 NavGraph hand-off，不再包含 WebView、KaTeX HTML、Bitmap loading 或预览实现。

## 6. 验证与结果

本地验证命令均在 `D:\android\jdk17`、`D:\android\sdk` 环境完成：

| 检查 | 结果 |
| --- | --- |
| `:app:compileDebugKotlin` | 通过 |
| `:app:compileDebugAndroidTestKotlin` | 通过 |
| `:app:testDebugUnitTest` | 通过，122 tests，0 failed |
| `:app:lintDebug` | 通过，0 Error；保留 2 个既有 Warning |
| `:app:assembleDebug` | 通过 |
| `:app:connectedDebugAndroidTest` | 通过，34 tests，4 skipped，0 failed |
| 设备 | `Tiji_API_35` / `emulator-5554` / API 35 |

新增或更新的验收用例：

- `LearningCalendarTest`：自然日、纽约春季 23 小时 / 秋季 25 小时、周一边界、连续复习、跨月。
- `KnowledgePointIntegrityTest`：缺失父级、自环、环路。
- `KnowledgeExplorerTest`：知识点列表、同名跨科目区分、详情回链结构化错题筛选。
- `KnowledgePointCleanupTest`：共享知识点保留 / 最后关联删除、标签关系变更清理。
- 更新 `LibraryFilterTest`、`MyNavigationTest` 以覆盖结构化筛选和真实知识点一级入口。

Debug APK 产物：

```text
app/build/outputs/apk/debug/tiji-v1.0.0-debug.apk
SHA-256: C6A13C84DE446C65247EBE8607BAF5F91EBBFB4A55FE87ECD60897152C02B7F1
```

Lint 仅报告既有 `System.load` 安全提示和无效 API 版本判断提示；Gradle 仍会提示 Android SDK XML 版本差异。这些不影响本轮构建和设备回归。

## 7. 明确未纳入本轮

没有加入账号 / 云同步 / 排行榜、复杂 Dashboard、知识树编辑器、Room 自引用外键迁移或新的高级图表。parent 关系只做 Repository 层安全清理，待真正的知识树编辑需求出现后再单独评估 Room 迁移。

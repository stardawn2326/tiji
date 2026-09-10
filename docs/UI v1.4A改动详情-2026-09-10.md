# Tiji UI v1.4A 改动详情

日期：2026-09-10  
依据：`Tiji_UI_v1.3_验收与v1.4下一步方案_2026-09-10.md`  
分支：`codex/v1.4a-hardening`  
基线：`origin/main` / `dc214e274269351589230f87892e2ed68cac41dc`

## 一、本轮范围

本轮执行 v1.4A「Merge Hardening & Learning Data Foundation」中的合并硬化、远端 instrumentation、行为级 UI 验收和源码职责拆分。v1.4B 的学习数据层没有提前并入，避免在门禁、测试和结构重构尚未稳定时扩大数据库变更范围。

本轮不重新设计底部导航、颜色、首页信息架构，也没有增加与方案无关的大量动画；重点是让现有五个一级页面和二级页面具备清晰路由、可持续维护的组件边界及可重复验收的行为证据。

## 二、具体代码改动

### 1. GitHub Actions 与合并门禁

- 保留现有 `verify` job，继续执行 Kotlin 编译、Android 测试编译、单元测试、Lint 和 Debug APK 打包。
- 在 `.github/workflows/android.yml` 新增 `Android instrumentation (API 35)` job。
- instrumentation job 使用 `reactivecircus/android-emulator-runner@v2` 启动 API 35、面向自动化测试的 `google_atd`、`x86_64`、`pixel` 模拟器 profile，配置 4 核、2048M RAM、512M heap 和 900 秒启动窗口，关闭动画后执行 `connectedDebugAndroidTest`。
- GitHub-hosted Ubuntu runner 检测到 `/dev/kvm` 后保持 Linux 硬件加速（`disable-linux-hw-accel: false`）；此前禁用加速会导致模拟器启动后 instrumentation 进程崩溃并发现 0 个测试，已定位并修复。
- instrumentation job 与 verify job 同时覆盖 push 到 `main`/`codex/**` 和 Pull Request 合并到 `main` 的场景。
- CI 中生成幂等的 debug keystore，仅用于自动化 Debug 验证，不替代正式签名。
- `main` 合并规则要求 Pull Request、`Compile, test, lint and package` 和 `Android instrumentation (API 35)` 两项检查通过，并阻止 force push 和分支删除。
- 已在 `stardawn2326/tiji` 创建并启用规则集 `main protection`（ruleset ID：`22763455`）；评审人数按方案保持为 0，但仍必须走 Pull Request 和两项状态检查。

### 2. 行为级 UI 验收

- `LibraryFilterTest` 建立数学/物理三条 fixture，验证：
  - 标题搜索和题干搜索只返回目标题；
  - 科目筛选只保留数学题；
  - 知识点筛选只保留带有目标标签的题；
  - 掌握状态「未掌握」和难度「简单」共同筛选后仍返回精确目标题；
  - 错题列表加入 `library_mistakes_list` 滚动语义，测试在小屏设备上可以定位非首屏卡片。
- `MistakeDetailUiTest` 点击详情页「已掌握」，等待数据库写入，再验证列表徽标、返回详情页后的按钮状态和持久化结果。
- `ReviewQuestionUiTest` 使用 `ReviewScheduler.preview` 生成预期反馈，验证答题页预览间隔与实际反馈一致，并检查 `mastery`、`reviewCount`、`nextReviewAt`、到期数量和完成提示。
- `MyNavigationTest` 将「我的」页六个入口分别绑定到具体设置 section，并验证每个入口的目标页面 tag：
  - `settings_review`
  - `settings_subject`
  - `settings_ai`
  - `settings_data`
  - `settings_appearance`
  - `settings_about`
- 设置页通过 `pageTag` 和 `initialItemIndex` 进入对应 section，返回时仍由导航栈处理。

### 3. 编辑器与数学文本职责拆分

从 `TijiApp.kt` 根组件移出编辑器字段与数学文本规范化逻辑：

| 新文件 | 负责内容 |
| --- | --- |
| `app/src/main/java/com/tiji/mistakes/ui/editor/CaptureFields.kt` | 拍照录题字段和录入相关 UI |
| `app/src/main/java/com/tiji/mistakes/ui/editor/MistakeFields.kt` | 错题内容字段、难度选择和内容卡片 |
| `app/src/main/java/com/tiji/mistakes/ui/editor/PhotoEditFields.kt` | 图片编辑字段与裁剪相关 UI |
| `app/src/main/java/com/tiji/mistakes/ui/editor/ErrorReasonPicker.kt` | 错因选择器 |
| `app/src/main/java/com/tiji/mistakes/ui/math/TextNormalization.kt` | 标点、公式、题干换行及数学源文本规范化 |

`CaptureScreen`、`MistakeDetailScreen`、`ReviewScreen` 和 `AiSolveScreen` 改为从 feature 目录导入这些能力；行为和数据语义保持不变。

### 4. 导航结构轻拆

新增 `app/src/main/java/com/tiji/mistakes/ui/navigation/` 下的：

- `Routes.kt`：集中维护五个一级页面、二级页面、参数路由、设置 section 和页面转场方向。
- `TijiNavGraphState.kt`：集中维护导航图需要的页面状态和回调数据。
- `TijiNavGraph.kt`：继续作为 `NavHost` 和页面路由入口，并使用集中路由常量。

`TijiApp.kt` 只保留主题、全局状态、底部一级导航和宿主级业务回调；“我的”页六个入口不再落到同一个无区分的设置路径，而是进入可识别的具体 section。

## 三、验证结果

执行环境：JDK 17、Android SDK 35、`Tiji_API_35`（API 35，320×640）模拟器。

- `:app:compileDebugKotlin`：通过。
- `:app:compileDebugAndroidTestKotlin`：通过。
- `:app:testDebugUnitTest`：通过。
- `:app:lintDebug`：通过。
- `:app:assembleDebug`：通过，Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。
- `:app:connectedDebugAndroidTest`：27 个测试完成，4 个环境能力用例跳过，0 个失败。
- 新增错题库行为测试曾暴露首屏外卡片和筛选弹窗语义歧义，已通过列表滚动语义、fixture 隔离字段和精确节点选择修复，并在 API 35 模拟器上重跑通过。
- 远端 PR 验证（提交 `065fa46a54b2cd72de8f993d45a2dafce265faee`）：[PR run 34477813593](https://github.com/stardawn2326/tiji/actions/runs/34477813593) 的编译/单测/Lint/打包和 API 35 instrumentation 均通过。
- 远端 push 验证：[push run 34477809447](https://github.com/stardawn2326/tiji/actions/runs/34477809447) 的两个 job 均通过；PR 当前为 `OPEN`、`MERGEABLE`、`CLEAN`，等待仓库合并流程。

## 四、v1.4B 明确留待后续

以下内容按 v1.4 方案留到下一阶段，本轮没有修改数据库 schema 或学习数据模型：

- Room v10 及 `ReviewRecordEntity`/Dao；
- 知识点实体、学习分析和首页洞察；
- backup schema 3；
- 复习历史与更完整的学习统计。

## 五、代码提交

相对于 `origin/main` 的代码提交：

| 提交 | 内容 |
| --- | --- |
| `e3c685d` | 新增 API 35 Android instrumentation CI job |
| `c9b6005` | 增加错题库、详情、复习和“我的”行为测试 |
| `0bb7e72` | 抽离编辑器字段和数学文本规范化辅助 |
| `a35a252` | 抽离导航路由、导航状态和设置 section |
| `c4f72d3` | 调整 API 35 模拟器 profile |
| `adf6772` | 增加模拟器启动参数、KVM 检测和资源配置 |
| `4774600` | 切换 API 35 轻量 `google_atd` 镜像 |
| `065fa46` | 恢复 GitHub runner 的 KVM 硬件加速，修复 instrumentation 进程崩溃 |

本文件作为 v1.4A 代码交付的一部分提交；本轮不提交构建缓存、临时 keystore 或 APK 二进制。

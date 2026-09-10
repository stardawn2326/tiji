# Tiji UI v1.3 改动详情

日期：2026-09-10
依据：`Tiji_UI_v1.2_验收与v1.3下一步方案_2026-09-10.md`
分支：`codex/ui-soft-academic-v2`

## 一、交付目标

本轮按 v1.3 方案完成 UI 导航架构收敛、跨页面公共能力拆分、CI 可验证性修复，以及 Compose 交互验收用例补齐。页面视觉基线继续沿用当前 Soft Academic 设计语言；本轮重点是让五个一级页面、二级页面和公共业务能力的边界清晰、可测试、可持续迭代。

## 二、具体代码改动

### 1. CI 与仓库规范

- 将 GitHub Actions 的 `actions/setup-java` 升级到 `v5`。
- 在 Android 构建前增加幂等的 CI debug keystore 生成步骤，保证 CI 可以执行 `compile`、`lint`、`test` 和 debug APK 打包。
- 明确该 keystore 仅用于 CI/debug 验证，不替代正式发布证书。
- 新增 `.gitattributes`，统一 Kotlin、Gradle、XML、YAML、Markdown 的 LF 行尾，完成仓库行尾归一化。
- 将 Firecrawl 临时目录加入忽略规则，避免本地解析痕迹进入源码提交。

### 2. 一级导航与路由

- 新增真实的 `ui/navigation/TijiNavGraph.kt`，由它统一持有 `NavHost`、一级页面路由、二级页面路由和页面转场。
- `TijiApp.kt` 收敛为主题、全局状态、Scaffold、底部一级导航和全局业务回调的宿主，不再直接堆叠所有页面路由。
- 五个底部入口继续作为独立一级页面：首页、错题库、AI 解题、复习、我的。
- 复习日历、错题详情、复习答题、设置详情、AI 历史、视觉辅助配置等均通过导航图中的二级路由进入，返回行为统一由导航栈处理。
- 清除 UI 源码中的通配符导入，并将跨 feature 依赖改为显式导入。

### 3. 公共 UI/业务辅助能力

从页面 feature 文件中抽离以下公共能力到 `ui/common/`：

| 文件 | 内容 |
| --- | --- |
| `Formatting.kt` | 日期、复习状态、复习间隔、难度、掌握度、标签和排序显示规则 |
| `ImagePreview.kt` | 相机 URI、图片替换通知、图片请求版本和照片题判断 |
| `ImageEditing.kt` | 裁剪手势类型、裁剪选区和初始选区 |
| `AiDisplayParser.kt` | AI 流式元数据、可见解答和结构化解答分段解析 |
| `Dialogs.kt` | AI 错因解析和公共错误原因选项 |
| `PdfPreview.kt` | PDF 导出状态、预览页渲染、保存与删除、加载/预览对话框 |

这样可以避免首页、错题库、复习、详情和 AI 页面各自维护一份相同的格式化、图片或 PDF 逻辑。

### 4. 交互可测试性与无障碍覆盖

- 为错题卡、错题库搜索框、科目/知识点/掌握状态/难度/排序控件、详情页操作、复习答案按钮、复习反馈选项和“我的”页面条目增加稳定的 Compose test tag。
- 新增 `LibraryFilterTest`：覆盖搜索、知识点、掌握状态、难度和排序入口。
- 新增 `MistakeDetailUiTest`：覆盖“我的答案、正确答案、错因标签、我的总结”和复习状态操作。
- 新增 `ReviewQuestionUiTest`：覆盖查看答案、解析和复习反馈选项。
- 新增 `MyNavigationTest`：覆盖“我的”页六个设置入口进入二级设置路由并返回。
- 扩展 `LargeFontAccessibilityTest`：覆盖 1.3x、1.5x 字体、深色模式下的五个一级导航点击语义。
- 保留并执行旋转回归用例，覆盖横屏后回到竖屏的一级导航可达性。
- 对详情页、复习答题页和“我的”页列表加入可滚动语义，确保小屏设备和大字号场景下测试/辅助技术可以定位到非首屏内容。

## 三、验证结果

执行环境：JDK 17、Android SDK 35、`Tiji_API_35`（API 35，320×640）模拟器。

- `:app:compileDebugKotlin`：通过。
- `:app:compileDebugAndroidTestKotlin`：通过。
- `testDebugUnitTest`：通过。
- `lintDebug`：通过，报告已生成到 Gradle build reports。
- `assembleDebug`：通过。
- `connectedDebugAndroidTest`：27 个测试完成，其中 23 个非跳过用例通过、4 个环境能力跳过、0 个失败。
- 跳过项为图形细化、图形检测和本地 OCR 所需的设备/模型能力用例，不是 UI 回归失败。

## 四、构建与发布边界

- Debug APK 由当前 Gradle 配置生成，正式 release 签名配置未被本轮 CI debug keystore 改写。
- 本轮完成的是源码、测试、CI 和文档交付；不会把临时 debug keystore 或本地构建缓存提交到仓库。
- GitHub 发布目标为 `stardawn2326/tiji` 的 `codex/ui-soft-academic-v2` 分支；推送后按方案检查 Actions，再创建到 `main` 的正式 PR，不直接合并主分支。

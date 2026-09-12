# 题迹 UI 2.0 结构重构交付

本轮参照《Tiji_UI_2.0_一次性完整重构实施总规范_2026-09-12》实现蓝白纸感界面。按用户最新要求，交付代码结构及安装包，视觉验收由用户完成。

分支：`codex/ui-2.0-paper-blue`。起点：`eef00b152a12efa5f7ac50d64a4c5c1f3e64d762`。

## 完成范围

| 范围 | 主要文件 / 目录 | 当前实现 |
| --- | --- | --- |
| 设计基础 | `ui/design/TijiColor.kt`、`TijiTypography.kt`、`TijiShapes.kt`、`TijiDimens.kt`、`TijiMotion.kt`、`TijiSubjectColors.kt` | 浅色与深色主题、字号、间距、圆角、学科色、动效时长集中管理 |
| 通用控件 | `ui/design/button`、`field`、`selection` | 主次按钮、危险操作、图标按钮、搜索、多行输入、密钥显隐、选项、标签、数量步进器统一封装 |
| 页面容器 | `ui/design/layout`、`card`、`overlay`、`feedback`、`media` | 标题栏、底部操作栏、纸张卡片、错题卡片、设置行、弹窗、底部面板、菜单、提示及图片状态统一 |
| 导航与首页 | `ui/TijiApp.kt`、`ui/navigation`、`ui/home` | 蓝白主题外壳；首页录题/AI 入口、今日复习、统计和纵向学科列表；学科直达已有筛选路由 |
| 错题库 | `ui/library/LibraryScreen.kt` | 单一滚动列表容纳标题、筛选和错题；保留知识点、掌握状态、排序、多选及批量操作 |
| 录题与编辑 | `ui/capture`、`ui/editor` | 输入模式统一分段控件；多行字段、保存操作、图片选择及独立图片编辑器迁移 |
| 错题详情 | `ui/detail/MistakeDetailScreen.kt` | 题目、答案、解析优先组织；更多信息、复习记录及编辑保存入口保留 |
| 复习 | `ui/review` | 评分区可换行；日历、总结拆分；数量编辑归入公共控件 |
| AI 解题 | `ui/solve` | 三种输入模式统一；结果组件、历史页面拆分；生成、保存、错误等状态入口保留 |
| 知识点 | `ui/knowledge` | 列表与详情拆分；增加名称/学科本地搜索；关联错题结构保留 |
| 设置 | `ui/settings` | 卡片堆叠改为轻量分组和设置行；外观、AI、OCR、复习、数据和关于页面迁移 |
| PDF 与公式 | `ui/common/PdfPreview.kt`、`ui/math` | PDF 操作栏和加载失败提示统一；公式宿主字体跟随系统缩放 |

原有 `TijiComponents`、`QuickButton`、`BatchBarAction`、`SettingCard`、`ConceptMistakeCard` 和复习数量控件的旧入口已删除或迁移至设计系统。业务页面中的按钮、输入框、选择控件、卡片和弹窗由公共封装承接。

## 保留行为

数据模型、存储和服务层没有改动。录题、OCR、AI 请求协议、解析和追问、编辑保存、知识点关联、筛选排序、批量操作、PDF 导出以及备份恢复仍使用原有业务链路。复习跟手切题、边界回弹和答后自动下一题继续使用原逻辑；难度数据继续保留原有等级。公式解析与数学内容协议保留，仅调整宿主样式及字号缩放。

## 验证边界

- 最终源码执行离线 Kotlin 编译、Android 测试源码编译、Lint 和 Debug APK 构建；结果见项目内 `outputs/ui2-final-build.log`。
- 用户补充要求前，中间版本在本地 API 30 模拟器运行了 10 个既有回归测试，结果 `OK (10 tests)`，覆盖导航、旋转、大字体入口、筛选、多选、详情和复习手势。日志为 `outputs/ui2/core-instrumentation.log`。这不是最终结构调整后的视觉验收结果。
- 测试前保存的模拟器应用文件已恢复，25 个文件逐一核对 SHA-256，原系统字体及旋转设置已恢复。
- 按用户要求停止视觉检查；最终版本未做截图验收，也未执行远程 API 35 验证。
- 构建仍提示系统栏颜色 API 弃用警告，不影响当前编译；Lint 报告保留在 `app/build/reports`。

交付包：`outputs/apk/tiji-ui2-paper-blue-debug.apk`。保留现有应用 ID、版本号和 Debug 签名配置；未发布远程仓库。

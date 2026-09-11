# Tiji UI v1.4B.1 改动详情

日期：2026-09-11
依据：`Tiji_UI_v1.4B_验收与v1.4B.1-v1.4C下一步方案_2026-09-11.md`
分支：`codex/v1.4b-learning-data`
远程仓库：`stardawn2326/tiji`
Pull Request：[stardawn2326/tiji#3](https://github.com/stardawn2326/tiji/pull/3)

## 一、本轮范围

本轮只处理 v1.4B 独立验收发现的两个 P0 兼容性问题，保持 v1.4B 的 UI、Room v10 数据模型和五个底部一级页面不变。v1.4C 的日历安全统计、知识点库筛选、知识点详情和复习历史页面按方案顺序暂不提前实现。

## 二、P0-1：备份读取边界修复

### 问题

Schema 3 已经增加复习记录、知识点和错题关系文件，但写出端仍声明 `minReaderSchemaVersion=2`。旧版 Schema 2 读取器可能把 Schema 3 当作可读格式，忽略新增文件后静默丢失学习数据。

### 改动

- `BackupService.MIN_READER_SCHEMA_VERSION` 从 `2` 提升为 `3`；新写出的 `.tiji` 明确要求 Schema 3 读取能力。
- 版本解析改为同时校验 `schemaVersion` 和 `minReaderSchemaVersion` 的合法范围：`minReaderSchemaVersion` 必须在 `1..schemaVersion` 内。
- 保留 Schema 2 导入能力；Schema 2 备份缺少 Schema 3 数据文件时按空学习数据兼容处理。
- 增加实际写出契约检查：读取 `BackupService.writeBackup()` 生成的归档中的 `manifest.json`，断言 `schemaVersion=3`、`minReaderSchemaVersion=3`。
- 增加旧 Schema 2 读取器契约测试，确认它会拒绝当前 Schema 3 写出格式，避免静默降级。

相关文件：

- `app/src/main/java/com/tiji/mistakes/service/BackupService.kt`
- `app/src/test/java/com/tiji/mistakes/BackupServiceTest.kt`
- `app/src/androidTest/java/com/tiji/mistakes/BackupRoundTripTest.kt`

## 三、P0-2：Schema 2 导入后的结构化知识点回填

### 问题

v1.4B 的一次性旧标签回填只在 marker 未完成时运行。若用户已经完成回填，之后再导入 Schema 2 备份，导入流程虽然恢复了 `Mistake.tags`，却不会同步 `KnowledgePoint` 和 `MistakeKnowledgePointCrossRef`，导致错题标签与知识点库脱节。

### 改动

- 在 `MistakeRepository` 新增 `syncKnowledgePointsForMistakes(Collection<Long>)`。
- Schema 2 或其他旧于当前 Schema 的导入完成后，仅使用本次导入的 stable ID 到本地 ID 映射执行结构化同步。
- 同步过程复用现有标签规范化、知识点稳定 ID、关系清理和幂等 upsert 逻辑，不重置回填 marker，不执行全库扫描。
- 空标签不会创建知识点；重复导入不会增加重复知识点或关系。
- Replace 模式只会对导入后的错题重建关系，原有被清理的结构化数据不会残留。

相关文件：

- `app/src/main/java/com/tiji/mistakes/service/BackupService.kt`
- `app/src/main/java/com/tiji/mistakes/data/MistakeRepository.kt`
- `app/src/androidTest/java/com/tiji/mistakes/BackupSchema2ImportTest.kt`

## 四、兼容性契约

| 输入/读取器 | 当前行为 |
| --- | --- |
| Schema 2 备份 → 当前读取器 | 接受；错题、偏好和旧标签可导入，旧标签会回填到结构化知识点 |
| Schema 3 备份 → 当前读取器 | 接受；复习记录、知识点和错题关系按 stable ID 恢复 |
| 当前 Schema 3 写出 → 旧 Schema 2 读取器 | 拒绝；由 `minReaderSchemaVersion=3` 阻止静默丢失学习数据 |
| Schema 4 备份 → 当前读取器 | 拒绝并提示升级应用 |

## 五、验证结果

执行环境：JDK 17、Android SDK 35、`Tiji_API_35`（API 35，`emulator-5554`）。

- `:app:compileDebugKotlin`：通过；
- `:app:compileDebugAndroidTestKotlin`：通过；
- `:app:testDebugUnitTest`：通过；
- `BackupSchema2ImportTest`：3 项通过；
- `BackupRoundTripTest`：1 项通过；
- `:app:connectedDebugAndroidTest`：全量 35 项完成，4 项按既有环境能力约定跳过，0 失败；
- `git diff --check`：通过。

最终静态检查、Lint、Debug 打包和 GitHub Actions 结果以本次提交后的执行结果为准；APK 不纳入本次源码提交。

## 六、提交边界

- 本次提交包含备份兼容修复、结构化知识点回填、契约测试、instrumentation 测试和本文件。
- 不提交 APK、构建缓存、临时签名文件或模拟器数据。
- 不启动 v1.4C 的新功能实现；待本分支合并后再按方案进入下一阶段。

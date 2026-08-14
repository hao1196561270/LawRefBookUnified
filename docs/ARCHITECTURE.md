# 架构说明 · 法条通 LawRefBook 整合版

本文档说明整合版 App 的架构、数据流向与关键设计决策，供代码审查与二次开发参考。

## 1. 整体分层

```
┌───────────────────────────────────────────────┐
│ UI 层（Jetpack Compose）                        │
│  HomeScreen / LawListScreen / ReaderScreen      │
│  SearchScreen / FavoritesScreen / HistoryScreen │
│  SettingsScreen（经 AppNav 路由 + 底部导航）      │
└───────────────┬───────────────────────────────┘
                │ 经 MyApplication 取得
┌───────────────┴───────────────────────────────┐
│ 仓库层 LawRepository（统一门面）                  │
│  - 目录库（只读）：AssetsLawDataSource           │
│  - 用户库（Room）：收藏 / 历史 / 检索索引         │
│  - 设置：SettingsRepository（DataStore）         │
└───────────────┬───────────────────────────────┘
                │
┌───────────────┴───────────────────────────────┐
│ 数据源层                                         │
│  - assets/Laws/db.sqlite3（category / law 表）   │
│  - assets/Laws/<folder>/<file>.md（法条正文）     │
│  - 用户库 Room：favorites / history / law_item    │
└───────────────────────────────────────────────┘
```

## 2. 数据模型

- **目录库（只读，来自 Laws 数据源）**
  - `category(id, name, folder, group, isSubFolder, order)`
  - `law(id, level, name, filename, publish, order, subtitle, category_id)`
  - 由 `AssetsLawDataSource` 以 `SQLiteDatabase.OPEN_READONLY` 访问，不写回。
- **用户库（Room）**
  - `favorites(id, lawId, lawName, article, content, classify, tag, createTime)`
  - `history(lawId, lawName, lastRead)`
  - `law_item(rowId, lawId, lawName, article, content, breadcrumb, category)` —— 检索索引。
- **领域模型（`data/model/LawModels.kt`）**
  - `LawGroup(level, title, groups, items)` 嵌套树；`LawItem(article, content)` 叶子。
  - `flatten(breadcrumb)` 将树展开为带面包屑的 `FlatItem` 列表，用于检索与展示。

## 3. 关键流程

### 3.1 首次启动
`MyApplication.onCreate` 在 IO 协程中调用 `LawRepository.ensureSearchIndex()`：
遍历所有 `law`，按 `resolvePath` 找到 Markdown，经 `LawParser.parse` 解析，
把每一条 `FlatItem` 写入 `law_item` 表（一次性，SharedPreferences 标记去重）。

### 3.2 阅读路径
`HomeScreen → LawListScreen(categoryId) → ReaderScreen(lawId[, article])`
- `ReaderScreen` 拉取 `LawEntity`，`parseLaw` 得到树，构建渲染节点（标题/条文）。
- 传入 `article` 时（来自检索/收藏深链），定位并高亮该条。
- 进入即写入 `history`；顶部收藏按钮写入/移除 `favorites`。

### 3.3 检索路径
`SearchScreen`：`repo.search(kw)` → `LawItemDao.search` 执行
`WHERE content LIKE '%'||:kw||'%' OR article LIKE '%'||:kw||'%'`（子串匹配）。
结果列表点击 → `ReaderScreen(lawId, article)`，实现深链高亮。

## 4. 整合点映射

| 能力 | 来源项目 | 落点文件 |
| --- | --- | --- |
| Markdown 解析（frontmatter/标题嵌套/条文拆分） | xloger/LawRefBookAndroid `LawParser` | `data/parser/LawParser.kt` |
| 路径解析（刑法特例/子标题/文件名/publish 变体） | xloger `lawTran` | `data/PathResolver.kt`（纯函数，已验证 1688/1688） |
| Room 收藏模型 | xloger | `data/entities.kt` `FavoritesEntity` |
| 丰富设置（深色/主题色/字号/行距/间距） | IncoderApp `SettingsFragment` | `data/settings/SettingsRepository.kt` + `ui/settings/SettingsScreen.kt` |
| 自定义分类显隐与顺序 | IncoderApp | 预留 `SettingsRepository.customCategories`（JSON） |
| 数据源 | LawRefBook/Laws | `AssetsLawDataSource` 只读访问 |

## 5. 设计决策

### 5.1 检索：为何不用 FTS5
- 旧版 Android（< API 30）SQLite 无 `trigram` tokenizer；
- `trigram` 要求查询 ≥ 3 字符，2 字中文（合同/赔偿/宪法）返回 0 命中；
- 改用 `LIKE` 子串匹配：中文任意长度通用、各版本通用，实测 2 字查询 < 2ms。

### 5.2 不引入依赖注入框架
以 `MyApplication` 懒加载 `repository` / `settings`，UI 通过 `rememberRepository()` /
`rememberSettings()` 组合取用，保持依赖最小、源码易读，契合“整合开源、便于阅读修改”的目标。

### 5.3 数据源打包策略
当前将 `Laws` 灌入 `assets/Laws/`（由 `scripts/prepare_assets.py` 完成）。
正式发布建议改为首次启动从外部存储/网络加载（已预留 `READ_EXTERNAL_STORAGE` 权限
与可替换的数据源接口），避免 APK 体积过大。

## 6. 测试

纯 JVM 单元测试（无需设备，`gradlew testDebugUnitTest`）：
- `LawParserTest`：frontmatter 跳过、标题嵌套、续行累积、面包屑。
- `PathResolverTest`：候选路径顺序（含刑法特例）。
- `SearchMatchTest`：子串检索语义（含 2 字中文命中）。

调研阶段另以 Python 对真实数据源做了规模化验证：解析 1688 部法规、74326 条法条、0 缺失；
路径解析 1688/1688 命中。

# 法条通 · LawRefBook 整合版

一个整合自多个开源安卓法条应用的**离线法律条文阅读器**（Android）。

整合了 [xloger/LawRefBookAndroid](https://github.com/xloger/LawRefBookAndroid)（Kotlin + Room）与 [IncoderApp/LawRefBook](https://github.com/IncoderApp/LawRefBook)（Java + 原生 SQLite）的核心能力，统一接入二者共享的开源数据源 [LawRefBook/Laws](https://github.com/LawRefBook/Laws)（公有领域法条）。

- 包名：`com.lawrefbook.unified`
- 技术栈：Kotlin + Jetpack Compose + Material 3 + Room + DataStore + Navigation Compose + WorkManager
- 最低 SDK：28（Android 9），目标 / 编译 SDK：34
- 仅发布 **64 位**安装包（`arm64-v8a` / `x86_64`）
- 许可证：Apache-2.0（数据源 LawRefBook/Laws 为公有领域）

---

## 功能

- **分类浏览**：宪法、刑法、民法典、法律、行政法规、司法解释、部门规章、司法案例等分类下钻到具体法规。
- **法条阅读**：解析后的嵌套目录树 + 条文卡片，章节抽屉快速跳转；支持自定义字号、行距、法条间距、深色模式、主题色（Material You 动态配色）。
- **全文检索**：对任意中文关键词（含 2 字）做子串检索（精确/模糊两种模式），支持按分类、效力层级、发布时间筛选与多字段排序，结果高亮并**深链到具体条文**。
- **收藏**：按条文收藏，可删除；**历史**：最近浏览（最多 100 条）。
- **文本选择**：阅读页长按选词，拖动手柄精确调整选区，实时放大镜 + 触觉反馈，支持复制 / 系统分享。
- **法条数据同步**：每月自动检查上游 LawRefBook/Laws 更新，发现新版本自动下载同步；设置页可手动检查并查看更新状态（检查中 / 有更新 / 下载进度 / 已更新）。
- **离线优先**：全部数据随 APK 打包，首次启动解压到私有目录，无网可用。

---

## 快速开始

### 环境要求（重要）

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | **17**（必须） | Kotlin 1.9.24 / KSP 1.9.24 在 **Java 21+（尤其 25）下会崩溃**（`IllegalArgumentException: 25.0.3`）。Android Studio 自带 JBR 可能是 21/25，请在 Gradle 中显式使用 JDK 17。 |
| Android SDK | `platforms;android-34`、`build-tools;34.0.0` | 见 `local.properties` 的 `sdk.dir` |
| Gradle | 8.9（已生成 wrapper） | |

本机已验证命令：`JAVA_HOME=<JDK17路径>` 后执行 `./gradlew ...`（Windows 用 `.\gradlew.bat`）。

### 构建

```bash
# 单元测试（纯 JVM，无需设备）
./gradlew testDebugUnitTest

# 安装包（debug 已签名可直接 adb 安装）
./gradlew assembleDebug

# Release（当前未配置签名密钥，产物为未签名 APK）
./gradlew assembleRelease
```

产物：`app/build/outputs/apk/<variant>/FatiaoTong-<variant>.apk`

### 安装到设备

```bash
adb install -r app/build/outputs/apk/debug/FatiaoTong-debug.apk
adb shell am start -n com.lawrefbook.unified/.MainActivity
```

> 已在真机（OPPO PLQ110 / Android 14）验证：首次启动自动解压数据、后台构建检索索引（74,326 条 / 1688 部法规）、无崩溃。

---

## 测试

纯 JVM 单元测试（无需设备），共 **17 个用例全部通过**：

| 测试类 | 覆盖 |
| --- | --- |
| `LawParserTest`（6） | frontmatter 跳过、标题嵌套、条文拆分、**多段落换行保留**、面包屑 |
| `PathResolverTest`（3） | 路径候选顺序（刑法特例 / subTitle / fileName / publish 变体） |
| `SearchMatchTest`（3） | LIKE 子串检索语义（含 2 字中文命中） |
| `ArticleOrdinalTest`（5） | 条号排序（中文数字 一/十/百/千、阿拉伯数字、不可解析兜底） |

---

## 整合来源

| 来源 | 借鉴点 | 在本工程中的落点 |
| --- | --- | --- |
| xloger/LawRefBookAndroid | Markdown 解析算法、`lawTran` 路径解析、Room 收藏模型 | `data/parser/LawParser.kt`、`data/PathResolver.kt`、`data/entities.kt` |
| IncoderApp/LawRefBook | 丰富的设置项（深色、主题色、字号/行距/间距） | `data/settings/SettingsRepository.kt`、`ui/settings/SettingsScreen.kt` |
| LawRefBook/Laws（数据源） | 目录库 `db.sqlite3` + 各分类 Markdown 法条 | `AssetsLawDataSource` 只读访问 + `assets/laws.zip`（首次解压到私有目录） |

---

## 架构

```
┌───────────────────────────────────────────────┐
│ UI 层（Jetpack Compose）                        │
│  HomeScreen / LawListScreen / ReaderScreen      │
│  SearchScreen / FavoritesScreen / HistoryScreen │
│  SettingsScreen（AppNav 路由 + 底部导航/导轨）     │
└───────────────┬───────────────────────────────┘
                │ 经 MyApplication 取得
┌───────────────┴───────────────────────────────┐
│ 仓库层 LawRepository（统一门面）                  │
│  - 目录库（只读）：AssetsLawDataSource           │
│  - 用户库（Room）：收藏 / 历史 / 检索索引         │
│  - 设置：SettingsRepository（DataStore）         │
└───────────────┬───────────────────────────────┘
┌───────────────┴───────────────────────────────┐
│ 数据源层                                         │
│  - assets/laws.zip → filesDir/laws/（db.sqlite3 目录库 + .md 正文）│
│  - 用户库 Room：favorites / history / law_item    │
└───────────────────────────────────────────────┘
```

详细说明见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

### 数据模型

- **目录库（只读，`db.sqlite3`）**：`category`（23 个分类）、`law`（1688 部法规，含 level/name/filename/publish/subtitle/tags）
- **用户库（Room）**：`favorites`（按条文收藏）、`history`（最近浏览）、`law_item`（检索索引，74,326 条）
- **领域模型（`data/model/LawModels.kt`）**：`LawGroup` 嵌套树 → `flatten(breadcrumb)` 带面包屑扁平化

### 关键流程

- **首次启动**：`MyApplication.onCreate` 后台解压 `laws.zip` → 遍历全部法规解析 Markdown → 写入 `law_item` 检索索引（一次性，版本戳 `search_index_version` 标记，递增即重建）
- **阅读**：`HomeScreen → LawListScreen(categoryId) → ReaderScreen(lawId[, article])`；进入写历史，深链条文高亮
- **检索**：`SearchScreen → repo.search(SearchQuery)` → SQL 子串匹配 + 筛选 → 内存排序 → 深链跳转
- **更新**：`MonthlyUpdateWorker`（每 30 天、仅非计费网络）→ `UpdateRepository` 比对上游 commit SHA → 下载 zip 解压覆盖（自动处理 GitHub 归档 zip 的顶层目录前缀）

---

## 体积优化与数据加载

- 数据源（`db.sqlite3` + 各分类 `.md`）以**单个 `assets/laws.zip`（约 36MB）**随 APK 发布。
- 上游 `db.sqlite3` 内含 FTS5 全文索引表（约 7.4 万行），而本 App 采用 `LIKE` 子串检索、从不查询 FTS，故 `scripts/optimize_assets.py` 在打包时**删除冗余 FTS 表**（并可排除 App 不消费的 DLC/工具目录）。
- `app/build.gradle` 通过 `aaptOptions { ignoreAssetsPattern "Laws" }` 排除原始散目录 `assets/Laws/`，**仅打包 `laws.zip`**。
- 首次启动把 zip 解压到私有目录 `filesDir/laws/`；**解压版本戳**（`laws_extracted_commit`）保证 App 升级携带新 zip 时自动重新解压。

### 重新生成数据（可选）

```bash
# 1) 从 Laws 仓库灌入原始数据到 assets/Laws/
python scripts/prepare_assets.py --source <Laws仓库路径> --dest app/src/main/assets/Laws

# 2) 删除冗余 FTS 索引、排除冗余目录，打包为 assets/laws.zip
python scripts/optimize_assets.py
```

---

## 检索方案说明（重要）

本工程**未采用 FTS5 trigram**，原因是：

1. 旧版 Android（< API 30）的 SQLite 不支持 `trigram` tokenizer；
2. `trigram` 要求查询长度 ≥ 3，而中文常见 2 字关键词（合同 / 赔偿 / 宪法）会返回 0 命中。

因此采用 **`LIKE '%' || :kw || '%'` 子串匹配**（精确）+ 同序容错子序列模式（模糊）：中文任意长度通用、各版本通用。筛选条件（分类 / 层级 / 发布时间）直接下沉到 SQL 执行，避免先截断再筛选导致漏命中。

---

## 工程结构

```
app/src/main/java/com/lawrefbook/unified/
├── MainActivity.kt                 # 入口，套用主题
├── MyApplication.kt                # Application：懒加载仓库；后台建索引 + 注册月度更新
├── data/
│   ├── LawDatabase.kt              # Room 单例（收藏/历史/检索索引）
│   ├── LawRepository.kt            # 统一仓库：目录 + 用户数据 + 检索
│   ├── AssetsLawDataSource.kt      # 目录库（只读 SQLite）+ 法条 Markdown 读取
│   ├── LawDataManager.kt           # 解压 laws.zip（带版本戳）
│   ├── BuiltinData.kt              # 内置数据版本锚点（commit / 上游地址）
│   ├── PathResolver.kt             # 纯函数：路径候选顺序
│   ├── parser/LawParser.kt         # 纯函数：Markdown 法条解析（标题嵌套/条文拆分/段落保留）
│   ├── daos.kt / entities.kt       # Room DAO 与实体（LIKE 检索）
│   ├── model/LawModels.kt          # 领域模型
│   ├── settings/SettingsRepository.kt  # DataStore 设置 + 数据版本/更新状态
│   └── update/
│       ├── UpdateRepository.kt     # 检查上游提交 / 下载并同步最新内容
│       └── MonthlyUpdateWorker.kt  # WorkManager 月度周期任务
├── ui/
│   ├── theme/Theme.kt              # M3 主题（深色 + 动态配色 + 自定义主色）
│   ├── navigation/AppNav.kt        # 底部导航/导轨 + 路由
│   ├── home/ lawlist/ reader/ search/ favorites/ history/ settings/
│   └── components/BottomSheet.kt   # 自实现 MD3 底部抽屉
├── design/                         # MD3 设计系统文档 + 可交互原型
├── ui-prototype/                   # 早期 UI 原型
├── scripts/                        # 数据准备 / 优化打包脚本
└── docs/ARCHITECTURE.md            # 架构说明
```

---

## 主要修复记录

| 问题 | 修复 |
| --- | --- |
| 中文数字「二」「两」被映射为 1，条号排序错误（第二十条=10） | `SearchSpec.kt` `cnToLong` 修正 + `ArticleOrdinalTest` 回归 |
| 多段落条文被合并为无换行连续文本 | `LawParser` 续行改为「先补换行再拼接」 |
| 检索先 `LIMIT` 后筛选，>500 命中或分类浏览漏结果 | 筛选条件下沉 SQL，内存仅做排序与截取 |
| GitHub 归档 zip 的 `Laws-master/` 前缀导致更新后目录库路径错位 | `UpdateRepository` 解压后自动上移顶层目录 |
| App 升级携带新 laws.zip 时旧数据不刷新 | `LawDataManager` 解压版本戳 |
| 首页「最近浏览」点击跳检索页而非法规 | 改为深链 `reader/{lawId}` |
| 主题默认色不一致（首帧闪色）、字号范围不一致、数据版本显示不更新等 | 统一默认值 / 显示实际更新时间 |
| 单元测试无法编译/失败（方法名含 `/`、测试输入与真实数据格式不符） | 修复 + 新增回归测试 |

## License

[Apache-2.0](LICENSE)。数据源 [LawRefBook/Laws](https://github.com/LawRefBook/Laws) 为公有领域，详见其仓库说明。

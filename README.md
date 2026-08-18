# 法条通 · LawRefBook 整合版（Android 离线法条阅读器）

> 本文档为**完整的工程交接文档**，供开发者/其他 AI 模型接手本工程使用。
> 覆盖：技术栈、架构、数据模型、全部页面与路由、核心实现细节、构建测试、已知坑位、近期改动。
> 最后更新：2026-08-18

---

## 1. 项目定位

离线法律条文阅读器（Android），整合自两个开源项目 + 开源数据源：

- 整合 [xloger/LawRefBookAndroid](https://github.com/xloger/LawRefBookAndroid)（Kotlin + Room）与 [IncoderApp/LawRefBook](https://github.com/IncoderApp/LawRefBook)（Java + 原生 SQLite）的核心能力
- 数据源：[LawRefBook/Laws](https://github.com/LawRefBook/Laws)（公有领域法条，Markdown + sqlite 目录库）

**核心卖点**：全部数据随 APK 打包（`assets/laws.zip`，约 8.4MB），首启解压到私有目录，完全离线可用。

### 关键版本信息

| 项 | 值 |
| --- | --- |
| 包名 | `com.lawrefbook.unified` |
| 技术栈 | Kotlin + Jetpack Compose + Material 3 + Room + DataStore + Navigation Compose + WorkManager |
| minSdk / target | 28（Android 9）/ 34 |
| 构建 | **JDK 17 必须**（Kotlin 1.9.24 / KSP 在 Java 21+ 崩溃），Gradle 8.9 wrapper |
| 数据规模 | 23 个分类、1688 部法规、74,326 条检索索引条目 |
| 单元测试 | 25 个（纯 JVM，`./gradlew testDebugUnitTest`） |

---

## 2. 架构总览

```
┌─────────────────────────────────────────────────┐
│ UI 层（Jetpack Compose）                          │
│  HomeScreen / LawListScreen / ReaderScreen        │
│  SearchScreen / FavoritesScreen / HistoryScreen   │
│  SettingsScreen×5 / VersionsScreen                │
│  AppNav（路由 + 底部导航 4 项 / 宽屏导轨）           │
└──────────────────┬──────────────────────────────┘
                   │ rememberRepository()（CompositionLocal 注入）
┌──────────────────┴──────────────────────────────┐
│ 仓库层 LawRepository（统一门面，全部 suspend+IO）   │
│  目录（只读）：AssetsLawDataSource                │
│  用户库（Room）：LawDatabase（favorites/history/law_item）│
│  设置：SettingsRepository（DataStore）             │
│  更新：UpdateRepository + MonthlyUpdateWorker      │
└──────────────────┬──────────────────────────────┘
┌──────────────────┴──────────────────────────────┐
│ 数据源层                                          │
│  assets/laws.zip → filesDir/laws/                │
│   （db.sqlite3 目录库 + 各分类 .md 法条正文）        │
└─────────────────────────────────────────────────┘
```

### 关键目录（`app/src/main/java/com/lawrefbook/unified/`）

```
MainActivity.kt             入口（Compose setContent）
MyApplication.kt            Application：后台建检索索引 + 注册月度更新
data/
  AssetsLawDataSource.kt    目录库只读访问（SQLite）+ 法条 md 读取/解析入口
  LawRepository.kt          统一仓库（业务层唯一门面）
  LawDataManager.kt         laws.zip 解压（版本戳控制）
  BuiltinData.kt            数据版本锚点（COMMIT/DATE/上游仓库）
  LawDatabase.kt            Room 单例 + 迁移（v1→v3）
  entities.kt / daos.kt     Room 实体与 DAO
  LawMeta(定义在 AssetsLawDataSource.kt 内)  法规元信息（发文字号/日期/机关/效力）
  AmendmentParser.kt        宪法/刑法修正案解析 → Amendment 列表
  parser/LawParser.kt       Markdown 法条解析器（纯函数）
  model/LawModels.kt        领域模型 + 扩展（lawYear/sortedByTime/displayName）
  search/SearchSpec.kt      检索规格（精确/模糊 + 筛选 + 排序 + 中文数字）
  settings/SettingsRepository.kt  DataStore 设置 + 数据版本 + 更新状态
  update/                   月度更新（GitHub 比对 + 下载 + 解压覆盖）
ui/
  navigation/AppNav.kt      路由表 + 底部导航（首页/收藏/历史/设置）
  home/HomeScreen.kt        首页：搜索框 + 分类列表（点击进二级）
  lawlist/LawListScreen.kt  二级：分类下法规列表（时间排序 + 名称（年份））
  reader/ReaderScreen.kt    阅读页（功能最丰富，见 §5）
  reader/SelectableText.kt  自研长按选择组件（选区/手柄/放大镜/复制）
  versions/VersionsScreen.kt 版本时间线页（当前入口被阅读页下拉替代，保留）
  search/SearchScreen.kt    全文检索页
  favorites/ history/ settings/ 收藏、历史、设置（5 个子页）
  categories/LawCategoriesScreen.kt  分类宫格页（当前无入口，保留）
  components/BottomSheet.kt 自实现 MD3 半屏底部抽屉
scripts/                    数据准备/打包脚本
design/                     HTML 原型（ui-themes.html 主题方案 / refactor-preview.html 重构预览）
```

---

## 3. 数据模型

### 3.1 目录库（只读，`filesDir/laws/db.sqlite3`，来自上游）

- `category`：id/name/folder/isSubFolder/group/order（23 个一级分类 + 子分类）
- `law`：id/level/name/filename/publish/order/subtitle/tags/category_id/**valid_to**（`2099-12-31`=现行有效）

### 3.2 用户库（Room，`lawrefbook_user.db`）

- `favorites`：id(=`lawId|article`)/lawId/lawName/article/content/classify/tag/createTime
- `history`：lawId(PK)/lawName/lastRead/**scrollIndex/scrollOffset**（阅读进度——注意：upsert 手写 SQL 必须显式给 0，否则全新建库因无 DEFAULT 约束崩溃，见 §8）
- `law_item`：检索索引（lawId/lawName/article/content/breadcrumb/category/level/publish/tags/categoryId）

### 3.3 领域模型（`data/model/LawModels.kt`）

```kotlin
LawEntity(id, level, name, fileName, publish, order, subTitle, tags, categoryId, validTo)
LawGroup(level, title, groups, items)   // 解析树
LawItem(article, content)
FlatItem(breadcrumb, article, content)  // 扁平化，用于检索
SearchResult(...)
CategoryEntity(id, name, folder, group, isSubFolder, order)

// 扩展函数（重要）：
fun LawEntity.lawYear(): Int?          // 名称（YYYY年）优先，其次 publish 年份
fun List<LawEntity>.sortedByTime()     // 现行（无年份）最前 → 年份降序 → 同年按名称
fun LawEntity.displayName(): String    // 名称未带年份时补（YYYY年）
```

### 3.4 法条 Markdown 格式（上游数据）

```markdown
# 中华人民共和国宪法          ← 标题（# 开头）
1982年12月4日 第五届全国人民代表大会第五次会议通过   ← 元信息区（日期+机关+动作）
1982年12月4日 全国人民代表大会公告公布施行
<!-- INFO END -->             ← 元信息区结束标记
## 序言                      ← 章节（# 数量=层级）
（正文段落，非"第X条"开头的行累积为一个无条号 item）
第一条 ……                    ← 条文（"第X条"开头拆分）
```

**注意**：
- 解析器会过滤 HTML 注释（`<!-- ... -->`），防止 INFO END/FORCE BREAK 泄漏到正文
- 年份中「〇（U+3007）/○（U+25CB）」两种圈字符已统一替换为「零」（20 处，10 个文件）
- `LawParser` 将"第X条"开头的行作为新条文起点；其他行累积为当前条文的续行（保留换行）

### 3.5 修正案解析（`data/AmendmentParser.kt`，核心新功能）

```kotlin
data class Amendment(year: Int, article: String?, target: String, detail: String)
// article 用于匹配正文："第一条" / "序言" / "第三章"；target 是位置原文；detail 是修正内容
```

支持的两种修正案格式：
- **宪法式**：`第X条 宪法序言第七自然段中"…"修改为"…"；…`（条目以"第X条 "开头）
- **刑法式**：`一、将刑法第一百四十五条修改为："…"`（条目以"一、二、三…"开头，位置可为"刑法第X条"/"第X条"/"第一百五十二条"）

年份解析：文件名带 `（YYYY年）` 直接取；否则从正文元信息区（`YYYY年M月D日 …通过/修正`）提取。

实测解析量：宪法 53 条、刑法 217 条（修正案一~十二）。

---

## 4. 导航与路由（`ui/navigation/AppNav.kt`）

底部导航 4 项（宽屏≥600dp 自动切换为侧边 NavigationRail）：
`home 首页` | `favorites 收藏` | `history 历史` | `settings 设置`

完整路由表：

| 路由 | 页面 | 参数 |
| --- | --- | --- |
| `home` | HomeScreen | - |
| `search` | SearchScreen | - |
| `favorites` | FavoritesScreen | - |
| `history` | HistoryScreen | - |
| `settings` | SettingsScreen | - |
| `theme_settings` / `reader_settings` / `data_settings` / `about` | 设置子页 | - |
| `law_categories` | LawCategoriesScreen（分类宫格，暂无人入口） | - |
| `lawlist/{categoryId}` | LawListScreen（二级法规列表） | categoryId |
| `reader/{lawId}?article={article}` | ReaderScreen（阅读页） | lawId，可选 article 深链定位 |
| `versions/{lawId}` | VersionsScreen（版本时间线页） | lawId |

---

## 5. 页面功能详解

### 5.1 首页（`ui/home/HomeScreen.kt`）

- 顶部搜索框（点击进 `search`）
- 「法律分类」标题 + **分类列表**（23 个分类，行 = 分类名 + 右箭头）
- 点击分类 → `lawlist/{categoryId}`（**点击进二级，非折叠展开**）
- 数据：`repo.getCategories()`，无状态缓存（每次进入重新加载）

### 5.2 二级法规列表（`ui/lawlist/LawListScreen.kt`）

- 标题 = 分类名，返回箭头
- 列表 = `repo.getLaws(categoryId).sortedByTime()`（现行最前→年份降序）
- 条目 = `displayName()`（如"法律援助法（2021）"）+ 层级副标题
- 点击 → `reader/{lawId}`
- **无筛选 chip 行**（已按需求移除）

### 5.3 阅读页（`ui/reader/ReaderScreen.kt`）——功能最丰富

页面结构（自上而下）：

```
顶栏：返回 | 标题 | 📤分享 | 🔍本页搜索 | ☰目录 | ♡收藏
  （🕘历史版本图标已移除，改由"历史变更"时间轴承担）
[可选] 本页搜索框（点 🔍 展开，输入条文号/关键词/数字回车定位到第一个命中项）
  - 支持纯数字跳转：输入 "20" → 自动搜索"第二十条"；输入"二十"同样生效
元信息卡（emoji 前缀 + 效力药丸徽章）：
  📄发文字号  全国人民代表大会公告      ← LawMeta.docNo（从"…公告公布施行"提取）
  🏛️发布机关  全国人民代表大会
  📅发布日期  1982年12月4日
  📅实施日期  1982年12月4日
  当前效力  有效（绿药丸）/ 失效（红药丸）     ← 依据 law.valid_to
历史变更卡（版本数>1 时显示，蓝色时间轴）：
  "历史变更 ▾ 共 N 个版本" → 点击展开蓝色时间轴
    ● 2018 · 宪法修正案（2018年） ›      ← 蓝点 + 竖线连接，点击跳转 reader/{版本lawId}
    ● 2004 · …（降序）
    ● 现行 · 宪法（当前，高亮 primaryContainer）
正文：
  章节标题（字号随层级 20/18/16/15sp）
  条文（SelectableText，可长按选择复制）
  修正脚注标注（有修正的条文下方）：
    "✎ 2018 年修正 · 1 处不同"（橙色卡片，可点击）
    → AlertDialog：年份徽章 + 修正内容原文
```

**状态与加载**（`LaunchedEffect(lawId)` 一次性加载）：
```kotlin
law, nodes(渲染树), flat(扁平), loading, isFav,
meta(LawMeta), versionCount/versionList(版本族), showHistoryChange,
amendments(Map<article, List<Amendment>>), amendDialog, showPageSearch, searchKeyword
```

**渲染树**（`RenderNode`）：`Heading(level,title)` + `Item(article,content)`，由 `LawGroup` 树 DFS 扁平化。

**关键逻辑**：
- 每次进入从顶部开始（**已移除滚动位置恢复**，保留深链定位）
- 修正标注匹配：`amendments[node.article.ifBlank { "序言" }]` → 命中显示标注
- 收藏：`id = "$lawId|$targetArticle"`，无深链时 article 为空串

### 5.4 版本时间线页（`ui/versions/VersionsScreen.kt`）

- 路由 `versions/{lawId}`，展示 `repo.getLawVersions(lawId)`（时间升序，现行置底高亮）
- 点击版本 → `reader/{版本id}`
- **当前主要入口已被阅读页"历史变更"下拉替代**；此页保留供复用（如从检索/收藏进入）

### 5.5 检索页（`ui/search/SearchScreen.kt`）

- 关键词：`SearchQuery(keyword, mode=EXACT/FUZZY, categoryId, level, publishFrom, publishTo, sortField, sortOrder, limit)`
- 精确 = `LIKE '%kw%'`（ESCAPE）；模糊 = 额外同序子序列（`%正%当%防%卫%`）
- 筛选条件下沉 SQL（不先 LIMIT 再筛选）
- 中文数字条号排序：`SearchSpec.articleOrdinal`（支持 零/〇/一~千，修正过「二/两」）
- 深链：点击结果 → `reader/{lawId}?article={article}`（阅读页高亮定位）
- 最近搜索 10 条（DataStore，`\u0001` 分隔）
- **结果卡片化**：每条结果为 Card（surfaceContainerLowest，12dp 圆角），右上角层级徽章（primaryContainer），面包屑用 `›` 分隔，关键词黄色高亮，右侧 `›` 箭头

### 5.6 收藏 / 历史

- 收藏：按条文收藏（lawId|article），支持分类整理（classify）
- 历史：最近 100 条（lastRead 降序），记录浏览（无滚动恢复）

### 5.7 设置（5 个子页）

- **设置主页**：分组卡片（外观/数据/关于），36dp 彩色圆形图标背景（紫/蓝/绿/橙），16dp 圆角卡片 + 阴影 + ChevronRight 箭头
- 主题：深色模式 / 动态配色 / 自定义主色（默认深蓝 0xFF1565C0）；主题色为**横向 5 个圆形色块**（40dp，选中带主色边框+勾选），色块与标签对齐
- 阅读：正文字号 12~28sp（默认 17）/ 行距 1.5 / 法条间距 8
- 数据：自动更新开关 / 手动检查 / 更新状态（检查中/有更新/下载进度/已更新）
- 关于：应用信息 / 数据版本 / 许可证

### 5.8 长按文本选择（`ui/reader/SelectableText.kt`，自研）

- 长按进入选择模式 → 选区高亮 + 边界手柄拖动
- 官方 `Modifier.magnifier` 放大镜（**不要**用自绘 loupe，会错位）
- 复制 / 系统分享，菜单跟随选区位置

---

## 6. 数据打包与版本机制（改数据必读）

### 6.1 数据链路

```
上游 Laws 仓库 → scripts/prepare_assets.py → app/src/main/assets/Laws/（散目录）
              → scripts/optimize_assets.py  → assets/laws.zip（排除 DLC/scrape/scripts/.github/点文件）
APK 构建 → 首启 LawDataManager.ensureExtracted() 解压到 filesDir/laws/
```

### 6.2 版本戳（三个独立机制）

| 机制 | 位置 | 作用 |
| --- | --- | --- |
| `BuiltinData.COMMIT` | `data/BuiltinData.kt` | 与 prefs `laws_extracted_commit` 比对，不同则重新解压（**改 zip 后必须改它**，如追加 `-local-XX`） |
| `INDEX_VERSION` | `LawRepository` companion（当前=5） | 检索索引版本；递增 → 清空重建 law_item |
| 月度更新 | `UpdateRepository` | 比对 GitHub 上游 commit SHA，下载 master.zip 解压覆盖 |

### 6.3 修改法条数据的标准流程

1. 改 `assets/Laws/` 下的 md
2. 重新打包：Python 压缩为 `assets/laws.zip`（排除 DLC 等，compresslevel=9；**不要跑 optimize_assets.py 全脚本**，它会删除散目录）
3. `BuiltinData.COMMIT` 追加标记（如 `-local-03`）触发重解压
4. 如需重建检索索引：`INDEX_VERSION` +1
5. 构建安装验证

---

## 7. 构建 / 测试 / 安装

### 环境（必须）

- **JDK 17**：本机路径 `D:\WorkBuddyData\.toolchain\jdk-17.0.20+8`（Android Studio JBR 是 25，会崩）
- Android SDK：`C:\Users\admin\AppData\Local\Android\Sdk`（platforms;android-34、build-tools;34.0.0）

### 命令

```powershell
# 单元测试（纯 JVM，无需设备）
$env:JAVA_HOME='D:\WorkBuddyData\.toolchain\jdk-17.0.20+8'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat testDebugUnitTest --console=plain

# 构建
.\gradlew.bat assembleDebug --console=plain
# 产物：app/build/outputs/apk/debug/FatiaoTong-debug.apk

# 安装到 MuMu 模拟器（adb 端口可能变化：16384/16385/5555，先探测）
$adb='C:\Users\admin\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb connect 127.0.0.1:16384
& $adb -s 127.0.0.1:16384 install -r app\build\outputs\apk\debug\FatiaoTong-debug.apk
& $adb -s 127.0.0.1:16384 shell am start -n com.lawrefbook.unified/.MainActivity

# OPPO 真机（无线调试，mDNS 自动发现）
& $adb devices -l   # 找到 PLQ110 后 -s 使用
```

### 测试清单（25 个）

| 测试类 | 覆盖 |
| --- | --- |
| `LawParserTest`（10） | frontmatter 跳过、标题嵌套、条文拆分、多段落换行、**HTML 注释过滤** |
| `PathResolverTest`（3） | 路径候选顺序 |
| `SearchMatchTest`（3） | LIKE 子串语义 |
| `ArticleOrdinalTest`（5） | 中文数字条号排序（含 二/两/〇） |
| `AmendmentParserTest`（5） | 宪法修正案（年份式）、刑法修正案（序号式+正文年份提取） |

---

## 8. 已知坑位与约定（重要）

1. **Kotlin 字符串模板 + 中文**：`"$main修正案"` 会被解析为变量 `main修正案`——必须写 `"${main}修正案"`（本项目已踩过两次）
2. **history 表 NOT NULL 崩溃**：`HistoryDao.upsert()` 手写 INSERT 必须显式写 `scrollIndex,scrollOffset` 为 0；实体 Kotlin 默认值不会生成 SQL DEFAULT，全新建库会崩（已修复，勿回退）
3. **圈字符**：数据中「〇（U+3007）/○（U+25CB）」已统一为「零」；`cnToLong` 对两者都兼容
4. **视觉模型误判**：「零」与「〇」字形相近，vision 可能误报——验证以 uiautomator dump（精确文本）为准
5. **JDK 版本**：不用 21/25 构建
6. **模拟器端口**：MuMu 12 的 adb 端口可能随重启变化（16384/16385/5555 都试）；`adb kill-server` 后需重连
7. **优化 zip 时勿删散目录**：手动写 Python 打包即可
8. **长按选择**：保持官方 magnifier，自绘 loupe 已废弃
9. **Compose 约定**：页面内状态用 `remember { mutableStateOf() }`，IO 用 `repo` 的 suspend（内部 withContext(IO)），协程用 `rememberCoroutineScope().launch`
10. **导航**：路由字符串集中定义在 `AppNav.kt`，新增页面先加路由再导航

---

## 9. 近期功能与改动记录（本工程演变）

按时间顺序：

1. **案例功能（已移除）**：曾实现指导性案例/典型案例自动抓取、案例库页面，后按需求**全部移除**（删除 CaseLibrary/CaseUpdater/ui/cases/legal-platform）。保留的通用能力：`getLawVersions`（版本族识别）、`VersionsScreen`。
2. **首页重构**：分类折叠展开 → 点击进二级页 → 当前形态（分类列表+右箭头）
3. **二级列表**：按时间排序（现行最前→年份降序）+ 名称（年份）显示
4. **阅读页增强**（对齐参考 App 核心体验，差异化实现）：
   - 元信息卡（发文字号/发布机关/发布日期/实施日期/当前效力，emoji 前缀 + 效力药丸徽章）
   - 本页搜索（条文号/关键词/数字定位——输入"20"自动转"第二十条"）
   - 历史变更**蓝色时间轴**（蓝点+竖线，替代下拉列表；对齐参考 App 的时间轴交互）
   - 顶栏**分享按钮**（系统分享面板，分享法规名+条文）
   - 修正脚注标注（橙色卡片）+ 点击弹窗（宪法/刑法等所有版本族自动生效）
   - 每次进入从顶部开始（移除阅读进度恢复）
   - 移除数据来源卡片
5. **数据清洗**：20 处圈字符统一为「零」
6. **修复**：history 表 NOT NULL 崩溃（全新建库）、修正案版本族支持序号式命名
7. **UI 整体改进**：
   - 设置页：分组卡片 + 彩色圆形图标背景（36dp）+ 16dp 圆角 + ChevronRight
   - 主题设置：横向 5 色块选择（40dp，选中主色边框+勾选），色块与标签对齐修复
   - 检索页：结果卡片化（12dp 圆角 + 层级徽章 + 面包屑 `›` 分隔 + 关键词高亮）
   - 阅读页：章节目录 AnimatedVisibility 顶部滑入面板（缩进+左侧主色竖线）
8. **反编译调研**：`E:\Harness\反编译\` 下有"法律法规速查 3.5.0"的 apktool/jadx 产物（参考用，勿并入工程）

---

## 10. 给接手者（AI/开发者）的续作建议

- **UI 主题**：`design/ui-themes.html` 有 4 套主题方案（清朗蓝/典雅青/深邃金/法徽红）待用户选定后实施到 `ui/theme/Theme.kt`
- **版本时间线页**：`VersionsScreen` 目前无独立入口（被阅读页下拉替代），如需要可从收藏/检索接入
- **分类宫格页**：`LawCategoriesScreen` 无入口，如需可挂到设置或首页
- **月度更新**：数据源为 GitHub 上游，注意本地 `-local-XX` 后缀会与上游 SHA 不一致（更新时会触发一次下载，可接受）
- **验证方法**：改 UI 后务必装机 + uiautomator dump 确认文本/布局（vision 识别不可靠）

---

## License

Apache-2.0（数据源 [LawRefBook/Laws](https://github.com/LawRefBook/Laws) 为公有领域）

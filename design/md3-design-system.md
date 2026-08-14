# 法条通 · Material Design 3 设计系统

> 面向「法条通」Android 客户端的全新 UI 设计方案。遵循 Material Design 3（MD3）规范，强调动态配色（Material You）、清晰视觉层级、直观导航与多设备响应式适配。
> 配套可交互原型：`design/index.html`（支持明暗主题、动态配色演示、手机/折叠屏/平板三档预览）。

---

## 1. 设计理念

| 维度 | 策略 |
|---|---|
| **视觉层级** | 大号顶部应用栏（Large Top App Bar）→ 分类色卡网格 → 二级信息列表，三级层级分明 |
| **动态配色** | 从壁纸提取源色，自动生成 tonal palette，驱动 primary / container / tertiary 全套角色 |
| **组件形态** | 统一圆角令牌（4/8/12/16/28px）、FAB、分段按钮、底部抽屉（Bottom Sheet）、开关 |
| **导航结构** | 手机：底部导航栏（4 项）；大屏：左侧导航导轨（Navigation Rail） |
| **手势交互** | 抽屉横滑开合、Bottom Sheet 拖拽、列表点击进入、长按（拓展预留） |
| **无障碍** | 文本对比度 ≥ WCAG AA（4.5:1），触控目标 ≥ 48×48px，支持系统字号缩放 |

---

## 2. 色彩系统（MD3 角色令牌）

MD3 不使用「主色/辅色」的简单概念，而是以**语义角色**组织色彩。下表为浅色/深色双主题基准（源色 `#6750A4`）。实际运行时由动态配色引擎按源色生成。

### 2.1 关键色角色（浅色 / 深色）

| 角色 | 浅色 | 深色 | 用途 |
|---|---|---|---|
| primary | `#6750A4` | `#D0BCFF` | 主要操作、选中态、关键强调 |
| onPrimary | `#FFFFFF` | `#381E72` | primary 上的文字/图标 |
| primaryContainer | `#EADDFF` | `#4F378B` | 次级强调背景（chip、图标底） |
| onPrimaryContainer | `#21005D` | `#EADDFF` | 容器上的文字 |
| secondary | `#625B71` | `#CCC2DC` | 辅助操作 |
| tertiary | `#7D5260` | `#EFB8C8` | 第三强调（分类区分） |
| error / onError | `#B3261E` / `#FFFFFF` | `#F2B8B5` / `#601410` | 错误态、必填校验 |
| surface / onSurface | `#FFFBFE` / `#1C1B1F` | `#1C1B1F` / `#E6E1E5` | 卡片/页面底色与文字 |
| surfaceVariant | `#E7E0EC` | `#49454F` | 输入框、分割块背景 |
| onSurfaceVariant | `#49454F` | `#CAC4D0` | 次级文字、图标 |
| outline / outlineVariant | `#79747E` / `#CAC4D0` | `#938F99` / `#49454F` | 描边、分割线 |
| background / onBackground | `#FFFBFE` / `#1C1B1F` | `#1C1B1F` / `#E6E1E5` | 应用底层背景 |

> **动态配色公式（原型已实现）**：源色 → HSL → 主色（保持高饱和、明度 0.45/0.80）→ 容器色（同色相、低饱和、极高/极低明度）→ 辅色（色相 +35°、降饱和）→ 第三色（色相 +150°）→ 中性面（源色极低饱和、近白/近黑）。全部自动联动，无需手工调配。

---

## 3. 排版系统（MD3 Type Scale）

字体族：`-apple-system, "PingFang SC", "Microsoft YaHei", "Noto Sans SC", Roboto, system-ui`。中文优先使用 苹方 / 雅黑，保证多端一致。

| 角色 | 字号 / 行高 | 字重 | 示例（应用位置） |
|---|---|---|---|
| displaySmall | 36 / 44 | 400 | （预留）大标题 |
| headlineLarge | 32 / 40 | 400 | 阅读页章节标题 |
| headlineMedium | 28 / 36 | 400 | 分类页大标题 |
| titleLarge | 22 / 28 | 400 | Large Top App Bar 标题（「法条通」） |
| titleMedium | 16 / 24 | 500 | 列表主文字、设置项 |
| bodyLarge | 16 / 24 | 400 | 法条正文阅读 |
| bodyMedium | 14 / 20 | 400 | 列表辅助文字、说明 |
| labelLarge | 14 / 20 | 500 | 按钮文字 |
| labelMedium | 12 / 16 | 500 | 底部导航、chip |
| labelSmall | 11 / 16 | 500 | 角标、注释 |

**排版原则**：法条正文 `bodyLarge`（16sp），章节标题 `headlineLarge`，条文编号加粗并着 primary 色以建立「条目锚点」视觉节奏。

---

## 4. 形状与 elevation 令牌

### 形状（圆角）
| 令牌 | 值 | 应用 |
|---|---|---|
| corner-extra-small | 4px | 分割小元素 |
| corner-small | 8px | 按钮、chip、输入框 |
| corner-medium | 12px | 卡片、列表项 |
| corner-large | 16px | 对话框、底部抽屉 |
| corner-extra-large | 28px | Bottom Sheet 顶部圆角 |
| corner-full | 999px | FAB、开关、pill 按钮 |

### Elevation（阴影层级）
| 层级 | 阴影 | 应用 |
|---|---|---|
| 0 | 无 | surface 平面 |
| 1 | `0 1px 2px rgba(0,0,0,.3)` | 分段按钮选中态 |
| 2 | `0 1px 3px rgba(0,0,0,.2)` | 卡片 hover |
| 3 | `0 4px 12px rgba(0,0,0,.3)` | FAB、Bottom Sheet |

---

## 5. 组件库

### 5.1 顶部应用栏（Top App Bar）
- **Large 版**（首页）：标题 `titleLarge` 左对齐，下方嵌入搜索栏；承载品牌与一级入口。
- **Small 版**（列表/阅读/搜索）：左返回、居中标题、右侧操作图标（筛选、目录、书签）。
- 颜色使用 `surface` + `onSurface`，避免状态栏与内容色差（旧版问题已规避）。

### 5.2 底部导航栏（Bottom Navigation）
4 项：首页 / 搜索 / 收藏 / 设置。选中项以 `primary` 着色，图标上方显示 `secondaryContainer` 圆底高亮。

### 5.3 导航导轨（Navigation Rail）
折叠屏 / 平板（≥720dp）自动切换：左侧竖排 4 项图标 + 文字标签，FAB 移至左下。

### 5.4 卡片（Card）
- 分类色卡：圆角 16px，`surfaceVariant` 底 + `primaryContainer` 图标块，点击切换至法规列表。
- 列表项：leading 圆形图标 + 主/次双行文字 + trailing 操作（书签/箭头），hover 微提亮。

### 5.5 搜索栏（Search Bar）
MD3 全圆角胶囊，置于 Large Top App Bar 下方；聚焦态展开筛选分段按钮（综合 / 效力层级 / 发布机关 / 仅标题）。

### 5.6 分段按钮（Segmented Button）
用于筛选与「收藏/历史」切换，选中项抬升为 `surface` + 阴影，未选中为 `surfaceVariant` + `onSurfaceVariant`。

### 5.7 开关（Switch）
设置页「动态配色」「自动月度更新」使用，开启态着色 `primary`。

### 5.8 FAB（浮动操作按钮）
阅读页右下「加入收藏」，圆 56px，`primaryContainer` 底；大屏移至左下。

### 5.9 底部抽屉（Bottom Sheet）
筛选条件以 Bottom Sheet 呈现，顶部拖拽手柄（handle），下滑关闭；遮罩 40% 黑。

### 5.10 左侧抽屉（Navigation Drawer）
分类导航 / 阅读页目录，横滑或点 ☰ 打开，点击遮罩或手柄外区域关闭。

---

## 6. 导航结构

```
法条通
├─ 首页 Home
│   ├─ 大号顶栏 + 搜索入口
│   ├─ 法律分类（色卡网格）
│   ├─ 最近浏览（chips）
│   └─ 常用法规（列表）→ 点击进入 阅读页
├─ 法规列表 LawList（分类下钻）
│   ├─ 分段筛选 + 排序
│   └─ 列表项 → 阅读页
├─ 阅读页 Reader
│   ├─ 目录抽屉（右滑）
│   ├─ 法条正文 + 修订提示卡
│   └─ FAB 收藏 / 字号调整
├─ 搜索 Search
│   ├─ 搜索栏 + 筛选分段
│   └─ 结果列表 → 阅读页
├─ 收藏 / 历史 Favorites
│   └─ 分段切换 → 阅读页
└─ 设置 Settings
    ├─ 外观（主题 / 动态配色 / 字号）
    ├─ 法条数据（版本 / 月度更新 / 检查更新）
    └─ 关于
```

---

## 7. 响应式布局

| 断点 | 形态 | 导航 | 布局要点 |
|---|---|---|---|
| < 600dp | 手机 | 底部导航栏 | 单列，分类 2 列网格 |
| 600–839dp | 折叠屏 | 导航导轨 | 收窄内容，导轨在左 |
| ≥ 840dp | 平板 | 导航导轨 | 内容可多列，阅读页支持目录常驻侧栏 |

实现方式：Compose 中用 `WindowSizeClass` 决策；原型中以设备切换演示三种形态。

---

## 8. 手势与动效

| 手势 | 触发 | 反馈 |
|---|---|---|
| 横滑 | 左缘向内 | 打开分类/目录抽屉 |
| 点击遮罩 | 抽屉/Bottom Sheet 外 | 关闭浮层 |
| 下滑 | Bottom Sheet 手柄 | 收起 Sheet |
| 点击 | 列表项 / 卡片 | 进入下一级，150ms 过渡 |
| 长按 | 列表项（预留） | 弹出上下文菜单 |

动效令牌：`transition-fast 150ms`、`transition-normal 300ms`，缓动 `cubic-bezier(.2,.8,.2,1)`。

---

## 9. 无障碍（Accessibility）

- **对比度**：所有文本/图标对比 ≥ WCAG AA（正文 4.5:1，大文本 3:1）。
- **触控目标**：导航项、按钮、开关最小 48×48dp。
- **语义**：列表/按钮具备 contentDescription；动态配色保证前景/背景自动满足对比。
- **字号缩放**：排版基于 sp，支持系统字号放大至 200% 不破版。
- **动效偏好**：尊重「减少动态效果」系统设置（实现预留）。

---

## 10. 交付物与下一步

**已交付**
- 可交互原型 `design/index.html`（动态配色 + 三档设备预览）
- 本设计系统文档 `design/md3-design-system.md`

**建议落地到代码的下一步**
1. 在 Compose 中建立 `FatiaoTongTheme`（含 light/dark `ColorScheme` + `dynamicDarkColorScheme`）。
2. 将原型中的组件映射为 Compose Material 3 组件（`NavigationBar` / `NavigationRail` / `SearchBar` / `ModalBottomSheet` / `NavigationDrawer`）。
3. 复用现有 `MonthlyUpdateWorker` 与设置页「动态配色 / 月度更新」开关，与原型设置项对齐。
4. 如需，我可直接输出对应的 Kotlin Compose 实现代码。

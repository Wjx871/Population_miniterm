# 微信小程序图标系统

## 当前状态

第 3.5 阶段已完成图标审计、`app-icon` 基础接入结构和最小资源接入。根据用户最新确认，图标可以来自官方、Iconfont 或 GitHub，但不得自行手绘。

当前选用 GitHub 上的 Tabler Icons 官方仓库作为统一图标来源。登录页和移动工作台的全部视觉图标已经统一接入，尚未取得资源的其他语义名称不渲染任何内容，不使用方框或 Unicode 字符占位。

## 系统目标

- 页面只引用稳定的业务语义名称，不感知字体类名、码点或矢量路径。
- 通用交互和业务功能图标使用同一套授权清晰的 Tabler Outline 图标，避免多套线性风格混用。
- 品牌标识使用项目自有图形，不使用普通图标冒充 Logo。
- 状态点、加载环等基础视觉由公共 CSS 组件统一实现。
- 所有正式图标资源本地打包，不依赖 CDN 或运行时网络请求。

## 来源范围

### Tabler Icons

Tabler Icons 官方仓库声明其图标基于 24×24 画布、2px 线宽并采用 MIT 许可，与本项目的线性、圆角和统一视觉重心要求一致。

- 官方项目：https://github.com/tabler/tabler-icons
- 作者/维护组织：Tabler
- 系列：Tabler Icons Outline
- 许可：https://github.com/tabler/tabler-icons/blob/main/LICENSE
- 本地许可副本：`miniprogram/assets/icons/tabler/LICENSE`

当前最小子集：

- `account` → `user.svg`
- `lock` → `lock.svg`
- `eye` → `eye.svg`
- `eye-off` → `eye-off.svg`
- `chevron-right` → `chevron-right.svg`
- `chevron-down` → `chevron-down.svg`
- `population` → `users.svg`
- `household` → `home.svg`
- `brand` → `database.svg`
- `user` → `user-shield.svg`
- `pending-approval`、`approval` → `clipboard-check.svg`
- `migration-in` → `login-2.svg`
- `migration-out` → `logout-2.svg`
- `residence-permit` → `id.svg`
- `application` → `file-description.svg`
- `profile` → `user-circle.svg`
- `checkbox`、`checkbox-checked` → `square.svg`、`square-check.svg`
- `error-circle` → `alert-circle.svg`
- `shield-check` → `shield-check.svg`
- `department` → `building.svg`
- `health` → `activity.svg`
- `refresh` → `refresh.svg`
- `dashboard` → `layout-dashboard.svg`

每个资源的上游 Git SHA 记录在 `miniprogram/utils/icon-assets.js`。SVG 路径直接取自官方仓库，不做手绘重制。

### 品牌标识

品牌标识将在资源接入完成后以项目自有矢量图形单独实现。它需要表达人口、数据和政务管理，但不得仿冒政府徽标、国家机关标志或第三方图标。

## 语义映射

完整语义注册表位于 `miniprogram/utils/icons.js`，资源内容集中在 `miniprogram/utils/icon-assets.js`。

允许多个语义名称在审核后复用同一底层字形，例如 `account`、`person`、`user`，但页面仍使用各自语义名称。

真实资源映射只能集中加入 `ICON_RESOURCE_NAMES`，页面中禁止出现原始路径、码点和资源实现细节。

## 尺寸、颜色与底板

允许尺寸为 `24、28、32、40、48、52、56、80、96rpx`，非法尺寸回退到 `32rpx`。

颜色语义：

- `primary`：`#1677FF`
- `deep`：`#123B7A`
- `success`：`#22A06B`
- `warning`：`#F59E0B`
- `danger`：`#E5484D`
- `muted`：`#98A2B3`

未指定颜色时继承当前文本颜色。`background` 模式使用浅蓝底板和统一圆角，禁用状态统一使用 muted 色。

## 资源目录

正式资源以本地 SVG data URI 形式集中生成，通过 CSS mask 继承页面颜色。没有字体、CDN、远程 URL、演示页或全量图标包。当前仅包含登录页、工作台、导航和现有列表交互实际需要的 24 个底层资源。底部导航使用 `dashboard`、`database`、`application`、`profile` 四个语义。

## app-icon 使用方式

```xml
<app-icon name="population" size="48" tone="primary" background />
```

支持属性：`name`、`size`、`color`、`tone`、`background`、`disabled`、`customClass`。

未知名称会在开发环境输出安全警告，正式渲染为空，不显示乱码或方框。

## 新增图标流程

1. 确认现有语义能否复用，避免同义图标重复。
2. 在 Tabler Icons 官方仓库内选取同一 Outline 系列图标；只有确实无法表达时才评估其他官方或授权清晰的 GitHub/Iconfont 来源。
3. 核对作者、系列和授权信息。
4. 检查是否为统一的 24×24 线性风格、约 2px 线宽和圆角端点。
5. 将语义来源登记到 `ICON_SOURCES`。
6. 将审核后的资源集中加入 `icon-assets.js`，再在 `ICON_RESOURCE_NAMES` 建立语义映射。
7. 运行测试、静态检查、微信开发者工具和真机验证。
8. 更新本文件的来源和验证记录。

## 禁止事项

- 页面直接写字体类名、Unicode 码点或 SVG path；
- 使用 Emoji、汉字、字符箭头或方框作为业务图标；
- 使用 CDN、远程字体或远程图标 URL；
- 自行手绘、临摹或伪造第三方图标来源；
- 混用线性、面性、卡通、拟物或高细节插画图标；
- 将普通第三方图标作为项目品牌 Logo。

## 验证记录

### 自动检查

- Git 门禁：通过，分支、HEAD、ahead/behind 和干净状态符合要求。
- 修改前测试：60/60 通过。
- `app-icon` 基础组件和 22 个底层资源：已实现。
- 登录页品牌、表单、勾选、错误和安全提示图标：已统一。
- 工作台管理员头像、部门、健康状态、刷新、六项指标和五个快捷入口：已统一。
- 人口列表右箭头：已接入。
- 原生 `custom-tab-bar` 四个导航图标：已接入；`layout-dashboard.svg` 上游提交 SHA 已记录在 `icon-assets.js`。

### 微信开发者工具

用户提供的微信开发者工具截图已确认首批 SVG data URI 和 CSS mask 图标可以正常显示，不是方框或乱码。本机仍未发现开发者工具 CLI，新增的底部导航图标、控制台、离线和打包路径需要继续人工复核。

### 真机

未验证。等待开发者工具验证后执行。

### 当前风险

- CSS mask 与 SVG data URI 需要在目标微信基础库和真机上确认。
- 尚未验证极端低版本基础库的 mask 支持情况。
- 剩余语义资源尚未下载，需在最小验证通过后继续选取，避免把未使用资源加入包体。

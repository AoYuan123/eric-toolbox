# Eric的百宝箱 · 项目说明（给下次接手的 Claude）

> 这是 Eric（GitHub 账号 AoYuan123）的个人 Android 工具箱 App。用户不写代码，需求用中文口述；
> 每次改动前**先出界面预览给他确认**，确认后再改代码、发布。所有回答必须基于实测数据，不编造。

## 1. 现状（截至 2026-10-30，v1.0.5）
- 仓库：`AoYuan123/eric-toolbox`（公开）。推送到 `main` → GitHub Actions 自动跑单元测试、编译、签名、发布 Release。
- 版本号 = `1.0.<Actions 运行序号>`，所以会跳号（编译失败的那次也占号）。
- 已安装在用户手机上；App 内「我的 › 检查更新」读 GitHub Releases。
- 界面预览（Design 画布）：https://claude.ai/artifact/L1dDVkCVJGMsTHHB2FNfUH
  - 上排：首页 / 记录 / 我的 / 后台保活 / 锁屏通知示例；下排：实时金价 / 我的积存金 / 我的如意金积存 / 金价设置。
  - 新需求先改这个画布给用户看，确认后再写代码。

## 2. 怎么连上（新会话按顺序做）
1. `add_repo`（owner `AoYuan123`，repo `eric-toolbox`，access `push`），按返回提示 clone 到 `/home/claude/eric-toolbox`。
   - 推送权限来自用户已安装的 Claude GitHub App（只授权了这个仓库）。若提示没有推送权限，让用户到 claude.ai 设置 › 连接器 重新连接 GitHub。
2. 提交信息末尾带会话要求的 Co-Authored-By 等署名行。只改文档时在提交信息里加 `[skip ci]`，避免发一个内容相同的新版本。
3. 本地容器**不能编译**（Google Maven / Gradle 被网络白名单拦截），只能推送后看 Actions 结果。
4. 看编译结果：用户电脑上的内置浏览器（Claude 桌面 App）已允许 github.com，可在页面里用 JS 调
   `https://api.github.com/repos/AoYuan123/eric-toolbox/actions/runs?per_page=1` 查状态；
   失败日志在 run 的 job 页面（`<run html_url>/job/<job id>`），用 `document.body.innerText` 找 `e: ` 开头的行。
5. 发布后核对：下载 Release 里的 APK，确认签名证书与密钥一致、版本号正确。
6. 行情接口在容器里访问不了；要实测接口时用内置浏览器（东方财富 push2/push2his、工行 mybank.icbc.com.cn 已在浏览器允许列表里）。

## 3. 签名与密钥
- 仓库 Secrets（用户自己粘贴的）：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`（别名固定 `eric`）。
- 密钥文件已交给用户另存；**不要**重新生成密钥，否则新版无法覆盖安装。
- 没有 Secrets 时 CI 只出 `-test` 测试包、不发 Release。

## 4. 代码结构
```
app/src/main/java/com/eric/toolbox/
├── MainActivity.kt        底部导航 首页/记录/我的 + 页面栈（Route）
├── App.kt                 启动时建通知渠道、看门狗、同步后台服务
├── core/
│   ├── ToolModule.kt      模块接口（id/name/category/icon/HomeSummary/Screen/background）
│   ├── ModuleRegistry.kt  模块登记表 ← 新模块在这里加一行
│   ├── MonitorService.kt  统一前台服务（specialUse 类型），每个模块一个循环；心跳；唤醒锁
│   ├── RunWindow.kt       运行时段（默认工作日 8:00–23:00），时段外停止，精确闹钟唤醒（WakeReceiver）
│   ├── Watchdog.kt        WorkManager 15 分钟看门狗 + 开机/更新后自启
│   ├── Notifier.kt        通知渠道、到价提醒、测试提醒
│   ├── AlertLog.kt        提醒记录（含 system 事件，如“后台曾被系统停止”）
│   ├── Store.kt           SharedPreferences 分区（global、mod_<id>、alert_log）+ 备份导出/恢复
│   ├── Http.kt            只允许 https，超时与大小上限
│   └── Updater.kt         GitHub Releases 检查更新
├── ui/
│   ├── Theme.kt           配色 C（可切主题：曜石黑/象牙白/墨绿/跟随系统；红涨绿跌可切）+ 通用组件
│   ├── HomeScreen.kt      首页（运行状态条 + 模块卡片，分“监控类/工具类”）
│   └── GlobalScreens.kt   我的、记录、后台保活引导、整理首页、备份恢复
└── modules/gold/          实时金价模块
    ├── GoldLogic.kt       纯逻辑（可单测）：档位、上涨提醒、智能间隔、交易时段、持仓移动加权成本
    ├── GoldSource.kt      行情源与解析（东方财富、新浪、工行 HTML、K 线）+ 异常值过滤
    ├── GoldModule.kt      设置、刷新与提醒判断、工行价格记录、交易明细存储、后台任务
    ├── GoldChart.kt       走势图（纵坐标、缩放、长按十字线）
    ├── GoldScreen.kt      实时金价主页（大盘/积存金/如意金切换、多源比对、走势、入口）
    └── GoldPages.kt       我的积存金/如意金积存（交易明细）、金价设置（含档位）
app/src/test/.../GoldLogicTest.kt   单元测试（含 10/30 实测数据）
```

### 新增一个模块
1. `modules/<name>/` 下实现 `ToolModule`（纯工具只要 `Screen()`；要后台监控再给 `BackgroundTask`）。
2. 在 `ModuleRegistry.all` 加一行。首页卡片、后台调度、运行时段、通知、备份自动复用。
3. 纯逻辑写进可单测的 object，并补单元测试。

## 5. 金价模块的已定规则（用户确认过）
- 品种：大盘（上海金 Au99.99）、工行积存金、工行如意金积存（积存价/赎回价）。
- **档位提醒以大盘价为准**（设置里可改为积存金/任一）；基准 852，首档 850，每档 10 元，无下限；每档只提醒一次；回升超过上次提醒档位一档以上自动重置；跳空合并为“连跌 N 档”。
- 两次查询之间用东方财富分钟线补漏（10/30 曾漏掉 22:24 的 836）。
- 上涨提醒默认 1000 元/克；开盘提醒；智能刷新（>8 元 3 分钟，3–8 元 60 秒，3 元内 15/30 秒）。
- 只在数据新鲜（大盘 10 分钟、工行 5 分钟内）且多源一致（T+D 1.5%、沪金 3%、积存金对大盘 2%）时提醒。
- 运行时段：周一至周五 8:00–23:00，周末不运行。
- 积存金手续费买卖各 0.5%（到手价 = 实时价 × 0.995）；如意金手续费规则未查到，由用户手填。
- 工行无公开历史：积存金/如意金分时、昨收、今开由 App 自己记录；日/月/年 K 显示大盘。
- 历史 K 线里 1000+、1256 是真实价格（2026 年初金价确实过千），只过滤偏离开收盘 ±10% 的异常影线（如 2016-03 最低 181.2）。

## 6. 数据源（2026-10-30 实测）
- 东方财富实时：`https://push2.eastmoney.com/api/qt/ulist.np/get?secids=118.AU9999,118.AUTD&fields=f1,f2,f12,f15,f16,f17,f18,f124`（价格 ÷10^f1，f124 秒级时间戳）
- 东方财富 K 线：`https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=118.AU9999&fields1=f1&fields2=f51,f52,f53,f54,f55&klt=1|101|103&fqt=0&end=20500101&lmt=N`
- 新浪沪金：`https://hq.sinajs.cn/list=nf_AU0`（需 Referer `https://finance.sina.com.cn/`，GBK；第 8 位为最新价）。新浪上没找到上海金现货代码。
- 工行：`https://mybank.icbc.com.cn/icbc/newperbank/perbank3/gold/goldaccrual_query_out.jsp`（GBK；积存金产品号 080020000521，如意金 080020000501；交易时间工作日 9:10–22:30，每 3 秒更新）
- 这些都是网站公开接口，不是官方 API，改版后要更新 `GoldSource.kt`。

## 7. 待定事项
- **国内下载更新**：用户在国内下载 GitHub Release 需要 VPN。已给出方案，用户尚未决定：
  1. （推荐）CI 同步上传到 Gitee 发行版，App 优先从 Gitee 检查和下载。需用户注册 Gitee、建公开仓库（可能需实名）、自己把 Gitee 私人令牌填进 GitHub Secrets。**未验证** Gitee 附件下载是否需要登录，设置后要先实测。
  2. 国内云存储（阿里云/腾讯云），更稳但要开通账号、少量费用、可能涉及域名备案。
  3. 维持现状：电脑开 VPN 下载后传到手机。
- 语言目前只有简体中文。
- 真机上的显示效果、后台保活时长、手势体验都还没有用户反馈。

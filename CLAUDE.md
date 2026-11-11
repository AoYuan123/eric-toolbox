# Eric的百宝箱 · 项目说明（给下次接手的 Claude）

> 这是 Eric（GitHub 账号 AoYuan123）的个人 Android 工具箱 App。用户不写代码，需求用中文口述；
> 每次改动前**先出界面预览给他确认**，确认后再改代码、发布。所有回答必须基于实测数据，不编造。

## 0. 最新状态（2026-11-09 检查）
- 线上 v2.1.69（11-08 23:30），Gitee 已同步；本次加入 2027 年节假日（国办发明电〔2026〕10号，逐日核对）。
- **注意**：11-08 晚另一个会话给 vod 加了 Spider 插件运行时（`SpiderEngine`/`SpiderJar`，DexClassLoader 加载远程 jar，可含加固壳和 .so），
  与下文「type=3 一律跳过」的红线相反。远程代码与 App 同进程，能读到 ai_secret / trials_secret / mail_secret。已向用户说明风险，等他决定。
- v2.1.73：`Session.marketTrading` 按上金所节假日判断（交易日=周一至周五且非放假日；节前最后一天无夜盘，对照 2026 年上金所公告 12/31、2/13、4/3、4/30 都对得上）。
  工行积存金 `bankTrading` 没改：如意金自 2026-02-07 起每天 9:10–22:30 都能做（非交易日限额），积存金节假日规则未查到。
- v2.1.75：按住说话改为松手才发（core/VoiceInput，AI 管家/练口语/学发音共用）；英文识别全大写时改正常大小写；学发音换句后比对错句已修。
- 下面第 1 节是 11-02 的旧状态，版本号等以本节为准。

## 1. 现状（截至 2026-11-02）
- 仓库：`AoYuan123/eric-toolbox`（公开）。推送到 `main` → GitHub Actions 自动跑单元测试、编译、签名、发布 Release，并镜像一份到 Gitee 发行版。
- 版本号 = `1.0.<Actions 运行序号>`，所以会跳号（编译失败的那次也占号）。当前线上 **v1.0.27**。
- 已安装在用户手机上；App 内「百宝箱设置 › 检查更新」**优先读 Gitee Releases**，不通再退回 GitHub（国内直连 GitHub 常常下不动）。
- 模块：实时金价、网盘搜索（juicePans）、在线影视、日期计算、临床试验监控。
- 界面预览（Design 画布）：https://claude.ai/artifact/L1dDVkCVJGMsTHHB2FNfUH
  - 这个画布是 10-31 的状态，**还没有网盘搜索和在线影视**两个模块；这两块目前只靠真机看。
  - 上排：首页 / 记录 / 我的 / 后台保活 / 锁屏通知示例；下排：实时金价 / 我的积存金 / 我的如意金积存 / 金价设置。
  - 新需求先改这个画布给用户看，确认后再写代码。

## 2. 怎么连上（新会话按顺序做）
1. `add_repo`（owner `AoYuan123`，repo `eric-toolbox`，access `push`），按返回提示 clone 到 `/home/claude/eric-toolbox`。
   - 推送权限来自用户已安装的 Claude GitHub App（只授权了这个仓库）。若提示没有推送权限，让用户到 claude.ai 设置 › 连接器 重新连接 GitHub。
2. 提交信息末尾带会话要求的 Co-Authored-By 等署名行。只改文档时在提交信息里加 `[skip ci]`，避免发一个内容相同的新版本。
3. 本地容器**不能编译 Android**（Google Maven / Gradle 被网络白名单拦截），只能推送后看 Actions 结果。
   纯逻辑可以本地验证：从 GitHub 下载 kotlinc（`github.com/JetBrains/kotlin/releases/download/v2.0.21/kotlin-compiler-2.0.21.zip`），
   org.json 用 `github.com/stleary/JSON-java` 源码 javac 编译，再自己写 JUnit/协程/Context 的极简桩代码跑单元测试（10/31 这样跑过 45 个测试）。
   看 CI 结果也可以直接在容器里用 `gh run list -R AoYuan123/eric-toolbox -L 1`；下载 Release 用 `gh api …/releases/latest`（gh 的 GraphQL 命令不可用）。
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
│   ├── MonitorService.kt  统一前台服务（specialUse 类型），全天运行，每个模块一个循环；心跳（含预计下次醒来时间）；唤醒锁
│   ├── RunWindow.kt       提醒时段（默认工作日 8:00–23:00）；数据全天更新，时段开始时精确闹钟唤醒（WakeReceiver）
│   ├── Watchdog.kt        WorkManager 15 分钟看门狗 + 开机/更新后自启
│   ├── Notifier.kt        通知渠道、到价提醒、测试提醒
│   ├── AlertLog.kt        提醒记录（含 system 事件，如“后台曾被系统停止”）
│   ├── Store.kt           SharedPreferences 分区（global、mod_<id>、alert_log）+ 备份导出/恢复
│   ├── Http.kt            只允许 https，超时与大小上限
│   ├── Updater.kt         检查更新（Gitee 优先，GitHub 兜底）
│   ├── ApkInstaller.kt    App 内下载新版并调起系统安装
│   └── Mailer.kt          邮件提醒（QQ 邮箱 SMTP smtp.qq.com:465 + 授权码；多收件人各选类别；失败重试 3 次后记入「记录」）
├── ui/
│   ├── Theme.kt           配色 C（可切主题：曜石黑/象牙白/墨绿/跟随系统；红涨绿跌可切）+ 通用组件
│   ├── HomeScreen.kt      首页（运行状态条 + 模块卡片，分“监控类/工具类”）
│   └── GlobalScreens.kt   我的、记录、后台保活引导、整理首页、备份恢复
├── modules/datecalc/      日期计算（工具类，纯本地）
│   ├── CalendarData.kt    生成的数据：holiday-cn 2007–2026 节假日/调休 + ICU 农历表 1901–2100（勿手改）
│   ├── DateLogic.kt       工作日、农历、日期间隔/推算（java.time，可单测）
│   └── DateCalcModule.kt  界面
├── modules/trials/        临床试验监控（ClinicalTrials.gov API v2）
│   ├── TrialLogic.kt      检索 URL、解析、变化对比、存储格式（可单测）
│   ├── TrialModule.kt     关注条件/快照/变化记录存储、每天 9:00 与 18:00 检查、失败 15 分钟重试、时段外延后推送
│   ├── TrialScreens.kt    主页、关注详情（长按排除）、试验详情、添加/编辑关注（先预览再保存）、翻译设置；NCT 编号可点开原文
│   └── Translator.kt      百度翻译通用文本翻译：签名 MD5(appid+q+salt+密钥)、批量、本地缓存、每月用量上限（默认 80 万）
└── modules/gold/          实时金价模块
    ├── GoldLogic.kt       纯逻辑（可单测）：档位、上涨提醒、智能间隔、交易时段、持仓移动加权成本
    ├── GoldSource.kt      行情源与解析（东方财富、新浪、工行 HTML、K 线）+ 异常值过滤
    ├── KlineRepo.kt       走势数据：东方财富优先 → 新浪沪金主连替代（界面标注差价）→ 手机缓存；分钟线增量拉取
    ├── GoldModule.kt      设置、刷新与提醒判断、工行价格记录、交易明细存储、后台任务
    ├── GoldChart.kt       走势图（纵坐标、缩放、长按十字线）
    ├── GoldScreen.kt      实时金价主页（大盘/积存金/如意金切换、多源比对、走势、入口）
    └── GoldPages.kt       我的积存金/如意金积存（交易明细）、金价设置（含档位）
app/src/test/.../GoldLogicTest.kt、DateLogicTest.kt、TrialLogicTest.kt   单元测试（含实测数据）
```

### 另外三个模块（11-01 / 11-07 加入，结构清单里原来没写）

```
├── modules/pan/           网盘搜索，显示名「juicePans」（id 仍是 pan，别改 id——会清空已存设置）
│   ├── PanModule.kt       模块入口 + 设置持久化（引擎开关/每盘条数/严格匹配/网盘筛选/熔断状态）
│   ├── PanScreen.kt       搜索框 + 网盘筛选 + 源开关 + 结果卡片 + 设置面板
│   ├── core/Aggregator.kt 聚合：去重 → 同名折叠 → 限条 → 四次稳定排序 → 严格匹配剔除（最容易踩坑的文件）
│   ├── core/TitleNormalizer.kt  标题归一化（去噪词/渠道词/全角转半角），给分组和排序用
│   ├── core/EngineCircuit.kt    熔断：普通失败连 2 次熔断 30 分钟；风控按 retry-after，可更久
│   ├── core/Http.kt       独立 HTTP 封装（不共用底座 core/Http.kt）
│   ├── engine/            6 个源 pansou/haisou/yunso/ataw/panxiaozi/movie + PanSearch 编排
│   └── verify/            LinkVerifier 链接存活核验
└── modules/vod/          在线影视。内置推荐源（远程清单可换），搜到即播，不下载不转存
    ├── VodModule.kt       模块入口 + 订阅/画质偏好 + 聚合首页
    ├── VodSubsScreen.kt   订阅管理：远程 URL、本地粘贴、导入文件；支持「图种」和 `//` 注释
    ├── VodQuickAdd.kt     添加接口：粘一条链接，当场判别类型、实测、勾选入库
    ├── VodScreen.kt       聚合首页（全部站点混排）+ 搜索 + 画质门槛（不限/720P+/1080P+/4K）
    ├── VodDetail.kt       详情、线路（带实测码率）、「换到最好的一条」
    ├── PlayerActivity.kt  全屏横屏播放，Media3 ExoPlayer
    └── core/              ConfigParser / SiteClient / EpisodeParser / SearchHub / Quality / TmdbClient
                           BuiltinSources（推荐源播种 + 远程清单）/ LineProbe（实测码率与字幕轨）
                           QuickAdd（链接嗅探：标准接口 / 整份配置淘金 / 拒绝 Spider）
└── modules/zlib/         Z-Library 入口。只拼搜索 URL 交给系统浏览器，App 内不加载站点
    ├── ZlibModule.kt      模块入口 + 站点列表 / 当前站点 / 搜索模板 / 最近搜过 / 探测记录
    ├── ZlibScreen.kt      搜索框 + 站点 chip + 最近搜过 + 快捷入口
    ├── ZlibDomains.kt     站点增删与切换、「试搜」验证搜索模板
    └── core/SiteProbe.kt  连通性探测（三态）+ 域名规范化
```

### 读书模块（11-07 第一批，用户确认预览后开发）
```
└── modules/read/          读书（id=read，工具类）
    ├── ReadLogic.kt       纯逻辑：Gutendex / Standard Ebooks 网页 / Open Library 解析、合并去重、TXT 分章与 GBK 识别、阅读时间估算
    ├── Epub.kt            EPUB2/3 解析（container→OPF→spine，nav 或 NCX 目录，封面）；书内路径规范化，越界条目丢弃；XML 防 XXE
    ├── BookContent.kt     阅读器统一接口（EPUB / TXT），章节纯文本缓存、全文搜索
    ├── ReaderHtml.kt      ReadSettings + 把章节包装成阅读页：删书里的 script/on*，注入样式（4 主题）和翻页脚本（CSS 多栏横向分页 / 上下滚动）
    ├── ReaderScreen.kt    阅读器：WebView 只从 https://book.local/ 读书内资源，其他网络请求全部拦截；目录/书签/搜索/排版/进度条；音量键（core/KeyHooks + MainActivity.dispatchKeyEvent）
    ├── Library.kt         书架 files/read/shelf.json + 书文件 files/read/books/<id>/（不进备份）；下载（https、跟随跳转、校验 PK 头并能解析）、导入
    ├── ReadScreens.kt     书架首页、搜索、详情下载、古籍（WebView 打开 http://read.nlc.cn/thematDataSearch/toGujiIndex）
    └── ReadModule.kt      入口、页面栈、ReadPrefs（排版/读速/最近搜索，存 mod_read，进备份）
```
- 11-07 国内实测：Gutendex 有时 45 秒以上不返回（搜索超时给 40 秒）；Gutenberg 无插图 EPUB 0.6MB 约 8 秒、带插图 24.9MB 约 35 秒；
  Standard Ebooks 完整 OPDS 要会员（401），网页搜索公开，下载链接必须加 `?source=download`，否则返回“感谢下载”HTML；
  Open Library 借阅要登录 IA 账号，App 只跳网页；国图古籍库无公开接口，只支持 http，免登录。
- LibriVox：`https://librivox.org/api/feed/audiobooks/?title=^…&format=json&extended=1`，`url_text_source` 指向 Gutenberg 书号可精确配对；
  音频在 archive.org，国内实测首字节 24 秒、第二次失败——第三批做“先下载章节再听”。
- **第二批（11-07）**：长按单词/句子 → `ReaderCards.WordCard`（离线释义、发音、加生词本、翻译整句、划线）；点已划线句子 → `HighlightCard`（笔记、翻译、删除）；
  `Dict.kt` 读 `assets/ecdict-mini.db.gz`（ECDICT MIT 精简：58,226 词 + 39,047 变形，首次解压到 files/read/）；`Notes.kt` 生词本 vocab.json（莱特纳复习 1/2/4/7/15/30/60 天）与划线 notes.json；
  `VocabScreens.kt` 生词本/笔记/卡片复习；导出走系统分享。翻译复用临床试验的百度翻译设置与额度（Translator.ready/translateMissing/cached）。
  阅读页脚本：`flat()` 把正文文本节点拼成一串（块之间插 \n）供取词取句、按“第 k 次出现”标划线；最后一页补占位点修正分页偏移（11-07 Chromium 实测 13 页左边距全为 22px）。
  默认长按查词（点击留给翻页）；设置里可开「点一下单词就查词」。
- **第三批（11-07）听书**：`Listen.kt` 全局引擎（Compose 可观察状态）——机器朗读用系统 TTS 逐句（`ReadLogic.splitSentences`，Mr./Mrs. 等缩写不断句、超长句按逗号切），
  真人朗读用 ExoPlayer 播放已下载的 LibriVox 段（`AudioDownloads` 排队下载、失败重试 3 次，存 files/read/audio/<书id>/）。`ListenService` 前台服务（mediaPlayback）+ MediaSessionCompat（依赖 androidx.media:media:1.7.0）：
  通知/锁屏/耳机控制，拔耳机暂停，暂停时退出前台可划掉。阅读页 `__speak(句子,k,follow)` 按压缩空白后的“第 k 次出现”高亮并自动翻页（Chromium 实测跨标签、第 31 次出现都能定位）；
  用户手动翻页后停止跟随、显示「回到朗读位置」。`stripTags` 改为块级标签→空格、行内标签直接去掉，保证与 `flat()` 文本一致。进度存在 Book.listen*/audio*。
- 11-07 真机反馈后修复：①loadDataWithBaseURL 在用户手机上先走 data: 地址被外链拦截挡住 → 章节改为 loadUrl("https://book.local/<路径>?__r=n")，拦截器直接返回页面，非 http(s) 一律放行；
  ②朗读/发音无声：targetSdk≥30 必须在 Manifest 声明 `<queries>` TTS_SERVICE；③左右翻页每页一行：WebView 早期 100vh≈0 且不更新 → 脚本 size() 用 innerHeight 像素设高度与栏宽，阅读区有尺寸后才加载；
  ④音量键翻页用户不要：默认关，存储键改为 volume_keys2；⑤LibriVox 查不到返回 HTTP 404 + {"error":"Audiobooks could not be found"}，按“没有录音”处理；中文书名不查。
- 待做：听书真机实测（语音引擎、锁屏续播、耳机按键）。
- 预览画布：读书模块 8 个画板（ReadHome/ReadSearch/ReadDetail/Reader/ReadStyle/ReadListen/ReadVocab/ReadGuji）。

### 翻译模块（11-07，用户确认预览后开发，v1.0.44）
```
└── modules/translate/     翻译（id=translate，工具类）
    ├── TranslateLogic.kt  方向（自动/中→英/英→中，含汉字即中→英）、单个英文单词识别、历史（最多 30 条去重）、语速 0.5/0.75/1/1.25/1.5/2.0（用户定）
    └── TranslateModule.kt 界面 + 自带 TextToSpeech：切句后一次性 QUEUE_ADD 排队（句间不停顿），onStart 高亮当前句；开读时暂停听书
```
- 翻译走 `Translator.translateText(ctx,text,from,to)`（百度，与临床试验/读书共用 APP ID、缓存和每月上限；非 en→zh 方向缓存键带 `from>to|` 前缀）。
- 单个英文单词时查 ECDICT 并可「加生词本」（Notes.addWord，书名记作「翻译」）。历史、方向、语速存 mod_translate（进备份）。

### AI 管家（11-08，用户确认预览后开发，v1.0.46）
```
└── modules/ai/            AI 管家（id=ai；首页顶部一条入口，不进模块格子）
    ├── AiLogic.kt         纯逻辑：服务商表、请求拼装（DeepSeek 加 thinking.type=disabled）、返回/工具调用解析、费用估算、上下文窗口
    ├── AiStore.kt         设置(mod_ai) / 密钥(ai_secret，不进备份) / 月花费 / 对话 files/ai/chat.json / 操作记录 log.json + 快照撤销
    ├── AiTools.kt         AI 可调用的功能：只读直接执行；改动类返回 Plan（改动清单+快照键），用户点确认才 apply
    ├── AiAgent.kt         对话循环（最多 6 轮工具调用），全局协程，离开页面也会完成
    └── AiModule.kt        对话页（确认卡、撤销、按住说话、朗读回答）/ 设置页 / 操作记录
```
- 11-08 实测：用户电脑网络下 api.deepseek.com、dashscope.aliyuncs.com/compatible-mode/v1 都能连通（无密钥返回 401）。
- DeepSeek 价格（官网 11-08）：v4-flash 输入 1 元（缓存命中 0.02）/ 输出 2 元每百万 tokens；v4-pro 3 / 6（命中 0.025）。官网写了“计划近期整体上调定价”，变了要改 AiLogic.deepseekPrice。
- 语音：系统 SpeechRecognizer，Manifest 已加 RECORD_AUDIO 和 `<queries>` RecognitionService。小米 17 Pro Max 有没有识别服务未知——App 第一次按🎙时检测并存 mod_ai.asr_available，设置页显示结果；没有的话备选 sherpa-onnx 离线识别（模型大小待核实）。
- 不开放给 AI 的：删书、删关注、密钥、网盘/影视/zlib 模块。新增工具：在 AiTools.all 加一项，改东西的一定走 plan + 快照键。

### V2.0（11-08，版本号改为 2.0.<run#>；首个 v2.0.50）
- 设计语言「精密工具」：`ui/Design.kt`（DotMatrix 自绘 5×7 点阵数字、Tile、Caption、Meter、`Modifier.press` 按压缩放+触感、NumStyle 等宽数字）。
  只用一个强调色 C.Gold（深色 #FF5A1F 信号橙 / 浅色 #E8501A）；主题只剩 dark（深色）/ ivory（浅色）/ system。不用渐变、发光、emoji、假英文大写标签——用户明确说不要“AI 味”。
  容器下不了字体（GitHub raw / npm 都被拦），所以点阵是 Canvas 自绘，等宽用系统 Monospace。
- 双语：`ui/I18n.kt`，界面文字写 `tr("中文","English")`，「我的 › 语言」跟随系统/简体中文/English，存 global.lang。V2 新页面全部双语，老模块（金价/读书/临床试验等）待补。
- 导航：底部 首页 / 学英语（Route.English）/ 我的 + 右侧对讲机式 AI 键（点=进 AI，长按=进 AI 并自动开始听 `AiAgent.voiceOnOpen`）。记录挪到「我的 › 提醒记录」（Route.Records，records_seen 算新条数）；Route.Storage 存储空间页。
- 首页 `ui/HomeScreen.kt` 便当盒：金价（点阵价、涨跌、距下一档、积存金走势小线）、学英语今日、正在听/最近在读、临床试验新变化（global.trials_seen）、工具格子。
- 学英语 `modules/english/`：EnglishBooks（Claude 编写：生物医药 IP 121 条、日常口语 37 句，给专业语境释义+例句）、EnglishLogic（SM-2 类调度，可单测）、EnglishStore（files/english/cards.json，mod_english）、EnglishModule（首页/背词四种练法/查词/词书）。考试词表来自 Dict.wordsByTag。「AI 讲讲」走 `ai/AiQuick.kt`（同一个 DeepSeek 密钥和月上限）。口语、发音留到下一批。
- 不留垃圾：App 启动时 `ApkInstaller.cleanup` 删已安装的更新包和残片。
- 本会话没有权限编辑 GitHub Release 的说明（API 403），发版说明来自最后一次提交信息——大版本推送前把提交信息写好。

### v2.1（11-08，版本号 2.1.<run#>）
- 学英语补全 `modules/english/EnglishPractice.kt`：练口语（8 个场景，AI 每轮返回 JSON {reply,better,why}，结束出报告并可把表达加入生词本；英文识别用系统 SpeechRecognizer en-US，没有就打字）、
  学发音（MediaRecorder 录到 cache/pron.m4a、离开页面删除；A/B=先 TTS 再放录音；识别检查用 EnglishLogic.wordCheck 标红没识别出的词；6 组易错音）。首页「今天的安排」：复习→新词→口语一场（本地规则，不花钱）。
- 体检 `ui/HealthScreen.kt`（Route.Health）：后台/保活/通知/金价/试验/TTS 引擎/语音识别/AI 密钥与花费/存储/闪退 + 网络通道检测（Gitee、GitHub、DeepSeek 余额），「复制全部」生成文字报告。
- 闪退记录 `core/CrashLog.kt`：未捕获异常写 files/crash/（留 5 份），下次启动记一条系统提醒。
- AI 管家：联网工具 web_search / web_fetch（DeepSeek 官方 POST /v1/tools/search、/v1/tools/fetch，同一把密钥，按 5 元/千次记花费；组名 web，老用户自动补上）；
  本地快答 `AiQuickAnswer`（短句问金价/余额/提醒/背词/试验直接回答，不调用模型）；设置页查余额（GET /user/balance）。搜索返回字段按文档猜的 results[].title/url/snippet|summary，真机没验证过。
- 电脑中转 sync.ps1：下载改为后台作业，5 分钟没完成就放弃，下轮自动重试（11-08 改，写入用户电脑）。

### v2.1.55（11-08）
- 底部导航五格：首页 · 学英语 · AI（居中凸起，GraphicEq 图标；点=进 AI，长按=说话）· 翻译（跳 Module("translate")，不是 tab）· 我的。
- 话筒居中大圆键：AI 对话页、练口语、学发音。
- AI 多对话：files/ai/chats/<id>.json + index.json，当前 id 存 mod_ai.chat_cur；老 chat.json 自动迁入。AiAgent.newChat/openChat/deleteChat；对话页右上 ＋/历史/设置，历史页长按删除，最多 100 个。
- 首页：金价改大号数字（整数 46sp + 小数灰色）+ 涨跌色块 + 整行走势；翻译卡在金价下面，临床试验在最底部。
- 电脑中转偶发 GitHub 下载 504（17:12、17:45、18:12 起连续多次），等下一轮自动重试即可。

### 专业法律英语（11-11，学英语 › 专业法律英语）
- 代码：`modules/english/law/`（LawData 读 assets/law/*.json；LawScreens：首页/法条目录/单条/判例/诉讼地图/中美对照/术语/练习）。每段默认英文，点一下切中文（`Flip`）。
- 数据由 `tools/law/build.py` 生成：原文在 `tools/law/src/`（GitHub Actions 从官方网站下载，sources 分支的 fetch-sources.yml，结果在 sources-data 分支；容器直连这些网站不通，artifact 也下不了，所以存成分支）。
  build.py 逐字核对：判决引文必须在原文里找到；地图里的每个期限必须在出处条文里找到对应数字；术语例句从条文自动截取。对不上就报错退出。
- 来源与译文：中国专利法 zh 原文（cnipa.gov.cn）+ 全国人大官方英译（subsites.chinadaily.com.cn/npc，CNIPA 英文站从境外 404）；美国 35 U.S.C. 来自 uscode.house.gov 发布点 PL 119-138，中文是 Claude 译（us_zh_*.py，逐行对应）；
  21 U.S.C. §355 取 govinfo 2024 年版；判例：supremecourt.gov（Amgen）、loc.gov U.S. Reports PDF（KSR/Mayo/Myriad/Chakrabarty/Festo）、court.gov.cn（指导案例 84 号、2022 知产法庭典型案例）。
- 没拿到原文的：CAFC 判决（Juno v. Kite、Ariad）——cafc.uscourts.gov https、govinfo、CourtListener、Justia 从 GitHub 都拿不到，所以没收录。
- 新增内容：在对应 .py 里加，跑 build.py；法条更新时重跑抓取再 build。术语词书 id = "law"（学英语 › 词书可选）。
- 审查指南（11-11 第二批）：`tools/law/guide_us.py`（MPEP 9th Rev. 01.2024 节选 31 节，英文原句逐字核对 src/mpep/*.html.gz）、`guide_cn.py`（审查指南 2023 第二部分 22 节，中文逐字核对 src/cn_guide/guide2023.txt.gz = 官方 PDF 的 pdftotext -layout 文字；
  局令第 84 号改过的章节必须 source="order84" 从 order84.txt 取，build.py 会拦）、`oa.py`（MPEP Form Paragraphs 原句 25 条按 ¶ 编号核对；中国审查意见句子和美国答复措辞是 Claude 整理，界面标明非官方）。
  输出 guide_us.json / guide_cn.json / oa.json；界面：法律英语首页「审查指南」「美国 MPEP」「审查意见英文表达」，单元页有「常用英文表达」「对照阅读」，法条页反查「审查指南 / MPEP 怎么用这一条」；练习加「审查意见句子」中译英。
  MPEP 正文里的费用金额别搬进 App（2025 年已调过）。

**zlib 的边界（改动前务必守住）**
- **不做内置 WebView**：站点前置 Cloudflare 人机校验（`Checking your browser`），WebView 里基本过不去；一律 `Intent.ACTION_VIEW` 交给系统浏览器，顺带走系统代理。
- **不做账号登录**：Z-Library 下载必须登录，但账号密码只能留在浏览器里，App 一个字都不收。
- **不内置「官方域名清单」**：Z-Library 没有唯一官网，是一组轮换镜像 + 个人域名机制。默认只给 z-library.biz / z-library.sk 当起点，用户自己加。
- **探测失败只允许说「可能需代理」，永远不能说「域名失效」**。墙内直连境外站点失败是常态（DNS 污染、SNI 阻断），同一个域名换网络环境结论就反过来——11-07 就栽在拿本机 curl 超时当「域名已死」的证据上，别在 App 里重犯。
- 搜索路径 `/s/{q}` 沿用 Z-Library 一贯格式，但**没能在第三方证据里确认**，所以做成可改的模板，并配了「试搜」按钮自验。

**pan / vod 的边界（改动前务必守住）**
- `type=3` Spider 一律跳过：它要用 DexClassLoader 把远程 dex 当代码跑，等于让别人在你进程里执行任意程序。影视仓类 App 靠这个吃饭，这里不做。
- 常见配置是「图种」（图片尾部拼 Base64）+ JSON 里带 `//` 注释，两者 `ConfigParser` 都已支持（v1.0.22）。
- 明文 http 已在 v1.0.22 放开，理由写在 `AndroidManifest.xml` 的注释里——**不是手误**，别改回 false。
- 严格匹配的口径：先 `pureTitle` 切掉简介正文，再比关键词；比较时忽略空白与全角差异（站点会把「仙逆」写成「仙 逆」，不忽略就会误杀）。
- **ataw 的 30 秒是单次 HTTP 请求超时，不是引擎超时**：它串行抓 quark/ali/sharepan 三个搜索页，各请求一次，桌面实测三遍合计 26~27 秒、单次 8~9 秒。`PanSearch` 没有 `withTimeout`。别拿「26 秒逼近 30 秒」当理由去放宽超时。
- pansou 默认 `src=all`（含 TG 频道源，噪音偏多）；skill 版默认 `plugin`。这个参数目前**没有 UI**，只能改 `SearchOptions` 的默认值。

**vod 的推荐源（2026-11-08 起，这是对旧策略的推翻）**
- 旧策略是「App 是空壳，一个地址都不内置」。实测下来这条路走不通：市面上流传的 TVBox 配置，点播部分几乎全是 Spider，用户随手找一份进来就是「48 个站、可用 0 个」，模块等于废掉。**用户明确抱怨过它是「虚设」**。
- 现在 `BuiltinSources` 会播种一条名为「推荐源」的本地订阅，装完即可搜、可播。三条红线没变：**只收 type=1（JSON）站、绝不收 Spider、只播不存（不下载不转存不缓存）**。
- 地址优先从 `vod-subscription/recommended.json` 拉（jsDelivr → GitHub raw → ghproxy 依次回退，24 小时节流）。换域名改那个文件即可，**不用发新版**。
- 用户删掉「推荐源」后不会被偷偷加回来（`seeded` 标记只播一次）；「我的」页有「恢复推荐源」手动入口。

**用户粘一条链接时，三种东西别混为一谈（`QuickAdd` 就是干这个的）**
- **标准苹果CMS接口**（`…/api.php/provide/vod`）：`?ac=list` 能返回 `list` 或 `class`，直接收成站点，能搜能播。这是唯一「粘了就能用」的类型。
- **整份 TVBox / 影视仓配置**：里面混着 Spider 和标准接口，可用率差别极大。2026-11-08 实测：`gao/js.json` 298 站**全 Spider、0 可用**；`0827.json` 33 站里 6 个标准接口；`0821.json` 86 站里 7 个。所以 `QuickAdd` 会把候选逐个实测再让用户勾选，**不要**整份塞进订阅了事。
- **Spider / drpy 脚本**（`./lib/drpy2.min.js` + `./js/xxx.js`）：影视仓能跑 298 个站是因为它内置 JS 运行时执行远程脚本。本 App 不做，既是安全红线（等于执行任意代码），也不值得为它塞一个 JS 引擎。
- 统计口径看 `VodConfig.stats`（total / spider / noApi / usable），界面文案从这里来，**不要用正则从 `warnings` 里抠数字**。
- 站点标的清晰度**不可信**（实测「1080P」常是 1080×608、800kbps）。`LineProbe` 在详情页实测每条线路的真实码率/分辨率/字幕轨，异步、失败不影响播放。
- 采集站片源**普遍没有独立字幕轨**（实测 5 个源 SUBTITLES 计数全 0），字幕是烧在画面里的硬字幕。想做「选字幕」只能走本地字幕文件或 OpenSubtitles，别指望片源自带。

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
- 数据全天更新；**提醒时段**周一至周五 8:00–23:00，周末不提醒（10/31 用户改的）。时段外只记录最低/最高价，时段开始时汇总提醒一次（“夜间跌破 840、830”）。
- 刷新间隔：不交易 30 分钟；时段外的夜盘 2 分钟（只记录）；时段内按智能刷新。
- 积存金手续费买卖各 0.5%（到手价 = 实时价 × 0.995）；如意金手续费规则未查到，由用户手填。
- 工行无公开历史：积存金/如意金分时、昨收、今开由 App 自己记录；日/月/年 K 显示大盘。
- 历史 K 线里 1000+、1256 是真实价格（2026 年初金价确实过千），只过滤偏离开收盘 ±10% 的异常影线（如 2016-03 最低 181.2）。

## 6. 数据源（2026-10-30 ~ 11-02 实测）
- 10/31 晚，用户网络下东方财富**全部接口**（含实时 push2）都连不上了，疑似被封 IP（App 请求过频 + 测试）。已加上金所官网备用：
  - 上金所延时行情 `POST https://www.sge.com.cn/graph/quotations`，表单 `instid=Au99.99` → `{"times":[...],"data":[...],"delaystr":"2026年11月02日 02:29:58"}`。
    times/data 覆盖整个交易日，**未发生的分钟也被填了值**，只取到 delaystr 时刻；delaystr 的日期是交易日（周五夜盘记作下周一），只用时分秒。延时多久尚未在交易时段实测。
  - 上金所日线 `POST https://www.sge.com.cn/graph/Dailyhq`，`instid=Au99.99` → `{"time":[["2026-10-30",开,收,低,高],...]}`（2016-12-19 起）。
  - 顺序：大盘价 东方财富 → 上金所；走势 东方财富 → 上金所 → 新浪沪金主连 → 手机缓存。
- ClinicalTrials.gov API v2：`https://clinicaltrials.gov/api/v2/studies`（CORS 允许，内置浏览器需用户允许站点）。query.intr/cond/spons/locn、filter.ids、filter.overallStatus（逗号分隔）、aggFilters=phase:2 3、sort=LastUpdatePostDate:desc、pageSize≤1000。关键词加引号=完整短语（“Lutetium Lu 177”+China：加引号 6 项，不加 21 项）。
- 节假日：NateScarlet/holiday-cn，2027.json 截至 10/31 为空（国务院未公布）。公布后重新生成 CalendarData.kt（脚本思路：按日期所属年份汇总所有 json，跨年调休日别漏）。
- 东方财富实时：`https://push2.eastmoney.com/api/qt/ulist.np/get?secids=118.AU9999,118.AUTD&fields=f1,f2,f12,f15,f16,f17,f18,f124`（价格 ÷10^f1，f124 秒级时间戳）
- 东方财富 K 线：`https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=118.AU9999&fields1=f1&fields2=f51,f52,f53,f54,f55&klt=1|101|103&fqt=0&end=20500101&lmt=N`
- 10/31 起东方财富 K 线路径（push2his …/kline/get、trends2）在用户网络下连接被断开（实时接口正常），疑似限流，原因未最终确认。走势改为：东方财富失败后 10 分钟内不再请求，改用新浪：
  - 新浪沪金主连日线 `https://stock2.finance.sina.com.cn/futures/api/jsonp.php/var%20_k=/InnerFuturesNewService.getDailyKLine?symbol=AU0`（2008 年至今），分钟线 `…/InnerFuturesNewService.getFewMinLine?symbol=AU0&type=1`（约 2 天）。是期货价，与 Au99.99 有差价；会出现未来时间的异常条目（已过滤）。档位补漏只用东方财富，不用新浪。
- 新浪沪金：`https://hq.sinajs.cn/list=nf_AU0`（需 Referer `https://finance.sina.com.cn/`，GBK；第 8 位为最新价）。新浪上没找到上海金现货代码。
- 工行：`https://mybank.icbc.com.cn/icbc/newperbank/perbank3/gold/goldaccrual_query_out.jsp`（GBK；积存金产品号 080020000521；交易时间工作日 9:10–22:30，每 3 秒更新）
  - **如意金（080020000501）不在这个页面的 HTML 里**，是网页打开后用 JS 异步取的（11-02 才发现，之前用浏览器渲染后的页面核对，误以为 HTML 里有，所以 App 一直没取到如意金）。
    接口：`POST https://mybank.icbc.com.cn/servlet/AsynGetDataServlet`，表单 `tranCode=A00505`，不需要登录，返回 UTF-8 JSON，`pronoinfo[].buyprice/sellprice` 单位是分（85103 = 851.03）。
  - 教训：核对网页数据时要看服务器原始返回（fetch 原文），不要看浏览器执行完 JS 的页面。
- 这些都是网站公开接口，不是官方 API，改版后要更新 `GoldSource.kt`。
- 网盘搜索（11-01 从 juicePans skill 移植，Python → Kotlin）：pansou、haisou、yunso（`mode=90001`，`note=ScrName`）、ataw `so.ataw.top`（三个搜索页 quark/ali/sharepan 串行抓 + `/api/v1/public/resources/{id}` 详情）、panxiaozi、movie。
  - ataw 详情**并发不能超过 2**，且要加 200~800ms 随机间隔，否则很快被风控（代码里已固定）。
  - pansou 天生慢（桌面经常 47s+），单独给了 45s 超时；`src=all` 含 TG 频道源，噪音多。
  - 11-02 实测：yunso 1.0s/100 条、ataw 26.0s/24 条；pansou、haisou 均处于风控冷却。
- 影视源：**内置一组推荐源（2026-11-08 起）**，同时仍支持用户自己填 TVBox/CatVod 订阅（远程 URL 或本地粘贴）。`ConfigParser` 解析 `sites/lives/parses`，只吃 `type=0/1` 且 api 是 http(s) 的站，`type=3` 跳过。多数流传配置是全 Spider（例如饭太硬那份：48 个站全 type=3），**订阅成功不等于可看**，只有直播部分能用——这正是要内置推荐源的原因。

## 7. 国内下载更新（11-02 定为 Gitee）
- Gitee 公开仓库 `aos-promise/eric-toolbox-release`（只放安装包）；GitHub Secret `GITEE_TOKEN`（用户自己贴的 Gitee 私人令牌）。
- 同步由 `.github/workflows/gitee-sync.yml` 负责：Build APK 完成后触发 + 每 2 小时补传 + 可手动运行；读 GitHub 最新正式版，Gitee 上没有带安装包的同名版本就上传（3 次，卡住即放弃），失败删空发行版；只保留最近 10 个（用户 11-02 定）。11-02 晚 GitHub→Gitee 上传曾连续卡住几十分钟，所以从编译流程里拆出来。
- App `Updater`：先查 Gitee `api/v5/repos/<repo>/releases/latest`，不通再查 GitHub。
- `core/ApkInstaller`：App 内下载（自己跟随跳转、只允许 https）→ 校验是本 App 且版本号对 → FileProvider 调起系统安装；首次需允许“安装未知应用”。Gitee 返回的 Content-Type 是 application/zip，所以不能交给浏览器下载（可能存成 .zip）。
- 11-02 实测：不登录下载 Gitee 发行版附件可行（99MB 附件跳转 foruda.gitee.com 正常下载）。
- 备选方案记录：阿里云 OSS 自 2024-08-05 起新建 Bucket 用默认域名下载 APK 会被拦截，必须绑定已 ICP 备案的域名。

- **11-07 起主通道改为用户电脑中转**（Windows，`文档\EricToolbox-GiteeSync\`）：任务计划程序 `EricToolbox-GiteeSync` 每 5 分钟跑 `sync.ps1`（经 run-hidden.vbs 静默）→ 查 GitHub 最新版 → Gitee 没有就下载到本机再上传。
  - 首次实测 v1.0.33：下载 14.2MB 用 51 秒，上传 Gitee 5 秒。同一天 GitHub Actions→Gitee 上传连续多次卡死 7 分钟 0 字节。
  - Gitee 令牌由用户在 install.bat 里自己粘贴，DPAPI 加密存 token.dat，Claude 不经手；uninstall.bat 删除任务和令牌。
  - 坑：PowerShell 5.1 读 curl.exe 输出默认按 GBK，会把 UTF-8 中文连同 JSON 读坏，脚本里已设 `[Console]::OutputEncoding = UTF8`；Invoke-WebRequest 必须关进度条。
  - 11-07 两个坑已修：①与 GitHub 备用同步同时建发行版时 Gitee 报“该标签已经存在发行版”→ 脚本改为再查一次用已有的；②任务计划默认“只在接通电源时运行”，笔记本用电池时不跑 → fix.bat / 新 install.ps1 已关掉该限制。
  - 往用户电脑写文件（device_commit_files）同一个 stagedPath 第二次提交可能写进旧内容：每次换新目录再提交，并用 device_list_dir 核对大小。
  - 电脑关机时 GitHub 的 gitee-sync.yml 仍作备用（任务内重试最长 2 小时，见 .github/scripts/gitee-sync.sh）。

## 8. 邮件提醒（11-02 用户确认预览后上线）
- 入口「我的 › 邮件提醒」（ui/MailScreen.kt）。Notifier.alert 统一分发：写记录 → 手机通知（可关）→ 邮件（类别 gold / trials / system，按收件人勾选）。
- 授权码存在 SharedPreferences "mail_secret"，不在 Store.allNames()，所以不进备份；其他邮件设置存 global（mail_*），可备份。
- 每个收件人单独发一封；校验 smtp.qq.com 证书主机名。授权码由用户自己在 QQ 邮箱生成并填入。

## 9. 临床试验翻译（11-02）
- 百度翻译开放平台「通用文本翻译」，用户已个人认证并切到**高级版**（官方定价页 11-02：高级版每月 100 万字符免费，超出 49 元/百万字符，用量统计有延迟、超出会欠费）。
- 用户自己在 App「临床试验 › 翻译设置」填 APP ID + 密钥（存 "trials_secret"，不进备份）。App 统计本月字符数，默认上限 80 万，到上限只显示英文。
- 标题在检查/保存关注后批量翻译并缓存（trans_cache.json）；详情页打开时翻正式标题、主要终点、前 3 个适应症。
- 没用「生物医药领域」翻译：它的免费额度当时没在官方页核实。

## 10. 待定事项
- 语言目前只有简体中文。
- （已完成 11-02）临床试验中文翻译：见下方「临床试验翻译」。
- 真机上的显示效果、后台保活时长、手势体验都还没有用户反馈。

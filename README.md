# Eric的百宝箱

个人 Android 工具箱 App。底座 + 模块：每个功能是一个独立模块，加新功能不用重做整个软件。

## 下载安装
到 [Releases](../../releases) 下载最新的 `EricToolbox-vX.Y.Z.apk`，在手机上打开安装。
以后在 App 里「百宝箱设置 → 检查更新」即可升级，设置和记录会保留。

## 现有模块
| 模块 | 类型 | 说明 |
| --- | --- | --- |
| 实时金价 | 监控 | 大盘（上海金 Au99.99）/ 工行积存金 / 如意金积存；按大盘价阶梯提醒（850、840…）、上涨提醒、开盘提醒；走势图；积存金与如意金持仓交易明细 |

项目背景、已定规则与接手说明见 [CLAUDE.md](CLAUDE.md)。

## 结构
```
app/src/main/java/com/eric/toolbox/
├── core/          底座：模块接口、登记表、后台服务、看门狗、通知、存储、备份、更新
├── ui/            主页与全局页面（主页、保活引导、提醒记录、整理主页、设置）
└── modules/
    └── gold/      实时金价模块（行情源、档位规则、界面）
```

## 如何新增一个模块
1. 在 `modules/` 下新建一个包，实现 `core/ToolModule.kt` 里的 `ToolModule` 接口
   - 纯工具：实现 `Screen()` 即可
   - 需要后台监控：再提供一个 `BackgroundTask`，由底座统一调度、保活、发通知
2. 在 `core/ModuleRegistry.kt` 的 `all` 列表里加一行
3. 推送到 `main`，GitHub Actions 自动跑测试、编译、发布新版本

## 编译与签名
- 每次推送到 `main` 由 GitHub Actions 编译，版本号自动递增（`1.0.<运行序号>`）
- 正式版需要仓库 Secrets：`KEYSTORE_BASE64`（签名密钥 base64）、`KEYSTORE_PASSWORD`
- 未配置密钥时只生成 `-test` 测试包，不发布版本
- **签名密钥请单独备份**：丢失后新版本无法覆盖安装，只能卸载重装（可先用「导出备份」保存设置）

## 行情数据说明
数据来自东方财富、新浪财经网站使用的公开接口，并非官方开放 API，仅供个人参考；接口改版后需更新 `modules/gold/GoldSource.kt`。

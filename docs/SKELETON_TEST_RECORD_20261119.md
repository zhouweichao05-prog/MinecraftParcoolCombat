# SKELETON_TEST_RECORD — 骨架阶段客户端测试记录

- 日期：2026-11-19
- 测试人：用户（客户端实际操作）
- 构建：ParkourCombat 2.0.0-alpha.1 骨架（commit `6e36a6c` 之前状态；无阶段 02 代码）
- 测试日志：[runClient-manual-20261119-1432.log](logs/runClient-manual-20261119-1432.log)
- 环境：Minecraft 1.20.1 / Forge 47.4.0 / Java 17.0.12 / Gradle 8.8
- 加载：`parkourcombat`、`parcool 3.4.3.3`、`bettercombat 1.9.0+1.20.1`、`playeranimator 1.0.2-rc1+1.20`、`cloth_config 11.1.136`

## 1. 测试会话事实

| 时间 | 事件 |
|---|---|
| 14:32:15 | 客户端启动（`ModLauncher running`，Java 17.0.12） |
| 14:32:26 | `Setting user: Dev` |
| 14:32:31 | `Sound engine started` |
| 14:32:58 | 集成服务器启动；14:33:02 `Dev` 登录世界 |
| 14:33:18 | 第一次世界退出并保存 |
| 14:33:22 | 第二次集成服务器启动；14:33:24 `Dev` 登录 |
| 14:34:15 | 第二次世界退出并保存 |
| 14:34:17 | `Stopping!`，客户端正常退出 |

- 无 `FATAL`、无崩溃报告、无 `Mixin apply failed`。
- 仅 4 条 ERROR，均为缺少 Patchouli 导致的 `parcool:grant_parcool_guide` 战利品表与 `parcool:parcool_guide` 配方解析失败（每次加载世界 2 条）。
- ParCool 客户端/服务端 limitation 同步成功；Better Combat `WeaponRegistry` 编码/解码成功。

## 2. 测试结论

| 项目 | 状态 | 依据 |
|---|---|---|
| Forge 启动 | **PASS** | 日志完整启动，Java 17 / Forge 47.4.0 |
| ParkourCombat 加载 | **PASS** | FML 构造 `com.MrGhost.parkourCombat.ParkourCombat`，无异常 |
| ParCool 加载与同步 | **PASS** | `parcool 3.4.3.3`；`Sent/Received Client Information`、`Server Limitation` |
| Better Combat 加载 | **PASS** | `bettercombat 1.9.0+1.20.1`；`WeaponRegistry` 编解码 |
| 进入单人世界 | **PASS** | 两次集成服务器启动与玩家登录 |
| 正常保存和退出 | **PASS** | `Saving chunks`、`Stopping!` |
| 跑酷动作识别 | **NOT TESTED** | 阶段 02 Adapter 未实现；日志无 `[ParkourAdapter]` |
| FALL 状态识别 | **NOT TESTED** | 阶段 02 未实现 |
| 动作中断识别 | **NOT TESTED** | 阶段 02 未实现 |
| 生命周期清理 | **NOT TESTED** | 阶段 02 未实现 |
| 多人服务器验证 | **NOT TESTED** | 本次为单人客户端 |

## 3. 本次日志的正确判定

- 该日志证明：**骨架能启动、加载、进入世界、保存并退出；ParCool/BC 在客户端共存正常**。
- 用户在游戏内报告“各项动作正常”，指的是 **ParCool 3.4.3.3 原生动作**，不是 ParkourCombat 的动作识别。
- 日志中 `[ParkourAdapter]` 出现次数为 0，因此不能把本日志当作阶段 02 的验收证据。
- Patchouli 缺失错误属于已知可选依赖缺失，不影响核心跑酷动作；禁止通过修改 ParCool JAR 或更换版本解决。

## 4. 阶段状态

- 骨架阶段：**PASS**（按上表）。
- 阶段 02 ParCoolAdapter：**NOT IMPLEMENTED / NOT TESTED**。
- 多人服务器：**NOT TESTED**。

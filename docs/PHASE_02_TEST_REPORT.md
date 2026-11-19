# PHASE_02_TEST_REPORT — ParCoolAdapter 实现与测试记录

- 阶段：02
- 日期：2026-11-19
- 代码提交：`c1c206b`（首轮实现 `18ea034` + 集成服务端 adapter 修复）
- 环境：Minecraft 1.20.1 / Forge 47.4.0 / Java 17 / Gradle 8.8 / ParCool 3.4.3.3 / Better Combat 1.9.0+1.20.1 / Player Animator 1.0.2-rc1 / Cloth Config 11.1.136
- 关联文档：
  - [PHASE_02_IMPLEMENTATION_PLAN.md](PHASE_02_IMPLEMENTATION_PLAN.md)
  - [PHASE_02_API_EVIDENCE.md](PHASE_02_API_EVIDENCE.md)
  - [PHASE_02_TEST_PLAN.md](PHASE_02_TEST_PLAN.md)
  - [PHASE02_CLIENT_CHECKLIST.md](PHASE02_CLIENT_CHECKLIST.md)

## 1. 结论摘要

| 项 | 结果 |
|---|---|
| 代码实现 | **IMPLEMENTED** |
| 编译 | **COMPILED** |
| 单元测试 | **TESTED（21/21 PASS）** |
| 专用服务器加载/初始化 | **SERVER-TESTED（仅启动级）** |
| 客户端启动/加载 | **PASS（启动级）** |
| 真实跑酷动作识别 | **PARTIALLY TESTED**（FAST_RUN/SLIDE/VAULT/WALL_RUN_HORIZONTAL；垂直墙跑/墙跳未测） |
| FALL 实机识别 | **PARTIALLY TESTED**（21 次，仅 LANDED；其他原因未测） |
| 取消/中断实机 | **NOT TESTED** |
| 退出清理实机 | **TESTED**（CLIENT_LOGOUT） |
| 死亡/维度清理实机 | **NOT TESTED** |
| 集成服务端 `PARCOOL_SYNCED` | **NOT TESTED**（当前双 adapter 构建待复测） |
| 多人 | **NOT TESTED** |
| 整体 VERIFIED | **NO** |

> 编译通过、单元测试通过、客户端/服务器启动通过，都不能替代真实跑酷动作识别验证。

## 2. 实际修改/新增文件

### 2.1 源码（新增 21 个 + 修改 1 个）

`src/main/java/com/MrGhost/parkourCombat/`

| 文件 | 职责 |
|---|---|
| `ParkourCombat.java`（修改） | 构造时创建 side-aware adapter，注册 `ParkourAdapterEvents`；无客户端类直接引用 |
| `adapter/ParkourActionType.java` | 7 个类型；`WALL_RUN` 为聚合类型 |
| `adapter/ParkourActionPhase.java` | STARTED/ACTIVE/FINISHED/CANCELLED/INTERRUPTED/UNKNOWN |
| `adapter/ParkourStateSource.java` | SERVER_NATIVE/PARCOOL_SYNCED/CLIENT_LOCAL/VANILLA_DERIVED |
| `adapter/ParkourTrust.java` | HIGH/MEDIUM/LOW/UNKNOWN |
| `adapter/ParkourActionState.java` | 状态接口 |
| `adapter/ImmutableParkourActionState.java` | 不可变状态实现 |
| `adapter/ParkourPosition.java` | 纯坐标 record（避免测试依赖 Minecraft Vec3） |
| `adapter/WallRunSnapshot.java` | 墙跑共享数据；`directionAvailable=false` |
| `adapter/FallPhase.java` | NOT_FALLING/FALLING/FALL_END |
| `adapter/FallEndReason.java` | LANDED/WATER/LAVA/FLYING/CLIMBING/VEHICLE/TELEPORT/DEATH/LOGOUT/DIMENSION_CHANGE/UNLOAD/UNKNOWN |
| `adapter/FallSnapshot.java` | FALL 数据快照 |
| `adapter/FallMotionSample.java` | 纯运动采样 |
| `adapter/FallStateMachine.java` | 纯 FALL 状态机 |
| `adapter/MotionTracker.java` | Player → `FallMotionSample`，驱动 FALL |
| `adapter/ParkourActionTracker.java` | 纯动作状态机（开始/持续/结束/取消/中断/超时） |
| `adapter/ParkourActionAdapter.java` | 查询接口 |
| `adapter/ParCoolActionMapping.java` | ParCool 类映射 + 版本守卫（3.4.3.3） |
| `adapter/ParCoolActionAdapter.java` | 公共门面：玩家 tracker、事件、清理、日志 |
| `adapter/ParkourAdapterEvents.java` | 公共 Forge 事件桥 |
| `adapter/server/ServerParCoolAdapter.java` | 服务端 adapter（`super(false)`） |
| `adapter/client/ClientParCoolAdapter.java` | 客户端专属事件订阅：LoggingOut/Clone 清理（由 Forge 仅在客户端发现） |

### 2.2 测试（新增 3 个）

- `ParkourActionTrackerTest`（11）
- `FallStateMachineTest`（8）
- `StateCleanupTest`（2）

### 2.3 构建与文档

- `build.gradle`：新增 JUnit 5 `testImplementation`/`testRuntimeOnly` + `useJUnitPlatform()`（仅测试，不进入运行时）。
- `docs/PHASE_02_TEST_REPORT.md`（本文件）。
- `docs/logs/`：编译/测试/构建/客户端/服务端日志。

### 2.4 未修改

- `libs/*.jar`（4 个锁定 JAR 未修改）
- `build.gradle` 中的游戏/Mod 版本锁定
- `gradle.properties` 版本参数
- `mods.toml` 依赖
- 无 Mixin、无新网络包、无资源文件、无战斗/词条/动画代码

## 3. ParCool API 实际调用证据

| 调用 | 用途 | 侧 |
|---|---|---|
| `Parkourability.get(Player)` | 获取能力对象，可能为 null | 双端 |
| `parkourability.get(FastRun.class).isDoing()` 等 6 个类 | 动作活跃识别 | 双端 |
| `ParCoolActionEvent.Start.Pre` | 动作开始边沿 | 双端 |
| `ParCoolActionEvent.Finish.Pre` | 动作结束边沿 | 双端 |
| `ParCoolActionEvent.TryToStart`（LOWEST priority，检查 `isCanceled()`） | 客户端取消识别 | 仅客户端 |
| `ParCoolActionEvent.TryToContinue`（同上） | 客户端继续取消 | 仅客户端 |
| `ModList.get().getModContainerById("parcool")` | 运行时版本守卫 3.4.3.3 | 双端 |
| `Player.onGround/fallDistance/getDeltaMovement/isInWater/isInLava/isFallFlying/onClimbable/getVehicle/isDeadOrDying/position` | FALL 派生 | 双端 |

- 未使用 `Animation`、`Animator`、`PlayerModelTransformer`、`ParCoolAnimationInfoEvent`。
- 未使用 `Vault.getCurrentAnimation()`、`Slide.getSlidingVector()` 作为服务端数据。
- 未反射或 Mixin 墙跑 private 字段。
- 详细签名见 [PHASE_02_API_EVIDENCE.md](PHASE_02_API_EVIDENCE.md)。

## 4. FALL 状态机

```text
NOT_FALLING → FALLING → FALL_END（40 tick TTL）→ NOT_FALLING
```

进入 FALLING（全部满足）：

- `!onGround`
- `deltaMovement.y < 0`（排除跳跃上升）
- `fallDistance > 0.5`
- 不在水/岩浆
- 非鞘翅飞行
- 非载具
- 非攀爬

记录：起始位置、当前下落距离、垂直速度、持续 tick、是否接地、结束原因。

结束原因：`LANDED / WATER / LAVA / FLYING / CLIMBING / VEHICLE / TELEPORT / DEATH / LOGOUT / DIMENSION_CHANGE / UNLOAD / UNKNOWN`。

- 服务端 FALL：`source=VANILLA_DERIVED`、`trust=HIGH`、`serverAuthoritative=true`（仅物理相位）。
- 客户端 FALL：`source=VANILLA_DERIVED`、`trust=MEDIUM`、`serverAuthoritative=false`。
- 单元测试覆盖上升排除、落地、水/岩浆/飞行/攀爬/载具、传送/维度/死亡、TTL、二次下落。

## 5. 客户端/服务端状态可信度

| 状态 | source | trust | serverAuthoritative |
|---|---|---|---|
| 服务端 ParCool 动作 | `PARCOOL_SYNCED` | LOW | false |
| 客户端本地 ParCool 动作 | `CLIENT_LOCAL` | MEDIUM | false |
| 服务端 FALL | `VANILLA_DERIVED` | HIGH（物理相位） | true |
| 客户端 FALL | `VANILLA_DERIVED` | MEDIUM | false |
| 服务端原生预留 | `SERVER_NATIVE` | HIGH | true |

- 服务端不把 ParCool 同步状态当作战斗权威；没有动画推断。
- `TryToStart/TryToContinue` 只在客户端；服务端无法精确识别取消。
- 服务端 2 tick 超时只产生 `inferred=true` 的 `INTERRUPTED`/`UNKNOWN`，不产生高可信度正常结束。

## 6. 测试结果

### 6.1 单元测试

命令：`gradlew test`

| 测试类 | 数量 | 失败 |
|---|---:|---:|
| `ParkourActionTrackerTest` | 11 | 0 |
| `FallStateMachineTest` | 8 | 0 |
| `StateCleanupTest` | 2 | 0 |
| **合计** | **21** | **0** |

覆盖：

- 开始→ACTIVE→结束；取消尝试；TryToContinue 取消；替换中断；服务端超时→INTERRUPTED/UNKNOWN；孤儿 Finish；迟到 Finish 不覆盖超时结论；墙跑聚合/双墙跑；终态 40 tick TTL；清理。
- FALL 进入/上升排除/落地/水/岩浆/飞行/攀爬/载具/传送/维度/死亡/TTL/二次下落/清理。
- 状态机清理（不依赖 Minecraft 运行时）。

### 6.2 编译与构建

```powershell
gradlew --no-daemon compileJava
gradlew --no-daemon build
```

- `compileJava`：**PASS**
- `build`：**PASS**（17s）
- 产物：`build/libs/parkourcombat-2.0.0-alpha.1.jar`（40,136 B）
- `jarJar` 仍 SKIPPED；未打包第三方类。
- 日志：[compileJava-phase02.log](logs/compileJava-phase02.log)、[build-phase02.log](logs/build-phase02.log)

### 6.3 runServer

命令：`gradlew --no-daemon runServer`

- 结果：`Done (7.442s)!`
- 日志：`[ParkourAdapter] initialized side=SERVER parCoolVersion=3.4.3.3`
- 无 adapter 异常；无 `ClassNotFoundException`/`NoSuchMethodError`。
- 已知非致命：Patchouli 缺失导致 2 条 ParCool 指南书 ERROR（战利品表/配方），与前置核查一致。
- 服务端在 `Done` 后由测试脚本强制结束，未验证控制台 `stop` 与玩家联机。
- 日志：[runServer-phase02-dual.latest.log](logs/runServer-phase02-dual.latest.log)

### 6.4 runClient 启动（双 adapter 构建）

命令：`gradlew --no-daemon runClient`

- 结果：启动成功；`Setting user: Dev`、`Sound engine started`。
- 日志同时出现：
  - `[ParkourAdapter] initialized side=SERVER parCoolVersion=3.4.3.3`
  - `[ParkourAdapter] initialized side=CLIENT parCoolVersion=3.4.3.3`
- 无 `FATAL`、无 `RuntimeDistCleaner`、无 Mixin 应用失败、无 adapter 异常。
- 自动化脚本在启动完成后终止，未进行动作操作。
- 日志：[runClient-phase02-dual.latest.log](logs/runClient-phase02-dual.latest.log)

### 6.5 首轮真实客户端动作测试（2026-11-19 15:03–15:05）

- 测试构建：`18ea034`（首轮为单侧 adapter；客户端路径与当前 `c1c206b` 相同）。
- 实际进入世界约 65 秒，期间执行了跑酷动作；`latest.log` 只有 INFO，动作相位日志在 `debug.log`。
- `debug.log` 共 139 行 `[ParkourAdapter]` 记录：

| 类型 | 次数 | 结果 |
|---|---:|---|
| FAST_RUN | 24 个 STARTED/ACTIVE/FINISHED 完整循环 | PASS（客户端） |
| SLIDE | 1 个完整循环 | PASS（客户端） |
| VAULT | 2 个完整循环 | PASS（客户端） |
| WALL_RUN_HORIZONTAL | 2 个完整循环 | PASS（客户端） |
| WALL_RUN_VERTICAL | 0 | NOT TESTED |
| WALL_JUMP | 0 | NOT TESTED |
| FALL | 21 次 FALLING / 21 次 FALL_END，全部 `LANDED` | PASS（仅基础落地） |
| CANCELLED / INTERRUPTED | 0 | NOT TESTED |

- 清理：`[ParkourAdapter] cleanup all side=CLIENT players=1 reason=CLIENT_LOGOUT`，退出清理 PASS。
- 无 `FATAL`；仅 Patchouli 指南书错误。
- 该轮没有 `PARCOOL_SYNCED` 服务端动作日志，因为当时构建只有单侧 adapter；当前 `c1c206b` 已加入集成服务端 adapter，需要复测。
- 日志证据：[runClient-phase02-adapter-transitions.log](logs/runClient-phase02-adapter-transitions.log)

## 7. 状态记录

| 功能 | 状态 |
|---|---|
| 类型/接口/快照 | **IMPLEMENTED** |
| ParCoolActionMapping + 版本守卫 | **IMPLEMENTED / COMPILED** |
| ParkourActionTracker | **IMPLEMENTED / COMPILED / TESTED（单元）** |
| FallStateMachine | **IMPLEMENTED / COMPILED / TESTED（单元）** |
| MotionTracker | **IMPLEMENTED / COMPILED**；FALL 基础落地 **TESTED（客户端）**；其他结束原因 NOT TESTED |
| ParCoolActionAdapter / 事件 / 生命周期 | **IMPLEMENTED / COMPILED**；客户端动作 **PARTIALLY TESTED**；退出清理 **TESTED**；集成服务端动作 **NOT TESTED**；专用服务器初始化 **SERVER-TESTED（启动级）** |
| 真实动作识别（FAST_RUN/SLIDE/VAULT/WALL_RUN_HORIZONTAL） | **TESTED（客户端，首轮 18ea034）** |
| 真实动作识别（WALL_RUN_VERTICAL/WALL_JUMP） | **NOT TESTED** |
| FALL 实机（LANDED） | **TESTED（客户端，首轮 18ea034）** |
| FALL 实机（水/岩浆/鞘翅/攀爬/载具/传送/维度/死亡） | **NOT TESTED** |
| 取消/中断实机 | **NOT TESTED** |
| 退出清理（CLIENT_LOGOUT） | **TESTED** |
| 死亡/维度清理实机 | **NOT TESTED** |
| 集成服务端 `PARCOOL_SYNCED` | **NOT TESTED**（当前 c1c206b 需复测） |
| 多人 | **NOT TESTED** |
| 整体功能 | **未 VERIFIED** |

## 8. 未完成 / 无法验证

1. `WALL_RUN_VERTICAL`、`WALL_JUMP` 真实识别。
2. FALL 在水/岩浆/鞘翅/攀爬/载具/传送/维度/死亡中的结束原因。
3. 客户端 `TryToStart/TryToContinue` 被取消的实机行为。
4. 服务端超时启发式的实际误判率。
5. 死亡、维度切换清理的实机验证。
6. 多人服务器（双客户端）隔离与同步。
7. 当前双 adapter 构建下集成服务端的 `PARCOOL_SYNCED` 动作日志。
8. 服务端 FALL 与客户端 FALL 的 `fallDistance` 一致性。
9. 墙面方向（ParCool 3.4.3.3 无公开 getter，明确不提供）。

## 9. 用户人工测试步骤

1. 启动开发客户端：

```powershell
Set-Location 'D:\MinecraftParcoolCombat'
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat --no-daemon runClient
```

2. 进入单人世界（超平坦、和平、生存）。
3. 按 [PHASE02_CLIENT_CHECKLIST.md](PHASE02_CLIENT_CHECKLIST.md) 执行 TC-01～TC-19。
4. 在 `run/logs/latest.log` 过滤 `[ParkourAdapter]`：
   - 客户端动作：`source=CLIENT_LOCAL`、`trust=MEDIUM`。
   - 服务端 ParCool 动作：`source=PARCOOL_SYNCED`、`trust=LOW`、`serverAuthoritative=false`。
   - 服务端 FALL：`source=VANILLA_DERIVED`、`trust=HIGH`、`serverAuthoritative=true`。
5. 重点检查：
   - 跳跃上升阶段不出现 `type=FALL phase=STARTED`。
   - FALL 落地 `endReason=LANDED`，记录起始位置/距离/垂直速度/持续时间。
   - 水平/垂直墙跑不混淆；两者同时 active 时分别记录。
   - 死亡/切维度/退出后无残留 active。
6. 将日志、截图、每个用例结果填入测试记录并反馈。

日志为 DEBUG 级别（开发运行配置已设置 `forge.logging.console.level=debug`）；生产默认 INFO 不会输出每动作生命周期。

## 10. 已知问题与实现调整

1. **RuntimeDistCleaner 问题（已解决）**：最初使用 `DistExecutor.unsafeRunForDist` + `new ClientParCoolAdapter()` 时，专用服务器在构造阶段尝试加载客户端类并崩溃。修复方式：
   - 公共构造器按 `FMLEnvironment.dist` 创建 side-aware adapter；
   - 客户端清理改为 `ClientParCoolAdapter` 作为 `@EventBusSubscriber(value=CLIENT)` 的独立事件类，公共代码不再直接引用客户端类；
   - 最终 `runServer` 成功。
2. **集成服务端 adapter 缺失（已修复，`c1c206b`）**：首轮单侧 adapter 在单人游戏中只创建客户端实例，导致集成服务端没有 `PARCOOL_SYNCED` 状态。现在始终创建 `ServerParCoolAdapter`，客户端构建另建客户端 adapter，事件按 `player.level().isClientSide` 路由；已通过双端启动验证。
3. 服务端无法精确区分自然结束/取消/中断；只能 FINISHED + 证据化 INTERRUPTED/UNKNOWN。
4. 墙面方向不可得；`directionAvailable=false`，不伪造。
5. Patchouli 缺失错误仅指南书数据，未修改 ParCool 解决。
6. 首轮真人测试（15:03–15:05，构建 `18ea034`）覆盖 FAST_RUN/SLIDE/VAULT/WALL_RUN_HORIZONTAL/FALL-LANDED/CLIENT_LOGOUT；当前 `c1c206b` 双 adapter 构建尚未重新执行真人动作，因此集成服务端 `PARCOOL_SYNCED` 仍为 NOT TESTED。

## 11. 结论

- ParCoolAdapter 已实现并编译通过；21 个单元测试通过；服务端与客户端均能加载 adapter。
- 首轮真人客户端日志已确认 FAST_RUN、SLIDE、VAULT、WALL_RUN_HORIZONTAL、FALL-LANDED 和 CLIENT_LOGOUT 清理；垂直墙跑、墙跳、取消/中断、FALL 其他结束原因、集成服务端 `PARCOOL_SYNCED`、死亡/维度清理、多人仍未验证。
- 当前双 adapter 构建（`c1c206b`）已通过编译、单元测试、专用服务器启动和客户端双端初始化；需按清单补测未覆盖项。
- 整体仍不能标记 VERIFIED；不得进入阶段 03。

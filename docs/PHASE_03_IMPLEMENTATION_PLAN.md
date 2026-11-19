# PHASE_03_IMPLEMENTATION_PLAN — MotionTracker 与玩家运动数据系统

- 阶段：03
- 文档状态：**IMPLEMENTED（见 PHASE_03_IMPLEMENTATION_REPORT；本文件保留设计）**
- 前置阶段：02，状态 `CONDITIONALLY_ACCEPTED`，HEAD `3389c13`
- 目标：建立每玩家、每逻辑端每 tick 最多一次的低开销运动采样，输出不可变 `MotionSnapshot`，并提供有界历史缓存。
- 本阶段不实现：Better Combat 桥接、特殊攻击、伤害、霸体、词条、装备、新动画、大规模网络同步、Mixin。

> 本文是实施计划，不是实现记录。用户确认前不得开始编码。

## 1. 现有代码审计

### 1.1 已存在

| 结构 | 当前内容 | 是否满足阶段 03 |
|---|---|---|
| `MotionTracker` | 只读取 `position`、`fallDistance`、`deltaMovement.y` 和环境标志，驱动 `FallStateMachine` | 否：无水平速度、加速度、方向、历史 |
| `FallSnapshot` | FALL 相位、起点、下落距离、垂直速度、持续 tick、结束原因、source/trust | 部分：只覆盖 FALL，不是通用运动快照 |
| `ParkourActionState` | 动作类型/相位/ticks/source/trust/inferred/evidence | 是：直接复用，不重新识别 |
| `ParkourPosition` | 纯坐标 record | 是：可复用，避免快照依赖 Minecraft `Vec3` |
| `ParCoolActionAdapter` | 每 tick 调 `actions.onTick` 后调 `motion.tick`，已有玩家级 map 和生命周期清理 | 是：天然的单次采样入口 |
| `ParkourActionAdapter` | 查询动作/FALL/墙跑，无运动查询 | 否：需增加运动快照/历史查询 |

### 1.2 关键结论

- 不需要新建第二套 ParCool 动作识别；阶段 02 的 `ParkourActionTracker` 保持不变。
- 不需要新的网络包；服务端和客户端各自从本地玩家实体采样。
- 应扩展现有 `MotionTracker`，而不是在事件层再增加一次采样。
- `FallStateMachine`、`FallSnapshot`、`FallEndReason` 本阶段不改变语义。

## 2. 数据模型

### 2.1 `MotionFrame`（纯输入，新增）

用于把 Minecraft 实体读取与计算分离，便于无游戏运行时单元测试。

```text
MotionFrame
- long gameTime
- ParkourPosition position
- double velocityX, velocityY, velocityZ
- boolean onGround
- boolean inWater
- boolean inLava
- boolean fallFlying
- boolean climbing
- boolean inVehicle
- boolean dead
- boolean dimensionChanged
- boolean teleported
```

- 由 `MotionTracker` 每 tick 从 `Player` 读取一次。
- 同一份数据同时用于 FALL 与运动快照，避免重复读取实体。
- 不包含世界实体列表、方块查询或网络数据。

### 2.2 `MotionSnapshot`（不可变输出，新增）

```text
MotionSnapshot
- long sampleTick                 // 采样时的 level.getGameTime()
- ParkourPosition position        // 当前绝对位置
- double velocityX, velocityY, velocityZ
- double horizontalSpeed          // sqrt(vx^2 + vz^2)
- double directionX, directionZ   // 水平单位方向；无效时为 0,0
- double horizontalAcceleration   // 水平速度变化量的大小 / tick
- double verticalAcceleration     // vy - previousVy，带符号
- double displacementX/Y/Z        // 相对上一个有效采样的位移
- int continuousMotionTicks       // 连续运动 tick
- boolean onGround
- FallSnapshot fallSnapshot       // 保留 FALL 自己的 source/trust
- ParkourActionState actionState  // 主活动动作；无则为 null，保留自身 source/trust
- ParkourStateSource source       // 仅运动学
- ParkourTrust trust              // 仅运动学
```

设计说明：

- **“当前位移”**：用 `position` 表示当前绝对位置；`displacementX/Y/Z` 表示相对上一有效采样的每 tick 位移。二者语义分开，避免把“位置”和“速度”混为一谈。
- **“三维速度”**：使用三个速度分量；`horizontalSpeed` 是派生值，不重复存储三维速度向量对象。
- **水平加速度**：`sqrt(dvx²+dvz²)/deltaTicks`；`deltaTicks=1` 时即每 tick 速度变化。
- **垂直加速度**：带符号，便于识别起跳/下落/落地。
- **运动方向**：水平单位向量；速度为 0 或低于阈值时不伪造方向，使用上一稳定方向做短暂保持。
- **主活动动作**：只允许一个 `ParkourActionState`。优先级：
  1. `WALL_RUN` 聚合状态（H/V 任一活跃）
  2. `WALL_JUMP`
  3. `VAULT`
  4. `SLIDE`
  5. `FAST_RUN`
  6. 无 → `null`
- 不包含 `fallDistance`、`ParkourActionType` 集合、世界坐标外推等冗余字段；需要时通过 `FallSnapshot` 或 `ParkourActionTracker` 查询。
- 使用 nullable `actionState` 而不是每 tick 创建 `Optional`，降低热路径分配。
- `MotionSnapshot` 不引用 Minecraft 类，便于单元测试。

### 2.3 `MotionHistory`（有界环形缓存，新增）

```text
MotionHistory
- 固定容量 20（1 秒，20 tick）
- add(snapshot)
- latest()
- get(int age)      // 0=最新，19=最旧
- size()
- clear()
```

- 使用固定数组 + 写指针，插入不创建新集合。
- 只保存有效快照；传送、维度、死亡、采样间隔异常时清空。
- 查询 `recent(max)` 时才创建只读列表，每 tick 不创建。
- 容量常量 `HISTORY_TICKS = 20`，后续战斗需要更长窗口时再评估。

### 2.4 `MotionAccumulator`（纯计算，新增）

职责：把连续 `MotionFrame` 转换为 `MotionSnapshot`，维护方向、加速度、连续运动和历史。

状态：

```text
MotionFrame previous
ParkourPosition lastDirection
int directionHoldTicks
int continuousMotionTicks
MotionHistory history
```

重置条件（任一满足）：

- 首帧（无 previous）：建立基线，返回零加速度快照。
- `dead`、`dimensionChanged`。
- 位置单 tick 位移平方 > `8^2`（传送/异常位移）。
- `gameTime` 小于 previous（回退）或间隔 >5 tick（大间隙）。
- 同一 gameTick 的重复帧：忽略，不清历史；不产生新快照。
- `teleported` 标志为 true。

重置行为：

- 清空 previous、方向保持、连续运动和历史。
- 当前帧只建立新基线；**不返回快照**，避免把传送后的巨大位移/加速度写入历史。
- 下一 tick 重新产生第一帧快照（零加速度、零位移）。

方向规则：

- 水平速度 `>= 0.05`：更新方向为单位向量，`directionHoldTicks = 5`。
- 低于阈值且 `directionHoldTicks > 0`：保留上一方向并递减。
- 否则方向为 `0,0`。
- 目的：短暂静止/低速转向不产生方向跳变，同时长时间静止不会永久保留旧方向。

连续运动规则：

- `horizontalSpeed >= 0.05` 或 `abs(velocityY) >= 0.08`：`continuousMotionTicks++`。
- 否则归零。
- 传送/维度/死亡/丢 tick 归零。
- 静止时速度接近零，连续运动为 0。

加速度规则：

- `horizontalAcceleration = sqrt((vx-prevVx)^2 + (vz-prevVz)^2)`。
- `verticalAcceleration = vy - prevVy`。
- 首帧/重置帧为 0。
- 不人为裁剪落地或撞墙产生的真实加速度；只消除采样间隔/传送/重建导致的虚假尖峰。

## 3. 采样管线

### 3.1 单次采样点

`PlayerTickEvent.Phase.END` 已经由 `ParkourAdapterEvents.onPlayerTick` 统一分发到逻辑端 adapter。阶段 03 复用该入口：

```text
onPlayerTick(player)
  -> actions.onTick(gameTime, doing)          // 阶段 02，不变
  -> primaryAction = selectPrimary(actions)    // 只读
  -> motion.tick(player, gameTime, primaryAction, motionSource, motionTrust)
       -> 读取一次 Player 运动数据
       -> 更新 FallStateMachine
       -> MotionAccumulator.accept(...)
       -> 更新 MotionHistory
  -> FALL 相位日志（阶段 02，不变）
```

- 每个玩家、每逻辑端、每 tick 最多一次 `motion.tick`。
- 单人会同时存在客户端与服务端两个逻辑端实例；这是两个独立世界，不是同一玩家重复采样。
- 不在网络包、渲染事件、攻击事件中额外采样。
- `getMotionSnapshot`/`getMotionHistory` 只读，不触发采样。

### 3.2 主活动动作选择

新增纯工具 `ParkourActionSelection`：

- 输入 `ParkourActionTracker`。
- 使用 `getActiveActions()`/`isActive()`，不把 TTL 终态当成当前动作。
- 按固定优先级返回一个 `ParkourActionState` 或 null。
- 不修改阶段 02 的 `ParkourActionTracker` 分类、生命周期或 trust。

## 4. 数据来源与权威性

| 数据 | 服务端 | 客户端 |
|---|---|---|
| 运动快照 `source` | `SERVER_NATIVE` | `CLIENT_LOCAL` |
| 运动快照 `trust` | `HIGH` | `MEDIUM` |
| 动作状态 | 阶段 02 `PARCOOL_SYNCED`/`LOW` | `CLIENT_LOCAL`/`MEDIUM` |
| FALL 快照 | `VANILLA_DERIVED`/`HIGH` | `VANILLA_DERIVED`/`MEDIUM` |

- 服务端运动数据是未来战斗判定的权威来源。
- 客户端运动快照只用于表现/调试；不得上传为服务端权威。
- 本阶段不新增网络同步；服务端不会用客户端 `MotionSnapshot` 做结算。
- 快照中的 `actionState` 保留其自身 source/trust，不与运动 source 混同。

## 5. 生命周期与清理

| 事件 | 处理 |
|---|---|
| 玩家登录/首次 tick | 新建 `PlayerTrackers`，首帧建立基线 |
| 玩家退出 | adapter 移除 map entry，`MotionTracker`/历史可回收 |
| 玩家死亡 | `motion.onDeath`，随后移除 entry；历史丢弃 |
| 玩家 respawn/Clone | 原/新 UUID 都移除；新实体首帧重建 |
| 维度切换 | adapter 移除 entry；新维度首帧重建，历史不跨维度 |
| 世界卸载 | 移除该维度所有 entry |
| 服务器停止 | 清空所有 entry |
| 同维度传送 | `MotionAccumulator` 检测位置跳变，清历史，重置基线 |
| 实体重建 | 等同于新登录，首帧无加速度 |

- 不保留离线玩家历史。
- 不把旧维度速度带入新维度。
- 不在死亡实体上继续采样。

## 6. 性能约束

- 每玩家每逻辑端每 tick 一次实体读取、一次 `MotionSnapshot` 创建、一次历史写入。
- 不扫描世界实体；只做 `Map<UUID, PlayerTrackers>` 查找。
- `MotionHistory` 固定 20，容量有界；无无限集合。
- 插入历史不创建 `List`/`Stream`；查询才创建只读结果。
- 不为每次采样创建 `Vec3` 以外的额外包装对象；`MotionFrame` 和 `MotionSnapshot` 各一个。
- 不新增每 tick 日志；只在 DEBUG 且采样出现异常/重置时记录。
- 不新增网络包。

## 7. 可观测性与人工验证

当前没有 `/parkourcombat state` 命令（阶段 02 决定）。阶段 03 需要验证速度/加速度/方向，单靠单元测试无法覆盖真实客户端/服务端。

建议的默认方案：

- 不新增命令。
- 在 DEBUG 级别、每 20 tick 最多一次输出紧凑快照：
  ```text
  [MotionTracker] side=... player=... tick=... pos=... vel=... hSpeed=... dir=... hAccel=... vAccel=... movingTicks=... onGround=... falling=... action=...
  ```
- 异常（传送/维度/死亡/丢 tick 重置）用 INFO/WARN 记录一次，不每 tick 刷屏。

待确认项：是否允许增加只读调试命令 `/parkourcombat motion`（只打印当前快照，不发网络包）。默认**不添加**；若需要精确人工验证，再批准。

## 8. 与阶段 02 的边界

- 不修改 `ParkourActionTracker` 的动作识别、分类、生命周期、trust。
- 不修改 `FallStateMachine` 的 FALL 进入/退出语义。
- 不修改 `FallSnapshot` 字段语义。
- 只扩展 `MotionTracker` 和 adapter 查询接口。
- 若发现阶段 02 真实缺陷，先报告，不擅自改语义。

## 9. 风险与待确认决策

| 编号 | 问题 | 默认方案 |
|---|---|---|
| D-01 | 历史窗口长度 | 20 tick（1 秒） |
| D-02 | 方向短暂保持 | 5 tick |
| D-03 | 传送/异常位移阈值 | 单 tick 8 格 |
| D-04 | “当前位移”含义 | `position` + 每 tick `displacement` 两者都提供 |
| D-05 | 连续运动是否含垂直 | 含：水平速度 ≥0.05 或绝对垂直速度 ≥0.08 |
| D-06 | 水平加速度方向 | 只提供大小；垂直加速度带符号 |
| D-07 | 主活动动作优先级 | WALL_RUN > WALL_JUMP > VAULT > SLIDE > FAST_RUN |
| D-08 | 调试可观测性 | 默认 DEBUG 采样日志；不加命令 |
| D-09 | 服务端/客户端 FALL 计数差异 | 阶段 03 只记录，不修改阶段 02 语义 |

## 10. 实施顺序（确认后）

1. 新增纯类型：`MotionFrame`、`MotionSnapshot`、`MotionHistory`。
2. 新增 `MotionAccumulator`（速度/方向/加速度/连续运动/重置）。
3. 新增 `ParkourActionSelection`。
4. 扩展 `MotionTracker`，复用现有 FALL 采样。
5. 扩展 `ParkourActionAdapter` 与 `ParCoolActionAdapter` 查询。
6. 接入生命周期清理/重置。
7. 单元测试。
8. `compileJava`、`build`。
9. 开发客户端启动 + 专用服务器启动。
10. 更新测试报告/人工清单，提交。
11. 停止，等待验收。

## 11. 完成标准

- 单元测试覆盖静止、水平移动、跳跃、下落、落地、方向保持、加速度、传送、维度、丢 tick、历史边界。
- 编译与 build 通过。
- 客户端/专用服务器启动通过。
- 真实客户端至少验证静止/移动/跳跃/下落/落地/传送；服务端日志显示 `SERVER_NATIVE/HIGH`。
- 不新增网络包/Mixin/战斗逻辑。
- 未覆盖项必须标记 NOT TESTED，不能标 VERIFIED。

## 12. 当前状态

**DESIGNED。未写任何阶段 03 代码，等待用户确认数据模型、默认参数和可观测性方案。**

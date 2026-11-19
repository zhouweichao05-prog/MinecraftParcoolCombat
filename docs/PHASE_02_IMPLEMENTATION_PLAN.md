# PHASE_02_IMPLEMENTATION_PLAN — ParCoolAdapter 与跑酷动作状态识别

- 阶段：02
- 状态：**PLANNED（未开始编码）**
- 目标版本：ParCool 3.4.3.3（永久锁定）
- 关联文档：
  - [PARCOOL_API_AUDIT.md](PARCOOL_API_AUDIT.md)
  - [PHASE_02_API_EVIDENCE.md](PHASE_02_API_EVIDENCE.md)
  - [PHASE_02_TEST_PLAN.md](PHASE_02_TEST_PLAN.md)
  - [PHASE_02_FILE_CHANGE_PLAN.md](PHASE_02_FILE_CHANGE_PLAN.md)
  - [PHASE02_CLIENT_CHECKLIST.md](PHASE02_CLIENT_CHECKLIST.md)

---

## 1. 范围

本阶段只实现：

1. `ParCoolAdapter`。
2. ParCool 动作开始、持续、结束识别。
3. 客户端精确的取消/中断识别；服务端 `FINISHED` 与有证据的启发式中断。
4. 独立 `FALL` 状态机（原版物理派生，不是 ParCool 动作）。
5. `WALL_RUN_HORIZONTAL` / `WALL_RUN_VERTICAL` 区分，及通用 `WALL_RUN` 聚合。
6. 死亡、退出、维度切换、实体重建、世界卸载清理。
7. 状态来源、权威性与可信度标记。
8. 单元测试、日志与客户端人工验证清单。

明确不做：Better Combat 桥接、特殊攻击、伤害修改、霸体/减伤、词条、武器/宝石、战斗动画、新网络玩法、Mixin。

---

## 2. 实施顺序

1. **类型与接口**：`ParkourActionType`、`ParkourActionPhase`、`ParkourStateSource`、`ParkourTrust`、`ParkourActionState`、`FallSnapshot`、`FallPhase`、`FallEndReason`、`WallRunSnapshot`、`ParkourActionAdapter`。
2. **纯状态机**（不依赖 Minecraft 运行时）：`ParkourActionTracker`、`MotionTracker`/`FallStateMachine`。
3. **ParCool 映射与版本守卫**：`ParCoolActionMapping`，运行时校验 ParCool 版本必须为 `3.4.3.3`。
4. **事件接入**：`ParkourAdapterEvents` 订阅 ParCool 事件、玩家 tick、生命周期事件。
5. **侧别实现**：`ServerParCoolAdapter`（服务端同步状态、启发式中断、清理）与 `ClientParCoolAdapter`（本地状态、取消检测、清理）。
6. **主类注册**：`ParkourCombat` 构造器注册 adapter；客户端类通过 `DistExecutor` 隔离。
7. **日志**：相位变化 INFO 日志，字段固定（见第 9 节）。
8. **测试**：单元测试 → `compileJava`/`build` → `runServer` → 客户端人工清单。

每一步完成后更新文档与测试记录；未通过当前步骤不进入下一步。

---

## 3. 类与接口设计

### 3.1 `ParkourActionType`

```java
public enum ParkourActionType {
    FAST_RUN,
    SLIDE,
    VAULT,
    WALL_RUN,             // 聚合查询类型，不是独立 ParCool 动作
    WALL_RUN_HORIZONTAL,
    WALL_RUN_VERTICAL,
    WALL_JUMP
}
```

- `WALL_RUN`：**聚合类型**。仅当 `WALL_RUN_HORIZONTAL` 或 `WALL_RUN_VERTICAL` 活跃时，查询 `WALL_RUN` 返回聚合状态；不单独维护一个 ParCool 类。
- 两个具体墙跑类型由 ParCool 的两个不同类识别；若映射或能力不可用，输出 `UNKNOWN`，不回退猜测。
- 不包含 `FALL`：`FALL` 不是 `ParkourActionType` 的动作实例，而是独立的 `FallSnapshot` 状态；如需要枚举式查询，可提供 `ParkourMotionType.FALL`，但不得混入 ParCool 动作表。

### 3.2 `ParkourActionPhase`

```java
public enum ParkourActionPhase {
    STARTED, ACTIVE, FINISHED, CANCELLED, INTERRUPTED, UNKNOWN
}
```

### 3.3 `ParkourStateSource` 与 `ParkourTrust`

```java
public enum ParkourStateSource {
    SERVER_NATIVE,     // 服务端原生（预留：服务端独立计算的状态）
    PARCOOL_SYNCED,    // 服务端收到的 ParCool 客户端同步状态
    CLIENT_LOCAL,      // 客户端本地 ParCool 状态
    VANILLA_DERIVED    // 原版物理派生（FALL）
}

public enum ParkourTrust {
    HIGH, MEDIUM, LOW, UNKNOWN
}
```

| 状态 | source | trust | serverAuthoritative |
|---|---|---|---|
| 服务端 ParCool 动作 | `PARCOOL_SYNCED` | LOW | false |
| 客户端 ParCool 动作 | `CLIENT_LOCAL` | MEDIUM | false |
| 服务端 FALL | `VANILLA_DERIVED` | HIGH（仅物理相位） | true |
| 客户端 FALL | `VANILLA_DERIVED` | MEDIUM | false |
| 服务端原生预留 | `SERVER_NATIVE` | HIGH | true |

### 3.4 `ParkourActionState`（接口）

```java
public interface ParkourActionState {
    ParkourActionType type();
    ParkourActionPhase phase();
    boolean active();
    int ticks();
    long startedGameTime();
    long lastChangedGameTime();
    ParkourStateSource source();
    ParkourTrust trust();
    boolean serverAuthoritative();
    boolean inferred();          // 服务端启发式结果
    String evidence();           // 判定依据（动作类、替代动作、超时等）
}
```

实现为不可变 record（`ImmutableParkourActionState`），只在状态变化时创建；每 tick 不产生新对象。

### 3.5 FALL 类型

```java
public enum FallPhase { NOT_FALLING, FALLING, FALL_END }

public enum FallEndReason {
    LANDED, WATER, LAVA, FLYING, CLIMBING, VEHICLE,
    TELEPORT, DEATH, LOGOUT, DIMENSION_CHANGE, UNLOAD, UNKNOWN
}

public record FallSnapshot(
    FallPhase phase,
    Vec3 startPosition,
    double fallDistance,
    double verticalVelocity,
    int durationTicks,
    boolean onGround,
    FallEndReason endReason,
    ParkourStateSource source,
    ParkourTrust trust,
    long lastChangedGameTime
) {}
```

### 3.6 `WallRunSnapshot`

```java
public record WallRunSnapshot(
    ParkourActionType type,   // WALL_RUN_HORIZONTAL / WALL_RUN_VERTICAL
    boolean active,
    int ticks,
    boolean directionAvailable // ParCool 3.4.3.3 当前为 false
) {}
```

- 墙面方向当前不可得：`HorizontalWallRun.bodyYaw`、`runningWallDirection` 为 private；`VerticalWallRun.wallDirection` 为 private；没有公开 getter。**不反射、不 Mixin、不伪造方向**。

### 3.7 `ParkourActionAdapter`

```java
public interface ParkourActionAdapter {
    Optional<ParkourActionState> getState(Player player, ParkourActionType type);
    Set<ParkourActionType> getActiveActions(Player player);
    boolean isActive(Player player, ParkourActionType type);
    Optional<FallSnapshot> getFallState(Player player);
}
```

---

## 4. 模块职责

| 模块 | 职责 | 侧 |
|---|---|---|
| `ParkourActionMapping` | ParCool 类 → `ParkourActionType`；版本守卫；类加载失败处理 | 双端 |
| `ParkourActionTracker` | 单玩家动作状态机：开始/持续/结束/取消/中断/超时；纯逻辑 | 双端（无 MC 依赖） |
| `FallStateMachine` / `MotionTracker` | 单玩家 FALL 状态机：进入条件、数据采样、排除、结束原因、保留 | 双端（输入为快照） |
| `ParCoolActionAdapter` | 统一查询门面；持有玩家 tracker 映射；事件分发；清理 | 双端 |
| `ServerParCoolAdapter` | 服务端来源标记、同步事件、启发式中断、服务端 FALL | 仅服务端 |
| `ClientParCoolAdapter` | 客户端 `TryToStart`/`TryToContinue` 取消检测、本地 FALL、清理 | 仅客户端 |
| `ParkourAdapterEvents` | Forge 事件订阅与路由；不包含业务状态 | 双端 |
| `ParkourCombat` | 注册 adapter；客户端类隔离 | 双端入口 |

---

## 5. 数据流

### 5.1 客户端

```text
PlayerTickEvent(END)
  ├─ MotionTracker.sample(player)  → FALL 状态机（原版物理字段）
  └─ ActionTracker.reconcile(Parkourability) → 6 个 ParCool 类 isDoing 对账

ParCoolActionEvent.Start.Pre  → ActionTracker.onStart(type, source=CLIENT_LOCAL)
ParCoolActionEvent.Finish.Pre → ActionTracker.onFinish(type)
ParCoolActionEvent.TryToStart 被取消 → ActionTracker.onCancelledAttempt(type)
ParCoolActionEvent.TryToContinue 被取消 → ActionTracker.markCancelRequested(type)
```

### 5.2 服务端

```text
ParCoolActionEvent.Start.Pre  ← SyncActionStateMessage.handleServer
  → ActionTracker.onStart(type, source=PARCOOL_SYNCED, trust=LOW)
ParCoolActionEvent.Finish.Pre ← 同上
  → ActionTracker.onFinish(type) / 启发式中断判断

PlayerTickEvent(END)
  ├─ MotionTracker.sample(player) → 服务端 FALL（服务端物理字段）
  └─ ActionTracker.reconcile(Parkourability) → 超时/丢同步检测
```

- 服务端**不订阅** `TryToStart` / `TryToContinue`（它们只在客户端发送）。
- 服务端**不读取** `Animation`、`Animator`、`PlayerModelTransformer` 等客户端动画类。
- 客户端与服务端各自持有独立 tracker；不存在“客户端状态覆盖服务端状态”。

---

## 6. 状态机设计

### 6.1 ParCool 动作状态机

| 当前 | 事件/条件 | 下一状态 | 说明 |
|---|---|---|---|
| 无 | `Start.Pre` | `STARTED` | `ticks=0`；记录来源/可信度/游戏时间 |
| `STARTED` | 下一 tick 仍 `isDoing()` | `ACTIVE` | `ticks++` |
| `ACTIVE` | 仍 `isDoing()` | `ACTIVE` | 每 tick 更新 ticks |
| `ACTIVE` | `Finish.Pre` | `FINISHED` | 正常结束（服务端默认） |
| `ACTIVE` | 客户端 `TryToContinue` 被取消后 `Finish.Pre` | `CANCELLED` | 仅客户端精确 |
| `ACTIVE` | 客户端 `TryToStart` 被取消 | `CANCELLED` | 未开始的尝试，`active=false` |
| `ACTIVE` | 被另一个动作取代（同 tick/下 1 tick 内结束） | `INTERRUPTED` | 客户端精确；服务端标记 `inferred=true` |
| `ACTIVE` | 服务端超时且无 Finish | `UNKNOWN` 或 `INTERRUPTED` | 有后继动作→INTERRUPTED；无→UNKNOWN |
| 无 | `Finish.Pre`（无 Start） | `UNKNOWN` | `ORPHAN_FINISH`，记录证据 |

- `Start.Pre` 在 `action.start(...)` **之前**发送；事件回调中 `isDoing()` 仍为 false。禁止在 Start.Pre 里用 `isDoing()` 判断是否成功。
- `Finish.Pre` 在 `action.finish(...)` **之前**发送；回调中 `isDoing()` 仍为 true。
- 事件是边沿通知；`PlayerTickEvent` 的 `isDoing()` 轮询用于对账和超时。

### 6.2 服务端启发式中断（必须记录依据）

1. A active；收到 B 的 `Start.Pre`，A 在 **1 tick 内**收到 `Finish.Pre` → A `INTERRUPTED`，`evidence=REPLACED_BY(<B>)`，`inferred=true`。
2. A active；下一 tick `isDoing(A)==false` 且 B active，但未收到 A 的 Finish：
   - 先等待 **2 tick** 对账窗口；
   - 仍缺失 → A `INTERRUPTED`，`evidence=SYNC_LOST_REPLACED_BY(<B>)`，`inferred=true`。
3. A active；`isDoing(A)==false` 连续 2 tick 且无 B：
   - A `UNKNOWN`，`evidence=SYNC_LOST`，随后清理。
4. 收到 Finish 但无 Start：`UNKNOWN`，`evidence=ORPHAN_FINISH`。
5. 服务端**不得**把 `FINISHED` 与 `INTERRUPTED` 混用；无证据时用 `UNKNOWN`。

### 6.3 FALL 状态机

**状态**：`NOT_FALLING → FALLING → FALL_END`（`FALL_END` 保留 40 tick 后回到 `NOT_FALLING`）。

**进入 FALLING（全部满足）**：

- `!player.onGround()`
- `player.getDeltaMovement().y < 0`（排除跳跃上升）
- `player.fallDistance > 0.5`
- `!player.isInWater()` 且 `!player.isInLava()`
- `!player.isFallFlying()`
- `player.getVehicle() == null`
- `!player.onClimbable()`

**记录**：

- `startPosition = player.position()`（进入时）
- `fallDistance = player.fallDistance`（每 tick 更新）
- `verticalVelocity = player.getDeltaMovement().y`
- `durationTicks`
- `onGround`
- `endReason`

**结束**：

| 条件 | endReason |
|---|---|
| `onGround()` | `LANDED` |
| 入水 | `WATER` |
| 入岩浆 | `LAVA` |
| 开始鞘翅飞行 | `FLYING` |
| 攀爬（梯子/藤蔓等） | `CLIMBING` |
| 上车/骑乘 | `VEHICLE` |
| 单 tick 位移 > 64 格或维度切换 | `TELEPORT` / `DIMENSION_CHANGE` |
| 死亡 | `DEATH` |
| 退出 | `LOGOUT` |
| 世界卸载 | `UNLOAD` |
| 其他 | `UNKNOWN` |

- 跳跃上升阶段 `deltaMovement.y >= 0`，不得进入 FALLING。
- 服务端使用服务端玩家字段；客户端使用客户端字段；两侧状态不做跨端覆盖。
- `FALL_END` 只保留摘要，不保留玩家引用；TTL 到期清理。

### 6.4 WALL_RUN

- `WALL_RUN_HORIZONTAL`：`Parkourability.get(player).get(HorizontalWallRun.class).isDoing()`。
- `WALL_RUN_VERTICAL`：`VerticalWallRun.class` 同理。
- 两个类是可区分的真实 API；分别记录，不互相覆盖。
- `WALL_RUN` 查询：
  - 若 H 或 V active → 返回聚合状态（`type=WALL_RUN`，`evidence` 列出具体类型）；
  - 两者都不 active → 无聚合状态；
  - 若两者同时 active → 两者都保留，聚合状态 `evidence=BOTH`（不猜测哪一个是“主”）。
- 墙面方向不可得；`directionAvailable=false`，不输出方向。

---

## 7. 生命周期与清理

| 事件 | 侧 | 处理 |
|---|---|---|
| `PlayerEvent.PlayerLoggedOutEvent` | 服务端 | 移除该 UUID 全部动作/FALL 状态 |
| `PlayerEvent.Clone` | 服务端 | 死亡/重生清理 |
| `LivingDeathEvent` | 服务端 | 清理（兜底） |
| `PlayerChangedDimensionEvent` | 服务端 | 清理；下一 tick 从 Capability 重建 |
| `LevelEvent.Unload` | 服务端 | 清理该维度玩家的状态（按最后维度过滤） |
| `ServerStoppingEvent` | 服务端 | 清空所有缓存 |
| `ClientPlayerNetworkEvent.LoggingOut` | 客户端 | 清空本地缓存 |
| `ClientPlayerNetworkEvent.Clone` | 客户端 | 清空本地缓存 |

- 数据结构：`Map<UUID, PlayerState>`；只持有 UUID 与不可变状态，不持有 `Player` 引用。
- 死亡/维度/退出后不得残留 active 状态；`FALL_END` 也清理。
- 玩家实体重建（Clone）后重新采样，不复制旧状态。

---

## 8. 权威性与同步

- `PARCOOL_SYNCED`（服务端）来自 ParCool 的客户端同步包，**不是服务端独立模拟**；不得单独作为攻击强化/伤害判定依据。
- `CLIENT_LOCAL` 只用于客户端表现与本地查询。
- `VANILLA_DERIVED`（服务端 FALL）基于服务端玩家物理字段，可标记 `serverAuthoritative=true`；但仍受移动包影响，后续战斗阶段需自行校验。
- `SERVER_NATIVE` 为未来服务端独立状态预留；当前阶段不产生需要它的状态。
- Adapter 不发送新的网络包；不改变 ParCool 同步协议。

---

## 9. 日志规范

相位变化时输出 INFO（不是每 tick）：

```text
[ParkourAdapter] side=CLIENT|SERVER player=<name> type=<TYPE> phase=<PHASE>
  source=<SOURCE> trust=<TRUST> ticks=<n> serverAuthoritative=<bool>
  inferred=<bool> evidence=<text>
```

FALL 进入/结束时附加：

```text
fall.start=<x,y,z> fall.distance=<double> fall.vy=<double>
fall.duration=<ticks> fall.onGround=<bool> fall.endReason=<REASON>
```

- 不在每 tick 打印。
- 服务端推断必须带 `inferred=true` 与 evidence。
- 客户端与服务端日志通过 `side=` 区分；单人日志中 `[Render thread]` / `[Server thread]` 也可辅助。

---

## 10. 性能

- 每玩家每 tick 只检查 6 个 ParCool 动作类（`get(Class).isDoing()`），O(6)。
- FALL 只读当前玩家字段，O(1)。
- 不扫描世界实体；不遍历所有玩家以外的实体。
- 状态对象只在变化时创建；稳定状态复用同一对象。
- 每个玩家状态有界：最多 7 个动作状态 + 1 个 FALL；`FALL_END` 40 tick TTL。
- 无新增网络同步。
- 无无界历史列表；不记录每 tick 快照。

---

## 11. 风险点

1. `ParCoolActionEvent` 位于 `api.unstable`；版本升级即需重新审计。启动时校验 ParCool 版本必须为 `3.4.3.3`，否则禁用 adapter 并记录错误（不猜测）。
2. 服务端动作状态是客户端同步镜像；trust 必须为 LOW，不能作为战斗权威。
3. 服务端无法看到 `TryToStart`/`TryToContinue`，无法精确区分自然结束与取消；只能用 FINISHED/启发式/UNKNOWN。
4. `Start.Pre` 在 `action.start` 之前，`isDoing()` 为 false；`Finish.Pre` 在 `action.finish` 之前，`isDoing()` 为 true；事件顺序必须按此处理。
5. FALL 的跳跃顶点/下落距离阈值在客户端与服务端可能有细微差异；单元测试覆盖边界，客户端人工验证。
6. 墙面方向无公开 API；不得反射/Mixin/伪造。
7. 服务端超时窗口（2 tick）是启发式，必须在日志与状态中标记，不视为精确事件。
8. 无 Mixin；若后续发现必须 Mixin，需单独评审，不能在本阶段擅自添加。

---

## 12. 验收门

1. 类型/接口与文件计划通过评审。
2. 单元测试全部通过。
3. `compileJava` / `build` 通过。
4. `runServer` 有/无 BC 均到达 `Done`。
5. 客户端人工清单（TC-01～TC-19）执行并记录；未执行项标记 NOT TESTED。
6. 不出现 `FINISHED`/`INTERRUPTED` 混用、无依据 UNKNOWN、状态泄漏。

本文件只是计划，不包含阶段 02 代码实现。

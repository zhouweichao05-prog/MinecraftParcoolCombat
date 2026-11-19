# PHASE_02_API_EVIDENCE — ParCool 3.4.3.3 API 证据

- 阶段：02
- 审计对象：`libs/ParCool-1.20.1-3.4.3.3.jar`
- SHA-256：`2DDEF8752B5A1428035B95F29E62C81E33B80D37851681E1B928D8A01615C6E7`
- 版本：Manifest `Implementation-Version: 3.4.3.3`
- 运行环境：Minecraft 1.20.1 / Forge 47.4.0 / Java 17
- 证据来源：`javap`、Fernflower 反编译、`mods.toml`、实际源码审计报告

> 说明：生产 JAR 中 Minecraft 成员是 SRG 名；开发运行使用 official 名。下文同时给出两套名字（如 `Player#m_20096_` = `onGround`），避免把 SRG 名当成 ParCool API。

---

## 1. 已确认的 ParCool 类与方法

### 1.1 `Parkourability`（能力对象）

```java
package com.alrex.parcool.common.capability;

public class Parkourability {
    public static Parkourability get(Player player);          // 可能为 null
    public <T extends Action> T get(Class<T> actionClass);    // 未注册时抛 IllegalArgumentException
    public List<Action> getList();                            // 内部列表引用，只读使用
    public boolean isDoingAny(Class<? extends Action>... classes);
}
```

- 附加位置：`AttachCapabilityHandler` 对每个 `Player` 附加 `PARKOURABILITY_LOCATION`。
- 执行环境：**客户端与服务端都有**（Capability 双端附加）。
- 调用限制：`get(Player)` 可能返回 `null`；必须先判空。

### 1.2 `Action`（动作基类）

```java
package com.alrex.parcool.common.action;

public abstract class Action {
    public boolean isDoing();
    public int getDoingTick();
    public int getNotDoingTick();
    public int getTickFromLastStarted();
    public boolean isJustStarted();
    public void start(Player, Parkourability, ByteBuffer, IStamina);
    public void finish(Player);
    // 客户端/服务端回调：onStartInLocalClient / onStartInOtherClient / onStartInServer ...
}
```

- 动作自身维护 `doing / doingTick / notDoingTick / tickFromStarted`。
- `isDoing()` 是只读查询入口；`start/finish` 是 ParCool 内部状态机使用，**Adapter 不直接调用**。
- 执行环境：类本身双端可用；客户端专属回调标注 `@OnlyIn(CLIENT)`，专用服务器会被 Forge 剥离。

### 1.3 六个首批动作类

| 类型 | 完整类名 | 识别方法 |
|---|---|---|
| FAST_RUN | `com.alrex.parcool.common.action.impl.FastRun` | `isDoing()` |
| SLIDE | `...impl.Slide` | `isDoing()` |
| VAULT | `...impl.Vault` | `isDoing()` |
| WALL_RUN_HORIZONTAL | `...impl.HorizontalWallRun` | `isDoing()` |
| WALL_RUN_VERTICAL | `...impl.VerticalWallRun` | `isDoing()` |
| WALL_JUMP | `...impl.WallJump` | `isDoing()` |

- **CONFIRMED**：六个类均存在、均继承 `Action`、均可用 `isDoing()` 查询。
- **CONFIRMED**：水平/垂直墙跑是**两个不同类**，不是同一个类的模式；因此可按类区分。
- `Vault.MAX_TICK = 11`；`Vault.getCurrentAnimation()` 只在客户端 start 回调中赋值，服务端为 `null`（**不要用于服务端判断**）。
- `Slide.getSlidingVector()` 只在客户端 start 回调中赋值，服务端为 `null`。
- `HorizontalWallRun` 的 `bodyYaw`、`runningWallDirection`、`runningDirection` 均为 private，无公开 getter；`VerticalWallRun.wallDirection` 亦为 private。**墙面方向在当前 API 不可得**。

### 1.4 动作事件 `ParCoolActionEvent`

```java
package com.alrex.parcool.api.unstable.action;

public class ParCoolActionEvent extends Event {
    public Player getPlayer();
    public Action getAction();

    public static class Start {
        public static class Pre extends Start { ... }
        public static class Post extends Start { ... }
    }
    public static class Finish {
        public static class Pre extends Finish { ... }
        public static class Post extends Finish { ... }
    }
    public static class Tick {
        public static class Pre extends Tick { ... }
        public static class Post extends Tick { ... }
    }
    @Cancelable public static class TryToContinue extends ParCoolActionEvent { ... }
    public static class TryToStart extends ParCoolActionEvent { public boolean isCancelable() { return true; } }
}
```

- **CONFIRMED**：`Start.Pre/Post`、`Finish.Pre/Post`、`TryToStart`、`TryToContinue` 均存在；构造函数为 `(Player, Action)`。
- **CONFIRMED**：`TryToContinue` 标注 `@Cancelable`；`TryToStart` 未标注但覆盖 `isCancelable()` 返回 `true`（字节码：`iconst_1; ireturn`）。
- **CONFIRMED**：包名为 `api.unstable`，属于明示不稳定 API；必须锁定 3.4.3.3。

---

## 2. 事件调用位置与执行环境

### 2.1 客户端动作判定

`com.alrex.parcool.common.action.ActionProcessor#checkAndChangeActionState` 标注 `@OnlyIn(Dist.CLIENT)`，只在本地玩家（`player.isLocalPlayer()`）执行：

```text
开始：
  TryToStartEvent（旧，@Deprecated @Cancelable）
  TryToStart（新，isCancelable=true）
  action.canStart(...)
  Start.Pre
  action.start(...)
  Start.Post

结束：
  TryToContinueEvent（旧，@Deprecated @Cancelable）
  TryToContinue（@Cancelable）
  action.canContinue(...)
  Finish.Pre
  action.finish(...)
  Finish.Post
```

- **CONFIRMED**：`TryToStart` / `TryToContinue` 只在**客户端**发送；取消它们可阻止客户端开始/继续。
- **CONFIRMED**：`Start.Pre` 在 `action.start` 之前；`Finish.Pre` 在 `action.finish` 之前。
- **CONFIRMED（关键）**：专用服务器没有客户端 `ActionProcessor` 路径，因此**服务端收不到 `TryToStart/TryToContinue`**。

### 2.2 服务端同步路径

`com.alrex.parcool.common.network.SyncActionStateMessage#handleServer`（`@OnlyIn(Dist.DEDICATED_SERVER)`）：

```text
收到 Start 数据：
  Start.Pre
  action.start(player, parkourability, buffer, null)
  Start.Post

收到 Finish 数据：
  Finish.Pre
  action.finish(player)
  Finish.Post
```

- **CONFIRMED**：服务端收到客户端 `Start` 后**直接执行 `action.start`**，不重新执行 `canStart`，也不发送 `TryToStart`。
- **CONFIRMED**：服务端无法精确区分“自然结束”与“客户端取消”，只能看到 `Finish`。
- **CONFIRMED**：`handleServer` 使用包内 `senderUUID` 转发给其他客户端；Adapter 不依赖该广播做权威判断。
- **CONFIRMED**：服务端动作状态是客户端同步镜像，Adapter 必须标记 `source=PARCOOL_SYNCED`、`trust=LOW`。

### 2.3 `Action#start` 的侧别分支

```text
if (player.isLocalPlayer())        → onStartInLocalClient
else if (level.isClientSide())     → onStartInOtherClient
else                                → onStartInServer
```

- **CONFIRMED**：同一动作在不同侧调用不同回调；服务端不能获取客户端专属的动画/滑动方向数据。

---

## 3. FALL 的原版 API 证据

FALL 不是 ParCool 动作，没有 `Fall` 类。使用原版 `Player`/`Entity` 字段与方法：

| 语义 | Official 名 | SRG 证据 |
|---|---|---|
| 是否接地 | `Entity#onGround()` | `m_20096_` → `onGround` |
| 累计下落距离 | `Entity#fallDistance`（public field） | `f_19789_` → `fallDistance` |
| 当前速度 | `Entity#getDeltaMovement()` | `m_20184_` → `getDeltaMovement` |
| 是否在水中 | `Entity#isInWater()` | `m_20069_` → `isInWater` |
| 是否在水中/气泡 | `Entity#isInWaterOrBubble()` | `m_20072_` → `isInWaterOrBubble` |
| 是否在岩浆中 | `Entity#isInLava()` | `m_20077_` → `isInLava` |
| 鞘翅飞行 | `LivingEntity#isFallFlying()` | `m_21255_` → `isFallFlying` |
| 载具 | `Entity#getVehicle()` | `m_20202_` → `getVehicle` |
| 攀爬 | `LivingEntity#onClimbable()` | `m_6147_` → `onClimbable` |
| Y 坐标 | `Entity#getY()` | `m_20186_` → `getY` |
| 位置 | `Entity#position()` | `m_20182_` → `position` |

- **CONFIRMED**：以上字段/方法在 1.20.1 official mappings 中存在；映射来自本工程 `build/createSrgToMcp/output.srg`。
- **CONFIRMED**：`isInLava()` 映射为 `m_20077_`。
- **CONFIRMED**：FALL 的进入/退出必须同时参考 `onGround`、`getDeltaMovement().y`、`fallDistance`，不能只看 `onGround`。
- **UNCONFIRMED**：客户端与服务端 `fallDistance` 的逐 tick 同步一致性（服务端由移动包更新）需要运行时测试。

---

## 4. Forge 生命周期 API

| 用途 | 类/签名 |
|---|---|
| 玩家退出 | `net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedOutEvent(Player)` |
| 死亡/重生 | `PlayerEvent$Clone(Player, Player, boolean)` |
| 维度切换 | `PlayerEvent$PlayerChangedDimensionEvent(Player, ResourceKey<Level>, ResourceKey<Level>)` |
| 世界卸载 | `net.minecraftforge.event.level.LevelEvent$Unload(LevelAccessor)` |
| 服务器停止 | `net.minecraftforge.event.server.ServerStoppingEvent(MinecraftServer)` |
| 死亡兜底 | `net.minecraftforge.event.entity.living.LivingDeathEvent(LivingEntity, DamageSource)` |

- **CONFIRMED**：以上事件类在 Forge 47.4.0 中存在（`javap` 实际输出）。
- 客户端清理使用 `net.minecraftforge.client.event.ClientPlayerNetworkEvent$LoggingOut` / `$Clone`（客户端专属，必须隔离）。
- `PlayerTickEvent` 用于每玩家采样（`TickEvent.PlayerTickEvent`，`Phase.END`）。

---

## 5. 已确认 / 部分确认 / 未确认

### CONFIRMED

1. `Parkourability.get(Player)`、`get(Class)`、`isDoingAny(...)` 的签名与双端可用性。
2. `Action.isDoing()` / `getDoingTick()` / `getNotDoingTick()` / `getTickFromLastStarted()`。
3. 六个动作类的存在与 `isDoing()` 识别。
4. `ParCoolActionEvent.Start/Finish/TryToStart/TryToContinue` 的类结构与构造函数。
5. `TryToStart` 覆盖 `isCancelable()` 为 true；`TryToContinue` 标注 `@Cancelable`。
6. `Start.Pre` 在 `action.start` 前、`Finish.Pre` 在 `action.finish` 前。
7. `TryToStart/TryToContinue` 只在客户端；服务端只收到同步后的 `Start/Finish`。
8. 服务端收到 `Start` 后直接 `action.start`，不重新校验。
9. 水平/垂直墙跑是两个不同类，可按类区分。
10. 墙面方向无公开 getter。
11. FALL 不是 ParCool 动作；原版字段可用于派生。
12. Forge 生命周期事件类存在。

### PARTIALLY_CONFIRMED

1. 运行时所有动作的 `Start.Pre/Finish.Pre` 是否都能被 Adapter 稳定观察到（静态代码确认事件发布，运行时未逐动作验证）。
2. 服务端 `Start.Pre` 与 `isDoing()` 的精确 tick 时序（事件在 `action.start` 前，轮询在 tick 末；需要实测）。
3. 服务端超时窗口 2 tick 的可靠性（启发式）。
4. 客户端/服务端 `fallDistance` 的一致性。
5. 服务端事件与轮询在同一 tick 内的精确先后（需实测日志验证）。

### UNCONFIRMED

1. 服务端能否可靠区分自然结束与中断（当前结论：不能精确区分，只能启发式/UNKNOWN）。
2. 多人服务器下同步包丢失/重排对状态的影响。
3. 同时活跃 H/V 墙跑的发生频率与语义（保留两者，不猜测）。
4. `TryToStart` 在真实客户端被其他模组取消时的行为（需运行时测试）。

---

## 6. 不使用的 API

- `Animation`、`Animator`、`PlayerModelTransformer`、`ParCoolAnimationInfoEvent`：客户端动画，禁止用于服务端状态推断。
- `Vault#getCurrentAnimation()`、`Slide#getSlidingVector()`：客户端专属，服务端为 `null`，不作为玩法数据。
- `HorizontalWallRun#bodyYaw` / `VerticalWallRun#wallDirection`：private，无公开 getter；不反射、不 Mixin。
- `Action#start/finish`：ParCool 内部状态机入口；Adapter 不调用。
- `SyncActionStateMessage` 的内部缓冲区格式：不依赖，避免协议耦合。

---

## 7. 复现命令

```powershell
Set-Location 'D:\MinecraftParcoolCombat'
$jar='libs\ParCool-1.20.1-3.4.3.3.jar'
javap -p -classpath $jar com.alrex.parcool.common.capability.Parkourability
javap -p -classpath $jar com.alrex.parcool.common.action.Action
javap -p -classpath $jar com.alrex.parcool.common.action.impl.HorizontalWallRun
javap -p -classpath $jar com.alrex.parcool.common.action.impl.VerticalWallRun
javap -p -classpath $jar 'com.alrex.parcool.api.unstable.action.ParCoolActionEvent$TryToStart'
javap -p -classpath $jar 'com.alrex.parcool.api.unstable.action.ParCoolActionEvent$TryToContinue'
```

映射核验：

```powershell
Select-String -Path 'build\createSrgToMcp\output.srg' -Pattern 'm_20096_|f_19789_|m_20184_|m_20069_|m_21255_|m_6147_'
```

---

## 8. 结论

API 证据足以支撑阶段 02 的**只读**适配：六个动作可由 `Parkourability + Action.isDoing()` 识别；`Start/Finish` 事件提供边沿；客户端取消事件可用；FALL 可由原版物理字段派生。

不足以支撑：

- 服务端精确取消/中断；
- 墙面方向数据；
- 把 ParCool 同步状态当作服务端权威；
- 依赖客户端动画推断服务端状态。

以上限制必须在实现与测试报告中明确标记。

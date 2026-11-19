# PARCOOL_API_AUDIT — ParCool 3.4.3.3 API 审计

- 审计对象：`D:\PCL 正式版 2.10.3\dc\.minecraft\versions\DeceasedCraft_CB\mods\ParCool-1.20.1-3.4.3.3.jar`
- SHA-256：`2DDEF8752B5A1428035B95F29E62C81E33B80D37851681E1B928D8A01615C6E7`
- Manifest 版本：`3.4.3.3`（`Implementation-Version`）
- 审计方式：`javap` 字节码 + Fernflower 反编译 + 事件总线字节码核对
- 结论标记：**CONFIRMED** / **PARTIALLY_CONFIRMED** / **UNCONFIRMED**

> 说明：1.20.1 Forge 生产 JAR 中，Minecraft 方法名以 SRG 名出现（如 `m_7578_` = `isLocalPlayer`）。本文引用反编译源码中的原始名字，并在括号中给出语义名。所有类名、方法名、字段名均来自实际 JAR，未使用其他版本推断。

---

## 1. 总体架构结论

| 结论 | 状态 | 证据 |
|---|---|---|
| ParCool 使用 Forge Capability `Parkourability` 挂在每个 `Player` 上，内部持有 27 个 `Action` 实例 | **CONFIRMED** | `com.alrex.parcool.common.capability.Parkourability`：`List<Action> actions = ActionList.constructActionsList();`、`static get(Player)`；`AttachCapabilityHandler` 对每个 `Player` 附加 `PARKOURABILITY_LOCATION` |
| 每个动作的状态由 `Action` 自身字段维护，不依赖全局状态机 | **CONFIRMED** | `Action`：`boolean doing`、`int doingTick`、`int notDoingTick`、`int tickFromStarted` |
| 动作的“开始/继续/结束”判定主要在客户端完成，服务器通过自定义包接收并复现 | **CONFIRMED** | `ActionProcessor.checkAndChangeActionState(...)` 标注 `@OnlyIn(Dist.CLIENT)`，仅在 `player.isLocalPlayer()` 时调用；服务器在 `SyncActionStateMessage.handleServer` 中直接 `action.start(...)` / `action.finish(...)` |
| 公开 API 主要集中在 `com.alrex.parcool.api`，但动作事件与动画 API 位于 `api.unstable` | **CONFIRMED** | 包列表：`api/Stamina`、`api/Attributes`、`api/Effects`、`api/SoundEvents`、`api/client/gui/ParCoolHUDEvent`、`api/unstable/action/ParCoolActionEvent`、`api/unstable/animation/*` |
| 只“读取当前动作”不需要 Mixin | **CONFIRMED** | `Parkourability.get(Player)` 与 `Action.isDoing()` 均为普通 public 方法，可在 Forge 事件/命令/服务端逻辑中直接调用 |
| 服务器端“阻止客户端发起的动作开始”没有公开事件（正常动作路径） | **CONFIRMED** | 服务器收到 `Start` 同步包后直接 `action.start(...)`，见 `SyncActionStateMessage.handleServer` 第 57-64 行；`TryToStart` 只在该包之前的客户端判定里发送 |

---

## 2. 动作状态管理

### 2.1 `Action` 的状态字段与方法

反编译 `com.alrex.parcool.common.action.Action`（JAR 内 `/com/alrex/parcool/common/action/Action.class`）：

```java
private boolean doing = false;
private int doingTick = 0;
private int notDoingTick = 0;
private int tickFromStarted = -1;

public boolean isJustStarted() { return isDoing() && getDoingTick() == 0; }
public int getDoingTick();        // 当前动作已持续 tick
public int getNotDoingTick();     // 距上次结束的 tick
public int getTickFromLastStarted();
public boolean isDoing();
public void tick();               // doingTick++ / notDoingTick++，由 ActionProcessor 每 tick 调用
public void start(Player, Parkourability, ByteBuffer startInfo, @Nullable IStamina);
public void finish(Player);
```

- **CONFIRMED**：`isDoing()` 是唯一权威的“当前是否在执行该动作”的只读入口。
- **CONFIRMED**：`start(...)` 会设置 `doing = true`、`tickFromStarted = 0`，然后按运行环境分派到 `onStart` / `onStartInLocalClient` / `onStartInOtherClient` / `onStartInServer`；`finish(...)` 反向设置 `doing = false` 并分派 stop 回调。
- **CONFIRMED**：`tick()` 由 `ActionProcessor` 每玩家每 tick 调用；`doingTick` 在动作期间递增，结束后清零并累计 `notDoingTick`。

### 2.2 动作列表与 ID

`com.alrex.parcool.common.action.ActionList` 中 `ACTION_REGISTRIES` 按固定顺序注册 27 个动作；`getIndexOf(Class)`、`getByIndex(int)`、`getByName(String)` 提供映射。

| 索引 | 类 | 备注 |
|---:|---|---|
| 0 | `BreakfallReady` | 落地缓冲准备（按住键） |
| 1 | `CatLeap` | |
| 2 | `ChargeJump` | |
| 3 | `ClimbPoles` | |
| 4 | `ClimbUp` | |
| 5 | `ClingToCliff` | |
| 6 | `HideInBlock` | |
| 7 | `Crawl` | |
| 8 | `Dive` | |
| 9 | `Dodge` | |
| 10 | `FastRun` | |
| 11 | `FastSwim` | |
| 12 | `Flipping` | |
| 13 | `HangDown` | |
| 14 | `HorizontalWallRun` | |
| 15 | `JumpFromBar` | |
| 16 | `QuickTurn` | |
| 17 | `RideZipline` | |
| 18 | `Roll` | |
| 19 | `SkyDive` | |
| 20 | `Slide` | |
| 21 | `Tap` | |
| 22 | `Vault` | |
| 23 | `VerticalWallRun` | |
| 24 | `WallJump` | |
| 25 | `WallSlide` | |
| 26 | `StartSwimByCrawl` | InstantAction |

- **CONFIRMED**：索引由 `ACTION_REGISTRIES` 的声明顺序决定，`SyncActionStateMessage` 用 `short` 索引同步动作；**不要跨 ParCool 版本硬编码索引**。
- **CONFIRMED**：推荐通过 `parkourability.get(FastRun.class)` 等类对象访问，而不是通过数字 ID。

---

## 3. 开始 / 持续 / 结束 / 取消机制

### 3.1 主循环：`ActionProcessor.onTick(TickEvent.PlayerTickEvent)`

反编译 `com.alrex.parcool.common.action.ActionProcessor`：

```java
public void onTick(TickEvent.PlayerTickEvent event) {
    if (event.phase != Phase.START) {
        ...
        boolean inClient = event.side == LogicalSide.CLIENT;
        boolean inServer = !inClient;
        ...
        for (Action action : parkourability.getList()) {
            MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.Tick.Pre(player, action));
            processAction(player, parkourability, stamina, builder, inClient, action);
            MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.Tick.Post(player, action));
        }
        ...
    }
}
```

- **CONFIRMED**：`Tick.Pre` / `Tick.Post` 在**客户端与服务端都会**对每个动作、每玩家、每 tick 发送（27 个动作 → 每玩家每 tick 最多 54 次事件）。高频，禁止在其中做重计算或全服扫描。
- **CONFIRMED**：`processAction` 依次调用 `action.tick()`、`action.onTick()`、`onClientTick` / `onServerTick`，再按 `isDoing()` 调用 working tick 回调。
- **CONFIRMED**：对于本地玩家（`player.m_7578_()` / `isLocalPlayer()`），额外调用 `checkAndChangeActionState(...)` 决定开始/结束；只有本地玩家会发送同步包。

### 3.2 客户端判定 → 同步 → 服务端复现

`ActionProcessor.checkAndChangeActionState(...)`（标注 `@OnlyIn(Dist.CLIENT)`，第 186-214 行）：

```java
if (action.isDoing()) {
    canContinue = parkourability.getActionInfo().can(action.getClass())
        && !MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.TryToContinueEvent(player, action))
        && !MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.TryToContinue(player, action))
        && action.canContinue(player, parkourability, stamina);
    if (!canContinue) {
        MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.Finish.Pre(player, action));
        action.finish(player);
        MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.StopEvent(player, action));
        MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.Finish.Post(player, action));
        builder.appendFinishMsg(parkourability, action);
    }
} else {
    bufferOfStarting.clear();
    canContinue = !player.m_5833_() && parkourability.getActionInfo().can(action.getClass())
        && !MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.TryToStartEvent(player, action))
        && !MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.TryToStart(player, action))
        && action.canStart(player, parkourability, stamina, bufferOfStarting);
    ...
}
```

- **CONFIRMED**：开始判定顺序为：`getActionInfo().can(...)` → 旧 `TryToStartEvent` → 新 `TryToStart` → `action.canStart(...)`。
- **CONFIRMED**：取消新 `TryToStart` 即可阻止该动作在客户端开始；取消 `TryToContinue` 会触发结束流程。
- **CONFIRMED**：开始后通过 `SyncActionStateMessage.Encoder.appendStartData(...)` 发送 `Start`；结束通过 `appendFinishMsg(...)` 发送 `Finish`；状态变化通过 `appendSyncData(...)` 发送 `Normal`。

服务端接收（`SyncActionStateMessage.handleServer`，标注 `@OnlyIn(Dist.DEDICATED_SERVER)`）：

```java
case Start:
    ByteBuffer startInfo = item.getBuffer();
    MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.Start.Pre(player, action));
    action.start(player, parkourability, startInfo, (IStamina) null);
    MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.StartEvent(player, action));
    MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.Start.Post(player, action));
    break;
case Finish:
    MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.Finish.Pre(player, action));
    action.finish(player);
    ...
```

- **CONFIRMED**：服务器**不会**重新执行 `canStart` / `TryToStart` 校验，直接调用 `action.start(...)`；`Start.Pre` 事件在服务器端只是通知，不可取消（未标注 `@Cancelable`）。
- **CONFIRMED**：`handleServer` 使用客户端包内的 `senderUUID` 重新广播给所有玩家（`ParCool.CHANNEL_INSTANCE.send(PacketDistributor.ALL.noArg(), this)`），未用 `player.getUUID()` 覆盖。协议层面存在伪造他人 UUID 广播的风险（属于 ParCool 自身行为，不在本阶段修复范围，但 ParkourCombat 不得把 ParCool 的服务器状态当作可信输入）。
- **CONFIRMED**：`Normal` 包调用 `action.restoreSynchronizedState(buffer)`，动作自定义的同步字段由各动作 `saveSynchronizedState` / `restoreSynchronizedState` 编解码（如 `HorizontalWallRun` 只同步一个 `bodyYaw` float）。

### 3.3 可取消事件清单

| 事件类 | 可取消 | 实际取消语义 | 状态 | 证据 |
|---|---|---|---|---|
| `ParCoolActionEvent.TryToStart` | 是（覆写 `isCancelable()` 返回 `true`） | 在 `canStart` 之前；取消则不开始 | **CONFIRMED** | `javap -c`：`isCancelable: iconst_1; ireturn`；无 `@Cancelable` 注解但覆写生效 |
| `ParCoolActionEvent.TryToStartEvent` | 是（`@Cancelable`，`@Deprecated`） | 旧兼容事件，先于新事件发送 | **CONFIRMED** | `javap -v` 显示 `@Deprecated` + `@Cancelable` |
| `ParCoolActionEvent.TryToContinue` | 是（`@Cancelable`） | 在 `canContinue` 之前；取消则结束 | **CONFIRMED** | `javap -v` 显示 `@Cancelable` |
| `ParCoolActionEvent.TryToContinueEvent` | 是（`@Cancelable`，`@Deprecated`） | 旧兼容事件 | **CONFIRMED** | 源码类定义 |
| `ParCoolActionEvent.Start.Pre` / `Start.Post` | 否 | 开始前/后通知 | **CONFIRMED** | 类未标注 `@Cancelable`，也未覆写 `isCancelable` |
| `ParCoolActionEvent.Finish.Pre` / `Finish.Post` | 否 | 结束前/后通知 | **CONFIRMED** | 同上 |
| `ParCoolActionEvent.Tick.Pre` / `Tick.Post` | 否 | 每 tick 通知 | **CONFIRMED** | 同上 |
| `ParCoolActionEvent.StopEvent` / `StartEvent` | 否（`@Deprecated`） | 旧通知 | **CONFIRMED** | 源码 |

补充事实：

- **CONFIRMED**：`TryToStart` 虽然未标注 `@Cancelable`，但 `Event.isCancelable()` 是实例方法，ParCool 覆写后返回 `true`，因此 `EventBus.post(...)` 返回“被取消”布尔值仍然有效（`Event.setCanceled` 内部调用虚方法 `isCancelable()` 校验）。源码中 `!MinecraftForge.EVENT_BUS.post(...TryToStart...)` 的写法证明作者依赖该返回值。
- **CONFIRMED（关键限制）**：正常游戏中，`TryToStart` / `TryToContinue` 的发送位置在 `ActionProcessor.checkAndChangeActionState`，该方法 `@OnlyIn(Dist.CLIENT)`，只在**本地客户端**执行。因此：
  - 客户端可以用这两个事件阻止/中止自己的动作；
  - **专用服务器无法用它们阻止客户端发起的动作**（服务器收到 `Start` 包时直接执行）。
- **CONFIRMED（例外）**：`PlayerDamageHandler.onFall` 在服务端处理 `LivingFallEvent` 时会自行发送 `TryToStartEvent` + `TryToStart`（用于 `HideInBlock` 的落地隐藏路径），这是少见的“服务器侧可取消开始”的 ParCool 内部路径，但只覆盖该特定动作。

---

## 4. 如何获取玩家当前正在执行的动作

### 4.1 公开入口

```java
Parkourability parkourability = Parkourability.get(player); // 可能为 null
if (parkourability != null) {
    FastRun fastRun = parkourability.get(FastRun.class);   // 泛型，不存在时抛 IllegalArgumentException
    boolean running = fastRun.isDoing();

    boolean any = parkourability.isDoingAny(FastRun.class, Slide.class, Vault.class);
    List<Action> all = parkourability.getList();           // 27 个实例，顺序固定
}
```

- **CONFIRMED**：`Parkourability.get(Player)` 返回 `null` 表示该玩家没有 Capability（例如能力未附加、玩家对象异常）；必须判空。
- **CONFIRMED**：`get(Class<T>)` 在类未注册时抛 `IllegalArgumentException`；只传 ParCool 自带动作类才是安全的。
- **CONFIRMED**：`getList()` 返回内部可变列表的引用（`actions`），不要修改；只读遍历。
- **CONFIRMED**：`isDoingAny(Class...)` 是便捷方法，内部逐个 `isDoing()`。
- **CONFIRMED**：`Parkourability` 在客户端与服务端都会附加（`AttachCapabilityHandler`），因此服务端也能读取 `isDoing()`；但该状态来源于客户端同步，见第 6 节。

### 4.2 各动作的识别方式（重点动作）

| 动作 | 类（完整包名） | 识别方式 | 服务端可见的附加状态 | 状态 |
|---|---|---|---|---|
| Fast Run | `com.alrex.parcool.common.action.impl.FastRun` | `get(FastRun.class).isDoing()`；另有 `canActWithRunning(player)`（客户端） | `isDoing()` 可用；`getDashTick()` 是 `@OnlyIn(CLIENT)`，服务端调用会缺失/不可用 | **CONFIRMED** |
| Slide | `com.alrex.parcool.common.action.impl.Slide` | `get(Slide.class).isDoing()`；客户端可用 `getSlidingVector()` | `getSlidingVector()` 仅在客户端 start 回调中赋值；服务端为 `null` | **CONFIRMED** |
| “Fall”（下落） | 没有名为 `Fall` 的动作类 | 下落相关由 `SkyDive`、`Dive`、`BreakfallReady`、`HideInBlock` 及 `LivingFallEvent` 共同处理 | 落地伤害减免/取消在服务端 `PlayerDamageHandler.onFall` 中执行 | **CONFIRMED**（“无 Fall 类”本身） |
| Vault | `com.alrex.parcool.common.action.impl.Vault` | `get(Vault.class).isDoing()`；`MAX_TICK = 11` | `getCurrentAnimation()` 在服务端返回 `null`（`currentAnimation` 只在 `onStartInLocalClient` / `onStartInOtherClient` 赋值） | **CONFIRMED** |
| Wall Run（水平） | `com.alrex.parcool.common.action.impl.HorizontalWallRun` | `get(HorizontalWallRun.class).isDoing()` | `bodyYaw` 通过 `saveSynchronizedState` / `restoreSynchronizedState` 同步到服务端 | **CONFIRMED** |
| Wall Run（垂直） | `com.alrex.parcool.common.action.impl.VerticalWallRun` | `get(VerticalWallRun.class).isDoing()` | 仅 `isDoing()` / tick 状态可靠；方向数据主要在客户端 | **CONFIRMED** |
| Wall Jump | `com.alrex.parcool.common.action.impl.WallJump` | `get(WallJump.class).isDoing()`；`justJumped()` 在 `onStart` 中置 `true`、下一 tick `onTick` 置 `false` | 服务器能在 start 当 tick 观察到 `justJumped()`；跨 tick 不可依赖 | **CONFIRMED** |
| Wall Slide | `com.alrex.parcool.common.action.impl.WallSlide` | `get(WallSlide.class).isDoing()`；`getLeanedWallDirection()`（客户端） | `getLeanedWallDirection()` 主要客户端使用 | **CONFIRMED** |
| Crawl / Roll / Dodge | `Crawl` / `Roll` / `Dodge` | 各自 `isDoing()` | Dodge 的 i-frame 在服务端 `LivingAttackEvent` 中实现（`PlayerDamageHandler.onAttack`） | **CONFIRMED** |

`FastRun` 关键源码（反编译）：

```java
public boolean canContinue(Player player, Parkourability parkourability, IStamina stamina) {
    return !stamina.isExhausted() && !player.m_20072_() && player.m_20202_() == null
        && !player.m_21255_() && player.m_20142_() && !player.m_20143_() && !player.m_6069_()
        && !player.m_6144_()
        && !((Crawl) parkourability.get(Crawl.class)).isDoing()
        && ... // ClingToCliff/HangDown/RideZipline/HideInBlock
        && (PressKey && KeyBindings... || Toggle && toggleStatus || Auto);
}
```

`FastRun.onServerTick` 在 `isDoing()` 时向玩家添加 `parcool.modifier.fast_run` 移动速度修饰符；即 **服务器确实按客户端同步的 `isDoing()` 改变移动属性**。这说明 ParCool 的服务器玩法状态是“客户端驱动”的，ParkourCombat 若要用跑酷状态做战斗结算，必须自行做服务端合理性校验，不能直接信任。

---

## 5. 客户端 / 服务端区分

| 位置 | 客户端 | 服务端 | 状态 |
|---|---|---|---|
| `Action.start(...)` 分派 | `onStartInLocalClient`（本地玩家） / `onStartInOtherClient`（其他玩家） | `onStartInServer` | **CONFIRMED**（`Action.java:59-71`） |
| 动作开始判定 | `ActionProcessor.checkAndChangeActionState`（`@OnlyIn(CLIENT)`） | 不判定，直接执行同步包 | **CONFIRMED** |
| 状态同步 | `SyncActionStateMessage.sync(...)` 发 C2S；`handleClient` 处理 S2C | `handleServer`（`@OnlyIn(DEDICATED_SERVER)`）处理 C2S 并转发 | **CONFIRMED** |
| 动画 Capability | `Animation` 仅在客户端附加（`AttachCapabilityHandler` 判断 `level.m_5776_()`） | 无 `Animation` Capability | **CONFIRMED** |
| 动作视觉数据 | `Vault.getCurrentAnimation()`、`Slide.getSlidingVector()`、`WallJump` 的跳跃类型等只写在客户端 start 回调 | 服务端拿不到（`null` / 默认值） | **CONFIRMED** |
| 网络通道 | `ParCool.CHANNEL_INSTANCE`，协议字符串 `3.4.3.0`，通道名 `parcool:message` | 同左 | **CONFIRMED** |

> **CONFIRMED（集成服务器补充）**：在单人游戏/集成服务器中，ParCool 使用 `ClientProxy` 注册消息，C2S 的 `SyncActionStateMessage` 由 `handleClient` 处理；该方法先判断 `getReceptionSide() == LogicalSide.CLIENT`，客户端分支处理 S2C，**否则走服务端分支**并同样直接 `action.start(...)`。只有专用服务器才使用 `ServerProxy` + `handleServer`。两条路径都不做 `canStart` 重新校验。
>
> **CONFIRMED 结论**：ParCool 的“服务端动作状态”是客户端上报后的镜像，不是服务端独立模拟结果。ParkourCombat 的服务端战斗结算不得把它当作不可伪造的物理事实。

---

## 6. 公开 API 稳定性评估

| API | 包 | 稳定性判断 | 状态 |
|---|---|---|---|
| `Stamina`（`get`/`getValue`/`isExhausted`/`setValue`/`consume`/`recover`） | `com.alrex.parcool.api` | 正式 API；但 `setValue`/`consume`/`recover` 标注 `@OnlyIn(CLIENT)` | **CONFIRMED** |
| `Attributes`（`MAX_STAMINA`、`STAMINA_RECOVERY`） | `com.alrex.parcool.api` | 正式 API（DeferredRegister） | **CONFIRMED** |
| `Effects` / `SoundEvents` | `com.alrex.parcool.api` | 正式 API | **CONFIRMED** |
| `ParCoolHUDEvent` | `com.alrex.parcool.api.client.gui` | 客户端 HUD 事件 | **CONFIRMED** |
| `ParCoolActionEvent` | `com.alrex.parcool.api.unstable.action` | **明确 unstable**；事件类结构可能随版本变化 | **CONFIRMED** |
| `AnimationOption` / `AnimationPart` / `ParCoolAnimationInfoEvent` | `com.alrex.parcool.api.unstable.animation` | **明确 unstable**，且全为 `@OnlyIn(CLIENT)` | **CONFIRMED** |
| `Parkourability` | `com.alrex.parcool.common.capability` | public 但位于 `common` 内部包，无 API 稳定承诺 | **PARTIALLY_CONFIRMED** |
| `Action` / `ActionList` / 各 `impl` 动作类 | `com.alrex.parcool.common.action[.impl]` | public 但内部实现包；仅建议做只读判断 | **PARTIALLY_CONFIRMED** |
| `ParCool.isActive()` | `com.alrex.parcool.ParCool` | public 静态方法，可用于判断 ParCool 是否启用 | **CONFIRMED** |

- **CONFIRMED**：ParCool 没有类似 `ParCoolAPI` 的统一门面类；能力对象与动作类就是事实上的 API 面。
- **PARTIALLY_CONFIRMED**：`api.unstable` 包名已经声明不稳定；在锁定 3.4.3.3 的前提下可以使用，但必须把“版本升级即重新审计”写进维护约束。

---

## 7. 是否需要 Mixin

| 需求 | 是否需要 Mixin | 说明 | 状态 |
|---|---|---|---|
| 在服务端读取玩家是否处于 FastRun/Slide/Vault/WallRun/WallJump | 否 | `Parkourability.get(player)` + `get(Class).isDoing()` | **CONFIRMED** |
| 监听动作开始/结束（通知用途） | 否 | `ParCoolActionEvent.Start/Finish`（客户端与服务端同步路径都会发） | **CONFIRMED** |
| 在**客户端**阻止某个动作开始/继续 | 否 | `TryToStart` / `TryToContinue` / 旧事件可取消 | **CONFIRMED** |
| 在**服务端**阻止客户端已发起的动作开始 | 是（或改为客户端拦截） | 服务器收到 `Start` 包直接执行，无服务端可取消事件；除非 Mixin 到 `SyncActionStateMessage.handleServer` 或动作 `start` | **CONFIRMED（限制）** |
| 修改 ParCool 动画某一部位 | 否 | `ParCoolAnimationInfoEvent` + `AnimationOption.cancel(AnimationPart)`（客户端） | **CONFIRMED** |
| 修改 ParCool 的移动/体力等服务端逻辑 | 是（若必须改内部） | 公开 API 不足以改变内部判定；优先通过 Forge 事件/属性修饰符实现 | **PARTIALLY_CONFIRMED** |

**建议**：ParkourCombat 第一阶段不需要 Mixin。需要“服务端阻止”时，采用“客户端事件拦截 + 服务端拒绝非法收益”的双层策略，而不是直接 Mixin 进 ParCool。

---

## 8. 安全与性能注意点（审计发现）

1. **CONFIRMED**：客户端可发送任意 `Start` 同步包，服务端不校验 `canStart`，直接执行动作（包括 FastRun 的移动速度属性修饰符）。恶意客户端可以伪造跑酷状态。
   - 证据：`SyncActionStateMessage.handleServer` 第 57-64 行；`FastRun.onServerTick` 在 `isDoing()` 时添加 `parcool.modifier.fast_run`。
2. **CONFIRMED**：`handleServer` 用包内 `senderUUID` 广播给所有客户端，未强制使用 `context.getSender().getUUID()`。客户端可伪造其他玩家的 UUID 让旁观者看到错误动作状态。
3. **CONFIRMED**：`Decoder` 只限制单条 `bufferSize <= 1024`，未校验动作 ID 范围以外的语义；`action == null` 时跳过该条。
4. **CONFIRMED**：`Tick.Pre/Post` 对每个动作每 tick 发送，27 个动作 × 每玩家 × 20 TPS；订阅时只做 O(1) 判断，禁止实体扫描。
5. **CONFIRMED**：`Parkourability.getList()` 返回内部列表引用；外部修改会破坏状态机。
6. **CONFIRMED**：`ActionList` 的 ID 是声明顺序，跨版本可能变化；持久化数据/网络协议中不要存 ParCool 的数字 ID。

---

## 9. 可复现验证

### 9.1 反编译

```powershell
$jar='D:\PCL 正式版 2.10.3\dc\.minecraft\versions\DeceasedCraft_CB\mods\ParCool-1.20.1-3.4.3.3.jar'
$out="$env:TEMP\pc_audit\src_parcool"
# 先解压目录，再用 IntelliJ Fernflower 反编译目录
& 'C:\Program Files\Java\jdk-17\bin\java.exe' -cp 'D:\IntelliJ IDEA 2024.1.7\plugins\java-decompiler\lib\java-decompiler.jar' `
  org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler -dgs=1 -asc=1 "$env:TEMP\pc_audit\x_parcool" $out
```

### 9.2 关键类核对

```powershell
javap -p -classpath $jar com.alrex.parcool.common.action.Action
javap -p -classpath $jar com.alrex.parcool.common.action.ActionProcessor
javap -p -classpath $jar com.alrex.parcool.common.capability.Parkourability
javap -p -classpath $jar com.alrex.parcool.api.unstable.action.ParCoolActionEvent
javap -v -classpath $jar com.alrex.parcool.api.unstable.action.ParCoolActionEvent`$TryToStart
javap -v -classpath $jar com.alrex.parcool.api.unstable.action.ParCoolActionEvent`$TryToContinue
```

### 9.3 事件取消语义

```powershell
javap -c -p -classpath 'D:\PCL 正式版 2.10.3\dc\.minecraft\libraries\net\minecraftforge\eventbus\6.0.5\eventbus-6.0.5.jar' net.minecraftforge.eventbus.api.Event
# isCancelable() -> EventListenerHelper.isCancelable(getClass())
# setCanceled(boolean) -> 先调用虚方法 isCancelable()
```

---

## 10. 结论汇总

- **CONFIRMED**：ParCool 3.4.3.3 有完整可用的动作状态读取面（Capability + `isDoing()` + 事件），无需 Mixin 即可做只读集成。
- **CONFIRMED**：动作开始/继续的可取消能力只在客户端路径完整；服务端对客户端上报的 `Start` 无条件执行。
- **CONFIRMED**：`ParCoolActionEvent` 与动画 API 明确标记为 `unstable`，必须锁定 3.4.3.3 使用。
- **CONFIRMED**：服务端无法从 `Vault`/`Slide`/`WallJump` 等对象拿到客户端才有的视觉参数（动画类型、滑动向量等）。
- **PARTIALLY_CONFIRMED**：`Parkourability`/`Action` 虽 public，但位于 `common` 内部包，适合只读使用，不适合作长期 API 契约。
- **PARTIALLY_CONFIRMED**：Compatibility Addon 1.1 与 ParCool 3.4.3.3 的运行时组合——专用服务器启动加载成功（无补丁/类加载崩溃），但客户端实机动画与玩法未测试。

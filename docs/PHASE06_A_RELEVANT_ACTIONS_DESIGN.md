# PHASE06_A_RELEVANT_ACTIONS_DESIGN.md

> Phase 06-A — 八种特殊攻击流派相关动作审查与适配设计  
> 分支：`gate-2.14-test-fixes`  
> 当前 HEAD：`5e6de1f`（Phase05.1 已提交；本报告不修改源码）  
> 结论：**READY_FOR_CHATGPT_REVIEW**

---

## 1. 审查范围与证据方法

本报告只做源码/API 审查和设计，不修改 Java、测试、Gradle、网络协议或第三方 JAR。

实际检查：

```text
Git branch / status / log
ParCool-1.20.1-3.4.3.3.jar
  jar tf
  javap -p
  javap -p com.alrex.parcool.common.action.Action
  javap -p com.alrex.parcool.common.action.InstantAction
  javap -p com.alrex.parcool.common.capability.Parkourability
  javap -p com.alrex.parcool.api.unstable.action.ParCoolActionEvent
  javap -p ActionList
现有 adapter / tracker / selection / event bridge
PARCOOL_API_AUDIT.md
AI_PROJECT_RULES.md
```

ParCool SHA-256：

```text
2DDEF8752B5A1428035B95F29E62C81E33B80D37851681E1B928D8A01615C6E7
```

---

## 2. 当前源码结构

### 2.1 动作枚举

`ParkourActionType` 当前只有：

```text
FAST_RUN
SLIDE
VAULT
WALL_RUN
WALL_RUN_HORIZONTAL
WALL_RUN_VERTICAL
WALL_JUMP
```

没有：

```text
DIVE
SKY_DIVE
DODGE
HANG_DOWN
CLIMB_UP
```

也没有 `FALL`。`FALL` 由 `FallSnapshot`/`MotionSnapshot.fallSnapshot` 表示，不是 ParCool Action。

### 2.2 映射

`ParCoolActionMapping` 当前只映射：

```text
FastRun                 -> FAST_RUN
Slide                   -> SLIDE
Vault                   -> VAULT
HorizontalWallRun       -> WALL_RUN_HORIZONTAL
VerticalWallRun         -> WALL_RUN_VERTICAL
WallJump                -> WALL_JUMP
```

### 2.3 采样架构

`ParCoolActionAdapter.onPlayerTick` 每玩家每 tick：

1. 取一次 `Parkourability.get(player)`；
2. 调 `mapping.activeTypes(parkourability)`；
3. `ParkourActionTracker.onTick`；
4. `ParkourActionSelection.select`；
5. `MotionTracker.tick`；
6. 记录日志。

没有每种动作独立 Tick 监听器，没有全世界实体扫描，没有每动作网络包。

### 2.4 当前状态机

`ParkourActionTracker`：

```text
STARTED -> ACTIVE -> FINISHED / CANCELLED / INTERRUPTED / UNKNOWN
TERMINAL_TTL_TICKS = 40
SYNC_LOST_TIMEOUT_TICKS = 2
WALL_RUN 聚合查询
```

`ParkourActionSelection` 优先级保持不变：

```text
WALL_RUN > WALL_JUMP > VAULT > SLIDE > FAST_RUN
```

---

## 3. ParCool 实际 API 证据

### 3.1 基类与能力

`javap` 确认：

```java
public abstract class com.alrex.parcool.common.action.Action {
    public boolean isDoing();
    public int getDoingTick();
    public int getNotDoingTick();
    public int getTickFromLastStarted();
    public void start(Player, Parkourability, ByteBuffer, IStamina);
    public void finish(Player);
    public abstract boolean canStart(...);
    public abstract boolean canContinue(...);
}
```

```java
public class com.alrex.parcool.common.capability.Parkourability {
    public static Parkourability get(Player);
    public <T extends Action> T get(Class<T>);
    public List<Action> getList();
    public boolean isDoingAny(Class<? extends Action>...);
}
```

所有 11 个候选动作的 `javap` 输出均为：

```text
extends com.alrex.parcool.common.action.Action
```

它们都不是 `InstantAction`。`InstantAction` 只被 `StartSwimByCrawl` 使用，不属于本阶段 11 个候选。

### 3.2 事件

`ParCoolActionEvent` 实际存在：

```text
ParCoolActionEvent.Start.Pre
ParCoolActionEvent.Start.Post
ParCoolActionEvent.Finish.Pre
ParCoolActionEvent.Finish.Post
ParCoolActionEvent.StopEvent
ParCoolActionEvent.TryToStart
ParCoolActionEvent.TryToContinue
```

`ParCoolActionEvent` 方法：

```java
public Player getPlayer();
public Action getAction();
```

事件位于 `com.alrex.parcool.api.unstable.action`，必须在 3.4.3.3 锁定版本下使用。

### 3.3 服务端状态可信度

现有 `PARCOOL_API_AUDIT.md` 已核实：

- 动作开始/继续主要由客户端 `ActionProcessor` 判定；
- 专用服务端收到 `SyncActionStateMessage` 后直接 `action.start(...)`，不重新执行 `canStart`；
- 服务端 `isDoing()` 是客户端上报后的镜像；
- `Vault.getCurrentAnimation()`、`Slide.getSlidingVector()` 等只写在客户端回调，服务端拿不到；
- 不能把 ParCool 服务端动作状态当作不可伪造的物理事实。

本设计继续使用：

```text
客户端动作：CLIENT_LOCAL / MEDIUM
服务端同步动作：PARCOOL_SYNCED / LOW
服务端运动：SERVER_NATIVE / HIGH
```

---

## 4. 11 个候选动作逐项审查

### 4.1 FastRun

- 类：`com.alrex.parcool.common.action.impl.FastRun`
- 基类：`Action`
- API：`isDoing()`；`getDashTick(AdditionalProperties)`、`getNotDashTick(...)`、`getLastDashTick()`。
- 生命周期：持续动作；`canContinue` 驱动；Start/Finish 由通用 Action 流程发送。
- 客户端：可读；`getDashTick` 是客户端状态字段，不能作为服务端事实。
- 服务端：可通过 `Parkourability.get(player).get(FastRun.class).isDoing()` 读取；来源 LOW。
- 战斗价值：高；适合“动能斩”触发；需要服务端水平速度/方向校验。
- 附加属性：`getLastDashTick()` 可辅助判断冲刺段，但服务端时序不可靠。

**分类：CONFIRMED**  
**识别：加入**  
**特殊攻击触发：可以作为候选，但不能单凭 isDoing 结算伤害**  
**运动扩展：不需要，现有 horizontalSpeed 足够**  
**服务端验证：必须**

### 4.2 Dive

- 类：`com.alrex.parcool.common.action.impl.Dive`
- 基类：`Action`
- API：`isDoing()`；`getPlayerYSpeed(float)`；`canStart/canContinue`。
- 生命周期：下落/俯冲期间持续；`onStartInLocalClient`、`onStopInLocalClient` 等存在；Start/Finish 通用事件。
- 客户端：可读；`getPlayerYSpeed` 可用于本地表现。
- 服务端：`isDoing()` 可读，但同步值 LOW；不能用客户端 Y 速度作为权威伤害条件。
- 战斗价值：高；适合“重力处刑”。
- 附加属性：精确下落速度不应依赖该 getter；以服务端 `MotionSnapshot.velocityY`/fall 数据为准。

**分类：CONFIRMED**  
**识别：加入**  
**触发：可作为候选**  
**运动扩展：不需要，现有 fall/velocity 足够**  
**服务端验证：必须**

### 4.3 SkyDive

- 类：`com.alrex.parcool.common.action.impl.SkyDive`
- 基类：`Action`
- API：只有 `canStart/canContinue/onWorkingTickInLocalClient/onRenderTick` 等；没有公开速度 getter。
- 生命周期：下落姿态持续；`isDoing()` 可用。
- 客户端：可读。
- 服务端：同步 `isDoing()` 可读，LOW。
- 战斗价值：与 Dive/Fall 重叠；单独作为“重力处刑”触发会重复计流派。
- 附加属性：无可靠的专用方向/速度 API，必须依赖运动快照。

**分类：DEFERRED**  
**识别：可加入辅助状态，但不作为独立攻击触发**  
**触发：暂不作为独立触发；可由 Dive/Fall 覆盖**  
**运动扩展：不需要**  
**服务端验证：未来若启用则需要**

### 4.4 Vault

- 类：`com.alrex.parcool.common.action.impl.Vault`
- 基类：`Action`
- API：`getCurrentAnimation()`；`MAX_TICK`；`canStart/canContinue`。
- 生命周期：短时动作，`MAX_TICK` 有界；Start/Finish 通用事件。
- 客户端：动画类型可读。
- 服务端：`getCurrentAnimation()` 通常为 `null`；只能可靠读 `isDoing()`，LOW。
- 战斗价值：高；适合“越障斩”。
- 附加属性：动画类型不能用于服务端伤害判断。

**分类：CONFIRMED**  
**识别：加入**  
**触发：可以作为候选**  
**运动扩展：不需要；可增加阶段标签，但不能改 MotionSnapshot 语义**  
**服务端验证：必须**

### 4.5 HorizontalWallRun

- 类：`com.alrex.parcool.common.action.impl.HorizontalWallRun`
- 基类：`Action`
- API：`isDoing()`；`saveSynchronizedState/restoreSynchronizedState`；`bodyYaw` 通过同步状态传递。
- 生命周期：持续墙跑，`canContinue`；Finish 通用事件。
- 客户端：可读。
- 服务端：`isDoing()` 与同步 `bodyYaw` 可见；墙方向字段为私有，无公开 getter；LOW。
- 战斗价值：高；适合“磁轨斩”水平分支。
- 附加属性：服务端墙方向不可靠；必须用玩家朝向/运动方向辅助，不能伪造墙面法线。

**分类：CONFIRMED**  
**识别：加入**  
**触发：可以作为候选**  
**运动扩展：不需要新增私有方向字段；使用现有方向/速度**  
**服务端验证：必须**

### 4.6 VerticalWallRun

- 类：`com.alrex.parcool.common.action.impl.VerticalWallRun`
- 基类：`Action`
- API：`isDoing()`；`wallDirection` 为私有；`playerYSpeed` 为私有；无公开方向 getter。
- 生命周期：持续墙跑；Finish 通用事件。
- 客户端：可读状态；方向主要在客户端。
- 服务端：只有 `isDoing()` 可靠，LOW；不能把私有字段当 API。
- 战斗价值：高；适合“磁轨斩”垂直分支。
- 附加属性：不要通过反射读取私有字段。

**分类：CONFIRMED（仅 isDoing）**  
**识别：加入**  
**触发：可以作为候选，但需服务端运动方向校验**  
**运动扩展：不需要**  
**服务端验证：必须**

### 4.7 Slide

- 类：`com.alrex.parcool.common.action.impl.Slide`
- 基类：`Action`
- API：`isDoing()`；`getSlidingVector()`；`canStart/canContinue`。
- 生命周期：持续滑行；Start/Finish 通用事件。
- 客户端：`getSlidingVector()` 可用。
- 服务端：`getSlidingVector()` 通常不可用/为客户端值；只有 `isDoing()` 可作 LOW 证据。
- 战斗价值：高；适合“低位切割”。
- 附加属性：滑行方向必须用服务端运动方向，不使用客户端 vector。

**分类：CONFIRMED**  
**识别：加入**  
**触发：可以作为候选**  
**运动扩展：不需要**  
**服务端验证：必须**

### 4.8 WallJump

- 类：`com.alrex.parcool.common.action.impl.WallJump`
- 基类：`Action`
- API：`isDoing()`；`justJumped()`；`isInputDone()`；`canStart`。
- 生命周期：短时/起跳动作；`justJumped` 只在起始附近有效，下一 tick 可能清除；不能建模为长 ACTIVE。
- 客户端：可读。
- 服务端：Start 当 tick 可能读到 `justJumped()`；跨 tick 不可靠；LOW。
- 战斗价值：高；适合“弹射斩”。
- 附加属性：不能依赖动画类型；以运动速度方向作为服务端验证。

**分类：CONFIRMED（短窗）**  
**识别：加入，但需短窗标记**  
**触发：可以作为候选**  
**运动扩展：需要记录起跳 tick/短窗，不是新运动字段**  
**服务端验证：必须**

### 4.9 Dodge

- 类：`com.alrex.parcool.common.action.impl.Dodge`
- 基类：`Action`；不是 `InstantAction`。
- API：`isDoing()`；`getCoolTime()`；`getSuccessivelyCoolTick()`；`isInSuccessiveCoolDown(ActionInfo)`；`MAX_TICK`。
- 生命周期：短时持续动作；有冷却/连续闪避状态；Finish 通用事件。
- 客户端：可读。
- 服务端：`isDoing()` 可读，LOW；冷却值不是服务端权威。
- 战斗价值：中高；“惯性回斩”可作为触发，但 Dodge 本身是防御动作，不能把它变成伤害免疫之外的额外伤害。
- 附加属性：冷却不可用于服务端奖励判定；需要运动速度/输入窗口。

**分类：CONFIRMED**  
**识别：加入**  
**触发：可作为候选，但优先级低于实际攻击动作**  
**运动扩展：不需要**  
**服务端验证：必须**

### 4.10 HangDown

- 类：`com.alrex.parcool.common.action.impl.HangDown`
- 基类：`Action`
- API：`isDoing()`；`getArmSwingAmount()`、`getBodySwingAngleFactor()`、`isOrthogonalToBar()`、`getHangingBarAxis()`；`saveSynchronizedState/restoreSynchronizedState`。
- 生命周期：持续悬挂；Finish 通用事件。
- 客户端：可读。
- 服务端：动作状态可读，部分同步字段存在；仍为 LOW。
- 战斗价值：中；“悬挂处决”需要目标距离/姿态校验，不能仅凭 isDoing。
- 附加属性：BarAxis 可用于表现/辅助，不可单独作为伤害依据。

**分类：CONFIRMED**  
**识别：加入**  
**触发：可作为候选**  
**运动扩展：可增加姿态标签，但需验证同步字段**  
**服务端验证：必须**

### 4.11 ClimbUp

- 类：`com.alrex.parcool.common.action.impl.ClimbUp`
- 基类：`Action`
- API：`isDoing()`；`canStart/canContinue`；`onStartInLocalClient/OtherClient`；没有专用公开属性。
- 生命周期：短时攀爬动作；Finish 通用事件；具体持续 tick 未由公开 getter 保证。
- 客户端：可读。
- 服务端：`isDoing()` LOW。
- 战斗价值：低到中；“悬挂处决”可由 HangDown 承担，ClimbUp 作为辅助状态。
- 附加属性：没有可靠方向/进度 getter。

**分类：PARTIALLY_CONFIRMED / DEFERRED for trigger**  
**识别：可加入辅助识别**  
**触发：暂不作为独立触发**  
**运动扩展：不需要**  
**服务端验证：未来启用则必须**

---

## 5. 生命周期表

| 动作 | 类型 | 起始 | 结束 | 可打断 | 主要风险 |
|---|---|---|---|---|---|
| FastRun | 持续 | Start.Pre/Post | Finish.Pre/Post | 是 | 客户端可伪造持续 |
| Dive | 下落持续 | Start | Finish/Stop | 是 | 与 Fall 重叠 |
| SkyDive | 下落持续 | Start | Finish/Stop | 是 | 无专用速度 API |
| Vault | 短时有界 | Start | Finish | 是 | 服务端动画为 null |
| HorizontalWallRun | 持续 | Start | Finish | 是 | 私有方向无 getter |
| VerticalWallRun | 持续 | Start | Finish | 是 | 私有方向无 getter |
| Slide | 持续 | Start | Finish | 是 | 服务端 vector 不可靠 |
| WallJump | 短窗 | Start | Finish | 是 | `justJumped` 跨 tick 失效 |
| Dodge | 短时 | Start | Finish | 是 | 冷却值不可作权威 |
| HangDown | 持续 | Start | Finish | 是 | 需目标姿态校验 |
| ClimbUp | 短时 | Start | Finish | 是 | 无专用进度属性 |

说明：

- 所有 11 个类都继承 `Action`，未发现 `InstantAction` 子类。
- `Start.Pre` / `Finish.Pre` 由 ParCool 的通用 Action 流程发送；具体帧内顺序应以现有 `ParCoolActionEvent` 监听和 `ActionProcessor` 调用链为准。
- 事件丢失、同步延迟、STARTED 无 FINISHED 的最终恢复仍由现有 `ParkourActionTracker` 的 `SYNC_LOST` / TTL 机制处理，不新增第二套状态机。

---

## 6. 客户端/服务端差异

| 项目 | 客户端 | 集成服务器 | 专用服务器 |
|---|---|---|---|
| `isDoing()` | 可读 | 可读，来自同步/本地 | 可读，来自 C2S 同步 |
| `Parkourability.get` | 可用 | 可用 | 可用 |
| Start/Finish 事件 | 有 | 有 | 有 |
| 动作视觉参数 | 通常可用 | 可能无 | 通常无 |
| FastRun dash 字段 | 可用 | 不可靠 | 不可靠 |
| Slide sliding vector | 可用 | 通常无 | 通常无 |
| Vault animation | 可用 | 通常 null | 通常 null |
| WallRun 私有方向 | 客户端内部 | 不一定 | 不一定 |
| WallJump justJumped | 起始窗口 | 起始窗口 | 起始窗口 |
| 动作同步信任 | MEDIUM | LOW | LOW |
| 运动快照 | CLIENT_LOCAL/MEDIUM | SERVER_NATIVE/HIGH | SERVER_NATIVE/HIGH |

结论：

- ParCool 服务端动作状态不是独立物理验证。
- 任何特殊攻击触发必须以服务端运动快照、攻击事件和距离/目标验证为组合条件。
- 客户端动作只可用于本地动画/提示，不可作为伤害权威。

---

## 7. 八种流派与动作对应

| 流派 | 主动作 | 辅助动作 | 可靠性判断 |
|---|---|---|---|
| 动能斩 | FastRun | 无 | 识别可靠；需速度校验 |
| 重力处刑 | Dive | SkyDive、Fall | Dive 优先；SkyDive 辅助；Fall 非 ParCool |
| 越障斩 | Vault | 无 | 短暂窗口；需快照时间 |
| 磁轨斩 | HorizontalWallRun、VerticalWallRun | WALL_RUN 聚合 | 方向服务端不可靠，需运动方向 |
| 低位切割 | Slide | 无 | 识别可靠；滑行 vector 不可作权威 |
| 弹射斩 | WallJump | 无 | 短窗；需起跳 tick |
| 惯性回斩 | Dodge | 无 | 防御动作，触发需限定 |
| 悬挂处决 | HangDown | ClimbUp | 需目标距离/姿态；ClimbUp 仅辅助 |

`WALL_RUN` 不是独立 ParCool 类，只是水平/垂直聚合查询；不得新增同义映射或把它当作独立 Action。

`FALL` 不是 ParCool Action；只能来自 `FallSnapshot`，不得加入 `ParCoolActionMapping`。

---

## 8. 动作分类结果

| 动作 | 分类 | 加入识别 | 特殊攻击触发 | 运动字段扩展 | 服务端额外验证 |
|---|---|---|---|---|---|
| FastRun | CONFIRMED | 是 | 是 | 否 | 是 |
| Dive | CONFIRMED | 是 | 是 | 否 | 是 |
| SkyDive | DEFERRED | 辅助 | 否（由 Dive/Fall 覆盖） | 否 | 未来需要 |
| Vault | CONFIRMED | 是 | 是 | 阶段标签可选 | 是 |
| HorizontalWallRun | CONFIRMED | 是 | 是 | 否 | 是 |
| VerticalWallRun | CONFIRMED（仅 isDoing） | 是 | 是 | 否 | 是 |
| Slide | CONFIRMED | 是 | 是 | 否 | 是 |
| WallJump | CONFIRMED（短窗） | 是 | 是 | 起跳 tick | 是 |
| Dodge | CONFIRMED | 是 | 有条件 | 否 | 是 |
| HangDown | CONFIRMED | 是 | 有条件 | 姿态标签可选 | 是 |
| ClimbUp | PARTIALLY_CONFIRMED | 辅助 | 否 | 否 | 未来需要 |

不得把“CONFIRMED”写成“可以安全触发伤害”；CONFIRMED 只表示 API/生命周期可达。

---

## 9. 多人/性能成本

### 9.1 当前成本

每玩家每 tick：

```text
Parkourability.get(player)              1 次
mapping.activeTypes(...)                6 次 isDoing()
tracker.onTick                          O(active types)
selection select                        O(5)
motion.tick                             1 次
```

没有每动作 tick 监听器、没有实体扫描、没有每动作网络包。

### 9.2 扩展到 11 个动作

如果只扩展映射：

```text
mapping.activeTypes: 6 -> 11 次 isDoing()
```

每玩家每 tick 增加 5 次轻量方法调用，仍为 O(11)，无新增集合/线程/网络包。

### 9.3 推荐约束

- 复用 `ParCoolActionAdapter.onPlayerTick`，不新增 PlayerTick 监听器；
- 不订阅 `ParCoolActionEvent.Tick.Pre/Post` 做每动作业务；
- 只使用现有 Start/Finish 和 PlayerTick；
- 新动作结果使用 `EnumSet`，有界；
- 不把 11 个动作都写入 `MotionSnapshot` 主字段；
- 不新增全局 Map/List；
- 清理沿用现有死亡/登出/维度/卸载/停服路径。

---

## 10. 计划修改文件（仅规划，未修改）

后续 Phase06-B 若批准：

```text
ParkourActionType.java
  增加 DIVE/SKY_DIVE/DODGE/HANG_DOWN/CLIMB_UP（是否全部取决于分类）
ParCoolActionMapping.java
  映射 Dive/SkyDive/Dodge/HangDown/ClimbUp
ParCoolActionAdapter.java
  复用 activeTypes，不新增 tick
ParkourActionSelection.java
  不改变现有 5 项优先级；新动作不自动成为主状态
CombatAttackContext.java
  仅在需要时增加有界动作证据摘要，不复制全部 Action
MotionSnapshot.java
  不改变现有字段语义
```

不得修改：

```text
ParCool/Better Combat 原始 JAR
H09/C09 语义
现有网络协议字段
F10 HUD 协议
```

---

## 11. 测试计划

### 单元测试

1. 11 个类均可通过 `Class.forName` 从锁定 JAR 加载。
2. 所有候选都 `extends Action`。
3. 映射 `typeOf` 对每个类返回稳定类型，未知类返回 empty。
4. `activeTypes` 只返回有界 `EnumSet`，不泄漏内部集合。
5. Tracker 对新增类型的 START/ACTIVE/FINISH/INTERRUPTED/UNKNOWN 语义与现有类型一致。
6. `WALL_RUN` 聚合仍不创建独立 Action。
7. `FALL` 不会被错误加入 ParCool 映射。
8. 服务端 trust 仍为 LOW，客户端不为 SERVER_NATIVE。
9. 11 个动作不会各自创建 PlayerTick 监听器。
10. SDK 版本不匹配时 adapter 仍禁用。

### 集成/人工

- 真实客户端按动作触发；
- 专用服务器观察同步状态；
- 多人玩家隔离；
- 死亡/登出/维度/停服；
- 真实攻击观察与 HUD；
- 性能采样。

真实 Forge/ParCool 事件、真实 PartEntity、多人仍属于独立测试，不由普通 JUnit 代替。

---

## 12. 风险与未验证事项

1. `ClimbUp`/`SkyDive` 的精确生命周期窗口未由公开 getter 明确，需运行时日志验证。
2. `Vault` 的 `MAX_TICK` 数值来自公开字段/既有审计，本报告未重新反编译常量值。
3. `WallJump.justJumped()` 的跨 tick 行为需运行时确认；当前只按“短窗”设计。
4. `HorizontalWallRun`/`VerticalWallRun` 私有方向字段没有公开 API，禁止反射。
5. 服务端同步动作可被客户端伪造；不能单独作为伤害触发。
6. 真实多人延迟、动作事件丢失、STARTED 无 FINISHED 仍需实测。
7. 本报告不验证特殊攻击伤害、动画、词条或网络实现。

---

## 13. 推荐 Phase06-B 顺序

1. 只增加动作类型和只读映射；不改变 selection 优先级。
2. 用现有 adapter/tracker 做 11 类状态单元测试。
3. 增加有界动作证据摘要，不复制 Action 对象。
4. 先做 FastRun/Slide/Vault/WallRun/WallJump 五个已有流派的证据丰富化。
5. 再做 Dive/SkyDive/Fall 的重力动作优先级。
6. 再做 Dodge、HangDown、ClimbUp 的辅助状态。
7. 最后才评估特殊攻击触发与伤害，且必须经过服务端验证。

---

## 14. 最终状态

```text
DESIGNED：YES
IMPLEMENTED：NO
COMPILED：NO（本阶段不修改代码）
UNIT-TESTED：NO（本阶段不修改代码）
CLIENT-STARTED：NO（本阶段不涉及）
CLIENT-TESTED：NO
SERVER-STARTED：NO（本阶段不涉及）
SERVER-TESTED：NO
MULTIPLAYER-TESTED：NO
VERIFIED：NO
```

最终：

```text
READY_FOR_CHATGPT_REVIEW
```

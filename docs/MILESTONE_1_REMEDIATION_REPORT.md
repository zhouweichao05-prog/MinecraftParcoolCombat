# MILESTONE_1_REMEDIATION_REPORT.md

> Milestone 1 整改 — 动作生命周期、窗口、分类器接入  
> 分支：`gate-2.14-test-fixes`  
> HEAD：`dd1a50aadac70e3db7dd2b056a15a15cd1390e28`  
> 未提交、未推送。

---

## 1. 整改前问题

1. `SpecialAttackClassifier` 使用 `EnumSet.copyOf(active)`，普通空集合可能抛异常。
2. `ParCoolActionAdapter.onPlayerTick` 仍每 Tick 调用 `activeTypes()` / `isDoing()`。
3. `SpecialAttackWindowTracker` 没有被 FINISH 事件调用。
4. 分类器没有接入真实攻击事件。
5. 特殊攻击窗口没有接入玩家生命周期清理。
6. 特殊攻击没有实际伤害接入。

---

## 2. 实际修改

### P0 空集合修复

`SpecialAttackClassifier.classify` 改为：

```java
EnumSet<ParkourActionType> actions = EnumSet.noneOf(ParkourActionType.class);
if (active != null) {
    actions.addAll(active);
}
```

测试覆盖：

- null；
- `Set.of()`；
- `Collections.emptySet()`；
- `HashSet`；
- 正常 `EnumSet`。

### P1 事件驱动生命周期

`ParkourActionAdapter` 已删除每 Tick 的：

```text
Parkourability.get(player)
mapping.activeTypes(parkourability)
actions.onTick(gameTime, doing)
```

改为：

```text
actions.onEventOnlyTick(gameTime)
```

Start/Finish 通过现有 `ParCoolActionEvent.Start.Pre` / `Finish.Pre` 进入 `ParkourActionTracker`。

为防事件丢失永久 ACTIVE，新增：

```text
MAX_EVENT_ONLY_ACTIVE_TICKS = 1200
```

`onEventOnlyTick` 只做有界老化，不读取全部 ParCool 动作，不调用 `isDoing()`。

### P2 窗口接入

`ParCoolActionAdapter.onActionFinish` 在状态机结束后：

```text
ParkourActionTracker.onFinish
  -> terminal phase
  -> SpecialAttackService.onActionFinish
  -> SpecialAttackWindowTracker.recordFinish
```

只接受：

```text
ParkourActionPhase.FINISHED
```

CANCELLED / INTERRUPTED / UNKNOWN 不创建窗口。

窗口：

```text
WINDOW_TICKS = 12
每个玩家最多 2 个
一次攻击消费一次
过期/清理移除
```

### P3 真实攻击分类

`CombatBridge.onAttackEntity` 接到真实服务端 `AttackEntityEvent` 后调用：

```java
SpecialAttackService.onAttack(player, CombatAttackIdGenerator.nextId());
```

分类器读取：

- 事件驱动 Tracker 的活动动作；
- WallJump/Dodge 窗口；
- 服务端 MotionSnapshot；
- 服务端 `player.fallDistance`；
- 服务端 `onGround`。

结果写入：

```text
SpecialAttackService.latest(player)
```

并消费被接受的一次性窗口。

没有在 `LivingHurtEvent` 增加伤害，没有第二套攻击入口。

### P4 物理数据

本阶段保留现有 `MotionTracker` 及 HUD 所需历史快照。

- 攻击分类优先使用攻击时读取的 `MotionSnapshot` 和服务端玩家 `fallDistance`/`onGround`。
- Fall 距离仍依赖现有服务端数据。
- 没有新增第二套全量物理采样器。
- `MotionTracker` 的完整删除/缩减未完成，避免破坏 HUD。

### P5 八种规则

分类器保留八种结果：

```text
KINETIC, GRAVITY, VAULT, WALL_RUN, SLIDE,
WALL_JUMP, DODGE, HANG_DOWN
```

DIVE 优先于 FALL；SkyDive/ClimbUp 未加入正式映射；FALL 不是 ParCool 动作。

---

## 3. Better Combat 安全结论

```text
CLASSIFICATION_VERIFIED = NO（单元分类已验证，真实游戏攻击链未验证）
EFFECTS_CALCULATED = YES
DAMAGE_APPLICATION_VERIFIED = NO / DEFERRED
KNOCKBACK_APPLICATION_VERIFIED = NO / DEFERRED
SLOW_APPLICATION_VERIFIED = NO / DEFERRED
```

原因：

- Better Combat `orderingKnown=false`；
- 服务端原始 Handler 归属和执行顺序未确认；
- 不能在 Forge 普通伤害事件上重复增加伤害。

因此没有实现第二套伤害、击退或减速结算。

---

## 4. 测试结果

```text
compileJava：BUILD SUCCESSFUL
test：165/165 passed
build：BUILD SUCCESSFUL
jar：184,948 bytes
```

新增/修正测试包括：

```text
emptyAndNullActiveCollectionsAreSafe
canceledInterruptedAndUnknownDoNotCreateWindows
windowTrackerIsBoundedAndClearable
wallJumpWindowIsTwelveTicksAndConsumesOnce
eightStylesCanBeClassified
...
```

`canceledOrInterruptedActionDoesNotCreateWindow` 已由新的真实 phase 测试取代，不再保留名不副实的断言。

---

## 5. 启动状态

```text
CLIENT-STARTED：NO
CLIENT-TESTED：NO
SERVER-STARTED：NO
SERVER-TESTED：NO
MULTIPLAYER-TESTED：NO
```

本轮未执行 runClient/runServer；不能把单元测试或编译当作游戏功能验证。

---

## 6. 仍未完成

1. 未在真实 ParCool 客户端/专用服务器验证 Start/Finish 事件顺序。
2. `SpecialAttackService.onActionFinish` 只根据 Tracker 的 terminal phase 判断正常结束；服务端取消/中断事件是否总能被 Tracker 正确标记仍需实测。
3. 特殊攻击已分类并记录，但未显示在 F10 HUD。
4. 未实现实际伤害、击退、减速应用。
5. MotionTracker 仍每 Tick 采集完整物理数据；仅攻击分类使用服务端当前值。
6. 未实现真实多人、死亡/登出/维度/停服测试。
7. 没有动画。

---

## 7. Git

```text
分支：gate-2.14-test-fixes
HEAD：dd1a50aadac70e3db7dd2b056a15a15cd1390e28
```

工作区有未提交修改；本轮未 commit、未 push、未切换分支、未合并 main。

---

## 8. 最终状态

```text
DESIGNED：YES
IMPLEMENTED：PARTIAL
COMPILED：YES
UNIT-TESTED：YES（165/165）
CLIENT-STARTED：NO
CLIENT-TESTED：NO
SERVER-STARTED：NO
SERVER-TESTED：NO
MULTIPLAYER-TESTED：NO
VERIFIED：NO
DEFERRED：DAMAGE/KNOCKBACK/SLOW APPLICATION
```

当前系统已能在真实服务端 `AttackEntityEvent` 中分类并记录特殊攻击，但仍不是完整的“实际伤害特殊攻击系统”。

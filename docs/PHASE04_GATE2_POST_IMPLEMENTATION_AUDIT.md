# PHASE04_GATE2_POST_IMPLEMENTATION_AUDIT.md

> Phase 04 Gate 2 — 实施后只读审计  
> 审计对象：`D:\MinecraftParcoolCombat`  
> 审计依据：`docs/PHASE04_GATE2_IMPLEMENTATION_PLAN.md`、`docs/PHASE04_GATE2_IMPLEMENTATION_REPORT.md`、当前实际源码与测试  
> 本审计不修改任何源码、测试、依赖或第三方 JAR。

---

## 1. Git 分支、HEAD、工作区状态

现场读取：

```text
分支：master
HEAD：170ea5bc29c3660525431263d07c3b79a9094f69
已跟踪文件改动：无（git diff --stat 为空）
未跟踪：
  docs/PHASE04_GATE2_IMPLEMENTATION_PLAN.md
  docs/PHASE04_GATE2_IMPLEMENTATION_REPORT.md
  docs/logs/phase04-gate2 日志
  src/main/java/com/MrGhost/parkourCombat/combat/
  src/main/java/com/MrGhost/parkourCombat/compat/
  src/test/java/com/MrGhost/parkourCombat/combat/
  src/test/java/com/MrGhost/parkourCombat/compat/
Git commit：无
```

未修改第三方 JAR、依赖版本、`build.gradle`、`gradle.properties`、`mods.toml`。

---

## 2. 实际检查的源码文件

### Combat

```text
src/main/java/com/MrGhost/parkourCombat/combat/
  CombatAttackPhase.java
  CombatAttackResult.java
  CombatAttackEndReason.java
  CombatAttackSource.java
  CombatTargetTrust.java
  CombatTargetResolution.java
  CombatTargetCoverage.java
  CombatTargetMatch.java
  CombatWeaponSnapshot.java
  CombatAttackRequest.java
  CombatAttackIdGenerator.java
  CombatAttackContext.java
  CombatRequestedTarget.java
  CombatDamageEventType.java
  CombatDamageSourceClassification.java
  CombatDamageEventObservation.java
  CombatTargetDamageObservation.java
  CombatAttackSession.java
  CombatAttackTracker.java
  CombatBridge.java
  CombatWeaponProvider.java
```

### Compat

```text
src/main/java/com/MrGhost/parkourCombat/compat/bettercombat/
  BetterCombatCompat.java
  BetterCombatAttackAdapter.java
```

### Tests

```text
src/test/java/com/MrGhost/parkourCombat/combat/CombatAttackRequestTest.java
src/test/java/com/MrGhost/parkourCombat/combat/CombatTargetDamageObservationTest.java
src/test/java/com/MrGhost/parkourCombat/compat/bettercombat/BetterCombatAttackAdapterTest.java
```

### 相关既有类型

```text
ParkourAdapterEvents
ParCoolActionAdapter
MotionSnapshot
ParkourActionState
FallSnapshot
```

---

## 3. Handler 安装与包装流程

### 3.1 实际安装入口

`CombatBridge.onServerStarted`：

```java
src/main/java/com/MrGhost/parkourCombat/combat/CombatBridge.java:160-171
```

流程：

```text
ServerStartedEvent
  -> ModList.isLoaded("bettercombat")
  -> Class.forName(BetterCombatCompat)
  -> BetterCombatCompat.tryInstall()
```

### 3.2 Handler Map 获取

`BetterCombatCompat.tryInstall`：

```java
src/main/java/com/MrGhost/parkourCombat/compat/bettercombat/BetterCombatCompat.java:51-102
```

实际获取：

```java
ServerPlayNetworking.HANDLERS
  .get(Packets.C2S_AttackRequest.ID)
```

只替换：

```text
Packets.C2S_AttackRequest.ID
```

没有替换 `AttackAnimation`、`ConfigSync`、`WeaponRegistrySync`。

### 3.3 原始 Handler 链

首次安装：

```text
current (preexisting)
  -> HandlerWrapper.current
  -> map.put(C2S_AttackRequest.ID, wrapper)
```

`HandlerWrapper.receive` 正常路径：

```java
src/main/java/com/MrGhost/parkourCombat/compat/bettercombat/BetterCombatCompat.java:169-180
original.receive(...)
```

原 Handler 链保留，没有使用 `registerGlobalReceiver` 覆盖。

### 3.4 安装状态

实际运行日志：

```text
[BetterCombatCompat] installed with orderingUnknown=true
```

当前源码：

```text
BetterCombatCompat.java:93-100
wrappedOriginal = current
wrappedOrderingKnown = false
handlerIdentity = UNKNOWN_PREEXISTING
installationState = DEGRADED_OR_UNKNOWN
```

`AUDITED_BETTER_COMBAT_ORIGINAL` 分支没有被赋值过。

### 3.5 问题：audited original 实际不可达

`BetterCombatCompat.java`：

```text
:75  if (current == wrappedOriginal && wrappedOrderingKnown)
:94  wrappedOrderingKnown = false;
```

首次安装后 `wrappedOrderingKnown` 永远为 `false`，因此：

- `reinstalled audited original wrapper` 分支不可达；
- `AUDITED_BETTER_COMBAT_ORIGINAL` 永远不会被赋值；
- 所有 context 的 `orderingKnown` 都是 `false`；
- `NO_ATTACK_EVENT_OBSERVED` 在实际运行中不可达。

这不是伪造 API，但意味着实施报告中的“已审计原始 Handler 的观察行为”没有实际覆盖，也不能实现。

### 3.6 问题：重复包装/未知 Handler

代码没有反射拆解，也没有覆盖未知 Handler：

- `current == installedWrapper` -> no-op；
- `current == wrappedOriginal && wrappedOrderingKnown` -> 只有 audited 才重装；
- 其他 -> `DEGRADED_OR_UNKNOWN`，不包装。

这部分符合保守规则。

### 3.7 原始 Handler 调用次数与异常

正常路径：

```java
try {
    original.receive(...);
} catch (Throwable throwable) {
    CombatBridge.markOriginalThrew(...);
    throw throwable;
} finally {
    CombatBridge.finishRequest(...);
}
```

证据：

```text
BetterCombatCompat.java:169-180
```

- 正常路径只调用一次；
- 原始异常原样抛出；
- bridge failure 以 suppressed 方式附加；
- finish 不覆盖原始异常。

### 3.8 停服调用

`HandlerWrapper.receive`：

```text
BetterCombatCompat.java:131-137
```

检测：

```java
!server.isRunning() || server.isStopped()
```

命中时直接返回，不调用原始 Handler，不建立 context。

未消除的竞态：

- 检查后、调用前服务器可能停止；
- 原始 Handler 内部仍可能 `server.execute` inline；
- `isStopped()` 非 volatile；
- 该风险保留。

---

## 4. 攻击事件关联流程

### 4.1 服务端过滤

事件：

```text
AttackEntityEvent
LivingAttackEvent
LivingHurtEvent
LivingDamageEvent
```

实现：

```text
CombatBridge.java:185-236
```

全部检查：

```java
if (event.getEntity().level().isClientSide) return;
```

`AttackEntityEvent` 还要求 `ServerPlayer`。

### 4.2 事件优先级

全部：

```java
@SubscribeEvent(
    priority = EventPriority.LOWEST,
    receiveCanceled = true
)
```

只读，不取消、不修改 amount。

### 4.3 DamageSource 过滤

```java
CombatBridge.java:271-283
```

条件：

```java
direct instanceof ServerPlayer player
&& causing == player
&& source.is(DamageTypes.PLAYER_ATTACK)
```

可过滤：

- 目标非玩家直接近战；
- 投射物；
- 多数环境伤害；
- 荆棘（通常不是 direct player/player_attack）。

不能完全区分：

- 其他 Mod 使用 `playerAttack` 且 direct player 的追加伤害；
- 同玩家在同一窗口内的其他近战事件。

### 4.4 事件与请求关联

`CombatAttackTracker.onAttackEntity`：

```text
CombatAttackTracker.java:83-91
```

只附加到该攻击者当前 active session。

`CombatAttackSession.observeAttackTarget`：

```text
CombatAttackSession.java:164-185
```

仅接受 `matchTarget` 可匹配的目标。

`CombatAttackSession.observeLivingEvent`：

```text
CombatAttackSession.java:187-223
```

只接受：

```text
target UUID 在 requestedTargets
或 target UUID 是 partParentUuids 的 value
```

但**没有要求此前已经观察到对应的 AttackEntityEvent**。

这是潜在问题，见第 8 节。

### 4.5 sealed 后事件

`observeAttackTarget` 和 `observeLivingEvent` 开头：

```text
if (sealed) return;
```

sealed 后不会修改 context。

`finish` 后 `sealed=true`，见：

```text
CombatAttackSession.java:255-294
```

### 4.6 连续攻击

`CombatAttackTracker.prepare`：

```text
CombatAttackTracker.java:35-41
```

若 active 存在：

```java
state.active.finish(INTERRUPTED_BY_NEW_REQUEST)
state.active = new session
```

旧会话在新会话建立前结束，事件只附加当前 active。

### 4.7 orderingUnknown 的结果

`CombatAttackSession.finish`：

```java
boolean windowComplete = orderingKnown
    && originalHandlerInvoked
    && !taskOutcomeUnknown
    && !originalHandlerThrew
    && endReason == NORMAL_FINISH;
```

实际 `orderingKnown=false`，因此没有事件时：

- `NO_REQUESTED_TARGETS` 仍可确定性产生；
- `NO_ATTACK_EVENT_OBSERVED` 不可达；
- 其他无事件结果为 `UNKNOWN`。

这部分符合保守原则。

---

## 5. 目标解析流程

`CombatAttackSession.prepare`：

```text
CombatAttackSession.java:78-142
```

使用：

```text
ServerPlayer.serverLevel()
ServerLevel.getEntity(int)
ServerLevel.getEntityOrPart(int)
Entity.getUUID()
Entity.isAlive()
PartEntity.getParent()
```

没有调用 `getEntities()` 或 `getEntitiesOfClass()`；没有跨维度搜索。

### 5.1 分类

- `RESOLVED_ALIVE`：存活普通实体，加入 requestedTargets；
- `RESOLVED_NOT_ALIVE`：解析到但死亡，加入 unresolved raw IDs；
- `PART_RESOLVED`：PartEntity，保存 part UUID；父实体用 `PartEntity#getParent()`；
- `NOT_FOUND`：getEntity/getEntityOrPart 均 null；
- `UNRESOLVED`：无世界扫描或跨级查找。

### 5.2 覆盖

`CombatAttackSession.java:127-140`：

```text
truncated -> PARTIAL_TRUNCATED
part present -> PARTIAL_PART
unresolved -> PARTIAL_UNRESOLVED
else COMPLETE
```

`requestedTargetsComplete` 只有 `COMPLETE` 且无截断/未解析为 true。

### 5.3 确认问题：part event 对 parent request 的 coverage

`CombatAttackSession.observeAttackTarget`：

```text
CombatAttackSession.java:164-185
```

只在这里把 coverage 改为 `PARTIAL_PART`：

```java
if (!partTargetUuids.isEmpty()) { ... }
```

如果请求的是 **parent UUID**，但服务端 `AttackEntityEvent` 目标是 **PartEntity**：

- `matchTarget` 在 `:237-240` 返回 `PARENT_MATCH`；
- `partTargetUuids` 为空；
- `targetCoverage` 仍可能保持 `COMPLETE`；
- `requestedTargetsComplete` 可能保持 true。

这会使 part event 的 context 错误地声称完整普通目标覆盖。

---

## 6. 多目标伤害记录流程

### 6.1 结构

`CombatAttackSession`：

```text
damageObservations: Map<UUID, List<CombatDamageEventObservation>>
damageObservationCounts: Map<UUID, Integer>
```

`CombatTargetDamageObservation`：

```text
targetUuid
List<CombatDamageEventObservation>
totalEventCount
truncated
```

每条 observation：

```text
eventType
amountAtObservation
canceledAtObservation
positiveAtObservation
sourceClassification
observationTick
```

### 6.2 上限

```text
MAX_OBSERVATIONS_PER_TARGET = 8
```

代码：

```text
CombatAttackSession.java:25
CombatAttackSession.java:202-211
```

目标数量上限由 requestedTargets 最多 64 个 raw ID 间接限制，未发现世界实体来源。

### 6.3 不合并/不累计

`observations.add` 逐条添加，没有金额求和。

`CombatTargetDamageObservation` 不暴露总额字段；`positiveObserved` 是派生布尔值。

### 6.4 问题：source classification 永远 PRIMARY

`CombatBridge.java:205-235` 对 `LivingAttack`/`LivingHurt`/`LivingDamage` 全部传入：

```java
CombatDamageSourceClassification.PRIMARY_PLAYER_ATTACK
```

`AMBIGUOUS_PLAYER_ATTACK` 和 `OTHER` 从未由桥接层赋值。

因此：

- 其他 Mod 使用相同 direct/player/playerAttack source 的追加伤害会被标记为 PRIMARY；
- `ambiguousSourceObserved` 只可能由测试手工构造，实际运行不会因该场景触发；
- 报告声称的 ambiguous 处理没有实际实现证据。

### 6.5 封存

`finish` 将 observation 列表复制为 `CombatTargetDamageObservation`；context 构造器再做 `List.copyOf`/`Map.copyOf`，见：

```text
CombatAttackSession.java:255-293
CombatAttackContext.java:31-66
```

---

## 7. 线程安全与生命周期审计

### 7.1 网络线程

`BetterCombatCompat.HandlerWrapper.receive`：

```text
BetterCombatCompat.java:122-180
```

网络线程执行：

- `buf.copy()`；
- 有界解码；
- `CombatAttackIdGenerator.nextId()`（在 `CombatBridge.begin` 内）；
- `server.tell`/调度。

网络线程没有直接调用 Tracker session。

### 7.2 服务端任务

`CombatBridge.beginBetterCombatRequest`：

```text
CombatBridge.java:55-114
```

`schedule`：

```text
CombatBridge.java:150-158
```

- 非服务端线程：`server.tell(new TickTask(...))`；
- 服务端线程：`server.execute(...)`；
- `server.tell` 只入队。

prepare/executing 任务：

```text
CombatBridge.java:68-105
```

`CombatAttackTracker.prepare` 和 `CombatAttackSession.prepare` 只应在服务端线程执行。

### 7.3 Tracker 静态状态

`CombatAttackTracker`：

```text
CombatAttackTracker.java:26
private final Map<UUID, PlayerState> players = new HashMap<>();
```

访问来源：

- `CombatBridge` 的 prepare/finish/mark 任务（服务端线程）；
- `CombatBridge` 的 Forge 事件（服务端线程）；
- `CombatBridge` 的 ServerTick（服务端线程）；
- `CombatBridge` 的 logout/dimension/death（服务端线程）。

没有发现网络线程直接访问 `players` 的路径。

### 7.4 停服竞态

`CombatBridge.schedule` 在服务端线程且 stopped 时跳过任务；非服务端使用 tell。

`BetterCombatCompat.HandlerWrapper` 在检测到停止时跳过原始 Handler。

仍未解决：

- 检查后、调用前停止；
- 原始 Handler 内部 `execute` inline；
- `isStopped()` 非 volatile；
- 迟到任务/停服清理。

### 7.5 生命周期清理

- `onLogout`：移除 player state，active finish；
- `onDeath`：移除 player state，active finish；
- `onDimensionChange`：移除 player state，active finish；
- `onServerStopping`：finish active，clear map；
- `onServerTick`：TTL=40，recent cap=8，空 state 移除。

证据：

```text
CombatAttackTracker.java:107-163
```

### 7.6 潜在问题：清理不保留 recent

`onLogout`/`onDeath`/`onDimensionChange` 调用 `finish` 后直接丢弃 `PlayerState`，不写入 recent。  
这不是内存泄漏，但诊断上下文不可用；如果 Phase 05 需要死亡/维度切换后的最近 context，当前实现不会保留。

### 7.7 静态 Map/缓存泄漏

- `CombatAttackTracker.players` 是实例字段，不是静态；
- `CombatBridge.TRACKER` 是单例；
- `CombatAttackIdGenerator.NEXT` 静态 AtomicLong，无 Map；
- `BetterCombatCompat` 静态 handler 引用三个，固定；
- 未发现无界静态集合。

---

## 8. 每个问题对应的源码证据

### 已确认问题

#### C1. audited original path 不可达

```text
BetterCombatCompat.java:75  if (current == wrappedOriginal && wrappedOrderingKnown)
BetterCombatCompat.java:94  wrappedOrderingKnown = false;
```

后果：

- `AUDITED_BETTER_COMBAT_ORIGINAL` 永不产生；
- `orderingKnown` 永远 false；
- `NO_ATTACK_EVENT_OBSERVED` 实际不可达；
- “已审计原始 Handler”测试没有实现路径。

#### C2. part event 对 parent request 的 coverage 错误

```text
CombatAttackSession.java:168  matchTarget(target).match()
CombatAttackSession.java:179  if (!partTargetUuids.isEmpty())
CombatAttackSession.java:237-240  PartEntity + requested parent -> PARENT_MATCH
```

当请求 parent、事件 target 是 PartEntity：

- `partTargetUuids` 为空；
- `targetCoverage` 可能保持 COMPLETE；
- `requestedTargetsComplete` 可能保持 true；
- 违反“部件目标不自动升级普通完整目标”。

#### C3. ambiguous source 实际未赋值

```text
CombatBridge.java:205-207  PRIMARY_PLAYER_ATTACK
CombatBridge.java:219-221  PRIMARY_PLAYER_ATTACK
CombatBridge.java:233-235  PRIMARY_PLAYER_ATTACK
```

`AMBIGUOUS_PLAYER_ATTACK`/`OTHER` 没有生产路径。

#### C4. 伤害事件可无 AttackEntityEvent 关联

```text
CombatAttackSession.java:187-196
if (!isMatchedDamageTarget(target)) return;
```

`isMatchedDamageTarget` 只检查 requested UUID/parent map，不要求该目标已有 `AttackEntityEvent`。  
因此同一 active window 内同玩家对 requested target 的其他直接近战伤害可能被附加到当前 context。

### 潜在问题

#### P1. 目标数量上限依赖 requestedTargets

`damageObservations` 没有显式 64 target 检查；上限依赖：

```text
CombatAttackSession.java:37-39
requestedTargets max 64
partParentUuids values max 64
```

如果未来引入额外目标来源，代码没有独立保护。当前路径没有发现超限来源。

#### P2. `targetCoverage` 的 COMPLETE 与 part event

与 C2 相同，但影响是 context 完整性，不是直接伤害判定。

#### P3. `requestedTargetsDuplicateCount` 只统计 captured 前缀

```text
CombatAttackSession.java:80-86
```

若 rawCount > 64，重复 ID 位于 captured 之外不会被计数；`truncated=true` 仍存在。

#### P4. `CombatAttackTracker` 的 `stateForAttack` 线性扫描

```text
CombatAttackTracker.java:172-179
```

每次 finish/mark 扫描所有在线玩家；玩家数有限，不是世界扫描，但高频攻击时是 O(players)。当前未发现安全问题。

### 证据不足

- 未实现/执行 `CombatAttackSession`、`CombatAttackTracker`、`CombatBridge` 的事件级单元测试；
- 未实际验证目标解析、死亡、重复、截断、part/parent；
- 未实际验证 LivingAttack/Hurt/Damage；
- 未实际验证延迟/停服/异常；
- 未验证 Better Combat 缺失启动；
- 未验证多玩家隔离。

---

## 9. 当前测试覆盖矩阵

### 9.1 单元测试

```text
总测试：76
通过：76
失败：0
```

新增：

```text
CombatAttackRequestTest                 4
CombatTargetDamageObservationTest      2
BetterCombatAttackAdapterTest          3
```

覆盖：

- 请求有界捕获；
- targetIds 防御性复制；
- raw/captured/truncated；
- ID 唯一递增；
- 逐目标观察容量/truncated/positive/canceled/ambiguous（手工构造）；
- 空请求；
- 超范围 VarInt count 拒绝。

### 9.2 客户端

- 启动：PASS；
- 进入单人世界：PASS；
- `BetterCombatCompat` 安装：PASS；
- 空目标攻击请求：PASS（`prepared/finished` 日志）；
- 有效目标/伤害事件：NOT_TESTED；
- 多目标/part：NOT_TESTED。

### 9.3 专用服务器

- 启动：PASS（`Done (7.777s)`）；
- compat 安装：PASS；
- 真实玩家攻击：NOT_TESTED；
- 停服请求：NOT_TESTED。

### 9.4 多人

- MULTIPLAYER-TESTED：NO。

### 9.5 测试状态

```text
DESIGNED / IMPLEMENTED / PASSED / NOT_TESTED
```

当前：

```text
已实现并通过：请求/适配器/观察对象单元测试
已实现未覆盖：Session/Tracker/Bridge 事件路径
未执行：目标解析、事件实机、多玩家、停服
```

---

## 10. 是否存在阻止 Phase 04 Gate 2 验收的问题

**存在需要修复/补测才能无条件验收的问题：**

1. C1：没有 audited original 路径；所有生产 context 都 `orderingKnown=false`。  
   这是计划允许的保守状态，但“已审计原始 Handler”测试没有实现。

2. C2：part event 对 parent request 可能错误保持 COMPLETE，属于 context 完整性缺陷。

3. C3：ambiguous source 没有实际分类路径，和报告/计划描述不完全一致。

4. C4：伤害事件可无 AttackEntityEvent 关联，存在事件归属风险。

5. 测试覆盖不足：Session/Tracker/Bridge 的实机/事件级测试缺失。

因此不能标记 **VERIFIED**；不能声称 Gate 2 完整验收。

---

## 11. 当前实现中可信的部分

- Handler 链未被覆盖；
- 只替换 C2S AttackRequest Handler；
- 原始 Handler 正常路径只调用一次；
- 原始异常保留；
- 停服检测命中时跳过原始 Handler；
- 网络线程不直接修改 Tracker 会话；
- 请求 ID 与 Tracker 分离；
- 请求解析有界；
- raw target 缓冲不被消费；
- 目标解析不使用世界扫描；
- 每个目标独立 observation 列表、每目标 8 条、目标上限 64；
- sealed 后不修改 context；
- 生命周期/结果/结束原因分离；
- 清理覆盖 logout/death/dimension/server stopping；
- 无新增网络包/Mixin/额外伤害。

---

## 12. 尚未解决的风险

1. Handler 身份无法由公开 API 确认；
2. 停服时原始 Handler 内部 execute 竞态；
3. `isStopped()` 非 volatile；
4. 无服务端空间验证；
5. 无法证明实际生命值减少；
6. 其他 Mod 同 source 伤害无法区分；
7. part/parent 匹配只覆盖部分路径；
8. 事件没有 AttackEntityEvent 前置关联；
9. 目标截断/未解析时 context 不能完整；
10. 多人/真实专用服务器未测；
11. Better Combat 缺失启动未测；
12. 失败/取消/迟到事件实机未测。

---

## 13. 最终结论

# `CONDITIONALLY_READY`

理由：

- 核心桥接和只读观察已实现、编译、76 个单元测试通过；
- 客户端启动、空目标攻击请求、专用服务器启动有实际证据；
- 但存在 C2 part coverage 缺陷、C3 ambiguous classification 缺口、C4 事件归属风险；
- Session/Tracker/Bridge 的事件级测试和多玩家/停服测试不足；
- 因此不能声明 VERIFIED，也不能无条件通过 Gate 2 验收。

建议修复/补测后再进行最终验收：

```text
1. 修复 part event 对 parent request 的 coverage 标记。
2. 明确并实现/记录 ambiguous source 分类。
3. 为 LivingAttack/Hurt/Damage 增加 AttackEntityEvent 关联或明确归属限制。
4. 增加 Session/Tracker/Bridge 事件级测试。
5. 完成目标解析、part/parent、多玩家、停服/异常实机测试。
```

本次未修改源码、测试、依赖或第三方 JAR。

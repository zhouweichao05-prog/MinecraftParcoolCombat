# PHASE04_GATE2_IMPLEMENTATION_PLAN.md

> ParkourCombat Phase 04 — Gate 2 最终实施计划修订版  
> 本文件是计划文档，不是编码授权。  
> 本阶段不创建 `combat/` 或 `compat/bettercombat/` 源码。

---

## 1. 项目基线

现场读取：

```text
项目：D:\MinecraftParcoolCombat
分支：master
HEAD：170ea5bc29c3660525431263d07c3b79a9094f69
HEAD 提交：docs: add phase 03.5 motion debug hud reports
工作区：clean（生成本文件前）
```

固定环境：

```text
Minecraft：1.20.1
Forge：47.4.0
Java：17
Gradle：8.8
ParCool：3.4.3.3
Better Combat Forge：1.9.0+1.20.1
Player Animator：1.0.2-rc1+1.20
Cloth Config：11.1.136
```

Better Combat JAR：

```text
libs/bettercombat-forge-1.9.0+1.20.1.jar
SHA-256：49C69DCCF2C641246B4E3DECA81EBAC21F0EE119E81FC713B838B8E33569EEA1
大小：1,054,339 bytes
```

当前已存在：

```text
ParkourAdapterEvents
ParkourActionAdapter
ParCoolActionAdapter
MotionSnapshot
MotionTracker
MotionHistory
MotionAccumulator
FallSnapshot
FallStateMachine
ParkourActionState
ParkourActionTracker
```

当前不存在：

```text
combat/
compat/bettercombat/
```

Gate 1 与补充审计已经确认：

- Better Combat 没有公开的服务端攻击开始/命中/结束事件。
- `AttackEntityEvent` 只是服务端观察到的 `Player.attack` 尝试，不是命中/伤害证明。
- `LivingDamageEvent` 不是实际生命值减少。
- `C2S_AttackRequest.entityIds` 完全来自客户端，服务端没有空间验证。
- `ServerPlayNetworking.HANDLERS` 是可变 Map，不能使用 `registerGlobalReceiver` 覆盖。
- Forge 47.4.0 / EventBus 6.0.5 没有 `EventPriority.MONITOR`。
- 只允许 `EventPriority.LOWEST` + `receiveCanceled = true`，但不能声称绝对最终状态。

---

## 2. Gate 1 审计结论（保留）

服务端链路：

```text
C2S_AttackRequest
  -> ServerNetwork C2S Handler
  -> server.execute(...)
  -> request.entityIds()
  -> ServerboundInteractPacket
  -> ServerGamePacketListenerImpl#handleInteract
  -> Player#attack(Entity)
  -> AttackEntityEvent
  -> LivingEntity#hurt(...)
  -> LivingAttackEvent
  -> 无敌/死亡/免疫检查
  -> LivingHurtEvent
  -> 护甲/附魔/吸收
  -> LivingDamageEvent
  -> setHealth(...)
  -> 可能 LivingDeathEvent
```

必须保留的结论：

1. `AttackEntityEvent` 不能证明伤害。
2. `LivingAttackEvent` 不能证明通过后续检查。
3. `LivingHurtEvent` 不是最终生命值减少。
4. `LivingDamageEvent` 是扣血前的伤害阶段观察点。
5. Phase 04 不实现 `setHealth` 后置 Mixin。
6. 客户端 `entityIds` 不得作为服务端可信命中依据。
7. 服务端没有距离、视线、Hitbox、目标数量、攻击冷却验证。
8. 不允许添加第二套 Hitbox。
9. 不允许修改 Better Combat 原始伤害结算。
10. 不允许把客户端动画当作服务端攻击事实。

---

## 3. 五项修正后的最终设计

### 修正 1：攻击结果状态必须更严谨

**完成。**

原计划中的 `REJECTED`、`NO_TARGET`、`MISS` 不足以表达真实证据，已删除或替换。

- 不再使用 `MISS`。
- 不再无条件使用 `REJECTED`。
- 没有 `HIT`、`DAMAGE_APPLIED`、`HEALTH_REDUCED`。
- 生命周期与观察结果拆成两个枚举。
- 每个状态有严格进入条件。
- 状态转换在文档第 5 节定义。

### 修正 2：伤害事件只能记录观察值

**完成。**

删除 `DAMAGE_RESOLVED`，改为：

```text
DAMAGE_EVENT_OBSERVED
DAMAGE_AMOUNT_OBSERVED_POSITIVE
```

记录：

- 事件类型；
- 监听器执行时的 `amount`；
- 监听器执行时的 `canceled`；
- 是否观察到正金额。

不保证：

- 后续同级监听器不修改；
- 最终生命值减少；
- 这是 Better Combat 最终伤害。

Phase 05 不得无条件使用该金额做词条倍率。

### 修正 3：服务端任务调度必须解决停止竞态

**计划层完成；停用原始 Handler 的内部 execute 仍有不可消除的第三方限制。**

审计结论：

- `server.execute(Runnable)` 在非服务端线程通常排队；
- 但在服务器停止或同线程空闲时可能直接执行；
- `isStopped()` 检查与 `execute` 之间存在竞态；
- `server.tell(...)` 只入队，不在调用线程直接执行；
- `MinecraftServer` 继承 `ReentrantBlockableEventLoop<TickTask>`；
- `tell` 的可用参数是 `TickTask`，不是 `Runnable`；
- 停服后 `tell` 的任务可能永远不被执行，因此不得依赖它执行清理。

桥接层可以保证自己的 prepare/finish 不在网络线程访问世界，  
但**不能控制 Better Combat 原始 Handler 内部的 `server.execute`**。

最终调度方案见第 9 节；停服剩余竞态见 9.5.3。

### 修正 4：HANDLERS 安装必须防止重复包装

**完成。**

采用保守身份规则：

```text
current == installedWrapper       -> 不重复包装
current == wrappedOriginal        -> 可重新包装
其他未知 current                  -> DEGRADED_OR_UNKNOWN，不包装
```

Map identity 改变本身不构成重新包装理由；必须检查新 Map 中当前 Handler 的身份。

完整策略见第 8 节。

### 修正 5：异常处理必须保留原始 Handler 语义

**完成（正常路径）；停服路径为明确降级。**

原则：

- 桥接异常不能阻止原始 Handler。
- 原始 Handler 在正常路径只能调用一次。
- 原始 Handler 的异常不能被桥接异常覆盖。
- 不无条件吞掉原始异常。
- `finally` 只保证调用结束，不保证攻击任务成功。
- 原始任务结果不确定时标记 `UNKNOWN`。
- 服务器已停止/不运行时，保守跳过原始 Handler，记录 `SKIPPED_SERVER_STOPPING`；这是终止降级，不重试。

完整异常路径见第 10 节。

---

## 4. 攻击生命周期状态模型

### 4.1 生命周期枚举

```java
enum CombatAttackPhase {
    REQUESTED,
    PREPARED,
    EXECUTING,
    FINALIZED,
    UNKNOWN
}
```

| 阶段 | 严格含义 |
|---|---|
| `REQUESTED` | Wrapper 已收到 C2S 请求；尚未建立 context |
| `PREPARED` | 服务端 prepare 任务成功建立 active context |
| `EXECUTING` | Bridge 已进入原始 Handler 调用路径；不证明 Better Combat 攻击任务已运行或成功 |
| `FINALIZED` | 对应 token 的 finish 已执行，context 已生成 |
| `UNKNOWN` | token 丢失、prepare/finish 异常、超时、顺序无法确认 |

### 4.2 独立结束原因

`INTERRUPTED` 不是生命周期枚举值，也不是观察结果。  
使用独立诊断枚举：

```java
enum CombatAttackEndReason {
    NORMAL_FINISH,
    INTERRUPTED_BY_NEW_REQUEST,
    PREPARE_FAILED,
    ORIGINAL_HANDLER_THREW,
    FINISH_SCHEDULE_FAILED,
    TTL_EXPIRED,
    PLAYER_DISCONNECTED,
    PLAYER_DIED,
    DIMENSION_CHANGED,
    SERVER_STOPPING,
    OBSERVATION_WINDOW_INCOMPLETE,
    HANDLER_DEGRADED,
    UNKNOWN
}
```

规则：

- 旧 active 被新请求打断：`endReason = INTERRUPTED_BY_NEW_REQUEST`；
- 生命周期可 `FINALIZED` 或 `UNKNOWN`，但 `INTERRUPTED` 只出现在 `endReason`；
- 观察结果保持独立；打断本身不生成 `HIT`；
- `SERVER_STOPPING` 路径不建立 context；
- `TTL_EXPIRED` 通常 `phase=FINALIZED/UNKNOWN`、`result=UNKNOWN`；
- `ORIGINAL_HANDLER_THREW` 不能强行为 `NO_ATTACK_EVENT_OBSERVED`；
- `FINISH_SCHEDULE_FAILED` 不能覆盖原始异常。

| endReason | phase | result |
|---|---|---|
| `NORMAL_FINISH` | `FINALIZED` | 按观察结果 |
| `INTERRUPTED_BY_NEW_REQUEST` | `FINALIZED` 或 `UNKNOWN` | 保留已有观察；无观察则 `UNKNOWN` |
| `PREPARE_FAILED` | `UNKNOWN` | `UNKNOWN` |
| `ORIGINAL_HANDLER_THREW` | `FINALIZED` 或 `UNKNOWN` | 已有观察或 `UNKNOWN`；不默认 NO_ATTACK |
| `FINISH_SCHEDULE_FAILED` | `UNKNOWN` | `UNKNOWN`（除非已有最终观察，仍保留） |
| `TTL_EXPIRED` | `UNKNOWN` | `UNKNOWN` |
| `PLAYER_DISCONNECTED` | `UNKNOWN` | `UNKNOWN`；清理 |
| `PLAYER_DIED` | `FINALIZED` 或 `UNKNOWN` | 已有观察或 `UNKNOWN` |
| `DIMENSION_CHANGED` | `FINALIZED` 或 `UNKNOWN` | 已有观察或 `UNKNOWN` |
| `SERVER_STOPPING` | 无 context | 无 result |
| `OBSERVATION_WINDOW_INCOMPLETE` | `FINALIZED` | `UNKNOWN`（若没有完整观察） |
| `HANDLER_DEGRADED` | `FINALIZED`/`UNKNOWN` | 观察结果或 `UNKNOWN` |
| `UNKNOWN` | `UNKNOWN` | `UNKNOWN` |

### 4.3 生命周期转换

```text
REQUESTED -> PREPARED
REQUESTED -> UNKNOWN       (prepare 失败/玩家断开/服务器停止)
PREPARED  -> EXECUTING     (原始 Handler 被调用)
PREPARED  -> FINALIZED     (旧 token 在原始调用前被中断)
EXECUTING -> FINALIZED     (finish 正常完成)
EXECUTING -> UNKNOWN       (finish 缺失、超时、任务异常)
* -> UNKNOWN
```

规则：

1. 每个 token 只能 finalize 一次。
2. 迟到的 finish 不得 finalize 新 token。
3. 新请求到来时，旧 active token 的 `endReason=INTERRUPTED_BY_NEW_REQUEST`；生命周期 `FINALIZED` 或 `UNKNOWN`，不新增 `INTERRUPTED` 生命周期值。
4. `EXECUTING` 只表示 Bridge 已进入原始调用路径，不表示攻击成功。
5. 没有 original 调用时，`PREPARED` 可以直接 `FINALIZED/UNKNOWN`。

---

## 5. 攻击结果状态模型

### 5.1 结果枚举

```java
enum CombatAttackResult {
    REQUEST_RECEIVED,
    NO_REQUESTED_TARGETS,
    NO_ATTACK_EVENT_OBSERVED,
    TARGET_OBSERVED,
    DAMAGE_EVENT_OBSERVED,
    DAMAGE_AMOUNT_OBSERVED_POSITIVE,
    UNKNOWN
}
```

### 5.2 严格语义

| 结果 | 进入条件 | 不得声称 |
|---|---|---|
| `REQUEST_RECEIVED` | 收到请求，尚无任何观察 | 攻击已执行/命中 |
| `NO_REQUESTED_TARGETS` | `requestedTargetsRawCount == 0` | 攻击落空/被拒绝 |
| `NO_ATTACK_EVENT_OBSERVED` | `targetCoverage == COMPLETE`、`observationWindowComplete`，且没有服务端 `AttackEntityEvent` | 攻击落空/被拒绝/未命中 |
| `TARGET_OBSERVED` | 至少一个服务端 `AttackEntityEvent` | 造成伤害/命中 |
| `DAMAGE_EVENT_OBSERVED` | 至少一个 `LivingHurtEvent` 或 `LivingDamageEvent` | 最终伤害/生命减少 |
| `DAMAGE_AMOUNT_OBSERVED_POSITIVE` | 至少一个目标出现 `LivingDamageEvent` 观察时 amount > 0 且未取消 | 最终生命值一定减少 |
| `UNKNOWN` | token/顺序/异常/超时/截断/未解析/观察窗口不完整 | 不猜测 |

### 5.3 结果推导优先级

finalize 时按顺序：

```text
1. 无 context / token 不匹配 / prepare 失败 / finish 超时
   -> UNKNOWN

2. requestedTargetsRawCount == 0
   -> NO_REQUESTED_TARGETS

3. targetCoverage != COMPLETE
   或 observationWindowComplete == false
   且没有任何攻击/伤害观察
   -> UNKNOWN

4. attackObservedTargets.isEmpty()
   -> NO_ATTACK_EVENT_OBSERVED

5. 无 LivingHurt 且无 LivingDamage 观察
   -> TARGET_OBSERVED

6. 有 LivingHurt/LivingDamage，
   但没有“未取消且 amount > 0”的 LivingDamage 观察
   -> DAMAGE_EVENT_OBSERVED

7. 至少一个目标有未取消且 amount > 0 的 LivingDamage 观察
   -> DAMAGE_AMOUNT_OBSERVED_POSITIVE
```

注意：

- `NO_ATTACK_EVENT_OBSERVED` 不是 `MISS`；它只表示“在完整覆盖且窗口完整的条件下没有观察到事件”。
- 目标截断/未解析/观察窗口不完整且没有事件时，必须是 `UNKNOWN`，不能是 `NO_ATTACK_EVENT_OBSERVED`。
- `AttackEntityEvent` 被取消仍算 `TARGET_OBSERVED`，但 `canceled` 标志为 true。
- `LivingAttackEvent` 单独不算 `DAMAGE_EVENT_OBSERVED`；它只是进入 hurt 的观察。
- `LivingHurtEvent` 被取消仍可算 `DAMAGE_EVENT_OBSERVED`，但 `canceled` 标志为 true。
- `LivingDamageEvent` amount 0 或取消 -> `DAMAGE_EVENT_OBSERVED`，不是 positive。
- `DAMAGE_AMOUNT_OBSERVED_POSITIVE` 是**逐目标观察**，不是聚合最终伤害。
- 不保证后续同级监听器不取消/修改。
- 没有 `CANCELLED` 结果；取消是独立布尔标志。
- 没有 `REJECTED`；除非未来能独立证明服务端拒绝，当前计划不使用。
- 没有 `MISS`；没有 `HIT`；没有 `DAMAGE_APPLIED`；没有 `HEALTH_REDUCED`。

### 5.4 状态转换表

```text
REQUEST_RECEIVED
  -> NO_REQUESTED_TARGETS
  -> NO_ATTACK_EVENT_OBSERVED (仅完整覆盖且窗口完整)
  -> TARGET_OBSERVED
  -> UNKNOWN (截断/未解析/窗口不完整/顺序未知)

TARGET_OBSERVED
  -> DAMAGE_EVENT_OBSERVED
  -> UNKNOWN

DAMAGE_EVENT_OBSERVED
  -> DAMAGE_AMOUNT_OBSERVED_POSITIVE
  -> UNKNOWN
```

结果只能停留在观察层级，不能升级成实际生命减少。

---

## 6. 目标可信度模型

```java
enum CombatTargetTrust {
    CLIENT_REPORTED_TARGET,
    SERVER_OBSERVED_ATTACK_TARGET,
    SERVER_DAMAGE_OBSERVED_TARGET,
    SERVER_SPATIALLY_VALIDATED_TARGET
}
```

### 6.1 分配

| 来源 | trust |
|---|---|
| `C2S_AttackRequest.entityIds` | `CLIENT_REPORTED_TARGET` |
| 服务端 `AttackEntityEvent` | `SERVER_OBSERVED_ATTACK_TARGET` |
| 服务端 `LivingHurtEvent`/`LivingDamageEvent` | `SERVER_DAMAGE_OBSERVED_TARGET` |
| 服务端独立距离/视线/Hitbox 验证 | `SERVER_SPATIALLY_VALIDATED_TARGET` |

### 6.2 关键限制

```text
SERVER_SPATIALLY_VALIDATED_TARGET
当前不可获得。
```

原因：

- Better Combat 服务端不验证距离；
- `range` 赋值后未使用；
- 没有服务端视线；
- 没有服务端 Hitbox；
- 现有事件不提供空间验证结果。

### 6.3 Phase 05 安全限制

1. `CLIENT_REPORTED_TARGET` 只能作为诊断/关联提示。
2. 不得因为 `entityIds` 包含目标就授予效果。
3. 不得从 `entityIds` 推断距离、视线或 Hitbox。
4. 效果关联只使用 `SERVER_OBSERVED_ATTACK_TARGET`/`SERVER_DAMAGE_OBSERVED_TARGET`。
5. `SERVER_OBSERVED_ATTACK_TARGET` 仍不是空间验证。
6. 需要位置/范围的特殊攻击必须在 Phase 05 自行做服务端验证。
7. 不允许在 Phase 04 添加第二套 Hitbox。
8. 多目标必须去重并设置数量上限。

---

## 7. CombatAttackContext 数据结构

### 7.1 不可变 context

```text
UUID attackerId
long attackId
int attackTick
int serverTick
CombatWeaponSnapshot weapon
int comboCurrent
int comboTotal
boolean offHand
int requestedTargetsRawCount
int requestedTargetsCapturedCount
boolean requestedTargetsTruncated
boolean requestedTargetsHaveDuplicates
int requestedTargetsDuplicateCount
List<CombatRequestedTarget> requestedTargetResolutions
Set<UUID> requestedTargets
Set<UUID> partTargetUuids
Map<UUID, UUID> partParentUuids
boolean partTargetUnmatched
Set<Integer> unresolvedRequestedEntityIds
boolean requestedTargetsComplete
CombatTargetCoverage targetCoverage
Set<UUID> attackObservedTargets
Set<UUID> damageObservedTargets
Map<UUID, CombatTargetDamageObservation> targetDamageObservations
boolean damageObservationTruncated
int attackEventCount
int hurtEventCount
int damageEventCount
int lateEventCount
boolean observationWindowComplete
boolean observationWindowSealed
boolean orderingKnown
boolean attackEntityCanceled
boolean livingAttackCanceled
boolean livingHurtCanceled
boolean livingDamageCanceled
boolean originalHandlerInvoked
boolean originalHandlerThrew
boolean taskOutcomeUnknown
CombatAttackEndReason endReason
CombatAttackPhase phase
CombatAttackResult result
CombatAttackSource source
MotionSnapshot motionSnapshot
boolean serverAuthoritative
boolean healthReductionVerified   // Phase 04 永远 false
```

### 7.2 约束

- 不保存 `Player`。
- 不保存长期 `ItemStack`。
- `CombatWeaponSnapshot` 只保存必要不可变字段：
  - item registry id；
  - count；
  - category；
  - twoHanded；
  - combo current/total；
  - animation；
  - attack range；
  - damage multiplier；
  - hitbox shape；
  - angle；
  - offHand。
- `MotionSnapshot` 由服务端 adapter 提供；无 snapshot 时为 `null`，不伪造 0。
- `requestedTargets` 上限 64；超出标记 truncation，不扩展为无界。
- `requestedTargetsRawCount` 记录原始请求数量；`requestedTargetsCapturedCount` 记录实际保留数量。
- `requestedTargetsTruncated=true` 时，完整目标集合未知；不能把观察结果当成完整证据。
- `requestedTargetsHaveDuplicates`/`requestedTargetsDuplicateCount` 独立记录。
- `requestedTargetsComplete` 只有在未截断、全部 raw ID 解析为存活实体且无 unresolved 时才为 true；`RESOLVED_NOT_ALIVE` 也使 complete=false。
- `attackObservedTargets`/`damageObservedTargets` 上限 64。
- `targetDamageObservations` 每目标最多 8 条；总目标最多 64。
- 所有集合最终不可变、去重。
- `healthReductionVerified` 永远 `false`；没有 `HEALTH_REDUCED` 状态。
- `targetDamageObservations` 中的每个 amount 只是监听器观察值，不求和、不称为最终伤害。

### 7.3 `CombatWeaponSnapshot`

不使用 `ItemStack` 长期引用。  
如果武器有自定义 NBT，只记录“有自定义数据”或稳定 hash，不复制整个 NBT 到 context；Phase 04 不需要网络传输武器。

### 7.4 每个玩家的边界

- 每玩家最多 1 个 active context。
- recent context 固定容量 8。
- TTL 40 tick。
- 玩家退出、死亡、维度切换、服务器停止清理。
- 迟到的 finish 不得完成新 token。
- session finalize 后 `sealed=true`，迟到事件不得再附加。

### 7.5 请求 ID 与线程所有权

请求 ID 不使用 Tracker 会话状态生成。

新增独立组件：

```text
CombatAttackIdGenerator
  static AtomicLong NEXT
  static long nextId()
```

- 可在网络线程调用；
- 只生成 `long`；
- 不读写 active/recent/session/TTL；
- 不访问 Player/World；
- 复合身份为 `(attacker UUID, attackId)`；
- 服务器重启后序列重置；
- 不用于持久化或防重放。

Tracker 的 active/recent/session 只在服务端线程修改。  
详见 9.4 数据所有权表。

### 7.6 请求实体 ID → UUID 解析

**转换阶段：服务端 prepare 任务。**

网络线程只保留原始整数 ID，不解析实体。  
服务端 prepare 使用攻击者所在 `ServerLevel`：

```java
ServerLevel level = player.serverLevel();
Entity entity = level.getEntity(rawId);
if (entity == null) {
    entity = level.getEntityOrPart(rawId);
}
```

锁定环境证据：

```text
ServerLevel#getEntity(int) -> Entity
ServerLevel#getEntityOrPart(int) -> Entity
Entity#getUUID()
Entity#getId()
Entity#isAlive()
```

实际映射：

```text
m_6815_  -> getEntity(int)
m_143317_ -> getEntityOrPart(int)
m_20148_ -> getUUID()
m_19879_ -> getId()
```

约束：

- 不遍历 `getEntities()`；
- 不扫描世界；
- 不跨维度搜索；
- 只查攻击者所在 `ServerLevel` 的实体管理器；
- `getEntity` 为按 ID 查找；
- `getEntityOrPart` 用于 Better Combat 已使用的 boss part 语义。

解析结果：

```java
enum CombatTargetResolution {
    RESOLVED_ALIVE,
    RESOLVED_NOT_ALIVE,
    PART_RESOLVED,
    NOT_FOUND,
    UNRESOLVED
}
```

`CombatRequestedTarget`：

```text
int rawEntityId
UUID uuid (nullable)
UUID parentUuid (nullable)
CombatTargetResolution resolution
```

处理：

| 情况 | 处理 |
|---|---|
| 解析成功且 alive | `RESOLVED_ALIVE`；加入 requestedTargets |
| 解析成功但 dead | `RESOLVED_NOT_ALIVE`；保留 UUID 诊断，不计为有效 requested target |
| boss part 解析 | `PART_RESOLVED`；同时保存 part UUID；有可靠父实体时保存 parentUuid |
| 找不到 | `NOT_FOUND`；加入 unresolved raw IDs |
| 跨维度 | 不跨级搜索；`NOT_FOUND`/`UNRESOLVED`，不能区分“其他维度” |
| 已移除 | `UNRESOLVED` |

`requestedTargetsComplete` 仅当：

```text
!truncated
&& capturedCount == rawCount
&& unresolvedRequestedEntityIds.isEmpty()
&& resolutions 中没有 NOT_FOUND/UNRESOLVED/RESOLVED_NOT_ALIVE/PART_RESOLVED
```

`PART_RESOLVED` 不算普通完整目标；目标覆盖至少为 `PARTIAL_PART`，不能升级为普通 `COMPLETE`。

### 7.6.1 实体部件与父实体

锁定版本证据：

```text
ServerLevel#getEntity(int)
  -> getEntities().get(id)

ServerLevel#getEntityOrPart(int)
  -> getEntities().get(id)
     ?: dragonParts.get(id)

EnderDragonPart extends PartEntity<EnderDragon>
PartEntity#getParent()
EnderDragonPart#parentMob (public final)
EnderDragon#getSubEntities() / getParts()
Entity#getUUID()
```

关键事实：

1. `getEntityOrPart` 只回退到 `dragonParts` Map，不是任意 `PartEntity` 的通用查询。
2. `EnderDragonPart` 有独立 UUID；`Entity` 构造时生成 UUID，部件没有覆盖为父 UUID。
3. `PartEntity#getParent()` 是可靠父实体 API；`EnderDragonPart#parentMob` 是公开父实体字段。
4. Better Combat 服务端先 `getEntity(id)`，失败后 `getEntityOrPart(id)`；可能拿到 `EnderDragonPart`。
5. `EnderDragonPart#hurt` 委托 `parentMob.hurt(this, source, amount)`；因此：
   - `AttackEntityEvent` 目标可以是 **part**；
   - `LivingAttackEvent`/`LivingHurtEvent`/`LivingDamageEvent` 目标通常是 **parent dragon**。
6. 通用 `PartEntity` 不一定在 `dragonParts` 中；未解析时不能猜测父实体。

匹配状态：

```java
enum CombatTargetMatch {
    DIRECT_MATCH,
    PART_MATCH,
    PARENT_MATCH,
    PART_TARGET_UNMATCHED,
    COLLATERAL
}
```

规则：

- part UUID 与请求 part UUID 匹配 -> `PART_MATCH`；
- 事件 part 的 parent UUID 与请求 parent UUID 匹配 -> `PARENT_MATCH`；
- 请求 parent、事件 parent -> `DIRECT_MATCH`；
- 无法建立 part/parent 关联 -> `PART_TARGET_UNMATCHED`；
- 不在请求集合 -> `COLLATERAL`；
- `PART_TARGET_UNMATCHED` 不升级为普通完整目标；
- `PART_MATCH` 也不自动升级为父实体命中；
- 父实体关系不可靠时不伪造 parent UUID；
- 部件目标存在时 `requestedTargetsComplete=false`，coverage 至少 `PARTIAL_PART`。

`getEntityOrPart` 在锁定映射中是 deprecated，但真实存在；使用它保持 Better Combat 语义，不修改第三方行为。

事件匹配：

- `AttackEntityEvent` 目标使用 `Entity#getUUID()` 与服务端解析出的 UUID 集合匹配；
- 匹配成功才作为主要 `SERVER_OBSERVED_ATTACK_TARGET`；
- 目标 UUID 不在解析集合中，标记 `UNMATCHED_TARGET_EVENT`/collateral；
- 不因一个未匹配事件把某个 unresolved raw ID 自动标记为完整；
- 如果事件目标恰好对应此前 unresolved raw ID，可记为 late-resolved 诊断，但不回填 `requestedTargetsComplete`。

### 7.7 目标截断与重复

#### 请求解码

Better Combat 原始 `C2S_AttackRequest.read` 使用：

```text
readVarIntArray()
```

`readVarIntArray()` 读取 VarInt 长度并分配完整数组。  
`readVarIntArray(int max)` 的 max 是当前 readableBytes，不是业务上限；因此仍可能分配较大数组。

桥接层不能改变原始 Handler 的解码，但**自己的副本解码**必须使用有界解析：

```text
readInt comboCount
readBoolean isSneaking
readInt selectedSlot
readVarInt rawCount
if rawCount < 0 || rawCount > readableBytes -> invalid
capturedCount = min(rawCount, 64)
read capturedCount 个 VarInt
skip 剩余 rawCount - capturedCount 个 VarInt
```

- 不调用会分配完整数组的 `C2S_AttackRequest.read` 作为桥接副本解析；
- 不改变原始 Handler 的 buffer/行为；
- 原始 Handler 仍按 Better Combat 原有方式处理；
- 桥接层避免额外完整数组分配；
- 如果 rawCount 超过 `readableBytes`，标记 invalid，仍调用原始 Handler；
- 不把桥接解析失败解释为攻击失败。

#### 截断语义

```text
requestedTargetsRawCount
requestedTargetsCapturedCount
requestedTargetsTruncated
requestedTargetsHaveDuplicates
requestedTargetsDuplicateCount
```

规则：

- `rawCount == 0` -> `NO_REQUESTED_TARGETS`；
- `rawCount > 64` -> `requestedTargetsTruncated=true`；
- 截断后目标集合不完整；
- 截断后没有事件 -> 不能得到“完整无事件”的结论，结果必须 `UNKNOWN` 或带 partial coverage；
- 截断后观察到事件 -> 可以 `TARGET_OBSERVED/DAMAGE_*`，但 coverage 仍为 `TRUNCATED`；
- 重复 raw ID 计数，解析 UUID 后去重；
- 重复不降低 coverage，但 `duplicate=true` 必须保留；
- 不按 rawCount 伪造多个目标。

`TargetCoverage`：

```java
enum CombatTargetCoverage {
    COMPLETE,
    PARTIAL_TRUNCATED,
    PARTIAL_UNRESOLVED,
    PARTIAL_PART,
    UNKNOWN
}
```

映射：

- 全部 `RESOLVED_ALIVE` 且未截断 -> `COMPLETE`；
- `rawCount > 64` -> `PARTIAL_TRUNCATED`；
- `NOT_FOUND`/`UNRESOLVED`/`RESOLVED_NOT_ALIVE` -> `PARTIAL_UNRESOLVED`；
- 存在 `PART_RESOLVED` 或 `PART_TARGET_UNMATCHED` -> `PARTIAL_PART`；
- 无法建立有效解析 -> `UNKNOWN`。

### 7.8 多目标伤害观察

不使用单一 `float damageAmountObserved`。

每个目标独立保存：

```java
final class CombatTargetDamageObservation {
    UUID targetUuid;
    List<CombatDamageEventObservation> observations; // immutable copy
    int totalEventCount;                            // 包括被截断的观察
    boolean truncated;
}

final class CombatDamageEventObservation {
    CombatDamageEventType eventType;
    float amountAtObservation;
    boolean canceledAtObservation;
    boolean positiveAtObservation;
    CombatDamageSourceClassification sourceClassification;
    int observationTick;
}
```

枚举：

```java
enum CombatDamageEventType {
    LIVING_ATTACK,
    LIVING_HURT,
    LIVING_DAMAGE
}

enum CombatDamageSourceClassification {
    PRIMARY_PLAYER_ATTACK,
    AMBIGUOUS_PLAYER_ATTACK,
    OTHER
}
```

结构：

```java
Map<UUID, CombatTargetDamageObservation>
```

限制：

- 最多 64 个目标；
- 每目标最多 8 条 `CombatDamageEventObservation`；
- `totalEventCount` 可继续计数，但 `observations` 不再扩容；
- 达到 8 条后 `truncated=true`；
- 不累加不同目标；
- 不对同一目标重复金额求和；
- 不把 amount 称为最终伤害；
- 不把任何金额称为实际生命值减少；
- 不因截断扩容或高频补采。

相同目标重复事件：

- 每条独立追加到 `observations`；
- 不做金额合并；
- 不生成新 attack token；
- `totalEventCount` 增加；
- 超过 8 条只增加 total 并 `truncated=true`。

同一目标多次 `LivingHurtEvent`/`LivingDamageEvent`：

- 每个事件保留独立 amount/canceled/tick；
- 不覆盖前一条；
- 不把两条相加；
- `positiveAtObservation` 逐条计算；
- 结果只表示“至少某一条观察到正金额”，不是最终伤害。

容量溢出：

- 目标 >64：不再新增 target；
- 单目标 >8：不再新增 observation；
- `damageObservationTruncated=true`；
- 已有观察仍有效；
- 结果不能声称完整覆盖全部伤害事件。

`ambiguousSourceObserved`：

- 派生值：任一 observation 的 `sourceClassification == AMBIGUOUS_PLAYER_ATTACK`；
- 不额外保存为独立可变字段；
- 其他 Mod 同 source 追加伤害只标记 ambiguous，不混入最终伤害。

`DamageSource` 过滤：

```text
source.getEntity() == player
source.getDirectEntity() == player
source.is(DamageTypes.PLAYER_ATTACK)
target 在 resolved requested/observed 集合中
```

仍无法完全区分其他 Mod 用相同 `playerAttack` source 追加的伤害；标记 `AMBIGUOUS_PLAYER_ATTACK`，Phase 05 不得无条件用于词条倍率。

不可变性：

- session 内部用可变列表；
- finalize 时复制为不可变列表；
- context 不暴露可变集合；
- 单条 observation 构造后不可变。

---

## 8. Handler 安装策略

### 8.1 审计事实

```text
ServerPlayNetworking.HANDLERS
public static Map<ResourceLocation, PlayChannelHandler>
```

- 不是 final；
- 实现为 `HashMap`；
- 每次 `ServerPlayNetworking.handle` 都 `HANDLERS.get`；
- 无 Handler 缓存；
- `registerGlobalReceiver` 直接 `put`，会覆盖；
- 不能使用 `registerGlobalReceiver`。

### 8.2 安装状态

```java
enum InstallationState {
    NOT_INSTALLED,
    INSTALLED,
    DEGRADED_OR_UNKNOWN
}
```

保存：

```text
installedMap
installedWrapper
wrappedOriginal          // 首轮捕获的内层 Handler，不自动等于 BC 原始 Handler
wrappedOrderingKnown     // 捕获时内层是否已确认调用顺序
installationState
innerOrderingKnown       // 内层调用顺序是否经过审计确认
```

### 8.2.1 Handler 身份分类

```java
enum HandlerIdentity {
    AUDITED_BETTER_COMBAT_ORIGINAL,
    OUR_WRAPPER,
    PREEXISTING_OTHER_WRAPPER,
    UNKNOWN_PREEXISTING,
    MISSING
}

enum OrderingKnowledge {
    KNOWN,
    UNKNOWN
}
```

身份规则：

| 身份 | 条件 | ordering |
|---|---|---|
| `AUDITED_BETTER_COMBAT_ORIGINAL` | 只有已有独立审计证据证明当前对象就是 BC 原始 Handler | `KNOWN` |
| `OUR_WRAPPER` | `current == installedWrapper` | 继承包装器的 `innerOrderingKnown` |
| `PREEXISTING_OTHER_WRAPPER` | 首次安装时发现当前不是已确认原始 Handler | `UNKNOWN` |
| `UNKNOWN_PREEXISTING` | 无法确认当前身份 | `UNKNOWN` |
| `MISSING` | current == null | 无桥接 |

**当前计划不假设 `wrappedOriginal` 必然是 BC 原始 Handler。**  
它只是“首次包装时捕获的内层对象”。

在锁定环境中，没有公开稳定的 Handler 类型标记可以证明 `AUDITED_BETTER_COMBAT_ORIGINAL`；因此：

- 首次安装默认 `UNKNOWN_PREEXISTING`；
- 若 `current` 是其他 Mod 先包装的 Handler，分类为 `PREEXISTING_OTHER_WRAPPER`；
- 只有外部审计明确证明原始对象身份时，才允许 `AUDITED_BETTER_COMBAT_ORIGINAL`；
- 该证明当前为 `NEEDS_VERIFICATION`，未获得时 `orderingKnown=false`；
- 不得通过反射拆解其他 Mod 包装链；
- 不得用类名猜测原始 Handler。

### 8.2.2 未知 Handler 的观察窗口

`orderingUnknown=true` 时：

- `observationWindowComplete=false`；
- 即使原始 Handler 正常返回；
- 即使没有观察到 `AttackEntityEvent`；
- 也不能推导 `NO_ATTACK_EVENT_OBSERVED`；
- 结果必须是 `UNKNOWN`；
- 不能声称攻击任务完成；
- 不能猜测任务队列行为；
- 已观察到的 `TARGET_OBSERVED`/`DAMAGE_*` 仍可保留，但覆盖不完整。

`orderingUnknown=true` 时允许观察结果：

| 已观察内容 | result | coverage |
|---|---|---|
| 无事件 | `UNKNOWN` | 不完整 |
| `AttackEntityEvent` | `TARGET_OBSERVED` | 不完整 |
| `LivingHurt`/`LivingDamage` | `DAMAGE_EVENT_OBSERVED` | 不完整 |
| 正向 LivingDamage | `DAMAGE_AMOUNT_OBSERVED_POSITIVE` | 不完整 |

`orderingKnown=true` 且覆盖完整时，才允许 `NO_ATTACK_EVENT_OBSERVED`。

### 8.3 安装规则

#### 首次安装

```text
if Better Combat absent: return

currentMap = ServerPlayNetworking.HANDLERS
current = currentMap.get(C2S_AttackRequest.ID)

if current == null:
    state = DEGRADED_OR_UNKNOWN
    return

original = current
wrapper = new BridgeHandler(original)
currentMap.put(C2S_AttackRequest.ID, wrapper)

installedMap = currentMap
installedWrapper = wrapper
wrappedOriginal = original
wrappedOrderingKnown = identity == AUDITED_BETTER_COMBAT_ORIGINAL
innerOrderingKnown = wrappedOrderingKnown
state = INSTALLED
```

首次安装时 `current` 可能是：

- 经独立审计确认的 Better Combat 原始 Handler（`AUDITED_BETTER_COMBAT_ORIGINAL`）；
- 其他 Mod 先包装的 Handler（`PREEXISTING_OTHER_WRAPPER`）；
- 无法确认的 preexisting Handler（`UNKNOWN_PREEXISTING`）。

分类规则：

```text
if current is independently proven Better Combat original:
    identity = AUDITED_BETTER_COMBAT_ORIGINAL
    orderingKnown = true
else if current is known other wrapper:
    identity = PREEXISTING_OTHER_WRAPPER
    orderingKnown = false
else:
    identity = UNKNOWN_PREEXISTING
    orderingKnown = false
```

首次安装包装当前 Handler，保留链；但只有 `orderingKnown=true` 才允许正常观察窗口。

#### 情况 A：current == installedWrapper

```text
不重复包装
state = INSTALLED
orderingKnown = installedWrapper.innerOrderingKnown
```

#### 情况 B：current == wrappedOriginal

```text
如果 wrappedOrderingKnown == true，可重新安装一次 wrapper
如果 wrappedOrderingKnown == false，不重新包装，DEGRADED_OR_UNKNOWN
```

原因：`wrappedOriginal` 只是捕获对象，不自动证明原始调用顺序。

#### 情况 C：其他未知 current

```text
不自动包装
state = DEGRADED_OR_UNKNOWN
orderingKnown = false
```

原因：

- 可能是其他 Mod 在我们外层；
- 可能其内部已经包含我们的 wrapper；
- 直接再包装会造成重复桥接；
- 不能为了自动恢复破坏其他 Mod 链；
- 不通过反射拆解。

### 8.4 Map 被整体替换

不能仅因 `installedMap != currentMap` 就包装。

必须先检查新 Map 的 `current`：

```text
current == installedWrapper  -> 已包含我们的 wrapper，不包装；继承 orderingKnown
current == wrappedOriginal   -> 仅 wrappedOrderingKnown=true 时重新安装
其他                          -> DEGRADED_OR_UNKNOWN；orderingKnown=false
```

### 8.5 生命周期场景

| 场景 | 行为 |
|---|---|
| 集成服务器首次启动 | 安装一次 |
| 集成服务器退出 | 清空 context；不卸载 wrapper |
| 集成服务器再次启动 | `current == installedWrapper`；不重复安装 |
| 专用服务器启动 | 同 |
| 专用服务器停止 | 清空 context；不卸载 |
| 其他 Mod 之前包装 | 首次安装包装当前链；orderingKnown=false；不推断 NO_ATTACK |
| 其他 Mod 之后包装 | 下次检查 current 为其他；不重复包装；桥接在内层；orderingUnknown |
| 其他 Mod 直接替换我们的 wrapper | current 未知；DEGRADED_OR_UNKNOWN；不自动恢复 |
| HANDLERS Map 整体替换 | 检查新 Map 的 current 身份；按 A/B/C |
| 我们重复安装 | identity 检查；不重复 |
| Better Combat 缺失 | 不加载 compat；不安装 |

任何 `orderingKnown=false` 的 context：

```text
observationWindowComplete=false
```

没有事件时必须是 `UNKNOWN`，不能推导 `NO_ATTACK_EVENT_OBSERVED`。

### 8.6 恢复策略

默认**不恢复原 Handler**。

理由：

- 原 Handler 可能是其他 Mod 的 wrapper；
- 恢复会移除其他 Mod 逻辑；
- 集成服务器重启后需要重新安装；
- wrapper 固定内存开销可接受；
- 服务器停止只需清 context。

### 8.7 安装线程

- 在 `ServerAboutToStartEvent`/`ServerStartedEvent`；
- 玩家加入前；
- `synchronized` 安装；
- 不在运行时修改 Map；
- 其他 Mod 若运行时改 Map，不在我们的控制范围。

---

## 9. 网络线程与服务端线程调度策略

### 9.1 线程事实

```text
PacketWrapper.handle
  -> 网络线程
  -> ServerPlayNetworking.handle
  -> BridgeHandler.receive
  -> 网络线程
```

`PacketWrapper` 没有：

```java
context.enqueueWork(...)
```

Better Combat 原始 Handler 内部：

```java
world.getServer().execute(...)
```

### 9.2 server.execute 限制

`MinecraftServer` 继承 `ReentrantBlockableEventLoop<TickTask>`。

`BlockableEventLoop.execute`：

```java
if (scheduleExecutables()) {
    tell(wrapRunnable(task));
} else {
    task.run();
}
```

`MinecraftServer.scheduleExecutables`：

```java
return super.scheduleExecutables() && !isStopped();
```

因此：

| 调用上下文 | 行为 |
|---|---|
| 网络线程 | 入队 |
| 服务端线程、空闲 | 立即执行 |
| 服务端线程、任务中 | 入队（reentrant） |
| 服务器已停止 | 立即在当前线程执行 |

`isStopped()` 检查后到 `execute` 之间仍可能停止，所以网络线程不能依赖 `execute` 保证“只在服务端线程执行”。

### 9.3 server.tell 审计

`MinecraftServer` 继承的 `tell` 签名为：

```java
public void tell(TickTask task)
```

`TickTask`：

```java
public class TickTask implements Runnable {
    public TickTask(int tick, Runnable runnable)
}
```

`server.tell` 的行为：

```java
pendingRunnables.add(task);
LockSupport.unpark(serverThread);
```

结论：

- 公开可调用；
- 参数是 `TickTask`，不是 `Runnable`；
- 只入队；
- 不在调用线程直接执行；
- 不检查 `isStopped`；
- 停服后任务可能永远不执行；
- 不能在停服后依赖它做清理；
- 但不会在网络线程直接访问世界。

`server.tell(Runnable)` 不存在；必须构造 `TickTask`。

#### 9.3.1 逐项 API 核验

| API | 实际签名 | 可见性 | 语义/注意 |
|---|---|---|---|
| `MinecraftServer.isRunning()` | `public boolean isRunning()` | public | 读取 `volatile boolean running`；启动为 true，`halt` 等停止路径置 false；不是“任务执行成功” |
| `MinecraftServer.isStopped()` | `public boolean isStopped()` | public | 读取 `private boolean stopped`；在 `runServer` finally 中置 true；**不是 volatile**，网络线程读取可能滞后 |
| `MinecraftServer.isSameThread()` | `public boolean isSameThread()` | public | `Thread.currentThread() == getRunningThread()` |
| `MinecraftServer.getTickCount()` | `public int getTickCount()` | public | 返回私有 `tickCount`；用于 `TickTask` 的 tick 元数据，不保证跨线程强一致 |
| `MinecraftServer.tell(TickTask)` | `public void tell(TickTask)` | public（继承） | 加入 `pendingRunnables` 并 unpark；不 inline；不检查 stopped |
| `MinecraftServer.execute(Runnable)` | `public void execute(Runnable)` | public（继承） | `scheduleExecutables()` 为 true 时入队，否则当前线程直接执行 |
| `TickTask` 构造 | `public TickTask(int tick, Runnable runnable)` | public class / public ctor | `getTick()`，`run()` 调用 Runnable |

补充：

- `MinecraftServer` 继承 `ReentrantBlockableEventLoop<TickTask>`。
- `server.tell(Runnable)` 不存在；泛型参数是 `TickTask`。
- `BlockableEventLoop` 有 `public void tell(Object)` 桥接，不能类型安全地传 `Runnable`。
- `executeIfPossible(Runnable)` 在 stopped 时抛 `RejectedExecutionException`；不作为正常调度方案。
- `submit`/`submitAsync`/`executeBlocking` 不作为本阶段方案（inline/阻塞/停止语义不适合）。
- `isStopped()` 的读取不能作为严格同步屏障；停服检查只是保守降级。
- `TickTask` 的 tick 值不参与业务 ID；只用于队列调度。
- `isRunning()` 读取 `volatile boolean running`；没有 synchronized。
- `isStopped()` 读取普通 `boolean stopped`；没有 synchronized/锁，且不是 volatile。
- `getTickCount()` 读取普通 `int tickCount`；没有同步保证。
- `execute`/`tell` 没有统一的跨线程屏障。

**NEEDS_VERIFICATION：**

- `isStopped` 的跨线程可见性没有 volatile 保证；不能依赖它做严格竞态消除。
- `getTickCount` 跨线程读取没有同步保证；只用于调度提示，不用于业务数据。
- 停服期间原始 Handler 内部 `execute` 的 inline 行为无法由我们验证/控制。

### 9.4 Tracker 线程所有权与请求 ID

#### 9.4.1 原则

`CombatAttackTracker` 的会话状态只能在服务端线程修改：

```text
active context
recent contexts
CombatAttackSession
TTL
cleanup
target sets
event counters
phase/result
```

网络线程只允许：

```text
读取/复制网络 buffer
解码 C2S_AttackRequest
生成一个独立的请求 ID
把请求交给服务端调度
```

网络线程**不允许**：

```text
修改 active context
创建 CombatAttackSession
写 recent
修改 TTL
读写 target sets
调用 tracker.finish
调用 tracker.cleanup
```

#### 9.4.2 请求 ID 生成

新增独立组件：

```java
final class CombatAttackIdGenerator {
    private static final AtomicLong NEXT = new AtomicLong();

    static long nextId() {
        return NEXT.incrementAndGet();
    }
}
```

职责：

- 只生成 `long` 请求 ID；
- 使用 `AtomicLong`；
- 无 Map、无 Player、无 session；
- 不修改 Tracker；
- 不接触 active/recent；
- 可在网络线程调用；
- 全局单调递增即可；
- 复合身份为 `(attacker UUID, attackId)`；
- 服务器重启后序列从头开始，不用于持久化/防重放。

网络线程：

```text
request = decode(copy)
attackId = CombatAttackIdGenerator.nextId()
schedule prepare(attackId, request)
```

**禁止**使用一个同时修改 active/recent 的 `tracker.nextToken(player)`。

如果未来需要 per-player 短 ID，必须使用独立的 `ConcurrentHashMap<UUID, AtomicLong>` 生成器，并且不能把该 Map 当作 Tracker 会话状态；当前计划不采用。

#### 9.4.3 数据所有权表

| 数据 | 写线程 | 读线程 | 说明 |
|---|---|---|---|
| C2S buffer 副本 | 网络线程 | 网络线程 | 解码后释放 |
| `CombatAttackRequest` | 网络线程 | 服务端 prepare | 不可变 DTO |
| `attackId` 生成器 | 任意线程 | 任意线程 | `AtomicLong`，无会话状态 |
| active context | 服务端线程 | 服务端事件 | 只由 Tracker prepare/finish 修改 |
| `CombatAttackSession` | 服务端线程 | 服务端事件 | 网络线程不可见 |
| recent contexts | 服务端线程 | 服务端线程 | 固定容量 |
| TTL | 服务端线程 | 服务端线程 | `ServerTickEvent` |
| cleanup | 服务端线程 | 服务端线程 | 退出/死亡/维度/停服 |
| target sets | 服务端线程 | 服务端事件 | 去重、容量上限 |

#### 9.4.4 执行流程

```text
网络线程：
    decode request
    attackId = generator.nextId()
    schedule prepare(attackId, request)

服务端线程 prepare(attackId):
    如果 active 存在，先设置旧 active endReason=INTERRUPTED_BY_NEW_REQUEST
    旧 phase=FINALIZED 或 UNKNOWN；不在 phase 中写 INTERRUPTED
    创建 CombatAttackSession(attackId)
    读取服务端 MotionSnapshot
    读取服务端 AttackHand/WeaponSnapshot
    写入 active map
    phase = PREPARED

服务端线程 markExecuting(attackId):
    只有 active.attackId == attackId
    phase = EXECUTING
    注意：表示“已进入原始 Handler 调用路径”，
          不表示 Better Combat 攻击任务已成功执行

原始 Handler：
    提交 Better Combat 攻击任务

服务端线程 finish(attackId):
    只有 active.attackId == attackId 且未 FINALIZED
    才生成不可变 CombatAttackContext
    写入 recent
    清理 active
    phase = FINALIZED

ServerTickEvent:
    TTL 过期 -> UNKNOWN + cleanup
```

#### 9.4.5 两名玩家并发

- attackId 全局递增，唯一；
- active map 以 attacker UUID 分区；
- 每个玩家独立 session；
- 服务端线程顺序处理任务；
- 一个玩家的事件不会附加到另一个玩家的 context；
- 不需要全局 `synchronized Tracker`。

#### 9.4.6 同一玩家连续请求

```text
request1 -> id1 -> prepare1 -> attack1 -> finish1
request2 -> id2 -> prepare2 -> attack2 -> finish2
```

规则：

1. `prepare2` 发现 active id1：
   - 如果 id1 未 finalize，设置 id1 `endReason=INTERRUPTED_BY_NEW_REQUEST`；
   - 不把 INTERRUPTED 写入 phase/result；
   - 不为 id1 伪造 HIT；
   - 不把 attack1 的后续事件附加到 id2。
2. 事件只附加到 active 的当前 id。
3. `finish(id1)` 迟到：
   - 如果 active 已是 id2，忽略 id1；
   - 不得 finalize id2。
4. `finish(id2)` 只完成 id2。
5. recent 容量固定；不无限保存旧 id。

#### 9.4.7 finish 先于攻击事件

可能发生：

- finish 任务先执行；
- 攻击事件后到；
- prepare 延迟；
- 任务队列/其他 Mod 干预。

保护：

- context finalize 后设置 `sealed=true`；
- 之后的事件不再附加；
- 迟到事件只记诊断，不修改结果；
- 如果 finish 早于 prepare，token 标 `UNKNOWN`；
- 如果 finish 在 prepare 后但攻击事件未到，context 只能记录当前观察，不得升级为 HIT；
- 不因为“没有事件”推断拒绝/落空。

---

### 9.5 最终调度

#### 9.5.1 停服边界

原始 Handler 内部仍会调用：

```java
server.execute(...)
```

桥接层不能控制它的内部调度。

因此：

- 服务器运行时：按正常调度；
- 检测到停止/不运行时：**桥接层保守跳过原始 Handler**，记录 `SKIPPED_SERVER_STOPPING`；
- 不建立 context；
- 不调度 prepare/finish；
- 不清空其他玩家 context（由 ServerStopping 统一清理）。

**这是有意的降级，不是正常成功语义。**

“原始 Handler 调用一次”原则适用于正常转发路径；停服路径明确记为 0 次 `SKIPPED_SERVER_STOPPING`，不允许之后重试，也不允许伪造攻击。

#### 9.5.2 正常路径伪代码

```text
decodeRequest 在网络线程（只读 buffer，不访问世界）
attackId = CombatAttackIdGenerator.nextId()

if (!server.isRunning() || server.isStopped()) {
    logSkippedStopping(attackId)       // 只写日志；不修改 Tracker 会话
    return                              // 保守：不调用原始 Handler
}

if (server.isSameThread()) {
    // 服务端线程：
    // 空闲时 execute 立即执行；任务中则入队
    server.execute(() -> tracker.prepare(attackId, player, request))
    server.execute(() -> tracker.markExecuting(attackId))

    try {
        original.receive(...)            // 只调用一次
    } finally {
        if (!server.isStopped()) {
            server.execute(() -> tracker.finish(attackId))
        } else {
            tracker.finishOnStoppedServer(attackId)
        }
    }
} else {
    // 网络线程/其他线程：
    // tell 只入队，不在调用线程执行
    server.tell(new TickTask(server.getTickCount(), () ->
        tracker.prepare(attackId, player, request)))
    server.tell(new TickTask(server.getTickCount(), () ->
        tracker.markExecuting(attackId)))

    try {
        original.receive(...)            // 只调用一次
    } finally {
        server.tell(new TickTask(server.getTickCount(), () ->
            tracker.finish(attackId)))
    }
}
```

#### 9.5.3 停服竞态

检查与调用之间仍可能发生停止。

因此：

- 停服检查只能缩小竞态窗口，不能严格消除；
- 如果停止发生在 `original.receive` 之前/期间，原始 Handler 仍可能执行其内部 `execute`；
- 如果 `isStopped()` 为 true，原始 Handler 的 `execute` 可能在网络线程内联；
- 桥接层**无法**在不修改第三方、不加 Mixin 的情况下控制这一点。

保守策略：

- 能检测到停止时不调用原始 Handler；
- 检测不到时保留原始行为；
- 桥接层自身绝不在网络线程访问世界/Tracker 会话；
- 任何不确定结果标 `UNKNOWN`；
- 不声称停服路径已经严格线程安全。

**结论：停服期间原始 Handler 的内部 `execute` 行为存在不可消除的第三方竞态，必须作为已知限制保留。**

#### 9.5.4 为什么不能简单全部用 `tell`

如果所有任务都只用 `tell`：

- 服务端线程空闲时，prepare 会延迟到下一次任务轮询；
- 原始 Handler 的 `server.execute` 可能立即执行攻击；
- 出现 attack 先于 prepare；
- 上下文缺失。

因此采用：

```text
服务端线程：execute（空闲立即，任务中入队）
网络线程：tell（只入队）
```

并要求 token 保护顺序异常。

#### 9.5.5 任务顺序结论

正常网络路径：

```text
tell(prepare)       -> 入队
tell(markExecuting) -> 入队
original.receive    -> server.execute(attack) -> 入队
tell(finish)        -> 入队
```

同一队列、同一提交线程：

```text
prepare -> markExecuting -> attack -> finish
```

可靠。

服务端线程空闲：

```text
execute(prepare)    立即
execute(markExecuting) 立即
original.execute    立即
execute(finish)     立即
```

顺序可靠。

服务端线程任务中：

```text
execute(prepare) 入队
execute(markExecuting) 入队
original.execute 入队
execute(finish) 入队
```

顺序可靠。

跨线程提交：

```text
没有全局 FIFO 保证
```

如果其他 Mod 包装后的原始 Handler：

- 同步执行攻击；
- 切换到其他线程；
- 异步延迟攻击；
- 不调用 `server.execute`；

则上面的顺序前提失效。  
此时 token 只能防止串线，不能保证 prepare/finish 围住攻击事件；结果必须标 `UNKNOWN`，不得伪造 `TARGET_OBSERVED`/positive。

服务器停止：

```text
tell 任务可能不执行
execute 可能内联
桥接保守跳过
```

---

### 9.6 线程边界

| 操作 | 允许线程 |
|---|---|
| 解码 `C2S_AttackRequest` 副本 | 网络线程 |
| 读取 `ServerPlayNetworking.HANDLERS` | 安装/网络线程读取 |
| `server.tell` | 任意线程（只入队） |
| `server.execute` | 只在服务端线程（或可接受的服务端任务） |
| 读取 Player/World/MotionSnapshot | 仅服务端线程 |
| Forge 攻击/伤害事件 | 服务端线程 |
| context prepare/finish | 服务端线程 |

### 9.7 攻击事件观察窗口

#### 打开

```text
服务端 prepare 完成后
```

此时 active context 建立，开始接受与当前 attackId 匹配的服务端事件。

#### 关闭

```text
服务端 finish(attackId) 执行时
```

finish 只关闭**当前桥接观察窗口**，不证明：

- 原始 Handler 已结束；
- 第三方任务已全部执行；
- 攻击目标已全部处理；
- 没有更晚的事件。

关闭时：

```text
sealed = true
```

#### 迟到事件

- sealed 之后到达的 `AttackEntityEvent`/`Living*Event` 不再修改 context；
- 只增加诊断计数/日志，不进入 target sets；
- 不允许重新打开 context；
- 不允许把迟到事件归属到新 token；
- 不允许为了等迟到事件而增加高频 tick 或无限缓存。

#### 无事件的语义

```text
没有观察到 AttackEntityEvent
```

只能说明：

```text
当前观察窗口内没有观察到，不等于攻击没有发生
```

如果同时存在：

- `requestedTargetsTruncated`
- `unresolvedRequestedEntityIds` 非空
- `observationWindowComplete=false`
- `orderingUnknown`

则结果必须是 `UNKNOWN`，不能是 `NO_ATTACK_EVENT_OBSERVED`。

只有：

```text
targetCoverage == COMPLETE
&& observationWindowComplete == true
&& 没有任何 AttackEntityEvent
```

才允许 `NO_ATTACK_EVENT_OBSERVED`，且仍不能称 MISS/拒绝。

#### 观察窗口完整性

```text
observationWindowComplete =
    正常 Better Combat 已知调用路径
    && 没有其他 Mod 改变 Handler 调度
    && token 顺序未冲突
    && finish 正常执行
```

任一不确定：

```text
observationWindowComplete = false
```

如果已经观察到事件：

- 保留 `TARGET_OBSERVED`/`DAMAGE_*`；
- 但 `observationWindowComplete=false`；
- 不声称完整覆盖全部目标/全部事件。

#### 时间边界

- 不新增每 tick 扫描；
- 不使用无界队列；
- finish 后不延长窗口；
- TTL 只用于清理，不用于补充观察；
- 迟到事件计数有上限，超过后只记录“dropped”。

---

## 10. 异常处理策略

### 10.1 异常分类

```text
A. 桥接解码异常
B. 桥接 prepare 异常
C. 桥接 finish 异常
D. 原始 Handler 正常返回
E. 原始 Handler 抛出异常
F. 原始 Handler 已入队攻击后抛出异常
G. 攻击任务执行异常
```

### 10.2 伪代码

```java
void bridgeReceive(...) {
    CombatAttackRequest request = null;
    Throwable bridgeFailure = null;

    try {
        request = adapter.decodeRequest(buf.copy());
    } catch (Throwable t) {
        bridgeFailure = t;
        logBridgeFailure(t);
    }

    // 只生成 ID，不修改 Tracker 会话状态
    long attackId = CombatAttackIdGenerator.nextId();

    // 保守停服策略：不调用原始 Handler，也不建立 context
    if (!server.isRunning() || server.isStopped()) {
        logSkippedStopping(attackId);
        return;
    }

    try {
        if (request != null) {
            schedulePrepare(server, attackId, player, request);
            scheduleExecuting(server, attackId);
        }
    } catch (Throwable t) {
        bridgeFailure = merge(bridgeFailure, t);
        logBridgeFailure(t);
    }

    Throwable originalFailure = null;
    boolean originalCalled = false;

    try {
        originalCalled = true;
        original.receive(server, player, handler, buf, sender); // exactly once
    } catch (Throwable t) {
        originalFailure = t;
        try {
            scheduleOriginalThrew(server, attackId);
        } catch (Throwable bridge) {
            bridgeFailure = merge(bridgeFailure, bridge);
        }
        throw t; // 原样传播；finally 只做 bridge 收尾
    } finally {
        try {
            scheduleFinish(server, attackId);
        } catch (Throwable t) {
            bridgeFailure = merge(bridgeFailure, t);
            logBridgeFailure(t);
        }

        if (bridgeFailure != null) {
            if (originalFailure != null) {
                originalFailure.addSuppressed(bridgeFailure);
            } else {
                logBridgeFailure(bridgeFailure);
            }
        }
    }
}
```

`originalCalled` 为 true 后不得再次调用原始 Handler。  
`finally` 中的 bridge 失败只记录/附加 suppressed，不得覆盖原始异常。  
停服路径是 0 次调用，必须单独记录 `SKIPPED_SERVER_STOPPING`，不得重试。

### 10.3 路径表

| 路径 | 原始 Handler | 结果 |
|---|---|---|
| 解码失败 | 仍调用一次 | 无 context/`UNKNOWN`；不阻断攻击 |
| prepare 失败 | 仍调用一次 | context `UNKNOWN`；不阻断攻击 |
| 原始正常返回 | 调用一次 | finish；按观察结果 |
| 原始抛异常 | 调用一次后异常传播 | finish；`originalHandlerThrew=true`；结果 `UNKNOWN` 或已有观察 |
| 原始入队攻击后抛异常 | 攻击任务可能仍执行 | finish 在 attack 后；按观察结果；不标记成功 |
| 攻击任务异常 | 由服务端任务循环捕获 | finish；`UNKNOWN/PARTIAL`；不伪造 |
| finish 失败/超时 | 不影响原始 | TTL 清理；`UNKNOWN` |
| 玩家退出 | 原始仍调用；context 不建立 | 清理；`UNKNOWN` |
| 检测到服务器停止 | 不调用（0 次） | `SKIPPED_SERVER_STOPPING`；不建 context |
| 检查后服务器才停止 | 原始可能调用；原始内部 execute 竞态 | 保留第三方限制；标 `UNKNOWN`，不称安全 |

### 10.4 原始调用次数

- 正常转发路径：`original.receive` 只能调用一次。
- 服务器已停止/不运行：0 次，记录 `SKIPPED_SERVER_STOPPING`。
- 不允许在异常重试中再次调用。
- 不允许用包装器重复模拟攻击。

---

## 11. 事件监听策略

### 11.1 优先级

Forge 47.4.0 / EventBus 6.0.5：

```text
HIGHEST, HIGH, NORMAL, LOW, LOWEST
```

没有 `MONITOR`。

最终：

```java
@SubscribeEvent(
    priority = EventPriority.LOWEST,
    receiveCanceled = true
)
```

### 11.2 监听器

| 事件 | 行为 |
|---|---|
| `AttackEntityEvent` | 服务端、active token、目标在 requested 集合；记录 observed/canceled |
| `LivingAttackEvent` | 服务端、直接近战 source；记录 observed/canceled |
| `LivingHurtEvent` | 服务端、直接近战 source；记录 observed amount/canceled |
| `LivingDamageEvent` | 服务端、直接近战 source；记录 amount/canceled；positive 仅当观察时未取消且 >0 |
| `LivingDeathEvent` | 可选；只记录死亡，不当攻击证明 |
| `ServerTickEvent` | TTL/清理 |
| `PlayerLoggedOutEvent` | 清理 UUID |
| `PlayerEvent.Clone` | 清理/迁移 |
| `PlayerChangedDimensionEvent` | 清理服务端 view |
| `ServerStoppingEvent` | 清空 active/recent |

### 11.3 DamageSource 过滤

```java
DamageSource source = event.getSource();
Entity attacker = source.getEntity();
Entity direct = source.getDirectEntity();

boolean directMelee =
    attacker == player
    && direct == player
    && source.is(DamageTypes.PLAYER_ATTACK);
```

同时：

```text
context.activeToken == token
target 在 requested/attackObserved 集合
!level.isClientSide
```

排除：

- 荆棘；
- 弹射物；
- 火焰/摔落/爆炸；
- 环境伤害；
- 其他 Mod 魔法/间接伤害；
- 原版横扫次要目标（无 AttackEntity，标记 collateral）；
- 客户端预测。

### 11.4 观测值

记录：

```text
event.getClass()
event.getAmount()
event.isCanceled()
event.getEntity().getUUID()
event.getSource()
```

不修改：

```text
event.setAmount
event.setCanceled
```

`LivingDamageEvent` 的 amount 是观察值，不是实际生命减少。

---

## 12. 类结构与职责

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
  CombatRequestedTarget.java
  CombatDamageEventObservation.java
  CombatTargetDamageObservation.java
  CombatWeaponSnapshot.java
  CombatAttackRequest.java
  CombatAttackIdGenerator.java
  CombatAttackContext.java
  CombatAttackSession.java
  CombatAttackTracker.java
  CombatBridge.java

src/main/java/com/MrGhost/parkourCombat/compat/bettercombat/
  BetterCombatCompat.java
  BetterCombatAttackAdapter.java
```

| 类 | 职责 |
|---|---|
| `CombatAttackPhase` | 请求生命周期 |
| `CombatAttackResult` | 观察结果 |
| `CombatAttackEndReason` | 独立结束/诊断原因，不混入 phase/result |
| `CombatAttackSource` | `BETTER_COMBAT` |
| `CombatTargetTrust` | 目标可信度 |
| `CombatTargetResolution` | raw entityId 的解析结果 |
| `CombatTargetCoverage` | 请求目标覆盖完整性 |
| `CombatTargetMatch` | DIRECT/PART/PARENT/UNMATCHED/COLLATERAL |
| `CombatRequestedTarget` | raw ID、part/parent UUID、resolution |
| `CombatDamageEventObservation` | 单次事件观察 |
| `CombatTargetDamageObservation` | 单目标有界观察列表 |
| `CombatWeaponSnapshot` | 不可变武器摘要 |
| `CombatAttackRequest` | 中立请求 DTO |
| `CombatAttackIdGenerator` | 线程安全 `AtomicLong` 请求 ID 生成，不持有会话状态 |
| `CombatAttackContext` | 不可变最终上下文 |
| `CombatAttackSession` | 包内可变会话，生成 context |
| `CombatAttackTracker` | per-player token、active/recent、TTL、清理 |
| `CombatBridge` | Forge 事件、prepare/finish、生命周期 |
| `BetterCombatCompat` | 可选 compat、HANDLERS 安装/降级 |
| `BetterCombatAttackAdapter` | C2S 解码、AttackHand/Weapon 转换 |

不新增 `CombatAttackEvent` 类；Phase 04 只提供 tracker/context。

---

## 13. 完整测试矩阵

> 当前全部为 `DESIGNED`。  
> 本阶段没有 Java 实现、没有编译、没有实机测试。

### 13.1 攻击请求

| ID | 用例 | 预期 | 状态 |
|---|---|---|---|
| RQ-01 | 正常请求 | token/prepare | DESIGNED |
| RQ-02 | 空 entityIds | `NO_REQUESTED_TARGETS` | DESIGNED |
| RQ-03 | 无效实体 ID | `NO_ATTACK_EVENT_OBSERVED` | DESIGNED |
| RQ-04 | 多目标 | 一个 token 多目标 | DESIGNED |
| RQ-05 | 重复目标 | 去重，不重复 token | DESIGNED |
| RQ-06 | 连续攻击 | token 串行，旧 context 完成/中断 | DESIGNED |
| RQ-07 | 两名玩家同时 | UUID 隔离 | DESIGNED |
| RQ-08 | feint 不发请求 | 无 context | DESIGNED |

### 13.2 攻击事件

| ID | 用例 | 预期 | 状态 |
|---|---|---|---|
| EV-01 | AttackEntityEvent 正常 | `TARGET_OBSERVED`，不称 HIT | DESIGNED |
| EV-02 | AttackEntityEvent 取消 | `TARGET_OBSERVED` + canceled | DESIGNED |
| EV-03 | LivingAttackEvent 取消 | 无 Hurt/Damage | DESIGNED |
| EV-04 | LivingHurtEvent 取消 | `DAMAGE_EVENT_OBSERVED` + canceled | DESIGNED |
| EV-05 | LivingDamageEvent 取消 | 不升级 positive | DESIGNED |
| EV-06 | LivingDamage amount 0 | 不升级 positive | DESIGNED |
| EV-07 | 无敌帧 | Attack/LivingAttack 可见，无伤害 | DESIGNED |
| EV-08 | 免疫伤害 | 无 Hurt/Damage | DESIGNED |
| EV-09 | 创造模式玩家 | 无实际伤害 | DESIGNED |
| EV-10 | 原版横扫次要目标 | 标记 collateral，不创建 token | DESIGNED |
| EV-11 | 其他 Mod 追加伤害 | 标记 ambiguous，不称最终伤害 | DESIGNED |

### 13.3 Handler 生命周期

| ID | 用例 | 预期 | 状态 |
|---|---|---|---|
| HL-01 | 首次安装 | `INSTALLED` | DESIGNED |
| HL-02 | 重复安装 | 不重复包装 | DESIGNED |
| HL-03 | 集成服务器反复启动 | 不重复包装 | DESIGNED |
| HL-04 | 专用服务器启停 | 清 context，不卸载 | DESIGNED |
| HL-05 | 其他 Mod 先包装 | 保留链 | DESIGNED |
| HL-06 | 其他 Mod 后包装 | 不重复包装 | DESIGNED |
| HL-07 | 其他 Mod 替换 | DEGRADED_OR_UNKNOWN | DESIGNED |
| HL-08 | HANDLERS Map 替换 | 检查 current 身份，不盲目包装 | DESIGNED |
| HL-09 | Better Combat 缺失 | 不加载 compat | DESIGNED |
| HL-10 | 未知 preexisting Handler | orderingUnknown；无事件 -> UNKNOWN | DESIGNED |
| HL-11 | 已审计原始 Handler | orderingKnown 才允许正常窗口 | DESIGNED |

### 13.4 线程与异常

| ID | 用例 | 预期 | 状态 |
|---|---|---|---|
| TH-01 | 正常 prepare/attack/finish | FIFO/token | DESIGNED |
| TH-02 | 停服期间请求 | 不 inline 访问世界 | DESIGNED |
| TH-03 | 任务延迟 | TTL；不伪造完成 | DESIGNED |
| TH-04 | 原始 Handler 异常 | 只调用一次；异常传播 | DESIGNED |
| TH-05 | 桥接解码失败 | 原 Handler 仍调用 | DESIGNED |
| TH-06 | 玩家退出 | 清理 | DESIGNED |
| TH-07 | 玩家死亡 | 不建立虚假 HIT | DESIGNED |
| TH-08 | 维度切换 | 清理 | DESIGNED |
| TH-09 | 两玩家并发 | UUID 隔离 | DESIGNED |
| TH-10 | 嵌套事件 | collateral/不新建 token | DESIGNED |

### 13.5 Tracker 线程与调度

| ID | 用例 | 预期 | 状态 |
|---|---|---|---|
| TR-01 | 网络线程生成请求 ID | 只改 `AtomicLong`；不触碰 Tracker 会话 | DESIGNED |
| TR-02 | 网络线程不得创建 session | 只有 prepare 服务端任务创建 | DESIGNED |
| TR-03 | 同一玩家连续快速攻击 | token 串行；旧 context 中断 | DESIGNED |
| TR-04 | 两名玩家并发 | UUID 隔离；ID 全局唯一 | DESIGNED |
| TR-05 | prepare/attack/finish 实际顺序 | FIFO 或 token 保护 | DESIGNED |
| TR-06 | finish 先执行 | context sealed；晚到事件忽略 | DESIGNED |
| TR-07 | prepare 延迟 | 事件无 active 时忽略；不伪造 | DESIGNED |
| TR-08 | 任务延迟 | TTL；不伪造完成 | DESIGNED |
| TR-09 | 集成服务器反复启动 | 不重复安装；无残留 context | DESIGNED |
| TR-10 | 专用服务器停止/重启 | 清 context；wrapper 幂等 | DESIGNED |

### 13.6 停服与原始 Handler

| ID | 用例 | 预期 | 状态 |
|---|---|---|---|
| SD-01 | 停服前检测到停止 | 跳过原始 Handler；`SKIPPED_SERVER_STOPPING` | DESIGNED |
| SD-02 | 检查后立即停止 | 已知第三方竞态；标 `UNKNOWN` | DESIGNED |
| SD-03 | 原始 Handler 在停服期间 execute | 不声称安全；记录限制 | DESIGNED |
| SD-04 | 原始 Handler 抛异常 | 原异常传播；bridge 失败不覆盖 | DESIGNED |
| SD-05 | 原始 Handler 已入队后抛异常 | finish 在 attack 后；结果不称成功 | DESIGNED |
| SD-06 | finish 调度失败 | 不影响原始；TTL 清理 | DESIGNED |
| SD-07 | 网络线程停服请求 | 不触碰 Tracker session | DESIGNED |

### 13.7 集成/专用/多人

| ID | 用例 | 状态 |
|---|---|---|
| IT-01 | 普通近战 | DESIGNED |
| IT-02 | 不同武器 | DESIGNED |
| IT-03 | 主手/副手 | DESIGNED |
| IT-04 | 连续攻击 | DESIGNED |
| IT-05 | 跑酷动作期间攻击 | DESIGNED |
| IT-06 | 动作结束/中断 | DESIGNED |
| IT-07 | 集成服务器 | DESIGNED |
| IT-08 | 专用服务器 | DESIGNED |
| IT-09 | 多人隔离 | DESIGNED |
| IT-10 | BC 缺失启动 | DESIGNED |

### 13.8 目标解析、截断与多目标伤害

| ID | 用例 | 预期 | 状态 |
|---|---|---|---|
| TG-01 | raw entityId 可解析 | UUID、`RESOLVED_ALIVE` | DESIGNED |
| TG-02 | raw entityId 不存在 | `NOT_FOUND`，不当作无事件 | DESIGNED |
| TG-03 | raw entityId 已死亡 | `RESOLVED_NOT_ALIVE`，不算有效目标 | DESIGNED |
| TG-04 | 跨维度/其他 level | 不扫描；`NOT_FOUND/UNRESOLVED` | DESIGNED |
| TG-05 | boss part | `PART_RESOLVED` | DESIGNED |
| TG-06 | 重复 raw ID | 去重 UUID，记录 duplicate 计数 | DESIGNED |
| TG-07 | rawCount > 64 | `requestedTargetsTruncated=true` | DESIGNED |
| TG-08 | rawCount 超大/超过 readableBytes | 有界解析失败；原 Handler 仍调用 | DESIGNED |
| TG-09 | 截断且无事件 | `UNKNOWN`，不称 NO_ATTACK/MISS | DESIGNED |
| TG-10 | 截断但有事件 | 保留观察；coverage=TRUNCATED | DESIGNED |
| PT-01 | 父实体 ID | EVENT target parent；DIRECT_MATCH | DESIGNED |
| PT-02 | 龙部件 ID | EVENT target part；PART_MATCH | DESIGNED |
| PT-03 | 部件 parent UUID | 父 UUID 作为关联，不伪造普通目标 | DESIGNED |
| PT-04 | 部件无可靠 parent | PART_TARGET_UNMATCHED；coverage=PARTIAL_PART | DESIGNED |
| PT-05 | part UUID 与 parent UUID 不一致 | 不自动升级；记录不匹配 | DESIGNED |
| DMG-01 | 多目标各自伤害 | 逐目标记录，不求和 | DESIGNED |
| DMG-02 | 同一目标多次 hurt/damage | 计数；不生成新 attack | DESIGNED |
| DMG-03 | 单目标超过 8 次 | 截断标记；不无界增长 | DESIGNED |
| DMG-04 | 其他 Mod 同 source 追加伤害 | `ambiguousSourceObserved` | DESIGNED |
| DMG-05 | LivingHurt 与 LivingDamage 金额 | 分别记录，不作为最终伤害 | DESIGNED |
| DMG-06 | 目标数量超过 64 | 目标观察截断；不伪造完整 | DESIGNED |
| DMG-07 | 每目标 8 条观察 | list 不扩容；truncated=true | DESIGNED |
| DMG-08 | 同一目标 LivingHurt + LivingDamage 多次 | 逐条保存，不覆盖/不求和 | DESIGNED |
| DMG-09 | ambiguous source | 派生标记，不混入最终伤害 | DESIGNED |

### 13.9 生命周期/结束原因

| ID | 用例 | 预期 | 状态 |
|---|---|---|---|
| ER-01 | 新请求打断旧请求 | endReason=`INTERRUPTED_BY_NEW_REQUEST`，不生成 HIT | DESIGNED |
| ER-02 | prepare 失败 | endReason=`PREPARE_FAILED`；result=`UNKNOWN` | DESIGNED |
| ER-03 | 原始 Handler 抛异常 | endReason=`ORIGINAL_HANDLER_THREW`；异常传播 | DESIGNED |
| ER-04 | finish 调度失败 | endReason=`FINISH_SCHEDULE_FAILED`；不覆盖原异常 | DESIGNED |
| ER-05 | TTL 过期 | endReason=`TTL_EXPIRED`；result=`UNKNOWN` | DESIGNED |
| ER-06 | 玩家退出/死亡/维度 | 对应 endReason；清理 | DESIGNED |
| ER-07 | 观察窗口不完整 | endReason=`OBSERVATION_WINDOW_INCOMPLETE` | DESIGNED |
| ER-08 | Handler 降级 | endReason=`HANDLER_DEGRADED` | DESIGNED |



```text
DESIGNED -> IMPLEMENTED -> COMPILED -> TESTED
-> CLIENT-TESTED / SERVER-TESTED / MULTIPLAYER-TESTED -> VERIFIED
```

编译通过不能写成实机验证。

---

## 14. 风险清单

| 风险 | 等级 | 说明 | 缓解 |
|---|---|---|---|
| 无公开服务端攻击事件 | HIGH | 只能内部 HANDLERS 包装 | 保守安装；降级 |
| HANDLERS 非 final | HIGH | 其他 Mod 可替换 Map | 检查 current 身份 |
| 其他 Mod 替换 Handler | HIGH | 桥接可能失效 | DEGRADED；不自动恢复 |
| 无服务端空间验证 | HIGH | 无距离/LOS/Hitbox | 不生成 SPATIALLY_VALIDATED |
| 客户端目标不可信 | HIGH | entityIds 客户端提供 | 仅 CLIENT_REPORTED |
| raw ID 解析失败/截断 | HIGH | 目标集合不完整，不能当作无事件 | unresolved/truncated 强制 UNKNOWN |
| 大数组解码 | MEDIUM | 原始 Handler 仍可能分配完整数组 | 桥接副本有界解析，不改原始行为 |
| 多目标伤害混合 | HIGH | 单 float 求和会误称最终伤害 | 逐目标有界观察，不求和 |
| 观察窗口关闭 | MEDIUM | finish 不证明第三方任务结束 | sealed；不完整结果 UNKNOWN |
| 无法证明生命减少 | HIGH | 无 setHealth 后置事件 | 不定义 HEALTH_REDUCED |
| 无敌帧/免疫 | MEDIUM | Attack/LivingAttack 先于伤害 | 分层观察 |
| 原版横扫 | MEDIUM | 次要目标无 AttackEntity | collateral |
| 其他 Mod 追加伤害 | MEDIUM | source 可混入 | ambiguous；不称最终 |
| MONITOR 不存在 | MEDIUM | 只有五级 | LOWEST + receiveCanceled |
| 同优先级顺序不保证 | MEDIUM | LOWEST 不是绝对最终 | 只读；不称绝对 |
| 停服 inline | HIGH | 原始 Handler 内部 `server.execute` 不受桥接控制；检查后停止仍有竞态 | 检测到停止时跳过原始 Handler；剩余竞态标 `UNKNOWN`，不称严格安全 |
| Tracker 网络线程写入 | HIGH | `nextToken` 若改 active/recent 会竞态 | ID 用 `CombatAttackIdGenerator`；会话只服务端线程 |
| 任务延迟 | MEDIUM | finish 可能迟到 | token/TTL |
| 连续请求 | MEDIUM | active 覆盖 | token 串行 |
| finish 先于事件 | MEDIUM | 归档错误 | sealed + token 诊断 |
| 嵌套攻击 | MEDIUM | 事件嵌套 | 目标白名单 |
| feint 不可观测 | MEDIUM | 无 C2S | 明确限制 |
| BC 缺失 | MEDIUM | compat 类加载 | 隔离 + ModList |
| Handler 异常 | MEDIUM | 原始异常必须保留 | finally + rethrow |
| 内存 | LOW | context 限制 | active 1/recent 8/TTL |
| 性能 | LOW | 无世界扫描 | 请求/事件驱动 |

---

## 15. 实施顺序

### Step 0：计划批准

- 审查本文件；
- 确认五项修正；
- 不修改依赖。

### Step 1：纯数据模型

新增：

```text
combat/CombatAttackPhase.java
combat/CombatAttackResult.java
combat/CombatAttackEndReason.java
combat/CombatAttackSource.java
combat/CombatTargetTrust.java
combat/CombatTargetResolution.java
combat/CombatTargetCoverage.java
combat/CombatRequestedTarget.java
combat/CombatTargetDamageObservation.java
combat/CombatWeaponSnapshot.java
combat/CombatAttackRequest.java
combat/CombatAttackIdGenerator.java
combat/CombatAttackContext.java
```

测试：

- 状态推导；
- target trust 不升级；
- raw ID 解析/截断；
- 多目标伤害观察；
- health reduction 永远 false；
- context 不可变；
- ID generator 只改 AtomicLong，不触碰 Tracker 会话。

### Step 2：Tracker/Session

新增：

```text
combat/CombatAttackSession.java
combat/CombatAttackTracker.java
```

实现：

- per-player token；
- active/recent；
- TTL；
- 迟到 finish；
- 清理；
- 所有会话状态只允许服务端线程修改；
- 网络线程不调用 session/tracker 会话方法。

### Step 3：Better Combat compat

新增：

```text
compat/bettercombat/BetterCombatCompat.java
compat/bettercombat/BetterCombatAttackAdapter.java
```

实现：

- ModList guard；
- HANDLERS 保守安装；
- 只包装 C2S；
- 副本解码；
- finally 调用原 Handler；
- 不恢复；
- 停服检测/降级。

### Step 4：Forge Bridge

新增：

```text
combat/CombatBridge.java
```

实现：

- LOWEST + receiveCanceled；
- 只读事件；
- DamageSource 过滤；
- 目标分层；
- TTL/清理。

### Step 5：调度与异常

- tell/execute 混合方案；
- ID 生成与 Tracker 分离；
- token/sealed；
- original 异常语义；
- 停服/退出/死亡；
- 停服竞态降级。

### Step 6：验证

```text
compileJava
test
build
runClient
runServer
双客户端
BC 缺失
集成服务器重启
```

### Step 7：文档

- 更新 Phase 04 实现/测试报告；
- 记录实际状态；
- 不进入 Phase 05。

---

## 16. 回滚策略

```text
Step 1-2：删除 combat 数据/tracker 文件
Step 3：删除 compat 包和安装调用
Step 4：删除 bridge 注册
Step 5：回退调度代码
```

由于不修改：

- ParCoolAdapter；
- MotionTracker；
- MotionHistory；
- FallStateMachine；
- ParkourActionTracker；
- 依赖；
- build 文件；

回滚不会破坏 Phase 02/03/03.5。

---

## 17. 明确不属于 Phase 04

- 特殊攻击分类。
- 攻击优先级/互斥。
- 额外伤害。
- 攻击动画扩展。
- 词条系统。
- 新 Hitbox。
- 新网络包。
- 修改 Better Combat 伤害结算。
- 修改 ParCool 移动。
- Phase 05 行为。

---

## 18. 最终结论

# `CONDITIONALLY_READY`

不是 `READY_FOR_CHATGPT_REVIEW`。

原因：

- Tracker 线程所有权已经拆分并可证明；
- 请求 ID 生成已改为独立 `AtomicLong`；
- 正常 prepare/attack/finish 顺序有队列证据和 token 保护；
- 但 **服务器停止期间原始 Handler 内部的 `server.execute` 竞态无法由桥接层严格消除**；
- 该竞态是 Better Combat/Forge 内部行为，不修改第三方、不使用 Mixin 时没有严格保证；
- 因此不能声明完整线程安全。

计划已完成：

- 生命周期、观察结果、结束原因三者分离；
- 删除过强 `DAMAGE_RESOLVED`；
- raw entityId → UUID 的服务端有界解析；
- 原始目标数量、捕获数量、截断、重复、未解析分层；
- 部件/父实体（part/parent）解析与匹配状态；
- 多目标逐目标事件列表观察，禁止求和/最终伤害；
- 观察窗口 sealed、迟到事件忽略；
- Handler 身份分类与 orderingUnknown 降级；
- Tracker 会话只允许服务端线程修改；
- 请求 ID 使用独立 `AtomicLong`；
- 正常路径使用 `tell`/`execute` 混合调度；
- HANDLERS 保守身份安装；
- 原始 Handler 正常路径异常语义明确；
- 停服路径保守跳过原始 Handler 并降级。

仍存在且必须接受的风险：

- Better Combat 无公开服务端攻击事件；
- 原始 Handler 身份无法由公开 API 稳定证明；`AUDITED_BETTER_COMBAT_ORIGINAL` 为 `NEEDS_VERIFICATION`；
- 未确认 Handler 的 orderingUnknown 使无事件结果只能 `UNKNOWN`；
- `HANDLERS` 是内部可变 Map；
- 其他 Mod 替换后可能 `DEGRADED_OR_UNKNOWN`；
- 无服务端空间验证；
- 无法证明实际生命值减少；
- feint 不可服务端观测；
- 目标截断/未解析/部件无法关联时不能得出完整结论；
- **停服检查后原始 Handler 仍存在内部 `execute` 竞态**。

这些风险已明确记录，未伪装成已验证能力。  
本计划不宣布 Gate 2 获得编码授权；等待 ChatGPT 审查和用户明确批准。

这些风险已明确记录，未伪装成已验证能力。  
本计划不宣布 Gate 2 获得编码授权；等待 ChatGPT 审查和用户明确批准。

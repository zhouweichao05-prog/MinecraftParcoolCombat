# PHASE04_GATE2_IMPLEMENTATION_REPORT.md

> ParkourCombat Phase 04 — Gate 2 Better Combat attack bridge  
> 实施依据：`docs/PHASE04_GATE2_IMPLEMENTATION_PLAN.md`（Gate 2.2）  
> 编码状态：IMPLEMENTED / COMPILED / TESTED（单元）/ 有限 CLIENT-TESTED / 启动级 SERVER-TESTED  
> 未 VERIFIED；未提交 Git commit；未进入 Phase 05

---

## 1. 实际新增和修改文件

### 新增源码

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

```text
src/main/java/com/MrGhost/parkourCombat/compat/bettercombat/
  BetterCombatCompat.java
  BetterCombatAttackAdapter.java
```

### 新增测试

```text
src/test/java/com/MrGhost/parkourCombat/combat/
  CombatAttackRequestTest.java
  CombatTargetDamageObservationTest.java
src/test/java/com/MrGhost/parkourCombat/compat/bettercombat/
  BetterCombatAttackAdapterTest.java
```

### 新增文档/证据

```text
docs/PHASE04_GATE2_IMPLEMENTATION_REPORT.md
docs/logs/compileJava-phase04-gate2.log
docs/logs/test-phase04-gate2.log
docs/logs/build-phase04-gate2.log
docs/logs/runClient-phase04-gate2*.log
docs/logs/runServer-phase04-gate2*.log
```

### 修改的既有源码

```text
无
```

未修改：

- `ParCoolActionAdapter`
- `MotionTracker`
- `MotionHistory`
- `FallStateMachine`
- `ParkourActionTracker`
- `ParkourAdapterEvents`
- 任何既有网络/HUD 代码
- `build.gradle`、`gradle.properties`、`mods.toml`
- 第三方 JAR

---

## 2. 核心类职责

| 类 | 职责 |
|---|---|
| `CombatAttackPhase` | 请求生命周期 |
| `CombatAttackResult` | 观察结果 |
| `CombatAttackEndReason` | 独立结束/诊断原因 |
| `CombatAttackSource` | `BETTER_COMBAT` |
| `CombatTargetTrust` | 目标可信度分级 |
| `CombatTargetResolution` | raw entityId 解析结果 |
| `CombatTargetCoverage` | 目标覆盖完整性 |
| `CombatTargetMatch` | DIRECT/PART/PARENT/UNMATCHED/COLLATERAL |
| `CombatWeaponSnapshot` | 不可变武器摘要 |
| `CombatAttackRequest` | 有界请求 DTO |
| `CombatAttackIdGenerator` | `AtomicLong` 请求 ID |
| `CombatAttackContext` | 不可变最终上下文 |
| `CombatRequestedTarget` | raw ID、UUID、parent UUID、resolution |
| `CombatDamageEventObservation` | 单次事件观察 |
| `CombatTargetDamageObservation` | 单目标有界观察列表 |
| `CombatAttackSession` | 服务端线程可变会话，生成 context |
| `CombatAttackTracker` | per-player active/recent/TTL/清理 |
| `CombatBridge` | Forge 事件、服务端调度、只读观察 |
| `CombatWeaponProvider` | 隔离 Better Combat 武器快照 |
| `BetterCombatCompat` | 可选 Handler 安装/身份/降级 |
| `BetterCombatAttackAdapter` | C2S 有界解码、武器快照 |

---

## 3. Handler 身份识别和安装结果

`BetterCombatCompat` 首次安装时：

```text
installed with orderingUnknown=true
```

实际客户端/专用服务器日志均显示：

```text
[BetterCombatCompat] installed with orderingUnknown=true
```

原因：

- 没有公开稳定 API 证明当前对象是 Better Combat 原始 Handler；
- `wrappedOriginal` 只是捕获对象，不自动等于原始 Handler；
- 未使用反射拆解其他 Mod 包装链；
- 未使用 `registerGlobalReceiver` 覆盖；
- 当前状态：

```text
installationState = DEGRADED_OR_UNKNOWN
handlerIdentity = UNKNOWN_PREEXISTING
orderingKnown = false
```

结果：

- Handler 桥接已安装并实际收到攻击请求；
- 无事件时不会因为 Handler 正常返回而推导 `NO_ATTACK_EVENT_OBSERVED`；
- 只有观察到的攻击/伤害事件被记录；
- 未知 Handler 的观察窗口标记不完整。

未实现 `AUDITED_BETTER_COMBAT_ORIGINAL` 的自动识别；这是计划中的已知限制，不是伪造结论。

---

## 4. 请求解析及目标解析结果

### 请求解析

`BetterCombatAttackAdapter.decodeRequest` 使用有界解析：

```text
comboCount
isSneaking
selectedSlot
rawCount
capturedCount = min(rawCount, 64)
first 64 VarInt IDs
skip remaining
```

不使用 Better Combat 原始 `C2S_AttackRequest.read` 作为桥接副本解析，避免桥接层再次分配完整数组。原始 Handler 仍按 Better Combat 原有行为解码。

### 目标解析

`CombatAttackSession.prepare` 在服务端线程：

```text
player.serverLevel()
level.getEntity(rawId)
level.getEntityOrPart(rawId)
Entity.getUUID()
Entity.isAlive()
PartEntity.getParent()
```

不扫描世界、不跨维度搜索。

记录：

```text
rawTargetCount
capturedTargetCount
requestedTargetsTruncated
requestedTargetsHaveDuplicates
requestedTargetsDuplicateCount
requestedTargetResolutions
requestedTargets
partTargetUuids
partParentUuids
partTargetUnmatched
unresolvedRequestedEntityIds
requestedTargetsComplete
targetCoverage
```

实际客户端攻击 smoke test：

```text
prepared attackId=1 player=Dev raw=0 captured=0 truncated=false orderingKnown=false
finished attackId=1 result=NO_REQUESTED_TARGETS phase=FINALIZED coverage=COMPLETE attackEvents=0 damageEvents=0
```

这说明 C2S 请求已实际进入桥接层；本次攻击没有目标，未验证目标解析和伤害事件。

---

## 5. 伤害观察机制

服务端 Forge 事件：

- `AttackEntityEvent`
- `LivingAttackEvent`
- `LivingHurtEvent`
- `LivingDamageEvent`

全部使用：

```java
@SubscribeEvent(
    priority = EventPriority.LOWEST,
    receiveCanceled = true
)
```

只读观察，不取消、不修改 amount。

每个服务端匹配目标：

```text
CombatTargetDamageObservation
  targetUuid
  List<CombatDamageEventObservation>
  totalEventCount
  truncated
```

每条：

```text
eventType
amountAtObservation
canceledAtObservation
positiveAtObservation
sourceClassification
observationTick
```

限制：

- 最多 64 个目标；
- 每目标最多 8 条观察；
- 超出只增加 total 并设置 truncated；
- 不合并、不求和、不跨目标累加；
- 不称最终伤害；
- 不称实际生命值减少。

当前实际测试只覆盖空目标攻击，未触发 LivingAttack/Hurt/Damage 事件。

---

## 6. 线程所有权和停服处理

### 线程所有权

网络线程：

```text
decode C2S copy
CombatAttackIdGenerator.nextId()
schedule prepare/executing
call original Handler (normal path)
finish scheduling
```

服务端线程：

```text
CombatAttackTracker.prepare
CombatAttackSession.prepare
markExecuting
target resolution
event observation
finish
TTL
cleanup
```

`CombatAttackTracker` 的 active/recent/session 不在网络线程修改。

### 调度

- 网络线程使用 `server.tell(new TickTask(...))`；
- 服务端线程使用 `server.execute(...)`；
- `server.tell` 只入队，不 inline；
- 停服检查失败时保守跳过原始 Handler；
- 原始 Handler 正常路径只调用一次；
- 停服路径记录 `skipped original during stopping`。

### 停服限制

保留计划中的第三方竞态：

```text
检测到停止 -> 跳过原始 Handler
检查后停止 -> 原始 Handler 内部 execute 可能 inline
```

未声称停服路径严格线程安全。`isStopped()` 仍是非 volatile 读取；`isRunning()` 是 volatile 但不是严格同步屏障。

---

## 7. 观察窗口与生命周期处理

- prepare 后打开；
- finish 后 closed + sealed；
- sealed 后不再附加事件；
- 无事件不等于攻击未发生；
- 只有完整目标覆盖、已知 Handler 顺序、完整窗口、无事件时，才允许 `NO_ATTACK_EVENT_OBSERVED`；
- 当前 installationState 为 DEGRADED_OR_UNKNOWN，orderingKnown=false，因此无事件结果只能 `UNKNOWN`；
- 实际空目标攻击得到 `NO_REQUESTED_TARGETS`，不是 HIT/MISS。

生命周期：

```text
CombatAttackPhase
CombatAttackResult
CombatAttackEndReason
```

`INTERRUPTED_BY_NEW_REQUEST` 只作为 `endReason`，不写入 Phase/Result。

---

## 8. 测试矩阵逐项状态

### 8.1 已实现并通过

当前 `build` 运行：

```text
76/76 tests passed
```

新增单元测试：

```text
CombatAttackRequestTest                 4 passed
CombatTargetDamageObservationTest      2 passed
BetterCombatAttackAdapterTest          3 passed
```

覆盖：

- 请求有界捕获；
- targetIds 防御性复制；
- raw/captured/truncated；
- ID 唯一递增；
- 逐目标观察容量/truncated/positive/canceled/ambiguous；
- 空请求；
- 超范围 VarInt count 拒绝。

日志：[test-phase04-gate2.log](logs/test-phase04-gate2.log)

### 8.2 状态汇总

| 项目 | 状态 |
|---|---|
| 实现 | **IMPLEMENTED** |
| 编译 | **COMPILED** |
| 单元测试 | **TESTED（76/76）** |
| 客户端启动 | **CLIENT-TESTED（启动级）** |
| 客户端空目标攻击请求 | **CLIENT-TESTED（smoke）** |
| 专用服务器启动 | **SERVER-TESTED（启动级）** |
| 目标解析实机 | **NOT_TESTED** |
| LivingAttack/Hurt/Damage 实机 | **NOT_TESTED** |
| 多目标实机 | **NOT_TESTED** |
| 玩家死亡/退出/维度实机 | **NOT_TESTED** |
| 多人 | **MULTIPLAYER-TESTED：NO** |
| 整体 VERIFIED | **NO** |

未把 `DESIGNED` 测试写成已通过。

---

## 9. `compileJava` 结果

```text
BUILD SUCCESSFUL
```

日志：[compileJava-phase04-gate2.log](logs/compileJava-phase04-gate2.log)

---

## 10. `build` 结果

```text
BUILD SUCCESSFUL
jar: build/libs/parkourcombat-2.0.0-alpha.1.jar
size: 129,317 bytes
```

日志：[build-phase04-gate2.log](logs/build-phase04-gate2.log)

---

## 11. 客户端启动结果

实际 `runClient`：

- 成功启动 Forge 1.20.1 客户端；
- 进入单人世界；
- `CombatBridge` 自动订阅 FORGE bus；
- `BetterCombatCompat` 安装成功；
- 无启动崩溃。

日志：

- [runClient-phase04-gate2.latest.log](logs/runClient-phase04-gate2.latest.log)
- [runClient-phase04-gate2-attack.debug.log](logs/runClient-phase04-gate2-attack.debug.log)

客户端日志中存在与 Phase 04 无关的 Mojang 认证服务连接错误；单人世界仍成功进入，未导致崩溃。

实际攻击 smoke test：

```text
[BetterCombatCompat] installed with orderingUnknown=true
[CombatBridge] prepared attackId=1 player=Dev raw=0 captured=0 truncated=false orderingKnown=false
[CombatBridge] finished attackId=1 result=NO_REQUESTED_TARGETS phase=FINALIZED coverage=COMPLETE attackEvents=0 damageEvents=0
```

---

## 12. 专用服务器启动结果

实际 `runServer`（最终构建后重跑）：

```text
Done (7.777s)!
[BetterCombatCompat] installed with orderingUnknown=true
```

无 FATAL/ClassNotFound/NoSuchMethod。

日志：

- [runServer-phase04-gate2.latest.log](logs/runServer-phase04-gate2.latest.log)
- [runServer-phase04-gate2-final.latest.log](logs/runServer-phase04-gate2-final.latest.log)

仅启动级验证；没有真实玩家连接，没有专用服务器攻击事件测试。

---

## 13. 尚未验证的行为

- 有效实体目标解析；
- 死亡实体、重复 ID、截断目标的实机行为；
- EnderDragonPart/父实体匹配实机；
- `AttackEntityEvent` 目标匹配；
- `LivingAttackEvent`/`LivingHurtEvent`/`LivingDamageEvent` 实机观察；
- 多目标、同目标多次事件；
- 取消/无敌帧/创造模式/横扫/其他 Mod 追加伤害；
- 玩家死亡、退出、维度切换；
- 服务器停止期间的请求；
- 原始 Handler 异常/迟到 finish；
- 多玩家上下文隔离；
- Better Combat 缺失启动；
- 真实多人/专用服务器玩家。

---

## 14. 已知风险与限制

1. **Handler 身份未确认**：当前 `orderingUnknown=true`，因此无事件不会得到 `NO_ATTACK_EVENT_OBSERVED`，只能 `UNKNOWN`。
2. **停服原始 Handler 内部 `execute` 竞态**：桥接层无法严格消除；检查后停止仍可能 inline。
3. **`isStopped` 非 volatile**：不能作为严格跨线程停止屏障。
4. **服务端无空间验证**：不生成 `SERVER_SPATIALLY_VALIDATED_TARGET`。
5. **实际生命值减少不可证明**：只记录 `LivingDamageEvent` 观察值。
6. **目标截断/未解析/部件不匹配**：coverage partial，结果不能称完整。
7. **其他 Mod 同 source 追加伤害**：当前 source classification 仍可能标为 primary；未能在实机区分。
8. **Bridge 只观察 C2S 请求**：feint/无请求取消不可观测。
9. **无新增网络包/Mixin**：符合边界。
10. **未做多玩家/真实专用服务器玩家测试**。

---

## 15. Git 分支、HEAD、工作区和 diff 摘要

现场状态：

```text
分支：master
HEAD：170ea5bc29c3660525431263d07c3b79a9094f69
源码改动：新增 combat/ 与 compat/bettercombat/ Java 文件
既有源码修改：无
依赖改动：无
第三方 JAR 改动：无
Git commit：无
```

计划文档仍为未跟踪文件；本阶段新增源码、测试、文档和日志均未提交。

---

## 16. 是否偏离 Gate 2.2 计划

实现按 Gate 2.2 执行，明确记录以下实现选择：

1. `BetterCombatCompat` 首次安装一律 `orderingUnknown=true`，没有伪造 `AUDITED_BETTER_COMBAT_ORIGINAL`。
2. 使用有界 VarInt 解析；未修改原始 Handler 解码。
3. 使用 `server.tell(TickTask)` 处理网络线程 prepare/finish。
4. 停服检测失败时跳过原始 Handler，记录 `skipped original during stopping`。
5. 多目标伤害使用逐事件列表，不使用单一金额。
6. `CombatAttackTracker` 增加 `LOGGER.debug` 诊断日志，不改变业务逻辑。
7. `CombatAttackContext` 保留 `healthReductionVerified=false`。
8. 未实现 Phase 05、特殊攻击、词条、额外伤害、Hitbox 或新网络包。

最终标签：

```text
IMPLEMENTED
COMPILED
TESTED（76/76 unit）
CLIENT-TESTED（startup + empty-target request smoke）
SERVER-TESTED（startup only）
MULTIPLAYER-TESTED：NO
VERIFIED：NO
```

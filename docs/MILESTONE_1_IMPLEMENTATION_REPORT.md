# MILESTONE_1_IMPLEMENTATION_REPORT.md

> ParkourCombat Milestone 1 — 特殊攻击分类与效果计算基础  
> 分支：`gate-2.14-test-fixes`  
> 当前基线 HEAD：`dd1a50aadac70e3db7dd2b056a15a15cd1390e28`  
> 本阶段未提交、未推送。

---

## 1. 修改文件

新增：

```text
src/main/java/com/MrGhost/parkourCombat/combat/special/SpecialAttackStyle.java
src/main/java/com/MrGhost/parkourCombat/combat/special/SpecialAttackContext.java
src/main/java/com/MrGhost/parkourCombat/combat/special/SpecialAttackEffects.java
src/main/java/com/MrGhost/parkourCombat/combat/special/SpecialAttackWindowTracker.java
src/main/java/com/MrGhost/parkourCombat/combat/special/SpecialAttackClassifier.java
src/test/java/com/MrGhost/parkourCombat/combat/special/SpecialAttackClassifierTest.java
```

仍保留上一阶段未提交的 Phase06-B 修改：

```text
M src/main/java/com/MrGhost/parkourCombat/adapter/ParkourActionType.java
M src/main/java/com/MrGhost/parkourCombat/adapter/ParCoolActionMapping.java
?? src/test/java/com/MrGhost/parkourCombat/adapter/Phase06BActionMappingTest.java
```

---

## 2. 八种特殊攻击实现状态

| 流派 | 分类器 | 效果计算 | 实际伤害接入 |
|---|---|---|---|
| 动能斩 KINETIC | 已实现 | 已实现 | DEFERRED |
| 重力处刑 GRAVITY | 已实现 | 已实现 | DEFERRED |
| 越障斩 VAULT | 已实现 | 已实现 | DEFERRED |
| 磁轨斩 WALL_RUN | 已实现 | 已实现 | DEFERRED |
| 低位切割 SLIDE | 已实现 | 伤害 + 减速参数 | DEFERRED |
| 弹射斩 WALL_JUMP | 已实现窗口分类 | 伤害 + 击退参数 | DEFERRED |
| 惯性回斩 DODGE | 已实现窗口分类 | 伤害 + 击退参数 | DEFERRED |
| 悬挂处决 HANG_DOWN | 已实现 | 已实现 | DEFERRED |

**没有把 HUD 显示或分类结果冒充为实际伤害效果。**

---

## 3. 分类和效果机制

### 持续动作

分类器读取 `Set<ParkourActionType>` 的活动证据：

```text
FAST_RUN           -> KINETIC
DIVE               -> GRAVITY
VAULT              -> VAULT
WALL_RUN_HORIZONTAL/VERTICAL -> WALL_RUN
SLIDE              -> SLIDE
HANG_DOWN          -> HANG_DOWN
```

`FINISH` 后活动集合为空，分类器不再产生持续动作流派。

### 瞬时窗口

`SpecialAttackWindowTracker`：

```text
WINDOW_TICKS = 12
每个玩家最多 2 个窗口
WALL_JUMP / DODGE 正常 Finish 才生成
consume 一次后删除
过期窗口清理
```

窗口未消费前不会因为同动作再次 Finish 被覆盖。

### Fall

`DIVE` 优先于原版 Fall；无 DIVE 时：

```text
fallDistance >= 1.5
velocityY < -0.05
falling 或 fallDistance/velocity 满足条件
```

才判定 GRAVITY。普通离地不会因为 `onGround=false` 直接触发。

### 效果上限

```text
MAX_DAMAGE_BONUS = 8.0
MAX_KNOCKBACK = 1.0
MAX_SLOW_TICKS = 100
MAX_SLOW_AMPLIFIER = 2
```

分类器只返回 `SpecialAttackEffects`，不修改实体。

---

## 4. Better Combat 接入结论

当前安全规则要求：

- Better Combat `orderingKnown=false`；
- 服务端无法确认原始 Handler 的独占攻击归属；
- 不能增加第二套伤害入口。

因此本阶段：

```text
分类器：完成
效果计算：完成
HUD/伤害接入：未完成
实际伤害、击退、减速：DEFERRED
```

没有新增 `LivingHurtEvent` 伤害修改，没有新增 `player.attack(...)`，没有重放攻击请求。

需要后续验证：

1. Better Combat 服务端伤害事件与 ParkourCombat 分类上下文的严格时序；
2. 同一攻击只应用一次效果；
3. 普通 Forge 攻击不被误认为 Better Combat 独占；
4. 客户端目标/速度不能授权伤害。

---

## 5. 物理数据

当前未删除 `MotionTracker`，未新增第二套全量物理采样。

分类器使用传入的 `MotionSnapshot`：

```text
horizontalSpeed
velocityY
sampleTick
trust
```

Fall 使用调用方提供的 `fallDistance` / `falling`。

本阶段没有把 `SpecialAttackClassifier` 接入 `CombatBridge` 的实际攻击时读取，因此“攻击时读取最新服务端物理数据”仍是后续接入项。

---

## 6. 测试结果

新增测试类：

```text
SpecialAttackClassifierTest
```

覆盖：

```text
activeFastRunClassifiesKineticWithCappedDamage
diveTakesPrecedenceOverFallAndFinishedActionDoesNotTrigger
wallJumpWindowIsTwelveTicksAndConsumesOnce
canceledOrInterruptedActionDoesNotCreateWindow
eightStylesCanBeClassified
wallJumpAndDodgeWindowsBothClassify
conflictHasDeterministicPriority
effectsAreBounded
windowTrackerIsBoundedAndClearable
```

测试总数：

```text
163/163 passed
```

相对 Phase06-B 的 154 项增加 9 项。

未测试：

- 真实 ParCool 事件驱动生命周期；
- 真实 Better Combat 伤害接入；
- 客户端/服务端多人；
- 攻击时服务端实体实时读取；
- 真实击退/减速应用。

---

## 7. 编译和构建

```text
compileJava：BUILD SUCCESSFUL
test：163/163 passed
build：BUILD SUCCESSFUL（49s）
```

已知警告：

```text
ResourceLocation(String,String) 已过时
CombatAttackSession 使用过时 API
```

无依赖变更，无第三方 JAR 修改，无新网络协议。

---

## 8. 客户端/服务端

```text
CLIENT-STARTED：NO
CLIENT-TESTED：NO
SERVER-STARTED：NO
SERVER-TESTED：NO
MULTIPLAYER-TESTED：NO
```

本轮未执行真实游戏启动；不能用单元测试替代。

---

## 9. 未完成项

1. **事件驱动动作识别未完成**：现有 `ParCoolActionAdapter.onPlayerTick` 和 `activeTypes`/`isDoing()` 仍在运行；本阶段分类器使用传入的活动集合，没有删除轮询。
2. **特殊攻击未接入 CombatBridge**：分类器未在真实攻击事件中调用。
3. **实际伤害未应用**：按安全规则 DEFERRED。
4. **HUD 未显示特殊攻击**：未修改网络协议。
5. **窗口清理未接入玩家生命周期**：`SpecialAttackWindowTracker` 有 `clear`/`clearAll`，但尚未挂接死亡/登出/维度事件。
6. **FallDistance 实时读取未接入**：仅接受调用方参数。
7. **真实物理数据读取未接入**。

这些是阻塞 Milestone 1 “实际八种战斗机制”验收的关键项，不能把当前状态标记为完成的游戏功能。

---

## 10. Git

```text
分支：gate-2.14-test-fixes
HEAD：dd1a50aadac70e3db7dd2b056a15a15cd1390e28
```

按要求：

```text
未 commit
未 push
未切换分支
未合并 main
```

工作区包含 Phase06-B 和 Milestone 1 的未提交文件。

---

## 11. 最终状态

```text
DESIGNED：YES
IMPLEMENTED：PARTIAL（分类/效果计算）
COMPILED：YES
UNIT-TESTED：YES（163/163）
CLIENT-STARTED：NO
CLIENT-TESTED：NO
SERVER-STARTED：NO
SERVER-TESTED：NO
MULTIPLAYER-TESTED：NO
VERIFIED：NO
```

等待审查；不得将本报告当作八种特殊攻击已在游戏中生效的证明。

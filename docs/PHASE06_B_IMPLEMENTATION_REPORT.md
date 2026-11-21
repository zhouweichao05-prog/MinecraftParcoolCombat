# PHASE06_B_IMPLEMENTATION_REPORT.md

> Phase 06-B — 扩展八种特殊攻击所需的核心动作识别  
> 分支：`gate-2.14-test-fixes`  
> 基于：Phase06-A 设计报告  
> 本阶段只实现三个新增 ParCool 只读动作映射，不实现特殊攻击。

---

## 1. 修改文件

```text
src/main/java/com/MrGhost/parkourCombat/adapter/ParkourActionType.java
src/main/java/com/MrGhost/parkourCombat/adapter/ParCoolActionMapping.java
src/test/java/com/MrGhost/parkourCombat/adapter/Phase06BActionMappingTest.java
```

新增报告：

```text
docs/PHASE06_B_IMPLEMENTATION_REPORT.md
```

未修改：

- Better Combat；
- ParCool 原始 JAR；
- MotionSnapshot 字段；
- ParkourActionSelection 优先级；
- 网络协议；
- F10 HUD；
- 伤害/攻击逻辑；
- Gradle 依赖。

---

## 2. 新增动作映射

### 枚举

新增：

```text
DIVE
DODGE
HANG_DOWN
```

保留：

```text
FAST_RUN
SLIDE
VAULT
WALL_RUN
WALL_RUN_HORIZONTAL
WALL_RUN_VERTICAL
WALL_JUMP
```

### 映射

```text
com.alrex.parcool.common.action.impl.Dive     -> DIVE
com.alrex.parcool.common.action.impl.Dodge    -> DODGE
com.alrex.parcool.common.action.impl.HangDown -> HANG_DOWN
```

映射使用 ParCool 3.4.3.3 真实类和 `Parkourability.get(Class).isDoing()` 路径。

### 暂缓

未加入正式映射：

```text
SkyDive
ClimbUp
```

`FALL` 仍由现有 `FallSnapshot` 处理，没有加入 `ParCoolActionMapping`。

---

## 3. 状态机兼容性

新增动作沿用现有 `ParkourActionTracker`：

```text
STARTED
ACTIVE
FINISHED
CANCELLED
INTERRUPTED
UNKNOWN
TERMINAL_TTL_TICKS = 40
SYNC_LOST_TIMEOUT_TICKS = 2
```

没有第二套状态机，没有新增每动作 Tick 监听器。

已验证：

- DIVE 正常 STARTED -> ACTIVE -> FINISHED；
- DODGE 取消 -> CANCELLED；
- HANG_DOWN 同步丢失 -> UNKNOWN；
- WALL_RUN 聚合行为未改动；
- 现有选择优先级未改动：

```text
WALL_RUN > WALL_JUMP > VAULT > SLIDE > FAST_RUN
```

DIVE/DODGE/HANG_DOWN 不会自动成为 `MotionSnapshot.actionState` 的主动作。

DIVE 不会替换 `FallSnapshot`；两者独立存在，没有新增下落状态。

---

## 4. 性能开销

扩展前：

```text
每玩家每 tick: 6 次 isDoing()
```

扩展后：

```text
每玩家每 tick: 9 次 isDoing()
```

仍复用：

- `ParCoolActionAdapter.onPlayerTick`；
- `ParCoolActionMapping.activeTypes`；
- 现有 `EnumSet`；
- 现有生命周期清理。

没有：

- 新 PlayerTick 监听器；
- `Tick.Pre` / `Tick.Post` 业务订阅；
- 实体扫描；
- 独立线程/定时任务；
- 每动作网络包；
- 无界缓存；
- 额外 MotionTracker。

---

## 5. 客户端/服务端可信度

保持不变：

```text
CLIENT_LOCAL action: MEDIUM
PARCOOL_SYNCED server action: LOW
SERVER_NATIVE motion: HIGH
VANILLA_DERIVED fall: 既有语义
```

新增动作没有提高服务端可信度，也没有修改任何伤害或攻击结算。

---

## 6. 测试结果

新增测试类：

```text
Phase06BActionMappingTest
```

覆盖：

```text
newActionTypesExist
newActionClassesMapToNewTypes
deferredActionsAreNotMapped
existingFiveActionMappingsRemain
newActionsUseExistingStateMachine
dodgeCancellationUsesExistingPhase
hangDownSyncLossUsesUnknown
newActionsDoNotChangePrimarySelectionPriority
```

结果：

```text
154/154 passed / 0 failed
```

相比 Phase05.1 的 146 项增加 8 项。

限制：

- `activeTypes` 的完整真实 `Parkourability` 行为无法脱离游戏环境直接测试；本阶段没有反射访问私有 `doing` 字段，也没有伪造游戏对象。
- 真实 ParCool 客户端、专用服务器和多人同步尚未测试。

---

## 7. 编译和构建

```text
compileJava：BUILD SUCCESSFUL
test：154/154 passed
build：BUILD SUCCESSFUL
jar：170,281 bytes
```

编译警告：

```text
ResourceLocation(String,String) 已过时
CombatAttackSession 使用过时 API
```

这些是既有警告，本轮没有新增警告类型。没有依赖变化，没有第三方 JAR 变化，没有新网络协议。

---

## 8. 客户端/服务端启动

本阶段未执行：

```text
CLIENT-STARTED：NO
SERVER-STARTED：NO
CLIENT-TESTED：NO
SERVER-TESTED：NO
MULTIPLAYER-TESTED：NO
```

不能用 JUnit 或编译结果替代真实 ParCool 动作验证。

---

## 9. 已知问题/未完成

- 真实 ParCool `Dive`/`Dodge`/`HangDown` 的客户端动作需要游戏内验证；
- 专用服务器同步状态需要实测；
- 多人玩家隔离需要实测；
- `activeTypes` 真实能力对象路径未由 JUnit 直接覆盖；
- WallJump 现有短窗时间信息未在本阶段扩展；
- 未实现任何特殊攻击、动画、倍率或词条。

---

## 10. Git

```text
分支：gate-2.14-test-fixes
HEAD：dd1a50aadac70e3db7dd2b056a15a15cd1390e28
工作区：存在本轮未提交的源码/测试/报告修改
```

按任务要求：

```text
未 commit
未 push
未切换分支
未合并 main
```

---

## 11. 最终状态

```text
DESIGNED：YES（Phase06-A）
IMPLEMENTED：YES
COMPILED：YES
UNIT-TESTED：YES（154/154）
CLIENT-STARTED：NO
CLIENT-TESTED：NO
SERVER-STARTED：NO
SERVER-TESTED：NO
MULTIPLAYER-TESTED：NO
VERIFIED：NO
```

等待 ChatGPT 和项目所有者审查及真实游戏验证。

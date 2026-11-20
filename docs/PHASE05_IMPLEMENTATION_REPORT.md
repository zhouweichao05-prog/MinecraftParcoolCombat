# PHASE05_IMPLEMENTATION_REPORT.md

> Phase 05 — 跑酷攻击观察 HUD 实现  
> 分支：gate-2.14-test-fixes  
> 最终：CONDITIONALLY_IMPLEMENTED  
> 未进入 Phase 05 战斗结算扩展；未实现特殊攻击/倍率/词条。

---

## 1. 修改文件

### 新增

```text
src/main/java/com/MrGhost/parkourCombat/network/ClientboundCombatDebugSnapshot.java
src/main/resources/assets/parkourcombat/lang/zh_cn.json
src/test/java/com/MrGhost/parkourCombat/combat/CombatDebugSnapshotTest.java
```

### 修改

```text
CombatAttackTracker.java
CombatBridge.java
DebugNetwork.java
DebugSnapshotInbox.java
DebugHudBroadcaster.java
ClientDebugTick.java
DebugHudState.java
DebugHudOverlay.java
DebugHudStateTest.java
```

未修改 H09/C09 的语义、Better Combat Handler、ParCool 或第三方 JAR。

---

## 2. HUD 中文化

已中文化：

- 面板标题：
  - 客户端本地数据
  - 服务端观察数据
- 运动字段、动作名称、来源、可信度、历史、过期提示；
- 攻击请求、攻击编号、阶段、结果、连击、武器；
- 攻击时动作、动作阶段、水平/垂直速度、采样时刻；
- 目标数量/覆盖、攻击/伤害/取消/迟到事件；
- Handler 状态、处理顺序、攻击归属、生命值验证；
- F10 键分类和名称（zh_cn.json）。

保留了内部英文枚举/日志标识，不修改协议或底层枚举。

---

## 3. 攻击观察读取

新增：

```java
CombatAttackTracker.latestContextForHud(ServerPlayer)
CombatBridge.latestContextForHud(ServerPlayer)
```

只返回最近 sealed `CombatAttackContext`，不暴露 Session/Tracker 内部集合。

HUD 数据来源：

- `CombatAttackContext` 的不可变字段；
- 不新建攻击追踪器；
- 不改变攻击结束规则/TTL；
- 每 4 tick 由现有 `DebugHudBroadcaster` 发送；
- 只发给已订阅 HUD 的玩家。

### 采样语义

攻击数据中的 MotionSnapshot 来自请求 prepare 阶段，不是命中瞬间。  
HUD 明确显示：

```text
采样说明：请求阶段运动快照
关联可信度：有限
```

没有声称精确命中时刻。

---

## 4. 网络数据包

新增：

```text
ClientboundCombatDebugSnapshot
```

- 复用现有 `DebugNetwork` debug 通道；
- 消息 ID = 2；
- 只传摘要，不传 UUID 集合、ItemStack 或完整 Context；
- 字符串上限 64；
- 数值检查负数/非有限值；
- 无效 count 抛 DecoderException；
- 旧 `ClientboundMotionDebugSnapshot` ID=1 保持兼容。

字段包括：

```text
hasContext, attackId, phase, result, combo, weaponId,
actionType, actionPhase, horizontal/vertical speed,
sampleTick, serverTick, targetCoverage, requested/observed targets,
attack/damage/canceled/late counts, truncated,
orderingKnown, serverAuthoritative, healthReductionVerified,
handlerState, handlerIdentity
```

---

## 5. 客户端 HUD

保留两个面板：

1. 客户端本地数据
2. 服务端观察数据

服务端面板新增战斗观察段落；未收到攻击时显示：

```text
战斗攻击观察：尚未观察到
```

显示不会把：

- `TARGET_OBSERVED` 说成命中；
- `DAMAGE_EVENT_OBSERVED` 说成生命值减少；
- `orderingKnown=false` 说成 Better Combat 独占。

`实际生命值减少：未验证` 始终按 `healthReductionVerified` 显示。

---

## 6. 生命周期与隔离

- 每名玩家只收到自己的攻击摘要；
- DebugHudSubscriptions 过滤未订阅玩家；
- HUD 关闭清理客户端服务端缓存；
- 死亡/登出/维度切换清理；
- 不保存攻击历史；
- 只读取最近 sealed Context；
- 不延长 TTL；
- 不扫描世界实体；
- 不新增高频任务。

---

## 7. 自动化测试

新增：

```text
CombatDebugSnapshotTest
  combatSnapshotRoundTrips
  emptyCombatSnapshotRoundTripsWithoutContext
  negativeCountIsRejected
```

更新 `DebugHudStateTest` 为中文断言。

结果：

```text
145/145 passed / 0 failed
```

相比 Gate 2.14 的 142 项增加 3 项。

---

## 8. 编译/构建/启动

```text
compileJava：BUILD SUCCESSFUL
test：145/145 passed
build：BUILD SUCCESSFUL
jar：167,551 bytes
```

```text
SERVER-STARTED：Done (7.636s)
BetterCombatCompat installed with orderingUnknown=true
```

```text
CLIENT-STARTED：本轮未重新确认
```

未执行 F10 实际游戏 HUD 测试；没有伪造截图。

---

## 9. 尚未完成的人工测试

- F10 开关与中文化实际界面；
- 站立/FAST_RUN/SLIDE/VAULT/WALL_RUN 攻击 HUD；
- 攻击编号/武器/连击更新；
- 客户端与服务器面板区分；
- 攻击上下文过期；
- 关闭重开无旧数据；
- 死亡/登出/维度；
- 多人隔离。

全部 `NOT_TESTED`。

---

## 10. 依赖 SHA-256

```text
bettercombat-forge-1.9.0+1.20.1.jar
49C69DCCF2C641246B4E3DECA81EBAC21F0EE119E81FC713B838B8E33569EEA1

ParCool-1.20.1-3.4.3.3.jar
2DDEF8752B5A1428035B95F29E62C81E33B80D37851681E1B928D8A01615C6E7

player-animation-lib-forge-1.0.2-rc1+1.20.jar
90D9965CB9EFDBDA29FDC5610BE3914CF7008BF5C392FF34F7AB25F96A852691

cloth-config-11.1.136-forge.jar
1E895E85CF5B1E1905EF3178EC155C8BADFE22A1577B92C09143A5AA1F4CE0F2
```

---

## 11. 最终状态

```text
IMPLEMENTED
COMPILED
UNIT-TESTED（145/145）
SERVER-STARTED（YES）
CLIENT-STARTED（NO，本轮未执行）
CLIENT-TESTED（NO）
SERVER-TESTED（NO）
MULTIPLAYER-TESTED（NO）
VERIFIED（NO）
```

F10 HUD 攻击观察仍等待实际游戏人工测试。

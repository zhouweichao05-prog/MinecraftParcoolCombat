# PHASE_03_IMPLEMENTATION_REPORT — MotionTracker 与玩家运动数据系统

- 阶段：03
- 实现提交：`f3dc5f1`（初始 `156b114` + 服务端运动/死亡清理修复）
- 规划提交：`3d0ddaf`
- 前置状态：阶段 02 `CONDITIONALLY_ACCEPTED`（HEAD `3389c13`，记录提交 `73ed388`）
- 状态：**IMPLEMENTED / COMPILED / 部分 TESTED；未 VERIFIED**

## 1. 实际修改文件

### 新增源码（5）

- `adapter/MotionFrame.java`
- `adapter/MotionSnapshot.java`
- `adapter/MotionHistory.java`
- `adapter/MotionAccumulator.java`
- `adapter/ParkourActionSelection.java`

### 新增测试（3）

- `MotionAccumulatorTest.java`（18）
- `MotionHistoryTest.java`（3）
- `ParkourActionSelectionTest.java`（6）

### 修改源码（3）

- `adapter/MotionTracker.java`
- `adapter/ParkourActionAdapter.java`
- `adapter/ParCoolActionAdapter.java`

未修改：`ParkourActionTracker`、`ParkourActionState`、`FallStateMachine`、`FallSnapshot`、`ParCoolActionMapping`、`ParkourAdapterEvents`、`ParkourCombat`、`build.gradle`、锁定依赖。

## 2. 核心设计

### 2.1 MotionSnapshot

不可变 record，包含：

- `sampleTick`
- `position`
- `velocityX/Y/Z`
- `horizontalSpeed`
- `directionX/Z`（无效时 0,0）
- `horizontalAcceleration`
- `verticalAcceleration`
- `displacementX/Y/Z`
- `continuousMotionTicks`
- `onGround`
- `FallSnapshot fallSnapshot`
- `ParkourActionState actionState`（可空）
- `source` / `trust`（仅描述运动学数据）

`falling()` 由 `fallSnapshot.active()` 派生；没有把 FALL 或 ParCool 动作的 trust 合并进运动 trust。

### 2.2 可信度隔离

| 数据 | 服务端 | 客户端 |
|---|---|---|
| 运动学 | `SERVER_NATIVE` / HIGH | `CLIENT_LOCAL` / MEDIUM |
| FALL | `VANILLA_DERIVED` / HIGH | `VANILLA_DERIVED` / MEDIUM |
| ParCool 动作 | `PARCOOL_SYNCED` / LOW | `CLIENT_LOCAL` / MEDIUM |

- `MotionSnapshot.source/trust` 只描述运动学。
- `fallSnapshot` 和 `actionState` 各自保留 source/trust。
- 不因快照在服务端生成而提升 ParCool 动作 trust。

### 2.3 加速度

- 水平：`sqrt((vx-vxPrev)^2 + (vz-vzPrev)^2) / deltaTicks`
- 垂直：`(vy-vyPrev) / deltaTicks`
- 正常 `deltaTicks=1`；2..5 tick 间隔按平均每 tick 计算；>5 tick 重置基线。
- 同一 gameTick 的重复帧直接忽略，不清历史。
- 传送（单 tick >8 格）、维度、死亡、非有限数据、负时间差均重置基线；重置帧不生成快照。

### 2.4 连续运动

- 水平速度 ≥0.05 或绝对垂直速度 ≥0.08 时累计。
- 只有两者都低于阈值才归零。
- 跳跃上升和自由下落都累计。
- 传送/维度/死亡/采样间隔异常/重建重置。

### 2.5 方向

- 水平速度 ≥0.05 时更新单位方向。
- 低速时保留上一方向最多 5 tick，随后归零。
- 防止短暂静止造成方向跳变。

### 2.6 历史

- 固定 20 条（1 秒）。
- 插入不创建集合；只有查询 `recent(max)` 时创建只读 List。
- 重置时清空，不跨维度/死亡保留。

### 2.7 主活动动作

优先级：

```text
WALL_RUN > WALL_JUMP > VAULT > SLIDE > FAST_RUN
```

只选择 `active` 状态；终态 TTL 不进入快照。未修改阶段 02 的动作生命周期。

## 3. 采样管线

`ParCoolActionAdapter.onPlayerTick`：

1. `actions.onTick`
2. `ParkourActionSelection.select`
3. `motion.tick(player, gameTime, primaryAction)`
4. 读取一次 Player 位置/速度/环境标志
5. 更新 `FallStateMachine`
6. `MotionAccumulator.accept`
7. 每 20 tick 最多一次 DEBUG `[MotionTracker]` 日志；重置单独 INFO

每玩家每逻辑端每 tick 最多一次 `MotionTracker.tick`；无世界扫描、无新网络包、无 Mixin。

## 4. 生命周期

- Logout/Clone/Dimension/Death/LevelUnload/ServerStopping：adapter 移除 entry，`MotionTracker.clear()`，历史丢弃。
- 死亡/维度：`MotionTracker` 先 end/clear，再移除。
- 同维度传送：`MotionAccumulator` 清历史，重设基线，不写虚假加速度。
- 新登录/实体重建：首帧零加速度、零位移。

## 5. 实际测试结果

- 单元测试：**50/50 PASS**（阶段 02 的 21 + 新增 29）。
- `compileJava`：PASS。
- `build`：PASS（13s；jar 52,070 B）。
- 开发客户端（quick-play 测试世界）：成功进入世界；最终空闲采样 18 条（客户端 9、集成服务端 9），无 reset、无 FATAL。
- 专用服务器：`Done (7.812s)`，`side=SERVER` 初始化成功。
- 修复前实测日志（用户提供，16:02）：98 条客户端 + 98 条服务端；客户端有运动值，服务端 `getDeltaMovement` 为 0，且死亡后 DEAD reset 每 tick 重复。该日志用于定位缺陷，不作为修复后通过证据。
- 修复后动态复测：142 条采样（71/71），0 reset，双端 hSpeed 非零；死亡清理各一次。
- 最终采样证据：[runClient-phase03-retest-motion.log](logs/runClient-phase03-retest-motion.log)、[runServer-phase03-final.latest.log](logs/runServer-phase03-final.latest.log)
- 修复前证据：[runClient-phase03-prefix-movement.log](logs/runClient-phase03-prefix-movement.log)

## 6. 实际采样示例

客户端空闲：

```text
[MotionTracker] side=CLIENT player=Dev tick=1
pos=(-16.93,71.0,54.31) vel=(0.0,-0.0784,0.0)
hSpeed=0.0 dir=(0.0,0.0) hAccel=0.0 vAccel=0.0
movingTicks=0 onGround=true
motionSource=CLIENT_LOCAL motionTrust=MEDIUM
fallPhase=NOT_FALLING fallSource=VANILLA_DERIVED fallTrust=MEDIUM
actionType=NONE
```

服务端空闲：

```text
[MotionTracker] side=SERVER player=Dev tick=1
pos=(-16.93,71.0,54.31) vel=(0.0,0.0,0.0)
hSpeed=0.0 vAccel=0.0
motionSource=SERVER_NATIVE motionTrust=HIGH
fallPhase=NOT_FALLING fallSource=VANILLA_DERIVED fallTrust=HIGH
```

## 7. 实测发现并修复

1. **服务端运动速度恒为 0**：16:02 日志中服务端位置在变化，但 `vel=(0,0,0)`、`hSpeed=0`。原因是 `ServerPlayer#getDeltaMovement` 在服务端移动包处理后可能为 0。修复：`MotionAccumulator` 对 `SERVER_NATIVE` 使用服务端观测到的位置差 / `sampleTick` 推导速度；加速度基线保存“有效速度”，避免首帧/重力造成虚假 vAccel。
2. **死亡后 DEAD reset 每 tick 重复**：死亡界面仍触发 `PlayerTickEvent`，adapter 每 tick 重新创建 tracker 并 reset。修复：`onPlayerTick` 检测 `isDeadOrDying` 后移除并跳过。
3. **客户端 level gameTime 导致 TICK_INVALID**：客户端 `Level#getGameTime` 在加载/同步时出现重复/回退，导致历史被清。修复：采样 tick 改用 `Player#tickCount`；修复后空闲采样无 reset。
4. **服务端空闲虚假 vAccel=0.0784**：位置未变但首帧 `deltaMovement.y` 为负；修复后服务端首帧有效速度为 0，空闲 vAccel=0。

### 7.1 修复后动态复测（2026-11-19 17:16–17:18）

- `[MotionTracker]` 共 142 条：客户端 71、集成服务端 71。
- **0 次 reset**（无 TICK_INVALID/TICK_GAP/DEAD）。
- 客户端与集成服务端各有 38 条 `hSpeed>0` 采样；服务端 36 条 `movingTicks>0`。
- 服务端速度已非零，例如：
  ```text
  side=SERVER ... hSpeed=0.3163 ... movingTicks=10 ... source=SERVER_NATIVE trust=HIGH
  ```
- 实际动作：FAST_RUN、VAULT、WALL_RUN、SLIDE；FALLING/FALL_END 均出现。
- 死亡清理：`cleanup side=SERVER reason=DEATH` + `cleanup side=CLIENT reason=DEATH_TICK` 各一次，无 DEAD reset 刷屏。
- 退出清理双端成功。
- 无 FATAL；仅 Patchouli/认证等既有错误。
- 证据：[runClient-phase03-retest-motion.log](logs/runClient-phase03-retest-motion.log)

## 8. 未测试/未验证

- 真实传送、维度切换的数值/清理（死亡清理已复测）。
- 历史在真实运行中的 20 条覆盖行为（仅单元测试）。
- 短暂停步方向保持、采样间隔异常的实机验证。
- 专用服务器真实玩家移动（仅启动 + 集成服务端动态采样）。
- 多人。
- FALL 水/岩浆/鞘翅/攀爬/载具等阶段 02 遗留项仍 NOT TESTED。
- 服务端 FALL 仍使用阶段 02 raw `getDeltaMovement.y`，本阶段未改其语义。

## 9. 已知实现注记

1. 客户端 `Level#getGameTime` 在加载/同步时可能重复/回退；当前采样 tick 已改为 `Player#tickCount`，修复后空闲采样无 reset。
2. `ParkourActionSelection` 为取得阶段 02 的当前状态，每 tick 调用一次 `ParkourActionTracker.getState`，产生一个 `Optional`。由于用户明确禁止修改 `ParkourActionTracker`，该 Optional 是阶段 02 API 边界；`MotionSnapshot`/`MotionHistory`/`MotionAccumulator` 热路径本身不使用 Optional/List/Stream。
3. 未添加调试命令；仅 DEBUG 采样日志每 20 tick 最多一次。
4. 服务端运动速度是“服务端接受的位置差 / tick”，不是客户端 `getDeltaMovement`；仍属于服务端观测数据，source/trust 保持 `SERVER_NATIVE/HIGH`。

## 10. 状态

| 项 | 状态 |
|---|---|
| 设计 | **DESIGNED** |
| 代码 | **IMPLEMENTED** |
| 编译 | **COMPILED** |
| 单元测试 | **TESTED（50/50）** |
| 客户端实际动态采样 | **TESTED（复测）** |
| 集成服务端实际动态采样 | **TESTED（复测）** |
| 服务端启动 | **SERVER-TESTED（启动级）** |
| 死亡清理 | **TESTED（复测）** |
| 传送/维度实机 | **NOT TESTED** |
| 历史运行时覆盖 | **NOT TESTED（仅单元）** |
| 专用服务器真实玩家/多人 | **NOT TESTED** |
| 整体 | **未 VERIFIED** |

## 11. 结论

阶段 03 代码已实现、编译、50 个单元测试通过；修复后复测确认双端动态速度非零、0 reset、死亡清理不再刷屏。真实传送/维度、历史运行时覆盖、专用服务器真实玩家和多人仍未验证。未进入阶段 04。

# PHASE_03_IMPLEMENTATION_REPORT — MotionTracker 与玩家运动数据系统

- 阶段：03
- 实现提交：`156b114`（`feat: implement phase 03 motion snapshot system`）
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

- 单元测试：**48/48 PASS**（阶段 02 的 21 + 新增 27）。
- `compileJava`：PASS。
- `build`：PASS（12s；jar 51,776 B）。
- 开发客户端（quick-play 测试世界）：成功进入世界；客户端/集成服务端各产生 24 条空闲运动采样，无 FATAL。
- 专用服务器：`Done (7.833s)`，`side=SERVER` 初始化成功。
- 实际采样证据：[runClient-phase03.debug.log](logs/runClient-phase03.debug.log)、[runServer-phase03.latest.log](logs/runServer-phase03.latest.log)

## 6. 实际采样示例

客户端空闲：

```text
[MotionTracker] side=CLIENT player=Dev tick=1337
pos=(-16.93,71.0,54.31) vel=(0.0,-0.0784,0.0)
hSpeed=0.0 dir=(0.0,0.0) hAccel=0.0 vAccel=0.0
movingTicks=0 onGround=true
motionSource=CLIENT_LOCAL motionTrust=MEDIUM
fallPhase=NOT_FALLING fallSource=VANILLA_DERIVED fallTrust=MEDIUM
actionType=NONE
```

服务端空闲：

```text
[MotionTracker] side=SERVER player=Dev tick=1338
pos=(-16.93,71.0,54.31) vel=(0.0,-0.0784,0.0)
hSpeed=0.0 ...
motionSource=SERVER_NATIVE motionTrust=HIGH
fallPhase=NOT_FALLING fallSource=VANILLA_DERIVED fallTrust=HIGH
```

## 7. 未测试/未验证

- 真实移动/冲刺/转向的数值。
- 真实跳跃/下落/落地时的 `vy`、FALL 和连续运动。
- 真实传送、维度切换、死亡清理。
- 历史在真实运行中的 20 条覆盖行为（仅单元测试）。
- 专用服务器真实玩家移动（仅启动 + 集成服务端空闲采样）。
- 多人。
- FALL 水/岩浆/鞘翅/攀爬/载具等阶段 02 遗留项仍 NOT TESTED。

## 8. 已知实现注记

1. 客户端世界加载时观察到 `TICK_INVALID`/`TICK_GAP` 重置；重置后正常采样。重置会清历史，属于采样异常保护。
2. `ParkourActionSelection` 为取得阶段 02 的当前状态，每 tick 调用一次 `ParkourActionTracker.getState`，产生一个 `Optional`。由于用户明确禁止修改 `ParkourActionTracker`，该 Optional 是阶段 02 API 边界；`MotionSnapshot`/`MotionHistory`/`MotionAccumulator` 热路径本身不使用 Optional/List/Stream。
3. 未添加调试命令；仅 DEBUG 采样日志每 20 tick 最多一次。

## 9. 状态

| 项 | 状态 |
|---|---|
| 设计 | **DESIGNED** |
| 代码 | **IMPLEMENTED** |
| 编译 | **COMPILED** |
| 单元测试 | **TESTED（48/48）** |
| 客户端实际空闲采样 | **TESTED（有限）** |
| 服务端启动 | **SERVER-TESTED（启动级）** |
| 真实移动/传送/维度/死亡 | **NOT TESTED** |
| 整体 | **未 VERIFIED** |

## 10. 结论

阶段 03 代码已实现、编译、48 个单元测试通过，客户端和集成服务端都有实际空闲采样；真实运动数值与生命周期仍需人工验证。未进入阶段 04。

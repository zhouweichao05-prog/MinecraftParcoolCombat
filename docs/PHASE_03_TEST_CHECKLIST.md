# PHASE_03_TEST_CHECKLIST — 运动数据客户端/服务端人工验证清单

- 阶段：03
- 状态：**PARTIALLY TESTED（仅空闲采样）**
- 代码提交：`156b114`
- 关联：[PHASE_03_TEST_REPORT.md](PHASE_03_TEST_REPORT.md)

> 只验证运动数据快照，不验证攻击、伤害、词条、动画或网络。
> 实测时请记录 `debug.log` 的 `[MotionTracker]` 行；`latest.log` 只有 INFO。

## 1. 前置

```powershell
Set-Location 'D:\MinecraftParcoolCombat'
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat --no-daemon runClient
```

- 超平坦/和平/生存，单独测试世界。
- 运行 20 tick 以上，确保至少一条 `[MotionTracker]`。
- 服务端：`runServer` 或集成服务端日志中过滤 `side=SERVER`。

## 2. 日志字段

```text
[MotionTracker] side=... player=... tick=...
pos=(x,y,z) vel=(vx,vy,vz) hSpeed=...
dir=(dx,dz) hAccel=... vAccel=...
disp=(dx,dy,dz) movingTicks=... onGround=...
motionSource=... motionTrust=...
fallPhase=... fallSource=... fallTrust=...
actionType=... actionPhase=... actionSource=... actionTrust=...
```

- 运动 source/trust 只描述运动学。
- FALL 和 ParCool 动作必须分别显示自己的 source/trust。

## 3. 用例

| ID | 场景 | 预期 | 结果 |
|---|---|---|---|
| M-01 | 静止 2 秒 | hSpeed≈0，movingTicks=0，onGround=true | **PASS（空闲采样）** |
| M-02 | 步行/冲刺 | hSpeed 随速度变化，方向与移动方向一致 | NOT TESTED |
| M-03 | 左右转向 | 方向平滑改变，不因短暂静止跳变 | NOT TESTED |
| M-04 | 跳跃上升 | vy>0；不误报 FALL | NOT TESTED |
| M-05 | 自由下落 | vy<0；FALL=FALLING | NOT TESTED |
| M-06 | 落地 | onGround=true；FALL_END；连续运动归零 | NOT TESTED |
| M-07 | 短暂停步再走 | 方向保持最多 5 tick，不立即反向/归零 | NOT TESTED |
| M-08 | 连续跑动/跳跃 | movingTicks 连续增加；静止归零 | NOT TESTED |
| M-09 | 历史窗口 | 连续采样后可查询最多 20 条；越界为 0 | NOT TESTED（仅单元） |
| M-10 | 同维度传送 | 无巨大 hAccel/vAccel；历史清空；下一帧基线 | NOT TESTED |
| M-11 | 放置/破坏方块/掉 tick | 不产生跨间隔虚假加速度 | NOT TESTED |
| M-12 | 死亡/切维度/退出 | 无残留 MotionTracker/历史 | NOT TESTED |
| M-13 | 跑酷动作中 | actionType 按 WALL_RUN > WALL_JUMP > VAULT > SLIDE > FAST_RUN | NOT TESTED（仅单元） |
| M-14 | 无动作 | actionType=NONE，快照仍有效 | **PASS（空闲采样）** |
| M-15 | 可信度隔离 | 客户端 motion CLIENT_LOCAL/MEDIUM；FALL/ParkCool 各自 trust | **PASS（单元 + 空闲采样）** |
| M-16 | 双端 source | 服务端 motion SERVER_NATIVE/HIGH；客户端 CLIENT_LOCAL/MEDIUM | **PASS（空闲采样）** |
| M-17 | 专用服务器真实玩家 | 服务端有采样，客户端数据不参与服务端 | NOT TESTED |
| M-18 | 多人 | 每个玩家独立历史，无串线 | NOT TESTED |

## 4. 结果记录模板

```text
测试日期：
构建 commit：
客户端/服务端：
场景：
日志片段：
实际值：
判定：PASS / FAIL / BLOCKED / NOT TESTED
备注：
```

## 5. 判定规则

- 没有实际日志/操作证据的项一律 `NOT TESTED`。
- 编译、单元测试、启动成功不能替代动态运动验证。
- 传送/维度/死亡后若仍有旧历史或巨大加速度，判 FAIL。
- 若运动 trust 与 FALL/动作 trust 混同，判 FAIL。

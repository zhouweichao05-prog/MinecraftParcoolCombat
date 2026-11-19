# PHASE_03_5_IMPLEMENTATION_REPORT — Motion Debug HUD

- 阶段：03.5
- 代码提交：`11a8b00`
- 基线：`f6168bd`
- 状态：**IMPLEMENTED / COMPILED / 部分 CLIENT-TESTED / INTEGRATED-SERVER-TESTED；未 VERIFIED**
- 关联：[PHASE_03_5_TEST_REPORT.md](PHASE_03_5_TEST_REPORT.md)

## 1. 实现范围

只实现开发调试 HUD、调试专用网络通道、F10 开关和历史容量只读接口。

未修改：

- `MotionAccumulator` 速度/加速度算法
- `MotionTracker` 采样频率/基线策略
- `FallStateMachine` / `FallSnapshot` 语义
- `ParkourActionTracker` / `ParkourActionState` 语义
- 阶段 02 生命周期判断
- 阶段 03 运动学阈值/历史窗口
- 第三方依赖/JAR
- `build.gradle` 依赖

## 2. 新增文件

### Common network/debug

```text
network/DebugNetwork.java
network/ServerboundDebugHudSubscription.java
network/ClientboundMotionDebugSnapshot.java
debug/DebugHudSubscriptions.java
debug/DebugSnapshotInbox.java
debug/DebugHudBroadcaster.java
```

### Client HUD

```text
client/debug/ClientDebugHud.java
client/debug/ClientDebugTick.java
client/debug/DebugHudState.java
client/debug/DebugHudOverlay.java
client/debug/HudLine.java
```

### Resource

```text
assets/parkourcombat/lang/en_us.json
```

### Tests

```text
network/DebugNetworkCodecTest.java
client/debug/DebugHudStateTest.java
debug/DebugHudSubscriptionsTest.java
debug/DebugHudBroadcasterTest.java
```

## 3. 修改文件

```text
adapter/MotionHistory.java          // capacity() 只读实例容量
adapter/MotionTracker.java          // historyCapacity()
adapter/ParkourActionAdapter.java   // getMotionHistoryCapacity(Player)
adapter/ParCoolActionAdapter.java   // 实现容量查询
adapter/MotionHistoryTest.java      // capacity 测试
```

历史窗口仍为 20；淘汰/采样/记录逻辑未改变。

## 4. 网络协议

Channel：

```text
parkourcombat:debug
version "1"
Forge 1.20.1 NetworkRegistry.newSimpleChannel + SimpleChannel
```

### C2S

`ServerboundDebugHudSubscription`

```text
boolean enabled
```

- 仅表示开关。
- 服务端通过 `ctx.getSender()` 获取真实发送者。
- 不接受客户端运动数据/UUID/坐标/速度。

### S2C

`ClientboundMotionDebugSnapshot`

字段：

```text
hasSnapshot
sampleTick
velocityX/Y/Z
horizontalSpeed
directionX/Z
horizontalAcceleration
verticalAcceleration
displacementX/Y/Z
continuousMotionTicks
onGround
historyCount / historyCapacity
hasAction -> actionType/actionPhase/actionSource/actionTrust
hasFall -> fallPhase/fallDistance/fallVerticalSpeed/fallSource/fallTrust
motionSource / motionTrust
serverAuthoritative
```

编码细节：

- `hasSnapshot=false` 时只写布尔，不写其余字段。
- 枚举使用稳定名称（UTF），不使用 ordinal。
- double 解码必须 finite。
- historyCapacity 必须 1..1024；0 <= count <= capacity。
- action/fall 缺失时只写 false。
- 非法枚举/NaN/Infinity/非法历史数会抛 `DecoderException`，由网络层丢弃，客户端不会崩。

## 5. 服务端发送

`DebugHudBroadcaster`：

- `ServerTickEvent.End`，每 4 tick 最多一次（5 Hz）。
- 只遍历 `PlayerList`，不扫描世界实体。
- 只处理 `DebugHudSubscriptions` 中已订阅玩家。
- 从 `ParkourAdapterEvents.getServerAdapter().getMotionSnapshot(ServerPlayer)` 读取真实服务端快照。
- 无快照时发送 `hasSnapshot=false`，不拿客户端数据补。
- `PacketDistributor.PLAYER` 只发给对应玩家。
- 玩家退出删除订阅；`ServerStoppingEvent` 清空。

## 6. 客户端 HUD

- F10 `KeyMapping` 默认关闭。
- F10 开启：发送 C2S true。
- F10 关闭：发送 C2S false，清服务端 view。
- `ClientTickEvent.End` 每 tick 更新本地缓存；每 4 tick 更新历史数量和字符串。
- Render 只读取 `DebugHudState` 缓存。
- 左右独立：
  - `CLIENT LOCAL`
  - `SERVER (DEBUG PACKET)`
- 服务端数据 age 每 tick +1；`>10` 显示 STALE；`>20` 清空为 NO SNAPSHOT。
- `NO SNAPSHOT` 不填 0。
- 颜色区分 HIGH/MEDIUM/LOW/UNKNOWN。

## 7. 可信度隔离

- 客户端 motion：`CLIENT_LOCAL/MEDIUM`
- 服务端 motion：`SERVER_NATIVE/HIGH`
- 服务端 action：`PARCOOL_SYNCED/LOW`
- 服务端 FALL：`VANILLA_DERIVED/HIGH`
- 客户端 FALL：`VANILLA_DERIVED/MEDIUM`

HUD 左/右两栏独立缓存，不合并、不互补。

`serverAuthoritative` 是 `source == SERVER_NATIVE` 的派生显示值，不是 `MotionSnapshot` 的独立字段。

## 8. 构建与测试

- 单元测试：**67/67 PASS**
- `compileJava`：PASS
- `build`：PASS（12s）
- 产物：`build/libs/parkourcombat-2.0.0-alpha.1.jar`（80,867 B）
- 专用服务器：`Done (7.276s)`

## 9. 实际客户端证据

- F10 开启：客户端日志 `[DebugHUD] toggled enabled=true`；服务端 `subscription ... enabled=true`。
- F10 关闭：客户端 `toggled enabled=false`；服务端 `subscription ... enabled=false`。
- HUD 截图显示：
  - 左栏 `CLIENT LOCAL`，`motionSource=CLIENT_LOCAL`，`motionTrust=MEDIUM`；
  - 右栏 `SERVER (DEBUG PACKET)`，`motionSource=SERVER_NATIVE`，`motionTrust=HIGH`，`serverAuthoritative=true`；
  - `history=20 / 20`，服务端 `ageTicks`；
  - 两侧 FALL/action 的 source/trust 独立。
- 截图：[phase03_5-hud-dynamic.png](logs/phase03_5-hud-dynamic.png)
- 关闭截图：[phase03_5-hud-off.png](logs/phase03_5-hud-off.png)
- 日志：[runClient-phase03_5-dynamic.debug.log](logs/runClient-phase03_5-dynamic.debug.log)

## 10. 已知限制

1. **HUD 动态运动未验证**：本次自动化按 F10 成功，但模拟 W 输入没有让玩家产生有效位移；HUD 只实际显示空闲数据。实际移动/跳跃/落地需要人工验证。
2. **NO SNAPSHOT / STALE 实际状态未在游戏内观察**；单元测试覆盖。
3. **专用服务器真实玩家**未连接；仅启动级通过。
4. **多人**未测试；只验证了代码只向订阅玩家发送。
5. `serverAuthoritative` 为派生值，不是独立存储字段。
6. 服务端 FALL verticalSpeed 仍沿用阶段 02 的 `FallSnapshot` 语义。
7. `ResourceLocation(String,String)` 在编译时有 deprecation warning；不影响构建。

## 11. 状态

| 项 | 状态 |
|---|---|
| 设计 | **DESIGNED** |
| 代码 | **IMPLEMENTED** |
| 编译 | **COMPILED** |
| 单元测试 | **TESTED（67/67）** |
| 客户端 HUD/F10 | **CLIENT-TESTED（部分）** |
| 集成服务端数据包 | **INTEGRATED-SERVER-TESTED** |
| 专用服务器启动 | **DEDICATED-SERVER-TESTED（启动级）** |
| 专用服务器真实玩家 | **NOT TESTED** |
| 多人 | **NOT TESTED** |
| 整体 VERIFIED | **NO** |

## 12. 结论

Phase 03.5 已实现、编译、67 个单元测试通过；F10、双栏 HUD、服务端真实快照包、历史容量 20/20 均有实际客户端证据。动态运动 HUD、专用服务器真实玩家和多人仍待人工验证。未进入 Phase 04。

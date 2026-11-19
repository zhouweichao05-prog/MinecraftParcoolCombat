# PHASE_03_API_EVIDENCE — 运动数据 API 审计

- 阶段：03
- 文档状态：**AUDITED（仅规划）**
- 目标：确认运动采样所需的 Minecraft/Forge API 在锁定环境中真实存在、签名可用、侧别明确。
- 不涉及 ParCool/Better Combat 的新玩法 API；阶段 02 的动作 API 继续复用。

## 1. 审计环境

- Minecraft 1.20.1
- Forge 47.4.0
- Java 17
- Gradle 8.8
- official mappings（开发运行时）
- 生产 JAR 中为 SRG 名，下列同时给出映射。

## 2. 实际 JAR 与命令

使用的 official-mapped 开发 JAR：

```text
D:\McMod\.gradle\caches\forge_gradle\minecraft_user_repo\
  net\minecraftforge\forge\1.20.1-47.4.0_mapped_official_1.20.1\
  forge-1.20.1-47.4.0_mapped_official_1.20.1.jar
```

可复现命令：

```powershell
$j = 'D:\McMod\.gradle\caches\forge_gradle\minecraft_user_repo\net\minecraftforge\forge\1.20.1-47.4.0_mapped_official_1.20.1\forge-1.20.1-47.4.0_mapped_official_1.20.1.jar'
javap -p -classpath $j net.minecraft.world.entity.Entity
javap -p -classpath $j net.minecraft.world.entity.LivingEntity
javap -p -classpath $j net.minecraft.world.level.Level
javap -p -classpath $j net.minecraft.world.phys.Vec3
javap -p -classpath $j net.minecraft.world.entity.player.Player
```

SRG 映射来自本工程：

```text
build/createSrgToMcp/output.srg
```

## 3. 已确认 API

| 用途 | official 签名 | SRG / 字段 | 访问 | 侧别 |
|---|---|---|---|---|
| 当前位置 | `Entity#position() : Vec3` | `m_20182_` | public | 双端 |
| 当前速度 | `Entity#getDeltaMovement() : Vec3` | `m_20184_` | public | 双端 |
| 是否着地 | `Entity#onGround() : boolean` | `m_20096_` | public | 双端 |
| 下落距离 | `Entity#fallDistance : float` | `f_19789_` | public field | 双端 |
| Y 坐标 | `Entity#getY() : double` | `m_20186_` | public | 双端 |
| 是否在水中 | `Entity#isInWater() : boolean` | `m_20069_` | public | 双端 |
| 是否在岩浆 | `Entity#isInLava() : boolean` | `m_20077_` | public | 双端 |
| 载具 | `Entity#getVehicle() : Entity` | `m_20202_` | public | 双端 |
| 鞘翅飞行 | `LivingEntity#isFallFlying() : boolean` | `m_21255_` | public | 双端 |
| 攀爬 | `LivingEntity#onClimbable() : boolean` | `m_6147_` | public | 双端 |
| 死亡/濒死 | `LivingEntity#isDeadOrDying() : boolean` | `m_21224_` | public | 双端 |
| 游戏时间 | `Level#getGameTime() : long` | `m_46467_` | public | 双端 |
| 玩家 tick | `Entity#tickCount : int` | `f_19797_` | public field | 双端 |
| 客户端侧 | `Level#isClientSide : boolean` / `isClientSide()` | `f_46443_` | public | 双端 |
| 维度 | `Level#dimension() : ResourceKey<Level>` | `m_46472_`（已在阶段 02 使用） | public | 双端 |
| 本地玩家 | `Player#isLocalPlayer() : boolean` | 阶段 02 已使用 | public | 客户端 |
| 玩家 tick 事件 | `TickEvent.PlayerTickEvent`（END） | Forge API | public | 双端 |

`Vec3`：

```java
public final double x;
public final double y;
public final double z;
public Vec3 normalize();
public double lengthSqr();
public double horizontalDistanceSqr();
```

## 4. 侧别与线程

- `PlayerTickEvent` 由 Forge 在主线程分发：
  - 客户端玩家在 `[Render thread]`。
  - 服务端玩家在 `[Server thread]`。
- `MotionTracker` 只应在 `PlayerTickEvent.END` 调用；不在网络包/渲染/异步线程读取。
- 服务端实体位置来自服务端移动包处理后的状态；客户端实体为本地预测/同步状态。
- **实测限制**：`ServerPlayer#getDeltaMovement()` 在服务端移动包处理后可能为 `(0,0,0)`，即使位置已变化。服务端运动速度必须由服务端观测到的位置差 / `sampleTick` 推导；不能用服务端 raw `getDeltaMovement` 作为唯一速度来源。
- **采样 tick**：客户端 `Level#getGameTime()` 在加载/同步时可能重复/回退；实际实现使用 `Player#tickCount` 作为采样 tick。
- 不使用客户端 `MotionSnapshot` 作为服务端权威数据。

## 5. 数据可用性判断

| 字段 | 能否可靠计算 | 说明 |
|---|---|---|
| 三维速度 | 是（服务端需位置推导） | 客户端 `getDeltaMovement()`；服务端 `ServerPlayer#getDeltaMovement` 可能为 0，使用位置差/tick |
| 水平速度 | 是 | `sqrt(vx²+vz²)` |
| 水平方向 | 是（带阈值/保持） | 速度过低时方向无定义 |
| 水平加速度 | 是 | 相邻有效采样速度差；传送/丢 tick 时重置 |
| 垂直加速度 | 是 | 相邻有效采样 `vy` 差 |
| 当前位移 | 是 | `position()`；每 tick 位移由相邻位置差计算 |
| 连续运动时间 | 是 | 每 tick 阈值判定；重置规则明确 |
| 着地 | 是 | `onGround()` |
| 是否 FALLING | 是 | 阶段 02 `FallSnapshot.active()` |
| 当前动作 | 是 | 阶段 02 `ParkourActionTracker` 的活动状态 |
| source/trust | 是 | 由逻辑端和阶段 02 决定 |
| 采样 tick | 是 | `Player#tickCount`（客户端 level gameTime 可能重复/回退） |

## 6. 未确认/运行时验证项

1. 服务端 `getDeltaMovement()` 在 `PlayerTickEvent.END` 的逐 tick 精度：实测可能为 0，已改为位置差推导。
2. 客户端与服务端位置/速度在快速移动/传送时的逐 tick 差异。
3. `fallDistance` 在服务端的更新时序（阶段 02 已有 20 vs 23 的 FALL 计数差异观察）。
4. 实体重建/跨维度后第一 tick 的 `getDeltaMovement()` 是否包含旧速度；因此首帧强制零加速度。
5. 极短下落/单 tick 位移的采样可见性。
6. Forge 事件在单人集成服务端与客户端双端的触发顺序（阶段 02 已确认双端都会触发；阶段 03 不改变路由）。

## 7. 结论

- 运动采样所需 API **全部存在于锁定环境**，无需 Mixin、反射、网络包或版本变更。
- 所有字段可计算；方向/加速度在低速度、传送、丢 tick、维度切换时需要显式重置/阈值。
- 阶段 03 不新增第三方 API 依赖；只使用原版实体/Level API 与现有阶段 02 状态。

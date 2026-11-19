# BETTERCOMBAT_API_AUDIT — Better Combat Forge 1.9.0+1.20.1 API 审计

- 审计对象：`D:\PCL 正式版 2.10.3\dc\.minecraft\versions\DeceasedCraft_CB\mods\bettercombat-forge-1.9.0+1.20.1.jar`
- SHA-256：`49C69DCCF2C641246B4E3DECA81EBAC21F0EE119E81FC713B838B8E33569EEA1`
- 版本来源：`mods.toml` `version="1.9.0+1.20.1"`
- 审计方式：`javap` 字节码 + Fernflower 反编译 + Forge 补丁类字节码核对
- 结论标记：**CONFIRMED** / **PARTIALLY_CONFIRMED** / **UNCONFIRMED**

> 下文使用反编译源码中的 SRG 方法名（如 `m_5706_`）并注明语义。Better Combat 是 Fabric 源码经 Forge 移植的版本，内部使用自带的 Fabric API 兼容 shim 与 Player Animator。

---

## 1. 攻击全链路（客户端 → 服务端 → 伤害）

```
[客户端]
  Minecraft#tick → BetterCombat 注入
    攻击键按下 → startUpswing(attributes)        (MinecraftClientInject.startUpswing)
    倒计时结束 → performAttack()                (MinecraftClientInject.performAttack)
      ├─ PlayerAttackHelper.getCurrentAttack(...) 选出 AttackHand
      ├─ TargetFinder.findAttackTargets(...)      OBB/角度/碰撞/关系过滤（纯客户端）
      ├─ 发送 C2S_AttackRequest(comboCount, isSneaking, selectedSlot, entityIds)
      ├─ 本地播放动画、swing
      └─ BetterCombatClientEvents.ATTACK_HIT / ATTACK_START（客户端 Publisher）

[网络]
  Forge SimpleChannel: bettercombat:network, 协议 "1.0"
  单包类型 PacketWrapper { boolean isClientBound; ResourceLocation packetType; ByteBuf data }
  C2S packetType = bettercombat:c2s_request_attack

[服务端]
  ServerNetwork.initializeHandlers 注册 receiver
    ├─ world 校验（ServerLevel, !isClientSide）
    ├─ 读取 C2S_AttackRequest
    ├─ PlayerAttackHelper.getCurrentAttack(player, request.comboCount())
    │    └─ 失败 → 仅打印错误并 return（本次请求丢弃）
    ├─ 主线程 execute:
    │    ├─ 应用 combo / dual-wield / sweeping 临时属性修饰符
    │    ├─ 遍历 request.entityIds()
    │    │    ├─ world.getEntity(id) 找不到 → ServerLevel.m_143317_(id)（isBossPart）
    │    │    ├─ 过滤：自己（除非可攻击坐骑）、ArmorStand marker、ItemEntity/XP orb/Arrow
    │    │    ├─ useVanillaPacket=true → ServerboundInteractPacket.createAttackPacket(entity, sneaking)
    │    │    │      → handler.handleInteract(packet)
    │    │    │      → entity 存在 + 世界边界 + player.canReach(entity, 3.0)
    │    │    │      → ServerPlayer.attack(entity)（m_5706_）
    │    │    └─ 否则 → player.attack(entity) 直接调用（无 canReach 检查）
    │    └─ 移除临时属性修饰符, comboCount = -1
    └─ 伤害：Player.attack 内部走原版逻辑
         → ForgeHooks.onPlayerAttackTarget → AttackEntityEvent（可取消）
         → 原版暴击/附魔/伤害属性 → LivingEntity.hurt
         → LivingAttackEvent → LivingHurtEvent → LivingDamageEvent
```

- **CONFIRMED**：攻击请求的客户端入口是 `net.bettercombat.mixin.client.MinecraftClientInject`（`@Mixin(Minecraft.class)`）中的 `startUpswing` / `performAttack`，不是 Forge 事件。
- **CONFIRMED**：网络包类为 `net.bettercombat.network.Packets.C2S_AttackRequest`，字段 `(int comboCount, boolean isSneaking, int selectedSlot, int[] entityIds)`。
- **CONFIRMED**：服务端处理入口为 `net.bettercombat.network.ServerNetwork` 中 `ServerPlayNetworking.registerGlobalReceiver(Packets.C2S_AttackRequest.ID, ...)` 的 lambda。
- **CONFIRMED**：真正的“执行攻击”最终仍进入原版 `Player.attack`（SRG `m_5706_`），没有复制一套独立伤害流程。
- **CONFIRMED**：`UseVanillaPacket = true`（默认）时，服务端为每个目标构造 `ServerboundInteractPacket` 并调用同一个 `ServerGamePacketListenerImpl.handleInteract`（SRG `m_6946_`），从而复用原版的距离/实体校验。

---

## 2. C2S 攻击网络包

`net.bettercombat.network.Packets`：

```java
public static record C2S_AttackRequest(int comboCount, boolean isSneaking, int selectedSlot, int[] entityIds) {
    public static ResourceLocation ID = new ResourceLocation("bettercombat", "c2s_request_attack");
    public static boolean UseVanillaPacket = true;
    ...
}
```

- **CONFIRMED**：包 ID 为 `bettercombat:c2s_request_attack`。
- **CONFIRMED**：`entityIds` 是**客户端计算出的目标列表**，直接随包发送；服务端不做二次目标搜索。
- **CONFIRMED**：`comboCount` 由客户端维护并上报；服务端用它选择 `WeaponAttributes.Attack` 索引（`comboCount % attacks.length`）。
- **CONFIRMED**：`selectedSlot` 在服务端**仅用于错误日志**（`ServerNetwork.java` 第 103 行），不参与攻击判定/切换槽位校验。
- **CONFIRMED**：`isSneaking` 被用于 `player.setSneaking(...)`（仅在 `UseVanillaPacket=false` 时）和构造原版攻击包；属于客户端可控的交互标志。
- **CONFIRMED**：没有请求序号/nonce/时间戳；同一个请求可被重复发送并重复执行。
- **CONFIRMED**：Forge 侧使用单一 `PacketWrapper` 承载所有 BC 包，协议版本字符串 `"1.0"`，且 `NetworkRegistry.newSimpleChannel(..., s -> true, s -> true)` 接受任意版本字符串。

服务端发送路径（`Packets.C2S_AttackRequest` 的写包）在 `MinecraftClientInject.performAttack`：

```java
ClientPlayNetworking.send(Packets.C2S_AttackRequest.ID,
    new Packets.C2S_AttackRequest(this.getComboCount(), this.f_91074_.m_6144_(),
                                  this.f_91074_.m_150109_.f_35977_, targets).write());
```

---

## 3. 服务端攻击请求处理与合法性验证

`net.bettercombat.network.ServerNetwork`（反编译，行号对应反编译源码）：

```java
ServerPlayNetworking.registerGlobalReceiver(Packets.C2S_AttackRequest.ID, (server, player, handler, buf, responseSender) -> {
    ServerLevel world = ...;                       // 必须等于 player 所在世界
    if (world != null && !world.f_46443_) {
        Packets.C2S_AttackRequest request = Packets.C2S_AttackRequest.read(buf);
        AttackHand hand = PlayerAttackHelper.getCurrentAttack(player, request.comboCount());
        if (hand == null) { ... return; }
        ...
        world.m_7654_().m_201446_(() -> {           // 切回主线程
            ((PlayerAttackProperties) player).setComboCount(request.comboCount());
            ...
            for (int entityId : request.entityIds()) {
                Entity entity = world.m_6815_(entityId);           // getEntity
                if (entity == null) { isBossPart = true; entity = world.m_143317_(entityId); }
                if (entity != null && ... ) {
                    ...
                    if (!isBossPart && useVanillaPacket) {
                        ServerboundInteractPacket vanillaAttackPacket =
                            ServerboundInteractPacket.m_179605_(entity, request.isSneaking());
                        handler.m_6946_(vanillaAttackPacket);        // handleInteract
                    } else {
                        if (entity instanceof ItemEntity || ... || entity == player) {
                            handler.m_9942_(Component.m_237115_("multiplayer.disconnect.invalid_entity_attacked"));
                            return;
                        }
                        player.m_5706_(entity);                      // 直接 attack
                    }
                    ...
                }
            }
            ...
            ((PlayerAttackProperties) player).setComboCount(-1);
        });
    }
});
```

### 3.1 服务端实际校验项

| 校验 | 是否存在 | 证据 | 状态 |
|---|---|---|---|
| 世界非空且不是客户端世界 | 是 | `world != null && !world.f_46443_` | **CONFIRMED** |
| 玩家当前武器有 BC 武器属性与 attack 数组 | 是 | `PlayerAttackHelper.getCurrentAttack` → `attributes != null && attributes.attacks() != null`，否则 `hand == null` 丢弃 | **CONFIRMED** |
| comboCount 处于合法攻击索引（取模） | 是（间接） | `comboCount % attacks.length`，负数被钳到 0 | **CONFIRMED** |
| 目标实体存在 | 是 | `world.getEntity(id)` / `ServerLevel.m_143317_(id)` | **CONFIRMED** |
| 不能攻击自己 | 是 | `entity == player` 时走 invalid 分支 | **CONFIRMED** |
| 不能攻击被骑乘的坐骑（除非配置允许） | 是 | `!entity.equals(player.m_20202_()) || TargetHelper.isAttackableMount(entity)` | **CONFIRMED** |
| 不能攻击 ArmorStand marker | 是 | `!(entity instanceof ArmorStand) || !((ArmorStand) entity).m_31677_()` | **CONFIRMED** |
| 不能攻击 ItemEntity / ExperienceOrb / AbstractArrow | 是 | `entity instanceof ...` → `disconnect("multiplayer.disconnect.invalid_entity_attacked")` | **CONFIRMED** |
| 原版距离校验 `canReach(entity, 3.0)` | **仅** `!isBossPart && UseVanillaPacket` 路径 | `ServerGamePacketListenerImpl.handleInteract` 字节码：`ServerPlayer.canReach(entity, 3.0)`，失败则不分发 | **CONFIRMED** |
| 世界边界校验 | 是（同上路径） | `WorldBorder.m_61937_(entity.blockPosition())` | **CONFIRMED** |
| 攻击范围 / OBB / 角度 / 穿墙 | 否 | 目标列表由客户端 `TargetFinder` 生成，服务端不重算 | **CONFIRMED** |
| 友军/中立关系（`TargetHelper.Relation`） | 否 | 关系过滤只在客户端 `TargetFinder.getInitialTargets` 中 | **CONFIRMED** |
| 攻击冷却 / 最小间隔 | 否（服务端无显式检查） | `attack_interval_cap` 只参与 `Math.max(getCurrentItemAttackStrengthDelay, cap)` 的击退计算；没有请求时间戳校验 | **CONFIRMED（服务端无显式限速）** |
| `selectedSlot` 与服务器槽位一致 | 否 | 只出现在错误日志 | **CONFIRMED** |
| 重复包去重 | 否 | 无 nonce/缓存 | **CONFIRMED** |

### 3.2 关键风险

1. **CONFIRMED**：当 `isBossPart == true`（`world.getEntity(id)` 找不到、但 `ServerLevel.m_143317_(id)` 能找到）时，服务端走 `player.attack(entity)` 直接分支，**绕过** `handleInteract` 的 `canReach(entity, 3.0)` 与世界边界校验。该分支同时用于 `UseVanillaPacket=false` 模式。
   - 证据：`ServerNetwork.java` 第 165-196 行；`m_6946_` 字节码仅在 vanilla 包路径中调用。
2. **CONFIRMED**：客户端可伪造 `entityIds` 为自己视野内/外任意实体 ID；服务端仅做上述有限校验。
3. **CONFIRMED**：客户端可重复发送同一 `C2S_AttackRequest`。服务端无频率限制；实际伤害仍受原版受击无敌帧等机制影响，但**不能把“BC 有冷却”当作服务端防护**。
4. **PARTIALLY_CONFIRMED**：`PlayerAttackHelper.getCurrentAttack(...)` 在 `world.getServer().execute(...)` **之前**、即网络线程上执行，读取玩家主手/副手与属性；跨线程读取玩家状态存在竞态风险（实际影响需运行时验证）。
5. **CONFIRMED**：`ServerNetwork` 对每个 `entityId` 调用一次攻击；一个请求可以打多个目标（横扫设计），因此“每次攻击只进入一次主要伤害结算”应按**每个目标**理解，而不是每个请求。

---

## 4. 命中检测（客户端）与服务端校验的边界

- **CONFIRMED**：命中检测完全在客户端 `net.bettercombat.client.collision.TargetFinder`：
  - `getInitialTargets(...)`：以玩家 AABB 扩张 `attackRange * target_search_range_multiplier + 1` 搜索，过滤 `Relation.HOSTILE`；
  - `WeaponHitBoxes.createHitbox(attack.hitbox(), attackRange, isSpinAttack)` 生成 OBB；
  - `OrientedBoundingBox` + `CollisionFilter`（OBB 相交）+ `RadialFilter`（角度/距离）+ `rayContainsNoObstacle`（可配置穿墙）。
- **CONFIRMED**：服务端**不**重新执行 OBB/角度/关系判定；它信任 `entityIds`。
- **CONFIRMED**：服务端唯一的几何校验是原版 `handleInteract` 的 `canReach(entity, 3.0)`（普通路径），与武器 `attackRange` 无关。
- **PARTIALLY_CONFIRMED**：因此“客户端命中 = 服务端命中”并不成立；长距离武器/横扫角度/关系判定均可能被客户端绕过，需由 ParkourCombat 自己建立服务端校正层。

---

## 5. 伤害结算

- **CONFIRMED**：伤害由原版 `Player.attack`（SRG `m_5706_`）执行；Better Combat 通过 Mixin 在 `Player.attack` 内选择手/物品、禁用原版横扫、应用连击倍率等，不复制 `LivingEntity.hurt` 流程。
- **CONFIRMED**：`ServerNetwork` 在调用原版攻击前临时添加：
  - `COMBO_DAMAGE_MODIFIER_ID`：`attack.damageMultiplier() - 1.0`，`Operation.MULTIPLY_BASE`；
  - `DUAL_WIELDING_MODIFIER_ID`：副手/主手双持倍率，`MULTIPLY_TOTAL`；
  - `SWEEPING_MODIFIER_ID`：多目标递减倍率（`reworked_sweeping_*` 配置）；
  并在循环结束后移除。
- **CONFIRMED**：`PlayerEntityMixin` 在 `Player.attack` 中重定向 `getMainHandStack` / `setStackInHand` / `getStackInHand`，由 `PlayerAttackHelper.getCurrentAttack(player, comboCount)` 决定使用主手还是副手。
- **CONFIRMED**：`LivingEntityMixin` 只在客户端（`player.m_9236_().m_5776_()`）对副手属性做临时交换；服务端副手属性由 `ServerNetwork` 的 `setAttributesForOffHandAttack(player, true)` 处理。
- **CONFIRMED**：`allow_fast_attacks` 时 `ServerNetwork` 写入 `LivingEntity.f_19802_ = 0`（反编译字段名，未在本次审计中确认其官方语义）；配置注释声称用于“绕过玩家攻击的受击节流”。是否真正绕过无敌帧需要运行时测试，标记 **PARTIALLY_CONFIRMED**。
- **CONFIRMED**：一次 `C2S_AttackRequest` 对每个目标只调用一次 `player.attack`，因此对单个目标只有一次原版伤害入口；多目标横扫是设计行为。

---

## 6. 攻击取消机制

| 机制 | 侧 | 效果 | 状态 |
|---|---|---|---|
| 客户端虚招（`feintKeyBinding`） | 客户端 | `performAttack` 中检测到虚招 → `cancelWeaponSwing()`，不发 C2S 包 | **CONFIRMED**（`MinecraftClientInject.performAttack` 开头） |
| 客户端 `stopAttackAnimation(length)` 包 | 客户端→服务端→其他客户端 | 仅停止动画表现，不影响伤害 | **CONFIRMED**（`Packets.AttackAnimation.StopSymbol`，`AbstractClientPlayerEntityMixin.stopAttackAnimation`） |
| 服务端取消请求 | 服务端 | **不存在** BC 层的请求取消事件；请求已进入 `ServerNetwork` 就会执行 | **CONFIRMED** |
| `AttackEntityEvent` 取消 | 服务端 | 在原版 `Player.attack` 最前面调用；取消则 `return`，BC 的伤害/附魔/连击全部不会发生 | **CONFIRMED**（`ForgeHooks.onPlayerAttackTarget` 字节码；`AttackEntityEvent` 标注 `@Cancelable`） |
| `LivingAttackEvent` 取消 | 服务端 | 已进入 `LivingEntity.hurt` 后取消伤害；BC 已执行完毕（属性已临时修改，随后恢复） | **CONFIRMED**（Forge 事件语义） |

> 结论：**CONFIRMED** 最早、最干净的服务端“阻止这次命中的伤害”钩子是 `AttackEntityEvent`（按目标触发）；但它发生在请求被接受、目标已解析之后，不是“请求入口”。

---

## 7. 攻击冷却

- **CONFIRMED**：`PlayerAttackHelper.getAttackCooldownTicksCapped(player)`：

  ```java
  return Math.max(player.m_36333_(), (float) BetterCombat.config.attack_interval_cap);
  ```

  - `m_36333_` 为原版 `getCurrentItemAttackStrengthDelay()`（攻击速度属性决定）；
  - `attack_interval_cap` 默认 `2` tick（`ServerConfig`）。
- **CONFIRMED**：客户端 `startUpswing` 用该冷却决定起手时间（`attackCooldownTicks`、`upswingTicks`、`comboReset`），并拒绝冷却未完成的攻击。
- **CONFIRMED**：服务端只在计算击退时使用冷却值（`knockbackMultiplier`），**没有**对 `C2S_AttackRequest` 做时间间隔校验。
- **CONFIRMED**：`combo_reset_rate`、`upswing_multiplier` 等均为客户端/双端配置，不能作为服务端反作弊依据。
- **PARTIALLY_CONFIRMED**：恶意客户端能否获得超出设计的 DPS，取决于原版 `LivingEntity` 受击无敌帧与 `allow_fast_attacks` 的实际组合；本次没有做运行时压测，因此不给出“可无限连击”的断言。

---

## 8. 服务端可用事件

| 事件 | 侧 | 可取消 | 触发点 | 状态 |
|---|---|---|---|---|
| `net.minecraftforge.event.entity.player.AttackEntityEvent` | 服务端（`Player.attack` 内） | 是（`@Cancelable`） | 每个目标进入原版攻击流程之前 | **CONFIRMED** |
| `LivingAttackEvent` | 服务端 | 是 | `LivingEntity.hurt` 入口 | **CONFIRMED** |
| `LivingHurtEvent` | 服务端 | 否（可改伤害） | 护甲/抗性计算前 | **CONFIRMED** |
| `LivingDamageEvent` | 服务端 | 否（可改最终伤害） | 伤害已计算后 | **CONFIRMED** |
| `BetterCombatClientEvents.ATTACK_START` | **客户端** | — | 客户端起手（upswing）时 | **CONFIRMED**（`@OnlyIn` 客户端 API，参数为 `LocalPlayer`） |
| `BetterCombatClientEvents.ATTACK_HIT` | **客户端** | — | 客户端**发出请求的同一 tick**，不是命中/伤害确认 | **CONFIRMED**（`MinecraftClientInject.performAttack` 第 409-411 行） |
| BC 自带服务端事件 | 无 | — | `BetterCombat`/`ServerNetwork` 没有 Forge 事件发布 | **CONFIRMED** |

> **警告（符合项目规则“不得把攻击请求当作攻击命中”）**：`BetterCombatClientEvents.ATTACK_HIT` 名字具有误导性；其回调参数是 `List<Entity> targets`（客户端预测），在服务端确认前触发。ParkourCombat **禁止**用它做伤害/词条结算。

---

## 9. 可安全扩展的 API

| API | 用途 | 侧 | 状态 |
|---|---|---|---|
| `data/<namespace>/weapon_attributes/*.json`（`WeaponRegistry.loadAttributes`） | 为物品声明武器属性/攻击段/动画名；支持 `parent` 继承 | 双端（数据包） | **CONFIRMED**（`WeaponRegistry.loadContainers`：`resourceManager.listResources("weapon_attributes", ...)`） |
| `net.bettercombat.api.AttributesContainer` / `WeaponAttributesHelper`（`encode`/`decode`/`override`/`validate`） | 生成/校验上述 JSON，或写入物品 NBT `weapon_attributes` | 双端 | **CONFIRMED** |
| `net.bettercombat.api.EntityPlayer_BetterCombat#getCurrentAttack()` | 读取当前 `AttackHand`（攻击段、连击、主/副手、属性、物品） | 双端（由 `PlayerEntityMixin` 实现） | **CONFIRMED**（服务端在 `ServerNetwork` 中先 `setComboCount(request.comboCount())`，再攻击；`comboCount >= 0` 时 `getCurrentAttack()` 可返回本次攻击） |
| `net.bettercombat.logic.WeaponRegistry#getAttributes(ItemStack)` | 只读武器属性 | 双端 | **CONFIRMED**（public static） |
| `net.bettercombat.api.client.AttackRangeExtensions#register(...)` | 客户端攻击距离修正 | **仅客户端** | **CONFIRMED**（类在 `api.client`，参数 `Player`） |
| `net.bettercombat.api.client.BetterCombatClientEvents` | 客户端表现扩展 | **仅客户端** | **CONFIRMED** |

不建议依赖的内部实现：

- `net.bettercombat.network.ServerNetwork`（无公开注册入口、协议内部）；
- `net.bettercombat.logic.PlayerAttackHelper`（虽有 public static，但属攻击选择内部逻辑）；
- `PlayerAttackProperties`、`MinecraftClient_BetterCombat`、各 `*Accessor` Mixin 接口；
- `Packets.C2S_AttackRequest.UseVanillaPacket`（全局静态开关，改它会影响所有攻击路径）。

---

## 10. 是否需要 Mixin

| 需求 | 是否需要 Mixin | 说明 | 状态 |
|---|---|---|---|
| 在服务端获知“某个目标即将被 BC 攻击” | 否 | `AttackEntityEvent`（可取消）+ `EntityPlayer_BetterCombat#getCurrentAttack()` | **CONFIRMED** |
| 修改/追加伤害 | 否 | `LivingHurtEvent` / `LivingDamageEvent` | **CONFIRMED** |
| 阻止一次命中造成伤害 | 否 | `AttackEntityEvent#setCanceled(true)` | **CONFIRMED** |
| 在“请求入口”拦截/改写目标列表 | 是（或自建协议） | `ServerNetwork` 无公开事件、无注册点 | **CONFIRMED（限制）** |
| 修改客户端命中检测 | 是 | `TargetFinder` 为客户端内部 | **PARTIALLY_CONFIRMED**（可通过 `AttackRangeExtensions` 有限扩展） |

**建议**：ParkourCombat 使用 `AttackEntityEvent` + `LivingHurtEvent` + `EntityPlayer_BetterCombat` 即可，不需要 Mixin 进 Better Combat。

---

## 11. 客户端/服务端加载边界

- **CONFIRMED**：`BetterCombatForge`（`@Mod("bettercombat")`）在构造器中：
  - 调用 `new BetterCombat().onInitialize()`（注册服务端 handler、配置、`WeaponRegistry`）
  - `NetworkHandler.registerMessages()`
  - 注册 `ConfigScreenHandler.ConfigScreenFactory` 的 lambda（引用客户端 `AutoConfig.getConfigScreen`）。
  该 lambda 只在客户端 GUI 打开时执行；但类字面量 `ConfigScreenHandler.ConfigScreenFactory.class` 在构造时即被解析，是否在专用服务器引发类加载问题需以启动测试为准。
- **CONFIRMED**：客户端专属 Mixin 全部位于 `bettercombat.mixins.json` 的 `"client"` 列表（`AbstractClientPlayerEntityMixin`、`MinecraftClientInject` 等）。
- **CONFIRMED**：`ServerNetwork.initializeHandlers()` 在 `onInitialize()` 中无条件执行（双端），使用 `ServerPlayNetworking` shim；服务端不加载 `ClientNetwork`。
- **PARTIALLY_CONFIRMED**：`CompatibilityFlags.initialize()` 在双端执行，若检测到 `pehkui` 会调用 `net.bettercombat.client.compat.PehkuiHelper.load()`（客户端类）；本实例未安装 Pehkui，未触发。若服务器安装 Pehkui，存在专用服务器类加载风险（需实测）。
- **CONFIRMED**：`ForgeClientEvents` / `ForgeClientModEvents` 使用 `@EventBusSubscriber(value = Dist.CLIENT)`，不会在专用服务器注册。

专用服务器启动验证见 `DEVELOPMENT_ENVIRONMENT_REPORT.md`。

---

## 12. 安全审计结论

1. **CONFIRMED**：攻击目标列表完全由客户端提供；服务端只做实体存在性/类型/距离（部分路径）校验，不验证 OBB、角度、关系、视线。
2. **CONFIRMED**：`isBossPart` 分支与 `UseVanillaPacket=false` 分支绕过 `canReach` 检查。
3. **CONFIRMED**：无请求去重、无服务端冷却校验、无 `selectedSlot` 校验。
4. **CONFIRMED**：BC 客户端事件不能作为服务端命中依据。
5. **PARTIALLY_CONFIRMED**：`getCurrentAttack` 的网络线程读取存在竞态风险。
6. **CONFIRMED**：ParkourCombat 若要在 BC 之上做“跑酷攻击”词条，必须以服务端 `AttackEntityEvent`/`LivingHurtEvent` 为结算点，并自行维护“每次攻击一次结算”的幂等键（例如 `(player, target, tick, comboCount)` 或自建攻击序号），不能依赖 BC 的客户端事件或 `entityIds` 顺序。

---

## 13. 可复现验证

```powershell
$jar='D:\PCL 正式版 2.10.3\dc\.minecraft\versions\DeceasedCraft_CB\mods\bettercombat-forge-1.9.0+1.20.1.jar'
# 反编译（目录模式）
& 'C:\Program Files\Java\jdk-17\bin\java.exe' -cp 'D:\IntelliJ IDEA 2024.1.7\plugins\java-decompiler\lib\java-decompiler.jar' `
  org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler -dgs=1 -asc=1 "$env:TEMP\pc_audit\x_bettercombat" "$env:TEMP\pc_audit\src_bettercombat"

# 关键类
javap -p -classpath $jar net.bettercombat.network.Packets`$C2S_AttackRequest
javap -p -classpath $jar net.bettercombat.network.ServerNetwork
javap -p -classpath $jar net.bettercombat.logic.PlayerAttackHelper
javap -p -classpath $jar net.bettercombat.api.EntityPlayer_BetterCombat

# 原版校验与事件（Forge 补丁 jar）
$fc='D:\PCL 正式版 2.10.3\dc\.minecraft\libraries\net\minecraftforge\forge\1.20.1-47.4.0\forge-1.20.1-47.4.0-client.jar'
javap -c -p -classpath $fc net.minecraft.server.network.ServerGamePacketListenerImpl  # m_6946_ -> canReach(entity, 3.0)
javap -c -p -classpath $fc net.minecraft.world.entity.player.Player                       # m_5706_ -> ForgeHooks.onPlayerAttackTarget

$u='D:\PCL 正式版 2.10.3\dc\.minecraft\libraries\net\minecraftforge\forge\1.20.1-47.4.0\forge-1.20.1-47.4.0-universal.jar'
javap -c -p -classpath $u net.minecraftforge.common.ForgeHooks                              # onPlayerAttackTarget -> AttackEntityEvent
javap -v -p -classpath $u net.minecraftforge.event.entity.player.AttackEntityEvent          # @Cancelable
```

---

## 14. 结论汇总

- **CONFIRMED**：BC 的攻击链路是“客户端 OBB 命中 → C2S 目标列表 → 服务端有限校验 → 原版 `Player.attack` 结算”。
- **CONFIRMED**：服务端可用的最早、最可靠的伤害拦截点是 `AttackEntityEvent`（可按目标取消），其次是 `LivingAttackEvent`/`LivingHurtEvent`。
- **CONFIRMED**：BC 没有服务端攻击请求事件；`ATTACK_START`/`ATTACK_HIT` 是客户端事件，且 `ATTACK_HIT` 在请求时触发，不能当作命中。
- **CONFIRMED**：目标列表、连击数、潜行标志均为客户端可控；服务端不校验 OBB/角度/关系/冷却。
- **CONFIRMED**：`EntityPlayer_BetterCombat#getCurrentAttack()` 可在服务端攻击流程中读取当前攻击段（`comboCount >= 0` 时），是构建词条结算的有用只读入口。
- **PARTIALLY_CONFIRMED**：Boss part 分支绕过 `canReach`、网络线程读取竞态、`allow_fast_attacks` 实际效果需要运行时验证。
- **CONFIRMED（启动级）**：专用服务器启动测试成功，BC 1.9.0+1.20.1 在服务端完成 `WeaponRegistry` 编码并到达 `Done`；玩家联机/攻击结算仍未做运行时验证（NOT TESTED）。

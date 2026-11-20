package com.MrGhost.parkourCombat.compat.bettercombat;

import com.MrGhost.parkourCombat.combat.CombatAttackRequest;
import com.MrGhost.parkourCombat.combat.CombatWeaponSnapshot;
import java.util.Optional;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.ComboState;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.PlayerAttackHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Version-locked Better Combat adapter. This class is only loaded when Better
 * Combat is present.
 */
final class BetterCombatAttackAdapter {
    private static final int MAX_TARGETS = 64;

    private BetterCombatAttackAdapter() {
    }

    static Optional<CombatWeaponSnapshot> snapshot(ServerPlayer player, int comboCount) {
        AttackHand hand = PlayerAttackHelper.getCurrentAttack(player, comboCount);
        if (hand == null || hand.attack() == null || hand.attributes() == null) {
            return Optional.empty();
        }
        WeaponAttributes attributes = hand.attributes();
        WeaponAttributes.Attack attack = hand.attack();
        ItemStack stack = hand.itemStack();
        ResourceLocation itemId = stack == null
                ? null
                : BuiltInRegistries.ITEM.getKey(stack.getItem());
        ComboState combo = hand.combo();
        return Optional.of(new CombatWeaponSnapshot(
                itemId,
                stack == null ? 0 : stack.getCount(),
                attributes.category(),
                attributes.isTwoHanded(),
                combo == null ? comboCount : combo.current(),
                combo == null ? 0 : combo.total(),
                attack.animation(),
                attributes.attackRange(),
                attack.damageMultiplier(),
                attack.hitbox() == null ? null : attack.hitbox().name(),
                attack.angle(),
                hand.isOffHand(),
                stack != null && stack.hasTag()
        ));
    }

    /**
     * Bounded parse of the exact Better Combat C2S_AttackRequest wire format.
     * The original buffer is not consumed; callers pass a copy.
     */
    static CombatAttackRequest decodeRequest(FriendlyByteBuf copy) {
        if (copy == null) {
            return null;
        }
        try {
            int comboCount = copy.readInt();
            boolean sneaking = copy.readBoolean();
            int selectedSlot = copy.readInt();
            int rawCount = copy.readVarInt();
            if (rawCount < 0 || rawCount > copy.readableBytes()) {
                return null;
            }
            int captured = Math.min(rawCount, MAX_TARGETS);
            int[] ids = new int[captured];
            for (int i = 0; i < captured; i++) {
                ids[i] = copy.readVarInt();
            }
            for (int i = captured; i < rawCount; i++) {
                copy.readVarInt();
            }
            return new CombatAttackRequest(
                    comboCount, sneaking, selectedSlot, rawCount, ids,
                    rawCount > captured);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}

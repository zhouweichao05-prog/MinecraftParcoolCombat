package com.MrGhost.parkourCombat.client.debug;

import com.MrGhost.parkourCombat.ParkourCombat;
import com.MrGhost.parkourCombat.adapter.MotionSnapshot;
import com.MrGhost.parkourCombat.adapter.ParCoolActionAdapter;
import com.MrGhost.parkourCombat.adapter.ParkourAdapterEvents;
import com.MrGhost.parkourCombat.debug.DebugSnapshotInbox;
import com.MrGhost.parkourCombat.network.ClientboundMotionDebugSnapshot;
import com.MrGhost.parkourCombat.network.DebugNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Client-only cache updater. The render path only reads DebugHudState.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ParkourCombat.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class ClientDebugTick {
    public static final DebugHudState STATE = new DebugHudState();
    private static final Logger LOGGER =
            LogManager.getLogger("parkourcombat.debug.hud");

    private static int tickCounter;
    private static int historyCount;
    private static int historyCapacity;
    private static ResourceKey<Level> lastDimension;

    private ClientDebugTick() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            if (STATE.isEnabled()) {
                STATE.clearAll();
            }
            DebugSnapshotInbox.clear();
            lastDimension = null;
            return;
        }

        ResourceKey<Level> dimension = player.level().dimension();
        if (lastDimension == null) {
            lastDimension = dimension;
        } else if (!lastDimension.equals(dimension)) {
            STATE.clearServer();
            DebugSnapshotInbox.clear();
            lastDimension = dimension;
        }

        if (ClientDebugHud.TOGGLE.consumeClick()) {
            boolean enabled = !STATE.isEnabled();
            STATE.setEnabled(enabled);
            DebugSnapshotInbox.clear();
            historyCount = 0;
            historyCapacity = 0;
            ClientPacketListener connection = minecraft.getConnection();
            if (connection != null) {
                DebugNetwork.sendSubscription(enabled);
            }
            if (enabled) {
                STATE.refresh();
            }
            LOGGER.debug("[DebugHUD] toggled enabled={}", enabled);
        }

        if (!STATE.isEnabled()) {
            return;
        }

        if (ClientDebugHud.DETAIL.consumeClick()) {
            STATE.toggleCompact();
            STATE.refresh();
        }
        if (ClientDebugHud.PAGE_UP.consumeClick()) {
            STATE.pageUp();
        }
        if (ClientDebugHud.PAGE_DOWN.consumeClick()) {
            STATE.pageDown();
        }

        tickCounter++;
        ParCoolActionAdapter client = ParkourAdapterEvents.getClientAdapter();
        MotionSnapshot local = null;
        if (client != null) {
            local = client.getMotionSnapshot(player).orElse(null);
            if (tickCounter % 4 == 0) {
                historyCapacity = client.getMotionHistoryCapacity(player);
                historyCount = local != null && historyCapacity > 0
                        ? client.getMotionHistory(player, historyCapacity).size()
                        : 0;
            }
        } else {
            historyCount = 0;
            historyCapacity = 0;
        }
        STATE.updateClient(local, historyCount, historyCapacity);

        if (player.isDeadOrDying()) {
            STATE.clearServer();
            DebugSnapshotInbox.clear();
        } else {
            ClientboundMotionDebugSnapshot packet = DebugSnapshotInbox.poll();
            if (packet != null) {
                STATE.acceptServer(packet);
            } else {
                STATE.tickServerAge();
            }
            var combat = DebugSnapshotInbox.pollCombat();
            if (combat != null) {
                STATE.acceptCombat(combat);
            }
        }

        if (tickCounter % 4 == 0) {
            STATE.refresh();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        STATE.clearAll();
        DebugSnapshotInbox.clear();
        historyCount = 0;
        historyCapacity = 0;
        lastDimension = null;
    }

    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        STATE.clearServer();
        DebugSnapshotInbox.clear();
        historyCount = 0;
        historyCapacity = 0;
    }
}

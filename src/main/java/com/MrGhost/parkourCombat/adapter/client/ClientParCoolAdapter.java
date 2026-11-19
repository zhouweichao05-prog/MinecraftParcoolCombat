package com.MrGhost.parkourCombat.adapter.client;

import com.MrGhost.parkourCombat.ParkourCombat;
import com.MrGhost.parkourCombat.adapter.ParCoolActionAdapter;
import com.MrGhost.parkourCombat.adapter.ParkourAdapterEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only lifecycle cleanup. This class is discovered by Forge only on the
 * physical client; common code never references it, which keeps client classes
 * away from the dedicated server.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ParkourCombat.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class ClientParCoolAdapter {
    private ClientParCoolAdapter() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ParCoolActionAdapter adapter = ParkourAdapterEvents.getClientAdapter();
        if (adapter != null) {
            adapter.clearAll("CLIENT_LOGOUT");
        }
    }

    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        ParCoolActionAdapter adapter = ParkourAdapterEvents.getClientAdapter();
        if (adapter != null) {
            adapter.clearAll("CLIENT_CLONE");
        }
    }
}

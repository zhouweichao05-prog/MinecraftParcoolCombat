package com.MrGhost.parkourCombat;

import com.MrGhost.parkourCombat.adapter.ParCoolActionAdapter;
import com.MrGhost.parkourCombat.adapter.ParkourAdapterEvents;
import com.MrGhost.parkourCombat.adapter.server.ServerParCoolAdapter;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * ParkourCombat 2.0 entry point.
 *
 * <p>Phase 02 registers a read-only ParCool adapter. No combat, damage, affix,
 * animation or network implementation is present.</p>
 */
@Mod(ParkourCombat.MOD_ID)
public final class ParkourCombat {
    public static final String MOD_ID = "parkourcombat";

    public ParkourCombat() {
        // The server adapter is always present, including inside an integrated
        // server. The client adapter exists only on the physical client.
        ParCoolActionAdapter serverAdapter = new ServerParCoolAdapter();
        ParCoolActionAdapter clientAdapter = FMLEnvironment.dist.isClient()
                ? new ParCoolActionAdapter(true)
                : null;
        ParkourAdapterEvents.init(serverAdapter, clientAdapter);
        MinecraftForge.EVENT_BUS.register(ParkourAdapterEvents.class);
    }
}

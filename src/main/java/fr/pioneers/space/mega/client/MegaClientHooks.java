package fr.pioneers.space.mega.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** 1.0.7 (lot 4) : ouverture de l'ecran du chantier depuis le code commun ({@code DistExecutor}). */
public final class MegaClientHooks {

    private MegaClientHooks() {
    }

    public static void openSite(BlockPos pos, int mega) {
        Minecraft.getInstance().setScreen(new MegaSiteScreen(pos, mega));
    }
}

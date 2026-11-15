package fr.pioneers.bronze;

import fr.pioneers.kinetics.KineticEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Evenements de l'age du bronze remontes aux progres (meme declencheur {@code pioneers:mechanics} que l'age mecanique). */
public final class BronzeEvents {

    private BronzeEvents() {
    }

    public static void award(ServerLevel level, BlockPos pos, String event) {
        KineticEvents.award(level, pos, event);
    }
}

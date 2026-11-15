package fr.pioneers.kinetics;

import fr.pioneers.registries.PiTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Evenements de l'age mecanique remontes aux progres (succes) des joueurs proches. */
public final class KineticEvents {

    private KineticEvents() {
    }

    /** Une piece a casse sous la contrainte. */
    public static void broken(ServerLevel level, BlockPos pos) {
        award(level, pos, "part_broken");
    }

    /** Declenche le critere {@code pioneers:mechanics} {@code event} pour les joueurs a moins de 24 blocs. */
    public static void award(ServerLevel level, BlockPos pos, String event) {
        //0.20.46 (audit) : la liste des joueurs du monde plutot qu'une recherche d'entites dans une boite de 49 blocs a chaque fabrication
        for (ServerPlayer player : level.players()) {
            if (Math.abs(player.getX() - pos.getX() - 0.5) <= 24.5 && Math.abs(player.getY() - pos.getY() - 0.5) <= 24.5
                  && Math.abs(player.getZ() - pos.getZ() - 0.5) <= 24.5) {
                PiTriggers.MECHANICS.trigger(player, event);
            }
        }
    }
}

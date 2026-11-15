package fr.pioneers.industry;

import fr.pioneers.kinetics.KineticEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Progres de l'age industriel (declencheur {@code pioneers:mechanics}, evenement {@code industry_<machine>}). */
public final class IndustryEvents {

    private IndustryEvents() {
    }

    public static void award(Level level, BlockPos pos, IndustryMachine machine) {
        if (level instanceof ServerLevel server) {
            KineticEvents.award(server, pos, "industry_" + machine.key);
        }
    }

    /**
     * 1.0.4-beta (lot 11, fusion des machines) : recette finie par une machine : son evenement, et celui de la machine absorbee d'ou vient la
     * recette ({@code family}) : les progres de l'absorbee (« Écume », « Frittage »...) se gagnent a la base.
     */
    public static void award(Level level, BlockPos pos, IndustryMachine machine, fr.pioneers.industry.recipe.IndustrialRecipe recipe) {
        award(level, pos, machine);
        if (recipe != null && recipe.family() != null) {
            award(level, pos, "industry_" + recipe.family());
        }
    }

    /**
     * Premiere formation d'un multibloc industriel : « industry_formed », et le progres du tier de ses carcasses (« Tier MKIII »...).
     * 1.0.4-beta (lot 32, Guillaume : « Garder, masquer partout ») : une salle blanche n'a pas de carcasse et son niveau fixe ne s'affiche
     * jamais : elle ne donne pas ce progres (son toast et son message de chat diraient « Tier MKIII / MKIV »).
     */
    public static void formed(Level level, BlockPos pos, IndustryMachine machine, int tier) {
        award(level, pos, "industry_formed");
        if (!fr.pioneers.industry.multi.Cleanroom.is(machine)) {
            award(level, pos, "industry_formed_" + tier);
        }
    }

    public static void award(Level level, BlockPos pos, String event) {
        if (level instanceof ServerLevel server) {
            KineticEvents.award(server, pos, event);
        }
    }
}

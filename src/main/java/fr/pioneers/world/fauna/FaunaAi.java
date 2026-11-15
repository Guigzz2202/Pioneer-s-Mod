package fr.pioneers.world.fauna;

import net.minecraft.world.entity.Mob;

/**
 * 1.0.5-beta (lot 4, spec §3 « IA allegee pour les animaux loin de tout joueur : au-dela de 48 blocs, decisions 4 fois moins souvent ») :
 * appele par le mixin de {@code Mob} (MobMixin, paquet des mixins) sur le compteur de {@code Mob#serverAiStep}. Seules les betes de la faune inscrite
 * ({@link FaunaRegistry}) sont concernees ; loin de tout joueur ({@link FaunaSettings#aiDistance}), la selection des buts (et des cibles)
 * ne tourne plus qu'un tick sur 8 au lieu d'un sur 2 ; les buts en cours, la navigation, les deplacements et la physique tournent comme
 * avant a chaque tick. Reglage {@code fauna.lightAi}.
 */
public final class FaunaAi {

    private FaunaAi() {
    }

    /** Selections de buts sautees depuis le demarrage (fiche de diagnostic, banc). */
    public static long skipped;

    public static int gate(Mob mob, int counter) {
        if ((counter & 1) != 0) {
            return counter;
        }
        FaunaSettings s = FaunaSettings.current();
        if (!s.enabled() || !s.lightAi() || FaunaRegistry.of(mob) == null) {
            return counter;
        }
        boolean far = !mob.level().hasNearbyAlivePlayer(mob.getX(), mob.getY(), mob.getZ(), s.aiDistance());
        int out = FaunaRules.aiGate(counter, far);
        if (out != counter) {
            skipped++;
        }
        return out;
    }
}

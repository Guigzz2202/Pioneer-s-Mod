package fr.pioneers.logistics.robotics;

import java.util.Locale;
import net.minecraft.core.BlockPos;

/**
 * Alerte du reseau robotique (facon Factorio) : affichee sur la carte tant que le probleme dure. Elle se resout seule quand elle
 * n'est plus signalee pendant un delai propre a son type (ou des que le probleme est regle : livraison faite, depot reussi, hub
 * alimente). {@code first} : debut ; {@code time} : dernier signalement ; {@code returns} : retours apres resolution (alerte
 * recurrente des le premier retour en moins de 10 minutes).
 */
public record RoboticsAlert(Type type, BlockPos pos, long time, long first, int returns) {

    public boolean recurring() {
        return returns > 0;
    }

    public enum Type {
        /** Une demande ne trouve aucune source. */
        NO_SOURCE(0xFFB74D),
        /** Plus de place dans les stockages. */
        STORAGE_FULL(0xFFD54F),
        /** Hub ou relais sans energie. */
        NO_ENERGY(0xE57373),
        /** Point inaccessible aux unites au sol (confie aux drones). */
        UNREACHABLE(0xBA68C8),
        /** Une livraison attend depuis longtemps : aucune unite libre pour la faire. */
        NO_CARRIER(0x4FC3F7);

        public final int color;

        Type(int color) {
            this.color = color;
        }

        /** Ticks sans signalement apres lesquels l'alerte est consideree comme resolue. */
        public long quiet() {
            return switch (this) {
                case NO_SOURCE, NO_CARRIER -> 200;
                case NO_ENERGY -> 120;
                case STORAGE_FULL, UNREACHABLE -> 1200;
            };
        }

        public String getTranslationKey() {
            return "robotics.pioneers.alert." + name().toLowerCase(Locale.ROOT);
        }
    }
}

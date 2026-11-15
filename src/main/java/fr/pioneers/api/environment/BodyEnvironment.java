package fr.pioneers.api.environment;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * Conditions a la surface d'un astre (ou dans une station orbitale).
 *
 * @param gravity     gravite de jeu (1 = Terre), deja ramenee a une valeur jouable pour les petits corps.
 * @param breathable  atmosphere respirable (seulement la Terre).
 * @param suitTier    combinaison minimale exigee hors d'une piece pressurisee (0 = aucune, 1 = base, 2 = thermique, 3 = anti-acide,
 *                    4 = atmospherique).
 * @param hazard      danger principal si la combinaison est insuffisante.
 * @param nitrogen    azote capte par le collecteur de gaz a ciel ouvert (mB/s, 0 = pas d'atmosphere azotee).
 * @param temperature temperature moyenne (degres Celsius), pour l'affichage.
 */
public record BodyEnvironment(double gravity, boolean breathable, int suitTier, Hazard hazard, double nitrogen, int temperature) {

    public static final BodyEnvironment EARTH = new BodyEnvironment(1, true, 0, Hazard.NONE, 4, 15);

    public enum Hazard {
        NONE, VACUUM, COLD, HEAT, ACID, RADIATION, PRESSURE;

        public String getTranslationKey() {
            return "environment.pioneers.hazard." + name().toLowerCase(Locale.ROOT);
        }

        public Component getName() {
            return Component.translatable(getTranslationKey());
        }
    }
}

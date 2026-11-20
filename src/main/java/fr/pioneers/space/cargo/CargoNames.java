package fr.pioneers.space.cargo;

import fr.pioneers.space.mega.Megastructures;
import net.minecraft.network.chat.Component;

/**
 * 1.0.7 : nom affiche d'une destination du reseau cargo. Un spatioport porte le nom que lui a donne son joueur ; un chantier de
 * megastructure porte {@code @mega:<id>} ({@link CargoNetwork#megaSiteName}), traduit ici en « Chantier : <megastructure> ».
 */
public final class CargoNames {

    private CargoNames() {
    }

    public static Component of(String name) {
        if (name.startsWith("@mega:")) {
            int i = Megastructures.byId(name.substring(6));
            if (i >= 0) {
                return Component.translatable("mega.pioneers.site_name", Megastructures.name(i));
            }
        }
        return Component.literal(name);
    }
}

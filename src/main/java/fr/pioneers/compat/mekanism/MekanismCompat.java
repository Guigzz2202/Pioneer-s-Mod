package fr.pioneers.compat.mekanism;

import net.minecraftforge.fml.ModList;

/**
 * Compatibilite avec Mekanism. Les classes de ce paquet sont les seules a voir Mekanism quand la migration sera finie ; elles ne sont
 * chargees que si {@link #LOADED}.
 */
public final class MekanismCompat {

    public static final boolean LOADED = ModList.get().isLoaded("mekanism");

    private MekanismCompat() {
    }

    public static void init() {
        if (LOADED) {
            MekanismGasBridge.init();
        }
    }
}

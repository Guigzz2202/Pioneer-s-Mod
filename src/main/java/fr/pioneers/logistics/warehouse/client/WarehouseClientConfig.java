package fr.pioneers.logistics.warehouse.client;

import fr.pioneers.core.common.PiClientConfig;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 1.0.7 (Guillaume, 18/11/2026 : « quand on trie le comptoir d'entrepot, il se souvient de notre choix ») : tri de la liste du comptoir,
 * section {@code warehouse} du fichier {@code config/pioneers-client.toml}. Classe sans classe client de Minecraft (construite par
 * {@code common/PiClientConfig}, comme la minimap et la fiche au regard) ; sauve tout de suite a chaque changement.
 */
public final class WarehouseClientConfig {

    /** Tris de la liste du comptoir, dans l'ordre du bouton. */
    public enum Sort {
        COUNT, NAME, MOD
    }

    public static ForgeConfigSpec.EnumValue<Sort> SORT;

    private WarehouseClientConfig() {
    }

    public static void build(ForgeConfigSpec.Builder b) {
        b.push("warehouse");
        SORT = b.comment("Tri de la liste du comptoir d'entrepot : COUNT (quantite, defaut), NAME (nom), MOD (un bandeau par mod).")
              .defineEnum("sort", Sort.COUNT);
        b.pop();
    }

    /** Tri garde (fichier pas encore charge : par quantite). */
    public static Sort sort() {
        try {
            return SORT == null ? Sort.COUNT : SORT.get();
        } catch (IllegalStateException e) {
            return Sort.COUNT;
        }
    }

    /** Garde le tri choisi (sauve tout de suite). */
    public static void setSort(Sort s) {
        if (SORT == null || s == sort()) {
            return;
        }
        try {
            SORT.set(s);
            PiClientConfig.SPEC.save();
        } catch (IllegalStateException ignored) {
            //fichier pas encore charge : rien a faire
        }
    }
}

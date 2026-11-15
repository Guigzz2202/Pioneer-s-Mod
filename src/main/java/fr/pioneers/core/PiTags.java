package fr.pioneers.core;

import fr.pioneers.Pioneers;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * Tags du mod (data-driven : la liste des blocs vit dans data/pioneers/tags, modifiable par pack de donnees ou KubeJS).
 * Les tags propres a un module restent pres de leur code (RoboticHubValidator.GLASS, RoverRoad.DIGGABLE, Wrenches).
 */
public final class PiTags {

    private PiTags() {
    }

    public static final class Blocks {

        private Blocks() {
        }

        /** Blocs que les unites robotiques evitent (feu, cactus, magma, neige poudreuse...). La lave est evitee par son fluide. */
        public static final TagKey<Block> ROBOT_HAZARDS = tag("robot_hazards");
        /** Plantes en colonne recoltees en laissant la base (canne a sucre, cactus, bambou). */
        public static final TagKey<Block> ROBOT_COLUMN_PLANTS = tag("robot_column_plants");
        /** Terre que les fermiers labourent. */
        public static final TagKey<Block> ROBOT_TILLABLE = tag("robot_tillable");
        /** Terre que les unites faconnent sur place en chemin ou en terre labouree (travaux). */
        public static final TagKey<Block> ROBOT_SHAPEABLE = tag("robot_shapeable");

        private static TagKey<Block> tag(String name) {
            return TagKey.create(Registries.BLOCK, Pioneers.rl(name));
        }
    }

    public static final class Items {

        private Items() {
        }

        /**
         * MAJ Recettes (lot 1) : sorties que le four electrique (kit, atelier, multibloc) refuse parmi les cuissons vanilla qu'il lit ; il ne
         * garde que la nourriture et les matieres de l'age du bronze (tag ecrit par T:balance/rebalance.py, electric_furnace_tag).
         */
        public static final TagKey<Item> ELECTRIC_FURNACE_DENIED = TagKey.create(Registries.ITEM, Pioneers.rl("furnace_denied"));
        /**
         * 1.0.3-beta (lot 3) : concasses des metaux de tier 1 (fer, cuivre, etain, plomb, zinc, or) : le broyage rend toujours 2 concasses par
         * minerai (pas de bonus de minerai du multibloc) ; le gain par tier vient du creuset electrique (tag ecrit par T:core/p103_lot3.py).
         */
        public static final TagKey<Item> FIXED_CRUSH_YIELD = TagKey.create(Registries.ITEM, Pioneers.rl("fixed_crush_yield"));

        /** Vrai si le four electrique peut sortir cet objet d'une cuisson vanilla. */
        public static boolean electricFurnaceAllows(ItemStack result) {
            return !result.isEmpty() && !result.is(ELECTRIC_FURNACE_DENIED);
        }
    }

    public static final class Fluids {

        private Fluids() {
        }

        /** Carburant des fusees (hydrogene liquide ; celui de Mekanism s'il est installe). */
        public static final TagKey<Fluid> ROCKET_FUEL = TagKey.create(Registries.FLUID, Pioneers.rl("rocket_fuel"));
    }
}

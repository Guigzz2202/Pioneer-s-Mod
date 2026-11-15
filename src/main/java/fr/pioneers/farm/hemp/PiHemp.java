package fr.pioneers.farm.hemp;

import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.item.ItemWithDescription;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.0.4-beta (lot 6, Guillaume : « implementer le canabis » ; precision : « chanvre + produit fumable ») : le chanvre (culture {@code hemp}
 * de {@code tools/gc/farm2/crops.py}, deja la : tige, graines, fibre, toile, corde, ficelle, huile a la presse) gagne ses TETES (fleurs) a la
 * recolte d'un plant mur ({@link #harvestExtras}), sechees au sechoir, et le JOINT ({@link HempJointItem}, {@link LitHempJointItem}) roule
 * avec du papier. Donnees : {@code tools/gc/farm2/hemp.py} (icones, modeles, recettes, tags, langue, guide).
 */
public final class PiHemp {

    private PiHemp() {
    }

    public static void init() {
    }

    public static final RegistryObject<Item> HEMP_FLOWER = PiCoreRegistries.ITEMS.register("hemp_flower",
          () -> new ItemWithDescription(new Item.Properties()));
    public static final RegistryObject<Item> DRIED_HEMP_FLOWER = PiCoreRegistries.ITEMS.register("dried_hemp_flower",
          () -> new ItemWithDescription(new Item.Properties()));
    public static final RegistryObject<Item> HEMP_JOINT = PiCoreRegistries.ITEMS.register("hemp_joint",
          () -> new HempJointItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> LIT_HEMP_JOINT = PiCoreRegistries.ITEMS.register("lit_hemp_joint",
          () -> new LitHempJointItem(new Item.Properties().stacksTo(1)));

    /** Tetes de chanvre d'un plant mur : 1 ou 2 (x rendement des genes, x 1,25 si fertilise), comme la tige ; rien pour les autres cultures. */
    public static List<ItemStack> harvestExtras(String crop, float yieldFactor, RandomSource random) {
        if (!"hemp".equals(crop)) {
            return List.of();
        }
        float amount = 1.5F * yieldFactor;
        int count = Math.max(1, (int) amount + (random.nextFloat() < amount - (int) amount ? 1 : 0));
        return List.of(new ItemStack(HEMP_FLOWER.get(), count));
    }
}

package fr.pioneers.core.vanilla;

import com.mojang.serialization.Codec;
import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.materials.PiMaterials;
import fr.pioneers.mechanics.PiMechanics;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * Materiaux qui remplacent ceux de Minecraft (1.0) et que les fiches materiau ne generent pas : charbon, charbon de bois, redstone (pose
 * le fil de redstone vanilla), blocs de charbon, de charbon de bois et de redstone (source de courant), fragments de netherite.
 * Les lingots, pepites, blocs de fer / or / cuivre / netherite et les gemmes (diamant, emeraude, lapis, quartz) viennent des fiches
 * materiau ({@link PiMaterials}, tools/gc/materials/materials.py). Conversion des objets vanilla : {@link VanillaReplacements}.
 */
public final class PiVanillaMaterials {

    private PiVanillaMaterials() {
    }

    /** Objet combustible (duree en ticks de four). */
    public static class FuelItem extends Item {

        private final int burn;

        public FuelItem(Properties properties, int burn) {
            super(properties);
            this.burn = burn;
        }

        @Override
        public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
            return burn;
        }
    }

    /** Bloc combustible. */
    public static class FuelBlockItem extends BlockItem {

        private final int burn;

        public FuelBlockItem(Block block, Properties properties, int burn) {
            super(block, properties);
            this.burn = burn;
        }

        @Override
        public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
            return burn;
        }
    }

    public static final RegistryObject<Item> COAL = PiCoreRegistries.ITEMS.register("coal", () -> new FuelItem(new Item.Properties(), 1600));
    public static final RegistryObject<Item> CHARCOAL = PiCoreRegistries.ITEMS.register("charcoal", () -> new FuelItem(new Item.Properties(), 1600));
    public static final RegistryObject<Item> REDSTONE = PiCoreRegistries.ITEMS.register("redstone",
          () -> new ItemNameBlockItem(Blocks.REDSTONE_WIRE, new Item.Properties()));
    public static final RegistryObject<Item> NETHERITE_SCRAP = PiCoreRegistries.ITEMS.register("netherite_scrap",
          () -> new Item(new Item.Properties().fireResistant()));

    public static final BlockEntry<Block> COAL_BLOCK = PiCoreRegistries.block("coal_block",
          () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(5.0F, 6.0F)),
          b -> new FuelBlockItem(b, new Item.Properties(), 16000));
    public static final BlockEntry<Block> CHARCOAL_BLOCK = PiCoreRegistries.block("charcoal_block",
          () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).requiresCorrectToolForDrops().strength(5.0F, 6.0F)),
          b -> new FuelBlockItem(b, new Item.Properties(), 16000));
    public static final BlockEntry<PoweredBlock> REDSTONE_BLOCK = PiCoreRegistries.block("redstone_block",
          () -> new PoweredBlock(BlockBehaviour.Properties.of().mapColor(MapColor.FIRE).requiresCorrectToolForDrops().strength(5.0F, 6.0F)
                .sound(SoundType.METAL).isRedstoneConductor((state, level, pos) -> false)),
          b -> new BlockItem(b, new Item.Properties()));

    /** Ordre dans l'onglet Core. */
    public static List<Supplier<? extends ItemLike>> tab() {
        return List.of(COAL, COAL_BLOCK, CHARCOAL, CHARCOAL_BLOCK, REDSTONE, REDSTONE_BLOCK, NETHERITE_SCRAP);
    }

    /** Objet du mod par identifiant (fiches materiau ou ce registre), pour remplacer une constante vanilla dans le code. */
    public static Item item(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(Pioneers.rl(id));
        return item == null ? Items.AIR : item;
    }

    /** Butins : objets vanilla remplaces (data/pioneers/loot_modifiers/replace_vanilla.json). */
    public static final RegistryObject<Codec<ReplaceVanillaModifier>> REPLACE_VANILLA =
          PiMechanics.LOOT_MODIFIERS.register("replace_vanilla", () -> ReplaceVanillaModifier.CODEC);

    public static void init() {
    }
}

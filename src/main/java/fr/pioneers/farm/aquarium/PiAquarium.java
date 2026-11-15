package fr.pioneers.farm.aquarium;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.registries.PiTileEntityTypes;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.0.4-beta (lot 28) : l'AQUARIUM ({@link AquariumBlock}, {@link AquariumTile}) et le SEAU DE CRUSTACE ({@link CrustaceanBucketItem}).
 * Donnees : {@code tools/gc/farm2/aquarium.py} (textures connectees, modeles, recette, langue). 1.0.5-beta (lot 25) : interface de
 * composition ({@link #MENU}) ; textes {@code T:core/p105_lot25.py}.
 */
public final class PiAquarium {

    private PiAquarium() {
    }

    public static void init() {
    }

    public static final BlockEntry<AquariumBlock> AQUARIUM = PiCoreRegistries.block("aquarium", () -> new AquariumBlock(BlockBehaviour.Properties.of()
          .mapColor(MapColor.NONE).strength(0.8F, 2.0F).sound(SoundType.GLASS).noOcclusion().isValidSpawn((s, l, p, t) -> false)
          .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<AquariumTile>> TILE = PiTileEntityTypes.VANILLA_TILES.register("aquarium",
          () -> BlockEntityType.Builder.of(AquariumTile::new, AQUARIUM.getBlock()).build(null));

    public static final RegistryObject<Item> CRUSTACEAN_BUCKET = PiCoreRegistries.ITEMS.register("crustacean_bucket",
          () -> new CrustaceanBucketItem(new Item.Properties().stacksTo(1)));

    /** 1.0.5-beta (lot 25) : interface de composition du bassin ({@link AquariumMenu}, ecran {@code J:client/farm/aquarium/AquariumScreen}). */
    public static final RegistryObject<MenuType<AquariumMenu>> MENU = PiCoreRegistries.MENUS.register("aquarium", () -> IForgeMenuType.create(AquariumMenu::client));
}

package fr.pioneers.logistics.transport.trash;

import fr.pioneers.core.block.DescribedBlockItem;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.core.registries.PiTileEntityTypes;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.0.5-beta (lot 22, retour 1 de Guillaume : « une poubelle pour tous types de ressources, sauf la data ») : registres de la POUBELLE
 * (bloc, objet, tuile, menu). Ressources : {@code T:core/p105_lot22.py} (source unique : modele, textures, etat, objet, recette, butin,
 * outil) ; langue : {@code T:core/p105_lot22.py TEXTS} ; guide : {@code T:core/guide_lot22.py}.
 */
public final class PiTrash {

    private PiTrash() {
    }

    /** Charge les registres (appele par {@code Pioneers}). */
    public static void init() {
    }

    public static final BlockEntry<TrashCanBlock> TRASH_CAN = PiCoreRegistries.block("trash_can", () -> new TrashCanBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5F, 6.0F).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()),
          b -> new DescribedBlockItem(b, new Item.Properties(), "description.pioneers.trash_can"));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<TrashCanTile>> TILE = PiTileEntityTypes.VANILLA_TILES.register("trash_can",
          () -> BlockEntityType.Builder.of(TrashCanTile::new, TRASH_CAN.getBlock()).build(null));

    public static final RegistryObject<MenuType<TrashCanMenu>> MENU = PiCoreRegistries.MENUS.register("trash_can",
          () -> IForgeMenuType.create(TrashCanMenu::client));
}

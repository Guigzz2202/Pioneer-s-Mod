package fr.pioneers.paperwork;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.registries.PiTileEntityTypes;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.0.5-beta (lot 16) : registres des bacs a papiers : le bac a lettres et le bac d'archives (blocs, objets, une tuile, un menu). Ressources :
 * {@code T:core/p105_lot16_bins.py} (source unique : modeles, textures, recettes, butins, outils).
 */
public final class PiPaperwork {

    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F, 3F).sound(SoundType.WOOD).noOcclusion().ignitedByLava();
    }

    public static final BlockEntry<PaperBinBlock> LETTER_BIN = PiCoreRegistries.block(PaperBinKind.LETTERS.id,
          () -> new PaperBinBlock(PaperBinKind.LETTERS, wood()));
    public static final BlockEntry<PaperBinBlock> ARCHIVE_BIN = PiCoreRegistries.block(PaperBinKind.ARCHIVES.id,
          () -> new PaperBinBlock(PaperBinKind.ARCHIVES, wood()));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<PaperBinTile>> TILE = PiTileEntityTypes.VANILLA_TILES.register("paper_bin",
          () -> BlockEntityType.Builder.of(PaperBinTile::new, LETTER_BIN.getBlock(), ARCHIVE_BIN.getBlock()).build(null));

    public static final RegistryObject<MenuType<PaperBinMenu>> MENU = PiCoreRegistries.MENUS.register("paper_bin",
          () -> IForgeMenuType.create(PaperBinMenu::client));

    private PiPaperwork() {
    }

    /** Charge les registres et le traitement reseau (appele par {@code PiMail.init}). */
    public static void init() {
        PaperBinMenu.init();
    }
}

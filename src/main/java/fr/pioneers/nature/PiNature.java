package fr.pioneers.nature;

import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.farm.PiFarm;
import fr.pioneers.registries.PiTileEntityTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * Age du vivant, cote monde : saignees a resine (bois, bronze ; sans energie). Les cultures (colza, chanvre, mais) sont passees a
 * l'Agriculture 2.0 ({@code PiFarm}) en 1.3.
 * Les objets de resine, de latex et les autres matieres sont au catalogue de l'industrie ({@code PiIndustry.ITEMS}).
 */
public final class PiNature {

    private PiNature() {
    }

    public static final TagKey<Block> RESIN_LOGS = BlockTags.create(Pioneers.rl("resin_logs"));
    public static final TagKey<Block> LATEX_LOGS = BlockTags.create(Pioneers.rl("latex_logs"));

    public static void init() {
    }

    // Saignees
    public static final BlockEntry<ResinTapBlock> WOODEN_RESIN_TAP = PiCoreRegistries.block("wooden_resin_tap", () -> new ResinTapBlock(false,
          BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion()));
    public static final BlockEntry<ResinTapBlock> BRONZE_RESIN_TAP = PiCoreRegistries.block("bronze_resin_tap", () -> new ResinTapBlock(true,
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0F, 6F).sound(SoundType.METAL).noOcclusion()));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<ResinTapBlock.Tile>> RESIN_TAP_TILE = PiTileEntityTypes.VANILLA_TILES.register("resin_tap",
          () -> BlockEntityType.Builder.of(ResinTapBlock.Tile::new, WOODEN_RESIN_TAP.getBlock(), BRONZE_RESIN_TAP.getBlock()).build(null));

    /** Au setup commun (file principale) : rien depuis 1.3 (les cultures sont passees a {@code PiFarm}). */
    public static void setup() {
    }
}

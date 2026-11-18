package fr.pioneers.society.clan.war;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.core.registries.PiTileEntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.RegistryObject;

/** 1.9 (MAJ RP, bloc 4) : blocs de la guerre (Drapeau de siege). */
public final class WarBlocks {

    public static final BlockEntry<SiegeFlagBlock> SIEGE_FLAG = PiCoreRegistries.block("siege_flag", () -> new SiegeFlagBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(-1.0F, 3_600_000.0F).sound(SoundType.WOOD).noOcclusion()
                .pushReaction(PushReaction.BLOCK)), b -> new SiegeFlagItem(b, new Item.Properties().stacksTo(16)));

    public static final RegistryObject<BlockEntityType<SiegeFlagTile>> SIEGE_FLAG_TILE = PiTileEntityTypes.VANILLA_TILES.register("siege_flag",
          () -> BlockEntityType.Builder.of(SiegeFlagTile::new, SIEGE_FLAG.getBlock()).build(null));

    private WarBlocks() {
    }

    static void init() {
    }
}

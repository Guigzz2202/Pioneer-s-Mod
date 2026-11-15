package fr.pioneers.core.machine;

import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.core.registries.PiTileEntityTypes;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/** Enregistrement du bloc fantome des grandes machines du socle ({@code pioneers:machine_bounds}). */
public final class MachineBounds {

    private MachineBounds() {
    }

    public static void init() {
    }

    public static final RegistryObject<BoundingBlock> BLOCK = PiCoreRegistries.BLOCKS.register("machine_bounds",
          () -> new BoundingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 9.0F).sound(SoundType.METAL).noOcclusion()
                .noLootTable().dynamicShape()));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<BoundingTile>> TILE = PiTileEntityTypes.VANILLA_TILES.register("machine_bounds",
          () -> BlockEntityType.Builder.of(BoundingTile::new, BLOCK.get()).build(null));
}

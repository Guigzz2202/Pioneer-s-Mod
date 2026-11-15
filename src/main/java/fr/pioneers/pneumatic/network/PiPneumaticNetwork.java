package fr.pioneers.pneumatic.network;

import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.registries.PiEntityTypes;
import fr.pioneers.registries.PiTileEntityTypes;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * Air comprime en reseau : hypertubes et stations (transport du joueur par capsules). 1.0.4-beta (lot 27, demande de Guillaume) : caisson
 * pneumatique, vanne de caverne, turbine de detente et ancrage d'hypertube retires (remaps {@code PiRemaps.P104_LOT27}) ; un hypertube garde
 * toujours une hitbox cassable ({@code client/pneumatic/HypertubeClient}).
 */
public final class PiPneumaticNetwork {

    private PiPneumaticNetwork() {
    }

    /** Appele depuis le constructeur du mod : charge les registres et le canal reseau. */
    public static void init() {
        PneumaticNet.register();
    }

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 6F).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    /** 0.20.38 : hypertube (id inchange) : objet {@link HypertubeItem}, pose d'un point A a un point B. */
    public static final BlockEntry<PneumaticTubeBlock> TUBE = PiCoreRegistries.block("pneumatic_tube", () -> new PneumaticTubeBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.8F, 3F).sound(SoundType.GLASS).noOcclusion()),
          b -> new HypertubeItem(b, new Item.Properties(), "description.pioneers.pneumatic_tube"));
    public static final BlockEntry<TubeStationBlock> STATION = PiCoreRegistries.block("pneumatic_tube_station", () -> new TubeStationBlock(metal()
          .noOcclusion()));
    /** Capsule de voyageur (cabine vitree qui file dans les tubes, sans sauvegarde : voir {@link TubeTravel}). */
    public static final RegistryObject<EntityType<TubeCapsuleEntity>> TUBE_CAPSULE = PiEntityTypes.ENTITY_TYPES.register("tube_capsule",
          () -> EntityType.Builder.<TubeCapsuleEntity>of(TubeCapsuleEntity::new, MobCategory.MISC)
                .sized(0.9F, 0.9F)
                .clientTrackingRange(8)
                .updateInterval(20)
                .fireImmune()
                .noSave()
                .noSummon()
                .build(Pioneers.rl("tube_capsule").toString()));
    /** 0.20.39 : vue d'un troncon d'hypertube courbe (jamais sauvegardee : recreee par {@link HypertubeWorld}). */
    public static final RegistryObject<EntityType<HypertubeEntity>> HYPERTUBE = PiEntityTypes.ENTITY_TYPES.register("hypertube",
          () -> EntityType.Builder.<HypertubeEntity>of(HypertubeEntity::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .clientTrackingRange(16)
                .updateInterval(Integer.MAX_VALUE)
                .fireImmune()
                .noSave()
                .noSummon()
                .build(Pioneers.rl("hypertube").toString()));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<TubeStationTile>> STATION_TILE = PiTileEntityTypes.VANILLA_TILES.register("pneumatic_tube_station",
          () -> BlockEntityType.Builder.of(TubeStationTile::new, STATION.getBlock()).build(null));

    /** Objets de l'onglet Logistique, dans l'ordre. */
    public static List<Supplier<? extends ItemLike>> creative() {
        return List.of(TUBE::getBlock, STATION::getBlock);
    }
}

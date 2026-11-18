package fr.pioneers.logistics.rail;

import fr.pioneers.Pioneers;
import fr.pioneers.core.machine.TieredMachineBlock;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.logistics.rail.CarMenu;
import fr.pioneers.logistics.rail.block.ConductorBlock;
import fr.pioneers.logistics.rail.block.ItemLoaderTile;
import fr.pioneers.logistics.rail.block.PostKind;
import fr.pioneers.logistics.rail.block.RailPostBlock;
import fr.pioneers.logistics.rail.block.RailPostTile;
import fr.pioneers.logistics.rail.block.RailSignalBlock;
import fr.pioneers.logistics.rail.block.StationBlock;
import fr.pioneers.logistics.rail.block.StationTile;
import fr.pioneers.logistics.rail.block.SubstationBlock;
import fr.pioneers.logistics.rail.block.SubstationTile;
import fr.pioneers.logistics.rail.block.TrackSideItem;
import fr.pioneers.logistics.rail.block.WaterCraneBlock;
import fr.pioneers.logistics.rail.block.WaterCraneTile;
import fr.pioneers.logistics.rail.block.Works;
import fr.pioneers.logistics.rail.net.Electrification;
import fr.pioneers.logistics.rail.net.Piece;
import fr.pioneers.logistics.rail.net.RailNetwork;
import fr.pioneers.logistics.rail.track.BufferStopBlock;
import fr.pioneers.logistics.rail.track.FunctionalTrackBlock;
import fr.pioneers.logistics.rail.track.RailTrackBlock;
import fr.pioneers.logistics.rail.track.TrackQuality;
import fr.pioneers.logistics.rail.train.CarType;
import fr.pioneers.logistics.rail.train.RailCarEntity;
import fr.pioneers.logistics.rail.train.RailCarItem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;
import fr.pioneers.core.registries.PiEntityTypes;
import fr.pioneers.core.registries.PiTileEntityTypes;

/**
 * 0.20.0 : le chemin de fer miniature : voies (cinq qualites), grands virages, butoir, rails fonctionnels, signaux, panneau de gare, postes de
 * quai, vehicules et leur entite.
 */
public final class PiRail {

    private PiRail() {
    }

    public static void init() {
    }

    // ------------------------------------------------------------------ voies

    public static final Map<TrackQuality, BlockEntry<RailTrackBlock>> TRACKS = new EnumMap<>(TrackQuality.class);

    static {
        for (TrackQuality q : TrackQuality.values()) {
            boolean wood = q == TrackQuality.WOOD;
            BlockEntry<RailTrackBlock> track = PiCoreRegistries.block(q.blockId(), () -> new RailTrackBlock(q, BlockBehaviour.Properties.of()
                  .mapColor(wood ? MapColor.WOOD : MapColor.METAL).strength(0.7F, 3.0F).sound(wood ? SoundType.WOOD : SoundType.METAL).noOcclusion()
                  .noCollission()));
            TRACKS.put(q, track);
        }
    }

    private static BlockBehaviour.Properties steel() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.9F, 4.0F).sound(SoundType.METAL).noOcclusion();
    }

    public static final BlockEntry<BufferStopBlock> BUFFER_STOP = PiCoreRegistries.block("buffer_stop", () -> new BufferStopBlock(steel()));
    public static final BlockEntry<FunctionalTrackBlock> BRAKE_TRACK = PiCoreRegistries.block("brake_track",
          () -> new FunctionalTrackBlock(Piece.Kind.BRAKE, steel().noCollission()));
    public static final BlockEntry<FunctionalTrackBlock> BOOSTER_TRACK = PiCoreRegistries.block("booster_track",
          () -> new FunctionalTrackBlock(Piece.Kind.BOOSTER, steel().noCollission().lightLevel(s -> 4)));
    public static final BlockEntry<FunctionalTrackBlock> DETECTOR_TRACK = PiCoreRegistries.block("detector_track",
          () -> new FunctionalTrackBlock(Piece.Kind.DETECTOR, steel().noCollission()));
    //0.20.5
    public static final BlockEntry<FunctionalTrackBlock> WEIGHBRIDGE = PiCoreRegistries.block("weighbridge",
          () -> new FunctionalTrackBlock(Piece.Kind.WEIGHBRIDGE, steel().noCollission()));
    public static final BlockEntry<FunctionalTrackBlock> UNCOUPLER_TRACK = PiCoreRegistries.block("uncoupler_track",
          () -> new FunctionalTrackBlock(Piece.Kind.UNCOUPLER, steel().noCollission()));

    //1.9.2 (retours de Guillaume) : plaque tournante, pont transbordeur et abri de quai supprimes (remaps : PiRemaps)

    //0.20.6 : passage a niveau et ouvrages
    public static final BlockEntry<FunctionalTrackBlock> LEVEL_CROSSING = PiCoreRegistries.block("level_crossing",
          () -> new FunctionalTrackBlock(Piece.Kind.LEVEL_CROSSING, steel().noCollission().sound(SoundType.WOOD)));
    public static final BlockEntry<Works.Barrier> CROSSING_BARRIER = PiCoreRegistries.block("crossing_barrier",
          () -> new Works.Barrier(steel().lightLevel(s -> s.getValue(Works.CLOSED) ? 6 : 0)));
    public static final BlockEntry<Works.Pillar> VIADUCT_PILLAR = PiCoreRegistries.block("viaduct_pillar", () -> new Works.Pillar(stone()));
    public static final BlockEntry<Works.Railing> RAILING = PiCoreRegistries.block("railing", () -> new Works.Railing(steel()));
    public static final BlockEntry<Works.Portal> TUNNEL_PORTAL = PiCoreRegistries.block("tunnel_portal", () -> new Works.Portal(stone()));
    public static final BlockEntry<Works.PlatformSlab> PLATFORM_SLAB = PiCoreRegistries.block("platform_slab", () -> new Works.PlatformSlab(stone()));
    public static final BlockEntry<Works.Lamp> PLATFORM_LAMP = PiCoreRegistries.block("platform_lamp", () -> new Works.Lamp(steel().lightLevel(s -> 15)));

    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0F, 8.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion();
    }

    // ------------------------------------------------------------------ signaux et gares

    public static final BlockEntry<RailSignalBlock> BLOCK_SIGNAL = PiCoreRegistries.block("block_signal",
          () -> new RailSignalBlock(RailNetwork.SignalType.BLOCK, steel().lightLevel(s -> 5)), b -> side(b, "block_signal", true));
    public static final BlockEntry<RailSignalBlock> CHAIN_SIGNAL = PiCoreRegistries.block("chain_signal",
          () -> new RailSignalBlock(RailNetwork.SignalType.CHAIN, steel().lightLevel(s -> 5)), b -> side(b, "chain_signal", true));
    public static final BlockEntry<StationBlock> STATION = PiCoreRegistries.block("train_station", () -> new StationBlock(steel().lightLevel(s -> 7)),
          b -> side(b, "train_station", true));

    /** 0.20.1 : objet pose en cliquant sur la voie (sens du regard). */
    private static TrackSideItem side(Block block, String id, boolean directional) {
        return new TrackSideItem(block, new Item.Properties(), "description.pioneers." + id, directional);
    }

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<StationTile>> STATION_TILE = PiTileEntityTypes.VANILLA_TILES.register("train_station",
          () -> BlockEntityType.Builder.of(StationTile::new, STATION.getBlock()).build(null));

    // ------------------------------------------------------------------ postes de quai

    private static BlockBehaviour.Properties post() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
              .noOcclusion();
    }

    /** 0.20.2 : postes par sorte (4 tiers ou un seul) ; les quais a objets d'origine sont {@code POSTS.get(ITEM)}. */
    public static final Map<PostKind, List<BlockEntry<RailPostBlock>>> POSTS = new EnumMap<>(PostKind.class);

    static {
        for (PostKind kind : PostKind.values()) {
            List<BlockEntry<RailPostBlock>> list = new ArrayList<>();
            for (int t = 0; t < (kind.tiered ? 4 : 1); t++) {
                int tier = t;
                String id = kind.id(t);
                boolean items = kind.items();
                list.add(PiCoreRegistries.block(id, () -> new RailPostBlock(kind, tier, post(), items ? () -> PiRail.ITEM_LOADER_TILE.get()
                      : () -> PiRail.POST_TILE.get(), items ? ItemLoaderTile::new : RailPostTile::new),
                      b -> new TrackSideItem(b, new Item.Properties(), "description.pioneers." + id, false, kind == PostKind.PIT)));
            }
            POSTS.put(kind, list);
        }
    }

    /** Quais a objets d'origine (0.20.0). */
    public static final List<BlockEntry<RailPostBlock>> ITEM_LOADERS = POSTS.get(PostKind.ITEM);

    public static final BlockEntry<WaterCraneBlock> WATER_CRANE = PiCoreRegistries.block("water_crane", () -> new WaterCraneBlock(post(),
          () -> PiRail.WATER_CRANE_TILE.get(), WaterCraneTile::new), b -> side(b, "water_crane", false));

    //0.20.2 : electrification
    public static final BlockEntry<ConductorBlock> CATENARY_POLE = PiCoreRegistries.block("catenary_pole",
          () -> new ConductorBlock(Electrification.Kind.POLE, post()), b -> side(b, "catenary_pole", false));
    public static final BlockEntry<ConductorBlock> THIRD_RAIL = PiCoreRegistries.block("third_rail",
          () -> new ConductorBlock(Electrification.Kind.THIRD_RAIL, post()), b -> side(b, "third_rail", false));
    public static final List<BlockEntry<SubstationBlock>> SUBSTATIONS = new ArrayList<>();

    static {
        for (int t = 0; t < 4; t++) {
            int tier = t;
            SUBSTATIONS.add(PiCoreRegistries.block(TieredMachineBlock.TIERS[t] + "_substation", () -> new SubstationBlock(tier, post(),
                  () -> PiRail.SUBSTATION_TILE.get(), SubstationTile::new)));
        }
    }

    /** Tous les blocs d'une liste de postes. */
    private static Block[] blocks(boolean items) {
        List<Block> out = new ArrayList<>();
        for (PostKind k : PostKind.values()) {
            if (k.items() == items) {
                POSTS.get(k).forEach(e -> out.add(e.getBlock()));
            }
        }
        return out.toArray(Block[]::new);
    }

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<ItemLoaderTile>> ITEM_LOADER_TILE = PiTileEntityTypes.VANILLA_TILES.register("item_loader",
          () -> BlockEntityType.Builder.of(ItemLoaderTile::new, blocks(true)).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<RailPostTile>> POST_TILE = PiTileEntityTypes.VANILLA_TILES.register("rail_post",
          () -> BlockEntityType.Builder.of(RailPostTile::new, blocks(false)).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<SubstationTile>> SUBSTATION_TILE = PiTileEntityTypes.VANILLA_TILES.register("substation",
          () -> BlockEntityType.Builder.of(SubstationTile::new, SUBSTATIONS.stream().map(BlockEntry::getBlock).toArray(Block[]::new)).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<WaterCraneTile>> WATER_CRANE_TILE = PiTileEntityTypes.VANILLA_TILES.register("water_crane",
          () -> BlockEntityType.Builder.of(WaterCraneTile::new, WATER_CRANE.getBlock()).build(null));

    // ------------------------------------------------------------------ materiaux de la voie

    /** Profiles de rail (acier, inox), traverses traitees (bois) et en beton. */
    public static final RegistryObject<Item> RAIL_PROFILE = PiCoreRegistries.ITEMS.register("rail_profile", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> STAINLESS_RAIL_PROFILE = PiCoreRegistries.ITEMS.register("stainless_rail_profile",
          () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> TREATED_TIE = PiCoreRegistries.ITEMS.register("treated_tie", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_TIE = PiCoreRegistries.ITEMS.register("concrete_tie", () -> new Item(new Item.Properties()));

    // ------------------------------------------------------------------ vehicules

    public static final Map<CarType, List<RegistryObject<Item>>> CARS = new EnumMap<>(CarType.class);

    static {
        for (CarType type : CarType.values()) {
            List<RegistryObject<Item>> tiers = new ArrayList<>();
            for (int t = 0; t < type.tiers; t++) {
                int tier = t;
                tiers.add(PiCoreRegistries.ITEMS.register(type.id(t), () -> new RailCarItem(type, tier, new Item.Properties().stacksTo(8))));
            }
            CARS.put(type, tiers);
        }
    }

    @Nullable
    public static Item car(CarType type, int tier) {
        List<RegistryObject<Item>> list = CARS.get(type);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(Math.max(0, Math.min(list.size() - 1, tier))).get();
    }

    public static final RegistryObject<EntityType<RailCarEntity>> CAR_ENTITY = PiEntityTypes.ENTITY_TYPES.register("rail_car",
          () -> EntityType.Builder.<RailCarEntity>of(RailCarEntity::new, MobCategory.MISC)
                .sized(0.875F, 0.875F)
                .clientTrackingRange(10)
                .updateInterval(1)
                .fireImmune()
                .noSave()
                .build(Pioneers.rl("rail_car").toString()));

    public static final RegistryObject<MenuType<CarMenu>> CAR_MENU = PiCoreRegistries.MENUS.register("rail_car",
          () -> IForgeMenuType.create(CarMenu::client));

    /** Ordre de l'onglet creatif. */
    public static List<Item> creative() {
        List<Item> out = new ArrayList<>();
        out.add(RAIL_PROFILE.get());
        out.add(STAINLESS_RAIL_PROFILE.get());
        out.add(TREATED_TIE.get());
        out.add(CONCRETE_TIE.get());
        for (TrackQuality q : TrackQuality.values()) {
            out.add(TRACKS.get(q).asItem());
        }
        out.add(BUFFER_STOP.asItem());
        out.add(BRAKE_TRACK.asItem());
        out.add(BOOSTER_TRACK.asItem());
        out.add(DETECTOR_TRACK.asItem());
        out.add(WEIGHBRIDGE.asItem());
        out.add(UNCOUPLER_TRACK.asItem());
        out.add(LEVEL_CROSSING.asItem());
        out.add(CROSSING_BARRIER.asItem());
        out.add(VIADUCT_PILLAR.asItem());
        out.add(RAILING.asItem());
        out.add(TUNNEL_PORTAL.asItem());
        out.add(PLATFORM_SLAB.asItem());
        out.add(PLATFORM_LAMP.asItem());
        out.add(BLOCK_SIGNAL.asItem());
        out.add(CHAIN_SIGNAL.asItem());
        out.add(STATION.asItem());
        for (PostKind k : PostKind.values()) {
            POSTS.get(k).forEach(e -> out.add(e.asItem()));
            if (k == PostKind.COALING) {
                out.add(WATER_CRANE.asItem());
            }
        }
        out.add(CATENARY_POLE.asItem());
        out.add(THIRD_RAIL.asItem());
        SUBSTATIONS.forEach(e -> out.add(e.asItem()));
        for (CarType type : CarType.values()) {
            CARS.get(type).forEach(r -> out.add(r.get()));
        }
        return out;
    }
}

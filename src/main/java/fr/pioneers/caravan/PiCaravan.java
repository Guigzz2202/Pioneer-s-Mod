package fr.pioneers.caravan;

import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.core.block.DescribedBlockItem;
import fr.pioneers.registries.PiEntityTypes;
import fr.pioneers.registries.PiTileEntityTypes;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 10, Guillaume : " automatiser le transport d'item via cheval / ane / Dromadaire (Chameaux + Dromadaire + retexture des
 * modeles) (Systeme de caravane avec chevaux, anes et chameaux) ") : registre des CARAVANES : dromadaire (entite, oeuf), bat, feuille de
 * route, relais de caravane (bloc, tuile), menus (sacoches, feuille de route), capacite {@link CaravanData} sur les betes de bat (cheval,
 * ane, mulet, chameau vanilla, dromadaire), ticket de chunks des caravanes en route, apparitions. Logique : {@link Caravans} ; regles :
 * {@link CaravanRules} ; donnees : {@code tools/gc/caravan/caravan.py}.
 */
public final class PiCaravan {

    private PiCaravan() {
    }

    public static void init() {
    }

    public static final Capability<CaravanData> CAP = CapabilityManager.get(new CapabilityToken<>() {
    });

    /** Ticket de chunks autour de la tete d'une caravane en route (sans fin : pose et retire par {@link CaravanTickets}). */
    public static final TicketType<ChunkPos> TICKET = TicketType.create("pioneers_caravan", Comparator.comparingLong(ChunkPos::toLong));

    public static final TagKey<Block> SPAWNABLE_ON = TagKey.create(Registries.BLOCK, Pioneers.rl("camels_spawnable_on"));

    public static final RegistryObject<EntityType<DromedaryEntity>> DROMEDARY = PiEntityTypes.ENTITY_TYPES.register("dromedary",
          () -> EntityType.Builder.<DromedaryEntity>of(DromedaryEntity::new, MobCategory.CREATURE).sized(1.55F, 2.375F).clientTrackingRange(10)
                .build("dromedary"));
    public static final RegistryObject<Item> DROMEDARY_EGG = PiCoreRegistries.ITEMS.register("dromedary_spawn_egg",
          () -> new ForgeSpawnEggItem(DROMEDARY, 0xDCB880, 0x8E6A42, new Item.Properties()));
    public static final RegistryObject<Item> PACK_SADDLE = PiCoreRegistries.ITEMS.register("pack_saddle",
          () -> new PackSaddleItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ROUTE_SHEET = PiCoreRegistries.ITEMS.register("caravan_route",
          () -> new RouteSheetItem(new Item.Properties().stacksTo(1)));

    public static final BlockEntry<RelayBlock> RELAY = PiCoreRegistries.block("caravan_relay",
          () -> new RelayBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion()),
          b -> new DescribedBlockItem(b, new Item.Properties(), "description.pioneers.caravan_relay"));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<RelayTile>> RELAY_TILE = PiTileEntityTypes.VANILLA_TILES.register("caravan_relay",
          () -> BlockEntityType.Builder.of(RelayTile::new, RELAY.getBlock()).build(null));

    public static final RegistryObject<MenuType<PackMenu>> PACK_MENU = PiCoreRegistries.MENUS.register("caravan_pack",
          () -> IForgeMenuType.create(PackMenu::client));
    public static final RegistryObject<MenuType<RouteMenu>> ROUTE_MENU = PiCoreRegistries.MENUS.register("caravan_route",
          () -> IForgeMenuType.create(RouteMenu::client));

    /** Betes de bat : cheval, ane, mulet, chameau (et dromadaire) ; pas le lama (caravanes vanilla), ni les chevaux squelette et zombie. */
    public static boolean carrier(@Nullable Entity e) {
        return e instanceof Horse || e instanceof Donkey || e instanceof Mule || e instanceof Camel;
    }

    public static boolean camel(Entity e) {
        return e instanceof Camel;
    }

    @Nullable
    public static CaravanData get(@Nullable Entity e) {
        return e == null ? null : e.getCapability(CAP).resolve().orElse(null);
    }

    /** Style du bat : 0 sacoches (cheval), 1 paniers (ane, mulet), 2 caisses (chameau), 3 caisses etroites (dromadaire). */
    public static int style(Entity e) {
        return e instanceof DromedaryEntity ? 3 : e instanceof Camel ? 2 : e instanceof Donkey || e instanceof Mule ? 1 : 0;
    }

    /** Apparition des chameaux et dromadaires : sol de desert, de badlands ou de steppe, assez de lumiere. */
    public static boolean spawnRule(EntityType<? extends AbstractHorse> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
          RandomSource random) {
        return level.getBlockState(pos.below()).is(SPAWNABLE_ON) && level.getRawBrightness(pos, 0) > 8;
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        @SubscribeEvent
        public static void register(RegisterCapabilitiesEvent event) {
            event.register(CaravanData.class);
        }

        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent event) {
            event.put(DROMEDARY.get(), Camel.createAttributes().add(Attributes.MOVEMENT_SPEED, 0.1F).add(Attributes.MAX_HEALTH, 30.0).build());
        }

        @SubscribeEvent
        public static void spawns(SpawnPlacementRegisterEvent event) {
            event.register(DROMEDARY.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PiCaravan::spawnRule,
                  SpawnPlacementRegisterEvent.Operation.REPLACE);
            event.register(EntityType.CAMEL, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PiCaravan::spawnRule,
                  SpawnPlacementRegisterEvent.Operation.REPLACE);
        }
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID)
    public static final class ForgeBus {

        private ForgeBus() {
        }

        @SubscribeEvent
        public static void attach(AttachCapabilitiesEvent<Entity> event) {
            if (carrier(event.getObject())) {
                event.addCapability(Pioneers.rl("caravan"), new Provider());
            }
        }
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag> {

        private final CaravanData data = new CaravanData();
        private final LazyOptional<CaravanData> opt = LazyOptional.of(() -> data);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == CAP ? opt.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            data.deserializeNBT(tag);
        }
    }

    static ResourceLocation id(String s) {
        return Pioneers.rl(s);
    }
}

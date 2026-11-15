package fr.pioneers.wild;

import fr.pioneers.Pioneers;
import fr.pioneers.core.block.DescribedBlockItem;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.registries.PiEntityTypes;
import fr.pioneers.registries.PiSounds;
import fr.pioneers.registries.PiTileEntityTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.0.5-beta (lot 7, specification « Le monde vivant » §4 « Forets, plaines et partout », §7, §9, §11 n° 9, 12, 13, 14) : registre des
 * PREDATEURS ET DE LA PETITE FAUNE : six especes ({@code pioneers:gray_wolf} {@link GrayWolf}, {@code bear} {@link Bear},
 * {@code woodpecker} {@link Woodpecker}, {@code rat} {@link Rat}, {@code pigeon} {@link Pigeon}, {@code snake} {@link Snake} ; sortes 5 a
 * 10, a la suite du gibier du lot 6 : meme base {@link WildAnimal}), leurs oeufs, les viandes nouvelles (ours, pigeon et leurs cuissons),
 * les sons (sons vanilla assembles « wild.* », ranges avec ceux du gibier dans {@link PiWild#SOUNDS}), les tags (proies du loup, objets qui
 * l'effraient, appats du piege), et deux blocs : le PIEGE A RAT ({@link RatTrapBlock}) et le NICHOIR A OISEAUX ({@link BirdHouseBlock}, 11
 * bois, tuile {@link BirdHouseTile}). Les especes entrent dans le socle de faune par {@link WildFauna7}. Donnees : tools/gc/wild/wild7.py
 * ({@link GeneratedWild7}), blocs : tools/gc/wild/wild7_blocks.py.
 */
public final class PiWild7 {

    private PiWild7() {
    }

    public static void init() {
    }

    public static final List<RegistryObject<? extends EntityType<? extends WildAnimal>>> TYPES = new ArrayList<>();
    public static final List<RegistryObject<Item>> EGGS = new ArrayList<>();
    public static final List<RegistryObject<Item>> ITEMS = new ArrayList<>();

    /** Proies du loup (betail vanilla ; les races du mod sont reconnues par leur classe), objets qui l'effraient, appats du piege a rat. */
    public static final TagKey<EntityType<?>> WOLF_PREY = TagKey.create(Registries.ENTITY_TYPE, Pioneers.rl("wolf_prey"));
    public static final TagKey<Item> WOLF_REPELLENTS = TagKey.create(Registries.ITEM, Pioneers.rl("wolf_repellents"));
    public static final TagKey<Item> RAT_BAIT = TagKey.create(Registries.ITEM, Pioneers.rl("rat_bait"));

    public static final RegistryObject<EntityType<GrayWolf>> GRAY_WOLF = type(Wild7Rules.WOLF, GrayWolf::new, 10);
    public static final RegistryObject<EntityType<Bear>> BEAR = type(Wild7Rules.BEAR, Bear::new, 10);
    public static final RegistryObject<EntityType<Woodpecker>> WOODPECKER = type(Wild7Rules.WOODPECKER, Woodpecker::new, 8);
    public static final RegistryObject<EntityType<Rat>> RAT = type(Wild7Rules.RAT, Rat::new, 8);
    public static final RegistryObject<EntityType<Pigeon>> PIGEON = type(Wild7Rules.PIGEON, Pigeon::new, 8);
    public static final RegistryObject<EntityType<Snake>> SNAKE = type(Wild7Rules.SNAKE, Snake::new, 8);

    /** Piege a rat (un seul bloc) ; nichoirs a oiseaux, un par bois des meubles. */
    public static final BlockEntry<RatTrapBlock> RAT_TRAP = PiCoreRegistries.block("rat_trap", () -> new RatTrapBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava()),
          b -> new DescribedBlockItem(b, new Item.Properties(), "description.pioneers.rat_trap"));
    public static final List<BlockEntry<BirdHouseBlock>> BIRD_HOUSES = new ArrayList<>();

    static {
        for (GeneratedWild.Food f : GeneratedWild7.ITEMS) {
            RegistryObject<Item> ro = PiCoreRegistries.ITEMS.register(f.id(), () -> new Item(new Item.Properties().food(food(f))));
            ITEMS.add(ro);
            PiWild.ITEMS.put(f.id(), ro);
        }
        String[][] sounds = {{"ambient", "hurt", "death", "howl", "growl"}, {"ambient", "hurt", "death", "roar"},
                             {"ambient", "hurt", "death", "drum"}, {"ambient", "hurt", "death", "steal"}, {"ambient", "hurt", "death", "flap"},
                             {"ambient", "hurt", "death", "bite"}};
        for (int k = 0; k < GeneratedWild7.SPECIES.length; k++) {
            String id = GeneratedWild7.SPECIES[k].id();
            for (String s : sounds[k]) {
                String name = "wild." + id + "." + s;
                PiWild.SOUNDS.put(id + "." + s, PiSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Pioneers.rl(name))));
            }
            //nourriture de l'elevage : index = sorte (a la suite des 5 especes du gibier)
            PiWild.FOOD.add(TagKey.create(Registries.ITEM, Pioneers.rl("wild_food/" + id)));
        }
        for (String w : fr.pioneers.home.HomeWoods.ALL) {
            BIRD_HOUSES.add(PiCoreRegistries.block(w + "_bird_house", () -> new BirdHouseBlock(houseProps(w)),
                  b -> new DescribedBlockItem(b, new Item.Properties(), "description.pioneers.bird_house")));
        }
    }

    /** Case dans un claim de clan ? */
    public static boolean claimed(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return fr.pioneers.clan.ClaimIndex.owner(fr.pioneers.clan.ClanApi.dim(level), net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4,
              pos.getZ() >> 4)) != null;
    }

    private static BlockBehaviour.Properties houseProps(String wood) {
        boolean nether = wood.equals("crimson") || wood.equals("warped");
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F, 2.0F)
              .sound(wood.equals("bamboo") ? SoundType.BAMBOO_WOOD : wood.equals("cherry") ? SoundType.CHERRY_WOOD : nether ? SoundType.NETHER_WOOD
                    : SoundType.WOOD).noOcclusion().randomTicks();
        return nether ? p : p.ignitedByLava();
    }

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<BirdHouseTile>> BIRD_HOUSE_TILE = PiTileEntityTypes.VANILLA_TILES.register("bird_house",
          () -> BlockEntityType.Builder.of(BirdHouseTile::new, BIRD_HOUSES.stream().map(BlockEntry::getBlock).toArray(Block[]::new)).build(null));

    private static <T extends WildAnimal> RegistryObject<EntityType<T>> type(int kind, EntityType.EntityFactory<T> factory, int range) {
        GeneratedWild.Species s = WildAnimal.speciesOf(kind);
        RegistryObject<EntityType<T>> ro = PiEntityTypes.ENTITY_TYPES.register(s.id(), () -> EntityType.Builder.of(factory, MobCategory.CREATURE)
              .sized(s.width(), s.height()).clientTrackingRange(range).build(s.id()));
        TYPES.add(ro);
        EGGS.add(PiCoreRegistries.ITEMS.register(s.id() + "_spawn_egg", () -> new ForgeSpawnEggItem(ro, s.egg1(), s.egg2(), new Item.Properties())));
        return ro;
    }

    private static net.minecraft.world.food.FoodProperties food(GeneratedWild.Food f) {
        net.minecraft.world.food.FoodProperties.Builder b = new net.minecraft.world.food.FoodProperties.Builder().nutrition(f.nutrition())
              .saturationMod(f.saturation()).meat();
        if (f.hunger()) {
            b.effect(() -> new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HUNGER, 600, 0), 0.3F);
        }
        return b.build();
    }

    /** Sol des rats et des pigeons : toute face solide, hors de l'eau ; les pigeons a la lumiere. */
    public static boolean checkUrban(boolean rat, net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos) {
        net.minecraft.core.BlockPos below = pos.below();
        return level.getFluidState(pos).isEmpty() && level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)
               && (rat || level.getRawBrightness(pos, 0) > 8);
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Wild7ModBus {

        private Wild7ModBus() {
        }

        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent event) {
            for (int k = 0; k < TYPES.size(); k++) {
                event.put(TYPES.get(k).get(), WildAnimal.createAttributes(GeneratedWild7.FIRST + k).build());
            }
        }

        @SubscribeEvent
        @SuppressWarnings("unchecked")
        public static void spawns(SpawnPlacementRegisterEvent event) {
            for (RegistryObject<? extends EntityType<? extends WildAnimal>> t : TYPES) {
                //rats et pigeons vivent aussi sur les sols batis (planches, pierre, paves) : toute face solide ; les rats meme dans l'ombre
                boolean urban = t == RAT || t == PIGEON, rat = t == RAT;
                event.register((EntityType<WildAnimal>) t.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                      urban ? (type, level, reason, pos, random) -> checkUrban(rat, level, pos) : WildAnimal::checkWildSpawnRules,
                      SpawnPlacementRegisterEvent.Operation.REPLACE);
            }
        }
    }
}

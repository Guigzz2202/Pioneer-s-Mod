package fr.pioneers.world.wild;

import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.core.registries.PiEntityTypes;
import fr.pioneers.core.registries.PiSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.0.5-beta (lot 9, specification « Le monde vivant » §4 « Oceans et mers » et « Cotes et banquise ») : registre de la MER ET DES COTES :
 * quinze especes (sortes 17 a 31, a la suite des lots 6 a 8 : meme base {@link WildAnimal}) : baleines {@link Whale} (bleue, a bosse,
 * orque, cachalot), requins {@link Shark} (blanc, marteau, tigre, baleine, gris), raie manta {@link MantaRay}, phoque et otarie
 * {@link Pinniped}, mouette {@link Seagull}, manchot et pingouin {@link Penguin} ; leurs oeufs ; viande de baleine, graisse, chair de
 * requin, ailes de raie, viande de phoque (crues et cuites) ; les sons (EVENEMENTS vanilla assembles « wild.* », ranges avec ceux du gibier
 * dans {@link PiWild#SOUNDS}) ; le tag des proies de l'orque. Les especes entrent dans le socle de faune par {@link WildFauna9}. Donnees :
 * tools/gc/wild/wild9.py ({@link GeneratedWild9}).
 */
public final class PiWild9 {

    private PiWild9() {
    }

    public static void init() {
    }

    public static final List<RegistryObject<? extends EntityType<? extends WildAnimal>>> TYPES = new ArrayList<>();
    public static final List<Integer> KINDS = new ArrayList<>();
    public static final List<RegistryObject<Item>> EGGS = new ArrayList<>();
    public static final List<RegistryObject<Item>> ITEMS = new ArrayList<>();

    /** Proies de l'orque (phoques, otaries, manchots, pingouins ; les requins en plus : {@link Wild9Rules#orcaPrey}). */
    public static final TagKey<EntityType<?>> ORCA_PREY = TagKey.create(Registries.ENTITY_TYPE, Pioneers.rl("orca_prey"));

    static {
        PiWild8.init();                                   //la nourriture de l'elevage est indexee par sorte : les lots 6 a 8 d'abord
    }

    public static final RegistryObject<EntityType<Whale>> BLUE_WHALE = type(Wild9Rules.BLUE_WHALE, (t, l) -> new Whale(t, l, Wild9Rules.BLUE_WHALE));
    public static final RegistryObject<EntityType<Whale>> HUMPBACK_WHALE = type(Wild9Rules.HUMPBACK, (t, l) -> new Whale(t, l, Wild9Rules.HUMPBACK));
    public static final RegistryObject<EntityType<Whale>> ORCA = type(Wild9Rules.ORCA, (t, l) -> new Whale(t, l, Wild9Rules.ORCA));
    public static final RegistryObject<EntityType<Whale>> SPERM_WHALE = type(Wild9Rules.SPERM_WHALE, (t, l) -> new Whale(t, l, Wild9Rules.SPERM_WHALE));
    public static final RegistryObject<EntityType<Shark>> GREAT_WHITE_SHARK = type(Wild9Rules.GREAT_WHITE, (t, l) -> new Shark(t, l, Wild9Rules.GREAT_WHITE));
    public static final RegistryObject<EntityType<Shark>> HAMMERHEAD_SHARK = type(Wild9Rules.HAMMERHEAD, (t, l) -> new Shark(t, l, Wild9Rules.HAMMERHEAD));
    public static final RegistryObject<EntityType<Shark>> TIGER_SHARK = type(Wild9Rules.TIGER_SHARK, (t, l) -> new Shark(t, l, Wild9Rules.TIGER_SHARK));
    public static final RegistryObject<EntityType<Shark>> WHALE_SHARK = type(Wild9Rules.WHALE_SHARK, (t, l) -> new Shark(t, l, Wild9Rules.WHALE_SHARK));
    public static final RegistryObject<EntityType<Shark>> GRAY_REEF_SHARK = type(Wild9Rules.REEF_SHARK, (t, l) -> new Shark(t, l, Wild9Rules.REEF_SHARK));
    public static final RegistryObject<EntityType<MantaRay>> MANTA_RAY = type(Wild9Rules.MANTA, MantaRay::new);
    public static final RegistryObject<EntityType<Pinniped>> SEAL = type(Wild9Rules.SEAL, (t, l) -> new Pinniped(t, l, Wild9Rules.SEAL));
    public static final RegistryObject<EntityType<Pinniped>> SEA_LION = type(Wild9Rules.SEA_LION, (t, l) -> new Pinniped(t, l, Wild9Rules.SEA_LION));
    public static final RegistryObject<EntityType<Seagull>> SEAGULL = type(Wild9Rules.SEAGULL, Seagull::new);
    public static final RegistryObject<EntityType<Penguin>> PENGUIN = type(Wild9Rules.PENGUIN, (t, l) -> new Penguin(t, l, Wild9Rules.PENGUIN));
    public static final RegistryObject<EntityType<Penguin>> RAZORBILL = type(Wild9Rules.RAZORBILL, (t, l) -> new Penguin(t, l, Wild9Rules.RAZORBILL));

    static {
        for (GeneratedWild.Food f : GeneratedWild9.ITEMS) {
            RegistryObject<Item> ro = PiCoreRegistries.ITEMS.register(f.id(), () -> f.nutrition() > 0 ? new Item(new Item.Properties().food(
                  new net.minecraft.world.food.FoodProperties.Builder().nutrition(f.nutrition()).saturationMod(f.saturation()).meat().build()))
                                                                        : new PiWild.Described(new Item.Properties(), "description.pioneers." + f.id()));
            ITEMS.add(ro);
            PiWild.ITEMS.put(f.id(), ro);
        }
        for (int k = 0; k < GeneratedWild9.SPECIES.length; k++) {
            int kind = GeneratedWild9.FIRST + k;
            String id = GeneratedWild9.SPECIES[k].id();
            for (String s : sounds(kind)) {
                String name = "wild." + id + "." + s;
                PiWild.SOUNDS.put(id + "." + s, PiSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Pioneers.rl(name))));
            }
            //nourriture de l'elevage : index = sorte (a la suite des lots 6 a 8)
            PiWild.FOOD.add(TagKey.create(Registries.ITEM, Pioneers.rl("wild_food/" + id)));
        }
    }

    /** Sons de chaque espece (memes noms que T:wild/wild9.py SOUNDS). */
    static String[] sounds(int kind) {
        if (Wild9Rules.cetacean(kind)) {
            return kind == Wild9Rules.ORCA || kind == Wild9Rules.SPERM_WHALE ? new String[]{"ambient", "hurt", "death", "blow", "attack"}
                                                                             : new String[]{"ambient", "hurt", "death", "blow"};
        }
        if (Wild9Rules.shark(kind) && kind != Wild9Rules.WHALE_SHARK) {
            return new String[]{"ambient", "hurt", "death", "bite"};
        }
        return switch (kind) {
            case Wild9Rules.SEAL, Wild9Rules.SEA_LION, Wild9Rules.PENGUIN, Wild9Rules.RAZORBILL -> new String[]{"ambient", "hurt", "death", "splash"};
            case Wild9Rules.SEAGULL -> new String[]{"ambient", "hurt", "death", "steal"};
            default -> new String[]{"ambient", "hurt", "death"};
        };
    }

    private static <T extends WildAnimal> RegistryObject<EntityType<T>> type(int kind, EntityType.EntityFactory<T> factory) {
        GeneratedWild.Species s = WildAnimal.speciesOf(kind);
        boolean water = GeneratedWild9.AQUATIC[kind - GeneratedWild9.FIRST];
        float len = GeneratedWild9.LENGTH[kind - GeneratedWild9.FIRST];
        int range = len >= 7 ? 16 : len >= 3 ? 12 : 10;                 //les geants se voient de loin
        RegistryObject<EntityType<T>> ro = PiEntityTypes.ENTITY_TYPES.register(s.id(), () -> EntityType.Builder.of(factory,
              water ? MobCategory.WATER_CREATURE : MobCategory.CREATURE).sized(s.width(), s.height()).clientTrackingRange(range).build(s.id()));
        TYPES.add(ro);
        KINDS.add(kind);
        EGGS.add(PiCoreRegistries.ITEMS.register(s.id() + "_spawn_egg", () -> new ForgeSpawnEggItem(ro, s.egg1(), s.egg2(), new Item.Properties())));
        return ro;
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Wild9ModBus {

        private Wild9ModBus() {
        }

        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent event) {
            for (int k = 0; k < TYPES.size(); k++) {
                event.put(TYPES.get(k).get(), WildAnimal.createAttributes(KINDS.get(k)).build());
            }
        }

        @SubscribeEvent
        @SuppressWarnings("unchecked")
        public static void spawns(SpawnPlacementRegisterEvent event) {
            for (int k = 0; k < TYPES.size(); k++) {
                EntityType<WildAnimal> t = (EntityType<WildAnimal>) TYPES.get(k).get();
                if (GeneratedWild9.AQUATIC[KINDS.get(k) - GeneratedWild9.FIRST]) {
                    event.register((EntityType<MarineAnimal>) (EntityType<?>) t, SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                          MarineAnimal::checkSeaSpawn, SpawnPlacementRegisterEvent.Operation.REPLACE);
                } else {
                    event.register(t, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, MarineAnimal::checkCoastSpawn,
                          SpawnPlacementRegisterEvent.Operation.REPLACE);
                }
            }
        }
    }
}

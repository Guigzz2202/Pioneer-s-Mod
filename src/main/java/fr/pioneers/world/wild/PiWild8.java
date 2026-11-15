package fr.pioneers.world.wild;

import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.core.registries.PiEntityTypes;
import fr.pioneers.core.registries.PiSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.0.5-beta (lot 8, specification « Le monde vivant » §4 « Savane » et « Jungle et foret tropicale ») : registre de la SAVANE ET DE LA
 * JUNGLE : six especes ({@code pioneers:giraffe} {@link Giraffe}, {@code lion} {@link Lion}, {@code rhino} {@link Rhino},
 * {@code elephant} {@link Elephant}, {@code monkey} {@link Monkey}, {@code tiger} {@link Tiger} ; sortes 11 a 16, a la suite des lots 6 et 7 :
 * meme base {@link WildAnimal}), leurs oeufs, la viande de gros gibier (crue et rotie), les sons (sons vanilla assembles « wild.* », ranges
 * avec ceux du gibier dans {@link PiWild#SOUNDS}), les tags (proies de la lionne, feuilles que broute la girafe, betes que le chien de
 * chasse ne piste pas) et la PEAU DE BANANE jetee par le singe ({@link BananaPeel} : ephemere, jamais sauvee, rien a ramasser). Les especes
 * entrent dans le socle de faune par {@link WildFauna8}. Donnees : tools/gc/wild/wild8.py ({@link GeneratedWild8}).
 */
public final class PiWild8 {

    private PiWild8() {
    }

    public static void init() {
    }

    public static final List<RegistryObject<? extends EntityType<? extends WildAnimal>>> TYPES = new ArrayList<>();
    public static final List<RegistryObject<Item>> EGGS = new ArrayList<>();
    public static final List<RegistryObject<Item>> ITEMS = new ArrayList<>();

    /** Proies de la lionne (gibier de la savane ; les girafons en plus, dans le code), feuilles de la girafe, betes non pistees par le chien. */
    public static final TagKey<EntityType<?>> LION_PREY = TagKey.create(Registries.ENTITY_TYPE, Pioneers.rl("lion_prey"));
    public static final TagKey<EntityType<?>> NOT_GAME = TagKey.create(Registries.ENTITY_TYPE, Pioneers.rl("not_game"));
    public static final TagKey<Block> GIRAFFE_BROWSE = TagKey.create(Registries.BLOCK, Pioneers.rl("giraffe_browse"));

    static {
        PiWild7.init();                                   //la nourriture de l'elevage est indexee par sorte : les lots 6 et 7 d'abord
    }

    public static final RegistryObject<EntityType<Giraffe>> GIRAFFE = type(Wild8Rules.GIRAFFE, Giraffe::new, 10);
    public static final RegistryObject<EntityType<Lion>> LION = type(Wild8Rules.LION, Lion::new, 10);
    public static final RegistryObject<EntityType<Rhino>> RHINO = type(Wild8Rules.RHINO, Rhino::new, 10);
    public static final RegistryObject<EntityType<Elephant>> ELEPHANT = type(Wild8Rules.ELEPHANT, Elephant::new, 10);
    public static final RegistryObject<EntityType<Monkey>> MONKEY = type(Wild8Rules.MONKEY, Monkey::new, 8);
    public static final RegistryObject<EntityType<Tiger>> TIGER = type(Wild8Rules.TIGER, Tiger::new, 10);
    /** Peau de banane jetee par le singe : petite entite ephemere (pas un objet). */
    public static final RegistryObject<EntityType<BananaPeel>> BANANA_PEEL = PiEntityTypes.ENTITY_TYPES.register("banana_peel",
          () -> EntityType.Builder.<BananaPeel>of(BananaPeel::new, MobCategory.MISC).sized(0.25F, 0.1F).clientTrackingRange(6).updateInterval(2)
                .noSave().build("banana_peel"));

    static {
        for (GeneratedWild.Food f : GeneratedWild8.ITEMS) {
            RegistryObject<Item> ro = PiCoreRegistries.ITEMS.register(f.id(), () -> new Item(new Item.Properties().food(
                  new net.minecraft.world.food.FoodProperties.Builder().nutrition(f.nutrition()).saturationMod(f.saturation()).meat().build())));
            ITEMS.add(ro);
            PiWild.ITEMS.put(f.id(), ro);
        }
        String[][] sounds = {{"ambient", "hurt", "death", "kick"}, {"ambient", "hurt", "death", "roar", "growl"},
                             {"ambient", "hurt", "death", "charge"}, {"ambient", "hurt", "death", "trumpet"},
                             {"ambient", "hurt", "death", "eat", "steal"}, {"ambient", "hurt", "death", "roar", "growl"}};
        for (int k = 0; k < GeneratedWild8.SPECIES.length; k++) {
            String id = GeneratedWild8.SPECIES[k].id();
            for (String s : sounds[k]) {
                String name = "wild." + id + "." + s;
                PiWild.SOUNDS.put(id + "." + s, PiSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Pioneers.rl(name))));
            }
            //nourriture de l'elevage : index = sorte (a la suite des lots 6 et 7)
            PiWild.FOOD.add(TagKey.create(Registries.ITEM, Pioneers.rl("wild_food/" + id)));
        }
    }

    /** La banane du mod (bananier, decision du lot 8 : le singe ne vole et ne mange qu'elle). */
    public static Item banana() {
        Item i = ForgeRegistries.ITEMS.getValue(new ResourceLocation(Pioneers.MODID, "banana"));
        return i == null ? net.minecraft.world.item.Items.AIR : i;
    }

    private static <T extends WildAnimal> RegistryObject<EntityType<T>> type(int kind, EntityType.EntityFactory<T> factory, int range) {
        GeneratedWild.Species s = WildAnimal.speciesOf(kind);
        RegistryObject<EntityType<T>> ro = PiEntityTypes.ENTITY_TYPES.register(s.id(), () -> EntityType.Builder.of(factory, MobCategory.CREATURE)
              .sized(s.width(), s.height()).clientTrackingRange(range).build(s.id()));
        TYPES.add(ro);
        EGGS.add(PiCoreRegistries.ITEMS.register(s.id() + "_spawn_egg", () -> new ForgeSpawnEggItem(ro, s.egg1(), s.egg2(), new Item.Properties())));
        return ro;
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Wild8ModBus {

        private Wild8ModBus() {
        }

        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent event) {
            for (int k = 0; k < TYPES.size(); k++) {
                event.put(TYPES.get(k).get(), WildAnimal.createAttributes(GeneratedWild8.FIRST + k).build());
            }
        }

        @SubscribeEvent
        @SuppressWarnings("unchecked")
        public static void spawns(SpawnPlacementRegisterEvent event) {
            for (RegistryObject<? extends EntityType<? extends WildAnimal>> t : TYPES) {
                event.register((EntityType<WildAnimal>) t.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                      WildAnimal::checkWildSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
            }
        }
    }
}

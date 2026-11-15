package fr.pioneers.farm.animal.breed;

import fr.pioneers.farm.animal.AnimalSpecies;
import fr.pioneers.farm.animal.BreedDef;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.registries.PiEntityTypes;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * Registre des entites de race (1.0.3-beta, lot 8) : 5 especes x 5 races = 25 types {@code pioneers:<race>_<espece>} (boite de la race :
 * {@link BreedDef#width} x {@link BreedDef#height}), leurs oeufs d'apparition {@code <id>_spawn_egg}, attributs et regles d'apparition
 * vanilla de l'espece, tags d'especes {@code pioneers:cows|sheep|pigs|chickens|goats} (les 5 races ET le type vanilla, pour les donnees).
 * Reproduction entre races : le petit prend la race de l'un des deux parents, 50 / 50 (meme regle qu'avant le lot 8).
 */
public final class PiBreeds {

    private PiBreeds() {
    }

    public static void init() {
    }

    /** Types par espece (ordre de {@link AnimalSpecies}) puis par race (ordre de {@code animals.py}). */
    public static final List<List<RegistryObject<? extends EntityType<? extends Animal>>>> TYPES = new ArrayList<>();
    public static final List<RegistryObject<Item>> EGGS = new ArrayList<>();
    private static Map<EntityType<?>, int[]> byType;

    /** Tags d'entites par espece (memes ordres) : {@code pioneers:cows}, {@code sheep}, {@code pigs}, {@code chickens}, {@code goats}. */
    public static final List<TagKey<EntityType<?>>> TAGS = List.of(tag("cows"), tag("sheep"), tag("pigs"), tag("chickens"), tag("goats"));

    private static TagKey<EntityType<?>> tag(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("pioneers", name));
    }

    static {
        for (AnimalSpecies sp : AnimalSpecies.values()) {
            List<RegistryObject<? extends EntityType<? extends Animal>>> list = new ArrayList<>();
            BreedDef[] breeds = sp.breeds();
            for (BreedDef b : breeds) {
                String id = entityId(sp, b);
                RegistryObject<? extends EntityType<? extends Animal>> ro = switch (sp) {
                    case COW -> PiEntityTypes.ENTITY_TYPES.register(id, () -> EntityType.Builder.<PiCow>of(PiCow::new, MobCategory.CREATURE)
                          .sized(b.width(), b.height()).clientTrackingRange(10).build(id));
                    case SHEEP -> PiEntityTypes.ENTITY_TYPES.register(id, () -> EntityType.Builder.<PiSheep>of(PiSheep::new, MobCategory.CREATURE)
                          .sized(b.width(), b.height()).clientTrackingRange(10).build(id));
                    case PIG -> PiEntityTypes.ENTITY_TYPES.register(id, () -> EntityType.Builder.<PiPig>of(PiPig::new, MobCategory.CREATURE)
                          .sized(b.width(), b.height()).clientTrackingRange(10).build(id));
                    case CHICKEN -> PiEntityTypes.ENTITY_TYPES.register(id, () -> EntityType.Builder.<PiChicken>of(PiChicken::new, MobCategory.CREATURE)
                          .sized(b.width(), b.height()).clientTrackingRange(10).build(id));
                    case GOAT -> PiEntityTypes.ENTITY_TYPES.register(id, () -> EntityType.Builder.<PiGoat>of(PiGoat::new, MobCategory.CREATURE)
                          .sized(b.width(), b.height()).clientTrackingRange(10).build(id));
                };
                list.add(ro);
                EGGS.add(PiCoreRegistries.ITEMS.register(id + "_spawn_egg", () -> new ForgeSpawnEggItem(ro, b.egg1(), b.egg2(), new Item.Properties())));
            }
            TYPES.add(list);
        }
    }

    public static String entityId(AnimalSpecies sp, BreedDef b) {
        return b.id() + "_" + sp.id();
    }

    public static EntityType<? extends Animal> type(AnimalSpecies sp, int breed) {
        List<RegistryObject<? extends EntityType<? extends Animal>>> l = TYPES.get(sp.ordinal());
        return l.get(Math.max(0, Math.min(l.size() - 1, breed))).get();
    }

    private static Map<EntityType<?>, int[]> table() {
        if (byType == null) {
            Map<EntityType<?>, int[]> m = new IdentityHashMap<>();
            for (AnimalSpecies sp : AnimalSpecies.values()) {
                for (int i = 0; i < TYPES.get(sp.ordinal()).size(); i++) {
                    m.put(TYPES.get(sp.ordinal()).get(i).get(), new int[]{sp.ordinal(), i});
                }
            }
            byType = m;
        }
        return byType;
    }

    /** Race d'un type d'entite de race (-1 sinon). */
    public static int breedOf(EntityType<?> type) {
        int[] e = table().get(type);
        return e == null ? -1 : e[1];
    }

    /** Espece d'un type d'entite de race (null sinon). */
    @Nullable
    public static AnimalSpecies speciesOf(EntityType<?> type) {
        int[] e = table().get(type);
        return e == null ? null : AnimalSpecies.values()[e[0]];
    }

    /**
     * Petit d'un couple (reproduction) : meme espece ; race de l'un des deux parents tiree a 50 / 50 (meme race -> meme race). Le partenaire
     * d'une autre classe (impossible avec {@code Animal.canMate}) donne la race du parent qui s'accouple.
     */
    @Nullable
    public static <T extends Animal> T offspring(BreedAnimal self, AgeableMob mate, ServerLevel level, RandomSource random, Class<T> cls) {
        int breed = self.breed();
        if (mate instanceof BreedAnimal other && other.species() == self.species() && random.nextBoolean()) {
            breed = other.breed();
        }
        EntityType<?> t = type(self.species(), breed);
        return cls.cast(t.create(level));
    }

    @Mod.EventBusSubscriber(modid = "pioneers", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        @SubscribeEvent
        @SuppressWarnings("unchecked")
        public static void attributes(EntityAttributeCreationEvent event) {
            for (AnimalSpecies sp : AnimalSpecies.values()) {
                for (RegistryObject<? extends EntityType<? extends Animal>> ro : TYPES.get(sp.ordinal())) {
                    event.put(ro.get(), switch (sp) {
                        case COW -> Cow.createAttributes().build();
                        case SHEEP -> Sheep.createAttributes().build();
                        case PIG -> Pig.createAttributes().build();
                        case CHICKEN -> Chicken.createAttributes().build();
                        case GOAT -> Goat.createAttributes().build();
                    });
                }
            }
        }

        @SubscribeEvent
        @SuppressWarnings("unchecked")
        public static void spawns(SpawnPlacementRegisterEvent event) {
            for (AnimalSpecies sp : AnimalSpecies.values()) {
                for (RegistryObject<? extends EntityType<? extends Animal>> ro : TYPES.get(sp.ordinal())) {
                    EntityType<Animal> t = (EntityType<Animal>) ro.get();
                    event.register(t, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                          sp == AnimalSpecies.GOAT ? Goat::checkGoatSpawnRules : Animal::checkAnimalSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
                }
            }
        }
    }
}

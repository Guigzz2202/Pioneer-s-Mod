package fr.pioneers.farm.herd;

import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.farm.animal.breed.BreedConversion;
import fr.pioneers.registries.PiEntityTypes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 28, Guillaume : « Les chevaux, lapin et autres animaux de minecraft doivent avoir de vraies races (et des vrais spawn egg)
 * -> comme tu as fait pour les autres animaux ») : UNE ENTITE PAR RACE pour les lapins (6), les chevaux (8), les anes (3) et les lamas (4) :
 * types {@code pioneers:<id>} de {@link GeneratedHerd#TYPES} ({@link HerdRabbit}, {@link HerdHorse}, {@link HerdDonkey}, {@link HerdLlama} :
 * tout le reste est l'entite vanilla), leurs OEUFS D'APPARITION {@code <id>_spawn_egg} (couleurs de la robe), attributs et regles
 * d'apparition vanilla de l'espece. Les stats individuelles restent dans {@link HerdData} (capacite, comme au lot 9).
 * <p>
 * CONVERSION (meme principe que {@link BreedConversion}) : tout lapin (sauf le lapin tueur), cheval, ane ou lama VANILLA qui entre dans un monde
 * (chunk charge, apparition naturelle, petit ne d'un couple, oeuf ou {@code /summon} vanilla) devient l'entite de sa race au tick serveur
 * suivant : la race de ses donnees (tiree au climat a son arrivee : memes biomes qu'avant ; heritee d'un parent pour un petit), NBT complet et
 * meme UUID. Le mulet (hybride sterile), le lama du marchand ambulant et les chameaux (deja deux especes, lot 10) ne changent pas.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class HerdBreeds {

    private HerdBreeds() {
    }

    public static void init() {
    }

    public static final int RABBIT = 0, HORSE = 1, DONKEY = 2, LLAMA = 3;
    public static final List<RegistryObject<? extends EntityType<? extends Animal>>> TYPES = new ArrayList<>();
    public static final List<RegistryObject<Item>> EGGS = new ArrayList<>();
    private static Map<EntityType<?>, GeneratedHerd.HerdType> byType;
    private static final ArrayDeque<Entity> PENDING = new ArrayDeque<>();
    /** Conversions faites depuis le demarrage (GameTests). */
    public static int converted;

    static {
        for (GeneratedHerd.HerdType t : GeneratedHerd.TYPES) {
            String id = t.id();
            RegistryObject<? extends EntityType<? extends Animal>> ro = switch (t.species()) {
                case RABBIT -> PiEntityTypes.ENTITY_TYPES.register(id, () -> EntityType.Builder.<HerdRabbit>of(HerdRabbit::new, MobCategory.CREATURE)
                      .sized(0.4F, 0.5F).clientTrackingRange(8).build(id));
                //1.0.4-beta (lot 33) : boite de collision au gabarit de la race (T:farm2/herd_shapes.py horse_size / llama_size)
                case HORSE -> PiEntityTypes.ENTITY_TYPES.register(id, () -> EntityType.Builder.<HerdHorse>of(HerdHorse::new, MobCategory.CREATURE)
                      .sized(t.width(), t.height()).clientTrackingRange(10).build(id));
                case DONKEY -> PiEntityTypes.ENTITY_TYPES.register(id, () -> EntityType.Builder.<HerdDonkey>of(HerdDonkey::new, MobCategory.CREATURE)
                      .sized(1.3964844F, 1.5F).clientTrackingRange(10).build(id));
                default -> PiEntityTypes.ENTITY_TYPES.register(id, () -> EntityType.Builder.<HerdLlama>of(HerdLlama::new, MobCategory.CREATURE)
                      .sized(t.width(), t.height()).clientTrackingRange(10).build(id));
            };
            TYPES.add(ro);
            EGGS.add(PiCoreRegistries.ITEMS.register(id + "_spawn_egg", () -> new ForgeSpawnEggItem(ro, t.egg1(), t.egg2(), new Item.Properties())));
        }
    }

    private static Map<EntityType<?>, GeneratedHerd.HerdType> table() {
        if (byType == null) {
            Map<EntityType<?>, GeneratedHerd.HerdType> m = new IdentityHashMap<>();
            for (int i = 0; i < TYPES.size(); i++) {
                m.put(TYPES.get(i).get(), GeneratedHerd.TYPES[i]);
            }
            byType = m;
        }
        return byType;
    }

    /** Race d'un type (premiere race de l'espece si le type n'en est pas un : ne doit pas arriver). */
    public static GeneratedHerd.HerdType typeOf(EntityType<?> type) {
        GeneratedHerd.HerdType t = table().get(type);
        return t != null ? t : GeneratedHerd.TYPES[0];
    }

    /** Type de l'entite d'une race (espece, index de race) ; null si aucune. */
    @Nullable
    public static EntityType<? extends Animal> type(int species, int breed) {
        for (int i = 0; i < GeneratedHerd.TYPES.length; i++) {
            GeneratedHerd.HerdType t = GeneratedHerd.TYPES[i];
            if (t.species() == species && t.breed() == breed) {
                return TYPES.get(i).get();
            }
        }
        return null;
    }

    /** Espece d'un type VANILLA a convertir (-1 sinon) : lapin (pas le tueur), cheval, ane, lama (pas celui du marchand). */
    public static int vanillaSpecies(Entity e) {
        EntityType<?> t = e.getType();
        if (t == EntityType.RABBIT) {
            return ((Rabbit) e).getVariant() == Rabbit.Variant.EVIL ? -1 : RABBIT;
        }
        return t == EntityType.HORSE ? HORSE : t == EntityType.DONKEY ? DONKEY : t == EntityType.LLAMA ? LLAMA : -1;
    }

    /** Sorte de {@link GeneratedHerd.HorseBreed#kind()} d'une espece (cheval 0, ane 1, lama 5). */
    public static int horseKind(int species) {
        return species == DONKEY ? 1 : species == LLAMA ? 5 : 0;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && vanillaSpecies(event.getEntity()) >= 0) {
            PENDING.add(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        int n = PENDING.size();
        for (int i = 0; i < n && !PENDING.isEmpty(); i++) {
            Entity e = PENDING.poll();
            if (e != null && !e.isRemoved() && e.level() instanceof ServerLevel sl && vanillaSpecies(e) >= 0) {
                convert(sl, e);
            }
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        PENDING.clear();
    }

    /** Type d'arrivee : race des donnees si elle est de la bonne espece, sinon tiree au climat (comme a l'arrivee, lot 9). */
    @Nullable
    public static EntityType<? extends Animal> target(ServerLevel level, Entity e) {
        int sp = vanillaSpecies(e);
        if (sp < 0) {
            return null;
        }
        HerdData d = PiHerd.get(e);
        Random r = new Random(level.random.nextLong());
        float temp = fr.pioneers.farm.crop.Climate.temperature(level, e.blockPosition());   //1.0.5 (lot 1) : climat du lieu, saison comprise
        int breed;
        if (sp == RABBIT) {
            breed = d != null && d.init && d.kind == HerdData.RABBIT && d.breed >= 0 ? d.breed : HerdRules.climateBreed(temp, HerdRules.rabbitRanges(), r);
        } else {
            int kind = horseKind(sp);
            boolean ok = d != null && d.init && d.kind == HerdData.HORSE && d.breed >= 0 && d.breed < GeneratedHerd.HORSES.length
                         && GeneratedHerd.HORSES[d.breed].kind() == kind;
            breed = ok ? d.breed : HerdRules.climateBreed(temp, HerdRules.kindRanges(kind), r);
        }
        return type(sp, breed);
    }

    /** Convertit maintenant (meme chemin que les autres elevages : {@link BreedConversion#convertTo}) ; la race des donnees suit le type. */
    @Nullable
    public static Entity convert(ServerLevel level, Entity old) {
        EntityType<? extends Animal> type = target(level, old);
        if (type == null) {
            return null;
        }
        Entity ne = BreedConversion.convertTo(level, old, type);
        if (ne instanceof HerdBreedEntity hb) {
            HerdData d = PiHerd.get(ne);
            if (d != null) {
                d.breed = hb.herdBreed();
                PiHerd.sync(ne);
            }
            converted++;
        }
        return ne;
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        @SubscribeEvent
        @SuppressWarnings("unchecked")
        public static void attributes(EntityAttributeCreationEvent event) {
            for (int i = 0; i < TYPES.size(); i++) {
                EntityType<? extends Animal> t = TYPES.get(i).get();
                event.put(t, switch (GeneratedHerd.TYPES[i].species()) {
                    case RABBIT -> Rabbit.createAttributes().build();
                    case HORSE -> AbstractHorse.createBaseHorseAttributes().build();
                    case DONKEY -> AbstractChestedHorse.createBaseChestedHorseAttributes().build();
                    default -> Llama.createAttributes().build();
                });
            }
        }

        @SubscribeEvent
        @SuppressWarnings("unchecked")
        public static void spawns(SpawnPlacementRegisterEvent event) {
            for (int i = 0; i < TYPES.size(); i++) {
                EntityType<Animal> t = (EntityType<Animal>) TYPES.get(i).get();
                if (GeneratedHerd.TYPES[i].species() == RABBIT) {
                    EntityType<Rabbit> rt = (EntityType<Rabbit>) (EntityType<?>) t;
                    event.register(rt, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Rabbit::checkRabbitSpawnRules,
                          SpawnPlacementRegisterEvent.Operation.REPLACE);
                } else {
                    event.register(t, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules,
                          SpawnPlacementRegisterEvent.Operation.REPLACE);
                }
            }
        }
    }

    /** Mob du type d'une race, pret a etre ajoute (clapier : lapin relache directement dans sa race). */
    @Nullable
    public static Mob create(ServerLevel level, int species, int breed) {
        EntityType<? extends Animal> t = type(species, breed);
        return t == null ? null : t.create(level);
    }
}

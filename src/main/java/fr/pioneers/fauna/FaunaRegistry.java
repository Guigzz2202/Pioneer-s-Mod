package fr.pioneers.fauna;

import fr.pioneers.farm.PiFishing;
import fr.pioneers.farm.animal.breed.BreedAnimal;
import fr.pioneers.farm.crustacean.Crustacean;
import fr.pioneers.farm.fish.PiFish;
import fr.pioneers.farm.herd.HerdDonkey;
import fr.pioneers.farm.herd.HerdHorse;
import fr.pioneers.farm.herd.HerdLlama;
import fr.pioneers.farm.herd.HerdRabbit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 4) : registre des especes de la faune persistante (spec §3 : toute la faune, vanilla et du mod). Une espece = une ligne
 * ({@link FaunaSpecies}) ; les lots 5 a 9 ajoutent les leurs par {@link #add}.
 * <p>
 * Une bete est rattachee a son espece par son TYPE (id du registre), puis par sa classe pour les entites qui remplacent une espece : races
 * d'elevage du mod ({@link BreedAnimal}, lapins, chevaux, anes, lamas : espece vanilla d'origine), poissons du mod (une entite par espece :
 * l'espece generique {@code pioneers:fish}), crustaces. Le resultat est mis en cache par type.
 * <p>
 * Choix faits (1.0.5-beta lot 4) : chauves-souris INCHANGEES (faune des grottes, comme les monstres : apparition et disparition vanilla) ;
 * loups, renards, ours polaires, ocelots = predateurs ; grenouilles, pandas, chameaux, dromadaires = gibier ; poissons vanilla (morue,
 * saumon, poissons tropicaux, poissons-globes) persistants mais jamais remis (le mod les a remplaces par ses especes depuis la 1.0.3) ;
 * crustaces du mod = faune marine ; villageois, colporteurs, golems, chats des villages, chevaux squelettes, lamas de marchand : hors faune.
 */
public final class FaunaRegistry {

    private FaunaRegistry() {
    }

    private static final List<FaunaSpecies> ALL = new ArrayList<>();
    private static final Map<String, FaunaSpecies> BY_ID = new LinkedHashMap<>();
    private static final Map<EntityType<?>, FaunaSpecies> BY_TYPE = new IdentityHashMap<>();
    private static final FaunaSpecies NONE = FaunaSpecies.of("-", FaunaGroup.GAME);
    private static boolean booted;

    /** Inscrit une espece (une ligne) ; rend l'espece pour la suite. Un id deja inscrit est remplace. */
    public static synchronized FaunaSpecies add(FaunaSpecies s) {
        FaunaSpecies old = BY_ID.get(s.id);
        if (old != null) {
            s.index = old.index;
            ALL.set(old.index, s);
        } else {
            s.index = ALL.size();
            ALL.add(s);
        }
        BY_ID.put(s.id, s);
        BY_TYPE.clear();
        FaunaPools.clear();
        return s;
    }

    public static List<FaunaSpecies> all() {
        bootstrap();
        return Collections.unmodifiableList(ALL);
    }

    public static int size() {
        bootstrap();
        return ALL.size();
    }

    @Nullable
    public static FaunaSpecies byId(String id) {
        bootstrap();
        return BY_ID.get(id);
    }

    /**
     * Les especes du lot 4 (spec §3 : « toute la faune »). Ordre : gibier, predateurs, oiseaux, faune marine, poissons. Appele une fois, a la
     * premiere lecture (les types d'entites sont lus par leur id, une fois les registres figes).
     */
    public static synchronized void bootstrap() {
        if (booted) {
            return;
        }
        booted = true;
        // gibier : betes de pature et petite faune (biomes, poids et bandes du jeu)
        add(FaunaSpecies.of("minecraft:cow", FaunaGroup.GAME).listed());
        add(FaunaSpecies.of("minecraft:sheep", FaunaGroup.GAME).listed());
        add(FaunaSpecies.of("minecraft:pig", FaunaGroup.GAME).listed());
        add(FaunaSpecies.of("minecraft:chicken", FaunaGroup.GAME).listed());
        add(FaunaSpecies.of("minecraft:rabbit", FaunaGroup.GAME).listed());
        add(FaunaSpecies.of("minecraft:horse", FaunaGroup.GAME).listed());
        add(FaunaSpecies.of("minecraft:donkey", FaunaGroup.GAME).listed());
        add(FaunaSpecies.of("minecraft:llama", FaunaGroup.GAME).listed());
        add(FaunaSpecies.of("minecraft:goat", FaunaGroup.GAME).listed());
        add(FaunaSpecies.of("minecraft:mooshroom", FaunaGroup.GAME).listed());
        add(FaunaSpecies.of("minecraft:panda", FaunaGroup.GAME).listed());
        add(FaunaSpecies.of("minecraft:frog", FaunaGroup.GAME).listed());
        add(FaunaSpecies.of("minecraft:camel", FaunaGroup.GAME).listed());
        add(FaunaSpecies.of("pioneers:dromedary", FaunaGroup.GAME).listed());
        // predateurs
        add(FaunaSpecies.of("minecraft:wolf", FaunaGroup.PREDATOR).listed());
        add(FaunaSpecies.of("minecraft:fox", FaunaGroup.PREDATOR).listed());
        add(FaunaSpecies.of("minecraft:polar_bear", FaunaGroup.PREDATOR).listed());
        add(FaunaSpecies.of("minecraft:ocelot", FaunaGroup.PREDATOR).listed());
        // oiseaux : le corbeau vit dans les plaines, forets, taigas et savanes (bandes de 2 a 4, au sol a ciel ouvert)
        add(FaunaSpecies.of("minecraft:parrot", FaunaGroup.BIRD).listed());
        add(FaunaSpecies.of("pioneers:crow", FaunaGroup.BIRD).biomes("family:temperate", "family:cold", "#minecraft:is_savanna").size(2, 4)
              .weight(8).where(FaunaSpecies.Where.LAND).rule(FaunaRegistry::openGround));
        // faune marine
        add(FaunaSpecies.of("minecraft:squid", FaunaGroup.MARINE).listed());
        add(FaunaSpecies.of("minecraft:glow_squid", FaunaGroup.MARINE).listed());
        add(FaunaSpecies.of("minecraft:dolphin", FaunaGroup.MARINE).listed());
        add(FaunaSpecies.of("minecraft:turtle", FaunaGroup.MARINE).listed());
        add(FaunaSpecies.of("minecraft:axolotl", FaunaGroup.MARINE).listed());
        add(FaunaSpecies.of("pioneers:crustacean", FaunaGroup.MARINE).listed());
        add(FaunaSpecies.of("pioneers:shore_crab", FaunaGroup.MARINE).listed());
        // poissons : ceux du mod (espece tiree par le milieu, FishSpawns) ; les poissons vanilla restent mais ne sont jamais remis
        add(FaunaSpecies.of("pioneers:fish", FaunaGroup.FISH).listed());
        add(FaunaSpecies.of("minecraft:cod", FaunaGroup.FISH).persistOnly());
        add(FaunaSpecies.of("minecraft:salmon", FaunaGroup.FISH).persistOnly());
        add(FaunaSpecies.of("minecraft:tropical_fish", FaunaGroup.FISH).persistOnly());
        add(FaunaSpecies.of("minecraft:pufferfish", FaunaGroup.FISH).persistOnly());
        //1.0.5-beta (lot 6) : le gibier des forets et des plaines (sanglier, cerf, dindon, lievre, bison), une ligne par espece
        fr.pioneers.wild.WildFauna.register();
        //1.0.5-beta (lot 7) : predateurs et petite faune (loup, ours, pic-vert, rat, pigeon, serpent), une ligne par espece
        fr.pioneers.wild.WildFauna7.register();
        //1.0.5-beta (lot 8) : savane et jungle (girafe, lion, rhinoceros, elephant, singe, tigre), une ligne par espece
        fr.pioneers.wild.WildFauna8.register();
        //1.0.5-beta (lot 9) : mer et cotes (baleines, requins, raie manta, phoque, otarie, mouette, manchot, pingouin), une ligne par espece
        fr.pioneers.wild.WildFauna9.register();
    }

    /** Corbeau : sur un bloc solide, a ciel ouvert, hors de l'eau. */
    static boolean openGround(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getFluidState(pos).isEmpty() && level.getFluidState(below).isEmpty() && level.canSeeSky(pos)
               && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    // ------------------------------------------------------------------ resolution

    /** Espece d'un type d'entite (null : hors faune). Types des especes de poissons du mod : l'espece generique. */
    @Nullable
    public static FaunaSpecies of(EntityType<?> type) {
        bootstrap();
        FaunaSpecies s = BY_TYPE.get(type);
        if (s == null) {
            s = resolve(type);
            BY_TYPE.put(type, s == null ? NONE : s);
        }
        return s == NONE ? null : s;
    }

    /** Espece d'une bete : par son type, sinon par sa classe (races, poissons, crustaces). */
    @Nullable
    public static FaunaSpecies of(Entity e) {
        FaunaSpecies s = of(e.getType());
        if (s != null) {
            return s;
        }
        String vanilla = null;
        if (e instanceof BreedAnimal b) {
            vanilla = "minecraft:" + b.species().id();
        } else if (e instanceof HerdRabbit) {
            vanilla = "minecraft:rabbit";
        } else if (e instanceof HerdHorse) {
            vanilla = "minecraft:horse";
        } else if (e instanceof HerdDonkey) {
            vanilla = "minecraft:donkey";
        } else if (e instanceof HerdLlama) {
            vanilla = "minecraft:llama";
        } else if (e instanceof PiFish) {
            vanilla = "pioneers:fish";
        } else if (e instanceof Crustacean) {
            vanilla = "pioneers:crustacean";
        }
        if (vanilla == null) {
            return null;
        }
        s = BY_ID.get(vanilla);
        if (s != null) {
            BY_TYPE.put(e.getType(), s);
        }
        return s;
    }

    @Nullable
    private static FaunaSpecies resolve(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        FaunaSpecies s = id == null ? null : BY_ID.get(id.toString());
        if (s == null && PiFishing.speciesOf(type) >= 0) {
            s = BY_ID.get("pioneers:fish");
        }
        return s;
    }

    /** Type d'entite d'une espece (null si l'id n'existe pas dans le jeu). */
    @Nullable
    public static EntityType<?> type(FaunaSpecies s) {
        ResourceLocation id = ResourceLocation.tryParse(s.id);
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }
}

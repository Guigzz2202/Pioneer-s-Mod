package fr.pioneers.farm.animal.breed;

import fr.pioneers.Pioneers;
import fr.pioneers.farm.PiFishing;
import fr.pioneers.farm.animal.AnimalData;
import fr.pioneers.farm.animal.AnimalSpecies;
import fr.pioneers.farm.animal.PiAnimals;
import fr.pioneers.farm.fish.PiFish;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Mondes existants et apparitions (1.0.3-beta, lot 8 : une entite par race) : toute vache, mouton, cochon, poule ou chevre VANILLA qui entre
 * dans un monde (chunk charge, apparition naturelle ou par le generateur, oeuf vanilla, {@code /summon minecraft:cow}, oeuf lance, autre mod)
 * devient l'entite de sa race au tick serveur suivant : la race de ses donnees ({@link AnimalData}, tiree au climat a son apparition par
 * {@code AnimalEvents.init} : memes biomes et memes proportions qu'avant), sinon tiree au climat. Tout le NBT est recopie (nom, vie, age,
 * sexe, faim, soif, domestication, laisse, toison, minuteries, proprietaire, effets, donnees des autres mods, MEME UUID) ; monture et passagers
 * sont remis. Meme chose pour un poisson {@code pioneers:fish} (type generique : seau, oeufs, oeuf d'apparition generique, apparition par le
 * milieu) : il devient l'entite de son espece {@code pioneers:fish_<id>} (taille gardee). Rien n'est scanne a chaque tick : seules les
 * entites qui entrent dans le monde sont mises dans la file.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class BreedConversion {

    private BreedConversion() {
    }

    private static final ArrayDeque<Entity> PENDING = new ArrayDeque<>();
    /** Conversions faites depuis le demarrage (GameTests, journal). */
    public static int converted;

    /** A convertir : animal vanilla d'une espece d'elevage, ou poisson du type generique. */
    public static boolean needs(Entity e) {
        return !(e instanceof BreedAnimal) && AnimalSpecies.vanilla(e.getType()) != null
               || e instanceof PiFish && e.getType() == PiFishing.FISH.get();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void join(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (event.getLevel().isClientSide) {
            return;
        }
        if (needs(e)) {
            PENDING.add(e);
        }
        //Le loup vanilla ne chasse que le type minecraft:sheep (PREY_SELECTOR) : il chasse aussi les moutons de race
        if (e instanceof Wolf wolf) {
            wolf.targetSelector.addGoal(5, new NonTameRandomTargetGoal<>(wolf, Animal.class, false, t -> t instanceof PiSheep));
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
            if (e != null && !e.isRemoved() && e.level() instanceof ServerLevel sl && needs(e)) {
                convert(sl, e);
            }
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        PENDING.clear();
    }

    /** Type d'arrivee d'une entite a convertir (null : rien a faire). */
    @Nullable
    public static EntityType<?> target(ServerLevel level, Entity e) {
        if (e instanceof PiFish fish) {
            return PiFishing.speciesType(fish.species());
        }
        AnimalSpecies sp = AnimalSpecies.vanilla(e.getType());
        if (sp == null) {
            return null;
        }
        AnimalData d = PiAnimals.get(e);
        int breed = d != null && d.init ? d.breed : fr.pioneers.farm.animal.AnimalEvents.climateBreed(level, e.blockPosition(), sp);
        return PiBreeds.type(sp, breed);
    }

    /** Convertit maintenant (NBT complet, meme UUID, monture et passagers) ; renvoie la nouvelle entite (null si rien n'a change). */
    @Nullable
    public static Entity convert(ServerLevel level, Entity old) {
        //1.0.5-beta (lot 25) : un requin gris sorti d'un seau (espece de peche seulement) redevient la vraie bete
        if (old instanceof PiFish fish && fr.pioneers.farm.fish.FishSpawns.catchOnly(fish.def())) {
            net.minecraft.world.entity.Mob m = fr.pioneers.farm.fish.LiveRelease.liveFish(level, fish.species(), 1F);
            if (m == null) {
                return null;
            }
            m.moveTo(old.getX(), old.getY(), old.getZ(), old.getYRot(), 0F);
            old.discard();
            level.addFreshEntity(m);
            converted++;
            return m;
        }
        EntityType<?> type = target(level, old);
        if (type == null) {
            return null;
        }
        Entity ne = convertTo(level, old, type);
        if (ne != null) {
            converted++;
        }
        return ne;
    }

    /**
     * 1.0.4-beta (lot 28) : le remplacement lui-meme, vers un type donne (aussi pour les lapins, chevaux, anes et lamas de race :
     * {@code J:farm/herd/HerdBreeds}) : NBT complet (capacites comprises), meme UUID, monture et passagers remis ; null si rien n'a change.
     */
    @Nullable
    public static Entity convertTo(ServerLevel level, Entity old, EntityType<?> type) {
        Entity ne = type.create(level);
        if (ne == null) {
            return null;
        }
        CompoundTag tag = old.saveWithoutId(new CompoundTag());
        tag.remove("Passengers");
        Entity vehicle = old.getVehicle();
        List<Entity> riders = new ArrayList<>(old.getPassengers());
        for (Entity r : riders) {
            r.stopRiding();
        }
        old.stopRiding();
        old.discard();
        ne.load(tag);
        if (!level.addFreshEntity(ne)) {
            ne.setUUID(Mth.createInsecureUUID(level.random));
            if (!level.addFreshEntity(ne)) {
                return null;
            }
        }
        if (ne instanceof BreedAnimal b) {
            AnimalData d = PiAnimals.get(ne);
            if (d != null) {
                d.breed = b.breed();
            }
        }
        if (vehicle != null && !vehicle.isRemoved()) {
            ne.startRiding(vehicle, true);
        }
        for (Entity r : riders) {
            r.startRiding(ne, true);
        }
        return ne;
    }
}

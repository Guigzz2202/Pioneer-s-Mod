package fr.pioneers.world.pet;

import fr.pioneers.world.fauna.FaunaGroup;
import fr.pioneers.world.fauna.FaunaRegistry;
import fr.pioneers.world.fauna.FaunaSpecies;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 5) : code commun des chiens et des chats ({@link PetDog}, {@link PetCat}) : sauvegarde (mode, niche, repos, cervelle),
 * stats de la race (aptitudes -> attributs, {@link PetRules}), petits, tick serveur (mode assis du vanilla, froid, aboiements de garde,
 * niche disparue), territoire et place de repos.
 */
public final class PetCore {

    private PetCore() {
    }

    /** 1.0.5-beta (lot 10) : un troupeau senti tient le chien en alerte 2 minutes (il va chercher son maitre). */
    public static final int HERD_ALERT = 2_400;

    private static final UUID COLD = UUID.fromString("6b1c0e57-3a51-4b0d-9f15-1d0c5e7a0105");

    public static int radius() {
        return fr.pioneers.core.common.PiCommonConfig.get(fr.pioneers.core.common.PiCommonConfig.PET_TERRITORY, PetRules.TERRITORY);
    }

    // ------------------------------------------------------------------ sauvegarde

    public static void save(Pet p, CompoundTag tag) {
        CompoundTag t = new CompoundTag();
        t.putByte("mode", (byte) p.mode().ordinal());
        GlobalPos h = p.home();
        if (h != null) {
            t.putString("home_dim", h.dimension().location().toString());
            t.putLong("home", h.pos().asLong());
        }
        t.put("brain", p.brain().save());
        tag.put("PioneersPet", t);
    }

    public static void load(Pet p, CompoundTag tag) {
        if (!tag.contains("PioneersPet")) {
            //chien converti d'un loup apprivoise, chat converti : assis -> mode Assis, sinon Suivre
            p.setMode(p.self().isOrderedToSit() ? PetMode.SIT : PetMode.FOLLOW);
            return;
        }
        CompoundTag t = tag.getCompound("PioneersPet");
        PetMode m = PetMode.of(t.getByte("mode"));
        if (t.contains("home")) {
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("home_dim"));
            p.setHome(dim == null ? null : GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(t.getLong("home"))));
        }
        p.brain().load(t.getCompound("brain"));
        p.setMode(m.allowed(p.isCat()) ? m : PetMode.FOLLOW);
    }

    // ------------------------------------------------------------------ stats de la race

    public static void applyStats(Pet p) {
        TamableAnimal a = p.self();
        if (p.isCat()) {
            GeneratedPets.CatBreed b = GeneratedPets.CATS[p.breed()];
            setBase(a, Attributes.ATTACK_DAMAGE, PetRules.catAttack(b.ratting()));
        } else {
            GeneratedPets.DogBreed b = GeneratedPets.DOGS[p.breed()];
            setBase(a, Attributes.MOVEMENT_SPEED, PetRules.dogSpeed(b.speed()));
            if (a.isTame()) {
                double max = PetRules.dogHealth(b.strength());
                boolean full = a.getHealth() >= a.getMaxHealth() - 0.01F;
                setBase(a, Attributes.MAX_HEALTH, max);
                setBase(a, Attributes.ATTACK_DAMAGE, PetRules.dogAttack(b.strength()));
                if (full || a.getHealth() > max) {
                    a.setHealth((float) max);
                }
            }
        }
    }

    private static void setBase(LivingEntity e, net.minecraft.world.entity.ai.attributes.Attribute attr, double v) {
        AttributeInstance i = e.getAttribute(attr);
        if (i != null && Math.abs(i.getBaseValue() - v) > 1E-6) {
            i.setBaseValue(v);
        }
    }

    /** Endurance au froid : ralentit le chien peu endurant dans le froid (temperature du lieu, saison comprise). */
    static void cold(Pet p) {
        TamableAnimal a = p.self();
        AttributeInstance speed = a.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || p.isCat()) {
            return;
        }
        float t = fr.pioneers.farm.crop.Climate.temperature(a.level(), a.blockPosition());
        double pen = t < PetRules.COLD_BELOW ? PetRules.coldPenalty(GeneratedPets.DOGS[p.breed()].cold()) : 0.0;
        AttributeModifier cur = speed.getModifier(COLD);
        if (pen <= 1E-4) {
            if (cur != null) {
                speed.removeModifier(COLD);
            }
        } else if (cur == null || Math.abs(cur.getAmount() + pen) > 1E-4) {
            speed.removeModifier(COLD);
            speed.addTransientModifier(new AttributeModifier(COLD, "pioneers pet cold", -pen, AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    // ------------------------------------------------------------------ petits

    /** Race du petit (meme race, ou l'une des deux) ; proprietaire repris du parent apprivoise (comme le vanilla). */
    static int childBreed(Pet a, @Nullable Object mate, double roll) {
        int b = mate instanceof Pet m && m.isCat() == a.isCat() ? m.breed() : a.breed();
        return PetRules.childBreed(a.breed(), b, roll);
    }

    static void adopt(TamableAnimal parent, TamableAnimal child) {
        UUID owner = parent.getOwnerUUID();
        if (owner != null) {
            child.setOwnerUUID(owner);
            child.setTame(true);
        }
    }

    // ------------------------------------------------------------------ tick serveur

    static void tick(Pet p) {
        TamableAnimal a = p.self();
        if (!(a.level() instanceof ServerLevel level)) {
            return;
        }
        PetBrain br = p.brain();
        if (br.statsDue) {
            br.statsDue = false;
            applyStats(p);
        }
        //mode Assis <-> clic droit vanilla (le maitre fait asseoir / lever son animal)
        if (a.isOrderedToSit() && p.mode() != PetMode.SIT) {
            p.setMode(PetMode.SIT);
        } else if (!a.isOrderedToSit() && p.mode() == PetMode.SIT) {
            p.setMode(p.home() != null ? PetMode.AUTONOMOUS : PetMode.FOLLOW);
        }
        if (p.rest() != Pet.AWAKE && (a.hurtTime > 0 || a.getTarget() != null || a.isInSittingPose() || p.effectiveMode() != PetMode.AUTONOMOUS)) {
            p.setRest(Pet.AWAKE);
        }
        long now = level.getGameTime();
        if ((a.tickCount + a.getId()) % 100 == 0) {
            cold(p);
            checkHome(p, level);
        }
        //1.0.5-beta (lot 10, spec §5.4) : un troupeau a portee : le chien autonome GROGNE puis va chercher son maitre (visite forcee)
        if (!p.isCat() && a.isTame() && p.effectiveMode() == PetMode.AUTONOMOUS && (a.tickCount + a.getId()) % 40 == 0 && now >= br.herdAlertUntil
            && HuntEvents.herdNearby(a)) {
            br.herdAlertUntil = now + HERD_ALERT;
            br.nextVisit = now;
            if (p.rest() != Pet.AWAKE) {
                p.setRest(Pet.AWAKE);
            }
            a.playSound(SoundEvents.WOLF_GROWL, 1.5F, 0.85F + a.getRandom().nextFloat() * 0.2F);
        }
        //1.0.5-beta (lot 28, retour 35) : UN releve toutes les 10 ticks (tick de l'entite : jamais saute) : la menace a combattre (lue par le
        //but de garde au choix de buts suivant) et, en autonome, l'intrus contre lequel aboyer (avant : deux releves toutes les 20 ticks)
        if (!p.isCat() && PetReact.scanTick(a.tickCount, a.getId(), PetReact.GUARD_SCAN)) {
            br.threat = null;
            boolean auto = a.isTame() && p.effectiveMode() == PetMode.AUTONOMOUS;
            if (a.isTame() && !a.isOrderedToSit()) {
                LivingEntity[] found = auto ? intruders(p, level) : new LivingEntity[]{defend(p, level), null};
                br.threat = found[0];
                LivingEntity bark = found[0] != null ? found[0] : found[1];
                if (auto && bark != null && now - br.lastBark >= PetRules.BARK_GAP) {
                    br.lastBark = now;
                    a.getLookControl().setLookAt(bark, 30F, 30F);
                    a.playSound(SoundEvents.WOLF_AMBIENT, 1.4F, 0.8F + a.getRandom().nextFloat() * 0.3F);
                }
                if (br.threat != null && p.rest() != Pet.AWAKE) {
                    p.setRest(Pet.AWAKE);
                }
            }
        }
    }

    /**
     * 1.0.5-beta (lot 28) : releve du chien AUTONOME : {predateur le plus proche, monstre le plus proche} dans le territoire, a moins de 24 blocs
     * et a vue (une seule recherche d'entites).
     */
    static LivingEntity[] intruders(Pet p, ServerLevel level) {
        BlockPos h = homeHere(p);
        LivingEntity[] out = new LivingEntity[2];
        if (h == null) {
            return out;
        }
        int r = radius();
        double[] best = {Double.MAX_VALUE, Double.MAX_VALUE};
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(h).inflate(r, 12, r), e -> e.isAlive() && e != p.self()
              && (e instanceof Enemy || predator(e)) && inTerritory(p, e.position()) && e.distanceToSqr(p.self()) < 24 * 24)) {
            int k = predator(e) ? 0 : 1;
            double d = e.distanceToSqr(p.self());
            if (d < best[k] && p.self().hasLineOfSight(e)) {
                best[k] = d;
                out[k] = e;
            }
        }
        return out;
    }

    /**
     * 1.0.5-beta (lot 28, retour 35) : en Suivre et en Chasse, le chien DEFEND : predateur du betail (tag, ou predateur du socle qui s'en
     * prend a un animal) ou predateur du socle qui vise le maitre, le chien ou un animal de compagnie, a moins de
     * {@link PetReact#DEFEND_RANGE} blocs du chien ou du maitre, a vue. Avant : rien tant que le loup n'avait pas mordu le maitre.
     */
    @Nullable
    static LivingEntity defend(Pet p, ServerLevel level) {
        TamableAnimal a = p.self();
        LivingEntity o = a.getOwner(), owner = o != null && o.level() == level ? o : null;
        double r = PetReact.DEFEND_RANGE;
        AABB box = a.getBoundingBox().inflate(r, 8, r);
        if (owner != null && owner.distanceToSqr(a) < 48 * 48) {
            box = box.minmax(owner.getBoundingBox().inflate(r, 8, r));
        }
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != a && e != owner)) {
            if (!(predator(e) || menaces(e, a, owner))) {
                continue;
            }
            double d = Math.min(e.distanceToSqr(a), owner == null ? Double.MAX_VALUE : e.distanceToSqr(owner));
            if (d < r * r && d < bd && a.hasLineOfSight(e)) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** Predateur sauvage (groupe PREDATEUR du socle) qui vise le maitre, le chien ou un animal de compagnie. */
    static boolean menaces(LivingEntity e, LivingEntity dog, @Nullable LivingEntity owner) {
        if (!(e instanceof Mob m) || m.getTarget() == null || e instanceof Pet || e instanceof TamableAnimal t && t.isTame()) {
            return false;
        }
        FaunaSpecies s = FaunaRegistry.of(e);
        LivingEntity t = m.getTarget();
        return s != null && s.group == FaunaGroup.PREDATOR && (t == dog || t == owner || t instanceof Pet);
    }

    /** Controle immediat de la niche (GameTests). */
    public static void checkHomeNow(Pet p, ServerLevel level) {
        checkHome(p, level);
    }

    /** Niche ou panier casse : l'animal est delie et passe en Suivre. */
    static void checkHome(Pet p, ServerLevel level) {
        GlobalPos h = p.home();
        if (h == null || !h.dimension().equals(level.dimension()) || !level.isLoaded(h.pos())) {
            return;
        }
        if (!PiPets.isHome(level.getBlockState(h.pos()).getBlock(), p.isCat())) {
            p.setHome(null);
            if (p.mode() == PetMode.AUTONOMOUS) {
                p.setMode(PetMode.FOLLOW);
            }
            if (p.self().getOwner() instanceof net.minecraft.world.entity.player.Player owner) {
                owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("pet.pioneers.msg.home_lost", p.self().getDisplayName()),
                      false);
            }
        }
    }

    // ------------------------------------------------------------------ territoire

    /** Niche ou panier dans le monde de l'animal (null sinon). */
    @Nullable
    public static BlockPos homeHere(Pet p) {
        GlobalPos h = p.home();
        return h != null && h.dimension().equals(p.self().level().dimension()) ? h.pos() : null;
    }

    /** Place de repos : devant la porte de la niche, sur le panier. */
    public static Vec3 restSpot(Level level, BlockPos home, boolean cat) {
        if (cat) {
            return new Vec3(home.getX() + 0.5, home.getY() + PetHomeBlock.BASKET_TOP / 16.0, home.getZ() + 0.5);
        }
        if (!level.isLoaded(home)) {
            return Vec3.atBottomCenterOf(home.above());                //1.0.5-beta (lot 21) : jamais de troncon charge par l'IA
        }
        BlockState s = level.getBlockState(home);
        BlockPos front = s.hasProperty(HorizontalDirectionalBlock.FACING) ? home.relative(s.getValue(HorizontalDirectionalBlock.FACING)) : home.above();
        return new Vec3(front.getX() + 0.5, front.getY(), front.getZ() + 0.5);
    }

    public static boolean inTerritory(Pet p, Vec3 pos) {
        BlockPos h = homeHere(p);
        return h != null && PetRules.inTerritory(pos.x - h.getX() - 0.5, pos.z - h.getZ() - 0.5, radius()) && Math.abs(pos.y - h.getY()) < 24;
    }

    /** Monstre ou predateur sauvage entre dans le territoire (chien) ; null sinon. Predateur = tag ou groupe PREDATEUR du socle de faune. */
    @Nullable
    public static LivingEntity intruder(Pet p, ServerLevel level, boolean monsters) {
        BlockPos h = homeHere(p);
        if (h == null) {
            return null;
        }
        int r = radius();
        AABB box = new AABB(h).inflate(r, 12, r);
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != p.self()
              && ((monsters && e instanceof Enemy) || predator(e)) && inTerritory(p, e.position()) && e.distanceToSqr(p.self()) < 24 * 24);
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : found) {
            double d = e.distanceToSqr(p.self());
            if (d < bd && p.self().hasLineOfSight(e)) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** Predateur sauvage du betail : tag {@code pioneers:livestock_predators}, ou bete du groupe PREDATEUR qui s'en prend a un animal. */
    public static boolean predator(LivingEntity e) {
        if (e instanceof Pet || e instanceof TamableAnimal t && t.isTame()) {
            return false;
        }
        if (e.getType().is(PiPets.LIVESTOCK_PREDATORS)) {
            return true;
        }
        FaunaSpecies s = FaunaRegistry.of(e);
        //1.0.5-beta (lot 7) : un serpent qui chasse un rat (nuisible, tag pioneers:cat_prey) n'est pas un predateur du betail
        return s != null && s.group == FaunaGroup.PREDATOR && e instanceof Mob m && m.getTarget() instanceof Animal prey && !(prey instanceof Pet)
               && !prey.getType().is(PiPets.CAT_PREY);
    }

    /** Gibier pour le chien de chasse : groupe GIBIER du socle de faune (les especes des lots 6+ y entrent seules), pas une bete d'elevage. */
    public static boolean game(LivingEntity e) {
        if (!e.isAlive() || e instanceof Pet || e.hasCustomName() || e instanceof Mob m && m.isLeashed()
            || e instanceof TamableAnimal t && t.isTame() || e instanceof net.minecraft.world.entity.animal.horse.AbstractHorse h && h.isTamed()) {
            return false;
        }
        FaunaSpecies s = FaunaRegistry.of(e);
        //1.0.5-beta (lot 7) : le rat (groupe GIBIER du socle pour le budget) est un nuisible, pas un gibier
        //1.0.5-beta (lot 8) : le singe (gibier du socle pour le budget, tag pioneers:not_game) n'est pas un gibier non plus
        return s != null && s.group == FaunaGroup.GAME && !e.getType().is(PiPets.CAT_PREY) && !e.getType().is(fr.pioneers.world.wild.PiWild8.NOT_GAME);
    }
}

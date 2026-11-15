package fr.pioneers.pet;

import fr.pioneers.farm.fish.FishDef;
import fr.pioneers.farm.fish.FishItems;
import fr.pioneers.farm.fish.FishSpawns;
import fr.pioneers.farm.fish.GeneratedFish;
import fr.pioneers.farm.fish.PiFish;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OcelotAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 5, specification §5.3 et §5.4) : les BUTS des chiens et des chats, poses par-dessus ceux du vanilla (le suivi du maitre
 * et la promenade vanilla sont remplaces par des versions qui suivent le mode) :
 * <ul>
 *   <li>{@link Home} : en AUTONOME, revient dans son territoire (rayon autour de la niche / du panier ; teleporte s'il est trop loin ou
 *   bloque) ;</li>
 *   <li>{@link Sleep} : la nuit, et de temps en temps le jour, va dormir a sa niche (couche devant la porte) ou dans son panier (en boule) ;</li>
 *   <li>{@link Visit} : de temps en temps, va retrouver son maitre s'il est dans le territoire ou a proximite, reste quelques minutes ;</li>
 *   <li>{@link Roam} : promenade dans le territoire (autonome) ou vanilla ;</li>
 *   <li>{@link Follow} : suit le maitre en mode SUIVRE (et CHASSE sans gibier) ;</li>
 *   <li>chien : {@link Guard} (attaque les predateurs du betail entres dans le territoire ; les aboiements sont dans {@link PetCore#tick}) et
 *   {@link Hunt} (mode CHASSE : piste le gibier, attend le maitre, marque l'arret, aboie vers la bete) ;</li>
 *   <li>chat : {@link Prey} (rats du territoire : tag {@code pioneers:cat_prey}) et {@link Fish} (va pecher dans une eau du territoire,
 *   rapporte le poisson dans son panier).</li>
 * </ul>
 */
public final class PetGoals {

    private PetGoals() {
    }

    /**
     * Buts poses par-dessus ceux du vanilla. 1.0.5-beta (lot 28, retour 35) : l'attaque vanilla ({@code MeleeAttackGoal} du chien, un essai
     * toutes les 20 ticks ; {@code OcelotAttackGoal} du chat en priorite 9, que le suivi et l'assise sur un coffre empechaient) est remplacee
     * par {@link Strike} (priorite 5, sans delai) ; chat : la peche passe AVANT la sieste et l'assise vanilla sur les coffres (priorite 6 contre
     * 7 : elle les interrompt) ; un seul releve des menaces dans {@link PetCore#tick}.
     */
    static void install(TamableAnimal mob, Pet pet, boolean cat) {
        mob.goalSelector.removeAllGoals(g -> g instanceof FollowOwnerGoal || g instanceof WaterAvoidingRandomStrollGoal
                                             || g instanceof MeleeAttackGoal || g instanceof OcelotAttackGoal);
        mob.goalSelector.addGoal(5, new Strike(mob, cat));
        int p = 6;
        mob.goalSelector.addGoal(p, new Home(pet));
        if (cat) {
            mob.goalSelector.addGoal(p, new Fish(pet));
        } else {
            mob.goalSelector.addGoal(p, new Hunt(pet));
        }
        mob.goalSelector.addGoal(cat ? 7 : p, new Sleep(pet));
        mob.goalSelector.addGoal(p, new Follow(pet, cat));
        mob.goalSelector.addGoal(cat ? 8 : 7, new Visit(pet));
        mob.goalSelector.addGoal(cat ? 11 : 8, new Roam(pet));
        mob.targetSelector.addGoal(5, cat ? new Prey(pet) : new Guard(pet));
    }

    /**
     * 1.0.5-beta (lot 28, retour 35) : l'ATTAQUE des chiens et des chats : celle du vanilla ({@code MeleeAttackGoal} : course, saut, morsure),
     * mais {@code canUse} sans le delai de 20 ticks entre deux essais : la course part au choix de buts qui suit la prise de cible. Le chemin
     * est calcule au premier {@code tick()} (recalcul toutes les 4 a 10 ticks, comme le vanilla).
     */
    static final class Strike extends MeleeAttackGoal {

        Strike(TamableAnimal mob, boolean cat) {
            super(mob, cat ? 1.2 : 1.0, true);
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && mob.canAttack(t) && !(t instanceof Player pl && (pl.isCreative() || pl.isSpectator()))
                   && !(mob instanceof TamableAnimal ta && ta.isOrderedToSit());
        }
    }

    static long now(TamableAnimal a) {
        return a.level().getGameTime();
    }

    static boolean autonomous(Pet p) {
        return p.self().isTame() && p.effectiveMode() == PetMode.AUTONOMOUS && PetCore.homeHere(p) != null;
    }

    // ------------------------------------------------------------------ territoire

    /** Revient dans son territoire ; teleporte a sa place s'il en est tres loin ou bloque 10 s. */
    static final class Home extends Goal {
        final Pet p;

        Home(Pet p) {
            this.p = p;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return autonomous(p) && p.self().getTarget() == null && !PetCore.inTerritory(p, p.self().position());
        }

        @Override
        public boolean canContinueToUse() {
            BlockPos h = PetCore.homeHere(p);
            return autonomous(p) && h != null && p.self().getTarget() == null
                   && p.self().position().distanceToSqr(Vec3.atCenterOf(h)) > Math.pow(PetCore.radius() * 0.6, 2);
        }

        @Override
        public void start() {
            p.brain().stuck = 0;
        }

        @Override
        public void tick() {
            TamableAnimal a = p.self();
            BlockPos h = PetCore.homeHere(p);
            if (h == null) {
                return;
            }
            Vec3 spot = PetCore.restSpot(a.level(), h, p.isCat());
            double d = a.position().distanceTo(spot);
            if (a.getNavigation().isDone()) {
                if (!a.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.1)) {
                    p.brain().stuck += 20;
                }
            }
            if (a.getDeltaMovement().horizontalDistanceSqr() < 1E-4) {
                p.brain().stuck++;
            }
            if ((d > PetCore.radius() + 48 || p.brain().stuck > 200) && a.level().isLoaded(h)) {
                a.moveTo(spot.x, spot.y, spot.z, a.getYRot(), a.getXRot());
                a.getNavigation().stop();
                p.brain().stuck = 0;
            }
        }

        @Override
        public void stop() {
            p.self().getNavigation().stop();
        }
    }

    /** Dort a sa place la nuit, et fait parfois la sieste le jour. */
    static final class Sleep extends Goal {
        final Pet p;
        boolean arrived;

        Sleep(Pet p) {
            this.p = p;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
        }

        boolean time() {
            TamableAnimal a = p.self();
            return !a.level().isDay() || now(a) < p.brain().napUntil;
        }

        @Override
        public boolean canUse() {
            TamableAnimal a = p.self();
            if (!autonomous(p) || a.getTarget() != null || a.isInWater() || a.getLastHurtByMob() != null && a.tickCount - a.getLastHurtByMobTimestamp() < 200) {
                return false;
            }
            //1.0.5-beta (lot 28) : la chance « par seconde » tiree une fois par seconde (avant : a chaque evaluation, 10 fois par seconde)
            if (a.level().isDay() && now(a) >= p.brain().napUntil && PetReact.due(now(a), p.brain().nextNapRoll)) {
                p.brain().nextNapRoll = now(a) + PetReact.NAP_ROLL;
                if (a.getRandom().nextDouble() < PetRules.NAP_CHANCE) {
                    p.brain().napUntil = now(a) + PetRules.NAP_MIN + a.getRandom().nextInt(PetRules.NAP_MAX - PetRules.NAP_MIN);
                }
            }
            return time();
        }

        @Override
        public boolean canContinueToUse() {
            return autonomous(p) && p.self().getTarget() == null && p.self().hurtTime == 0 && time();
        }

        @Override
        public void start() {
            arrived = false;
        }

        @Override
        public void tick() {
            TamableAnimal a = p.self();
            BlockPos h = PetCore.homeHere(p);
            if (h == null) {
                return;
            }
            Vec3 spot = PetCore.restSpot(a.level(), h, p.isCat());
            if (!arrived) {
                if (a.position().distanceToSqr(spot) < 1.0) {
                    arrived = true;
                    a.getNavigation().stop();
                    a.moveTo(spot.x, spot.y, spot.z, a.getYRot(), 0F);
                    p.setRest(p.isCat() ? Pet.CURLED : Pet.LYING);
                } else if (a.getNavigation().isDone()) {
                    a.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
                }
            } else if (a.position().distanceToSqr(spot) > 2.25) {
                arrived = false;
                p.setRest(Pet.AWAKE);
            }
        }

        @Override
        public void stop() {
            p.setRest(Pet.AWAKE);
            p.brain().napUntil = 0;
        }
    }

    /** Va retrouver son maitre de temps en temps (s'il est dans le territoire ou a proximite) et reste quelques minutes pres de lui. */
    static final class Visit extends Goal {
        final Pet p;

        Visit(Pet p) {
            this.p = p;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Nullable
        Player master() {
            TamableAnimal a = p.self();
            BlockPos h = PetCore.homeHere(p);
            if (h == null || !(a.getOwner() instanceof Player pl) || pl.level() != a.level() || pl.isSpectator()) {
                return null;
            }
            //1.0.5-beta (lot 10) : troupeau senti : le chien va chercher son maitre plus loin (48 blocs de plus)
            double r = PetCore.radius() + 16 + (now(a) < p.brain().herdAlertUntil ? 48 : 0);
            return pl.position().distanceToSqr(Vec3.atCenterOf(h)) < r * r ? pl : null;
        }

        @Override
        public boolean canUse() {
            TamableAnimal a = p.self();
            if (!autonomous(p) || a.getTarget() != null || now(a) < p.brain().nextVisit) {
                return false;
            }
            if (master() == null) {
                p.brain().nextVisit = now(a) + PetReact.VISIT_RETRY;         //1.0.5-beta (lot 28) : 400 -> 100 ticks
                return false;
            }
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return autonomous(p) && p.self().getTarget() == null && now(p.self()) < p.brain().visitUntil && master() != null;
        }

        @Override
        public void start() {
            TamableAnimal a = p.self();
            p.brain().visitUntil = now(a) + PetRules.VISIT_MIN + a.getRandom().nextInt(PetRules.VISIT_MAX - PetRules.VISIT_MIN);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            TamableAnimal a = p.self();
            Player m = master();
            if (m == null) {
                return;
            }
            a.getLookControl().setLookAt(m, 10F, a.getMaxHeadXRot());
            boolean alert = now(a) < p.brain().herdAlertUntil;
            if (a.distanceToSqr(m) > 9 && (a.getNavigation().isDone() || alert && now(a) % 20 == 0)) {
                a.getNavigation().moveTo(m, alert ? 1.3 : 1.1);
            }
            //1.0.5-beta (lot 10) : arrive pres du maitre pendant un troupeau : il aboie pour l'appeler (Maj + clic droit : Chasse mis en avant)
            if (alert && a.distanceToSqr(m) < 16 && a.tickCount % 50 == 0) {
                a.playSound(SoundEvents.WOLF_AMBIENT, 1.2F, 1.1F);
            }
        }

        @Override
        public void stop() {
            TamableAnimal a = p.self();
            int gap = p.isCat() ? PetRules.catVisitInterval(GeneratedPets.CATS[p.breed()].independence()) : PetRules.DOG_VISIT_INTERVAL;
            p.brain().nextVisit = now(a) + gap / 2 + a.getRandom().nextInt(gap);
            a.getNavigation().stop();
        }
    }

    /** Promenade : dans le territoire en autonome ; vanilla sinon (pas en mode Chasse). */
    static final class Roam extends WaterAvoidingRandomStrollGoal {
        final Pet p;

        Roam(Pet p) {
            super(p.self(), p.isCat() ? 0.8 : 1.0, p.isCat() ? 1.0E-5F : 0.001F);
            this.p = p;
        }

        @Override
        public boolean canUse() {
            return p.effectiveMode() != PetMode.HUNT && p.rest() == Pet.AWAKE && super.canUse();
        }

        @Nullable
        @Override
        protected Vec3 getPosition() {
            if (!autonomous(p)) {
                return super.getPosition();
            }
            for (int i = 0; i < 6; i++) {
                Vec3 v = LandRandomPos.getPos(p.self(), 10, 7);
                if (v != null && PetCore.inTerritory(p, v)) {
                    return v;
                }
            }
            BlockPos h = PetCore.homeHere(p);
            return h == null ? null : LandRandomPos.getPosTowards(p.self(), 10, 7, Vec3.atBottomCenterOf(h));
        }
    }

    /** Suit le maitre (vanilla) en mode Suivre, et en mode Chasse quand il n'y a pas de gibier a pister. */
    static final class Follow extends FollowOwnerGoal {
        final Pet p;

        Follow(Pet p, boolean cat) {
            super(p.self(), 1.0, cat ? (float) PetRules.catFollowStart(GeneratedPets.CATS[p.breed()].independence()) : 10.0F, cat ? 5.0F : 2.0F, false);
            this.p = p;
        }

        boolean mode() {
            PetMode m = p.effectiveMode();
            return m == PetMode.FOLLOW || m == PetMode.HUNT;
        }

        @Override
        public boolean canUse() {
            return mode() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return mode() && super.canContinueToUse();
        }
    }

    // ------------------------------------------------------------------ chien

    /**
     * Garde : attaque les predateurs du betail entres dans le territoire (chiens sauvages, renards, vrais loups du lot 7...). 1.0.5-beta (lot
     * 28, retour 35) : la menace est RELEVEE par {@link PetCore#tick} toutes les {@link PetReact#GUARD_SCAN} ticks (et plus ici toutes les 20
     * ticks) ; en Suivre et en Chasse, le chien defend aussi, a {@link PetReact#DEFEND_RANGE} blocs de lui ou de son maitre.
     */
    static final class Guard extends TargetGoal {
        final Pet p;
        @Nullable
        LivingEntity found;

        Guard(Pet p) {
            super(p.self(), false);
            this.p = p;
        }

        @Override
        public boolean canUse() {
            TamableAnimal a = p.self();
            LivingEntity t = p.brain().threat;
            if (t == null || !t.isAlive() || t.isRemoved() || t.level() != a.level() || !a.isTame() || a.isOrderedToSit()
                || p.effectiveMode() == PetMode.SIT) {
                return false;
            }
            found = t;
            return true;
        }

        @Override
        public void start() {
            mob.setTarget(found);
            p.setRest(Pet.AWAKE);
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = mob.getTarget();
            if (t == null || !t.isAlive() || mob instanceof TamableAnimal ta && ta.isOrderedToSit()) {
                return false;
            }
            boolean inRange = autonomous(p) ? PetCore.inTerritory(p, t.position()) : t.distanceToSqr(mob) < 32 * 32;
            return inRange && super.canContinueToUse();
        }

        @Override
        public void stop() {
            super.stop();
            p.brain().threat = null;
        }
    }

    /** Chasse : piste le gibier le plus proche dans la portee de son flair et guide le maitre (avance, attend, marque l'arret, aboie). */
    static final class Hunt extends Goal {
        final Pet p;
        @Nullable
        LivingEntity prey;
        long nextSearch, nextBark, nextPath;

        Hunt(Pet p) {
            this.p = p;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Nullable
        Player master() {
            return p.self().getOwner() instanceof Player pl && pl.level() == p.self().level() && !pl.isSpectator() ? pl : null;
        }

        double range(Player m) {
            return PetRules.scentRange(GeneratedPets.DOGS[p.breed()].scent(), HuntEvents.bonus(m));
        }

        @Nullable
        LivingEntity search(Player m) {
            LivingEntity near = scent(m);
            //1.0.5-beta (lot 10) : rien a portee de flair : il suit la PISTE d'un troupeau (traces au sol) jusqu'a 2 fois son flair
            return near != null ? near : HuntEvents.trailOf(p.self(), range(m) * 2.0);
        }

        @Nullable
        LivingEntity scent(Player m) {
            TamableAnimal a = p.self();
            double r = range(m);
            List<LivingEntity> all = a.level().getEntitiesOfClass(LivingEntity.class, a.getBoundingBox().inflate(r, 16, r), PetCore::game);
            LivingEntity best = null;
            double bd = r * r;
            for (LivingEntity e : all) {
                double d = e.distanceToSqr(a);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
            return best;
        }

        @Override
        public boolean canUse() {
            TamableAnimal a = p.self();
            if (p.effectiveMode() != PetMode.HUNT || a.isOrderedToSit() || a.getTarget() != null || now(a) < nextSearch) {
                return false;
            }
            nextSearch = now(a) + PetReact.HUNT_SCAN;                      //1.0.5-beta (lot 28) : un essai par seconde (avant : 2 s)
            Player m = master();
            prey = m == null ? null : search(m);
            return prey != null;
        }

        @Override
        public boolean canContinueToUse() {
            Player m = master();
            return p.effectiveMode() == PetMode.HUNT && m != null && prey != null && prey.isAlive() && p.self().getTarget() == null
                   && prey.distanceToSqr(p.self()) < Math.pow(range(m) * 2.5, 2);
        }

        @Override
        public void tick() {
            TamableAnimal a = p.self();
            Player m = master();
            if (m == null || prey == null || !prey.isAlive()) {
                return;
            }
            if (now(a) >= nextSearch) {
                nextSearch = now(a) + PetReact.HUNT_RESCAN;
                LivingEntity n = search(m);
                if (n != null) {
                    prey = n;
                }
            }
            double toMaster = a.distanceTo(m), toPrey = a.distanceTo(prey);
            boolean bark = false;
            if (toMaster > PetRules.HUNT_WAIT) {
                //attend son maitre, tourne vers lui, l'appelle
                a.getNavigation().stop();
                a.getLookControl().setLookAt(m, 30F, 30F);
                bark = now(a) >= nextBark && toMaster > PetRules.HUNT_WAIT + 4;
            } else if (toPrey > PetRules.HUNT_POINT) {
                //1.0.5-beta (lot 28) : nouveau chemin toutes les 20 ticks a l'heure (avant : « tickCount % 20 », jamais vrai pour un chien sur
                //deux : ce but n'est pas tenu a chaque tick, son tick() ne tourne qu'aux choix de buts)
                if (a.getNavigation().isDone() || now(a) >= nextPath) {
                    nextPath = now(a) + 20;
                    a.getNavigation().moveTo(prey, toMaster < PetRules.HUNT_WAIT * 0.5 ? 1.15 : 0.9);
                }
                a.getLookControl().setLookAt(prey, 30F, 30F);
            } else {
                //arret : immobile, nez vers le gibier
                a.getNavigation().stop();
                a.getLookControl().setLookAt(prey, 30F, 30F);
                bark = now(a) >= nextBark;
            }
            if (bark) {
                nextBark = now(a) + 60 + a.getRandom().nextInt(40);
                a.playSound(SoundEvents.WOLF_AMBIENT, 1.3F, 0.95F + a.getRandom().nextFloat() * 0.2F);
            }
        }

        @Override
        public void stop() {
            prey = null;
            p.self().getNavigation().stop();
        }
    }

    // ------------------------------------------------------------------ chat

    /** Chasse aux rats : les betes du tag {@code pioneers:cat_prey} dans le territoire (autonome) ou pres du chat (sinon). */
    static final class Prey extends TargetGoal {
        final Pet p;
        @Nullable
        LivingEntity found;

        Prey(Pet p) {
            super(p.self(), true);
            this.p = p;
        }

        @Override
        public boolean canUse() {
            TamableAnimal a = p.self();
            //1.0.5-beta (lot 28, retour 35) : heure du prochain essai (avant : « tickCount % 20 », jamais vrai a un choix de buts pour un chat
            //sur deux : canUse n'est evalue qu'un tick sur deux, selon la parite du temps du serveur + identifiant)
            if (!a.isTame() || a.isOrderedToSit() || !PetReact.due(now(a), p.brain().nextPrey)) {
                return false;
            }
            p.brain().nextPrey = now(a) + PetReact.PREY_SCAN;
            double r = PetRules.ratRange(GeneratedPets.CATS[p.breed()].ratting());
            boolean auto = autonomous(p);
            found = null;
            double bd = r * r;
            for (LivingEntity e : a.level().getEntitiesOfClass(LivingEntity.class, a.getBoundingBox().inflate(r, 6, r),
                  e -> e.isAlive() && e.getType().is(PiPets.CAT_PREY))) {
                double d = e.distanceToSqr(a);
                if (d < bd && (!auto || PetCore.inTerritory(p, e.position()))) {
                    bd = d;
                    found = e;
                }
            }
            return found != null;
        }

        @Override
        public void start() {
            mob.setTarget(found);
            p.setRest(Pet.AWAKE);
            super.start();
        }
    }

    /** Peche : de temps en temps, va a une eau du territoire, guette, attrape un poisson et le rapporte dans son panier. */
    static final class Fish extends Goal {
        final Pet p;
        @Nullable
        BlockPos water, bank;
        int phase, wait, timeout;

        Fish(Pet p) {
            this.p = p;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        /**
         * 1.0.5-beta (lot 28, retour 35 : « les chats ne pechent pas ») : l'intervalle n'est pris qu'au DEPART d'une sortie ; sans panier
         * (ou plein) ou sans eau dans le territoire, nouvel essai {@link PetReact#FISH_RETRY} ticks plus tard (avant : tout l'intervalle, 3
         * a 8 minutes, perdu a chaque echec).
         */
        @Override
        public boolean canUse() {
            TamableAnimal a = p.self();
            if (!fr.pioneers.common.PiCommonConfig.get(fr.pioneers.common.PiCommonConfig.PET_CAT_FISHING, true) || !autonomous(p)
                || a.getTarget() != null || !a.level().isDay() || now(a) < p.brain().nextFish) {
                return false;
            }
            float f = GeneratedPets.CATS[p.breed()].fishing();
            long interval = PetRules.fishInterval(f) * 3L / 4 + a.getRandom().nextInt(Math.max(1, PetRules.fishInterval(f) / 2));
            if (!p.brain().carried.isEmpty()) {
                p.brain().nextFish = now(a) + interval;
                phase = 2;
                return true;
            }
            BlockPos h = PetCore.homeHere(p);
            if (h == null || !a.level().isLoaded(h) || !(a.level().getBlockEntity(h) instanceof CatBasketTile t) || t.full() || !findWater(h)) {
                p.brain().nextFish = now(a) + PetReact.FISH_RETRY;
                return false;
            }
            p.brain().nextFish = now(a) + interval;
            return true;
        }

        /**
         * 1.0.5-beta (lot 28) : le coin de peche retenu s'il est encore bon, sinon la colonne d'eau la plus proche du panier
         * ({@link PetReact#ring} : du centre vers le bord ; avant : 40 colonnes au hasard, une mare de 3 x 3 trouvee 9 fois sur 100).
         */
        boolean findWater(BlockPos h) {
            TamableAnimal a = p.self();
            PetBrain br = p.brain();
            if (br.fishWater != null && br.fishBank != null && a.level().isLoaded(br.fishWater) && waterAt(a.level(), br.fishWater)
                && PetCore.inTerritory(p, Vec3.atCenterOf(br.fishWater)) && standable(a.level(), br.fishBank)) {
                water = br.fishWater;
                bank = br.fishBank;
                phase = 0;
                return true;
            }
            br.fishWater = br.fishBank = null;
            for (int[] o : PetReact.ring(PetCore.radius())) {
                int x = h.getX() + o[0], z = h.getZ() + o[1];
                if (!a.level().isLoaded(new BlockPos(x, h.getY(), z))) {
                    continue;
                }
                int y = a.level().getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                BlockPos w = new BlockPos(x, y, z);
                if (!waterAt(a.level(), w)) {
                    w = w.below();                                               //un nenuphar par-dessus
                    if (!waterAt(a.level(), w)) {
                        continue;
                    }
                }
                if (Math.abs(w.getY() - h.getY()) >= 24) {
                    continue;
                }
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos b = w.relative(d);
                    for (int dy = 0; dy <= 1; dy++) {
                        BlockPos stand = b.above(dy);
                        if (standable(a.level(), stand)) {
                            water = br.fishWater = w;
                            bank = br.fishBank = stand;
                            phase = 0;
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        static boolean waterAt(net.minecraft.world.level.Level l, BlockPos w) {
            return l.getFluidState(w).is(FluidTags.WATER) && l.getFluidState(w).isSource();
        }

        static boolean standable(net.minecraft.world.level.Level l, BlockPos stand) {
            return l.isLoaded(stand) && l.getBlockState(stand.below()).isFaceSturdy(l, stand.below(), Direction.UP) && l.getFluidState(stand).isEmpty()
                   && l.getBlockState(stand).getCollisionShape(l, stand).isEmpty();
        }

        @Override
        public boolean canContinueToUse() {
            return phase < 3 && autonomous(p) && p.self().getTarget() == null && p.self().hurtTime == 0 && timeout < 2400;
        }

        @Override
        public void start() {
            wait = 0;
            timeout = 0;
            p.setRest(Pet.AWAKE);
        }

        @Override
        public void tick() {
            TamableAnimal a = p.self();
            timeout++;
            if (phase == 0 && bank != null) {
                if (a.position().distanceToSqr(Vec3.atBottomCenterOf(bank)) < 1.6) {
                    phase = 1;
                    a.getNavigation().stop();
                } else if (a.getNavigation().isDone() && !a.getNavigation().moveTo(bank.getX() + 0.5, bank.getY(), bank.getZ() + 0.5, 1.0)) {
                    phase = 3;
                }
            } else if (phase == 1 && water != null) {
                a.getLookControl().setLookAt(Vec3.atCenterOf(water));
                a.setInSittingPose(true);
                if (++wait >= PetRules.FISH_WAIT) {
                    a.setInSittingPose(a.isOrderedToSit());
                    a.getJumpControl().jump();                                   //1.0.5-beta (lot 28) : le coup de patte (petit bond)
                    ItemStack caught = catchFish(p, water, false);
                    if (caught.isEmpty()) {
                        phase = 3;
                    } else {
                        p.brain().carried = caught;
                        phase = 2;
                    }
                }
            } else if (phase == 2) {
                BlockPos h = PetCore.homeHere(p);
                if (h == null) {
                    phase = 3;
                    return;
                }
                Vec3 spot = PetCore.restSpot(a.level(), h, true);
                if (a.position().distanceToSqr(spot) < 2.5) {
                    deposit(a, h, p.brain().carried);
                    p.brain().carried = ItemStack.EMPTY;
                    phase = 3;
                } else if (a.getNavigation().isDone()) {
                    a.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
                }
            }
        }

        static void depositIn(TamableAnimal a, BlockPos h, ItemStack s) {
            if (s.isEmpty()) {
                return;
            }
            ItemStack rest = a.level().getBlockEntity(h) instanceof CatBasketTile t ? ItemHandlerHelper.insertItem(t.items, s, false) : s;
            if (!rest.isEmpty()) {
                a.level().addFreshEntity(new ItemEntity(a.level(), h.getX() + 0.5, h.getY() + 0.5, h.getZ() + 0.5, rest));
            }
            a.playSound(SoundEvents.CAT_PURR, 0.8F, 1.0F);
        }

        @Override
        public void stop() {
            TamableAnimal a = p.self();
            a.setInSittingPose(a.isOrderedToSit());
            a.getNavigation().stop();
            phase = 3;
        }
    }

    // ------------------------------------------------------------------ crochets des GameTests (et du code ci-dessus)

    /** Hors de son territoire en autonome : le but « Home » le ramenera. */
    public static boolean wantsHome(Pet p) {
        return autonomous(p) && !PetCore.inTerritory(p, p.self().position());
    }

    /**
     * Une prise du chat au bord de l'eau {@code w} : d'abord un vrai poisson du coin (population et plafonds des poissons respectes), sinon un
     * poisson de l'habitat ({@code FishSpawns.pickWild}, petite taille) ; {@code force} : sans le tirage de l'aptitude.
     */
    public static ItemStack catchFish(Pet p, BlockPos w, boolean force) {
        TamableAnimal a = p.self();
        if (!(a.level() instanceof ServerLevel sl)) {
            return ItemStack.EMPTY;
        }
        sl.sendParticles(ParticleTypes.SPLASH, w.getX() + 0.5, w.getY() + 1.0, w.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.1);
        a.playSound(SoundEvents.GENERIC_SPLASH, 0.6F, 1.4F);
        if (!force && !p.brain().testCatch && a.getRandom().nextDouble() >= PetRules.fishChance(GeneratedPets.CATS[p.breed()].fishing())) {
            return ItemStack.EMPTY;
        }
        List<PiFish> near = sl.getEntitiesOfClass(PiFish.class, new AABB(w).inflate(PetRules.FISH_REACH));
        if (!near.isEmpty()) {
            PiFish f = near.get(a.getRandom().nextInt(near.size()));
            ItemStack s = FishItems.catchOf(f);
            f.discard();
            return s;
        }
        //1.0.5-beta (lot 28, retour 35) : plus de second tirage « une fois sur deux » sans vrai poisson : la chance de l'aptitude (35 a 85 %)
        //est la seule (avant : 17 a 42 % dans une mare sans poisson)
        int sp = FishSpawns.pickWild(sl, w, a.getRandom());   //1.0.5-beta (lot 25) : jamais un requin au bec du chat
        if (sp < 0) {
            return ItemStack.EMPTY;
        }
        FishDef d = GeneratedFish.SPECIES[sp];
        int cm = d.cmMin() + a.getRandom().nextInt(Math.max(1, (d.cmMax() - d.cmMin()) / 3 + 1));
        return FishItems.of(sp, cm);
    }

    /** Depose le poisson porte dans le panier (le reste tombe a cote). */
    public static void deposit(TamableAnimal a, BlockPos h, ItemStack s) {
        Fish.depositIn(a, h, s);
    }
}

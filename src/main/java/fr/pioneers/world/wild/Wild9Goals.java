package fr.pioneers.world.wild;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 9) : buts de la mer et des cotes.
 * <ul>
 *     <li>{@link Breathe} : remonter respirer quand le souffle baisse (droit vers la surface, ou vers un trou dans la glace) ; un cetace
 *     reste en surface le temps de souffler ;</li>
 *     <li>{@link Cruise} : croisiere en eau libre (cap qui change doucement, cible verifiee : eau libre et trajet degage a la largeur de la
 *     bete ; jamais dans les hauts-fonds pour les geants ; apres un souffle, plongee) ;</li>
 *     <li>{@link School} : bancs, groupes, couples (raies, requins-marteaux, orques, baleines) ;</li>
 *     <li>{@link SwimAttack} : poursuite et morsure dans l'eau (requins, orque, cachalot), {@link SharkTarget} et {@link OrcaHunt} : choix de
 *     la proie ({@link Wild9Rules}) ;</li>
 *     <li>{@link SwimFlee} : fuite a la nage (raie, baleines paisibles, requin-baleine) ;</li>
 *     <li>{@link Dive} : a l'approche d'un joueur, phoques, otaries, manchots et pingouins gagnent l'eau et plongent ; {@link Shore} : ils
 *     regagnent la cote ; {@link Bask} : ils s'y prelassent ; {@link FishDive} : manchots et pingouins plongent pecher.</li>
 * </ul>
 */
public final class Wild9Goals {

    private Wild9Goals() {
    }

    /** Rayon d'eau libre exige autour d'une bete (moitie de sa largeur, au moins un bloc). */
    static double room(MarineAnimal m) {
        return Math.max(1.0, Math.floor(m.getBbWidth() * 0.5));
    }

    // ------------------------------------------------------------------ respiration

    public static final class Breathe extends Goal {

        final MarineAnimal m;
        @Nullable
        Vec3 air;
        int left;

        public Breathe(MarineAnimal m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return m.isInWater() && (m.needsAir() || m.blowLeft > 0);
        }

        @Override
        public boolean canContinueToUse() {
            return m.isInWater() && left > 0 && (m.getAirSupply() < m.getMaxAirSupply() || m.blowLeft > 0);
        }

        @Override
        public void start() {
            left = 400;
            air = find();
        }

        /** Point de respiration : la surface au-dessus, sinon une colonne d'eau a l'air libre a moins de 12 blocs. */
        @Nullable
        Vec3 find() {
            Level l = m.level();
            BlockPos p = m.blockPosition();
            for (int r = 0; r <= 12; r += 3) {
                for (int a = 0; a < (r == 0 ? 1 : 8); a++) {
                    double ang = a * Math.PI / 4;
                    BlockPos c = p.offset((int) Math.round(Math.cos(ang) * r), 0, (int) Math.round(Math.sin(ang) * r));
                    //1.0.5-beta (lot 21) : jamais de troncon charge par l'IA (getBlockState d'un troncon absent le charge en synchrone)
                    if (!l.isLoaded(c) || !l.getFluidState(c).is(FluidTags.WATER)) {
                        continue;
                    }
                    int top = MarineAnimal.surfaceY(l, c);
                    BlockPos above = new BlockPos(c.getX(), top, c.getZ());
                    if (l.getBlockState(above).isAir()) {
                        return new Vec3(c.getX() + 0.5, top - m.getBbHeight() * 0.6, c.getZ() + 0.5);
                    }
                }
            }
            return null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            left--;
            if (m.blowLeft > 0) {
                m.getNavigation().stop();
                m.getMoveControl().setWantedPosition(m.getX(), m.getY(), m.getZ(), 0.2);
                m.setXRot(Mth.approach(m.getXRot(), 0F, 2F));
                return;
            }
            if (air == null || m.tickCount % 40 == 0) {
                air = find();
            }
            if (air != null) {
                m.swimTo(air, 1.5);
            }
        }
    }

    // ------------------------------------------------------------------ croisiere

    public static final class Cruise extends Goal {

        final MarineAnimal m;
        final double speed;
        @Nullable
        Vec3 to;
        int left;

        public Cruise(MarineAnimal m, double speed) {
            this.m = m;
            this.speed = speed;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!m.isInWater() || m.getTarget() != null || m.alarmed()) {
                return false;
            }
            if (!m.aquatic() && m.getRandom().nextInt(30) != 0) {
                return false;
            }
            to = pick();
            return to != null;
        }

        @Nullable
        Vec3 pick() {
            Level l = m.level();
            double r = room(m);
            BlockPos here = m.blockPosition();
            int top = MarineAnimal.surfaceY(l, here);
            int depth = MarineAnimal.depth(l, here);
            double lo = top - depth + r + 1, hi = top - m.getBbHeight() - 1.0;
            for (int i = 0; i < 10; i++) {
                //1.0.5-beta (lot 13) : etapes longues devant soi, profondeur dans la bande d'eau (Wild9Rules.waypoint, comme le banc)
                double[] w = Wild9Rules.waypoint(m.getRandom()::nextDouble, m.getX(), m.getY(), m.getZ(), m.getYRot(), m.length(), lo,
                      Math.max(lo, hi), m.diveLeft > 0, i);
                Vec3 t = new Vec3(w[0], w[1], w[2]);
                if (!MarineAnimal.openWater(l, t, r) || !MarineAnimal.clearPath(l, m.position(), t, r)) {
                    continue;
                }
                if (m.aquatic() && MarineAnimal.depth(l, BlockPos.containing(t)) < Math.max(3, GeneratedWild9.DEPTH[m.j()] / 2)) {
                    continue;                                                               //les geants evitent les hauts-fonds
                }
                return t;
            }
            return null;
        }

        @Override
        public void start() {
            left = 600;
            m.swimTo(to, speed);
            if (m.diveLeft > 0) {
                m.setAction(Wild9Rules.DIVE);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && m.isInWater() && m.getTarget() == null && !m.alarmed() && to != null && !m.needsAir();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            left--;
            //1.0.5-beta (lot 13) : arrivee a une fraction du corps : le point suivant est choisi tout de suite, sans s'arreter (avant : arret
            //net, puis nouvelle cible au prochain choix des buts : baleines souvent immobiles)
            //1.0.5-beta (lot 28) : le point suivant est pris AVANT la zone de freinage (Wild9Rules.repick ; avant : 2 rayons d'arrivee, dedans)
            if (to != null && m.position().distanceToSqr(to) < Math.pow(Wild9Rules.repick(m.length()), 2)) {
                Vec3 next = pick();
                if (next == null) {
                    to = null;
                    return;
                }
                to = next;
                m.swimTo(to, speed);
            } else if (to != null && m.tickCount % 20 == 0) {
                m.swimTo(to, speed);
            }
            if (m.action() == Wild9Rules.DIVE && m.diveLeft <= 0) {
                m.setAction(WildRules.IDLE);
            }
        }

        @Override
        public void stop() {
            if (m.action() == Wild9Rules.DIVE) {
                m.setAction(WildRules.IDLE);
            }
        }
    }

    // ------------------------------------------------------------------ bancs

    public static final class School extends Goal {

        final MarineAnimal m;
        final double keep;
        @Nullable
        Vec3 center;
        int cool;

        public School(MarineAnimal m, double keep) {
            this.m = m;
            this.keep = keep;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (--cool > 0 || !m.isInWater() || m.getTarget() != null || m.alarmed()) {
                return false;
            }
            cool = 30 + m.getRandom().nextInt(30);
            List<MarineAnimal> kin = m.school(32);
            if (kin.isEmpty()) {
                return false;
            }
            double x = 0, y = 0, z = 0;
            for (MarineAnimal w : kin) {
                x += w.getX();
                y += w.getY();
                z += w.getZ();
            }
            center = new Vec3(x / kin.size(), y / kin.size(), z / kin.size());
            return m.position().distanceTo(center) > keep && MarineAnimal.openWater(m.level(), center, room(m));
        }

        @Override
        public void start() {
            m.swimTo(center, 1.1);
        }

        @Override
        public boolean canContinueToUse() {
            return center != null && m.isInWater() && m.getTarget() == null && !m.alarmed() && m.position().distanceTo(center) > keep * 0.5
                   && !m.needsAir();
        }

        @Override
        public void tick() {
            if (m.tickCount % 20 == 0 && center != null) {
                m.swimTo(center, 1.1);
            }
        }
    }

    // ------------------------------------------------------------------ attaque

    public static final class SwimAttack extends Goal {

        final MarineAnimal m;
        final double speed;
        int cool, dry;

        public SwimAttack(MarineAnimal m, double speed) {
            this.m = m;
            this.speed = speed;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = m.getTarget();
            return t != null && t.isAlive() && m.isInWater();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = m.getTarget();
            return t != null && t.isAlive() && m.isInWater() && m.distanceTo(t) < Wild9Rules.SHARK_GIVE_UP && dry < 80
                   && !(t instanceof Player p && (p.isCreative() || p.isSpectator()));
        }

        @Override
        public void start() {
            cool = 0;
            dry = 0;
            m.setAction(WildRules.CHARGE);
        }

        @Override
        public void stop() {
            if (m.aquatic() || m.isInWater()) {
                m.setAction(WildRules.IDLE);
            }
            LivingEntity t = m.getTarget();
            if (t != null && (!t.isAlive() || dry >= 80 || m.distanceTo(t) >= Wild9Rules.SHARK_GIVE_UP)) {
                m.setTarget(null);
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            //1.0.5-beta (lot 21) : but tenu a chaque tick : la cible peut mourir ou disparaitre entre deux verifications du selecteur
            LivingEntity t = m.getTarget();
            if (t == null || !t.isAlive()) {
                return;
            }
            dry = MarineAnimal.wet(t) ? 0 : dry + 1;
            Vec3 aim = t.position().add(0, t.getBbHeight() * 0.4, 0);
            m.swimTo(aim, speed, true);                                     //1.0.5-beta (lot 28) : poursuite sans arret pres de la proie
            double reach = m.getBbWidth() * 0.5 + t.getBbWidth() * 0.5 + 1.2 + m.length() * 0.15;
            if (cool > 0) {
                cool--;
            }
            if (m.action() == Wild9Rules.BITE && cool < 10) {
                m.setAction(WildRules.CHARGE);
            }
            if (cool <= 0 && m.position().add(0, m.getBbHeight() * 0.5, 0).distanceTo(aim) <= reach) {
                cool = 20;
                m.setAction(Wild9Rules.BITE);
                if (m.doHurtTarget(t)) {
                    m.strikes++;
                    m.playOwn(Wild9Rules.shark(m.kind) ? "bite" : "attack");
                }
            }
        }
    }

    /** Proie des requins : le nageur (selon l'espece : a vue ou s'il saigne), sinon de temps en temps un poisson. */
    public static final class SharkTarget extends Goal {

        final MarineAnimal m;

        long nextTry;

        public SharkTarget(MarineAnimal m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            //1.0.5-beta (lot 28) : heure du prochain essai (avant : « tickCount % 10 » : canUse n'etant evalue qu'un tick sur deux, selon la
            //parite du temps du serveur + identifiant, un requin sur deux ne voyait JAMAIS le nageur ni les poissons)
            if (m.getTarget() != null || m.level().getGameTime() < nextTry || !m.isInWater()) {
                return false;
            }
            nextTry = m.level().getGameTime() + 10;
            int range = m.species().charge();
            Player best = null;
            double bd = 1E9;
            for (Player p : m.level().players()) {
                if (!WildAnimal.scary(p)) {
                    continue;
                }
                double d = m.distanceTo(p);
                boolean bleeding = Wild9Rules.bleeding(p.getHealth(), p.getMaxHealth(), Wild9Events.ticksSinceHurt(p));
                if (d < bd && Wild9Rules.sharkAttacks(m.kind, range, MarineAnimal.wet(p), bleeding, d) && m.hasLineOfSight(p)) {
                    best = p;
                    bd = d;
                }
            }
            if (best != null) {
                m.setTarget(best);
                return false;
            }
            if (m.fishHunt > 0) {
                return false;
            }
            List<AbstractFish> fish = m.level().getEntitiesOfClass(AbstractFish.class, m.getBoundingBox().inflate(Wild9Rules.FISH_RANGE),
                  f -> f.isAlive() && !f.hasCustomName() && !f.fromBucket());                   //jamais un poisson lache par un joueur
            boolean claim = PiWild7.claimed(m.level(), m.blockPosition());
            if (Wild9Rules.huntsFish(m.kind, claim, fish.size(), 0)) {
                AbstractFish f = fish.get(m.getRandom().nextInt(fish.size()));
                if (!PiWild7.claimed(m.level(), f.blockPosition())) {
                    m.setTarget(f);
                }
            }
            m.fishHunt = Wild9Rules.FISH_HUNT_MIN + m.getRandom().nextInt(Wild9Rules.FISH_HUNT_MAX - Wild9Rules.FISH_HUNT_MIN);
            return false;
        }
    }

    /** Proie de l'orque : phoques, otaries, manchots, pingouins, requins, dans l'eau ; une proie toutes les {@link Wild9Rules#ORCA_FED} ticks. */
    public static final class OrcaHunt extends Goal {

        final MarineAnimal m;

        long nextTry;

        public OrcaHunt(MarineAnimal m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (m.kind != Wild9Rules.ORCA || m.getTarget() != null || m.level().getGameTime() < nextTry || m.fed > 0 || m.isBaby()) {
                return false;
            }
            nextTry = m.level().getGameTime() + 20;                        //1.0.5-beta (lot 28) : avant « tickCount % 20 » (une orque sur deux)
            List<LivingEntity> prey = m.level().getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(Wild9Rules.ORCA_HUNT_RANGE),
                  e -> e != m && e.isAlive() && MarineAnimal.wet(e) && !e.hasCustomName() && Wild9Rules.orcaPrey(e.getType().is(PiWild9.ORCA_PREY),
                        e instanceof WildAnimal w ? w.kind : -1, m.fed));
            if (!prey.isEmpty()) {
                prey.sort((a, b) -> Double.compare(m.distanceToSqr(a), m.distanceToSqr(b)));
                m.setTarget(prey.get(0));
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ fuite a la nage

    public static final class SwimFlee extends Goal {

        final MarineAnimal m;

        public SwimFlee(MarineAnimal m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!m.isInWater()) {
                return false;
            }
            Player p = WildGoals.nearestScary(m, Math.max(1, m.species().flee()));
            if (p != null && m.species().flee() > 0) {
                m.alarm(p.position(), WildRules.ALARM_TICKS, true);
            }
            return m.alarmed();
        }

        @Override
        public boolean canContinueToUse() {
            return m.alarmed() && m.isInWater();
        }

        @Override
        public void start() {
            m.setAction(WildRules.FLEE);
        }

        @Override
        public void stop() {
            m.setAction(WildRules.IDLE);
        }

        @Override
        public void tick() {
            if (m.threat == null || m.tickCount % 10 != 0) {
                return;
            }
            Vec3 away = m.position().subtract(m.threat);
            if (away.lengthSqr() < 1E-4) {
                away = new Vec3(1, 0, 0);
            }
            away = away.normalize();
            double r = room(m);
            for (int i = 0; i < 6; i++) {
                double yaw = Math.atan2(away.z, away.x) + (i - 2.5) * 0.4;
                Vec3 t = m.position().add(Math.cos(yaw) * 10, -1 - m.getRandom().nextInt(3), Math.sin(yaw) * 10);
                if (MarineAnimal.openWater(m.level(), t, r)) {
                    m.swimTo(t, m.species().fleeSpeed());
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ cote : plongee, retour, prelassement, peche

    /** Point d'eau le plus proche (a moins de {@code r} blocs), un peu sous la surface. */
    @Nullable
    static BlockPos nearestWater(Level l, BlockPos p, int r) {
        BlockPos best = null;
        double bd = 1E9;
        for (int dx = -r; dx <= r; dx += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
                if (!l.isLoaded(p.offset(dx, 0, dz))) {
                    continue;                                                  //1.0.5-beta (lot 21) : troncon absent : ignore
                }
                for (int dy = -3; dy <= 1; dy++) {
                    BlockPos c = p.offset(dx, dy, dz);
                    if (l.getFluidState(c).is(FluidTags.WATER) && l.getFluidState(c.below()).is(FluidTags.WATER)) {
                        double d = c.distSqr(p);
                        if (d < bd) {
                            bd = d;
                            best = c;
                        }
                        break;
                    }
                }
            }
        }
        return best;
    }

    /** Une place sur la cote : bloc plein sous un bloc d'air, au bord de l'eau, a moins de {@code r} blocs. */
    @Nullable
    static BlockPos nearestShore(Level l, BlockPos p, int r, @Nullable BlockPos prefer) {
        BlockPos best = null;
        double bd = 1E9;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!l.isLoaded(p.offset(dx, 0, dz))) {
                    continue;                                                  //1.0.5-beta (lot 21) : troncon absent : ignore
                }
                for (int dy = -2; dy <= 4; dy++) {
                    BlockPos c = p.offset(dx, dy, dz);
                    if (l.getBlockState(c).isAir() && l.getFluidState(c).isEmpty() && l.getBlockState(c.above()).isAir()
                        && l.getBlockState(c.below()).isFaceSturdy(l, c.below(), net.minecraft.core.Direction.UP)) {
                        if (!MarineAnimal.nearWater(l, c, 2)) {
                            continue;
                        }
                        double d = c.distSqr(prefer != null ? prefer : p);
                        if (d < bd) {
                            bd = d;
                            best = c;
                        }
                    }
                }
            }
        }
        return best;
    }

    public static final class Dive extends Goal {

        final MarineAnimal m;
        @Nullable
        BlockPos water;
        boolean splashed;

        public Dive(MarineAnimal m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Nullable
        Player scare() {
            Player p = m.level().getNearestPlayer(m.getX(), m.getY(), m.getZ(), m.species().flee() + 2, WildAnimal::scary);
            return p != null && Wild9Rules.dives(m.species().flee(), m.distanceTo(p), p.isCrouching()) ? p : null;
        }

        @Override
        public boolean canUse() {
            Player p = scare();
            if (p != null) {
                m.alarm(p.position(), WildRules.ALARM_TICKS, true);
                return true;
            }
            return m.alarmed();
        }

        @Override
        public boolean canContinueToUse() {
            return m.alarmed() || scare() != null;
        }

        @Override
        public void start() {
            splashed = m.isInWater();
            water = m.isInWater() ? null : nearestWater(m.level(), m.blockPosition(), Wild9Rules.WATER_RANGE);
            m.setAction(m.isInWater() ? Wild9Rules.DIVE : WildRules.FLEE);
            if (water != null) {
                m.getNavigation().moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, m.species().fleeSpeed());
            }
        }

        @Override
        public void stop() {
            m.setAction(m.isInWater() ? Wild9Rules.SWIM : WildRules.IDLE);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Player p = scare();
            if (p != null) {
                m.alarm(p.position(), WildRules.ALARM_TICKS, false);
            }
            if (!m.isInWater()) {
                if (water == null || m.getNavigation().isDone()) {
                    water = nearestWater(m.level(), m.blockPosition(), Wild9Rules.WATER_RANGE);
                    if (water != null) {
                        m.getNavigation().moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, m.species().fleeSpeed());
                    } else if (m.threat != null) {
                        Vec3 away = m.position().subtract(m.threat).normalize().scale(8).add(m.position());
                        m.getNavigation().moveTo(away.x, away.y, away.z, m.species().fleeSpeed());
                    }
                }
                return;
            }
            if (!splashed) {
                splashed = true;
                m.playOwn("splash");
                m.dives++;
            }
            m.setAction(Wild9Rules.DIVE);
            if (m.tickCount % 10 == 0) {
                //sous l'eau, a l'ecart : 3 a 5 blocs plus bas, loin de la menace
                Vec3 away = m.threat == null ? Vec3.ZERO : m.position().subtract(m.threat).multiply(1, 0, 1);
                away = away.lengthSqr() < 1E-4 ? new Vec3(1, 0, 0) : away.normalize();
                Level l = m.level();
                int deep = MarineAnimal.depth(l, m.blockPosition());
                Vec3 t = m.position().add(away.scale(6)).add(0, -Math.min(4, Math.max(0, deep - 2)), 0);
                if (!MarineAnimal.openWater(l, t, 0)) {
                    t = m.position().add(0, -Math.min(3, Math.max(0, deep - 2)), 0);
                }
                m.swimTo(t, m.species().fleeSpeed());
            }
        }
    }

    public static final class Shore extends Goal {

        final MarineAnimal m;
        @Nullable
        BlockPos spot;
        int left;

        public Shore(MarineAnimal m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!m.isInWater() || m.alarmed() || m.getTarget() != null) {
                return false;
            }
            boolean want = m instanceof Penguin pg && pg.hasFish() || m.getRandom().nextInt(m.level().isDay() ? 160 : 400) == 0;
            if (!want) {
                return false;
            }
            spot = nearestShore(m.level(), m.blockPosition(), Wild9Rules.SHORE_RANGE, m.home);
            return spot != null;
        }

        @Override
        public void start() {
            left = 400;
            m.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && spot != null && !m.alarmed() && (m.isInWater() || !m.getNavigation().isDone());
        }

        @Override
        public void tick() {
            left--;
            if (spot == null) {
                return;
            }
            if (m.tickCount % 40 == 0 && m.getNavigation().isDone()) {
                m.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
            }
        }
    }

    public static final class Bask extends Goal {

        final MarineAnimal m;
        int left;

        public Bask(MarineAnimal m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return !m.isInWater() && m.onGround() && !m.alarmed() && m.getTarget() == null && !m.isInLove()
                   && m.getRandom().nextInt(m.level().isDay() ? 60 : 200) == 0;
        }

        @Override
        public void start() {
            left = Wild9Rules.BASK_MIN + m.getRandom().nextInt(Wild9Rules.BASK_MAX - Wild9Rules.BASK_MIN);
            m.getNavigation().stop();
            m.setAction(m.kind == Wild9Rules.SEA_LION && m.getRandom().nextInt(4) == 0 ? WildRules.DISPLAY : WildRules.REST);
            if (m.action() == WildRules.DISPLAY) {
                left = 80 + m.getRandom().nextInt(80);
                m.playOwn("ambient");
            }
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && !m.alarmed() && !m.isInWater() && m.getTarget() == null;
        }

        @Override
        public void tick() {
            left--;
        }

        @Override
        public void stop() {
            m.setAction(WildRules.IDLE);
        }
    }

    /** Manchot, pingouin : gagne l'eau, plonge pecher quelques secondes, revient au bec un poisson (le retour : {@link Shore}). */
    public static final class FishDive extends Goal {

        final Penguin m;
        @Nullable
        BlockPos water;
        int left, under;

        public FishDive(Penguin m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (m.hasFish() || m.fishCooldown > 0 || m.alarmed() || m.isBaby()) {
                return false;
            }
            water = m.isInWater() ? m.blockPosition() : nearestWater(m.level(), m.blockPosition(), 16);
            return water != null;
        }

        @Override
        public void start() {
            left = 600;
            under = 0;
            if (!m.isInWater()) {
                m.getNavigation().moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, 1.1);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && !m.hasFish() && !m.alarmed();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            left--;
            if (!m.isInWater()) {
                if (m.getNavigation().isDone() && water != null) {
                    m.getNavigation().moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, 1.1);
                }
                return;
            }
            if (under == 0) {
                m.playOwn("splash");
            }
            under++;
            m.setAction(Wild9Rules.DIVE);
            if (m.tickCount % 15 == 0) {
                Level l = m.level();
                int deep = MarineAnimal.depth(l, m.blockPosition());
                double yaw = m.getRandom().nextDouble() * Math.PI * 2;
                Vec3 t = m.position().add(Math.cos(yaw) * 4, -Math.min(5, Math.max(0, deep - 2)), Math.sin(yaw) * 4);
                if (MarineAnimal.openWater(l, t, 0)) {
                    m.swimTo(t, 1.2);
                }
            }
            if (under >= Wild9Rules.CATCH_TICKS) {
                m.setFish(true);
            }
        }

        @Override
        public void stop() {
            m.fishCooldown = Wild9Rules.FISH_MIN + m.getRandom().nextInt(Wild9Rules.FISH_MAX - Wild9Rules.FISH_MIN);
            m.setAction(m.isInWater() ? Wild9Rules.SWIM : WildRules.IDLE);
        }
    }
}

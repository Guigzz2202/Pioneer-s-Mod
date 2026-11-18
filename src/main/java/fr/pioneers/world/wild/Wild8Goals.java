package fr.pioneers.world.wild;

import fr.pioneers.farm.PiFarm;
import fr.pioneers.farm.tree.FruitLeavesBlock;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 8) : buts de la savane et de la jungle.
 * <ul>
 *     <li>{@link Idle} : repos (le lion couche presque toute la journee), broutage, animation propre (le lion rugit, l'elephant barrit trompe
 *     levee, le rhinoceros secoue la tete, la girafe balance le cou, le singe se toilette, le tigre baille), le tigre se tapit dans les
 *     hautes herbes ;</li>
 *     <li>{@link Browse} : la girafe broute la cime des acacias, cou tendu ;</li>
 *     <li>{@link Stalk} : le tigre approche tapi, lentement, puis bondit ; {@link FearFire} : il fuit le feu ;</li>
 *     <li>{@link Forage} : le singe va cueillir les bananes mures des bananiers du mod, ou grimpe dans la canopee ; {@link Eat} : il
 *     s'assoit, epluche, mange et jette la peau.</li>
 * </ul>
 */
public final class Wild8Goals {

    private Wild8Goals() {
    }

    /** Bete qui craint le feu (tigre) : d'ou vient la peur, ou null. */
    public interface Fearful {
        @Nullable
        Vec3 fear();
    }

    // ------------------------------------------------------------------ repos, broutage, animation propre

    public static final class Idle extends Goal {

        final WildAnimal a;
        int left, act;

        public Idle(WildAnimal a) {
            this.a = a;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (a.alarmed() || a.getTarget() != null || !a.onGround() || a.isInWater() || a.isInLove()
                || a instanceof Monkey m && (m.climbing() || m.action() == Wild8Rules.EAT) || a instanceof Giraffe g && g.kicking()) {
                return false;
            }
            boolean night = a.level().isNight();
            int k = a.kind;
            int odds = k == Wild8Rules.LION ? 30 : k == Wild8Rules.TIGER ? 60 : 120;
            if (a.getRandom().nextInt(odds) != 0) {
                return false;
            }
            double r = a.getRandom().nextDouble();
            switch (k) {
                //le lion est faineant : couche la plupart du temps, surtout le jour
                case Wild8Rules.LION -> act = r < (night ? 0.55 : 0.85) ? WildRules.REST : r < 0.93 ? WildRules.DISPLAY : WildRules.ALERT;
                case Wild8Rules.TIGER -> act = a instanceof Tiger t && t.inTallGrass() && r < 0.6 ? Wild8Rules.STALK
                                                                                              : r < 0.55 ? WildRules.REST : r < 0.8 ? WildRules.DISPLAY : WildRules.ALERT;
                case Wild8Rules.ELEPHANT -> act = r < (night ? 0.4 : 0.1) ? WildRules.REST : r < 0.85 ? WildRules.GRAZE : WildRules.DISPLAY;
                case Wild8Rules.RHINO -> act = r < (night ? 0.5 : 0.2) ? WildRules.REST : r < 0.88 ? WildRules.GRAZE : WildRules.DISPLAY;
                case Wild8Rules.GIRAFFE -> act = r < (night ? 0.3 : 0.05) ? WildRules.REST : r < 0.75 ? WildRules.GRAZE : r < 0.9
                                                                                                                        ? WildRules.DISPLAY : WildRules.ALERT;
                case Wild8Rules.MONKEY -> act = r < (night ? 0.6 : 0.2) ? WildRules.REST : r < 0.6 ? WildRules.DISPLAY : WildRules.ALERT;
                default -> act = WildRules.GRAZE;
            }
            left = switch (act) {
                case WildRules.REST -> (k == Wild8Rules.LION ? 1200 : 400) + a.getRandom().nextInt(1000);
                case Wild8Rules.STALK -> 200 + a.getRandom().nextInt(400);
                case WildRules.DISPLAY -> 50 + a.getRandom().nextInt(50);
                default -> 60 + a.getRandom().nextInt(100);
            };
            return true;
        }

        @Override
        public void start() {
            a.getNavigation().stop();
            a.setAction(act);
            if (act == WildRules.DISPLAY) {
                switch (a.kind) {
                    case Wild8Rules.LION, Wild8Rules.TIGER -> a.playOwn("roar");
                    case Wild8Rules.ELEPHANT -> a.playOwn("trumpet");
                    case Wild8Rules.MONKEY, Wild8Rules.RHINO -> a.playAmbientSound();
                    default -> {
                    }
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && !a.alarmed() && a.getTarget() == null && !a.isInWater();
        }

        @Override
        public void tick() {
            left--;
        }

        @Override
        public void stop() {
            if (a.action() == act) {
                a.setAction(WildRules.IDLE);
            }
        }
    }

    // ------------------------------------------------------------------ girafe : la cime des acacias

    public static final class Browse extends Goal {

        final Giraffe g;
        @Nullable
        BlockPos leaf;
        int left, cool;

        public Browse(Giraffe g) {
            this.g = g;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        /** Feuilles a brouter : tag pioneers:giraffe_browse, de 3 a 4 blocs au-dessus des sabots, a 8 blocs. */
        @Nullable
        BlockPos find() {
            BlockPos c = g.blockPosition();
            BlockPos best = null;
            double bd = 1E9;
            int r = Wild8Rules.BROWSE_RANGE;
            //1.0.5-beta (lot 29) : palettes des sections d'abord (exact)
            if (!fr.pioneers.core.block.ScanFilter.mayContain(g.level(), c.offset(-r, Wild8Rules.BROWSE_LOW, -r), c.offset(r, Wild8Rules.BROWSE_HIGH, r),
                  s -> s.is(PiWild8.GIRAFFE_BROWSE))) {
                return null;
            }
            for (BlockPos b : BlockPos.betweenClosed(c.offset(-r, Wild8Rules.BROWSE_LOW, -r), c.offset(r, Wild8Rules.BROWSE_HIGH, r))) {
                if (g.level().isLoaded(b) && g.level().getBlockState(b).is(PiWild8.GIRAFFE_BROWSE) && g.level().getBlockState(b.below()).isAir()) {
                    double d = b.distSqr(c);
                    if (d < bd) {
                        bd = d;
                        best = b.immutable();
                    }
                }
            }
            return best;
        }

        @Override
        public boolean canUse() {
            if (--cool > 0 || g.alarmed() || g.kicking() || g.isBaby() || !g.onGround() || g.getRandom().nextInt(80) != 0) {
                return false;
            }
            cool = 100;
            leaf = find();
            return leaf != null;
        }

        @Override
        public void start() {
            left = Wild8Rules.BROWSE_MIN + g.getRandom().nextInt(Wild8Rules.BROWSE_MAX - Wild8Rules.BROWSE_MIN);
            //se placer a cote de la colonne des feuilles (la tete passe dessous, au bout du cou)
            Vec3 to = Vec3.atBottomCenterOf(leaf).subtract(0, leaf.getY() - g.getY(), 0);
            g.getNavigation().moveTo(to.x, to.y, to.z, 0.8);
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && leaf != null && !g.alarmed() && !g.kicking() && g.level().isLoaded(leaf) && g.level().getBlockState(leaf).is(PiWild8.GIRAFFE_BROWSE);
        }

        @Override
        public void tick() {
            left--;
            if (leaf == null) {
                return;                                                    //1.0.5-beta (lot 21)
            }
            double dx = leaf.getX() + 0.5 - g.getX(), dz = leaf.getZ() + 0.5 - g.getZ();
            g.getLookControl().setLookAt(leaf.getX() + 0.5, leaf.getY() + 0.5, leaf.getZ() + 0.5, 20F, 40F);
            if (dx * dx + dz * dz < 2.6) {
                g.getNavigation().stop();
                g.setAction(Wild8Rules.BROWSE);
                if (left % 30 == 0) {
                    g.level().playSound(null, leaf, SoundEvents.AZALEA_LEAVES_HIT, SoundSource.NEUTRAL, 0.5F, 0.8F);
                }
            } else if (g.getNavigation().isDone()) {
                left = Math.min(left, 20);
            }
        }

        @Override
        public void stop() {
            if (g.action() == Wild8Rules.BROWSE) {
                g.setAction(WildRules.IDLE);
            }
            leaf = null;
        }
    }

    // ------------------------------------------------------------------ tigre : peur du feu, affut et bond

    public static final class FearFire extends Goal {

        final WildAnimal w;
        int next;

        public <T extends WildAnimal & Fearful> FearFire(T w) {
            this.w = w;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
        }

        @Nullable
        Vec3 fear() {
            return ((Fearful) w).fear();
        }

        @Override
        public boolean canUse() {
            return fear() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return fear() != null;
        }

        @Override
        public void start() {
            w.setTarget(null);
            w.setAction(WildRules.FLEE);
            next = 0;
        }

        @Override
        public void stop() {
            w.setAction(WildRules.IDLE);
            w.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Vec3 from = fear();
            if (from == null) {
                return;
            }
            w.setTarget(null);
            if (--next <= 0 || w.getNavigation().isDone()) {
                Vec3 to = Wild7Goals.away(w, from, 14, 0);
                if (to != null) {
                    w.getNavigation().moveTo(to.x, to.y, to.z, w.species().fleeSpeed());
                }
                next = 20;
            }
        }
    }

    /** Chasse du tigre : a l'affut (tapi, lent) jusqu'a 5 blocs, puis le bond, puis le corps a corps. */
    public static final class Stalk extends Goal {

        final Tiger t;
        int repath, hitCool;

        public Stalk(Tiger t) {
            this.t = t;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity e = t.getTarget();
            return e != null && e.isAlive() && t.fear() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            repath = 0;
            t.setAction(Wild8Rules.STALK);
            t.playOwn("growl");
        }

        @Override
        public void stop() {
            t.getNavigation().stop();
            if (t.action() == Wild8Rules.STALK || t.action() == WildRules.CHARGE) {
                t.setAction(WildRules.IDLE);
            }
        }

        @Override
        public void tick() {
            //1.0.5-beta (lot 21) : but tenu a chaque tick : la proie peut mourir ou disparaitre entre deux verifications du selecteur
            LivingEntity e = t.getTarget();
            if (e == null || !e.isAlive()) {
                return;
            }
            t.getLookControl().setLookAt(e, 30F, 30F);
            double d = t.distanceTo(e);
            if (hitCool > 0) {
                hitCool--;
            }
            if (t.pouncing()) {
                if (d < 1.8 && hitCool <= 0) {
                    t.doHurtTarget(e);
                    t.strikes++;
                    hitCool = 20;
                }
                return;
            }
            switch (Wild8Rules.tigerStep(d, t.onGround(), t.pounceCool)) {
                case 0 -> {
                    t.setAction(Wild8Rules.STALK);
                    if (--repath <= 0) {
                        t.getNavigation().moveTo(e, Wild8Rules.STALK_SPEED);
                        repath = 10;
                    }
                }
                case 1 -> {
                    t.getNavigation().stop();
                    t.pounce(e);
                }
                default -> {
                    t.setAction(WildRules.CHARGE);
                    if (--repath <= 0) {
                        t.getNavigation().moveTo(e, 1.4);
                        repath = 5;
                    }
                    if (d < 1.9 + e.getBbWidth() / 2 && hitCool <= 0) {
                        t.doHurtTarget(e);
                    t.strikes++;
                        hitCool = 20;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ singe : cueillette, repas

    /** Le singe va cueillir une banane mure d'un bananier du mod (12 blocs) ; sinon il grimpe dans la canopee. */
    public static final class Forage extends Goal {

        final Monkey m;
        @Nullable
        BlockPos target;
        boolean pick;
        int left, cool;

        public Forage(Monkey m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Nullable
        BlockPos ripe() {
            Block leaves = PiFarm.LEAVES.containsKey("banana") ? PiFarm.LEAVES.get("banana").get() : null;
            if (leaves == null) {
                return null;
            }
            BlockPos c = m.blockPosition();
            int r = Wild8Rules.FORAGE_RANGE;
            //1.0.5-beta (lot 29) : palettes des sections d'abord (exact) : aucune feuille de bananier mure possible, rien a lire
            if (!fr.pioneers.core.block.ScanFilter.mayContain(m.level(), c.offset(-r, -3, -r), c.offset(r, 8, r),
                  s -> s.is(leaves) && s.getValue(FruitLeavesBlock.FRUIT) == 4)) {
                return null;
            }
            for (BlockPos b : BlockPos.betweenClosed(c.offset(-r, -3, -r), c.offset(r, 8, r))) {
                if (!m.level().isLoaded(b)) {
                    continue;                                              //1.0.5-beta (lot 21) : troncon absent : ignore
                }
                BlockState s = m.level().getBlockState(b);
                if (s.is(leaves) && s.getValue(FruitLeavesBlock.FRUIT) == 4) {
                    return b.immutable();
                }
            }
            return null;
        }

        /** Un tronc a grimper (bûche avec des feuilles au-dessus), a 10 blocs. */
        @Nullable
        BlockPos trunk() {
            BlockPos c = m.blockPosition();
            for (int i = 0; i < 24; i++) {
                BlockPos b = c.offset(m.getRandom().nextInt(21) - 10, m.getRandom().nextInt(5) - 1, m.getRandom().nextInt(21) - 10);
                if (m.level().isLoaded(b) && m.level().getBlockState(b).is(BlockTags.LOGS)) {
                    BlockPos top = b;
                    int n = 0;
                    while (n < 24 && m.level().getBlockState(top.above()).is(BlockTags.LOGS)) {
                        top = top.above();
                        n++;
                    }
                    if (m.level().getBlockState(top.above()).is(BlockTags.LEAVES) || n >= 3) {
                        return top.above();
                    }
                }
            }
            return null;
        }

        @Override
        public boolean canUse() {
            if (--cool > 0 || m.hasBanana() || m.alarmed() || m.isBaby() || m.action() == Wild8Rules.EAT || m.getRandom().nextInt(60) != 0) {
                return false;
            }
            cool = 200;
            target = ripe();
            pick = target != null;
            if (target == null) {
                target = trunk();
            }
            return target != null;
        }

        @Override
        public void start() {
            left = 400;
            m.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && target != null && !m.hasBanana() && !m.alarmed();
        }

        @Override
        public void tick() {
            left--;
            if (target == null) {
                return;                                                    //1.0.5-beta (lot 21)
            }
            m.getLookControl().setLookAt(Vec3.atCenterOf(target));
            if (m.getNavigation().isDone()) {
                m.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
            }
            if (pick && m.position().distanceTo(Vec3.atCenterOf(target)) < 2.2) {
                BlockState s = m.level().getBlockState(target);
                if (s.hasProperty(FruitLeavesBlock.FRUIT) && s.getValue(FruitLeavesBlock.FRUIT) == 4) {
                    m.level().setBlock(target, s.setValue(FruitLeavesBlock.FRUIT, 1), 2);   //cueillie : la branche revient au repos
                    m.level().playSound(null, target, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.NEUTRAL, 1F, 1.2F);
                    m.setBanana(true);
                }
                left = 0;
            } else if (!pick && m.blockPosition().distManhattan(target) <= 2) {
                left = Math.min(left, 60);                                       //perche dans la canopee : il s'y repose
                m.getNavigation().stop();
            }
        }

        @Override
        public void stop() {
            target = null;
        }
    }

    /** Le repas du singe : assis, il epluche (1,75 s), mange (2 s), jette la peau, puis se leche les doigts. */
    public static final class Eat extends Goal {

        final Monkey m;
        int t;

        public Eat(Monkey m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return m.hasBanana() && !m.isBaby() && m.onGround() && !m.climbing() && m.getTarget() == null && (!m.alarmed() || m.alarm < 40)
                   && m.getRandom().nextInt(40) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return t < Wild8Rules.EAT_TICKS && m.hasBanana() && m.isAlive() && m.getLastHurtByMob() == null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            t = 0;
            m.getNavigation().stop();
            m.setAction(Wild8Rules.EAT);
            m.eatStart = m.tickCount;
        }

        @Override
        public void tick() {
            //1.0.5-beta (lot 21) : but tenu a chaque tick : le repas fini, tick() peut encore etre appele (jusqu'a 7 fois loin des
            //joueurs) avant que le selecteur ne l'arrete : la banane et le soin ne se donnent qu'une fois
            if (t >= Wild8Rules.EAT_TICKS) {
                return;
            }
            t++;
            m.getNavigation().stop();
            if (Wild8Rules.eatStage(t) == 1 && t % 12 == 0) {
                m.playOwn("eat");
            }
            if (t == Wild8Rules.THROW_AT) {
                m.tossPeel();
            }
            if (t >= Wild8Rules.EAT_TICKS) {
                m.setBanana(false);
                m.heal(2F);
            }
        }

        @Override
        public void stop() {
            if (m.action() == Wild8Rules.EAT) {
                m.setAction(WildRules.IDLE);
            }
        }
    }
}

package fr.pioneers.wild;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 7) : buts des predateurs et de la petite faune.
 * <ul>
 *     <li>{@link Idle} : repos, flairage / picorage, animation propre (le loup hurle la nuit, l'ours se dresse, le rat se toilette assis,
 *     le pigeon roucoule en faisant le beau, le serpent s'enroule et dresse la tete) ;</li>
 *     <li>{@link FearFire} : le loup fuit le joueur qui tient une torche ou une lanterne, et les feux ;</li>
 *     <li>{@link BirdFlee} : l'oiseau s'envole loin et haut ; {@link Perch} : le pic-vert s'accroche a un tronc et tambourine ;
 *     {@link Roost} : le pigeon se juche sur un toit ; {@link GoHome} : un oiseau appele par un nichoir y vole et se pose pres de lui.</li>
 * </ul>
 */
public final class Wild7Goals {

    private Wild7Goals() {
    }

    /** Point de fuite a ~{@code d} blocs a l'oppose de {@code from} (repli : point au hasard loin de la menace). */
    @Nullable
    static Vec3 away(WildAnimal a, Vec3 from, double d, double up) {
        Vec3 dir = a.position().subtract(from).multiply(1, 0, 1);
        if (dir.lengthSqr() < 1E-4) {
            dir = new Vec3(a.getRandom().nextDouble() - 0.5, 0, a.getRandom().nextDouble() - 0.5);
        }
        dir = dir.normalize();
        Vec3 to = a.position().add(dir.x * d, up, dir.z * d);
        if (up == 0 && a.getNavigation().createPath(to.x, to.y, to.z, 0) == null) {
            return DefaultRandomPos.getPosAway(a, (int) d + 4, 7, from);
        }
        return to;
    }

    // ------------------------------------------------------------------ repos, flairage, animation propre

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
                || a instanceof WildBird b && b.clinging()) {
                return false;
            }
            boolean night = a.level().isNight();
            int k = a.kind;
            int odds = k == Wild7Rules.SNAKE ? 60 : k == Wild7Rules.WOLF ? (night ? 160 : 60) : 120;
            if (a.getRandom().nextInt(odds) != 0) {
                return false;
            }
            double r = a.getRandom().nextDouble();
            switch (k) {
                case Wild7Rules.WOLF -> {
                    act = night ? (r < 0.25 ? WildRules.DISPLAY : r < 0.6 ? WildRules.GRAZE : WildRules.ALERT) : (r < 0.55 ? WildRules.REST : WildRules.GRAZE);
                }
                case Wild7Rules.BEAR -> act = r < (night ? 0.6 : 0.3) ? WildRules.REST : r < 0.85 ? WildRules.GRAZE : WildRules.DISPLAY;
                case Wild7Rules.RAT -> act = r < 0.45 ? WildRules.GRAZE : r < 0.85 ? WildRules.DISPLAY : WildRules.ALERT;
                case Wild7Rules.SNAKE -> act = r < 0.65 ? WildRules.REST : WildRules.ALERT;
                case Wild7Rules.PIGEON -> act = r < 0.6 ? WildRules.GRAZE : WildRules.DISPLAY;
                default -> act = WildRules.GRAZE;
            }
            left = switch (act) {
                case WildRules.REST -> 400 + a.getRandom().nextInt(800);
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
                    case Wild7Rules.WOLF -> a.playOwn("howl");
                    case Wild7Rules.BEAR -> a.playOwn("roar");
                    case Wild7Rules.PIGEON -> a.playAmbientSound();
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

    // ------------------------------------------------------------------ peur du feu (loup)

    public static final class FearFire extends Goal {

        final GrayWolf w;
        int next;

        public FearFire(GrayWolf w) {
            this.w = w;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            return w.fear() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return w.fear() != null;
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
            Vec3 from = w.fear();
            if (from == null) {
                return;
            }
            w.setTarget(null);
            if (--next <= 0 || w.getNavigation().isDone()) {
                Vec3 to = away(w, from, 14, 0);
                if (to != null) {
                    w.getNavigation().moveTo(to.x, to.y, to.z, w.species().fleeSpeed());
                }
                next = 20;
            }
        }
    }

    // ------------------------------------------------------------------ oiseaux

    /** L'oiseau s'envole loin et en hauteur devant un joueur trop proche (moitie s'il est accroupi) ou sur alerte. */
    public static final class BirdFlee extends Goal {

        final WildBird b;
        int next;

        public BirdFlee(WildBird b) {
            this.b = b;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Player p = WildGoals.nearestScary(b, b.species().flee());
            if (p != null) {
                b.alarm(p.position(), WildRules.ALARM_TICKS, true);
                return true;
            }
            return b.alarmed();
        }

        @Override
        public boolean canContinueToUse() {
            return b.alarmed();
        }

        @Override
        public void start() {
            b.cling(null, null);
            b.setAction(Wild7Rules.FLY);
            b.playOwn("flap");
            next = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (b.threat == null) {
                return;
            }
            if (--next <= 0 || b.getNavigation().isDone()) {
                Vec3 to = away(b, b.threat, 16, 5 + b.getRandom().nextInt(4));
                if (to != null) {
                    b.getNavigation().moveTo(to.x, to.y, to.z, b.species().fleeSpeed());
                }
                next = 30;
            }
        }
    }

    /** Pic-vert : vole a un tronc (bois des arbres, bois au-dessus et au-dessous), s'y accroche, tambourine par salves, puis repart. */
    public static final class Perch extends Goal {

        final Woodpecker b;
        @Nullable
        BlockPos spot;
        Direction face = Direction.NORTH;
        int left, cool, drum, nextDrum;

        public Perch(Woodpecker b) {
            this.b = b;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (--cool > 0 || b.alarmed()) {
                return false;
            }
            cool = 60 + b.getRandom().nextInt(80);
            return findTrunk();
        }

        boolean findTrunk() {
            Level level = b.level();
            RandomSource r = b.getRandom();
            BlockPos c = b.blockPosition();
            for (int t = 0; t < 24; t++) {
                BlockPos log = c.offset(r.nextInt(2 * Wild7Rules.PERCH_RANGE + 1) - Wild7Rules.PERCH_RANGE, r.nextInt(9) - 2,
                      r.nextInt(2 * Wild7Rules.PERCH_RANGE + 1) - Wild7Rules.PERCH_RANGE);
                if (!trunk(level, log)) {
                    continue;
                }
                Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(r);
                BlockPos air = log.relative(d);
                if (level.isLoaded(air) && level.getBlockState(air).isAir() && level.getBlockState(air.below()).isAir()) {
                    spot = air;
                    face = d.getOpposite();
                    return true;
                }
            }
            return false;
        }

        static boolean trunk(Level level, BlockPos p) {
            return level.isLoaded(p) && level.getBlockState(p).is(BlockTags.LOGS) && level.getBlockState(p.above()).is(BlockTags.LOGS)
                   && level.getBlockState(p.below()).is(BlockTags.LOGS);
        }

        @Override
        public void start() {
            left = Wild7Rules.PERCH_MIN + b.getRandom().nextInt(Wild7Rules.PERCH_MAX - Wild7Rules.PERCH_MIN);
            drum = 0;
            nextDrum = 20 + b.getRandom().nextInt(40);
            b.getNavigation().moveTo(spot.getX() + 0.5, spot.getY() + 0.1, spot.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && spot != null && !b.alarmed() && trunk(b.level(), spot.relative(face));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            //1.0.5-beta (lot 21) : but tenu a chaque tick (et IA allegee : jusqu'a 7 ticks sans canContinueToUse) : jamais de place nulle
            if (spot == null) {
                return;
            }
            Vec3 at = Vec3.atBottomCenterOf(spot).add(face.getStepX() * 0.28, 0.15, face.getStepZ() * 0.28);
            if (!b.clinging()) {
                if (b.position().distanceTo(at) < 1.0) {
                    b.cling(at, face);
                    b.setAction(Wild7Rules.PERCH);
                } else if (b.getNavigation().isDone()) {
                    b.getNavigation().moveTo(at.x, at.y, at.z, 1.0);
                    if (--left < Wild7Rules.PERCH_MIN / 2) {
                        left = 0;
                    }
                }
                return;
            }
            left--;
            if (drum > 0) {
                drum--;
                b.drumBeat();
                if (drum == 0) {
                    b.setAction(Wild7Rules.PERCH);
                    nextDrum = 40 + b.getRandom().nextInt(120);
                }
            } else if (--nextDrum <= 0) {
                drum = Wild7Rules.DRUM_BURST;
                b.setAction(WildRules.DISPLAY);
            }
        }

        @Override
        public void stop() {
            b.cling(null, null);
            b.setAction(WildRules.IDLE);
            spot = null;
            cool = 200 + b.getRandom().nextInt(400);
        }
    }

    /** Pigeon : se juche sur un toit (escaliers, dalles, planches, briques, murets, barrieres, nichoirs) et y reste un moment. */
    public static final class Roost extends Goal {

        final Pigeon b;
        @Nullable
        BlockPos spot;
        int left, cool;

        public Roost(Pigeon b) {
            this.b = b;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (--cool > 0 || b.alarmed()) {
                return false;
            }
            cool = 80 + b.getRandom().nextInt(120);
            return find();
        }

        boolean find() {
            Level level = b.level();
            RandomSource r = b.getRandom();
            for (int t = 0; t < 16; t++) {
                int x = b.getBlockX() + r.nextInt(2 * Wild7Rules.ROOF_RANGE + 1) - Wild7Rules.ROOF_RANGE;
                int z = b.getBlockZ() + r.nextInt(2 * Wild7Rules.ROOF_RANGE + 1) - Wild7Rules.ROOF_RANGE;
                //1.0.5-beta (lot 21) : getHeight charge le troncon : tester AVANT
                if (!level.isLoaded(new BlockPos(x, b.getBlockY(), z))) {
                    continue;
                }
                BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (roof(level.getBlockState(top.below())) && level.getBlockState(top).isAir()) {
                    spot = top;
                    return true;
                }
            }
            return false;
        }

        static boolean roof(BlockState s) {
            return s.is(BlockTags.STAIRS) || s.is(BlockTags.SLABS) || s.is(BlockTags.PLANKS) || s.is(BlockTags.WALLS) || s.is(BlockTags.FENCES)
                   || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.BRICKS) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.COBBLESTONE)
                   || s.getBlock() instanceof BirdHouseBlock;
        }

        @Override
        public void start() {
            left = Wild7Rules.PERCH_MIN + b.getRandom().nextInt(Wild7Rules.PERCH_MAX);
            b.getNavigation().moveTo(spot.getX() + 0.5, spot.getY() + 0.2, spot.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && spot != null && !b.alarmed();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            left--;
            if (spot == null) {
                return;                                                    //1.0.5-beta (lot 21) : but tenu a chaque tick
            }
            if (b.action() != Wild7Rules.PERCH) {
                if (b.onGround() && b.position().distanceTo(Vec3.atBottomCenterOf(spot)) < 1.2) {
                    b.getNavigation().stop();
                    b.setAction(Wild7Rules.PERCH);
                } else if (b.getNavigation().isDone()) {
                    b.getNavigation().moveTo(spot.getX() + 0.5, spot.getY() + 0.2, spot.getZ() + 0.5, 1.0);
                }
            } else if (b.getRandom().nextInt(200) == 0) {
                b.playAmbientSound();
            }
        }

        @Override
        public void stop() {
            if (b.action() == Wild7Rules.PERCH) {
                b.setAction(WildRules.IDLE);
            }
            spot = null;
        }
    }

    /** Oiseau appele par un nichoir ({@link WildBird#home}) : il y vole et se pose a cote (puis vit sa vie dans les environs). */
    public static final class GoHome extends Goal {

        final WildBird b;

        public GoHome(WildBird b) {
            this.b = b;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return b.home != null && !b.alarmed();
        }

        @Override
        public void start() {
            BlockPos h = b.home;
            b.getNavigation().moveTo(h.getX() + 0.5, h.getY() + 1.2, h.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return b.home != null && !b.alarmed() && b.homeTicks > 0;
        }

        @Override
        public void tick() {
            BlockPos h = b.home;
            if (h == null) {
                return;
            }
            b.homeTicks--;
            if (b.position().distanceTo(Vec3.atCenterOf(h)) < 2.5) {
                b.home = null;
            } else if (b.getNavigation().isDone()) {
                b.getNavigation().moveTo(h.getX() + 0.5, h.getY() + 1.2, h.getZ() + 0.5, 1.0);
            }
        }

        @Override
        public void stop() {
            b.home = null;
        }
    }
}

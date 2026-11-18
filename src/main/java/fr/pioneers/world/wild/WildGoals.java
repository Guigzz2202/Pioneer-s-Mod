package fr.pioneers.world.wild;

import fr.pioneers.world.season.Seasons;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 6) : buts communs du gibier.
 * <ul>
 *     <li>{@link Flee} : fuite devant un joueur (distance de l'espece, moitie s'il s'approche accroupi ; createurs et spectateurs ignores) ou
 *     sur alerte (blessure, congenere qui fuit a 16 blocs : la HARDE fuit ensemble) ; le cerf fuit en BONDS, le lievre en ZIGZAG ;</li>
 *     <li>{@link Herd} : les adultes restent pres du centre de leur harde (au-dela de 10 blocs, ils la rejoignent) ;</li>
 *     <li>{@link Act} : repos couche (surtout la nuit), broutage / fouille du groin, animation propre : le dindon fait la roue (surtout au
 *     printemps), le cerf brame (surtout a l'automne), le lievre se dresse aux aguets, la biche leve la tete.</li>
 * </ul>
 */
public final class WildGoals {

    private WildGoals() {
    }

    @Nullable
    static Player nearestScary(WildAnimal a, double reach) {
        Player p = a.level().getNearestPlayer(a.getX(), a.getY(), a.getZ(), reach, WildAnimal::scary);
        if (p == null) {
            return null;
        }
        return a.distanceTo(p) < WildRules.fleeDistance(a.species().flee(), p.isCrouching()) ? p : null;
    }

    // ------------------------------------------------------------------ fuite

    public static final class Flee extends Goal {

        final WildAnimal a;
        int leg, next;

        public Flee(WildAnimal a) {
            this.a = a;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (a.getTarget() != null || a.species().flee() <= 0) {
                return false;
            }
            Player p = nearestScary(a, a.species().flee());
            if (p != null) {
                a.alarm(p.position(), WildRules.ALARM_TICKS, a.kind != WildRules.BOAR && a.kind != WildRules.BISON);
                return true;
            }
            return a.alarmed();
        }

        @Override
        public boolean canContinueToUse() {
            return a.getTarget() == null && (a.alarmed() || nearestScary(a, a.species().flee() * 1.5) != null);
        }

        @Override
        public void start() {
            a.setAction(WildRules.FLEE);
            leg = 0;
            next = 0;
        }

        @Override
        public void stop() {
            a.setAction(WildRules.IDLE);
            a.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Player p = nearestScary(a, a.species().flee() * 1.5);
            if (p != null) {
                a.alarm(p.position(), WildRules.ALARM_TICKS, false);
            }
            if (a.threat == null) {
                return;
            }
            Vec3 away = a.position().subtract(a.threat).multiply(1, 0, 1);
            if (away.lengthSqr() < 1E-4) {
                away = new Vec3(a.getRandom().nextDouble() - 0.5, 0, a.getRandom().nextDouble() - 0.5);
            }
            away = away.normalize();
            double speed = a.species().fleeSpeed();
            if (--next <= 0 || a.getNavigation().isDone()) {
                double yaw = Math.atan2(away.z, away.x);
                boolean zig = a.kind == WildRules.HARE;
                if (zig) {
                    yaw = WildRules.zigzag(yaw, leg++);
                }
                double d = zig ? 7 : 12;
                Vec3 to = a.position().add(Math.cos(yaw) * d, 0, Math.sin(yaw) * d);
                Path path = a.getNavigation().createPath(to.x, to.y, to.z, 0);
                if (path == null) {
                    Vec3 r = DefaultRandomPos.getPosAway(a, 16, 7, a.threat);
                    path = r == null ? null : a.getNavigation().createPath(r.x, r.y, r.z, 0);
                }
                if (path != null) {
                    a.getNavigation().moveTo(path, speed);
                }
                next = zig ? WildRules.ZIG_TICKS : 20;
                if (zig && a.getRandom().nextInt(3) == 0) {
                    a.playOwn("jump");
                }
            }
            //le cerf fuit en bonds
            if (a.kind == WildRules.DEER && a.onGround() && a.tickCount % WildRules.LEAP_TICKS == 0) {
                a.getJumpControl().jump();
                a.setDeltaMovement(a.getDeltaMovement().add(away.x * 0.22, 0, away.z * 0.22));
            }
        }
    }

    // ------------------------------------------------------------------ harde

    public static final class Herd extends Goal {

        final WildAnimal a;
        int cool;
        Vec3 center;

        public Herd(WildAnimal a) {
            this.a = a;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (--cool > 0 || a.isBaby() || a.alarmed() || a.getTarget() != null) {
                return false;
            }
            cool = 40 + a.getRandom().nextInt(40);
            List<WildAnimal> kin = a.kin(WildRules.HERD_SEARCH);
            if (kin.size() < 2) {
                return false;
            }
            double x = 0, y = 0, z = 0;
            int n = 0;
            for (WildAnimal w : kin) {
                if (w != a) {
                    x += w.getX();
                    y += w.getY();
                    z += w.getZ();
                    n++;
                }
            }
            center = new Vec3(x / n, y / n, z / n);
            return a.position().distanceTo(center) > WildRules.HERD_KEEP;
        }

        @Override
        public void start() {
            a.getNavigation().moveTo(center.x, center.y, center.z, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return !a.getNavigation().isDone() && a.position().distanceTo(center) > 4 && !a.alarmed();
        }
    }

    // ------------------------------------------------------------------ repos, broutage, animation propre

    public static final class Act extends Goal {

        final WildAnimal a;
        int left, act;

        public Act(WildAnimal a) {
            this.a = a;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        int season() {
            Seasons.Date d = a.date();
            return d == null ? 0 : d.season().ordinal();
        }

        @Override
        public boolean canUse() {
            if (a.alarmed() || a.getTarget() != null || !a.onGround() || a.isInWater() || a.isInLove()) {
                return false;
            }
            boolean night = a.level().isNight();
            if (a.getRandom().nextInt(night ? 50 : 140) != 0) {
                return false;
            }
            double r = a.getRandom().nextDouble();
            int season = season();
            if (r < (night ? 0.6 : 0.12)) {
                act = WildRules.REST;
                left = 400 + a.getRandom().nextInt(800);
                return true;
            }
            r = a.getRandom().nextDouble();
            boolean adultMale = a.isMale() && !a.isBaby();
            if (a.kind == WildRules.TURKEY && adultMale && r < (season == 0 ? 0.5 : 0.15)) {
                act = WildRules.DISPLAY;
                left = 80 + a.getRandom().nextInt(80);
            } else if (a.kind == WildRules.DEER && adultMale && r < (season == 2 ? 0.4 : 0.05)) {
                act = WildRules.DISPLAY;
                left = 60 + a.getRandom().nextInt(40);
            } else if (a.kind == WildRules.HARE && r < 0.3 || a.kind == WildRules.DEER && !a.isMale() && r < 0.15) {
                act = WildRules.ALERT;
                left = 40 + a.getRandom().nextInt(60);
            } else {
                act = WildRules.GRAZE;
                left = 60 + a.getRandom().nextInt(100);
            }
            return true;
        }

        @Override
        public void start() {
            a.getNavigation().stop();
            a.setAction(act);
            if (act == WildRules.DISPLAY) {
                a.playOwn(a.kind == WildRules.TURKEY ? "strut" : "bell");
            }
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && !a.alarmed() && a.getTarget() == null && !a.isInWater();
        }

        @Override
        public void tick() {
            left--;
            if (act == WildRules.DISPLAY && a.kind == WildRules.TURKEY) {
                a.setYBodyRot(a.yBodyRot + 1.5F);            //le dindon tourne lentement en faisant la roue
                a.setYRot(a.yBodyRot);
            }
        }

        @Override
        public void stop() {
            a.setAction(WildRules.IDLE);
        }
    }
}

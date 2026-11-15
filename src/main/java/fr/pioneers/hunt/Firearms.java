package fr.pioneers.hunt;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.territory.Protection;
import fr.pioneers.fauna.FaunaGroup;
import fr.pioneers.fauna.FaunaRegistry;
import fr.pioneers.fauna.FaunaSpecies;
import fr.pioneers.wild.WildAnimal;
import fr.pioneers.wild.WildRules;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 10, specification §6.3, decision 11) : le TIR des armes a feu (mousquet, revolver), decide par le SERVEUR seul (robuste
 * en multijoueur : aucun projectile a synchroniser, aucune donnee de visee venue du client) : lancer de rayon depuis les yeux du tireur,
 * dispersion tiree au hasard (reduite par la competence Chasse), premier bloc ou premiere creature touchee (parties des geants de la mer
 * comprises), portee de l'arme.
 * <ul>
 *   <li>degats {@code pioneers:gunshot} (projectile), contre un joueur x {@link HuntSettings#pvpFactor} (0,5 par defaut) ; regle PvP du
 *   serveur, alliances de clans (tir allie coupe par {@code WarBootstrap}) ; une bete dans un claim : la matrice du claim (groupe HUNT) ;</li>
 *   <li>bruit : le gibier fuit dans un rayon de {@link HuntRules#SCARE_RADIUS} blocs ; fumee a la bouche du canon, eclats a l'impact ;</li>
 *   <li>refus sous l'eau (poudre mouillee).</li>
 * </ul>
 */
public final class Firearms {

    private Firearms() {
    }

    public static final ResourceKey<DamageType> GUNSHOT = ResourceKey.create(Registries.DAMAGE_TYPE, Pioneers.rl("gunshot"));

    /** Resultat d'un tir (GameTests). */
    public record Shot(@Nullable Entity hit, @Nullable Vec3 at, float damage, boolean blocked) {
    }

    /** Le tireur a-t-il la tete sous l'eau (les armes a feu refusent) ? */
    public static boolean wet(LivingEntity shooter) {
        return shooter.isEyeInFluid(FluidTags.WATER) || shooter.isUnderWater();
    }

    public static DamageSource source(ServerLevel level, Entity shooter) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(GUNSHOT), shooter, shooter);
    }

    /** Tir : rayon, degats, protections, bruit, fumee, son. */
    public static Shot fire(ServerLevel level, ServerPlayer shooter, float damage, int range, float spread, SoundEvent sound, float volume,
          float pitch, boolean heavy) {
        float[] dev = HuntRules.deviate(spread, level.random.nextDouble(), level.random.nextDouble());
        Vec3 eye = shooter.getEyePosition();
        Vec3 dir = Vec3.directionFromRotation(shooter.getXRot() + dev[1], shooter.getYRot() + dev[0]);
        Shot s = ray(level, shooter, eye, dir, damage, range);
        effects(level, shooter, eye, dir, s, sound, volume, pitch, heavy);
        scare(level, shooter, shooter.position());
        return s;
    }

    /** Coeur du tir (GameTests : direction donnee). */
    public static Shot ray(ServerLevel level, ServerPlayer shooter, Vec3 eye, Vec3 dir, float damage, int range) {
        Vec3 end = eye.add(dir.scale(range));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, shooter, eye, end, new AABB(eye, end).inflate(1.0),
              e -> !e.isSpectator() && e.isPickable() && e != shooter && !(e instanceof PartEntity<?> p && p.getParent() == shooter), 0.3F);
        if (eh == null) {
            return new Shot(null, block.getType() == HitResult.Type.MISS ? null : block.getLocation(), 0, false);
        }
        Entity hit = eh.getEntity();
        Entity target = hit instanceof PartEntity<?> p ? p.getParent() : hit;
        float dmg = damage;
        if (target instanceof Player victim) {
            if (!shooter.canHarmPlayer(victim)) {
                return new Shot(target, eh.getLocation(), 0, true);
            }
            dmg = HuntRules.pvp(damage, HuntSettings.current().pvpFactor());
        } else {
            ClanApi.Action a = Protection.classify(target, true);
            if (a != null && !ClanApi.canModify(shooter, level, target.blockPosition(), a)) {
                shooter.displayClientMessage(Component.translatable("hunt.pioneers.protected"), true);
                return new Shot(target, eh.getLocation(), 0, true);
            }
        }
        boolean done = dmg > 0 && hit.hurt(source(level, shooter), dmg);
        if (done && target instanceof LivingEntity le) {
            le.knockback(0.35, -dir.x, -dir.z);
        }
        return new Shot(target, eh.getLocation(), done ? dmg : 0, !done);
    }

    static void effects(ServerLevel level, ServerPlayer shooter, Vec3 eye, Vec3 dir, Shot s, SoundEvent sound, float volume, float pitch,
          boolean heavy) {
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
        Vec3 muzzle = eye.add(dir.scale(1.1)).add(0, -0.15, 0);
        level.sendParticles(heavy ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, heavy ? 6 : 4,
              0.12, 0.08, 0.12, 0.01);
        level.sendParticles(ParticleTypes.POOF, muzzle.x, muzzle.y, muzzle.z, heavy ? 8 : 3, 0.1, 0.1, 0.1, 0.02);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 2, 0.03, 0.03, 0.03, 0.01);
        if (s.at() != null) {
            if (s.hit() == null) {
                net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(s.at().subtract(dir.scale(0.05)).add(dir.scale(0.1)));
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(bp)), s.at().x, s.at().y, s.at().z, 8,
                      0.05, 0.05, 0.05, 0.1);
            } else {
                level.sendParticles(ParticleTypes.CRIT, s.at().x, s.at().y, s.at().z, 6, 0.1, 0.1, 0.1, 0.2);
            }
        }
    }

    /** Bruit d'un coup de feu : le gibier (betes sauvages, betes du groupe GIBIER) fuit dans un rayon de 32 blocs. */
    public static int scare(ServerLevel level, @Nullable Entity shooter, Vec3 from) {
        int r = HuntRules.SCARE_RADIUS, n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(r, 16, r),
              e -> e.isAlive() && e.position().distanceToSqr(from) < r * r)) {
            if (e instanceof WildAnimal w) {
                if (w.species().flee() > 0) {
                    w.alarm(from, WildRules.ALARM_TICKS * 2, false);
                    n++;
                }
            } else if (e instanceof Animal a && !(a instanceof TamableAnimal t && t.isTame()) && !a.isLeashed() && !a.hasCustomName()) {
                FaunaSpecies s = FaunaRegistry.of(a);
                if (s != null && s.group == FaunaGroup.GAME) {
                    PathfinderMob pm = a;
                    Vec3 to = DefaultRandomPos.getPosAway(pm, 16, 7, from);
                    if (to != null) {
                        pm.getNavigation().moveTo(to.x, to.y, to.z, 1.6);
                        n++;
                    }
                }
            }
        }
        return n;
    }
}

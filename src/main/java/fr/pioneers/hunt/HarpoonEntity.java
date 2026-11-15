package fr.pioneers.hunt;

import fr.pioneers.fauna.FaunaGroup;
import fr.pioneers.fauna.FaunaRegistry;
import fr.pioneers.fauna.FaunaSpecies;
import fr.pioneers.wild.MarineAnimal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 10, specification §6.3) : le HARPON lance, RETENU PAR UNE CORDE (dessinee par {@code HarpoonRenderer} jusqu'a la main du
 * lanceur). Fonctionne sous l'eau (inertie {@link HuntRules#HARPOON_WATER_INERTIA}, celle du trident). Une bete touchee (la bete entiere
 * quand c'est une partie d'un geant de la mer) prend {@link HuntRules#harpoonDamage} (x 2,5 contre la faune marine) et reste HARPONNEE :
 * freinee (lenteur), tiree vers le lanceur quand la corde se tend (sauf les betes de plus de 2 blocs de large : baleines, requins geants) ;
 * corde rompue au-dela de 1,5 fois sa longueur. Un clic droit avec l'objet RAMENE LA CORDE : le harpon revient dans la main.
 * <p>
 * L'objet reste toujours dans l'inventaire (marque {@code Thrown} = UUID de l'entite) : l'entite n'est que le harpon en vol ; perdue
 * (dechargee, lanceur parti), la marque se leve au clic suivant. Jamais de duplication, jamais de harpon perdu.
 */
public class HarpoonEntity extends AbstractArrow {

    private static final EntityDataAccessor<Integer> ATTACHED = SynchedEntityData.defineId(HarpoonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RETURNING = SynchedEntityData.defineId(HarpoonEntity.class, EntityDataSerializers.BOOLEAN);
    /** Duree de vie sans lanceur a portee (ticks). */
    static final int LOST = 2_400;
    private Vec3 offset = Vec3.ZERO;
    private int idle;
    private boolean dealt;

    public HarpoonEntity(EntityType<? extends HarpoonEntity> type, Level level) {
        super(type, level);
        pickup = Pickup.DISALLOWED;
    }

    public HarpoonEntity(Level level, LivingEntity owner) {
        super(PiHunt.HARPOON_ENTITY.get(), owner, level);
        pickup = Pickup.DISALLOWED;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ATTACHED, -1);
        entityData.define(RETURNING, false);
    }

    /** Bete harponnee (ou null). */
    @Nullable
    public Entity attached() {
        int id = entityData.get(ATTACHED);
        return id < 0 ? null : level().getEntity(id);
    }

    public boolean returning() {
        return entityData.get(RETURNING);
    }

    /** Ramener la corde : le harpon lache la bete et revient au lanceur. */
    public void reel() {
        entityData.set(ATTACHED, -1);
        entityData.set(RETURNING, true);
        setNoPhysics(true);
        playSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 1.0F, 0.6F);
    }

    @Override
    protected float getWaterInertia() {
        return HuntRules.HARPOON_WATER_INERTIA;
    }

    @NotNull
    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @NotNull
    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    /** Faune marine : bete de la mer du mod, animal aquatique, groupe MARIN ou POISSON du socle de faune. */
    public static boolean marine(Entity e) {
        if (e instanceof MarineAnimal || e instanceof WaterAnimal) {
            return true;
        }
        FaunaSpecies s = FaunaRegistry.of(e);
        return s != null && (s.group == FaunaGroup.MARINE || s.group == FaunaGroup.FISH);
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult hit) {
        Entity e = hit.getEntity();
        Entity target = e instanceof PartEntity<?> p ? p.getParent() : e;
        if (target == getOwner() || returning() || dealt) {
            return;
        }
        Entity owner = getOwner();
        float dmg = HuntRules.harpoonDamage(marine(target));
        dealt = true;
        boolean hurt = e.hurt(damageSources().trident(this, owner == null ? this : owner), dmg);
        if (hurt) {
            playSound(SoundEvents.TRIDENT_HIT, 1.0F, 0.8F);
        }
        //1.0.5-beta (lot 12) : la corde ne retient qu'une BETE vraiment touchee : jamais un joueur (ni ralenti ni tire), jamais un animal
        //protege (maitre, PvP coupe, invulnerable : le coup est refuse) ; sinon un joueur pouvait en trainer un autre ou voler un animal
        if (anchors(target, hurt)) {
            entityData.set(ATTACHED, target.getId());
            offset = position().subtract(target.position());
            double max = Math.max(0.2, target.getBbWidth() * 0.45);
            offset = new Vec3(Math.max(-max, Math.min(max, offset.x)), Math.max(0.1, Math.min(target.getBbHeight() * 0.9, offset.y)),
                  Math.max(-max, Math.min(max, offset.z)));
            setDeltaMovement(Vec3.ZERO);
        } else {
            setDeltaMovement(getDeltaMovement().multiply(-0.01, -0.1, -0.01));
        }
    }

    /** 1.0.5-beta (lot 12) : le harpon s'accroche-t-il ? Une creature vivante, pas un joueur, que le coup a vraiment touchee. */
    public static boolean anchors(Entity target, boolean hurt) {
        return hurt && target.isAlive() && target instanceof LivingEntity && !(target instanceof Player);
    }

    @Override
    public void tick() {
        Entity owner = getOwner();
        if (!level().isClientSide) {
            if (owner == null || !owner.isAlive() || owner.level() != level()) {
                if (++idle > 40) {
                    discard();
                    return;
                }
            } else {
                idle = 0;
                double d = owner.distanceTo(this);
                if (!returning() && d > HuntRules.HARPOON_ROPE * 1.5) {
                    playSound(SoundEvents.LEASH_KNOT_BREAK, 1.0F, 0.7F);
                    reel();
                }
            }
            if (tickCount > LOST * 10) {
                discard();
                return;
            }
        }
        if (returning()) {
            setNoPhysics(true);
            if (owner == null) {
                discard();
                return;
            }
            Vec3 to = owner.getEyePosition().subtract(position());
            if (!level().isClientSide && to.length() < 1.6) {
                back(owner);
                discard();
                return;
            }
            setPosRaw(getX(), getY() + to.y * 0.015, getZ());
            setDeltaMovement(getDeltaMovement().scale(0.9).add(to.normalize().scale(0.35)));
            super.tick();
            return;
        }
        Entity t = attached();
        if (t != null) {
            if (!t.isAlive()) {
                entityData.set(ATTACHED, -1);
                setDeltaMovement(new Vec3(0, -0.1, 0));
                super.tick();
                return;
            }
            baseTick();
            setPos(t.getX() + offset.x, t.getY() + offset.y, t.getZ() + offset.z);
            setDeltaMovement(Vec3.ZERO);
            if (!level().isClientSide && t instanceof LivingEntity le) {
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 2, false, false));
                le.setDeltaMovement(le.getDeltaMovement().multiply(0.7, 1, 0.7));
                if (owner != null && le.getBbWidth() <= HuntRules.HARPOON_PULL_MAX_WIDTH) {
                    double pull = HuntRules.pull(owner.distanceTo(le), HuntRules.HARPOON_ROPE);
                    if (pull > 0) {
                        Vec3 dir = owner.position().subtract(le.position()).normalize();
                        le.push(dir.x * pull, dir.y * pull * 0.5 + (le.isInWater() ? 0 : 0.02), dir.z * pull);
                        le.hurtMarked = true;
                    }
                }
            }
            return;
        }
        super.tick();
    }

    /** Le harpon revient : la marque de l'objet du lanceur se leve. */
    void back(Entity owner) {
        if (owner instanceof Player p) {
            HarpoonItem.recover(p, getUUID());
            ((ServerLevel) level()).playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("dealt", dealt);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        dealt = tag.getBoolean("dealt");
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d2) {
        return d2 < 128 * 128;
    }

    /** Position cote client pour le rendu (suit la bete harponnee). */
    public Vec3 offset() {
        return offset;
    }
}

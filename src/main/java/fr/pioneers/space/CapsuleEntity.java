package fr.pioneers.space;

import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.common.PiLang;
import fr.pioneers.core.network.PacketCapsuleInput;
import fr.pioneers.core.registries.PiSounds;
import fr.pioneers.space.environment.Environments;
import fr.pioneers.space.giant.Aerostats;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Capsule d'arrivee : la descente se pilote.
 * <ul>
 *     <li><b>Espace</b> : retrofusee principale (consomme du carburant) ; <b>ZQSD</b> : propulseurs lateraux, dans la direction du regard ;</li>
 *     <li><b>parachute</b> (touche dediee) : seulement dans une atmosphere, et il se dechire au-dela de la vitesse de deploiement ;</li>
 *     <li><b>pilote automatique</b> (touche dediee) : descente prudente, parachute et retrofusee geres seuls ;</li>
 *     <li>la vitesse verticale au contact decide de l'issue : atterrissage parfait, brutal (blessures) ou ecrasement.</li>
 * </ul>
 * Sur une geante gazeuse, il n'y a pas de sol : la capsule est guidee vers un aerostat, deploye au besoin a l'arrivee.
 * La physique tourne sur le serveur ; le passager envoie ses commandes ({@link PacketCapsuleInput}).
 * Une fois posee, la capsule s'ouvre comme un coffre a un seul emplacement contenant la fusee repliee.
 */
public class CapsuleEntity extends Entity {

    /** Ancienne altitude fixe d'arrivee (conservee pour les mondes de 256 blocs). */
    public static final double ARRIVAL_ALTITUDE = 230;
    /** Vitesses de contact (blocs/tick) : parfait, sans dommage, brutal (au-dela : ecrasement). */
    public static final double PERFECT_SPEED = 0.25, SAFE_SPEED = 0.5, HARD_SPEED = 0.85;
    /** Vitesse au-dela de laquelle le parachute se dechire au deploiement. */
    public static final double CHUTE_TEAR_SPEED = 1.35;

    private static final EntityDataAccessor<Boolean> CHUTE = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LANDED = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TIMER = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FUEL = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FUEL_MAX = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> AUTOPILOT = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.BOOLEAN);
    /** Propulseurs allumes (bit 0 : principal, bit 1 : lateraux) et parachute dechire (bit 2). */
    private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> VSPEED = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HSPEED = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> AIRLESS = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.BOOLEAN);

    private final SimpleContainer cargo = new SimpleContainer(1);
    /** Commandes du passager (serveur). */
    private float inputForward, inputStrafe;
    private boolean inputThrust;
    private int lastInput;
    /** Cible de guidage (aerostat d'une geante gazeuse), null ailleurs. */
    @Nullable
    private BlockPos guidance;
    private double gravity = -1, density = -1;
    private int alarmCooldown;

    public CapsuleEntity(EntityType<? extends CapsuleEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(CHUTE, false);
        entityData.define(LANDED, false);
        entityData.define(TIMER, 0);
        entityData.define(FUEL, 400);
        entityData.define(FUEL_MAX, 400);
        entityData.define(AUTOPILOT, false);
        entityData.define(FLAGS, (byte) 0);
        entityData.define(VSPEED, 0F);
        entityData.define(HEIGHT, 0F);
        entityData.define(HSPEED, 0F);
        entityData.define(AIRLESS, false);
    }

    /** Altitude d'arrivee : le haut de la dimension (la capsule apparait sous la limite de construction). */
    public static double arrivalAltitude(Level level) {
        return Math.max(ARRIVAL_ALTITUDE, level.getMaxBuildHeight() - 16);
    }

    /** Preparation de la descente (serveur) : carburant selon le tier, aerostat sur une geante gazeuse. */
    public void prepareDescent(ServerLevel level, RocketTier tier, @Nullable SolarSystem.Body body) {
        int fuel = (int) ((320 + 30 * tier.level) * PiCommonConfig.get(PiCommonConfig.CAPSULE_FUEL, 1.0));
        entityData.set(FUEL_MAX, fuel);
        entityData.set(FUEL, fuel);
        if (body != null && body.isGasGiant()) {
            guidance = Aerostats.ensureNear(level, getBlockX(), getBlockZ());
            entityData.set(AUTOPILOT, true);
        }
        updateAtmosphere();
    }

    private void updateAtmosphere() {
        gravity = Mth.clamp(Environments.get(level()).gravity(), 0.05, 2.5);
        density = Environments.atmosphereDensity(level().dimension());
        entityData.set(AIRLESS, density < 0.05);
    }

    public void setCargo(ItemStack stack) {
        cargo.setItem(0, stack);
    }

    public SimpleContainer getCargo() {
        return cargo;
    }

    public boolean isChuteDeployed() {
        return entityData.get(CHUTE);
    }

    public boolean hasLanded() {
        return entityData.get(LANDED);
    }

    public int getTimer() {
        return entityData.get(TIMER);
    }

    public int getFuel() {
        return entityData.get(FUEL);
    }

    public int getFuelMax() {
        return Math.max(1, entityData.get(FUEL_MAX));
    }

    public boolean isAutopilot() {
        return entityData.get(AUTOPILOT);
    }

    public boolean isMainThrusting() {
        return (entityData.get(FLAGS) & 1) != 0;
    }

    public boolean isSideThrusting() {
        return (entityData.get(FLAGS) & 2) != 0;
    }

    public boolean isChuteTorn() {
        return (entityData.get(FLAGS) & 4) != 0;
    }

    public boolean isAirless() {
        return entityData.get(AIRLESS);
    }

    /** Vitesse verticale (blocs/tick, negative en descente). */
    public float getVerticalSpeed() {
        return entityData.get(VSPEED);
    }

    public float getHorizontalSpeed() {
        return entityData.get(HSPEED);
    }

    /** Hauteur au-dessus du sol (ou de l'eau). */
    public float getHeightAboveGround() {
        return entityData.get(HEIGHT);
    }

    /** Commandes recues du passager. */
    public void handleInput(ServerPlayer player, float forward, float strafe, boolean thrust, boolean toggleChute, boolean toggleAutopilot) {
        if (player.getVehicle() != this || hasLanded()) {
            return;
        }
        inputForward = Mth.clamp(forward, -1, 1);
        inputStrafe = Mth.clamp(strafe, -1, 1);
        inputThrust = thrust;
        lastInput = tickCount;
        if (toggleAutopilot) {
            boolean on = !isAutopilot();
            entityData.set(AUTOPILOT, on);
            player.displayClientMessage(Component.translatable(on ? "capsule.pioneers.autopilot_on" : "capsule.pioneers.autopilot_off")
                  .withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GOLD), true);
        }
        if (toggleChute) {
            deployChute(player);
        }
    }

    private void deployChute(@Nullable ServerPlayer player) {
        if (isChuteDeployed() || isChuteTorn()) {
            return;
        }
        if (density < 0.05) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("capsule.pioneers.no_atmosphere").withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        if (-getDeltaMovement().y > CHUTE_TEAR_SPEED) {
            setFlag(4, true);
            level().playSound(null, blockPosition(), SoundEvents.WOOL_BREAK, SoundSource.NEUTRAL, 2.0F, 0.6F);
            if (player != null) {
                player.displayClientMessage(Component.translatable("capsule.pioneers.chute_torn").withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        entityData.set(CHUTE, true);
        entityData.set(TIMER, 0);
        level().playSound(null, blockPosition(), PiSounds.CAPSULE_CHUTE.get(), SoundSource.NEUTRAL, 2.0F, 1.0F);
    }

    private void setFlag(int bit, boolean on) {
        byte flags = entityData.get(FLAGS);
        entityData.set(FLAGS, (byte) (on ? flags | bit : flags & ~bit));
    }

    @Nullable
    private ServerPlayer pilot() {
        return getFirstPassenger() instanceof ServerPlayer player ? player : null;
    }

    @Override
    public void tick() {
        super.tick();
        stepLerp();
        if (!hasLanded()) {
            if (level().isClientSide) {
                effects();
            } else {
                entityData.set(TIMER, getTimer() + 1);
                fly();
            }
        } else {
            if (!onGround()) {
                setDeltaMovement(0, Math.max(getDeltaMovement().y - 0.04, -0.6), 0);
                move(MoverType.SELF, getDeltaMovement());
            }
            //Fusee recuperee : la capsule disparait
            if (!level().isClientSide && cargo.isEmpty() && !isVehicle()) {
                discard();
            }
        }
    }

    /** Hauteur au-dessus de la premiere surface solide ou liquide. */
    private double heightAboveGround() {
        //Le heightmap donne directement le sommet de la colonne : on ne parcourt que les quelques blocs sous la capsule
        int columnTop = level().getHeight(Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ());
        int startY = Math.min(Math.min(getBlockY(), columnTop), level().getMaxBuildHeight() - 1);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(getBlockX(), startY, getBlockZ());
        int min = level().getMinBuildHeight();
        while (pos.getY() > min) {
            BlockState state = level().getBlockState(pos);
            if (!state.getCollisionShape(level(), pos).isEmpty() || !state.getFluidState().isEmpty()) {
                double top = state.getFluidState().isEmpty() ? pos.getY() + state.getCollisionShape(level(), pos).max(Direction.Axis.Y)
                                                             : pos.getY() + 0.9;
                return Math.max(0, getY() - top);
            }
            pos.move(0, -1, 0);
        }
        return getY() - min;
    }

    /** Physique de la descente (serveur). */
    private void fly() {
        if (gravity < 0) {
            updateAtmosphere();
        }
        ServerPlayer pilot = pilot();
        if (pilot == null || tickCount - lastInput > 40) {
            inputForward = inputStrafe = 0;
            inputThrust = false;
        }
        boolean auto = isAutopilot() || pilot == null;
        double height = heightAboveGround();
        Vec3 v = getDeltaMovement();
        double g = 0.03 * gravity;
        double vy = v.y - g;
        double vx = v.x, vz = v.z;
        //Traînee atmospherique
        double drag = 0.012 * density;
        vy -= vy * drag;
        int fuel = getFuel();
        double thrustAccel = Math.max(0.06, g * 2.2);
        boolean thrust;
        if (auto) {
            //Descente prudente : vite en altitude, lente pres du sol
            double desired = -Mth.clamp(0.12 + height * 0.022, 0.18, 1.6);
            if (density >= 0.05 && !isChuteDeployed() && !isChuteTorn() && height < 140 && -vy < CHUTE_TEAR_SPEED - 0.1) {
                deployChute(null);
            }
            thrust = vy < desired - 0.015;
        } else {
            thrust = inputThrust;
        }
        thrust &= fuel > 0;
        if (thrust) {
            vy += thrustAccel;
            fuel--;
        }
        //Propulseurs lateraux (dans la direction du regard du passager)
        boolean side = false;
        if (!auto && (inputForward != 0 || inputStrafe != 0) && fuel > 0 && pilot != null) {
            float yaw = pilot.getYRot() * Mth.DEG_TO_RAD;
            double sin = Mth.sin(yaw), cos = Mth.cos(yaw);
            vx += (inputStrafe * cos - inputForward * sin) * 0.02;
            vz += (inputForward * cos + inputStrafe * sin) * 0.02;
            side = true;
            if (tickCount % 4 == 0) {
                fuel--;
            }
        }
        if (guidance != null && (auto || !side)) {
            //Guidage vers l'aerostat
            double dx = guidance.getX() + 0.5 - getX(), dz = guidance.getZ() + 0.5 - getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            double speed = Math.min(0.6, dist * 0.04);
            if (dist > 0.05) {
                vx += (dx / dist * speed - vx) * 0.12;
                vz += (dz / dist * speed - vz) * 0.12;
            }
            side = dist > 1;
        } else if (auto) {
            vx *= 0.9;
            vz *= 0.9;
        }
        double hdrag = 0.03 + 0.02 * density + (isChuteDeployed() ? 0.1 : 0);
        vx *= 1 - hdrag;
        vz *= 1 - hdrag;
        if (isChuteDeployed()) {
            double terminal = Mth.clamp(0.26 / Math.sqrt(Math.max(0.05, density)), 0.16, 0.7);
            if (vy < -terminal) {
                vy += (-terminal - vy) * 0.18;
            }
        }
        vy = Mth.clamp(vy, -3.0, 1.2);
        entityData.set(FUEL, Math.max(0, fuel));
        setFlag(1, thrust);
        setFlag(2, side);
        double impact = -vy;
        setDeltaMovement(vx, vy, vz);
        move(MoverType.SELF, getDeltaMovement());
        entityData.set(VSPEED, (float) vy);
        entityData.set(HSPEED, (float) Math.sqrt(vx * vx + vz * vz));
        entityData.set(HEIGHT, (float) height);
        //Alarme : trop vite pres du sol
        if (alarmCooldown > 0) {
            alarmCooldown--;
        } else if (height < 40 && impact > SAFE_SPEED + 0.2 && pilot != null) {
            alarmCooldown = 20;
            pilot.playNotifySound(PiSounds.CAPSULE_ALARM.get(), SoundSource.NEUTRAL, 0.8F, 1.0F);
        }
        if (onGround() || isInWater() || isInLava() || verticalCollisionBelow) {
            touchdown(impact, isInWater());
        } else if (getY() < level().getMinBuildHeight() + 2) {
            //Sous le monde (geante gazeuse manquee...) : la capsule est rattrapee par l'aerostat le plus proche
            if (level() instanceof ServerLevel server) {
                BlockPos deck = Aerostats.ensureNear(server, getBlockX(), getBlockZ());
                teleportTo(deck.getX() + 0.5, deck.getY() + 30, deck.getZ() + 0.5);
                guidance = deck;
                setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    /** Contact avec le sol : issue de l'atterrissage selon la vitesse verticale. */
    private void touchdown(double speed, boolean water) {
        entityData.set(LANDED, true);
        entityData.set(CHUTE, false);
        setFlag(1, false);
        setFlag(2, false);
        setDeltaMovement(Vec3.ZERO);
        double effective = water ? speed * 0.5 : speed;
        ServerPlayer pilot = pilot();
        //Le passager sort de la capsule a l'atterrissage
        ejectPassengers();
        String key;
        ChatFormatting color;
        //1.9 (MAJ RP) : XP de Spatial pour un atterrissage pilote reussi (doux = entier, correct = moitie)
        if (pilot != null && effective <= SAFE_SPEED) {
            fr.pioneers.core.profile.PioneerApi.gain(pilot, "spaceflight.landing", effective <= PERFECT_SPEED ? 1 : 0.5, null);
        }
        if (effective <= PERFECT_SPEED) {
            key = "perfect";
            color = ChatFormatting.GREEN;
        } else if (effective <= SAFE_SPEED) {
            key = "good";
            color = ChatFormatting.AQUA;
        } else if (effective <= HARD_SPEED) {
            key = "hard";
            color = ChatFormatting.GOLD;
            if (pilot != null && PiCommonConfig.get(PiCommonConfig.CRASH_DAMAGE, true)) {
                pilot.hurt(damageSources().fall(), (float) (3 + (effective - SAFE_SPEED) * 20));
            }
        } else {
            key = "crash";
            color = ChatFormatting.RED;
            boolean damage = PiCommonConfig.get(PiCommonConfig.CRASH_DAMAGE, true);
            if (damage) {
                level().explode(this, getX(), getY(), getZ(), 1.5F, Level.ExplosionInteraction.NONE);
            }
            if (pilot != null && damage) {
                pilot.hurt(damageSources().fall(), (float) Math.min(40, (effective - 0.4) * 28));
            }
        }
        level().playSound(null, blockPosition(), water ? SoundEvents.GENERIC_SPLASH : PiSounds.CAPSULE_TOUCHDOWN.get(), SoundSource.NEUTRAL, 1.5F,
              key.equals("crash") ? 0.6F : 1.0F);
        if (pilot != null) {
            pilot.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("capsule.pioneers.landing." + key).withStyle(color)));
            pilot.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("capsule.pioneers.landing.speed",
                  String.format("%.1f", effective * 20)).withStyle(ChatFormatting.GRAY)));
        }
    }

    /** Flammes des retrofusees et des propulseurs, poussiere soulevee pres du sol (client). */
    private void effects() {
        if (isMainThrusting()) {
            for (int i = 0; i < 4; i++) {
                double ox = (random.nextDouble() - 0.5) * 0.6, oz = (random.nextDouble() - 0.5) * 0.6;
                level().addParticle(ParticleTypes.FLAME, getX() + ox, getY() - 0.1, getZ() + oz, ox * 0.1, -0.5 - random.nextDouble() * 0.3, oz * 0.1);
            }
            level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() - 0.4, getZ(), 0, -0.2, 0);
            float height = getHeightAboveGround();
            if (height < 8) {
                for (int i = 0; i < 6; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    level().addParticle(ParticleTypes.POOF, getX() + Math.cos(angle), getY() - height + 0.2, getZ() + Math.sin(angle),
                          Math.cos(angle) * 0.4, 0.02, Math.sin(angle) * 0.4);
                }
            }
        }
        if (isSideThrusting()) {
            double angle = random.nextDouble() * Math.PI * 2;
            level().addParticle(ParticleTypes.CLOUD, getX() + Math.cos(angle) * 0.8, getY() + 0.8, getZ() + Math.sin(angle) * 0.8,
                  Math.cos(angle) * 0.15, 0, Math.sin(angle) * 0.15);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!hasLanded() || isVehicle()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider((id, inventory, p) -> new CapsuleMenu(id, inventory, cargo, this),
                  PiLang.CAPSULE.translate()), buffer -> buffer.writeInt(getId()));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return hasLanded();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && !hasLanded();
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.25;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 side = Vec3.directionFromRotation(0, passenger.getYRot()).scale(1.4);
        return new Vec3(getX() + side.x, getY(), getZ() + side.z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        cargo.setItem(0, ItemStack.of(tag.getCompound("cargo")));
        entityData.set(LANDED, tag.getBoolean("landed"));
        if (tag.contains("fuel")) {
            entityData.set(FUEL, tag.getInt("fuel"));
            entityData.set(FUEL_MAX, Math.max(1, tag.getInt("fuelMax")));
        }
        entityData.set(AUTOPILOT, tag.getBoolean("autopilot"));
        if (tag.contains("guidance")) {
            guidance = BlockPos.of(tag.getLong("guidance"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("cargo", cargo.getItem(0).save(new CompoundTag()));
        tag.putBoolean("landed", hasLanded());
        tag.putInt("fuel", getFuel());
        tag.putInt("fuelMax", getFuelMax());
        tag.putBoolean("autopilot", isAutopilot());
        if (guidance != null) {
            tag.putLong("guidance", guidance.asLong());
        }
    }

    // ------------------------------------------------------------------
    // Interpolation cote client : les positions du serveur arrivent par paquets irreguliers ; au lieu de sauter d'une position a
    // l'autre (saccades, fusee qui « disparait » un instant), on glisse vers la derniere recue en quelques ticks.
    // ------------------------------------------------------------------

    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private float lerpYRot;

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpSteps = Math.max(1, steps) + 1;
    }

    /** Avance d'un pas vers la position recue du serveur (client). */
    private void stepLerp() {
        if (!level().isClientSide || lerpSteps <= 0) {
            return;
        }
        double nx = getX() + (lerpX - getX()) / lerpSteps;
        double ny = getY() + (lerpY - getY()) / lerpSteps;
        double nz = getZ() + (lerpZ - getZ()) / lerpSteps;
        setYRot(getYRot() + Mth.wrapDegrees(lerpYRot - getYRot()) / lerpSteps);
        lerpSteps--;
        setPos(nx, ny, nz);
    }
}

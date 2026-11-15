package fr.pioneers.kinetics.sources;

import fr.pioneers.core.RenderClock;
import fr.pioneers.kinetics.KineticAngle;
import fr.pioneers.kinetics.KineticEvents;
import fr.pioneers.kinetics.KineticTile;
import fr.pioneers.kinetics.KineticsConfig;
import fr.pioneers.registries.PiKinetics;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class HorseMillTile extends KineticTile {

    public static final int MAX_ANIMALS = 4;
    public static final double RADIUS = 3.0, TAU_PER_ANIMAL = 5_000, FREE = 6;
    /** Ticks de travail par ration (un ble ; une botte de foin = 9). */
    private static final int RATION = 1_200;

    private final List<UUID> animals = new ArrayList<>();
    private final List<Integer> fed = new ArrayList<>();
    /** Angle des bras (degres, serveur) et force totale. */
    private double arm;
    private double strength;
    private int working, sentWorking = -1;

    public HorseMillTile(BlockPos pos, BlockState state) {
        super(PiKinetics.HORSE_MILL_TILE.get(), pos, state);
    }

    public int count() {
        return animals.size();
    }

    public int working() {
        return working;
    }

    /** Force relative d'un animal (0 : refuse). */
    public static double strength(Mob mob) {
        if (mob instanceof Llama) {
            return 0.4;
        }
        if (mob instanceof Mule) {
            return 0.7;
        }
        if (mob instanceof Donkey) {
            return 0.5;
        }
        if (mob instanceof Camel) {
            return 1.2;
        }
        if (mob instanceof AbstractHorse) {
            return 1;
        }
        if (mob instanceof Cow && !mob.isBaby()) {
            return 0.6;
        }
        return 0;
    }

    boolean harness(Mob mob) {
        if (animals.size() >= MAX_ANIMALS || animals.contains(mob.getUUID())) {
            return false;
        }
        animals.add(mob.getUUID());
        fed.add(RATION);
        mob.setPersistenceRequired();
        setChanged();
        return true;
    }

    int releaseAll() {
        int n = animals.size();
        animals.clear();
        fed.clear();
        setChanged();
        return n;
    }

    @Override
    public double shaftFactor(Direction face) {
        return face.getAxis() == Direction.Axis.Y ? 1 : 0;
    }

    @Override
    public double sourceTorque() {
        return TAU_PER_ANIMAL * strength * KineticsConfig.sourcePower();
    }

    @Override
    public double sourceFreeSpeed() {
        return FREE;
    }

    @Override
    public double inertia() {
        return 60;
    }

    @Override
    public double maxTorque() {
        return 30_000;
    }

    @Override
    public double maxSpeed() {
        return 20;
    }

    @Override
    protected double renderRadius() {
        return 3.5;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        arm = (arm + speed * DEG_PER_TICK) % 360;
        int n = animals.size();
        double total = 0;
        int busy = 0;
        BlockPos trough = level.getGameTime() % 20 == 0 && KineticsConfig.animalFood() ? findTrough(level) : null;
        for (int i = n - 1; i >= 0; i--) {
            Entity entity = level.getEntity(animals.get(i));
            if (entity == null) {
                continue;                                           //Animal pas encore charge : on patiente
            }
            if (!(entity instanceof Mob mob) || !mob.isAlive() || mob.distanceToSqr(Vec3.atCenterOf(worldPosition)) > 16 * 16) {
                animals.remove(i);
                fed.remove(i);
                setChanged();
                continue;
            }
            //Place de l'animal sur la piste : au bout de son bras
            double a = Math.toRadians(arm + i * 360.0 / Math.max(1, n));
            double sx = worldPosition.getX() + 0.5 + Math.cos(a) * RADIUS, sz = worldPosition.getZ() + 0.5 + Math.sin(a) * RADIUS;
            mob.getNavigation().stop();
            boolean inPlace = mob.distanceToSqr(sx, mob.getY(), sz) < 2.6 * 2.6;
            if (!inPlace) {
                //Pas encore attele a sa place : il y marche
                double pace = mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
                mob.getMoveControl().setWantedPosition(sx, worldPosition.getY(), sz, Math.max(0.6, Math.min(1.4, 0.3 / Math.max(0.05, pace))));
            } else {
                //0.19.15 : attele, l'animal suit EXACTEMENT son bras (avant : il visait un point mobile devant lui et son IA le detournait :
                //il « dansait » sur place). Pas borne pour rejoindre la piste en douceur, regard dans le sens de la marche, aucune derive.
                double dx = sx - mob.getX(), dz = sz - mob.getZ(), d = Math.sqrt(dx * dx + dz * dz), step = 0.3;
                if (d > step) {
                    dx *= step / d;
                    dz *= step / d;
                }
                mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
                mob.setPos(mob.getX() + dx, mob.getY(), mob.getZ() + dz);
                if (Math.abs(speed) > 0.05) {
                    double sign = speed >= 0 ? 1 : -1;
                    float yaw = (float) Math.toDegrees(Math.atan2(Math.sin(a) * sign, Math.cos(a) * sign));
                    mob.setYRot(yaw);
                    mob.setYBodyRot(yaw);
                    mob.setYHeadRot(yaw);
                }
            }
            int food = fed.get(i);
            if (trough != null && food < RATION / 2 && level.getBlockEntity(trough) instanceof FeedingTroughBlock.Tile t && t.eat()) {
                food += RATION;
                level.playSound(null, mob.blockPosition(), SoundEvents.HORSE_EAT, SoundSource.NEUTRAL, 0.6F, 1F);
            }
            boolean hungry = KineticsConfig.animalFood() && food <= 0;
            if (inPlace && !hungry) {
                total += strength(mob);
                busy++;
                if (Math.abs(speed) > 0.2) {
                    food--;
                }
            }
            fed.set(i, food);
        }
        strength = total;
        working = busy;
        if (busy > 0 && Math.abs(speed) > 0.5 && level.getGameTime() % 200 == 0) {
            KineticEvents.award(level, worldPosition, "horse_mill");
        }
    }

    private BlockPos findTrough(ServerLevel level) {
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-4, -1, -4), worldPosition.offset(4, 1, 4))) {
            if (level.getBlockEntity(p) instanceof FeedingTroughBlock.Tile t && t.hasFood()) {
                return p.immutable();
            }
        }
        return null;
    }

    @Override
    public void describe(List<Component> lines) {
        super.describe(lines);
        lines.add(Component.translatable("kinetics.pioneers.info.horse_mill", working, animals.size()).withStyle(ChatFormatting.AQUA));
    }

    @Override
    protected boolean clientDirty() {
        boolean dirty = working != sentWorking;
        sentWorking = working;
        return dirty;
    }

    // Client : angle des bras, cale sur celui du serveur (les animaux suivent les bras). 1.0.5-beta (lot 29) : heure serveur estimee
    // (RenderClock, plus l'heure du monde du client) et recalage raccorde sans saut (KineticAngle).
    private final KineticAngle armView = new KineticAngle();

    @Override
    protected void writeClient(CompoundTag tag) {
        tag.putInt("working", working);
        tag.putInt("animals", animals.size());
        tag.putDouble("arm", arm);
        if (level != null) {
            tag.putLong("armAt", level.getGameTime());
        }
    }

    @Override
    protected void readClient(CompoundTag tag) {
        working = tag.getInt("working");
        if (level != null) {
            double now = RenderClock.now();
            //Instant serveur de l'angle envoye (avant le lot 29 : aucun, l'angle valait pour l'instant de reception)
            double at = tag.contains("armAt") ? tag.getLong("armAt") : now;
            armView.receive(speed, tag.getDouble("arm"), at, now, RenderClock.started());
        }
    }

    /**
     * 0.20.46 (audit) : la vitesse arrive par le paquet du reseau, un tick avant le recalage du bras (paquet du bloc) : le bras repart de
     * l'angle affiche pour ne pas sauter.
     */
    @Override
    protected void rotationReceived(double previousSpeed) {
        if (level != null && armView.known()) {
            double now = RenderClock.now();
            armView.receive(speed, armView.angle(now), now, now, false);
        }
    }

    /** Angle des bras (degres) pour le rendu. */
    public float armAngle(float partial) {
        if (level == null || !armView.known()) {
            return 0;
        }
        return (float) (armView.angle(RenderClock.time(partial)) % 360);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (int i = 0; i < animals.size(); i++) {
            CompoundTag a = new CompoundTag();
            a.putUUID("id", animals.get(i));
            a.putInt("fed", fed.get(i));
            list.add(a);
        }
        tag.put("animals", list);
        tag.putDouble("arm", arm);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        animals.clear();
        fed.clear();
        for (Tag t : tag.getList("animals", Tag.TAG_COMPOUND)) {
            CompoundTag a = (CompoundTag) t;
            if (a.hasUUID("id")) {
                animals.add(a.getUUID("id"));
                fed.add(a.getInt("fed"));
            }
        }
        arm = tag.getDouble("arm");
    }
}

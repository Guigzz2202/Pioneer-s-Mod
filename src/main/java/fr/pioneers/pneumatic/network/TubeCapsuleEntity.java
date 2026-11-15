package fr.pioneers.pneumatic.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

/**
 * Capsule de voyageur des tubes pneumatiques : une cabine vitree qui suit le chemin trouve par {@link TubeRoutes}, de la station de depart
 * a celle d'arrivee, avec un profil de vitesse en trapeze (acceleration douce, croisiere a {@value #SPEED_BPS} blocs/s, freinage doux).
 * La position ne depend que du chemin et du nombre de ticks ecoules : serveur et client la calculent chacun de leur cote (le client ignore
 * les corrections de position du serveur), d'ou un mouvement parfaitement fluide. Pas de sauvegarde : un voyage interrompu (deconnexion,
 * arret du serveur) est repris par {@link TubeTravel} grace a la marque laissee sur le joueur.
 */
public class TubeCapsuleEntity extends Entity implements IEntityAdditionalSpawnData {

    /** Vitesse de croisiere (blocs/tick) et acceleration (blocs/tick²). */
    public static final double SPEED = 1.25, ACCEL = 0.0625;
    public static final int SPEED_BPS = 25;
    /** Intervalle (ticks) entre deux verifications des tubes. */
    private static final int CHECK = 4;

    /**
     * 0.20.39 : points de l'axe (centre de la station de depart, courbes des hypertubes ou centres des anciens tubes en blocs, centre de la
     * station d'arrivee) ; troncons d'hypertube et anciens tubes traverses (verifies en route) ; stations de depart et d'arrivee.
     */
    private final List<Vec3> nodes = new ArrayList<>();
    private final List<Integer> segments = new ArrayList<>();
    private final List<BlockPos> blocks = new ArrayList<>();
    private BlockPos origin = BlockPos.ZERO, target = BlockPos.ZERO;
    private double[] cumulative = new double[0];
    private double length, duration;
    private int age;
    /** Vrai quand la capsule laisse elle-meme descendre son passager (arrivee, secours). */
    boolean releasing;

    public TubeCapsuleEntity(EntityType<? extends TubeCapsuleEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Nouvelle capsule (serveur) sur le chemin {@code route} depuis la station {@code origin}. */
    static TubeCapsuleEntity create(Level level, BlockPos origin, TubeRoutes.Route route) {
        TubeCapsuleEntity capsule = new TubeCapsuleEntity(PiPneumaticNetwork.TUBE_CAPSULE.get(), level);
        capsule.nodes.addAll(route.points());
        capsule.segments.addAll(route.segments());
        capsule.blocks.addAll(route.tubes());
        capsule.origin = origin;
        capsule.target = route.station();
        capsule.prepare();
        Vec3 start = capsule.positionAt(0);
        capsule.moveTo(start.x, start.y, start.z, 0, 0);
        return capsule;
    }

    private void prepare() {
        cumulative = new double[nodes.size()];
        for (int i = 1; i < nodes.size(); i++) {
            cumulative[i] = cumulative[i - 1] + nodes.get(i).distanceTo(nodes.get(i - 1));
        }
        length = cumulative.length == 0 ? 0 : cumulative[cumulative.length - 1];
        double accelTime = SPEED / ACCEL, accelDistance = 0.5 * ACCEL * accelTime * accelTime;
        duration = length >= 2 * accelDistance ? 2 * accelTime + (length - 2 * accelDistance) / SPEED : 2 * Math.sqrt(length / ACCEL);
    }

    /** Distance parcourue apres {@code t} ticks (trapeze, ou triangle pour les trajets courts). */
    private double distanceAt(double t) {
        if (t <= 0) {
            return 0;
        }
        if (t >= duration) {
            return length;
        }
        double accelTime = Math.min(SPEED / ACCEL, duration / 2);
        if (t < accelTime) {
            return 0.5 * ACCEL * t * t;
        }
        double remaining = duration - t;
        if (remaining < accelTime) {
            return length - 0.5 * ACCEL * remaining * remaining;
        }
        return 0.5 * ACCEL * accelTime * accelTime + SPEED * (t - accelTime);
    }

    /** Position de l'entite (bas de la boite : axe du tube moins un demi-bloc) apres {@code t} ticks. */
    Vec3 positionAt(double t) {
        if (nodes.isEmpty()) {
            return position();
        }
        double s = distanceAt(t);
        int i = segment(s);
        Vec3 a = nodes.get(i).subtract(0, 0.5, 0);
        if (i + 1 >= nodes.size()) {
            return a;
        }
        Vec3 b = nodes.get(i + 1).subtract(0, 0.5, 0);
        double span = cumulative[i + 1] - cumulative[i];
        return span <= 0 ? a : a.lerp(b, (s - cumulative[i]) / span);
    }

    /** Indice du segment contenant la distance {@code s}. */
    private int segment(double s) {
        //Recherche dichotomique (les courbes des hypertubes ont un point tous les quarts de bloc)
        int lo = 0, hi = Math.max(0, cumulative.length - 2);
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (cumulative[mid] <= s) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /** Indice du dernier point de passage atteint. */
    int reached() {
        return segment(distanceAt(age));
    }

    List<Vec3> nodes() {
        return nodes;
    }

    List<Integer> segments() {
        return segments;
    }

    List<BlockPos> blocks() {
        return blocks;
    }

    BlockPos origin() {
        return origin;
    }

    BlockPos target() {
        return target;
    }

    boolean arrived() {
        return age >= duration;
    }

    public int age() {
        return age;
    }

    @Override
    public void tick() {
        super.tick();
        if (nodes.size() < 2) {
            if (!level().isClientSide) {
                discard();
            }
            return;
        }
        age++;
        Vec3 pos = positionAt(age);
        double dx = pos.x - getX(), dy = pos.y - getY(), dz = pos.z - getZ();
        if (dx * dx + dz * dz > 1.0E-6) {
            //La cabine s'oriente dans le sens de la marche (cap, et pente des hypertubes courbes)
            setYRot((float) (Math.atan2(-dx, dz) * (180 / Math.PI)));
        }
        if (dx * dx + dy * dy + dz * dz > 1.0E-6) {
            setXRot((float) (-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180 / Math.PI)));
        }
        setPos(pos.x, pos.y, pos.z);
        if (!level().isClientSide && level() instanceof ServerLevel server) {
            Entity passenger = getFirstPassenger();
            if (!(passenger instanceof ServerPlayer player)) {
                releasing = true;
                discard();
                return;
            }
            if (arrived()) {
                TubeTravel.arrive(server, this, player);
            } else if (age % CHECK == 0) {
                TubeTravel.check(server, this, player);
            }
        }
    }

    /** Laisse descendre le passager (le seul moyen de sortir) puis disparait. */
    void release() {
        releasing = true;
        ejectPassengers();
        discard();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        //Le client calcule lui-meme la position : les corrections du serveur feraient sauter la capsule
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull MoveFunction move) {
        //Pieds sous la capsule : les yeux du voyageur (rendu reduit) arrivent au centre du tube
        move.accept(passenger, getX(), getY() - 1.02, getZ());
        passenger.fallDistance = 0;
    }

    @Override
    protected boolean canAddPassenger(@NotNull Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @NotNull
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buf) {
        buf.writeVarInt(nodes.size());
        for (Vec3 v : nodes) {
            buf.writeFloat((float) v.x);
            buf.writeFloat((float) v.y);
            buf.writeFloat((float) v.z);
        }
        buf.writeVarInt(age);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buf) {
        int n = Math.min(65_536, buf.readVarInt());
        nodes.clear();
        for (int i = 0; i < n; i++) {
            nodes.add(new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()));
        }
        age = buf.readVarInt();
        prepare();
        Vec3 pos = positionAt(age);
        setPos(pos.x, pos.y, pos.z);
        setOldPosAndRot();
    }
}

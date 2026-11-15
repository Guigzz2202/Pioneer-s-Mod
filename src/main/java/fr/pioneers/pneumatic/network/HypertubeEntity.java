package fr.pioneers.pneumatic.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.39 : un troncon d'hypertube vu dans le monde : un tube de verre courbe cercle de laiton, d'un seul tenant (une entite, plus des blocs).
 * Vue du {@link HypertubeWorld} (jamais sauvegardee : recreee par lui), placee au milieu de la corde ; la courbe arrive avec les donnees
 * d'apparition. Ni collision ni visee (on traverse le tube, on casse par la cle ou l'hypertube en main : {@code HypertubeClient}) ; boite de
 * rendu = toute la courbe.
 */
public class HypertubeEntity extends Entity implements IEntityAdditionalSpawnData {

    private int segmentId = -1;
    @Nullable
    private HypertubeCurve curve;
    private boolean stationA, stationB;
    private AABB cull = new AABB(0, 0, 0, 0, 0, 0);
    /** Client : maillage mis en cache par le rendu. */
    @Nullable
    public Object clientMesh;

    public HypertubeEntity(EntityType<? extends HypertubeEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    static HypertubeEntity create(ServerLevel level, HypertubeWorld.Segment s) {
        HypertubeEntity e = new HypertubeEntity(PiPneumaticNetwork.HYPERTUBE.get(), level);
        e.set(s.id(), HypertubeWorld.get(level).curve(s), s.stationA() != null, s.stationB() != null);
        return e;
    }

    private void set(int id, HypertubeCurve c, boolean sa, boolean sb) {
        segmentId = id;
        curve = c;
        stationA = sa;
        stationB = sb;
        Vec3 mid = c.a.add(c.b).scale(0.5);
        setPos(mid.x, mid.y, mid.z);
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (Vec3 p : c.points()) {
            x0 = Math.min(x0, p.x);
            y0 = Math.min(y0, p.y);
            z0 = Math.min(z0, p.z);
            x1 = Math.max(x1, p.x);
            y1 = Math.max(y1, p.y);
            z1 = Math.max(z1, p.z);
        }
        cull = new AABB(x0, y0, z0, x1, y1, z1).inflate(1);
    }

    public int segmentId() {
        return segmentId;
    }

    @Nullable
    public HypertubeCurve curve() {
        return curve;
    }

    public boolean stationA() {
        return stationA;
    }

    public boolean stationB() {
        return stationB;
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (level() instanceof ServerLevel server) {
            HypertubeWorld.get(server).viewAdded(this);
        } else {
            HypertubeClientViews.add(this);
        }
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (level() instanceof ServerLevel server) {
            HypertubeWorld.get(server).viewRemoved(this);
        } else {
            HypertubeClientViews.remove(this);
        }
    }

    @Override
    public void tick() {
        //Immobile : rien a calculer ; le monde des hypertubes retire les vues orphelines
        if (!level().isClientSide && tickCount % 40 == 0 && level() instanceof ServerLevel server && !HypertubeWorld.get(server).exists(segmentId)) {
            discard();
        }
    }

    @NotNull
    @Override
    public AABB getBoundingBoxForCulling() {
        return cull;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 192 * 192;
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
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
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
        buf.writeVarInt(segmentId);
        HypertubeCurve c = curve;
        Vec3[] d = c.deviation();
        for (Vec3 v : new Vec3[]{c.a, c.b, c.ta, c.tb, d[0], d[1], d[2]}) {
            buf.writeDouble(v.x);
            buf.writeDouble(v.y);
            buf.writeDouble(v.z);
        }
        buf.writeBoolean(stationA);
        buf.writeBoolean(stationB);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        Vec3[] v = new Vec3[7];
        for (int i = 0; i < 7; i++) {
            v[i] = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        }
        boolean sa = buf.readBoolean(), sb = buf.readBoolean();
        if (v[0].distanceTo(v[1]) <= HypertubeCurve.MAX_LENGTH * 2) {
            set(id, new HypertubeCurve(v[0], v[1], v[2], v[3], new Vec3[]{v[4], v[5], v[6]}), sa, sb);
            setOldPosAndRot();
        }
    }
}

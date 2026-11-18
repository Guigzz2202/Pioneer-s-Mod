package fr.pioneers.core.network;

import fr.pioneers.logistics.robotics.client.ClientRobotics;
import fr.pioneers.logistics.robotics.RobotTask;
import fr.pioneers.logistics.robotics.RoboticOrder;
import fr.pioneers.logistics.robotics.RoboticsOrders;
import fr.pioneers.logistics.robotics.RoboticsScheduler;
import fr.pioneers.logistics.robotics.works.WorksData;
import fr.pioneers.logistics.robotics.works.WorksPlan;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Planificateur de travaux : envoi d'un plan valide (nouvel ordre ou remplacement), et lecture du plan d'un ordre existant.
 * <p>
 * 0.20.46 (audit) : le plan part compresse et decoupe en morceaux de {@link #CHUNK} octets (un paquet client -> serveur est limite a
 * 32 767 octets : au-dela, le joueur etait expulse d'un serveur dedie), puis il est reassemble cote serveur (un transfert par joueur).
 */
public final class PacketWorks {

    /** Taille maximale d'un plan transmis (octets NBT comptes a la lecture, et octets compresses recus). */
    private static final long MAX_BYTES = 4_000_000;
    private static final int MAX_DISTANCE = 256;
    /** Morceau de plan compresse par paquet (le premier porte aussi l'ordre, quelques centaines d'octets). */
    public static final int CHUNK = 28_000;
    private static final int MAX_PARTS = (int) (MAX_BYTES / CHUNK) + 1;

    /** Transfert en cours d'un joueur. */
    private static final class Transfer {

        final int id, parts;
        final CompoundTag order;
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        int next;

        Transfer(int id, int parts, CompoundTag order) {
            this.id = id;
            this.parts = parts;
            this.order = order;
        }
    }

    private static final Map<UUID, Transfer> TRANSFERS = new HashMap<>();
    private static int nextTransfer;

    private PacketWorks() {
    }

    static void forget(UUID player) {
        TRANSFERS.remove(player);
    }

    static void forgetAll() {
        TRANSFERS.clear();
    }

    private static CompoundTag readBig(FriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt(new NbtAccounter(MAX_BYTES));
        return tag == null ? new CompoundTag() : tag;
    }

    /**
     * Client : envoie le plan en morceaux compresses. Faux si le plan compresse depasse la taille admise (rien n'est envoye).
     */
    public static boolean submit(CompoundTag order, CompoundTag plan) {
        byte[] bytes;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(plan, out);
            bytes = out.toByteArray();
        } catch (IOException e) {
            return false;
        }
        if (bytes.length > MAX_BYTES) {
            return false;
        }
        int parts = Math.max(1, (bytes.length + CHUNK - 1) / CHUNK);
        int id = ++nextTransfer;
        for (int part = 0; part < parts; part++) {
            int from = part * CHUNK, to = Math.min(bytes.length, from + CHUNK);
            byte[] chunk = new byte[to - from];
            System.arraycopy(bytes, from, chunk, 0, chunk.length);
            PiNetwork.sendToServer(new Submit(id, part, parts, part == 0 ? order : null, chunk));
        }
        return true;
    }

    /** Lecture bornee d'un plan compresse (la taille decompressee est comptee : pas de bombe de decompression). */
    @Nullable
    private static CompoundTag decompress(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes))))) {
            return NbtIo.read(in, new NbtAccounter(MAX_BYTES));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Client -> serveur : un morceau du plan a enregistrer (ordre 0 : nouveau) ; le premier morceau porte les reglages de l'ordre
     * (priorite, unites, affectation).
     */
    public record Submit(int transfer, int part, int parts, @Nullable CompoundTag order, byte[] data) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(transfer);
            buffer.writeVarInt(part);
            buffer.writeVarInt(parts);
            buffer.writeNbt(order);
            buffer.writeByteArray(data);
        }

        public static Submit decode(FriendlyByteBuf buffer) {
            int transfer = buffer.readVarInt(), part = buffer.readVarInt(), parts = buffer.readVarInt();
            CompoundTag order = buffer.readNbt();
            return new Submit(transfer, part, parts, order, buffer.readByteArray(CHUNK));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player == null || player.isSpectator() || parts < 1 || parts > MAX_PARTS || part < 0 || part >= parts) {
                return;
            }
            UUID id = player.getUUID();
            Transfer current;
            if (part == 0) {
                current = new Transfer(transfer, parts, order == null ? new CompoundTag() : order);
                TRANSFERS.put(id, current);
            } else {
                current = TRANSFERS.get(id);
                if (current == null || current.id != transfer || current.parts != parts || current.next != part) {
                    TRANSFERS.remove(id);
                    return;
                }
            }
            current.data.write(data, 0, data.length);
            current.next++;
            if (current.data.size() > MAX_BYTES) {
                TRANSFERS.remove(id);
                return;
            }
            if (current.next < current.parts) {
                return;
            }
            TRANSFERS.remove(id);
            CompoundTag plan = decompress(current.data.toByteArray());
            if (plan != null) {
                apply(player, current.order, plan);
            }
        }
    }

    /** Plan complet recu : verifications puis enregistrement de l'ordre. */
    private static void apply(ServerPlayer player, CompoundTag order, CompoundTag plan) {
        ServerLevel level = player.serverLevel();
        WorksPlan works = WorksPlan.load(plan, WorksPlan.blocks(level));
        if (!works.isValid() || works.ops.size() > WorksPlan.maxOps(player)) {
            return;
        }
        BlockPos min = works.min(), max = works.max();
        if (min != null) {
            //1.9 (MAJ RP) : XP de Robotique pour un plan de travaux programme (une fois par jour et par chantier)
            fr.pioneers.core.profile.PioneerApi.gain(player, "robotics.program", 1, min);
        }
        if (min == null || max == null || min.distSqr(player.blockPosition()) > (double) MAX_DISTANCE * MAX_DISTANCE
            || max.distSqr(player.blockPosition()) > (double) MAX_DISTANCE * MAX_DISTANCE) {
            return;
        }
        //0.20.46 (audit) : chantier hors de la zone protegee du spawn et dans la bordure du monde
        if (!level.mayInteract(player, min) || !level.mayInteract(player, max)) {
            return;
        }
        RoboticsOrders orders = RoboticsOrders.get(level);
        RoboticOrder incoming = RoboticOrder.load(order);
        RoboticOrder existing = incoming.id > 0 ? orders.get(incoming.id) : null;
        if (existing != null && existing.task != RobotTask.BUILDER) {
            incoming.id = 0;
        } else if (existing != null && !PacketRobotics.mayEdit(player, existing)) {
            //0.20.46 (audit) : on ne remplace que les travaux a portee du joueur
            return;
        }
        incoming.type = RoboticOrder.Type.ZONE;
        incoming.task = RobotTask.BUILDER;
        incoming.min = min;
        incoming.max = max;
        incoming.rest = false;
        incoming.status = RoboticOrder.Status.WAITING;
        if (orders.put(incoming)) {
            WorksData.get(level).put(incoming.id, works);
            RoboticsScheduler.forget(level, incoming.id);
            PacketRobotics.changed(level, player);
        }
    }

    /** Client -> serveur : le plan d'un ordre de travaux (pour le modifier). */
    public record Request(int order) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(order);
        }

        public static Request decode(FriendlyByteBuf buffer) {
            return new Request(buffer.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            //Demande unique a l'ouverture de l'editeur (le client ne la renvoie pas) : seule la meme demande repetee est ecartee
            if (player == null || !PacketRate.allowKeyed(player, PacketRate.WORKS, order)) {
                return;
            }
            WorksPlan plan = WorksData.get(player.serverLevel()).plan(order);
            PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Data(order, plan == null ? new CompoundTag() : plan.save()));
        }
    }

    /** Serveur -> client : le plan demande. */
    public record Data(int order, CompoundTag plan) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(order);
            buffer.writeNbt(plan);
        }

        public static Data decode(FriendlyByteBuf buffer) {
            return new Data(buffer.readVarInt(), readBig(buffer));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            int id = order;
            CompoundTag data = plan;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRobotics.worksPlan(id, data));
        }
    }
}

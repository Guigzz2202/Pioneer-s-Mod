package fr.pioneers.network;

import fr.pioneers.transport.logistic.DiagramService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * 1.11 (D2b, spec §5.4) : paquets du diagramme du cable logistique.
 * <ul>
 *     <li>{@link Snapshot} (serveur -> client) : tout le diagramme, NBT compresse (ouverture, apres une edition, quand le reseau change) ;</li>
 *     <li>{@link Stats} (serveur -> client) : debits, etats et corps, une fois par seconde tant que l'ecran est ouvert ;</li>
 *     <li>{@link Edit} (client -> serveur) : une operation ({@code DiagramEdits}), bornee a {@link #EDIT_MAX} octets ;</li>
 *     <li>{@link Close} (client -> serveur) : fin de session.</li>
 * </ul>
 * L'ouverture passe par le clic droit sur le cable (cote serveur) : pas de paquet d'ouverture.
 */
public final class PacketLogistic {

    /** Taille max d'un instantane compresse (octets) et d'une edition. */
    public static final int SNAPSHOT_MAX = 1 << 20, EDIT_MAX = 30_000;

    private PacketLogistic() {
    }

    public static void send(ServerPlayer player, Object packet) {
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    // ------------------------------------------------------------------
    // Serveur -> client
    // ------------------------------------------------------------------

    /** Instantane complet ({@code open} : ouvrir l'ecran s'il ne l'est pas ; {@code gone} : le reseau n'existe plus, fermer). */
    public record Snapshot(byte[] data) {

        public static Snapshot of(CompoundTag tag) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                NbtIo.writeCompressed(tag, out);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return new Snapshot(out.toByteArray());
        }

        public CompoundTag tag() {
            try {
                return NbtIo.readCompressed(new ByteArrayInputStream(data));
            } catch (IOException | RuntimeException e) {
                return new CompoundTag();
            }
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeByteArray(data);
        }

        public static Snapshot decode(FriendlyByteBuf buffer) {
            return new Snapshot(buffer.readByteArray(SNAPSHOT_MAX));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                  () -> () -> fr.pioneers.client.logistic.LogisticDiagramClient.snapshot(tag())));
            context.get().setPacketHandled(true);
        }
    }

    /** Debits (une fois par seconde). */
    public record Stats(CompoundTag tag) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeNbt(tag);
        }

        public static Stats decode(FriendlyByteBuf buffer) {
            CompoundTag tag = buffer.readAnySizeNbt();
            return new Stats(tag == null ? new CompoundTag() : tag);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                  () -> () -> fr.pioneers.client.logistic.LogisticDiagramClient.stats(tag)));
            context.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------
    // Client -> serveur
    // ------------------------------------------------------------------

    /** Une operation d'edition (verifiee par la session : distance, droits, bornes). */
    public record Edit(CompoundTag op) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeNbt(op);
        }

        public static Edit decode(FriendlyByteBuf buffer) {
            CompoundTag tag = buffer.readNbt();
            return new Edit(tag == null ? new CompoundTag() : tag);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> {
                if (player != null) {
                    DiagramService.edit(player, op);
                }
            });
            context.get().setPacketHandled(true);
        }
    }

    /** Fin de session (ecran ferme). */
    public record Close() {

        public void encode(FriendlyByteBuf buffer) {
        }

        public static Close decode(FriendlyByteBuf buffer) {
            return new Close();
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> {
                if (player != null) {
                    DiagramService.close(player);
                }
            });
            context.get().setPacketHandled(true);
        }
    }
}

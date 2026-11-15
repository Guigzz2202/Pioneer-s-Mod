package fr.pioneers.core.network;

import fr.pioneers.logistics.rail.client.ClientRail;
import fr.pioneers.logistics.rail.RailNetworking;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * 0.20.0 : paquets du chemin de fer : fiche d'un train et de son horaire, fiche d'une gare (serveur -> client) ; modifications (client ->
 * serveur), verifiees par {@link RailNetworking} (joueur pres du train ou de la gare).
 */
public final class PacketRail {

    private PacketRail() {
    }

    /** Serveur -> client : fiche d'un train ({@code kind} 0) ou d'une gare (1). */
    public record Info(int kind, CompoundTag data) {

        public void encode(FriendlyByteBuf buf) {
            buf.writeByte(kind);
            buf.writeNbt(data);
        }

        public static Info decode(FriendlyByteBuf buf) {
            return new Info(buf.readByte(), buf.readNbt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRail.info(kind, data == null ? new CompoundTag() : data));
        }
    }

    /** Client -> serveur : action sur un train. */
    public record TrainEdit(UUID train, int action, int value, String text, CompoundTag data) {

        public static final int REFRESH = 0, NAME = 1, MODE = 2, SCHEDULE = 3, BOARD = 4, DECOUPLE = 5, SKIP = 6, FLIP = 7, OPEN = 8, INVENTORY = 9;
        /** 0.20.5 : groupe (texte), interruptions activees (data "names"), service logistique, bibliotheque, envoi, arrets temporaires. */
        public static final int GROUP = 10, INTERRUPTS = 11, LOGISTIC = 12, INTERRUPT_PUT = 13, INTERRUPT_DEL = 14, SEND = 15, CANCEL_TEMP = 16;

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(train);
            buf.writeVarInt(action);
            buf.writeVarInt(value);
            buf.writeUtf(text, 64);
            buf.writeNbt(data);
        }

        public static TrainEdit decode(FriendlyByteBuf buf) {
            return new TrainEdit(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64), buf.readNbt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                RailNetworking.editTrain(player, this);
            }
        }
    }

    /** Client -> serveur : renommer une gare, regler sa limite ; 0.20.5 : role logistique ({@code logistics} vide : inchange). */
    public record StationEdit(BlockPos pos, String name, int limit, CompoundTag logistics) {

        public StationEdit(BlockPos pos, String name, int limit) {
            this(pos, name, limit, new CompoundTag());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUtf(name, 64);
            buf.writeVarInt(limit + 1);
            buf.writeNbt(logistics);
        }

        public static StationEdit decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            String name = buf.readUtf(64);
            int limit = buf.readVarInt() - 1;
            CompoundTag t = buf.readNbt();
            return new StationEdit(pos, name, limit, t == null ? new CompoundTag() : t);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                RailNetworking.editStation(player, this);
            }
        }
    }
}

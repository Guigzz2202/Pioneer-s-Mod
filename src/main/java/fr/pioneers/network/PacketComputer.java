package fr.pioneers.network;

import fr.pioneers.computing.ComputerTile;
import fr.pioneers.computing.client.ComputerScreen;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Terminal de l'ordinateur (0.17.1). Client -> serveur : sauver (texte decoupe en morceaux de 8 000 caracteres, au plus 64 Ko), ouvrir,
 * supprimer, lancer un fichier, saisie de console. Serveur -> client : contenu d'un fichier ouvert.
 */
public final class PacketComputer {

    public static final int SAVE = 0, OPEN = 1, DELETE = 2, RUN = 3, INPUT = 4;
    public static final int CHUNK = 8_000, MAX_TEXT = 65_536, MAX_PATH = 40;

    /** Morceaux en cours de reception : joueur -> texte partiel. */
    private static final Map<UUID, StringBuilder> PENDING = new HashMap<>();

    private PacketComputer() {
    }

    /** 0.20.46 (audit) : texte partiel d'un joueur deconnecte. */
    static void forget(UUID player) {
        PENDING.remove(player);
    }

    static void forgetAll() {
        PENDING.clear();
    }

    public static void sendContent(ServerPlayer player, BlockPos pos, String path, String text) {
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Content(pos, path, text));
    }

    /** 0.20.46 (audit) : console, fichiers et liaisons d'un ordinateur, au seul joueur qui a son interface ouverte. */
    public static void sendState(ServerPlayer player, BlockPos pos, CompoundTag tag) {
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new State(pos, tag));
    }

    /** Client -> serveur. {@code part}/{@code parts} : morceau n sur m du texte (sauver). */
    public record Action(BlockPos pos, int kind, String path, String text, int part, int parts) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeVarInt(kind);
            buffer.writeUtf(path, MAX_PATH);
            buffer.writeUtf(text, CHUNK);
            buffer.writeVarInt(part);
            buffer.writeVarInt(parts);
        }

        public static Action decode(FriendlyByteBuf buffer) {
            return new Action(buffer.readBlockPos(), buffer.readVarInt(), buffer.readUtf(MAX_PATH), buffer.readUtf(CHUNK), buffer.readVarInt(),
                  buffer.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            ComputerTile tile = PacketGuard.tile(player, pos, ComputerTile.class);
            context.get().setPacketHandled(true);
            if (tile == null || player.isSpectator()) {
                return;
            }
            String text = text();
            if (kind == SAVE) {
                if (parts < 1 || parts > MAX_TEXT / CHUNK + 1 || part < 0 || part >= parts) {
                    return;
                }
                StringBuilder b = part == 0 ? new StringBuilder() : PENDING.get(player.getUUID());
                if (b == null) {
                    return;
                }
                b.append(text);
                if (b.length() > MAX_TEXT) {
                    PENDING.remove(player.getUUID());
                    return;
                }
                if (part < parts - 1) {
                    PENDING.put(player.getUUID(), b);
                    return;
                }
                PENDING.remove(player.getUUID());
                text = b.toString();
            } else if (kind == INPUT) {
                text = PacketGuard.sanitize(text, 256);
            }
            tile.handlePacket(player, kind, path, text);
        }
    }

    /** Serveur -> client : contenu d'un fichier ouvert dans l'editeur. */
    public record Content(BlockPos pos, String path, String text) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeUtf(path, MAX_PATH);
            buffer.writeUtf(text, MAX_TEXT);
        }

        public static Content decode(FriendlyByteBuf buffer) {
            return new Content(buffer.readBlockPos(), buffer.readUtf(MAX_PATH), buffer.readUtf(MAX_TEXT));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().setPacketHandled(true);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ComputerScreen.receive(pos, path, text));
        }
    }

    /** 0.20.46 (audit) : serveur -> client, console, fichiers et liaisons (interface ouverte ; avant : dans la mise a jour du bloc). */
    public record State(BlockPos pos, CompoundTag tag) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeNbt(tag);
        }

        public static State decode(FriendlyByteBuf buffer) {
            BlockPos pos = buffer.readBlockPos();
            CompoundTag tag = buffer.readAnySizeNbt();
            return new State(pos, tag == null ? new CompoundTag() : tag);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().setPacketHandled(true);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ComputerScreen.receiveState(pos, tag));
        }
    }
}

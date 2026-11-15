package fr.pioneers.network;

import fr.pioneers.client.robotics.ClientRobotics;
import fr.pioneers.robotics.RobotUnitEntity;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Fiche d'une unite (Maj + clic droit) : le serveur envoie l'etat, le client rafraichit, renomme ou rappelle l'unite.
 */
public final class PacketRobotUnit {

    /** Longueur maximale d'un nom d'unite. */
    public static final int NAME_LENGTH = 24;

    private PacketRobotUnit() {
    }

    public static void sendInfo(ServerPlayer player, RobotUnitEntity unit, ServerLevel level) {
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Info(unit.infoTag(level)));
    }

    /** Serveur -> client : etat de l'unite (ouvre la fiche, ou la met a jour). */
    public record Info(CompoundTag tag) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeNbt(tag);
        }

        public static Info decode(FriendlyByteBuf buffer) {
            CompoundTag tag = buffer.readNbt();
            return new Info(tag == null ? new CompoundTag() : tag);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            CompoundTag data = tag;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRobotics.unitInfo(data));
        }
    }

    /** Client -> serveur : rafraichir, renommer, rappeler. */
    public record Action(int entityId, int action, String name) {

        public static final int REFRESH = 0, RENAME = 1, RECALL = 2;

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(entityId);
            buffer.writeVarInt(action);
            buffer.writeUtf(name, NAME_LENGTH * 4);
        }

        public static Action decode(FriendlyByteBuf buffer) {
            return new Action(buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(NAME_LENGTH * 4));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player == null || player.isSpectator()) {
                return;
            }
            ServerLevel level = player.serverLevel();
            if (!(level.getEntity(entityId) instanceof RobotUnitEntity unit) || !unit.isAlive() || unit.distanceToSqr(player) > 64 * 64) {
                return;
            }
            switch (action) {
                case RENAME -> unit.rename(level, PacketGuard.sanitize(name, NAME_LENGTH));
                case RECALL -> unit.recall(level);
                default -> {
                }
            }
            sendInfo(player, unit, level);
        }
    }
}

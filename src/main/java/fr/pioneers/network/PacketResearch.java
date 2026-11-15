package fr.pioneers.network;

import fr.pioneers.client.research.ClientResearch;
import fr.pioneers.space.research.ResearchNode;
import fr.pioneers.space.research.ResearchState;
import fr.pioneers.tile.TileEntityResearchTable;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Paquets de l'arbre technologique.
 */
public final class PacketResearch {

    private PacketResearch() {
    }

    /** Serveur -> clients : etat complet de la recherche. */
    public record Sync(int[] completed, int active, int[] progress, int[] samples, int[] queue, int labs, String[] milestones) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarIntArray(completed);
            buffer.writeVarInt(active);
            buffer.writeVarIntArray(progress);
            buffer.writeVarIntArray(samples);
            buffer.writeVarIntArray(queue);
            buffer.writeVarInt(labs);
            buffer.writeVarInt(milestones.length);
            for (String id : milestones) {
                buffer.writeUtf(id);
            }
        }

        public static Sync decode(FriendlyByteBuf buffer) {
            int[] completed = buffer.readVarIntArray();
            int active = buffer.readVarInt();
            int[] progress = buffer.readVarIntArray(), samples = buffer.readVarIntArray(), queue = buffer.readVarIntArray();
            int labs = buffer.readVarInt();
            String[] milestones = new String[Math.min(256, buffer.readVarInt())];
            for (int i = 0; i < milestones.length; i++) {
                milestones[i] = buffer.readUtf(256);
            }
            return new Sync(completed, active, progress, samples, queue, labs, milestones);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            Sync sync = this;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                  () -> () -> ClientResearch.update(sync));
        }
    }

    /** Serveur -> clients : une recherche vient d'aboutir (notification). */
    public record Completed(int node) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(node);
        }

        public static Completed decode(FriendlyByteBuf buffer) {
            return new Completed(buffer.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ResearchNode completed = ResearchNode.byIndex(node);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                  () -> () -> ClientResearch.onCompleted(completed));
        }
    }

    /** Client -> serveur : lancer, mettre en file ou retirer une recherche (depuis une table de recherche). */
    public record Select(BlockPos table, int node, int action) {

        public static final int START = 0, ENQUEUE = 1, REMOVE = 2;

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(table);
            buffer.writeVarInt(node);
            buffer.writeVarInt(action);
        }

        public static Select decode(FriendlyByteBuf buffer) {
            return new Select(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player == null || PacketGuard.tile(player, table, TileEntityResearchTable.class) == null) {
                return;
            }
            ResearchState state = ResearchState.get(player.server);
            ResearchNode target = ResearchNode.byIndex(node);
            switch (action) {
                case START -> state.start(player.server, target);
                case ENQUEUE -> state.enqueue(player.server, target);
                case REMOVE -> state.dequeue(player.server, target);
                default -> {
                }
            }
        }
    }
}

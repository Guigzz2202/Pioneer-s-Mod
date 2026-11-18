package fr.pioneers.core.network;

import fr.pioneers.client.look.LookCardClient;
import fr.pioneers.core.common.look.LookCards;
import fr.pioneers.farm.FarmCard;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * 1.4 : fiche au regard ({@link LookCards}) : le client la demande pour le bloc vise, le serveur la renvoie. 1.6 : lignes typees (texte,
 * indication, libelle + valeur, jauge, icones) ; le format change avec la version du canal.
 */
public final class PacketLookCard {

    private PacketLookCard() {
    }

    /** Distance maximale (blocs) entre le joueur et le bloc dont il demande la fiche. */
    private static final double REACH = 9;

    /** Client -> serveur : fiche du bloc vise. */
    public record Request(BlockPos pos, boolean sneaking) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeBoolean(sneaking);
        }

        public static Request decode(FriendlyByteBuf buffer) {
            return new Request(buffer.readBlockPos(), buffer.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player == null || !PacketRate.allowKeyed(player, PacketRate.LOOK, pos.asLong() ^ (sneaking ? 1L << 63 : 0))) {
                return;
            }
            ServerLevel level = player.serverLevel();
            if (!level.isLoaded(pos) || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH * REACH) {
                return;
            }
            FarmCard card = LookCards.build(level, pos, player, sneaking);
            PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Reply(pos, card));
        }
    }

    /** Serveur -> client : la fiche (null = aucune). */
    public record Reply(BlockPos pos, @Nullable FarmCard card) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeBoolean(card != null);
            if (card != null) {
                buffer.writeItem(card.icon());
                buffer.writeComponent(card.title());
                buffer.writeInt(card.accent());
                buffer.writeVarInt(card.lines().size());
                for (FarmCard.Line l : card.lines()) {
                    //1.6 : sorte, libelle/texte, valeur, jauge, couleur, icones et leur avancement
                    buffer.writeByte(l.kind().ordinal());
                    writeOptional(buffer, l.text());
                    writeOptional(buffer, l.value());
                    buffer.writeFloat(l.bar());
                    buffer.writeInt(l.colour());
                    int n = Math.min(MAX_ITEMS, l.items().size());
                    buffer.writeVarInt(n);
                    for (int i = 0; i < n; i++) {
                        buffer.writeItem(l.items().get(i));
                        buffer.writeFloat(i < l.fill().length ? l.fill()[i] : -1);
                    }
                }
            }
        }

        public static Reply decode(FriendlyByteBuf buffer) {
            BlockPos pos = buffer.readBlockPos();
            if (!buffer.readBoolean()) {
                return new Reply(pos, null);
            }
            var icon = buffer.readItem();
            var title = buffer.readComponent();
            int accent = buffer.readInt();
            int n = Math.min(32, buffer.readVarInt());
            List<FarmCard.Line> lines = new ArrayList<>();
            FarmCard.Kind[] kinds = FarmCard.Kind.values();
            for (int i = 0; i < n; i++) {
                FarmCard.Kind kind = kinds[Math.min(kinds.length - 1, buffer.readUnsignedByte())];
                Component text = readOptional(buffer), value = readOptional(buffer);
                float bar = buffer.readFloat();
                int colour = buffer.readInt();
                int m = Math.min(MAX_ITEMS, buffer.readVarInt());
                List<ItemStack> items = new ArrayList<>();
                float[] fill = new float[m];
                for (int j = 0; j < m; j++) {
                    items.add(buffer.readItem());
                    fill[j] = buffer.readFloat();
                }
                lines.add(new FarmCard.Line(kind, text, value, bar, colour, items, fill));
            }
            return new Reply(pos, new FarmCard(icon, title, accent, lines));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LookCardClient.receive(pos, card));
        }
    }

    /** Icones par ligne au plus (rangee d'objets). */
    private static final int MAX_ITEMS = 12;

    private static void writeOptional(FriendlyByteBuf buffer, @Nullable Component c) {
        buffer.writeBoolean(c != null);
        if (c != null) {
            buffer.writeComponent(c);
        }
    }

    @Nullable
    private static Component readOptional(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readComponent() : null;
    }
}

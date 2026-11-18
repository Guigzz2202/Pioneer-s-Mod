package fr.pioneers.world.pet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** 1.0.5-beta (lot 5) : serveur -> client : la liste des animaux du joueur pour la roue du rappel (nom, sorte, type, dimension, distance). */
public record PacketPetList(List<Row> rows) {

    /** Lignes au plus (roue du rappel). */
    public static final int MAX_ROWS = 256;

    public record Row(UUID id, int kind, String type, String name, String dim, int distance, boolean loaded) {
    }

    public void encode(FriendlyByteBuf buf) {
        int n = Math.min(MAX_ROWS, rows.size());   //1.0.5-beta (lot 12) : meme borne que le decodage (rien ne reste dans le paquet)
        buf.writeVarInt(n);
        for (Row r : rows.subList(0, n)) {
            buf.writeUUID(r.id());
            buf.writeByte(r.kind());
            buf.writeUtf(r.type());
            buf.writeUtf(r.name());
            buf.writeUtf(r.dim());
            buf.writeVarInt(r.distance() + 1);
            buf.writeBoolean(r.loaded());
        }
    }

    public static PacketPetList decode(FriendlyByteBuf buf) {
        int n = Math.min(MAX_ROWS, buf.readVarInt());
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            rows.add(new Row(buf.readUUID(), buf.readByte(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt() - 1, buf.readBoolean()));
        }
        return new PacketPetList(rows);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.pioneers.world.pet.client.PetRecallClient.receive(rows));
        ctx.get().setPacketHandled(true);
    }
}

package fr.pioneers.logistics.caravan;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** 1.0.4-beta (lot 10) : serveur -> clients : vue de caravane d'une bete ({@link CaravanData#view}) : bat rempli, rabats, fiche au regard. */
public record PacketCaravan(int entity, CompoundTag view) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entity);
        buf.writeNbt(view);
    }

    public static PacketCaravan decode(FriendlyByteBuf buf) {
        return new PacketCaravan(buf.readVarInt(), buf.readNbt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> apply(this));
        ctx.get().setPacketHandled(true);
    }

    private static void apply(PacketCaravan p) {
        if (Minecraft.getInstance().level == null || p.view == null) {
            return;
        }
        Entity e = Minecraft.getInstance().level.getEntity(p.entity);
        CaravanData d = PiCaravan.get(e);
        if (d != null) {
            d.readView(p.view);
        }
    }
}

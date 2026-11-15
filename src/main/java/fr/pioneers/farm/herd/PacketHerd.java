package fr.pioneers.farm.herd;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** 1.0.4-beta (lot 9) : serveur -> clients : donnees de race d'un lapin ou d'un cheval ({@link HerdData}) : robe, echelle, fiche. */
public record PacketHerd(int entity, CompoundTag data) {

    public static PacketHerd of(Entity e, HerdData d) {
        return new PacketHerd(e.getId(), d.serializeNBT());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entity);
        buf.writeNbt(data);
    }

    public static PacketHerd decode(FriendlyByteBuf buf) {
        return new PacketHerd(buf.readVarInt(), buf.readNbt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> apply(this));
        ctx.get().setPacketHandled(true);
    }

    private static void apply(PacketHerd p) {
        if (Minecraft.getInstance().level == null || p.data == null) {
            return;
        }
        Entity e = Minecraft.getInstance().level.getEntity(p.entity);
        HerdData d = e == null ? null : PiHerd.get(e);
        if (d != null) {
            d.deserializeNBT(p.data);
        }
    }
}

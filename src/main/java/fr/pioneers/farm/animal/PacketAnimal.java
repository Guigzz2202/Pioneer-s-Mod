package fr.pioneers.farm.animal;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Serveur -> clients : race, sexe, etats et besoins d'un animal d'elevage (robe, taille, fiche au regard). */
public record PacketAnimal(int entity, int breed, int flags, int hunger, int thirst, int produce) {

    public static PacketAnimal of(Entity e, AnimalData d) {
        return new PacketAnimal(e.getId(), d.breed, d.flags(), Math.round(d.hunger), Math.round(d.thirst), Math.round(d.produce * 100));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entity);
        buf.writeByte(breed);
        buf.writeByte(flags);
        buf.writeByte(hunger);
        buf.writeByte(thirst);
        buf.writeByte(produce);
    }

    public static PacketAnimal decode(FriendlyByteBuf buf) {
        return new PacketAnimal(buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> apply(this));
        ctx.get().setPacketHandled(true);
    }

    private static void apply(PacketAnimal p) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Entity e = Minecraft.getInstance().level.getEntity(p.entity);
        AnimalData d = e == null ? null : PiAnimals.get(e);
        if (d != null) {
            d.breed = p.breed;
            d.flags(p.flags);
            d.hunger = p.hunger;
            d.thirst = p.thirst;
            d.produce = p.produce / 100F;
        }
    }
}

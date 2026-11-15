package fr.pioneers.pet;

import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

/**
 * 1.0.5-beta (lot 5) : client -> serveur : action de l'interface d'un animal ({@link PetMenu} ouverte sur cette bete, a 8 blocs, par un
 * joueur qui la commande) : mode, adoption permise, lier a la niche / au panier le plus proche (16 blocs), delier, inventaire de la
 * monture (sacoches si elle porte un bat, sinon l'inventaire vanilla).
 */
public record PacketPet(int entity, int action, int arg) {

    public static final int MODE = 0, ADOPT = 1, LINK = 2, UNLINK = 3, INVENTORY = 4;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entity);
        buf.writeByte(action);
        buf.writeVarInt(arg);
    }

    public static PacketPet decode(FriendlyByteBuf buf) {
        return new PacketPet(buf.readVarInt(), buf.readByte(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (p == null || !(p.containerMenu instanceof PetMenu m) || m.entityId != entity) {
            return;
        }
        Entity e = p.level().getEntity(entity);
        if (e == null || !e.isAlive() || e.distanceToSqr(p) > 64 || !PetMaster.canCommand(e, p)) {
            return;
        }
        switch (action) {
            case MODE -> {
                if (e instanceof Pet pet) {
                    PetMode mode = PetMode.of(arg);
                    if (!mode.allowed(pet.isCat()) || mode == PetMode.AUTONOMOUS && pet.home() == null) {
                        p.displayClientMessage(Component.translatable("pet.pioneers.msg.need_home").withStyle(ChatFormatting.GOLD), true);
                        return;
                    }
                    if (pet.mode() == PetMode.HUNT && mode != PetMode.HUNT) {
                        HuntEvents.endHunt(pet.self());
                    }
                    pet.setMode(mode);
                }
            }
            case ADOPT -> {
                if (PetMaster.master(e) != null) {
                    PetMaster.setAdoptable(e, arg != 0);
                }
            }
            case LINK -> {
                if (e instanceof Pet pet) {
                    BlockPos h = nearestHome(e, pet.isCat());
                    if (h == null) {
                        p.displayClientMessage(Component.translatable(pet.isCat() ? "pet.pioneers.msg.no_basket" : "pet.pioneers.msg.no_kennel")
                              .withStyle(ChatFormatting.GOLD), true);
                    } else {
                        PetHomeBlock.link(pet, e.level(), h, p);
                    }
                }
            }
            case UNLINK -> {
                if (e instanceof Pet pet) {
                    pet.setHome(null);
                    if (pet.mode() == PetMode.AUTONOMOUS) {
                        pet.setMode(PetMode.FOLLOW);
                    }
                }
            }
            case INVENTORY -> {
                if (e instanceof AbstractHorse h && h.isTamed()) {
                    fr.pioneers.caravan.CaravanData d = fr.pioneers.caravan.PiCaravan.get(h);
                    if (d != null && d.pack && e instanceof Mob mob) {
                        NetworkHooks.openScreen(p, new SimpleMenuProvider((id, inv, pl) -> new fr.pioneers.caravan.PackMenu(id, inv, mob, d),
                              Component.translatable("gui.pioneers.pack.title", mob.getDisplayName())), buf -> {
                            buf.writeVarInt(mob.getId());
                            buf.writeVarInt(d.slots);
                        });
                    } else {
                        h.openCustomInventoryScreen(p);
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Niche (chien) ou panier (chat) le plus proche de l'animal, a 16 blocs. */
    static BlockPos nearestHome(Entity e, boolean cat) {
        BlockPos c = e.blockPosition(), best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos b : BlockPos.betweenClosed(c.offset(-16, -4, -16), c.offset(16, 4, 16))) {
            if (PiPets.isHome(e.level().getBlockState(b).getBlock(), cat)) {
                double d = b.distSqr(c);
                if (d < bd) {
                    bd = d;
                    best = b.immutable();
                }
            }
        }
        return best;
    }
}

package fr.pioneers.logistics.caravan;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/**
 * 1.0.4-beta (lot 10) : client -> serveur : modification de la FEUILLE DE ROUTE tenue en main (monter, descendre, retirer une etape ;
 * boucle / aller-retour ; tout effacer). Le serveur relit l'objet tenu (rien n'est cru du client que l'action et l'indice).
 */
public record PacketCaravanRoute(InteractionHand hand, int action, int index) {

    public static final int UP = 0, DOWN = 1, REMOVE = 2, MODE = 3, CLEAR = 4;

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(hand);
        buf.writeByte(action);
        buf.writeVarInt(index);
    }

    public static PacketCaravanRoute decode(FriendlyByteBuf buf) {
        return new PacketCaravanRoute(buf.readEnum(InteractionHand.class), buf.readByte(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (player == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof RouteSheetItem) {
            apply(stack, action, index);
        }
    }

    /** Applique une modification a une feuille de route (commun : GameTests). */
    public static void apply(ItemStack stack, int action, int index) {
        RouteSheetItem.Route r = RouteSheetItem.Route.of(stack);
        int n = r.stops().size();
        boolean loop = r.loop();
        if (action == MODE) {
            loop = !loop;
        } else if (action == CLEAR) {
            r.stops().clear();
            r.names().clear();
        } else if (index >= 0 && index < n) {
            if (action == REMOVE) {
                r.stops().remove(index);
                r.names().remove(index);
            } else if (action == UP && index > 0 || action == DOWN && index < n - 1) {
                int j = action == UP ? index - 1 : index + 1;
                BlockPos p = r.stops().get(index);
                String s = r.names().get(index);
                r.stops().set(index, r.stops().get(j));
                r.names().set(index, r.names().get(j));
                r.stops().set(j, p);
                r.names().set(j, s);
            }
        }
        new RouteSheetItem.Route(r.stops().isEmpty() ? "" : r.dim(), loop, r.stops(), r.names()).write(stack);
    }
}

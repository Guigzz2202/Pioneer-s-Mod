package fr.pioneers.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * 1.12.3 (lot F3) : piles de plus de 127 objets d'un menu du socle (cases des buffers d'objets), serveur -> client. Le paquet vanilla des
 * cases ecrit la quantite d'une pile sur un OCTET en 1.20.1 ({@code FriendlyByteBuf.writeItem}) : au-dela de 127 le client recoit une
 * quantite fausse, voire une pile vide. Ce paquet suit le paquet vanilla (meme connexion, meme file du fil principal) et remet la vraie pile
 * dans la case. Envoye par {@code core/menu/PiTileMenu} seulement pour les cases dont la pile depasse 127.
 *
 * @param containerId identifiant du menu ouvert.
 * @param slots       index des cases dans le menu.
 * @param stacks      piles (quantite entiere).
 */
public record PacketBigSlots(int containerId, int[] slots, ItemStack[] stacks) {

    public static PacketBigSlots of(int containerId, List<Integer> slots, List<ItemStack> stacks) {
        int[] index = new int[slots.size()];
        for (int i = 0; i < index.length; i++) {
            index[i] = slots.get(i);
        }
        return new PacketBigSlots(containerId, index, stacks.toArray(new ItemStack[0]));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(slots.length);
        for (int i = 0; i < slots.length; i++) {
            buffer.writeVarInt(slots[i]);
            buffer.writeItem(stacks[i].copyWithCount(1));
            buffer.writeVarInt(stacks[i].getCount());
        }
    }

    public static PacketBigSlots decode(FriendlyByteBuf buffer) {
        int id = buffer.readVarInt();
        int n = Math.min(buffer.readVarInt(), 256);
        List<Integer> slots = new ArrayList<>(n);
        List<ItemStack> stacks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            slots.add(buffer.readVarInt());
            ItemStack stack = buffer.readItem();
            int count = buffer.readVarInt();
            if (!stack.isEmpty()) {
                stack.setCount(Math.max(1, count));
            }
            stacks.add(stack);
        }
        return of(id, slots, stacks);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        //Deja sur le fil principal (consumerMainThread) : applique tout de suite, juste apres le paquet vanilla des cases
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.pioneers.client.gui.core.BigSlotsClient.apply(this));
        context.get().setPacketHandled(true);
    }
}

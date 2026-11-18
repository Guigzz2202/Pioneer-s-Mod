package fr.pioneers.building.home.bag;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * 1.0.5-beta (lot 31, retour 42) : DEPOT RAPIDE du contenu d'un sac de rangement : dans le conteneur VISE (Maj + clic droit sur un coffre, un
 * panier, une caisse… : sa capacite d'objets) ou dans le conteneur OUVERT (Maj + clic droit sur le sac dans l'ecran d'un coffre). Chaque pile
 * n'est retiree du sac que pour ce qui est vraiment entre (le reste revient dans sa case) : ni perte ni duplication. Le filtre ne compte pas
 * ici (on vide le sac).
 */
public final class BagDeposit {

    private BagDeposit() {
    }

    /** Vide le sac dans une capacite d'objets ; rend le nombre d'objets deposes. */
    public static int intoHandler(IItemHandler handler, ItemStack bag, BagKind kind) {
        BagLedger l = BagStore.read(bag, kind);
        int moved = drain(l, kind, stack -> ItemHandlerHelper.insertItemStacked(handler, stack, false));
        if (moved > 0 || l.migrated) {
            BagStore.write(bag, l);
        }
        return moved;
    }

    /** Vide le sac tenu dans la case {@code slotIndex} du menu ouvert vers les cases du conteneur de ce menu. */
    public static int intoOpenMenu(ServerPlayer player, int slotIndex) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu instanceof InventoryMenu || menu instanceof StorageBagMenu || menu instanceof BagMenu || slotIndex < 0 || slotIndex >= menu.slots.size()
            || !menu.stillValid(player)) {
            return 0;
        }
        Slot from = menu.slots.get(slotIndex);
        ItemStack bag = from.getItem();
        if (from.container != player.getInventory() || !(bag.getItem() instanceof BagItem b) || !b.kind.storage()) {
            return 0;
        }
        List<Slot> targets = new ArrayList<>();
        for (Slot s : menu.slots) {
            //1.0.5-beta (lot 35) : seulement les vraies cases du conteneur (actives, d'ou le joueur peut reprendre : pas de case verrouillee,
            //de resultat ni de modele) ; une case ou l'on ne reprend pas garderait les graines sans retour
            if (s.container != player.getInventory() && s.isActive() && s.mayPickup(player)) {
                targets.add(s);
            }
        }
        if (targets.isEmpty()) {
            return 0;
        }
        BagLedger l = BagStore.read(bag, b.kind);
        int moved = drain(l, b.kind, stack -> intoSlots(targets, stack, player));
        if (moved > 0 || l.migrated) {
            BagStore.write(bag, l);
        }
        menu.broadcastChanges();
        tell(player, moved);
        return moved;
    }

    /** Message et son (serveur). */
    public static void tell(Player player, int moved) {
        player.displayClientMessage(moved > 0 ? Component.translatable("home.pioneers.bag.dumped", moved).withStyle(ChatFormatting.GREEN)
                                              : Component.translatable("home.pioneers.bag.dump_none").withStyle(ChatFormatting.YELLOW), true);
        if (moved > 0) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.6F, 1.0F);
        }
    }

    interface Sink {
        /** Range la pile, rend le reste. */
        ItemStack put(ItemStack stack);
    }

    /** Sort pile par pile chaque case du registre vers {@code sink} ; une case s'arrete au premier reste. */
    static int drain(BagLedger l, BagKind kind, Sink sink) {
        int moved = 0, cases = Integer.MAX_VALUE;
        for (int i = 0; i < l.entries.size(); ) {
            BagLedger.Entry e = l.entries.get(i);
            ItemStack proto = BagStore.display(l, e);
            if (proto.isEmpty()) {
                i++;
                continue;
            }
            BagLedger.Taken t = l.take(i, proto.getMaxStackSize());
            ItemStack rest = sink.put(BagStore.toStack(t));
            moved += t.count() - rest.getCount();
            if (!rest.isEmpty()) {
                //plus de place pour cette sorte : le reste revient a sa place triee (meme index), on passe a la sorte suivante
                l.giveBack(t, rest.getCount(), cases);
                i++;
            }
            //sinon : meme index (la case continue, ou la sorte suivante a pris sa place si elle s'est videe) ; le total baisse : fini
        }
        return moved;
    }

    /** Equivalent de {@code moveItemStackTo} sur une liste de cases : piles identiques d'abord, puis cases vides. */
    static ItemStack intoSlots(List<Slot> targets, ItemStack stack, Player player) {
        for (Slot s : targets) {
            if (stack.isEmpty()) {
                break;
            }
            ItemStack in = s.getItem();
            if (!in.isEmpty() && ItemStack.isSameItemSameTags(in, stack) && s.mayPlace(stack)) {
                int max = Math.min(s.getMaxStackSize(stack), stack.getMaxStackSize());
                int n = Math.min(stack.getCount(), max - in.getCount());
                if (n > 0) {
                    in.grow(n);
                    stack.shrink(n);
                    s.setChanged();
                }
            }
        }
        for (Slot s : targets) {
            if (stack.isEmpty()) {
                break;
            }
            if (!s.hasItem() && s.mayPlace(stack)) {
                int n = Math.min(stack.getCount(), s.getMaxStackSize(stack));
                if (n > 0) {
                    s.setByPlayer(stack.split(n));
                }
            }
        }
        return stack;
    }
}

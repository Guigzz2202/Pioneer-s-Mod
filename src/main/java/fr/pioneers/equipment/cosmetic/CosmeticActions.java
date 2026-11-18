package fr.pioneers.equipment.cosmetic;

import fr.pioneers.core.registries.PiTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 1.12.0 (lot E4) : gestes sur la garde-robe, cote SERVEUR (le client n'envoie que des intentions, {@link CosmeticNet.Action}) :
 * poser / prendre avec l'objet tenu par la souris de l'inventaire, Maj + clic dans un sens ou dans l'autre, interrupteur, clic droit sur
 * la piece en main. Chaque changement est renvoye au joueur et a ceux qui le voient.
 */
public final class CosmeticActions {

    public static final int CLICK = 0, QUICK_OUT = 1, QUICK_IN = 2, TOGGLE = 3;

    private CosmeticActions() {
    }

    /** L'inventaire du joueur est-il le menu ouvert (seul ecran ou le panneau existe) ? */
    private static boolean inInventory(ServerPlayer p) {
        return p.containerMenu == p.inventoryMenu && !p.isSpectator();
    }

    public static boolean handle(ServerPlayer p, int op, int index) {
        return switch (op) {
            case CLICK -> click(p, index);
            case QUICK_OUT -> quickOut(p, index);
            case QUICK_IN -> quickIn(p, index);
            case TOGGLE -> toggle(p, index);
            default -> false;
        };
    }

    /** Clic sur la case i : echange avec l'objet tenu par la souris (s'il a sa place ici). */
    public static boolean click(ServerPlayer p, int i) {
        if (!inInventory(p) || i < 0 || i >= Wardrobe.SIZE) {
            return false;
        }
        Wardrobe w = CosmeticCap.get(p);
        ItemStack carried = p.inventoryMenu.getCarried();
        if (!Wardrobe.fits(i, carried) || carried.getCount() > 1 || carried.isEmpty() && w.get(i).isEmpty()) {
            return false;
        }
        ItemStack old = w.get(i);
        w.set(i, carried.copy());
        p.inventoryMenu.setCarried(old);
        p.inventoryMenu.broadcastChanges();
        changed(p);
        return true;
    }

    /** Maj + clic sur la case i : la piece retourne dans l'inventaire (rien ne bouge s'il est plein). */
    public static boolean quickOut(ServerPlayer p, int i) {
        Wardrobe w = CosmeticCap.get(p);
        if (!inInventory(p) || w.get(i).isEmpty()) {
            return false;
        }
        ItemStack s = w.get(i).copy();
        if (!p.getInventory().add(s) || !s.isEmpty()) {
            return false;
        }
        w.set(i, ItemStack.EMPTY);
        p.inventoryMenu.broadcastChanges();
        changed(p);
        return true;
    }

    /** Maj + clic sur une case de l'inventaire (0..35) qui tient une piece cosmetique : elle est enfilee, l'ancienne prend sa place. */
    public static boolean quickIn(ServerPlayer p, int invSlot) {
        Inventory inv = p.getInventory();
        if (!inInventory(p) || invSlot < 0 || invSlot >= inv.items.size()) {
            return false;
        }
        ItemStack s = inv.getItem(invSlot);
        if (!(s.getItem() instanceof CosmeticItem c)) {
            return false;
        }
        inv.setItem(invSlot, equip(p, s.copy()));
        p.inventoryMenu.broadcastChanges();
        return true;
    }

    /** Enfile la piece et renvoie celle qu'on portait a cet emplacement (ou rien). */
    public static ItemStack equip(ServerPlayer p, ItemStack stack) {
        if (!(stack.getItem() instanceof CosmeticItem c)) {
            return stack;
        }
        Wardrobe w = CosmeticCap.get(p);
        int i = c.piece.ordinal();
        ItemStack old = w.get(i);
        w.set(i, stack);
        changed(p);
        return old;
    }

    /** Interrupteur de la piece i : montrer le cosmetique ou l'armure. */
    public static boolean toggle(ServerPlayer p, int i) {
        if (i < 0 || i >= Wardrobe.SIZE) {
            return false;
        }
        CosmeticCap.get(p).toggle(i);
        changed(p);
        return true;
    }

    static void changed(ServerPlayer p) {
        CosmeticNet.sync(p);
        if (CosmeticCap.get(p).fullOutfit()) {
            PiTriggers.MECHANICS.trigger(p, "cosmetic_outfit");
        }
    }
}

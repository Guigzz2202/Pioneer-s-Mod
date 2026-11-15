package fr.pioneers.core.menu;

import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Case d'interface branchee sur un {@link ItemSlot} du socle (le joueur agit en MANUAL). */
public class MenuSlot extends Slot {

    private static final Container EMPTY = new SimpleContainer(0);
    public final ItemSlot slot;
    /** 1.12.3 : quantite au-dela de laquelle l'ecran du socle dessine son propre nombre abrege (« 512 », « 1,2k »). */
    public static final int BIG_COUNT = 99;
    /**
     * 1.12.3 : vrai seulement pendant le dessin des cases par l'ecran (menu du CLIENT, jamais celui du serveur) : une grosse pile est rendue
     * avec une quantite de 1 pour que le jeu n'ecrive pas un nombre qui deborde de la case.
     */
    boolean hideCount;

    public MenuSlot(ItemSlot slot) {
        super(EMPTY, 0, slot.x, slot.y);
        this.slot = slot;
    }

    @NotNull
    @Override
    public ItemStack getItem() {
        ItemStack stack = slot.getStack();
        return hideCount && stack.getCount() > BIG_COUNT ? stack.copyWithCount(1) : stack;
    }

    @Override
    public boolean hasItem() {
        return !slot.isEmpty();
    }

    @Override
    public void set(@NotNull ItemStack stack) {
        slot.setStack(stack);
    }

    /** 1.12.3 : la pile de la case a grandi ou diminue en place (clic du menu vanilla) : le bloc est prevenu (sauvegarde). */
    @Override
    public void setChanged() {
        slot.markChanged();
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return slot.canInsert(stack, Automation.MANUAL);
    }

    @Override
    public boolean mayPickup(@NotNull Player player) {
        return slot.canExtract(Automation.MANUAL);
    }

    @Override
    public int getMaxStackSize() {
        return slot.getLimit(ItemStack.EMPTY);
    }

    @Override
    public int getMaxStackSize(@NotNull ItemStack stack) {
        return slot.getLimit(stack);
    }

    @NotNull
    @Override
    public ItemStack remove(int amount) {
        return slot.extract(amount, Action.EXECUTE, Automation.MANUAL);
    }
}

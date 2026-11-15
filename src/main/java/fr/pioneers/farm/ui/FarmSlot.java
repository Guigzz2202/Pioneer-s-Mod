package fr.pioneers.farm.ui;

import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.ContentsListener;
import fr.pioneers.core.inventory.ItemSlot;
import java.util.function.BiPredicate;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 41) : case de l'interface standard VUE sur la liste d'objets d'un poste ou d'un appareil de la ferme ({@code items},
 * sauvee comme avant par {@code ContainerHelper} : indices et cles inchanges, mondes existants relus sans rien changer). La pile vit dans la
 * liste ; la case n'en garde aucune copie. Droits : {@code canInsert} / {@code canExtract} selon l'origine ({@link Automation#MANUAL} =
 * le joueur dans l'interface).
 */
public class FarmSlot extends ItemSlot {

    private final NonNullList<ItemStack> list;
    public final int index;
    public final FarmLayout.Role role;
    /** Regle de sortie (celle de la case de base lit sa propre pile, toujours vide ici). */
    private final BiPredicate<ItemStack, Automation> extractRule;

    public FarmSlot(NonNullList<ItemStack> list, int index, FarmLayout.Role role, IntSupplier limit, Predicate<ItemStack> filter,
          BiPredicate<ItemStack, Automation> canInsert, BiPredicate<ItemStack, Automation> canExtract, @Nullable ContentsListener listener, int x,
          int y) {
        super(limit, filter, canInsert, canExtract, listener, x, y);
        this.list = list;
        this.index = index;
        this.role = role;
        this.extractRule = canExtract;
    }

    @NotNull
    @Override
    public ItemStack getStack() {
        return list.get(index);
    }

    @Override
    public void setStack(@NotNull ItemStack stack) {
        list.set(index, stack.isEmpty() ? ItemStack.EMPTY : stack);
        changed();
    }

    @Override
    public boolean isEmpty() {
        return getStack().isEmpty();
    }

    @Override
    public int getCount() {
        return getStack().getCount();
    }

    @Override
    public boolean canExtract(Automation automation) {
        ItemStack stack = getStack();
        return !stack.isEmpty() && extractRule.test(stack, automation);
    }

    @NotNull
    @Override
    public ItemStack insert(@NotNull ItemStack offered, Action action, Automation automation) {
        if (offered.isEmpty()) {
            return offered;
        }
        ItemStack stack = getStack();
        if (!stack.isEmpty() && !ItemStack.isSameItemSameTags(stack, offered)) {
            return offered;
        }
        int room = getLimit(offered) - stack.getCount();
        if (room <= 0 || !canInsert(offered, automation)) {
            return offered;
        }
        int moved = Math.min(room, offered.getCount());
        if (action.execute()) {
            if (stack.isEmpty()) {
                setStack(offered.copyWithCount(moved));
            } else {
                stack.grow(moved);
                changed();
            }
        }
        return moved == offered.getCount() ? ItemStack.EMPTY : offered.copyWithCount(offered.getCount() - moved);
    }

    @NotNull
    @Override
    public ItemStack extract(int amount, Action action, Automation automation) {
        ItemStack stack = getStack();
        if (amount <= 0 || stack.isEmpty() || !canExtract(automation)) {
            return ItemStack.EMPTY;
        }
        int moved = Math.min(Math.min(amount, stack.getCount()), Math.max(1, stack.getMaxStackSize()));
        ItemStack out = stack.copyWithCount(moved);
        if (action.execute()) {
            if (moved >= stack.getCount()) {
                setStack(ItemStack.EMPTY);
            } else {
                stack.shrink(moved);
                changed();
            }
        }
        return out;
    }

    /** La liste du poste est sauvee par le poste lui-meme (ContainerHelper) : rien ici. */
    @Override
    public CompoundTag save() {
        return saveStack(getStack());
    }

    @Override
    public void load(CompoundTag tag) {
        list.set(index, loadStack(tag));
    }
}

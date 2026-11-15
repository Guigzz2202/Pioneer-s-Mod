package fr.pioneers.core.inventory;

import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.ContentsListener;
import java.util.function.BiPredicate;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Emplacement d'objet du socle (sans Mekanism) : une pile, un filtre (ce qui peut y entrer), des droits d'entree et de sortie selon
 * l'origine ({@link Automation}), une limite et une position dans l'interface.
 */
public class ItemSlot {

    private static final BiPredicate<ItemStack, Automation> ALWAYS = (stack, automation) -> true;

    private ItemStack stack = ItemStack.EMPTY;
    private final Predicate<ItemStack> filter;
    private final BiPredicate<ItemStack, Automation> canInsert, canExtract;
    private final IntSupplier limit;
    @Nullable
    private final ContentsListener listener;
    public final int x, y;
    /** Aide visuelle de l'interface (case vide) : objet fantome attendu, ou pictogramme du mod ({@code textures/gui/machine/<nom>.png}). */
    @Nullable
    private Supplier<ItemStack> ghost;
    @Nullable
    private String ghostIcon;
    /**
     * 1.12.3 (lot F3, buffers) : multiplicateur de la taille de pile (0 = limite ordinaire, au plus une pile normale). Avec un multiplicateur
     * N, une case garde N piles normales du meme objet (64 x N) ; un objet non empilable reste a 1 par case. Ce qui sort d'une case ne
     * depasse jamais une pile normale (tuyaux, cables, robots, souris).
     */
    private int stackMultiplier;
    /** Cle NBT de la quantite au-dela d'un octet ({@code Count} vanilla est un octet signe : 127 au plus). */
    public static final String NBT_BIG_COUNT = "CountL";

    public ItemSlot(IntSupplier limit, Predicate<ItemStack> filter, BiPredicate<ItemStack, Automation> canInsert, BiPredicate<ItemStack, Automation> canExtract,
          @Nullable ContentsListener listener, int x, int y) {
        this.limit = limit;
        this.filter = filter;
        this.canInsert = canInsert;
        this.canExtract = canExtract;
        this.listener = listener;
        this.x = x;
        this.y = y;
    }

    /** Emplacement libre (filtre seulement). */
    public static ItemSlot of(Predicate<ItemStack> filter, @Nullable ContentsListener listener, int x, int y) {
        return new ItemSlot(() -> 64, filter, ALWAYS, ALWAYS, listener, x, y);
    }

    public static ItemSlot of(@Nullable ContentsListener listener, int x, int y) {
        return of(stack -> true, listener, x, y);
    }

    /** Objet attendu, dessine en transparence quand la case est vide. */
    public ItemSlot ghost(Supplier<ItemStack> ghost) {
        this.ghost = ghost;
        return this;
    }

    /** 1.12.3 : case qui garde {@code multiplier} piles normales (buffers d'objets). */
    public ItemSlot stacked(int multiplier) {
        this.stackMultiplier = Math.max(0, multiplier);
        return this;
    }

    /** Multiplicateur de pile (0 ou 1 : case ordinaire). */
    public int stackMultiplier() {
        return stackMultiplier;
    }

    public ItemSlot ghost(ItemLike item) {
        return ghost(() -> new ItemStack(item));
    }

    /** Pictogramme du mod dessine en transparence quand la case est vide (batterie, reservoir de gaz...). */
    public ItemSlot ghostIcon(String icon) {
        this.ghostIcon = icon;
        return this;
    }

    public ItemStack getGhost() {
        if (ghost == null) {
            return ItemStack.EMPTY;
        }
        try {
            ItemStack stack = ghost.get();
            return stack == null ? ItemStack.EMPTY : stack;
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    @Nullable
    public String getGhostIcon() {
        return ghostIcon;
    }

    @NotNull
    public ItemStack getStack() {
        return stack;
    }

    /** Remplace la pile (sans verification). */
    public void setStack(@NotNull ItemStack stack) {
        this.stack = stack.isEmpty() ? ItemStack.EMPTY : stack;
        changed();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public int getCount() {
        return stack.getCount();
    }

    /** Taille maximale d'une pile de cet objet ici. */
    public int getLimit(ItemStack stack) {
        if (stackMultiplier > 1) {
            int base = stack.isEmpty() ? 64 : stack.getMaxStackSize();
            return base <= 1 ? 1 : base * stackMultiplier;
        }
        int max = limit.getAsInt();
        return stack.isEmpty() ? max : Math.min(max, stack.getMaxStackSize());
    }

    public boolean isItemValid(ItemStack stack) {
        return filter.test(stack);
    }

    public boolean canInsert(ItemStack stack, Automation automation) {
        return !stack.isEmpty() && filter.test(stack) && canInsert.test(stack, automation);
    }

    public boolean canExtract(Automation automation) {
        return !stack.isEmpty() && canExtract.test(stack, automation);
    }

    /** Insere une pile ; renvoie le reste. */
    @NotNull
    public ItemStack insert(@NotNull ItemStack offered, Action action, Automation automation) {
        if (offered.isEmpty()) {
            return offered;
        }
        //0.20.46 (audit) : refus bon marche d'abord (case prise par un autre objet, pleine) : le filtre (souvent une recherche dans les
        //recettes) ne tourne plus a chaque essai des cables, entonnoirs et robots sur une case qui ne peut rien prendre
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

    /** Retire jusqu'a {@code amount} objets ; renvoie ce qui sort. */
    @NotNull
    public ItemStack extract(int amount, Action action, Automation automation) {
        if (amount <= 0 || !canExtract(automation)) {
            return ItemStack.EMPTY;
        }
        //1.12.3 : jamais plus d'une pile normale a la fois (une case de buffer peut en garder plusieurs)
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

    protected void changed() {
        if (listener != null) {
            listener.onContentsChanged();
        }
    }

    /** La pile a ete modifiee en place (menu : grow / shrink de la pile de la case) : prevenir le bloc. */
    public void markChanged() {
        changed();
    }

    public CompoundTag save() {
        return saveStack(stack);
    }

    /** Chargement sans prevenir (le listener n'est pas appele). */
    public void load(CompoundTag tag) {
        stack = loadStack(tag);
    }

    /**
     * 1.12.3 : pile sauvee avec une quantite au-dela de 127 : {@code Count} (octet vanilla) borne a une pile normale, la vraie quantite dans
     * {@link #NBT_BIG_COUNT} (entier). Un lecteur qui ne connait pas cette cle voit une pile normale, jamais une pile vide.
     */
    public static CompoundTag saveStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        CompoundTag tag = stack.save(new CompoundTag());
        if (stack.getCount() > Byte.MAX_VALUE) {
            tag.putByte("Count", (byte) Math.min(Byte.MAX_VALUE, Math.max(1, stack.getMaxStackSize())));
            tag.putInt(NBT_BIG_COUNT, stack.getCount());
        }
        return tag;
    }

    /** Relit une pile sauvee par {@link #saveStack} (et l'ancien format : {@code Count} seul). */
    public static ItemStack loadStack(CompoundTag tag) {
        if (tag.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack read = ItemStack.of(tag);
        if (!read.isEmpty() && tag.contains(NBT_BIG_COUNT)) {
            read.setCount(Math.max(1, tag.getInt(NBT_BIG_COUNT)));
        }
        return read;
    }
}

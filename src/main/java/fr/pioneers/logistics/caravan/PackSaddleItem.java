package fr.pioneers.logistics.caravan;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 10) : BAT de bois et de cuir. Pose et retrait sur la bete : {@link Caravans#interact} (clic droit avec le bat ; cisailles
 * pour le retirer). Le contenu des sacoches reste DANS l'objet retire (NBT {@code pack_items}) et revient a la pose suivante.
 */
public class PackSaddleItem extends Item {

    public static final String ITEMS = "pack_items";

    public PackSaddleItem(Properties properties) {
        super(properties);
    }

    /** Contenu garde dans l'objet (vide si aucun). */
    public static ItemStackHandler contents(ItemStack stack) {
        ItemStackHandler h = new ItemStackHandler(GeneratedCaravan.MAX_SLOTS);
        CompoundTag t = stack.getTag();
        if (t != null && t.contains(ITEMS)) {
            ItemStackHandler read = new ItemStackHandler();
            read.deserializeNBT(t.getCompound(ITEMS));
            for (int i = 0; i < Math.min(read.getSlots(), h.getSlots()); i++) {
                h.setStackInSlot(i, read.getStackInSlot(i));
            }
        }
        return h;
    }

    /** Piles gardees dans l'objet. */
    public static int stacks(ItemStack stack) {
        ItemStackHandler h = contents(stack);
        int n = 0;
        for (int i = 0; i < h.getSlots(); i++) {
            n += h.getStackInSlot(i).isEmpty() ? 0 : 1;
        }
        return n;
    }

    /** Range le contenu des sacoches dans l'objet (rien s'il est vide). */
    public static void store(ItemStack stack, ItemStackHandler items) {
        boolean any = false;
        for (int i = 0; i < items.getSlots(); i++) {
            any |= !items.getStackInSlot(i).isEmpty();
        }
        if (any) {
            stack.getOrCreateTag().put(ITEMS, items.serializeNBT());
        } else if (stack.getTag() != null) {
            stack.getTag().remove(ITEMS);
        }
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips.of(tooltip).desc("description.pioneers.pack_saddle");
        int n = stacks(stack);
        if (n > 0) {
            tooltip.add(Component.translatable("card.pioneers.caravan.pack_value", n, GeneratedCaravan.MAX_SLOTS).withStyle(ChatFormatting.GOLD));
        }
    }
}

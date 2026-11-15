package fr.pioneers.transport.trash;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.core.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 22) : interface de la POUBELLE : deux cases du menu (rien n'est garde par le bloc) et l'inventaire du joueur.
 * <ul>
 *     <li>{@link #TRASH} « Jeter » : la pile deposee est detruite ; la DERNIERE pile jetee reste visible et se reprend tant que l'ecran est
 *     ouvert (repentir) ; une nouvelle pile (ou Maj + clic depuis l'inventaire) la remplace et la detruit ; a la fermeture, elle disparait ;</li>
 *     <li>{@link #DRAIN} « Vider » : un recipient depose (seau, bidon, reservoir de gaz, batterie...) est vide aussitot de son fluide, de son
 *     gaz ou de son energie ; il reste dans la case, vide, a reprendre (rendu au joueur a la fermeture).</li>
 * </ul>
 * Cotes : {@link #W} x {@link #H} (zone {@link #ZONE} + inventaire standard), cases {@link #TRASH_X} / {@link #DRAIN_X} a {@link #SLOT_Y} ;
 * l'ecran est {@code J:client/transport/TrashCanScreen} (meme dessin que les interfaces standard v2).
 */
public class TrashCanMenu extends AbstractContainerMenu {

    public static final int TRASH = 0, DRAIN = 1;
    public static final int W = 196, ZONE = 72, H = ZONE + 108, TRASH_X = 52, DRAIN_X = 128, SLOT_Y = 32, INV_X = 18, INV_Y = ZONE + 26,
          HOTBAR_Y = ZONE + 84;
    private final SimpleContainer cases = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            TrashCanMenu.this.slotsChanged(this);
        }
    };
    @Nullable
    private final BlockPos pos;
    private final boolean server;

    public TrashCanMenu(int id, Inventory inv, @Nullable BlockPos pos) {
        super(PiTrash.MENU.get(), id);
        this.pos = pos;
        this.server = !inv.player.level().isClientSide;
        addSlot(new Slot(cases, TRASH, TRASH_X, SLOT_Y));
        addSlot(new Slot(cases, DRAIN, DRAIN_X, SLOT_Y) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return drainable(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inv, c, INV_X + c * 18, HOTBAR_Y));
        }
    }

    /** Client : position du bloc lue dans le tampon d'ouverture. */
    public static TrashCanMenu client(int id, Inventory inv, FriendlyByteBuf buf) {
        return new TrashCanMenu(id, inv, buf.readBlockPos());
    }

    /** Recipient qu'on peut vider : fluide, gaz ou energie a l'interieur. */
    public static boolean drainable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ItemStack one = stack.copyWithCount(1);
        IFluidHandlerItem f = FluidUtil.getFluidHandler(one).resolve().orElse(null);
        if (f != null && !f.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE).isEmpty()) {
            return true;
        }
        IGasHandler g = one.getCapability(PioneersAPI.GAS_HANDLER).resolve().orElse(null);
        if (g != null && !g.extractGas(Long.MAX_VALUE, Action.SIMULATE).isEmpty()) {
            return true;
        }
        IEnergyStorage e = one.getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
        return e != null && e.canExtract() && e.extractEnergy(Integer.MAX_VALUE, true) > 0;
    }

    /** Vide le recipient (fluide, gaz, energie : detruits) ; rend le recipient vide (ou la pile telle quelle si rien a vider). */
    public static ItemStack drain(ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        ItemStack one = stack.copyWithCount(1);
        IFluidHandlerItem f = FluidUtil.getFluidHandler(one).resolve().orElse(null);
        if (f != null && !f.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE).isEmpty()) {
            for (int i = 0; i < 64 && !f.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE).isEmpty(); i++) {
                //plusieurs reservoirs : on vide tout
            }
            return f.getContainer().copy();
        }
        IGasHandler g = one.getCapability(PioneersAPI.GAS_HANDLER).resolve().orElse(null);
        if (g != null && !g.extractGas(Long.MAX_VALUE, Action.SIMULATE).isEmpty()) {
            for (int i = 0; i < 64 && !g.extractGas(Long.MAX_VALUE, Action.EXECUTE).isEmpty(); i++) {
                //idem
            }
            return one;
        }
        IEnergyStorage e = one.getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
        if (e != null && e.canExtract()) {
            for (int i = 0; i < 64 && e.extractEnergy(Integer.MAX_VALUE, false) > 0; i++) {
                //idem
            }
            return one;
        }
        return stack;
    }

    @Override
    public void slotsChanged(@NotNull net.minecraft.world.Container container) {
        super.slotsChanged(container);
        if (server && container == cases) {
            ItemStack in = cases.getItem(DRAIN);
            if (drainable(in)) {
                ItemStack out = drain(in);
                if (!ItemStack.matches(out, in)) {
                    cases.setItem(DRAIN, out);
                }
            }
        }
    }

    public ItemStack trashed() {
        return cases.getItem(TRASH);
    }

    @Override
    public boolean stillValid(@NotNull Player p) {
        return pos == null || p.level().getBlockState(pos).getBlock() instanceof TrashCanBlock
                              && p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    /**
     * Clic avec une pile portee sur « Jeter » : elle est JETEE (clic : toute la pile ; clic droit : un objet), la pile jetee d'avant est
     * detruite (jamais d'echange : la case ne rend pas l'ancienne pile au curseur) ; main vide : reprendre la derniere pile jetee.
     */
    @Override
    public void clicked(int slotId, int button, @NotNull ClickType type, @NotNull Player player) {
        if (slotId == TRASH && type == ClickType.PICKUP && !getCarried().isEmpty()) {
            ItemStack carried = getCarried();
            ItemStack thrown;
            if (button == 1) {
                thrown = carried.split(1);
            } else {
                thrown = carried.copy();
                setCarried(ItemStack.EMPTY);
            }
            slots.get(TRASH).set(thrown);
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    /** Maj + clic : de l'inventaire vers « Jeter » (l'ancienne pile jetee est detruite) ou « Vider » (recipient) ; des cases vers l'inventaire. */
    @NotNull
    @Override
    public ItemStack quickMoveStack(@NotNull Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index <= DRAIN) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (drainable(stack) && !slots.get(DRAIN).hasItem()) {
            if (!moveItemStackTo(stack, DRAIN, DRAIN + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            //Jeter : la pile remplace la derniere jetee (detruite)
            slots.get(TRASH).set(stack.copy());
            stack.setCount(0);
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return ItemStack.EMPTY;
    }

    /** Fermeture : la pile jetee disparait pour de bon ; le recipient de « Vider » revient au joueur. */
    @Override
    public void removed(@NotNull Player p) {
        super.removed(p);
        if (server) {
            cases.setItem(TRASH, ItemStack.EMPTY);
            ItemStack back = cases.removeItemNoUpdate(DRAIN);
            if (!back.isEmpty()) {
                if (!p.getInventory().add(back)) {
                    p.drop(back, false);
                }
            }
        }
    }
}

package fr.pioneers.farm.crustacean;

import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.farm.fish.FishDef;
import fr.pioneers.farm.fish.FishItems;
import fr.pioneers.farm.fish.FishSpawns;
import fr.pioneers.farm.fish.GeneratedFish;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.4 : tuile du filet de peche et du casier a crabe ({@link TrapBlock}). Toutes les {@link #CHECK} ticks : le filet compte l'eau autour
 * (5 x 3 x 5) et avance d'autant (au moins {@link #NET_MIN_WATER} blocs) ; une prise = un poisson du milieu ({@link FishSpawns}) ; le casier
 * avance si il est appate et a l'eau, et prend un crustace aquatique du milieu ({@link CrustaceanSpawns}) en mangeant un appat. Prises dans
 * {@code catch} (9 cases), appats dans {@code bait} (casier, 16 au plus). NBT : {@code catch}, {@code bait}, {@code progress}, {@code goal}.
 */
public class TrapTile extends PiBlockEntity {

    public static final int CHECK = 100, NET_MIN_WATER = 20, NET_GOAL = 1600, POT_GOAL = 40, BAIT_MAX = 16;

    private final ItemStackHandler catches = new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private ItemStack bait = ItemStack.EMPTY;
    private int progress, goal;
    private final LazyOptional<IItemHandler> handler = LazyOptional.of(Access::new);

    public TrapTile(BlockPos pos, BlockState state) {
        super(PiCrustaceans.TRAP_TILE.get(), pos, state);
    }

    public TrapBlock.Kind kind() {
        return getBlockState().getBlock() instanceof TrapBlock b ? b.kind() : TrapBlock.Kind.NET;
    }

    public boolean submerged() {
        return getBlockState().hasProperty(TrapBlock.WATERLOGGED) && getBlockState().getValue(TrapBlock.WATERLOGGED);
    }

    public ItemStack bait() {
        return bait;
    }

    public int progress() {
        return progress;
    }

    public int goal() {
        return goal > 0 ? goal : kind() == TrapBlock.Kind.NET ? NET_GOAL : POT_GOAL;
    }

    /** Nombre de prises en attente. */
    public int caught() {
        int n = 0;
        for (int i = 0; i < catches.getSlots(); i++) {
            n += catches.getStackInSlot(i).getCount();
        }
        return n;
    }

    public boolean full() {
        for (int i = 0; i < catches.getSlots(); i++) {
            if (catches.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Blocs d'eau dans 5 x 3 x 5 autour du filet. */
    public int water() {
        if (level == null) {
            return 0;
        }
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-2, -1, -2), worldPosition.offset(2, 1, 2))) {
            if (level.getFluidState(p).is(FluidTags.WATER)) {
                n++;
            }
        }
        return n;
    }

    /** Ajoute des appats ; renvoie ce qui reste. */
    public ItemStack addBait(ItemStack stack) {
        if (!stack.is(PiCrustaceans.BAITS)) {
            return stack;
        }
        if (bait.isEmpty()) {
            int n = Math.min(BAIT_MAX, stack.getCount());
            bait = stack.copyWithCount(n);
            stack.shrink(n);
        } else if (ItemStack.isSameItemSameTags(bait, stack)) {
            int n = Math.min(BAIT_MAX - bait.getCount(), stack.getCount());
            bait.grow(n);
            stack.shrink(n);
        }
        setChanged();
        return stack;
    }

    public List<ItemStack> takeCatch() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < catches.getSlots(); i++) {
            ItemStack s = catches.getStackInSlot(i);
            if (!s.isEmpty()) {
                out.add(s.copy());
                catches.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        return out;
    }

    public List<ItemStack> everything() {
        List<ItemStack> out = takeCatch();
        if (!bait.isEmpty()) {
            out.add(bait);
            bait = ItemStack.EMPTY;
        }
        return out;
    }

    void serverTick(ServerLevel level) {
        if ((level.getGameTime() + worldPosition.asLong()) % CHECK != 0 || full() || !submerged()) {
            return;
        }
        if (goal <= 0) {
            newGoal(level);
        }
        if (kind() == TrapBlock.Kind.NET) {
            int w = water();
            if (w < NET_MIN_WATER) {
                return;
            }
            progress += w;
            if (progress >= goal) {
                int species = FishSpawns.pickWild(level, worldPosition, level.random);   //1.0.5-beta (lot 25) : pas de requin au casier
                if (species >= 0) {
                    FishDef d = GeneratedFish.SPECIES[species];
                    int cm = d.cmMin() + level.random.nextInt(Math.max(1, (d.cmMax() - d.cmMin()) / 2 + 1));
                    ItemHandlerHelper.insertItemStacked(catches, FishItems.of(species, cm), false);
                }
                progress = 0;
                newGoal(level);
            }
        } else {
            if (bait.isEmpty() || CrustaceanSpawns.pick(level, worldPosition, level.random, true) < 0) {
                return;
            }
            progress++;
            if (progress >= goal) {
                int species = CrustaceanSpawns.pick(level, worldPosition, level.random, true);
                if (species >= 0) {
                    ItemHandlerHelper.insertItemStacked(catches, CrustaceanSpawns.catchOf(species, 0.8F + level.random.nextFloat() * 0.4F, level.random),
                          false);
                    bait.shrink(1);
                }
                progress = 0;
                newGoal(level);
            }
        }
        setChanged();
    }

    private void newGoal(ServerLevel level) {
        int base = kind() == TrapBlock.Kind.NET ? NET_GOAL : POT_GOAL;
        goal = base * (75 + level.random.nextInt(51)) / 100;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("catch", catches.serializeNBT());
        if (!bait.isEmpty()) {
            tag.put("bait", bait.save(new CompoundTag()));
        }
        tag.putInt("progress", progress);
        tag.putInt("goal", goal);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        catches.deserializeNBT(tag.getCompound("catch"));
        bait = tag.contains("bait") ? ItemStack.of(tag.getCompound("bait")) : ItemStack.EMPTY;
        progress = tag.getInt("progress");
        goal = tag.getInt("goal");
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return handler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        handler.invalidate();
    }

    /** Vue des entonnoirs : 9 cases de prises (sortie seulement), puis la case d'appat du casier (entree seulement). */
    private class Access implements IItemHandler {

        @Override
        public int getSlots() {
            return catches.getSlots() + (kind() == TrapBlock.Kind.POT ? 1 : 0);
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot < catches.getSlots() ? catches.getStackInSlot(slot) : bait;
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (slot < catches.getSlots() || !stack.is(PiCrustaceans.BAITS)) {
                return stack;
            }
            if (simulate) {
                if (!bait.isEmpty() && !ItemStack.isSameItemSameTags(bait, stack)) {
                    return stack;
                }
                int n = Math.min(BAIT_MAX - bait.getCount(), stack.getCount());
                return stack.copyWithCount(stack.getCount() - n);
            }
            return addBait(stack.copy());
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot < catches.getSlots() ? catches.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot < catches.getSlots() ? 64 : BAIT_MAX;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot >= catches.getSlots() && stack.is(PiCrustaceans.BAITS);
        }
    }
}

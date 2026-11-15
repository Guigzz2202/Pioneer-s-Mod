package fr.pioneers.transport;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.core.Action;
import fr.pioneers.core.gas.GasAccess;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Ce qu'un reseau de cables sait faire avec une ressource : trouver le gestionnaire d'un bloc, en extraire, y inserer. Une implementation
 * par famille (objets et fluides par les capacites de Forge, gaz par le socle et sa passerelle Mekanism).
 *
 * @param <H> gestionnaire (capacite)
 * @param <R> pile de ressource
 */
public interface Transfer<H, R> {

    @Nullable
    H find(BlockEntity tile, Direction face);

    /**
     * 0.20.46 (audit) : capacite Forge qui porte le gestionnaire trouve par {@link #find} : le reseau garde ce gestionnaire tant qu'elle reste
     * valide (sans elle, il le relit regulierement). Null si inconnue (passerelle de gaz Mekanism).
     */
    @Nullable
    default LazyOptional<?> capability(BlockEntity tile, Direction face) {
        return null;
    }

    /** Extrait jusqu'a {@code max} (une seule sorte de ressource). */
    R extract(H handler, long max, boolean simulate);

    /** Insere ; renvoie la quantite acceptee. */
    long insert(H handler, R resource, boolean simulate);

    long amount(R resource);

    R withAmount(R resource, long amount);

    /** Meme sorte de ressource (objet + composants, fluide + NBT, gaz ; l'energie est toujours la meme). */
    boolean same(R a, R b);

    /** Plusieurs sortes a la fois dans un reseau (objets) ; sinon une seule. */
    default boolean multi() {
        return false;
    }

    /** Taille maximale d'une pile rangee (objets). */
    default long stackLimit(R resource) {
        return Long.MAX_VALUE;
    }

    /** Extrait jusqu'a {@code max} de la meme sorte que {@code like}. */
    default R extractLike(H handler, R like, long max, boolean simulate) {
        R out = extract(handler, max, true);
        if (isEmpty(out) || !same(out, like)) {
            return withAmount(like, 0);
        }
        return simulate ? out : extract(handler, amount(out), false);
    }

    CompoundTag save(R resource);

    R load(CompoundTag tag);

    R empty();

    /** Instantane de la ressource pour l'affichage client (cables transparents) ; vide pour l'energie. */
    default CompoundTag snapshot(R resource) {
        return new CompoundTag();
    }

    default boolean isEmpty(R resource) {
        return amount(resource) <= 0;
    }

    Transfer<IItemHandler, ItemStack> ITEMS = new Transfer<>() {
        @Nullable
        @Override
        public IItemHandler find(BlockEntity tile, Direction face) {
            return tile.getCapability(ForgeCapabilities.ITEM_HANDLER, face).resolve().orElse(null);
        }

        @Override
        public LazyOptional<?> capability(BlockEntity tile, Direction face) {
            return tile.getCapability(ForgeCapabilities.ITEM_HANDLER, face);
        }

        @Override
        public ItemStack extract(IItemHandler handler, long max, boolean simulate) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (handler.getStackInSlot(slot).isEmpty()) {
                    continue;
                }
                ItemStack out = handler.extractItem(slot, (int) Math.min(64, max), simulate);
                if (!out.isEmpty()) {
                    return out;
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public long insert(IItemHandler handler, ItemStack resource, boolean simulate) {
            ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, resource.copy(), simulate);
            return resource.getCount() - rest.getCount();
        }

        @Override
        public long amount(ItemStack resource) {
            return resource.getCount();
        }

        @Override
        public boolean same(ItemStack a, ItemStack b) {
            return ItemStack.isSameItemSameTags(a, b);
        }

        @Override
        public boolean multi() {
            return true;
        }

        @Override
        public long stackLimit(ItemStack resource) {
            return resource.getMaxStackSize();
        }

        @Override
        public ItemStack extractLike(IItemHandler handler, ItemStack like, long max, boolean simulate) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (!ItemStack.isSameItemSameTags(handler.getStackInSlot(slot), like)) {
                    continue;
                }
                ItemStack out = handler.extractItem(slot, (int) Math.min(64, max), simulate);
                if (!out.isEmpty()) {
                    return out;
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public CompoundTag save(ItemStack resource) {
            CompoundTag tag = resource.copyWithCount(1).save(new CompoundTag());
            tag.putInt("n", resource.getCount());
            return tag;
        }

        @Override
        public ItemStack load(CompoundTag tag) {
            ItemStack stack = ItemStack.of(tag);
            return stack.isEmpty() ? stack : stack.copyWithCount(Math.max(1, tag.getInt("n")));
        }

        @Override
        public ItemStack empty() {
            return ItemStack.EMPTY;
        }

        @Override
        public CompoundTag snapshot(ItemStack resource) {
            return resource.copyWithCount(1).save(new CompoundTag());
        }

        @Override
        public ItemStack withAmount(ItemStack resource, long amount) {
            return amount <= 0 ? ItemStack.EMPTY : resource.copyWithCount((int) Math.min(Integer.MAX_VALUE, amount));
        }
    };

    Transfer<IFluidHandler, FluidStack> FLUIDS = new Transfer<>() {
        @Nullable
        @Override
        public IFluidHandler find(BlockEntity tile, Direction face) {
            return tile.getCapability(ForgeCapabilities.FLUID_HANDLER, face).resolve().orElse(null);
        }

        @Override
        public LazyOptional<?> capability(BlockEntity tile, Direction face) {
            return tile.getCapability(ForgeCapabilities.FLUID_HANDLER, face);
        }

        @Override
        public FluidStack extract(IFluidHandler handler, long max, boolean simulate) {
            return handler.drain((int) Math.min(Integer.MAX_VALUE, max), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public long insert(IFluidHandler handler, FluidStack resource, boolean simulate) {
            return handler.fill(resource.copy(), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public long amount(FluidStack resource) {
            return resource.getAmount();
        }

        @Override
        public boolean same(FluidStack a, FluidStack b) {
            return a.isFluidEqual(b);
        }

        @Override
        public FluidStack extractLike(IFluidHandler handler, FluidStack like, long max, boolean simulate) {
            return handler.drain(new FluidStack(like, (int) Math.min(Integer.MAX_VALUE, max)),
                  simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public CompoundTag save(FluidStack resource) {
            return resource.writeToNBT(new CompoundTag());
        }

        @Override
        public FluidStack load(CompoundTag tag) {
            return FluidStack.loadFluidStackFromNBT(tag);
        }

        @Override
        public FluidStack empty() {
            return FluidStack.EMPTY;
        }

        @Override
        public CompoundTag snapshot(FluidStack resource) {
            return new FluidStack(resource, 1000).writeToNBT(new CompoundTag());
        }

        @Override
        public FluidStack withAmount(FluidStack resource, long amount) {
            return amount <= 0 ? FluidStack.EMPTY : new FluidStack(resource, (int) Math.min(Integer.MAX_VALUE, amount));
        }
    };

    Transfer<IGasHandler, GasStack> GASES = new Transfer<>() {
        @Nullable
        @Override
        public IGasHandler find(BlockEntity tile, Direction face) {
            return GasAccess.find(tile, face);
        }

        @Nullable
        @Override
        public LazyOptional<?> capability(BlockEntity tile, Direction face) {
            //Notre capacite si c'est elle qui a servi ; la passerelle Mekanism n'en donne pas
            LazyOptional<IGasHandler> own = tile.getCapability(PioneersAPI.GAS_HANDLER, face);
            return own.isPresent() ? own : null;
        }

        @Override
        public GasStack extract(IGasHandler handler, long max, boolean simulate) {
            return handler.extractGas(max, Action.get(!simulate));
        }

        @Override
        public long insert(IGasHandler handler, GasStack resource, boolean simulate) {
            return resource.getAmount() - handler.insertGas(resource.copy(), Action.get(!simulate)).getAmount();
        }

        @Override
        public long amount(GasStack resource) {
            return resource.getAmount();
        }

        @Override
        public boolean same(GasStack a, GasStack b) {
            return a.isSameGas(b);
        }

        @Override
        public GasStack extractLike(IGasHandler handler, GasStack like, long max, boolean simulate) {
            return handler.extractGas(like.copyWithAmount(max), Action.get(!simulate));
        }

        @Override
        public CompoundTag save(GasStack resource) {
            return resource.save();
        }

        @Override
        public GasStack load(CompoundTag tag) {
            return GasStack.load(tag);
        }

        @Override
        public GasStack empty() {
            return GasStack.EMPTY;
        }

        @Override
        public CompoundTag snapshot(GasStack resource) {
            return resource.copyWithAmount(1).save();
        }

        @Override
        public GasStack withAmount(GasStack resource, long amount) {
            return resource.copyWithAmount(amount);
        }
    };

    /** Energie (FE) : la « ressource » n'est qu'une quantite. */
    Transfer<IEnergyStorage, Long> ENERGY = new Transfer<>() {
        @Nullable
        @Override
        public IEnergyStorage find(BlockEntity tile, Direction face) {
            return tile.getCapability(ForgeCapabilities.ENERGY, face).resolve().orElse(null);
        }

        @Override
        public LazyOptional<?> capability(BlockEntity tile, Direction face) {
            return tile.getCapability(ForgeCapabilities.ENERGY, face);
        }

        @Override
        public Long extract(IEnergyStorage handler, long max, boolean simulate) {
            return handler.canExtract() ? (long) handler.extractEnergy((int) Math.min(Integer.MAX_VALUE, max), simulate) : 0L;
        }

        @Override
        public long insert(IEnergyStorage handler, Long resource, boolean simulate) {
            return handler.canReceive() ? handler.receiveEnergy((int) Math.min(Integer.MAX_VALUE, resource), simulate) : 0;
        }

        @Override
        public long amount(Long resource) {
            return resource == null ? 0 : resource;
        }

        @Override
        public boolean same(Long a, Long b) {
            return true;
        }

        @Override
        public Long extractLike(IEnergyStorage handler, Long like, long max, boolean simulate) {
            return extract(handler, max, simulate);
        }

        @Override
        public CompoundTag save(Long resource) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("fe", resource == null ? 0 : resource);
            return tag;
        }

        @Override
        public Long load(CompoundTag tag) {
            return tag.getLong("fe");
        }

        @Override
        public Long empty() {
            return 0L;
        }

        @Override
        public Long withAmount(Long resource, long amount) {
            return Math.max(0, amount);
        }
    };
}

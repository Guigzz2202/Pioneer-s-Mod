package fr.pioneers.space.farm;

import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.common.port.PortConfigurable;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.Configurable;
import fr.pioneers.core.Directions;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.industry.multi.CasingBlock;
import fr.pioneers.tech.industry.multi.IndustrialGlassBlock;
import fr.pioneers.tech.industry.multi.SheetMetalBlock;
import fr.pioneers.tech.industry.multi.StructureFrameBlock;
import fr.pioneers.core.registries.PiBaseMachines;
import fr.pioneers.core.registries.PiBlocks;
import fr.pioneers.core.registries.PiTileEntityTypes;
import fr.pioneers.space.tile.TileEntityGreenhouse;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Port de serre : relaie les objets ou l'energie vers le controleur de la serre a laquelle il appartient.
 */
public class GreenhousePortTile extends PiBlockEntity implements Configurable.Holder {

    @Nullable
    private BlockPos controller;
    private long linkedAt = -1000;
    private final LazyOptional<IItemHandler> items = LazyOptional.of(ItemHandler::new);
    private final LazyOptional<IEnergyStorage> energy = LazyOptional.of(EnergyHandler::new);

    public GreenhousePortTile(BlockPos pos, BlockState state) {
        super(PiTileEntityTypes.GREENHOUSE_PORT.get(), pos, state);
    }

    /** Appele par le controleur a chaque verification de la structure. */
    public void link(BlockPos controllerPos) {
        controller = controllerPos;
        linkedAt = level == null ? 0 : level.getGameTime();
    }

    @Nullable
    private TileEntityGreenhouse getController() {
        //Lot 8a : hote trouve par le registre commun des hotes (cases de la serre formee, MultiblockPort)
        TileEntityGreenhouse host = fr.pioneers.api.multiblock.MultiblockPort.house(level, worldPosition, TileEntityGreenhouse.class);
        if (host != null && host.isFormed()) {
            return host;
        }
        if (controller == null || level == null || level.getGameTime() - linkedAt > 45) {
            return null;
        }
        return level.getBlockEntity(controller) instanceof TileEntityGreenhouse greenhouse && greenhouse.isFormed() ? greenhouse : null;
    }

    private GreenhousePortBlock.Mode getMode() {
        return getBlockState().getValue(GreenhousePortBlock.MODE);
    }

    /** Port de sortie : ejecte la recolte dans les inventaires voisins (hors serre). */
    public void tickServer() {
        if (level == null || level.getGameTime() % 10 != 0 || getMode() != GreenhousePortBlock.Mode.OUTPUT) {
            return;
        }
        TileEntityGreenhouse greenhouse = getController();
        if (greenhouse == null) {
            return;
        }
        for (Direction direction : Directions.ALL) {
            BlockPos target = worldPosition.relative(direction);
            if (isGreenhouseBlock(level.getBlockState(target))) {
                continue;
            }
            //1.9 (MAJ RP, bloc 4) : rien ne sort a travers une frontiere de claim refusee
            BlockEntity tile = ClanApi.canTransfer(level, worldPosition, target) ? level.getBlockEntity(target) : null;
            if (tile == null) {
                continue;
            }
            tile.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite()).ifPresent(handler -> {
                for (ItemSlot slot : greenhouse.getOutputSlots()) {
                    if (slot.isEmpty()) {
                        continue;
                    }
                    ItemStack stack = slot.extract(64, Action.SIMULATE, Automation.INTERNAL);
                    ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, stack, false);
                    int moved = stack.getCount() - rest.getCount();
                    if (moved > 0) {
                        slot.extract(moved, Action.EXECUTE, Automation.INTERNAL);
                    }
                }
            });
        }
    }

    /** Piece de la base construite (1.0) : on n'y pousse rien. */
    private static boolean isGreenhouseBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof CasingBlock || block instanceof StructureFrameBlock
               || block instanceof SheetMetalBlock || block instanceof IndustrialGlassBlock
               || block == PiBlocks.GREENHOUSE_PORT.getBlock() || block == PiBaseMachines.GREENHOUSE_CONTROLLER.getBlock();
    }

    private final PortConfigurable configurable = (new PortConfigurable(
          () -> Component.translatable(getMode().getTranslationKey()), this::cycleMode));

    private void cycleMode() {
        if (level != null) {
            //0.20.28 : le port ne connait que son sens ; l'energie entre par tous les ports (ancien mode energie : lu comme une entree)
            GreenhousePortBlock.Mode next = getMode() == GreenhousePortBlock.Mode.OUTPUT ? GreenhousePortBlock.Mode.INPUT : GreenhousePortBlock.Mode.OUTPUT;
            level.setBlock(worldPosition, getBlockState().setValue(GreenhousePortBlock.MODE, next), Block.UPDATE_ALL);
        }
    }

    /** Main vide : ouvre l'interface du controleur de la structure. */
    public InteractionResult openController(Player player, InteractionHand hand,
          BlockHitResult hit) {
        if (level == null || controller == null) {
            return InteractionResult.PASS;
        }
        return level.getBlockState(controller).use(level, player, hand, new BlockHitResult(hit.getLocation(), hit.getDirection(), controller, false));
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        GreenhousePortBlock.Mode mode = getMode();
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return items.cast();
        } else if (cap == ForgeCapabilities.ENERGY) {
            return energy.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        items.invalidate();
        energy.invalidate();
    }

    /** Entree : seules les cases de graines ; sortie : seules les cases de recolte. */
    private class ItemHandler implements IItemHandler {

        private List<ItemSlot> slots() {
            TileEntityGreenhouse greenhouse = getController();
            if (greenhouse == null) {
                return List.of();
            }
            return getMode() == GreenhousePortBlock.Mode.OUTPUT ? greenhouse.getOutputSlots() : greenhouse.getSeedSlots();
        }

        @Override
        public int getSlots() {
            return slots().size();
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            var list = slots();
            return slot < list.size() ? list.get(slot).getStack() : ItemStack.EMPTY;
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            var list = slots();
            if (getMode() == GreenhousePortBlock.Mode.OUTPUT || slot >= list.size()) {
                return stack;
            }
            return list.get(slot).insert(stack, simulate ? Action.SIMULATE : Action.EXECUTE, Automation.INTERNAL);
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            var list = slots();
            if (getMode() != GreenhousePortBlock.Mode.OUTPUT || slot >= list.size()) {
                return ItemStack.EMPTY;
            }
            return list.get(slot).extract(amount, simulate ? Action.SIMULATE : Action.EXECUTE, Automation.INTERNAL);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return getMode() != GreenhousePortBlock.Mode.OUTPUT && stack.getItem() instanceof ItemSpaceSeeds;
        }
    }

    /** Energie (Forge Energy, accepte aussi les cables universels de Mekanism). */
    private class EnergyHandler implements IEnergyStorage {

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            TileEntityGreenhouse greenhouse = getController();
            return greenhouse == null || maxReceive <= 0 ? 0 : greenhouse.receiveFromPort(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            TileEntityGreenhouse greenhouse = getController();
            return greenhouse == null ? 0 : (int) Math.min(Integer.MAX_VALUE, greenhouse.getEnergy().getStored());
        }

        @Override
        public int getMaxEnergyStored() {
            TileEntityGreenhouse greenhouse = getController();
            return greenhouse == null ? 0 : (int) Math.min(Integer.MAX_VALUE, greenhouse.getEnergy().getCapacity());
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    @Override
    public Configurable getConfigurable(@Nullable Direction side) {
        return configurable;
    }
}

package fr.pioneers.robotics;

import com.mojang.authlib.GameProfile;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.IDataHandler;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.api.pressure.IPressureHandler;
import fr.pioneers.core.Action;
import fr.pioneers.core.gas.GasAccess;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Actions des unites sur le monde : casser un bloc (avec l'accord des mods de protection, via un faux joueur), et echanges logistiques
 * par les capacites des blocs, pour toutes les familles de ressources (objets, fluides, gaz de Mekanism, energie FE, donnees du mod).
 */
public final class RoboticsWorld {

    private RoboticsWorld() {
    }

    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("2b6f4c1e-7a35-4d8e-9f10-6c3e5a7b9d21"), "[Pioneer's]");

    public static FakePlayer fakePlayer(ServerLevel level) {
        return FakePlayerFactory.get(level, PROFILE);
    }

    /** Bloc qu'une unite peut casser : solide, cassable, sans entite de bloc (coffres, machines) et pas un liquide. */
    public static boolean canBreak(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0 && !state.hasBlockEntity() && !(state.getBlock() instanceof LiquidBlock);
    }

    /** Casse un bloc et renvoie ce qu'il donne, ou null si c'est interdit (protection, bloc incassable). */
    @Nullable
    public static List<ItemStack> breakBlock(ServerLevel level, BlockPos pos, Entity breaker, ItemStack tool) {
        return breakBlock(level, pos, breaker, tool, null);
    }

    /** Renvoye par {@link #breakBlock} quand le butin ne tiendrait pas : le bloc n'est pas casse. */
    public static final List<ItemStack> NO_ROOM = Collections.unmodifiableList(new ArrayList<>());

    /**
     * Casse un bloc et renvoie son butin (rien ne tombe au sol), ou null si c'est interdit. Si {@code accept} refuse le butin (soute
     * trop pleine), le bloc reste en place et on renvoie {@link #NO_ROOM}.
     */
    @Nullable
    public static List<ItemStack> breakBlock(ServerLevel level, BlockPos pos, Entity breaker, ItemStack tool,
          @Nullable Predicate<List<ItemStack>> accept) {
        BlockState state = level.getBlockState(pos);
        if (!canBreak(level, pos, state)) {
            return null;
        }
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), breaker, tool);
        if (accept != null && !accept.test(drops)) {
            return NO_ROOM;
        }
        FakePlayer player = fakePlayer(level);
        player.setPos(breaker.getX(), breaker.getY(), breaker.getZ());
        if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player))) {
            return null;
        }
        level.destroyBlock(pos, false, breaker);
        return drops;
    }

    // ------------------------------------------------------------------
    // Capacites
    // ------------------------------------------------------------------

    @Nullable
    public static <T> T capability(ServerLevel level, BlockPos pos, @Nullable Direction side, Capability<T> capability) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockEntity tile = level.getBlockEntity(pos);
        return tile == null ? null : tile.getCapability(capability, side).resolve().orElse(null);
    }

    /** Reservoirs de gaz (Pioneer's, ou ceux d'un autre mod par passerelle). */
    @Nullable
    public static IGasHandler gases(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        return level.isLoaded(pos) ? GasAccess.find(level.getBlockEntity(pos), side) : null;
    }

    @Nullable
    public static IItemHandler items(ServerLevel level, Endpoint at) {
        return capability(level, at.pos(), at.side(), ForgeCapabilities.ITEM_HANDLER);
    }

    /** Le bloc expose-t-il quelque chose pour cette famille de ressources ? */
    public static boolean handles(ServerLevel level, Endpoint at, Resource type) {
        return switch (type) {
            case ITEMS -> items(level, at) != null;
            case FLUIDS -> capability(level, at.pos(), at.side(), ForgeCapabilities.FLUID_HANDLER) != null;
            case GAS -> gases(level, at.pos(), at.side()) != null;
            case ENERGY -> capability(level, at.pos(), at.side(), ForgeCapabilities.ENERGY) != null;
            case PRESSURE -> capability(level, at.pos(), at.side(), PioneersAPI.PRESSURE_HANDLER) != null;
            case DATA -> capability(level, at.pos(), at.side(), PioneersAPI.DATA_HANDLER) != null;
        };
    }

    // ------------------------------------------------------------------
    // Inventaires : comptage et contenu
    // ------------------------------------------------------------------

    /** Quantite d'une ressource (objets, mB, FE, Mo) presente dans le bloc. */
    public static long count(ServerLevel level, Endpoint at, LogisticResource res) {
        long count = 0;
        switch (res.type) {
            case ITEMS -> {
                IItemHandler handler = items(level, at);
                if (handler != null) {
                    for (int slot = 0; slot < handler.getSlots(); slot++) {
                        ItemStack stack = handler.getStackInSlot(slot);
                        if (res.matches(stack)) {
                            count += stack.getCount();
                        }
                    }
                }
            }
            case FLUIDS -> {
                IFluidHandler handler = capability(level, at.pos(), at.side(), ForgeCapabilities.FLUID_HANDLER);
                if (handler != null) {
                    for (int tank = 0; tank < handler.getTanks(); tank++) {
                        FluidStack stack = handler.getFluidInTank(tank);
                        if (res.matches(stack)) {
                            count += stack.getAmount();
                        }
                    }
                }
            }
            case GAS -> {
                IGasHandler handler = gases(level, at.pos(), at.side());
                if (handler != null) {
                    for (int tank = 0; tank < handler.getGasTanks(); tank++) {
                        GasStack stack = handler.getGasInTank(tank);
                        if (res.matches(stack)) {
                            count += stack.getAmount();
                        }
                    }
                }
            }
            case ENERGY -> {
                IEnergyStorage handler = capability(level, at.pos(), at.side(), ForgeCapabilities.ENERGY);
                if (handler != null) {
                    count = handler.getEnergyStored();
                }
            }
            case PRESSURE -> {
                IPressureHandler handler = capability(level, at.pos(), at.side(), PioneersAPI.PRESSURE_HANDLER);
                if (handler != null) {
                    count = (long) handler.getAir();
                }
            }
            case DATA -> {
                IDataHandler handler = capability(level, at.pos(), at.side(), PioneersAPI.DATA_HANDLER);
                if (handler != null) {
                    for (int tank = 0; tank < handler.getDataTanks(); tank++) {
                        DataStack stack = handler.getDataInTank(tank);
                        if (res.matches(stack)) {
                            count += stack.getAmount();
                        }
                    }
                }
            }
        }
        return count;
    }

    /** Tout ce que contient le bloc (stocks du reseau), ressource par ressource. */
    public static Map<LogisticResource, Long> contents(ServerLevel level, Endpoint at) {
        Map<LogisticResource, Long> map = new LinkedHashMap<>();
        IItemHandler items = items(level, at);
        if (items != null) {
            for (int slot = 0; slot < items.getSlots(); slot++) {
                ItemStack stack = items.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    map.merge(LogisticResource.of(stack), (long) stack.getCount(), Long::sum);
                }
            }
        }
        IFluidHandler fluids = capability(level, at.pos(), at.side(), ForgeCapabilities.FLUID_HANDLER);
        if (fluids != null) {
            for (int tank = 0; tank < fluids.getTanks(); tank++) {
                FluidStack stack = fluids.getFluidInTank(tank);
                if (!stack.isEmpty()) {
                    map.merge(LogisticResource.of(stack), (long) stack.getAmount(), Long::sum);
                }
            }
        }
        IGasHandler gases = gases(level, at.pos(), at.side());
        if (gases != null) {
            for (int tank = 0; tank < gases.getGasTanks(); tank++) {
                GasStack stack = gases.getGasInTank(tank);
                if (!stack.isEmpty()) {
                    map.merge(LogisticResource.of(stack.getGas()), stack.getAmount(), Long::sum);
                }
            }
        }
        IEnergyStorage energy = capability(level, at.pos(), at.side(), ForgeCapabilities.ENERGY);
        if (energy != null && energy.getEnergyStored() > 0 && energy.canExtract()) {
            map.merge(LogisticResource.ENERGY, (long) energy.getEnergyStored(), Long::sum);
        }
        IPressureHandler air = capability(level, at.pos(), at.side(), PioneersAPI.PRESSURE_HANDLER);
        if (air != null && air.getAir() >= 1) {
            map.merge(LogisticResource.PRESSURE, (long) air.getAir(), Long::sum);
        }
        IDataHandler data = capability(level, at.pos(), at.side(), PioneersAPI.DATA_HANDLER);
        if (data != null) {
            for (int tank = 0; tank < data.getDataTanks(); tank++) {
                DataStack stack = data.getDataInTank(tank);
                if (!stack.isEmpty()) {
                    map.merge(LogisticResource.of(stack.getType()), stack.getAmount(), Long::sum);
                }
            }
        }
        return map;
    }

    /** Premiere ressource qu'on peut sortir du bloc et qui convient (precise), ou null. */
    @Nullable
    public static LogisticResource firstExtractable(ServerLevel level, Endpoint at, Predicate<LogisticResource> wanted) {
        IItemHandler items = items(level, at);
        if (items != null) {
            for (int slot = 0; slot < items.getSlots(); slot++) {
                ItemStack stack = items.extractItem(slot, 64, true);
                if (!stack.isEmpty() && wanted.test(LogisticResource.of(stack))) {
                    return LogisticResource.of(stack);
                }
            }
        }
        for (Map.Entry<LogisticResource, Long> entry : contents(level, at).entrySet()) {
            LogisticResource res = entry.getKey();
            if (res.type != Resource.ITEMS && wanted.test(res) && extractable(level, at, res) > 0) {
                return res;
            }
        }
        return null;
    }

    /** Quantite d'une ressource qu'on pourrait sortir (simulation). */
    public static long extractable(ServerLevel level, Endpoint at, LogisticResource res) {
        UnitCargo probe = new UnitCargo(27, Integer.MAX_VALUE / 2, Long.MAX_VALUE / 4);
        return take(level, at, res, Long.MAX_VALUE, probe, null, true);
    }

    // ------------------------------------------------------------------
    // Prise et livraison
    // ------------------------------------------------------------------

    /**
     * Charge dans la soute jusqu'a {@code limit} de la ressource (precise ou famille entiere), sans depasser ce que {@code accept}
     * (si donne) peut recevoir. Renvoie la quantite chargee (ou qui le serait en simulation).
     */
    public static long take(ServerLevel level, Endpoint from, LogisticResource res, long limit, UnitCargo cargo, @Nullable Endpoint accept,
          boolean simulate) {
        if (limit <= 0) {
            limit = Long.MAX_VALUE;
        }
        switch (res.type) {
            case ITEMS -> {
                IItemHandler source = items(level, from);
                IItemHandler target = accept == null ? null : items(level, accept);
                if (source == null || accept != null && target == null) {
                    return 0;
                }
                long moved = 0;
                int stacksLeft = cargo.stacks - cargo.items.size();
                for (int slot = 0; slot < source.getSlots() && moved < limit; slot++) {
                    ItemStack peek = source.extractItem(slot, (int) Math.min(64, limit - moved), true);
                    if (peek.isEmpty() || !res.matches(peek)) {
                        continue;
                    }
                    int room = simulate ? Math.max(0, stacksLeft) * peek.getMaxStackSize() : cargo.itemRoom(peek);
                    int amount = Math.min(peek.getCount(), room);
                    if (target != null && amount > 0) {
                        amount -= ItemHandlerHelper.insertItem(target, peek.copyWithCount(amount), true).getCount();
                    }
                    if (amount <= 0) {
                        continue;
                    }
                    if (simulate) {
                        moved += amount;
                        stacksLeft -= (amount + peek.getMaxStackSize() - 1) / peek.getMaxStackSize();
                        continue;
                    }
                    ItemStack taken = source.extractItem(slot, amount, false);
                    ItemStack left = cargo.insert(taken);
                    if (!left.isEmpty()) {
                        ItemHandlerHelper.insertItem(source, left, false);
                    }
                    moved += taken.getCount() - left.getCount();
                }
                return moved;
            }
            case FLUIDS -> {
                IFluidHandler source = capability(level, from.pos(), from.side(), ForgeCapabilities.FLUID_HANDLER);
                IFluidHandler target = accept == null ? null : capability(level, accept.pos(), accept.side(), ForgeCapabilities.FLUID_HANDLER);
                if (source == null || accept != null && target == null || !cargo.gas.isEmpty() || !cargo.data.isEmpty()) {
                    return 0;
                }
                int room = (int) Math.min(Math.min(limit, Integer.MAX_VALUE), cargo.tank - cargo.fluid.getAmount());
                FluidStack peek = FluidStack.EMPTY;
                if (room > 0) {
                    if (res.isAny()) {
                        peek = cargo.fluid.isEmpty() ? source.drain(room, IFluidHandler.FluidAction.SIMULATE)
                                                     : source.drain(new FluidStack(cargo.fluid, room), IFluidHandler.FluidAction.SIMULATE);
                    } else {
                        peek = source.drain(new FluidStack(res.fluid(), room), IFluidHandler.FluidAction.SIMULATE);
                    }
                }
                if (peek.isEmpty() || !res.matches(peek) || !cargo.fluid.isEmpty() && !cargo.fluid.isFluidEqual(peek)) {
                    return 0;
                }
                int amount = target == null ? peek.getAmount() : target.fill(peek, IFluidHandler.FluidAction.SIMULATE);
                if (amount <= 0 || simulate) {
                    return Math.max(0, amount);
                }
                FluidStack taken = source.drain(new FluidStack(peek, amount), IFluidHandler.FluidAction.EXECUTE);
                if (cargo.fluid.isEmpty()) {
                    cargo.fluid = taken;
                } else {
                    cargo.fluid.grow(taken.getAmount());
                }
                return taken.getAmount();
            }
            case GAS -> {
                IGasHandler source = gases(level, from.pos(), from.side());
                IGasHandler target = accept == null ? null : gases(level, accept.pos(), accept.side());
                if (source == null || accept != null && target == null || !cargo.fluid.isEmpty() || !cargo.data.isEmpty()) {
                    return 0;
                }
                long room = Math.min(limit, cargo.tank - cargo.gas.getAmount());
                GasStack peek = GasStack.EMPTY;
                if (room > 0) {
                    if (res.gas() != null) {
                        peek = source.extractGas(GasStack.of(res.gas(), room), Action.SIMULATE);
                    } else if (!cargo.gas.isEmpty()) {
                        peek = source.extractGas(cargo.gas.copyWithAmount(room), Action.SIMULATE);
                    } else {
                        peek = source.extractGas(room, Action.SIMULATE);
                    }
                }
                if (peek.isEmpty() || !res.matches(peek) || !cargo.gas.isEmpty() && !cargo.gas.isSameGas(peek)) {
                    return 0;
                }
                long amount = target == null ? peek.getAmount() : peek.getAmount() - target.insertGas(peek, Action.SIMULATE).getAmount();
                if (amount <= 0 || simulate) {
                    return Math.max(0, amount);
                }
                GasStack taken = source.extractGas(peek.copyWithAmount(amount), Action.EXECUTE);
                if (cargo.gas.isEmpty()) {
                    cargo.gas = taken;
                } else {
                    cargo.gas.grow(taken.getAmount());
                }
                return taken.getAmount();
            }
            case ENERGY -> {
                IEnergyStorage source = capability(level, from.pos(), from.side(), ForgeCapabilities.ENERGY);
                IEnergyStorage target = accept == null ? null : capability(level, accept.pos(), accept.side(), ForgeCapabilities.ENERGY);
                if (source == null || accept != null && target == null) {
                    return 0;
                }
                int room = (int) Math.min(Integer.MAX_VALUE, Math.min(limit, cargo.energyCapacity - cargo.energy));
                int amount = room <= 0 ? 0 : source.extractEnergy(room, true);
                if (target != null && amount > 0) {
                    amount = Math.min(amount, target.receiveEnergy(amount, true));
                }
                if (amount <= 0 || simulate) {
                    return Math.max(0, amount);
                }
                int taken = source.extractEnergy(amount, false);
                cargo.energy += taken;
                return taken;
            }
            case PRESSURE -> {
                IPressureHandler source = capability(level, from.pos(), from.side(), PioneersAPI.PRESSURE_HANDLER);
                IPressureHandler target = accept == null ? null : capability(level, accept.pos(), accept.side(), PioneersAPI.PRESSURE_HANDLER);
                if (source == null || accept != null && target == null) {
                    return 0;
                }
                long room = Math.min(limit, cargo.airCapacity - cargo.air);
                long amount = room <= 0 ? 0 : (long) source.extractAir(room, true);
                if (target != null && amount > 0) {
                    amount = Math.min(amount, (long) target.insertAir(amount, true));
                }
                if (amount <= 0 || simulate) {
                    return Math.max(0, amount);
                }
                long taken = (long) source.extractAir(amount, false);
                cargo.air += taken;
                return taken;
            }
            case DATA -> {
                IDataHandler source = capability(level, from.pos(), from.side(), PioneersAPI.DATA_HANDLER);
                IDataHandler target = accept == null ? null : capability(level, accept.pos(), accept.side(), PioneersAPI.DATA_HANDLER);
                if (source == null || accept != null && target == null || !cargo.fluid.isEmpty() || !cargo.gas.isEmpty()) {
                    return 0;
                }
                long room = Math.min(limit, cargo.tank - cargo.data.getAmount());
                DataStack peek = DataStack.EMPTY;
                if (room > 0) {
                    if (res.data() != null) {
                        peek = source.extractData(DataStack.of(res.data(), room), Action.SIMULATE);
                    } else if (!cargo.data.isEmpty()) {
                        peek = source.extractData(cargo.data.copyWithAmount(room), Action.SIMULATE);
                    } else {
                        peek = source.extractData(room, Action.SIMULATE);
                    }
                }
                if (peek.isEmpty() || !res.matches(peek) || !cargo.data.isEmpty() && !cargo.data.isTypeEqual(peek)) {
                    return 0;
                }
                long amount = target == null ? peek.getAmount() : peek.getAmount() - target.insertData(peek, Action.SIMULATE).getAmount();
                if (amount <= 0 || simulate) {
                    return Math.max(0, amount);
                }
                DataStack taken = source.extractData(peek.copyWithAmount(amount), Action.EXECUTE);
                if (cargo.data.isEmpty()) {
                    cargo.data = taken.copy();
                } else {
                    cargo.data.grow(taken.getAmount());
                }
                return taken.getAmount();
            }
        }
        return 0;
    }

    /** Livre au bloc ce que la soute contient de cette ressource (famille entiere si « n'importe »), au plus {@code limit}. */
    public static long give(ServerLevel level, Endpoint to, UnitCargo cargo, LogisticResource res, long limit) {
        if (limit <= 0) {
            limit = Long.MAX_VALUE;
        }
        long delivered = 0;
        switch (res.type) {
            case ITEMS -> {
                IItemHandler target = items(level, to);
                if (target == null) {
                    return 0;
                }
                for (int i = 0; i < cargo.items.size() && delivered < limit; i++) {
                    ItemStack stack = cargo.items.get(i);
                    if (!res.matches(stack)) {
                        continue;
                    }
                    int amount = (int) Math.min(stack.getCount(), limit - delivered);
                    ItemStack rest = ItemHandlerHelper.insertItem(target, stack.copyWithCount(amount), false);
                    int moved = amount - rest.getCount();
                    stack.shrink(moved);
                    delivered += moved;
                }
                cargo.clearItems();
            }
            case FLUIDS -> {
                IFluidHandler target = capability(level, to.pos(), to.side(), ForgeCapabilities.FLUID_HANDLER);
                if (target != null && res.matches(cargo.fluid)) {
                    int filled = target.fill(new FluidStack(cargo.fluid, (int) Math.min(cargo.fluid.getAmount(), limit)), IFluidHandler.FluidAction.EXECUTE);
                    cargo.fluid.shrink(filled);
                    delivered = filled;
                }
            }
            case GAS -> {
                IGasHandler target = gases(level, to.pos(), to.side());
                if (target != null && res.matches(cargo.gas)) {
                    GasStack offered = cargo.gas.copyWithAmount(Math.min(cargo.gas.getAmount(), limit));
                    long moved = offered.getAmount() - target.insertGas(offered, Action.EXECUTE).getAmount();
                    cargo.gas.shrink(moved);
                    delivered = moved;
                }
            }
            case ENERGY -> {
                IEnergyStorage target = capability(level, to.pos(), to.side(), ForgeCapabilities.ENERGY);
                if (target != null && cargo.energy > 0) {
                    int moved = target.receiveEnergy((int) Math.min(Integer.MAX_VALUE, Math.min(cargo.energy, limit)), false);
                    cargo.energy -= moved;
                    delivered = moved;
                }
            }
            case PRESSURE -> {
                IPressureHandler target = capability(level, to.pos(), to.side(), PioneersAPI.PRESSURE_HANDLER);
                if (target != null && cargo.air > 0) {
                    long moved = (long) target.insertAir(Math.min(cargo.air, limit), false);
                    cargo.air -= moved;
                    delivered = moved;
                }
            }
            case DATA -> {
                IDataHandler target = capability(level, to.pos(), to.side(), PioneersAPI.DATA_HANDLER);
                if (target != null && res.matches(cargo.data)) {
                    DataStack offered = cargo.data.copyWithAmount(Math.min(cargo.data.getAmount(), limit));
                    long moved = offered.getAmount() - target.insertData(offered, Action.EXECUTE).getAmount();
                    cargo.data.shrink(moved);
                    delivered = moved;
                }
            }
        }
        return delivered;
    }

    /** Combien le bloc accepterait de cette ressource (simulation, au plus {@code amount}). */
    public static long room(ServerLevel level, Endpoint to, LogisticResource res, long amount) {
        switch (res.type) {
            case ITEMS -> {
                IItemHandler target = items(level, to);
                if (target == null || res.isAny()) {
                    return target == null ? 0 : amount;
                }
                long accepted = 0;
                while (accepted < amount) {
                    int batch = (int) Math.min(res.item().getMaxStackSize(), amount - accepted);
                    int took = batch - ItemHandlerHelper.insertItem(target, res.item().copyWithCount(batch), true).getCount();
                    accepted += took;
                    if (took < batch) {
                        break;
                    }
                    //La simulation ne remplit pas : une pile suffit pour savoir qu'il y a de la place
                    if (accepted >= res.item().getMaxStackSize()) {
                        return amount;
                    }
                }
                return accepted;
            }
            case FLUIDS -> {
                IFluidHandler target = capability(level, to.pos(), to.side(), ForgeCapabilities.FLUID_HANDLER);
                return target == null || res.isAny() ? 0 : target.fill(new FluidStack(res.fluid(), (int) Math.min(Integer.MAX_VALUE, amount)),
                      IFluidHandler.FluidAction.SIMULATE);
            }
            case GAS -> {
                IGasHandler target = gases(level, to.pos(), to.side());
                if (target == null || res.gas() == null) {
                    return 0;
                }
                return amount - target.insertGas(GasStack.of(res.gas(), amount), Action.SIMULATE).getAmount();
            }
            case ENERGY -> {
                IEnergyStorage target = capability(level, to.pos(), to.side(), ForgeCapabilities.ENERGY);
                return target == null ? 0 : target.receiveEnergy((int) Math.min(Integer.MAX_VALUE, amount), true);
            }
            case PRESSURE -> {
                IPressureHandler target = capability(level, to.pos(), to.side(), PioneersAPI.PRESSURE_HANDLER);
                return target == null ? 0 : (long) target.insertAir(amount, true);
            }
            case DATA -> {
                IDataHandler target = capability(level, to.pos(), to.side(), PioneersAPI.DATA_HANDLER);
                if (target == null || res.data() == null) {
                    return 0;
                }
                return amount - target.insertData(DataStack.of(res.data(), amount), Action.SIMULATE).getAmount();
            }
        }
        return 0;
    }

    /** Range une pile dans le bloc (renvoie le reste). */
    public static ItemStack insert(ServerLevel level, Endpoint at, ItemStack stack, boolean simulate) {
        IItemHandler handler = items(level, at);
        return handler == null ? stack : ItemHandlerHelper.insertItem(handler, stack, simulate);
    }

    /** Premier objet qui peut sortir et qui convient (copie, sans le prendre), ou vide. */
    public static ItemStack peekItem(ServerLevel level, Endpoint at, Predicate<ItemStack> predicate) {
        IItemHandler handler = items(level, at);
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.extractItem(slot, 64, true);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}

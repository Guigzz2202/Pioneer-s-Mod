package fr.pioneers.core.compat.mekanism;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.api.gas.IGasTank;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.side.SideMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import mekanism.api.MekanismAPI;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Passerelle entre les gaz du socle Pioneer's et ceux de Mekanism. C'est le seul endroit qui connait les gaz de Mekanism : un gaz du
 * meme identifiant devient le gaz Pioneer's correspondant, les autres deviennent des gaz « etrangers » (meme nom, meme couleur).
 * Les reservoirs Mekanism se voient ainsi comme des {@link IGasHandler}, et inversement.
 */
public final class MekanismGasBridge {

    private static final Map<ResourceLocation, Gas> FOREIGN = new ConcurrentHashMap<>();

    private MekanismGasBridge() {
    }

    /** Branche la passerelle (appelee seulement si Mekanism est la). */
    public static void init() {
        PioneersAPI.setForeignGases(MekanismGasBridge::foreign);
        PioneersAPI.setForeignGasList(() -> {
            List<Gas> list = new ArrayList<>();
            for (mekanism.api.chemical.gas.Gas gas : MekanismAPI.gasRegistry().getValues()) {
                if (!gas.isEmptyType() && !gas.isHidden()) {
                    list.add(toOurs(gas));
                }
            }
            return list;
        });
    }

    @Nullable
    private static Gas foreign(ResourceLocation id) {
        if (!MekanismAPI.gasRegistry().containsKey(id)) {
            return null;
        }
        mekanism.api.chemical.gas.Gas gas = MekanismAPI.gasRegistry().getValue(id);
        return gas == null || gas.isEmptyType() ? null : toOurs(gas);
    }

    public static Gas toOurs(mekanism.api.chemical.gas.Gas gas) {
        if (gas.isEmptyType()) {
            return Gas.EMPTY;
        }
        ResourceLocation id = gas.getRegistryName();
        Gas ours = PioneersAPI.gasRegistry().containsKey(id) ? PioneersAPI.gasRegistry().getValue(id) : null;
        if (ours != null && !ours.isEmptyType()) {
            return ours;
        }
        return FOREIGN.computeIfAbsent(id, key -> Gas.foreign(key, gas.getTint(), gas.getTranslationKey(), gas.getIcon(), gas.isHidden()));
    }

    @Nullable
    public static mekanism.api.chemical.gas.Gas toMekanism(Gas gas) {
        ResourceLocation id = gas.getRegistryName();
        if (id == null || !MekanismAPI.gasRegistry().containsKey(id)) {
            return null;
        }
        mekanism.api.chemical.gas.Gas mek = MekanismAPI.gasRegistry().getValue(id);
        return mek == null || mek.isEmptyType() ? null : mek;
    }

    public static GasStack toOurs(mekanism.api.chemical.gas.GasStack stack) {
        return stack.isEmpty() ? GasStack.EMPTY : GasStack.of(toOurs(stack.getType()), stack.getAmount());
    }

    public static mekanism.api.chemical.gas.GasStack toMekanism(GasStack stack) {
        mekanism.api.chemical.gas.Gas gas = stack.isEmpty() ? null : toMekanism(stack.getGas());
        return gas == null ? mekanism.api.chemical.gas.GasStack.EMPTY : new mekanism.api.chemical.gas.GasStack(gas, stack.getAmount());
    }

    private static mekanism.api.Action toMekanism(Action action) {
        return action.execute() ? mekanism.api.Action.EXECUTE : mekanism.api.Action.SIMULATE;
    }

    /** Les reservoirs de gaz Mekanism d'un bloc, vus comme une capacite Pioneer's (ou null). */
    @Nullable
    public static IGasHandler find(BlockEntity tile, @Nullable Direction side) {
        mekanism.api.chemical.gas.IGasHandler handler = tile.getCapability(Capabilities.GAS_HANDLER, side).resolve().orElse(null);
        return handler == null ? null : new View(handler);
    }

    /** Les reservoirs de gaz Mekanism d'un objet (reservoir chimique porte...), vus comme une capacite Pioneer's (ou null). */
    @Nullable
    public static IGasHandler find(ItemStack stack) {
        mekanism.api.chemical.gas.IGasHandler handler = stack.getCapability(Capabilities.GAS_HANDLER).resolve().orElse(null);
        return handler == null ? null : new View(handler);
    }

    /** Vue Pioneer's d'un gestionnaire de gaz Mekanism. */
    private record View(mekanism.api.chemical.gas.IGasHandler handler) implements IGasHandler {

        @Override
        public int getGasTanks() {
            return handler.getTanks();
        }

        @NotNull
        @Override
        public GasStack getGasInTank(int tank) {
            return toOurs(handler.getChemicalInTank(tank));
        }

        @Override
        public long getGasTankCapacity(int tank) {
            return handler.getTankCapacity(tank);
        }

        @Override
        public boolean isGasValid(int tank, @NotNull GasStack stack) {
            mekanism.api.chemical.gas.GasStack mek = toMekanism(stack);
            return !mek.isEmpty() && handler.isValid(tank, mek);
        }

        @NotNull
        @Override
        public GasStack insertGas(@NotNull GasStack stack, @NotNull Action action) {
            mekanism.api.chemical.gas.GasStack mek = toMekanism(stack);
            if (mek.isEmpty()) {
                return stack;
            }
            return toOurs(handler.insertChemical(mek, toMekanism(action)));
        }

        @NotNull
        @Override
        public GasStack extractGas(long amount, @NotNull Action action) {
            return toOurs(handler.extractChemical(amount, toMekanism(action)));
        }

        @NotNull
        @Override
        public GasStack extractGas(@NotNull GasStack stack, @NotNull Action action) {
            mekanism.api.chemical.gas.GasStack mek = toMekanism(stack);
            return mek.isEmpty() ? GasStack.EMPTY : toOurs(handler.extractChemical(mek, toMekanism(action)));
        }
    }

    /** La capacite est-elle celle des gaz de Mekanism ? */
    public static boolean isMekanismGasCapability(Capability<?> cap) {
        return cap == Capabilities.GAS_HANDLER;
    }

    /**
     * Nos reservoirs vus comme un gestionnaire de gaz Mekanism (pour que les tuyaux Mekanism s'y branchent), limites par le reglage de la
     * face : une face d'entree n'accepte que l'insertion, une face de sortie que l'extraction.
     */
    public static Object expose(List<? extends IGasTank> tanks, SideMode mode) {
        return new Exposed(tanks, mode);
    }

    private record Exposed(List<? extends IGasTank> tanks, SideMode mode)
          implements mekanism.api.chemical.gas.IGasHandler {

        @Override
        public int getTanks() {
            return tanks.size();
        }

        @NotNull
        @Override
        public mekanism.api.chemical.gas.GasStack getChemicalInTank(int tank) {
            return tank < 0 || tank >= tanks.size() ? mekanism.api.chemical.gas.GasStack.EMPTY : toMekanism(tanks.get(tank).getStack());
        }

        @Override
        public void setChemicalInTank(int tank, @NotNull mekanism.api.chemical.gas.GasStack stack) {
            if (tank >= 0 && tank < tanks.size()) {
                tanks.get(tank).setStack(toOurs(stack));
            }
        }

        @Override
        public long getTankCapacity(int tank) {
            return tank < 0 || tank >= tanks.size() ? 0 : tanks.get(tank).getCapacity();
        }

        @Override
        public boolean isValid(int tank, @NotNull mekanism.api.chemical.gas.GasStack stack) {
            return tank >= 0 && tank < tanks.size() && tanks.get(tank).isValid(toOurs(stack));
        }

        @NotNull
        @Override
        public mekanism.api.chemical.gas.GasStack insertChemical(int tank, @NotNull mekanism.api.chemical.gas.GasStack stack,
              @NotNull mekanism.api.Action action) {
            if (tank < 0 || tank >= tanks.size() || !mode.canInsert() || stack.isEmpty()) {
                return stack;
            }
            GasStack rest = tanks.get(tank).insert(toOurs(stack), action.execute() ? Action.EXECUTE : Action.SIMULATE,
                  Automation.EXTERNAL);
            return rest.isEmpty() ? mekanism.api.chemical.gas.GasStack.EMPTY : new mekanism.api.chemical.gas.GasStack(stack.getType(), rest.getAmount());
        }

        @NotNull
        @Override
        public mekanism.api.chemical.gas.GasStack extractChemical(int tank, long amount, @NotNull mekanism.api.Action action) {
            if (tank < 0 || tank >= tanks.size() || !mode.canExtract()) {
                return mekanism.api.chemical.gas.GasStack.EMPTY;
            }
            return toMekanism(tanks.get(tank).extract(amount, action.execute() ? Action.EXECUTE : Action.SIMULATE,
                  Automation.EXTERNAL));
        }
    }
}

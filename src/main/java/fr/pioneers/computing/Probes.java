package fr.pioneers.computing;

import fr.pioneers.compute.ComputeCore;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.industry.multi.ControllerTile;
import fr.pioneers.nuclear.ReactorControl;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * Sondes de l'informatique industrielle (0.17.0) : ce qu'un automate ou un ecran de supervision lit sur une machine liee, et les commandes
 * qu'il peut lui envoyer. Multiblocs industriels (statut, temperature, energie, progression, reservoirs ; reacteur : puissance,
 * temperature, barres, combustible), machines du socle, automates, puis capacites Forge generiques (energie, fluides, objets).
 * Commandes : seulement les reacteurs ({@link ReactorControl}) : barres, arret d'urgence, rearmement, arret automatique.
 */
public final class Probes {

    /** Commande possible sur une machine : cle, bornes de la valeur (min = max : sans valeur). */
    public record Command(String key, int min, int max) {

        public Component name() {
            return Component.translatable("computing.pioneers.command." + key);
        }

        public boolean hasValue() {
            return max > min;
        }
    }

    public static final List<Command> REACTOR_COMMANDS = List.of(new Command("rods", 0, 100), new Command("scram", 0, 0), new Command("reset", 0, 0),
          new Command("auto_scram", 0, 1));

    private Probes() {
    }

    /** Machine sondable a cette position (bloc charge avec une tile). */
    @Nullable
    public static BlockEntity at(@Nullable Level level, BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos);
    }

    /** Nom affiche de la machine. */
    public static Component name(@Nullable BlockEntity tile) {
        if (tile == null) {
            return Component.translatable("computing.pioneers.link.missing");
        }
        if (tile instanceof MenuProvider provider) {
            return provider.getDisplayName();
        }
        return tile.getBlockState().getBlock().getName();
    }

    /** Grandeurs lues (cote serveur). */
    public static List<ProbeValue> read(@Nullable BlockEntity tile) {
        List<ProbeValue> out = new ArrayList<>();
        if (tile == null) {
            return out;
        }
        Level level = tile.getLevel();
        if (tile instanceof ControllerTile c) {
            ReactorControl reactor = c.reactor();
            out.add(new ProbeValue("status", "status", -1, c.guiValue(4), "", 0, "industry.pioneers.multi.status."));
            double nominal = c.guiValue(4 + 9);
            out.add(ProbeValue.of("temperature", reactor != null ? reactor.temperature() : c.guiValue(4 + 8) / 10.0, "K", nominal * 1.25));
            if (reactor != null) {
                out.add(ProbeValue.of("power", reactor.powerFraction() * 100, "%", 100));
                out.add(ProbeValue.of("thermal_power", reactor.thermalPower() * 20 / 1e6, "MW", 0));
                out.add(ProbeValue.of("rods", reactor.rods(), "%", 100));
                out.add(ProbeValue.of("fuel", reactor.fuelLoaded(), "", Math.max(1, reactor.fuelCapacity())));
                out.add(ProbeValue.of("burnup", reactor.burnup() * 100, "%", 100));
                out.add(ProbeValue.of("scram_temperature", reactor.scramTemperature(), "K", 0));
                out.add(new ProbeValue("scrammed", "scrammed", -1, reactor.scrammed() ? 1 : 0, "", 0, "computing.pioneers.bool."));
                out.add(new ProbeValue("auto_scram", "auto_scram", -1, reactor.autoScram() ? 1 : 0, "", 0, "computing.pioneers.bool."));
            } else if (c.computeCore() != null) {
                //0.17.2 : centre de donnees, ordinateur quantique
                ComputeCore cc = c.computeCore();
                String unit = cc.quantum ? "QOPS" : "GOPS";
                out.add(ProbeValue.of("compute_served", cc.served(), unit, Math.max(1, cc.fullCapacity())));
                out.add(ProbeValue.of("compute_capacity", cc.capacity(), unit, Math.max(1, cc.fullCapacity())));
                out.add(ProbeValue.of("compute_load", cc.load() * 100, "%", 100));
                out.add(ProbeValue.of("energy_use", cc.power(), "FE/t", 0));
            } else {
                out.add(ProbeValue.of("progress", c.guiValue(4 + 2) / 10.0, "%", 100));
            }
            machine(c, out);
            tanks(c.coolantTank(), "coolant", 0, out);
            tanks(c.coolantOutTank(), "coolant_out", 0, out);
            //1.0 (0.19.12) : la vapeur du caloporteur eau est un gaz
            BasicGasTank steam = c.steamOutTank();
            out.add(new ProbeValue("steam_0", "steam", -1, steam.getCapacity() <= 0 ? 0 : 100.0 * steam.getStack().getAmount() / steam.getCapacity(), "%", 100, ""));
            List<FluidTank> in = c.fluidInTanks(), outTanks = c.fluidOutTanks();
            for (int i = 0; i < in.size(); i++) {
                tanks(in.get(i), "fluid_in", i, out);
            }
            for (int i = 0; i < outTanks.size(); i++) {
                tanks(outTanks.get(i), "fluid_out", i, out);
            }
        } else if (tile instanceof LogicTile logic) {
            logic.probe(out);
        } else if (tile instanceof ComputerTile computer) {
            computer.probe(out);
        } else if (tile instanceof MachineTile m) {
            out.add(new ProbeValue("active", "active", -1, m.getActive() ? 1 : 0, "", 0, "computing.pioneers.bool."));
            machine(m, out);
            List<FluidTank> fluids = m.getFluidTanks();
            for (int i = 0; i < fluids.size(); i++) {
                tanks(fluids.get(i), "fluid", i, out);
            }
            List<BasicGasTank> gases = m.getGasTanks();
            for (int i = 0; i < gases.size(); i++) {
                BasicGasTank g = gases.get(i);
                out.add(new ProbeValue("gas_" + i, "gas", i, g.getCapacity() <= 0 ? 0 : 100.0 * g.getStack().getAmount() / g.getCapacity(), "%", 100, ""));
            }
        } else {
            generic(tile, out);
        }
        if (level != null) {
            out.add(ProbeValue.of("redstone", level.getBestNeighborSignal(tile.getBlockPos()), "", 15));
        }
        return out;
    }

    private static void machine(MachineTile m, List<ProbeValue> out) {
        long cap = m.getEnergy().getCapacity();
        if (cap > 0) {
            out.add(ProbeValue.of("energy", 100.0 * m.getEnergy().getStored() / cap, "%", 100));
            out.add(ProbeValue.of("energy_stored", m.getEnergy().getStored(), "FE", 0));
        }
    }

    private static void tanks(@Nullable FluidTank tank, String label, int index, List<ProbeValue> out) {
        if (tank == null || tank.getCapacity() <= 0) {
            return;
        }
        out.add(new ProbeValue(label + "_" + index, label, label.startsWith("coolant") ? -1 : index, 100.0 * tank.getFluidAmount() / tank.getCapacity(),
              "%", 100, ""));
    }

    /** Capacites Forge : energie, fluides, objets. */
    private static void generic(BlockEntity tile, List<ProbeValue> out) {
        tile.getCapability(ForgeCapabilities.ENERGY, null).ifPresent(e -> {
            if (e.getMaxEnergyStored() > 0) {
                out.add(ProbeValue.of("energy", 100.0 * e.getEnergyStored() / e.getMaxEnergyStored(), "%", 100));
                out.add(ProbeValue.of("energy_stored", e.getEnergyStored(), "FE", 0));
            }
        });
        tile.getCapability(ForgeCapabilities.FLUID_HANDLER, null).ifPresent(f -> {
            long amount = 0, capacity = 0;
            for (int i = 0; i < f.getTanks(); i++) {
                amount += f.getFluidInTank(i).getAmount();
                capacity += f.getTankCapacity(i);
            }
            if (capacity > 0) {
                out.add(ProbeValue.of("fluid_total", 100.0 * amount / capacity, "%", 100));
            }
        });
        tile.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(h -> {
            long count = 0, room = 0;
            for (int i = 0; i < h.getSlots(); i++) {
                count += h.getStackInSlot(i).getCount();
                room += Math.min(h.getSlotLimit(i), h.getStackInSlot(i).isEmpty() ? 64 : h.getStackInSlot(i).getMaxStackSize());
            }
            if (room > 0) {
                out.add(ProbeValue.of("items", 100.0 * count / room, "%", 100));
                out.add(ProbeValue.of("item_count", count, "", 0));
            }
        });
    }

    /** Commandes acceptees par la machine. */
    public static List<Command> commands(@Nullable BlockEntity tile) {
        if (tile instanceof ControllerTile c && c.reactor() != null) {
            return REACTOR_COMMANDS;
        }
        return List.of();
    }

    /** Envoie une commande ; vrai si elle a ete prise. */
    public static boolean command(@Nullable BlockEntity tile, String key, int value) {
        if (!(tile instanceof ControllerTile c)) {
            return false;
        }
        ReactorControl reactor = c.reactor();
        if (reactor == null) {
            return false;
        }
        switch (key) {
            case "rods" -> {
                if (reactor.rods() != value) {
                    reactor.setRods(Math.max(0, Math.min(100, value)));
                }
            }
            case "scram" -> {
                if (!reactor.scrammed()) {
                    reactor.scram();
                }
            }
            case "reset" -> {
                if (reactor.scrammed()) {
                    reactor.resetScram();
                }
            }
            case "auto_scram" -> {
                if (reactor.autoScram() != (value != 0)) {
                    reactor.setAutoScram(value != 0);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}

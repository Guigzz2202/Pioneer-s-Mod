package fr.pioneers.energy;

import fr.pioneers.api.machine.unit.RenewableUnit;
import fr.pioneers.core.Directions;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * 0.20.45 : centrale geothermique. De l'eau injectee dans la roche chaude revient en vapeur et fait tourner sa turbine : plus elle est
 * posee profond, plus elle rend (rien au-dessus de y 48, plein a y -60) ; dans le Nether, toujours au mieux. Chaque bloc de lave (ou de
 * magma) qui la touche ajoute 10 % (30 % au plus). Elle consomme {@link EnergyKind#GEO_WATER} mB d'eau par tick a pleine puissance
 * (l'eau entre par toutes les faces) ; sans assez d'eau, la production suit l'eau disponible.
 * <p>
 * Interface : 8 profondeur x1000, 9 eau (mB), 10 reservoir (mB), 11 lave voisine (blocs).
 * <p>
 * API Machine (lot 3) : logique ({@link fr.pioneers.api.machine.unit.RenewableUnit}), code repris de l'ancienne {@code GeothermalPlantTile}.
 */
public class GeothermalLogic extends RenewableUnit {

    private final FluidTank water;
    private double depth;
    private int lava;

    public GeothermalLogic(MachineTile.Access machine, int tier) {
        super(machine, tier, EnergyKind.GEOTHERMAL);
        water = machine.fluidTank(new FluidTank(EnergyKind.GEO_TANK[tier], f -> f.getFluid().isSame(Fluids.WATER)) {
            @Override
            protected void onContentsChanged() {
                machine.changed();
            }
        });
        for (RelativeSide side : RelativeSide.values()) {
            machine.sides().set(SideType.FLUIDS, side, SideMode.INPUT);
        }
    }

    @Override
    protected double survey(ServerLevel level) {
        if (level.dimension() == Level.NETHER) {
            depth = 1;
        } else {
            depth = clamp((48 - pos().getY()) / 108.0, 0, 1);
        }
        lava = 0;
        for (Direction d : Directions.ALL) {
            BlockPos at = pos().relative(d);
            if (level.getFluidState(at).is(FluidTags.LAVA) || level.getBlockState(at).is(Blocks.MAGMA_BLOCK)) {
                lava++;
            }
        }
        return depth * (1 + 0.1 * Math.min(3, lava));
    }

    @Override
    protected long produce(ServerLevel level) {
        if (factor <= 0) {
            return 0;
        }
        //Eau voulue a ce regime (au moins 1 mB) ; la production suit l'eau reellement prise
        int want = Math.max(1, (int) Math.ceil(EnergyKind.GEO_WATER[tier] * Math.min(1, factor)));
        FluidStack taken = water.drain(want, IFluidHandler.FluidAction.EXECUTE);
        if (taken.isEmpty()) {
            return 0;
        }
        return Math.round(peak() * factor * taken.getAmount() / (double) want);
    }

    @Override
    protected long detail(int index) {
        return switch (index) {
            case 0 -> Math.round(depth * 1000);
            case 1 -> water.getFluidAmount();
            case 2 -> water.getCapacity();
            default -> lava;
        };
    }
}

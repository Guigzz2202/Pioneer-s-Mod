package fr.pioneers.tech.kinetics.sources;

import fr.pioneers.api.machine.StandardLayout.Part;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.tech.kinetics.KineticMachineTile;
import fr.pioneers.tech.kinetics.PiKinetics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Chaudiere et piston. API Machine (lot 8b) : la tile ne fait plus que PORTER son unite ({@link BeamEngineUnit} : foyer, chaudiere, eau,
 * seaux) par l'adaptateur cinetique {@link KineticMachineTile} ; elle reste la SOURCE du reseau de rotation (couple de demarrage = 20 000 N.m x
 * pression, 24 tr/min a vide, arbre par les flancs) et garde ses constantes. Type de tile {@code beam_engine}, cles de sauvegarde et ordre des
 * cases inchanges ; interface v2 (vitesse a la place de l'energie motrice, thermometre et niveau d'eau, foyer, seaux, carte, ecran).
 */
public class BeamEngineTile extends KineticMachineTile {

    //1.12.4 (lot F4, audit de l'energie) : foyer 600 -> 1 400, couple 9 000 -> 20 000 N.m, 14 -> 24 tr/min a vide : ~12,6 kW au mieux (avant
    //~3,3 kW, 11 fois moins que la machine a vapeur pour plus cher) ; sa niche : tout en un (foyer, chaudiere, piston), sans chaudiere a batir
    public static final double CAPACITY = 8_000, FIRE = 1_400, LOSS = 4, BOIL = 373, FULL = 403, MAX_T = 560, TAU0 = 20_000, FREE = 24;
    /** Interface v2 : thermometre et niveau d'eau a droite de la vitesse, foyer, carte, seau d'eau et seau vide contre le bord droit. */
    public static final Rect THERMO = StandardLayout.ageGauge(0), WATER = StandardLayout.ageGauge(1), CARD = StandardLayout.ageCard(86, 76);
    public static final int FUEL_X = 64, FUEL_Y = StandardLayout.TOP + 28, BUCKET_X = 171, BUCKET_Y = StandardLayout.TOP + 4, EMPTY_X = 171,
          EMPTY_Y = StandardLayout.TOP + 40;

    private final BeamEngineUnit unit;
    private final LazyOptional<IFluidHandler> waterCap;

    public BeamEngineTile(BlockPos pos, BlockState state) {
        super(PiKinetics.BEAM_ENGINE_TILE.get(), pos, state);
        unit = carry(new BeamEngineUnit(host));
        waterCap = LazyOptional.of(unit::water);
    }

    public BeamEngineUnit engine() {
        return unit;
    }

    public Direction facing() {
        return getBlockState().getValue(BeamEngineBlock.FACING);
    }

    public double pressure() {
        return unit.pressure();
    }

    public double temperature() {
        return unit.temperature();
    }

    @Override
    public double shaftFactor(Direction face) {
        return face.getAxis() == facing().getClockWise().getAxis() ? 1 : 0;
    }

    @Override
    public double sourceTorque() {
        return unit.sourceTorque();
    }

    @Override
    public double sourceFreeSpeed() {
        return FREE;
    }

    @Override
    public double inertia() {
        return 150;
    }

    @Override
    public double maxTorque() {
        return 20_000;
    }

    @Override
    public double maxSpeed() {
        return 60;
    }

    @Override
    protected double renderRadius() {
        return 1.5;
    }

    /** Une source sans donnees propres pour le client (comme avant : la rotation seule part dans le paquet du reseau). */
    @Override
    protected boolean ownClientData() {
        return false;
    }

    List<ItemStack> drops() {
        List<ItemStack> list = new ArrayList<>();
        for (var slot : unit.slots()) {
            if (!slot.isEmpty()) {
                list.add(slot.getStack().copy());
            }
        }
        return list;
    }

    @Override
    public List<Part> guiParts() {
        return StandardLayout.ageService(false, StandardLayout.framed("gauge", THERMO), StandardLayout.framed("gauge", WATER),
              new Part("card", CARD));
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return waterCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        waterCap.invalidate();
    }
}

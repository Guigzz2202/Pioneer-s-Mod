package fr.pioneers.tech.industry.atelier;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.logistics.transport.CableTier;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.tech.kinetics.AxisKineticBlock;
import fr.pioneers.tech.kinetics.KineticMachineTile;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.6 (Guillaume, 17/11/2026) : MOTEUR ELECTRIQUE MKI a MKIV : l'inverse de la dynamo. Il prend l'energie par ses quatre faces qui ne
 * portent pas l'arbre (reglables, onglet Faces) et fait tourner son arbre (axe du bloc) a la vitesse reglee, avec un couple qui croit avec le
 * tier ({@link #RATED_TORQUE}). Unite : {@link MotorUnit} ; meme bloc que la dynamo ({@link DynamoBlock} : pose orientee, facade = boite a
 * bornes tournee vers le cable). Interface standard : reserve et batterie a gauche, reglage de la vitesse et du sens, rotation en sortie.
 */
public class MotorTile extends KineticMachineTile {

    /** Couple de demarrage par tier (N.m) : le meme que celui que la dynamo du tier encaisse. */
    public static final double[] RATED_TORQUE = {8_000, 32_000, 128_000, 512_000};
    /** Vitesse reglable par tier (tr/min, au plus). */
    public static final int[] MAX_SPEED = {256, 384, 512, 768};
    /** Energie prise au plus par tick (FE/t) : celle que la dynamo du tier rend. */
    public static final long[] RATED_FE = {2_048, 8_192, 32_768, 131_072};
    /** Rendement electrique -> mecanique (le moteur et la dynamo ensemble perdent toujours : pas de mouvement perpetuel). */
    public static final double[] EFFICIENCY = {0.80, 0.85, 0.90, 0.95};
    public static final int RESERVE_TICKS = 200;
    /** Actions de l'interface : vitesse +1 / -1 / +10 / -10, sens. */
    public static final int FASTER = CUSTOM, SLOWER = CUSTOM + 1, FASTER_10 = CUSTOM + 2, SLOWER_10 = CUSTOM + 3, REVERSE = CUSTOM + 4;
    public static final UiValues.Key DIRECTION = UiValues.Key.of("direction");

    private final CableTier tier;
    private final MotorUnit unit;
    private final LazyOptional<IEnergyStorage> cap;

    public MotorTile(BlockPos pos, BlockState state) {
        super(PiIndustry.MOTOR_TILE.get(), pos, state);
        tier = state.getBlock() instanceof DynamoBlock d ? d.tier : CableTier.BASIC;
        enableSides(SideType.ENERGY, SideMode.INPUT, SideMode.INPUT);
        enableRedstone();
        unit = carry(new MotorUnit(host, this, tier));
        cap = LazyOptional.of(() -> unit.energy);
    }

    public MotorUnit motor() {
        return unit;
    }

    Direction.Axis axis() {
        return AxisKineticBlock.axis(getBlockState());
    }

    boolean jammed() {
        return jammed;
    }

    @Override
    public double shaftFactor(Direction face) {
        return face.getAxis() == axis() ? 1 : 0;
    }

    // ------------------------------------------------------------------ source du reseau

    @Override
    public double sourceTorque() {
        return unit.sourceTorque();
    }

    @Override
    public double sourceFreeSpeed() {
        return unit.sourceFreeSpeed();
    }

    @Override
    public void sourceDelivered(double torque) {
        unit.delivered(torque);
    }

    @Override
    public double maxTorque() {
        return RATED_TORQUE[tier.ordinal()] * 1.5;
    }

    @Override
    public double maxSpeed() {
        return MAX_SPEED[tier.ordinal()] * 1.5;
    }

    @Override
    protected boolean ownClientData() {
        return false;
    }

    @Override
    public void describe(List<Component> lines) {
        super.describe(lines);
        lines.add(Component.translatable("industry.pioneers.motor.target", unit.target()));
        lines.add(Component.translatable("industry.pioneers.dynamo.stored", unit.energy.getStored(), unit.energy.getCapacity()));
    }

    // ------------------------------------------------------------------ interface standard

    @Override
    public String guiTier() {
        return tier.id();
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return MotorUnit.parts();
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        switch (action) {
            case FASTER -> unit.adjust(1);
            case SLOWER -> unit.adjust(-1);
            case FASTER_10 -> unit.adjust(10);
            case SLOWER_10 -> unit.adjust(-10);
            case REVERSE -> unit.reverse();
            default -> {
            }
        }
    }

    /** Lignes d'infobulle de l'objet (bornes du tier). */
    public static void specs(CableTier tier, Tooltips tips) {
        int t = tier.ordinal();
        tips.spec("industry.pioneers.motor.spec_torque", fr.pioneers.core.Texts.grouped(Math.round(RATED_TORQUE[t])), MAX_SPEED[t]);
        tips.spec("industry.pioneers.motor.spec_power", fr.pioneers.core.Texts.grouped(RATED_FE[t]),
              Component.translatable("card.pioneers.percent", Math.round(EFFICIENCY[t] * 100)));
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> wanted, @Nullable Direction side) {
        if (wanted == ForgeCapabilities.ENERGY && (side == null || side.getAxis() != axis() && sideOpen(SideType.ENERGY, side))) {
            return cap.cast();
        }
        return super.getCapability(wanted, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        cap.invalidate();
    }
}

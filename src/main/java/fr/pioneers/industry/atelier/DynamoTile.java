package fr.pioneers.industry.atelier;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.kinetics.AxisKineticBlock;
import fr.pioneers.kinetics.KineticMachineTile;
import fr.pioneers.kinetics.KineticNode;
import fr.pioneers.registries.PiIndustry;
import fr.pioneers.transport.CableTier;
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
 * Dynamo (4 tiers). 1.12.4 (lot F4, Guillaume : « hard buff le dynamo et lui donner une interface claire ») :
 * <ul>
 *   <li><b>1 J de travail mecanique = 1 FE</b> ({@link #FE_PER_JOULE} ; avant : 2,5 J par FE) ;</li>
 *   <li><b>excitation adaptee</b> : la dynamo n'est plus une charge a couple fixe (3 000 N.m, qui calait les petites roues et laissait filer la
 *   machine a vapeur) mais un frottement visqueux que le reseau regle a chaque tick pour tirer le maximum des sources (voir
 *   {@link KineticNode#generatorRating()}) : une machine a vapeur a 8 bar donne 37,7 kW en prise directe (60 tr/min), soit ~1 885 FE/t ;</li>
 *   <li>bornes du tier : puissance {@link #RATED_FE} (2 048 FE/t au MKI, x4 par tier), couple {@link #RATED_TORQUE} (un moulin lent se
 *   multiplie par une boite de vitesses), vitesse {@link #MAX_SPEED} ;</li>
 *   <li>excitation reglable dans l'interface (auto, 75, 50, 25 %, coupee) pour laisser de la puissance aux machines du meme reseau.</li>
 * </ul>
 * L'energie part par les quatre faces qui ne portent pas l'arbre (la facade, ou boite a bornes, est tournee vers le cable a la pose).
 * <p>
 * API Machine (lot 7) : la tile ne fait plus que PORTER son unite de generateur ({@link DynamoUnit}) par l'adaptateur cinetique
 * {@link KineticMachineTile} ; elle reste le noeud du reseau (axe, bornes, excitation lue par le reseau). Type de tile
 * {@code industry_dynamo}, cles {@code energy} et {@code excitation} inchangees ; interface standard v2.
 */
public class DynamoTile extends KineticMachineTile {

    /** FE produits par joule de travail mecanique (1.12.4 : 1 ; avant 1 / 2,5). */
    public static final double FE_PER_JOULE = 1.0;
    /** Puissance electrique nominale par tier (FE/t). */
    public static final long[] RATED_FE = {2_048, 8_192, 32_768, 131_072};
    /** Couple nominal par tier (N.m) : le reseau ne demande jamais plus a la dynamo ; casse a x1,5. */
    public static final double[] RATED_TORQUE = {8_000, 32_000, 128_000, 512_000};
    /** Vitesse maximale par tier (tr/min) : au-dessus, survitesse (vibrations, casse a x1,5). */
    public static final double[] MAX_SPEED = {400, 600, 800, 1_000};
    /** Excitation : part de la charge adaptee (auto = 100 %). */
    public static final double[] EXCITATION = {1, 0.75, 0.5, 0.25, 0};
    /** Reserve : 200 ticks de production nominale. */
    public static final int RESERVE_TICKS = 200;
    /** Etats de l'interface. */
    public static final int STOPPED = 0, PRODUCING = 1, STALLED = 2, OVERSPEED = 3, NO_OUTPUT = 4, SATURATED = 5, TORQUE_LIMIT = 6, OFF = 7;
    /** Actions de l'interface : excitation suivante / precedente. */
    public static final int NEXT = CUSTOM, PREVIOUS = CUSTOM + 1;

    private final CableTier tier;
    private final DynamoUnit unit;
    private final LazyOptional<IEnergyStorage> cap;

    public DynamoTile(BlockPos pos, BlockState state) {
        super(PiIndustry.DYNAMO_TILE.get(), pos, state);
        tier = state.getBlock() instanceof DynamoBlock d ? d.tier : CableTier.BASIC;
        unit = carry(new DynamoUnit(host, this, tier));
        cap = LazyOptional.of(() -> unit.energy);
    }

    public DynamoUnit generator() {
        return unit;
    }

    private int t() {
        return tier.ordinal();
    }

    public long ratedFe() {
        return unit.ratedFe();
    }

    Direction.Axis axis() {
        return AxisKineticBlock.axis(getBlockState());
    }

    boolean jammed() {
        return jammed;
    }

    double networkCapacity() {
        return network == null ? 0 : network.capacity();
    }

    @Override
    public double shaftFactor(Direction face) {
        return face.getAxis() == axis() ? 1 : 0;
    }

    // ------------------------------------------------------------------
    // Reseau : generateur a excitation adaptee (chiffres de l'unite)
    // ------------------------------------------------------------------

    @Override
    public double generatorRating() {
        return unit.rating();
    }

    @Override
    public double generatorTorque() {
        return RATED_TORQUE[t()];
    }

    @Override
    public double generatorExcitation() {
        return unit.excitationShare();
    }

    @Override
    public void generatorDamping(double c) {
        unit.damping(c);
    }

    @Override
    public double loadTorque() {
        return 0;
    }

    @Override
    public double maxTorque() {
        return RATED_TORQUE[t()] * 1.5;
    }

    @Override
    public double maxSpeed() {
        return MAX_SPEED[t()];
    }

    /** Couple de la charge electrique (N.m). */
    public double electricTorque() {
        return unit.electricTorque();
    }

    /** Aucun voisin n'a pris d'energie depuis 2 s alors que la reserve en contient. */
    public boolean noConsumer() {
        return unit.noConsumer();
    }

    @Override
    protected boolean ownClientData() {
        return false;
    }

    @Override
    public void describe(List<Component> lines) {
        super.describe(lines);
        lines.add(Component.translatable("industry.pioneers.dynamo.output", Math.round(unit.producedNow())));
        lines.add(Component.translatable("industry.pioneers.dynamo.stored", unit.energy.getStored(), unit.energy.getCapacity()));
    }

    // ------------------------------------------------------------------
    // Interface standard v2 (valeurs nommees de l'unite, memes indices qu'avant)
    // ------------------------------------------------------------------

    @Override
    public String guiTier() {
        return tier.id();
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return DynamoUnit.parts();
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        if (action == NEXT || action == PREVIOUS) {
            unit.cycleExcitation(action == NEXT);
        }
    }

    /** Lignes d'infobulle de l'objet (bornes du tier). */
    public static void specs(CableTier tier, Tooltips tips) {
        int t = tier.ordinal();
        tips.spec("industry.pioneers.dynamo.spec_power", RATED_FE[t]);
        tips.spec("industry.pioneers.dynamo.spec_torque", Math.round(RATED_TORQUE[t]), Math.round(MAX_SPEED[t]));
        tips.spec("industry.pioneers.dynamo.spec_rate");
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> wanted, @Nullable Direction side) {
        if (wanted == ForgeCapabilities.ENERGY && (side == null || side.getAxis() != axis())) {
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

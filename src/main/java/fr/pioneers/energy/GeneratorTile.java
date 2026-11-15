package fr.pioneers.energy;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.RenewableUnit;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.machine.TieredMachineBlock;
import fr.pioneers.core.side.SideMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.45 : tile des generateurs renouvelables ({@link EnergyKind}). API Machine (lot 3) : elle ne fait plus que PORTER l'unite de
 * generateur ({@link RenewableUnit} : mesure de l'environnement toutes les {@link #SCAN} ticks, production, poussee aux voisins, valeurs
 * nommees, sauvegarde) et sa logique propre ({@link SolarLogic}, {@link WindLogic}, {@link HydroLogic}, {@link GeothermalLogic},
 * {@link MicrobialLogic}). Memes types de tiles, memes cles de sauvegarde ({@code factor}...), memes valeurs d'interface (indices 4..11 :
 * genre, production FE/t x100, production maximale, facteur x1000, mesures propres).
 */
public abstract class GeneratorTile extends MachineTile {

    public static final int SCAN = RenewableUnit.SCAN;

    protected final EnergyKind kind;
    protected final int tier;
    protected final RenewableUnit unit;

    protected GeneratorTile(BlockEntityType<?> type, BlockPos pos, BlockState state, EnergyKind kind, Function<Access, RenewableUnit> logic) {
        super(type, pos, state, kind.capacity(TieredMachineBlock.tierOf(state)), (int) Math.min(Integer.MAX_VALUE,
              kind.capacity(TieredMachineBlock.tierOf(state))));
        this.kind = kind;
        this.tier = TieredMachineBlock.tierOf(state);
        unit = logic.apply(access());
    }

    @Override
    protected boolean producesEnergy() {
        return true;
    }

    @Override
    protected Set<SideMode> allowedModes() {
        return EnumSet.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT);
    }

    public EnergyKind kind() {
        return kind;
    }

    /** L'unite de generateur (API Machine). */
    public RenewableUnit unit() {
        return unit;
    }

    public long peak() {
        return unit.peak();
    }

    @Override
    protected void tick() {
        unit.tick();
    }

    /** Production moyenne (FE/t). */
    public double output() {
        return unit.output();
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return StandardLayout.generatorParts(true, kind == EnergyKind.GEOTHERMAL ? 1 : 0, kind == EnergyKind.MICROBIAL);
    }

    @Override
    public String guiTier() {
        return EnergyKind.TIERS[tier];
    }

    @Override
    protected boolean namedValues() {
        return true;
    }

    @Override
    protected void declareValues(UiValues.Builder values) {
        unit.declareValues(values);
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        unit.writeClient(tag);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        unit.readClient(tag);
    }

    public float clientFactor() {
        return unit.clientFactor();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        unit.save(tag);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        unit.load(tag);
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

package fr.pioneers.space.tile;

import fr.pioneers.Pioneers;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.machine.TieredMachineBlock;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.registries.PiSpaceMachines;
import fr.pioneers.space.environment.OxygenRooms;
import fr.pioneers.space.environment.RoomScanner.Room;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Diffuseur d'oxygene (socle) : pressurise la piece close dans laquelle il se trouve.
 * <ul>
 *     <li>la piece est balayee quand elle change, au plus toutes les deux secondes (jusqu'a {@link #MAX_VOLUME} blocs d'air) ;</li>
 *     <li>pressuriser coute 2 mB d'oxygene par bloc d'air, puis l'entretien compense de petites fuites ;</li>
 *     <li>si la piece s'ouvre (porte classique, trou, sas ouvert), elle se depressurise aussitot.</li>
 * </ul>
 * Dans une piece pressurisee, on respire sans combinaison, et les reserves d'oxygene des combinaisons s'y remplissent.
 * <p>
 * 0.20.46 (audit) : la piece n'est plus rebalayee toutes les deux secondes (jusqu'a 50 000 lectures de blocs) mais quand un bloc qui la
 * touche change ({@link OxygenRooms} : mises a jour de blocs, portes, chunks charges ou decharges), toujours au meme creneau de deux
 * secondes, et par securite toutes les minutes.
 * <p>
 * Interface : 4 oxygene injecte (mB), 5 cible (mB), 6 volume de la piece, 7 piece close, 8 pressurisee.
 * <p>
 * API Machine (lot 8c) : logique dans {@link OxygenDiffuserUnit} (fiche {@code migrated}) ; cette tile la porte ({@link #carry}) et reste
 * inscrite au registre des pieces ({@code OxygenRooms}).
 */
public class TileEntityOxygenDiffuser extends MachineTile {

    /** Oxygene du socle ({@code pioneers:oxygen}). */
    public static final ResourceLocation OXYGEN = Pioneers.rl("oxygen");
    /** Oxygene de Mekanism (gaz etranger de la passerelle) : accepte aussi. */
    private static final ResourceLocation MEKANISM_OXYGEN = ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen");
    public static final int MAX_VOLUME = 8_192;
    /** 1.0 : volume de piece le plus grand par tier (MKI a MKIV) ; le debit de remplissage double a chaque tier. */
    public static final int[] TIER_VOLUME = {1_024, 2_048, 4_096, 8_192};
    public static final long TANK_CAPACITY = 32_000;
    public static final long OXYGEN_PER_BLOCK = 2;
    /** Debit de pressurisation (mB/t). */
    public static final long FILL_RATE = 64;
    public static final long ENERGY_BASE_FE = 100, ENERGY_STORAGE_FE = 400_000;
    public static final int BATTERY_X = StandardLayout.BATTERY_X, BATTERY_Y = StandardLayout.BATTERY_Y;

    public final BasicGasTank oxygenTank;
    /** API Machine (lot 8c) : la logique (piece, pressurisation, fuites) est dans l'unite portee. */
    private final OxygenDiffuserUnit unit;

    public TileEntityOxygenDiffuser(BlockPos pos, BlockState state) {
        super(PiSpaceMachines.OXYGEN_DIFFUSER_TILE.get(), pos, state, ENERGY_STORAGE_FE, (int) (getMaxEnergyUsage(TieredMachineBlock.tierOf(state)) * 4));
        unit = carry(new OxygenDiffuserUnit(access()));
        oxygenTank = unit.oxygenTank;
        sides.set(RelativeSide.FRONT, SideMode.NONE);
        sides.set(RelativeSide.TOP, SideMode.NONE);
    }

    public static boolean isOxygen(GasStack stack) {
        ResourceLocation id = stack.getGas().getRegistryName();
        return OXYGEN.equals(id) || MEKANISM_OXYGEN.equals(id);
    }

    @Override
    protected Set<SideMode> allowedModes() {
        return EnumSet.of(SideMode.NONE, SideMode.INPUT);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        OxygenRooms.register(this);
    }

    @Override
    public void setRemoved() {
        OxygenRooms.unregister(this);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        OxygenRooms.unregister(this);
        super.onChunkUnloaded();
    }

    @Override
    protected void tick() {
    }

    /** 0.20.46 (audit) : boite de la piece elargie de {@code margin}. */
    public boolean near(int x, int y, int z, int margin) {
        return unit.near(x, y, z, margin);
    }

    /** 0.20.46 (audit) : la boite de la piece (elargie d'un bloc) croise-t-elle ces colonnes ? */
    public boolean nearColumns(int x0, int z0, int x1, int z1) {
        return unit.nearColumns(x0, z0, x1, z1);
    }

    /** 0.20.46 (audit) : a rebalayer au prochain creneau (porte, chunk charge ou decharge). */
    public void markDirty() {
        unit.markDirty();
    }

    /** 0.20.46 (audit) : un bloc a change en {@code pos} (etat {@code now}) : voir {@link OxygenDiffuserUnit#blockChanged}. */
    public void blockChanged(BlockPos pos, BlockState now) {
        unit.blockChanged(pos, now);
    }

    public long getTarget() {
        return unit.getTarget();
    }

    /** La piece est close, alimentee et pressurisee a 95 % au moins. */
    public boolean isPressurized() {
        return unit.isPressurized();
    }

    public Room getRoom() {
        return unit.getRoom();
    }

    /** Remplissage de la reserve d'une combinaison depuis le diffuseur (mB effectivement fournis). */
    public long supplyOxygen(long amount) {
        return oxygenTank.extract(amount, Action.EXECUTE, Automation.INTERNAL).getAmount();
    }

    public long getEnergyUsage() {
        return unit.getEnergyUsage();
    }

    public static long getMaxEnergyUsage(int tier) {
        return ENERGY_BASE_FE + TIER_VOLUME[tier] / 8;
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
        return StandardLayout.diffuserParts();
    }

    /** API Machine (lot 4) : valeurs NOMMEES (lot 8c : declarees par l'unite, memes cles et indices). */
    public static final UiValues.Key OXYGEN_MB = OxygenDiffuserUnit.OXYGEN_MB, TARGET = OxygenDiffuserUnit.TARGET, VOLUME = OxygenDiffuserUnit.VOLUME,
          SEALED = OxygenDiffuserUnit.SEALED, PRESSURIZED = OxygenDiffuserUnit.PRESSURIZED;
}

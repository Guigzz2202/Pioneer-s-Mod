package fr.pioneers.space.tile;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataSideMode;
import fr.pioneers.api.data.IDataConfigurable;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.DataBankUnit;
import fr.pioneers.space.content.DataBankBlock;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.core.data.BasicDataTank;
import fr.pioneers.core.data.CreativeDataTank;
import fr.pioneers.core.registries.PiMachines;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Banque de donnees (socle) : un reservoir de donnees, faces reglables pour les donnees (canal {@link SideType#DATA}, facade verrouillee),
 * ejection automatique par les faces de SORTIE (une face « entree et sortie » se vide a la demande, sans ejection). Interface standard.
 * Les anciennes banques (Mekanism) sont reprises : reservoir {@code dataTank}, faces {@code dataSides}. Le contenu suit l'objet
 * ({@code mekData.dataTank}, table de butin) : voir {@link DataBankBlock}.
 * <p>
 * Interface : 4 contenu, 5 capacite, 6 type (id du registre, -1 vide), 7 debit d'ejection (Mo/t).
 * <p>
 * API Machine (lot 4) : la logique est dans {@link DataBankUnit} (stockage DATA de la couche ressource) ; cette tile la porte (meme type,
 * memes cles, memes methodes pour le rendu et la table de butin). Interface standard v2 (vue {@code data_bank} de la fiche).
 */
public class TileEntityDataBank extends MachineTile implements IDataConfigurable {

    public enum Tier {
        BASIC(1_000_000L, 2_000, 0x55FF55),
        ADVANCED(4_000_000L, 8_000, 0xAA0000),
        ELITE(16_000_000L, 32_000, 0x00AAAA),
        ULTIMATE(64_000_000L, 128_000, 0xAA00AA),
        /** Creative : une source infinie d'un type de donnees (et un puits sans fond pour ce type). */
        CREATIVE(CreativeDataTank.INFINITE, 1_000_000_000L, 0xB040FF);

        public final long capacity;
        /** Ejection par tick (Mo) sur chaque face en sortie. */
        public final long rate;
        /** Couleur du tier (RGB). */
        public final int color;

        Tier(long capacity, long rate, int color) {
            this.capacity = capacity;
            this.rate = rate;
            this.color = color;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT) + "_data_bank";
        }

        public boolean isCreative() {
            return this == CREATIVE;
        }
    }

    private final Tier tier;
    /** API Machine (lot 4) : la logique de la banque (stockage DATA, faces, ejection, sauvegarde, client). */
    private final DataBankUnit unit;
    private int clientLevel = -1;
    private int clientColor;

    public TileEntityDataBank(BlockPos pos, BlockState state, Tier tier) {
        super(PiMachines.DATA_BANK_TILES.get(tier).get(), pos, state, 0, 0);
        this.tier = tier;
        BasicDataTank tank = tier.isCreative() ? new CreativeDataTank(this::tankChanged) : BasicDataTank.create(tier.capacity, this::tankChanged);
        unit = new DataBankUnit(access(), tank, tier.rate);
        sides.addType(SideType.DATA).ejects(SideType.DATA);
        sides.lock(RelativeSide.FRONT);
        sides.setEject(true);
    }

    private void tankChanged() {
        setChanged();
        markSync();
    }

    @Override
    protected boolean canEject() {
        return true;
    }

    @Override
    protected SideType[] ejectTypes() {
        return new SideType[]{SideType.DATA};
    }

    @Override
    public String guiTier() {
        return tier.name().toLowerCase(Locale.ROOT);
    }

    public Tier getTier() {
        return tier;
    }

    public DataBankUnit unit() {
        return unit;
    }

    public BasicDataTank getTank() {
        return unit.tank();
    }

    /** Remplissage (0..1), pour la jauge en facade. */
    public float getFill() {
        return (float) unit.store.fill();
    }

    @Override
    protected void tick() {
        unit.tick();
    }

    // ------------------------------------------------------------------
    // Faces de donnees (cables optiques, configurateur, apercu)
    // ------------------------------------------------------------------

    private static DataSideMode toData(SideMode mode) {
        return switch (mode) {
            case NONE -> DataSideMode.NONE;
            case INPUT -> DataSideMode.INPUT;
            case OUTPUT -> DataSideMode.OUTPUT;
            case IO -> DataSideMode.INPUT_OUTPUT;
        };
    }

    private static SideMode fromData(DataSideMode mode) {
        return switch (mode) {
            case NONE -> SideMode.NONE;
            case INPUT -> SideMode.INPUT;
            case OUTPUT -> SideMode.OUTPUT;
            case INPUT_OUTPUT -> SideMode.IO;
        };
    }

    @Override
    public DataSideMode getDataMode(Direction side) {
        return toData(sides.mode(SideType.DATA, side));
    }

    @Override
    public boolean setDataMode(Direction side, DataSideMode mode) {
        RelativeSide relative = RelativeSide.fromDirections(facing(), side);
        if (sides.isLocked(relative) || getDataMode(side) == mode) {
            return false;
        }
        sides.set(SideType.DATA, relative, fromData(mode));
        return getDataMode(side) == mode;
    }

    @Override
    public List<DataSideMode> getSupportedDataModes() {
        return List.of(DataSideMode.NONE, DataSideMode.INPUT, DataSideMode.OUTPUT, DataSideMode.INPUT_OUTPUT);
    }

    @Override
    public boolean isDataSideConfigurable(Direction side) {
        return !sides.isLocked(RelativeSide.fromDirections(facing(), side));
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == PioneersAPI.DATA_HANDLER) {
            return unit.capability(side).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    protected void sidesChanged() {
        super.sidesChanged();
        unit.invalidate(false);
        markSync();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        unit.invalidate(true);
    }

    // ------------------------------------------------------------------
    // Interface (lot 4 : valeurs nommees, memes indices ; interface standard v2), sauvegarde, synchronisation
    // ------------------------------------------------------------------

    @Override
    protected boolean namedValues() {
        return true;
    }

    @Override
    protected void declareValues(UiValues.Builder values) {
        unit.declareValues(values);
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
        return StandardLayout.bufferParts(false);
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

    /** Contenu porte par l'objet de la banque ({@code mekData.dataTank}, ecrit par la table de butin). */
    public void loadFromItem(CompoundTag dataTank) {
        unit.tank().deserializeNBT(dataTank);
        tankChanged();
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
        clientLevel = Math.round(getFill() * 16);
        clientColor = getTank().isEmpty() ? 0 : getTank().getType().getColor();
    }

    public int getClientLevel() {
        return Math.max(0, clientLevel);
    }

    public int getClientColor() {
        return clientColor;
    }
}

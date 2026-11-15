package fr.pioneers.transport.legacy;

import fr.pioneers.core.block.PiBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 1.11 (D2c) : tile d'un cable herite ({@code pioneers:cable} et {@code pioneers:data_cable}) : garde l'ancien NBT tel quel (masques
 * {@code disabled}/{@code pulling}/{@code pushing}, parts {@code share}, corps {@code heat}/{@code pressure}) et se signale a la migration
 * au chargement. Aucun tick, aucune capacite.
 */
public class LegacyCableTile extends PiBlockEntity {

    private CompoundTag old = new CompoundTag();

    public LegacyCableTile(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            LegacyMigration.queue(level, worldPosition);
        }
    }

    public int disabled() {
        return old.getByte("disabled") & 63;
    }

    public int pulling() {
        return old.getByte("pulling") & 63;
    }

    public int pushing() {
        return old.getByte("pushing") & 63;
    }

    /** Parts du contenu interne (objets : piles a deposer au sol ; le reste est perdu). */
    public ListTag share() {
        return old.getList("share", Tag.TAG_COMPOUND);
    }

    /** Temperature du corps (K) d'un ancien cable de chaleur, NaN sinon. */
    public double temperature() {
        CompoundTag heat = old.getCompound("heat");
        return heat.contains("temperature") ? Math.max(0, heat.getDouble("temperature")) : Double.NaN;
    }

    /** Air (L) d'un ancien tuyau de pression, NaN sinon. */
    public double air() {
        CompoundTag pressure = old.getCompound("pressure");
        return pressure.contains("air") ? Math.max(0, pressure.getDouble("air")) : Double.NaN;
    }

    /** GameTest et banc : pose des reglages d'un ancien cable. */
    public void setLegacy(int disabled, int pulling, int pushing) {
        old.putByte("disabled", (byte) disabled);
        old.putByte("pulling", (byte) pulling);
        old.putByte("pushing", (byte) pushing);
        setChanged();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        for (String key : old.getAllKeys()) {
            if (!tag.contains(key)) {
                tag.put(key, old.get(key).copy());
            }
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        old = tag.copy();
    }
}

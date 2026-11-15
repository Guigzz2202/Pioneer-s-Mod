package fr.pioneers.space.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Gisement repere par le radar : une position en surface, un type de filon et ce qu'il en reste (en unites de forage).
 */
public final class Deposit {

    public final BlockPos pos;
    public final MiningVein vein;
    public final int total;
    public int remaining;
    /** Chemin barre par des blocs que le rover ne peut pas creuser. */
    public boolean blocked;

    public Deposit(BlockPos pos, MiningVein vein, int total, int remaining) {
        this.pos = pos;
        this.vein = vein;
        this.total = total;
        this.remaining = remaining;
    }

    public boolean isDepleted() {
        return remaining <= 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.asLong());
        tag.putInt("vein", vein.ordinal());
        tag.putInt("total", total);
        tag.putInt("left", remaining);
        tag.putBoolean("blocked", blocked);
        return tag;
    }

    public static Deposit load(CompoundTag tag) {
        Deposit deposit = new Deposit(BlockPos.of(tag.getLong("pos")), MiningVein.byIndex(tag.getInt("vein")), tag.getInt("total"), tag.getInt("left"));
        deposit.blocked = tag.getBoolean("blocked");
        return deposit;
    }
}

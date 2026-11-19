package fr.pioneers.tech.industry.multi;

import fr.pioneers.space.mega.MkvCores;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 1.0.7 (« Plus loin et au-dela », lot 5) : le COEUR MKV porte par le controleur d'un multibloc de famille ({@link MkvCores}). Le multibloc
 * passe au tier MKV (4) quand toute sa structure est MKV et que le coeur est celui de son domaine ; sinon il reste borne au MKIV. Un seul
 * coeur ; il tombe avec le controleur ({@link ControllerBlock#onRemove}). Hors de {@link ControllerTile} (cliquet de taille de la classe).
 */
public final class MkvCoreSlot {

    private final Runnable changed;
    private ItemStack core = ItemStack.EMPTY;

    MkvCoreSlot(Runnable changed) {
        this.changed = changed;
    }

    /** Tier du multibloc : celui de sa structure, borne au MKIV, ou MKV avec le coeur de son domaine. */
    int tier(int structureTier, String machineKey) {
        if (structureTier >= 4 && !core.isEmpty() && MkvCores.fits(core, machineKey)) {
            return 4;
        }
        return Math.max(0, Math.min(3, structureTier));
    }

    public ItemStack get() {
        return core;
    }

    /** Pose le coeur (un seul) ; faux si un coeur est deja la ou si la pile est vide. */
    public boolean install(ItemStack stack) {
        if (!core.isEmpty() || stack.isEmpty()) {
            return false;
        }
        core = stack.copyWithCount(1);
        changed.run();
        return true;
    }

    public ItemStack take() {
        ItemStack out = core;
        core = ItemStack.EMPTY;
        if (!out.isEmpty()) {
            changed.run();
        }
        return out;
    }

    void save(CompoundTag tag) {
        if (!core.isEmpty()) {
            tag.put("mkvCore", core.save(new CompoundTag()));
        }
    }

    void load(CompoundTag tag) {
        core = tag.contains("mkvCore") ? ItemStack.of(tag.getCompound("mkvCore")) : ItemStack.EMPTY;
    }

    /** Etat de l'ecran d'un controleur (UNFORMED, IDLE, WORKING..., MEGA_LOCKED, MKV_CASINGS) : GameTests et diagnostics. */
    public static int status(ControllerTile tile) {
        return tile.status;
    }
}

package fr.pioneers.tech.industry.multi;

import fr.pioneers.api.multiblock.MultiblockLogic;
import fr.pioneers.core.inventory.ItemSlot;
import java.util.function.LongPredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 1.0.4-beta (lot 22) : COEUR du SILO A OBJETS ({@code item_silo}, process « silo » de sa fiche, fabrique par {@link CoreLogics}) : stockage
 * en vrac d'UN SEUL objet (objet et NBT identiques). Contenance = volume interieur de la structure x {@link #PER_BLOCK} x 4^tier. La case
 * d'entree (ports d'objets en entree) se vide dans le stock ; la case de sortie (ports en sortie) est tenue pleine depuis le stock. Aucune
 * energie, aucune chaleur. Le stock est sauve sous la cle {@code silo} du controleur ; la table de butin du controleur le COPIE dans
 * l'objet casse ({@code BlockEntityTag.silo}) : reposer le controleur le rend (un silo demonte ne perd rien). Structure defaite : le stock
 * reste, plus rien n'entre ni ne sort ; une structure reconstruite plus petite ne prend plus rien tant qu'elle deborde.
 */
public final class SiloCore implements MultiblockLogic {

    /** Objets par case interieure au tier MKI (x4 par tier). */
    public static final long PER_BLOCK = 2048;
    /**
     * Indice de la valeur d'interface « contenu » (71 : libre depuis le lot 15 de la 1.0.4) : objets tenus (40 bits bas) | cases interieures
     * &lt;&lt; 40 ({@link #unpack}).
     */
    public static final int VALUE_STORED = 71;

    private final ControllerTile tile;
    /** Objet stocke (quantite 1), vide si le silo est vide. */
    private ItemStack type = ItemStack.EMPTY;
    private long stored;
    private long capacity;
    /** Cases interieures de la derniere structure formee (contenance cote client : {@link #capacity(int, int)}). */
    private int volume;

    SiloCore(ControllerTile tile) {
        this.tile = tile;
    }

    /** Contenance d'une structure de {@code volume} cases interieures au tier {@code tier} (0..3). */
    public static long capacity(int volume, int tier) {
        return Math.max(1, volume) * PER_BLOCK << (2 * Math.max(0, tier));
    }

    public ItemStack type() {
        return type;
    }

    /** Objets tenus : stock + case de sortie. */
    public long total() {
        ItemSlot out = tile.outputs.isEmpty() ? null : tile.outputs.get(0);
        return stored + (out == null ? 0 : out.getCount());
    }

    public long capacity() {
        return capacity;
    }

    @Override
    public String saveKey() {
        return "silo";
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (!type.isEmpty()) {
            tag.put("item", ItemSlot.saveStack(type));
        }
        tag.putLong("stored", stored);
        tag.putLong("capacity", capacity);
        tag.putInt("volume", volume);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        type = tag.contains("item") ? ItemSlot.loadStack(tag.getCompound("item")) : ItemStack.EMPTY;
        if (!type.isEmpty()) {
            type.setCount(1);
        }
        stored = Math.max(0, tag.getLong("stored"));
        capacity = Math.max(0, tag.getLong("capacity"));
        volume = tag.getInt("volume");
    }

    @Override
    public void onStructure(Structure.Result result, Level level) {
        volume = result.volume();
        capacity = capacity(volume, result.tier());
    }

    @Override
    public int work(LongPredicate use, boolean enabled) {
        ItemSlot in = tile.inputs.isEmpty() ? null : tile.inputs.get(0), out = tile.outputs.isEmpty() ? null : tile.outputs.get(0);
        boolean moved = false;
        if (in != null && !in.isEmpty() && accepts(in.getStack())) {
            if (type.isEmpty()) {
                type = in.getStack().copyWithCount(1);
            }
            int n = (int) Math.min(in.getCount(), Math.max(0, capacity - total()));
            if (n > 0) {
                stored += n;
                ItemStack rest = in.getStack().copy();
                rest.shrink(n);
                in.setStack(rest);
                moved = true;
            }
        }
        if (out != null && !type.isEmpty() && stored > 0 && (out.isEmpty() || ItemStack.isSameItemSameTags(out.getStack(), type))) {
            int room = type.getMaxStackSize() - out.getCount();
            int n = (int) Math.min(room, stored);
            if (n > 0) {
                out.setStack(type.copyWithCount(out.getCount() + n));
                stored -= n;
                moved = true;
            }
        }
        if (stored == 0 && (out == null || out.isEmpty()) && (in == null || in.isEmpty())) {
            type = ItemStack.EMPTY;
        }
        if (moved) {
            tile.setChanged();
        }
        return IDLE;
    }

    /** L'objet entre-t-il ? (silo vide, ou meme objet et meme NBT) */
    private boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && (type.isEmpty() || ItemStack.isSameItemSameTags(stack, type));
    }

    @Override
    public boolean ownsItems() {
        return true;
    }

    @Override
    public boolean acceptsItem(ItemStack stack) {
        return accepts(stack) && total() < capacity;
    }

    @Override
    public int itemKinds() {
        return 1;
    }

    @Override
    public boolean heats() {
        return false;
    }

    @Override
    public int comparator() {
        long t = total();
        return t <= 0 || capacity <= 0 ? 0 : (int) Math.min(15, 1 + Math.floor(t * 14.0 / capacity));
    }

    @Override
    public long value(int index) {
        return index == VALUE_STORED ? Math.min(total(), (1L << 40) - 1) | (long) Math.min(0xFFFF, volume) << 40 : 0;
    }

    /** Valeur d'interface -> {objets tenus, cases interieures}. */
    public static long[] unpack(long packed) {
        return new long[]{packed & ((1L << 40) - 1), packed >>> 40 & 0xFFFF};
    }
}

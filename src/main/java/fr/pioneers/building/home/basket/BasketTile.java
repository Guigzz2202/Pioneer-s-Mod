package fr.pioneers.building.home.basket;

import fr.pioneers.building.home.PiHome;
import fr.pioneers.building.home.bag.FishStacking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.3-beta (lot 11b, Guillaume : « un panier que l'on peut remplir de produits agricoles ; il ne prend qu'un type d'objet et sa texture
 * dans le monde varie selon ce qu'il contient et sa quantite ») : contenu du panier = UNE sorte d'objet ({@link #kind}, NBT compris) et son
 * nombre, au plus {@link #STACKS} piles. Filtre : tag {@code pioneers:basket_produce} (cultures, fruits, legumes, viandes, poissons, oeufs,
 * laine, miel...). Entonnoirs, cables et robots par la capacite d'objets (une case virtuelle : {@link Handler}). Cle de sauvegarde
 * {@code Basket} {Item, Count}, recopiee dans l'objet par la table de butin (copy_nbt vers BlockEntityTag) : le panier casse garde son contenu.
 * Le rendu (tas de l'objet a {@link #LEVELS} paliers) est {@code J:building/home/client/BasketRenderer}.
 * <p>
 * 1.0.3-beta (lot 12, Guillaume : « le panier doit avoir une version haute et une demi-hauteur ») : la tuile sert aux deux formats ; le
 * format vient du bloc ({@link BasketBlock#tall()}) : demi-hauteur (id d'origine {@code basket}) {@link #STACKS} 4 piles et {@link #LEVELS}
 * 6 paliers, haut ({@code tall_basket}) {@link #TALL_STACKS} 8 piles et {@link #TALL_LEVELS} 10 paliers.
 */
public class BasketTile extends BlockEntity {

    public static final TagKey<Item> PRODUCE = ItemTags.create(PiHome.rl("basket_produce"));
    /** Capacite : 4 piles de l'objet (panier demi-hauteur), 8 (panier haut). */
    public static final int STACKS = 4, TALL_STACKS = 8;
    /** Paliers du tas dessine (1..LEVELS ; 0 = vide) : demi-hauteur, haut. */
    public static final int LEVELS = BasketHeap.HALF_LEVELS, TALL_LEVELS = BasketHeap.TALL_LEVELS;
    public static final String KEY = "Basket";

    private ItemStack kind = ItemStack.EMPTY;
    private int count;
    private final LazyOptional<IItemHandler> items = LazyOptional.of(() -> new Handler(this));

    public BasketTile(BlockPos pos, BlockState state) {
        super(PiHome.BASKET_TILE.get(), pos, state);
    }

    // ------------------------------------------------------------------ regles (pures sur le contenu)

    public static boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && stack.is(PRODUCE);
    }

    public ItemStack kind() {
        return kind;
    }

    public int count() {
        return count;
    }

    /** Panier haut (format du bloc). */
    public boolean tall() {
        return getBlockState().getBlock() instanceof BasketBlock b && b.tall();
    }

    /** Piles que tient ce panier (4 ou 8). */
    public int stacks() {
        return tall() ? TALL_STACKS : STACKS;
    }

    /** Paliers de ce panier (6 ou 10). */
    public int levels() {
        return tall() ? TALL_LEVELS : LEVELS;
    }

    /** 1.0.3-beta (correctif, Guillaume : « le panier sert de deco : le petit prend 8 objets, le grand 16 ») : capacite fixe, en objets. */
    public static final int ITEMS = 8, TALL_ITEMS = 16;

    public int capacity() {
        return kind.isEmpty() ? 0 : tall() ? TALL_ITEMS : ITEMS;
    }

    /** Capacite pour cet objet (le panier vide prend la sorte du premier objet pose). */
    public int capacityFor(ItemStack stack) {
        return capacityFor(stack, stacks());
    }

    /** Capacite d'un panier de {@code stacks} piles pour cet objet. */
    public static int capacityFor(ItemStack stack, int stacks) {
        return stacks == TALL_STACKS ? TALL_ITEMS : ITEMS;
    }

    /** Palier du tas du panier demi-hauteur : 0 vide, sinon 1..LEVELS selon le remplissage. */
    public static int level(int count, int capacity) {
        return level(count, capacity, LEVELS);
    }

    /** Palier du tas : 0 vide, sinon 1..{@code levels} selon le remplissage. */
    public static int level(int count, int capacity, int levels) {
        if (count <= 0 || capacity <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(levels, (int) Math.ceil(levels * (double) count / capacity)));
    }

    public int level() {
        return level(count, capacity(), levels());
    }

    /** 1.0.4-beta (lot 4) : deux poissons de la meme espece sont de la meme sorte, quelle que soit leur taille ({@link FishStacking}). */
    public boolean sameKind(ItemStack stack) {
        return kind.isEmpty() || ItemStack.isSameItemSameTags(kind, stack) || FishStacking.sameSpecies(kind, stack);
    }

    /** Combien de {@code stack} on peut mettre (0 : autre sorte, objet refuse ou panier plein). */
    public int room(ItemStack stack) {
        if (!accepts(stack) || !sameKind(stack)) {
            return 0;
        }
        return Math.max(0, capacityFor(stack) - count);
    }

    /** Ajoute jusqu'a {@code n} objets de {@code stack} ; renvoie le nombre pris. */
    public int insert(ItemStack stack, int n, boolean simulate) {
        int take = Math.min(n, Math.min(stack.getCount(), room(stack)));
        if (take > 0 && !simulate) {
            if (kind.isEmpty()) {
                kind = stack.copyWithCount(1);
            } else if (FishStacking.sized(kind)) {
                //1.0.4-beta (lot 4) : le panier garde la plus grande taille de ses poissons
                FishStacking.setSize(kind, FishStacking.merged(kind, stack));
            }
            count += take;
            changed();
        }
        return take;
    }

    /** Retire jusqu'a {@code n} objets (au plus une pile) ; renvoie la pile retiree. */
    public ItemStack extract(int n, boolean simulate) {
        if (kind.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        int take = Math.min(n, Math.min(count, kind.getMaxStackSize()));
        ItemStack out = kind.copyWithCount(take);
        if (!simulate && take > 0) {
            count -= take;
            if (count <= 0) {
                count = 0;
                kind = ItemStack.EMPTY;
            }
            changed();
        }
        return out;
    }

    /** Signal du comparateur (0..15). */
    public int signal() {
        return count <= 0 ? 0 : 1 + (int) Math.floor(14.0 * count / Math.max(1, capacity()));
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    // ------------------------------------------------------------------ sauvegarde, client

    public static CompoundTag write(ItemStack kind, int count) {
        CompoundTag t = new CompoundTag();
        if (!kind.isEmpty() && count > 0) {
            t.put("Item", kind.copyWithCount(1).save(new CompoundTag()));
            t.putInt("Count", count);
        }
        return t;
    }

    /** Contenu lu dans une cle {@code Basket} (objet de l'objet ou de la tuile) : la sorte (1 objet) et le nombre. */
    public static ItemStack readKind(CompoundTag t) {
        return t.contains("Item") ? ItemStack.of(t.getCompound("Item")) : ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(KEY, write(kind, count));
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        CompoundTag t = tag.getCompound(KEY);
        kind = readKind(t);
        //1.0.3-beta (correctif) : un panier d'avant la capacite de 8 / 16 garde tout son contenu (rien n'est perdu ; plein, il ne prend plus rien)
        count = kind.isEmpty() ? 0 : Math.max(0, t.getInt("Count"));
        if (count == 0) {
            kind = ItemStack.EMPTY;
        }
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.put(KEY, write(kind, count));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            load(packet.getTag());
        }
    }

    // ------------------------------------------------------------------ capacite d'objets

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return items.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        items.invalidate();
    }

    /** Une case virtuelle : la sorte du panier, au plus une pile a la fois en lecture ; limite = 4 ou 8 piles. */
    private record Handler(BasketTile tile) implements IItemHandler {

        @Override
        public int getSlots() {
            return 1;
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return tile.kind.isEmpty() ? ItemStack.EMPTY : tile.kind.copyWithCount(Math.min(tile.count, tile.kind.getMaxStackSize()));
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            int took = tile.insert(stack, stack.getCount(), simulate);
            return took >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - took);
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return tile.extract(amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return tile.tall() ? TALL_ITEMS : ITEMS;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return tile.room(stack) > 0;
        }
    }
}

package fr.pioneers.economy.stall;

import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.economy.EconomyBlocks;
import fr.pioneers.economy.Values;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : stock, annonces et proprietaire d'un etal (27 cases, 9 annonces + 3 au niveau 10 de Commerce) ou d'un comptoir
 * (54 cases, 27 + 9). La tuile n'execute RIEN par tick : tout se recalcule au changement (annonce, stock), et les objets montres sur le plateau
 * ne sont renvoyes aux clients que s'ils changent. Le stock s'ouvre aux conduits du mod par toutes les faces, en INSERTION seulement, et
 * seulement pour un objet d'une annonce (une usine ne peut pas remplir l'etal d'objets qu'il ne vend pas). Le bonus d'annonces et la taxe du
 * proprietaire sont des instantanes pris a chacune de ses visites (il peut etre hors ligne quand un client achete).
 */
public class StallTile extends BlockEntity {

    private final boolean counter;
    @Nullable
    private UUID owner;
    private String ownerName = "";
    private final Listing[] listings;
    private final Stock stock;
    /** Instantanes du proprietaire : annonces en plus (Commerce), taxe en pour mille (-1 : taxe de base). */
    private int bonus;
    private int taxPermille = -1;
    /** Objets montres sur le plateau (3 ou 6), calcules par le serveur, synchronises au client. */
    private List<ItemStack> shown = List.of();
    /** Cote client : la protection est-elle active (reglage du serveur) ? */
    private boolean protectedFlag = true;
    private LazyOptional<IItemHandler> pipes = LazyOptional.empty();

    public StallTile(BlockPos pos, BlockState state) {
        super(EconomyBlocks.STALL_TILE.get(), pos, state);
        counter = state.getBlock() instanceof StallBlock b && b.counter();
        listings = new Listing[maxListings(counter)];
        for (int i = 0; i < listings.length; i++) {
            listings[i] = new Listing();
        }
        stock = new Stock(counter ? 54 : 27);
    }

    /** Une annonce : objet vendu (exemple, quantite 1), quantite par lot, prix du lot (jetons), memoire des acheteurs. */
    public static final class Listing {

        public ItemStack item = ItemStack.EMPTY;
        public int lot = 1;
        public long price;
        public final StallRules.History history = new StallRules.History();

        public boolean empty() {
            return item.isEmpty();
        }

        public void clear() {
            item = ItemStack.EMPTY;
            lot = 1;
            price = 0;
            history.clear();
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            if (!item.isEmpty()) {
                t.put("item", item.save(new CompoundTag()));
                t.putInt("lot", lot);
                t.putLong("price", price);
                t.putLongArray("buyers", history.toArray());
            }
            return t;
        }

        void load(CompoundTag t) {
            clear();
            if (t.contains("item")) {
                item = ItemStack.of(t.getCompound("item"));
                item.setCount(1);
                lot = Math.max(1, Math.min(StallRules.MAX_LOT, t.getInt("lot")));
                price = Math.max(0, t.getLong("price"));
                for (long b : t.getLongArray("buyers")) {
                    history.add(b);
                }
            }
        }
    }

    /** Le stock : un coffre que seuls le proprietaire et les operateurs ouvrent (vrai coffre de Minecraft, aucun ecran a nous). */
    public final class Stock extends SimpleContainer {

        Stock(int size) {
            super(size);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            stockChanged();
        }

        @Override
        public boolean stillValid(@NotNull Player player) {
            return !isRemoved() && level != null && level.getBlockEntity(worldPosition) == StallTile.this
                   && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64
                   && canManage(player);
        }
    }

    // ------------------------------------------------------------------ lecture
    public static int baseListings(boolean counter) {
        return counter ? 27 : 9;
    }

    public static int maxListings(boolean counter) {
        return baseListings(counter) + StallRules.listingBonus(3, counter);
    }

    public boolean counter() {
        return counter;
    }

    /** Annonces utilisables : base + bonus de Commerce du proprietaire (instantane). */
    public int slots() {
        return Math.min(listings.length, baseListings(counter) + bonus);
    }

    public int bonus() {
        return bonus;
    }

    public Listing listing(int i) {
        return listings[i];
    }

    public int listingCount() {
        return listings.length;
    }

    public Stock stock() {
        return stock;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public List<ItemStack> shown() {
        return shown;
    }

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    /** Reglage {@code stallProtected} (cote client : valeur envoyee par le serveur). */
    public boolean isProtected() {
        return level != null && level.isClientSide ? protectedFlag : PiCommonConfig.get(RpConfig.STALL_PROTECTED, true);
    }

    /** Configurer, vider, casser : le proprietaire, un operateur, ou tout le monde si la protection est coupee. */
    public boolean canManage(Player player) {
        return isOwner(player) || player.hasPermissions(2) || !isProtected();
    }

    /** Taxe en pour mille appliquee aux ventes (instantane du proprietaire, sinon taxe de base). */
    public int taxPermille() {
        return taxPermille >= 0 ? taxPermille : StallRules.taxPermille(PiCommonConfig.get(RpConfig.STALL_TAX, 0.05), 0);
    }

    // ------------------------------------------------------------------ ecriture (serveur)
    void setOwner(UUID uuid, String name) {
        owner = uuid;
        ownerName = name == null ? "" : name;
        markChanged(true);
    }

    /** Instantane du proprietaire connecte : nom de pionnier, annonces en plus, taxe. */
    void snapshot(String name, int listingBonus, int permille) {
        boolean look = !ownerName.equals(name);
        ownerName = name;
        bonus = Math.max(0, listingBonus);
        taxPermille = permille;
        markChanged(look);
    }

    // ------------------------------------------------------------------ prix (bornes de partners.json)
    public static double minPct() {
        return Values.partner("stall", "min", 0.25);
    }

    public static double maxPct() {
        return Values.partner("stall", "max", 4.0);
    }

    /** L'annonce est-elle vendable en ce moment (objet, valeur, prix dans les bornes) ? */
    public static boolean priced(Listing l) {
        return !l.empty() && StallRules.valid(l.price, Values.value(l.item), l.lot, minPct(), maxPct());
    }

    /** Unites de cet objet dans le stock (meme objet, memes donnees). */
    public int stockOf(ItemStack like) {
        if (like.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < stock.getContainerSize(); i++) {
            ItemStack s = stock.getItem(i);
            if (ItemStack.isSameItemSameTags(s, like)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Retire {@code units} unites de l'objet du stock ; rien n'est retire s'il en manque. */
    boolean takeStock(ItemStack like, int units) {
        if (stockOf(like) < units) {
            return false;
        }
        int left = units;
        for (int i = stock.getContainerSize() - 1; i >= 0 && left > 0; i--) {
            ItemStack s = stock.getItem(i);
            if (ItemStack.isSameItemSameTags(s, like)) {
                int k = Math.min(left, s.getCount());
                s.shrink(k);
                left -= k;
                if (s.isEmpty()) {
                    stock.setItem(i, ItemStack.EMPTY);
                }
            }
        }
        stock.setChanged();
        return true;
    }

    /** L'objet est-il celui d'une annonce (insertion par les conduits) ? */
    boolean listed(ItemStack stack) {
        for (int i = 0; i < slots(); i++) {
            if (!listings[i].empty() && ItemStack.isSameItemSameTags(listings[i].item, stack)) {
                return true;
            }
        }
        return false;
    }

    /** Annonce montree aux acheteurs : utilisable, vendable, et au moins un lot en stock. */
    public boolean active(int i) {
        Listing l = listings[i];
        return i < slots() && priced(l) && stockOf(l.item) >= l.lot;
    }

    private List<ItemStack> computeShown() {
        int max = counter ? 6 : 3;
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < listings.length && out.size() < max; i++) {
            if (active(i)) {
                out.add(listings[i].item.copyWithCount(1));
            }
        }
        return out;
    }

    void stockChanged() {
        markChanged(false);
    }

    /** Annonce ou stock change : sauvegarde, puis renvoi aux clients si le plateau ou le panneau change. */
    void markChanged(boolean look) {
        setChanged();
        if (level == null || level.isClientSide) {
            return;
        }
        List<ItemStack> now = computeShown();
        boolean same = now.size() == shown.size();
        for (int i = 0; same && i < now.size(); i++) {
            same = ItemStack.isSameItemSameTags(now.get(i), shown.get(i));
        }
        if (!same || look) {
            shown = now;
            BlockState s = getBlockState();
            level.sendBlockUpdated(worldPosition, s, s, 3);
        }
    }

    // ------------------------------------------------------------------ sauvegarde et synchronisation
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        tag.putString("ownerName", ownerName);
        tag.putInt("bonus", bonus);
        tag.putInt("tax", taxPermille);
        ListTag l = new ListTag();
        for (Listing x : listings) {
            l.add(x.save());
        }
        tag.put("listings", l);
        NonNullList<ItemStack> items = NonNullList.withSize(stock.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, stock.getItem(i));
        }
        CompoundTag st = new CompoundTag();
        ContainerHelper.saveAllItems(st, items, true);
        tag.put("stock", st);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
        if (tag.contains("listings")) {
            bonus = tag.getInt("bonus");
            taxPermille = tag.contains("tax") ? tag.getInt("tax") : -1;
            ListTag l = tag.getList("listings", Tag.TAG_COMPOUND);
            for (int i = 0; i < listings.length; i++) {
                listings[i].load(i < l.size() ? l.getCompound(i) : new CompoundTag());
            }
            NonNullList<ItemStack> items = NonNullList.withSize(stock.getContainerSize(), ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag.getCompound("stock"), items);
            for (int i = 0; i < items.size(); i++) {
                stock.setItem(i, items.get(i));
            }
        }
        if (tag.contains("shown")) {
            //paquet du serveur (client)
            List<ItemStack> s = new ArrayList<>();
            for (Tag x : tag.getList("shown", Tag.TAG_COMPOUND)) {
                s.add(ItemStack.of((CompoundTag) x));
            }
            shown = List.copyOf(s);
            protectedFlag = tag.getBoolean("prot");
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            shown = computeShown();
            //1.0.3-beta (lot 11) : etal pose avant (1 bloc) : son bloc du haut est pose au premier tick si la place est libre
            if (getBlockState().getBlock() instanceof TallStallBlock) {
                level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
            }
        }
    }

    /** Pour le client : proprietaire, nom, objets montres, protection (jamais le stock ni les prix). */
    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        if (owner != null) {
            t.putUUID("owner", owner);
        }
        t.putString("ownerName", ownerName);
        ListTag l = new ListTag();
        for (ItemStack s : shown) {
            l.add(s.save(new CompoundTag()));
        }
        t.put("shown", l);
        t.putBoolean("prot", isProtected());
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------------ conduits : insertion seulement
    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && !isRemoved()) {
            if (!pipes.isPresent()) {
                pipes = LazyOptional.of(() -> new Inlet(stock));
            }
            return pipes.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        pipes.invalidate();
    }

    /** Vue des conduits : insertion des seuls objets annonces, aucune extraction (ni conduit, ni entonnoir, ni robot). */
    private final class Inlet extends InvWrapper {

        Inlet(Stock stock) {
            super(stock);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack.isEmpty() || !listed(stack) ? stack : super.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return listed(stack);
        }
    }
}

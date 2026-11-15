package fr.pioneers.logistics.warehouse.client;

import fr.pioneers.core.network.PacketWarehouse;
import fr.pioneers.logistics.warehouse.CounterTile;
import fr.pioneers.logistics.warehouse.Parcel;
import fr.pioneers.logistics.warehouse.StockKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 1) : etat CLIENT de l'entrepot. Derniere liste recue de chaque comptoir ({@link PacketWarehouse.Contents}, interface
 * ouverte) ; comptoirs connus (leurs colis, pour ouvrir les battants des trappes ou un colis monte ou descend). Rien n'est sauvegarde.
 * <p>1.0.5-beta (lot 17, Guillaume : « fluide et performant ») : le STOCK arrive par differences ({@link PacketWarehouse.Stock}) et se garde
 * EN TABLE par type ({@link StockKey}) : l'interface cherche la quantite d'un objet en une lecture (avant : toute la liste parcourue pour
 * chaque case de la grille, a chaque image) ; la liste triee n'est refaite que si le stock change ({@link #received}). Destinations des
 * commandes (interfaces de sortie du reseau) ; panier local de l'interface ouverte (lu par la poignee de transfert de JEI).
 */
public final class CounterClient {

    /** Stock d'un comptoir : table par type, version, destinations. */
    public static final class Stock {
        private final Map<StockKey, PacketWarehouse.Entry> rows = new LinkedHashMap<>();
        private List<PacketWarehouse.Entry> list = List.of();
        private boolean listDirty = true;
        public List<BlockPos> dests = List.of();
        @Nullable
        public BlockPos dest;
        public boolean ready;

        public long count(ItemStack proto) {
            PacketWarehouse.Entry e = proto.isEmpty() ? null : rows.get(StockKey.of(proto));
            return e == null ? 0 : e.count();
        }

        public long count(StockKey key) {
            PacketWarehouse.Entry e = rows.get(key);
            return e == null ? 0 : e.count();
        }

        /** Lignes du stock (refaite seulement apres un changement). */
        public List<PacketWarehouse.Entry> entries() {
            if (listDirty) {
                list = new ArrayList<>(rows.values());
                listDirty = false;
            }
            return list;
        }

        public int size() {
            return rows.size();
        }
    }

    private static final Map<BlockPos, PacketWarehouse.Contents> LISTS = new HashMap<>();
    /** Stocks des derniers comptoirs ouverts (4 au plus). */
    private static final Map<BlockPos, Stock> STOCKS = new LinkedHashMap<>(8, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, Stock> eldest) {
            return size() > 4;
        }
    };
    private static final Set<CounterTile> COUNTERS = Collections.newSetFromMap(new WeakHashMap<>());
    /** Augmente a chaque liste ou stock recu (la vue refait son tri). */
    private static int received;
    /** Panier local de l'interface ouverte (quantite au panier d'un objet) ; null si aucune. */
    @Nullable
    private static BlockPos cartAt;
    @Nullable
    private static ToIntFunction<ItemStack> cart;

    private CounterClient() {
    }

    public static void accept(PacketWarehouse.Contents contents) {
        LISTS.put(contents.pos(), contents);
        received++;
    }

    /** 1.0.5-beta (lot 17) : stock par differences ({@code reset} : la liste repart de zero). */
    public static void accept(PacketWarehouse.Stock packet) {
        Stock s = STOCKS.computeIfAbsent(packet.pos(), k -> new Stock());
        if (packet.reset()) {
            s.rows.clear();
        }
        for (PacketWarehouse.Entry e : packet.entries()) {
            StockKey k = StockKey.of(e.proto());
            if (e.count() <= 0) {
                s.rows.remove(k);
            } else {
                s.rows.put(k, new PacketWarehouse.Entry(e.proto().copyWithCount(1), e.count()));
            }
        }
        s.listDirty = true;
        s.ready = true;
        s.dests = List.copyOf(packet.dests());
        s.dest = packet.dest() == 0 ? null : BlockPos.of(packet.dest());
        received++;
    }

    @Nullable
    public static PacketWarehouse.Contents list(BlockPos pos) {
        return LISTS.get(pos);
    }

    /** 1.0.5-beta (lot 17) : stock connu du comptoir en {@code pos} (vide tant que rien n'est arrive : {@link Stock#ready}). */
    public static Stock stock(BlockPos pos) {
        return STOCKS.computeIfAbsent(pos.immutable(), k -> new Stock());
    }

    /** Quantite demandee en file pour ce type (liste du comptoir). */
    public static long queued(BlockPos pos, ItemStack proto) {
        PacketWarehouse.Contents c = LISTS.get(pos);
        if (c == null) {
            return 0;
        }
        for (PacketWarehouse.Entry q : c.queued()) {
            if (ItemStack.isSameItemSameTags(q.proto(), proto)) {
                return q.count();
            }
        }
        return 0;
    }

    /** 1.0.5-beta (lot 17) : l'interface ouverte publie son panier local (JEI le lit pour dire ce qui manque). */
    public static void cart(BlockPos pos, @Nullable ToIntFunction<ItemStack> local) {
        cartAt = local == null ? null : pos.immutable();
        cart = local;
    }

    /** Quantite au panier local de l'interface ouverte sur ce comptoir. */
    public static int inCart(BlockPos pos, ItemStack proto) {
        return cart != null && pos.equals(cartAt) ? cart.applyAsInt(proto) : 0;
    }

    /** Ce qui peut encore aller au panier : stock - file - panier. */
    public static long free(BlockPos pos, ItemStack proto) {
        return Math.max(0, stock(pos).count(proto) - queued(pos, proto) - inCart(pos, proto));
    }

    public static int received() {
        return received;
    }

    /** Un comptoir rendu a l'ecran (ses colis comptent pour les trappes). */
    public static void seen(CounterTile tile) {
        COUNTERS.add(tile);
    }

    /** La roue {@code pulley} dessert-elle la trappe {@code hatch} (posee dessus ou plus haut : lot 2) ? */
    private static boolean serves(CounterTile t, BlockPos pulley, BlockPos hatch) {
        if (pulley.getX() != hatch.getX() || pulley.getZ() != hatch.getZ() || pulley.getY() <= hatch.getY()) {
            return false;
        }
        fr.pioneers.logistics.warehouse.WarehouseLines.Station st = fr.pioneers.logistics.warehouse.WarehouseLines.station(t.getLevel(), pulley);
        return st != null && st.block().equals(hatch);
    }

    /** Un colis monte ou descend-il par la trappe {@code hatch} ? */
    public static boolean hatchBusy(BlockPos hatch) {
        for (CounterTile t : COUNTERS) {
            if (t.isRemoved()) {
                continue;
            }
            for (Parcel p : t.counter().parcels()) {
                if (p.route.isEmpty()) {
                    continue;
                }
                if (p.phase == Parcel.Phase.RISE && serves(t, p.startStation(), hatch)
                    || (p.phase == Parcel.Phase.LOWER || p.phase == Parcel.Phase.TRAVEL && p.geometryOrNull() != null
                        && p.clientS > p.geometryOrNull().length() - 0.6) && serves(t, p.endStation(), hatch)) {
                    return true;
                }
            }
        }
        return false;
    }
}

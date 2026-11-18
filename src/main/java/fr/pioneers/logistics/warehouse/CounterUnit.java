package fr.pioneers.logistics.warehouse;

import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.tech.kinetics.rope.PulleyTile;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 1) : UNITE du comptoir d'entrepot (portee par {@link CounterTile}) : la FILE DES DEMANDES, les COLIS en route
 * ({@link Parcel}, verite du serveur, sauvegardes), les ENVOIS des cases d'envoi. A chaque tick : colis avances a la vitesse de la corde
 * (rien ne bouge sans rotation), un depart au plus toutes les {@link #SPACING} ticks, {@link #MAX_PARCELS} colis en route au plus. Le reseau
 * (graphe des cordes) est garde jusqu'au prochain changement ({@link WarehouseNetwork#generation}) ; aucun balayage des caisses par tick :
 * on ne lit leurs index qu'au depart d'un colis ou pour la liste de l'interface (ouverte, au plus toutes les 10 ticks).
 * <p>1.0.4-beta (lot 3) : PANIERS par joueur ({@link CounterCart}, {@link #cartSet}, {@link #order}) : l'interface remplit le panier,
 * « Commander » le verse dans la file des demandes en une fois.
 */
public class CounterUnit extends MachineUnit {

    /** 1.0.5-beta (lot 13) : {@code SPACING} 8 ticks (avant 10) : meme ecart sur la corde a la cadence {@link LineNode#CADENCE}. */
    public static final int MAX_PARCELS = 24, SPACING = 8, MAX_QUEUE = 64;
    /** Valeurs d'interface nommees. */
    public static final UiValues.Key ROPE = UiValues.Key.of("rope_x100"), PARCELS = UiValues.Key.of("parcels"), QUEUE = UiValues.Key.of("queue"),
          SENDING = UiValues.Key.of("sending"), CRATES = UiValues.Key.of("crates"), LINKED = UiValues.Key.of("linked");
    /**
     * 1.0.5-beta (lot 17, Guillaume : « le joueur assiste au maximum : pourquoi une commande attend ») : POURQUOI la file attend
     * ({@link #waitCode} : un code {@code W_*}, indice de {@link #WAIT_KEYS}, langue {@code gui.pioneers.warehouse.wait.<cle>}) ; colis en
     * route VERS cette station (le reste de {@link #PARCELS} repart a vide ou vient d'elle).
     */
    public static final UiValues.Key WAIT = UiValues.Key.of("wait"), INBOUND = UiValues.Key.of("inbound");
    public static final int W_NONE = 0, W_LINK = 1, W_STILL = 2, W_BUSY = 3, W_FULL = 4, W_COLUMN = 5, W_STOCK = 6, W_ROOM = 7;
    public static final String[] WAIT_KEYS = {"none", "link", "still", "busy", "full", "column", "stock", "room"};
    /** 1.0.5-beta (lot 17) : regles « garder N » de l'interface de sortie, au plus ; relues toutes les {@value #KEEP_EVERY} ticks. */
    public static final int MAX_KEEPS = 6, KEEP_EVERY = 20, MAX_KEEP = 64 * 64;
    /**
     * 1.0.4-beta (lot 34) : DIAGNOSTIC du raccordement ({@link #diagnose}) : code, puis jusqu'a trois positions (BlockPos.asLong) ou nombres
     * selon le code ; l'onglet Infos et la ligne du bas de l'interface le disent en clair ({@code gui.pioneers.warehouse.diag.<code>}).
     */
    public static final UiValues.Key DIAG = UiValues.Key.of("diag"), DIAG_A = UiValues.Key.of("diag_a"), DIAG_B = UiValues.Key.of("diag_b"),
          DIAG_C = UiValues.Key.of("diag_c");
    /** Codes du diagnostic (memes indices que {@link #DIAG_KEYS}). */
    public static final int D_OK = 0, D_STILL = 1, D_NO_PULLEY = 2, D_BIG_PULLEY = 3, D_COLUMN = 4, D_TOO_HIGH = 5, D_NO_ROPE = 6, D_ROPE_BLOCKED = 7,
          D_UNDER_WHEEL = 8, D_ONE_WAY = 9, D_UNFORMED = 10, D_OFF_HATCH = 11, D_NO_CRATE = 12, D_LOADING = 13, D_HORIZONTAL = 14;
    /** Suffixe de langue de chaque code ({@code gui.pioneers.warehouse.diag.<suffixe>} et {@code .fix} : ce qu'il faut faire). */
    public static final String[] DIAG_KEYS = {"ok", "still", "no_pulley", "big_pulley", "column", "too_high", "no_rope", "rope_blocked", "under_wheel",
          "one_way", "unformed", "off_hatch", "no_crate", "loading", "horizontal"};

    /** Un diagnostic : code ({@code D_*}) et ses trois valeurs (positions en BlockPos.asLong, ou nombres). */
    public record Diagnosis(int code, long a, long b, long c) {

        static Diagnosis of(int code) {
            return new Diagnosis(code, 0, 0, 0);
        }

        static Diagnosis of(int code, BlockPos a) {
            return new Diagnosis(code, a.asLong(), 0, 0);
        }

        static Diagnosis of(int code, BlockPos a, BlockPos b) {
            return new Diagnosis(code, a.asLong(), b.asLong(), 0);
        }

        static Diagnosis of(int code, BlockPos a, BlockPos b, BlockPos c) {
            return new Diagnosis(code, a.asLong(), b.asLong(), c == null ? 0 : c.asLong());
        }

        public boolean linked() {
            return code == D_OK || code == D_STILL;
        }
    }

    /** Une demande : un type, une quantite restante. */
    public static final class Request {
        public final ItemStack proto;
        public int left;

        Request(ItemStack proto, int left) {
            this.proto = proto;
            this.left = left;
        }
    }

    private final CounterTile tile;
    private final List<Request> queue = new ArrayList<>();
    private final List<Parcel> parcels = new ArrayList<>();
    /** 1.0.4-beta (lot 3) : paniers, par joueur. */
    private final Map<UUID, CounterCart> carts = new java.util.HashMap<>();
    private int nextId = 1;
    private long lastDispatch = -1000;
    private int graphGeneration = -1;
    private WarehouseNetwork.Graph graph = WarehouseNetwork.Graph.EMPTY;
    /** 1.0.4-beta (lot 34) : heure du dernier calcul du reseau ; diagnostic garde une seconde. */
    private long graphBuilt = -1_000_000, diagAt = -1_000_000;
    private Diagnosis diagnosis = Diagnosis.of(D_NO_PULLEY);
    /** Reseau incomplet (aucune caisse, corde ecartee, bout pas charge) : recalcule toutes les {@value #RETRY} ticks meme sans changement. */
    public static final int RETRY = 40;
    /** Reseau complet : relu toutes les {@value #REFRESH} ticks (un bloc pose sous une corde ne previent personne). */
    public static final int REFRESH = 200;
    private long lastResync;
    /** Etat du dernier envoi au client (resynchronisation des colis en route). */
    private boolean moving;
    /** Message du dernier refus (objet introuvable...), cle de langue. */
    private String notice = "";
    /** 1.0.5-beta (lot 17) : regles « garder N » (interface de sortie) ; une regle n'a pas trouve assez de stock a la derniere passe. */
    private final List<Keep> keeps = new ArrayList<>();
    private boolean keepShort;

    /** 1.0.5-beta (lot 17) : une regle « garder N » de l'interface de sortie : un type, la quantite a tenir dans ses cases. */
    public static final class Keep {
        public final ItemStack proto;
        public int count;

        public Keep(ItemStack proto, int count) {
            this.proto = proto.copyWithCount(1);
            this.count = count;
        }
    }

    public CounterUnit(UnitHost machine, CounterTile tile) {
        super(machine);
        this.tile = tile;
    }

    public List<Parcel> parcels() {
        return parcels;
    }

    public List<Request> queue() {
        return queue;
    }

    public String notice() {
        return notice;
    }

    /** Reseau du comptoir (reconstruit si une corde, une poulie ou une caisse a change). */
    public WarehouseNetwork.Graph graph() {
        Level level = level();
        if (level == null) {
            return WarehouseNetwork.Graph.EMPTY;
        }
        long now = level.getGameTime();
        //1.0.4-beta (lot 34) : un reseau incomplet se refait de lui-meme (bloc genant retire, chunk charge, caisse formee, poulie posee
        //au-dessus du comptoir) : avant, il restait faux jusqu'au prochain changement de corde
        boolean incomplete = graph.partial() || !graph.skipped().isEmpty() || graph.crates().isEmpty()
                             || (tile.station() == null) != graph.links().isEmpty();
        if (graphGeneration != WarehouseNetwork.generation() || now - graphBuilt >= (incomplete ? RETRY : REFRESH)) {
            graphGeneration = WarehouseNetwork.generation();
            graphBuilt = now;
            PulleyTile st = tile.station();
            graph = st == null ? WarehouseNetwork.Graph.EMPTY : WarehouseNetwork.build(level, st.getBlockPos());
            validateRoutes(level);
            diagAt = -1_000_000;
        }
        return graph;
    }

    /** 1.0.4-beta (lot 34) : diagnostic du raccordement (garde 20 ticks ; refait aussitot apres un nouveau calcul du reseau). */
    public Diagnosis diagnosis() {
        Level level = level();
        if (level == null) {
            return diagnosis;
        }
        WarehouseNetwork.Graph g = graph();
        if (level.getGameTime() - diagAt >= 20) {
            diagAt = level.getGameTime();
            diagnosis = diagnose(level, tile.getBlockPos(), tile.station(), g);
        }
        return diagnosis;
    }

    /**
     * 1.0.4-beta (lot 34, Guillaume : « mon comptoir ne se connecte pas au reseau ») : CE QUI MANQUE, dans l'ordre ou le joueur monte la
     * ligne : petite poulie au-dessus du comptoir (taille, axe, colonne libre, hauteur), une corde, des cordes que les colis peuvent suivre
     * (bloc touche, sous une roue, corde a sens unique), une caisse au bout (formee, poulie a la verticale de la TRAPPE), la rotation.
     */
    public static Diagnosis diagnose(Level level, BlockPos counter, @Nullable PulleyTile station, WarehouseNetwork.Graph g) {
        if (station == null) {
            BlockPos blocked = null;
            for (int k = 1; k <= WarehouseLines.MAX_DROP + 9; k++) {
                BlockPos p = counter.above(k);
                if (level.getBlockEntity(p) instanceof PulleyTile w) {
                    if (!w.lineNode()) {
                        return Diagnosis.of(D_HORIZONTAL, p);
                    }
                    if (!w.stationWheel()) {
                        return Diagnosis.of(D_BIG_PULLEY, p);
                    }
                    if (blocked != null) {
                        return Diagnosis.of(D_COLUMN, blocked, p);
                    }
                    return new Diagnosis(D_TOO_HIGH, p.asLong(), k - 1, WarehouseLines.MAX_DROP);
                }
                if (blocked == null && !WarehouseLines.open(level, p)) {
                    blocked = p;
                }
            }
            return Diagnosis.of(D_NO_PULLEY);
        }
        BlockPos st = station.getBlockPos();
        if (station.lineLinks().isEmpty()) {
            return Diagnosis.of(D_NO_ROPE, st);
        }
        if (!g.hatches(level).isEmpty()) {
            return Diagnosis.of(station.spin() == 0 ? D_STILL : D_OK, st);
        }
        for (WarehouseNetwork.Skipped k : g.skipped()) {
            if (k.hit() != null) {
                return k.wheel() ? Diagnosis.of(D_UNDER_WHEEL, k.to(), k.to(), k.hit()) : Diagnosis.of(D_ROPE_BLOCKED, k.from(), k.to(), k.hit());
            }
        }
        for (WarehouseNetwork.Skipped k : g.skipped()) {
            return Diagnosis.of(D_ONE_WAY, k.from(), k.to());
        }
        List<BlockPos> unformed = g.unformed(level);
        if (!unformed.isEmpty()) {
            return Diagnosis.of(D_UNFORMED, unformed.get(0));
        }
        for (BlockPos p : g.links().keySet()) {
            if (g.crates().containsKey(p)) {
                continue;
            }
            for (int k = 1; k <= WarehouseLines.MAX_DROP + 1; k++) {
                BlockPos q = p.below(k);
                if (level.getBlockState(q).getBlock() instanceof CrateBlock) {
                    return Diagnosis.of(D_OFF_HATCH, p, q);
                }
                if (!WarehouseLines.open(level, q)) {
                    break;
                }
            }
        }
        return Diagnosis.of(g.partial() ? D_LOADING : D_NO_CRATE, st);
    }

    /** Vitesse de la corde a la station du comptoir (blocs par tick ; 0 sans poulie ou a l'arret). */
    public double ropeSpeed() {
        PulleyTile st = tile.station();
        return st == null ? 0 : st.ropeSpeed();
    }

    // ------------------------------------------------------------------ demandes

    /** Demande {@code count} objets du type {@code proto} (bornee) ; faux si la file est pleine. */
    public boolean request(ItemStack proto, int count) {
        if (proto.isEmpty() || count <= 0) {
            return false;
        }
        for (Request r : queue) {
            if (ItemStack.isSameItemSameTags(r.proto, proto)) {
                r.left = (int) Math.min(Integer.MAX_VALUE / 2, (long) r.left + count);
                machine.changed();
                return true;
            }
        }
        if (queue.size() >= MAX_QUEUE) {
            return false;
        }
        queue.add(new Request(proto.copyWithCount(1), count));
        machine.changed();
        return true;
    }

    public void clearQueue() {
        queue.clear();
        machine.changed();
    }

    /** Quantite deja demandee (en file) pour ce type. */
    public long queued(ItemStack proto) {
        long n = 0;
        for (Request r : queue) {
            if (ItemStack.isSameItemSameTags(r.proto, proto)) {
                n += r.left;
            }
        }
        return n;
    }

    /** Stock de ce type dans les caisses du reseau (lu a la demande, jamais par tick). */
    public long stock(ItemStack proto) {
        Level level = level();
        if (level == null) {
            return 0;
        }
        long n = 0;
        for (CrateHatchTile h : graph().hatches(level)) {
            n += h.storage().count(proto);
        }
        return n;
    }

    /** Disponible a la commande : le stock moins ce qui est deja demande (en file). */
    public long available(ItemStack proto) {
        return Math.max(0, stock(proto) - queued(proto));
    }

    // ------------------------------------------------------------------ panier (lot 3)

    /** Panier du joueur {@code player} (cree vide au besoin). */
    public CounterCart cart(UUID player) {
        return carts.computeIfAbsent(player, k -> new CounterCart());
    }

    /** Les paniers (lecture : GameTests, sauvegarde). */
    public Map<UUID, CounterCart> carts() {
        return carts;
    }

    /**
     * Fixe la quantite d'une ligne du panier de {@code player}, bornee par ce qui est disponible ({@link #available}) ; 0 retire la ligne.
     * Faux (et avis) si le panier est plein.
     */
    public boolean cartSet(UUID player, ItemStack proto, int count) {
        if (proto.isEmpty()) {
            return false;
        }
        CounterCart c = cart(player);
        int n = (int) Math.min(Math.max(0, count), available(proto));
        if (!c.set(proto, n)) {
            c.say(new CounterCart.Notice("gui.pioneers.warehouse.cart.full", CounterCart.MAX_LINES, -1, "", 2));
            return false;
        }
        //1.0.5-beta (lot 22) : la part des recettes demandees suit les lignes baissees a la main
        c.prune(false);
        machine.changed();
        return true;
    }

    public void cartClear(UUID player) {
        CounterCart c = carts.get(player);
        if (c != null && (!c.lines.isEmpty() || !c.crafts.isEmpty())) {
            c.lines.clear();
            c.crafts.clear();
            machine.changed();
        }
    }

    /**
     * « COMMANDER » : tout le panier de {@code player} passe dans la file des demandes, en une fois. Chaque ligne est bornee par le
     * disponible (stock moins file) ; une ligne qui ne tient pas (stock parti entre-temps, file pleine : {@link #MAX_QUEUE} types) RESTE au
     * panier (ce qui manque seulement) ; les lignes acceptees en sortent. L'avis dit ce qui est parti et ce qui reste, et pourquoi.
     */
    public CounterCart.Notice order(UUID player) {
        CounterCart c = cart(player);
        CounterCart.Notice n;
        //1.0.5-beta (lot 17) : livraison a une INTERFACE DE SORTIE du reseau (destination du panier) : ses colis, sa file ; disparue ou
        //hors du reseau : retour au comptoir
        CounterTile target = destTile(c.dest);
        if (c.dest != null && target == null) {
            c.dest = null;
        }
        CounterUnit to = target == null ? this : target.counter();
        if (c.lines.isEmpty()) {
            n = new CounterCart.Notice("gui.pioneers.warehouse.order.empty", 0, -1, "", 0);
        } else if (tile.station() == null) {
            n = new CounterCart.Notice("gui.pioneers.warehouse.order.none", c.lines.size(), -1, "gui.pioneers.warehouse.reason.station", 2);
        } else {
            int accepted = 0, piles = 0, left = 0;
            String reason = "";
            for (Iterator<CounterCart.Line> it = c.lines.iterator(); it.hasNext(); ) {
                CounterCart.Line l = it.next();
                int k = (int) Math.min(l.count, to.available(l.proto));
                if (k <= 0) {
                    left++;
                    reason = reason.isEmpty() ? "gui.pioneers.warehouse.reason.stock" : reason;
                    continue;
                }
                if (!to.request(l.proto, k)) {
                    left++;
                    reason = "gui.pioneers.warehouse.reason.queue";
                    continue;
                }
                accepted++;
                piles += CounterCart.piles(l.proto, k);
                if (k < l.count) {
                    l.count -= k;
                    left++;
                    reason = reason.isEmpty() ? "gui.pioneers.warehouse.reason.stock" : reason;
                } else {
                    it.remove();
                }
            }
            n = left == 0 ? new CounterCart.Notice("gui.pioneers.warehouse.order.ok", accepted, piles, "", 1)
                          : accepted > 0 ? new CounterCart.Notice("gui.pioneers.warehouse.order.partial", accepted, left, reason, 2)
                                         : new CounterCart.Notice("gui.pioneers.warehouse.order.none", left, -1, reason, 2);
        }
        //1.0.5-beta (lot 22) : les recettes dont tout est parti quittent le panier ; les autres gardent ce qui reste
        c.prune(true);
        c.say(n);
        machine.changed();
        return n;
    }

    /** Contenu de l'entrepot (somme des caisses du reseau), par type. */
    public Map<ItemStack, Long> contents() {
        Map<ItemStack, Long> out = new LinkedHashMap<>();
        Level level = level();
        if (level == null) {
            return out;
        }
        Map<Object, ItemStack> keys = new java.util.HashMap<>();
        for (CrateHatchTile h : graph().hatches(level)) {
            for (CrateStorage.Entry e : h.storage().entries()) {
                Object key = java.util.List.of(e.proto().getItem(), java.util.Objects.requireNonNullElse((Object) e.proto().getTag(), ""));
                ItemStack found = keys.get(key);
                if (found == null) {
                    keys.put(key, e.proto());
                    out.put(e.proto(), e.count());
                } else {
                    out.put(found, out.get(found) + e.count());
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 1.0.5-beta (lot 17) : stock par differences, destinations, recettes

    /**
     * Empreinte du stock du reseau : generation du reseau et generation de chaque caisse (O(caisses)) : la liste par differences n'est relue
     * que si elle change.
     */
    public long stockStamp() {
        Level level = level();
        if (level == null) {
            return 0;
        }
        WarehouseNetwork.Graph g = graph();
        long h = graphGeneration * 1_000_003L + g.crates().size();
        for (CrateHatchTile t : g.hatches(level)) {
            h = h * 31 + t.storage().generation() * 7L + t.getBlockPos().asLong();
        }
        return h;
    }

    /** Stock du reseau par type (somme des caisses ; une passe sur leurs index, jamais par tick). */
    public Map<StockKey, Long> stockByKey() {
        Map<StockKey, Long> out = new LinkedHashMap<>();
        Level level = level();
        if (level == null) {
            return out;
        }
        for (CrateHatchTile h : graph().hatches(level)) {
            for (CrateStorage.Entry e : h.storage().entries()) {
                out.merge(StockKey.of(e.proto()), e.count(), Long::sum);
            }
        }
        return out;
    }

    /** Interfaces de sortie du reseau (position du bloc), destinations possibles d'une commande du comptoir. */
    public List<BlockPos> destinations() {
        Level level = level();
        List<BlockPos> out = new ArrayList<>();
        if (level == null || tile.role() != CounterTile.Role.COUNTER) {
            return out;
        }
        for (BlockPos pulley : graph().counters()) {
            WarehouseLines.Station st = WarehouseLines.station(level, pulley);
            if (st != null && st.tile() instanceof CounterTile ct && ct.role() == CounterTile.Role.OUTLET && out.size() < CounterTile.MAX_DESTS) {
                out.add(st.block().immutable());
            }
        }
        return out;
    }

    /** L'interface de sortie {@code dest} si elle est sur le reseau du comptoir (et desservie), sinon null. */
    @Nullable
    public CounterTile destTile(@Nullable BlockPos dest) {
        Level level = level();
        if (dest == null || level == null || !level.isLoaded(dest) || !(level.getBlockEntity(dest) instanceof CounterTile ct)
            || ct.role() != CounterTile.Role.OUTLET || ct.station() == null) {
            return null;
        }
        return destinations().contains(dest) ? ct : null;
    }

    /**
     * 1.0.5-beta (lot 17, Guillaume : « Maj + clic sur une recette JEI = demande des ingredients au reseau ») : une RECETTE au panier de
     * {@code player} : {@code times} fois les lignes ({@link RecipeAsk}), alternatives choisies d'apres le stock REEL (moins la file et ce
     * qui est deja au panier) ; ce qui manque n'est pas ajoute (l'avis dit combien) ; {@code order} : « Commander » aussitot.
     */
    public CounterCart.Notice recipe(UUID player, List<RecipeAsk.Line<StockKey>> lines, Map<StockKey, ItemStack> protos, int times, boolean order) {
        return recipe(player, lines, protos, times, order, ItemStack.EMPTY, 1);
    }

    /**
     * 1.0.5-beta (lot 22, retour 24 de Guillaume : « on devrait voir les recettes demandees et ajuster le nombre de fabrications qu'on veut
     * realiser (via la molette) ») : meme demande, et la RECETTE est gardee au panier ({@link CounterCart.Craft}) quand JEI donne son
     * produit ({@code result}, {@code yield} par fabrication) : elle se voit au panier et son nombre se regle ({@link #craftTimes}).
     */
    public CounterCart.Notice recipe(UUID player, List<RecipeAsk.Line<StockKey>> lines, Map<StockKey, ItemStack> protos, int times, boolean order,
          ItemStack result, int yield) {
        CounterCart c = cart(player);
        List<RecipeAsk.Line<StockKey>> merged = RecipeAsk.merge(lines);
        CounterCart.Notice n;
        if (merged.isEmpty()) {
            n = new CounterCart.Notice("gui.pioneers.warehouse.recipe.empty", 0, -1, "", 2);
            c.say(n);
            machine.changed();
            return n;
        }
        RecipeAsk.Plan<StockKey> plan = RecipeAsk.plan(merged, times, k -> {
            ItemStack p = protos.get(k);
            return p == null ? 0 : available(p) - c.count(p);
        });
        int added = 0;
        boolean full = false;
        Map<StockKey, Long> took = new LinkedHashMap<>();
        for (Map.Entry<StockKey, Long> e : plan.byKey().entrySet()) {
            ItemStack proto = protos.get(e.getKey());
            if (proto == null) {
                continue;
            }
            long before = c.count(proto);
            if (!cartSet(player, proto, (int) Math.min(CounterCart.MAX_COUNT, c.count(proto) + e.getValue()))) {
                full = true;
                break;
            }
            took.merge(e.getKey(), c.count(proto) - before, Long::sum);
            added++;
        }
        //1.0.5-beta (lot 22) : la recette demandee reste visible au panier (produit, ingredients, nombre de fabrications a la molette)
        if (!result.isEmpty() && !took.isEmpty()) {
            remember(c, merged, protos, times, plan.missingTotal(), took, result, yield);
        }
        if (added == 0 && !full) {
            n = new CounterCart.Notice("gui.pioneers.warehouse.recipe.none", plan.missingLines(), -1, "gui.pioneers.warehouse.reason.short", 2);
        } else if (plan.complete() && !full) {
            n = new CounterCart.Notice("gui.pioneers.warehouse.recipe.ok", plan.times(), added, "", 1);
        } else {
            n = new CounterCart.Notice("gui.pioneers.warehouse.recipe.partial", plan.times(), plan.missingTotal(),
                  full ? "gui.pioneers.warehouse.reason.cart" : "gui.pioneers.warehouse.reason.short", 2);
        }
        c.say(n);
        machine.changed();
        return order && added > 0 ? order(player) : n;
    }

    /**
     * 1.0.5-beta (lot 22) : garde la recette demandee au panier : une recette deja demandee (meme produit, memes lignes) s'ajoute (fabrications
     * et part du panier), sinon une recette nouvelle ({@link CounterCart#MAX_CRAFTS} au plus : la plus ancienne n'est plus suivie, ses
     * objets restent au panier).
     */
    private static void remember(CounterCart c, List<RecipeAsk.Line<StockKey>> lines, Map<StockKey, ItemStack> protos, int times, long missing,
          Map<StockKey, Long> took, ItemStack result, int yield) {
        List<RecipeAsk.Line<StockKey>> kept = new ArrayList<>();
        Map<StockKey, ItemStack> own = new java.util.HashMap<>();
        for (RecipeAsk.Line<StockKey> l : lines) {
            List<StockKey> alts = new ArrayList<>();
            for (StockKey k : l.alternatives()) {
                ItemStack p = protos.get(k);
                if (p != null && !p.isEmpty()) {
                    alts.add(k);
                    own.put(k, p.copyWithCount(1));
                }
            }
            if (!alts.isEmpty()) {
                kept.add(new RecipeAsk.Line<>(alts, l.perCraft()));
            }
        }
        if (kept.isEmpty()) {
            return;
        }
        CounterCart.Craft k = null;
        for (CounterCart.Craft x : c.crafts) {
            if (x.same(result, kept)) {
                k = x;
                break;
            }
        }
        if (k == null) {
            if (c.crafts.size() >= CounterCart.MAX_CRAFTS) {
                c.crafts.remove(0);
            }
            k = new CounterCart.Craft(c.nextCraft++, result, yield, kept, own, 0);
            c.crafts.add(k);
        }
        k.times = Math.min(RecipeAsk.MAX_TIMES, k.times + Math.max(1, times));
        k.missing = Math.max(0, missing);
        for (Map.Entry<StockKey, Long> e : took.entrySet()) {
            if (e.getValue() > 0) {
                k.contrib.merge(e.getKey(), e.getValue(), Long::sum);
                k.protos.putIfAbsent(e.getKey(), protos.get(e.getKey()).copyWithCount(1));
            }
        }
    }

    /**
     * 1.0.5-beta (lot 22, retour 24 : « ajuster le nombre de fabrications via la molette ») : NOMBRE DE FABRICATIONS de la recette demandee
     * {@code id} du panier de {@code player} ({@code times} borne a {@link RecipeAsk#MAX_TIMES} ; 0 retire la recette ET sa part du panier) :
     * la part de la recette sort du panier, puis le plan est refait pour {@code times} fabrications sur le stock REEL (moins la file et le
     * reste du panier) ; ce qui manque n'est pas ajoute (l'avis et la recette le disent).
     */
    public CounterCart.Notice craftTimes(UUID player, int id, int times) {
        CounterCart c = cart(player);
        CounterCart.Craft k = c.craft(id);
        if (k == null) {
            return CounterCart.Notice.NONE;
        }
        int at = c.crafts.indexOf(k);
        c.crafts.remove(at);
        //La part de la recette sort du panier (bornee par ce qui y est encore)
        for (Map.Entry<StockKey, Long> e : k.contrib.entrySet()) {
            ItemStack p = k.protos.get(e.getKey());
            if (p != null) {
                cartSet(player, p, (int) Math.max(0, c.count(p) - e.getValue()));
            }
        }
        k.contrib.clear();
        int t = Math.max(0, Math.min(RecipeAsk.MAX_TIMES, times));
        CounterCart.Notice n;
        if (t == 0) {
            n = new CounterCart.Notice("gui.pioneers.warehouse.craft.removed", 0, -1, "", 0);
        } else {
            RecipeAsk.Plan<StockKey> plan = RecipeAsk.plan(k.lines, t, key -> {
                ItemStack p = k.protos.get(key);
                return p == null ? 0 : available(p) - c.count(p);
            });
            boolean full = false;
            for (Map.Entry<StockKey, Long> e : plan.byKey().entrySet()) {
                ItemStack p = k.protos.get(e.getKey());
                if (p == null) {
                    continue;
                }
                long before = c.count(p);
                if (!cartSet(player, p, (int) Math.min(CounterCart.MAX_COUNT, before + e.getValue()))) {
                    full = true;
                    break;
                }
                k.contrib.merge(e.getKey(), c.count(p) - before, Long::sum);
            }
            k.contrib.values().removeIf(v -> v <= 0);
            k.times = t;
            k.missing = plan.missingTotal();
            c.crafts.add(Math.min(at, c.crafts.size()), k);
            n = plan.complete() && !full ? new CounterCart.Notice("gui.pioneers.warehouse.craft.set", t, -1, "", 1)
                                         : new CounterCart.Notice("gui.pioneers.warehouse.craft.short", t, plan.missingTotal(),
                                               full ? "gui.pioneers.warehouse.reason.cart" : "gui.pioneers.warehouse.reason.short", 2);
        }
        c.say(n);
        machine.changed();
        return n;
    }

    /** Objets de ce type dans les colis qui viennent a cette station. */
    public long inbound(ItemStack proto) {
        long n = 0;
        for (Parcel p : parcels) {
            if (p.kind == Parcel.Kind.REQUEST && p.towardCounter() && ItemStack.isSameItemSameTags(p.proto, proto)) {
                n += p.count;
            }
        }
        return n;
    }

    /** Regles « garder N » (interface de sortie). */
    public List<Keep> keeps() {
        return keeps;
    }

    /** Fixe une regle « garder N » (0 la retire) ; faux si ce n'est pas une interface de sortie ou s'il y a deja {@link #MAX_KEEPS} regles. */
    public boolean keepSet(ItemStack proto, int count) {
        if (tile.role() != CounterTile.Role.OUTLET || proto.isEmpty()) {
            return false;
        }
        int n = Math.max(0, Math.min(MAX_KEEP, count));
        for (Iterator<Keep> it = keeps.iterator(); it.hasNext(); ) {
            Keep k = it.next();
            if (ItemStack.isSameItemSameTags(k.proto, proto)) {
                if (n == 0) {
                    it.remove();
                } else {
                    k.count = n;
                }
                machine.changed();
                tile.syncSoon();
                return true;
            }
        }
        if (n == 0) {
            return true;
        }
        if (keeps.size() >= MAX_KEEPS) {
            return false;
        }
        keeps.add(new Keep(proto, n));
        machine.changed();
        tile.syncSoon();
        return true;
    }

    /**
     * Interface de sortie : chaque regle « garder N » demande ce qui manque (ce qui est dans ses cases, en file et en route compte), borne
     * par ce que ses cases peuvent encore recevoir et par le stock ; pas assez de stock : {@link #W_STOCK}.
     */
    private void keepTick() {
        boolean shortNow = false;
        for (Keep k : keeps) {
            long have = tile.held(k.proto) + queued(k.proto) + inbound(k.proto);
            if (have >= k.count) {
                continue;
            }
            long want = Math.min(k.count - have, Math.max(0, tile.room(k.proto) - queued(k.proto) - inbound(k.proto)));
            if (want <= 0) {
                continue;
            }
            long can = Math.min(want, available(k.proto));
            if (can > 0) {
                request(k.proto, (int) can);
            }
            if (can < want) {
                shortNow = true;
            }
        }
        if (shortNow != keepShort) {
            keepShort = shortNow;
            machine.changed();
        }
    }

    /**
     * POURQUOI la file (ou un envoi) attend, dans l'ordre ou le joueur peut y faire quelque chose : pas relie, corde arretee, colonne
     * bouchee, plus de place dans les caisses (envois), 24 colis deja en route, sortie pleine ; interface de sortie : pas assez de stock pour
     * une regle.
     */
    public int waitCode() {
        long sending = 0;
        for (int i = 0; i < tile.sendSlots().getSlots(); i++) {
            sending += tile.sendSlots().getStackInSlot(i).getCount();
        }
        if (queue.isEmpty() && sending == 0 && !keepShort) {
            return W_NONE;
        }
        Diagnosis d = diagnosis();
        if (!d.linked()) {
            return W_LINK;
        }
        if (d.code() == D_STILL) {
            return W_STILL;
        }
        if ("warehouse.pioneers.counter.column".equals(notice)) {
            return W_COLUMN;
        }
        if (sending > 0 && "warehouse.pioneers.counter.full".equals(notice)) {
            return W_ROOM;
        }
        if (parcels.size() >= MAX_PARCELS) {
            return W_BUSY;
        }
        if (!queue.isEmpty()) {
            boolean room = false;
            for (Request r : queue) {
                room |= tile.room(r.proto) - inbound(r.proto) > 0;
            }
            if (!room) {
                return W_FULL;
            }
        }
        return keepShort ? W_STOCK : W_NONE;
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        Level level = level();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        WarehouseNetwork.Graph g = graph();
        boolean changed = false;
        for (Iterator<Parcel> it = parcels.iterator(); it.hasNext(); ) {
            Parcel p = it.next();
            int r = step(server, p);
            if (r < 0) {
                it.remove();
                changed = true;
            } else if (r > 0) {
                changed = true;
            }
        }
        long now = server.getGameTime();
        PulleyTile st = tile.station();
        if (tile.role() == CounterTile.Role.OUTLET && !keeps.isEmpty() && now % KEEP_EVERY == 0 && st != null && g.contains(st.getBlockPos())) {
            keepTick();
        }
        if (st != null && g.contains(st.getBlockPos()) && st.spin() != 0 && parcels.size() < MAX_PARCELS && now - lastDispatch >= SPACING) {
            if (dispatchRequest(server, g) || dispatchSend(server, g)) {
                lastDispatch = now;
                changed = true;
            }
        }
        boolean nowMoving = !parcels.isEmpty() && st != null && st.spin() != 0;
        if (changed || nowMoving != moving || nowMoving && now - lastResync >= 40) {
            moving = nowMoving;
            lastResync = now;
            tile.syncSoon();
        }
        if (changed) {
            machine.changed();
        }
    }

    /** Avance un colis ; 0 : rien de notable, 1 : phase changee, -1 : colis termine (a retirer). */
    private int step(ServerLevel level, Parcel p) {
        if (p.route.isEmpty()) {
            return -1;
        }
        BlockPos at = p.phase == Parcel.Phase.TRAVEL ? null : p.phase == Parcel.Phase.RISE ? p.startStation() : p.endStation();
        LineNode wheel = wheelFor(level, p, at);
        int spin = wheel == null ? 0 : wheel.spin();
        switch (p.phase) {
            case RISE -> {
                //1.0.4-beta (lot 2) : la colonne de la station doit etre libre (descente au filin) au moment ou le colis la parcourt
                WarehouseLines.Station st = WarehouseLines.station(level, p.startStation());
                if (spin == 0 || st == null) {
                    if (st == null) {
                        blocked(p.startStation());
                    }
                    return 0;
                }
                if (++p.timer >= Parcel.riseTicks(st.gap())) {
                    p.phase = Parcel.Phase.TRAVEL;
                    p.timer = 0;
                    return 1;
                }
                return 0;
            }
            case TRAVEL -> {
                if (spin == 0) {
                    return 0;
                }
                ParcelRoute geo = p.geometry(level, spin);
                p.s += wheel.ropeSpeed();
                if (p.s >= geo.length()) {
                    p.s = geo.length();
                    p.phase = Parcel.Phase.LOWER;
                    p.timer = 0;
                    return 1;
                }
                return 0;
            }
            case LOWER -> {
                WarehouseLines.Station st = WarehouseLines.station(level, p.endStation());
                if (spin == 0 || st == null) {
                    if (st == null) {
                        blocked(p.endStation());
                    }
                    return 0;
                }
                if (++p.timer < Parcel.lowerTicks(st.gap())) {
                    return 0;
                }
                return arrive(level, p, st);
            }
            case OPEN -> {
                if (++p.timer < Parcel.OPEN_TICKS) {
                    return 0;
                }
                //Au comptoir : les objets passent dans les cases (sortie pour une demande, envoi pour un retour)
                if (!p.empty()) {
                    int left = p.kind == Parcel.Kind.REQUEST ? tile.deliver(p.proto, p.count) : tile.giveBack(p.proto, p.count);
                    if (left != p.count && p.kind == Parcel.Kind.REQUEST) {
                        BronzeEvents.award(level, machine.pos(), "warehouse_parcel");
                    }
                    p.count = left;
                    if (left > 0) {
                        p.timer = Parcel.OPEN_TICKS - 10;     //Cases pleines : on reessaie
                        return 0;
                    }
                }
                if (p.kind == Parcel.Kind.REQUEST && p.leg == 0) {
                    p.turnBack();
                    return 1;
                }
                return -1;
            }
        }
        return 0;
    }

    /** Une station dont la colonne est bouchee : le colis attend, le comptoir le dit. */
    private void blocked(BlockPos station) {
        if (!"warehouse.pioneers.counter.column".equals(notice)) {
            notice = "warehouse.pioneers.counter.column";
            machine.changed();
        }
    }

    /** Roue qui mene le colis : celle de la station (montee, descente) ou du troncon en cours. */
    @Nullable
    private LineNode wheelFor(ServerLevel level, Parcel p, @Nullable BlockPos station) {
        BlockPos pos = station;
        if (pos == null) {
            ParcelRoute geo = p.geometry != null ? p.geometry : null;
            int i = geo == null ? 0 : geo.pulleyAt(p.s);
            pos = p.route.get(Math.max(0, Math.min(p.route.size() - 1, i)));
        }
        return level.isLoaded(pos) ? LineNode.at(level, pos) : null;
    }

    /** Fin de descente : au comptoir le couvercle s'ouvre ; a une caisse le colis entre (rangement) puis repart, ou disparait. */
    private int arrive(ServerLevel level, Parcel p, WarehouseLines.Station st) {
        if ("warehouse.pioneers.counter.column".equals(notice)) {
            notice = "";
        }
        if (p.towardCounter()) {
            p.phase = Parcel.Phase.OPEN;
            p.timer = 0;
            level.playSound(null, machine.pos(), SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5F, 1.3F);
            return 1;
        }
        BlockPos hatch = st.block();
        CrateHatchTile crate = level.getBlockEntity(hatch) instanceof CrateHatchTile h && h.formed() ? h : null;
        if (!p.empty() && crate != null) {
            long n = crate.storage().insert(p.proto, p.count, false);
            p.count -= (int) n;
        }
        level.playSound(null, hatch, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.4F, 0.8F);
        if (p.kind == Parcel.Kind.SEND && p.leg == 0) {
            //Retour au comptoir avec ce qui n'a pas trouve de place (souvent rien)
            p.turnBack();
            return 1;
        }
        if (!p.empty()) {
            //Retour a vide qui rapporte quelque chose : jamais perdu
            dropAt(level, Vec(hatch.above()), p);
        }
        return -1;
    }

    private static net.minecraft.world.phys.Vec3 Vec(BlockPos pos) {
        return net.minecraft.world.phys.Vec3.atCenterOf(pos);
    }

    private void dropAt(ServerLevel level, net.minecraft.world.phys.Vec3 at, Parcel p) {
        int left = p.count;
        while (left > 0) {
            int n = Math.min(left, p.proto.getMaxStackSize());
            level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, p.proto.copyWithCount(n)));
            left -= n;
        }
        p.count = 0;
    }

    /** Depart d'un colis pour la premiere demande servable ; vrai si un colis est parti. */
    private boolean dispatchRequest(ServerLevel level, WarehouseNetwork.Graph g) {
        BlockPos home = tile.station().getBlockPos();
        for (Iterator<Request> it = queue.iterator(); it.hasNext(); ) {
            Request r = it.next();
            //Place dans la sortie, colis deja en route vers le comptoir compris
            int room = tile.room(r.proto);
            for (Parcel p : parcels) {
                if (p.towardCounter() && ItemStack.isSameItemSameTags(p.proto, r.proto)) {
                    room -= p.count;
                }
            }
            if (room <= 0) {
                continue;
            }
            CrateHatchTile best = null;
            long bestCount = 0;
            for (CrateHatchTile h : g.hatches(level)) {
                long c = h.storage().count(r.proto);
                if (c > bestCount && g.stationOf(h.getBlockPos()) != null && g.route(g.stationOf(h.getBlockPos()), home) != null) {
                    best = h;
                    bestCount = c;
                }
            }
            if (best == null) {
                notice = "warehouse.pioneers.counter.none";
                it.remove();
                machine.changed();
                continue;
            }
            int n = (int) Math.min(Math.min(r.left, room), Math.min(bestCount, (long) Parcel.PILES * r.proto.getMaxStackSize()));
            if (n <= 0) {
                continue;
            }
            List<BlockPos> route = g.route(g.stationOf(best.getBlockPos()), home);
            long got = best.storage().extract(r.proto, n, false);
            if (got <= 0) {
                continue;
            }
            Parcel p = new Parcel(nextId++, Parcel.Kind.REQUEST);
            p.route.addAll(route);
            p.proto = r.proto.copyWithCount(1);
            p.count = (int) got;
            parcels.add(p);
            r.left -= (int) got;
            if (r.left <= 0) {
                it.remove();
            }
            notice = "";
            level.playSound(null, best.getBlockPos(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.35F, 1.4F);
            return true;
        }
        return false;
    }

    /** Depart d'un colis des cases d'envoi vers une caisse qui a la place ; vrai si un colis est parti. */
    private boolean dispatchSend(ServerLevel level, WarehouseNetwork.Graph g) {
        BlockPos home = tile.station().getBlockPos();
        for (int i = 0; i < tile.sendSlots().getSlots(); i++) {
            ItemStack s = tile.sendSlots().getStackInSlot(i);
            if (s.isEmpty()) {
                continue;
            }
            CrateHatchTile best = null;
            long bestRoom = 0;
            for (CrateHatchTile h : g.hatches(level)) {
                long room = h.storage().room(s);
                //Le type deja present d'abord (moins de types occupes)
                if (room > 0 && h.storage().count(s) > 0) {
                    room += Long.MAX_VALUE / 4;
                }
                if (room > bestRoom && g.stationOf(h.getBlockPos()) != null && g.route(home, g.stationOf(h.getBlockPos())) != null) {
                    best = h;
                    bestRoom = room;
                }
            }
            if (best == null) {
                notice = "warehouse.pioneers.counter.full";
                continue;
            }
            ItemStack taken = tile.sendSlots().extractItem(i, (int) Math.min(s.getCount(), best.storage().room(s)), false);
            if (taken.isEmpty()) {
                continue;
            }
            Parcel p = new Parcel(nextId++, Parcel.Kind.SEND);
            p.route.addAll(g.route(home, g.stationOf(best.getBlockPos())));
            p.proto = taken.copyWithCount(1);
            p.count = taken.getCount();
            parcels.add(p);
            notice = "";
            return true;
        }
        return false;
    }

    /** Le reseau a change : un colis dont une corde de la course a disparu tombe (son contenu au sol, la ou il etait). */
    private void validateRoutes(Level level) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        for (Iterator<Parcel> it = parcels.iterator(); it.hasNext(); ) {
            Parcel p = it.next();
            boolean ok = true;
            for (int i = 0; i + 1 < p.route.size() && ok; i++) {
                BlockPos a = p.route.get(i), b = p.route.get(i + 1);
                LineNode na = server.isLoaded(a) && server.isLoaded(b) ? LineNode.at(server, a) : null;
                ok = na != null && na.linkedTo(b);
            }
            if (!ok) {
                net.minecraft.world.phys.Vec3 at = Vec(tile.getBlockPos().above());
                if (p.geometry != null) {
                    double[] pt = new double[7];
                    p.geometry.point(p.s, pt);
                    at = new net.minecraft.world.phys.Vec3(pt[0], pt[6] - 0.5, pt[1]);
                }
                if (!p.empty()) {
                    dropAt(server, at, p);
                }
                it.remove();
                tile.syncSoon();
            }
        }
    }

    /** Casse du comptoir : contenu des colis en route. */
    public void spill(Consumer<ItemStack> drop) {
        for (Parcel p : parcels) {
            int left = p.count;
            while (left > 0 && !p.proto.isEmpty()) {
                int n = Math.min(left, p.proto.getMaxStackSize());
                drop.accept(p.proto.copyWithCount(n));
                left -= n;
            }
        }
        parcels.clear();
    }

    /** Boite de rendu du comptoir : toutes les courses (colis visibles meme loin du comptoir). */
    public AABB renderBox(BlockPos pos) {
        AABB box = new AABB(pos).inflate(2);
        for (Parcel p : parcels) {
            for (BlockPos q : p.route) {
                box = box.minmax(new AABB(q).inflate(1.5));
            }
        }
        return box;
    }

    // ------------------------------------------------------------------ interface, sauvegarde, client

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(ROPE, () -> Math.round(ropeSpeed() * 20 * 100))
              .add(PARCELS, parcels::size)
              .add(QUEUE, () -> queue.stream().mapToLong(r -> r.left).sum())
              .add(SENDING, () -> {
                  long n = 0;
                  for (int i = 0; i < tile.sendSlots().getSlots(); i++) {
                      n += tile.sendSlots().getStackInSlot(i).getCount();
                  }
                  return n;
              })
              .add(CRATES, () -> level() == null ? 0 : graph().hatches(level()).size())
              .add(LINKED, () -> tile.station() == null ? 0 : graph().links().size())
              .add(DIAG, () -> diagnosis().code())
              .add(DIAG_A, () -> diagnosis().a())
              .add(DIAG_B, () -> diagnosis().b())
              .add(DIAG_C, () -> diagnosis().c())
              .add(WAIT, this::waitCode)
              .add(INBOUND, () -> parcels.stream().filter(p -> p.kind == Parcel.Kind.REQUEST && p.towardCounter() && p.count > 0).count());
    }

    @Override
    public void save(CompoundTag tag) {
        CompoundTag w = new CompoundTag();
        ListTag ps = new ListTag();
        parcels.forEach(p -> ps.add(p.save()));
        w.put("parcels", ps);
        ListTag qs = new ListTag();
        for (Request r : queue) {
            CompoundTag t = new CompoundTag();
            t.put("item", r.proto.save(new CompoundTag()));
            t.putInt("left", r.left);
            qs.add(t);
        }
        w.put("queue", qs);
        w.putInt("next", nextId);
        ListTag cs = new ListTag();
        carts.forEach((id, c) -> {
            if (!c.lines.isEmpty() || c.dest != null) {
                CompoundTag t = c.save();
                t.putUUID("player", id);
                cs.add(t);
            }
        });
        w.put("carts", cs);
        //1.0.5-beta (lot 17) : regles « garder N » de l'interface de sortie
        if (!keeps.isEmpty()) {
            w.put("keep", saveKeeps());
        }
        tag.put("warehouse", w);
    }

    @Override
    public void load(CompoundTag tag) {
        CompoundTag w = tag.getCompound("warehouse");
        parcels.clear();
        ListTag ps = w.getList("parcels", Tag.TAG_COMPOUND);
        for (int i = 0; i < ps.size(); i++) {
            parcels.add(Parcel.load(ps.getCompound(i)));
        }
        queue.clear();
        ListTag qs = w.getList("queue", Tag.TAG_COMPOUND);
        for (int i = 0; i < qs.size(); i++) {
            ItemStack proto = ItemStack.of(qs.getCompound(i).getCompound("item"));
            if (!proto.isEmpty()) {
                queue.add(new Request(proto.copyWithCount(1), qs.getCompound(i).getInt("left")));
            }
        }
        nextId = Math.max(1, w.getInt("next"));
        //Les paniers ouverts gardent leur numero de paquet et leur avis (rechargement au cours d'une partie)
        ListTag cs = w.getList("carts", Tag.TAG_COMPOUND);
        Map<UUID, CounterCart> fresh = new java.util.HashMap<>();
        for (int i = 0; i < cs.size(); i++) {
            CompoundTag t = cs.getCompound(i);
            if (t.hasUUID("player")) {
                CounterCart c = CounterCart.load(t);
                CounterCart old = carts.get(t.getUUID("player"));
                if (old != null) {
                    c.seq = old.seq;
                    c.notice = old.notice;
                    c.noticeId = old.noticeId;
                }
                fresh.put(t.getUUID("player"), c);
            }
        }
        carts.clear();
        carts.putAll(fresh);
        loadKeeps(w.getList("keep", Tag.TAG_COMPOUND));
        graphGeneration = -1;
    }

    /** Client : colis en route (vues extrapolees par le rendu), heure du serveur a l'envoi. */
    @Override
    public void writeClient(CompoundTag tag) {
        ListTag ps = new ListTag();
        for (Parcel p : parcels) {
            CompoundTag t = p.save();
            ps.add(t);
        }
        tag.put("parcels", ps);
        tag.putString("notice", notice);
        tag.put("keep", saveKeeps());
    }

    private ListTag saveKeeps() {
        ListTag ks = new ListTag();
        for (Keep k : keeps) {
            CompoundTag t = new CompoundTag();
            t.put("item", k.proto.save(new CompoundTag()));
            t.putInt("n", k.count);
            ks.add(t);
        }
        return ks;
    }

    private void loadKeeps(ListTag ks) {
        keeps.clear();
        for (int i = 0; i < ks.size() && keeps.size() < MAX_KEEPS; i++) {
            ItemStack proto = ItemStack.of(ks.getCompound(i).getCompound("item"));
            int n = Math.min(MAX_KEEP, ks.getCompound(i).getInt("n"));
            if (!proto.isEmpty() && n > 0) {
                keeps.add(new Keep(proto, n));
            }
        }
    }

    @Override
    public void readClient(CompoundTag tag) {
        List<Parcel> fresh = new ArrayList<>();
        ListTag ps = tag.getList("parcels", Tag.TAG_COMPOUND);
        for (int i = 0; i < ps.size(); i++) {
            fresh.add(Parcel.load(ps.getCompound(i)));
        }
        //Garde la geometrie et l'avance locale des colis deja connus (pas de saut si l'ecart est petit)
        for (Parcel f : fresh) {
            for (Parcel old : parcels) {
                if (old.id == f.id && old.phase == f.phase && old.leg == f.leg) {
                    f.geometry = old.geometry;
                    f.geometrySpin = old.geometrySpin;
                    f.clientS = Math.abs(old.clientS - f.s) < 0.6 ? old.clientS : f.s;
                    f.clientTimer = old.clientTimer;
                }
            }
            if (Double.isNaN(f.clientS)) {
                f.clientS = f.s;
                f.clientTimer = f.timer;
            }
        }
        parcels.clear();
        parcels.addAll(fresh);
        notice = tag.getString("notice");
        loadKeeps(tag.getList("keep", Tag.TAG_COMPOUND));
    }
}

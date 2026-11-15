package fr.pioneers.logistics.warehouse;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.tech.bronze.BronzeTile;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.tech.kinetics.rope.PulleyTile;
import fr.pioneers.core.network.PacketWarehouse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 1) : tile du COMPTOIR D'ENTREPOT. Porte son unite ({@link CounterUnit} : file des demandes, colis en route, envois) ;
 * garde les 9 CASES DE SORTIE (le joueur y prend, un entonnoir dessous les vide ; rien n'y entre de l'exterieur) et 9 CASES D'ENVOI
 * invisibles (seule l'automatisation y range : entonnoir, cable logistique ; chaque pile part en colis vers une caisse). Par les cotes et le
 * dessous : sortie a prendre, envoi a remplir ; le dessus porte la poulie (ou la ligne passe plus haut, lot 2 : {@link #station}).
 * <p>Interface standard v2 (vue {@code warehouse}) : liste de l'entrepot (recherche, tri, quantites), sortie, etat de la corde ; lot 3 :
 * panier par joueur et « Commander » ({@link CounterCart}).
 * <p>1.0.5-beta (lot 17, Guillaume : « le comptoir ne sert qu'aux commandes ; des blocs d'interface pour les entrees / sorties ») : la meme
 * tile porte aussi les deux INTERFACES D'ENTREPOT ({@link Role}) : stations du reseau comme le comptoir (petite poulie au-dessus), meme
 * unite, memes colis. INTERFACE D'ENTREE : 9 cases ou tout entre (entonnoir, tuyau, convoyeur, joueur) et part en colis vers une caisse qui
 * a la place (cases pleines = refus propre : rien n'entre plus) ; INTERFACE DE SORTIE : 9 cases livrees par le reseau (commandes du
 * comptoir dirigees vers elle, regles « garder N » de son unite), videes par un entonnoir dessous, un tuyau, ou poussees dans l'inventaire
 * qu'elle regarde (sa goulotte). Les 9 cases d'envoi du comptoir restent (mondes existants : un entonnoir sur le cote envoie toujours).
 * <p>1.0.5-beta (lot 45, retour 65 de Guillaume : « Le port d'entree d'entrepot aura egalement un slot de sac a dos pour extraire le contenu du
 * sac pour que ce dernier parte dans l'entrepot ») : l'interface d'ENTREE a une CASE DE SAC (derniere case : index d'avant inchanges ; joueur
 * seulement) ; le contenu du sac ({@code BagView}, lu et ecrit dans son NBT) fait partie de ses cases d'envoi : l'unite le vide en colis
 * vers les caisses, sans copie.
 */
public class CounterTile extends BronzeTile {

    /** 1.0.5-beta (lot 17) : comptoir, interface d'entree, interface de sortie. */
    public enum Role {
        COUNTER, INLET, OUTLET
    }

    /**
     * Geometrie de l'interface (vue {@code warehouse}). 1.0.4-beta (lot 3, facon terminal AE2 avec panier). 1.0.5-beta (lot 17, Guillaume :
     * « interface plus petite : place pour JEI ») : largeur 350 -> {@value #WIDTH} (la colonne de JEI, 9 objets de large, tient a droite a
     * l'echelle 3 en 1920 x 1080 et a l'echelle 2 en 1366 x 768 ; calcul repris par {@code tools/check_p105_17.py}). A gauche la recherche,
     * le tri et la GRILLE du stock (8 x 6) ; a droite la SORTIE (3 x 3), le bouton PANIER (volet par-dessus la grille : lignes, quantites,
     * livraison), « COMMANDER » et la carte EN ROUTE (volet : colis, file, « Annuler ») ; en bas la ligne d'aide (ou le dernier avis).
     */
    public static final int WIDTH = 240, GRID_X = 8, GRID_Y = 34, COLS = 8, ROWS = 6, CELL = 18, RIGHT_X = 164, RIGHT_W = 70, OUT_X = 172, OUT_Y = 33;
    public static final StandardLayout.Rect SEARCH = new StandardLayout.Rect(6, StandardLayout.TOP - 2, 106, 11),
          SORT = new StandardLayout.Rect(114, StandardLayout.TOP - 2, 44, 11),
          GRID = new StandardLayout.Rect(GRID_X - 2, GRID_Y - 2, COLS * CELL + 8, ROWS * CELL + 4),
          OUT_CARD = new StandardLayout.Rect(RIGHT_X, StandardLayout.TOP - 2, RIGHT_W, 11),
          CART_BUTTON = new StandardLayout.Rect(RIGHT_X, 90, RIGHT_W, 13),
          ORDER = new StandardLayout.Rect(RIGHT_X, 105, RIGHT_W, 14),
          STATUS = new StandardLayout.Rect(RIGHT_X, 121, RIGHT_W, GRID_Y - 2 + ROWS * CELL + 4 - 121),
          LINE = new StandardLayout.Rect(6, GRID_Y - 2 + ROWS * CELL + 5, WIDTH - 12, 5);
    /**
     * 1.0.5-beta (lot 17) : VOLET (panier ou « En route ») ouvert PAR-DESSUS la recherche et la grille (comme le carre du selecteur : rien
     * ne bouge, la sortie et « Commander » restent a portee) ; lignes du panier ({@value #CART_ROWS} de {@value #CART_ROW}).
     */
    public static final StandardLayout.Rect VOLET = new StandardLayout.Rect(5, StandardLayout.TOP - 3, GRID_X - 2 + COLS * CELL + 8 - 5 + 2, 126);
    public static final int CART_ROW = 14, CART_ROWS = 6, CART_TOP = VOLET.y() + 16;
    /**
     * 1.0.5-beta (lot 22, retour 24) : hauteur d'une RECETTE DEMANDEE dans le volet du panier (produit et fabrications, puis ses ingredients
     * en petites icones) ; zone des rangees du volet (recettes puis lignes) : {@value #CART_AREA} px.
     */
    public static final int CRAFT_ROW = 21, CART_AREA = CART_ROWS * CART_ROW;

    /**
     * 1.0.5-beta (lot 17) : interfaces d'entree et de sortie (vue {@code warehouse_io}, largeur standard) : 9 cases a gauche, carte a droite
     * (sortie : regles « garder N » ; entree : etat et explication), etat de la corde dessous.
     */
    public static final int IO_X = 12, IO_Y = 33;
    public static final StandardLayout.Rect IO_CARD = new StandardLayout.Rect(8, StandardLayout.TOP - 2, 62, 11),
          IO_STATUS = new StandardLayout.Rect(8, 91, 62, 53),
          IO_SIDE = new StandardLayout.Rect(76, StandardLayout.TOP - 2, StandardLayout.WIDTH - 8 - 76, 124),
          IO_LINE = new StandardLayout.Rect(6, 145, StandardLayout.WIDTH - 12, 5);
    /** Lignes des regles « garder N » de l'interface de sortie (carte {@link #IO_SIDE}). */
    public static final int KEEP_TOP = IO_SIDE.y() + 14, KEEP_ROW = 14;
    /**
     * 1.0.5-beta (lot 45, retour 65) : interface d'entree : carte de droite raccourcie ({@link #IO_SIDE_INLET}) ; dessous, la case de sac
     * ({@link #BAG_X}, {@link #BAG_Y}) et sa carte ({@link #IO_BAG} : « Sac a vider », ce qui reste a envoyer), cote a cote, sans se chevaucher.
     */
    public static final StandardLayout.Rect IO_SIDE_INLET = new StandardLayout.Rect(IO_SIDE.x(), IO_SIDE.y(), IO_SIDE.w(), IO_SIDE.h() - 26);
    public static final int BAG_X = IO_SIDE.x() + 8, BAG_Y = IO_SIDE_INLET.y() + IO_SIDE_INLET.h() + 4;
    public static final StandardLayout.Rect IO_BAG = new StandardLayout.Rect(BAG_X + 19, BAG_Y - 2, IO_SIDE.x() + IO_SIDE.w() - (BAG_X + 19), 20);
    /** Actions : vider la file des demandes. */
    public static final int CLEAR_QUEUE = CUSTOM;
    /** 1.0.5-beta (lot 17) : lignes au plus par paquet de la liste par differences ({@link StockSync}). */
    public static final int MAX_DELTA = 1024;
    /** 1.0.5-beta (lot 17) : interfaces de sortie proposees comme destination d'une commande, au plus. */
    public static final int MAX_DESTS = 8;
    /** 1.0.5-beta (lot 17) : l'interface de sortie pousse dans l'inventaire qu'elle regarde toutes les {@value #PUSH_EVERY} ticks. */
    public static final int PUSH_EVERY = 10;

    private final Role role;
    private final List<ItemSlot> outputs = new ArrayList<>();
    private final ItemStackHandler send = new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final CounterUnit unit;
    private final LazyOptional<IItemHandler> sides = LazyOptional.of(this::sideHandler);
    /** Interface d'entree : ses 9 cases vues par l'unite (envois) ; vues de l'exterieur (entonnoirs : on y range, on n'y prend pas). */
    private final IItemHandler inletSend, outside;
    /** 1.0.5-beta (lot 45) : interface d'entree : case du sac et son contenu (null ailleurs). */
    @Nullable
    private final ItemSlot bagSlot;
    @Nullable
    private final fr.pioneers.building.home.bag.BagView bag;
    private static final IItemHandler NOTHING = new ItemStackHandler(0);

    public CounterTile(BlockPos pos, BlockState state) {
        this(PiWarehouse.COUNTER_TILE.get(), pos, state, Role.COUNTER);
    }

    /** 1.0.5-beta (lot 17) : interface d'entree. */
    public static CounterTile inlet(BlockPos pos, BlockState state) {
        return new CounterTile(PiWarehouse.INLET_TILE.get(), pos, state, Role.INLET);
    }

    /** 1.0.5-beta (lot 17) : interface de sortie. */
    public static CounterTile outlet(BlockPos pos, BlockState state) {
        return new CounterTile(PiWarehouse.OUTLET_TILE.get(), pos, state, Role.OUTLET);
    }

    protected CounterTile(BlockEntityType<?> type, BlockPos pos, BlockState state, Role role) {
        super(type, pos, state);
        this.role = role;
        int x0 = role == Role.COUNTER ? OUT_X : IO_X, y0 = role == Role.COUNTER ? OUT_Y : IO_Y;
        for (int i = 0; i < 9; i++) {
            int x = x0 + (i % 3) * 18, y = y0 + (i / 3) * 18;
            if (role == Role.INLET) {
                //Entree : tout y entre (joueur, entonnoir, tuyau) ; seuls le joueur et l'unite en reprennent
                outputs.add(slot(new ItemSlot(() -> 64, s -> true, (s, a) -> true, (s, a) -> a != Automation.EXTERNAL, this::changed, x, y)));
            } else {
                outputs.add(slot(new ItemSlot(() -> 64, s -> true, (s, a) -> a == Automation.INTERNAL, (s, a) -> true, this::changed, x, y)));
            }
        }
        outside = new ItemSlots(outputs, Automation.EXTERNAL);
        unit = carry(new CounterUnit(host(), this));
        if (role == Role.INLET) {
            //1.0.5-beta (lot 45, retour 65) : case de sac en DERNIER (mondes existants : les 9 cases gardent leur index)
            bagSlot = slot(fr.pioneers.building.home.bag.BagInterfaceTile.bagSlot(this::changed, BAG_X, BAG_Y));
            bag = new fr.pioneers.building.home.bag.BagView(bagSlot::getStack, this::changed);
            inletSend = new SendWithBag(new ItemSlots(outputs, Automation.INTERNAL), bag);
        } else {
            bagSlot = null;
            bag = null;
            inletSend = new ItemSlots(outputs, Automation.INTERNAL);
        }
    }

    /** 1.0.5-beta (lot 45) : case de sac de l'interface d'entree (null ailleurs) et contenu du sac. */
    @Nullable
    public ItemSlot bagSlot() {
        return bagSlot;
    }

    @Nullable
    public fr.pioneers.building.home.bag.BagView bagContents() {
        return bag;
    }

    /**
     * 1.0.5-beta (lot 45, retour 65) : cases d'envoi de l'interface d'entree : ses 9 cases, puis le CONTENU du sac pose (vue du NBT du sac :
     * l'unite y prend directement de quoi faire ses colis, un colis rapporte peut y revenir) ; rien n'est copie.
     */
    private record SendWithBag(IItemHandler slots, IItemHandler bag) implements IItemHandler {

        @Override
        public int getSlots() {
            return slots.getSlots() + bag.getSlots();
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            int n = slots.getSlots();
            return slot < n ? slots.getStackInSlot(slot) : bag.getStackInSlot(slot - n);
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            int n = slots.getSlots();
            return slot < n ? slots.insertItem(slot, stack, simulate) : bag.insertItem(slot - n, stack, simulate);
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int n = slots.getSlots();
            return slot < n ? slots.extractItem(slot, amount, simulate) : bag.extractItem(slot - n, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            int n = slots.getSlots();
            return slot < n ? slots.getSlotLimit(slot) : bag.getSlotLimit(slot - n);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            int n = slots.getSlots();
            return slot < n ? slots.isItemValid(slot, stack) : bag.isItemValid(slot - n, stack);
        }
    }

    public Role role() {
        return role;
    }

    public CounterUnit counter() {
        return unit;
    }

    /** Les 9 cases visibles : sortie du comptoir ou de l'interface de sortie ; cases d'entree de l'interface d'entree. */
    public List<ItemSlot> outputs() {
        return outputs;
    }

    /**
     * Cases d'ENVOI vues par l'unite : les 9 cases invisibles du comptoir, les 9 cases de l'interface d'entree ; rien pour l'interface de
     * sortie.
     */
    public IItemHandler sendSlots() {
        return role == Role.COUNTER ? send : role == Role.INLET ? inletSend : NOTHING;
    }

    public net.minecraft.core.Direction facing() {
        return getBlockState().getValue(CounterBlock.FACING);
    }

    /** Derniere recherche de la poulie (tick du monde) : la colonne se relit une fois par tick au plus. */
    private long stationTick = Long.MIN_VALUE;
    @Nullable
    private PulleyTile stationCache;

    /**
     * Poulie d'entrepot qui dessert le comptoir (station) : posee dessus (lot 1) ou plus haut dans la ligne, jusqu'a
     * {@link WarehouseLines#MAX_DROP} blocs de vide (lot 2 : le comptoir se pose au sol sous la ligne) ; ou null.
     */
    @Nullable
    public PulleyTile station() {
        if (level == null) {
            return null;
        }
        long now = level.getGameTime();
        if (now != stationTick || stationCache != null && stationCache.isRemoved()) {
            stationTick = now;
            stationCache = WarehouseLines.pulleyAbove(level, worldPosition);
        }
        return stationCache;
    }

    /** Range dans les cases de sortie ce qui y tient ; renvoie le reste (l'interface d'entree ne recoit rien). */
    public int deliver(ItemStack proto, int count) {
        if (role == Role.INLET) {
            return count;
        }
        int left = count;
        for (ItemSlot slot : outputs) {
            if (left <= 0) {
                break;
            }
            ItemStack offer = proto.copyWithCount(Math.min(left, proto.getMaxStackSize()));
            ItemStack rest = slot.insert(offer, fr.pioneers.core.Action.EXECUTE, Automation.INTERNAL);
            left -= offer.getCount() - rest.getCount();
        }
        return left;
    }

    /** Place libre pour ce type dans les cases de sortie (0 pour l'interface d'entree). */
    public int room(ItemStack proto) {
        if (role == Role.INLET) {
            return 0;
        }
        int room = 0;
        for (ItemSlot slot : outputs) {
            ItemStack s = slot.getStack();
            if (s.isEmpty()) {
                room += proto.getMaxStackSize();
            } else if (ItemStack.isSameItemSameTags(s, proto)) {
                room += Math.max(0, s.getMaxStackSize() - s.getCount());
            }
        }
        return room;
    }

    /** Objets de ce type dans les 9 cases. */
    public int held(ItemStack proto) {
        int n = 0;
        for (ItemSlot slot : outputs) {
            if (ItemStack.isSameItemSameTags(slot.getStack(), proto)) {
                n += slot.getStack().getCount();
            }
        }
        return n;
    }

    /** Rend aux cases d'envoi (puis de sortie) ce qu'un colis rapporte ; renvoie le reste. */
    public int giveBack(ItemStack proto, int count) {
        ItemStack rest = proto.copyWithCount(count);
        IItemHandler back = sendSlots();
        for (int i = 0; i < back.getSlots() && !rest.isEmpty(); i++) {
            rest = back.insertItem(i, rest, false);
        }
        return rest.isEmpty() ? 0 : deliver(rest, rest.getCount());
    }

    /** Derniere liste envoyee a chaque joueur qui a l'interface ouverte (empreinte : file, panier). */
    private final Map<UUID, Long> sent = new HashMap<>();
    /** 1.0.5-beta (lot 17) : stock deja connu de chaque joueur qui a l'interface ouverte (synchro par differences). */
    private final Map<UUID, StockSync<StockKey>> syncs = new HashMap<>();
    /** 1.0.5-beta (lot 17) : destinations deja envoyees a chaque joueur (empreinte). */
    private final Map<UUID, Long> destSent = new HashMap<>();

    @Override
    public void serverTick(ServerLevel level) {
        unit.tick();
        flushClient(level, 4);
        long t = level.getGameTime();
        if (role == Role.COUNTER) {
            if (t % 2 == 0) {
                sendStock(level);
            }
            if (t % 10 == 0) {
                sendLists(level);
            }
        } else if (role == Role.OUTLET && t % PUSH_EVERY == 0) {
            push(level);
        }
    }

    /** Joueurs qui ont l'interface ouverte. */
    private List<ServerPlayer> viewers(ServerLevel level) {
        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.containerMenu instanceof fr.pioneers.core.machine.MachineMenu m && m.getTile() == this) {
                viewers.add(p);
            }
        }
        return viewers;
    }

    /**
     * 1.0.5-beta (lot 17) : STOCK aux joueurs qui ont l'interface ouverte, par DIFFERENCES ({@link StockSync}) : le stock n'est relu (une
     * passe sur les caisses du reseau) que si l'empreinte des caisses a change ({@link CounterUnit#stockStamp}) ou si un paquet precedent
     * etait plein ; au plus {@link #MAX_DELTA} lignes par paquet. Les destinations possibles d'une commande partent avec (si elles changent).
     */
    private void sendStock(ServerLevel level) {
        List<ServerPlayer> viewers = viewers(level);
        syncs.keySet().removeIf(id -> viewers.stream().noneMatch(p -> p.getUUID().equals(id)));
        destSent.keySet().removeIf(id -> viewers.stream().noneMatch(p -> p.getUUID().equals(id)));
        if (viewers.isEmpty()) {
            return;
        }
        long stamp = unit.stockStamp();
        Map<StockKey, Long> now = null;
        List<BlockPos> dests = null;
        for (ServerPlayer p : viewers) {
            StockSync<StockKey> sync = syncs.computeIfAbsent(p.getUUID(), k -> new StockSync<>());
            if (dests == null) {
                dests = unit.destinations();
            }
            BlockPos dest = unit.cart(p.getUUID()).dest;
            long destStamp = dests.hashCode() * 31L + (dest == null ? 0 : dest.asLong());
            Long lastDest = destSent.get(p.getUUID());
            boolean destChanged = lastDest == null || lastDest != destStamp;
            //Le stock n'est relu que toutes les 10 ticks au plus (un entrepot qui bouge sans cesse : une passe par demi-seconde)
            boolean relook = sync.stale(stamp) && (sync.size() == 0 || level.getGameTime() % 10 == 0);
            if (!relook && !sync.pending() && !destChanged) {
                continue;
            }
            boolean reset = false;
            if (relook) {
                if (now == null) {
                    now = unit.stockByKey();
                }
                reset = sync.takeReset();
                sync.compute(now);
                sync.stamp(stamp);
            }
            //Les differences deja calculees partent par paquets (premiere ouverture d'un gros entrepot : un paquet par passe)
            List<StockSync.Change<StockKey>> delta = sync.drain(MAX_DELTA);
            if (delta.isEmpty() && !reset && !destChanged) {
                continue;
            }
            List<PacketWarehouse.Entry> entries = new ArrayList<>(delta.size());
            for (StockSync.Change<StockKey> c : delta) {
                entries.add(new PacketWarehouse.Entry(c.key().stack(), c.count()));
            }
            new PacketWarehouse.Stock(worldPosition, reset, entries, dests, dest == null ? 0 : dest.asLong()).sendTo(p);
            destSent.put(p.getUUID(), destStamp);
        }
    }

    /** File et panier aux joueurs qui ont l'interface ouverte, s'ils ont change (ou toutes les 5 s). */
    private void sendLists(ServerLevel level) {
        List<ServerPlayer> viewers = viewers(level);
        sent.keySet().removeIf(id -> viewers.stream().noneMatch(p -> p.getUUID().equals(id)));
        if (viewers.isEmpty()) {
            return;
        }
        long base = WarehouseNetwork.generation() * 31L ^ unit.queue().stream().mapToLong(r -> r.left).sum();
        List<PacketWarehouse.Entry> queued = null;
        for (ServerPlayer p : viewers) {
            //Lot 3 : le panier du joueur est dans la liste (empreinte par joueur)
            long stamp = base * 1_000_003L ^ unit.cart(p.getUUID()).stamp();
            Long last = sent.get(p.getUUID());
            if (last != null && last == stamp && level.getGameTime() % 100 != 0) {
                continue;
            }
            if (queued == null) {
                queued = queuedEntries();
            }
            //1.0.5-beta (lot 17) : le stock part a part, par differences (paquet Stock) : la liste ne porte plus que la file et le panier
            new PacketWarehouse.Contents(worldPosition, List.of(), queued, cartView(p.getUUID())).sendTo(p);
            sent.put(p.getUUID(), stamp);
        }
    }

    private List<PacketWarehouse.Entry> queuedEntries() {
        List<PacketWarehouse.Entry> queued = new ArrayList<>();
        unit.queue().forEach(r -> queued.add(new PacketWarehouse.Entry(r.proto, r.left)));
        return queued;
    }

    /** 1.0.4-beta (lot 3) : le panier de ce joueur, tel qu'il part au client. */
    private PacketWarehouse.CartView cartView(UUID player) {
        CounterCart c = unit.cart(player);
        List<PacketWarehouse.Entry> lines = new ArrayList<>();
        c.lines.forEach(l -> lines.add(new PacketWarehouse.Entry(l.proto, l.count)));
        CounterCart.Notice n = c.notice;
        //1.0.5-beta (lot 22) : recettes demandees (produit, fabrications, ce qui manque, part du panier)
        List<PacketWarehouse.CraftView> crafts = new ArrayList<>();
        for (CounterCart.Craft k : c.crafts) {
            List<PacketWarehouse.Entry> uses = new ArrayList<>();
            k.contrib.forEach((key, v) -> {
                ItemStack p = k.protos.get(key);
                if (p != null && v > 0) {
                    uses.add(new PacketWarehouse.Entry(p, v));
                }
            });
            crafts.add(new PacketWarehouse.CraftView(k.id, k.result, k.yield, k.times, k.missing, uses));
        }
        return new PacketWarehouse.CartView(lines, c.seq, n.key(), n.a(), n.b(), n.reason(), n.tone(), c.noticeId, crafts);
    }

    /** 1.0.4-beta (lot 3) : liste et panier envoyes AUSSITOT a ce joueur (apres une modification du panier ou « Commander »). */
    public void pushList(ServerPlayer player) {
        new PacketWarehouse.Contents(worldPosition, List.of(), queuedEntries(), cartView(player.getUUID())).sendTo(player);
        sent.remove(player.getUUID());
        //Le stock disponible a peut-etre change (commande partie) : la prochaine passe le relit
        destSent.remove(player.getUUID());
    }

    /**
     * 1.0.5-beta (lot 17) : l'interface de sortie POUSSE ses cases dans l'inventaire qu'elle regarde (sa goulotte : une machine, un coffre,
     * un tuyau), une pile au plus par passe et par case ; un entonnoir dessous la vide aussi.
     */
    private void push(ServerLevel level) {
        Direction front = facing();
        //Regle des clans : rien ne sort vers un bloc d'un autre territoire (frontiere de chunk)
        if (!ClanApi.canTransfer(level, worldPosition, worldPosition.relative(front))) {
            return;
        }
        BlockEntity be = level.getBlockEntity(worldPosition.relative(front));
        if (be == null) {
            return;
        }
        IItemHandler target = be.getCapability(ForgeCapabilities.ITEM_HANDLER, front.getOpposite()).orElse(null);
        if (target == null) {
            return;
        }
        for (ItemSlot slot : outputs) {
            ItemStack s = slot.getStack();
            if (s.isEmpty()) {
                continue;
            }
            ItemStack rest = ItemHandlerHelper.insertItemStacked(target, s.copy(), true);
            int moved = s.getCount() - rest.getCount();
            if (moved > 0) {
                ItemStack taken = slot.extract(moved, fr.pioneers.core.Action.EXECUTE, Automation.INTERNAL);
                ItemStack back = ItemHandlerHelper.insertItemStacked(target, taken, false);
                if (!back.isEmpty()) {
                    slot.insert(back, fr.pioneers.core.Action.EXECUTE, Automation.INTERNAL);
                }
            }
        }
    }

    /** Client : avance locale des colis (vitesse de la corde lue sur les roues du client), en attendant la prochaine synchro. */
    @Override
    public void clientTick() {
        if (level == null) {
            return;
        }
        for (Parcel p : unit.parcels()) {
            if (p.route.isEmpty()) {
                continue;
            }
            BlockPos at = p.phase == Parcel.Phase.RISE ? p.startStation() : p.endStation();
            if (p.phase == Parcel.Phase.TRAVEL) {
                ParcelRoute geo = p.geometry != null ? p.geometry : null;
                at = p.route.get(Math.max(0, Math.min(p.route.size() - 1, geo == null ? 0 : geo.pulleyAt(p.clientS))));
            }
            LineNode w = LineNode.at(level, at);
            int spin = w == null ? 0 : w.spin();
            if (p.phase == Parcel.Phase.OPEN) {
                p.clientTimer = Math.min(Parcel.OPEN_TICKS, p.clientTimer + 1);
            } else if (spin != 0) {
                if (p.phase == Parcel.Phase.TRAVEL) {
                    ParcelRoute geo = p.geometry(level, spin);
                    p.clientS = Math.min(geo.length(), p.clientS + w.ropeSpeed());
                } else {
                    int gap = WarehouseLines.gap(level, at);
                    p.clientTimer = Math.min(p.phase == Parcel.Phase.RISE ? Parcel.riseTicks(gap) : Parcel.lowerTicks(gap), p.clientTimer + 1);
                }
            }
        }
    }

    /** Synchro du client (colis, etat) au prochain passage. */
    void syncSoon() {
        markClient();
    }

    @Override
    public void dropContents(Consumer<ItemStack> drop) {
        super.dropContents(drop);
        for (int i = 0; i < send.getSlots(); i++) {
            ItemStack s = send.getStackInSlot(i);
            if (!s.isEmpty()) {
                drop.accept(s.copy());
                send.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        unit.spill(drop);
    }

    // ------------------------------------------------------------------ capacites : sortie a prendre, envoi a remplir

    private IItemHandler sideHandler() {
        if (role != Role.COUNTER) {
            //1.0.5-beta (lot 17) : interfaces : leurs 9 cases (entree : on y range ; sortie : on y prend)
            return outside;
        }
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return 18;
            }

            @NotNull
            @Override
            public ItemStack getStackInSlot(int slot) {
                return slot < 9 ? outputs.get(slot).getStack() : send.getStackInSlot(slot - 9);
            }

            @NotNull
            @Override
            public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                return slot < 9 ? stack : send.insertItem(slot - 9, stack, simulate);
            }

            @NotNull
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return slot < 9 ? outputs.get(slot).extract(amount, simulate ? fr.pioneers.core.Action.SIMULATE : fr.pioneers.core.Action.EXECUTE,
                      Automation.EXTERNAL) : ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 64;
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return slot >= 9;
            }
        };
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return side == Direction.UP ? LazyOptional.empty() : sides.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        sides.invalidate();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("send", send.serializeNBT());
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("send")) {
            send.deserializeNBT(tag.getCompound("send"));
        }
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        return unit.renderBox(worldPosition);
    }

    // ------------------------------------------------------------------ interface standard v2

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public int guiWidth() {
        return role == Role.COUNTER ? WIDTH : StandardLayout.WIDTH;
    }

    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public String guiTier() {
        return "bench";
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        if (role == Role.INLET) {
            //1.0.5-beta (lot 45) : carte raccourcie, case de sac et sa carte dessous
            return List.of(new StandardLayout.Part("card", IO_CARD), new StandardLayout.Part("card", IO_STATUS), new StandardLayout.Part("card", IO_SIDE_INLET),
                  new StandardLayout.Part("card", IO_BAG), new StandardLayout.Part("card", IO_LINE));
        }
        if (role != Role.COUNTER) {
            return List.of(new StandardLayout.Part("card", IO_CARD), new StandardLayout.Part("card", IO_STATUS), new StandardLayout.Part("card", IO_SIDE),
                  new StandardLayout.Part("card", IO_LINE));
        }
        return List.of(new StandardLayout.Part("setting", SEARCH), new StandardLayout.Part("setting", SORT), new StandardLayout.Part("card", GRID),
              new StandardLayout.Part("card", OUT_CARD), new StandardLayout.Part("setting", CART_BUTTON), new StandardLayout.Part("setting", ORDER),
              new StandardLayout.Part("card", STATUS), new StandardLayout.Part("card", LINE));
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        if (action == CLEAR_QUEUE) {
            unit.clearQueue();
        }
    }
}

package fr.pioneers.network;

import fr.pioneers.warehouse.CounterTile;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * 1.0.4-beta (lot 1) : paquets du COMPTOIR D'ENTREPOT. {@link Request} (client -> serveur) : demander N objets d'un type (verifie par
 * {@link PacketGuard#tile} : distance, interface ouverte) ; {@link Contents} (serveur -> client) : la liste de l'entrepot (types et
 * quantites, somme des caisses du reseau), envoyee seulement au joueur qui a l'interface ouverte, au plus toutes les 10 ticks et si elle a
 * change. PiNetwork.VERSION 20. Lot 3 : {@link CartEdit} (client -> serveur : panier, « Commander »), panier du joueur dans {@link Contents}
 * ({@link CartView}). 1.0.5-beta (lot 17) : {@link Stock} (stock par differences), {@link Recipe} (recette de JEI au panier), {@link DeskEdit}
 * (destination des commandes, regles « garder N ») ; la liste {@link Contents} ne porte plus que la file et le panier.
 */
public final class PacketWarehouse {

    private PacketWarehouse() {
    }

    /** Demande : {@code count} objets du type {@code proto} au comptoir en {@code pos}. */
    public record Request(BlockPos pos, ItemStack proto, int count) {

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeItem(proto.copyWithCount(1));
            buf.writeVarInt(count);
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readBlockPos(), buf.readItem(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            CounterTile tile = PacketGuard.tile(player, pos, CounterTile.class);
            if (tile != null && !proto.isEmpty()) {
                int n = Math.max(1, Math.min(count, 64 * 64));
                if (!tile.counter().request(proto, n)) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("warehouse.pioneers.counter.queue_full")
                          .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
                }
            }
            context.get().setPacketHandled(true);
        }
    }

    /** Une ligne de la liste : le type et sa quantite totale. */
    public record Entry(ItemStack proto, long count) {
    }

    /**
     * 1.0.4-beta (lot 3) : modification du PANIER du joueur au comptoir en {@code pos} ({@link fr.pioneers.warehouse.CounterCart}) :
     * {@link #SET} (quantite ABSOLUE de la ligne {@code proto}, 0 la retire), {@link #CLEAR} (vider), {@link #ORDER} (« Commander » : tout
     * le panier part dans la file, une fois). {@code seq} : numero du paquet, renvoye dans {@link CartView#seq} quand il est traite. Le
     * serveur repond aussitot par la liste ({@link Contents}) a ce joueur.
     */
    public record CartEdit(BlockPos pos, int op, ItemStack proto, int count, int seq) {

        public static final int SET = 0, CLEAR = 1, ORDER = 2;

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(op);
            buf.writeItem(proto.isEmpty() ? ItemStack.EMPTY : proto.copyWithCount(1));
            buf.writeVarInt(Math.max(0, count));
            buf.writeInt(seq);
        }

        public static CartEdit decode(FriendlyByteBuf buf) {
            return new CartEdit(buf.readBlockPos(), buf.readVarInt(), buf.readItem(), buf.readVarInt(), buf.readInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            CounterTile tile = PacketGuard.tile(player, pos, CounterTile.class);
            if (tile != null) {
                fr.pioneers.warehouse.CounterUnit unit = tile.counter();
                switch (op) {
                    case SET -> unit.cartSet(player.getUUID(), proto, count);
                    case CLEAR -> unit.cartClear(player.getUUID());
                    case ORDER -> unit.order(player.getUUID());
                    default -> {
                    }
                }
                unit.cart(player.getUUID()).seq = seq;
                tile.pushList(player);
            }
            context.get().setPacketHandled(true);
        }
    }

    /**
     * 1.0.4-beta (lot 3) : le panier du joueur tel que le serveur le tient : lignes, numero du dernier paquet traite, dernier avis (cle de
     * langue, deux nombres, cle de la raison, ton 0 neutre / 1 bon / 2 attention, numero).
     */
    public record CartView(List<Entry> lines, int seq, String notice, long a, long b, String reason, int tone, int noticeId, List<CraftView> crafts) {

        public static final CartView EMPTY = new CartView(List.of(), 0, "", 0, 0, "", 0, 0, List.of());

        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(lines.size());
            for (Entry e : lines) {
                buf.writeItem(e.proto());
                buf.writeVarLong(e.count());
            }
            buf.writeInt(seq);
            buf.writeUtf(notice, 128);
            buf.writeVarLong(a);
            buf.writeVarLong(b);
            buf.writeUtf(reason, 128);
            buf.writeVarInt(tone);
            buf.writeVarInt(noticeId);
            //1.0.5-beta (lot 22) : recettes demandees, EN FIN (un client d'avant ne les lit pas ; lues seulement s'il reste des octets)
            int n = Math.min(crafts.size(), fr.pioneers.warehouse.CounterCart.MAX_CRAFTS);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                crafts.get(i).encode(buf);
            }
        }

        static CartView decode(FriendlyByteBuf buf) {
            int n = Math.min(buf.readVarInt(), 256);
            List<Entry> lines = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                lines.add(new Entry(buf.readItem(), buf.readVarLong()));
            }
            int seq = buf.readInt();
            String notice = buf.readUtf(128);
            long a = buf.readVarLong(), b = buf.readVarLong();
            String reason = buf.readUtf(128);
            int tone = buf.readVarInt(), noticeId = buf.readVarInt();
            List<CraftView> crafts = new ArrayList<>();
            if (buf.isReadable()) {
                int c = Math.min(Math.max(0, buf.readVarInt()), fr.pioneers.warehouse.CounterCart.MAX_CRAFTS);
                for (int i = 0; i < c; i++) {
                    crafts.add(CraftView.decode(buf));
                }
            }
            return new CartView(lines, seq, notice, a, b, reason, tone, noticeId, crafts);
        }
    }

    /**
     * 1.0.5-beta (lot 22, retour 24 de Guillaume : « voir les recettes demandees ») : une RECETTE DEMANDEE du panier telle que le client la
     * montre : numero, produit et nombre par fabrication, fabrications voulues, objets qui manquaient au dernier plan, ingredients que la
     * recette a mis au panier ({@link #MAX_USES} au plus).
     */
    public record CraftView(int id, ItemStack result, int yield, int times, long missing, List<Entry> uses) {

        public static final int MAX_USES = 16;

        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeItem(result.copyWithCount(1));
            buf.writeVarInt(yield);
            buf.writeVarInt(times);
            buf.writeVarLong(Math.max(0, missing));
            int n = Math.min(uses.size(), MAX_USES);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                buf.writeItem(uses.get(i).proto().copyWithCount(1));
                buf.writeVarLong(Math.max(0, uses.get(i).count()));
            }
        }

        static CraftView decode(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            ItemStack result = buf.readItem();
            int yield = buf.readVarInt(), times = buf.readVarInt();
            long missing = buf.readVarLong();
            int n = Math.min(Math.max(0, buf.readVarInt()), MAX_USES);
            List<Entry> uses = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                uses.add(new Entry(buf.readItem(), buf.readVarLong()));
            }
            return new CraftView(id, result, yield, times, missing, uses);
        }
    }

    /**
     * Liste de l'entrepot du comptoir en {@code pos} ; {@code queued} : quantite demandee en attente par type (meme ordre) ; lot 3 :
     * {@code cart} = le panier du joueur qui recoit.
     */
    public record Contents(BlockPos pos, List<Entry> entries, List<Entry> queued, CartView cart) {

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(entries.size());
            for (Entry e : entries) {
                buf.writeItem(e.proto());
                buf.writeVarLong(e.count());
            }
            buf.writeVarInt(queued.size());
            for (Entry e : queued) {
                buf.writeItem(e.proto());
                buf.writeVarLong(e.count());
            }
            cart.encode(buf);
        }

        public static Contents decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            int n = Math.min(buf.readVarInt(), 4096);
            List<Entry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(new Entry(buf.readItem(), buf.readVarLong()));
            }
            int q = Math.min(buf.readVarInt(), 4096);
            List<Entry> queued = new ArrayList<>(q);
            for (int i = 0; i < q; i++) {
                queued.add(new Entry(buf.readItem(), buf.readVarLong()));
            }
            return new Contents(pos, list, queued, CartView.decode(buf));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.pioneers.client.warehouse.CounterClient.accept(this));
            context.get().setPacketHandled(true);
        }

        public void sendTo(ServerPlayer player) {
            PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), this);
        }
    }

    // ------------------------------------------------------------------ 1.0.5-beta (lot 17) : ajoutes EN FIN de PiNetwork, VERSION 21 inchangee

    /**
     * 1.0.5-beta (lot 17, Guillaume : « fluide et performant ») : STOCK du comptoir en {@code pos} PAR DIFFERENCES (serveur -> client, joueur
     * qui a l'interface ouverte) : {@code reset} = repartir d'une liste vide ; {@code entries} = lignes qui changent (quantite 0 : type
     * disparu ; au plus {@link #MAX_ENTRIES}) ; destinations possibles d'une commande (interfaces de sortie du reseau, au plus
     * {@link #MAX_DESTS}) et celle choisie par ce joueur ({@code dest}, BlockPos.asLong ; 0 : la sortie du comptoir).
     */
    public record Stock(BlockPos pos, boolean reset, List<Entry> entries, List<BlockPos> dests, long dest) {

        public static final int MAX_ENTRIES = 1024, MAX_DESTS = 8;

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeBoolean(reset);
            int n = Math.min(entries.size(), MAX_ENTRIES);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                buf.writeItem(entries.get(i).proto());
                buf.writeVarLong(Math.max(0, entries.get(i).count()));
            }
            int d = Math.min(dests.size(), MAX_DESTS);
            buf.writeVarInt(d);
            for (int i = 0; i < d; i++) {
                buf.writeBlockPos(dests.get(i));
            }
            buf.writeLong(dest);
        }

        public static Stock decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            boolean reset = buf.readBoolean();
            int n = Math.min(buf.readVarInt(), MAX_ENTRIES);
            List<Entry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(new Entry(buf.readItem(), buf.readVarLong()));
            }
            int d = Math.min(buf.readVarInt(), MAX_DESTS);
            List<BlockPos> dests = new ArrayList<>(d);
            for (int i = 0; i < d; i++) {
                dests.add(buf.readBlockPos());
            }
            return new Stock(pos, reset, list, dests, buf.readLong());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.pioneers.client.warehouse.CounterClient.accept(this));
            context.get().setPacketHandled(true);
        }

        public void sendTo(ServerPlayer player) {
            PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), this);
        }
    }

    /**
     * 1.0.5-beta (lot 17, Guillaume : « Maj + clic sur une recette JEI = demande des ingredients au reseau ») : une RECETTE de JEI au panier
     * du joueur au comptoir en {@code pos} (client -> serveur) : lignes (objets acceptes, nombre par fabrication), nombre de fabrications,
     * {@link #ORDER} = « Commander » aussitot. BORNE au decodage ({@link fr.pioneers.warehouse.RecipeAsk} : lignes, alternatives, fabrications,
     * nombres) ; le serveur refait le plan sur SON stock (source de verite) et repond par la liste et l'avis.
     */
    public record Recipe(BlockPos pos, List<List<ItemStack>> lines, List<Integer> perCraft, int times, int flags, ItemStack result, int yield) {

        public static final int ORDER = 1;

        /** Demande sans produit (gardee pour les appels d'avant le lot 22 : la recette n'est pas montree au panier). */
        public Recipe(BlockPos pos, List<List<ItemStack>> lines, List<Integer> perCraft, int times, int flags) {
            this(pos, lines, perCraft, times, flags, ItemStack.EMPTY, 1);
        }

        /** Paquet dans les bornes (lignes, alternatives, nombres) ? Le decodage borne deja ; le serveur reverifie. */
        public boolean valid() {
            if (lines.size() != perCraft.size() || lines.isEmpty() || lines.size() > fr.pioneers.warehouse.RecipeAsk.MAX_LINES
                || times < 1 || times > fr.pioneers.warehouse.RecipeAsk.MAX_TIMES) {
                return false;
            }
            for (int i = 0; i < lines.size(); i++) {
                int n = perCraft.get(i);
                if (lines.get(i).isEmpty() || lines.get(i).size() > fr.pioneers.warehouse.RecipeAsk.MAX_ALTERNATIVES || n < 1
                    || n > fr.pioneers.warehouse.RecipeAsk.MAX_PER_CRAFT) {
                    return false;
                }
            }
            return true;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            int n = Math.min(lines.size(), fr.pioneers.warehouse.RecipeAsk.MAX_LINES);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                List<ItemStack> alts = lines.get(i);
                int a = Math.min(alts.size(), fr.pioneers.warehouse.RecipeAsk.MAX_ALTERNATIVES);
                buf.writeVarInt(a);
                for (int k = 0; k < a; k++) {
                    buf.writeItem(alts.get(k).copyWithCount(1));
                }
                buf.writeVarInt(perCraft.get(i));
            }
            buf.writeVarInt(times);
            buf.writeVarInt(flags);
            //1.0.5-beta (lot 22) : produit de la recette (montre au panier), EN FIN
            buf.writeItem(result.isEmpty() ? ItemStack.EMPTY : result.copyWithCount(1));
            buf.writeVarInt(Math.max(1, Math.min(64, yield)));
        }

        /** Decodage BORNE : un paquet hors bornes est lu jusqu'a la borne et refuse par {@link #valid}. */
        public static Recipe decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            int n = buf.readVarInt();
            List<List<ItemStack>> lines = new ArrayList<>();
            List<Integer> per = new ArrayList<>();
            if (n < 0 || n > fr.pioneers.warehouse.RecipeAsk.MAX_LINES) {
                return new Recipe(pos, List.of(), List.of(), 0, 0);
            }
            for (int i = 0; i < n; i++) {
                int a = buf.readVarInt();
                if (a < 0 || a > fr.pioneers.warehouse.RecipeAsk.MAX_ALTERNATIVES) {
                    return new Recipe(pos, List.of(), List.of(), 0, 0);
                }
                List<ItemStack> alts = new ArrayList<>(a);
                for (int k = 0; k < a; k++) {
                    alts.add(buf.readItem());
                }
                lines.add(alts);
                per.add(buf.readVarInt());
            }
            int times = buf.readVarInt(), flags = buf.readVarInt();
            ItemStack result = ItemStack.EMPTY;
            int yield = 1;
            if (buf.isReadable()) {
                result = buf.readItem();
                yield = Math.max(1, Math.min(64, buf.readVarInt()));
            }
            return new Recipe(pos, lines, per, times, flags, result, yield);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            CounterTile tile = PacketGuard.tile(player, pos, CounterTile.class);
            if (tile != null && tile.role() == CounterTile.Role.COUNTER) {
                fr.pioneers.warehouse.CounterUnit unit = tile.counter();
                if (!valid()) {
                    unit.cart(player.getUUID()).say(new fr.pioneers.warehouse.CounterCart.Notice("gui.pioneers.warehouse.recipe.refused", 0, -1, "", 2));
                } else {
                    List<fr.pioneers.warehouse.RecipeAsk.Line<fr.pioneers.warehouse.StockKey>> asked = new ArrayList<>();
                    java.util.Map<fr.pioneers.warehouse.StockKey, ItemStack> protos = new java.util.HashMap<>();
                    for (int i = 0; i < lines.size(); i++) {
                        List<fr.pioneers.warehouse.StockKey> keys = new ArrayList<>();
                        for (ItemStack s : lines.get(i)) {
                            if (!s.isEmpty()) {
                                fr.pioneers.warehouse.StockKey k = fr.pioneers.warehouse.StockKey.of(s);
                                keys.add(k);
                                protos.putIfAbsent(k, s.copyWithCount(1));
                            }
                        }
                        asked.add(new fr.pioneers.warehouse.RecipeAsk.Line<>(keys, perCraft.get(i)));
                    }
                    unit.recipe(player.getUUID(), asked, protos, times, (flags & ORDER) != 0, result, yield);
                }
                tile.pushList(player);
            }
            context.get().setPacketHandled(true);
        }
    }

    /**
     * 1.0.5-beta (lot 22, retour 24 de Guillaume : « ajuster le nombre de fabrications qu'on veut realiser (via la molette) ») : NOMBRE DE
     * FABRICATIONS de la recette demandee {@code id} du panier du joueur au comptoir en {@code pos} (client -> serveur ; {@code times} 0 :
     * retirer la recette et sa part du panier). BORNE ({@link #valid} : numero positif, 0 a {@link fr.pioneers.warehouse.RecipeAsk#MAX_TIMES}
     * fabrications), verifie par {@link PacketGuard#tile} (distance, interface ouverte) et le role COMPTOIR ; le serveur refait le plan sur son
     * stock et repond par la liste ({@code seq} : comme {@link CartEdit}). Ajoute EN FIN de PiNetwork, VERSION 21 inchangee.
     */
    public record CraftEdit(BlockPos pos, int id, int times, int seq) {

        public boolean valid() {
            return id > 0 && times >= 0 && times <= fr.pioneers.warehouse.RecipeAsk.MAX_TIMES;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(id);
            buf.writeVarInt(times);
            buf.writeInt(seq);
        }

        public static CraftEdit decode(FriendlyByteBuf buf) {
            return new CraftEdit(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            CounterTile tile = PacketGuard.tile(player, pos, CounterTile.class);
            if (tile != null && tile.role() == CounterTile.Role.COUNTER) {
                fr.pioneers.warehouse.CounterUnit unit = tile.counter();
                if (valid()) {
                    unit.craftTimes(player.getUUID(), id, times);
                } else {
                    unit.cart(player.getUUID()).say(new fr.pioneers.warehouse.CounterCart.Notice("gui.pioneers.warehouse.recipe.refused", 0, -1, "", 2));
                }
                unit.cart(player.getUUID()).seq = seq;
                tile.pushList(player);
            }
            context.get().setPacketHandled(true);
        }
    }

    /**
     * 1.0.5-beta (lot 17) : reglage d'une station de l'entrepot (client -> serveur) : {@link #DEST} (comptoir : destination des commandes de ce
     * joueur, {@code value} = BlockPos.asLong d'une interface de sortie du reseau, 0 = la sortie du comptoir) ; {@link #KEEP} (interface de
     * sortie : regle « garder {@code count} » de {@code proto}, 0 la retire ; {@code count} borne a {@link fr.pioneers.warehouse.CounterUnit#MAX_KEEP}).
     */
    public record DeskEdit(BlockPos pos, int op, long value, ItemStack proto, int count) {

        public static final int DEST = 0, KEEP = 1;

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(op);
            buf.writeLong(value);
            buf.writeItem(proto.isEmpty() ? ItemStack.EMPTY : proto.copyWithCount(1));
            buf.writeVarInt(Math.max(0, count));
        }

        public static DeskEdit decode(FriendlyByteBuf buf) {
            return new DeskEdit(buf.readBlockPos(), buf.readVarInt(), buf.readLong(), buf.readItem(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            CounterTile tile = PacketGuard.tile(player, pos, CounterTile.class);
            if (tile != null) {
                fr.pioneers.warehouse.CounterUnit unit = tile.counter();
                if (op == DEST && tile.role() == CounterTile.Role.COUNTER) {
                    BlockPos dest = value == 0 ? null : BlockPos.of(value);
                    //Seule une interface de sortie du reseau du comptoir est acceptee
                    unit.cart(player.getUUID()).dest = dest != null && unit.destTile(dest) != null ? dest.immutable() : null;
                    tile.pushList(player);
                } else if (op == KEEP && tile.role() == CounterTile.Role.OUTLET && !proto.isEmpty()) {
                    if (!unit.keepSet(proto, Math.min(count, fr.pioneers.warehouse.CounterUnit.MAX_KEEP))) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.pioneers.warehouse.keep.full",
                              fr.pioneers.warehouse.CounterUnit.MAX_KEEPS).withStyle(net.minecraft.ChatFormatting.YELLOW), true);
                    }
                }
            }
            context.get().setPacketHandled(true);
        }
    }
}

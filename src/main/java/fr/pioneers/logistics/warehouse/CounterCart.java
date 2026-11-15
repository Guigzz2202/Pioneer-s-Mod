package fr.pioneers.logistics.warehouse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 3, Guillaume : « on devrait utiliser ce comptoir comme AE2 mais en cliquant, l'item part en zone tampon avec un bouton
 * commander ») : PANIER d'un joueur a un comptoir (zone tampon) : des lignes (type, quantite) que rien n'envoie avant « Commander »
 * ({@link CounterUnit#order}). Garde au SERVEUR, dans le comptoir, PAR JOUEUR (sauvegarde du monde, cle {@code warehouse.carts}) : il
 * survit a la fermeture de l'interface et au redemarrage, et deux joueurs au meme comptoir ont chacun le leur. Le client le modifie par
 * {@code PacketWarehouse.CartEdit} (quantite ABSOLUE d'une ligne : rejouer un paquet ne double rien) et le recoit avec la liste de
 * l'entrepot ({@code PacketWarehouse.Contents}) ; {@link #seq} = numero du dernier paquet traite (le client garde son panier local tant
 * que le serveur n'a pas traite sa derniere modification).
 */
public final class CounterCart {

    /** 1.0.5-beta (lot 22) : recettes demandees au plus par panier. */
    public static final int MAX_CRAFTS = 8;
    /** Lignes au plus (autant que de demandes dans la file du comptoir). */
    public static final int MAX_LINES = CounterUnit.MAX_QUEUE;
    /** Quantite au plus par ligne (64 piles de 64). */
    public static final int MAX_COUNT = 64 * 64;

    /** Une ligne : un type (pile de 1), une quantite. */
    public static final class Line {
        public final ItemStack proto;
        public int count;

        public Line(ItemStack proto, int count) {
            this.proto = proto.copyWithCount(1);
            this.count = count;
        }
    }

    /**
     * 1.0.5-beta (lot 22) : une RECETTE DEMANDEE : le produit ({@link #result}, {@link #yield} par fabrication), les lignes de la recette
     * (alternatives et nombre par fabrication, {@link #protos} : la pile de chaque cle), le nombre de fabrications voulu ({@link #times}),
     * ce qui manquait au dernier plan ({@link #missing} objets) et ce que la recette a mis au panier, par cle ({@link #contrib}).
     */
    public static final class Craft {
        public final int id;
        public final ItemStack result;
        public final int yield;
        public final List<RecipeAsk.Line<StockKey>> lines;
        public final Map<StockKey, ItemStack> protos;
        public int times;
        public long missing;
        public final Map<StockKey, Long> contrib = new LinkedHashMap<>();

        public Craft(int id, ItemStack result, int yield, List<RecipeAsk.Line<StockKey>> lines, Map<StockKey, ItemStack> protos, int times) {
            this.id = id;
            this.result = result.copyWithCount(1);
            this.yield = Math.max(1, Math.min(64, yield));
            this.lines = List.copyOf(lines);
            this.protos = protos;
            this.times = times;
        }

        /** Meme recette (meme produit, memes lignes) ? Une deuxieme demande s'ajoute alors a la premiere. */
        public boolean same(ItemStack result, List<RecipeAsk.Line<StockKey>> lines) {
            return ItemStack.isSameItemSameTags(this.result, result) && this.lines.equals(lines);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("id", id);
            t.put("result", result.save(new CompoundTag()));
            t.putInt("yield", yield);
            t.putInt("times", times);
            t.putLong("missing", missing);
            ListTag ls = new ListTag();
            for (RecipeAsk.Line<StockKey> l : lines) {
                CompoundTag e = new CompoundTag();
                ListTag alts = new ListTag();
                for (StockKey k : l.alternatives()) {
                    alts.add(protos.get(k).save(new CompoundTag()));
                }
                e.put("alts", alts);
                e.putInt("n", l.perCraft());
                ls.add(e);
            }
            t.put("lines", ls);
            ListTag cs = new ListTag();
            for (Map.Entry<StockKey, Long> e : contrib.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.put("item", protos.get(e.getKey()).save(new CompoundTag()));
                c.putLong("n", e.getValue());
                cs.add(c);
            }
            t.put("contrib", cs);
            return t;
        }

        @Nullable
        static Craft load(CompoundTag t) {
            ItemStack result = ItemStack.of(t.getCompound("result"));
            Map<StockKey, ItemStack> protos = new java.util.HashMap<>();
            List<RecipeAsk.Line<StockKey>> lines = new ArrayList<>();
            ListTag ls = t.getList("lines", Tag.TAG_COMPOUND);
            for (int i = 0; i < ls.size() && lines.size() < RecipeAsk.MAX_LINES; i++) {
                List<StockKey> keys = new ArrayList<>();
                ListTag alts = ls.getCompound(i).getList("alts", Tag.TAG_COMPOUND);
                for (int k = 0; k < alts.size() && keys.size() < RecipeAsk.MAX_ALTERNATIVES; k++) {
                    ItemStack s = ItemStack.of(alts.getCompound(k));
                    if (!s.isEmpty()) {
                        StockKey key = StockKey.of(s);
                        keys.add(key);
                        protos.putIfAbsent(key, s.copyWithCount(1));
                    }
                }
                int n = ls.getCompound(i).getInt("n");
                if (!keys.isEmpty() && n > 0) {
                    lines.add(new RecipeAsk.Line<>(keys, Math.min(RecipeAsk.MAX_PER_CRAFT, n)));
                }
            }
            if (result.isEmpty() || lines.isEmpty()) {
                return null;
            }
            Craft c = new Craft(t.getInt("id"), result, t.getInt("yield"), lines, protos, Math.max(1, Math.min(RecipeAsk.MAX_TIMES, t.getInt("times"))));
            c.missing = Math.max(0, t.getLong("missing"));
            ListTag cs = t.getList("contrib", Tag.TAG_COMPOUND);
            for (int i = 0; i < cs.size(); i++) {
                ItemStack s = ItemStack.of(cs.getCompound(i).getCompound("item"));
                long n = cs.getCompound(i).getLong("n");
                if (!s.isEmpty() && n > 0) {
                    StockKey key = StockKey.of(s);
                    protos.putIfAbsent(key, s.copyWithCount(1));
                    c.contrib.merge(key, n, Long::sum);
                }
            }
            return c;
        }
    }

    /** 1.0.5-beta (lot 22) : la recette demandee {@code id}, ou null. */
    @Nullable
    public Craft craft(int id) {
        for (Craft c : crafts) {
            if (c.id == id) {
                return c;
            }
        }
        return null;
    }

    /**
     * 1.0.5-beta (lot 22) : la part de chaque recette ne depasse jamais ce qui reste au panier (lignes baissees a la main, commande partie) ;
     * {@code dropEmpty} (apres « Commander ») : une recette dont plus rien n'est au panier s'en va (sa commande est partie).
     */
    public void prune(boolean dropEmpty) {
        for (java.util.Iterator<Craft> it = crafts.iterator(); it.hasNext(); ) {
            Craft c = it.next();
            for (java.util.Iterator<Map.Entry<StockKey, Long>> e = c.contrib.entrySet().iterator(); e.hasNext(); ) {
                Map.Entry<StockKey, Long> en = e.next();
                ItemStack p = c.protos.get(en.getKey());
                long have = p == null ? 0 : count(p);
                if (have <= 0) {
                    e.remove();
                } else if (en.getValue() > have) {
                    en.setValue(have);
                }
            }
            if (dropEmpty && c.contrib.isEmpty()) {
                it.remove();
            }
        }
    }

    /**
     * Avis du dernier « Commander » : cle de langue, ses arguments (a ; b s'il est >= 0 ; la raison traduite si elle n'est pas vide), ton
     * (0 neutre, 1 bon, 2 attention).
     */
    public record Notice(String key, long a, long b, String reason, int tone) {

        public static final Notice NONE = new Notice("", 0, -1, "", 0);
    }

    public final List<Line> lines = new ArrayList<>();
    /**
     * 1.0.5-beta (lot 22, retour 24 de Guillaume : « on devrait voir les recettes demandees et ajuster le nombre de fabrications qu'on veut
     * realiser (via la molette) ») : RECETTES DEMANDEES par ce joueur (bouton « + » de JEI), au plus {@link #MAX_CRAFTS}, sauvegardees
     * ({@code crafts}) : le produit, les lignes de la recette, le nombre de fabrications et ce que la recette a mis au panier
     * ({@link Craft#contrib}) ; changer le nombre ({@code CounterUnit.craftTimes}) retire cette part du panier et refait le plan sur le stock.
     */
    public final List<Craft> crafts = new ArrayList<>();
    /** Numero de la prochaine recette (sauvegarde : jamais deux recettes au meme numero). */
    public int nextCraft = 1;
    /**
     * 1.0.5-beta (lot 17) : DESTINATION des commandes de ce joueur : une interface de sortie du reseau (position du bloc), ou null : la
     * sortie du comptoir. Sauvegardee ({@code dest}).
     */
    @Nullable
    public net.minecraft.core.BlockPos dest;
    /** Dernier paquet du client traite (non sauvegarde). */
    public int seq;
    public Notice notice = Notice.NONE;
    /** Augmente a chaque avis (le client l'affiche quelques secondes a reception d'un numero neuf). */
    public int noticeId;

    @Nullable
    public Line line(ItemStack proto) {
        for (Line l : lines) {
            if (ItemStack.isSameItemSameTags(l.proto, proto)) {
                return l;
            }
        }
        return null;
    }

    /** Quantite au panier pour ce type. */
    public int count(ItemStack proto) {
        Line l = line(proto);
        return l == null ? 0 : l.count;
    }

    /** Fixe la quantite d'une ligne (0 : retiree) ; faux si le panier est plein (ligne nouvelle refusee). */
    public boolean set(ItemStack proto, int count) {
        Line l = line(proto);
        int n = Math.max(0, Math.min(MAX_COUNT, count));
        if (l != null) {
            if (n == 0) {
                lines.remove(l);
            } else {
                l.count = n;
            }
            return true;
        }
        if (n == 0) {
            return true;
        }
        if (lines.size() >= MAX_LINES) {
            return false;
        }
        lines.add(new Line(proto, n));
        return true;
    }

    public void say(Notice n) {
        notice = n;
        noticeId++;
    }

    /** Piles d'une quantite de ce type. */
    public static int piles(ItemStack proto, long count) {
        int max = Math.max(1, proto.getMaxStackSize());
        return (int) ((count + max - 1) / max);
    }

    /** Colis estimes : 4 piles par colis, par type. */
    public static int parcels(ItemStack proto, long count) {
        return (piles(proto, count) + Parcel.PILES - 1) / Parcel.PILES;
    }

    /** Empreinte (envoi au client seulement si elle change). */
    public long stamp() {
        long h = seq * 31L + noticeId * 131L + (dest == null ? 0 : dest.asLong() * 17);
        for (Line l : lines) {
            h = h * 31 + l.proto.getItem().hashCode() * 7L + l.count;
        }
        for (Craft c : crafts) {
            h = h * 37 + c.id * 101L + c.times * 13L + c.missing;
            for (long n : c.contrib.values()) {
                h = h * 7 + n;
            }
        }
        return h;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag ls = new ListTag();
        for (Line l : lines) {
            CompoundTag e = new CompoundTag();
            e.put("item", l.proto.save(new CompoundTag()));
            e.putInt("n", l.count);
            ls.add(e);
        }
        t.put("lines", ls);
        if (dest != null) {
            t.putLong("dest", dest.asLong());
        }
        //1.0.5-beta (lot 22) : recettes demandees (cle nouvelle : les paniers d'avant n'en ont pas)
        if (!crafts.isEmpty()) {
            ListTag cs = new ListTag();
            crafts.forEach(c -> cs.add(c.save()));
            t.put("crafts", cs);
            t.putInt("nextCraft", nextCraft);
        }
        return t;
    }

    public static CounterCart load(CompoundTag t) {
        CounterCart c = new CounterCart();
        if (t.contains("dest")) {
            c.dest = net.minecraft.core.BlockPos.of(t.getLong("dest"));
        }
        ListTag ls = t.getList("lines", Tag.TAG_COMPOUND);
        for (int i = 0; i < ls.size() && c.lines.size() < MAX_LINES; i++) {
            ItemStack proto = ItemStack.of(ls.getCompound(i).getCompound("item"));
            int n = ls.getCompound(i).getInt("n");
            if (!proto.isEmpty() && n > 0) {
                c.lines.add(new Line(proto, Math.min(MAX_COUNT, n)));
            }
        }
        ListTag cs = t.getList("crafts", Tag.TAG_COMPOUND);
        for (int i = 0; i < cs.size() && c.crafts.size() < MAX_CRAFTS; i++) {
            Craft k = Craft.load(cs.getCompound(i));
            if (k != null && c.craft(k.id) == null) {
                c.crafts.add(k);
                c.nextCraft = Math.max(c.nextCraft, k.id + 1);
            }
        }
        c.nextCraft = Math.max(c.nextCraft, t.getInt("nextCraft"));
        return c;
    }
}

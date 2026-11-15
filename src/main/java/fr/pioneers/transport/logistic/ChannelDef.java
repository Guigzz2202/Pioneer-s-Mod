package fr.pioneers.transport.logistic;

import net.minecraft.nbt.CompoundTag;

/**
 * 1.11 (spec §2) : definition d'un canal. {@code uid} : entier aleatoire non nul, stable (reconciliation) ; {@code rev} : horodatage de
 * l'edition (la plus grande gagne) ; {@code deleted} : tombe gardee pour la reconciliation.
 * <p>
 * Corps des canaux de chaleur et de pression : {@link #body} = temperature (K) ou pression (bar), grandeurs intensives : une decoupe garde la
 * meme valeur dans chaque moitie (equivalent au prorata des cables pour l'air), NaN = jamais mesure (ambiant / vide).
 */
public final class ChannelDef {

    public static final int NAME_MAX = 24, COLORS = 16;

    public enum Condition {
        ALWAYS, POWERED, UNPOWERED;

        private static final Condition[] VALUES = values();

        public static Condition byIndex(int index) {
            return VALUES[Math.floorMod(index, VALUES.length)];
        }
    }

    public int uid;
    /** Nom libre (vide : nom par defaut du type, traduit chez le client). */
    public String name = "";
    public ChannelKind kind = ChannelKind.ITEM;
    public int color;
    public Distribution mode = Distribution.ROUND_ROBIN;
    public Filter filter = Filter.EMPTY;
    public Condition condition = Condition.ALWAYS;
    /** Debit max du canal (0 : debit du niveau) ; par seconde pour les objets, par tick sinon. */
    public long cap;
    public long rev;
    public boolean deleted;
    /** Migration (D2c) : canal issu d'un ancien reseau a cheval sur un chunk non charge, a fusionner avec son jumeau. */
    public boolean partial;
    /** Migration (D2c) : origine (ex. {@code item}, famille de l'ancien reseau) pour la fusion des canaux partiels ; vide sinon. */
    public String origin = "";
    public double body = Double.NaN;
    /** 1.11 (D2b) : rang du canal dans la liste (reordonne dans le diagramme ; egalite : ordre de la liste). Le premier canal du bon type recoit
     * les ejections d'une machine (spec §3.3). */
    public int order;

    public ChannelDef() {
    }

    public ChannelDef(int uid, ChannelKind kind) {
        this.uid = uid;
        this.kind = kind;
    }

    public ChannelDef copy() {
        ChannelDef c = new ChannelDef(uid, kind);
        c.name = name;
        c.color = color;
        c.mode = mode;
        c.filter = filter;
        c.condition = condition;
        c.cap = cap;
        c.rev = rev;
        c.deleted = deleted;
        c.partial = partial;
        c.origin = origin;
        c.body = body;
        c.order = order;
        return c;
    }

    public ChannelDef clamp() {
        name = name.length() > NAME_MAX ? name.substring(0, NAME_MAX) : name;
        color = Math.floorMod(color, COLORS);
        cap = Math.max(0, cap);
        return this;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("u", uid);
        tag.putLong("v", rev);
        if (deleted) {
            //Tombe : seuls l'identifiant et la revision comptent
            tag.putBoolean("x", true);
            return tag;
        }
        if (!name.isEmpty()) {
            tag.putString("n", name);
        }
        tag.putByte("k", (byte) kind.ordinal());
        tag.putByte("c", (byte) color);
        tag.putByte("m", (byte) mode.ordinal());
        if (!filter.isEmpty() || filter.blacklist) {
            tag.put("f", filter.save());
        }
        if (condition != Condition.ALWAYS) {
            tag.putByte("r", (byte) condition.ordinal());
        }
        if (cap > 0) {
            tag.putLong("cap", cap);
        }
        if (partial) {
            tag.putBoolean("p", true);
        }
        if (!origin.isEmpty()) {
            tag.putString("o", origin);
        }
        if (!Double.isNaN(body)) {
            tag.putDouble("b", body);
        }
        if (order != 0) {
            tag.putShort("ord", (short) order);
        }
        return tag;
    }

    public static ChannelDef load(CompoundTag tag) {
        ChannelDef c = new ChannelDef(tag.getInt("u"), ChannelKind.byIndex(tag.getByte("k")));
        c.rev = tag.getLong("v");
        c.deleted = tag.getBoolean("x");
        c.name = tag.getString("n");
        c.color = tag.getByte("c");
        c.mode = Distribution.byIndex(tag.getByte("m"));
        c.filter = Filter.load(tag.getCompound("f"), Filter.CHANNEL_SIZE);
        c.condition = Condition.byIndex(tag.getByte("r"));
        c.cap = tag.getLong("cap");
        c.partial = tag.getBoolean("p");
        c.origin = tag.getString("o");
        c.body = tag.contains("b") ? tag.getDouble("b") : Double.NaN;
        c.order = tag.getShort("ord");
        return c.clamp();
    }
}

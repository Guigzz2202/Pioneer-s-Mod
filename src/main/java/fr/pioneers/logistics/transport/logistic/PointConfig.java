package fr.pioneers.logistics.transport.logistic;

import net.minecraft.nbt.CompoundTag;

/**
 * 1.11 (spec §3.1) : reglages d'un point (raccord x canal). Mutable mais jamais partage : le reseau copie a l'edition.
 * <ul>
 *     <li>role, tirer (entree : le cable extrait ; sinon il n'accepte que ce que la machine ejecte dans la fiche) ;</li>
 *     <li>face d'acces : {@link #TOUCHED} ou face relative a la facade (RelativeSide 0..5), independante de la face touchee ;</li>
 *     <li>priorite -99..99, poids 1..16, garder en stock (entree), remplir jusqu'a (sortie), debit max (par seconde pour les objets, par tick
 *     sinon ; 0 = sans limite), filtre propre (5 entrees) ;</li>
 *     <li>{@code rev} : horodatage de l'edition (reconciliation : la plus grande gagne).</li>
 * </ul>
 */
public final class PointConfig {

    /** Face d'acces = face touchee par la fiche. */
    public static final int TOUCHED = -1;
    public static final long UNLIMITED = Long.MAX_VALUE;
    /** Point des canaux automatiques : les deux, sans tirer (ce que la machine ejecte part aux autres). Ne jamais le modifier. */
    public static final PointConfig AUTO = new PointConfig(Role.BOTH, false);

    public Role role = Role.NONE;
    public boolean pull = true;
    public int face = TOUCHED;
    public int priority;
    public int weight = 1;
    public long keep;
    public long fillTo = UNLIMITED;
    public long maxRate;
    public Filter filter = Filter.EMPTY;
    public long rev;

    public PointConfig() {
    }

    public PointConfig(Role role, boolean pull) {
        this.role = role;
        this.pull = pull;
    }

    public PointConfig copy() {
        PointConfig c = new PointConfig(role, pull);
        c.face = face;
        c.priority = priority;
        c.weight = weight;
        c.keep = keep;
        c.fillTo = fillTo;
        c.maxRate = maxRate;
        c.filter = filter;
        c.rev = rev;
        return c;
    }

    /** Bornes des reglages (paquets du client, NBT ancien). */
    public PointConfig clamp() {
        priority = Math.max(-99, Math.min(99, priority));
        weight = Math.max(1, Math.min(16, weight));
        face = face < 0 ? TOUCHED : Math.min(5, face);
        keep = Math.max(0, keep);
        fillTo = fillTo <= 0 ? UNLIMITED : fillTo;
        maxRate = Math.max(0, maxRate);
        return this;
    }

    /** Memes reglages (hors revision) : groupes de machines identiques du diagramme. */
    public boolean sameSettings(PointConfig o) {
        return role == o.role && pull == o.pull && face == o.face && priority == o.priority && weight == o.weight && keep == o.keep
               && fillTo == o.fillTo && maxRate == o.maxRate && filter.same(o.filter);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("r", (byte) role.ordinal());
        if (!pull) {
            tag.putBoolean("np", true);
        }
        if (face != TOUCHED) {
            tag.putByte("f", (byte) face);
        }
        if (priority != 0) {
            tag.putByte("p", (byte) priority);
        }
        if (weight != 1) {
            tag.putByte("w", (byte) weight);
        }
        if (keep > 0) {
            tag.putLong("k", keep);
        }
        if (fillTo != UNLIMITED) {
            tag.putLong("u", fillTo);
        }
        if (maxRate > 0) {
            tag.putLong("m", maxRate);
        }
        if (!filter.isEmpty() || filter.blacklist) {
            tag.put("fi", filter.save());
        }
        tag.putLong("v", rev);
        return tag;
    }

    public static PointConfig load(CompoundTag tag) {
        PointConfig c = new PointConfig(Role.byIndex(tag.getByte("r")), !tag.getBoolean("np"));
        c.face = tag.contains("f") ? tag.getByte("f") : TOUCHED;
        c.priority = tag.getByte("p");
        c.weight = tag.contains("w") ? tag.getByte("w") : 1;
        c.keep = tag.getLong("k");
        c.fillTo = tag.contains("u") ? tag.getLong("u") : UNLIMITED;
        c.maxRate = tag.getLong("m");
        c.filter = Filter.load(tag.getCompound("fi"), Filter.POINT_SIZE);
        c.rev = tag.getLong("v");
        return c.clamp();
    }
}

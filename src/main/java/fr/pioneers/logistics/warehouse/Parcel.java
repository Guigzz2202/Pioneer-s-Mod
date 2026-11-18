package fr.pioneers.logistics.warehouse;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 1, Guillaume : « ils arriveront par petites caisses via les cordages qui relient tout l'entrepot et qui tournent pour les
 * deplacer ») : un COLIS (petite caisse) en route, possede par le comptoir qui l'a envoye (sauvegarde avec lui : la verite est au serveur).
 * Un seul type d'objet, au plus {@link #PILES} piles. Deux sortes :
 * <ul>
 *     <li>{@link Kind#REQUEST} : demande du comptoir ; aller caisse -> comptoir charge (depose dans les cases de sortie), retour a vide ;</li>
 *     <li>{@link Kind#SEND} : envoi des cases d'envoi du comptoir ; aller comptoir -> caisse charge (range dans la caisse), retour au comptoir
 *     avec ce qui n'a pas trouve de place (rendu aux cases d'envoi).</li>
 * </ul>
 * Phases d'une course : {@link Phase#RISE} (le colis sort de la trappe ou quitte le comptoir et monte a la corde), {@link Phase#TRAVEL}
 * (le long des cordes, a leur vitesse), {@link Phase#LOWER} (descend sur le comptoir ou dans la trappe), {@link Phase#OPEN} (au comptoir :
 * le couvercle s'ouvre, les objets passent dans les cases). 1.0.4-beta (lot 2) : a une station plus basse que la roue, la montee et la
 * descente durent {@link WarehouseLines#TICKS_PER_BLOCK} ticks de plus par bloc de vide (le colis file au bout de son filin).
 */
public final class Parcel {

    public static final int PILES = 4;
    /**
     * Durees des phases (ticks de rotation ; l'ouverture ne demande pas de rotation). 1.0.5-beta (lot 13) : divisees par la cadence
     * {@link LineNode#CADENCE} (avant 16, 14 et 18).
     */
    public static final int RISE_TICKS = 12, LOWER_TICKS = 11, OPEN_TICKS = 14;

    public enum Kind {
        REQUEST, SEND
    }

    public enum Phase {
        RISE, TRAVEL, LOWER, OPEN
    }

    public final int id;
    public final Kind kind;
    /** 0 : aller, 1 : retour. */
    public int leg;
    /** Poulies de la course (station de depart d'abord). */
    public final List<BlockPos> route = new ArrayList<>();
    public Phase phase = Phase.RISE;
    public int timer;
    /** Abscisse le long des cordes (blocs). */
    public double s;
    public ItemStack proto = ItemStack.EMPTY;
    public int count;
    /** Cache de geometrie (pas sauvegarde). */
    @Nullable
    transient ParcelRoute geometry;
    transient int geometrySpin;
    /** Client : abscisse et minuteur extrapoles localement (vitesse de la corde lue sur la roue du client). */
    public double clientS = Double.NaN;
    public float clientTimer;

    public Parcel(int id, Kind kind) {
        this.id = id;
        this.kind = kind;
    }

    public boolean empty() {
        return count <= 0 || proto.isEmpty();
    }

    /** Vers le comptoir ? (demande a l'aller, envoi au retour) */
    public boolean towardCounter() {
        return kind == Kind.REQUEST ? leg == 0 : leg == 1;
    }

    public BlockPos startStation() {
        return route.get(0);
    }

    public BlockPos endStation() {
        return route.get(route.size() - 1);
    }

    /** Demi-tour : meme course a l'envers, repart de la station d'arrivee. */
    public void turnBack() {
        java.util.Collections.reverse(route);
        leg = 1;
        phase = Phase.RISE;
        timer = 0;
        s = 0;
        geometry = null;
    }

    /** Geometrie deja calculee (null : pas encore). */
    @Nullable
    public ParcelRoute geometryOrNull() {
        return geometry;
    }

    /** Geometrie de la course pour ce sens de rotation (gardee tant qu'il ne change pas) : rayons et hauteurs des roues du monde. */
    public ParcelRoute geometry(net.minecraft.world.level.BlockGetter level, int spin) {
        int sp = spin >= 0 ? 1 : -1;
        if (geometry == null || geometrySpin != sp) {
            geometry = ParcelRoute.of(level, route, sp);
            geometrySpin = sp;
        }
        return geometry;
    }

    /** 1.0.4-beta (lot 2) : duree de la montee depuis une station a {@code gap} blocs de vide sous la roue (descente au filin). */
    public static int riseTicks(int gap) {
        return RISE_TICKS + Math.max(0, gap) * WarehouseLines.TICKS_PER_BLOCK;
    }

    /** Duree de la descente vers une station a {@code gap} blocs de vide sous la roue. */
    public static int lowerTicks(int gap) {
        return LOWER_TICKS + Math.max(0, gap) * WarehouseLines.TICKS_PER_BLOCK;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putInt("kind", kind.ordinal());
        t.putInt("leg", leg);
        long[] r = new long[route.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = route.get(i).asLong();
        }
        t.putLongArray("route", r);
        t.putInt("phase", phase.ordinal());
        t.putInt("timer", timer);
        t.putDouble("s", s);
        if (!proto.isEmpty() && count > 0) {
            t.put("item", proto.save(new CompoundTag()));
            t.putInt("count", count);
        }
        return t;
    }

    public static Parcel load(CompoundTag t) {
        Parcel p = new Parcel(t.getInt("id"), Kind.values()[Math.max(0, Math.min(1, t.getInt("kind")))]);
        p.leg = t.getInt("leg");
        for (long l : t.getLongArray("route")) {
            p.route.add(BlockPos.of(l));
        }
        p.phase = Phase.values()[Math.max(0, Math.min(Phase.values().length - 1, t.getInt("phase")))];
        p.timer = t.getInt("timer");
        p.s = t.getDouble("s");
        if (t.contains("item")) {
            p.proto = ItemStack.of(t.getCompound("item")).copyWithCount(1);
            p.count = t.getInt("count");
        }
        return p;
    }
}

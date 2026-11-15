package fr.pioneers.logistics.transport.legacy;

import fr.pioneers.logistics.transport.logistic.CableLevel;
import fr.pioneers.logistics.transport.logistic.ChannelDef;
import fr.pioneers.logistics.transport.logistic.Distribution;
import fr.pioneers.logistics.transport.logistic.PointConfig;
import fr.pioneers.logistics.transport.logistic.Role;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (D2c, spec §8.1) : plan de conversion d'un ancien reseau en cable logistique. Pur (lecture par {@link World}, aucune ecriture) :
 * {@code LegacyMigration} l'applique au monde, le banc hors jeu ({@code tools/cables/MigrationBench}) le verifie.
 * <ul>
 *     <li>composant = anciens cables de la meme famille relies (quel que soit leur tier ou leur transparence, comme avant), arret aux chunks
 *     non charges (composant {@code partial} : son canal fusionnera avec celui de l'autre moitie a la rencontre) et a {@link #MAX} cables ;</li>
 *     <li>chaque cable garde son niveau et sa transparence ; ses faces coupees le restent ; les faces vers un ancien cable d'une AUTRE famille
 *     sont coupees : deux anciens reseaux voisins ne se melangent pas (sinon un reseau mixte prendrait le niveau le plus faible : une goulotte
 *     en bois a cote d'un cable d'energie MKIV l'aurait suspendu) ; la cle les rebranche en un clic ;</li>
 *     <li>chaque ancienne fiche devient un point du canal : neutre = les deux sans tirer, entree = entree (tirer), sortie = sortie ; chaleur :
 *     les deux ; pression : clapets entree / sortie ; fibre : les deux sans tirer (la machine ejecte selon ses faces de donnees).</li>
 * </ul>
 */
public final class LegacyPlan {

    /** Taille maximale d'un composant traite d'un coup (au-dela : canal partiel, la suite se convertit ensuite). */
    public static final int MAX = 4096;
    private static final Direction[] DIRS = Direction.values();

    private LegacyPlan() {
    }

    /** Lecture du monde (ancien cable d'une position, chunk charge). */
    public interface World {

        @Nullable
        Old at(long pos);

        boolean loaded(long pos);

        /** Cable logistique deja converti (moitie d'un ancien reseau convertie plus tot) : le composant est alors partiel. */
        boolean converted(long pos);
    }

    /** Un ancien cable tel qu'il a ete sauve. */
    public static final class Old {

        public final long pos;
        public final LegacyFamily family;
        public final CableLevel level;
        public final boolean clear;
        /** Masques par face (bits = Direction.ordinal) : coupee, entree (le reseau tirait), sortie. */
        public int disabled, pulling, pushing;
        /** Faces branchees sur un bloc (ancien etat PLUG). */
        public int plugs;
        /** Temperature (K) du corps d'un cable de chaleur ; air (L) d'un tuyau de pression ; NaN sinon. */
        public double temperature = Double.NaN, air = Double.NaN;

        public Old(long pos, LegacyFamily family, CableLevel level, boolean clear) {
            this.pos = pos;
            this.family = family;
            this.level = level;
            this.clear = clear;
        }
    }

    /** Un cable converti : niveau, transparence, faces coupees et points des fiches. */
    public static final class Converted {

        public final Old old;
        public int cut;
        public final PointConfig[] points = new PointConfig[6];

        Converted(Old old) {
            this.old = old;
        }

        public boolean hasPoints() {
            for (PointConfig p : points) {
                if (p != null) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class Component {

        public final LegacyFamily family;
        public final List<Converted> cables = new ArrayList<>();
        public boolean partial;
        /** Corps du canal (chaleur : moyenne des temperatures en K ; pression : air total / volume total en bar) ; NaN sinon. */
        public double body = Double.NaN;

        Component(LegacyFamily family) {
            this.family = family;
        }

        /** Canal de l'ancien reseau : type de la famille, couleur du type, tour a tour, sans filtre ; partiel + origine pour la fusion. */
        public ChannelDef channel(int uid, long rev) {
            ChannelDef def = new ChannelDef(uid, family.kind);
            def.color = family.kind.ordinal();
            def.mode = Distribution.ROUND_ROBIN;
            def.rev = rev;
            def.partial = partial;
            def.origin = family.id();
            def.body = body;
            return def;
        }

        public boolean hasPoints() {
            for (Converted c : cables) {
                if (c.hasPoints()) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Point d'une ancienne fiche (face {@code d} de {@code old}). */
    public static PointConfig point(Old old, int d) {
        boolean in = (old.pulling >> d & 1) != 0, out = (old.pushing >> d & 1) != 0;
        return switch (old.family) {
            //Chaleur : conduction libre (neutre ou coupee, pas de mode)
            case HEAT -> new PointConfig(Role.BOTH, false);
            //Pression : clapets (entree = l'air ne va que du bloc au tuyau)
            case PRESSURE -> new PointConfig(in ? Role.INPUT : out ? Role.OUTPUT : Role.BOTH, false);
            //Fibre : la machine ejecte selon ses faces de donnees, le reseau livre aux autres
            case DATA -> new PointConfig(Role.BOTH, false);
            default -> in ? new PointConfig(Role.INPUT, true) : out ? new PointConfig(Role.OUTPUT, true) : new PointConfig(Role.BOTH, false);
        };
    }

    /** Composant (ancien reseau) qui contient {@code start} ; null si ce n'est pas un ancien cable. */
    @Nullable
    public static Component collect(World world, long start) {
        Old first = world.at(start);
        if (first == null) {
            return null;
        }
        Component component = new Component(first.family);
        Long2ObjectOpenHashMap<Converted> seen = new Long2ObjectOpenHashMap<>();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        seen.put(start, new Converted(first));
        queue.enqueue(start);
        double tempSum = 0, airSum = 0, volume = 0;
        int temps = 0;
        while (!queue.isEmpty()) {
            long pos = queue.dequeueLong();
            Converted c = seen.get(pos);
            component.cables.add(c);
            Old old = c.old;
            c.cut = old.disabled & 63;
            if (!Double.isNaN(old.temperature)) {
                tempSum += old.temperature;
                temps++;
            }
            if (!Double.isNaN(old.air)) {
                airSum += old.air;
                volume += Math.max(1, old.level.airVolume);
            }
            for (int d = 0; d < 6; d++) {
                if ((old.disabled >> d & 1) != 0) {
                    continue;
                }
                long next = BlockPos.offset(pos, DIRS[d]);
                if (!world.loaded(next)) {
                    //Chunk voisin non charge : l'ancien reseau continue peut-etre derriere
                    component.partial = true;
                    continue;
                }
                Old other = world.at(next);
                if (other == null) {
                    if (world.converted(next)) {
                        component.partial = true;
                    }
                    if ((old.plugs >> d & 1) != 0) {
                        c.points[d] = point(old, d);
                    }
                    continue;
                }
                if (other.family != old.family) {
                    //Deux anciens reseaux voisins ne se melangent pas
                    c.cut |= 1 << d;
                    continue;
                }
                if ((other.disabled >> DIRS[d].getOpposite().ordinal() & 1) != 0) {
                    c.cut |= 1 << d;
                    continue;
                }
                if (!seen.containsKey(next)) {
                    if (seen.size() >= MAX) {
                        component.partial = true;
                        continue;
                    }
                    seen.put(next, new Converted(other));
                    queue.enqueue(next);
                }
            }
        }
        if (temps > 0) {
            component.body = tempSum / temps;
        } else if (volume > 0) {
            component.body = airSum / volume;
        }
        return component;
    }
}

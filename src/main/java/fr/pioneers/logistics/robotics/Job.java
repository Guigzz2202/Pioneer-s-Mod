package fr.pioneers.logistics.robotics;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Travail confie a une unite par l'ordonnanceur :
 * <ul>
 *     <li>une <b>tournee</b> : une suite d'arrets (prendre ici, livrer la...) ;</li>
 *     <li>un <b>bloc</b> (recolte, abattage, minage, terrassement, peche) ou une <b>entite</b> (objet au sol, animal, monstre), avec
 *     d'eventuels arrets de fournitures avant (nourriture, pousses).</li>
 * </ul>
 * Un travail appartient a un groupe (l'ordre ou la zone qui l'a fait naitre ; -1 pour les points logistiques) et reserve des quantites
 * sur des demandes (pour ne jamais livrer deux fois).
 */
public final class Job {

    public enum Type {BLOCK, ENTITY, TRIP}

    /** Groupe des demandes nees des points logistiques (pas d'ordre). */
    public static final int NO_GROUP = -1;

    /**
     * Arret d'une tournee. {@code load} : prendre ; sinon livrer. {@code amount} : quantite au plus (0 : tout). {@code accept} : pour une
     * prise, ne prendre que ce que ce bloc acceptera (transport continu). {@code demand} : demande servie par une livraison.
     */
    public record Stop(Endpoint at, boolean load, LogisticResource res, long amount, @Nullable Endpoint accept, long demand) {}

    /** Reservation : quantite en route pour une demande. */
    public record Claim(long demand, long amount) {}

    public final Type type;
    public RobotTask task;
    @Nullable
    public final BlockPos pos;
    public final int entityId;
    /** Tournee, ou fournitures a prendre avant un travail de zone. */
    public final List<Stop> stops = new ArrayList<>();
    public final List<Claim> claims = new ArrayList<>();
    /** Ordre ou zone d'origine (identifiant), ou {@link #NO_GROUP}. */
    public int group = NO_GROUP;
    /** Cle d'unicite d'un travail de zone. */
    public long key;
    public int priority;
    /** Livraison du reseau passif : genre de l'unite qui la fait (limite par genre) ; -1 sinon. */
    public int passiveKind = -1;
    /** Reparation par un intendant (degats d'explosion) : travail du reseau passif. */
    public boolean repair;
    /** Blocs supplementaires (troncs d'un arbre). */
    public final List<BlockPos> extra = new ArrayList<>();
    /** Abattage : identifiant du rondin au pied de l'arbre (pour replanter la meme essence). */
    @Nullable
    public String woodName;
    public int partner = -1;
    /** Elevage : bete en surplus a eliminer (et non a nourrir). */
    public boolean cull;
    /** Travaux : premier etat a poser (null : bloc a retirer) ; les positions sont {@link #pos} puis {@link #extra}, leurs etats {@link #states}. */
    @Nullable
    public BlockState place;
    public final List<BlockState> states = new ArrayList<>();
    /** Travaux : objet a poser (vide : rien a apporter). */
    public ItemStack material = ItemStack.EMPTY;
    /** Agriculture : 0 recolter, 1 rebecher la terre (puis semer), 2 semer sur une terre labouree vide. */
    public int farm;

    /** Travail reserve aux unites volantes (point hors d'atteinte au sol). */
    public boolean airOnly;

    private Job(Type type, RobotTask task, @Nullable BlockPos pos, int entityId) {
        this.type = type;
        this.task = task;
        this.pos = pos == null ? null : pos.immutable();
        this.entityId = entityId;
    }

    public static Job block(RobotTask task, BlockPos pos) {
        Job job = new Job(Type.BLOCK, task, pos, -1);
        job.key = pos.asLong();
        return job;
    }

    public static Job entity(RobotTask task, int id, BlockPos at) {
        Job job = new Job(Type.ENTITY, task, at, id);
        job.key = 0x4000_0000_0000_0000L | id;
        return job;
    }

    public static Job trip() {
        return new Job(Type.TRIP, RobotTask.LOGISTICS, null, -1);
    }

    /** Tournee simple : prendre puis livrer. */
    public static Job transfer(Endpoint from, Endpoint to, LogisticResource res, long amount, boolean acceptCheck, long demand) {
        Job job = trip();
        job.stops.add(new Stop(from, true, res, amount, acceptCheck ? to : null, 0));
        job.stops.add(new Stop(to, false, res, amount, null, demand));
        if (demand != 0) {
            job.claims.add(new Claim(demand, amount));
        }
        return job;
    }

    /** Point de reference (choix de la porte de sortie, distance). */
    public Vec3 anchor() {
        if (!stops.isEmpty()) {
            return stops.get(0).at().accessPoint();
        }
        return pos == null ? Vec3.ZERO : Vec3.atCenterOf(pos);
    }

    /** Toutes les positions que l'unite devra atteindre (reseau, portee). */
    public List<BlockPos> points() {
        List<BlockPos> list = new ArrayList<>();
        for (Stop stop : stops) {
            list.add(stop.at().standPos());
        }
        if (pos != null) {
            list.add(pos);
        }
        return list;
    }

    /** Quantite totale reservee (tri des candidats). */
    public long planned() {
        long sum = 0;
        for (Claim claim : claims) {
            sum += claim.amount();
        }
        return sum;
    }

    /** Ressource principale d'une tournee (capacite d'emport). */
    @Nullable
    public LogisticResource mainResource() {
        return stops.isEmpty() ? null : stops.get(0).res();
    }
}

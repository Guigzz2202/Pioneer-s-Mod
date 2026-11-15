package fr.pioneers.world.pet.client;

import fr.pioneers.world.pet.PacketPetList;
import fr.pioneers.world.pet.PetRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * 1.0.5-beta (lot 33, retour 45 : « des fois elle apparait comme vide, et elle a de la latence ») : CACHE client de la liste de la roue du
 * rappel et regles PURES de la touche (aucune classe de Minecraft : banc {@code tools/pets/wheel_open_bench.sh}).
 * <p>
 * Causes trouvees dans le code d'avant : (1) la roue n'apparaissait qu'apres {@link PetRules#WHEEL_HOLD} ticks de maintien (300 ms), puis
 * (2) demandait la liste et attendait l'aller-retour du reseau avant d'avoir un contenu ; (3) une demande a moins d'une seconde de la
 * precedente etait jetee par le serveur et la roue restait vide jusqu'a la redemande 1,25 s plus tard ; (4) une reponse arrivee apres la
 * fermeture etait perdue (aucun cache) ; (5) la mise en page et la liste de dessin etaient refaites a chaque image.
 * <p>
 * Desormais : la liste vit ICI (remplie par toute {@link PacketPetList} : reponse, envoi du ou poussee par le serveur a la connexion, au
 * changement de dimension, a la reapparition et a chaque changement d'un animal), videe a la deconnexion ; la roue s'ouvre a l'appui meme
 * (evenement de touche, meme image) avec ce contenu, et la liste fraiche demandee a l'ouverture la remplace des qu'elle arrive.
 */
public final class WheelCache {

    /** Le cache du client. */
    public static final WheelCache CLIENT = new WheelCache();

    /** Issue du relachement de la touche : tous les animaux, celui vise, rien. */
    public static final int ALL = 0, ONE = 1, NONE = 2;
    /** Appui bref : relache avant {@link PetRules#WHEEL_HOLD} ticks (en millisecondes : la roue est deja ouverte, l'horloge est celle du client). */
    public static final long HOLD_MS = PetRules.WHEEL_HOLD * 50L;

    /** Les plus proches d'abord (meme dimension), puis les autres par dimension et par nom. */
    public static final Comparator<PacketPetList.Row> ORDER = Comparator.comparingInt((PacketPetList.Row r) -> r.distance() >= 0 ? 0 : 1)
          .thenComparingInt(r -> Math.max(0, r.distance())).thenComparing(PacketPetList.Row::dim).thenComparing(PacketPetList.Row::name);

    private List<PacketPetList.Row> rows = List.of();
    private boolean known;
    private int version;

    /** Liste recue du serveur (toute source). */
    public void receive(List<PacketPetList.Row> list) {
        rows = List.copyOf(list);
        known = true;
        version++;
    }

    /** Deconnexion / autre serveur : plus rien de connu. */
    public void clear() {
        rows = List.of();
        known = false;
        version++;
    }

    /** Une liste a-t-elle deja ete recue depuis la connexion ? */
    public boolean known() {
        return known;
    }

    /** Change a chaque reception (la roue ouverte se met a jour). */
    public int version() {
        return version;
    }

    public List<PacketPetList.Row> rows() {
        return rows;
    }

    /**
     * Lignes a montrer, triees ({@link #ORDER}), vues depuis la dimension du joueur MAINTENANT : bete que le client voit dans cette dimension
     * -> distance mesuree ({@code live} : distance arrondie, ou -1 si le client ne la voit pas) ; meme dimension sans la voir -> distance du
     * serveur ; autre dimension -> -1 (nom de la dimension affiche). Une liste recue avant un changement de dimension reste ainsi juste.
     */
    public List<PacketPetList.Row> view(String playerDim, ToIntFunction<UUID> live) {
        List<PacketPetList.Row> out = new ArrayList<>(rows.size());
        for (PacketPetList.Row r : rows) {
            int d = -1, seen = -1;
            if (r.dim().equals(playerDim)) {
                seen = live.applyAsInt(r.id());
                d = seen >= 0 ? seen : r.distance();
            }
            out.add(d == r.distance() ? r : new PacketPetList.Row(r.id(), r.kind(), r.type(), r.name(), r.dim(), d, r.loaded() || seen >= 0));
        }
        out.sort(ORDER);
        return out;
    }

    /**
     * Touche relachee apres {@code heldMs} millisecondes, secteur vise {@code hovered} (-1 : centre) : un animal vise est rappele seul ; au
     * centre, un appui bref rappelle TOUS les animaux (comme avant), un maintien n'en rappelle aucun (annuler).
     */
    public static int decide(long heldMs, int hovered) {
        if (hovered >= 0) {
            return ONE;
        }
        return heldMs < HOLD_MS ? ALL : NONE;
    }
}

package fr.pioneers.world.pet;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 1.0.5-beta (lot 33, retour 45 : « des fois elle apparait comme vide, et elle a de la latence ») : envoi PUR de la liste de la roue du
 * rappel ({@link PacketPetList}), sans classe du jeu (banc {@code tools/pets/wheel_open_bench.sh}).
 * <p>
 * Cause mesuree : une demande de liste arrivee moins de {@link #GAP} ticks apres la precedente etait JETEE par le serveur (paquet sans
 * reponse) ; le client redemandait {@code PetWheelScreen.RETRY_TICKS} (25) ticks plus tard : roue vide 1,25 s au moins a chaque reouverture
 * rapide. Desormais une demande trop rapprochee est DUE (servie des que le delai le permet, jamais perdue), et le serveur POUSSE la liste
 * sans qu'on la demande : a la connexion, au changement de dimension, a la reapparition et quand un animal du joueur change (apprivoise,
 * nomme, mort, change de maitre ou de dimension) ; le client la garde en cache ({@code client/pet/WheelCache}).
 */
public final class PetListSync {

    /** Ecart minimal entre deux envois de la liste a un meme joueur (ticks du serveur). */
    public static final int GAP = 20;
    /** File du serveur (videe a l'arret du serveur). */
    public static final PetListSync SERVER = new PetListSync();

    private final Map<UUID, Long> last = new HashMap<>();
    private final Set<UUID> owed = new LinkedHashSet<>();

    /**
     * Demande de la liste par un joueur a l'heure {@code now} : vrai = l'envoyer maintenant (heure notee) ; faux = trop tot, l'envoi est DU et
     * partira par {@link #due} des que le delai sera passe.
     */
    public boolean ask(UUID player, long now) {
        Long l = last.get(player);
        if (PetRules.waiting(l, now, GAP)) {
            owed.add(player);
            return false;
        }
        last.put(player, now);
        owed.remove(player);
        return true;
    }

    /** La liste du joueur a change (ou il vient d'arriver) : un envoi est du au prochain passage permis. */
    public void owe(UUID player) {
        owed.add(player);
    }

    /** Rien a envoyer (le tick du serveur s'arrete la, sans allocation). */
    public boolean idle() {
        return owed.isEmpty();
    }

    /** Les joueurs a servir a {@code now} (retires de la file, heure notee) sont ajoutes a {@code out}. */
    public void due(long now, List<UUID> out) {
        for (Iterator<UUID> it = owed.iterator(); it.hasNext(); ) {
            UUID p = it.next();
            if (!PetRules.waiting(last.get(p), now, GAP)) {
                last.put(p, now);
                it.remove();
                out.add(p);
            }
        }
    }

    /** Joueur parti : plus rien de du. */
    public void forget(UUID player) {
        owed.remove(player);
        last.remove(player);
    }

    public boolean isOwed(UUID player) {
        return owed.contains(player);
    }

    public void clear() {
        owed.clear();
        last.clear();
    }
}

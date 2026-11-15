package fr.pioneers.clan.core;

import fr.pioneers.clan.Clan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * 1.9 (MAJ RP, bloc 4) : logique PURE des membres (aucune classe du serveur, testee par le banc) : entree et sortie d'un membre, conversion
 * du clan solo en clan ordinaire et retour au solo, effectif maximal, absence du Representant et choix du successeur selon le regime.
 */
public final class ClanLogic {

    /** Ce qu'a change une entree ou une sortie. */
    public enum Change {
        JOINED, CONVERTED, LEFT, BACK_TO_SOLO, EMPTY, REFUSED
    }

    /** Successeur : {@code kind} = none, direct (designe tout de suite), election (election d'urgence) ou council (le Conseil choisit). */
    public record Succession(String kind, @Nullable UUID who) {

        public static final Succession NONE = new Succession("none", null);
    }

    /** Cle de {@link Clan#extra} : le Representant d'un clan converti choisit son regime une fois, librement. */
    public static final String CHOOSE_REGIME = "core.choose";

    private ClanLogic() {
    }

    /** Effectif maximal (tableau des niveaux, plafond de configuration). */
    public static int maxMembers(Clan c, ClanDefs defs, int configCap) {
        ClanDefs.LevelDef d = defs.level(c.level);
        int base = d == null ? 8 + 4 * (Math.max(1, c.level) - 1) : d.members();
        return Math.max(1, Math.min(configCap, base));
    }

    /** Ajoute un membre (role de Recrue). Un clan solo qui recoit un deuxieme joueur devient un clan ordinaire (regime a choisir). */
    public static Change addMember(Clan c, UUID player, String account, String character, long now, int max) {
        if (c.members.containsKey(player)) {
            return Change.REFUSED;
        }
        if (c.members.size() >= max) {
            return Change.REFUSED;
        }
        Clan.Member m = new Clan.Member(player, ClanRoles.RECRUIT, now);
        m.account = account == null ? "" : account;
        m.character = character == null ? "" : character;
        c.members.put(player, m);
        c.emptySince = 0;
        if (c.members.size() == 1) {
            ClanRoles.setRepresentative(c, player);
            return Change.JOINED;
        }
        if (c.solo) {
            c.solo = false;
            c.regime = ClanDefs.EMPIRE;
            c.extra.putBoolean(CHOOSE_REGIME, true);
            return Change.CONVERTED;
        }
        return Change.JOINED;
    }

    /**
     * Retire un membre. Le dernier compagnon parti, le clan redevient solo (le restant le represente) ; plus personne : clan vide. Si le
     * Representant part, l'appelant designe le successeur ({@link #successor}).
     */
    public static Change removeMember(Clan c, UUID player, long now) {
        if (c.members.remove(player) == null) {
            return Change.REFUSED;
        }
        c.council.remove(player);
        if (player.equals(c.heir)) {
            c.heir = null;
        }
        if (c.members.isEmpty()) {
            c.representative = null;
            c.emptySince = now;
            return Change.EMPTY;
        }
        if (c.members.size() == 1 && !c.solo) {
            UUID last = c.members.keySet().iterator().next();
            c.solo = true;
            c.regime = ClanDefs.SOLO;
            c.council.clear();
            c.heir = null;
            c.pendingRegime = "";
            c.extra.remove(CHOOSE_REGIME);
            ClanRoles.setRepresentative(c, last);
            return Change.BACK_TO_SOLO;
        }
        if (player.equals(c.representative)) {
            c.representative = null;
        }
        return Change.LEFT;
    }

    /** Le Representant est-il absent depuis trop longtemps (30 jours reels) ? */
    public static boolean representativeAbsent(Clan c, long now, long absenceMs) {
        if (c.representative == null) {
            return c.members.size() > 0;
        }
        Clan.Member m = c.member(c.representative);
        return m == null || now - m.lastSeen > absenceMs;
    }

    /**
     * Successeur selon le regime (Representant parti ou absent). Empire et solo : l'heritier s'il est actif, sinon le plus ancien membre
     * actif. Democratie : interim au plus haut rang et election d'urgence. Conseil : interim au plus ancien conseiller actif, puis le
     * Conseil choisit. Cooperative : le porte-parole suivant.
     */
    public static Succession successor(Clan c, ClanDefs.Constitution k, long now, long activeMs) {
        UUID out = c.representative;
        String how = k == null ? "heir" : k.succession();
        switch (how) {
            case "emergency_election" -> {
                UUID who = highest(c, now, activeMs, out);
                return new Succession("election", who);
            }
            case "council" -> {
                UUID who = null;
                for (UUID u : oldestFirst(c, out)) {
                    if (c.council.contains(u) && active(c, u, now, activeMs)) {
                        who = u;
                        break;
                    }
                }
                return new Succession("council", who != null ? who : highest(c, now, activeMs, out));
            }
            case "rotation" -> {
                return new Succession("direct", nextSpokesperson(c, now, activeMs, out));
            }
            default -> {
                if (c.heir != null && !c.heir.equals(out) && active(c, c.heir, now, activeMs)) {
                    return new Succession("direct", c.heir);
                }
                return new Succession("direct", oldestActive(c, now, activeMs, out));
            }
        }
    }

    /** Porte-parole suivant de la Cooperative : le membre actif qui suit l'actuel dans l'ordre d'anciennete (en boucle). */
    @Nullable
    public static UUID nextSpokesperson(Clan c, long now, long activeMs, @Nullable UUID current) {
        List<UUID> order = oldestFirst(c, null);
        if (order.isEmpty()) {
            return null;
        }
        int start = current == null ? -1 : order.indexOf(current);
        for (int k = 1; k <= order.size(); k++) {
            UUID u = order.get(Math.floorMod(start + k, order.size()));
            if (!u.equals(current) && active(c, u, now, activeMs)) {
                return u;
            }
        }
        return current != null && c.members.containsKey(current) ? current : order.get(0);
    }

    /** Plus ancien membre actif (autre que {@code except}), sinon le plus ancien tout court. */
    @Nullable
    public static UUID oldestActive(Clan c, long now, long activeMs, @Nullable UUID except) {
        List<UUID> order = oldestFirst(c, except);
        for (UUID u : order) {
            if (active(c, u, now, activeMs)) {
                return u;
            }
        }
        return order.isEmpty() ? null : order.get(0);
    }

    /** Membre actif au plus haut rang (egalite : le plus ancien), sinon le plus ancien. */
    @Nullable
    public static UUID highest(Clan c, long now, long activeMs, @Nullable UUID except) {
        UUID best = null;
        int bestRank = -1;
        for (UUID u : oldestFirst(c, except)) {
            if (!active(c, u, now, activeMs)) {
                continue;
            }
            int r = c.roles.containsKey(c.members.get(u).role) ? c.roles.get(c.members.get(u).role).rank : 0;
            if (r > bestRank) {
                best = u;
                bestRank = r;
            }
        }
        return best != null ? best : oldestActive(c, now, activeMs, except);
    }

    /** Membres du plus ancien au plus recent (egalite : identifiant), sans {@code except}. */
    public static List<UUID> oldestFirst(Clan c, @Nullable UUID except) {
        List<Clan.Member> l = new ArrayList<>(c.members.values());
        l.sort(Comparator.<Clan.Member>comparingLong(m -> m.joined).thenComparing(m -> m.id));
        List<UUID> out = new ArrayList<>();
        for (Clan.Member m : l) {
            if (!m.id.equals(except)) {
                out.add(m.id);
            }
        }
        return out;
    }

    public static boolean active(Clan c, UUID u, long now, long activeMs) {
        Clan.Member m = c.member(u);
        return m != null && now - m.lastSeen <= activeMs;
    }

    public static int activeCount(Clan c, long now, long activeMs) {
        int n = 0;
        for (Clan.Member m : c.members.values()) {
            if (now - m.lastSeen <= activeMs) {
                n++;
            }
        }
        return n;
    }
}

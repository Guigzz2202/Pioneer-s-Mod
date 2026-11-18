package fr.pioneers.society.clan.core;

import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.Permission;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/**
 * 1.9 (MAJ RP, bloc 4) : roles et permissions d'un clan. Le Representant (rang 100, unique) et la Recrue (rang le plus bas) existent
 * toujours ; 12 roles au plus ; on n'agit que sur des rangs INFERIEURS au sien. La permission effective tient compte du regime : une
 * permission reservee par le regime n'est exercee que par le Representant ; en Conseil, les conseillers exercent celles des decisions qui
 * reviennent au Conseil. Methodes pures sur {@link Clan} (banc) : elles renvoient la cle de langue du refus, ou {@code null}.
 */
public final class ClanRoles {

    public static final String REPRESENTATIVE = "representative", RECRUIT = "recruit";

    private ClanRoles() {
    }

    /** Installe les roles proposes (creation d'un clan ou d'un clan solo). */
    public static void installDefaults(Clan c, ClanDefs defs) {
        c.roles.clear();
        for (ClanDefs.RoleDef d : defs.roles.values()) {
            Clan.Role r = new Clan.Role(d.id(), "", d.color(), d.rank(), d.withdrawCap());
            for (String p : d.perms()) {
                Permission perm = Permission.byId(p);
                if (perm != null) {
                    r.perms.add(perm);
                }
            }
            c.roles.put(r.id, r);
        }
        c.extra.putInt(ROLES_VERSION, ROLES_V);
    }

    /** 1.0.4-beta (lot 8) : version des roles proposes ({@code Clan.extra}) : 2 = Arpenteur et Recruteur, achat de claims. */
    public static final String ROLES_VERSION = "roles_v";
    public static final int ROLES_V = 2;

    /**
     * 1.0.4-beta (lot 8) : migration UNE fois par clan (clans d'avant le lot 8) : les roles proposes qui manquent (Arpenteur, Recruteur) sont
     * ajoutes avec leurs permissions par defaut s'il reste de la place ; les roles existants gardent EXACTEMENT leurs permissions, plus
     * « Acheter des claims » pour ceux qui pouvaient deja acheter (Reclamer + Retirer). Les permissions reservees par le regime, qu'ils
     * portaient sans pouvoir les exercer, deviennent effectives (delegation) : aucun droit perdu. Renvoie vrai si le clan a change.
     */
    public static boolean migrate(Clan c, ClanDefs defs) {
        if (c.extra.getInt(ROLES_VERSION) >= ROLES_V) {
            return false;
        }
        c.extra.putInt(ROLES_VERSION, ROLES_V);
        int maxRoles = defs.rules == null ? 12 : defs.rules.maxRoles();
        for (Clan.Role r : c.roles.values()) {
            if (r.perms.contains(Permission.CLAIM) && r.perms.contains(Permission.WITHDRAW)) {
                r.perms.add(Permission.BUY_CLAIMS);
            }
        }
        if (!c.solo) {
            for (String id : List.of("surveyor", "recruiter")) {
                ClanDefs.RoleDef d = defs.roles.get(id);
                if (d == null || c.roles.containsKey(id) || c.roles.size() >= maxRoles) {
                    continue;
                }
                Clan.Role r = new Clan.Role(d.id(), "", d.color(), d.rank(), d.withdrawCap());
                for (String p : d.perms()) {
                    Permission perm = Permission.byId(p);
                    if (perm != null) {
                        r.perms.add(perm);
                    }
                }
                c.roles.put(r.id, r);
            }
        }
        return true;
    }

    /** Rang le plus bas des roles (celui de la Recrue). */
    public static int lowestRank(Clan c) {
        Clan.Role r = c.roles.get(RECRUIT);
        return r == null ? 1 : r.rank;
    }

    /** Permission effective d'un membre, regime compris. */
    public static boolean has(Clan c, UUID player, Permission p) {
        Clan.Member m = c.member(player);
        if (m == null) {
            return false;
        }
        if (player.equals(c.representative)) {
            return true;
        }
        ClanDefs defs = ClanDefs.get();
        //1.0.4-beta (lot 8) : une permission « reservee » par le regime (decision du Representant) n'est plus refusee aux roles : le
        //Representant la DELEGUE en la cochant sur un role (matrice de la fiche Roles) ; ClanGov.decide l'applique en son nom
        Clan.Role r = c.roles.get(m.role);
        if (r != null && r.has(p)) {
            return true;
        }
        //en Conseil, les conseillers portent les decisions qui leur reviennent
        ClanDefs.Constitution k = defs.regime(c.regime);
        ClanDefs.PermDef d = defs.perms.get(p.id());
        if (k != null && d != null && !d.decision().isEmpty() && c.council.contains(player)) {
            ClanDefs.Rule rule = k.rule(d.decision());
            return rule != null && rule.by() == ClanDefs.Decider.COUNCIL;
        }
        return false;
    }

    public static int rank(Clan c, UUID player) {
        if (player.equals(c.representative)) {
            return 100;
        }
        return c.rankOf(player);
    }

    /** L'acteur peut-il agir sur ce rang (strictement inferieur au sien) ? */
    public static boolean above(Clan c, UUID actor, int targetRank) {
        return rank(c, actor) > targetRank;
    }

    /** Donne un role a un membre (permission de promouvoir, rangs inferieurs seulement ; jamais le role de Representant). */
    @Nullable
    public static String assign(Clan c, UUID actor, UUID target, String roleId) {
        Clan.Member t = c.member(target);
        Clan.Role role = c.roles.get(roleId);
        if (t == null) {
            return "clan.pioneers.error.not_member";
        }
        if (role == null) {
            return "clan.pioneers.error.no_role";
        }
        if (!has(c, actor, Permission.PROMOTE)) {
            return "clan.pioneers.error.permission";
        }
        if (actor.equals(target) || REPRESENTATIVE.equals(roleId) || target.equals(c.representative)) {
            return "clan.pioneers.error.rank";
        }
        if (!above(c, actor, rank(c, target)) || !above(c, actor, role.rank)) {
            return "clan.pioneers.error.rank";
        }
        t.role = roleId;
        return null;
    }

    /** Passe le titre de Representant : l'ancien prend le role du nouveau (echange), le nouveau prend le role de Representant. */
    public static void setRepresentative(Clan c, UUID next) {
        Clan.Member n = c.member(next);
        if (n == null) {
            return;
        }
        UUID prev = c.representative;
        String nextRole = REPRESENTATIVE.equals(n.role) ? RECRUIT : n.role;
        if (prev != null && !prev.equals(next)) {
            Clan.Member p = c.member(prev);
            if (p != null) {
                p.role = c.roles.containsKey(nextRole) ? nextRole : RECRUIT;
            }
        }
        //aucun autre membre ne garde le role de Representant
        for (Clan.Member m : c.members.values()) {
            if (!m.id.equals(next) && REPRESENTATIVE.equals(m.role)) {
                m.role = RECRUIT;
            }
        }
        n.role = REPRESENTATIVE;
        c.representative = next;
        if (next.equals(c.heir)) {
            c.heir = null;
        }
    }

    // ------------------------------------------------------------------ modification des roles (decision « roles »)
    /**
     * Charge utile d'une modification : {@code op} (create, delete, perm, rank, cap, rename, color), {@code role}, puis selon l'operation
     * {@code name}, {@code rank}, {@code color}, {@code perm} + {@code on}, {@code cap}. Verifie AVANT d'ecrire.
     */
    @Nullable
    public static String check(Clan c, CompoundTag d, ClanDefs defs, @Nullable UUID actor) {
        String op = d.getString("op"), id = d.getString("role");
        Clan.Role r = c.roles.get(id);
        int maxRoles = defs.rules == null ? 12 : defs.rules.maxRoles();
        switch (op) {
            case "create" -> {
                if (r != null || !id.matches("[a-z0-9_]{2,24}")) {
                    return "clan.pioneers.error.role_id";
                }
                if (c.roles.size() >= maxRoles) {
                    return "clan.pioneers.error.role_max";
                }
                int rank = d.getInt("rank");
                if (rank <= lowestRank(c) || rank >= 100) {
                    return "clan.pioneers.error.role_rank";
                }
                if (actor != null && !above(c, actor, rank)) {
                    return "clan.pioneers.error.rank";
                }
                return checkRoleName(d.getString("name"), defs);
            }
            case "delete", "perm", "rank", "cap", "rename", "color" -> {
                if (r == null) {
                    return "clan.pioneers.error.no_role";
                }
                if (REPRESENTATIVE.equals(id)) {
                    return "clan.pioneers.error.role_fixed";
                }
                if (actor != null && !above(c, actor, r.rank)) {
                    return "clan.pioneers.error.rank";
                }
                switch (op) {
                    case "delete" -> {
                        return RECRUIT.equals(id) ? "clan.pioneers.error.role_fixed" : null;
                    }
                    case "perm" -> {
                        Permission p = Permission.byId(d.getString("perm"));
                        if (p == null) {
                            return "clan.pioneers.error.no_perm";
                        }
                        //1.0.4-beta (lot 8) : toute permission se delegue ; hors Representant, on ne donne ni ne retire que ce que l'on a
                        if (actor != null && !actor.equals(c.representative) && !has(c, actor, p)) {
                            return "clan.pioneers.error.perm_not_own";
                        }
                        return null;
                    }
                    case "rank" -> {
                        int rank = d.getInt("rank");
                        if (rank < 1 || rank >= 100) {
                            return "clan.pioneers.error.role_rank";
                        }
                        if (RECRUIT.equals(id)) {
                            for (Clan.Role o : c.roles.values()) {
                                if (o != r && o.rank <= rank) {
                                    return "clan.pioneers.error.role_rank";
                                }
                            }
                        } else if (rank <= lowestRank(c)) {
                            return "clan.pioneers.error.role_rank";
                        }
                        return actor != null && !above(c, actor, rank) ? "clan.pioneers.error.rank" : null;
                    }
                    case "cap" -> {
                        return d.getLong("cap") < -1 ? "clan.pioneers.error.amount" : null;
                    }
                    case "rename" -> {
                        return checkRoleName(d.getString("name"), defs);
                    }
                    default -> {
                        return d.getInt("color") < 0 || d.getInt("color") > 15 ? "clan.pioneers.error.color" : null;
                    }
                }
            }
            default -> {
                return "clan.pioneers.error.invalid";
            }
        }
    }

    @Nullable
    private static String checkRoleName(String name, ClanDefs defs) {
        String n = ClanNames.cleanName(name);
        if (n.isEmpty()) {
            return null;
        }
        if (n.length() > 24) {
            return "clan.pioneers.error.name_length";
        }
        return defs.rules != null && ClanNames.banned(n, defs.rules) ? "clan.pioneers.error.name_banned" : null;
    }

    /** Applique une modification deja verifiee (revalidee ici : un vote a pu laisser passer du temps). */
    @Nullable
    public static String apply(Clan c, CompoundTag d, ClanDefs defs) {
        String err = check(c, d, defs, null);
        if (err != null) {
            return err;
        }
        String op = d.getString("op"), id = d.getString("role");
        Clan.Role r = c.roles.get(id);
        switch (op) {
            case "create" -> {
                String name = ClanNames.cleanName(d.getString("name"));
                Clan.Role n = new Clan.Role(id, name.isEmpty() ? id : name, Math.max(0, Math.min(15, d.getInt("color"))), d.getInt("rank"), 0);
                c.roles.put(id, n);
            }
            case "delete" -> {
                c.roles.remove(id);
                for (Clan.Member m : c.members.values()) {
                    if (id.equals(m.role)) {
                        m.role = RECRUIT;
                    }
                }
            }
            case "perm" -> {
                Permission p = Permission.byId(d.getString("perm"));
                if (d.getBoolean("on")) {
                    r.perms.add(p);
                } else {
                    r.perms.remove(p);
                }
            }
            case "rank" -> r.rank = d.getInt("rank");
            case "cap" -> r.withdrawCap = d.getLong("cap");
            case "rename" -> r.name = ClanNames.cleanName(d.getString("name"));
            default -> r.color = d.getInt("color");
        }
        return null;
    }

    /** Roles tries par rang decroissant. */
    public static List<Clan.Role> ordered(Clan c) {
        List<Clan.Role> l = new ArrayList<>(c.roles.values());
        l.sort((a, b) -> Integer.compare(b.rank, a.rank));
        return l;
    }

    /** Nom affiche d'un role : nom choisi par le clan, sinon cle de langue du role propose (prefixe « # »). */
    public static String displayName(Clan.Role r) {
        if (r.name == null || r.name.isBlank()) {
            return ClanDefs.get().roles.containsKey(r.id) ? "#clan.pioneers.role." + r.id.toLowerCase(Locale.ROOT) : r.id;
        }
        return r.name;
    }
}

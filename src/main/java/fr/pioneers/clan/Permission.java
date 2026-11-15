package fr.pioneers.clan;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * 1.9 (MAJ RP, bloc 4) : les 25 permissions (26 depuis la 1.0.4-beta, lot 8 : {@code buy_claims}) d'un role de clan, en cinq familles (Membres, Territoire, Tresor, Gouvernement, Communication).
 * Le regime en reserve certaines (constitution, {@code data/pioneers/clans/regimes/*.json}). Identifiants stables (donnees, langue
 * {@code clan.pioneers.perm.<id>}, icones).
 */
public enum Permission {
    //Membres
    INVITE("members"), KICK("members"), PROMOTE("members"), VIEW_IDENTITIES("members"), VIEW_STATS("members"),
    //Territoire
    CLAIM("territory"), ZONES("territory"), BUILD("territory"), CONTAINERS("territory"), MACHINES("territory"), TRANSIT("territory"),
    CHUNKLOAD("territory"), SEALS("territory"),
    //Tresor
    TREASURY_VIEW("treasury"), WITHDRAW("treasury"), SET_TAXES("treasury"), BONUSES("treasury"),
    //Gouvernement
    PROPOSE("government"), EDIT_ROLES("government"), EDIT_IDENTITY("government"), DECLARE_WAR("government"), DIPLOMACY("government"),
    SIEGE("government"),
    //Communication
    MODERATE("communication"), PIN("communication"),
    //1.0.4-beta (lot 8) : acheter des claims au tresor (avant : Reclamer + Retirer ; ajoute EN FIN, les identifiants restent stables)
    BUY_CLAIMS("territory");

    public final String family;

    Permission(String family) {
        this.family = family;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Permission byId(String id) {
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Set<Permission> all() {
        return EnumSet.allOf(Permission.class);
    }
}

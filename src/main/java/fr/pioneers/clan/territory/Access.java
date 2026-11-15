package fr.pioneers.clan.territory;

import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.Permission;
import java.util.function.Predicate;

/**
 * 1.9 (MAJ RP, bloc 4) : decision PURE de la protection d'un chunk reclame (banc : matrice de protection). {@code ClanApi.canModify} et les
 * evenements de protection lisent tous cette fonction ; l'index des claims a deja dit qui possede le chunk (une lecture, aucune boucle).
 */
public final class Access {

    private Access() {
    }

    /**
     * Ce que la decision doit savoir du chunk et de l'acteur. 1.0.3-beta (lot 16) : {@code visitor} (categorie du visiteur, lue seulement
     * hors du clan) et {@code matrix} (permissions des visiteurs reglees par le clan proprietaire, zone par zone).
     */
    public record Case(ClanApi.Relation relation, Zone zone, int minRank, int rank, Predicate<Permission> perms, boolean federalSameFaction,
                       boolean sealed, boolean sealedForMe, ZonePerms.Visitor visitor, ZonePerms matrix) {

        /** Cas d'avant le lot 16 : visiteur d'apres la seule relation (sans clan = neutre), matrice par defaut. */
        public Case(ClanApi.Relation relation, Zone zone, int minRank, int rank, Predicate<Permission> perms, boolean federalSameFaction,
                    boolean sealed, boolean sealedForMe) {
            this(relation, zone, minRank, rank, perms, federalSameFaction, sealed, sealedForMe, ZonePerms.visitor(relation, false),
                  ZonePerms.DEFAULTS);
        }
    }

    /** Zone du chunk (copie de {@code Clan.Zone}, pour un banc sans le jeu). */
    public enum Zone {
        MEMBERS, PRIVATE, OPEN
    }

    /**
     * L'acteur peut-il faire {@code action} ici ? Membres : selon les permissions de leur role (BUILD, CONTAINERS, MACHINES), et le rang
     * minimal d'une zone Privee. Conteneurs scelles apres une capture : seuls les anciens proprietaires les ouvrent (avant tout). Zone
     * federale : les clans de la faction y font tout sauf casser, poser et cultiver. Autres visiteurs : 1.0.3-beta (lot 16) la matrice du
     * clan proprietaire ({@link ZonePerms}) pour la zone du chunk et la categorie du visiteur ; zone Privee toujours fermee. Par defaut la
     * matrice redonne l'ancienne regle : zone Ouverte, tout le monde passe et utilise portes et boutons, un allie y ouvre aussi conteneurs
     * et machines (decision no 39) ; personne d'autre que les membres ne casse ni ne pose.
     */
    public static boolean decide(Case c, ClanApi.Action action) {
        if (c.sealed() && action == ClanApi.Action.CONTAINER) {
            return c.sealedForMe();
        }
        if (c.relation() == ClanApi.Relation.SAME) {
            if (c.zone() == Zone.PRIVATE && c.rank() < c.minRank()) {
                return false;
            }
            return switch (action) {
                case BREAK, PLACE, FARMLAND -> c.perms().test(Permission.BUILD);
                case CONTAINER -> c.perms().test(Permission.CONTAINERS);
                case MACHINE, VEHICLE -> c.perms().test(Permission.MACHINES);
                default -> true;
            };
        }
        if (c.federalSameFaction()) {
            //1.0.3-beta (lot 16) : cultiver (labour, engrais, recolte) etait une pose ou une casse : toujours refuse ici
            return action != ClanApi.Action.BREAK && action != ClanApi.Action.PLACE && action != ClanApi.Action.FARMLAND;
        }
        if (c.zone() == Zone.PRIVATE || c.matrix() == null || c.visitor() == null) {
            return false;
        }
        return c.matrix().allows(c.zone(), c.visitor(), ZonePerms.Group.of(action));
    }
}

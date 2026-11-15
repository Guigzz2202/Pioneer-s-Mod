package fr.pioneers.society.clan.territory;

import fr.pioneers.society.clan.ClanApi;
import java.util.Arrays;
import java.util.Locale;

/**
 * 1.0.3-beta (lot 16, Guillaume : « ajoute des permissions pour les chunks claims : etrangers, clans allies, neutres, ennemis ») : PUR (banc
 * {@code tools/rptest/TerritoryBench}). Ce qu'un VISITEUR (joueur hors du clan proprietaire) peut faire dans un chunk reclame, reglable par
 * le clan PAR ZONE : une matrice categorie de visiteur x groupe d'actions pour la zone Membres et une pour la zone Ouverte. La zone Privee
 * reste fermee a tout visiteur. Valeur immuable : {@link #with} rend une copie.
 * <p>
 * Defauts = l'ancienne regle de {@code Access.decide} : zone Membres fermee ; zone Ouverte : tout le monde interagit (portes, boutons,
 * leviers) et prend les vehicules, un allie ouvre aussi conteneurs et machines (decision no 39). Un clan qui ne touche a rien ne voit donc
 * aucun changement.
 */
public final class ZonePerms {

    /** Categorie d'un visiteur, d'apres sa relation au clan proprietaire. */
    public enum Visitor {
        /** Clan allie (traite d'alliance, meme faction, traite federal). */
        ALLY,
        /** Membre d'un autre clan, sans traite ni guerre. */
        NEUTRAL,
        /** Joueur sans clan (ou Solitaire : un clan solo n'est pas un vrai clan). */
        STRANGER,
        /** Clan en guerre contre le proprietaire. */
        ENEMY;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Groupes d'actions reglables (les actions protegees de {@code ClanApi.Action}, regroupees lisiblement). */
    public enum Group {
        /** Portes, trappes, portillons, boutons, leviers, cloches, etablis sans stock. */
        INTERACT,
        /** Coffres, tonneaux, fours, entonnoirs, lutrins, supports d'armure, cadres (ouvrir). */
        CONTAINERS,
        /** Machines et buffers du mod. */
        MACHINES,
        /** Bateaux, wagonnets, montures, vehicules du mod. */
        VEHICLES,
        /** Casser et poser des blocs, seaux, reglages qui changent le monde. */
        BUILD,
        /** Cultures : labourer, semer, recolter, engrais. */
        CROPS,
        /** Animaux : nourrir, traire, tondre, mener en laisse. */
        ANIMALS,
        /** Frapper ou tuer un animal. */
        HUNT;

        public int bit() {
            return 1 << ordinal();
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Groupe d'une action protegee. */
        public static Group of(ClanApi.Action a) {
            return switch (a) {
                case INTERACT -> INTERACT;
                case CONTAINER -> CONTAINERS;
                case MACHINE -> MACHINES;
                case VEHICLE -> VEHICLES;
                case BREAK, PLACE -> BUILD;
                case FARMLAND -> CROPS;
                case ENTITY -> ANIMALS;
                case ATTACK_ANIMAL -> HUNT;
            };
        }
    }

    /** Zones qui ont une matrice (la zone Privee n'en a pas : fermee aux visiteurs). */
    public static final Access.Zone[] ZONES = {Access.Zone.MEMBERS, Access.Zone.OPEN};
    /** Tous les groupes. */
    public static final int ALL = (1 << Group.values().length) - 1;
    private static final int V = Visitor.values().length;

    /** Defauts (ancienne regle). */
    public static final ZonePerms DEFAULTS = new ZonePerms(defaultMasks());

    /** Masque de groupes, case [zone * 4 + visiteur] (zone : 0 = Membres, 1 = Ouverte). */
    private final int[] masks;

    private ZonePerms(int[] masks) {
        this.masks = masks;
    }

    private static int[] defaultMasks() {
        int[] m = new int[ZONES.length * V];
        for (int z = 0; z < ZONES.length; z++) {
            for (Visitor v : Visitor.values()) {
                m[z * V + v.ordinal()] = defaultMask(ZONES[z], v);
            }
        }
        return m;
    }

    /** Masque par defaut : la regle d'avant le lot 16. */
    public static int defaultMask(Access.Zone zone, Visitor v) {
        if (zone != Access.Zone.OPEN) {
            return 0;
        }
        int m = Group.INTERACT.bit() | Group.VEHICLES.bit();
        return v == Visitor.ALLY ? m | Group.CONTAINERS.bit() | Group.MACHINES.bit() : m;
    }

    private static int zoneIndex(Access.Zone zone) {
        return switch (zone) {
            case MEMBERS -> 0;
            case OPEN -> 1;
            case PRIVATE -> -1;
        };
    }

    /** Masque des groupes permis a {@code v} dans {@code zone} (0 en zone Privee). */
    public int mask(Access.Zone zone, Visitor v) {
        int z = zoneIndex(zone);
        return z < 0 ? 0 : masks[z * V + v.ordinal()];
    }

    public boolean allows(Access.Zone zone, Visitor v, Group g) {
        return (mask(zone, v) & g.bit()) != 0;
    }

    /** Copie avec le masque de {@code v} dans {@code zone} change (zone Privee : inchange). */
    public ZonePerms with(Access.Zone zone, Visitor v, int mask) {
        int z = zoneIndex(zone);
        if (z < 0) {
            return this;
        }
        int[] m = masks.clone();
        m[z * V + v.ordinal()] = mask & ALL;
        return new ZonePerms(m);
    }

    /** Copie avec les defauts remis pour une zone. */
    public ZonePerms reset(Access.Zone zone) {
        ZonePerms out = this;
        for (Visitor v : Visitor.values()) {
            out = out.with(zone, v, defaultMask(zone, v));
        }
        return out;
    }

    public boolean isDefault() {
        return Arrays.equals(masks, DEFAULTS.masks);
    }

    /** Pour la sauvegarde et le reseau : 8 entiers (zone Membres puis Ouverte, visiteurs dans l'ordre de l'enum). */
    public int[] toArray() {
        return masks.clone();
    }

    /** Relu depuis {@link #toArray} ; tableau absent ou de mauvaise taille : defauts. */
    public static ZonePerms of(int[] a) {
        if (a == null || a.length != ZONES.length * V) {
            return DEFAULTS;
        }
        int[] m = a.clone();
        for (int i = 0; i < m.length; i++) {
            m[i] &= ALL;
        }
        return new ZonePerms(m);
    }

    /**
     * Categorie d'un visiteur : en guerre = ennemi, allie = allie, sinon sans clan (ou Solitaire) = etranger, sinon neutre. {@code SAME} n'est
     * pas un visiteur (renvoie neutre, jamais lu).
     */
    public static Visitor visitor(ClanApi.Relation rel, boolean noClan) {
        return switch (rel) {
            case WAR -> Visitor.ENEMY;
            case ALLY -> Visitor.ALLY;
            default -> noClan ? Visitor.STRANGER : Visitor.NEUTRAL;
        };
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ZonePerms z && Arrays.equals(masks, z.masks);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(masks);
    }
}

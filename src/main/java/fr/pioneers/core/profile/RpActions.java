package fr.pioneers.core.profile;

/**
 * 1.9 (MAJ RP) : numeros des actions des paquets {@link RpNet}. Plages : profil 0..99, ruines 100..199, economie 200..299, villages 300..399,
 * reputation 400..499, clans 500..699. Un numero ne change jamais (ajouts en fin de plage).
 */
public final class RpActions {

    private RpActions() {
    }

    // ------------------------------------------------------------------ profil (serveur -> client)
    public static final int DEFS = 0, PROFILE = 1, XP_GAIN = 2, IDENTITY_OF = 3, OPEN_INITIATION = 4, OPEN_NOTEBOOK = 5, OPEN_DESK = 6,
          TOAST = 7, CEREMONY = 8, READ_LORE = 9, FEATURES = 10;
    // ------------------------------------------------------------------ profil (client -> serveur) ; 52 (ancienne allure) retire en 1.12.0
    public static final int INIT_STEP = 50, INIT_SUBMIT = 51, RECONVERT = 53, REQUEST_PROFILE = 54, CANCEL_CEREMONY = 55;
    /**
     * 1.0.4-beta (lot 29) : teleportation d'un ADMINISTRATEUR (operateur, niveau 2) a un de ses waypoints, dans tous les modes de jeu
     * ({@code core/profile/WaypointTeleport}) ; mode partie du guide : demande (client -> serveur) et vue de la partie (serveur -> client,
     * {@code guide/party/PartyService}).
     */
    public static final int WAYPOINT_TP = 56, PARTY_REQUEST = 57, PARTY_VIEW = 11;

    // ------------------------------------------------------------------ ruines
    public static final int CONSOLE_OPEN = 100, CONSOLE_STATE = 101, RESTORATION_VIEW = 102, LORE_PAGE = 103;
    public static final int CONSOLE_CODE = 150, RESTORATION_ACTION = 151;

    // ------------------------------------------------------------------ economie
    public static final int TRADE_VIEW = 200, BALANCE = 201, STALL_VIEW = 202, LEDGER = 203, MONEY_TOAST = 204;
    public static final int TRADE_BUY = 250, TRADE_SELL = 251, TRADE_QUOTE = 252, PAY = 253, STALL_EDIT = 254, STALL_BUY = 255, TRADE_SERVICE = 256;
    /**
     * 1.0.4-beta (lot 7) : contrats entre joueurs au taux horaire ({@code economy/contract}) : vue des contrats du joueur (serveur -> client,
     * aussi poussee a l'autre partie apres un changement) et demande (client -> serveur : creer, proposer, accepter, resilier...).
     */
    public static final int CONTRACT_VIEW = 205, CONTRACT_REQUEST = 257;
    /** 1.0.4-beta (lot 29) : boite aux lettres : nombre de lettres non lues du joueur (serveur -> client : le « ! » au-dessus de ses boites). */
    public static final int MAIL_STATE = 206;

    // ------------------------------------------------------------------ villages
    //302 : ancienne bulle de parole (retiree en 1.9.1, le dialogue s'ecrit dans la fiche) : numero reserve, jamais reutilise
    public static final int VILLAGER_VIEW = 300, BOARD_VIEW = 301, VILLAGER_PEOPLE = 303;
    /** 1.9.1 (retours de Guillaume) : ecran de l'emissaire Colporteur. */
    public static final int EMISSARY_VIEW = 304;
    public static final int VILLAGER_TALK = 350, VILLAGER_TRADE = 351, VILLAGER_SHOW_BOOK = 352, BOARD_HIRE = 353, BOARD_REQUEST = 354;
    /** 1.9.1 (retours de Guillaume) : amende honorable, taches de reparation (Tableau), envoi d'un emissaire Colporteur. */
    public static final int BOARD_AMENDS = 355, BOARD_QUEST = 356, EMISSARY_SEND = 357;

    // ------------------------------------------------------------------ reputation
    public static final int REPUTATION_VIEW = 400, REPUTATION_TIER = 401;
    public static final int REPUTATION_REQUEST = 450;

    // ------------------------------------------------------------------ clans
    public static final int CLAN_VIEW = 500, CLAN_MAP = 501, CLAN_STATS = 502, CLAN_DIRECTORY = 503, CLAN_NOTICE = 504, CHARTER_VIEW = 505,
          SIEGE_STATE = 506, FACTION_VIEW = 507;
    /** 1.10.3 (minimap) : membres en ligne de son clan dans la meme dimension, toutes les 5 s, aux abonnes de la carte qui l'ont demande. */
    public static final int CLAN_MATES = 508;
    public static final int CLAN_REQUEST = 600, CLAN_ACTION = 601, CLAN_MAP_REQUEST = 602, CHARTER_EDIT = 603, FACTION_ACTION = 604;

    // ------------------------------------------------------------------ 1.0.5-beta (lot 16) : selecteur de joueur deja vu (EN FIN)
    /**
     * Liste des joueurs connus du serveur (connectes + deja venus : registre {@code KnownPlayers}), demandee par le selecteur du Carnet, de la
     * Bourse et de la Partie : demande {q, purpose} (q borne a 16 caracteres) ; reponse {purpose, list[{u, n, p, o, s}]} bornee a 200.
     */
    public static final int PLAYERS_VIEW = 12, PLAYERS_REQUEST = 58;
    /** Bacs a lettres et d'archives : action sur le bac ouvert {c (menu), op (take / read / deposit / mailbox), i (case)}. */
    public static final int PAPER_BIN = 59;

    // ------------------------------------------------------------------ 1.0.5-beta (lot 26) : onglet Documents du Carnet (EN FIN)
    /**
     * Documents (factures, contrats, lettres, devis, recus, reconnaissances de dette, bons de commande, actes de vente, procurations) : demande
     * {op (list / open / save / send / withdraw / accept / refuse / cancel / pay / delete / print), id, et pour « save » : type, toKind, to,
     * subject, body, lines[{l, a}], dues[{a, d}], send} ; vue {today, me, balance, msg, focus, list[...], doc (document ouvert)} bornee a
     * {@code DocumentService.VIEW_MAX} documents.
     */
    public static final int DOCUMENT_VIEW = 207, DOCUMENT_REQUEST = 258;
}

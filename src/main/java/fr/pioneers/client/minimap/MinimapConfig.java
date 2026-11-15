package fr.pioneers.client.minimap;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 1.10.3 : reglages de la minimap ({@code pioneers-client.toml}, section {@code minimap}). Comme {@code RpClientConfig}, la classe ne cite
 * AUCUNE classe client de Minecraft : elle est chargee aussi sur un serveur dedie (construction du fichier de reglages par
 * {@code PiClientConfig}). L'ecran de reglages ({@link MinimapSettingsScreen}) modifie ces valeurs et sauve le fichier.
 */
public final class MinimapConfig {

    private MinimapConfig() {
    }

    /** Coins de l'ecran. */
    public enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    /** Forme du cadre. */
    public enum Shape {
        SQUARE, ROUND
    }

    /** Lisere du cadre : acier, ou couleur d'un tier (MKI vert ... MKIV bordeaux), comme les buffers et les cables. */
    public enum Frame {
        STEEL, MK1, MK2, MK3, MK4
    }

    /** Dimensions a plafond (Nether) : plafond ignore (premiere surface ouverte sous le plafond), ou minimap masquee. */
    public enum Ceiling {
        IGNORE, HIDE
    }

    /**
     * 1.0.2 : rendu des waypoints dans le monde : BEACON (faisceau facon balise vanilla + nom et distance), POINT (losange face a la camera,
     * nom en petit, distance quand on le regarde), HIDDEN (rien dans le monde ; le waypoint reste sur la minimap et la carte).
     */
    public enum BeaconStyle {
        BEACON, POINT, HIDDEN
    }

    /** Echelles possibles (pixels d'interface par bloc), de la plus large a la plus serree. */
    public static final float[] ZOOMS = {0.125F, 0.25F, 0.5F, 1F, 1.5F, 2F, 3F, 4F, 6F, 8F};

    public static ForgeConfigSpec.BooleanValue ENABLED, ROTATE, COORDS, BIOME, TIME, ASTRE, SHADING, BIOME_TINT, WATER_DEPTH, NORTH_MARK, ENVIRONMENT;
    public static ForgeConfigSpec.IntValue SIZE, OFFSET_X, OFFSET_Y, ZOOM, OPACITY, TEXT_SCALE, MAP_ZOOM, CONFIG_VERSION;
    /** 1.12.1 (F1) : valeurs par defaut (demande de Guillaume : taille 60, texte 50 %, en haut a droite) et version des reglages. */
    public static final int DEFAULT_SIZE = 60, DEFAULT_TEXT = 50, VERSION = 2;
    public static final Corner DEFAULT_CORNER = Corner.TOP_RIGHT;
    /** Anciennes valeurs par defaut (1.10.3) : remplacees une fois par les nouvelles si le fichier les porte encore. */
    static final int OLD_SIZE = 112, OLD_TEXT = 75;
    static final Corner OLD_CORNER = Corner.TOP_LEFT;
    public static ForgeConfigSpec.EnumValue<Corner> CORNER;
    public static ForgeConfigSpec.EnumValue<Shape> SHAPE;
    public static ForgeConfigSpec.EnumValue<Frame> FRAME;
    public static ForgeConfigSpec.EnumValue<Ceiling> CEILING;
    /** 1.0.2 : rendu des waypoints dans le monde (reglage global ; chaque waypoint peut le remplacer). */
    public static ForgeConfigSpec.EnumValue<BeaconStyle> W_BEACON_STYLE;
    // calques
    public static ForgeConfigSpec.BooleanValue L_CLAIMS, L_RAIL, L_STATIONS, L_SEA, L_ROADS, L_ROBOTICS, L_POINTS, L_WAYPOINTS, L_ENTITIES, L_PLAYERS,
          L_MATES, L_GRID;
    // entites
    public static ForgeConfigSpec.BooleanValue E_HOSTILE, E_PASSIVE, E_ITEMS, E_VEHICLES, E_ROBOTS, E_NAMES, E_PLAYER_NAMES;
    public static ForgeConfigSpec.IntValue E_RADIUS, E_HEIGHT;
    // waypoints
    public static ForgeConfigSpec.BooleanValue W_BEACONS, W_DISTANCE, W_DEATHS, W_NAMES;
    public static ForgeConfigSpec.IntValue W_BEACON_RANGE, W_DEATH_KEEP;
    // couleurs (RGB)
    public static ForgeConfigSpec.IntValue C_HOSTILE, C_PASSIVE, C_ITEM, C_PLAYER, C_SELF, C_RAIL, C_ROAD, C_TEXT;
    // carte et cache
    public static ForgeConfigSpec.BooleanValue DISK_CACHE;
    public static ForgeConfigSpec.IntValue CHUNKS_PER_TICK, MAX_REGIONS;

    public static void build(ForgeConfigSpec.Builder b) {
        b.comment("1.10.3 : minimap (surface, calques, waypoints). Tout se regle aussi en jeu (carte plein ecran, bouton Reglages).").push("minimap");
        ENABLED = b.comment("Affiche la minimap.").define("enabled", true);
        SIZE = b.comment("Cote de la minimap (pixels d'interface).").defineInRange("size", DEFAULT_SIZE, 48, 256);
        CORNER = b.comment("Coin de l'ecran.").defineEnum("corner", DEFAULT_CORNER);
        OFFSET_X = b.comment("Decalage horizontal depuis le coin.").defineInRange("offsetX", 6, 0, 400);
        OFFSET_Y = b.comment("Decalage vertical depuis le coin.").defineInRange("offsetY", 6, 0, 400);
        SHAPE = b.comment("Forme : SQUARE (carree) ou ROUND (ronde).").defineEnum("shape", Shape.SQUARE);
        FRAME = b.comment("Lisere du cadre : STEEL (acier) ou MK1..MK4 (couleur du tier).").defineEnum("frame", Frame.STEEL);
        ZOOM = b.comment("Echelle de la minimap (indice de " + java.util.Arrays.toString(ZOOMS) + " pixels par bloc).").defineInRange("zoom", 3, 0,
              ZOOMS.length - 1);
        MAP_ZOOM = b.comment("Echelle de la carte plein ecran (meme table).").defineInRange("mapZoom", 2, 0, ZOOMS.length - 1);
        ROTATE = b.comment("Tourne avec le joueur (sinon le nord reste en haut).").define("rotate", false);
        NORTH_MARK = b.comment("Marque du nord sur le cadre.").define("northMark", true);
        OPACITY = b.comment("Opacite de la carte (%).").defineInRange("opacity", 100, 10, 100);
        TEXT_SCALE = b.comment("Taille des textes sous la minimap (%).").defineInRange("textScale", DEFAULT_TEXT, 50, 150);
        COORDS = b.comment("Coordonnees sous la minimap.").define("coords", true);
        BIOME = b.comment("Biome sous la minimap.").define("biome", true);
        TIME = b.comment("Heure du jour sous la minimap.").define("time", false);
        ASTRE = b.comment("Nom de l'astre (dimensions du mod) sous la minimap.").define("astre", true);
        ENVIRONMENT = b.comment("Ligne climat (temperature, humidite), oxygene et gravite sous la minimap.").define("environment", true);
        CONFIG_VERSION = b.comment("Version des reglages (ne pas modifier) : 0 = fichier d'avant la 1.12.1, ses anciennes valeurs par defaut sont "
                                   + "remplacees une fois.").defineInRange("configVersion", 0, 0, 99);
        SHADING = b.comment("Ombrage du relief.").define("shading", true);
        BIOME_TINT = b.comment("Herbe, feuillages et eau a la couleur du biome (sinon couleurs de carte vanilla).").define("biomeTint", true);
        WATER_DEPTH = b.comment("Eau plus sombre avec la profondeur.").define("waterDepth", true);
        CEILING = b.comment("Dimensions a plafond (Nether) : IGNORE (premiere surface ouverte sous le plafond) ou HIDE (minimap masquee).")
              .defineEnum("ceiling", Ceiling.IGNORE);
        b.push("layers");
        L_CLAIMS = b.comment("Territoires des clans (le tien, allies, autres, zones disputees).").define("claims", true);
        L_RAIL = b.comment("Voies ferrees.").define("rail", true);
        L_STATIONS = b.comment("Gares, ports et postes.").define("stations", true);
        L_SEA = b.comment("Balises et routes maritimes (routes : connues apres un passage sur la carte O).").define("sea", true);
        L_ROADS = b.comment("Routes robotiques.").define("roads", true);
        L_ROBOTICS = b.comment("Hubs robotiques, relais, terminaux, stations d'hypertube.").define("robotics", true);
        L_POINTS = b.comment("Points logistiques (balises et coffres).").define("points", true);
        L_WAYPOINTS = b.comment("Waypoints.").define("waypoints", true);
        L_ENTITIES = b.comment("Entites.").define("entities", true);
        L_PLAYERS = b.comment("Joueurs.").define("players", true);
        L_MATES = b.comment("Membres de ton clan hors de portee (position envoyee par le serveur toutes les 5 s).").define("mates", true);
        L_GRID = b.comment("Grille des chunks.").define("grid", false);
        b.pop();
        b.push("entities");
        E_HOSTILE = b.define("hostile", true);
        E_PASSIVE = b.define("passive", true);
        E_ITEMS = b.comment("Objets au sol.").define("items", false);
        E_VEHICLES = b.comment("Trains, navires, wagonnets, bateaux.").define("vehicles", true);
        E_ROBOTS = b.comment("Unites robotiques.").define("robots", true);
        E_NAMES = b.comment("Noms des creatures nommees.").define("names", false);
        E_PLAYER_NAMES = b.comment("Pseudos des joueurs.").define("playerNames", true);
        E_RADIUS = b.comment("Rayon de recherche des entites (blocs).").defineInRange("radius", 64, 8, 160);
        E_HEIGHT = b.comment("Ecart vertical maximal avec le joueur (blocs).").defineInRange("height", 32, 4, 384);
        b.pop();
        b.push("waypoints");
        W_BEACONS = b.comment("Ancien interrupteur des balises (avant les waypoints dans le monde de la 1.0.1-beta) : lu une fois par la migration (false -> beaconStyle = HIDDEN).")
              .define("beacons", true);
        W_BEACON_STYLE = b.comment("Waypoints dans le monde : BEACON (faisceau, nom et distance), POINT (losange, nom ; distance quand on le "
                                   + "regarde) ou HIDDEN (rien dans le monde).").defineEnum("beaconStyle", BeaconStyle.BEACON);
        W_DISTANCE = b.comment("Distance affichee sous les balises.").define("distance", true);
        W_NAMES = b.comment("Noms des waypoints sur la minimap.").define("names", false);
        W_BEACON_RANGE = b.comment("Distance maximale des balises (blocs, 0 = sans limite).").defineInRange("beaconRange", 0, 0, 100_000);
        W_DEATHS = b.comment("Point de mort automatique.").define("deaths", true);
        W_DEATH_KEEP = b.comment("Points de mort gardes.").defineInRange("deathKeep", 3, 1, 20);
        b.pop();
        b.push("colors");
        C_HOSTILE = b.comment("Couleurs RGB (decimal).").defineInRange("hostile", 0xE5484D, 0, 0xFFFFFF);
        C_PASSIVE = b.defineInRange("passive", 0x6CD46C, 0, 0xFFFFFF);
        C_ITEM = b.defineInRange("item", 0xF2D45C, 0, 0xFFFFFF);
        C_PLAYER = b.defineInRange("player", 0xFFFFFF, 0, 0xFFFFFF);
        C_SELF = b.defineInRange("self", 0x3FD0A0, 0, 0xFFFFFF);
        C_RAIL = b.defineInRange("rail", 0xC8CED6, 0, 0xFFFFFF);
        C_ROAD = b.defineInRange("road", 0xD8B070, 0, 0xFFFFFF);
        C_TEXT = b.defineInRange("text", 0xE0F5EA, 0, 0xFFFFFF);
        b.pop();
        b.push("cache");
        DISK_CACHE = b.comment("Garde la carte exploree sur le disque (<jeu>/pioneers/minimap/<monde>/<dimension>).").define("disk", true);
        CHUNKS_PER_TICK = b.comment("Chunks releves par tick au plus.").defineInRange("chunksPerTick", 3, 1, 16);
        MAX_REGIONS = b.comment("Regions de 256 x 256 blocs gardees en memoire (256 Ko chacune).").defineInRange("maxRegions", 96, 16, 512);
        b.pop();
        b.pop();
    }

    // ------------------------------------------------------------------ lecture sure (fichier pas encore charge, banc)
    public static boolean on(ForgeConfigSpec.BooleanValue v, boolean fallback) {
        try {
            return v == null ? fallback : v.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static int num(ForgeConfigSpec.IntValue v, int fallback) {
        try {
            return v == null ? fallback : v.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static <T extends Enum<T>> T pick(ForgeConfigSpec.EnumValue<T> v, T fallback) {
        try {
            return v == null ? fallback : v.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    /** Change une valeur (l'ecran de reglages) ; le fichier est ecrit par {@link #save()}. */
    public static <T> void set(ForgeConfigSpec.ConfigValue<T> v, T value) {
        try {
            v.set(value);
        } catch (IllegalStateException ignored) {
            //fichier pas encore charge : rien a faire
        }
    }

    public static void save() {
        try {
            fr.pioneers.common.PiClientConfig.SPEC.save();
        } catch (IllegalStateException ignored) {
            //fichier pas encore charge
        }
    }

    /**
     * 1.0.2 : version 2 : l'ancien interrupteur des balises coupe devient le rendu « Invisible ».
     * <p>
     * 1.12.1 (F1) : migration unique des reglages (appelee au premier tick de la minimap, fichier charge). Un fichier d'avant la 1.12.1 qui
     * porte encore les anciennes valeurs PAR DEFAUT (taille 112, texte 75 %, coin haut gauche) recoit les nouvelles ; une valeur choisie par
     * le joueur est gardee. Rend vrai si quelque chose a change (le fichier est alors sauve).
     */
    public static boolean migrate() {
        try {
            if (CONFIG_VERSION == null || CONFIG_VERSION.get() >= VERSION) {
                return false;
            }
            int from = CONFIG_VERSION.get();
            if (from < 1) {
                if (SIZE.get() == OLD_SIZE) {
                    SIZE.set(DEFAULT_SIZE);
                }
                if (TEXT_SCALE.get() == OLD_TEXT) {
                    TEXT_SCALE.set(DEFAULT_TEXT);
                }
                if (CORNER.get() == OLD_CORNER) {
                    CORNER.set(DEFAULT_CORNER);
                }
            }
            if (from < 2 && !W_BEACONS.get()) {
                //1.0.2 : balises coupees avant -> waypoints invisibles dans le monde
                W_BEACON_STYLE.set(BeaconStyle.HIDDEN);
                W_BEACONS.set(true);
            }
            CONFIG_VERSION.set(VERSION);
            save();
            return true;
        } catch (IllegalStateException e) {
            //fichier pas encore charge : on reessaiera
            return false;
        }
    }

    /** 1.0.2 : rendu global des waypoints dans le monde. */
    public static BeaconStyle beaconStyle() {
        return pick(W_BEACON_STYLE, BeaconStyle.BEACON);
    }

    public static float zoom() {
        return ZOOMS[Math.max(0, Math.min(ZOOMS.length - 1, num(ZOOM, 3)))];
    }
}

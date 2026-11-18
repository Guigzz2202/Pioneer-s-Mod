package fr.pioneers.world.season;

import fr.pioneers.world.season.SeasonClimate.Family;
import fr.pioneers.world.season.Seasons.Date;
import fr.pioneers.world.season.Seasons.Season;
import javax.annotation.Nullable;

/**
 * 1.0.5-beta (lot 2, specification §1.2) : TEINTE SAISONNIERE des vegetaux, pure (aucun etat du jeu ; testee hors jeu par
 * {@code tools/seasons/TintBench.java} avec les vraies couleurs de biome du jeu).
 * <p>
 * Une couleur de vegetal (celle que le jeu calcule par biome, ou la couleur fixe du bouleau, de l'epicea, du nenuphar, des feuilles
 * fruitieres) est tiree vers la COULEUR CIBLE de la saison avec une FORCE : {@code mix(couleur, cible ramenee a mi-chemin de la clarte de la couleur, force)}.
 * Cibles et forces dependent de la sorte de vegetal ({@link Kind}) et de la famille du biome :
 * <ul>
 *     <li>printemps vert tendre et lumineux ; ete vert profond ; automne : feuillus du jaune a l'orange puis au rouge au fil de la saison
 *     (bouleaux jaunes), herbe qui roussit ; hiver : herbe brun-vert eteinte, feuillus brun-gris ;</li>
 *     <li>coniferes : peu ; jungles et mangroves : presque pas ; deserts, badlands et savane : saison seche (jaune paille) et saison humide
 *     (vert) d'apres l'humidite saisonniere ({@link SeasonClimate#humidityOffset}).</li>
 * </ul>
 * La teinte avance par PALIERS ({@link Seasons#STEPS} par saison, {@link #phase}) : la couleur d'un palier est celle du milieu du palier,
 * fondue entre deux saisons pendant les 2 derniers / 2 premiers jours ({@link Seasons#blend}). L'eau n'est jamais teintee.
 */
public final class SeasonTint {

    private SeasonTint() {
    }

    /** Sortes de vegetaux teints (ordre = colonnes de la table : ajouter en fin). */
    public enum Kind {
        /** Herbe, fougeres, hautes herbes, canne a sucre, petales roses. */
        GRASS,
        /** Feuillus colores par biome sans essence propre (jungle, acacia, mangrove), lianes, feuilles des fruitiers a feuilles caduques. */
        FOLIAGE,
        /** Bouleau (couleur fixe en vanilla) : jaune vif a l'automne. */
        BIRCH,
        /** Epicea (couleur fixe en vanilla) et feuilles des fruitiers persistants : restent verts, vert sombre l'hiver. */
        CONIFER,
        /** Nenuphar (couleur fixe en vanilla). */
        LILY,
        /** 1.0.5-beta (lot 14) : chene : roux a l'automne. */
        OAK,
        /** 1.0.5-beta (lot 14) : chene noir (l'« erable » du jeu) : rouge vif a l'automne. */
        DARK_OAK
    }

    // 1.0.5-beta (lot 14, Guillaume : « les saisons doivent etre plus contrastees en couleur ») : cibles franches, forces relevees, la cible garde
    // l'essentiel de sa clarte (LIGHT_KEEP), une essence par couleur d'automne. Cibles RGB par saison (printemps, ete, automne, hiver).
    /** Herbe : printemps vert vif et frais, ete vert profond (qui seche en fin d'ete : {@link #GRASS_LATE_SUMMER}), automne paille, hiver brun-gris. */
    static final int[][] GRASS_TARGET = {{104, 226, 56}, {50, 132, 30}, {182, 156, 66}, {122, 108, 82}};
    static final double[] GRASS_FORCE = {0.66, 0.56, 0.72, 0.80};
    /** Herbe a la fin de l'ete (jaunie, un peu seche) : l'ete glisse du vert profond vers elle. */
    static final int[] GRASS_LATE_SUMMER = {120, 140, 48};
    /** Feuillus : printemps vert tendre, ete vert profond, automne en rampe ({@link #AUTUMN_RAMP}), hiver brun-gris eteint. */
    static final int[][] FOLIAGE_TARGET = {{110, 220, 60}, {40, 112, 26}, {226, 150, 40}, {112, 96, 80}};
    static final double[] FOLIAGE_FORCE = {0.66, 0.56, 0.90, 0.86};
    static final int[] FOLIAGE_LATE_SUMMER = {92, 124, 34};
    /** Rampe d'automne des feuillus sans essence : jaune, orange, rouge brique. */
    static final int[][] AUTUMN_RAMP = {{236, 196, 46}, {232, 128, 30}, {190, 60, 30}};
    /** Chene : ocre jaune, roux, brun roux. */
    static final int[][] OAK_RAMP = {{222, 176, 52}, {206, 106, 34}, {150, 66, 32}};
    /** Chene noir (erable du jeu) : orange vif, rouge, rouge sombre. */
    static final int[][] DARK_OAK_RAMP = {{236, 120, 36}, {214, 44, 30}, {150, 24, 28}};
    /** Bouleau : vert clair, vert, jaune d'or vif, brun-gris clair. */
    static final int[][] BIRCH_TARGET = {{150, 226, 72}, {70, 132, 36}, {248, 214, 48}, {150, 132, 102}};
    static final double[] BIRCH_FORCE = {0.62, 0.52, 0.92, 0.82};
    /** Coniferes : restent verts ; vert sombre l'hiver. */
    static final int[][] CONIFER_TARGET = {{86, 150, 74}, {54, 112, 54}, {60, 106, 58}, {30, 70, 50}};
    static final double[] CONIFER_FORCE = {0.25, 0.20, 0.22, 0.55};
    /** Saison seche (jaune paille) et humide (vert) des deserts, badlands et savanes. */
    static final int[] STRAW = {214, 186, 92}, WET = {86, 170, 50};
    static final double STRAW_FORCE = 0.68, WET_FORCE = 0.45;
    /** Tropiques (jungle, mangrove, savane boisee) : toujours verts, a peine plus sombres en saison seche ; jamais d'automne rouge. */
    static final int[][] TROPICAL_TARGET = {{92, 196, 60}, {56, 150, 38}, {74, 150, 44}, {70, 134, 48}};
    static final double TROPICAL_FORCE = 0.30;
    /** Marais : feuillus et herbe vert olive, automne brun olive (pas de rouge), hiver brun ; contraste adouci. */
    static final int[][] WETLAND_RAMP = {{178, 160, 64}, {156, 120, 52}, {120, 92, 50}};
    static final double WETLAND_FORCE = 0.6;
    /** Force par famille de biome (deserts et savanes : a part ; tropical : table propre). */
    static final double[] FAMILY_FORCE = {1.0, 0.85, 1.0, 1.0, 0.9, 0.6};
    /** Nenuphar : moitie de la force de l'herbe. */
    static final double LILY_FORCE = 0.5;
    /** Part de la clarte de la couleur d'origine que garde la cible (0 : cible telle quelle ; lot 2 : 0,5). */
    static final double LIGHT_KEEP = 0.3;

    /** Phase de teinte : saison et palier (la couleur ne change qu'a un changement de phase). */
    public record Phase(Season season, int step, int length) {

        /** Date du milieu du palier (celle dont la couleur vaut pour tout le palier). */
        public Date middle() {
            double day = season.ordinal() * (double) length + (step + 0.5) * length / Seasons.STEPS;
            long d = (long) Math.floor(day);
            return new Date(d, day - d, length);
        }
    }

    public static Phase phase(Date d) {
        return new Phase(d.season(), d.step(), d.length());
    }

    /**
     * Etat d'une phase, calcule une fois : poids des saisons, avancement de l'automne, humidite des familles seches, et pour chaque sorte et
     * chaque famille la cible et la force deja melangees ({@link #table}). Lu par les resolveurs de couleur (fils de construction des
     * troncons) sans aucun calcul de date.
     */
    public record State(Phase phase, double[] weights, double autumn, double dryWet, double savannaWet, double[][][] table) {
    }

    /** Colonnes de la table : les 6 familles, puis « seche » (deserts, badlands), « savane » et (lot 14) « marais ». */
    static final int DRY_COLUMN = 6, SAVANNA_COLUMN = 7, WETLAND_COLUMN = 8, COLUMNS = 9;

    public static State state(Phase phase) {
        Date d = phase.middle();
        Seasons.Blend b = Seasons.blend(d);
        double[] w = new double[4];
        for (Season s : Season.values()) {
            w[s.ordinal()] = b.weight(s);
        }
        // avancement de l'automne : 0 au debut, 1 a la fin (pendant l'amorce depuis l'ete : 0 ; pendant la fin vers l'hiver : 1)
        double autumn = d.season() == Season.AUTUMN ? d.inSeason() / d.length() : d.season() == Season.WINTER ? 1 : 0;
        //1.0.5-beta (lot 14) : avancement de l'ete (l'herbe et les feuillus sechent un peu en fin d'ete) ; 1 pendant l'amorce de l'automne
        double summer = d.season() == Season.SUMMER ? d.inSeason() / d.length() : d.season() == Season.AUTUMN ? 1 : 0;
        double dryWet = wetness(SeasonClimate.humidityOffset(Family.DRY, false, d), -0.2, 0.1);
        double savWet = wetness(SeasonClimate.humidityOffset(Family.TROPICAL, true, d), 0, 0.25);
        double[][][] table = new double[Kind.values().length][COLUMNS][];
        for (Kind kind : Kind.values()) {
            for (int col = 0; col < COLUMNS; col++) {
                table[kind.ordinal()][col] = entry(kind, col, w, autumn, summer, dryWet, savWet);
            }
        }
        return new State(phase, w, autumn, dryWet, savWet, table);
    }

    /** {r, g, b, force, clarte} d'une sorte dans une colonne de famille. */
    static double[] entry(Kind kind, int col, double[] weights, double autumn, double summer, double dryWet, double savWet) {
        double[] target = new double[3];
        double force = 0;
        if ((col == DRY_COLUMN || col == SAVANNA_COLUMN) && kind != Kind.CONIFER) {
            double wet = col == SAVANNA_COLUMN ? savWet : dryWet;
            for (int c = 0; c < 3; c++) {
                target[c] = STRAW[c] + (WET[c] - STRAW[c]) * wet;
            }
            force = STRAW_FORCE + (WET_FORCE - STRAW_FORCE) * wet;
        } else {
            boolean tropical = col == Family.TROPICAL.ordinal();
            for (Season s : Season.values()) {
                double w = weights[s.ordinal()];
                if (w <= 0) {
                    continue;
                }
                int[] t = tropical ? TROPICAL_TARGET[s.ordinal()] : col == WETLAND_COLUMN ? wetland(kind, s, autumn, summer) : target(kind, s, autumn, summer);
                for (int c = 0; c < 3; c++) {
                    target[c] += w * t[c];
                }
                force += w * (tropical ? TROPICAL_FORCE : force(kind, s));
            }
            if (col == WETLAND_COLUMN) {
                force *= WETLAND_FORCE;
            } else if (!tropical) {
                force *= FAMILY_FORCE[col == DRY_COLUMN ? Family.DRY.ordinal() : col == SAVANNA_COLUMN ? Family.TROPICAL.ordinal() : col];
            }
        }
        if (kind == Kind.LILY) {
            force *= LILY_FORCE;
        }
        return new double[]{target[0], target[1], target[2], Math.min(1, force), Math.max(1, light(target[0], target[1], target[2]))};
    }

    /** Marais : cible d'une sorte (coniferes comme ailleurs ; le reste olive, automne brun olive, jamais rouge). */
    static int[] wetland(Kind kind, Season s, double autumn, double summer) {
        if (kind == Kind.CONIFER || s != Season.AUTUMN) {
            return target(kind == Kind.CONIFER ? Kind.CONIFER : kind == Kind.GRASS || kind == Kind.LILY ? Kind.GRASS : Kind.FOLIAGE, s, autumn, summer);
        }
        return ramp(WETLAND_RAMP, autumn);
    }

    /** 0 = saison seche, 1 = saison humide, d'apres le decalage d'humidite entre ses deux extremes. */
    static double wetness(double h, double dry, double wet) {
        return Math.max(0, Math.min(1, (h - dry) / (wet - dry)));
    }

    /** Teinte la couleur {@code rgb} d'un vegetal de cette sorte dans un biome de cette famille. Etat null (pas de saison) : inchangee. */
    public static int apply(int rgb, Kind kind, Family family, boolean savanna, @Nullable State st) {
        return apply(rgb, kind, family, savanna, false, st);
    }

    /** 1.0.5-beta (lot 14) : idem, {@code wetland} = biome de marais (herbe a la teinte des marais du vanilla) : contraste adouci, sans rouge. */
    public static int apply(int rgb, Kind kind, Family family, boolean savanna, boolean wetland, @Nullable State st) {
        if (st == null) {
            return rgb;
        }
        int col = savanna ? SAVANNA_COLUMN : family == Family.DRY ? DRY_COLUMN : wetland && family != Family.TROPICAL ? WETLAND_COLUMN : family.ordinal();
        return mix(rgb, st.table()[kind.ordinal()][col]);
    }

    static int[] target(Kind kind, Season s, double autumn, double summer) {
        int i = s.ordinal();
        return switch (kind) {
            case GRASS, LILY -> s == Season.SUMMER ? lerp(GRASS_TARGET[1], GRASS_LATE_SUMMER, late(summer)) : GRASS_TARGET[i];
            case FOLIAGE -> s == Season.AUTUMN ? ramp(AUTUMN_RAMP, autumn) : s == Season.SUMMER ? lerp(FOLIAGE_TARGET[1], FOLIAGE_LATE_SUMMER, late(summer))
                                                                         : FOLIAGE_TARGET[i];
            case OAK -> s == Season.AUTUMN ? ramp(OAK_RAMP, autumn) : target(Kind.FOLIAGE, s, autumn, summer);
            case DARK_OAK -> s == Season.AUTUMN ? ramp(DARK_OAK_RAMP, autumn) : target(Kind.FOLIAGE, s, autumn, summer);
            case BIRCH -> BIRCH_TARGET[i];
            case CONIFER -> CONIFER_TARGET[i];
        };
    }

    static double force(Kind kind, Season s) {
        int i = s.ordinal();
        return switch (kind) {
            case GRASS, LILY -> GRASS_FORCE[i];
            case FOLIAGE, OAK, DARK_OAK -> FOLIAGE_FORCE[i];
            case BIRCH -> BIRCH_FORCE[i];
            case CONIFER -> CONIFER_FORCE[i];
        };
    }

    /** Secheresse de fin d'ete : rien pendant la premiere moitie, puis monte jusqu'a la fin (0..1). */
    static double late(double summer) {
        double x = Math.max(0, Math.min(1, (summer - 0.5) * 2));
        return x * x;
    }

    static int[] lerp(int[] a, int[] b, double t) {
        return new int[]{(int) Math.round(a[0] + (b[0] - a[0]) * t), (int) Math.round(a[1] + (b[1] - a[1]) * t), (int) Math.round(a[2] + (b[2] - a[2]) * t)};
    }

    /** Rampe d'automne : debut (0), milieu (0,5), fin (1) de la saison. */
    static int[] ramp(int[][] ramp, double t) {
        double x = Math.max(0, Math.min(1, t)) * (ramp.length - 1);
        int a = Math.min(ramp.length - 2, (int) x);
        double f = x - a;
        int[] r = new int[3];
        for (int c = 0; c < 3; c++) {
            r[c] = (int) Math.round(ramp[a][c] + (ramp[a + 1][c] - ramp[a][c]) * f);
        }
        return r;
    }

    static int[] ramp(double t) {
        return ramp(AUTUMN_RAMP, t);
    }

    static double light(double r, double g, double b) {
        return 0.299 * r + 0.587 * g + 0.114 * b;
    }

    /**
     * Melange vers la cible, dont la clarte est rapprochee de celle de la couleur d'origine dans la proportion {@link #LIGHT_KEEP} (lot 2 :
     * a mi-chemin ; lot 14 : 30 %, la cible garde l'essentiel de sa propre clarte : printemps lumineux, hiver terne).
     */
    static int mix(int rgb, double[] e) {
        double force = e[3];
        if (force <= 0) {
            return rgb;
        }
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        double k = Math.pow(Math.max(1, light(r, g, b)) / e[4], LIGHT_KEEP);
        int nr = channel(r, e[0] * k, force), ng = channel(g, e[1] * k, force), nb = channel(b, e[2] * k, force);
        return rgb & 0xFF000000 | nr << 16 | ng << 8 | nb;
    }

    static int channel(int src, double target, double force) {
        int v = (int) Math.round(src + (Math.min(255, target) - src) * force);
        return Math.max(0, Math.min(255, v));
    }
}

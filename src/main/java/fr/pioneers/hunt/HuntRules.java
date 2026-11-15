package fr.pioneers.hunt;

/**
 * 1.0.5-beta (lot 10, specification « Le monde vivant » §6, decisions 10 et 11) : REGLES PURES de la chasse (aucune classe du jeu : le banc
 * {@code tools/hunt/HuntBench.java} et {@code tools/check_p105_10.py} les lisent hors jeu).
 * <ul>
 *   <li>evenement « troupeau » (§6.1) : delai entre deux troupeaux d'un joueur (1 a 2 jours de jeu), distance (48 a 96 blocs), taille (4 a 10),
 *   duree de vie sans approche (un jour), table des especes par biome et par saison ({@link #herdKinds}) ;</li>
 *   <li>competence Chasse (§6.2) : effets au niveau 10 (viande et cuir +30 %, trophee 5 % -> 10 %, rechargement -25 %, dispersion -30 %,
 *   flair +50 %), XP (gibier abattu, de loin, bete dangereuse, troupeau trouve, depecage) ;</li>
 *   <li>armes (§6.3) : mousquet (chargement 3 s, un coup tres puissant, portee 64), revolver (6 coups, cartouches, balle par balle),
 *   dispersion, degats contre les joueurs (x 0,5 par defaut), bruit (le gibier fuit a 32 blocs), harpon (corde, bonus contre la faune
 *   marine).</li>
 * </ul>
 */
public final class HuntRules {

    private HuntRules() {
    }

    // ------------------------------------------------------------------ troupeau (§6.1)

    /** Un jour de jeu (ticks). */
    public static final int DAY = 24_000;
    /**
     * Delai entre deux troupeaux d'un meme joueur : 3 a 6 jours (reglages {@code fauna.herdEveryMinDays} / {@code herdEveryMaxDays}).
     * 1.0.5-beta (lot 31, retour 43 « les chasses doivent etre moins frequentes ») : 1 a 2 jours avant (moyenne 1,5 jour -> 4,5 : un
     * troupeau trois fois plus rare ; banc {@code tools/hunt/herd_freq.sh} : 0,67 -> 0,22 troupeau par jour et par joueur) ; cles de reglage
     * renommees (les fichiers d'avant gardaient 1 et 2).
     */
    public static final double HERD_MIN_DAYS = 3.0, HERD_MAX_DAYS = 6.0;
    /** Valeurs d'avant le lot 31 (banc de frequence, controle). */
    public static final double HERD_MIN_DAYS_BEFORE = 1.0, HERD_MAX_DAYS_BEFORE = 2.0;
    /** Distance du troupeau au joueur (blocs, reglages {@code fauna.herdMinDistance} / {@code herdMaxDistance}). */
    public static final int HERD_MIN_DISTANCE = 48, HERD_MAX_DISTANCE = 96;
    /** Taille du troupeau (reglages {@code fauna.herdMinSize} / {@code herdMaxSize}). */
    public static final int HERD_MIN_SIZE = 4, HERD_MAX_SIZE = 10;
    /** Duree de vie d'un troupeau que personne n'a approche (un jour de jeu), puis il se disperse et disparait. */
    public static final int HERD_LIFE = DAY;
    /** Un joueur a moins de 24 blocs d'une bete : le troupeau est « trouve » (XP, il ne disparait plus a la fin du jour). */
    public static final int HERD_APPROACH = 24;
    /** Troupeau trouve : un jour apres la decouverte, les survivants s'en vont s'il n'y a aucun joueur a 48 blocs. */
    public static final int HERD_FOUND_LIFE = DAY, HERD_LEAVE_DISTANCE = 48;
    /** Dispersion : les betes s'eloignent pendant 10 s puis disparaissent. */
    public static final int DISPERSE_TICKS = 200;
    /** Pas de deux troupeaux actifs a moins de 160 blocs (joueurs proches : un seul troupeau). */
    public static final int HERD_SPACING = 160;
    /** Controle des troupeaux (ticks) ; nouvel essai quand aucun lieu ne convient (ticks). */
    public static final int HERD_CHECK = 100, HERD_RETRY = 2_400;
    /** Lieux essayes a chaque tentative. */
    public static final int HERD_TRIES = 24;
    /** Signes : traces au sol toutes les 2 s pres d'un joueur a moins de 40 blocs de la piste, cris au loin toutes les 30 a 60 s. */
    public static final int TRACK_PERIOD = 40, TRACK_LENGTH = 40, TRACK_SEEN = 40, CALL_MIN = 600, CALL_MAX = 1_200;
    /** Volume d'un cri au loin (le jeu l'entend a 16 x volume blocs). */
    public static final float CALL_VOLUME = 6F;
    /** Chien autonome : grogne quand un troupeau est a moins de 128 blocs de sa niche ; va chercher son maitre a 48 blocs de plus. */
    public static final int DOG_ALERT = 128, DOG_FETCH = 48;
    /** Le chien en mode Chasse flaire la PISTE d'un troupeau jusqu'a 2 fois sa portee de flair. */
    public static final double TRAIL_FACTOR = 2.0;
    /**
     * Rayon de recherche des chiens en mode Chasse a la fin d'un troupeau (blocs). 1.0.5-beta (lot 31) : ne sert plus au bouton Chasse (avant :
     * allume tant qu'un troupeau actif etait a 160 blocs du MAITRE, alors qu'ils apparaissent a 48-96 blocs : allume pendant toute la vie de
     * chaque troupeau) : voir {@link #huntInReach}.
     */
    public static final int HIGHLIGHT_DISTANCE = 160;

    /**
     * 1.0.5-beta (lot 31, retour 43) : une CHASSE est disponible pour ce chien si un troupeau qui n'est pas en dispersion (actif ou trouve) et
     * qui a encore des betes a son centre a moins de {@code scentRange} x {@link #TRAIL_FACTOR} blocs (a l'horizontale) du CHIEN : c'est la
     * portee a laquelle le chien en mode Chasse prend la piste ({@code PetGoals.Hunt.search}). Meme regle pour le serveur (bouton du menu,
     * synchronise) et pour les bancs.
     */
    public static boolean huntInReach(boolean dispersing, int members, double dx, double dz, double scentRange) {
        double r = scentRange * TRAIL_FACTOR;
        return !dispersing && members > 0 && dx * dx + dz * dz <= r * r;
    }

    /** Saisons (ordre du calendrier). */
    public static final int SPRING = 0, SUMMER = 1, AUTUMN = 2, WINTER = 3;

    /**
     * Une sorte de troupeau : espece (id sans espace de noms), biomes (ids vanilla ou {@code #tag}), poids par saison (printemps, ete, automne,
     * hiver ; 0 = jamais).
     */
    public record HerdKind(String species, String[] biomes, double[] seasons) {

        public boolean in(String biome, java.util.Set<String> tags) {
            for (String b : biomes) {
                if (b.startsWith("#") ? tags.contains(b.substring(1)) : b.equals(biome)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Table des troupeaux (spec §6.1 + savane et taiga, choix faits seul) : cerfs en foret (et en taiga), bisons en plaine (et sur le
     * plateau de savane), sangliers en foret sombre (et en taiga), dindons en prairie (sauf l'hiver), lievres l'hiver (plaines, prairies,
     * forets, taigas, neige), girafes et elephants en savane.
     */
    public static final HerdKind[] KINDS = {
        new HerdKind("deer", new String[]{"minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest", "minecraft:old_growth_birch_forest",
                                          "#minecraft:is_taiga"}, new double[]{1, 1, 1.2, 0.8}),
        new HerdKind("bison", new String[]{"minecraft:plains", "minecraft:sunflower_plains", "minecraft:savanna_plateau"}, new double[]{1, 1, 1, 0.8}),
        new HerdKind("boar", new String[]{"minecraft:dark_forest", "#minecraft:is_taiga"}, new double[]{1, 1, 1.2, 0.8}),
        new HerdKind("turkey", new String[]{"minecraft:meadow"}, new double[]{1.2, 1, 1, 0}),
        new HerdKind("hare", new String[]{"minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:snowy_plains",
                                          "minecraft:snowy_taiga", "minecraft:forest", "minecraft:birch_forest", "#minecraft:is_taiga"},
              new double[]{0, 0, 0, 3}),
        new HerdKind("giraffe", new String[]{"minecraft:savanna", "minecraft:savanna_plateau"}, new double[]{1, 1, 1, 1}),
        new HerdKind("elephant", new String[]{"minecraft:savanna"}, new double[]{0.6, 0.6, 0.6, 0.6}),
    };

    /** Index des sortes possibles ici a cette saison (poids > 0). */
    public static java.util.List<HerdKind> herdKinds(String biome, java.util.Set<String> tags, int season) {
        java.util.List<HerdKind> out = new java.util.ArrayList<>();
        for (HerdKind k : KINDS) {
            if (k.seasons[season] > 0 && k.in(biome, tags)) {
                out.add(k);
            }
        }
        return out;
    }

    /** Tirage d'une sorte (poids de la saison) ; null si aucune. {@code roll} dans [0, 1). */
    public static HerdKind pickHerd(String biome, java.util.Set<String> tags, int season, double roll) {
        java.util.List<HerdKind> l = herdKinds(biome, tags, season);
        double sum = 0;
        for (HerdKind k : l) {
            sum += k.seasons[season];
        }
        double x = roll * sum;
        for (HerdKind k : l) {
            x -= k.seasons[season];
            if (x < 0) {
                return k;
            }
        }
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }

    /** Delai avant le prochain troupeau d'un joueur (ticks), entre {@code minDays} et {@code maxDays} jours. */
    public static long nextDelay(double minDays, double maxDays, double roll) {
        double lo = Math.max(0.05, Math.min(minDays, maxDays)), hi = Math.max(lo, Math.max(minDays, maxDays));
        return Math.round((lo + (hi - lo) * roll) * DAY);
    }

    /** Taille d'un troupeau entre {@code min} et {@code max} (bornes comprises). */
    public static int size(int min, int max, double roll) {
        int lo = Math.max(1, Math.min(min, max)), hi = Math.max(lo, Math.max(min, max));
        return lo + Math.min(hi - lo, (int) Math.floor(roll * (hi - lo + 1)));
    }

    /** Distance du troupeau au joueur entre {@code min} et {@code max}. */
    public static double distance(int min, int max, double roll) {
        int lo = Math.min(min, max), hi = Math.max(min, max);
        return lo + (hi - lo) * roll;
    }

    /** Etats d'un troupeau. */
    public static final int ACTIVE = 0, FOUND = 1, DISPERSING = 2, OVER = 3;

    /**
     * Etat d'un troupeau a l'instant {@code now} : non trouve et age d'un jour -> dispersion ; trouve, un jour apres la decouverte et aucun
     * joueur a {@link #HERD_LEAVE_DISTANCE} -> dispersion ; plus aucune bete -> fini.
     */
    public static int state(long start, long found, long now, int alive, boolean playerNear, int life) {
        if (alive <= 0) {
            return OVER;
        }
        if (found < 0) {
            return now - start >= life ? DISPERSING : ACTIVE;
        }
        return now - found >= HERD_FOUND_LIFE && !playerNear ? DISPERSING : FOUND;
    }

    // ------------------------------------------------------------------ competence Chasse (§6.2)

    /** Valeurs au niveau 10 (donnees de T:rp/pioneer_data.py ; garde-fous propres a la Chasse, decision 10 de la spec). */
    public static final double YIELD_10 = 0.30, TROPHY_10 = 1.0, RELOAD_10 = -0.25, SPREAD_10 = -0.30, SCENT_10 = 0.50;
    /** Chance de trophee de base (5 %). */
    public static final double TROPHY_CHANCE = 0.05;

    /** Effet au niveau {@code level} d'une valeur donnee au niveau 10 (progressif, lineaire). */
    public static double atLevel(double value10, int level) {
        return value10 * Math.max(0, Math.min(10, level)) / 10.0;
    }

    /** Chance de trophee : 5 % x (1 + bonus), bonus +100 % au niveau 10 -> 10 %. */
    public static double trophyChance(double bonus) {
        return TROPHY_CHANCE * (1 + Math.max(0, bonus));
    }

    /** Viande ou cuir avec le bonus : n x (1 + bonus), la partie decimale tiree au sort ({@code roll} dans [0, 1)). */
    public static int yield(int n, double bonus, double roll) {
        if (n <= 0 || bonus <= 0) {
            return n;
        }
        double x = n * (1 + bonus);
        int whole = (int) Math.floor(x);
        return whole + (roll < x - whole ? 1 : 0);
    }

    /** XP : base du gibier abattu ; x 2 de loin (au moins 24 blocs) ; x 2 pour une bete dangereuse (predateur, charge, defense). */
    public static final int FAR_SHOT = 24;

    public static double killXp(double base, double distance, boolean dangerous) {
        return base * (distance >= FAR_SHOT ? 2 : 1) * (dangerous ? 2 : 1);
    }

    // ------------------------------------------------------------------ armes (§6.3)

    /** Mousquet : chargement 3 s (60 ticks), degats, portee, dispersion (degres), recul (degres de tangage). */
    public static final int MUSKET_RELOAD = 60, MUSKET_RANGE = 64;
    public static final float MUSKET_DAMAGE = 20F, MUSKET_SPREAD = 1.6F, MUSKET_RECOIL = 9F;
    /** Revolver : 6 coups, une cartouche toutes les 12 ticks, 8 ticks entre deux coups, degats, portee, dispersion, recul. */
    public static final int REVOLVER_ROUNDS = 6, REVOLVER_LOAD = 12, REVOLVER_COOLDOWN = 8, REVOLVER_RANGE = 40;
    public static final float REVOLVER_DAMAGE = 8F, REVOLVER_SPREAD = 2.4F, REVOLVER_RECOIL = 4F;
    /** Un coup de feu effraie le gibier dans ce rayon (blocs). */
    public static final int SCARE_RADIUS = 32;
    /** Degats des armes a feu contre un joueur : x 0,5 par defaut (reglage {@code hunt.firearmPvpFactor}, 0 a 1). */
    public static final double PVP_FACTOR = 0.5;

    /** Duree de rechargement avec le bonus de la competence (-0,25 au niveau 10) : jamais moins de 5 ticks. */
    public static int reload(int base, double bonus) {
        return Math.max(5, (int) Math.round(base * (1 + Math.min(0, bonus))));
    }

    /** Dispersion avec le bonus de la competence (-0,30 au niveau 10). */
    public static float spread(float base, double bonus) {
        return (float) (base * Math.max(0, 1 + Math.min(0, bonus)));
    }

    /** Degats contre un joueur. */
    public static float pvp(float damage, double factor) {
        return (float) (damage * Math.max(0, Math.min(1, factor)));
    }

    /** Direction tiree avec dispersion : angles (degres) ajoutes au lacet et au tangage, {@code a} et {@code b} dans [0, 1). */
    public static float[] deviate(float spread, double a, double b) {
        double r = spread * Math.sqrt(a), t = 2 * Math.PI * b;
        return new float[]{(float) (r * Math.cos(t)), (float) (r * Math.sin(t))};
    }

    // ------------------------------------------------------------------ harpon (§6.3)

    /** Harpon : degats, x 2,5 contre la faune marine, corde (blocs), vitesse de lancer (air / eau), frein de la bete harponnee. */
    public static final float HARPOON_DAMAGE = 8F, HARPOON_MARINE = 2.5F, HARPOON_SPEED = 2.2F;
    public static final int HARPOON_ROPE = 32;
    /** Ralentissement de la bete harponnee (-60 %) ; elle est tiree vers le joueur au-dela de la longueur de corde tendue. */
    public static final double HARPOON_SLOW = -0.6;
    /** Inertie dans l'eau (le trident vanilla : 0,99 ; une fleche : 0,6). */
    public static final float HARPOON_WATER_INERTIA = 0.99F;
    /** Une bete plus large que 2 blocs n'est pas tiree : elle est seulement freinee (baleines, requins geants). */
    public static final double HARPOON_PULL_MAX_WIDTH = 2.0;

    /** Degats du harpon contre une cible (faune marine : x 2,5). */
    public static float harpoonDamage(boolean marine) {
        return HARPOON_DAMAGE * (marine ? HARPOON_MARINE : 1F);
    }

    /** Force de traction de la corde : 0 tant que la corde n'est pas tendue, puis proportionnelle a l'exces (bornee). */
    public static double pull(double distance, int rope) {
        return distance <= rope * 0.75 ? 0 : Math.min(0.35, (distance - rope * 0.75) * 0.05);
    }

    // ------------------------------------------------------------------ trophees (§6.4)

    /** Trophees : cerf (male portant ses bois), ours, requins (blanc, tigre, marteau, gris : le requin-baleine est trop grand). */
    public static final String[] TROPHIES = {"deer", "bear", "shark"};
}

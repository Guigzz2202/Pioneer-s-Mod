package fr.pioneers.common;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Options communes (fichier config/pioneers-common.toml), valables pour tout le monde sur le serveur. Les valeurs se modifient
 * a chaud : la plupart s'appliquent des la sauvegarde du fichier.
 */
public final class PiCommonConfig {

    private PiCommonConfig() {
    }

    public static final ForgeConfigSpec SPEC;
    // Extraction
    public static final ForgeConfigSpec.IntValue MINING_BASE_RANGE;
    public static final ForgeConfigSpec.IntValue MINING_MIN_RANGE;
    public static final ForgeConfigSpec.BooleanValue ROVER_DIGGING;
    public static final ForgeConfigSpec.BooleanValue FARM_CROWS;
    // Monde
    public static final ForgeConfigSpec.BooleanValue FINITE_WATER;
    // Outils
    public static final ForgeConfigSpec.BooleanValue TREE_FELLING;
    public static final ForgeConfigSpec.IntValue TREE_FELLING_MAX;
    /** 1.0.3-beta (lot 9) : le lance-flammes allume les blocs inflammables (avec la regle mobGriefing). */
    public static final ForgeConfigSpec.BooleanValue FLAMETHROWER_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue AREA_TOOLS;
    // Evenements spatiaux
    public static final ForgeConfigSpec.BooleanValue EVENTS_ENABLED;
    public static final ForgeConfigSpec.IntValue EVENT_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.DoubleValue EVENT_CHANCE;
    public static final ForgeConfigSpec.BooleanValue EVENT_METEOR_SHOWER, EVENT_SOLAR_FLARE, EVENT_DUST_STORM, EVENT_AURORA, EVENT_GIANT_STORM;
    public static final ForgeConfigSpec.DoubleValue FLARE_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue METEOR_DENSITY;
    public static final ForgeConfigSpec.BooleanValue METEOR_TERRAIN;
    public static final ForgeConfigSpec.DoubleValue METEOR_EMBED_CHANCE;
    public static final ForgeConfigSpec.BooleanValue FLARE_FIRES;
    public static final ForgeConfigSpec.BooleanValue EVENT_DIFFICULTY_SCALING;

    public static final ForgeConfigSpec.LongValue FORCE_FIELD_UPKEEP;
    public static final ForgeConfigSpec.LongValue FORCE_FIELD_IMPACT;
    public static final ForgeConfigSpec.LongValue LIGHTNING_ENERGY;
    public static final ForgeConfigSpec.DoubleValue ROBOTICS_ENERGY;
    public static final ForgeConfigSpec.BooleanValue ROBOTICS_GRIEFING;
    // Fusees et voyage
    public static final ForgeConfigSpec.DoubleValue ROCKET_FUEL;
    public static final ForgeConfigSpec.DoubleValue TRANSIT_TIME;
    public static final ForgeConfigSpec.DoubleValue CAPSULE_FUEL;
    public static final ForgeConfigSpec.BooleanValue CRASH_DAMAGE;
    // Survie
    public static final ForgeConfigSpec.DoubleValue OXYGEN_CONSUMPTION;
    public static final ForgeConfigSpec.BooleanValue VACUUM_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue HAZARD_DAMAGE;
    // Geantes gazeuses
    public static final ForgeConfigSpec.IntValue POCKETS_AROUND_PLAYER;
    public static final ForgeConfigSpec.BooleanValue GIANT_LIGHTNING;
    public static final ForgeConfigSpec.BooleanValue GIANT_CRUSH;
    public static final ForgeConfigSpec.IntValue DRONE_BAY_MAX_DRONES;
    public static final ForgeConfigSpec.IntValue DRONE_RANGE;
    public static final ForgeConfigSpec.DoubleValue DRONE_CARGO;
    // Multiblocs de fin de jeu
    public static final ForgeConfigSpec.LongValue ABSORBER_FE_PER_COLLECTOR;
    public static final ForgeConfigSpec.DoubleValue UNIVERSE_ENERGY;
    public static final ForgeConfigSpec.DoubleValue UNIVERSE_DURATION;
    public static final ForgeConfigSpec.DoubleValue UNIVERSE_OUTPUT;
    // Serre
    public static final ForgeConfigSpec.DoubleValue GREENHOUSE_GROWTH;
    // 1.0.5-beta (lot 1) : saisons
    public static final ForgeConfigSpec.IntValue SEASON_DAYS;
    public static final ForgeConfigSpec.BooleanValue SEASONAL_WEATHER;
    // 1.0.5-beta (lot 4) : faune persistante et repeuplement par zone
    public static final ForgeConfigSpec.BooleanValue FAUNA_ENABLED;
    public static final ForgeConfigSpec.IntValue FAUNA_PERIOD;
    public static final ForgeConfigSpec.DoubleValue FAUNA_THRESHOLD;
    public static final ForgeConfigSpec.IntValue FAUNA_PLAYER_DISTANCE;
    public static final ForgeConfigSpec.IntValue FAUNA_VILLAGE_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue FAUNA_LIGHT_AI;
    public static final ForgeConfigSpec.IntValue FAUNA_AI_DISTANCE;
    /** 1.0.5-beta (lot 6) : reproduction sauvage lente (activation, chance par adulte et par passage, distance aux joueurs). */
    public static final ForgeConfigSpec.BooleanValue FAUNA_WILD_BREEDING;
    public static final ForgeConfigSpec.DoubleValue FAUNA_WILD_BREED_CHANCE;
    public static final ForgeConfigSpec.IntValue FAUNA_WILD_BREED_DISTANCE;
    /** 1.0.5-beta (lot 7, decision 14) : vol des rats dans les coffres et tonneaux ouverts (actif par defaut). */
    public static final ForgeConfigSpec.BooleanValue FAUNA_RAT_THEFT;
    /** 1.0.5-beta (lot 8) : vol des bananes par les singes (actif par defaut, comme les autres voleurs : decision 14). */
    public static final ForgeConfigSpec.BooleanValue FAUNA_MONKEY_THEFT;
    /** 1.0.5-beta (lot 9) : vol d'un aliment de la barre d'action par les mouettes (actif par defaut : decision 14). */
    public static final ForgeConfigSpec.BooleanValue FAUNA_SEAGULL_THEFT;
    /** 1.0.5-beta (lot 13) : les sangliers affames detruisent des cultures non closes, hors claim ; probabilite par minute. */
    public static final ForgeConfigSpec.BooleanValue FAUNA_BOAR_CROP_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue FAUNA_BOAR_CROP_CHANCE;
    /** 1.0.5 (lot 10) : evenement « troupeau » (fr.pioneers.hunt.HerdEvents). */
    public static final ForgeConfigSpec.BooleanValue FAUNA_HERDS;
    public static final ForgeConfigSpec.DoubleValue FAUNA_HERD_MIN_DAYS, FAUNA_HERD_MAX_DAYS;
    public static final ForgeConfigSpec.IntValue FAUNA_HERD_MIN_DISTANCE, FAUNA_HERD_MAX_DISTANCE, FAUNA_HERD_MIN_SIZE, FAUNA_HERD_MAX_SIZE;
    /** 1.0.5 (lot 10) : chasse (armes a feu contre les joueurs). */
    public static final ForgeConfigSpec.DoubleValue HUNT_PVP_FACTOR;
    /** Plafonds par zone, dans l'ordre de {@link fr.pioneers.fauna.FaunaGroup}. */
    public static final ForgeConfigSpec.IntValue[] FAUNA_CAPS = new ForgeConfigSpec.IntValue[fr.pioneers.fauna.FaunaGroup.COUNT];
    // 1.0.5-beta (lot 5) : chiens et chats, maitre, rappel
    public static final ForgeConfigSpec.IntValue PET_RECALL_COOLDOWN;
    public static final ForgeConfigSpec.IntValue PET_TERRITORY;
    public static final ForgeConfigSpec.BooleanValue PET_PROTECTION;
    public static final ForgeConfigSpec.BooleanValue PET_CAT_FISHING;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Événements spatiaux : pluies de météores, éruptions solaires, tempêtes de poussière, aurores, supertempêtes.").push("events");
        EVENTS_ENABLED = builder.comment("Active les événements spatiaux.").define("enabled", true);
        EVENT_COOLDOWN_MINUTES = builder
              .comment("Calme minimal entre deux événements d'un même astre, en minutes de jeu (temps réel).")
              .defineInRange("cooldownMinutes", 40, 1, 1440);
        EVENT_CHANCE = builder
              .comment("Multiplicateur de la probabilité qu'un événement commence une fois le calme écoulé (1 = valeur de base, 0 = jamais).")
              .defineInRange("chanceMultiplier", 0.5, 0.0, 10.0);
        EVENT_METEOR_SHOWER = builder.define("meteorShowers", true);
        EVENT_SOLAR_FLARE = builder.define("solarFlares", true);
        EVENT_DUST_STORM = builder.define("dustStorms", true);
        EVENT_AURORA = builder.define("auroras", true);
        EVENT_GIANT_STORM = builder.define("gasGiantSuperstorms", true);
        FLARE_DAMAGE = builder
              .comment("Multiplicateur des dégâts du rayonnement des éruptions solaires (0 = sans danger).")
              .defineInRange("flareDamageMultiplier", 1.0, 0.0, 10.0);
        METEOR_DENSITY = builder
              .comment("Multiplicateur du nombre de météores pendant une pluie.")
              .defineInRange("meteorDensity", 1.0, 0.0, 10.0);
        METEOR_TERRAIN = builder
              .comment("Les météores creusent un cratère (explosion qui casse les blocs).")
              .define("meteorTerrainDamage", true);
        METEOR_EMBED_CHANCE = builder
              .comment("Probabilité qu'un météore reste enfoui au fond de son cratère (météorite à récupérer). Sinon il se désintègre.")
              .defineInRange("meteorEmbedChance", 0.35, 0.0, 1.0);
        FLARE_FIRES = builder
              .comment("Les éruptions solaires enflamment les blocs combustibles à ciel ouvert, sur les astres qui ont une atmosphère.")
              .define("flareFires", true);
        EVENT_DIFFICULTY_SCALING = builder
              .comment("La probabilité des événements dangereux (météores, éruptions, tempêtes) suit la difficulté locale (comme les monstres).")
              .define("localDifficultyScaling", true);
        builder.pop();

        builder.comment("Robotique : drones, robots et rovers.").push("robotics");
        ROBOTICS_ENERGY = builder
              .comment("Multiplicateur de la consommation des unités en mission.")
              .defineInRange("energyMultiplier", 1.0, 0.0, 100.0);
        ROBOTICS_GRIEFING = builder
              .comment("Les unités peuvent casser des blocs (récolte, abattage, minage, terrassement). Les mods de protection sont toujours consultés.")
              .define("canBreakBlocks", true);
        builder.pop();

        builder.comment("Champ de force et paratonnerre.").push("protection");
        FORCE_FIELD_UPKEEP = builder
              .comment("Entretien du champ de force : FE/t par bloc de rayon au carré (rayon 16 -> 256 x cette valeur).")
              .defineInRange("forceFieldUpkeep", 8L, 0L, 1_000_000L);
        FORCE_FIELD_IMPACT = builder
              .comment("FE consommés par le champ de force pour arrêter un météore (multiplié par sa taille).")
              .defineInRange("forceFieldImpactCost", 400_000L, 0L, Long.MAX_VALUE);
        LIGHTNING_ENERGY = builder
              .comment("FE recueillis par le paratonnerre à chaque éclair (multiplié par la hauteur du mât).")
              .defineInRange("lightningEnergyPerStrike", 1_000_000L, 0L, Long.MAX_VALUE);
        builder.pop();

        builder.comment("Fusées, transit et capsule de descente.").push("rockets");
        ROCKET_FUEL = builder
              .comment("Multiplicateur de l'hydrogène liquide nécessaire à un vol habité.")
              .defineInRange("fuelMultiplier", 1.0, 0.05, 20.0);
        TRANSIT_TIME = builder
              .comment("Multiplicateur de la durée du transit entre deux astres (cinématique).")
              .defineInRange("transitTimeMultiplier", 1.0, 0.1, 10.0);
        CAPSULE_FUEL = builder
              .comment("Multiplicateur du carburant des rétrofusées de la capsule de descente.")
              .defineInRange("capsuleFuelMultiplier", 1.0, 0.0, 20.0);
        CRASH_DAMAGE = builder
              .comment("Un atterrissage trop rapide blesse le pilote (et l'écrasement provoque une explosion sans dégâts aux blocs).")
              .define("crashDamage", true);
        builder.pop();

        builder.comment("Survie hors de la Terre.").push("survival");
        OXYGEN_CONSUMPTION = builder
              .comment("Multiplicateur de l'oxygène consommé par la combinaison (0 = réserve infinie).")
              .defineInRange("oxygenConsumption", 1.0, 0.0, 20.0);
        VACUUM_DAMAGE = builder
              .comment("Suffocation sans combinaison ou sans oxygène sur les astres sans air respirable.")
              .define("vacuumDamage", true);
        HAZARD_DAMAGE = builder
              .comment("Dégâts d'une combinaison trop faible pour l'astre (froid, chaleur, acide, radiations, pression).")
              .define("hazardDamage", true);
        builder.pop();

        builder.comment("Géantes gazeuses : poches de gaz, drones collecteurs, dangers.").push("gas_giants");
        POCKETS_AROUND_PLAYER = builder
              .comment("Nombre de poches de gaz entretenues autour de chaque joueur.")
              .defineInRange("pocketsAroundPlayer", 14, 0, 64);
        GIANT_LIGHTNING = builder.comment("La foudre tombe dans la couche des orages.").define("lightning", true);
        GIANT_CRUSH = builder.comment("Pression écrasante en profondeur (sous l'altitude 60).").define("crushingPressure", true);
        DRONE_BAY_MAX_DRONES = builder
              .comment("Nombre maximal de drones en vol par baie.")
              .defineInRange("maxDronesPerBay", 4, 1, 16);
        DRONE_RANGE = builder
              .comment("Portée des drones autour de leur baie, en blocs.")
              .defineInRange("droneRange", 80, 16, 256);
        DRONE_CARGO = builder
              .comment("Multiplicateur de ce qu'un drone rapporte par voyage.")
              .defineInRange("droneCargoMultiplier", 1.0, 0.1, 20.0);
        builder.pop();

        builder.comment("Multiblocs de fin de jeu.").push("endgame");
        ABSORBER_FE_PER_COLLECTOR = builder
              .comment("Absorbeur solaire : FE/t produits par capteur autour du Soleil (multipliés par la luminosité de l'étoile).")
              .defineInRange("absorberCollectorPower", 2_000_000L, 1L, 1_000_000_000_000L);
        UNIVERSE_ENERGY = builder
              .comment("Simulateur d'univers : multiplicateur de l'énergie consommée par les galaxies (et par le Big Bang).")
              .defineInRange("universeEnergyMultiplier", 1.0, 0.001, 100.0);
        UNIVERSE_DURATION = builder
              .comment("Simulateur d'univers : multiplicateur de la durée d'un cycle de galaxie.")
              .defineInRange("universeCycleMultiplier", 1.0, 0.05, 20.0);
        UNIVERSE_OUTPUT = builder
              .comment("Simulateur d'univers : multiplicateur des chances de produit bonus.")
              .defineInRange("universeBonusMultiplier", 1.0, 0.0, 10.0);
        builder.pop();

        builder.comment("Serre spatiale.").push("greenhouse");
        GREENHOUSE_GROWTH = builder
              .comment("Multiplicateur de la vitesse de croissance des plantes extraterrestres.")
              .defineInRange("growthMultiplier", 1.0, 0.1, 20.0);
        builder.pop();

        builder.comment("Bases d'extraction et rovers.").push("mining");
        MINING_BASE_RANGE = builder
              .comment("Portée maximale de base d'une base d'extraction, en blocs (avant les recherches et le module sondeur).")
              .defineInRange("baseRange", 24, 8, 96);
        MINING_MIN_RANGE = builder
              .comment("Portée minimale réglable dans l'interface de la base, en blocs.")
              .defineInRange("minRange", 8, 6, 32);
        ROVER_DIGGING = builder
              .comment("Le rover creuse la roche naturelle des astres sur son passage. Si désactivé, il passe au-dessus du relief.")
              .define("roverDigging", true);
        builder.pop();

        builder.comment("Ferme.").push("farm");
        FARM_CROWS = builder
              .comment("Des bandes de corbeaux viennent ravager les cultures avancées non protégées par un épouvantail.")
              .define("crows", true);
        builder.pop();

        builder.comment("Saisons (1.0.5) : calendrier commun au serveur, climat et intempéries selon la saison.").push("seasons");
        SEASON_DAYS = builder
              .comment("Durée d'une saison, en jours de jeu (4 saisons par an).")
              .defineInRange("seasonDays", fr.pioneers.season.Seasons.DEFAULT_LENGTH, fr.pioneers.season.Seasons.MIN_LENGTH,
                    fr.pioneers.season.Seasons.MAX_LENGTH);
        SEASONAL_WEATHER = builder
              .comment("Intempéries selon la saison : fréquence et durée des pluies et des orages, orages d'été plus violents. Faux : cycle vanilla (la neige d'hiver reste).")
              .define("seasonalWeather", true);
        builder.pop();

        builder.comment("Faune (1.0.5) : animaux persistants (plus de disparition ni d'apparition continue au hasard), peuplement à la génération, repeuplement par zone de 128 x 128 blocs. Les monstres hostiles et les chauves-souris ne sont pas concernés.",
                "Budget par défaut (serveur d'une dizaine de joueurs) : au plus 144 bêtes sauvages par zone (somme des plafonds capGame..capFish), plus un troupeau actif par joueur (10 bêtes au plus) ; loin de tout joueur, l'IA est allégée (lightAi). Commandes des opérateurs : /pioneer fauna zone|map|pass|forget, /pioneer hunt herd|herds.").push("fauna");
        FAUNA_ENABLED = builder
              .comment("Active la faune persistante et le repeuplement. Faux : apparitions et disparitions vanilla.")
              .define("enabled", true);
        FAUNA_PERIOD = builder
              .comment("Période du repeuplement, en ticks (12000 = 10 minutes de jeu ; 6000 à 240000 : plus court coûte plus de calcul à chaque passage).")
              .defineInRange("periodTicks", fr.pioneers.fauna.FaunaRules.PERIOD, 6000, 240000);
        FAUNA_THRESHOLD = builder
              .comment("Un groupe (gibier, prédateurs, oiseaux, faune marine, poissons) est repeuplé sous cette part de sa densité de départ.")
              .defineInRange("threshold", fr.pioneers.fauna.FaunaRules.THRESHOLD, 0.0, 1.0);
        FAUNA_PLAYER_DISTANCE = builder
              .comment("Distance minimale entre un groupe qui réapparaît et tout joueur, en blocs (il apparaît aussi hors de sa vue).")
              .defineInRange("playerDistance", fr.pioneers.fauna.FaunaRules.PLAYER_DISTANCE, 24, 256);
        FAUNA_VILLAGE_DISTANCE = builder
              .comment("Aucun repeuplement à moins de cette distance d'un village, en blocs (ni dans un claim de clan).")
              .defineInRange("villageDistance", fr.pioneers.fauna.FaunaRules.VILLAGE_DISTANCE, 0, 256);
        FAUNA_LIGHT_AI = builder
              .comment("IA allégée : loin de tout joueur, les animaux choisissent leurs actions 4 fois moins souvent.")
              .define("lightAi", true);
        FAUNA_AI_DISTANCE = builder
              .comment("Distance à partir de laquelle l'IA est allégée, en blocs.")
              .defineInRange("aiDistance", fr.pioneers.fauna.FaunaRules.AI_DISTANCE, 16, 256);
        FAUNA_WILD_BREEDING = builder
              .comment("Reproduction sauvage lente (1.0.5, lot 6) : un groupe entre le seuil de repeuplement et sa densité de départ fait de temps en temps un petit, près d'un adulte, hors de vue et loin des joueurs, jamais au-delà de sa densité de départ ni dans un claim.")
              .define("wildBreeding", true);
        FAUNA_WILD_BREED_CHANCE = builder
              .comment("Chance qu'un adulte du groupe fasse un petit à chaque passage du repeuplement (0,12 : une zone abandonnée retrouve sa densité de départ en quelques jours de jeu).")
              .defineInRange("wildBreedChance", fr.pioneers.fauna.FaunaRules.WILD_BREED_CHANCE, 0.0, 1.0);
        FAUNA_WILD_BREED_DISTANCE = builder
              .comment("Distance minimale entre une naissance sauvage et tout joueur, en blocs (elle a lieu aussi hors de sa vue).")
              .defineInRange("wildBreedDistance", fr.pioneers.fauna.FaunaRules.WILD_BREED_DISTANCE, 16, 256);
        FAUNA_RAT_THEFT = builder
              .comment("Vol des rats (1.0.5, lot 7) : de temps en temps, un rat vole un objet dans un coffre ou un tonneau vanilla sans verrou et hors claim (jamais dans une caisse d'entrepôt ni une machine). Faux : les rats ne volent plus rien.")
              .define("ratTheft", true);
        FAUNA_MONKEY_THEFT = builder
              .comment("Vol des singes (1.0.5, lot 8) : de temps en temps, un singe tout proche vole la banane qu'un joueur tient en main (une seule, jamais un autre objet). Faux : les singes ne volent plus rien.")
              .define("monkeyTheft", true);
        FAUNA_SEAGULL_THEFT = builder
              .comment("Vol des mouettes (1.0.5, lot 9) : de temps en temps, une mouette vole UN aliment dans la barre d'action d'un joueur proche (jamais l'objet tenu en main ni l'armure). Faux : les mouettes ne volent plus rien.")
              .define("seagullTheft", true);
        FAUNA_BOAR_CROP_DAMAGE = builder
              .comment("Sangliers et cultures (1.0.5, lot 13) : de temps en temps, un sanglier adulte affamé fouille et détruit quelques cultures proches (jamais dans un claim, jamais dans un champ clos par des clôtures, murets ou portillons ; seulement si la règle mobGriefing le permet). Faux : les sangliers ne touchent plus aux cultures.")
              .define("boarCropDamage", true);
        FAUNA_BOAR_CROP_CHANCE = builder
              .comment("Probabilité, par minute de jeu, qu'un sanglier adulte ait faim de cultures (0 à 1).")
              .defineInRange("boarCropChance", fr.pioneers.wild.WildRules.BOAR_CROP_CHANCE, 0.0, 1.0);
        FAUNA_HERDS = builder
              .comment("Troupeaux (1.0.5, lot 10) : de temps en temps, un troupeau de gibier apparaît près d'un joueur, hors de sa vue, hors des villages et des claims ; s'il n'est pas approché en un jour de jeu, il se disperse et disparaît. Faux : plus aucun troupeau.")
              .define("herds", true);
        FAUNA_HERD_MIN_DAYS = builder
              .comment("Délai minimal entre deux troupeaux d'un même joueur, en jours de jeu (un seul troupeau actif par joueur ; 0,25 à 30). 1.0.5, lot 31 : 3 jours (avant : 1 ; clé renommée de herdMinDays pour que les fichiers existants prennent la nouvelle valeur).")
              .defineInRange("herdEveryMinDays", fr.pioneers.hunt.HuntRules.HERD_MIN_DAYS, 0.25, 30.0);
        FAUNA_HERD_MAX_DAYS = builder
              .comment("Délai maximal entre deux troupeaux d'un même joueur, en jours de jeu (0,25 à 30 ; s'il est plus petit que herdEveryMinDays, les deux sont échangés). 1.0.5, lot 31 : 6 jours (avant : 2 ; clé renommée de herdMaxDays).")
              .defineInRange("herdEveryMaxDays", fr.pioneers.hunt.HuntRules.HERD_MAX_DAYS, 0.25, 30.0);
        FAUNA_HERD_MIN_DISTANCE = builder
              .comment("Distance minimale du troupeau au joueur, en blocs.")
              .defineInRange("herdMinDistance", fr.pioneers.hunt.HuntRules.HERD_MIN_DISTANCE, 24, 160);
        FAUNA_HERD_MAX_DISTANCE = builder
              .comment("Distance maximale du troupeau au joueur, en blocs.")
              .defineInRange("herdMaxDistance", fr.pioneers.hunt.HuntRules.HERD_MAX_DISTANCE, 32, 192);
        FAUNA_HERD_MIN_SIZE = builder
              .comment("Nombre minimal de bêtes d'un troupeau (1 à 16).")
              .defineInRange("herdMinSize", fr.pioneers.hunt.HuntRules.HERD_MIN_SIZE, 1, 16);
        FAUNA_HERD_MAX_SIZE = builder
              .comment("Nombre maximal de bêtes d'un troupeau (1 à 16 ; les bêtes d'un troupeau s'ajoutent aux plafonds de zone et disparaissent avec lui).")
              .defineInRange("herdMaxSize", fr.pioneers.hunt.HuntRules.HERD_MAX_SIZE, 1, 16);
        for (fr.pioneers.fauna.FaunaGroup g : fr.pioneers.fauna.FaunaGroup.ALL) {
            String name = g.id().substring(0, 1).toUpperCase(java.util.Locale.ROOT) + g.id().substring(1);
            FAUNA_CAPS[g.ordinal()] = builder
                  .comment("Plafond de bêtes sauvages par zone de 128 x 128 blocs, groupe " + g.id() + " (" + capLabel(g) + ") ; par défaut "
                        + g.defaultCap + ", de 0 (aucun repeuplement de ce groupe) à 256.")
                  .defineInRange("cap" + name, g.defaultCap, 0, 256);
        }
        builder.pop();

        builder.comment("Chiens et chats (1.0.5) : maître par étiquette, niche et panier, rappel des animaux (chiens, chats, montures).").push("pets");
        PET_RECALL_COOLDOWN = builder
              .comment("Délai minimal entre deux rappels d'un même joueur, en secondes (touche Rappel).")
              .defineInRange("recallCooldownSeconds", fr.pioneers.pet.PetRules.RECALL_COOLDOWN, 0, 3600);
        PET_TERRITORY = builder
              .comment("Rayon du territoire d'un chien ou d'un chat autonome autour de sa niche ou de son panier, en blocs.")
              .defineInRange("territoryRadius", fr.pioneers.pet.PetRules.TERRITORY, 8, 64);
        PET_PROTECTION = builder
              .comment("Un animal qui a un maître ne peut pas être blessé par les autres joueurs (sauf clans en guerre ouverte).")
              .define("protection", true);
        PET_CAT_FISHING = builder
              .comment("Les chats autonomes vont pêcher de temps en temps et rapportent un poisson dans leur panier.")
              .define("catFishing", true);
        builder.pop();

        builder.comment("Chasse (1.0.5) : armes à feu, harpon, trophées.").push("hunt");
        HUNT_PVP_FACTOR = builder
              .comment("Multiplicateur des dégâts des armes à feu (mousquet, revolver) contre un joueur (0 = aucun dégât, 1 = dégâts pleins). Les protections des claims et des clans s'appliquent toujours.")
              .defineInRange("firearmPvpFactor", fr.pioneers.hunt.HuntRules.PVP_FACTOR, 0.0, 1.0);
        builder.pop();

        builder.comment("Monde.").push("world");
        FINITE_WATER = builder
              .comment("L'eau n'est pas infinie : deux sources ne créent plus de source, sauf dans les océans et les rivières.")
              .define("finiteWater", true);
        builder.pop();

        builder.comment("Outils.").push("tools");
        TREE_FELLING = builder
              .comment("Une hache abat l'arbre entier (troncs reliés au-dessus du bloc cassé, arbre naturel avec feuillage). Accroupi : un seul bloc.")
              .define("treeFelling", true);
        TREE_FELLING_MAX = builder
              .comment("Nombre maximal de troncs abattus d'un coup.")
              .defineInRange("treeFellingMax", 192, 8, 1024);
        AREA_TOOLS = builder
              .comment("Outils de zone : marteau de minage et excavateur en 3 x 3, faux en carré (récolte, fauche, labour). Accroupi : un seul bloc.")
              .define("areaTools", true);
        FLAMETHROWER_BLOCKS = builder
              .comment("Le lance-flammes allume les blocs inflammables visés (seulement si la règle mobGriefing est vraie et dans les claims où le joueur peut bâtir). Faux : il ne brûle que les créatures.")
              .define("flamethrowerIgnitesBlocks", true);
        builder.pop();
        fr.pioneers.core.profile.RpConfig.build(builder); //1.9 : MAJ RP (pionnier, ruines, economie, clans)
        SPEC = builder.build();
    }

    /** Valeur lue sans planter si le fichier n'est pas encore charge (ecrans du menu principal, JEI...). */
    public static double get(ForgeConfigSpec.DoubleValue value, double fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static int get(ForgeConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static long get(ForgeConfigSpec.LongValue value, long fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static boolean get(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    /** 1.0.5-beta (lot 11) : groupe de faune en clair, pour le commentaire de son plafond dans le fichier de config. */
    private static String capLabel(fr.pioneers.fauna.FaunaGroup g) {
        return switch (g) {
            case GAME -> "gibier et bêtes de pâture sauvages : cerfs, sangliers, bisons, ours, lièvres, girafes, vaches, moutons...";
            case PREDATOR -> "prédateurs : loups, chiens sauvages, renards, lions, tigres, serpents, orques, grands requins...";
            case BIRD -> "oiseaux : corbeaux, pics-verts, pigeons, mouettes, perroquets...";
            case MARINE -> "faune marine : baleines, requins, raies, phoques, manchots, dauphins, calmars...";
            case FISH -> "poissons du mod et poissons vanilla";
        };
    }
}

package fr.pioneers.common;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Options cote client (fichier config/pioneers-client.toml).
 */
public final class PiClientConfig {

    private PiClientConfig() {
    }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue GRAVITATIONAL_LENSING;
    public static final ForgeConfigSpec.BooleanValue ACCRETION_DISK;
    public static final ForgeConfigSpec.BooleanValue SUN_HEAT_HAZE;
    public static final ForgeConfigSpec.BooleanValue SPACE_MUSIC;
    /** 1.6 : musiques Pioneer's de la Terre (client/music/EarthMusic). */
    public static final ForgeConfigSpec.BooleanValue EARTH_MUSIC;
    public static final ForgeConfigSpec.DoubleValue EARTH_MUSIC_SHARE;
    public static final ForgeConfigSpec.BooleanValue EARTH_MUSIC_PROGRESSION;
    public static final ForgeConfigSpec.BooleanValue EARTH_MUSIC_TITLE;
    public static final ForgeConfigSpec.DoubleValue CAMERA_SHAKE;
    public static final ForgeConfigSpec.BooleanValue TRANSIT_SCREEN;
    public static final ForgeConfigSpec.BooleanValue EVENT_BANNERS;
    public static final ForgeConfigSpec.BooleanValue EVENT_VISUALS;
    public static final ForgeConfigSpec.BooleanValue STAGE_SEPARATION;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("black_hole");
        GRAVITATIONAL_LENSING = builder
              .comment("Deforme l'image autour du trou noir (lentille gravitationnelle). A desactiver en cas de souci avec un pack de shaders.")
              .define("gravitationalLensing", true);
        ACCRETION_DISK = builder
              .comment("Affiche le disque d'accretion quand le trou noir est stabilise.")
              .define("accretionDisk", true);
        builder.pop();
        builder.push("sun");
        SUN_HEAT_HAZE = builder
              .comment("Distorsion de chaleur autour du soleil. A desactiver en cas de souci avec un pack de shaders.")
              .define("heatHaze", true);
        builder.pop();
        builder.push("audio");
        SPACE_MUSIC = builder
              .comment("Musiques du mod sur les astres et en orbite (les disques de juke-box restent jouables).")
              .define("spaceMusic", true);
        EARTH_MUSIC = builder
              .comment("Musiques Pioneer's sur la Terre (Overworld), jouees a la place d'une partie des musiques vanilla (les disques restent jouables).")
              .define("earthMusic", true);
        EARTH_MUSIC_SHARE = builder
              .comment("Part des musiques de la Terre confiees aux pieces Pioneer's (0 = aucune, 1 = toutes ; le rythme des silences reste celui du jeu).")
              .defineInRange("earthMusicShare", 0.5, 0.0, 1.0);
        EARTH_MUSIC_PROGRESSION = builder
              .comment("Les pieces se debloquent avec la progression (progres du mod : moulins, forge, vapeur, industrie, chimie, atome) ; sinon toutes passent des le debut.")
              .define("earthMusicProgression", true);
        EARTH_MUSIC_TITLE = builder
              .comment("Affiche discretement le titre d'une piece Pioneer's quand elle commence.")
              .define("earthMusicTitle", true);
        builder.pop();
        builder.push("travel");
        CAMERA_SHAKE = builder
              .comment("Intensite des tremblements de camera (decollage, rentree, capsule). 0 = aucun.")
              .defineInRange("cameraShake", 1.0, 0.0, 2.0);
        TRANSIT_SCREEN = builder
              .comment("Affiche l'ecran de transit (cinematique du voyage) ; sinon, un simple compteur en haut de l'ecran.")
              .define("transitScreen", true);
        STAGE_SEPARATION = builder
              .comment("Montre le premier etage qui se detache et retombe pendant l'ascension.")
              .define("stageSeparation", true);
        builder.pop();
        builder.push("events");
        EVENT_BANNERS = builder.comment("Bandeaux d'annonce des evenements spatiaux.").define("banners", true);
        EVENT_VISUALS = builder.comment("Effets visuels des evenements (brouillard des tempetes, teinte des eruptions, aurores).").define("visuals", true);
        builder.pop();
        fr.pioneers.client.rp.RpClientConfig.build(builder); //1.9 : MAJ RP (allures, gains d'XP)
        fr.pioneers.client.minimap.MinimapConfig.build(builder); //1.10.3 : minimap (sans classe client de Minecraft)
        fr.pioneers.client.guide.JourneyHudConfig.build(builder); //1.0.3-beta (lot 2) : etapes du Periple a l'ecran (sans classe client de Minecraft)
        fr.pioneers.client.look.LookHudConfig.build(builder); //1.0.5-beta (lot 14) : fiche au regard (coin, ecarts, taille ; sans classe client)
        SPEC = builder.build();
    }
}

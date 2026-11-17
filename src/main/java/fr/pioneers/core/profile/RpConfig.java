package fr.pioneers.core.profile;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 1.9 (MAJ RP) : reglages des quatre blocs, sections du fichier commun {@code pioneers-common.toml} (construites par
 * {@code PiCommonConfig}). Valeurs de depart des specifications ; lecture sure par {@code PiCommonConfig.get(valeur, defaut)}.
 */
public final class RpConfig {

    private RpConfig() {
    }

    // ------------------------------------------------------------------ pionnier (bloc 2)
    public static ForgeConfigSpec.DoubleValue HAND_XP, AUTO_XP, AFFINITY, BRIGADE_STEP, BRIGADE_MAX, RECONVERSION_PENALTY;
    public static ForgeConfigSpec.IntValue AUTO_CAP, AUTO_RADIUS, BRIGADE_RADIUS, FATIGUE_HALF, FATIGUE_TENTH, RECONVERSION_DAYS;
    public static ForgeConfigSpec.BooleanValue XP_IN_CREATIVE, ECONOMY, INITIATION;
    public static ForgeConfigSpec.LongValue RECONVERSION_TOKENS;
    // ------------------------------------------------------------------ ruines (bloc 1)
    public static ForgeConfigSpec.BooleanValue RUINS, SENTINELS, RESTORED_OWNER_ONLY;
    public static ForgeConfigSpec.IntValue RUIN_DENSITY, RUIN_ANNEXES;
    public static ForgeConfigSpec.DoubleValue RUIN_WEAR;
    // ------------------------------------------------------------------ economie, villages, reputation (bloc 3)
    public static ForgeConfigSpec.DoubleValue STALL_TAX, QUOTA_MULTIPLIER, EVENT_CHANCE;
    public static ForgeConfigSpec.LongValue PAY_DAILY_MAX;
    public static ForgeConfigSpec.IntValue STALLS, COUNTERS, REPUTATION_DAILY_CAP;
    public static ForgeConfigSpec.BooleanValue STALL_PROTECTED, PEDDLER_REPLACE_VANILLA, VILLAGE_DECOR, VILLAGERS;
    // ------------------------------------------------------------------ clans (bloc 4)
    public static ForgeConfigSpec.BooleanValue CLANS, WAR;
    public static ForgeConfigSpec.IntValue MAX_CLANS, MAX_MEMBERS, CLAIM_BASE, CLAIM_PER_MEMBER, CLAIM_PER_LEVEL, CLAIM_MAX, CLAIM_SOLO_MAX,
          MAX_PLAYER_PEOPLES;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> CHUNKLOAD_DIMENSIONS;
    /**
     * 1.0.3-beta (lot 16) : achat de claims au tresor du clan (plafonds d'achat). 1.0.5-beta (lot 13, Guillaume : « prix fixe 100 Shining ») :
     * prix FIXE par claim achete (plus de progression base / pas).
     */
    public static ForgeConfigSpec.LongValue CLAIM_PRICE;
    public static ForgeConfigSpec.IntValue CLAIM_BUY_MAX, CLAIM_BUY_SOLO_MAX;
    /**
     * 1.0.5-beta (lot 13, Guillaume : « les claims ne sont plus charges ; chunkloads plus chers, courbe en logarithme neperien, base 50
     * Shining, maximum 500 ») : prix du n-ieme emplacement de chunkloading d'un clan = min(max, arrondi(base + coef * ln n)), paye a la pose.
     * (Les claims charges d'office du lot 8 de la 1.0.4 sont retires : cles claimsLoaded* supprimees.)
     */
    public static ForgeConfigSpec.LongValue CHUNKLOAD_PRICE_BASE, CHUNKLOAD_PRICE_MAX;
    // 1.0.6 (Guillaume, 17/11/2026, 21 h 03 : « retire le plafond de chunk max ») : plus de plafond de chunkloading (ni par clan, ni pour le
    // serveur) : cles chunkloadGlobalMax, chunkloadBuyMax et chunkloadBuySoloMax retirees
    public static ForgeConfigSpec.DoubleValue CHUNKLOAD_PRICE_COEF;

    /** Appele par le bloc statique de {@code PiCommonConfig}, avant {@code builder.build()}. */
    public static void build(ForgeConfigSpec.Builder b) {
        b.comment("MAJ RP : le pionnier (Carnet, skills, vocations).").push("pioneer");
        HAND_XP = b.comment("Multiplicateur de l'XP gagnee a la main.").defineInRange("handXp", 1.0, 0.0, 10.0);
        AUTO_XP = b.comment("Part de l'XP gagnee par les machines, robots et programmes du joueur (Regle de la Main).")
              .defineInRange("autoXp", 0.2, 0.0, 1.0);
        AUTO_CAP = b.comment("Plafond d'XP automatique par skill et par jour de jeu.").defineInRange("autoDailyCap", 300, 0, 100_000);
        AUTO_RADIUS = b.comment("Distance maximale (blocs) entre le proprietaire et sa machine pour l'XP automatique.")
              .defineInRange("autoRadius", 32, 1, 512);
        AFFINITY = b.comment("Multiplicateur d'XP des deux skills de la vocation.").defineInRange("affinity", 1.5, 1.0, 4.0);
        BRIGADE_STEP = b.comment("Brigade : bonus d'XP par vocation distincte presente.").defineInRange("brigadeStep", 0.05, 0.0, 1.0);
        BRIGADE_MAX = b.defineInRange("brigadeMax", 0.15, 0.0, 1.0);
        BRIGADE_RADIUS = b.defineInRange("brigadeRadius", 24, 1, 256);
        FATIGUE_HALF = b.comment("Fatigue : au-dela de N actions identiques par minute, XP a 50 %.").defineInRange("fatigueHalf", 30, 1, 10_000);
        FATIGUE_TENTH = b.comment("Au-dela de N, XP a 10 %.").defineInRange("fatigueTenth", 60, 1, 10_000);
        XP_IN_CREATIVE = b.comment("Gagner de l'XP en creatif.").define("xpInCreative", false);
        ECONOMY = b.comment("Interrupteur de l'economie (jetons, etals, Colporteurs, Commercant, skill Commerce).").define("economy", true);
        INITIATION = b.comment("Initiation obligatoire a la premiere connexion.").define("initiation", true);
        RECONVERSION_DAYS = b.comment("Jours de jeu entre deux reconversions.").defineInRange("reconversionDays", 30, 0, 10_000);
        RECONVERSION_PENALTY = b.comment("XP perdue sur les deux skills de l'ancienne vocation.").defineInRange("reconversionPenalty", 0.25, 0.0, 1.0);
        RECONVERSION_TOKENS = b.comment("Prix d'une reconversion en jetons quand l'economie est active.")
              .defineInRange("reconversionTokens", 5_000L, 0L, 1_000_000_000L);
        b.pop();

        b.comment("MAJ RP : ruines, Sentinelles, restauration.").push("ruins");
        RUINS = b.comment("Generation des ruines (Overworld).").define("enabled", true);
        RUIN_DENSITY = b.comment("Densite des ruines (0 : aucune, 1 : normale, 3 : triple).").defineInRange("density", 1, 0, 3);
        SENTINELS = b.comment("Sentinelles (faux : serveur pacifique, aucune Sentinelle n'apparait).").define("sentinels", true);
        RUIN_WEAR = b.comment("Multiplicateur de l'usure des ruines.").defineInRange("wear", 1.0, 0.0, 2.0);
        RUIN_ANNEXES = b.comment("Annexes en briques et en pierre par ruine (au plus).").defineInRange("annexes", 2, 0, 3);
        RESTORED_OWNER_ONLY = b.comment("Une ruine restauree n'est utilisable que par ses contributeurs.").define("restoredOwnerOnly", false);
        b.pop();

        b.comment("MAJ RP : economie, villages, reputation.").push("economy");
        STALL_TAX = b.comment("Taxe des etals (detruite).").defineInRange("stallTax", 0.05, 0.0, 0.5);
        PAY_DAILY_MAX = b.comment("Plafond quotidien des virements par joueur.").defineInRange("payDailyMax", 50_000L, 0L, 1_000_000_000L);
        QUOTA_MULTIPLIER = b.comment("Multiplicateur des quotas d'achat des PNJ.").defineInRange("quotaMultiplier", 1.0, 0.0, 100.0);
        EVENT_CHANCE = b.comment("Chance d'un evenement de village tous les 3 jours.").defineInRange("eventChance", 0.25, 0.0, 1.0);
        STALLS = b.comment("Etals par joueur et par monde.").defineInRange("stalls", 4, 0, 1000);
        COUNTERS = b.comment("Comptoirs par joueur et par monde.").defineInRange("counters", 2, 0, 1000);
        STALL_PROTECTED = b.comment("Seul le proprietaire (et les operateurs) configure, vide ou casse un etal.").define("stallProtected", true);
        PEDDLER_REPLACE_VANILLA = b.comment("Le marchand ambulant est remplace par un Colporteur.").define("peddlerReplacesVanilla", true);
        VILLAGE_DECOR = b.comment("Decor de place pose a la decouverte d'un village.").define("villageDecor", true);
        VILLAGERS = b.comment("Ecran et identite des villageois du mod (faux : echanges de Minecraft).").define("villagers", true);
        REPUTATION_DAILY_CAP = b.comment("Points de reputation gagnes au plus par peuple et par jour.").defineInRange("reputationDailyCap", 30, 0, 10_000);
        b.pop();

        b.comment("MAJ RP : clans et multijoueur.").push("clans");
        CLANS = b.comment("Interrupteur des clans.").define("enabled", true);
        MAX_CLANS = b.defineInRange("maxClans", 200, 1, 100_000);
        MAX_MEMBERS = b.defineInRange("maxMembers", 40, 2, 1000);
        CLAIM_BASE = b.comment("Budget de claims : B = base + parMembre * actifs + parNiveau * niveau.").defineInRange("claimBase", 9, 0, 10_000);
        CLAIM_PER_MEMBER = b.defineInRange("claimPerMember", 3, 0, 10_000);
        CLAIM_PER_LEVEL = b.defineInRange("claimPerLevel", 4, 0, 10_000);
        CLAIM_MAX = b.defineInRange("claimMax", 400, 1, 100_000);
        CLAIM_SOLO_MAX = b.comment("Plafond du Solitaire (clan solo).").defineInRange("claimSoloMax", 100, 1, 100_000);
        CLAIM_PRICE = b.comment("Achat de claims au tresor du clan : prix fixe d'un claim achete (Shining). Les plafonds claimMax / claimSoloMax"
              + " ne bornent que la part gratuite du budget.").defineInRange("claimPrice", 100L, 0L, 1_000_000_000L);
        CLAIM_BUY_MAX = b.comment("Claims achetes au plus par un clan (0 : achat ferme).").defineInRange("claimBuyMax", 200, 0, 100_000);
        CLAIM_BUY_SOLO_MAX = b.comment("Claims achetes au plus par un Solitaire (clan solo).").defineInRange("claimBuySoloMax", 50, 0, 100_000);
        CHUNKLOAD_DIMENSIONS = b.comment("Dimensions ou le chunkloading de clan est permis.")
              .defineListAllowEmpty(List.of("chunkloadDimensions"), () -> List.of("minecraft:overworld"), o -> o instanceof String);
        CHUNKLOAD_PRICE_BASE = b.comment("Prix du n-ieme emplacement de chunkloading achete par un clan (tresor) :",
              "min(chunkloadPriceMax, arrondi(chunkloadPriceBase + chunkloadPriceCoef * ln n)) Shining (1er : 50, 2e : 125, 4e : 200, 8e : 275), paye a l'achat.")
              .defineInRange("chunkloadPriceBase", 50L, 0L, 1_000_000_000L);
        CHUNKLOAD_PRICE_COEF = b.comment("Coefficient du logarithme neperien du prix des emplacements.").defineInRange("chunkloadPriceCoef", 108.0, 0.0, 1_000_000.0);
        CHUNKLOAD_PRICE_MAX = b.comment("Prix maximal d'un emplacement de chunkloading.").defineInRange("chunkloadPriceMax", 500L, 0L, 1_000_000_000L);
        WAR = b.comment("Guerres entre clans.").define("war", true);
        MAX_PLAYER_PEOPLES = b.comment("Peuples de joueurs au plus.").defineInRange("maxPlayerPeoples", 4, 0, 100);
        b.pop();
    }
}

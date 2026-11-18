package fr.pioneers.society.rp.client;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 1.9 (MAJ RP) : options client ({@code pioneers-client.toml}, section {@code rp}). Ne reference aucune classe client de Minecraft : la
 * classe est chargee aussi sur un serveur dedie (construction du fichier de reglages).
 */
public final class RpClientConfig {

    private RpClientConfig() {
    }

    /** 1.12.0 : les options de l'ancienne allure masquent desormais les cosmetiques portes (garde-robe). */
    public static ForgeConfigSpec.BooleanValue HIDE_OTHERS_COSMETICS, HIDE_OWN_COSMETICS, XP_POPUPS, CLAIM_BORDERS;

    public static void build(ForgeConfigSpec.Builder b) {
        b.comment("MAJ RP : Carnet, cosmetiques, territoires.").push("rp");
        HIDE_OTHERS_COSMETICS = b.comment("Masquer les cosmetiques portes par les autres joueurs (leur armure se voit).").define("hideOthersCosmetics", false);
        HIDE_OWN_COSMETICS = b.comment("Masquer ses propres cosmetiques (son armure se voit).").define("hideOwnCosmetics", false);
        XP_POPUPS = b.comment("Afficher les gains d'XP et de jetons au-dessus de la barre de vie.").define("xpPopups", true);
        CLAIM_BORDERS = b.comment("Lignes de bordure des territoires de clan.").define("claimBorders", false);
        b.pop();
    }

    public static boolean get(ForgeConfigSpec.BooleanValue v, boolean fallback) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }
}

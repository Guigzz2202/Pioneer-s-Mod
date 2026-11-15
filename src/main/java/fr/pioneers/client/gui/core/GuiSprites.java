package fr.pioneers.client.gui.core;

import fr.pioneers.Pioneers;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Textures optionnelles de l'interface standard, a deposer dans {@code assets/pioneers/textures/gui/machine/<nom>.png} : si elles
 * existent, elles sont dessinees (etirees a la taille demandee) ; sinon l'interface garde son dessin vectoriel. Le graphisme peut ainsi
 * arriver apres le code sans rien casser. La liste des noms est dans le brief du Projet ({@code claude/brief-sonnet-interfaces.md}).
 */
public final class GuiSprites {

    private static final Map<String, Boolean> PRESENT = new HashMap<>();

    private GuiSprites() {
    }

    public static ResourceLocation id(String name) {
        return Pioneers.rl("textures/gui/machine/" + name + ".png");
    }

    /** La texture existe-t-elle (resultat garde ; vide a chaque rechargement des ressources par {@link #reset()}). */
    public static boolean has(String name) {
        return PRESENT.computeIfAbsent(name, n -> Minecraft.getInstance().getResourceManager().getResource(id(n)).isPresent());
    }

    /** Dessine la texture entiere dans le rectangle ; renvoie false si elle n'existe pas. */
    public static boolean draw(GuiGraphics graphics, String name, int x, int y, int w, int h, int texWidth, int texHeight) {
        if (!has(name)) {
            return false;
        }
        graphics.blit(id(name), x, y, w, h, 0, 0, texWidth, texHeight, texWidth, texHeight);
        return true;
    }

    /** API Machine (lot 2) : texture retournee de gauche a droite (onglets du bord droit de l'interface standard v2). */
    public static boolean drawMirrored(GuiGraphics graphics, String name, int x, int y, int w, int h, int texWidth, int texHeight) {
        if (!has(name)) {
            return false;
        }
        graphics.blit(id(name), x, y, w, h, texWidth, 0, -texWidth, texHeight, texWidth, texHeight);
        return true;
    }

    /** Variante du tier ({@code nom_<tier>}) si elle existe, sinon le nom commun. */
    public static String tiered(String name, String tier) {
        String own = name + "_" + tier;
        if (has(own)) {
            return own;
        }
        //Machine sans textures propres : l'acier, puis le tier de base
        return has(name + "_steel") ? name + "_steel" : has(name + "_basic") ? name + "_basic" : name;
    }

    /** Dessine une partie (u, v, uw, vh) de la texture etiree dans le rectangle. */
    public static void region(GuiGraphics graphics, String name, int x, int y, int w, int h, int u, int v, int uw, int vh, int texWidth, int texHeight) {
        if (w > 0 && h > 0 && uw > 0 && vh > 0) {
            graphics.blit(id(name), x, y, w, h, u, v, uw, vh, texWidth, texHeight);
        }
    }

    /**
     * Neuf morceaux : coins de {@code border} px gardes tels quels, bords et centre etires. Renvoie false si la texture n'existe pas.
     */
    public static boolean nine(GuiGraphics graphics, String name, int x, int y, int w, int h, int texWidth, int texHeight, int border) {
        if (!has(name)) {
            return false;
        }
        int b = Math.min(border, Math.min(w, h) / 2), tb = border;
        int cw = texWidth - 2 * tb, ch = texHeight - 2 * tb;
        int[] dx = {x, x + b, x + w - b}, dw = {b, w - 2 * b, b}, su = {0, tb, texWidth - tb}, sw = {tb, cw, tb};
        int[] dy = {y, y + b, y + h - b}, dh = {b, h - 2 * b, b}, sv = {0, tb, texHeight - tb}, sh = {tb, ch, tb};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                region(graphics, name, dx[i], dy[j], dw[i], dh[j], su[i], sv[j], sw[i], sh[j], texWidth, texHeight);
            }
        }
        return true;
    }

    /** Trois tranches verticales : haut et bas gardes, rangees {@code top..texHeight-bottom} etirees. */
    public static boolean vertical(GuiGraphics graphics, String name, int x, int y, int w, int h, int texWidth, int texHeight, int top, int bottom) {
        if (!has(name)) {
            return false;
        }
        region(graphics, name, x, y, w, top, 0, 0, texWidth, top, texWidth, texHeight);
        region(graphics, name, x, y + top, w, h - top - bottom, 0, top, texWidth, texHeight - top - bottom, texWidth, texHeight);
        region(graphics, name, x, y + h - bottom, w, bottom, 0, texHeight - bottom, texWidth, bottom, texWidth, texHeight);
        return true;
    }

    /**
     * 1.0.3-beta (lot 6) : bandes verticales libres (chacune {@code {haut texture, bas texture, haut ecran, bas ecran}}, relatives), chaque
     * bande coupee en trois colonnes ({@code left} et {@code right} gardees, le milieu etire). Sert a l'habillage du panneau v2
     * ({@code StandardLayout.panelBands}). Renvoie false si la texture n'existe pas.
     */
    public static boolean bands(GuiGraphics graphics, String name, int x, int y, int w, int texWidth, int texHeight, int left, int right, int[][] bands) {
        if (!has(name)) {
            return false;
        }
        int[] dx = {x, x + left, x + w - right}, dw = {left, w - left - right, right}, su = {0, left, texWidth - right},
              sw = {left, texWidth - left - right, right};
        for (int[] b : bands) {
            for (int i = 0; i < 3; i++) {
                region(graphics, name, dx[i], y + b[2], dw[i], b[3] - b[2], su[i], b[0], sw[i], b[1] - b[0], texWidth, texHeight);
            }
        }
        return true;
    }

    /**
     * Neuf morceaux de tailles libres : colonnes {@code left} et {@code right}, rangees {@code top} et {@code bottom} gardees, le reste
     * etire (panneaux plus larges ou plus hauts que la texture).
     */
    public static boolean grid(GuiGraphics graphics, String name, int x, int y, int w, int h, int texWidth, int texHeight, int left, int right,
          int top, int bottom) {
        if (!has(name)) {
            return false;
        }
        int[] dx = {x, x + left, x + w - right}, dw = {left, w - left - right, right}, su = {0, left, texWidth - right},
              sw = {left, texWidth - left - right, right};
        int[] dy = {y, y + top, y + h - bottom}, dh = {top, h - top - bottom, bottom}, sv = {0, top, texHeight - bottom},
              sh = {top, texHeight - top - bottom, bottom};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                region(graphics, name, dx[i], dy[j], dw[i], dh[j], su[i], sv[j], sw[i], sh[j], texWidth, texHeight);
            }
        }
        return true;
    }

    /** Couleur de fonction de chaque machine (la meme que ses modeles ; source : {@code machine_colors()} de tools/gc/core/gui_machine.py). */
    private static final Map<String, Integer> ACCENTS = Map.ofEntries(
          Map.entry("research_table", 0x4FD8C0),
          Map.entry("ai_lab", 0x5AC8FF),
          Map.entry("factory_ai_core", 0xC878FF),
          Map.entry("geo_spectrometer", 0xD9A441),
          Map.entry("bio_sequencer", 0xE2578F),
          Map.entry("asteroid_crusher", 0xE0503A),
          Map.entry("biome_analyzer", 0x8BD24A),
          Map.entry("data_antenna", 0x5B8CFF),
          Map.entry("module_assembly_table", 0x3FA9F5),
          Map.entry("equipment_assembly_table", 0xB07CFF),
          Map.entry("rover_assembly_table", 0xF0A030),
          Map.entry("synthesizer", 0x6C7CFF),
          Map.entry("prospecting_radar", 0xF0C040),
          Map.entry("rover_dock", 0xF08A30),
          Map.entry("chunk_loader", 0x8E4FC8),
          Map.entry("force_field_generator", 0x3FB6C4),
          Map.entry("lightning_collector", 0xE0C040),
          Map.entry("oxygen_diffuser", 0x8FD0F0),
          Map.entry("gravity_compressor", 0xFF8A1F),
          Map.entry("rocket_assembler", 0xE8553A),
          Map.entry("greenhouse", 0x6FD06F),
          Map.entry("mining_outpost", 0xE0A030),
          Map.entry("launch_pad", 0xE8553A),
          Map.entry("drone_bay", 0x5B8CFF),
          Map.entry("space_stabilizer_star", 0xFFB020),
          Map.entry("space_stabilizer_singularity", 0xA865F5),
          Map.entry("space_simulator_solar", 0xFFC24A),
          Map.entry("space_simulator_galaxy", 0x7C8CFF),
          Map.entry("solar_absorber", 0xF08A30));

    /** 1.0.4-beta (lot 26) : habillage clair des machines de salle blanche (meme valeur que {@code NbtRoles.CLEANROOM_GUI}). */
    public static final String LIGHT = "cleanroom";
    /**
     * 1.0.4-beta (lot 31, Guillaume : « l'interface de la salle blanche n'embarque pas de tier ») : accent PROPRE de la salle blanche, gris
     * argent a peine bleute (= {@code CR_ACCENT} de T:core/gui_machine.py : cerclages, filets des cases, reperes), sans rien de commun avec
     * le vert, le bleu, le rouge, le bordeaux des tiers ; {@link #CLEANROOM_INK} : le meme, fonce, pour les remplissages sur fond blanc
     * (fleche, barre, pastille de l'ecran).
     */
    public static final int CLEANROOM_ACCENT = 0xB4BCC4, CLEANROOM_INK = 0x6C7783;

    /** Couleur d'accent d'une interface (id de machine), vert d'accent du socle a defaut ; salle blanche : gris argent. */
    public static int accent(String tier) {
        return LIGHT.equals(tier) ? CLEANROOM_ACCENT : ACCENTS.getOrDefault(tier, 0x3FD0A0);
    }

    public static void reset() {
        PRESENT.clear();
    }
}

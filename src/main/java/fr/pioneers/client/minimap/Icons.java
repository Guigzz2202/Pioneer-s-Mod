package fr.pioneers.client.minimap;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.pioneers.Pioneers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.10.3 : pictogrammes de la minimap, atlas {@code textures/gui/minimap/icons.png} (256 x 64, cases de 16 px, dessin de {@link #SIZE} px
 * au coin de la case) ecrit par {@code tools/gc/minimap/minimap_assets.py}. Les pictogrammes des lieux portent leurs couleurs ; ceux des
 * waypoints sont clairs, teintes a la couleur du waypoint.
 */
public final class Icons {

    public static final ResourceLocation ATLAS = Pioneers.rl("textures/gui/minimap/icons.png");
    public static final int SIZE = 9, CELL = 16, W = 256, H = 64;

    // lieux (rangee 0)
    public static final int STATION = 0, HARBOR = 1, POST = 2, SUBSTATION = 3, BUOY = 4, HUB = 5, RELAY = 6, TERMINAL = 7, POINT = 8, CHEST = 9,
          TUBE = 10;
    // waypoints (rangee 1), dans l'ordre de l'ecran d'edition
    public static final int WP_FIRST = 16;
    public static final String[] WAYPOINT = {"diamond", "flag", "house", "star", "pickaxe", "skull", "circle", "cross", "tower", "tree", "cave",
          "heart"};
    public static final int WP_DEATH = WP_FIRST + 5;
    // vehicules et unites (rangee 2)
    public static final int TRAIN = 32, SHIP = 33, ROBOT = 34, CART = 35, NORTH = 36;
    // 1.12.1 (F1) : ligne d'environnement (thermometre, oxygene, gravite ; clairs, teintes a l'affichage)
    public static final int THERMO = 37, OXYGEN = 38, GRAVITY = 39;
    // 1.0.5-beta (lot 14) : goutte de la ligne climat (humidite)
    public static final int HUMIDITY = 40;

    private Icons() {
    }

    public static int waypoint(int index) {
        return WP_FIRST + Math.max(0, Math.min(WAYPOINT.length - 1, index));
    }

    /** Dessine le pictogramme {@code icon} centre en (x, y), teinte {@code rgb} (0xFFFFFF : couleurs d'origine), opacite {@code alpha}. */
    public static void draw(GuiGraphics g, int icon, float x, float y, int rgb, float alpha) {
        int u = (icon % 16) * CELL, v = (icon / 16) * CELL;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, alpha);
        int ix = Math.round(x - SIZE / 2F), iy = Math.round(y - SIZE / 2F);
        g.blit(ATLAS, ix, iy, u, v, SIZE, SIZE, W, H);
        g.setColor(1, 1, 1, 1);
    }
}

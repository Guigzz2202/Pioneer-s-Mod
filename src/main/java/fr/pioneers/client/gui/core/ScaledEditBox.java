package fr.pioneers.client.gui.core;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 1.0.4-beta (lot 34, Guillaume : « [l'interface du comptoir] est cassee » : le texte de « Chercher... » chevauchait le cadre) : CHAMP DE
 * RECHERCHE des interfaces v2, a la taille des autres textes de l'ecran. Le champ vanilla ecrit a l'echelle 1 (8 px de haut) dans un cadre
 * de 11 px : ses lettres touchaient les coins du cadre et paraissaient deux fois plus grosses que tout le reste. Ici le texte, le curseur, la
 * selection et le texte d'aide sont dessines a l'echelle {@link #scale} (0,75 : 6 px), centres dans le cadre ; la souris est ramenee dans le
 * repere du champ (clic = curseur a la bonne lettre, survol limite au cadre). Le champ garde ses coordonnees d'ecran : x, y, largeur et
 * hauteur VISIBLES ({@code w} x {@code h}).
 */
public class ScaledEditBox extends EditBox {

    /** Echelle du texte des champs de recherche (celle des boutons d'acier de l'ecran). */
    public static final float SCALE = 0.75F;
    private final float scale;

    /** Champ de {@code w} x {@code h} (visibles) en (x, y), texte a {@link #SCALE}. */
    public ScaledEditBox(Font font, int x, int y, int w, int h, Component name) {
        this(font, x, y, w, h, SCALE, name);
    }

    public ScaledEditBox(Font font, int x, int y, int w, int h, float scale, Component name) {
        super(font, x, y, Math.round(w / scale), Math.round(h / scale), name);
        this.scale = scale;
    }

    public float scale() {
        return scale;
    }

    /** Largeur et hauteur visibles. */
    public int shownWidth() {
        return Math.round(getWidth() * scale);
    }

    public int shownHeight() {
        return Math.round(getHeight() * scale);
    }

    private double localX(double mouseX) {
        return getX() + (mouseX - getX()) / scale;
    }

    private double localY(double mouseY) {
        return getY() + (mouseY - getY()) / scale;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        graphics.pose().pushPose();
        graphics.pose().translate(getX(), getY(), 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.pose().translate(-getX(), -getY(), 0);
        super.renderWidget(graphics, (int) Math.floor(localX(mouseX)), (int) Math.floor(localY(mouseY)), partial);
        graphics.pose().popPose();
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(localX(mouseX), localY(mouseY));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(localX(mouseX), localY(mouseY), button);
    }
}

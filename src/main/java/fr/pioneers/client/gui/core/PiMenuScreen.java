package fr.pioneers.client.gui.core;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.core.Texts;
import fr.pioneers.core.menu.MenuSlot;
import fr.pioneers.core.menu.PiTileMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface d'un bloc du socle (sans Mekanism), au style sombre de la robotique : panneau a filet d'accent, cases en creux, ecran d'etat,
 * jauge d'energie (FE) et petits boutons dessines a chaque image (zones cliquables retenues pour le clic suivant).
 */
public abstract class PiMenuScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {

    public static final int BG = 0xFF141A20, EDGE = 0xFF2E3A44, ACCENT = 0xFF3FD0A0, TEXT = 0xFFE0F5EA, DIM = 0xFF8FA3A0, SCREEN = 0xFF0B1016,
          SCREEN_TEXT = 0xFF8CF5C6;

    private record Hit(int x0, int y0, int x1, int y1, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>(), pending = new ArrayList<>();
    @Nullable
    private List<Component> tooltip;

    protected PiMenuScreen(M menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title);
        imageWidth = width;
        imageHeight = height;
        inventoryLabelY = height - 94;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        pending.clear();
        tooltip = null;
        renderBackground(graphics);
        //1.12.3 (lot F3) : grosses piles des buffers : quantite vanilla masquee pendant le dessin des cases, nombre abrege dessine ensuite
        PiTileMenu<?> tileMenu = menu instanceof PiTileMenu<?> m ? m : null;
        if (tileMenu != null) {
            tileMenu.hideBigCounts(true);
        }
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
        } finally {
            if (tileMenu != null) {
                tileMenu.hideBigCounts(false);
            }
        }
        //0.19.13 : calque au-dessus de tout (panneaux ouverts) : apres les cases et leurs nombres, avant les infobulles
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos, topPos, 350);
        drawOverlay(graphics, mouseX - leftPos, mouseY - topPos);
        graphics.pose().popPose();
        hits.clear();
        hits.addAll(pending);
        if (tooltip != null && menu.getCarried().isEmpty()) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        drawPanel(graphics, x, y);
        for (Slot slot : menu.slots) {
            drawSlot(graphics, slot, x + slot.x, y + slot.y);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos, topPos, 0);
        drawBackground(graphics, mouseX - leftPos, mouseY - topPos);
        graphics.pose().popPose();
    }

    /** Fond du panneau (coordonnees ecran). */
    protected void drawPanel(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + imageWidth, y + imageHeight, BG);
        graphics.renderOutline(x, y, imageWidth, imageHeight, EDGE);
        graphics.fill(x, y, x + imageWidth, y + 1, ACCENT);
    }

    /** Fond d'une case (x, y : coin de l'objet, coordonnees ecran). */
    protected void drawSlot(GuiGraphics graphics, Slot slot, int x, int y) {
        slot(graphics, x, y);
    }

    /** Fond propre a l'interface (coordonnees relatives au panneau, deja translatees). */
    protected void drawBackground(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        drawTitle(graphics, mouseX - leftPos, mouseY - topPos);
        if (inventoryLabelY >= 0) {
            graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, DIM, false);
        }
        graphics.pose().pushPose();
        //Au-dessus des objets des cases
        graphics.pose().translate(0, 0, 300);
        drawBigCounts(graphics);
        drawForeground(graphics, mouseX - leftPos, mouseY - topPos);
        graphics.pose().popPose();
    }

    /** Titre du panneau (coordonnees relatives au panneau). */
    protected void drawTitle(GuiGraphics graphics, int mouseX, int mouseY) {
        //Titre tenu dans le panneau (resserre jusqu'a 70 %, puis coupe) : les noms de machines « compact(e) MKIII » sont longs
        GuiDraw.text(graphics, font, title, titleLabelX, titleLabelY, 1F, TEXT, imageWidth - titleLabelX - 8);
    }

    /** 1.12.3 : quantite abregee des piles de plus de 99 objets (cases a plusieurs piles), en bas a droite de la case, aux trois quarts. */
    private void drawBigCounts(GuiGraphics graphics) {
        for (Slot slot : menu.slots) {
            if (!(slot instanceof MenuSlot menuSlot) || menuSlot.slot.getCount() <= MenuSlot.BIG_COUNT) {
                continue;
            }
            String text = BigSlotsClient.shortCount(menuSlot.slot.getCount());
            float scale = 0.75F;
            graphics.pose().pushPose();
            graphics.pose().translate(slot.x + 17 - font.width(text) * scale, slot.y + 17 - 7 * scale, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, text, 0, 0, 0xFFFFFF, true);
            graphics.pose().popPose();
        }
    }

    /** Premier plan (coordonnees relatives au panneau ; souris relative aussi). */
    protected void drawForeground(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    /** 0.19.13 : calque dessine apres tout le reste (cases, piles, premier plan) : panneaux ouverts par-dessus l'interface. */
    protected void drawOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    /** 0.19.13 : la souris est-elle sur un panneau ouvert (les cases dessous ne reagissent plus) ? Coordonnees ecran. */
    protected boolean covers(double mouseX, double mouseY) {
        return false;
    }

    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        return !covers(mouseX, mouseY) && super.isHovering(x, y, width, height, mouseX, mouseY);
    }

    // ------------------------------------------------------------------
    // Elements
    // ------------------------------------------------------------------

    /** Case en creux (x, y : coin de l'objet, coordonnees ecran). */
    public static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF2C3640);
        graphics.fill(x, y, x + 16, y + 16, 0xFF0E1419);
    }

    /** Ecran d'etat (relatif au panneau, dans drawBackground/drawForeground). */
    protected static void screen(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, SCREEN);
        graphics.renderOutline(x, y, w, h, EDGE);
    }

    /** Lignes d'un ecran d'etat (texte reduit, sans deborder). */
    protected void screenLines(GuiGraphics graphics, int x, int y, int w, float scale, List<Component> lines, int color) {
        float lineHeight = 9 * scale + 1;
        for (int i = 0; i < lines.size(); i++) {
            GuiDraw.text(graphics, font, lines.get(i), x + 3, y + 3 + i * lineHeight, scale, color, w - 6);
        }
    }

    /** Jauge d'energie verticale avec son infobulle (stock, consommation). */
    protected void energyBar(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, long stored, long capacity, long used) {
        graphics.fill(x, y, x + w, y + h, SCREEN);
        graphics.renderOutline(x, y, w, h, EDGE);
        double ratio = capacity <= 0 ? 0 : Math.min(1, stored / (double) capacity);
        int filled = (int) Math.round((h - 2) * ratio);
        if (filled > 0) {
            graphics.fill(x + 1, y + h - 1 - filled, x + w - 1, y + h - 1, 0xFFE04848);
            graphics.fill(x + 1, y + h - 1 - filled, x + w - 1, y + h - filled, 0x60FFFFFF);
        }
        if (over(mouseX, mouseY, x, y, w, h)) {
            tooltip(List.of(Component.translatable("gui.pioneers.energy.stored", Texts.energy(stored), Texts.energy(capacity)),
                  Component.translatable("gui.pioneers.energy.using", Texts.energy(used)).withStyle(ChatFormatting.GRAY)));
        }
    }

    /** Bouton (relatif au panneau). */
    protected void button(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, Component label, boolean enabled, Runnable action) {
        boolean hover = enabled && over(mouseX, mouseY, x, y, w, h);
        graphics.fill(x, y, x + w, y + h, !enabled ? 0xFF1A2027 : hover ? 0xFF3A4C5C : 0xFF2A3540);
        graphics.renderOutline(x, y, w, h, !enabled ? 0xFF242C34 : hover ? 0xFF6F8EA6 : 0xFF3C4A57);
        GuiDraw.center(graphics, font, label, x + w / 2F, y + (h - 7) / 2F, 0.9F, enabled ? TEXT : 0xFF59666A, w - 2);
        if (enabled) {
            click(x, y, w, h, action);
        }
    }

    protected boolean over(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** Zone cliquable (relative au panneau). */
    protected void click(int x, int y, int w, int h, Runnable action) {
        pending.add(new Hit(leftPos + x, topPos + y, leftPos + x + w, topPos + y + h, action));
    }

    protected void tooltip(List<Component> lines) {
        tooltip = lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (mouseX >= hit.x0 && mouseX < hit.x1 && mouseY >= hit.y0 && mouseY < hit.y1) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    hit.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

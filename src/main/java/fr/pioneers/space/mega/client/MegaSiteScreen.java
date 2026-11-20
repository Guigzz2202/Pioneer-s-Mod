package fr.pioneers.space.mega.client;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.TextFit;
import fr.pioneers.space.mega.Megastructures;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.7 (« Plus loin et au-dela », lot 4) : l'ecran du CHANTIER DE MEGASTRUCTURE, ouvert depuis la carte de la fusee. En tete : la
 * megastructure, l'ouvreur du chantier et l'avancement ; au milieu : la liste d'ingredients sur deux colonnes (icone, nom, livre / demande,
 * barre) ; en bas : les composants dont elle sera la seule source, ses ports, et comment la ravitailler (fusees cargo vers « Chantier : ... »).
 * Tout vient de l'etat synchronise ({@link MegaClient}, relu a chaque image : l'ecran suit les livraisons des fusees) ; il n'envoie rien.
 * Echap : retour a la carte.
 */
public class MegaSiteScreen extends Screen {

    private static final int W = 316, H = 236, ROW = 18, COLS = 2, ROWS = 7;
    @Nullable
    private final Screen parent;
    private final int mega;
    private final int accent;
    private final ItemStack[] icons, gatedIcons;
    private int left, top;
    private final TextFit.Hover hover = new TextFit.Hover();

    public MegaSiteScreen(int mega, @Nullable Screen parent) {
        super(Component.translatable("mega.pioneers.gui.title", Megastructures.name(mega)));
        this.parent = parent;
        this.mega = mega;
        this.accent = 0xFF000000 | Megastructures.color(mega);
        icons = new ItemStack[Megastructures.ingredientCount(mega)];
        for (int k = 0; k < icons.length; k++) {
            icons[k] = Megastructures.ingredientStack(mega, k);
        }
        String[] gated = Megastructures.gated(mega);
        gatedIcons = new ItemStack[gated.length];
        for (int j = 0; j < gated.length; j++) {
            var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(gated[j]));
            gatedIcons[j] = item == null ? ItemStack.EMPTY : new ItemStack(item);
        }
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        hover.at(mx, my);
        GuiDraw.panel(g, left, top, W, H);
        g.fill(left, top, left + W, top + 1, accent);
        boolean done = MegaClient.unlocked(mega);
        TextFit.fit(g, font, getTitle(), left + 8, top + 7, W - 16, 0xFFE8E8E8, 1F, hover);
        //ouvreur et avancement
        GuiDraw.screen(g, left + 8, top + 20, W - 16, 26);
        String opener = MegaClient.opener(mega);
        int total = Megastructures.total(mega), have = done ? total : MegaClient.deliveredTotal(mega);
        Component state = done ? Component.translatable("mega.pioneers.gui.unlocked")
              : Component.translatable("mega.pioneers.gui.progress", String.valueOf(have), String.valueOf(total));
        int sw = opener.isEmpty() ? 0 : TextFit.right(g, font, Component.translatable("mega.pioneers.gui.opened_by", opener), left + W / 2,
              top + 24, W / 2 - 14, 0xFF9AA6B2, 0.75F, hover);
        TextFit.fit(g, font, state, left + 12, top + 24, W - 24 - sw - 6, done ? 0xFF7FD69A : 0xFFD8D8D8, 0.75F, hover);
        GuiDraw.bar(g, left + 12, top + 36, W - 24, 4, have / (double) Math.max(1, total), done ? 0xFF3FB950 : accent);
        //ingredients, deux colonnes
        int n = Megastructures.ingredientCount(mega);
        int cw = (W - 16) / COLS;
        GuiDraw.screen(g, left + 8, top + 50, W - 16, ROWS * ROW + 4);
        for (int k = 0; k < n && k < COLS * ROWS; k++) {
            int x = left + 10 + (k / ROWS) * cw, y = top + 52 + (k % ROWS) * ROW;
            ItemStack icon = icons[k];
            g.renderFakeItem(icon, x, y);
            int need = Megastructures.needed(mega, k), got = done ? need : Math.min(need, MegaClient.delivered(mega, k));
            boolean full = got >= need;
            int nw = TextFit.right(g, font, Component.translatable("mega.pioneers.gui.line", String.valueOf(got), String.valueOf(need)),
                  x + cw - 62, y + 1, 56, full ? 0xFF7FD69A : 0xFFB0B8C0, 0.75F, hover);
            TextFit.fit(g, font, icon.getHoverName(), x + 19, y + 1, cw - 19 - nw - 10, full ? 0xFF7FD69A : 0xFFD8D8D8, 0.75F, hover);
            GuiDraw.bar(g, x + 19, y + 11, cw - 26, 2, got / (double) Math.max(1, need), full ? 0xFF3FB950 : accent);
            hover.tip(x, y, 16, 16, List.of(icon.getHoverName()));
        }
        //composants gates et ports
        int gy = top + 56 + ROWS * ROW;
        TextFit.fit(g, font, Component.translatable("mega.pioneers.gui.gated"), left + 10, gy + 4, 90, 0xFFE3C46B, 0.75F, hover);
        for (int j = 0; j < gatedIcons.length; j++) {
            ItemStack stack = gatedIcons[j];
            if (stack.isEmpty()) {
                continue;
            }
            int x = left + 100 + j * 20;
            g.renderFakeItem(stack, x, gy);
            hover.tip(x, gy, 16, 16, List.of(stack.getHoverName()));
        }
        Component port = Component.translatable("industry.pioneers.machine." + Megastructures.port(mega));
        TextFit.fit(g, font, Component.translatable("mega.pioneers.gui.port", port), left + 10, gy + 20, W - 20, 0xFF9AA6B2, 0.75F, hover);
        //ravitaillement : fusees cargo
        int by = top + H - 24;
        g.fill(left + 8, by, left + W - 8, by + 16, 0xFF1A2027);
        g.fill(left + 8, by + 15, left + W - 8, by + 16, done ? 0xFF3FB950 : accent);
        Component how = done ? Component.translatable("mega.pioneers.gui.unlocked")
              : Component.translatable("mega.pioneers.gui.cargo", Component.translatable("mega.pioneers.site_name", Megastructures.name(mega)));
        TextFit.center(g, font, how, left + 10, by + 4, W - 20, done ? 0xFF7FD69A : 0xFFDDE6EE, 0.7F, hover);
        if (!done) {
            hover.tip(left + 8, by, W - 16, 16, List.of(Component.translatable("mega.pioneers.gui.cargo.tooltip")));
        }
        super.render(g, mx, my, pt);
        hover.render(g, font);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}

package fr.pioneers.society.economy.client;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.TextFit;
import fr.pioneers.client.gui.TooltipLayer;
import fr.pioneers.society.rp.client.RpClient;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * 1.9 (MAJ RP, bloc 3) : configuration d'un etal ou d'un comptoir par son proprietaire (ou un operateur) : grille des annonces (verrouillees
 * au-dela du nombre permis), inventaire du joueur (un clic fixe l'objet de l'annonce choisie depuis cet exemple, rien n'est pris), prix du
 * lot et quantite par lot, valeur de reference, bornes et pourcentage (calcules par le serveur), bouton du stock (vrai coffre). Le serveur
 * verifie et borne tout ce qui est propose ici.
 * <p>
 * 1.9.1 (retours de Guillaume : textes qui se superposaient) : le titre laisse la place a la taxe (mesuree d'abord), les aides de la
 * colonne de droite se replient sur toute la hauteur du cadre, tout texte passe par {@link TextFit} (resserre, puis coupe avec
 * l'infobulle).
 */
public class StallEditScreen extends Screen {

    private static final int W = 336, H = 236, CELL = 18, GRID_Y = 40, INV_Y = 128, RIGHT = 180, RW = 148;
    private static final int TEXT = 0xFFE8E8E8, DIM = 0xFF8A96A2, ACCENT = 0xFF3FD0A0, WARN = 0xFFE3A23B, BAD = 0xFFE3735F, GOOD = 0xFF6FCF7F;

    private StallClient.View view;
    private int selected = -1, left, top;
    private EditBox price, lot;
    private final TextFit.Hover hover = new TextFit.Hover();

    public StallEditScreen(StallClient.View view) {
        super(view.title());
        this.view = view;
    }

    public long pos() {
        return view.pos();
    }

    public void read(StallClient.View v) {
        view = v;
        fill();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        String p = price == null ? "" : price.getValue(), l = lot == null ? "" : lot.getValue();
        price = addRenderableWidget(new EditBox(font, left + RIGHT, top + 84, 66, 14, Component.translatable("stall.pioneers.edit.price")));
        lot = addRenderableWidget(new EditBox(font, left + RIGHT + 82, top + 84, 40, 14, Component.translatable("stall.pioneers.edit.lot")));
        price.setFilter(s -> s.matches("\\d{0,9}"));
        lot.setFilter(s -> s.matches("\\d{0,2}"));
        price.setValue(p);
        lot.setValue(l);
        boxes();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private StallClient.Entry entry() {
        return selected >= 0 && selected < view.entries().size() ? view.entries().get(selected) : null;
    }

    /** Remplit les champs avec les valeurs du serveur pour l'annonce choisie. */
    private void fill() {
        StallClient.Entry e = entry();
        if (price != null && e != null && !e.empty()) {
            price.setValue(Long.toString(e.price()));
            lot.setValue(Integer.toString(e.lot()));
        }
        boxes();
    }

    private void boxes() {
        StallClient.Entry e = entry();
        boolean show = e != null && !e.empty() && !e.locked();
        if (price != null) {
            price.setVisible(show);
            lot.setVisible(show);
        }
    }

    private void select(int i) {
        selected = i;
        fill();
    }

    private void send(String op, int i, CompoundTag extra) {
        CompoundTag t = extra == null ? new CompoundTag() : extra;
        t.putLong("pos", view.pos());
        t.putString("op", op);
        t.putInt("i", i);
        RpNet.toServer(RpActions.STALL_EDIT, t);
    }

    private void submit() {
        StallClient.Entry e = entry();
        if (e == null || e.empty() || e.locked()) {
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putLong("price", price.getValue().isEmpty() ? 0 : Long.parseLong(price.getValue()));
        t.putInt("lot", lot.getValue().isEmpty() ? e.lot() : Integer.parseInt(lot.getValue()));
        send("set", e.index(), t);
    }

    // ------------------------------------------------------------------ dessin
    private int cellX(int i) {
        return left + 9 + (i % 9) * CELL;
    }

    private int cellY(int i) {
        return top + GRID_Y + 1 + (i / 9) * CELL;
    }

    /** Case d'inventaire : 3 rangees principales (9..35) puis la barre (0..8). */
    private int invX(int slot) {
        return left + 9 + (slot % 9) * CELL;
    }

    private int invY(int slot) {
        return slot < 9 ? top + INV_Y + 1 + 3 * CELL + 4 : top + INV_Y + 1 + (slot / 9 - 1) * CELL;
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, Component label, boolean on) {
        boolean hover = on && in(mx, my, x, y, w, 16);
        g.fill(x, y, x + w, y + 16, !on ? 0xFF182027 : hover ? 0xFF2F4A3E : 0xFF26392F);
        g.fill(x, y + 15, x + w, y + 16, on ? ACCENT : 0xFF262E36);
        TextFit.center(g, font, label, x + 2, y + 4, w - 4, on ? TEXT : DIM, 0.75F, this.hover);
    }

    private String percentOf(int permille) {
        int whole = permille / 10, frac = permille % 10;
        return frac == 0 ? Integer.toString(whole) : I18n.get("stall.pioneers.decimal", Integer.toString(whole), Integer.toString(frac));
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        this.hover.at(mx, my);
        GuiDraw.panel(g, left, top, W, H);
        //1.9.1 : la taxe est mesuree d'abord (100 px au plus), le titre prend le reste
        int tw = TextFit.right(g, font, Component.translatable("stall.pioneers.edit.tax", percentOf(view.tax())), left + W - 108, top + 7, 100, ACCENT,
              0.8F, this.hover);
        TextFit.fit(g, font, Component.translatable("stall.pioneers.edit.title", view.title()), left + 8, top + 7, W - 16 - tw - 8, TEXT, 1F,
              this.hover);
        TextFit.fit(g, font, Component.translatable("stall.pioneers.edit.slots", view.slots(), view.base(), view.bonus()), left + 8, top + 19,
              W - 16, DIM, 0.7F, this.hover);
        //grille des annonces
        TextFit.fit(g, font, Component.translatable("stall.pioneers.edit.listings"), left + 8, top + 30, RIGHT - 16, TEXT, 0.75F, this.hover);
        StallClient.Entry hoverEntry = null;
        ItemStack hoverStack = ItemStack.EMPTY;
        for (int i = 0; i < view.entries().size(); i++) {
            StallClient.Entry e = view.entries().get(i);
            int x = cellX(i), y = cellY(i);
            if (e.locked()) {
                GuiDraw.locked(g, x, y);
                continue;
            }
            GuiDraw.slot(g, x, y);
            if (!e.empty()) {
                if (e.lots() > 0 && e.priced()) {
                    g.renderItem(e.stack(), x, y);
                } else {
                    GuiDraw.ghost(g, e.stack(), x, y);
                }
                if (e.lot() > 1) {
                    g.renderItemDecorations(font, e.stack().copyWithCount(e.lot()), x, y);
                }
                if (!e.priced()) {
                    g.fill(x + 12, y, x + 16, y + 4, BAD);
                }
            }
            if (i == selected) {
                g.renderOutline(x - 1, y - 1, 18, 18, ACCENT);
            }
            if (in(mx, my, x, y, 16, 16)) {
                hoverEntry = e;
            }
        }
        //inventaire du joueur
        TextFit.fit(g, font, Component.translatable("stall.pioneers.edit.inventory"), left + 8, top + INV_Y - 10, RIGHT - 16, DIM, 0.7F, this.hover);
        Inventory inv = minecraft == null || minecraft.player == null ? null : minecraft.player.getInventory();
        if (inv != null) {
            for (int s = 0; s < 36; s++) {
                int x = invX(s), y = invY(s);
                GuiDraw.slot(g, x, y);
                ItemStack st = inv.getItem(s);
                if (!st.isEmpty()) {
                    g.renderItem(st, x, y);
                    g.renderItemDecorations(font, st, x, y);
                    if (in(mx, my, x, y, 16, 16)) {
                        hoverStack = st;
                        g.fill(x, y, x + 16, y + 16, 0x40FFFFFF);
                    }
                }
            }
        }
        drawDetail(g, mx, my);
        button(g, mx, my, left + RIGHT, top + H - 40, RW, Component.translatable("stall.pioneers.edit.stock"), true);
        if (!view.message().getString().isEmpty()) {
            TextFit.center(g, font, view.message(), left + 8, top + H - 13, W - 16, WARN, 0.75F, this.hover);
        }
        super.render(g, mx, my, pt);
        if (hoverEntry != null && !hoverEntry.empty()) {
            TooltipLayer.item(font, hoverEntry.stack(), mx, my);
        } else if (hoverEntry != null && hoverEntry.locked()) {
            TooltipLayer.text(font, Component.translatable("stall.pioneers.locked"), mx, my);
        } else if (!hoverStack.isEmpty()) {
            TooltipLayer.item(font, hoverStack, mx, my);
        } else {
            this.hover.render(g, font);
        }
    }

    /** Colonne de droite : l'annonce choisie. */
    private void drawDetail(GuiGraphics g, int mx, int my) {
        int x = left + RIGHT;
        StallClient.Entry e = entry();
        GuiDraw.screen(g, x - 2, top + 28, RW + 4, 144);
        //1.9.1 : les aides se replient sur toute la hauteur du cadre (17 lignes au plus)
        int helpLines = (144 - 8) / TextFit.step(0.75F);
        if (e == null) {
            TextFit.wrap(g, font, Component.translatable("stall.pioneers.edit.pick"), x + 2, top + 34, RW - 4, DIM, 0.75F, helpLines, this.hover);
            return;
        }
        if (e.locked()) {
            TextFit.wrap(g, font, Component.translatable("stall.pioneers.edit.locked_help"), x + 2, top + 34, RW - 4, DIM, 0.75F, helpLines,
                  this.hover);
            return;
        }
        if (e.empty()) {
            TextFit.wrap(g, font, Component.translatable("stall.pioneers.edit.empty_help"), x + 2, top + 34, RW - 4, DIM, 0.75F, helpLines,
                  this.hover);
            return;
        }
        g.renderItem(e.stack(), x + 2, top + 32);
        TextFit.fit(g, font, e.stack().getHoverName(), x + 22, top + 36, RW - 24, TEXT, 0.8F, this.hover);
        TextFit.fit(g, font, Component.translatable("stall.pioneers.edit.value", StallClient.centi(e.value()), e.lot()), x + 2, top + 52, RW - 4,
              DIM, 0.7F, this.hover);
        Component bounds = e.min() >= 0 ? Component.translatable("stall.pioneers.edit.bounds", Long.toString(e.min()), Long.toString(e.max()),
              view.minPct(), view.maxPct()) : Component.translatable("stall.pioneers.edit.no_bounds");
        TextFit.fit(g, font, bounds, x + 2, top + 62, RW - 4, DIM, 0.7F, this.hover);
        TextFit.fit(g, font, Component.translatable("stall.pioneers.edit.price"), x, top + 75, 76, TEXT, 0.7F, this.hover);
        TextFit.fit(g, font, Component.translatable("stall.pioneers.edit.lot"), x + 82, top + 75, 66, TEXT, 0.7F, this.hover);
        TextFit.fit(g, font, Component.translatable("stall.pioneers.edit.pct", StallClient.percent(e.pct())), x + 2, top + 104, RW - 4,
              StallClient.percentColor(e.pct()), 0.75F, this.hover);
        TextFit.fit(g, font, Component.translatable("stall.pioneers.edit.stock_line", e.stock(), e.lots()), x + 2, top + 115, RW - 4, DIM, 0.7F,
              this.hover);
        Component state = !e.priced() ? Component.translatable("stall.pioneers.state.bad_price")
              : e.lots() <= 0 ? Component.translatable("stall.pioneers.state.no_stock") : Component.translatable("stall.pioneers.state.active");
        TextFit.fit(g, font, state, x + 2, top + 126, RW - 4, !e.priced() ? BAD : e.lots() <= 0 ? WARN : GOOD, 0.7F, this.hover);
        button(g, mx, my, x, top + 146, 70, Component.translatable("stall.pioneers.edit.save"), true);
        button(g, mx, my, x + 76, top + 146, 72, Component.translatable("stall.pioneers.edit.clear"), true);
    }

    // ------------------------------------------------------------------ actions
    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        int mx = (int) mxd, my = (int) myd;
        for (int i = 0; i < view.entries().size(); i++) {
            if (in(mx, my, cellX(i), cellY(i), 16, 16)) {
                select(i);
                RpClient.play(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.6F);
                return true;
            }
        }
        for (int s = 0; s < 36; s++) {
            if (in(mx, my, invX(s), invY(s), 16, 16)) {
                pickFrom(s);
                return true;
            }
        }
        StallClient.Entry e = entry();
        int x = left + RIGHT;
        if (e != null && !e.empty() && !e.locked()) {
            if (in(mx, my, x, top + 146, 70, 16)) {
                submit();
                return true;
            }
            if (in(mx, my, x + 76, top + 146, 72, 16)) {
                send("clear", e.index(), null);
                return true;
            }
        }
        if (in(mx, my, x, top + H - 40, RW, 16)) {
            send("stock", -1, null);
            return true;
        }
        return super.mouseClicked(mxd, myd, button);
    }

    /** Clic sur l'inventaire : l'objet devient celui de l'annonce choisie (sinon de la premiere annonce libre). */
    private void pickFrom(int slot) {
        if (minecraft == null || minecraft.player == null || minecraft.player.getInventory().getItem(slot).isEmpty()) {
            return;
        }
        StallClient.Entry e = entry();
        if (e == null || e.locked()) {
            for (int i = 0; i < view.entries().size(); i++) {
                StallClient.Entry c = view.entries().get(i);
                if (!c.locked() && c.empty()) {
                    selected = i;
                    e = c;
                    break;
                }
            }
        }
        if (e == null || e.locked()) {
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putInt("slot", slot);
        send("item", e.index(), t);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && (price.isFocused() || lot.isFocused())) {
            submit();
            return true;
        }
        if ((price.isFocused() || lot.isFocused()) && key != GLFW.GLFW_KEY_ESCAPE) {
            //le champ garde toutes les touches (la touche d'inventaire ne ferme pas l'ecran pendant la saisie)
            super.keyPressed(key, scan, mods);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}

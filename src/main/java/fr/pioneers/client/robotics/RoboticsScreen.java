package fr.pioneers.client.robotics;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.robotics.Endpoint;
import fr.pioneers.robotics.LogisticResource;
import fr.pioneers.robotics.RoboticOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base des menus de la robotique (ordres, edition, zones) : panneau facon Mekanism, et petits controles dessines a chaque image
 * (pastilles, boutons) dont les zones cliquables sont retenues pour le clic suivant.
 */
public abstract class RoboticsScreen extends Screen {

    protected static final int TEXT = 0xFF404040, TEXT_DIM = 0xFF707070, SCREEN_TEXT = 0xFF8CF5C6, ACCENT = 0xFF3FD0A0;

    @Nullable
    protected final Screen parent;
    protected int left, top, panelWidth, panelHeight;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> pending = new ArrayList<>();
    @Nullable
    private List<Component> tooltip;
    /** Panneau de fond (sinon plein ecran, la carte dessine tout). */
    protected boolean drawPanel = true;
    /** Style sombre « console » (planificateur, editeurs) au lieu du panneau gris de Mekanism. */
    protected boolean dark = true;
    /** Zone cliquable courante (liste qui defile) : les controles hors de ce cadre ne sont ni cliquables ni survoles. */
    private int clipX0 = Integer.MIN_VALUE, clipY0 = Integer.MIN_VALUE, clipX1 = Integer.MAX_VALUE, clipY1 = Integer.MAX_VALUE;
    /** 0.20.36 : cadres englobants (un cadre dans un cadre : liste qui defile dans une page qui defile). */
    private final ArrayDeque<int[]> clips = new ArrayDeque<>();

    protected static final int D_BG = 0xFF0B1016, D_BAR = 0xFF1B2128, D_PANEL = 0xFF141A20, D_LINE = 0xFF2C3640, D_TEXT = 0xFFE0F5EA, D_DIM = 0xFF8FA3A0,
          D_FAINT = 0xFF5D6E6C;

    private record Hit(int x0, int y0, int x1, int y1, Runnable action, boolean right) {}

    //0.20.1 : saisie au clavier d'une valeur de reglage (clic sur le nombre d'un stepper)
    @Nullable
    private String editKey;
    private String editText = "";
    @Nullable
    private IntConsumer editSet;
    private int editMin, editMax, editX0, editY0, editX1, editY1;
    private boolean editSeen;

    protected RoboticsScreen(Component title, @Nullable Screen parent, int width, int height) {
        super(title);
        this.parent = parent;
        this.panelWidth = width;
        this.panelHeight = height;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(panelWidth, width - 8);
        panelHeight = Math.min(panelHeight, height - 8);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        super.init();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (drawPanel && dark) {
            renderBackground(graphics);
            darkPanel(graphics, left, top, panelWidth, panelHeight);
            graphics.drawString(font, title, left + 8, top + 7, D_TEXT, false);
        } else if (drawPanel) {
            renderBackground(graphics);
            GuiDraw.panel(graphics, left, top, panelWidth, panelHeight);
            graphics.drawString(font, title, left + 8, top + 7, TEXT, false);
        }
        pending.clear();
        tooltip = null;
        editSeen = false;
        drawContents(graphics, mouseX, mouseY, partialTick);
        //0.20.36 : filet de securite : aucun cadre ne survit a l'image (il couperait le dessin du monde apres la fermeture)
        while (!clips.isEmpty()) {
            endClip(graphics);
        }
        if (editKey != null && !editSeen) {
            //Le champ n'est plus dessine (liste qui a defile, ligne retiree) : la saisie est gardee
            commitEdit();
        }
        hits.clear();
        hits.addAll(pending);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (tooltip != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            graphics.pose().popPose();
        }
    }

    protected abstract void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editKey != null) {
            if (mouseX >= editX0 && mouseX < editX1 && mouseY >= editY0 && mouseY < editY1) {
                return true;
            }
            commitEdit();
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        //Le dernier controle dessine est au-dessus : il gagne le clic (menus deroulants, boutons dans une ligne)
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (mouseX >= hit.x0 && mouseX < hit.x1 && mouseY >= hit.y0 && mouseY < hit.y1 && (button == 0 || hit.right && button == 1)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                hit.action.run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (editKey != null) {
            if (key == 257 || key == 335) {
                commitEdit();
            } else if (key == 256) {
                editKey = null;
            } else if (key == 259 && !editText.isEmpty()) {
                editText = editText.substring(0, editText.length() - 1);
            }
            //Toutes les touches sont prises pendant la saisie (E ne ferme pas la fenetre)
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (editKey != null) {
            if (Character.isDigit(c) && editText.length() < 7 || c == '-' && editMin < 0 && editText.isEmpty()) {
                editText += c;
            }
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (editKey != null) {
            commitEdit();
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** Une saisie au clavier est-elle en cours (les ecrans qui ont leurs propres champs la laissent passer d'abord) ? */
    protected boolean editing() {
        return editKey != null;
    }

    /** Valide la saisie en cours (bornee) ; vide = valeur inchangee. */
    protected void commitEdit() {
        String key = editKey;
        editKey = null;
        if (key == null || editSet == null || editText.isEmpty() || editText.equals("-")) {
            return;
        }
        try {
            long v = Long.parseLong(editText);
            editSet.accept((int) Math.max(editMin, Math.min(editMax, v)));
        } catch (NumberFormatException ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Controles
    // ------------------------------------------------------------------

    protected boolean over(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= Math.max(x, clipX0) && mouseX < Math.min(x + w, clipX1) && mouseY >= Math.max(y, clipY0) && mouseY < Math.min(y + h, clipY1);
    }

    protected void click(int x, int y, int w, int h, Runnable action) {
        addHit(x, y, w, h, action, false);
    }

    protected void clickBoth(int x, int y, int w, int h, Runnable action) {
        addHit(x, y, w, h, action, true);
    }

    private void addHit(int x, int y, int w, int h, Runnable action, boolean right) {
        int x0 = Math.max(x, clipX0), y0 = Math.max(y, clipY0), x1 = Math.min(x + w, clipX1), y1 = Math.min(y + h, clipY1);
        if (x0 < x1 && y0 < y1) {
            pending.add(new Hit(x0, y0, x1, y1, action, right));
        }
    }

    /** Limite les clics et survols a un cadre (et coupe le dessin) ; {@link #endClip} le retire. */
    protected void beginClip(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        clips.push(new int[]{clipX0, clipY0, clipX1, clipY1});
        clipX0 = x0;
        clipY0 = y0;
        clipX1 = x1;
        clipY1 = y1;
        graphics.enableScissor(x0, y0, x1, y1);
    }

    /**
     * Retire le dernier cadre et revient au cadre englobant. 0.20.36 : chaque {@link #beginClip} a son {@code endClip} ; un cadre laisse ouvert
     * restait actif apres l'ecran (le monde n'etait plus redessine que dans ce rectangle : l'interface semblait rester a l'ecran).
     */
    protected void endClip(GuiGraphics graphics) {
        graphics.disableScissor();
        int[] prev = clips.isEmpty() ? new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE} : clips.pop();
        clipX0 = prev[0];
        clipY0 = prev[1];
        clipX1 = prev[2];
        clipY1 = prev[3];
    }

    protected void tooltip(int mouseX, int mouseY, int x, int y, int w, int h, Component... lines) {
        if (over(mouseX, mouseY, x, y, w, h)) {
            tooltip = List.of(lines);
        }
    }

    protected void tooltip(List<Component> lines) {
        tooltip = lines;
    }

    /** Pastille a choisir (selectionnee : allumee). */
    protected void chip(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, Component label, boolean selected, int color,
          Runnable action) {
        boolean hover = over(mouseX, mouseY, x, y, w, h);
        if (dark) {
            graphics.fill(x, y, x + w, y + h, selected ? 0xFF000000 | color : hover ? 0xFF2E3A46 : 0xFF222B34);
            graphics.renderOutline(x, y, w, h, selected ? 0x90FFFFFF : hover ? 0xFF4A5A68 : 0xFF2F3B46);
            GuiDraw.center(graphics, font, label, x + w / 2F, y + (h - 6) / 2F, 0.75F, selected ? 0xFFFFFFFF : hover ? D_TEXT : 0xFFB8C8C4, w - 4);
            click(x, y, w, h, action);
            return;
        }
        graphics.fill(x, y, x + w, y + h, selected ? 0xFF000000 | color : hover ? 0xFF9A9A9A : 0xFF8B8B8B);
        graphics.fill(x, y, x + w, y + 1, selected ? 0x60FFFFFF : 0xFFFFFFFF);
        graphics.fill(x, y, x + 1, y + h, selected ? 0x60FFFFFF : 0xFFFFFFFF);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF373737);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF373737);
        GuiDraw.center(graphics, font, label, x + w / 2F, y + (h - 6) / 2F, 0.75F, selected ? 0xFFFFFFFF : 0xFF2A2A2A, w - 4);
        click(x, y, w, h, action);
    }

    /** Bouton d'action (gris, a relief). */
    protected void button(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, Component label, boolean enabled, Runnable action) {
        boolean hover = enabled && over(mouseX, mouseY, x, y, w, h);
        if (dark) {
            graphics.fill(x, y, x + w, y + h, !enabled ? 0xFF1A2027 : hover ? 0xFF3A4C5C : 0xFF2A3540);
            graphics.renderOutline(x, y, w, h, !enabled ? 0xFF242C34 : hover ? 0xFF6F8EA6 : 0xFF3C4A57);
            GuiDraw.center(graphics, font, label, x + w / 2F, y + (h - 7) / 2F, 0.8F, enabled ? D_TEXT : 0xFF59666A, w - 4);
            if (enabled) {
                click(x, y, w, h, action);
            }
            return;
        }
        graphics.fill(x, y, x + w, y + h, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, !enabled ? 0xFF6E6E6E : hover ? 0xFF7FA8D8 : 0xFFA8A8A8);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 2, 0x70FFFFFF);
        graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x50000000);
        GuiDraw.center(graphics, font, label, x + w / 2F, y + (h - 7) / 2F, 0.8F, enabled ? 0xFFFFFFFF : 0xFFA0A0A0, w - 4);
        if (enabled) {
            click(x, y, w, h, action);
        }
    }

    /** Bouton principal (valider) : couleur d'accent. */
    protected void primary(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, Component label, boolean enabled, Runnable action) {
        boolean hover = enabled && over(mouseX, mouseY, x, y, w, h);
        graphics.fill(x, y, x + w, y + h, !enabled ? 0xFF1F2A28 : hover ? 0xFF3CC596 : 0xFF2E9E78);
        graphics.renderOutline(x, y, w, h, !enabled ? 0xFF2A3634 : 0xFF7FE6C0);
        GuiDraw.center(graphics, font, label, x + w / 2F, y + (h - 7) / 2F, 0.8F, enabled ? 0xFFFFFFFF : 0xFF5E706C, w - 4);
        if (enabled) {
            click(x, y, w, h, action);
        }
    }

    /** Bouton a icone (objet) : pastille carree, allumee si choisie. */
    protected void iconChip(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, ItemStack icon, boolean selected, int color,
          Runnable action) {
        boolean hover = over(mouseX, mouseY, x, y, w, h);
        graphics.fill(x, y, x + w, y + h, selected ? 0xFF000000 | GuiDraw.darken(color, 0.55F) : hover ? 0xFF2E3A46 : 0xFF1E262E);
        graphics.renderOutline(x, y, w, h, selected ? 0xFF000000 | color : hover ? 0xFF4A5A68 : 0xFF2C3740);
        float scale = Math.min(1, (Math.min(w, h) - 2) / 16F);
        graphics.pose().pushPose();
        graphics.pose().translate(x + (w - 16 * scale) / 2F, y + (h - 16 * scale) / 2F, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.renderItem(icon, 0, 0);
        graphics.pose().popPose();
        click(x, y, w, h, action);
    }

    /** Interrupteur (case a cocher) avec son libelle. Renvoie la largeur occupee. */
    protected int toggle(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, Component label, boolean on, Runnable action) {
        boolean hover = over(mouseX, mouseY, x, y - 2, w, 12);
        graphics.fill(x, y, x + 16, y + 8, on ? 0xFF2E9E78 : 0xFF2A333C);
        graphics.renderOutline(x, y, 16, 8, hover ? 0xFF7FE6C0 : 0xFF3C4A57);
        graphics.fill(on ? x + 9 : x + 1, y + 1, on ? x + 15 : x + 7, y + 7, on ? 0xFFFFFFFF : 0xFF8FA3A0);
        GuiDraw.text(graphics, font, label, x + 20, y, 0.7F, dark ? D_TEXT : TEXT, w - 20);
        click(x, y - 2, w, 12, action);
        return w;
    }

    /** Reglage numerique : - valeur + (Maj : pas de 8 ou 10). */
    protected void stepper(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int value, int min, int max, int bigStep,
          IntConsumer set) {
        stepper(graphics, mouseX, mouseY, x, y, w, value, min, max, bigStep, "", set);
    }

    /**
     * Reglage numerique : - valeur + (Maj : grand pas). 0.20.1 : un clic sur le nombre permet de le taper au clavier (Entree ou clic
     * ailleurs : valide ; Echap : annule) ; {@code unit} suit la valeur (« 30 s »).
     */
    protected void stepper(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int value, int min, int max, int bigStep, String unit,
          IntConsumer set) {
        int step = hasShiftDown() ? bigStep : 1;
        String key = x + ":" + y;
        boolean editingThis = key.equals(editKey);
        button(graphics, mouseX, mouseY, x, y, 11, 12, Component.literal("-"), !editingThis && value > min, () -> set.accept(Math.max(min, value - step)));
        int fx = x + 12, fw = w - 24;
        boolean hover = over(mouseX, mouseY, fx, y, fw, 12);
        if (editingThis) {
            editSeen = true;
            editSet = set;
            editMin = min;
            editMax = max;
            editX0 = Math.max(fx, clipX0);
            editY0 = Math.max(y, clipY0);
            editX1 = Math.min(fx + fw, clipX1);
            editY1 = Math.min(y + 12, clipY1);
            graphics.fill(fx, y, fx + fw, y + 12, 0xFF06231A);
            graphics.renderOutline(fx, y, fw, 12, ACCENT);
            boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
            GuiDraw.center(graphics, font, Component.literal(editText + (caret ? "_" : " ") + unit), fx + fw / 2F, y + 3, 0.75F, 0xFFFFFFFF, fw - 4);
        } else {
            graphics.fill(fx, y, fx + fw, y + 12, dark ? (hover ? 0xFF16222B : 0xFF0E1419) : 0xFF13221B);
            if (hover) {
                graphics.renderOutline(fx, y, fw, 12, 0xFF4A6A78);
            }
            GuiDraw.center(graphics, font, Component.literal(value + unit), fx + fw / 2F, y + 3, 0.75F, dark ? D_TEXT : SCREEN_TEXT, fw - 4);
            click(fx, y, fw, 12, () -> {
                commitEdit();
                editKey = key;
                editText = Integer.toString(value);
                editSet = set;
                editMin = min;
                editMax = max;
            });
            if (hover) {
                tooltip(List.of(Component.translatable("gui.pioneers.type_value").withStyle(ChatFormatting.GRAY)));
            }
        }
        button(graphics, mouseX, mouseY, x + w - 11, y, 11, 12, Component.literal("+"), !editingThis && value < max,
              () -> set.accept(Math.min(max, value + step)));
    }

    /** Titre de section (style sombre) : libelle et filet. Renvoie le y suivant. */
    protected int section(GuiGraphics graphics, int x, int y, int w, Component label) {
        GuiDraw.text(graphics, font, label.copy().withStyle(ChatFormatting.BOLD), x, y, 0.62F, 0xFF7FE6C0, w);
        graphics.fill(x, y + 7, x + w, y + 8, D_LINE);
        return y + 11;
    }

    /** Ligne de legende : pastille de couleur et texte (sur plusieurs lignes au besoin). Renvoie la hauteur occupee. */
    protected int legend(GuiGraphics graphics, int x, int y, int w, int swatch, Component text, int color) {
        graphics.fill(x + 1, y + 2, x + 6, y + 7, swatch);
        return Math.max(10, wrapped(graphics, text, x + 10, y + 1, 0.62F, color, w - 10, 4) + 2);
    }

    /** Hauteur d'un texte sur plusieurs lignes, sans le dessiner. */
    protected int wrappedHeight(Component text, float scale, int w, int maxLines) {
        int count = Math.min(font.split(text, (int) (w / scale)).size(), maxLines);
        return count * (Math.round(9 * scale) + 1);
    }

    /** Texte sur plusieurs lignes (coupe aux mots). Renvoie la hauteur occupee. */
    protected int wrapped(GuiGraphics graphics, Component text, int x, int y, float scale, int color, int w, int maxLines) {
        var lines = font.split(text, (int) (w / scale));
        int count = Math.min(lines.size(), maxLines);
        int lineH = Math.round(9 * scale) + 1;
        for (int i = 0; i < count; i++) {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y + i * lineH, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, lines.get(i), 0, 0, color, false);
            graphics.pose().popPose();
        }
        return count * lineH;
    }

    /** Etat d'un ordre et son explication (pourquoi il attend, quoi faire). Renvoie le y suivant. */
    protected int statusBox(GuiGraphics graphics, int x, int y, int w, RoboticOrder order) {
        int color = 0xFF000000 | OrderTexts.statusColor(order.status);
        int top = y;
        y += 2;
        GuiDraw.text(graphics, font, OrderTexts.status(order).withStyle(ChatFormatting.BOLD), x + 6, y, 0.65F, color, w - 8);
        y += 9;
        if (order.status != RoboticOrder.Status.ACTIVE && order.status != RoboticOrder.Status.PAUSED) {
            y += wrapped(graphics, OrderTexts.why(order), x + 6, y, 0.58F, dark ? D_DIM : TEXT_DIM, w - 8, 4) + 1;
        } else if (order.activeUnits > 0) {
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.orders.units", order.activeUnits, order.maxUnits), x + 6, y, 0.58F,
                  dark ? D_DIM : TEXT_DIM, w - 8);
            y += 8;
        }
        graphics.fill(x, top, x + 2, y, color);
        return y + 3;
    }

    /** Infobulle d'une ressource : un tag liste ses objets (les premiers), pour qu'on voie tout de suite ce qu'il couvre. */
    protected static List<Component> resourceLines(LogisticResource res) {
        List<Component> lines = new ArrayList<>();
        lines.add(res.name());
        if (res.tagKey() != null) {
            var items = BuiltInRegistries.ITEM.getTag(res.tagKey());
            int size = items.map(set -> set.size()).orElse(0);
            lines.add(Component.translatable("robotics.pioneers.picker.tag_count", size).withStyle(ChatFormatting.AQUA));
            if (items.isPresent()) {
                for (int i = 0; i < Math.min(8, size); i++) {
                    lines.add(Component.literal(" • ").append(new ItemStack(items.get().get(i).value()).getHoverName()).withStyle(ChatFormatting.GRAY));
                }
                if (size > 8) {
                    lines.add(Component.translatable("robotics.pioneers.picker.tag_more", size - 8).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
        return lines;
    }

    /** Panneau sombre a bord lumineux. */
    protected static void darkPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, D_PANEL);
        graphics.renderOutline(x, y, w, h, 0xFF2E3A44);
        graphics.fill(x, y, x + w, y + 1, 0xFF3FD0A0);
    }

    /** Case en creux du style sombre (x, y : coin de l'objet). */
    protected static void darkSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF2C3640);
        graphics.fill(x, y, x + 16, y + 16, 0xFF0E1419);
    }

    // ------------------------------------------------------------------
    // Textes
    // ------------------------------------------------------------------

    protected static Component side(Direction side) {
        return Component.translatable("robotics.pioneers.side." + side.getName().toLowerCase(Locale.ROOT));
    }

    /** « Coffre (12, 64, -3) · face du dessus » */
    protected static Component describe(@Nullable Endpoint endpoint) {
        if (endpoint == null) {
            return Component.translatable("robotics.pioneers.order.network");
        }
        if (endpoint.side() == null) {
            return Component.translatable("robotics.pioneers.order.endpoint_auto", blockName(endpoint.pos()), endpoint.pos().toShortString());
        }
        return Component.translatable("robotics.pioneers.order.endpoint", blockName(endpoint.pos()), endpoint.pos().toShortString(),
              side(endpoint.side()));
    }

    protected static Component blockName(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.isLoaded(pos)) {
            return Component.translatable("robotics.pioneers.order.unloaded");
        }
        return minecraft.level.getBlockState(pos).getBlock().getName();
    }
}

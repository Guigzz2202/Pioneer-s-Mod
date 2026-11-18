package fr.pioneers.society.rp.client;

import fr.pioneers.core.profile.PioneerProfile;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 1.9 (MAJ RP) : page de garde du Carnet a la premiere connexion dans un monde : 1 le nom, 2 la vocation, 3 la lecture obligatoire (le
 * bouton « Oui » ne s'active qu'en bas de la page). 1.12.0 (lot E4) : l'etape de l'allure est retiree (barbe et coiffure : skin du joueur ;
 * habillage : garde-robe cosmetique de l'inventaire). Echap ne ferme pas ; une deconnexion reprend a l'etape en cours (le
 * serveur la garde). Le serveur revalide tout a l'envoi.
 */
public class InitiationScreen extends PioneerScreen {

    public static final int COVER_LINES = 6;
    /** Derniere etape (la lecture). */
    public static final int LAST = 2;
    private int step;
    private String vocation = "";
    private EditBox nameBox;
    private boolean readToEnd;

    public InitiationScreen(int step) {
        super(Component.translatable("pioneer.pioneers.cover.title"));
        this.step = Math.max(0, Math.min(LAST, step));
        PioneerProfile p = RpClient.profile();
        vocation = p.vocation;
    }

    @Override
    protected void init() {
        super.init();
        String name = nameBox != null ? nameBox.getValue() : RpClient.profile().name;
        nameBox = new EditBox(font, left + 122, top + 104, 140, 18, Component.translatable("pioneer.pioneers.cover.name"));
        nameBox.setMaxLength(24);
        nameBox.setValue(name == null ? "" : name);
        addRenderableWidget(nameBox);
        nameBox.visible = step == 0;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private void go(int to) {
        step = Math.max(0, Math.min(LAST, to));
        nameBox.visible = step == 0;
        CompoundTag t = new CompoundTag();
        t.putInt("step", step);
        t.putString("name", nameBox.getValue());
        if (!vocation.isEmpty()) {
            t.putString("vocation", vocation);
        }
        RpNet.toServer(RpActions.INIT_STEP, t);
    }

    private boolean stepDone() {
        return switch (step) {
            case 0 -> !nameBox.getValue().isBlank();
            case 1 -> !vocation.isEmpty();
            default -> readToEnd;
        };
    }

    /** 1.9.1 : mise en page (titre, pastilles des etapes, contenu, boutons du bas) ; meme valeurs dans tools/gc/rp/pioneer_ui_preview.py. */
    public static final int STEP_X = 20, STEP_PITCH = 88, STEP_W = 82, STEP_Y = 22, BODY_Y = 38, NAV_Y = PAGE_H - 26;

    @Override
    protected void drawContents(GuiGraphics g, int mx, int my, float pt) {
        fitCentered(g, Component.translatable("pioneer.pioneers.cover.title"), PAGE_W / 2, 10, PAGE_W - 24, INK, 1F);
        String[] steps = {"name", "vocation", "read"};
        for (int i = 0; i < steps.length; i++) {
            MutableComponent c = Component.literal((i + 1) + " ").append(Component.translatable("pioneer.pioneers.cover.step." + steps[i]));
            int ii = i;
            chip(g, mx, my, STEP_X + i * STEP_PITCH, STEP_Y, STEP_W, 12, c, step == i, i <= step, () -> go(ii));
        }
        switch (step) {
            case 0 -> {
                wrap(g, Component.translatable("pioneer.pioneers.cover.name_hint"), 60, 60, 264, TEXT, 1F, 6);
                fitRight(g, Component.translatable("pioneer.pioneers.cover.name"), 118, 109, 56, DIM, 1F);
            }
            case 1 -> {
                //1.9.1 (retours de Guillaume) : onze cartes en 4 x 3 et le panneau de lecture
                vocationPicker(g, mx, my, 9, BODY_Y, PAGE_W - 9, vocation, null, id -> vocation = id);
                if (!vocation.isEmpty()) {
                    String id = vocation;
                    button(g, mx, my, 9, NAV_Y, 110, 16, Component.translatable("pioneer.pioneers.more"), true,
                          () -> minecraft.setScreen(new LoreScreen(this, "trade_" + id)));
                }
            }
            default -> drawCover(g, mx, my);
        }
        boolean last = step == LAST;
        button(g, mx, my, PAGE_W - 200, NAV_Y, 80, 16, Component.translatable("pioneer.pioneers.previous"), step > 0, () -> go(step - 1));
        Component next = last ? Component.translatable(readToEnd ? "pioneer.pioneers.cover.yes" : "pioneer.pioneers.cover.read_to_end")
              : Component.translatable("pioneer.pioneers.next");
        button(g, mx, my, PAGE_W - 110, NAV_Y, 96, 16, next, stepDone(), last ? this::submit : () -> go(step + 1));
    }

    private void drawCover(GuiGraphics g, int mx, int my) {
        int x = 24, y = 42, w = PAGE_W - 48, h = 162;
        int content = 0;
        for (int i = 1; i <= COVER_LINES; i++) {
            content += wrappedHeight(Component.translatable("pioneer.pioneers.cover." + i), w - 8) + 8;
        }
        scrollArea(0, x, y, w, h, content);
        beginClip(g, x, y, w, h);
        int yy = y - scroll[0];
        for (int i = 1; i <= COVER_LINES; i++) {
            Component c = Component.translatable("pioneer.pioneers.cover." + i);
            int color = i == 1 || i == COVER_LINES ? INK : TEXT;
            yy += wrapped(g, c, x + 4, yy, w - 8, color) + 8;
        }
        endClip(g);
        if (scroll[0] >= content - h - 2) {
            readToEnd = true;
        }
        if (!readToEnd) {
            centered(g, Component.literal("▼"), PAGE_W / 2, y + h + 2, DIM);
        }
    }

    private void submit() {
        if (!readToEnd) {
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putString("name", nameBox.getValue());
        t.putString("vocation", vocation);
        t.putBoolean("read", true);
        RpNet.toServer(RpActions.INIT_SUBMIT, t);
    }
}

package fr.pioneers.farm.client.leisure;

import com.mojang.blaze3d.platform.InputConstants;
import fr.pioneers.farm.leisure.GeneratedLeisure.Tune;
import fr.pioneers.farm.leisure.GeneratedLeisure;
import fr.pioneers.farm.leisure.Instrument;
import fr.pioneers.farm.leisure.PiLeisure;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * 1.5 : clavier d'un instrument. Instruments a notes : 15 touches (Do a Do sur deux octaves), touches physiques de la rangee du bas (Z..M sur un
 * clavier QWERTY : 7 notes graves) et de la rangee du milieu (A..K : 8 notes aigues), noms affiches selon le clavier du joueur ; tambour :
 * 8 frappes sur la rangee du milieu. Clic sur une touche : la jouer. Airs connus (ou rythmes) : joues par {@link TunePlayer}, meme ecran ferme.
 * L'ecran ne met pas le jeu en pause.
 */
public class InstrumentScreen extends Screen {

    private static final int[] LOW = {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M};
    private static final int[] HIGH = {GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_J,
                                       GLFW.GLFW_KEY_K};
    private static final int C_FRAME = 0xFF2C3A4A, C_PANEL = 0xFF141C26, C_HEAD = 0xFF0C1218, C_ACCENT = 0xFF55CCFF, C_TITLE = 0xFFFFD27A,
          C_TEXT = 0xFFD8E0EA, C_DIM = 0xFF7F8EA0, C_IVORY = 0xFFF2EBD9, C_IVORY_DARK = 0xFFC9BFA8, C_PAD = 0xFF3A2A22, C_PAD_RIM = 0xFFB89A5E;
    private static final int W = 340, KEY_W = 21, KEY_H = 66, PAD_W = 76, PAD_H = 34, TUNE_W = 104, TUNE_H = 16, LIGHT = 4;

    private final Instrument instrument;
    private final Set<Integer> down = new HashSet<>();
    private int left, top, h, keysY, tunesY;

    private InstrumentScreen(Instrument instrument) {
        super(new net.minecraft.world.item.ItemStack(PiLeisure.INSTRUMENTS.get(instrument).get()).getHoverName());
        this.instrument = instrument;
    }

    public static void open(Instrument instrument) {
        Minecraft.getInstance().setScreen(new InstrumentScreen(instrument));
    }

    private List<Tune> tunes() {
        return instrument.drum ? GeneratedLeisure.RHYTHMS : GeneratedLeisure.MELODIES;
    }

    @Override
    protected void init() {
        int keyboard = instrument.drum ? 2 * PAD_H + 6 : KEY_H;
        int rows = (tunes().size() + 2) / 3;
        h = 30 + keyboard + 26 + 14 + rows * (TUNE_H + 4) + 24 + 18;
        left = (width - W) / 2;
        top = Math.max(4, (height - h) / 2);
        keysY = top + 30;
        tunesY = keysY + keyboard + 26 + 14;
        List<Tune> tunes = tunes();
        for (int i = 0; i < tunes.size(); i++) {
            Tune tune = tunes.get(i);
            int x = left + 10 + (i % 3) * (TUNE_W + 6), y = tunesY + (i / 3) * (TUNE_H + 4);
            addRenderableWidget(Button.builder(Component.translatable("leisure.pioneers.tune." + tune.id()), b -> TunePlayer.start(instrument, tune))
                  .bounds(x, y, TUNE_W, TUNE_H).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("leisure.pioneers.instrument.stop"), b -> TunePlayer.stop())
              .bounds(left + W - 10 - TUNE_W, tunesY + rows * (TUNE_H + 4) + 2, TUNE_W, TUNE_H).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String keyName(int code) {
        return InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString().toUpperCase();
    }

    /** Touche physique d'une note (ou d'une frappe), ou -1. */
    private int keyCode(int key) {
        if (instrument.drum) {
            return HIGH[key];
        }
        return key < LOW.length ? LOW[key] : HIGH[key - LOW.length];
    }

    private int keyOf(int code) {
        for (int key = 0; key < instrument.keys(); key++) {
            if (keyCode(key) == code) {
                return key;
            }
        }
        return -1;
    }

    /** Rectangle de la touche : x, y, largeur, hauteur. */
    private int[] rect(int key) {
        if (instrument.drum) {
            int x0 = left + (W - 4 * PAD_W - 3 * 6) / 2;
            return new int[]{x0 + (key % 4) * (PAD_W + 6), keysY + (key / 4) * (PAD_H + 6), PAD_W, PAD_H};
        }
        int x0 = left + (W - instrument.keys() * KEY_W) / 2;
        return new int[]{x0 + key * KEY_W, keysY, KEY_W - 1, KEY_H};
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left - 1, top - 1, left + W + 1, top + h + 1, C_FRAME);
        g.fill(left, top, left + W, top + h, C_PANEL);
        g.fill(left, top, left + W, top + 22, C_HEAD);
        g.drawString(font, title, left + 10, top + 7, C_TITLE, false);
        Tune playing = TunePlayer.playing();
        if (playing != null) {
            Component now = Component.translatable("leisure.pioneers.instrument.playing", Component.translatable("leisure.pioneers.tune." + playing.id()));
            g.drawString(font, now, left + W - 10 - font.width(now), top + 7, C_ACCENT, false);
        }
        long clock = TunePlayer.clock;
        for (int key = 0; key < instrument.keys(); key++) {
            int[] r = rect(key);
            boolean lit = clock - TunePlayer.HIT[key] < LIGHT && TunePlayer.HIT[key] > 0;
            boolean hover = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
            String label = keyName(keyCode(key));
            if (instrument.drum) {
                g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], lit ? C_ACCENT : C_PAD_RIM);
                g.fill(r[0] + 2, r[1] + 2, r[0] + r[2] - 2, r[1] + r[3] - 2, lit ? 0xFF2E5C78 : hover ? 0xFF4A382E : C_PAD);
                Component name = Component.translatable("leisure.pioneers.drum." + Instrument.PAD_IDS[key]);
                g.pose().pushPose();
                g.pose().translate(r[0] + r[2] / 2F, r[1] + 8, 0);
                g.pose().scale(0.75F, 0.75F, 1);
                g.drawCenteredString(font, name, 0, 0, C_TEXT);
                g.pose().popPose();
                g.drawCenteredString(font, label, r[0] + r[2] / 2, r[1] + 20, C_TITLE);
            } else {
                int body = lit ? C_ACCENT : hover ? 0xFFFFF8E8 : C_IVORY;
                g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], C_IVORY_DARK);
                g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3] - 3, body);
                if (key == 7) {
                    g.fill(r[0] - 1, r[1], r[0], r[1] + r[3], C_ACCENT);          //Do du milieu : debut de la rangee aigue
                }
                Component note = Component.translatable("leisure.pioneers.note." + key % 7);
                g.pose().pushPose();
                g.pose().translate(r[0] + r[2] / 2F, r[1] + 6, 0);
                g.pose().scale(0.75F, 0.75F, 1);
                g.drawString(font, note, -font.width(note) / 2, 0, 0xFF3A3028, false);
                String octave = String.valueOf(instrument.octave + key / 7);
                g.drawString(font, octave, -font.width(octave) / 2, 10, 0xFF8A7A66, false);
                g.pose().popPose();
                g.fill(r[0] + 3, r[1] + r[3] - 18, r[0] + r[2] - 3, r[1] + r[3] - 6, 0xFF2A2420);
                g.drawCenteredString(font, label, r[0] + r[2] / 2, r[1] + r[3] - 16, C_TITLE);
            }
        }
        int y = keysY + (instrument.drum ? 2 * PAD_H + 6 : KEY_H) + 8;
        Component help = instrument.drum
              ? Component.translatable("leisure.pioneers.instrument.play_drum", keyName(HIGH[0]), keyName(HIGH[HIGH.length - 1]))
              : Component.translatable("leisure.pioneers.instrument.play", keyName(LOW[0]), keyName(LOW[LOW.length - 1]), keyName(HIGH[0]),
                    keyName(HIGH[HIGH.length - 1]));
        g.drawCenteredString(font, help, left + W / 2, y, C_DIM);
        g.drawString(font, Component.translatable(instrument.drum ? "leisure.pioneers.instrument.rhythms" : "leisure.pioneers.instrument.tunes"),
              left + 10, tunesY - 12, C_TITLE, false);
        if (playing != null) {
            int bx = left + 10, bw = W - 30 - TUNE_W, by = tunesY + ((tunes().size() + 2) / 3) * (TUNE_H + 4) + 8;
            g.fill(bx, by, bx + bw, by + 4, 0xFF0C1218);
            g.fill(bx, by, bx + Math.round(bw * TunePlayer.progress()), by + 4, C_ACCENT);
        }
        g.drawCenteredString(font, Component.translatable("leisure.pioneers.instrument.hint"), left + W / 2, top + h - 12, C_DIM);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int key = 0; key < instrument.keys(); key++) {
                int[] r = rect(key);
                if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                    TunePlayer.play(instrument, 1 << key);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = keyOf(keyCode);
        if (key >= 0) {
            if (down.add(keyCode)) {
                TunePlayer.play(instrument, 1 << key);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        down.remove(keyCode);
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
}

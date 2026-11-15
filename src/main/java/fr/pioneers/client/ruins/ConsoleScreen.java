package fr.pioneers.client.ruins;

import fr.pioneers.client.gui.TextFit;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import fr.pioneers.client.rp.RpClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * 1.9 (MAJ RP, bloc 1) : clavier de la Console de perimetre (4 chiffres). Ecran sombre, chiffres verts de vieux terminal. Le serveur juge le
 * code ({@code Console}) et renvoie l'etat : Veille, alarme, verrouillage.
 * <p>
 * 1.9.1 (retours de Guillaume) : l'invite et l'etat sont bornes a l'ecran vert ({@link TextFit} : resserres, puis coupes avec l'infobulle),
 * l'anglais « Code refused. Tries left: 2 » en sortait.
 */
public class ConsoleScreen extends Screen {

    private static final int W = 150, H = 196;
    private final long pos;
    private final int tier;
    private String typed = "";
    private String result = "";
    private long standby, locked;
    private int wrong;
    private boolean dead;
    private int left, top;
    private final TextFit.Hover hover = new TextFit.Hover();

    public ConsoleScreen(CompoundTag t) {
        super(Component.literal(""));
        pos = t.getLong("pos");
        tier = t.getInt("tier");
        read(t);
    }

    public void read(CompoundTag t) {
        dead = t.getBoolean("dead");
        standby = t.getLong("standby");
        locked = t.getLong("locked");
        wrong = t.getInt("wrong");
        if (t.contains("result")) {
            result = t.getString("result");
            typed = "";
        }
    }

    public long pos() {
        return pos;
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

    private static final String[] KEYS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK"};

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        hover.at(mx, my);
        int base = tier == 1 ? 0xFF3A2F26 : tier == 2 ? 0xFF3A3F45 : 0xFF2A2F38;
        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, 0xFF101010);
        g.fill(left, top, left + W, top + H, base);
        //ecran
        g.fill(left + 10, top + 10, left + W - 10, top + 52, 0xFF071208);
        int green = 0xFF5BE36A, amber = 0xFFE3A23B, red = 0xFFE35050, blue = 0xFF9FD0FF;
        TextFit.fit(g, font, Component.translatable("ruins.pioneers.console.prompt"), left + 14, top + 14, W - 28, green, 1F, hover);
        String shown = (typed + "____").substring(0, 4);
        g.drawString(font, shown.replace("", " ").trim(), left + 14, top + 28, green, false);
        Component status;
        int color;
        if (dead) {
            status = Component.translatable("ruins.pioneers.console.dead");
            color = red;
        } else if (locked > 0) {
            status = Component.translatable("ruins.pioneers.console.locked", Math.max(1, locked / 20));
            color = red;
        } else if (standby > 0) {
            status = Component.translatable("ruins.pioneers.console.standby_left", Math.max(1, standby / 1200));
            color = blue;
        } else if ("wrong".equals(result)) {
            status = Component.translatable("ruins.pioneers.console.wrong", 3 - wrong);
            color = amber;
        } else {
            status = Component.translatable("ruins.pioneers.console.ready");
            color = green;
        }
        TextFit.fit(g, font, status, left + 14, top + 40, W - 28, color, 1F, hover);
        //clavier
        for (int i = 0; i < KEYS.length; i++) {
            int x = left + 18 + (i % 3) * 40, y = top + 62 + (i / 3) * 32;
            boolean on = mx >= x && mx < x + 34 && my >= y && my < y + 26;
            g.fill(x, y, x + 34, y + 26, on ? 0xFF5A5A5A : 0xFF404040);
            g.fill(x, y + 24, x + 34, y + 26, 0xFF202020);
            g.drawCenteredString(font, KEYS[i], x + 17, y + 9, KEYS[i].equals("OK") ? green : KEYS[i].equals("C") ? amber : 0xFFE0E0E0);
        }
        super.render(g, mx, my, pt);
        hover.render(g, font);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < KEYS.length; i++) {
            int x = left + 18 + (i % 3) * 40, y = top + 62 + (i / 3) * 32;
            if (mx >= x && mx < x + 34 && my >= y && my < y + 26) {
                press(KEYS[i]);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            press(String.valueOf(key - GLFW.GLFW_KEY_0));
            return true;
        }
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) {
            press(String.valueOf(key - GLFW.GLFW_KEY_KP_0));
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            press("C");
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            press("OK");
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void press(String k) {
        if (dead || locked > 0) {
            return;
        }
        RpClient.play(RpSounds.KEYPAD.get(), "OK".equals(k) ? 1.3F : 1.0F + typed.length() * 0.05F);
        switch (k) {
            case "C" -> typed = "";
            case "OK" -> {
                if (typed.length() == 4) {
                    CompoundTag t = new CompoundTag();
                    t.putLong("pos", pos);
                    t.putString("code", typed);
                    RpNet.toServer(RpActions.CONSOLE_CODE, t);
                }
            }
            default -> {
                if (typed.length() < 4) {
                    typed += k;
                }
            }
        }
    }
}

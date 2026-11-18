package fr.pioneers.guide.client;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 29) : la PARTIE du guide (bouton « Partie » de l'en-tete du guide, {@link GuideScreen}) : ce qu'est une partie, l'invitation
 * recue (Accepter / Refuser), les membres (en ligne ou absents), les etapes du Periple partagees, l'invitation d'un joueur connecte par son nom,
 * « Quitter la partie ». Tout vient du serveur ({@code guide/party/PartyService}, vue {@code PARTY_VIEW}) ; l'ecran n'envoie que des intentions.
 * Couleurs et cadre du guide. Maquette fidele : {@code tools/gc/guide/party_preview.py} (memes constantes).
 */
public class PartyScreen extends Screen {

    public static final int W = 300, H = 222, HEAD = 22, PAD = 10, ROW = 12, BTN_H = 14, INVITE_W = 64, LEAVE_W = 110, ACT_W = 70, BAR_H = 5,
          INTRO_Y = 30, INTRO_LINES = 3, CARD_Y = 64, CARD_H = 36, LIST_Y = 106, LIST_ROWS = 4, STEPS_Y = 165, FIELD_Y = 186, FOOT_Y = 205;
    @Nullable
    private static CompoundTag view;
    private static long receivedAt;
    @Nullable
    private final Screen parent;
    private int left, top;
    private EditBox name;
    private final List<Btn> buttons = new ArrayList<>();
    @Nullable
    private String message;
    private long messageAt;

    private record Btn(int x, int y, int w, int h, Runnable action) {
    }

    public PartyScreen(@Nullable Screen parent) {
        super(Component.translatable("party.pioneers.title"));
        this.parent = parent;
    }

    /** Vue du serveur : l'ecran ouvert se met a jour. */
    public static void onServer(CompoundTag t) {
        view = t;
        receivedAt = Util.getMillis();
        if (Minecraft.getInstance().screen instanceof PartyScreen s && t.contains("msg")) {
            s.message = t.getString("msg");
            s.messageAt = Util.getMillis();
        }
    }

    static void request(String op, String player) {
        CompoundTag t = new CompoundTag();
        t.putString("op", op);
        if (player != null) {
            t.putString("name", player);
        }
        RpNet.toServer(RpActions.PARTY_REQUEST, t);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        //1.0.5-beta (lot 16) : selecteur de joueur deja vu (liste des joueurs connus du serveur ; saisie libre gardee)
        name = new fr.pioneers.client.gui.PlayerPicker(font, left + PAD + 2, top + FIELD_Y + 3, W - 2 * PAD - INVITE_W - 10, 10,
              Component.translatable("party.pioneers.field.name"), "party", fr.pioneers.client.gui.PlayerPicker.Style.DARK);
        name.setBordered(false);
        name.setMaxLength(16);
        name.setTextColor(0xFFE8EEF4);
        name.setHint(Component.translatable("party.pioneers.field.name").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(name);
        request("view", null);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        buttons.clear();
        renderBackground(g);
        g.fill(left - 1 + 3, top - 1 + 3, left + W + 1 + 3, top + H + 1 + 3, 0x60000000);
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, GuideScreen.C_FRAME);
        g.fill(left, top, left + W, top + H, GuideScreen.C_PANEL);
        g.fill(left, top, left + W, top + HEAD, GuideScreen.C_HEAD);
        g.fill(left, top + HEAD - 1, left + W, top + HEAD, GuideScreen.C_ACCENT);
        PartyIcon.draw(g, left + 8, top + 6, GuideScreen.C_ACCENT);
        GuiDraw.text(g, font, Component.translatable("party.pioneers.title"), left + 26, top + 7, 1F, GuideScreen.C_TITLE, W - 60);
        button(g, mx, my, left + W - 18, top + 4, 14, 14, Component.literal("x"), true, this::onClose);
        int x = left + PAD, cw = W - 2 * PAD;
        GuiDraw.wrapped(g, font, Component.translatable("party.pioneers.intro"), x, top + INTRO_Y, 0.7F, GuideScreen.C_DIM, cw, INTRO_LINES, 8);
        CompoundTag v = view;
        if (v == null) {
            GuiDraw.text(g, font, Component.translatable("party.pioneers.loading"), x, top + CARD_Y + 4, 0.8F, GuideScreen.C_DIM, cw);
            super.render(g, mx, my, pt);
            return;
        }
        // invitation recue
        int cy = top + CARD_Y;
        g.fill(x, cy, x + cw, cy + CARD_H, GuideScreen.C_SIDE);
        if (v.contains("inviteFrom")) {
            g.fill(x, cy, x + 2, cy + CARD_H, GuideScreen.C_TITLE);
            GuiDraw.text(g, font, Component.translatable("party.pioneers.invite.received", v.getString("inviteFrom"), v.getInt("inviteSize")), x + 7,
                  cy + 5, 0.8F, GuideScreen.C_TEXT, cw - 12);
            button(g, mx, my, x + 7, cy + 18, ACT_W, BTN_H, Component.translatable("party.pioneers.action.accept"), true, () -> request("accept", null));
            button(g, mx, my, x + 11 + ACT_W, cy + 18, ACT_W, BTN_H, Component.translatable("party.pioneers.action.decline"), true,
                  () -> request("decline", null));
        } else {
            GuiDraw.wrapped(g, font, Component.translatable(v.getBoolean("in") ? "party.pioneers.together" : "party.pioneers.alone"), x + 7, cy + 6,
                  0.75F, GuideScreen.C_DIM, cw - 12, 3, 9);
        }
        // membres
        ListTag ms = v.getList("members", Tag.TAG_COMPOUND);
        int ly = top + LIST_Y;
        GuiDraw.text(g, font, Component.translatable("party.pioneers.members", ms.size(), v.getInt("max")), x, ly, 0.8F, GuideScreen.C_CRUMB, cw);
        for (int i = 0; i < LIST_ROWS * 2 && i < ms.size(); i++) {
            CompoundTag m = ms.getCompound(i);
            int col = i / LIST_ROWS, row = i % LIST_ROWS;
            int rx = x + col * (cw / 2), ry = ly + 12 + row * ROW;
            boolean on = m.getBoolean("on");
            g.fill(rx + 2, ry + 3, rx + 6, ry + 7, on ? GuideScreen.C_OK : 0xFF4A5560);
            Component label = m.getBoolean("me") ? Component.translatable("party.pioneers.me", m.getString("n")) : Component.literal(m.getString("n"));
            GuiDraw.text(g, font, label, rx + 10, ry + 1, 0.8F, on ? GuideScreen.C_TEXT : GuideScreen.C_DIM, cw / 2 - 14);
        }
        if (ms.isEmpty()) {
            GuiDraw.text(g, font, Component.translatable("party.pioneers.no_members"), x + 10, ly + 13, 0.75F, GuideScreen.C_DIM, cw - 10);
        }
        // etapes partagees
        int total = Math.max(1, v.getInt("total")), shared = v.getBoolean("in") ? v.getInt("shared") : v.getInt("mine");
        int sy = top + STEPS_Y;
        GuiDraw.text(g, font, Component.translatable(v.getBoolean("in") ? "party.pioneers.steps" : "party.pioneers.steps_mine", shared, total), x, sy,
              0.75F, GuideScreen.C_TEXT, cw);
        g.fill(x, sy + 10, x + cw, sy + 10 + BAR_H, GuideScreen.C_TRACK);
        g.fill(x, sy + 10, x + Math.round(cw * Math.min(1F, shared / (float) total)), sy + 10 + BAR_H, GuideScreen.C_OK);
        // inviter
        int fy = top + FIELD_Y;
        boolean focused = name.isFocused();
        g.fill(x - 1, fy - 1, x + cw - INVITE_W - 4 + 1, fy + BTN_H + 1, focused ? GuideScreen.C_ACCENT : GuideScreen.C_FRAME);
        g.fill(x, fy, x + cw - INVITE_W - 4, fy + BTN_H, 0xFF080C11);
        boolean canInvite = !name.getValue().isBlank() && (!v.getBoolean("in") || ms.size() < v.getInt("max"));
        button(g, mx, my, x + cw - INVITE_W, fy, INVITE_W, BTN_H, Component.translatable("party.pioneers.action.invite"), canInvite, () -> {
            request("invite", name.getValue().trim());
            name.setValue("");
        });
        // pied : quitter, resultat de la derniere demande
        int by = top + FOOT_Y;
        if (v.getBoolean("in")) {
            button(g, mx, my, x + cw - LEAVE_W, by, LEAVE_W, BTN_H, Component.translatable("party.pioneers.action.leave"), true, () -> request("leave", null));
        }
        if (message != null && Util.getMillis() - messageAt < 8000) {
            boolean ok = message.startsWith("ok_");
            GuiDraw.text(g, font, Component.translatable(ok ? "party.pioneers.result." + message : "party.pioneers.error." + message), x, by + 3, 0.7F,
                  ok ? GuideScreen.C_OK : 0xFFE08A6A, cw - (v.getBoolean("in") ? LEAVE_W + 6 : 0));
        }
        super.render(g, mx, my, pt);
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Component label, boolean enabled, Runnable action) {
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, !enabled ? 0xFF161C24 : hover ? GuideScreen.C_SELECT : GuideScreen.C_CHIP);
        g.fill(x, y + h - 1, x + w, y + h, enabled ? GuideScreen.C_ACCENT : GuideScreen.C_FRAME);
        GuiDraw.center(g, font, label, x + w / 2F, y + (h - 6) / 2F, 0.75F, enabled ? GuideScreen.C_TEXT : GuideScreen.C_DIM, w - 4);
        if (enabled) {
            buttons.add(new Btn(x, y, w, h, action));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (name instanceof fr.pioneers.client.gui.PlayerPicker p && p.captures(mx, my)) {
            return p.mouseClicked(mx, my, button);
        }
        if (name != null && name.isFocused() && !name.isMouseOver(mx, my)) {
            name.setFocused(false);
            setFocused(null);
        }
        if (button == 0) {
            for (Btn b : List.copyOf(buttons)) {
                if (mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h()) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F));
                    b.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (name instanceof fr.pioneers.client.gui.PlayerPicker p && p.captures(mx, my)) {
            return p.mouseScrolled(mx, my, delta);
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (name instanceof fr.pioneers.client.gui.PlayerPicker p && p.takesEnter()) {
            return p.keyPressed(key, scan, mods);
        }
        if (name != null && name.isFocused() && (key == 257 || key == 335) && !name.getValue().isBlank()) {
            request("invite", name.getValue().trim());
            name.setValue("");
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** Pictogramme de la partie (deux silhouettes), dessine au pixel. */
    static final class PartyIcon {

        private PartyIcon() {
        }

        static void draw(GuiGraphics g, int x, int y, int color) {
            int back = 0xFF2E6E9E;
            g.fill(x + 7, y + 1, x + 11, y + 5, back);
            g.fill(x + 6, y + 6, x + 12, y + 10, back);
            g.fill(x + 2, y + 2, x + 6, y + 6, color);
            g.fill(x + 1, y + 7, x + 7, y + 11, color);
        }
    }
}

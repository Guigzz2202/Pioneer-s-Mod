package fr.pioneers.client.village;

import fr.pioneers.Pioneers;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.rp.RpClient;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9.1 (retours de Guillaume) : l'emissaire Colporteur (Maj + clic droit sur un Colporteur). Pour chaque peuple fache : sa reputation,
 * le prix de l'offrande (amende + commission), ce qu'elle rachetera, et Envoyer ; ou ce qui l'empeche (emissaire deja en route, envoi du jour
 * fait, jetons). Les Colporteurs ne portent rien pour qui ils tiennent pour Hostile, et ne se reconcilient pas avec eux-memes par un des
 * leurs. Tout vient du serveur. Coordonnees alignees avec la maquette {@code tools/gc/rp/village_ui_preview.py}.
 */
public class EmissaryScreen extends Screen {

    static final int W = 264, H = 206, LIST_Y = 52, ROW_H = 30, FOOT_H = 22;
    private static final int TEXT = 0xFFE8E8E8, DIM = 0xFF8A96A2, ACCENT = 0xFF3FD0A0, GOOD = 0xFF6FCF7F, BAD = 0xFFE3735F, WARN = 0xFFE3A23B;

    record Row(String id, String icon, Component name, int rep, String tier, long cost, int gain, long arrive, String refusal) {
    }

    private int entity, days, sent, max;
    private long balance;
    private boolean refused;
    private Component name = Component.empty();
    @Nullable
    private Component voice, message;
    private final List<Row> rows = new ArrayList<>();
    private int left, top, scroll;

    public EmissaryScreen(CompoundTag t) {
        super(Component.translatable("village.pioneers.emissary.title"));
        read(t);
    }

    public int entity() {
        return entity;
    }

    public void read(CompoundTag t) {
        entity = t.getInt("entity");
        name = Component.Serializer.fromJson(t.getString("name"));
        refused = t.getBoolean("refused");
        balance = t.getLong("balance");
        days = t.getInt("days");
        sent = t.getInt("sent");
        max = t.getInt("max");
        voice = t.contains("voice") ? Component.translatable(t.getString("voice")) : null;
        message = t.contains("msg") ? Component.Serializer.fromJson(t.getString("msg")) : null;
        rows.clear();
        for (Tag x : t.getList("peoples", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            rows.add(new Row(c.getString("id"), c.getString("icon"), Component.Serializer.fromJson(c.getString("name")), c.getInt("rep"), c.getString("tier"),
                  c.getLong("cost"), c.getInt("gain"), c.contains("arrive") ? c.getLong("arrive") : -1, c.getString("refusal")));
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
        GuiDraw.panel(g, left, top, W, H);
        g.blit(Pioneers.rl("textures/gui/village/people_peddlers.png"), left + 8, top + 6, 0, 0, 16, 16, 16, 16);
        GuiDraw.text(g, font, Component.translatable("village.pioneers.emissary.header", name), left + 28, top + 10, 1, TEXT, W - 36);
        if (voice != null) {
            GuiDraw.wrapped(g, font, Component.translatable("village.pioneers.dialogue.quote", voice), left + 8, top + 24, 0.7F, ACCENT, W - 16, 1, 8);
        }
        GuiDraw.wrapped(g, font, Component.translatable(refused ? "village.pioneers.emissary.refused" : "village.pioneers.emissary.info", days, sent, max),
              left + 8, top + 34, 0.65F, refused ? BAD : DIM, W - 16, 2, 7);
        int listTop = top + LIST_Y, listBottom = top + H - FOOT_H;
        int viewH = listBottom - listTop;
        int contentH = Math.max(1, rows.size()) * ROW_H;
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - viewH));
        g.enableScissor(left + 2, listTop, left + W - 2, listBottom);
        if (rows.isEmpty()) {
            GuiDraw.wrapped(g, font, Component.translatable("village.pioneers.emissary.none"), left + 10, listTop + 6, 0.75F, DIM, W - 20, 2, 9);
        }
        for (int i = 0; i < rows.size(); i++) {
            row(g, mx, my, rows.get(i), left + 8, listTop - scroll + i * ROW_H, W - 22, listTop, listBottom);
        }
        g.disableScissor();
        if (contentH > viewH) {
            int track = viewH - 4, knob = Math.max(10, track * viewH / contentH);
            int p = (int) Math.round((track - knob) * scroll / (double) (contentH - viewH));
            g.fill(left + W - 6, listTop + 2, left + W - 3, listBottom - 2, 0xFF1C242C);
            g.fill(left + W - 6, listTop + 2 + p, left + W - 3, listTop + 2 + p + knob, 0xFF4E6070);
        }
        g.fill(left + 6, listBottom + 1, left + W - 6, listBottom + 2, 0xFF2E3A44);
        if (message != null && !message.getString().isEmpty()) {
            GuiDraw.wrapped(g, font, message, left + 8, listBottom + 4, 0.7F, WARN, W - 90, 2, 8);
        }
        GuiDraw.right(g, font, Component.translatable("village.pioneers.emissary.balance", BoardScreen.group(balance)), left + W - 8, listBottom + 6, 0.7F,
              DIM);
        super.render(g, mx, my, pt);
    }

    private void row(GuiGraphics g, int mx, int my, Row r, int x, int y, int w, int clipTop, int clipBottom) {
        g.fill(x, y, x + w, y + ROW_H - 3, 0xFF10161B);
        g.blit(Pioneers.rl("textures/gui/village/people_" + (r.icon().isEmpty() ? r.id() : r.icon()) + ".png"), x + 3, y + 3, 0, 0, 12, 12, 12, 12);
        Component tier = Component.translatable("reputation.pioneers.tier." + r.tier());
        GuiDraw.text(g, font, Component.translatable("village.pioneers.emissary.people", r.name(), tier, BoardScreen.signed(r.rep())), x + 18, y + 4, 0.75F,
              r.rep() < 0 ? BAD : TEXT, w - 84);
        Component detail;
        boolean can = "NONE".equals(r.refusal());
        if (r.arrive() >= 0) {
            detail = Component.translatable("village.pioneers.emissary.flying", r.arrive());
        } else if (can || "TOKENS".equals(r.refusal())) {
            detail = Component.translatable("village.pioneers.emissary.cost", BoardScreen.group(r.cost()), r.gain());
        } else {
            detail = Component.translatable("village.pioneers.repair.refusal." + r.refusal().toLowerCase(java.util.Locale.ROOT));
        }
        GuiDraw.text(g, font, detail, x + 18, y + 15, 0.65F, r.arrive() >= 0 ? GOOD : can ? DIM : WARN, w - 84);
        int bx = x + w - 60, by = y + 5;
        boolean hover = can && mx >= bx && mx < bx + 58 && my >= by && my < by + 16 && my >= clipTop && my < clipBottom;
        g.fill(bx, by, bx + 58, by + 16, !can ? 0xFF182027 : hover ? 0xFF2F4A3E : 0xFF26392F);
        g.fill(bx, by + 15, bx + 58, by + 16, can ? ACCENT : 0xFF262E36);
        GuiDraw.center(g, font, Component.translatable("village.pioneers.emissary.send"), bx + 29F, by + 4, 0.75F, can ? TEXT : DIM, 54);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.round(delta * 10);
        return true;
    }

    @Override
    public boolean mouseClicked(double mxd, double myd, int b) {
        int mx = (int) mxd, my = (int) myd;
        int listTop = top + LIST_Y, listBottom = top + H - FOOT_H;
        if (my >= listTop && my < listBottom) {
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                int x = left + 8, w = W - 22, y = listTop - scroll + i * ROW_H;
                int bx = x + w - 60, by = y + 5;
                if ("NONE".equals(r.refusal()) && mx >= bx && mx < bx + 58 && my >= by && my < by + 16) {
                    CompoundTag t = new CompoundTag();
                    t.putInt("entity", entity);
                    t.putString("people", r.id());
                    RpNet.toServer(RpActions.EMISSARY_SEND, t);
                    RpClient.play(RpSounds.QUILL.get(), 1.0F);
                    return true;
                }
            }
        }
        return super.mouseClicked(mxd, myd, b);
    }
}

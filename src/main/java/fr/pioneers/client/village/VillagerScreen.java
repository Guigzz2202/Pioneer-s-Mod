package fr.pioneers.client.village;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.Pioneers;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.TooltipLayer;
import fr.pioneers.client.rp.RpClient;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : fiche du villageois : portrait (l'entite elle-meme), nom, metier, peuple, barre d'affinite et palier, Echanger,
 * Parler, Montrer le Carnet. Tout vient du serveur.
 * <p>
 * 1.9.1 (retours de Guillaume : « les villageois devraient parler dans une interface ») : la fiche reste ouverte quand on clique Parler ; la
 * reponse s'ecrit dans le panneau de dialogue de la fiche (nom du villageois, replique repliee a la vraie largeur de la police, historique de
 * la visite, defilement a la molette). Rien dans le chat, plus de bulle. Echanger et Parler coexistent dans la meme fiche. Un villageois
 * Hostile envers le joueur refuse de parler : court ecran de refus avec son insulte et un seul bouton, Partir.
 * Coordonnees a garder alignees avec la maquette {@code tools/gc/rp/village_ui_preview.py}.
 */
public class VillagerScreen extends Screen {

    static final int W = 300, H = 214, HOSTILE_W = 240, HOSTILE_H = 132;
    static final int DLG_X = 8, DLG_Y = 100, DLG_H = 84, BTN_Y = H - 24, BTN_H = 16;
    static final float TEXT_SCALE = 0.75F;
    static final int LINE = 8, PAD = 4, BAR = 3;
    private static final int GOOD = 0xFF6FCF7F, BAD = 0xFFE3735F, TEXT = 0xFFE8E8E8, DIM = 0xFF8A96A2, ACCENT = 0xFF3FD0A0, WARN = 0xFFE3A23B;
    private int entity, affinity, trust, village;
    private boolean canTrade, canBook, hasVillage, hostile;
    private String profession = "", people = "";
    /** 1.9 (MAJ RP, bloc 4) : icone (peuple modele) et nom d'un peuple de joueurs. */
    private String icon = "";
    @javax.annotation.Nullable
    private Component peopleName;
    private Component name = Component.empty(), refusal = Component.empty();
    private int left, top;
    private VillageClient.Visit visit;
    /** Defilement du dialogue en lignes depuis le bas (0 = la derniere replique visible). */
    private int scroll;

    public VillagerScreen(CompoundTag t) {
        super(Component.translatable("village.pioneers.screen"));
        read(t);
    }

    public int entity() {
        return entity;
    }

    public void read(CompoundTag t) {
        entity = t.getInt("entity");
        name = Component.Serializer.fromJson(t.getString("name"));
        profession = t.getString("profession");
        people = t.getString("people");
        icon = t.getString("icon");
        peopleName = t.contains("peopleName") ? Component.Serializer.fromJson(t.getString("peopleName")) : null;
        affinity = t.getInt("affinity");
        trust = t.getInt("trust");
        canTrade = t.getBoolean("trade");
        refusal = t.contains("refusal") ? Component.translatable(t.getString("refusal")) : Component.empty();
        canBook = t.getBoolean("book");
        hasVillage = t.contains("village");
        village = t.getInt("village");
        hostile = t.getBoolean("hostile");
        visit = VillageClient.visit(entity);
        if (t.contains("message")) {
            VillageClient.add(visit, new VillageClient.Line(null, Component.translatable(t.getString("message")), true));
        }
        if (t.contains("reply")) {
            Component reply = Component.Serializer.fromJson(t.getString("reply"));
            if (reply != null && !reply.getString().isEmpty()) {
                VillageClient.add(visit, new VillageClient.Line(name, reply, false));
            }
        }
        scroll = 0;
        if (minecraft != null) {
            init();
        }
    }

    @Override
    protected void init() {
        left = (width - (hostile ? HOSTILE_W : W)) / 2;
        top = (height - (hostile ? HOSTILE_H : H)) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String mood() {
        return affinity <= -20 ? "contempt" : affinity >= 40 ? "esteem" : "neutral";
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        if (hostile) {
            renderRefusal(g, mx, my);
            super.render(g, mx, my, pt);
            return;
        }
        GuiDraw.panel(g, left, top, W, H);
        //portrait
        GuiDraw.screen(g, left + 8, top + 8, 64, 86);
        Entity e = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(entity);
        if (e instanceof LivingEntity le) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, left + 40, top + 88, 34, left + 40 - mx, top + 40 - my, le);
        }
        g.blit(Pioneers.rl("textures/gui/village/mood_" + mood() + ".png"), left + 54, top + 10, 0, 0, 16, 16, 16, 16);
        //identite
        int x = left + 80, iw = W - 88;
        GuiDraw.text(g, font, name, x, top + 10, 1, TEXT, iw);
        String key = profession.replace("minecraft:", "");
        GuiDraw.text(g, font, Component.translatable("village.pioneers.profession." + key), x, top + 22, 0.8F, DIM, iw);
        //1.9 (MAJ RP, bloc 4) : peuple de joueurs : icone du peuple modele et nom saisi a la secession
        g.blit(Pioneers.rl("textures/gui/village/people_" + (icon.isEmpty() ? people : icon) + ".png"), x, top + 32, 0, 0, 12, 12, 12, 12);
        GuiDraw.text(g, font, peopleName != null ? peopleName : Component.translatable("people.pioneers." + people), x + 15, top + 34, 0.8F, DIM, iw - 15);
        //affinite (-100..+100), palier et signe
        int bx = x, by = top + 50, bw = iw;
        g.fill(bx, by, bx + bw, by + 6, 0xFF14181C);
        int mid = bx + bw / 2;
        int end = mid + (int) Math.round(bw / 2.0 * affinity / 100.0);
        g.fill(Math.min(mid, end), by, Math.max(mid, end), by + 6, affinity >= 0 ? GOOD : BAD);
        g.fill(mid, by - 1, mid + 1, by + 7, TEXT);
        String tierKey = trust >= 1 ? "trust_" + trust : trust == 0 ? "reserved" : trust == -1 ? "scornful" : "hostile";
        if (trust >= 1) {
            g.blit(Pioneers.rl("textures/gui/village/trust_" + trust + ".png"), bx, by + 10, 0, 0, 12, 12, 12, 12);
        }
        GuiDraw.text(g, font, Component.translatable("village.pioneers.tier." + tierKey), bx + (trust >= 1 ? 15 : 0), by + 12, 0.8F, TEXT, bw - 40);
        GuiDraw.right(g, font, Component.literal((affinity > 0 ? "+" : affinity < 0 ? "−" : "") + Math.abs(affinity)), bx + bw, by + 12, 0.8F,
              affinity >= 0 ? GOOD : BAD);
        if (hasVillage) {
            GuiDraw.text(g, font, Component.translatable("village.pioneers.village_affinity", (village > 0 ? "+" : village < 0 ? "−" : "") + Math.abs(village)),
                  bx, by + 24, 0.7F, DIM, bw);
        }
        if (!canTrade && !refusal.getString().isEmpty()) {
            GuiDraw.text(g, font, refusal, bx, by + 34, 0.7F, WARN, bw);
        }
        //panneau de dialogue
        dialogue(g, left + DLG_X, top + DLG_Y, W - 2 * DLG_X, DLG_H, visit.lines, Component.translatable("village.pioneers.dialogue.empty"));
        //actions : Echanger et Parler dans la meme fiche
        int bwid = (W - 16 - 8) / 3;
        button(g, mx, my, left + 8, top + BTN_Y, bwid, Component.translatable("village.pioneers.action.trade"), canTrade);
        button(g, mx, my, left + 12 + bwid, top + BTN_Y, bwid, Component.translatable("village.pioneers.action.talk"), true);
        if (canBook) {
            button(g, mx, my, left + 16 + 2 * bwid, top + BTN_Y, bwid, Component.translatable("village.pioneers.action.book"), true);
        }
        if (!canTrade && inside(mx, my, left + 8, top + BTN_Y, bwid, BTN_H) && !refusal.getString().isEmpty()) {
            TooltipLayer.text(font, refusal, mx, my);
        }
        super.render(g, mx, my, pt);
    }

    /** Court ecran de refus : nom, metier, l'insulte dans le panneau de dialogue, Partir. */
    private void renderRefusal(GuiGraphics g, int mx, int my) {
        int w = HOSTILE_W, h = HOSTILE_H;
        GuiDraw.panel(g, left, top, w, h);
        g.blit(Pioneers.rl("textures/gui/village/mood_contempt.png"), left + 8, top + 8, 0, 0, 16, 16, 16, 16);
        GuiDraw.text(g, font, name, left + 30, top + 8, 1, TEXT, w - 38);
        String key = profession.replace("minecraft:", "");
        GuiDraw.text(g, font, Component.translatable("village.pioneers.profession." + key), left + 30, top + 19, 0.7F, DIM, w - 38);
        GuiDraw.text(g, font, Component.translatable("village.pioneers.refuse_talk", name), left + 8, top + 32, 0.8F, BAD, w - 16);
        dialogue(g, left + 8, top + 44, w - 16, h - 44 - 28, visit.lines, Component.empty());
        button(g, mx, my, left + (w - 80) / 2, top + h - 22, 80, Component.translatable("village.pioneers.action.leave"), true);
    }

    /** Lignes d'affichage du dialogue, repliees a la largeur reelle de la police (echelle comprise). */
    private List<Row> rows(List<VillageClient.Line> lines, int width) {
        List<Row> out = new ArrayList<>();
        int wrap = (int) Math.floor((width - 2 * PAD - BAR - 2) / TEXT_SCALE);
        for (VillageClient.Line l : lines) {
            if (!out.isEmpty()) {
                out.add(new Row(null, 0, true));
            }
            if (l.narration()) {
                for (FormattedCharSequence s : font.split(l.text().copy().withStyle(ChatFormatting.ITALIC), wrap)) {
                    out.add(new Row(s, DIM, false));
                }
                continue;
            }
            if (l.speaker() != null) {
                for (FormattedCharSequence s : font.split(l.speaker(), wrap)) {
                    out.add(new Row(s, ACCENT, false));
                }
            }
            Component quoted = Component.translatable("village.pioneers.dialogue.quote", l.text());
            for (FormattedCharSequence s : font.split(quoted, wrap)) {
                out.add(new Row(s, TEXT, false));
            }
        }
        return out;
    }

    record Row(@javax.annotation.Nullable FormattedCharSequence text, int color, boolean gap) {
    }

    private int visibleRows(int h) {
        return Math.max(1, (h - 2 * PAD) / LINE);
    }

    private void dialogue(GuiGraphics g, int x, int y, int w, int h, List<VillageClient.Line> lines, Component empty) {
        GuiDraw.screen(g, x, y, w, h);
        List<Row> rows = rows(lines, w);
        if (rows.isEmpty()) {
            GuiDraw.text(g, font, empty, x + PAD, y + PAD, TEXT_SCALE, DIM, w - 2 * PAD);
            return;
        }
        int vis = visibleRows(h);
        int max = Math.max(0, rows.size() - vis);
        scroll = Mth.clamp(scroll, 0, max);
        int first = Math.max(0, rows.size() - vis - scroll);
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        PoseStack pose = g.pose();
        for (int i = 0; i < vis && first + i < rows.size(); i++) {
            Row r = rows.get(first + i);
            if (r.text() == null) {
                continue;
            }
            pose.pushPose();
            pose.translate(x + PAD, y + PAD + i * LINE, 0);
            pose.scale(TEXT_SCALE, TEXT_SCALE, 1);
            g.drawString(font, r.text(), 0, 0, r.color(), false);
            pose.popPose();
        }
        g.disableScissor();
        if (max > 0) {
            //barre de defilement
            int track = h - 4;
            int knob = Math.max(8, track * vis / rows.size());
            int pos = (int) Math.round((track - knob) * (1 - scroll / (double) max));
            g.fill(x + w - BAR - 2, y + 2, x + w - 2, y + h - 2, 0xFF1C242C);
            g.fill(x + w - BAR - 2, y + 2 + pos, x + w - 2, y + 2 + pos + knob, 0xFF4E6070);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll += delta > 0 ? 2 : -2;
        return true;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, Component label, boolean enabled) {
        boolean hover = enabled && inside(mx, my, x, y, w, BTN_H);
        g.fill(x, y, x + w, y + BTN_H, !enabled ? 0xFF182027 : hover ? 0xFF2F4A3E : 0xFF26392F);
        g.fill(x, y + BTN_H - 1, x + w, y + BTN_H, enabled ? ACCENT : 0xFF262E36);
        GuiDraw.center(g, font, label, x + w / 2F, y + 4, 0.85F, enabled ? TEXT : DIM, w - 4);
    }

    @Override
    public boolean mouseClicked(double mxd, double myd, int b) {
        int mx = (int) mxd, my = (int) myd;
        if (hostile) {
            if (inside(mx, my, left + (HOSTILE_W - 80) / 2, top + HOSTILE_H - 22, 80, BTN_H)) {
                onClose();
                return true;
            }
            return super.mouseClicked(mxd, myd, b);
        }
        int bwid = (W - 16 - 8) / 3;
        int action = -1;
        if (inside(mx, my, left + 8, top + BTN_Y, bwid, BTN_H) && canTrade) {
            action = RpActions.VILLAGER_TRADE;
        } else if (inside(mx, my, left + 12 + bwid, top + BTN_Y, bwid, BTN_H)) {
            action = RpActions.VILLAGER_TALK;
        } else if (canBook && inside(mx, my, left + 16 + 2 * bwid, top + BTN_Y, bwid, BTN_H)) {
            action = RpActions.VILLAGER_SHOW_BOOK;
        }
        if (action >= 0) {
            CompoundTag t = new CompoundTag();
            t.putInt("entity", entity);
            RpNet.toServer(action, t);
            RpClient.play(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F);
            return true;
        }
        return super.mouseClicked(mxd, myd, b);
    }
}

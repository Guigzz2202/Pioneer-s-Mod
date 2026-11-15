package fr.pioneers.client.village;

import fr.pioneers.Pioneers;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.TooltipLayer;
import fr.pioneers.client.rp.RpClient;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : le Tableau des besoins : petits papiers des evenements en cours (la voix du village), cinq icones de service,
 * les cinq joueurs qui ont le plus fait pour le village, et les ouvriers d'un chantier proche (engager, congedier).
 * <p>
 * 1.9.1 (retours de Guillaume) : deux onglets, « Annonces » (ce qui precede) et « Reparations » : amende honorable (jetons ou marchandises
 * demandees), taches de reparation (livrer, veiller, replanter, repousser un raid), emissaire en route, treve du drapeau blanc. Le contenu
 * defile a la molette ; un message du serveur (reponse a une action, replique d'un villageois) s'ecrit au pied du Tableau. Tout est calcule
 * par le serveur ; les boutons envoient des intentions. Coordonnees alignees avec la maquette {@code tools/gc/rp/village_ui_preview.py}.
 */
public class BoardScreen extends Screen {

    static final int W = 276, H = 220, TAB_Y = 24, BODY_Y = 40, FOOT_H = 22;
    private static final int TEXT = 0xFFE8E8E8, DIM = 0xFF8A96A2, ACCENT = 0xFF3FD0A0, PAPER = 0xFFE9DFC6, INK = 0xFF3A2E22, GOOD = 0xFF6FCF7F,
          BAD = 0xFFE3735F, WARN = 0xFFE3A23B;
    private static final String[] SERVICES = {"water", "energy", "workshop", "storage", "archives"};

    private record Notice(String type, ItemStack item, long days, String line) {
    }

    /** Zone cliquable construite pendant le dessin (meme geometrie pour le dessin et le clic). */
    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    private long pos;
    private boolean none;
    private String people = "";
    /** 1.9 (MAJ RP, bloc 4) : icone (peuple modele d'un peuple de joueurs) et pourparlers de ralliement en cours. */
    private String icon = "";
    private CompoundTag rally;
    private Component peopleName = Component.empty();
    /** 1.0.3-beta (lot 11b) : nom du village fonde par un joueur (null : village de PNJ) et son fondateur. */
    private Component villageName;
    private String owner = "";
    private final List<Notice> notices = new ArrayList<>();
    private final Set<String> services = new HashSet<>();
    private final List<String> top = new ArrayList<>();
    private boolean workers, allowed;
    private int hired, candidates, affinity;
    private long wage;
    private CompoundTag repairs = new CompoundTag();
    @Nullable
    private Component message;
    private int tab, scroll, contentH;
    private int left, topY;
    private final List<Hit> hits = new ArrayList<>();

    public BoardScreen(CompoundTag t) {
        super(Component.translatable("block.pioneers.needs_board"));
        read(t);
        //un village fache s'ouvre sur ses reparations
        tab = repairs.getBoolean("need") && repairs.getInt("depth") > 0 ? 1 : 0;
    }

    public void read(CompoundTag t) {
        pos = t.getLong("pos");
        none = t.getBoolean("none");
        people = t.getString("people");
        icon = t.contains("icon") ? t.getString("icon") : people;
        rally = t.contains("rally") ? t.getCompound("rally") : null;
        peopleName = t.contains("peopleName") ? Component.Serializer.fromJson(t.getString("peopleName")) : Component.empty();
        villageName = t.contains("villageName") ? Component.Serializer.fromJson(t.getString("villageName")) : null;
        owner = t.getString("owner");
        notices.clear();
        for (Tag x : t.getList("events", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            notices.add(new Notice(c.getString("type"), stack(c.getString("item")), c.getLong("days"), c.getString("line")));
        }
        services.clear();
        for (Tag x : t.getList("services", Tag.TAG_STRING)) {
            services.add(x.getAsString());
        }
        top.clear();
        for (Tag x : t.getList("top", Tag.TAG_STRING)) {
            top.add(x.getAsString());
        }
        affinity = t.getInt("affinity");
        workers = t.contains("workers");
        CompoundTag w = t.getCompound("workers");
        hired = w.getInt("hired");
        candidates = w.getInt("candidates");
        wage = w.getLong("wage");
        allowed = w.getBoolean("allowed");
        repairs = t.getCompound("repairs");
        message = t.contains("msg") ? Component.Serializer.fromJson(t.getString("msg")) : null;
    }

    static ItemStack stack(String id) {
        return id.isEmpty() ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id)));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        topY = (height - H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        hits.clear();
        GuiDraw.panel(g, left, topY, W, H);
        if (none) {
            GuiDraw.center(g, font, Component.translatable("village.pioneers.board.none"), left + W / 2F, topY + H / 2F, 0.9F, DIM, W - 20);
            super.render(g, mx, my, pt);
            return;
        }
        g.blit(Pioneers.rl("textures/gui/village/people_" + icon + ".png"), left + 8, topY + 6, 0, 0, 16, 16, 16, 16);
        GuiDraw.text(g, font, villageName != null ? Component.translatable("village.pioneers.board.title_founded", villageName, owner)
              : Component.translatable("village.pioneers.board.title", peopleName), left + 28, topY + 10, 1, TEXT, W - 40);
        //onglets
        boolean need = repairs.getBoolean("need");
        tab(g, mx, my, 0, left + 8, Component.translatable("village.pioneers.board.tab.notices"), false);
        tab(g, mx, my, 1, left + 8 + (W - 20) / 2 + 4, Component.translatable("village.pioneers.board.tab.repairs"), need);
        //corps qui defile
        int bodyTop = topY + BODY_Y, bodyBottom = topY + H - FOOT_H;
        int viewH = bodyBottom - bodyTop;
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - viewH));
        g.enableScissor(left + 2, bodyTop, left + W - 2, bodyBottom);
        int y0 = bodyTop - scroll;
        int firstBody = hits.size();
        int end = tab == 0 ? notices(g, mx, my, y0) : repairs(g, mx, my, y0);
        g.disableScissor();
        contentH = end - y0;
        //un bouton masque par le defilement ne se clique pas
        List<Hit> body = hits.subList(firstBody, hits.size());
        body.removeIf(h -> h.y() < bodyTop || h.y() + h.h() > bodyBottom);
        if (contentH > viewH) {
            int track = viewH - 4, knob = Math.max(10, track * viewH / contentH);
            int p = (int) Math.round((track - knob) * scroll / (double) (contentH - viewH));
            g.fill(left + W - 6, bodyTop + 2, left + W - 3, bodyBottom - 2, 0xFF1C242C);
            g.fill(left + W - 6, bodyTop + 2 + p, left + W - 3, bodyTop + 2 + p + knob, 0xFF4E6070);
        }
        //pied : message du serveur (reponse, replique)
        g.fill(left + 6, bodyBottom + 1, left + W - 6, bodyBottom + 2, 0xFF2E3A44);
        if (message != null && !message.getString().isEmpty()) {
            GuiDraw.wrapped(g, font, message, left + 8, bodyBottom + 4, 0.7F, WARN, W - 16, 2, 8);
        }
        super.render(g, mx, my, pt);
    }

    private void tab(GuiGraphics g, int mx, int my, int index, int x, Component label, boolean dot) {
        int w = (W - 20) / 2, y = topY + TAB_Y;
        boolean sel = tab == index, hover = mx >= x && mx < x + w && my >= y && my < y + 13;
        g.fill(x, y, x + w, y + 13, sel ? 0xFF26392F : hover ? 0xFF1E2A26 : 0xFF161E24);
        g.fill(x, y + 12, x + w, y + 13, sel ? ACCENT : 0xFF2E3A44);
        GuiDraw.center(g, font, label, x + w / 2F, y + 3, 0.75F, sel ? TEXT : DIM, w - 12);
        if (dot) {
            g.fill(x + w - 7, y + 4, x + w - 3, y + 8, BAD);
        }
        hits.add(new Hit(x, y, w, 13, () -> {
            tab = index;
            scroll = 0;
        }));
    }

    // ------------------------------------------------------------------ onglet Annonces (contenu d'avant 1.9.1)
    private int notices(GuiGraphics g, int mx, int my, int y) {
        if (rally != null) {
            //1.9 (MAJ RP, bloc 4) : pourparlers de ralliement : ruban a la couleur du peuple, jours ecoules sur jours prevus
            int col = 0xFF000000 | rally.getInt("color");
            g.fill(left + 8, y, left + W - 10, y + 30, PAPER);
            g.fill(left + 8, y, left + 12, y + 30, col);
            GuiDraw.wrapped(g, font, Component.translatable("village.pioneers.board.rally", rally.getString("name")), left + 16, y + 4, 0.75F, INK,
                  W - 34, 2, 9);
            long total = Math.max(1, rally.getLong("total")), done = rally.getLong("done");
            int bw = W - 120;
            g.fill(left + 16, y + 23, left + 16 + bw, y + 26, 0xFFB9A889);
            g.fill(left + 16, y + 23, left + 16 + (int) (bw * Math.min(1.0, done / (double) total)), y + 26, col);
            GuiDraw.text(g, font, Component.translatable("village.pioneers.board.rally_days", done, total), left + 24 + bw, y + 21, 0.6F, 0xFF7A6A55, 80);
            y += 34;
        }
        if (notices.isEmpty()) {
            GuiDraw.text(g, font, Component.translatable("village.pioneers.board.quiet"), left + 10, y + 4, 0.8F, DIM, W - 20);
            y += 18;
        }
        for (Notice n : notices) {
            g.fill(left + 8, y, left + W - 10, y + 30, PAPER);
            g.fill(left + W / 2 - 1, y - 1, left + W / 2 + 2, y + 2, 0xFFB23A2E);
            g.blit(Pioneers.rl("textures/gui/village/event_" + n.type() + ".png"), left + 12, y + 7, 0, 0, 16, 16, 16, 16);
            Component line = n.line().isEmpty() ? Component.translatable("village.pioneers.event." + n.type())
                  : Component.translatable(n.line(), n.item().isEmpty() ? Component.empty() : n.item().getHoverName());
            GuiDraw.wrapped(g, font, line, left + 32, y + 4, 0.75F, INK, W - 66, 2, 9);
            if (!n.item().isEmpty()) {
                g.renderItem(n.item(), left + W - 30, y + 7);
            }
            GuiDraw.text(g, font, Component.translatable("village.pioneers.board.days", n.days()), left + 32, y + 22, 0.6F, 0xFF7A6A55, 120);
            y += 34;
        }
        //services
        GuiDraw.text(g, font, Component.translatable("village.pioneers.board.services"), left + 10, y + 2, 0.8F, DIM, 76);
        for (int k = 0; k < SERVICES.length; k++) {
            boolean on = services.contains(SERVICES[k]);
            int x = left + 90 + k * 22;
            g.setColor(on ? 1F : 0.3F, on ? 1F : 0.3F, on ? 1F : 0.3F, 1F);
            g.blit(Pioneers.rl("textures/gui/village/service_" + SERVICES[k] + ".png"), x, y - 2, 0, 0, 16, 16, 16, 16);
            g.setColor(1F, 1F, 1F, 1F);
            if (mx >= x && mx < x + 16 && my >= y - 2 && my < y + 14) {
                //1.0.1 : infobulle dessinee apres l'ecran, hors du ciseau de la zone qui defile (elle etait coupee au bord du panneau)
                TooltipLayer.components(font, List.of(Component.translatable("village.pioneers.service." + SERVICES[k]),
                      Component.translatable(on ? "village.pioneers.service.on" : "village.pioneers.service.off")), mx, my);
            }
        }
        y += 20;
        //les meilleurs pionniers du village
        GuiDraw.text(g, font, Component.translatable("village.pioneers.board.top"), left + 10, y, 0.8F, DIM, W - 20);
        y += 10;
        if (top.isEmpty()) {
            GuiDraw.text(g, font, Component.translatable("village.pioneers.board.top_none"), left + 14, y, 0.75F, DIM, W - 28);
            y += 10;
        }
        for (int i = 0; i < top.size(); i++) {
            GuiDraw.text(g, font, Component.literal((i + 1) + ". " + top.get(i)), left + 14 + (i % 2) * 124, y + (i / 2) * 10, 0.75F, TEXT, 118);
        }
        y += ((top.size() + 1) / 2) * 10 + 4;
        //ouvriers
        if (workers) {
            g.blit(Pioneers.rl("textures/gui/village/worker.png"), left + 8, y, 0, 0, 16, 16, 16, 16);
            Component info = allowed ? Component.translatable("village.pioneers.board.workers", hired, 4, wage, candidates)
                  : Component.translatable("village.pioneers.board.workers_refused");
            GuiDraw.wrapped(g, font, info, left + 28, y + 1, 0.7F, TEXT, W - 132, 2, 9);
            boolean canHire = allowed && hired < 4 && candidates > 0;
            button(g, mx, my, left + W - 102, y, 44, Component.translatable("village.pioneers.board.hire"), canHire, () -> hire(false));
            button(g, mx, my, left + W - 54, y, 44, Component.translatable("village.pioneers.board.release"), hired > 0, () -> hire(true));
            y += 20;
        }
        return y + 4;
    }

    private void hire(boolean release) {
        CompoundTag t = new CompoundTag();
        t.putLong("pos", pos);
        t.putBoolean("release", release);
        RpNet.toServer(RpActions.BOARD_HIRE, t);
        RpClient.play(RpSounds.PAPER_PIN.get(), 1.0F);
    }

    // ------------------------------------------------------------------ onglet Reparations (1.9.1)
    private int repairs(GuiGraphics g, int mx, int my, int y) {
        int x = left + 10, w = W - 22;
        int rep = repairs.getInt("rep"), avg = repairs.getInt("avg");
        Component tier = Component.translatable("reputation.pioneers.tier." + repairs.getString("tier"));
        g.blit(Pioneers.rl("textures/gui/village/people_" + icon + ".png"), x, y + 2, 0, 0, 12, 12, 12, 12);
        GuiDraw.text(g, font, Component.translatable("village.pioneers.repair.standing", peopleName, tier, signed(rep)), x + 16, y + 4, 0.8F,
              rep < 0 ? BAD : TEXT, w - 16);
        y += 16;
        GuiDraw.text(g, font, Component.translatable("village.pioneers.repair.village", signed(avg)), x + 16, y, 0.7F, avg < 0 ? BAD : DIM, w - 16);
        y += 12;
        if (!repairs.getBoolean("need") || repairs.getInt("depth") <= 0) {
            y += GuiDraw.wrapped(g, font, Component.translatable("village.pioneers.repair.nothing"), x, y + 2, 0.75F, DIM, w, 3, 9) * 9 + 6;
            return tail(g, y, x, w);
        }
        //amende honorable
        y = header(g, Component.translatable("village.pioneers.repair.amends"), x, y, w);
        CompoundTag a = repairs.getCompound("amends");
        String refusal = a.getString("refusal");
        boolean daily = "DAILY".equals(refusal);
        long cost = a.getLong("tokens");
        GuiDraw.text(g, font, Component.translatable("village.pioneers.repair.pay", group(cost)), x, y + 4, 0.75F, TEXT, w - 64);
        button(g, mx, my, x + w - 58, y, 58, Component.translatable("village.pioneers.repair.pay_button"), "NONE".equals(refusal),
              () -> act(RpActions.BOARD_AMENDS, "mode", "tokens"));
        y += 18;
        if (a.contains("item")) {
            ItemStack it = stack(a.getString("item"));
            int count = a.getInt("count"), have = a.getInt("have");
            g.renderItem(it, x, y);
            GuiDraw.wrapped(g, font, Component.translatable("village.pioneers.repair.deliver", count, it.getHoverName(), have), x + 20, y + 1, 0.7F,
                  have >= count ? TEXT : DIM, w - 84, 2, 8);
            button(g, mx, my, x + w - 58, y, 58, Component.translatable("village.pioneers.repair.deliver_button"), !daily && have >= count,
                  () -> act(RpActions.BOARD_AMENDS, "mode", "goods"));
            y += 20;
        }
        GuiDraw.text(g, font, Component.translatable("village.pioneers.repair.amends_info", a.getInt("done"), a.getInt("max"), a.getInt("people"),
              a.getInt("villager")), x, y, 0.65F, daily ? WARN : DIM, w);
        y += 12;
        //taches
        y = header(g, Component.translatable("village.pioneers.repair.tasks"), x, y, w);
        List<CompoundTag> quests = new ArrayList<>();
        for (Tag tq : repairs.getList("quests", Tag.TAG_COMPOUND)) {
            quests.add((CompoundTag) tq);
        }
        if (quests.isEmpty()) {
            GuiDraw.text(g, font, Component.translatable("village.pioneers.repair.no_task"), x, y + 2, 0.7F, DIM, w);
            y += 12;
        }
        for (CompoundTag q : quests) {
            y = quest(g, mx, my, q, x, y, w);
        }
        return tail(g, y, x, w);
    }

    /** Fin de l'onglet : emissaire en route, rappel du drapeau blanc, devise. */
    private int tail(GuiGraphics g, int y, int x, int w) {
        if (repairs.contains("envoy")) {
            y += GuiDraw.wrapped(g, font, Component.translatable("village.pioneers.repair.envoy", repairs.getLong("envoy")), x, y + 2, 0.7F, GOOD, w, 2, 8)
                 * 8 + 4;
        }
        Component flag = Component.translatable(repairs.getBoolean("truce") ? "village.pioneers.repair.truce_on" : "village.pioneers.repair.truce_hint");
        y += GuiDraw.wrapped(g, font, flag, x, y + 2, 0.65F, repairs.getBoolean("truce") ? GOOD : DIM, w, 3, 8) * 8 + 4;
        GuiDraw.text(g, font, Component.translatable("village.pioneers.repair.motto"), x, y + 2, 0.65F, DIM, w);
        return y + 14;
    }

    private int header(GuiGraphics g, Component label, int x, int y, int w) {
        g.fill(x, y + 4, x + w, y + 5, 0xFF2E3A44);
        int tw = (int) Math.ceil(font.width(label) * 0.75F) + 6;
        g.fill(x + 6, y, x + 6 + tw, y + 9, 0xFF141A20);
        GuiDraw.text(g, font, label, x + 9, y + 1, 0.75F, ACCENT, w - 12);
        return y + 12;
    }

    /** Une tache : pictogramme, intitule, conditions, recompense, bouton selon son etat. */
    private int quest(GuiGraphics g, int mx, int my, CompoundTag q, int x, int y, int w) {
        String type = q.getString("type"), state = q.getString("state");
        int h = 40;
        g.fill(x, y, x + w, y + h, 0xFF10161B);
        g.renderOutline(x, y, w, h, state.equals("active") ? ACCENT : 0xFF2E3A44);
        ItemStack it = stack(q.getString("item"));
        //pictogramme : le bien a livrer, sinon une lanterne (veille), des graines (champs), une arbalete (raid)
        g.renderItem(!it.isEmpty() ? it : new ItemStack(switch (type) {
            case "watch" -> Items.LANTERN;
            case "replant" -> Items.WHEAT_SEEDS;
            default -> Items.CROSSBOW;
        }), x + 4, y + 4);
        Component title = switch (type) {
            case "deliver" -> Component.translatable("village.pioneers.repair.task.deliver", q.getInt("count"), it.getHoverName());
            case "watch" -> Component.translatable("village.pioneers.repair.task.watch", q.getInt("count") > 0 ? q.getInt("count") : q.getInt("need"));
            case "replant" -> Component.translatable("village.pioneers.repair.task.replant", q.getInt("count") > 0 ? q.getInt("count") : q.getInt("need"));
            default -> Component.translatable("village.pioneers.repair.task.raid");
        };
        int textW = w - 24 - 64;
        GuiDraw.wrapped(g, font, title, x + 24, y + 4, 0.75F, TEXT, textW, 2, 8);
        Component detail = switch (state) {
            case "active" -> progress(type, q);
            case "elsewhere" -> Component.translatable("village.pioneers.repair.elsewhere");
            case "done" -> Component.translatable("village.pioneers.repair.done_window");
            default -> Component.translatable("village.pioneers.repair.reward", q.getInt("people"), q.getInt("villager"), q.getInt("days"));
        };
        GuiDraw.wrapped(g, font, detail, x + 24, y + 22, 0.65F, state.equals("active") ? GOOD : DIM, textW, 2, 7);
        int bx = x + w - 62;
        switch (state) {
            case "offer" -> button(g, mx, my, bx, y + 4, 58, Component.translatable("village.pioneers.repair.accept"), true,
                  () -> act(RpActions.BOARD_QUEST, "op", "accept", type));
            case "active" -> {
                boolean report = type.equals("deliver") || type.equals("replant");
                if (report) {
                    boolean ready = !type.equals("deliver") || q.getInt("have") >= q.getInt("count");
                    button(g, mx, my, bx, y + 4, 58, Component.translatable(type.equals("deliver") ? "village.pioneers.repair.deliver_button"
                          : "village.pioneers.repair.report"), ready, () -> act(RpActions.BOARD_QUEST, "op", "report", type));
                }
                button(g, mx, my, bx, y + 22, 58, Component.translatable("village.pioneers.repair.abandon"), true,
                      () -> act(RpActions.BOARD_QUEST, "op", "abandon", type));
            }
            default -> {
            }
        }
        return y + h + 4;
    }

    private Component progress(String type, CompoundTag q) {
        return switch (type) {
            case "deliver" -> Component.translatable("village.pioneers.repair.progress.deliver", q.getInt("have"), q.getInt("count"), q.getLong("left"));
            case "watch" -> Component.translatable(q.getBoolean("night") ? "village.pioneers.repair.progress.watch_night"
                  : "village.pioneers.repair.progress.watch", q.getInt("kills"), q.getInt("need"), q.getInt("present"), q.getLong("left"));
            case "replant" -> Component.translatable("village.pioneers.repair.progress.replant", q.getInt("planted"), q.getInt("need"), q.getLong("left"));
            default -> Component.translatable("village.pioneers.repair.progress.raid", q.getLong("left"));
        };
    }

    private void act(int action, String key, String value) {
        act(action, key, value, null);
    }

    private void act(int action, String key, String value, @Nullable String type) {
        CompoundTag t = new CompoundTag();
        t.putLong("pos", pos);
        t.putString(key, value);
        if (type != null) {
            t.putString("type", type);
        }
        RpNet.toServer(action, t);
        RpClient.play(RpSounds.PAPER_PIN.get(), 1.0F);
    }

    static String group(long v) {
        String r = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder(v < 0 ? "−" : "");
        for (int i = 0; i < r.length(); i++) {
            if (i > 0 && (r.length() - i) % 3 == 0) {
                b.append(' ');
            }
            b.append(r.charAt(i));
        }
        return b.toString();
    }

    static String signed(int v) {
        return (v > 0 ? "+" : v < 0 ? "−" : "") + Math.abs(v);
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, Component label, boolean enabled, Runnable action) {
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + 16;
        g.fill(x, y, x + w, y + 16, !enabled ? 0xFF182027 : hover ? 0xFF2F4A3E : 0xFF26392F);
        g.fill(x, y + 15, x + w, y + 16, enabled ? ACCENT : 0xFF262E36);
        GuiDraw.center(g, font, label, x + w / 2F, y + 4, 0.75F, enabled ? TEXT : DIM, w - 4);
        if (enabled) {
            hits.add(new Hit(x, y, w, 16, action));
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.round(delta * 12);
        return true;
    }

    @Override
    public boolean mouseClicked(double mxd, double myd, int b) {
        int mx = (int) mxd, my = (int) myd;
        for (Hit h : hits) {
            if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) {
                h.action().run();
                return true;
            }
        }
        return super.mouseClicked(mxd, myd, b);
    }
}

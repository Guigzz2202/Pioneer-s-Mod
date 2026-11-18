package fr.pioneers.world.ruins.client;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.TextFit;
import fr.pioneers.society.rp.client.RpClient;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : fiche du chantier de restauration (clic sur le Controleur delabre). Quatre etapes en tete (Releve, Degagement,
 * Remise en etat, Mise en service), avancement en jours, reserve ligne par ligne (deja livre / demande), obstacles (Sentinelles, gravats)
 * et boutons de l'etape. Tout est decide par le serveur ({@code Restoration}) : l'ecran n'envoie que des intentions.
 * <p>
 * 1.9.1 (retours de Guillaume : textes qui se superposaient) : le message du serveur chevauchait la derniere ligne de la reserve (le
 * panneau passe de 214 a 230 de haut, le message a sa bande de deux lignes) ; chaque ligne mesure d'abord sa partie droite (tier, niveau
 * requis, quantites) et la partie gauche prend le reste ; tout texte passe par {@link TextFit} (infobulle du texte coupe).
 */
public class RestorationScreen extends Screen {

    private static final int W = 276, H = 230, ROWS = 7, ROW = 16, MSG_Y = 191;
    private static final int[] TIER_RGB = {0x3FB950, 0x3D8BFF, 0xE5534B};
    private static final String[] STAGES = {"survey", "clearing", "repair", "done"};
    private long site;
    private int tier, stage, days, done, sentinels, rubble, minLevel, playerLevel;
    private String state = "";
    private Component name = Component.empty(), message = Component.empty();
    private final List<Line> lines = new ArrayList<>();
    private int scroll, left, top;
    private final TextFit.Hover hover = new TextFit.Hover();

    private record Line(ItemStack icon, Component label, int have, int need) {
    }

    public RestorationScreen(CompoundTag t) {
        super(Component.translatable("ruins.pioneers.restore.title"));
        read(t);
    }

    public long site() {
        return site;
    }

    public void read(CompoundTag t) {
        site = t.getLong("site");
        tier = Math.max(1, Math.min(3, t.getInt("tier")));
        state = t.getString("state");
        minLevel = t.getInt("minLevel");
        playerLevel = t.getInt("level");
        sentinels = t.getInt("sentinels");
        rubble = t.getInt("rubble");
        if (t.contains("name")) {
            name = Component.Serializer.fromJson(t.getString("name"));
        }
        message = t.contains("message") ? Component.Serializer.fromJson(t.getString("message")) : Component.empty();
        lines.clear();
        CompoundTag p = t.getCompound("project");
        stage = p.getInt("stage");
        days = Math.max(1, p.getInt("days"));
        done = p.getInt("done");
        ListTag needs = p.getList("needs", Tag.TAG_COMPOUND);
        for (int i = 0; i < needs.size(); i++) {
            CompoundTag e = needs.getCompound(i);
            String id = e.getString("id");
            lines.add(new Line(icon(id), label(id), e.getInt("have"), e.getInt("need")));
        }
        scroll = Math.max(0, Math.min(scroll, lines.size() - ROWS));
    }

    private static ItemStack icon(String id) {
        if ("tokens".equals(id)) {
            return new ItemStack(Items.GOLD_NUGGET);
        }
        if (id.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation(id.substring(1)));
            for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                return new ItemStack(h.value());
            }
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id)));
    }

    private static Component label(String id) {
        if ("tokens".equals(id)) {
            return Component.translatable("ruins.pioneers.restore.tokens");
        }
        ItemStack s = icon(id);
        return s.isEmpty() ? Component.literal(id) : s.getHoverName();
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

    private int tierColor() {
        return 0xFF000000 | TIER_RGB[tier - 1];
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        hover.at(mx, my);
        GuiDraw.panel(g, left, top, W, H);
        g.fill(left, top, left + W, top + 1, tierColor());
        //titre : machine et tier (1.9.1 : le tier est mesure, le nom prend le reste)
        Component mk = Component.literal("MK" + "I".repeat(tier));
        int mkW = TextFit.right(g, font, mk, left + W - 48, top + 7, 40, tierColor(), 1F, hover);
        TextFit.fit(g, font, name, left + 8, top + 7, W - 16 - mkW - 8, 0xFFE8E8E8, 1F, hover);
        //etapes
        int sw = (W - 16) / 4;
        boolean restored = "RESTORED".equals(state);
        int current = restored ? 3 : stage;
        for (int i = 0; i < 4; i++) {
            int x = left + 8 + i * sw;
            boolean past = i < current || restored, now = i == current && !restored;
            int fill = past ? 0xFF1F3A2C : now ? GuiDraw.darken(TIER_RGB[tier - 1], 0.45F) | 0xFF000000 : 0xFF1A2027;
            g.fill(x + 1, top + 20, x + sw - 1, top + 34, fill);
            int text = past ? 0xFF7FD69A : now ? 0xFFFFFFFF : 0xFF6C7884;
            TextFit.center(g, font, Component.translatable("ruins.pioneers.restore.stage." + STAGES[i]), x + 3, top + 24, sw - 6, text, 0.75F, hover);
        }
        //avancement et conditions : le niveau requis a droite (60 % de la ligne au plus), les jours dans le reste
        GuiDraw.screen(g, left + 8, top + 38, W - 16, 30);
        boolean levelOk = playerLevel >= minLevel;
        int lw = TextFit.right(g, font, Component.translatable("ruins.pioneers.restore.level_line", String.valueOf(minLevel),
              String.valueOf(playerLevel)), left + 12 + (W - 24) * 2 / 5, top + 42, (W - 24) * 3 / 5, levelOk ? 0xFF7FD69A : 0xFFE35050, 0.75F, hover);
        Component days1 = Component.translatable("ruins.pioneers.restore.days", String.valueOf(done), String.valueOf(days));
        TextFit.fit(g, font, days1, left + 12, top + 42, W - 24 - lw - 6, 0xFFD8D8D8, 0.75F, hover);
        GuiDraw.bar(g, left + 12, top + 51, W - 24, 4, done / (double) days, tierColor());
        Component obstacle = sentinels > 0 ? Component.translatable("ruins.pioneers.restore.obstacle_sentinels", String.valueOf(sentinels))
              : rubble > 0 ? Component.translatable("ruins.pioneers.restore.obstacle_rubble", String.valueOf(rubble))
              : Component.translatable("ruins.pioneers.restore.obstacle_none");
        TextFit.fit(g, font, obstacle, left + 12, top + 59, W - 24, sentinels > 0 || rubble > 0 ? 0xFFE3A23B : 0xFF7FD69A, 0.75F, hover);
        //reserve : quantites mesurees a droite, nom dans le reste
        GuiDraw.screen(g, left + 8, top + 72, W - 16, ROWS * ROW + 4);
        for (int r = 0; r < ROWS && scroll + r < lines.size(); r++) {
            Line l = lines.get(scroll + r);
            int y = top + 74 + r * ROW;
            if (!l.icon().isEmpty()) {
                g.renderFakeItem(l.icon(), left + 11, y);
            }
            boolean full = l.have() >= l.need();
            int nw = TextFit.right(g, font, Component.literal(l.have() + " / " + l.need()), left + W - 14 - 80, y + 2, 80,
                  full ? 0xFF7FD69A : 0xFFB0B8C0, 0.75F, hover);
            TextFit.fit(g, font, l.label(), left + 31, y + 2, W - 14 - 31 - nw - 6, full ? 0xFF7FD69A : 0xFFD8D8D8, 0.75F, hover);
            GuiDraw.bar(g, left + 31, y + 11, W - 46, 2, l.need() == 0 ? 1 : l.have() / (double) l.need(), full ? 0xFF3FB950 : tierColor());
        }
        if (lines.size() > ROWS) {
            int track = ROWS * ROW, knob = Math.max(10, track * ROWS / lines.size());
            int y = top + 74 + (track - knob) * scroll / Math.max(1, lines.size() - ROWS);
            g.fill(left + W - 11, y, left + W - 9, y + knob, 0xFF56636F);
        }
        //message du serveur : sa propre bande de deux lignes, entre la reserve et les boutons
        if (!message.getString().isEmpty()) {
            TextFit.wrap(g, font, message, left + 8, top + MSG_Y, W - 16, 0xFFE3A23B, 0.75F, 2, hover, true);
        }
        //boutons
        for (Button b : buttons()) {
            boolean on = b.enabled && mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + 16;
            g.fill(b.x, b.y, b.x + b.w, b.y + 16, !b.enabled ? 0xFF1A2027 : on ? 0xFF3A4652 : 0xFF2A333C);
            g.fill(b.x, b.y + 15, b.x + b.w, b.y + 16, b.enabled ? tierColor() : 0xFF262E36);
            TextFit.center(g, font, Component.translatable("ruins.pioneers.restore.button." + b.action), b.x + 2, b.y + 4, b.w - 4,
                  b.enabled ? 0xFFFFFFFF : 0xFF5A6670, 0.75F, hover);
        }
        super.render(g, mx, my, pt);
        hover.render(g, font);
    }

    private record Button(String action, int x, int y, int w, boolean enabled) {
    }

    private List<Button> buttons() {
        List<Button> out = new ArrayList<>();
        int y = top + H - 22;
        if ("RESTORED".equals(state) || "DISMANTLED".equals(state)) {
            return out;
        }
        if (stage == 0) {
            out.add(new Button("open", left + W / 2 - 60, y, 120, playerLevel >= minLevel && sentinels == 0));
        } else {
            int bw = (W - 16 - 8) / 3;
            out.add(new Button("deposit", left + 8, y, bw, true));
            out.add(new Button("repair", left + 12 + bw, y, bw, stage == 1 && sentinels == 0 && rubble == 0));
            out.add(new Button("cancel", left + 16 + 2 * bw, y, bw, true));
        }
        return out;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Button b : buttons()) {
            if (b.enabled && mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + 16) {
                if ("cancel".equals(b.action) && !hasShiftDown()) {
                    message = Component.translatable("ruins.pioneers.restore.cancel_confirm");
                    return true;
                }
                RpClient.play(RpSounds.PAPER_PIN.get(), 1.0F);
                CompoundTag t = new CompoundTag();
                t.putLong("site", site);
                t.putString("do", b.action);
                RpNet.toServer(RpActions.RESTORATION_ACTION, t);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, lines.size() - ROWS), scroll - (int) Math.signum(delta)));
        return true;
    }
}

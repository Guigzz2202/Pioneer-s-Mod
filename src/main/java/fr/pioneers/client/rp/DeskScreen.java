package fr.pioneers.client.rp;

import fr.pioneers.core.profile.PioneerDefs;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
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

/**
 * 1.9 (MAJ RP) : Pupitre du Restaurateur. Trois temps : 1 choisir la nouvelle vocation (cartes, l'actuelle est marquee), 2 le cout (ce
 * qu'on a / ce qu'il faut, delai, penalite) et la double confirmation (taper le nom de la vocation puis sceller), 3 la ceremonie (barre de
 * 10 s, interrompue si on s'eloigne ou si on est blesse). Le serveur revalide tout.
 */
public class DeskScreen extends PioneerScreen {

    private record CostLine(String id, int need, int have) {
    }

    private final long desk;
    private final String current;
    private final long cooldown;
    private final List<CostLine> cost = new ArrayList<>();
    private final int categories, categoriesNeed;
    private final long tokens, balance;
    private final boolean economy;
    private int step;
    private String chosen = "";
    private EditBox typed;
    private boolean armed;
    private int ceremonyTicks, ceremonyTotal;
    private boolean interrupted;

    public DeskScreen(CompoundTag t) {
        super(Component.translatable("block.pioneers.restorer_desk"));
        desk = t.getLong("desk");
        current = t.getString("vocation");
        cooldown = t.getLong("cooldown");
        ListTag l = t.getList("cost", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            cost.add(new CostLine(c.getString("id"), c.getInt("need"), c.getInt("have")));
        }
        categories = t.getInt("categories");
        categoriesNeed = t.getInt("categoriesNeed");
        economy = t.contains("tokens");
        tokens = t.getLong("tokens");
        balance = t.getLong("balance");
    }

    @Override
    protected void init() {
        super.init();
        String v = typed != null ? typed.getValue() : "";
        typed = new EditBox(font, left + 200, top + 168, 160, 16, Component.translatable("pioneer.pioneers.desk.type_name", ""));
        typed.setMaxLength(32);
        typed.setValue(v);
        typed.setResponder(s -> armed = false);
        addRenderableWidget(typed);
        typed.visible = step == 1;
    }

    private void go(int s) {
        step = s;
        armed = false;
        typed.visible = step == 1;
        if (step != 1) {
            typed.setValue("");
        }
    }

    /** Appele par {@link RpClient} quand le serveur lance la ceremonie. */
    public void ceremony(int ticks) {
        ceremonyTotal = Math.max(1, ticks);
        ceremonyTicks = 0;
        interrupted = false;
        go(2);
    }

    /** Refus du serveur ou ceremonie interrompue : retour au cout, rien n'a ete pris. */
    public void cancelled() {
        if (step == 2) {
            interrupted = true;
        }
        go(chosen.isEmpty() ? 0 : 1);
    }

    @Override
    public void tick() {
        super.tick();
        if (step == 2 && ceremonyTicks < ceremonyTotal) {
            ceremonyTicks++;
            if (ceremonyTicks % 20 == 0) {
                RpClient.play(fr.pioneers.core.profile.RpSounds.QUILL.get(), 0.9F + ceremonyTicks / (float) ceremonyTotal * 0.3F);
            }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return step != 2;
    }

    private boolean paid() {
        for (CostLine c : cost) {
            if (c.have() < c.need()) {
                return false;
            }
        }
        return economy ? balance >= tokens : categories >= categoriesNeed;
    }

    private static String fold(String s) {
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private boolean typedOk() {
        return !chosen.isEmpty() && fold(typed.getValue()).equals(fold(I18n.get("vocation.pioneers." + chosen)));
    }

    @Override
    protected void drawContents(GuiGraphics g, int mx, int my, float pt) {
        fitCentered(g, Component.translatable("block.pioneers.restorer_desk"), PAGE_W / 2, 8, PAGE_W - 24, INK, 1F);
        switch (step) {
            case 0 -> drawChoice(g, mx, my);
            case 1 -> drawCost(g, mx, my);
            default -> drawCeremony(g, mx, my);
        }
    }

    // ------------------------------------------------------------------ 1. choix
    /** 1.9.1 : haut de la grille des vocations du Pupitre. */
    public static final int CHOICE_Y = 34;

    private void drawChoice(GuiGraphics g, int mx, int my) {
        fitCentered(g, Component.translatable("pioneer.pioneers.desk.choose"), PAGE_W / 2, 20, PAGE_W - 24, DIM, 1F);
        //1.9.1 (retours de Guillaume) : onze cartes en 4 x 3 et le panneau de lecture (l'actuelle en cadre dore, non choisissable)
        int h = vocationPicker(g, mx, my, 9, CHOICE_Y, PAGE_W - 9, chosen, current, id -> {
            if (!id.equals(current)) {
                chosen = id;
            }
        });
        if (cooldown > 0) {
            fitCentered(g, Component.translatable("pioneer.pioneers.desk.cooldown", cooldown), PAGE_W / 2, CHOICE_Y + h + 4, PAGE_W - 24, MINUS, 1F);
        }
        boolean ok = !chosen.isEmpty() && !chosen.equals(current);
        button(g, mx, my, PAGE_W - 110, PAGE_H - 24, 96, 16, Component.translatable("pioneer.pioneers.next"), ok, () -> go(1));
        if (!chosen.isEmpty()) {
            String id = chosen;
            button(g, mx, my, 14, PAGE_H - 24, 100, 16, Component.translatable("pioneer.pioneers.more"), true,
                  () -> minecraft.setScreen(new LoreScreen(this, "trade_" + id)));
        }
    }

    // ------------------------------------------------------------------ 2. cout et confirmation
    private void drawCost(GuiGraphics g, int mx, int my) {
        PioneerDefs.Vocation from = PioneerDefs.get().vocation(current), to = PioneerDefs.get().vocation(chosen);
        //Ancienne -> nouvelle (1.9.1 : noms bornes a leur colonne, texte de penalite selon l'ancienne vocation)
        icon(g, RpGui.emblem(current), 30, 26, 32);
        fitCentered(g, RpGui.vocationName(current), 46, 62, 84, DIM, 1F);
        centered(g, Component.literal("→"), 92, 38, INK);
        icon(g, RpGui.emblem(chosen), 122, 26, 32);
        fitCentered(g, RpGui.vocationName(chosen), 138, 62, 84, INK, 1F);
        if (to != null) {
            tooltip(mx, my, 122, 26, 32, 32, RpGui.vocationTooltip(to));
        }
        int y0 = 76;
        if (from != null) {
            tooltip(mx, my, 30, 26, 32, 32, RpGui.vocationTooltip(from));
            if (from.affinities().size() >= 3) { //1.0.5-beta (lot 10) : l'Eclaireur a trois affinites (Combat, Recuperation, Chasse)
                Component a = RpGui.skillName(from.affinities().get(0)), b = RpGui.skillName(from.affinities().get(1)),
                      c = RpGui.skillName(from.affinities().get(2));
                y0 += wrap(g, Component.translatable("pioneer.pioneers.desk.penalty3", a, b, c), 14, y0, 170, MINUS, 1F, 3) + 6;
            } else if (from.affinities().size() >= 2) {
                Component a = RpGui.skillName(from.affinities().get(0)), b = RpGui.skillName(from.affinities().get(1));
                y0 += wrap(g, Component.translatable("pioneer.pioneers.desk.penalty", a, b), 14, y0, 170, MINUS, 1F, 3) + 6;
            } else { //1.9.1 : le Polyvalent n'a pas d'affinite : rien a perdre
                y0 += wrap(g, Component.translatable("pioneer.pioneers.desk.no_penalty"), 14, y0, 170, PLUS, 1F, 3) + 6;
            }
        }
        wrap(g, Component.translatable("pioneer.pioneers.desk.warning"), 14, Math.max(104, y0), 170, TEXT, 1F, 8);
        //Cout
        fit(g, Component.translatable("pioneer.pioneers.desk.cost"), 200, 24, 170, INK);
        separator(g, 200, 34, 170);
        int y = 40;
        long t = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        for (CostLine c : cost) {
            ItemStack s = stackOf(c.id(), t);
            g.renderItem(s, 200, y);
            boolean ok = c.have() >= c.need();
            Component name = c.id().startsWith("#") ? Component.translatable("pioneer.pioneers.desk.tag." + c.id().substring(1).replace(':', '.')
                  .replace('/', '.')) : s.getHoverName();
            Component count = Component.literal(c.have() + " / " + c.need());
            int cw = fitRight(g, count, 366, y + 4, 60, ok ? PLUS : MINUS, 1F);
            tooltip(mx, my, 200, y, 170, 16, name);
            fit(g, name, 220, y + 4, 366 - cw - 4 - 220, TEXT);
            y += 18;
        }
        Component label, value;
        boolean ok;
        if (economy) {
            label = Component.translatable("pioneer.pioneers.desk.tokens");
            value = Component.literal(balance + " / " + tokens);
            ok = balance >= tokens;
        } else {
            label = Component.translatable("pioneer.pioneers.desk.categories");
            value = Component.literal(categories + " / " + categoriesNeed);
            ok = categories >= categoriesNeed;
            tooltip(mx, my, 200, y, 170, 16, Component.translatable("pioneer.pioneers.desk.categories_help"));
        }
        int vw = fitRight(g, value, 366, y + 4, 60, ok ? PLUS : MINUS, 1F);
        fit(g, label, 220, y + 4, 366 - vw - 4 - 220, TEXT);
        y += 20;
        Component status = cooldown > 0 ? Component.translatable("pioneer.pioneers.desk.cooldown", cooldown) : !paid()
              ? Component.translatable("pioneer.pioneers.desk.missing") : interrupted ? Component.translatable("pioneer.pioneers.desk.interrupted") : null;
        if (status != null) {
            wrap(g, status, 200, y, 170, MINUS, 1F, Math.max(1, (148 - y) / 10));
        }
        //consigne sur deux lignes au plus, juste au-dessus du champ
        Component ask = Component.translatable("pioneer.pioneers.desk.type_name", RpGui.vocationName(chosen));
        int ah = Math.min(2, lines(ask, 166, RpGui.MIN_SCALE).size()) * lineStep(RpGui.MIN_SCALE);
        wrap(g, ask, 200, 166 - ah, 166, DIM, RpGui.MIN_SCALE, 2);
        boolean can = cooldown <= 0 && paid() && typedOk();
        button(g, mx, my, 14, PAGE_H - 24, 80, 16, Component.translatable("pioneer.pioneers.previous"), true, () -> go(0));
        Component seal = Component.translatable(armed ? "pioneer.pioneers.desk.confirm" : "pioneer.pioneers.desk.seal");
        button(g, mx, my, PAGE_W - 150, PAGE_H - 24, 136, 16, seal, can, () -> {
            if (!armed) {
                armed = true;
                return;
            }
            CompoundTag r = new CompoundTag();
            r.putLong("desk", desk);
            r.putString("vocation", chosen);
            r.putBoolean("confirm", true);
            RpNet.toServer(RpActions.RECONVERT, r);
            armed = false;
        });
    }

    private static ItemStack stackOf(String id, long time) {
        if (id.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation(id.substring(1)));
            List<Holder<Item>> all = new ArrayList<>();
            BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(all::add);
            return all.isEmpty() ? new ItemStack(Items.BARRIER) : new ItemStack(all.get((int) (time / 20 % all.size())).value());
        }
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        return new ItemStack(item == Items.AIR ? Items.BARRIER : item);
    }

    // ------------------------------------------------------------------ 3. ceremonie
    private void drawCeremony(GuiGraphics g, int mx, int my) {
        icon(g, RpGui.emblem(chosen), PAGE_W / 2 - 16, 50, 32);
        fitCentered(g, RpGui.vocationName(chosen), PAGE_W / 2, 88, PAGE_W - 40, INK, 1F);
        fitCentered(g, Component.translatable("pioneer.pioneers.desk.ceremony"), PAGE_W / 2, 110, PAGE_W - 40, TEXT, 1F);
        double frac = ceremonyTicks / (double) Math.max(1, ceremonyTotal);
        bar(g, 92, 130, 200, 6, frac, GOLD);
        fitCentered(g, Component.translatable("pioneer.pioneers.desk.stay"), PAGE_W / 2, 144, PAGE_W - 40, DIM, 1F);
        button(g, mx, my, PAGE_W / 2 - 50, PAGE_H - 30, 100, 16, Component.translatable("pioneer.pioneers.desk.interrupt"), true,
              () -> RpNet.toServer(RpActions.CANCEL_CEREMONY, new CompoundTag()));
    }
}

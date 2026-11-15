package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.tech.bronze.autofoundry.AutoFoundryTile;
import fr.pioneers.tech.bronze.autofoundry.AutoMoldBlock;
import fr.pioneers.tech.bronze.autofoundry.AutoMoldTile;
import fr.pioneers.tech.bronze.autofoundry.AutoMoldUnit;
import fr.pioneers.tech.bronze.autofoundry.FoundryLayout;
import fr.pioneers.tech.bronze.autofoundry.FoundryUnit;
import fr.pioneers.tech.bronze.foundry.Metals;
import fr.pioneers.tech.bronze.client.AutoMoldRenderer;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.core.GuiMachine.Row;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 1.0.2-beta (lot 4) : ecrans de la FONDERIE AUTOMATIQUE, interface standard v2 :
 * <ul>
 *     <li>{@link #FOUNDRY} : corps du controleur dans la vue maison {@code house} (voile et erreur commune quand la structure n'est pas formee,
 *     onglet Structure, carre du selecteur d'alliage) : foyer a la place de l'energie motrice (thermometre, trait de la coulee), combustible
 *     sous la flamme, CARTE DE LA CUVE (resultat, total / contenance, barre de composition, chaque metal EN mB), 4 cases de metal avec leur
 *     fusion, case Recette ; ecran d'etat (taille, moules libres, air) ; rangee : interrupteur « Auto » et chaine des moules ;</li>
 *     <li>{@link #AUTO_MOLD} : moule automatique : figeage a la place de l'energie motrice, 6 boutons d'EMPREINTE (moules d'argile en icone),
 *     fleche du figeage, piece, ecran (metal, quantite, temps).</li>
 * </ul>
 * Valeurs lues par leur nom ; tiles lues par {@code StorageViews.as} (pas d'{@code instanceof}).
 */
final class FoundryViews {

    private static final int FIRE = 0xFF7A1F, RED = 0xE04848, OK = 0x5FD07A;

    private FoundryViews() {
    }

    // ------------------------------------------------------------------
    // Fonderie automatique (corps de la vue maison)
    // ------------------------------------------------------------------

    static final MachineScreens.View FOUNDRY = new AgeViews.Base() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            AutoFoundryTile tile = StorageViews.as(AutoFoundryTile.class, be);
            if (tile == null) {
                return;
            }
            double c = gui.value(UiValues.HEAT) / 10.0, reach = gui.value(UiValues.REACH) / 10.0, fire = gui.value(UiValues.BURN) / 1000.0;
            double air = gui.value(UiValues.BLAST) / 1000.0;
            Map<String, Integer> mix = tile.mix();
            int total = mix.values().stream().mapToInt(Integer::intValue).sum();
            int capacity = (int) Math.max(1, gui.value(FoundryUnit.CAPACITY));
            String result = Metals.resolve(mix);
            int pourC = mix.isEmpty() ? 0 : Metals.pourC(mix);
            AgeViews.fireGauge(gui, g, mouseX, mouseY, StandardLayout.POWER, c, pourC, Component.translatable("gui.pioneers.foundry.reach", AgeViews.celsius(reach)));
            GuiMachine.flame(g, FoundryUnit.FUEL_X + 1, FoundryUnit.FUEL_Y - 18, fire);
            bath(gui, g, FoundryUnit.BATH, mix, total, capacity, result, c >= pourC && !mix.isEmpty(), pourC);
            //1.0.3-beta (lot 7b) : plan de coulee de la regle commune, cases qui attendent un autre metal (barre ambre)
            Metals.Plan plan = tile.foundry().plan();
            SourceViews.planLines(gui, g, mouseX, mouseY, FoundryUnit.BATH, plan);
            String waits = "";
            for (int i = 0; i < FoundryUnit.METAL_SLOTS; i++) {
                int[] p = FoundryUnit.inPos(i);
                String w = tile.foundry().waitsFor(tile.foundry().input(i));
                SourceViews.slotBar(g, p[0], p[1] + 17, w.isEmpty() ? gui.value(UiValues.melt(i)) / 1000.0 : 1, w.isEmpty() ? FIRE : SourceViews.WAIT);
                waits = waits.isEmpty() ? w : waits;
            }
            int status = (int) gui.value(UiValues.STATUS);
            int size = (int) gui.value(FoundryUnit.SIZE);
            long molds = gui.value(FoundryUnit.MOLDS), free = gui.value(FoundryUnit.FREE);
            Rect s = StandardLayout.shortScreen(StandardLayout.WIDTH);
            List<Row> rows = new ArrayList<>();
            rows.add(Row.of("gui.pioneers.auto_foundry.size", size < 0 ? Component.translatable("gui.pioneers.multiblock.not_formed")
                  : Component.translatable("gui.pioneers.auto_foundry.size." + FoundryLayout.Size.of(size).key())));
            rows.add(Row.of("industry.pioneers.gui.air", GuiMachine.percent(air)));
            int chip = status == FoundryUnit.POURING ? FIRE : status == FoundryUnit.UNFORMED || status == FoundryUnit.MIXED || status == FoundryUnit.NO_MOLD
                  ? RED : 0x8FA3A0;
            Component title = status == FoundryUnit.DOSING && !plan.waiting().isEmpty() ? Component.translatable("gui.pioneers.bath.waits", Metals.name(plan.waiting()))
                  : !waits.isEmpty() && status != FoundryUnit.POURING ? Component.translatable("gui.pioneers.bath.waits", Metals.name(waits))
                  : Component.translatable("gui.pioneers.auto_foundry.status." + status);
            gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, title, AgeViews.celsius(c), -1, FIRE, rows, false);
            //Rangee : interrupteur « Auto », chaine des moules (libres / atteints)
            Rect r = StandardLayout.settingRow(StandardLayout.WIDTH);
            autoSwitch(gui, g, mouseX, mouseY, r.x(), r.y(), 40, r.h(), gui.value(UiValues.AUTO) != 0);
            //1.0.3-beta (lot 7b) : chaine des moules raccourcie, bouton « Vider » au bout de la rangee
            int cw = r.w() - 42 - SourceViews.DUMP_W - 2;
            gui.screenAt(g, r.x() + 42, r.y(), cw, r.h());
            Component chain = molds == 0 ? Component.translatable("gui.pioneers.auto_foundry.no_mold")
                  : Component.translatable("gui.pioneers.auto_foundry.molds", free, molds);
            GuiDraw.center(g, gui.font(), chain, r.x() + 42 + cw / 2F, r.y() + (r.h() - 6.4F) / 2F, 0.7F,
                  molds == 0 ? 0xFFFF6A5A : free > 0 ? 0xFF000000 | OK : PiMenuScreen.TEXT, cw - 6);
            SourceViews.dumpButton(gui, g, mouseX, mouseY, r.x() + r.w() - SourceViews.DUMP_W, r.y(), r.h(), total > 0, AutoFoundryTile.DUMP);
            if (gui.over(mouseX, mouseY, r.x() + 42, r.y(), cw, r.h())) {
                gui.tooltip(List.of(Component.translatable("gui.pioneers.auto_foundry.chain_tip").withStyle(ChatFormatting.GRAY),
                      Component.translatable("gui.pioneers.auto_foundry.chain_tip2").withStyle(ChatFormatting.DARK_GRAY)));
            }
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            gui.slotTip(mouseX, mouseY, FoundryUnit.FUEL_X, FoundryUnit.FUEL_Y, "gui.pioneers.auto_foundry.fuel");
            for (int i = 0; i < FoundryUnit.METAL_SLOTS; i++) {
                int[] p = FoundryUnit.inPos(i);
                gui.slotTip(mouseX, mouseY, p[0], p[1], "gui.pioneers.foundry.metal");
            }
            return true;
        }

        @Override
        void info(GuiMachine gui, BlockEntity be, List<StandardView.InfoRow> rows) {
            rows.add(new StandardView.InfoRow("fire", Component.translatable("gui.pioneers.api.info.fire_value", AgeViews.celsius(FoundryUnit.BASE_C),
                  AgeViews.celsius(FoundryUnit.BASE_C + FoundryUnit.AIR_C)), 0xFFFF7A1F));
            rows.add(new StandardView.InfoRow("capacity", GuiMachine.literal(gui.value(FoundryUnit.CAPACITY) + " mB"), PiMenuScreen.TEXT));
            int size = (int) gui.value(FoundryUnit.SIZE);
            int speed = FoundryLayout.Size.of(Math.max(0, size)).speed;
            rows.add(new StandardView.InfoRow("speed", GuiMachine.literal("×" + speed + " · " + FoundryUnit.POUR_RATE * speed + " mB/t"), PiMenuScreen.TEXT));
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_auto_foundry";
        }
    };

    /** Carte de la cuve : resultat, total / contenance, barre de composition, legende EN mB par metal, etat liquide / fige. */
    private static void bath(GuiMachine gui, GuiGraphics g, Rect r, Map<String, Integer> mix, int total, int capacity, String result, boolean hot, int pourC) {
        int sx = r.x(), sy = r.y(), sw = r.w(), sh = r.h();
        gui.screenAt(g, sx, sy, sw, sh);
        Component name = mix.isEmpty() ? Component.translatable("gui.pioneers.foundry.empty") : Metals.nameOrMixed(result);
        GuiDraw.text(g, gui.font(), name, sx + 5, sy + 5, 0.8F, SourceViews.nameColour(mix, result), sw - 44);
        GuiDraw.right(g, gui.font(), Component.literal(fr.pioneers.core.Texts.format(total) + " / " + fr.pioneers.core.Texts.format(capacity)),
              sx + sw - 5, sy + 6, 0.55F, PiMenuScreen.SCREEN_TEXT);
        int bx = sx + 5, bw = sw - 10, by = sy + 16;
        g.fill(bx, by, bx + bw, by + 6, 0xFF05080B);
        int cx = bx, k = 0;
        for (Map.Entry<String, Integer> e : mix.entrySet()) {
            int w = k == mix.size() - 1 ? bx + (int) Math.round(bw * Math.min(1, total / (double) capacity)) - cx
                  : (int) Math.round(bw * e.getValue() / (double) capacity);
            g.fill(cx, by, cx + Math.max(1, w), by + 6, 0xFF000000 | Metals.colour(e.getKey()));
            cx += w;
            k++;
        }
        int ly = by + 9;
        for (Map.Entry<String, Integer> e : mix.entrySet()) {
            //1.0.3-beta (lot 7b) : la legende s'arrete au-dessus du plan de coulee
            if (ly + 7 > SourceViews.planTop(r)) {
                break;
            }
            g.fill(sx + 5, ly + 1, sx + 9, ly + 5, 0xFF000000 | Metals.colour(e.getKey()));
            GuiDraw.text(g, gui.font(), Metals.name(e.getKey()), sx + 12, ly, 0.6F, PiMenuScreen.DIM, 44);
            GuiDraw.right(g, gui.font(), Component.literal(fr.pioneers.core.Texts.format(e.getValue()) + " mB"), sx + sw - 5, ly, 0.6F, PiMenuScreen.TEXT);
            ly += 7;
        }
        if (!mix.isEmpty()) {
            GuiDraw.text(g, gui.font(), Component.translatable(hot ? "gui.pioneers.foundry.liquid" : "gui.pioneers.foundry.solid", AgeViews.celsius(pourC)),
                  sx + 5, sy + sh - 7, 0.55F, hot ? 0xFFFFB050 : 0xFF8FA3A0, sw - 10);
        }
    }

    /** Interrupteur de la coulee automatique (action {@link AutoFoundryTile#AUTO}, valeur nommee {@link UiValues#AUTO}). */
    private static void autoSwitch(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, boolean on) {
        boolean hover = gui.over(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, 0xFF14181E);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, on ? 0xFF1E3A2A : hover ? 0xFF323A45 : 0xFF262C35);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, on ? 0xFF2F5A40 : 0xFF55606F);
        if (on) {
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF3FD06A);
        }
        int lx = x + 4, ly = y + (h - 5) / 2;
        g.fill(lx - 1, ly - 1, lx + 6, ly + 6, 0xFF0B1016);
        g.fill(lx, ly, lx + 5, ly + 5, on ? 0xFF3FD06A : 0xFF3A4048);
        if (on) {
            g.fill(lx, ly, lx + 2, ly + 2, 0xFFB8FFD0);
        }
        GuiDraw.center(g, gui.font(), Component.translatable("gui.pioneers.foundry.auto"), x + 7 + (w - 7) / 2F, y + (h - 6.4F) / 2F, 0.8F,
              on || hover ? 0xFFFFFFFF : PiMenuScreen.TEXT, w - 10);
        gui.click(x, y, w, h, () -> gui.send(AutoFoundryTile.AUTO));
        if (hover) {
            gui.tooltip(List.of(Component.translatable(on ? "gui.pioneers.auto_foundry.auto_on" : "gui.pioneers.auto_foundry.auto_off")
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                  Component.translatable("gui.pioneers.auto_foundry.auto_tip").withStyle(ChatFormatting.GRAY),
                  Component.translatable("gui.pioneers.foundry.auto_click").withStyle(ChatFormatting.DARK_GRAY)));
        }
    }

    // ------------------------------------------------------------------
    // Moule automatique
    // ------------------------------------------------------------------

    static final MachineScreens.View AUTO_MOLD = new AgeViews.Base() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            AutoMoldTile tile = StorageViews.as(AutoMoldTile.class, be);
            if (tile == null) {
                return;
            }
            AutoMoldUnit unit = tile.mold();
            double solid = gui.value(UiValues.PROGRESS_X1000) / 1000.0;
            int status = (int) gui.value(UiValues.STATUS);
            long mb = gui.value(UiValues.MOLTEN), need = gui.value(AutoMoldUnit.NEED);
            int form = (int) gui.value(AutoMoldUnit.FORM);
            boolean full = mb > 0;
            int colour = full ? Metals.colour(unit.metal()) : 0x5A2A20;
            SourceViews.ratioGauge(gui, g, mouseX, mouseY, StandardLayout.POWER, full ? 1 - solid * 0.6 : 0, colour, full ? 0xFFB050 : 0x5A2A20,
                  List.of(Component.translatable("gui.pioneers.auto_mold.status." + status), GuiMachine.percent(solid).copy().withStyle(ChatFormatting.GRAY)));
            //Boutons d'empreinte : le moule d'argile de la forme en icone ; choisie = cadre allume ; seulement moule vide
            gui.screenAt(g, AutoMoldUnit.FORMS.x(), AutoMoldUnit.FORMS.y(), AutoMoldUnit.FORMS.w(), AutoMoldUnit.FORMS.h());
            for (AutoMoldBlock.Form f : AutoMoldBlock.Form.values()) {
                Rect b = AutoMoldTile.formButton(f.ordinal());
                boolean chosen = f.ordinal() == form, hover = gui.over(mouseX, mouseY, b.x(), b.y(), b.w(), b.h());
                g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), chosen ? 0xFF2A3A30 : hover && !full ? 0xFF323A45 : 0xFF161D24);
                if (chosen) {
                    g.renderOutline(b.x(), b.y(), b.w(), b.h(), 0xFF000000 | OK);
                }
                g.renderItem(AutoMoldRenderer.icon(f), b.x() + 1, b.y() + 1);
                if (!chosen && !full) {
                    gui.click(b.x(), b.y(), b.w(), b.h(), () -> gui.send(AutoMoldTile.FORM_ACTION + f.ordinal()));
                }
                if (hover) {
                    List<Component> tip = new ArrayList<>();
                    tip.add(Component.translatable("gui.pioneers.auto_mold.form." + f.key()).withStyle(chosen ? ChatFormatting.GREEN : ChatFormatting.WHITE));
                    if (f == AutoMoldBlock.Form.ANVIL) {
                        //1.0.2-beta (lot 5) : l'enclume se coule seulement en fer (le bronze est refuse)
                        tip.add(Component.translatable("gui.pioneers.auto_mold.anvil_iron").withStyle(ChatFormatting.GOLD));
                    }
                    tip.add(Component.translatable(chosen ? "gui.pioneers.auto_mold.chosen" : full ? "gui.pioneers.auto_mold.busy"
                          : "gui.pioneers.auto_mold.choose").withStyle(ChatFormatting.GRAY));
                    gui.tooltip(tip);
                }
            }
            StandardView.arrow(g, AutoMoldUnit.ARROW, status == AutoMoldUnit.COOLING || status == AutoMoldUnit.BLOCKED ? solid : 0, 0x8FA3A0);
            List<Row> rows = new ArrayList<>();
            rows.add(new Row(Component.translatable("gui.pioneers.auto_mold.form"), Component.translatable("gui.pioneers.auto_mold.form."
                  + AutoMoldBlock.Form.of(form).key()), PiMenuScreen.SCREEN_TEXT));
            if (AutoMoldBlock.Form.of(form) == AutoMoldBlock.Form.ANVIL && !full) {
                rows.add(new Row(Component.translatable("gui.pioneers.foundry.metal"), Component.translatable("gui.pioneers.auto_mold.anvil_iron_only"),
                      PiMenuScreen.SCREEN_TEXT));
            }
            if (full) {
                rows.add(new Row(Component.translatable("gui.pioneers.foundry.metal"), Metals.nameOrMixed(unit.metal()), PiMenuScreen.SCREEN_TEXT));
                rows.add(new Row(Component.translatable("gui.pioneers.foundry.amount"), Component.literal(mb + " / " + need + " mB"), PiMenuScreen.SCREEN_TEXT));
            }
            Rect s = StandardLayout.screen(StandardLayout.WIDTH);
            int chip = status == AutoMoldUnit.FILLING || status == AutoMoldUnit.COOLING ? FIRE : status == AutoMoldUnit.BLOCKED ? RED : 0;
            gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, Component.translatable("gui.pioneers.auto_mold.status." + status),
                  status == AutoMoldUnit.COOLING ? GuiMachine.percent(solid) : Component.empty(), status == AutoMoldUnit.COOLING ? solid : -1, 0x8FA3A0,
                  rows, false);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            gui.slotTip(mouseX, mouseY, AutoMoldUnit.OUT_X, AutoMoldUnit.OUT_Y, "gui.pioneers.auto_mold.out");
            return true;
        }

        @Override
        void info(GuiMachine gui, BlockEntity be, List<StandardView.InfoRow> rows) {
            rows.add(new StandardView.InfoRow("poured_by", Component.translatable("gui.pioneers.api.info.auto_mold_value"), PiMenuScreen.TEXT));
            long ticks = gui.value(UiValues.DURATION);
            if (ticks > 0) {
                rows.add(new StandardView.InfoRow("cool_time", GuiMachine.literal(String.format(java.util.Locale.ROOT, "%.1f s", ticks / 20.0)), PiMenuScreen.TEXT));
            }
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_auto_mold";
        }
    };
}

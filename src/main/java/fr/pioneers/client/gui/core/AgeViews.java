package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.MachineSpec;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.core.Texts;
import fr.pioneers.tech.forge.ForgeHeat;
import fr.pioneers.tech.forge.HeatedMetal;
import fr.pioneers.tech.forge.smithing.ForgeHearthTile;
import fr.pioneers.tech.forge.smithing.HearthUnit;
import fr.pioneers.tech.industry.atelier.DynamoTile;
import fr.pioneers.tech.industry.atelier.DynamoUnit;
import fr.pioneers.tech.industry.bronze.CokeOvenProcess;
import fr.pioneers.tech.industry.bronze.CokeOvenTile;
import fr.pioneers.tech.kinetics.KineticTile;
import fr.pioneers.tech.kinetics.machines.MachineType;
import fr.pioneers.tech.kinetics.machines.MechanicalMachineTile;
import fr.pioneers.tech.kinetics.machines.MechanicalProcess;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * API Machine (lot 7) : INTERFACE STANDARD v2 des machines de l'age mecanique et du bronze (decision 1 de Guillaume : meme fiche, meme
 * interface, energie motrice rotation / vapeur / feu au lieu du FE, pastille d'age au lieu du MK). Meme grammaire que {@link StandardView} :
 * a gauche la jauge de l'energie motrice (vitesse avec la zone utile minimum → 1,5 x nominale, manometre avec le repere du minimum, foyer
 * avec la temperature demandee), cases et fleche de {@link StandardLayout}, case « Recette » (carre du selecteur), ecran d'etat de 52 px aux
 * valeurs NOMMEES ({@link UiValues}), onglet Infos (age, energie motrice, chiffres de la machine). Vues : {@code mechanical} (machines
 * mecaniques), {@code steam_process} (machines a vapeur), {@code steam_blower}, {@code coke_oven}, {@code dynamo} (1.0.2-beta : la forge au charbon est retiree).
 */
final class AgeViews {

    private static final int GREEN = 0x3FD0A0, AMBER = 0xE0B040, RED = 0xE04848, GREY = 0x8A9099;

    static final MachineScreens.View MECHANICAL = new Mechanical();
    static final MachineScreens.View STEAM_BLOWER = new Blower();
    static final MachineScreens.View COKE_OVEN = new Coke();
    static final MachineScreens.View HEARTH = new Hearth();
    static final MachineScreens.View DYNAMO = new Dynamo();

    private AgeViews() {
    }

    // ------------------------------------------------------------------
    // Commun
    // ------------------------------------------------------------------

    abstract static class Base implements MachineScreens.View {

        @Override
        public boolean standard() {
            return true;
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_age";
        }

        /** Lignes de l'onglet Infos propres a la machine (apres l'age). */
        abstract void info(GuiMachine gui, BlockEntity be, List<StandardView.InfoRow> rows);

        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity be) {
            List<StandardView.InfoRow> rows = new ArrayList<>();
            MachineSpec spec = MachineScreens.spec(be);
            if (spec != null && spec.age() != null) {
                rows.add(new StandardView.InfoRow("age", Component.translatable("tooltip.pioneers.api.age." + spec.age()), StandardView.ageColor(spec.age())));
            }
            info(gui, be, rows);
            return rows;
        }
    }

    static Component rpm(double v) {
        return GuiMachine.literal(String.format(Locale.ROOT, Math.abs(v) >= 10 ? "%.0f tr/min" : "%.1f tr/min", Math.abs(v)));
    }

    /** Quantite de vapeur (mB). */
    static Component mb(double v) {
        return GuiMachine.literal(fr.pioneers.core.Texts.format(Math.round(v)) + " mB");
    }

    /** Debit de vapeur (mB/t). */
    static Component mbt(double v) {
        return GuiMachine.literal(String.format(Locale.ROOT, v >= 10 || v == Math.rint(v) ? "%.0f mB/t" : "%.1f mB/t", v));
    }

    static Component celsius(double c) {
        return GuiMachine.literal(String.format(Locale.ROOT, "%.0f °C", c));
    }

    /**
     * Jauge de vitesse (energie motrice de rotation) : zone utile verte de {@code min} a 1,5 x {@code nominal} sur le flanc, remplissage a la
     * vitesse, repere du regime nominal ; echelle jusqu'a {@code full}.
     */
    static void speedGauge(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, Rect r, double speed, double min, double nominal, double full) {
        int x = r.x(), y = r.y(), w = r.w(), h = r.h();
        gui.frame(g, x, y, w, h);
        int inner = h - 2;
        double s = Math.abs(speed);
        int filled = (int) Math.round(inner * Math.max(0, Math.min(1, s / full)));
        for (int i = 0; i < filled; i++) {
            double v = (i + 0.5) / inner * full;
            int c = v < min ? 0xFF8A6A4A : v <= nominal * 1.5 ? 0xFF3FD0A0 : 0xFFE04848;
            g.fill(x + 3, y + h - 2 - i, x + w - 1, y + h - 1 - i, c);
        }
        //Zone utile sur le flanc gauche, repere du regime nominal
        int z0 = y + h - 1 - (int) Math.round(inner * Math.min(1, min / full)), z1 = y + h - 1 - (int) Math.round(inner * Math.min(1, nominal * 1.5 / full));
        g.fill(x + 1, z1, x + 2, z0, 0xFF3FD0A0);
        int n = y + h - 1 - (int) Math.round(inner * Math.min(1, nominal / full));
        g.fill(x - 1, n, x + w + 1, n + 1, 0xFFFFFFFF);
        GuiMachine.ticks(g, x, y, w, h);
        if (gui.over(mouseX, mouseY, x, y, w, h)) {
            gui.tooltip(List.of(Component.translatable("gui.pioneers.kinetics.speed_tip", KineticTile.fmt(s), KineticTile.fmt(nominal), KineticTile.fmt(min))));
        }
    }

    /**
     * 1.0.2-beta (lot 3, plus de pression) : RESERVE DE VAPEUR (energie motrice de vapeur) : jauge haute remplie de vapeur (mB en reserve sur
     * la contenance), trait de la prise d'un tick de travail ; infobulle : reserve et debit consomme en marche.
     */
    static void steamGauge(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, Rect r, double stored, double capacity, double use) {
        int x = r.x(), y = r.y(), w = r.w(), h = r.h();
        gui.frame(g, x, y, w, h);
        double ratio = capacity <= 0 ? 0 : Math.max(0, Math.min(1, stored / capacity));
        int filled = (int) Math.round((h - 2) * ratio);
        for (int i = 0; i < filled; i++) {
            //Vapeur : du gris chaud au blanc, volutes plus claires une rangee sur trois
            int c = GuiMachine.lerp(0xFF8E959C, 0xFFE8EDF0, i / (double) Math.max(1, h - 2));
            g.fill(x + 1, y + h - 2 - i, x + w - 1, y + h - 1 - i, (i % 3 == 0) ? GuiMachine.lerp(c, 0xFFFFFFFF, 0.25) : c);
        }
        if (filled > 0) {
            g.fill(x + 1, y + h - 1 - filled, x + w - 1, y + h - filled, 0xFFFFFFFF);
        }
        g.fill(x + 2, y + 1, x + 3, y + h - 1, 0x30FFFFFF);
        GuiMachine.ticks(g, x, y, w, h);
        boolean short_ = stored < use;
        if (short_) {
            g.fill(x - 1, y + h - 2, x + w + 1, y + h - 1, 0xFFE04848);
        }
        if (gui.over(mouseX, mouseY, x, y, w, h)) {
            gui.tooltip(List.of(Component.translatable("gui.pioneers.steam.reserve", mb(stored), mb(capacity)),
                  Component.translatable("gui.pioneers.steam.use_tip", mbt(use)).withStyle(ChatFormatting.GRAY)));
        }
    }

    /** Foyer (energie motrice du feu) : thermometre, trait de la temperature demandee. */
    static void fireGauge(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, Rect r, double celsius, int need, Component hint) {
        fireGauge(gui, g, mouseX, mouseY, r, celsius, need, hint, 27, 1_527);
    }

    /** 1.0.3-beta (correctif) : jauge de feu a echelle choisie, en degres Celsius (la chaudiere ne depasse pas 100 C). */
    static void fireGauge(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, Rect r, double celsius, int need, Component hint, double minC,
          double maxC) {
        gui.thermometer(g, mouseX, mouseY, r.x(), r.y(), r.w(), r.h(), celsius + 273, need > 0 ? need + 273 : 0,
              List.of(Component.translatable("industry.pioneers.gui.forge_temperature", celsius(celsius)),
                    need > 0 ? Component.translatable("industry.pioneers.gui.forge_needs", celsius(need)).withStyle(ChatFormatting.GRAY)
                          : hint.copy().withStyle(ChatFormatting.GRAY)), minC + 273, maxC + 273);
    }

    static void slotTips(GuiMachine gui, int mouseX, int mouseY, StandardLayout l) {
        for (int[] p : l.itemIn) {
            gui.slotTip(mouseX, mouseY, p[0], p[1], "gui.pioneers.machine.in");
        }
        for (int[] p : l.itemOut) {
            gui.slotTip(mouseX, mouseY, p[0], p[1], "gui.pioneers.machine.out");
        }
    }

    /** Rangee de reglage « < libelle > » (gabarit du marteau-pilon). */
    static void settingRow(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, Rect r, Component label, List<Component> tip, int prev, int next) {
        gui.steelButton(g, mouseX, mouseY, r.x(), r.y() + 1, 12, 12, "<", () -> gui.send(prev));
        gui.screenAt(g, r.x() + 14, r.y() + 1, r.w() - 28, 12);
        GuiDraw.center(g, gui.font(), label, r.x() + r.w() / 2F, r.y() + 4, 0.6F, PiMenuScreen.TEXT, r.w() - 32);
        gui.steelButton(g, mouseX, mouseY, r.x() + r.w() - 12, r.y() + 1, 12, 12, ">", () -> gui.send(next));
        if (gui.over(mouseX, mouseY, r.x() + 14, r.y(), r.w() - 28, 14)) {
            gui.tooltip(tip);
        }
    }

    // ------------------------------------------------------------------
    // Machines mecaniques (meule, tamis, concasseur, scie, tour, marteau-pilon, perceuse, noria, soufflet)
    // ------------------------------------------------------------------

    private static final class Mechanical extends Base {

        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            MechanicalMachineTile tile = StorageViews.as(MechanicalMachineTile.class, be);
            if (tile == null) {
                return;
            }
            MechanicalProcess unit = tile.process();
            MachineType type = unit.type;
            StandardLayout l = unit.layout;
            double speed = gui.value(UiValues.SPEED) / 100.0, torque = gui.value(UiValues.TORQUE) / 10.0, progress = gui.value(UiValues.PROGRESS_X1000) / 1000.0;
            double rate = gui.value(UiValues.RATE) / 100.0, turns = gui.value(UiValues.TURNS) / 100.0;
            int status = (int) gui.value(UiValues.STATUS);
            speedGauge(gui, g, mouseX, mouseY, StandardLayout.POWER, speed, type.minSpeed, type.nominalSpeed, type.nominalSpeed * 2);
            if (type.inputs + type.outputs > 0) {
                StandardView.arrow(g, l.arrow, progress, GREEN);
            }
            int chip = switch (status) {
                case MechanicalProcess.STATUS_WORKING -> GREEN;
                case MechanicalProcess.STATUS_SLOW -> AMBER;
                case MechanicalProcess.STATUS_FULL -> RED;
                case MechanicalProcess.STATUS_STOPPED -> GREY;
                default -> 0;
            };
            List<GuiMachine.Row> rows = new ArrayList<>();
            rows.add(GuiMachine.Row.of("gui.pioneers.kinetics.torque", GuiMachine.literal(KineticTile.fmt(torque) + " / " + KineticTile.fmt(type.torque) + " N·m")));
            rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.kinetics.rate"), GuiMachine.literal("×" + String.format(Locale.ROOT, "%.2f", rate)
                  + (turns > 0 ? " · " + KineticTile.fmt(turns) + " tr" : "")), status == MechanicalProcess.STATUS_WORKING ? GuiMachine.FLOW_UP_COLOR : 0xFF8FA3A0));
            Rect s = l.screen;
            gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, Component.translatable("kinetics.pioneers.machine.status." + status), rpm(speed),
                  type.inputs + type.outputs > 0 ? progress : -1, GREEN, rows, false);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            MechanicalMachineTile tile = StorageViews.as(MechanicalMachineTile.class, be);
            if (tile != null) {
                slotTips(gui, mouseX, mouseY, tile.process().layout);
            }
            return true;
        }

        @Override
        void info(GuiMachine gui, BlockEntity be, List<StandardView.InfoRow> rows) {
            MechanicalMachineTile tile = StorageViews.as(MechanicalMachineTile.class, be);
            if (tile == null) {
                return;
            }
            MachineType type = tile.process().type;
            rows.add(new StandardView.InfoRow("torque", GuiMachine.literal(KineticTile.fmt(type.torque) + " N·m"), 0xFFE0B040));
            rows.add(new StandardView.InfoRow("speed_range", Component.translatable("gui.pioneers.api.info.speed_range_value", KineticTile.fmt(type.minSpeed),
                  KineticTile.fmt(type.nominalSpeed)), PiMenuScreen.TEXT));
            rows.add(new StandardView.InfoRow("power_w", GuiMachine.literal(KineticTile.watts(type.nominalPower())), PiMenuScreen.TEXT));
        }
    }

    // 1.0.5-beta (lot 38) : machines de traitement a vapeur : vue standard (StandardView.drawSteam : jauge de vapeur, faces, selecteur, tier)

    // ------------------------------------------------------------------
    // Soufflerie a vapeur
    // ------------------------------------------------------------------

    private static final class Blower extends Base {

        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            double stored = gui.value(UiValues.STEAM), capacity = gui.value(UiValues.STEAM_MAX), air = gui.value(UiValues.BLAST) / 1000.0;
            double used = gui.value(UiValues.STEAM_USE) / 100.0;
            steamGauge(gui, g, mouseX, mouseY, StandardLayout.POWER, stored, capacity, fr.pioneers.tech.bronze.steam.SteamRules.BLOWER_USE);
            Rect card = new Rect(StandardLayout.PANEL_X, StandardLayout.TOP, StandardLayout.WIDTH - 8 - StandardLayout.PANEL_X, StandardLayout.GAUGE_H);
            List<GuiMachine.Row> crow = new ArrayList<>();
            crow.add(GuiMachine.Row.of("gui.pioneers.steam.bellows_equiv", GuiMachine.literal(String.format(Locale.ROOT, "x%.1f", air))));
            crow.add(GuiMachine.Row.of("gui.pioneers.steam.pressure_short", mb(stored)));
            gui.info(g, card.x(), card.y(), card.w(), card.h(), 0, Component.translatable("gui.pioneers.api.info.blast"), GuiMachine.percent(air / 3), air / 3,
                  0xC8D8E4, crow, false);
            Rect s = StandardLayout.screen(StandardLayout.WIDTH);
            List<GuiMachine.Row> rows = new ArrayList<>();
            rows.add(GuiMachine.Row.of("gui.pioneers.steam.used", GuiMachine.literal(String.format(Locale.ROOT, "%.1f mB/t", used))));
            gui.info(g, s.x(), s.y(), s.w(), s.h(), air > 0 ? 0xC8D8E4 : 0, Component.translatable(air > 0 ? "gui.pioneers.steam.blowing" : "gui.pioneers.steam.blower_idle"),
                  mbt(used), -1, 0xC8D8E4, rows, false);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            return true;
        }

        @Override
        void info(GuiMachine gui, BlockEntity be, List<StandardView.InfoRow> rows) {
            rows.add(new StandardView.InfoRow("steam_use", mbt(fr.pioneers.tech.bronze.steam.SteamRules.BLOWER_USE), 0xFFE0A030));
            rows.add(new StandardView.InfoRow("blast", GuiMachine.literal("x3"), PiMenuScreen.TEXT));
        }
    }

    // ------------------------------------------------------------------
    // 1.0.2-beta (lot 2) : foyer de forge (« une interface pour ajuster le nombre de lingots et de charbon mis dedans »)
    // ------------------------------------------------------------------

    private static final class Hearth extends Base {

        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            ForgeHearthTile tile = StorageViews.as(ForgeHearthTile.class, be);
            if (tile == null) {
                return;
            }
            double c = gui.value(UiValues.HEAT) / 10.0;
            long pieceX10 = gui.value(UiValues.PIECE_HEAT);
            int work = (int) gui.value(UiValues.PIECE_WORK);
            boolean lit = tile.isLit();
            fireGauge(gui, g, mouseX, mouseY, StandardLayout.POWER, c, work, Component.translatable("gui.pioneers.hearth.fire_hint"));
            GuiMachine.flame(g, ForgeHearthTile.FUEL_X + 4, ForgeHearthTile.FUEL_Y - 16, lit ? gui.value(UiValues.BURN) / 1000.0 : 0);
            //Carte de la braise : temperature, air, charbon, piece
            Rect r = ForgeHearthTile.CARD;
            List<GuiMachine.Row> rows = new ArrayList<>();
            rows.add(GuiMachine.Row.of("industry.pioneers.gui.air", GuiMachine.percent(gui.value(UiValues.BLAST) / 1000.0)));
            rows.add(GuiMachine.Row.of("gui.pioneers.hearth.coal", GuiMachine.literal(tile.fuelCount() + " / " + gui.value(UiValues.FUEL_LIMIT))));
            boolean bloom = !tile.hotPiece().isEmpty() && !HeatedMetal.is(tile.hotPiece());
            if (pieceX10 >= 0) {
                rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.hearth.piece"), GuiMachine.literal(String.format(Locale.ROOT, "%.0f / %d °C",
                      pieceX10 / 10.0, work)), pieceX10 / 10.0 >= work ? GuiMachine.FLOW_UP_COLOR : PiMenuScreen.SCREEN_TEXT));
            } else {
                rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.hearth.piece"), Component.translatable("gui.pioneers.hearth.no_piece"),
                      PiMenuScreen.DIM));
            }
            gui.info(g, r.x(), r.y(), r.w(), r.h(), 0, Component.translatable("gui.pioneers.hearth.embers"), celsius(c), Math.min(1, c / 1700.0),
                  0xE07030, rows, false);
            //Ecran d'etat
            String state;
            double progress = -1;
            if (!lit) {
                state = tile.fuelCount() > 0 ? "out" : "no_coal";
            } else if (bloom) {
                state = "bloom";
                progress = gui.value(UiValues.BLOOM) / 1000.0;
            } else if (pieceX10 < 0) {
                state = tile.waiting().isEmpty() ? "idle" : "loading";
            } else {
                state = pieceX10 / 10.0 >= work ? "ready" : "heating";
            }
            Rect sc = StandardLayout.sliderScreen(StandardLayout.WIDTH);
            int chip = !lit ? GREY : "ready".equals(state) ? GREEN : 0xE07030;
            gui.info(g, sc.x(), sc.y(), sc.w(), sc.h(), chip, Component.translatable("gui.pioneers.hearth.state." + state),
                  progress >= 0 ? GuiMachine.percent(progress) : celsius(c), progress, 0xE07030, List.of(), false);
            if (gui.over(mouseX, mouseY, sc.x(), sc.y(), sc.w(), 16)) {
                gui.tooltip(List.of(Component.translatable("gui.pioneers.hearth.state." + state),
                      Component.translatable("gui.pioneers.hearth.help").withStyle(ChatFormatting.GRAY)));
            }
            //Deux curseurs : lingots et charbon gardes (clic droit avec une pile, entonnoirs)
            Rect row = StandardLayout.sliderRow(StandardLayout.WIDTH);
            int half = (row.w() - 4) / 2;
            Rect left = new Rect(row.x(), row.y(), half, row.h()), right = new Rect(row.x() + half + 4, row.y(), row.w() - half - 4, row.h());
            int[] im = {1, 4, 8, 12, HearthUnit.MAX_INGOTS}, fm = {1, 16, 32, 48, HearthUnit.MAX_FUEL};
            gui.slider(g, mouseX, mouseY, left, HearthUnit.SETTING_INGOTS, 1, HearthUnit.MAX_INGOTS, (int) gui.value(UiValues.INGOT_LIMIT), im,
                  labels(im), 0xE0B040, List.of(Component.translatable("gui.pioneers.hearth.slider_ingots"),
                        Component.translatable("gui.pioneers.hearth.slider_help").withStyle(ChatFormatting.GRAY)));
            gui.slider(g, mouseX, mouseY, right, HearthUnit.SETTING_FUEL, 1, HearthUnit.MAX_FUEL, (int) gui.value(UiValues.FUEL_LIMIT), fm,
                  labels(fm), 0xE07030, List.of(Component.translatable("gui.pioneers.hearth.slider_coal"),
                        Component.translatable("gui.pioneers.hearth.slider_help").withStyle(ChatFormatting.GRAY)));
        }

        private static List<Component> labels(int[] marks) {
            List<Component> out = new ArrayList<>();
            for (int m : marks) {
                out.add(GuiMachine.literal(String.valueOf(m)));
            }
            return out;
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            gui.slotTip(mouseX, mouseY, ForgeHearthTile.INPUT_X, ForgeHearthTile.INPUT_Y, "gui.pioneers.hearth.slot_ingots");
            gui.slotTip(mouseX, mouseY, ForgeHearthTile.FUEL_X, ForgeHearthTile.FUEL_Y, "gui.pioneers.hearth.slot_coal");
            gui.slotTip(mouseX, mouseY, ForgeHearthTile.HOT_X, ForgeHearthTile.HOT_Y, "gui.pioneers.hearth.slot_piece");
            return true;
        }

        @Override
        void info(GuiMachine gui, BlockEntity be, List<StandardView.InfoRow> rows) {
            rows.add(new StandardView.InfoRow("fire", Component.translatable("gui.pioneers.api.info.fire_value", celsius(ForgeHearthTile.BASE_C),
                  celsius(ForgeHearthTile.BASE_C + ForgeHeat.BELLOWS_MAX_C)), 0xFFFF7A1F));
        }
    }

    // ------------------------------------------------------------------
    // Four a coke
    // ------------------------------------------------------------------

    private static final class Coke extends Base {

        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            CokeOvenTile tile = StorageViews.as(CokeOvenTile.class, be);
            if (tile == null) {
                return;
            }
            CokeOvenProcess unit = tile.process();
            StandardLayout l = unit.layout;
            int status = (int) gui.value(UiValues.STATUS);
            boolean working = status == CokeOvenProcess.WORKING;
            fireGauge(gui, g, mouseX, mouseY, StandardLayout.POWER, working ? 1_000 : 20, 0, Component.translatable("gui.pioneers.api.info.coke_fire"));
            Rect t = l.fluidOut.get(0);
            gui.views().fluidGauge(g, mouseX, mouseY, t.x(), t.y(), t.w(), t.h(), tile.tar);
            long duration = gui.value(UiValues.DURATION);
            double progress = duration <= 0 ? 0 : gui.value(UiValues.PROGRESS) / (double) duration;
            StandardView.arrow(g, l.arrow, progress, 0xB08050);
            List<GuiMachine.Row> rows = new ArrayList<>();
            rows.add(GuiMachine.Row.of("gui.pioneers.api.info.tar", GuiMachine.literal(Texts.grouped(gui.value(UiValues.FLUID)) + " / 16 000 mB")));
            gui.info(g, l.screen.x(), l.screen.y(), l.screen.w(), l.screen.h(), working ? 0xB08050 : status == CokeOvenProcess.IDLE ? 0 : 0xE0A030,
                  Component.translatable("industry.pioneers.coke.status." + status), GuiMachine.percent(progress), working ? progress : -1, 0xB08050, rows, false);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            CokeOvenTile tile = StorageViews.as(CokeOvenTile.class, be);
            if (tile != null) {
                slotTips(gui, mouseX, mouseY, tile.process().layout);
            }
            return true;
        }

        @Override
        void info(GuiMachine gui, BlockEntity be, List<StandardView.InfoRow> rows) {
            rows.add(new StandardView.InfoRow("fire", Component.translatable("gui.pioneers.api.info.coke_fire"), 0xFFB08050));
        }
    }

    // ------------------------------------------------------------------
    // Dynamo (1.12.4, lot F4) : rotation -> FE
    // ------------------------------------------------------------------

    private static final class Dynamo extends Base {

        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            long stored = gui.value(UiValues.ENERGY), capacity = Math.max(1, gui.value(UiValues.CAPACITY)), rated = Math.max(1, gui.value(UiValues.RATED));
            double fe = gui.value(UiValues.OUTPUT) / 100.0, speed = gui.value(UiValues.SPEED) / 100.0, torque = gui.value(UiValues.TORQUE) / 10.0;
            double watts = gui.value(UiValues.MECHANICAL), match = gui.value(UiValues.MATCH) / 1000.0, vmax = Math.max(1, gui.value(UiValues.MAX_SPEED));
            double tmax = Math.max(1, gui.value(UiValues.RATED_TORQUE));
            int excitation = (int) Math.max(0, Math.min(DynamoTile.EXCITATION.length - 1, gui.value(UiValues.EXCITATION)));
            int status = (int) Math.max(0, Math.min(7, gui.value(UiValues.STATUS)));
            boolean output = gui.value(UiValues.OUTPUT_OK) != 0;
            //1.0.6 (Guillaume : « au norme des machines compactes ») : reserve et case de recharge a gauche, rotation en entree, fleche,
            //carte des mesures, bouton d'excitation, ecran standard
            Rect e = DynamoUnit.ENERGY;
            gui.energy(g, mouseX, mouseY, e.x(), e.y(), e.w(), e.h());
            speedGauge(gui, g, mouseX, mouseY, DynamoUnit.SPEED, speed, 0, vmax / 1.5, vmax);
            StandardView.arrow(g, DynamoUnit.ARROW, status == DynamoTile.PRODUCING || status == DynamoTile.SATURATED
                  || status == DynamoTile.TORQUE_LIMIT ? Math.min(1, fe / rated) : 0, 0xE0B040);
            List<GuiMachine.Row> crow = new ArrayList<>();
            crow.add(GuiMachine.Row.of("gui.pioneers.kinetics.torque", GuiMachine.literal(KineticTile.fmt(torque) + " N·m")));
            crow.add(GuiMachine.Row.of("gui.pioneers.dynamo.mechanical", GuiMachine.literal(watts >= 1000 ? String.format(Locale.ROOT, "%.1f kW", watts / 1000)
                  : Math.round(watts) + " W")));
            crow.add(new GuiMachine.Row(Component.translatable("gui.pioneers.dynamo.match"), GuiMachine.percent(match), match >= 0.9 ? GuiMachine.FLOW_UP_COLOR
                  : match >= 0.5 ? PiMenuScreen.SCREEN_TEXT : 0xFFE0B040));
            Rect c = DynamoUnit.CARD;
            int oc = output ? 0x6FD08A : 0xE04848;
            gui.info(g, c.x(), c.y(), c.w(), c.h(), oc, Component.translatable(output ? "gui.pioneers.dynamo.output_ok" : "gui.pioneers.dynamo.output_none"),
                  rpm(speed), Math.min(1, torque / tmax), 0xE0B040, crow, false);
            if (gui.over(mouseX, mouseY, c.x(), c.y(), c.w(), 16)) {
                gui.tooltip(List.of(Component.translatable(output ? "gui.pioneers.dynamo.output_ok_tip" : "gui.pioneers.dynamo.output_none_tip")));
            }
            Rect r = DynamoUnit.BUTTON;
            Component label = Component.translatable("gui.pioneers.dynamo.excitation", Component.translatable("gui.pioneers.dynamo.excitation." + excitation));
            gui.steelButton(g, mouseX, mouseY, r.x(), r.y(), r.w(), r.h(), label, true, () -> gui.send(Screen.hasShiftDown() ? DynamoTile.PREVIOUS : DynamoTile.NEXT));
            if (gui.over(mouseX, mouseY, r.x(), r.y(), r.w(), r.h())) {
                gui.tooltip(List.of(label, Component.translatable("gui.pioneers.dynamo.excitation_tip").withStyle(ChatFormatting.GRAY)));
            }
            int chip = switch (status) {
                case DynamoTile.PRODUCING -> GREEN;
                case DynamoTile.SATURATED, DynamoTile.TORQUE_LIMIT -> AMBER;
                case DynamoTile.STALLED, DynamoTile.OVERSPEED -> RED;
                case DynamoTile.NO_OUTPUT -> 0xE08A3A;
                default -> GREY;
            };
            Rect s = StandardLayout.screen(StandardLayout.WIDTH);
            List<GuiMachine.Row> rows = new ArrayList<>();
            rows.add(GuiMachine.Row.of("gui.pioneers.api.info.peak", GuiMachine.literal(Texts.grouped(rated) + " FE/t")));
            rows.add(GuiMachine.Row.of("gui.pioneers.kinetics.torque", GuiMachine.literal(KineticTile.fmt(tmax) + " N·m max")));
            gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, Component.translatable("gui.pioneers.dynamo.status." + status), GuiMachine.literal(GuiMachine.compact(fe) + " FE/t"),
                  fe / rated, 0xE8C060, rows, false);
            if (gui.over(mouseX, mouseY, s.x(), s.y(), s.w(), 16)) {
                gui.tooltip(List.of(Component.translatable("gui.pioneers.dynamo.status." + status), Component.translatable("gui.pioneers.dynamo.help." + status)
                      .withStyle(ChatFormatting.GRAY), Component.translatable("gui.pioneers.dynamo.rated", Texts.grouped(rated), KineticTile.fmt(tmax),
                      KineticTile.fmt(vmax)).withStyle(ChatFormatting.DARK_GRAY)));
            }
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            return true;
        }

        @Override
        void info(GuiMachine gui, BlockEntity be, List<StandardView.InfoRow> rows) {
            rows.add(new StandardView.InfoRow("peak", GuiMachine.literal(Texts.grouped(gui.value(UiValues.RATED)) + " FE/t"), 0xFFFFFF55));
            rows.add(new StandardView.InfoRow("torque", GuiMachine.literal(Texts.grouped(gui.value(UiValues.RATED_TORQUE)) + " N·m"), 0xFFE0B040));
            rows.add(new StandardView.InfoRow("max_speed", GuiMachine.literal(gui.value(UiValues.MAX_SPEED) + " tr/min"), PiMenuScreen.TEXT));
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_dynamo";
        }
    }
}

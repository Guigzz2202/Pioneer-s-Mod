package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.tech.bronze.foundry.CastingRecipe;
import fr.pioneers.tech.bronze.foundry.CastingTableTile;
import fr.pioneers.tech.bronze.foundry.CastingUnit;
import fr.pioneers.tech.bronze.foundry.CrucibleFurnaceTile;
import fr.pioneers.tech.bronze.foundry.KilnTile;
import fr.pioneers.tech.bronze.foundry.Metals;
import fr.pioneers.tech.bronze.steam.SteamEngineTile;
import fr.pioneers.tech.bronze.steam.SteamRules;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.core.GuiMachine.Row;
import fr.pioneers.tech.kinetics.KineticTile;
import fr.pioneers.tech.kinetics.kitchen.KitchenMachineTile;
import fr.pioneers.tech.kinetics.kitchen.KitchenMachineType;
import fr.pioneers.tech.kinetics.kitchen.KitchenProcess;
import fr.pioneers.tech.kinetics.sources.BeamEngineTile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * API Machine (lot 8b) : INTERFACE STANDARD v2 des dernieres machines de l'age mecanique et du bronze (meme grammaire que {@link AgeViews} :
 * energie motrice a gauche, jauges, cases, carte, ecran d'etat de 52 px, onglet Infos, pastille d'age) : postes mecaniques de la cuisine
 * ({@code kitchen} : vitesse, reservoirs, carre du selecteur = la recette fixee d'avant), machine a balancier ({@code beam_engine} : vitesse,
 * chaudiere, eau, foyer, seaux), machine a vapeur ({@code steam_engine} : manometre, vitesse de sortie ; 1.0.4-beta lot 27 : injecteur et
 * condenseur retires), fourneau a creuset ({@code crucible} : foyer, bain, interrupteur « Auto » et bouton « Couler »), table de
 * coulee ({@code casting}), four de potier ({@code kiln}), ateliers a vapeur ({@code steam_workshop} : carre du selecteur a la place de la
 * grille de l'etabli). Valeurs lues par leur NOM ({@link UiValues}) ; machine choisie par la vue de sa fiche (plus d'{@code instanceof}).
 * Remplace {@code KineticViews} et les branches d'avant de {@code SteamViews} et {@code BronzeViews}.
 */
final class SourceViews {

    private static final int GREEN = 0x3FD0A0, AMBER = 0xE0B040, RED = 0xE04848, GREY = 0x8A9099, WATER = 0x3F76E4, FIRE = 0xFF7A1F;

    static final MachineScreens.View KITCHEN = new Kitchen();
    static final MachineScreens.View BEAM_ENGINE = new Beam();
    static final MachineScreens.View STEAM_ENGINE = new Engine();
    static final MachineScreens.View CRUCIBLE = new Crucible();
    static final MachineScreens.View CASTING = new Casting();
    static final MachineScreens.View KILN = new Kiln();
    //1.0.5-beta (lot 38) : plus d'ateliers a vapeur (marteau-pilon retire, presse devenue machine de traitement : vue standard)

    private SourceViews() {
    }

    private static Component mb(double v) {
        return GuiMachine.literal(String.format(Locale.ROOT, "%.1f mB/t", v));
    }

    /** Tube de niveau en verre : eau bleue, reperes bas (rouge) et haut (jaune) si > 0. */
    static void waterGauge(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, Rect r, double water, double capacity, double low, double high) {
        int x = r.x(), y = r.y(), w = r.w(), h = r.h();
        gui.frame(g, x, y, w, h);
        double ratio = capacity <= 0 ? 0 : Math.max(0, Math.min(1, water / capacity));
        int filled = (int) Math.round((h - 2) * ratio);
        if (filled > 0) {
            g.fill(x + 1, y + h - 1 - filled, x + w - 1, y + h - 1, 0xFF3F76E4);
            g.fill(x + 1, y + h - 1 - filled, x + 3, y + h - 1, 0xFF6F9CF0);
            g.fill(x + 1, y + h - 1 - filled, x + w - 1, y + h - filled, 0xFFB8D4FF);
        }
        g.fill(x + 2, y + 1, x + 3, y + h - 1, 0x40FFFFFF);
        for (double[] m : new double[][]{{low, 0xFFE04848}, {high, 0xFFE0C040}}) {
            if (m[0] > 0) {
                int my = y + h - 1 - (int) Math.round((h - 2) * m[0]);
                g.fill(x - 1, my, x, my + 1, (int) (long) m[1]);
                g.fill(x + w, my, x + w + 1, my + 1, (int) (long) m[1]);
            }
        }
        GuiMachine.ticks(g, x, y, w, h);
        if (gui.over(mouseX, mouseY, x, y, w, h)) {
            gui.tooltip(List.of(Component.translatable("gui.pioneers.steam.water", Math.round(water), Math.round(capacity))));
        }
    }

    /** Jauge haute d'une part 0..1 (echauffement du condenseur, vitesse de sortie...) : du vert au rouge. */
    static void ratioGauge(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, Rect r, double ratio, int low, int high, List<Component> tip) {
        int x = r.x(), y = r.y(), w = r.w(), h = r.h();
        gui.frame(g, x, y, w, h);
        int filled = (int) Math.round((h - 2) * Math.max(0, Math.min(1, ratio)));
        for (int i = 0; i < filled; i++) {
            g.fill(x + 1, y + h - 2 - i, x + w - 1, y + h - 1 - i, GuiMachine.lerp(0xFF000000 | low, 0xFF000000 | high, i / (double) (h - 2)));
        }
        GuiMachine.ticks(g, x, y, w, h);
        if (gui.over(mouseX, mouseY, x, y, w, h)) {
            gui.tooltip(tip);
        }
    }

    /** Barre de progression horizontale de 2 px sous une case (fusion, cuisson ; 1.0.2-beta lot 4 : aussi la fonderie automatique). */
    /** 1.0.3-beta (lot 7b) : hauteur des lignes du plan (au-dessus de la ligne liquide / fige), couleur d'une case qui attend, bouton « Vider ». */
    static final int PLAN_H = 13, WAIT = 0xE0A030, DUMP_W = 40;

    /**
     * 1.0.3-beta (lot 7b) : plan de coulee en bas d'une carte de bain (fourneau a creuset, fonderie automatique) : « Coule : bronze 4 lingots »
     * puis « Reste : cuivre 36 mB » (ou ce que le filtre attend) ; info-bulle avec les deux lignes entieres.
     */
    static void planLines(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, Rect r, Metals.Plan plan) {
        if (plan.reason() == Metals.WHY_EMPTY) {
            return;
        }
        int sx = r.x(), sw = r.w(), y = planTop(r);
        g.fill(sx + 4, y - 1, sx + sw - 4, y, 0xFF1E2A33);
        Component pour = Metals.pourLine(plan), rest = Metals.restLine(plan);
        GuiDraw.text(g, gui.font(), pour, sx + 5, y + 1, 0.55F, plan.pours() ? 0xFFFFC060 : 0xFF000000 | WAIT, sw - 10);
        GuiDraw.text(g, gui.font(), rest, sx + 5, y + 7, 0.55F, PiMenuScreen.DIM, sw - 10);
        if (gui.over(mouseX, mouseY, sx, y, sw, PLAN_H)) {
            gui.tooltip(List.of(pour.copy().withStyle(plan.pours() ? ChatFormatting.GOLD : ChatFormatting.YELLOW), rest.copy().withStyle(ChatFormatting.GRAY),
                  Component.translatable("gui.pioneers.bath.rule").withStyle(ChatFormatting.DARK_GRAY)));
        }
    }

    /**
     * 1.0.3-beta (lot 7b) : couleur du nom du bain : exactement un metal ou un alliage = texte ; melange qui se coule en parts = ambre ; rouge
     * seulement si rien ne peut couler.
     */
    static int nameColour(Map<String, Integer> mix, String result) {
        if (mix.isEmpty() || !result.isEmpty()) {
            return PiMenuScreen.TEXT;
        }
        return Metals.portions(mix).isEmpty() ? 0xFFFF6A5A : 0xFF000000 | WAIT;
    }

    /** Haut des lignes du plan dans une carte de bain (la ligne liquide / fige est a h - 7) ; la legende s'arrete au-dessus. */
    static int planTop(Rect r) {
        return r.y() + r.h() - 7 - PLAN_H;
    }

    /** 1.0.3-beta (lot 7b) : bouton « Vider » (sortie de secours : le bain rendu en lingots, pepites et scorie ; action {@code action}). */
    static void dumpButton(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, int x, int y, int h, boolean active, int action) {
        gui.steelButton(g, mouseX, mouseY, x, y, DUMP_W, h, Component.translatable("gui.pioneers.bath.dump"), active, () -> gui.send(action));
        if (gui.over(mouseX, mouseY, x, y, DUMP_W, h)) {
            gui.tooltip(List.of(Component.translatable("gui.pioneers.bath.dump_tip").withStyle(ChatFormatting.GOLD),
                  Component.translatable("gui.pioneers.bath.dump_tip2").withStyle(ChatFormatting.GRAY)));
        }
    }

    static void slotBar(GuiGraphics g, int x, int y, double ratio, int color) {
        g.fill(x, y, x + 16, y + 2, 0xFF05080B);
        if (ratio > 0) {
            g.fill(x, y, x + (int) Math.round(16 * Math.min(1, ratio)), y + 2, 0xFF000000 | color);
        }
    }

    // ------------------------------------------------------------------
    // Postes mecaniques de la cuisine
    // ------------------------------------------------------------------

    private static final class Kitchen extends AgeViews.Base {

        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            KitchenMachineTile tile = StorageViews.as(KitchenMachineTile.class, be);
            if (tile == null) {
                return;
            }
            KitchenProcess unit = tile.process();
            KitchenMachineType type = unit.type;
            StandardLayout l = unit.layout;
            double speed = gui.value(UiValues.SPEED) / 100.0, torque = gui.value(UiValues.TORQUE) / 10.0, progress = gui.value(UiValues.PROGRESS_X1000) / 1000.0;
            double rate = gui.value(UiValues.RATE) / 100.0, turns = gui.value(UiValues.TURNS) / 100.0;
            int status = (int) gui.value(UiValues.STATUS);
            AgeViews.speedGauge(gui, g, mouseX, mouseY, StandardLayout.POWER, speed, type.minSpeed, type.nominalSpeed, type.nominalSpeed * 2);
            if (unit.tankIn() != null) {
                Rect t = l.fluidIn.get(0);
                gui.views().fluidGauge(g, mouseX, mouseY, t.x(), t.y(), t.w(), t.h(), unit.tankIn());
            }
            if (unit.tankOut() != null) {
                Rect t = l.fluidOut.get(0);
                gui.views().fluidGauge(g, mouseX, mouseY, t.x(), t.y(), t.w(), t.h(), unit.tankOut());
            }
            StandardView.arrow(g, l.arrow, progress, GREEN);
            int chip = switch (status) {
                case KitchenProcess.STATUS_WORKING -> GREEN;
                case KitchenProcess.STATUS_SLOW -> AMBER;
                case KitchenProcess.STATUS_FULL -> RED;
                case KitchenProcess.STATUS_STOPPED -> GREY;
                default -> 0;
            };
            List<Row> rows = new ArrayList<>();
            rows.add(Row.of("gui.pioneers.kinetics.torque", GuiMachine.literal(KineticTile.fmt(torque) + " / " + KineticTile.fmt(type.torque) + " N·m")));
            rows.add(new Row(Component.translatable("gui.pioneers.kinetics.rate"), GuiMachine.literal("×" + String.format(Locale.ROOT, "%.2f", rate)
                  + " · " + KineticTile.fmt(turns) + " tr"), status == KitchenProcess.STATUS_WORKING ? GuiMachine.FLOW_UP_COLOR : 0xFF8FA3A0));
            Rect s = l.screen;
            gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, Component.translatable("kinetics.pioneers.machine.status." + status), GuiMachine.percent(progress),
                  progress, GREEN, rows, false);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            KitchenMachineTile tile = StorageViews.as(KitchenMachineTile.class, be);
            if (tile != null && tile.type() == KitchenMachineType.HONEY_EXTRACTOR && tile.process().containerIn() != null) {
                //1.0.5-beta (lot 32, retour 41) : extracteur de miel : cases nommees (memes textes que l'extracteur manuel)
                KitchenProcess unit = tile.process();
                String k = "gui.pioneers.honey_extractor.slot.";
                int[] in = unit.layout.itemIn.get(0);
                gui.slotTip(mouseX, mouseY, in[0], in[1], k + "frames");
                String[] outs = {"wax", "comb", "empty_frame"};
                for (int i = 0; i < unit.layout.itemOut.size() && i < outs.length; i++) {
                    int[] p = unit.layout.itemOut.get(i);
                    gui.slotTip(mouseX, mouseY, p[0], p[1], k + outs[i]);
                }
                gui.slotTip(mouseX, mouseY, unit.containerIn().x, unit.containerIn().y, k + "container");
                gui.slotTip(mouseX, mouseY, unit.containerOut().x, unit.containerOut().y, k + "filled");
            } else if (tile != null) {
                KitchenProcess unit = tile.process();
                for (int[] p : unit.layout.itemIn) {
                    gui.slotTip(mouseX, mouseY, p[0], p[1], "gui.pioneers.kitchen_machine.ingredient");
                }
                for (int[] p : unit.layout.itemOut) {
                    gui.slotTip(mouseX, mouseY, p[0], p[1], "gui.pioneers.machine.out");
                }
                //1.0.5-beta (lot 41, retour 60) : paires « Remplir » (pressoir) et « Vider » (baratte, petrin) a la place du seau au clic
                if (unit.containerIn() != null && unit.containerOut() != null) {
                    gui.slotTip(mouseX, mouseY, unit.containerIn().x, unit.containerIn().y, "gui.pioneers.farm.slot.fill_in");
                    gui.slotTip(mouseX, mouseY, unit.containerOut().x, unit.containerOut().y, "gui.pioneers.farm.slot.fill_out");
                }
                if (unit.drainIn() != null && unit.drainOut() != null) {
                    gui.slotTip(mouseX, mouseY, unit.drainIn().x, unit.drainIn().y, "gui.pioneers.farm.slot.drain_in");
                    gui.slotTip(mouseX, mouseY, unit.drainOut().x, unit.drainOut().y, "gui.pioneers.farm.slot.drain_out");
                }
            }
            return true;
        }

        @Override
        void info(GuiMachine gui, BlockEntity be, List<StandardView.InfoRow> rows) {
            KitchenMachineTile tile = StorageViews.as(KitchenMachineTile.class, be);
            if (tile == null) {
                return;
            }
            KitchenMachineType type = tile.type();
            rows.add(new StandardView.InfoRow("torque", GuiMachine.literal(KineticTile.fmt(type.torque) + " N·m"), 0xFFE0B040));
            rows.add(new StandardView.InfoRow("speed_range", Component.translatable("gui.pioneers.api.info.speed_range_value", KineticTile.fmt(type.minSpeed),
                  KineticTile.fmt(type.nominalSpeed)), PiMenuScreen.TEXT));
            rows.add(new StandardView.InfoRow("power_w", GuiMachine.literal(KineticTile.watts(type.nominalPower())), PiMenuScreen.TEXT));
            rows.add(new StandardView.InfoRow("stroke", GuiMachine.literal(String.valueOf(type.turnsPerStroke).replace(".0", "") + " tr"), PiMenuScreen.TEXT));
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_kitchen";
        }
    }

    // ------------------------------------------------------------------
    // Machine a balancier
    // ------------------------------------------------------------------

    private static final class Beam extends AgeViews.Base {

        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            double speed = gui.value(UiValues.SPEED) / 100.0, torque = gui.value(UiValues.TORQUE) / 10.0, kelvin = gui.value(UiValues.BOILER) / 10.0;
            double pressure = gui.value(UiValues.PRESSURE) / 1000.0, burn = gui.value(UiValues.BURN) / 1000.0;
            long water = gui.value(UiValues.WATER), capacity = Math.max(1, gui.value(UiValues.WATER_MAX));
            boolean dry = gui.value(UiValues.DRY) != 0;
            AgeViews.speedGauge(gui, g, mouseX, mouseY, StandardLayout.POWER, speed, 1, 7, 20);
            Rect t = BeamEngineTile.THERMO;
            gui.thermometer(g, mouseX, mouseY, t.x(), t.y(), t.w(), t.h(), kelvin, (int) BeamEngineTile.BOIL,
                  List.of(Component.translatable("gui.pioneers.kinetics.boiler", String.format(Locale.ROOT, "%.0f", kelvin))));
            waterGauge(gui, g, mouseX, mouseY, BeamEngineTile.WATER, water, capacity, 0.0125, 0);
            GuiMachine.flame(g, BeamEngineTile.FUEL_X + 1, BeamEngineTile.FUEL_Y - 18, burn);
            Rect c = BeamEngineTile.CARD;
            List<Row> crow = new ArrayList<>();
            crow.add(Row.of("gui.pioneers.kinetics.torque", GuiMachine.literal(KineticTile.fmt(torque) + " N·m")));
            crow.add(new Row(Component.translatable("gui.pioneers.kinetics.fire"), GuiMachine.percent(burn), burn > 0 ? 0xFFFFB050 : 0xFF8FA3A0));
            gui.info(g, c.x(), c.y(), c.w(), c.h(), dry ? RED : pressure > 0 ? 0xE8EEF2 : 0, Component.translatable("gui.pioneers.kinetics.steam"),
                  GuiMachine.percent(pressure), pressure, 0xC8D8E4, crow, false);
            Rect s = StandardLayout.screen(StandardLayout.WIDTH);
            List<Row> rows = new ArrayList<>();
            rows.add(Row.of("gui.pioneers.api.info.boiler", GuiMachine.literal(String.format(Locale.ROOT, "%.0f K", kelvin))));
            rows.add(Row.of("gui.pioneers.api.info.water", GuiMachine.literal(water + " / " + capacity + " mB")));
            if (gui.value(UiValues.SURPLUS) > 0) {
                //1.0.4-beta (lot 5) : chaudiere au maximum, le feu continue : la chaleur en trop se perd
                rows.add(new Row(Component.translatable("gui.pioneers.generator.surplus"), Component.translatable("gui.pioneers.generator.surplus_value"),
                      EnergyViews.SURPLUS_COLOR));
            }
            gui.info(g, s.x(), s.y(), s.w(), s.h(), dry ? RED : Math.abs(speed) > 0.5 ? GREEN : 0, Component.translatable(dry ? "gui.pioneers.kinetics.dry"
                  : Math.abs(speed) > 0.5 ? "gui.pioneers.api.source.running" : "gui.pioneers.api.source.stopped"), AgeViews.rpm(speed), -1, GREEN, rows, false);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            gui.slotTip(mouseX, mouseY, BeamEngineTile.FUEL_X, BeamEngineTile.FUEL_Y, "gui.pioneers.machine.fuel");
            gui.slotTip(mouseX, mouseY, BeamEngineTile.BUCKET_X, BeamEngineTile.BUCKET_Y, "gui.pioneers.kinetics.water_bucket");
            gui.slotTip(mouseX, mouseY, BeamEngineTile.EMPTY_X, BeamEngineTile.EMPTY_Y, "gui.pioneers.machine.out");
            return true;
        }

        @Override
        void info(GuiMachine gui, BlockEntity be, List<StandardView.InfoRow> rows) {
            rows.add(new StandardView.InfoRow("start_torque", GuiMachine.literal(KineticTile.fmt(BeamEngineTile.TAU0) + " N·m"), 0xFFE0B040));
            rows.add(new StandardView.InfoRow("free_speed", GuiMachine.literal(KineticTile.fmt(BeamEngineTile.FREE) + " tr/min"), PiMenuScreen.TEXT));
            rows.add(new StandardView.InfoRow("fire", GuiMachine.literal(KineticTile.watts(BeamEngineTile.FIRE)), 0xFFFF7A1F));
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_source";
        }
    }

    // ------------------------------------------------------------------
    // Machine a vapeur
    // ------------------------------------------------------------------

    private static final class Engine extends AgeViews.Base {

        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            double speed = gui.value(UiValues.SPEED) / 100.0, torque = gui.value(UiValues.TORQUE) / 10.0, stored = gui.value(UiValues.STEAM);
            double capacity = gui.value(UiValues.STEAM_MAX);
            double eff = gui.value(UiValues.USEFUL) / 1000.0, used = gui.value(UiValues.STEAM_USE) / 100.0, wet = gui.value(UiValues.WET) / 1000.0;
            AgeViews.steamGauge(gui, g, mouseX, mouseY, StandardLayout.POWER, stored, capacity, Math.max(used, SteamRules.engineNeed(speed, 0)));
            ratioGauge(gui, g, mouseX, mouseY, SteamEngineTile.SPEED, Math.abs(speed) / SteamEngineTile.FREE, 0x3A6A5A, 0x3FD0A0,
                  List.of(Component.translatable("gui.pioneers.api.source.output_speed", AgeViews.rpm(speed), KineticTile.fmt(SteamEngineTile.FREE))));
            Rect c = SteamEngineTile.CARD;
            List<Row> crow = new ArrayList<>();
            crow.add(Row.of("gui.pioneers.kinetics.torque", GuiMachine.literal(KineticTile.fmt(torque) + " N·m")));
            crow.add(Row.of("gui.pioneers.steam.useful", GuiMachine.percent(eff)));
            crow.add(new Row(Component.translatable("gui.pioneers.steam.wet"), GuiMachine.percent(wet), wet > 0.1 ? 0xFFE0C040 : PiMenuScreen.SCREEN_TEXT));
            gui.info(g, c.x(), c.y(), c.w(), c.h(), 0, Component.translatable("gui.pioneers.kinetics.steam"), AgeViews.mb(stored),
                  capacity <= 0 ? 0 : Math.min(1, stored / capacity), 0xC8D8E4, crow, false);
            Rect s = StandardLayout.screen(StandardLayout.WIDTH);
            List<Row> rows = new ArrayList<>();
            rows.add(Row.of("gui.pioneers.steam.used", mb(used)));
            boolean running = Math.abs(speed) > 0.5;
            gui.info(g, s.x(), s.y(), s.w(), s.h(), running ? 0xE8EEF2 : 0, Component.translatable(running ? "gui.pioneers.api.source.running"
                  : "gui.pioneers.api.source.stopped"), AgeViews.rpm(speed), -1, 0xC8D8E4, rows, false);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            return true;
        }

        @Override
        void info(GuiMachine gui, BlockEntity be, List<StandardView.InfoRow> rows) {
            rows.add(new StandardView.InfoRow("start_torque", GuiMachine.literal(KineticTile.fmt(SteamEngineTile.TORQUE) + " N·m"), 0xFFE0B040));
            rows.add(new StandardView.InfoRow("free_speed", GuiMachine.literal(KineticTile.fmt(SteamEngineTile.FREE) + " tr/min"), PiMenuScreen.TEXT));
            rows.add(new StandardView.InfoRow("steam_use", Component.translatable("gui.pioneers.api.info.full_power_use", String.format(Locale.ROOT, "%.1f",
                  SteamRules.ENGINE_FULL_USE)), PiMenuScreen.TEXT));
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_source";
        }
    }

    // ------------------------------------------------------------------
    // Fourneau a creuset
    // ------------------------------------------------------------------

    private static final class Crucible extends AgeViews.Base {

        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            CrucibleFurnaceTile tile = StorageViews.as(CrucibleFurnaceTile.class, be);
            if (tile == null) {
                return;
            }
            double c = gui.value(UiValues.HEAT) / 10.0, reach = gui.value(UiValues.REACH) / 10.0, fire = gui.value(UiValues.BURN) / 1000.0;
            double air = gui.value(UiValues.BLAST) / 1000.0;
            Map<String, Integer> mix = tile.mix();
            int total = mix.values().stream().mapToInt(Integer::intValue).sum();
            String result = Metals.resolve(mix);
            int pourC = mix.isEmpty() ? 0 : Metals.pourC(mix);
            AgeViews.fireGauge(gui, g, mouseX, mouseY, StandardLayout.POWER, c, pourC, Component.translatable("gui.pioneers.foundry.reach", AgeViews.celsius(reach)));
            GuiMachine.flame(g, CrucibleFurnaceTile.FUEL_X + 1, CrucibleFurnaceTile.FUEL_Y - 18, fire);
            //1.0.3-beta (lot 7b) : plan de coulee de la regle commune (Metals.plan) : ce qui coule, ce qui reste, ce que le filtre attend
            Metals.Plan plan = tile.plan();
            bath(gui, g, CrucibleFurnaceTile.BATH, mix, total, result, c >= pourC && !mix.isEmpty(), pourC);
            planLines(gui, g, mouseX, mouseY, CrucibleFurnaceTile.BATH, plan);
            String waits = "";
            for (int i = 0; i < CrucibleFurnaceTile.METAL_SLOTS; i++) {
                int[] p = CrucibleFurnaceTile.inPos(i);
                String w = tile.furnace().waitsFor(tile.furnace().input(i));
                slotBar(g, p[0], p[1] + 17, w.isEmpty() ? gui.value(UiValues.melt(i)) / 1000.0 : 1, w.isEmpty() ? FIRE : WAIT);
                waits = waits.isEmpty() ? w : waits;
            }
            Rect s = StandardLayout.shortScreen(StandardLayout.WIDTH);
            List<Row> rows = new ArrayList<>();
            rows.add(waits.isEmpty() ? Row.of("gui.pioneers.api.info.reach", AgeViews.celsius(reach))
                  : new Row(Component.translatable("gui.pioneers.bath.slot_waits"), Metals.name(waits), 0xFFE0A030));
            rows.add(Row.of("industry.pioneers.gui.air", GuiMachine.percent(air)));
            boolean hot = !mix.isEmpty() && c >= pourC;
            gui.info(g, s.x(), s.y(), s.w(), s.h(), fire > 0 ? FIRE : 0, Component.translatable(mix.isEmpty() ? "gui.pioneers.foundry.empty" : hot
                  ? "gui.pioneers.api.crucible.molten" : "gui.pioneers.api.crucible.heating"), AgeViews.celsius(c), -1, FIRE, rows, false);
            //Rangee de coulee : interrupteur « Auto », bouton « Couler », 1.0.3-beta (lot 7b) bouton « Vider »
            Rect r = StandardLayout.settingRow(StandardLayout.WIDTH);
            autoSwitch(gui, g, mouseX, mouseY, r.x(), r.y(), 40, r.h(), gui.value(UiValues.AUTO) != 0);
            CastingTableTile table = tile.table();
            CastingRecipe recipe = table == null || !plan.pours() ? null : table.recipeFor(plan.metal());
            Component label = table == null ? Component.translatable("gui.pioneers.foundry.no_table")
                  : recipe == null ? Component.translatable("gui.pioneers.foundry.pour") : Component.translatable("gui.pioneers.foundry.pour_n", recipe.amount());
            int pw = r.w() - 42 - DUMP_W - 2;
            gui.steelButton(g, mouseX, mouseY, r.x() + 42, r.y(), pw, r.h(), label, hot && recipe != null && !table.busy(),
                  () -> gui.send(CrucibleFurnaceTile.POUR));
            dumpButton(gui, g, mouseX, mouseY, r.x() + r.w() - DUMP_W, r.y(), r.h(), !mix.isEmpty(), CrucibleFurnaceTile.DUMP);
        }

        /** Carte du bain : resultat, quantite, barre de composition, legende, etat liquide / fige. */
        private static void bath(GuiMachine gui, GuiGraphics g, Rect r, Map<String, Integer> mix, int total, String result, boolean hot, int pourC) {
            int sx = r.x(), sy = r.y(), sw = r.w(), sh = r.h();
            gui.screenAt(g, sx, sy, sw, sh);
            Component name = mix.isEmpty() ? Component.translatable("gui.pioneers.foundry.empty")
                  : Metals.nameOrMixed(result);
            GuiDraw.text(g, gui.font(), name, sx + 5, sy + 5, 0.8F, nameColour(mix, result), sw - 40);
            GuiDraw.right(g, gui.font(), Component.literal(total + " mB"), sx + sw - 5, sy + 5, 0.7F, PiMenuScreen.SCREEN_TEXT);
            int bx = sx + 5, bw = sw - 10, by = sy + 16;
            g.fill(bx, by, bx + bw, by + 6, 0xFF05080B);
            int cx = bx;
            int k = 0;
            for (Map.Entry<String, Integer> e : mix.entrySet()) {
                int w = k == mix.size() - 1 ? bx + bw * total / CrucibleFurnaceTile.CAPACITY - cx
                      : (int) Math.round(bw * e.getValue() / (double) CrucibleFurnaceTile.CAPACITY);
                g.fill(cx, by, cx + Math.max(1, w), by + 6, 0xFF000000 | Metals.colour(e.getKey()));
                cx += w;
                k++;
            }
            int ly = by + 9;
            for (Map.Entry<String, Integer> e : mix.entrySet()) {
                //1.0.3-beta (lot 7b) : deux lignes de legende, puis le plan de coulee (planLines)
                if (ly + 7 > planTop(r)) {
                    break;
                }
                g.fill(sx + 5, ly + 1, sx + 9, ly + 5, 0xFF000000 | Metals.colour(e.getKey()));
                GuiDraw.text(g, gui.font(), Metals.name(e.getKey()), sx + 12, ly, 0.6F, PiMenuScreen.DIM, 50);
                GuiDraw.right(g, gui.font(), Component.literal(Math.round(e.getValue() * 100.0 / Math.max(1, total)) + " %"), sx + sw - 5, ly, 0.6F,
                      PiMenuScreen.TEXT);
                ly += 7;
            }
            if (!mix.isEmpty()) {
                GuiDraw.text(g, gui.font(), Component.translatable(hot ? "gui.pioneers.foundry.liquid" : "gui.pioneers.foundry.solid", AgeViews.celsius(pourC)),
                      sx + 5, sy + sh - 7, 0.55F, hot ? 0xFFFFB050 : 0xFF8FA3A0, sw - 10);
            }
        }

        /**
         * Interrupteur de la coulee automatique (1.0.1-beta) : bouton d'acier avec un voyant (vert allume, eteint sinon) et « Auto » ; un clic
         * bascule (action {@link CrucibleFurnaceTile#AUTO}), l'etat vient du serveur (valeur nommee {@link UiValues#AUTO}).
         */
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
            gui.click(x, y, w, h, () -> gui.send(CrucibleFurnaceTile.AUTO));
            if (hover) {
                gui.tooltip(List.of(Component.translatable(on ? "gui.pioneers.foundry.auto_on" : "gui.pioneers.foundry.auto_off")
                            .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                      Component.translatable("foundry.pioneers.auto_pour").withStyle(ChatFormatting.GRAY),
                      Component.translatable("gui.pioneers.foundry.auto_inputs").withStyle(ChatFormatting.DARK_GRAY),
                      Component.translatable("gui.pioneers.foundry.auto_click").withStyle(ChatFormatting.DARK_GRAY)));
            }
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            gui.slotTip(mouseX, mouseY, CrucibleFurnaceTile.CRUCIBLE_X, CrucibleFurnaceTile.CRUCIBLE_Y, "gui.pioneers.foundry.crucible");
            gui.slotTip(mouseX, mouseY, CrucibleFurnaceTile.FUEL_X, CrucibleFurnaceTile.FUEL_Y, "gui.pioneers.machine.fuel");
            for (int i = 0; i < CrucibleFurnaceTile.METAL_SLOTS; i++) {
                int[] p = CrucibleFurnaceTile.inPos(i);
                gui.slotTip(mouseX, mouseY, p[0], p[1], "gui.pioneers.foundry.metal");
            }
            return true;
        }

        @Override
        void info(GuiMachine gui, BlockEntity be, List<StandardView.InfoRow> rows) {
            rows.add(new StandardView.InfoRow("fire", Component.translatable("gui.pioneers.api.info.fire_value", AgeViews.celsius(CrucibleFurnaceTile.BASE_C),
                  AgeViews.celsius(CrucibleFurnaceTile.BASE_C + CrucibleFurnaceTile.AIR_C)), 0xFFFF7A1F));
            rows.add(new StandardView.InfoRow("capacity", GuiMachine.literal(CrucibleFurnaceTile.CAPACITY + " mB"), PiMenuScreen.TEXT));
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_crucible";
        }
    }

    // ------------------------------------------------------------------
    // Table de coulee
    // ------------------------------------------------------------------

    private static final class Casting extends AgeViews.Base {

        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            CastingTableTile tile = StorageViews.as(CastingTableTile.class, be);
            if (tile == null) {
                return;
            }
            CastingUnit unit = tile.casting();
            StandardLayout l = unit.layout;
            boolean full = tile.amount() > 0;
            double solid = full ? tile.solid() : 0;
            int colour = full ? Metals.colour(tile.metal()) : 0x5A2A20;
            //Metal verse : de la couleur du metal en fusion au gris fige
            ratioGauge(gui, g, mouseX, mouseY, StandardLayout.POWER, full ? 1 - solid * 0.6 : 0, colour, full ? 0xFFB050 : 0x5A2A20,
                  List.of(Component.translatable(full ? "gui.pioneers.foundry.cooling" : "gui.pioneers.foundry.waiting"),
                        GuiMachine.percent(solid).copy().withStyle(ChatFormatting.GRAY)));
            StandardView.arrow(g, l.arrow, solid, 0x8FA3A0);
            List<Row> rows = new ArrayList<>();
            if (full) {
                rows.add(new Row(Component.translatable("gui.pioneers.foundry.metal"), Metals.nameOrMixed(tile.metal()), PiMenuScreen.SCREEN_TEXT));
                rows.add(new Row(Component.translatable("gui.pioneers.foundry.amount"), Component.literal(tile.amount() + " mB"), PiMenuScreen.SCREEN_TEXT));
            }
            Rect s = l.screen;
            gui.info(g, s.x(), s.y(), s.w(), s.h(), full ? FIRE : 0, Component.translatable(full ? "gui.pioneers.foundry.cooling" : "gui.pioneers.foundry.waiting"),
                  full ? GuiMachine.percent(solid) : Component.empty(), full ? solid : -1, 0x8FA3A0, rows, false);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            CastingTableTile tile = StorageViews.as(CastingTableTile.class, be);
            if (tile != null) {
                int[] m = tile.casting().layout.itemIn.get(0), o = tile.casting().layout.itemOut.get(0);
                gui.slotTip(mouseX, mouseY, m[0], m[1], "gui.pioneers.foundry.mold");
                gui.slotTip(mouseX, mouseY, o[0], o[1], "gui.pioneers.machine.out");
            }
            return true;
        }

        @Override
        void info(GuiMachine gui, BlockEntity be, List<StandardView.InfoRow> rows) {
            rows.add(new StandardView.InfoRow("poured_by", Component.translatable("gui.pioneers.api.info.poured_by_value"), PiMenuScreen.TEXT));
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_casting";
        }
    }

    // ------------------------------------------------------------------
    // Four de potier
    // ------------------------------------------------------------------

    private static final class Kiln extends AgeViews.Base {

        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            double c = gui.value(UiValues.HEAT) / 10.0, fire = gui.value(UiValues.BURN) / 1000.0;
            AgeViews.fireGauge(gui, g, mouseX, mouseY, StandardLayout.POWER, c, KilnTile.MAX_C, Component.translatable("gui.pioneers.api.info.kiln_fire"));
            GuiMachine.flame(g, KilnTile.FUEL_X + 1, KilnTile.FUEL_Y - 16, fire);
            Rect card = KilnTile.CARD;
            gui.info(g, card.x(), card.y(), card.w(), KilnTile.CARD_H, fire > 0 ? FIRE : 0, Component.translatable("gui.pioneers.foundry.fire"),
                  GuiMachine.percent(fire), -1, 0, List.of(), false);
            int firing = 0;
            for (int i = 0; i < 4; i++) {
                int[] p = KilnTile.warePos(i);
                long v = gui.value(UiValues.firing(i));
                if (v >= 0) {
                    firing++;
                }
                slotBar(g, p[0], p[1] + 17, v / 1000.0, 0xFF9A3A);
            }
            Rect s = StandardLayout.screen(StandardLayout.WIDTH);
            List<Row> rows = new ArrayList<>();
            rows.add(Row.of("gui.pioneers.api.info.firing", GuiMachine.literal(firing + " / 4")));
            gui.info(g, s.x(), s.y(), s.w(), s.h(), fire > 0 ? FIRE : 0, Component.translatable(fire > 0 ? "gui.pioneers.api.kiln.firing"
                  : "gui.pioneers.api.kiln.cold"), AgeViews.celsius(c), Math.min(1, c / KilnTile.MAX_C), FIRE, rows, false);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            gui.slotTip(mouseX, mouseY, KilnTile.FUEL_X, KilnTile.FUEL_Y, "gui.pioneers.machine.fuel");
            gui.slotTip(mouseX, mouseY, KilnTile.ASH_X, KilnTile.ASH_Y, "gui.pioneers.steam.ash");
            for (int i = 0; i < 4; i++) {
                int[] p = KilnTile.warePos(i);
                gui.slotTip(mouseX, mouseY, p[0], p[1], "gui.pioneers.api.kiln.ware");
            }
            return true;
        }

        @Override
        void info(GuiMachine gui, BlockEntity be, List<StandardView.InfoRow> rows) {
            rows.add(new StandardView.InfoRow("fire", AgeViews.celsius(KilnTile.MAX_C), 0xFFFF7A1F));
        }
    }
}

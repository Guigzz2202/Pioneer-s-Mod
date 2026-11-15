package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.multiblock.MultiblockLayout;
import fr.pioneers.api.multiblock.MultiblockValues;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.core.Texts;
import fr.pioneers.core.machine.MachineGui;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.multi.ControllerTile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * API Multibloc (lot 6) : CORPS SPECIAUX des controleurs a coeur propre (reacteurs a fission, fusion, calcul) dans l'interface v2 commune
 * des multiblocs ({@link MultiblockViews} : titre et pastilles, panneau de 196 au moins, inventaire dans le cadre, onglets a droite dont
 * Structure, zone voilee et erreur commune quand la structure n'est pas formee). Decision de Guillaume : « interface standard meme si speciale
 * pour eux ». Parties speciales : jauges propres (sodium, helium, gaz du plasma), ecran d'etat du coeur, rangee de commandes
 * ({@link MultiblockLayout#controls} : reacteurs : pilote / manuel, barres -10 -1 +1 +10, arret d'urgence, rearmement, arret automatique ;
 * fusion : chauffer / couper, risque). Valeurs lues PAR LEUR NOM ({@link MultiblockValues} : plus d'indice brut). Les commandes restent
 * actives structure defaite (un reacteur defait en marche se pilote encore).
 */
final class CoreViews {

    private static final int RED = 0xE04848;

    private CoreViews() {
    }

    /** Corps d'un coeur : jauges, fleche, ecran (forme seulement) et commandes. */
    interface Body {
        void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, ControllerTile tile, MultiblockLayout l, boolean formed);
    }

    private static long v(GuiMachine gui, fr.pioneers.api.machine.UiValues.Key key) {
        return gui.value(key);
    }

    /** Colonne de gauche commune : energie, thermometre (avec ses lignes), caloporteur ; caloporteur use a droite. */
    private static void column(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, ControllerTile tile, MultiblockLayout l, int target,
          List<Component> thermoTip, String coolantHelp) {
        MachineViews views = gui.views();
        Rect e = l.energy;
        gui.energy(g, mouseX, mouseY, e.x(), e.y(), e.w(), e.h());
        double kelvin = v(gui, MultiblockValues.TEMPERATURE) / 10.0;
        Rect t = l.thermometer;
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("industry.pioneers.gui.temperature", Math.round(kelvin)));
        tip.addAll(thermoTip);
        gui.thermometer(g, mouseX, mouseY, t.x(), t.y(), t.w(), t.h(), kelvin, target, tip);
        Rect c = l.coolant;
        views.fluidGauge(g, mouseX, mouseY, c.x(), c.y(), c.w(), c.h(), tile.coolantTank());
        if (gui.over(mouseX, mouseY, c.x(), c.y(), c.w(), c.h())) {
            gui.tooltip(List.of(Component.translatable("nuclear.pioneers.gui.water"), Component.translatable(coolantHelp).withStyle(ChatFormatting.GRAY)));
        }
        Rect co = l.coolantOut;
        views.coolantOutGauge(g, mouseX, mouseY, co.x(), co.y(), co.w(), co.h(), tile.coolantOutTank(), tile.steamOutTank());
    }

    // ------------------------------------------------------------------ reacteur a fission

    static final Body REACTOR = (gui, g, mouseX, mouseY, tile, l, formed) -> {
        IndustryMachine m = tile.machine;
        MachineViews views = gui.views();
        long scramT = v(gui, MultiblockValues.R_SCRAM_T), meltT = v(gui, MultiblockValues.R_MELT_T);
        long flags = v(gui, MultiblockValues.R_FLAGS);
        boolean scrammed = (flags & 1) != 0, auto = (flags & 2) != 0, alarm = (flags & 4) != 0;
        int mark = (int) Math.max(1, v(gui, MultiblockValues.R_MARK));
        column(gui, g, mouseX, mouseY, tile, l, (int) scramT, List.of(
              Component.translatable("nuclear.pioneers.gui.scram_at", scramT).withStyle(ChatFormatting.GRAY),
              Component.translatable("nuclear.pioneers.gui.melt_at", meltT).withStyle(ChatFormatting.RED),
              Component.translatable("nuclear.pioneers.gui.heat_help").withStyle(ChatFormatting.DARK_GRAY)), "nuclear.pioneers.gui.water_help");
        //0.17.1 : sodium primaire (MKII) ; les autres jauges d'entree a leur place
        for (int i = 0; i < l.fluidIn.size() && i < tile.fluidInTanks().size(); i++) {
            Rect r = l.fluidIn.get(i);
            views.fluidGauge(g, mouseX, mouseY, r.x(), r.y(), r.w(), r.h(), tile.fluidInTanks().get(i));
            if (mark == 2 && i == 0 && gui.over(mouseX, mouseY, r.x(), r.y(), r.w(), r.h())) {
                gui.tooltip(List.of(Component.translatable("nuclear.pioneers.gui.sodium"), Component.translatable("nuclear.pioneers.gui.sodium_help")
                      .withStyle(ChatFormatting.GRAY)));
            }
        }
        for (int i = 0; i < l.fluidOut.size() && i < tile.fluidOutTanks().size(); i++) {
            Rect r = l.fluidOut.get(i);
            views.fluidGauge(g, mouseX, mouseY, r.x(), r.y(), r.w(), r.h(), tile.fluidOutTanks().get(i));
        }
        double power = v(gui, MultiblockValues.R_POWER), max = Math.max(1, v(gui, MultiblockValues.R_MAX_POWER));
        double fraction = Math.min(1, power / max);
        MultiblockViews.arrow(g, l.arrow, fraction, alarm || scrammed ? RED : m.accent);
        //0.20.24 : diagnostic (ce qui empeche la puissance de monter) et pilote automatique
        long diag = v(gui, MultiblockValues.R_WHY);
        int why = (int) (diag & 0xFF);
        boolean pilot = (diag >> 8 & 1) != 0;
        if (formed) {
            Rect s = l.screen;
            Component name = Component.translatable(scrammed ? "nuclear.pioneers.gui.state.scram" : alarm ? "nuclear.pioneers.gui.state.alarm"
                  : why != 0 && why != 4 ? "nuclear.pioneers.gui.why." + why : power > max * 0.01
                  ? (pilot ? "nuclear.pioneers.gui.state.pilot" : "nuclear.pioneers.gui.state.power") : "nuclear.pioneers.gui.why.4");
            int chip = scrammed || alarm ? RED : power > max * 0.01 ? m.accent : 0;
            Component headline = Component.literal(String.format(Locale.ROOT, "%.2f MW", power * 20 / 1_000_000.0));
            long rods = v(gui, MultiblockValues.R_RODS), actual = v(gui, MultiblockValues.R_RODS_ACTUAL);
            List<GuiMachine.Row> rows = new ArrayList<>();
            rows.add(GuiMachine.Row.of(mark == 3 ? "nuclear.pioneers.gui.injection" : "nuclear.pioneers.gui.rods",
                  mark == 3 ? Component.translatable("nuclear.pioneers.gui.injection_value", rods, Math.round(actual / 10.0))
                        : Component.translatable("nuclear.pioneers.gui.rods_value", rods, Math.round(actual / 10.0), v(gui, MultiblockValues.R_DRIVES))));
            rows.add(GuiMachine.Row.of(mark == 3 ? "nuclear.pioneers.gui.cartridges" : "nuclear.pioneers.gui.fuel",
                  Component.translatable("nuclear.pioneers.gui.fuel_value", v(gui, MultiblockValues.R_FUEL), v(gui, MultiblockValues.R_CHANNELS),
                        Math.round(v(gui, MultiblockValues.R_BURNUP) / 10.0))));
            long charges = v(gui, MultiblockValues.R_CHARGES);
            int targets = (int) (charges & 0xFF), blankets = (int) (charges >> 8 & 0xFF);
            if (mark == 2) {
                rows.add(GuiMachine.Row.of("nuclear.pioneers.gui.sodium", Component.translatable("nuclear.pioneers.gui.sodium_value",
                      Math.round(v(gui, MultiblockValues.R_SODIUM) / 10.0), Math.round(v(gui, MultiblockValues.R_COOLING) / 10.0))));
            } else if (mark == 3) {
                rows.add(GuiMachine.Row.of("nuclear.pioneers.gui.field", Component.translatable("nuclear.pioneers.gui.field_value",
                      Math.round(v(gui, MultiblockValues.R_FIELD) / 10.0), Texts.format(v(gui, MultiblockValues.R_ELECTRIC)))));
            } else if (targets > 0) {
                rows.add(GuiMachine.Row.of("nuclear.pioneers.gui.targets", Component.translatable("nuclear.pioneers.gui.targets_value", targets,
                      Math.round(v(gui, MultiblockValues.R_CHARGE) / 10.0))));
            }
            gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, name, headline, fraction, alarm || scrammed ? RED : m.accent, rows, false);
            ComputeViews.chipBadge(g, s.x() + s.w() - 13, s.y() + s.h() - 13, v(gui, MultiblockValues.CHIP));
            if (gui.over(mouseX, mouseY, s.x(), s.y(), s.w(), s.h())) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("block.pioneers." + m.multiId()));
                tip.add(Component.translatable("nuclear.pioneers.gui.power_tip", Texts.format(Math.round(power)), Texts.format(Math.round(max)))
                      .withStyle(ChatFormatting.GRAY));
                if (why != 0) {
                    tip.add(Component.translatable("nuclear.pioneers.gui.why_help." + why).withStyle(ChatFormatting.GOLD));
                }
                tip.add(Component.translatable("nuclear.pioneers.gui.rule_rods").withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable("nuclear.pioneers.gui.rule_fuel").withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable("nuclear.pioneers.gui.rule_redstone").withStyle(ChatFormatting.DARK_GRAY));
                if (mark == 2) {
                    long parts = v(gui, MultiblockValues.R_PUMPS);
                    tip.add(Component.translatable("nuclear.pioneers.gui.mk2_parts", parts & 0xFF, parts >> 8 & 0xFF).withStyle(ChatFormatting.GRAY));
                    tip.add(Component.translatable("nuclear.pioneers.gui.rule_sodium").withStyle(ChatFormatting.GRAY));
                    if (blankets > 0 || targets > 0) {
                        tip.add(Component.translatable("nuclear.pioneers.gui.charges", blankets, targets, Math.round(v(gui, MultiblockValues.R_CHARGE) / 10.0))
                              .withStyle(ChatFormatting.GRAY));
                    }
                } else if (mark == 3) {
                    long parts = v(gui, MultiblockValues.R_COILS);
                    tip.add(Component.translatable("nuclear.pioneers.gui.mk3_parts", parts & 0xFF, parts >> 8 & 0xFF).withStyle(ChatFormatting.GRAY));
                    tip.add(Component.translatable("nuclear.pioneers.gui.rule_field").withStyle(ChatFormatting.GRAY));
                }
                if (v(gui, MultiblockValues.R_MELT) > 0) {
                    tip.add(Component.translatable("nuclear.pioneers.gui.melting", Math.round(v(gui, MultiblockValues.R_MELT) / 10.0)).withStyle(ChatFormatting.RED));
                }
                ComputeViews.chipTip(tip, v(gui, MultiblockValues.CHIP));
                gui.tooltip(tip);
            }
        }
        reactorControls(gui, g, mouseX, mouseY, l.controls, m, scrammed, auto, pilot, scramT);
    };

    /** Rangee de commandes du reacteur : pilote (0.20.24), barres (-10, -1, +1, +10 : a la main, coupe le pilote), arret d'urgence, rearmement, auto. */
    private static void reactorControls(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, Rect r, IndustryMachine m, boolean scrammed, boolean auto,
          boolean pilot, long scramT) {
        if (r == null) {
            return;
        }
        int x = r.x(), y = r.y(), h = r.h();
        gui.steelButton(g, mouseX, mouseY, x, y, 34, h, Component.translatable(pilot ? "nuclear.pioneers.gui.pilot_on" : "nuclear.pioneers.gui.pilot_off"),
              true, () -> gui.send(MachineGui.CUSTOM + ControllerTile.REACTOR_PILOT));
        if (pilot) {
            g.fill(x + 2, y + h - 3, x + 32, y + h - 2, 0xFF000000 | m.accent);
        }
        if (gui.over(mouseX, mouseY, x, y, 34, h)) {
            gui.tooltip(List.of(Component.translatable("nuclear.pioneers.gui.pilot_help", v(gui, MultiblockValues.R_SETPOINT))));
        }
        int bx = x + 36;
        gui.steelButton(g, mouseX, mouseY, bx, y, 18, h, "-10", () -> gui.send(MachineGui.CUSTOM + ControllerTile.REACTOR_RODS_DOWN10));
        gui.steelButton(g, mouseX, mouseY, bx + 19, y, 14, h, "-1", () -> gui.send(MachineGui.CUSTOM + ControllerTile.REACTOR_RODS_DOWN1));
        gui.screenAt(g, bx + 34, y, 30, h);
        GuiDraw.center(g, gui.font(), Component.literal(v(gui, MultiblockValues.R_RODS) + " %"), bx + 49, y + (h - 6) / 2F, 0.75F,
              pilot ? 0xFF8FD8A0 : 0xFFD8E4EC, 28);
        gui.steelButton(g, mouseX, mouseY, bx + 65, y, 14, h, "+1", () -> gui.send(MachineGui.CUSTOM + ControllerTile.REACTOR_RODS_UP1));
        gui.steelButton(g, mouseX, mouseY, bx + 80, y, 18, h, "+10", () -> gui.send(MachineGui.CUSTOM + ControllerTile.REACTOR_RODS_UP10));
        if (gui.over(mouseX, mouseY, bx, y, 98, h)) {
            gui.tooltip(List.of(Component.translatable("nuclear.pioneers.gui.rods_help"),
                  Component.translatable("nuclear.pioneers.gui.rods_manual").withStyle(ChatFormatting.GRAY)));
        }
        int sx = bx + 101, sw = 46;
        redButton(gui, g, mouseX, mouseY, sx, y, sw, h, Component.translatable("nuclear.pioneers.gui.scram"),
              () -> gui.send(MachineGui.CUSTOM + ControllerTile.REACTOR_SCRAM));
        gui.steelButton(g, mouseX, mouseY, sx + sw + 2, y, 30, h, Component.translatable("nuclear.pioneers.gui.reset"), scrammed,
              () -> gui.send(MachineGui.CUSTOM + ControllerTile.REACTOR_RESET));
        int ax = sx + sw + 34, aw = r.x() + r.w() - ax;
        gui.steelButton(g, mouseX, mouseY, ax, y, aw, h, Component.translatable(auto ? "nuclear.pioneers.gui.auto_on" : "nuclear.pioneers.gui.auto_off"),
              true, () -> gui.send(MachineGui.CUSTOM + ControllerTile.REACTOR_AUTO));
        if (gui.over(mouseX, mouseY, ax, y, aw, h)) {
            gui.tooltip(List.of(Component.translatable("nuclear.pioneers.gui.auto_help", scramT)));
        }
    }

    /** Bouton rouge (arret d'urgence, arret commande). */
    private static void redButton(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, Component label, Runnable action) {
        boolean hover = gui.over(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, 0xFF14181E);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? 0xFFD03A30 : 0xFFA82820);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFE86050);
        GuiDraw.center(g, gui.font(), label, x + w / 2F, y + (h - 6) / 2F, 0.7F, 0xFFFFFFFF, w - 4);
        gui.click(x, y, w, h, action);
    }

    // ------------------------------------------------------------------ fusion

    static final Body FUSION = (gui, g, mouseX, mouseY, tile, l, formed) -> {
        IndustryMachine m = tile.machine;
        MachineViews views = gui.views();
        long flags = v(gui, MultiblockValues.F_FLAGS);
        boolean heating = (flags & 1) != 0, disrupted = (flags & 2) != 0, ignited = (flags & 4) != 0, stopping = (flags & 8) != 0;
        boolean noHelium = (flags & 16) != 0, noEnergy = (flags & 32) != 0;
        boolean tokamak = v(gui, MultiblockValues.F_MARK) == 4;
        double plasma = v(gui, MultiblockValues.F_PLASMA) / 10.0, ignition = Math.max(1, v(gui, MultiblockValues.F_IGNITION));
        double peak = Math.max(1, v(gui, MultiblockValues.F_PEAK));
        double power = v(gui, MultiblockValues.F_POWER), max = Math.max(1, v(gui, MultiblockValues.F_MAX));
        double field = v(gui, MultiblockValues.F_FIELD) / 1000.0, risk = v(gui, MultiblockValues.F_RISK) / 1000.0;
        long parts = v(gui, MultiblockValues.F_PARTS);
        int nominal = (int) v(gui, MultiblockValues.NOMINAL);
        column(gui, g, mouseX, mouseY, tile, l, nominal, List.of(
              Component.translatable("industry.pioneers.gui.nominal", nominal).withStyle(ChatFormatting.GRAY),
              Component.translatable(tokamak ? "fusion.pioneers.gui.heat_help_tokamak" : "fusion.pioneers.gui.heat_help_stellarator")
                    .withStyle(ChatFormatting.DARK_GRAY)), "fusion.pioneers.gui.water_help");
        //Helium liquide des bobines
        for (int i = 0; i < l.fluidIn.size() && i < tile.fluidInTanks().size(); i++) {
            Rect he = l.fluidIn.get(i);
            views.fluidGauge(g, mouseX, mouseY, he.x(), he.y(), he.w(), he.h(), tile.fluidInTanks().get(i));
            if (i == 0 && gui.over(mouseX, mouseY, he.x(), he.y(), he.w(), he.h())) {
                gui.tooltip(List.of(Component.translatable("fusion.pioneers.gui.helium"), Component.translatable("fusion.pioneers.gui.helium_help",
                      parts & 0xFF).withStyle(ChatFormatting.GRAY)));
            }
        }
        //Gaz : deuterium et tritium (ou helium 3) en entree ; helium (cendres et evaporation des bobines) et tritium produit en sortie
        String[] ins = {"fusion.pioneers.gui.gas.deuterium", tokamak ? "fusion.pioneers.gui.gas.tritium" : "fusion.pioneers.gui.gas.helium_3"};
        for (int i = 0; i < l.gasIn.size() && i < tile.gasInTanks().size(); i++) {
            Rect r = l.gasIn.get(i);
            views.gasGauge(g, mouseX, mouseY, r.x(), r.y(), r.w(), r.h(), tile.gasInTanks().get(i), Component.translatable(ins[Math.min(i, 1)]));
        }
        String[] outs = {"fusion.pioneers.gui.gas.helium", "fusion.pioneers.gui.gas.bred"};
        for (int i = 0; i < l.gasOut.size() && i < tile.gasOutTanks().size(); i++) {
            Rect r = l.gasOut.get(i);
            views.gasGauge(g, mouseX, mouseY, r.x(), r.y(), r.w(), r.h(), tile.gasOutTanks().get(i), Component.translatable(outs[Math.min(i, 1)]));
        }
        double fraction = Math.min(1, power / max);
        boolean alarm = disrupted || risk >= 0.5;
        MultiblockViews.arrow(g, l.arrow, fraction, alarm ? RED : m.accent);
        if (formed) {
            //Ecran : etat, temperature du plasma, Q, puissance, champ, densite
            Rect s = l.screen;
            String state = disrupted ? "disruption" : stopping ? "stopping" : ignited ? (heating ? "burning_heated" : "burning")
                  : heating ? (field < 0.9 ? "field" : "heating") : plasma > 1 ? "cooling" : "off";
            Component name = Component.translatable("fusion.pioneers.gui.state." + state);
            int chip = alarm ? RED : ignited ? m.accent : heating ? 0xE0A030 : 0;
            Component headline = Component.translatable("fusion.pioneers.gui.plasma_value", String.format(Locale.ROOT, "%.0f", plasma));
            long q = v(gui, MultiblockValues.F_Q);
            Component qText = q < 0 ? Component.literal("∞") : Component.literal(String.format(Locale.ROOT, "%.1f", q / 100.0));
            List<GuiMachine.Row> rows = new ArrayList<>();
            rows.add(GuiMachine.Row.of("fusion.pioneers.gui.power", Component.translatable("fusion.pioneers.gui.power_value",
                  String.format(Locale.ROOT, "%.2f", power * 20 / 1_000_000.0), qText)));
            rows.add(GuiMachine.Row.of("fusion.pioneers.gui.field", Component.translatable("fusion.pioneers.gui.field_value", Math.round(field * 100),
                  Math.round(v(gui, MultiblockValues.F_DENSITY) / 10.0))));
            if (tokamak) {
                rows.add(GuiMachine.Row.of("fusion.pioneers.gui.breeding", Component.translatable("fusion.pioneers.gui.breeding_value",
                      String.format(Locale.ROOT, "%.2f", v(gui, MultiblockValues.F_BREEDING) / 1000.0), parts >> 16 & 0xFF)));
            } else {
                rows.add(GuiMachine.Row.of("fusion.pioneers.gui.electric", Component.translatable("fusion.pioneers.gui.electric_value",
                      Texts.format(v(gui, MultiblockValues.F_ELECTRIC)), Math.round(v(gui, MultiblockValues.F_SHARE) / 10.0))));
            }
            gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, name, headline, Math.min(1, plasma / peak), alarm ? RED : m.accent, rows, false);
            ComputeViews.chipBadge(g, s.x() + s.w() - 13, s.y() + s.h() - 13, v(gui, MultiblockValues.CHIP));
            if (gui.over(mouseX, mouseY, s.x(), s.y(), s.w(), s.h())) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("block.pioneers." + m.multiId()));
                tip.add(Component.translatable("fusion.pioneers.gui.plasma_tip", Math.round(plasma), Math.round(ignition), Math.round(peak))
                      .withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable("nuclear.pioneers.gui.power_tip", Texts.format(Math.round(power)), Texts.format(Math.round(max)))
                      .withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable("fusion.pioneers.gui.heating_tip", Texts.format(v(gui, MultiblockValues.F_HEATING)),
                      (parts >> 8 & 0xFF) + (tokamak ? 1 : 2)).withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable(tokamak ? "fusion.pioneers.gui.rule_tokamak" : "fusion.pioneers.gui.rule_stellarator")
                      .withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable("fusion.pioneers.gui.rule_coils").withStyle(ChatFormatting.GRAY));
                if (noHelium) {
                    tip.add(Component.translatable("fusion.pioneers.gui.no_helium").withStyle(ChatFormatting.RED));
                }
                if (noEnergy) {
                    tip.add(Component.translatable("fusion.pioneers.gui.no_energy").withStyle(ChatFormatting.RED));
                }
                if (risk > 0) {
                    tip.add(Component.translatable("fusion.pioneers.gui.risk", Math.round(risk * 100)).withStyle(risk >= 0.5 ? ChatFormatting.RED
                          : ChatFormatting.GOLD));
                }
                ComputeViews.chipTip(tip, v(gui, MultiblockValues.CHIP));
                gui.tooltip(tip);
            }
        }
        //Commandes : chauffer / arreter le chauffage, couper (arret commande), voyant de risque
        Rect r = l.controls;
        if (r == null) {
            return;
        }
        int x = r.x(), y = r.y(), h = r.h(), bw = 80;
        gui.steelButton(g, mouseX, mouseY, x, y, bw, h, Component.translatable(heating ? "fusion.pioneers.gui.heat_off" : "fusion.pioneers.gui.heat_on"),
              true, () -> gui.send(MachineGui.CUSTOM + ControllerTile.FUSION_HEAT));
        if (gui.over(mouseX, mouseY, x, y, bw, h)) {
            gui.tooltip(List.of(Component.translatable("fusion.pioneers.gui.heat_help")));
        }
        int sx = x + bw + 4, sw = 54;
        redButton(gui, g, mouseX, mouseY, sx, y, sw, h, Component.translatable("fusion.pioneers.gui.stop"),
              () -> gui.send(MachineGui.CUSTOM + ControllerTile.FUSION_STOP));
        if (gui.over(mouseX, mouseY, sx, y, sw, h)) {
            gui.tooltip(List.of(Component.translatable("fusion.pioneers.gui.stop_help")));
        }
        int rx = sx + sw + 4, rw = r.x() + r.w() - rx;
        gui.screenAt(g, rx, y, rw, h);
        int fill = (int) Math.round((rw - 2) * Math.min(1, risk));
        if (fill > 0) {
            g.fill(rx + 1, y + h - 3, rx + 1 + fill, y + h - 1, risk >= 0.5 ? 0xFFE04848 : 0xFFE0A030);
        }
        GuiDraw.center(g, gui.font(), Component.translatable("fusion.pioneers.gui.risk_short", Math.round(risk * 100)),
              rx + rw / 2F, y + (h - 6) / 2F, 0.7F, risk >= 0.5 ? 0xFFFF8080 : 0xFFD8E4EC, rw - 4);
    };

    // ------------------------------------------------------------------ calcul (centre de donnees, ordinateur quantique)

    static final Body COMPUTE = (gui, g, mouseX, mouseY, tile, l, formed) -> {
        MachineViews views = gui.views();
        boolean quantum = v(gui, MultiblockValues.C_KIND) == 2;
        int status = (int) v(gui, MultiblockValues.STATUS);
        double capacity = v(gui, MultiblockValues.C_CAPACITY) / 10.0, served = v(gui, MultiblockValues.C_SERVED) / 10.0;
        double full = v(gui, MultiblockValues.C_FULL) / 10.0, load = v(gui, MultiblockValues.C_LOAD) / 1000.0;
        double factor = v(gui, MultiblockValues.C_FACTOR) / 1000.0;
        long units = v(gui, MultiblockValues.C_UNITS) & 0xFF, coolers = v(gui, MultiblockValues.C_UNITS) >> 8 & 0xFF;
        long power = v(gui, MultiblockValues.C_POWER);
        String unit = quantum ? "QOPS" : "GOPS";
        //1.0.4-beta (lot 31) : salle blanche (centre de donnees, ordinateur quantique) : accent gris argent, pas la couleur de la machine
        int accent = gui.skinAccent(tile.machine.accent);
        Rect e = l.energy;
        gui.energy(g, mouseX, mouseY, e.x(), e.y(), e.w(), e.h());
        Rect t = l.thermometer;
        if (quantum) {
            //Thermometre de la chambre de melange : de l'ambiante aux millikelvins
            double mk = v(gui, MultiblockValues.C_TELEMETRY);
            gui.frame(g, t.x(), t.y(), t.w(), t.h());
            int fill = (int) Math.round((t.h() - 2) * Math.max(0, Math.min(1, 1 - factor)));
            int cold = factor >= 1 ? 0xFF7FD8FF : 0xFFB0C8E0;
            if (fill > 0) {
                g.fill(t.x() + 1, t.y() + t.h() - 1 - fill, t.x() + t.w() - 1, t.y() + t.h() - 1, 0xFFE07050);
            }
            g.fill(t.x() + 1, t.y() + t.h() - 3, t.x() + t.w() - 1, t.y() + t.h() - 1, cold);
            if (gui.over(mouseX, mouseY, t.x(), t.y(), t.w(), t.h())) {
                gui.tooltip(List.of(Component.translatable("compute.pioneers.gui.mixing", mk >= 1_000_000 ? String.format(Locale.ROOT, "%.0f K", mk / 1000)
                            : mk >= 1000 ? String.format(Locale.ROOT, "%.1f K", mk / 1000) : String.format(Locale.ROOT, "%.0f mK", mk)),
                      Component.translatable("compute.pioneers.gui.mixing_help").withStyle(ChatFormatting.GRAY)));
            }
        } else {
            double kelvin = v(gui, MultiblockValues.TEMPERATURE) / 10.0;
            int nominal = (int) v(gui, MultiblockValues.NOMINAL);
            gui.thermometer(g, mouseX, mouseY, t.x(), t.y(), t.w(), t.h(), kelvin, nominal,
                  List.of(Component.translatable("industry.pioneers.gui.temperature", Math.round(kelvin)),
                        Component.translatable("industry.pioneers.gui.nominal", nominal).withStyle(ChatFormatting.GRAY),
                        Component.translatable("compute.pioneers.gui.heat_help").withStyle(ChatFormatting.DARK_GRAY)));
        }
        Rect c = l.coolant;
        views.fluidGauge(g, mouseX, mouseY, c.x(), c.y(), c.w(), c.h(), tile.coolantTank());
        for (int i = 0; i < l.fluidIn.size() && i < tile.fluidInTanks().size(); i++) {
            Rect r = l.fluidIn.get(i);
            views.fluidGauge(g, mouseX, mouseY, r.x(), r.y(), r.w(), r.h(), tile.fluidInTanks().get(i));
            if (gui.over(mouseX, mouseY, r.x(), r.y(), r.w(), r.h()) && tile.fluidInTanks().get(i).isEmpty()) {
                gui.tooltip(List.of(Component.translatable(quantum ? "compute.pioneers.gui.helium" : "compute.pioneers.gui.immersion")));
            }
        }
        for (int i = 0; i < l.gasIn.size() && i < tile.gasInTanks().size(); i++) {
            Rect r = l.gasIn.get(i);
            views.gasGauge(g, mouseX, mouseY, r.x(), r.y(), r.w(), r.h(), tile.gasInTanks().get(i), Component.translatable("compute.pioneers.gui.helium_3"));
        }
        for (int i = 0; i < l.fluidOut.size() && i < tile.fluidOutTanks().size(); i++) {
            Rect r = l.fluidOut.get(i);
            views.fluidGauge(g, mouseX, mouseY, r.x(), r.y(), r.w(), r.h(), tile.fluidOutTanks().get(i));
        }
        for (int i = 0; i < l.gasOut.size() && i < tile.gasOutTanks().size(); i++) {
            Rect r = l.gasOut.get(i);
            views.gasGauge(g, mouseX, mouseY, r.x(), r.y(), r.w(), r.h(), tile.gasOutTanks().get(i), Component.translatable("gui.pioneers.machine.out"));
        }
        Rect co = l.coolantOut;
        views.coolantOutGauge(g, mouseX, mouseY, co.x(), co.y(), co.w(), co.h(), tile.coolantOutTank(), tile.steamOutTank());
        //Charge : barre a la place de la fleche
        Rect a = l.arrow;
        gui.frame(g, a.x(), a.y(), a.w(), a.h());
        int lw = (int) Math.round((a.w() - 2) * Math.max(0, Math.min(1, load)));
        if (lw > 0) {
            g.fill(a.x() + 1, a.y() + 1, a.x() + 1 + lw, a.y() + a.h() - 1, 0xFF000000 | accent);
        }
        if (gui.over(mouseX, mouseY, a.x(), a.y(), a.w(), a.h())) {
            gui.tooltip(List.of(Component.translatable("compute.pioneers.gui.load", Math.round(load * 100))));
        }
        if (!formed) {
            return;
        }
        Rect s = l.screen;
        Component name = Component.translatable(quantum ? (factor >= 1 ? "compute.pioneers.gui.state.coherent" : status == ControllerTile.WORKING
              ? "compute.pioneers.gui.state.cooling" : "compute.pioneers.gui.state.decoherent") : status == ControllerTile.OVERHEAT
              ? "compute.pioneers.gui.state.throttled" : "industry.pioneers.multi.status." + status);
        int chip = status == ControllerTile.WORKING ? accent : status == ControllerTile.IDLE ? 0 : status == ControllerTile.OVERHEAT ? 0xE0A030 : RED;
        Component headline = Component.literal(GuiMachine.compact(served) + " / " + GuiMachine.compact(capacity) + " " + unit);
        List<GuiMachine.Row> rows = new ArrayList<>();
        if (quantum) {
            rows.add(GuiMachine.Row.of("compute.pioneers.gui.matrices", Component.translatable("compute.pioneers.gui.matrices_value", units,
                  GuiMachine.compact(full), Math.round(factor * 100))));
            long flags = v(gui, MultiblockValues.C_SUPPLY);
            rows.add(new GuiMachine.Row(Component.translatable("compute.pioneers.gui.supply"), Component.translatable("compute.pioneers.gui.supply_value",
                  yes(flags & 1), yes(flags & 2), Texts.energy(power)), (flags & 3) == 3 ? PiMenuScreen.SCREEN_TEXT : 0xFFFF6A5A));
        } else {
            rows.add(GuiMachine.Row.of("compute.pioneers.gui.racks", Component.translatable("compute.pioneers.gui.racks_value", units, coolers,
                  GuiMachine.compact(full))));
            rows.add(new GuiMachine.Row(Component.translatable("compute.pioneers.gui.power"), Component.translatable("compute.pioneers.gui.power_value",
                  Texts.energy(power), Math.round(factor * 100)), factor < 1 ? 0xFFFFAA00 : PiMenuScreen.SCREEN_TEXT));
        }
        gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, name, headline, load, accent, rows, false);
        ComputeViews.chipBadge(g, s.x() + s.w() - 13, s.y() + s.h() - 13, v(gui, MultiblockValues.CHIP));
        if (gui.over(mouseX, mouseY, s.x(), s.y(), s.w(), s.h())) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("block.pioneers." + tile.machine.multiId()));
            //Lot 31 : salle blanche : chiffres de SA structure (niveau fixe) au lieu des regles « x 2^tier » ; lot 32 : le centre de donnees et
            //l'ordinateur quantique sont TOUJOURS des salles blanches : plus de regle « x 2^tier » (cles retirees)
            int level = MultiblockViews.cleanroomTier(gui);
            if (quantum) {
                tip.add(Component.translatable("compute.pioneers.gui.rule_quantum_cleanroom",
                      GuiMachine.compact(fr.pioneers.tech.compute.ComputeCore.MATRIX_QOPS * (1 << level)), Texts.grouped(fr.pioneers.tech.compute.ComputeCore.MATRIX_FE << 2 * level))
                      .withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable("compute.pioneers.gui.rule_helium").withStyle(ChatFormatting.GRAY));
            } else {
                tip.add(Component.translatable("compute.pioneers.gui.rule_racks_cleanroom",
                      GuiMachine.compact(fr.pioneers.tech.compute.ComputeCore.RACK_GOPS * (1 << level)), Texts.grouped(fr.pioneers.tech.compute.ComputeCore.RACK_FE << 2 * level))
                      .withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable("compute.pioneers.gui.rule_heat").withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable("compute.pioneers.gui.telemetry", String.format(Locale.ROOT, "%.1f", v(gui, MultiblockValues.C_TELEMETRY) / 10.0))
                      .withStyle(ChatFormatting.DARK_AQUA));
            }
            tip.add(Component.translatable("compute.pioneers.gui.rule_network").withStyle(ChatFormatting.DARK_GRAY));
            ComputeViews.chipTip(tip, v(gui, MultiblockValues.CHIP));
            gui.tooltip(tip);
        }
    };

    private static Component yes(long flag) {
        return Component.literal(flag != 0 ? "✔" : "✘");
    }
}

package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.compute.AiChips;
import fr.pioneers.compute.AiLabTile;
import fr.pioneers.compute.FactoryAiCoreTile;
import fr.pioneers.core.Texts;
import fr.pioneers.core.machine.DataMachineTile;
import fr.pioneers.core.machine.MachineGui;
import fr.pioneers.data.DataUnits;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Vues du calcul et de l'IA (0.17.2) : puce d'IA des controleurs (badge et infobulle, lus par {@code CoreViews}) ; API Machine (lot 8b) :
 * vue {@code ai} en VRAIE v2 du laboratoire d'IA (reserve et batterie, puce, carte du modele vise, modele, reservoir, ecran d'etat) et du noyau
 * d'usine (puce, carte de l'etat, journal, « Delier »), valeurs NOMMEES ({@code AiLabTile}, {@code FactoryAiCoreTile}).
 */
final class ComputeViews {

    private static final int GREEN = 0xFF55FF88, RED = 0xFFFF6A5A, GOLD = 0xFFFFAA00, AQUA = 0xFF55DDFF, VIOLET = 0xFFE08CFF;
    /** Couleurs des modeles (expert, reseau, fondation). */
    private static final int[] MODEL = {0x9AA4AE, 0x78E678, 0x5AC8FF, 0xE68CFF};

    private ComputeViews() {
    }

    private static Component model(int level) {
        return Component.translatable("item.pioneers." + AiChips.IDS[Math.max(0, Math.min(3, level))]);
    }

    private static String num(double v) {
        return GuiMachine.compact(v);
    }

    // ------------------------------------------------------------------
    // Puce d'IA des controleurs : valeur 60 (v 64) = niveau installe | niveau du noyau superviseur << 4
    // ------------------------------------------------------------------

    static void chipTip(List<Component> tip, long value) {
        int own = (int) (value & 15), sup = (int) (value >> 4 & 15);
        if (own > 0) {
            tip.add(Component.translatable("ai.pioneers.gui.chip", model(own), Component.translatable("ai.pioneers.effect." + own))
                  .withStyle(ChatFormatting.AQUA));
            tip.add(Component.translatable("ai.pioneers.gui.chip_remove").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tip.add(Component.translatable("ai.pioneers.gui.chip_none").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (sup > 0) {
            tip.add(Component.translatable("ai.pioneers.gui.supervised", model(sup), Component.translatable("ai.pioneers.effect." + sup))
                  .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** Petite puce dans le coin de l'ecran (puce installee, ou celle du noyau superviseur). */
    static void chipBadge(GuiGraphics g, int x, int y, long value) {
        int level = Math.max((int) (value & 15), (int) (value >> 4 & 15));
        if (level <= 0) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 100);
        g.pose().scale(0.75F, 0.75F, 1);
        g.renderItem(new ItemStack(AiChips.item(level)), 0, 0);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------
    // Laboratoire d'IA, noyau d'usine (lot 8b : vraie v2, valeurs nommees, vue {@code ai} choisie par la fiche)
    // ------------------------------------------------------------------

    /** Vue {@code ai} : laboratoire d'IA ({@code ai_lab}) et noyau d'usine ({@code factory_ai_core}). */
    static final MachineScreens.View VIEW = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            FactoryAiCoreTile core = StorageViews.as(FactoryAiCoreTile.class, be);
            if (core != null) {
                core(gui, g, mouseX, mouseY, core);
            } else if (StorageViews.as(AiLabTile.class, be) != null) {
                lab(gui, g, mouseX, mouseY);
            }
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            if (StorageViews.as(AiLabTile.class, be) != null) {
                gui.slotTip(mouseX, mouseY, AiLabTile.IN_X, AiLabTile.IN_Y, "ai.pioneers.gui.lab_in");
                gui.slotTip(mouseX, mouseY, AiLabTile.OUT_X, AiLabTile.OUT_Y, "gui.pioneers.machine.out");
                gui.slotTip(mouseX, mouseY, AiLabTile.BATTERY_X, AiLabTile.BATTERY_Y, "gui.pioneers.machine.battery");
            } else {
                gui.slotTip(mouseX, mouseY, FactoryAiCoreTile.CHIP_X, FactoryAiCoreTile.CHIP_Y, "ai.pioneers.gui.core_in");
                gui.slotTip(mouseX, mouseY, FactoryAiCoreTile.BATTERY_X, FactoryAiCoreTile.BATTERY_Y, "gui.pioneers.machine.battery");
            }
            return true;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity be) {
            return ScienceViews.energyRows(gui);
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_network";
        }
    };

    /** Laboratoire : etat, modele vise, donnees, telemetrie, GOPS.s, QOPS.s (faits / demandes), accordes x10, reservoir. */
    private static void lab(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY) {
        gui.energy(g, mouseX, mouseY, 8, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.ENERGY_H);
        int status = (int) gui.value(UiValues.STATUS), target = (int) gui.value(AiLabTile.TARGET_MODEL);
        long data = gui.value(AiLabTile.DATA_DONE), dataNeed = gui.value(AiLabTile.DATA_NEED), tele = gui.value(AiLabTile.TELE_DONE);
        long teleNeed = gui.value(AiLabTile.TELE_NEED), gops = gui.value(AiLabTile.GOPS_DONE), gopsNeed = gui.value(AiLabTile.GOPS_NEED);
        long qops = gui.value(AiLabTile.QOPS_DONE), qopsNeed = gui.value(AiLabTile.QOPS_NEED);
        double grantG = gui.value(AiLabTile.GRANT_G) / 10.0, grantQ = gui.value(AiLabTile.GRANT_Q) / 10.0;
        Rect c = AiLabTile.CARD;
        //Fleches puce -> carte -> modele
        g.fill(AiLabTile.IN_X + 17, AiLabTile.IN_Y + 7, c.x(), AiLabTile.IN_Y + 9, 0xFF3C4A57);
        g.fill(c.x() + c.w(), AiLabTile.OUT_Y + 7, AiLabTile.OUT_X - 1, AiLabTile.OUT_Y + 9, 0xFF3C4A57);
        double progress = 0;
        int parts = 0;
        if (target > 0) {
            progress += dataNeed <= 0 ? 1 : Math.min(1, data / (double) dataNeed);
            progress += gopsNeed <= 0 ? 1 : Math.min(1, gops / (double) gopsNeed);
            parts = 2;
            if (qopsNeed > 0) {
                progress += Math.min(1, qops / (double) qopsNeed);
                parts++;
            }
            progress /= parts;
        }
        int color = MODEL[Math.max(0, Math.min(3, target))];
        //Carte : modele vise et ce qu'il demande
        List<GuiMachine.Row> need = new ArrayList<>();
        if (target > 0) {
            need.add(new GuiMachine.Row(Component.translatable("ai.pioneers.lab.data"), Component.literal(DataUnits.format(data) + " / "
                  + DataUnits.format(dataNeed)), data >= dataNeed ? GREEN : PiMenuScreen.SCREEN_TEXT));
            need.add(new GuiMachine.Row(Component.translatable("ai.pioneers.lab.gops"), Component.literal(Texts.format(gops) + " / "
                  + Texts.format(gopsNeed)), gops >= gopsNeed ? GREEN : PiMenuScreen.SCREEN_TEXT));
            if (qopsNeed > 0) {
                need.add(new GuiMachine.Row(Component.translatable("ai.pioneers.lab.qops"), Component.literal(Texts.format(qops) + " / "
                      + Texts.format(qopsNeed)), qops >= qopsNeed ? GREEN : PiMenuScreen.SCREEN_TEXT));
            }
            if (teleNeed > 0) {
                need.add(new GuiMachine.Row(Component.translatable("ai.pioneers.lab.telemetry"), Component.literal(DataUnits.format(tele) + " / "
                      + DataUnits.format(teleNeed)), tele >= teleNeed ? GREEN : AQUA));
            }
        } else {
            need.add(GuiMachine.Row.of("ai.pioneers.lab.hint", Component.literal("")));
        }
        gui.info(g, c.x(), c.y(), c.w(), c.h(), target > 0 ? color : 0, target > 0 ? Component.translatable("ai.pioneers.lab.target", model(target))
              : Component.translatable("ai.pioneers.lab.status." + status), Component.empty(), -1, color, need, false);
        if (gui.over(mouseX, mouseY, c.x(), c.y(), c.w(), c.h())) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("ai.pioneers.lab.rule_1").withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("ai.pioneers.lab.rule_2").withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("ai.pioneers.lab.rule_3").withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("ai.pioneers.lab.rule_4").withStyle(ChatFormatting.DARK_GRAY));
            gui.tooltip(tip);
        }
        //Reservoir de donnees
        Rect t = AiLabTile.GAUGE;
        gui.frame(g, t.x(), t.y(), t.w(), t.h());
        long stored = gui.value(UiValues.dataStored(0));
        var type = DataMachineTile.typeById(gui.value(UiValues.dataType(0)));
        int filled = (int) Math.round((t.h() - 2) * Math.min(1, stored / (double) AiLabTile.TANK));
        if (filled > 0 && type != null) {
            g.fill(t.x() + 1, t.y() + t.h() - 1 - filled, t.x() + t.w() - 1, t.y() + t.h() - 1, 0xFF000000 | type.getColor());
        }
        if (gui.over(mouseX, mouseY, t.x(), t.y(), t.w(), t.h())) {
            gui.tooltip(List.of(type == null ? Component.translatable("data.pioneers.tank.empty") : type.getName(),
                  Component.translatable("data.pioneers.gauge", DataUnits.format(stored), DataUnits.format(AiLabTile.TANK))));
        }
        //Ecran d'etat : etat, avancement, calcul accorde
        Rect s = StandardLayout.screen(StandardLayout.WIDTH);
        int chip = status == AiLabTile.TRAINING ? color : status == AiLabTile.NO_CHIP || status == AiLabTile.DONE ? 0 : 0xE0A030;
        List<GuiMachine.Row> rows = new ArrayList<>();
        rows.add(GuiMachine.Row.of("gui.pioneers.api.ai.grant", Component.translatable("ai.pioneers.lab.grant", num(grantG), num(grantQ))));
        gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, Component.translatable("ai.pioneers.lab.status." + status), target > 0 ? GuiMachine.percent(progress)
              : Component.empty(), target > 0 ? progress : -1, color, rows, false);
    }

    /** Noyau d'usine : etat, modele, liaisons (faites / permises), FE/t, interventions, multiblocs supervises ; journal ; « Delier ». */
    private static void core(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, FactoryAiCoreTile tile) {
        gui.energy(g, mouseX, mouseY, 8, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.ENERGY_H);
        int state = (int) gui.value(UiValues.STATE), model = (int) gui.value(FactoryAiCoreTile.MODEL);
        int color = MODEL[Math.max(0, Math.min(3, model))];
        Rect c = FactoryAiCoreTile.CARD;
        Component name = Component.translatable("ai.pioneers.core.state." + state);
        Component headline = model >= AiChips.NEURAL ? Component.translatable("ai.pioneers.name." + AiChips.KEYS[model]) : Component.literal("");
        List<GuiMachine.Row> rows = new ArrayList<>();
        rows.add(GuiMachine.Row.of("ai.pioneers.core.links", Component.translatable("ai.pioneers.core.links_value", gui.value(FactoryAiCoreTile.LINKS),
              gui.value(FactoryAiCoreTile.MAX_LINKS), gui.value(FactoryAiCoreTile.SUPERVISED))));
        rows.add(GuiMachine.Row.of("ai.pioneers.core.interventions", Component.literal(Texts.format(gui.value(FactoryAiCoreTile.INTERVENTIONS)) + " · "
              + Texts.energy(gui.value(UiValues.FE_PER_TICK)) + "/t")));
        gui.info(g, c.x(), c.y(), c.w(), c.h(), state == 2 ? color : state == 1 ? 0xE04848 : 0, name, headline, -1, color, rows, false);
        if (gui.over(mouseX, mouseY, c.x(), c.y(), c.w(), c.h())) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("ai.pioneers.core.rule_1").withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("ai.pioneers.core.rule_2").withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("ai.pioneers.core.rule_3").withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("ai.pioneers.core.rule_4").withStyle(ChatFormatting.DARK_GRAY));
            if (model >= AiChips.NEURAL) {
                tip.add(Component.translatable("ai.pioneers.gui.chip", model(model), Component.translatable("ai.pioneers.effect." + model))
                      .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            gui.tooltip(tip);
        }
        //Journal : derniers messages (ecran raccourci)
        Rect s = StandardLayout.shortScreen(StandardLayout.WIDTH);
        gui.screenAt(g, s.x(), s.y(), s.w(), s.h());
        GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.api.ai.journal"), s.x() + 5, s.y() + 4, 0.6F, PiMenuScreen.DIM, s.w() - 10);
        List<Component> said = tile.messages();
        int lines = Math.min(3, said.size());
        for (int i = 0; i < lines; i++) {
            Component m = said.get(said.size() - lines + i);
            GuiDraw.text(g, gui.font(), m, s.x() + 5, s.y() + 13 + i * 7, 0.55F, i == lines - 1 ? VIOLET & 0xFFFFFFFF : PiMenuScreen.DIM, s.w() - 10);
        }
        if (said.isEmpty()) {
            GuiDraw.text(g, gui.font(), Component.translatable("ai.pioneers.core.silent"), s.x() + 5, s.y() + 13, 0.55F, PiMenuScreen.DIM, s.w() - 10);
        }
        if (gui.over(mouseX, mouseY, s.x(), s.y(), s.w(), s.h()) && !said.isEmpty()) {
            gui.tooltip(said);
        }
        Rect r = StandardLayout.settingRow(StandardLayout.WIDTH);
        gui.steelButton(g, mouseX, mouseY, r.x(), r.y(), r.w(), r.h(), Component.translatable("ai.pioneers.core.unlink"), gui.value(FactoryAiCoreTile.LINKS) > 0,
              () -> gui.send(MachineGui.CUSTOM + FactoryAiCoreTile.CLEAR_LINKS));
        if (gui.over(mouseX, mouseY, r.x(), r.y(), r.w(), r.h())) {
            gui.tooltip(List.of(Component.translatable("ai.pioneers.core.unlink_help")));
        }
    }
}

package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.client.gui.core.GuiMachine.Row;
import fr.pioneers.tech.kinetics.KineticTile;
import fr.pioneers.tech.kinetics.parts.GovernorUnit;
import fr.pioneers.tech.kinetics.parts.InlineBlock;
import fr.pioneers.tech.kinetics.parts.TransmissionUnit;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 1.0.2-beta (Guillaume : « une interface de type slider a la boite de vitesses et au regulateur a boules ») : interface standard v2 des pieces
 * de transmission de l'age mecanique, au registre {@link MachineScreens} (vues {@code transmission} et {@code governor} des fiches). Meme
 * grammaire que les machines de l'age ({@link AgeViews}) : jauge de vitesse a gauche (energie motrice), carte des mesures, ecran d'etat aux
 * valeurs NOMMEES, pastille d'age ; et un CURSEUR ({@link GuiMachine#slider}, valeur envoyee au serveur par {@code PacketMachineSetting}) :
 * rapport de la boite de vitesses (x1/4 a x4), vitesse visee du regulateur ({@link GovernorUnit#MIN_RPM} a {@link GovernorUnit#MAX_RPM}).
 * L'embrayage et l'inverseur ont la meme vue sans curseur (la redstone les commande).
 */
final class TransmissionViews {

    private static final int GREEN = 0x3FD0A0, AMBER = 0xE0B040, GREY = 0x8A9099, BRASS = 0xE0B040;

    static final MachineScreens.View TRANSMISSION = new Transmission();
    static final MachineScreens.View GOVERNOR = new Governor();

    private TransmissionViews() {
    }

    /** Jauge de vitesse 0 .. {@link TransmissionUnit#SCALE} tr/min, trait de la vitesse visee si {@code mark} > 0. */
    static void speed(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, Rect r, double rpm, double mark, Component what) {
        int x = r.x(), y = r.y(), w = r.w(), h = r.h();
        gui.frame(g, x, y, w, h);
        double scale = Math.max(TransmissionUnit.SCALE, mark * 1.25);
        int filled = (int) Math.round((h - 2) * Math.max(0, Math.min(1, Math.abs(rpm) / scale)));
        for (int i = 0; i < filled; i++) {
            double v = (i + 0.5) / (h - 2) * scale;
            int c = mark > 0 && v > mark ? 0xFFE0B040 : 0xFF3FD0A0;
            g.fill(x + 1, y + h - 2 - i, x + w - 1, y + h - 1 - i, c);
        }
        if (mark > 0) {
            int my = y + h - 1 - (int) Math.round((h - 2) * Math.min(1, mark / scale));
            g.fill(x - 1, my, x + w + 1, my + 1, 0xFFFFFFFF);
        }
        GuiMachine.ticks(g, x, y, w, h);
        if (gui.over(mouseX, mouseY, x, y, w, h)) {
            gui.tooltip(List.of(what.copy().append(" : ").append(AgeViews.rpm(rpm))));
        }
    }

    private static net.minecraft.network.chat.MutableComponent t(String key, Object... args) {
        return Component.translatable("gui.pioneers.mech." + key, args);
    }

    // ------------------------------------------------------------------
    // Boite de vitesses, embrayage, inverseur
    // ------------------------------------------------------------------

    private static final class Transmission extends AgeViews.Base {

        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            double in = gui.value(UiValues.SPEED) / 100.0, out = gui.value(UiValues.OUT_SPEED) / 100.0, torque = gui.value(UiValues.TORQUE) / 10.0;
            int kind = (int) Math.max(0, Math.min(2, gui.value(UiValues.TRANSMISSION)));
            boolean powered = gui.value(UiValues.POWERED) != 0, gearshift = kind == InlineBlock.Kind.GEARSHIFT.ordinal();
            int position = (int) Math.max(0, Math.min(4, gui.value(UiValues.RATIO)));
            double tmax = Math.max(1, gui.value(UiValues.RATED_TORQUE));
            speed(gui, g, mouseX, mouseY, StandardLayout.POWER, in, 0, t("input"));
            speed(gui, g, mouseX, mouseY, TransmissionUnit.OUT, out, 0, t("output"));
            //Carte : rapport (ou etat), entree, sortie, couple
            Rect c = TransmissionUnit.CARD;
            List<Row> rows = new ArrayList<>();
            rows.add(new Row(t("input"), AgeViews.rpm(in), PiMenuScreen.SCREEN_TEXT));
            rows.add(new Row(t("output"), AgeViews.rpm(out), Math.abs(out) > 0.5 ? GuiMachine.FLOW_UP_COLOR : PiMenuScreen.SCREEN_TEXT));
            rows.add(Row.of("gui.pioneers.kinetics.torque", GuiMachine.literal(KineticTile.fmt(torque) + " / " + KineticTile.fmt(tmax) + " N·m")));
            Component head = gearshift ? t("ratio", InlineBlock.RATIO_NAMES[InlineBlock.SLIDER[position]]) : t("kind." + kind);
            gui.info(g, c.x(), c.y(), c.w(), c.h(), 0, head, AgeViews.rpm(out), Math.min(1, torque / tmax), BRASS, rows, false);
            //Ecran d'etat : prise directe, embraye / debraye, sens
            String state = switch (kind) {
                case 0 -> powered ? "direct" : "geared";
                case 1 -> powered ? "clutch_off" : "clutch_on";
                default -> powered ? "reversed" : "forward";
            };
            int chip = powered ? AMBER : Math.abs(in) > 0.5 ? GREEN : GREY;
            Rect s = gearshift ? StandardLayout.sliderScreen(StandardLayout.WIDTH) : StandardLayout.screen(StandardLayout.WIDTH);
            gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, t("state." + state), AgeViews.rpm(in), -1, BRASS, List.of(), false);
            if (gui.over(mouseX, mouseY, s.x(), s.y(), s.w(), 16)) {
                gui.tooltip(List.of(t("state." + state), t("state_help." + kind).withStyle(ChatFormatting.GRAY)));
            }
            if (gearshift) {
                List<Component> labels = new ArrayList<>();
                int[] marks = new int[InlineBlock.SLIDER.length];
                for (int i = 0; i < marks.length; i++) {
                    marks[i] = i;
                    labels.add(GuiMachine.literal(InlineBlock.RATIO_NAMES[InlineBlock.SLIDER[i]]));
                }
                gui.slider(g, mouseX, mouseY, StandardLayout.sliderRow(StandardLayout.WIDTH), TransmissionUnit.SETTING_RATIO, 0, marks.length - 1, position,
                      marks, labels, BRASS, List.of(t("slider_ratio"), t("slider_ratio_help").withStyle(ChatFormatting.GRAY)));
            }
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            return true;
        }

        @Override
        void info(GuiMachine gui, BlockEntity be, List<StandardView.InfoRow> rows) {
            rows.add(new StandardView.InfoRow("torque", GuiMachine.literal(gui.value(UiValues.RATED_TORQUE) + " N·m"), 0xFFE0B040));
            rows.add(new StandardView.InfoRow("max_speed", GuiMachine.literal(gui.value(UiValues.MAX_SPEED) + " tr/min"), PiMenuScreen.TEXT));
        }
    }

    // ------------------------------------------------------------------
    // Regulateur a boules
    // ------------------------------------------------------------------

    private static final class Governor extends AgeViews.Base {

        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            double speed = gui.value(UiValues.SPEED) / 100.0, torque = gui.value(UiValues.TORQUE) / 10.0;
            int target = (int) gui.value(UiValues.TARGET_RPM);
            boolean limiting = gui.value(UiValues.LIMITING) != 0;
            Rect row = StandardLayout.sliderRow(StandardLayout.WIDTH);
            int[] marks = {GovernorUnit.MIN_RPM, 30, 60, 90, GovernorUnit.MAX_RPM};
            List<Component> labels = new ArrayList<>();
            for (int m : marks) {
                labels.add(GuiMachine.literal(String.valueOf(m)));
            }
            int shown = gui.slider(g, mouseX, mouseY, row, GovernorUnit.SETTING_TARGET, GovernorUnit.MIN_RPM, GovernorUnit.MAX_RPM, target, marks,
                  labels, BRASS, List.of(t("slider_target"), t("slider_target_help").withStyle(ChatFormatting.GRAY)));
            speed(gui, g, mouseX, mouseY, StandardLayout.POWER, speed, shown, Component.translatable("gui.pioneers.kinetics.speed"));
            Rect c = GovernorUnit.CARD;
            List<Row> rows = new ArrayList<>();
            rows.add(new Row(Component.translatable("gui.pioneers.kinetics.speed"), AgeViews.rpm(speed), limiting ? 0xFFE0B040 : PiMenuScreen.SCREEN_TEXT));
            rows.add(Row.of("gui.pioneers.kinetics.torque", GuiMachine.literal(KineticTile.fmt(torque) + " N·m")));
            rows.add(new Row(t("signal"), GuiMachine.literal(limiting ? "15" : "0"), limiting ? 0xFFFF5555 : PiMenuScreen.DIM));
            gui.info(g, c.x(), c.y(), c.w(), c.h(), 0, t("target"), GuiMachine.literal(shown + " tr/min"), Math.min(1, Math.abs(speed) / Math.max(1, shown)),
                  BRASS, rows, false);
            Rect s = StandardLayout.sliderScreen(StandardLayout.WIDTH);
            gui.info(g, s.x(), s.y(), s.w(), s.h(), limiting ? AMBER : Math.abs(speed) > 0.5 ? GREEN : GREY,
                  t(limiting ? "governor.limiting" : "governor.free"), AgeViews.rpm(speed), -1, BRASS, List.of(), false);
            if (gui.over(mouseX, mouseY, s.x(), s.y(), s.w(), 16)) {
                gui.tooltip(List.of(t(limiting ? "governor.limiting" : "governor.free"), t("governor.help").withStyle(ChatFormatting.GRAY)));
            }
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            return true;
        }

        @Override
        void info(GuiMachine gui, BlockEntity be, List<StandardView.InfoRow> rows) {
            rows.add(new StandardView.InfoRow("torque", GuiMachine.literal(gui.value(UiValues.RATED_TORQUE) + " N·m"), 0xFFE0B040));
            rows.add(new StandardView.InfoRow("max_speed", GuiMachine.literal(gui.value(UiValues.MAX_SPEED) + " tr/min"), PiMenuScreen.TEXT));
        }
    }
}

package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.client.gui.core.GuiMachine.Row;
import fr.pioneers.core.Texts;
import fr.pioneers.energy.EnergyKind;
import fr.pioneers.energy.HydroTurbineTile;
import fr.pioneers.energy.MicrobialCellTile;
import fr.pioneers.api.machine.MachineSpec;
import fr.pioneers.api.machine.UiValues;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 0.20.45 : vues des generateurs renouvelables dans l'interface standard : reserve d'energie a gauche (puis l'eau de la centrale
 * geothermique, ou la case de la pile microbienne), ecran d'etat : production, part de la production maximale, mesures du lieu.
 * Valeurs NOMMEES (lot 3 : {@link UiValues#GEN_KIND}, {@link UiValues#OUTPUT}, {@link UiValues#PEAK}, mesures {@link UiValues#detail}) ;
 * vue choisie par la fiche ({@code generator}, {@link MachineScreens}). Lot 4 : interface standard v2 ({@link #VIEW}).
 */
final class EnergyViews {

    private static final int W = StandardLayout.GAUGE_W, H = StandardLayout.GAUGE_H, TOP = StandardLayout.TOP;
    private final GuiMachine gui;
    private static final String[] MK = {"MKI", "MKII", "MKIII", "MKIV"};

    EnergyViews(GuiMachine gui) {
        this.gui = gui;
    }

    /**
     * Lot 4 : vue {@code generator} de la fiche a l'interface standard v2 (renouvelables ; 1.0.3-beta lot 5 : turbine et echappement compacts retires) : reserve
     * d'energie a la place commune, jauges hautes et carte des mesures du lieu dans la colonne, ecran d'etat pleine largeur (production,
     * part de la production maximale, etat et pic), onglet Infos.
     */
    static final MachineScreens.View VIEW = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
            new EnergyViews(gui).draw(g, mouseX, mouseY, tile);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
            MachineSpec spec = MachineScreens.spec(tile);
            if (spec != null && spec.key().equals("microbial_fuel_cell")) {
                gui.slotTip(mouseX, mouseY, MicrobialCellTile.SLOT_X, MicrobialCellTile.SLOT_Y, "energy.pioneers.gui.fuel_slot");
            }
            return true;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity tile) {
            List<StandardView.InfoRow> rows = new ArrayList<>();
            long peak = gui.value(UiValues.PEAK);
            rows.add(new StandardView.InfoRow("peak", GuiMachine.literal(GuiMachine.compact(peak) + " FE/t"), 0xFFFFFF55));
            rows.add(new StandardView.InfoRow("reserve", GuiMachine.literal(Texts.energy(gui.value(UiValues.CAPACITY))), PiMenuScreen.TEXT));
            return rows;
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_generator";
        }
    };

    /** 1.0.4-beta (lot 5) : couleur de la ligne « Surplus perdu » (ambre, comme les avertissements des ecrans). */
    static final int SURPLUS_COLOR = 0xE0A030;

    /** Mesure propre n (0..3) d'un generateur renouvelable (valeur nommee). */
    private long d(int n) {
        return gui.value(UiValues.detail(n));
    }

    private static Component pct(long thousandths) {
        return GuiMachine.literal(Math.round(thousandths / 10.0) + " %");
    }

    private static Component mult(long thousandths) {
        return GuiMachine.literal(String.format(Locale.ROOT, "× %.2f", thousandths / 1000.0));
    }

    private void energy(GuiGraphics g, int mouseX, int mouseY) {
        gui.energy(g, mouseX, mouseY, 8, TOP, W, StandardLayout.ENERGY_H);
    }

    private void screen(GuiGraphics g, int chip, Component name, Component headline, double ratio, List<Row> rows) {
        Rect s = StandardLayout.screen(StandardLayout.WIDTH);
        gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, name, headline, ratio, chip, rows, false);
    }

    boolean draw(GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
        //Lot 3 : la vue est choisie par la fiche (MachineScreens, vue « generator ») ; la machine par la cle de sa fiche, le genre par sa valeur
        EnergyKind kind = EnergyKind.values()[(int) Math.max(0, Math.min(EnergyKind.values().length - 1, gui.value(UiValues.GEN_KIND)))];
        energy(g, mouseX, mouseY);
        if (kind == EnergyKind.GEOTHERMAL) {
            long water = d(1), capacity = Math.max(1, d(2));
            Rect q = StandardLayout.generatorGauge(true, 0);
            gui.manometer(g, mouseX, mouseY, q.x(), q.y(), q.w(), q.h(), water / (double) capacity, -1,
                  List.of(Component.translatable("energy.pioneers.gui.water", GuiMachine.compact(water), GuiMachine.compact(capacity))));
        }
        double power = gui.value(UiValues.OUTPUT) / 100.0;
        long peak = gui.value(UiValues.PEAK);
        Component headline = GuiMachine.literal("+" + GuiMachine.compact(power) + " FE/t");
        List<Row> rows = new ArrayList<>();
        int chip;
        switch (kind) {
            case SOLAR -> {
                rows.add(Row.of("energy.pioneers.gui.sun", pct(d(0))));
                rows.add(Row.of("energy.pioneers.gui.weather", pct(d(1))));
                rows.add(Row.of("energy.pioneers.gui.light", mult(d(2))));
                rows.add(Row.of("energy.pioneers.gui.sky", Component.translatable(d(3) > 0 ? "energy.pioneers.gui.yes" : "energy.pioneers.gui.no")));
                chip = 0xF2C43A;
            }
            case WIND -> {
                //1.9.2 (retours de Guillaume) : le rotor est un bloc a part ; sans rotor, l'ecran dit ou le poser
                int rotor = (int) d(3) - 1;
                if (rotor < 0) {
                    rows.add(new Row(Component.translatable("energy.pioneers.gui.rotor"), Component.translatable("energy.pioneers.gui.rotor_none"),
                          0xFF8A6A));
                    rows.add(new Row(Component.translatable("energy.pioneers.gui.rotor_hint"), Component.empty(), 0xFF8A6A));
                } else {
                    rows.add(Row.of("energy.pioneers.gui.rotor", GuiMachine.literal(MK[Math.min(3, rotor)])));
                    rows.add(Row.of("energy.pioneers.gui.altitude", pct(d(0))));
                    rows.add(Row.of("energy.pioneers.gui.clear", Component.translatable("energy.pioneers.gui.clear_value", pct(d(1)),
                          EnergyKind.WIND_RADIUS[Math.min(3, rotor)])));
                    rows.add(Row.of("energy.pioneers.gui.wind", mult(d(2))));
                }
                chip = rotor < 0 ? 0xC85A3A : 0xD8E4EC;
            }
            case HYDRO -> {
                rows.add(Row.of("energy.pioneers.gui.head", Component.translatable("energy.pioneers.gui.blocks", d(0), EnergyKind.HYDRO_HEAD)));
                rows.add(Row.of("energy.pioneers.gui.volume", GuiMachine.literal(GuiMachine.compact(d(1)))));
                rows.add(Row.of("energy.pioneers.gui.turbines", GuiMachine.literal(String.valueOf(d(2)))));
                chip = (int) d(3) == HydroTurbineTile.OK ? 0x3E8FD8 : 0xC85A3A;
            }
            case GEOTHERMAL -> {
                rows.add(Row.of("energy.pioneers.gui.depth", pct(d(0))));
                rows.add(Row.of("energy.pioneers.gui.lava", GuiMachine.literal(String.valueOf(d(3)))));
                rows.add(Row.of("energy.pioneers.gui.water_row", GuiMachine.literal(GuiMachine.compact(d(1)) + " mB")));
                chip = 0xE0672A;
            }
            default -> {
                rows.add(Row.of("energy.pioneers.gui.fuel", GuiMachine.literal(GuiMachine.compact(d(0)) + " FE")));
                rows.add(Row.of("energy.pioneers.gui.eaten", GuiMachine.literal(String.valueOf(d(1)))));
                rows.add(Row.of("energy.pioneers.gui.item_value", GuiMachine.literal(GuiMachine.compact(d(2)) + " FE")));
                chip = 0x7DB04A;
            }
        }
        //Carte des mesures du lieu (colonne) ; ecran : production, part du pic, pic (et etat de la centrale hydraulique)
        SpaceViews.card(gui, g, StandardLayout.generatorCard(true, kind == EnergyKind.GEOTHERMAL ? 1 : 0, kind == EnergyKind.MICROBIAL),
              Component.translatable("energy.pioneers.gui.site"), rows);
        List<Row> screen = new ArrayList<>();
        if (kind == EnergyKind.HYDRO) {
            screen.add(Row.of("energy.pioneers.gui.state", Component.translatable("energy.pioneers.gui.hydro." + d(3))));
        }
        screen.add(Row.of("energy.pioneers.gui.peak", GuiMachine.literal(GuiMachine.compact(peak) + " FE/t")));
        if (gui.value(UiValues.SURPLUS) > 0) {
            //1.0.4-beta (lot 5) : reserve pleine, la production continue : ce qui ne rentre pas est perdu
            screen.add(new Row(Component.translatable("gui.pioneers.generator.surplus"), Component.translatable("gui.pioneers.generator.surplus_value"),
                  SURPLUS_COLOR));
        }
        screen(g, chip, StandardView.baseName(tile.getBlockState().getBlock().getName()), headline, peak <= 0 ? 0 : power / peak, screen);
        return true;
    }
}

package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.farm.greenhouse.GreenhouseRegulatorTile;
import fr.pioneers.farm.greenhouse.GreenhouseScan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 1.0.5-beta (lot 3) : CORPS du REGULATEUR DE SERRE dans la vue maison commune ({@code house} : voile, bandeau rouge de l'erreur commune,
 * onglets Infos et Structure). Deux ecrans du gabarit commun ({@link GuiMachine#info}) : en haut la SERRE (formee : volume, dimensions de
 * l'interieur, cultures ; non formee : le probleme, la case exacte x y z, le bloc fautif) ; en bas le CLIMAT INTERIEUR (ideal pour toutes
 * les cultures, en toute saison, rien n'y tombe ; non formee : climat du dehors, rappel du plan des fautes au Maj + clic droit). Valeurs
 * NOMMEES de la tile ({@code gh_*}) ; seul le nom du bloc fautif est lu sur la tile.
 */
final class GreenhouseViews {

    static final int GREEN = 0x7CC860, RED = 0xE04848, AMBER = 0xF0B040;
    /** Ecran de la serre (haut) et du climat (bas, ecran commun). */
    static final StandardLayout.Rect CARD = new StandardLayout.Rect(8, StandardLayout.TOP, StandardLayout.WIDTH - 16, 62);
    /** Hauteur de l'ecran du climat quand la serre n'est pas formee (au-dessus du bandeau de l'erreur commune, a ZONE - 34). */
    static final int UNFORMED_H = 27;

    private GreenhouseViews() {
    }

    static final MachineScreens.View BODY = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            boolean formed = gui.value(GreenhouseRegulatorTile.FORMED) != 0;
            GreenhouseScan.Status[] all = GreenhouseScan.Status.values();
            GreenhouseScan.Status status = all[(int) Math.max(0, Math.min(all.length - 1, gui.value(GreenhouseRegulatorTile.STATUS)))];
            List<GuiMachine.Row> rows = new ArrayList<>();
            if (formed) {
                rows.add(GuiMachine.Row.of("gui.pioneers.greenhouse.volume", Component.translatable("gui.pioneers.greenhouse.volume_value",
                      gui.value(GreenhouseRegulatorTile.VOLUME))));
                rows.add(GuiMachine.Row.of("gui.pioneers.greenhouse.size", Component.translatable("gui.pioneers.greenhouse.size_value",
                      gui.value(GreenhouseRegulatorTile.SIZE_X), gui.value(GreenhouseRegulatorTile.SIZE_Z), gui.value(GreenhouseRegulatorTile.SIZE_Y))));
                rows.add(GuiMachine.Row.of("gui.pioneers.greenhouse.crops", GuiMachine.literal(String.valueOf(gui.value(GreenhouseRegulatorTile.CROPS)))));
            } else {
                rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.greenhouse.problem"), Component.translatable(status.key() + ".short"), 0xFF000000 | RED));
                if (gui.value(GreenhouseRegulatorTile.HAS_AT) != 0) {
                    rows.add(new GuiMachine.Row(Component.translatable(gui.value(GreenhouseRegulatorTile.EXACT) != 0 || status != GreenhouseScan.Status.LEAK
                          ? "gui.pioneers.greenhouse.where" : "gui.pioneers.greenhouse.near"), GuiMachine.literal(gui.value(GreenhouseRegulatorTile.AT_X) + " "
                          + gui.value(GreenhouseRegulatorTile.AT_Y) + " " + gui.value(GreenhouseRegulatorTile.AT_Z)), 0xFF000000 | AMBER));
                    GreenhouseRegulatorTile tile = StorageViews.as(GreenhouseRegulatorTile.class, be);
                    String block = tile == null ? null : tile.faultBlockKey();
                    if (block != null && status != GreenhouseScan.Status.SHARED) {
                        rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.greenhouse.block"), Component.translatable(block), 0xFF000000 | AMBER));
                    }
                }
            }
            gui.info(g, CARD.x(), CARD.y(), CARD.w(), CARD.h(), formed ? GREEN : RED, Component.translatable("gui.pioneers.greenhouse.title"),
                  Component.translatable(formed ? "gui.pioneers.greenhouse.formed" : "gui.pioneers.greenhouse.unformed"), -1, 0, rows, false);
            StandardLayout.Rect s = StandardLayout.screen(StandardLayout.WIDTH);
            List<GuiMachine.Row> climate = new ArrayList<>();
            if (formed) {
                climate.add(GuiMachine.Row.of("gui.pioneers.greenhouse.all_crops", Component.translatable("gui.pioneers.greenhouse.all_seasons")));
                climate.add(GuiMachine.Row.of("gui.pioneers.greenhouse.weather", Component.translatable("gui.pioneers.greenhouse.weather_none")));
            } else {
                climate.add(GuiMachine.Row.of("gui.pioneers.greenhouse.plan", Component.translatable("gui.pioneers.greenhouse.plan_keys")));
            }
            //non formee : l'ecran s'arrete au-dessus du bandeau rouge de l'erreur commune (MultiblockViews.houseBand)
            gui.info(g, s.x(), s.y(), s.w(), formed ? s.h() : UNFORMED_H, formed ? GREEN : AMBER, Component.translatable("gui.pioneers.greenhouse.climate"),
                  Component.translatable(formed ? "gui.pioneers.greenhouse.ideal" : "gui.pioneers.greenhouse.outside"), -1, 0, climate, false);
        }
    };
}

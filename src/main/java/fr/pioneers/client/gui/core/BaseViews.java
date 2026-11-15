package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.common.PiLang;
import fr.pioneers.core.Texts;
import fr.pioneers.data.DataUnits;
import fr.pioneers.network.PacketRoverName;
import fr.pioneers.network.PacketSetStationName;
import fr.pioneers.network.PiNetwork;
import fr.pioneers.space.LaunchPadStructure.Size;
import fr.pioneers.space.Propellant;
import fr.pioneers.space.RocketTier;
import fr.pioneers.space.giant.PocketKind;
import fr.pioneers.space.mining.DrillHead;
import fr.pioneers.space.mining.MiningVein;
import fr.pioneers.space.module.RocketModule;
import fr.pioneers.space.research.ProbeDestination;
import fr.pioneers.tile.TileEntityDroneBay;
import fr.pioneers.tile.TileEntityGreenhouse;
import fr.pioneers.tile.TileEntityLaunchPad.Mission;
import fr.pioneers.tile.TileEntityLaunchPad.Rule;
import fr.pioneers.tile.TileEntityLaunchPad.Status;
import fr.pioneers.tile.TileEntityLaunchPad;
import fr.pioneers.tile.TileEntityMiningOutpost;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Vues de l'interface standard (sombre, couleur de la machine) des controleurs des bases planetaires passes au socle (lot D) : serre.
 * Positions reprises des anciens ecrans ; textes bornes a leur cadre.
 */
final class BaseViews {

    private static final int TEXT = PiMenuScreen.TEXT, DIM = PiMenuScreen.DIM;
    private static final int GREEN = 0xFF55FF88, RED = 0xFFFF6A5A, GOLD = 0xFFFFAA00, AQUA = 0xFF55DDFF;
    private static final String[] ROMAN = {"I", "II", "III", "IV"};

    private final GuiMachine gui;
    private final MachineViews machines;

    BaseViews(GuiMachine gui, MachineViews machines) {
        this.gui = gui;
        this.machines = machines;
    }

    /**
     * Lot 8b : CORPS des bases dans la vue maison {@code house} (choisi par la cle de la fiche : {@link MachineScreens#registerBody}) ;
     * valeurs NOMMEES des tiles, plus d'{@code instanceof}. La serre est en vraie v2 ; avant-poste, baie de drones et complexe de lancement
     * gardent leur corps special (carte de prospection, radar, quai), dans le cadre v2.
     */
    static final MachineScreens.View BODY = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            BaseViews v = gui.views().bases();
            TileEntityMiningOutpost outpost = StorageViews.as(TileEntityMiningOutpost.class, be);
            TileEntityDroneBay bay = StorageViews.as(TileEntityDroneBay.class, be);
            TileEntityLaunchPad pad = StorageViews.as(TileEntityLaunchPad.class, be);
            if (outpost != null) {
                v.outpost(g, mouseX, mouseY, outpost);
            } else if (bay != null) {
                v.droneBay(g, mouseX, mouseY, bay);
            } else if (pad != null) {
                v.launchPad(g, mouseX, mouseY, pad);
            } else if (StorageViews.as(TileEntityGreenhouse.class, be) != null) {
                v.greenhouse(g, mouseX, mouseY);
            }
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            if (StorageViews.as(TileEntityGreenhouse.class, be) != null) {
                gui.slotTip(mouseX, mouseY, StandardLayout.BATTERY_X, StandardLayout.BATTERY_Y, "gui.pioneers.machine.battery");
            } else if (StorageViews.as(TileEntityMiningOutpost.class, be) != null) {
                gui.slotTip(mouseX, mouseY, StandardLayout.BATTERY_X, StandardLayout.BATTERY_Y, "gui.pioneers.machine.battery");
            } else if (StorageViews.as(TileEntityLaunchPad.class, be) != null) {
                gui.slotTip(mouseX, mouseY, TileEntityLaunchPad.ROCKET_X, TileEntityLaunchPad.ROCKET_Y, "launch_pad.pioneers.slot.rocket");
                gui.slotTip(mouseX, mouseY, TileEntityLaunchPad.UPGRADE_X, TileEntityLaunchPad.UPGRADE_Y, "launch_pad.pioneers.slot.upgrade");
                gui.slotTip(mouseX, mouseY, TileEntityLaunchPad.BUCKET_X, TileEntityLaunchPad.BUCKET_IN_Y, "launch_pad.pioneers.slot.fuel_in");
                gui.slotTip(mouseX, mouseY, TileEntityLaunchPad.BUCKET_X, TileEntityLaunchPad.BUCKET_OUT_Y, "launch_pad.pioneers.slot.fuel_out");
            } else if (StorageViews.as(TileEntityDroneBay.class, be) != null) {
                gui.slotTip(mouseX, mouseY, StandardLayout.BATTERY_X, StandardLayout.BATTERY_Y, "gui.pioneers.machine.battery");
                gui.slotTip(mouseX, mouseY, 8, TileEntityDroneBay.SLOT_Y, "drone_bay.pioneers.slot.drones");
                gui.slotTip(mouseX, mouseY, 26, TileEntityDroneBay.SLOT_Y, "drone_bay.pioneers.slot.plating");
                gui.slotTip(mouseX, mouseY, 62, TileEntityDroneBay.SLOT_Y, "drone_bay.pioneers.slot.crystals");
                gui.slotTip(mouseX, mouseY, 80, TileEntityDroneBay.SLOT_Y, "drone_bay.pioneers.slot.diamonds");
            }
            return true;
        }
    };

    // ------------------------------------------------------------------
    // Champs de saisie (nom du rover)
    // ------------------------------------------------------------------

    @Nullable
    private EditBox nameField;

    void init(@Nullable BlockEntity tile) {
        nameField = null;
        TileEntityLaunchPad pad = StorageViews.as(TileEntityLaunchPad.class, tile);
        TileEntityMiningOutpost outpost = StorageViews.as(TileEntityMiningOutpost.class, tile);
        if (pad != null) {
            Rect n = TileEntityLaunchPad.NAME;
            nameField = gui.widget(new EditBox(gui.font(), gui.left() + n.x() + 3, gui.top() + n.y() + 2, n.w() - 6, 10, Component.translatable("launch_pad.pioneers.name")));
            nameField.setMaxLength(TileEntityLaunchPad.STATION_NAME_LENGTH);
            nameField.setValue(pad.getStationName());
            nameField.setBordered(false);
            nameField.setTextColor(0xFFE0F5EA);
        }
        if (outpost != null) {
            nameField = gui.widget(new EditBox(gui.font(), gui.left() + OUT_SCREEN_X + 3, gui.top() + OUT_MAP_Y + 1, OUT_SCREEN_W - 6, 10,
                  Component.translatable("mining.pioneers.name")));
            nameField.setMaxLength(TileEntityMiningOutpost.ROVER_NAME_LENGTH);
            nameField.setValue(outpost.getClientRoverName());
            nameField.setBordered(false);
            nameField.setTextColor(0xFFE0F5EA);
        }
    }

    void removed(@Nullable BlockEntity tile) {
        TileEntityLaunchPad pad = StorageViews.as(TileEntityLaunchPad.class, tile);
        TileEntityMiningOutpost outpost = StorageViews.as(TileEntityMiningOutpost.class, tile);
        if (nameField != null && pad != null) {
            String text = nameField.getValue().trim();
            if (!text.isEmpty() && !text.equals(pad.getStationName())) {
                PiNetwork.sendToServer(new PacketSetStationName(pad.getBlockPos(), text));
            }
        }
        if (nameField != null && outpost != null) {
            String text = nameField.getValue().trim();
            if (!text.equals(outpost.getClientRoverName())) {
                PiNetwork.sendToServer(new PacketRoverName(outpost.getBlockPos(), text));
            }
        }
    }

    // ------------------------------------------------------------------
    // Avant-poste (212 de large, lot 8c : vraie v2, geometrie de TileEntityMiningOutpost) : valeurs nommees V_* ; gisements et rover lus sur
    // le bloc (client)
    // ------------------------------------------------------------------

    private static final int OUT_MAP_X = TileEntityMiningOutpost.MAP.x(), OUT_MAP_Y = TileEntityMiningOutpost.MAP.y(), OUT_MAP = TileEntityMiningOutpost.MAP.w(),
          OUT_SCREEN_X = TileEntityMiningOutpost.SCREEN.x(), OUT_SCREEN_W = TileEntityMiningOutpost.SCREEN.w(), OUT_ROW_Y = TileEntityMiningOutpost.RESCAN.y();

    private static MutableComponent mining(String key, Object... args) {
        return Component.translatable("mining.pioneers." + key, args);
    }

    void outpost(GuiGraphics graphics, int mouseX, int mouseY, TileEntityMiningOutpost tile) {
        ProspectMap.draw(gui, graphics, mouseX, mouseY, tile, OUT_MAP_X, OUT_MAP_Y, OUT_MAP);
        //Nom du rover (champ de saisie par-dessus) et etat
        gui.screenAt(graphics, OUT_SCREEN_X, OUT_MAP_Y, OUT_SCREEN_W, 12);
        int sy = OUT_MAP_Y + 14, sh = TileEntityMiningOutpost.SCREEN.h() - 14;
        gui.screenAt(graphics, OUT_SCREEN_X, sy, OUT_SCREEN_W, sh);
        List<Component> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        TileEntityMiningOutpost.Status status = TileEntityMiningOutpost.Status.values()[(int) Math.max(0, Math.min(gui.value(TileEntityMiningOutpost.V_STATUS), TileEntityMiningOutpost.Status.values().length - 1))];
        TileEntityMiningOutpost.Mode mode = TileEntityMiningOutpost.Mode.values()[(int) Math.max(0, Math.min(gui.value(TileEntityMiningOutpost.V_MODE), TileEntityMiningOutpost.Mode.values().length - 1))];
        if (status == TileEntityMiningOutpost.Status.WORKING) {
            lines.add(mining("mode." + mode.name().toLowerCase(Locale.ROOT)));
            colors.add(GREEN);
        } else {
            lines.add(mining("status." + status.name().toLowerCase(Locale.ROOT)));
            colors.add(status == TileEntityMiningOutpost.Status.OUTPUT_FULL || status == TileEntityMiningOutpost.Status.LOW_BATTERY ? GOLD : RED);
        }
        if (gui.value(TileEntityMiningOutpost.V_FORMED) != 0) {
            lines.add(mining("garage", "MK" + ROMAN[Math.max(0, Math.min(3, tile.getTier()))], tile.crew()));
            colors.add(GREEN);
        }
        if (gui.value(TileEntityMiningOutpost.V_FORMED) != 0 && tile.hasRover()) {
            lines.add(mining("battery", GuiMachine.percent(gui.value(TileEntityMiningOutpost.V_BATTERY) / (double) Math.max(1, tile.getBatteryMax())).getString()));
            colors.add(AQUA);
            lines.add(mining("cargo", gui.value(TileEntityMiningOutpost.V_CARGO), tile.getCargoMax()));
            colors.add(DIM);
            DrillHead head = tile.getDrillHead();
            lines.add(head == null ? mining("drill.none")
                                   : mining("drill.info", head.tier, Math.round(tile.getDrillRate(head) * 100 / TileEntityMiningOutpost.DRILL_TICKS * 20) / 100F));
            colors.add(head == null ? RED : DIM);
            int target = (int) gui.value(TileEntityMiningOutpost.V_TARGET_INDEX);
            var deposits = tile.getClientDeposits();
            if (target >= 0 && target < deposits.size() && mode != TileEntityMiningOutpost.Mode.IDLE) {
                var deposit = deposits.get(target);
                lines.add(mining("target", Component.translatable(deposit.vein.getTranslationKey())));
                colors.add(0xFF000000 | deposit.vein.color);
                lines.add(mining("target_left", deposit.remaining, deposit.total));
                colors.add(DIM);
            } else {
                lines.add(mining("deposits", deposits.stream().filter(d -> !d.isDepleted()).count()));
                colors.add(DIM);
            }
            lines.add(mining("stats", gui.value(TileEntityMiningOutpost.V_MINED), gui.value(TileEntityMiningOutpost.V_TRIPS)));
            colors.add(DIM);
        }
        float ly = sy + 4;
        for (int i = 0; i < lines.size() && ly < sy + sh - 6; i++) {
            int room = Math.max(1, (int) ((sy + sh - 4 - ly) / 6));
            ly += GuiDraw.wrapped(graphics, gui.font(), lines.get(i), OUT_SCREEN_X + 4, ly, 0.5F, colors.get(i), OUT_SCREEN_W - 8, Math.min(4, room), 6) * 6 + 1.5F;
        }
        Rect en = TileEntityMiningOutpost.ENERGY;
        gui.energy(graphics, mouseX, mouseY, en.x(), en.y(), en.w(), en.h());
        //Filtre de minerai
        gui.steelButton(graphics, mouseX, mouseY, OUT_MAP_X, OUT_ROW_Y, 12, 12, Component.literal("<"), true, () -> gui.send(TileEntityMiningOutpost.ACTION_FILTER_PREV));
        gui.steelButton(graphics, mouseX, mouseY, OUT_MAP_X + OUT_MAP - 12, OUT_ROW_Y, 12, 12, Component.literal(">"), true,
              () -> gui.send(TileEntityMiningOutpost.ACTION_FILTER_NEXT));
        MiningVein filter = tile.getFilterVein();
        Component label = filter == null ? mining("filter.all") : Component.translatable(filter.getTranslationKey());
        GuiDraw.center(graphics, gui.font(), label, OUT_MAP_X + OUT_MAP / 2F, OUT_ROW_Y + 3.5F, 0.55F, filter == null ? DIM : 0xFF000000 | filter.color, OUT_MAP - 28);
        if (gui.over(mouseX, mouseY, OUT_MAP_X, OUT_ROW_Y, OUT_MAP, 12)) {
            gui.tooltip(List.of(mining("filter.tooltip")));
        }
        //Portee
        gui.steelButton(graphics, mouseX, mouseY, OUT_SCREEN_X, OUT_ROW_Y, 12, 12, Component.literal("-"), true, () -> gui.send(TileEntityMiningOutpost.ACTION_RANGE_DOWN));
        gui.steelButton(graphics, mouseX, mouseY, OUT_SCREEN_X + OUT_SCREEN_W - 12, OUT_ROW_Y, 12, 12, Component.literal("+"), true,
              () -> gui.send(TileEntityMiningOutpost.ACTION_RANGE_UP));
        Component range = Component.literal(tile.getSurveyRadius() + " m");
        GuiDraw.center(graphics, gui.font(), range, OUT_SCREEN_X + OUT_SCREEN_W / 2F, OUT_ROW_Y + 3.5F, 0.55F, tile.isRangeMax() ? GOLD : PiMenuScreen.SCREEN_TEXT, OUT_SCREEN_W - 28);
        if (gui.over(mouseX, mouseY, OUT_SCREEN_X, OUT_ROW_Y, OUT_SCREEN_W, 12)) {
            gui.tooltip(List.of(mining(tile.isRangeMax() ? "range.max" : "range", tile.getSurveyRadius()), mining("range.tooltip", tile.getMaxRange())));
        }
        Rect rs = TileEntityMiningOutpost.RESCAN;
        gui.steelButton(graphics, mouseX, mouseY, rs.x(), rs.y(), rs.w(), rs.h(), mining("rescan"), true, () -> gui.send(TileEntityMiningOutpost.ACTION_RESCAN));
        if (gui.over(mouseX, mouseY, rs.x(), rs.y(), rs.w(), rs.h())) {
            gui.tooltip(List.of(mining("rescan.tooltip")));
        }
        GuiDraw.text(graphics, gui.font(), mining("slot.modules"), TileEntityMiningOutpost.MODULES_X - 1, TileEntityMiningOutpost.ROVER_Y + 59, 0.45F, DIM, 20);
    }

    // ------------------------------------------------------------------
    // Serre : 4 forme, 5 astre favorable, 6 tortue, 7 batterie, 8 mode, 9 plantes, 10 mures, 11 recoltes
    // ------------------------------------------------------------------

    private void greenhouse(GuiGraphics graphics, int mouseX, int mouseY) {
        gui.energy(graphics, mouseX, mouseY, 8, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.ENERGY_H);
        Rect c = TileEntityGreenhouse.CARD;
        int sx = c.x(), sy = c.y(), sw = c.w(), sh = c.h();
        gui.screenAt(graphics, sx, sy, sw, sh);
        List<Component> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        boolean formed = gui.value(TileEntityGreenhouse.FORMED) != 0;
        lines.add(formed ? Component.translatable("gui.pioneers.base.formed", PiLang.GREENHOUSE_FORMED.translate(), "MK" + ROMAN[(int) Math.max(0,
              Math.min(3, gui.value(TileEntityGreenhouse.TIER)))]) : PiLang.GREENHOUSE_NOT_FORMED.translate());
        colors.add(formed ? GREEN : RED);
        if (gui.value(TileEntityGreenhouse.ROBOT) == 0) {
            lines.add(PiLang.GREENHOUSE_NO_ROBOT.translate());
            colors.add(GOLD);
        } else {
            TileEntityGreenhouse.Mode mode = TileEntityGreenhouse.Mode.values()[(int) Math.max(0, Math.min(gui.value(TileEntityGreenhouse.MODE), 3))];
            lines.add(PiLang.GREENHOUSE_BATTERY.translate(GuiMachine.percent(gui.value(TileEntityGreenhouse.BATTERY) / (double) TileEntityGreenhouse.BATTERY_MAX)
                  .getString(), Component.translatable("greenhouse.pioneers.mode." + mode.name().toLowerCase(Locale.ROOT))));
            colors.add(AQUA);
        }
        lines.add(PiLang.GREENHOUSE_PLANTS.translate(gui.value(TileEntityGreenhouse.PLANTED), gui.value(TileEntityGreenhouse.BEDS),
              gui.value(TileEntityGreenhouse.MATURE)));
        colors.add(DIM);
        if (gui.value(TileEntityGreenhouse.PLANTED) > 0 && gui.value(TileEntityGreenhouse.PLANET_OK) == 0) {
            lines.add(PiLang.GREENHOUSE_WRONG_PLANET.translate());
            colors.add(RED);
        }
        lines.add(PiLang.GREENHOUSE_HARVESTS.translate(gui.value(TileEntityGreenhouse.HARVESTS)));
        colors.add(DIM);
        for (int i = 0; i < lines.size() && i < 5; i++) {
            GuiDraw.text(graphics, gui.font(), lines.get(i), sx + 5, sy + 4 + i * 7, 0.6F, colors.get(i), sw - 10);
        }
        int lx = TileEntityGreenhouse.ROWS_X + 6 * 18 + 4, lw = StandardLayout.WIDTH - 8 - lx;
        GuiDraw.text(graphics, gui.font(), PiLang.GREENHOUSE_SEEDS.translate(), lx, TileEntityGreenhouse.SEEDS_Y + 5, 0.6F, DIM, lw);
        GuiDraw.text(graphics, gui.font(), PiLang.GREENHOUSE_OUTPUT.translate(), lx, TileEntityGreenhouse.OUT_Y + 14, 0.6F, DIM, lw);
    }

    // ------------------------------------------------------------------
    // Baie de drones (212 de large, lot 8c : vraie v2, geometrie de TileEntityDroneBay) : valeurs nommees ; reservoirs lus sur le bloc
    // ------------------------------------------------------------------

    private static final int BAY_TOP = TileEntityDroneBay.RADAR.y(), BAY_H = TileEntityDroneBay.RADAR.h(), BAY_RADAR_X = TileEntityDroneBay.RADAR.x(),
          BAY_SCREEN_X = TileEntityDroneBay.CARD.x(), BAY_SCREEN_W = TileEntityDroneBay.CARD.w(), BAY_ROW_Y = TileEntityDroneBay.FILTERS.y();

    private static MutableComponent bay(String key, Object... args) {
        return Component.translatable("drone_bay.pioneers." + key, args);
    }

    void droneBay(GuiGraphics graphics, int mouseX, int mouseY, TileEntityDroneBay tile) {
        PocketKind[] gases = TileEntityDroneBay.gasOrder();
        for (int i = 0; i < gases.length; i++) {
            machines.gasGauge(graphics, mouseX, mouseY, TileEntityDroneBay.GAS_X + i * TileEntityDroneBay.GAS_STEP, BAY_TOP, StandardLayout.GAUGE_W, BAY_H, tile.tankFor(gases[i]),
                  Component.translatable(gases[i].getTranslationKey()));
        }
        long filter = gui.value(TileEntityDroneBay.FILTER);
        DroneRadar.draw(gui, graphics, mouseX, mouseY, tile, filter, BAY_RADAR_X, BAY_TOP, BAY_H);
        //Etat
        gui.screenAt(graphics, BAY_SCREEN_X, BAY_TOP, BAY_SCREEN_W, BAY_H);
        List<Component> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (gui.value(TileEntityDroneBay.FORMED) == 0) {
            lines.add(bay("not_built"));
            colors.add(RED);
        } else if (gui.value(TileEntityDroneBay.GAS_GIANT) == 0) {
            lines.add(bay("not_giant"));
            colors.add(RED);
        } else {
            lines.add(bay("built", "MK" + ROMAN[(int) Math.max(0, Math.min(3, gui.value(TileEntityDroneBay.TIER)))], gui.value(TileEntityDroneBay.PADS)));
            colors.add(GREEN);
        }
        //Lot 8c : ecran des recoltes sous la rangee des reglages (drones en vol / a bord, gaz et objets recoltes, pertes dans les orages)
        Rect st = TileEntityDroneBay.STATS;
        List<GuiMachine.Row> rows = new ArrayList<>();
        rows.add(new GuiMachine.Row(bay("stats", GuiMachine.compact(gui.value(TileEntityDroneBay.GAS_HARVESTED)), gui.value(TileEntityDroneBay.ITEMS_HARVESTED)),
              Component.empty(), PiMenuScreen.SCREEN_TEXT));
        if (gui.value(TileEntityDroneBay.LOST) > 0) {
            rows.add(new GuiMachine.Row(bay("lost", gui.value(TileEntityDroneBay.LOST)), Component.empty(), RED));
        }
        gui.info(graphics, st.x(), st.y(), st.w(), st.h(), gui.value(TileEntityDroneBay.LOST) > 0 ? RED & 0xFFFFFF : AQUA & 0xFFFFFF,
              bay("drones", gui.value(TileEntityDroneBay.FLYING), gui.value(TileEntityDroneBay.DRONES), gui.value(TileEntityDroneBay.PLATED)),
              Component.empty(), -1, 0, rows, false);
        float ly = BAY_TOP + 4;
        for (int i = 0; i < lines.size() && ly < BAY_TOP + BAY_H - 6; i++) {
            int room = Math.max(1, (int) ((BAY_TOP + BAY_H - 4 - ly) / 6));
            ly += GuiDraw.wrapped(graphics, gui.font(), lines.get(i), BAY_SCREEN_X + 4, ly, 0.5F, colors.get(i), BAY_SCREEN_W - 8, Math.min(4, room), 6) * 6
                  + 2;
        }
        Rect en = TileEntityDroneBay.ENERGY;
        gui.energy(graphics, mouseX, mouseY, en.x(), en.y(), en.w(), en.h());
        //Filtres : un bouton par contenu
        PocketKind[] kinds = PocketKind.values();
        for (int i = 0; i < kinds.length; i++) {
            PocketKind kind = kinds[i];
            int bx = 8 + i * 13;
            boolean on = TileEntityDroneBay.accepts(filter, kind);
            gui.steelButton(graphics, mouseX, mouseY, bx, BAY_ROW_Y, 12, 12, Component.empty(), true,
                  () -> gui.send(TileEntityDroneBay.ACTION_FILTER + kind.ordinal()));
            graphics.fill(bx + 3, BAY_ROW_Y + 3, bx + 9, BAY_ROW_Y + 9, (on ? 0xFF000000 : 0x50000000) | kind.color);
            if (!on) {
                for (int k = 0; k < 6; k++) {
                    graphics.fill(bx + 3 + k, BAY_ROW_Y + 3 + k, bx + 4 + k, BAY_ROW_Y + 4 + k, 0xFFFF3030);
                }
            }
            if (gui.over(mouseX, mouseY, bx, BAY_ROW_Y, 12, 12)) {
                gui.tooltip(List.of(Component.translatable(kind.getTranslationKey()).withStyle(style -> style.withColor(kind.color)),
                      bay(on ? "filter.on" : "filter.off").withStyle(ChatFormatting.GRAY)));
            }
        }
        int mx = TileEntityDroneBay.MODE_BUTTON.x();
        gui.steelButton(graphics, mouseX, mouseY, mx, BAY_ROW_Y, TileEntityDroneBay.MODE_BUTTON.w(), 12, bay(gui.value(TileEntityDroneBay.MODE) == 0 ? "mode.auto" : "mode.marked"), true,
              () -> gui.send(TileEntityDroneBay.ACTION_MODE));
        if (gui.over(mouseX, mouseY, mx, BAY_ROW_Y, 56, 12)) {
            gui.tooltip(List.of(bay("mode.tooltip")));
        }
        int rx = TileEntityDroneBay.RECALL.x();
        gui.steelButton(graphics, mouseX, mouseY, rx, BAY_ROW_Y, 52, 12, bay("recall"), gui.value(TileEntityDroneBay.FLYING) > 0, () -> gui.send(TileEntityDroneBay.ACTION_RECALL));
        if (gui.over(mouseX, mouseY, rx, BAY_ROW_Y, 52, 12)) {
            gui.tooltip(List.of(bay("recall.tooltip")));
        }
    }

    // ------------------------------------------------------------------
    // Complexe de lancement (236 de large) : 4 mission, 5 taille, 6 destination, 7 regle, 8 minuterie, 9 etat, 10 compte a rebours,
    // 11 lancements, 12 reussites, 13 echecs, 14 envoyes, 15 recus, 16 sondes a destination, 17 tier fusee, 18 carburant, 19 capacite,
    // 20 carburant demande, 21 duree, 22 escale, 23 remplissage ; reservoirs, nom, cible et modules lus sur le bloc
    // ------------------------------------------------------------------

    //Lot 8c : vraie v2 (geometrie de TileEntityLaunchPad)
    private static final int PAD_TOP = TileEntityLaunchPad.PAD_TOP, PAD_SCREEN_X = TileEntityLaunchPad.STATUS_SCREEN.x(), PAD_SCREEN_W = TileEntityLaunchPad.STATUS_SCREEN.w(),
          PAD_STATUS_Y = TileEntityLaunchPad.STATUS_SCREEN.y(), PAD_TARGET_Y = TileEntityLaunchPad.TARGET_ROW.y(), PAD_INFO_Y = TileEntityLaunchPad.INFO_ROW.y();

    private static MutableComponent pad(String key, Object... args) {
        return Component.translatable("launch_pad.pioneers." + key, args);
    }

    private static MutableComponent cargo(String key, Object... args) {
        return Component.translatable("cargo.pioneers." + key, args);
    }

    private static int missionColor(Mission mission) {
        return switch (mission) {
            case MANNED -> GREEN;
            case PROBE -> GOLD;
            case LOGISTICS -> 0xFFFF77FF;
            case RECEIVE -> AQUA;
        };
    }

    void launchPad(GuiGraphics graphics, int mouseX, int mouseY, TileEntityLaunchPad tile) {
        Mission mission = Mission.values()[(int) Math.floorMod(gui.value(TileEntityLaunchPad.MISSION), Mission.values().length)];
        Status status = Status.values()[(int) Math.max(0, Math.min(gui.value(TileEntityLaunchPad.STATUS), Status.values().length - 1))];
        int tierIndex = (int) gui.value(TileEntityLaunchPad.ROCKET_TIER);
        RocketTier tier = tierIndex < 0 ? null : RocketTier.byIndex(tierIndex);
        List<ItemStack> modules = tile.getActiveModules();
        //Nom de la station (champ de saisie par-dessus) et mission
        gui.screenAt(graphics, 8, PAD_TOP, 118, 13);
        gui.steelButton(graphics, mouseX, mouseY, 130, PAD_TOP, 98, 13, pad("mission." + mission.name().toLowerCase(Locale.ROOT)).withStyle(style -> style.withColor(missionColor(mission) & 0xFFFFFF)), true,
              () -> gui.send(TileEntityLaunchPad.ACTION_MISSION));
        if (gui.over(mouseX, mouseY, 130, PAD_TOP, 98, 13)) {
            gui.tooltip(List.of(pad("mission.tooltip")));
        }
        //Carburant
        Rect fu = TileEntityLaunchPad.FUEL;
        machines.fluidGauge(graphics, mouseX, mouseY, fu.x(), fu.y(), fu.w(), fu.h(), tile.fuelTank);
        padStatus(graphics, mouseX, mouseY, tile, mission, status, tier);
        padTarget(graphics, mouseX, mouseY, tile, mission);
        padInfo(graphics, mouseX, mouseY, mission, tier, modules);
        padModules(graphics, mouseX, mouseY, tile, modules);
        padDock(graphics, mouseX, mouseY, tile, mission);
    }

    /** Ecran principal : etat, fusee et carburant, ligne propre a la mission. */
    private void padStatus(GuiGraphics graphics, int mouseX, int mouseY, TileEntityLaunchPad tile, Mission mission, Status status, @Nullable RocketTier tier) {
        int x = PAD_SCREEN_X, y = PAD_STATUS_Y, w = PAD_SCREEN_W;
        gui.screenAt(graphics, x, y, w, 30);
        int tx = x + 4, tw = w - 8;
        MutableComponent state;
        if (status == Status.COUNTDOWN) {
            state = pad("countdown", Math.max(1, (gui.value(TileEntityLaunchPad.COUNTDOWN) + 19) / 20));
        } else if (status == Status.MAINTENANCE) {
            state = cargo("maintenance", gui.value(TileEntityLaunchPad.TURNAROUND));
        } else {
            state = pad("status." + status.name().toLowerCase(Locale.ROOT));
        }
        int color = switch (status) {
            case NOT_FORMED, PAD_TOO_SMALL, NO_PASSENGER, NO_PROBE, RESEARCH_LOCKED, PROBE_LOCKED, TIER_TOO_LOW, SKY_BLOCKED, NO_CARGO_MODULE,
                 NO_TARGET, BUFFER_FULL, WRONG_FUEL -> RED;
            case COUNTDOWN, LANDING, MAINTENANCE, PAD_OCCUPIED -> GOLD;
            case IN_FLIGHT, UNLOADING, BOARDED -> AQUA;
            case FUELING, REFUELING, WAITING_CARGO, NO_ROCKET -> 0xFFFFFF55;
            case DISABLED -> DIM;
            default -> GREEN;
        };
        GuiDraw.text(graphics, gui.font(), state, tx, y + 4, 0.7F, color, tw);
        if (status == Status.WRONG_FUEL && tier != null && gui.over(mouseX, mouseY, tx, y + 3, tw, 8)) {
            gui.tooltip(List.of(pad("wrong_fuel.tooltip", Propellant.forTier(tier).getName(), tier.level)
                  .withStyle(ChatFormatting.GOLD)));
        }
        if ((status == Status.RESEARCH_LOCKED || status == Status.PROBE_LOCKED) && gui.over(mouseX, mouseY, tx, y + 3, tw, 8)) {
            gui.tooltip(List.of(Component.translatable("research.pioneers.blocked").withStyle(ChatFormatting.GOLD)));
        }
        //Fusee et carburant
        int size = (int) gui.value(TileEntityLaunchPad.SIZE);
        if (tier == null) {
            Size padSize = Size.byIndex(size);
            Component line = padSize == null ? pad("gui.not_formed") : pad("gui.complex", Component.translatable(padSize.getTranslationKey()).withStyle(st -> st.withColor(padSize.textColor())), padSize.maxTier);
            GuiDraw.text(graphics, gui.font(), line, tx, y + 13, 0.55F, padSize == null ? RED : AQUA, tw);
        } else {
            long fuel = gui.value(TileEntityLaunchPad.ROCKET_FUEL), needed = Math.max(1, gui.value(TileEntityLaunchPad.FUEL_NEEDED) > 0 ? gui.value(TileEntityLaunchPad.FUEL_NEEDED) : gui.value(TileEntityLaunchPad.ROCKET_CAPACITY));
            double ratio = Math.min(1, fuel / (double) needed);
            int fuelColor = ratio >= 1 ? GREEN : GOLD;
            GuiDraw.text(graphics, gui.font(), pad("gui.rocket_fuel", tier.level, Texts.format(fuel), Texts.format(needed)), tx, y + 13, 0.55F, DIM, tw - 24);
            GuiDraw.right(graphics, gui.font(), GuiMachine.percent(ratio), tx + tw, y + 13, 0.55F, fuelColor);
            GuiDraw.bar(graphics, tx, y + 18, tw, 2, ratio, fuelColor);
        }
        //Detail de la mission
        Component detail = switch (mission) {
            case MANNED -> pad("gui.manned_detail");
            case PROBE -> pad("gui.launches", gui.value(TileEntityLaunchPad.LAUNCHES), gui.value(TileEntityLaunchPad.SUCCESSES), gui.value(TileEntityLaunchPad.FAILURES));
            case LOGISTICS -> gui.value(TileEntityLaunchPad.FUEL_NEEDED) > 0 ? cargo("trip", gui.value(TileEntityLaunchPad.ETA), Texts.format(gui.value(TileEntityLaunchPad.FUEL_NEEDED))) : cargo("stats", gui.value(TileEntityLaunchPad.SENT), gui.value(TileEntityLaunchPad.RECEIVED));
            case RECEIVE -> cargo("stats", gui.value(TileEntityLaunchPad.SENT), gui.value(TileEntityLaunchPad.RECEIVED));
        };
        GuiDraw.text(graphics, gui.font(), detail, tx, y + 23, 0.5F, DIM, tw);
    }

    /** Ligne de destination : astre des sondes, station cible, ou rappel de la mission. */
    private void padTarget(GuiGraphics graphics, int mouseX, int mouseY, TileEntityLaunchPad tile, Mission mission) {
        boolean choice = mission == Mission.PROBE || mission == Mission.LOGISTICS;
        boolean locked = gui.value(TileEntityLaunchPad.COUNTDOWN) > 0;
        int sx = PAD_SCREEN_X + 14, sw = PAD_SCREEN_W - 28;
        if (choice) {
            gui.steelButton(graphics, mouseX, mouseY, PAD_SCREEN_X, PAD_TARGET_Y, 12, 12, Component.literal("<"), !locked,
                  () -> gui.send(TileEntityLaunchPad.ACTION_PREVIOUS));
            gui.steelButton(graphics, mouseX, mouseY, PAD_SCREEN_X + PAD_SCREEN_W - 12, PAD_TARGET_Y, 12, 12, Component.literal(">"), !locked,
                  () -> gui.send(TileEntityLaunchPad.ACTION_NEXT));
        } else {
            sx = PAD_SCREEN_X;
            sw = PAD_SCREEN_W;
        }
        gui.screenAt(graphics, sx, PAD_TARGET_Y, sw, 12);
        Component target;
        int color = TEXT;
        switch (mission) {
            case PROBE -> {
                ProbeDestination destination = ProbeDestination.byIndex((int) gui.value(TileEntityLaunchPad.DESTINATION));
                target = Component.translatable(destination.getTranslationKey());
                color = 0xFF000000 | destination.color;
            }
            case LOGISTICS -> {
                String label = tile.getTargetLabel();
                if (label.isEmpty()) {
                    target = cargo("no_target");
                    color = RED;
                } else {
                    String[] parts = label.split("\\|", 2);
                    target = Component.literal(parts[0]).append(parts.length > 1 ? Component.literal("  ").append(Component.translatable(parts[1])
                          .withStyle(ChatFormatting.GRAY)) : Component.empty());
                }
            }
            case RECEIVE -> {
                target = cargo("receiving");
                color = AQUA;
            }
            default -> {
                target = pad("gui.manned_target");
                color = DIM;
            }
        }
        GuiDraw.center(graphics, gui.font(), target, sx + sw / 2F, PAD_TARGET_Y + 3.5F, 0.65F, color, sw - 6);
    }

    /** Ligne d'information : exigences des sondes, regle de depart, rappel. */
    private void padInfo(GuiGraphics graphics, int mouseX, int mouseY, Mission mission, @Nullable RocketTier tier, List<ItemStack> modules) {
        int x = PAD_SCREEN_X, w = PAD_SCREEN_W;
        switch (mission) {
            case PROBE -> {
                ProbeDestination destination = ProbeDestination.byIndex((int) gui.value(TileEntityLaunchPad.DESTINATION));
                int probes = RocketModule.count(modules, RocketModule.PROBE);
                GuiDraw.text(graphics, gui.font(), pad("gui.probe_requires", destination.minTier.level, destination.probeTier(),
                      Texts.format(TileEntityLaunchPad.hydrogenFor(destination, modules))), x, PAD_INFO_Y + 1, 0.55F, DIM, w);
                Component orbit = tier == null ? pad("gui.in_orbit", gui.value(TileEntityLaunchPad.PROBES_AT))
                                               : pad("gui.probe_payload", probes, RocketModule.probeTier(tier),
                                                     GuiMachine.percent(TileEntityLaunchPad.failureFor(tier, modules)), gui.value(TileEntityLaunchPad.PROBES_AT));
                GuiDraw.text(graphics, gui.font(), orbit, x, PAD_INFO_Y + 8, 0.55F, AQUA, w);
            }
            case LOGISTICS -> {
                Rule rule = Rule.values()[(int) Math.floorMod(gui.value(TileEntityLaunchPad.RULE), Rule.values().length)];
                gui.steelButton(graphics, mouseX, mouseY, x, PAD_INFO_Y, 98, 12, cargo("rule", Component.translatable("cargo.pioneers.rule."
                      + rule.name().toLowerCase(Locale.ROOT))), true, () -> gui.send(TileEntityLaunchPad.ACTION_RULE));
                if (rule == Rule.TIMER) {
                    gui.steelButton(graphics, mouseX, mouseY, x + 100, PAD_INFO_Y, 34, 12, Component.literal(gui.value(TileEntityLaunchPad.TIMER) + " s"), true,
                          () -> gui.send(TileEntityLaunchPad.ACTION_TIMER));
                }
                int bx = x + w - 38;
                gui.frame(graphics, bx, PAD_INFO_Y + 1, 38, 10);
                GuiDraw.bar(graphics, bx + 1, PAD_INFO_Y + 2, 36, 8, gui.value(TileEntityLaunchPad.FILL) / 100.0, 0xFF000000 | RocketModule.dominantCargo(modules).color);
                if (gui.over(mouseX, mouseY, bx, PAD_INFO_Y + 1, 38, 10)) {
                    gui.tooltip(List.of(GuiMachine.percent(gui.value(TileEntityLaunchPad.FILL) / 100.0)));
                }
            }
            case RECEIVE -> GuiDraw.wrapped(graphics, gui.font(), cargo("receiver_hint"), x, PAD_INFO_Y + 1, 0.55F, DIM, w, 2, 7);
            default -> GuiDraw.wrapped(graphics, gui.font(), pad("gui.manned_hint"), x, PAD_INFO_Y + 1, 0.55F, DIM, w, 2, 7);
        }
    }

    /** Modules de la fusee posee (lecture seule), compteur et effets. */
    private void padModules(GuiGraphics graphics, int mouseX, int mouseY, TileEntityLaunchPad tile, List<ItemStack> modules) {
        int capacity = tile.getModuleCapacity();
        for (int i = 0; i < RocketModule.MAX_SLOTS; i++) {
            int x = TileEntityLaunchPad.MODULES_X + i * 18, y = TileEntityLaunchPad.MODULES_Y;
            if (i >= capacity) {
                GuiDraw.locked(graphics, x, y);
                continue;
            }
            PiMenuScreen.slot(graphics, x, y);
            ItemStack stack = i < modules.size() ? modules.get(i) : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x, y);
            }
            if (gui.over(mouseX, mouseY, x - 1, y - 1, 18, 18)) {
                gui.tooltip(stack.isEmpty() ? List.of(pad("gui.modules_hint").withStyle(ChatFormatting.GRAY))
                                            : List.of(stack.getHoverName(), pad("gui.modules_hint").withStyle(ChatFormatting.DARK_GRAY)));
            }
        }
        int tx = TileEntityLaunchPad.MODULES_X + RocketModule.MAX_SLOTS * 18 + 3, tw = 236 - 8 - tx;
        GuiDraw.text(graphics, gui.font(), capacity == 0 ? Component.literal("-") : Component.literal(modules.size() + "/" + capacity), tx,
              TileEntityLaunchPad.MODULES_Y + 1, 0.6F, TEXT, tw);
        List<String> effects = new ArrayList<>();
        double fuel = RocketModule.fuelMultiplier(modules), time = RocketModule.timeMultiplier(modules), failure = RocketModule.failureMultiplier(modules);
        if (fuel < 1) {
            effects.add("H\u2082-" + Math.round((1 - fuel) * 100) + "%");
        }
        if (time < 1) {
            effects.add("\u23F1-" + Math.round((1 - time) * 100) + "%");
        }
        if (failure < 1) {
            effects.add("\u26E8-" + Math.round((1 - failure) * 100) + "%");
        }
        for (int i = 0; i < effects.size(); i++) {
            GuiDraw.text(graphics, gui.font(), Component.literal(effects.get(i)), tx, TileEntityLaunchPad.MODULES_Y + 8 + i * 5, 0.45F, 0xFF7CE070, tw);
        }
        if (gui.over(mouseX, mouseY, tx, TileEntityLaunchPad.MODULES_Y, tw, 18)) {
            gui.tooltip(List.of(capacity == 0 ? pad("gui.no_rocket_modules") : pad("gui.modules", modules.size(), capacity),
                  effects.isEmpty() ? pad("gui.no_upgrade").withStyle(ChatFormatting.GRAY) : Component.literal(String.join("  ", effects))));
        }
    }

    /** Quai : soutes a fluide, gaz et donnees a droite des 18 cases ; voile si la mission ne s'en sert pas (sondes). */
    private void padDock(GuiGraphics graphics, int mouseX, int mouseY, TileEntityLaunchPad tile, Mission mission) {
        int gy = TileEntityLaunchPad.DOCK.y(), gh = TileEntityLaunchPad.DOCK.h();
        int gx = TileEntityLaunchPad.DOCK.x();
        machines.fluidGauge(graphics, mouseX, mouseY, gx, gy, 10, gh, tile.fluidCargo);
        machines.gasGauge(graphics, mouseX, mouseY, gx + 12, gy, 10, gh, tile.gasCargo, pad("gui.gas_dock"));
        var data = tile.getDataCargo();
        int dx = gx + 24;
        gui.frame(graphics, dx, gy, 10, gh);
        int filled = (int) Math.round((gh - 2) * Math.min(1, data.getStored() / (double) Math.max(1, data.getCapacity())));
        if (filled > 0 && !data.isEmpty()) {
            graphics.fill(dx + 1, gy + gh - 1 - filled, dx + 9, gy + gh - 1, 0xFF000000 | data.getType().getColor());
            graphics.fill(dx + 1, gy + gh - 1 - filled, dx + 9, gy + gh - filled, 0x80FFFFFF);
        }
        GuiMachine.ticks(graphics, dx, gy, 10, gh);
        if (gui.over(mouseX, mouseY, dx, gy, 10, gh)) {
            gui.tooltip(List.of(pad("gui.data_dock"), data.isEmpty() ? Component.translatable("data.pioneers.tank.empty") : data.getType().getName(),
                  Component.translatable("data.pioneers.gauge", DataUnits.format(data.getStored()),
                        DataUnits.format(data.getCapacity()))));
        }
        if (mission == Mission.PROBE) {
            int x0 = TileEntityLaunchPad.DOCK_X - 1, w = dx + 10 - x0;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 260);
            graphics.fill(x0, gy - 1, x0 + w, gy + gh + 1, 0xD0161A20);
            GuiDraw.center(graphics, gui.font(), pad("gui.cargo_unused"), x0 + w / 2F, gy + 15, 0.65F, DIM, w - 8);
            graphics.pose().popPose();
        }
    }
}

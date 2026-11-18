package fr.pioneers.client.gui.core;

import fr.pioneers.api.data.DataType;
import fr.pioneers.api.machine.MachineSpec;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.space.research.client.ClientResearch;
import fr.pioneers.space.research.client.TechTreeScreen;
import fr.pioneers.core.common.PiLang;
import fr.pioneers.core.Texts;
import fr.pioneers.core.machine.DataMachineTile;
import fr.pioneers.core.data.AnalysisData;
import fr.pioneers.core.data.BasicDataTank;
import fr.pioneers.core.data.DataUnits;
import fr.pioneers.core.registries.PiItems;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.research.ProbeDestination;
import fr.pioneers.space.research.ResearchEffects;
import fr.pioneers.space.research.ResearchNode;
import fr.pioneers.space.tile.TileEntityAnalyzer.Status;
import fr.pioneers.space.tile.TileEntityAnalyzer;
import fr.pioneers.space.tile.TileEntityAsteroidCrusher;
import fr.pioneers.space.tile.TileEntityBiomeAnalyzer;
import fr.pioneers.space.tile.TileEntityDataAntenna;
import fr.pioneers.space.tile.TileEntityResearchTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Vues de l'interface standard (sombre, couleur de la machine) des machines scientifiques passees au socle (lot B) : broyeur
 * d'asteroides, spectrometre, sequenceur ; analyseur de biome et table de recherche. Positions reprises des anciens ecrans Mekanism.
 * Tout texte variable passe par {@link GuiDraw} avec une largeur maximale (reduit, puis coupe) : aucun debordement.
 */
/**
 * API Machine (lot 8b) : INTERFACE STANDARD v2 des machines scientifiques (corps propres en VRAIE v2, plus seulement encadres) : broyeur
 * d'asteroides, spectrometre, sequenceur (vue {@code science} : reserve d'energie et batterie a la place commune, entrees, carte de la
 * recherche visee, ecran d'etat raccourci, rangee des reservoirs de sortie), analyseur de biome (9 cellules et leurs reservoirs, carte, ecran),
 * antenne (reservoir, carte, ecran, rangee de la destination), table de recherche (vue {@code research} : pages, ecrans principal et de
 * l'unite, page memoire, impression des plans). Valeurs lues par leur NOM ({@link UiValues}), machine choisie par la cle de sa fiche
 * ({@link StorageViews#as} au lieu d'{@code instanceof}). Tout texte variable passe par {@link GuiDraw} avec une largeur maximale.
 */
final class ScienceViews {

    private static final int TEXT = PiMenuScreen.TEXT, DIM = PiMenuScreen.DIM, SCREEN_TEXT = PiMenuScreen.SCREEN_TEXT;
    private static final int GREEN = 0xFF55FF88, RED = 0xFFFF6A5A, GOLD = 0xFFFFAA00, AQUA = 0xFF55DDFF;

    private final GuiMachine gui;

    private ScienceViews(GuiMachine gui) {
        this.gui = gui;
    }

    @Nullable
    private static String key(BlockEntity be) {
        MachineSpec spec = MachineScreens.spec(be);
        return spec == null ? null : spec.key();
    }

    /** Vue {@code science} : broyeur, spectrometre, sequenceur, analyseur de biome, antenne. */
    static final MachineScreens.View VIEW = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            ScienceViews v = new ScienceViews(gui);
            TileEntityAnalyzer analyzer = StorageViews.as(TileEntityAnalyzer.class, be);
            if (analyzer != null) {
                v.analyzer(g, mouseX, mouseY, analyzer, "asteroid_crusher".equals(key(be)), "geo_spectrometer".equals(key(be)));
            } else if ("biome_analyzer".equals(key(be))) {
                v.biome(g, mouseX, mouseY);
            } else {
                TileEntityDataAntenna antenna = StorageViews.as(TileEntityDataAntenna.class, be);
                if (antenna != null) {
                    v.antenna(g, mouseX, mouseY, antenna);
                }
            }
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            if (StorageViews.as(TileEntityAnalyzer.class, be) != null) {
                gui.slotTip(mouseX, mouseY, TileEntityAnalyzer.BATTERY_X, TileEntityAnalyzer.BATTERY_Y, "gui.pioneers.machine.battery");
            }
            return true;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity be) {
            return energyRows(gui);
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_science";
        }
    };

    /** Vue {@code research} : table de recherche. */
    static final MachineScreens.View RESEARCH = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            TileEntityResearchTable table = StorageViews.as(TileEntityResearchTable.class, be);
            if (table != null) {
                new ScienceViews(gui).research(g, mouseX, mouseY, table);
            }
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            gui.slotTip(mouseX, mouseY, TileEntityResearchTable.OUTPUT_X, TileEntityResearchTable.OUTPUT_Y, "gui.pioneers.machine.out");
            return true;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity be) {
            return energyRows(gui);
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_science";
        }
    };

    /** Onglet Infos commun : consommation et reserve d'energie. */
    static List<StandardView.InfoRow> energyRows(GuiMachine gui) {
        List<StandardView.InfoRow> rows = new ArrayList<>();
        if (gui.value(UiValues.CAPACITY) > 0) {
            rows.add(new StandardView.InfoRow("consumption", GuiMachine.literal(GuiMachine.compact(gui.value(UiValues.USAGE) / 100.0) + " FE/t"), 0xFFFFFF55));
            rows.add(new StandardView.InfoRow("reserve", GuiMachine.literal(Texts.energy(gui.value(UiValues.CAPACITY))), PiMenuScreen.TEXT));
        }
        return rows;
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("analyzer.pioneers." + key, args);
    }
    // ------------------------------------------------------------------
    // Analyseurs : avancement, etat, recherche visee, FE/t ; type et contenu de chaque reservoir de sortie
    // ------------------------------------------------------------------

    private void analyzer(GuiGraphics graphics, int mouseX, int mouseY, TileEntityAnalyzer tile, boolean crusher, boolean geology) {
        int accent = gui.accent();
        gui.energy(graphics, mouseX, mouseY, 8, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.ENERGY_H);
        //Avancement de l'objet en cours, sous les entrees
        Rect bar = TileEntityAnalyzer.BAR;
        GuiDraw.bar(graphics, bar.x(), bar.y(), bar.w(), bar.h(), gui.value(UiValues.PROGRESS_X1000) / 1000.0, 0xFF000000 | accent);
        if (crusher) {
            for (int type = 0; type < 4; type++) {
                int[] p = tile.inputSlotPosition(type);
                if (gui.getMenu().getSlot(type).getItem().isEmpty()) {
                    GuiDraw.ghost(graphics, TileEntityAsteroidCrusher.meteorite(type), p[0], p[1]);
                }
            }
        }
        Rect c = TileEntityAnalyzer.CARD;
        gui.screenAt(graphics, c.x(), c.y(), c.w(), c.h());
        int left = c.x() + 6, right = c.x() + c.w() - 6, width = c.w() - 12, top = c.y();
        long research = gui.value(UiValues.RESEARCH);
        ResearchNode node = crusher ? TileEntityAsteroidCrusher.currentTier() : research < 0 ? null : ResearchNode.byIndex((int) research);
        String kind = crusher ? "asteroids" : geology ? "geology" : "biology";
        if (node == null) {
            GuiDraw.text(graphics, gui.font(), tr("no_target"), left, top + 6, 0.7F, DIM, width);
            GuiDraw.text(graphics, gui.font(), tr("hint." + kind), left, top + 17, 0.55F, DIM, width);
            GuiDraw.text(graphics, gui.font(), tr("hint2." + kind), left, top + 24, 0.55F, DIM, width);
        } else {
            boolean done = ClientResearch.isCompleted(node);
            GuiDraw.text(graphics, gui.font(), Component.translatable(node.getTranslationKey()), left, top + 6, 0.7F, done ? GREEN : TEXT, width);
            Body body = node.getBody();
            Component sub = body == null ? Component.translatable(node.kind.getTranslationKey())
                                         : Component.translatable(node.kind.getTranslationKey()).append(" · ").append(Component.translatable(body.getTranslationKey()));
            GuiDraw.text(graphics, gui.font(), sub, left, top + 14, 0.55F, DIM, width);
            if (node.sampleTypes() > 1) {
                for (int type = 0; type < node.sampleTypes(); type++) {
                    int y = top + 22 + type * 9;
                    int have = done ? node.units : Math.min(node.units, ClientResearch.getSamples(node, type));
                    boolean full = have >= node.units;
                    GuiDraw.text(graphics, gui.font(), tile.typeName(type), left, y, 0.55F, full ? GREEN : DIM, 40);
                    GuiDraw.right(graphics, gui.font(), Component.literal(Texts.format(have) + "/" + Texts.format(node.units)), right, y, 0.55F,
                          full ? GREEN : SCREEN_TEXT);
                    GuiDraw.bar(graphics, left, y + 5, width, 2, have / (double) node.units, full ? GREEN : 0xFF000000 | accent);
                }
            } else {
                int have = done ? node.units : Math.min(node.units, ClientResearch.getSamples(node, 0));
                GuiDraw.bar(graphics, left, top + 27, width, 5, have / (double) node.units, done ? GREEN : 0xFF000000 | accent);
                GuiDraw.text(graphics, gui.font(), tr("count." + kind, Texts.format(have), Texts.format(node.units)), left, top + 36, 0.6F, TEXT,
                      width - 24);
                GuiDraw.right(graphics, gui.font(), GuiMachine.percent(have / (double) node.units), right, top + 36, 0.6F, SCREEN_TEXT);
            }
        }
        //Ecran d'etat (le detail d'une recherche verrouillee est dans l'infobulle)
        Status status = TileEntityAnalyzer.status(gui.value(UiValues.STATUS));
        int color = switch (status) {
            case WORKING -> GREEN;
            case LOCKED, NO_ENERGY, UNKNOWN -> RED;
            case OUTPUT_FULL -> GOLD;
            case COMPLETE -> AQUA;
            default -> DIM;
        };
        Rect s = StandardLayout.shortScreen(StandardLayout.WIDTH);
        List<GuiMachine.Row> rows = new ArrayList<>();
        rows.add(GuiMachine.Row.of("gui.pioneers.api.info.consumption", GuiMachine.literal(Texts.format(gui.value(UiValues.FE_PER_TICK)) + " FE/t")));
        gui.info(graphics, s.x(), s.y(), s.w(), s.h(), color & 0xFFFFFF, tr("status." + status.name().toLowerCase(Locale.ROOT)),
              GuiMachine.percent(gui.value(UiValues.PROGRESS_X1000) / 1000.0), -1, accent, rows, false);
        if (status == Status.LOCKED && node != null && gui.over(mouseX, mouseY, s.x(), s.y(), s.w(), 16)) {
            gui.tooltip(List.of(Component.translatable("research.pioneers.blocked").withStyle(ChatFormatting.GOLD),
                  Component.translatable(node.getTranslationKey()).withStyle(ChatFormatting.WHITE),
                  tr("status.locked_detail", lockedHint(node)).withStyle(ChatFormatting.RED)));
        }
        //Reservoirs de sortie
        List<long[]> tanks = new ArrayList<>();
        for (int i = 0; i < tile.getOutputTanks().size(); i++) {
            tanks.add(new long[]{gui.value(UiValues.dataType(i)), gui.value(UiValues.dataStored(i)), TileEntityAnalyzer.OUTPUT_CAPACITY});
        }
        Rect r = StandardLayout.settingRow(StandardLayout.WIDTH);
        dataRow(graphics, mouseX, mouseY, r.x(), r.y(), r.w(), r.h(), tanks);
    }

    // ------------------------------------------------------------------
    // Analyseur de biome : cellules en production, cout par cellule ; type et contenu de chaque reservoir
    // ------------------------------------------------------------------

    private void biome(GuiGraphics graphics, int mouseX, int mouseY) {
        gui.energy(graphics, mouseX, mouseY, 8, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.ENERGY_H);
        for (int i = 0; i < TileEntityBiomeAnalyzer.CELL_SLOTS; i++) {
            if (gui.getMenu().getSlot(i).getItem().isEmpty()) {
                GuiDraw.ghost(graphics, new ItemStack(PiItems.BIOME_DATA_CELL.get()), TileEntityBiomeAnalyzer.SLOTS_X + i * 18, TileEntityBiomeAnalyzer.SLOTS_Y);
            }
        }
        //Sous chaque cellule, son reservoir : barre a la couleur du biome (infobulle : biome, contenu)
        for (int i = 0; i < TileEntityBiomeAnalyzer.CELL_SLOTS; i++) {
            DataType type = DataMachineTile.typeById(gui.value(UiValues.dataType(i)));
            long stored = gui.value(UiValues.dataStored(i));
            int x = TileEntityBiomeAnalyzer.SLOTS_X - 1 + i * 18, y = TileEntityBiomeAnalyzer.SLOTS_Y + 19, w = 18, h = 6;
            graphics.fill(x, y, x + w, y + h, 0xFF0B1016);
            graphics.fill(x, y, x + w, y + 1, 0xFF1F242C);
            graphics.fill(x, y + h - 1, x + w, y + h, 0xFF55606F);
            int fill = (int) Math.round((w - 2) * Math.min(1, stored / (double) TileEntityBiomeAnalyzer.TANK_CAPACITY));
            if (fill > 0 && type != null) {
                graphics.fill(x + 1, y + 1, x + 1 + fill, y + h - 1, 0xFF000000 | type.getColor());
            }
            if (gui.over(mouseX, mouseY, x, y, w, h)) {
                gui.tooltip(List.of(type == null ? Component.translatable("data.pioneers.tank.empty").withStyle(ChatFormatting.GRAY)
                                                 : type.getName().copy().withStyle(style -> style.withColor(type.getColor())),
                      Component.translatable("data.pioneers.gauge", DataUnits.format(stored), DataUnits.format(TileEntityBiomeAnalyzer.TANK_CAPACITY))));
            }
        }
        long producing = gui.value(UiValues.CELLS);
        Rect c = TileEntityBiomeAnalyzer.CARD;
        gui.screenAt(graphics, c.x(), c.y(), c.w(), c.h());
        GuiDraw.text(graphics, gui.font(), Component.translatable("biome_analyzer.pioneers.cost", Texts.energy(gui.value(UiValues.CELL_COST))), c.x() + 6,
              c.y() + 6, 0.6F, DIM, c.w() - 12);
        GuiDraw.text(graphics, gui.font(), Component.translatable("gui.pioneers.api.biome.per_cell", DataUnits.format(TileEntityBiomeAnalyzer.DATA_PER_TICK)),
              c.x() + 6, c.y() + 16, 0.6F, DIM, c.w() - 12);
        Rect s = StandardLayout.screen(StandardLayout.WIDTH);
        List<GuiMachine.Row> rows = new ArrayList<>();
        rows.add(GuiMachine.Row.of("gui.pioneers.api.biome.cells", GuiMachine.literal(producing + " / " + TileEntityBiomeAnalyzer.CELL_SLOTS)));
        rows.add(GuiMachine.Row.of("gui.pioneers.api.info.consumption", GuiMachine.literal(Texts.energy(producing * gui.value(UiValues.CELL_COST)) + "/t")));
        Component state = producing > 0
                          ? Component.translatable("biome_analyzer.pioneers.producing", producing, DataUnits.format(producing * TileEntityBiomeAnalyzer.DATA_PER_TICK))
                          : Component.translatable("biome_analyzer.pioneers.idle");
        gui.info(graphics, s.x(), s.y(), s.w(), s.h(), producing > 0 ? 0x55FF88 : 0, state, Component.empty(),
              producing / (double) TileEntityBiomeAnalyzer.CELL_SLOTS, 0x55FF88, rows, false);
    }

    // ------------------------------------------------------------------
    // Table de recherche (236 de large) : unite en cours, avancement (pour mille), attente, rang du plan, vitesse x100, sondes par destination
    // ------------------------------------------------------------------

    /** Page affichee (gardee d'une ouverture a l'autre). */
    private static boolean memoryPage;
    private static final int X0 = TileEntityResearchTable.BODY_X, LAB_W = TileEntityResearchTable.BODY_W, MAIN_Y = TileEntityResearchTable.MAIN_Y,
          MAIN_H = TileEntityResearchTable.MAIN_H, UNIT_Y = TileEntityResearchTable.UNIT_Y, UNIT_H = TileEntityResearchTable.UNIT_H,
          LAB_ROW_Y = TileEntityResearchTable.ROW_Y, ROW_W = TileEntityResearchTable.ROW_W;
    private static final ProbeDestination[] ORDER = {
          ProbeDestination.EARTH_ORBIT, ProbeDestination.MOON, ProbeDestination.DEEP_SPACE, ProbeDestination.SUN, ProbeDestination.MERCURY,
          ProbeDestination.VENUS, ProbeDestination.MARS, ProbeDestination.JUPITER, ProbeDestination.SATURN, ProbeDestination.URANUS,
          ProbeDestination.NEPTUNE, ProbeDestination.PLUTO};

    private static MutableComponent lab(String key, Object... args) {
        return Component.translatable("research.pioneers.lab." + key, args);
    }

    private void research(GuiGraphics graphics, int mouseX, int mouseY, TileEntityResearchTable tile) {
        int accent = gui.accent();
        int half = LAB_W / 2;
        gui.pageButton(graphics, mouseX, mouseY, X0, TileEntityResearchTable.PAGE_Y, half - 1, 12, lab("page.research"), !memoryPage, accent,
              () -> memoryPage = false);
        gui.pageButton(graphics, mouseX, mouseY, X0 + half + 1, TileEntityResearchTable.PAGE_Y, LAB_W - half - 1, 12, lab("page.memory"), memoryPage, accent,
              () -> memoryPage = true);
        gui.energy(graphics, mouseX, mouseY, 8, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.ENERGY_H);
        if (memoryPage) {
            memory(graphics, mouseX, mouseY, tile, accent);
        } else {
            labMain(graphics, accent);
            labUnit(graphics, tile, accent);
            gui.steelButton(graphics, mouseX, mouseY, X0, LAB_ROW_Y, ROW_W, 16, lab("open_tree"), true,
                  () -> gui.minecraft().setScreen(new TechTreeScreen(tile.getBlockPos(), gui)));
            if (gui.over(mouseX, mouseY, X0, LAB_ROW_Y, ROW_W, 16)) {
                gui.tooltip(List.of(lab("open_tree.tooltip")));
            }
        }
    }

    /** Ecran principal : recherche commune. */
    private void labMain(GuiGraphics graphics, int accent) {
        gui.screenAt(graphics, X0, MAIN_Y, LAB_W, MAIN_H);
        int x = X0 + 5, y = MAIN_Y + 5, right = X0 + LAB_W - 6;
        ResearchNode active = ClientResearch.getActive();
        if (active == null) {
            GuiDraw.text(graphics, gui.font(), lab("none_title"), x, y + 2, 0.75F, AQUA, LAB_W - 12);
            GuiDraw.text(graphics, gui.font(), lab("no_research"), x, y + 14, 0.55F, DIM, LAB_W - 12);
        } else {
            graphics.renderItem(active.getIcon(), x, y);
            int tx = x + 20, tw = right - tx;
            GuiDraw.text(graphics, gui.font(), Component.translatable(active.getTranslationKey()), tx, y, 0.75F, TEXT, tw);
            int progress = ClientResearch.getProgress(active);
            GuiDraw.bar(graphics, tx, y + 9, tw, 4, progress / (double) active.units, 0xFF000000 | accent);
            Component line = lab("progress", progress, active.units, Math.round(100F * progress / active.units)).append("   ").append(eta(active));
            GuiDraw.text(graphics, gui.font(), line, tx, y + 15, 0.55F, DIM, tw);
        }
        MutableComponent line = lab("labs", ClientResearch.getLabs()).append("   ").append(lab("speed", String.format(Locale.ROOT, "%.2f",
              gui.value(UiValues.SPEED_X100) / 100.0)));
        int waiting = (int) gui.value(UiValues.WAITING);
        Component status = switch (waiting) {
            case 0 -> lab("status.working");
            case 1 -> lab("status.data");
            case 2 -> lab("status.energy");
            case 4 -> lab("status.compute");
            default -> lab("status.idle");
        };
        int statusColor = switch (waiting) {
            case 0 -> GREEN;
            case 1 -> GOLD;
            case 2, 4 -> RED;
            default -> DIM;
        };
        float sw = gui.font().width(status) * 0.55F;
        GuiDraw.text(graphics, gui.font(), line, x, y + 27, 0.55F, DIM, LAB_W - 18 - sw);
        GuiDraw.right(graphics, gui.font(), status, right, y + 27, 0.55F, statusColor);
    }

    private static Component eta(ResearchNode node) {
        int labs = ClientResearch.getLabs();
        if (labs <= 0) {
            return Component.translatable("research.pioneers.tree.eta_none");
        }
        int remaining = node.units - ClientResearch.getProgress(node);
        long seconds = (long) Math.ceil(remaining * node.unitSeconds / (labs * ResearchEffects.labSpeedMultiplier()));
        String time = seconds >= 3600 ? seconds / 3600 + "h" + String.format("%02d", seconds % 3600 / 60) : seconds / 60 + ":" + String.format("%02d", seconds % 60);
        return Component.translatable("research.pioneers.tree.eta", time);
    }

    /** Unite en cours : uniquement les donnees qu'elle demande, et combien d'unites la memoire permet. */
    private void labUnit(GuiGraphics graphics, TileEntityResearchTable tile, int accent) {
        gui.screenAt(graphics, X0, UNIT_Y, LAB_W, UNIT_H);
        int x = X0 + 5, y = UNIT_Y + 5, right = X0 + LAB_W - 6, w = right - x;
        int working = (int) gui.value(UiValues.UNIT);
        double unitProgress = gui.value(UiValues.PROGRESS_X1000);
        ResearchNode shown = working >= 0 ? ResearchNode.byIndex(working) : ClientResearch.getActive();
        Component unit = working >= 0 ? lab("unit_working", Math.round(unitProgress / 10.0)) : lab("unit_waiting");
        float uw = gui.font().width(unit) * 0.55F;
        GuiDraw.text(graphics, gui.font(), lab("unit_title"), x, y, 0.55F, AQUA, w - uw - 4);
        if (shown == null) {
            return;
        }
        GuiDraw.right(graphics, gui.font(), unit, right, y, 0.55F, working >= 0 ? GREEN : DIM);
        GuiDraw.bar(graphics, x, y + 6, w, 2, unitProgress / 1000.0, 0xFF000000 | accent);
        int row = y + 11;
        for (Map.Entry<ProbeDestination, Long> cost : shown.getUnitCost().entrySet()) {
            if (row > UNIT_Y + UNIT_H - 8) {
                break;
            }
            ProbeDestination type = cost.getKey();
            long stored = tile.getData(type);
            long units = stored / Math.max(1, cost.getValue());
            int color = type.color;
            graphics.fill(x, row, x + 2, row + 5, 0xFF000000 | color);
            GuiDraw.text(graphics, gui.font(), Component.translatable(type.getShortKey()), x + 4, row, 0.5F, 0xFF000000 | color, 26);
            GuiDraw.bar(graphics, x + 32, row + 1, 76, 3, Math.min(1, stored / (double) cost.getValue()), units > 0 ? 0xFF000000 | color : 0xFF804040);
            GuiDraw.right(graphics, gui.font(), lab("unit_need", cost.getValue(), units), right, row, 0.5F, units > 0 ? 0xFFB0F0C0 : 0xFFFF9080);
            row += 7;
        }
    }

    /** Page memoire : donnees des sondes (2 colonnes), donnees d'analyse, impression des plans. */
    private void memory(GuiGraphics graphics, int mouseX, int mouseY, TileEntityResearchTable tile, int accent) {
        int mh = UNIT_Y + UNIT_H - MAIN_Y;
        gui.screenAt(graphics, X0, MAIN_Y, LAB_W, mh);
        List<ProbeDestination> shown = new ArrayList<>();
        for (ProbeDestination type : ORDER) {
            if (tile.getData(type) > 0 || ResearchEffects.isDestinationUnlocked(type)) {
                shown.add(type);
            }
        }
        int x0 = X0 + 4, y0 = MAIN_Y + 4, cw = (LAB_W - 8) / 2, rowH = 11;
        long capacity = TileEntityResearchTable.CAPACITY;
        for (int i = 0; i < shown.size(); i++) {
            ProbeDestination type = shown.get(i);
            int cx = x0 + (i % 2) * cw, cy = y0 + (i / 2) * rowH;
            boolean hover = gui.over(mouseX, mouseY, cx, cy, cw - 2, rowH - 1);
            long stored = tile.getData(type);
            int color = type.color;
            graphics.fill(cx, cy, cx + cw - 2, cy + rowH - 1, hover ? 0xFF1A2434 : 0xFF0D121B);
            graphics.fill(cx, cy, cx + 2, cy + rowH - 1, 0xFF000000 | color);
            Component value = DataUnits.format(stored);
            float vw = gui.font().width(value) * 0.55F;
            GuiDraw.text(graphics, gui.font(), Component.translatable(type.getDataKey()), cx + 5, cy + 2, 0.55F, TEXT, cw - 14 - vw);
            GuiDraw.right(graphics, gui.font(), value, cx + cw - 5, cy + 2, 0.55F, stored >= capacity ? 0xFFFFE080 : SCREEN_TEXT);
            GuiDraw.bar(graphics, cx + 5, cy + 7, cw - 10, 3, Math.min(1, stored / (double) capacity), 0xFF000000 | color);
            if (hover) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable(type.getDataKey()).withStyle(style -> style.withColor(type.color)));
                lines.add(Component.translatable("data.pioneers.gauge", DataUnits.format(stored), DataUnits.format(capacity)));
                int count = (int) gui.value(UiValues.item("probes", type.ordinal()));
                lines.add(lab("probe_count", count, type.minTier.level).withStyle(ChatFormatting.GRAY));
                if (count > 0) {
                    double rate = type.averageRate(count) * ResearchEffects.probeDataMultiplier();
                    lines.add(lab("rate", String.format(Locale.ROOT, "%.1f", rate)).withStyle(ChatFormatting.DARK_AQUA));
                }
                gui.tooltip(lines);
            }
        }
        if (shown.isEmpty()) {
            GuiDraw.text(graphics, gui.font(), lab("no_data"), x0, y0 + 2, 0.6F, DIM, LAB_W - 10);
        }
        //Donnees d'analyse et de biomes recues (quatre au plus)
        List<long[]> analysis = new ArrayList<>();
        for (BasicDataTank tank : tile.getAnalysisTanks()) {
            analysis.add(new long[]{DataMachineTile.typeId(tank.isEmpty() ? null : tank.getType()), tank.getStored(), TileEntityResearchTable.CAPACITY});
        }
        dataRow(graphics, mouseX, mouseY, X0 + 2, MAIN_Y + mh - 18, LAB_W - 4, 16, analysis);
        //Impression d'un plan (donnees orbitales)
        List<ResearchNode> printable = TileEntityResearchTable.printable(ClientResearch::isCompleted);
        ResearchNode target = printable.isEmpty() ? null : printable.get(Math.floorMod((int) gui.value(UiValues.PRINT), printable.size()));
        int pw = ROW_W - 66;
        gui.screenAt(graphics, X0, LAB_ROW_Y, pw, 16);
        ItemStack blueprint = target == null ? null : target.getBlueprint();
        GuiDraw.text(graphics, gui.font(), blueprint == null ? lab("print_none") : blueprint.getHoverName(), X0 + 5, LAB_ROW_Y + 5, 0.6F,
              blueprint == null ? DIM : TEXT, pw - 10);
        gui.steelButton(graphics, mouseX, mouseY, X0 + pw + 2, LAB_ROW_Y, 14, 16, Component.literal(">"), true,
              () -> gui.send(TileEntityResearchTable.ACTION_NEXT_PRINT));
        if (gui.over(mouseX, mouseY, X0 + pw + 2, LAB_ROW_Y, 14, 16)) {
            gui.tooltip(List.of(lab("print_next")));
        }
        boolean canPrint = target != null && tile.getData(ProbeDestination.EARTH_ORBIT) >= TileEntityResearchTable.PRINT_COST;
        gui.steelButton(graphics, mouseX, mouseY, X0 + pw + 18, LAB_ROW_Y, 48, 16, lab("print"), canPrint, () -> gui.send(TileEntityResearchTable.ACTION_PRINT));
        if (gui.over(mouseX, mouseY, X0 + pw + 18, LAB_ROW_Y, 48, 16)) {
            gui.tooltip(List.of(lab("print_cost", DataUnits.format(TileEntityResearchTable.PRINT_COST))));
        }
    }

    // ------------------------------------------------------------------
    // Antenne : destination, sondes, debit, cable branche, en ecoute ; reservoir lu sur le bloc (client)
    // ------------------------------------------------------------------

    private void antenna(GuiGraphics graphics, int mouseX, int mouseY, TileEntityDataAntenna tile) {
        gui.energy(graphics, mouseX, mouseY, 8, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.ENERGY_H);
        BasicDataTank buffer = tile.getBuffer();
        Rect b = TileEntityDataAntenna.BUFFER;
        int gx = b.x(), gy = b.y(), gw = b.w(), gh = b.h();
        gui.frame(graphics, gx, gy, gw, gh);
        int filled = (int) Math.round((gh - 2) * Math.min(1, buffer.getStored() / (double) Math.max(1, buffer.getCapacity())));
        if (filled > 0 && !buffer.isEmpty()) {
            graphics.fill(gx + 1, gy + gh - 1 - filled, gx + gw - 1, gy + gh - 1, 0xFF000000 | buffer.getType().getColor());
            graphics.fill(gx + 1, gy + gh - 1 - filled, gx + gw - 1, gy + gh - filled, 0x80FFFFFF);
        }
        GuiMachine.ticks(graphics, gx, gy, gw, gh);
        if (gui.over(mouseX, mouseY, gx, gy, gw, gh)) {
            gui.tooltip(List.of(buffer.isEmpty() ? Component.translatable("data.pioneers.tank.empty") : buffer.getType().getName(),
                  Component.translatable("data.pioneers.gauge", DataUnits.format(buffer.getStored()), DataUnits.format(buffer.getCapacity()))));
        }
        Rect c = TileEntityDataAntenna.CARD;
        gui.screenAt(graphics, c.x(), c.y(), c.w(), c.h());
        ProbeDestination destination = ProbeDestination.byIndex((int) gui.value(UiValues.DESTINATION));
        int probes = (int) gui.value(UiValues.PROBES);
        long rate = gui.value(UiValues.DATA_RATE);
        List<Component> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        lines.add(PiLang.AUTO_PAD_PROBES.translate(probes));
        colors.add(AQUA);
        lines.add(PiLang.ANTENNA_EXPECTED.translate(String.format(Locale.ROOT, "%.1f", destination.averageRate(probes))));
        colors.add(DIM);
        lines.add(PiLang.ANTENNA_RATE.translate(rate));
        colors.add(rate > 0 ? GREEN : DIM);
        lines.add(PiLang.ANTENNA_LOSS.translate(GuiMachine.percent(destination.dataFailure).getString()));
        colors.add(DIM);
        for (int i = 0; i < lines.size() && i < 7; i++) {
            GuiDraw.text(graphics, gui.font(), lines.get(i), c.x() + 5, c.y() + 5 + i * 8, 0.6F, colors.get(i), c.w() - 10);
        }
        //Ecran d'etat : ecoute, cable, donnees melangees
        boolean listening = gui.value(UiValues.LISTENING) != 0, linked = gui.value(UiValues.LINKED) != 0;
        Component state = tile.hasMismatchedData() ? PiLang.ANTENNA_MIXED.translate() : !listening ? PiLang.ANTENNA_IDLE.translate()
              : !linked ? PiLang.ANTENNA_NO_CABLE.translate() : Component.translatable("gui.pioneers.api.antenna.listening");
        int chip = tile.hasMismatchedData() || !listening ? 0xFF6A5A : !linked ? 0xFFAA00 : 0x55FF88;
        Rect s = StandardLayout.shortScreen(StandardLayout.WIDTH);
        gui.info(graphics, s.x(), s.y(), s.w(), s.h(), chip, state, GuiMachine.literal(rate + " Mo/t"), -1, 0, List.of(), false);
        Rect r = StandardLayout.settingRow(StandardLayout.WIDTH);
        gui.steelButton(graphics, mouseX, mouseY, r.x(), r.y(), 14, r.h(), Component.literal("<"), true, () -> gui.send(TileEntityDataAntenna.PREVIOUS));
        gui.steelButton(graphics, mouseX, mouseY, r.x() + r.w() - 14, r.y(), 14, r.h(), Component.literal(">"), true,
              () -> gui.send(TileEntityDataAntenna.NEXT));
        gui.screenAt(graphics, r.x() + 16, r.y(), r.w() - 32, r.h());
        GuiDraw.center(graphics, gui.font(), Component.translatable(destination.getTranslationKey()), r.x() + r.w() / 2F, r.y() + 4, 0.75F,
              0xFF000000 | destination.color, r.w() - 36);
    }

    /** Ce qu'il manque pour debloquer la recherche : le premier groupe de prerequis non satisfait. */
    private static Component lockedHint(ResearchNode node) {
        for (var group : node.getPrerequisiteGroups()) {
            if (group.stream().noneMatch(ClientResearch::isCompleted)) {
                return Component.translatable(group.get(0).getTranslationKey());
            }
        }
        return Component.empty();
    }

    // ------------------------------------------------------------------
    // Elements communs
    // ------------------------------------------------------------------

    /**
     * Rangee de reservoirs de donnees (type, contenu, capacite) : cellules sombres, liseret a la couleur du type, nom (raccourci si la
     * cellule est etroite), barre ; infobulle complete au survol.
     */
    void dataRow(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, List<long[]> tanks) {
        if (tanks.isEmpty()) {
            return;
        }
        gui.frame(graphics, x, y, w, h);
        int cw = (w - 2) / tanks.size();
        for (int i = 0; i < tanks.size(); i++) {
            long[] t = tanks.get(i);
            DataType type = DataMachineTile.typeById(t[0]);
            int cx = x + 1 + i * cw, cy = y + 1;
            boolean hover = gui.over(mouseX, mouseY, cx, cy, cw - 1, h - 2);
            int color = type == null ? 0x404850 : type.getColor();
            graphics.fill(cx, cy, cx + cw - 1, cy + h - 2, hover ? 0xFF1A2434 : 0xFF0D121B);
            graphics.fill(cx, cy, cx + 2, cy + h - 2, 0xFF000000 | color);
            Component name = type == null ? Component.translatable("data.pioneers.tank.empty") : type.getName();
            String full = name.getString();
            int dot = full.indexOf(" · ");
            if (dot >= 0 && gui.font().width(full) * 0.55F > cw - 8) {
                String rest = full.substring(dot + 3);
                name = Component.literal(rest.isEmpty() ? rest : Character.toUpperCase(rest.charAt(0)) + rest.substring(1));
            }
            GuiDraw.text(graphics, gui.font(), name, cx + 5, cy + 3, 0.55F, type == null ? 0xFF6A7480 : TEXT, cw - 9);
            GuiDraw.bar(graphics, cx + 5, cy + h - 8, cw - 10, 3, t[1] / (double) Math.max(1, t[2]), 0xFF000000 | color);
            if (hover) {
                List<Component> lines = new ArrayList<>();
                if (type == null) {
                    lines.add(Component.translatable("data.pioneers.tank.empty").withStyle(ChatFormatting.GRAY));
                } else {
                    lines.add(type.getName().copy().withStyle(style -> style.withColor(type.getColor())));
                    AnalysisData.Target target = AnalysisData.targetOf(type);
                    if (target != null) {
                        lines.add(Component.translatable("data.pioneers.analysis.feeds", Component.translatable(target.node().getTranslationKey()))
                              .withStyle(ChatFormatting.DARK_AQUA));
                    }
                }
                lines.add(Component.translatable("data.pioneers.gauge", DataUnits.format(t[1]), DataUnits.format(t[2])));
                gui.tooltip(lines);
            }
        }
    }
}

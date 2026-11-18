package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.multiblock.StructureError;
import fr.pioneers.api.multiblock.StructureMatcher;
import fr.pioneers.client.gui.core.GuiMachine.Row;
import fr.pioneers.core.Texts;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.logistics.naval.HarborTile;
import fr.pioneers.logistics.rail.block.ItemLoaderTile;
import fr.pioneers.logistics.rail.block.PostKind;
import fr.pioneers.logistics.rail.block.RailPostTile;
import fr.pioneers.logistics.rail.block.SubstationTile;
import fr.pioneers.logistics.rail.block.WaterCraneTile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.2 : vues des postes du quai et de la sous-station, dans la grammaire de l'interface standard : jauges a gauche, ecran d'etat
 * (vehicule servi, quantite passee), bouton charger / decharger sous l'ecran quand le poste est reversible, cases a droite (postes a objets).
 * Valeurs des postes : 4 mode, 5 vehicule devant, 6 quantite passee, 7 sorte ; voir {@link RailPostTile} et {@link SubstationTile}.
 * <p>
 * API Machine (lot 4) : postes, sous-station et grue a eau a l'interface standard v2 ({@link #VIEW}, vue {@code rail} de la fiche) :
 * jauges hautes a gauche, carte du poste et bouton du sens dans la colonne, 9 cases des postes a objets a droite, ecran d'etat en bas,
 * valeurs NOMMEES ({@link RailPostTile#SERVING}...) ; reste ici la vue d'avant du port (multibloc, lot 6).
 */
final class RailViews {

    private final GuiMachine gui;
    private final MachineViews views;

    RailViews(GuiMachine gui, MachineViews views) {
        this.gui = gui;
        this.views = views;
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.pioneers." + key, args);
    }

    // ------------------------------------------------------------------
    // Lot 4 : postes du quai, sous-station, grue a eau a l'interface standard v2 (vue « rail » de la fiche)
    // ------------------------------------------------------------------

    static final MachineScreens.View VIEW = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
            RailViews rail = new RailViews(gui, gui.views());
            ItemLoaderTile loader = StorageViews.as(ItemLoaderTile.class, tile);
            RailPostTile post = StorageViews.as(RailPostTile.class, tile);
            if (loader != null) {
                rail.items(g, mouseX, mouseY, loader.kind());
            } else if (post != null) {
                rail.post(g, mouseX, mouseY, post);
            } else if (StorageViews.as(SubstationTile.class, tile) != null) {
                rail.substation(g, mouseX, mouseY);
            } else {
                WaterCraneTile crane = StorageViews.as(WaterCraneTile.class, tile);
                if (crane != null) {
                    rail.crane(g, mouseX, mouseY, crane);
                }
            }
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
            return true;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Nullable
        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity tile) {
            List<StandardView.InfoRow> rows = new ArrayList<>();
            ItemLoaderTile loader = StorageViews.as(ItemLoaderTile.class, tile);
            RailPostTile post = StorageViews.as(RailPostTile.class, tile);
            PostKind kind = loader != null ? loader.kind() : post != null ? post.kind() : null;
            if (kind != null) {
                rows.add(new StandardView.InfoRow("serves", tr("rail_post.serves." + kind.lower()), PiMenuScreen.TEXT));
                rows.add(new StandardView.InfoRow("reversible", tr(kind.reversible() ? "info.yes" : "info.no"), PiMenuScreen.TEXT));
            }
            if (gui.value(UiValues.CAPACITY) > 0) {
                rows.add(new StandardView.InfoRow("reserve", GuiMachine.literal(Texts.energy(gui.value(UiValues.CAPACITY))), PiMenuScreen.TEXT));
            }
            return rows;
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_rail";
        }
    };

    private Row car() {
        boolean serving = gui.value(RailPostTile.SERVING) != 0;
        return new Row(tr("loader.car"), tr(serving ? "loader.car_here" : "loader.car_none"), serving ? PiMenuScreen.SCREEN_TEXT : PiMenuScreen.DIM);
    }

    /** Titre de l'ecran : sens du poste. */
    private Component heading(PostKind kind) {
        if (kind.unloadOnly() || kind.reversible() && gui.value(RailPostTile.UNLOAD) != 0) {
            return tr("loader.unload");
        }
        return tr(kind == PostKind.FUEL || kind == PostKind.STEAM || kind == PostKind.CHARGER || kind == PostKind.COALING || kind == PostKind.NUCLEAR
              ? "rail_post.refuel" : "loader.load");
    }

    /** Carte du poste (ce qu'il sert, mesure propre) et bouton du sens dessous s'il est reversible. */
    private void column(GuiGraphics g, int mx, int my, PostKind kind, int gauges, boolean slots, List<Row> card) {
        Rect c = StandardLayout.postCard(gauges, slots, kind.reversible());
        card.add(0, new Row(tr("rail_post.serves"), tr("rail_post.serves." + kind.lower()), PiMenuScreen.TEXT));
        SpaceViews.card(gui, g, c, heading(kind), card);
        if (kind.reversible()) {
            Rect b = StandardLayout.postButton(gauges, slots);
            boolean unload = gui.value(RailPostTile.UNLOAD) != 0;
            gui.steelButton(g, mx, my, b.x(), b.y(), b.w(), b.h(), tr(unload ? "loader.to_load" : "loader.to_unload"), true,
                  () -> gui.send(RailPostTile.MODE));
        }
    }

    private void screen(GuiGraphics g, int chip, Component name, double ratio, int bar, List<Row> rows) {
        Rect s = StandardLayout.screen(StandardLayout.WIDTH);
        gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, name, Component.empty(), ratio, bar, rows, false);
    }

    private void items(GuiGraphics g, int mx, int my, PostKind kind) {
        column(g, mx, my, kind, 0, true, new ArrayList<>());
        List<Row> rows = new ArrayList<>();
        rows.add(car());
        rows.add(Row.of("gui.pioneers.loader.moved", GuiMachine.literal(Texts.format(gui.value(RailPostTile.MOVED)))));
        screen(g, gui.value(RailPostTile.SERVING) != 0 ? 0xB07A3E : 0, heading(kind), -1, 0, rows);
    }

    private void post(GuiGraphics g, int mx, int my, RailPostTile tile) {
        PostKind kind = tile.kind();
        String unit = "mB";
        double ratio = -1;
        List<Row> card = new ArrayList<>();
        switch (kind) {
            case FLUID, FUEL -> {
                Rect f = StandardLayout.postGauge(0), q = StandardLayout.postGauge(1);
                if (!tile.getFluidTanks().isEmpty()) {
                    views.fluidGauge(g, mx, my, f.x(), f.y(), f.w(), f.h(), tile.getFluidTanks().get(0));
                }
                if (!tile.getGasTanks().isEmpty()) {
                    views.gasGauge(g, mx, my, q.x(), q.y(), q.w(), q.h(), tile.getGasTanks().get(0), tr("rail_post.gas"));
                }
            }
            case STEAM -> {
                Rect q = StandardLayout.postGauge(0);
                if (!tile.getGasTanks().isEmpty()) {
                    views.gasGauge(g, mx, my, q.x(), q.y(), q.w(), q.h(), tile.getGasTanks().get(0), tr("rail_post.steam"));
                }
            }
            case ENERGY, CHARGER -> {
                Rect q = StandardLayout.postGauge(0);
                gui.energy(g, mx, my, q.x(), q.y(), q.w(), q.h());
                unit = "FE";
            }
            case DATA -> {
                unit = "Mo";
                long stored = gui.value(RailPostTile.DATA_STORED), cap = gui.value(RailPostTile.DATA_CAPACITY);
                ratio = cap <= 0 ? 0 : stored / (double) cap;
                card.add(new Row(tr("rail_post.stored"), GuiMachine.literal(Texts.format(stored) + " / " + Texts.format(cap) + " Mo"), PiMenuScreen.TEXT));
            }
            case AIR -> {
                unit = "L";
                long air = gui.value(RailPostTile.AIR_X10), volume = gui.value(RailPostTile.VOLUME);
                ratio = volume <= 0 ? 0 : air / 10.0 / (volume * 10.0);
                card.add(new Row(tr("rail_post.pressure"), GuiMachine.literal(String.format(Locale.ROOT, "%.1f bar", volume <= 0 ? 0 : air / 10.0 / volume)),
                      PiMenuScreen.TEXT));
            }
            default -> unit = "";
        }
        column(g, mx, my, kind, RailPostTile.gauges(kind), false, card);
        List<Row> rows = new ArrayList<>();
        rows.add(car());
        rows.add(Row.of("gui.pioneers.loader.moved", GuiMachine.literal(Texts.format(gui.value(RailPostTile.MOVED)) + (unit.isEmpty() ? "" : " " + unit))));
        screen(g, gui.value(RailPostTile.SERVING) != 0 ? 0x3F76E4 : 0, heading(kind), ratio, 0x30C8A0, rows);
    }

    private void crane(GuiGraphics g, int mx, int my, WaterCraneTile tile) {
        Rect q = StandardLayout.postGauge(0);
        if (!tile.getFluidTanks().isEmpty()) {
            views.fluidGauge(g, mx, my, q.x(), q.y(), q.w(), q.h(), tile.getFluidTanks().get(0));
        }
        boolean serving = gui.value(WaterCraneTile.SERVING) != 0;
        List<Row> card = new ArrayList<>();
        card.add(Row.of("gui.pioneers.crane.poured", GuiMachine.literal(Texts.format(gui.value(WaterCraneTile.POURED)) + " mB")));
        SpaceViews.card(gui, g, StandardLayout.postCard(1, false, false), tr("crane.title"), card);
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(tr("loader.car"), tr(serving ? "crane.filling" : "loader.car_none"), serving ? PiMenuScreen.SCREEN_TEXT : PiMenuScreen.DIM));
        rows.add(Row.of("gui.pioneers.crane.poured", GuiMachine.literal(Texts.format(gui.value(WaterCraneTile.POURED)) + " mB")));
        screen(g, serving ? 0x3F76E4 : 0, tr("crane.title"), -1, 0, rows);
    }

    // ------------------------------------------------------------------
    // Lot 6 : le port (capitainerie, multibloc a plan fixe HarborLayout) a l'interface standard v2 (vue « harbor » de la fiche)
    // ------------------------------------------------------------------

    private static final String[] BERTHS = {"ok", "no_water", "too_small", "unknown", "short_quay", "not_u"};

    /** Case fautive du plan relative a la capitainerie (droite, haut, profondeur vers le large), ou null. */
    @Nullable
    private static Component missingWhere(GuiMachine gui, BlockEntity tile) {
        if (gui.value(HarborTile.MISSING) == 0) {
            return null;
        }
        BlockPos at = new BlockPos((int) gui.value(HarborTile.MISSING_X), (int) gui.value(HarborTile.MISSING_Y), (int) gui.value(HarborTile.MISSING_Z));
        Direction front = tile.getBlockState().hasProperty(MachineBlock.FACING) ? tile.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        return StructureError.where(StructureMatcher.relative(tile.getBlockPos(), front, at));
    }

    /**
     * Port : fluide, gaz et energie a gauche ; carte (sens, navire a quai) avec les boutons « charger / decharger » et « Fiche » dessous ; 9
     * cases a droite ; ecran (poste d'amarrage et plus grand navire accepte, quai ou case a corriger avec le « ou » commun, donnees et air,
     * quantite passee). Valeurs NOMMEES ({@link HarborTile#BERTH}...).
     */
    static final MachineScreens.View HARBOR = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mx, int my, BlockEntity be) {
            HarborTile tile = StorageViews.as(HarborTile.class, be);
            if (tile == null) {
                return;
            }
            MachineViews views = gui.views();
            Rect f = StandardLayout.postGauge(0), q = StandardLayout.postGauge(1), e = StandardLayout.postGauge(2);
            if (!tile.getFluidTanks().isEmpty()) {
                views.fluidGauge(g, mx, my, f.x(), f.y(), f.w(), f.h(), tile.getFluidTanks().get(0));
            }
            if (!tile.getGasTanks().isEmpty()) {
                views.gasGauge(g, mx, my, q.x(), q.y(), q.w(), q.h(), tile.getGasTanks().get(0), tr("rail_post.gas"));
            }
            gui.energy(g, mx, my, e.x(), e.y(), e.w(), e.h());
            boolean ship = gui.value(HarborTile.SERVING) != 0, unload = gui.value(HarborTile.UNLOAD) != 0;
            int berth = (int) gui.value(HarborTile.BERTH), max = (int) gui.value(HarborTile.MAX_TIER);
            List<Row> card = new ArrayList<>();
            card.add(new Row(tr("harbor.ship"), tr(ship ? "harbor.ship_here" : "harbor.ship_none"), ship ? PiMenuScreen.SCREEN_TEXT : PiMenuScreen.DIM));
            SpaceViews.card(gui, g, StandardLayout.postCard(HarborTile.GAUGES, true, true), tr(unload ? "loader.unload" : "loader.load"), card);
            Rect b = StandardLayout.postButton(HarborTile.GAUGES, true);
            int half = b.w() / 2;
            gui.steelButton(g, mx, my, b.x(), b.y(), half - 1, b.h(), tr(unload ? "harbor.to_load" : "harbor.to_unload"), true,
                  () -> gui.send(HarborTile.MODE));
            gui.steelButton(g, mx, my, b.x() + half + 1, b.y(), b.w() - half - 1, b.h(), tr("harbor.sheet"), true, () -> gui.send(HarborTile.SHEET));
            List<Row> rows = new ArrayList<>();
            Component state = tr("harbor.berth." + BERTHS[Math.max(0, Math.min(5, berth))]);
            if (berth == 0 && max >= 0) {
                state = GuiMachine.literal(state.getString() + " · " + new String[]{"MKI", "MKII", "MKIII", "MKIV"}[Math.min(3, max)]);
            }
            Component where = berth != 0 ? missingWhere(gui, be) : null;
            if (where != null) {
                //0.20.23 : premiere case fautive du plan ; lot 6 : « ou » commun (relatif a la capitainerie)
                rows.add(new Row(Component.translatable("gui.pioneers.multiblock.where"), where, 0xFFFFB050));
            } else {
                rows.add(new Row(tr("harbor.quay"), Component.translatable("gui.pioneers.harbor.quay_value", gui.value(HarborTile.BASIN), gui.value(HarborTile.QUAY),
                      Long.bitCount(gui.value(HarborTile.EQUIPMENT))), PiMenuScreen.TEXT));
            }
            long volume = gui.value(HarborTile.VOLUME);
            rows.add(new Row(tr("rail_post.stored"), GuiMachine.literal(Texts.format(gui.value(HarborTile.DATA_STORED)) + " Mo · "
                  + String.format(Locale.ROOT, "%.1f bar", volume <= 0 ? 0 : gui.value(HarborTile.AIR_X10) / 10.0 / volume)), PiMenuScreen.TEXT));
            rows.add(Row.of("gui.pioneers.loader.moved", GuiMachine.literal(Texts.format(gui.value(HarborTile.MOVED)))));
            Rect s = StandardLayout.screen(StandardLayout.WIDTH);
            gui.info(g, s.x(), s.y(), s.w(), s.h(), berth == 0 ? (ship ? 0x3F9EC8 : 0) : 0xE04848, state, Component.empty(), -1, 0, rows, false);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
            return true;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Override
        public Component titleNote(GuiMachine gui, BlockEntity be) {
            return gui.value(HarborTile.BERTH) == 0 ? null : Component.translatable("gui.pioneers.multiblock.not_formed").withStyle(ChatFormatting.RED);
        }

        @Nullable
        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity tile) {
            List<StandardView.InfoRow> rows = new ArrayList<>();
            rows.add(new StandardView.InfoRow("reserve", GuiMachine.literal(Texts.energy(gui.value(UiValues.CAPACITY))), PiMenuScreen.TEXT));
            return rows;
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_rail";
        }

        @Override
        public boolean structureTab(BlockEntity be) {
            return StorageViews.as(HarborTile.class, be) != null;
        }

        @Override
        public void structure(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            int berth = (int) gui.value(HarborTile.BERTH);
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{tr("harbor.berth"), tr("harbor.berth." + BERTHS[Math.max(0, Math.min(5, berth))]), berth == 0 ? MultiblockViews.OK : MultiblockViews.WARN});
            rows.add(new Object[]{tr("harbor.quay"), Component.translatable("gui.pioneers.harbor.quay_value", gui.value(HarborTile.BASIN), gui.value(HarborTile.QUAY),
                  Long.bitCount(gui.value(HarborTile.EQUIPMENT))), PiMenuScreen.TEXT});
            Component where = berth != 0 ? missingWhere(gui, be) : null;
            MultiblockViews.housePanel(gui, g, mouseX, mouseY, be, StandardLayout.WIDTH, tr("harbor.berth"), rows,
                  where == null ? null : Component.translatable("gui.pioneers.multiblock.first_fault").append(" : ").append(where));
        }
    };

    private void substation(GuiGraphics g, int mx, int my) {
        Rect q = StandardLayout.postGauge(0);
        gui.energy(g, mx, my, q.x(), q.y(), q.w(), q.h());
        long stored = gui.value(SubstationTile.GRID_STORED), cap = Math.max(1, gui.value(SubstationTile.GRID_CAPACITY)), draw = gui.value(SubstationTile.DRAW);
        List<Row> card = new ArrayList<>();
        card.add(Row.of("gui.pioneers.rail_post.grid_store", GuiMachine.literal(Texts.energy(stored) + " / " + Texts.energy(cap))));
        card.add(new Row(tr("rail_post.grid_hint"), Component.empty(), PiMenuScreen.DIM));
        SpaceViews.card(gui, g, StandardLayout.postCard(1, false, false), tr("rail_post.substation"), card);
        List<Row> rows = new ArrayList<>();
        rows.add(Row.of("gui.pioneers.rail_post.grid_store", GuiMachine.literal(Texts.energy(stored) + " / " + Texts.energy(cap))));
        rows.add(new Row(tr("rail_post.trains_draw"), GuiMachine.literal(GuiMachine.compact(draw) + " FE/t"), draw > 0 ? PiMenuScreen.SCREEN_TEXT : PiMenuScreen.DIM));
        screen(g, 0xD8B030, tr("rail_post.substation"), stored / (double) cap, 0xD8B030, rows);
    }
}

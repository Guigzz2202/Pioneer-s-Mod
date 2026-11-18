package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.MachineSpec;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.core.GuiMachine.Row;
import fr.pioneers.client.render.ChunkLoaderPreview;
import fr.pioneers.core.Texts;
import fr.pioneers.space.tile.TileEntityChunkLoader;
import fr.pioneers.space.tile.TileEntityForceField;
import fr.pioneers.space.tile.TileEntityLightningCollector;
import fr.pioneers.space.tile.TileEntityOxygenDiffuser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 4) : VUE DE L'INTERFACE STANDARD v2 des petites machines spatiales a tiers (fiche {@code space}) : chunkloader, champ de
 * force, collecteur de foudre, diffuseur d'oxygene. Meme grammaire que les ateliers : reserve d'energie a gauche et batterie dessous (place
 * commune), colonne des reglages ou des mesures a droite ({@link StandardLayout#panel}, {@link StandardLayout#setting}), ecran d'etat pleine
 * largeur en bas (etat, valeur cle, barre, 2 lignes), onglet Infos pour le reste. Valeurs lues par leur NOM (cles des tiles), machine
 * choisie par la cle de sa fiche (plus d'{@code instanceof}). Remplace les vues d'avant de {@code MachineViews} (zone de 104 a 118).
 */
final class SpaceViews {

    private static final int W = StandardLayout.WIDTH, PX = StandardLayout.PANEL_X, PW = W - 8 - PX;
    private static final int A = StandardLayout.STEP_A, B = StandardLayout.STEP_B, STEP_H = StandardLayout.STEP_H;

    private SpaceViews() {
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.pioneers." + key, args);
    }

    @Nullable
    private static String key(BlockEntity be) {
        MachineSpec spec = MachineScreens.spec(be);
        return spec == null ? null : spec.key();
    }

    static final MachineScreens.View VIEW = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            String key = key(be);
            if (key == null) {
                return;
            }
            gui.energy(g, mouseX, mouseY, 8, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.ENERGY_H);
            switch (key) {
                case "chunk_loader" -> chunkLoader(gui, g, mouseX, mouseY, StorageViews.as(TileEntityChunkLoader.class, be));
                case "force_field_generator" -> forceField(gui, g, mouseX, mouseY, StorageViews.as(TileEntityForceField.class, be));
                case "lightning_collector" -> lightning(gui, g, mouseX, mouseY, StorageViews.as(TileEntityLightningCollector.class, be));
                case "oxygen_diffuser" -> diffuser(gui, g, mouseX, mouseY, StorageViews.as(TileEntityOxygenDiffuser.class, be));
                default -> {
                }
            }
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            gui.slotTip(mouseX, mouseY, StandardLayout.BATTERY_X, StandardLayout.BATTERY_Y,
                  "lightning_collector".equals(key(be)) ? "gui.pioneers.machine.charge" : "gui.pioneers.machine.battery");
            return true;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Nullable
        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity be) {
            List<StandardView.InfoRow> rows = new ArrayList<>();
            String key = key(be);
            if ("force_field_generator".equals(key)) {
                rows.add(new StandardView.InfoRow("impacts", GuiMachine.literal(Texts.format(gui.value(TileEntityForceField.IMPACTS))), PiMenuScreen.TEXT));
            } else if ("lightning_collector".equals(key)) {
                rows.add(new StandardView.InfoRow("strikes", GuiMachine.literal(Texts.format(gui.value(TileEntityLightningCollector.STRIKES))), PiMenuScreen.TEXT));
            } else if ("oxygen_diffuser".equals(key)) {
                rows.add(new StandardView.InfoRow("volume", tr("info.blocks", Texts.format(gui.value(TileEntityOxygenDiffuser.VOLUME))), PiMenuScreen.TEXT));
            }
            if (!"lightning_collector".equals(key)) {
                rows.add(new StandardView.InfoRow("consumption", GuiMachine.literal(GuiMachine.compact(gui.value(UiValues.USAGE) / 100.0) + " FE/t"),
                      0xFFFFFF55));
            }
            rows.add(new StandardView.InfoRow("reserve", GuiMachine.literal(Texts.energy(gui.value(UiValues.CAPACITY))), PiMenuScreen.TEXT));
            return rows;
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_space";
        }
    };

    /** Legende d'un reglage (au-dessus de lui). */
    private static void caption(GuiMachine gui, GuiGraphics g, Component text, int stepY) {
        GuiDraw.text(g, gui.font(), text, PX + 1, stepY - 7, 0.6F, PiMenuScreen.DIM, PW);
    }

    /** Bouton a bascule de la colonne (libelle allume / eteint). */
    private static void toggle(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, int y, Component label, boolean on, Runnable action,
          List<Component> tip) {
        gui.steelButton(g, mouseX, mouseY, PX, y, PW, STEP_H, "", action);
        GuiDraw.center(g, gui.font(), label, PX + PW / 2F, y + 4, 0.7F, on ? PiMenuScreen.SCREEN_TEXT : PiMenuScreen.DIM, PW - 4);
        if (gui.over(mouseX, mouseY, PX, y, PW, STEP_H)) {
            gui.tooltip(tip);
        }
    }

    /** Carte de mesures (ecran sombre, lignes « libelle : valeur »). */
    static void card(GuiMachine gui, GuiGraphics g, Rect r, Component title, List<Row> rows) {
        gui.screenAt(g, r.x(), r.y(), r.w(), r.h());
        GuiDraw.text(g, gui.font(), title, r.x() + 5, r.y() + 4.5F, 0.6F, PiMenuScreen.DIM, r.w() - 10);
        g.fill(r.x() + 5, r.y() + 13, r.x() + r.w() - 5, r.y() + 14, 0xFF24333E);
        int y = r.y() + 18;
        for (Row row : rows) {
            if (y + 7 > r.y() + r.h() - 2) {
                break;
            }
            float vw = Math.min(gui.font().width(row.value()) * 0.7F, r.w() - 30);
            GuiDraw.text(g, gui.font(), row.label(), r.x() + 5, y + 1, 0.6F, PiMenuScreen.DIM, Math.max(1, r.w() - 12 - vw - 3));
            GuiDraw.text(g, gui.font(), row.value(), r.x() + r.w() - 5 - vw, y, 0.7F, row.color(), vw);
            y += 10;
        }
    }

    private static Rect screen() {
        return StandardLayout.screen(W);
    }

    // ------------------------------------------------------------------
    // Chunkloader : rayon (reglage), apercu (bouton) ; ecran : chunks charges, zone, cout
    // ------------------------------------------------------------------

    private static void chunkLoader(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, @Nullable TileEntityChunkLoader tile) {
        if (tile == null) {
            return;
        }
        int radius = (int) gui.value(TileEntityChunkLoader.RADIUS), loaded = (int) gui.value(TileEntityChunkLoader.LOADED);
        boolean on = loaded >= 0;
        caption(gui, g, tr("info.radius"), A);
        gui.stepper(g, mouseX, mouseY, PX, A, PW, tr("info.radius_value", radius), tr("info.radius"), TileEntityChunkLoader.DECREASE,
              TileEntityChunkLoader.INCREASE);
        ChunkLoaderPreview.update(tile.getLevel(), tile.getBlockPos(), radius);
        boolean shown = ChunkLoaderPreview.isShown(tile.getLevel(), tile.getBlockPos());
        caption(gui, g, tr("chunk_loader.preview"), B);
        toggle(gui, g, mouseX, mouseY, B, tr(shown ? "chunk_loader.preview_on" : "chunk_loader.preview_off"), shown,
              () -> ChunkLoaderPreview.toggle(tile.getLevel(), tile.getBlockPos(), radius), List.of(tr("chunk_loader.preview")));
        List<Row> rows = new ArrayList<>();
        rows.add(Row.of("gui.pioneers.info.area", tr("info.chunks_area", 2 * radius + 1, 2 * radius + 1)));
        rows.add(Row.of("gui.pioneers.info.cost", GuiMachine.literal(Texts.format(gui.value(TileEntityChunkLoader.COST)) + " FE/t")));
        Rect s = screen();
        gui.info(g, s.x(), s.y(), s.w(), s.h(), on ? 0x8E4FC8 : 0, tr(on ? "chunk_loader.loading" : "chunk_loader.idle"),
              tr("info.chunks", on ? TileEntityChunkLoader.chunkCount(loaded) : 0), radius / (double) Math.max(1, tile.maxRadius()), 0x8E4FC8, rows, false);
    }

    // ------------------------------------------------------------------
    // Champ de force : rayon (reglage), visibilite (bouton) ; ecran : etat, rayon, entretien, eruption ou effondrement
    // ------------------------------------------------------------------

    private static void forceField(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, @Nullable TileEntityForceField tile) {
        if (tile == null) {
            return;
        }
        int radius = (int) gui.value(TileEntityForceField.RADIUS);
        boolean shielding = gui.value(TileEntityForceField.SHIELDING) != 0, flare = gui.value(TileEntityForceField.FLARE) != 0,
              visible = gui.value(TileEntityForceField.VISIBLE) != 0;
        long collapse = gui.value(TileEntityForceField.COLLAPSE);
        caption(gui, g, tr("info.radius"), A);
        gui.stepper(g, mouseX, mouseY, PX, A, PW, tr("info.radius_value", radius), tr("info.radius"), TileEntityForceField.DECREASE,
              TileEntityForceField.INCREASE);
        caption(gui, g, tr("force_field.visibility"), B);
        toggle(gui, g, mouseX, mouseY, B, tr(visible ? "force_field.visible" : "force_field.hidden"), visible,
              () -> gui.send(TileEntityForceField.TOGGLE_VISIBLE),
              List.of(tr("force_field.visibility"), tr("force_field.visibility_tip").withStyle(ChatFormatting.DARK_GRAY)));
        String state = shielding ? "force_field.shielding" : collapse > 0 ? "force_field.collapsed" : "force_field.offline";
        List<Row> rows = new ArrayList<>();
        rows.add(Row.of("gui.pioneers.info.upkeep", GuiMachine.literal(Texts.format(gui.value(TileEntityForceField.UPKEEP)) + " FE/t")));
        rows.add(collapse > 0 ? new Row(tr("info.collapse"), GuiMachine.literal(String.format(Locale.ROOT, "%.0f s", collapse / 20.0)), 0xFFFF6A5A)
              : new Row(tr("info.flare"), tr(flare ? "force_field.flare_on" : "info.no"), flare ? 0xFFFFB050 : PiMenuScreen.DIM));
        Rect s = screen();
        gui.info(g, s.x(), s.y(), s.w(), s.h(), shielding ? 0x3FB6C4 : 0, tr(state), tr("info.radius_value", radius),
              radius / (double) Math.max(1, tile.maxRadius()), 0x3FB6C4, rows, false);
    }

    // ------------------------------------------------------------------
    // Collecteur de foudre : carte (mat, ciel, eclairs) ; ecran : meteo, remplissage, production moyenne, dernier eclair
    // ------------------------------------------------------------------

    private static void lightning(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, @Nullable TileEntityLightningCollector tile) {
        if (tile == null) {
            return;
        }
        int tier = tile.machineTier();
        int mast = (int) gui.value(TileEntityLightningCollector.MAST), weather = (int) gui.value(TileEntityLightningCollector.WEATHER);
        boolean sky = gui.value(TileEntityLightningCollector.SKY) != 0, formed = mast > 0;
        double average = gui.value(TileEntityLightningCollector.AVERAGE) / 100.0;
        long capacity = gui.value(UiValues.CAPACITY);
        double ratio = capacity <= 0 ? 0 : gui.value(UiValues.ENERGY) / (double) capacity;
        List<Row> card = new ArrayList<>();
        card.add(new Row(tr("info.mast"), formed ? GuiMachine.literal(mast + " / " + TileEntityLightningCollector.TIER_MAST[Math.max(0, Math.min(3, tier))])
                                                  : tr("lightning.incomplete"), formed ? PiMenuScreen.SCREEN_TEXT : 0xFFFF6A5A));
        card.add(new Row(tr("info.open_sky"), tr(sky ? "info.yes" : "info.no"), sky ? PiMenuScreen.SCREEN_TEXT : 0xFFFF6A5A));
        card.add(Row.of("gui.pioneers.info.strikes", GuiMachine.literal(Texts.format(gui.value(TileEntityLightningCollector.STRIKES)))));
        card(gui, g, StandardLayout.panel(W), tr("lightning.weather." + weather), card);
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(tr("info.production"), GuiMachine.literal("~" + GuiMachine.compact(average) + " FE/t"),
              average > 0 ? GuiMachine.FLOW_UP_COLOR : PiMenuScreen.DIM));
        rows.add(Row.of("gui.pioneers.info.last_strike", GuiMachine.literal(Texts.energy(gui.value(TileEntityLightningCollector.LAST_STRIKE)))));
        Rect s = screen();
        gui.info(g, s.x(), s.y(), s.w(), s.h(), formed && sky ? 0xF0D040 : 0, tr("lightning.weather." + weather), GuiMachine.percent(ratio), ratio,
              0xE0C040, rows, false);
    }

    // ------------------------------------------------------------------
    // Diffuseur d'oxygene : jauge d'oxygene, carte de la piece ; ecran : pressurisation, oxygene / cible
    // ------------------------------------------------------------------

    private static void diffuser(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, @Nullable TileEntityOxygenDiffuser tile) {
        if (tile == null) {
            return;
        }
        long pressure = gui.value(TileEntityOxygenDiffuser.OXYGEN_MB), target = gui.value(TileEntityOxygenDiffuser.TARGET);
        boolean sealed = gui.value(TileEntityOxygenDiffuser.SEALED) != 0, pressurized = gui.value(TileEntityOxygenDiffuser.PRESSURIZED) != 0;
        double ratio = target <= 0 ? 0 : pressure / (double) target;
        Rect q = StandardLayout.DIFFUSER_GAUGE;
        gui.views().gasGauge(g, mouseX, mouseY, q.x(), q.y(), q.w(), q.h(), tile.oxygenTank, tr("info.oxygen"));
        List<Row> card = new ArrayList<>();
        card.add(new Row(tr("info.room"), sealed ? tr("info.blocks", Texts.format(gui.value(TileEntityOxygenDiffuser.VOLUME))) : tr("diffuser.open"),
              sealed ? PiMenuScreen.SCREEN_TEXT : 0xFFFF6A5A));
        card.add(Row.of("gui.pioneers.info.target_oxygen", GuiMachine.literal(Texts.format(target) + " mB")));
        card(gui, g, StandardLayout.diffuserCard(W), tr("info.oxygen"), card);
        List<Row> rows = new ArrayList<>();
        rows.add(Row.of("gui.pioneers.info.oxygen", GuiMachine.literal(Texts.format(pressure) + " / " + Texts.format(target) + " mB")));
        double using = gui.value(UiValues.USAGE) / 100.0;
        rows.add(new Row(tr("info.using"), GuiMachine.literal(GuiMachine.compact(using) + " FE/t"), using > 0 ? PiMenuScreen.SCREEN_TEXT : PiMenuScreen.DIM));
        Rect s = screen();
        gui.info(g, s.x(), s.y(), s.w(), s.h(), pressurized ? 0x8FD0F0 : 0, tr(pressurized ? "diffuser.pressurized" : sealed ? "diffuser.filling"
              : "diffuser.unsealed"), GuiMachine.percent(ratio), ratio, 0x8FD0F0, rows, false);
    }
}

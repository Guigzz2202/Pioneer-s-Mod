package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.tech.bronze.blast.BlastFurnaceTile;
import fr.pioneers.tech.bronze.steam.FireboxTile;
import fr.pioneers.tech.bronze.steam.SteamRules;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.world.geology.drill.SteamDrillTile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * API Machine (lot 8b) : CORPS en VRAIE v2 des multiblocs maison de l'age du bronze, dans la vue maison {@code house} (voile et erreur commune
 * quand la structure n'est pas formee, onglet Structure) : chaudiere ({@code firebox} : foyer a la place de l'energie motrice, niveau d'eau,
 * manometre et bouton du feu, carte des pieces, combustible et cendres, ecran d'etat), foreuse a vapeur ({@code steam_drill} : manometre,
 * carte du gisement, fleche, 9 sorties) et haut fourneau ({@code blast_furnace} : feu, trois colonnes fer / carbone / fondant (case, nom,
 * reserve ; 1.0.4-beta lot 9), fleche, acier, laitier).
 * Valeurs lues par leur NOM ({@code namedIndices} des tiles : indices d'avant) ; corps choisi par la cle de la fiche
 * ({@link MachineScreens#registerBody}). Remplace {@code SteamViews} et la branche du haut fourneau de {@code BronzeViews}.
 */
final class HouseAgeViews {

    private static final int FIRE = 0xFF7A1F, RED = 0xE04848;

    private HouseAgeViews() {
    }

    /**
     * 1.0.2-beta (lot 3, plus de pression) : JAUGE DE VAPEUR ronde de la chaudiere : cadran emaille, graduations de 0 a 100 % de la reserve,
     * arc vert au-dela de 80 % (pleine : la chaudiere ne produit plus), aiguille. Plus de zone rouge : la vapeur ne fait plus sauter la chaudiere.
     */
    static void dial(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, Rect r, double steam, double capacity) {
        double full = 1, p = capacity <= 0 ? 0 : steam / capacity;
        int x = r.x(), y = r.y(), size = Math.min(r.w(), r.h());
        gui.frame(g, x, y, size, size);
        int cx = x + size / 2, cy = y + size / 2, rad = size / 2 - 2;
        for (int dy = -rad; dy <= rad; dy++) {
            for (int dx = -rad; dx <= rad; dx++) {
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d > rad) {
                    continue;
                }
                int c = d > rad - 1.2 ? 0xFFB07A3A : 0xFFE6DFC8;
                if (d > rad - 3.2 && d <= rad - 1.2) {
                    double ang = Math.toDegrees(Math.atan2(-dy, dx));
                    double t = (225 - (ang < -90 ? ang + 360 : ang)) / 270.0;
                    if (t >= 0 && t <= 1) {
                        c = t > 0.8 ? 0xFF5AA060 : 0xFFE6DFC8;
                    }
                }
                g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, c);
            }
        }
        for (int k = 0; k <= 8; k++) {
            double a = Math.toRadians(225 - 270 * k / 8.0);
            int tx = cx + (int) Math.round(Math.cos(a) * (rad - 4)), ty = cy - (int) Math.round(Math.sin(a) * (rad - 4));
            g.fill(tx, ty, tx + 1, ty + 1, 0xFF2A2420);
        }
        double t = Math.max(0, Math.min(1.04, p / full));
        double a = Math.toRadians(225 - 270 * t);
        for (double s = 0; s <= rad - 2; s += 0.5) {
            int nx = cx + (int) Math.round(Math.cos(a) * s), ny = cy - (int) Math.round(Math.sin(a) * s);
            g.fill(nx, ny, nx + 1, ny + 1, 0xFF14100E);
        }
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF6A4A2A);
        if (gui.over(mouseX, mouseY, x, y, size, size)) {
            gui.tooltip(List.of(Component.translatable("gui.pioneers.steam.reserve", AgeViews.mb(steam), AgeViews.mb(capacity)),
                  Component.translatable("gui.pioneers.steam.boil", AgeViews.celsius(SteamRules.BOIL_C)).withStyle(net.minecraft.ChatFormatting.GRAY)));
        }
    }

    // ------------------------------------------------------------------
    // Chaudiere
    // ------------------------------------------------------------------

    static final MachineScreens.View FIREBOX = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            double c = gui.value(UiValues.HEAT) / 10.0, water = gui.value(UiValues.WATER), capacity = Math.max(1, gui.value(UiValues.WATER_MAX));
            double steam = gui.value(UiValues.STEAM), reserve = Math.max(1, gui.value(UiValues.STEAM_MAX)), fire = gui.value(UiValues.BURN) / 1000.0;
            double draft = gui.value(FireboxTile.DRAFT) / 100.0, made = gui.value(FireboxTile.MADE) / 100.0, damage = gui.value(FireboxTile.DAMAGE) / 10.0;
            double wet = gui.value(UiValues.WET) / 1000.0;
            boolean on = gui.value(FireboxTile.ENABLED) != 0, cut = gui.value(FireboxTile.ENABLED) == 2;   //1.0.3-beta (lot 7) : 2 = surete
            String problem = FireboxTile.PROBLEMS.get((int) Math.max(0, Math.min(FireboxTile.PROBLEMS.size() - 1, gui.value(FireboxTile.PROBLEM))));
            AgeViews.fireGauge(gui, g, mouseX, mouseY, StandardLayout.POWER, c, (int) SteamRules.BOIL_C,
                  Component.translatable("gui.pioneers.steam.limit", AgeViews.celsius(SteamRules.BOIL_C)), 0, 120);   //1.0.3-beta : echelle 0 a 120 C
            SourceViews.waterGauge(gui, g, mouseX, mouseY, FireboxTile.WATER, water, capacity, SteamRules.LOW_WATER, 0.92);
            dial(gui, g, mouseX, mouseY, FireboxTile.DIAL, steam, reserve);
            Rect f = FireboxTile.FIRE;
            gui.steelButton(g, mouseX, mouseY, f.x(), f.y(), f.w(), f.h(), Component.translatable(on ? "gui.pioneers.steam.fire_on" : "gui.pioneers.steam.fire_off"),
                  true, () -> gui.send(FireboxTile.TOGGLE));
            GuiMachine.flame(g, FireboxTile.FUEL_X + 1, FireboxTile.FUEL_Y + 20, fire);
            //Carte : pieces, tirage, feu restant (1.0.3-beta lot 14), humidite et usure seulement si elles comptent (4 lignes tiennent)
            Rect card = FireboxTile.CARD;
            List<GuiMachine.Row> crow = new ArrayList<>();
            long burnLeft = gui.value(FireboxTile.BURN_LEFT), bonus = gui.value(FireboxTile.BONUS);
            crow.add(GuiMachine.Row.of("gui.pioneers.steam.structure", Component.translatable("gui.pioneers.steam.parts", gui.value(FireboxTile.SHELLS),
                  gui.value(FireboxTile.DOMES), gui.value(FireboxTile.CHIMNEY))));
            crow.add(GuiMachine.Row.of("gui.pioneers.steam.draft", GuiMachine.literal(String.format(Locale.ROOT, "x%.2f", draft))));
            //1.0.3-beta (lot 14) : feu restant de l'objet en cours (et bonus du combustible), sinon le regime atteignable a ce tirage
            crow.add(fire > 0 ? GuiMachine.Row.of("gui.pioneers.steam.fire_left", Component.translatable("gui.pioneers.steam.fire_left_value", burnLeft,
                  bonus + " %")) : GuiMachine.Row.of("gui.pioneers.steam.max_rate", GuiMachine.literal(String.format(Locale.ROOT, "%.1f mB/t",
                  SteamRules.boilerNet(draft, 0, (int) Math.max(1, gui.value(FireboxTile.SHELLS)))))));
            if (wet > 0.005) {
                crow.add(new GuiMachine.Row(Component.translatable("gui.pioneers.steam.wet"), GuiMachine.percent(wet), wet > 0.1 ? 0xFFE0C040 : PiMenuScreen.SCREEN_TEXT));
            }
            if (damage > 0) {
                crow.add(new GuiMachine.Row(Component.translatable("gui.pioneers.steam.damage"), GuiMachine.literal(Math.round(damage) + " %"), 0xFFFF6A5A));
            }
            gui.info(g, card.x(), card.y(), card.w(), card.h(), 0, Component.translatable("gui.pioneers.api.info.boiler"), AgeViews.mb(steam), -1, 0, crow, false);
            //Ecran d'etat
            //1.0.3-beta (lot 14) : etat lisible : structure, surete, feu coupe au bouton, a sec, eau basse, reserve pleine, ebullition, chauffe, froide
            Component title = !problem.isEmpty() ? Component.translatable("gui.pioneers.steam.problem." + problem)
                  : cut ? Component.translatable("gui.pioneers.steam.cutoff")
                  : !on ? Component.translatable("gui.pioneers.steam.off")
                  : water < SteamRules.DRY_WATER && fire > 0 ? Component.translatable("gui.pioneers.steam.dry_alarm")
                  : water < capacity * SteamRules.LOW_WATER && fire > 0 ? Component.translatable("gui.pioneers.steam.low")
                  : steam >= reserve - 1 && fire > 0 ? Component.translatable("gui.pioneers.steam.reserve_full")
                  : fire > 0 ? Component.translatable(c >= 100 ? "gui.pioneers.steam.boiling" : "gui.pioneers.steam.heating")
                  : Component.translatable("gui.pioneers.steam.cold");
            int chip = !problem.isEmpty() || cut || damage > 0 || water < capacity * SteamRules.LOW_WATER && fire > 0 ? RED : fire > 0 ? FIRE : 0;
            Rect s = StandardLayout.screen(StandardLayout.WIDTH);
            List<GuiMachine.Row> rows = new ArrayList<>();
            rows.add(GuiMachine.Row.of("gui.pioneers.steam.made", GuiMachine.literal(String.format(Locale.ROOT, "%.1f mB/t", made))));
            rows.add(GuiMachine.Row.of("gui.pioneers.api.info.water", GuiMachine.literal(Math.round(water) + " / " + Math.round(capacity) + " mB")));
            gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, title, AgeViews.celsius(c), Math.min(1, steam / reserve), 0xC8D8E4, rows, false);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            gui.slotTip(mouseX, mouseY, FireboxTile.FUEL_X, FireboxTile.FUEL_Y, "gui.pioneers.machine.fuel");
            gui.slotTip(mouseX, mouseY, FireboxTile.ASH_X, FireboxTile.ASH_Y, "gui.pioneers.steam.ash");
            return true;
        }
    };

    // ------------------------------------------------------------------
    // Foreuse a vapeur
    // ------------------------------------------------------------------

    static final MachineScreens.View DRILL = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            SteamDrillTile tile = StorageViews.as(SteamDrillTile.class, be);
            if (tile == null) {
                return;
            }
            int status = (int) gui.value(UiValues.STATUS);
            long left = gui.value(SteamDrillTile.LEFT), reserve = gui.value(SteamDrillTile.RESERVE);
            double stored = gui.value(UiValues.STEAM), progress = gui.value(UiValues.PROGRESS_X1000) / 1000.0, ticks = gui.value(SteamDrillTile.TICKS);
            AgeViews.steamGauge(gui, g, mouseX, mouseY, StandardLayout.POWER, stored, SteamRules.DRILL_RESERVE, SteamRules.DRILL_USE);
            StandardView.arrow(g, SteamDrillTile.ARROW, progress, 0xD8A040);
            Rect c = SteamDrillTile.CARD;
            gui.screenAt(g, c.x(), c.y(), c.w(), c.h());
            GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.api.drill.deposit"), c.x() + 5, c.y() + 5, 0.55F, PiMenuScreen.DIM, c.w() - 10);
            GuiDraw.text(g, gui.font(), tile.depositName(), c.x() + 5, c.y() + 14, 0.65F, PiMenuScreen.TEXT, c.w() - 10);
            if (reserve > 0) {
                GuiDraw.bar(g, c.x() + 5, c.y() + 26, c.w() - 10, 4, left / (double) reserve, 0xFFD8A040);
                GuiDraw.text(g, gui.font(), GuiMachine.literal(left + " / " + reserve), c.x() + 5, c.y() + 33, 0.55F, PiMenuScreen.SCREEN_TEXT, c.w() - 10);
            }
            int chip = status == 0 ? 0x6FD08A : status == 3 || status == 4 || status == 5 || status == 1 ? RED : 0xE0A030;
            Rect s = StandardLayout.screen(StandardLayout.WIDTH);
            List<GuiMachine.Row> rows = new ArrayList<>();
            rows.add(GuiMachine.Row.of("geology.pioneers.gui.rate", GuiMachine.literal(String.format(Locale.ROOT, "%.1f s", ticks / 20.0))));
            rows.add(GuiMachine.Row.of("gui.pioneers.steam.pressure_short", AgeViews.mb(stored)));
            gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, Component.translatable("geology.pioneers.status." + status), GuiMachine.percent(progress),
                  status == 0 ? progress : -1, 0xD8A040, rows, false);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            return true;
        }
    };

    // ------------------------------------------------------------------
    // Haut fourneau
    // ------------------------------------------------------------------

    /** Reserve d'un role (sous sa case) : cadre, remplissage, graduations, infobulle « Fer : n / max ». */
    private static void charge(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, Rect r, long n, int max, int color, String key) {
        int x = r.x(), y = r.y(), w = r.w(), h = r.h();
        gui.frame(g, x, y, w, h);
        int filled = (int) Math.round((h - 2) * Math.min(1, n / (double) max));
        if (filled > 0) {
            g.fill(x + 1, y + h - 1 - filled, x + w - 1, y + h - 1, color);
            g.fill(x + 1, y + h - 1 - filled, x + 3, y + h - 1, GuiMachine.lerp(color, 0xFFFFFFFF, 0.25));
        }
        GuiMachine.ticks(g, x, y, w, h);
        if (gui.over(mouseX, mouseY, x, y, w, h)) {
            gui.tooltip(List.of(Component.translatable(key, n, max)));
        }
    }

    /** 1.0.4-beta (lot 9) : couleurs des trois roles (fer, carbone, fondant) : reserve et nom court. */
    static final int[] ROLE_FILL = {0xFF8A5040, 0xFF303034, 0xFFE6E2D6}, ROLE_TEXT = {0xFFD8A080, 0xFFB8B8C0, 0xFFE6E2D6};
    /** Nom court sous la case : echelle et largeur maximale (colonnes espacees de 26 px). */
    static final float LABEL_SCALE = 0.55F;
    static final int LABEL_W = 25;

    /** Nom court d'un role, centre sous sa case (vraie police, echelle {@link #LABEL_SCALE}, au plus {@link #LABEL_W} px). */
    private static void label(GuiMachine gui, GuiGraphics g, int role) {
        Component t = Component.translatable("gui.pioneers.blast.role." + BlastFurnaceTile.ROLE_KEYS[role]);
        float w = Math.min(LABEL_W, gui.font().width(t) * LABEL_SCALE);
        GuiDraw.text(g, gui.font(), t, BlastFurnaceTile.INPUT_X[role] + 8 - w / 2F, BlastFurnaceTile.LABEL_Y, LABEL_SCALE, ROLE_TEXT[role], LABEL_W);
    }

    static final MachineScreens.View BLAST = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            double c = gui.value(UiValues.HEAT) / 10.0, eq = gui.value(UiValues.REACH) / 10.0, air = gui.value(UiValues.BLAST) / 1000.0;
            double progress = gui.value(UiValues.PROGRESS_X1000) / 1000.0;
            long iron = gui.value(BlastFurnaceTile.IRON), carbon = gui.value(BlastFurnaceTile.CARBON), flux = gui.value(BlastFurnaceTile.FLUX);
            int status = (int) gui.value(UiValues.STATUS);
            AgeViews.fireGauge(gui, g, mouseX, mouseY, StandardLayout.POWER, c, (int) BlastFurnaceTile.STEEL_C,
                  Component.translatable("gui.pioneers.foundry.reach", AgeViews.celsius(eq)));
            //1.0.4-beta (lot 9) : une colonne par role : case (fantome), nom court, reserve
            long[] held = {iron, carbon, flux};
            int[] max = {BlastFurnaceTile.MAX_IRON, BlastFurnaceTile.MAX_CARBON, BlastFurnaceTile.MAX_FLUX};
            for (int r = 0; r < 3; r++) {
                label(gui, g, r);
                charge(gui, g, mouseX, mouseY, BlastFurnaceTile.chargeGauge(r), held[r], max[r], ROLE_FILL[r],
                      "gui.pioneers.blast." + BlastFurnaceTile.ROLE_KEYS[r]);
            }
            StandardView.arrow(g, BlastFurnaceTile.ARROW, progress, 0xFFB050);
            int chip = status == 0 ? FIRE : status == 4 ? 0xE0A030 : status == 1 || status == 2 ? RED : 0x8FA3A0;
            Rect s = StandardLayout.screen(StandardLayout.WIDTH);
            List<GuiMachine.Row> rows = new ArrayList<>();
            rows.add(GuiMachine.Row.of("gui.pioneers.blast.tuyeres", GuiMachine.literal(gui.value(BlastFurnaceTile.TUYERES) + " · x"
                  + String.format(Locale.ROOT, "%.1f", air))));
            rows.add(GuiMachine.Row.of("gui.pioneers.blast.ingots", GuiMachine.literal(String.valueOf(iron / BlastFurnaceTile.IRON_PER_INGOT))));
            gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, Component.translatable("gui.pioneers.blast.status." + status), AgeViews.celsius(c),
                  status == 0 ? progress : -1, 0xFFB050, rows, false);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            for (int r = 0; r < 3; r++) {
                gui.slotTip(mouseX, mouseY, BlastFurnaceTile.INPUT_X[r], BlastFurnaceTile.INPUT_Y, "gui.pioneers.blast.slot." + BlastFurnaceTile.ROLE_KEYS[r]);
            }
            gui.slotTip(mouseX, mouseY, BlastFurnaceTile.STEEL_X, BlastFurnaceTile.STEEL_Y, "gui.pioneers.machine.out");
            gui.slotTip(mouseX, mouseY, BlastFurnaceTile.SLAG_X, BlastFurnaceTile.SLAG_Y, "gui.pioneers.machine.out");
            return true;
        }
    };
}

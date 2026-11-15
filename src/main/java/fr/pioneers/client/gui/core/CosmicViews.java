package fr.pioneers.client.gui.core;

import fr.pioneers.Pioneers;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.space.content.absorber.AbsorberControllerTile.Status;
import fr.pioneers.space.content.absorber.AbsorberControllerTile;
import fr.pioneers.space.content.absorber.AbsorberStructure;
import fr.pioneers.space.content.spacesim.SpaceSimControllerTile;
import fr.pioneers.space.content.spacesim.SpaceSimMode;
import fr.pioneers.space.content.spacesim.SpaceSimStructure;
import fr.pioneers.space.content.stabilizer.Regime;
import fr.pioneers.space.content.stabilizer.StabilizerControllerTile;
import fr.pioneers.space.content.stabilizer.StabilizerCoreTile;
import fr.pioneers.space.content.stabilizer.StabilizerState;
import fr.pioneers.space.content.stabilizer.StabilizerStructure;
import fr.pioneers.space.content.universe.GalaxyType;
import fr.pioneers.core.Texts;
import fr.pioneers.space.SolarSystem.Body;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * API Machine (lot 8c) : CORPS en VRAIE v2 des multiblocs cosmiques, dans la vue maison {@code house} (voile et erreur commune quand la
 * structure n'est pas formee, onglet Structure) : controleur du Stabilisateur spatial ({@code CONTROLLER} : carte de la chambre, case du noyau,
 * ecran d'etat, « Ouvrir la chambre »), noyau du Stabilisateur ({@code CORE} : entree et sortie, carte du regime, reserve du champ, ecran,
 * debit et arret d'urgence), Simulateur spatial ({@code SIMULATOR} : energie, plasma, carte, modes, allumage / arret / galaxie, planetes et
 * sorties) et Absorbeur solaire ({@code ABSORBER} : energie, caloporteur, carte de l'astre, thermometre, ecran, volets). Panneaux de 196 de
 * large ; geometrie lue sur les tiles (constantes partagees avec leurs {@code guiParts} et le banc) ; valeurs lues par leur NOM ({@code V_*}).
 * Remplace {@code StabilizerViews}, {@code SpaceSimViews} et {@code AbsorberViews} (chaine d'avant de {@code MachineViews}).
 */
final class CosmicViews {

    private static final int DIM = PiMenuScreen.DIM, TEXT = PiMenuScreen.SCREEN_TEXT;
    private static final int GREEN = 0x55FF88, RED = 0xFF6A5A, GOLD = 0xFFAA00, AQUA = 0x55DDFF, VIOLET = 0xB98CFF, STAR = 0xFFC060,
          FIELD = 0xFFC8383A, SOLAR = 0xFFC24A, GALAXY = 0x9CA8FF, SUN = 0xFFD060;
    private static final ResourceLocation SUN_LIT = Pioneers.rl("textures/gui/machine/absorber_sun_lit.png");
    private static final ResourceLocation SUN_DIM = Pioneers.rl("textures/gui/machine/absorber_sun_dim.png");

    private CosmicViews() {
    }

    private static MutableComponent stab(String key, Object... args) {
        return Component.translatable("stabilizer.pioneers." + key, args);
    }

    private static MutableComponent sim(String key, Object... args) {
        return Component.translatable("space_simulator.pioneers." + key, args);
    }

    private static MutableComponent abs(String key, Object... args) {
        return Component.translatable("solar_absorber.pioneers." + key, args);
    }

    private static MutableComponent lit(String text) {
        return Component.literal(text);
    }

    /** Lignes de texte d'une carte (repliees a sa largeur, bornees a sa hauteur). */
    private static void lines(GuiMachine gui, GuiGraphics g, Rect card, List<Component> lines, List<Integer> colors) {
        gui.screenAt(g, card.x(), card.y(), card.w(), card.h());
        float y = card.y() + 4;
        for (int i = 0; i < lines.size() && y < card.y() + card.h() - 6; i++) {
            int room = Math.max(1, (int) ((card.y() + card.h() - 4 - y) / 6));
            y += GuiDraw.wrapped(g, gui.font(), lines.get(i), card.x() + 4, y, i == 0 ? 0.6F : 0.5F, 0xFF000000 | colors.get(i), card.w() - 8,
                  Math.min(4, room), 6) * 6 + (i == 0 ? 2.5F : 1F);
        }
    }

    /** Ecran d'etat raccourci (sous la rangee des jauges, au-dessus de la rangee de reglage). */
    private static Rect screen() {
        return StandardLayout.shortScreen(StandardLayout.WIDTH);
    }

    // ------------------------------------------------------------------
    // Stabilisateur spatial : controleur
    // ------------------------------------------------------------------

    static final MachineScreens.View CONTROLLER = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            StabilizerControllerTile tile = StorageViews.as(StabilizerControllerTile.class, be);
            if (tile == null) {
                return;
            }
            boolean formed = gui.value(StabilizerControllerTile.V_FORMED) != 0, hasCore = gui.value(StabilizerControllerTile.V_HAS_CORE) != 0;
            boolean spent = gui.value(StabilizerControllerTile.V_SPENT) != 0;
            int regime = (int) gui.value(StabilizerControllerTile.V_REGIME);
            int tint = regime == 2 ? STAR : regime == 1 ? VIOLET : 0x8A98A0;
            //Carte de la chambre
            List<Component> lines = new ArrayList<>();
            List<Integer> colors = new ArrayList<>();
            lines.add(stab("controller.title"));
            colors.add(tint);
            if (!formed) {
                lines.add(StabilizerStructure.errorText(tile.getError()));
                colors.add(RED);
            } else {
                lines.add(stab("controller.valid"));
                colors.add(GREEN);
                lines.add(!hasCore ? stab("controller.waiting") : spent ? stab("controller.spent")
                                                                      : stab("controller.core", stab("regime." + Regime.values()[Math.max(0, regime - 1)].key())));
                colors.add(!hasCore || spent ? GOLD : TEXT & 0xFFFFFF);
            }
            lines(gui, g, StabilizerControllerTile.CARD, lines, colors);
            //Ecran d'etat : regime et etat de l'astre
            StabilizerState state = StabilizerState.byIndex((int) gui.value(StabilizerControllerTile.V_STATE));
            Component title = !formed ? Component.translatable("gui.pioneers.multiblock.not_formed") : !hasCore
                  ? Component.translatable("gui.pioneers.api.cosmic.waiting") : spent ? Component.translatable("gui.pioneers.api.cosmic.spent")
                  : stab(state == StabilizerState.STABLE ? "state.stable" : state == StabilizerState.OFF ? "state.off" : "state.igniting", "");
            Component head = regime > 0 ? stab("regime." + Regime.values()[regime - 1].key()) : lit("—");
            List<GuiMachine.Row> rows = new ArrayList<>();
            rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.api.cosmic.chamber"),
                  formed ? Component.translatable("gui.pioneers.multiblock.house.formed") : Component.translatable("gui.pioneers.multiblock.not_formed"),
                  0xFF000000 | (formed ? GREEN : RED)));
            rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.api.cosmic.core"), !hasCore ? lit("—")
                  : spent ? Component.translatable("gui.pioneers.api.cosmic.core_spent") : Component.translatable("gui.pioneers.api.cosmic.core_in"),
                  0xFF000000 | (!hasCore || spent ? GOLD : TEXT & 0xFFFFFF)));
            Rect s = screen();
            gui.info(g, s.x(), s.y(), s.w(), s.h(), formed && hasCore && !spent ? tint : RED, title, head, -1, 0, rows, false);
            Rect b = StabilizerControllerTile.OPEN;
            gui.steelButton(g, mouseX, mouseY, b.x(), b.y(), b.w(), b.h(), stab("controller.open"), formed && hasCore,
                  () -> gui.send(StabilizerControllerTile.ACTION_OPEN));
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            if (gui.over(mouseX, mouseY, StabilizerControllerTile.CORE_X - 1, StabilizerControllerTile.CORE_Y - 1, 18, 18)) {
                gui.tooltip(List.of(stab("controller.slot")));
            }
            return true;
        }
    };

    // ------------------------------------------------------------------
    // Stabilisateur spatial : noyau (chambre formee)
    // ------------------------------------------------------------------

    static final MachineScreens.View CORE = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            StabilizerCoreTile core = StorageViews.as(StabilizerCoreTile.class, be);
            if (core == null) {
                return;
            }
            Regime regime = Regime.values()[(int) Math.max(0, Math.min(gui.value(StabilizerCoreTile.V_REGIME) & 0xFF, Regime.values().length - 1))];
            StabilizerState state = StabilizerState.byIndex((int) gui.value(StabilizerCoreTile.V_STATE));
            boolean formed = gui.value(StabilizerCoreTile.V_FORMED) != 0, star = regime == Regime.STAR;
            Rect in = StabilizerCoreTile.IN, out = StabilizerCoreTile.OUT;
            if (regime.fluidInput()) {
                gui.views().fluidGauge(g, mouseX, mouseY, in.x(), in.y(), in.w(), in.h(), core.lavaTank);
            } else {
                gui.views().gasGauge(g, mouseX, mouseY, in.x(), in.y(), in.w(), in.h(), core.antimatterTank, stab("input"));
            }
            gui.views().gasGauge(g, mouseX, mouseY, out.x(), out.y(), out.w(), out.h(), core.outputTank, stab("output"));
            field(gui, g, mouseX, mouseY);
            //Carte du regime : production, consommation, energie, emetteurs
            List<Component> lines = new ArrayList<>();
            List<Integer> colors = new ArrayList<>();
            lines.add(stab("regime." + regime.key()));
            colors.add(star ? STAR : VIOLET);
            if (!formed) {
                lines.add(StabilizerStructure.errorText(core.getError()));
                colors.add(RED);
            } else {
                lines.add(stab("production", String.format(Locale.ROOT, "%.3f", gui.value(StabilizerCoreTile.V_PRODUCED_X1000) / 1000.0)));
                colors.add(gui.value(StabilizerCoreTile.V_PRODUCED_X1000) > 0 ? TEXT & 0xFFFFFF : DIM & 0xFFFFFF);
                lines.add(stab("consumption", Texts.format(gui.value(StabilizerCoreTile.V_CONSUMPTION)), star ? stab("lava") : stab("water")));
                colors.add(DIM & 0xFFFFFF);
                lines.add(stab("energy", Texts.energy(gui.value(StabilizerCoreTile.V_ENERGY))));
                colors.add(DIM & 0xFFFFFF);
                long powered = gui.value(StabilizerCoreTile.V_EMITTERS);
                lines.add(stab("emitters", powered, StabilizerCoreTile.EMITTERS));
                colors.add(powered == StabilizerCoreTile.EMITTERS || state == StabilizerState.OFF && gui.value(StabilizerCoreTile.V_FIELD_ENERGY) > 0 ? AQUA : RED);
            }
            lines(gui, g, StabilizerCoreTile.CARD, lines, colors);
            //Ecran d'etat : etat de l'astre, barre d'allumage ou d'avancement, ligne d'allumage
            Component title = switch (state) {
                case OFF -> stab("state.off");
                case IGNITING -> stab("state.igniting", GuiMachine.percent(gui.value(StabilizerCoreTile.V_FORMATION_X1000) / 1000.0));
                case STABLE -> stab("state.stable");
            };
            int chip = state == StabilizerState.OFF ? RED : state == StabilizerState.IGNITING ? GOLD : GREEN;
            double ratio;
            List<GuiMachine.Row> rows = new ArrayList<>();
            long capacity = gui.value(StabilizerCoreTile.V_FIELD_CAPACITY);
            if (state == StabilizerState.OFF && (gui.value(StabilizerCoreTile.V_REGIME) & 0x100) != 0) {
                ratio = 0;
                rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.api.cosmic.core"), stab("core.spent"), 0xFF000000 | GOLD));
            } else if (state == StabilizerState.OFF) {
                //Allumage en tout ou rien : la reserve doit contenir toute l'energie d'allumage
                long charge = gui.value(StabilizerCoreTile.V_IGNITION_CHARGE);
                boolean small = capacity < StabilizerCoreTile.IGNITION_ENERGY_FE;
                ratio = Math.min(1, charge / (double) StabilizerCoreTile.IGNITION_ENERGY_FE);
                rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.api.cosmic.ignition"),
                      lit(Texts.energy(small ? capacity : charge) + " / " + Texts.energy(StabilizerCoreTile.IGNITION_ENERGY_FE)),
                      0xFF000000 | (charge >= StabilizerCoreTile.IGNITION_ENERGY_FE ? GREEN : small ? RED : GOLD)));
            } else {
                ratio = state == StabilizerState.IGNITING ? gui.value(StabilizerCoreTile.V_FORMATION_X1000) / 1000.0
                                                          : gui.value(StabilizerCoreTile.V_PROGRESS_X1000) / 1000.0;
                rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.api.cosmic.field_use"),
                      lit(Texts.energy(gui.value(StabilizerCoreTile.V_FIELD_USE)) + "/t"), TEXT));
            }
            Rect s = screen();
            gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, title, GuiMachine.percent(Math.max(0, Math.min(1, ratio))), ratio, star ? STAR : VIOLET, rows,
                  false);
            //Rangee de reglage : debit (-10 -1 [valeur] +1 +10) et arret d'urgence
            Rect r = StabilizerCoreTile.RATE;
            int x = r.x(), y = r.y(), h = r.h();
            gui.steelButton(g, mouseX, mouseY, x, y, 22, h, lit("-10"), formed, () -> gui.send(StabilizerCoreTile.ACTION_RATE_DOWN_10));
            gui.steelButton(g, mouseX, mouseY, x + 24, y, 18, h, lit("-1"), formed, () -> gui.send(StabilizerCoreTile.ACTION_RATE_DOWN));
            gui.screenAt(g, x + 44, y, 44, h);
            GuiDraw.center(g, gui.font(), stab("rate_value", gui.value(StabilizerCoreTile.V_RATE)), x + 66, y + 4.5F, 0.6F, TEXT, 40);
            if (gui.over(mouseX, mouseY, x + 44, y, 44, h)) {
                gui.tooltip(List.of(stab("rate.tooltip", StabilizerCoreTile.MIN_RATE, StabilizerCoreTile.MAX_RATE)));
            }
            gui.steelButton(g, mouseX, mouseY, x + 90, y, 18, h, lit("+1"), formed, () -> gui.send(StabilizerCoreTile.ACTION_RATE_UP));
            gui.steelButton(g, mouseX, mouseY, x + 110, y, 22, h, lit("+10"), formed, () -> gui.send(StabilizerCoreTile.ACTION_RATE_UP_10));
            int sx = x + 136, sw = r.w() - 136;
            gui.steelButton(g, mouseX, mouseY, sx, y, sw, h, stab("scram").withStyle(ChatFormatting.RED), state != StabilizerState.OFF,
                  () -> gui.send(StabilizerCoreTile.ACTION_SCRAM));
            if (gui.over(mouseX, mouseY, sx, y, sw, h)) {
                gui.tooltip(List.of(stab("scram.tooltip")));
            }
        }

        /** Reserve d'energie commune des ports d'alimentation (champ), en rouge comme toute energie. */
        private void field(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY) {
            Rect f = StabilizerCoreTile.FIELD;
            long stored = gui.value(StabilizerCoreTile.V_FIELD_ENERGY), capacity = gui.value(StabilizerCoreTile.V_FIELD_CAPACITY);
            double ratio = capacity <= 0 ? 0 : Math.min(1, stored / (double) capacity);
            gui.frame(g, f.x(), f.y(), f.w(), f.h());
            int filled = (int) Math.round((f.h() - 2) * ratio);
            if (filled > 0) {
                g.fill(f.x() + 1, f.y() + f.h() - 1 - filled, f.x() + f.w() - 1, f.y() + f.h() - 1, FIELD);
                g.fill(f.x() + 1, f.y() + f.h() - 1 - filled, f.x() + f.w() - 1, f.y() + f.h() - filled, 0x80FFFFFF);
            }
            GuiMachine.ticks(g, f.x(), f.y(), f.w(), f.h());
            if (gui.over(mouseX, mouseY, f.x(), f.y(), f.w(), f.h())) {
                gui.tooltip(List.of(stab("field"), lit(Texts.energy(stored) + " / " + Texts.energy(capacity)).withStyle(ChatFormatting.GRAY),
                      stab("field.use", Texts.energy(gui.value(StabilizerCoreTile.V_FIELD_USE))).withStyle(ChatFormatting.GRAY),
                      stab("field.tooltip").withStyle(ChatFormatting.DARK_GRAY)));
            }
        }
    };

    // ------------------------------------------------------------------
    // Simulateur spatial
    // ------------------------------------------------------------------

    static final MachineScreens.View SIMULATOR = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            SpaceSimControllerTile tile = StorageViews.as(SpaceSimControllerTile.class, be);
            if (tile == null) {
                return;
            }
            SpaceSimMode mode = SpaceSimMode.byIndex((int) gui.value(SpaceSimControllerTile.V_MODE));
            SpaceSimControllerTile.Status status = SpaceSimControllerTile.Status.byIndex((int) gui.value(SpaceSimControllerTile.V_STATUS));
            boolean running = gui.value(SpaceSimControllerTile.V_RUNNING) != 0, formed = gui.value(SpaceSimControllerTile.V_FORMED) != 0;
            GalaxyType galaxy = GalaxyType.byIndex((int) gui.value(SpaceSimControllerTile.V_GALAXY));
            Rect e = SpaceSimControllerTile.ENERGY, p = SpaceSimControllerTile.PLASMA;
            gui.energy(g, mouseX, mouseY, e.x(), e.y(), e.w(), e.h());
            gui.views().gasGauge(g, mouseX, mouseY, p.x(), p.y(), p.w(), p.h(), tile.plasmaTank, sim("plasma"));
            card(gui, g, tile, mode, status, running, formed, galaxy);
            //Choix du mode (le coeur pose doit correspondre)
            boolean idle = !running;
            Rect m = SpaceSimControllerTile.MODES;
            gui.steelButton(g, mouseX, mouseY, m.x(), m.y(), m.w(), 12, sim("mode.short.solar").withStyle(mode == SpaceSimMode.SOLAR ? ChatFormatting.GOLD
                  : ChatFormatting.GRAY), idle && mode != SpaceSimMode.SOLAR, () -> gui.send(SpaceSimControllerTile.ACTION_MODE_SOLAR));
            gui.steelButton(g, mouseX, mouseY, m.x(), m.y() + 14, m.w(), 12, sim("mode.short.galaxy").withStyle(mode == SpaceSimMode.GALAXY
                  ? ChatFormatting.BLUE : ChatFormatting.GRAY), idle && mode != SpaceSimMode.GALAXY, () -> gui.send(SpaceSimControllerTile.ACTION_MODE_GALAXY));
            if (gui.over(mouseX, mouseY, m.x(), m.y(), m.w(), m.h())) {
                gui.tooltip(List.of(sim("mode.tooltip")));
            }
            //Rangee : allumage, arret, galaxie
            Rect r = SpaceSimControllerTile.ROW;
            int x = r.x(), y = r.y(), h = r.h();
            boolean canIgnite = formed && !running && mode != SpaceSimMode.NONE && gui.value(SpaceSimControllerTile.V_CAN_IGNITE) != 0;
            gui.steelButton(g, mouseX, mouseY, x, y, 56, h, sim(mode == SpaceSimMode.GALAXY ? "big_bang" : "ignite"), canIgnite,
                  () -> gui.send(SpaceSimControllerTile.ACTION_IGNITE));
            gui.steelButton(g, mouseX, mouseY, x + 58, y, 44, h, sim("shutdown").withStyle(ChatFormatting.RED), running,
                  () -> gui.send(SpaceSimControllerTile.ACTION_SHUTDOWN));
            boolean pick = mode == SpaceSimMode.GALAXY;
            int gx = x + 106, gw = r.w() - 106;
            gui.steelButton(g, mouseX, mouseY, gx, y, 12, h, lit("<"), pick, () -> gui.send(SpaceSimControllerTile.ACTION_GALAXY_PREV));
            gui.screenAt(g, gx + 14, y, gw - 28, h);
            GuiDraw.center(g, gui.font(), Component.translatable("universe.pioneers.galaxy." + galaxy.id()), gx + gw / 2F, y + 4.5F, 0.55F,
                  0xFF000000 | (pick ? GALAXY : DIM), gw - 32);
            if (gui.over(mouseX, mouseY, gx + 14, y, gw - 28, h)) {
                gui.tooltip(List.of(Component.translatable("universe.pioneers.galaxy." + galaxy.id()),
                      Component.translatable("universe.pioneers.galaxy." + galaxy.id() + ".desc").withStyle(ChatFormatting.GRAY),
                      sim("galaxy.tooltip").withStyle(ChatFormatting.DARK_GRAY)));
            }
            gui.steelButton(g, mouseX, mouseY, gx + gw - 12, y, 12, h, lit(">"), pick, () -> gui.send(SpaceSimControllerTile.ACTION_GALAXY_NEXT));
            //Legende des cases
            GuiDraw.text(g, gui.font(), Component.translatable("gui.pioneers.api.cosmic.planets"), SpaceSimControllerTile.PLANETS_X - 1,
                  SpaceSimControllerTile.PLANETS_Y - 6, 0.45F, DIM, 140);
        }

        private void card(GuiMachine gui, GuiGraphics g, SpaceSimControllerTile tile, SpaceSimMode mode, SpaceSimControllerTile.Status status,
              boolean running, boolean formed, GalaxyType galaxy) {
            List<Component> lines = new ArrayList<>();
            List<Integer> colors = new ArrayList<>();
            lines.add(mode == SpaceSimMode.GALAXY ? sim("mode.galaxy", Component.translatable("universe.pioneers.galaxy." + galaxy.id())) : sim("mode." + mode.key()));
            colors.add(mode == SpaceSimMode.GALAXY ? GALAXY : mode == SpaceSimMode.SOLAR ? SOLAR : DIM & 0xFFFFFF);
            if (!formed) {
                lines.add(SpaceSimStructure.errorText(tile.getError()));
                colors.add(RED);
            } else {
                lines.add(sim("status." + status.key()));
                colors.add(running ? GREEN : status == SpaceSimControllerTile.Status.OFF ? DIM & 0xFFFFFF : RED);
                lines.add(sim("upkeep", Texts.energy(gui.value(SpaceSimControllerTile.V_UPKEEP_ENERGY)), Texts.format(gui.value(SpaceSimControllerTile.V_UPKEEP_ITEMS))));
                colors.add(DIM & 0xFFFFFF);
                if (!running) {
                    lines.add(sim("ignition", Texts.energy(gui.value(SpaceSimControllerTile.V_IGNITION_ENERGY)),
                          Texts.format(gui.value(SpaceSimControllerTile.V_IGNITION_ITEMS))));
                    colors.add(mode == SpaceSimMode.NONE ? DIM & 0xFFFFFF : GOLD);
                    if (gui.value(SpaceSimControllerTile.V_CAN_IGNITE) == 0) {
                        lines.add(sim(mode == SpaceSimMode.GALAXY ? "core.hint.galaxy" : "core.hint.solar"));
                        colors.add(GOLD);
                    }
                } else {
                    lines.add(sim("used", Texts.energy(gui.value(SpaceSimControllerTile.V_USED))));
                    colors.add(TEXT & 0xFFFFFF);
                }
                if (mode == SpaceSimMode.GALAXY) {
                    long cycle = Math.max(1, gui.value(SpaceSimControllerTile.V_CYCLE));
                    lines.add(sim("progress", GuiMachine.percent(gui.value(SpaceSimControllerTile.V_PROGRESS) / (double) cycle)));
                    colors.add(DIM & 0xFFFFFF);
                    lines.add(sim("cycles", gui.value(SpaceSimControllerTile.V_CYCLES)));
                    colors.add(DIM & 0xFFFFFF);
                } else if (mode == SpaceSimMode.SOLAR) {
                    lines.add(sim("planets", gui.value(SpaceSimControllerTile.V_PLANETS), SpaceSimControllerTile.PLANETS));
                    colors.add(gui.value(SpaceSimControllerTile.V_PLANETS) > 0 ? TEXT & 0xFFFFFF : DIM & 0xFFFFFF);
                    lines.add(sim("progress", GuiMachine.percent(gui.value(SpaceSimControllerTile.V_PROGRESS) / 1000.0)));
                    colors.add(DIM & 0xFFFFFF);
                }
            }
            lines(gui, g, SpaceSimControllerTile.CARD, lines, colors);
        }
    };

    // ------------------------------------------------------------------
    // Absorbeur solaire
    // ------------------------------------------------------------------

    static final MachineScreens.View ABSORBER = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            AbsorberControllerTile tile = StorageViews.as(AbsorberControllerTile.class, be);
            if (tile == null) {
                return;
            }
            Status status = Status.byIndex((int) gui.value(AbsorberControllerTile.V_STATUS));
            double temperature = gui.value(AbsorberControllerTile.V_TEMPERATURE_X10) / 10.0;
            boolean formed = gui.value(AbsorberControllerTile.V_FORMED) != 0, shutters = gui.value(AbsorberControllerTile.V_SHUTTERS) != 0;
            Rect e = AbsorberControllerTile.ENERGY, c = AbsorberControllerTile.COLD, h = AbsorberControllerTile.HOT, t = AbsorberControllerTile.THERMO;
            gui.energy(g, mouseX, mouseY, e.x(), e.y(), e.w(), e.h());
            gui.views().gasGauge(g, mouseX, mouseY, c.x(), c.y(), c.w(), c.h(), tile.coolantTank, abs("coolant_in"));
            gui.views().gasGauge(g, mouseX, mouseY, h.x(), h.y(), h.w(), h.h(), tile.heatedTank, abs("coolant_out"));
            //Thermometre : degres Celsius affiches, echelle en kelvins
            gui.thermometer(g, mouseX, mouseY, t.x(), t.y(), t.w(), t.h(), temperature + 273, (int) (AbsorberControllerTile.SAFE_TEMPERATURE + 273),
                  List.of(abs("temperature", Math.round(temperature))));
            //Carte de l'astre : soleil, etoile et capteurs, eruption
            Rect card = AbsorberControllerTile.CARD;
            gui.screenAt(g, card.x(), card.y(), card.w(), card.h());
            g.blit(formed && status == Status.RUNNING ? SUN_LIT : SUN_DIM, card.x() + 4, card.y() + 4, 0, 0, 16, 16, 16, 16);
            int tx = card.x() + 24, tw = card.w() - 28;
            int star = (int) gui.value(AbsorberControllerTile.V_STAR) - 1;
            Body body = star < 0 ? null : Body.byIndex(star);
            Component head = body == null || !body.isStar() ? abs("no_star")
                  : abs("star_short", Component.translatable(body.getTranslationKey()), String.format(Locale.ROOT, "%.2f", AbsorberControllerTile.luminosity(body)));
            GuiDraw.wrapped(g, gui.font(), head, tx, card.y() + 5, 0.55F, 0xFF000000 | SUN, tw, 2, 6);
            GuiDraw.text(g, gui.font(), abs("collectors_short", gui.value(AbsorberControllerTile.V_COLLECTORS)), card.x() + 5, card.y() + 26, 0.55F, TEXT,
                  card.w() - 10);
            GuiDraw.text(g, gui.font(), abs("row.output").append(" : ").append(Texts.energy(gui.value(AbsorberControllerTile.V_OUTPUT)) + "/t"),
                  card.x() + 5, card.y() + card.h() - 10, 0.55F, 0xFF000000 | AQUA, card.w() - 10);
            double flare = gui.value(AbsorberControllerTile.V_FLARE_X100) / 100.0;
            if (status == Status.NO_RESEARCH) {
                GuiDraw.wrapped(g, gui.font(), Component.translatable("research.pioneers.node.solar_absorption"), card.x() + 5, card.y() + 36, 0.5F,
                      0xFF000000 | RED, card.w() - 10, 2, 6);
            } else if (flare > 1.01) {
                GuiDraw.text(g, gui.font(), abs("row.flare").append(" : ").append(String.format(Locale.ROOT, "×%.1f", flare)), card.x() + 5,
                      card.y() + 36, 0.55F, 0xFF000000 | GOLD, card.w() - 10);
            }
            //Ecran d'etat
            Status shown = status == Status.NO_RESEARCH ? Status.SHUTTERED : status;
            int chip = !formed ? RED : switch (shown) {
                case RUNNING -> GREEN;
                case FULL, SHUTTERED -> GOLD;
                default -> RED;
            };
            Component title = !formed ? AbsorberStructure.errorText(tile.getError()) : abs("status." + status.key());
            List<GuiMachine.Row> rows = new ArrayList<>();
            rows.add(GuiMachine.Row.of("solar_absorber.pioneers.row.coolant", lit(gui.value(AbsorberControllerTile.V_COOLANT) + " mB/t")));
            rows.add(new GuiMachine.Row(abs("row.temperature"), lit(Math.round(temperature) + " °C"),
                  temperature > AbsorberControllerTile.SAFE_TEMPERATURE ? 0xFF000000 | RED : TEXT));
            Rect s = screen();
            gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, title, lit(Texts.energy(gui.value(AbsorberControllerTile.V_OUTPUT)) + "/t"), -1, 0, rows, false);
            Rect b = AbsorberControllerTile.SHUTTERS;
            gui.steelButton(g, mouseX, mouseY, b.x(), b.y(), b.w(), b.h(), abs(shutters ? "shutters.open" : "shutters.close"), formed,
                  () -> gui.send(AbsorberControllerTile.ACTION_SHUTTERS));
            if (gui.over(mouseX, mouseY, b.x(), b.y(), b.w(), b.h())) {
                gui.tooltip(List.of(abs("shutters.tooltip")));
            }
        }
    };
}

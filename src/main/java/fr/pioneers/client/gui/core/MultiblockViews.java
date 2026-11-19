package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.multiblock.HouseShapes;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockLayout;
import fr.pioneers.api.multiblock.MultiblockValues;
import fr.pioneers.api.multiblock.StructureError;
import fr.pioneers.api.multiblock.StructureMatcher;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.guide.client.GuideLinks;
import fr.pioneers.core.common.projector.Projections;
import fr.pioneers.core.Texts;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.IndustryStyle;
import fr.pioneers.tech.industry.multi.PortBlock;
import fr.pioneers.tech.industry.multi.SiloCore;
import fr.pioneers.tech.industry.multi.Structure;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * API Multibloc (lot 5) : INTERFACE v2 COMMUNE des controleurs de multiblocs (vues {@code controller} et {@code offshore} des fiches), dans
 * la grammaire des machines compactes ({@link StandardView} : titre et pastilles, panneau 196, inventaire dans le cadre, onglets de 18 a
 * droite, carre du selecteur par-dessus la zone machine quand il y a des recettes, centrage) et conforme aux maquettes validees
 * (scratchpad/api/planches/planche_multibloc.png, apilot1/planches/planche_api1_multibloc.png) :
 * <ul>
 *   <li>FORME : energie, thermometre et caloporteur a gauche, jauges et cases d'entree, case « Recette » sur la fleche, sorties, caloporteur
 *   use ; ecran : etat, avancement, « parallele · consommation » et « structure : formee · L x l x H » ;</li>
 *   <li>NON FORME : zone voilee de gris (le selecteur reste utilisable), pastille « Non formé », ecran rouge avec l'ERREUR COMMUNE
 *   ({@link StructureError} : a corriger, ou, attendu) ;</li>
 *   <li>onglet STRUCTURE (par-dessus la zone machine seulement) : decompte par role ({@link StructureMatcher#census}), ports poses, tier,
 *   premiere faute, boutons « Afficher le plan » (la projection) et « Voir dans JEI » (page « Multiblocs »).</li>
 * </ul>
 * Valeurs lues par leur NOM ({@link MultiblockValues}). Lot 6 : reacteurs, fusion et calcul dans la MEME interface (vues {@code reactor},
 * {@code fusion}, {@code compute}) avec leurs parties speciales ({@link CoreViews} : jauges propres, ecran du coeur, rangee de commandes).
 */
final class MultiblockViews {

    static final int RED = 0xE04848, OK = 0xFF7FE8A0, WARN = 0xFFE0A030;

    private MultiblockViews() {
    }

    @Nullable
    private static ControllerTile controller(BlockEntity be) {
        ControllerTile tile = StorageViews.as(ControllerTile.class, be);
        return tile;
    }

    private static boolean formed(GuiMachine gui) {
        return gui.value(MultiblockValues.STATUS) != ControllerTile.UNFORMED;
    }

    private static StructureError error(GuiMachine gui, ControllerTile tile) {
        long at = gui.value(MultiblockValues.ERROR_AT);
        int code = (int) gui.value(MultiblockValues.ERROR);
        Structure.Error e = Structure.Error.values()[Math.max(0, Math.min(code, Structure.Error.values().length - 1))];
        return StructureError.of(e, at == Long.MIN_VALUE ? null : BlockPos.of(at), (int) gui.value(MultiblockValues.ERROR_DETAIL), tile.getBlockPos(),
              tile.facing(), tile.machine);
    }

    static final MachineScreens.View CONTROLLER = new Controller(null);
    /** Lot 6 : controleurs a coeur propre (meme interface, corps special). */
    static final MachineScreens.View REACTOR = new Controller(CoreViews.REACTOR), FUSION = new Controller(CoreViews.FUSION),
          COMPUTE = new Controller(CoreViews.COMPUTE);

    /** Interface v2 d'un controleur ; {@code core} : corps special du coeur declare par la fiche (null : procede commun). */
    private static final class Controller implements MachineScreens.View {

        @Nullable
        private final CoreViews.Body core;

        Controller(@Nullable CoreViews.Body core) {
            this.core = core;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            ControllerTile tile = controller(be);
            if (tile == null) {
                return;
            }
            MultiblockLayout l = tile.v2;
            IndustryMachine m = tile.machine;
            MachineViews views = gui.views();
            boolean formed = formed(gui);
            if (core != null) {
                //Lot 6 : corps du coeur (jauges, fleche, ecran du coeur forme, commandes toujours actives) ; non forme : voile et erreur commune
                core.draw(gui, g, mouseX, mouseY, tile, l, formed);
                if (!formed) {
                    veil(g, l);
                    screen(gui, g, mouseX, mouseY, tile, l.screen, false, ControllerTile.UNFORMED, 0);
                }
                return;
            }
            Rect e = l.energy;
            gui.energy(g, mouseX, mouseY, e.x(), e.y(), e.w(), e.h());
            double kelvin = gui.value(MultiblockValues.TEMPERATURE) / 10.0;
            int nominal = (int) gui.value(MultiblockValues.NOMINAL);
            Rect t = l.thermometer;
            gui.thermometer(g, mouseX, mouseY, t.x(), t.y(), t.w(), t.h(), kelvin, nominal,
                  List.of(Component.translatable("industry.pioneers.gui.temperature", Math.round(kelvin)),
                        Component.translatable("industry.pioneers.gui.nominal", nominal).withStyle(ChatFormatting.GRAY),
                        Component.translatable("industry.pioneers.gui.heat_help").withStyle(ChatFormatting.DARK_GRAY)));
            Rect c = l.coolant;
            views.fluidGauge(g, mouseX, mouseY, c.x(), c.y(), c.w(), c.h(), tile.coolantTank());
            if (gui.over(mouseX, mouseY, c.x(), c.y(), c.w(), c.h())) {
                gui.tooltip(List.of(Component.translatable("industry.pioneers.gui.coolant"), Component.translatable("industry.pioneers.gui.coolant_help")
                      .withStyle(ChatFormatting.GRAY)));
            }
            //1.0.6 : jauges de ressources (liquide ou gaz, infobulle de la sorte, fantome de ce qu'attend la recette verrouillee)
            fr.pioneers.core.recipe.RecipeChoice locked = l.recipe && tile.recipeLock().isSet() ? tile.lockedChoice() : null;
            views.resourceRow(g, mouseX, mouseY, l.fluidIn, l.gasIn, tile.fluidInTanks(), tile.gasInTanks(),
                  fr.pioneers.api.machine.resource.ResourceRows.pairedIn(m), false, locked);
            for (int i = 0; i < l.dataIn.size() && i < tile.dataInputs().size(); i++) {
                Rect r = l.dataIn.get(i);
                views.dataGauge(g, mouseX, mouseY, r.x(), r.y(), r.w(), r.h(), tile.dataInputs().get(i));
            }
            views.resourceRow(g, mouseX, mouseY, l.fluidOut, l.gasOut, tile.fluidOutTanks(), tile.gasOutTanks(), tile.pairedOutputs(), true, locked);
            Rect co = l.coolantOut;
            views.coolantOutGauge(g, mouseX, mouseY, co.x(), co.y(), co.w(), co.h(), tile.coolantOutTank(), tile.steamOutTank());
            int status = (int) gui.value(MultiblockValues.STATUS);
            double progress = gui.value(MultiblockValues.PROGRESS) / 1000.0;
            //1.0.4-beta (lot 31) : salle blanche : accent gris argent (pas la couleur de la machine)
            arrow(g, l.arrow, status == ControllerTile.WORKING ? progress : 0, gui.skinAccent(m.accent));
            if (l.tool) {
                gui.toolSlot(g, mouseX, mouseY, l.toolX, l.toolY, m.key);   // 1.0.5-beta (lot 44) : case Outil des remanents
            }
            if (!formed) {
                veil(g, l);
            }
            screen(gui, g, mouseX, mouseY, tile, l.screen, formed, status, progress);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            ControllerTile tile = controller(be);
            if (tile != null) {
                for (int[] p : tile.v2.itemIn) {
                    gui.slotTip(mouseX, mouseY, p[0], p[1], "gui.pioneers.machine.in");
                }
                for (int[] p : tile.v2.itemOut) {
                    gui.slotTip(mouseX, mouseY, p[0], p[1], "gui.pioneers.machine.out");
                }
            }
            return true;
        }

        @Override
        public Component titleNote(GuiMachine gui, BlockEntity be) {
            return formed(gui) ? null : Component.translatable("gui.pioneers.multiblock.not_formed").withStyle(ChatFormatting.RED);
        }

        @Override
        public int titleTier(GuiMachine gui, BlockEntity be, int fallback) {
            //1.0.4-beta (lot 31, Guillaume : « l'interface de la salle blanche n'embarque pas de tier car le multi n'utilise pas les notions
            //de tiers ») : pas de pastille MK au titre d'une machine de salle blanche (son tier fixe ne s'affiche nulle part dans l'interface)
            if (gui.light()) {
                return -1;
            }
            return formed(gui) ? (int) Math.max(0, Math.min(3, gui.value(MultiblockValues.TIER))) : -1;
        }

        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity be) {
            ControllerTile tile = controller(be);
            List<StandardView.InfoRow> rows = new ArrayList<>();
            if (tile == null) {
                return rows;
            }
            MutableComponent parallel = Component.empty();
            for (int t = 0; t < 4; t++) {
                parallel.append(t == 0 ? Component.empty() : Component.literal(" / ")).append(Component.literal(String.valueOf(Tiers.parallelCap(t))));
            }
            if (core == null) {
                //Lot 31 : salle blanche : sa capacite (fixe), pas l'echelle MKI -> MKIV
                rows.add(gui.light() ? new StandardView.InfoRow("parallel_cleanroom", Component.literal(String.valueOf(Tiers.parallelCap(cleanroomTier(gui)))),
                      PiMenuScreen.TEXT) : new StandardView.InfoRow("parallel", parallel, PiMenuScreen.TEXT));
                rows.add(new StandardView.InfoRow("consumption", GuiMachine.literal(GuiMachine.compact(gui.value(MultiblockValues.POWER)) + " FE/t"),
                      0xFFFFFF55));
            }
            rows.add(new StandardView.InfoRow("reserve", GuiMachine.literal(Texts.energy(gui.value(fr.pioneers.api.machine.UiValues.CAPACITY))), PiMenuScreen.TEXT));
            int[] size = fr.pioneers.api.multiblock.SpecBlueprint.size(tile.machine);
            if (size[3] > 0 && !tile.machine.freeform) {
                rows.add(new StandardView.InfoRow("size", Component.translatable("gui.pioneers.multiblock.size_value", size[0], size[1], size[2], size[3]),
                      PiMenuScreen.TEXT));
            }
            var spec = MachineScreens.spec(be);
            var compact = spec == null ? null : fr.pioneers.api.machine.MachineRegistry.byKey(spec.compact());
            if (compact != null && !compact.blocks().isEmpty()) {
                //1.0.4-beta (lot 32) : salle blanche : le nom de la compacte, sans « (MKI a MKIV) »
                Component name = StandardView.baseName(Component.translatable("block.pioneers." + compact.blocks().get(0)));
                rows.add(new StandardView.InfoRow("compact", gui.light() ? name : Component.translatable("gui.pioneers.multiblock.compact_value", name),
                      0xFF3FD0A0));
            }
            return rows;
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.multiblock.info_help";
        }

        @Override
        public boolean structureTab(BlockEntity be) {
            return controller(be) != null;
        }

        @Override
        public void structure(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            ControllerTile tile = controller(be);
            if (tile != null) {
                structurePanel(gui, g, mouseX, mouseY, tile);
            }
        }
    }

    /** Non forme : la zone des jauges est voilee (les cases et les commandes restent utilisables ; lot 31 : voile blanc sur la salle blanche). */
    private static void veil(GuiGraphics g, MultiblockLayout l) {
        g.fill(6, StandardLayout.TOP - 2, l.width - 6, StandardLayout.TOP + l.gaugeH + 2, GuiMachine.lightSkin() ? 0x90E4E8EC : 0x70202830);
    }

    /**
     * 1.0.4-beta (lot 31) : niveau FIXE d'une machine de salle blanche (structure sans piece a tier : {@code GeneratedNbtShapes.Shape#fixedTier},
     * T:industry/nbt_shapes.py FIXED_TIER). Il regle toujours la vitesse, le parallele, la puissance et la chaleur (gameplay inchange au
     * lot 31) mais ne s'affiche plus comme un tier : seulement ses effets (operations a la fois, GOPS, FE/t).
     */
    static int cleanroomTier(GuiMachine gui) {
        return (int) Math.max(0, Math.min(3, gui.value(MultiblockValues.TIER)));
    }

    /** Fleche horizontale (meme dessin que les machines compactes). */
    static void arrow(GuiGraphics g, Rect r, double ratio, int accent) {
        GuiMachine.jeiArrow = new net.minecraft.client.renderer.Rect2i(r.x(), r.y() - 2, r.w(), r.h() + 4);
        int x = r.x(), y = r.y() + r.h() / 2;
        int len = r.w() - 5;
        //1.0.4-beta (lot 31) : rail clair sur la salle blanche
        int track = GuiMachine.lightSkin() ? GuiMachine.LIGHT_TRACK : 0xFF2A323B;
        g.fill(x, y - 1, x + len, y + 2, track);
        for (int i = 0; i < 5; i++) {
            g.fill(x + len + i, y - 4 + i, x + len + i + 1, y + 5 - i, track);
        }
        int filled = (int) Math.round(r.w() * Math.max(0, Math.min(1, ratio)));
        int c = 0xFF000000 | accent;
        g.fill(x, y - 1, x + Math.min(filled, len), y + 2, c);
        for (int i = 0; i < 5 && filled > len + i; i++) {
            g.fill(x + len + i, y - 4 + i, x + len + i + 1, y + 5 - i, c);
        }
    }

    /** Ecran d'etat : forme (etat, avancement, parallele et consommation, structure) ou non forme (erreur commune : quoi, ou, attendu). */
    private static void screen(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, ControllerTile tile, Rect s, boolean formed, int status,
          double progress) {
        IndustryMachine m = tile.machine;
        List<GuiMachine.Row> rows = new ArrayList<>();
        Component name;
        Component headline = Component.empty();
        int chip;
        double ratio = -1;
        if (!formed) {
            StructureError err = error(gui, tile);
            name = Component.translatable("gui.pioneers.multiblock.unformed");
            chip = RED;
            rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.multiblock.fix"), err.what((int) gui.value(MultiblockValues.ERROR_DETAIL)),
                  0xFFFF6A5A));
            Component where = err.where();
            rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.multiblock.where"), where == null ? Component.literal("—") : where,
                  PiMenuScreen.TEXT));
            Component expected = err.expectedText();
            rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.multiblock.expected"), expected == null ? Component.literal("—") : expected,
                  PiMenuScreen.TEXT));
        } else {
            long tierNeeded = gui.value(MultiblockValues.TIER_NEEDED);
            //Lot 31 : salle blanche : « recette hors de portee » sans nommer de tier
            name = status == ControllerTile.TIER && gui.light() ? Component.translatable("industry.pioneers.multi.status.cleanroom_tier")
                   : status == ControllerTile.TIER && tierNeeded >= 0 && tierNeeded <= 3
                   ? Component.translatable("industry.pioneers.tier_required", Component.translatable("industry.pioneers.tier." + Tiers.tier((int) tierNeeded).id()))
                   : status == ControllerTile.WORKING && gui.value(UiValues.SURPLUS) > 0 ? Component.translatable("industry.pioneers.multi.status.surplus")
                   : Component.translatable("industry.pioneers.multi.status." + status);
            chip = status == ControllerTile.WORKING ? gui.skinAccent(m.accent) : status == ControllerTile.IDLE ? 0 : status == ControllerTile.OVERHEAT ? RED
                   : 0xE0A030;
            if (status == ControllerTile.WORKING) {
                headline = GuiMachine.percent(progress);
                ratio = progress;
            }
            long power = gui.value(MultiblockValues.POWER), saved = gui.value(MultiblockValues.HEAT_SAVED);
            Component energy = saved > 0 ? Component.translatable("industry.pioneers.gui.power_heat", Texts.energy(power), Texts.energy(saved))
                               : Component.literal(Texts.grouped(power) + " FE/t");
            rows.add(new GuiMachine.Row(Component.translatable(m.isGenerator() ? "gui.pioneers.multiblock.parallel_production" : "gui.pioneers.multiblock.parallel_power"),
                  Component.translatable("gui.pioneers.multiblock.parallel_value", gui.value(MultiblockValues.BATCH), gui.value(MultiblockValues.PARALLEL), energy),
                  0xFFFFFF55));
            int penalty = (int) gui.value(MultiblockValues.PENALTY);
            long dims = gui.value(MultiblockValues.DIMENSIONS);
            if (penalty == 1) {
                rows.add(GuiMachine.Row.of("industry.pioneers.gui.penalty", Component.translatable("industry.pioneers.gui.penalty.pressure",
                      fmt1(gui.value(MultiblockValues.RECIPE_AIR) / 10.0), fmt1(gui.value(MultiblockValues.AIR) / 10.0))));
            } else if (penalty == 2 || penalty == 3) {
                rows.add(GuiMachine.Row.of("industry.pioneers.gui.penalty", Component.translatable(penalty == 2 ? "industry.pioneers.gui.penalty.cold"
                      : "industry.pioneers.gui.penalty.hot", Math.round(gui.value(MultiblockValues.TEMPERATURE) / 10.0))));
            } else if (m.freeform) {
                long risers = gui.value(MultiblockValues.RISERS);
                rows.add(GuiMachine.Row.of("industry.pioneers.gui.risers", Component.translatable("industry.pioneers.gui.risers_value",
                      risers & 0xFFFF, risers >> 16 & 0xFFFF, gui.value(MultiblockValues.TUBES))));
            } else if (m == IndustryMachine.ITEM_SILO) {
                //1.0.4-beta (lot 22) : silo a objets : objets tenus / contenance (l'objet est celui de la case de sortie)
                long[] silo = SiloCore.unpack(gui.value(MultiblockValues.SILO));
                rows.add(GuiMachine.Row.of("industry.pioneers.gui.silo", Component.translatable("industry.pioneers.gui.silo_value",
                      Texts.grouped(silo[0]), Texts.grouped(SiloCore.capacity((int) silo[1], (int) gui.value(MultiblockValues.TIER))))));
            } else if (m.itemDrill()) {
                rows.add(GuiMachine.Row.of("industry.pioneers.gui.reserve", Component.literal(Texts.format(gui.value(MultiblockValues.RESERVE)))));
            } else {
                rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.multiblock.structure"), Component.translatable("gui.pioneers.multiblock.formed_value",
                      dims >> 16 & 0xFF, dims >> 8 & 0xFF, dims & 0xFF), OK));
            }
        }
        gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, name, headline, ratio, gui.skinAccent(m.accent), rows, false);
        ComputeViews.chipBadge(g, s.x() + s.w() - 13, s.y() + s.h() - 13, gui.value(MultiblockValues.CHIP));
        if (gui.over(mouseX, mouseY, s.x(), s.y(), s.w(), s.h())) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("block.pioneers." + m.multiId()));
            //Lot 31 : salle blanche : sa capacite fixe au lieu de l'echelle par tier
            tip.add((gui.light() ? Component.translatable("industry.pioneers.gui.parallel_rule_cleanroom", Tiers.parallelCap(cleanroomTier(gui)))
                     : Component.translatable("industry.pioneers.gui.parallel_rule")).withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("industry.pioneers.gui.industrial_rule").withStyle(ChatFormatting.GRAY));
            if (m.freeform) {
                if (gui.value(MultiblockValues.TUBES_NEEDED) > 0) {
                    tip.add(Component.translatable("industry.pioneers.gui.risers_need", gui.value(MultiblockValues.TUBES_NEEDED)).withStyle(ChatFormatting.GOLD));
                }
                tip.add(Component.translatable("industry.pioneers.gui.risers_help").withStyle(ChatFormatting.DARK_AQUA));
            }
            if (m.module != null) {
                tip.add(Component.translatable("industry.pioneers.gui.needs_module", Component.translatable("block.pioneers." + m.module.id()))
                      .withStyle(ChatFormatting.DARK_AQUA));
            }
            if (formed && (gui.value(MultiblockValues.HEAT_IN) > 0 || gui.value(MultiblockValues.HEAT_SAVED) > 0)) {
                tip.add(Component.translatable("industry.pioneers.gui.heat_in", Texts.format(gui.value(MultiblockValues.HEAT_IN)),
                      Texts.energy(gui.value(MultiblockValues.HEAT_SAVED))).withStyle(ChatFormatting.GOLD));
            }
            if (formed && m.heat > 0 && !m.isGenerator()) {
                tip.add(Component.translatable("industry.pioneers.gui.heat_rule").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (formed && (gui.value(MultiblockValues.RECIPE_AIR) > 0 || gui.value(MultiblockValues.AIR) > 0)) {
                tip.add(Component.translatable("industry.pioneers.gui.process_air", fmt1(gui.value(MultiblockValues.RECIPE_AIR) / 10.0))
                      .withStyle(ChatFormatting.AQUA));
            }
            if (m.style == IndustryStyle.LIVING) {
                tip.add(Component.translatable("industry.pioneers.gui.climate_rule").withStyle(ChatFormatting.DARK_GREEN));
            }
            ComputeViews.chipTip(tip, gui.value(MultiblockValues.CHIP));
            tip.add(Component.translatable("industry.pioneers.gui.plan_keys").withStyle(ChatFormatting.DARK_GRAY));
            gui.tooltip(tip);
        }
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    // ------------------------------------------------------------------ onglet Structure

    /** Cles de langue des categories du decompte ({@link StructureMatcher#SHELL}...). */
    private static final String[] CATEGORY = {"shell", "sheet", "frame", "interior", "reactor", "parts"};

    /**
     * Onglet Structure : par-dessus la zone machine (meme cadre que l'onglet Infos) : en-tete (taille, blocs), decompte par role, ports
     * poses, tier, premiere faute, boutons « Afficher le plan » et « Voir dans JEI ».
     */
    static void structurePanel(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, ControllerTile tile) {
        Font font = gui.font();
        Rect r = StandardView.infoRect(tile.v2.width);
        int x = r.x(), y = r.y(), w = r.w(), h = r.h();
        StandardView.overlayPanel(gui, g, x, y, w, h, gui.light() ? GuiSprites.CLEANROOM_INK : 0x3FD0A0);
        boolean formed = formed(gui);
        int[] size = fr.pioneers.api.multiblock.SpecBlueprint.size(tile.machine);
        Component head = tile.machine.freeform ? Component.translatable("gui.pioneers.multiblock.tab_head_free")
                         : Component.translatable("gui.pioneers.multiblock.tab_head", size[0], size[1], size[2], size[3]);
        GuiDraw.text(g, font, head, x + 6, y + 5, 0.75F, gui.ink(PiMenuScreen.TEXT), w - 12);
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < StructureMatcher.CATEGORIES; i++) {
            int[] c = MultiblockValues.split(gui.value(MultiblockValues.census(i)));
            if (c[1] == 0) {
                continue;
            }
            Component label = Component.translatable("gui.pioneers.multiblock.role." + CATEGORY[i]);
            rows.add(new Object[]{label, Component.literal(c[0] + " / " + c[1]), c[0] >= c[1] ? OK : WARN});
        }
        long ports = gui.value(MultiblockValues.PORTS);
        MutableComponent list = Component.empty();
        boolean first = true;
        for (PortBlock.Type type : PortBlock.Type.values()) {
            int n = (int) (ports >> (4 * type.ordinal()) & 15);
            if (n > 0) {
                list.append(first ? Component.empty() : Component.literal(" · ")).append(Component.literal(n + " "))
                      .append(Component.translatable("gui.pioneers.multiblock.port." + type.name().toLowerCase(Locale.ROOT)));
                first = false;
            }
        }
        rows.add(new Object[]{Component.translatable("gui.pioneers.multiblock.ports"), first ? Component.literal("—") : list, first ? WARN : PiMenuScreen.TEXT});
        if (!gui.light()) {
            //Lot 31 : pas de ligne « Tier » pour une machine de salle blanche (aucune piece a tier, interface sans tier)
            rows.add(new Object[]{Component.translatable("gui.pioneers.multiblock.tier"), formed ? Component.translatable("industry.pioneers.tier."
                  + Tiers.tier((int) Math.max(0, Math.min(3, gui.value(MultiblockValues.TIER)))).id()) : Component.literal("—"), PiMenuScreen.TEXT});
        }
        int yy = y + 17;
        for (Object[] row : rows) {
            Component value = (Component) row[1];
            float vw = Math.min(font.width(value) * 0.6F, w * 0.6F);
            GuiDraw.text(g, font, (Component) row[0], x + 8, yy, 0.6F, gui.ink(PiMenuScreen.DIM), Math.max(1, w - 24 - vw));
            GuiDraw.text(g, font, value, x + w - 8 - vw, yy, 0.6F, gui.ink((int) row[2]), vw + 1);
            yy += 9;
        }
        if (!formed) {
            StructureError err = error(gui, tile);
            Component where = err.where();
            MutableComponent fault = Component.translatable("gui.pioneers.multiblock.first_fault").append(" : ")
                  .append(err.what((int) gui.value(MultiblockValues.ERROR_DETAIL)));
            if (where != null) {
                fault.append(", ").append(where);
            }
            GuiDraw.text(g, font, fault, x + 8, yy + 1, 0.6F, gui.ink(0xFFFF6A5A), w - 16);
        }
        int by = y + h - 18, bw = (w - 18) / 2;
        gui.steelButton(g, mouseX, mouseY, x + 6, by, bw, 13, Component.translatable("gui.pioneers.multiblock.show_plan"), true, () -> showPlan(tile));
        gui.steelButton(g, mouseX, mouseY, x + 12 + bw, by, bw, 13, Component.translatable("gui.pioneers.multiblock.show_jei"), GuideLinks.jeiAvailable(),
              () -> GuideLinks.showInJei(new ItemStack(tile.getBlockState().getBlock()), false));
    }

    /**
     * Lot 6 : onglet Structure d'un multibloc maison (meme cadre que celui des industriels) : en-tete, lignes (libelle, valeur, couleur),
     * premiere faute (avec le « ou » commun), boutons « Afficher le plan » et « Voir dans JEI ».
     */
    static void housePanel(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile, int width, Component head, List<Object[]> rows,
          @Nullable Component fault) {
        Font font = gui.font();
        Rect r = StandardView.infoRect(width);
        int x = r.x(), y = r.y(), w = r.w(), h = r.h();
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0x78000000);
        g.fill(x, y, x + w, y + h, 0xFF2A3440);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF121820);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFF3FD0A0);
        gui.swallow(x, y, w, h);
        GuiDraw.text(g, font, head, x + 6, y + 5, 0.75F, PiMenuScreen.TEXT, w - 12);
        int yy = y + 17;
        for (Object[] row : rows) {
            Component value = (Component) row[1];
            float vw = Math.min(font.width(value) * 0.6F, w * 0.6F);
            GuiDraw.text(g, font, (Component) row[0], x + 8, yy, 0.6F, PiMenuScreen.DIM, Math.max(1, w - 24 - vw));
            GuiDraw.text(g, font, value, x + w - 8 - vw, yy, 0.6F, (int) row[2], vw + 1);
            yy += 9;
        }
        if (fault != null) {
            GuiDraw.text(g, font, fault, x + 8, yy + 1, 0.6F, 0xFFFF6A5A, w - 16);
        }
        int by = y + h - 18, bw = (w - 18) / 2;
        gui.steelButton(g, mouseX, mouseY, x + 6, by, bw, 13, Component.translatable("gui.pioneers.multiblock.show_plan"), true, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && tile.getLevel() != null) {
                Projections.showClient(tile.getLevel(), tile.getBlockPos(), tile.getBlockState(), mc.player.getDirection());
                mc.player.closeContainer();
            }
        });
        gui.steelButton(g, mouseX, mouseY, x + 12 + bw, by, bw, 13, Component.translatable("gui.pioneers.multiblock.show_jei"), GuideLinks.jeiAvailable(),
              () -> GuideLinks.showInJei(new ItemStack(tile.getBlockState().getBlock()), false));
    }

    // ------------------------------------------------------------------ lot 8a : multiblocs maison a l'interface v2

    /**
     * Lot 8a : INTERFACE v2 COMMUNE des MULTIBLOCS MAISON (vues {@code house} et {@code house_works} des fiches) : le corps propre d'avant
     * garde sa place dans le cadre v2 (inventaire dans le cadre, titre et pastilles, onglets a droite, Infos) ; NON FORME : corps voile de
     * gris (les cases restent utilisables), pastille « Non formé », bandeau rouge de l'ERREUR COMMUNE (a corriger, ou, attendu : cycle
     * {@code MultiblockCore.House}, attendu lu sur le plan de la fiche) ; onglet STRUCTURE (etat, taille, premiere faute, boutons « Afficher
     * le plan » et « Voir dans JEI »).
     */
    static final MachineScreens.View HOUSE = new House("gui.pioneers.api.info.help_house"), HOUSE_WORKS = new House("gui.pioneers.api.info.help_works");

    @Nullable
    private static MultiblockCore.House house(BlockEntity be) {
        MultiblockCore.House.Holder h = StorageViews.as(MultiblockCore.House.Holder.class, be);
        return h == null ? null : h.house();
    }

    /** Lignes de l'erreur commune d'une maison : {libelle, valeur} (quoi, ou, attendu). */
    static List<Component[]> houseFault(MultiblockCore.House h) {
        List<Component[]> out = new ArrayList<>();
        String what = h.error();
        out.add(new Component[]{Component.translatable("gui.pioneers.multiblock.fix"),
              Component.translatable(what == null ? MultiblockCore.House.UNFORMED : what)});
        Component where = StructureError.where(h.where());
        if (where != null) {
            out.add(new Component[]{Component.translatable("gui.pioneers.multiblock.where"), where});
        }
        if (h.expected() != null) {
            out.add(new Component[]{Component.translatable("gui.pioneers.multiblock.expected"), Component.translatable(h.expected())});
        }
        return out;
    }

    /** Bandeau rouge de l'erreur commune, en bas de la zone machine. */
    static Rect houseBand(int width, int height) {
        return new Rect(6, height - 34, width - 12, 30);
    }

    private static final class House implements MachineScreens.View {

        private final String help;

        House(String help) {
            this.help = help;
        }

        private static int[] size(BlockEntity be) {
            fr.pioneers.core.machine.MachineGui m = StorageViews.as(fr.pioneers.core.machine.MachineGui.class, be);
            return m == null ? new int[]{StandardLayout.WIDTH, StandardLayout.ZONE} : new int[]{m.guiWidth(), m.guiHeight()};
        }

        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            MachineScreens.body(be).draw(gui, g, mouseX, mouseY, be);
            MultiblockCore.House h = house(be);
            if (h == null || h.formed()) {
                return;
            }
            int[] wh = size(be);
            //Voile gris sur le corps (sous les cases : elles restent visibles et utilisables)
            for (StandardLayout.Part part : StandardLayout.body(wh[0], wh[1])) {
                Rect r = part.r();
                g.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), 0x70202830);
            }
            Rect b = houseBand(wh[0], wh[1]);
            g.fill(b.x() - 1, b.y() - 1, b.x() + b.w() + 1, b.y() + b.h() + 1, 0xC0000000);
            g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), 0xFF2A1416);
            g.fill(b.x(), b.y(), b.x() + 2, b.y() + b.h(), 0xFF000000 | RED);
            Font font = gui.font();
            int yy = b.y() + 3;
            for (Component[] row : houseFault(h)) {
                MutableComponent line = Component.empty().append(row[0]).append(" : ").append(row[1]);
                GuiDraw.text(g, font, line, b.x() + 6, yy, 0.6F, yy == b.y() + 3 ? 0xFFFF6A5A : 0xFFFFB050, b.w() - 10);
                yy += 9;
            }
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            MachineScreens.body(be).foreground(gui, g, mouseX, mouseY, be);
            return true;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Nullable
        @Override
        public Component titleNote(GuiMachine gui, BlockEntity be) {
            MultiblockCore.House h = house(be);
            return h == null || h.formed() ? null : Component.translatable("gui.pioneers.multiblock.not_formed").withStyle(ChatFormatting.RED);
        }

        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity be) {
            List<StandardView.InfoRow> rows = new ArrayList<>();
            MultiblockCore.House h = house(be);
            if (h != null) {
                rows.add(new StandardView.InfoRow("structure", h.formed() ? Component.translatable("gui.pioneers.multiblock.house.formed")
                      : Component.translatable("gui.pioneers.multiblock.not_formed"), h.formed() ? OK : WARN));
            }
            if (gui.value(fr.pioneers.api.machine.UiValues.CAPACITY) > 0) {
                rows.add(new StandardView.InfoRow("consumption", GuiMachine.literal(GuiMachine.compact(gui.value(fr.pioneers.api.machine.UiValues.USAGE) / 100.0)
                      + " FE/t"), 0xFFFFFF55));
                rows.add(new StandardView.InfoRow("reserve", GuiMachine.literal(Texts.energy(gui.value(fr.pioneers.api.machine.UiValues.CAPACITY))),
                      PiMenuScreen.TEXT));
            }
            return rows;
        }

        @Override
        public String infoHelp() {
            return help;
        }

        @Override
        public boolean structureTab(BlockEntity be) {
            return house(be) != null;
        }

        @Override
        public void structure(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            MultiblockCore.House h = house(be);
            if (h == null) {
                return;
            }
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{Component.translatable("gui.pioneers.multiblock.house.state"), h.formed()
                  ? Component.translatable("gui.pioneers.multiblock.house.formed") : Component.translatable("gui.pioneers.multiblock.not_formed"),
                  h.formed() ? OK : WARN});
            int[] size = h.size();
            if (h.formed() && size[0] > 0) {
                rows.add(new Object[]{Component.translatable("gui.pioneers.multiblock.house.size"),
                      Component.translatable("gui.pioneers.multiblock.house.size_value", size[0], size[2], size[1]), PiMenuScreen.TEXT});
            }
            HouseShapes.HouseShape shape = HouseShapes.shapeFor(be.getBlockState().getBlock());
            if (shape != null) {
                rows.add(new Object[]{Component.translatable("gui.pioneers.multiblock.house.plans"), Component.literal(String.valueOf(shape.plans().size())),
                      PiMenuScreen.TEXT});
            }
            MutableComponent fault = null;
            if (!h.formed()) {
                for (Component[] row : houseFault(h)) {
                    MutableComponent part = Component.empty().append(row[0]).append(" : ").append(row[1]);
                    fault = fault == null ? part : fault.append(" · ").append(part);
                }
            }
            housePanel(gui, g, mouseX, mouseY, be, size(be)[0], Component.translatable("gui.pioneers.multiblock.house.head"), rows, fault);
        }
    }

    /** « Afficher le plan » : la projection du plan sur le controleur (comme Maj + clic droit), puis l'interface se ferme pour la voir. */
    private static void showPlan(ControllerTile tile) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || tile.getLevel() == null) {
            return;
        }
        Projections.showClient(tile.getLevel(), tile.getBlockPos(), tile.getBlockState(), mc.player.getDirection());
        mc.player.closeContainer();
    }
}

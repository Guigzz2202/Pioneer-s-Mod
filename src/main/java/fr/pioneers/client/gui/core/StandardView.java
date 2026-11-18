package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.MachineRegistry;
import fr.pioneers.api.machine.MachineSpec;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.tier.GeneratedTierFamilies;
import fr.pioneers.api.machine.tier.TierRecipes;
import fr.pioneers.api.machine.unit.ProcessUnit;
import fr.pioneers.tech.bronze.machines.SteamMachine;
import fr.pioneers.tech.bronze.machines.SteamProcess;
import fr.pioneers.tech.bronze.machines.SteamProcessTile;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.core.Texts;
import fr.pioneers.core.side.SideType;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.industry.recipe.Remanents;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 2) : VUE DE L'INTERFACE STANDARD v2 des machines compactes a procede (ateliers MKI a MKIV et kit), conforme aux
 * maquettes validees (scratchpad/apilot1/planches) : zone machine {@link StandardLayout} (energie et batterie, jauges d'entree, cases,
 * case « Recette » au-dessus de la fleche, sorties, jauges de sortie), ecran d'etat de 48 px (pastille, etat, valeur cle, barre, 2 lignes
 * « libelle : valeur »), titre sans tier avec pastille de tier et pastille multibloc, onglet Infos. Les valeurs sont lues par leur NOM
 * ({@link UiValues}).
 * <p>
 * 1.0.5-beta (lot 38, specification age du bronze §3.2) : les machines de traitement a VAPEUR (T1 de leur famille) ont le meme ecran : la jauge
 * d'energie dessine la reserve de vapeur (code de l'ancienne vue {@code steam_process}), cases de {@link StandardLayout}, eau du melangeur a sa
 * place de fluide, ecran « Cycle · vapeur par operation » et « Rendement », onglets Faces et Recettes ; pastille de TIER GLOBAL dans le titre de
 * toute machine : « T0 · Mecanique », « T1 · Bronze », « T2 · MKI »... (texte toujours ecrit : jamais la couleur seule).
 */
final class StandardView implements MachineScreens.View {

    static final StandardView INSTANCE = new StandardView();
    private static final int CONSUMPTION = 0xFFFFFF55;
    /** Couleurs des tiers (pastille de tier, comme le badge MK de JEI). */
    private static final int[] TIER_COLORS = {0x58C860, 0x488CE6, 0xDC4038, 0x962848};

    private StandardView() {
    }

    @Override
    public boolean standard() {
        return true;
    }

    private static int accent(GuiMachine gui, AtelierTile tile) {
        return tile.machine != null ? tile.machine.accent : gui.accent();
    }

    // ------------------------------------------------------------------
    // Zone machine
    // ------------------------------------------------------------------

    @Override
    public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
        SteamProcessTile steam = StorageViews.as(SteamProcessTile.class, be);
        if (steam != null) {
            drawSteam(gui, g, mouseX, mouseY, steam);
            return;
        }
        AtelierTile tile = StorageViews.as(AtelierTile.class, be);
        if (tile == null) {
            return;
        }
        StandardLayout l = tile.layout;
        Rect e = l.energy;
        gui.energy(g, mouseX, mouseY, e.x(), e.y(), e.w(), e.h());
        MachineViews views = gui.views();
        //1.0.6 : jauges de ressources (liquide ou gaz, infobulle de la sorte, fantome de ce qu'attend la recette verrouillee)
        fr.pioneers.core.recipe.RecipeChoice locked = tile.recipeLock().isSet() ? tile.lockedChoice() : null;
        boolean pin = tile.machine != null && fr.pioneers.api.machine.resource.ResourceRows.pairedIn(tile.machine);
        boolean pout = tile.machine != null && fr.pioneers.api.machine.resource.ResourceRows.pairedOut(tile.machine);
        views.resourceRow(g, mouseX, mouseY, l.fluidIn, l.gasIn, tile.fluidInTanks(), tile.gasInTanks(), pin, false, locked);
        views.resourceRow(g, mouseX, mouseY, l.fluidOut, l.gasOut, tile.fluidOutTanks(), tile.gasOutTanks(), pout, true, locked);
        long duration = gui.value(UiValues.DURATION);
        double progress = duration <= 0 ? 0 : gui.value(UiValues.PROGRESS) / (double) duration;
        int status = (int) gui.value(UiValues.STATUS);
        int accent = accent(gui, tile);
        arrow(g, l.arrow, progress, accent);
        if (tile.toolSlot() != null && tile.machine.kind == IndustryMachine.Kind.ROCKS) {
            gui.sampleSlot(g, mouseX, mouseY, l.toolX, l.toolY, tile.tier.ordinal());   // 1.0.5-beta (lot 46) : case d'echantillon
        } else if (tile.toolSlot() != null) {
            gui.toolSlot(g, mouseX, mouseY, l.toolX, l.toolY, tile.machine.key);   // 1.0.5-beta (lot 44) : case Outil des remanents
        }
        screen(gui, g, mouseX, mouseY, tile, l.screen, status, progress, duration, accent);
    }

    /**
     * 1.0.5-beta (lot 38) : zone machine d'une machine de traitement a vapeur (T1) : jauge de vapeur a la place de l'energie (reserve,
     * contenance, consommation en infobulle), eau du melangeur, fleche a l'accent de la machine, ecran : etat, recette en cours, cycle et vapeur
     * par operation, rendement (x1,5 sur une recette mecanique a rendement).
     */
    private static void drawSteam(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, SteamProcessTile tile) {
        SteamProcess unit = tile.process();
        SteamMachine machine = unit.machineKind;
        StandardLayout l = unit.layout;
        double progress = gui.value(UiValues.PROGRESS_X1000) / 1000.0, stored = gui.value(UiValues.STEAM), ticks = gui.value(UiValues.DURATION);
        double capacity = gui.value(UiValues.STEAM_MAX), yield = gui.value(UiValues.YIELD_X1000) / 1000.0;
        int status = (int) gui.value(UiValues.STATUS);
        AgeViews.steamGauge(gui, g, mouseX, mouseY, StandardLayout.POWER, stored, capacity, unit.steamUse);
        if (tile.waterTank() != null && !l.fluidIn.isEmpty()) {
            Rect w = l.fluidIn.get(0);
            gui.views().fluidGauge(g, mouseX, mouseY, w.x(), w.y(), w.w(), w.h(), tile.waterTank());
        }
        arrow(g, l.arrow, progress, machine.accent);
        if (unit.toolSlot() != null) {
            gui.toolSlot(g, mouseX, mouseY, l.toolX, l.toolY, Remanents.STEAM + machine.key());   // lot 44
        }
        List<GuiMachine.Row> rows = new ArrayList<>();
        rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.steam.cycle"), Component.translatable("gui.pioneers.tier.cycle_steam",
              String.format(Locale.ROOT, "%.1f", ticks / 20.0), Texts.format(Math.round(unit.steamUse * ticks))), PiMenuScreen.TEXT));
        rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.tier.yield"), GuiMachine.literal("×" + yieldText(yield)),
              yield > 1.001 ? 0xFF7BD87B : PiMenuScreen.TEXT));
        int chip = status == SteamProcess.WORKING ? machine.accent : status == SteamProcess.IDLE ? 0 : 0xE0A030;
        Component headline = AgeViews.mb(stored);
        net.minecraft.resources.ResourceLocation cur = unit.shownRecipe();
        Component name = Component.translatable("gui.pioneers.steam_machine.status." + status);
        if (cur != null && status == SteamProcess.WORKING && tile.getLevel() != null) {
            TierRecipes.TierRecipe r = TierRecipes.byId(tile.getLevel(), unit.family, cur);
            if (r != null) {
                name = r.main().getHoverName();
                if (r.opened()) {
                    //1.0.5-beta (lot 47) : recette MKI ouverte a la vapeur : deux fois plus lente qu'a la machine MKI
                    rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.steam_mki.row"), Component.translatable("gui.pioneers.steam_mki"),
                          0xFFE0A030));
                }
            }
        }
        gui.info(g, l.screen.x(), l.screen.y(), l.screen.w(), l.screen.h(), chip, name, headline, status == SteamProcess.WORKING ? progress : -1,
              machine.accent, rows, false);
    }

    /** Multiplicateur de rendement : « 1 », « 1,5 », « 1,75 »… (au plus 3 decimales utiles). */
    static String yieldText(double y) {
        String t = String.format(Locale.ROOT, "%.3f", y).replaceAll("0+$", "").replaceAll("\\.$", "");
        return t.replace('.', ',');
    }

    /** Fleche horizontale qui se remplit avec l'avancement (clic : recettes dans JEI). */
    static void arrow(GuiGraphics g, Rect r, double ratio, int accent) {
        GuiMachine.jeiArrow = new Rect2i(r.x(), r.y() - 2, r.w(), r.h() + 4);
        int x = r.x(), y = r.y() + r.h() / 2;
        int len = r.w() - 5;
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

    /** Ecran d'etat de 48 px : pastille, etat, valeur cle (« % » ou « FE/t »), barre, puis 2 lignes « libelle : valeur ». */
    private static void screen(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, AtelierTile tile, Rect s, int status, double progress,
          long duration, int accent) {
        IndustryMachine m = tile.machine;
        boolean generator = m != null && m.isGenerator();
        int chip = status == ProcessUnit.WORKING ? accent : status == ProcessUnit.IDLE ? 0 : status == ProcessUnit.TIER ? 0xE04848 : 0xE0A030;
        long tierNeeded = gui.value(UiValues.TIER_NEEDED);
        Component name = tierNeeded >= 0 && tierNeeded <= 3 && status == ProcessUnit.TIER
                         ? Component.translatable("industry.pioneers.tier_required", tierName((int) tierNeeded))
                         //1.0.4-beta (lot 5) : generateur plein qui continue : « En marche · surplus perdu »
                         : generator && status == ProcessUnit.WORKING && gui.value(UiValues.SURPLUS) > 0 ? Component.translatable("industry.pioneers.status.surplus")
                         : Component.translatable("industry.pioneers.status." + status);
        Component headline = generator ? Component.literal(Texts.energy(gui.value(UiValues.PRODUCTION)) + "/t")
                             : status == ProcessUnit.TIER ? Component.empty() : GuiMachine.percent(progress);
        List<GuiMachine.Row> rows = new ArrayList<>();
        int tier = tile.tier.ordinal();
        if (m != null && m.kind == IndustryMachine.Kind.WATER) {
            //1.0.5-beta (lot 46) : bloc a eau : debit (mB/t) en tete et en ligne, consommation
            String rate = Texts.grouped(fr.pioneers.tech.industry.GeneratedLot46.WATER_RATE[tier]);
            headline = status == ProcessUnit.WORKING ? Component.translatable("gui.pioneers.water.rate_value", rate) : Component.empty();
            rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.info.using"), Component.literal(Texts.grouped(Tiers.power(tile.tier)) + " FE/t"),
                  CONSUMPTION));
            rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.water.rate"), Component.translatable("gui.pioneers.water.rate_value", rate),
                  PiMenuScreen.TEXT));
            gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, name, headline, -1, accent, rows, false);
            return;
        }
        if (m != null && m.kind == IndustryMachine.Kind.ROCKS) {
            //1.0.5-beta (lot 46) : generateur de roches : consommation, echantillon (nom de la roche) et cycle
            var sample = tile.toolSlot() == null ? net.minecraft.world.item.ItemStack.EMPTY : tile.toolSlot().getStack();
            rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.info.using"), Component.literal(Texts.grouped(Tiers.power(tile.tier)) + " FE/t"),
                  CONSUMPTION));
            rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.rocks.sample"), sample.isEmpty() ? Component.translatable("gui.pioneers.rocks.none")
                  : sample.getHoverName(), PiMenuScreen.TEXT));
            gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, name, headline, status == ProcessUnit.WORKING ? progress : -1, accent, rows, false);
            return;
        }
        if (generator) {
            rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.info.production"),
                  Component.literal(Texts.grouped(gui.value(UiValues.PRODUCTION)) + " FE/t"), CONSUMPTION));
        } else {
            rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.info.using"), Component.literal(Texts.grouped(Tiers.power(tile.tier)) + " FE/t"),
                  CONSUMPTION));
        }
        rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.api.screen.cycle"),
              Component.translatable("gui.pioneers.api.screen.cycle_value", seconds(duration), 1 << tier), PiMenuScreen.TEXT));
        gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, name, headline, status == ProcessUnit.WORKING ? progress : -1, accent, rows, false);
    }

    @Override
    public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
        SteamProcessTile steam = StorageViews.as(SteamProcessTile.class, be);
        if (steam != null) {
            AgeViews.slotTips(gui, mouseX, mouseY, steam.process().layout);
            return true;
        }
        AtelierTile tile = StorageViews.as(AtelierTile.class, be);
        if (tile != null) {
            StandardLayout l = tile.layout;
            gui.slotTip(mouseX, mouseY, l.batteryX, l.batteryY, "gui.pioneers.machine.battery");
            for (int[] p : l.itemIn) {
                gui.slotTip(mouseX, mouseY, p[0], p[1], "gui.pioneers.machine.in");
            }
            for (int[] p : l.itemOut) {
                gui.slotTip(mouseX, mouseY, p[0], p[1], "gui.pioneers.machine.out");
            }
        }
        return true;
    }

    /** 1.0.5-beta (lot 38) : onglet Infos d'une machine a vapeur : tier, famille, vapeur, reserve, vitesse et rendement par rapport au T0. */
    @Override
    public List<InfoRow> infoRows(GuiMachine gui, BlockEntity be) {
        SteamProcessTile steam = StorageViews.as(SteamProcessTile.class, be);
        if (steam == null) {
            return null;
        }
        List<InfoRow> rows = new ArrayList<>();
        rows.add(new InfoRow("global_tier", Component.translatable("tier.pioneers.global." + SteamProcess.TIER), 0xFF000000 | GeneratedTierFamilies.COLORS[1]));
        rows.add(new InfoRow("family", Component.translatable("tier.pioneers.family." + steam.process().family), PiMenuScreen.TEXT));
        rows.add(new InfoRow("steam_use", AgeViews.mbt(steam.process().steamUse), 0xFFE0A030));
        rows.add(new InfoRow("steam_reserve", AgeViews.mb(fr.pioneers.tech.bronze.steam.SteamRules.PROCESS_RESERVE), PiMenuScreen.TEXT));
        rows.add(new InfoRow("tier_speed", Component.translatable("gui.pioneers.tier.speed_value", 2), PiMenuScreen.TEXT));
        rows.add(new InfoRow("tier_yield", Component.translatable("gui.pioneers.tier.yield_value", yieldText(fr.pioneers.api.machine.PowerRules.yieldFactor(1, 0))),
              0xFF7BD87B));
        return rows;
    }

    @Override
    public String infoHelp() {
        return "gui.pioneers.api.info.help";
    }

    // ------------------------------------------------------------------
    // Titre, onglet Infos
    // ------------------------------------------------------------------

    /** Nom de la machine sans son tier (« Broyeur compact MKII » -> « Broyeur compact »). */
    static Component baseName(Component title) {
        String s = title.getString().replaceFirst("\\s+MK(I{1,3}|IV)$", "");
        return Component.literal(s);
    }

    /**
     * Titre a gauche (nom sans tier), pastille de tier a droite (couleur du tier) et pastille multibloc (trois cubes, infobulle : le
     * multibloc) quand la fiche en a un.
     */
    static void title(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, Component title, int width, int tier, @Nullable MachineSpec spec,
          @Nullable Component note) {
        Font font = gui.font();
        //1.0.5-beta (lot 48) : geometrie PURE de la barre de titre (TitleBar.layout), la meme que mesure le banc de l'API avec la vraie police
        //(tools/api/TitleBench : aucun titre sous la pastille, en francais et en anglais, sur tous les ecrans)
        Component chip = TitleBar.chip(gui.light(), tier, spec, age(gui, spec));
        TitleBar.Layout layout = TitleBar.layout(width, chip == null ? -1 : font.width(chip), spec != null && spec.multiblock() != null,
              note == null ? -1 : font.width(note));
        int bx = layout.chipX();
        //1.0.5-beta (lot 38) : pastille du TIER GLOBAL (« T3 · MKII », « T1 · Bronze », « T0 · Mecanique ») ; jamais sur le panneau clair de la
        //salle blanche (lot 31 : aucun tier)
        int global = TitleBar.global(gui.light(), tier, spec);
        if (global >= 0 && global <= 6) {
            Component label = globalChip(global);
            int color = GeneratedTierFamilies.COLORS[global];
            int bw = layout.chipW();
            g.fill(bx, 5, bx + bw, 15, 0xFF000000 | GuiDraw.darken(color, 0.55F));
            g.fill(bx, 14, bx + bw, 15, 0xFF000000 | color);
            GuiDraw.text(g, font, label, bx + 4, 7, 0.7F, 0xFFFFFFFF, bw - 6);
            if (gui.over(mouseX, mouseY, bx, 5, bw, 10)) {
                List<Component> tip = new ArrayList<>(List.of(Component.translatable("tier.pioneers.global." + global)));
                String family = spec == null || gui.machineBlock() == null ? null
                                : TierRecipes.familyOf(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(gui.machineBlock()).getPath());
                if (family != null) {
                    tip.add(Component.translatable("tooltip.pioneers.tier.family", Component.translatable("tier.pioneers.family." + family))
                          .withStyle(ChatFormatting.GRAY));
                }
                gui.tooltip(tip);
            }
        } else if (age(gui, spec) != null && !gui.light()) {
            //Lot 7 : machines de l'age mecanique et du bronze : pastille d'age au lieu du MK ; lot 8c : aussi les etablis (hors API, age de
            //leur place dans la progression : MachineRegistry.stationAge)
            String age = age(gui, spec);
            Component label = Component.translatable("gui.pioneers.api.age_chip." + age);
            int color = ageColor(age) & 0xFFFFFF;
            int bw = layout.chipW();
            g.fill(bx, 5, bx + bw, 15, 0xFF000000 | GuiDraw.darken(color, 0.55F));
            g.fill(bx, 14, bx + bw, 15, 0xFF000000 | color);
            GuiDraw.text(g, font, label, bx + 4, 7, 0.7F, 0xFFFFFFFF, bw - 6);
            if (gui.over(mouseX, mouseY, bx, 5, bw, 10)) {
                gui.tooltip(List.of(Component.translatable("tooltip.pioneers.api.age").append(" : ")
                      .append(Component.translatable("tooltip.pioneers.api.age." + age))));
            }
        }
        if (spec != null && spec.multiblock() != null) {
            int ox = bx - TitleBar.MULTI - 1;
            for (int[] d : new int[][]{{0, 4}, {5, 4}, {2, 0}}) {
                g.fill(ox + d[0], 5 + d[1], ox + d[0] + 5, 5 + d[1] + 5, 0xFF3FD0A0);
                g.fill(ox + d[0] + 1, 5 + d[1] + 1, ox + d[0] + 4, 5 + d[1] + 4, 0xFF1C5A48);
            }
            if (gui.over(mouseX, mouseY, ox, 5, 11, 10)) {
                gui.tooltip(List.of(Component.translatable("tooltip.pioneers.api.multiblock", Component.translatable("block.pioneers." + spec.multiblock()))
                      .withStyle(ChatFormatting.DARK_AQUA)));
            }
        }
        if (note != null) {
            //Lot 3 : texte court avant les pastilles (buffer d'objets : cases occupees)
            float nw = font.width(note) * 0.7F;
            GuiDraw.text(g, font, note, layout.left() + 2, 8, 0.7F, gui.panelDim(), nw + 1);
        }
        //1.0.4-beta (lot 26) : titre sombre sur le panneau clair des machines de salle blanche
        Component name = baseName(title);
        GuiDraw.text(g, font, name, TitleBar.X, 7, 1F, gui.panelText(), layout.room());
        //1.0.5-beta (lot 48) : titre resserre ou coupe par « … » (il ne passe jamais sous la pastille) : nom complet au survol
        if (TitleBar.shortened(font.width(name), layout.room()) && gui.over(mouseX, mouseY, TitleBar.X, 5, layout.room(), 10)) {
            gui.tooltip(List.of(title.copy()));
        }
    }

    /** 1.0.5-beta (lot 38) : tier global d'une fiche a un seul tier (age mecanique ou bronze, TIER, MKI seul) ; -1 sinon. */
    static int singleTier(@Nullable MachineSpec spec) {
        if (spec == null) {
            return -1;
        }
        return switch (spec.tiers()) {
            case AGE, TIER, SINGLE_MKI -> spec.globalTier(0);
            default -> -1;
        };
    }

    /** 1.0.5-beta (lot 38) : texte de la pastille d'un tier global : « T<n> · <nom> » (« T3 · MKII », « T1 · Bronze »). */
    static Component globalChip(int global) {
        return Component.translatable("tier.pioneers.global.short." + global).append(" · ").append(Component.translatable("tier.pioneers.name." + global));
    }

    /** Lot 7 : couleur d'un age (pastille du titre, onglet Infos ; memes teintes que l'infobulle standard ; lot 8c : age industriel). */
    static int ageColor(String age) {
        return 0xFF000000 | ("mechanical".equals(age) ? 0xC8945A : "industrial".equals(age) ? 0x8FA6B8 : 0xD89A50);
    }

    /** Lot 8c : age de la pastille du titre : celui de la fiche (tiers AGE), sinon celui d'un poste hors API (etablis), sinon null. */
    @Nullable
    static String age(GuiMachine gui, @Nullable MachineSpec spec) {
        if (spec != null) {
            return spec.tiers() == MachineSpec.TierMode.AGE ? spec.age() : null;
        }
        return gui.machineBlock() == null ? null : MachineRegistry.stationAge(gui.machineBlock());
    }

    /** Panneau de l'onglet Infos : par-dessus la zone machine seulement (jamais sur l'inventaire ni sur le carre). */
    static Rect infoRect(int width) {
        return new Rect(4, 16, width - 8, StandardLayout.ZONE - 18);
    }

    /** Lot 3 : une ligne de l'onglet Infos (cle {@code gui.pioneers.api.info.<key>}, valeur, couleur de la valeur). */
    record InfoRow(String key, Component value, int color) {}

    /**
     * Cadre des panneaux par-dessus la zone machine (onglets Infos et Structure) : ombre, bord, fond, filet d'accent en haut ; avale la souris.
     * 1.0.4-beta (lot 31) : salle blanche : panneau blanc (bord gris moyen), textes a l'encre sombre ({@link GuiMachine#ink}).
     */
    static void overlayPanel(GuiMachine gui, GuiGraphics g, int x, int y, int w, int h, int accent) {
        boolean light = gui.light();
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, light ? 0x40000000 : 0x78000000);
        g.fill(x, y, x + w, y + h, light ? GuiMachine.LIGHT_FRAME_DARK : 0xFF2A3440);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, light ? GuiMachine.PAPER : 0xFF121820);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFF000000 | accent);
        gui.swallow(x, y, w, h);
    }

    static void info(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be, int width, int recipes, MachineScreens.View view) {
        Font font = gui.font();
        Rect r = infoRect(width);
        int x = r.x(), y = r.y(), w = r.w(), h = r.h();
        overlayPanel(gui, g, x, y, w, h, gui.light() ? GuiSprites.CLEANROOM_INK : gui.accent());
        MachineRegistry.Entry entry = MachineRegistry.of(be.getBlockState().getBlock());
        MachineSpec spec = entry == null ? null : entry.spec();
        int tier = entry == null ? -1 : entry.tier();
        GuiDraw.text(g, font, Component.translatable("gui.pioneers.api.info.title").append(" · ").append(baseName(gui.getTitle())), x + 6, y + 5,
              0.75F, gui.ink(PiMenuScreen.TEXT), w - 12);
        List<Object[]> rows = new ArrayList<>();
        List<InfoRow> own = view.infoRows(gui, be);
        if (own != null) {
            //Lot 3 : lignes propres de la vue (buffers, radiateur, compresseur), apres le tier
            if (tier >= 0 && tier <= 3) {
                rows.add(new Object[]{"tier", Component.translatable("tier.pioneers.global." + (tier + 2)), 0xFF000000 | TIER_COLORS[tier]});
            }
            for (InfoRow row : own) {
                rows.add(new Object[]{row.key(), row.value(), row.color()});
            }
        } else if (tier >= 0 && tier <= 3) {
            rows.add(new Object[]{"tier", Component.translatable("tier.pioneers.global." + (tier + 2)), 0xFF000000 | TIER_COLORS[tier]});
            boolean generator = spec != null && spec.power() == MachineSpec.Power.FE_OUT;
            if (!generator) {
                rows.add(new Object[]{"consumption", Component.literal(Texts.grouped(Tiers.power(tier)) + " FE/t"), CONSUMPTION});
            }
            rows.add(new Object[]{"speed", Component.translatable("gui.pioneers.api.info.speed_value", 1 << tier), PiMenuScreen.TEXT});
        }
        if (own == null) {
            rows.add(new Object[]{"reserve", Component.literal(Texts.energy(gui.value(UiValues.CAPACITY))), PiMenuScreen.TEXT});
        }
        if (recipes > 0) {
            rows.add(new Object[]{"recipes", Component.translatable("gui.pioneers.api.info.recipes_value", recipes), PiMenuScreen.TEXT});
        }
        if (spec != null && own == null) {
            rows.add(new Object[]{"multiblock", spec.multiblock() == null ? Component.literal("—")
                                                : Component.translatable("block.pioneers." + spec.multiblock()), spec.multiblock() == null ? PiMenuScreen.DIM : 0xFF3FD0A0});
            Set<String> faces = new LinkedHashSet<>();
            for (MachineSpec.PortSpec p : spec.ports()) {
                faces.add(p.resource().name().toLowerCase(Locale.ROOT));
            }
            if (spec.battery()) {
                faces.add(SideType.ITEMS.name().toLowerCase(Locale.ROOT));
            }
            MutableComponent list = Component.empty();
            boolean first = true;
            for (SideType t : SideType.values()) {
                String k = t.name().toLowerCase(Locale.ROOT);
                if (faces.contains(k)) {
                    list.append(first ? Component.empty() : Component.literal(", ")).append(Component.translatable("tooltip.pioneers.api.side." + k));
                    first = false;
                }
            }
            rows.add(new Object[]{"faces", list, PiMenuScreen.TEXT});
        }
        if (spec != null && own != null) {
            MutableComponent list = Component.empty();
            for (int i = 0; i < spec.ports().size(); i++) {
                list.append(i == 0 ? Component.empty() : Component.literal(", "))
                      .append(Component.translatable("tooltip.pioneers.api.side." + spec.ports().get(i).resource().name().toLowerCase(Locale.ROOT)));
            }
            rows.add(new Object[]{"faces", list, PiMenuScreen.TEXT});
        }
        int yy = y + 18;
        for (Object[] row : rows) {
            Component label = Component.translatable("gui.pioneers.api.info." + row[0]);
            Component value = (Component) row[1];
            float vw = Math.min(font.width(value) * 0.65F, w * 0.62F);
            GuiDraw.text(g, font, label, x + 8, yy, 0.6F, gui.ink(PiMenuScreen.DIM), Math.max(1, w - 24 - vw));
            GuiDraw.text(g, font, value, x + w - 8 - vw, yy - 0.5F, 0.65F, gui.ink((int) row[2]), vw + 1);
            yy += 9;
            if (yy > y + h - 20) {
                break;
            }
        }
        //1.0.4-beta (lot 34) : bloc libre de la vue sous les lignes (diagnostic du comptoir d'entrepot)
        view.infoExtra(gui, g, be, x + 8, yy + 3, w - 16, y + h - 20);
        g.fill(x + 6, y + h - 17, x + w - 6, y + h - 16, gui.light() ? GuiMachine.LIGHT_SEPARATOR : 0xFF24333E);
        if (own == null) {
            GuiDraw.text(g, font, Component.translatable("industry.pioneers.gui.speed_rule"), x + 8, y + h - 13, 0.55F, gui.ink(0xFF59666A), w - 16);
        }
        //1.0.4-beta (lot 31) : salle blanche : aide sans « selon le tier » (sa capacite est fixe)
        String help = view.infoHelp();
        if (gui.light() && "gui.pioneers.multiblock.info_help".equals(help)) {
            help = "gui.pioneers.multiblock.info_help_cleanroom";
        }
        GuiDraw.text(g, font, Component.translatable(help), x + 8, y + h - 7, 0.55F, gui.ink(0xFF59666A), w - 16);
    }

    private static Component tierName(int tier) {
        return Component.translatable("industry.pioneers.tier." + Tiers.tier(tier).id());
    }

    private static String seconds(long ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }
}

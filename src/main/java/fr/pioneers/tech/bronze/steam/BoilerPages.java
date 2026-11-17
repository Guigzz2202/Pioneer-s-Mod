package fr.pioneers.tech.bronze.steam;

import fr.pioneers.api.multiblock.GeneratedHouseNbt;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 1.0.3-beta (lot 14) : CONTENU de la page JEI « Chaudiere a vapeur » (age du bronze), SANS Minecraft : lu par
 * {@code J:integration/jei/BoilerJei} (qui pose les objets et traduit) et par le banc {@code tools/bronze/SteamBench} (planche hors jeu,
 * memes textes et memes chiffres). Tous les chiffres viennent de {@link SteamRules}, {@link BoilerPart} et {@link AccessoryTile}. 1.0.4-beta
 * (lot 24) : la chaudiere a la FORME FIXE de Guillaume ({@link GeneratedHouseNbt}) : ses chiffres sont ceux de ses {@link #SHELLS} corps
 * (avant : 2 corps de reference, lignes 1 / 2 / 4 corps).
 */
public final class BoilerPages {

    /** Cle de langue et ses arguments (deja mis en forme). */
    public record Line(String key, Object... args) {
    }

    /**
     * Une page : cases (ids d'objets par case, alternatives qui defilent ; {@code #steam_machines} = les machines de traitement a vapeur),
     * cle du titre, lignes.
     */
    public record Page(List<List<String>> slots, String title, List<Line> lines) {
    }

    public static final String K = "jei.pioneers.boiler.", MACHINES = "#steam_machines";
    private static final String FIREBOX = "pioneers:firebox", COPPER = "pioneers:copper_boiler_shell", STEEL = "pioneers:steel_boiler_shell",
          DOME = "pioneers:steam_dome", CHIMNEY = "pioneers:chimney", SAFETY = "pioneers:safety_valve";

    /** Nombre de corps de la structure de Guillaume (cases 'o'). */
    public static final int SHELLS = shells();

    private BoilerPages() {
    }

    private static int shells() {
        GeneratedHouseNbt.Shape s = GeneratedHouseNbt.of("bronze_boiler");
        return s == null ? 2 : (int) s.layout().chars().filter(c -> c == 'o').count();
    }

    private static Line l(String key, Object... args) {
        return new Line(K + key, args);
    }

    static String f1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    static String sec(int ticks) {
        return ticks < 0 ? "-" : Math.round(ticks / 20.0) + " s";
    }

    /** Quantite en mB groupee ({@code Texts.grouped}, sans Minecraft). */
    static String mb(double v) {
        String s = String.format(Locale.ROOT, "%,d", Math.round(v)).replace(',', ' ');
        return s + " mB";
    }

    /** Tirage de la meilleure cheminee (8 blocs). */
    static double best() {
        return SteamRules.draft(BoilerPart.MAX_CHIMNEY);
    }

    /** Les 5 pages fixes : structure, cheminee, nombre de corps, surete, consommateurs. */
    public static List<Page> pages() {
        List<Page> out = new ArrayList<>();
        out.add(new Page(List.of(List.of(FIREBOX), List.of(COPPER, STEEL), List.of(DOME), List.of(CHIMNEY), List.of(SAFETY)), K + "structure",
              List.of(l("structure.1", SHELLS), l("structure.2", mb(BoilerPart.WATER)), l("structure.3"))));
        List<Line> draft = new ArrayList<>();
        draft.add(l("draft.head", SHELLS));
        for (int ch : new int[]{0, 4, 8}) {
            double d = SteamRules.draft(ch);
            draft.add(l("draft.row", ch, String.format(Locale.ROOT, "%.2f", d), f1(SteamRules.boilerNet(d, 0, SHELLS)), f1(SteamRules.boilerNet(d, 60, SHELLS))));
        }
        draft.add(l("draft.note"));
        out.add(new Page(List.of(List.of(CHIMNEY), List.of(FIREBOX)), K + "draft", draft));
        List<Line> sh = new ArrayList<>();
        double heat = SteamRules.fireHeat(best(), 60);
        sh.add(l("shells.head", f1(SteamRules.boilerNet(best(), 60, SHELLS)), mb(BoilerPart.WATER)));
        for (int n : new int[]{SHELLS}) {
            double water = n * BoilerPart.WATER, net = SteamRules.boilerNet(best(), 60, n);
            sh.add(l("shells.row", n, sec(SteamRules.heatUpTicks(n, water, heat)), sec((int) Math.round(water / Math.max(0.01, net / SteamRules.STEAM_PER_WATER)))));
        }
        sh.add(l("shells.steel", mb(SteamRules.COPPER_SHELL_STEAM), mb(SteamRules.STEEL_SHELL_STEAM), mb(SteamRules.DOME_STEAM)));
        out.add(new Page(List.of(List.of(COPPER, STEEL), List.of(DOME)), K + "shells", sh));
        out.add(new Page(List.of(List.of(SAFETY), List.of(FIREBOX)), K + "safety",
              List.of(l("safety.1", AccessoryTile.DEFAULT_CUTOFF + " %", AccessoryTile.MIN_CUTOFF + " %", AccessoryTile.MAX_CUTOFF + " %"),
                    l("safety.2", AccessoryTile.RESUME_GAP), l("safety.3"))));
        double wood = SteamRules.boilerNet(best(), 0, SHELLS), coal = SteamRules.boilerNet(best(), 60, SHELLS);
        List<Line> use = new ArrayList<>();
        use.add(l("use.head", f1(wood), f1(coal)));
        Object[][] rows = {{"use.engine", SteamRules.ENGINE_FULL_USE}, {"use.process", SteamRules.PROCESS_USE}, {"use.blower", SteamRules.BLOWER_USE},
              {"use.drill", SteamRules.DRILL_USE}};
        for (Object[] r : rows) {
            double mbt = (double) r[1];
            use.add(l((String) r[0], f1(mbt), f1(wood / mbt), f1(coal / mbt)));
        }
        out.add(new Page(List.of(List.of("pioneers:steam_engine"), List.of(MACHINES), List.of("pioneers:steam_blower"), List.of("pioneers:steam_drill")),
              K + "use", use));
        return out;
    }

    /** Lignes de la page d'un groupe de combustibles ({@code burn} ticks de feu, bonus de chaleur en %). Titre : {@code K + "fuel"}. */
    public static List<Line> fuel(int burn, int bonus) {
        double steam = SteamRules.steamPerFuel(burn, bonus);
        return List.of(l("fuel.steam", mb(steam), mb(steam / SteamRules.STEAM_PER_WATER)),
              l("fuel.time", sec((int) Math.round(burn / SteamRules.draft(0))), sec((int) Math.round(burn / best()))),
              bonus > 0 ? l("fuel.bonus", bonus + " %") : l("fuel.no_bonus", "60 %", "30 %", "80 %"));
    }
}

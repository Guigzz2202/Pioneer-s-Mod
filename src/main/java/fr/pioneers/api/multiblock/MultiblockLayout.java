package fr.pioneers.api.multiblock;

import fr.pioneers.api.machine.StandardLayout.Part;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.recipe.Remanents;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * API Multibloc (lot 5) : DISPOSITION de l'interface v2 d'un controleur de multibloc, dans la grammaire de {@link StandardLayout} (maquette
 * validee scratchpad/api/planches/planche_multibloc.png et apilot1/planches/planche_api1_multibloc.png) : panneau de 196 au moins, zone
 * machine {@link StandardLayout#ZONE} (inventaire DANS le cadre dessous), colonne de gauche energie + thermometre + caloporteur, jauges
 * d'entree (fluides, gaz, donnees), cases d'entree (3 colonnes), case « Recette » au-dessus de la fleche, cases de sortie (2 colonnes, 3
 * au-dela de 6), jauges de sortie puis caloporteur use / vapeur ; ecran d'etat pleine largeur ({@link StandardLayout#screen}). Contenu
 * centre comme celui des ateliers.
 * <p>
 * Lot 6 : les reacteurs, la fusion et le calcul passent aussi a cette interface (« interface standard meme si speciale pour eux ») : memes
 * colonnes, jauges, cases, ecran et onglets ; parties speciales : la rangee de commandes {@link #controls} (reacteurs : pilote, barres, arret
 * d'urgence, rearmement, arret automatique ; fusion : chauffer, couper, risque) entre les jauges, raccourcies a {@link #CORE_GAUGE_H}, et
 * l'ecran d'etat ; aucune case « Recette » (pas de selecteur).
 */
public final class MultiblockLayout {

    public static final int THERMO_W = 8;
    /** Lot 6 : jauges des controleurs a commandes (reacteurs, fusion) ; rangee de commandes (haut, hauteur) ; largeur minimale. */
    public static final int CORE_GAUGE_H = 46, CONTROLS_Y = StandardLayout.TOP + CORE_GAUGE_H + 4, CONTROLS_H = 14, CORE_WIDTH = 264;

    public final Rect energy, thermometer, coolant, coolantOut, arrow, screen;
    public final List<Rect> fluidIn = new ArrayList<>(), gasIn = new ArrayList<>(), dataIn = new ArrayList<>(), fluidOut = new ArrayList<>(),
          gasOut = new ArrayList<>();
    public final List<int[]> itemIn = new ArrayList<>(), itemOut = new ArrayList<>();
    public final int recipeX, recipeY, width, height, inputs, outputs;
    /** 1.0.5-beta (lot 44) : case « Outil » des remanents (coin de l'objet), sous la fleche ; {@link #tool} : le controleur en a une. */
    public final int toolX, toolY;
    public final boolean tool;
    /** Lot 6 : rangee de commandes (reacteurs, fusion), sinon null ; hauteur des jauges ; case « Recette » (selecteur de recette). */
    public final Rect controls;
    public final int gaugeH;
    public final boolean recipe;

    private static final Map<IndustryMachine, MultiblockLayout> CACHE = new EnumMap<>(IndustryMachine.class);

    /** Lot 6 : controleur a coeur propre declare par sa fiche (reacteur, fusion, calcul) : pas de selecteur de recette. */
    public static boolean core(IndustryMachine m) {
        MultiblockSpec spec = MultiblockRegistry.of(m);
        return spec != null && ("reactor".equals(spec.process()) || "fusion".equals(spec.process()) || "compute".equals(spec.process()));
    }

    /** Lot 6 : controleur a rangee de commandes (reacteurs, fusion). */
    public static boolean controlled(IndustryMachine m) {
        MultiblockSpec spec = MultiblockRegistry.of(m);
        return spec != null && ("reactor".equals(spec.process()) || "fusion".equals(spec.process()));
    }

    public static MultiblockLayout of(IndustryMachine machine) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(machine, MultiblockLayout::new);
        }
    }

    /** Cases tampon d'objets : 6 (ou 9 des 3 sortes d'objets) ; aucune si la machine n'en prend pas (meme regle que l'ecran d'avant). */
    static int buffer(int kinds) {
        return kinds == 0 ? 0 : kinds <= 2 ? 6 : 9;
    }

    private MultiblockLayout(IndustryMachine m) {
        boolean controlled = controlled(m);
        recipe = !core(m);
        int top = StandardLayout.TOP, gh = controlled ? CORE_GAUGE_H : StandardLayout.GAUGE_H, gw = StandardLayout.GAUGE_W, slot = StandardLayout.SLOT;
        gaugeH = gh;
        inputs = m.kind == IndustryMachine.Kind.DRILL ? 0 : buffer(m.itemsIn);
        outputs = m.kind == IndustryMachine.Kind.DRILL ? 9 : buffer(m.itemsOut);
        int x = 8;
        energy = new Rect(x, top, gw, gh);
        x += gw + 4;
        Rect thermo = new Rect(x, top, THERMO_W, gh);
        x += THERMO_W + 4;
        Rect cool = new Rect(x, top, gw, gh);
        x += gw + 8;
        List<Rect> fi = new ArrayList<>(), gi = new ArrayList<>(), di = new ArrayList<>(), fo = new ArrayList<>(), go = new ArrayList<>();
        List<int[]> ii = new ArrayList<>(), io = new ArrayList<>();
        for (int i = 0; i < m.fluidsIn; i++, x += gw + 3) {
            fi.add(new Rect(x, top, gw, gh));
        }
        for (int i = 0; i < m.gasesIn; i++, x += gw + 3) {
            gi.add(new Rect(x, top, gw, gh));
        }
        for (int i = 0; i < ("synthesizer".equals(m.key) ? 3 : 0); i++, x += gw + 3) {
            di.add(new Rect(x, top, gw, gh));
        }
        if (!fi.isEmpty() || !gi.isEmpty() || !di.isEmpty()) {
            x += 4;
        }
        int cols = Math.min(3, Math.max(1, inputs));
        int rows = (inputs + cols - 1) / cols;
        int y0 = top + (gh - Math.max(1, rows) * slot) / 2 + 1;
        for (int i = 0; i < inputs; i++) {
            ii.add(new int[]{x + 1 + (i % cols) * slot, y0 + (i / cols) * slot});
        }
        if (inputs > 0) {
            x += cols * slot + 4;
        }
        int arrowX = x;
        x += 32;
        //Lot 6 : jauges raccourcies des controleurs a commandes : sorties sur 3 colonnes (2 rangees)
        int ocols = outputs > 6 || controlled && outputs > 4 ? 3 : Math.min(2, Math.max(1, outputs));
        int orows = (outputs + ocols - 1) / ocols;
        int oy0 = top + (gh - Math.max(1, orows) * slot) / 2 + 1;
        for (int i = 0; i < outputs; i++) {
            io.add(new int[]{x + 1 + (i % ocols) * slot, oy0 + (i / ocols) * slot});
        }
        if (outputs > 0) {
            x += ocols * slot + 6;
        }
        for (int i = 0; i < (m.kind == IndustryMachine.Kind.TANK ? 0 : m.fluidsOut); i++, x += gw + 3) {
            fo.add(new Rect(x, top, gw, gh));
        }
        for (int i = 0; i < (m.kind == IndustryMachine.Kind.TANK ? 0 : m.gasesOut); i++, x += gw + 3) {
            go.add(new Rect(x, top, gw, gh));
        }
        Rect coolOut = new Rect(x, top, gw, gh);
        x += gw;
        width = Math.max(controlled ? CORE_WIDTH : StandardLayout.WIDTH, x + 8);
        //Centrage du contenu (sauf la colonne de gauche : energie, thermometre, caloporteur) dans la place qui reste a droite
        int dx = Math.max(0, (width - (x + 8)) / 2);
        for (List<Rect> list : List.of(fi, gi, di, fo, go)) {
            list.replaceAll(r -> new Rect(r.x() + dx, r.y(), r.w(), r.h()));
        }
        fluidIn.addAll(fi);
        gasIn.addAll(gi);
        dataIn.addAll(di);
        fluidOut.addAll(fo);
        gasOut.addAll(go);
        ii.forEach(p -> itemIn.add(new int[]{p[0] + dx, p[1]}));
        io.forEach(p -> itemOut.add(new int[]{p[0] + dx, p[1]}));
        thermometer = thermo;
        coolant = cool;
        coolantOut = new Rect(coolOut.x() + dx, coolOut.y(), coolOut.w(), coolOut.h());
        arrow = new Rect(arrowX + dx, top + gh / 2 - 4, 28, 8);
        recipeX = arrow.x() + (arrow.w() - 16) / 2;
        recipeY = top + 4;
        tool = recipe && inputs > 0 && Remanents.hasToolSlot(m.key);
        toolX = recipeX;
        toolY = top + gh - 20;
        screen = StandardLayout.screen(width);
        controls = controlled ? new Rect(8, CONTROLS_Y, width - 16, CONTROLS_H) : null;
        height = StandardLayout.ZONE;
    }

    /** Onglet Structure / Infos : par-dessus la zone machine seulement. */
    public Rect panel() {
        return new Rect(4, 16, width - 8, StandardLayout.ZONE - 18);
    }

    /** Elements de l'ecran (geometrie controlee par tools/check_machine_api.py : rien ne se chevauche, tout dans la zone machine). */
    public List<Part> parts() {
        List<Part> out = new ArrayList<>();
        for (Rect r : List.of(energy, thermometer, coolant)) {
            out.add(new Part(r == energy ? "energy" : r == thermometer ? "thermometer" : "coolant", new Rect(r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2)));
        }
        for (List<Rect> list : List.of(fluidIn, gasIn, dataIn, fluidOut, gasOut)) {
            for (Rect r : list) {
                out.add(new Part("gauge", new Rect(r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2)));
            }
        }
        out.add(new Part("coolant_out", new Rect(coolantOut.x() - 1, coolantOut.y() - 1, coolantOut.w() + 2, coolantOut.h() + 2)));
        for (int[] p : itemIn) {
            out.add(new Part("slot_in", StandardLayout.slotFrame(p[0], p[1])));
        }
        for (int[] p : itemOut) {
            out.add(new Part("slot_out", StandardLayout.slotFrame(p[0], p[1])));
        }
        if (recipe) {
            out.add(new Part("recipe", StandardLayout.slotFrame(recipeX, recipeY)));
        }
        if (tool) {
            out.add(new Part("slot_tool", StandardLayout.slotFrame(toolX, toolY)));
        }
        if (controls != null) {
            out.add(new Part("controls", controls));
        }
        out.add(new Part("arrow", arrow));
        out.add(new Part("screen", screen));
        return out;
    }
}

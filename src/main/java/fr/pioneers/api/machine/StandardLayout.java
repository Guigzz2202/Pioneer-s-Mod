package fr.pioneers.api.machine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API Machine (lot 2) : DISPOSITION DE L'INTERFACE STANDARD v2 (maquettes validees par Guillaume : scratchpad/apilot1/planches), calculee
 * d'apres les cases et reservoirs de la machine, partagee par la tile (positions des cases, case « Recette ») et l'ecran (jauges, fleche,
 * ecran d'etat, cadre de l'inventaire, carre du selecteur, onglets). Code commun : aucune classe du client.
 * <p>
 * Zone machine de {@link #ZONE} : de gauche a droite, energie (batterie dessous), jauges d'entree (fluides puis gaz), cases d'entree
 * (3 colonnes, 4 au-dela de 9), case « Recette » au-dessus de la fleche, cases de sortie (2 colonnes au plus), jauges de sortie ; ecran
 * d'etat de {@link #SCREEN_H} sur toute la largeur. Dessous, l'inventaire du joueur DANS un cadre interieur ({@link #INVENTORY} = 108 depuis le lot 6 de la 1.0.3 : glissiere,
 * libelle, 3 rangees, barre d'action ; rien ne depasse du panneau). Onglets {@link #TAB} x {@link #TAB} sur le bord droit.
 * <p>
 * Lot 3 (retour de Guillaume apres le test du lot 2 : « que le carre soit ouvrable / fermable et aille au-dessus de l'interface machine
 * pour optimiser la place ») : le carre du selecteur de recette ({@link #SQ} x {@link #SQ}) n'est plus colle a gauche du panneau ; ouvert,
 * il se pose PAR-DESSUS la zone machine ({@link #squareOver}), au calque du dessus ; ferme, il ne prend aucune place.
 * <p>
 * Lot 4 (retour de Guillaume apres le test du lot 3 : « reduis la taille des icones sur le cote ; que le cadre vert soit plus propre et
 * au-dessus de l'inventaire (agrandir la fenetre au global pour mieux distribuer les elements), puis centrer l'interface ») : panneau plus
 * large ({@link #WIDTH} 196 au lieu de 176) et zone machine plus haute ({@link #ZONE} 152 au lieu de 130) : le carre tient ENTIEREMENT sous
 * le titre et au-dessus du cadre de l'inventaire, sans le toucher ({@link #SQ_TOP}, marge {@link #SQ_MARGIN}) ; jauges de 60, ecran d'etat
 * de 52, ecarts elargis ; onglets de 18 (icones de 12). Toute la geometrie d'une interface v2 est decrite par des {@link Part} (exportees
 * par le banc dans screens.json, verifiees sans chevauchement par {@code tools/check_machine_api.py}).
 * <p>
 * Remplace {@code AtelierLayout} (zone de 112, ecran de 30, inventaire de 90 hors cadre).
 */
public final class StandardLayout {

    public record Rect(int x, int y, int w, int h) {

        /** Les deux rectangles se recouvrent-ils (un bord commun ne compte pas) ? */
        public boolean overlaps(Rect o) {
            return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h;
        }
    }

    /** Lot 4 : un element de l'interface v2 (role : energy, battery, slot, gauge, arrow, recipe, screen, card, setting...) et sa place. */
    public record Part(String role, Rect r) {}

    /** Nombre de cases et de reservoirs de chaque sorte (entrees, sorties). */
    public record Counts(int itemsIn, int itemsOut, int fluidsIn, int gasesIn, int fluidsOut, int gasesOut, boolean pairedIn, boolean pairedOut) {

        public Counts(int itemsIn, int itemsOut, int fluidsIn, int gasesIn, int fluidsOut, int gasesOut) {
            this(itemsIn, itemsOut, fluidsIn, gasesIn, fluidsOut, gasesOut, false, false);
        }
    }

    /** Largeur minimale du panneau (lot 4 : 196 ; l'inventaire de 176 est centre dans son cadre). */
    public static final int WIDTH = 196;
    public static final int TOP = 22, GAUGE_W = 12, GAUGE_H = 60, SLOT = 18;
    /** Zone machine (titre, jauges, cases, ecran d'etat) ; ecran d'etat (haut, hauteur) ; bande de l'inventaire dans le cadre. */
    public static final int ZONE = 152, SCREEN_Y = TOP + GAUGE_H + 8, SCREEN_H = 52, INVENTORY = 108;
    /**
     * 1.0.3-beta (lot 6, Guillaume : « il manque de la place en hauteur, la partie inventaire est superposee avec le design de
     * l'interface ») : la bande de l'inventaire commence par une GLISSIERE de {@link #SEPARATOR} px (bas de l'alcove de la machine, cerclage
     * du tier) ; le cadre de l'inventaire vient DESSOUS (avant : cadre pose sur la glissiere, bande de 98). Les cases du joueur gardent leur
     * place depuis le bas du panneau ({@code MachineMenu} : hauteur - 82).
     */
    public static final int SEPARATOR = 10;
    /**
     * Habillage du panneau (texture {@code panel_<tier>} 176 x 176 de {@code tools/gc/core/gui_machine.py}, zone machine de 86) en bandes
     * verticales : rangees de la texture {@code [debut, fin[} -> place dans le panneau. Titre {@code 0..24} garde ; alcove de la machine
     * {@code 24..79} etiree jusqu'au bas de la zone ; bas de l'alcove + glissiere {@code 79..89} = la glissiere de la bande ; alcove de
     * l'inventaire {@code 89..172} etiree sous le cadre ; plinthe {@code 172..176} gardee.
     */
    public static final int PANEL_TEX = 176, PANEL_TITLE = 24, PANEL_ALCOVE_END = 79, PANEL_INV_START = 89, PANEL_PLINTH = 172;
    /** Reserve d'energie (hauteur) ; case de la batterie dessous (meme place pour toutes les machines de l'interface v2). */
    public static final int ENERGY_H = 40, BATTERY_X = 6, BATTERY_Y = TOP + ENERGY_H + 4;
    /** Carre du selecteur : cote, haut (sous la bande du titre), marge au bord droit ; onglets : cote, premier en haut, pas. */
    public static final int SQ = 130, SQ_TOP = 17, SQ_MARGIN = 6, TAB = 18, TAB_Y = 14, TAB_STEP = 20;
    /** Cadre de l'inventaire : libelle (decalage depuis le haut de la bande), premiere rangee, barre d'action. */
    public static final int INV_LABEL = SEPARATOR + 5, INV_ROWS = SEPARATOR + 16, INV_HOTBAR = SEPARATOR + 74;

    // -- Buffers a jauge (lot 3, places du lot 4) : grande jauge, carte du contenu, cases des recipients (entree en haut, sortie en bas)
    public static final Rect BUFFER_GAUGE = new Rect(10, TOP, 24, GAUGE_H), BUFFER_CARD = new Rect(42, TOP, 118, GAUGE_H);
    public static final int IO_X = 172, IN_Y = TOP + 2, OUT_Y = TOP + GAUGE_H - 18;

    /** Carte du contenu d'un buffer : jusqu'aux cases des recipients, ou toute la largeur s'il n'en a pas (chaleur, air). */
    public static Rect bufferCard(boolean io) {
        return io ? BUFFER_CARD : new Rect(BUFFER_CARD.x(), TOP, WIDTH - 8 - BUFFER_CARD.x(), GAUGE_H);
    }
    // -- Buffer d'objets : premiere case (9 colonnes), marge sous la derniere rangee
    public static final int ITEMS_X = 8 + (WIDTH - 176) / 2, ITEMS_Y = 20, ITEMS_BOTTOM = 8;
    // -- Radiateur, compresseur : thermometre / manometre, colonne des reglages (legende 7 px au-dessus de chaque reglage de 14 px)
    public static final Rect TALL = new Rect(26, TOP, GAUGE_W, GAUGE_H);
    public static final int SET_X = 46, SET_W = WIDTH - 8 - SET_X, STEP_A = TOP + 10, STEP_B = TOP + 38, STEP_H = 14;

    // -- Machines a reglages ou a mesures (spatiales, rail, sous-station...) : colonne a droite de l'energie (lot 4)
    public static final int PANEL_X = 26;

    public final Rect energy;
    public final int batteryX, batteryY;
    public final List<Rect> fluidIn = new ArrayList<>(), gasIn = new ArrayList<>(), fluidOut = new ArrayList<>(), gasOut = new ArrayList<>();
    /** Coin haut-gauche des cases (16 x 16, comme les cases vanilla). */
    public final List<int[]> itemIn = new ArrayList<>(), itemOut = new ArrayList<>();
    public final Rect arrow, screen;
    /** Case « Recette » (coin de l'objet), au-dessus de la fleche. */
    public final int recipeX, recipeY;
    /** 1.0.5-beta (lot 44) : case « Outil » des remanents (coin de l'objet), SOUS la fleche (machines qui en ont une). */
    public final int toolX, toolY;
    /** 1.0.5-beta (lot 44) : place de la case Outil sous la fleche (bas des jauges - 20). */
    public static final int TOOL_Y = TOP + GAUGE_H - 20;
    /** Largeur du panneau ; hauteur de la zone machine (au-dessus de l'inventaire). */
    public final int width, height;

    private static final Map<Counts, StandardLayout> CACHE = new HashMap<>();

    public static StandardLayout of(Counts counts) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(counts, StandardLayout::new);
        }
    }

    private StandardLayout(Counts m) {
        int x = 8;
        energy = new Rect(x, TOP, GAUGE_W, ENERGY_H);
        batteryX = BATTERY_X;
        batteryY = BATTERY_Y;
        x += GAUGE_W + 8;
        List<Rect> fi = new ArrayList<>(), gi = new ArrayList<>(), fo = new ArrayList<>(), go = new ArrayList<>();
        List<int[]> ii = new ArrayList<>(), io = new ArrayList<>();
        //1.0.6 : jauges partagees (liquide ou gaz) : le reservoir de liquide i et celui de gaz i ont la meme jauge
        for (int i = 0; i < m.fluidsIn(); i++, x += GAUGE_W + 3) {
            fi.add(new Rect(x, TOP, GAUGE_W, GAUGE_H));
            if (m.pairedIn()) {
                gi.add(fi.get(i));
            }
        }
        for (int i = 0; i < (m.pairedIn() ? 0 : m.gasesIn()); i++, x += GAUGE_W + 3) {
            gi.add(new Rect(x, TOP, GAUGE_W, GAUGE_H));
        }
        if (m.fluidsIn() + m.gasesIn() > 0) {
            x += 4;
        }
        //9 entrees (assembleur) en 3 x 3, 12 (placeuse de composants) en 4 x 3
        int cols = m.itemsIn() > 9 ? 4 : Math.min(3, Math.max(1, m.itemsIn()));
        int rows = (m.itemsIn() + cols - 1) / Math.max(1, cols);
        int y0 = TOP + (GAUGE_H - Math.max(1, rows) * SLOT) / 2 + 1;
        for (int i = 0; i < m.itemsIn(); i++) {
            ii.add(new int[]{x + 1 + (i % cols) * SLOT, y0 + (i / cols) * SLOT});
        }
        if (m.itemsIn() > 0) {
            x += cols * SLOT + 4;
        }
        int arrowX = x;
        x += 32;
        int ocols = Math.min(2, Math.max(1, m.itemsOut()));
        int orows = (m.itemsOut() + ocols - 1) / Math.max(1, ocols);
        int oy0 = TOP + (GAUGE_H - Math.max(1, orows) * SLOT) / 2 + 1;
        for (int i = 0; i < m.itemsOut(); i++) {
            io.add(new int[]{x + 1 + (i % ocols) * SLOT, oy0 + (i / ocols) * SLOT});
        }
        if (m.itemsOut() > 0) {
            x += ocols * SLOT + 6;
        }
        for (int i = 0; i < m.fluidsOut(); i++, x += GAUGE_W + 3) {
            fo.add(new Rect(x, TOP, GAUGE_W, GAUGE_H));
            if (m.pairedOut()) {
                go.add(fo.get(i));
            }
        }
        for (int i = 0; i < (m.pairedOut() ? 0 : m.gasesOut()); i++, x += GAUGE_W + 3) {
            go.add(new Rect(x, TOP, GAUGE_W, GAUGE_H));
        }
        width = Math.max(WIDTH, x + 8);
        //Centrage horizontal du contenu (sauf l'energie et la batterie) dans la place qui reste a droite de l'energie
        int dx = Math.max(0, (width - (x + 8)) / 2);
        for (List<Rect> list : List.of(fi, gi, fo, go)) {
            list.replaceAll(r -> new Rect(r.x() + dx, r.y(), r.w(), r.h()));
        }
        fluidIn.addAll(fi);
        gasIn.addAll(gi);
        fluidOut.addAll(fo);
        gasOut.addAll(go);
        ii.forEach(p -> itemIn.add(new int[]{p[0] + dx, p[1]}));
        io.forEach(p -> itemOut.add(new int[]{p[0] + dx, p[1]}));
        arrow = new Rect(arrowX + dx, TOP + GAUGE_H / 2 - 4, 28, 8);
        recipeX = arrow.x() + (arrow.w() - 16) / 2;
        recipeY = TOP + 4;
        toolX = recipeX;
        toolY = TOOL_Y;
        screen = screen(width);
        height = ZONE;
    }

    /** Ecran d'etat pleine largeur d'un panneau de largeur {@code width}. */
    public static Rect screen(int width) {
        return new Rect(8, SCREEN_Y, width - 16, SCREEN_H);
    }

    /** Hauteur totale du panneau (zone machine + inventaire dans le cadre). */
    public int panelHeight() {
        return height + INVENTORY;
    }

    /**
     * Carre du selecteur OUVERT, relatif au panneau de largeur {@code width} : par-dessus la zone machine, sous la bande du titre (le nom
     * et la pastille de tier restent visibles), contre le bord droit (cote des onglets, dont celui qui l'ouvre), au-dessus du cadre de
     * l'inventaire sans le toucher (lot 4).
     */
    public static Rect squareOver(int width) {
        return new Rect(width - SQ - SQ_MARGIN, SQ_TOP, SQ, SQ);
    }

    /** Cadre interieur de l'inventaire (relatif au panneau), pour une zone machine de hauteur {@code zone}. */
    public static Rect inventoryFrame(int width, int zone) {
        if (width > WIDTH) {
            //1.0.4-beta (lot 34, Guillaume : « [l'interface du comptoir] est cassee » : « le bas ne fait pas la meme largeur que le haut ») :
            //panneau plus large que le standard : le cadre de l'inventaire prend toute la largeur de l'alcove, comme la zone machine au-dessus
            //(avant : 170 px centres, deux grands vides sombres de part et d'autre) ; les cases du joueur restent centrees dedans
            return new Rect(WIDE_FRAME_X, zone + SEPARATOR + 1, width - 2 * WIDE_FRAME_X, INVENTORY - SEPARATOR - 5);
        }
        int x0 = 8 + (width - 176) / 2;
        return new Rect(x0 - 4, zone + SEPARATOR + 1, 170, INVENTORY - SEPARATOR - 5);
    }

    /** 1.0.4-beta (lot 34) : bord gauche (et droit) du cadre de l'inventaire d'un panneau large : celui de l'alcove de l'habillage. */
    public static final int WIDE_FRAME_X = 5;

    /**
     * Lot 6 (1.0.3) : bandes verticales de l'habillage d'un panneau v2 de hauteur {@code height} dont la zone machine fait {@code zone} :
     * {@code {haut de la texture, bas de la texture, haut dans le panneau, bas dans le panneau}} par bande (voir {@link #PANEL_TEX}). Le bas
     * de l'alcove de la machine tombe sur la derniere rangee de la zone, la glissiere remplit {@link #SEPARATOR}, l'alcove de l'inventaire
     * est sous le cadre : plus rien de l'habillage sous le cadre ni sous le libelle.
     */
    public static int[][] panelBands(int zone, int height) {
        int sep = zone + SEPARATOR;
        return new int[][]{
              {0, PANEL_TITLE, 0, PANEL_TITLE},
              {PANEL_TITLE, PANEL_ALCOVE_END, PANEL_TITLE, zone - 1},
              {PANEL_ALCOVE_END, PANEL_INV_START, zone - 1, sep - 1},
              {PANEL_INV_START, PANEL_PLINTH, sep - 1, height - (PANEL_TEX - PANEL_PLINTH)},
              {PANEL_PLINTH, PANEL_TEX, height - (PANEL_TEX - PANEL_PLINTH), height}};
    }

    public Rect inventoryFrame() {
        return inventoryFrame(width, height);
    }

    /** Onglet n (0 = le premier), sur le bord droit d'un panneau de largeur {@code width}. */
    public static Rect tab(int width, int n) {
        return new Rect(width, TAB_Y + n * TAB_STEP, TAB, TAB);
    }

    public Rect tab(int n) {
        return tab(width, n);
    }

    /** Case d'objet (cadre de 18 autour de l'objet de 16 dont le coin est (x, y)). */
    public static Rect slotFrame(int x, int y) {
        return new Rect(x - 1, y - 1, SLOT, SLOT);
    }

    /** Lot 4 : les elements de cette disposition (cases comprises), pour le controle des chevauchements. */
    public List<Part> parts() {
        return parts(false);
    }

    /** 1.0.5-beta (lot 44) : avec la case Outil ({@code tool}) sous la fleche. */
    public List<Part> parts(boolean tool) {
        List<Part> out = base();
        if (tool) {
            out.add(new Part("slot_tool", slotFrame(toolX, toolY)));
        }
        return out;
    }

    /** 1.0.5-beta (lot 46) : avec la case reservee d'ECHANTILLON du generateur de roches (meme place que la case Outil). */
    public List<Part> sampleParts() {
        List<Part> out = base();
        out.add(new Part("slot_sample", slotFrame(toolX, toolY)));
        return out;
    }

    private List<Part> base() {
        List<Part> out = new ArrayList<>();
        out.add(new Part("energy", new Rect(energy.x() - 1, energy.y() - 1, energy.w() + 2, energy.h() + 2)));
        out.add(new Part("battery", slotFrame(batteryX, batteryY)));
        //1.0.6 : une jauge partagee (liquide ou gaz) n'est qu'un element
        java.util.Set<Rect> seen = new java.util.HashSet<>();
        for (List<Rect> list : List.of(fluidIn, gasIn, fluidOut, gasOut)) {
            for (Rect r : list) {
                if (!seen.add(r)) {
                    continue;
                }
                out.add(new Part("gauge", new Rect(r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2)));
            }
        }
        for (int[] p : itemIn) {
            out.add(new Part("slot_in", slotFrame(p[0], p[1])));
        }
        for (int[] p : itemOut) {
            out.add(new Part("slot_out", slotFrame(p[0], p[1])));
        }
        out.add(new Part("recipe", slotFrame(recipeX, recipeY)));
        out.add(new Part("arrow", arrow));
        out.add(new Part("screen", screen));
        return out;
    }

    // -- Lot 7 : machines de l'age mecanique et du bronze (energie motrice rotation / vapeur / feu au lieu du FE, sans batterie) : la jauge
    //    de l'energie motrice prend la place de la reserve et de la batterie (pleine hauteur des jauges) ; le reste = la disposition standard
    /** Jauge de l'energie motrice (vitesse avec la zone utile, manometre, foyer). */
    public static final Rect POWER = new Rect(8, TOP, GAUGE_W, GAUGE_H);
    /** Rangee de reglage sous un ecran raccourci (gabarit du marteau-pilon, recette d'un poste de cuisine). */
    public static final int ROW_H = 14;

    /** Ecran d'etat raccourci pour laisser une rangee de reglage dessous. */
    public static Rect shortScreen(int width) {
        return new Rect(8, SCREEN_Y, width - 16, SCREEN_H - ROW_H - 2);
    }

    /** Rangee de reglage sous l'ecran raccourci. */
    public static Rect settingRow(int width) {
        return new Rect(8, SCREEN_Y + SCREEN_H - ROW_H, width - 16, ROW_H);
    }

    // -- 1.0.2-beta : CURSEUR de l'interface standard (boite de vitesses, regulateur a boules) : rail, bouton, graduations libellees ; la
    //    valeur part au serveur par PacketMachineSetting (garde PacketGuard) et arrive a MachineGui.handleSetting
    /** Hauteur de la rangee d'un curseur (rail et bouton, puis les libelles des graduations). */
    public static final int SLIDER_H = 24;

    /** Ecran d'etat raccourci au-dessus d'un curseur. */
    public static Rect sliderScreen(int width) {
        return new Rect(8, SCREEN_Y, width - 16, SCREEN_H - SLIDER_H - 2);
    }

    /** Rangee du curseur sous l'ecran raccourci. */
    public static Rect sliderRow(int width) {
        return new Rect(8, SCREEN_Y + SCREEN_H - SLIDER_H, width - 16, SLIDER_H);
    }

    /** Rail du curseur dans sa rangee {@code r} (bornes du bouton : de x0 a x0 + w). */
    public static Rect sliderTrack(Rect r) {
        return new Rect(r.x() + 8, r.y() + 6, r.w() - 16, 4);
    }

    /**
     * Lot 7 : elements d'une machine de l'age mecanique ou du bronze : jauge de l'energie motrice, jauges, cases, case « Recette » (si
     * {@code recipe}), fleche, ecran (raccourci avec une rangee de reglage si {@code row}) ; {@code extra} : cases de plus (maille du tamis).
     */
    public List<Part> ageParts(boolean recipe, boolean row, int[]... extra) {
        List<Part> out = new ArrayList<>();
        out.add(new Part("power", new Rect(POWER.x() - 1, POWER.y() - 1, POWER.w() + 2, POWER.h() + 2)));
        for (Part p : parts()) {
            switch (p.role()) {
                case "energy", "battery", "screen" -> {
                }
                case "recipe" -> {
                    if (recipe) {
                        out.add(p);
                    }
                }
                case "arrow" -> {
                    //Soufflet, noria : ni case ni fleche
                    if (!itemIn.isEmpty() || !itemOut.isEmpty() || !fluidOut.isEmpty() || !gasOut.isEmpty()) {
                        out.add(p);
                    }
                }
                default -> out.add(p);
            }
        }
        for (int[] e : extra) {
            out.add(new Part("slot_in", slotFrame(e[0], e[1])));
        }
        if (row) {
            out.add(new Part("screen", shortScreen(width)));
            out.add(new Part("setting", settingRow(width)));
        } else {
            out.add(new Part("screen", screen));
        }
        return out;
    }

    // -- Lot 8b : sources et services de l'age mecanique et du bronze (machine a balancier, machine a vapeur, injecteur, condenseur, fours) :
    //    jauges hautes a droite de l'energie motrice, carte des mesures, cases propres, ecran d'etat pleine largeur
    /** Jauge haute n a droite de l'energie motrice (thermometre, niveau d'eau, vitesse de sortie). */
    public static Rect ageGauge(int n) {
        return new Rect(PANEL_X + n * (GAUGE_W + 4), TOP, GAUGE_W, GAUGE_H);
    }

    /** Carte des mesures de {@code x} au bord droit (largeur {@code w} ; 0 : jusqu'au bord droit du panneau v2). */
    public static Rect ageCard(int x, int w) {
        return new Rect(x, TOP, w > 0 ? w : WIDTH - 8 - x, GAUGE_H);
    }

    /** Cadre de 1 px autour d'une jauge (element d'une planche ou du controle des chevauchements). */
    public static Part framed(String role, Rect r) {
        return new Part(role, new Rect(r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2));
    }

    /** Elements d'une machine de l'age sans disposition de procede : energie motrice, jauges et autres elements, ecran (raccourci si {@code row}). */
    public static List<Part> ageService(boolean row, Part... parts) {
        List<Part> out = new ArrayList<>();
        out.add(framed("power", POWER));
        out.addAll(List.of(parts));
        if (row) {
            out.add(new Part("screen", shortScreen(WIDTH)));
            out.add(new Part("setting", settingRow(WIDTH)));
        } else {
            out.add(new Part("screen", screen(WIDTH)));
        }
        return out;
    }

    /** Buffer a jauge : jauge, carte, cases des recipients (si {@code io}), ecran d'etat. */
    public static List<Part> bufferParts(boolean io) {
        List<Part> out = new ArrayList<>();
        out.add(new Part("gauge", new Rect(BUFFER_GAUGE.x() - 1, BUFFER_GAUGE.y() - 1, BUFFER_GAUGE.w() + 2, BUFFER_GAUGE.h() + 2)));
        out.add(new Part("card", bufferCard(io)));
        if (io) {
            out.add(new Part("slot_in", slotFrame(IO_X, IN_Y)));
            out.add(new Part("arrow", new Rect(IO_X + 6, IN_Y + 18, 5, OUT_Y - IN_Y - 20)));
            out.add(new Part("slot_out", slotFrame(IO_X, OUT_Y)));
        }
        out.add(new Part("screen", screen(WIDTH)));
        return out;
    }

    /** Hauteur de la zone d'un buffer d'objets de {@code slots} cases. */
    public static int itemBufferHeight(int slots) {
        return ITEMS_Y + Math.max(1, (slots + 8) / 9) * SLOT + ITEMS_BOTTOM;
    }

    /** Radiateur, compresseur : energie, batterie, jauge haute, deux reglages (ou un reglage et une carte), ecran d'etat. */
    public static List<Part> regulatorParts(boolean card) {
        List<Part> out = new ArrayList<>();
        out.add(new Part("energy", new Rect(7, TOP - 1, GAUGE_W + 2, ENERGY_H + 2)));
        out.add(new Part("battery", slotFrame(BATTERY_X, BATTERY_Y)));
        out.add(new Part("gauge", new Rect(TALL.x() - 1, TALL.y() - 1, TALL.w() + 2, TALL.h() + 2)));
        out.add(new Part("setting", new Rect(SET_X, STEP_A - 7, SET_W, STEP_H + 7)));
        out.add(card ? new Part("card", new Rect(SET_X, STEP_B - 6, SET_W, 26)) : new Part("setting", new Rect(SET_X, STEP_B - 7, SET_W, STEP_H + 7)));
        out.add(new Part("screen", screen(WIDTH)));
        return out;
    }

    /** Lot 4 : colonne a droite de l'energie (de {@link #TOP} sur la hauteur des jauges), d'un panneau de largeur {@code width}. */
    public static Rect panel(int width) {
        return new Rect(PANEL_X, TOP, width - 8 - PANEL_X, GAUGE_H);
    }

    /** Lot 4 : un reglage de la colonne (legende 7 px au-dessus, reglage de {@link #STEP_H}) dont le reglage est en {@code stepY}. */
    public static Rect setting(int width, int stepY) {
        return new Rect(PANEL_X, stepY - 7, width - 8 - PANEL_X, STEP_H + 7);
    }

    /** Lot 4 : diffuseur d'oxygene : jauge d'oxygene de la colonne, carte de la piece a sa droite. */
    public static final Rect DIFFUSER_GAUGE = new Rect(PANEL_X, TOP, 24, GAUGE_H);

    public static Rect diffuserCard(int width) {
        return new Rect(PANEL_X + 30, TOP, width - 8 - PANEL_X - 30, GAUGE_H);
    }

    public static List<Part> diffuserParts() {
        return serviceParts(true, new Part("gauge", new Rect(DIFFUSER_GAUGE.x() - 1, TOP - 1, DIFFUSER_GAUGE.w() + 2, GAUGE_H + 2)),
              new Part("card", diffuserCard(WIDTH)));
    }

    /** Lot 4 : machine a energie (reserve a gauche, batterie dessous si {@code battery}), elements de la colonne, ecran d'etat. */
    public static List<Part> serviceParts(boolean battery, Object... column) {
        List<Part> out = new ArrayList<>();
        out.add(new Part("energy", new Rect(7, TOP - 1, GAUGE_W + 2, ENERGY_H + 2)));
        if (battery) {
            out.add(new Part("battery", slotFrame(BATTERY_X, BATTERY_Y)));
        }
        for (Object o : column) {
            out.add(o instanceof Part p ? p : new Part("setting", (Rect) o));
        }
        out.add(new Part("screen", screen(WIDTH)));
        return out;
    }

    // -- Generateurs (lot 4) : jauges hautes (eau, vapeur...) dans la colonne, puis la carte des mesures du lieu ; case de la pile microbienne
    public static final int GEN_SLOT_X = PANEL_X + 4, GEN_SLOT_Y = TOP + (GAUGE_H - 16) / 2;

    /** Jauge haute n d'un generateur (dans la colonne ; sans reserve d'energie, a la place de la reserve). */
    public static Rect generatorGauge(boolean energy, int n) {
        return new Rect((energy ? PANEL_X : 8) + n * (GAUGE_W + 4), TOP, GAUGE_W, GAUGE_H);
    }

    /** Carte des mesures d'un generateur (apres ses jauges et sa case). */
    public static Rect generatorCard(boolean energy, int gauges, boolean slot) {
        int x = (energy ? PANEL_X : 8) + gauges * (GAUGE_W + 4) + (slot ? 26 : 0);
        return new Rect(x, TOP, WIDTH - 8 - x, GAUGE_H);
    }

    public static List<Part> generatorParts(boolean energy, int gauges, boolean slot) {
        List<Part> out = new ArrayList<>();
        if (energy) {
            out.add(new Part("energy", new Rect(7, TOP - 1, GAUGE_W + 2, ENERGY_H + 2)));
        }
        for (int i = 0; i < gauges; i++) {
            Rect g = generatorGauge(energy, i);
            out.add(new Part("gauge", new Rect(g.x() - 1, g.y() - 1, g.w() + 2, g.h() + 2)));
        }
        if (slot) {
            out.add(new Part("slot_in", slotFrame(GEN_SLOT_X, GEN_SLOT_Y)));
        }
        out.add(new Part("card", generatorCard(energy, gauges, slot)));
        out.add(new Part("screen", screen(WIDTH)));
        return out;
    }

    // -- Postes du chemin de fer, sous-station, grue a eau (lot 4) : jauges hautes a gauche, carte et bouton du sens a droite, 9 cases des
    //    postes a objets contre le bord droit, ecran d'etat pleine largeur
    public static final int POST_GAUGE_W = 16, POST_SLOTS_X = WIDTH - 8 - 3 * SLOT + 1, POST_SLOTS_Y = TOP + 1, POST_BUTTON_Y = TOP + 46;

    /** Jauge haute n d'un poste (fluide, gaz, energie). */
    public static Rect postGauge(int n) {
        return new Rect(8 + n * (POST_GAUGE_W + 4), TOP, POST_GAUGE_W, GAUGE_H);
    }

    private static int postX(int gauges) {
        return gauges > 0 ? 8 + gauges * (POST_GAUGE_W + 4) + 4 : 8;
    }

    private static int postRight(boolean slots) {
        return slots ? POST_SLOTS_X - 1 - 6 : WIDTH - 8;
    }

    /** Carte du poste (ce qu'il sert, mesures) ; plus basse quand le bouton du sens est dessous. */
    public static Rect postCard(int gauges, boolean slots, boolean reversible) {
        int x = postX(gauges);
        return new Rect(x, TOP, postRight(slots) - x, reversible ? POST_BUTTON_Y - TOP - 4 : GAUGE_H);
    }

    /** Bouton charger / decharger d'un poste reversible (sous la carte). */
    public static Rect postButton(int gauges, boolean slots) {
        int x = postX(gauges);
        return new Rect(x, POST_BUTTON_Y, postRight(slots) - x, STEP_H);
    }

    public static List<Part> postParts(int gauges, boolean slots, boolean reversible) {
        List<Part> out = new ArrayList<>();
        for (int i = 0; i < gauges; i++) {
            Rect g = postGauge(i);
            out.add(new Part("gauge", new Rect(g.x() - 1, g.y() - 1, g.w() + 2, g.h() + 2)));
        }
        out.add(new Part("card", postCard(gauges, slots, reversible)));
        if (reversible) {
            out.add(new Part("setting", postButton(gauges, slots)));
        }
        out.add(new Part("screen", screen(WIDTH)));
        return out;
    }

    /**
     * Lot 4 : machine a CORPS PROPRE (science, table de recherche, tables d'assemblage, terminal, IA) dans le cadre de l'interface v2 :
     * le corps d'avant garde sa place (zone de la machine, sous la bande du titre) ; l'inventaire est DANS le cadre, les onglets a droite.
     * Le controle verifie que le corps et ses cases tiennent dans la zone.
     */
    public static List<Part> body(int width, int height) {
        return List.of(new Part("body", new Rect(4, 16, width - 8, height - 18)));
    }
}

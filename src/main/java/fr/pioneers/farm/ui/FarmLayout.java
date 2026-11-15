package fr.pioneers.farm.ui;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.StandardLayout.Part;
import fr.pioneers.api.machine.StandardLayout.Rect;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 41, retour 60 de Guillaume : « Ajout d'interfaces sur les machines de fermes (plus d'interactions directes, une vraie
 * interface et fin de la bulle d'info, car l'interface est explicite) ») : DISPOSITION de l'interface standard v2 d'un poste de cuisine ou
 * d'un appareil de la ferme (meme grammaire que {@link StandardLayout} : jauge a gauche, jauges des cuves, cases d'entree, fleche, cases de
 * sortie, paires de cases « Vider » / « Remplir », ecran d'etat, rangee de boutons). Commune au serveur (positions des cases du menu,
 * {@code guiParts} verifies par {@code tools/check_machine_api.py}) et au client (dessin : {@code client/gui/core/FarmViews}).
 * <p>
 * Construite de gauche a droite ({@link Builder}), puis centree dans la place a droite de la jauge (comme {@link StandardLayout}) ; panneau de
 * 196 px au moins (plus large si le contenu le demande). Les indices des cases sont ceux de la liste du poste (ordre de sauvegarde garde).
 */
public final class FarmLayout {

    /** Role d'une case (infobulle de la case vide, droits du joueur et de l'automatisation decides par la tile). */
    public enum Role {
        /** Ingredient. */
        IN,
        /** Produit. */
        OUT,
        /** Combustible (sous la jauge du feu). */
        FUEL,
        /** Outil (couteau) : il s'use au geste, il n'est pas consomme. */
        TOOL,
        /** Contenant vide a remplir depuis la cuve de sortie (fiole, chope, seau). */
        FILL_IN,
        /** Contenant rempli. */
        FILL_OUT,
        /** Contenant plein a vider dans la cuve d'entree (seau d'eau, de lait...). */
        DRAIN_IN,
        /** Contenant vide rendu. */
        DRAIN_OUT,
        /** Reserve (mangeoire, distributeur, nourriture du clapier). */
        STORE,
        /** Case de cuisson en place (poele, four, sechoir, fumoir) : l'ingredient y devient le produit ; barre d'avancement dessous. */
        CELL,
        /** Contenant a servir (bol, tasse) : le plat de la marmite y est servi. */
        SERVE_IN,
        /** Portion servie. */
        SERVE_OUT
    }

    /** Une case : indice dans la liste du poste, role, coin de l'objet (relatif au panneau), cle de l'infobulle de la case vide. */
    public record SlotSpec(int index, Role role, int x, int y, String tip) {}

    /** Une cuve : 0 = entree, 1 = sortie (ou cuve unique), sa jauge. */
    public record TankSpec(int which, Rect rect) {}

    /** Jauge de gauche : rien, feu (combustible), chaleur du dessous, air (sechoir : pluie / feu), eau. */
    public enum Gauge {
        NONE, FIRE, HEAT, AIR
    }

    public static final int TOP = StandardLayout.TOP, GAUGE_W = StandardLayout.GAUGE_W, GAUGE_H = StandardLayout.GAUGE_H, SLOT = StandardLayout.SLOT;
    /** Pas vertical d'une case de cuisson (case + barre de 2 px + 2 px). */
    public static final int CELL_STEP = SLOT + 4;

    public final List<SlotSpec> slots;
    public final List<TankSpec> tanks;
    public final Gauge gauge;
    /** Jauge de gauche (null : aucune) ; courte (40) quand une case de combustible ou d'outil est dessous. */
    @Nullable
    public final Rect gaugeRect;
    @Nullable
    public final Rect arrow;
    /** Case « Recette » du selecteur (coin de l'objet) ; null : pas de selecteur. */
    @Nullable
    public final int[] recipe;
    public final Rect screen;
    /** Boutons de la rangee sous l'ecran (cles de langue des libelles) et leur place. */
    public final List<String> buttons;
    public final List<Rect> buttonRects;
    public final int width;

    private FarmLayout(Builder b, int width, int dx) {
        this.width = width;
        List<SlotSpec> s = new ArrayList<>();
        for (SlotSpec spec : b.slots) {
            boolean left = spec.role == Role.FUEL || spec.role == Role.TOOL && b.leftTool;
            s.add(left ? spec : new SlotSpec(spec.index, spec.role, spec.x + dx, spec.y, spec.tip));
        }
        this.slots = List.copyOf(s);
        List<TankSpec> t = new ArrayList<>();
        for (TankSpec spec : b.tanks) {
            t.add(new TankSpec(spec.which, shift(spec.rect, dx)));
        }
        this.tanks = List.copyOf(t);
        this.gauge = b.gauge;
        this.gaugeRect = b.gaugeRect;
        this.arrow = b.arrow == null ? null : shift(b.arrow, dx);
        this.recipe = b.recipe == null ? null : new int[]{b.recipe[0] + dx, b.recipe[1]};
        this.buttons = List.copyOf(b.buttons);
        if (b.tall) {
            //Instrument sans case (station meteo) : l'ecran prend toute la zone sous le titre
            this.screen = new Rect(8, TOP, width - 16, StandardLayout.SCREEN_Y + StandardLayout.SCREEN_H - TOP);
            this.buttonRects = List.of();
        } else if (buttons.isEmpty()) {
            this.screen = StandardLayout.screen(width);
            this.buttonRects = List.of();
        } else {
            this.screen = StandardLayout.shortScreen(width);
            Rect row = StandardLayout.settingRow(width);
            List<Rect> rects = new ArrayList<>();
            int n = buttons.size(), gap = 4, w = (row.w() - gap * (n - 1)) / n;
            for (int i = 0; i < n; i++) {
                rects.add(new Rect(row.x() + i * (w + gap), row.y(), w, row.h()));
            }
            this.buttonRects = List.copyOf(rects);
        }
    }

    private static Rect shift(Rect r, int dx) {
        return new Rect(r.x() + dx, r.y(), r.w(), r.h());
    }

    /** Case de role donne (premiere), ou null. */
    @Nullable
    public SlotSpec slot(Role role) {
        for (SlotSpec s : slots) {
            if (s.role == role) {
                return s;
            }
        }
        return null;
    }

    /** Cases de ce role, dans l'ordre. */
    public List<SlotSpec> slots(Role role) {
        List<SlotSpec> out = new ArrayList<>();
        for (SlotSpec s : slots) {
            if (s.role == role) {
                out.add(s);
            }
        }
        return out;
    }

    /** Case d'indice {@code index}, ou null. */
    @Nullable
    public SlotSpec at(int index) {
        for (SlotSpec s : slots) {
            if (s.index == index) {
                return s;
            }
        }
        return null;
    }

    /** Jauge de la cuve {@code which}, ou null. */
    @Nullable
    public Rect tank(int which) {
        for (TankSpec t : tanks) {
            if (t.which == which) {
                return t.rect;
            }
        }
        return null;
    }

    /** Barre d'avancement d'une case de cuisson (sous la case). */
    public static Rect bar(SlotSpec s) {
        return new Rect(s.x, s.y + 18, 16, 2);
    }

    /** Elements de l'interface (controle des chevauchements, {@code screens.json}). */
    public List<Part> parts() {
        List<Part> out = new ArrayList<>();
        if (gaugeRect != null) {
            out.add(new Part("power", new Rect(gaugeRect.x() - 1, gaugeRect.y() - 1, gaugeRect.w() + 2, gaugeRect.h() + 2)));
        }
        for (TankSpec t : tanks) {
            out.add(new Part("gauge", new Rect(t.rect.x() - 1, t.rect.y() - 1, t.rect.w() + 2, t.rect.h() + 2)));
        }
        for (SlotSpec s : slots) {
            boolean in = switch (s.role) {
                case OUT, FILL_OUT, DRAIN_OUT, SERVE_OUT -> false;
                default -> true;
            };
            out.add(new Part(in ? "slot_in" : "slot_out", StandardLayout.slotFrame(s.x, s.y)));
            if (s.role == Role.CELL) {
                out.add(new Part("bar", bar(s)));
            }
        }
        if (recipe != null) {
            out.add(new Part("recipe", StandardLayout.slotFrame(recipe[0], recipe[1])));
        }
        if (arrow != null) {
            out.add(new Part("arrow", arrow));
        }
        out.add(new Part("screen", screen));
        for (Rect r : buttonRects) {
            out.add(new Part("setting", r));
        }
        return out;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Construction de gauche a droite. */
    public static final class Builder {

        private final List<SlotSpec> slots = new ArrayList<>();
        private final List<TankSpec> tanks = new ArrayList<>();
        private final List<String> buttons = new ArrayList<>();
        private Gauge gauge = Gauge.NONE;
        @Nullable
        private Rect gaugeRect, arrow;
        @Nullable
        private int[] recipe;
        private boolean leftTool, tall;
        private int x = 8;

        private Builder() {
        }

        /** Jauge de gauche pleine hauteur (chaleur, air). */
        public Builder gauge(Gauge kind) {
            gauge = kind;
            gaugeRect = StandardLayout.POWER;
            x = 8 + GAUGE_W + 8;
            return this;
        }

        /** Jauge du feu (courte) et la case du combustible dessous (place de la batterie de l'interface standard). */
        public Builder fire(int fuelIndex) {
            gauge = Gauge.FIRE;
            gaugeRect = new Rect(8, TOP, GAUGE_W, StandardLayout.ENERGY_H);
            slots.add(new SlotSpec(fuelIndex, Role.FUEL, StandardLayout.BATTERY_X, StandardLayout.BATTERY_Y, "gui.pioneers.farm.slot.fuel"));
            x = 8 + GAUGE_W + 8;
            return this;
        }

        /** Outil (couteau) a la place de la batterie, colonne de gauche (sans jauge au-dessus, ou sous une jauge courte). */
        public Builder tool(int index) {
            slots.add(new SlotSpec(index, Role.TOOL, StandardLayout.BATTERY_X, StandardLayout.BATTERY_Y, "gui.pioneers.farm.slot.tool"));
            leftTool = true;
            x = Math.max(x, 8 + GAUGE_W + 8);
            return this;
        }

        /** Paire verticale (haut : contenant, bas : contenant transforme) dans une colonne de 18. */
        private void pair(int in, Role inRole, String inTip, int out, Role outRole, String outTip) {
            slots.add(new SlotSpec(in, inRole, x + 1, TOP + 1, inTip));
            slots.add(new SlotSpec(out, outRole, x + 1, TOP + GAUGE_H - 17, outTip));
            x += SLOT + 4;
        }

        /** Cuve d'entree : paire « Vider » (seau plein -> cuve, seau vide rendu) puis sa jauge. */
        public Builder tankIn(int drainIn, int drainOut) {
            if (drainIn >= 0) {
                pair(drainIn, Role.DRAIN_IN, "gui.pioneers.farm.slot.drain_in", drainOut, Role.DRAIN_OUT, "gui.pioneers.farm.slot.drain_out");
            }
            tanks.add(new TankSpec(0, new Rect(x, TOP, GAUGE_W, GAUGE_H)));
            x += GAUGE_W + 8;
            return this;
        }

        /** Cases d'ingredients en grille (au plus {@code cols} colonnes), centrees en hauteur. */
        public Builder inputs(int[] indices, int cols) {
            return grid(indices, cols, Role.IN, "gui.pioneers.farm.slot.in", SLOT, 4);
        }

        /** Reserve en grille (mangeoire, distributeur). */
        public Builder store(int[] indices, int cols, String tip) {
            return grid(indices, cols, Role.STORE, tip, SLOT, 4);
        }

        /** Cases de cuisson en place (barre d'avancement sous chacune). */
        public Builder cells(int[] indices, int cols) {
            return grid(indices, cols, Role.CELL, "gui.pioneers.farm.slot.cell", CELL_STEP, 4);
        }

        /** Sorties en grille (au plus {@code cols} colonnes). */
        public Builder outputs(int[] indices, int cols, String... tips) {
            int c = Math.max(1, Math.min(cols, indices.length));
            int rows = (indices.length + c - 1) / c;
            int y0 = TOP + (GAUGE_H - rows * SLOT) / 2 + 1;
            for (int i = 0; i < indices.length; i++) {
                String tip = i < tips.length ? tips[i] : "gui.pioneers.farm.slot.out";
                slots.add(new SlotSpec(indices[i], Role.OUT, x + 1 + (i % c) * SLOT, y0 + (i / c) * SLOT, tip));
            }
            x += c * SLOT + 6;
            return this;
        }

        private Builder grid(int[] indices, int cols, Role role, String tip, int step, int after) {
            int c = Math.max(1, Math.min(cols, indices.length));
            int rows = (indices.length + c - 1) / c;
            int height = rows * step - (step - SLOT);
            int y0 = TOP + (GAUGE_H - height) / 2 + 1;
            for (int i = 0; i < indices.length; i++) {
                slots.add(new SlotSpec(indices[i], role, x + 1 + (i % c) * SLOT, y0 + (i / c) * step, tip));
            }
            x += c * SLOT + after;
            return this;
        }

        /** Fleche d'avancement (et la case « Recette » au-dessus si {@code selector}). */
        public Builder arrow(boolean selector) {
            arrow = new Rect(x, TOP + GAUGE_H / 2 - 4, 28, 8);
            if (selector) {
                recipe = new int[]{x + 6, TOP + 4};
            }
            x += 32;
            return this;
        }

        /** Cuve de sortie : sa jauge puis la paire « Remplir » (contenant vide -> plein). */
        public Builder tankOut(int fillIn, int fillOut) {
            tanks.add(new TankSpec(1, new Rect(x, TOP, GAUGE_W, GAUGE_H)));
            x += GAUGE_W + 5;
            if (fillIn >= 0) {
                pair(fillIn, Role.FILL_IN, "gui.pioneers.farm.slot.fill_in", fillOut, Role.FILL_OUT, "gui.pioneers.farm.slot.fill_out");
            }
            return this;
        }

        /** Cuve unique (abreuvoir, fut, arroseur) : paire « Vider », jauge, paire « Remplir » (facultatives : indice < 0). */
        public Builder tank(int drainIn, int drainOut, int fillIn, int fillOut) {
            if (drainIn >= 0) {
                pair(drainIn, Role.DRAIN_IN, "gui.pioneers.farm.slot.drain_in", drainOut, Role.DRAIN_OUT, "gui.pioneers.farm.slot.drain_out");
            }
            tanks.add(new TankSpec(1, new Rect(x, TOP, GAUGE_W, GAUGE_H)));
            x += GAUGE_W + 5;
            if (fillIn >= 0) {
                pair(fillIn, Role.FILL_IN, "gui.pioneers.farm.slot.fill_in", fillOut, Role.FILL_OUT, "gui.pioneers.farm.slot.fill_out");
            }
            x += 3;
            return this;
        }

        /** Paire « Servir » (bol vide -> portion) de la marmite. */
        public Builder serve(int in, int out) {
            pair(in, Role.SERVE_IN, "gui.pioneers.farm.slot.serve_in", out, Role.SERVE_OUT, "gui.pioneers.farm.slot.serve_out");
            return this;
        }

        /** Case isolee centree en hauteur (role et infobulle donnes). */
        public Builder single(int index, Role role, String tip) {
            slots.add(new SlotSpec(index, role, x + 1, TOP + (GAUGE_H - SLOT) / 2 + 1, tip));
            x += SLOT + 4;
            return this;
        }

        /** Ecran d'etat sur toute la zone (instrument sans case ni bouton : station meteo). */
        public Builder tall() {
            tall = true;
            return this;
        }

        /** Bouton de la rangee sous l'ecran (geste manuel, action propre). */
        public Builder button(String key) {
            buttons.add(key);
            return this;
        }

        public FarmLayout build() {
            //Contenu jusqu'a last ; centre dans la place a droite de la colonne de gauche, jusqu'a width - 8 (la jauge de gauche reste a sa place)
            int last = x - 4;
            int width = Math.max(StandardLayout.WIDTH, last + 8);
            int dx = Math.max(0, (width - 8 - last) / 2);
            return new FarmLayout(this, width, dx);
        }
    }
}

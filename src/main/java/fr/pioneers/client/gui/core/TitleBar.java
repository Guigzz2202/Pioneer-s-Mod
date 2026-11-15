package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.MachineSpec;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 48, vu sur la planche du lot 44 : la pastille « T1 · Bronze » sur le titre de la presse a vapeur compacte) : GEOMETRIE PURE
 * de la barre de titre des interfaces standard v2, dessinee par {@link StandardView#title} : titre a gauche (nom sans tier), pastille de tier
 * (ou d'age) a droite, pastille multibloc et note eventuelles devant elle. Sans police ni dessin : le jeu la mesure avec sa police, le banc de
 * l'API (tools/api/TitleBench, appele par tools/api/RegistryBench) avec la VRAIE police du jeu (fichiers de la police du jar), en francais et en
 * anglais, sur tous les ecrans du banc ; tools/check_machine_api.py --bench et tools/check_p105_48.py verifient qu'aucun titre ne passe sous
 * la pastille.
 * <ul>
 *     <li>pastille : largeur = texte a 70 % + 8, a 8 du bord droit ;</li>
 *     <li>pastille multibloc (trois cubes) : 14 devant la pastille ; note (buffer, multibloc non forme) : sa largeur a 70 % + 6 devant ;</li>
 *     <li>titre : de x 8 a 12 avant ce qui est a sa droite ; trop long, il est RESSERRE jusqu'a 70 % puis COUPE par « … »
 *     ({@code GuiDraw.fitScale} / {@code cutText}) ; dans les deux cas, infobulle du nom complet au survol ({@link #shortened}).</li>
 * </ul>
 */
public final class TitleBar {

    /** Titre a x 8 ; pastilles a 8 du bord droit ; pastille multibloc de 14 ; note : 6 de marge ; 12 entre le titre et ce qui le suit. */
    public static final int X = 8, EDGE = 8, MULTI = 14, NOTE_PAD = 6, GAP = 12;

    private TitleBar() {
    }

    /**
     * {@code chipX} / {@code chipW} : pastille de tier (ou d'age) ; {@code left} : bord gauche de ce qui est a droite du titre (note, sinon
     * pastille multibloc, sinon pastille, sinon bord) ; {@code room} : largeur permise au titre (de x 8 a {@code left - GAP}).
     */
    public record Layout(int chipX, int chipW, int left, int room) {}

    /**
     * Geometrie de la barre de titre d'un ecran de largeur {@code width} : {@code chipText} = largeur du texte de la pastille (pixels de la
     * police, &lt; 0 : aucune pastille), {@code noteText} = largeur de la note (&lt; 0 : aucune note).
     */
    public static Layout layout(int width, int chipText, boolean multiblock, int noteText) {
        int bx = width - EDGE, bw = 0;
        if (chipText >= 0) {
            bw = (int) (chipText * 0.7F) + 8;
            bx = width - EDGE - bw;
        }
        int mx = multiblock ? bx - MULTI : bx;
        if (noteText >= 0) {
            mx -= (int) (noteText * 0.7F) + NOTE_PAD;
        }
        return new Layout(bx, bw, mx, Math.max(1, mx - GAP - X));
    }

    /** Vrai si un titre de largeur {@code textWidth} (pixels de la police) n'est pas ecrit en entier a sa taille (resserre ou coupe). */
    public static boolean shortened(int textWidth, int room) {
        return textWidth > room;
    }

    /** Tier global de la pastille (0..6), ou -1 (aucun ; salle blanche : jamais, lot 31). */
    public static int global(boolean light, int tier, @Nullable MachineSpec spec) {
        int global = light ? -1 : tier >= 0 && tier <= 3 ? tier + 2 : StandardView.singleTier(spec);
        return global >= 0 && global <= 6 ? global : -1;
    }

    /**
     * Texte de la pastille (tier global « T1 · Bronze », sinon age de la fiche ou du poste), ou null ; {@code age} = celui de
     * {@code StandardView.age} (fiche aux tiers AGE, sinon age du poste hors API). Meme choix que le dessin de {@link StandardView#title}.
     */
    @Nullable
    public static Component chip(boolean light, int tier, @Nullable MachineSpec spec, @Nullable String age) {
        int global = global(light, tier, spec);
        if (global >= 0) {
            return StandardView.globalChip(global);
        }
        return age != null && !light ? Component.translatable("gui.pioneers.api.age_chip." + age) : null;
    }

    /** Nom du titre sans son tier (« Presse compacte MKI » -> « Presse compacte ») : celui que dessine {@link StandardView#title}. */
    public static Component name(Component title) {
        return StandardView.baseName(title);
    }
}

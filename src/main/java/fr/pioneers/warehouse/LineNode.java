package fr.pioneers.warehouse;

import fr.pioneers.kinetics.rope.LinkType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 2, Guillaume : « il faut que le raccordement par poulie de base fonctionne egalement ») : NOEUD D'UNE LIGNE D'ENTREPOT,
 * une roue a axe vertical dont les cordes portent les colis. 1.0.4-beta (lot 27, Guillaume : « Supprime la poulie d'entrepot. Les poulies de
 * bases sont suffisantes ») : c'est toujours une poulie de base a axe vertical ({@code PulleyTile}, jusqu'a 4 cordes) ; la PETITE poulie est
 * aussi la station d'une trappe ou d'un comptoir ({@link #stationWheel}). Une seule verite, la cinetique : chaque corde lie ses deux roues au
 * rapport de leurs RAYONS PRIMITIFS ({@link #pitchPx}), la corde a donc la meme vitesse lineaire sur tout le reseau ({@link #ropeSpeed}) et
 * les colis avancent a cette vitesse.
 */
public interface LineNode {

    /** Vrai si la roue porte des colis (axe vertical). */
    boolean lineNode();

    /** 1.0.4-beta (lot 27) : la roue peut-elle etre la station d'une trappe ou d'un comptoir pose dessous (petite poulie) ? */
    default boolean stationWheel() {
        return false;
    }

    /** Rayon primitif de la gorge (px) : la ou passent la corde et les colis. */
    float pitchPx();

    /** Hauteur de la gorge dans le bloc (px) : la plus basse des cordes (degagement des colis). */
    float ropeYPx();

    /** Hauteur dans le bloc (px) de la corde vers {@code other} (une poulie de base a deux cordes les tient a deux hauteurs). */
    default float ropeYPx(BlockPos other) {
        return ropeYPx();
    }

    /** Voisins relies par une corde qui peut porter des colis (droite, pas croisee), lien reciproque a verifier par l'appelant. */
    List<BlockPos> lineLinks();

    /** Corde vers {@code other} (portant des colis ou non) ? */
    boolean linkedTo(BlockPos other);

    /** Sorte de la corde vers {@code other} (fleche du dessin), corde de fibre si aucune. */
    LinkType lineType(BlockPos other);

    /** Vitesse de rotation (tr/min, signee). */
    double lineSpeed();

    /**
     * 1.0.5-beta (lot 13, retour de Guillaume : « vitesse des colis un peu plus grande ») : CADENCE des lignes d'entrepot : la corde et ses
     * colis courent 28 % plus vite que la jante primitive (la griffe file sur un brin demultiplie) ; la meme regle partout : avance du serveur,
     * extrapolation du client, rendu du colis ET defilement de la corde ({@code KineticVisuals}) : le colis ne glisse jamais sur sa corde.
     */
    double CADENCE = 1.28;

    /** Vitesse lineaire de la corde (blocs par tick) : |tr/min| x circonference primitive x {@link #CADENCE}. */
    default double ropeSpeed() {
        return Math.abs(lineSpeed()) / 60.0 / 20.0 * 2 * Math.PI * pitchPx() / 16.0 * CADENCE;
    }

    /** Sens de rotation : +1 (angle croissant), -1, 0 a l'arret. */
    default int spin() {
        double s = lineSpeed();
        return s > 1e-6 ? 1 : s < -1e-6 ? -1 : 0;
    }

    /** Le noeud de ligne en {@code pos} (poulie d'entrepot, poulie de base a axe vertical), ou null. */
    @Nullable
    static LineNode at(@Nullable BlockGetter level, BlockPos pos) {
        return level != null && level.getBlockEntity(pos) instanceof LineNode n && n.lineNode() ? n : null;
    }
}

package fr.pioneers.farm.ui;

import fr.pioneers.farm.FarmCard;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 41, retour 60) : ce que l'interface standard d'un poste ou d'un appareil de la ferme lit sur sa tile (vue {@code farm} :
 * {@code client/gui/core/FarmViews}) : sa disposition, ses cuves, l'etat a l'ecran (le contenu de l'ancienne fiche au regard, sans les
 * icones du contenu ni les indications de geste : l'interface les montre), l'avancement des cases de cuisson et de la fleche, la jauge de
 * gauche. Lu cote CLIENT sur la tile du monde (synchronisee a chaque changement et chaque seconde pendant un travail) ; l'avancement vient
 * aussi des valeurs du menu (synchronisees a chaque tick).
 */
public interface FarmMachine {

    /** Disposition de l'interface (meme objet des deux cotes). */
    FarmLayout farmLayout();

    /** Etat a l'ecran : premiere ligne = en-tete (pastille, valeur, barre si c'est une jauge), puis les lignes (au plus 3 a 4). */
    FarmCard screenState();

    /** Cuve {@code which} (0 = entree, 1 = sortie ou cuve unique), ou null. */
    @Nullable
    FluidTank farmTank(int which);

    /** Avancement 0..1 de la case de cuisson d'indice {@code index} (-1 : rien a cuire). */
    default float cellProgress(int index) {
        return -1;
    }

    /** Jauge de gauche : niveau 0..1 (feu restant, chaleur du dessous, air sec). */
    default float gaugeLevel() {
        return 0;
    }

    /** Infobulle de la jauge de gauche. */
    default List<Component> gaugeTip() {
        return List.of();
    }

    /** Avancement 0..1 de la fleche. */
    default float arrowProgress() {
        return 0;
    }

    /** Le bouton {@code i} de la rangee est-il utilisable maintenant ? */
    default boolean buttonActive(int i) {
        return true;
    }

    /** Infobulle du bouton {@code i}. */
    default List<Component> buttonTip(int i) {
        return List.of();
    }

    /** Poste a geste manuel (Maj + clic droit main vide = le premier bouton) ? */
    default boolean manualGesture() {
        return false;
    }

    /** Lignes propres de l'onglet Infos : {cle de libelle gui.pioneers.api.info.*, valeur}. */
    default List<Object[]> infoRows() {
        return List.of();
    }
}

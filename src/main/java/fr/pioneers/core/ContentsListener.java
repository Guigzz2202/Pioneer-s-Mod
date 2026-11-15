package fr.pioneers.core;

/** Prevenu quand un contenu change (pour marquer le bloc a sauvegarder, resynchroniser...). */
@FunctionalInterface
public interface ContentsListener {

    void onContentsChanged();
}

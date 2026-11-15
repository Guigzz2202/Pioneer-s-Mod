package fr.pioneers.core;

/**
 * Qui demande le transfert : une machine voisine ou un tuyau ({@code EXTERNAL}), la machine elle-meme ({@code INTERNAL}), ou un joueur
 * dans l'interface ({@code MANUAL}). Les reservoirs peuvent refuser selon l'origine (une sortie n'accepte rien de l'exterieur...).
 */
public enum Automation {
    EXTERNAL, INTERNAL, MANUAL;

    public boolean external() {
        return this == EXTERNAL;
    }
}

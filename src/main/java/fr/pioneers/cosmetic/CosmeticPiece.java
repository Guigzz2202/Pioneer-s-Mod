package fr.pioneers.cosmetic;

import net.minecraft.world.entity.EquipmentSlot;

/** 1.12.0 (lot E4) : les quatre pieces d'une tenue cosmetique, dans l'ordre des emplacements d'armure (tete, torse, jambes, pieds). */
public enum CosmeticPiece {
    HAT("hat", EquipmentSlot.HEAD),
    TOP("top", EquipmentSlot.CHEST),
    BOTTOM("bottom", EquipmentSlot.LEGS),
    SHOES("shoes", EquipmentSlot.FEET);

    public static final CosmeticPiece[] ALL = values();
    /** Cle des textes et des identifiants ({@code cosmetic.pioneers.piece.<cle>}). */
    public final String key;
    /** Emplacement d'armure que la piece habille (et dont elle remplace le rendu). */
    public final EquipmentSlot slot;

    CosmeticPiece(String key, EquipmentSlot slot) {
        this.key = key;
        this.slot = slot;
    }

    public static CosmeticPiece byIndex(int i) {
        return ALL[Math.max(0, Math.min(ALL.length - 1, i))];
    }
}

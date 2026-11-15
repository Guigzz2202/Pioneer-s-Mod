package fr.pioneers.equipment.gear;

import java.util.Locale;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * 0.20.15 : sortes d'objets modulaires et leur categorie d'accueil des modules ({@link GearModule#categories}) : H casque, C plastron,
 * L jambieres, F bottes, T outils (pioche, hache, pelle, houe, multi-outil), B lame, R fusil.
 */
public enum GearKind {
    HELMET('H'),
    CHESTPLATE('C'),
    LEGGINGS('L'),
    BOOTS('F'),
    PICKAXE('T'),
    AXE('T'),
    SHOVEL('T'),
    HOE('T'),
    MULTITOOL('T'),
    BLADE('B'),
    RIFLE('R');

    public final char category;

    GearKind(char category) {
        this.category = category;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isArmor() {
        return ordinal() <= BOOTS.ordinal();
    }

    public boolean isTool() {
        return category == 'T';
    }

    /** Emplacement porte (armure) ; la main pour le reste. */
    public EquipmentSlot slot() {
        return switch (this) {
            case HELMET -> EquipmentSlot.HEAD;
            case CHESTPLATE -> EquipmentSlot.CHEST;
            case LEGGINGS -> EquipmentSlot.LEGS;
            case BOOTS -> EquipmentSlot.FEET;
            default -> EquipmentSlot.MAINHAND;
        };
    }

    /** Index de la piece d'armure (0 casque ... 3 bottes). */
    public int piece() {
        return isArmor() ? ordinal() : -1;
    }
}

package fr.pioneers.materials;

import java.util.List;

/**
 * GENERE par tools/gc/materials/armor.py : NE PAS MODIFIER A LA MAIN.
 * Armures de metal forgees (1.0) : defense casque / plastron / jambieres / bottes, multiplicateur de durabilite (comme Minecraft :
 * 11 / 16 / 15 / 13 x multiplicateur), solidite, resistance au recul, enchantabilite, metal de reparation (tag {@code #...} ou objet).
 */
public final class GeneratedArmor {

    private GeneratedArmor() {
    }

    public record Def(String id, int[] defense, int durability, float toughness, float knockback, int enchant, String repair) {
    }

    public static final List<Def> ARMORS = List.of(
        new Def("bronze", new int[]{2, 5, 4, 1}, 13, 0.00F, 0.00F, 12, "#forge:plates/bronze"),
        new Def("iron", new int[]{2, 6, 5, 2}, 15, 0.00F, 0.00F, 9, "#forge:plates/iron"),
        new Def("gold", new int[]{3, 8, 6, 3}, 30, 2.00F, 0.00F, 30, "#forge:plates/gold"),
        new Def("steel", new int[]{3, 7, 6, 2}, 25, 1.00F, 0.00F, 10, "#forge:plates/steel"),
        new Def("hardened_steel", new int[]{3, 8, 6, 3}, 30, 2.00F, 0.05F, 9, "pioneers:hardened_steel_ingot"),
        new Def("titanium", new int[]{3, 8, 6, 3}, 33, 2.50F, 0.00F, 16, "#forge:plates/titanium"),
        new Def("tungsten_carbide", new int[]{4, 9, 7, 3}, 40, 3.50F, 0.15F, 8, "#forge:plates/tungsten_carbide"),
        new Def("nitinol", new int[]{4, 9, 7, 4}, 37, 3.00F, 0.10F, 18, "#forge:plates/nitinol")
    );
}

package fr.pioneers.kinetics;

import java.util.Locale;

/**
 * Materiau d'une piece de transmission : couple et vitesse maximaux, inertie d'un arbre, frottement. Le bois et le fer sont de l'age
 * mecanique ; le bronze et l'acier arrivent avec l'age de la vapeur.
 */
public enum KineticMaterial {
    WOOD("wooden", 3_000, 120, 0.25, 0.010, 1),
    IRON("iron", 12_000, 400, 0.8, 0.006, 1),
    BRONZE("bronze", 20_000, 600, 0.9, 0.004, 2),
    STEEL("steel", 60_000, 1_200, 0.8, 0.003, 2);

    public final String prefix;
    public final double maxTorque, maxSpeed, shaftInertia, friction;
    public final int age;

    KineticMaterial(String prefix, double maxTorque, double maxSpeed, double shaftInertia, double friction, int age) {
        this.prefix = prefix;
        this.maxTorque = maxTorque;
        this.maxSpeed = maxSpeed;
        this.shaftInertia = shaftInertia;
        this.friction = friction;
        this.age = age;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}

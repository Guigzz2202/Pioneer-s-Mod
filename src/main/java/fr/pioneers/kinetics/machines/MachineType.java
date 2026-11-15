package fr.pioneers.kinetics.machines;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Machines mecaniques de l'age 1 : couple de travail, vitesses minimale et nominale (cadence x1), recette, cases. L'arbre entre par les deux
 * flancs (axe perpendiculaire a la facade), sauf la meule (par dessous ou dessus). 1.0.2-beta : le martinet est retire (bloc remappe vers le
 * tour a bois, qui reprend son cintrage).
 */
public enum MachineType {
    MILLSTONE(MechanicalRecipe.Kind.MILLING, 150, 4, 30, 1, 2, false, 30),
    //1.0.2-beta : plus de case de maille (rendement de base = ancienne maille de fibres)
    SIFTER(MechanicalRecipe.Kind.SIFTING, 80, 10, 60, 1, 6, false, 6),
    JAW_CRUSHER(MechanicalRecipe.Kind.JAW_CRUSHING, 480, 8, 30, 1, 2, false, 25),
    SAW(MechanicalRecipe.Kind.SAWING, 60, 20, 120, 1, 2, false, 8),
    LATHE(MechanicalRecipe.Kind.TURNING, 40, 40, 200, 1, 2, false, 4),
    BELLOWS(null, 100, 5, 30, 0, 0, false, 6),
    NORIA(null, 200, 4, 20, 0, 0, false, 20),
    /** 0.20.42 : perceuse posee sur la roche, tire de la pierre (pierre -> pierre taillee, etc.) sans entree ; une case de sortie. */
    DRILL(null, 240, 8, 40, 0, 1, false, 12);

    @Nullable
    public final MechanicalRecipe.Kind recipe;
    /** Couple de travail (N.m), vitesses (tr/min). */
    public final double torque, minSpeed, nominalSpeed, inertia;
    public final int inputs, outputs;
    public final boolean mesh;

    MachineType(@Nullable MechanicalRecipe.Kind recipe, double torque, double minSpeed, double nominalSpeed, int inputs, int outputs, boolean mesh, double inertia) {
        this.recipe = recipe;
        this.torque = torque;
        this.minSpeed = minSpeed;
        this.nominalSpeed = nominalSpeed;
        this.inputs = inputs;
        this.outputs = outputs;
        this.mesh = mesh;
        this.inertia = inertia;
    }

    public String id() {
        return switch (this) {
            case MILLSTONE -> "millstone";
            case SIFTER -> "mechanical_sifter";
            case JAW_CRUSHER -> "jaw_crusher";
            case SAW -> "mechanical_saw";
            case LATHE -> "wood_lathe";
            case BELLOWS -> "bellows";
            case NORIA -> "noria";
            case DRILL -> "mechanical_drill";
        };
    }

    public boolean hasGui() {
        return recipe != null || this == DRILL;
    }

    public boolean vertical() {
        return this == MILLSTONE;
    }

    /** Puissance nominale (W). */
    public double nominalPower() {
        return torque * nominalSpeed * 2 * Math.PI / 60;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}

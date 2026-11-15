package fr.pioneers.space;

import fr.pioneers.Pioneers;
import fr.pioneers.core.PiTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Ergols des fusees (0.18.0) : chaque famille convient a une plage de tiers. Le pas de tir refuse de faire le plein d'une fusee avec un
 * ergol qui ne lui convient pas (statut {@code WRONG_FUEL}).
 * <ul>
 *     <li>kerolox (RP-1 + oxygene liquide) : T1-T2 ;</li>
 *     <li>hydrolox (hydrogene + oxygene liquides) : T1-T7 ;</li>
 *     <li>hydrogene liquide seul (propulsion nucleaire thermique) : T8-T10 ;</li>
 *     <li>ergol de fusion deuterium-helium 3 : T11-T13.</li>
 * </ul>
 * Fluides des tags {@code pioneers:propellants/<code>} ; un ancien carburant du tag {@code pioneers:rocket_fuel} sans famille compte comme
 * de l'hydrolox (mondes d'avant 0.18).
 */
public enum Propellant {
    KEROLOX("kerolox", 1, 2),
    HYDROLOX("hydrolox", 1, 7),
    NUCLEAR_THERMAL("nuclear_thermal", 8, 10),
    FUSION("fusion", 11, 13);

    public final String code;
    public final int minTier, maxTier;
    public final TagKey<Fluid> tag;

    Propellant(String code, int minTier, int maxTier) {
        this.code = code;
        this.minTier = minTier;
        this.maxTier = maxTier;
        this.tag = TagKey.create(Registries.FLUID, Pioneers.rl("propellants/" + code));
    }

    public boolean suits(RocketTier tier) {
        return tier.level >= minTier && tier.level <= maxTier;
    }

    public Component getName() {
        return Component.translatable("propellant.pioneers." + code);
    }

    /** Famille d'un fluide, null si ce n'est pas un ergol. */
    @Nullable
    public static Propellant of(FluidStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Fluid fluid = stack.getFluid();
        for (Propellant p : values()) {
            if (fluid.is(p.tag)) {
                return p;
            }
        }
        return fluid.is(PiTags.Fluids.ROCKET_FUEL) ? HYDROLOX : null;
    }

    /** L'ergol convient-il a ce tier de fusee ? */
    public static boolean suits(FluidStack stack, RocketTier tier) {
        Propellant p = of(stack);
        return p != null && p.suits(tier);
    }

    /** Ergol conseille pour un tier (le premier qui convient). */
    public static Propellant forTier(RocketTier tier) {
        for (Propellant p : values()) {
            if (p.suits(tier)) {
                return p;
            }
        }
        return FUSION;
    }
}

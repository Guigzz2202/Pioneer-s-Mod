package fr.pioneers.gear;

import fr.pioneers.Pioneers;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ForgeTier;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.TierSortingRegistry;

/**
 * 0.20.15 : les quatre tiers de l'equipement modulaire (memes ids que tools/gc/gear/spec.py) :
 * <ol>
 *     <li>Graviton (alliages de titane, age de la chimie) ;</li>
 *     <li>Stellaris (carbures et superalliages) ;</li>
 *     <li>Neutronique (metaux de l'espace) : premier tier qui accepte le module de vol ;</li>
 *     <li>Singularite (matieres du Simulateur d'univers).</li>
 * </ol>
 * Chaque tier fixe le nombre d'emplacements de module par objet, la reserve d'energie (FE) par objet, la protection de l'armure, la vitesse
 * des outils et les degats des armes. Les objets sont incassables : ils vivent de leur energie.
 */
public enum ModularTier {
    //1.0.3-beta (correctif, Guillaume : « buffe les armures du plus haut rang ») : Graviton (19) etait sous le set Voltaique charge (21)
    //et sous le nitinol (24) ; protection 24 / 25 / 26 / 27, robustesse 3,5 / 4,5 / 5,5 / 6,5 (avant 19 / 20 / 23 / 27 et 2 / 3 / 4 / 5)
    GRAVITON(3, 1_000_000L, new int[]{4, 9, 7, 4}, 3.5F, 0.05F, 12.0F, 8, 6.0F, Rarity.UNCOMMON, 0xB27AFF),
    STELLARIS(4, 4_000_000L, new int[]{4, 9, 8, 4}, 4.5F, 0.1F, 16.0F, 10, 9.0F, Rarity.RARE, 0xFFC65C),
    NEUTRONIC(6, 16_000_000L, new int[]{5, 9, 8, 4}, 5.5F, 0.15F, 22.0F, 13, 13.0F, Rarity.EPIC, 0x5CE8FF),
    SINGULARITY(8, 64_000_000L, new int[]{5, 10, 8, 4}, 6.5F, 0.2F, 30.0F, 17, 18.0F, Rarity.EPIC, 0xFF70E8);

    /** Emplacements de module par objet. */
    public final int slots;
    /** Reserve d'energie de base par objet (FE). */
    public final long energy;
    /** Protection : casque, plastron, jambieres, bottes. */
    private final int[] defense;
    public final float toughness, knockback;
    /** Vitesse de minage des outils (avec de l'energie). */
    public final float digSpeed;
    /** Degats de la lame (points, poing compris). */
    public final int bladeDamage;
    /** Degats d'un tir du fusil. */
    public final float rifleDamage;
    public final Rarity rarity;
    /** Couleur de la lueur (barre d'energie, tirs, textes). */
    public final int color;
    private Tier toolTier;

    ModularTier(int slots, long energy, int[] defense, float toughness, float knockback, float digSpeed, int bladeDamage, float rifleDamage,
          Rarity rarity, int color) {
        this.slots = slots;
        this.energy = energy;
        this.defense = defense;
        this.toughness = toughness;
        this.knockback = knockback;
        this.digSpeed = digSpeed;
        this.bladeDamage = bladeDamage;
        this.rifleDamage = rifleDamage;
        this.rarity = rarity;
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** MKI ... MKIV. */
    public String mk() {
        return switch (this) {
            case GRAVITON -> "MKI";
            case STELLARIS -> "MKII";
            case NEUTRONIC -> "MKIII";
            case SINGULARITY -> "MKIV";
        };
    }

    /** Protection de la piece (0 casque, 1 plastron, 2 jambieres, 3 bottes). */
    public int defense(int piece) {
        return defense[piece];
    }

    /** Debit de recharge d'un objet (FE/t). */
    public int transfer() {
        return (int) Math.min(Integer.MAX_VALUE, energy / 100);
    }

    /** Materiau d'outil (niveau de minage 4, apres la netherite) ; enregistre au premier appel. */
    public synchronized Tier toolTier() {
        if (toolTier == null) {
            toolTier = new ForgeTier(4, 0, digSpeed, bladeDamage - 1, 0, Tags.Blocks.NEEDS_NETHERITE_TOOL, () -> Ingredient.EMPTY);
            TierSortingRegistry.registerTier(toolTier, Pioneers.rl("modular_" + id()), List.of(Tiers.NETHERITE), List.of());
        }
        return toolTier;
    }
}

package fr.pioneers.forge;

import fr.pioneers.Pioneers;
import fr.pioneers.bronze.foundry.Metals;
import fr.pioneers.materials.GeneratedMaterials;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Forge primaire : SOURCE UNIQUE des donnees des metaux (fusion, temperature de travail, enclume, creuset, couleurs a chaud).
 * <p>
 * Les valeurs viennent des fiches {@code tools/gc/materials/materials.py} (champs {@code working}, {@code anvil}), ecrites dans
 * {@link GeneratedMaterials#METALS} par {@code tools/gc/materials/gen.py} (fonction {@code forge_data}). La table de fusion des objets reste
 * {@link GeneratedMaterials#MELTS} (lue par {@link Metals#melt}) ; {@link #meltOf} y ajoute le metal chauffe. Ne jamais recopier ces chiffres
 * ailleurs : passer par cette classe.
 */
public final class ForgeMetals {

    /** Temperature ambiante (degres C) : vers elle que tout refroidit. */
    public static final int AMBIENT_C = 20;
    public static final int INGOT_MB = Metals.INGOT;
    public static final int NUGGET_MB = 16;
    /** Un lingot = 32 pixels de matiere sur la grille 16 x 16 de l'enclume. */
    public static final int PIXELS_PER_INGOT = 32;
    /** Chute : 1 pepite pour 4 pixels coupes. */
    public static final int PIXELS_PER_NUGGET = 4;

    /** Enclumes : pierre (cuivre, etain, plomb, or, zinc, soudure), bronze (+ bronze, laiton, fer), fer (+ acier). 3 = forge industrielle. */
    public static final int ANVIL_STONE = 0, ANVIL_BRONZE = 1, ANVIL_IRON = 2, ANVIL_INDUSTRIAL = 3;

    /** Au-dessus : la piece brule la main qui la tient sans pince (degres C). */
    public static final int BURN_C = 400;
    /** En dessous : la piece est froide (n'est plus un « metal chauffe » a manipuler avec precaution). */
    public static final int COLD_C = 60;

    private ForgeMetals() {
    }

    @Nullable
    public static GeneratedMaterials.Metal get(String metal) {
        return Metals.get(metal);
    }

    /** Fusion (degres C) ; 1 000 pour un metal inconnu. */
    public static int meltingC(String metal) {
        return Metals.meltingC(metal);
    }

    /** Temperature de travail a l'enclume (degres C) : en dessous, les coups ne font rien. */
    public static int workingC(String metal) {
        GeneratedMaterials.Metal m = Metals.get(metal);
        return m == null ? 800 : m.working();
    }

    /** Enclume minimale ({@link #ANVIL_STONE}..{@link #ANVIL_INDUSTRIAL}). */
    public static int anvilTier(String metal) {
        GeneratedMaterials.Metal m = Metals.get(metal);
        return m == null ? ANVIL_INDUSTRIAL : m.anvil();
    }

    /** Vrai si le metal se fond au creuset d'argile (forge primaire) : metaux coulables des ages 1-2, sauf l'acier (haut fourneau). */
    public static boolean crucible(String metal) {
        GeneratedMaterials.Metal m = Metals.get(metal);
        return m != null && m.crucible();
    }

    /** Vrai si le metal se forge a la main (enclume de pierre, de bronze ou de fer). */
    public static boolean forgeable(String metal) {
        return anvilTier(metal) <= ANVIL_IRON;
    }

    /** Couleur du metal a froid (RGB). */
    public static int colour(String metal) {
        return Metals.colour(metal);
    }

    /** Metaux forgeables a la main, dans l'ordre des fiches. */
    public static List<String> forgeableMetals() {
        List<String> out = new ArrayList<>();
        for (GeneratedMaterials.Metal m : Metals.all()) {
            if (m.anvil() <= ANVIL_IRON) {
                out.add(m.id());
            }
        }
        return Collections.unmodifiableList(out);
    }

    // ------------------------------------------------------------------
    // Sources de chaleur necessaires
    // ------------------------------------------------------------------

    /** Ce qu'il faut pour fondre ce metal : feu de camp, + charbon de bois, + soufflet, sinon four industriel. */
    public static ForgeHeat.Level heatToMelt(String metal) {
        return ForgeHeat.Level.toReach(meltingC(metal));
    }

    // ------------------------------------------------------------------
    // Couleurs a chaud
    // ------------------------------------------------------------------

    /**
     * Couleur d'incandescence (ARGB) : alpha = intensite (0 sous 450 degres), rouge sombre, rouge, orange, jaune puis blanc vers 1 300 degres.
     * Sert a la teinte des objets, aux barres des infobulles et aux rendus (sans allocation).
     */
    public static int glow(float tempC) {
        if (tempC < 450) {
            return 0;
        }
        int r, g, b;
        float alpha = Math.min(1F, (tempC - 450F) / 350F);
        if (tempC < 650) {
            float t = (tempC - 450F) / 200F;
            r = lerp(0x5A, 0xA8, t);
            g = lerp(0x08, 0x18, t);
            b = 0;
        } else if (tempC < 850) {
            float t = (tempC - 650F) / 200F;
            r = lerp(0xA8, 0xF0, t);
            g = lerp(0x18, 0x6C, t);
            b = 0;
        } else if (tempC < 1100) {
            float t = (tempC - 850F) / 250F;
            r = lerp(0xF0, 0xFF, t);
            g = lerp(0x6C, 0xD8, t);
            b = lerp(0x00, 0x50, t);
        } else {
            float t = Math.min(1F, (tempC - 1100F) / 300F);
            r = 0xFF;
            g = lerp(0xD8, 0xF6, t);
            b = lerp(0x50, 0xE0, t);
        }
        return ((int) (alpha * 255) << 24) | (r << 16) | (g << 8) | b;
    }

    /** Teinte RGB d'une piece : couleur du metal melee a l'incandescence selon la temperature. */
    public static int tint(String metal, float tempC) {
        int base = colour(metal);
        int glow = glow(tempC);
        float a = (glow >>> 24) / 255F;
        if (a <= 0) {
            return base;
        }
        int r = lerp((base >> 16) & 0xFF, (glow >> 16) & 0xFF, a);
        int g = lerp((base >> 8) & 0xFF, (glow >> 8) & 0xFF, a);
        int b = lerp(base & 0xFF, glow & 0xFF, a);
        return (r << 16) | (g << 8) | b;
    }

    /** Couleur du metal en fusion (RGB) : la couleur du metal tiree vers l'orange vif. */
    public static int moltenColour(String metal) {
        return tint(metal, Math.max(1100, meltingC(metal)));
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    // ------------------------------------------------------------------
    // Objets
    // ------------------------------------------------------------------

    /**
     * Ce que donne un objet fondu : la table generee ({@link Metals#melt}), plus le metal chauffe (son metal et sa quantite). Null si l'objet ne
     * se fond pas.
     */
    @Nullable
    public static GeneratedMaterials.Melt meltOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (HeatedMetal.is(stack)) {
            String metal = HeatedMetal.metal(stack);
            return metal.isEmpty() ? null : new GeneratedMaterials.Melt(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), metal,
                  HeatedMetal.amount(stack));
        }
        return Metals.melt(stack);
    }

    /** Lingots du metal ({@code pioneers:<metal>_ingot}, sinon le premier objet du tag {@code forge:ingots/<metal>}), vide s'il n'y en a pas. */
    public static ItemStack ingot(String metal, int count) {
        return form(metal, "ingot", "ingots", count);
    }

    public static ItemStack nugget(String metal, int count) {
        return form(metal, "nugget", "nuggets", count);
    }

    /** Vrai si l'objet est un lingot (tag {@code forge:ingots}) d'un metal connu de la table de fusion. */
    public static boolean isIngot(ItemStack stack) {
        GeneratedMaterials.Melt melt = Metals.melt(stack);
        return melt != null && melt.mb() == INGOT_MB && stack.is(TagKey.create(Registries.ITEM,
              ResourceLocation.fromNamespaceAndPath("forge", "ingots")));
    }

    private static ItemStack form(String metal, String suffix, String tag, int count) {
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(Pioneers.rl(metal + "_" + suffix));
        if (item == Items.AIR) {
            TagKey<Item> key = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", tag + "/" + metal));
            for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(key)) {
                item = holder.value();
                break;
            }
        }
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    /** Lingots et pepites pour une quantite (mB) ; le reste sous une pepite est perdu. */
    public static List<ItemStack> asIngotsAndNuggets(String metal, int mb) {
        List<ItemStack> out = new ArrayList<>(2);
        int ingots = mb / INGOT_MB;
        int nuggets = (mb - ingots * INGOT_MB) / NUGGET_MB;
        ItemStack i = ingot(metal, ingots);
        if (!i.isEmpty()) {
            out.add(i);
        }
        ItemStack n = nugget(metal, nuggets);
        if (!n.isEmpty()) {
            out.add(n);
        }
        return out;
    }
}

package fr.pioneers.core.recipe;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.tech.bronze.bench.BenchRecipe.Counted;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe.FluidIn;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe.GasAmount;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fluids.FluidStack;

/**
 * MAJ Recettes (lot 7, selecteur de recette) : une recette telle que le selecteur la montre et la verifie, commune a toutes les familles de
 * machines (recettes industrielles, machines mecaniques, kit) : identifiant, tier minimal, entrees (objets, fluides, gaz, donnees) et
 * sorties. Construite des deux cotes a partir des recettes synchronisees : la liste du selecteur se calcule chez le client, et le serveur
 * s'en sert pour refuser une insertion etrangere a la recette verrouillee.
 */
public record RecipeChoice(ResourceLocation id, int tier, boolean industrialOnly, List<Counted> items, List<FluidIn> fluids, List<GasAmount> gases,
                           List<DataStack> data, List<ItemStack> outputs, List<FluidStack> fluidOutputs, List<GasAmount> gasOutputs, int time,
                           boolean turns) {

    /** 1.11.2 : duree de la recette au tier de base (ticks, ou tours d'arbre si {@code turns}) ; 0 : inconnue. */
    public RecipeChoice withTime(int time, boolean turns) {
        return new RecipeChoice(id, tier, industrialOnly, items, fluids, gases, data, outputs, fluidOutputs, gasOutputs, time, turns);
    }

    /** Recette industrielle (ateliers, controleurs, forge au charbon, four a coke). */
    public static RecipeChoice of(IndustrialRecipe r) {
        List<ItemStack> outs = new ArrayList<>();
        for (IndustrialRecipe.Result res : r.results()) {
            outs.add(res.stack());
        }
        if (r.draws() != null) {
            //Recycleur : les lots du tirage pondere
            for (IndustrialRecipe.Draw d : r.draws().pool()) {
                outs.add(d.item());
            }
        }
        return new RecipeChoice(r.getId(), r.tier(), r.industrialOnly(), r.items(), r.fluids(), r.gases(), r.data(), List.copyOf(outs),
              r.fluidResults(), r.gasResults(), r.time(), false);
    }

    /** Recette a une entree d'objets (machines mecaniques, kit). */
    public static RecipeChoice simple(ResourceLocation id, Ingredient input, int count, List<ItemStack> outputs) {
        return new RecipeChoice(id, 0, false, List.of(new Counted(input, Math.max(1, count))), List.of(), List.of(), List.of(), List.copyOf(outputs),
              List.of(), List.of(), 0, false);
    }

    /** MAJ Recettes (lot 8a) : recette du compacteur avec un liant (2e entree d'objets). */
    public static RecipeChoice withBinder(ResourceLocation id, Ingredient input, int count, Ingredient binder, int binderCount,
          List<ItemStack> outputs) {
        return new RecipeChoice(id, 0, false, List.of(new Counted(input, Math.max(1, count)), new Counted(binder, Math.max(1, binderCount))),
              List.of(), List.of(), List.of(), List.copyOf(outputs), List.of(), List.of(), 0, false);
    }

    /** L'objet est-il un des ingredients de la recette ? */
    public boolean takesItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (Counted c : items) {
            if (c.ingredient().test(stack)) {
                return true;
            }
        }
        return false;
    }

    public boolean takesFluid(FluidStack stack) {
        for (FluidIn in : fluids) {
            if (in.test(stack)) {
                return true;
            }
        }
        return false;
    }

    public boolean takesGas(GasStack stack) {
        for (GasAmount in : gases) {
            if (in.test(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Nombre de sortes d'entrees (objets, fluides, gaz, donnees). */
    public int inputKinds() {
        return items.size() + fluids.size() + gases.size() + data.size();
    }

    /**
     * Sortes d'entrees de la recette deja presentes dans la machine (sans regarder les quantites) : classement du selecteur (celles qui
     * collent aux entrees presentes d'abord) et avertissement « plusieurs recettes possibles ».
     */
    public int present(List<ItemStack> haveItems, List<FluidStack> haveFluids, List<GasStack> haveGases) {
        int n = 0;
        for (Counted c : items) {
            for (ItemStack s : haveItems) {
                if (!s.isEmpty() && c.ingredient().test(s)) {
                    n++;
                    break;
                }
            }
        }
        for (FluidIn in : fluids) {
            for (FluidStack s : haveFluids) {
                if (in.test(s)) {
                    n++;
                    break;
                }
            }
        }
        for (GasAmount in : gases) {
            for (GasStack s : haveGases) {
                if (in.test(s)) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    /** Toutes les entrees (hors donnees) sont presentes. */
    public boolean ready(List<ItemStack> haveItems, List<FluidStack> haveFluids, List<GasStack> haveGases) {
        int kinds = items.size() + fluids.size() + gases.size();
        return kinds > 0 && present(haveItems, haveFluids, haveGases) == kinds;
    }

    /** Icone : le premier produit (objet), sinon le premier ingredient. */
    public ItemStack icon() {
        for (ItemStack s : outputs) {
            if (!s.isEmpty()) {
                return s;
            }
        }
        for (Counted c : items) {
            ItemStack[] all = c.ingredient().getItems();
            if (all.length > 0) {
                return all[0];
            }
        }
        return ItemStack.EMPTY;
    }

    /** Nom affiche : le premier produit (objet, fluide ou gaz), sinon l'identifiant. */
    public Component name() {
        for (ItemStack s : outputs) {
            if (!s.isEmpty()) {
                return s.getHoverName();
            }
        }
        for (FluidStack f : fluidOutputs) {
            if (!f.isEmpty()) {
                return f.getDisplayName();
            }
        }
        for (GasAmount g : gasOutputs) {
            GasStack s = g.stack();
            if (!s.isEmpty()) {
                return s.getGas().getName();
            }
        }
        return Component.literal(id.getPath());
    }

    /** Texte de recherche : noms des produits et des entrees, en minuscules sans accents (filtre du selecteur au clavier). */
    public String searchText() {
        StringBuilder b = new StringBuilder(id.getPath()).append(' ');
        for (ItemStack s : outputs) {
            b.append(s.getHoverName().getString()).append(' ');
        }
        for (FluidStack f : fluidOutputs) {
            b.append(f.getDisplayName().getString()).append(' ');
        }
        for (GasAmount g : gasOutputs) {
            GasStack s = g.stack();
            if (!s.isEmpty()) {
                b.append(s.getGas().getName().getString()).append(' ');
            }
        }
        for (Counted c : items) {
            ItemStack[] all = c.ingredient().getItems();
            if (all.length > 0) {
                b.append(all[0].getHoverName().getString()).append(' ');
            }
        }
        for (FluidIn in : fluids) {
            List<FluidStack> shown = in.displayed();
            if (!shown.isEmpty()) {
                b.append(shown.get(0).getDisplayName().getString()).append(' ');
            }
        }
        for (GasAmount g : gases) {
            GasStack s = g.stack();
            if (!s.isEmpty()) {
                b.append(s.getGas().getName().getString()).append(' ');
            }
        }
        return fold(b.toString());
    }

    /** Minuscules sans accents (recherche). */
    public static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Le produit principal est-il cet objet ? (glisser un objet de JEI sur la case Recette) */
    public boolean makes(ItemStack stack) {
        for (ItemStack s : outputs) {
            if (!s.isEmpty() && ItemStack.isSameItem(s, stack)) {
                return true;
            }
        }
        return false;
    }
}

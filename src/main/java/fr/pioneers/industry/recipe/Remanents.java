package fr.pioneers.industry.recipe;

import fr.pioneers.bronze.bench.BenchRecipe.Counted;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 44, retour 62 de Guillaume : « Les items types "Moules" "Forme d'extrusion" et autres items remanants doivent aller dans un
 * slot reserve dans leur machine ») : objets REMANENTS et CASE « OUTIL » des machines.
 * <p>
 * Un remanent est de l'outillage que sa recette rend intact ou a l'usure (matrices, filiere et formes d'extrusion, photomasque, catalyseurs,
 * disque diamante, objectifs de gravure qui s'usent...). Source unique : {@code tools/gc/core/p105_lot44.py} -> {@link GeneratedRemanents}
 * (par machine : cle du catalogue industriel, ou {@code steam:<machine>}). Une machine qui a des remanents a une case Outil (la derniere de
 * ses cases : sauvegardes d'avant intactes) ; DANS cette machine, un ingredient remanent est toujours lu dans cette case (et jamais dans
 * une case d'entree, qui le refuse), pris comme avant (une operation, un outil ; k operations d'un multibloc, k outils) et rendu DANS LA
 * CASE avec la chance de la recette (usure). Moteur : {@link RecipeMatch} (ateliers et multiblocs), {@code SteamProcess} (vapeur).
 */
public final class Remanents {

    /** Prefixe des cles des machines a vapeur. */
    public static final String STEAM = "steam:";

    /**
     * L'outil d'une recette : indice de l'ingredient objet, indice du produit qui le rend (-1 : la recette le consomme), chance du retour (0
     * si consomme), outil d'usure ({@link WearTool} : objectifs de gravure).
     */
    public record Tool(int input, int result, float keep, boolean wear) {

        /** La recette rend-elle l'outil (meme avec une usure) ? */
        public boolean returned() {
            return result >= 0;
        }
    }

    private static final Map<String, Set<Item>> ITEMS = new HashMap<>();

    private Remanents() {
    }

    /** La machine ({@code machineKey}) a-t-elle une case Outil ? */
    public static boolean hasToolSlot(String machineKey) {
        return GeneratedRemanents.BY_MACHINE.containsKey(machineKey);
    }

    /** Identifiants des remanents de la machine (vide : pas de case Outil). */
    public static List<String> ids(String machineKey) {
        return GeneratedRemanents.BY_MACHINE.getOrDefault(machineKey, List.of());
    }

    /** Sorte d'un remanent ({@code die}, {@code extrusion}, {@code tool}, {@code catalyst}, {@code objective}) ou null. */
    @Nullable
    public static String kind(String id) {
        return GeneratedRemanents.KIND.get(id);
    }

    /** Objets remanents de la machine (resolus une fois, apres le chargement des registres). */
    public static Set<Item> items(String machineKey) {
        synchronized (ITEMS) {
            Set<Item> set = ITEMS.get(machineKey);
            if (set == null) {
                set = new HashSet<>();
                for (String id : ids(machineKey)) {
                    Item item = item(id);
                    if (item != Items.AIR) {
                        set.add(item);
                    }
                }
                //Registres pas encore prets (un objet manque) : on ne garde pas un ensemble incomplet
                if (set.size() == ids(machineKey).size()) {
                    set = Collections.unmodifiableSet(set);
                    ITEMS.put(machineKey, set);
                }
            }
            return set;
        }
    }

    /** Resolution des identifiants : le registre de Forge en jeu ; un banc hors jeu (tools/remanent) donne la sienne ({@link #lookup}). */
    private static java.util.function.Function<ResourceLocation, Item> lookup = rl -> {
        Item forge = ForgeRegistries.ITEMS.getValue(rl);
        return forge == null ? Items.AIR : forge;
    };

    /** Banc hors jeu : resolution des identifiants par ses registres (le cache est vide). */
    public static void lookup(java.util.function.Function<ResourceLocation, Item> f) {
        synchronized (ITEMS) {
            lookup = f;
            ITEMS.clear();
        }
    }

    /** Objet d'un identifiant (AIR s'il manque). */
    static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return Items.AIR;
        }
        Item item = lookup.apply(rl);
        return item == null ? Items.AIR : item;
    }

    /** L'objet est-il un remanent de cette machine (sa place : la case Outil) ? */
    public static boolean isTool(String machineKey, ItemStack stack) {
        return !stack.isEmpty() && hasToolSlot(machineKey) && items(machineKey).contains(stack.getItem());
    }

    /** Premier remanent de la machine qu'accepte l'ingredient (null : ce n'est pas un outil de cette machine). */
    @Nullable
    public static Item toolOf(String machineKey, Ingredient ingredient) {
        if (!hasToolSlot(machineKey)) {
            return null;
        }
        for (Item item : items(machineKey)) {
            if (ingredient.test(new ItemStack(item))) {
                return item;
            }
        }
        return null;
    }

    /** Outil d'une recette industrielle (null : aucun) ; calcule une fois par recette ({@link IndustrialRecipe#tool()}). */
    @Nullable
    static Tool find(IndustrialRecipe recipe) {
        String key = recipe.machine().key;
        if (!hasToolSlot(key)) {
            return null;
        }
        List<Counted> items = recipe.items();
        for (int i = 0; i < items.size(); i++) {
            Item tool = toolOf(key, items.get(i).ingredient());
            if (tool == null) {
                continue;
            }
            List<IndustrialRecipe.Result> results = recipe.results();
            for (int r = 0; r < results.size(); r++) {
                if (results.get(r).stack().is(tool)) {
                    return new Tool(i, r, results.get(r).chance(), tool instanceof WearTool);
                }
            }
            return new Tool(i, -1, 0F, tool instanceof WearTool);
        }
        return null;
    }

    /**
     * Outil d'une liste d'entrees comptees (recettes a vapeur, JEI) : indice de l'entree outil, ou -1. {@code machineKey} : cle de la machine
     * ({@code steam:press}...).
     */
    public static int toolInput(String machineKey, List<Counted> inputs) {
        if (!hasToolSlot(machineKey)) {
            return -1;
        }
        for (int i = 0; i < inputs.size(); i++) {
            if (toolOf(machineKey, inputs.get(i).ingredient()) != null) {
                return i;
            }
        }
        return -1;
    }

    /** Ingredient outil d'une recette (industrielle ou a vapeur : bouton « + » de JEI), ou null. */
    @Nullable
    public static Ingredient ingredientOf(Object recipe) {
        if (recipe instanceof IndustrialRecipe r && r.tool() != null) {
            return r.items().get(r.tool().input()).ingredient();
        }
        if (recipe instanceof fr.pioneers.bronze.machines.SteamRecipe r) {
            int i = toolInput(STEAM + r.machine().key(), r.inputs());
            return i < 0 ? null : r.inputs().get(i).ingredient();
        }
        return null;
    }

    /** Objets que montre la case Outil vide (fantome) d'une recette : ceux de l'ingredient outil. */
    public static List<ItemStack> shown(Counted input) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : input.ingredient().getItems()) {
            out.add(s.copyWithCount(input.count()));
        }
        return out;
    }
}

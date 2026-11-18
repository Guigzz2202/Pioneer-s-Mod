package fr.pioneers.core.recipe;

import fr.pioneers.api.gas.GasStack;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * MAJ Recettes (lot 7) : machine a selecteur de recette. Sa case fantome « Recette » ouvre la liste de ses recettes ; une recette
 * verrouillee est la seule qu'elle fait, et ses cases et reservoirs refusent tout ce qui n'y entre pas (entonnoirs, tapis, robots,
 * logistique, ports). Sans verrou, la regle de la machine s'applique (la recette la plus complete, puis la premiere par identifiant).
 * <p>
 * Familles : machines d'atelier et controleurs des multiblocs industriels, forge au charbon et four a coke (recettes industrielles), machines
 * mecaniques a recettes (sauf le tamis, dont la maille choisit deja), machines du kit. Les autres machines a plusieurs recettes choisissent
 * deja la leur (etablis, ateliers a vapeur, tables d'assemblage, gabarit du marteau-pilon, postes de la cuisine).
 */
public interface RecipeLockable {

    /** Source des recettes de la machine ({@link RecipeSources}) : la meme liste, dans le meme ordre, chez le client et le serveur. */
    String recipeSource();

    /** Le verrou de la machine. */
    RecipeLock recipeLock();

    /** Serveur : le verrou vient de changer (sauvegarde, envoi au client, nouvelle recherche de recette). */
    void recipeLockChanged();

    /** Case « Recette » de l'interface (coin de l'objet, relatif au panneau), ou null : pas de case dans l'interface. */
    @Nullable
    int[] recipeSlot();

    /** Tier de la machine (0 = MKI ... 3 = MKIV) : les recettes d'un tier au-dessus sont signalees (« Tier insuffisant »). */
    int recipeTier();

    /** Objets presents dans les entrees (classement du selecteur, avertissement). */
    List<ItemStack> presentItems();

    default List<FluidStack> presentFluids() {
        return List.of();
    }

    default List<GasStack> presentGases() {
        return List.of();
    }

    @Nullable
    Level recipeLevel();

    /** Les recettes que la machine sait faire (vide : pas de selecteur). */
    default List<RecipeChoice> recipeChoices() {
        Level level = recipeLevel();
        return level == null ? List.of() : RecipeSources.choices(level, recipeSource());
    }

    @Nullable
    default ResourceLocation lockedRecipe() {
        return recipeLock().id();
    }

    /** La recette verrouillee (null : aucun verrou, ou recette disparue : la machine fait alors comme sans verrou). */
    @Nullable
    default RecipeChoice lockedChoice() {
        return recipeLock().resolve(recipeChoices());
    }

    /** 1.0.6 : recette verrouillee, ou null sans verrou (jauges partagees : ResourceRows). */
    @Nullable
    default RecipeChoice lockedIfSet() {
        return recipeLock().isSet() ? lockedChoice() : null;
    }

    /**
     * Serveur : pose ({@code id}) ou retire ({@code null}) le verrou. Faux si la recette n'est pas une recette de cette machine (rien ne
     * change).
     */
    default boolean setLockedRecipe(@Nullable ResourceLocation id) {
        if (id != null && find(recipeChoices(), id) == null) {
            return false;
        }
        if (recipeLock().set(id)) {
            recipeLockChanged();
        }
        return true;
    }

    /**
     * Filtre d'insertion des objets : sans verrou (ou verrou inconnu), {@code otherwise} (calcule seulement alors) ; sinon les ingredients de
     * la recette seulement.
     */
    default boolean lockAllows(ItemStack stack, BooleanSupplier otherwise) {
        RecipeChoice locked = recipeLock().isSet() ? lockedChoice() : null;
        return locked == null ? otherwise.getAsBoolean() : locked.takesItem(stack);
    }

    /** Filtre d'insertion des fluides (comme {@link #lockAllows}). */
    default boolean lockAllowsFluid(FluidStack stack, BooleanSupplier otherwise) {
        RecipeChoice locked = recipeLock().isSet() ? lockedChoice() : null;
        return locked == null ? otherwise.getAsBoolean() : locked.takesFluid(stack);
    }

    /** Filtre d'insertion des gaz (comme {@link #lockAllows}). */
    default boolean lockAllowsGas(GasStack stack, BooleanSupplier otherwise) {
        RecipeChoice locked = recipeLock().isSet() ? lockedChoice() : null;
        return locked == null ? otherwise.getAsBoolean() : locked.takesGas(stack);
    }

    /**
     * 1.11.1 (migration) : serveur, appele de temps en temps par la machine. Un verrou pose sur une recette que la machine ne fait plus (recette
     * retiree, ou partie vers une autre machine : composants de l'assembleur vers l'assembleur de composants) est efface proprement : la
     * machine revient a sa regle sans verrou au lieu d'afficher « recette introuvable ». Vrai si le verrou a ete efface.
     */
    default boolean dropMissingLock() {
        if (!recipeLock().isSet() || lockedChoice() != null) {
            return false;
        }
        if (recipeChoices().isEmpty()) {
            return false;                                                 // recettes pas encore chargees : rien a juger
        }
        recipeLock().set(null);
        recipeLockChanged();
        return true;
    }

    @Nullable
    static RecipeChoice find(List<RecipeChoice> choices, ResourceLocation id) {
        for (RecipeChoice c : choices) {
            if (c.id().equals(id)) {
                return c;
            }
        }
        return null;
    }
}

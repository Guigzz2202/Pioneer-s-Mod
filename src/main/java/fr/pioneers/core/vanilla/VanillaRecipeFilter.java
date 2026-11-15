package fr.pioneers.core.vanilla;

import fr.pioneers.Pioneers;
import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.tech.bronze.bench.BenchType;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.core.registries.PiBronze;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraftforge.common.crafting.CompoundIngredient;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 0.19.15 : regle de Guillaume « des qu'une recette est faisable dans un de nos etablis, on supprime la recette vanilla ». Les recettes vanilla
 * sont deja desactivees par des fichiers {@code data/minecraft/recipes} (T:core/devanilla.py), mais certaines restaient faisables en jeu
 * (canne a peche, cisailles, seau...). Filet de securite cote serveur : au demarrage et a chaque /reload, toute recette de fabrication
 * {@code minecraft:*} dont le resultat se fait sur un etabli ({@code pioneers:bench}) ou est un objet vanilla remplace par le mod est retiree,
 * puis les recettes sont renvoyees aux joueurs (JEI, livre de recettes).
 * <p>
 * 1.0.1 (« la torche refuse notre charbon ») : les surcharges {@code data/minecraft/recipes} ne s'appliquent pas toujours en jeu (ordre des
 * paquets de donnees : la torche demandait encore le charbon vanilla). Toute recette chargee (vanilla, du mod ou d'un autre mod) dont un
 * ingredient accepte un objet vanilla remplace accepte desormais aussi l'objet du mod ({@link #widen}) ; les recettes de cuisson et du
 * tailleur de pierre qui donnent un objet vanilla remplace donnent l'objet du mod. Independant de l'ordre des paquets.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class VanillaRecipeFilter {

    private VanillaRecipeFilter() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStarting(ServerStartingEvent event) {
        filter(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        MinecraftServer server = event.getPlayerList().getServer();
        if (filter(server) > 0 && event.getPlayer() == null) {
            //Rechargement : les clients recoivent la liste filtree (sans attendre l'envoi vanilla, s'il a deja eu lieu)
            event.getPlayerList().broadcastAll(new ClientboundUpdateRecipesPacket(server.getRecipeManager().getRecipes()));
        }
    }

    /**
     * Matieres de depart que les etablis font mieux mais que la grille doit garder (sinon on ne pourrait meme pas fabriquer le premier etabli) :
     * planches, baton, sable, gravier, poudre d'os.
     */
    private static boolean basic(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !"minecraft".equals(id.getNamespace())) {
            return false;
        }
        String p = id.getPath();
        return p.endsWith("_planks") || p.equals("stick") || p.equals("sand") || p.equals("gravel") || p.equals("bone_meal");
    }

    /** Retire les recettes vanilla redondantes et elargit les ingredients aux objets du mod ; renvoie le nombre de recettes changees. */
    public static int filter(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        Set<Item> onBench = new HashSet<>();
        for (BenchRecipe recipe : manager.getAllRecipesFor(PiBronze.BENCH.get())) {
            if (!basic(recipe.result())) {
                onBench.add(recipe.result().getItem());
            }
        }
        Set<Item> replaced = new HashSet<>(VanillaReplacements.replaced());
        //1.12.0 (lot E4) : le metier a tisser n'est plus fabricable (remplace par l'etabli du tanneur ; le bloc reste dans le monde)
        replaced.add(net.minecraft.world.item.Items.LOOM);
        List<Recipe<?>> kept = new ArrayList<>();
        int removed = 0;
        int widened = 0;
        for (Recipe<?> recipe : manager.getRecipes()) {
            //1.0.4-beta (lot 4) : un vehicule (tag pioneers:vehicles : bateaux, radeaux, wagonnets, trains, navires) ne se fabrique qu'aux Ateliers
            //ferroviaires et au chantier naval : toute recette de grille ou d'etabli qui en donne un est retiree, quel que soit son paquet
            if (vehicleCraft(recipe, server)) {
                removed++;
                continue;
            }
            if ("minecraft".equals(recipe.getId().getNamespace()) && recipe.getType() == RecipeType.CRAFTING) {
                ItemStack out = recipe.getResultItem(server.registryAccess());
                if (!out.isEmpty() && (onBench.contains(out.getItem()) || replaced.contains(out.getItem()))) {
                    removed++;
                    continue;
                }
            }
            Recipe<?> fixed = devanilla(recipe, server);
            if (fixed != recipe) {
                widened++;
            }
            kept.add(fixed);
        }
        if (removed > 0 || widened > 0) {
            manager.replaceRecipes(kept);
            Pioneers.LOGGER.info("Recettes vanilla retirees (faites sur nos etablis ou objets remplaces) : {} ; recettes elargies aux objets du mod : {}",
                  removed, widened);
        }
        return removed + widened;
    }

    /** Vehicules : seuls les Ateliers ferroviaires et le chantier naval les fabriquent (tag genere par T:core/p104_lot4.py). */
    public static final TagKey<Item> VEHICLES = TagKey.create(Registries.ITEM, Pioneers.rl("vehicles"));

    /**
     * 1.0.5-beta (lot 32, retour 47 de Guillaume : « Les bateaux en bois peuvent etre faits en dehors du chantier naval ») : un vehicule = le tag
     * {@link #VEHICLES} OU tout bateau, bateau a coffre ou radeau ({@link BoatItem}, vanilla et autres mods), sans dependre du tag (lu aussi
     * cote client, avant que les tags n'arrivent).
     */
    public static boolean isVehicle(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof BoatItem || stack.is(VEHICLES));
    }

    /**
     * 1.0.5-beta (lot 36, retour 61 de Guillaume : « Pour les bateaux en bois il fallait justement que ces derniers soient craftables ailleurs
     * que dans le chantier naval » ; reponse : « a l'etabli du charpentier + au chantier naval (pas l'etabli vanilla) » ; le lot 32 avait
     * compris l'inverse) : LISTE BLANCHE des recettes de vehicule = chantier naval et Ateliers ferroviaires (recettes industrielles), plus
     * l'etabli du CHARPENTIER pour les bateaux en bois ({@link BoatItem} : bateaux, bateaux a coffre, radeaux de tous les bois ; recettes
     * ecrites par T:core/devanilla.py, {@code recipes/wooden_boats}). Aucune autre recette (grille vanilla, autres etablis, autres
     * machines, autres paquets) ne donne de vehicule. Lu des deux cotes (serveur et client, avant JEI).
     */
    public static boolean carpenterBoat(Recipe<?> recipe) {
        return recipe instanceof BenchRecipe b && b.bench() == BenchType.CARPENTER && b.result() != null
              && b.result().getItem() instanceof BoatItem;
    }

    /** Recette qui donne un vehicule hors des Ateliers ferroviaires et du chantier naval (voir {@link #vehicleCraft(Recipe, RegistryAccess)}). */
    public static boolean vehicleCraft(Recipe<?> recipe, MinecraftServer server) {
        return vehicleCraft(recipe, server.registryAccess());
    }

    /**
     * Recette qui donne un vehicule ailleurs qu'aux Ateliers ferroviaires ou au chantier naval. 1.0.4-beta (lot 4) : grille (tout paquet) et
     * etablis du mod ; 1.0.5-beta (lot 32) : TOUT type de recette (grille, etablis, machines du mod, recettes d'autres mods), tous les produits
     * d'une recette industrielle ; seules les recettes {@code pioneers:industrial} des machines {@code shipyard} et {@code rail_works} restent ;
     * 1.0.5-beta (lot 36) : et les bateaux en bois de l'etabli du charpentier ({@link #carpenterBoat}).
     * Les recettes vanilla des bateaux sont aussi neutralisees par fichier ({@code data/minecraft/recipes}, condition {@code forge:false}),
     * mais Forge recrit lui-meme celles des bateaux a coffre (son paquet de donnees, ordre non garanti) : ce filtre en est le filet.
     */
    public static boolean vehicleCraft(Recipe<?> recipe, RegistryAccess access) {
        if (carpenterBoat(recipe)) {
            return false;
        }
        if (recipe instanceof IndustrialRecipe ir) {
            if (ir.machine() == IndustryMachine.SHIPYARD || ir.machine() == IndustryMachine.RAIL_WORKS) {
                return false;
            }
            for (IndustrialRecipe.Result r : ir.results()) {
                if (isVehicle(r.stack())) {
                    return true;
                }
            }
            return false;
        }
        ItemStack out;
        try {
            out = recipe instanceof BenchRecipe b ? b.result() : recipe.getResultItem(access);
        } catch (RuntimeException e) {
            return false;
        }
        return out != null && isVehicle(out);
    }

    /**
     * 1.0.5-beta (lot 32) : cote CLIENT (JEI, livre de recettes) : retire des recettes recues celles qui donnent un vehicule hors des ateliers,
     * meme si un serveur les envoie ; renvoie le nombre retire. Appele par {@code J:client/vanilla/VehicleRecipeClient} a la reception des
     * recettes (avant JEI).
     */
    public static int filterVehicles(RecipeManager manager, RegistryAccess access) {
        List<Recipe<?>> kept = new ArrayList<>();
        int removed = 0;
        for (Recipe<?> recipe : manager.getRecipes()) {
            if (vehicleCraft(recipe, access)) {
                removed++;
            } else {
                kept.add(recipe);
            }
        }
        if (removed > 0) {
            manager.replaceRecipes(kept);
        }
        return removed;
    }

    /**
     * Ingredient qui accepte aussi les objets du mod remplacant les objets vanilla qu'il accepte (charbon vanilla -> charbon du mod...), ou
     * l'ingredient lui-meme. Seuls les ingredients ordinaires (objets, tags) sont elargis ; les ingredients speciaux (NBT...) sont gardes.
     */
    public static Ingredient widen(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty() || ingredient.getClass() != Ingredient.class) {
            return ingredient;
        }
        Set<Item> extra = new LinkedHashSet<>();
        for (ItemStack stack : ingredient.getItems()) {
            Item to = VanillaReplacements.replacementOf(stack.getItem());
            if (to != null && !ingredient.test(new ItemStack(to))) {
                extra.add(to);
            }
        }
        if (extra.isEmpty()) {
            return ingredient;
        }
        return CompoundIngredient.of(ingredient, Ingredient.of(extra.toArray(new Item[0])));
    }

    /** Elargit une liste d'ingredients sur place ; vrai si elle a change. */
    private static boolean widenAll(NonNullList<Ingredient> list) {
        boolean changed = false;
        for (int i = 0; i < list.size(); i++) {
            Ingredient w = widen(list.get(i));
            if (w != list.get(i)) {
                list.set(i, w);
                changed = true;
            }
        }
        return changed;
    }

    /** Resultat converti en l'objet du mod (cuisson, tailleur de pierre). */
    private static ItemStack result(Recipe<?> recipe, MinecraftServer server) {
        return VanillaReplacements.convert(recipe.getResultItem(server.registryAccess()).copy());
    }

    /** La recette, ou une copie dont les ingredients acceptent les objets du mod (et dont le resultat de cuisson est l'objet du mod). */
    static Recipe<?> devanilla(Recipe<?> recipe, MinecraftServer server) {
        try {
            Class<?> c = recipe.getClass();
            if (c == ShapedRecipe.class) {
                ShapedRecipe r = (ShapedRecipe) recipe;
                NonNullList<Ingredient> items = NonNullList.create();
                items.addAll(r.getIngredients());
                return widenAll(items) ? new ShapedRecipe(r.getId(), r.getGroup(), r.category(), r.getWidth(), r.getHeight(), items,
                      r.getResultItem(server.registryAccess()), r.showNotification()) : recipe;
            }
            if (c == ShapelessRecipe.class) {
                ShapelessRecipe r = (ShapelessRecipe) recipe;
                NonNullList<Ingredient> items = NonNullList.create();
                items.addAll(r.getIngredients());
                return widenAll(items) ? new ShapelessRecipe(r.getId(), r.getGroup(), r.category(), r.getResultItem(server.registryAccess()), items)
                      : recipe;
            }
            if (recipe instanceof AbstractCookingRecipe r && (c == SmeltingRecipe.class || c == BlastingRecipe.class || c == SmokingRecipe.class
                  || c == CampfireCookingRecipe.class)) {
                Ingredient in = r.getIngredients().get(0);
                Ingredient w = widen(in);
                ItemStack out = result(r, server);
                if (w == in && out.getItem() == r.getResultItem(server.registryAccess()).getItem()) {
                    return recipe;
                }
                if (c == SmeltingRecipe.class) {
                    return new SmeltingRecipe(r.getId(), r.getGroup(), r.category(), w, out, r.getExperience(), r.getCookingTime());
                }
                if (c == BlastingRecipe.class) {
                    return new BlastingRecipe(r.getId(), r.getGroup(), r.category(), w, out, r.getExperience(), r.getCookingTime());
                }
                if (c == SmokingRecipe.class) {
                    return new SmokingRecipe(r.getId(), r.getGroup(), r.category(), w, out, r.getExperience(), r.getCookingTime());
                }
                return new CampfireCookingRecipe(r.getId(), r.getGroup(), r.category(), w, out, r.getExperience(), r.getCookingTime());
            }
            if (c == StonecutterRecipe.class) {
                Ingredient in = recipe.getIngredients().get(0);
                Ingredient w = widen(in);
                ItemStack out = result(recipe, server);
                if (w == in && out.getItem() == recipe.getResultItem(server.registryAccess()).getItem()) {
                    return recipe;
                }
                return new StonecutterRecipe(recipe.getId(), recipe.getGroup(), w, out);
            }
            //Autres recettes (du mod ou d'autres mods) : liste d'ingredients modifiee sur place quand elle le permet
            widenAll(recipe.getIngredients());
        } catch (RuntimeException e) {
            //Liste immuable ou recette speciale : gardee telle quelle
        }
        return recipe;
    }
}

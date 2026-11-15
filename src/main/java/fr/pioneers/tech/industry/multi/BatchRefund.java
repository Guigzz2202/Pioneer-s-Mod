package fr.pioneers.tech.industry.multi;

import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 42 ; lots 38 a 41, retours 51, 52, 56 et 64) : UN SEUL MECANISME pour le lot en cours d'un multibloc industriel (entrees
 * prises au DEPART, recette sauvee sous {@code batch}) dont la recette n'est plus a cette machine au chargement d'un monde : retiree par un
 * generateur (113 copies de la taille de pierre a la presse, lot 38 ; acier du creuset, lot 39 ; brique crue a la presse, lot 40), partie a
 * une autre machine (creuset -> four a induction, lot 39 ; 208 recettes de presse, lot 40) ou renommee (lots 34, 38, 39).
 * <ul>
 *   <li>REPRISE par sa remplacante sur cette machine ({@link IndustryRecipes#successor}) seulement si elle prend EXACTEMENT les memes entrees
 *       (memes objets, fluides et gaz, memes nombres) ;</li>
 *   <li>sinon les entrees d'avant, deja prises, sont RENDUES ({@link #give}) : objets dans les sorties du controleur (sinon au sol devant lui),
 *       fluides et gaz dans ses reservoirs d'entree. Rien ne se perd, rien ne se cree (regle du cout matiere de Guillaume).</li>
 * </ul>
 * Entrees d'avant : table GENEREE {@link GeneratedBatchRefund} (registre tools/gc/core/batch_ledger.json de toutes les recettes qu'un multibloc
 * peut avoir en lot, tenu par tools/gc/core/p105_lot42.py a chaque regeneration : un retrait futur y est couvert sans code nouveau) ; a
 * defaut, la recette du meme identifiant partie a une autre machine (entrees d'apres, garde-fou si la table n'a pas ete regeneree).
 * Remplace les tables {@code PiRemaps.P105_LOT39_REFUND}, {@code P105_LOT40_REFUND} et {@code MultiProcess.movedInputs}.
 * <p>
 * La partie pure ({@link #retired}, {@link #parse}, {@link #same}, {@link #decide}) n'utilise aucun registre du jeu : banc hors jeu
 * tools/batchrefund/RefundBench.java. Public pour les GameTests ({@link #of}, {@link #restart}).
 */
public final class BatchRefund {

    /** Une entree : {@code ref} = identifiant ou {@code #tag} ; {@code live} = ingredient ou fluide de la recette du jeu (comparaison). */
    public record Ref(String ref, long count, @Nullable Object live) {

        public boolean tag() {
            return ref.startsWith("#");
        }
    }

    /** Entrees d'UNE operation. */
    public record Inputs(List<Ref> items, List<Ref> fluids, List<Ref> gases) {

        public boolean isEmpty() {
            return items.isEmpty() && fluids.isEmpty() && gases.isEmpty();
        }
    }

    /** Decision : reprendre le lot avec la remplacante, rendre ses entrees, ou rien a faire (recette inconnue ou toujours la). */
    public enum Kind { RESUME, REFUND, NONE }

    /** Plan d'un lot sauve : {@code next} (reprise), {@code inputs} (entrees rendues), {@code source} (table, deplacee, aucune). */
    public record Plan(Kind kind, @Nullable IndustrialRecipe next, @Nullable Inputs inputs, String source) {
    }

    private BatchRefund() {
    }

    // ------------------------------------------------------------------ partie pure (banc hors jeu)

    /** Cle de la table : {@code machine|chemin} (espace pioneers seulement ; null sinon). */
    @Nullable
    public static String key(String machine, @Nullable ResourceLocation id) {
        return id == null || !fr.pioneers.Pioneers.MODID.equals(id.getNamespace()) ? null : machine + "|" + id.getPath();
    }

    /** Entrees d'avant d'une recette qui n'est plus a la machine {@code machine} (table generee) ; null si elle n'y est pas. */
    @Nullable
    public static Inputs retired(String machine, @Nullable ResourceLocation id) {
        String k = key(machine, id);
        String[] row = k == null ? null : GeneratedBatchRefund.TABLE.get(k);
        return row == null ? null : parse(row);
    }

    /** Ligne de la table {objets, fluides, gaz} : listes {@code ref*n} separees par des virgules. */
    public static Inputs parse(String[] row) {
        return new Inputs(refs(row.length > 0 ? row[0] : ""), refs(row.length > 1 ? row[1] : ""), refs(row.length > 2 ? row[2] : ""));
    }

    private static List<Ref> refs(String s) {
        if (s == null || s.isEmpty()) {
            return List.of();
        }
        List<Ref> out = new ArrayList<>();
        for (String part : s.split(",")) {
            int star = part.lastIndexOf('*');
            if (star > 0) {
                out.add(new Ref(part.substring(0, star), Long.parseLong(part.substring(star + 1)), null));
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Memes entrees : autant d'entrees de chaque ressource, et chaque entree d'avant a, dans la nouvelle recette, une entree du meme nombre qui
     * l'accepte ({@code accepts(nouvelle, ancienne)}), chacune servant une fois.
     */
    public static boolean same(Inputs old, Inputs next, BiPredicate<Ref, Ref> accepts) {
        return sameList(old.items(), next.items(), accepts) && sameList(old.fluids(), next.fluids(), accepts)
               && sameList(old.gases(), next.gases(), accepts);
    }

    private static boolean sameList(List<Ref> old, List<Ref> next, BiPredicate<Ref, Ref> accepts) {
        if (old.size() != next.size()) {
            return false;
        }
        boolean[] used = new boolean[next.size()];
        for (Ref o : old) {
            boolean found = false;
            for (int i = 0; i < next.size() && !found; i++) {
                if (!used[i] && next.get(i).count() == o.count() && accepts.test(next.get(i), o)) {
                    used[i] = true;
                    found = true;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /** Egalite des references (banc, gaz) : meme identifiant ou meme tag. */
    public static boolean sameRef(Ref next, Ref old) {
        return next.ref().equals(old.ref());
    }

    /**
     * Decision pure : reprise si une remplacante existe et prend les memes entrees (ou si les entrees d'avant sont inconnues : recette
     * dominee, encore chargee) ; sinon rendu si les entrees d'avant sont connues ; sinon rien.
     */
    public static Kind decide(@Nullable Inputs old, @Nullable Inputs next, BiPredicate<Ref, Ref> accepts) {
        if (next != null && (old == null || same(old, next, accepts))) {
            return Kind.RESUME;
        }
        if (old != null && !old.isEmpty()) {
            return Kind.REFUND;
        }
        return Kind.NONE;
    }

    // ------------------------------------------------------------------ jeu

    /** Plan du lot sauve {@code id} d'un multibloc de la machine {@code machine}. */
    public static Plan plan(Level level, IndustryMachine machine, @Nullable ResourceLocation id) {
        if (id == null) {
            return new Plan(Kind.NONE, null, null, "aucune");
        }
        IndustrialRecipe here = IndustryRecipes.byId(level, machine, id);
        if (here != null) {
            //toujours a cette machine (hors de sa liste du moment : separateur de gaz hors d'une atmosphere) : le lot continue tel quel
            return new Plan(Kind.RESUME, here, null, "presente");
        }
        String source = "table";
        Inputs old = retired(machine.key, id);
        if (old == null) {
            old = moved(level, machine, id);
            source = old == null ? "inconnue" : "deplacee";
        }
        IndustrialRecipe next = IndustryRecipes.successor(level, machine, id);
        Kind kind = decide(old, next == null ? null : of(next), (n, o) -> accepts(n, o));
        return switch (kind) {
            case RESUME -> new Plan(kind, next, null, source);
            case REFUND -> new Plan(kind, null, old, source);
            case NONE -> new Plan(kind, null, null, source);
        };
    }

    /** Entrees d'une recette du jeu (ingredients et fluides gardes pour la comparaison). */
    public static Inputs of(IndustrialRecipe r) {
        List<Ref> items = new ArrayList<>(), fluids = new ArrayList<>(), gases = new ArrayList<>();
        for (BenchRecipe.Counted x : r.items()) {
            ItemStack[] stacks = x.ingredient().getItems();
            items.add(new Ref(stacks.length > 0 ? String.valueOf(ForgeRegistries.ITEMS.getKey(stacks[0].getItem())) : "?", x.count(), x.ingredient()));
        }
        for (IndustrialRecipe.FluidIn f : r.fluids()) {
            fluids.add(new Ref(f.fluid() != null ? String.valueOf(ForgeRegistries.FLUIDS.getKey(f.fluid())) : "#" + f.tag().location(), f.amount(), f));
        }
        for (IndustrialRecipe.GasAmount g : r.gases()) {
            gases.add(new Ref(g.gas().toString(), g.amount(), null));
        }
        return new Inputs(items, fluids, gases);
    }

    /** L'entree {@code next} de la nouvelle recette accepte-t-elle l'entree d'avant {@code old} ? */
    private static boolean accepts(Ref next, Ref old) {
        if (next.live() instanceof net.minecraft.world.item.crafting.Ingredient ing) {
            Item item = item(old.ref());
            return item != Items.AIR && ing.test(new ItemStack(item));
        }
        if (next.live() instanceof IndustrialRecipe.FluidIn f) {
            Fluid fluid = fluid(old.ref());
            return fluid != null && (f.fluid() != null ? f.fluid().isSame(fluid) : f.tag() != null && fluid.is(f.tag()));
        }
        return sameRef(next, old);
    }

    /** Garde-fou : recette du meme identifiant partie a une AUTRE machine (entrees d'apres) ; null sinon. */
    @Nullable
    private static Inputs moved(Level level, IndustryMachine machine, ResourceLocation id) {
        for (IndustrialRecipe r : fr.pioneers.core.recipes.PiRecipes.sorted(level, fr.pioneers.core.registries.PiIndustry.INDUSTRIAL.get())) {
            if (r.getId().equals(id)) {
                return r.machine() == machine ? null : of(r);
            }
        }
        return null;
    }

    /** Objet d'une reference : identifiant, ou premier objet du tag (du mod d'abord). */
    public static Item item(String ref) {
        if (ref.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(ref.substring(1));
            if (tag == null || ForgeRegistries.ITEMS.tags() == null) {
                return Items.AIR;
            }
            Item first = Items.AIR;
            for (Item i : ForgeRegistries.ITEMS.tags().getTag(TagKey.create(net.minecraft.core.registries.Registries.ITEM, tag))) {
                ResourceLocation k = ForgeRegistries.ITEMS.getKey(i);
                if (k != null && fr.pioneers.Pioneers.MODID.equals(k.getNamespace())) {
                    return i;
                }
                if (first == Items.AIR) {
                    first = i;
                }
            }
            return first;
        }
        ResourceLocation id = ResourceLocation.tryParse(ref);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return item == null ? Items.AIR : item;
    }

    /** Fluide d'une reference : identifiant, ou premier fluide source du tag. */
    @Nullable
    public static Fluid fluid(String ref) {
        if (ref.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(ref.substring(1));
            if (tag == null) {
                return null;
            }
            TagKey<Fluid> key = TagKey.create(net.minecraft.core.registries.Registries.FLUID, tag);
            return ForgeRegistries.FLUIDS.getValues().stream().filter(x -> x.is(key) && x.isSource(x.defaultFluidState())).findFirst().orElse(null);
        }
        ResourceLocation id = ResourceLocation.tryParse(ref);
        return id == null ? null : ForgeRegistries.FLUIDS.getValue(id);
    }

    /**
     * Rend {@code count} operations des entrees {@code r} au controleur : objets dans ses sorties (sinon au sol devant lui), fluides et gaz
     * dans ses reservoirs d'entree (ce qui n'y tient pas est signale au journal : aucun autre endroit ne peut les garder).
     */
    public static void give(ControllerTile c, Inputs r, int count) {
        if (!(c.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        int k = Math.max(1, count);
        ItemSlots out = new ItemSlots(c.outputs, Automation.INTERNAL);
        for (Ref x : r.items()) {
            Item item = item(x.ref());
            if (item == Items.AIR) {
                fr.pioneers.Pioneers.LOGGER.warn("Lot rendu : objet inconnu {} ({})", x.ref(), c.getBlockPos());
                continue;
            }
            long n = x.count() * k;
            while (n > 0) {
                int part = (int) Math.min(n, item.getMaxStackSize());
                ItemStack rest = ItemHandlerHelper.insertItemStacked(out, new ItemStack(item, part), false);
                if (!rest.isEmpty()) {
                    net.minecraft.world.Containers.dropItemStack(level, c.getBlockPos().getX() + 0.5, c.getBlockPos().getY() + 1.0,
                          c.getBlockPos().getZ() + 0.5, rest);
                }
                n -= part;
            }
        }
        for (Ref x : r.fluids()) {
            Fluid fluid = fluid(x.ref());
            if (fluid != null) {
                FluidStack left = new FluidStack(fluid, (int) Math.min(Integer.MAX_VALUE, x.count() * k));
                for (net.minecraftforge.fluids.capability.templates.FluidTank t : c.fluidIn) {
                    left.shrink(t.fill(left, IFluidHandler.FluidAction.EXECUTE));
                }
                if (!left.isEmpty()) {
                    fr.pioneers.Pioneers.LOGGER.warn("Lot rendu : {} mB de {} sans place ({})", left.getAmount(), x.ref(), c.getBlockPos());
                }
            }
        }
        for (Ref x : r.gases()) {
            ResourceLocation id = ResourceLocation.tryParse(x.ref());
            fr.pioneers.api.gas.Gas gas = id == null ? null : fr.pioneers.api.PioneersAPI.gas(id);
            if (gas == null) {
                fr.pioneers.Pioneers.LOGGER.warn("Lot rendu : gaz inconnu {} ({})", x.ref(), c.getBlockPos());
                continue;
            }
            fr.pioneers.api.gas.GasStack left = fr.pioneers.api.gas.GasStack.of(gas, x.count() * k);
            for (fr.pioneers.core.gas.BasicGasTank t : c.gasIn) {
                left = t.insert(left, fr.pioneers.core.Action.EXECUTE, Automation.INTERNAL);
            }
            if (!left.isEmpty()) {
                fr.pioneers.Pioneers.LOGGER.warn("Lot rendu : {} de {} sans place ({})", left.getAmount(), x.ref(), c.getBlockPos());
            }
        }
    }

    /**
     * GameTests : rend le lot de {@code count} operations de la recette {@code id} au controleur comme au chargement d'un monde ; vrai si
     * elle n'est plus a cette machine et que ses entrees ont ete rendues (faux si elle y est, ou si une remplacante aux memes entrees le
     * reprend).
     */
    public static boolean of(ControllerTile controller, ResourceLocation id, int count) {
        Plan p = plan(controller.getLevel(), controller.machine, id);
        if (p.kind() != Kind.REFUND) {
            return false;
        }
        give(controller, p.inputs(), count);
        return true;
    }

    /** GameTests : relance la recherche de recette du controleur (chemin reel d'un lot sauve au chargement, {@code MultiProcess.start}). */
    public static void restart(ControllerTile controller) {
        controller.process.start();
    }

    /** GameTests : recette du lot en cours ({@code null} sans lot) et nombre d'operations. */
    @Nullable
    public static ResourceLocation batch(ControllerTile controller) {
        return controller.process.batch == null ? null : controller.process.batch.getId();
    }

    public static int batchCount(ControllerTile controller) {
        return controller.process.batchCount;
    }
}

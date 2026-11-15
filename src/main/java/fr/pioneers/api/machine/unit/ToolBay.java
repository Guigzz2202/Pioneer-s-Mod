package fr.pioneers.api.machine.unit;

import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.ContentsListener;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.industry.RockSamples;
import fr.pioneers.industry.recipe.Remanents;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 44, retour 62 de Guillaume : « Les items types "Moules" "Forme d'extrusion" et autres items remanants doivent aller dans un
 * slot reserve dans leur machine ») : la CASE « OUTIL » des machines a remanents ({@link Remanents}), commune aux ateliers MKI a MKIV
 * ({@link IndustryProcess}), aux controleurs des multiblocs industriels ({@code ControllerTile}) et aux machines a vapeur
 * ({@code SteamProcess}).
 * <ul>
 *     <li>derniere case de la machine (les index des cases d'avant ne bougent pas : mondes existants) ;</li>
 *     <li>filtre : les remanents de la machine seulement (recette verrouillee : le sien) ; les cases d'entree les refusent ;</li>
 *     <li>REGLE de l'automatisation (pas de face dediee) : toute face d'entree d'objets (cables, entonnoirs, ports d'objets d'un multibloc) et
 *     Maj + clic depuis l'inventaire y rangent un remanent ; jamais extraite de l'exterieur ni ejectee (seules les sorties le sont) ;</li>
 *     <li>case vide : fantome de l'outil de la recette verrouillee, sinon le pictogramme {@code ghost_tool} ;</li>
 *     <li>monde existant ({@link #move}) : un remanent reste dans une case d'entree passe dans la case Outil au chargement, sinon il est garde
 *     (cle {@code ioOverflow}) et pose au sol au premier tick : rien ne se perd.</li>
 * </ul>
 */
public final class ToolBay {

    /** Pictogramme de la case vide ({@code textures/gui/machine/ghost_tool.png}, tools/gc/core/p105_lot44.py). */
    public static final String GHOST = "ghost_tool";

    private ToolBay() {
    }

    /** La case Outil de la machine {@code machineKey} (cle du catalogue, ou {@code steam:<machine>}) a la place (x, y). */
    public static ItemSlot slot(String machineKey, RecipeLockable lock, @Nullable ContentsListener changed, int x, int y) {
        return new Bay(s -> Remanents.isTool(machineKey, s) && (lock.recipeLevel() == null || lock.lockAllows(s, () -> true)), changed, x, y)
              .ghost(() -> lockedTool(machineKey, lock)).ghostIcon(GHOST);
    }

    /**
     * 1.0.5-beta (lot 46, retour 72 : « echantillon + eau + lave jamais consommee ») : CASE D'ECHANTILLON du generateur de roches, meme
     * mecanisme que la case Outil (case reservee, derniere case, jamais extraite de l'exterieur ni ejectee, lisere dore) : une roche de la liste
     * permise au tier de la machine ({@code tier} 0..3 : {@link fr.pioneers.industry.RockSamples#refusal}), UNE seule ; fantome : le pave.
     */
    public static ItemSlot sample(int tier, @Nullable ContentsListener changed, int x, int y) {
        return new Sample(tier, changed, x, y).ghost(Items.COBBLESTONE);
    }

    /** Case d'echantillon : un seul bloc, jamais consomme (la machine lit son objet et produit la meme roche). */
    static final class Sample extends ItemSlot {

        Sample(int tier, @Nullable ContentsListener changed, int x, int y) {
            super(() -> 1, s -> RockSamples.accepts(s) && RockSamples.refusal(s, tier) == null,
                  (s, a) -> true, (s, a) -> !a.external(), changed, x, y);
        }

        @Override
        public int getLimit(ItemStack stack) {
            return 1;
        }
    }

    /**
     * La case Outil : comme une case ordinaire, sauf que des outils NON EMPILABLES sans NBT ni usure (matrices a lingot, a plaque, a tiges, a
     * engrenage) s'y rangent jusqu'a 64 : un multibloc en prend un par operation en parallele (comme quand chaque matrice prenait sa case
     * d'entree). Ce qui en sort ne depasse jamais une pile normale (souris, Maj + clic : un a la fois) ; les objectifs de gravure (usure)
     * restent seuls.
     */
    static final class Bay extends ItemSlot {

        Bay(java.util.function.Predicate<ItemStack> filter, @Nullable ContentsListener changed, int x, int y) {
            super(() -> 64, filter, (s, a) -> true, (s, a) -> !a.external(), changed, x, y);
        }

        @Override
        public int getLimit(ItemStack stack) {
            if (!stack.isEmpty() && stack.getMaxStackSize() == 1 && !stack.hasTag() && !stack.isDamageableItem()) {
                return 64;
            }
            return super.getLimit(stack);
        }
    }

    /** Fantome de la case Outil : l'outil de la recette verrouillee (vide sans verrou ou sans outil). */
    public static ItemStack lockedTool(String machineKey, RecipeLockable lock) {
        RecipeChoice c = lock.recipeLock().isSet() ? lock.lockedChoice() : null;
        if (c == null) {
            return ItemStack.EMPTY;
        }
        int i = Remanents.toolInput(machineKey, c.items());
        if (i < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack[] shown = c.items().get(i).ingredient().getItems();
        return shown.length == 0 ? ItemStack.EMPTY : shown[0].copyWithCount(1);
    }

    /**
     * MONDE EXISTANT : un remanent de la machine reste d'avant dans une case d'entree passe dans la case Outil (meme objet : ajoute jusqu'a sa
     * limite) ; ce qui n'y tient pas va dans {@code overflow} (sauve sous {@code ioOverflow}, pose au sol au premier tick). Vrai si quelque chose
     * a bouge.
     */
    public static boolean move(String machineKey, List<ItemSlot> inputs, @Nullable ItemSlot tool, List<ItemStack> overflow) {
        if (tool == null) {
            return false;
        }
        boolean moved = false;
        for (ItemSlot in : inputs) {
            ItemStack stack = in.getStack();
            if (!Remanents.isTool(machineKey, stack)) {
                continue;
            }
            ItemStack held = tool.getStack();
            ItemStack rest = stack.copy();
            int room = held.isEmpty() ? tool.getLimit(rest) : ItemStack.isSameItemSameTags(held, rest) ? tool.getLimit(held) - held.getCount() : 0;
            int put = Math.max(0, Math.min(room, rest.getCount()));
            if (put > 0) {
                tool.setStack(rest.copyWithCount(put + (held.isEmpty() ? 0 : held.getCount())));
                rest.shrink(put);
            }
            if (!rest.isEmpty()) {
                overflow.add(rest);
            }
            in.setStack(ItemStack.EMPTY);
            moved = true;
        }
        return moved;
    }

    /** La case Outil d'une machine (atelier, controleur de multibloc, machine a vapeur), ou null. */
    @Nullable
    public static ItemSlot of(@Nullable net.minecraft.world.level.block.entity.BlockEntity be) {
        if (be instanceof fr.pioneers.bronze.machines.SteamProcessTile steam) {
            return steam.process().toolSlot();
        }
        if (be instanceof fr.pioneers.industry.recipe.RecipeMatch.Buffers b && !b.toolSlots().isEmpty()) {
            return b.toolSlots().get(0);
        }
        return null;
    }

    /** Entrees presentes (selecteur : recettes faisables) : celles des cases d'entree, puis la case Outil. */
    public static List<ItemStack> present(List<ItemStack> inputs, @Nullable ItemSlot tool) {
        if (tool != null) {
            inputs.add(tool.getStack());
        }
        return inputs;
    }

    /**
     * Ports d'objets d'un multibloc (entree) : la case Outil passe en DERNIERE case du gestionnaire des entrees ; un remanent offert a une case
     * d'entree y est refuse (filtre), il entre donc par elle.
     */
    public static IItemHandler ports(IItemHandler inputs, @Nullable ItemSlot tool) {
        return tool == null ? inputs : new IItemHandler() {
            @Override
            public int getSlots() {
                return inputs.getSlots() + 1;
            }

            @NotNull
            @Override
            public ItemStack getStackInSlot(int slot) {
                return slot == inputs.getSlots() ? tool.getStack() : inputs.getStackInSlot(slot);
            }

            @NotNull
            @Override
            public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                return slot == inputs.getSlots() ? tool.insert(stack, Action.get(!simulate), Automation.EXTERNAL) : inputs.insertItem(slot, stack, simulate);
            }

            @NotNull
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return slot == inputs.getSlots() ? ItemStack.EMPTY : inputs.extractItem(slot, amount, simulate);
            }

            @Override
            public int getSlotLimit(int slot) {
                return slot == inputs.getSlots() ? 64 : inputs.getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return slot == inputs.getSlots() ? tool.isItemValid(stack) : inputs.isItemValid(slot, stack);
            }
        };
    }
}

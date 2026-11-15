package fr.pioneers.farm.kitchen;

import fr.pioneers.Pioneers;
import fr.pioneers.core.vanilla.VanillaReplacements;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * 1.6 (lot A2, demande de Guillaume : « un vrai fumoir qui ressemble vraiment a un fumoir ») : fumoir du mod (grand poste 1 x 2,
 * {@link BigStationBlock}), qui remplace le fumoir de Minecraft.
 * <ul>
 *     <li>Case 0 : le foyer, qui ne brule que du bois, des copeaux, de la sciure ou du charbon de bois (tag {@code pioneers:smokehouse_fuels}) ;
 *     le feu couve : chaque combustible dure deux fois plus longtemps qu'au four.</li>
 *     <li>Cases 1 a 4 : les crochets ; chaque piece fume a son rythme (recettes {@code pioneers:smoking} a un ingredient : viandes et poissons
 *     d'abord seches au sechoir, voir recipes_food.py smoking()).</li>
 *     <li>Sans recette du mod, les recettes de cuisson du fumoir de Minecraft ({@code minecraft:smoking}, ex. crustaces) sont rejouees, quatre
 *     fois plus lentes ; leur resultat vanilla est remplace par celui du mod ({@link VanillaReplacements}).</li>
 * </ul>
 * 1.0.5-beta (lot 41, retour 60) : interface standard (jauge du feu, case du foyer, 4 crochets a barre d'avancement) ; plus de combustible ni
 * de piece a la main.
 */
public class SmokehouseTile extends StationTiles.Slots {

    public static final TagKey<Item> FUELS = ItemTags.create(Pioneers.rl("smokehouse_fuels"));
    /** Cuisson vanilla rejouee : duree x 4 (au moins 10 s). */
    static final int VANILLA_SLOWDOWN = 4;
    /** Recettes vanilla traduites (par recette, liberees au rechargement des recettes). */
    private static final Map<SmokingRecipe, KitchenRecipe> VANILLA = Collections.synchronizedMap(new WeakHashMap<>());

    public SmokehouseTile(BlockPos pos, BlockState state) {
        super(pos, state, 5, 1);
    }

    @Override
    Station station() {
        return Station.SMOKING;
    }

    @Override
    boolean running() {
        return lit();
    }

    @Nullable
    @Override
    KitchenRecipe recipeFor(ItemStack stack) {
        KitchenRecipe r = findSingle(Station.SMOKING, stack);
        return r != null ? r : vanilla(stack);
    }

    /** Recette de cuisson du fumoir vanilla pour cet objet, traduite en recette du poste. */
    @Nullable
    private KitchenRecipe vanilla(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return null;
        }
        Optional<SmokingRecipe> found = level.getRecipeManager().getRecipeFor(RecipeType.SMOKING, new SimpleContainer(stack), level);
        if (found.isEmpty()) {
            return null;
        }
        SmokingRecipe v = found.get();
        return VANILLA.computeIfAbsent(v, x -> {
            ItemStack out = VanillaReplacements.convert(x.getResultItem(level.registryAccess()).copy());
            return new KitchenRecipe(x.getId(), Station.SMOKING, List.of(new KitchenRecipe.Counted(x.getIngredients().get(0), 1)), null,
                  Math.max(200, x.getCookingTime() * VANILLA_SLOWDOWN), 0, List.of(new KitchenRecipe.Result(out, 1F)), FluidStack.EMPTY, false, false);
        });
    }

    /** Ticks de feu d'un combustible du fumoir (0 : refuse). */
    static int fuelTicks(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(FUELS)) {
            return 0;
        }
        return Math.max(400, ForgeHooks.getBurnTime(stack, null)) * 2;
    }

    @Override
    protected void burnTick() {
        boolean was = burn > 0;
        if (burn > 0) {
            burn--;
        }
        if (burn <= 0) {
            ItemStack fuel = items.get(0);
            int t = fuelTicks(fuel);
            if (t > 0) {
                burn = burnMax = t;
                ItemStack rest = fuel.getCraftingRemainingItem();
                fuel.shrink(1);
                if (fuel.isEmpty() && !rest.isEmpty()) {
                    items.set(0, rest);
                }
                sync();
            }
        }
        boolean now = burn > 0;
        if (was != now && level != null) {
            BlockState s = getBlockState();
            if (s.hasProperty(StationBlock.LIT)) {
                level.setBlock(worldPosition, s.setValue(StationBlock.LIT, now), 3);
            }
            sync();
        }
    }

    @Override
    protected boolean acceptsAutomation(int slot, ItemStack stack) {
        if (slot == 0) {
            return fuelTicks(stack) > 0;
        }
        return super.acceptsAutomation(slot, stack);
    }

    @Override
    void done(int slot) {
        StationTiles.sound(this, SoundEvents.CAMPFIRE_CRACKLE, 0.7F, 0.8F);
    }
}

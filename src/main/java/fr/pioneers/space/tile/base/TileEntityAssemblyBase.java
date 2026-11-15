package fr.pioneers.space.tile.base;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.recipes.AssemblyRecipe;
import fr.pioneers.core.recipes.AssemblyStation;
import fr.pioneers.core.recipes.PiRecipes;
import fr.pioneers.core.registries.PiItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Logique commune des tables d'assemblage (fusees, rover, modules, equipements), sur le socle.
 * <p>
 * La grille de 5x10 cases prend la forme de la recette choisie ({@link AssemblyRecipe}, data-driven) : chaque case n'accepte que
 * son ingredient (les tuyaux remplissent donc la bonne case), le plan eventuel n'est pas consomme, et tout le reste l'est a la fin.
 * <p>
 * Interface standard large (236) : valeurs 4 avancement (pour mille), 5 en cours, 6 repetition, 7 consommation de la recette (FE/t) ;
 * actions {@link #CYCLE_BACK}, {@link #CYCLE_NEXT}, {@link #START}, {@link #STOP}, {@link #REPEAT}. La recette choisie et l'etat du rendu
 * suivent le bloc (client).
 */
public abstract class TileEntityAssemblyBase extends MachineTile {

    /** Grille de 5 lignes de 10 colonnes (lot 8b, vraie v2 : a droite de la reserve d'energie, sous l'en-tete du produit). */
    public static final int GRID_X = 25, GRID_Y = 33;
    /** Colonne de droite : plan, avancement, sortie ; batterie a la place commune (lot 8b). */
    public static final int BLUEPRINT_X = 213, BLUEPRINT_Y = 38, OUTPUT_X = 213, OUTPUT_Y = 84, ENERGY_X = StandardLayout.BATTERY_X,
          ENERGY_Y = StandardLayout.BATTERY_Y;
    public static final int GUI_WIDTH = 236, GUI_HEIGHT = StandardLayout.ZONE;
    /** Lot 8b : en-tete (fleches et produit), barre d'avancement, rangee des boutons et de l'etat. */
    public static final StandardLayout.Rect HEADER = new StandardLayout.Rect(26, 17, 180, 15), BAR = new StandardLayout.Rect(218, 59, 6, 20),
          BUTTONS = new StandardLayout.Rect(26, 128, GUI_WIDTH - 8 - 26, 14);
    /** Lot 8b : valeur nommee « repetition ». */
    public static final UiValues.Key REPEAT_ON = UiValues.Key.of("repeat");
    public static final int CYCLE_BACK = CUSTOM, CYCLE_NEXT = CUSTOM + 1, START = CUSTOM + 2, STOP = CUSTOM + 3, REPEAT = CUSTOM + 4;

    private final List<ItemSlot> gridSlots = new ArrayList<>();
    private final ItemSlot outputSlot, energySlot, blueprintSlot;

    private int progress;
    private boolean building;
    private boolean repeat;
    /** Recette choisie (suit le bloc vers le client). */
    @Nullable
    private ResourceLocation selected;

    //Cote client (rendu)
    private boolean clientShowProduct;
    private boolean clientBuilding;
    private int clientStage;

    protected TileEntityAssemblyBase(BlockEntityType<?> type, BlockPos pos, BlockState state, long energyCapacity) {
        super(type, pos, state, energyCapacity, (int) Math.min(Integer.MAX_VALUE, Math.max(20_000, energyCapacity / 8)));
        //Ancien ordre des cases (Mekanism) : grille, sortie, batterie, plan
        for (int row = 0; row < AssemblyRecipe.ROWS; row++) {
            for (int col = 0; col < AssemblyRecipe.COLUMNS; col++) {
                int index = row * AssemblyRecipe.COLUMNS + col;
                //Chaque case n'accepte que l'ingredient attendu ; l'automatisation ne peut pas en retirer
                gridSlots.add(slot(new ItemSlot(() -> 64, stack -> {
                    Ingredient ingredient = cellIngredient(index);
                    return ingredient != null && ingredient.test(stack);
                }, (stack, automation) -> true, (stack, automation) -> !automation.external(), this::setChanged,
                      GRID_X + col * 18 + 1, GRID_Y + row * 18 + 1)));
            }
        }
        outputSlot = slot(new ItemSlot(() -> 64, stack -> true, (stack, automation) -> !automation.external(), (stack, automation) -> true,
              this::setChanged, OUTPUT_X, OUTPUT_Y));
        energySlot = batterySlot(ENERGY_X, ENERGY_Y);
        blueprintSlot = slot(new ItemSlot(() -> 64, stack -> true, (stack, automation) -> true, (stack, automation) -> !automation.external(),
              this::setChanged, BLUEPRINT_X, BLUEPRINT_Y).ghost(PiItems.BLUEPRINT_TIER_2.get()));
    }

    /** Poste de cette table (filtre des recettes). */
    public abstract AssemblyStation getStation();

    @Nullable
    private Ingredient cellIngredient(int index) {
        AssemblyRecipe recipe = getSelectedRecipe();
        return recipe == null ? null : recipe.cell(index);
    }

    @Override
    public int guiWidth() {
        return GUI_WIDTH;
    }

    @Override
    public int guiHeight() {
        return GUI_HEIGHT;
    }

    @Override
    public String guiTier() {
        return guiName();
    }

    /** Identifiant de la table (textures de l'interface a sa couleur). */
    protected abstract String guiName();

    // ------------------------------------------------------------------
    // Boucle
    // ------------------------------------------------------------------

    @Override
    protected void tick() {
        discharge(energySlot, 20_000);
        boolean active = false;
        AssemblyRecipe recipe = getSelectedRecipe();
        if (recipe == null) {
            building = false;
            progress = 0;
        } else {
            if (!building && repeat && outputSlot.isEmpty() && isUnlocked(recipe) && hasRecipe()) {
                building = true;
                progress = 0;
            }
            if (building && !isUnlocked(recipe)) {
                //Plan retire en cours d'assemblage
                building = false;
                progress = 0;
                setChanged();
            }
            if (building) {
                ItemStack result = recipe.getResult();
                //Sans ses ingredients, l'assemblage reste en pause ; 1.9.2 : de meme sans sa structure (multiblocs d'assemblage)
                if (canWork() && structureReady() && hasRecipe() && outputSlot.insert(result, Action.SIMULATE, Automation.INTERNAL).isEmpty() && use(buildEnergyPerTick())) {
                    active = true;
                    if (++progress >= recipe.getDuration()) {
                        consumeRecipe(recipe);
                        outputSlot.insert(result, Action.EXECUTE, Automation.INTERNAL);
                        progress = 0;
                        building = false;
                        onAssembled(result);
                    }
                    setChanged();
                }
            }
        }
        markWorking(active);
        boolean show = building || !outputSlot.isEmpty();
        int stage = (int) (getScaledProgress() * 20);
        if (show != clientShowProduct || stage != clientStage || building != clientBuilding) {
            clientShowProduct = show;
            clientBuilding = building;
            clientStage = stage;
            markSync();
        }
    }

    /**
     * 1.9.2 (retours de Guillaume) : la structure de la table est-elle complete ? Toujours vrai pour les tables d'un bloc ; les multiblocs
     * d'assemblage (baie robotique, hall des fusees) la verifient. Cote client : derniere valeur recue.
     */
    public boolean structureReady() {
        return true;
    }

    /** 1.9.2 : ce qui manque a la structure (ecran du controleur), ou null. */
    @Nullable
    public Component structureProblem() {
        return null;
    }

    /** 1.9.2 : case de sortie (ports de sortie des multiblocs d'assemblage). */
    protected ItemSlot outputSlot() {
        return outputSlot;
    }

    /** Assemblage termine (effets). */
    protected void onAssembled(ItemStack result) {
    }

    private void consumeRecipe(AssemblyRecipe recipe) {
        for (int i = 0; i < gridSlots.size(); i++) {
            if (recipe.usesSlot(i)) {
                gridSlots.get(i).extract(1, Action.EXECUTE, Automation.INTERNAL);
            }
        }
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        switch (action) {
            case CYCLE_BACK -> cycle(-1);
            case CYCLE_NEXT -> cycle(1);
            case START -> startBuilding();
            case STOP -> cancelBuilding();
            case REPEAT -> toggleRepeat();
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    // Recette choisie
    // ------------------------------------------------------------------

    @Nullable
    public AssemblyRecipe getSelectedRecipe() {
        return level == null ? null : PiRecipes.assembly(level, getStation(), selected);
    }

    public List<AssemblyRecipe> getRecipes() {
        return level == null ? List.of() : PiRecipes.assembly(level, getStation());
    }

    /** Fleches de choix du produit (seulement a l'arret). */
    public void cycle(int step) {
        List<AssemblyRecipe> recipes = getRecipes();
        if (building || recipes.isEmpty()) {
            return;
        }
        AssemblyRecipe current = getSelectedRecipe();
        int index = current == null ? 0 : recipes.indexOf(current);
        selected = recipes.get(Math.floorMod(index + step, recipes.size())).getId();
        setChanged();
        syncNow();
    }

    /** Toutes les cases du motif contiennent leur ingredient. */
    public boolean hasRecipe() {
        return missingIngredients() == 0;
    }

    /** Cases du motif vides ou garnies du mauvais objet. */
    public int missingIngredients() {
        AssemblyRecipe recipe = getSelectedRecipe();
        if (recipe == null) {
            return AssemblyRecipe.CELLS;
        }
        int missing = 0;
        for (int i = 0; i < gridSlots.size(); i++) {
            Ingredient ingredient = recipe.cell(i);
            if (ingredient != null && !ingredient.test(gridSlots.get(i).getStack())) {
                missing++;
            }
        }
        return missing;
    }

    /** Case garnie d'un objet que la recette choisie n'attend pas (a retirer). */
    public boolean isWrongItem(int index) {
        ItemStack stack = gridSlots.get(index).getStack();
        if (stack.isEmpty()) {
            return false;
        }
        Ingredient ingredient = cellIngredient(index);
        return ingredient == null || !ingredient.test(stack);
    }

    public boolean isGridSlotEmpty(int index) {
        return gridSlots.get(index).isEmpty();
    }

    /** Plan present (si la recette en demande un) et recherche terminee. */
    public boolean isUnlocked(AssemblyRecipe recipe) {
        return recipe.acceptsBlueprint(blueprintSlot.getStack()) && recipe.isResearched();
    }

    public boolean isBlueprintMissing() {
        AssemblyRecipe recipe = getSelectedRecipe();
        return recipe != null && !recipe.acceptsBlueprint(blueprintSlot.getStack());
    }

    /** Bouton « Assembler ». */
    public void startBuilding() {
        AssemblyRecipe recipe = getSelectedRecipe();
        if (!building && recipe != null && isUnlocked(recipe) && hasRecipe()) {
            building = true;
            progress = 0;
            setChanged();
        }
    }

    public void cancelBuilding() {
        building = false;
        progress = 0;
        setChanged();
    }

    public void toggleRepeat() {
        repeat = !repeat;
        setChanged();
    }

    public double getScaledProgress() {
        AssemblyRecipe recipe = getSelectedRecipe();
        return recipe == null ? 0 : Math.min(1, progress / (double) recipe.getDuration());
    }

    /** Consommation de la recette choisie (FE/t). */
    public long buildEnergyPerTick() {
        AssemblyRecipe recipe = getSelectedRecipe();
        return recipe == null ? 0 : Math.max(1, recipe.getEnergy() / recipe.getDuration());
    }

    /** Produit termine en attente dans la sortie. */
    public boolean hasFinishedProduct() {
        return !outputSlot.isEmpty();
    }

    /** Lot 8b : vraie v2 (reserve et batterie, en-tete, grille, colonne du plan et de la sortie, rangee des boutons). */
    @Override
    public java.util.List<StandardLayout.Part> guiParts() {
        return java.util.List.of(new StandardLayout.Part("energy", new StandardLayout.Rect(7, StandardLayout.TOP - 1, StandardLayout.GAUGE_W + 2,
                    StandardLayout.ENERGY_H + 2)), new StandardLayout.Part("setting", HEADER), new StandardLayout.Part("bar", BAR),
              new StandardLayout.Part("setting", BUTTONS));
    }

    /** API Machine (lot 8b) : valeurs NOMMEES aux indices d'avant (avancement, en cours, repetition, consommation de la recette). */
    @Override
    protected boolean namedValues() {
        return true;
    }

    @Override
    protected void declareValues(UiValues.Builder values) {
        values.add(UiValues.PROGRESS_X1000, () -> machineValue(0)).add(UiValues.WORKING, () -> machineValue(1)).add(REPEAT_ON, () -> machineValue(2))
              .add(UiValues.FE_PER_TICK, () -> machineValue(3));
    }

    @Override
    protected int machineValueCount() {
        return 4;
    }

    @Override
    protected long machineValue(int index) {
        return switch (index) {
            case 0 -> Math.round(getScaledProgress() * 1000);
            case 1 -> building ? 1 : 0;
            case 2 -> repeat ? 1 : 0;
            default -> buildEnergyPerTick();
        };
    }

    // ------------------------------------------------------------------
    // Rendu (client)
    // ------------------------------------------------------------------

    public boolean shouldShowProduct() {
        return clientShowProduct;
    }

    public boolean isFinishedClient() {
        return clientShowProduct && !clientBuilding;
    }

    public float getRenderProgress() {
        return clientStage / 20F;
    }

    /** Produit de la recette choisie (rendu). */
    public ItemStack getRenderedProduct() {
        AssemblyRecipe recipe = getSelectedRecipe();
        return recipe == null ? ItemStack.EMPTY : recipe.getResult();
    }

    // ------------------------------------------------------------------
    // Sauvegarde et synchronisation
    // ------------------------------------------------------------------

    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        tag.putBoolean("showProduct", building || !outputSlot.isEmpty());
        tag.putBoolean("building", building);
        tag.putInt("stage", (int) (getScaledProgress() * 20));
        if (selected != null) {
            tag.putString("recipe", selected.toString());
        }
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        clientShowProduct = tag.getBoolean("showProduct");
        clientBuilding = tag.getBoolean("building");
        clientStage = tag.getInt("stage");
        selected = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
    }

    @Override
    public void load(@NotNull CompoundTag nbt) {
        super.load(nbt);
        progress = nbt.getInt("assemblyProgress");
        building = nbt.getBoolean("assembling");
        repeat = nbt.getBoolean("repeat");
        selected = nbt.contains("recipe") ? ResourceLocation.tryParse(nbt.getString("recipe")) : null;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag nbtTags) {
        super.saveAdditional(nbtTags);
        nbtTags.putInt("assemblyProgress", progress);
        nbtTags.putBoolean("assembling", building);
        nbtTags.putBoolean("repeat", repeat);
        if (selected != null) {
            nbtTags.putString("recipe", selected.toString());
        }
    }
}

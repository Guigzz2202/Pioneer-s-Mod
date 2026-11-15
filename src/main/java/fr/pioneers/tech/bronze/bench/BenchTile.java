package fr.pioneers.tech.bronze.bench;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.tech.bronze.BronzeTile;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.forge.smithing.HandForged;
import fr.pioneers.core.recipes.PiRecipes;
import fr.pioneers.core.registries.PiBronze;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Atelier : 3 cases d'outils, 6 de materiaux, 1 de sortie ; la recette choisie (valeur d'interface 0, index dans la liste triee des
 * recettes de cet atelier) est fabriquee au bouton (1, ou autant que possible). Les outils s'usent d'un point par piece.
 */
public class BenchTile extends BronzeTile {

    /**
     * Lot 8c : l'etabli (hors API Machine, decision 1) a le MEME CADRE v2 que les machines (inventaire dans le cadre, titre et pastille d'age,
     * onglet Infos) : outils et materiaux a gauche, recherche et grille des pieces (5 rangees) au milieu, sortie et boutons a droite, besoins
     * de la piece choisie dessous. Cases au meme ORDRE (outils, materiaux, sortie), places decalees sous la bande du titre.
     */
    public static final int WIDTH = 236, TOOL_X = 8, TOOL_Y = StandardLayout.TOP + 14, MAT_X = 30, MAT_Y = TOOL_Y, OUT_X = WIDTH - 25,
          OUT_Y = TOOL_Y;
    public static final int GRID_X = 72, GRID_Y = TOOL_Y - 2, COLS = 7, ROWS = 5, CELL = 18, SIDE_X = WIDTH - 33, SIDE_W = 30;
    public static final StandardLayout.Rect SEARCH = new StandardLayout.Rect(GRID_X - 2, StandardLayout.TOP - 2, COLS * CELL + 4 - 22, 11),
          GRID = new StandardLayout.Rect(GRID_X - 2, GRID_Y - 2, COLS * CELL + 4, ROWS * CELL + 4),
          PATTERNS_BUTTON = new StandardLayout.Rect(SIDE_X, OUT_Y + 22, SIDE_W, 14),
          CRAFT_BUTTONS = new StandardLayout.Rect(SIDE_X, GRID_Y + ROWS * CELL - 31, SIDE_W, 31),
          NEEDS = new StandardLayout.Rect(8, GRID_Y + ROWS * CELL + 4, WIDTH - 16, 20);
    /**
     * Actions : choisir la recette n (SELECT + n), fabriquer 1 (CRAFT), fabriquer le maximum (CRAFT_ALL) ; 1.12.0 : etabli du tanneur,
     * ouvrir les motifs de banniere (PATTERNS).
     */
    public static final int SELECT = CUSTOM + 100, CRAFT = CUSTOM, CRAFT_ALL = CUSTOM + 1, PATTERNS = CUSTOM + 2;

    private final List<ItemSlot> tools = new ArrayList<>(), materials = new ArrayList<>();
    private final ItemSlot output;
    private int selected = -1;

    public BenchTile(BlockPos pos, BlockState state) {
        super(PiBronze.BENCH_TILE.get(), pos, state);
        for (int i = 0; i < BenchType.TOOLS; i++) {
            tools.add(slot(new ItemSlot(() -> 1, s -> s.isDamageableItem() || s.getMaxStackSize() == 1, (s, a) -> !a.external(), (s, a) -> !a.external(),
                  this::changed, TOOL_X, TOOL_Y + i * 20)));
        }
        for (int i = 0; i < BenchType.MATERIALS; i++) {
            materials.add(slot(new ItemSlot(() -> 64, s -> true, (s, a) -> true, (s, a) -> !a.external(), this::changed, MAT_X + (i % 2) * 18,
                  MAT_Y + (i / 2) * 20)));
        }
        output = slot(new ItemSlot(() -> 64, s -> true, (s, a) -> a == Automation.INTERNAL, (s, a) -> true, this::changed, OUT_X, OUT_Y));
    }

    public BenchType type() {
        return getBlockState().getBlock() instanceof BenchBlock bench ? bench.type : BenchType.TOOLMAKER;
    }

    /** Recettes de cet atelier, dans l'ordre affiche (identique client et serveur). */
    public static List<BenchRecipe> recipes(Level level, BenchType type) {
        List<BenchRecipe> out = new ArrayList<>();
        for (BenchRecipe recipe : PiRecipes.sorted(level, PiBronze.BENCH.get())) {
            if (recipe.bench() == type) {
                out.add(recipe);
            }
        }
        return out;
    }

    public List<ItemStack> toolStacks() {
        return tools.stream().map(ItemSlot::getStack).toList();
    }

    public List<ItemStack> materialStacks() {
        return materials.stream().map(ItemSlot::getStack).toList();
    }

    public ItemSlot outputSlot() {
        return output;
    }

    /** Combien de pieces sont faisables tout de suite (outils presents et assez de materiaux). */
    public int possible(BenchRecipe recipe) {
        if (recipe.matchTools(toolStacks()) == null) {
            return 0;
        }
        return recipe.times(materialStacks());
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        if (level == null) {
            return;
        }
        List<BenchRecipe> list = recipes(level, type());
        if (action >= SELECT) {
            int index = action - SELECT;
            selected = index >= 0 && index < list.size() ? index : -1;
            setChanged();
        } else if ((action == CRAFT || action == CRAFT_ALL) && selected >= 0 && selected < list.size()) {
            craft((ServerLevel) level, player, list.get(selected), action == CRAFT_ALL ? 64 : 1);
        } else if (action == PATTERNS && type() == BenchType.TANNER) {
            TannerLoomMenu.open(player, level, worldPosition, getBlockState().getBlock());
        }
    }

    private void craft(ServerLevel level, ServerPlayer player, BenchRecipe recipe, int wanted) {
        int made = 0;
        ItemStack result = recipe.result();
        while (made < wanted) {
            int[] used = recipe.matchTools(toolStacks());
            if (used == null || recipe.times(materialStacks()) < 1) {
                break;
            }
            List<ItemStack> live = new ArrayList<>();
            for (ItemSlot slot : materials) {
                live.add(slot.getStack().copy());
            }
            List<ItemStack> rest = recipe.consume(live);
            //Forge primaire : une piece « forgee main » consommee donne un outil forge main (+10 % de durabilite)
            List<ItemStack> before = new ArrayList<>();
            for (ItemSlot slot : materials) {
                before.add(slot.getStack());
            }
            ItemStack part = HandForged.consumed(before, live) ? HandForged.mark(result.copy()) : result;
            ItemStack out = output.getStack();
            if (!out.isEmpty() && (!ItemStack.isSameItemSameTags(out, part) || out.getCount() + part.getCount() > out.getMaxStackSize())) {
                break;
            }
            //1.0.5-beta (lot 21) : restes de fabrication (seau vide d'un seau de fluide) : a sa place, sinon au joueur, sinon a terre
            for (ItemStack r : BenchRecipe.settle(live, rest)) {
                if (!player.getInventory().add(r) && !r.isEmpty()) {
                    player.drop(r, false);
                }
            }
            for (int i = 0; i < materials.size(); i++) {
                materials.get(i).setStack(live.get(i));
            }
            //1.9 (MAJ RP) : usure selon le joueur (Ingenierie : moins ; Eleveur-Pecheur a l'etabli de montage : plus)
            int wear = fr.pioneers.core.profile.SkillHooks.benchWear(player, type() == BenchType.ASSEMBLY || type() == BenchType.ADVANCED_COMPONENTS
                  || type() == BenchType.LOGISTICS, level.random);
            for (int slot : used) {
                ItemStack tool = tools.get(slot).getStack().copy();
                if (tool.isDamageableItem() && wear > 0) {
                    tool.setDamageValue(tool.getDamageValue() + wear);
                    if (tool.getDamageValue() >= tool.getMaxDamage()) {
                        tool = ItemStack.EMPTY;
                        level.playSound(null, worldPosition, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.8F, 1F);
                    }
                }
                tools.get(slot).setStack(tool);
            }
            output.insert(part.copy(), Action.EXECUTE, Automation.INTERNAL);
            made++;
        }
        if (made > 0) {
            fr.pioneers.core.profile.SkillHooks.benchCrafted(player, result, made);
            level.playSound(null, worldPosition, sound(), SoundSource.BLOCKS, 0.7F, 0.9F + level.random.nextFloat() * 0.2F);
            BronzeEvents.award(level, worldPosition, "bench_" + type().key());
            setChanged();
        }
    }

    private SoundEvent sound() {
        return switch (type()) {
            case METALWORKER, COPPERSMITH -> SoundEvents.ANVIL_USE;
            case CARPENTER -> SoundEvents.AXE_STRIP;
            case POTTER -> SoundEvents.MUD_PLACE;
            case MASON -> SoundEvents.GRINDSTONE_USE;
            case TOOLMAKER -> SoundEvents.SMITHING_TABLE_USE;
            case ASSEMBLY -> SoundEvents.IRON_TRAPDOOR_CLOSE;
            case REDSTONER -> SoundEvents.COMPARATOR_CLICK;
            case TRANSPORTER -> SoundEvents.CHAIN_PLACE;
            case LUTHIER -> SoundEvents.NOTE_BLOCK_GUITAR.value();
            case AGRICULTURAL -> SoundEvents.CROP_PLANTED;
            case MOLDER -> SoundEvents.SAND_PLACE;
            case COMPONENTS -> SoundEvents.ANVIL_USE;
            case ADVANCED_COMPONENTS -> SoundEvents.IRON_TRAPDOOR_CLOSE;
            case LOGISTICS -> SoundEvents.CHAIN_PLACE;
            case TANNER -> SoundEvents.UI_LOOM_TAKE_RESULT;
        };
    }

    // ------------------------------------------------------------------
    // Interface et sauvegarde
    // ------------------------------------------------------------------

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public int guiWidth() {
        return WIDTH;
    }

    /** Lot 8c : inventaire dans le cadre v2. */
    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        List<StandardLayout.Part> parts = new ArrayList<>(List.of(new StandardLayout.Part("setting",
              new StandardLayout.Rect(SEARCH.x(), SEARCH.y(), GRID.w(), SEARCH.h())), new StandardLayout.Part("card", GRID),
              new StandardLayout.Part("setting", CRAFT_BUTTONS), new StandardLayout.Part("card", NEEDS)));
        if (type() == BenchType.TANNER) {
            parts.add(new StandardLayout.Part("setting", PATTERNS_BUTTON));
        }
        return parts;
    }

    @Override
    public String guiTier() {
        return "bench";
    }

    /** API Machine (lot 8b) : valeur nommee de l'etabli (hors API : recette choisie, indice d'avant 0). */
    public static final UiValues.Key SELECTED = UiValues.Key.of("selected");

    @Override
    public UiValues uiValues() {
        return namedIndices(SELECTED);
    }

    @Override
    public int guiValueCount() {
        return 1;
    }

    @Override
    public long guiValue(int index) {
        return selected;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("selected", selected);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        selected = tag.contains("selected") ? tag.getInt("selected") : -1;
    }
}

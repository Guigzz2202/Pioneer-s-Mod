package fr.pioneers.farm.kitchen;

import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.device.PiFarmDevices;
import fr.pioneers.farm.ui.FarmLayout;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Extracteur de miel MANUEL (centrifugeuse a manivelle, poste {@link StationBlock.Kind#HONEY_EXTRACTOR}). 1.0.5-beta (lot 32, retour 41 de
 * Guillaume : « Donne une interface a l'extracteur de miel. On y met les cadres de ruches. Et il produit les 3 ingredients (cire, rayon et
 * miel liquide que l'on recupere via des bouteilles) ») : interface au clic droit. 1.0.5-beta (lot 41, retour 60) : ALIGNE sur la norme des
 * interfaces de la ferme (interface standard de l'API Machine, vue {@code farm} : faces, Infos ; son menu propre du lot 32 est retire) ;
 * plus de fiole, de seau ni de cadre en main : la case des cadres et la paire « Remplir ».
 * <p>
 * Cases (ordre de sauvegarde d'avant garde : 0 cadres, 1 cire, 2 cadre vide ; puis celles du lot 32) : {@link #FRAMES} cadres pleins (16 au
 * plus), {@link #WAX} cire, {@link #EMPTY_FRAME} cadres vides, {@link #COMB} rayons de miel, {@link #CONTAINER} contenants vides (fioles,
 * seaux : paire « Remplir »), {@link #FILLED} bouteilles et seaux de miel ; cuve de miel {@link #TANK} mB. Chaque produit va dans SA case
 * ({@link #slotFor}), sinon dans une case de sortie libre, sinon au joueur. Proportions : {@link HoneyFill}.
 * <p>
 * Geste garde : Maj + clic droit main vide = un tour de manivelle ({@link #crank}) ; le bouton « Tourner la manivelle » de l'interface fait le
 * meme tour. Automatisation : entonnoirs par le haut et les cotes vers les cadres et les contenants ; par le bas, cire, rayons, cadres vides
 * et contenants pleins ; conduites : le miel de la cuve ; faces reglables dans l'interface.
 */
public class HoneyExtractorTile extends StationTile {

    public static final int FRAMES = 0, WAX = 1, EMPTY_FRAME = 2, COMB = 3, CONTAINER = 4, FILLED = 5, SLOTS = 6, TANK = 4000, MAX_FRAMES = 16;
    /** Etats affiches par l'interface. */
    public static final int IDLE = 0, READY = 1, TURNING = 2, TANK_FULL = 3, OUTPUTS_FULL = 4;
    /** Cles des etats ({@code gui.pioneers.honey_extractor.status.<cle>}, lot 32). */
    private static final String[] STATUS_KEYS = {"idle", "ready", "turning", "tank_full", "outputs_full"};

    public HoneyExtractorTile(BlockPos pos, BlockState state) {
        super(pos, state, SLOTS, 1, 0, TANK);
    }

    @Override
    protected FarmLayout layout() {
        String k = "gui.pioneers.honey_extractor.slot.";
        return FarmLayout.builder().inputs(new int[]{FRAMES}, 1).arrow(false).outputs(new int[]{WAX, COMB, EMPTY_FRAME}, 1, k + "wax", k + "comb",
              k + "empty_frame").tankOut(CONTAINER, FILLED).button("gui.pioneers.honey_extractor.crank").build();
    }

    @Override
    protected ItemStack ghost(FarmLayout.SlotSpec spec) {
        return switch (spec.index()) {
            case FRAMES -> new ItemStack(PiFarmDevices.HONEY_FRAME.get());
            case WAX -> new ItemStack(PiFarmDevices.BEESWAX.get());
            case COMB -> new ItemStack(Items.HONEYCOMB);
            case EMPTY_FRAME -> new ItemStack(PiFarmDevices.HIVE_FRAME.get());
            default -> super.ghost(spec);
        };
    }

    // ------------------------------------------------------------------
    // Recette et etat
    // ------------------------------------------------------------------

    @Nullable
    KitchenRecipe recipe() {
        ItemStack s = items.get(FRAMES);
        if (s.isEmpty()) {
            return null;
        }
        for (KitchenRecipe r : recipes(Station.EXTRACTING)) {
            if (r.ingredients().size() == 1 && r.ingredients().get(0).ingredient().test(s) && s.getCount() >= r.ingredients().get(0).count()) {
                return r;
            }
        }
        return null;
    }

    /** Un cadre (ingredient d'une recette de l'extracteur). */
    public boolean acceptsFrame(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (KitchenRecipe r : recipes(Station.EXTRACTING)) {
            if (r.accepts(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Case de sortie d'un produit : cire, cadre vide, rayon (les autres : -1, la premiere case de sortie qui le prend). */
    public static int slotFor(ItemStack stack) {
        if (stack.is(PiFarmDevices.BEESWAX.get())) {
            return WAX;
        }
        if (stack.is(PiFarmDevices.HIVE_FRAME.get())) {
            return EMPTY_FRAME;
        }
        if (stack.is(Items.HONEYCOMB)) {
            return COMB;
        }
        return -1;
    }

    /** Place pour ce produit (simulation sur des copies quand {@code real} est faux) ; renvoie le reste. */
    private ItemStack place(ItemStack stack, boolean real) {
        ItemStack rest = stack.copy();
        int own = slotFor(stack);
        int[] order = own >= 0 ? new int[]{own} : new int[]{WAX, EMPTY_FRAME, COMB};
        for (int i : order) {
            ItemStack o = items.get(i);
            if (o.isEmpty()) {
                if (real) {
                    items.set(i, rest.copy());
                }
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameTags(o, rest)) {
                int n = Math.min(rest.getCount(), o.getMaxStackSize() - o.getCount());
                if (real) {
                    o.grow(n);
                }
                rest.shrink(n);
                if (rest.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        return rest;
    }

    /** Les sorties peuvent-elles recevoir tous les produits de la recette (chance comprise : le cas le plus plein) ? */
    private boolean outputsRoom(KitchenRecipe r) {
        for (KitchenRecipe.Result res : r.results()) {
            int own = slotFor(res.stack());
            ItemStack o = own >= 0 ? items.get(own) : ItemStack.EMPTY;
            if (own >= 0 && !o.isEmpty() && (!ItemStack.isSameItemSameTags(o, res.stack()) || o.getCount() + res.stack().getCount() > o.getMaxStackSize())) {
                return false;
            }
        }
        return true;
    }

    /** Etat (ecran de l'interface, GameTests). */
    public int status() {
        KitchenRecipe r = recipe();
        if (r == null) {
            return IDLE;
        }
        if (out != null && out.fill(r.fluidResult(), IFluidHandler.FluidAction.SIMULATE) < r.fluidResult().getAmount()) {
            return TANK_FULL;
        }
        if (!outputsRoom(r)) {
            return OUTPUTS_FULL;
        }
        return progress > 0 ? TURNING : READY;
    }

    /** Tours qu'il faut pour le cadre engage (celui de la recette, sinon celui de la recette d'origine). */
    public int strokesNeeded() {
        KitchenRecipe r = recipe();
        return Math.max(1, r == null ? HoneyFill.STROKES : r.strokes());
    }

    // ------------------------------------------------------------------
    // Geste : un tour de manivelle (bouton de l'interface, Maj + clic droit main vide)
    // ------------------------------------------------------------------

    @Override
    public void manual(Player player, int button) {
        crank(player);
    }

    @Override
    public boolean buttonActive(int i) {
        int s = status();
        return s == READY || s == TURNING;
    }

    @Override
    public List<Component> buttonTip(int i) {
        return List.of(Component.translatable("gui.pioneers.honey_extractor.status." + STATUS_KEYS[status()]),
              Component.translatable("gui.pioneers.farm.tip.strokes", progress, strokesNeeded()));
    }

    @Override
    public float arrowProgress() {
        return Math.min(1F, progress / (float) strokesNeeded());
    }

    /** Un tour de manivelle (geste ou bouton de l'interface) : vrai si la cage a tourne. */
    public boolean crank(Player player) {
        if (level == null || level.isClientSide) {
            return false;
        }
        KitchenRecipe r = recipe();
        if (r == null) {
            StationTiles.say(player, "honey_extractor.empty");
            return false;
        }
        if (out != null && out.fill(r.fluidResult(), IFluidHandler.FluidAction.SIMULATE) < r.fluidResult().getAmount()) {
            StationTiles.say(player, "tank_full");
            return false;
        }
        if (!outputsRoom(r)) {
            StationTiles.say(player, "outputs_full");
            return false;
        }
        need = Math.max(1, r.strokes());
        progress += StationTiles.stroke(player);
        strokes++;
        StationTiles.sound(this, SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.6F, 1.1F);
        StationTiles.sound(this, SoundEvents.CHAIN_STEP, 0.4F, 1.4F);
        if (progress >= need) {
            StationTiles.dish(player);
            items.get(FRAMES).shrink(r.ingredients().get(0).count());
            for (ItemStack res : r.roll(level.random, null)) {
                ItemStack rest = place(res, true);
                if (!rest.isEmpty()) {
                    give(player, rest);
                }
            }
            if (out != null) {
                out.fill(r.fluidResult().copy(), IFluidHandler.FluidAction.EXECUTE);
            }
            progress = 0;
            StationTiles.sound(this, SoundEvents.HONEY_DRINK, 0.6F, 1.0F);
        }
        contentsChanged();
        return true;
    }

    /** Les contenants poses dans « Remplir » se remplissent au changement du contenu (paire de la base, jamais par tick). */
    @Override
    protected void contentsChanged() {
        if (level != null && !level.isClientSide && items.get(FRAMES).isEmpty() && progress != 0) {
            //Plus de cadre a extraire : le compte de tours repart a zero
            progress = 0;
        }
        super.contentsChanged();
    }

    // ------------------------------------------------------------------
    // Automatisation et cases
    // ------------------------------------------------------------------

    @Override
    protected boolean acceptsInput(int slot, ItemStack stack) {
        return slot == FRAMES && acceptsFrame(stack);
    }

    @Override
    protected boolean acceptsAutomation(int slot, ItemStack stack) {
        return slot == FRAMES ? acceptsFrame(stack) : super.acceptsAutomation(slot, stack);
    }

    @Override
    protected int automationLimit(int slot) {
        return MAX_FRAMES;
    }

    // ------------------------------------------------------------------
    // Ecran d'etat
    // ------------------------------------------------------------------

    @Override
    protected void describe(FarmCard card, boolean sneaking) {
        int s = status();
        int colour = s == READY || s == TURNING ? FarmCard.OK : s == IDLE ? FarmCard.DIM : FarmCard.WARN;
        card.add(FarmCard.Line.gauge(FarmCard.label("crank"), arrowProgress(), Component.translatable("gui.pioneers.honey_extractor.status." + STATUS_KEYS[s]), colour));
        contents(card);
    }
}

package fr.pioneers.integration.jei;

import fr.pioneers.client.warehouse.CounterClient;
import fr.pioneers.network.PacketWarehouse;
import fr.pioneers.network.PiNetwork;
import fr.pioneers.warehouse.CounterTile;
import fr.pioneers.warehouse.CounterUnit;
import fr.pioneers.warehouse.RecipeAsk;
import fr.pioneers.warehouse.StockKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 17, Guillaume : « Maj + clic sur une recette JEI = demande des ingredients au reseau ») : POIGNEE DE TRANSFERT de JEI sur
 * le COMPTOIR D'ENTREPOT (appelee par la poignee universelle des machines, {@link RecipeLockJei.Transfer} : JEI n'en garde qu'une par menu).
 * Toute recette dont les entrees sont des objets (fabrication vanilla, etablis, machines du mod...) : chaque case d'entree = une ligne
 * (ses alternatives : tag, variantes ; son nombre) ; le plan ({@link RecipeAsk}) est fait sur le stock CONNU du client (stock - file -
 * panier) pour montrer ce qui manque EN ROUGE dans JEI avec le nombre manquant, puis refait par le serveur sur son stock.
 * <ul>
 *     <li>bouton « + » : la recette une fois au panier ; Maj + « + » : autant de fois que le stock le permet, 64 au plus (convention de
 *     JEI) ; Ctrl : « Commander » aussitot (vers la destination choisie au panier) ;</li>
 *     <li>rien de la recette dans l'entrepot, comptoir pas relie ou liste pas encore arrivee : bouton grise, avec la raison ;</li>
 *     <li>fluides et gaz : pas demandes (dit dans l'infobulle).</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
final class WarehouseJei {

    private WarehouseJei() {
    }

    /** Lignes de la recette (cases d'entree a objets), et pour chaque ligne les cases de JEI qui la composent. */
    private record Lines(List<RecipeAsk.Line<StockKey>> lines, List<List<IRecipeSlotView>> slots, Map<StockKey, ItemStack> protos, boolean fluids) {}

    private static Lines lines(IRecipeSlotsView view) {
        Map<List<StockKey>, Integer> index = new LinkedHashMap<>();
        List<RecipeAsk.Line<StockKey>> lines = new ArrayList<>();
        List<List<IRecipeSlotView>> slots = new ArrayList<>();
        Map<StockKey, ItemStack> protos = new HashMap<>();
        boolean fluids = false;
        for (IRecipeSlotView slot : view.getSlotViews(RecipeIngredientRole.INPUT)) {
            List<ItemStack> stacks = slot.getItemStacks().filter(s -> !s.isEmpty()).toList();
            if (stacks.isEmpty()) {
                fluids |= !slot.isEmpty();
                continue;
            }
            List<StockKey> keys = new ArrayList<>();
            int count = 1;
            for (ItemStack s : stacks) {
                StockKey k = StockKey.of(s);
                if (!keys.contains(k) && keys.size() < 64) {
                    keys.add(k);
                    protos.putIfAbsent(k, s.copyWithCount(1));
                }
                count = Math.max(count, s.getCount());
            }
            count = Math.min(RecipeAsk.MAX_PER_CRAFT, count);
            Integer at = index.get(keys);
            if (at != null) {
                RecipeAsk.Line<StockKey> old = lines.get(at);
                lines.set(at, new RecipeAsk.Line<>(old.alternatives(), Math.min(RecipeAsk.MAX_PER_CRAFT, old.perCraft() + count)));
                slots.get(at).add(slot);
            } else if (lines.size() < RecipeAsk.MAX_LINES) {
                index.put(keys, lines.size());
                lines.add(new RecipeAsk.Line<>(keys, count));
                List<IRecipeSlotView> own = new ArrayList<>();
                own.add(slot);
                slots.add(own);
            }
        }
        return new Lines(lines, slots, protos, fluids);
    }

    /** Ce que le client sait encore pouvoir mettre au panier de ce type (stock - file - panier local). */
    private static long free(BlockPos pos, Map<StockKey, ItemStack> protos, StockKey k) {
        ItemStack p = protos.get(k);
        return p == null ? 0 : CounterClient.free(pos, p);
    }

    /**
     * Transfert vers le comptoir {@code tile} (menu ouvert) : null = bouton actif sans remarque ; erreur COSMETIQUE (bouton actif) avec ce qui
     * manque ; erreur JOUEUR (bouton grise) si rien ne peut partir.
     */
    @Nullable
    static IRecipeTransferError transfer(CounterTile tile, long diag, IRecipeSlotsView view, boolean maxTransfer, boolean doTransfer,
          IRecipeTransferHandlerHelper helper) {
        BlockPos pos = tile.getBlockPos();
        Lines l = lines(view);
        if (l.lines().isEmpty()) {
            return helper.createUserErrorWithTooltip(Component.translatable("gui.pioneers.warehouse.jei.no_items"));
        }
        CounterClient.Stock stock = CounterClient.stock(pos);
        if (!stock.ready) {
            return helper.createUserErrorWithTooltip(Component.translatable("gui.pioneers.warehouse.loading"));
        }
        if (diag != CounterUnit.D_OK && diag != CounterUnit.D_STILL) {
            return helper.createUserErrorWithTooltip(Component.translatable("gui.pioneers.warehouse.jei.not_linked",
                  Component.translatable("gui.pioneers.warehouse.diag." + CounterUnit.DIAG_KEYS[(int) Math.max(0, Math.min(CounterUnit.DIAG_KEYS.length - 1, diag))])));
        }
        RecipeAsk.Plan<StockKey> once = RecipeAsk.plan(l.lines(), 1, k -> free(pos, l.protos(), k));
        int max = RecipeAsk.maxTimes(l.lines(), k -> free(pos, l.protos(), k), RecipeAsk.MAX_TIMES);
        if (once.taken() == 0) {
            List<IRecipeSlotView> all = new ArrayList<>();
            l.slots().forEach(all::addAll);
            return helper.createUserErrorForMissingSlots(Component.translatable("gui.pioneers.warehouse.jei.none"), all);
        }
        if (doTransfer) {
            int times = maxTransfer ? Math.max(1, max) : 1;
            boolean order = Screen.hasControlDown();
            List<List<ItemStack>> sent = new ArrayList<>();
            List<Integer> per = new ArrayList<>();
            for (RecipeAsk.Line<StockKey> line : l.lines()) {
                //Au serveur : les alternatives que l'entrepot a (16 au plus), sinon la premiere (pour dire ce qui manque)
                List<ItemStack> alts = new ArrayList<>();
                for (StockKey k : line.alternatives()) {
                    if (alts.size() < RecipeAsk.MAX_ALTERNATIVES && stock.count(k) > 0) {
                        alts.add(l.protos().get(k));
                    }
                }
                if (alts.isEmpty()) {
                    alts.add(l.protos().get(line.alternatives().get(0)));
                }
                sent.add(alts);
                per.add(line.perCraft());
            }
            //1.0.5-beta (lot 22) : le produit de la recette (premiere sortie de JEI) part avec : la recette se voit au panier, son nombre se regle
            ItemStack result = view.getSlotViews(RecipeIngredientRole.OUTPUT).stream().flatMap(IRecipeSlotView::getItemStacks)
                  .filter(st -> !st.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
            PiNetwork.sendToServer(new PacketWarehouse.Recipe(pos, sent, per, times, order ? PacketWarehouse.Recipe.ORDER : 0, result,
                  result.isEmpty() ? 1 : result.getCount()));
            return null;
        }
        return new Missing(once, l, max);
    }

    /**
     * Erreur COSMETIQUE (le bouton reste actif) : infobulle (ce qui part, ce qui manque, Maj, Ctrl), cases manquantes en rouge avec le nombre
     * manquant (comme les vraies poignees de JEI).
     */
    private static final class Missing implements IRecipeTransferError {

        private final RecipeAsk.Plan<StockKey> plan;
        private final Lines lines;
        private final int max;

        Missing(RecipeAsk.Plan<StockKey> plan, Lines lines, int max) {
            this.plan = plan;
            this.lines = lines;
            this.max = max;
        }

        @NotNull
        @Override
        public Type getType() {
            return Type.COSMETIC;
        }

        @Override
        public int getButtonHighlightColor() {
            //Tout est la : pas de surlignage ; il manque quelque chose : orange (convention de JEI)
            return plan.complete() ? 0 : 0x80FFA500;
        }

        @Override
        public int getMissingCountHint() {
            return plan.missingLines();
        }

        @NotNull
        @Override
        public List<Component> getTooltip() {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.pioneers.warehouse.jei.title").withStyle(ChatFormatting.GOLD));
            tip.add(Component.translatable("gui.pioneers.warehouse.jei.takes", plan.taken(), plan.byKey().size()).withStyle(ChatFormatting.GRAY));
            for (int i = 0; i < plan.missing().length; i++) {
                long m = plan.missing()[i];
                if (m > 0) {
                    ItemStack p = lines.protos().get(lines.lines().get(i).alternatives().get(0));
                    tip.add(Component.translatable("gui.pioneers.warehouse.jei.missing", m, p == null ? Component.empty() : p.getHoverName())
                          .withStyle(ChatFormatting.RED));
                }
            }
            if (lines.fluids()) {
                tip.add(Component.translatable("gui.pioneers.warehouse.jei.fluids").withStyle(ChatFormatting.DARK_GRAY));
            }
            tip.add(Component.translatable("gui.pioneers.warehouse.jei.shift", Math.max(1, max)).withStyle(ChatFormatting.DARK_GRAY));
            tip.add(Component.translatable("gui.pioneers.warehouse.jei.ctrl").withStyle(ChatFormatting.DARK_GRAY));
            return tip;
        }

        @Override
        public void showError(@NotNull GuiGraphics g, int mouseX, int mouseY, @NotNull IRecipeSlotsView view, int recipeX, int recipeY) {
            g.pose().pushPose();
            g.pose().translate(recipeX, recipeY, 0);
            var font = Minecraft.getInstance().font;
            for (int i = 0; i < plan.missing().length; i++) {
                long m = plan.missing()[i];
                if (m <= 0) {
                    continue;
                }
                boolean first = true;
                for (IRecipeSlotView slot : lines.slots().get(i)) {
                    slot.drawHighlight(g, 0x66FF0000);
                    if (first && slot instanceof IRecipeSlotDrawable d) {
                        //Le nombre manquant, en bas a gauche de la premiere case de la ligne
                        Rect2i r = d.getRect();
                        g.pose().pushPose();
                        g.pose().translate(r.getX() + 1, r.getY() + r.getHeight() - 7, 300);
                        g.pose().scale(0.75F, 0.75F, 1);
                        g.drawString(font, "-" + m, 0, 0, 0xFFFF5050, true);
                        g.pose().popPose();
                        first = false;
                    }
                }
            }
            g.pose().popPose();
        }
    }
}

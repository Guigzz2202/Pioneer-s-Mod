package fr.pioneers.farm.kitchen;

import fr.pioneers.Pioneers;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.PiFarm;
import fr.pioneers.farm.PiKitchen;
import fr.pioneers.farm.animal.AnimalSpecies;
import fr.pioneers.farm.animal.FarmSites;
import fr.pioneers.farm.food.CannedFoodItem;
import fr.pioneers.farm.food.FarmFoodItem;
import fr.pioneers.farm.ui.FarmLayout;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Les postes de cuisine et de transformation (une classe par poste ; base commune {@link StationTile}). 1.0.5-beta (lot 41, retour 60) :
 * chaque poste declare la disposition de son interface ({@link #layout}) ; les gestes en monde (objet en main, seau, bouteille, couteau,
 * Maj pour reprendre) sont retires : il ne reste que le geste des postes manuels ({@link StationTile#manual}, bouton de l'interface ou
 * Maj + clic droit main vide). Cases ajoutees EN FIN de liste (indices d'avant gardes : mondes existants relus tels quels).
 */
public final class StationTiles {

    public static final TagKey<Item> KNIVES = ItemTags.create(Pioneers.rl("tools/knives"));

    private StationTiles() {
    }

    /** 1.9 (MAJ RP) : un coup de couteau, de fouet ou de presse ; parfois deux avec la Cuisine (« Preparation a la main »). */
    static int stroke(Player player) {
        return player instanceof net.minecraft.server.level.ServerPlayer sp && fr.pioneers.core.profile.SkillHooks.quickHands(sp, "cooking.hand_speed")
               ? 2 : 1;
    }

    /** XP de Cuisine : une preparation menee a la main jusqu'au bout. */
    static void dish(Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            fr.pioneers.core.profile.PioneerApi.gain(sp, "cooking.dish", 1, null);
        }
    }

    static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("farm.pioneers.station." + key, args).withStyle(ChatFormatting.GOLD), true);
    }

    static void sound(StationTile t, SoundEvent sound, float volume, float pitch) {
        if (t.getLevel() != null) {
            t.getLevel().playSound(null, t.getBlockPos(), sound, SoundSource.BLOCKS, volume, pitch);
        }
    }

    static void itemBurst(StationTile t, ItemStack stack, double y, int n) {
        if (t.getLevel() instanceof ServerLevel server && !stack.isEmpty()) {
            BlockPos p = t.getBlockPos();
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack), p.getX() + 0.5, p.getY() + y, p.getZ() + 0.5, n, 0.15, 0.05, 0.15, 0.06);
        }
    }

    /** Les cases [from, to) contiennent-elles de quoi faire la recette (au moins les ingredients) ? */
    static boolean hasAtLeast(KitchenRecipe r, List<ItemStack> items, int from, int to) {
        for (KitchenRecipe.Counted c : r.ingredients()) {
            int n = 0;
            for (int i = from; i < to; i++) {
                if (c.ingredient().test(items.get(i))) {
                    n += items.get(i).getCount();
                }
            }
            if (n < c.count()) {
                return false;
            }
        }
        return true;
    }

    /** Retire les ingredients d'une recette des cases [from, to) ; rend ce qui a ete pris. */
    static List<ItemStack> take(KitchenRecipe r, List<ItemStack> items, int from, int to) {
        List<ItemStack> taken = new ArrayList<>();
        for (KitchenRecipe.Counted c : r.ingredients()) {
            int left = c.count();
            for (int i = from; i < to && left > 0; i++) {
                ItemStack s = items.get(i);
                if (!s.isEmpty() && c.ingredient().test(s)) {
                    int n = Math.min(left, s.getCount());
                    taken.add(s.copyWithCount(n));
                    s.shrink(n);
                    left -= n;
                }
            }
        }
        return taken;
    }

    public static int[] range(int from, int to) {
        int[] r = new int[to - from];
        for (int i = from; i < to; i++) {
            r[i - from] = i;
        }
        return r;
    }

    // ================================================================== planche a decouper
    /**
     * Lot 41 : case 0 l'ingredient (16 au plus), cases 1 et 2 les produits (avant : sur la planche meme ou au sol), case 3 le couteau (il
     * s'use a chaque coup) ; selecteur = la decoupe choisie (avant : Maj prenait l'autre decoupe d'un meme objet) ; bouton « Couper ».
     */
    public static class CuttingBoard extends StationTile {

        public static final int IN = 0, OUT = 1, OUT2 = 2, KNIFE = 3;

        public CuttingBoard(BlockPos pos, BlockState state) {
            super(pos, state, 4, 1, 0, 0);
        }

        @Override
        protected FarmLayout layout() {
            return FarmLayout.builder().tool(KNIFE).inputs(new int[]{IN}, 1).arrow(true).outputs(new int[]{OUT, OUT2}, 1)
                  .button("gui.pioneers.farm.button.cut").build();
        }

        @Nullable
        @Override
        protected Station selectorStation() {
            return Station.CUTTING;
        }

        @Override
        protected boolean acceptsAutomation(int slot, ItemStack stack) {
            return slot == IN ? countSingle(Station.CUTTING, stack.copyWithCount(1)) > 0 : super.acceptsAutomation(slot, stack);
        }

        @Override
        protected int automationLimit(int slot) {
            return slot == IN ? 16 : super.automationLimit(slot);
        }

        @Override
        protected void slotTaken() {
            if (items.get(IN).isEmpty()) {
                progress = 0;
            }
        }

        /** La decoupe engagee (verrouillee, sinon la premiere par identifiant). */
        @Nullable
        public KitchenRecipe recipe() {
            return findSingle(Station.CUTTING, items.get(IN));
        }

        private boolean outputsFit(KitchenRecipe r) {
            //Chaque produit possible (chance comprise) dans une des deux sorties, deux produits differents dans deux cases
            List<ItemStack> outs = new ArrayList<>();
            for (KitchenRecipe.Result res : r.results()) {
                outs.add(res.stack());
            }
            ItemStack a = items.get(OUT).copy(), b = items.get(OUT2).copy();
            for (ItemStack s : outs) {
                if (fits(a, s)) {
                    a = merged(a, s);
                } else if (fits(b, s)) {
                    b = merged(b, s);
                } else {
                    return false;
                }
            }
            return true;
        }

        private static boolean fits(ItemStack o, ItemStack s) {
            return o.isEmpty() || ItemStack.isSameItemSameTags(o, s) && o.getCount() + s.getCount() <= o.getMaxStackSize();
        }

        private static ItemStack merged(ItemStack o, ItemStack s) {
            return o.isEmpty() ? s.copy() : o.copyWithCount(o.getCount() + s.getCount());
        }

        @Override
        public boolean buttonActive(int i) {
            KitchenRecipe r = recipe();
            return r != null && items.get(KNIFE).is(KNIVES) && outputsFit(r);
        }

        @Override
        public List<Component> buttonTip(int i) {
            if (items.get(IN).isEmpty()) {
                return List.of(Component.translatable("gui.pioneers.farm.tip.need_input"));
            }
            if (!items.get(KNIFE).is(KNIVES)) {
                return List.of(Component.translatable("gui.pioneers.farm.tip.need_knife"));
            }
            KitchenRecipe r = recipe();
            if (r == null) {
                return List.of(Component.translatable("farm.pioneers.station.nothing_to_cut"));
            }
            return List.of(outputsFit(r) ? Component.translatable("gui.pioneers.farm.tip.cut", progress, Math.max(1, r.strokes()))
                                         : Component.translatable("farm.pioneers.station.take_first"));
        }

        @Override
        public void manual(Player player, int button) {
            ItemStack on = items.get(IN), knife = items.get(KNIFE);
            if (level == null || on.isEmpty()) {
                return;
            }
            if (!knife.is(KNIVES)) {
                say(player, "need_knife");
                return;
            }
            KitchenRecipe r = recipe();
            if (r == null) {
                say(player, "nothing_to_cut");
                return;
            }
            if (!outputsFit(r)) {
                say(player, "take_first");
                return;
            }
            need = Math.max(1, r.strokes());
            progress += stroke(player);
            strokes++;
            sound(this, SoundEvents.WOOD_HIT, 0.7F, 1.4F + level.random.nextFloat() * 0.3F);
            itemBurst(this, on, 0.15, 4);
            if (progress >= need) {
                dish(player);
                List<ItemStack> res = r.roll(level.random, on);
                on.shrink(r.ingredients().get(0).count());
                progress = 0;
                for (ItemStack s : res) {
                    if (!put(OUT, s) && !put(OUT2, s)) {
                        Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.3, worldPosition.getZ() + 0.5, s);
                    }
                }
                knife.hurtAndBreak(1, player, p -> {
                });
                sound(this, SoundEvents.SHEEP_SHEAR, 0.5F, 1.6F);
            }
            contentsChanged();
        }

        @Override
        protected void describe(FarmCard card, boolean sneaking) {
            super.describe(card, sneaking);
            if (!items.get(IN).isEmpty()) {
                KitchenRecipe r = recipe();
                if (r == null || r.results().isEmpty()) {
                    card.add(FarmCard.Line.pair(FarmCard.label("cut"), FarmCard.word("nothing"), FarmCard.DIM));
                } else {
                    card.add(FarmCard.Line.pair(FarmCard.label("cut"), r.results().get(0).stack().getHoverName(), FarmCard.INFO));
                }
            }
            if (!items.get(KNIFE).is(KNIVES)) {
                card.add(FarmCard.Line.text(Component.translatable("gui.pioneers.farm.tip.need_knife"), FarmCard.WARN));
            }
        }
    }

    // ================================================================== fourneau (chauffe le bloc du dessus)
    public static class Stove extends StationTile {

        protected int burn, burnMax;

        public Stove(BlockPos pos, BlockState state) {
            super(pos, state, 1, 1, 0, 0);
        }

        protected Stove(BlockPos pos, BlockState state, int slots, int inputs) {
            super(pos, state, slots, inputs, 0, 0);
        }

        @Override
        protected FarmLayout layout() {
            return FarmLayout.builder().fire(0).build();
        }

        public boolean lit() {
            return burn > 0;
        }

        /** Combustible : case 0. */
        protected void burnTick() {
            boolean was = burn > 0;
            if (burn > 0) {
                burn--;
            }
            if (burn <= 0) {
                ItemStack fuel = items.get(0);
                int bt = fuel.isEmpty() ? 0 : ForgeHooks.getBurnTime(fuel, null);
                if (bt > 0) {
                    burn = burnMax = bt * 3 / 2;
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
        public void serverTick() {
            burnTick();
            super.serverTick();
        }

        @Override
        protected boolean acceptsAutomation(int slot, ItemStack stack) {
            return slot == 0 && ForgeHooks.getBurnTime(stack, null) > 0;
        }

        @Override
        protected int automationLimit(int slot) {
            return 64;
        }

        @Override
        public float gaugeLevel() {
            return burnMax > 0 && burn > 0 ? burn / (float) burnMax : 0;
        }

        @Override
        public List<Component> gaugeTip() {
            return List.of(burn > 0 ? Component.translatable("gui.pioneers.farm.gauge.fire", FarmCard.percent(gaugeLevel()))
                                    : Component.translatable("gui.pioneers.farm.gauge.fire_out"));
        }

        @Override
        protected void declareValues(UiValues.Builder b) {
            b.add(UiValues.BURN, () -> burn);
            b.add(UiValues.BURN_MAX, () -> burnMax);
        }

        @Override
        protected void describe(FarmCard card, boolean sneaking) {
            //1.6 : feu en jauge (ou eteint), combustible en une valeur
            if (burnMax > 0 && burn > 0) {
                card.add(FarmCard.Line.gauge(FarmCard.label("fire"), burn / (float) burnMax, FarmCard.percent(burn / (float) burnMax), 0xF08030));
            } else {
                card.add(FarmCard.Line.pair(FarmCard.label("fire"), FarmCard.word("out"), FarmCard.WARN));
            }
            if (!items.get(0).isEmpty()) {
                card.add(FarmCard.Line.pair(FarmCard.label("fuel"), FarmCard.stack(items.get(0)), FarmCard.INFO));
            }
        }

        @Override
        public void clientTick() {
            if (burn > 0) {
                burn--;
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            tag.putInt("burn", burn);
            tag.putInt("burnMax", burnMax);
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            burn = tag.getInt("burn");
            burnMax = tag.getInt("burnMax");
        }
    }

    /** Chaleur du dessous (fourneau, feu) en jauge pleine ou vide ; infobulle. */
    static List<Component> heatTip(boolean hot) {
        return List.of(Component.translatable(hot ? "gui.pioneers.farm.gauge.heat_on" : "gui.pioneers.farm.gauge.heat_off"));
    }

    // ================================================================== marmite
    /**
     * Lot 41 : cases 0-5 ingredients, 6 le plat ; 7 / 8 la paire « Servir » (bol, tasse : le plat qui en demande un y est servi a la portion ;
     * avant : contenant en main), 9 / 10 la paire « Vider » (seau d'eau, de lait : dans la marmite).
     */
    public static class CookingPot extends StationTile {

        public static final int OUT = 6, SERVE_IN = 7, SERVE_OUT = 8, DRAIN_IN = 9, DRAIN_OUT = 10;
        @Nullable
        private KitchenRecipe recipe;
        private boolean recipeDirty = true;
        @Nullable
        private static Set<Item> containers;

        public CookingPot(BlockPos pos, BlockState state) {
            super(pos, state, 11, 6, 2000, 0);
        }

        @Override
        protected FarmLayout layout() {
            return FarmLayout.builder().gauge(FarmLayout.Gauge.HEAT).tankIn(DRAIN_IN, DRAIN_OUT).inputs(range(0, 6), 3).arrow(true)
                  .outputs(new int[]{OUT}, 1).serve(SERVE_IN, SERVE_OUT).build();
        }

        @Nullable
        @Override
        protected Station selectorStation() {
            return Station.COOKING;
        }

        @Override
        protected void contentsChanged() {
            recipeDirty = true;
            super.contentsChanged();
        }

        @Override
        public void recipeLockChanged() {
            recipeDirty = true;
            super.recipeLockChanged();
        }

        @Nullable
        public KitchenRecipe recipe() {
            if (recipeDirty) {
                recipe = findExact(Station.COOKING);
                recipeDirty = false;
            }
            return recipe;
        }

        public boolean hot() {
            return level != null && Heat.below(level, worldPosition);
        }

        @Override
        public void serverTick() {
            ItemStack output = items.get(OUT);
            KitchenRecipe r = output.isEmpty() ? recipe() : null;
            boolean run = r != null && hot();
            if (run != working) {
                working = run;
                sync();
            }
            if (run) {
                need = r.time();
                if (++progress >= need) {
                    List<ItemStack> used = take(r, items, 0, OUT);
                    if (r.fluid() != null && tank != null) {
                        tank.drain(r.fluid().amount(), IFluidHandler.FluidAction.EXECUTE);
                    }
                    List<ItemStack> res = r.roll(level.random, null);
                    items.set(OUT, res.isEmpty() ? ItemStack.EMPTY : res.get(0));
                    remainders(used);
                    progress = 0;
                    working = false;
                    sound(this, SoundEvents.BREWING_STAND_BREW, 0.7F, 1.2F);
                    contentsChanged();
                }
            }
            super.serverTick();
        }

        /** Contenant demande pour servir le plat (bol, tasse...) ou vide. */
        public ItemStack serving() {
            ItemStack output = items.get(OUT);
            return output.getItem() instanceof FarmFoodItem food ? food.container() : ItemStack.EMPTY;
        }

        /** Contenants des plats de la cuisine (bols, tasses...) : ce que la paire « Servir » accepte sans plat pret. */
        private static Set<Item> containers() {
            if (containers == null) {
                Set<Item> set = new HashSet<>();
                for (var e : PiKitchen.FOODS.values()) {
                    if (e.get() instanceof FarmFoodItem food && !food.container().isEmpty()) {
                        set.add(food.container().getItem());
                    }
                }
                containers = set;
            }
            return containers;
        }

        @Override
        protected boolean acceptsServe(ItemStack stack) {
            ItemStack need = serving();
            return need.isEmpty() ? containers().contains(stack.getItem()) : stack.is(need.getItem());
        }

        /** Le plat qui demande un contenant ne se prend pas a la main : il se sert dans la paire « Servir ». */
        @Override
        protected boolean playerTakes(int slot) {
            return slot != OUT || serving().isEmpty();
        }

        @Override
        protected boolean serveAll() {
            boolean any = false;
            for (int k = 0; k < 64; k++) {
                ItemStack output = items.get(OUT), need = serving(), bowl = items.get(SERVE_IN);
                if (output.isEmpty() || need.isEmpty() || !bowl.is(need.getItem()) || !room(SERVE_OUT, output.copyWithCount(1))) {
                    break;
                }
                put(SERVE_OUT, output.split(1));
                bowl.shrink(1);
                any = true;
            }
            if (any) {
                sound(this, SoundEvents.BOTTLE_FILL, 0.6F, 1.3F);
            }
            return any;
        }

        @Override
        protected boolean acceptsInput(int slot, ItemStack stack) {
            return slot < OUT && couldTake(Station.COOKING, OUT, stack);
        }

        @Override
        protected int automationLimit(int slot) {
            return 16;
        }

        @Override
        public float gaugeLevel() {
            return hot() ? 1 : 0;
        }

        @Override
        public List<Component> gaugeTip() {
            return heatTip(hot());
        }

        @Override
        public void particles(RandomSource random) {
            if (working && level != null) {
                double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.62, z = worldPosition.getZ() + 0.5;
                level.addParticle(ParticleTypes.BUBBLE_POP, x + (random.nextDouble() - 0.5) * 0.5, y, z + (random.nextDouble() - 0.5) * 0.5, 0, 0.02, 0);
                if (random.nextInt(2) == 0) {
                    level.addParticle(ParticleTypes.CLOUD, x + (random.nextDouble() - 0.5) * 0.4, y + 0.15, z + (random.nextDouble() - 0.5) * 0.4, 0,
                          0.03, 0);
                }
            }
        }

        @Override
        protected void describe(FarmCard card, boolean sneaking) {
            //1.6 : plat pret (et de quoi le servir), ou plat en cours, chaleur s'il en manque, puis avancement et contenu
            ItemStack output = items.get(OUT);
            if (!output.isEmpty()) {
                card.add(FarmCard.Line.pair(FarmCard.label("ready"), FarmCard.stack(output), 0x7CDC6A));
                ItemStack need = serving();
                if (!need.isEmpty()) {
                    card.add(FarmCard.Line.pair(FarmCard.label("serve"), need.getHoverName(), FarmCard.INFO));
                }
                return;
            }
            KitchenRecipe r = recipe();
            if (r != null) {
                card.add(FarmCard.Line.pair(FarmCard.label("cooking"), r.getResultItem(level.registryAccess()).getHoverName(), 0xF0D070));
                if (!hot()) {
                    card.add(FarmCard.Line.pair(FarmCard.label("heat"), FarmCard.word("none_f"), FarmCard.BAD));
                }
            } else if (items.subList(0, OUT).stream().anyMatch(s -> !s.isEmpty())) {
                card.add(FarmCard.Line.pair(FarmCard.label("recipe"), FarmCard.word("incomplete"), FarmCard.DIM));
            }
            super.describe(card, sneaking);
        }
    }

    // ================================================================== poele, four, sechoir : cases independantes chronometrees
    abstract static class Slots extends Stove {

        protected final int from, count;
        protected final int[] times = new int[4];
        protected final int[] needs = new int[4];

        Slots(BlockPos pos, BlockState state, int slots, int from) {
            super(pos, state, slots, slots);
            this.from = from;
            this.count = slots - from;
        }

        abstract Station station();

        abstract boolean running();

        /** Jauge de gauche des postes sans foyer propre (poele : chaleur ; sechoir : air). */
        FarmLayout.Gauge gaugeKind() {
            return FarmLayout.Gauge.HEAT;
        }

        @Override
        protected FarmLayout layout() {
            FarmLayout.Builder b = FarmLayout.builder();
            if (from > 0) {
                b.fire(0);
            } else {
                b.gauge(gaugeKind());
            }
            return b.cells(range(from, from + count), 2).build();
        }

        @Nullable
        KitchenRecipe recipeFor(ItemStack stack) {
            return findSingle(station(), stack);
        }

        @Override
        public void serverTick() {
            if (from > 0) {
                burnTick();
            }
            boolean run = running();
            boolean any = false;
            for (int i = 0; i < count; i++) {
                ItemStack s = items.get(from + i);
                KitchenRecipe r = s.isEmpty() ? null : recipeFor(s);
                if (r == null) {
                    times[i] = 0;
                    continue;
                }
                needs[i] = r.time();
                //1.6 (lot A2) : pas d'avancement propre a chaque case (sechoir sur le feu : x2, recette qui exige le feu : 0 sans feu)
                int st = run ? step(r) : 0;
                any |= st > 0;
                if (st > 0 && (times[i] += st) >= needs[i]) {
                    List<ItemStack> res = r.roll(level.random, s);
                    items.set(from + i, res.isEmpty() ? ItemStack.EMPTY : res.get(0));
                    times[i] = 0;
                    done(i);
                    sync();
                }
            }
            boolean w = run && any;
            if (w != working) {
                working = w;
                sync();
            }
            if (working && level.getGameTime() % 20 == 0) {
                sync();
            }
        }

        void done(int slot) {
            sound(this, SoundEvents.FIRE_EXTINGUISH, 0.3F, 1.8F);
        }

        /** 1.6 (lot A2) : ticks d'avancement par tick pour cette recette (1 ; sechoir : 2 sur le feu, 0 si la recette exige le feu et qu'il manque). */
        int step(KitchenRecipe r) {
            return 1;
        }

        @Override
        public void clientTick() {
            super.clientTick();
            if (working) {
                for (int i = 0; i < count; i++) {
                    ItemStack s = items.get(from + i);
                    if (!s.isEmpty() && times[i] < needs[i]) {
                        KitchenRecipe r = recipeFor(s);
                        times[i] = Math.min(needs[i], times[i] + (r == null ? 0 : step(r)));
                    }
                }
            }
        }

        public float slotFraction(int i) {
            return needs[i] <= 0 ? 0 : Math.min(1F, times[i] / (float) needs[i]);
        }

        public boolean cooking(int i) {
            return times[i] > 0 && !items.get(from + i).isEmpty();
        }

        /** Lot 41 : barre de la case : avancement, pleine (verte) quand le produit est pret, -1 si la case est vide. */
        @Override
        public float cellProgress(int index) {
            int i = index - from;
            if (i < 0 || i >= count || items.get(index).isEmpty()) {
                return -1;
            }
            return recipeFor(items.get(index)) == null ? 1F : Math.min(0.99F, slotFraction(i));
        }

        @Override
        public float arrowProgress() {
            return 0;
        }

        @Override
        public float gaugeLevel() {
            return from > 0 ? super.gaugeLevel() : running() ? 1 : 0;
        }

        @Override
        public List<Component> gaugeTip() {
            return from > 0 ? super.gaugeTip() : heatTip(running());
        }

        @Override
        protected boolean acceptsAutomation(int slot, ItemStack stack) {
            if (slot < from) {
                return super.acceptsAutomation(slot, stack);
            }
            return items.get(slot).isEmpty() && recipeFor(stack.copyWithCount(1)) != null;
        }

        @Override
        protected int automationLimit(int slot) {
            return slot < from ? 64 : 1;
        }

        @Override
        protected boolean extractable(int slot) {
            return slot >= from && !items.get(slot).isEmpty() && recipeFor(items.get(slot)) == null;
        }

        @Override
        protected boolean extractInputs() {
            return true;
        }

        @Override
        protected void slotTaken() {
            for (int i = 0; i < count; i++) {
                if (items.get(from + i).isEmpty()) {
                    times[i] = 0;
                }
            }
        }

        @Override
        protected void describe(FarmCard card, boolean sneaking) {
            if (from > 0) {
                super.describe(card, sneaking);
            } else if (!running()) {
                card.add(station() == Station.DRYING ? FarmCard.Line.pair(FarmCard.label("air"), FarmCard.word("rain"), FarmCard.WARN)
                                                     : FarmCard.Line.pair(FarmCard.label("heat"), FarmCard.word("none_f"), FarmCard.BAD));
            }
            //Lot 41 : ce qui cuit (premiere case engagee) et combien sont prets
            int ready = 0, cooking = 0;
            for (int i = 0; i < count; i++) {
                ItemStack s = items.get(from + i);
                if (!s.isEmpty()) {
                    if (recipeFor(s) == null) {
                        ready++;
                    } else {
                        cooking++;
                    }
                }
            }
            if (cooking > 0) {
                card.add(FarmCard.Line.pair(FarmCard.label("cooking"), Component.literal(String.valueOf(cooking)), 0xF0D070));
            }
            if (ready > 0) {
                card.add(FarmCard.Line.pair(FarmCard.label("ready"), Component.literal(String.valueOf(ready)), 0x7CDC6A));
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            tag.putIntArray("times", times);
            tag.putIntArray("needs", needs);
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            int[] t = tag.getIntArray("times"), n = tag.getIntArray("needs");
            System.arraycopy(t, 0, times, 0, Math.min(t.length, 4));
            System.arraycopy(n, 0, needs, 0, Math.min(n.length, 4));
        }
    }

    public static class FryingPan extends Slots {

        public FryingPan(BlockPos pos, BlockState state) {
            super(pos, state, 4, 0);
        }

        @Override
        Station station() {
            return Station.FRYING;
        }

        @Override
        boolean running() {
            return level != null && Heat.below(level, worldPosition);
        }

        @Override
        public void particles(RandomSource random) {
            if (working && level != null) {
                level.addParticle(ParticleTypes.SMOKE, worldPosition.getX() + 0.3 + random.nextDouble() * 0.4, worldPosition.getY() + 0.25,
                      worldPosition.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
                if (random.nextInt(8) == 0) {
                    level.playLocalSound(worldPosition, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.6F, 1.6F, false);
                }
            }
        }
    }

    public static class Oven extends Slots {

        public Oven(BlockPos pos, BlockState state) {
            super(pos, state, 5, 1);
        }

        @Override
        Station station() {
            return Station.BAKING;
        }

        @Override
        boolean running() {
            return lit();
        }

        @Override
        void done(int slot) {
            sound(this, SoundEvents.CAMPFIRE_CRACKLE, 0.6F, 1.0F);
        }
    }

    public static class DryingRack extends Slots {

        public DryingRack(BlockPos pos, BlockState state) {
            super(pos, state, 4, 0);
        }

        @Override
        Station station() {
            return Station.DRYING;
        }

        @Override
        FarmLayout.Gauge gaugeKind() {
            return FarmLayout.Gauge.AIR;
        }

        /** 1.6 (lot A2) : feu de camp allume sous le sechoir (pose dessus : etat lit) ou feu 1 a 3 blocs plus bas. */
        public boolean fire() {
            return level != null && Heat.smoke(level, worldPosition);
        }

        @Override
        boolean running() {
            return level != null && (!level.isRainingAt(worldPosition.above()) || fire());
        }

        @Override
        public List<Component> gaugeTip() {
            return List.of(Component.translatable(fire() ? "gui.pioneers.farm.gauge.air_fire" : running() ? "gui.pioneers.farm.gauge.air_dry"
                                                                                                         : "gui.pioneers.farm.gauge.air_rain"));
        }

        /** 1.6 (lot A2) : le sechoir n'enfume plus (c'est le fumoir) ; une recette {@code smoke} exige le sechoir sur un feu de camp allume. */
        @Override
        @Nullable
        KitchenRecipe recipeFor(ItemStack stack) {
            return findSingle(Station.DRYING, stack);
        }

        /** Sur le feu : deux fois plus vite ; recette qui exige le feu : arretee sans feu. */
        @Override
        int step(KitchenRecipe r) {
            boolean f = fire();
            return f ? 2 : r.smoke() ? 0 : 1;
        }

        @Override
        public void serverTick() {
            super.serverTick();
            //Etat « pose sur un feu de camp » (piquets du modele) : rattrape les sechoirs poses avant la 1.6 ou un feu de camp change
            if (level != null && level.getGameTime() % 40 == 7) {
                StationBlock.syncRackOverFire(level, worldPosition, getBlockState());
            }
        }

        @Override
        protected void describe(FarmCard card, boolean sneaking) {
            super.describe(card, sneaking);
            if (fire()) {
                card.add(FarmCard.Line.pair(FarmCard.label("fire"), FarmCard.word("rack.over_fire"), FarmCard.OK));
            } else {
                for (int i = 0; i < count; i++) {
                    KitchenRecipe r = items.get(i).isEmpty() ? null : recipeFor(items.get(i));
                    if (r != null && r.smoke()) {
                        card.add(FarmCard.Line.pair(FarmCard.label("fire"), FarmCard.word("rack.need_fire"), FarmCard.WARN));
                        break;
                    }
                }
            }
        }

        @Override
        void done(int slot) {
            sound(this, SoundEvents.GRASS_BREAK, 0.4F, 1.4F);
        }
    }

    // ================================================================== saladier, baratte (recettes du poste, coups)
    /** Lot 41 : cases 0-5 ingredients, 6 le produit, 7 / 8 la paire « Vider » (seau de lait, d'eau : dans le saladier) ; bouton du geste. */
    public static class MixingBowl extends StationTile {

        Station station() {
            Station st = kind().station();
            return st == null ? Station.MIXING : st;
        }

        public static final int OUT = 6, DRAIN_IN = 7, DRAIN_OUT = 8;

        public MixingBowl(BlockPos pos, BlockState state) {
            super(pos, state, 9, 6, 1000, 0);
        }

        @Override
        protected FarmLayout layout() {
            return FarmLayout.builder().tankIn(DRAIN_IN, DRAIN_OUT).inputs(range(0, 6), 3).arrow(true).outputs(new int[]{OUT}, 1)
                  .button(kind() == StationBlock.Kind.BUTTER_CHURN ? "gui.pioneers.farm.button.churn" : "gui.pioneers.farm.button.mix").build();
        }

        @Nullable
        @Override
        protected Station selectorStation() {
            return station();
        }

        @Override
        protected boolean acceptsInput(int slot, ItemStack stack) {
            return slot < OUT && couldTake(station(), OUT, stack);
        }

        @Override
        protected void slotTaken() {
            progress = 0;
        }

        @Override
        public boolean buttonActive(int i) {
            return items.get(OUT).isEmpty() && findExact(station()) != null;
        }

        @Override
        public List<Component> buttonTip(int i) {
            if (!items.get(OUT).isEmpty()) {
                return List.of(Component.translatable("farm.pioneers.station.take_first"));
            }
            KitchenRecipe r = findExact(station());
            return List.of(r == null ? Component.translatable("farm.pioneers.station.incomplete_mix")
                                     : Component.translatable("gui.pioneers.farm.tip.strokes", progress, Math.max(1, r.strokes())));
        }

        @Override
        public void manual(Player player, int button) {
            if (level == null) {
                return;
            }
            ItemStack output = items.get(OUT);
            KitchenRecipe r = findExact(station());
            if (r == null) {
                if (items.subList(0, OUT).stream().anyMatch(s -> !s.isEmpty())) {
                    say(player, "incomplete_mix");
                }
                return;
            }
            if (!output.isEmpty()) {
                say(player, "take_first");
                return;
            }
            need = Math.max(1, r.strokes());
            progress += stroke(player);
            strokes++;
            sound(this, SoundEvents.HONEY_BLOCK_SLIDE, 0.6F, 1.3F + level.random.nextFloat() * 0.3F);
            if (progress >= need) {
                dish(player);
                List<ItemStack> used = take(r, items, 0, OUT);
                if (r.fluid() != null && tank != null) {
                    tank.drain(r.fluid().amount(), IFluidHandler.FluidAction.EXECUTE);
                }
                List<ItemStack> res = r.roll(level.random, null);
                items.set(OUT, res.isEmpty() ? ItemStack.EMPTY : res.get(0));
                remainders(used);
                progress = 0;
                sound(this, SoundEvents.SLIME_SQUISH_SMALL, 0.8F, 1.0F);
            }
            contentsChanged();
        }

        @Override
        protected int automationLimit(int slot) {
            return 16;
        }

        @Override
        public float arrowProgress() {
            KitchenRecipe r = level == null ? null : findExact(station());
            return r == null ? 0 : Math.min(1F, progress / (float) Math.max(1, r.strokes()));
        }

        @Override
        protected void describe(FarmCard card, boolean sneaking) {
            //1.6 : pret, ou ce qui se prepare avec ses coups en jauge, puis le contenu
            ItemStack output = items.get(OUT);
            if (!output.isEmpty()) {
                card.add(FarmCard.Line.pair(FarmCard.label("ready"), FarmCard.stack(output), 0x7CDC6A));
                return;
            }
            KitchenRecipe r = findExact(station());
            if (r != null) {
                card.add(FarmCard.Line.gauge(FarmCard.label("strokes"), progress / (float) Math.max(1, r.strokes()),
                      r.getResultItem(level.registryAccess()).getHoverName(), 0xF0B040));
            } else if (items.subList(0, OUT).stream().anyMatch(s -> !s.isEmpty())) {
                card.add(FarmCard.Line.pair(FarmCard.label("recipe"), FarmCard.word("incomplete"), FarmCard.DIM));
            }
            contents(card);
        }
    }

    // ================================================================== bocal
    public static class Jar extends StationTile {

        static final int OUT = 4;
        private boolean sealed;

        public Jar(BlockPos pos, BlockState state) {
            super(pos, state, 5, 4, 0, 0);
        }

        @Override
        protected FarmLayout layout() {
            return FarmLayout.builder().inputs(range(0, 4), 2).arrow(true).outputs(new int[]{OUT}, 1).build();
        }

        @Nullable
        @Override
        protected Station selectorStation() {
            return Station.PRESERVING;
        }

        public boolean sealed() {
            return sealed;
        }

        @Override
        public void serverTick() {
            if (!items.get(OUT).isEmpty()) {
                return;
            }
            KitchenRecipe r = findExact(Station.PRESERVING);
            if (r == null) {
                if (sealed || working) {
                    sealed = working = false;
                    progress = 0;
                    sync();
                }
                return;
            }
            if (!sealed) {
                sealed = true;
                working = true;
                need = r.time();
                sound(this, SoundEvents.WOODEN_TRAPDOOR_CLOSE, 0.7F, 1.6F);
                sync();
            }
            if (++progress >= need) {
                ItemStack source = ItemStack.EMPTY;
                KitchenRecipe.Counted first = r.ingredients().get(0);
                for (int i = 0; i < OUT; i++) {
                    if (first.ingredient().test(items.get(i))) {
                        source = items.get(i).copy();
                        break;
                    }
                }
                List<ItemStack> used = take(r, items, 0, OUT);
                List<ItemStack> res = r.roll(level.random, source);
                items.set(OUT, res.isEmpty() ? ItemStack.EMPTY : res.get(0));
                for (int i = 1; i < res.size(); i++) {
                    Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5, res.get(i));
                }
                remainders(used);
                sealed = working = false;
                progress = 0;
                sound(this, SoundEvents.WOODEN_TRAPDOOR_OPEN, 0.7F, 1.6F);
                sync();
            }
            super.serverTick();
        }

        /** Bocal scelle : on n'ouvre pas avant la fin (ni ajout ni retrait d'ingredient). */
        @Override
        protected boolean acceptsInput(int slot, ItemStack stack) {
            return !sealed && slot < OUT && couldTake(Station.PRESERVING, OUT, stack);
        }

        @Override
        protected boolean playerTakes(int slot) {
            return slot >= OUT || !sealed;
        }

        @Override
        protected int automationLimit(int slot) {
            return 16;
        }

        @Override
        protected void describe(FarmCard card, boolean sneaking) {
            if (sealed) {
                card.add(FarmCard.Line.gauge(FarmCard.label("preserving"), fraction(), FarmCard.percent(fraction()), 0xC06080));
                contents(card);
                return;
            }
            super.describe(card, sneaking);
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            tag.putBoolean("sealed", sealed);
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            sealed = tag.getBoolean("sealed");
        }
    }

    // ================================================================== cuve, chaudron de brassage, alambic : fluide -> fluide par lots
    /** Lot 41 : cases 0-1 ingredients, 2 le produit, 3 / 4 la paire « Vider » (cuve d'entree), 5 / 6 la paire « Remplir » (cuve de sortie). */
    abstract static class Batch extends StationTile {

        public static final int OUT = 2, DRAIN_IN = 3, DRAIN_OUT = 4, FILL_IN = 5, FILL_OUT = 6;

        Batch(BlockPos pos, BlockState state, int tankCap, int outCap) {
            super(pos, state, 7, 2, tankCap, outCap);
        }

        abstract Station station();

        boolean powered() {
            return true;
        }

        /** Poste chauffe par le dessous (chaudron, alambic) : jauge de la chaleur. */
        boolean heated() {
            return false;
        }

        @Override
        protected FarmLayout layout() {
            FarmLayout.Builder b = FarmLayout.builder();
            if (heated()) {
                b.gauge(FarmLayout.Gauge.HEAT);
            }
            return b.tankIn(DRAIN_IN, DRAIN_OUT).inputs(new int[]{0, 1}, 1).arrow(true).outputs(new int[]{OUT}, 1).tankOut(FILL_IN, FILL_OUT).build();
        }

        @Nullable
        @Override
        protected Station selectorStation() {
            return station();
        }

        @Nullable
        KitchenRecipe current() {
            if (tank == null) {
                return null;
            }
            KitchenRecipe locked = lockedKitchen(station());
            if (locked != null) {
                return locked.fluidOk(tank.getFluid()) && hasAtLeast(locked, items, 0, OUT) && (locked.fluid() != null || !locked.ingredients().isEmpty())
                       ? locked : null;
            }
            for (KitchenRecipe r : recipes(station())) {
                if (r.fluidOk(tank.getFluid()) && hasAtLeast(r, items, 0, OUT) && (r.fluid() != null || !r.ingredients().isEmpty())) {
                    return r;
                }
            }
            return null;
        }

        private boolean room(KitchenRecipe r) {
            if (!r.fluidResult().isEmpty() && (out == null || out.fill(r.fluidResult(), IFluidHandler.FluidAction.SIMULATE) < r.fluidResult().getAmount())) {
                return false;
            }
            for (KitchenRecipe.Result res : r.results()) {
                ItemStack o = items.get(OUT);
                if (!o.isEmpty() && (!ItemStack.isSameItemSameTags(o, res.stack()) || o.getCount() + res.stack().getCount() > o.getMaxStackSize())) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void serverTick() {
            KitchenRecipe r = current();
            boolean run = r != null && powered() && room(r);
            if (run != working) {
                working = run;
                if (!run) {
                    progress = 0;
                }
                sync();
            }
            if (run) {
                need = r.time();
                if (++progress >= need) {
                    if (r.fluid() != null) {
                        tank.drain(r.fluid().amount(), IFluidHandler.FluidAction.EXECUTE);
                    }
                    List<ItemStack> used = take(r, items, 0, OUT);
                    remainders(used);
                    if (!r.fluidResult().isEmpty() && out != null) {
                        out.fill(r.fluidResult().copy(), IFluidHandler.FluidAction.EXECUTE);
                    }
                    for (ItemStack res : r.roll(level.random, null)) {
                        ItemStack o = items.get(OUT);
                        if (o.isEmpty()) {
                            items.set(OUT, res);
                        } else {
                            o.grow(res.getCount());
                        }
                    }
                    progress = 0;
                    finished();
                    contentsChanged();
                }
            }
            super.serverTick();
        }

        void finished() {
            sound(this, SoundEvents.BREWING_STAND_BREW, 0.6F, 0.8F);
        }

        @Override
        protected boolean acceptsInput(int slot, ItemStack stack) {
            if (slot >= OUT) {
                return false;
            }
            KitchenRecipe locked = lockedKitchen(station());
            if (locked != null) {
                return locked.accepts(stack);
            }
            for (KitchenRecipe r : recipes(station())) {
                if (r.accepts(stack)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected int automationLimit(int slot) {
            return slot < OUT ? 64 : super.automationLimit(slot);
        }

        @Override
        public float gaugeLevel() {
            return powered() ? 1 : 0;
        }

        @Override
        public List<Component> gaugeTip() {
            return heatTip(powered());
        }

        @Override
        protected void describe(FarmCard card, boolean sneaking) {
            if (!powered()) {
                card.add(FarmCard.Line.pair(FarmCard.label("heat"), FarmCard.word("none_f"), FarmCard.BAD));
            }
            super.describe(card, sneaking);
        }
    }

    public static class Vat extends Batch {

        public Vat(BlockPos pos, BlockState state) {
            super(pos, state, 8000, 8000);           //1.6 (lot A2) : grande cuve 2 x 2
        }

        @Override
        Station station() {
            return Station.FERMENTING;
        }

        @Override
        public void particles(RandomSource random) {
            if (working && level != null && random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.BUBBLE_POP, worldPosition.getX() + 0.2 + random.nextDouble() * 0.6, worldPosition.getY() + 0.85,
                      worldPosition.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.01, 0);
                if (random.nextInt(4) == 0) {
                    level.playLocalSound(worldPosition, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.3F, 1.4F, false);
                }
            }
        }
    }

    public static class Kettle extends Batch {

        public Kettle(BlockPos pos, BlockState state) {
            super(pos, state, 4000, 4000);           //1.6 (lot A2) : grand chaudron 1 x 2
        }

        @Override
        Station station() {
            return Station.BREWING;
        }

        @Override
        boolean heated() {
            return true;
        }

        @Override
        boolean powered() {
            return level != null && Heat.below(level, worldPosition);
        }

        @Override
        public void particles(RandomSource random) {
            if (working && level != null) {
                level.addParticle(ParticleTypes.CLOUD, worldPosition.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3, worldPosition.getY() + 1.0,
                      worldPosition.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3, 0, 0.05, 0);
            }
        }
    }

    public static class Still extends Batch {

        public Still(BlockPos pos, BlockState state) {
            super(pos, state, 4000, 4000);           //1.6 (lot A2) : grand alambic 2 x 2
        }

        @Override
        Station station() {
            return Station.DISTILLING;
        }

        @Override
        boolean heated() {
            return true;
        }

        @Override
        boolean powered() {
            return level != null && Heat.below(level, worldPosition);
        }

        @Override
        public void particles(RandomSource random) {
            if (working && level != null) {
                Direction f = facing();
                level.addParticle(ParticleTypes.DRIPPING_WATER, worldPosition.getX() + 0.5 - f.getStepX() * 0.45, worldPosition.getY() + 0.35,
                      worldPosition.getZ() + 0.5 - f.getStepZ() * 0.45, 0, 0, 0);
                if (random.nextInt(2) == 0) {
                    level.addParticle(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.05, worldPosition.getZ() + 0.5, 0, 0.04, 0);
                }
            }
        }
    }

    // ================================================================== fut de vieillissement
    /** Lot 41 : cases 0 / 1 la paire « Vider » (avant : seau au clic), 2 / 3 la paire « Remplir » (bouteilles, chopes, seaux). */
    public static class Barrel extends StationTile {

        public static final int DRAIN_IN = 0, DRAIN_OUT = 1, FILL_IN = 2, FILL_OUT = 3;
        private int age, lastAmount;

        public Barrel(BlockPos pos, BlockState state) {
            super(pos, state, 4, 0, 16000, 0);       //1.6 (lot A2) : gros fut 2 x 2
        }

        @Override
        protected FarmLayout layout() {
            return FarmLayout.builder().tank(DRAIN_IN, DRAIN_OUT, FILL_IN, FILL_OUT).build();
        }

        @Nullable
        KitchenRecipe current() {
            if (tank == null || tank.isEmpty()) {
                return null;
            }
            for (KitchenRecipe r : recipes(Station.AGING)) {
                if (r.fluid() != null && r.fluid().test(new FluidStack(tank.getFluid(), Math.max(tank.getFluidAmount(), r.fluid().amount())))) {
                    return r;
                }
            }
            return null;
        }

        @Override
        protected void contentsChanged() {
            int now = tank == null ? 0 : tank.getFluidAmount();
            if (now > lastAmount && lastAmount > 0) {
                age = (int) ((long) age * lastAmount / now);
            } else if (now == 0) {
                age = 0;
            }
            lastAmount = now;
            super.contentsChanged();
        }

        @Override
        public void serverTick() {
            KitchenRecipe r = current();
            boolean run = r != null;
            if (run != working) {
                working = run;
                sync();
            }
            if (run) {
                need = r.time();
                progress = ++age;
                if (age >= need) {
                    int amount = tank.getFluidAmount() * r.fluidResult().getAmount() / r.fluid().amount();
                    tank.setFluid(new FluidStack(r.fluidResult().getFluid(), amount));
                    age = 0;
                    lastAmount = amount;
                    contentsChanged();
                }
            }
            super.serverTick();
        }

        @Override
        protected void describe(FarmCard card, boolean sneaking) {
            KitchenRecipe r = current();
            if (r != null) {
                card.add(FarmCard.Line.gauge(FarmCard.label("aging"), fraction(), r.fluidResult().getDisplayName(), 0xB07040));
                contents(card);
                return;
            }
            super.describe(card, sneaking);
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            tag.putInt("age", age);
            tag.putInt("last", lastAmount);
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            age = tag.getInt("age");
            lastAmount = tag.getInt("last");
        }
    }

    // ================================================================== presses (gestes)
    /** Lot 41 : case 0 les fruits (16), cuve de jus ; 1 / 2 la paire « Remplir » (bouteilles, seaux ; avant : au clic) ; bouton « Presser ». */
    public static class Press extends StationTile {

        public static final int FILL_IN = 1, FILL_OUT = 2;

        public Press(BlockPos pos, BlockState state) {
            super(pos, state, 3, 1, 0, 4000);
        }

        @Override
        protected FarmLayout layout() {
            return FarmLayout.builder().inputs(new int[]{0}, 1).arrow(true).tankOut(FILL_IN, FILL_OUT).button("gui.pioneers.farm.button.press").build();
        }

        @Nullable
        @Override
        protected Station selectorStation() {
            return Station.PRESSING;
        }

        @Nullable
        KitchenRecipe recipe() {
            ItemStack s = items.get(0);
            if (s.isEmpty()) {
                return null;
            }
            KitchenRecipe locked = lockedKitchen(Station.PRESSING);
            if (locked != null) {
                return locked.ingredients().size() == 1 && locked.ingredients().get(0).ingredient().test(s) ? locked : null;
            }
            for (KitchenRecipe r : recipes(Station.PRESSING)) {
                if (r.ingredients().size() == 1 && r.ingredients().get(0).ingredient().test(s)) {
                    return r;
                }
            }
            return null;
        }

        @Override
        protected boolean acceptsInput(int slot, ItemStack stack) {
            if (slot != 0) {
                return false;
            }
            KitchenRecipe locked = lockedKitchen(Station.PRESSING);
            if (locked != null) {
                return locked.accepts(stack);
            }
            for (KitchenRecipe r : recipes(Station.PRESSING)) {
                if (r.accepts(stack)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void slotTaken() {
            if (items.get(0).isEmpty()) {
                progress = 0;
            }
        }

        private int problem() {
            KitchenRecipe r = recipe();
            if (r == null) {
                return 1;
            }
            if (items.get(0).getCount() < r.ingredients().get(0).count()) {
                return 2;
            }
            return out.fill(r.fluidResult(), IFluidHandler.FluidAction.SIMULATE) < r.fluidResult().getAmount() ? 3 : 0;
        }

        @Override
        public boolean buttonActive(int i) {
            return problem() == 0;
        }

        @Override
        public List<Component> buttonTip(int i) {
            KitchenRecipe r = recipe();
            return List.of(switch (problem()) {
                case 1 -> Component.translatable("gui.pioneers.farm.tip.need_input");
                case 2 -> Component.translatable("farm.pioneers.station.need_more", r.ingredients().get(0).count(), items.get(0).getHoverName());
                case 3 -> Component.translatable("farm.pioneers.station.tank_full");
                default -> Component.translatable("gui.pioneers.farm.tip.strokes", progress, Math.max(1, r.strokes()));
            });
        }

        @Override
        public void manual(Player player, int button) {
            if (level == null) {
                return;
            }
            ItemStack in = items.get(0);
            KitchenRecipe r = recipe();
            if (r == null) {
                return;
            }
            if (in.getCount() < r.ingredients().get(0).count()) {
                say(player, "need_more", r.ingredients().get(0).count(), in.getHoverName());
                return;
            }
            if (out.fill(r.fluidResult(), IFluidHandler.FluidAction.SIMULATE) < r.fluidResult().getAmount()) {
                say(player, "tank_full");
                return;
            }
            need = Math.max(1, r.strokes());
            progress += stroke(player);
            strokes++;
            sound(this, SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.7F, 0.7F);
            sound(this, SoundEvents.HONEY_BLOCK_STEP, 0.6F, 0.9F);
            itemBurst(this, in, 0.5, 3);
            if (progress >= need) {
                dish(player);
                in.shrink(r.ingredients().get(0).count());
                out.fill(r.fluidResult().copy(), IFluidHandler.FluidAction.EXECUTE);
                progress = 0;
                sound(this, SoundEvents.BUCKET_FILL, 0.6F, 1.2F);
            }
            contentsChanged();
        }

        @Override
        protected int automationLimit(int slot) {
            return slot == 0 ? 16 : super.automationLimit(slot);
        }

        @Override
        public float arrowProgress() {
            KitchenRecipe r = level == null ? null : recipe();
            return r == null ? 0 : Math.min(1F, progress / (float) Math.max(1, r.strokes()));
        }

        @Override
        protected void describe(FarmCard card, boolean sneaking) {
            KitchenRecipe r = recipe();
            if (r != null) {
                float f = progress / (float) Math.max(1, r.strokes());
                card.add(FarmCard.Line.gauge(FarmCard.label("press"), f, FarmCard.percent(f), 0xA060C0));
                contents(card);
                return;
            }
            super.describe(card, sneaking);
        }
    }

    // ================================================================== extracteur de miel
    // 1.0.5-beta (lot 32) : classe propre, HoneyExtractorTile (meme tuile, memes cles de sauvegarde) ; lot 41 : interface standard

    /** Presse a fromage : case 0 le caille, 1 la meule ; bouton « Presser ». */
    public static class CheesePress extends StationTile {

        public CheesePress(BlockPos pos, BlockState state) {
            super(pos, state, 2, 1, 0, 0);
        }

        @Override
        protected FarmLayout layout() {
            return FarmLayout.builder().inputs(new int[]{0}, 1).arrow(true).outputs(new int[]{1}, 1).button("gui.pioneers.farm.button.press_cheese").build();
        }

        @Nullable
        @Override
        protected Station selectorStation() {
            return Station.CHEESE_PRESSING;
        }

        @Nullable
        KitchenRecipe recipe() {
            KitchenRecipe locked = lockedKitchen(Station.CHEESE_PRESSING);
            if (locked != null) {
                return locked;
            }
            KitchenRecipe r = null;
            for (KitchenRecipe k : recipes(Station.CHEESE_PRESSING)) {
                if (k.matchesSingle(items.get(0))) {
                    return k;
                }
                r = k;
            }
            return r;
        }

        @Override
        protected boolean acceptsInput(int slot, ItemStack stack) {
            if (slot != 0) {
                return false;
            }
            for (KitchenRecipe k : recipes(Station.CHEESE_PRESSING)) {
                if (k.accepts(stack)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected int automationLimit(int slot) {
            return slot == 0 ? 16 : super.automationLimit(slot);
        }

        @Override
        protected void slotTaken() {
            if (items.get(0).isEmpty()) {
                progress = 0;
            }
        }

        @Override
        public boolean buttonActive(int i) {
            KitchenRecipe r = recipe();
            return r != null && r.matchesSingle(items.get(0)) && items.get(1).isEmpty();
        }

        @Override
        public List<Component> buttonTip(int i) {
            KitchenRecipe r = recipe();
            if (!items.get(1).isEmpty()) {
                return List.of(Component.translatable("farm.pioneers.station.take_first"));
            }
            if (r == null || !r.matchesSingle(items.get(0))) {
                return List.of(Component.translatable("gui.pioneers.farm.tip.need_input"));
            }
            return List.of(Component.translatable("gui.pioneers.farm.tip.strokes", progress, Math.max(1, r.strokes())));
        }

        @Override
        public void manual(Player player, int button) {
            KitchenRecipe r = recipe();
            if (level == null || r == null || !r.matchesSingle(items.get(0)) || !items.get(1).isEmpty()) {
                return;
            }
            need = Math.max(1, r.strokes());
            progress += stroke(player);
            strokes++;
            sound(this, SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.7F, 0.6F);
            if (progress >= need) {
                dish(player);
                List<ItemStack> res = r.roll(level.random, null);
                items.get(0).shrink(r.ingredients().get(0).count());
                items.set(1, res.isEmpty() ? ItemStack.EMPTY : res.get(0));
                progress = 0;
                sound(this, SoundEvents.SLIME_BLOCK_PLACE, 0.6F, 1.2F);
            }
            contentsChanged();
        }

        @Override
        public float arrowProgress() {
            KitchenRecipe r = level == null ? null : recipe();
            return r == null ? 0 : Math.min(1F, progress / (float) Math.max(1, r.strokes()));
        }
    }

    // ================================================================== sertisseuse (conserves)
    /** Sertisseuse : case 0 les boites vides, 1 le plat, 2 les conserves ; bouton « Sertir ». */
    public static class Sealer extends StationTile {

        static final int STROKES = 3;

        public Sealer(BlockPos pos, BlockState state) {
            super(pos, state, 3, 2, 0, 0);
        }

        @Override
        protected FarmLayout layout() {
            return FarmLayout.builder().inputs(new int[]{0, 1}, 1).arrow(false).outputs(new int[]{2}, 1).button("gui.pioneers.farm.button.seal").build();
        }

        @Override
        protected boolean acceptsInput(int slot, ItemStack stack) {
            return slot == 0 ? stack.is(PiFarm.item("pioneers:tin_can")) : slot == 1 && CannedFoodItem.cannable(stack);
        }

        @Override
        protected boolean acceptsAutomation(int slot, ItemStack stack) {
            return slot < 2 ? acceptsInput(slot, stack) : super.acceptsAutomation(slot, stack);
        }

        @Override
        protected ItemStack ghost(FarmLayout.SlotSpec spec) {
            return spec.index() == 0 ? new ItemStack(PiFarm.item("pioneers:tin_can")) : super.ghost(spec);
        }

        @Override
        protected void slotTaken() {
            if (items.get(0).isEmpty() || items.get(1).isEmpty()) {
                progress = 0;
            }
        }

        private int problem() {
            ItemStack can = items.get(0), dish = items.get(1), outStack = items.get(2);
            if (can.isEmpty() || dish.isEmpty()) {
                return 1;
            }
            ItemStack product = CannedFoodItem.of(PiFarm.item("pioneers:canned_food"), dish);
            return !outStack.isEmpty() && (!ItemStack.isSameItemSameTags(outStack, product) || outStack.getCount() >= outStack.getMaxStackSize()) ? 2 : 0;
        }

        @Override
        public boolean buttonActive(int i) {
            return problem() == 0;
        }

        @Override
        public List<Component> buttonTip(int i) {
            return List.of(switch (problem()) {
                case 1 -> Component.translatable("farm.pioneers.station.sealer_needs");
                case 2 -> Component.translatable("farm.pioneers.station.take_first");
                default -> Component.translatable("gui.pioneers.farm.tip.strokes", progress, STROKES);
            });
        }

        @Override
        public void manual(Player player, int button) {
            if (level == null) {
                return;
            }
            int p = problem();
            if (p != 0) {
                say(player, p == 1 ? "sealer_needs" : "take_first");
                return;
            }
            ItemStack can = items.get(0), dish = items.get(1), outStack = items.get(2);
            ItemStack product = CannedFoodItem.of(PiFarm.item("pioneers:canned_food"), dish);
            need = STROKES;
            progress += stroke(player);
            strokes++;
            sound(this, SoundEvents.IRON_TRAPDOOR_CLOSE, 0.5F, 1.6F);
            if (progress >= need) {
                dish(player);
                ItemStack rest = dish.getCraftingRemainingItem();
                can.shrink(1);
                dish.shrink(1);
                if (!rest.isEmpty()) {
                    Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5, rest);
                }
                if (outStack.isEmpty()) {
                    items.set(2, product);
                } else {
                    outStack.grow(1);
                }
                progress = 0;
                sound(this, SoundEvents.ANVIL_LAND, 0.3F, 1.8F);
            }
            contentsChanged();
        }

        @Override
        public float arrowProgress() {
            return Math.min(1F, progress / (float) STROKES);
        }

        @Override
        protected int automationLimit(int slot) {
            return 16;
        }
    }

    // ================================================================== elevage : mangeoire et abreuvoir
    /** Mangeoire : 9 cases de nourriture (reserve) ; les betes y mangent ({@code NeedGoal}). */
    public static class Feeder extends StationTile {

        public Feeder(BlockPos pos, BlockState state) {
            super(pos, state, 9, 9, 0, 0);
        }

        @Override
        protected FarmLayout layout() {
            return FarmLayout.builder().store(range(0, 9), 3, "gui.pioneers.farm.slot.feed").build();
        }

        @Override
        public void onLoad() {
            super.onLoad();
            if (level != null) {
                FarmSites.add(level, worldPosition, false);
            }
        }

        @Override
        public void setRemoved() {
            super.setRemoved();
            if (level != null) {
                FarmSites.remove(level, worldPosition, false);
            }
        }

        /** Un aliment pour cette espece ? */
        public boolean hasFor(AnimalSpecies species) {
            for (ItemStack s : items) {
                if (species.feedValue(s) > 0) {
                    return true;
                }
            }
            return false;
        }

        /** L'animal mange un aliment : valeur nourrissante (0 si rien pour lui) ; {@code eaten} recoit l'objet mange (particules). */
        public int eat(AnimalSpecies species, ItemStack[] eaten) {
            for (ItemStack s : items) {
                int v = species.feedValue(s);
                if (v > 0) {
                    eaten[0] = s.copyWithCount(1);
                    s.shrink(1);
                    contentsChanged();
                    return v;
                }
            }
            return 0;
        }

        public int total() {
            int n = 0;
            for (ItemStack s : items) {
                n += s.getCount();
            }
            return n;
        }

        @Override
        protected boolean acceptsAutomation(int slot, ItemStack stack) {
            return AnimalSpecies.anyFeed(stack);
        }

        @Override
        protected int automationLimit(int slot) {
            return 64;
        }

        @Override
        protected boolean extractable(int slot) {
            return false;
        }

        @Override
        protected void describe(FarmCard card, boolean sneaking) {
            card.add(FarmCard.Line.gauge(FarmCard.label("feed"), Math.min(1F, total() / 192F), Component.literal(String.valueOf(total())), 0xC8A050));
            super.describe(card, sneaking);
        }
    }

    /** Abreuvoir : cuve d'eau (pluie, tuyaux) ; lot 41 : cases 0 / 1 la paire « Vider » (seau d'eau ; avant : au clic). */
    public static class Trough extends StationTile {

        public static final int DRAIN_IN = 0, DRAIN_OUT = 1;

        public Trough(BlockPos pos, BlockState state) {
            super(pos, state, 2, 0, 4000, 0);
        }

        @Override
        protected FarmLayout layout() {
            return FarmLayout.builder().tank(DRAIN_IN, DRAIN_OUT, -1, -1).build();
        }

        @Override
        protected boolean canFill(FluidStack stack) {
            return stack.getFluid().isSame(Fluids.WATER);
        }

        @Override
        public void onLoad() {
            super.onLoad();
            if (level != null) {
                FarmSites.add(level, worldPosition, true);
            }
        }

        @Override
        public void setRemoved() {
            super.setRemoved();
            if (level != null) {
                FarmSites.remove(level, worldPosition, true);
            }
        }

        public boolean hasWater(int mb) {
            return tank != null && tank.getFluidAmount() >= mb;
        }

        public boolean drink(int mb) {
            if (!hasWater(mb)) {
                return false;
            }
            tank.drain(mb, IFluidHandler.FluidAction.EXECUTE);
            return true;
        }

        @Override
        public void serverTick() {
            //La pluie remplit l'abreuvoir a ciel ouvert
            if (level != null && level.getGameTime() % 20 == 7 && level.isRainingAt(worldPosition.above()) && tank != null
                && tank.getFluidAmount() < tank.getCapacity()) {
                tank.fill(new FluidStack(Fluids.WATER, 25), IFluidHandler.FluidAction.EXECUTE);
            }
            super.serverTick();
        }

        @Override
        protected void describe(FarmCard card, boolean sneaking) {
            if (tank != null) {
                float r = tank.getFluidAmount() / (float) tank.getCapacity();
                card.add(FarmCard.Line.gauge(FarmCard.label("water"), r, FarmCard.millibuckets(tank.getFluidAmount()), r < 0.1F ? FarmCard.BAD : 0x3C78DC));
            }
            if (level != null && level.isRainingAt(worldPosition.above())) {
                card.add(FarmCard.Line.pair(FarmCard.label("air"), FarmCard.word("rain"), FarmCard.WATER));
            }
        }
    }
}

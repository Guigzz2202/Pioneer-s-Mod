package fr.pioneers.forge.smithing;

import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.bronze.BronzeEvents;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.forge.ForgeHeat;
import fr.pioneers.forge.ForgeMetals;
import fr.pioneers.forge.HeatedMetal;
import fr.pioneers.industry.IndustryEvents;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.recipe.IndustrialRecipe;
import fr.pioneers.industry.recipe.IndustryRecipes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.2-beta (lot 2) : UNITE du foyer de forge (API Machine, interface v2 ; code de l'ancienne {@link ForgeHearthTile}, qui ne fait plus que la
 * porter : bloc, allumage, gestes a la main, entonnoirs, rendu). Energie motrice : le feu de la braise (charbon de bois ou de terre, tag
 * {@code minecraft:coals}) : {@link ForgeHearthTile#BASE_C} plus l'air des soufflets ({@link ForgeHeat#BELLOWS_MAX_C} au plus) ; une piece a
 * la fois chauffe dans la braise jusqu'a la temperature du foyer, sans jamais fondre ({@link ForgeHearthTile#MELT_MARGIN_C} en dessous).
 * <ul>
 * <li>notes de Guillaume : « une interface au foyer de forge afin d'ajuster le nombre de lingots et de charbon mis dedans » : deux CURSEURS
 * ({@link #SETTING_INGOTS} 1..16, {@link #SETTING_FUEL} 1..64) bornent ce que la case des lingots et celle du charbon gardent : un clic droit
 * avec une pile, un entonnoir n'y mettent pas davantage ; les cases se remplissent et se vident aussi dans l'interface ;</li>
 * <li>la forge au charbon est retiree : le foyer chauffe aussi le LOPIN D'ACIER (recettes industrielles de la forge au tier 0 dont l'entree
 * n'est pas un lingot : loupe d'acier -> lopin incandescent, a 1 100 degres pendant la duree de la recette ; evenement
 * {@code industry_coal_forge} de l'ancien progres « Au rouge »).</li>
 * </ul>
 * Sauvegarde au format d'avant : {@code slots} (tile : lingots, charbon, piece), {@code temp}, {@code air}, {@code burn}, {@code burnMax},
 * {@code announced} ; nouveau : {@code ingotLimit}, {@code fuelLimit}, {@code bloom}. Client : {@code temp}, {@code hot}, {@code input},
 * {@code fuel}, limites, avancement du lopin.
 */
public class HearthUnit extends MachineUnit {

    /** Reglages (curseurs) : lingots gardes, charbon garde. */
    public static final int SETTING_INGOTS = 0, SETTING_FUEL = 1;
    public static final int MAX_INGOTS = 16, MAX_FUEL = 64;
    /** Constante de temps du foyer qui monte (ticks) ; il retombe deux fois plus lentement. */
    private static final float WARM_TAU = 200F, COOL_TAU = 400F;
    /** Constante de temps de la chauffe d'un lingot dans la braise (ticks, x racine cubique des lingots). */
    private static final float PIECE_TAU = 120F;
    private static final int PIECE_STEP = 5;

    private final ForgeHearthTile tile;
    final ItemSlot input, fuel, hot;
    private float temp = ForgeMetals.AMBIENT_C;
    private float air;
    private int burn, burnMax;
    private boolean announced;
    private int ingotLimit = MAX_INGOTS, fuelLimit = MAX_FUEL;
    /** Lopin d'acier dans la braise : ticks passes a la temperature de sa recette. */
    private int bloom;
    // Client
    private int clientFuel;
    private float clientBloom;
    private String waitingMetal = "";

    public HearthUnit(UnitHost machine, ForgeHearthTile tile) {
        super(machine);
        this.tile = tile;
        input = machine.slot(new ItemSlot(() -> ingotLimit, this::isRawMetal, (s, a) -> true, (s, a) -> !a.external(), machine::changed,
              ForgeHearthTile.INPUT_X, ForgeHearthTile.INPUT_Y));
        fuel = machine.slot(new ItemSlot(() -> fuelLimit, ForgeHearthTile::isFuel, (s, a) -> true, (s, a) -> !a.external(), machine::changed,
              ForgeHearthTile.FUEL_X, ForgeHearthTile.FUEL_Y));
        hot = machine.slot(new ItemSlot(() -> 1, s -> HeatedMetal.is(s) || bloomRecipe(level(), s) != null || isBloomResult(s), (s, a) -> !a.external(),
              (s, a) -> !a.external() || level() != null && (HeatedMetal.isWorkable(s, level()) || isBloomResult(s)), machine::changed,
              ForgeHearthTile.HOT_X, ForgeHearthTile.HOT_Y));
    }

    // ------------------------------------------------------------------
    // Objets acceptes
    // ------------------------------------------------------------------

    /** Lingot (ou metal chauffe), ou loupe d'acier a chauffer en lopin. */
    boolean isRawMetal(ItemStack stack) {
        return ForgeHearthTile.isRawMetal(stack) || bloomRecipe(level(), stack) != null;
    }

    /**
     * 1.0.4-beta (lot 14, fusion des machines) : le marteau-pilon industriel (ex-forge, dont la forge au charbon etait le « compact » de l'age
     * du bronze) est absorbe par le Marteau-pilon ; ses recettes passent au Marteau-pilon (famille « forge », reservees a son multibloc). Le
     * foyer garde ses deux recettes de lopin (chauffe d'une loupe ou d'un lingot d'acier en lopin chaud), memes ids qu'avant.
     */
    static final java.util.Set<String> HEARTH_RECIPES = java.util.Set.of("industry/forge/steel_bloom", "industry/forge/steel_bloom_cold");

    /** Recette de lopin du foyer (famille « forge » du Marteau-pilon, tier 0, une entree : {@link #HEARTH_RECIPES}). */
    public static boolean hearthRecipe(IndustrialRecipe r) {
        return r.tier() == 0 && "forge".equals(r.family()) && r.items().size() == 1 && !r.results().isEmpty()
              && HEARTH_RECIPES.contains(r.getId().getPath());
    }

    /**
     * Recette de lopin que le foyer fait pour cet objet : une entree qui n'est PAS un lingot (le lingot se chauffe pour l'enclume) ; null sinon.
     */
    @Nullable
    static IndustrialRecipe bloomRecipe(@Nullable Level level, ItemStack stack) {
        if (level == null || stack.isEmpty() || ForgeMetals.isIngot(stack) || HeatedMetal.is(stack)) {
            return null;
        }
        for (IndustrialRecipe r : IndustryRecipes.of(level, IndustryMachine.POWER_HAMMER)) {
            if (hearthRecipe(r) && r.items().get(0).ingredient().test(stack)) {
                return r;
            }
        }
        return null;
    }

    /** Vrai si l'objet est le produit d'une recette de lopin (il sort par le dessous). */
    boolean isBloomResult(ItemStack stack) {
        Level level = level();
        if (level == null || stack.isEmpty()) {
            return false;
        }
        for (IndustrialRecipe r : IndustryRecipes.of(level, IndustryMachine.POWER_HAMMER)) {
            if (hearthRecipe(r) && ItemStack.isSameItem(r.results().get(0).stack(), stack)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Etat
    // ------------------------------------------------------------------

    public float temperature() {
        return temp;
    }

    public float air() {
        return air;
    }

    public float burnRatio() {
        return burnMax <= 0 ? 0 : burn / (float) burnMax;
    }

    public int ingotLimit() {
        return ingotLimit;
    }

    public int fuelLimit() {
        return fuelLimit;
    }

    public String waitingMetal() {
        return waitingMetal;
    }

    public int fuelCount() {
        Level level = level();
        return level != null && level.isClientSide ? clientFuel : fuel.getCount();
    }

    /** Avancement de la chauffe du lopin (0..1 ; client compris). */
    public float bloomProgress() {
        Level level = level();
        if (level != null && level.isClientSide) {
            return clientBloom;
        }
        IndustrialRecipe r = bloomRecipe(level, hot.getStack());
        return r == null ? 0 : Math.min(1, bloom / (float) Math.max(1, r.time()));
    }

    void blast(double strength) {
        air = Math.min(ForgeHeat.BELLOWS_MAX_C, air + (float) Math.max(0, strength) * 25F);
    }

    void startBurn(int time) {
        burn = burnMax = time;
    }

    boolean burning() {
        return burn > 0;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        boolean lit = tile.isLit();
        if (lit && burn <= 0) {
            ItemStack f = fuel.getStack();
            int time = f.isEmpty() ? 0 : ForgeHooks.getBurnTime(f, null);
            if (time > 0) {
                burn = burnMax = time;
                ItemStack rest = f.getCraftingRemainingItem();
                ItemStack left = f.copy();
                left.shrink(1);
                fuel.setStack(left.isEmpty() ? rest : left);
            } else {
                tile.setLit(false);
                lit = false;
            }
        }
        if (lit) {
            //L'air attise la braise : elle brule plus vite
            burn -= air > 100 ? 2 : 1;
        }
        float target = lit ? ForgeHearthTile.BASE_C + air : ForgeMetals.AMBIENT_C;
        float before = temp;
        temp += (target - temp) / (target > temp ? WARM_TAU : COOL_TAU);
        air = air < 1 ? 0 : air * 0.96F;
        if (Math.abs(temp - before) > 0.01F && level.getGameTime() % 20 == 0) {
            machine.markSync();
            machine.changed();
        }
        //Une piece entre dans la braise des qu'il y a de la place
        if (hot.isEmpty() && !input.isEmpty()) {
            ItemStack one = input.getStack().copyWithCount(1);
            ItemStack heated = HeatedMetal.is(one) || bloomRecipe(level, one) != null ? one : HeatedMetal.from(one, ForgeMetals.AMBIENT_C, level);
            if (!heated.isEmpty()) {
                input.extract(1, Action.EXECUTE, Automation.INTERNAL);
                hot.setStack(heated);
                bloom = 0;
            }
        }
        ItemStack piece = hot.getStack();
        if (lit && !piece.isEmpty() && (level.getGameTime() + pos().asLong()) % PIECE_STEP == 0) {
            if (HeatedMetal.is(piece)) {
                heatPiece(level, piece);
            } else {
                heatBloom(level, piece);
            }
        }
    }

    private void heatPiece(ServerLevel level, ItemStack piece) {
        String metal = HeatedMetal.metal(piece);
        float now = HeatedMetal.temperatureNow(piece, level);
        float goal = ForgeHearthTile.pieceTarget(temp, metal);
        if (now >= goal - 0.5F) {
            return;
        }
        float tau = PIECE_TAU * (float) Math.cbrt(Math.max(1, HeatedMetal.amount(piece) / (double) ForgeMetals.INGOT_MB));
        float next = goal - (goal - now) * (float) Math.exp(-PIECE_STEP / tau);
        boolean wasWorkable = now >= ForgeMetals.workingC(metal);
        HeatedMetal.heat(piece, next, level);
        machine.changed();
        machine.markSync();
        if (!wasWorkable && next >= ForgeMetals.workingC(metal) && !announced) {
            announced = true;
            BronzeEvents.award(level, pos(), "forge_hearth_heated");
        }
    }

    /** Lopin : a la temperature de sa recette pendant sa duree, la loupe devient le produit (lopin incandescent). */
    private void heatBloom(ServerLevel level, ItemStack piece) {
        IndustrialRecipe r = bloomRecipe(level, piece);
        if (r == null || temp < r.temperature()) {
            return;
        }
        bloom += PIECE_STEP;
        machine.markSync();
        if (bloom >= r.time()) {
            bloom = 0;
            hot.setStack(r.results().get(0).stack().copy());
            level.playSound(null, pos(), SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5F, 0.7F);
            IndustryEvents.award(level, pos(), "industry_coal_forge");
            machine.changed();
        }
    }

    // ------------------------------------------------------------------
    // Curseurs
    // ------------------------------------------------------------------

    @Override
    public void setting(int setting, int value) {
        if (setting == SETTING_INGOTS) {
            ingotLimit = Math.max(1, Math.min(MAX_INGOTS, value));
        } else if (setting == SETTING_FUEL) {
            fuelLimit = Math.max(1, Math.min(MAX_FUEL, value));
        } else {
            return;
        }
        machine.markSync();
        machine.changed();
    }

    // ------------------------------------------------------------------
    // Interface : valeurs nommees
    // ------------------------------------------------------------------

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.HEAT, () -> Math.round(temp * 10)).add(UiValues.BLAST, () -> Math.round(Math.min(1, air / ForgeHeat.BELLOWS_MAX_C) * 1000))
              .add(UiValues.BURN, () -> Math.round(burnRatio() * 1000)).add(UiValues.INGOT_LIMIT, () -> ingotLimit)
              .add(UiValues.FUEL_LIMIT, () -> fuelLimit).add(UiValues.PIECE_HEAT, this::pieceHeatX10)
              .add(UiValues.PIECE_WORK, this::pieceWork).add(UiValues.BLOOM, () -> Math.round(bloomProgress() * 1000));
    }

    private long pieceHeatX10() {
        ItemStack piece = hot.getStack();
        Level level = level();
        if (piece.isEmpty() || level == null) {
            return -1;
        }
        return Math.round((HeatedMetal.is(piece) ? HeatedMetal.temperatureNow(piece, level) : temp) * 10);
    }

    private long pieceWork() {
        ItemStack piece = hot.getStack();
        if (HeatedMetal.is(piece)) {
            return ForgeMetals.workingC(HeatedMetal.metal(piece));
        }
        IndustrialRecipe r = bloomRecipe(level(), piece);
        return r == null ? 0 : r.temperature();
    }

    // ------------------------------------------------------------------
    // Client, sauvegarde
    // ------------------------------------------------------------------

    @Override
    public void writeClient(CompoundTag tag) {
        tag.putFloat("temp", temp);
        tag.put("hot", hot.save());
        tag.put("input", input.save());
        tag.putInt("fuel", fuel.getCount());
        tag.putInt("ingotLimit", ingotLimit);
        tag.putInt("fuelLimit", fuelLimit);
        tag.putFloat("bloomP", bloomProgress());
    }

    @Override
    public void readClient(CompoundTag tag) {
        temp = tag.getFloat("temp");
        hot.load(tag.getCompound("hot"));
        input.load(tag.getCompound("input"));
        clientFuel = tag.getInt("fuel");
        ingotLimit = tag.contains("ingotLimit") ? tag.getInt("ingotLimit") : MAX_INGOTS;
        fuelLimit = tag.contains("fuelLimit") ? tag.getInt("fuelLimit") : MAX_FUEL;
        clientBloom = tag.getFloat("bloomP");
        ItemStack w = input.getStack();
        var melt = w.isEmpty() ? null : ForgeMetals.meltOf(w);
        waitingMetal = melt == null ? "" : melt.metal();
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putFloat("temp", temp);
        tag.putFloat("air", air);
        tag.putInt("burn", burn);
        tag.putInt("burnMax", burnMax);
        tag.putBoolean("announced", announced);
        tag.putInt("ingotLimit", ingotLimit);
        tag.putInt("fuelLimit", fuelLimit);
        tag.putInt("bloom", bloom);
    }

    @Override
    public void load(CompoundTag tag) {
        temp = tag.contains("temp") ? tag.getFloat("temp") : ForgeMetals.AMBIENT_C;
        air = tag.getFloat("air");
        burn = tag.getInt("burn");
        burnMax = tag.getInt("burnMax");
        announced = tag.getBoolean("announced");
        ingotLimit = tag.contains("ingotLimit") ? Math.max(1, Math.min(MAX_INGOTS, tag.getInt("ingotLimit"))) : MAX_INGOTS;
        fuelLimit = tag.contains("fuelLimit") ? Math.max(1, Math.min(MAX_FUEL, tag.getInt("fuelLimit"))) : MAX_FUEL;
        bloom = tag.getInt("bloom");
    }

    /** Charbon (tag {@code minecraft:coals}) : la braise du foyer. */
    static boolean coal(ItemStack stack) {
        return stack.is(ItemTags.COALS);
    }
}

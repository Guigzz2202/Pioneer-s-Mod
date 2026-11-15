package fr.pioneers.tech.industry.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.tech.bronze.bench.BenchRecipe.Counted;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.core.registries.PiIndustry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Recette de l'age industriel (un « process ») : machine, tier minimal, duree (ticks au tier minimal, dans une machine d'atelier),
 * objets / fluides / gaz consommes, objets (avec chance) / fluides / gaz produits, energie produite (generateurs, FE/t au tier basique),
 * et regles : {@code industrial} (multibloc seulement), {@code pure} (entrees pures exigees), {@code impure_only} (la 1re entree doit etre
 * impure : affinage), {@code impurity} (purete du produit principal au tier minimal), {@code temperature} (degres C, forge au charbon),
 * {@code pressure} (bar de procede, fournis par un port de pression ; sinon 50 % de la vitesse) et {@code air} (litres consommes par operation).
 * <pre>{"type":"pioneers:industrial","machine":"roaster","tier":0,"time":200,
 *  "items":[{"ingredient":{"item":"pioneers:chalcopyrite"},"count":1}], "fluids":[{"fluid":"minecraft:water","amount":250}],
 *  "gases":[{"gas":"pioneers:oxygen","amount":100}], "results":[{"item":"pioneers:copper_calcine","count":1,"chance":1.0}],
 *  "fluid_results":[{"fluid":"...","amount":100}], "gas_results":[{"gas":"pioneers:sulfur_dioxide","amount":100}], "energy":0}</pre>
 */
public class IndustrialRecipe implements Recipe<Container> {

    /** Fluide consomme : un fluide precis ou un tag de fluides. */
    public record FluidIn(@Nullable Fluid fluid, @Nullable TagKey<Fluid> tag, int amount) {

        public boolean test(FluidStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            return fluid != null ? stack.getFluid().isSame(fluid) : tag != null && stack.getFluid().is(tag);
        }

        /** Fluides possibles (JEI). */
        public List<FluidStack> displayed() {
            List<FluidStack> out = new ArrayList<>();
            if (fluid != null) {
                out.add(new FluidStack(fluid, amount));
            } else if (tag != null) {
                for (Fluid f : ForgeRegistries.FLUIDS.getValues()) {
                    if (f.is(tag) && f.isSource(f.defaultFluidState())) {
                        out.add(new FluidStack(f, amount));
                    }
                }
            }
            return out;
        }

        static FluidIn fromJson(JsonObject o) {
            int amount = GsonHelper.getAsInt(o, "amount", 1000);
            if (o.has("tag")) {
                return new FluidIn(null, TagKey.create(Registries.FLUID, ResourceLocation.parse(GsonHelper.getAsString(o, "tag"))), amount);
            }
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(ResourceLocation.parse(GsonHelper.getAsString(o, "fluid")));
            if (fluid == null || fluid == Fluids.EMPTY) {
                throw new JsonParseException("Unknown fluid " + GsonHelper.getAsString(o, "fluid"));
            }
            return new FluidIn(fluid, null, amount);
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(tag != null);
            if (tag != null) {
                buffer.writeResourceLocation(tag.location());
            } else {
                buffer.writeResourceLocation(ForgeRegistries.FLUIDS.getKey(fluid));
            }
            buffer.writeVarInt(amount);
        }

        static FluidIn read(FriendlyByteBuf buffer) {
            if (buffer.readBoolean()) {
                return new FluidIn(null, TagKey.create(Registries.FLUID, buffer.readResourceLocation()), buffer.readVarInt());
            }
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(buffer.readResourceLocation());
            return new FluidIn(fluid, null, buffer.readVarInt());
        }
    }

    /** Gaz consomme ou produit (par son id : les gaz etrangers marchent aussi). */
    public record GasAmount(ResourceLocation gas, long amount) {

        public boolean test(GasStack stack) {
            return !stack.isEmpty() && gas.equals(stack.getGas().getRegistryName());
        }

        public GasStack stack() {
            Gas g = PioneersAPI.gas(gas);
            return g == null || g.isEmptyType() ? GasStack.EMPTY : GasStack.of(g, amount);
        }

        static GasAmount fromJson(JsonObject o) {
            return new GasAmount(ResourceLocation.parse(GsonHelper.getAsString(o, "gas")), GsonHelper.getAsLong(o, "amount", 100));
        }
    }

    /** Objet produit, avec sa chance (sous-produits). */
    public record Result(ItemStack stack, float chance) {}

    /** 1.9 (MAJ RP) : un lot du tirage pondere (recycleur) : objet, poids, quantite min..max. */
    public record Draw(ItemStack item, int weight, int min, int max) {}

    /**
     * 1.9 (MAJ RP) : tirage pondere un-parmi-N (recycleur) : {@code rolls} tirages par operation (min..max, multiplies par le facteur du
     * proprietaire), chacun choisit un lot du {@code pool} selon les poids. JSON : {@code "draws": {"rolls": [2, 3], "pool": [{"item": ..., "weight": 12,
     * "count": [1, 3]}]}}.
     */
    public record Draws(int min, int max, List<Draw> pool) {

        public int total() {
            int t = 0;
            for (Draw d : pool) {
                t += d.weight();
            }
            return t;
        }

        public ItemStack roll(net.minecraft.util.RandomSource random) {
            int pick = random.nextInt(Math.max(1, total()));
            for (Draw d : pool) {
                pick -= d.weight();
                if (pick < 0) {
                    return d.item().copyWithCount(d.min() + random.nextInt(Math.max(1, d.max() - d.min() + 1)));
                }
            }
            return ItemStack.EMPTY;
        }
    }

    private final ResourceLocation id;
    private final IndustryMachine machine;
    private final int tier, time;
    private final List<Counted> items;
    private final List<FluidIn> fluids;
    private final List<GasAmount> gases;
    private final List<Result> results;
    private final List<FluidStack> fluidResults;
    private final List<GasAmount> gasResults;
    private final long energy;
    private final boolean industrialOnly, pure, impureOnly;
    private final int impurity, temperature;
    private final boolean ore;
    /** Pression de procede (bar, 0 : aucune) et air comprime consomme par operation (L). Sans cette pression : 50 % de la vitesse. */
    private final double pressure;
    private final int air;
    /** 0.20.34 : donnees consommees par operation (synthetiseur : planetes simulees), arrivees par les cables de donnees du port de donnees. */
    private List<DataStack> data = List.of();
    @javax.annotation.Nullable
    private Draws draws;
    /**
     * 1.0.3-beta (lot 3) : rendement du PRODUIT PRINCIPAL selon le tier de la machine qui la fait (MKI, MK2, MK3, MK4 ; champ JSON
     * {@code tier_yield}) : la fonte du minerai au creuset electrique (x1,5 / x2 / x2,5 / x3), la lixiviation acide. Applique par
     * {@link RecipeMatch} au premier objet produit, sinon au premier fluide ; {@code null} : x1 partout.
     */
    @javax.annotation.Nullable
    private double[] tierYield;
    /**
     * 1.0.4-beta (lot 11, fusion des machines) : FAMILLE de la recette = cle de la machine absorbee dont elle vient (champ JSON {@code family},
     * ecrit par tools/gc/industry/fusion.py) ; JEI la montre (pastille, tri), l'XP du proprietaire la suit. Null : recette propre a la machine.
     */
    @javax.annotation.Nullable
    private String family;
    /**
     * 1.0.5-beta (lot 38, specification de l'age du bronze §2) : recette BASSE convertie (mecanique m = 0 ou a vapeur m = 1 de la famille de
     * tiers de la machine, {@link IndustryRecipes}) : son tier minimal global m, -1 pour une recette ordinaire. Duree deja divisee par
     * 2^(2 - m) (tier 0 = MKI) ; rendement par tier ({@link #tierYield}) sur TOUTES les sorties objets et leurs chances ({@link #yieldAll}) ;
     * place souple des tirages ({@link RecipeMatch}). Jamais montree dans les pages JEI industrielles (pages de sa famille basse).
     */
    private int lowTier = -1;
    private boolean yieldAll;
    /** 1.0.5-beta (lot 44) : outil remanent de la recette dans sa machine ({@link Remanents}), calcule au premier usage. */
    @javax.annotation.Nullable
    private Remanents.Tool tool;
    private boolean toolKnown;

    public IndustrialRecipe(ResourceLocation id, IndustryMachine machine, int tier, int time, List<Counted> items, List<FluidIn> fluids, List<GasAmount> gases,
          List<Result> results, List<FluidStack> fluidResults, List<GasAmount> gasResults, long energy, boolean industrialOnly, boolean pure, boolean impureOnly,
          int impurity, int temperature, boolean ore) {
        this(id, machine, tier, time, items, fluids, gases, results, fluidResults, gasResults, energy, industrialOnly, pure, impureOnly, impurity, temperature,
              ore, 0, 0);
    }

    public IndustrialRecipe(ResourceLocation id, IndustryMachine machine, int tier, int time, List<Counted> items, List<FluidIn> fluids, List<GasAmount> gases,
          List<Result> results, List<FluidStack> fluidResults, List<GasAmount> gasResults, long energy, boolean industrialOnly, boolean pure, boolean impureOnly,
          int impurity, int temperature, boolean ore, double pressure, int air) {
        this.pressure = Math.max(0, pressure);
        this.air = Math.max(0, air);
        this.ore = ore;
        this.id = id;
        this.machine = machine;
        this.tier = Math.max(0, Math.min(3, tier));
        this.time = Math.max(1, time);
        this.items = List.copyOf(items);
        this.fluids = List.copyOf(fluids);
        this.gases = List.copyOf(gases);
        this.results = List.copyOf(results);
        this.fluidResults = List.copyOf(fluidResults);
        this.gasResults = List.copyOf(gasResults);
        this.energy = energy;
        this.industrialOnly = industrialOnly;
        this.pure = pure;
        this.impureOnly = impureOnly;
        this.impurity = impurity;
        this.temperature = temperature;
    }

    public IndustryMachine machine() {
        return machine;
    }

    public int tier() {
        return tier;
    }

    public int time() {
        return time;
    }

    public List<Counted> items() {
        return items;
    }

    public List<FluidIn> fluids() {
        return fluids;
    }

    public List<GasAmount> gases() {
        return gases;
    }

    public List<Result> results() {
        return results;
    }

    public List<FluidStack> fluidResults() {
        return fluidResults;
    }

    public List<GasAmount> gasResults() {
        return gasResults;
    }

    /** Generateurs : FE/t produits pendant l'operation (tier basique). */
    public long energy() {
        return energy;
    }

    /** Multibloc seulement : recette marquee ainsi, ou sous pression de procede (seuls les multiblocs ont des ports de pression). */
    public boolean industrialOnly() {
        return industrialOnly || pressure > 0;
    }

    public boolean pure() {
        return pure;
    }

    public boolean impureOnly() {
        return impureOnly;
    }

    public int impurity() {
        return impurity;
    }

    public int temperature() {
        return temperature;
    }

    /** Pression de procede exigee (bar) ; 0 : recette sans pression. */
    public double pressure() {
        return pressure;
    }

    /** Air comprime consomme par operation quand la pression est fournie (L). */
    public int air() {
        return air;
    }

    /** 0.20.34 : donnees consommees par operation (Mo). */
    public List<DataStack> data() {
        return data;
    }

    @javax.annotation.Nullable
    public Draws draws() {
        return draws;
    }

    IndustrialRecipe withDraws(@javax.annotation.Nullable Draws d) {
        this.draws = d == null || d.pool().isEmpty() ? null : d;
        return this;
    }

    IndustrialRecipe withData(List<DataStack> list) {
        data = List.copyOf(list);
        return this;
    }

    IndustrialRecipe withTierYield(@javax.annotation.Nullable double[] y) {
        this.tierYield = y == null || y.length == 0 ? null : java.util.Arrays.copyOf(y, 4);
        if (this.tierYield != null) {
            for (int i = y.length; i < 4; i++) {
                this.tierYield[i] = y[y.length - 1];
            }
        }
        return this;
    }

    IndustrialRecipe withFamily(@javax.annotation.Nullable String f) {
        family = f == null || f.isEmpty() ? null : f;
        return this;
    }

    IndustrialRecipe withLow(int minTier, boolean all) {
        lowTier = minTier;
        yieldAll = all;
        return this;
    }

    /**
     * 1.0.5-beta (lot 44, retour 62) : OUTIL remanent de la recette (matrice, forme d'extrusion, catalyseur... : lu dans la case Outil de sa
     * machine), ou null. Calcule une fois (les registres et les tags sont prets quand une machine ou JEI le demande).
     */
    @javax.annotation.Nullable
    public Remanents.Tool tool() {
        if (!toolKnown) {
            tool = Remanents.find(this);
            toolKnown = true;
        }
        return tool;
    }

    /** 1.0.5-beta (lot 38) : tier minimal global de la recette basse convertie, ou -1 (recette ordinaire). */
    public int lowTier() {
        return lowTier;
    }

    /** 1.0.5-beta (lot 38) : recette basse convertie (mecanique ou a vapeur) ? */
    public boolean converted() {
        return lowTier >= 0;
    }

    /** 1.0.5-beta (lot 38) : le rendement par tier porte sur toutes les sorties objets (et leurs chances), pas seulement la premiere. */
    public boolean yieldAll() {
        return yieldAll;
    }

    /** 1.0.4-beta (lot 11) : famille (cle de la machine absorbee d'origine), ou null. */
    @javax.annotation.Nullable
    public String family() {
        return family;
    }

    /** 1.0.3-beta (lot 3) : la recette a un rendement par tier ({@code tier_yield}). */
    public boolean hasTierYield() {
        return tierYield != null;
    }

    /**
     * 1.0.5-beta (lot 39) : le rendement par tier porte sur le FLUIDE produit (fonte du minerai au creuset) quand les seuls objets produits sont
     * des sous-produits tires au sort (laitier a 10 %) : jamais sur le laitier.
     */
    public boolean yieldOnFluid() {
        if (tierYield == null || fluidResults.isEmpty() || results.isEmpty()) {
            return false;
        }
        for (Result r : results) {
            if (r.chance() >= 1F) {
                return false;
            }
        }
        return true;
    }

    /** 1.0.3-beta (lot 3) : multiplicateur du produit principal pour une machine de ce tier (0 MKI .. 3 MK4) ; 1 sans {@code tier_yield}. */
    public double tierYield(int machineTier) {
        return tierYield == null ? 1 : tierYield[Math.max(0, Math.min(3, machineTier))];
    }

    /** Quantite du produit principal fluide (mB) a ce tier, pour une operation (JEI, guide, GameTests). */
    public int yieldedFluid(int machineTier) {
        return fluidResults.isEmpty() ? 0 : (int) Math.floor(fluidResults.get(0).getAmount() * tierYield(machineTier) + 1e-6);
    }

    /** Traitement de minerai : le produit principal profite du rendement du tier (x1 basique, x1,25, x1,5, x2 ultime). */
    public boolean ore() {
        return ore;
    }

    @Override
    public boolean matches(@NotNull Container container, @NotNull Level level) {
        return false;
    }

    @NotNull
    @Override
    public ItemStack assemble(@NotNull Container container, @NotNull RegistryAccess access) {
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0).stack().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @NotNull
    @Override
    public ItemStack getResultItem(@NotNull RegistryAccess access) {
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0).stack();
    }

    @NotNull
    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        items.forEach(c -> list.add(c.ingredient()));
        return list;
    }

    @NotNull
    @Override
    public ResourceLocation getId() {
        return id;
    }

    @NotNull
    @Override
    public RecipeSerializer<?> getSerializer() {
        return PiIndustry.INDUSTRIAL_SERIALIZER.get();
    }

    @NotNull
    @Override
    public RecipeType<?> getType() {
        return PiIndustry.INDUSTRIAL.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static class Serializer implements RecipeSerializer<IndustrialRecipe> {

        @NotNull
        @Override
        public IndustrialRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
            IndustryMachine machine = IndustryMachine.byKey(GsonHelper.getAsString(json, "machine"));
            if (machine == null) {
                throw new JsonParseException("Unknown industrial machine " + GsonHelper.getAsString(json, "machine"));
            }
            List<Counted> items = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "items", new com.google.gson.JsonArray())) {
                JsonObject o = e.getAsJsonObject();
                items.add(new Counted(Ingredient.fromJson(GsonHelper.getNonNull(o, "ingredient")), GsonHelper.getAsInt(o, "count", 1)));
            }
            List<FluidIn> fluids = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "fluids", new com.google.gson.JsonArray())) {
                fluids.add(FluidIn.fromJson(e.getAsJsonObject()));
            }
            List<GasAmount> gases = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "gases", new com.google.gson.JsonArray())) {
                gases.add(GasAmount.fromJson(e.getAsJsonObject()));
            }
            List<Result> results = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "results", new com.google.gson.JsonArray())) {
                JsonObject o = e.getAsJsonObject();
                results.add(new Result(ShapedRecipe.itemStackFromJson(o), GsonHelper.getAsFloat(o, "chance", 1F)));
            }
            List<FluidStack> fluidResults = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "fluid_results", new com.google.gson.JsonArray())) {
                JsonObject o = e.getAsJsonObject();
                Fluid fluid = ForgeRegistries.FLUIDS.getValue(ResourceLocation.parse(GsonHelper.getAsString(o, "fluid")));
                if (fluid == null || fluid == Fluids.EMPTY) {
                    throw new JsonParseException("Unknown fluid " + GsonHelper.getAsString(o, "fluid"));
                }
                fluidResults.add(new FluidStack(fluid, GsonHelper.getAsInt(o, "amount", 1000)));
            }
            List<GasAmount> gasResults = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "gas_results", new com.google.gson.JsonArray())) {
                gasResults.add(GasAmount.fromJson(e.getAsJsonObject()));
            }
            List<DataStack> data = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "data", new com.google.gson.JsonArray())) {
                JsonObject o = e.getAsJsonObject();
                ResourceLocation typeId = ResourceLocation.parse(GsonHelper.getAsString(o, "type"));
                DataType type = PioneersAPI.dataTypeRegistry().getValue(typeId);
                if (type == null || type == DataType.EMPTY) {
                    throw new JsonParseException("Unknown data type " + typeId);
                }
                data.add(DataStack.of(type, Math.max(1, GsonHelper.getAsLong(o, "amount"))));
            }
            return new IndustrialRecipe(id, machine, GsonHelper.getAsInt(json, "tier", 0), GsonHelper.getAsInt(json, "time", 200), items, fluids, gases, results,
                  fluidResults, gasResults, GsonHelper.getAsLong(json, "energy", 0), GsonHelper.getAsBoolean(json, "industrial", false),
                  GsonHelper.getAsBoolean(json, "pure", false), GsonHelper.getAsBoolean(json, "impure_only", false), GsonHelper.getAsInt(json, "impurity", 0),
                  GsonHelper.getAsInt(json, "temperature", 0), GsonHelper.getAsBoolean(json, "ore", false), GsonHelper.getAsDouble(json, "pressure", 0),
                  GsonHelper.getAsInt(json, "air", 0)).withData(data).withDraws(drawsFromJson(json)).withTierYield(tierYieldFromJson(json))
                  .withFamily(GsonHelper.getAsString(json, "family", null));
        }

        @javax.annotation.Nullable
        private static double[] tierYieldFromJson(JsonObject json) {
            if (!json.has("tier_yield")) {
                return null;
            }
            com.google.gson.JsonArray a = GsonHelper.getAsJsonArray(json, "tier_yield");
            double[] y = new double[a.size()];
            for (int i = 0; i < y.length; i++) {
                y[i] = Math.max(0, a.get(i).getAsDouble());
            }
            return y;
        }

        @javax.annotation.Nullable
        private static Draws drawsFromJson(JsonObject json) {
            if (!json.has("draws")) {
                return null;
            }
            JsonObject d = GsonHelper.getAsJsonObject(json, "draws");
            com.google.gson.JsonArray rolls = GsonHelper.getAsJsonArray(d, "rolls");
            List<Draw> pool = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(d, "pool")) {
                JsonObject o = e.getAsJsonObject();
                //1.0.3-beta (correctif) : « count » est une fourchette [min, max] ; itemStackFromJson le lisait comme un entier et les
                //24 recettes du recycleur des ruines ne se chargeaient pas
                JsonObject one = o.deepCopy();
                one.remove("count");
                ItemStack item = net.minecraft.world.item.crafting.ShapedRecipe.itemStackFromJson(one);
                com.google.gson.JsonArray c = GsonHelper.getAsJsonArray(o, "count");
                pool.add(new Draw(item.copyWithCount(1), Math.max(1, GsonHelper.getAsInt(o, "weight", 1)), c.get(0).getAsInt(), c.get(1).getAsInt()));
            }
            return new Draws(rolls.get(0).getAsInt(), rolls.get(1).getAsInt(), pool);
        }

        @Override
        public IndustrialRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buffer) {
            IndustryMachine machine = buffer.readEnum(IndustryMachine.class);
            int tier = buffer.readVarInt(), time = buffer.readVarInt();
            List<Counted> items = new ArrayList<>();
            for (int i = buffer.readVarInt(); i > 0; i--) {
                items.add(new Counted(Ingredient.fromNetwork(buffer), buffer.readVarInt()));
            }
            List<FluidIn> fluids = new ArrayList<>();
            for (int i = buffer.readVarInt(); i > 0; i--) {
                fluids.add(FluidIn.read(buffer));
            }
            List<GasAmount> gases = new ArrayList<>();
            for (int i = buffer.readVarInt(); i > 0; i--) {
                gases.add(new GasAmount(buffer.readResourceLocation(), buffer.readVarLong()));
            }
            List<Result> results = new ArrayList<>();
            for (int i = buffer.readVarInt(); i > 0; i--) {
                results.add(new Result(buffer.readItem(), buffer.readFloat()));
            }
            List<FluidStack> fluidResults = new ArrayList<>();
            for (int i = buffer.readVarInt(); i > 0; i--) {
                fluidResults.add(buffer.readFluidStack());
            }
            List<GasAmount> gasResults = new ArrayList<>();
            for (int i = buffer.readVarInt(); i > 0; i--) {
                gasResults.add(new GasAmount(buffer.readResourceLocation(), buffer.readVarLong()));
            }
            long energy = buffer.readVarLong();
            int flags = buffer.readByte();
            IndustrialRecipe r = new IndustrialRecipe(id, machine, tier, time, items, fluids, gases, results, fluidResults, gasResults, energy, (flags & 1) != 0,
                  (flags & 2) != 0, (flags & 4) != 0, buffer.readVarInt(), buffer.readVarInt(), (flags & 8) != 0, buffer.readDouble(), buffer.readVarInt());
            List<DataStack> data = new ArrayList<>();
            for (int i = buffer.readVarInt(); i > 0; i--) {
                data.add(DataStack.readFromPacket(buffer));
            }
            Draws draws = null;
            if (buffer.readBoolean()) {
                int min = buffer.readVarInt(), max = buffer.readVarInt();
                List<Draw> pool = new ArrayList<>();
                for (int i = buffer.readVarInt(); i > 0; i--) {
                    pool.add(new Draw(buffer.readItem(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
                }
                draws = new Draws(min, max, pool);
            }
            double[] yields = null;
            if (buffer.readBoolean()) {
                yields = new double[4];
                for (int i = 0; i < 4; i++) {
                    yields[i] = buffer.readDouble();
                }
            }
            String family = buffer.readBoolean() ? buffer.readUtf() : null;
            return r.withData(data).withDraws(draws).withTierYield(yields).withFamily(family);
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull IndustrialRecipe recipe) {
            buffer.writeEnum(recipe.machine);
            buffer.writeVarInt(recipe.tier);
            buffer.writeVarInt(recipe.time);
            buffer.writeVarInt(recipe.items.size());
            for (Counted c : recipe.items) {
                c.ingredient().toNetwork(buffer);
                buffer.writeVarInt(c.count());
            }
            buffer.writeVarInt(recipe.fluids.size());
            recipe.fluids.forEach(f -> f.write(buffer));
            buffer.writeVarInt(recipe.gases.size());
            for (GasAmount g : recipe.gases) {
                buffer.writeResourceLocation(g.gas());
                buffer.writeVarLong(g.amount());
            }
            buffer.writeVarInt(recipe.results.size());
            for (Result r : recipe.results) {
                buffer.writeItem(r.stack());
                buffer.writeFloat(r.chance());
            }
            buffer.writeVarInt(recipe.fluidResults.size());
            recipe.fluidResults.forEach(buffer::writeFluidStack);
            buffer.writeVarInt(recipe.gasResults.size());
            for (GasAmount g : recipe.gasResults) {
                buffer.writeResourceLocation(g.gas());
                buffer.writeVarLong(g.amount());
            }
            buffer.writeVarLong(recipe.energy);
            buffer.writeByte((recipe.industrialOnly ? 1 : 0) | (recipe.pure ? 2 : 0) | (recipe.impureOnly ? 4 : 0) | (recipe.ore ? 8 : 0));
            buffer.writeVarInt(recipe.impurity);
            buffer.writeVarInt(recipe.temperature);
            buffer.writeDouble(recipe.pressure);
            buffer.writeVarInt(recipe.air);
            buffer.writeVarInt(recipe.data.size());
            recipe.data.forEach(d -> d.writeToPacket(buffer));
            buffer.writeBoolean(recipe.draws != null);
            if (recipe.draws != null) {
                buffer.writeVarInt(recipe.draws.min());
                buffer.writeVarInt(recipe.draws.max());
                buffer.writeVarInt(recipe.draws.pool().size());
                for (Draw d : recipe.draws.pool()) {
                    buffer.writeItem(d.item());
                    buffer.writeVarInt(d.weight());
                    buffer.writeVarInt(d.min());
                    buffer.writeVarInt(d.max());
                }
            }
            buffer.writeBoolean(recipe.tierYield != null);
            if (recipe.tierYield != null) {
                for (double y : recipe.tierYield) {
                    buffer.writeDouble(y);
                }
            }
            buffer.writeBoolean(recipe.family != null);
            if (recipe.family != null) {
                buffer.writeUtf(recipe.family);
            }
        }
    }
}

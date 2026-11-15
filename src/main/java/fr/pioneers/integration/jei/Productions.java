package fr.pioneers.integration.jei;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.pioneers.Pioneers;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.tech.compute.AiChips;
import fr.pioneers.core.Texts;
import fr.pioneers.core.data.PiDataTypes;
import fr.pioneers.tech.energy.EnergyKind;
import fr.pioneers.tech.energy.MicrobialCellTile;
import fr.pioneers.tech.energy.PiEnergy;
import fr.pioneers.tech.industry.Extraction;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.atelier.DynamoTile;
import fr.pioneers.tech.kinetics.machines.DrillProducts;
import fr.pioneers.tech.kinetics.machines.MachineType;
import fr.pioneers.core.registries.PiIndustry;
import fr.pioneers.core.registries.PiItems;
import fr.pioneers.core.registries.PiKinetics;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.mining.DrillHead;
import fr.pioneers.space.mining.MiningMaterial;
import fr.pioneers.space.research.ProbeDestination;
import fr.pioneers.space.research.ResearchNode;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.10.2 (JEI) : les familles de PRODUCTIONS montrees par {@link ProductionCategory}.
 * <ul>
 *     <li>familles de donnees : {@code assets/pioneers/jei/productions.json}, ecrit par {@code tools/jei/jei_info.py} depuis les memes
 *     donnees que le jeu (tables de butin, modificateurs de butin, generation du monde, sources de la ferme, ruines) ;</li>
 *     <li>familles calculees ici, depuis le code du jeu : generateurs renouvelables ({@link EnergyKind}), perceuse et noria
 *     ({@link DrillProducts}), sondes ({@link ProbeDestination}), plans de recherche ({@link ResearchNode}) et modeles d'IA
 *     ({@link AiChips}).</li>
 * </ul>
 * Chaque famille a sa {@link RecipeType} {@code pioneers:prod_<famille>} et ses catalyseurs.
 */
final class Productions {

    private static final ResourceLocation FILE = Pioneers.rl("jei/productions.json");
    /** Familles calculees en Java (ordre d'affichage). */
    static final String RENEWABLE = "renewable", KINETIC = "kinetic", OUTPOST = "outpost", PROBES = "probes", RESEARCH = "research", WATER = "water",
          ROCKS = "rocks";

    private record Family(String id, String title, ItemLike icon, List<String> catalysts, RecipeType<ProductionCategory.Entry> type) {
    }

    private static final Map<String, Family> FAMILIES = new LinkedHashMap<>();
    private static JsonObject root = new JsonObject();

    private Productions() {
    }

    // ------------------------------------------------------------------ lecture du fichier

    private static JsonObject read() {
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(FILE);
            if (res.isPresent()) {
                try (Reader reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
        } catch (Exception e) {
            Pioneers.LOGGER.warn("JEI : lecture de {} impossible", FILE, e);
        }
        return new JsonObject();
    }

    private static Item item(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id));
        return item == null ? Items.AIR : item;
    }

    private static void family(String id, String title, ItemLike icon, List<String> catalysts) {
        FAMILIES.put(id, new Family(id, title, icon, catalysts, JeiOrder.create("prod_" + id, ProductionCategory.Entry.class)));
    }

    // ------------------------------------------------------------------ enregistrement

    static void categories(IRecipeCategoryRegistration registration, IGuiHelper helper) {
        FAMILIES.clear();
        root = read();
        if (root.has("families")) {
            for (JsonElement e : root.getAsJsonArray("families")) {
                JsonObject f = e.getAsJsonObject();
                List<String> catalysts = new ArrayList<>();
                if (f.has("catalysts")) {
                    f.getAsJsonArray("catalysts").forEach(c -> catalysts.add(c.getAsString()));
                }
                Item icon = item(f.get("icon").getAsString());
                family(f.get("id").getAsString(), f.get("title").getAsString(), icon == Items.AIR ? Items.BOOK : icon, catalysts);
            }
        }
        family(RENEWABLE, "jei.pioneers.prod.renewable", PiEnergy.SOLAR.get(0), List.of());
        family(KINETIC, "jei.pioneers.prod.kinetic", PiKinetics.machine(MachineType.DRILL), List.of());
        family(OUTPOST, "jei.pioneers.prod.outpost", item("pioneers:mining_outpost_controller"), List.of("pioneers:mining_outpost_controller",
              "pioneers:rover_chassis"));
        family(PROBES, "jei.pioneers.prod.probes", item("pioneers:data_antenna"), List.of("pioneers:launch_pad_core", "pioneers:data_antenna"));
        family(RESEARCH, "jei.pioneers.prod.research", item("pioneers:research_table"), List.of("pioneers:research_table", "pioneers:ai_lab"));
        //1.0.5-beta (lot 46, retour 72) : bloc a eau et generateur de roches (catalyseurs : leurs fiches, PiJeiPlugin.apiCatalysts)
        family(WATER, "jei.pioneers.prod.water", item("pioneers:basic_water_source"), List.of());
        family(ROCKS, "jei.pioneers.prod.rocks", item("pioneers:basic_rock_generator"), List.of());
        for (Family f : FAMILIES.values()) {
            registration.addRecipeCategories(new ProductionCategory(helper, f.type(), f.title(), f.icon()));
        }
    }

    static void recipes(IRecipeRegistration registration) {
        JsonObject entries = root.has("entries") ? root.getAsJsonObject("entries") : new JsonObject();
        for (Family f : FAMILIES.values()) {
            List<ProductionCategory.Entry> list = new ArrayList<>();
            if (entries.has(f.id())) {
                for (JsonElement e : entries.getAsJsonArray(f.id())) {
                    ProductionCategory.Entry entry = entry(e.getAsJsonObject());
                    if (entry != null) {
                        list.add(entry);
                    }
                }
            }
            switch (f.id()) {
                case RENEWABLE -> list.addAll(renewable());
                case KINETIC -> list.addAll(kinetic());
                case OUTPOST -> list.addAll(outpost());
                case PROBES -> list.addAll(probes());
                case RESEARCH -> list.addAll(research());
                case WATER -> list.addAll(water());
                case ROCKS -> list.addAll(rocks());
                default -> {
                }
            }
            registration.addRecipes(f.type(), list);
        }
    }

    static void catalysts(IRecipeCatalystRegistration registration) {
        for (Family f : FAMILIES.values()) {
            for (String id : f.catalysts()) {
                stacks(id).forEach(s -> registration.addRecipeCatalyst(s, f.type()));
            }
        }
        //API Machine (lot 1) : les generateurs renouvelables, la perceuse, la noria, la pompe et la dynamo sont catalyseurs par leur fiche
        //(PiJeiPlugin.apiCatalysts) ; restent ici les pales de l'eolienne (pieces, pas des machines)
        Family r = FAMILIES.get(RENEWABLE);
        if (r != null) {
            PiEnergy.ROTORS.forEach(b -> registration.addRecipeCatalyst(new ItemStack(b.getBlock()), r.type()));
        }
    }

    /** API Machine (lot 1) : type JEI d'une famille ({@code pioneers:prod_<famille>}), pour les catalyseurs tires des fiches ; null sinon. */
    @org.jetbrains.annotations.Nullable
    static RecipeType<?> type(String uid) {
        for (Family f : FAMILIES.values()) {
            if (JeiOrder.uid(f.type()).equals(uid)) {
                return f.type();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ entrees du fichier

    private static List<ItemStack> stacks(String id) {
        List<ItemStack> out = new ArrayList<>();
        if (id.startsWith("#")) {
            BuiltInRegistries.ITEM.getTagOrEmpty(net.minecraft.tags.ItemTags.create(ResourceLocation.parse(id.substring(1))))
                  .forEach(h -> out.add(new ItemStack(h.value())));
        } else {
            Item item = item(id);
            if (item != Items.AIR) {
                out.add(new ItemStack(item));
            }
        }
        return out;
    }

    private static String pct(double chance) {
        double p = chance * 100;
        return (p >= 10 || p == Math.floor(p) ? String.valueOf(Math.round(p)) : String.format(Locale.ROOT, "%.1f", p)) + "%";
    }

    /** Case d'entree ou de sortie : liste d'alternatives (objets, tags) ou objet {id|fluid|gas, amount, min, max, chance, tip}. */
    @org.jetbrains.annotations.Nullable
    private static ProductionCategory.Slot slot(JsonElement e) {
        if (e.isJsonArray()) {
            List<ItemStack> alts = new ArrayList<>();
            e.getAsJsonArray().forEach(id -> alts.addAll(stacks(id.getAsString())));
            return alts.isEmpty() ? null : ProductionCategory.Slot.items(alts);
        }
        JsonObject o = e.getAsJsonObject();
        ProductionCategory.Slot slot;
        if (o.has("fluid")) {
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(ResourceLocation.parse(o.get("fluid").getAsString()));
            if (fluid == null || fluid == Fluids.EMPTY) {
                return null;
            }
            slot = ProductionCategory.Slot.fluid(new FluidStack(fluid, o.has("amount") ? o.get("amount").getAsInt() : 1000));
        } else if (o.has("gas")) {
            Gas gas = PioneersAPI.gas(ResourceLocation.parse(o.get("gas").getAsString()));
            if (gas == null || gas.isEmptyType()) {
                return null;
            }
            slot = ProductionCategory.Slot.gas(GasStack.of(gas, o.has("amount") ? o.get("amount").getAsLong() : 1000));
        } else {
            List<ItemStack> alts = new ArrayList<>();
            JsonElement id = o.get("id");
            if (id.isJsonArray()) {
                id.getAsJsonArray().forEach(i -> alts.addAll(stacks(i.getAsString())));
            } else {
                alts.addAll(stacks(id.getAsString()));
            }
            if (alts.isEmpty()) {
                return null;
            }
            int max = o.has("max") ? o.get("max").getAsInt() : 1;
            alts.forEach(s -> s.setCount(Math.max(1, Math.min(64, max))));
            slot = ProductionCategory.Slot.items(alts);
        }
        int min = o.has("min") ? o.get("min").getAsInt() : -1, max = o.has("max") ? o.get("max").getAsInt() : -1;
        double chance = o.has("chance") ? o.get("chance").getAsDouble() : 1;
        List<Component> tip = new ArrayList<>();
        String note = null;
        if (min >= 0 && max > min) {
            note = min + "-" + max;
            tip.add(Component.translatable("jei.pioneers.prod.count", min, max).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (chance < 1) {
            note = pct(chance);
            tip.add(Component.translatable("jei.pioneers.prod.chance", pct(chance)).withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        if (o.has("tip")) {
            o.getAsJsonArray("tip").forEach(t -> tip.add(Component.empty().append(GuideEntries.line(t)).withStyle(net.minecraft.ChatFormatting.GRAY)));
        }
        return slot.with(note, tip);
    }

    @org.jetbrains.annotations.Nullable
    private static ProductionCategory.Entry entry(JsonObject o) {
        List<ProductionCategory.Slot> in = new ArrayList<>(), out = new ArrayList<>();
        if (o.has("in")) {
            for (JsonElement e : o.getAsJsonArray("in")) {
                ProductionCategory.Slot s = slot(e);
                if (s != null) {
                    in.add(s);
                }
            }
        }
        if (o.has("out")) {
            for (JsonElement e : o.getAsJsonArray("out")) {
                ProductionCategory.Slot s = slot(e);
                if (s != null) {
                    out.add(s);
                }
            }
        }
        if (in.isEmpty() && out.isEmpty()) {
            return null;
        }
        List<Component> lines = new ArrayList<>();
        if (o.has("lines")) {
            o.getAsJsonArray("lines").forEach(l -> lines.add(GuideEntries.line(l)));
        }
        return new ProductionCategory.Entry(GuideEntries.line(o.get("title")), in, out, lines);
    }

    // ------------------------------------------------------------------ familles calculees

    /** « a / b / c / d » : valeurs par tier. */
    private static Component perTier(long[] values, java.util.function.LongFunction<String> fmt) {
        MutableComponent c = Component.empty();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                c.append(" / ");
            }
            c.append(fmt.apply(values[i]));
        }
        return c;
    }

    private static long[] longs(int[] values) {
        long[] out = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = values[i];
        }
        return out;
    }

    private static List<ProductionCategory.Entry> renewable() {
        List<ProductionCategory.Entry> out = new ArrayList<>();
        String k = "jei.pioneers.prod.renewable.";
        for (EnergyKind kind : EnergyKind.values()) {
            List<ItemStack> machines = new ArrayList<>();
            if (PiEnergy.BLOCKS.containsKey(kind)) {
                PiEnergy.BLOCKS.get(kind).forEach(b -> machines.add(new ItemStack(b.getBlock())));
            }
            List<ProductionCategory.Slot> in = new ArrayList<>();
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(k + "peak", perTier(kind.peak, v -> Texts.energy(v) + "/t")));
            switch (kind) {
                case SOLAR -> lines.add(Component.translatable(k + "solar"));
                case WIND -> {
                    List<ItemStack> rotors = new ArrayList<>();
                    PiEnergy.ROTORS.forEach(b -> rotors.add(new ItemStack(b.getBlock())));
                    in.add(ProductionCategory.Slot.items(rotors).with(null, List.of(Component.translatable(k + "rotor"))));
                    lines.add(Component.translatable(k + "wind", perTier(longs(EnergyKind.WIND_RADIUS), String::valueOf)));
                }
                case HYDRO -> {
                    in.add(ProductionCategory.Slot.fluid(new FluidStack(Fluids.WATER, 1000))
                          .with(null, List.of(Component.translatable(k + "not_used"))));
                    lines.add(Component.translatable(k + "hydro", perTier(longs(EnergyKind.HYDRO_VOLUME), Texts::grouped), EnergyKind.HYDRO_HEAD));
                }
                case GEOTHERMAL -> {
                    in.add(ProductionCategory.Slot.fluid(new FluidStack(Fluids.WATER, EnergyKind.GEO_WATER[0])));
                    lines.add(Component.translatable(k + "geothermal", perTier(longs(EnergyKind.GEO_WATER), v -> v + " mB/t")));
                }
                case MICROBIAL -> {
                    List<ItemStack> food = new ArrayList<>();
                    BuiltInRegistries.ITEM.getTagOrEmpty(MicrobialCellTile.ORGANIC).forEach(h -> food.add(new ItemStack(h.value())));
                    if (!food.isEmpty()) {
                        in.add(ProductionCategory.Slot.items(food));
                    }
                    lines.add(Component.translatable(k + "microbial", perTier(EnergyKind.MICROBIAL_UNIT, Texts::energy)));
                }
            }
            //La machine elle-meme en premiere case : U sur le generateur trouve la page, comme sur ce qu'il consomme
            in.add(0, ProductionCategory.Slot.items(machines));
            out.add(new ProductionCategory.Entry(Component.translatable("block.pioneers." + kind.id(0)), in, List.of(), lines));
        }
        return out;
    }

    private static List<ProductionCategory.Entry> kinetic() {
        List<ProductionCategory.Entry> out = new ArrayList<>();
        String k = "jei.pioneers.prod.kinetic.";
        //Perceuse : roches du sol -> ce qu'elle en tire (DrillProducts, la regle du jeu)
        Map<Item, List<ItemStack>> byProduct = new LinkedHashMap<>();
        List<Block> rocks = new ArrayList<>(List.of(Blocks.STONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.SMOOTH_STONE, Blocks.DEEPSLATE,
              Blocks.COBBLED_DEEPSLATE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.TUFF, Blocks.CALCITE, Blocks.NETHERRACK,
              Blocks.BLACKSTONE, Blocks.BASALT, Blocks.END_STONE));
        for (net.minecraft.tags.TagKey<Block> tag : List.of(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD,
              net.minecraft.tags.BlockTags.BASE_STONE_NETHER)) {
            BuiltInRegistries.BLOCK.getTagOrEmpty(tag).forEach(h -> {
                if (!rocks.contains(h.value())) {
                    rocks.add(h.value());
                }
            });
        }
        for (Block rock : rocks) {
            ItemStack product = DrillProducts.of(rock.defaultBlockState());
            ItemStack in = new ItemStack(rock.asItem());
            if (product.isEmpty() || in.isEmpty()) {
                continue;
            }
            byProduct.computeIfAbsent(product.getItem(), x -> new ArrayList<>()).add(in);
        }
        ItemStack drill = new ItemStack(PiKinetics.machine(MachineType.DRILL));
        byProduct.forEach((product, inputs) -> out.add(new ProductionCategory.Entry(Component.translatable("block.pioneers.mechanical_drill"),
              List.of(ProductionCategory.Slot.item(drill), ProductionCategory.Slot.items(inputs)
                    .with(null, List.of(Component.translatable(k + "rock")))),
              List.of(ProductionCategory.Slot.item(new ItemStack(product))),
              List.of(Component.translatable(k + "drill", fmt(DrillProducts.TURNS))))));
        //Pompes : aspirent les blocs de fluide sous elles (Extraction.pump : 1 000 mB par bloc, l'eau ne s'epuise pas)
        List<ItemStack> pumps = machines(IndustryMachine.FLUID_PUMP);
        if (!pumps.isEmpty()) {
            out.add(new ProductionCategory.Entry(Component.translatable("industry.pioneers.machine." + IndustryMachine.FLUID_PUMP.key),
                  List.of(ProductionCategory.Slot.items(pumps)), List.of(ProductionCategory.Slot.fluid(new FluidStack(Fluids.WATER, 1000)),
                  ProductionCategory.Slot.fluid(new FluidStack(Fluids.LAVA, 1000))),
                  List.of(Component.translatable(k + "pump", Extraction.PUMP_RANGE, Extraction.PUMP_SCAN))));
        }
        //Dynamo : rotation -> energie (DynamoTile.FE_PER_JOULE, 1.12.4 : excitation adaptee, puissance nominale du MKI)
        List<ItemStack> dynamos = machines(IndustryMachine.DYNAMO);
        if (!dynamos.isEmpty()) {
            out.add(new ProductionCategory.Entry(Component.translatable("industry.pioneers.machine." + IndustryMachine.DYNAMO.key),
                  List.of(ProductionCategory.Slot.items(dynamos)), List.of(),
                  List.of(Component.translatable(k + "dynamo", fmt((float) (1 / DynamoTile.FE_PER_JOULE)), Texts.grouped(DynamoTile.RATED_FE[0])))));
        }
        //Noria : monte l'eau du dessous
        ItemStack noria = new ItemStack(PiKinetics.machine(MachineType.NORIA));
        out.add(new ProductionCategory.Entry(Component.translatable("block.pioneers.noria"), List.of(ProductionCategory.Slot.item(noria)),
              List.of(ProductionCategory.Slot.fluid(new FluidStack(Fluids.WATER, 1000))), List.of(Component.translatable(k + "noria"))));
        return out;
    }

    /** Ateliers (4 tiers) et controleur d'une machine industrielle. */
    private static List<ItemStack> machines(IndustryMachine m) {
        List<ItemStack> out = new ArrayList<>();
        if (PiIndustry.ATELIERS.containsKey(m)) {
            PiIndustry.ATELIERS.get(m).values().forEach(e -> out.add(new ItemStack(e.getBlock())));
        }
        if (m.industrial && PiIndustry.controller(m) != null) {
            out.add(new ItemStack(PiIndustry.controller(m).getBlock()));
        }
        return out;
    }

    /**
     * 1.0.5-beta (lot 46, retour 72) : BLOC A EAU : un tier par entree (eau par tick, FE/t, pompe multibloc du meme tier pour comparer) ;
     * chiffres = la regle du jeu ({@link fr.pioneers.tech.industry.GeneratedLot46}, {@link fr.pioneers.tech.industry.Tiers#power}).
     */
    private static List<ProductionCategory.Entry> water() {
        List<ProductionCategory.Entry> out = new ArrayList<>();
        List<ItemStack> all = machines(IndustryMachine.WATER_SOURCE);
        for (int t = 0; t < all.size() && t < 4; t++) {
            int rate = fr.pioneers.tech.industry.GeneratedLot46.WATER_RATE[t];
            out.add(new ProductionCategory.Entry(all.get(t).getHoverName(), List.of(ProductionCategory.Slot.item(all.get(t))),
                  List.of(ProductionCategory.Slot.fluid(new FluidStack(Fluids.WATER, rate))),
                  List.of(Component.translatable("jei.pioneers.prod.water.line", TIER_NAMES[t], Texts.grouped(rate),
                              Texts.grouped(fr.pioneers.tech.industry.Tiers.power(t))),
                        Component.translatable("jei.pioneers.prod.water.pump", Texts.grouped(fr.pioneers.tech.industry.GeneratedLot46.PUMP_RATE[t])))));
        }
        return out;
    }

    private static final String[] TIER_NAMES = {"MKI", "MKII", "MKIII", "MKIV"};

    /** Secondes d'une duree en ticks (« 2 », « 0.25 »). */
    private static String secs(int ticks) {
        String v = String.format(Locale.ROOT, "%.2f", ticks / 20.0).replaceAll("0+$", "").replaceAll("\\.$", "");
        return v;
    }

    /**
     * 1.0.5-beta (lot 46, retour 72 ; « echantillon + eau + lave jamais consommee ») : GENERATEUR DE ROCHES : une entree par roche permise
     * (source unique {@link fr.pioneers.tech.industry.GeneratedLot46#ROCKS}) : machines du tier minimal et au-dessus, echantillon (jamais consomme),
     * 1 000 mB d'eau et de lave (jamais prelevees) -> la meme roche.
     */
    private static List<ProductionCategory.Entry> rocks() {
        List<ProductionCategory.Entry> out = new ArrayList<>();
        List<ItemStack> all = machines(IndustryMachine.ROCK_GENERATOR);
        if (all.size() < 4) {
            return out;
        }
        int keep = fr.pioneers.tech.industry.GeneratedLot46.SAMPLE_FLUID;
        List<Component> never = List.of(Component.translatable("jei.pioneers.prod.rocks.never"));
        for (String id : fr.pioneers.tech.industry.GeneratedLot46.ROCKS) {
            Item rock = item(id);
            if (rock == Items.AIR) {
                continue;
            }
            int t = fr.pioneers.tech.industry.GeneratedLot46.ROCK_TIER.get(id);
            ItemStack stack = new ItemStack(rock);
            out.add(new ProductionCategory.Entry(stack.getHoverName(), List.of(ProductionCategory.Slot.items(all.subList(t, 4)),
                  ProductionCategory.Slot.item(stack).with(null, List.of(Component.translatable("jei.pioneers.prod.rocks.sample"))),
                  ProductionCategory.Slot.fluid(new FluidStack(Fluids.WATER, keep)).with(null, never),
                  ProductionCategory.Slot.fluid(new FluidStack(Fluids.LAVA, keep)).with(null, never)),
                  List.of(ProductionCategory.Slot.item(stack.copy())),
                  List.of(Component.translatable("jei.pioneers.prod.rocks.line", TIER_NAMES[t],
                        secs(fr.pioneers.tech.industry.GeneratedLot46.ROCK_TICKS[t]), Texts.grouped(fr.pioneers.tech.industry.Tiers.power(t))))));
        }
        return out;
    }

    /** Base d'extraction planetaire : le rover fore les filons de l'astre ou elle est batie (MiningMaterial.body). */
    private static List<ProductionCategory.Entry> outpost() {
        List<ProductionCategory.Entry> out = new ArrayList<>();
        List<ItemStack> heads = new ArrayList<>();
        for (DrillHead h : DrillHead.values()) {
            Item head = item("pioneers:" + h.id());
            if (head != Items.AIR) {
                heads.add(new ItemStack(head));
            }
        }
        Map<Body, List<ProductionCategory.Slot>> byBody = new LinkedHashMap<>();
        for (MiningMaterial m : MiningMaterial.values()) {
            var raw = PiItems.RAW.get(m);
            if (raw != null) {
                byBody.computeIfAbsent(m.body, b -> new ArrayList<>()).add(ProductionCategory.Slot.item(new ItemStack(raw.get())));
            }
        }
        ItemStack controller = new ItemStack(item("pioneers:mining_outpost_controller"));
        byBody.forEach((body, raws) -> out.add(new ProductionCategory.Entry(Component.translatable(body.getTranslationKey()),
              List.of(ProductionCategory.Slot.item(controller), ProductionCategory.Slot.items(heads)), raws,
              List.of(Component.translatable("jei.pioneers.prod.outpost.line", Component.translatable(body.getTranslationKey()))))));
        return out;
    }

    private static String fmt(float v) {
        return v == (int) v ? Integer.toString((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static List<ProductionCategory.Entry> probes() {
        List<ProductionCategory.Entry> out = new ArrayList<>();
        String k = "jei.pioneers.prod.probes.";
        for (ProbeDestination d : ProbeDestination.values()) {
            DataType type = PiDataTypes.of(d);
            Item rocket = d.minTier.getItem();
            if (type == null) {
                continue;
            }
            List<ProductionCategory.Slot> in = new ArrayList<>();
            if (rocket != null && rocket != Items.AIR) {
                in.add(ProductionCategory.Slot.item(new ItemStack(rocket)));
            }
            Gas hydrogen = PioneersAPI.gas(Pioneers.rl("hydrogen"));
            if (hydrogen != null && !hydrogen.isEmptyType() && d.hydrogen > 0) {
                in.add(ProductionCategory.Slot.gas(GasStack.of(hydrogen, d.hydrogen)));
            }
            out.add(new ProductionCategory.Entry(Component.translatable(d.getTranslationKey()), in,
                  List.of(ProductionCategory.Slot.data(DataStack.of(type, 1))),
                  List.of(Component.translatable(k + "launch", "T" + d.minTier.level),
                        Component.translatable(k + "data", fmt((float) ProbeDestination.DATA_PER_PROBE_PER_TICK), pct(d.dataFailure)))));
        }
        return out;
    }

    private static List<ProductionCategory.Entry> research() {
        List<ProductionCategory.Entry> out = new ArrayList<>();
        String k = "jei.pioneers.prod.research.";
        ItemStack table = new ItemStack(item("pioneers:research_table"));
        for (ResearchNode node : ResearchNode.values()) {
            ItemStack blueprint = node.getBlueprint();
            if (blueprint == null || blueprint.isEmpty()) {
                continue;
            }
            out.add(new ProductionCategory.Entry(Component.translatable(node.getTranslationKey()), List.of(ProductionCategory.Slot.item(table)),
                  List.of(ProductionCategory.Slot.item(blueprint)), List.of(Component.translatable(k + "blueprint",
                  Component.translatable(node.getTranslationKey())))));
        }
        ItemStack lab = new ItemStack(item("pioneers:ai_lab"));
        for (int level = AiChips.NONE; level < AiChips.FOUNDATION; level++) {
            ItemStack input = new ItemStack(AiChips.item(level));
            ItemStack result = new ItemStack(AiChips.item(level + 1));
            if (input.isEmpty() || result.isEmpty()) {
                continue;
            }
            out.add(new ProductionCategory.Entry(result.getHoverName(), List.of(ProductionCategory.Slot.item(lab), ProductionCategory.Slot.item(input)),
                  List.of(ProductionCategory.Slot.item(result)), List.of(Component.translatable(k + "ai"))));
        }
        return out;
    }
}

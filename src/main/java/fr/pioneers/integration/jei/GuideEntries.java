package fr.pioneers.integration.jei;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.pioneers.Pioneers;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.core.Texts;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.tech.industry.GeneratorOutput;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.RadiatorBlock;
import fr.pioneers.tech.industry.multi.RadiatorTile;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Contenu des categories explicatives et des pages d'information JEI :
 * <ul>
 *     <li>combustibles : construits ici depuis les durees de combustion et les recettes des generateurs et de la chaudiere ;</li>
 *     <li>chaleur, air comprime et pages d'information (1.10.2 : groupes d'objets, usage seulement ; les ressources sauvages sont dans
 *     {@link Productions}) : lus dans {@code assets/pioneers/jei/guide.json}, ecrit par
 *     {@code tools/jei/jei_info.py} (un argument « @cle » est une cle de langue, sinon un texte tel quel ; « #tag » designe un tag d'objets).</li>
 * </ul>
 */
final class GuideEntries {

    private static final ResourceLocation FILE = Pioneers.rl("jei/guide.json");

    private GuideEntries() {
    }

    // ------------------------------------------------------------------ combustibles

    /**
     * MAJ simplification (03/11/2026, Guillaume : « mieux documenter les radiateurs dans JEI avec les chiffres ») : une page par radiateur,
     * chiffres lus dans {@link RadiatorTile} (source unique, aussi lue par le jeu et l'infobulle) : pertes, conductance, capacite, chaleur
     * evacuee contre un multibloc de son tier a sa temperature nominale et contre un multibloc MKI.
     */
    static List<GuideCategory.Entry> radiators() {
        List<GuideCategory.Entry> out = new ArrayList<>();
        String k = "jei.pioneers.radiator.";
        for (Map.Entry<CableTier, BlockEntry<RadiatorBlock>> e : PiIndustry.RADIATORS.entrySet()) {
            int t = e.getKey().ordinal();
            int nominal = Tiers.nominal(t);
            long rated = Math.round(RadiatorTile.rated(t, t, nominal));
            long early = Math.round(RadiatorTile.rated(t, 0, Tiers.nominal(0)));
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(k + "loss", Texts.grouped(Math.round(RadiatorTile.DISSIPATION[t])),
                  Texts.grouped(Math.round(RadiatorTile.CONDUCTANCE[t])), Texts.grouped(Math.round(RadiatorTile.CAPACITY))));
            lines.add(Component.translatable(k + "rated", Tooltips.tierName(t), Texts.grouped(nominal), Texts.grouped(rated),
                  Texts.grouped(Math.round(rated / ControllerTile.WATER_J))));
            if (t > 0) {
                lines.add(Component.translatable(k + "early", Texts.grouped(Tiers.nominal(0)), Texts.grouped(early)));
            }
            lines.add(Component.translatable(k + "use"));
            out.add(GuideCategory.Entry.of(List.of(List.of(new ItemStack(e.getValue().getBlock()))), List.of(),
                  Component.translatable("block.pioneers." + e.getKey().id() + "_radiator"), lines));
        }
        return out;
    }

    static List<GuideCategory.Entry> fuels(Level level) {
        List<GuideCategory.Entry> out = new ArrayList<>();
        //1.11.2 (lot E2) : tout vient des recettes et de GeneratorOutput (plus de constantes recopiees) : FE/t et FE au total partout
        Gas steamGas = PioneersAPI.gas(STEAM);
        GeneratorOutput.Use turbine = steamGas == null ? null : GeneratorOutput.gas(level, GasStack.of(steamGas, 1));
        double fePerSteam = turbine == null ? 0 : GeneratorOutput.perUnit(turbine.recipe(), turbine.amount(), turbine.industrial());
        Component generator = machineName(IndustryMachine.SOLID_COMBUSTION_GENERATOR, false);
        Map<Item, Long> boiled = boiledSolids(level);
        //Solides : tout objet qui brule
        for (Item item : ForgeRegistries.ITEMS) {
            ItemStack stack = new ItemStack(item);
            IndustrialRecipe fuel = GeneratorOutput.solidFuel(stack);
            if (fuel == null) {
                continue;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("jei.pioneers.fe.fuel_in", generator, Texts.grouped(GeneratorOutput.perTick(fuel, 0, false)),
                  PiCategory.seconds(fuel.time()), Texts.grouped(GeneratorOutput.total(fuel, false))));
            lines.add(Component.translatable("jei.pioneers.fe.fuel_tiers", Texts.grouped(GeneratorOutput.perTick(fuel, 1, false)),
                  Texts.grouped(GeneratorOutput.perTick(fuel, 2, false)), Texts.grouped(GeneratorOutput.perTick(fuel, 3, false)),
                  Texts.grouped(GeneratorOutput.total(fuel, true))));
            Long steam = boiled.get(item);
            if (steam != null && fePerSteam > 0) {
                lines.add(Component.translatable("jei.pioneers.fuels.boiler_solid", PiCategory.mb(steam), Texts.energy(Math.round(steam * fePerSteam))));
            }
            out.add(GuideCategory.Entry.of(List.of(List.of(stack)), List.of(), Component.translatable("jei.pioneers.fuels.solid", stack.getHoverName()),
                  lines));
        }
        //Fluides et gaz : recettes des generateurs (energie) et de la chaudiere (vapeur)
        Map<Fluid, List<Component>> fluids = new LinkedHashMap<>();
        Map<Fluid, Integer> fluidAmounts = new LinkedHashMap<>();
        Map<ResourceLocation, List<Component>> gases = new LinkedHashMap<>();
        Map<ResourceLocation, Long> gasAmounts = new LinkedHashMap<>();
        for (IndustryMachine m : IndustryMachine.values()) {
            boolean isGenerator = m.kind == IndustryMachine.Kind.GENERATOR;
            if (!isGenerator && m != IndustryMachine.BOILER) {
                continue;
            }
            boolean multi = m.atelier == IndustryMachine.Atelier.NONE;
            Component name = machineName(m, multi);
            for (IndustrialRecipe recipe : IndustryRecipes.withoutLow(IndustryRecipes.of(level, m))) {   // 1.0.5-beta (lot 38) : sans les converties
                Component line;
                if (isGenerator) {
                    if (recipe.energy() <= 0) {
                        continue;
                    }
                    line = Component.translatable("jei.pioneers.fe.fuel_in", name, Texts.grouped(GeneratorOutput.perTick(recipe, 0, multi)),
                          PiCategory.seconds(GeneratorOutput.duration(recipe.time(), 0)), Texts.grouped(GeneratorOutput.total(recipe, multi)));
                } else {
                    //1.0 (0.19.12) : la vapeur est un gaz
                    long steam = steamOf(recipe);
                    if (steam <= 0 || (recipe.fluids().size() < 2 && recipe.gases().isEmpty())) {
                        continue;
                    }
                    line = Component.translatable("jei.pioneers.fuels.boiler", name, PiCategory.mb(steam), Texts.energy(Math.round(steam * fePerSteam)));
                }
                for (IndustrialRecipe.FluidIn in : recipe.fluids()) {
                    List<FluidStack> shown = in.displayed();
                    if (shown.isEmpty() || shown.get(0).getFluid() == Fluids.WATER) {
                        continue;
                    }
                    Fluid f = shown.get(0).getFluid();
                    fluids.computeIfAbsent(f, k -> new ArrayList<>()).add(with(line, in.amount()));
                    fluidAmounts.putIfAbsent(f, in.amount());
                }
                for (IndustrialRecipe.GasAmount in : recipe.gases()) {
                    gases.computeIfAbsent(in.gas(), k -> new ArrayList<>()).add(with(line, in.amount()));
                    gasAmounts.putIfAbsent(in.gas(), in.amount());
                }
            }
        }
        fluids.forEach((f, lines) -> {
            FluidStack stack = new FluidStack(f, fluidAmounts.get(f));
            out.add(new GuideCategory.Entry(List.of(), stack, null, List.of(), Component.translatable("jei.pioneers.fuels.liquid", stack.getDisplayName()),
                  lines));
        });
        gases.forEach((id, lines) -> {
            Gas gas = PioneersAPI.gas(id);
            if (gas == null || gas.isEmptyType()) {
                return;
            }
            GasStack stack = GasStack.of(gas, gasAmounts.get(id));
            out.add(new GuideCategory.Entry(List.of(), null, stack, List.of(), Component.translatable("jei.pioneers.fuels.gas", gas.getName()), lines));
        });
        return out;
    }

    private static final ResourceLocation STEAM = Pioneers.rl("steam");

    /** Vapeur rendue par une recette de la chaudiere (mB). */
    private static long steamOf(IndustrialRecipe recipe) {
        long steam = 0;
        for (IndustrialRecipe.GasAmount res : recipe.gasResults()) {
            if (res.gas().equals(STEAM)) {
                steam += res.amount();
            }
        }
        return steam;
    }

    /** Combustibles solides de la chaudiere : vapeur rendue par objet brule (mB). */
    private static Map<Item, Long> boiledSolids(Level level) {
        Map<Item, Long> out = new LinkedHashMap<>();
        for (IndustrialRecipe recipe : IndustryRecipes.of(level, IndustryMachine.BOILER)) {
            long steam = steamOf(recipe);
            if (steam <= 0 || recipe.items().isEmpty()) {
                continue;
            }
            var in = recipe.items().get(0);
            for (ItemStack s : in.ingredient().getItems()) {
                out.putIfAbsent(s.getItem(), steam / Math.max(1, in.count()));
            }
        }
        return out;
    }

    /** Nom d'un generateur : sa machine d'atelier MKI, sinon son multibloc. */
    private static Component machineName(IndustryMachine m, boolean multi) {
        if (!multi && PiIndustry.ATELIERS.get(m) != null && PiIndustry.ATELIERS.get(m).get(CableTier.BASIC) != null) {
            return PiIndustry.ATELIERS.get(m).get(CableTier.BASIC).getBlock().getName();
        }
        return PiIndustry.controller(m) != null ? PiIndustry.controller(m).getBlock().getName() : Component.translatable("industry.pioneers.machine." + m.key);
    }

    private static Component with(Component line, long amount) {
        return Component.empty().append(line).append(Component.translatable("jei.pioneers.fuels.per", PiCategory.mb(amount)));
    }

    // ------------------------------------------------------------------ fichier guide.json

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

    /** Ligne : ["cle", arg...] ; arg « @cle » = traduction, sinon texte. */
    static Component line(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return Component.translatable(element.getAsString());
        }
        JsonArray a = element.getAsJsonArray();
        Object[] args = new Object[a.size() - 1];
        for (int i = 1; i < a.size(); i++) {
            String s = a.get(i).getAsString();
            args[i - 1] = s.startsWith("@") ? joined(s.substring(1)) : s;
        }
        return Component.translatable(a.get(0).getAsString(), args);
    }

    /** « a|b|c » : traductions jointes par des virgules. */
    private static Component joined(String keys) {
        MutableComponent out = Component.empty();
        String[] parts = keys.split("\\|");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(Component.translatable(parts[i]));
        }
        return out;
    }

    private static List<ItemStack> stacks(String id) {
        List<ItemStack> out = new ArrayList<>();
        if (id.startsWith("#")) {
            BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.create(ResourceLocation.parse(id.substring(1)))).forEach(h -> out.add(new ItemStack(h.value())));
        } else {
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id));
            if (item != null && item != Items.AIR) {
                out.add(new ItemStack(item));
            }
        }
        return out;
    }

    static List<GuideCategory.Entry> entries(String section) {
        List<GuideCategory.Entry> out = new ArrayList<>();
        JsonObject root = read();
        if (!root.has(section)) {
            return out;
        }
        for (JsonElement e : root.getAsJsonArray(section)) {
            JsonObject o = e.getAsJsonObject();
            List<List<ItemStack>> inputs = new ArrayList<>();
            if (o.has("in")) {
                for (JsonElement slot : o.getAsJsonArray("in")) {
                    List<ItemStack> alts = new ArrayList<>();
                    for (JsonElement id : slot.getAsJsonArray()) {
                        alts.addAll(stacks(id.getAsString()));
                    }
                    if (!alts.isEmpty()) {
                        inputs.add(alts);
                    }
                }
            }
            List<ItemStack> outputs = new ArrayList<>();
            if (o.has("out")) {
                for (JsonElement r : o.getAsJsonArray("out")) {
                    JsonArray a = r.getAsJsonArray();
                    for (ItemStack s : stacks(a.get(0).getAsString())) {
                        s.setCount(a.size() > 1 ? a.get(1).getAsInt() : 1);
                        outputs.add(s);
                    }
                }
            }
            List<Component> lines = new ArrayList<>();
            if (o.has("lines")) {
                o.getAsJsonArray("lines").forEach(l -> lines.add(line(l)));
            }
            out.add(GuideCategory.Entry.of(inputs, outputs, line(o.get("title")), lines));
        }
        return out;
    }

    /** Catalyseurs d'une categorie explicative : liste d'identifiants sous « catalysts.<section> ». */
    static void catalysts(mezz.jei.api.registration.IRecipeCatalystRegistration registration, String section,
          mezz.jei.api.recipe.RecipeType<GuideCategory.Entry> type) {
        JsonObject root = read();
        if (!root.has("catalysts") || !root.getAsJsonObject("catalysts").has(section)) {
            return;
        }
        for (JsonElement id : root.getAsJsonObject("catalysts").getAsJsonArray(section)) {
            for (ItemStack stack : stacks(id.getAsString())) {
                registration.addRecipeCatalyst(stack, type);
            }
        }
    }

    /** Pages d'information : objets, fluides et gaz. */
    static void info(IRecipeRegistration registration) {
        JsonObject root = read();
        if (!root.has("info")) {
            return;
        }
        JsonObject info = root.getAsJsonObject("info");
        //1.10.2 : une page pour un groupe d'objets (meme usage : marteaux de prospection, unites robotiques...)
        if (info.has("groups")) {
            for (JsonElement e : info.getAsJsonArray("groups")) {
                JsonObject g = e.getAsJsonObject();
                List<ItemStack> group = new ArrayList<>();
                g.getAsJsonArray("items").forEach(id -> group.addAll(stacks(id.getAsString())));
                if (!group.isEmpty()) {
                    registration.addIngredientInfo(group, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, components(g.get("lines"), group.get(0)));
                }
            }
        }
        if (info.has("items")) {
            for (Map.Entry<String, JsonElement> e : info.getAsJsonObject("items").entrySet()) {
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(e.getKey()));
                if (item == null || item == Items.AIR) {
                    continue;
                }
                ItemStack stack = new ItemStack(item);
                registration.addIngredientInfo(stack, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, components(e.getValue(), stack));
            }
        }
        if (info.has("fluids")) {
            for (Map.Entry<String, JsonElement> e : info.getAsJsonObject("fluids").entrySet()) {
                Fluid fluid = ForgeRegistries.FLUIDS.getValue(ResourceLocation.parse(e.getKey()));
                if (fluid == null || fluid == Fluids.EMPTY) {
                    continue;
                }
                registration.addIngredientInfo(new FluidStack(fluid, 1000), mezz.jei.api.forge.ForgeTypes.FLUID_STACK, components(e.getValue(), ItemStack.EMPTY));
            }
        }
        if (info.has("gases")) {
            for (Map.Entry<String, JsonElement> e : info.getAsJsonObject("gases").entrySet()) {
                Gas gas = PioneersAPI.gas(ResourceLocation.parse(e.getKey()));
                if (gas == null || gas.isEmptyType()) {
                    continue;
                }
                registration.addIngredientInfo(GasStack.of(gas, 1000), GasIngredient.TYPE, components(e.getValue(), ItemStack.EMPTY));
            }
        }
    }

    /** Lignes d'une page ; « $tool » et « $armor » : caracteristiques lues sur l'objet lui-meme. */
    private static Component[] components(JsonElement lines, ItemStack stack) {
        List<Component> out = new ArrayList<>();
        for (JsonElement l : lines.getAsJsonArray()) {
            if (l.isJsonPrimitive() && l.getAsString().startsWith("$")) {
                Component stats = stats(l.getAsString(), stack);
                if (stats != null) {
                    out.add(stats);
                }
                continue;
            }
            out.add(Component.empty().append(line(l)));
        }
        return out.toArray(new Component[0]);
    }

    @Nullable
    private static Component stats(String token, ItemStack stack) {
        Item item = stack.getItem();
        if ("$tool".equals(token) && item instanceof TieredItem tiered) {
            Tier tier = tiered.getTier();
            float damage = item instanceof DiggerItem digger ? digger.getAttackDamage()
                  : item instanceof SwordItem sword ? sword.getDamage() : tier.getAttackDamageBonus();
            return Component.translatable("jei.pioneers.info.tool_stats", tier.getLevel(), stack.getMaxDamage(), fmt(tier.getSpeed()), fmt(damage + 1));
        }
        if ("$armor".equals(token) && item instanceof ArmorItem armor) {
            return Component.translatable("jei.pioneers.info.armor_stats", armor.getDefense(), fmt(armor.getToughness()), stack.getMaxDamage());
        }
        return null;
    }

    private static String fmt(float value) {
        return value == (int) value ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}

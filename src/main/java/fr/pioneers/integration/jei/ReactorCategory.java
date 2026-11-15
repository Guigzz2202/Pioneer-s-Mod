package fr.pioneers.integration.jei;

import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.core.Texts;
import fr.pioneers.fusion.FusionType;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.multi.ControllerTile;
import fr.pioneers.industry.multi.IndustryShape;
import fr.pioneers.industry.multi.ShapeTemplate;
import fr.pioneers.nuclear.FissionCore;
import fr.pioneers.nuclear.ReactorType;
import fr.pioneers.registries.PiIndustry;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.24 : categorie JEI « Reacteurs » : ce que chaque reacteur a fission (MKI a MKIII) et a fusion (tokamak, heliotron) consomme et rend,
 * avec ses chiffres (puissance, duree d'un combustible, eau et vapeur a pleine puissance) lus dans le code ({@link ReactorType},
 * {@link FissionCore}, {@link FusionType}) et dans le gabarit du multibloc ({@link ShapeTemplate}). Une entree par usage : combustion,
 * cibles d'irradiation, couvertures surgeneratrices, fusion, surgeneration du tritium.
 */
class ReactorCategory extends PiCategory<ReactorCategory.Entry> {

    static final int W = 176, H = 100, SLOT = 18, TOP = 0, GAUGE = 18;

    /** Fluide ou gaz affiche dans une jauge (l'un des deux). */
    record Flow(@Nullable FluidStack fluid, @Nullable GasStack gas) {

        static Flow of(Fluid fluid, long amount) {
            return new Flow(new FluidStack(fluid, (int) Math.max(1, Math.min(Integer.MAX_VALUE, amount))), null);
        }

        @Nullable
        static Flow gas(String id, long amount) {
            Gas g = PiIndustry.gas(id);
            return g == null ? null : new Flow(null, GasStack.of(g, Math.max(1, amount)));
        }
    }

    /** Une entree : reacteur, objets et flux d'entree, objets et flux de sortie, titre, lignes d'explication. */
    record Entry(IndustryMachine machine, List<List<ItemStack>> inputs, List<Flow> flowsIn, List<ItemStack> outputs, List<Flow> flowsOut,
                 Component title, List<Component> lines) {
    }

    ReactorCategory(IGuiHelper helper, RecipeType<Entry> type) {
        super(helper, type, "jei.pioneers.reactors", PiIndustry.controller(IndustryMachine.FISSION_REACTOR).getBlock(), W, H);
        family(JeiDraw.Family.FUSION);
    }

    /** 1.11.3 : le titre de l'entree passe dans l'en-tete de la charte. */
    @Override
    protected Component headerTitle(Entry entry) {
        return entry.title();
    }

    /** 1.11.3 : badge MK de la generation du reacteur a fission (MKI a MKIII), « Fusion » pour le tokamak et l'heliotron. */
    @Override
    protected JeiDraw.Badge badge(Entry entry) {
        return switch (entry.machine()) {
            case FISSION_REACTOR -> JeiDraw.Badge.tier(0);
            case FAST_REACTOR -> JeiDraw.Badge.tier(1);
            case FRAGMENT_REACTOR -> JeiDraw.Badge.tier(2);
            default -> super.badge(entry);
        };
    }

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, Entry entry, @NotNull IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.CATALYST, 1, TOP + 1).setStandardSlotBackground().addItemStack(new ItemStack(PiIndustry.controller(entry.machine()).getBlock()));
        int x = SLOT + 4;
        for (List<ItemStack> slot : entry.inputs()) {
            builder.addSlot(RecipeIngredientRole.INPUT, x + 1, TOP + 1).addItemStacks(slot).setStandardSlotBackground();
            x += SLOT;
        }
        for (Flow f : entry.flowsIn()) {
            flow(builder, RecipeIngredientRole.INPUT, x, f);
            x += GAUGE;
        }
        int n = entry.outputs().size() + entry.flowsOut().size();
        int ox = W - SLOT * n;
        for (ItemStack out : entry.outputs()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, ox + 1, TOP + 1).addItemStack(out).setStandardSlotBackground();
            ox += SLOT;
        }
        for (Flow f : entry.flowsOut()) {
            flow(builder, RecipeIngredientRole.OUTPUT, ox, f);
            ox += GAUGE;
        }
    }

    private static void flow(IRecipeLayoutBuilder builder, RecipeIngredientRole role, int x, Flow f) {
        if (f.fluid() != null) {
            fluid(builder, role, x, TOP, GAUGE, GAUGE, f.fluid().getFluid(), f.fluid().getAmount(), f.fluid().getAmount());
        } else if (f.gas() != null) {
            gas(builder, role, x, TOP, GAUGE, GAUGE, f.gas(), f.gas().getAmount());
        }
    }

    @Override
    protected void drawContent(@NotNull Entry entry, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        int left = SLOT + 4 + entry.inputs().size() * SLOT + entry.flowsIn().size() * GAUGE;
        int right = W - SLOT * (entry.outputs().size() + entry.flowsOut().size());
        JeiDraw.still(graphics, (left + right) / 2F, TOP + 5 + 4);
        List<Note> notes = new ArrayList<>();
        for (Component line : entry.lines()) {
            notes.add(new Note(line, TEXT));
        }
        fr.pioneers.client.gui.GuiDraw.screen(graphics, 0, TOP + SLOT + 3, W, H - (TOP + SLOT + 3));
        block(graphics, notes, 3, TOP + SLOT + 3 + 2.5F, SMALL, W - 6, H - 1);
    }

    // ------------------------------------------------------------------ entrees

    private static ItemStack item(String id) {
        return new ItemStack(PiIndustry.item(id));
    }

    private static List<ItemStack> one(String id) {
        return List.of(item(id));
    }

    /** Nombre de cases de ce role dans le gabarit du multibloc. */
    static int count(IndustryMachine m, int role) {
        List<ShapeTemplate> all = ShapeTemplate.of(m);
        if (all.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (int cell : all.get(0).cells()) {
            if (ShapeTemplate.reactor(cell) == role) {
                n++;
            }
        }
        return n;
    }

    /** Places de combustible d'un reacteur a fission (canaux ; MKIII : une cartouche pour deux bobines). */
    static int channels(ReactorType t) {
        IndustryMachine m = machine(t);
        return t == ReactorType.MK3 ? Math.max(4, count(m, IndustryShape.COIL) / 2) : count(m, IndustryShape.FUEL);
    }

    static IndustryMachine machine(ReactorType t) {
        return switch (t) {
            case MK1 -> IndustryMachine.FISSION_REACTOR;
            case MK2 -> IndustryMachine.FAST_REACTOR;
            case MK3 -> IndustryMachine.FRAGMENT_REACTOR;
        };
    }

    private static Component num(double v) {
        return Component.literal(Texts.format(Math.round(v)));
    }

    static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        Fluid sodium = PiIndustry.fluid("liquid_sodium"), helium = PiIndustry.fluid("liquid_helium");
        for (ReactorType t : ReactorType.values()) {
            IndustryMachine m = machine(t);
            int n = channels(t);
            double full = n * t.fuelPower;
            double heat = t == ReactorType.MK3 ? full * (1 - FissionCore.ELECTRIC_SHARE) : full;
            long water = Math.round(heat / ControllerTile.WATER_J);
            double steamPer = t == ReactorType.MK2 ? 1.25 : 1;
            long steam = Math.round(water * 8 * steamPer);
            List<Flow> in = new ArrayList<>();
            if (t == ReactorType.MK2) {
                in.add(Flow.of(sodium, (long) FissionCore.SODIUM_PER_CHANNEL * n));
            }
            in.add(Flow.of(Fluids.WATER, water));
            List<Flow> outFlows = new ArrayList<>();
            Flow s = Flow.gas("steam", steam);
            if (s != null) {
                outFlows.add(s);
            }
            List<Component> lines = new ArrayList<>();
            String k = "jei.pioneers.reactor.";
            lines.add(Component.translatable(k + "fuel_power", num(t.fuelPower), n, num(full)));
            lines.add(Component.translatable(k + "fuel_life", num(t.fuelLife), num(t.fuelLife / 1200.0)));
            if (t == ReactorType.MK3) {
                lines.add(Component.translatable(k + "electric", num(full * FissionCore.ELECTRIC_SHARE), num(FissionCore.IGNITION_RATE)));
            }
            if (t == ReactorType.MK2) {
                lines.add(Component.translatable(k + "sodium", num((long) FissionCore.SODIUM_PER_CHANNEL * n)));
            }
            lines.add(Component.translatable(k + "cooling", num(water), num(steam)));
            lines.add(Component.translatable(k + "pilot"));
            out.add(new Entry(m, List.of(one(t.fuel)), in, List.of(item(t.spent)), outFlows,
                  Component.translatable(k + "burn", Component.translatable("block.pioneers." + m.multiId())), lines));
            if (t.targets()) {
                out.add(new Entry(m, List.of(List.of(item("lithium_target"), item("neptunium_target"))), List.of(),
                      List.of(item("irradiated_lithium_target"), item("irradiated_neptunium_target")), List.of(),
                      Component.translatable(k + "targets_title", Component.translatable("block.pioneers." + m.multiId())),
                      List.of(Component.translatable(k + "targets", Math.max(1, n / 2)), Component.translatable(k + "targets_use"))));
            }
            if (t.blankets()) {
                out.add(new Entry(m, List.of(List.of(item("depleted_blanket_assembly"), item("thorium_blanket_assembly"))), List.of(),
                      List.of(item("irradiated_depleted_blanket"), item("irradiated_thorium_blanket")), List.of(),
                      Component.translatable(k + "blankets_title", Component.translatable("block.pioneers." + m.multiId())),
                      List.of(Component.translatable(k + "blankets", Math.max(1, n / 2), String.valueOf(FissionCore.BREEDING_RATIO)),
                            Component.translatable(k + "blankets_use"))));
            }
        }
        for (FusionType f : FusionType.values()) {
            IndustryMachine m = f == FusionType.TOKAMAK ? IndustryMachine.TOKAMAK : IndustryMachine.STELLARATOR;
            double mj = f.maxPower / 1_000_000.0;
            long fuel = Math.max(1, Math.round(mj * f.fuelPerMj * 1_200));
            List<Flow> in = new ArrayList<>();
            addGas(in, "deuterium", fuel);
            addGas(in, f.tritium() ? "tritium" : "helium_3", fuel);
            in.add(Flow.of(helium, 1_000));
            List<Flow> outFlows = new ArrayList<>();
            addGas(outFlows, "helium", fuel);
            String k = "jei.pioneers.reactor.";
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(k + "fusion_power", num(f.maxPower), num(f.perCell)));
            lines.add(Component.translatable(k + "fusion_fuel", String.valueOf(f.fuelPerMj), num(fuel)));
            lines.add(Component.translatable(k + (f.tritium() ? "fusion_tokamak" : "fusion_stellarator")));
            lines.add(Component.translatable(k + "fusion_helium"));
            out.add(new Entry(m, List.of(), in, List.of(), outFlows,
                  Component.translatable(k + "fusion", Component.translatable("block.pioneers." + m.multiId())), lines));
            if (f.tritium()) {
                List<Flow> bred = new ArrayList<>();
                addGas(bred, "tritium", 1_000);
                out.add(new Entry(m, List.of(one("lithium_ceramic_pebbles")), List.of(), List.of(), bred,
                      Component.translatable(k + "breeding_title", Component.translatable("block.pioneers." + m.multiId())),
                      List.of(Component.translatable(k + "breeding"))));
            }
        }
        return out;
    }

    private static void addGas(List<Flow> list, String id, long amount) {
        Flow f = Flow.gas(id, amount);
        if (f != null) {
            list.add(f);
        }
    }
}

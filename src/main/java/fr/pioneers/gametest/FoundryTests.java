package fr.pioneers.gametest;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.core.registries.PiFluids;
import fr.pioneers.core.registries.PiIndustry;
import fr.pioneers.core.registries.PiTransport;
import fr.pioneers.logistics.transport.CableTier;
import fr.pioneers.logistics.transport.buffer.BufferType;
import fr.pioneers.logistics.transport.logistic.Accesses;
import fr.pioneers.logistics.transport.logistic.CableLevel;
import fr.pioneers.logistics.transport.logistic.ChannelKind;
import fr.pioneers.logistics.transport.logistic.LogisticCableBlock;
import fr.pioneers.logistics.transport.logistic.LogisticNetwork;
import fr.pioneers.logistics.transport.logistic.LogisticNetworks;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * MAJ Recettes (lot 4b, GameTests du brief §6) : la fonderie.
 * <ul>
 *     <li>une coulee avec chaque fluide : un lingot de cuivre a la matrice, a l'air, a l'eau, a l'huile de trempe, au caloporteur, a l'azote
 *     liquide et a l'helium liquide (recettes {@code industry/casting_machine/cast_ingot_copper[_<fluide>]} de
 *     tools/gc/industry/foundry_gen.py), sur six machines de coulee ultimes : lingot et jets (pepites) en sortie, fluide chaud rendu (vapeur
 *     detendue, huile, caloporteur chaud, azote et helium gazeux) ;</li>
 *     <li>le moule en sable : ebauche d'engrenage d'acier a l'eau ; 1.0.5-beta (lot 46, retour 62 bis) : le moule est incassable en machine
 *     (case Outil, rendu a chaque coulee, plus de sable use) ;</li>
 *     <li>la conduite refractaire : un reseau de conduites refractaires prend l'acier fondu, un reseau de conduites ordinaires (ou mixte) le
 *     refuse ; tous prennent l'etain fondu (moins de 1 200 degres C).</li>
 * </ul>
 * Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer) ; aucune classe du jeu ne cite ce paquet.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class FoundryTests {

    private FoundryTests() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("pioneers", path);
    }

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(rl(id));
        Gt.check(it != null && it != net.minecraft.world.item.Items.AIR, "objet absent : " + id);
        return it;
    }

    private static Fluid fluid(String id) {
        Fluid f = PiIndustry.fluid(id);
        Gt.check(f != Fluids.EMPTY, "fluide absent : " + id);
        return f;
    }

    /** Machine de coulee ultime posee en rel, energie pleine, recette verrouillee. */
    private static AtelierTile caster(GameTestHelper h, BlockPos rel, String recipe) {
        Block block = PiIndustry.ATELIERS.get(IndustryMachine.CASTING_MACHINE).get(CableTier.ULTIMATE).getBlock();
        BlockState st = block.defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        h.setBlock(rel, st);
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "pas de tuile d'atelier pour la machine de coulee");
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        t.getEnergy().setStored(t.getEnergy().getCapacity());
        Gt.check(t.setLockedRecipe(rl(recipe)), "recette " + recipe + " absente de la machine de coulee");
        return t;
    }

    private static int count(AtelierTile m, Item it) {
        int n = 0;
        for (ItemSlot s : m.itemOutputs()) {
            if (s.getStack().is(it)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static long gasOut(AtelierTile m, String id) {
        Gas gas = PioneersAPI.gas(rl(id));
        long n = 0;
        for (var tank : m.gasOutputs()) {
            GasStack st = tank.getStack();
            if (!st.isEmpty() && st.getGas().equals(gas)) {
                n += st.getAmount();
            }
        }
        return n;
    }

    private static int fluidOut(AtelierTile m, Fluid f) {
        int n = 0;
        for (var tank : m.fluidOutputs()) {
            if (!tank.isEmpty() && tank.getFluid().getFluid().isSame(f)) {
                n += tank.getFluidAmount();
            }
        }
        return n;
    }

    /** Une variante de coulee : suffixe de la recette, fluide de refroidissement (null : air), sortie chaude attendue. */
    private record Variant(String suffix, @Nullable Fluid coolant, @Nullable String hotGas, @Nullable Fluid hotFluid) {}

    @GameTest(template = Gt.SMALL, batch = "pioneers_fonderie_fluides", timeoutTicks = 1200)
    public static void coulee_chaque_fluide(GameTestHelper h) {
        Gt.begin(h);
        Item ingot = item("copper_ingot"), nugget = item("copper_nugget"), die = item("ingot_die");
        List<Variant> variants = List.of(
              new Variant("", null, null, null),
              new Variant("_water", Fluids.WATER, "exhaust_steam", null),
              new Variant("_oil", fluid("quench_oil"), null, fluid("quench_oil")),
              new Variant("_coolant", fluid("coolant"), null, fluid("hot_coolant")),
              new Variant("_cryo", PiFluids.LIQUID_NITROGEN.get(), "nitrogen", null),
              new Variant("_helium", fluid("liquid_helium"), "helium", null));
        List<AtelierTile> machines = new ArrayList<>();
        for (int i = 0; i < variants.size(); i++) {
            Variant v = variants.get(i);
            AtelierTile m = caster(h, new BlockPos(1 + i, 2, 1 + 2 * (i % 2)), "industry/casting_machine/cast_ingot_copper" + v.suffix());
            m.fluidInputs().get(0).setFluid(new FluidStack(fluid("molten_copper"), 2_000));
            if (v.coolant() != null) {
                m.fluidInputs().get(1).setFluid(new FluidStack(v.coolant(), 1_000));
            }
            Gt.tool(m, new ItemStack(die, 8));                    // 1.0.5-beta (lot 44) : la matrice dans la case Outil
            machines.add(m);
        }
        h.succeedWhen(() -> {
            for (int i = 0; i < variants.size(); i++) {
                Variant v = variants.get(i);
                AtelierTile m = machines.get(i);
                m.getEnergy().setStored(m.getEnergy().getCapacity());
                String what = "coulee" + (v.suffix().isEmpty() ? " a l'air" : v.suffix());
                Gt.check(count(m, ingot) >= 2, what + " : " + count(m, ingot) + " lingot(s) / 2");
                if (v.suffix().isEmpty()) {
                    Gt.check(count(m, nugget) >= 4, what + " : jets " + count(m, nugget) + " pepite(s) / 4 au moins");
                }
                if (v.hotGas() != null) {
                    Gt.check(gasOut(m, v.hotGas()) > 0, what + " : pas de " + v.hotGas() + " en sortie");
                }
                if (v.hotFluid() != null) {
                    Gt.check(fluidOut(m, v.hotFluid()) > 0, what + " : pas de fluide rendu en sortie");
                }
            }
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_fonderie_sable", timeoutTicks = 1200)
    public static void moule_en_sable(GameTestHelper h) {
        Gt.begin(h);
        Item blank = item("steel_gear_blank"), spent = item("spent_foundry_sand"), mold = item("sand_mold");
        AtelierTile m = caster(h, new BlockPos(2, 2, 2), "industry/casting_machine/sand_cast_gear_blank_steel");
        m.fluidInputs().get(0).setFluid(new FluidStack(fluid("molten_steel"), 4_000));
        m.fluidInputs().get(1).setFluid(new FluidStack(Fluids.WATER, 2_000));
        //1.0.5-beta (lot 46, retour 62 bis : « les moules doivent etre incassables dans les machines ») : UN moule dans la case Outil coule
        //plusieurs fois et reste ; plus de sable use
        Gt.tool(m, new ItemStack(mold, 1));
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            Gt.check(count(m, blank) >= 2, "ebauches au sable : " + count(m, blank) + " / 2");
            Gt.equal(0, count(m, spent), "plus de sable use : le moule ne casse plus");
            Gt.check(Gt.toolSlot(m).getStack().is(mold) && Gt.toolSlot(m).getCount() == 1, "le moule en sable reste dans la case Outil");
            Gt.equal(0, count(m, mold), "aucun moule aux sorties");
        });
    }

    /**
     * 1.11 : le metal fondu (plus de 1 200 degres C) ne passe que dans un cable logistique MKI ou mieux (garni de briques refractaires, il
     * remplace la conduite refractaire) ; le bronze s'arrete a sa propre fonte. Un reseau mixte prend le niveau le plus faible.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_fonderie_conduite", timeoutTicks = 200)
    public static void conduite_refractaire(GameTestHelper h) {
        Gt.begin(h);
        FluidStack steel = new FluidStack(fluid("molten_steel"), 100), tin = new FluidStack(fluid("molten_tin"), 100);
        Gt.check(Accesses.molten(steel), "l'acier fondu doit depasser 1 200 degres C");
        Gt.check(!Accesses.molten(tin) && !Accesses.molten(new FluidStack(fluid("molten_copper"), 100)),
              "l'etain et le cuivre fondus restent sous 1 200 degres C");
        Block basic = PiTransport.logisticCable(CableLevel.BASIC, false).getBlock(), bronze = PiTransport.logisticCable(CableLevel.BRONZE, false).getBlock();
        //Trois lignes de trois : MKI seul, mixte (deux bronzes et un MKI), bronze seul ; un buffer de fluides au bout (fiche : le reseau existe)
        Block[][] lines = {{basic, basic, basic}, {bronze, basic, bronze}, {bronze, bronze, bronze}};
        for (int z = 0; z < lines.length; z++) {
            h.setBlock(new BlockPos(0, 2, 1 + 2 * z), PiTransport.buffer(BufferType.FLUID, CableTier.BASIC).getBlock());
            for (int x = 0; x < 3; x++) {
                h.setBlock(new BlockPos(1 + x, 2, 1 + 2 * z), lines[z][x]);
            }
        }
        //Branchements : chaque cable relit ses voisins (pose dans l'etat par defaut, faces libres)
        for (int z = 0; z < lines.length; z++) {
            for (int x = 0; x < 3; x++) {
                BlockPos rel = new BlockPos(1 + x, 2, 1 + 2 * z);
                if (h.getBlockState(rel).getBlock() instanceof LogisticCableBlock cable) {
                    h.setBlock(rel, cable.refresh(h.getLevel(), h.absolutePos(rel), h.getBlockState(rel)));
                }
            }
        }
        double steelT = steel.getFluid().getFluidType().getTemperature(steel), tinT = tin.getFluid().getFluidType().getTemperature(tin);
        h.succeedWhen(() -> {
            boolean[] wantSteel = {true, false, false};
            for (int z = 0; z < lines.length; z++) {
                LogisticNetwork net = LogisticNetworks.network(h.getLevel(), h.absolutePos(new BlockPos(2, 2, 1 + 2 * z)));
                Gt.check(net != null, "reseau de la ligne " + z + " pas encore construit");
                Gt.equal(wantSteel[z], net.level.accepts(ChannelKind.FLUID, steelT, false), "ligne " + z + " : acier fondu");
                Gt.check(net.level.accepts(ChannelKind.FLUID, tinT, false), "ligne " + z + " : l'etain fondu passe partout");
            }
        });
    }
}

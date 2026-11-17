package fr.pioneers.gametest;

import com.mojang.logging.LogUtils;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.industry.recipe.Remanents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * 1.0.6 (Guillaume, 17/11/2026 : « tu compares toutes les recettes avec tous les slots possibles de l'interface et tu supprimes les slots en
 * trop ») : AUDIT des cases et reservoirs de chaque machine de traitement du catalogue, mesure sur les VRAIES recettes du serveur
 * ({@link IndustryRecipes#of} : recettes propres, famille de tiers, recettes basses, conversions).
 * <ul>
 *     <li>objets en entree : ingredients de la recette (une case par pile de 64), sans l'outil des remanents (case Outil) ;</li>
 *     <li>objets en sortie : produits (une case par pile, rendement de tier du MKIV compris), sans l'outil rendu, plus les tirages ;</li>
 *     <li>liquides et gaz : une jauge par ressource ; les deux sortes partagent les memes jauges (1.0.6) : il en faut le plus grand nombre
 *     de liquides + gaz d'une meme recette.</li>
 * </ul>
 * Le compact ne fait pas les recettes reservees au multibloc : l'io d'une machine est celle de son multibloc s'il en a un (meme catalogue).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP106InterfacesTests {

    private static final Logger LOGGER = LogUtils.getLogger();

    private PatchP106InterfacesTests() {
    }

    /** Besoin d'une liste de recettes : objets entree, sortie ; liquides entree, sortie ; gaz entree, sortie ; ressources entree, sortie. */
    static int[] need(IndustryMachine m, List<IndustrialRecipe> recipes) {
        int[] n = new int[8];
        boolean tool = Remanents.hasToolSlot(m.key);
        for (IndustrialRecipe r : recipes) {
            Remanents.Tool t = tool ? r.tool() : null;
            int in = 0, worn = 0;
            for (int i = 0; i < r.items().size(); i++) {
                if (t != null && t.input() == i) {
                    //outil d'usure (objectif de gravure) : au dernier point, sa forme usee quitte la case Outil vers les sorties AVANT le
                    //produit (RecipeMatch.consume puis produce) : une case de sortie de plus
                    for (var st : r.items().get(i).ingredient().getItems()) {
                        if (st.getItem() instanceof fr.pioneers.tech.industry.recipe.WearTool) {
                            worn = 1;
                            break;
                        }
                    }
                    continue;
                }
                in += Math.max(1, (r.items().get(i).count() + 63) / 64);
            }
            double yield = r.hasTierYield() ? Math.max(1, r.tierYield(3)) : 1;
            int out = 0;
            int lenient = 0;
            for (int i = 0; i < r.results().size(); i++) {
                if (t != null && t.result() == i) {
                    continue;
                }
                if (r.converted() && r.results().get(i).chance() < 1F) {
                    lenient++;                                  //recette basse convertie : tirages en place souple (RecipeMatch.fits)
                    continue;
                }
                var s = r.results().get(i).stack();
                int max = Math.max(1, Math.min(64, s.getMaxStackSize()));
                out += Math.max(1, (int) Math.ceil(s.getCount() * yield / max));
            }
            out += Math.min(lenient, 1) + worn;
            if (r.draws() != null) {
                out += r.draws().max();
            }
            n[0] = Math.max(n[0], in);
            n[1] = Math.max(n[1], out);
            n[2] = Math.max(n[2], r.fluids().size());
            n[3] = Math.max(n[3], r.fluidResults().size());
            n[4] = Math.max(n[4], r.gases().size());
            n[5] = Math.max(n[5], r.gasResults().size());
            n[6] = Math.max(n[6], r.fluids().size() + r.gases().size());
            n[7] = Math.max(n[7], r.fluidResults().size() + r.gasResults().size());
        }
        return n;
    }

    /** Machines dont les cases ne viennent pas de recettes generiques (production calculee, stockage, coeurs propres). */
    static boolean audited(IndustryMachine m) {
        return m.kind == IndustryMachine.Kind.PROCESS || m.kind == IndustryMachine.Kind.GENERATOR;
    }

    /** Exceptions gardees telles quelles (T:industry/io_audit.py) : recycleur (tirages + tirages du skill), silo a objets (stockage). */
    static final java.util.Set<String> KEPT = java.util.Set.of("recycler", "item_silo");

    /** io attendue (objets entree, sortie ; liquides entree, sortie ; gaz entree, sortie) d'apres le besoin des recettes. */
    static int[] expected(IndustryMachine m, int[] n) {
        int fi = n[2] > 0 ? n[6] : 0, gi = n[4] > 0 ? n[6] : 0;
        int fo = m.fractions ? n[3] : n[3] > 0 ? n[7] : 0, go = m.fractions ? n[5] : n[5] > 0 ? n[7] : 0;
        return new int[]{n[0], n[1], fi, fo, gi, go};
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p106_io_audit", timeoutTicks = 40)
    public static void p106_io_au_plus_juste(GameTestHelper h) {
        Gt.begin(h);
        List<String> faults = new ArrayList<>();
        for (IndustryMachine m : IndustryMachine.values()) {
            if (!audited(m) || KEPT.contains(m.key)) {
                continue;
            }
            List<IndustrialRecipe> all = IndustryRecipes.of(h.getLevel(), m);
            if (all.isEmpty()) {
                continue;
            }
            int[] e = expected(m, need(m, all));
            int[] io = {m.itemsIn, m.itemsOut, m.fluidsIn, m.fluidsOut, m.gasesIn, m.gasesOut};
            if (!java.util.Arrays.equals(e, io)) {
                String line = String.format("'%s': (%d, %d, %d, %d, %d, %d) (io actuelle %s)", m.key, e[0], e[1], e[2], e[3], e[4], e[5],
                      java.util.Arrays.toString(io));
                LOGGER.info("IOAUDIT " + line);
                faults.add(line);
            }
            //Jauges partagees : un cote qui prend liquides et gaz a autant de reservoirs des deux (ResourceRows)
            if (m.fluidsIn > 0 && m.gasesIn > 0 && !fr.pioneers.api.machine.resource.ResourceRows.pairedIn(m)) {
                faults.add(m.key + " : entrees de liquide et de gaz non partagees");
            }
            if (!m.fractions && m.fluidsOut > 0 && m.gasesOut > 0 && !fr.pioneers.api.machine.resource.ResourceRows.pairedOut(m)) {
                faults.add(m.key + " : sorties de liquide et de gaz non partagees");
            }
        }
        Gt.check(faults.isEmpty(), "io a ecrire dans T:industry/io_audit.py : " + faults);
        h.succeed();
    }

    /** Gaz du registre par son nom. */
    private static fr.pioneers.api.gas.GasStack gas(String name, long amount) {
        return fr.pioneers.api.gas.GasStack.of(fr.pioneers.core.gas.PiGasTypes.REGISTRY.get().getValue(fr.pioneers.Pioneers.rl(name)), amount);
    }

    /**
     * Jauges partagees en jeu : Reacteur chimique compact (3 jauges en entree, 4 en sortie), recette verrouillee eau + dioxyde d'azote
     * -> acide nitrique (liquide) + monoxyde d'azote (gaz). Le gaz ne va jamais dans la jauge d'un liquide, chaque ressource prend sa jauge,
     * une ressource hors de la recette est refusee, et le liquide et le gaz produits sortent dans deux jauges differentes.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p106_jauges_partagees", timeoutTicks = 400)
    public static void p106_jauges_liquide_ou_gaz(GameTestHelper h) {
        Gt.begin(h);
        IndustryMachine m = IndustryMachine.CHEMICAL_REACTOR;
        Gt.check(fr.pioneers.api.machine.resource.ResourceRows.pairedIn(m) && fr.pioneers.api.machine.resource.ResourceRows.pairedOut(m),
              "reacteur chimique : jauges partagees");
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(2, 2, 2);
        net.minecraft.world.level.block.Block block = fr.pioneers.tech.industry.PiIndustry.ATELIERS.get(m).get(fr.pioneers.logistics.transport.CableTier.ULTIMATE)
              .getBlock();
        h.setBlock(pos, block.defaultBlockState());
        fr.pioneers.tech.industry.atelier.AtelierTile t = (fr.pioneers.tech.industry.atelier.AtelierTile) h.getBlockEntity(pos);
        t.getEnergy().setStored(t.getEnergy().getCapacity());
        Gt.check(t.setLockedRecipe(fr.pioneers.Pioneers.rl("industry/chemical_reactor/nitric_absorption")), "recette de l'acide nitrique absente");
        var fluids = t.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER, null).orElseThrow(IllegalStateException::new);
        var gases = t.getCapability(fr.pioneers.api.PioneersAPI.GAS_HANDLER, null).orElseThrow(IllegalStateException::new);
        Gt.check(gases.insertGas(gas("nitrogen_dioxide", 4_000), fr.pioneers.core.Action.EXECUTE).isEmpty(), "dioxyde d'azote refuse");
        Gt.check(fluids.fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 4_000),
              net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) == 4_000, "eau refusee");
        Gt.check(!gases.insertGas(gas("oxygen", 1_000), fr.pioneers.core.Action.EXECUTE).isEmpty(), "oxygene hors de la recette accepte");
        Gt.equal(0, fluids.fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1_000),
              net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE), "lave hors de la recette acceptee");
        for (int i = 0; i < 3; i++) {
            Gt.check(t.fluidInTanks().get(i).isEmpty() || t.gasInTanks().get(i).getStack().isEmpty(), "jauge " + i + " : liquide et gaz ensemble");
        }
        int used = 0;
        for (int i = 0; i < 3; i++) {
            used += t.fluidInTanks().get(i).isEmpty() && t.gasInTanks().get(i).getStack().isEmpty() ? 0 : 1;
        }
        Gt.equal(2, used, "deux ressources dans deux jauges");
        h.succeedWhen(() -> {
            t.getEnergy().setStored(t.getEnergy().getCapacity());
            boolean acid = false, oxide = false;
            for (int i = 0; i < t.fluidOutTanks().size(); i++) {
                boolean f = !t.fluidOutTanks().get(i).isEmpty(), g = !t.gasOutTanks().get(i).getStack().isEmpty();
                Gt.check(!(f && g), "sortie " + i + " : liquide et gaz ensemble");
                acid |= f;
                oxide |= g;
            }
            Gt.check(acid && oxide, "pas encore d'acide nitrique et de monoxyde d'azote");
        });
    }

    /**
     * Dynamo compacte au standard des machines compactes (Guillaume : « pas d'interface classique ») : onglets Faces (sorties d'energie
     * reglables) et Redstone, case de recharge sous la reserve ; une face fermee ne donne plus l'energie ; redstone qui l'arrete : plus
     * d'excitation.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p106_dynamo_standard", timeoutTicks = 40)
    public static void p106_dynamo_au_standard(GameTestHelper h) {
        Gt.begin(h);
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(2, 2, 2);
        h.setBlock(pos, fr.pioneers.tech.industry.PiIndustry.DYNAMOS.get(fr.pioneers.logistics.transport.CableTier.BASIC).getBlock().defaultBlockState());
        fr.pioneers.tech.industry.atelier.DynamoTile d = (fr.pioneers.tech.industry.atelier.DynamoTile) h.getBlockEntity(pos);
        Gt.check(d.getSideConfig() != null, "dynamo : pas d'onglet Faces");
        Gt.check(d.hasRedstoneTab(), "dynamo : pas d'onglet Redstone");
        Gt.equal(1, d.guiSlots().size(), "dynamo : case de recharge");
        Gt.equal(fr.pioneers.api.machine.StandardLayout.BATTERY_X, d.guiSlots().get(0).x, "case de recharge a la place des machines compactes");
        net.minecraft.core.Direction side = d.getBlockState().getValue(fr.pioneers.tech.kinetics.AxisKineticBlock.AXIS) == net.minecraft.core.Direction.Axis.Y
              ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.UP;
        Gt.check(d.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY, side).isPresent(), "energie par une face ouverte");
        Gt.check(d.generator().rating() > 0, "excitee sans redstone");
        d.handleAction(null, fr.pioneers.core.machine.MachineTile.REDSTONE);   // ignoree -> active au signal
        Gt.check(d.generator().rating() == 0, "redstone : sans signal, la dynamo ne doit plus etre excitee");
        h.succeed();
    }

    /**
     * Moteur electrique (Guillaume, 17/11/2026) : MKI a MKIV, couple croissant par tier, vitesse reglable, sens inversable ; il prend
     * l'energie par ses faces (pas par celles de l'arbre) et tourne jusqu'a la vitesse visee ; sans energie, plus de couple.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p106_moteur", timeoutTicks = 200)
    public static void p106_moteur_electrique(GameTestHelper h) {
        Gt.begin(h);
        for (int t = 1; t < 4; t++) {
            Gt.check(fr.pioneers.tech.industry.atelier.MotorTile.RATED_TORQUE[t] > fr.pioneers.tech.industry.atelier.MotorTile.RATED_TORQUE[t - 1],
                  "couple croissant par tier");
        }
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(2, 2, 2);
        h.setBlock(pos, fr.pioneers.tech.industry.PiIndustry.MOTORS.get(fr.pioneers.logistics.transport.CableTier.BASIC).getBlock().defaultBlockState());
        fr.pioneers.tech.industry.atelier.MotorTile m = (fr.pioneers.tech.industry.atelier.MotorTile) h.getBlockEntity(pos);
        Gt.check(m.getSideConfig() != null && m.hasRedstoneTab() && m.guiSlots().size() == 1, "moteur : faces, redstone, batterie");
        Gt.check(m.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY, net.minecraft.core.Direction.UP).isPresent(),
              "energie par une face");
        Gt.check(!m.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY, net.minecraft.core.Direction.NORTH).isPresent(),
              "pas d'energie par le bout de l'arbre");
        Gt.check(m.sourceTorque() == 0, "sans energie : pas de couple");
        m.motor().energy.insert(200_000, false);
        m.motor().setting(0, 120);
        Gt.equal(120, m.motor().target(), "vitesse visee reglee");
        m.handleCustom(null, fr.pioneers.tech.industry.atelier.MotorTile.FASTER_10);
        Gt.equal(130, m.motor().target(), "+10 tr/min");
        long before = m.motor().energy.getStored();
        h.succeedWhen(() -> {
            Gt.check(Math.abs(m.getSpeed()) > 60, "l'arbre ne tourne pas encore (" + m.getSpeed() + " tr/min)");
            Gt.check(Math.abs(m.getSpeed()) <= 131, "plus vite que la vitesse visee");
            Gt.check(m.motor().energy.getStored() < before, "le moteur ne prend pas d'energie");
        });
    }
}

package fr.pioneers.gametest;

import fr.pioneers.api.machine.unit.FuelGenerator;
import fr.pioneers.api.machine.unit.IndustryProcess;
import fr.pioneers.api.machine.unit.ProcessUnit;
import fr.pioneers.tech.bronze.steam.BoilerPart;
import fr.pioneers.tech.bronze.steam.FireboxTile;
import fr.pioneers.tech.bronze.steam.SteamRules;
import fr.pioneers.core.vanilla.PiVanillaMaterials;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.building.lighting.LightFixtureBlock;
import fr.pioneers.building.lighting.LightLinks;
import fr.pioneers.building.lighting.LightSwitchBlock;
import fr.pioneers.building.lighting.PiLighting;
import fr.pioneers.tech.bronze.PiBronze;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.4-beta, lot 5 (GameTests) : eclairage et producteurs pleins. Un interrupteur allume et eteint ses lumieres liees (une lumiere
 * libre reste allumee, signal de redstone de 15) ; regles de liaison (portee, une lumiere -> un interrupteur, deliaison, interrupteur casse
 * = lumieres liberees) et sauvegarde ; une lumiere liee s'allume aussi par la redstone ; un generateur a combustion plein continue de bruler
 * sans depasser sa contenance (« surplus perdu ») ; une chaudiere de l'age du bronze a reserve pleine continue de bruler et garde sa
 * temperature.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot5Tests {

    private PatchP104Lot5Tests() {
    }

    /** Interrupteur au mur (le mur au sud, il regarde le nord), arret. */
    private static BlockPos wallSwitch(GameTestHelper h, BlockPos at) {
        h.setBlock(at.south(), Blocks.STONE.defaultBlockState());
        h.setBlock(at, PiLighting.SWITCHES.get(LightSwitchBlock.Style.BRONZE).getBlock().defaultBlockState()
              .setValue(LightSwitchBlock.FACE, AttachFace.WALL).setValue(LightSwitchBlock.FACING, Direction.NORTH).setValue(LightSwitchBlock.POWERED, false));
        return h.absolutePos(at);
    }

    /** Lanterne de bronze au sol (allumee : pas encore liee). */
    private static BlockPos lantern(GameTestHelper h, BlockPos at) {
        h.setBlock(at, PiLighting.FIXTURES.get(LightFixtureBlock.Kind.BRONZE_LANTERN).getBlock().defaultBlockState()
              .setValue(LightFixtureBlock.FACE, AttachFace.FLOOR).setValue(LightFixtureBlock.LIT, true));
        return h.absolutePos(at);
    }

    private static boolean lit(ServerLevel level, BlockPos abs) {
        BlockState st = level.getBlockState(abs);
        return st.getBlock() instanceof LightFixtureBlock && st.getValue(LightFixtureBlock.LIT);
    }

    private static void toggle(ServerLevel level, BlockPos sw) {
        BlockState st = level.getBlockState(sw);
        ((LightSwitchBlock) st.getBlock()).toggle(st, level, sw, null);
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104e_switch", timeoutTicks = 40)
    public static void p104_5_interrupteur_allume_eteint(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = h.getLevel();
        BlockPos sw = wallSwitch(h, new BlockPos(4, 2, 4));
        BlockPos a = lantern(h, new BlockPos(2, 1, 2)), b = lantern(h, new BlockPos(6, 1, 2)), free = lantern(h, new BlockPos(4, 1, 7));
        LightLinks data = LightLinks.get(level);
        Gt.equal("light.pioneers.link.added", data.toggleLink(level, sw, a), "liaison de la 1re lanterne");
        Gt.equal("light.pioneers.link.added", data.toggleLink(level, sw, b), "liaison de la 2e lanterne");
        Gt.equal(2, data.lightsOf(sw).size(), "deux lumieres liees");
        Gt.check(!lit(level, a) && !lit(level, b), "interrupteur a l'arret : lumieres liees eteintes");
        Gt.check(lit(level, free), "lumiere libre : toujours allumee");
        toggle(level, sw);
        Gt.check(lit(level, a) && lit(level, b), "marche : toutes les lumieres liees s'allument");
        BlockState st = level.getBlockState(sw);
        Gt.equal(15, st.getSignal(level, sw, Direction.NORTH), "signal de redstone de l'interrupteur en marche");
        Gt.check(data.isOn(sw), "etat memorise : marche");
        toggle(level, sw);
        Gt.check(!lit(level, a) && !lit(level, b), "arret : toutes eteintes");
        Gt.equal(0, level.getBlockState(sw).getSignal(level, sw, Direction.NORTH), "plus de signal a l'arret");
        Gt.check(lit(level, free), "la lumiere libre n'a pas bouge");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104e_links", timeoutTicks = 40)
    public static void p104_5_regles_de_liaison(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = h.getLevel();
        BlockPos sw1 = wallSwitch(h, new BlockPos(2, 2, 4)), sw2 = wallSwitch(h, new BlockPos(6, 2, 4));
        BlockPos a = lantern(h, new BlockPos(4, 1, 1));
        LightLinks data = LightLinks.get(level);
        Gt.equal("light.pioneers.link.far", data.toggleLink(level, sw1, sw1.offset(LightLinks.RANGE + 1, 0, 0)), "au-dela de 32 blocs : refus");
        Gt.equal("light.pioneers.link.added", data.toggleLink(level, sw1, a), "liee au 1er");
        Gt.equal("light.pioneers.link.moved", data.toggleLink(level, sw2, a), "liee au 2e : detachee du 1er");
        Gt.equal(sw2, data.switchOf(a), "une lumiere -> un interrupteur");
        Gt.equal(0, data.lightsOf(sw1).size(), "le 1er n'a plus de lumiere");
        Gt.check(!lit(level, a), "liee a un interrupteur a l'arret : eteinte");
        //sauvegarde : liaison et etat relus
        toggle(level, sw2);
        LightLinks copy = Gt.reload(data);
        Gt.equal(sw2, copy.switchOf(a), "liaison relue");
        Gt.check(copy.isOn(sw2), "etat de l'interrupteur relu");
        toggle(level, sw2);
        //interrupteur casse : lumiere liberee (allumee)
        level.setBlock(sw2, Blocks.AIR.defaultBlockState(), 3);
        Gt.check(data.switchOf(a) == null, "interrupteur casse : lumiere libre");
        Gt.check(lit(level, a), "lumiere liberee : allumee");
        //deliaison
        Gt.equal("light.pioneers.link.added", data.toggleLink(level, sw1, a), "reliee au 1er");
        Gt.equal("light.pioneers.link.removed", data.toggleLink(level, sw1, a), "re-clic : deliee");
        Gt.check(lit(level, a), "deliee : allumee");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104e_redstone", timeoutTicks = 40)
    public static void p104_5_lumiere_liee_pilotable_par_la_redstone(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = h.getLevel();
        BlockPos sw = wallSwitch(h, new BlockPos(2, 2, 6));
        BlockPos a = lantern(h, new BlockPos(5, 1, 3));
        LightLinks data = LightLinks.get(level);
        data.toggleLink(level, sw, a);
        Gt.check(!lit(level, a), "interrupteur a l'arret : eteinte");
        h.setBlock(new BlockPos(6, 1, 3), Blocks.REDSTONE_BLOCK.defaultBlockState());
        Gt.check(lit(level, a), "signal de redstone : allumee malgre l'interrupteur a l'arret");
        h.setBlock(new BlockPos(6, 1, 3), Blocks.AIR.defaultBlockState());
        Gt.check(!lit(level, a), "plus de signal : eteinte");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104e_generator", timeoutTicks = 120)
    public static void p104_5_generateur_plein_continue(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos at = new BlockPos(4, 1, 4);
        BlockState st = PiIndustry.ATELIERS.get(IndustryMachine.SOLID_COMBUSTION_GENERATOR).get(CableTier.BASIC).getBlock().defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        h.setBlock(at, st);
        Gt.check(h.getBlockEntity(at) instanceof AtelierTile, "pas d'atelier de generateur");
        AtelierTile tile = (AtelierTile) h.getBlockEntity(at);
        FuelGenerator gen = ((IndustryProcess) tile.unit()).generator();
        Gt.check(gen != null, "unite de generateur absente");
        tile.itemInputs().get(0).setStack(new ItemStack(PiVanillaMaterials.COAL.get(), 16));
        long capacity = tile.getEnergy().getCapacity();
        tile.getEnergy().insert(capacity, false);
        Gt.equal(capacity, tile.getEnergy().getStored(), "reserve pleine au depart");
        h.runAtTickTime(60, () -> {
            Gt.check(tile.itemInputs().get(0).getStack().getCount() < 16, "reserve pleine : le charbon brule quand meme");
            Gt.check(gen.production() > 0, "toujours en marche (production en cours)");
            Gt.equal(ProcessUnit.WORKING, gen.status(), "etat : en marche (plus « sortie pleine »)");
            Gt.check(tile.getEnergy().getStored() <= capacity, "le stock ne depasse jamais la contenance");
            Gt.check(gen.wasting(), "« surplus perdu » affiche");
            Gt.check(gen.surplus.total() > 0, "le surplus est perdu");
            s.done();
        });
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p104e_boiler", timeoutTicks = 1300)
    public static void p104_5_chaudiere_pleine_continue(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos at = new BlockPos(3, 1, 3);
        //1.0.6 : forme de Guillaume du lot 24 (l'ancienne colonne n'est plus formee) ; at = coin de la forme
        FireboxTile f = MultiblockApi6Tests.boiler(h, at, BoilerPart.MAX_CHIMNEY, null);
        f.guiSlots().get(0).setStack(new ItemStack(PiVanillaMaterials.COAL.get(), 32));
        f.fillWater(3_000, false);
        int[] coal = new int[1];
        h.runAtTickTime(900, () -> {
            Gt.check(f.temperature() >= SteamRules.BOIL_C - 0.01, "ebullition atteinte");
            //reserve pleine
            f.steamVolume().add(f.steamVolume().capacity() - f.steamVolume().steam(), 0);
            f.fillWater(2_000, false);
            coal[0] = f.guiSlots().get(0).getStack().getCount();
        });
        h.runAtTickTime(1250, () -> {
            Gt.check(f.steamVolume().full(), "la reserve est restee pleine (rien ne la vide)");
            Gt.check(f.steamVolume().steam() <= f.steamVolume().capacity() + 1e-6, "jamais au-dela de la contenance");
            Gt.check(f.guiValue(5) > 0, "le feu brule toujours");
            Gt.check(f.guiSlots().get(0).getStack().getCount() < coal[0], "reserve pleine : le charbon brule quand meme");
            Gt.check(f.temperature() >= SteamRules.BOIL_C - 0.01, "la chaudiere garde sa temperature (ebullition)");
            s.done();
        });
    }
}

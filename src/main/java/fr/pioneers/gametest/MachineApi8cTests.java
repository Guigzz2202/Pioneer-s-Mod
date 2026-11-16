package fr.pioneers.gametest;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.tech.bronze.bench.BenchTile;
import fr.pioneers.tech.bronze.bench.BenchType;
import fr.pioneers.space.content.absorber.AbsorberControllerTile;
import fr.pioneers.space.content.spacesim.SpaceSimControllerTile;
import fr.pioneers.space.content.stabilizer.StabilizerControllerTile;
import fr.pioneers.space.content.stabilizer.StabilizerCoreTile;
import fr.pioneers.core.machine.MachineGui;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.equipment.gear.ModulationBenchTile;
import fr.pioneers.logistics.rail.block.ItemLoaderTile;
import fr.pioneers.logistics.rail.block.PostKind;
import fr.pioneers.logistics.rail.block.RailPostTile;
import fr.pioneers.logistics.rail.block.SubstationTile;
import fr.pioneers.logistics.rail.block.WaterCraneTile;
import fr.pioneers.tech.bronze.PiBronze;
import fr.pioneers.equipment.gear.PiGear;
import fr.pioneers.logistics.rail.PiRail;
import fr.pioneers.space.research.PiScienceMachines;
import fr.pioneers.space.tile.PiSpaceMachines;
import fr.pioneers.space.simulation.PiSpaceSim;
import fr.pioneers.space.content.PiStabilizer;
import fr.pioneers.space.tile.TileEntityBiomeAnalyzer;
import fr.pioneers.space.tile.TileEntityChunkLoader;
import fr.pioneers.space.tile.TileEntityDataAntenna;
import fr.pioneers.space.tile.TileEntityForceField;
import fr.pioneers.space.tile.TileEntityGeoSpectrometer;
import fr.pioneers.space.tile.TileEntityLightningCollector;
import fr.pioneers.space.tile.TileEntityOxygenDiffuser;
import fr.pioneers.space.tile.TileEntityResearchTable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * API Machine (lot 8c, GameTests) : machines dont la LOGIQUE est passee dans une unite portee par la tile ({@code MachineTile.carry}) :
 * postes du quai (sous-station, grue a eau, quai a objets, bras de remplissage), machines spatiales a tiers (chunkloader, champ de force,
 * collecteur de foudre, diffuseur), science (spectrometre, analyseur de biome, table de recherche, antenne), etabli de modulation :
 * aller-retour de sauvegarde au format d'avant (memes cles, cases relues dans le meme ordre), valeurs NOMMEES aux indices d'avant ; ecrans
 * passes en vraie v2 (multiblocs cosmiques a 196 de large, noyau du Stabilisateur, etablis dans le cadre v2).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class MachineApi8cTests {

    private MachineApi8cTests() {
    }

    private static <T extends BlockEntity> T place(GameTestHelper h, BlockPos rel, Block block, Class<T> type) {
        h.setBlock(rel, block.defaultBlockState());
        BlockEntity be = h.getBlockEntity(rel);
        Gt.check(type.isInstance(be), "pas de tuile " + type.getSimpleName());
        return type.cast(be);
    }

    /** Sauve {@code a}, relit dans {@code b} (meme bloc) : cles d'avant presentes, sauvegarde identique apres relecture. */
    private static CompoundTag roundTrip(BlockEntity a, BlockEntity b, String... keys) {
        CompoundTag tag = a.saveWithoutMetadata();
        for (String k : keys) {
            Gt.check(tag.contains(k), "cle « " + k + " » manquante (format d'avant le lot 8c)");
        }
        b.load(tag.copy());
        Gt.equal(tag, b.saveWithoutMetadata(), "sauvegarde identique apres relecture");
        return tag;
    }

    /** La logique est dans une unite portee ; valeur nommee {@code key} a l'indice d'avant {@code index}. */
    private static void unit(MachineTile t, UiValues.Key key, int index, String what) {
        Gt.check(t.carried() != null, what + " : logique portee par une unite");
        UiValues ui = t.uiValues();
        Gt.check(ui != null, what + " : valeurs nommees");
        Gt.equal(index, ui.index(key), what + " : " + key.name() + " a l'indice " + index);
    }

    // ================================================================== postes du quai
    @GameTest(template = Gt.SMALL, batch = "pioneers_api8c_rail", timeoutTicks = 40)
    public static void api8c_quai_aller_retour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        SubstationTile sub = place(h, new BlockPos(1, 2, 1), PiRail.SUBSTATIONS.get(0).getBlock(), SubstationTile.class);
        unit(sub, SubstationTile.DRAW, 6, "sous-station");
        WaterCraneTile a = place(h, new BlockPos(3, 2, 1), PiRail.WATER_CRANE.getBlock(), WaterCraneTile.class);
        WaterCraneTile b = place(h, new BlockPos(5, 2, 1), PiRail.WATER_CRANE.getBlock(), WaterCraneTile.class);
        roundTrip(a, b, "poured", "fluids");
        unit(b, WaterCraneTile.POURED, 5, "grue a eau");
        ItemLoaderTile la = place(h, new BlockPos(1, 2, 4), PiRail.ITEM_LOADERS.get(0).getBlock(), ItemLoaderTile.class);
        la.guiSlots().get(4).setStack(new ItemStack(Items.COAL, 7));
        la.setUnloads(true);
        ItemLoaderTile lb = place(h, new BlockPos(3, 2, 4), PiRail.ITEM_LOADERS.get(0).getBlock(), ItemLoaderTile.class);
        roundTrip(la, lb, "unload", "moved", "items");
        Gt.equal(9, lb.guiSlots().size(), "quai a objets : 9 cases");
        Gt.equal(7, lb.guiSlots().get(4).getStack().getCount(), "quai a objets : case 5 relue a sa place");
        Gt.check(lb.unloads(), "quai a objets : sens relu");
        unit(lb, RailPostTile.KIND, 7, "quai a objets");
        RailPostTile pa = place(h, new BlockPos(5, 2, 4), PiRail.POSTS.get(PostKind.FLUID).get(0).getBlock(), RailPostTile.class);
        RailPostTile pb = place(h, new BlockPos(7, 2, 4), PiRail.POSTS.get(PostKind.FLUID).get(0).getBlock(), RailPostTile.class);
        roundTrip(pa, pb, "unload", "moved", "fluids", "gases");
        unit(pb, RailPostTile.DATA_CAPACITY, 11, "bras de remplissage");
        s.done();
    }

    // ================================================================== machines spatiales a tiers
    @GameTest(template = Gt.SMALL, batch = "pioneers_api8c_space", timeoutTicks = 40)
    public static void api8c_spatial_aller_retour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        TileEntityChunkLoader ca = place(h, new BlockPos(1, 2, 1), PiSpaceMachines.CHUNK_LOADER.getBlock(), TileEntityChunkLoader.class);
        TileEntityChunkLoader cb = place(h, new BlockPos(3, 2, 1), PiSpaceMachines.CHUNK_LOADER.getBlock(), TileEntityChunkLoader.class);
        roundTrip(ca, cb, "radius", "loadedRadius");
        unit(cb, TileEntityChunkLoader.COST, 6, "chunkloader");
        Gt.equal(1, cb.guiSlots().size(), "chunkloader : la batterie seule");
        TileEntityForceField fa = place(h, new BlockPos(5, 2, 1), PiSpaceMachines.FORCE_FIELD_GENERATOR.getBlock(), TileEntityForceField.class);
        TileEntityForceField fb = place(h, new BlockPos(7, 2, 1), PiSpaceMachines.FORCE_FIELD_GENERATOR.getBlock(), TileEntityForceField.class);
        roundTrip(fa, fb, "radius", "impacts", "visible");
        unit(fb, TileEntityForceField.FLARE, 10, "champ de force");
        TileEntityLightningCollector la = place(h, new BlockPos(1, 2, 4), PiSpaceMachines.LIGHTNING_COLLECTOR.getBlock(), TileEntityLightningCollector.class);
        TileEntityLightningCollector lb = place(h, new BlockPos(3, 2, 4), PiSpaceMachines.LIGHTNING_COLLECTOR.getBlock(), TileEntityLightningCollector.class);
        roundTrip(la, lb, "strikes");
        unit(lb, TileEntityLightningCollector.AVERAGE, 9, "collecteur de foudre");
        TileEntityOxygenDiffuser da = place(h, new BlockPos(5, 2, 4), PiSpaceMachines.OXYGEN_DIFFUSER.getBlock(), TileEntityOxygenDiffuser.class);
        TileEntityOxygenDiffuser db = place(h, new BlockPos(7, 2, 4), PiSpaceMachines.OXYGEN_DIFFUSER.getBlock(), TileEntityOxygenDiffuser.class);
        roundTrip(da, db, "pressure", "volume", "sealed", "gases");
        unit(db, TileEntityOxygenDiffuser.PRESSURIZED, 8, "diffuseur");
        Gt.check(db.oxygenTank != null, "diffuseur : reservoir d'oxygene de l'unite");
        s.done();
    }

    // ================================================================== science et etabli de modulation
    @GameTest(template = Gt.SMALL, batch = "pioneers_api8c_science", timeoutTicks = 40)
    public static void api8c_science_aller_retour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        TileEntityGeoSpectrometer ga = place(h, new BlockPos(1, 2, 1), PiScienceMachines.GEO_SPECTROMETER.getBlock(), TileEntityGeoSpectrometer.class);
        TileEntityGeoSpectrometer gb = place(h, new BlockPos(3, 2, 1), PiScienceMachines.GEO_SPECTROMETER.getBlock(), TileEntityGeoSpectrometer.class);
        roundTrip(ga, gb, "analysisProgress", "analysisTarget", "analysisCursor");
        unit(gb, UiValues.FE_PER_TICK, 7, "spectrometre");
        Gt.equal(1, gb.getOutputTanks().size(), "spectrometre : un reservoir de sortie");
        TileEntityBiomeAnalyzer ba = place(h, new BlockPos(5, 2, 1), PiScienceMachines.BIOME_ANALYZER.getBlock(), TileEntityBiomeAnalyzer.class);
        unit(ba, UiValues.CELL_COST, 5, "analyseur de biome");
        Gt.equal(TileEntityBiomeAnalyzer.CELL_SLOTS, ba.getOutputTanks().size(), "analyseur de biome : un reservoir par cellule");
        TileEntityResearchTable ra = place(h, new BlockPos(1, 2, 4), PiScienceMachines.RESEARCH_TABLE.getBlock(), TileEntityResearchTable.class);
        TileEntityResearchTable rb = place(h, new BlockPos(3, 2, 4), PiScienceMachines.RESEARCH_TABLE.getBlock(), TileEntityResearchTable.class);
        roundTrip(ra, rb, "workingNode", "unitTicks", "printIndex");
        unit(rb, UiValues.SPEED_X100, 8, "table de recherche");
        TileEntityDataAntenna aa = place(h, new BlockPos(5, 2, 4), PiScienceMachines.DATA_ANTENNA.getBlock(), TileEntityDataAntenna.class);
        TileEntityDataAntenna ab = place(h, new BlockPos(7, 2, 4), PiScienceMachines.DATA_ANTENNA.getBlock(), TileEntityDataAntenna.class);
        roundTrip(aa, ab, "destination");
        unit(ab, UiValues.LISTENING, 8, "antenne");
        ModulationBenchTile ma = place(h, new BlockPos(1, 2, 7), PiGear.BENCH.getBlock(), ModulationBenchTile.class);
        ModulationBenchTile mb = place(h, new BlockPos(3, 2, 7), PiGear.BENCH.getBlock(), ModulationBenchTile.class);
        roundTrip(ma, mb, "items", "energy");
        unit(mb, ModulationBenchTile.CHARGING, 4, "etabli de modulation");
        Gt.equal(3, mb.guiSlots().size(), "etabli de modulation : objet, module, batterie");
        s.done();
    }

    // ================================================================== ecrans en vraie v2
    @GameTest(template = Gt.SMALL, batch = "pioneers_api8c_screens", timeoutTicks = 40)
    public static void api8c_ecrans_v2(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        MachineGui[] cosmic = {place(h, new BlockPos(1, 2, 1), PiStabilizer.CONTROLLER.getBlock(), StabilizerControllerTile.class),
              place(h, new BlockPos(3, 2, 1), PiStabilizer.SINGULARITY_CORE.getBlock(), StabilizerCoreTile.class),
              place(h, new BlockPos(5, 2, 1), PiSpaceSim.CONTROLLER.getBlock(), SpaceSimControllerTile.class)};
        for (MachineGui g : cosmic) {
            Gt.equal(StandardLayout.WIDTH, g.guiWidth(), "multibloc cosmique : panneau de 196");
            Gt.equal(StandardLayout.INVENTORY, g.guiInventory(), "multibloc cosmique : inventaire dans le cadre");
            Gt.check(g.guiParts().stream().noneMatch(p -> p.role().equals("body")), "multibloc cosmique : elements declares (plus de corps encadre)");
        }
        BenchTile bench = place(h, new BlockPos(1, 2, 4), PiBronze.BENCHES.get(BenchType.TOOLMAKER).getBlock(), BenchTile.class);
        Gt.equal(StandardLayout.INVENTORY, bench.guiInventory(), "etabli : inventaire dans le cadre v2");
        Gt.equal(BenchType.TOOLS + BenchType.MATERIALS + 1, bench.guiSlots().size(), "etabli : outils, materiaux, sortie");
        Gt.check(bench.guiSlots().get(0).y >= StandardLayout.TOP, "etabli : cases sous la bande du titre");
        Gt.check(AbsorberControllerTile.CARD.x() + AbsorberControllerTile.CARD.w() < AbsorberControllerTile.THERMO.x(), "absorbeur : carte avant le thermometre");
        s.done();
    }
}

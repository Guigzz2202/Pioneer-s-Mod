package fr.pioneers.gametest;

import fr.pioneers.farm.PiFarm;
import fr.pioneers.farm.crop.Climate;
import fr.pioneers.farm.crop.CropDef;
import fr.pioneers.farm.crop.FarmCropBlock;
import fr.pioneers.farm.crop.Genes;
import fr.pioneers.farm.greenhouse.GreenhouseMaterial;
import fr.pioneers.farm.greenhouse.GreenhouseRegistry;
import fr.pioneers.farm.greenhouse.GreenhouseRegulatorTile;
import fr.pioneers.farm.greenhouse.GreenhouseScan;
import fr.pioneers.farm.greenhouse.PiGreenhouse;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.world.season.SeasonClimate;
import fr.pioneers.world.season.SeasonData;
import fr.pioneers.world.season.SeasonEvents;
import fr.pioneers.world.season.Seasons;
import fr.pioneers.world.season.Seasons.Season;
import fr.pioneers.space.SolarSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 3 (GameTests ; specification « Le monde vivant » §2.2) : serre formee -> une culture hors saison pousse (climat
 * « serre », rien ne tombe dedans) ; trou dans le toit -> non formee, l'erreur commune donne la case exacte ; porte ouverte -> toujours
 * formee ; culture sous l'ancienne serre de verre -> suit le climat du dehors (plus de serre sur Terre) ; serre spatiale (autre astre) :
 * l'ancienne regle tient toujours ; serres voisines a paroi partagee : deux serres, chaque culture dans la sienne.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot3Tests {

    private PatchP105Lot3Tests() {
    }

    private static void keepCalendar(Gt.Scope s) {
        long offset = SeasonData.get(s.server.overworld()).offset(), time = s.level.getDayTime();
        s.onCleanup(() -> {
            s.level.setDayTime(time);
            SeasonEvents.setOffset(s.server, offset);
        });
    }

    /**
     * Serre de bois traite : sol de terre en y, murs de y + 1 a y + 3, toit en y + 4, x0..x1 / z0..z1 (relatif) ; regulateur au milieu de la
     * facade nord, tourne vers le nord (le dehors). Rend la position du regulateur (absolue).
     */
    static BlockPos build(GameTestHelper h, int x0, int z0, int x1, int z1, int y) {
        ServerLevel level = h.getLevel();
        BlockState glass = PiGreenhouse.GLASS.get(GreenhouseMaterial.TREATED).getBlock().defaultBlockState();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                level.setBlock(h.absolutePos(new BlockPos(x, y, z)), Blocks.DIRT.defaultBlockState(), 3);
                for (int dy = 1; dy <= 4; dy++) {
                    boolean edge = x == x0 || x == x1 || z == z0 || z == z1 || dy == 4;
                    level.setBlock(h.absolutePos(new BlockPos(x, y + dy, z)), edge ? glass : Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        BlockPos reg = h.absolutePos(new BlockPos((x0 + x1) / 2, y + 2, z0));
        level.setBlock(reg, PiGreenhouse.REGULATOR.getBlock().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH), 3);
        return reg;
    }

    static GreenhouseRegulatorTile tile(Gt.Scope s, BlockPos reg) {
        Gt.check(s.level.getBlockEntity(reg) instanceof GreenhouseRegulatorTile, "tile du regulateur");
        return (GreenhouseRegulatorTile) s.level.getBlockEntity(reg);
    }

    /** Culture et saison hors climat a cette case (climat du lieu, sans serre) ; la saison est posee. */
    static CropDef hostileCrop(Gt.Scope s, BlockPos pos) {
        SeasonClimate.Place place = Climate.place(s.level, pos);
        for (Season season : Season.values()) {
            SeasonEvents.set(s.server, season, Seasons.serverLength() / 2 + 1);
            for (var e : PiFarm.CROPS.entrySet()) {
                CropDef def = e.getValue().get().def();
                //1.0.6 : pas de riziere (elle demande un sol inonde, la terre labouree du test ne l'est pas) : le lieu du test change a chaque
                //run de la CI (climat de la latitude), et la culture choisie avec lui
                if (!(e.getValue().get() instanceof fr.pioneers.farm.crop.MushroomCropBlock) && !def.paddy() && Climate.status(place, Seasons.now(s.level), def.tMin(),
                      def.tMax(), def.hMin(), def.hMax(), Genes.BASE.tolerance(), true) == Climate.Status.HOSTILE) {
                    return def;
                }
            }
        }
        throw new net.minecraft.gametest.framework.GameTestAssertException("aucune culture hors climat ici, a aucune saison");
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_3_formed", timeoutTicks = 100)
    public static void p105_3_serre_formee_hors_saison(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        keepCalendar(s);
        ServerLevel level = s.level;
        BlockPos reg = build(h, 3, 3, 9, 9, 1);
        BlockPos soil = h.absolutePos(new BlockPos(6, 1, 6)), pos = soil.above();
        level.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
        CropDef def = hostileCrop(s, pos);
        FarmCropBlock block = PiFarm.CROPS.get(def.id()).get();
        level.setBlock(pos, block.defaultBlockState(), 3);
        GreenhouseRegulatorTile t = tile(s, reg);
        Gt.check(t.checkNow(), "serre 7 x 7 fermee : formee");
        Gt.equal(GreenhouseScan.Status.FORMED, t.status(), "etat forme");
        Gt.equal(75L, (long) t.volume(), "volume 5 x 3 x 5 (la terre labouree est le sol)");
        Gt.equal(1L, (long) t.crops(), "une culture comptee");
        Gt.check(GreenhouseRegistry.covers(level, pos), "la culture est dans le registre des serres");
        Gt.equal(Climate.Status.GREENHOUSE, Climate.evaluate(level, pos, def, Genes.BASE, true), "hors saison dehors, climat de serre dedans");
        Gt.check(level.getBlockState(reg).getValue(MachineBlock.ACTIVE), "voyant du regulateur allume");
        //rien ne tombe dans la serre : le toit arrete la pluie et la neige (hauteur des precipitations du jeu)
        Gt.check(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() > pos.getY() + 1, "toit de serre au-dessus : ni pluie ni neige dedans");
        int age = block.getAge(level.getBlockState(pos));
        for (int i = 0; i < 6000 && block.getAge(level.getBlockState(pos)) == age; i++) {
            block.randomTick(level.getBlockState(pos), level, pos, level.random);
        }
        Gt.check(level.getBlockState(pos).is(block) && block.getAge(level.getBlockState(pos)) > age, "la culture hors saison pousse dans la serre ("
              + def.id() + ", " + level.getBlockState(pos) + ")");
        s.done();
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_3_leak", timeoutTicks = 100)
    public static void p105_3_trou_dans_le_toit(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = s.level;
        BlockPos reg = build(h, 3, 3, 9, 9, 1);
        GreenhouseRegulatorTile t = tile(s, reg);
        Gt.check(t.checkNow(), "formee avant le trou");
        BlockPos hole = h.absolutePos(new BlockPos(7, 5, 7));
        level.setBlock(hole, Blocks.AIR.defaultBlockState(), 3);
        Gt.check(!t.checkNow(), "toit troue : plus formee");
        Gt.equal(GreenhouseScan.Status.LEAK, t.status(), "fuite");
        Gt.equal(hole, t.at(), "la fuite est a la case exacte du trou");
        Gt.check(t.exact(), "position sure");
        Gt.equal(GreenhouseScan.Status.LEAK.key(), t.house().error(), "erreur commune : la serre fuit");
        Gt.equal(hole, t.house().at(), "erreur commune : case du trou");
        Gt.check(t.house().expected() != null && t.house().expected().contains("greenhouse_glass"), "attendu : une vitre de serre");
        Gt.check(!GreenhouseRegistry.covers(level, h.absolutePos(new BlockPos(6, 2, 6))), "plus de climat de serre");
        Gt.check(!t.projectionPlans().isEmpty(), "plan des fautes au Maj + clic droit");
        level.setBlock(hole, PiGreenhouse.GLASS.get(GreenhouseMaterial.IRON).getBlock().defaultBlockState(), 3);
        Gt.check(t.checkNow(), "trou bouche (meme d'une vitre de fer) : formee de nouveau");
        s.done();
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_3_door", timeoutTicks = 100)
    public static void p105_3_porte_ouverte(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = s.level;
        BlockPos reg = build(h, 3, 3, 9, 9, 1);
        BlockPos lower = h.absolutePos(new BlockPos(3, 2, 6)), upper = lower.above();
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.OPEN, true);
        //1.0.6 : la porte se pose dans une ouverture, comme un joueur (bas puis haut, DoorBlock.setPlacedBy) : posee a la place de la vitre
        //du bas sous une vitre restee en place, la vitre connectee se mettait a jour (1.0.4-beta lot 23, UPDATE_ALL) et la demi-porte du bas,
        //sans moitie haute, devenait de l'air (DoorBlock.updateShape) : la serre « fuyait » par la porte
        level.setBlock(upper, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(lower, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(lower, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(upper, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        GreenhouseRegulatorTile t = tile(s, reg);
        Gt.check(t.checkNow(), "porte ouverte dans le mur : la serre reste formee (" + t.status() + ", case " + t.house().at() + " soit "
              + (t.house().at() == null ? "?" : t.house().at().subtract(h.absolutePos(BlockPos.ZERO))) + " en relatif : "
              + (t.house().at() == null ? "?" : level.getBlockState(t.house().at())) + ", attendu " + t.house().expected() + ")");
        level.setBlock(lower, level.getBlockState(lower).setValue(DoorBlock.OPEN, false), 3);
        level.setBlock(upper, level.getBlockState(upper).setValue(DoorBlock.OPEN, false), 3);
        Gt.check(t.checkNow(), "porte fermee : formee");
        BlockPos vent = h.absolutePos(new BlockPos(6, 5, 6));
        level.setBlock(vent, PiGreenhouse.VENT.get(GreenhouseMaterial.TREATED).getBlock().defaultBlockState()
              .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true), 3);
        Gt.check(t.checkNow(), "trappe vitree ouverte dans le toit : formee");
        s.done();
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_3_legacy", timeoutTicks = 60)
    public static void p105_3_ancienne_serre(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        keepCalendar(s);
        ServerLevel level = s.level;
        BlockPos soil = h.absolutePos(new BlockPos(6, 1, 6)), pos = soil.above();
        level.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
        level.setBlock(pos.above(3), Blocks.GLASS.defaultBlockState(), 3);
        CropDef def = hostileCrop(s, pos);
        level.setBlock(pos, PiFarm.CROPS.get(def.id()).get().defaultBlockState(), 3);
        Gt.check(Climate.legacyGlass(level, pos), "verre au-dessus de la culture (ancienne serre)");
        Gt.check(!Climate.legacyRule(SolarSystem.fromDimension(level.dimension())), "sur Terre, l'ancienne regle ne vaut plus");
        Gt.check(!Climate.greenhouse(level, pos), "plus une serre");
        Gt.equal(Climate.Status.HOSTILE, Climate.evaluate(level, pos, def, Genes.BASE, true), "la culture suit le climat du dehors (hors saison)");
        Gt.check(FarmCropBlock.tile(level, pos) == null || FarmCropBlock.tile(level, pos).hostileSince() < 0, "rien ne meurt au chargement : delai de grace pas commence");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_3_space", timeoutTicks = 40)
    public static void p105_3_serre_spatiale_inchangee(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = s.level;
        Gt.check(Climate.legacyRule(SolarSystem.Body.MOON) && Climate.legacyRule(SolarSystem.Body.MARS), "Lune, Mars : l'ancienne serre de verre compte");
        Gt.check(!Climate.legacyRule(SolarSystem.Body.EARTH) && !Climate.legacyRule(null), "Terre, Nether, End : non");
        BlockPos soil = h.absolutePos(new BlockPos(4, 1, 4)), pos = soil.above();
        level.setBlock(soil, Blocks.FARMLAND.defaultBlockState(), 3);
        level.setBlock(pos.above(2), Blocks.GLASS.defaultBlockState(), 3);
        Gt.check(Climate.legacyGlass(level, pos), "la regle d'avant (verre sur 12 blocs, tag greenhouse_glass) est intacte");
        Gt.check(fr.pioneers.api.multiblock.HouseShapes.all().stream().anyMatch(x -> x.key().equals("greenhouse_controller")),
              "la serre spatiale (base) garde sa fiche et son plan");
        s.done();
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_3_neighbours", timeoutTicks = 100)
    public static void p105_3_serres_voisines(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = s.level;
        BlockPos west = build(h, 2, 3, 8, 9, 1), east = build(h, 8, 3, 14, 9, 1);
        GreenhouseRegulatorTile a = tile(s, west), b = tile(s, east);
        Gt.check(a.checkNow() && b.checkNow(), "deux serres a paroi partagee : formees toutes les deux");
        BlockPos inA = h.absolutePos(new BlockPos(5, 2, 6)), inB = h.absolutePos(new BlockPos(11, 2, 6));
        Gt.equal(west, GreenhouseRegistry.coveringRegulator(level, inA), "case de la serre ouest : son regulateur");
        Gt.equal(east, GreenhouseRegistry.coveringRegulator(level, inB), "case de la serre est : son regulateur");
        //trou dans la paroi partagee : une seule piece, un seul regulateur la garde
        level.setBlock(h.absolutePos(new BlockPos(8, 2, 6)), Blocks.AIR.defaultBlockState(), 3);
        boolean fa = a.checkNow(), fb = b.checkNow();
        Gt.check(fa ^ fb, "piece commune : une serre formee, l'autre regulateur « deja reglee »");
        GreenhouseRegulatorTile owner = fa ? a : b, other = fa ? b : a;
        Gt.equal(GreenhouseScan.Status.SHARED, other.status(), "l'autre regulateur : deja reglee");
        Gt.check(GreenhouseRegistry.covers(level, inA) && GreenhouseRegistry.covers(level, inB), "toute la piece reste sous serre");
        Gt.check(owner.volume() > 75, "volume de la piece commune");
        s.done();
    }
}

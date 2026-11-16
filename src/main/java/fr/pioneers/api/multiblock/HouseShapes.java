package fr.pioneers.api.multiblock;

import fr.pioneers.tech.bronze.autofoundry.FoundryLayout;
import fr.pioneers.logistics.warehouse.CrateLayout;
import fr.pioneers.logistics.warehouse.PiWarehouse;
import fr.pioneers.tech.bronze.blast.BlastFurnaceTile;
import fr.pioneers.tech.bronze.blast.BlastLayout;
import fr.pioneers.tech.bronze.steam.FireboxTile;
import fr.pioneers.core.common.projector.Blueprint;
import fr.pioneers.core.common.projector.ProjectedStructure;
import fr.pioneers.core.common.projector.ProjectedStructure.Ghost;
import fr.pioneers.core.common.projector.ProjectedStructure.Plan;
import fr.pioneers.space.content.absorber.AbsorberStructure;
import fr.pioneers.space.content.spacesim.SpaceSimShape;
import fr.pioneers.space.content.spacesim.SpaceSimStructure;
import fr.pioneers.space.content.stabilizer.StabilizerStructure;
import fr.pioneers.world.geology.drill.SteamDrillTile;
import fr.pioneers.tech.industry.multi.Structure;
import fr.pioneers.logistics.naval.HarborBlueprint;
import fr.pioneers.logistics.naval.HarborStructure;
import fr.pioneers.space.base.PiBaseMachines;
import fr.pioneers.core.registries.PiBlocks;
import fr.pioneers.tech.bronze.PiBronze;
import fr.pioneers.space.content.PiCosmicBlocks;
import fr.pioneers.logistics.naval.PiNaval;
import fr.pioneers.logistics.robotics.PiRobotics;
import fr.pioneers.space.simulation.PiSpaceSim;
import fr.pioneers.space.content.PiStabilizer;
import fr.pioneers.logistics.robotics.hub.RoboticHubValidator;
import fr.pioneers.space.LaunchPadStructure;
import fr.pioneers.space.assembly.AssemblyHall;
import fr.pioneers.space.assembly.AssemblyHallBlueprint;
import fr.pioneers.space.assembly.AssemblyHallStructure;
import fr.pioneers.space.base.BaseBlueprint;
import fr.pioneers.space.base.BaseLayout;
import fr.pioneers.space.base.BaseStructure;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * API Multibloc (lot 6) : les MULTIBLOCS MAISON (moteurs propres) dans l'API : pour chaque fiche maison ({@code tools/gc/api/multiblocks.py
 * house()}), ses plans et SA verification sur un monde reel ou factice ({@link Structure.View}). Les plans sont tires de la forme (memes
 * constantes que la verification : {@code StabilizerStructure.GRID}, {@link SpaceSimShape}, {@link BlastLayout}, {@link BaseLayout},
 * {@code AssemblyHall}, {@code HarborLayout}, {@code LaunchPadStructure.Size}...) : plus aucun plan ecrit a la main dans
 * {@link ProjectedStructure}. La MEME source sert a la projection (Maj + clic droit : {@link #forBlock}, fournisseur des projections), a la
 * page JEI « Multiblocs » (le hub robotique y entre enfin) et au BANC des multiblocs ({@code tools/api/MultiblockBench}) : chaque plan est
 * construit dans un monde factice et verifie par la VRAIE validation du multibloc, dans les 4 orientations.
 * <p>
 * Les validations maison (stabilisateur, simulateur, absorbeur, hub, bases, halls, port, complexe de lancement) lisent un
 * {@link Structure.View} (le monde reel par {@code Structure.View.of(level)}, inchange) ; haut fourneau et foreuse a vapeur ont une regle
 * statique unique ({@code fault}) partagee par la tile et le banc ; la chaudiere : releve unique {@link FireboxTile#measure}
 * en jeu et au banc (lot 8a). Lot 8a : tous sur le cycle de vie commun {@link MultiblockCore.House} ; {@link #fault} donne l'erreur commune
 * (ou, ATTENDU) lue sur ces plans. 1.0.4-beta (lot 24) : chaudieres, foreuse a vapeur, haut fourneau primitif, fonderie (grande taille),
 * four de potier et four a coke primitif suivent les structures dessinees par Guillaume ({@link HouseNbt}).
 */
public final class HouseShapes {

    /** Une fiche maison : cle, plans (variantes comprises), verification d'un monde construit selon un plan. */
    public interface HouseShape {

        /** Cle de la fiche ({@code MultiblockSpec.key}). */
        String key();

        /** Plans du multibloc (projection, JEI, banc). */
        List<Blueprint> plans();

        /** Verifie le multibloc d'un monde construit selon {@code plans().get(plan)} (variante, ancre, avant). */
        Check check(Structure.View view, int plan, int variant, BlockPos anchor, Direction forward);
    }

    /** Resultat d'une verification : formee, cle de l'erreur, case fautive, case du controleur (pour la case relative). */
    public record Check(boolean formed, @Nullable String error, @Nullable BlockPos at, @Nullable BlockPos controller) {

        static Check ok(@Nullable BlockPos controller) {
            return new Check(true, null, null, controller);
        }

        static Check fail(String error, @Nullable BlockPos at, @Nullable BlockPos controller) {
            return new Check(false, error, at, controller);
        }
    }

    private static List<HouseShape> all;

    private HouseShapes() {
    }

    /** Toutes les fiches maison, dans l'ordre de {@code house()} (hub robotique compris). */
    public static synchronized List<HouseShape> all() {
        if (all == null) {
            all = build();
        }
        return all;
    }

    /** Fournisseur des projections : les plans maison dont {@code block} est le controleur (plans ecrits ici, tires de la forme). */
    public static List<Blueprint> forBlock(Block block) {
        List<Blueprint> out = new ArrayList<>();
        for (HousePlan p : plans()) {
            if (p.controller() == block) {
                out.add(p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ plans tires de la forme

    /** Plan maison : nom, variantes, controleur, dessin d'une variante dans le repere du plan. */
    public static final class HousePlan implements Blueprint {

        private final String name;
        private final int variants;
        private final Supplier<Block> controller;
        @Nullable
        private final IntFunction<String> variantKey;
        private final BiConsumer<Plan, Integer> draw;
        private final int rank;

        HousePlan(int rank, String name, int variants, Supplier<Block> controller, @Nullable IntFunction<String> variantKey, BiConsumer<Plan, Integer> draw) {
            this.name = name;
            this.variants = variants;
            this.controller = controller;
            this.variantKey = variantKey;
            this.draw = draw;
            this.rank = rank;
        }

        /** Rang de la page JEI « Multiblocs » (bronze et vapeur d'abord, puis industriels, port, bases, halls, hub, fin de jeu). */
        public int rank() {
            return rank;
        }

        @Override
        public int variants() {
            return variants;
        }

        @Override
        public Map<BlockPos, Ghost> layout(int variant, BlockPos anchor, Direction forward) {
            Plan plan = new Plan(anchor, forward);
            draw.accept(plan, Math.floorMod(variant, variants));
            return plan.ghosts();
        }

        @Override
        public Block controller() {
            return controller.get();
        }

        /** Meme cle qu'avant le lot 6 ({@code projector.pioneers.structure.<nom>}). */
        @Override
        public String getTranslationKey() {
            return "projector.pioneers.structure." + name;
        }

        @Nullable
        @Override
        public String getVariantKey(int variant) {
            return variantKey == null ? null : variantKey.apply(Math.floorMod(variant, variants));
        }
    }

    private static List<HousePlan> plans;

    static synchronized List<HousePlan> plans() {
        if (plans == null) {
            List<HousePlan> l = new ArrayList<>();
            l.add(STABILIZATION_CHAMBER);
            l.add(STELLAR_CHAMBER);
            l.add(LAUNCH_COMPLEX);
            l.add(SPACE_SIMULATOR);
            l.add(SOLAR_ABSORBER);
            l.add(BOILER);
            l.add(STEAM_DRILL);
            l.add(BLAST_FURNACE);
            l.add(AUTO_FOUNDRY);
            l.add(POTTERY_KILN);
            l.add(COKE_OVEN);
            l.add(WAREHOUSE_CRATE);
            l.add(GREENHOUSE);
            l.add(ROBOTIC_HUB);
            plans = List.copyOf(l);
        }
        return plans;
    }

    /** Stabilisateur spatial : meme globe ({@code StabilizerStructure.GRID}), le noyau change (singularite ou etoile). */
    public static final HousePlan STABILIZATION_CHAMBER = new HousePlan(50000, "stabilization_chamber", 1, PiStabilizer.CONTROLLER::getBlock, null,
          (plan, v) -> chamber(plan, false));
    public static final HousePlan STELLAR_CHAMBER = new HousePlan(50001, "stellar_chamber", 1, PiStabilizer.CONTROLLER::getBlock, null,
          (plan, v) -> chamber(plan, true));
    /** Complexe de lancement : une variante par taille ({@code LaunchPadStructure.Size}). */
    public static final HousePlan LAUNCH_COMPLEX = new HousePlan(50002, "launch_complex", LaunchPadStructure.Size.values().length,
          PiBaseMachines.LAUNCH_PAD_CORE::getBlock, v -> LaunchPadStructure.Size.values()[v].getTranslationKey(),
          (plan, v) -> launchComplex(plan, LaunchPadStructure.Size.values()[v]));
    public static final HousePlan SPACE_SIMULATOR = new HousePlan(50005, "space_simulator", 1, PiSpaceSim.CONTROLLER::getBlock, null,
          (plan, v) -> spaceSimulator(plan));
    /** Absorbeur : 3 tailles (cote 5 / 9 / 13, hauteur 3 / 4 / 5). */
    public static final HousePlan SOLAR_ABSORBER = new HousePlan(50006, "solar_absorber", 3, PiCosmicBlocks.ABSORBER_CONTROLLER::getBlock,
          v -> "projector.pioneers.absorber.size." + v, (plan, v) -> absorber(plan, 5 + 4 * v, 3 + v));
    /**
     * 1.0.4-beta (lot 24) : chaudiere de Guillaume ({@link HouseNbt}), deux variantes : corps en cuivre (avec l'indicateur de niveau du NBT)
     * et corps en acier ; meme forme.
     */
    public static final HousePlan BOILER = new HousePlan(0, "boiler", 2, PiBronze.FIREBOX::getBlock, v -> "projector.pioneers.boiler." + (v == 1 ? "steel" : "copper"),
          (plan, v) -> HouseNbt.draw(plan, v == 1 ? "steel_boiler" : "bronze_boiler", PiBronze.FIREBOX.getBlock(), v == 1));
    /** 1.0.4-beta (lot 24) : foreuse a vapeur de Guillaume ({@link HouseNbt}, train de forage en bronze au centre). */
    public static final HousePlan STEAM_DRILL = new HousePlan(0, "steam_drill", 1, PiBronze.STEAM_DRILL::getBlock, null,
          (plan, v) -> HouseNbt.draw(plan, "steam_drill", PiBronze.STEAM_DRILL.getBlock(), false));
    /** 1.0.4-beta (lot 24) : haut fourneau primitif de Guillaume ({@link HouseNbt} par {@link BlastLayout}). */
    public static final HousePlan BLAST_FURNACE = new HousePlan(0, "blast_furnace", 1, PiBronze.BLAST_FURNACE::getBlock, null,
          (plan, v) -> HouseNbt.draw(plan, BlastLayout.SHAPE, PiBronze.BLAST_FURNACE.getBlock(), false));
    /** 1.0.4-beta (lot 24, Guillaume : « le four a poterie prend une nouvelle structure ») : four de potier ({@link HouseNbt}). */
    public static final HousePlan POTTERY_KILN = new HousePlan(1, "pottery_kiln", 1, PiBronze.POTTERY_KILN::getBlock, null,
          (plan, v) -> HouseNbt.draw(plan, fr.pioneers.tech.bronze.foundry.KilnTile.SHAPE, PiBronze.POTTERY_KILN.getBlock(), false));
    /** 1.0.4-beta (lot 24) : four a coke primitif de Guillaume ({@link HouseNbt}). */
    public static final HousePlan COKE_OVEN = new HousePlan(1, "coke_oven", 1, fr.pioneers.tech.industry.PiIndustry.COKE_OVEN::getBlock, null,
          (plan, v) -> HouseNbt.draw(plan, fr.pioneers.tech.industry.bronze.CokeOvenTile.SHAPE, fr.pioneers.tech.industry.PiIndustry.COKE_OVEN.getBlock(), false));
    /**
     * 1.0.2-beta (lot 4) : fonderie automatique, une variante par taille ({@link FoundryLayout.Size} : 1 x 1 x 2, 2 x 2 x 3, 3 x 3 x 5) ; le
     * controleur (porte du foyer) sur la rangee de devant, au milieu (a gauche pour la taille 2 x 2).
     */
    public static final HousePlan AUTO_FOUNDRY = new HousePlan(1, "auto_foundry", FoundryLayout.Size.values().length, PiBronze.AUTO_FOUNDRY::getBlock,
          v -> "projector.pioneers.auto_foundry.size." + FoundryLayout.Size.of(v).key(), (plan, v) -> autoFoundry(plan, FoundryLayout.Size.of(v)));
    /**
     * 1.0.4-beta (lot 1) : caisse d'entrepot, une variante par longueur ({@link CrateLayout.Length} : 3 x 3 x 3, 3 x 5 x 3, 3 x 7 x 3) ; la
     * trappe (controleur) en (0, 0, 0) au centre du dessus, la caisse dessous, sa longueur vers l'avant du plan ; interieur vide.
     */
    public static final HousePlan WAREHOUSE_CRATE = new HousePlan(2, "warehouse_crate", CrateLayout.Length.values().length, PiWarehouse.CRATE_HATCH::getBlock,
          v -> "projector.pioneers.warehouse_crate.size." + CrateLayout.Length.of(v).key(), (plan, v) -> warehouseCrate(plan, CrateLayout.Length.of(v)));
    /**
     * 1.0.5-beta (lot 3) : SERRE (forme libre : piece scellee de blocs de serre) : un EXEMPLE par matiere (bois traite, fer) : 5 x 5 au
     * sol, murs de 3 blocs, toit ; cadres aux aretes, vitres ailleurs, trappe vitree au milieu du toit, porte en bois sur le flanc gauche,
     * regulateur au milieu de la facade (vers le joueur), sol de terre (interieur de 3 x 3 x 3 blocs d'air). Toute piece fermee de 8 a 4 096
     * blocs d'air convient ({@code farm.greenhouse.GreenhouseScan}) : le plan sert de modele (projection, JEI, banc).
     */
    public static final HousePlan GREENHOUSE = new HousePlan(1, "greenhouse", 2, fr.pioneers.farm.greenhouse.PiGreenhouse.REGULATOR::getBlock,
          v -> "projector.pioneers.greenhouse." + (v == 1 ? "iron" : "treated"), (plan, v) -> greenhouse(plan,
                v == 1 ? fr.pioneers.farm.greenhouse.GreenhouseMaterial.IRON : fr.pioneers.farm.greenhouse.GreenhouseMaterial.TREATED));
    /** Lot 6 : le hub robotique GAGNE un plan (et sa page JEI) : Maj + clic droit sur le port du hub (une seule case du plan). */
    public static final HousePlan ROBOTIC_HUB = new HousePlan(45000, "robotic_hub", 1, PiRobotics.PORT::getBlock, null, (plan, v) -> hub(plan));

    /** Chambres stabilisees : globe de {@code StabilizerStructure.SIZE} (motif {@code GRID}), emetteurs derriere les ports d'alimentation. */
    private static void chamber(Plan plan, boolean stellar) {
        int size = StabilizerStructure.SIZE, half = StabilizerStructure.HALF;
        Block casing = PiStabilizer.CASING.getBlock(), glass = PiStabilizer.GLASS.getBlock(), port = PiStabilizer.PORT.getBlock();
        Block powerPort = PiStabilizer.POWER_PORT.getBlock(), stabilizer = PiStabilizer.EMITTER.getBlock();
        Set<Block> wall = Set.of(casing, glass, port);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    int lx = x - half, lz = z - half;
                    boolean bx = x == 0 || x == size - 1, by = y == 0 || y == size - 1, bz = z == 0 || z == size - 1;
                    if (!bx && !by && !bz) {
                        int ly = y - half;
                        boolean stabilizerCell = Math.abs(lx) + Math.abs(ly) + Math.abs(lz) == half - 1
                                                 && (Math.abs(lx) == half - 1 || Math.abs(ly) == half - 1 || Math.abs(lz) == half - 1);
                        boolean coreCell = y == half && lx == 0 && lz == 0;
                        //Le noyau se pose depuis le controleur : la case reste vide (un noyau y est accepte)
                        plan.put(lx, y, lz, stabilizerCell ? stabilizer : null, coreCell ? Set.of(PiStabilizer.SINGULARITY_CORE.getBlock(),
                              PiStabilizer.STELLAR_CORE.getBlock()) : Set.of());
                        continue;
                    }
                    //Coordonnees dans la face (comme la verification)
                    int h, v;
                    if (bx) {
                        h = y;
                        v = z;
                    } else if (by) {
                        h = x;
                        v = z;
                    } else {
                        h = x;
                        v = y;
                    }
                    int cell = StabilizerStructure.GRID[h][v];
                    if (cell == 0) {
                        continue;
                    }
                    Block block = cell == 1 ? casing : glass;
                    if (y == half && (Math.abs(lx) == half && lz == 0 || Math.abs(lz) == half && lx == 0) || (y == 0 || y == size - 1) && lx == 0 && lz == 0) {
                        block = powerPort;
                    } else if (y == 0 && lx == 0 && lz == 2) {
                        block = port;
                    } else if (y == 1 && lx == 0 && lz == -half) {
                        block = PiStabilizer.CONTROLLER.getBlock();
                    }
                    plan.put(lx, y, lz, block, cell == 1 || block == PiStabilizer.CONTROLLER.getBlock() ? Set.of() : wall);
                }
            }
        }
    }

    /** Complexe de lancement : disque de plaques autour du coeur, tour au bord, du cote oppose au joueur. */
    private static void launchComplex(Plan plan, LaunchPadStructure.Size size) {
        Block plate = PiBlocks.LAUNCH_PAD.getBlock();
        Set<Block> plates = Set.of(plate, PiBlocks.LAUNCH_PORT.getBlock());
        for (int dx = -size.radius; dx <= size.radius; dx++) {
            for (int dz = -size.radius; dz <= size.radius; dz++) {
                if (dx == 0 && dz == 0) {
                    plan.put(0, 0, 0, PiBaseMachines.LAUNCH_PAD_CORE.getBlock(), Set.of());
                } else if (!LaunchPadStructure.isOutside(dx, dz, size.radius)) {
                    plan.put(dx, 0, dz, plate, plates);
                }
            }
        }
        for (int y = 1; y <= size.towerHeight; y++) {
            plan.put(0, y, size.radius, PiBlocks.LAUNCH_TOWER.getBlock(), Set.of());
        }
    }

    /** Simulateur spatial ({@link SpaceSimShape}) : globe pose sur le sol, controleur en bas cote joueur, aretes de boitier, anneau. */
    private static void spaceSimulator(Plan plan) {
        int lift = SpaceSimShape.RADIUS;
        Block casing = PiSpaceSim.CASING.getBlock(), glass = PiSpaceSim.GLASS.getBlock(), port = PiSpaceSim.PORT.getBlock();
        Set<Block> wall = Set.of(casing, glass, port);
        BlockPos controller = SpaceSimShape.controllerSpot(Direction.NORTH);
        for (BlockPos rel : SpaceSimShape.SHELL) {
            int x = rel.getX(), y = rel.getY(), z = rel.getZ();
            Block block;
            Set<Block> accepted = wall;
            if (SpaceSimShape.isProjector(rel)) {
                block = PiSpaceSim.PROJECTOR.getBlock();
                accepted = Set.of();
            } else if (rel.equals(controller)) {
                block = PiSpaceSim.CONTROLLER.getBlock();
                accepted = Set.of();
            } else if (SpaceSimShape.isRib(rel)) {
                block = casing;
                accepted = Set.of();
            } else if (z < 0 && y <= -SpaceSimShape.RADIUS + 3 && Math.abs(x) == 2) {
                block = port;
            } else {
                block = glass;
            }
            plan.put(x, y + lift, z, block, accepted);
        }
        for (BlockPos rel : SpaceSimShape.INTERIOR) {
            plan.put(rel.getX(), rel.getY() + lift, rel.getZ(), null, Set.of());
        }
        for (BlockPos rel : SpaceSimShape.COILS) {
            plan.put(rel.getX(), lift, rel.getZ(), PiSpaceSim.COIL.getBlock(), Set.of());
        }
    }

    /** Absorbeur : caisson creux, aretes et fond en boitier, controleur au milieu de la face avant entre deux ports, capteurs dessus. */
    private static void absorber(Plan plan, int width, int height) {
        int half = width / 2;
        Block casing = PiCosmicBlocks.ABSORBER_CASING.getBlock(), collector = PiCosmicBlocks.ABSORBER_COLLECTOR.getBlock(),
              port = PiCosmicBlocks.ABSORBER_PORT.getBlock();
        Set<Block> wall = Set.of(casing, port);
        for (int x = -half; x <= half; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = -half; z <= half; z++) {
                    boolean edgeX = Math.abs(x) == half, edgeZ = Math.abs(z) == half, top = y == height - 1, bottom = y == 0;
                    int faces = (edgeX ? 1 : 0) + (edgeZ ? 1 : 0) + (top || bottom ? 1 : 0);
                    if (faces >= 2) {
                        plan.put(x, y, z, casing, Set.of());
                    } else if (top) {
                        plan.put(x, y, z, collector, Set.of());
                    } else if (faces == 1) {
                        if (x == 0 && y == 1 && z == -half) {
                            plan.put(x, y, z, PiCosmicBlocks.ABSORBER_CONTROLLER.getBlock(), Set.of());
                        } else {
                            plan.put(x, y, z, (x == -1 || x == 1) && y == 1 && z == -half ? port : casing, wall);
                        }
                    } else {
                        plan.put(x, y, z, null, Set.of());
                    }
                }
            }
        }
    }

    /** 1.0.4-beta (lot 1) : caisse d'entrepot ({@link CrateLayout}) : cadre sur les aretes, panneaux (ou ports) sur les faces, trappe au centre du dessus. */
    private static void warehouseCrate(Plan plan, CrateLayout.Length length) {
        int center = (length.blocks - 1) / 2;
        Set<Block> face = Set.of(PiWarehouse.CRATE_PORT.getBlock());
        for (CrateLayout.Cell c : CrateLayout.cells(length)) {
            Block b = CrateLayout.block(c.kind());
            boolean inside = c.kind() == CrateLayout.Kind.INSIDE;
            plan.put(c.u() - 1, c.v() - 2, c.w() - center, inside ? null : b, c.kind() == CrateLayout.Kind.PANEL ? face : Set.of());
        }
    }

    /** 1.0.2-beta (lot 4) : fonderie automatique ({@link FoundryLayout}) : foyers en bas (le controleur devant), cuve au-dessus. */
    private static void autoFoundry(Plan plan, FoundryLayout.Size size) {
        for (FoundryLayout.Cell c : FoundryLayout.cells(size, FoundryLayout.defaultOffset(size))) {
            plan.put(c.x(), c.y(), c.z(), FoundryLayout.block(c.kind()), Set.of());
        }
    }

    /** 1.0.5-beta (lot 3) : exemple de serre ({@link #GREENHOUSE}). */
    private static void greenhouse(Plan plan, fr.pioneers.farm.greenhouse.GreenhouseMaterial m) {
        Block glass = fr.pioneers.farm.greenhouse.PiGreenhouse.GLASS.get(m).getBlock();
        Block frame = fr.pioneers.farm.greenhouse.PiGreenhouse.FRAME.get(m).getBlock();
        Block vent = fr.pioneers.farm.greenhouse.PiGreenhouse.VENT.get(m).getBlock();
        Block regulator = fr.pioneers.farm.greenhouse.PiGreenhouse.REGULATOR.getBlock();
        Set<Block> wall = fr.pioneers.farm.greenhouse.PiGreenhouse.wallBlocks();
        Set<Block> soil = Set.of(net.minecraft.world.level.block.Blocks.GRASS_BLOCK, net.minecraft.world.level.block.Blocks.FARMLAND,
              net.minecraft.world.level.block.Blocks.COARSE_DIRT, net.minecraft.world.level.block.Blocks.ROOTED_DIRT,
              net.minecraft.world.level.block.Blocks.PODZOL, net.minecraft.world.level.block.Blocks.MUD, net.minecraft.world.level.block.Blocks.MOSS_BLOCK,
              net.minecraft.world.level.block.Blocks.STONE, net.minecraft.world.level.block.Blocks.COBBLESTONE, net.minecraft.world.level.block.Blocks.GRAVEL,
              net.minecraft.world.level.block.Blocks.SAND);
        net.minecraft.world.level.block.state.properties.EnumProperty<Direction.Axis> axis = net.minecraft.world.level.block.RotatedPillarBlock.AXIS;
        for (int x = -2; x <= 2; x++) {
            for (int z = 0; z <= 4; z++) {
                boolean ex = Math.abs(x) == 2, ez = z == 0 || z == 4;
                plan.put(x, 0, z, net.minecraft.world.level.block.Blocks.DIRT, soil);
                for (int y = 1; y <= 4; y++) {
                    boolean top = y == 4;
                    if (!ex && !ez && !top) {
                        continue;
                    }
                    if (ex && ez) {
                        plan.putState(x, y, z, frame.defaultBlockState().setValue(axis, Direction.Axis.Y), wall);
                    } else if (top && (ex || ez)) {
                        plan.putState(x, y, z, frame.defaultBlockState().setValue(axis, ex ? plan.forward().getAxis() : plan.right().getAxis()), wall);
                    } else if (top && x == 0 && z == 2) {
                        plan.putState(x, y, z, vent.defaultBlockState().setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF,
                              net.minecraft.world.level.block.state.properties.Half.TOP), wall);
                    } else if (x == 0 && y == 2 && z == 0) {
                        plan.putState(x, y, z, regulator.defaultBlockState().setValue(fr.pioneers.core.machine.MachineBlock.FACING, plan.forward().getOpposite()),
                              Set.of());
                    } else if (x == -2 && z == 2 && y <= 2) {
                        net.minecraft.world.level.block.state.BlockState door = net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState()
                              .setValue(net.minecraft.world.level.block.DoorBlock.FACING, plan.right())
                              .setValue(net.minecraft.world.level.block.DoorBlock.HALF, y == 1 ? net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                                    : net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
                        plan.putState(x, y, z, door, wall);
                    } else {
                        plan.put(x, y, z, glass, wall);
                    }
                }
            }
        }
    }

    /**
     * Hub robotique ({@link RoboticHubValidator} : cube de {@code WIDTH} x {@code HEIGHT}) : coins en boitiers, parois en boitiers (ou verre
     * du tag {@code pioneers:robotic_hub_glass}), centre vide, une porte de baie sur le flanc oppose au joueur (jamais dessous), un port
     * devant, a hauteur du sol.
     */
    private static void hub(Plan plan) {
        int w = RoboticHubValidator.WIDTH, h = RoboticHubValidator.HEIGHT, r = w / 2;
        Block casing = PiRobotics.CASING.getBlock(), port = PiRobotics.PORT.getBlock(), bay = PiRobotics.BAY.getBlock();
        Set<Block> parts = Set.of(casing, port, bay);
        for (int x = -r; x <= w - 1 - r; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = -r; z <= w - 1 - r; z++) {
                    int extremes = (x == -r || x == w - 1 - r ? 1 : 0) + (y == 0 || y == h - 1 ? 1 : 0) + (z == -r || z == w - 1 - r ? 1 : 0);
                    if (extremes == 0) {
                        plan.put(x, y, z, null, Set.of());
                    } else if (extremes == 3) {
                        plan.put(x, y, z, casing, Set.of());
                    } else if (x == 0 && y == h / 2 && z == w - 1 - r) {
                        plan.put(x, y, z, bay, parts);
                    } else if (x == 0 && y == 0 && z == -r) {
                        plan.put(x, y, z, port, parts);
                    } else {
                        plan.put(x, y, z, casing, parts);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ lot 8a : erreur commune « ou / attendu » lue sur le plan

    /** Lot 8a : case fautive et cle de langue du bloc ATTENDU par le plan de la fiche a cette case. */
    public record Fault(BlockPos at, String expected) {
    }

    /** Cle de l'attendu d'une case qui doit rester vide. */
    public static final String EXPECTED_EMPTY = "gui.pioneers.multiblock.expected_empty";

    /** Fiche maison dont {@code block} est un controleur (premier plan qui le porte), ou null. */
    @Nullable
    public static HouseShape shapeFor(Block block) {
        for (HouseShape s : all()) {
            for (Blueprint p : s.plans()) {
                if (p.controller() == block) {
                    return s;
                }
            }
        }
        return null;
    }

    /** Fiche maison dont un plan contient {@code block} (hub robotique : le maitre est un boitier du cube), ou null. */
    @Nullable
    private static HouseShape shapeContaining(Block block) {
        for (HouseShape s : all()) {
            for (Blueprint p : s.plans()) {
                for (Ghost g : p.layout(0, BlockPos.ZERO, Direction.NORTH).values()) {
                    if (g.state() != null && g.state().getBlock() == block) {
                        return s;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Lot 8a : ERREUR COMMUNE d'un multibloc maison non forme, lue sur les plans de sa fiche (memes plans que la projection, la page JEI et le
     * banc) : chaque plan, variante et orientation est pose pour que sa case « controleur » tombe sur le controleur (le maitre du hub : chacune
     * des cases de son bloc) ; on garde celui qui a le moins de cases fausses. {@code at} connue (la validation maison l'a donnee) : bloc du
     * plan a cette case ; sinon la premiere case fausse du plan retenu. Null : aucun plan ne porte ce bloc ou ne couvre la case.
     */
    @Nullable
    public static Fault fault(Structure.View view, BlockPos controller, net.minecraft.world.level.block.state.BlockState state, @Nullable BlockPos at) {
        Block block = state.getBlock();
        HouseShape shape = shapeFor(block);
        boolean master = false;
        if (shape == null) {
            shape = shapeContaining(block);
            master = true;
        }
        if (shape == null) {
            return null;
        }
        Fault best = null;
        int bestBad = Integer.MAX_VALUE;
        for (Blueprint plan : shape.plans()) {
            if (!master && plan.controller() != block) {
                continue;
            }
            for (int v = 0; v < plan.variants(); v++) {
                for (Direction forward : Direction.Plane.HORIZONTAL) {
                    Map<BlockPos, Ghost> local = plan.layout(v, BlockPos.ZERO, forward);
                    for (Map.Entry<BlockPos, Ghost> c : local.entrySet()) {
                        if (c.getValue().state() == null || c.getValue().state().getBlock() != block) {
                            continue;
                        }
                        BlockPos anchor = controller.subtract(c.getKey());
                        int bad = 0;
                        BlockPos first = null;
                        Ghost firstGhost = null, atGhost = null;
                        for (Map.Entry<BlockPos, Ghost> e : local.entrySet()) {
                            BlockPos p = e.getKey().offset(anchor);
                            if (at != null && p.equals(at)) {
                                atGhost = e.getValue();
                            }
                            if (!view.loaded(p) || e.getValue().isSatisfiedBy(view.state(p))) {
                                continue;
                            }
                            bad++;
                            if (first == null) {
                                first = p;
                                firstGhost = e.getValue();
                            }
                        }
                        if (!(at != null && atGhost == null) && bad < bestBad) {
                            bestBad = bad;
                            Ghost g = at != null ? atGhost : firstGhost;
                            BlockPos where = at != null ? at : first;
                            best = g == null || where == null ? null : new Fault(where, expectedKey(g));
                        }
                        if (!master) {
                            break;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static String expectedKey(Ghost g) {
        return g.state() == null ? EXPECTED_EMPTY : g.state().getBlock().getDescriptionId();
    }

    // ------------------------------------------------------------------ fiches maison et leurs verifications

    /** Case du plan ou se trouve {@code block} (le controleur), ou null. */
    @Nullable
    private static BlockPos cell(Blueprint plan, int variant, BlockPos anchor, Direction forward, Block block) {
        for (Map.Entry<BlockPos, Ghost> e : plan.layout(variant, anchor, forward).entrySet()) {
            if (e.getValue().state() != null && e.getValue().state().getBlock() == block) {
                return e.getKey();
            }
        }
        return null;
    }

    private record Shape(String key, Supplier<List<Blueprint>> source, Checker checker) implements HouseShape {

        @Override
        public List<Blueprint> plans() {
            return source.get();
        }

        @Override
        public Check check(Structure.View view, int plan, int variant, BlockPos anchor, Direction forward) {
            return checker.check(view, plans().get(plan), plan, variant, anchor, forward);
        }
    }

    @FunctionalInterface
    private interface Checker {
        Check check(Structure.View view, Blueprint plan, int index, int variant, BlockPos anchor, Direction forward);
    }

    private static Check base(Structure.View view, BaseLayout.Kind kind, Blueprint plan, int variant, BlockPos anchor, Direction forward) {
        BlockPos c = cell(plan, variant, anchor, forward, plan.controller());
        if (c == null) {
            return Check.fail("controller", anchor, null);
        }
        //Avant du plan = BaseStructure.forward(kind, facade du controleur)
        Direction facing = BaseLayout.controllerOnLeftGable(kind) ? forward.getCounterClockWise() : forward.getOpposite();
        BaseStructure.Result r = BaseStructure.find(view, c, facing, kind);
        if (r == null) {
            return Check.fail("unformed", c, c);
        }
        return r.size() == variant ? Check.ok(c) : Check.fail("size " + r.size(), c, c);
    }

    private static List<HouseShape> build() {
        List<HouseShape> l = new ArrayList<>();
        l.add(new Shape("space_stabilizer_controller", () -> List.of(STABILIZATION_CHAMBER, STELLAR_CHAMBER), (view, plan, i, v, anchor, f) -> {
            BlockPos c = cell(plan, v, anchor, f, plan.controller());
            StabilizerStructure.Result r = StabilizerStructure.validate(view, anchor.above(StabilizerStructure.HALF));
            return r == null ? Check.fail("unloaded", anchor, c) : r.valid() ? Check.ok(c) : Check.fail(r.error(), r.at(), c);
        }));
        l.add(new Shape("space_simulator_controller", () -> List.of(SPACE_SIMULATOR), (view, plan, i, v, anchor, f) -> {
            BlockPos c = cell(plan, v, anchor, f, plan.controller());
            SpaceSimStructure.Result r = c == null ? null : SpaceSimStructure.check(view, anchor.above(SpaceSimShape.RADIUS), c);
            return r == null ? Check.fail("unloaded", anchor, c) : r.valid() ? Check.ok(c) : Check.fail(r.error(), r.at(), c);
        }));
        l.add(new Shape("solar_absorber_controller", () -> List.of(SOLAR_ABSORBER), (view, plan, i, v, anchor, f) -> {
            BlockPos c = cell(plan, v, anchor, f, plan.controller());
            //La facade du controleur regarde l'exterieur (vers le joueur)
            AbsorberStructure.Result r = c == null ? null : AbsorberStructure.check(view, c, f.getOpposite());
            return r == null ? Check.fail("unloaded", anchor, c) : r.valid() ? Check.ok(c) : Check.fail(r.error(), r.at(), c);
        }));
        l.add(new Shape("greenhouse_controller", () -> List.of(BaseBlueprint.of(BaseLayout.Kind.GREENHOUSE)),
              (view, plan, i, v, anchor, f) -> base(view, BaseLayout.Kind.GREENHOUSE, plan, v, anchor, f)));
        l.add(new Shape("mining_outpost_controller", () -> List.of(BaseBlueprint.of(BaseLayout.Kind.GARAGE)),
              (view, plan, i, v, anchor, f) -> base(view, BaseLayout.Kind.GARAGE, plan, v, anchor, f)));
        l.add(new Shape("drone_bay", () -> List.of(BaseBlueprint.of(BaseLayout.Kind.DRONE_BAY)),
              (view, plan, i, v, anchor, f) -> base(view, BaseLayout.Kind.DRONE_BAY, plan, v, anchor, f)));
        l.add(new Shape("launch_pad_core", () -> List.of(LAUNCH_COMPLEX), (view, plan, i, v, anchor, f) -> {
            LaunchPadStructure.Found found = LaunchPadStructure.measure(view, anchor);
            return found == null ? Check.fail("unformed", anchor, anchor) : found.size().ordinal() == v ? Check.ok(anchor)
                  : Check.fail("size " + found.size().name().toLowerCase(Locale.ROOT), anchor, anchor);
        }));
        for (AssemblyHall.Kind kind : AssemblyHall.Kind.values()) {
            String key = kind == AssemblyHall.Kind.ROBOTICS ? "robotic_assembly_bay" : "rocket_integration_hall";
            l.add(new Shape(key, () -> List.of(AssemblyHallBlueprint.of(kind)), (view, plan, i, v, anchor, f) -> {
                BlockPos c = cell(plan, v, anchor, f, plan.controller());
                if (c == null) {
                    return Check.fail("controller", anchor, null);
                }
                AssemblyHallStructure.Result r = AssemblyHallStructure.check(view, kind, c, f.getOpposite());
                return r.formed() ? Check.ok(c) : Check.fail("expected " + (r.expected() == null ? "?" : r.expected().name().toLowerCase(Locale.ROOT)), r.error(), c);
            }));
        }
        l.add(new Shape("harbor", () -> {
            List<Blueprint> out = new ArrayList<>();
            PiNaval.HARBORS.forEach(b -> out.addAll(HarborBlueprint.forBlock(b.getBlock())));
            return out;
        }, (view, plan, i, v, anchor, f) -> {
            //Capitainerie en (0, 0), bassin vers l'avant ; lot 8a : l'eau aussi (le banc lie les tags de fluides et remplit le bassin)
            HarborStructure.Result r = HarborStructure.validate(view, anchor, f, i);
            return r.formed() ? Check.ok(anchor) : Check.fail(r.berth().name().toLowerCase(Locale.ROOT), r.error(), anchor);
        }));
        l.add(new Shape("firebox", () -> List.of(BOILER), (view, plan, i, v, anchor, f) -> {
            //Lot 8a : le RELEVE UNIQUE de la chaudiere (le meme qu'en jeu, FireboxTile.measure) ; 1.0.4-beta (lot 24) : forme de Guillaume
            //(convention des plans maison : forward = facade du controleur)
            FireboxTile.Boiler b = FireboxTile.measure(view, anchor, f);
            return b.complete() ? Check.ok(anchor) : Check.fail(b.problem().isEmpty() ? "unformed" : b.problem(), b.at() != null ? b.at() : anchor.above(), anchor);
        }));
        l.add(new Shape("blast_furnace", () -> List.of(BLAST_FURNACE), (view, plan, i, v, anchor, f) -> {
            BlockPos c = cell(plan, v, anchor, f, plan.controller());
            BlockPos fault = c == null ? anchor : BlastFurnaceTile.fault(view, c, f);
            return fault == null ? Check.ok(c) : Check.fail("blast", fault, c);
        }));
        l.add(new Shape("auto_foundry", () -> List.of(AUTO_FOUNDRY), (view, plan, i, v, anchor, f) -> {
            //1.0.2-beta (lot 4) : le RELEVE UNIQUE de la fonderie (FoundryLayout.survey, le meme qu'en jeu) ; la variante = la taille formee
            BlockPos c = cell(plan, v, anchor, f, plan.controller());
            if (c == null) {
                return Check.fail("controller", anchor, null);
            }
            FoundryLayout.Size want = FoundryLayout.Size.of(v);
            FoundryLayout.Survey s = FoundryLayout.survey(view, c, f);
            if (s.found() != null && s.found().size() == want) {
                return Check.ok(c);
            }
            //Une plus petite fonderie tient dans une grande incomplete : la faute est celle de la taille du plan
            BlockPos fault = FoundryLayout.fault(view, c, f, want, FoundryLayout.defaultOffset(want));
            return Check.fail(s.found() == null ? "auto_foundry" : "size " + s.found().size().key(), fault == null ? s.fault() : fault, c);
        }));
        l.add(new Shape("warehouse_crate", () -> List.of(WAREHOUSE_CRATE), (view, plan, i, v, anchor, f) -> {
            //1.0.4-beta (lot 1) : le RELEVE UNIQUE de la caisse (CrateLayout.survey, le meme qu'en jeu) ; la variante = la longueur formee
            BlockPos c = cell(plan, v, anchor, f, plan.controller());
            if (c == null) {
                return Check.fail("controller", anchor, null);
            }
            CrateLayout.Length want = CrateLayout.Length.of(v);
            CrateLayout.Survey s = CrateLayout.survey(view, c);
            if (s.found() != null && s.found().length() == want) {
                return Check.ok(c);
            }
            BlockPos fault = CrateLayout.fault(view, c, f.getAxis(), want);
            return Check.fail(s.found() == null ? "warehouse_crate" : "length " + s.found().length().key(), fault == null ? s.fault() : fault, c);
        }));
        l.add(new Shape("steam_drill", () -> List.of(STEAM_DRILL), (view, plan, i, v, anchor, f) -> {
            BlockPos fault = SteamDrillTile.fault(view, anchor, f);
            return fault == null ? Check.ok(anchor) : Check.fail("drill", fault, anchor);
        }));
        //1.0.4-beta (lot 24) : four de potier et four a coke primitif devenus multiblocs (structures de Guillaume, HouseNbt)
        l.add(new Shape("pottery_kiln", () -> List.of(POTTERY_KILN), (view, plan, i, v, anchor, f) -> {
            BlockPos fault = fr.pioneers.tech.bronze.foundry.KilnTile.fault(view, anchor, f);
            return fault == null ? Check.ok(anchor) : Check.fail("kiln", fault, anchor);
        }));
        l.add(new Shape("coke_oven", () -> List.of(COKE_OVEN), (view, plan, i, v, anchor, f) -> {
            BlockPos fault = fr.pioneers.tech.industry.bronze.CokeOvenTile.fault(view, anchor, f);
            return fault == null ? Check.ok(anchor) : Check.fail("coke_oven", fault, anchor);
        }));
        //1.0.5-beta (lot 3) : serre : le RELEVE UNIQUE (GreenhouseScan, le meme qu'en jeu) sur le monde du plan
        l.add(new Shape("greenhouse_regulator", () -> List.of(GREENHOUSE), (view, plan, i, v, anchor, f) -> {
            BlockPos c = cell(plan, v, anchor, f, plan.controller());
            if (c == null) {
                return Check.fail("controller", anchor, null);
            }
            fr.pioneers.farm.greenhouse.GreenhouseScan.Grid grid = new fr.pioneers.farm.greenhouse.GreenhouseScan.Grid() {
                @Override
                public fr.pioneers.farm.greenhouse.GreenhouseScan.Kind kind(BlockPos pos) {
                    return view.loaded(pos) ? fr.pioneers.farm.greenhouse.GreenhouseRegulatorTile.classify(view.state(pos))
                          : fr.pioneers.farm.greenhouse.GreenhouseScan.Kind.UNLOADED;
                }

                @Nullable
                @Override
                public Direction facing(BlockPos pos) {
                    net.minecraft.world.level.block.state.BlockState s = view.state(pos);
                    return s.hasProperty(fr.pioneers.core.machine.MachineBlock.FACING) ? s.getValue(fr.pioneers.core.machine.MachineBlock.FACING) : null;
                }
            };
            fr.pioneers.farm.greenhouse.GreenhouseScan.Result r = fr.pioneers.farm.greenhouse.GreenhouseScan.survey(grid, c, grid.facing(c));
            return r.formed() ? Check.ok(c) : Check.fail(r.status().id, r.at() == null ? c : r.at(), c);
        }));
        l.add(new Shape("robotic_hub", () -> List.of(ROBOTIC_HUB), (view, plan, i, v, anchor, f) -> {
            int r = RoboticHubValidator.WIDTH / 2;
            BlockPos min = anchor.offset(-r, 0, -r);
            RoboticHubValidator.Result res = RoboticHubValidator.validate(view, min);
            return res == null ? Check.fail("unloaded", min, min) : res.valid() ? Check.ok(min)
                  : Check.fail(res.error() == null ? "?" : res.error().getString(), min, min);
        }));
        return List.copyOf(l);
    }
}

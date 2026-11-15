package fr.pioneers.api.multiblock;

import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.multi.CasingBlock;
import fr.pioneers.industry.multi.ControllerBlock;
import fr.pioneers.industry.multi.FreeformStructure;
import fr.pioneers.industry.multi.IndustryShape;
import fr.pioneers.industry.multi.IndustryStyle;
import fr.pioneers.industry.multi.ModuleBlock;
import fr.pioneers.industry.multi.NbtRoles;
import fr.pioneers.industry.multi.PartBlock;
import fr.pioneers.industry.multi.PortBlock;
import fr.pioneers.industry.multi.ShapeTemplate;
import fr.pioneers.industry.multi.Signature;
import fr.pioneers.industry.multi.Structure.Error;
import fr.pioneers.industry.multi.Structure.Result;
import fr.pioneers.industry.multi.Structure;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * API Multibloc (lot 5) : LA VALIDATION des multiblocs (code de l'ancienne {@code Structure.validate}, roles par tag). Gabarit
 * ({@link MultiblockSpec.Shape#TEMPLATE}, {@link IndustryShape}) : le controleur est a la base de la facade, facade vers l'exterieur.
 * <ol>
 *     <li>« sondes » du monde : pieces de structure a la suite depuis le controleur (gauche, droite, haut, bas, puis vers l'arriere depuis
 *     le bas) ; elles designent le candidat du gabarit (taille) a verifier, sans tout essayer ;</li>
 *     <li>chaque case du candidat contre les blocs de son ROLE ({@link Role} : classe du bloc ou tag {@code pioneers:multiblock/<role>}) :
 *     enveloppe (carcasses, verre, hublots, ports, pieces signature ; un seul controleur, le notre), parois de contenant (toles), cadres,
 *     interieur (modules du type de la machine, air, eau d'un bassin), roles des reacteurs ;</li>
 *     <li>pieces signature exigees a leur place (cheminee au sommet, tuyeres au premier anneau...) ;</li>
 *     <li>tier = celui de la plus faible piece a tier ({@link MultiblockSpec.TierRule#WEAKEST}) ; interieur entierement rempli de modules
 *     (sauf bassins : eau ; fusion : plasma ; cuves, reacteurs : libre) ; blocs de paroi fonctionnels comptes.</li>
 * </ol>
 * En cas d'echec, l'erreur rapportee est celle du candidat alle le plus loin ; {@link StructureError} la decrit (quoi, ou relatif au
 * controleur, bloc attendu). Forme libre ({@link MultiblockSpec.Shape#FREEFORM}) : {@link FreeformStructure} (pieces reliees).
 * <p>
 * Lot 5 : le controleur est accepte a sa case meme quand le gabarit y met une paroi de contenant (autoclave et dessaleur industriels : leur
 * plan ne se formait jamais, faute trouvee par le banc des multiblocs).
 */
public final class StructureMatcher {

    /** Candidats essayes quand aucun ne correspond exactement aux sondes (structure en construction). */
    private static final int NEAREST = 6;

    private StructureMatcher() {
    }

    /** Validation dans le monde du jeu (gabarit ou forme libre). */
    public static Result match(Level level, BlockPos controller, Direction front, IndustryMachine machine) {
        return match(Structure.View.of(level), controller, front, machine);
    }

    /** Validation sur une vue du monde (le monde du jeu, ou le monde factice du banc des multiblocs). */
    public static Result match(Structure.View level, BlockPos controller, Direction front, IndustryMachine machine) {
        if (machine.freeform) {
            return FreeformStructure.validate(level, controller, machine);
        }
        Direction forward = front.getOpposite(), right = forward.getClockWise();
        List<ShapeTemplate> all = ShapeTemplate.of(machine);
        if (all.isEmpty()) {
            return fail(Error.TOO_SMALL, null, controller, 0);
        }
        int[] progress = new int[1];
        if (all.get(0).nbt) {
            //1.0.4-beta (lot 24) : structure de Guillaume (NBT) : un seul gabarit, verifie case par case (premiere faute rapportee)
            return check(level, controller, forward, right, machine, all.get(0), progress);
        }
        int[] probes = probes(level, controller, forward, right);
        Result best = null;
        int bestProgress = -1;
        boolean exact = false;
        for (ShapeTemplate t : all) {
            if (!t.matches(probes)) {
                continue;
            }
            exact = true;
            progress[0] = 0;
            Result r = check(level, controller, forward, right, machine, t, progress);
            if (r.formed()) {
                return r;
            }
            if (progress[0] > bestProgress) {
                bestProgress = progress[0];
                best = r;
            }
        }
        if (!exact) {
            //Structure en construction : les quelques candidats les plus proches des sondes
            List<ShapeTemplate> near = new ArrayList<>(all);
            near.sort((x, y) -> Integer.compare(x.distance(probes), y.distance(probes)));
            for (int i = 0; i < Math.min(NEAREST, near.size()); i++) {
                progress[0] = 0;
                Result r = check(level, controller, forward, right, machine, near.get(i), progress);
                if (r.formed()) {
                    return r;
                }
                if (progress[0] > bestProgress) {
                    bestProgress = progress[0];
                    best = r;
                }
            }
            if (bestProgress <= 0) {
                //Rien ne ressemble au gabarit : trop petit ou trop grand selon les sondes
                ShapeTemplate small = all.get(all.size() - 1);
                boolean tooSmall = probes[2] < small.probes[2] || probes[0] + probes[1] < small.probes[0] + small.probes[1];
                return fail(tooSmall ? Error.TOO_SMALL : best != null ? best.error() : Error.TOO_BIG, best == null ? null : best.at(), controller, 0);
            }
        }
        return best;
    }

    private static Result fail(Error error, @Nullable BlockPos at, BlockPos controller, int detail) {
        return new Result(error, at, controller, controller, 0, 0, 0, 0, List.of(), 0, 0, 0, null, List.of(), detail);
    }

    /** Piece de structure (sondes, forme libre) : une piece d'enveloppe, de paroi ou de cadre (classe ou tag), un port, un controleur. */
    public static boolean isFrame(BlockState state) {
        return Structure.isFrame(state) || Role.CASING.accepts(state) || Role.GLASS.accepts(state) || Role.FRAME.accepts(state)
               || Role.SHEET.accepts(state) || Role.CONCRETE.accepts(state);
    }

    /** Sondes du monde (meme definition que {@link ShapeTemplate#probes}). */
    private static int[] probes(Structure.View level, BlockPos controller, Direction forward, Direction right) {
        int left = walk(level, controller, right.getOpposite()), r = walk(level, controller, right), up = walk(level, controller, Direction.UP);
        int down = walk(level, controller, Direction.DOWN);
        return new int[]{left, r, up, down, walk(level, controller.below(down), forward)};
    }

    /** Verifie un candidat ; {@code progress[0]} : cases verifiees avant l'echec. */
    private static Result check(Structure.View level, BlockPos controller, Direction forward, Direction right, IndustryMachine machine, ShapeTemplate t,
          int[] progress) {
        int[] cells = t.cells();
        int tier = 99, modules = 0, casings = 0;
        BlockPos firstEmpty = null;
        //1.0.4-beta (lot 24) : gabarit NBT : la structure dit ou vont les modules (baies de serveurs), aucune piece signature (cases propres)
        boolean fill = !t.nbt && Structure.mustFill(machine);
        List<BlockPos> ports = new ArrayList<>(), sheetPorts = new ArrayList<>();
        List<Signature> wanted = t.nbt ? List.of() : machine.signatures;
        int[] found = new int[wanted.size()];
        BlockPos[] zoneAt = new BlockPos[wanted.size()];
        boolean pool = t.shape == IndustryShape.POOL;
        Role moduleRole = machine.module == null ? null : Role.module(machine.module);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int cell : cells) {
            int x = ShapeTemplate.x(cell), y = ShapeTemplate.y(cell), z = ShapeTemplate.z(cell);
            set(pos, controller, forward, right, x, y, z);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
            if (!level.loaded(pos)) {
                return fail(Error.BAD_SHELL, pos.immutable(), controller, 0);
            }
            BlockState state = level.state(pos);
            Block block = state.getBlock();
            int reactorRole = ShapeTemplate.reactor(cell), nbtRole = ShapeTemplate.nbtRole(cell);
            if (x == 0 && y == 0 && z == 0) {
                //Le controleur lui-meme (lot 5 : avant tout role, meme si le gabarit y met une paroi de contenant)
            } else if (block instanceof PortBlock && !ShapeTemplate.portAllowed(cell)) {
                //1.0.4-beta (lot 26) : un port ne remplace qu'une carcasse (lot 30 : ou une tole ; jamais une charpente, un verre...)
                return fail(Error.PORT_NOT_HERE, pos.immutable(), controller, 0);
            } else if (nbtRole != 0) {
                //1.0.4-beta (lot 24) : role propre d'une structure de Guillaume (verre, foyer, bobine, bloc empilable, salle blanche...)
                if (!NbtRoles.fits(nbtRole, state, machine)) {
                    return fail(Error.MISSING_BLOCK, pos.immutable(), controller, nbtRole);
                }
                if (block instanceof PortBlock) {
                    ports.add(pos.immutable());
                }
                int pt = NbtRoles.tier(nbtRole, state);
                if (pt >= 0) {
                    tier = Math.min(tier, pt);
                    casings++;
                }
                if (nbtRole == ShapeTemplate.MODULE) {
                    modules++;
                }
            } else if (t.nbt && ShapeTemplate.interior(cell)) {
                //1.0.4-beta (lot 24) : interieur d'une structure NBT : vide (air, ou eau d'un bassin)
                if (!state.isAir() && !state.is(Blocks.WATER)) {
                    return fail(Error.BAD_INTERIOR, pos.immutable(), controller, 0);
                }
            } else if (reactorRole != 0) {
                //1.0 (lot 3) : bloc fonctionnel de reacteur, verre, ou case vide (le fluide et le plasma sont dessines)
                if (!Structure.reactorFits(reactorRole, state)) {
                    return fail(Error.MISSING_REACTOR, pos.immutable(), controller, reactorRole);
                }
                if (Role.GLASS.accepts(state)) {
                    tier = Math.min(tier, Role.tierOf(state));
                }
                if (reactorRole == IndustryShape.FUEL || reactorRole == IndustryShape.LINING) {
                    modules++;
                }
            } else if (ShapeTemplate.frame(cell)) {
                //1.0 : cadre de structure : n'importe quel tier, le plus faible compte
                if (!Role.FRAME.accepts(state)) {
                    return fail(Error.MISSING_FRAME, pos.immutable(), controller, 0);
                }
                tier = Math.min(tier, Role.tierOf(state));
            } else if (ShapeTemplate.sheet(cell)) {
                //1.0 : paroi d'un contenant : tole (tier), ou verre, bloc de paroi ; port : retire au lot 26, de nouveau permis au lot 30
                //(Guillaume : « on peut autoriser les ports sur les sheetmetals ») : il prend la peau de la tole a la formation (sheetPorts)
                if (Role.SHEET.accepts(state)) {
                    tier = Math.min(tier, Role.tierOf(state));
                } else if (Role.CONCRETE.accepts(state) && machine.shape == IndustryShape.HYPERBOLOID) {
                    //0.20.26 : coque en voile de beton des tours de refroidissement (sans tier)
                } else if (Role.GLASS.accepts(state)) {
                    tier = Math.min(tier, Role.tierOf(state));
                } else if (Role.PORT.accepts(state)) {
                    ports.add(pos.immutable());
                    if (block instanceof PortBlock) {
                        sheetPorts.add(pos.immutable());
                    }
                } else if (!Role.PART.accepts(state)) {
                    return fail(Error.MISSING_SHEET, pos.immutable(), controller, 0);
                }
            } else if (ShapeTemplate.interior(cell)) {
                if (block instanceof ModuleBlock module) {
                    if (machine.module != null && module.module != machine.module) {
                        return fail(Error.WRONG_MODULE, pos.immutable(), controller, 0);
                    }
                    modules++;
                } else if (moduleRole != null && moduleRole.tagOnly(state)) {
                    //Lot 5 : module accepte par le tag de sa sorte
                    modules++;
                } else if (!state.isAir() && !(pool && state.is(Blocks.WATER))) {
                    return fail(Error.BAD_INTERIOR, pos.immutable(), controller, 0);
                } else if (fill && !(pool && state.is(Blocks.WATER)) && firstEmpty == null) {
                    firstEmpty = pos.immutable();
                }
            } else if (ShapeTemplate.isAttached(cell)) {
                //Piece rapportee au-dessus du sommet : la piece exigee, ou rien (piece manquante, verifiee plus bas)
                for (int i = 0; i < wanted.size(); i++) {
                    if (wanted.get(i).zone() == Signature.Zone.TOP) {
                        if (zoneAt[i] == null) {
                            zoneAt[i] = pos.immutable();
                        }
                        if (block instanceof PartBlock part && part.kind == wanted.get(i).part()) {
                            found[i]++;
                        }
                    }
                }
                if (!Role.PART.accepts(state) && !state.isAir()) {
                    return fail(Error.BAD_SHELL, pos.immutable(), controller, 0);
                }
            } else {
                int zones = ShapeTemplate.zones(cell);
                for (int i = 0; i < wanted.size(); i++) {
                    if (zoneAt[i] == null && wanted.get(i).zone().in(zones)) {
                        zoneAt[i] = pos.immutable();
                    }
                }
                if (block instanceof CasingBlock casing) {
                    if (casing.scorched()) {
                        return fail(Error.SCORCHED, pos.immutable(), controller, 0);
                    }
                    tier = Math.min(tier, casing.tier);
                    casings++;
                } else if (block instanceof PortBlock) {
                    ports.add(pos.immutable());
                } else if (block instanceof PartBlock part) {
                    for (int i = 0; i < wanted.size(); i++) {
                        if (wanted.get(i).part() == part.kind && wanted.get(i).zone().in(zones)) {
                            found[i]++;
                        }
                    }
                } else if (block instanceof ControllerBlock) {
                    return fail(Error.OTHER_CONTROLLER, pos.immutable(), controller, 0);
                } else if (Role.GLASS.accepts(state)) {
                    tier = Math.min(tier, Role.tierOf(state));
                } else if (Role.CASING.tagOnly(state)) {
                    //Lot 5 : carcasse accepte par le tag : piece passive au tier 0
                    tier = 0;
                    casings++;
                } else if (!Role.PORT.accepts(state) && !Role.PART.accepts(state)) {
                    return fail(Error.BAD_SHELL, pos.immutable(), controller, 0);
                }
            }
            progress[0]++;
        }
        if (casings == 0 && !(t.nbt && t.fixedTier >= 0)) {
            return fail(Error.NO_CASING, null, controller, 0);
        }
        if (t.nbt && t.fixedTier >= 0 && tier == 99) {
            //1.0.4-beta (lot 24) : structure sans piece a tier (salle blanche seule) : tier fixe
            tier = t.fixedTier;
        }
        //0.17.2 : la fusion n'exige pas de module (couvertures et convertisseurs facultatifs : l'interieur vide est le plasma)
        if (machine.module != null && modules == 0 && machine.kind != IndustryMachine.Kind.FUSION) {
            return fail(Error.NO_MODULE, null, controller, 0);
        }
        for (int i = 0; i < wanted.size(); i++) {
            if (found[i] < wanted.get(i).count()) {
                return fail(Error.MISSING_PART, zoneAt[i], controller, wanted.get(i).detail());
            }
        }
        if (firstEmpty != null) {
            return fail(Error.INCOMPLETE, firstEmpty, controller, 0);
        }
        Set<BlockPos> members = new HashSet<>(cells.length * 2);
        for (int cell : cells) {
            members.add(set(new BlockPos.MutableBlockPos(), controller, forward, right, ShapeTemplate.x(cell), ShapeTemplate.y(cell), ShapeTemplate.z(cell))
                  .immutable());
        }
        //1.0.4-beta (lot 24) : structure NBT : enveloppe et volume d'avant (capacites et vitesses qui en dependaient : inchangees)
        return new Result(Error.NONE, null, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), tier, modules,
              t.nbt ? t.resultShell : t.shellCount(), t.nbt ? t.resultVolume : t.interiorCount(), List.copyOf(ports), t.sx, t.sy, t.sz, members, List.of(), 0,
              List.copyOf(sheetPorts));
    }

    /** Categories du decompte par role ({@link #census}) : enveloppe, parois de contenant, cadres, interieur, reacteur, pieces exigees. */
    public static final int SHELL = 0, SHEET = 1, FRAME = 2, INTERIOR = 3, REACTOR = 4, PARTS = 5, CATEGORIES = 6;

    /**
     * Lot 5 (onglet Structure) : DECOMPTE PAR ROLE du candidat que designent les sondes (le gabarit forme, ou le plus proche d'une structure
     * en construction) : pour chaque categorie, cases deja bonnes et cases du gabarit, empaquetees {@code bonnes | total << 16} ; puis les
     * ports poses par type ({@code PortBlock.Type}, 4 bits chacun). Vide pour la forme libre.
     */
    public static long[] census(Structure.View level, BlockPos controller, Direction front, IndustryMachine machine) {
        long[] out = new long[CATEGORIES + 1];
        if (machine.freeform) {
            return out;
        }
        Direction forward = front.getOpposite(), right = forward.getClockWise();
        List<ShapeTemplate> all = ShapeTemplate.of(machine);
        if (all.isEmpty()) {
            return out;
        }
        int[] probes = probes(level, controller, forward, right);
        ShapeTemplate t = all.get(0).nbt ? all.get(0) : null;
        for (ShapeTemplate c : all) {
            if (t == null && c.matches(probes)) {
                t = c;
                break;
            }
        }
        if (t == null) {
            List<ShapeTemplate> near = new ArrayList<>(all);
            near.sort((x, y) -> Integer.compare(x.distance(probes), y.distance(probes)));
            t = near.get(0);
        }
        int[] ok = new int[CATEGORIES], total = new int[CATEGORIES];
        int[] ports = new int[PortBlock.Type.values().length];
        Role moduleRole = machine.module == null ? null : Role.module(machine.module);
        boolean pool = t.shape == IndustryShape.POOL;
        boolean fill = Structure.mustFill(machine);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cell : t.cells()) {
            int x = ShapeTemplate.x(cell), y = ShapeTemplate.y(cell), z = ShapeTemplate.z(cell);
            if (x == 0 && y == 0 && z == 0) {
                continue;
            }
            set(pos, controller, forward, right, x, y, z);
            BlockState state = level.loaded(pos) ? level.state(pos) : Blocks.AIR.defaultBlockState();
            if (state.getBlock() instanceof PortBlock port) {
                ports[port.type.ordinal()] = Math.min(15, ports[port.type.ordinal()] + 1);
            }
            int role = ShapeTemplate.reactor(cell), nbtRole = ShapeTemplate.nbtRole(cell);
            int cat;
            boolean good;
            if (nbtRole != 0) {
                //1.0.4-beta (lot 24) : role propre d'une structure NBT, compte dans sa categorie (enveloppe, interieur, pieces)
                cat = NbtRoles.category(nbtRole);
                good = NbtRoles.fits(nbtRole, state, machine);
            } else if (t.nbt && ShapeTemplate.interior(cell)) {
                cat = INTERIOR;
                good = state.isAir() || state.is(Blocks.WATER);
            } else if (role != 0) {
                cat = REACTOR;
                good = Structure.reactorFits(role, state);
            } else if (ShapeTemplate.frame(cell)) {
                cat = FRAME;
                good = Role.FRAME.accepts(state);
            } else if (ShapeTemplate.sheet(cell)) {
                cat = SHEET;
                good = Role.SHEET.accepts(state) || Role.CONCRETE.accepts(state) && machine.shape == IndustryShape.HYPERBOLOID
                       || Role.PART.accepts(state) || Role.GLASS.accepts(state) || Role.PORT.accepts(state);
            } else if (ShapeTemplate.interior(cell)) {
                cat = INTERIOR;
                boolean module = state.getBlock() instanceof ModuleBlock m ? machine.module == null || m.module == machine.module
                                 : moduleRole != null && moduleRole.tagOnly(state);
                good = module || !fill && (state.isAir() || pool && state.is(Blocks.WATER));
            } else if (ShapeTemplate.isAttached(cell)) {
                continue;
            } else {
                cat = SHELL;
                good = state.getBlock() instanceof CasingBlock c ? !c.scorched() : Role.CASING.accepts(state) || Role.GLASS.accepts(state)
                       || Role.PORT.accepts(state) || Role.PART.accepts(state);
            }
            total[cat]++;
            if (good) {
                ok[cat]++;
            }
        }
        List<Signature> signatures = t.nbt ? List.of() : machine.signatures;
        for (Signature s : signatures) {
            total[PARTS] += s.count();
        }
        //Pieces exigees : comptees par la validation ; ici, les pieces du bon genre posees dans le gabarit (lot 24 : NBT : cases propres)
        for (int cell : signatures.isEmpty() ? new int[0] : t.cells()) {
            set(pos, controller, forward, right, ShapeTemplate.x(cell), ShapeTemplate.y(cell), ShapeTemplate.z(cell));
            if (level.loaded(pos) && level.state(pos).getBlock() instanceof PartBlock part) {
                for (Signature s : signatures) {
                    if (s.part() == part.kind) {
                        ok[PARTS]++;
                        break;
                    }
                }
            }
        }
        ok[PARTS] = Math.min(ok[PARTS], total[PARTS]);
        for (int i = 0; i < CATEGORIES; i++) {
            out[i] = Math.min(0xFFFF, ok[i]) | (long) Math.min(0xFFFF, total[i]) << 16;
        }
        long packed = 0;
        for (int i = 0; i < ports.length; i++) {
            packed |= (long) ports[i] << (4 * i);
        }
        out[CATEGORIES] = packed;
        return out;
    }

    /** Case du monde d'une case du gabarit (relative au controleur : droite, haut, profondeur). */
    public static BlockPos.MutableBlockPos set(BlockPos.MutableBlockPos pos, BlockPos controller, Direction forward, Direction right, int x, int y, int z) {
        return pos.set(controller.getX() + right.getStepX() * x + forward.getStepX() * z, controller.getY() + y,
              controller.getZ() + right.getStepZ() * x + forward.getStepZ() * z);
    }

    /** Case relative au controleur (droite, haut, profondeur) d'une case du monde ; facade {@code front} vers l'exterieur. */
    public static int[] relative(BlockPos controller, Direction front, BlockPos p) {
        Direction forward = front.getOpposite(), right = forward.getClockWise();
        int dx = p.getX() - controller.getX(), dy = p.getY() - controller.getY(), dz = p.getZ() - controller.getZ();
        return new int[]{dx * right.getStepX() + dz * right.getStepZ(), dy, dx * forward.getStepX() + dz * forward.getStepZ()};
    }

    /** Nombre de pieces de structure a la suite depuis {@code from} (exclu) dans une direction (64 au plus). */
    private static int walk(Structure.View level, BlockPos from, Direction direction) {
        int n = 0;
        BlockPos.MutableBlockPos pos = from.mutable();
        while (n < 64) {
            pos.move(direction);
            if (!level.loaded(pos) || !isFrame(level.state(pos))) {
                break;
            }
            n++;
        }
        return n;
    }

    /** Le style peint a la formation (habillage de la famille). */
    public static IndustryStyle style(IndustryMachine machine) {
        return machine.style;
    }
}

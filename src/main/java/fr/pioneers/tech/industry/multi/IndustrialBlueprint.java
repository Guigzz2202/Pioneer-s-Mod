package fr.pioneers.tech.industry.multi;

import fr.pioneers.core.common.projector.Blueprint;
import fr.pioneers.core.common.projector.ProjectedStructure.Ghost;
import fr.pioneers.tech.fusion.FusionType;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.nuclear.ReactorBlock;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.tech.nuclear.PiNuclear;
import fr.pioneers.logistics.transport.CableTier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Plan projete d'un multibloc industriel (Maj + clic droit sur le controleur) : la seule taille de la machine (0.19.22 ; le
 * parallelisme ne depend que du tier ; l'interieur est entierement fait de modules). Enveloppe en carcasses (tout tier, verre, hublot, port ou piece
 * acceptes), verriere sur le toit des halles, pieces signature a leur place, un hublot propose, ports conseilles autour du controleur ;
 * interieur : les modules necessaires en bas, le reste libre. 1.0.4-beta (lot 26) : un port n'est accepte (et conseille) qu'a la place
 * d'une carcasse (lot 30 : ou d'une tole ; {@link ShapeTemplate#portAllowed}) ; le rendu du plan marque ces cases (StructureProjectionRenderer).
 */
public final class IndustrialBlueprint implements Blueprint {

    private static final Map<IndustryMachine, IndustrialBlueprint> ALL = new EnumMap<>(IndustryMachine.class);

    public static synchronized IndustrialBlueprint of(IndustryMachine machine) {
        return ALL.computeIfAbsent(machine, IndustrialBlueprint::new);
    }

    /** Plans du bloc (fournisseur des projections). */
    public static List<IndustrialBlueprint> forBlock(Block block) {
        return block instanceof ControllerBlock controller ? List.of(of(controller.machine)) : List.of();
    }

    private final IndustryMachine machine;
    private final ShapeTemplate[] chosen = new ShapeTemplate[1];

    private IndustrialBlueprint(IndustryMachine machine) {
        this.machine = machine;
    }

    /** 1.11.3 : la machine du plan (ordre de la page « Multiblocs » de JEI). */
    public IndustryMachine machine() {
        return machine;
    }

    /** 0.19.22 : une seule taille par machine (la moyenne), quelle que soit la variante demandee. */
    private ShapeTemplate template(int variant) {
        if (chosen[0] == null) {
            List<ShapeTemplate> all = ShapeTemplate.of(machine);
            chosen[0] = all.isEmpty() ? null : all.get(0);
        }
        return chosen[0];
    }

    @Override
    public int variants() {
        return machine.freeform ? 4 : 1;                                                                      //plate-forme : 4 exemples de pont
    }

    /**
     * Plate-forme sans forme imposee : une proposition de pont carre (carcasses), une colonne montante sur deux cases, controleur et ports au
     * bord. Le joueur peut faire un tout autre pont : seules comptent les pieces reliees et des colonnes qui plongent jusqu'au fond.
     */
    private Map<BlockPos, Ghost> deck(int variant, BlockPos anchor, Direction forward) {
        int risers = Tiers.parallelCap(Math.floorMod(variant, 4)) / Structure.PER_MODULE;
        int side = (int) Math.ceil(Math.sqrt(risers)) * 2 + 1;
        Direction right = forward.getClockWise();
        Map<BlockPos, Ghost> ghosts = new LinkedHashMap<>();
        Set<Block> frame = frame();
        Block casing = PiIndustry.CASINGS.get(CableTier.BASIC).getBlock();
        //1.0.4-beta (lot 23) : la colonne montante est une pompe a colonne (plus de module « Colonne montante »)
        Block module = machine.module != null && PiIndustry.MODULES.containsKey(machine.module) ? PiIndustry.MODULES.get(machine.module).getBlock()
              : fr.pioneers.tech.industry.construction.PiConstruction.STACKED.get("column_pump").get(CableTier.BASIC).getBlock();
        int cx = side / 2, placed = 0;
        for (int x = 0; x < side; x++) {
            for (int z = 0; z < side; z++) {
                BlockPos pos = anchor.offset(right.getStepX() * (x - cx) + forward.getStepX() * z, 0, right.getStepZ() * (x - cx) + forward.getStepZ() * z);
                Block b = casing;
                if (z == 0 && x == cx) {
                    ghosts.put(pos, new Ghost(PiIndustry.controller(machine).getBlock().defaultBlockState(), Set.of()));
                    continue;
                } else if (z == 0 && x == cx - 1) {
                    b = PiIndustry.PORTS.get(PortBlock.Type.ENERGY).getBlock();
                } else if (z == 0 && x == cx + 1) {
                    b = PiIndustry.PORTS.get(PortBlock.Type.FLUID).getBlock();
                } else if (z == 0 && x == cx + 2) {
                    b = PiIndustry.PORTS.get(PortBlock.Type.GAS).getBlock();
                } else if (x % 2 == 1 && z % 2 == 1 && placed < risers) {
                    ghosts.put(pos, new Ghost(module.defaultBlockState(), Set.of()));
                    placed++;
                    continue;
                }
                ghosts.put(pos, new Ghost(b.defaultBlockState(), frame));
            }
        }
        return ghosts;
    }

    /** 1.0 (lot 3) : bloc conseille pour un role de reacteur (vide : la case doit rester vide). */
    private Ghost reactorGhost(int role) {
        ReactorBlock.Kind kind = switch (role) {
            case IndustryShape.FUEL -> machine == IndustryMachine.FAST_REACTOR ? ReactorBlock.Kind.FAST_FUEL_CHANNEL
                  : ReactorBlock.Kind.FUEL_CHANNEL;
            case IndustryShape.COIL -> ReactorBlock.Kind.CONFINEMENT_COIL;
            case IndustryShape.REFLECTOR -> ReactorBlock.Kind.DIAMOND_REFLECTOR;
            case IndustryShape.COLLECTOR -> ReactorBlock.Kind.FRAGMENT_COLLECTOR;
            case IndustryShape.CHAMBER -> ReactorBlock.Kind.VACUUM_CHAMBER;
            case IndustryShape.LINING -> machine == IndustryMachine.STELLARATOR ? ReactorBlock.Kind.DIRECT_CONVERTER
                  : ReactorBlock.Kind.BREEDING_BLANKET;
            case IndustryShape.SOLENOID -> ReactorBlock.Kind.SOLENOID_COIL;
            case IndustryShape.TFCOIL -> ReactorBlock.Kind.TOROIDAL_COIL;
            case IndustryShape.HELIX -> ReactorBlock.Kind.HELICAL_COIL;
            default -> null;
        };
        if (kind != null) {
            return new Ghost(PiNuclear.REACTOR.get(kind).getBlock().defaultBlockState(), Set.of());
        }
        if (role == IndustryShape.GLASS) {
            return new Ghost(PiIndustry.GLASS.getBlock().defaultBlockState(), glasses());
        }
        return new Ghost(null, Set.of());
    }

    /** 0.20.28 : vitres industrielles de tous les tiers. */
    private static Set<Block> glasses() {
        Set<Block> out = new HashSet<>();
        PiIndustry.GLASSES.values().forEach(g -> out.add(g.getBlock()));
        return out;
    }

    /** 1.0 : paroi d'un contenant : toles (tout tier), ports (retires au lot 26, de nouveau au lot 30), verre, blocs de paroi. */
    private static Set<Block> sheets() {
        Set<Block> out = new HashSet<>();
        PiIndustry.SHEETS.values().forEach(f -> out.add(f.getBlock()));
        PiIndustry.PORTS.values().forEach(p -> out.add(p.getBlock()));
        out.add(PiIndustry.CONCRETE_SHELL.getBlock());
        out.addAll(glasses());
        PiIndustry.PARTS.values().forEach(p -> out.add(p.getBlock()));
        return out;
    }

    /** 1.0 : cadres de structure acceptes (tout tier). */
    private static Set<Block> frames() {
        Set<Block> out = new HashSet<>();
        PiIndustry.FRAMES.values().forEach(f -> out.add(f.getBlock()));
        return out;
    }

    private static Set<Block> frame() {
        Set<Block> frame = new HashSet<>();
        PiIndustry.CASINGS.values().forEach(c -> frame.add(c.getBlock()));
        frame.addAll(glasses());
        PiIndustry.PORTS.values().forEach(p -> frame.add(p.getBlock()));
        PiIndustry.PARTS.values().forEach(p -> frame.add(p.getBlock()));
        return frame;
    }

    @Override
    public Map<BlockPos, Ghost> layout(int variant, BlockPos anchor, Direction forward) {
        if (machine.freeform) {
            return deck(variant, anchor, forward);
        }
        ShapeTemplate t = template(Math.floorMod(variant, 4));
        Map<BlockPos, Ghost> ghosts = new LinkedHashMap<>();
        if (t == null) {
            return ghosts;
        }
        Direction right = forward.getClockWise();
        Set<Block> frame = frame();
        Block casing = PiIndustry.CASINGS.get(CableTier.BASIC).getBlock();
        Block glass = PiIndustry.GLASS.getBlock();
        Block module = machine.module == null || !PiIndustry.MODULES.containsKey(machine.module) ? null : PiIndustry.MODULES.get(machine.module).getBlock();
        //1.0 : modules partout a l'interieur (sauf reacteurs : une suggestion comme avant)
        int n = Structure.mustFill(machine) ? Integer.MAX_VALUE : Tiers.parallelCap(Math.floorMod(variant, 4)) / Structure.PER_MODULE;
        if (machine.kind == IndustryMachine.Kind.FUSION) {
            //0.17.2 : couvertures tritigenes (tokamak) ou convertisseurs directs (stellarator) conseilles ; le reste est le plasma
            n = FusionType.suggestedModules(machine.key, t.interiorCount());
        }
        int[] cells = t.cells();
        //Cases reservees : controleur, ports conseilles, pieces signature
        Map<Integer, BlockState> special = new LinkedHashMap<>();
        Set<Integer> shell = new HashSet<>();
        for (int cell : cells) {
            //1.0.4-beta (lot 24) : structure NBT : ports conseilles aussi sur les parois de salle blanche ; lot 26 : SEULEMENT ou un port est
            //permis (carcasse, paroi de salle blanche ; lot 30 : tole aussi)
            if (ShapeTemplate.portAllowed(cell)) {
                shell.add(cell & 0x1FFFFF);
            }
        }
        special.put(ShapeTemplate.pack(0, 0, 0), PiIndustry.controller(machine).getBlock().defaultBlockState());
        Block in0 = machine.itemsIn > 0 ? port(PortBlock.Type.ITEM) : machine.fluidsIn > 0 ? port(PortBlock.Type.FLUID) : machine.gasesIn > 0
              ? port(PortBlock.Type.GAS) : null;
        Block out0 = machine.itemsOut > 0 || machine.kind == IndustryMachine.Kind.DRILL ? port(PortBlock.Type.ITEM) : machine.fluidsOut > 0
              ? port(PortBlock.Type.FLUID) : machine.gasesOut > 0 ? port(PortBlock.Type.GAS) : null;
        boolean energy = suggest(special, shell, ShapeTemplate.pack(0, 1, 0), port(PortBlock.Type.ENERGY));
        boolean in = suggest(special, shell, ShapeTemplate.pack(-1, 0, 0), in0);
        boolean out = suggest(special, shell, ShapeTemplate.pack(1, 0, 0), out0);
        //1.0 : socles plats (tours ajourees, pompe a balancier) : rien au-dessus du controleur, l'energie a cote ou juste derriere
        for (int[] o : new int[][]{{-1, 0, 0}, {-2, 0, 0}, {2, 0, 0}, {-1, 0, 1}, {1, 0, 1}}) {
            if (energy) {
                break;
            }
            energy = suggest(special, shell, ShapeTemplate.pack(o[0], o[1], o[2]), port(PortBlock.Type.ENERGY));
        }
        //1.0.4-beta (lot 26) : places conseillees prises (ou interdites : un port ne remplace qu'une carcasse ou une tole) : la case permise
        //libre la plus proche du controleur
        if (!in && in0 != null) {
            nearest(special, shell, in0);
        }
        if (!out && out0 != null) {
            nearest(special, shell, out0);
        }
        if (!energy) {
            nearest(special, shell, port(PortBlock.Type.ENERGY));
        }
        for (Signature s : t.nbt ? List.<Signature>of() : machine.signatures) {
            placeParts(special, cells, t, s, right, forward);
        }
        int filled = 0;
        for (int cell : cells) {
            int key = cell & 0x1FFFFF;
            BlockPos pos = anchor.offset(right.getStepX() * ShapeTemplate.x(cell) + forward.getStepX() * ShapeTemplate.z(cell), ShapeTemplate.y(cell),
                  right.getStepZ() * ShapeTemplate.x(cell) + forward.getStepZ() * ShapeTemplate.z(cell));
            int reactorRole = ShapeTemplate.reactor(cell), nbtRole = ShapeTemplate.nbtRole(cell);
            if (nbtRole != 0) {
                //1.0.4-beta (lot 24) : role propre d'une structure de Guillaume (NBT) : son bloc MKI, tous les tiers acceptes
                BlockState port = nbtRole == ShapeTemplate.CLEANROOM ? special.get(key) : null;
                ghosts.put(pos, port != null ? new Ghost(port, NbtRoles.accepted(nbtRole))
                      : new Ghost(NbtRoles.ghost(nbtRole, machine).defaultBlockState(), NbtRoles.accepted(nbtRole)));
                continue;
            }
            if (t.nbt && ShapeTemplate.interior(cell)) {
                //1.0.4-beta (lot 24) : interieur d'une structure NBT : vide (air, ou eau d'un bassin)
                ghosts.put(pos, new Ghost(null, Set.of(Blocks.WATER)));
                continue;
            }
            if (reactorRole != 0) {
                ghosts.put(pos, reactorGhost(reactorRole));
                continue;
            }
            if (ShapeTemplate.frame(cell)) {
                ghosts.put(pos, new Ghost(PiIndustry.FRAMES.get(CableTier.BASIC).getBlock().defaultBlockState(), frames()));
                continue;
            }
            if (ShapeTemplate.interior(cell)) {
                if (module != null && filled < n) {
                    //1.0.5-beta (lot 27, retour 33) : modules CONSEILLES que la validation n'exige pas (Structure.validate : tout
                    //l'interieur si mustFill ; sinon un seul, aucun pour la fusion) : facultatifs dans la page JEI « Multiblocs »
                    boolean optional = !Structure.mustFill(machine) && (machine.kind == IndustryMachine.Kind.FUSION || filled > 0);
                    ghosts.put(pos, new Ghost(module.defaultBlockState(), Set.of(), optional));
                    filled++;
                } else {
                    Set<Block> free = new HashSet<>();
                    if (module != null) {
                        free.add(module);
                    }
                    if (t.shape == IndustryShape.POOL) {
                        free.add(Blocks.WATER);
                    }
                    ghosts.put(pos, new Ghost(null, free));
                }
                continue;
            }
            BlockState s = special.get(key);
            if (s != null) {
                boolean exact = s.getBlock() instanceof ControllerBlock || s.getBlock() instanceof PartBlock;
                ghosts.put(pos, new Ghost(s, exact ? Set.of() : frame));
                continue;
            }
            if (ShapeTemplate.sheet(cell)) {
                //1.0 : paroi d'un contenant : toles (tout tier), ports, verre, blocs de paroi ; lot 3 : bandes de verre sur les cuves des reacteurs
                boolean band = (t.shape == IndustryShape.PWR || t.shape == IndustryShape.SFR)
                               && (ShapeTemplate.x(cell) + t.cx == (t.sx - 1) / 2 || ShapeTemplate.z(cell) + t.cz == (t.sz - 1) / 2);
                //0.20.26 : coque des tours de refroidissement en voile de beton
                ghosts.put(pos, new Ghost((band ? PiIndustry.GLASS : t.shape == IndustryShape.HYPERBOLOID ? PiIndustry.CONCRETE_SHELL
                                                                                                          : PiIndustry.SHEETS.get(CableTier.BASIC)).getBlock()
                      .defaultBlockState(), sheets()));
                continue;
            }
            //Verriere : le toit des halles et des fermes
            boolean roof = t.shape == IndustryShape.HALL && t.canonicalY(cell) > t.b
                           //1.9.2 : atelier ferroviaire : fenetres, lanterneau, verrieres des pignons
                           || t.shape == IndustryShape.DEPOT && IndustryShape.depotGlass(t.a, t.b, t.c, t.canonicalX(cell), t.canonicalY(cell), t.canonicalZ(cell));
            ghosts.put(pos, new Ghost((roof ? glass : casing).defaultBlockState(), frame));
        }
        return ghosts;
    }

    private static Block port(PortBlock.Type type) {
        return PiIndustry.PORTS.get(type).getBlock();
    }

    /** 1.0.4-beta (lot 26) : port conseille a la case permise libre la plus proche du controleur (plus bas d'abord, puis la plus a gauche). */
    private static void nearest(Map<Integer, BlockState> special, Set<Integer> shell, Block block) {
        int best = -1;
        long bestScore = Long.MAX_VALUE;
        for (int key : shell) {
            if (special.containsKey(key)) {
                continue;
            }
            int x = ShapeTemplate.x(key), y = ShapeTemplate.y(key), z = ShapeTemplate.z(key);
            long score = ((long) (x * x + y * y + z * z) << 20) + ((long) (y + 64) << 12) + (x + 64) * 64L + z + 64;
            if (score < bestScore) {
                bestScore = score;
                best = key;
            }
        }
        if (best >= 0) {
            special.put(best, block.defaultBlockState());
        }
    }

    private static boolean suggest(Map<Integer, BlockState> special, Set<Integer> shell, int key, @Nullable Block block) {
        if (block == null || !shell.contains(key) || special.containsKey(key)) {
            return false;
        }
        special.put(key, block.defaultBlockState());
        return true;
    }

    /** Pieces signature : reparties regulierement dans leur zone, tournees vers l'exterieur (vers le haut au sommet). */
    private void placeParts(Map<Integer, BlockState> special, int[] cells, ShapeTemplate t, Signature s, Direction right, Direction forward) {
        List<Integer> zone = new ArrayList<>();
        for (int cell : cells) {
            if (!ShapeTemplate.interior(cell) && !ShapeTemplate.special(cell) && s.zone().in(ShapeTemplate.zones(cell))
                && !special.containsKey(cell & 0x1FFFFF)) {
                zone.add(cell);
            }
        }
        if (zone.isEmpty()) {
            return;
        }
        if (t.shape == IndustryShape.TORUS && s.zone() == Signature.Zone.SIDE) {
            //0.17.2 : tore : les pieces de paroi (injecteur de neutres) sur l'anneau exterieur a mi-hauteur, entre les bobines
            List<Integer> outer = new ArrayList<>();
            for (int cell : zone) {
                if (Signature.Zone.OUTER.in(ShapeTemplate.zones(cell))) {
                    outer.add(cell);
                }
            }
            if (outer.size() >= s.count()) {
                zone = outer;
            }
        }
        //Tri par angle autour de l'axe (anneaux), pour repartir les pieces
        double ax = (t.sx - 1) / 2.0 - t.cx, az = (t.sz - 1) / 2.0 - t.cz;
        zone.sort((p, q) -> Double.compare(Math.atan2(ShapeTemplate.z(p) - az, ShapeTemplate.x(p) - ax),
              Math.atan2(ShapeTemplate.z(q) - az, ShapeTemplate.x(q) - ax)));
        int count = Math.min(s.count(), zone.size());
        Block block = PiIndustry.PARTS.get(s.part()).getBlock();
        List<Integer> chosen;
        if (s.zone() == Signature.Zone.RIDGE || s.zone() == Signature.Zone.RIM) {
            chosen = roofCells(zone, count, ax, az);
        } else if (s.zone() == Signature.Zone.LOW_RING) {
            chosen = faceCenters(zone, cells, count, ax, az);
        } else if (s.zone() == Signature.Zone.ROOF) {
            //0.17.1 : dalle : pompes, echangeurs, grappes sur un anneau a mi-rayon, tries par angle
            double half = (t.sx - 1) / 4.0;
            List<Integer> ring = new ArrayList<>();
            for (int cell : zone) {
                double r = Math.sqrt(Math.pow(ShapeTemplate.x(cell) - ax, 2) + Math.pow(ShapeTemplate.z(cell) - az, 2));
                if (Math.abs(r - half) <= 1.0) {
                    ring.add(cell);
                }
            }
            List<Integer> from = ring.size() >= count ? ring : zone;
            chosen = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int offset = from.size() * (s.part().ordinal() % 4) / (4 * Math.max(1, count));
                chosen.add(from.get((i * from.size() / count + offset) % from.size()));
            }
        } else {
            chosen = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                chosen.add(zone.get(i * zone.size() / count % zone.size()));
            }
        }
        for (int cell : chosen) {
            Direction facing = Direction.UP;
            if (s.zone() == Signature.Zone.LOW_RING || s.zone() == Signature.Zone.SIDE || s.zone() == Signature.Zone.EQUATOR
                || s.zone() == Signature.Zone.OUTER || s.zone() == Signature.Zone.INNER) {
                double dx = ShapeTemplate.x(cell) - ax, dz = ShapeTemplate.z(cell) - az;
                facing = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? right : right.getOpposite()) : (dz > 0 ? forward : forward.getOpposite());
                if (s.zone() == Signature.Zone.INNER) {
                    //0.17.2 : anneau interieur du tore : la piece regarde le trou central
                    facing = facing.getOpposite();
                }
            } else if (s.zone() == Signature.Zone.BACK) {
                facing = forward;
            }
            if (s.part().roof()) {
                facing = Direction.UP;
            }
            special.put(cell & 0x1FFFFF, block.defaultBlockState().setValue(PartBlock.FACING, facing));
        }
    }

    /**
     * 0.19.13 : pieces du toit (groupes froids, torcheres...) : sur la dalle, loin des bords (une case de marge si la dalle le permet),
     * reparties d'un bout a l'autre du grand axe sur la ligne du centre ; une seule : au centre. Meme regle que
     * tools/gc/industry/shapes_preview.place_parts (apercus).
     */
    private static List<Integer> roofCells(List<Integer> zone, int count, double ax, double az) {
        Set<Integer> roofKeys = new HashSet<>();
        List<Integer> roof = new ArrayList<>();
        for (int cell : zone) {
            if (Signature.Zone.ROOF.in(ShapeTemplate.zones(cell))) {
                roof.add(cell);
                roofKeys.add(cell & 0x1FFFFF);
            }
        }
        List<Integer> inner = new ArrayList<>();
        for (int cell : roof) {
            int x = ShapeTemplate.x(cell), y = ShapeTemplate.y(cell), z = ShapeTemplate.z(cell);
            if (roofKeys.contains(ShapeTemplate.pack(x + 1, y, z)) && roofKeys.contains(ShapeTemplate.pack(x - 1, y, z))
                && roofKeys.contains(ShapeTemplate.pack(x, y, z + 1)) && roofKeys.contains(ShapeTemplate.pack(x, y, z - 1))) {
                inner.add(cell);
            }
        }
        List<Integer> cand = zone;
        for (List<Integer> better : List.of(roof, inner)) {
            if (better.size() >= count && (count == 1 || span(better) >= 2 * (count - 1))) {
                cand = better;
            }
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int cell : cand) {
            minX = Math.min(minX, ShapeTemplate.x(cell));
            maxX = Math.max(maxX, ShapeTemplate.x(cell));
            minZ = Math.min(minZ, ShapeTemplate.z(cell));
            maxZ = Math.max(maxZ, ShapeTemplate.z(cell));
        }
        boolean longX = maxX - minX >= maxZ - minZ;
        double lo = longX ? minX : minZ, hi = longX ? maxX : maxZ;
        List<Integer> chosen = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double target = count > 1 ? lo + (hi - lo) * i / (count - 1.0) : (lo + hi) / 2.0;
            Integer best = null;
            double bestD = Double.MAX_VALUE;
            for (int cell : cand) {
                if (chosen.contains(cell)) {
                    continue;
                }
                double along = (longX ? ShapeTemplate.x(cell) : ShapeTemplate.z(cell)) - target;
                double across = longX ? ShapeTemplate.z(cell) - az : ShapeTemplate.x(cell) - ax;
                double d = along * along + across * across;
                if (d < bestD) {
                    bestD = d;
                    best = cell;
                }
            }
            if (best != null) {
                chosen.add(best);
            }
        }
        return chosen;
    }

    private static int span(List<Integer> cells) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int cell : cells) {
            minX = Math.min(minX, ShapeTemplate.x(cell));
            maxX = Math.max(maxX, ShapeTemplate.x(cell));
            minZ = Math.min(minZ, ShapeTemplate.z(cell));
            maxZ = Math.max(maxZ, ShapeTemplate.z(cell));
        }
        return cells.isEmpty() ? -1 : Math.max(maxX - minX, maxZ - minZ);
    }

    /**
     * 0.19.13 : pieces de l'anneau bas (bruleurs, tuyeres) : au milieu des faces, jamais dans un angle ; droite, gauche, arriere, puis la
     * facade du controleur en dernier. Meme regle que shapes_preview.place_parts.
     */
    private static List<Integer> faceCenters(List<Integer> zone, int[] cells, int count, double ax, double az) {
        Set<Integer> solid = new HashSet<>();
        for (int cell : cells) {
            solid.add(cell & 0x1FFFFF);
        }
        int[][] order = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        List<List<Integer>> faces = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            faces.add(new ArrayList<>());
        }
        for (int cell : zone) {
            int x = ShapeTemplate.x(cell), y = ShapeTemplate.y(cell), z = ShapeTemplate.z(cell);
            int out = -1, n = 0;
            for (int i = 0; i < 4; i++) {
                if (!solid.contains(ShapeTemplate.pack(x + order[i][0], y, z + order[i][1]))) {
                    out = i;
                    n++;
                }
            }
            if (n == 1) {
                faces.get(out).add(cell);
            }
        }
        for (int i = 0; i < 4; i++) {
            boolean alongZ = order[i][0] != 0;
            faces.get(i).sort((p, q) -> Double.compare(alongZ ? Math.abs(ShapeTemplate.z(p) - az) : Math.abs(ShapeTemplate.x(p) - ax),
                  alongZ ? Math.abs(ShapeTemplate.z(q) - az) : Math.abs(ShapeTemplate.x(q) - ax)));
        }
        List<Integer> chosen = new ArrayList<>();
        boolean progress = true;
        while (chosen.size() < count && progress) {
            progress = false;
            for (List<Integer> face : faces) {
                if (chosen.size() >= count) {
                    break;
                }
                for (int cell : face) {
                    if (!chosen.contains(cell)) {
                        chosen.add(cell);
                        progress = true;
                        break;
                    }
                }
            }
        }
        for (int cell : zone) {
            if (chosen.size() >= count) {
                break;
            }
            if (!chosen.contains(cell)) {
                chosen.add(cell);
            }
        }
        return chosen;
    }

    @Override
    public Block controller() {
        return PiIndustry.controller(machine).getBlock();
    }

    @Override
    public String getTranslationKey() {
        return "block.pioneers." + machine.multiId();
    }

    @Nullable
    @Override
    public String getVariantKey(int variant) {
        return machine.freeform ? "industry.pioneers.parallel." + Math.floorMod(variant, 4) : null;
    }
}

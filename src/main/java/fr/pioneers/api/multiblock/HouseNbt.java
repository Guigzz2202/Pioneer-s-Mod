package fr.pioneers.api.multiblock;

import fr.pioneers.tech.bronze.steam.BoilerPart;
import fr.pioneers.tech.bronze.steam.SteamAccessory;
import fr.pioneers.core.common.projector.ProjectedStructure.Plan;
import fr.pioneers.tech.industry.construction.PiConstruction;
import fr.pioneers.tech.industry.multi.Structure;
import fr.pioneers.core.registries.PiBronze;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 24) : STRUCTURES DES MULTIBLOCS DE L'AGE MECANIQUE ET DU BRONZE dessinees par Guillaume (NBT {@code tools/gc/industry/nbt/dev_v1},
 * table {@link GeneratedHouseNbt}) : four de potier, four a coke primitif, haut fourneau primitif, fonderie primitive (grande taille),
 * foreuse a vapeur, chaudieres (corps en cuivre ou en acier). SOURCE UNIQUE des verifications (tiles, banc des multiblocs, GameTests) et des
 * plans projetes (Maj + clic droit, page JEI « Multiblocs »).
 * <p>
 * Repere du controleur : x a droite (en regardant la facade depuis l'exterieur), y en haut, z vers l'interieur (la facade est en z = 0) ;
 * {@link #at} place une case dans le monde d'apres la FACADE du controleur (son {@code facing}). Lettres : voir {@link GeneratedHouseNbt}.
 * Seule la nature du bloc compte pour la verification (orientation des escaliers, etats peints : ignores) ; le PLAN reprend l'orientation
 * du NBT ({@link Cell#state}, relative a la facade : escaliers, dalle, tuyere, indicateur) ; l'indicateur de niveau ({@code w}) est facultatif ;
 * la cheminee ({@code k}) : au moins le premier bloc, plus haute elle tire mieux (comme avant).
 */
public final class HouseNbt {

    /**
     * Une case de la structure, relative au controleur ({@code K} : le controleur lui-meme) ; {@code state} : orientation du NBT pour le
     * plan (« facing=back,half=bottom », facing relatif a la facade), ou null.
     */
    public record Cell(int x, int y, int z, char letter, @Nullable String state) {
    }

    private static final Map<String, List<Cell>> CELLS = new HashMap<>();

    private HouseNbt() {
    }

    /** Cases de la structure (sans les cases libres), du bas vers le haut. */
    public static synchronized List<Cell> cells(String key) {
        return CELLS.computeIfAbsent(key, k -> {
            GeneratedHouseNbt.Shape s = GeneratedHouseNbt.of(k);
            List<Cell> out = new ArrayList<>();
            if (s == null) {
                return out;
            }
            Map<Integer, String> states = new HashMap<>();
            for (String e : s.states().split(";")) {
                int i = e.indexOf(':');
                if (i > 0) {
                    states.put(Integer.parseInt(e.substring(0, i)), e.substring(i + 1));
                }
            }
            for (int y = 0; y < s.sy(); y++) {
                for (int z = 0; z < s.sz(); z++) {
                    for (int x = 0; x < s.sx(); x++) {
                        char l = s.at(x, y, z);
                        if (l != '.') {
                            out.add(new Cell(x - s.cx(), y - s.cy(), z - s.cz(), l, states.get((y * s.sz() + z) * s.sx() + x)));
                        }
                    }
                }
            }
            return List.copyOf(out);
        });
    }

    /** Premiere case d'une lettre (repere du controleur), ou null. */
    @Nullable
    public static Cell first(String key, char letter) {
        for (Cell c : cells(key)) {
            if (c.letter() == letter) {
                return c;
            }
        }
        return null;
    }

    /** Case du monde d'une case de la structure, pour un controleur en {@code controller} dont la facade regarde {@code front}. */
    public static BlockPos at(BlockPos controller, Direction front, int x, int y, int z) {
        Direction forward = front.getOpposite(), right = forward.getClockWise();
        return controller.offset(right.getStepX() * x + forward.getStepX() * z, y, right.getStepZ() * x + forward.getStepZ() * z);
    }

    public static BlockPos at(BlockPos controller, Direction front, Cell c) {
        return at(controller, front, c.x(), c.y(), c.z());
    }

    /** Le bloc convient-il a la lettre ? */
    public static boolean fits(char letter, BlockState s) {
        Block b = s.getBlock();
        return switch (letter) {
            case 'X' -> b == PiBronze.FIRE_BRICK_FIREBOX.getBlock();
            case 'B' -> b == PiBronze.FIRE_BRICKS.getBlock();
            case 'T' -> b == PiBronze.TUYERE.getBlock();
            case 'V' -> b == PiBronze.FOUNDRY_VAT.getBlock();
            case 'b' -> b == PiBronze.DRILL_BASE.getBlock() || b == PiBronze.DRILL_PORT.getBlock();
            case 'f' -> b == PiBronze.DRILL_FRAME.getBlock();
            case 'c' -> b == PiBronze.DRILL_CROWN.getBlock();
            case 'D' -> b == PiConstruction.DRILL_STRING_BRONZE.getBlock();
            case 'o' -> b == PiBronze.BOILER_PARTS.get(BoilerPart.COPPER_SHELL).getBlock() || b == PiBronze.BOILER_PARTS.get(BoilerPart.STEEL_SHELL).getBlock();
            case 'd' -> b == PiBronze.BOILER_PARTS.get(BoilerPart.STEAM_DOME).getBlock();
            case 'k' -> b == PiBronze.BOILER_PARTS.get(BoilerPart.CHIMNEY).getBlock();
            //1.0.5-beta (lot 23) : la maconnerie finie du four de potier forme tient lieu de ses briques, escaliers et dalle
            case 'r' -> b == Blocks.BRICKS || fr.pioneers.tech.bronze.foundry.KilnMasonry.fits('r', s);
            case '/' -> b == Blocks.BRICK_STAIRS || fr.pioneers.tech.bronze.foundry.KilnMasonry.fits('/', s);
            case '_' -> b == Blocks.BRICK_SLAB || fr.pioneers.tech.bronze.foundry.KilnMasonry.fits('_', s);
            case 'I' -> s.isAir();
            case 'w' -> true;
            default -> false;
        };
    }

    /** Bloc conseille par le plan ({@code steel} : corps de chaudiere en acier). */
    public static Block block(char letter, boolean steel) {
        return switch (letter) {
            case 'X' -> PiBronze.FIRE_BRICK_FIREBOX.getBlock();
            case 'B' -> PiBronze.FIRE_BRICKS.getBlock();
            case 'T' -> PiBronze.TUYERE.getBlock();
            case 'V' -> PiBronze.FOUNDRY_VAT.getBlock();
            case 'b' -> PiBronze.DRILL_BASE.getBlock();
            case 'f' -> PiBronze.DRILL_FRAME.getBlock();
            case 'c' -> PiBronze.DRILL_CROWN.getBlock();
            case 'D' -> PiConstruction.DRILL_STRING_BRONZE.getBlock();
            case 'o' -> PiBronze.BOILER_PARTS.get(steel ? BoilerPart.STEEL_SHELL : BoilerPart.COPPER_SHELL).getBlock();
            case 'd' -> PiBronze.BOILER_PARTS.get(BoilerPart.STEAM_DOME).getBlock();
            case 'k' -> PiBronze.BOILER_PARTS.get(BoilerPart.CHIMNEY).getBlock();
            case 'w' -> PiBronze.ACCESSORIES.get(SteamAccessory.WATER_GAUGE).getBlock();
            case 'r' -> Blocks.BRICKS;
            case '/' -> Blocks.BRICK_STAIRS;
            case '_' -> Blocks.BRICK_SLAB;
            default -> Blocks.AIR;
        };
    }

    /** Autres blocs acceptes par le plan a cette case. */
    public static Set<Block> accepted(char letter) {
        return switch (letter) {
            case 'b' -> Set.of(PiBronze.DRILL_PORT.getBlock());
            case 'o' -> Set.of(PiBronze.BOILER_PARTS.get(BoilerPart.COPPER_SHELL).getBlock(), PiBronze.BOILER_PARTS.get(BoilerPart.STEEL_SHELL).getBlock());
            case 'w' -> Set.of(Blocks.AIR);
            case 'r', '/', '_' -> Set.of(PiBronze.KILN_MASONRY.get());
            default -> Set.of();
        };
    }

    /** Case facultative (indicateur de niveau ; blocs de cheminee au-dessus du premier). */
    private static boolean optional(String key, Cell c) {
        if (c.letter() == 'w') {
            return true;
        }
        if (c.letter() == 'k') {
            Cell low = first(key, 'k');
            return low != null && c.y() > low.y();
        }
        return false;
    }

    /**
     * Premiere case fautive de la structure {@code key} (le controleur n'est pas verifie), ou null : complete. Une case non chargee compte
     * comme fautive.
     */
    @Nullable
    public static BlockPos fault(Structure.View view, BlockPos controller, Direction front, String key) {
        for (Cell c : cells(key)) {
            if (c.letter() == 'K' || optional(key, c)) {
                continue;
            }
            BlockPos p = at(controller, front, c);
            if (!view.loaded(p) || !fits(c.letter(), view.getBlockState(p))) {
                return p;
            }
        }
        return null;
    }

    /** Cases du monde des lettres donnees (toutes : chaine vide), le controleur exclu. */
    public static List<BlockPos> positions(BlockPos controller, Direction front, String key, String letters) {
        List<BlockPos> out = new ArrayList<>();
        for (Cell c : cells(key)) {
            if (c.letter() != 'K' && (letters.isEmpty() || letters.indexOf(c.letter()) >= 0)) {
                out.add(at(controller, front, c));
            }
        }
        return out;
    }

    /** Cases de la structure formee (registre des hotes), sans le controleur ni l'air interieur, ni les cases facultatives. */
    public static List<BlockPos> members(BlockPos controller, Direction front, String key) {
        List<BlockPos> out = new ArrayList<>();
        for (Cell c : cells(key)) {
            if (c.letter() != 'K' && c.letter() != 'I' && !optional(key, c)) {
                out.add(at(controller, front, c));
            }
        }
        return out;
    }

    /**
     * Hote du cycle de vie commun (lot 8a, {@code new MultiblockCore.House(host, 40)}) d'un multibloc maison a structure NBT simple, sans
     * pieces a rattacher (four de potier, four a coke primitif) : erreur {@code error} (quoi) a la premiere case fautive.
     *
     * @param tile    le controleur
     * @param front   facade du controleur
     * @param changed forme ou erreur changee (sauvegarde, synchro du client)
     */
    public static MultiblockCore.House.Host host(net.minecraft.world.level.block.entity.BlockEntity tile, java.util.function.Supplier<Direction> front,
          String key, String error, Runnable changed) {
        return new MultiblockCore.House.Host() {
            @Nullable
            @Override
            public net.minecraft.world.level.Level level() {
                return tile.getLevel();
            }

            @Override
            public BlockPos pos() {
                return tile.getBlockPos();
            }

            @Override
            public Direction front() {
                return front.get();
            }

            @Nullable
            @Override
            public MultiblockCore.House.Survey survey() {
                if (tile.getLevel() == null) {
                    return null;
                }
                BlockPos fault = fault(Structure.View.of(tile.getLevel()), tile.getBlockPos(), front.get(), key);
                return fault != null ? MultiblockCore.House.Survey.fail(error, fault, null)
                      : MultiblockCore.House.Survey.ok(members(tile.getBlockPos(), front.get(), key), null);
            }

            @Override
            public void houseChanged() {
                changed.run();
            }
        };
    }

    /** Facade d'un controleur a {@code facing} horizontal (nord sinon). */
    public static Direction facing(BlockState state) {
        return state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
               ? state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    /** Peint la chaleur des foyers en briques refractaires de la structure (0 froids .. 2 en flammes ; rien si un bloc n'est pas un foyer). */
    public static void paintFire(net.minecraft.world.level.Level level, BlockPos controller, Direction front, String key, int heat) {
        for (BlockPos p : positions(controller, front, key, "X")) {
            if (level.isLoaded(p)) {
                fr.pioneers.tech.bronze.autofoundry.FoundryPartBlock.paint(level, p, heat, false);
            }
        }
    }

    /**
     * Plan projete de la structure : chaque case avec son bloc (l'air interieur : vide), le controleur ({@code controller}) a sa case.
     * 1.0.5-beta (lot 27) : les cases facultatives de la VALIDATION ({@link #optional} : tube de niveau, cheminee au-dessus du premier bloc)
     * sont marquees facultatives dans le plan (page JEI « Multiblocs »).
     * Convention des plans maison : le {@code forward} du plan est la FACADE du controleur ; la case (x, y, z) du repere du controleur va donc
     * en (-x, y, -z) du plan.
     */
    public static void draw(Plan plan, String key, Block controller, boolean steel) {
        for (Cell c : cells(key)) {
            if (c.letter() == 'K') {
                plan.put(-c.x(), c.y(), -c.z(), controller, Set.of());
            } else if (c.letter() == 'I') {
                plan.put(-c.x(), c.y(), -c.z(), null, Set.of());
            } else if (c.state() != null) {
                plan.putState(-c.x(), c.y(), -c.z(), oriented(block(c.letter(), steel).defaultBlockState(), c.state(), plan), accepted(c.letter()),
                      optional(key, c));
            } else {
                plan.put(-c.x(), c.y(), -c.z(), block(c.letter(), steel), accepted(c.letter()), optional(key, c));
            }
        }
    }

    /**
     * Etat du plan d'apres l'orientation du NBT ({@link Cell#state}) : facing relatif a la facade (front = la facade, le plan regarde
     * {@link Plan#forward()} ; left = sa droite a lui, {@link Plan#right()}), autres proprietes par leur nom ; une valeur inconnue est ignoree.
     */
    static BlockState oriented(BlockState s, String spec, Plan plan) {
        for (String kv : spec.split(",")) {
            int i = kv.indexOf('=');
            if (i <= 0) {
                continue;
            }
            String k = kv.substring(0, i), v = kv.substring(i + 1);
            Property<?> p = s.getBlock().getStateDefinition().getProperty(k);
            if (p == null) {
                continue;
            }
            if (p instanceof DirectionProperty dp) {
                Direction d = switch (v) {
                    case "front" -> plan.forward();
                    case "back" -> plan.forward().getOpposite();
                    case "left" -> plan.right();
                    case "right" -> plan.right().getOpposite();
                    default -> Direction.byName(v);
                };
                if (d != null && dp.getPossibleValues().contains(d)) {
                    s = s.setValue(dp, d);
                }
            } else {
                s = with(s, p, v);
            }
        }
        return s;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> p, String v) {
        return p.getValue(v).map(val -> s.setValue(p, val)).orElse(s);
    }
}

package fr.pioneers.tech.industry.multi;

import fr.pioneers.api.multiblock.StructureMatcher;
import fr.pioneers.core.Directions;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.nuclear.ReactorBlock;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Lot 5 : la validation elle-meme est dans {@link StructureMatcher} (API Multibloc, roles par tag) ; restent ici l'erreur, le resultat et la
 * vue du monde.
 * <p>
 * Validation d'un multibloc industriel selon le gabarit de sa machine (0.16.1 : tour, fut couche, dome, halle, bassin, cuve a ventre,
 * hyperboloide ou pave ; {@link IndustryShape}). Le controleur est a la base de la facade, facade vers l'exterieur.
 * <ol>
 *     <li>« sondes » du monde : pieces de structure a la suite depuis le controleur (gauche, droite, haut, bas, puis vers l'arriere depuis
 *     le bas) ; elles designent le candidat du gabarit (taille) a verifier, sans tout essayer ;</li>
 *     <li>toute l'enveloppe du candidat doit etre faite de pieces (carcasses, verre, hublots, ports, pieces signature ; un seul controleur, le
 *     notre), l'interieur d'air et de modules du type de la machine (et d'eau pour un bassin) ;</li>
 *     <li>pieces signature exigees a leur place (cheminee au sommet, tuyeres au premier anneau...) ;</li>
 *     <li>le tier est celui de la plus faible carcasse ; 1.0 : le parallelisme ne depend que du tier, et l'interieur doit etre entierement
 *     rempli de modules (blocs metier) du type de la machine (sauf bassins : eau ; fusion : plasma ; cuves, reacteurs : libre) ;</li>
 *     <li>1.0.4-beta (lot 15) : plus de blocs de paroi fonctionnels (bobines, events, ventilateurs, blocs d'ingenierie retires).</li>
 * </ol>
 * En cas d'echec, l'erreur rapportee est celle du candidat alle le plus loin.
 */
public final class Structure {

    public static final int PER_MODULE = 2;

    /** Raison d'un refus. */
    public enum Error { NONE, TOO_SMALL, TOO_BIG, BAD_SHELL, BAD_INTERIOR, WRONG_MODULE, NO_MODULE, NO_CASING, SCORCHED, OTHER_CONTROLLER, DRY_RISER, MISSING_PART,
        /** 1.0 : case interieure vide (les modules sont obligatoires partout). */
        INCOMPLETE,
        /** 1.0 : un cadre de structure manque (pieds, chevalet, balancier). */
        MISSING_FRAME,
        /** 1.0 : la paroi d'un contenant doit etre en toles (ou verre, blocs de paroi ; 1.0.4-beta lot 26 : plus de port). */
        MISSING_SHEET,
        /** 1.0 (lot 3) : un bloc de reacteur manque ou la case doit etre vide (detail = role, {@link #reactorKey}). */
        MISSING_REACTOR,
        /**
         * 1.0.4-beta (lot 24) : structure de Guillaume (NBT) : le bloc de la case manque (verre, foyer, bobine, bloc empilable, salle blanche,
         * baie de serveurs, substrat, refrigerateur ; detail = code de la case, {@link NbtRoles#expectedKey}).
         */
        MISSING_BLOCK,
        /**
         * 1.0.4-beta (lot 26) : un port est pose a une case ou il n'a pas le droit d'etre : un port ne remplace qu'une carcasse ou (lot 30)
         * une tole, jamais une charpente, un verre ou un bloc a role propre ; {@link fr.pioneers.tech.industry.multi.ShapeTemplate#portAllowed}.
         */
        PORT_NOT_HERE }

    /** 1.0 (lot 3) : cle de langue du bloc attendu pour un role de reacteur. */
    public static String reactorKey(int role) {
        return switch (role) {
            case IndustryShape.FUEL -> "block.pioneers.fuel_channel";
            case IndustryShape.COIL -> "block.pioneers.confinement_coil";
            case IndustryShape.REFLECTOR -> "block.pioneers.diamond_reflector";
            case IndustryShape.COLLECTOR -> "block.pioneers.fragment_collector";
            case IndustryShape.GLASS -> "block.pioneers.industrial_glass";
            case IndustryShape.CHAMBER -> "block.pioneers.vacuum_chamber";
            case IndustryShape.LINING -> "industry.pioneers.reactor.lining";
            case IndustryShape.SOLENOID -> "block.pioneers.solenoid_coil";
            case IndustryShape.TFCOIL -> "block.pioneers.toroidal_coil";
            case IndustryShape.HELIX -> "block.pioneers.stellarator_coil";
            default -> "industry.pioneers.reactor.empty";
        };
    }

    /** 1.0 (lot 3) : le bloc convient-il au role de reacteur ? */
    public static boolean reactorFits(int role, BlockState state) {
        Block block = state.getBlock();
        if (role == IndustryShape.EMPTY) {
            return state.isAir();
        }
        if (role == IndustryShape.GLASS) {
            return block instanceof IndustrialGlassBlock;
        }
        if (!(block instanceof ReactorBlock rb)) {
            return false;
        }
        return switch (role) {
            case IndustryShape.FUEL -> rb.kind.fuel();
            case IndustryShape.COIL -> rb.kind == ReactorBlock.Kind.CONFINEMENT_COIL;
            case IndustryShape.REFLECTOR -> rb.kind == ReactorBlock.Kind.DIAMOND_REFLECTOR;
            case IndustryShape.COLLECTOR -> rb.kind == ReactorBlock.Kind.FRAGMENT_COLLECTOR;
            case IndustryShape.CHAMBER -> rb.kind == ReactorBlock.Kind.VACUUM_CHAMBER;
            case IndustryShape.LINING -> rb.kind == ReactorBlock.Kind.BREEDING_BLANKET
                                         || rb.kind == ReactorBlock.Kind.DIRECT_CONVERTER;
            case IndustryShape.SOLENOID -> rb.kind == ReactorBlock.Kind.SOLENOID_COIL;
            case IndustryShape.TFCOIL -> rb.kind == ReactorBlock.Kind.TOROIDAL_COIL;
            case IndustryShape.HELIX -> rb.kind == ReactorBlock.Kind.HELICAL_COIL;
            default -> false;
        };
    }

    /**
     * Resultat : bornes (coin min, coin max), tier, modules, enveloppe (nombre de blocs), ports, volume (cases interieures), dimensions du
     * gabarit, pieces (enveloppe et interieur), colonnes montantes (forme libre), detail de l'erreur ({@link Signature#detail}).
     * 1.0.4-beta (lot 30) : {@code sheetPorts} = ports poses a la place d'une tole ({@link ShapeTemplate#portOnSheet}) : peints a la
     * formation avec la peau de la tole ({@code Connections.ON_SHEET}).
     */
    public record Result(Error error, @Nullable BlockPos at, BlockPos min, BlockPos max, int tier, int modules, int shell, int volume, List<BlockPos> ports,
                         int width, int height, int depth, @Nullable Set<BlockPos> members, List<BlockPos> risers, int detail, List<BlockPos> sheetPorts) {

        public Result(Error error, @Nullable BlockPos at, BlockPos min, BlockPos max, int tier, int modules, int shell, int volume, List<BlockPos> ports,
              int width, int height, int depth, @Nullable Set<BlockPos> members, List<BlockPos> risers, int detail) {
            this(error, at, min, max, tier, modules, shell, volume, ports, width, height, depth, members, risers, detail, List.of());
        }

        public Result(Error error, @Nullable BlockPos at, BlockPos min, BlockPos max, int tier, int modules, int shell, int volume, List<BlockPos> ports,
              int width, int height, int depth, @Nullable Set<BlockPos> members, List<BlockPos> risers) {
            this(error, at, min, max, tier, modules, shell, volume, ports, width, height, depth, members, risers, 0);
        }

        public Result(Error error, @Nullable BlockPos at, BlockPos min, BlockPos max, int tier, int modules, int shell, int volume, List<BlockPos> ports,
              int width, int height, int depth) {
            this(error, at, min, max, tier, modules, shell, volume, ports, width, height, depth, null, List.of(), 0);
        }

        public boolean formed() {
            return error == Error.NONE;
        }

        /** Le bloc fait-il partie de la structure ? (une piece, ou une case qui la touche : une pose la changerait) */
        public boolean contains(BlockPos pos) {
            if (members != null) {
                if (members.contains(pos)) {
                    return true;
                }
                for (Direction d : Directions.ALL) {
                    if (members.contains(pos.relative(d))) {
                        return true;
                    }
                }
                return false;
            }
            return pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                   && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }

        /** Bloc de la structure elle-meme (pas un voisin) : les ports n'y poussent pas leurs sorties. */
        public boolean isPart(BlockPos pos) {
            return members != null ? members.contains(pos) : contains(pos);
        }

        static Result fail(Error error, @Nullable BlockPos at, BlockPos controller) {
            return fail(error, at, controller, 0);
        }

        static Result fail(Error error, @Nullable BlockPos at, BlockPos controller, int detail) {
            return new Result(error, at, controller, controller, 0, 0, 0, 0, List.of(), 0, 0, 0, null, List.of(), detail);
        }
    }

    private Structure() {
    }

    /** Piece de structure (enveloppe) ? */
    public static boolean isFrame(BlockState state) {
        Block block = state.getBlock();
        return block instanceof CasingBlock || block instanceof IndustrialGlassBlock || block instanceof PortBlock || block instanceof ControllerBlock
               || block instanceof PartBlock || block instanceof StructureFrameBlock
               || block instanceof SheetMetalBlock || block instanceof ConcreteShellBlock || block instanceof ReactorBlock;
    }

    /** 0.20.24 : vue du monde lue par la validation (le monde du jeu, ou un monde factice du banc {@code tools/reactortest}). */
    public interface View {

        boolean loaded(BlockPos pos);

        BlockState state(BlockPos pos);

        /** API Multibloc (lot 5) : de l'eau (forme libre : colonnes montantes) ? */
        default boolean water(BlockPos pos) {
            return state(pos).getFluidState().is(net.minecraft.tags.FluidTags.WATER);
        }

        /** API Multibloc (lot 5) : fond solide (dessus porteur) ? */
        default boolean sturdy(BlockPos pos) {
            return false;
        }

        /** API Multibloc (lot 5) : bas du monde. */
        default int minY() {
            return -64;
        }

        /** API Multibloc (lot 6) : tile d'une case (monde reel ; null dans un monde factice). */
        @org.jetbrains.annotations.Nullable
        default net.minecraft.world.level.block.entity.BlockEntity tile(BlockPos pos) {
            return null;
        }

        // Lot 6 : memes noms que Level, pour que les validations des multiblocs maison lisent un monde reel ou factice (banc) sans changer
        // de code : getBlockState, isLoaded, getFluidState, getBlockEntity

        default BlockState getBlockState(BlockPos pos) {
            return state(pos);
        }

        default boolean isLoaded(BlockPos pos) {
            return loaded(pos);
        }

        default net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) {
            return state(pos).getFluidState();
        }

        @org.jetbrains.annotations.Nullable
        default net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
            return tile(pos);
        }

        static View of(Level level) {
            return new View() {
                @Override
                public boolean loaded(BlockPos pos) {
                    return level.isLoaded(pos);
                }

                @Override
                public BlockState state(BlockPos pos) {
                    return level.getBlockState(pos);
                }

                @Override
                public boolean sturdy(BlockPos pos) {
                    return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
                }

                @Override
                public int minY() {
                    return level.getMinBuildHeight();
                }

                @Override
                public net.minecraft.world.level.block.entity.BlockEntity tile(BlockPos pos) {
                    return level.getBlockEntity(pos);
                }

                @Override
                public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) {
                    return level.getFluidState(pos);
                }
            };
        }
    }

    /** Lot 5 : delegue a LA validation de l'API Multibloc ({@link StructureMatcher}). */
    public static Result validate(Level level, BlockPos controller, Direction front, IndustryMachine machine) {
        return StructureMatcher.match(level, controller, front, machine);
    }

    /** Validation sur une vue du monde (gabarit ou forme libre) : {@link StructureMatcher}. */
    public static Result validate(View level, BlockPos controller, Direction front, IndustryMachine machine) {
        return StructureMatcher.match(level, controller, front, machine);
    }

    /**
     * 1.0.4-beta (lot 23) : unites de travail d'une structure formee : les modules exiges (baie de serveurs, colonnes montantes) ; pour une
     * machine dont le module a ete retire, le volume interieur (= le nombre de modules d'une structure pleine d'avant : garnissage de la tour
     * de refroidissement, matrices de qubits de l'ordinateur quantique).
     */
    public static int units(IndustryMachine machine, Result s) {
        return machine.module != null || machine.kind == IndustryMachine.Kind.REACTOR || machine.kind == IndustryMachine.Kind.FUSION ? s.modules()
              : s.volume();
    }

    /**
     * 1.0 : l'interieur doit-il etre entierement rempli de modules ? Oui pour toutes les machines a module, sauf la fusion (le vide est le
     * plasma) et les reacteurs nucleaires (refaits a la livraison suivante).
     */
    public static boolean mustFill(IndustryMachine machine) {
        return machine.module != null && machine.kind != IndustryMachine.Kind.FUSION && machine.kind != IndustryMachine.Kind.REACTOR
               && machine.style != IndustryStyle.NUCLEAR;
    }
}

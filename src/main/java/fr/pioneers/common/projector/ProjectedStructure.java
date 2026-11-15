package fr.pioneers.common.projector;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Outils des plans projetables. API Multibloc (lot 6) : plus AUCUN plan ecrit ici : les plans des multiblocs maison sont tires de leur forme
 * ({@code fr.pioneers.api.multiblock.HouseShapes}, {@code BaseBlueprint}, {@code AssemblyHallBlueprint}, {@code HarborBlueprint}), ceux des
 * industriels de leur gabarit ({@code SpecBlueprint}). Restent la case fantome ({@link Ghost}) et le repere de dessin ({@link Plan}).
 * <p>
 * Chaque plan est decrit dans un repere local : x vers la droite du joueur, z vers l'avant (loin de lui), y vers le haut, l'ancre etant le
 * centre du sol de la structure.
 */
public final class ProjectedStructure {

    private ProjectedStructure() {
    }

    /**
     * Bloc attendu a une position : {@code state} = bloc conseille (null : la case doit rester vide), {@code accepted} = blocs qui
     * conviennent aussi (verre ou port a la place d'un boitier...). 1.0.5-beta (lot 27, retour 33 de Guillaume : « JEI indique le tube de
     * niveau comme obligatoire pour la chaudiere ») : {@code optional} = case CONSEILLEE que la validation du multibloc n'exige pas (tube de
     * niveau, cheminee au-dessus du premier bloc, modules facultatifs) : la page JEI « Multiblocs » la montre a part ({@link BlueprintBill}).
     */
    public record Ghost(@Nullable BlockState state, Set<Block> accepted, boolean optional) {

        /** Case exigee (cas general). */
        public Ghost(@Nullable BlockState state, Set<Block> accepted) {
            this(state, accepted, false);
        }

        public boolean isSatisfiedBy(BlockState current) {
            if (state == null) {
                return current.isAir() || accepted.contains(current.getBlock());
            }
            return current.getBlock() == state.getBlock() || accepted.contains(current.getBlock());
        }
    }

    /** Plan en cours de construction : repere local -> monde. */
    public static final class Plan {

        private final BlockPos anchor;
        private final Direction forward, right;
        private final Map<BlockPos, Ghost> ghosts = new LinkedHashMap<>();

        public Plan(BlockPos anchor, Direction forward) {
            this.anchor = anchor;
            this.forward = forward;
            this.right = forward.getClockWise();
        }

        public void put(int x, int y, int z, @Nullable Block block, Set<Block> accepted) {
            put(x, y, z, block, accepted, false);
        }

        /** 1.0.5-beta (lot 27) : case facultative ({@code optional}) ou exigee. */
        public void put(int x, int y, int z, @Nullable Block block, Set<Block> accepted, boolean optional) {
            BlockPos pos = anchor.offset(right.getStepX() * x + forward.getStepX() * z, y, right.getStepZ() * x + forward.getStepZ() * z);
            ghosts.put(pos, new Ghost(block == null ? null : block.defaultBlockState(), accepted, optional));
        }

        /** 1.0.4-beta (lot 24) : bloc conseille avec son etat (orientation des escaliers d'une structure de Guillaume...). */
        public void putState(int x, int y, int z, @Nullable BlockState state, Set<Block> accepted) {
            putState(x, y, z, state, accepted, false);
        }

        /** 1.0.5-beta (lot 27) : case facultative ({@code optional}) ou exigee, avec son etat. */
        public void putState(int x, int y, int z, @Nullable BlockState state, Set<Block> accepted, boolean optional) {
            BlockPos pos = anchor.offset(right.getStepX() * x + forward.getStepX() * z, y, right.getStepZ() * x + forward.getStepZ() * z);
            ghosts.put(pos, new Ghost(state, accepted, optional));
        }

        /** Avant du plan (repere local : +z). */
        public Direction forward() {
            return forward;
        }

        /** Droite du plan (repere local : +x). */
        public Direction right() {
            return right;
        }

        public Map<BlockPos, Ghost> ghosts() {
            return ghosts;
        }
    }
}

package fr.pioneers.content;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.46 (audit) : partie commune des multiblocs maison a controleur (Simulateur spatial, absorbeur) : registre des controleurs charges
 * par dimension (une piece retrouve le sien sans balayer le monde) et file des changements de blocs, traitee en fin de tick. Avant,
 * chaque structure recopiait ce code ; vide a l'arret du serveur (rien ne passe d'un monde a l'autre).
 *
 * @param <C> tuile du controleur
 * @param <P> tuile d'une piece
 */
public abstract class StructureQueue<C extends BlockEntity, P extends BlockEntity> {

    private final Map<ResourceKey<Level>, Set<BlockPos>> pending = new HashMap<>();
    private final Map<ResourceKey<Level>, Set<BlockPos>> controllers = new HashMap<>();
    private final Class<C> controllerType;
    private final Class<P> partType;

    protected StructureQueue(Class<C> controllerType, Class<P> partType) {
        this.controllerType = controllerType;
        this.partType = partType;
    }

    /** Controleur de la piece (null : libre). */
    @Nullable
    protected abstract BlockPos controllerOf(P part);

    /** Piece dont le controleur a disparu : la liberer (et l'eteindre). */
    protected abstract void detach(ServerLevel level, BlockPos pos, P part);

    /** Controleur charge dont la structure peut contenir cette position. */
    @Nullable
    protected abstract BlockPos findController(ServerLevel level, BlockPos pos);

    /** Verification complete de la structure d'un controleur. */
    protected abstract void validate(ServerLevel level, C controller);

    public void register(ServerLevel level, BlockPos controller) {
        controllers.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(controller.immutable());
    }

    public void unregister(ServerLevel level, BlockPos controller) {
        Set<BlockPos> set = controllers.get(level.dimension());
        if (set != null) {
            set.remove(controller);
        }
    }

    /** Controleurs charges de la dimension (vue en lecture). */
    public Set<BlockPos> controllers(ServerLevel level) {
        Set<BlockPos> set = controllers.get(level.dimension());
        return set == null ? Collections.emptySet() : set;
    }

    public void queue(ServerLevel level, BlockPos pos) {
        pending.computeIfAbsent(level.dimension(), key -> new LinkedHashSet<>()).add(pos.immutable());
    }

    /** Arret du serveur : plus rien du monde quitte. */
    public void clear() {
        pending.clear();
        controllers.clear();
    }

    /** Fin de tick d'une dimension : chaque controleur touche est verifie une fois. */
    public void tick(ServerLevel level) {
        Set<BlockPos> queued = pending.remove(level.dimension());
        if (queued == null) {
            return;
        }
        Set<BlockPos> done = new HashSet<>();
        for (BlockPos pos : queued) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockEntity at = level.getBlockEntity(pos);
            if (controllerType.isInstance(at)) {
                if (done.add(pos)) {
                    validate(level, controllerType.cast(at));
                }
                continue;
            }
            P part = partType.isInstance(at) ? partType.cast(at) : null;
            BlockPos known = part != null ? controllerOf(part) : null;
            if (known != null && level.isLoaded(known) && controllerType.isInstance(level.getBlockEntity(known))) {
                if (done.add(known)) {
                    validate(level, controllerType.cast(level.getBlockEntity(known)));
                }
                continue;
            }
            if (part != null && known != null) {
                detach(level, pos, part);
            }
            BlockPos found = findController(level, pos);
            if (found != null && done.add(found) && controllerType.isInstance(level.getBlockEntity(found))) {
                validate(level, controllerType.cast(level.getBlockEntity(found)));
            }
        }
    }
}

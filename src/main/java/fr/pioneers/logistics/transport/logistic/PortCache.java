package fr.pioneers.logistics.transport.logistic;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.gas.GasAccess;
import fr.pioneers.core.machine.BoundingTile;
import fr.pioneers.core.machine.MachineGui;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.tech.industry.multi.PortTile;
import fr.pioneers.tech.industry.multi.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (spec §7.1) : une machine raccordee, vue du moteur ({@link Endpoint}) : capacites gardees en cache par (type, face d'acces), invalidees
 * par le {@code LazyOptional} de la machine, relues au plus toutes les 200 ticks par securite ; jamais de {@code getBlockEntity} par tick.
 * <p>
 * Face d'acces (spec §3.1) : la face touchee par la fiche, ou une face relative a la facade de la machine ({@link RelativeSide} ; machine sans
 * facade = nord), quelle que soit la face touchee. Jamais l'acces interne (face nulle).
 * <p>
 * Ecart D2a : le raccord est le bloc touche lui-meme (un port de multibloc ou un bloc fantome est son propre raccord, chacun ayant ses
 * reservoirs et son sens) ; {@link #group()} = controleur du multibloc ou bloc principal, pour regrouper dans le diagramme.
 */
public final class PortCache implements Endpoint {

    private static final int FACES = 7, REFRESH = 200;
    /** Jamais lu : assez bas pour forcer la lecture, assez haut pour que {@code now - NEVER} ne depasse pas la capacite d'un long (MIN_VALUE debordait : lecture jamais faite). */
    private static final long NEVER = Long.MIN_VALUE / 2;

    private final Level level;
    private final BlockPos pos;
    /** Cable dont la fiche touche la machine (frontiere de claim : {@link ClanApi#canTransfer}). */
    private final BlockPos cable;
    private final long key;
    private final Direction touched;
    private final long group;
    private final int kinds;
    private final Object[] cache = new Object[ChannelKind.COUNT * FACES];
    private final long[] readAt = new long[ChannelKind.COUNT * FACES];
    private final LazyOptional<?>[] optionals = new LazyOptional<?>[ChannelKind.COUNT * FACES];

    /**
     * @param pos     position de la machine touchee
     * @param touched face de la machine touchee par la fiche (vue de la machine)
     */
    public PortCache(Level level, BlockPos pos, Direction touched, int kinds) {
        this(level, pos, touched, kinds, groupOf(level, pos));
    }

    /**
     * 1.0.5-beta (lot 45, retour 66) : raccord dont le groupe est deja connu (machine dans un chunk non charge : groupe lu dans
     * {@link CableIndex}) ; aucune lecture du monde.
     */
    public PortCache(Level level, BlockPos pos, Direction touched, int kinds, long group) {
        this.level = level;
        this.pos = pos.immutable();
        this.cable = pos.relative(touched).immutable();
        this.key = pos.asLong();
        this.touched = touched;
        this.kinds = kinds;
        this.group = group;
        java.util.Arrays.fill(readAt, NEVER);
    }

    /**
     * Groupe du diagramme d'une machine : controleur d'un multibloc (port), bloc principal (fantome), sinon elle-meme. 1.0.5-beta (lot 45) :
     * jamais de lecture d'un chunk non charge (la cle elle-meme).
     */
    public static long groupOf(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return pos.asLong();
        }
        BlockEntity tile = level.getBlockEntity(pos);
        BlockPos main = null;
        if (tile instanceof BoundingTile bounding) {
            main = bounding.getMain();
        } else if (tile instanceof PortTile) {
            main = Structures.controllerAt(level, pos);
        }
        return main == null ? pos.asLong() : main.asLong();
    }

    @Override
    public void wake() {
        java.util.Arrays.fill(readAt, NEVER);
    }

    @Override
    public long key() {
        return key;
    }

    /** Controleur ou bloc principal (regroupement du diagramme) ; la cle elle-meme sinon. */
    @Override
    public long group() {
        return group;
    }

    @Override
    public int kinds() {
        return kinds;
    }

    @Nullable
    @Override
    public Object handler(ChannelKind kind, int face) {
        int index = kind.ordinal() * FACES + (face < 0 ? 0 : 1 + Math.min(5, face));
        long now = level.getGameTime();
        if (now - readAt[index] < REFRESH) {
            return cache[index];
        }
        readAt[index] = now;
        cache[index] = null;
        //Frontiere de claim : relue avec la capacite (au plus toutes les 200 ticks), un claim pose entre-temps coupe le raccord
        if (!level.isLoaded(pos) || !ClanApi.canTransfer(level, cable, pos)) {
            return null;
        }
        BlockEntity tile = level.getBlockEntity(pos);
        if (tile == null || tile.isRemoved()) {
            return null;
        }
        Direction side = worldFace(tile.getBlockState(), face);
        Object found;
        LazyOptional<?> optional = null;
        if (kind == ChannelKind.GAS) {
            found = GasAccess.find(tile, side);
            LazyOptional<?> own = tile.getCapability(PioneersAPI.GAS_HANDLER, side);
            optional = own.isPresent() ? own : null;
        } else {
            optional = tile.getCapability(capability(kind), side);
            found = optional.resolve().orElse(null);
        }
        cache[index] = found;
        //Une seule ecoute par LazyOptional (sinon les ecouteurs s'accumuleraient a chaque relecture)
        if (optional != null && optional.isPresent() && optionals[index] != optional) {
            optionals[index] = optional;
            optional.addListener(lo -> readAt[index] = NEVER);
        }
        return found;
    }

    /** Face du monde pour une face d'acces (touchee, ou relative a la facade). */
    public Direction worldFace(BlockState state, int face) {
        if (face < 0) {
            return touched;
        }
        return RelativeSide.byIndex(face).toAbsolute(facing(state));
    }

    /** Facade d'un bloc (FACING, sinon HORIZONTAL_FACING, sinon nord). */
    public static Direction facing(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING);
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        return Direction.NORTH;
    }

    public static Capability<?> capability(ChannelKind kind) {
        return switch (kind) {
            case ITEM -> ForgeCapabilities.ITEM_HANDLER;
            case FLUID -> ForgeCapabilities.FLUID_HANDLER;
            case GAS -> PioneersAPI.GAS_HANDLER;
            case ENERGY -> ForgeCapabilities.ENERGY;
            case HEAT -> PioneersAPI.HEAT_HANDLER;
            case PRESSURE -> PioneersAPI.PRESSURE_HANDLER;
            case DATA -> PioneersAPI.DATA_HANDLER;
        };
    }

    /** Types offerts par une tile sur l'une quelconque de ses 6 faces (fiche, canaux auto), parmi {@code mask}. */
    public static int kindsOf(@Nullable BlockEntity tile, int mask) {
        if (tile == null) {
            return 0;
        }
        int found = 0;
        for (ChannelKind kind : ChannelKind.all()) {
            if ((mask & kind.bit()) == 0) {
                continue;
            }
            for (Direction side : Direction.values()) {
                boolean present = kind == ChannelKind.GAS ? GasAccess.find(tile, side) != null : tile.getCapability(capability(kind), side).isPresent();
                if (present) {
                    found |= kind.bit();
                    break;
                }
            }
        }
        return found;
    }

    @Override
    public int faces(ChannelKind kind) {
        if (!level.isLoaded(pos) || !ClanApi.canTransfer(level, cable, pos)) {
            return 0;
        }
        BlockEntity tile = level.getBlockEntity(pos);
        if (tile == null) {
            return 0;
        }
        int out = 0;
        Direction facing = facing(tile.getBlockState());
        SideConfig sides = tile instanceof MachineGui gui ? gui.getSideConfig() : null;
        for (int r = 0; r < 6; r++) {
            Direction side = RelativeSide.byIndex(r).toAbsolute(facing);
            boolean present = kind == ChannelKind.GAS ? GasAccess.find(tile, side) != null : tile.getCapability(capability(kind), side).isPresent();
            //Presence seulement : le sens exact (insertion / extraction) n'est pas expose par les capacites generiques (3 = les deux) ;
            //1.11 (D2b) : machines du socle a faces reglables : le reglage de la face dit le sens (entree = 1, sortie = 2, les deux = 3)
            int bits = present ? 3 : 0;
            if (present && sides != null) {
                SideMode mode = sides.mode(SideType.byIndex(kind.ordinal()), side);
                bits = mode == SideMode.INPUT ? 1 : mode == SideMode.OUTPUT ? 2 : 3;
            }
            out |= bits << (r * 2);
        }
        return out;
    }
}

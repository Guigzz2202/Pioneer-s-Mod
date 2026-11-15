package fr.pioneers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.IDataConfigurable;
import fr.pioneers.core.Directions;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.transport.logistic.LogisticCableBlock;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Prises de donnees des machines : un petit modele par face, affiche seulement sur les faces ouvertes aux donnees.
 * Le cable optique qui arrive sur la face vient s'y enficher exactement (plus de prise fixe sur le modele de la machine).
 * <p>
 * Les modeles sont decrits dans le repere de la machine tournee vers le nord : {@code <base>/port_north} pour l'avant,
 * {@code port_south} pour l'arriere, {@code port_east} pour la gauche, {@code port_west} pour la droite, {@code port_up} et {@code port_down}.
 * <p>
 * 0.20.46 (audit) : ce qui est branche autour de la machine (voisins, capacites, modeles) est garde et relu toutes les secondes, tout de
 * suite si les faces de donnees ou l'orientation changent, ou si un voisin dessine (prise, collerette) change de bloc.
 */
public final class DataPortRenderer {

    private static final RelativeSide[] SIDES = RelativeSide.values();
    private static final int REFRESH = 20;
    /** Fil de rendu seulement. Cle faible : l'entree disparait avec la tile. */
    private static final Map<BlockEntity, Ports> PORTS = new WeakHashMap<>();

    private DataPortRenderer() {
    }

    /** Ce que la machine montre, relu par {@link #refresh}. */
    private static final class Ports {

        boolean ready;
        long at;
        int modes;
        @Nullable
        Direction facing;
        @Nullable
        String base;
        @Nullable
        Object generation;
        /** Prise par face relative (null : rien). */
        final BakedModel[] ports = new BakedModel[6];
        /** Collerette du cable optique voisin par direction (null : rien). */
        final BlockPos[] neighbors = new BlockPos[6];
        /** Etat des voisins des faces dessinees, surveille a chaque image. */
        final BlockState[] seen = new BlockState[6];
        int watched;
        boolean anyPort;
    }

    /** Modele d'une face relative. */
    public static ResourceLocation model(String base, RelativeSide side) {
        String face = switch (side) {
            case FRONT -> "north";
            case BACK -> "south";
            case LEFT -> "east";
            case RIGHT -> "west";
            case TOP -> "up";
            case BOTTOM -> "down";
        };
        return Pioneers.rl("block/" + base + "/port_" + face);
    }

    /** Enregistre les modeles supplementaires des faces qui peuvent porter une prise. */
    public static void register(Consumer<ResourceLocation> registrar, String base, RelativeSide... sides) {
        for (RelativeSide side : sides) {
            registrar.accept(model(base, side));
        }
    }

    /** Angle du repere nord vers l'orientation de la machine. */
    public static float yaw(Direction facing) {
        return switch (facing) {
            case EAST -> -90;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        };
    }

    /**
     * La prise n'apparait que si quelque chose y est branche : un cable optique enfiche sur cette face, ou une machine a donnees
     * accolee qui echange par la face d'en face.
     */
    private static boolean isLinked(BlockEntity tile, Direction direction) {
        Level level = tile.getLevel();
        if (level == null) {
            return false;
        }
        BlockPos neighbor = tile.getBlockPos().relative(direction);
        BlockState state = level.getBlockState(neighbor);
        if (state.getBlock() instanceof LogisticCableBlock) {
            return LogisticCableBlock.link(state, direction.getOpposite()) == LogisticCableBlock.Link.PLUG;
        }
        BlockEntity other = level.getBlockEntity(neighbor);
        return other != null && other.getCapability(PioneersAPI.DATA_HANDLER, direction.getOpposite()).isPresent();
    }

    /** Toute machine a donnees ({@link IDataConfigurable}), Mekanism ou socle : facade {@code facing}. */
    public static void render(BlockEntity tile, Direction facing, String base, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(tile instanceof IDataConfigurable configurable) || tile.getLevel() == null) {
            return;
        }
        Level level = tile.getLevel();
        Ports ports = ports(tile, configurable, facing, base, level);
        if (ports.anyPort) {
            Minecraft minecraft = Minecraft.getInstance();
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(yaw(facing)));
            pose.translate(-0.5, 0, -0.5);
            for (BakedModel model : ports.ports) {
                if (model != null) {
                    minecraft.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), null, model, 1, 1, 1,
                          light, overlay, ModelData.EMPTY, RenderType.cutout());
                }
            }
            pose.popPose();
        }
    }

    private static Ports ports(BlockEntity tile, IDataConfigurable configurable, Direction facing, String base, Level level) {
        Ports ports = PORTS.get(tile);
        if (ports == null) {
            ports = new Ports();
            PORTS.put(tile, ports);
        }
        int modes = 0;
        for (Direction direction : Directions.ALL) {
            modes = modes << 3 | configurable.getDataMode(direction).ordinal();
        }
        long now = level.getGameTime();
        Object generation = Minecraft.getInstance().getModelManager().getMissingModel();
        boolean stale = !ports.ready || now - ports.at >= REFRESH || now < ports.at || modes != ports.modes || facing != ports.facing
                        || !base.equals(ports.base) || generation != ports.generation;
        for (int i = 0; !stale && i < 6; i++) {
            stale = (ports.watched >> i & 1) != 0 && level.getBlockState(ports.neighbors[i]) != ports.seen[i];
        }
        if (stale) {
            refresh(ports, tile, configurable, facing, base, level);
            ports.ready = true;
            ports.at = now;
            ports.modes = modes;
            ports.facing = facing;
            ports.base = base;
            ports.generation = generation;
        }
        return ports;
    }

    private static void refresh(Ports ports, BlockEntity tile, IDataConfigurable configurable, Direction facing, String base, Level level) {
        ModelManager manager = Minecraft.getInstance().getModelManager();
        BakedModel missing = manager.getMissingModel();
        BlockPos pos = tile.getBlockPos();
        for (Direction direction : Directions.ALL) {
            BlockPos neighbor = pos.relative(direction);
            ports.neighbors[direction.ordinal()] = neighbor;
            ports.seen[direction.ordinal()] = level.getBlockState(neighbor);
        }
        ports.watched = 0;
        ports.anyPort = false;
        for (RelativeSide side : SIDES) {
            Direction direction = side.toAbsolute(facing);
            BakedModel model = null;
            if (configurable.getDataMode(direction).isConnectable() && isLinked(tile, direction)) {
                model = manager.getModel(model(base, side));
                if (model == missing) {
                    model = null;
                }
            }
            ports.ports[side.ordinal()] = model;
            if (model != null) {
                ports.anyPort = true;
                ports.watched |= 1 << direction.ordinal();
            }
        }
        //Relecture : toutes les faces (une fibre posee contre une face encore vide doit apparaitre a l'image suivante, pas 1 s plus tard)
        ports.watched = 0x3F;
    }
}

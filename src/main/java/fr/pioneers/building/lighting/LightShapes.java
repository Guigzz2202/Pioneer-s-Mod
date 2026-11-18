package fr.pioneers.building.lighting;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 1.0.4-beta (lot 5) : formes (visee, collision) des luminaires et des interrupteurs. Boites du REPERE CANONIQUE (en px, tourne vers le nord)
 * : au mur, le mur est au sud (z = 16) et l'objet regarde le nord ; au sol, pose sur y = 0 ; au plafond, pendu sous y = 16. Memes cotes que
 * les modeles de {@code tools/gc/lighting/lighting.py} (le controle {@code tools/check_p104_5.py} verifie que chaque modele tient dans sa
 * boite). Rotation vers les autres faces : {@link #rotate}.
 */
public final class LightShapes {

    private LightShapes() {
    }

    /** Boite canonique (x0, y0, z0, x1, y1, z1) d'une sorte de luminaire et d'une pose. */
    static double[] box(LightFixtureBlock.Kind kind, AttachFace face) {
        return switch (kind) {
            case BRONZE_WALL_SCONCE -> new double[]{5, 2, 7, 11, 14, 16};
            case BRONZE_LANTERN -> face == AttachFace.CEILING ? new double[]{5, 2, 5, 11, 16, 11} : new double[]{5, 0, 5, 11, 10, 11};
            case BRONZE_CHANDELIER -> new double[]{1, 3, 1, 15, 16, 15};
            case ELECTRIC_WALL_LAMP -> new double[]{5, 3, 6, 11, 13, 16};
            case ELECTRIC_CEILING_LAMP -> new double[]{3, 12, 3, 13, 16, 13};
            case INDUSTRIAL_CAGE_LAMP -> switch (face) {
                case FLOOR -> new double[]{5, 0, 5, 11, 9, 11};
                case WALL -> new double[]{5, 5, 7, 11, 11, 16};
                case CEILING -> new double[]{5, 7, 5, 11, 16, 11};
            };
        };
    }

    /** Boite canonique d'un interrupteur (platine 6 x 8 du levier vanilla + la manette basculee en marche ou a l'arret : on la vise aussi). */
    static double[] switchBox(AttachFace face) {
        return switch (face) {
            case WALL -> new double[]{5, 1.5, 7, 11, 14.5, 16};
            case FLOOR -> new double[]{5, 0, 1.5, 11, 9, 14.5};
            case CEILING -> new double[]{5, 7, 1.5, 11, 16, 14.5};
        };
    }

    /** Boite canonique tournee vers {@code facing} (au mur : mur derriere ; au sol et au plafond : rotation autour de y). */
    public static double[] rotate(double[] b, Direction facing) {
        double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
        return switch (facing) {
            case SOUTH -> new double[]{16 - x1, b[1], 16 - z1, 16 - x0, b[4], 16 - z0};
            case WEST -> new double[]{z0, b[1], 16 - x1, z1, b[4], 16 - x0};
            case EAST -> new double[]{16 - z1, b[1], x0, 16 - z0, b[4], x1};
            default -> b.clone();
        };
    }

    /** Formes precalculees (lues par le client et le serveur en meme temps : jamais remplies a la demande). */
    private static final Map<LightFixtureBlock.Kind, Map<AttachFace, VoxelShape[]>> FIXTURES = new EnumMap<>(LightFixtureBlock.Kind.class);
    private static final Map<AttachFace, VoxelShape[]> SWITCHES = new EnumMap<>(AttachFace.class);

    static {
        for (AttachFace f : AttachFace.values()) {
            SWITCHES.put(f, all(switchBox(f)));
        }
        for (LightFixtureBlock.Kind k : LightFixtureBlock.Kind.values()) {
            Map<AttachFace, VoxelShape[]> m = new EnumMap<>(AttachFace.class);
            for (AttachFace f : AttachFace.values()) {
                m.put(f, all(box(k, f)));
            }
            FIXTURES.put(k, m);
        }
    }

    private static VoxelShape[] all(double[] b) {
        VoxelShape[] out = new VoxelShape[4];
        for (Direction d : Direction.Plane.HORIZONTAL) {
            double[] r = rotate(b, d);
            out[d.get2DDataValue()] = Block.box(r[0], r[1], r[2], r[3], r[4], r[5]);
        }
        return out;
    }

    static VoxelShape fixture(LightFixtureBlock.Kind kind, AttachFace face, Direction facing) {
        VoxelShape[] s = FIXTURES.get(kind).get(face);
        return facing.getAxis() == Direction.Axis.Y ? Shapes.block() : s[facing.get2DDataValue()];
    }

    static VoxelShape lightSwitch(AttachFace face, Direction facing) {
        VoxelShape[] s = SWITCHES.get(face);
        return facing.getAxis() == Direction.Axis.Y ? Shapes.block() : s[facing.get2DDataValue()];
    }
}

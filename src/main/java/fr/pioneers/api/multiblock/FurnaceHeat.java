package fr.pioneers.api.multiblock;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * 1.0.5-beta (lot 13, retour de Guillaume : « degats au joueur s'il entre dans les fours multiblocs ») : CHALEUR DES FOURS PRIMITIFS.
 * Seulement four ALLUME, toutes les {@link #PERIOD} ticks :
 * <ul>
 *     <li>dans l'INTERIEUR (cases d'air de la structure de Guillaume, lettre {@code I} de {@link HouseNbt} : cuve du haut fourneau
 *     primitif, chambre du four a coke primitif) : degats de feu vanilla {@code inFire} ({@link #INSIDE}) et la bete prend feu
 *     ({@link #BURN_SECONDS} s) ;</li>
 *     <li>SUR la cuve de la fonderie (le bain : ses cuves sont des blocs pleins, on ne peut que marcher dessus) : degats de sol brulant
 *     vanilla {@code hotFloor} ({@link #ON_TOP}), comme un bloc de magma (pas en marchant accroupi).</li>
 * </ul>
 * Les resistances vanilla jouent (resistance au feu, betes ignifugees, creatif). Les fours industriels (controleurs a modules) n'ont pas
 * d'interieur accessible : rien a faire.
 */
public final class FurnaceHeat {

    /** Rythme (ticks), degats a l'interieur et sur le bain (points de vie), duree du feu (secondes). */
    public static final int PERIOD = 10, BURN_SECONDS = 3;
    public static final float INSIDE = 2F, ON_TOP = 1F;

    private FurnaceHeat() {
    }

    /** Le moment d'agir (une fois toutes les {@link #PERIOD} ticks, decale par position : les fours ne frappent pas tous ensemble). */
    public static boolean due(ServerLevel level, BlockPos controller) {
        return level.getGameTime() % PERIOD == Math.floorMod(controller.asLong(), PERIOD);
    }

    /** Boite qui couvre toutes les cases (null si aucune). */
    static AABB around(List<BlockPos> cells, double top) {
        AABB box = null;
        for (BlockPos p : cells) {
            AABB b = new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + top, p.getZ() + 1);
            box = box == null ? b : box.minmax(b);
        }
        return box;
    }

    /** Brule les etres vivants qui touchent une case interieure du four allume. Renvoie le nombre de betes touchees. */
    public static int inside(ServerLevel level, List<BlockPos> cells, boolean lit) {
        AABB all = lit ? around(cells, 1) : null;
        if (all == null) {
            return 0;
        }
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, all, LivingEntity::isAlive)) {
            for (BlockPos p : cells) {
                if (e.getBoundingBox().intersects(new AABB(p))) {
                    if (e.hurt(level.damageSources().inFire(), INSIDE)) {
                        e.setSecondsOnFire(BURN_SECONDS);
                    }
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    /** Brule les etres vivants debout sur les cases {@code tops} (le bain d'une fonderie allumee), sauf en marchant accroupi. */
    public static int onTop(ServerLevel level, List<BlockPos> tops, boolean lit) {
        if (!lit || tops.isEmpty()) {
            return 0;
        }
        List<BlockPos> above = tops.stream().map(BlockPos::above).toList();
        AABB all = around(above, 0.5);
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, all, LivingEntity::isAlive)) {
            if (e.isSteppingCarefully()) {
                continue;
            }
            for (BlockPos p : above) {
                if (e.getBoundingBox().intersects(new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 0.5, p.getZ() + 1))) {
                    e.hurt(level.damageSources().hotFloor(), ON_TOP);
                    n++;
                    break;
                }
            }
        }
        return n;
    }
}

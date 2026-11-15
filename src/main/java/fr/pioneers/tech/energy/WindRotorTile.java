package fr.pioneers.tech.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * 1.9.2 (retours de Guillaume) : tuile du rotor d'eolienne, sans donnees ni tick : elle ne sert qu'au rendu anime (le moyeu et les pales
 * debordent de plusieurs blocs). L'angle vient de la nacelle situee derriere ({@link #nacelle()}) ; sans nacelle, le rotor reste immobile.
 */
public class WindRotorTile extends BlockEntity {

    private AABB renderBox;

    public WindRotorTile(BlockPos pos, BlockState state) {
        super(PiEnergy.ROTOR_TILE.get(), pos, state);
    }

    public int tier() {
        return getBlockState().getBlock() instanceof WindRotorBlock b ? b.tier : 0;
    }

    /** Nacelle qui porte ce rotor (derriere lui, tournee dans le meme sens), ou null. */
    @Nullable
    public WindTurbineTile nacelle() {
        if (level == null) {
            return null;
        }
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        BlockEntity be = level.getBlockEntity(worldPosition.relative(facing.getOpposite()));
        return be instanceof WindTurbineTile t && t.facing() == facing ? t : null;
    }

    /** Les pales debordent : rendu tant que le disque est a l'ecran (calculee une fois). */
    @Override
    public AABB getRenderBoundingBox() {
        if (renderBox == null) {
            renderBox = new AABB(worldPosition).inflate(EnergyKind.WIND_RADIUS[tier()] + 1);
        }
        return renderBox;
    }
}

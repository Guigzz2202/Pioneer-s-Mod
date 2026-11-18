package fr.pioneers.world.hunt;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 1.0.5-beta (lot 10) : tuile des trophees (sans donnees) : elle porte le rendu de la bete ({@code TrophyRenderer}). */
public class TrophyTile extends BlockEntity {

    public TrophyTile(BlockPos pos, BlockState state) {
        super(PiHunt.TROPHY_TILE.get(), pos, state);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(1.5);
    }
}

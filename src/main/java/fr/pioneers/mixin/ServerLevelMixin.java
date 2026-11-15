package fr.pioneers.mixin;

import fr.pioneers.world.weather.Precipitations;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 1.0.5-beta (lot 2) : meteo d'un troncon ({@code ServerLevel#tickChunk}) : le bloc de surface recoit la precipitation de l'API
 * ({@link Precipitations}) au lieu de celle du biome. C'est ce qui remplit un chaudron de neige poudreuse sous la neige (de pluie sous la
 * pluie). Les couches de neige et la glace du meme tick passent par {@code Biome#shouldSnow} / {@code shouldFreeze} ({@link BiomeMixin}).
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @ModifyArg(method = "tickChunk", index = 3,
          at = @At(value = "INVOKE",
                target = "Lnet/minecraft/world/level/block/Block;handlePrecipitation(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/biome/Biome$Precipitation;)V"))
    private Biome.Precipitation pioneers$precipitation(BlockState state, Level level, BlockPos pos, Biome.Precipitation vanilla) {
        return Precipitations.vanillaAt(level, pos, vanilla);
    }
}

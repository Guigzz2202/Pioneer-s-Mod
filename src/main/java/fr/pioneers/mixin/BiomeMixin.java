package fr.pioneers.mixin;

import fr.pioneers.weather.Precipitations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.0.5-beta (lot 2) : couche de neige ({@code shouldSnow}) et glace ({@code shouldFreeze}) selon l'API des precipitations
 * ({@link Precipitations#shouldSnow}, {@link Precipitations#shouldFreeze}) quand un fournisseur repond pour ce lieu d'un vrai monde ; le
 * vanilla sinon (et toujours pendant la generation du monde). La neige d'hiver des saisons y repond « ni couche ni glace ».
 */
@Mixin(Biome.class)
public abstract class BiomeMixin {

    @Inject(method = "shouldSnow", at = @At("HEAD"), cancellable = true)
    private void pioneers$snow(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Boolean snow = Precipitations.shouldSnow(level, pos);
        if (snow != null) {
            cir.setReturnValue(snow);
        }
    }

    /** La forme a 3 arguments : celle a 2 arguments l'appelle (tick des troncons, givre). */
    @Inject(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z", at = @At("HEAD"), cancellable = true)
    private void pioneers$freeze(LevelReader level, BlockPos pos, boolean mustBeAtEdge, CallbackInfoReturnable<Boolean> cir) {
        Boolean freeze = Precipitations.shouldFreeze(level, pos, mustBeAtEdge);
        if (freeze != null) {
            cir.setReturnValue(freeze);
        }
    }
}

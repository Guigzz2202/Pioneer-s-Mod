package fr.pioneers.mixin;

import fr.pioneers.world.weather.Precipitations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.0.5-beta (lot 2) : {@code Level#isRainingAt} suit l'API des precipitations ({@link Precipitations}). Injection juste avant la lecture
 * de la precipitation du biome (le vanilla a deja verifie : il pleut sur le monde, le ciel est visible, rien au-dessus) : si un fournisseur
 * repond, « il pleut ici » = sa precipitation est de la pluie ; sinon le vanilla continue. Sous la neige : feux pas eteints, joueur pas
 * mouille, golem de neige intact, aucun eclair, terre labouree pas arrosee (comme dans un biome enneige vanilla).
 */
@Mixin(Level.class)
public abstract class LevelMixin {

    @Inject(method = "isRainingAt", cancellable = true,
          at = @At(value = "INVOKE",
                target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private void pioneers$precipitation(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Boolean raining = Precipitations.rainingAt((Level) (Object) this, pos);
        if (raining != null) {
            cir.setReturnValue(raining);
        }
    }
}

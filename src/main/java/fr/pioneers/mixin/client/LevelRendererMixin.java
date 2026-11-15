package fr.pioneers.mixin.client;

import fr.pioneers.weather.Precipitations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.0.5-beta (lot 2) : la pluie et la neige DESSINEES par le vanilla ({@code renderSnowAndRain} : rideaux de pluie ou flocons ;
 * {@code tickRain} : eclaboussures et bruit, seulement sous la pluie) suivent la vraie precipitation de l'API ({@link Precipitations}) :
 * la neige d'hiver d'une plaine est la neige vanilla, ni plus ni moins. Seule la lecture de la precipitation de la colonne est redirigee.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Redirect(method = {"renderSnowAndRain", "tickRain"},
          at = @At(value = "INVOKE",
                target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation pioneers$precipitation(Biome biome, BlockPos pos) {
        return Precipitations.vanillaAt(Minecraft.getInstance().level, pos, biome.getPrecipitationAt(pos));
    }
}

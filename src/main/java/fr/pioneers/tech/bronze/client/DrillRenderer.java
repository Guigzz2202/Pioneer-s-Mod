package fr.pioneers.tech.bronze.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.api.multiblock.GeneratedHouseNbt;
import fr.pioneers.tech.industry.client.big.NbtAnimations;
import fr.pioneers.world.geology.drill.SteamDrillTile;
import fr.pioneers.tech.industry.multi.GeneratedNbtModels;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Foreuse a vapeur. 1.0.4-beta (lot 24) : le controleur est un BLOC PLEIN au bord du socle (forme de Guillaume) et le centre porte un vrai
 * train de forage en bronze ; l'ancienne table de rotation dessinee au-dessus du controleur depassait de la structure (retiree).
 * 1.0.4-beta (lot 25) : le train de forage en bronze de la structure FORMEE tourne quand la foreuse travaille (bloc du monde masque, dessine
 * ici dans sa case : {@link NbtAnimations}) ; bouffees de vapeur a la couronne ({@code T:industry/nbt_models.py HOUSE_SPINS / HOUSE_FX}) ;
 * les deblais au pied du train restent ceux du serveur.
 */
public class DrillRenderer implements BlockEntityRenderer<SteamDrillTile> {

    public DrillRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull SteamDrillTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        GeneratedNbtModels.Anim anim = GeneratedNbtModels.house(SteamDrillTile.SHAPE);
        GeneratedHouseNbt.Shape s = GeneratedHouseNbt.of(SteamDrillTile.SHAPE);
        if (tile.getLevel() == null || anim == null || s == null || !tile.formed()) {
            return;
        }
        NbtAnimations.draw(tile.getLevel(), tile.getBlockPos(), tile.facing(), s.cx(), s.cy(), s.cz(), anim, tile.clientWorking(), 0, partial, pose,
              buffers);
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull SteamDrillTile tile) {
        return true;
    }
}

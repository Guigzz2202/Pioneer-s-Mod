package fr.pioneers.logistics.warehouse.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.tech.kinetics.client.KDraw;
import fr.pioneers.tech.kinetics.client.WarehouseVisuals;
import fr.pioneers.core.RenderClock;
import fr.pioneers.logistics.warehouse.CrateBlock;
import fr.pioneers.logistics.warehouse.CrateHatchTile;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 1) : battants de la TRAPPE d'une caisse d'entrepot formee : ils s'ouvrent quand un colis monte ou descend (colis connus du
 * client, {@link CounterClient#hatchBusy}), se referment ensuite (au quart de seconde). Le puits sous la trappe est dans le modele du bloc.
 */
public class HatchRenderer implements BlockEntityRenderer<CrateHatchTile> {

    /** Ouverture courante et heure du dernier dessin (ticks) de chaque trappe. */
    private final Map<CrateHatchTile, float[]> doors = new WeakHashMap<>();

    public HatchRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull CrateHatchTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        if (tile.getLevel() == null || tile.getBlockState().getValue(CrateBlock.PART) == 0) {
            return;
        }
        float now = (float) RenderClock.time(partial);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        float[] st = doors.computeIfAbsent(tile, t -> new float[]{0, now});
        float target = CounterClient.hatchBusy(tile.getBlockPos()) ? 1 : 0;
        float dt = Math.max(0, Math.min(5, now - st[1]));
        st[1] = now;
        st[0] += Math.signum(target - st[0]) * Math.min(Math.abs(target - st[0]), dt * 0.16F);
        KDraw d = new KDraw(pose, buffers, light, overlay);
        d.push();
        if (tile.getBlockState().getValue(CrateBlock.AXIS) == Direction.Axis.X) {
            d.rotate(Direction.Axis.Y, -90, 8, 8, 8);
        }
        WarehouseVisuals.hatchDoors(d, st[0] * st[0] * (3 - 2 * st[0]));
        d.pop();
    }
}

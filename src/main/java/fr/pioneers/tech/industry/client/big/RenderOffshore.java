package fr.pioneers.tech.industry.client.big;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.core.RenderClock;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.OffshoreRisers;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 0.20.37 : colonnes montantes de la plate-forme offshore (demande de Guillaume : « ameliorer les animations ; c'est la plate-forme qui descend
 * ses tuyaux ; risers animes »). Sous chaque colonne du pont :
 * <ul>
 *     <li>compensateur de houle : cadre, quatre verins dont les tiges suivent la houle, la colonne monte et descend doucement ;</li>
 *     <li>tube de production descendu par la plate-forme, colliers tous les deux blocs ; pendant la descente, l'outil de pose (orange) au
 *     bout du tube, qui glisse d'un bloc par pas, et des bulles ;</li>
 *     <li>arrivee au fond : tete de puits sous-marine (bloc d'obturation jaune, voyant vert en production, orange a l'arret) ; en production,
 *     anneaux lumineux de brut qui remontent le tube et bulles a la tete de puits ;</li>
 *     <li>colonne en attente de tubes : bout de tube suspendu et feu rouge qui clignote.</li>
 * </ul>
 * Niveau de detail selon la distance (colliers et anneaux de pres seulement), pas de particules au-dela de 48 blocs.
 */
public final class RenderOffshore {

    private static final Map<Long, Long> LAST_FX = new HashMap<>();
    /** 0.20.46 (audit) : maillage de travail reutilise d'une colonne a l'autre et d'une image a l'autre (fil de rendu). */
    private static final BigMesh MESH = new BigMesh();

    private RenderOffshore() {
    }

    public static void render(ControllerTile tile, OffshoreRisers.View view, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        Level level = tile.getLevel();
        if (level == null || view.risers().isEmpty()) {
            return;
        }
        BlockState state = tile.getBlockState();
        boolean active = state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE);
        float t = (float) (RenderClock.time(partialTick) % 240_000.0);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cp = cam.getPosition();
        BlockPos c = tile.getBlockPos();
        long now = level.getGameTime();
        Long last = LAST_FX.get(c.asLong());
        boolean fx = last == null || last != now;
        LAST_FX.put(c.asLong(), now);
        if (LAST_FX.size() > 256) {
            LAST_FX.clear();
        }
        float lowerFrac = Math.max(0, Math.min(1, (float) (RenderClock.time(partialTick) - view.step()) / OffshoreRisers.STEP));
        for (int i = 0; i < view.risers().size(); i++) {
            OffshoreRisers.Riser r = view.risers().get(i);
            BlockPos p = r.pos;
            double dist = cp.distanceTo(Vec3.atCenterOf(p));
            if (dist > 192) {
                continue;
            }
            boolean near = dist < 64;
            boolean down = r.complete();
            boolean moving = !down && view.lowering() && r.lowered > 0;
            float depth = moving ? r.lowered - 1 + lowerFrac : r.lowered;
            BigMesh m = MESH.reset();
            float sway = (float) Math.sin(t * 0.05F + i * 1.7F);
            build(m, r, depth, down, moving, active, view.stock() > 0, t, i, sway, near);
            pose.pushPose();
            pose.translate(p.getX() - c.getX(), p.getY() - c.getY(), p.getZ() - c.getZ());
            pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
            int light = LevelRenderer.getLightColor(level, p.below());
            RenderBigMachine.drawMesh(m, pose, buffers, light);
            pose.popPose();
            if (fx && dist < 48) {
                particles(level, r, depth, down, moving, active);
            }
        }
    }

    /** Geometrie d'une colonne (px, origine = coin bas du module, le tube descend sous y = 0). */
    static void build(BigMesh m, OffshoreRisers.Riser r, float depth, boolean down, boolean moving, boolean active, boolean stock, float t, int i,
          float sway, boolean near) {
        float cx = 8, cz = 8;
        float heave = sway * 0.8F;
        //Compensateur de houle sous le pont : cadre, quatre verins (corps fixes, tiges qui suivent la houle)
        m.box(1, -1.5F, 1, 15, -0.2F, 15, BigMat.STEEL_D, null, 8);
        m.cyl('y', cx, cz, 4.2F, -7 + heave, -5 + heave, BigMat.CHROME, 10, null, 2, false, 1.2F);
        for (int k = 0; k < 4; k++) {
            Matrix4f rot = BigMesh.rot('y', cx, 0, cz, 90 * k + 45);
            m.box(cx - 0.9F, -4.2F, cz + 5.2F, cx + 0.9F, -1.5F, cz + 7, BigMat.MOTOR, rot, 2);
            m.box(cx - 0.4F, -6.2F + heave, cz + 5.7F, cx + 0.4F, -4.2F, cz + 6.5F, BigMat.CHROME, rot, 1);
            m.box(cx - 0.5F, -6.6F + heave, cz + 4.6F, cx + 0.5F, -6.0F + heave, cz + 6.6F, BigMat.STEEL_D, rot, 1);
        }
        float bottom = -16 * depth;
        if (r.lowered <= 0 && !moving) {
            //En attente : bout de tube suspendu, feu rouge qui clignote (rien en stock) ou orange (descente imminente)
            m.cyl('y', cx, cz, 2.4F, -14 + heave, -1.5F, BigMat.PIPE, 8, null, 4);
            if (((int) (t / 10) + i) % 2 == 0) {
                m.glow(15).color(stock ? 0xFFFFA020 : 0xFFFF3020);
                m.box(cx + 4.4F, -3.2F, cz - 0.6F, cx + 5.6F, -2, cz + 0.6F, BigMat.LED, null, 1);
                m.glow(0).color(0xFFFFFFFF);
            }
            return;
        }
        //Tube de production (de sous le pont jusqu'au bout descendu), legere ondulation avec la houle
        m.cyl('y', cx, cz, 2.4F, Math.min(-1.6F, bottom + heave * 0.3F), -1.5F, BigMat.PIPE, 8, null, 16);
        if (near) {
            for (float y = -32; y > bottom + 4; y -= 32) {
                m.cyl('y', cx, cz, 3.1F, y - 0.8F, y + 0.8F, BigMat.CHROME, 8, null, 2);
            }
        }
        if (moving) {
            //Outil de pose au bout du tube : corps orange, cone de guidage, feux
            m.cyl('y', cx, cz, 4, bottom - 2, bottom + 6, BigMat.PAINT_O, 10, null, 4);
            m.cyl('y', cx, cz, 2.6F, bottom - 5, bottom - 2, BigMat.STEEL_D, 8, null, 2);
            m.glow(15).color(0xFFFFE070);
            m.box(cx - 0.6F, bottom + 3, cz - 4.6F, cx + 0.6F, bottom + 4.2F, cz - 3.9F, BigMat.LED, null, 1);
            m.box(cx - 0.6F, bottom + 3, cz + 3.9F, cx + 0.6F, bottom + 4.2F, cz + 4.6F, BigMat.LED, null, 1);
            m.glow(0).color(0xFFFFFFFF);
            return;
        }
        if (!down) {
            return;
        }
        //Tete de puits sous-marine : socle, bloc d'obturation jaune, vannes, voyant
        m.box(cx - 7, bottom, cz - 7, cx + 7, bottom + 1.5F, cz + 7, BigMat.STEEL_D, null, 8);
        m.box(cx - 4.5F, bottom + 1.5F, cz - 4.5F, cx + 4.5F, bottom + 11, cz + 4.5F, BigMat.PAINT_O, null, 8);
        m.cyl('y', cx, cz, 3.4F, bottom + 11, bottom + 14, BigMat.STEEL_D, 10, null, 2);
        for (int k = 0; k < 4; k++) {
            Matrix4f rot = BigMesh.rot('y', cx, 0, cz, 90 * k);
            m.box(cx - 1, bottom + 4, cz + 4.5F, cx + 1, bottom + 6, cz + 6.5F, BigMat.STEEL_D, rot, 1);
            //Volant de vanne, qui tourne en production
            Matrix4f wheel = BigMesh.chain(BigMesh.rot('z', cx, bottom + 5, 0, active ? t * 4 : 0), rot);
            m.box(cx - 1.8F, bottom + 4.8F, cz + 6.5F, cx + 1.8F, bottom + 5.2F, cz + 6.9F, BigMat.CHROME, wheel, 1);
            m.box(cx - 0.2F, bottom + 3.2F, cz + 6.5F, cx + 0.2F, bottom + 6.8F, cz + 6.9F, BigMat.CHROME, wheel, 1);
        }
        boolean blink = ((int) (t / (active ? 8 : 20)) + i) % 2 == 0;
        m.glow(blink ? 15 : 4).color(active ? 0xFF40FF70 : 0xFFFFA020);
        m.box(cx - 0.7F, bottom + 14, cz - 0.7F, cx + 0.7F, bottom + 15.4F, cz + 0.7F, BigMat.LED, null, 1);
        m.glow(0).color(0xFFFFFFFF);
        if (active && near) {
            //Brut qui remonte : anneaux lumineux le long du tube
            float len = -bottom - 2;
            for (int k = 0; k < 6; k++) {
                float u = ((t * 0.9F + k * len / 6F) % len);
                float y = bottom + 14 + u;
                if (y > -2) {
                    continue;
                }
                m.glow(10).color(0xFFE08A20);
                m.cyl('y', cx, cz, 2.7F, y, y + 1.2F, BigMat.LED, 8, null, 1, false, 0.4F);
            }
            m.glow(0).color(0xFFFFFFFF);
        }
    }

    private static void particles(Level level, OffshoreRisers.Riser r, float depth, boolean down, boolean moving, boolean active) {
        RandomSource rnd = level.random;
        BlockPos p = r.pos;
        if (moving && rnd.nextInt(2) == 0) {
            double y = p.getY() - depth - 0.2;
            level.addParticle(ParticleTypes.BUBBLE, p.getX() + 0.5 + (rnd.nextDouble() - 0.5) * 0.4, y, p.getZ() + 0.5 + (rnd.nextDouble() - 0.5) * 0.4,
                  0, 0.08, 0);
        }
        if (down && active && rnd.nextInt(3) == 0) {
            double y = p.getY() - r.target + 1.0;
            level.addParticle(ParticleTypes.BUBBLE, p.getX() + 0.5 + (rnd.nextDouble() - 0.5) * 0.6, y, p.getZ() + 0.5 + (rnd.nextDouble() - 0.5) * 0.6,
                  0, 0.05, 0);
        }
    }
}

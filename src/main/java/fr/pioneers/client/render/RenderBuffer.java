package fr.pioneers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.transport.buffer.BufferTile;
import fr.pioneers.transport.buffer.BufferType;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Jauges des buffers de fluide (fut) et de gaz (bonbonne) : le contenu dessine dans les quatre fenetres du modele (une par face
 * horizontale), a la hauteur du remplissage. Geometrie en pixels de bloc, la meme que le generateur ({@code transport_buffers.py},
 * {@code fill_box}) : largeur u 6,5..9,5 vue de l'exterieur, profondeur 0,1 (face avant) a 0,35 depuis le bord, hauteur y 3,5..12,5 (fut)
 * ou 4,5..10,5 (bonbonne). Fluide : sa texture immobile et sa teinte ; gaz : la texture du gaz teintee, un peu transparente.
 * <p>
 * 0.20.46 (audit) : rien au-dela de 48 blocs ; sprite et teinte gardes par fluide / gaz ; plus d'allocation par quad ni par sommet (memes
 * sommets, UV et couleurs qu'avant).
 */
public class RenderBuffer implements BlockEntityRenderer<BufferTile> {

    private static final float U0 = 6.5F, U1 = 9.5F, D0 = 0.1F, D1 = 0.35F;
    private static final float GAS_ALPHA = 0.85F;
    private static final double MAX_DISTANCE_SQR = 48 * 48;
    /** Apparence d'un contenu : sprite, couleur ARGB, lumineux (fluide qui eclaire). */
    private record Look(TextureAtlasSprite sprite, int argb, boolean glow) {
    }
    /** Fil de rendu seulement ; vides apres un rechargement des ressources. Fluides sans NBT seulement (la texture peut dependre du NBT). */
    private static final Map<Fluid, Look> FLUIDS = new IdentityHashMap<>();
    private static final Map<Gas, Look> GASES = new IdentityHashMap<>();
    @Nullable
    private static Object looksOf;
    /** Travail du fil de rendu : sommets du quad (repere de la face puis du bloc), UV, normale. */
    private static final float[][] LOCAL = new float[4][3], WORLD = new float[4][3], UV = new float[4][2];
    private static final float[] NORMAL_LOCAL = new float[3];
    private static final Vector4f POSITION = new Vector4f();
    private static final Vector3f NORMAL = new Vector3f();

    public RenderBuffer(BlockEntityRendererProvider.Context context) {
    }

    /** 0.20.46 (audit) : seuls les buffers a jauge, a moins de 48 blocs, passent par le rendu (et la lecture de la lumiere). */
    @Override
    public boolean shouldRender(@NotNull BufferTile tile, @NotNull Vec3 camera) {
        BufferType type = tile.getBufferType();
        return (type == BufferType.FLUID || type == BufferType.GAS) && !beyond(camera, tile.getBlockPos(), MAX_DISTANCE_SQR);
    }

    @Override
    public void render(@NotNull BufferTile tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        BufferType type = tile.getBufferType();
        if ((type != BufferType.FLUID && type != BufferType.GAS) || tile.getLevel() == null) {
            return;
        }
        double fill = tile.shownFill(tile.getLevel().getGameTime());
        if (fill <= 0) {
            return;
        }
        float y0 = type == BufferType.FLUID ? 3.5F : 4.5F, y1 = type == BufferType.FLUID ? 12.5F : 10.5F;
        //Au moins un pixel visible des qu'il y a du contenu
        float top = (float) Math.min(y1, Math.max(y0 + 1, y0 + (y1 - y0) * fill));
        Look look;
        if (type == BufferType.FLUID) {
            FluidTank tank = tile.getFluidTank();
            if (tank == null || tank.isEmpty()) {
                return;
            }
            look = look(tank.getFluid());
        } else {
            GasStack stack = tile.getGasTank() == null ? GasStack.EMPTY : tile.getGasTank().getStack();
            if (stack.isEmpty()) {
                return;
            }
            look = look(stack.getGas());
        }
        TextureAtlasSprite sprite = look.sprite();
        int argb = look.argb();
        int packedLight = look.glow() ? LightTexture.FULL_BRIGHT : light;
        float a = (argb >>> 24 & 255) / 255F, r = (argb >> 16 & 255) / 255F, g = (argb >> 8 & 255) / 255F, b = (argb & 255) / 255F;
        if (a <= 0) {
            a = 1;
        }
        VertexConsumer consumer = buffers.getBuffer(TRANSLUCENT);
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        for (int face = 0; face < 4; face++) {
            //Face avant (a d = 0,1), tournee vers l'exterieur
            corner(0, U0, y0, D0);
            corner(1, U0, top, D0);
            corner(2, U1, top, D0);
            corner(3, U1, y0, D0);
            quad(consumer, matrix, normal, sprite, r, g, b, a, packedLight, face, 0, 0, -1);
            //Surface du contenu (vue d'en haut, dans l'epaisseur de la fenetre)
            if (top < y1) {
                corner(0, U0, top, D0);
                corner(1, U0, top, D1);
                corner(2, U1, top, D1);
                corner(3, U1, top, D0);
                quad(consumer, matrix, normal, sprite, r, g, b, a, packedLight, face, 0, 1, 0);
            }
        }
    }

    private static Look look(FluidStack stack) {
        if (stack.getTag() != null) {
            return fluidLook(stack);
        }
        checkLooks();
        Look look = FLUIDS.get(stack.getFluid());
        if (look == null) {
            look = fluidLook(stack);
            FLUIDS.put(stack.getFluid(), look);
        }
        return look;
    }

    private static Look fluidLook(FluidStack stack) {
        IClientFluidTypeExtensions fluid = IClientFluidTypeExtensions.of(stack.getFluid());
        return new Look(atlas(fluid.getStillTexture(stack)), fluid.getTintColor(stack),
              stack.getFluid().getFluidType().getLightLevel(stack) > 0);
    }

    private static Look look(Gas gas) {
        checkLooks();
        Look look = GASES.get(gas);
        if (look == null) {
            look = new Look(atlas(gas.getIcon()), (Math.round(GAS_ALPHA * 255) << 24) | (gas.getTint() & 0xFFFFFF), false);
            GASES.put(gas, look);
        }
        return look;
    }

    private static void checkLooks() {
        Object generation = generation();
        if (generation != looksOf) {
            FLUIDS.clear();
            GASES.clear();
            looksOf = generation;
        }
    }

    private static void corner(int i, float u, float y, float d) {
        LOCAL[i][0] = u;
        LOCAL[i][1] = y;
        LOCAL[i][2] = d;
    }

    /**
     * Quad defini dans le repere de la face ({@code face} : 0 nord, 1 sud, 2 ouest, 3 est ; points {@link #LOCAL} en (u, y, d) pixels) ; la
     * normale locale (nu, ny, nd) est tournee de meme. Les sommets sont emis dans les deux sens de rotation pour rester visibles malgre le
     * tri des faces arriere.
     */
    private static void quad(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, TextureAtlasSprite sprite, float r, float g, float b, float a,
          int light, int face, float nu, float ny, float nd) {
        for (int i = 0; i < 4; i++) {
            toBlock(face, LOCAL[i][0], LOCAL[i][1], LOCAL[i][2], WORLD[i]);
        }
        toNormal(face, nu, ny, nd, NORMAL_LOCAL);
        //Normale transformee une fois pour les huit sommets (meme calcul que VertexConsumer.normal(Matrix3f, ...))
        normal.transform(NORMAL.set(NORMAL_LOCAL[0], NORMAL_LOCAL[1], NORMAL_LOCAL[2]));
        //UV : la texture prise a la position du point (u sur la largeur, y ou d sur la hauteur), comme un bloc plein
        for (int i = 0; i < 4; i++) {
            UV[i][0] = sprite.getU(LOCAL[i][0]);
            UV[i][1] = sprite.getV(ny != 0 ? LOCAL[i][2] * 16 / 3 : 16 - LOCAL[i][1]);
        }
        for (int i = 0; i < 4; i++) {
            vertex(consumer, matrix, WORLD[i], UV[i], r, g, b, a, light);
        }
        for (int i = 3; i >= 0; i--) {
            vertex(consumer, matrix, WORLD[i], UV[i], r, g, b, a, light);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float[] p, float[] uv, float r, float g, float b, float a, int light) {
        matrix.transform(POSITION.set(p[0], p[1], p[2], 1.0F));
        consumer.vertex(POSITION.x, POSITION.y, POSITION.z).color(r, g, b, a).uv(uv[0], uv[1]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
              .normal(NORMAL.x, NORMAL.y, NORMAL.z).endVertex();
    }

    /** (u, y, d) en pixels dans le repere d'une face → coordonnees du bloc (0..1), ecrites dans {@code out}. */
    private static void toBlock(int face, float u, float y, float d, float[] out) {
        float x, z;
        switch (face) {
            case 0 -> {
                x = u;
                z = d;
            }
            case 1 -> {
                x = 16 - u;
                z = 16 - d;
            }
            case 2 -> {
                x = d;
                z = 16 - u;
            }
            default -> {
                x = 16 - d;
                z = u;
            }
        }
        out[0] = x / 16F;
        out[1] = y / 16F;
        out[2] = z / 16F;
    }

    /** Normale locale (nu, ny, nd) ; nd = -1 pointe vers l'exterieur de la face. */
    private static void toNormal(int face, float nu, float ny, float nd, float[] out) {
        switch (face) {
            case 0 -> {
                out[0] = nu;
                out[1] = ny;
                out[2] = nd;
            }
            case 1 -> {
                out[0] = -nu;
                out[1] = ny;
                out[2] = -nd;
            }
            case 2 -> {
                out[0] = nd;
                out[1] = ny;
                out[2] = -nu;
            }
            default -> {
                out[0] = -nd;
                out[1] = ny;
                out[2] = nu;
            }
        }
    }

    // ------------------------------------------------------------------
    // Outils repris de l'ancien rendu des cables transparents (1.11 : RenderCableFlow retire)
    // ------------------------------------------------------------------

    private static final RenderType TRANSLUCENT = RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS);

    /** Generation des ressources graphiques (change a chaque rechargement des modeles) : invalide les sprites gardes. */
    private static Object generation() {
        return Minecraft.getInstance().getModelManager().getMissingModel();
    }

    private static boolean beyond(Vec3 camera, BlockPos pos, double maxSqr) {
        double dx = camera.x - (pos.getX() + 0.5), dy = camera.y - (pos.getY() + 0.5), dz = camera.z - (pos.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz > maxSqr;
    }

    private static TextureAtlasSprite atlas(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }
}

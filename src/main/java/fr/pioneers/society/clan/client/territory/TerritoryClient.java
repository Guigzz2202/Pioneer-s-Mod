package fr.pioneers.society.clan.client.territory;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.society.clan.seal.SealItems;
import fr.pioneers.society.clan.territory.TerritoryNet;
import fr.pioneers.client.PiKeys;
import fr.pioneers.society.rp.client.RpClient;
import fr.pioneers.core.profile.RpNet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * 1.9 (MAJ RP, bloc 4) : cote client du territoire. Sceau en main : la vue arrive aussi, et le bloc designe que l'on regarde porte une marque
 * discrete (particules de cire) et le nom de sa fonction. Le client n'affiche que ce que le serveur envoie.
 * <p>
 * 1.9.2 (retours de Guillaume : « Quand je claim des chunks, je ne souhaite pas voir de bordure blanche bizarre ») : l'ancienne cage de
 * lignes (trois lignes flottantes a la hauteur du joueur et des montants tous les 4 blocs, blanches pour un Solitaire dont le clan solo a
 * la teinture 0) est retiree. A la place, apres un claim ou un abandon, et sur demande (touche U, {@code /clan borders}), un seul liseré
 * fin pose au sol (il suit le relief), a la couleur du clan (l'or de la carte pour un clan solo, rouge pour un ennemi en guerre), apparait
 * puis s'efface en {@link #FLASH_MS} ms.
 */
public final class TerritoryClient {

    public static final KeyMapping BORDERS = new KeyMapping("key.pioneers.clan_borders", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
          GLFW.GLFW_KEY_U, PiKeys.CATEGORY);

    /** Duree du liseré (ms), dont l'apparition et l'effacement. */
    static final long FLASH_MS = 6000, FADE_IN_MS = 300, FADE_OUT_MS = 2000;
    /** Or du joueur sur la carte : couleur d'un clan solo (jamais choisie). */
    static final int SOLO_RGB = 0xFFD24A;

    /** Derniere vue recue. */
    static boolean on;
    /** Debut du liseré en cours (horloge du client), 0 : aucun. */
    static long flashAt;
    static int cx, cz, r;
    static byte[] codes = new byte[0];
    static int[] rel = new int[0], color = new int[0];
    static final Long2ObjectOpenHashMap<String> SEALS = new Long2ObjectOpenHashMap<>();
    private static int markTicks;

    private TerritoryClient() {
    }

    // ------------------------------------------------------------------ enregistrements (bus du mod)
    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {

        private Setup() {
        }

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                RpClient.on(TerritoryNet.BORDERS, TerritoryClient::receive);
                RpClient.on(TerritoryNet.SEAL_CHOICE, t -> Minecraft.getInstance().setScreen(new SealScreen(t)));
            });
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(BORDERS);
        }
    }

    static void receive(CompoundTag t) {
        on = t.getBoolean("on");
        if (t.getBoolean("flash")) {
            flashAt = net.minecraft.Util.getMillis();
        }
        SEALS.clear();
        if (!on || !t.contains("codes")) {
            codes = new byte[0];
            return;
        }
        cx = t.getInt("cx");
        cz = t.getInt("cz");
        r = Math.min(16, Math.max(0, t.getInt("r")));
        codes = t.getByteArray("codes");
        ListTag pal = t.getList("palette", Tag.TAG_COMPOUND);
        rel = new int[pal.size() + 1];
        color = new int[pal.size() + 1];
        for (int i = 0; i < pal.size(); i++) {
            rel[i + 1] = pal.getCompound(i).getInt("rel");
            color[i + 1] = pal.getCompound(i).getInt("color");
        }
        long[] pos = t.getLongArray("seals");
        ListTag fns = t.getList("fns", Tag.TAG_STRING);
        for (int i = 0; i < pos.length && i < fns.size(); i++) {
            SEALS.put(pos[i], fns.getString(i));
        }
    }

    /** Code d'un chunk de la vue (0 : libre ou hors vue ; bit 64 : en declin). */
    static int code(int x, int z) {
        int dx = x - cx + r, dz = z - cz + r, n = 2 * r + 1;
        if (dx < 0 || dz < 0 || dx >= n || dz >= n || codes.length != n * n) {
            return 0;
        }
        return codes[dz * n + dx] & 0xFF;
    }

    // ------------------------------------------------------------------ touche et marques (bus Forge)
    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
    public static final class Events {

        private Events() {
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (event.phase != TickEvent.Phase.END || mc.player == null || mc.level == null) {
                return;
            }
            while (BORDERS.consumeClick()) {
                //1.9.2 : liseré ponctuel (le serveur renvoie la vue marquee « flash »)
                RpNet.toServer(TerritoryNet.WATCH, new CompoundTag());
                mc.player.displayClientMessage(Component.translatable("clan.pioneers.territory.borders_flash"), true);
            }
            marks(mc, mc.player);
        }

        @SubscribeEvent
        public static void render(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS && flashAt > 0 && codes.length > 0) {
                float k = fade(net.minecraft.Util.getMillis() - flashAt);
                if (k <= 0) {
                    flashAt = 0;
                } else {
                    drawBorders(event, k);
                }
            }
        }
    }

    /** Sceau en main, regard sur un bloc designe : marque de cire et nom de la fonction (quelques fois par seconde). */
    static void marks(Minecraft mc, LocalPlayer p) {
        if (SEALS.isEmpty() || !(p.getMainHandItem().is(SealItems.FUNCTION_SEAL.get()) || p.getOffhandItem().is(SealItems.FUNCTION_SEAL.get()))) {
            return;
        }
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult b) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        String fn = SEALS.get(b.getBlockPos().asLong());
        if (fn == null || ++markTicks % 4 != 0) {
            return;
        }
        BlockPos pos = b.getBlockPos();
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + 0.15 + p.getRandom().nextDouble() * 0.7, z = pos.getZ() + 0.15 + p.getRandom().nextDouble() * 0.7;
            mc.level.addParticle(ParticleTypes.WAX_ON, x, pos.getY() + 1.05, z, 0, 0.01, 0);
        }
        if (markTicks % 20 == 0) {
            p.displayClientMessage(Component.translatable("clan.pioneers.seal.looking", Component.translatable("clan.pioneers.function." + fn)), true);
        }
    }

    // ------------------------------------------------------------------ liseré (1.9.2)
    /** Opacite relative du liseré a {@code dt} ms de son debut : monte en 0,3 s, tient, s'efface les 2 dernieres secondes (0 : fini). */
    static float fade(long dt) {
        if (dt < 0 || dt >= FLASH_MS) {
            return 0;
        }
        if (dt < FADE_IN_MS) {
            return dt / (float) FADE_IN_MS;
        }
        long left = FLASH_MS - dt;
        return left < FADE_OUT_MS ? left / (float) FADE_OUT_MS : 1F;
    }

    static void drawBorders(RenderLevelStageEvent event, float k) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                int here = code(x, z);
                //chaque limite une seule fois : vers l'est et vers le sud (et les bords de la vue)
                edge(mc, vc, m, n, here, code(x + 1, z), (x + 1) * 16, z * 16, true, k);
                edge(mc, vc, m, n, here, code(x, z + 1), x * 16, (z + 1) * 16, false, k);
                if (x == cx - r) {
                    edge(mc, vc, m, n, here, 0, x * 16, z * 16, true, k);
                }
                if (z == cz - r) {
                    edge(mc, vc, m, n, here, 0, x * 16, z * 16, false, k);
                }
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /**
     * Limite entre deux chunks de proprietaires differents, longue de 16 blocs a partir de (x0, z0), le long de z ({@code alongZ}) ou de x :
     * un trait par bloc, pose 0,06 bloc au-dessus du sol le plus haut des deux cotes, relie au suivant par un court trait vertical.
     */
    private static void edge(Minecraft mc, VertexConsumer vc, Matrix4f m, Matrix3f n, int a, int b, int x0, int z0, boolean alongZ, float k) {
        int ia = a & 63, ib = b & 63;
        if (ia == ib) {
            return;
        }
        int side = ia != 0 ? a : b;
        int idx = side & 63;
        if (idx >= rel.length) {
            return;
        }
        int rgb = rel[idx] == TerritoryNet.REL_WAR ? 0xD03A2E : color[idx] < 0 ? SOLO_RGB : DyeColor.byId(color[idx] & 15).getTextColor();
        float alpha = ((side & 64) != 0 ? 0.3F : 0.6F) * k;
        float rr = (rgb >> 16 & 255) / 255F, gg = (rgb >> 8 & 255) / 255F, bb = (rgb & 255) / 255F;
        double prev = Double.NaN;
        for (int i = 0; i < 16; i++) {
            int bx = alongZ ? x0 : x0 + i, bz = alongZ ? z0 + i : z0;
            if (!mc.level.hasChunkAt(new BlockPos(bx, 0, bz))) {
                prev = Double.NaN;
                continue;
            }
            //sol des deux cotes de la limite (le plus haut : le trait ne s'enfonce pas)
            int h = Math.max(ground(mc, bx, bz), ground(mc, alongZ ? bx - 1 : bx, alongZ ? bz : bz - 1));
            double y = h + 0.06;
            double ax = bx, az = bz, cx1 = alongZ ? bx : bx + 1, cz1 = alongZ ? bz + 1 : bz;
            if (!Double.isNaN(prev) && prev != y) {
                line(vc, m, n, ax, prev, az, ax, y, az, rr, gg, bb, alpha);
            }
            line(vc, m, n, ax, y, az, cx1, y, cz1, rr, gg, bb, alpha);
            prev = y;
        }
    }

    /**
     * Hauteur du sol d'une colonne cote client : la carte de hauteur MOTION_BLOCKING (la seule que le serveur envoie avec WORLD_SURFACE),
     * sans le feuillage ni les troncs (le liseré ne passe pas sur la cime des arbres).
     */
    private static int ground(Minecraft mc, int x, int z) {
        int h = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), min = mc.level.getMinBuildHeight();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, h - 1, z);
        for (int i = 0; i < 40 && h > min; i++) {
            net.minecraft.world.level.block.state.BlockState st = mc.level.getBlockState(p.setY(h - 1));
            if (!st.is(net.minecraft.tags.BlockTags.LEAVES) && !st.is(net.minecraft.tags.BlockTags.LOGS)) {
                break;
            }
            h--;
        }
        return h;
    }

    private static void line(VertexConsumer vc, Matrix4f m, Matrix3f n, double ax, double ay, double az, double bx, double by, double bz, float r,
          float g, float b, float a) {
        float dx = (float) (bx - ax), dy = (float) (by - ay), dz = (float) (bz - az);
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l <= 0) {
            return;
        }
        dx /= l;
        dy /= l;
        dz /= l;
        vc.vertex(m, (float) ax, (float) ay, (float) az).color(r, g, b, a).normal(n, dx, dy, dz).endVertex();
        vc.vertex(m, (float) bx, (float) by, (float) bz).color(r, g, b, a).normal(n, dx, dy, dz).endVertex();
    }
}

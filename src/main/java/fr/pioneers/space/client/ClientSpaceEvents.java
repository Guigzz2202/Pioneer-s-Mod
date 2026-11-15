package fr.pioneers.space.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.pioneers.Pioneers;
import fr.pioneers.core.common.PiClientConfig;
import fr.pioneers.core.RenderClock;
import fr.pioneers.core.network.PacketSpaceEvent;
import fr.pioneers.space.event.SpaceEventType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Evenements spatiaux cote client : bandeau d'alerte, brouillard et poussiere de la tempete martienne, lueur des eruptions solaires,
 * aurores dans le ciel nocturne, ciel assombri des supertempetes. 1.6 : le bandeau d'une aurore rappelle le bonus de chance a ciel ouvert.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class ClientSpaceEvents {

    private ClientSpaceEvents() {
    }

    private static SpaceEventType type;
    private static long end, flareEnd;
    private static float intensity, flareIntensity;
    /** Intensite affichee (fondu d'entree et de sortie). */
    private static float fade, flareFade;

    public static void receive(PacketSpaceEvent packet) {
        ClientLevel level = Minecraft.getInstance().level;
        long now = level == null ? 0 : level.getGameTime();
        type = packet.type() < 0 || packet.type() >= SpaceEventType.values().length ? null : SpaceEventType.values()[packet.type()];
        end = now + packet.remaining();
        intensity = packet.intensity();
        flareEnd = packet.flareRemaining() > 0 ? now + packet.flareRemaining() : 0;
        flareIntensity = packet.flareIntensity();
    }

    private static boolean active(SpaceEventType wanted) {
        ClientLevel level = Minecraft.getInstance().level;
        return type == wanted && level != null && level.getGameTime() < end;
    }

    private static boolean flareActive() {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && level.getGameTime() < flareEnd;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
            return;
        }
        boolean storm = active(SpaceEventType.DUST_STORM) || active(SpaceEventType.GIANT_STORM) || active(SpaceEventType.AURORA)
                        || active(SpaceEventType.METEOR_SHOWER);
        fade = Mth.clamp(fade + (storm ? 0.01F : -0.02F), 0, 1);
        flareFade = Mth.clamp(flareFade + (flareActive() ? 0.02F : -0.03F), 0, 1);
        if (active(SpaceEventType.DUST_STORM)) {
            dust(minecraft.level, minecraft.player);
        }
    }

    /** Poussiere poussee par le vent, et le vent lui-meme a decouvert. */
    private static void dust(ClientLevel level, LocalPlayer player) {
        float strength = fade * intensity;
        Vector3f color = new Vector3f(0.78F, 0.52F, 0.32F);
        double wx = 0.9, wz = 0.35;
        for (int i = 0; i < 6 * strength + 1; i++) {
            double x = player.getX() + (level.random.nextDouble() - 0.5) * 24, z = player.getZ() + (level.random.nextDouble() - 0.5) * 24;
            double y = player.getY() + (level.random.nextDouble() - 0.3) * 8;
            level.addParticle(new DustParticleOptions(color, 1.6F), x, y, z, wx * 0.6, 0, wz * 0.6);
        }
        if (level.canSeeSky(BlockPos.containing(player.getEyePosition())) && !player.isPassenger()) {
            player.setDeltaMovement(player.getDeltaMovement().add(wx * 0.004 * strength, 0, wz * 0.004 * strength));
        }
    }

    /**
     * Musiques spatiales du mod coupees dans la configuration (les disques, joues dans la categorie « juke-box », ne sont pas concernes ;
     * les musiques de la Terre ont leur propre reglage, voir client/music/EarthMusic).
     */
    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound != null && sound.getSource() == SoundSource.MUSIC && Pioneers.MODID.equals(sound.getLocation().getNamespace())
            && sound.getLocation().getPath().startsWith("music.space.") && !PiClientConfig.SPACE_MUSIC.get()) {
            event.setSound(null);
        }
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!PiClientConfig.EVENT_VISUALS.get()) {
            return;
        }
        if (fade > 0 && type == SpaceEventType.DUST_STORM) {
            float k = fade * Math.min(1, intensity);
            event.setRed(Mth.lerp(k, event.getRed(), 0.62F));
            event.setGreen(Mth.lerp(k, event.getGreen(), 0.42F));
            event.setBlue(Mth.lerp(k, event.getBlue(), 0.26F));
        } else if (fade > 0 && type == SpaceEventType.GIANT_STORM) {
            float k = fade * 0.6F;
            event.setRed(event.getRed() * (1 - k));
            event.setGreen(event.getGreen() * (1 - k));
            event.setBlue(event.getBlue() * (1 - k * 0.7F));
        }
        if (flareFade > 0) {
            float k = flareFade * 0.35F * flareIntensity;
            event.setRed(Mth.lerp(k, event.getRed(), 1.0F));
            event.setGreen(Mth.lerp(k, event.getGreen(), 0.78F));
            event.setBlue(Mth.lerp(k, event.getBlue(), 0.45F));
        }
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (!PiClientConfig.EVENT_VISUALS.get()) {
            return;
        }
        if (fade > 0 && (type == SpaceEventType.DUST_STORM || type == SpaceEventType.GIANT_STORM)) {
            float far = Mth.lerp(fade * Math.min(1, intensity), event.getFarPlaneDistance(), type == SpaceEventType.DUST_STORM ? 28 : 48);
            event.setFarPlaneDistance(far);
            event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.1F));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int sw = graphics.guiWidth(), sh = graphics.guiHeight();
        //Lueur de l'eruption (bords de l'ecran qui pulsent)
        if (flareFade > 0) {
            float pulse = 0.5F + 0.5F * Mth.sin(minecraft.level.getGameTime() * 0.15F);
            int a = (int) (flareFade * flareIntensity * (25 + 25 * pulse));
            graphics.fillGradient(0, 0, sw, sh / 4, a << 24 | 0xFFB040, 0);
            graphics.fillGradient(0, sh * 3 / 4, sw, sh, 0, a << 24 | 0xFFB040);
        }
        //Bandeau des evenements en cours
        if (!PiClientConfig.EVENT_BANNERS.get()) {
            return;
        }
        int y = 4;
        long now = minecraft.level.getGameTime();
        if (type != null && now < end) {
            banner(graphics, minecraft, type, (int) (end - now), y);
            y += 13;
        }
        if (flareActive()) {
            banner(graphics, minecraft, SpaceEventType.SOLAR_FLARE, (int) (flareEnd - now), y);
        }
    }

    private static void banner(GuiGraphics graphics, Minecraft minecraft, SpaceEventType event, int remaining, int y) {
        int seconds = remaining / 20;
        MutableComponent text = Component.translatable(event.getTranslationKey()).append(String.format("  %d:%02d", seconds / 60, seconds % 60));
        //1.6 : sous une aurore, a ciel ouvert, le joueur a +1 de chance (SpaceEvents.auroraLuck) : on le rappelle dans le bandeau
        if (event == SpaceEventType.AURORA && minecraft.player != null && minecraft.level != null
            && minecraft.level.canSeeSky(BlockPos.containing(minecraft.player.getEyePosition()))) {
            text.append("  ").append(Component.translatable("event.pioneers.aurora.luck").withStyle(style -> style.withColor(0xB0FFD0)));
        }
        //1.0.5-beta (lot 26, retour 16) : milieu du bord haut fixe quand le compte a rebours change de largeur (utilitaire commun d'ancrage)
        int[] box = fr.pioneers.client.look.HudLayouts.banner(graphics.guiWidth(), graphics.guiHeight(), minecraft.font.width(text) + 12, 11, y);
        int w = box[2] - box[0], x = box[0];
        graphics.fill(x, y, x + w, y + 11, 0x90000000);
        graphics.fill(x, y + 10, x + w, y + 11, 0xFF000000 | event.color);
        graphics.drawString(minecraft.font, text, x + 6, y + 2, event.color);
    }

    /** Aurores : voiles ondulants vers le nord, visibles la nuit (ou toujours sur les astres sans cycle jour/nuit lumineux). */
    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || type != SpaceEventType.AURORA || fade <= 0 || !PiClientConfig.EVENT_VISUALS.get()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        float night = Mth.clamp(level.getStarBrightness(event.getPartialTick()) * 2.5F, level.dimensionType().hasFixedTime() ? 0.6F : 0, 1);
        float alpha = fade * night * Math.min(1.2F, intensity);
        if (alpha <= 0.01F) {
            return;
        }
        float time = (float) RenderClock.time(event.getPartialTick());   //1.0.5-beta (lot 29) : horloge de rendu continue
        Matrix4f matrix = event.getPoseStack().last().pose();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float radius = 90;
        int segments = 48;
        for (int ribbon = 0; ribbon < 4; ribbon++) {
            float baseEl = 16 + ribbon * 6, height = 16 + ribbon * 3, phase = ribbon * 1.7F;
            for (int i = 0; i < segments; i++) {
                float u0 = i / (float) segments, u1 = (i + 1) / (float) segments;
                float[] p0 = point(u0, ribbon, time, phase), p1 = point(u1, ribbon, time, phase);
                float a0 = alpha * 0.55F * Mth.sin(u0 * Mth.PI) * (0.6F + 0.4F * Mth.sin(u0 * 9 + time * 0.03F + phase));
                float a1 = alpha * 0.55F * Mth.sin(u1 * Mth.PI) * (0.6F + 0.4F * Mth.sin(u1 * 9 + time * 0.03F + phase));
                vertex(builder, matrix, p0[0], baseEl + p0[1], radius, 0.25F, 1.0F, 0.6F, a0);
                vertex(builder, matrix, p1[0], baseEl + p1[1], radius, 0.25F, 1.0F, 0.6F, a1);
                vertex(builder, matrix, p1[0], baseEl + p1[1] + height, radius, 0.55F, 0.3F, 1.0F, 0);
                vertex(builder, matrix, p0[0], baseEl + p0[1] + height, radius, 0.55F, 0.3F, 1.0F, 0);
            }
        }
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    /** Point du voile : azimut (degres, 0 = nord) et ondulation en elevation. */
    private static float[] point(float u, int ribbon, float time, float phase) {
        float az = -80 + 160 * u + 10 * Mth.sin(u * 4 + time * 0.004F + phase);
        float wave = 4 * Mth.sin(u * 7 + time * 0.012F + phase) + 2 * Mth.sin(u * 17 - time * 0.02F);
        return new float[]{az, wave};
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, float azDeg, float elDeg, float radius, float r, float g, float b, float a) {
        double az = Math.toRadians(azDeg), el = Math.toRadians(elDeg);
        float x = (float) (Math.cos(el) * Math.sin(az) * radius), y = (float) (Math.sin(el) * radius), z = (float) (-Math.cos(el) * Math.cos(az) * radius);
        builder.vertex(matrix, x, y, z).color(r, g, b, Mth.clamp(a, 0, 1)).endVertex();
    }
}

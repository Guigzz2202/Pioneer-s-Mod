package fr.pioneers.space.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.Pioneers;
import fr.pioneers.core.RenderClock;
import fr.pioneers.space.RocketEntity;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.SolarSystem.Sector;
import java.util.Locale;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cinematique du transit : on quitte l'orbite, l'astre de depart s'eloigne, les etoiles defilent (un saut en distorsion pour les
 * voyages interstellaires) et la destination grossit jusqu'a remplir l'ecran avant la descente. Echap masque la cinematique
 * (le transit continue ; la touche de la carte la rouvre).
 */
public class TransitScreen extends Screen {

    private static final int STARS = 420;
    private final RocketEntity rocket;
    private final float[] starAngle = new float[STARS], starDepth = new float[STARS], starBright = new float[STARS];
    private final int[] starTint = new int[STARS];
    private final String[] tips;
    private final int tip;

    public TransitScreen(RocketEntity rocket) {
        super(Component.translatable("transit.pioneers.title"));
        this.rocket = rocket;
        Random random = new Random(rocket.getId() * 31L + rocket.getTransitTicks());
        for (int i = 0; i < STARS; i++) {
            starAngle[i] = random.nextFloat() * Mth.TWO_PI;
            starDepth[i] = random.nextFloat();
            starBright[i] = 0.35F + random.nextFloat() * 0.65F;
            float t = random.nextFloat();
            starTint[i] = t < 0.15F ? 0xFFB8C8FF : t > 0.9F ? 0xFFFFD8A8 : 0xFFF2F4FF;
        }
        tips = new String[]{"tip1", "tip2", "tip3", "tip4", "tip5", "tip6"};
        tip = random.nextInt(tips.length);
    }

    public static void open(RocketEntity rocket) {
        Minecraft.getInstance().setScreen(new TransitScreen(rocket));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.getVehicle() != rocket || rocket.getState() != RocketEntity.State.TRANSIT) {
            onClose();
        }
    }

    private boolean interstellar() {
        Body from = rocket.getTransitOrigin(), to = rocket.getDestination();
        return from != null && to != null && from.sector() != to.sector();
    }

    @Nullable
    private static ResourceLocation texture(@Nullable Body body) {
        if (body == null) {
            return null;
        }
        String name = body.name().toLowerCase(Locale.ROOT);
        ResourceLocation sky = Pioneers.rl("textures/environment/sky/" + name + ".png");
        if (Minecraft.getInstance().getResourceManager().getResource(sky).isPresent()) {
            return sky;
        }
        return Pioneers.rl("textures/gui/solar/" + name + ".png");
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int w = width, h = height;
        float progress = rocket.getTransitProgress(partialTick);
        float time = (float) RenderClock.time(partialTick);   //1.0.5-beta (lot 29) : horloge de rendu continue
        boolean warp = interstellar();
        //Phase de saut : au milieu du voyage interstellaire, les etoiles s'etirent en traits
        float warpAmount = warp ? Mth.clamp(Math.min((progress - 0.12F) / 0.12F, (0.85F - progress) / 0.1F), 0, 1) : 0;
        graphics.fill(0, 0, w, h, 0xFF02030A);
        //Lueur de fond (teinte de la destination)
        Body to = rocket.getDestination();
        int tint = to == null ? 0x406080 : to.color;
        float glow = 0.12F + 0.25F * progress;
        graphics.fillGradient(0, 0, w, h / 2, 0x00000000, alpha(tint, glow * 0.6F));
        graphics.fillGradient(0, h / 2, w, h, alpha(tint, glow * 0.6F), 0x00000000);
        //Champ d'etoiles en fuite depuis le centre
        float cx = w / 2F, cy = h / 2F, max = (float) Math.hypot(cx, cy);
        float speed = warp ? 0.0025F + warpAmount * 0.03F : 0.0035F;
        for (int i = 0; i < STARS; i++) {
            float depth = (starDepth[i] + time * speed * (0.4F + starBright[i])) % 1F;
            float r = depth * depth * max * 1.15F;
            float x = cx + Mth.cos(starAngle[i]) * r, y = cy + Mth.sin(starAngle[i]) * r;
            float a = Mth.clamp(depth * 2.5F, 0, 1) * starBright[i];
            int color = alpha(starTint[i] & 0xFFFFFF, a);
            if (warpAmount > 0.05F) {
                float len = warpAmount * (6 + depth * 60);
                line(graphics, x, y, x - Mth.cos(starAngle[i]) * len, y - Mth.sin(starAngle[i]) * len, color);
            } else {
                int s = depth > 0.7F ? 2 : 1;
                graphics.fill(Math.round(x), Math.round(y), Math.round(x) + s, Math.round(y) + s, color);
            }
        }
        //Astre de depart : s'eloigne en bas a gauche
        Body from = rocket.getTransitOrigin();
        ResourceLocation fromTex = texture(from);
        if (fromTex != null && progress < 0.35F) {
            float t = progress / 0.35F;
            float size = Mth.lerp(t * t, h * 0.9F, 8);
            float px = Mth.lerp(t, w * 0.15F, -size), py = Mth.lerp(t, h * 0.95F, h + size * 0.2F);
            blit(graphics, fromTex, px - size / 2, py - size / 2, size, 1 - t);
        }
        //Destination : grossit au centre
        ResourceLocation toTex = texture(to);
        if (toTex != null) {
            float t = Mth.clamp((progress - (warp ? 0.8F : 0.25F)) / (warp ? 0.2F : 0.75F), 0, 1);
            float size = Mth.lerp(t * t * t, 4, h * 1.25F);
            float a = Mth.clamp(t * 4, 0, 1);
            if (to != null && to.isStar()) {
                //Halo d'une etoile
                for (int k = 4; k > 0; k--) {
                    float hs = size * (1 + k * 0.35F);
                    disc(graphics, cx, cy, hs / 2, alpha(to.color, 0.05F * a));
                }
            }
            blit(graphics, toTex, cx - size / 2, cy - size / 2, size, a);
        }
        //Flash d'entree dans l'atmosphere / de sortie de distorsion
        if (warp && progress > 0.78F && progress < 0.86F) {
            float f = 1 - Math.abs(progress - 0.82F) / 0.04F;
            graphics.fill(0, 0, w, h, alpha(0xD8E8FF, Mth.clamp(f, 0, 1) * 0.8F));
        }
        if (progress > 0.94F) {
            float f = (progress - 0.94F) / 0.06F;
            graphics.fill(0, 0, w, h, alpha(to != null && to.isGasGiant() ? 0xF2E8D8 : 0x000000, f * 0.9F));
        }
        //Bandeau d'informations
        renderInfo(graphics, progress, warp, warpAmount);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderInfo(GuiGraphics graphics, float progress, boolean warp, float warpAmount) {
        int w = width, h = height;
        Body to = rocket.getDestination(), from = rocket.getTransitOrigin();
        graphics.fillGradient(0, 0, w, 34, 0xC0000000, 0x00000000);
        graphics.fillGradient(0, h - 46, w, h, 0x00000000, 0xC0000000);
        Component dest = to == null ? Component.literal("?") : Component.translatable(to.getTranslationKey());
        Component title = Component.translatable(rocket.isTransitToStation() ? "transit.pioneers.to_station" : "transit.pioneers.to_surface", dest)
              .withStyle(ChatFormatting.WHITE);
        graphics.drawCenteredString(font, title, w / 2, 9, 0xFFFFFF);
        Component phase;
        if (warp) {
            phase = Component.translatable(progress < 0.12F ? "transit.pioneers.phase.spinup" : warpAmount > 0.5F ? "transit.pioneers.phase.warp"
                                                                                                    : "transit.pioneers.phase.dropout");
        } else {
            phase = Component.translatable(progress < 0.2F ? "transit.pioneers.phase.burn" : progress < 0.8F ? "transit.pioneers.phase.coast"
                                                                                                  : "transit.pioneers.phase.approach");
        }
        graphics.drawCenteredString(font, phase.copy().withStyle(ChatFormatting.AQUA), w / 2, 21, 0xFFFFFF);
        //Barre de progression
        int barW = Math.min(260, w - 40), bx = (w - barW) / 2, by = h - 30;
        graphics.fill(bx - 1, by - 1, bx + barW + 1, by + 5, 0xFF404858);
        graphics.fill(bx, by, bx + barW, by + 4, 0xFF0C1018);
        int fill = Math.round(barW * progress);
        graphics.fillGradient(bx, by, bx + fill, by + 4, 0xFF60D0FF, 0xFF2878C8);
        //Distance restante et temps
        Component distance;
        if (warp && to != null && from != null) {
            float ly = Math.abs(to.sector().lightYears() - from.sector().lightYears());
            if (ly < 0.1F) {
                ly = Sector.ALPHA_CENTAURI.lightYears();
            }
            distance = Component.translatable("transit.pioneers.distance_ly", String.format("%.2f", ly * (1 - progress)));
        } else {
            float au = from == null || to == null ? 1 : Math.max(0.05F, Math.abs(from.getPrimary().orbit - to.getPrimary().orbit) * 3.1F);
            distance = Component.translatable("transit.pioneers.distance_au", String.format("%.2f", au * (1 - progress)));
        }
        int seconds = Math.max(0, Math.round(rocket.getTransitTicks() * (1 - progress) / 20F));
        graphics.drawString(font, distance, bx, by - 11, 0xA8B8D0);
        Component eta = Component.translatable("transit.pioneers.eta", seconds);
        graphics.drawString(font, eta, bx + barW - font.width(eta), by - 11, 0xA8B8D0);
        Component hint = Component.translatable("transit.pioneers." + tips[tip]).withStyle(ChatFormatting.DARK_GRAY);
        graphics.drawCenteredString(font, hint, w / 2, by + 10, 0x707888);
    }

    private static void blit(GuiGraphics graphics, ResourceLocation texture, float x, float y, float size, float alpha) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(size / 64F, size / 64F, 1);
        RenderSystem.enableBlend();
        graphics.setColor(1, 1, 1, Mth.clamp(alpha, 0, 1));
        graphics.blit(texture, 0, 0, 0, 0, 64, 64, 64, 64);
        graphics.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void disc(GuiGraphics graphics, float cx, float cy, float r, int color) {
        int ir = Math.max(1, Math.round(r));
        for (int dy = -ir; dy <= ir; dy += 2) {
            int half = Math.round((float) Math.sqrt(Math.max(0, r * r - dy * dy)));
            graphics.fill(Math.round(cx) - half, Math.round(cy) + dy, Math.round(cx) + half, Math.round(cy) + dy + 2, color);
        }
    }

    private static void line(GuiGraphics graphics, float x0, float y0, float x1, float y1, int color) {
        int steps = Math.max(1, Math.round(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) / 2));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = Math.round(x0 + (x1 - x0) * t), y = Math.round(y0 + (y1 - y0) * t);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static int alpha(int rgb, float a) {
        return Mth.clamp((int) (a * 255), 0, 255) << 24 | rgb & 0xFFFFFF;
    }
}

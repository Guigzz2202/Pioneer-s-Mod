package fr.pioneers.farm.client.fish;

import fr.pioneers.Pioneers;
import fr.pioneers.farm.fish.PiBobber;
import fr.pioneers.farm.fish.PiRodItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Jauge du combat (au centre de l'ecran depuis la 1.0.4-beta, lot 4) : tension du fil (zones verte, orange, rouge ; tremble et clignote au rouge, pulse quand
 * le fil est mou), distance du poisson, consigne. Pendant la touche : point d'exclamation qui bat. 1.0.2-beta : la zone verte est celle du
 * poisson ferre ({@link PiBobber#greenZone}, synchronisee), bornee par deux traits ; hors du vert le moulinet patine.
 * <p>
 * 1.6 : jauge de force pendant que l'on arme un lancer (degrade bleu, vert, or ; graduations ; eclat qui court a pleine force), avec la
 * portee prevue : le vol est simule comme en jeu ({@link PiBobber#flight}) jusqu'a l'eau ou au sol (bleu sur l'eau, ocre a terre).
 * Maquette : {@code tools/gc/farm2/cast_gauge_preview.py} (memes constantes).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FishingHud {

    private FishingHud() {
    }

    private static float shown, lastDistance;
    /** Jauge de force : largeur, hauteur (comme la tension). */
    static final int CAST_W = 150, CAST_H = 10;
    /**
     * 1.0.4-beta (lot 4, Guillaume : « Centrer la position de l'interface de peche ») : le cadre des deux jauges (titre, barre, consigne :
     * de barre - 14 a barre + 10 + 14 = {@link #PANEL_H} px) est CENTRE a l'ecran, en x et en y, quelles que soient la fenetre et l'echelle
     * (avant : barre a height - 64, au-dessus de la barre d'objets, sur les coeurs et l'armure). Maquette : scratchpad p104/lot4.
     */
    static final int PANEL_H = 14 + 10 + 14;

    /** Haut de la barre d'une jauge (cadre centre en y). */
    static int barY(int height) {
        return (height - PANEL_H) / 2 + 14;
    }

    /** Gauche d'une jauge de largeur {@code w} (centree en x). */
    static int barX(int width, int w) {
        //1.0.5-beta (lot 26, retour 16) : milieu fixe (utilitaire commun d'ancrage)
        return fr.pioneers.client.look.HudLayouts.fishingBar(width, w);
    }
    /** Portee prevue (recalculee a chaque tick d'armement). */
    private static int estTick = -1;
    private static float estDistance;
    private static boolean estWater;

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "fishing", FishingHud::render);
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        if (PiRodItem.charging(mc.player)) {
            charge(g, mc, partial, width, height);
            return;
        }
        PiBobber b = PiBobber.of(mc.player);
        if (b == null) {
            return;
        }
        Font font = mc.font;
        float time = mc.player.tickCount + partial;
        int cx = width / 2, y = barY(height);
        if (b.state() == PiBobber.BITE) {
            float s = 1.6F + 0.4F * Mth.sin(time * 1.2F);
            g.pose().pushPose();
            g.pose().translate(cx, height / 2F - 24, 0);
            g.pose().scale(s, s, 1);
            g.drawCenteredString(font, "!", 0, -4, 0xFFE040);
            g.pose().popPose();
            return;
        }
        if (b.state() != PiBobber.FIGHT) {
            shown = 0;
            return;
        }
        float t = b.tension();
        shown += (t - shown) * 0.35F;
        int w = 150, h = 10, x0 = barX(width, w);
        int shake = shown > 0.9F ? Math.round(Mth.sin(time * 3.1F) * 1.5F) : 0;
        g.pose().pushPose();
        g.pose().translate(shake, 0, 0);
        //Cadre (1.0.4-beta lot 4 : assez large pour la consigne, centre) et zones
        Component hold = Component.translatable("farm.pioneers.fishing.hold");
        frame(g, cx, x0, w, y, h, font.width(hold));
        g.fill(x0, y, x0 + w, y + h, 0xFF1C2026);
        //1.0.2-beta : zone verte propre au poisson ferre (PiBobber.greenZone : plus il est difficile, plus elle est etroite)
        float lo = b.greenLow(), hi = b.greenHigh();
        int green0 = x0 + Math.round(w * lo), green1 = x0 + Math.round(w * hi), red0 = x0 + Math.round(w * 0.9F);
        g.fill(x0, y + h - 2, green0, y + h, 0xFF4A80C0);
        g.fill(green0, y + h - 2, green1, y + h, 0xFF4CB050);
        g.fill(green1, y + h - 2, red0, y + h, 0xFFE0A030);
        g.fill(red0, y + h - 2, x0 + w, y + h, 0xFFE04040);
        //Remplissage
        int fill = Math.round(w * Mth.clamp(shown, 0, 1));
        int col = shown >= 0.9F ? ((int) (time * 2) % 2 == 0 ? 0xFFFF5050 : 0xFFB02020) : shown > hi ? 0xFFF0B040 : shown < lo ? 0xFF5A90D0
              : 0xFF62C860;
        g.fill(x0 + 1, y + 1, x0 + fill, y + h - 3, col);
        g.fill(x0 + 1, y + 1, x0 + fill, y + 2, 0x60FFFFFF);
        //Bornes de la zone verte (traits), aiguille
        g.fill(green0, y, green0 + 1, y + h, 0x9062C860);
        g.fill(green1 - 1, y, green1, y + h, 0x9062C860);
        g.fill(x0 + fill - 1, y - 2, x0 + fill + 1, y + h + 2, 0xFFFFFFFF);
        g.drawString(font, Component.translatable("farm.pioneers.fishing.tension"), x0, y - 11, 0xD0D0D0, true);
        float d = b.distance();
        lastDistance += (d - lastDistance) * 0.3F;
        Component dist = Component.translatable("farm.pioneers.fishing.distance", String.format("%.1f", lastDistance));
        g.drawString(font, dist, x0 + w - font.width(dist), y - 11, 0x80C8FF, true);
        if (b.jump() > 0) {
            g.drawCenteredString(font, "~", cx, y - 24, 0x80E0FF);
        }
        g.drawCenteredString(font, hold, cx, y + h + 3, 0x909090);
        g.pose().popPose();
    }

    /**
     * Fond du cadre : de la barre - 3 a la barre + 3 en x, elargi (centre) pour couvrir la consigne si elle est plus large (1.0.4-beta lot 4 :
     * la consigne debordait du cadre, illisible sur un fond clair).
     */
    static void frame(GuiGraphics g, int cx, int x0, int w, int y, int h, int hintWidth) {
        int left = Math.min(x0 - 3, cx - (hintWidth + 1) / 2 - 4), right = Math.max(x0 + w + 3, cx + (hintWidth + 1) / 2 + 4);
        g.fill(left, y - 14, right, y + h + 14, 0xC0101418);
    }

    // ------------------------------------------------------------------ 1.6 : jauge de force du lancer

    /** Couleur du degrade de la jauge a la position {@code f} (0..1) : bleu, vert, or. */
    static int castColour(float f) {
        int a, b;
        float t;
        if (f < 0.55F) {
            a = 0x3A78C8;
            b = 0x62C860;
            t = f / 0.55F;
        } else {
            a = 0x62C860;
            b = 0xF0C040;
            t = (f - 0.55F) / 0.45F;
        }
        int r = Math.round(Mth.lerp(t, a >> 16 & 255, b >> 16 & 255)), gg = Math.round(Mth.lerp(t, a >> 8 & 255, b >> 8 & 255)),
              bb = Math.round(Mth.lerp(t, a & 255, b & 255));
        return 0xFF000000 | r << 16 | gg << 8 | bb;
    }

    private static void charge(GuiGraphics g, Minecraft mc, float partial, int width, int height) {
        Player p = mc.player;
        if (!(p.getUseItem().getItem() instanceof PiRodItem rod)) {
            return;
        }
        Font font = mc.font;
        float ticks = PiRodItem.chargeTicks(p, partial);
        float power = PiBobber.power(ticks);
        boolean full = ticks >= PiBobber.CHARGE_TICKS;
        int tier = Mth.clamp(rod.tier, 0, 2);
        estimate(p, tier, PiBobber.power(Math.min(ticks, PiBobber.CHARGE_TICKS)));
        float time = p.tickCount + partial;
        int cx = width / 2, y = barY(height), w = CAST_W, h = CAST_H, x0 = barX(width, w);
        //Cadre (1.0.4-beta lot 4 : centre, assez large pour la plus longue des trois consignes : il ne change pas de taille)
        int hintWidth = Math.max(font.width(Component.translatable("farm.pioneers.fishing.cast_aim")),
              Math.max(font.width(Component.translatable("farm.pioneers.fishing.cast_max")),
                    font.width(Component.translatable("farm.pioneers.fishing.cast_release"))));
        frame(g, cx, x0, w, y, h, hintWidth);
        if (full) {
            int a = (int) (0x50 + 0x50 * (0.5F + 0.5F * Mth.sin(time * 0.6F)));
            int edge = a << 24 | 0xF0C040;
            g.fill(x0 - 1, y - 1, x0 + w + 1, y, edge);
            g.fill(x0 - 1, y + h, x0 + w + 1, y + h + 1, edge);
            g.fill(x0 - 1, y, x0, y + h, edge);
            g.fill(x0 + w, y, x0 + w + 1, y + h, edge);
        }
        g.fill(x0, y, x0 + w, y + h, 0xFF1C2026);
        //Remplissage en degrade
        int inner = w - 2, fill = Math.round(inner * Mth.clamp(power, 0, 1));
        for (int k = 0; k < fill; k++) {
            g.fill(x0 + 1 + k, y + 1, x0 + 2 + k, y + h - 1, castColour(k / (float) (inner - 1)));
        }
        g.fill(x0 + 1, y + 1, x0 + 1 + fill, y + 2, 0x60FFFFFF);
        //Eclat qui court a pleine force
        if (full) {
            int sx = x0 + (int) ((time * 7) % (w + 24)) - 12;
            g.fill(Math.max(x0 + 1, sx), y + 1, Math.min(x0 + w - 1, sx + 7), y + h - 1, 0x50FFFFFF);
        }
        //Graduations (quarts)
        for (int q = 1; q < 4; q++) {
            int gx = x0 + w * q / 4;
            g.fill(gx, y + h - 3, gx + 1, y + h - 1, 0x90FFFFFF);
        }
        //Aiguille
        g.fill(x0 + fill, y - 2, x0 + fill + 2, y + h + 2, 0xFFFFFFFF);
        g.drawString(font, Component.translatable("farm.pioneers.fishing.cast"), x0, y - 11, 0xD0D0D0, true);
        Component reach = Component.translatable("farm.pioneers.fishing.cast_reach", Math.round(estDistance), Math.round(PiBobber.LINE[tier]));
        g.drawString(font, reach, x0 + w - font.width(reach), y - 11, estWater ? 0x80C8FF : 0xC8A070, true);
        Component hint;
        int colour;
        if (!estWater) {
            hint = Component.translatable("farm.pioneers.fishing.cast_aim");
            colour = 0xC8A070;
        } else if (full) {
            hint = Component.translatable("farm.pioneers.fishing.cast_max");
            colour = (int) (time * 2) % 2 == 0 ? 0xF0C040 : 0xC89A30;
        } else {
            hint = Component.translatable("farm.pioneers.fishing.cast_release");
            colour = 0x909090;
        }
        g.drawCenteredString(font, hint, cx, y + h + 3, colour);
    }

    /** Portee prevue du lancer : vol simule depuis le bout de la canne jusqu'au premier bloc ou a l'eau (au plus 200 ticks). */
    private static void estimate(Player p, int tier, float power) {
        if (p.tickCount == estTick) {
            return;
        }
        estTick = p.tickCount;
        Level level = p.level();
        Vec3 pos = PiBobber.start(p), v = PiBobber.launch(p, tier, power), origin = pos, end = pos;
        boolean water = false;
        for (int i = 0; i < 200; i++) {
            Vec3 nv = PiBobber.flight(pos, v, origin, PiBobber.LINE[tier]);
            Vec3 next = pos.add(nv);
            BlockHitResult hit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
            if (hit.getType() != HitResult.Type.MISS) {
                end = hit.getLocation();
                water = level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER);
                break;
            }
            pos = next;
            v = nv;
            end = pos;
        }
        double dx = end.x - p.getX(), dz = end.z - p.getZ();
        estDistance = (float) Math.sqrt(dx * dx + dz * dz);
        estWater = water;
    }
}

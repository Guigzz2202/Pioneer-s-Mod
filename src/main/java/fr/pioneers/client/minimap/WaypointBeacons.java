package fr.pioneers.client.minimap;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Waypoints dans le monde : trois rendus (1.0.2, reglage global {@link MinimapConfig#W_BEACON_STYLE}, remplace par waypoint
 * {@link Waypoint#style}) :
 * <ul>
 *     <li>BALISE : faisceau facon balise vanilla ({@link BeaconRenderer#renderBeaconBeam}, texture qui defile, coeur opaque + halo) du
 *     waypoint jusqu'en haut du monde, et etiquette nom + distance ;</li>
 *     <li>POINT : losange a la couleur du waypoint, toujours face a la camera, de taille constante a l'ecran ; nom en petit, distance quand
 *     on le regarde (moins de 4 degres) ou tout pres ;</li>
 *     <li>INVISIBLE : rien dans le monde (le waypoint reste sur la minimap et la carte).</li>
 * </ul>
 * Rien n'est dessine quand l'interface est masquee (F1).
 * <p>
 * 1.0.4-beta (lot 5, « les voir en toutes circonstances ») : les ETIQUETTES et les LOSANGES ne sont plus dessines dans le monde (ils
 * disparaissaient derriere le relief, sous terre, sous l'eau) : {@link WaypointHud} les projette a l'ecran, au bord quand la cible est hors
 * champ, attenues derriere le terrain. Ici ne restent que le faisceau du mode Balise (dans le monde, cache par le relief comme une vraie
 * balise) et, a l'etape AFTER_PARTICLES, la capture des matrices de l'image ({@link WaypointHud#capture}).
 * <p>
 * 1.0.1 : JAMAIS a l'etape AFTER_WEATHER avec la pile de l'evenement (Forge 1.20.1 y a deja pose la vue de la camera dans
 * {@code RenderSystem.getModelViewStack} : rotation appliquee deux fois). Banc hors jeu : {@code tools/minimap/BeaconBench.java}.
 * <p>
 * 1.0.2 (« l'aspect graphique est assez buggue ») : defauts corriges.
 * <ul>
 *     <li>Faisceau en {@code RenderType.lightning()} : melange ADDITIF (couleurs sombres invisibles, tout vire au blanc de jour), profondeur
 *     ECRITE par le haut transparent du faisceau (rectangles sans nuages ni pluie derriere), sortie vers la cible meteo en mode
 *     « Fabuleux », fondu par le brouillard. Maintenant : {@code RenderType.beaconBeam} (couleur multipliee, pas de lumiere : pas assombri
 *     la nuit) a l'etape AFTER_BLOCK_ENTITIES comme la balise vanilla, AVANT l'eau et le verre : l'eau devant teinte le faisceau au lieu de
 *     le cacher, l'eau derriere ne passe plus devant (coeur opaque qui ecrit la profondeur).</li>
 *     <li>Balise lointaine rapprochee a 96 blocs : dessinee DEVANT les collines entre 96 blocs et sa vraie distance. Maintenant rapprochee
 *     au-dela du terrain dessine ({@link MapMath#pullDistance}) : profondeur juste partout.</li>
 *     <li>Faisceau de 0,18 bloc reduit avec la distance : moins d'un pixel au loin, il scintillait. Maintenant au moins ~4 pixels
 *     ({@link MapMath#beamRadius}).</li>
 *     <li>Brouillard : faisceau et texte noyes dans le brouillard proche (petite distance d'affichage). Maintenant sans brouillard pendant
 *     NOTRE dessin (debut du brouillard sauve puis remis).</li>
 *     <li>Etiquette en mode SEE_THROUGH a pleine couleur : aussi vive a travers les murs, sans profondeur avec le reste. Maintenant deux
 *     passes comme les noms vanilla : attenuee a travers les blocs, puis pleine la ou elle est visible ; triees de la plus loin a la plus
 *     proche ; couleur eclaircie si elle est trop sombre ({@link MapMath#readable}).</li>
 *     <li>Distance arrondie a chaque image depuis la camera (+1,6 bloc) : le chiffre sautait et differait de la minimap. Maintenant depuis
 *     le joueur (comme la minimap) avec hysteresis ({@link MapMath#shownDistance}), « 1,2 km » au-dela de 1 000 m.</li>
 *     <li>{@code bufferSource().endBatch()} vidait TOUT le tampon commun du jeu au milieu du rendu. Maintenant un tampon a nous, vide a la
 *     fin de chaque etape : etats GL remis par les types de rendu eux-memes.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class WaypointBeacons {

    /** Losange du mode Point ({@code tools/gc/minimap/minimap_assets.py}). */
    static final ResourceLocation MARKER = Pioneers.rl("textures/gui/minimap/marker.png");
    /** Haut du faisceau (comme la balise vanilla). */
    static final int BEAM_TOP = BeaconRenderer.MAX_RENDER_Y;

    private static MultiBufferSource.BufferSource buffers;

    private WaypointBeacons() {
    }

    private static MultiBufferSource.BufferSource buffers() {
        if (buffers == null) {
            buffers = MultiBufferSource.immediate(new BufferBuilder(4096));
        }
        return buffers;
    }

    /** Un waypoint a dessiner a cette image. */
    private record Item(Waypoint w, MinimapConfig.BeaconStyle style, double dx, double dy, double dz, double camDist, double shown) {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        //Surtout pas AFTER_WEATHER : la vue de la camera y est deja dans la matrice modele-vue (rotation appliquee deux fois)
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            //1.0.4-beta (lot 5) : les etiquettes et losanges sont dessines A L'ECRAN par WaypointHud (toujours visibles) : ici, les matrices
            WaypointHud.capture(event);
            return;
        }
        boolean beams = stage == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES;
        if (!beams) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || !MinimapConfig.on(MinimapConfig.L_WAYPOINTS, true)) {
            return;
        }
        List<Item> items = collect(mc, event, beams);
        if (items.isEmpty()) {
            return;
        }
        //sans brouillard pendant notre dessin (le faisceau et le texte ne doivent pas s'y noyer), remis ensuite
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        FogShape fogShape = RenderSystem.getShaderFogShape();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        try {
            drawBeams(mc, event, items);
        } finally {
            buffers().endBatch();
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(fogShape);
        }
    }

    /** Waypoints de la dimension a dessiner a cette etape, du plus loin au plus proche (le plus proche dessine en dernier, par-dessus). */
    private static List<Item> collect(Minecraft mc, RenderLevelStageEvent event, boolean beams) {
        List<Waypoint> list = Waypoints.cached(mc.level.dimension().location().toString());
        if (list.isEmpty()) {
            return List.of();
        }
        MinimapConfig.BeaconStyle global = MinimapConfig.beaconStyle();
        int range = MinimapConfig.num(MinimapConfig.W_BEACON_RANGE, 0);
        Vec3 c = event.getCamera().getPosition();
        Vec3 p = mc.player.getPosition(event.getPartialTick());
        List<Item> out = new ArrayList<>();
        for (Waypoint w : list) {
            if (!w.visible) {
                continue;
            }
            MinimapConfig.BeaconStyle style = w.resolvedStyle(global);
            if (style == MinimapConfig.BeaconStyle.HIDDEN || beams && style != MinimapConfig.BeaconStyle.BEACON) {
                continue;
            }
            double dist = w.distanceTo(p.x, p.y, p.z);
            if (range > 0 && dist > range) {
                continue;
            }
            double dx = w.x + 0.5 - c.x, dy = w.y + (style == MinimapConfig.BeaconStyle.POINT ? 0.5 : 0) - c.y, dz = w.z + 0.5 - c.z;
            double camDist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            out.add(new Item(w, style, dx, dy, dz, camDist, w.shownDist));
        }
        out.sort((a, b) -> Double.compare(b.camDist, a.camDist));
        return out;
    }

    // ------------------------------------------------------------------ faisceaux (AFTER_BLOCK_ENTITIES, avant l'eau et le verre)
    private static void drawBeams(Minecraft mc, RenderLevelStageEvent event, List<Item> items) {
        double pull = MapMath.pullDistance(mc.options.getEffectiveRenderDistance());
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = buffers();
        long time = mc.level.getGameTime();
        float partial = event.getPartialTick();
        for (Item it : items) {
            double hx = it.dx, hz = it.dz;
            if (hx * hx + hz * hz < 1) {
                //dans le faisceau : rien (vu de l'interieur, ses faces sont ecartees)
                continue;
            }
            Waypoint w = it.w;
            int height = BEAM_TOP - w.y;
            if (height <= 0) {
                continue;
            }
            float k = (float) MapMath.beaconPull(it.camDist, pull);
            float radius = (float) MapMath.beamRadius(it.camDist);
            ps.pushPose();
            //coin du bloc (la balise vanilla ajoute 0,5 elle-meme), puis reduction autour de la camera
            ps.translate((it.dx - 0.5) * k, it.dy * k, (it.dz - 0.5) * k);
            ps.scale(k, k, k);
            float[] rgb = {((w.color >> 16) & 255) / 255F, ((w.color >> 8) & 255) / 255F, (w.color & 255) / 255F};
            BeaconRenderer.renderBeaconBeam(ps, buf, BeaconRenderer.BEAM_LOCATION, partial, 1F, time, 0, height, rgb, radius, radius * 1.25F);
            ps.popPose();
        }
    }

    /** « 42 m », « 1,2 km » (virgule en francais). */
    static String distanceText(double shown) {
        if (shown < 1000) {
            return I18n.get("minimap.pioneers.beacon.meters", (int) Math.round(shown));
        }
        String km = String.format(Locale.ROOT, "%.1f", shown / 1000.0);
        if (I18n.get("minimap.pioneers.beacon.decimal").equals(",")) {
            km = km.replace('.', ',');
        }
        return I18n.get("minimap.pioneers.beacon.km", km);
    }
}

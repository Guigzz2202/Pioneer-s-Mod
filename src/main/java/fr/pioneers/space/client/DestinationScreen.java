package fr.pioneers.space.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.client.PiKeys;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.space.research.client.ClientResearch;
import fr.pioneers.core.common.PiLang;
import fr.pioneers.core.network.PacketAbortMission;
import fr.pioneers.core.network.PacketCargoMap;
import fr.pioneers.core.network.PacketSelectDestination;
import fr.pioneers.core.network.PiNetwork;
import fr.pioneers.space.RocketEntity;
import fr.pioneers.space.RocketTier;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.SolarSystem.Sector;
import fr.pioneers.space.SolarSystem;
import fr.pioneers.space.cargo.CargoType;
import fr.pioneers.space.environment.Environments;
import fr.pioneers.space.research.BodyResearch;
import fr.pioneers.space.research.ResearchEffects;
import fr.pioneers.space.research.ResearchNode;
import fr.pioneers.space.station.OrbitalStations;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Carte de navigation : les systemes stellaires animes, et la carte galactique (G) qui les relie.
 * <ul>
 *     <li>Galaxie : le voisinage du Soleil (Alpha du Centaure, TRAPPIST-1) ; un clic ouvre la carte d'un systeme.</li>
 *     <li>Navigateur (a gauche) : on choisit un systeme, puis un astre de ce systeme ; la carte suit.</li>
 *     <li>Fiche de mission (en bas) : etat de l'astre, sa maitrise (orbite, surface, geologie, biologie) et le decollage.</li>
 *     <li>Logistique (L) : fenetre a part entiere avec les stations et les vols cargo.</li>
 *     <li>Vue generale : le Soleil, les planetes, la ceinture d'asteroides et Pluton, bien espaces.</li>
 *     <li>Vue detaillee : un clic sur un hote (planete a satellites, ceinture) zoome dessus et montre ses satellites.</li>
 *     <li>Molette : zoom vers le curseur. Glisser : deplacer la carte. Clic droit / Echap : retour a la vue generale.</li>
 * </ul>
 * Sans fusee en orbite (touche de la carte), la carte est en simple consultation.
 */
public class DestinationScreen extends Screen {

    private static final ResourceLocation NEBULA = Pioneers.rl("textures/gui/solar/nebula.png");
    private static final ResourceLocation GLOW = Pioneers.rl("textures/effect/glow.png");
    private static final ResourceLocation RING = Pioneers.rl("textures/gui/solar/ring.png");
    private static final ResourceLocation SHADOW = Pioneers.rl("textures/gui/solar/shadow.png");
    private static final float SQUASH = 0.6F;
    /** Rayon de la carte generale du systeme solaire (unites d'orbite) qui doit tenir a l'ecran au zoom 1. */
    private static final float SYSTEM_RADIUS = 11.6F;
    private static final float BELT_INNER = 4.85F, BELT_OUTER = 5.55F;
    private static final float MIN_ZOOM = 0.6F, MAX_ZOOM = 8F;
    private static final float TRANSITION = 0.45F;
    private static final int STARS = 420, BELT = 600;

    private final ResourceKey<Level> here;
    @Nullable
    private final RocketTier tier;
    private final long openedAt = System.nanoTime();
    private final float[] starX = new float[STARS], starY = new float[STARS], starPhase = new float[STARS], starSize = new float[STARS];
    private final int[] starColor = new int[STARS];
    private final float[] beltRadius = new float[BELT], beltAngle = new float[BELT], beltSpeed = new float[BELT];
    private final Map<Body, float[]> positions = new EnumMap<>(Body.class);
    private final Map<Body, Float> radii = new EnumMap<>(Body.class);
    private final List<Body> visible = new ArrayList<>();
    /** Systeme stellaire affiche. */
    private Sector sector;
    /** Carte galactique affichee (choix du systeme). */
    private boolean galaxy;
    /** Bornes de la fiche de mission (pour ne pas cliquer a travers). */
    private int[] missionBounds;
    /** Defilement du navigateur quand il ne tient pas au-dessus de la fiche de mission. */
    private int navScroll;

    /** Hote de la vue detaillee (null = vue generale). */
    @Nullable
    private Body focus;
    @Nullable
    private Body hovered;
    private float seconds, lastSeconds;
    /** Centre de la vue (avant deplacement), rayon disponible, echelle des astres. */
    private float baseX, baseY, avail, bodyScale;
    private float zoom = 1, targetZoom = 1, panX, panY;
    private boolean anchored;
    private float anchorMouseX, anchorMouseY, anchorWorldX, anchorWorldY;
    private float transitionStart = -10, originX, originY, transitionFrom = 0.15F;
    private boolean pressed, dragged;
    private double pressX, pressY;
    /** Astre choisi (fiche de mission en bas de l'ecran). */
    @Nullable
    private Body selected;
    private long lastCargoRequest;
    /** Position du joueur (dans la dimension orbitale, elle indique la station ou l'on se trouve). */
    private final double hereX;
    /** Zones cliquables de l'interface (recalculees a chaque image). */
    private final List<Object[]> hitboxes = new ArrayList<>();

    public DestinationScreen(ResourceKey<Level> here, double hereX, @Nullable RocketTier tier) {
        super(PiLang.MAP_TITLE.translate());
        this.here = here;
        this.hereX = hereX;
        this.tier = tier;
        Body origin = SolarSystem.origin(here, hereX);
        this.sector = origin == null ? Sector.SOL : origin.sector();
        RandomSource random = RandomSource.create(42);
        for (int i = 0; i < STARS; i++) {
            starX[i] = random.nextFloat();
            starY[i] = random.nextFloat();
            starPhase[i] = random.nextFloat() * Mth.TWO_PI;
            starSize[i] = random.nextFloat() < 0.08F ? 1.5F : 0.75F;
            float tint = random.nextFloat();
            starColor[i] = tint < 0.15F ? 0xFFD8A0 : tint < 0.3F ? 0xA8C8FF : 0xFFFFFF;
        }
        for (int i = 0; i < BELT; i++) {
            beltRadius[i] = (random.nextFloat() + random.nextFloat()) / 2;
            beltAngle[i] = random.nextFloat() * Mth.TWO_PI;
            beltSpeed[i] = 0.035F + random.nextFloat() * 0.02F;
        }
    }

    public static void open(RocketEntity rocket) {
        Minecraft.getInstance().setScreen(new DestinationScreen(rocket.level().dimension(), rocket.getX(), rocket.getTier()));
    }

    private static ResourceLocation texture(Body body) {
        return Pioneers.rl("textures/gui/solar/" + body.name().toLowerCase() + ".png");
    }

    @Override
    protected void init() {
        requestCargo();
    }

    private void requestCargo() {
        lastCargoRequest = System.currentTimeMillis();
        PiNetwork.sendToServer(new PacketCargoMap.Request());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean reachable(Body body) {
        return known(body) && body.isReachableFrom(here, hereX, tier);
    }

    /** Astre decouvert par la recherche (ou celui ou l'on se trouve deja). */
    private boolean known(Body body) {
        return ClientResearch.isDiscovered(body) || containsHere(body);
    }

    /** L'astre, ou un de ses satellites, est celui ou se trouve le joueur. */
    private boolean containsHere(Body body) {
        if (body.isHere(here)) {
            return true;
        }
        for (Body child : body.getChildren()) {
            if (child.isHere(here)) {
                return true;
            }
        }
        return false;
    }

    private void setFocus(@Nullable Body body, float fromX, float fromY) {
        focus = body;
        zoom = targetZoom = 1;
        panX = panY = 0;
        anchored = false;
        hovered = null;
        pressed = false;
        transitionStart = seconds;
        originX = fromX;
        originY = fromY;
        //Vers une vue detaillee : on part petit depuis l'hote ; retour : on part grand
        transitionFrom = body != null ? 0.15F : 2.4F;
        rebuildWidgets();
    }

    private void back() {
        setFocus(null, baseX, baseY);
        if (navSystem != null && navSystem.focus != null) {
            navSystem = null;
        }
    }

    // ------------------------------------------------------------------
    // Rendu
    // ------------------------------------------------------------------

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        seconds = (System.nanoTime() - openedAt) / 1.0E9F;
        float delta = Mth.clamp(seconds - lastSeconds, 0, 0.1F);
        lastSeconds = seconds;
        baseX = width * 0.5F;
        baseY = height * 0.53F;
        avail = Math.min(width * 0.44F, height * 0.42F / SQUASH);
        bodyScale = Mth.clamp(avail / 170F, 0.7F, 2F);
        //Zoom adouci, ancre sur le point vise par la molette
        zoom += (targetZoom - zoom) * Math.min(1, delta * 14);
        if (Math.abs(targetZoom - zoom) < 0.0005F) {
            zoom = targetZoom;
        }
        if (anchored) {
            panX = anchorMouseX - baseX - anchorWorldX * zoom;
            panY = anchorMouseY - baseY - anchorWorldY * zoom;
            if (zoom == targetZoom) {
                anchored = false;
            }
        }
        clampPan();
        computePositions();

        //Fond : nebuleuse qui derive lentement + etoiles (non zoomes, leger parallaxe)
        graphics.fill(0, 0, width, height, 0xFF010208);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 0.85F);
        graphics.blit(NEBULA, 0, 0, width, height, seconds * 2 - panX * 0.05F, -panY * 0.05F, 256, 128, 256, 128);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        graphics.flush();
        drawStars(graphics.pose().last().pose());

        if (galaxy) {
            hitboxes.clear();
            drawGalaxy(graphics, mouseX, mouseY);
            PoseStack ui = graphics.pose();
            ui.pushPose();
            ui.translate(0, 0, 300);
            drawTopBar(graphics, mouseX, mouseY);
            drawAbort(graphics, mouseX, mouseY);
            ui.popPose();
            RenderSystem.disableBlend();
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        //Transition d'entree / de sortie d'une vue detaillee
        float t = Mth.clamp((seconds - transitionStart) / TRANSITION, 0, 1);
        float ease = 1 - (1 - t) * (1 - t) * (1 - t);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        if (t < 1) {
            float s = Mth.lerp(ease, transitionFrom, 1);
            pose.translate(Mth.lerp(ease, originX, baseX), Mth.lerp(ease, originY, baseY), 0);
            pose.scale(s, s, 1);
            pose.translate(-baseX, -baseY, 0);
        }
        hovered = null;
        if (t >= 1 && !overPanel(mouseX, mouseY)) {
            pickHovered(mouseX, mouseY);
        }
        Matrix4f matrix = pose.last().pose();
        drawOrbits(matrix);
        if (focus == null && sector == Sector.SOL) {
            drawBelt(matrix, BELT_INNER * unit(), (BELT_OUTER - BELT_INNER) * unit(), hovered == Body.ASTEROID_BELT);
        } else if (focus == Body.ASTEROID_BELT) {
            drawBelt(matrix, avail * 0.5F * zoom, avail * 0.5F * zoom, false);
        }
        List<Body> order = new ArrayList<>(visible);
        order.sort(Comparator.comparingDouble(b -> positions.get(b)[1]));
        for (Body body : order) {
            drawBody(graphics, body);
        }
        drawLabels(graphics);
        for (Body body : visible) {
            if (focus == null ? containsHere(body) : body.isHere(here)) {
                drawSelection(graphics, body, 0x55CCFF, 0.9F);
            }
        }
        if (hovered != null && hovered != Body.ASTEROID_BELT) {
            drawSelection(graphics, hovered, canTravel(hovered) ? 0x55FF88 : 0xBBBBBB, 0.7F);
        }
        if (selected != null && positions.containsKey(selected)) {
            drawSelection(graphics, selected, stateColor(selected) & 0xFFFFFF, 1);
        }
        pose.popPose();
        if (t < 1) {
            //Fondu
            int alpha = (int) ((1 - ease) * 0.8F * 255);
            graphics.fill(0, 0, width, height, alpha << 24 | 0x010208);
        }
        if (showRoutes || logisticsOpen) {
            drawCargoRoutes(graphics);
        }
        hitboxes.clear();
        PoseStack ui = graphics.pose();
        ui.pushPose();
        ui.translate(0, 0, 300);
        if (logisticsOpen) {
            //Fenetre modale : seule sa zone reagit
            drawTopBar(graphics, mouseX, mouseY);
            drawNavigator(graphics, -1, -1);
            if (selected != null) {
                drawMissionBar(graphics, -1, -1);
            }
            hitboxes.clear();
            drawLogisticsPopup(graphics, mouseX, mouseY);
        } else {
            drawTopBar(graphics, mouseX, mouseY);
            drawNavigator(graphics, mouseX, mouseY);
            if (selected != null) {
                drawMissionBar(graphics, mouseX, mouseY);
            }
            if (hovered != null && hovered != selected) {
                drawTooltip(graphics, hovered, mouseX, mouseY);
            }
            drawAbort(graphics, mouseX, mouseY);
        }
        ui.popPose();
        if (System.currentTimeMillis() - lastCargoRequest > 2000) {
            requestCargo();
        }

        RenderSystem.disableBlend();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private float centerX() {
        return baseX + panX;
    }

    private float centerY() {
        return baseY + panY;
    }

    /** Pixels par unite d'orbite sur la carte generale. */
    private float unit() {
        return avail / systemRadius() * zoom;
    }

    /** Rayon de la carte generale du systeme affiche (unites d'orbite). */
    private float systemRadius() {
        if (sector == Sector.SOL) {
            return SYSTEM_RADIUS;
        }
        float max = 1;
        for (Body body : Body.ALL) {
            if (body.sector() == sector && body.isPrimary() && !body.isRoot()) {
                max = Math.max(max, body.orbit);
            }
        }
        return max + 0.8F;
    }

    /** Etoile centrale du systeme affiche. */
    private Body root() {
        return sector.root();
    }

    private void clampPan() {
        float limit = avail * zoom;
        panX = Mth.clamp(panX, -limit, limit);
        panY = Mth.clamp(panY, -limit * SQUASH, limit * SQUASH);
    }

    /** Rayon de l'hote au centre d'une vue detaillee (au zoom 1). */
    private float hostRadius(Body host) {
        return switch (host) {
            case JUPITER, SATURN -> avail * 0.17F;
            case ASTEROID_BELT -> 9 * bodyScale;
            case PLUTO -> avail * 0.08F;
            default -> avail * 0.12F;
        };
    }

    /** Rayon de la premiere orbite d'une vue detaillee (au zoom 1). */
    private float innerOrbit() {
        return focus == Body.ASTEROID_BELT ? avail * 0.25F : hostRadius(focus) * 1.6F;
    }

    /** Les astres grossissent moins vite que les distances : zoomer les ecarte. */
    private float bodyZoom() {
        return (float) Math.pow(zoom, 0.45);
    }

    private void computePositions() {
        positions.clear();
        radii.clear();
        visible.clear();
        float cx = centerX(), cy = centerY();
        float pulse = 1 + 0.025F * Mth.sin(seconds * 2.2F);
        if (focus == null) {
            for (Body body : Body.ALL) {
                if (!body.isPrimary() || body.sector() != sector) {
                    continue;
                }
                if (body.isRoot()) {
                    positions.put(body, new float[]{cx, cy});
                    radii.put(body, body.size * bodyScale * bodyZoom() * pulse);
                } else {
                    float angle = body.phase + (body.period > 0 ? seconds * Mth.TWO_PI / body.period : 0);
                    float radius = orbitRadius(body);
                    positions.put(body, new float[]{cx + Mth.cos(angle) * radius, cy + Mth.sin(angle) * radius * SQUASH});
                    radii.put(body, body.size * bodyScale * bodyZoom());
                }
                if (body != Body.ASTEROID_BELT) {
                    visible.add(body);
                }
            }
        } else {
            Body host = focus == Body.ASTEROID_BELT ? Body.SUN : focus;
            positions.put(host, new float[]{cx, cy});
            radii.put(host, hostRadius(focus) * bodyZoom() * (host.isStar() ? pulse : 1));
            visible.add(host);
            for (Body child : focus.getChildren()) {
                float angle = child.phase + seconds * Mth.TWO_PI / child.period;
                float radius = orbitRadius(child);
                positions.put(child, new float[]{cx + Mth.cos(angle) * radius, cy + Mth.sin(angle) * radius * SQUASH});
                radii.put(child, child.size * bodyScale * bodyZoom());
                visible.add(child);
            }
        }
    }

    private float orbitRadius(Body body) {
        if (focus == null) {
            return body.orbit * unit();
        }
        float inner = innerOrbit();
        return (inner + (avail - inner) * body.orbit) * zoom;
    }

    private void pickHovered(int mouseX, int mouseY) {
        float best = Float.MAX_VALUE;
        for (Body body : visible) {
            float[] pos = positions.get(body);
            float r = radii.get(body);
            float dx = mouseX - pos[0], dy = mouseY - pos[1];
            float dist = dx * dx + dy * dy;
            float pick = Math.max(r + 3, 7);
            if (dist <= pick * pick && dist < best) {
                best = dist;
                hovered = body;
            }
        }
        if (hovered == null && focus == null && sector == Sector.SOL) {
            //La ceinture d'asteroides : un anneau elliptique
            float dx = (mouseX - centerX()) / unit(), dy = (mouseY - centerY()) / (unit() * SQUASH);
            float d = Mth.sqrt(dx * dx + dy * dy);
            if (d >= BELT_INNER - 0.1F && d <= BELT_OUTER + 0.1F) {
                hovered = Body.ASTEROID_BELT;
            }
        }
    }

    // ------------------------------------------------------------------
    // Decor en lots (une seule commande de dessin par couche)
    // ------------------------------------------------------------------

    private void drawStars(Matrix4f matrix) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < STARS; i++) {
            float x = Mth.positiveModulo(starX[i] * width + panX * 0.08F, (float) width);
            float y = Mth.positiveModulo(starY[i] * height + panY * 0.08F, (float) height);
            float s = starSize[i];
            float alpha = 0.35F + 0.65F * (0.5F + 0.5F * Mth.sin(seconds * (1.2F + starPhase[i] * 0.3F) + starPhase[i]));
            quad(builder, matrix, x - s / 2, y - s / 2, s, s, starColor[i], alpha);
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private void drawOrbits(Matrix4f matrix) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        float cx = centerX(), cy = centerY();
        for (Body body : visible) {
            if (body.isRoot() || body == focus) {
                continue;
            }
            boolean highlight = reachable(body) || body.isHere(here) || (focus == null && containsHere(body));
            int color = highlight ? 0x55CCFF : 0xFFFFFF;
            float alpha = highlight ? 0.5F : 0.15F;
            float radius = orbitRadius(body);
            int steps = Mth.clamp((int) (radius * 0.8F), 32, 360);
            float prevX = cx + radius, prevY = cy;
            for (int i = 1; i <= steps; i++) {
                float angle = i * Mth.TWO_PI / steps;
                float x = cx + Mth.cos(angle) * radius, y = cy + Mth.sin(angle) * radius * SQUASH;
                builder.vertex(matrix, prevX, prevY, 0).color(r(color), g(color), b(color), alpha).endVertex();
                builder.vertex(matrix, x, y, 0).color(r(color), g(color), b(color), alpha).endVertex();
                prevX = x;
                prevY = y;
            }
        }
        BufferUploader.drawWithShader(builder.end());
    }

    /** Ceinture d'asteroides : anneau de rayon interieur {@code inner} et d'epaisseur {@code thickness} (pixels). */
    private void drawBelt(Matrix4f matrix, float inner, float thickness, boolean highlight) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float cx = centerX(), cy = centerY();
        float size = Mth.clamp(thickness / 25F, 0.8F, 2.4F);
        for (int i = 0; i < BELT; i++) {
            float angle = beltAngle[i] + seconds * beltSpeed[i];
            float radius = inner + beltRadius[i] * thickness;
            float x = cx + Mth.cos(angle) * radius, y = cy + Mth.sin(angle) * radius * SQUASH;
            float s = (i % 7 == 0 ? 1.6F : 1F) * size;
            int color = highlight ? (i % 3 == 0 ? 0xE8D8B0 : 0xC0B090) : (i % 3 == 0 ? 0xB8A890 : 0x8C8070);
            quad(builder, matrix, x - s / 2, y - s / 2, s, s, color, highlight ? 0.95F : 0.75F);
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static void quad(BufferBuilder builder, Matrix4f matrix, float x, float y, float w, float h, int color, float alpha) {
        builder.vertex(matrix, x, y + h, 0).color(r(color), g(color), b(color), alpha).endVertex();
        builder.vertex(matrix, x + w, y + h, 0).color(r(color), g(color), b(color), alpha).endVertex();
        builder.vertex(matrix, x + w, y, 0).color(r(color), g(color), b(color), alpha).endVertex();
        builder.vertex(matrix, x, y, 0).color(r(color), g(color), b(color), alpha).endVertex();
    }

    private static float r(int color) {
        return (color >> 16 & 255) / 255F;
    }

    private static float g(int color) {
        return (color >> 8 & 255) / 255F;
    }

    private static float b(int color) {
        return (color & 255) / 255F;
    }

    // ------------------------------------------------------------------
    // Astres
    // ------------------------------------------------------------------

    /** Direction (angle) du Soleil vue depuis cet astre. */
    private float lightAngle(float[] pos) {
        if (focus == null || focus == Body.ASTEROID_BELT || focus.isStar()) {
            return (float) Math.atan2(centerY() - pos[1], centerX() - pos[0]);
        }
        //Vue d'une planete : le Soleil est a l'oppose de la position de la planete sur sa propre orbite
        float angle = focus.phase + seconds * Mth.TWO_PI / focus.period;
        return (float) Math.atan2(-Mth.sin(angle) * SQUASH, -Mth.cos(angle));
    }

    private void drawBody(GuiGraphics graphics, Body body) {
        float[] pos = positions.get(body);
        float radius = Math.max(1, radii.get(body));
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(pos[0], pos[1], 0);
        if (body.isStar() && known(body)) {
            //Couronne : deux halos additifs qui tournent en sens inverse, a la couleur de l'etoile
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            float sr = r(body.color), sg = g(body.color), sb = b(body.color);
            for (int layer = 0; layer < 2; layer++) {
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotation(seconds * (layer == 0 ? 0.15F : -0.1F)));
                RenderSystem.setShaderColor(sr, sg * (layer == 0 ? 0.6F : 0.85F), sb * (layer == 0 ? 0.3F : 0.6F), layer == 0 ? 0.45F : 0.7F);
                blitCentered(graphics, GLOW, radius * (layer == 0 ? 4.2F : 2.6F));
                pose.popPose();
            }
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
            blitCentered(graphics, texture(body), radius);
            graphics.flush();
            pose.popPose();
            return;
        }
        if (!known(body)) {
            //Astre inconnu : une silhouette sombre au liseré violet, qui attend les sondes
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShaderColor(0.55F, 0.4F, 1F, 0.18F + 0.08F * Mth.sin(seconds * 1.7F + body.ordinal()));
            blitCentered(graphics, GLOW, radius * 1.8F);
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(0.07F, 0.07F, 0.1F, 1);
            blitCentered(graphics, texture(body), radius);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            graphics.flush();
            pose.popPose();
            return;
        }
        //Halo atmospherique discret pour les astres a atmosphere
        int atmosphere = atmosphere(body);
        if (atmosphere != 0) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShaderColor(r(atmosphere), g(atmosphere), b(atmosphere), 0.35F);
            blitCentered(graphics, GLOW, radius * 1.9F);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
        }
        if (body.ringed) {
            drawRing(graphics, radius, body == Body.URANUS);
        }
        blitCentered(graphics, texture(body), radius);
        //Ombre : face opposee au Soleil
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotation(lightAngle(pos) + Mth.PI));
        blitCentered(graphics, SHADOW, radius + 0.5F);
        pose.popPose();
        graphics.flush();
        pose.popPose();
    }

    /** Image 64x64 centree sur l'origine, de demi-cote {@code half} (taille flottante : pas d'a-coups au zoom). */
    private static void blitCentered(GuiGraphics graphics, ResourceLocation texture, float half) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.scale(half / 32F, half / 32F, 1);
        graphics.blit(texture, -32, -32, 64, 64, 0, 0, 64, 64, 64, 64);
        pose.popPose();
    }

    private static int atmosphere(Body body) {
        return switch (body) {
            case EARTH -> 0x6FB0FF;
            case VENUS -> 0xFFD890;
            case MARS -> 0xFF9A70;
            case TITAN -> 0xFFB050;
            case URANUS -> 0xA0F0FF;
            case NEPTUNE -> 0x6F90FF;
            case JUPITER, SATURN -> 0xFFE0B0;
            default -> 0;
        };
    }

    private void drawRing(GuiGraphics graphics, float radius, boolean thin) {
        float w = radius * (thin ? 2.0F : 2.4F), h = radius * (thin ? 1.2F : 0.8F);
        RenderSystem.setShaderColor(thin ? 0.75F : 1F, 0.95F, thin ? 1F : 0.82F, thin ? 0.55F : 0.9F);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.scale(w / 32F, h / 16F, 1);
        graphics.blit(RING, -32, -16, 64, 32, 0, 0, 64, 32, 64, 32);
        pose.popPose();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private void drawLabels(GuiGraphics graphics) {
        float labelScale = Mth.clamp(0.6F + 0.1F * bodyZoom(), 0.66F, 1F);
        for (Body body : visible) {
            float[] pos = positions.get(body);
            drawLabel(graphics, body, pos[0], pos[1] + radii.get(body) + 3, labelScale);
        }
        if (focus == null && sector == Sector.SOL) {
            //Nom de la ceinture, en bas de l'anneau
            float radius = BELT_OUTER * unit();
            drawLabel(graphics, Body.ASTEROID_BELT, centerX(), centerY() + radius * SQUASH + 3, labelScale);
        }
    }

    private void drawLabel(GuiGraphics graphics, Body body, float x, float y, float labelScale) {
        Component name = known(body) ? Component.translatable(body.getTranslationKey()) : Component.literal("???");
        boolean isHere = focus == null ? containsHere(body) : body.isHere(here);
        int color = canTravel(body) ? 0x77FF99 : isHere ? 0x77DDFF : body == hovered ? 0xFFFFFF : 0x9AA0AA;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(labelScale, labelScale, 1);
        graphics.drawString(font, name, -font.width(name) / 2, 0, color, true);
        pose.popPose();
    }

    private void drawSelection(GuiGraphics graphics, Body body, int color, float alpha) {
        graphics.flush();
        float[] pos = positions.get(body);
        float radius = radii.get(body) + 4 + Mth.sin(seconds * 4) * 1.2F;
        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        //Cercle en tirets qui tourne
        int segments = 32;
        for (int i = 0; i < segments; i++) {
            if (i % 4 == 3) {
                continue;
            }
            float a0 = i * Mth.TWO_PI / segments + seconds * 0.8F, a1 = (i + 1) * Mth.TWO_PI / segments + seconds * 0.8F;
            builder.vertex(matrix, pos[0] + Mth.cos(a0) * radius, pos[1] + Mth.sin(a0) * radius, 0).color(r(color), g(color), b(color), alpha).endVertex();
            builder.vertex(matrix, pos[0] + Mth.cos(a1) * radius, pos[1] + Mth.sin(a1) * radius, 0).color(r(color), g(color), b(color), alpha).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());
    }

    // ------------------------------------------------------------------
    // Interface : barre du haut, navigateur (systeme puis astre), fiche de mission
    // ------------------------------------------------------------------

    private static final int NAV_W = 132, NAV_ROW = 20, NAV_X = 8, NAV_Y = 26, MISSION_H = 48;
    private static final int PANEL_BG = 0xC80A0F18, PANEL_EDGE = 0x5590A8C8, ACCENT = 0xFF55CCFF;
    private static final int GREEN = 0xFF55FF88, ORANGE = 0xFFFFAA44, VIOLET = 0xFFB58CFF, DIM = 0xFF707884;

    /** Les systemes du navigateur : icone, vue detaillee de la carte, astres ou l'on peut se poser. */
    private enum StarSystem {
        EARTH(Body.EARTH, Body.EARTH, Body.EARTH, Body.MOON),
        MARS(Body.MARS, Body.MARS, Body.MARS, Body.PHOBOS, Body.DEIMOS),
        INNER(Body.VENUS, null, Body.SUN, Body.VENUS, Body.MERCURY),
        BELT(Body.CERES, Body.ASTEROID_BELT, Body.CERES, Body.VESTA, Body.PALLAS, Body.HYGIEA),
        JUPITER(Body.JUPITER, Body.JUPITER, Body.JUPITER, Body.IO, Body.EUROPA, Body.GANYMEDE, Body.CALLISTO),
        SATURN(Body.SATURN, Body.SATURN, Body.SATURN, Body.ENCELADUS, Body.TITAN),
        URANUS(Body.URANUS, Body.URANUS, Body.URANUS, Body.MIRANDA, Body.TITANIA),
        NEPTUNE(Body.NEPTUNE, Body.NEPTUNE, Body.NEPTUNE, Body.TRITON),
        PLUTO(Body.PLUTO, Body.PLUTO, Body.PLUTO, Body.CHARON),
        //Alpha du Centaure
        RIGIL(Body.CENTAURI_A, null, Body.CENTAURI_A, Body.TOLIMAN_B, Body.CENTAURI_B),
        PROXIMA(Body.PROXIMA, Body.PROXIMA, Body.PROXIMA, Body.PROXIMA_B, Body.PROXIMA_D),
        //TRAPPIST-1
        TRAPPIST_INNER(Body.TRAPPIST_1B, null, Body.TRAPPIST_1, Body.TRAPPIST_1B, Body.TRAPPIST_1C, Body.TRAPPIST_1D),
        TRAPPIST_OUTER(Body.TRAPPIST_1F, null, Body.TRAPPIST_1E, Body.TRAPPIST_1F, Body.TRAPPIST_1G, Body.TRAPPIST_1H);

        final Body icon;
        @Nullable
        final Body focus;
        final List<Body> bodies;
        final Sector sector;

        StarSystem(Body icon, @Nullable Body focus, Body... bodies) {
            this.icon = icon;
            this.focus = focus;
            this.bodies = List.of(bodies);
            this.sector = icon.sector();
        }

        static List<StarSystem> of(Sector sector) {
            List<StarSystem> list = new ArrayList<>();
            for (StarSystem system : values()) {
                if (system.sector == sector) {
                    list.add(system);
                }
            }
            return list;
        }

        String key() {
            return "map.pioneers.system." + name().toLowerCase();
        }

        @Nullable
        static StarSystem of(Body body) {
            for (StarSystem system : values()) {
                if (system.bodies.contains(body) || system.focus == body || system.icon == body) {
                    return system;
                }
            }
            return null;
        }
    }

    /** Systeme ouvert dans le navigateur (null = liste des systemes). */
    @Nullable
    private StarSystem navSystem;
    /** Fenetre de logistique ouverte. */
    private boolean logisticsOpen;
    /** Navigateur replie (garde d'une ouverture de la carte a l'autre). */
    private static boolean navCollapsed;
    private int stationScroll, flightScroll;

    private void addHitbox(int x0, int y0, int x1, int y1, Runnable action) {
        hitboxes.add(new Object[]{new int[]{x0, y0, x1, y1}, action});
    }

    private int navRows() {
        return navSystem == null ? StarSystem.of(sector).size() : navSystem.bodies.size();
    }

    /** Lignes visibles du navigateur : il s'arrete au-dessus de la fiche de mission (le reste defile). */
    private int navVisibleRows() {
        int bottom = selected != null ? height - 18 - MISSION_H - 6 : missionLocked() ? height - 24 : height - 16;
        return Mth.clamp((bottom - (NAV_Y + 18) - 3) / NAV_ROW, 1, navRows());
    }

    private boolean overPanel(int mouseX, int mouseY) {
        if (logisticsOpen) {
            return true;
        }
        if (navCollapsed) {
            if (mouseX < NAV_X + 22 && mouseY > NAV_Y - 2 && mouseY < NAV_Y + 22) {
                return true;
            }
        } else if (mouseX < NAV_X + NAV_W + 4 && mouseY > NAV_Y - 2 && mouseY < NAV_Y + 18 + navVisibleRows() * NAV_ROW + 4) {
            return true;
        }
        int[] m = missionBounds;
        return selected != null && m != null && mouseX >= m[0] && mouseX < m[2] && mouseY >= m[1] && mouseY < m[3];
    }

    private void panel(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        graphics.fill(x0, y0, x1, y1, PANEL_BG);
        graphics.fill(x0, y0, x1, y0 + 1, PANEL_EDGE);
        graphics.fill(x0, y1 - 1, x1, y1, 0x3390A8C8);
    }

    private void smallText(GuiGraphics graphics, Component text, float x, float y, int color, float scale) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }

    /** Texte tronque avec « … » pour tenir dans {@code maxWidth} pixels d'ecran. */
    private void fitText(GuiGraphics graphics, Component text, float x, float y, int color, float scale, float maxWidth) {
        String plain = text.getString();
        if (font.width(plain) * scale > maxWidth) {
            while (plain.length() > 1 && font.width(plain + "…") * scale > maxWidth) {
                plain = plain.substring(0, plain.length() - 1);
            }
            plain += "…";
        }
        smallText(graphics, Component.literal(plain), x, y, color, scale);
    }

    private boolean button(GuiGraphics graphics, Component label, int x0, int y0, int x1, int y1, int mouseX, int mouseY, boolean active, Runnable action) {
        boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
        graphics.fill(x0, y0, x1, y1, active ? 0xC0284058 : hover ? 0xC0203448 : 0x90182430);
        graphics.fill(x0, y0, x1, y0 + 1, active ? 0xFF55CCFF : 0x5590A8C8);
        smallText(graphics, label, x0 + (x1 - x0 - font.width(label) * 0.8F) / 2F, y0 + (y1 - y0 - 6) / 2F, hover || active ? 0xFFFFFFFF : 0xFFB8C8D8, 0.8F);
        addHitbox(x0, y0, x1, y1, action);
        return hover;
    }

    private void drawTopBar(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fillGradient(0, 0, width, 22, 0xD0000000, 0x00000000);
        Component heading = galaxy ? Component.translatable("map.pioneers.galaxy.title")
                                   : Component.translatable(sector.getTranslationKey()).copy()
                                           .append(focus == null ? Component.empty() : Component.literal(" · ").append(Component.translatable(focus.getTranslationKey())));
        graphics.drawCenteredString(font, heading, width / 2, 7, 0xE8F0FF);
        //Carte galactique (G)
        Component galaxyLabel = Component.literal("✦ ").append(Component.translatable(galaxy ? "map.pioneers.galaxy.back" : "map.pioneers.galaxy"))
              .append("  [G]");
        int gw = (int) (font.width(galaxyLabel) * 0.8F) + 14;
        button(graphics, galaxyLabel, 8, 6, 8 + gw, 18, mouseX, mouseY, galaxy, this::toggleGalaxy);
        if (galaxy) {
            return;
        }
        //Fenetre de logistique (L), avec le nombre de vols en cours
        int flights = ClientCargoMap.getFlights().size();
        Component logistics = PiLang.MAP_LOGISTICS.translate().copy().append(flights > 0 ? " · " + flights : "").append("  [L]");
        int w = (int) (font.width(logistics) * 0.8F) + 14;
        int x = width - 8 - w;
        button(graphics, logistics, x, 6, x + w, 18, mouseX, mouseY, logisticsOpen, () -> logisticsOpen = !logisticsOpen);
        if (flights > 0) {
            graphics.fill(x + 3, 10, x + 6, 13, 0xFFE0A040);
        }
    }

    // ---------------------------------------------------------------- navigateur

    private boolean systemKnown(StarSystem system) {
        return system == StarSystem.EARTH || system.bodies.stream().anyMatch(this::known) || known(system.icon);
    }

    private boolean systemHere(StarSystem system) {
        return system.bodies.stream().anyMatch(b -> b.isHere(here));
    }

    private void openSystem(@Nullable StarSystem system) {
        navSystem = system;
        navScroll = 0;
        if (system == null) {
            if (focus != null) {
                back();
            }
            return;
        }
        if (system.focus != focus) {
            float[] pos = system.focus == null ? null : positions.get(system.focus);
            setFocus(system.focus, pos == null ? baseX : pos[0], pos == null ? baseY : pos[1]);
        }
    }

    /** Navigateur : on choisit d'abord un systeme, puis un astre de ce systeme. */
    private void drawNavigator(GuiGraphics graphics, int mouseX, int mouseY) {
        if (navCollapsed) {
            //Onglet replie : un clic (ou N) redeploie le navigateur
            int x0 = NAV_X, y0 = NAV_Y, x1 = x0 + 20, y1 = y0 + 20;
            boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
            panel(graphics, x0, y0, x1, y1);
            graphics.fill(x0, y0, x0 + 2, y1, ACCENT);
            smallText(graphics, Component.literal("☰"), x0 + 6, y0 + 6, hover ? 0xFFFFFFFF : 0xFF8FB8D8, 1.0F);
            addHitbox(x0, y0, x1, y1, () -> navCollapsed = false);
            if (hover) {
                graphics.renderTooltip(font, Component.translatable("map.pioneers.nav.expand"), mouseX, mouseY);
            }
            return;
        }
        int rowsShown = navVisibleRows();
        navScroll = Mth.clamp(navScroll, 0, Math.max(0, navRows() - rowsShown));
        int x0 = NAV_X, y0 = NAV_Y, x1 = x0 + NAV_W, y1 = y0 + 18 + rowsShown * NAV_ROW + 3;
        panel(graphics, x0, y0, x1, y1);
        //Bouton de repli, a droite de l'en-tete
        boolean hoverCollapse = mouseX >= x1 - 15 && mouseX < x1 - 2 && mouseY >= y0 + 2 && mouseY < y0 + 15;
        graphics.fill(x1 - 15, y0 + 2, x1 - 2, y0 + 15, hoverCollapse ? 0x60FFFFFF : 0x20FFFFFF);
        smallText(graphics, Component.literal("◂"), x1 - 11, y0 + 5, hoverCollapse ? 0xFFFFFFFF : 0xFF8FB8D8, 0.9F);
        addHitbox(x1 - 15, y0 + 2, x1 - 2, y0 + 15, () -> navCollapsed = true);
        if (navSystem == null) {
            fitText(graphics, Component.translatable(sector.getTranslationKey()).copy().append(" · ")
                  .append(Component.translatable("map.pioneers.nav.systems")), x0 + 6, y0 + 6, 0xFF8FB8D8, 0.7F, NAV_W - 24);
        } else {
            //Retour a la liste des systemes
            Component back = Component.literal("◂ ").append(Component.translatable(navSystem.key()));
            boolean hover = mouseX >= x0 && mouseX < x1 - 16 && mouseY >= y0 && mouseY < y0 + 17;
            if (hover) {
                graphics.fill(x0 + 1, y0 + 1, x1 - 17, y0 + 17, 0x30FFFFFF);
            }
            fitText(graphics, back, x0 + 6, y0 + 6, hover ? 0xFFFFFFFF : 0xFF8FB8D8, 0.7F, NAV_W - 28);
            addHitbox(x0, y0, x1 - 16, y0 + 17, () -> openSystem(null));
        }
        graphics.fill(x0 + 4, y0 + 16, x1 - 4, y0 + 17, 0x3090A8C8);
        int y = y0 + 18;
        if (navSystem == null) {
            List<StarSystem> systems = StarSystem.of(sector);
            for (int i = navScroll; i < systems.size() && i < navScroll + rowsShown; i++) {
                drawSystemRow(graphics, systems.get(i), x0, y, x1, mouseX, mouseY);
                y += NAV_ROW;
            }
        } else {
            for (int i = navScroll; i < navSystem.bodies.size() && i < navScroll + rowsShown; i++) {
                drawBodyRow(graphics, navSystem.bodies.get(i), x0, y, x1, mouseX, mouseY);
                y += NAV_ROW;
            }
        }
        //Fleches de defilement
        if (navScroll > 0) {
            smallText(graphics, Component.literal("▲"), x1 - 10, y0 + 19, 0xFF8FB8D8, 0.6F);
        }
        if (navScroll + rowsShown < navRows()) {
            smallText(graphics, Component.literal("▼"), x1 - 10, y1 - 7, 0xFF8FB8D8, 0.6F);
        }
    }

    private void drawSystemRow(GuiGraphics graphics, StarSystem system, int x0, int y, int x1, int mouseX, int mouseY) {
        boolean known = systemKnown(system);
        boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + NAV_ROW;
        if (hover && known) {
            graphics.fill(x0 + 1, y, x1 - 1, y + NAV_ROW, 0x30FFFFFF);
        }
        RenderSystem.setShaderColor(known ? 1 : 0.12F, known ? 1 : 0.12F, known ? 1 : 0.16F, 1);
        graphics.blit(texture(system.icon), x0 + 5, y + 3, 14, 14, 0, 0, 64, 64, 64, 64);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        if (!known) {
            smallText(graphics, Component.literal("???"), x0 + 23, y + 4, DIM, 0.75F);
            var discovery = ResearchEffects.discoveryOf(system.bodies.get(0));
            if (discovery != null) {
                fitText(graphics, Component.translatable("map.pioneers.nav.discover", Component.translatable(discovery.getTranslationKey())),
                      x0 + 23, y + 12, 0xFF5A6270, 0.55F, NAV_W - 28);
            }
            return;
        }
        boolean isHere = systemHere(system);
        fitText(graphics, Component.translatable(system.key()), x0 + 23, y + 4, isHere ? 0xFF77DDFF : 0xFFF0F4FF, 0.75F, NAV_W - 50);
        int mastered = 0, landable = 0;
        for (Body body : system.bodies) {
            if (BodyResearch.isResearchBody(body) && ClientResearch.mastery(body) >= 4) {
                mastered++;
            }
            if (canTravel(body)) {
                landable++;
            }
        }
        Component sub = Component.translatable("map.pioneers.nav.bodies", system.bodies.size());
        if (system != StarSystem.EARTH) {
            sub = sub.copy().append(" · ").append(Component.translatable("map.pioneers.nav.mastered", mastered));
        }
        fitText(graphics, sub, x0 + 23, y + 12, 0xFF8090A0, 0.55F, NAV_W - 40);
        //Pastille : ici / accessible depuis la fusee
        if (isHere) {
            chip(graphics, PiLang.MAP_HERE_SHORT.translate(), x1 - 4, y + 4, 0xFF2A6A88);
        } else if (tier != null && landable > 0) {
            chip(graphics, Component.literal("✈ " + landable), x1 - 4, y + 4, 0xFF2A7048);
        }
        Component arrow = Component.literal("›");
        smallText(graphics, arrow, x1 - 8, y + 12, 0xFF607080, 0.7F);
        addHitbox(x0, y, x1, y + NAV_ROW, () -> openSystem(system));
    }

    private void drawBodyRow(GuiGraphics graphics, Body body, int x0, int y, int x1, int mouseX, int mouseY) {
        boolean known = known(body);
        boolean isHere = body.isHere(here);
        boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + NAV_ROW;
        if (body == selected) {
            graphics.fill(x0 + 1, y, x1 - 1, y + NAV_ROW, 0x60307090);
            graphics.fill(x0 + 1, y, x0 + 3, y + NAV_ROW, stateColor(body));
        } else if (hover && known) {
            graphics.fill(x0 + 1, y, x1 - 1, y + NAV_ROW, 0x30FFFFFF);
        }
        RenderSystem.setShaderColor(known ? 1 : 0.12F, known ? 1 : 0.12F, known ? 1 : 0.16F, canTravel(body) || isHere || !known ? 1 : 0.55F);
        graphics.blit(texture(body), x0 + 6, y + 3, 14, 14, 0, 0, 64, 64, 64, 64);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        if (!known) {
            smallText(graphics, Component.literal("???"), x0 + 24, y + 6, DIM, 0.75F);
            return;
        }
        int nameColor = isHere ? 0xFF77DDFF : canTravel(body) ? 0xFFF0F4FF : 0xFFB0B8C4;
        fitText(graphics, Component.translatable(body.getTranslationKey()), x0 + 24, y + 3, nameColor, 0.75F, NAV_W - 56);
        fitText(graphics, statusShort(body), x0 + 24, y + 12, stateColor(body), 0.55F, NAV_W - 30);
        if (BodyResearch.isResearchBody(body)) {
            pips(graphics, x1 - 25, y + 5, ClientResearch.mastery(body));
        }
        addHitbox(x0, y, x1, y + NAV_ROW, () -> selected = body == selected ? null : body);
    }

    /** Quatre pastilles de maitrise : orbite, surface, geologie, biologie. */
    private static void pips(GuiGraphics graphics, int x, int y, int mastery) {
        for (int k = 0; k < 4; k++) {
            graphics.fill(x + k * 5, y, x + k * 5 + 4, y + 3, k < mastery ? 0xFFD2A040 : 0xFF2A3440);
        }
    }

    private void chip(GuiGraphics graphics, Component text, int right, int y, int color) {
        int w = (int) (font.width(text) * 0.6F) + 6;
        graphics.fill(right - w, y, right, y + 8, color);
        smallText(graphics, text, right - w + 3, y + 2, 0xFFFFFFFF, 0.6F);
    }

    /** Peut-on s'y rendre maintenant : fusee suffisante, astre decouvert et orbite etudiee. */
    private boolean canTravel(Body body) {
        return reachable(body) && ClientResearch.canLand(body);
    }

    private int stateColor(Body body) {
        if (body.dimension() == null) {
            return body.isStar() ? 0xFFFFC860 : DIM;
        }
        if (body.isHere(here)) {
            return ACCENT;
        }
        if (canTravel(body)) {
            return GREEN;
        }
        if (known(body) && !ClientResearch.canLand(body)) {
            return VIOLET;
        }
        return ORANGE;
    }

    /** Etat en quelques mots : ici, accessible, orbite a etudier, fusee requise. */
    private Component statusShort(Body body) {
        if (body.dimension() == null) {
            return Component.translatable(body.isStar() ? "map.pioneers.nav.star" : "map.pioneers.nav.orbit_only");
        }
        if (body.isHere(here)) {
            return PiLang.MAP_HERE.translate();
        }
        if (!ClientResearch.canLand(body)) {
            return Component.translatable("map.pioneers.nav.orbit_needed");
        }
        if (tier == null) {
            return Component.translatable("map.pioneers.nav.tier", body.requiredTier());
        }
        if (canTravel(body)) {
            return PiLang.MAP_REACHABLE.translate();
        }
        return PiLang.MAP_NEEDS_TIER.translate(body.requiredTier());
    }

    /** Fiche de mission en bas : l'astre choisi, sa maitrise et le bouton de decollage. */
    private void drawMissionBar(GuiGraphics graphics, int mouseX, int mouseY) {
        Body body = selected;
        int h = MISSION_H;
        //Largeur : centree si possible, sinon decalee a droite du navigateur (qui s'arrete au-dessus d'elle de toute facon)
        int w = Mth.clamp(width - 16, 230, 340);
        int x0 = Math.max(8, width / 2 - w / 2), y0 = height - 18 - h, x1 = Math.min(width - 8, x0 + w), y1 = y0 + h;
        w = x1 - x0;
        missionBounds = new int[]{x0, y0, x1, y1};
        panel(graphics, x0, y0, x1, y1);
        graphics.fill(x0, y0, x0 + 2, y1, stateColor(body));
        graphics.blit(texture(body), x0 + 8, y0 + 8, 32, 32, 0, 0, 64, 64, 64, 64);
        int tx = x0 + 46;
        fitText(graphics, Component.translatable(body.getTranslationKey()), tx, y0 + 6, 0xFFFFFFFF, 1.0F, Math.max(40, x1 - 20 - tx));
        Component info = Component.translatable(body.getKindKey()).append(" · ")
              .append(PiLang.MAP_GRAVITY.translate(String.format("%.2f", body.surfaceGravity())));
        StarSystem system = StarSystem.of(body);
        if (system != null) {
            info = info.copy().append(" · ").append(Component.translatable(system.key()));
        }
        if (body.dimension() != null) {
            int suit = Environments.get(body.dimension()).suitTier();
            if (suit > 0) {
                info = info.copy().append(" · ").append(Component.translatable("map.pioneers.suit", suit));
            }
        }
        //Les boutons occupent la droite : le texte s'arrete avant eux
        boolean stationButton = tier != null && OrbitalStations.canHost(body) && ClientResearch.isCompleted(ResearchNode.ORBITAL_STATIONS) && known(body);
        boolean departButton = tier != null && !body.isHere(here) && body.dimension() != null;
        //1.0.7 : chantier de la megastructure de l'astre (demarrage depuis la fusee, puis avancement)
        int mega = fr.pioneers.space.mega.Megastructures.byBody(body);
        boolean megaButton = tier != null && known(body) && mega >= 0 && !fr.pioneers.space.mega.client.MegaClient.unlocked(mega);
        int buttonsLeft = x1 - 8 - (departButton ? 78 : 0) - (stationButton ? 80 : 0) - (megaButton ? 90 : 0);
        int textW = Math.max(40, buttonsLeft - 6 - tx);
        fitText(graphics, info, tx, y0 + 17, 0xFF9AAABB, 0.65F, textW);
        //Etat detaille
        Component state;
        if (body.dimension() == null) {
            state = Component.translatable(body.isStar() ? "map.pioneers.nav.star_detail" : "map.pioneers.nav.orbit_only_detail");
        } else if (body.isHere(here)) {
            state = PiLang.MAP_HERE.translate();
        } else if (!ClientResearch.canLand(body)) {
            var orbit = BodyResearch.node(body, ResearchNode.Kind.LAB);
            state = orbit == null ? PiLang.MAP_LOCKED.translate()
                                  : Component.translatable("map.pioneers.nav.orbit_research", Component.translatable(orbit.getTranslationKey()));
        } else if (canTravel(body)) {
            state = PiLang.MAP_REACHABLE.translate();
        } else if (tier == null) {
            state = PiLang.MAP_VIEW_ONLY.translate();
        } else {
            state = PiLang.MAP_NEEDS_TIER.translate(body.requiredTier());
        }
        fitText(graphics, state, tx, y0 + 26, stateColor(body), 0.65F, textW);
        //Maitrise de l'astre : 4 paliers nommes
        if (BodyResearch.isResearchBody(body)) {
            int mastery = ClientResearch.mastery(body);
            String[] steps = {"orbit", "surface", "geology", "biology"};
            int cx = tx;
            for (int k = 0; k < 4; k++) {
                Component step = Component.translatable("map.pioneers.mastery." + steps[k]);
                int sw = (int) (font.width(step) * 0.55F) + 8;
                boolean done = k < mastery;
                graphics.fill(cx, y0 + 36, cx + sw, y0 + 44, done ? 0x60D2A040 : 0x40202A36);
                graphics.fill(cx, y0 + 36, cx + 2, y0 + 44, done ? 0xFFD2A040 : 0xFF3A4656);
                smallText(graphics, step, cx + 5, y0 + 38, done ? 0xFFF0D8A0 : 0xFF6A7686, 0.55F);
                cx += sw + 2;
            }
        }
        //Bouton Decoller (seulement en orbite, vers un astre accessible)
        if (departButton) {
            boolean ok = canTravel(body);
            int bx0 = x1 - 80, by0 = y0 + 13, bx1 = x1 - 8, by1 = y0 + 35;
            boolean hover = ok && mouseX >= bx0 && mouseX < bx1 && mouseY >= by0 && mouseY < by1;
            float pulse = 0.5F + 0.5F * Mth.sin(seconds * 4);
            int fill = !ok ? 0xFF2A3038 : hover ? 0xFF3AC070 : (0xFF000000 | (int) (0x20 + 0x18 * pulse) << 16 | (int) (0x80 + 0x30 * pulse) << 8 | 0x50);
            graphics.fill(bx0, by0, bx1, by1, fill);
            graphics.fill(bx0, by0, bx1, by0 + 1, ok ? 0xFF88FFB0 : 0xFF404850);
            graphics.drawCenteredString(font, PiLang.MAP_DEPART.translate(), (bx0 + bx1) / 2, by0 + 7, ok ? 0xFFFFFF : 0x707880);
            if (ok) {
                addHitbox(bx0, by0, bx1, by1, () -> travel(body));
            }
        }
        //Station orbitale de l'astre (recherche « Stations orbitales ») : amarrage, ou deploiement avec un kit
        if (stationButton) {
            Body origin = SolarSystem.origin(here, hereX);
            boolean ok = Math.max(body.requiredTier(), origin == null ? 1 : origin.requiredTier()) <= tier.level
                         && (!body.isStar() || ClientResearch.isCompleted(ResearchNode.SOLAR_ABSORPTION));
            int bx1 = departButton ? x1 - 86 : x1 - 8, bx0 = bx1 - 78, by0 = y0 + 13, by1 = y0 + 35;
            boolean hover = ok && mouseX >= bx0 && mouseX < bx1 && mouseY >= by0 && mouseY < by1;
            graphics.fill(bx0, by0, bx1, by1, !ok ? 0xFF2A3038 : hover ? 0xFF3A86C0 : 0xFF1E3E5C);
            graphics.fill(bx0, by0, bx1, by0 + 1, ok ? 0xFF88C8FF : 0xFF404850);
            Component label = Component.translatable("map.pioneers.station");
            smallText(graphics, label, (bx0 + bx1) / 2F - font.width(label) * 0.4F, by0 + 8, ok ? 0xFFFFFFFF : 0xFF707880, 0.8F);
            if (ok) {
                addHitbox(bx0, by0, bx1, by1, () -> {
                    PiNetwork.sendToServer(new PacketSelectDestination(body, true));
                    depart();
                });
            }
        }
        if (megaButton) {
            Body origin = SolarSystem.origin(here, hereX);
            boolean reach = Math.max(body.requiredTier(), origin == null ? 1 : origin.requiredTier()) <= tier.level;
            boolean opened = !fr.pioneers.space.mega.client.MegaClient.opener(mega).isEmpty();
            boolean ok = opened || reach;
            int bx1 = x1 - 8 - (departButton ? 78 : 0) - (stationButton ? 80 : 0), bx0 = bx1 - 88, by0 = y0 + 13, by1 = y0 + 35;
            boolean hover = ok && mouseX >= bx0 && mouseX < bx1 && mouseY >= by0 && mouseY < by1;
            int accent = 0xFF000000 | fr.pioneers.space.mega.Megastructures.color(mega);
            graphics.fill(bx0, by0, bx1, by1, !ok ? 0xFF2A3038 : hover ? 0xFF4A3A6A : 0xFF2C2440);
            graphics.fill(bx0, by0, bx1, by0 + 1, ok ? accent : 0xFF404850);
            Component label;
            if (opened) {
                int total = fr.pioneers.space.mega.Megastructures.total(mega);
                int have = fr.pioneers.space.mega.client.MegaClient.deliveredTotal(mega);
                label = Component.translatable("map.pioneers.mega_site.progress");
                graphics.fill(bx0, by1 - 2, bx0 + (int) ((bx1 - bx0) * Math.min(1F, have / (float) Math.max(1, total))), by1, accent);
            } else {
                label = Component.translatable("map.pioneers.mega_site.start");
            }
            fitText(graphics, label, bx0 + 4, by0 + 8, ok ? 0xFFFFFFFF : 0xFF707880, 0.7F, bx1 - bx0 - 8);
            if (ok) {
                int m = mega;
                addHitbox(bx0, by0, bx1, by1, () -> {
                    if (opened) {
                        Minecraft.getInstance().setScreen(new fr.pioneers.space.mega.client.MegaSiteScreen(m, this));
                    } else {
                        PiNetwork.sendToServer(new fr.pioneers.space.mega.PacketMegaOpen(m));
                    }
                });
            }
        }
        //Fermer la fiche
        int cx0 = x1 - 11, cy0 = y0 + 2;
        boolean hoverClose = mouseX >= cx0 && mouseX < cx0 + 9 && mouseY >= cy0 && mouseY < cy0 + 9;
        smallText(graphics, Component.literal("✕"), cx0 + 1, cy0 + 1, hoverClose ? 0xFFFFFFFF : 0xFF607080, 0.7F);
        addHitbox(cx0, cy0, cx0 + 9, cy0 + 9, () -> selected = null);
    }

    /** Infobulle compacte pres du curseur. */
    private void drawTooltip(GuiGraphics graphics, Body body, int mouseX, int mouseY) {
        boolean known = known(body);
        ResearchNode discovery = ResearchEffects.discoveryOf(body);
        Component name = known ? Component.translatable(body.getTranslationKey()) : Component.translatable("map.pioneers.unknown");
        Component kind = known || discovery == null ? Component.translatable(body.getKindKey())
                                                    : Component.translatable("map.pioneers.unknown_hint", Component.translatable(discovery.getTranslationKey()));
        Component status = known && body.dimension() != null ? statusShort(body) : null;
        int w = Math.max(font.width(name), (int) (font.width(kind) * 0.7F));
        if (status != null) {
            w = Math.max(w, (int) (font.width(status) * 0.7F));
        }
        w += 10;
        int h = status != null ? 30 : 22;
        int x0 = Math.min(mouseX + 10, width - w - 4), y0 = Math.min(mouseY + 8, height - h - 4);
        graphics.fill(x0, y0, x0 + w, y0 + h, 0xE0080C14);
        graphics.fill(x0, y0, x0 + 1, y0 + h, body.dimension() != null ? stateColor(body) : 0xFF607080);
        graphics.drawString(font, name, x0 + 5, y0 + 3, 0xFFFFFF, false);
        smallText(graphics, kind, x0 + 5, y0 + 13, 0xFF9AAABB, 0.7F);
        if (status != null) {
            smallText(graphics, status, x0 + 5, y0 + 21, stateColor(body), 0.7F);
        }
    }

    // ------------------------------------------------------------------
    // Reseau cargo sur la carte
    // ------------------------------------------------------------------

    @Nullable
    private float[] anchor(Body body) {
        float[] pos = positions.get(body);
        if (pos == null && focus == null) {
            pos = positions.get(body.getPrimary());
        }
        return pos;
    }

    private void drawCargoRoutes(GuiGraphics graphics) {
        var flights = ClientCargoMap.getFlights();
        if (flights.isEmpty()) {
            return;
        }
        graphics.flush();
        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        List<float[]> dots = new ArrayList<>();
        for (var flight : flights) {
            Body from = Body.ALL.get(flight.fromBody()), to = Body.ALL.get(flight.toBody());
            float[] a = anchor(from), b = anchor(to);
            if (a == null || b == null) {
                continue;
            }
            int color = CargoType.byIndex(flight.type()).color;
            //Courbe de Bezier, bombee vers le haut ; petite boucle si les deux astres se confondent
            float dx = b[0] - a[0], dy = b[1] - a[1];
            float dist = Mth.sqrt(dx * dx + dy * dy);
            float cx, cy;
            if (dist < 4) {
                cx = a[0];
                cy = a[1] - 26;
                b = new float[]{a[0] + 10, a[1] - 4};
                a = new float[]{a[0] - 10, a[1] - 4};
            } else {
                cx = (a[0] + b[0]) / 2 + dy * 0.25F;
                cy = (a[1] + b[1]) / 2 - Math.abs(dx) * 0.25F - 10;
            }
            int segments = 40;
            float flow = seconds * 1.5F;
            for (int i = 0; i < segments; i++) {
                float t0 = i / (float) segments, t1 = (i + 1) / (float) segments;
                //Tirets qui glissent dans le sens du vol
                if (((int) Math.floor(i * 0.5F - flow)) % 2 != 0) {
                    continue;
                }
                float[] p0 = bezier(a, cx, cy, b, t0), p1 = bezier(a, cx, cy, b, t1);
                float alpha = flight.returning() ? 0.25F : 0.6F;
                builder.vertex(matrix, p0[0], p0[1], 0).color(r(color), g(color), b(color), alpha).endVertex();
                builder.vertex(matrix, p1[0], p1[1], 0).color(r(color), g(color), b(color), alpha).endVertex();
            }
            float progress = Math.min(1, flight.progress());
            float[] p;
            if (flight.waiting() || flight.landing()) {
                //En orbite autour de la destination
                float angle = seconds * 2 + flight.toBody();
                float[] target = anchor(to);
                float radius = radii.getOrDefault(to, radii.getOrDefault(to.getPrimary(), 4F)) + 6;
                p = new float[]{target[0] + Mth.cos(angle) * radius, target[1] + Mth.sin(angle) * radius * SQUASH};
            } else {
                p = bezier(a, cx, cy, b, progress);
            }
            dots.add(new float[]{p[0], p[1], color, flight.returning() ? 0.6F : 1});
        }
        BufferUploader.drawWithShader(builder.end());
        //Fusees : point lumineux avec halo
        for (float[] dot : dots) {
            int color = (int) dot[2];
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(dot[0], dot[1], 0);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShaderColor(r(color), g(color), b(color), 0.8F * dot[3]);
            blitCentered(graphics, GLOW, 5 + Mth.sin(seconds * 6) * 0.8F);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
            pose.popPose();
            graphics.fill((int) dot[0] - 1, (int) dot[1] - 1, (int) dot[0] + 1, (int) dot[1] + 1, 0xFFFFFFFF);
        }
        graphics.flush();
    }

    private static float[] bezier(float[] a, float cx, float cy, float[] b, float t) {
        float u = 1 - t;
        return new float[]{u * u * a[0] + 2 * u * t * cx + t * t * b[0], u * u * a[1] + 2 * u * t * cy + t * t * b[1]};
    }

    /** Affichage des routes cargo sur la carte (case a cocher de la fenetre de logistique). */
    private boolean showRoutes = true;

    private int[] logisticsBounds() {
        int w = Math.min(380, width - 40), h = Math.min(250, height - 60);
        int x0 = (width - w) / 2, y0 = (height - h) / 2 + 6;
        return new int[]{x0, y0, x0 + w, y0 + h};
    }

    /** Fenetre de logistique : stations du reseau (a gauche) et vols en cours (a droite). */
    private void drawLogisticsPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, height, 0x90000000);
        int[] b = logisticsBounds();
        int x0 = b[0], y0 = b[1], x1 = b[2], y1 = b[3];
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF2A3A4C);
        graphics.fill(x0, y0, x1, y1, 0xF00A0F18);
        graphics.fill(x0, y0, x1, y0 + 18, 0xFF101A26);
        graphics.fill(x0, y0 + 18, x1, y0 + 19, 0xFFE0A040);
        var stations = ClientCargoMap.getStations();
        var flights = ClientCargoMap.getFlights();
        graphics.drawString(font, Component.translatable("map.pioneers.logistics.title"), x0 + 8, y0 + 5, 0xFFE0A040, false);
        Component summary = Component.translatable("map.pioneers.logistics.summary", stations.size(), flights.size());
        smallText(graphics, summary, x0 + 14 + font.width(Component.translatable("map.pioneers.logistics.title")), y0 + 7, 0xFF9AAABB, 0.7F);
        //Fermer
        boolean hoverClose = mouseX >= x1 - 16 && mouseX < x1 - 2 && mouseY >= y0 + 2 && mouseY < y0 + 16;
        graphics.fill(x1 - 16, y0 + 2, x1 - 2, y0 + 16, hoverClose ? 0xFF7A2A2A : 0x00000000);
        graphics.drawString(font, "✕", x1 - 12, y0 + 5, hoverClose ? 0xFFFFFFFF : 0xFF9AAABB, false);
        addHitbox(x1 - 16, y0 + 2, x1 - 2, y0 + 16, () -> logisticsOpen = false);
        int split = x0 + (x1 - x0) * 2 / 5;
        int top = y0 + 24, bottom = y1 - 20;
        graphics.fill(split, top, split + 1, bottom, 0x3090A8C8);
        //Stations, regroupees par astre
        smallText(graphics, Component.translatable("map.pioneers.logistics.stations"), x0 + 8, top, 0xFF8FB8D8, 0.7F);
        int rowsVisible = Math.max(1, (bottom - top - 12) / 12);
        stationScroll = Mth.clamp(stationScroll, 0, Math.max(0, stations.size() - rowsVisible));
        int y = top + 11;
        if (stations.isEmpty()) {
            for (var line : font.split(Component.translatable("map.pioneers.logistics.no_station"), (int) ((split - x0 - 16) / 0.65F))) {
                PoseStack pose = graphics.pose();
                pose.pushPose();
                pose.translate(x0 + 8, y, 0);
                pose.scale(0.65F, 0.65F, 1);
                graphics.drawString(font, line, 0, 0, 0xFF707884, false);
                pose.popPose();
                y += 7;
            }
        }
        for (int i = stationScroll; i < stations.size() && i < stationScroll + rowsVisible; i++) {
            var station = stations.get(i);
            Body body = Body.ALL.get(Math.max(0, Math.min(Body.ALL.size() - 1, station.body())));
            int color = station.receiver() ? 0xFF7CC8FF : 0xFFFFC870;
            graphics.blit(texture(body), x0 + 8, y, 9, 9, 0, 0, 64, 64, 64, 64);
            graphics.fill(x0 + 20, y + 3, x0 + 23, y + 6, color);
            fitText(graphics, fr.pioneers.space.cargo.CargoNames.of(station.name()), x0 + 26, y + 1, 0xFFE8F0FF, 0.65F, split - x0 - 60);
            Component role = Component.translatable(station.receiver() ? "map.pioneers.logistics.receiver" : "map.pioneers.logistics.sender");
            smallText(graphics, role, split - 6 - font.width(role) * 0.58F, y + 2, station.receiver() ? 0xFF7CC8FF : 0xFFFFC870, 0.58F);
            y += 12;
        }
        scrollHint(graphics, x0 + 8, bottom - 2, stationScroll, stations.size(), rowsVisible);
        //Vols
        int fx = split + 8;
        smallText(graphics, Component.translatable("map.pioneers.logistics.flights"), fx, top, 0xFF8FB8D8, 0.7F);
        int flightRows = Math.max(1, (bottom - top - 12) / 20);
        flightScroll = Mth.clamp(flightScroll, 0, Math.max(0, flights.size() - flightRows));
        y = top + 11;
        if (flights.isEmpty()) {
            smallText(graphics, PiLang.MAP_LOGISTICS_EMPTY.translate(), fx, y, 0xFF707884, 0.65F);
        }
        for (int i = flightScroll; i < flights.size() && i < flightScroll + flightRows; i++) {
            var flight = flights.get(i);
            var type = CargoType.byIndex(flight.type());
            int color = 0xFF000000 | type.color;
            graphics.fill(fx, y + 1, fx + 3, y + 16, color);
            Component route = fr.pioneers.space.cargo.CargoNames.of(flight.fromName()).copy().append("  →  ")
                  .append(fr.pioneers.space.cargo.CargoNames.of(flight.toName()));
            fitText(graphics, route, fx + 7, y + 1, flight.returning() ? 0xFF8090A0 : 0xFFE8F0FF, 0.65F, x1 - fx - 60);
            Component state = flight.landing() ? Component.translatable("map.pioneers.logistics.landing")
                                               : flight.waiting() ? Component.translatable("map.pioneers.logistics.waiting")
                                                                  : Component.literal(Math.max(0, flight.secondsLeft() - (int) ClientCargoMap.age()) + " s");
            smallText(graphics, state, x1 - 8 - font.width(state) * 0.62F, y + 1, 0xFF9AAABB, 0.62F);
            Component typeName = Component.translatable(type.getTranslationKey()).copy()
                  .append(flight.returning() ? Component.literal(" · ").append(Component.translatable("map.pioneers.logistics.returning")) : Component.empty());
            fitText(graphics, typeName, fx + 7, y + 8, 0xFF8090A0, 0.55F, x1 - fx - 20);
            float progress = Math.min(1, flight.progress());
            graphics.fill(fx + 7, y + 14, x1 - 8, y + 16, 0xFF1A2230);
            graphics.fill(fx + 7, y + 14, fx + 7 + (int) ((x1 - 8 - fx - 7) * progress), y + 16, color);
            y += 20;
        }
        scrollHint(graphics, fx, bottom - 2, flightScroll, flights.size(), flightRows);
        //Pied : routes sur la carte
        graphics.fill(x0, y1 - 18, x1, y1 - 17, 0x3090A8C8);
        Component routes = Component.literal(showRoutes ? "☑ " : "☐ ").append(Component.translatable("map.pioneers.logistics.show_routes"));
        int rw = (int) (font.width(routes) * 0.7F) + 8;
        boolean hoverRoutes = mouseX >= x0 + 6 && mouseX < x0 + 6 + rw && mouseY >= y1 - 15 && mouseY < y1 - 3;
        smallText(graphics, routes, x0 + 10, y1 - 12, hoverRoutes ? 0xFFFFFFFF : 0xFFB8C8D8, 0.7F);
        addHitbox(x0 + 6, y1 - 15, x0 + 6 + rw, y1 - 3, () -> showRoutes = !showRoutes);
        Component hint = Component.translatable("map.pioneers.logistics.hint");
        smallText(graphics, hint, x1 - 8 - font.width(hint) * 0.6F, y1 - 11, 0xFF56606C, 0.6F);
    }

    private void scrollHint(GuiGraphics graphics, int x, int y, int scroll, int size, int visible) {
        if (size > visible) {
            smallText(graphics, Component.literal((scroll > 0 ? "▲ " : "  ") + (scroll + visible < size ? "▼ " : "") + (scroll + 1) + "-"
                                                  + Math.min(size, scroll + visible) + " / " + size), x, y - 5, 0xFF607080, 0.55F);
        }
    }

    // ------------------------------------------------------------------
    // Commandes
    // ------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (logisticsOpen) {
            int[] b = logisticsBounds();
            int split = b[0] + (b[2] - b[0]) * 2 / 5;
            if (mouseX < split) {
                stationScroll -= (int) Math.signum(delta);
            } else {
                flightScroll -= (int) Math.signum(delta);
            }
            return true;
        }
        if (!galaxy && !navCollapsed && mouseX < NAV_X + NAV_W + 4 && mouseY > NAV_Y && mouseY < NAV_Y + 18 + navVisibleRows() * NAV_ROW + 4) {
            navScroll -= (int) Math.signum(delta);
            return true;
        }
        if (galaxy) {
            galaxyZoom = Mth.clamp(galaxyZoom * (float) Math.pow(1.15, delta), 0.6F, 3F);
            return true;
        }
        float next = Mth.clamp(targetZoom * (float) Math.pow(1.18, delta), MIN_ZOOM, MAX_ZOOM);
        if (next != targetZoom) {
            //Le point sous le curseur reste sous le curseur pendant le zoom
            anchorMouseX = (float) mouseX;
            anchorMouseY = (float) mouseY;
            anchorWorldX = ((float) mouseX - baseX - panX) / zoom;
            anchorWorldY = ((float) mouseY - baseY - panY) / zoom;
            anchored = true;
            targetZoom = next;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        //Elements de l'interface (panneaux, boutons)
        for (Object[] box : new ArrayList<>(hitboxes)) {
            int[] r = (int[]) box[0];
            if (button == 0 && mouseX >= r[0] && mouseX < r[2] && mouseY >= r[1] && mouseY < r[3]) {
                ((Runnable) box[1]).run();
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                      SoundEvents.UI_BUTTON_CLICK.get(), 1.6F, 0.25F));
                return true;
            }
        }
        if (logisticsOpen) {
            //Clic hors de la fenetre : on la ferme
            int[] b = logisticsBounds();
            if (mouseX < b[0] || mouseX >= b[2] || mouseY < b[1] || mouseY >= b[3]) {
                logisticsOpen = false;
            }
            return true;
        }
        if (galaxy) {
            if (button == 1) {
                toggleGalaxy();
            } else if (galaxyHovered != null) {
                setSector(galaxyHovered);
            }
            return true;
        }
        if (overPanel((int) mouseX, (int) mouseY)) {
            return true;
        }
        if (button == 1 && focus != null) {
            back();
            return true;
        }
        if (button == 0) {
            pressed = true;
            dragged = false;
            pressX = mouseX;
            pressY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pressed && button == 0) {
            if (!dragged && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 4) {
                dragged = true;
            }
            if (dragged) {
                anchored = false;
                panX += (float) dragX;
                panY += (float) dragY;
                clampPan();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && pressed) {
            pressed = false;
            if (!dragged && hovered != null) {
                select(hovered);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void select(Body body) {
        if (!known(body)) {
            return;
        }
        if (focus == null && body.hasChildren()) {
            float[] pos = body == Body.ASTEROID_BELT ? null : positions.get(body);
            setFocus(body, pos == null ? (float) pressX : pos[0], pos == null ? (float) pressY : pos[1]);
            navSystem = StarSystem.of(body);
        } else if (body != Body.ASTEROID_BELT) {
            selected = body == selected ? null : body;
            if (body.dimension() != null && StarSystem.of(body) != null) {
                navSystem = StarSystem.of(body);
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_L) {
            logisticsOpen = !logisticsOpen;
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_G && !logisticsOpen) {
            toggleGalaxy();
            return true;
        }
        if (keyCode == 256 && galaxy) {
            toggleGalaxy();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_N && !logisticsOpen) {
            navCollapsed = !navCollapsed;
            return true;
        }
        if (keyCode == 256 && logisticsOpen) {
            logisticsOpen = false;
            return true;
        }
        if (logisticsOpen) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == 257 && selected != null && canTravel(selected)) {
            travel(selected);
            return true;
        }
        if (keyCode == 256 && selected != null) {
            selected = null;
            return true;
        }
        if (keyCode == 256 && focus != null) {
            back();
            return true;
        }
        if (keyCode == 256 && navSystem != null) {
            navSystem = null;
            return true;
        }
        if (keyCode == 256 && missionLocked()) {
            //Mission en cours : Echap ouvre le menu pause (la carte revient ensuite toute seule)
            Minecraft.getInstance().pauseGame(false);
            return true;
        }
        if (PiKeys.SOLAR_MAP.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------
    // Carte galactique
    // ------------------------------------------------------------------

    private float galaxyZoom = 1;
    @Nullable
    private Sector galaxyHovered;

    private void toggleGalaxy() {
        galaxy = !galaxy;
        galaxyHovered = null;
        selected = null;
    }

    /** Ouvre la carte d'un systeme stellaire. */
    private void setSector(Sector next) {
        if (!sectorKnown(next)) {
            return;
        }
        galaxy = false;
        if (next != sector) {
            sector = next;
            navSystem = null;
            navScroll = 0;
            selected = null;
            setFocus(null, width / 2F, height / 2F);
        }
    }

    /** Systeme reconnu par les sondes (le systeme solaire l'est toujours). */
    private boolean sectorKnown(Sector s) {
        Body origin = SolarSystem.origin(here, hereX);
        return s == Sector.SOL || ClientResearch.isDiscovered(s.root()) || origin != null && origin.sector() == s;
    }

    /**
     * Rayon d'affichage d'une distance (annees-lumiere) : echelle compressee (logarithmique) pour que les systemes proches ne
     * s'entassent pas autour du Soleil et que les lointains restent a l'ecran.
     */
    private float galaxyRadius(float lightYears) {
        float unit = Math.min(width, height) * 0.085F * galaxyZoom;
        return unit * (float) (Math.log(1 + lightYears) / Math.log(2));
    }

    /** Position d'un systeme sur la carte galactique (le Soleil un peu a gauche du centre ; direction reelle, distance compressee). */
    private float[] galaxyPos(Sector s) {
        float cx = width * 0.44F, cy = height * 0.56F;
        if (s == Sector.SOL) {
            return new float[]{cx, cy};
        }
        float len = Mth.sqrt(s.galacticX * s.galacticX + s.galacticY * s.galacticY);
        float r = galaxyRadius(s.lightYears());
        return new float[]{cx + s.galacticX / len * r, cy + s.galacticY / len * r};
    }

    /** Taille d'un systeme (mini planetaire) sur la carte. */
    private float systemSize() {
        return Mth.clamp(Math.min(width, height) * 0.06F * galaxyZoom, 16, 52);
    }

    private void drawGalaxy(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, height, 0xFF02030A);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(0.45F, 0.5F, 0.9F, 0.35F);
        graphics.blit(NEBULA, 0, 0, width, height, seconds * 0.6F, 0, 256, 128, 256, 128);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        graphics.flush();
        drawStars(graphics.pose().last().pose());
        float[] sun = galaxyPos(Sector.SOL);
        drawGalacticArm(graphics, sun);
        Matrix4f matrix = graphics.pose().last().pose();
        //Anneaux de distance, avec leur valeur
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        int[] rings = {5, 10, 20, 40};
        for (int ly : rings) {
            float radius = galaxyRadius(ly);
            int steps = 120;
            for (int i = 0; i < steps; i++) {
                if (i % 3 == 2) {
                    continue;
                }
                float a0 = i * Mth.TWO_PI / steps, a1 = (i + 1) * Mth.TWO_PI / steps;
                builder.vertex(matrix, sun[0] + Mth.cos(a0) * radius, sun[1] + Mth.sin(a0) * radius * 0.92F, 0).color(0.45F, 0.6F, 0.9F, 0.13F).endVertex();
                builder.vertex(matrix, sun[0] + Mth.cos(a1) * radius, sun[1] + Mth.sin(a1) * radius * 0.92F, 0).color(0.45F, 0.6F, 0.9F, 0.13F).endVertex();
            }
        }
        BufferUploader.drawWithShader(builder.end());
        for (int ly : rings) {
            float radius = galaxyRadius(ly);
            Component label = Component.translatable("map.pioneers.galaxy.ly", ly);
            smallText(graphics, label, sun[0] + radius * 0.77F + 2, sun[1] + radius * 0.6F - 2, 0x605A7898, 0.55F);
        }
        //Routes : arcs depuis le systeme solaire, parcourus de points lumineux
        for (Sector s : Sector.values()) {
            if (s != Sector.SOL) {
                drawRoute(graphics, sun, galaxyPos(s), s);
            }
        }
        //Systemes
        galaxyHovered = null;
        Body hereBody = SolarSystem.origin(here, hereX);
        float size = systemSize();
        for (Sector s : Sector.values()) {
            float[] p = galaxyPos(s);
            boolean known = sectorKnown(s);
            boolean hover = Math.hypot(mouseX - p[0], mouseY - p[1]) < size + 6;
            if (hover && known) {
                galaxyHovered = s;
            }
            drawMiniSystem(graphics, s, p, size, known, hover && known);
            if (hereBody != null && hereBody.sector() == s) {
                drawGalaxyRing(graphics, p, size + 4, 0x55CCFF);
            }
            if (hover && known) {
                drawGalaxyRing(graphics, p, size + 8, 0x77FF99);
            }
            drawSystemCard(graphics, s, p, size, known, hover && known);
        }
        RenderSystem.disableBlend();
    }

    /** Bras galactique : une bande diagonale d'etoiles faibles et de poussiere lumineuse, derriere les systemes. */
    private void drawGalacticArm(GuiGraphics graphics, float[] sun) {
        RandomSource random = RandomSource.create(11);
        float span = Math.max(width, height) * 0.9F;
        float angle = -0.42F, ca = Mth.cos(angle), sa = Mth.sin(angle);
        PoseStack pose = graphics.pose();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        for (int i = 0; i < 18; i++) {
            float t = (i / 17F - 0.5F) * span, off = Mth.sin(i * 1.7F) * span * 0.04F;
            float x = sun[0] + ca * t - sa * off, y = sun[1] + sa * t + ca * off;
            pose.pushPose();
            pose.translate(x, y, 0);
            RenderSystem.setShaderColor(0.42F, 0.36F, 0.7F, 0.10F);
            blitCentered(graphics, GLOW, span * 0.16F);
            pose.popPose();
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();
        graphics.flush();
        for (int i = 0; i < 420; i++) {
            float t = (random.nextFloat() - 0.5F) * span;
            float off = (float) random.nextGaussian() * span * 0.07F;
            int x = Math.round(sun[0] + ca * t - sa * off), y = Math.round(sun[1] + sa * t + ca * off);
            float tw = 0.55F + 0.45F * Mth.sin(seconds * (0.5F + random.nextFloat()) + i);
            int alpha = (int) (tw * (60 + random.nextInt(90)));
            float tint = random.nextFloat();
            int rgb = tint < 0.2F ? 0xFFC890 : tint < 0.5F ? 0xB8C8FF : 0xE8E8F4;
            graphics.fill(x, y, x + 1, y + 1, alpha << 24 | rgb);
        }
    }

    /** Arc d'une route (courbe de Bezier) ; des impulsions lumineuses le parcourent vers la destination. */
    private void drawRoute(GuiGraphics graphics, float[] a, float[] b, Sector s) {
        boolean known = sectorKnown(s);
        boolean reachable = tier != null && tier.level >= s.requiredTier;
        float mx = (a[0] + b[0]) / 2, my = (a[1] + b[1]) / 2;
        float dx = b[0] - a[0], dy = b[1] - a[1], len = Mth.sqrt(dx * dx + dy * dy);
        float cx = mx - dy / len * len * 0.18F, cy = my + dx / len * len * 0.18F;
        float cr = reachable ? 0.45F : 1F, cg = reachable ? 1F : 0.66F, cb = reachable ? 0.65F : 0.3F;
        float base = known ? 0.32F : 0.08F;
        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        int segments = 64;
        float size = systemSize();
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments, t1 = (i + 1) / (float) segments;
            float[] p0 = bezier(a, cx, cy, b, t0), p1 = bezier(a, cx, cy, b, t1);
            //On ne trace pas sous les systemes
            if (Math.hypot(p0[0] - a[0], p0[1] - a[1]) < size + 4 || Math.hypot(p0[0] - b[0], p0[1] - b[1]) < size + 4) {
                continue;
            }
            float pulse = known ? Math.max(0, 1 - Math.abs(((t0 - seconds * 0.12F) % 0.25F + 0.25F) % 0.25F - 0.125F) * 24) : 0;
            float alpha = base + pulse * 0.6F;
            builder.vertex(matrix, p0[0], p0[1], 0).color(cr, cg, cb, alpha).endVertex();
            builder.vertex(matrix, p1[0], p1[1], 0).color(cr, cg, cb, alpha).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());
    }

    /** Un systeme en miniature : son etoile (ou ses etoiles) et ses planetes sur leurs orbites. */
    private void drawMiniSystem(GuiGraphics graphics, Sector s, float[] p, float size, boolean known, boolean hover) {
        PoseStack pose = graphics.pose();
        List<Body> planets = new ArrayList<>();
        List<Body> stars = new ArrayList<>();
        for (Body body : Body.ALL) {
            if (body.sector() != s || body.isMoon() || body == Body.ASTEROID_BELT) {
                continue;
            }
            (body.isStar() ? stars : planets).add(body);
        }
        //Orbites
        if (known) {
            Matrix4f matrix = pose.last().pose();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            for (int k = 0; k < planets.size(); k++) {
                float radius = orbitRadius(k, planets.size(), size);
                int steps = 40;
                for (int i = 0; i < steps; i++) {
                    float a0 = i * Mth.TWO_PI / steps, a1 = (i + 1) * Mth.TWO_PI / steps;
                    builder.vertex(matrix, p[0] + Mth.cos(a0) * radius, p[1] + Mth.sin(a0) * radius * 0.45F, 0).color(0.6F, 0.7F, 0.9F, hover ? 0.3F : 0.16F).endVertex();
                    builder.vertex(matrix, p[0] + Mth.cos(a1) * radius, p[1] + Mth.sin(a1) * radius * 0.45F, 0).color(0.6F, 0.7F, 0.9F, hover ? 0.3F : 0.16F).endVertex();
                }
            }
            BufferUploader.drawWithShader(builder.end());
        }
        //Planetes derriere l'etoile (moitie haute de l'orbite), etoile(s), puis planetes devant
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) {
                drawStarsOf(graphics, s, stars, p, size, known);
            }
            if (!known) {
                continue;
            }
            for (int k = 0; k < planets.size(); k++) {
                float radius = orbitRadius(k, planets.size(), size);
                float speed = 0.6F / (float) Math.sqrt(1 + k);
                float angle = seconds * speed + k * 2.1F + s.ordinal();
                float y = Mth.sin(angle) * radius * 0.45F;
                if (y < 0 == (pass == 1)) {
                    continue;
                }
                pose.pushPose();
                pose.translate(p[0] + Mth.cos(angle) * radius, p[1] + y, 0);
                float half = Mth.clamp(size * (planets.get(k).isGasGiant() ? 0.13F : 0.09F), 1.6F, 5F);
                blitCentered(graphics, texture(planets.get(k)), half);
                pose.popPose();
            }
        }
        graphics.flush();
    }

    private static float orbitRadius(int index, int count, float size) {
        return size * (0.42F + 0.58F * (index + 1) / count);
    }

    private void drawStarsOf(GuiGraphics graphics, Sector s, List<Body> stars, float[] p, float size, boolean known) {
        PoseStack pose = graphics.pose();
        for (int i = 0; i < stars.size(); i++) {
            Body star = stars.get(i);
            float ox = 0, oy = 0, half = size * 0.22F;
            if (s == Sector.ALPHA_CENTAURI) {
                //Rigil Kentaurus et Toliman tournent l'une autour de l'autre ; Proxima, plus petite, a l'ecart
                if (star == Body.PROXIMA) {
                    ox = size * 0.95F;
                    oy = -size * 0.55F;
                    half = size * 0.11F;
                } else {
                    float a = seconds * 0.35F + (star == Body.CENTAURI_A ? 0 : Mth.PI);
                    float d = size * (star == Body.CENTAURI_A ? 0.12F : 0.2F);
                    ox = Mth.cos(a) * d;
                    oy = Mth.sin(a) * d * 0.45F;
                    half = size * (star == Body.CENTAURI_A ? 0.2F : 0.16F);
                }
            }
            int color = s.color;
            pose.pushPose();
            pose.translate(p[0] + ox, p[1] + oy, 0);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            float pulse = 1 + 0.06F * Mth.sin(seconds * 2 + i + s.ordinal());
            RenderSystem.setShaderColor(r(color), g(color), b(color), known ? 0.55F : 0.15F);
            blitCentered(graphics, GLOW, half * 4.2F * pulse);
            RenderSystem.defaultBlendFunc();
            if (!known) {
                RenderSystem.setShaderColor(0.12F, 0.12F, 0.16F, 1);
            }
            blitCentered(graphics, texture(star), half);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            pose.popPose();
        }
    }

    /** Carte du systeme sous la miniature : nom, distance et fusee requise, ou la recherche qui le revelera. */
    private void drawSystemCard(GuiGraphics graphics, Sector s, float[] p, float size, boolean known, boolean hover) {
        Component name = known ? Component.translatable(s.getTranslationKey()) : Component.literal("? ? ?");
        Component sub = s == Sector.SOL ? Component.translatable("map.pioneers.galaxy.home")
                                        : Component.translatable("map.pioneers.galaxy.distance", String.format("%.1f", s.lightYears()), s.requiredTier);
        Component hint = known ? null : Component.translatable("map.pioneers.galaxy.unknown", Component.translatable(
              s == Sector.ALPHA_CENTAURI ? ResearchNode.CENTAURI_SURVEY.getTranslationKey() : ResearchNode.TRAPPIST_SURVEY.getTranslationKey()));
        float subScale = 0.7F, hintScale = 0.6F;
        int w = Math.max(font.width(name), (int) (font.width(sub) * subScale));
        if (hint != null) {
            w = Math.max(w, (int) (font.width(hint) * hintScale));
        }
        w += 14;
        int h = hint != null ? 33 : 26;
        //Le systeme solaire porte sa fiche au-dessus (ses voisins sont en dessous et a droite), les autres en dessous
        int x0 = Math.round(p[0] - w / 2F);
        //Au-dessus pour le Soleil et tout systeme plus haut que lui (sur un petit ecran, les fiches se chevauchaient), sous la barre du haut
        boolean above = s == Sector.SOL || p[1] < galaxyPos(Sector.SOL)[1];
        int y0 = above ? Math.max(24, Math.round(p[1] - size * 0.55F - 8 - h)) : Math.round(p[1] + size * 0.55F + 8);
        graphics.fill(x0, y0, x0 + w, y0 + h, hover ? 0xD0142438 : 0xB00A121E);
        graphics.fill(x0, y0, x0 + w, y0 + 1, 0xFF000000 | (known ? s.color : 0x404858));
        graphics.fill(x0, y0 + h - 1, x0 + w, y0 + h, 0x40FFFFFF);
        graphics.drawCenteredString(font, name, Math.round(p[0]), y0 + 4, known ? 0xF0F4FF : 0x707884);
        int subColor = s == Sector.SOL ? 0x8FB8D8 : tier != null && tier.level >= s.requiredTier ? 0x77FF99 : 0xFFAA44;
        smallText(graphics, sub, p[0] - font.width(sub) * subScale / 2, y0 + 15, 0xFF000000 | subColor, subScale);
        if (hint != null) {
            smallText(graphics, hint, p[0] - font.width(hint) * hintScale / 2, y0 + 24, 0xFF6A7280, hintScale);
        }
    }

    private void drawGalaxyRing(GuiGraphics graphics, float[] p, float radius, int color) {
        graphics.flush();
        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        int segments = 32;
        for (int i = 0; i < segments; i++) {
            if (i % 4 == 3) {
                continue;
            }
            float a0 = i * Mth.TWO_PI / segments + seconds * 0.8F, a1 = (i + 1) * Mth.TWO_PI / segments + seconds * 0.8F;
            builder.vertex(matrix, p[0] + Mth.cos(a0) * radius, p[1] + Mth.sin(a0) * radius, 0).color(r(color), g(color), b(color), 0.9F).endVertex();
            builder.vertex(matrix, p[0] + Mth.cos(a1) * radius, p[1] + Mth.sin(a1) * radius, 0).color(r(color), g(color), b(color), 0.9F).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private void travel(Body body) {
        PiNetwork.sendToServer(new PacketSelectDestination(body));
        depart();
    }

    // ------------------------------------------------------------------
    // Mission en cours : la carte reste ouverte jusqu'au choix d'une destination (ou l'abandon)
    // ------------------------------------------------------------------

    /** Instant du dernier depart ou abandon (la carte ne se rouvre pas pendant que le serveur traite la demande). */
    public static long departedAt;
    private boolean departing;
    private float lockedFlash = -10, abortArmedAt = -10;

    /** Fusee en orbite avec le joueur a bord : il faut choisir une destination ou abandonner la mission. */
    private boolean missionLocked() {
        Minecraft minecraft = Minecraft.getInstance();
        return !departing && tier != null && minecraft.player != null && minecraft.player.getVehicle() instanceof RocketEntity rocket
               && rocket.getState() == RocketEntity.State.ORBIT;
    }

    private void depart() {
        departing = true;
        departedAt = System.currentTimeMillis();
        onClose();
    }

    @Override
    public void onClose() {
        if (missionLocked()) {
            lockedFlash = seconds;
            return;
        }
        super.onClose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    /** Bouton « Abandonner la mission » en bas a gauche (deux clics : armer, puis confirmer) et rappel quand on tente de fermer la carte. */
    private void drawAbort(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!missionLocked()) {
            return;
        }
        boolean armed = seconds - abortArmedAt < 3;
        Component label = Component.translatable(armed ? "map.pioneers.abort.confirm" : "map.pioneers.abort");
        int w = (int) (font.width(label) * 0.8F) + 14, x0 = 8, y1 = height - 4, y0 = y1 - 14;
        boolean hover = mouseX >= x0 && mouseX < x0 + w && mouseY >= y0 && mouseY < y1;
        graphics.fill(x0, y0, x0 + w, y1, armed ? 0xE0782020 : hover ? 0xD0502020 : 0xB0301818);
        graphics.fill(x0, y0, x0 + w, y0 + 1, armed ? 0xFFFF7060 : 0xFFB05040);
        smallText(graphics, label, x0 + 7, y0 + 4, 0xFFFFE0D8, 0.8F);
        addHitbox(x0, y0, x0 + w, y1, () -> {
            if (seconds - abortArmedAt < 3) {
                PiNetwork.sendToServer(new PacketAbortMission());
                depart();
            } else {
                abortArmedAt = seconds;
            }
        });
        if (seconds - lockedFlash < 2.5F) {
            float a = 1 - Math.max(0, seconds - lockedFlash - 1.5F);
            Component hint = Component.translatable("map.pioneers.locked");
            int alpha = (int) (a * 255) << 24;
            int tw = Math.min(width - 8, font.width(hint) + 16), tx = width / 2 - tw / 2, ty = height / 2 - 60;
            graphics.fill(tx, ty, tx + tw, ty + 16, (int) (a * 0xC0) << 24);
            GuiDraw.center(graphics, font, hint, width / 2F, ty + 4, 1F, alpha | 0xFFB070, tw - 8);
        }
    }
}

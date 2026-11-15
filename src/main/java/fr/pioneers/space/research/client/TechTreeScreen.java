package fr.pioneers.space.research.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.pioneers.Pioneers;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.core.data.DataUnits;
import fr.pioneers.core.network.PacketResearch;
import fr.pioneers.core.network.PiNetwork;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.research.BodyResearch;
import fr.pioneers.space.research.ProbeDestination;
import fr.pioneers.space.research.ResearchEffects;
import fr.pioneers.space.research.ResearchMilestones;
import fr.pioneers.space.research.ResearchNode;
import fr.pioneers.space.research.ResearchState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Arbre technologique : toile deplacable / zoomable organisee en chapitres (un par systeme), onglets de chapitres,
 * colonnes d'astres (orbite, surface, geologie, biologie) avec leur maitrise, liaisons animees (les liaisons « l'un ou l'autre »
 * en pointilles violets), fiche detaillee et file d'attente des recherches de laboratoire.
 */
public class TechTreeScreen extends Screen {

    private static final ResourceLocation NEBULA = Pioneers.rl("textures/gui/solar/nebula.png");
    private static final ResourceLocation GLOW = Pioneers.rl("textures/effect/glow.png");

    private static final float COL_W = 80, ROW_H = 44, NODE = 24;
    private static final float MIN_ZOOM = 0.5F, MAX_ZOOM = 2.6F;
    private static final int PANEL_W = 172, TOP = 38, BOTTOM = 34;
    private static final int PANEL_BG = 0xD00A0F18, PANEL_EDGE = 0x5590A8C8;
    private static final int GOLD = 0xFFD27A, CYAN = 0x55CCFF, DIM = 0x3A4656, VIOLET = 0xB58CFF;
    private static final int[] ERA_TINT = {0xC8C8D8, 0xE0583A, 0xF0B040, 0xA49884, 0xC07848, 0xE6CF8C, 0x6FE3E8, 0x4F62F5, 0xF09AC0, 0xB58CFF,
          0xE8A060, 0xFFD27A, 0xFF7A5A, 0x8FA8FF};
    private static final String[] NUMERALS = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV"};
    private static final int STARS = 300;

    /** Position dans la grille (colonne, ligne) de chaque recherche, centree sur la colonne 10. */
    private static final Map<ResearchNode, float[]> LAYOUT = new EnumMap<>(ResearchNode.class);
    private static final int MAX_COL;
    /** Premiere et derniere colonne de chaque ere. */
    private static final int[][] ERA_COLS = new int[ResearchNode.ERAS][];

    static {
        int max = 0;
        for (ResearchNode node : ResearchNode.values()) {
            max = Math.max(max, node.col);
        }
        MAX_COL = max;
        for (ResearchNode node : ResearchNode.values()) {
            LAYOUT.put(node, new float[]{colX(node.col), (node.row - 2.5F) * ROW_H});
            int[] range = ERA_COLS[node.era];
            ERA_COLS[node.era] = range == null ? new int[]{node.col, node.col} : new int[]{Math.min(range[0], node.col), Math.max(range[1], node.col)};
        }
    }

    private static float colX(float col) {
        return (col - MAX_COL / 2F) * COL_W;
    }

    /** Table de recherche d'ou l'arbre est ouvert ; null : simple consultation (touche du mod). */
    @Nullable
    private final BlockPos table;
    @Nullable
    private final Screen parent;
    private final long openedAt = System.nanoTime();
    private float seconds;

    private float zoom = 1, targetZoom = 1, panX, panY;
    private boolean pressed, dragged, pressedInWorld;
    private double pressX, pressY;
    private long lastClick;
    @Nullable
    private ResearchNode lastClicked;

    @Nullable
    private ResearchNode hovered, selected;
    private final List<Object[]> hitboxes = new ArrayList<>();
    /** 0.20.46 (audit) : nom de chaque noeud deja coupe en lignes, a la largeur de coupe de la derniere image (refait si le zoom change). */
    private final Map<ResearchNode, Label> labels = new EnumMap<>(ResearchNode.class);

    private record Label(int wrap, List<FormattedCharSequence> lines) {
    }

    private final float[] starX = new float[STARS], starY = new float[STARS], starPhase = new float[STARS], starSize = new float[STARS];

    public TechTreeScreen(@Nullable BlockPos table, @Nullable Screen parent) {
        super(Component.translatable("research.pioneers.tree.title"));
        this.table = table;
        this.parent = parent;
        RandomSource random = RandomSource.create(7);
        for (int i = 0; i < STARS; i++) {
            starX[i] = random.nextFloat();
            starY[i] = random.nextFloat();
            starPhase[i] = random.nextFloat() * Mth.TWO_PI;
            starSize[i] = random.nextFloat() < 0.1F ? 1.5F : 0.75F;
        }
        selected = ClientResearch.getActive();
        if (selected == null) {
            for (ResearchNode node : ResearchNode.values()) {
                if (ClientResearch.isAvailable(node)) {
                    selected = node;
                    break;
                }
            }
            if (selected == null) {
                selected = ResearchNode.TELEMETRY;
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        labels.clear();
        //Assez grand pour lire les noms ; l'arbre complet se parcourt en glissant
        float fit = Mth.clamp((viewRight() - 40) / (10 * COL_W), 0.8F, 1.4F);
        zoom = targetZoom = fit;
        //Centre la vue sur la recherche choisie
        if (selected != null) {
            float[] pos = LAYOUT.get(selected);
            panX = -pos[0] * zoom;
            panY = -pos[1] * zoom * 0.5F;
        }
        clampPan();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    // ------------------------------------------------------------------
    // Etat des recherches
    // ------------------------------------------------------------------

    private enum State {COMPLETED, ACTIVE, AVAILABLE, LOCKED}

    private static State state(ResearchNode node) {
        if (ClientResearch.isCompleted(node)) {
            return State.COMPLETED;
        }
        if (ClientResearch.getActive() == node) {
            return State.ACTIVE;
        }
        return ClientResearch.isAvailable(node) ? State.AVAILABLE : State.LOCKED;
    }

    private static int stateColor(State state) {
        return switch (state) {
            case COMPLETED -> GOLD;
            case ACTIVE, AVAILABLE -> CYAN;
            case LOCKED -> 0x6A7686;
        };
    }

    /** Couleur propre a chaque type de recherche par echantillons. */
    private static int kindColor(ResearchNode.Kind kind) {
        return switch (kind) {
            case SURFACE -> 0x5BE0B0;
            case GEOLOGY -> 0xE8A458;
            case BIOLOGY -> 0x8EE070;
            case ASTEROIDS -> 0xB8A6F0;
            case LAB -> CYAN;
        };
    }

    /** Couleur de cadre d'une recherche : or si acquise, couleur du type sinon. */
    private static int nodeColor(ResearchNode node, State state) {
        if (state == State.COMPLETED || state == State.LOCKED || node.isLab()) {
            return stateColor(state);
        }
        return kindColor(node.kind);
    }

    private static boolean plannable(ResearchNode node) {
        if (!node.isLab() || ClientResearch.isCompleted(node) || ClientResearch.getActive() == node || ClientResearch.getQueue().contains(node)) {
            return false;
        }
        return node.prerequisitesMet(prerequisite -> ClientResearch.isCompleted(prerequisite) || ClientResearch.getActive() == prerequisite
                                                     || ClientResearch.getQueue().contains(prerequisite))
               && ClientResearch.milestonesMet(node) && ClientResearch.getQueue().size() < ResearchState.MAX_QUEUE;
    }

    // ------------------------------------------------------------------
    // Geometrie
    // ------------------------------------------------------------------

    private float viewRight() {
        return width - PANEL_W;
    }

    private float centerX() {
        return viewRight() / 2F + panX;
    }

    private float centerY() {
        //Decale sous les titres des eres
        return (TOP + 20 + height - BOTTOM) / 2F + panY;
    }

    private float sx(float wx) {
        return centerX() + wx * zoom;
    }

    private float sy(float wy) {
        return centerY() + wy * zoom;
    }

    private void clampPan() {
        float halfW = (MAX_COL / 2F + 0.7F) * COL_W * zoom, halfH = 2.9F * ROW_H * zoom;
        float limitX = Math.max(0, halfW - viewRight() / 2F + 30), limitY = Math.max(0, halfH - (height - TOP - BOTTOM) / 2F + 30);
        panX = Mth.clamp(panX, -limitX, limitX);
        panY = Mth.clamp(panY, -limitY, limitY);
    }

    private boolean inWorld(double mouseX, double mouseY) {
        return mouseX < viewRight() && mouseY > TOP && mouseY < height - BOTTOM;
    }

    @Nullable
    private ResearchNode nodeAt(double mouseX, double mouseY) {
        if (!inWorld(mouseX, mouseY)) {
            return null;
        }
        float half = NODE / 2F * zoom + 2;
        for (ResearchNode node : ResearchNode.values()) {
            float[] pos = LAYOUT.get(node);
            if (Math.abs(mouseX - sx(pos[0])) <= half && Math.abs(mouseY - sy(pos[1])) <= half) {
                return node;
            }
        }
        return null;
    }

    /** Colonnes des astres (orbite, surface, geologie, biologie empilees). */
    private static final Set<Integer> BODY_COLS = new HashSet<>();

    static {
        for (ResearchNode node : ResearchNode.values()) {
            if (node.isOrbit()) {
                BODY_COLS.add(node.col);
            }
        }
    }

    /** Ligne au-dessus des orbites (bus de decouverte) et canal sous les biologies (liaisons qui traversent des astres). */
    private static final float TOP_BUS = (1 - 2.5F) * ROW_H - 20, BOTTOM_BUS = (4 - 2.5F) * ROW_H + 38;

    /** Liaison structurelle (colonne d'astre, bus) : toujours bien visible ; les autres s'estompent hors selection. */
    private static boolean structural(ResearchNode from, ResearchNode to) {
        return from.col == to.col || to.isOrbit() || from.kind == ResearchNode.Kind.BIOLOGY;
    }

    /** Traces precalcules de toutes les liaisons (allees et canaux repartis pour que les traits ne se superposent pas). */
    private static final Map<ResearchNode, Map<ResearchNode, float[]>> PATHS = new EnumMap<>(ResearchNode.class);
    /** Ecart entre deux traits voisins dans une allee ou un canal. */
    private static final float LANE_GAP = 3;

    private enum Route {VERTICAL, DISCOVERY, CHANNEL, ELBOW, STRAIGHT}

    private static Route route(ResearchNode from, ResearchNode to) {
        if (from.col == to.col && to.row > from.row) {
            return Route.VERTICAL;
        }
        if (to.isOrbit() && to.col > from.col && !from.kind.isSample()) {
            return Route.DISCOVERY;
        }
        for (int col : BODY_COLS) {
            if (from.col < col && col < to.col) {
                return Route.CHANNEL;
            }
        }
        if (from.kind == ResearchNode.Kind.BIOLOGY && to.col > from.col + 1) {
            return Route.CHANNEL;
        }
        return from.row == to.row ? Route.STRAIGHT : Route.ELBOW;
    }

    /** Decalages centres (0, -g, +g...) pour {@code count} traits. */
    private static float spread(int index, int count) {
        return Mth.clamp((index - (count - 1) / 2F) * LANE_GAP, -9, 9);
    }

    static {
        List<ResearchNode[]> edges = new ArrayList<>();
        for (ResearchNode node : ResearchNode.values()) {
            for (ResearchNode prerequisite : node.getPrerequisites()) {
                edges.add(new ResearchNode[]{prerequisite, node});
            }
        }
        //Allees d'arrivee (avant la colonne de la recherche) : un trait vertical par liaison, ranges par ligne
        Map<Integer, List<ResearchNode[]>> inLanes = new HashMap<>(), outLanes = new HashMap<>();
        List<ResearchNode[]> channel = new ArrayList<>();
        for (ResearchNode[] edge : edges) {
            Route r = route(edge[0], edge[1]);
            if (r == Route.ELBOW || r == Route.CHANNEL) {
                inLanes.computeIfAbsent(edge[1].col, c -> new ArrayList<>()).add(edge);
            }
            if (r == Route.CHANNEL) {
                outLanes.computeIfAbsent(edge[0].col, c -> new ArrayList<>()).add(edge);
                channel.add(edge);
            }
        }
        Map<ResearchNode[], Float> inOffset = new IdentityHashMap<>(), outOffset = new IdentityHashMap<>(), busY = new IdentityHashMap<>();
        //Allees d'arrivee : une par recherche cible (les liaisons vers une meme recherche fusionnent)
        for (List<ResearchNode[]> lane : inLanes.values()) {
            List<ResearchNode> targets = lane.stream().map(e -> e[1]).distinct().sorted(Comparator.comparingInt(n -> n.row)).toList();
            for (ResearchNode[] edge : lane) {
                inOffset.put(edge, spread(targets.indexOf(edge[1]), targets.size()));
            }
        }
        //Allees de depart et voies du canal : une par recherche source (un tronc commun qui se ramifie)
        for (List<ResearchNode[]> lane : outLanes.values()) {
            List<ResearchNode> sources = lane.stream().map(e -> e[0]).distinct().sorted(Comparator.comparingInt(n -> n.row)).toList();
            for (ResearchNode[] edge : lane) {
                outOffset.put(edge, spread(sources.indexOf(edge[0]), sources.size()));
            }
        }
        Map<ResearchNode, float[]> spans = new EnumMap<>(ResearchNode.class);
        for (ResearchNode[] edge : channel) {
            float start = LAYOUT.get(edge[0])[0], end = LAYOUT.get(edge[1])[0];
            spans.merge(edge[0], new float[]{start, end}, (x, y) -> new float[]{Math.min(x[0], y[0]), Math.max(x[1], y[1])});
        }
        List<Map.Entry<ResearchNode, float[]>> ordered = new ArrayList<>(spans.entrySet());
        ordered.sort(Comparator.comparingDouble(e -> e.getValue()[0]));
        List<Float> trackEnds = new ArrayList<>();
        Map<ResearchNode, Integer> tracks = new EnumMap<>(ResearchNode.class);
        for (Map.Entry<ResearchNode, float[]> entry : ordered) {
            int track = 0;
            while (track < trackEnds.size() && trackEnds.get(track) >= entry.getValue()[0] - 2) {
                track++;
            }
            if (track == trackEnds.size()) {
                trackEnds.add(entry.getValue()[1]);
            } else {
                trackEnds.set(track, entry.getValue()[1]);
            }
            tracks.put(entry.getKey(), track);
        }
        for (ResearchNode[] edge : channel) {
            busY.put(edge, BOTTOM_BUS + Math.min(tracks.get(edge[0]), 6) * LANE_GAP);
        }
        float half = NODE / 2F;
        for (ResearchNode[] edge : edges) {
            ResearchNode from = edge[0], to = edge[1];
            float[] a = LAYOUT.get(from), b = LAYOUT.get(to);
            float[] path = switch (route(from, to)) {
                case VERTICAL -> new float[]{a[0], a[1] + half, b[0], b[1] - half};
                case DISCOVERY -> {
                    float lane = a[0] + COL_W / 2F;
                    yield new float[]{a[0] + half, a[1], lane, a[1], lane, TOP_BUS, b[0], TOP_BUS, b[0], b[1] - half};
                }
                case CHANNEL -> {
                    float out = a[0] + COL_W / 2F - 4 + outOffset.get(edge), in = b[0] - COL_W / 2F + 4 + inOffset.get(edge), bus = busY.get(edge);
                    yield new float[]{a[0] + half, a[1], out, a[1], out, bus, in, bus, in, b[1], b[0] - half, b[1]};
                }
                case STRAIGHT -> new float[]{a[0] + half, a[1], b[0] - half, b[1]};
                case ELBOW -> {
                    float lane = b[0] - COL_W / 2F + inOffset.get(edge);
                    yield new float[]{a[0] + half, a[1], lane, a[1], lane, b[1], b[0] - half, b[1]};
                }
            };
            PATHS.computeIfAbsent(to, n -> new EnumMap<>(ResearchNode.class)).put(from, path);
        }
    }

    /** Trace d'une liaison : prerequis -> recherche (voir {@link #PATHS}). */
    private static float[] edgePath(ResearchNode from, ResearchNode to) {
        Map<ResearchNode, float[]> map = PATHS.get(to);
        float[] path = map == null ? null : map.get(from);
        if (path == null) {
            float[] a = LAYOUT.get(from), b = LAYOUT.get(to);
            return new float[]{a[0], a[1], b[0], b[1]};
        }
        return path;
    }

    // ------------------------------------------------------------------
    // Rendu
    // ------------------------------------------------------------------

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        seconds = (System.nanoTime() - openedAt) / 1.0E9F;
        float before = zoom;
        zoom += (targetZoom - zoom) * 0.25F;
        if (Math.abs(targetZoom - zoom) < 0.001F) {
            zoom = targetZoom;
        }
        if (before != zoom) {
            //Zoom centre sur le curseur
            float ax = anchorX, ay = anchorY;
            panX = (ax - viewRight() / 2F) - (ax - viewRight() / 2F - panX) * zoom / before;
            panY = (ay - (TOP + 20 + height - BOTTOM) / 2F) - (ay - (TOP + 20 + height - BOTTOM) / 2F - panY) * zoom / before;
            clampPan();
        }
        hovered = pressed && dragged ? null : nodeAt(mouseX, mouseY);

        drawBackground(graphics);
        graphics.enableScissor(0, TOP, (int) viewRight(), height - BOTTOM);
        drawEras(graphics);
        drawEdges(graphics);
        drawNodes(graphics);
        graphics.disableScissor();

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 400);
        hitboxes.clear();
        drawTopBar(graphics, mouseX, mouseY);
        drawBottomBar(graphics, mouseX, mouseY);
        drawSidePanel(graphics, mouseX, mouseY);
        if (hovered != null) {
            drawTooltip(graphics, hovered, mouseX, mouseY);
        }
        pose.popPose();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xFF020309);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(0.75F, 0.8F, 1, 0.7F);
        graphics.blit(NEBULA, 0, 0, width, height, seconds * 1.5F - panX * 0.04F, 40 - panY * 0.04F, 256, 128, 256, 128);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        graphics.flush();
        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < STARS; i++) {
            float x = Mth.positiveModulo(starX[i] * width + panX * 0.1F, (float) width);
            float y = Mth.positiveModulo(starY[i] * height + panY * 0.1F, (float) height);
            float alpha = 0.3F + 0.6F * (0.5F + 0.5F * Mth.sin(seconds * 1.3F + starPhase[i]));
            quad(builder, matrix, x, y, starSize[i], starSize[i], 0xFFFFFF, alpha);
        }
        BufferUploader.drawWithShader(builder.end());
    }

    /** Bandes verticales des cinq eres, avec leur titre en haut. */
    private void drawEras(GuiGraphics graphics) {
        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int era = 0; era < ResearchNode.ERAS; era++) {
            float x0 = sx(colX(ERA_COLS[era][0] - 0.5F)), x1 = sx(colX(ERA_COLS[era][1] + 0.5F));
            boolean reached = eraReached(era);
            quad(builder, matrix, x0, TOP, x1 - x0, height - TOP - BOTTOM, ERA_TINT[era], reached ? 0.07F : 0.025F);
            quad(builder, matrix, x1 - 0.5F, TOP, 1, height - TOP - BOTTOM, 0x90A8C8, 0.12F);
        }
        BufferUploader.drawWithShader(builder.end());
        for (int era = 0; era < ResearchNode.ERAS; era++) {
            float center = sx(colX((ERA_COLS[era][0] + ERA_COLS[era][1]) / 2F));
            boolean reached = eraReached(era);
            int color = reached ? ERA_TINT[era] : 0x56606C;
            String numeral = NUMERALS[era];
            Component header = Component.translatable("research.pioneers.tree.era", numeral);
            smallCentered(graphics, header, center, TOP + 5, 0xFF000000 | color, 0.75F);
            smallCentered(graphics, Component.translatable(ResearchNode.getEraKey(era)), center, TOP + 14, reached ? 0xFFB8C4D4 : 0xFF4C5662, 0.6F);
        }
        drawBodyColumns(graphics);
    }

    /** Colonne de chaque astre : bandeau discret, nom et maitrise (4 paliers). */
    private void drawBodyColumns(GuiGraphics graphics) {
        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (List<Body> chapter : BodyResearch.CHAPTERS) {
            for (Body body : chapter) {
                float[] top = LAYOUT.get(BodyResearch.node(body, ResearchNode.Kind.LAB));
                float x0 = sx(top[0] - COL_W * 0.42F), x1 = sx(top[0] + COL_W * 0.42F);
                float y0 = sy(top[1] - ROW_H * 0.95F), y1 = sy(top[1] + ROW_H * 3.62F);
                boolean known = ClientResearch.isDiscovered(body);
                quad(builder, matrix, x0, y0, x1 - x0, y1 - y0, body.color, known ? 0.07F : 0.025F);
                quad(builder, matrix, x0, y0, x1 - x0, Math.max(1, zoom), body.color, known ? 0.45F : 0.12F);
            }
        }
        BufferUploader.drawWithShader(builder.end());
        for (List<Body> chapter : BodyResearch.CHAPTERS) {
            for (Body body : chapter) {
                float[] top = LAYOUT.get(BodyResearch.node(body, ResearchNode.Kind.LAB));
                boolean known = ClientResearch.isDiscovered(body);
                float x = sx(top[0]), y = sy(top[1] - ROW_H * 0.86F);
                float scale = Mth.clamp(0.7F * zoom, 0.55F, 1.1F);
                smallCentered(graphics, Component.translatable(body.getTranslationKey()), x, y, known ? 0xFF000000 | body.color : 0xFF4C5662, scale);
                int mastery = ClientResearch.mastery(body);
                float pip = Math.max(3, 3.5F * zoom), gap = pip * 0.6F, total = 4 * pip + 3 * gap;
                for (int k = 0; k < 4; k++) {
                    float px = x - total / 2 + k * (pip + gap), py = y + 9 * scale + 1;
                    fillF(graphics, px, py, px + pip, py + pip * 0.6F, k < mastery ? 0xFFD2A040 : 0xFF2A3440);
                }
            }
        }
    }

    private static boolean eraReached(int era) {
        for (ResearchNode node : ResearchNode.values()) {
            if (node.era == era && (ClientResearch.isCompleted(node) || ClientResearch.isAvailable(node))) {
                return true;
            }
        }
        return false;
    }

    private void drawEdges(GuiGraphics graphics) {
        graphics.flush();
        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float thickness = Math.max(1, 1.6F * zoom);
        //Du plus terne au plus vif : les liaisons actives passent par-dessus
        for (int pass = 0; pass < 3; pass++) {
            for (ResearchNode node : ResearchNode.values()) {
                for (List<ResearchNode> group : node.getPrerequisiteGroups()) {
                    boolean anyOf = group.size() > 1;
                    boolean satisfied = group.stream().anyMatch(ClientResearch::isCompleted);
                    for (ResearchNode prerequisite : group) {
                        int level = edgeLevel(prerequisite, node);
                        if (level != pass) {
                            continue;
                        }
                        //Groupe deja satisfait par un autre astre : la liaison s'efface
                        if (anyOf && satisfied && !ClientResearch.isCompleted(prerequisite)) {
                            continue;
                        }
                        int color = level == 2 ? GOLD : level == 1 ? CYAN : anyOf ? VIOLET : DIM;
                        float alpha = level == 0 ? (anyOf ? 0.55F : 0.8F) : level == 1 ? 0.55F + 0.3F * Mth.sin(seconds * 3) : 0.9F;
                        //Liaisons transversales : discretes, sauf autour de la recherche choisie ou survolee
                        boolean focused = node == selected || node == hovered || prerequisite == selected || prerequisite == hovered;
                        if (!structural(prerequisite, node) && !focused) {
                            alpha *= 0.35F;
                        } else if (focused && !structural(prerequisite, node)) {
                            alpha = Math.min(1, alpha + 0.2F);
                        }
                        float[] path = edgePath(prerequisite, node);
                        float[] screen = new float[path.length];
                        for (int i = 0; i < path.length; i += 2) {
                            screen[i] = sx(path[i]);
                            screen[i + 1] = sy(path[i + 1]);
                        }
                        polyline(builder, matrix, screen, thickness, color, alpha, anyOf && level < 2);
                        arrow(builder, matrix, screen, thickness, color, Math.min(1, alpha + 0.15F));
                    }
                }
            }
        }
        //Impulsions qui courent vers la recherche en cours
        ResearchNode active = ClientResearch.getActive();
        if (active != null) {
            for (ResearchNode prerequisite : active.getPrerequisites()) {
                float[] path = edgePath(prerequisite, active);
                for (int k = 0; k < 3; k++) {
                    float[] point = along(path, (seconds * 0.45F + k / 3F) % 1F);
                    float s = 2.4F * Math.max(1, zoom);
                    quad(builder, matrix, sx(point[0]) - s / 2, sy(point[1]) - s / 2, s, s, 0xE8FFFF, 0.95F);
                    quad(builder, matrix, sx(point[0]) - s, sy(point[1]) - s, s * 2, s * 2, CYAN, 0.25F);
                }
            }
        }
        BufferUploader.drawWithShader(builder.end());
    }

    /** 0 = verrouillee, 1 = accessible, 2 = acquise. */
    private static int edgeLevel(ResearchNode from, ResearchNode to) {
        if (ClientResearch.isCompleted(to)) {
            return 2;
        }
        return ClientResearch.isCompleted(from) ? 1 : 0;
    }

    private static float[] along(float[] path, float t) {
        float total = 0;
        for (int i = 0; i + 3 < path.length; i += 2) {
            total += Math.abs(path[i + 2] - path[i]) + Math.abs(path[i + 3] - path[i + 1]);
        }
        float target = t * total;
        for (int i = 0; i + 3 < path.length; i += 2) {
            float length = Math.abs(path[i + 2] - path[i]) + Math.abs(path[i + 3] - path[i + 1]);
            if (target <= length && length > 0) {
                float f = target / length;
                return new float[]{Mth.lerp(f, path[i], path[i + 2]), Mth.lerp(f, path[i + 1], path[i + 3])};
            }
            target -= length;
        }
        return new float[]{path[path.length - 2], path[path.length - 1]};
    }

    private void drawNodes(GuiGraphics graphics) {
        PoseStack pose = graphics.pose();
        List<ResearchNode> queue = ClientResearch.getQueue();
        //Halos (additifs)
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        for (ResearchNode node : ResearchNode.values()) {
            State state = state(node);
            if (state == State.LOCKED) {
                continue;
            }
            float[] pos = LAYOUT.get(node);
            if (offView(sx(pos[0]), sy(pos[1]))) {
                continue;
            }
            float pulse = state == State.AVAILABLE ? 0.25F + 0.2F * Mth.sin(seconds * 3 + node.ordinal()) : state == State.ACTIVE ? 0.55F : 0.22F;
            if (node == selected) {
                pulse += 0.2F;
            }
            int color = state == State.COMPLETED ? GOLD : nodeColor(node, state);
            RenderSystem.setShaderColor(r(color), g(color), b(color), pulse);
            float size = NODE * zoom * (state == State.ACTIVE ? 2.6F : 2.0F);
            pose.pushPose();
            pose.translate(sx(pos[0]), sy(pos[1]), 0);
            pose.scale(size / 64F, size / 64F, 1);
            graphics.blit(GLOW, -32, -32, 64, 64, 0, 0, 64, 64, 64, 64);
            pose.popPose();
        }
        graphics.flush();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();

        for (ResearchNode node : ResearchNode.values()) {
            float[] pos = LAYOUT.get(node);
            float x = sx(pos[0]), y = sy(pos[1]);
            if (offView(x, y)) {
                continue;
            }
            State state = state(node);
            float grow = node == hovered ? 1.08F : 1;
            float half = NODE / 2F * zoom * grow;
            int border = nodeColor(node, state);
            if (node == selected) {
                border = 0xFFFFFF;
            }
            pose.pushPose();
            pose.translate(x, y, 0);
            //Cadre octogonal (laboratoire) ou arrondi (echantillons)
            float c = (node.isLab() ? 4 : 7.5F) * zoom;
            fillF(graphics, -half - 1, -half - 1 + c, half + 1, half + 1 - c, 0xFF000000 | border);
            fillF(graphics, -half - 1 + c, -half - 1, half + 1 - c, half + 1, 0xFF000000 | border);
            int inner = switch (state) {
                case COMPLETED -> 0xFF2A2210;
                case ACTIVE -> 0xFF0C2A3A;
                case AVAILABLE -> node.isLab() ? 0xFF0E1C28 : 0xFF000000 | mix(0x0E1418, kindColor(node.kind), 0.18F);
                case LOCKED -> 0xFF0B0E13;
            };
            fillF(graphics, -half, -half + c, half, half - c, inner);
            fillF(graphics, -half + c, -half, half - c, half, inner);
            //Reflet
            fillF(graphics, -half + c, -half, half - c, -half + 1.2F * zoom, 0x30FFFFFF);
            //Icone
            float k = zoom * grow;
            pose.pushPose();
            pose.scale(k, k, 1);
            graphics.renderItem(node.getIcon(), -8, -8);
            pose.popPose();
            pose.translate(0, 0, 250);
            if (state == State.LOCKED) {
                fillF(graphics, -half, -half, half, half, 0xA0070A10);
            }
            //Badges
            float badge = Math.max(5, 5.5F * zoom);
            if (state == State.COMPLETED) {
                fillF(graphics, half - badge * 0.9F, -half - badge * 0.3F, half + badge * 0.6F, -half + badge * 1.2F, 0xFFD27A00 | 0xFF000000);
                smallCentered(graphics, Component.literal("✔"), half - badge * 0.15F, -half + badge * 0.05F, 0xFF2A1C00, badge / 9F);
            }
            int queueIndex = queue.indexOf(node);
            if (queueIndex >= 0) {
                fillF(graphics, half - badge * 0.9F, -half - badge * 0.3F, half + badge * 0.6F, -half + badge * 1.2F, 0xFF1A6890);
                smallCentered(graphics, Component.literal(String.valueOf(queueIndex + 1)), half - badge * 0.15F, -half + badge * 0.05F, 0xFFFFFFFF, badge / 9F);
            }
            if (node.isDiscovery()) {
                smallCentered(graphics, Component.literal("★"), -half + badge * 0.2F, -half - badge * 0.2F, 0xFF000000 | VIOLET, badge / 8F);
            }
            if (node.getBlueprint() != null) {
                fillF(graphics, -half - badge * 0.4F, half - badge * 0.9F, -half + badge * 1.0F, half + badge * 0.4F, 0xFF1E4C9A);
                smallCentered(graphics, Component.literal("P"), -half + badge * 0.3F, half - badge * 0.6F, 0xFFD8E8FF, badge / 9F);
            }
            pose.popPose();
            if (state == State.ACTIVE) {
                drawProgressRing(graphics, x, y, half + 3.5F * zoom, ClientResearch.getProgress(node) / (float) node.units, CYAN);
            } else if (!node.isLab() && state == State.AVAILABLE && ClientResearch.getFraction(node) > 0) {
                drawProgressRing(graphics, x, y, half + 3.5F * zoom, (float) ClientResearch.getFraction(node), kindColor(node.kind));
            }
            //Nom sous le noeud
            float labelScale = Mth.clamp(0.55F * zoom, 0.5F, 1F);
            int labelColor = switch (state) {
                case COMPLETED -> 0xFFF0D8A0;
                case ACTIVE, AVAILABLE -> 0xFFD8F0FF;
                case LOCKED -> 0xFF667280;
            };
            List<FormattedCharSequence> lines = labelLines(node, (int) (COL_W * 0.95F * zoom / labelScale));
            float ly = y + half + 3 * zoom;
            for (FormattedCharSequence line : lines) {
                pose.pushPose();
                pose.translate(x, ly, 250);
                pose.scale(labelScale, labelScale, 1);
                int lw = font.width(line);
                graphics.fill(-lw / 2 - 2, -1, lw / 2 + 2, 9, 0xB0040608);
                graphics.drawString(font, line, -lw / 2, 0, labelColor, true);
                pose.popPose();
                ly += 9 * labelScale;
            }
        }
    }

    /**
     * 0.20.46 (audit) : noeud entierement hors de la zone visible de l'arbre (halo, cadre, badges, anneau et nom compris, avec de la marge) :
     * on ne le dessine pas (184 noeuds, icone et nom chacun, a chaque image avant).
     */
    private boolean offView(float x, float y) {
        float side = Math.max(1.3F * NODE, 0.6F * COL_W) * zoom + 16;
        float above = 1.3F * NODE * zoom + 16, below = 1.3F * NODE * zoom + 96;
        return x + side < 0 || x - side > viewRight() || y + below < TOP || y - above > height - BOTTOM;
    }

    /** Nom du noeud coupe a la largeur {@code wrap} (en cache tant que la largeur ne change pas). */
    private List<FormattedCharSequence> labelLines(ResearchNode node, int wrap) {
        Label cached = labels.get(node);
        if (cached == null || cached.wrap() != wrap) {
            Component label = node.getBody() != null ? Component.translatable("map.pioneers.mastery." + switch (node.kind) {
                case SURFACE -> "surface";
                case GEOLOGY -> "geology";
                case BIOLOGY -> "biology";
                default -> "orbit";
            }) : Component.translatable(node.getTranslationKey());
            cached = new Label(wrap, font.split(label, wrap));
            labels.put(node, cached);
        }
        return cached.lines();
    }

    private void drawProgressRing(GuiGraphics graphics, float x, float y, float radius, float progress, int fill) {
        graphics.flush();
        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 48;
        float width = Math.max(1.2F, 2 * zoom);
        for (int i = 0; i < segments; i++) {
            float a0 = -Mth.HALF_PI + i * Mth.TWO_PI / segments, a1 = -Mth.HALF_PI + (i + 1) * Mth.TWO_PI / segments;
            boolean done = i < progress * segments;
            int color = done ? fill : 0x1C2C3C;
            ring(builder, matrix, x, y, radius, width, a0, a1, color, done ? 1 : 0.8F);
        }
        //Etincelle qui tourne
        float spark = -Mth.HALF_PI + seconds * 2.2F;
        float s = 2.5F * Math.max(1, zoom);
        quad(builder, matrix, x + Mth.cos(spark) * radius - s / 2, y + Mth.sin(spark) * radius - s / 2, s, s, 0xFFFFFF, 0.9F);
        BufferUploader.drawWithShader(builder.end());
    }

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    private void drawTopBar(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fillGradient(0, 0, width, TOP, 0xE0000000, 0x90000000);
        graphics.fill(0, TOP - 1, width, TOP, PANEL_EDGE);
        graphics.drawString(font, title, 10, 8, 0xFFE8F0FF, true);
        int done = ClientResearch.completedCount(), total = ResearchNode.values().length;
        Component count = Component.translatable("research.pioneers.tree.completed", done, total);
        int barX = 14 + font.width(title) + 8;
        smallText(graphics, count, barX, 6, 0xFFF0D8A0, 0.7F);
        float fraction = done / (float) total;
        graphics.fill(barX, 15, barX + 80, 18, 0xFF141C26);
        graphics.fill(barX, 15, barX + (int) (80 * fraction), 18, 0xFFD2A040);
        int labs = ClientResearch.getLabs();
        Component labsText = labs > 0 ? Component.translatable("research.pioneers.tree.labs", labs) : Component.translatable("research.pioneers.tree.no_labs");
        int lw = (int) (font.width(labsText) * 0.7F);
        smallText(graphics, Component.literal(labs > 0 ? "● " : "○ ").append(labsText), width - lw - 20, 9, labs > 0 ? 0xFF55FF88 : 0xFFFF7755, 0.7F);
        drawChapterTabs(graphics, mouseX, mouseY);
    }

    /** Onglets des chapitres : un clic centre la toile sur le chapitre. */
    /** Onglets visibles a la fois (les autres se font defiler avec les fleches). */
    private static final int VISIBLE_TABS = 5;
    private int tabStart = -1, lastCurrent = -1;

    private void drawChapterTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int y0 = 23, h = 13, arrow = 12, gap = 2;
        int x0 = 6 + arrow + gap, x1 = width - 6 - arrow - gap;
        int visible = Math.min(VISIBLE_TABS, ResearchNode.ERAS);
        int tabW = (x1 - x0 - gap * (visible - 1)) / visible;
        float viewCenter = -panX / zoom;
        int current = 0;
        float best = Float.MAX_VALUE;
        for (int era = 0; era < ResearchNode.ERAS; era++) {
            float center = colX((ERA_COLS[era][0] + ERA_COLS[era][1]) / 2F);
            if (Math.abs(center - viewCenter) < best) {
                best = Math.abs(center - viewCenter);
                current = era;
            }
        }
        //Le chapitre affiche reste dans la fenetre d'onglets quand on se deplace dans l'arbre
        if (tabStart < 0 || current != lastCurrent) {
            if (tabStart < 0 || current < tabStart || current >= tabStart + visible) {
                tabStart = current < tabStart || tabStart < 0 ? Math.max(0, current - visible / 2) : current - visible + 1;
            }
            lastCurrent = current;
        }
        tabStart = Mth.clamp(tabStart, 0, ResearchNode.ERAS - visible);
        //Fleches de defilement
        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            int ax = left ? 6 : width - 6 - arrow;
            boolean enabled = left ? tabStart > 0 : tabStart + visible < ResearchNode.ERAS;
            boolean hover = enabled && mouseX >= ax && mouseX < ax + arrow && mouseY >= y0 && mouseY < y0 + h;
            graphics.fill(ax, y0, ax + arrow, y0 + h, hover ? 0x60FFFFFF : 0x50101820);
            smallText(graphics, Component.literal(left ? "◀" : "▶"), ax + 3, y0 + 3, enabled ? 0xFFE0E8F0 : 0xFF3C4450, 0.75F);
            if (enabled) {
                int step = left ? -1 : 1;
                addHitbox(ax, y0, ax + arrow, y0 + h, () -> tabStart = Mth.clamp(tabStart + step, 0, ResearchNode.ERAS - visible));
            }
        }
        for (int era = tabStart; era < tabStart + visible; era++) {
            int x = x0 + (era - tabStart) * (tabW + gap);
            boolean reached = eraReached(era), here = era == current;
            boolean hover = mouseX >= x && mouseX < x + tabW && mouseY >= y0 && mouseY < y0 + h;
            int tint = ERA_TINT[era];
            graphics.fill(x, y0, x + tabW, y0 + h, here ? (tint & 0xFFFFFF) | 0x50000000 : hover ? 0x40FFFFFF : 0x50101820);
            graphics.fill(x, y0 + h - 1, x + tabW, y0 + h, here ? 0xFF000000 | tint : reached ? (tint & 0xFFFFFF) | 0x80000000 : 0x40606C78);
            boolean mastered = chapterMastered(era);
            Component label = Component.literal(NUMERALS[era] + " ").append(Component.translatable(ResearchNode.getEraKey(era)));
            int color = !reached ? 0xFF56606C : here ? 0xFFFFFFFF : 0xFFB8C4D4;
            float scale = 0.7F;
            int maxW = (int) ((tabW - (mastered ? 10 : 4)) / scale);
            String text = label.getString();
            if (font.width(text) > maxW) {
                while (text.length() > 2 && font.width(text + "…") > maxW) {
                    text = text.substring(0, text.length() - 1);
                }
                text += "…";
            }
            smallText(graphics, Component.literal(text), x + 3, y0 + 4, color, scale);
            if (mastered) {
                smallText(graphics, Component.literal("✔"), x + tabW - 8, y0 + 3, 0xFFD2A040, 0.7F);
            }
            int target = era;
            addHitbox(x, y0, x + tabW, y0 + h, () -> focusChapter(target));
        }
    }

    /** Au moins un astre du chapitre est entierement maitrise (le chapitre suivant est ouvert). */
    private static boolean chapterMastered(int era) {
        if (era >= BodyResearch.CHAPTERS.size() || BodyResearch.CHAPTERS.get(era).isEmpty()) {
            //Chapitres sans astre : leur recherche phare
            ResearchNode milestone = switch (era) {
                case 9 -> ResearchNode.SINGULARITY_THEORY;
                case 10 -> ResearchNode.GAS_HARVESTING;
                case 13 -> ResearchNode.UNIVERSE_SIMULATION;
                default -> null;
            };
            return milestone != null && ClientResearch.isCompleted(milestone);
        }
        for (Body body : BodyResearch.CHAPTERS.get(era)) {
            if (ClientResearch.mastery(body) >= 4) {
                return true;
            }
        }
        return false;
    }

    private void focusChapter(int era) {
        float center = colX((ERA_COLS[era][0] + ERA_COLS[era][1]) / 2F);
        panX = -center * zoom;
        panY = 0;
        clampPan();
    }

    private void drawBottomBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int y0 = height - BOTTOM;
        graphics.fillGradient(0, y0, width, height, 0xC0000000, 0xF0000000);
        graphics.fill(0, y0, width, y0 + 1, PANEL_EDGE);
        //Aide, discrete, en bas de la toile
        GuiDraw.text(graphics, font, Component.translatable("research.pioneers.tree.hint"), 6, y0 - 7, 0.55F, 0xFF56606C, Math.max(1, (int) viewRight() - 12));
        ResearchNode active = ClientResearch.getActive();
        int x = 10;
        if (active == null) {
            GuiDraw.text(graphics, font, Component.translatable("research.pioneers.tree.idle"), x, y0 + 13, 0.7F, 0xFF8090A0, Math.max(1, (int) viewRight() - 20));
        } else {
            smallText(graphics, Component.translatable("research.pioneers.tree.active"), x, y0 + 4, 0xFF55CCFF, 0.6F);
            graphics.renderItem(active.getIcon(), x, y0 + 12);
            int bx = x + 20;
            smallText(graphics, Component.translatable(active.getTranslationKey()), bx, y0 + 11, 0xFFE8F4FF, 0.8F);
            int progress = ClientResearch.getProgress(active);
            int barW = (int) Math.max(60, Math.min(160, viewRight() - bx - 12));
            int by = y0 + 21;
            float fraction = progress / (float) active.units;
            graphics.fill(bx, by, bx + barW, by + 4, 0xFF101822);
            graphics.fill(bx, by, bx + (int) (barW * fraction), by + 4, 0xFF3FA8E0);
            //Reflet qui court sur la barre
            int shine = bx + (int) ((seconds * 40) % (barW + 20)) - 10;
            if (shine > bx && shine + 6 < bx + barW * fraction) {
                graphics.fill(shine, by, shine + 6, by + 4, 0x60FFFFFF);
            }
            smallText(graphics, Component.translatable("research.pioneers.tree.progress", progress, active.units).append("  \u00B7  ").append(eta(active)),
                  bx, by + 6, 0xFF9FB4C8, 0.55F);
        }
        //File d'attente, sous la fiche
        List<ResearchNode> queue = ClientResearch.getQueue();
        int qx = (int) viewRight() + 8;
        smallText(graphics, Component.translatable("research.pioneers.tree.queue"), qx, y0 + 4, 0xFF8FB8D8, 0.6F);
        for (int i = 0; i < ResearchState.MAX_QUEUE; i++) {
            int bx = qx + i * 22, by = y0 + 12;
            graphics.fill(bx, by, bx + 20, by + 18, 0x90182430);
            graphics.fill(bx, by, bx + 20, by + 1, PANEL_EDGE);
            if (i < queue.size()) {
                ResearchNode node = queue.get(i);
                graphics.renderItem(node.getIcon(), bx + 2, by + 1);
                if (mouseX >= bx && mouseX < bx + 20 && mouseY >= by && mouseY < by + 18) {
                    graphics.fill(bx, by, bx + 20, by + 18, 0x40FFFFFF);
                }
                addHitbox(bx, by, bx + 20, by + 18, () -> selected = node);
            } else {
                smallCentered(graphics, Component.literal("\u00B7"), bx + 10, by + 6, 0xFF3A4656, 1);
            }
        }
    }

    private Component eta(ResearchNode node) {
        int labs = ClientResearch.getLabs();
        if (labs <= 0) {
            return Component.translatable("research.pioneers.tree.eta_none");
        }
        double speed = ClientResearch.isCompleted(ResearchNode.QUANTUM_LABS) ? 1.5 : 1;
        int remaining = node.units - ClientResearch.getProgress(node);
        long secondsLeft = (long) Math.ceil(remaining * node.unitSeconds / (labs * speed));
        return Component.translatable("research.pioneers.tree.eta", formatTime(secondsLeft));
    }

    private static String formatTime(long seconds) {
        if (seconds >= 3600) {
            return seconds / 3600 + "h" + String.format("%02d", seconds % 3600 / 60);
        }
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    /** Fiche detaillee de la recherche choisie. */
    private void drawSidePanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int x0 = (int) viewRight(), x1 = width, y0 = TOP, y1 = height - BOTTOM;
        graphics.fill(x0, y0, x1, y1, PANEL_BG);
        graphics.fill(x0, y0, x0 + 1, y1, PANEL_EDGE);
        ResearchNode node = selected;
        if (node == null) {
            smallText(graphics, Component.translatable("research.pioneers.tree.select"), x0 + 10, y0 + 12, 0xFF8090A0, 0.7F);
            return;
        }
        State state = state(node);
        int x = x0 + 8, w = PANEL_W - 16;
        int y = y0 + 8;
        //En-tete : icone + nom + ere
        graphics.fill(x, y, x + 22, y + 22, 0xFF000000 | stateColor(state));
        graphics.fill(x + 1, y + 1, x + 21, y + 21, 0xFF0C141E);
        graphics.renderItem(node.getIcon(), x + 3, y + 3);
        List<FormattedCharSequence> nameLines = font.split(Component.translatable(node.getTranslationKey()), (int) ((w - 28) / 0.85F));
        int ny = y + (nameLines.size() > 1 ? 1 : 3);
        for (FormattedCharSequence line : nameLines) {
            smallText(graphics, line, x + 28, ny, 0xFFFFFFFF, 0.85F);
            ny += 8;
        }
        smallText(graphics, Component.translatable(ResearchNode.getEraKey(node.era)), x + 28, Math.max(ny, y + 13) + 1, 0xFF000000 | ERA_TINT[node.era], 0.6F);
        y += 28;
        //Etat
        int queueIndex = ClientResearch.getQueue().indexOf(node);
        Component stateText = switch (state) {
            case COMPLETED -> Component.translatable("research.pioneers.state.completed");
            case ACTIVE -> Component.translatable("research.pioneers.state.active");
            case AVAILABLE -> !node.isLab() ? Component.translatable("research.pioneers.state.samples")
                              : queueIndex >= 0 ? Component.translatable("research.pioneers.state.queued", queueIndex + 1)
                                              : Component.translatable("research.pioneers.state.available");
            case LOCKED -> queueIndex >= 0 ? Component.translatable("research.pioneers.state.queued", queueIndex + 1)
                                           : Component.translatable("research.pioneers.state.locked");
        };
        int chipW = (int) (font.width(stateText) * 0.65F) + 8;
        int chipColor = nodeColor(node, state);
        graphics.fill(x, y, x + chipW, y + 10, (chipColor & 0xFFFFFF) | 0x50000000);
        smallText(graphics, stateText, x + 4, y + 2, 0xFF000000 | chipColor, 0.65F);
        if (!node.isLab()) {
            Component kind = Component.translatable(node.kind.getTranslationKey());
            smallText(graphics, kind, x + chipW + 5, y + 2, 0xFF000000 | kindColor(node.kind), 0.6F);
        }
        y += 14;
        //Description
        for (FormattedCharSequence line : font.split(Component.translatable(node.getDescriptionKey()), (int) (w / 0.62F))) {
            smallText(graphics, line, x, y, 0xFFB0BCCA, 0.62F);
            y += 6;
        }
        y += 4;
        //Effet
        y = section(graphics, "research.pioneers.tree.effect", x, y, w);
        for (FormattedCharSequence line : font.split(Component.translatable(node.getTranslationKey() + ".effect"), (int) (w / 0.62F))) {
            smallText(graphics, line, x, y, 0xFF7CFFA8, 0.62F);
            y += 6;
        }
        y += 4;
        if (!node.isLab()) {
            y = drawSamples(graphics, node, state, x, y, w);
        } else {
        //Cout
        y = section(graphics, "research.pioneers.tree.cost_unit", x, y, w);
        int cx = x;
        for (Map.Entry<ProbeDestination, Long> cost : node.getUnitCost().entrySet()) {
            Component chip = Component.translatable(cost.getKey().getShortKey()).append(" " + cost.getValue());
            int cw = (int) (font.width(chip) * 0.6F) + 8;
            if (cx + cw > x + w) {
                cx = x;
                y += 11;
            }
            int color = cost.getKey().color;
            graphics.fill(cx, y, cx + cw, y + 9, (color & 0xFFFFFF) | 0x40000000);
            graphics.fill(cx, y, cx + 2, y + 9, 0xFF000000 | color);
            smallText(graphics, chip, cx + 5, y + 2, 0xFFE8F0FF, 0.6F);
            cx += cw + 3;
        }
        y += 12;
        long totalData = 0;
        for (long value : node.getUnitCost().values()) {
            totalData += value * node.units;
        }
        //Sur deux lignes : sur une seule, la fin sortait de l'ecran
        GuiDraw.text(graphics, font, Component.translatable("research.pioneers.tree.units", node.units, node.unitSeconds), x, y, 0.58F, 0xFF9FB4C8, w);
        y += 7;
        GuiDraw.text(graphics, font, Component.translatable("research.pioneers.tree.total", DataUnits.format(totalData)), x, y, 0.58F, 0xFF9FB4C8, w);
        y += 9;
        //Progression
        if (state != State.COMPLETED) {
            int progress = ClientResearch.getProgress(node);
            graphics.fill(x, y, x + w, y + 4, 0xFF101822);
            graphics.fill(x, y, x + (int) (w * progress / (float) node.units), y + 4, state == State.ACTIVE ? 0xFF3FA8E0 : 0xFF2A5070);
            y += 6;
            smallText(graphics, Component.translatable("research.pioneers.tree.progress", progress, node.units).append("  ").append(eta(node)), x, y, 0xFF8090A0, 0.58F);
            y += 9;
        }
        }
        int limit = y1 - (hasActions(node, state) ? 30 : 6);
        //Prerequis (un groupe "l'un ou l'autre" sur une ligne)
        if (!node.getPrerequisiteGroups().isEmpty() && y + 9 + node.getPrerequisiteGroups().size() * 7 <= limit) {
            y = section(graphics, "research.pioneers.tree.prereq", x, y, w);
            for (List<ResearchNode> group : node.getPrerequisiteGroups()) {
                ResearchNode done = group.stream().filter(ClientResearch::isCompleted).findFirst().orElse(null);
                boolean ok = done != null;
                if (group.size() == 1) {
                    y = link(graphics, group.get(0), (ok ? "✔ " : "✖ "), ok ? 0xFF7CFFA8 : 0xFFFF7766, x, y, w, mouseX, mouseY);
                } else {
                    y = groupLink(graphics, group, ok ? done : group.get(0), ok, x, y, w, mouseX, mouseY);
                }
            }
            y += 3;
        }
        //0.18.0 : jalons industriels (progres des ages) et plancher de calcul
        List<ResourceLocation> milestones = ResearchMilestones.of(node);
        if (!milestones.isEmpty() && state != State.COMPLETED && y + 9 + milestones.size() * 7 <= limit) {
            y = section(graphics, "research.pioneers.tree.milestones", x, y, w);
            for (ResourceLocation id : milestones) {
                boolean ok = ClientResearch.hasMilestone(id);
                Component title = Component.translatable("advancements." + id.getNamespace() + "." + id.getPath().replace('/', '.') + ".title");
                smallText(graphics, Component.literal(ok ? "✔ " : "✖ ").append(title), x, y, ok ? 0xFF7CFFA8 : 0xFFFF7766, 0.6F);
                y += 7;
            }
            y += 3;
        }
        double gops = ResearchMilestones.gopsFloor(node), qops = ResearchMilestones.qopsFloor(node);
        if (node.isLab() && gops > 0 && state != State.COMPLETED && y + 9 + 13 <= limit) {
            y = section(graphics, "research.pioneers.tree.compute", x, y, w);
            Component line = qops > 0 ? Component.translatable("research.pioneers.tree.compute_q", (long) gops, (long) qops)
                  : Component.translatable("research.pioneers.tree.compute_g", (long) gops);
            int n = GuiDraw.wrapped(graphics, font, line, x, y, 0.6F, 0xFF80E0B0, w, 2, 6);
            y += n * 6 + 4;
        }
        //Recompenses
        List<Component> rewards = new ArrayList<>();
        ItemStack blueprint = node.getBlueprint();
        if (blueprint != null) {
            rewards.add(Component.translatable("research.pioneers.tree.blueprint", blueprint.getHoverName()));
        }
        List<String> bodies = new ArrayList<>();
        for (Body body : Body.ALL) {
            if (ResearchEffects.discoveryOf(body) == node) {
                bodies.add(Component.translatable(body.getTranslationKey()).getString());
            }
        }
        if (!bodies.isEmpty()) {
            rewards.add(Component.translatable("research.pioneers.tree.discovers", String.join(", ", bodies)));
        }
        if (node.isOrbit() && node.getBody() != null) {
            rewards.add(Component.translatable("research.pioneers.tree.landing", Component.translatable(node.getBody().getTranslationKey())));
        }
        for (ProbeDestination destination : ProbeDestination.values()) {
            if (ResearchEffects.unlockOf(destination) == node) {
                rewards.add(Component.translatable("research.pioneers.tree.probe_target", Component.translatable(destination.getTranslationKey())));
            }
        }
        if (!rewards.isEmpty() && y + 9 + rewards.size() * 6 <= limit) {
            y = section(graphics, "research.pioneers.tree.reward", x, y, w);
            for (Component reward : rewards) {
                for (FormattedCharSequence line : font.split(reward, (int) (w / 0.6F))) {
                    smallText(graphics, line, x, y, 0xFFD8B8FF, 0.6F);
                    y += 6;
                }
            }
            y += 3;
        }
        //Debouche sur
        List<ResearchNode> unlocks = new ArrayList<>();
        for (ResearchNode other : ResearchNode.values()) {
            if (other.getPrerequisites().contains(node)) {
                unlocks.add(other);
            }
        }
        if (!unlocks.isEmpty() && y + 9 + 7 <= limit) {
            y = section(graphics, "research.pioneers.tree.unlocks", x, y, w);
            for (ResearchNode other : unlocks) {
                if (y + 7 > limit) {
                    break;
                }
                y = link(graphics, other, "▸ ", 0xFF9FB4C8, x, y, w, mouseX, mouseY);
            }
        }
        drawActions(graphics, node, state, x0, y1, mouseX, mouseY);
    }

    private int section(GuiGraphics graphics, String key, int x, int y, int w) {
        smallText(graphics, Component.translatable(key), x, y, 0xFF8FB8D8, 0.6F);
        graphics.fill(x, y + 6, x + w, y + 7, 0x3090A8C8);
        return y + 9;
    }

    /** Ligne « l'un des astres » : biologie de Mars, Phobos ou Deimos. */
    private int groupLink(GuiGraphics graphics, List<ResearchNode> group, ResearchNode target, boolean ok, int x, int y, int w, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y - 1 && mouseY < y + 6;
        if (hover) {
            graphics.fill(x - 2, y - 1, x + w, y + 6, 0x30FFFFFF);
        }
        List<String> names = new ArrayList<>();
        for (ResearchNode alternative : group) {
            Body body = alternative.getBody();
            names.add(body != null ? Component.translatable(body.getTranslationKey()).getString() : Component.translatable(alternative.getTranslationKey()).getString());
        }
        Component kind = Component.translatable(group.get(0).kind.getTranslationKey());
        Component text = Component.literal(ok ? "✔ " : "✖ ").append(Component.translatable("research.pioneers.tree.any_of", kind, String.join(" / ", names)));
        String plain = text.getString();
        int maxW = (int) (w / 0.6F);
        if (font.width(plain) > maxW) {
            while (plain.length() > 2 && font.width(plain + "…") > maxW) {
                plain = plain.substring(0, plain.length() - 1);
            }
            plain += "…";
        }
        smallText(graphics, Component.literal(plain), x, y, hover ? 0xFFFFFFFF : ok ? 0xFF7CFFA8 : 0xFFD8A8FF, 0.6F);
        addHitbox(x - 2, y - 1, x + w, y + 6, () -> select(target));
        return y + 7;
    }

    /** Fiche d'une recherche par echantillons : comment la faire avancer, et l'avancement de chaque type. */
    private int drawSamples(GuiGraphics graphics, ResearchNode node, State state, int x, int y, int w) {
        y = section(graphics, "research.pioneers.tree.samples", x, y, w);
        Body body = node.getBody();
        Component bodyName = body == null ? Component.empty() : Component.translatable(body.getTranslationKey());
        for (FormattedCharSequence line : font.split(Component.translatable("research.pioneers.tree.how." + node.kind.name().toLowerCase(), bodyName),
              (int) (w / 0.6F))) {
            smallText(graphics, line, x, y, 0xFF9FD8C8, 0.6F);
            y += 6;
        }
        y += 3;
        int color = 0xFF000000 | kindColor(node.kind);
        boolean done = state == State.COMPLETED;
        for (int type = 0; type < node.sampleTypes(); type++) {
            int have = done ? node.units : Math.min(node.units, ClientResearch.getSamples(node, type));
            Component label = node.sampleTypes() > 1 ? Component.translatable("analyzer.pioneers.asteroid." + TYPE_KEYS[type])
                                                      : Component.translatable("research.pioneers.tree.sample_unit." + node.kind.name().toLowerCase());
            smallText(graphics, label, x, y, 0xFFB0BCCA, 0.58F);
            Component count = Component.literal(have + " / " + node.units);
            smallText(graphics, count, x + w - font.width(count) * 0.58F, y, have >= node.units ? 0xFF7CFFA8 : 0xFFE8F0FF, 0.58F);
            y += 6;
            graphics.fill(x, y, x + w, y + 3, 0xFF101822);
            graphics.fill(x, y, x + (int) (w * have / (float) node.units), y + 3, have >= node.units ? 0xFF4CC870 : color);
            y += 6;
        }
        return y + 2;
    }

    private static final String[] TYPE_KEYS = {"rock", "iron", "ice", "volcanic"};

    private int link(GuiGraphics graphics, ResearchNode target, String prefix, int color, int x, int y, int w, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y - 1 && mouseY < y + 6;
        if (hover) {
            graphics.fill(x - 2, y - 1, x + w, y + 6, 0x30FFFFFF);
        }
        smallText(graphics, Component.literal(prefix).append(Component.translatable(target.getTranslationKey())), x, y, hover ? 0xFFFFFFFF : color, 0.6F);
        addHitbox(x - 2, y - 1, x + w, y + 6, () -> select(target));
        return y + 7;
    }

    private static boolean hasActions(ResearchNode node, State state) {
        return node.isLab() && (state == State.AVAILABLE || state == State.ACTIVE || ClientResearch.getQueue().contains(node) || plannable(node) && ClientResearch.getActive() != null);
    }

    private void drawActions(GuiGraphics graphics, ResearchNode node, State state, int x0, int y1, int mouseX, int mouseY) {
        if (!node.isLab()) {
            return;
        }
        if (table == null) {
            //Consultation : les recherches se lancent depuis une table de recherche
            if (hasActions(node, state)) {
                GuiDraw.wrapped(graphics, font, Component.translatable("research.pioneers.tree.view_only"), x0 + 8, y1 - 22, 0.65F, 0xFF8090A0, PANEL_W - 16, 2, 7);
            }
            return;
        }
        List<Object[]> actions = new ArrayList<>();
        boolean queued = ClientResearch.getQueue().contains(node);
        if (state == State.AVAILABLE) {
            actions.add(new Object[]{"research.pioneers.tree.start", 0xFF1E7A4A, PacketResearch.Select.START});
        }
        if (!queued && state != State.ACTIVE && plannable(node) && ClientResearch.getActive() != null) {
            actions.add(new Object[]{"research.pioneers.tree.enqueue", 0xFF1A5A80, PacketResearch.Select.ENQUEUE});
        }
        if (queued || state == State.ACTIVE) {
            actions.add(new Object[]{"research.pioneers.tree.remove", 0xFF7A2A2A, PacketResearch.Select.REMOVE});
        }
        if (actions.isEmpty()) {
            return;
        }
        int gap = 4, bw = (PANEL_W - 16 - gap * (actions.size() - 1)) / actions.size(), bh = 16;
        int y = y1 - bh - 8;
        for (int i = 0; i < actions.size(); i++) {
            Object[] action = actions.get(i);
            int bx = x0 + 8 + i * (bw + gap);
            boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= y && mouseY < y + bh;
            int color = (Integer) action[1];
            graphics.fill(bx, y, bx + bw, y + bh, hover ? brighten(color) : color);
            graphics.fill(bx, y, bx + bw, y + 1, 0x60FFFFFF);
            smallCentered(graphics, Component.translatable((String) action[0]), bx + bw / 2F, y + 5, 0xFFFFFFFF, 0.75F);
            int code = (Integer) action[2];
            addHitbox(bx, y, bx + bw, y + bh, () -> send(node, code));
        }
    }

    private void drawTooltip(GuiGraphics graphics, ResearchNode node, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(node.getTranslationKey()).withStyle(style -> style.withColor(stateColor(state(node)))));
        lines.add(Component.translatable(node.getTranslationKey() + ".effect").withStyle(style -> style.withColor(0x7CFFA8)));
        if (!node.isLab()) {
            lines.add(1, Component.translatable(node.kind.getTranslationKey()).withStyle(style -> style.withColor(kindColor(node.kind))));
            if (!ClientResearch.isCompleted(node)) {
                lines.add(Component.translatable("research.pioneers.tree.fraction", Math.round(ClientResearch.getFraction(node) * 100))
                      .withStyle(style -> style.withColor(0x9FB4C8)));
            }
        } else {
            int progress = ClientResearch.getProgress(node);
            if (!ClientResearch.isCompleted(node) && progress > 0) {
                lines.add(Component.translatable("research.pioneers.tree.progress", progress, node.units).withStyle(style -> style.withColor(0x9FB4C8)));
            }
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    // ------------------------------------------------------------------
    // Entrees
    // ------------------------------------------------------------------

    private float anchorX, anchorY;

    private void select(ResearchNode node) {
        selected = node;
    }

    private void send(ResearchNode node, int action) {
        if (table == null) {
            return;
        }
        PiNetwork.sendToServer(new PacketResearch.Select(table, node.ordinal(), action));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
              action == PacketResearch.Select.REMOVE ? SoundEvents.UI_BUTTON_CLICK.get() : SoundEvents.BEACON_ACTIVATE,
              action == PacketResearch.Select.REMOVE ? 0.8F : 1.6F, 0.6F));
    }

    private void addHitbox(int x0, int y0, int x1, int y1, Runnable action) {
        hitboxes.add(new Object[]{new int[]{x0, y0, x1, y1}, action});
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Object[] hitbox : hitboxes) {
                int[] box = (int[]) hitbox[0];
                if (mouseX >= box[0] && mouseX < box[2] && mouseY >= box[1] && mouseY < box[3]) {
                    ((Runnable) hitbox[1]).run();
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                          SoundEvents.UI_BUTTON_CLICK.get(), 1.4F, 0.3F));
                    return true;
                }
            }
            if (inWorld(mouseX, mouseY)) {
                pressed = true;
                dragged = false;
                pressedInWorld = true;
                pressX = mouseX;
                pressY = mouseY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pressed && pressedInWorld && button == 0) {
            if (!dragged && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 4) {
                dragged = true;
            }
            if (dragged) {
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
            if (!dragged) {
                ResearchNode node = nodeAt(mouseX, mouseY);
                if (node != null) {
                    long now = System.currentTimeMillis();
                    boolean doubleClick = node == lastClicked && now - lastClick < 300;
                    lastClick = now;
                    lastClicked = node;
                    selected = node;
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                          SoundEvents.UI_BUTTON_CLICK.get(), 1.8F, 0.25F));
                    if (hasShiftDown()) {
                        if (plannable(node) || node.isLab() && ClientResearch.isAvailable(node) && ClientResearch.getActive() == null) {
                            send(node, PacketResearch.Select.ENQUEUE);
                        }
                    } else if (doubleClick && node.isLab() && ClientResearch.isAvailable(node) && ClientResearch.getActive() != node) {
                        send(node, PacketResearch.Select.START);
                    }
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= 23 && mouseY < 36 && tabStart >= 0) {
            //Molette sur les onglets : ils defilent
            tabStart = Mth.clamp(tabStart - (int) Math.signum(delta), 0, Math.max(0, ResearchNode.ERAS - VISIBLE_TABS));
            return true;
        }
        if (mouseX < viewRight()) {
            targetZoom = Mth.clamp(targetZoom * (float) Math.pow(1.15, delta), MIN_ZOOM, MAX_ZOOM);
            anchorX = (float) mouseX;
            anchorY = (float) mouseY;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && selected != null && selected.isLab() && ClientResearch.isAvailable(selected)
            && ClientResearch.getActive() != selected) {
            send(selected, PacketResearch.Select.START);
            return true;
        }
        if (minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------
    // Outils de dessin
    // ------------------------------------------------------------------

    private void smallText(GuiGraphics graphics, Component text, float x, float y, int color, float scale) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }

    private void smallText(GuiGraphics graphics, FormattedCharSequence text, float x, float y, int color, float scale) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }

    private void smallCentered(GuiGraphics graphics, Component text, float x, float y, int color, float scale) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, false);
        pose.popPose();
    }

    /** Rectangle a coordonnees flottantes (sous-pixel une fois zoome). */
    private static void fillF(GuiGraphics graphics, float x0, float y0, float x1, float y1, int argb) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x0, y0, 0);
        pose.scale((x1 - x0) / 64F, (y1 - y0) / 64F, 1);
        graphics.fill(0, 0, 64, 64, argb);
        pose.popPose();
    }

    private static void quad(BufferBuilder builder, Matrix4f matrix, float x, float y, float w, float h, int color, float alpha) {
        builder.vertex(matrix, x, y + h, 0).color(r(color), g(color), b(color), alpha).endVertex();
        builder.vertex(matrix, x + w, y + h, 0).color(r(color), g(color), b(color), alpha).endVertex();
        builder.vertex(matrix, x + w, y, 0).color(r(color), g(color), b(color), alpha).endVertex();
        builder.vertex(matrix, x, y, 0).color(r(color), g(color), b(color), alpha).endVertex();
    }

    /** Segment horizontal ou vertical d'epaisseur donnee (avec des bouts carres pour bien joindre les coudes). */
    private static void segment(BufferBuilder builder, Matrix4f matrix, float x0, float y0, float x1, float y1, float thickness, int color, float alpha) {
        float h = thickness / 2;
        quad(builder, matrix, Math.min(x0, x1) - h, Math.min(y0, y1) - h, Math.abs(x1 - x0) + thickness, Math.abs(y1 - y0) + thickness, color, alpha);
    }

    /** Polyligne orthogonale a coins arrondis (en pointilles pour les liaisons « l'un ou l'autre »). */
    private void polyline(BufferBuilder builder, Matrix4f matrix, float[] p, float thickness, int color, float alpha, boolean dashedLine) {
        int points = p.length / 2;
        float radius = 5 * zoom;
        float[] trimmed = p.clone();
        //Raccourcit chaque segment aux coins pour y poser un arc
        float[] cut = new float[points];
        for (int i = 1; i < points - 1; i++) {
            float in = Math.abs(p[2 * i] - p[2 * i - 2]) + Math.abs(p[2 * i + 1] - p[2 * i - 1]);
            float out = Math.abs(p[2 * i + 2] - p[2 * i]) + Math.abs(p[2 * i + 3] - p[2 * i + 1]);
            cut[i] = Math.min(radius, Math.min(in, out) / 2);
        }
        for (int i = 0; i + 1 < points; i++) {
            float x0 = p[2 * i], y0 = p[2 * i + 1], x1 = p[2 * i + 2], y1 = p[2 * i + 3];
            float length = Math.abs(x1 - x0) + Math.abs(y1 - y0);
            if (length <= 0.01F) {
                continue;
            }
            float dx = (x1 - x0) / length, dy = (y1 - y0) / length;
            float sx0 = x0 + dx * cut[i], sy0 = y0 + dy * cut[i], sx1 = x1 - dx * cut[i + 1], sy1 = y1 - dy * cut[i + 1];
            if (dashedLine) {
                dashed(builder, matrix, sx0, sy0, sx1, sy1, thickness, color, alpha);
            } else {
                segment(builder, matrix, sx0, sy0, sx1, sy1, thickness, color, alpha);
            }
        }
        //Arcs
        for (int i = 1; i < points - 1; i++) {
            float r = cut[i];
            if (r < 0.5F) {
                continue;
            }
            float px = p[2 * i], py = p[2 * i + 1];
            float inX = Math.signum(px - p[2 * i - 2]), inY = Math.signum(py - p[2 * i - 1]);
            float outX = Math.signum(p[2 * i + 2] - px), outY = Math.signum(p[2 * i + 3] - py);
            if (inX * outX + inY * outY != 0) {
                continue;
            }
            float cx = px - inX * r + outX * r, cy = py - inY * r + outY * r;
            float a0 = (float) Math.atan2(-outY, -outX), a1 = (float) Math.atan2(inY, inX);
            float sweep = Mth.wrapDegrees((a1 - a0) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            float start = sweep >= 0 ? a0 : a0 + sweep, end = sweep >= 0 ? a0 + sweep : a0;
            int steps = 4;
            for (int k = 0; k < steps; k++) {
                if (dashedLine && k % 2 == 1) {
                    continue;
                }
                ring(builder, matrix, cx, cy, r, thickness, Mth.lerp(k / (float) steps, start, end), Mth.lerp((k + 1) / (float) steps, start, end), color, alpha);
            }
        }
    }

    /** Pointe de fleche en escalier (facon pixel) a l'arrivee de la liaison. */
    private static void arrow(BufferBuilder builder, Matrix4f matrix, float[] p, float thickness, int color, float alpha) {
        int n = p.length;
        float ex = p[n - 2], ey = p[n - 1], dx = Math.signum(ex - p[n - 4]), dy = Math.signum(ey - p[n - 3]);
        if (dx == 0 && dy == 0) {
            return;
        }
        float step = Math.max(1, thickness * 0.9F);
        for (int k = 0; k < 3; k++) {
            float back = (k + 1) * step, spanHalf = (3 - k) * step;
            float cx = ex - dx * back, cy = ey - dy * back;
            if (dx != 0) {
                quad(builder, matrix, Math.min(cx, cx + dx * step), cy - spanHalf, step, spanHalf * 2, color, alpha);
            } else {
                quad(builder, matrix, cx - spanHalf, Math.min(cy, cy + dy * step), spanHalf * 2, step, color, alpha);
            }
        }
    }

    /** Segment en pointilles (liaisons « l'un ou l'autre »). */
    private static void dashed(BufferBuilder builder, Matrix4f matrix, float x0, float y0, float x1, float y1, float thickness, int color, float alpha) {
        float length = Math.abs(x1 - x0) + Math.abs(y1 - y0);
        float dash = 4, gap = 3;
        for (float d = 0; d < length; d += dash + gap) {
            float t0 = d / Math.max(1, length), t1 = Math.min(1, (d + dash) / Math.max(1, length));
            segment(builder, matrix, Mth.lerp(t0, x0, x1), Mth.lerp(t0, y0, y1), Mth.lerp(t1, x0, x1), Mth.lerp(t1, y0, y1), thickness, color, alpha);
        }
    }

    private static void ring(BufferBuilder builder, Matrix4f matrix, float cx, float cy, float radius, float width, float a0, float a1, int color, float alpha) {
        float r0 = radius - width / 2, r1 = radius + width / 2;
        builder.vertex(matrix, cx + Mth.cos(a0) * r0, cy + Mth.sin(a0) * r0, 0).color(r(color), g(color), b(color), alpha).endVertex();
        builder.vertex(matrix, cx + Mth.cos(a1) * r0, cy + Mth.sin(a1) * r0, 0).color(r(color), g(color), b(color), alpha).endVertex();
        builder.vertex(matrix, cx + Mth.cos(a1) * r1, cy + Mth.sin(a1) * r1, 0).color(r(color), g(color), b(color), alpha).endVertex();
        builder.vertex(matrix, cx + Mth.cos(a0) * r1, cy + Mth.sin(a0) * r1, 0).color(r(color), g(color), b(color), alpha).endVertex();
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF), g = (int) Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF), bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return r << 16 | g << 8 | bl;
    }

    private static int brighten(int argb) {
        int r = Math.min(255, (argb >> 16 & 0xFF) + 40), g = Math.min(255, (argb >> 8 & 0xFF) + 40), b = Math.min(255, (argb & 0xFF) + 40);
        return argb & 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static float r(int color) {
        return (color >> 16 & 0xFF) / 255F;
    }

    private static float g(int color) {
        return (color >> 8 & 0xFF) / 255F;
    }

    private static float b(int color) {
        return (color & 0xFF) / 255F;
    }
}

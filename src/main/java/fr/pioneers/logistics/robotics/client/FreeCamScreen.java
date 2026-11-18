package fr.pioneers.logistics.robotics.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.client.gui.GuiDraw;
import java.util.ArrayDeque;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Base des planificateurs en vue libre : une camera detachee du joueur survole le vrai monde (ZQSD / WASD, espace, Maj ; clic droit :
 * regarder ; molette : avancer), avec un selecteur de volume (glisser, ou deux clics aux coins opposes) dont on etire ensuite les faces
 * avec des poignees. Barre du haut (vue, annuler / retablir), outils a gauche, panneau a droite, aide en bas ; le contenu (zone de
 * travail, travaux) est fourni par la sous-classe.
 */
abstract class FreeCamScreen extends RoboticsScreen {

    protected static final int TOP = 24, BOTTOM = 14, SIDE = 178, TOOLS = 26, FOOTER = 24;
    protected static final int PURPLE = 0xA65CFF;
    protected static final String SELECT = "select";

    protected enum Drag {NONE, LOOK, PAN, SELECT, HANDLE, MOVE, TOOL}

    /** Poignees du volume : une par face, et le deplacement. */
    protected enum Handle {
        WEST(0, -1), EAST(0, 1), DOWN(1, -1), UP(1, 1), NORTH(2, -1), SOUTH(2, 1), MOVE(-1, 0);

        final int axis, sign;

        Handle(int axis, int sign) {
            this.axis = axis;
            this.sign = sign;
        }
    }

    /** Bloc vise : sa position, la face touchee, le point exact. */
    protected record Hit(BlockPos pos, Direction face, Vec3 point) {}

    /** Outil de la barre de gauche : identifiant, glyphe, raccourci, couleur. */
    protected record ToolDef(String id, String glyph, int key, String keyName, int color) {}

    @Nullable
    protected final Screen back;
    protected Component plannerTitle;

    //Camera libre
    @Nullable
    private Marker camera;
    protected Vec3 camPos = Vec3.ZERO;
    protected float camYaw = 180, camPitch = 50;
    @Nullable
    private Entity previousCamera;
    private boolean previousHideGui, active;
    @Nullable
    private CameraType previousCameraType;
    protected float flySpeed = 14;
    protected boolean mouseInView;
    private long lastFrame;
    /** Point a cadrer a l'ouverture (sans volume). */
    protected Vec3 focus = Vec3.ZERO;

    //Souris
    protected Drag drag = Drag.NONE;
    protected double lastX, lastY, startX, startY;
    @Nullable
    protected Hit hover;
    @Nullable
    protected Handle hovered, dragged;
    @Nullable
    private BlockPos dragMin, dragMax, drawStart;
    @Nullable
    private Vec3 movePlane;
    /** Selection en deux clics : le premier coin, en attendant le second. */
    @Nullable
    protected BlockPos corner;
    private boolean drawMoved;

    //Annuler / retablir
    private final ArrayDeque<Object> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

    protected String tool = SELECT;
    protected int ticks;
    private int panelScroll, panelContent;

    protected FreeCamScreen(Component title, @Nullable Screen back) {
        super(title, back, 10_000, 10_000);
        this.back = back;
        this.plannerTitle = title;
        drawPanel = false;
        dark = true;
    }

    // ------------------------------------------------------------------
    // A fournir par les planificateurs
    // ------------------------------------------------------------------

    protected abstract List<ToolDef> tools();

    @Nullable
    protected abstract BlockPos selMin();

    @Nullable
    protected abstract BlockPos selMax();

    /** Nouveau volume (coins quelconques, ou null pour l'effacer). */
    protected abstract void setSelection(@Nullable BlockPos a, @Nullable BlockPos b);

    /** Taille maximale du volume sur un axe. */
    protected int maxSpan(int axis) {
        return axis == 1 ? 64 : 96;
    }

    protected int selectionColor() {
        return 0x3FD0A0;
    }

    /** Un rectangle vient d'etre trace au sol (hauteurs : sol le plus bas et le plus haut dessous). */
    protected void selectionDrawn(int x0, int z0, int x1, int z1, int low, int high) {
        setSelection(new BlockPos(x0, low, z0), new BlockPos(x1, high, z1));
    }

    /** Le volume a ete modifie (trace, poignees, bornes) : fin du geste. */
    protected void selectionChanged() {
    }

    protected abstract Object snapshot();

    protected abstract void restore(Object state);

    /** Contenu du panneau de droite (defilant) ; renvoie le y atteint. */
    protected abstract int drawPanel(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w);

    /** Pied du panneau (boutons). */
    protected abstract void drawFooter(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w);

    /** Aide de la barre du bas (outil courant). */
    protected abstract Component hint();

    protected abstract void save();

    /** Clic gauche avec un autre outil que la selection ; true si traite. */
    /** Clic droit simple (sans faire tourner la vue) dans la vue. */
    protected void rightClick(@Nullable Hit hit, double mouseX, double mouseY) {
    }

    protected boolean toolClick(@Nullable Hit hit, double mouseX, double mouseY) {
        return false;
    }

    /** Glisser avec un autre outil. */
    protected void toolDrag(double mouseX, double mouseY) {
    }

    /** Dessin dans le monde (avec profondeur). */
    protected void renderExtra(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cameraPos) {
    }

    /** Dessin par-dessus la vue (etiquettes, messages). */
    protected void drawOverlayExtra(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    /** Boutons supplementaires de la barre du haut, a partir de x ; renvoie le x atteint. */
    protected int drawTopBarExtra(GuiGraphics graphics, int mouseX, int mouseY, int x) {
        return x;
    }

    /** Touche propre au planificateur ; true si traitee. */
    protected boolean keyExtra(int key, boolean ctrl, boolean shift) {
        return false;
    }

    /** Le volume se montre-t-il avec ses poignees ? */
    protected boolean showsSelection() {
        return selMin() != null && selMax() != null;
    }

    protected boolean handlesActive() {
        return SELECT.equals(tool) && corner == null && hasSelection() && showsSelection();
    }

    protected boolean hasSelection() {
        return selMin() != null && selMax() != null;
    }

    protected Vec3 selectionCenter() {
        BlockPos min = selMin(), max = selMax();
        return new Vec3((min.getX() + max.getX() + 1) / 2.0, (min.getY() + max.getY() + 1) / 2.0, (min.getZ() + max.getZ() + 1) / 2.0);
    }

    // ------------------------------------------------------------------
    // Cycle de vie
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        super.init();
        left = 0;
        top = 0;
        panelWidth = width;
        panelHeight = height;
        if (!active) {
            enter();
        }
    }

    public boolean isActive() {
        return active;
    }

    private void enter() {
        ClientLevel level = minecraft.level;
        if (level == null || active) {
            return;
        }
        camera = new Marker(EntityType.MARKER, level);
        previousCamera = minecraft.getCameraEntity();
        previousHideGui = minecraft.options.hideGui;
        previousCameraType = minecraft.options.getCameraType();
        minecraft.options.hideGui = true;
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        active = true;
        //Retour d'un sous-ecran (choix d'objet...) : la camera reste ou elle etait
        if (camPos == Vec3.ZERO) {
            frame(false);
        }
        placeCamera();
        minecraft.setCameraEntity(camera);
    }

    private void exit() {
        if (!active) {
            return;
        }
        active = false;
        minecraft.setCameraEntity(previousCamera != null && previousCamera.isAlive() ? previousCamera : minecraft.player);
        minecraft.options.hideGui = previousHideGui;
        if (previousCameraType != null) {
            minecraft.options.setCameraType(previousCameraType);
        }
        camera = null;
    }

    @Override
    public void removed() {
        super.removed();
        exit();
    }

    @Override
    public void onClose() {
        if (corner != null) {
            cancelCorner();
            return;
        }
        cancel();
    }

    protected void cancel() {
        exit();
        minecraft.setScreen(back);
    }

    /** Ferme le planificateur (apres un enregistrement). */
    protected void close() {
        exit();
        minecraft.setScreen(back);
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        //Etat des ordres a jour (pourquoi une zone attend)
        if (ticks % 40 == 0) {
            ClientRobotics.request();
        }
    }

    // ------------------------------------------------------------------
    // Camera
    // ------------------------------------------------------------------

    /** Cadre le volume (ou le point de depart) depuis le ciel, en biais (ou a la verticale). */
    protected void frame(boolean top) {
        Vec3 target = hasSelection() ? selectionCenter() : focus;
        double extent = hasSelection() ? Math.max(selMax().getX() - selMin().getX(), selMax().getZ() - selMin().getZ()) + 1 : 16;
        double distance = extent * 1.15 + 10;
        if (top) {
            camYaw = 180;
            camPitch = 89.9F;
        } else if (camPitch > 85 || camPos == Vec3.ZERO) {
            camYaw = minecraft.player == null ? 180 : minecraft.player.getYRot();
            camPitch = 50;
        }
        camPos = target.subtract(look().scale(distance));
        clampCamera();
    }

    protected void toggleTop() {
        if (camPitch > 85) {
            camPitch = 50;
            frame(false);
        } else {
            frame(true);
        }
    }

    /** Direction du regard. */
    protected Vec3 look() {
        float yaw = camYaw * Mth.DEG_TO_RAD, pitch = camPitch * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
    }

    /** La camera reste la ou le client a le monde (autour du joueur). */
    private void clampCamera() {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        double radius = Math.max(32, minecraft.options.getEffectiveRenderDistance() * 16 - 12);
        Vec3 player = minecraft.player.position();
        double dx = camPos.x - player.x, dz = camPos.z - player.z, d = Math.sqrt(dx * dx + dz * dz);
        if (d > radius) {
            camPos = new Vec3(player.x + dx / d * radius, camPos.y, player.z + dz / d * radius);
        }
        camPos = new Vec3(camPos.x, Mth.clamp(camPos.y, minecraft.level.getMinBuildHeight() + 1, minecraft.level.getMaxBuildHeight() + 64), camPos.z);
    }

    private void placeCamera() {
        if (camera == null) {
            return;
        }
        camera.setPos(camPos.x, camPos.y, camPos.z);
        camera.xo = camera.xOld = camPos.x;
        camera.yo = camera.yOld = camPos.y;
        camera.zo = camera.zOld = camPos.z;
        camera.setYRot(camYaw);
        camera.setXRot(camPitch);
        camera.yRotO = camYaw;
        camera.xRotO = camPitch;
    }

    /** Deplacement au clavier (ZQSD/WASD, espace, Maj ; Ctrl : plus vite), d'une image a l'autre. */
    private void flyStep(float dt) {
        long window = minecraft.getWindow().getWindow();
        double forward = 0, strafe = 0, up = 0;
        if (key(window, minecraft.options.keyUp)) {
            forward++;
        }
        if (key(window, minecraft.options.keyDown)) {
            forward--;
        }
        if (key(window, minecraft.options.keyLeft)) {
            strafe++;
        }
        if (key(window, minecraft.options.keyRight)) {
            strafe--;
        }
        if (key(window, minecraft.options.keyJump)) {
            up++;
        }
        //Maj descend, sauf dans le panneau (pas de 8) ou pendant un glisser
        if (key(window, minecraft.options.keyShift) && drag == Drag.NONE && mouseInView) {
            up--;
        }
        if (forward == 0 && strafe == 0 && up == 0 || getFocused() != null) {
            return;
        }
        float yaw = camYaw * Mth.DEG_TO_RAD;
        Vec3 ahead = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        double speed = flySpeed * (hasControlDown() ? 3 : 1) * dt;
        camPos = camPos.add(ahead.scale(forward * speed)).add(left.scale(strafe * speed)).add(0, up * speed, 0);
        clampCamera();
    }

    private static boolean key(long window, KeyMapping mapping) {
        return mapping.getKey().getType() == InputConstants.Type.KEYSYM
               && InputConstants.isKeyDown(window, mapping.getKey().getValue());
    }

    /** Taille d'un pixel de l'ecran en blocs, a un endroit de la scene (poignees de taille constante a l'ecran). */
    protected double pixel(Vec3 at) {
        double fov = minecraft.options.fov().get() * Mth.DEG_TO_RAD;
        return Math.max(0.01, at.distanceTo(camPos)) * 2 * Math.tan(fov / 2) / Math.max(1, height);
    }

    // ------------------------------------------------------------------
    // Projection et visee
    // ------------------------------------------------------------------

    /** Point du monde a l'ecran ; z = proximite (negatif : derriere la camera). */
    protected Vector3f project(Vec3 world) {
        Vec3 rel = world.subtract(camPos), f = look();
        float yaw = camYaw * Mth.DEG_TO_RAD;
        Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        Vec3 up = left.cross(f).scale(-1);
        double depth = rel.dot(f);
        if (depth < 0.05) {
            return new Vector3f(-10000, -10000, -1);
        }
        double t = Math.tan(minecraft.options.fov().get() * Mth.DEG_TO_RAD / 2), aspect = width / (double) Math.max(1, height);
        double sx = width / 2.0 + (-rel.dot(left) / depth) / (t * aspect) * width / 2.0, sy = height / 2.0 - (rel.dot(up) / depth) / t * height / 2.0;
        return new Vector3f((float) sx, (float) sy, (float) (10000 - depth));
    }

    /** Rayon de la souris dans le monde (de la camera vers le fond). */
    protected Vec3[] ray(double mouseX, double mouseY) {
        double t = Math.tan(minecraft.options.fov().get() * Mth.DEG_TO_RAD / 2), aspect = width / (double) Math.max(1, height);
        double nx = 2 * mouseX / width - 1, ny = 1 - 2 * mouseY / height;
        Vec3 f = look();
        float yaw = camYaw * Mth.DEG_TO_RAD;
        Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        Vec3 up = left.cross(f).scale(-1);
        Vec3 dir = f.add(left.scale(-nx * t * aspect)).add(up.scale(ny * t)).normalize();
        return new Vec3[]{camPos, camPos.add(dir.scale(320))};
    }

    /** Bloc sous la souris (le vrai monde), ou null. */
    @Nullable
    protected Hit pick(double mouseX, double mouseY) {
        Vec3[] ray = ray(mouseX, mouseY);
        if (minecraft.level == null || camera == null) {
            return null;
        }
        BlockHitResult hit = minecraft.level.clip(new ClipContext(ray[0], ray[1], ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, camera));
        return hit.getType() == HitResult.Type.BLOCK ? new Hit(hit.getBlockPos(), hit.getDirection(), hit.getLocation()) : null;
    }

    /** Intersection du rayon de la souris avec le plan horizontal y. */
    @Nullable
    protected Vec3 planeHit(double mouseX, double mouseY, double y) {
        Vec3[] ray = ray(mouseX, mouseY);
        Vec3 d = ray[1].subtract(ray[0]);
        if (Math.abs(d.y) < 1e-6) {
            return null;
        }
        double t = (y - ray[0].y) / d.y;
        return t < 0 || t > 1 ? null : ray[0].add(d.scale(t));
    }

    /** Vecteur du monde correspondant a un deplacement a l'ecran (pixels). */
    private Vec3 screenToWorld(double dx, double dy) {
        float yaw = camYaw * Mth.DEG_TO_RAD;
        Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        Vec3 up = left.cross(look()).scale(-1);
        double k = pixel(camPos.add(look().scale(24)));
        return left.scale(dx * k).add(up.scale(-dy * k));
    }

    /** Hauteur du sol d'une colonne : le monde charge, sinon la carte robotique. */
    protected int groundAt(int x, int z) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return Integer.MIN_VALUE;
        }
        if (level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
            return surface(level, x, z);
        }
        return ClientRoboticsMap.height(x, z);
    }

    /**
     * Sol d'une colonne chargee : le client n'a pas la carte des hauteurs « sans feuillage » (elle renvoie le fond du monde), on part
     * donc du plus haut bloc bloquant et on descend sous le feuillage, l'herbe et les fleurs.
     */
    static int surface(ClientLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
        for (int i = 0; i < 48 && pos.getY() > level.getMinBuildHeight(); i++) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.is(BlockTags.LEAVES) && !state.canBeReplaced()) {
                break;
            }
            pos.move(0, -1, 0);
        }
        return pos.getY();
    }

    protected boolean inView(double mouseX, double mouseY) {
        return mouseX >= TOOLS && mouseX < width - SIDE && mouseY >= TOP && mouseY < height - BOTTOM;
    }

    // ------------------------------------------------------------------
    // Selection
    // ------------------------------------------------------------------

    protected void remember() {
        undo.push(snapshot());
        if (undo.size() > 64) {
            undo.removeLast();
        }
        redo.clear();
    }

    protected void undo() {
        if (!undo.isEmpty()) {
            redo.push(snapshot());
            restore(undo.pop());
            corner = null;
        }
    }

    protected void redo() {
        if (!redo.isEmpty()) {
            undo.push(snapshot());
            restore(redo.pop());
            corner = null;
        }
    }

    private void cancelCorner() {
        corner = null;
        undo();
        redo.clear();
    }

    /** Rectangle d'un coin a l'autre, hauteurs selon le relief dessous. */
    private void applyDraw(BlockPos start, BlockPos end) {
        int x0 = Math.min(start.getX(), end.getX()), x1 = Math.max(start.getX(), end.getX());
        int z0 = Math.min(start.getZ(), end.getZ()), z1 = Math.max(start.getZ(), end.getZ());
        int spanX = maxSpan(0), spanZ = maxSpan(2);
        if (end.getX() < start.getX()) {
            x0 = Math.max(x0, x1 - spanX + 1);
        } else {
            x1 = Math.min(x1, x0 + spanX - 1);
        }
        if (end.getZ() < start.getZ()) {
            z0 = Math.max(z0, z1 - spanZ + 1);
        } else {
            z1 = Math.min(z1, z0 + spanZ - 1);
        }
        int low = Math.min(start.getY(), end.getY()), high = Math.max(start.getY(), end.getY());
        int stepX = Math.max(1, (x1 - x0) / 24), stepZ = Math.max(1, (z1 - z0) / 24);
        for (int x = x0; x <= x1; x += stepX) {
            for (int z = z0; z <= z1; z += stepZ) {
                int ground = groundAt(x, z);
                if (ground != Integer.MIN_VALUE) {
                    low = Math.min(low, ground);
                    high = Math.max(high, ground);
                }
            }
        }
        high = Math.min(high, low + maxSpan(1) - 1);
        selectionDrawn(x0, z0, x1, z1, low, high);
    }

    private void dragHandle(double mouseX, double mouseY) {
        if (dragged == null || dragMin == null || dragMax == null) {
            return;
        }
        if (dragged == Handle.MOVE) {
            if (hasShiftDown()) {
                int d = axisDelta(1, handlePoint(Handle.UP), mouseX, mouseY);
                setSelection(dragMin.offset(0, d, 0), dragMax.offset(0, d, 0));
                return;
            }
            Vec3 now = planeHit(mouseX, mouseY, dragMax.getY() + 1);
            if (now != null && movePlane != null) {
                int dx = (int) Math.round(now.x - movePlane.x), dz = (int) Math.round(now.z - movePlane.z);
                setSelection(dragMin.offset(dx, 0, dz), dragMax.offset(dx, 0, dz));
            }
            return;
        }
        int d = axisDelta(dragged.axis, handlePoint(dragged), mouseX, mouseY);
        int[] lo = {dragMin.getX(), dragMin.getY(), dragMin.getZ()}, hi = {dragMax.getX(), dragMax.getY(), dragMax.getZ()};
        int a = dragged.axis, limit = maxSpan(a);
        if (dragged.sign < 0) {
            lo[a] = Mth.clamp(lo[a] + d, hi[a] - limit + 1, hi[a]);
        } else {
            hi[a] = Mth.clamp(hi[a] + d, lo[a], lo[a] + limit - 1);
        }
        setSelection(new BlockPos(lo[0], lo[1], lo[2]), new BlockPos(hi[0], hi[1], hi[2]));
    }

    /** Deplacement de la souris depuis le debut du glisser, projete sur un axe du monde (en blocs). */
    private int axisDelta(int axis, Vec3 at, double mouseX, double mouseY) {
        Vector3f p0 = project(at), p1 = project(at.add(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0));
        double ax = p1.x - p0.x, ay = p1.y - p0.y;
        double len2 = ax * ax + ay * ay;
        if (len2 < 1e-4) {
            return 0;
        }
        return (int) Math.round(((mouseX - startX) * ax + (mouseY - startY) * ay) / len2);
    }

    /** Ou se trouve une poignee (monde). */
    protected Vec3 handlePoint(Handle handle) {
        AABB box = new AABB(selMin(), selMax().offset(1, 1, 1));
        Vec3 c = box.getCenter();
        double out = 10 * pixel(c);
        return switch (handle) {
            case WEST -> new Vec3(box.minX - out, c.y, c.z);
            case EAST -> new Vec3(box.maxX + out, c.y, c.z);
            case DOWN -> new Vec3(c.x, box.minY - out, c.z);
            case UP -> new Vec3(c.x, box.maxY + out, c.z);
            case NORTH -> new Vec3(c.x, c.y, box.minZ - out);
            case SOUTH -> new Vec3(c.x, c.y, box.maxZ + out);
            case MOVE -> new Vec3(c.x, box.maxY + 0.02, c.z);
        };
    }

    private Vec3 faceCenter(Handle handle) {
        AABB box = new AABB(selMin(), selMax().offset(1, 1, 1));
        Vec3 c = box.getCenter();
        return switch (handle) {
            case WEST -> new Vec3(box.minX, c.y, c.z);
            case EAST -> new Vec3(box.maxX, c.y, c.z);
            case DOWN -> new Vec3(c.x, box.minY, c.z);
            case UP -> new Vec3(c.x, box.maxY, c.z);
            case NORTH -> new Vec3(c.x, c.y, box.minZ);
            case SOUTH -> new Vec3(c.x, c.y, box.maxZ);
            case MOVE -> c;
        };
    }

    @Nullable
    private Handle handleAt(double mouseX, double mouseY) {
        if (!handlesActive()) {
            return null;
        }
        Handle best = null;
        double bestDistance = 64;
        for (Handle handle : Handle.values()) {
            Vector3f p = project(handlePoint(handle));
            double radius = handle == Handle.MOVE ? 9 : 7;
            double d = (p.x - mouseX) * (p.x - mouseX) + (p.y - mouseY) * (p.y - mouseY);
            if (d < radius * radius && d < bestDistance) {
                best = handle;
                bestDistance = d;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Rendu
    // ------------------------------------------------------------------

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1e9F);
        lastFrame = now;
        mouseInView = inView(mouseX, mouseY);
        flyStep(dt);
        placeCamera();
        if (mouseInView && drag == Drag.NONE) {
            hovered = handleAt(mouseX, mouseY);
            hover = hovered == null ? pick(mouseX, mouseY) : null;
            //Selection en deux clics : le rectangle suit la souris jusqu'au second coin
            if (corner != null && hover != null) {
                applyDraw(corner, hover.pos());
            }
        } else if (!mouseInView) {
            hovered = null;
            hover = null;
        }
        beginClip(graphics, TOOLS, TOP, width - SIDE, height - BOTTOM);
        if (hasSelection() && showsSelection()) {
            BlockPos min = selMin(), max = selMax();
            AABB box = new AABB(min, max.offset(1, 1, 1));
            label(graphics, new Vec3(box.getCenter().x, box.minY, edgeZ(box)), Integer.toString(max.getX() - min.getX() + 1), axisColor(0));
            label(graphics, new Vec3(edgeX(box), box.minY, box.getCenter().z), Integer.toString(max.getZ() - min.getZ() + 1), axisColor(2));
            label(graphics, new Vec3(edgeX(box), box.getCenter().y, edgeZ(box)), Integer.toString(max.getY() - min.getY() + 1), axisColor(1));
        }
        drawOverlayExtra(graphics, mouseX, mouseY);
        compass(graphics, TOOLS + 22, TOP + 22);
        if (corner != null) {
            banner(graphics, Component.translatable("robotics.pioneers.planner.corner_hint"), PURPLE);
        }
        endClip(graphics);
        drawTopBar(graphics, mouseX, mouseY);
        drawToolbar(graphics, mouseX, mouseY);
        drawStatusBar(graphics);
        drawSidePanel(graphics, mouseX, mouseY);
    }

    /** Message centre en haut de la vue. */
    protected void banner(GuiGraphics graphics, Component text, int color) {
        //A droite de la boussole (TOOLS + 7 .. TOOLS + 37), pour ne jamais la recouvrir
        int vx = TOOLS + 42, vw = width - SIDE - vx;
        int w = Math.min(vw - 20, (int) (font.width(text) * 0.8F) + 16);
        graphics.fill(vx + (vw - w) / 2, TOP + 10, vx + (vw + w) / 2, TOP + 26, 0xC0101820);
        graphics.renderOutline(vx + (vw - w) / 2, TOP + 10, w, 16, 0xFF000000 | color);
        GuiDraw.center(graphics, font, text, vx + vw / 2F, TOP + 14, 0.8F, 0xFFE8F0FF, w - 6);
    }

    /**
     * Appele pendant le rendu du monde : case visee, volume, extras de la sous-classe, puis poignees et cadre visibles a travers le
     * terrain.
     */
    public void renderWorld(PoseStack pose, Vec3 cameraPos) {
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        renderExtra(pose, buffers, cameraPos);
        buffers.endBatch();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        if (hover != null && drag != Drag.LOOK && drag != Drag.PAN) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(hover.pos()).inflate(0.005), 1, 1, 1, 0.9F);
        }
        buffers.endBatch(RenderType.lines());
        if (hasSelection() && showsSelection()) {
            drawSelection(pose, buffers, false);
            buffers.endBatch();
            drawSelection(pose, buffers, true);
            if (handlesActive()) {
                drawHandles(pose, buffers);
            }
            buffers.endBatch();
        }
        pose.popPose();
    }

    /** Volume : voile translucide et cadre ; en rayons X, un cadre pale. */
    private void drawSelection(PoseStack pose, MultiBufferSource.BufferSource buffers, boolean xray) {
        AABB box = new AABB(selMin(), selMax().offset(1, 1, 1));
        int c = drag == Drag.SELECT || corner != null ? PURPLE : selectionColor();
        float r = (c >> 16 & 255) / 255F, g = (c >> 8 & 255) / 255F, b = (c & 255) / 255F;
        if (!xray) {
            VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
            float alpha = drag == Drag.SELECT || corner != null ? 0.22F : 0.1F;
            box(quads, pose.last().pose(), box.inflate(0.01), r, g, b, alpha);
            buffers.endBatch(RenderType.debugQuads());
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            LevelRenderer.renderLineBox(pose, lines, box.inflate(0.02), r, g, b, 1);
            //Quadrillage du dessus : un trait par bloc
            if (box.getXsize() <= 48 && box.getZsize() <= 48) {
                Matrix4f lm = pose.last().pose();
                var normal = pose.last().normal();
                float y = (float) box.maxY + 0.02F;
                for (int x = (int) box.minX + 1; x < box.maxX; x++) {
                    lines.vertex(lm, x, y, (float) box.minZ).color(r, g, b, 0.35F).normal(normal, 0, 0, 1).endVertex();
                    lines.vertex(lm, x, y, (float) box.maxZ).color(r, g, b, 0.35F).normal(normal, 0, 0, 1).endVertex();
                }
                for (int z = (int) box.minZ + 1; z < box.maxZ; z++) {
                    lines.vertex(lm, (float) box.minX, y, z).color(r, g, b, 0.35F).normal(normal, 1, 0, 0).endVertex();
                    lines.vertex(lm, (float) box.maxX, y, z).color(r, g, b, 0.35F).normal(normal, 1, 0, 0).endVertex();
                }
            }
            buffers.endBatch(RenderType.lines());
        } else {
            VertexConsumer lines = buffers.getBuffer(XRayTypes.LINES);
            LevelRenderer.renderLineBox(pose, lines, box.inflate(0.02), r, g, b, 0.3F);
            buffers.endBatch(XRayTypes.LINES);
        }
    }

    /** Poignees : un cube par face (couleur de l'axe) au bout d'une tige, et un carre central pour deplacer. */
    private void drawHandles(PoseStack pose, MultiBufferSource.BufferSource buffers) {
        Matrix4f m = pose.last().pose();
        VertexConsumer lines = buffers.getBuffer(XRayTypes.LINES);
        var normal = pose.last().normal();
        for (Handle handle : Handle.values()) {
            if (handle == Handle.MOVE) {
                continue;
            }
            Vec3 a = faceCenter(handle), b = handlePoint(handle);
            int c = axisColor(handle.axis);
            float r = (c >> 16 & 255) / 255F, g = (c >> 8 & 255) / 255F, bl = (c & 255) / 255F;
            Vec3 d = b.subtract(a).normalize();
            lines.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(r, g, bl, 1).normal(normal, (float) d.x, (float) d.y, (float) d.z).endVertex();
            lines.vertex(m, (float) b.x, (float) b.y, (float) b.z).color(r, g, bl, 1).normal(normal, (float) d.x, (float) d.y, (float) d.z).endVertex();
        }
        buffers.endBatch(XRayTypes.LINES);
        VertexConsumer quads = buffers.getBuffer(XRayTypes.QUADS);
        for (Handle handle : Handle.values()) {
            Vec3 p = handlePoint(handle);
            float size = (float) (4.5 * pixel(p));
            boolean hot = handle == hovered || handle == dragged;
            if (handle == Handle.MOVE) {
                float s = (float) (7 * pixel(p));
                float y = (float) p.y + 0.01F;
                float k = hot ? 1F : 0.8F;
                face(quads, m, (float) p.x - s, y, (float) p.z - s, (float) p.x - s, y, (float) p.z + s, (float) p.x + s, y, (float) p.z + s, (float) p.x + s, y,
                      (float) p.z - s, k, 0.84F * k, 0.3F * k, hot ? 0.95F : 0.75F);
                continue;
            }
            int c = hot ? 0xFFFFFF : axisColor(handle.axis);
            float s = hot ? size * 1.35F : size;
            box(quads, m, new AABB(p.x - s, p.y - s, p.z - s, p.x + s, p.y + s, p.z + s), (c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F, 1F);
        }
        buffers.endBatch(XRayTypes.QUADS);
    }

    protected static int axisColor(int axis) {
        return axis == 0 ? 0xFF5A5A : axis == 1 ? 0x6EE06E : 0x5AA0FF;
    }

    protected static void box(VertexConsumer quads, Matrix4f m, AABB b, float r, float g, float bl, float a) {
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ, x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        face(quads, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, bl, a);
        face(quads, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r * 0.6F, g * 0.6F, bl * 0.6F, a);
        face(quads, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r * 0.85F, g * 0.85F, bl * 0.85F, a);
        face(quads, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r * 0.85F, g * 0.85F, bl * 0.85F, a);
        face(quads, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r * 0.72F, g * 0.72F, bl * 0.72F, a);
        face(quads, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r * 0.72F, g * 0.72F, bl * 0.72F, a);
    }

    /** Quadrilatere visible des deux cotes. */
    protected static void face(VertexConsumer q, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
          float dx, float dy, float dz, float r, float g, float b, float a) {
        q.vertex(m, ax, ay, az).color(r, g, b, a).endVertex();
        q.vertex(m, bx, by, bz).color(r, g, b, a).endVertex();
        q.vertex(m, cx, cy, cz).color(r, g, b, a).endVertex();
        q.vertex(m, dx, dy, dz).color(r, g, b, a).endVertex();
        q.vertex(m, dx, dy, dz).color(r, g, b, a).endVertex();
        q.vertex(m, cx, cy, cz).color(r, g, b, a).endVertex();
        q.vertex(m, bx, by, bz).color(r, g, b, a).endVertex();
        q.vertex(m, ax, ay, az).color(r, g, b, a).endVertex();
    }

    /** Arete avant du volume (la plus proche de la camera) pour y poser les cotes. */
    private double edgeZ(AABB box) {
        Vector3f a = project(new Vec3(box.getCenter().x, box.minY, box.minZ)), b = project(new Vec3(box.getCenter().x, box.minY, box.maxZ));
        return a.z > b.z ? box.minZ : box.maxZ;
    }

    private double edgeX(AABB box) {
        Vector3f a = project(new Vec3(box.minX, box.minY, box.getCenter().z)), b = project(new Vec3(box.maxX, box.minY, box.getCenter().z));
        return a.z > b.z ? box.minX : box.maxX;
    }

    protected void label(GuiGraphics graphics, Vec3 at, String text, int color) {
        Vector3f p = project(at);
        if (p.z < 0) {
            return;
        }
        pill(graphics, Component.literal(text), p.x, p.y, 0xD0101418, 0xFF000000 | color);
    }

    /** Etiquette a fond sombre centree sur un point de l'ecran. */
    protected void pill(GuiGraphics graphics, Component text, float x, float y, int background, int color) {
        float scale = 0.7F;
        int w = (int) (font.width(text) * scale) + 6;
        int x0 = Math.round(x - w / 2F), y0 = Math.round(y - 5);
        graphics.fill(x0, y0, x0 + w, y0 + 10, background);
        graphics.fill(x0, y0 + 9, x0 + w, y0 + 10, color | 0xFF000000);
        GuiDraw.center(graphics, font, text, x, y0 + 2, scale, background == 0xD0101418 ? color : 0xFF101418, w);
    }

    /** Boussole : le nord tourne avec la vue. */
    private void compass(GuiGraphics graphics, int cx, int cy) {
        float angle = (180 - camYaw) * Mth.DEG_TO_RAD;
        graphics.fill(cx - 15, cy - 15, cx + 15, cy + 15, 0x90101418);
        graphics.renderOutline(cx - 15, cy - 15, 30, 30, 0xFF2C3640);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotation(angle));
        graphics.fill(-1, -11, 1, 0, 0xFFFF5A5A);
        graphics.fill(-1, 0, 1, 11, 0xFF8FA3A0);
        graphics.fill(-3, -9, 3, -7, 0xFFFF5A5A);
        pose.translate(0, -13, 0);
        pose.mulPose(Axis.ZP.rotation(-angle));
        GuiDraw.center(graphics, font, Component.translatable("robotics.pioneers.planner.north"), 0, -3, 0.6F, 0xFFFFFFFF, 10);
        pose.popPose();
    }

    // ------------------------------------------------------------------
    // Barres et panneau
    // ------------------------------------------------------------------

    private void drawTopBar(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, TOP, D_BAR);
        graphics.fill(0, TOP - 1, width, TOP, 0xFF3FD0A0);
        graphics.drawString(font, plannerTitle, 8, 8, D_TEXT, false);
        int x = 8 + font.width(plannerTitle) + 12;
        x = topButton(graphics, mouseX, mouseY, x, "frame", "F", false, () -> frame(false));
        x = topButton(graphics, mouseX, mouseY, x, "top", "T", camPitch > 85, this::toggleTop);
        x += 6;
        button(graphics, mouseX, mouseY, x, 5, 16, 14, Component.literal("↶"), !undo.isEmpty(), this::undo);
        tooltip(mouseX, mouseY, x, 5, 16, 14, Component.translatable("robotics.pioneers.planner.undo"));
        button(graphics, mouseX, mouseY, x + 17, 5, 16, 14, Component.literal("↷"), !redo.isEmpty(), this::redo);
        tooltip(mouseX, mouseY, x + 17, 5, 16, 14, Component.translatable("robotics.pioneers.planner.redo"));
        x += 40;
        drawTopBarExtra(graphics, mouseX, mouseY, x);
    }

    protected int topButton(GuiGraphics graphics, int mouseX, int mouseY, int x, String key, String shortcut, boolean on, Runnable action) {
        Component label = Component.translatable("robotics.pioneers.planner.view." + key);
        int w = (int) (font.width(label) * 0.75F) + 12;
        chip(graphics, mouseX, mouseY, x, 5, w, 14, label, on, 0x3A6FA0, action);
        tooltip(mouseX, mouseY, x, 5, w, 14, Component.translatable("robotics.pioneers.planner.view." + key + ".tooltip"),
              Component.translatable("robotics.pioneers.planner.key", shortcut).withStyle(ChatFormatting.DARK_GRAY));
        return x + w + 2;
    }

    private void drawToolbar(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, TOP, TOOLS, height - BOTTOM, D_PANEL);
        graphics.fill(TOOLS - 1, TOP, TOOLS, height - BOTTOM, D_LINE);
        List<ToolDef> list = tools();
        for (int i = 0; i < list.size(); i++) {
            ToolDef t = list.get(i);
            int y = TOP + 5 + i * 22;
            chip(graphics, mouseX, mouseY, 4, y, 18, 18, Component.literal(t.glyph()), t.id().equals(tool), t.color(), () -> selectTool(t.id()));
            tooltip(mouseX, mouseY, 4, y, 18, 18, Component.translatable("robotics.pioneers.planner.tool." + t.id()),
                  Component.translatable("robotics.pioneers.planner.tool." + t.id() + ".tooltip").withStyle(ChatFormatting.GRAY),
                  Component.translatable("robotics.pioneers.planner.key", t.keyName()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    protected void selectTool(String id) {
        if (corner != null) {
            cancelCorner();
        }
        tool = id;
    }

    private void drawStatusBar(GuiGraphics graphics) {
        int y = height - BOTTOM;
        graphics.fill(0, y, width - SIDE, height, D_BAR);
        graphics.fill(0, y, width - SIDE, y + 1, D_LINE);
        Component hint = switch (drag) {
            case SELECT -> Component.translatable("robotics.pioneers.planner.hint.drawing");
            case HANDLE, MOVE -> Component.translatable("robotics.pioneers.planner.hint.dragging");
            default -> hint();
        };
        int infoW = 0;
        if (hover != null && minecraft.level != null) {
            BlockPos pos = hover.pos();
            BlockState state = minecraft.level.getBlockState(pos);
            Component info = Component.translatable("robotics.pioneers.planner.hover", state.getBlock().getName(), pos.toShortString());
            infoW = (int) (font.width(info) * 0.62F) + 8;
            GuiDraw.right(graphics, font, info, width - SIDE - 6, y + 4, 0.62F, D_DIM);
        }
        GuiDraw.text(graphics, font, hint, 6, y + 4, 0.62F, D_DIM, Math.max(1, width - SIDE - infoW - 14));
    }

    private void drawSidePanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int x0 = width - SIDE, x = x0 + 7, w = SIDE - 14;
        graphics.fill(x0, TOP, width, height, D_PANEL);
        graphics.fill(x0, TOP, x0 + 1, height, 0xFF3FD0A0);
        int top = TOP + 4, bottom = height - FOOTER;
        beginClip(graphics, x0 + 1, top, width, bottom);
        int y = drawPanel(graphics, mouseX, mouseY, x, top + 2 - panelScroll, w);
        panelContent = y + panelScroll - top;
        endClip(graphics);
        int max = Math.max(0, panelContent - (bottom - top));
        panelScroll = Mth.clamp(panelScroll, 0, max);
        if (max > 0) {
            int track = bottom - top - 4, thumb = Math.max(12, track * (bottom - top) / Math.max(1, panelContent));
            int ty = top + 2 + (track - thumb) * panelScroll / max;
            graphics.fill(width - 3, ty, width - 1, ty + thumb, 0xFF3C4A57);
        }
        int fy = height - FOOTER;
        graphics.fill(x0 + 1, fy, width, height, D_BAR);
        graphics.fill(x0 + 1, fy, width, fy + 1, D_LINE);
        drawFooter(graphics, mouseX, mouseY, x, fy + 5, w);
    }

    // ------------------------------------------------------------------
    // Souris et clavier
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (!inView(mouseX, mouseY)) {
            return false;
        }
        lastX = startX = mouseX;
        lastY = startY = mouseY;
        if (button == 1 || button == 2) {
            drag = button == 2 || hasShiftDown() ? Drag.PAN : Drag.LOOK;
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (!SELECT.equals(tool)) {
            if (toolClick(pick(mouseX, mouseY), mouseX, mouseY)) {
                drag = Drag.TOOL;
            }
            return true;
        }
        Handle handle = handleAt(mouseX, mouseY);
        if (handle != null) {
            remember();
            dragged = handle;
            dragMin = selMin();
            dragMax = selMax();
            drag = handle == Handle.MOVE ? Drag.MOVE : Drag.HANDLE;
            movePlane = handle == Handle.MOVE ? planeHit(mouseX, mouseY, dragMax.getY() + 1) : null;
            return true;
        }
        Hit hit = pick(mouseX, mouseY);
        if (hit != null) {
            if (corner == null) {
                remember();
            }
            drawStart = corner != null ? corner : hit.pos();
            drawMoved = false;
            drag = Drag.SELECT;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        switch (drag) {
            case LOOK -> {
                camYaw += (float) (mouseX - lastX) * 0.3F;
                camPitch = Mth.clamp(camPitch + (float) (mouseY - lastY) * 0.3F, -89.9F, 89.9F);
            }
            case PAN -> {
                camPos = camPos.subtract(screenToWorld(mouseX - lastX, mouseY - lastY));
                clampCamera();
            }
            case SELECT -> {
                if (drawStart != null && (drawMoved || Math.abs(mouseX - startX) + Math.abs(mouseY - startY) > 5)) {
                    drawMoved = true;
                    corner = null;
                    Hit hit = pick(mouseX, mouseY);
                    Vec3 plane = planeHit(mouseX, mouseY, drawStart.getY() + 1);
                    BlockPos end = hit != null ? hit.pos() : plane != null ? BlockPos.containing(plane.x, drawStart.getY(), plane.z) : null;
                    if (end != null) {
                        applyDraw(drawStart, end);
                    }
                }
            }
            case HANDLE, MOVE -> dragHandle(mouseX, mouseY);
            case TOOL -> toolDrag(mouseX, mouseY);
            default -> {
                return super.mouseDragged(mouseX, mouseY, button, dx, dy);
            }
        }
        lastX = mouseX;
        lastY = mouseY;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1 && drag == Drag.LOOK && Math.abs(mouseX - startX) + Math.abs(mouseY - startY) < 4 && inView(mouseX, mouseY)) {
            //Clic droit sans tourner la vue : action de l'outil (ex. retirer un point de route)
            rightClick(pick(mouseX, mouseY), mouseX, mouseY);
        }
        if (drag == Drag.SELECT) {
            if (drawMoved) {
                selectionChanged();
            } else {
                //Un simple clic : premier coin, puis second coin
                Hit hit = pick(mouseX, mouseY);
                if (corner == null && hit != null) {
                    corner = hit.pos();
                    applyDraw(corner, corner);
                } else if (corner != null) {
                    applyDraw(corner, hit != null ? hit.pos() : corner);
                    corner = null;
                    selectionChanged();
                }
            }
            drawStart = null;
        } else if (drag == Drag.HANDLE || drag == Drag.MOVE) {
            selectionChanged();
        }
        drag = Drag.NONE;
        dragged = null;
        movePlane = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= width - SIDE) {
            panelScroll = Math.max(0, panelScroll - (int) Math.signum(delta) * 16);
            return true;
        }
        if (!inView(mouseX, mouseY)) {
            return false;
        }
        if (hasControlDown()) {
            flySpeed = Mth.clamp(flySpeed * (delta > 0 ? 1.25F : 0.8F), 3, 80);
            return true;
        }
        Vec3[] ray = ray(mouseX, mouseY);
        Vec3 dir = ray[1].subtract(ray[0]).normalize();
        Hit hit = pick(mouseX, mouseY);
        double distance = hit == null ? 24 : hit.point().distanceTo(camPos);
        double step = Mth.clamp(distance * 0.18, 1, 24) * Math.signum(delta);
        if (delta < 0 || distance > 3) {
            camPos = camPos.add(dir.scale(step));
            clampCamera();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (getFocused() != null && getFocused().keyPressed(key, scanCode, modifiers)) {
            return true;
        }
        boolean ctrl = hasControlDown(), shift = hasShiftDown();
        if (keyExtra(key, ctrl, shift)) {
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            if (shift) {
                redo();
            } else {
                undo();
            }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        if (!ctrl) {
            for (ToolDef t : tools()) {
                if (t.key() == key) {
                    selectTool(t.id());
                    return true;
                }
            }
            switch (key) {
                case GLFW.GLFW_KEY_F -> frame(false);
                case GLFW.GLFW_KEY_T -> toggleTop();
                default -> {
                }
            }
        }
        //Le deplacement se lit en continu (touches de deplacement du jeu) : on garde les autres touches pour nous
        return true;
    }
}

package fr.pioneers.client.robotics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.network.PacketWorks;
import fr.pioneers.network.PiNetwork;
import fr.pioneers.rail.RailConfigs;
import fr.pioneers.rail.track.TrackQuality;
import fr.pioneers.registries.PiRail;
import fr.pioneers.registries.PiRobotics;
import fr.pioneers.robotics.Resource;
import fr.pioneers.robotics.RobotTask;
import fr.pioneers.robotics.RoboticOrder;
import fr.pioneers.robotics.logistics.LogisticPoint;
import fr.pioneers.robotics.logistics.PointConfig;
import fr.pioneers.robotics.works.RailPlanner;
import fr.pioneers.robotics.works.RailWorks;
import fr.pioneers.robotics.works.RoadPlanner;
import fr.pioneers.robotics.works.WorksPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Planificateur de travaux, en vue libre : on compose un chantier par etapes (vider un volume, tracer une route, coller un plan copie)
 * et l'apercu montre a chaque etape le resultat (en rouge ce qui part, en fantome ce qui est pose). Une fois lance, les unites
 * l'executent dans le bon ordre : tous les retraits du haut vers le bas, puis toutes les poses du bas vers le haut, avec les materiaux
 * pris dans le reseau.
 */
public class WorksPlannerScreen extends FreeCamScreen {

    private static final String ROAD = "road", RAIL = "rail", PASTE = "paste";
    private static final List<ToolDef> TOOLS_LIST = List.of(new ToolDef(SELECT, "⬚", GLFW.GLFW_KEY_V, "V", 0x2E9E78),
          new ToolDef(ROAD, "⛙", GLFW.GLFW_KEY_R, "R", 0x8D6E63), new ToolDef(RAIL, "≡", GLFW.GLFW_KEY_G, "G", 0x7E8B99), new ToolDef(PASTE, "⎘", GLFW.GLFW_KEY_P, "P / Ctrl + V", PURPLE));

    private final RoboticOrder draft;
    private WorksPlan plan = new WorksPlan();
    @Nullable
    private BlockPos selA, selB;
    /** Ordre dont on attend le plan (modification), ou 0. */
    private int waitingFor;
    private boolean isNew;

    //Apercu
    private final PlanPreview preview = new PlanPreview();
    private boolean previewDirty = true, showPreview = true;
    private int hoveredOp = -1;

    //Route : points cliques, chemin calcule (cases ou l'on marche), largeur
    private final List<BlockPos> roadPoints = new ArrayList<>();
    private final List<BlockPos> roadPath = new ArrayList<>();
    private boolean roadBroken, roadComputing;
    private static int roadWidth = 1, roadRun = 2;
    /** 0.20.6 : voie ferree : qualite de la voie et pilier des viaducs. */
    private static TrackQuality railQuality = TrackQuality.BASIC;
    private static boolean roadFill = true;
    private static BlockState roadMaterial, fillMaterial;
    /** Calcul du trace en cours : chaque nouvelle demande abandonne la precedente. */
    private static final AtomicInteger ROAD_VERSION = new AtomicInteger();
    private static final ExecutorService ROAD_THREAD = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Pioneer's road planner");
        thread.setDaemon(true);
        return thread;
    });
    /** Apercu des blocs de la route (revetement, remblai), en coordonnees relatives a roadOrigin. */
    private final PlanPreview roadModel = new PlanPreview();
    private Map<BlockPos, BlockState> roadModelBlocks;
    private BlockPos roadOrigin;
    private boolean roadModelDirty;

    //Copier / coller
    @Nullable
    private static Blueprint clipboard;
    private static boolean pasteAir = true;
    /** La copie a ete modifiee depuis qu'elle a ete chargee ou rangee (gomme, retrait, remplacement). */
    private static boolean clipEdited;
    /** Gomme : clic sur un bloc du fantome epingle pour le retirer de la copie (glisser : plusieurs). */
    private boolean eraser;
    /** Cases du fantome (monde -> position relative dans la copie), et case visee par la gomme. */
    private Map<BlockPos, BlockPos> ghostCells = Map.of();
    @Nullable
    private BlockPos eraseHover;
    private final Set<BlockPos> erasedStroke = new HashSet<>();
    private EditBox planName;
    private int rotation;
    /** Collage epingle : coin bas du fantome, a ajuster au bloc pres avant de valider (null : il suit la souris). */
    @Nullable
    private BlockPos pasteAnchor;
    @Nullable
    private Map<BlockPos, BlockState> ghost;
    private Map<BlockPos, CompoundTag> ghostConfigs = Map.of();
    private List<CompoundTag> ghostZones = List.of();
    private List<AABB> ghostZoneBoxes = List.of();
    /** Fantome reel du collage (vrais modeles), tesselle par rotation ; coin bas du collage en cours. */
    private final PlanPreview pasteModel = new PlanPreview();
    @Nullable
    private Object pasteModelKey;
    @Nullable
    private BlockPos ghostOrigin;

    /** 0.20.46 (audit) : plan demande au serveur toujours attendu apres 1 s (demande perdue) : on la renvoie. */
    @Override
    public void tick() {
        super.tick();
        if (waitingFor != 0 && ++waitTicks % 20 == 0) {
            PiNetwork.sendToServer(new PacketWorks.Request(waitingFor));
        }
    }

    private int waitTicks;

    public WorksPlannerScreen(@Nullable Screen back, @Nullable RoboticOrder existing) {
        super(Component.translatable(existing == null ? "robotics.pioneers.works.title_new" : "robotics.pioneers.works.title_edit"), back);
        if (existing != null) {
            draft = existing.copy();
            waitingFor = existing.id;
            PiNetwork.sendToServer(new PacketWorks.Request(existing.id));
            if (existing.min != null && existing.max != null) {
                focus = new Vec3((existing.min.getX() + existing.max.getX() + 1) / 2.0, existing.max.getY(), (existing.min.getZ() + existing.max.getZ() + 1) / 2.0);
            }
        } else {
            draft = new RoboticOrder();
            draft.type = RoboticOrder.Type.ZONE;
            draft.task = RobotTask.BUILDER;
            draft.maxUnits = 4;
            isNew = true;
            if (Minecraft.getInstance().player != null) {
                focus = Minecraft.getInstance().player.position();
            }
        }
        ClientRobotics.request();
    }

    @Override
    protected void init() {
        super.init();
        planName = new EditBox(font, 0, 0, 100, 12, Component.translatable("robotics.pioneers.plans.name"));
        planName.setMaxLength(48);
        planName.setHint(Component.translatable("robotics.pioneers.plans.name.hint").withStyle(ChatFormatting.DARK_GRAY));
        planName.setVisible(false);
        addRenderableWidget(planName);
        syncPlanName();
    }

    public WorksPlannerScreen focusOn(Vec3 point) {
        focus = point;
        return this;
    }

    /** Le plan d'un ordre existant est arrive. */
    public void receive(int order, CompoundTag tag) {
        if (order != waitingFor || minecraft.level == null) {
            return;
        }
        waitingFor = 0;
        plan = WorksPlan.load(tag, WorksPlan.blocks(minecraft.level));
        previewDirty = true;
    }

    // ------------------------------------------------------------------
    // Volume de selection
    // ------------------------------------------------------------------

    @Override
    protected List<ToolDef> tools() {
        return TOOLS_LIST;
    }

    @Nullable
    @Override
    protected BlockPos selMin() {
        return selA;
    }

    @Nullable
    @Override
    protected BlockPos selMax() {
        return selB;
    }

    @Override
    protected void setSelection(@Nullable BlockPos a, @Nullable BlockPos b) {
        if (a == null || b == null) {
            selA = selB = null;
            return;
        }
        selA = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        selB = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    @Override
    protected int maxSpan(int axis) {
        return axis == 1 ? 64 : 96;
    }

    @Override
    protected int selectionColor() {
        return 0x4FC3F7;
    }

    @Override
    protected boolean showsSelection() {
        return SELECT.equals(tool);
    }

    @Override
    protected void selectionDrawn(int x0, int z0, int x1, int z1, int low, int high) {
        //Travaux : tout ce qui depasse du sol (au-dessus du point le plus bas), au moins 4 blocs de haut ; on descend sous terre en
        //tirant la face du bas (la partie souterraine est alors surlignee)
        int bottom = low + 1;
        setSelection(new BlockPos(x0, bottom, z0), new BlockPos(x1, Math.min(Math.max(high + 1, bottom + 3), bottom + 63), z1));
    }

    private record State(WorksPlan plan, @Nullable BlockPos a, @Nullable BlockPos b, List<BlockPos> road, @Nullable Blueprint clip, boolean clipEdited) {}

    @Override
    protected Object snapshot() {
        return new State(plan.copy(), selA, selB, new ArrayList<>(roadPoints), clipboard, clipEdited);
    }

    @Override
    protected void restore(Object state) {
        State s = (State) state;
        boolean changed = s.plan().ops.size() != plan.ops.size() || !s.plan().ops.equals(plan.ops);
        plan = s.plan().copy();
        selA = s.a();
        selB = s.b();
        roadPoints.clear();
        roadPoints.addAll(s.road());
        computeRoad();
        if (s.clip() != clipboard) {
            clipboard = s.clip();
            clipEdited = s.clipEdited();
            pasteModelKey = null;
            syncPlanName();
        }
        if (changed) {
            previewDirty = true;
        }
    }

    // ------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------

    private void addOp(WorksPlan.Op op) {
        if (plan.ops.size() >= WorksPlan.maxOps(minecraft.player)) {
            return;
        }
        remember();
        plan.ops.add(op);
        previewDirty = true;
    }

    private void removeOp(int index) {
        if (index >= 0 && index < plan.ops.size()) {
            remember();
            plan.ops.remove(index);
            previewDirty = true;
        }
    }

    /** Vider la selection. */
    private void clearSelection() {
        if (selA != null && selB != null) {
            addOp(WorksPlan.Op.clear(selA, selB));
        }
    }

    /** Etat d'une position une fois le plan actuel applique. */
    private BlockState after(BlockPos pos) {
        ClientLevel level = minecraft.level;
        return level == null ? Blocks.AIR.defaultBlockState() : plan.after(level, pos);
    }

    /** Copie la selection telle qu'elle sera apres les etapes deja prevues. */
    private void copy() {
        if (selA == null || selB == null) {
            return;
        }
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(selA, selB)) {
            BlockState state = after(pos);
            if (state.getBlock() instanceof LiquidBlock) {
                continue;
            }
            blocks.put(pos.subtract(selA), state);
            if (blocks.size() > WorksPlan.MAX_PLACED) {
                return;
            }
        }
        //Reglages des points logistiques (balises, coffres) : ceux du plan s'ils y sont poses, sinon ceux du monde
        Map<BlockPos, CompoundTag> configs = new LinkedHashMap<>();
        AABB box = new AABB(selA, selB.offset(1, 1, 1));
        for (BlockPos pos : BlockPos.betweenClosed(selA, selB)) {
            CompoundTag planned = plan.configAt(pos);
            if (planned != null) {
                configs.put(pos.subtract(selA), planned.copy());
            } else if (plan.target(pos) == null && minecraft.level != null
                       && minecraft.level.getBlockEntity(pos) instanceof LogisticPoint point) {
                configs.put(pos.subtract(selA), point.getConfig().save());
            }
            //0.20.7 : gares, postes de quai, plaques tournantes
            else if (plan.target(pos) == null && minecraft.level != null) {
                CompoundTag rail = RailConfigs.capture(minecraft.level, pos);
                if (rail != null) {
                    configs.put(pos.subtract(selA), rail);
                }
            }
        }
        //Zones de travail dont le centre est dans la selection (existantes, ou deja collees dans le plan)
        List<RoboticOrder> zones = new ArrayList<>();
        List<RoboticOrder> candidates = new ArrayList<>();
        for (RoboticOrder order : ClientRobotics.orders()) {
            if (order.type == RoboticOrder.Type.ZONE && order.task != RobotTask.BUILDER && order.min != null && order.max != null) {
                candidates.add(order);
            }
        }
        for (WorksPlan.Op op : plan.ops) {
            for (CompoundTag tag : op.zones) {
                candidates.add(RoboticOrder.load(tag));
            }
        }
        for (RoboticOrder order : candidates) {
            if (order.min != null && order.max != null && box.contains(new AABB(order.min, order.max.offset(1, 1, 1)).getCenter())) {
                RoboticOrder copy = order.copy();
                copy.id = 0;
                copy.min = order.min.subtract(selA);
                copy.max = order.max.subtract(selA);
                zones.add(copy);
            }
        }
        int cx = (selA.getX() + selB.getX()) / 2, cz = (selA.getZ() + selB.getZ()) / 2;
        int ground = groundAt(cx, cz);
        clipboard = new Blueprint(null, "", blocks, configs, zones, selB.getX() - selA.getX() + 1, selB.getY() - selA.getY() + 1, selB.getZ() - selA.getZ() + 1,
              ground == Integer.MIN_VALUE ? 0 : selA.getY() - ground);
        rotation = 0;
        clipEdited = false;
        syncPlanName();
        selectTool(PASTE);
    }

    /** Fantome du collage sous la souris (tourne, a la meme profondeur sous le sol que l'original). */
    private void updateGhost() {
        ghost = null;
        ghostOrigin = null;
        ghostConfigs = Map.of();
        ghostZones = List.of();
        ghostZoneBoxes = List.of();
        ghostCells = Map.of();
        if (!PASTE.equals(tool) || clipboard == null || hover == null && pasteAnchor == null) {
            return;
        }
        Blueprint print = clipboard;
        BlockPos origin = pasteAnchor != null ? pasteAnchor : originAt(hover.pos());
        Rotation rot = Rotation.values()[rotation & 3];
        ghostOrigin = origin;
        Map<BlockPos, BlockState> placed = new LinkedHashMap<>();
        Object key = List.of(System.identityHashCode(print), rotation, pasteAir);
        if (!key.equals(pasteModelKey)) {
            //Nouveau plan ou nouvelle rotation : on retesselle le fantome (en relatif)
            pasteModelKey = key;
            Map<BlockPos, BlockState> relative = new LinkedHashMap<>();
            for (Map.Entry<BlockPos, BlockState> entry : print.blocks().entrySet()) {
                if (!entry.getValue().isAir()) {
                    relative.put(rotate(entry.getKey(), rot, print.sx(), print.sz()), entry.getValue().rotate(rot));
                }
            }
            buildPasteModel = relative;
        }
        Map<BlockPos, BlockPos> cells = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : print.blocks().entrySet()) {
            BlockState state = entry.getValue();
            if (state.isAir() && !pasteAir) {
                continue;
            }
            BlockPos rel = rotate(entry.getKey(), rot, print.sx(), print.sz());
            BlockPos at = origin.offset(rel);
            placed.put(at, state.rotate(rot));
            if (!state.isAir()) {
                cells.put(at, entry.getKey());
            }
        }
        ghost = placed;
        ghostCells = cells;
        Map<BlockPos, CompoundTag> configs = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, CompoundTag> entry : print.configs().entrySet()) {
            configs.put(origin.offset(rotate(entry.getKey(), rot, print.sx(), print.sz())), rotateConfig(entry.getValue(), rot));
        }
        ghostConfigs = configs;
        List<CompoundTag> zones = new ArrayList<>();
        List<AABB> boxes = new ArrayList<>();
        for (RoboticOrder zone : print.zones()) {
            RoboticOrder copy = zone.copy();
            BlockPos a = origin.offset(rotate(zone.min, rot, print.sx(), print.sz())), b = origin.offset(rotate(zone.max, rot, print.sx(), print.sz()));
            copy.min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
            copy.max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
            zones.add(copy.save(false));
            boxes.add(new AABB(copy.min, copy.max.offset(1, 1, 1)));
        }
        ghostZones = zones;
        ghostZoneBoxes = boxes;
    }

    /** Fantome a tesseller au prochain rendu (thread de rendu). */
    @Nullable
    private Map<BlockPos, BlockState> buildPasteModel;

    /** Reglages d'un point tourne avec le collage (face d'acces, face de chaque ressource). */
    private static CompoundTag rotateConfig(CompoundTag tag, Rotation rot) {
        if (RailConfigs.isRail(tag)) {
            return RailConfigs.rotate(tag, rot);
        }
        PointConfig config = PointConfig.load(tag);
        if (config.accessSide != null) {
            config.accessSide = rot.rotate(config.accessSide);
        }
        for (int i = 0; i < config.entries.size(); i++) {
            var entry = config.entries.get(i);
            if (entry.side() != null) {
                config.entries.set(i, entry.withSide(rot.rotate(entry.side())));
            }
        }
        return config.save();
    }

    /** Coin bas du collage centre sur un bloc vise, a la meme profondeur sous le sol que l'original. */
    private BlockPos originAt(BlockPos at) {
        Blueprint print = clipboard;
        boolean quarter = rotation % 2 == 1;
        int sx = quarter ? print.sz() : print.sx(), sz = quarter ? print.sx() : print.sz();
        int ground = groundAt(at.getX(), at.getZ());
        int y = (ground == Integer.MIN_VALUE ? at.getY() : ground) + print.depth();
        return new BlockPos(at.getX() - sx / 2, y, at.getZ() - sz / 2);
    }

    /** Pose le collage epingle (ou sous la souris) comme une etape. */
    private void confirmPaste() {
        if (ghost != null && !ghost.isEmpty()) {
            addOp(WorksPlan.Op.place(WorksPlan.Kind.PASTE, ghost, ghostConfigs, ghostZones));
            pasteAnchor = null;
        }
    }

    // ------------------------------------------------------------------
    // Edition de la copie (gomme, retrait, remplacement) et plans
    // ------------------------------------------------------------------

    /** Remplace la copie par sa version modifiee (annulable). */
    private void editClipboard(Blueprint edited) {
        if (clipboard == null || edited == clipboard) {
            return;
        }
        remember();
        clipboard = edited;
        clipEdited = true;
        pasteModelKey = null;
    }

    /** Bloc du fantome sous la souris (premier bloc plein traverse par le rayon), en position monde, ou null. */
    @Nullable
    private BlockPos ghostAt(double mouseX, double mouseY) {
        if (ghostCells.isEmpty()) {
            return null;
        }
        Vec3[] ray = ray(mouseX, mouseY);
        Vec3 dir = ray[1].subtract(ray[0]).normalize();
        Vec3 p = ray[0];
        BlockPos last = null;
        for (int i = 0; i < 2400; i++) {
            p = p.add(dir.scale(0.1));
            BlockPos cell = BlockPos.containing(p);
            if (cell.equals(last)) {
                continue;
            }
            last = cell;
            if (ghostCells.containsKey(cell)) {
                return cell;
            }
        }
        return null;
    }

    /** Gomme : retire le bloc vise ; Maj : tous les blocs de ce type. */
    private void eraseAt(double mouseX, double mouseY, boolean stroke) {
        BlockPos at = ghostAt(mouseX, mouseY);
        if (at == null || clipboard == null || erasedStroke.contains(at)) {
            return;
        }
        BlockPos rel = ghostCells.get(at);
        BlockState state = clipboard.blocks().get(rel);
        if (state == null) {
            return;
        }
        if (!stroke) {
            remember();
        }
        erasedStroke.add(at);
        clipboard = hasShiftDown() ? clipboard.without(state.getBlock()) : clipboard.erase(List.of(rel));
        clipEdited = true;
        pasteModelKey = null;
        updateGhost();
    }

    private void openLibrary() {
        Blueprint current = clipboard;
        minecraft.setScreen(new BlueprintLibraryScreen(this, current, chosen -> {
            remember();
            clipboard = chosen;
            clipEdited = false;
            rotation = 0;
            pasteModelKey = null;
            pasteAnchor = null;
            syncPlanName();
            super.selectTool(PASTE);
        }));
    }

    /** Range la copie dans la bibliotheque (nouveau plan, ou mise a jour de celui d'ou elle vient). */
    private void savePlan(boolean asNew) {
        if (clipboard == null) {
            return;
        }
        String name = planName == null || planName.getValue().isBlank() ? clipboard.name() : planName.getValue().trim();
        if (name.isBlank()) {
            name = Component.translatable("robotics.pioneers.plans.default_name", BlueprintLibrary.list().size() + 1).getString();
        }
        Blueprint saved = BlueprintLibrary.save(clipboard.withName(asNew ? null : clipboard.id(), name));
        if (saved != null) {
            clipboard = saved;
            clipEdited = false;
            savedFlash = ticks;
            pasteModelKey = null;
            syncPlanName();
        }
    }

    private int savedFlash = -1000;

    private void syncPlanName() {
        if (planName != null) {
            planName.setValue(clipboard == null ? "" : clipboard.name());
        }
    }

    private void nudge(int dx, int dy, int dz) {
        if (pasteAnchor != null) {
            pasteAnchor = pasteAnchor.offset(dx, dy, dz);
        }
    }

    private static BlockPos rotate(BlockPos rel, Rotation rotation, int sx, int sz) {
        int x = rel.getX(), z = rel.getZ();
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(sz - 1 - z, rel.getY(), x);
            case CLOCKWISE_180 -> new BlockPos(sx - 1 - x, rel.getY(), sz - 1 - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, rel.getY(), sx - 1 - x);
            default -> rel;
        };
    }

    // ------------------------------------------------------------------
    // Sous terre
    // ------------------------------------------------------------------

    @Nullable
    private BlockPos undergroundFor;
    private int[] groundCache = new int[0];
    private long undergroundCount;

    /** Hauteur du sol de chaque colonne de la selection (mise en cache tant que la selection ne bouge pas). */
    private void refreshGround() {
        BlockPos key = selA == null || selB == null ? null : new BlockPos(selA.getX() * 31 + selB.getX(), selA.getY() * 31 + selB.getY(),
              selA.getZ() * 31 + selB.getZ());
        if (key == null || key.equals(undergroundFor) && ticks % 40 != 0) {
            return;
        }
        undergroundFor = key;
        int sx = selB.getX() - selA.getX() + 1, sz = selB.getZ() - selA.getZ() + 1;
        groundCache = new int[sx * sz];
        undergroundCount = 0;
        for (int i = 0; i < sx; i++) {
            for (int j = 0; j < sz; j++) {
                int g = groundAt(selA.getX() + i, selA.getZ() + j);
                groundCache[i * sz + j] = g;
                if (g != Integer.MIN_VALUE && g >= selA.getY()) {
                    undergroundCount += Math.min(g, selB.getY()) - selA.getY() + 1;
                }
            }
        }
    }

    /** Blocs de la selection situes sous la surface. */
    private long underground() {
        refreshGround();
        return selA == null ? 0 : undergroundCount;
    }

    /**
     * La partie de la selection sous la surface, visible a travers le terrain : la ligne du sol (dessus orange) et les flancs de la
     * tranche souterraine sur le pourtour.
     */
    private void drawUnderground(PoseStack pose, MultiBufferSource.BufferSource buffers) {
        refreshGround();
        int sx = selB.getX() - selA.getX() + 1, sz = selB.getZ() - selA.getZ() + 1;
        if (groundCache.length != sx * sz || undergroundCount == 0) {
            return;
        }
        Matrix4f m = pose.last().pose();
        VertexConsumer quads = buffers.getBuffer(XRayTypes.QUADS);
        float r = 1F, g = 0.6F, b = 0.18F;
        float y0 = selA.getY();
        for (int i = 0; i < sx; i++) {
            for (int j = 0; j < sz; j++) {
                int ground = groundCache[i * sz + j];
                if (ground == Integer.MIN_VALUE || ground < selA.getY()) {
                    continue;
                }
                float top = Math.min(ground, selB.getY()) + 1.01F;
                float x = selA.getX() + i, z = selA.getZ() + j;
                face(quads, m, x, top, z, x, top, z + 1, x + 1, top, z + 1, x + 1, top, z, r, g, b, 0.22F);
                //Flancs sur le pourtour de la selection
                if (i == 0) {
                    face(quads, m, x, y0, z, x, y0, z + 1, x, top, z + 1, x, top, z, r * 0.8F, g * 0.8F, b, 0.16F);
                }
                if (i == sx - 1) {
                    face(quads, m, x + 1, y0, z, x + 1, top, z, x + 1, top, z + 1, x + 1, y0, z + 1, r * 0.8F, g * 0.8F, b, 0.16F);
                }
                if (j == 0) {
                    face(quads, m, x, y0, z, x, top, z, x + 1, top, z, x + 1, y0, z, r * 0.9F, g * 0.9F, b, 0.16F);
                }
                if (j == sz - 1) {
                    face(quads, m, x, y0, z + 1, x + 1, y0, z + 1, x + 1, top, z + 1, x, top, z + 1, r * 0.9F, g * 0.9F, b, 0.16F);
                }
            }
        }
        buffers.endBatch(XRayTypes.QUADS);
    }

    // ------------------------------------------------------------------
    // Route
    // ------------------------------------------------------------------

    /** Route ou voie ferree (memes points, meme apercu). */
    private boolean routeTool() {
        return ROAD.equals(tool) || RAIL.equals(tool);
    }

    private boolean railTool() {
        return RAIL.equals(tool);
    }

    /** Texte de la voie ferree a la place de celui de la route. */
    private String rk(String key) {
        return railTool() ? key.replace(".road_", ".rail_") : key;
    }

    /** Revetements proposes (le premier : route robotique). */
    private static List<BlockState> roadMaterials() {
        return List.of(PiRobotics.ROAD_BLOCK.getBlock().defaultBlockState(), Blocks.DIRT_PATH.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(),
              Blocks.COBBLESTONE.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(), Blocks.SMOOTH_STONE.defaultBlockState(),
              Blocks.OAK_PLANKS.defaultBlockState());
    }

    /** Blocs de remblai proposes. */
    private static List<BlockState> fillMaterials() {
        return List.of(Blocks.COBBLESTONE.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState(),
              Blocks.STONE_BRICKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.GLASS.defaultBlockState());
    }

    private static BlockState roadMaterial() {
        return roadMaterial == null ? PiRobotics.ROAD_BLOCK.getBlock().defaultBlockState() : roadMaterial;
    }

    private static BlockState fillMaterial() {
        return fillMaterial == null ? Blocks.COBBLESTONE.defaultBlockState() : fillMaterial;
    }

    /** Case de marche d'un point clique (le bloc vise est le sol, on marche dessus). */
    private static BlockPos walk(BlockPos point) {
        return point.above();
    }

    /**
     * Recalcule le trace, en tache de fond : un releve du terrain (sol et eau de chaque colonne) est pris ici, puis le planificateur
     * cherche la route la plus douce (pente limitee, lacets si besoin) d'un point au suivant.
     */
    private void computeRoad() {
        int version = ROAD_VERSION.incrementAndGet();
        roadPath.clear();
        roadBroken = false;
        roadComputing = false;
        roadModelDirty = true;
        ClientLevel level = minecraft.level;
        if (level == null || roadPoints.isEmpty()) {
            return;
        }
        if (roadPoints.size() == 1) {
            roadPath.add(walk(roadPoints.get(0)));
            return;
        }
        //Releve : les points, plus une marge pour les lacets (d'autant plus large que la montee est forte)
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE, rise = 0;
        for (int i = 0; i < roadPoints.size(); i++) {
            BlockPos point = roadPoints.get(i);
            minX = Math.min(minX, point.getX());
            minZ = Math.min(minZ, point.getZ());
            maxX = Math.max(maxX, point.getX());
            maxZ = Math.max(maxZ, point.getZ());
            if (i > 0) {
                rise = Math.max(rise, Math.abs(point.getY() - roadPoints.get(i - 1).getY()));
            }
        }
        int margin = Math.max(12, Math.min(64, rise * roadRun / 2 + 8));
        minX -= margin;
        minZ -= margin;
        maxX += margin;
        maxZ += margin;
        //Releve borne (au-dela, le trace reste dans le cadre)
        int sizeX = Math.min(maxX - minX + 1, MAX_ROAD_SPAN), sizeZ = Math.min(maxZ - minZ + 1, MAX_ROAD_SPAN);
        RoadPlanner.Terrain terrain = new RoadPlanner.Terrain(minX, minZ, sizeX, sizeZ);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = minX; x < minX + sizeX; x++) {
            for (int z = minZ; z < minZ + sizeZ; z++) {
                int ground = groundAt(x, z);
                if (ground == Integer.MIN_VALUE) {
                    continue;
                }
                boolean wet = level.hasChunk(x >> 4, z >> 4) && !level.getFluidState(at.set(x, ground + 1, z)).isEmpty();
                terrain.set(x, z, ground + 1, wet);
            }
        }
        //Les points eux-memes sont toujours praticables, a leur hauteur
        for (BlockPos point : roadPoints) {
            if (terrain.ground(point.getX(), point.getZ()) == Integer.MIN_VALUE) {
                terrain.set(point.getX(), point.getZ(), walk(point).getY(), false);
            }
        }
        List<BlockPos> points = new ArrayList<>(roadPoints);
        RoadPlanner.Settings settings = new RoadPlanner.Settings(roadRun, 10, 16, roadWidth, 350_000);
        roadComputing = true;
        BooleanSupplier cancelled = () -> ROAD_VERSION.get() != version;
        if (railTool()) {
            //0.20.6 : voie ferree (caps par 45 degres, pentes douces en ligne droite)
            RailPlanner.Settings rs = new RailPlanner.Settings(8, 24, 400_000);
            ROAD_THREAD.execute(() -> {
                List<BlockPos> path = new ArrayList<>();
                boolean complete = true;
                path.add(walk(points.get(0)));
                for (int i = 1; i < points.size() && !cancelled.getAsBoolean(); i++) {
                    RailPlanner.Result result = RailPlanner.plan(terrain, path.get(path.size() - 1), walk(points.get(i)), rs, cancelled);
                    complete &= result.complete();
                    for (BlockPos cell : result.cells()) {
                        if (!cell.equals(path.get(path.size() - 1))) {
                            path.add(cell);
                        }
                    }
                    if (!result.complete()) {
                        break;
                    }
                }
                boolean done = complete;
                minecraft.execute(() -> {
                    if (ROAD_VERSION.get() != version) {
                        return;
                    }
                    roadPath.clear();
                    roadPath.addAll(path);
                    roadBroken = !done;
                    roadComputing = false;
                    roadModelDirty = true;
                });
            });
            return;
        }
        ROAD_THREAD.execute(() -> {
            List<BlockPos> path = new ArrayList<>();
            boolean complete = true;
            path.add(walk(points.get(0)));
            for (int i = 1; i < points.size() && !cancelled.getAsBoolean(); i++) {
                BlockPos from = path.get(path.size() - 1), to = walk(points.get(i));
                RoadPlanner.Result result = RoadPlanner.plan(terrain, from, to, settings, cancelled);
                complete &= result.complete();
                for (BlockPos cell : result.cells()) {
                    BlockPos last = path.get(path.size() - 1);
                    if (cell.equals(last)) {
                        continue;
                    }
                    //Pas en diagonale : on bouche l'angle pour une route continue
                    if (last.getX() != cell.getX() && last.getZ() != cell.getZ()) {
                        path.add(new BlockPos(cell.getX(), last.getY(), last.getZ()));
                    }
                    path.add(cell);
                }
                if (!result.complete()) {
                    //Arrivee hors d'atteinte : on finit en ligne droite (le chantier nivellera)
                    BlockPos last = path.get(path.size() - 1);
                    int steps = Math.max(Math.abs(to.getX() - last.getX()), Math.abs(to.getZ() - last.getZ()));
                    for (int s = 1; s <= steps; s++) {
                        double t = s / (double) steps;
                        path.add(new BlockPos((int) Math.round(last.getX() + (to.getX() - last.getX()) * t),
                              (int) Math.round(last.getY() + (to.getY() - last.getY()) * t), (int) Math.round(last.getZ() + (to.getZ() - last.getZ()) * t)));
                    }
                }
            }
            boolean done = complete;
            minecraft.execute(() -> {
                if (ROAD_VERSION.get() != version) {
                    return;
                }
                roadPath.clear();
                roadPath.addAll(path);
                roadBroken = !done;
                roadComputing = false;
                roadModelDirty = true;
            });
        });
    }

    private static final int MAX_ROAD_SPAN = 384;

    /**
     * Blocs de la route : le revetement sous chaque case, la place degagee au-dessus (deux blocs) et, si demande, le remblai sous le
     * revetement jusqu'au terrain.
     */
    private Map<BlockPos, BlockState> roadBlocks() {
        if (railTool()) {
            ClientLevel level = minecraft.level;
            if (level == null) {
                return new LinkedHashMap<>();
            }
            Map<BlockPos, BlockState> tracks = RailWorks.lay(level, roadPath, PiRail.TRACKS.get(railQuality).getBlock());
            return RailWorks.blocks(level, roadPath, tracks, fillMaterial(), fillMaterial(), PiRail.VIADUCT_PILLAR.getBlock().defaultBlockState(),
                  level.getMinBuildHeight());
        }
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        BlockState road = roadMaterial(), fill = fillMaterial();
        ClientLevel level = minecraft.level;
        int r = roadWidth / 2;
        Set<BlockPos> surface = new HashSet<>();
        for (BlockPos cell : roadPath) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    surface.add(cell.offset(dx, -1, dz));
                }
            }
        }
        for (BlockPos cell : roadPath) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos at = cell.offset(dx, 0, dz);
                    if (!surface.contains(at)) {
                        blocks.putIfAbsent(at, Blocks.AIR.defaultBlockState());
                    }
                    if (!surface.contains(at.above())) {
                        blocks.putIfAbsent(at.above(), Blocks.AIR.defaultBlockState());
                    }
                    if (roadFill && level != null) {
                        //Remblai : du dessous du revetement jusqu'au premier bloc de terrain
                        BlockPos.MutableBlockPos down = at.below(2).mutable();
                        for (int i = 0; i < 32 && down.getY() > level.getMinBuildHeight(); i++, down.move(0, -1, 0)) {
                            BlockState state = level.getBlockState(down);
                            if (!state.isAir() && !state.canBeReplaced() && !state.is(BlockTags.LEAVES)) {
                                break;
                            }
                            if (!surface.contains(down)) {
                                blocks.putIfAbsent(down.immutable(), fill);
                            }
                        }
                    }
                }
            }
        }
        //Le revetement passe toujours avant le reste
        for (BlockPos at : surface) {
            blocks.put(at, road);
        }
        return blocks;
    }

    /** Apercu de la route tracee : les vrais blocs, translucides. */
    private void rebuildRoadModel() {
        roadModelDirty = false;
        roadModelBlocks = null;
        roadOrigin = null;
        if (roadPath.size() < 2 || roadComputing) {
            roadModel.close();
            return;
        }
        Map<BlockPos, BlockState> blocks = roadBlocks();
        int x = Integer.MAX_VALUE, y = Integer.MAX_VALUE, z = Integer.MAX_VALUE;
        for (BlockPos pos : blocks.keySet()) {
            x = Math.min(x, pos.getX());
            y = Math.min(y, pos.getY());
            z = Math.min(z, pos.getZ());
        }
        BlockPos origin = new BlockPos(x, y, z);
        Map<BlockPos, BlockState> relative = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            relative.put(entry.getKey().subtract(origin), entry.getValue());
        }
        roadModelBlocks = blocks;
        roadOrigin = origin;
        roadModel.buildBlueprint(relative);
    }

    private void confirmRoad() {
        if (roadPath.size() < 2 || roadComputing) {
            return;
        }
        addOp(WorksPlan.Op.place(WorksPlan.Kind.ROAD, roadBlocks()));
        roadPoints.clear();
        roadPath.clear();
        roadModelDirty = true;
    }

    /** Deplace un point de la route (panneau) : on recalcule le trace. */
    private void movePoint(int index, int dx, int dy, int dz) {
        if (index < 0 || index >= roadPoints.size()) {
            return;
        }
        remember();
        roadPoints.set(index, roadPoints.get(index).offset(dx, dy, dz));
        computeRoad();
    }

    /** Rangee de blocs au choix (icones), plus « autre... » qui ouvre la liste des objets. Renvoie le y suivant. */
    private int materialRow(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, List<BlockState> choices, BlockState current,
          Consumer<BlockState> set) {
        int size = 16, cx = x;
        boolean known = false;
        for (BlockState choice : choices) {
            if (cx + size > x + w - size - 2) {
                break;
            }
            boolean selected = choice.getBlock() == current.getBlock();
            known |= selected;
            iconChip(graphics, mouseX, mouseY, cx, y, size, size, new ItemStack(choice.getBlock()), selected, 0x8D6E63, () -> {
                set.accept(choice);
                roadModelDirty = true;
            });
            if (over(mouseX, mouseY, cx, y, size, size)) {
                tooltip(List.of(choice.getBlock().getName()));
            }
            cx += size + 2;
        }
        //Autre bloc : la liste des objets (seuls les blocs comptent)
        int ox = x + w - size;
        if (!known) {
            iconChip(graphics, mouseX, mouseY, ox, y, size, size, new ItemStack(current.getBlock()), true, 0x8D6E63, () -> pickMaterial(set));
        } else {
            chip(graphics, mouseX, mouseY, ox, y, size, size, Component.literal("…"), false, 0x8D6E63, () -> pickMaterial(set));
        }
        if (over(mouseX, mouseY, ox, y, size, size)) {
            tooltip(List.of(Component.translatable("robotics.pioneers.works.road_other"),
                  Component.translatable("robotics.pioneers.works.road_other.tooltip").withStyle(ChatFormatting.GRAY)));
        }
        return y + size + 4;
    }

    private void pickMaterial(Consumer<BlockState> set) {
        minecraft.setScreen(new ResourcePickerScreen(this, Resource.ITEMS, Set.of(), false, res -> {
            if (res != null && res.item().getItem() instanceof BlockItem block) {
                set.accept(block.getBlock().defaultBlockState());
                roadModelDirty = true;
            }
        }));
    }

    /** Ligne de trois compteurs X, Y, Z pour un point de la route. */
    private int pointRow(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, Component label, int index) {
        BlockPos point = roadPoints.get(index);
        //Libelle au-dessus, les trois reglages sur toute la largeur : les coordonnees (jusqu'a -12345) restent lisibles
        GuiDraw.text(graphics, font, label, x, y, 0.62F, D_DIM, w);
        y += 8;
        int cw = (w - 4) / 3;
        int[] values = {point.getX(), point.getY(), point.getZ()};
        String[] axes = {"X", "Y", "Z"};
        for (int a = 0; a < 3; a++) {
            int axis = a, cx = x + a * (cw + 2);
            stepper(graphics, mouseX, mouseY, cx, y, cw, values[a], values[a] - 64, values[a] + 64, 10, value -> movePoint(index, axis == 0 ? value - values[0] : 0,
                  axis == 1 ? value - values[1] : 0, axis == 2 ? value - values[2] : 0));
            tooltip(mouseX, mouseY, cx + 12, y, cw - 24, 12, Component.literal(axes[a]),
                  Component.translatable("robotics.pioneers.works.road_axis").withStyle(ChatFormatting.GRAY));
        }
        return y + 15;
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    @Override
    protected void selectTool(String id) {
        pasteAnchor = null;
        if (PASTE.equals(id) && clipboard == null) {
            if (selA != null) {
                copy();
            }
            return;
        }
        super.selectTool(id);
    }

    @Override
    protected boolean toolClick(@Nullable Hit hit, double mouseX, double mouseY) {
        if (routeTool() && hit != null) {
            remember();
            if (hasShiftDown() && roadPoints.size() >= 2) {
                //Maj + clic : deplace l'arrivee
                roadPoints.set(roadPoints.size() - 1, hit.pos());
            } else {
                roadPoints.add(hit.pos());
            }
            computeRoad();
        } else if (PASTE.equals(tool) && clipboard != null && eraser && pasteAnchor != null) {
            //Gomme : on retire de la copie ce qu'on vise (glisser pour en retirer plusieurs)
            erasedStroke.clear();
            eraseAt(mouseX, mouseY, false);
            return true;
        } else if (PASTE.equals(tool) && clipboard != null) {
            if (hasShiftDown()) {
                //Maj + clic : pose directe, sans ajustement
                confirmPaste();
            } else if (hit != null) {
                //Clic : on epingle le collage ici ; on l'ajuste ensuite (fleches, Pg.prec/suiv, panneau), puis Entree
                pasteAnchor = originAt(hit.pos());
            }
        }
        return false;
    }

    @Override
    protected void toolDrag(double mouseX, double mouseY) {
        if (PASTE.equals(tool) && eraser && pasteAnchor != null && !hasShiftDown()) {
            eraseAt(mouseX, mouseY, true);
        }
    }

    /** Route : clic droit sur un point (ou tout pres) le retire. */
    @Override
    protected void rightClick(@Nullable Hit hit, double mouseX, double mouseY) {
        if (!routeTool() || roadPoints.isEmpty()) {
            return;
        }
        int found = -1;
        double best = 14 * 14;
        for (int i = 0; i < roadPoints.size(); i++) {
            var p = project(Vec3.atCenterOf(roadPoints.get(i)));
            if (p.z < 0) {
                continue;
            }
            double d = (p.x - mouseX) * (p.x - mouseX) + (p.y - mouseY) * (p.y - mouseY);
            if (d < best) {
                best = d;
                found = i;
            }
        }
        if (found < 0 && hit != null) {
            double near = 2.5 * 2.5;
            for (int i = 0; i < roadPoints.size(); i++) {
                double d = roadPoints.get(i).distSqr(hit.pos());
                if (d <= near) {
                    near = d;
                    found = i;
                }
            }
        }
        if (found >= 0) {
            remember();
            roadPoints.remove(found);
            computeRoad();
        }
    }

    @Override
    protected boolean keyExtra(int key, boolean ctrl, boolean shift) {
        if (ctrl && key == GLFW.GLFW_KEY_C) {
            copy();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            selectTool(PASTE.equals(tool) ? SELECT : PASTE);
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE && SELECT.equals(tool)) {
            clearSelection();
            return true;
        }
        if (PASTE.equals(tool) && key == GLFW.GLFW_KEY_E && clipboard != null) {
            eraser = !eraser;
            return true;
        }
        if (PASTE.equals(tool) && pasteAnchor != null) {
            switch (key) {
                case GLFW.GLFW_KEY_LEFT -> nudge(-1, 0, 0);
                case GLFW.GLFW_KEY_RIGHT -> nudge(1, 0, 0);
                case GLFW.GLFW_KEY_UP -> nudge(0, 0, -1);
                case GLFW.GLFW_KEY_DOWN -> nudge(0, 0, 1);
                case GLFW.GLFW_KEY_PAGE_UP -> nudge(0, 1, 0);
                case GLFW.GLFW_KEY_PAGE_DOWN -> nudge(0, -1, 0);
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> confirmPaste();
                case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_BACKSPACE -> pasteAnchor = null;
                default -> {
                    return key == GLFW.GLFW_KEY_X && rotate(shift);
                }
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_X && PASTE.equals(tool)) {
            rotation = (rotation + (shift ? 3 : 1)) & 3;
            return true;
        }
        if (routeTool()) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !roadPoints.isEmpty()) {
                remember();
                roadPoints.remove(roadPoints.size() - 1);
                computeRoad();
                return true;
            }
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && roadPath.size() >= 2 && !roadComputing) {
                confirmRoad();
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_H) {
            showPreview = !showPreview;
            return true;
        }
        if (key == GLFW.GLFW_KEY_B) {
            openLibrary();
            return true;
        }
        return false;
    }

    /** Tourne le collage d'un quart de tour (Maj : dans l'autre sens) autour de son centre. */
    private boolean rotate(boolean back) {
        if (pasteAnchor != null && clipboard != null) {
            //Garder le meme centre une fois tourne
            boolean quarter = rotation % 2 == 1;
            int sx = quarter ? clipboard.sz() : clipboard.sx(), sz = quarter ? clipboard.sx() : clipboard.sz();
            int cx = pasteAnchor.getX() + sx / 2, cz = pasteAnchor.getZ() + sz / 2;
            rotation = (rotation + (back ? 3 : 1)) & 3;
            pasteAnchor = new BlockPos(cx - sz / 2, pasteAnchor.getY(), cz - sx / 2);
        } else {
            rotation = (rotation + (back ? 3 : 1)) & 3;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Cycle de vie
    // ------------------------------------------------------------------

    @Override
    public void removed() {
        super.removed();
        preview.close();
        pasteModel.close();
        pasteModelKey = null;
        roadModel.close();
        roadModelDirty = true;
        previewDirty = true;
    }

    @Override
    protected void save() {
        if (!canSave()) {
            return;
        }
        //0.20.46 (audit) : plan compresse et decoupe en morceaux (au-dela de 32 Ko, un seul paquet expulsait le joueur d'un serveur)
        if (!PacketWorks.submit(draft.save(false), plan.save())) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatableWithFallback("robotics.pioneers.works.too_big",
                      "This construction plan is too large to be sent to the server"), true);
            }
            return;
        }
        close();
    }

    private boolean canSave() {
        return waitingFor == 0 && plan.isValid();
    }

    // ------------------------------------------------------------------
    // Rendu
    // ------------------------------------------------------------------

    @Override
    protected void renderExtra(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cameraPos) {
        if (previewDirty) {
            previewDirty = false;
            preview.build(plan);
        }
        if (showPreview) {
            buffers.endBatch();
            preview.render(pose);
        }
        if (selA != null && selB != null && SELECT.equals(tool)) {
            drawUnderground(pose, buffers);
        }
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        //Etape survolee dans la liste : son volume
        if (hoveredOp >= 0 && hoveredOp < plan.ops.size()) {
            WorksPlan.Op op = plan.ops.get(hoveredOp);
            if (op.min != null && op.max != null) {
                LevelRenderer.renderLineBox(pose, lines, new AABB(op.min, op.max.offset(1, 1, 1)).inflate(0.04), 1, 0.85F, 0.3F, 1);
            }
        }
        //Trace de route en cours : les blocs prevus, puis le fil du trace
        if (roadModelDirty) {
            rebuildRoadModel();
        }
        if (roadOrigin != null && roadModelBlocks != null && routeTool()) {
            buffers.endBatch(RenderType.lines());
            buffers.endBatch();
            roadModel.renderAt(pose, roadOrigin);
            lines = buffers.getBuffer(RenderType.lines());
        }
        if (!roadPath.isEmpty()) {
            Matrix4f m = pose.last().pose();
            var normal = pose.last().normal();
            float r = roadComputing ? 0.8F : roadBroken ? 1F : 0.55F, g = roadComputing ? 0.8F : roadBroken ? 0.45F : 0.9F,
                  b = roadComputing ? 0.8F : roadBroken ? 0.35F : 1F;
            for (int i = 1; i < roadPath.size(); i++) {
                Vec3 a = Vec3.atBottomCenterOf(roadPath.get(i - 1)).add(0, 0.08, 0), c = Vec3.atBottomCenterOf(roadPath.get(i)).add(0, 0.08, 0);
                Vec3 d = c.subtract(a).normalize();
                lines.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(r, g, b, 1).normal(normal, (float) d.x, (float) d.y, (float) d.z).endVertex();
                lines.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(r, g, b, 1).normal(normal, (float) d.x, (float) d.y, (float) d.z).endVertex();
            }
            int half = roadWidth / 2;
            for (BlockPos cell : roadPath) {
                LevelRenderer.renderLineBox(pose, lines, new AABB(cell.below()).inflate(half, 0, half).inflate(0.01), r, g, b, 0.35F);
            }
        }
        for (BlockPos point : roadPoints) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(point).inflate(0.06), 1, 1, 1, 1);
        }
        //Zones de travail qui seront creees (collees), et celles du collage en cours
        for (WorksPlan.Op op : plan.ops) {
            for (CompoundTag tag : op.zones) {
                RoboticOrder zone = RoboticOrder.load(tag);
                if (zone.min != null && zone.max != null) {
                    int c = zone.task.color;
                    LevelRenderer.renderLineBox(pose, lines, new AABB(zone.min, zone.max.offset(1, 1, 1)).inflate(0.05), (c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F,
                          (c & 255) / 255F, 0.9F);
                }
            }
        }
        if (eraseHover != null) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(eraseHover).inflate(0.02), 1, 0.3F, 0.25F, 1);
        }
        for (AABB box : ghostZoneBoxes) {
            LevelRenderer.renderLineBox(pose, lines, box.inflate(0.05), 0.95F, 0.85F, 0.4F, 1);
        }
        buffers.endBatch(RenderType.lines());
        //Fantome du collage : les vrais blocs, translucides, la ou ils seront poses
        if (buildPasteModel != null) {
            pasteModel.buildBlueprint(buildPasteModel);
            buildPasteModel = null;
        }
        if (ghost != null && ghostOrigin != null) {
            buffers.endBatch();
            pasteModel.renderAt(pose, ghostOrigin);
            AABB bounds = null;
            for (BlockPos pos : ghost.keySet()) {
                AABB b = new AABB(pos);
                bounds = bounds == null ? b : bounds.minmax(b);
            }
            if (bounds != null) {
                LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), bounds.inflate(0.03), 0.66F, 0.36F, 1, 1);
                buffers.endBatch(RenderType.lines());
            }
        }
    }

    @Override
    protected void drawOverlayExtra(GuiGraphics graphics, int mouseX, int mouseY) {
        updateGhost();
        eraseHover = PASTE.equals(tool) && eraser && pasteAnchor != null && inView(mouseX, mouseY) ? ghostAt(mouseX, mouseY) : null;

        if (waitingFor != 0) {
            banner(graphics, Component.translatable("robotics.pioneers.works.loading"), 0x4FC3F7);
        } else if (PASTE.equals(tool) && eraser) {
            banner(graphics, Component.translatable(pasteAnchor != null ? "robotics.pioneers.plans.eraser_on" : "robotics.pioneers.plans.eraser_pin"),
                  0xFF5A4D);
        } else if (PASTE.equals(tool)) {
            banner(graphics, Component.translatable(pasteAnchor != null ? "robotics.pioneers.works.paste_pinned" : "robotics.pioneers.works.paste_hint"),
                  PURPLE);
        } else if (routeTool()) {
            banner(graphics, Component.translatable(roadComputing ? "robotics.pioneers.works.road_computing"
                                                                  : roadPoints.isEmpty() ? rk("robotics.pioneers.works.road_start")
                                                                                         : roadPoints.size() == 1 ? rk("robotics.pioneers.works.road_end")
                                                                                                                  : rk("robotics.pioneers.works.road_next"),
                  roadPath.size()), 0x8D6E63);
        } else if (plan.isEmpty() && selA == null && corner == null) {
            banner(graphics, Component.translatable("robotics.pioneers.works.empty"), 0x4FC3F7);
        }
        //Numero des etapes, au-dessus de leur volume
        for (int i = 0; i < plan.ops.size(); i++) {
            WorksPlan.Op op = plan.ops.get(i);
            if (op.min == null || op.max == null) {
                continue;
            }
            Vec3 top = new Vec3((op.min.getX() + op.max.getX() + 1) / 2.0, op.max.getY() + 1.4, (op.min.getZ() + op.max.getZ() + 1) / 2.0);
            if (top.distanceToSqr(camPos) < 200 * 200) {
                var p = project(top);
                if (p.z >= 0) {
                    pill(graphics, Component.literal((i + 1) + " · ").append(Component.translatable("robotics.pioneers.works.op." + kind(op))), p.x, p.y,
                          0xD0101418, 0xFF000000 | opColor(op));
                }
            }
        }
    }

    @Override
    protected int drawTopBarExtra(GuiGraphics graphics, int mouseX, int mouseY, int x) {
        x = topButton(graphics, mouseX, mouseY, x, "preview", "H", showPreview, () -> showPreview = !showPreview);
        x = topButton(graphics, mouseX, mouseY, x, "copy", "Ctrl + C", false, this::copy);
        x = topButton(graphics, mouseX, mouseY, x, "paste", "Ctrl + V", PASTE.equals(tool), () -> selectTool(PASTE.equals(tool) ? SELECT : PASTE));
        return topButton(graphics, mouseX, mouseY, x, "plans", "B", false, this::openLibrary);
    }

    @Override
    protected Component hint() {
        return Component.translatable("robotics.pioneers.works.hint." + tool);
    }

    private static String kind(WorksPlan.Op op) {
        return op.kind.name().toLowerCase(Locale.ROOT);
    }

    private static int opColor(WorksPlan.Op op) {
        return switch (op.kind) {
            case CLEAR -> 0xFF5A4D;
            case ROAD -> 0xC8A07A;
            case PASTE -> 0xB08CFF;
        };
    }

    // ------------------------------------------------------------------
    // Panneau
    // ------------------------------------------------------------------

    @Override
    protected int drawPanel(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w) {
        if (planName != null) {
            planName.setVisible(false);
        }
        RoboticOrder live = isNew ? null : ClientRobotics.orders().stream().filter(o -> o.id == draft.id).findFirst().orElse(null);
        if (live != null) {
            y = section(graphics, x, y, w, Component.translatable("robotics.pioneers.planner.section.status"));
            y = statusBox(graphics, x, y, w, live) + 2;
        }
        //Outil courant
        if (SELECT.equals(tool)) {
            y = section(graphics, x, y, w, Component.translatable("robotics.pioneers.works.section.selection"));
            if (selA == null || selB == null) {
                y += wrapped(graphics, Component.translatable("robotics.pioneers.works.select_hint"), x, y, 0.6F, D_DIM, w, 3) + 2;
            } else {
                GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.preview.size", selB.getX() - selA.getX() + 1, selB.getY() - selA.getY() + 1,
                      selB.getZ() - selA.getZ() + 1), x, y + 1, 0.65F, D_TEXT, w);
                y += 10;
                long below = underground();
                if (below > 0) {
                    graphics.fill(x + 1, y + 2, x + 6, y + 7, 0xFFFF9F2E);
                    y += wrapped(graphics, Component.translatable("robotics.pioneers.works.underground", OrderTexts.compact(below)), x + 10, y + 1, 0.6F,
                          0xFFFFB45A, w - 10, 2) + 1;
                }
                y += 2;
                int bw = (w - 3) / 2;
                button(graphics, mouseX, mouseY, x, y, bw, 15, Component.translatable("robotics.pioneers.works.clear"), true, this::clearSelection);
                tooltip(mouseX, mouseY, x, y, bw, 15, Component.translatable("robotics.pioneers.works.clear.tooltip"),
                      Component.translatable("robotics.pioneers.planner.key", "Suppr / Delete").withStyle(ChatFormatting.DARK_GRAY));
                button(graphics, mouseX, mouseY, x + bw + 3, y, bw, 15, Component.translatable("robotics.pioneers.works.copy"), true, this::copy);
                tooltip(mouseX, mouseY, x + bw + 3, y, bw, 15, Component.translatable("robotics.pioneers.works.copy.tooltip"),
                      Component.translatable("robotics.pioneers.planner.key", "Ctrl + C").withStyle(ChatFormatting.DARK_GRAY));
                y += 19;
            }
        } else if (routeTool()) {
            y = section(graphics, x, y, w, Component.translatable(railTool() ? "robotics.pioneers.works.section.rail" : "robotics.pioneers.works.section.road"));
            y += wrapped(graphics, Component.translatable(railTool() ? "robotics.pioneers.works.rail_intro" : "robotics.pioneers.works.road_info"), x, y, 0.58F,
                  D_DIM, w, 5) + 2;
            //Points de depart et d'arrivee, reglables au bloc pres
            if (!roadPoints.isEmpty()) {
                y = pointRow(graphics, mouseX, mouseY, x, y, w, Component.translatable("robotics.pioneers.works.road_from"), 0);
            }
            if (roadPoints.size() >= 2) {
                y = pointRow(graphics, mouseX, mouseY, x, y, w, Component.translatable("robotics.pioneers.works.road_to"), roadPoints.size() - 1);
                if (roadPoints.size() > 2) {
                    GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.works.road_waypoints", roadPoints.size() - 2), x, y, 0.58F, D_FAINT, w);
                    y += 9;
                }
                //Denivele, distance, pente moyenne et longueur minimale qu'impose la pente choisie
                BlockPos from = roadPoints.get(0), to = roadPoints.get(roadPoints.size() - 1);
                int rise = to.getY() - from.getY();
                double distance = Math.sqrt(Math.pow(to.getX() - from.getX(), 2) + Math.pow(to.getZ() - from.getZ(), 2));
                long grade = distance < 1 ? 0 : Math.round(Math.abs(rise) * 100 / distance);
                GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.works.road_rise", (rise > 0 ? "+" : "") + rise, Math.round(distance), grade),
                      x, y, 0.6F, D_TEXT, w);
                y += 9;
                if (Math.abs(rise) * (railTool() ? 2 : roadRun) > distance + 1) {
                    y += wrapped(graphics, Component.translatable("robotics.pioneers.works.road_switchback", Math.abs(rise) * (railTool() ? 2 : roadRun)), x, y, 0.56F, 0xFFFFD98A,
                          w, 2) + 1;
                }
            }
            y += 2;
            if (railTool()) {
                //0.20.6 : qualite de la voie, remblai et tablier
                GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.works.rail_quality"), x, y, 0.62F, D_DIM, w);
                y += 9;
                int cx = x;
                for (TrackQuality q : TrackQuality.values()) {
                    iconChip(graphics, mouseX, mouseY, cx, y, 16, 16, new ItemStack(PiRail.TRACKS.get(q).getBlock()), railQuality == q, 0x7E8B99, () -> {
                        railQuality = q;
                        roadModelDirty = true;
                    });
                    if (over(mouseX, mouseY, cx, y, 16, 16)) {
                        tooltip(List.of(PiRail.TRACKS.get(q).getBlock().getName()));
                    }
                    cx += 18;
                }
                y += 20;
                GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.works.rail_fill"), x, y, 0.62F, D_DIM, w);
                y += 9;
                y = materialRow(graphics, mouseX, mouseY, x, y, w, fillMaterials(), fillMaterial(), st -> fillMaterial = st);
                y += wrapped(graphics, Component.translatable("robotics.pioneers.works.rail_info"), x, y, 0.56F, D_FAINT, w, 4) + 2;
            } else {
            //Pente maximale
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.works.road_slope"), x, y + 3, 0.62F, D_DIM, 50);
            for (int i = 0; i < 4; i++) {
                int run = i + 1;
                chip(graphics, mouseX, mouseY, x + 52 + i * 26, y, 24, 13, Component.literal("1/" + run), roadRun == run, 0x8D6E63, () -> {
                    roadRun = run;
                    computeRoad();
                });
            }
            tooltip(mouseX, mouseY, x, y, 50, 13, Component.translatable("robotics.pioneers.works.road_slope"),
                  Component.translatable("robotics.pioneers.works.road_slope.tooltip").withStyle(ChatFormatting.GRAY));
            y += 16;
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.works.road_width"), x, y + 3, 0.62F, D_DIM, 50);
            for (int i = 0; i < 2; i++) {
                int width = i == 0 ? 1 : 3;
                chip(graphics, mouseX, mouseY, x + 52 + i * 26, y, 24, 13, Component.literal(Integer.toString(width)), roadWidth == width, 0x8D6E63, () -> {
                    roadWidth = width;
                    roadModelDirty = true;
                });
            }
            y += 16;
            //Revetement
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.works.road_material"), x, y, 0.62F, D_DIM, w);
            y += 9;
            y = materialRow(graphics, mouseX, mouseY, x, y, w, roadMaterials(), roadMaterial(), state -> roadMaterial = state);
            //Remblai
            toggle(graphics, mouseX, mouseY, x, y, w, Component.translatable("robotics.pioneers.works.road_fill"), roadFill, () -> {
                roadFill = !roadFill;
                roadModelDirty = true;
            });
            tooltip(mouseX, mouseY, x, y, w, 12, Component.translatable("robotics.pioneers.works.road_fill"),
                  Component.translatable("robotics.pioneers.works.road_fill.tooltip").withStyle(ChatFormatting.GRAY));
            y += 15;
            if (roadFill) {
                y = materialRow(graphics, mouseX, mouseY, x, y, w, fillMaterials(), fillMaterial(), state -> fillMaterial = state);
            }
            }
            //Etat du trace
            Component state = roadComputing ? Component.translatable("robotics.pioneers.works.road_computing")
                                            : Component.translatable("robotics.pioneers.works.road_length", roadPath.size(), roadPoints.size());
            GuiDraw.text(graphics, font, state, x, y, 0.62F, roadComputing ? 0xFF4FC3F7 : roadBroken ? 0xFFFF8A65 : D_TEXT, w);
            y += 9;
            if (roadBroken && !roadComputing) {
                y += wrapped(graphics, Component.translatable(rk("robotics.pioneers.works.road_broken")), x, y, 0.58F, 0xFFFF8A65, w, 3) + 1;
            }
            if (roadModelBlocks != null && !roadComputing) {
                y += wrapped(graphics, Component.translatable(rk("robotics.pioneers.works.road_blocks"), roadModelBlocks.values().stream().filter(b -> !b.isAir()).count()),
                      x, y, 0.58F, D_DIM, w, 2) + 1;
            }
            int bw = (w - 3) / 2;
            primary(graphics, mouseX, mouseY, x, y, bw, 15, Component.translatable(rk("robotics.pioneers.works.road_confirm")), roadPath.size() >= 2 && !roadComputing,
                  this::confirmRoad);
            tooltip(mouseX, mouseY, x, y, bw, 15, Component.translatable("robotics.pioneers.planner.key", "Entrée / Enter").withStyle(ChatFormatting.GRAY));
            button(graphics, mouseX, mouseY, x + bw + 3, y, bw, 15, Component.translatable("robotics.pioneers.works.road_reset"), !roadPoints.isEmpty(), () -> {
                remember();
                roadPoints.clear();
                computeRoad();
            });
            y += 19;
        } else if (PASTE.equals(tool) && clipboard != null) {
            y = section(graphics, x, y, w, Component.translatable("robotics.pioneers.works.section.paste"));
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.works.paste_size", clipboard.sx(), clipboard.sy(), clipboard.sz()), x, y + 1,
                  0.65F, D_TEXT, w);
            y += 10;
            if (!clipboard.configs().isEmpty() || !clipboard.zones().isEmpty()) {
                y += wrapped(graphics, Component.translatable("robotics.pioneers.works.paste_extras", clipboard.configs().size(), clipboard.zones().size()), x, y,
                      0.58F, 0xFFFFD98A, w, 2) + 1;
            }
            y += 1;
            button(graphics, mouseX, mouseY, x, y, 70, 14, Component.translatable("robotics.pioneers.works.rotate", rotation * 90), true,
                  () -> rotate(false));
            tooltip(mouseX, mouseY, x, y, 70, 14, Component.translatable("robotics.pioneers.planner.key", "X"));
            y += 18;
            //Position : epinglee au clic, reglable au bloc pres
            if (pasteAnchor == null) {
                y += wrapped(graphics, Component.translatable("robotics.pioneers.works.paste_pin"), x, y, 0.58F, D_DIM, w, 3) + 3;
            } else {
                String[] axes = {"x", "y", "z"};
                int sw = w - 14;
                for (int axis = 0; axis < 3; axis++) {
                    int a = axis;
                    int value = axis == 0 ? pasteAnchor.getX() : axis == 1 ? pasteAnchor.getY() : pasteAnchor.getZ();
                    GuiDraw.text(graphics, font, Component.literal(axes[axis].toUpperCase(Locale.ROOT)), x + 2, y + 3, 0.7F, 0xFF000000 | axisColor(axis), 10);
                    stepper(graphics, mouseX, mouseY, x + 12, y, sw, value, value - 64, value + 64, 8, v -> nudge(a == 0 ? v - value : 0, a == 1 ? v - value : 0,
                          a == 2 ? v - value : 0));
                    y += 15;
                }
                y += wrapped(graphics, Component.translatable("robotics.pioneers.works.paste_nudge"), x, y, 0.56F, D_FAINT, w, 3) + 2;
                int bw = (w - 3) / 2;
                primary(graphics, mouseX, mouseY, x, y, bw, 15, Component.translatable("robotics.pioneers.works.paste_confirm"), true, this::confirmPaste);
                tooltip(mouseX, mouseY, x, y, bw, 15, Component.translatable("robotics.pioneers.planner.key", "Entrée / Enter"));
                button(graphics, mouseX, mouseY, x + bw + 3, y, bw, 15, Component.translatable("robotics.pioneers.works.paste_unpin"), true,
                      () -> pasteAnchor = null);
                y += 19;
            }
            toggle(graphics, mouseX, mouseY, x, y + 2, w, Component.translatable("robotics.pioneers.works.paste_air"), pasteAir, () -> pasteAir = !pasteAir);
            tooltip(mouseX, mouseY, x, y, w, 12, Component.translatable("robotics.pioneers.works.paste_air.tooltip"));
            y += 16;
            y = drawPlanSection(graphics, mouseX, mouseY, x, y, w);
            y = drawEditSection(graphics, mouseX, mouseY, x, y, w);
        }
        //Etapes du chantier
        y = section(graphics, x, y + 4, w, Component.translatable("robotics.pioneers.works.section.steps", plan.ops.size()));
        hoveredOp = -1;
        if (plan.isEmpty()) {
            y += wrapped(graphics, Component.translatable("robotics.pioneers.works.no_steps"), x, y, 0.58F, D_FAINT, w, 3) + 2;
        }
        for (int i = 0; i < plan.ops.size(); i++) {
            WorksPlan.Op op = plan.ops.get(i);
            int index = i;
            boolean over = over(mouseX, mouseY, x, y, w, 13);
            if (over) {
                hoveredOp = i;
                graphics.fill(x, y, x + w, y + 13, 0xFF1E2A33);
            }
            graphics.fill(x + 1, y + 3, x + 7, y + 9, 0xFF000000 | opColor(op));
            Component text = Component.literal((i + 1) + ". ").append(Component.translatable("robotics.pioneers.works.op." + kind(op)))
                  .append(Component.literal(" · " + OrderTexts.compact(op.size())).withStyle(ChatFormatting.GRAY));
            GuiDraw.text(graphics, font, text, x + 10, y + 3, 0.65F, D_TEXT, w - 26);
            button(graphics, mouseX, mouseY, x + w - 13, y, 13, 13, Component.literal("✕"), true, () -> removeOp(index));
            y += 14;
        }
        //Bilan : ce qui part, ce qui est pose, les materiaux
        y = section(graphics, x, y + 4, w, Component.translatable("robotics.pioneers.works.section.result"));
        y += legend(graphics, x, y, w, 0xFFFF5A4D, Component.translatable("robotics.pioneers.works.removed", OrderTexts.compact(preview.removed())), D_TEXT);
        if (preview.buried() > 0) {
            y += legend(graphics, x, y, w, 0xFFFF9F2E, Component.translatable("robotics.pioneers.works.buried", OrderTexts.compact(preview.buried())),
                  0xFFFFB45A);
        }
        y += legend(graphics, x, y, w, 0xFF8FC8FF, Component.translatable("robotics.pioneers.works.placed", OrderTexts.compact(preview.placed())), D_TEXT) + 1;
        if (preview.truncated()) {
            y += wrapped(graphics, Component.translatable("robotics.pioneers.works.truncated"), x, y, 0.58F, 0xFFFFC870, w, 2) + 1;
        }
        List<Map.Entry<Item, Integer>> materials = preview.materials();
        if (!materials.isEmpty()) {
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.works.materials"), x, y + 1, 0.62F, D_DIM, w);
            y += 10;
            int perRow = Math.max(1, w / 20);
            int shown = materials.size();
            for (int i = 0; i < shown; i++) {
                int sx = x + (i % perRow) * 20, sy = y + (i / perRow) * 20;
                darkSlot(graphics, sx + 1, sy + 1);
                ItemStack stack = new ItemStack(materials.get(i).getKey());
                graphics.renderItem(stack, sx + 1, sy + 1);
                String count = OrderTexts.compact(materials.get(i).getValue());
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200);
                GuiDraw.right(graphics, font, Component.literal(count), sx + 18, sy + 12, 0.55F, 0xFFFFFFFF);
                graphics.pose().popPose();
                if (over(mouseX, mouseY, sx + 1, sy + 1, 16, 16)) {
                    tooltip(List.of(stack.getHoverName(), Component.literal("× " + materials.get(i).getValue()).withStyle(ChatFormatting.GRAY)));
                }
            }
            y += ((shown + perRow - 1) / perRow) * 20 + 2;
        }
        //Unites
        y = section(graphics, x, y + 4, w, Component.translatable("robotics.pioneers.planner.section.assign"));
        return AssignControls.draw(this, graphics, mouseX, mouseY, x, y, w, draft);
    }

    /** Plan : nom, ranger dans la bibliotheque (ou mettre a jour le plan d'origine). */
    private int drawPlanSection(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w) {
        y = section(graphics, x, y + 2, w, Component.translatable("robotics.pioneers.plans.section"));
        if (clipboard.id() != null) {
            Component from = Component.translatable(clipEdited ? "robotics.pioneers.plans.from_edited" : "robotics.pioneers.plans.from", clipboard.name());
            y += wrapped(graphics, from, x, y, 0.58F, clipEdited ? 0xFFFFD98A : D_DIM, w, 2) + 2;
        }
        if (planName != null) {
            planName.setX(x);
            planName.setY(y);
            planName.setWidth(w);
            planName.setVisible(y >= TOP + 4 && y + 12 <= height - FOOTER);
        }
        y += 15;
        int bw = (w - 3) / 2;
        boolean update = clipboard.id() != null;
        primary(graphics, mouseX, mouseY, x, y, update ? bw : w, 15,
              Component.translatable(update ? "robotics.pioneers.plans.update" : "robotics.pioneers.plans.save"), !update || clipEdited
                                                                                                                            || !planName.getValue().trim().equals(clipboard.name()),
              () -> savePlan(false));
        tooltip(mouseX, mouseY, x, y, update ? bw : w, 15, Component.translatable(update ? "robotics.pioneers.plans.update.tooltip"
                                                                                          : "robotics.pioneers.plans.save.tooltip"));
        if (update) {
            button(graphics, mouseX, mouseY, x + bw + 3, y, bw, 15, Component.translatable("robotics.pioneers.plans.save_as"), true, () -> savePlan(true));
        }
        y += 18;
        if (ticks - savedFlash < 60) {
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.plans.saved"), x, y, 0.6F, 0xFF55E07A, w);
            y += 10;
        }
        button(graphics, mouseX, mouseY, x, y, w, 14, Component.translatable("robotics.pioneers.plans.open_library"), true, this::openLibrary);
        tooltip(mouseX, mouseY, x, y, w, 14, Component.translatable("robotics.pioneers.planner.key", "B"));
        return y + 18;
    }

    /** Modifier la copie : gomme, contenu par materiau (retirer, remplacer), rogner, sans air, sans reglages ni zones. */
    private int drawEditSection(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w) {
        y = section(graphics, x, y + 2, w, Component.translatable("robotics.pioneers.plans.edit"));
        toggle(graphics, mouseX, mouseY, x, y + 2, w, Component.translatable("robotics.pioneers.plans.eraser"), eraser, () -> eraser = !eraser);
        tooltip(mouseX, mouseY, x, y, w, 12, Component.translatable("robotics.pioneers.plans.eraser"),
              Component.translatable("robotics.pioneers.plans.eraser.tooltip").withStyle(ChatFormatting.GRAY),
              Component.translatable("robotics.pioneers.planner.key", "E").withStyle(ChatFormatting.DARK_GRAY));
        y += 15;
        if (eraser && pasteAnchor == null) {
            y += wrapped(graphics, Component.translatable("robotics.pioneers.plans.eraser_pin"), x, y, 0.56F, 0xFFFFB45A, w, 3) + 2;
        }
        //Contenu : une ligne par materiau
        List<Map.Entry<Item, Integer>> materials = clipboard.materials();
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.plans.content", clipboard.solidCount()), x, y, 0.6F, D_DIM, w);
        y += 10;
        for (Map.Entry<Item, Integer> entry : materials) {
            ItemStack stack = new ItemStack(entry.getKey());
            darkSlot(graphics, x, y);
            graphics.renderItem(stack, x, y);
            GuiDraw.text(graphics, font, stack.getHoverName(), x + 19, y + 1, 0.58F, D_TEXT, w - 19 - 30);
            GuiDraw.text(graphics, font, Component.literal("× " + entry.getValue()), x + 19, y + 9, 0.52F, D_DIM, w - 19 - 30);
            Block block = entry.getKey() instanceof BlockItem item ? item.getBlock() : null;
            if (block != null) {
                button(graphics, mouseX, mouseY, x + w - 28, y + 2, 13, 13, Component.literal("⇄"), true,
                      () -> minecraft.setScreen(new ResourcePickerScreen(this, Resource.ITEMS, Set.of(), false, res -> {
                          if (res != null && res.item().getItem() instanceof BlockItem to && clipboard != null) {
                              editClipboard(clipboard.replace(block, to.getBlock()));
                          }
                      })));
                tooltip(mouseX, mouseY, x + w - 28, y + 2, 13, 13, Component.translatable("robotics.pioneers.plans.replace", stack.getHoverName()));
                button(graphics, mouseX, mouseY, x + w - 13, y + 2, 13, 13, Component.literal("✕"), true, () -> editClipboard(clipboard.without(block)));
                tooltip(mouseX, mouseY, x + w - 13, y + 2, 13, 13, Component.translatable("robotics.pioneers.plans.remove", stack.getHoverName()));
            }
            y += 19;
        }
        //Outils d'ensemble
        int bw = (w - 3) / 2;
        button(graphics, mouseX, mouseY, x, y, bw, 14, Component.translatable("robotics.pioneers.plans.trim"), true, () -> editClipboard(clipboard.trimmed()));
        tooltip(mouseX, mouseY, x, y, bw, 14, Component.translatable("robotics.pioneers.plans.trim.tooltip"));
        button(graphics, mouseX, mouseY, x + bw + 3, y, bw, 14, Component.translatable("robotics.pioneers.plans.no_air"), true,
              () -> editClipboard(clipboard.withoutAir()));
        tooltip(mouseX, mouseY, x + bw + 3, y, bw, 14, Component.translatable("robotics.pioneers.plans.no_air.tooltip"));
        y += 17;
        if (!clipboard.configs().isEmpty() || !clipboard.zones().isEmpty()) {
            button(graphics, mouseX, mouseY, x, y, bw, 14, Component.translatable("robotics.pioneers.plans.no_configs"), !clipboard.configs().isEmpty(),
                  () -> editClipboard(clipboard.withExtras(Map.of(), clipboard.zones())));
            button(graphics, mouseX, mouseY, x + bw + 3, y, bw, 14, Component.translatable("robotics.pioneers.plans.no_zones"), !clipboard.zones().isEmpty(),
                  () -> editClipboard(clipboard.withExtras(clipboard.configs(), List.of())));
            y += 17;
        }
        return y;
    }

    @Override
    protected void drawFooter(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w) {
        int bx = x;
        if (!isNew && draft.id > 0) {
            button(graphics, mouseX, mouseY, bx, y, 16, 15, Component.literal("🗑"), true, () -> {
                ClientRobotics.delete(draft);
                close();
            });
            tooltip(mouseX, mouseY, bx, y, 16, 15, Component.translatable("robotics.pioneers.orders.delete"));
            bx += 18;
        }
        int cancelW = 48;
        button(graphics, mouseX, mouseY, bx, y, cancelW, 15, Component.translatable("gui.cancel"), true, this::cancel);
        int saveX = bx + cancelW + 3;
        primary(graphics, mouseX, mouseY, saveX, y, x + w - saveX, 15, Component.translatable("robotics.pioneers.works.launch"), canSave(), this::save);
        tooltip(mouseX, mouseY, saveX, y, x + w - saveX, 15, Component.translatable(canSave() ? "robotics.pioneers.works.launch.tooltip"
                                                                                              : "robotics.pioneers.works.launch.invalid"));
    }
}

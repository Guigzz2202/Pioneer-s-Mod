package fr.pioneers.client.robotics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.robotics.Resource;
import fr.pioneers.robotics.RobotTask;
import fr.pioneers.robotics.RoboticOrder;
import fr.pioneers.robotics.ZoneScanner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Planificateur des zones de travail, en vue libre sur le vrai monde : on selectionne le volume (glisser, ou deux clics aux coins), on
 * etire ses faces avec les poignees, et tout le reste (tache, reglages, bornes, unites) est dans le panneau de droite. Ce que la zone
 * contient est surligne et compte ; les autres zones sont visibles (un clic sur leur nom pour passer dessus) ; copier / coller pose des
 * copies de la zone.
 */
public class ZonePlannerScreen extends FreeCamScreen {

    private static final int MAX_MARKS = 800;

    private record Mark(AABB box, int color) {}

    private record Stat(Component text, int color) {}

    private RoboticOrder draft;
    private boolean isNew;
    /** Etat enregistre de la zone (pour savoir si elle a ete modifiee). */
    private String snapshot;

    //Copier / coller
    @Nullable
    private static RoboticOrder clipboard;
    /** Profondeur du bas de la zone copiee sous le sol de son centre. */
    private static int clipboardDepth;
    @Nullable
    private BlockPos ghostMin, ghostMax;
    private int pasted;

    //Hauteurs automatiques : sol le plus bas et le plus haut du dernier trace (les hauteurs suivent alors la tache)
    private boolean autoHeights;
    private int groundLow, groundHigh;

    //Analyse de la zone
    private int analyzeIn = 2;
    private List<Mark> marks = List.of();
    private List<Stat> stats = List.of();
    private List<Entity> shownEntities = List.of();
    private Set<Entity> targetEntities = Set.of();

    private static final List<ToolDef> TOOLS_LIST = List.of(new ToolDef(SELECT, "⬚", GLFW.GLFW_KEY_V, "V", 0x2E9E78),
          new ToolDef("paste", "⎘", GLFW.GLFW_KEY_P, "P / Ctrl + V", PURPLE));

    /**
     * @param back  ecran de retour
     * @param draft zone a editer (copie), bornes eventuellement nulles (a tracer)
     * @param isNew nouvelle zone (sinon : modification d'une zone existante)
     */
    public ZonePlannerScreen(@Nullable Screen back, RoboticOrder draft, boolean isNew) {
        super(Component.translatable(isNew ? "robotics.pioneers.planner.title_new" : "robotics.pioneers.planner.title_edit"), back);
        this.draft = draft;
        this.isNew = isNew;
        draft.type = RoboticOrder.Type.ZONE;
        if (draft.task == RobotTask.LOGISTICS || draft.task == RobotTask.BUILDER) {
            draft.task = RobotTask.FARMER;
        }
        autoHeights = isNew;
        if (hasZone()) {
            focus = selectionCenter();
            groundLow = draft.min.getY();
            groundHigh = draft.max.getY();
        } else if (Minecraft.getInstance().player != null) {
            focus = Minecraft.getInstance().player.position();
        }
        snapshot = draft.save(false).toString();
        ClientRobotics.request();
    }

    /** Centre la vue sur un point (planificateur ouvert sans zone). */
    public void focusOn(Vec3 point) {
        focus = point;
    }

    private boolean dirty() {
        return !draft.save(false).toString().equals(snapshot);
    }

    private boolean hasZone() {
        return draft.min != null && draft.max != null;
    }

    /** Passe a une autre zone existante, sans quitter le planificateur. */
    private void edit(RoboticOrder other) {
        draft = other.copy();
        isNew = false;
        autoHeights = false;
        tool = SELECT;
        snapshot = draft.save(false).toString();
        plannerTitle = Component.translatable("robotics.pioneers.planner.title_edit");
        groundLow = draft.min.getY();
        groundHigh = draft.max.getY();
        analyzeIn = 1;
        frame(false);
    }

    // ------------------------------------------------------------------
    // Volume
    // ------------------------------------------------------------------

    @Override
    protected List<ToolDef> tools() {
        return TOOLS_LIST;
    }

    @Nullable
    @Override
    protected BlockPos selMin() {
        return draft.min;
    }

    @Nullable
    @Override
    protected BlockPos selMax() {
        return draft.max;
    }

    @Override
    protected void setSelection(@Nullable BlockPos a, @Nullable BlockPos b) {
        if (a == null || b == null) {
            draft.min = draft.max = null;
        } else {
            draft.min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
            draft.max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        }
        analyzeIn = 4;
    }

    @Override
    protected int maxSpan(int axis) {
        return axis == 1 ? RoboticOrder.MAX_ZONE_HEIGHT : RoboticOrder.MAX_ZONE;
    }

    @Override
    protected int selectionColor() {
        return draft.isValid() || !hasZone() ? draft.task.color : 0xFF4D40;
    }

    @Override
    protected boolean showsSelection() {
        return !"paste".equals(tool);
    }

    @Override
    protected void selectionDrawn(int x0, int z0, int x1, int z1, int low, int high) {
        groundLow = low;
        groundHigh = high;
        autoHeights = true;
        draft.min = new BlockPos(x0, low, z0);
        draft.max = new BlockPos(x1, high, z1);
        applyTaskHeights();
        analyzeIn = 4;
    }

    @Override
    protected void selectionChanged() {
        if (dragged != null && dragged.axis == 1) {
            autoHeights = false;
        }
        analyzeIn = 2;
    }

    @Override
    protected Object snapshot() {
        return new BlockPos[]{draft.min, draft.max};
    }

    @Override
    protected void restore(Object state) {
        BlockPos[] bounds = (BlockPos[]) state;
        setSelection(bounds[0], bounds[1]);
    }

    /** Hauteurs par defaut d'une tache, d'apres le sol trace (cultures et elevage au ras du sol, minage en profondeur...). */
    private void applyTaskHeights() {
        if (!hasZone() || !autoHeights) {
            return;
        }
        int low = groundLow, high = groundHigh;
        int y0, y1;
        switch (draft.task) {
            case MINER -> {
                y0 = low - 16;
                y1 = high;
            }
            case EXCAVATOR -> {
                y0 = low - 2;
                y1 = high;
            }
            default -> {
                y0 = low;
                y1 = high + 1;
            }
        }
        y1 = Math.min(y1, y0 + RoboticOrder.MAX_ZONE_HEIGHT - 1);
        setSelection(new BlockPos(draft.min.getX(), y0, draft.min.getZ()), new BlockPos(draft.max.getX(), y1, draft.max.getZ()));
    }

    // ------------------------------------------------------------------
    // Cycle de vie
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (analyzeIn > 0 && --analyzeIn == 0) {
            analyze();
        }
        if (ticks % 60 == 0 && drag == Drag.NONE) {
            analyzeIn = 1;
        }
    }

    @Override
    protected void save() {
        if (!canSave()) {
            return;
        }
        ClientRobotics.save(draft);
        close();
    }

    private boolean canSave() {
        return hasZone() && draft.isValid();
    }

    // ------------------------------------------------------------------
    // Analyse de la zone : ce qui sera traite
    // ------------------------------------------------------------------

    private void analyze() {
        ClientLevel level = minecraft.level;
        List<Mark> found = new ArrayList<>();
        List<Stat> lines = new ArrayList<>();
        Set<Entity> targets = new HashSet<>();
        List<Entity> entities = new ArrayList<>();
        if (level == null) {
            return;
        }
        if (hasZone()) {
            AABB box = draft.zoneBox(level.getMinBuildHeight());
            int x0 = Mth.floor(box.minX), x1 = Mth.floor(box.maxX) - 1, y0 = Mth.floor(box.minY), y1 = Mth.floor(box.maxY) - 1;
            int z0 = Mth.floor(box.minZ), z1 = Mth.floor(box.maxZ) - 1;
            int primary = 0, secondary = 0, unknown = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            RobotTask task = draft.task;
            boolean blocks = task == RobotTask.FARMER || task == RobotTask.LUMBERJACK || task == RobotTask.MINER || task == RobotTask.EXCAVATOR
                             || task == RobotTask.FISHER;
            if (blocks) {
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                            unknown++;
                            continue;
                        }
                        for (int y = y1; y >= y0; y--) {
                            pos.set(x, y, z);
                            BlockState state = level.getBlockState(pos);
                            if (state.isAir()) {
                                continue;
                            }
                            switch (task) {
                                case FARMER -> {
                                    if (ZoneScanner.isRipe(level, pos, state)) {
                                        primary++;
                                        mark(found, pos, 0x7CFC5A);
                                    } else if (isCrop(state)) {
                                        secondary++;
                                        mark(found, pos, 0xE8C547);
                                    }
                                }
                                case LUMBERJACK -> {
                                    if (state.is(BlockTags.LOGS)) {
                                        secondary++;
                                        if (!level.getBlockState(pos.below()).is(BlockTags.LOGS) && level.getBlockState(pos.below()).is(BlockTags.DIRT)) {
                                            primary++;
                                            mark(found, pos, 0x7CFC5A);
                                        } else {
                                            mark(found, pos, 0xC49A6C);
                                        }
                                    }
                                }
                                case MINER -> {
                                    if (state.is(Tags.Blocks.ORES)) {
                                        primary++;
                                        mark(found, pos, 0xFFD54F);
                                    }
                                }
                                case EXCAVATOR -> {
                                    if (!state.is(Blocks.BEDROCK) && state.getFluidState().isEmpty()) {
                                        primary++;
                                    }
                                }
                                case FISHER -> {
                                    if (state.getFluidState().is(Fluids.WATER) && state.getFluidState().isSource() && level.getBlockState(pos.above()).isAir()) {
                                        primary++;
                                        mark(found, pos, 0x4DD0E1);
                                    }
                                }
                                default -> {
                                }
                            }
                        }
                    }
                }
                switch (task) {
                    case FARMER -> {
                        lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.ripe", primary), 0x7CFC5A));
                        lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.growing", secondary), 0xE8C547));
                    }
                    case LUMBERJACK -> {
                        lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.trees", primary), 0x7CFC5A));
                        lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.logs", secondary), 0xC49A6C));
                    }
                    case MINER -> lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.ores", primary), 0xFFD54F));
                    case EXCAVATOR -> lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.dig", primary), 0xD7A86E));
                    case FISHER -> lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.water", primary), 0x4DD0E1));
                    default -> {
                    }
                }
            } else {
                int animals = 0, monsters = 0, items = 0, babies = 0, surplus = 0;
                Map<EntityType<?>, Integer> adults = new HashMap<>();
                for (Entity entity : level.getEntities((Entity) null, box, e -> true)) {
                    if (entity instanceof ItemEntity) {
                        items++;
                        entities.add(entity);
                        if (task == RobotTask.COLLECTOR) {
                            targets.add(entity);
                        }
                    } else if (entity instanceof Enemy) {
                        monsters++;
                        entities.add(entity);
                        if (task == RobotTask.HUNTER && draft.hunt != RoboticOrder.HUNT_ANIMALS) {
                            targets.add(entity);
                        }
                    } else if (entity instanceof Animal animal) {
                        animals++;
                        entities.add(entity);
                        if (animal.isBaby()) {
                            babies++;
                        } else if (adults.merge(animal.getType(), 1, Integer::sum) > draft.herd) {
                            surplus++;
                        }
                        if (task == RobotTask.BREEDER || task == RobotTask.HUNTER && draft.hunt != RoboticOrder.HUNT_MONSTERS
                            || task == RobotTask.APIARIST && entity instanceof Bee) {
                            targets.add(entity);
                        }
                    }
                }
                switch (task) {
                    case COLLECTOR -> lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.items", items), 0xFFD54F));
                    case APIARIST -> {
                        //Ruches et nids de la zone : places (3 par ruche), abeilles a l'interieur et ruches pleines de miel
                        int hives = 0, full = 0, inside = 0, bees = 0;
                        for (BlockPos at : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
                            if (level.getBlockEntity(at) instanceof BeehiveBlockEntity hive) {
                                hives++;
                                inside += hive.getOccupantCount();
                                if (ZoneScanner.honeyReady(level.getBlockState(at))) {
                                    full++;
                                }
                            }
                        }
                        for (Entity entity : entities) {
                            if (entity instanceof Bee) {
                                bees++;
                            }
                        }
                        lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.hives", hives, hives * 3), 0xFFC107));
                        lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.bees", bees + inside), 0xFFE082));
                        if (full > 0) {
                            lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.honey", full), 0xFFB300));
                        }
                    }
                    case BREEDER -> {
                        lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.animals", animals), 0xF48FB1));
                        lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.babies", babies), 0xF8BBD0));
                        if (surplus > 0) {
                            lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.surplus", surplus), 0xE57373));
                        }
                    }
                    case HUNTER -> {
                        if (draft.hunt != RoboticOrder.HUNT_ANIMALS) {
                            lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.monsters", monsters), 0xE57373));
                        }
                        if (draft.hunt != RoboticOrder.HUNT_MONSTERS) {
                            lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.animals", animals), 0xF48FB1));
                        }
                    }
                    default -> {
                    }
                }
            }
            if (unknown > 0) {
                lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.unknown"), 0xFFB050));
            }
            long volume = (long) (draft.max.getX() - draft.min.getX() + 1) * (draft.max.getY() - draft.min.getY() + 1) * (draft.max.getZ() - draft.min.getZ() + 1);
            lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.volume", OrderTexts.compact(volume)), 0x8FA3A0));
            if (!ClientRoboticsMap.networks.isEmpty()) {
                Vec3 c = selectionCenter();
                if (ClientRoboticsMap.networkAt(c.x, c.z) == null) {
                    lines.add(new Stat(Component.translatable("robotics.pioneers.planner.stat.no_network"), 0xFF6E5A));
                }
            }
        }
        marks = found;
        stats = lines;
        shownEntities = entities;
        targetEntities = targets;
    }

    private static void mark(List<Mark> found, BlockPos pos, int color) {
        if (found.size() < MAX_MARKS) {
            found.add(new Mark(new AABB(pos).inflate(0.02), color));
        }
    }

    private static boolean isCrop(BlockState state) {
        return state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock || state.getBlock() instanceof SugarCaneBlock
               || state.getBlock() instanceof CactusBlock || state.getBlock() instanceof SweetBerryBushBlock || state.getBlock() instanceof NetherWartBlock
               || state.getBlock() instanceof CocoaBlock;
    }

    // ------------------------------------------------------------------
    // Rendu
    // ------------------------------------------------------------------

    @Override
    protected void renderExtra(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cameraPos) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (Mark mark : marks) {
            int c = mark.color();
            LevelRenderer.renderLineBox(pose, lines, mark.box(), (c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F, 0.9F);
        }
        for (Entity entity : shownEntities) {
            if (targetEntities.contains(entity) && entity.isAlive()) {
                LevelRenderer.renderLineBox(pose, lines, entity.getBoundingBox().inflate(0.05), 1F, 0.45F, 0.4F, 1F);
            }
        }
        for (RoboticOrder order : ClientRobotics.orders()) {
            if (order.type == RoboticOrder.Type.ZONE && order.id != draft.id && order.min != null && order.max != null
                && order.anchor().distSqr(BlockPos.containing(cameraPos)) < 256 * 256) {
                int c = order.task.color;
                LevelRenderer.renderLineBox(pose, lines, new AABB(order.min, order.max.offset(1, 1, 1)).inflate(0.02), (c >> 16 & 255) / 255F,
                      (c >> 8 & 255) / 255F, (c & 255) / 255F, 0.6F);
            }
        }
        if (ghostMin != null && ghostMax != null) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(ghostMin, ghostMax.offset(1, 1, 1)).inflate(0.03), 0.66F, 0.36F, 1, 1);
        }
        buffers.endBatch(RenderType.lines());
        if (ghostMin != null && ghostMax != null) {
            VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
            box(quads, pose.last().pose(), new AABB(ghostMin, ghostMax.offset(1, 1, 1)).inflate(0.01), 0.66F, 0.36F, 1, 0.15F);
            buffers.endBatch(RenderType.debugQuads());
        }
    }

    @Override
    protected void drawOverlayExtra(GuiGraphics graphics, int mouseX, int mouseY) {
        updateGhost(mouseX, mouseY);
        //Autres zones : leur nom, un clic pour passer dessus
        AABB shown = new AABB(camPos, camPos).inflate(256);
        for (RoboticOrder order : ClientRobotics.orders()) {
            if (order.type == RoboticOrder.Type.ZONE && order.id != draft.id && order.min != null && order.max != null) {
                Vec3 top = new Vec3((order.min.getX() + order.max.getX() + 1) / 2.0, order.max.getY() + 1.3, (order.min.getZ() + order.max.getZ() + 1) / 2.0);
                if (!shown.contains(top)) {
                    continue;
                }
                Vector3f p = project(top);
                if (p.z < 0) {
                    continue;
                }
                Component name = Component.translatable(order.task.getTranslationKey());
                pill(graphics, name, p.x, p.y, 0xC0000000 | order.task.color, 0xFF000000);
                int w = (int) (font.width(name) * 0.7F) + 6;
                int x0 = Math.round(p.x - w / 2F), y0 = Math.round(p.y - 5);
                boolean dirty = dirty() && hasZone();
                click(x0, y0, w, 10, () -> {
                    if (!dirty()) {
                        if (order.task == RobotTask.BUILDER) {
                            ClientRobotics.openWorks(back, order);
                        } else {
                            edit(order);
                        }
                    }
                });
                tooltip(mouseX, mouseY, x0, y0, w, 10, name,
                      Component.translatable(dirty ? "robotics.pioneers.planner.switch.dirty" : "robotics.pioneers.planner.switch")
                            .withStyle(dirty ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            }
        }
        if ("paste".equals(tool)) {
            banner(graphics, Component.translatable("robotics.pioneers.planner.paste.hint", pasted), PURPLE);
        } else if (!hasZone() && corner == null) {
            banner(graphics, Component.translatable("robotics.pioneers.planner.empty"), PURPLE);
        }
    }

    @Override
    protected int drawTopBarExtra(GuiGraphics graphics, int mouseX, int mouseY, int x) {
        x = topButton(graphics, mouseX, mouseY, x, "copy", "Ctrl + C", false, this::copy);
        return topButton(graphics, mouseX, mouseY, x, "paste", "Ctrl + V", "paste".equals(tool), this::togglePaste);
    }

    @Override
    protected Component hint() {
        if ("paste".equals(tool)) {
            return Component.translatable("robotics.pioneers.planner.hint.paste");
        }
        return Component.translatable(hasZone() ? "robotics.pioneers.planner.hint.free_edit" : "robotics.pioneers.planner.hint.free_draw");
    }

    // ------------------------------------------------------------------
    // Copier / coller
    // ------------------------------------------------------------------

    private void copy() {
        if (!hasZone()) {
            return;
        }
        clipboard = draft.copy();
        int cx = (draft.min.getX() + draft.max.getX()) / 2, cz = (draft.min.getZ() + draft.max.getZ()) / 2;
        int ground = groundAt(cx, cz);
        clipboardDepth = ground == Integer.MIN_VALUE ? 0 : draft.min.getY() - ground;
    }

    private void togglePaste() {
        if (clipboard == null) {
            copy();
        }
        if (clipboard == null) {
            return;
        }
        selectTool("paste".equals(tool) ? SELECT : "paste");
        pasted = 0;
    }

    @Override
    protected void selectTool(String id) {
        if ("paste".equals(id) && clipboard == null) {
            copy();
            if (clipboard == null) {
                return;
            }
        }
        super.selectTool(id);
    }

    /** Fantome de la zone a coller, sous la souris (centre sur le bloc vise, a la meme profondeur sous le sol). */
    private void updateGhost(int mouseX, int mouseY) {
        ghostMin = ghostMax = null;
        if (!"paste".equals(tool) || clipboard == null || clipboard.min == null || clipboard.max == null || !inView(mouseX, mouseY)) {
            return;
        }
        Hit hit = hover;
        if (hit == null) {
            return;
        }
        int sx = clipboard.max.getX() - clipboard.min.getX(), sy = clipboard.max.getY() - clipboard.min.getY(), sz = clipboard.max.getZ() - clipboard.min.getZ();
        int ground = groundAt(hit.pos().getX(), hit.pos().getZ());
        int y = (ground == Integer.MIN_VALUE ? hit.pos().getY() : ground) + clipboardDepth;
        ghostMin = new BlockPos(hit.pos().getX() - sx / 2, y, hit.pos().getZ() - sz / 2);
        ghostMax = ghostMin.offset(sx, sy, sz);
    }

    @Override
    protected boolean toolClick(@Nullable Hit hit, double mouseX, double mouseY) {
        if ("paste".equals(tool) && clipboard != null && ghostMin != null && ghostMax != null) {
            //Pose une copie : une nouvelle zone, enregistree tout de suite (on peut en poser plusieurs)
            RoboticOrder order = clipboard.copy();
            order.id = 0;
            order.paused = false;
            order.min = ghostMin;
            order.max = ghostMax;
            ClientRobotics.save(order);
            pasted++;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Panneau
    // ------------------------------------------------------------------

    /** Nom de la zone (saisi dans le panneau). */
    private EditBox nameBox;

    @Override
    protected void init() {
        super.init();
        nameBox = new EditBox(font, 0, 0, 100, 12, Component.translatable("robotics.pioneers.zone.name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(draft.label);
        nameBox.setHint(Component.translatable("robotics.pioneers.zone.name.hint").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setResponder(value -> draft.label = value.trim());
        nameBox.setVisible(false);
        addRenderableWidget(nameBox);
    }

    @Override
    protected int drawPanel(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w) {
        RoboticOrder live = isNew ? null : ClientRobotics.orders().stream().filter(o -> o.id == draft.id).findFirst().orElse(null);
        if (live != null) {
            y = section(graphics, x, y, w, Component.translatable("robotics.pioneers.planner.section.status"));
            y = statusBox(graphics, x, y, w, live) + 2;
        }
        //Nom de la zone
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.zone.name"), x, y + 3, 0.65F, D_DIM, 30);
        if (nameBox != null) {
            nameBox.setX(x + 32);
            nameBox.setY(y);
            nameBox.setWidth(w - 32);
            nameBox.setVisible(y >= TOP + 4 && y + 12 <= height - FOOTER);
        }
        y += 16;
        y = drawTask(graphics, mouseX, mouseY, x, y, w);
        y = drawSettings(graphics, mouseX, mouseY, x, y + 4, w);
        y = drawBounds(graphics, mouseX, mouseY, x, y + 4, w);
        y = drawStats(graphics, x, y + 4, w);
        y = section(graphics, x, y + 4, w, Component.translatable("robotics.pioneers.planner.section.assign"));
        return AssignControls.draw(this, graphics, mouseX, mouseY, x, y, w, draft);
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
        primary(graphics, mouseX, mouseY, saveX, y, x + w - saveX, 15, Component.translatable("robotics.pioneers.edit.save"), canSave(), this::save);
        if (!canSave()) {
            tooltip(mouseX, mouseY, saveX, y, x + w - saveX, 15, hasZone() ? Component.translatable("robotics.pioneers.zone.invalid", RoboticOrder.MAX_ZONE,
                  RoboticOrder.MAX_ZONE_HEIGHT) : Component.translatable("robotics.pioneers.planner.empty"));
        } else {
            tooltip(mouseX, mouseY, saveX, y, x + w - saveX, 15, Component.translatable("robotics.pioneers.planner.key", "Entrée / Enter")
                  .withStyle(ChatFormatting.GRAY));
        }
    }

    /** Tache : tuiles a icone, puis son nom, sa description et ses etapes. */
    private int drawTask(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w) {
        y = section(graphics, x, y, w, Component.translatable("robotics.pioneers.planner.section.task"));
        RobotTask[] tasks = RobotTask.ZONE_TASKS;
        //Deux rangees de trois : chasse, bucheronnage, agriculture / peche, elevage, apiculture
        int tw = (w - 2 * 2) / 3;
        for (int i = 0; i < tasks.length; i++) {
            RobotTask task = tasks[i];
            int tx = x + (i % 3) * (tw + 2), ty = y + (i / 3) * 22;
            iconChip(graphics, mouseX, mouseY, tx, ty, tw, 20, new ItemStack(task.tool), draft.task == task, task.color, () -> selectTask(task));
            tooltip(mouseX, mouseY, tx, ty, tw, 20, Component.translatable(task.getTranslationKey()),
                  Component.translatable(task.getTranslationKey() + ".desc").withStyle(ChatFormatting.GRAY),
                  Component.translatable("robotics.pioneers.planner.key", Integer.toString(i + 1)).withStyle(ChatFormatting.DARK_GRAY));
        }
        y += ((tasks.length + 2) / 3) * 22 + 2;
        GuiDraw.text(graphics, font, Component.translatable(draft.task.getTranslationKey()).withStyle(ChatFormatting.BOLD), x, y, 0.75F,
              0xFF000000 | draft.task.color, w);
        y += 9;
        y += wrapped(graphics, Component.translatable(draft.task.getTranslationKey() + ".desc"), x, y, 0.6F, D_DIM, w, 3);
        y += 2;
        y += wrapped(graphics, Component.translatable("robotics.pioneers.planner.steps",
              Component.translatable("robotics.pioneers.task.steps." + draft.task.name().toLowerCase(Locale.ROOT))), x, y, 0.58F, 0xFF7FE6C0, w, 2);
        return y;
    }

    private void selectTask(RobotTask task) {
        if (draft.task != task) {
            draft.task = task;
            applyTaskHeights();
            analyzeIn = 2;
        }
    }

    /** Reglages propres a la tache, et le mode repos. */
    private int drawSettings(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w) {
        y = section(graphics, x, y, w, Component.translatable("robotics.pioneers.planner.section.settings"));
        if (draft.task == RobotTask.HUNTER) {
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.zone.hunt"), x, y + 3, 0.65F, D_DIM, 40);
            String[] modes = {"all", "monsters", "animals"};
            int[] order = {RoboticOrder.HUNT_MONSTERS, RoboticOrder.HUNT_ANIMALS, RoboticOrder.HUNT_ALL};
            int cw = (w - 40 - 4) / 3;
            for (int i = 0; i < 3; i++) {
                int mode = order[i];
                int cx = x + 40 + i * (cw + 2);
                chip(graphics, mouseX, mouseY, cx, y, cw, 13, Component.translatable("robotics.pioneers.zone.hunt." + modes[mode]), draft.hunt == mode,
                      0xC0504A, () -> {
                          draft.hunt = mode;
                          analyzeIn = 1;
                      });
                tooltip(mouseX, mouseY, cx, y, cw, 13, Component.translatable("robotics.pioneers.zone.hunt." + modes[mode] + ".tooltip"));
            }
            y += 17;
        }
        if (draft.task == RobotTask.BREEDER || draft.task == RobotTask.HUNTER && draft.hunt != RoboticOrder.HUNT_MONSTERS) {
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.zone.herd"), x, y + 3, 0.65F, D_DIM, 40);
            int min = draft.task == RobotTask.HUNTER ? 0 : 1;
            stepper(graphics, mouseX, mouseY, x + 40, y, 48, draft.herd, min, RoboticOrder.MAX_HERD, 10, value -> draft.herd = value);
            Component hint = Component.translatable(draft.task == RobotTask.HUNTER && draft.herd == 0 ? "robotics.pioneers.zone.herd.none"
                                                                                                      : "robotics.pioneers.zone.herd.hint");
            tooltip(mouseX, mouseY, x, y, w, 12, Component.translatable("robotics.pioneers.zone.herd.tooltip"));
            y += 15;
            y += wrapped(graphics, hint, x, y, 0.58F, D_FAINT, w, 3) + 3;
        }
        if (draft.task == RobotTask.APIARIST) {
            //Part des recoltes en rayons (le reste en fioles de miel)
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.zone.honey"), x, y + 3, 0.65F, D_DIM, 40);
            int[] shares = {100, 75, 50, 25, 0};
            int cw = (w - 40 - 8) / 5;
            for (int i = 0; i < shares.length; i++) {
                int share = shares[i], cx = x + 40 + i * (cw + 2);
                chip(graphics, mouseX, mouseY, cx, y, cw, 13, Component.literal(share + "%"), draft.honeycomb == share, 0xFFC107, () -> draft.honeycomb = share);
            }
            tooltip(mouseX, mouseY, x, y, 40, 13, Component.translatable("robotics.pioneers.zone.honey"),
                  Component.translatable("robotics.pioneers.zone.honey.tooltip").withStyle(ChatFormatting.GRAY));
            y += 15;
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.zone.honey.split", draft.honeycomb, 100 - draft.honeycomb), x, y, 0.58F,
                  D_FAINT, w);
            y += 11;
        }
        if (draft.task == RobotTask.FARMER || draft.task == RobotTask.LUMBERJACK) {
            String key = draft.task == RobotTask.FARMER ? "farm" : "wood";
            toggle(graphics, mouseX, mouseY, x, y + 2, w, Component.translatable("robotics.pioneers.zone.replant." + key), draft.replant,
                  () -> draft.replant = !draft.replant);
            tooltip(mouseX, mouseY, x, y, w, 12, Component.translatable("robotics.pioneers.zone.replant." + key + ".tooltip"));
            y += 15;
            if (draft.replant) {
                ItemStack plant = draft.plantItem();
                GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.zone.plant." + key), x, y + 5, 0.65F, D_DIM, 50);
                int px = x + 52;
                darkSlot(graphics, px, y);
                if (!plant.isEmpty()) {
                    graphics.renderItem(plant, px, y);
                }
                Component plantName = plant.isEmpty() ? Component.translatable("robotics.pioneers.zone.plant.auto") : plant.getHoverName();
                int nameW = w - 72 - (plant.isEmpty() ? 0 : 14);
                int nameH = wrappedHeight(plantName, 0.6F, nameW, 3);
                wrapped(graphics, plantName, px + 20, y + Math.max(1, 9 - nameH / 2), 0.6F, D_TEXT, nameW, 3);
                click(px, y, 16, 16, () -> minecraft.setScreen(new ResourcePickerScreen(this, Resource.ITEMS,
                      Set.of(Resource.ITEMS), false, res -> {
                          var id = ForgeRegistries.ITEMS.getKey(res.item().getItem());
                          draft.plant = id == null ? "" : id.toString();
                      })));
                tooltip(mouseX, mouseY, px, y, 16, 16, Component.translatable("robotics.pioneers.zone.plant.tooltip"));
                if (!plant.isEmpty()) {
                    button(graphics, mouseX, mouseY, x + w - 13, y + 2, 13, 13, Component.literal("✕"), true, () -> draft.plant = "");
                    tooltip(mouseX, mouseY, x + w - 13, y + 2, 13, 13, Component.translatable("robotics.pioneers.zone.plant.auto"));
                }
                y += 20;
            }
        }
        toggle(graphics, mouseX, mouseY, x, y + 2, w, Component.translatable("robotics.pioneers.zone.rest"), draft.rest, () -> draft.rest = !draft.rest);
        tooltip(mouseX, mouseY, x, y, w, 12, Component.translatable("robotics.pioneers.zone.rest"),
              Component.translatable("robotics.pioneers.zone.rest.tooltip").withStyle(ChatFormatting.GRAY));
        return y + 14;
    }

    /** Bornes : une ligne par axe (bord bas / bord haut), et la taille. */
    private int drawBounds(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w) {
        y = section(graphics, x, y, w, Component.translatable("robotics.pioneers.planner.section.bounds"));
        if (!hasZone()) {
            y += wrapped(graphics, Component.translatable("robotics.pioneers.planner.empty"), x, y, 0.6F, 0xFFC8A8FF, w, 3);
            return y;
        }
        String[] axes = {"x", "y", "z"};
        int sw = (w - 14) / 2 - 2;
        for (int axis = 0; axis < 3; axis++) {
            int a = axis;
            GuiDraw.text(graphics, font, Component.literal(axes[axis].toUpperCase(Locale.ROOT)), x + 2, y + 3, 0.7F, 0xFF000000 | axisColor(axis), 10);
            int low = get(draft.min, axis), high = get(draft.max, axis);
            stepper(graphics, mouseX, mouseY, x + 12, y, sw, low, low - 512, high, 8, value -> bound(a, false, value));
            stepper(graphics, mouseX, mouseY, x + 14 + sw + 2, y, sw, high, low, high + 512, 8, value -> bound(a, true, value));
            tooltip(mouseX, mouseY, x + 12, y, sw, 12, Component.translatable("robotics.pioneers.preview.low." + axes[axis]));
            tooltip(mouseX, mouseY, x + 16 + sw, y, sw, 12, Component.translatable("robotics.pioneers.preview.high." + axes[axis]));
            y += 15;
        }
        int sx = draft.max.getX() - draft.min.getX() + 1, sy = draft.max.getY() - draft.min.getY() + 1, sz = draft.max.getZ() - draft.min.getZ() + 1;
        boolean tooBig = !draft.isValid();
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.preview.size", sx, sy, sz), x, y + 1, 0.65F,
              tooBig ? 0xFFFF6E5A : D_TEXT, w);
        if (tooBig) {
            y += 9;
            y += wrapped(graphics, Component.translatable("robotics.pioneers.zone.invalid", RoboticOrder.MAX_ZONE, RoboticOrder.MAX_ZONE_HEIGHT), x, y,
                  0.58F, 0xFFFF6E5A, w, 2);
        }
        return y + 10;
    }

    private void bound(int axis, boolean high, int value) {
        remember();
        if (axis == 1) {
            autoHeights = false;
        }
        int[] lo = {draft.min.getX(), draft.min.getY(), draft.min.getZ()}, hi = {draft.max.getX(), draft.max.getY(), draft.max.getZ()};
        if (high) {
            hi[axis] = Math.max(lo[axis], value);
        } else {
            lo[axis] = Math.min(hi[axis], value);
        }
        setSelection(new BlockPos(lo[0], lo[1], lo[2]), new BlockPos(hi[0], hi[1], hi[2]));
    }

    private static int get(BlockPos pos, int axis) {
        return axis == 0 ? pos.getX() : axis == 1 ? pos.getY() : pos.getZ();
    }

    /** Ce que la zone contient. */
    private int drawStats(GuiGraphics graphics, int x, int y, int w) {
        y = section(graphics, x, y, w, Component.translatable("robotics.pioneers.planner.section.content"));
        if (!hasZone()) {
            return y;
        }
        for (Stat stat : stats) {
            graphics.fill(x + 1, y + 2, x + 6, y + 7, 0xFF000000 | stat.color());
            y += wrapped(graphics, stat.text(), x + 10, y + 1, 0.62F, D_TEXT, w - 10, 2) + 2;
        }
        return y;
    }

    // ------------------------------------------------------------------
    // Clavier
    // ------------------------------------------------------------------

    @Override
    protected boolean keyExtra(int key, boolean ctrl, boolean shift) {
        if (ctrl && key == GLFW.GLFW_KEY_C) {
            copy();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            togglePaste();
            return true;
        }
        if (!ctrl && key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + RobotTask.ZONE_TASKS.length) {
            selectTask(RobotTask.ZONE_TASKS[key - GLFW.GLFW_KEY_1]);
            return true;
        }
        return false;
    }
}

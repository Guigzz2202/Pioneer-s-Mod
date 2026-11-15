package fr.pioneers.logistics.robotics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.client.PiKeys;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.logistics.rail.client.ClientRailMap;
import fr.pioneers.logistics.rail.client.RailText;
import fr.pioneers.core.network.PacketRail;
import fr.pioneers.core.network.PacketRoboticsMap;
import fr.pioneers.core.network.PiNetwork;
import fr.pioneers.logistics.rail.logistics.StationLogistics;
import fr.pioneers.logistics.rail.track.TrackQuality;
import fr.pioneers.logistics.rail.train.Train;
import fr.pioneers.core.registries.PiRail;
import fr.pioneers.core.registries.PiRobotics;
import fr.pioneers.logistics.robotics.Endpoint;
import fr.pioneers.logistics.robotics.RobotTask;
import fr.pioneers.logistics.robotics.RobotUnitEntity;
import fr.pioneers.logistics.robotics.RoboticOrder;
import fr.pioneers.logistics.robotics.RoboticsOrders;
import fr.pioneers.logistics.robotics.UnitKind;
import fr.pioneers.logistics.robotics.UnitTier;
import fr.pioneers.logistics.robotics.hub.RoboticHubData;
import fr.pioneers.logistics.robotics.logistics.MarkerRole;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Carte robotique (touche O) : le centre de commande de la robotique, en plein ecran.
 * <ul>
 *     <li>la carte du terrain vue du dessus, avec la couverture des reseaux, les hubs, les relais, les points logistiques, les unites en
 *     direct et leur trajet, les zones, les transports et les alertes (calques activables) ;</li>
 *     <li>des outils : selectionner (et regler a distance), tracer une zone, creer un transport en cliquant la source puis la cible ;</li>
 *     <li>un panneau : details de la selection, et onglets Ordres, Reseau (stocks), Unites, Alertes.</li>
 * </ul>
 * Glisser pour deplacer la carte, molette pour zoomer.
 */
public class RoboticsMapScreen extends RoboticsScreen {

    private static final int TOP = 26, PANEL = 178;
    private static final float MIN_SCALE = 0.5F, MAX_SCALE = 12F;

    private enum Tool {SELECT, ZONE, WORKS, TRANSFER}

    private enum Tab {ORDERS, NETWORK, UNITS, ALERTS, RAIL}

    private enum Layer {COVERAGE, POINTS, UNITS, ROUTES, ZONES, ALERTS, RAIL}

    private static double camX = Double.NaN, camZ;
    private static float scale = 3;
    private static Tool tool = Tool.SELECT;
    private static Tab tab = Tab.ORDERS;
    private static final boolean[] LAYERS = {true, true, true, true, true, true, true};
    /** 0.20.5 : train suivi par la camera (null : aucun) ; liste des gares ouverte pour « Envoyer a ». */
    @Nullable
    private static UUID follow;
    private boolean sendOpen;
    /** Filtres par type : zones par tache, points par role, unites par genre, hubs, relais, lignes dedies. */
    private static final EnumSet<RobotTask> HIDDEN_TASKS = EnumSet.noneOf(RobotTask.class);
    private static final EnumSet<MarkerRole> HIDDEN_ROLES = EnumSet.noneOf(MarkerRole.class);
    private static final EnumSet<UnitKind> HIDDEN_KINDS = EnumSet.noneOf(UnitKind.class);
    private static boolean hideHubs, hideRelays, hideLines;
    private boolean filtersOpen;
    /** Ordres deplies dans la liste. */
    private static final Set<Integer> EXPANDED = new HashSet<>();
    /** Selection : clic sans glisser (sinon la carte defile, meme sur une zone). */
    @Nullable
    private Object pressHit;
    private boolean pressPending;
    private double pressX, pressY;

    /** Selection : un point, un noeud, une unite ou un ordre (zone, transport). */
    @Nullable
    private Object selected;
    /** Outil transport : source choisie (point), ou « reseau ». */
    @Nullable
    private ClientRoboticsMap.Point transferSource;
    private boolean transferFromNetwork, transferStarted;
    /** Outil zone : coin de depart (monde). */
    private double dragStartX = Double.NaN, dragStartZ;
    private double dragEndX, dragEndZ;
    private boolean panning;
    private double panX, panY;
    private int listScroll, ticks;
    private EditBox search;
    /** Intendants : saisie du nombre par genre (onglet Unites). */
    private final EditBox[] stewardBoxes = new EditBox[UnitKind.values().length];

    public RoboticsMapScreen() {
        super(Component.translatable("robotics.pioneers.map.title"), null, 10_000, 10_000);
        drawPanel = false;
        dark = true;
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
        if (Double.isNaN(camX) && minecraft != null && minecraft.player != null) {
            camX = minecraft.player.getX();
            camZ = minecraft.player.getZ();
        }
        search = new EditBox(font, width - PANEL + 6, TOP + 40, PANEL - 12, 12, Component.translatable("robotics.pioneers.picker.search"));
        search.setHint(Component.translatable("robotics.pioneers.picker.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setVisible(tab == Tab.NETWORK || tab == Tab.ORDERS);
        addRenderableWidget(search);
        for (UnitKind kind : UnitKind.values()) {
            int index = kind.ordinal();
            EditBox box = new EditBox(font, 0, 0, 26, 12, Component.translatable("entity.pioneers.unit_" + kind.id()));
            box.setMaxLength(3);
            box.setFilter(text -> text.isEmpty() || text.equals("∞") || text.chars().allMatch(Character::isDigit));
            box.setResponder(text -> {
                if (box.isFocused() && !text.isEmpty() && !text.equals("∞")) {
                    int value = Integer.parseInt(text);
                    setPassive(index, value >= RoboticsOrders.PASSIVE_ALL ? RoboticsOrders.PASSIVE_ALL : value);
                }
            });
            box.setVisible(false);
            stewardBoxes[index] = box;
            addRenderableWidget(box);
        }
        PiNetwork.sendToServer(new PacketRoboticsMap.Watch(true));
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % 40 == 0) {
            PiNetwork.sendToServer(new PacketRoboticsMap.Watch(true));
        }
        if (ticks % 10 == 1) {
            int vx0 = 0, vx1 = mapWidth();
            int cx0 = Mth.floor(toWorldX(vx0)) >> 4, cx1 = Mth.floor(toWorldX(vx1)) >> 4;
            int cz0 = Mth.floor(toWorldZ(TOP)) >> 4, cz1 = Mth.floor(toWorldZ(height)) >> 4;
            ClientRoboticsMap.requestTiles(cx0, cz0, cx1, cz1);
        }
        search.setVisible(tab == Tab.NETWORK || tab == Tab.ORDERS);
    }

    @Override
    public void removed() {
        super.removed();
        PiNetwork.sendToServer(new PacketRoboticsMap.Watch(false));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        for (EditBox box : stewardBoxes) {
            if (box != null && box.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                    box.setFocused(false);
                    return true;
                }
                return box.keyPressed(key, scanCode, modifiers) || box.canConsumeInput();
            }
        }
        if (!search.isFocused() && PiKeys.ROBOTICS.matches(key, scanCode)) {
            onClose();
            return true;
        }
        if (!search.isFocused()) {
            double step = 24 / scale;
            switch (key) {
                case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> camX -= step;
                case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> camX += step;
                case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> camZ -= step;
                case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> camZ += step;
                default -> {
                    return super.keyPressed(key, scanCode, modifiers);
                }
            }
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    // ------------------------------------------------------------------
    // Reperes
    // ------------------------------------------------------------------

    private int mapWidth() {
        return width - PANEL;
    }

    private double toWorldX(double sx) {
        return camX + (sx - mapWidth() / 2.0) / scale;
    }

    private double toWorldZ(double sy) {
        return camZ + (sy - (TOP + (height - TOP) / 2.0)) / scale;
    }

    private float toScreenX(double wx) {
        return (float) (mapWidth() / 2.0 + (wx - camX) * scale);
    }

    private float toScreenY(double wz) {
        return (float) (TOP + (height - TOP) / 2.0 + (wz - camZ) * scale);
    }

    private boolean inMap(double mx, double my) {
        return mx >= 0 && mx < mapWidth() && my >= TOP && my < height;
    }

    private void centerOn(double x, double z) {
        camX = x;
        camZ = z;
    }

    private static boolean visible(RoboticOrder order) {
        if (ClientRobotics.HIDDEN_ORDERS.contains(order.id)) {
            return false;
        }
        return order.type == RoboticOrder.Type.ZONE ? !HIDDEN_TASKS.contains(order.task) : !hideLines;
    }

    private static boolean visible(ClientRoboticsMap.Point point) {
        return !HIDDEN_ROLES.contains(point.config().role);
    }

    private static boolean visible(ClientRoboticsMap.Unit unit) {
        return !HIDDEN_KINDS.contains(unit.kind());
    }

    private static boolean visible(ClientRoboticsMap.Node node) {
        return node.hub() ? !hideHubs : !hideRelays;
    }

    // ------------------------------------------------------------------
    // Rendu
    // ------------------------------------------------------------------

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (follow != null) {
            ClientRailMap.MapTrain t = ClientRailMap.train(follow);
            if (t == null) {
                follow = null;
            } else {
                centerOn(t.headX(), t.headZ());
            }
        }
        if (selected instanceof ClientRailMap.MapTrain old) {
            //Le train choisi suit les mises a jour
            ClientRailMap.MapTrain now = ClientRailMap.train(old.id());
            selected = now;
        }
        graphics.fill(0, 0, width, height, 0xFF0B1016);
        drawMap(graphics, mouseX, mouseY);
        drawTopBar(graphics, mouseX, mouseY);
        drawBottomBar(graphics, mouseX, mouseY);
        drawPanelSide(graphics, mouseX, mouseY);
        if (filtersOpen) {
            drawFilters(graphics, mouseX, mouseY);
        }
    }

    private static final int FILTER_W = 190, BAR = 18;
    private int filterButtonX = 4;

    /** Barre du bas de la carte : calques, filtres (a gauche) et coordonnees sous la souris (a droite). */
    private void drawBottomBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = height - BAR, mw = mapWidth();
        graphics.flush();
        graphics.fill(0, y, mw, height, 0xE01B2128);
        graphics.fill(0, y, mw, y + 1, 0xFF2C3640);
        int x = 4;
        int hidden = HIDDEN_TASKS.size() + HIDDEN_ROLES.size() + HIDDEN_KINDS.size() + (hideHubs ? 1 : 0) + (hideRelays ? 1 : 0) + (hideLines ? 1 : 0);
        Component filters = Component.translatable("robotics.pioneers.map.filters").append(hidden > 0 ? " (" + hidden + ")" : "").append(" ▴");
        int fw = Math.max(54, font.width(filters) * 3 / 4 + 12);
        filterButtonX = x;
        chip(graphics, mouseX, mouseY, x, y + 3, fw, 13, filters, filtersOpen || hidden > 0, 0x3A6FA0, () -> filtersOpen = !filtersOpen);
        tooltip(mouseX, mouseY, x, y + 3, fw, 13, Component.translatable("robotics.pioneers.map.filters.tooltip"));
        x += fw + 6;
        for (Layer layer : Layer.values()) {
            Component label = Component.translatable("robotics.pioneers.map.layer." + layer.name().toLowerCase(Locale.ROOT));
            int w = Math.max(30, font.width(label) * 3 / 4 + 8);
            if (x + w > mw - 110) {
                break;
            }
            chip(graphics, mouseX, mouseY, x, y + 3, w, 13, label, LAYERS[layer.ordinal()], 0x3A6FA0, () -> LAYERS[layer.ordinal()] = !LAYERS[layer.ordinal()]);
            x += w + 1;
        }
        if (inMap(mouseX, mouseY) && mouseY < y) {
            int wx = Mth.floor(toWorldX(mouseX)), wz = Mth.floor(toWorldZ(mouseY));
            int h = ClientRoboticsMap.height(wx, wz);
            String text = wx + ", " + wz + (h == Integer.MIN_VALUE ? "" : " (y " + h + ")");
            GuiDraw.right(graphics, font, Component.literal(text), mw - 6, y + 6, 0.7F, 0xFFB0C4D0);
        }
    }

    private int filterX() {
        return filterButtonX;
    }

    private int filterY() {
        return height - BAR - 2 - filterHeight();
    }

    private int filterHeight() {
        return 12 + rowsHeight(RobotTask.ZONE_TASKS.length + 1) + 12 + rowsHeight(MarkerRole.values().length) + 12 + rowsHeight(UnitKind.values().length) + 12
               + rowsHeight(3) + 4;
    }

    private static int rowsHeight(int count) {
        return ((count + 1) / 2) * 13;
    }

    /** Menu des filtres : ce qui s'affiche sur la carte, type par type. */
    private void drawFilters(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = filterX(), y = filterY(), w = FILTER_W, h = filterHeight();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF3A6FA0);
        graphics.fill(x, y, x + w, y + h, 0xF0141A20);
        int cx = x + 4, cw = (w - 10) / 2;
        y += 3;
        //Zones par tache
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.map.filters.zones"), cx, y, 0.6F, 0xFF7FE6C0);
        y += 9;
        List<RobotTask> tasks = new ArrayList<>(List.of(RobotTask.ZONE_TASKS));
        tasks.add(RobotTask.BUILDER);
        for (int i = 0; i < tasks.size(); i++) {
            RobotTask task = tasks.get(i);
            int fx = cx + (i % 2) * (cw + 2), fy = y + (i / 2) * 13;
            toggle(graphics, mouseX, mouseY, fx, fy, cw, Component.translatable(task.getTranslationKey()), !HIDDEN_TASKS.contains(task), () -> flip(HIDDEN_TASKS, task));
        }
        y += rowsHeight(tasks.size()) + 3;
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.map.filters.points"), cx, y, 0.6F, 0xFF7FE6C0);
        y += 9;
        MarkerRole[] roles = MarkerRole.values();
        for (int i = 0; i < roles.length; i++) {
            MarkerRole role = roles[i];
            int fx = cx + (i % 2) * (cw + 2), fy = y + (i / 2) * 13;
            toggle(graphics, mouseX, mouseY, fx, fy, cw, Component.translatable(role.getTranslationKey()), !HIDDEN_ROLES.contains(role), () -> flip(HIDDEN_ROLES, role));
        }
        y += rowsHeight(roles.length) + 3;
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.map.filters.units"), cx, y, 0.6F, 0xFF7FE6C0);
        y += 9;
        UnitKind[] kinds = UnitKind.values();
        for (int i = 0; i < kinds.length; i++) {
            UnitKind kind = kinds[i];
            int fx = cx + (i % 2) * (cw + 2), fy = y + (i / 2) * 13;
            toggle(graphics, mouseX, mouseY, fx, fy, cw, Component.translatable("entity.pioneers.unit_" + kind.id()), !HIDDEN_KINDS.contains(kind),
                  () -> flip(HIDDEN_KINDS, kind));
        }
        y += rowsHeight(kinds.length) + 3;
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.map.filters.other"), cx, y, 0.6F, 0xFF7FE6C0);
        y += 9;
        toggle(graphics, mouseX, mouseY, cx, y, cw, Component.translatable("robotics.pioneers.map.filters.hubs"), !hideHubs, () -> hideHubs = !hideHubs);
        toggle(graphics, mouseX, mouseY, cx + cw + 2, y, cw, Component.translatable("robotics.pioneers.map.filters.relays"), !hideRelays,
              () -> hideRelays = !hideRelays);
        toggle(graphics, mouseX, mouseY, cx, y + 13, cw, Component.translatable("robotics.pioneers.map.filters.lines"), !hideLines, () -> hideLines = !hideLines);
        graphics.pose().popPose();
    }

    private static <T extends Enum<T>> void flip(EnumSet<T> set, T value) {
        if (!set.remove(value)) {
            set.add(value);
        }
    }

    private boolean overFilters(double mouseX, double mouseY) {
        return filtersOpen && mouseX >= filterX() - 1 && mouseX < filterX() + FILTER_W + 1 && mouseY >= filterY() - 1 && mouseY < filterY() + filterHeight() + 1;
    }

    private void drawMap(GuiGraphics graphics, int mouseX, int mouseY) {
        int mw = mapWidth();
        graphics.enableScissor(0, TOP, mw, height - BAR);
        //Terrain : regions de 128 blocs
        int rx0 = Math.floorDiv(Mth.floor(toWorldX(0)), 128), rx1 = Math.floorDiv(Mth.floor(toWorldX(mw)), 128);
        int rz0 = Math.floorDiv(Mth.floor(toWorldZ(TOP)), 128), rz1 = Math.floorDiv(Mth.floor(toWorldZ(height)), 128);
        PoseStack pose = graphics.pose();
        for (int rz = rz0; rz <= rz1; rz++) {
            for (int rx = rx0; rx <= rx1; rx++) {
                ResourceLocation texture = ClientRoboticsMap.region(rx, rz);
                if (texture == null) {
                    continue;
                }
                pose.pushPose();
                pose.translate(toScreenX(rx * 128.0), toScreenY(rz * 128.0), 0);
                pose.scale(scale, scale, 1);
                graphics.blit(texture, 0, 0, 0, 0, 128, 128, 128, 128);
                pose.popPose();
            }
        }
        //Voile sombre pour faire ressortir les calques
        graphics.fill(0, TOP, mw, height, 0x38000000);
        if (LAYERS[Layer.COVERAGE.ordinal()]) {
            drawCoverage(graphics);
        }
        if (LAYERS[Layer.ZONES.ordinal()]) {
            for (RoboticOrder order : ClientRobotics.orders()) {
                if (order.type == RoboticOrder.Type.ZONE && order.min != null && order.max != null && visible(order)) {
                    float x0 = toScreenX(order.min.getX()), x1 = toScreenX(order.max.getX() + 1);
                    float y0 = toScreenY(order.min.getZ()), y1 = toScreenY(order.max.getZ() + 1);
                    int color = order.task.color;
                    rect(graphics, x0, y0, x1, y1, (order.paused ? 0x10000000 : 0x30000000) | color);
                    outline(graphics, x0, y0, x1, y1, (selected == order ? 0xFF000000 : 0xCC000000) | (selected == order ? 0xFFFFFF : color));
                    if (scale >= 1.5F) {
                        graphics.renderItem(new ItemStack(order.task.tool), (int) x0 + 2, (int) y0 + 2);
                        if (!order.label.isEmpty() && x1 - x0 > 30) {
                            GuiDraw.text(graphics, font, Component.literal(order.label), x0 + 3, y0 + 19, 0.6F, 0xFFFFFFFF, (int) (x1 - x0) - 6);
                        }
                    }
                }
            }
        }
        if (LAYERS[Layer.ROUTES.ordinal()]) {
            float time = ticks + minecraft.getFrameTime();
            for (RoboticOrder order : ClientRobotics.orders()) {
                if (order.type == RoboticOrder.Type.TRANSFER && order.target != null && visible(order)) {
                    Endpoint from = order.source;
                    float tx = toScreenX(order.target.pos().getX() + 0.5), ty = toScreenY(order.target.pos().getZ() + 0.5);
                    if (from != null) {
                        float fx = toScreenX(from.pos().getX() + 0.5), fy = toScreenY(from.pos().getZ() + 0.5);
                        dashed(graphics, fx, fy, tx, ty, selected == order ? 0xFFFFFFFF : order.paused ? 0x8055A0C0 : 0xFF4FC3F7, time);
                    }
                    ring(graphics, tx, ty, 4, 0xFFFFB74D);
                }
            }
        }
        if (LAYERS[Layer.RAIL.ordinal()]) {
            drawRail(graphics, mouseX, mouseY);
        }
        //Noeuds
        for (ClientRoboticsMap.Network network : ClientRoboticsMap.networks) {
            for (ClientRoboticsMap.Node node : network.nodes()) {
                if (!visible(node)) {
                    continue;
                }
                float x = toScreenX(node.pos().getX() + 0.5), y = toScreenY(node.pos().getZ() + 0.5);
                ItemStack icon = node.hub() ? new ItemStack(PiRobotics.UNITS.get(node.kind()).get(UnitTier.T1).get()) : new ItemStack(PiRobotics.RELAY);
                int size = node.hub() ? 12 : 9;
                graphics.fill((int) x - size, (int) y - size, (int) x + size, (int) y + size, selected == node ? 0xE0FFFFFF : 0xC0101820);
                graphics.fill((int) x - size + 1, (int) y - size + 1, (int) x + size - 1, (int) y + size - 1, node.hub() ? 0xFF1C3A2A : 0xFF16283A);
                graphics.renderItem(icon, (int) x - 8, (int) y - 8);
                if (node.hub() && node.active() > 0) {
                    GuiDraw.center(graphics, font, Component.literal(Integer.toString(node.active())), x + size - 2, y + size - 6, 0.6F, 0xFF55E07A, 20);
                }
            }
        }
        if (LAYERS[Layer.POINTS.ordinal()]) {
            for (ClientRoboticsMap.Point point : ClientRoboticsMap.points) {
                if (!visible(point)) {
                    continue;
                }
                float x = toScreenX(point.pos().getX() + 0.5), y = toScreenY(point.pos().getZ() + 0.5);
                int color = 0xFF000000 | point.config().role.color;
                boolean pick = tool == Tool.TRANSFER && Math.hypot(mouseX - x, mouseY - y) < 7;
                if (scale >= 4) {
                    graphics.fill((int) x - 9, (int) y - 9, (int) x + 9, (int) y + 9, selected == point || pick ? 0xFFFFFFFF : color);
                    graphics.fill((int) x - 8, (int) y - 8, (int) x + 8, (int) y + 8, 0xFF1A1F26);
                    graphics.renderItem(point.icon(), (int) x - 8, (int) y - 8);
                } else {
                    graphics.fill((int) x - 3, (int) y - 3, (int) x + 4, (int) y + 4, selected == point || pick ? 0xFFFFFFFF : 0xFF000000);
                    graphics.fill((int) x - 2, (int) y - 2, (int) x + 3, (int) y + 3, color);
                }
                if (point == transferSource) {
                    ring(graphics, x, y, 11, 0xFF4FC3F7);
                }
            }
        }
        if (LAYERS[Layer.UNITS.ordinal()]) {
            for (ClientRoboticsMap.Unit unit : ClientRoboticsMap.units) {
                if (!visible(unit)) {
                    continue;
                }
                float x = toScreenX(unit.x()), y = toScreenY(unit.z());
                int color = unitColor(unit.kind());
                if (unit.moving() && !Double.isNaN(unit.tx())) {
                    line(graphics, x, y, toScreenX(unit.tx()), toScreenY(unit.tz()), 1, (color & 0xFFFFFF) | 0x50000000);
                }
                int r = unit.kind() == UnitKind.ROVER ? 3 : 2;
                graphics.fill((int) x - r - 1, (int) y - r - 1, (int) x + r + 2, (int) y + r + 2, selected == unit ? 0xFFFFFFFF : 0xFF000000);
                graphics.fill((int) x - r, (int) y - r, (int) x + r + 1, (int) y + r + 1, color);
                if (unit.battery() < 20) {
                    graphics.fill((int) x + r + 1, (int) y - r - 2, (int) x + r + 3, (int) y - r, (ticks / 5) % 2 == 0 ? 0xFFFF4040 : 0xFF601010);
                }
            }
        }
        if (LAYERS[Layer.ALERTS.ordinal()]) {
            for (ClientRoboticsMap.Alert alert : ClientRoboticsMap.alerts) {
                float x = toScreenX(alert.pos().getX() + 0.5), y = toScreenY(alert.pos().getZ() + 0.5);
                if ((ticks / 8) % 2 == 0) {
                    triangle(graphics, x, y - 10, 0xFF000000 | alert.type().color);
                }
                if (alert.recurring()) {
                    ring(graphics, x, y - 9, 8, 0xFFFFA040);
                }
            }
        }
        //Joueur
        if (minecraft.player != null) {
            float px = toScreenX(minecraft.player.getX()), py = toScreenY(minecraft.player.getZ());
            float yaw = minecraft.player.getYRot() * Mth.DEG_TO_RAD;
            float dx = -Mth.sin(yaw), dz = Mth.cos(yaw);
            line(graphics, px, py, px + dx * 8, py + dz * 8, 2, 0xFFFFFFFF);
            graphics.fill((int) px - 2, (int) py - 2, (int) px + 3, (int) py + 3, 0xFFFFFFFF);
        }
        //Outils en cours
        if (tool == Tool.ZONE && !Double.isNaN(dragStartX)) {
            float x0 = toScreenX(Math.min(dragStartX, dragEndX)), x1 = toScreenX(Math.max(dragStartX, dragEndX) + 1);
            float y0 = toScreenY(Math.min(dragStartZ, dragEndZ)), y1 = toScreenY(Math.max(dragStartZ, dragEndZ) + 1);
            rect(graphics, x0, y0, x1, y1, 0x3355E0B0);
            outline(graphics, x0, y0, x1, y1, 0xFF55E0B0);
            int sx = Math.abs(Mth.floor(dragEndX) - Mth.floor(dragStartX)) + 1, sz = Math.abs(Mth.floor(dragEndZ) - Mth.floor(dragStartZ)) + 1;
            GuiDraw.text(graphics, font, Component.literal(sx + " × " + sz), x1 + 3, y1 + 2, 0.75F, 0xFFFFFFFF);
        }
        if (tool == Tool.TRANSFER && transferStarted && inMap(mouseX, mouseY)) {
            float fx = transferSource != null ? toScreenX(transferSource.pos().getX() + 0.5) : mouseX - 1;
            float fy = transferSource != null ? toScreenY(transferSource.pos().getZ() + 0.5) : mouseY - 1;
            if (transferSource != null) {
                dashed(graphics, fx, fy, mouseX, mouseY, 0xFF4FC3F7, ticks);
            }
        }
        //Infobulle de ce qui est sous la souris
        if (inMap(mouseX, mouseY) && mouseY < height - BAR) {
            if (!overFilters(mouseX, mouseY)) {
                hoverTooltip(mouseX, mouseY);
            }
        }
        graphics.flush();
        graphics.disableScissor();
    }

    private static int unitColor(UnitKind kind) {
        return switch (kind) {
            case DRONE -> 0xFF5AE1FF;
            case ROBOT -> 0xFF78FF96;
            case ROVER -> 0xFFFFBA3C;
            case TURTLE -> 0xFF8BC34A;
        };
    }

    private void hoverTooltip(int mouseX, int mouseY) {
        Object hover = pick(mouseX, mouseY);
        if (hover instanceof ClientRoboticsMap.Point point) {
            tooltip(List.of(Component.literal(point.name()).withStyle(style -> style.withColor(point.config().role.color)),
                  Component.translatable(point.config().role.getTranslationKey()).withStyle(ChatFormatting.GRAY)));
        } else if (hover instanceof ClientRoboticsMap.Unit unit) {
            tooltip(List.of(Component.translatable("robotics.pioneers.map.unit", Component.translatable("entity.pioneers.unit_" + unit.kind().id()),
                        unit.tier() + 1), phaseText(unit.phase()).withStyle(ChatFormatting.GRAY),
                  Component.translatable("robotics.pioneers.map.battery", unit.battery()).withStyle(unit.battery() < 20 ? ChatFormatting.RED
                                                                                                                             : ChatFormatting.GREEN)));
        } else if (hover instanceof ClientRoboticsMap.Node node) {
            tooltip(List.of(node.hub() ? Component.translatable("robotics.pioneers.hub." + node.kind().id())
                                       : Component.translatable("block.pioneers.robotic_relay"),
                  Component.literal(node.pos().toShortString()).withStyle(ChatFormatting.GRAY)));
        } else if (hover instanceof ClientRoboticsMap.Alert alert) {
            tooltip(List.of(Component.translatable(alert.type().getTranslationKey()).withStyle(style -> style.withColor(alert.type().color)),
                  Component.literal(alert.pos().toShortString()).withStyle(ChatFormatting.GRAY)));
        } else if (hover instanceof ClientRailMap.MapTrain t) {
            tooltip(List.of(Component.literal(t.name()).withStyle(ChatFormatting.BOLD),
                  Component.translatable("rail.pioneers.status." + t.status().name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GRAY),
                  t.dest().isEmpty() ? Component.empty() : Component.translatable("rail.pioneers.train.dest", RailText.station(t.dest())).withStyle(ChatFormatting.GRAY)));
        } else if (hover instanceof ClientRailMap.MapStation st) {
            tooltip(List.of(RailText.station(st.name()).copy().withStyle(ChatFormatting.BOLD),
                  Component.translatable("rail.pioneers.station.role." + st.role().name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GRAY),
                  Component.translatable(st.comingKey(), st.coming()).withStyle(ChatFormatting.GRAY)));
        } else if (hover instanceof ClientRailMap.MapAlert alert) {
            tooltip(List.of(Component.translatable("rail.pioneers.alert." + alert.type().name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GOLD),
                  Component.literal(ClientRailMap.subject(alert)).withStyle(ChatFormatting.GRAY)));
        }
    }

    private static MutableComponent phaseText(int phase) {
        RobotUnitEntity.Phase[] phases = RobotUnitEntity.Phase.values();
        return Component.translatable("robotics.pioneers.unit.phase." + phases[Mth.clamp(phase, 0, phases.length - 1)].name()
              .toLowerCase(Locale.ROOT));
    }

    private void drawTopBar(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, TOP, 0xFF1B2128);
        graphics.fill(0, TOP - 1, width, TOP, 0xFF3FD0A0);
        graphics.drawString(font, title, 6, 9, 0xFFE0F5EA, false);
        int x = 6 + font.width(title) + 10;
        for (Tool t : Tool.values()) {
            Component label = Component.translatable("robotics.pioneers.map.tool." + t.name().toLowerCase(Locale.ROOT));
            int w = Math.max(44, font.width(label) * 3 / 4 + 10);
            chip(graphics, mouseX, mouseY, x, 5, w, 15, label, tool == t, 0x2E9E78, () -> {
                //Zone et travaux : la vue libre s'ouvre tout de suite, sur le joueur
                if (t == Tool.ZONE || t == Tool.WORKS) {
                    Vec3 at = minecraft.player == null ? new Vec3(camX, 64, camZ) : minecraft.player.position();
                    if (t == Tool.ZONE) {
                        ClientRobotics.planZoneAt(this, at.x, at.y, at.z);
                    } else {
                        minecraft.setScreen(new WorksPlannerScreen(this, null).focusOn(at));
                    }
                    return;
                }
                tool = t;
                transferSource = null;
                transferStarted = false;
                transferFromNetwork = false;
                dragStartX = Double.NaN;
            });
            tooltip(mouseX, mouseY, x, 5, w, 15, Component.translatable("robotics.pioneers.map.tool." + t.name().toLowerCase(Locale.ROOT) + ".tooltip"));
            x += w + 2;
        }
        //« Moi » apres les outils (jamais par-dessus, meme sur un petit ecran)
        button(graphics, mouseX, mouseY, Math.max(x + 4, width - PANEL - 38), 5, 34, 15, Component.translatable("robotics.pioneers.map.me"), true, () -> {
            if (minecraft.player != null) {
                centerOn(minecraft.player.getX(), minecraft.player.getZ());
            }
        });
        //Aide de l'outil
        Component hint = switch (tool) {
            case SELECT -> Component.translatable("robotics.pioneers.map.hint.select");
            case ZONE, WORKS -> Component.translatable("robotics.pioneers.map.hint.zone");
            case TRANSFER -> Component.translatable(transferStarted ? "robotics.pioneers.map.hint.transfer_target"
                                                                    : "robotics.pioneers.map.hint.transfer_source");
        };
        //Bandeau d'aide tenu dans la carte : sur deux lignes au besoin
        int hw = Math.min(mapWidth() - 8, (int) (font.width(hint) * 0.75F) + 12);
        int hintLines = Math.min(2, font.split(hint, (int) ((hw - 8) / 0.7F)).size());
        int hx = (mapWidth() - hw) / 2;
        graphics.fill(hx, TOP + 4, hx + hw, TOP + 8 + hintLines * 8, 0xB0000000);
        if (hintLines == 1) {
            GuiDraw.center(graphics, font, hint, mapWidth() / 2F, TOP + 7, 0.75F, 0xFFE0F5EA, hw - 6);
        } else {
            GuiDraw.wrapped(graphics, font, hint, hx + 4, TOP + 6, 0.7F, 0xFFE0F5EA, hw - 8, 2, 8);
        }
    }

    // ------------------------------------------------------------------
    // Panneau de droite
    // ------------------------------------------------------------------

    /** Nombre d'intendants d'un genre (PASSIVE_ALL : sans limite). */
    private void setPassive(int kind, int value) {
        BlockPos at = minecraft.player == null ? BlockPos.ZERO : minecraft.player.blockPosition();
        value = Mth.clamp(value, 0, RoboticsOrders.PASSIVE_ALL);
        PiNetwork.sendToServer(new PacketRoboticsMap.HubAction(at, PacketRoboticsMap.HubAction.PASSIVE + kind * 100 + value));
        if (kind < ClientRoboticsMap.passive.length) {
            ClientRoboticsMap.passive[kind] = value;
        }
    }

    private void drawPanelSide(GuiGraphics graphics, int mouseX, int mouseY) {
        for (EditBox box : stewardBoxes) {
            if (box != null) {
                box.setVisible(false);
            }
        }
        int x0 = width - PANEL, y = TOP;
        graphics.fill(x0, y, width, height, 0xFF141A20);
        graphics.fill(x0, y, x0 + 1, height, 0xFF3FD0A0);
        int tw = (PANEL - 8) / Tab.values().length;
        for (Tab t : Tab.values()) {
            int tx = x0 + 4 + t.ordinal() * tw;
            Component label = Component.translatable("robotics.pioneers.map.tab." + t.name().toLowerCase(Locale.ROOT));
            //Alertes en attente : une pastille rouge (le nombre est dans l'onglet)
            if (t == Tab.ALERTS && !ClientRoboticsMap.alerts.isEmpty()) {
                tooltip(mouseX, mouseY, tx, y + 4, tw - 2, 14, Component.translatable("robotics.pioneers.map.alerts.count", ClientRoboticsMap.alerts.size()));
            }
            chip(graphics, mouseX, mouseY, tx, y + 4, tw - 2, 14, label, tab == t, 0x2E9E78, () -> {
                if (tab != t) {
                    search.setValue("");
                }
                tab = t;
                listScroll = 0;
                search.setVisible(tab == Tab.NETWORK || tab == Tab.ORDERS);
            });
            if (t == Tab.ALERTS && !ClientRoboticsMap.alerts.isEmpty()) {
                int bx = tx + tw - 8, by = y + 3;
                graphics.fill(bx - 1, by - 1, bx + 6, by + 6, 0xFF101418);
                graphics.fill(bx, by, bx + 5, by + 5, (ticks / 10) % 2 == 0 ? 0xFFFF5A4D : 0xFFB03A30);
            }
        }
        y += 22;
        //Une unite choisie dans l'onglet Unites y est deja surlignee : son bloc de selection pousserait la liste hors de l'ecran
        if (selected != null && !(tab == Tab.UNITS && selected instanceof ClientRoboticsMap.Unit)) {
            y = drawSelection(graphics, mouseX, mouseY, x0 + 4, y, PANEL - 8);
            graphics.fill(x0 + 4, y, width - 4, y + 1, 0xFF2C3640);
            y += 4;
        }
        switch (tab) {
            case ORDERS -> drawOrders(graphics, mouseX, mouseY, x0 + 4, y, PANEL - 8, height - y - 4);
            case NETWORK -> drawStocks(graphics, mouseX, mouseY, x0 + 4, y, PANEL - 8, height - y - 4);
            case UNITS -> drawUnits(graphics, mouseX, mouseY, x0 + 4, y, PANEL - 8, height - y - 4);
            case ALERTS -> drawAlerts(graphics, mouseX, mouseY, x0 + 4, y, PANEL - 8, height - y - 4);
            case RAIL -> drawRailTab(graphics, mouseX, mouseY, x0 + 4, y, PANEL - 8, height - y - 4);
        }
    }

    private int drawSelection(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w) {
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.map.selection"), x, y, 0.6F, 0xFF7FA8A0);
        y += 8;
        if (selected instanceof ClientRoboticsMap.Point point) {
            graphics.renderItem(point.icon(), x, y);
            GuiDraw.text(graphics, font, Component.literal(point.name()), x + 19, y + 1, 0.75F, 0xFFE0F5EA, w - 20);
            GuiDraw.text(graphics, font, Component.translatable(point.config().role.getTranslationKey()), x + 19, y + 9, 0.6F,
                  0xFF000000 | point.config().role.color, w - 20);
            y += 19;
            GuiDraw.text(graphics, font, describe(point.endpoint()), x, y, 0.6F, 0xFFB0C4D0, w);
            y += 9;
            for (var entry : point.config().entries) {
                if (y > height - 110) {
                    break;
                }
                ResourceIcon.draw(graphics, entry.res(), x, y - 2);
                GuiDraw.text(graphics, font, entry.res().name(), x + 18, y + 2, 0.6F, 0xFFD0E0E8, w - 64);
                if (point.config().role.requests) {
                    GuiDraw.right(graphics, font, Component.literal(OrderTexts.amount(entry.amount(), entry.res().type)), x + w, y + 2, 0.6F, 0xFF8CF5C6);
                }
                y += 16;
            }
            button(graphics, mouseX, mouseY, x, y, 54, 14, Component.translatable("robotics.pioneers.map.edit_point"), true,
                  () -> minecraft.setScreen(new PointEditScreen(this, point.pos(), point.endpoint().pos(), point.config().copy(), point.icon(), point.name(),
                        point.endpoint().side() != null)));
            button(graphics, mouseX, mouseY, x + 56, y, 58, 14, Component.translatable("robotics.pioneers.map.from_here"), true, () -> {
                tool = Tool.TRANSFER;
                transferSource = point;
                transferStarted = true;
            });
            button(graphics, mouseX, mouseY, x + 116, y, w - 116, 14, Component.translatable("robotics.pioneers.map.to_here"), true, () -> {
                RoboticOrder draft = new RoboticOrder();
                draft.amount = 64;
                draft.target = point.endpoint();
                minecraft.setScreen(new OrderEditScreen(this, draft));
            });
            return y + 18;
        }
        if (selected instanceof ClientRoboticsMap.Node node) {
            ItemStack icon = node.hub() ? new ItemStack(PiRobotics.UNITS.get(node.kind()).get(UnitTier.T1).get()) : new ItemStack(PiRobotics.RELAY);
            graphics.renderItem(icon, x, y);
            Component name = node.hub() ? Component.translatable("robotics.pioneers.hub." + node.kind().id())
                                        : Component.translatable("block.pioneers.robotic_relay");
            GuiDraw.text(graphics, font, name, x + 19, y + 1, 0.75F, 0xFFE0F5EA, w - 20);
            GuiDraw.text(graphics, font, Component.literal(node.pos().toShortString()), x + 19, y + 9, 0.6F, 0xFF7FA8A0, w - 20);
            y += 20;
            if (node.hub()) {
                RoboticHubData.Status status = RoboticHubData.Status.values()[Mth.clamp(node.status(), 0, RoboticHubData.Status.values().length - 1)];
                GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.hub.status." + status.name().toLowerCase(Locale.ROOT)), x, y,
                      0.65F, status == RoboticHubData.Status.WORKING || status == RoboticHubData.Status.IDLE ? 0xFF55E07A : 0xFFFF6A5A, w);
                y += 9;
                GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.map.hub_units", node.active(), node.units(), node.capacity(),
                      node.docked()), x, y, 0.65F, 0xFFD0E0E8, w);
                y += 9;
                GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.orders.hub_range", node.coverage()), x, y, 0.65F, 0xFFD0E0E8, w);
                y += 11;
                button(graphics, mouseX, mouseY, x, y, 70, 14, Component.translatable("robotics.pioneers.hub.recall"), true,
                      () -> PiNetwork.sendToServer(new PacketRoboticsMap.HubAction(node.pos(), PacketRoboticsMap.HubAction.RECALL)));
                return y + 18;
            }
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.map.relay", node.charging(), Math.round(node.energy() * 100),
                  node.coverage()), x, y, 0.65F, 0xFFD0E0E8, w);
            return y + 12;
        }
        if (selected instanceof ClientRoboticsMap.Unit unit) {
            graphics.renderItem(new ItemStack(PiRobotics.UNITS.get(unit.kind()).get(UnitTier.byIndex(unit.tier())).get()), x, y);
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.map.unit",
                  Component.translatable("entity.pioneers.unit_" + unit.kind().id()), unit.tier() + 1), x + 19, y + 1, 0.75F, 0xFFE0F5EA, w - 20);
            GuiDraw.text(graphics, font, phaseText(unit.phase()), x + 19, y + 9, 0.6F, 0xFF7FA8A0, w - 20);
            y += 20;
            GuiDraw.text(graphics, font, Component.translatable(RobotTask.byIndex(unit.task()).getTranslationKey()), x, y, 0.65F, 0xFFD0E0E8, w);
            y += 10;
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.map.battery", unit.battery()), x, y, 0.6F, 0xFFD0E0E8);
            GuiDraw.bar(graphics, x + 60, y, w - 60, 5, unit.battery() / 100.0, unit.battery() < 20 ? 0xFFE04040 : 0xFF55E07A);
            y += 8;
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.map.cargo", unit.fill()), x, y, 0.6F, 0xFFD0E0E8);
            GuiDraw.bar(graphics, x + 60, y, w - 60, 5, unit.fill() / 100.0, 0xFF4FC3F7);
            return y + 10;
        }
        if (selected instanceof ClientRailMap.MapTrain t) {
            return drawTrainSelection(graphics, mouseX, mouseY, x, y, w, t);
        }
        if (selected instanceof ClientRailMap.MapStation st) {
            graphics.renderItem(new ItemStack(PiRail.STATION.get()), x, y);
            GuiDraw.text(graphics, font, RailText.station(st.name()), x + 19, y + 1, 0.75F, 0xFFE0F5EA, w - 20);
            GuiDraw.text(graphics, font, Component.translatable("rail.pioneers.station.role." + st.role().name().toLowerCase(Locale.ROOT)), x + 19, y + 9, 0.6F,
                  roleColor(st.role()), w - 20);
            y += 20;
            GuiDraw.text(graphics, font, Component.translatable(st.comingKey(), st.coming()), x, y, 0.65F, 0xFFD0E0E8, w);
            y += 9;
            GuiDraw.text(graphics, font, Component.literal(st.pos().toShortString()), x, y, 0.6F, 0xFF7FA8A0, w);
            return y + 12;
        }
        if (selected instanceof RoboticOrder order) {
            graphics.renderItem(OrderTexts.icon(order), x, y);
            GuiDraw.text(graphics, font, OrderTexts.title(order), x + 19, y + 1, 0.65F, 0xFFE0F5EA, w - 20);
            GuiDraw.text(graphics, font, OrderTexts.detail(order), x + 19, y + 9, 0.55F, 0xFF000000 | OrderTexts.statusColor(order.status), w - 20);
            y += 20;
            y = statusBox(graphics, x, y, w, order);
            orderButtons(graphics, mouseX, mouseY, order, x, y, w);
            if (order.type == RoboticOrder.Type.ZONE) {
                y += 16;
                button(graphics, mouseX, mouseY, x, y, w, 14, Component.translatable("robotics.pioneers.planner.open"), true,
                      () -> ClientRobotics.openOrder(this, order));
            }
            return y + 18;
        }
        return y;
    }

    private void orderButtons(GuiGraphics graphics, int mouseX, int mouseY, RoboticOrder order, int x, int y, int w) {
        int bw = (w - 4) / 3;
        button(graphics, mouseX, mouseY, x, y, bw, 14, Component.translatable(order.paused ? "robotics.pioneers.orders.resume"
                                                                                          : "robotics.pioneers.orders.pause"), true,
              () -> ClientRobotics.togglePause(order));
        button(graphics, mouseX, mouseY, x + bw + 2, y, bw, 14, Component.translatable("robotics.pioneers.orders.edit"), true, () -> {
            RoboticOrder copy = order.copy();
            if (order.type == RoboticOrder.Type.ZONE) {
                ClientRobotics.openOrder(this, order);
            } else {
                minecraft.setScreen(new OrderEditScreen(this, copy));
            }
        });
        button(graphics, mouseX, mouseY, x + 2 * (bw + 2), y, bw, 14, Component.translatable("robotics.pioneers.orders.delete"), true, () -> {
            ClientRobotics.delete(order);
            selected = null;
        });
    }

    /** Place la recherche (partagee par les onglets Ordres et Reseau) a l'endroit voulu. */
    private void placeSearch(int x, int y, int w) {
        search.setX(x);
        search.setY(y);
        search.setWidth(w);
    }

    private void drawOrders(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h) {
        int bottom = y + h;
        button(graphics, mouseX, mouseY, x, y, w, 14, Component.translatable("robotics.pioneers.orders.new_transfer"), true, () -> {
            RoboticOrder draft = new RoboticOrder();
            draft.amount = 64;
            minecraft.setScreen(new OrderEditScreen(this, draft));
        });
        y += 17;
        placeSearch(x, y, w);
        y += 16;
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        List<RoboticOrder> orders = new ArrayList<>();
        for (RoboticOrder order : ClientRobotics.orders()) {
            if (query.isEmpty() || OrderTexts.title(order).getString().toLowerCase(Locale.ROOT).contains(query)
                || OrderTexts.detail(order).getString().toLowerCase(Locale.ROOT).contains(query)) {
                orders.add(order);
            }
        }
        if (orders.isEmpty()) {
            GuiDraw.center(graphics, font, Component.translatable(ClientRobotics.orders().isEmpty() ? "robotics.pioneers.orders.empty"
                                                                                                    : "robotics.pioneers.orders.no_match"),
                  x + w / 2F, y + 6, 0.65F, 0xFF7FA8A0, w);
            if (ClientRobotics.orders().isEmpty()) {
                GuiDraw.center(graphics, font, Component.translatable("robotics.pioneers.map.orders_hint"), x + w / 2F, y + 16, 0.55F, 0xFF5E7E78, w);
            }
            return;
        }
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, orders.size() - 1));
        //Lignes compactes : icone, titre et etat ; le detail se deplie (▸)
        for (int i = listScroll; i < orders.size(); i++) {
            RoboticOrder order = orders.get(i);
            boolean open = EXPANDED.contains(order.id), hidden = ClientRobotics.HIDDEN_ORDERS.contains(order.id);
            int detailH = open ? wrappedHeight(OrderTexts.detail(order), 0.52F, w - 22, 4) : 0;
            int whyH = open ? wrappedHeight(OrderTexts.why(order), 0.5F, w - 22, 6) : 0;
            int titleH = Math.max(14, wrappedHeight(OrderTexts.title(order), 0.58F, w - 48, 2) + 5);
            int rh = open ? titleH + detailH + whyH + 4 : 14;
            if (y + rh > bottom - 8) {
                break;
            }
            boolean hover = over(mouseX, mouseY, x, y, w - 28, rh);
            graphics.fill(x, y, x + w, y + rh - 1, selected == order ? 0xFF24443A : hover ? 0xFF1E2E28 : 0xFF182420);
            graphics.fill(x, y, x + 2, y + rh - 1, 0xFF000000 | OrderTexts.statusColor(order.status));
            graphics.pose().pushPose();
            graphics.pose().translate(x + 4, y + 1, 0);
            graphics.pose().scale(0.7F, 0.7F, 1);
            graphics.renderItem(OrderTexts.icon(order), 0, 0);
            graphics.pose().popPose();
            if (open) {
                wrapped(graphics, OrderTexts.title(order), x + 18, y + 3, 0.58F, hidden ? 0xFF6E7E78 : 0xFFE0F5EA, w - 48, 2);
                wrapped(graphics, OrderTexts.detail(order), x + 18, y + titleH, 0.52F, 0xFF000000 | OrderTexts.statusColor(order.status), w - 22, 4);
                wrapped(graphics, OrderTexts.why(order), x + 18, y + titleH + detailH + 1, 0.5F, 0xFF8FA3A0, w - 22, 6);
            } else {
                GuiDraw.text(graphics, font, OrderTexts.title(order), x + 18, y + 3, 0.58F, hidden ? 0xFF6E7E78 : 0xFFE0F5EA, w - 48);
            }
            click(x, y, w - 28, rh - 1, () -> {
                selected = order;
                centerOn(order.anchor().getX() + 0.5, order.anchor().getZ() + 0.5);
            });
            if (hover && !open) {
                tooltip(List.of(OrderTexts.status(order), OrderTexts.why(order).copy().withStyle(ChatFormatting.GRAY)));
            }
            //Cacher de la carte
            int id = order.id;
            button(graphics, mouseX, mouseY, x + w - 27, y + 1, 12, 11, Component.literal(hidden ? "○" : "●"), true, () -> {
                if (!ClientRobotics.HIDDEN_ORDERS.remove(id)) {
                    ClientRobotics.HIDDEN_ORDERS.add(id);
                }
            });
            tooltip(mouseX, mouseY, x + w - 27, y + 1, 12, 11, Component.translatable(hidden ? "robotics.pioneers.orders.show" : "robotics.pioneers.orders.hide"));
            //Deplier le detail
            button(graphics, mouseX, mouseY, x + w - 14, y + 1, 12, 11, Component.literal(open ? "▾" : "▸"), true, () -> {
                if (!EXPANDED.remove(id)) {
                    EXPANDED.add(id);
                }
            });
            tooltip(mouseX, mouseY, x + w - 14, y + 1, 12, 11, Component.translatable(open ? "robotics.pioneers.orders.collapse" : "robotics.pioneers.orders.expand"));
            y += rh;
        }
        if (orders.size() > 1) {
            GuiDraw.right(graphics, font, Component.literal((listScroll + 1) + " / " + orders.size()), x + w, bottom - 6, 0.5F, 0xFF5E7E78);
        }
    }

    private void drawStocks(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h) {
        ClientRoboticsMap.Network network = minecraft.player == null ? null : ClientRoboticsMap.networkAt(camX, camZ);
        if (network == null && !ClientRoboticsMap.networks.isEmpty()) {
            network = ClientRoboticsMap.networks.get(0);
        }
        if (network == null) {
            GuiDraw.center(graphics, font, Component.translatable("robotics.pioneers.map.no_network"), x + w / 2F, y + 6, 0.65F, 0xFF7FA8A0, w);
            return;
        }
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.map.network", network.label(), network.nodes().size()), x, y, 0.65F,
              0xFFB0C4D0, w);
        placeSearch(x, y + 12, w);
        y += 28;
        h -= 28;
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        List<ClientRoboticsMap.Stock> stocks = new ArrayList<>();
        for (ClientRoboticsMap.Stock stock : network.totals()) {
            if (query.isEmpty() || stock.res().name().getString().toLowerCase(Locale.ROOT).contains(query)) {
                stocks.add(stock);
            }
        }
        int cols = w / 20, rows = Math.max(1, h / 22);
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, (stocks.size() + cols - 1) / cols - rows));
        for (int i = 0; i < cols * rows; i++) {
            int index = listScroll * cols + i;
            if (index >= stocks.size()) {
                break;
            }
            ClientRoboticsMap.Stock stock = stocks.get(index);
            int sx = x + (i % cols) * 20, sy = y + (i / cols) * 22;
            darkSlot(graphics, sx + 1, sy + 1);
            ResourceIcon.draw(graphics, stock.res(), sx + 1, sy + 1);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            GuiDraw.right(graphics, font, Component.literal(OrderTexts.compact(stock.amount())), sx + 18, sy + 12, 0.5F, 0xFFFFFFFF);
            graphics.pose().popPose();
            if (over(mouseX, mouseY, sx, sy, 18, 18)) {
                tooltip(List.of(stock.res().name(), Component.literal(OrderTexts.amount(stock.amount(), stock.res().type)).withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    private void drawUnits(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h) {
        //Intendants : unites de chaque genre qui servent le reseau passif (demandes des points, coffres a vider, tampons, terminaux)
        int top = y;
        y = section(graphics, x, y, w, Component.translatable("robotics.pioneers.map.passive"));
        tooltip(mouseX, mouseY, x, top, w, 9, Component.translatable("robotics.pioneers.map.passive"),
              Component.translatable("robotics.pioneers.map.passive.tooltip").withStyle(ChatFormatting.GRAY));
        for (UnitKind kind : UnitKind.values()) {
            int index = kind.ordinal();
            int limit = index < ClientRoboticsMap.passive.length ? ClientRoboticsMap.passive[index] : RoboticsOrders.PASSIVE_ALL;
            int busy = index < ClientRoboticsMap.passiveBusy.length ? ClientRoboticsMap.passiveBusy[index] : 0;
            boolean all = limit >= RoboticsOrders.PASSIVE_ALL;
            graphics.pose().pushPose();
            graphics.pose().translate(x, y - 1, 0);
            graphics.pose().scale(0.75F, 0.75F, 1);
            graphics.renderItem(new ItemStack(PiRobotics.UNITS.get(kind).get(UnitTier.T1).get()), 0, 0);
            graphics.pose().popPose();
            GuiDraw.text(graphics, font, Component.translatable("entity.pioneers.unit_" + kind.id()), x + 15, y + 3, 0.6F, 0xFFE0F5EA, 60);
            Component state = all ? Component.translatable("robotics.pioneers.map.passive.all", busy)
                                  : limit == 0 ? Component.translatable("robotics.pioneers.map.passive.none")
                                               : Component.translatable("robotics.pioneers.map.passive.some", busy, limit);
            GuiDraw.right(graphics, font, state, x + w, y + 3, 0.52F, limit == 0 ? 0xFFFFB060 : 0xFF8FA3A0);
            y += 12;
            //-10, -1, saisie, +1, +10, illimite : sur toute la largeur
            int gap = 2, unit = (w - 5 * gap) / 7;
            int bw = unit, fw = w - 5 * gap - 6 * unit + unit;
            int bx = x, base = all ? 0 : limit;
            button(graphics, mouseX, mouseY, bx, y, bw, 12, Component.literal("-10"), !all && limit > 0, () -> setPassive(index, Math.max(0, base - 10)));
            bx += bw + gap;
            button(graphics, mouseX, mouseY, bx, y, bw, 12, Component.literal("-1"), !all && limit > 0, () -> setPassive(index, Math.max(0, base - 1)));
            bx += bw + gap;
            EditBox box = stewardBoxes[index];
            box.setX(bx);
            box.setY(y);
            box.setWidth(fw);
            box.setHeight(12);
            box.setVisible(true);
            if (!box.isFocused()) {
                String shown = all ? "∞" : Integer.toString(limit);
                if (!box.getValue().equals(shown)) {
                    box.setValue(shown);
                }
            }
            bx += fw + gap;
            button(graphics, mouseX, mouseY, bx, y, bw, 12, Component.literal("+1"), true, () -> setPassive(index, all ? 1 : Math.min(RoboticsOrders.PASSIVE_ALL - 1, base + 1)));
            bx += bw + gap;
            button(graphics, mouseX, mouseY, bx, y, bw, 12, Component.literal("+10"), true,
                  () -> setPassive(index, all ? 10 : Math.min(RoboticsOrders.PASSIVE_ALL - 1, base + 10)));
            bx += bw + gap;
            int iw = x + w - bx;
            chip(graphics, mouseX, mouseY, bx, y, iw, 12, Component.literal("∞"), all, 0x2E9E78, () -> setPassive(index, RoboticsOrders.PASSIVE_ALL));
            tooltip(mouseX, mouseY, bx, y, iw, 12, Component.translatable("robotics.pioneers.map.passive.unlimited"));
            y += 16;
        }
        //Reparations des degats (explosions, meteores) par les intendants
        BlockPos at = minecraft.player == null ? BlockPos.ZERO : minecraft.player.blockPosition();
        boolean repairs = ClientRoboticsMap.repairs;
        toggle(graphics, mouseX, mouseY, x, y, w, Component.translatable("robotics.pioneers.map.repairs", ClientRoboticsMap.damaged), repairs, () -> {
            ClientRoboticsMap.repairs = !repairs;
            PiNetwork.sendToServer(new PacketRoboticsMap.HubAction(at, repairs ? PacketRoboticsMap.HubAction.REPAIRS_OFF : PacketRoboticsMap.HubAction.REPAIRS_ON));
        });
        tooltip(mouseX, mouseY, x, y, w, 12, Component.translatable("robotics.pioneers.map.repairs.title"),
              Component.translatable("robotics.pioneers.map.repairs.tooltip").withStyle(ChatFormatting.GRAY));
        y += 14;
        y += 3;
        h -= y - top;
        List<ClientRoboticsMap.Unit> units = ClientRoboticsMap.units;
        if (units.isEmpty()) {
            GuiDraw.center(graphics, font, Component.translatable("robotics.pioneers.map.no_units"), x + w / 2F, y + 6, 0.65F, 0xFF7FA8A0, w);
            return;
        }
        int row = 20, rows = Math.max(1, h / row);
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, units.size() - rows));
        for (int i = 0; i < rows && listScroll + i < units.size(); i++) {
            ClientRoboticsMap.Unit unit = units.get(listScroll + i);
            int ry = y + i * row;
            graphics.fill(x, ry, x + w, ry + row - 2, selected == unit ? 0xFF24443A : 0xFF182420);
            graphics.renderItem(new ItemStack(PiRobotics.UNITS.get(unit.kind()).get(UnitTier.byIndex(unit.tier())).get()), x + 2, ry + 1);
            GuiDraw.text(graphics, font, phaseText(unit.phase()), x + 21, ry + 3, 0.6F, 0xFFE0F5EA, w - 60);
            GuiDraw.bar(graphics, x + 21, ry + 12, w - 60, 3, unit.battery() / 100.0, unit.battery() < 20 ? 0xFFE04040 : 0xFF55E07A);
            GuiDraw.right(graphics, font, Component.literal(Math.round(unit.x()) + ", " + Math.round(unit.z())), x + w - 2, ry + 6, 0.5F, 0xFF7FA8A0);
            click(x, ry, w, row - 2, () -> {
                selected = unit;
                centerOn(unit.x(), unit.z());
            });
        }
    }

    /** Duree lisible (s, min, h). */
    private static String duration(int seconds) {
        if (seconds < 60) {
            return seconds + " s";
        }
        if (seconds < 3600) {
            return seconds / 60 + " min";
        }
        return seconds / 3600 + " h " + (seconds % 3600) / 60 + " min";
    }

    private void drawAlerts(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h) {
        //Les alertes recurrentes d'abord, puis les plus anciennes
        List<ClientRoboticsMap.Alert> alerts = new ArrayList<>(ClientRoboticsMap.alerts);
        alerts.sort(Comparator.comparingInt((ClientRoboticsMap.Alert a) -> -a.returns()).thenComparingInt(a -> -a.age()));
        if (alerts.isEmpty()) {
            GuiDraw.center(graphics, font, Component.translatable("robotics.pioneers.map.no_alerts"), x + w / 2F, y + 6, 0.65F, 0xFF55E07A, w);
            return;
        }
        //Tout effacer
        button(graphics, mouseX, mouseY, x, y, w, 13, Component.translatable("robotics.pioneers.map.alerts.clear"), true, () -> {
            BlockPos at = minecraft.player == null ? BlockPos.ZERO : minecraft.player.blockPosition();
            PiNetwork.sendToServer(new PacketRoboticsMap.HubAction(at, PacketRoboticsMap.HubAction.DISMISS_ALL));
            ClientRoboticsMap.alerts = List.of();
        });
        tooltip(mouseX, mouseY, x, y, w, 13, Component.translatable("robotics.pioneers.map.alerts.clear.tooltip"));
        y += 16;
        h -= 16;
        y += wrapped(graphics, Component.translatable("robotics.pioneers.map.alerts.auto"), x, y, 0.5F, 0xFF6E7E78, w, 3) + 3;
        int row = 25, rows = Math.max(1, (h - 12) / row);
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, alerts.size() - rows));
        for (int i = 0; i < rows && listScroll + i < alerts.size(); i++) {
            ClientRoboticsMap.Alert alert = alerts.get(listScroll + i);
            int ry = y + i * row;
            graphics.fill(x, ry, x + w, ry + row - 2, alert.recurring() ? 0xFF2A1C14 : 0xFF201818);
            graphics.fill(x, ry, x + 2, ry + row - 2, 0xFF000000 | alert.type().color);
            GuiDraw.text(graphics, font, Component.translatable(alert.type().getTranslationKey()), x + 6, ry + 2, 0.6F, 0xFF000000 | alert.type().color, w - 24);
            GuiDraw.text(graphics, font, Component.literal(alert.pos().toShortString()).append(" · ")
                  .append(Component.translatable("robotics.pioneers.map.alerts.since", duration(alert.age()))), x + 6, ry + 9, 0.5F, 0xFF9A9A9A, w - 24);
            if (alert.recurring()) {
                GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.map.alerts.recurring", alert.returns()), x + 6, ry + 16, 0.5F,
                      0xFFFFA040, w - 24);
                tooltip(mouseX, mouseY, x, ry, w - 16, row - 2, Component.translatable("robotics.pioneers.map.alerts.recurring.tooltip"));
            }
            click(x, ry, w, row - 2, () -> centerOn(alert.pos().getX() + 0.5, alert.pos().getZ() + 0.5));
            //Effacer celle-ci (elle se tait 30 minutes si le probleme persiste)
            button(graphics, mouseX, mouseY, x + w - 15, ry + 2, 13, 12, Component.literal("✕"), true, () -> {
                PiNetwork.sendToServer(new PacketRoboticsMap.HubAction(alert.pos(), PacketRoboticsMap.HubAction.DISMISS_ALERT));
                List<ClientRoboticsMap.Alert> left = new ArrayList<>(ClientRoboticsMap.alerts);
                left.removeIf(other -> other.pos().equals(alert.pos()));
                ClientRoboticsMap.alerts = left;
            });
            tooltip(mouseX, mouseY, x + w - 15, ry + 2, 13, 12, Component.translatable("robotics.pioneers.map.alerts.dismiss"));
        }
    }

    // ------------------------------------------------------------------
    // Souris
    // ------------------------------------------------------------------

    /** Objet de la carte sous la souris, ou null. */
    @Nullable
    private Object pick(double mx, double my) {
        if (LAYERS[Layer.RAIL.ordinal()]) {
            for (ClientRailMap.MapAlert alert : ClientRailMap.alerts) {
                if (Math.hypot(mx - toScreenX(alert.pos().getX() + 0.5), my - (toScreenY(alert.pos().getZ() + 0.5) - 8)) < 6) {
                    return alert;
                }
            }
            for (ClientRailMap.MapTrain t : ClientRailMap.trains) {
                float[] xz = t.xz();
                for (int i = 0; i + 1 < xz.length; i += 2) {
                    if (Math.hypot(mx - toScreenX(xz[i]), my - toScreenY(xz[i + 1])) < Math.max(5, scale * 0.8)) {
                        return t;
                    }
                }
            }
            for (ClientRailMap.MapStation st : ClientRailMap.stations) {
                if (Math.hypot(mx - toScreenX(st.pos().getX() + 0.5), my - toScreenY(st.pos().getZ() + 0.5)) < 6) {
                    return st;
                }
            }
        }
        if (LAYERS[Layer.UNITS.ordinal()]) {
            for (ClientRoboticsMap.Unit unit : ClientRoboticsMap.units) {
                if (visible(unit) && Math.hypot(mx - toScreenX(unit.x()), my - toScreenY(unit.z())) < 5) {
                    return unit;
                }
            }
        }
        if (LAYERS[Layer.POINTS.ordinal()]) {
            for (ClientRoboticsMap.Point point : ClientRoboticsMap.points) {
                if (visible(point) && Math.hypot(mx - toScreenX(point.pos().getX() + 0.5), my - toScreenY(point.pos().getZ() + 0.5)) < (scale >= 4 ? 9 : 6)) {
                    return point;
                }
            }
        }
        for (ClientRoboticsMap.Network network : ClientRoboticsMap.networks) {
            for (ClientRoboticsMap.Node node : network.nodes()) {
                if (visible(node) && Math.abs(mx - toScreenX(node.pos().getX() + 0.5)) < 11 && Math.abs(my - toScreenY(node.pos().getZ() + 0.5)) < 11) {
                    return node;
                }
            }
        }
        if (LAYERS[Layer.ALERTS.ordinal()]) {
            for (ClientRoboticsMap.Alert alert : ClientRoboticsMap.alerts) {
                if (Math.hypot(mx - toScreenX(alert.pos().getX() + 0.5), my - (toScreenY(alert.pos().getZ() + 0.5) - 8)) < 6) {
                    return alert;
                }
            }
        }
        if (LAYERS[Layer.ROUTES.ordinal()]) {
            for (RoboticOrder order : ClientRobotics.orders()) {
                if (order.type == RoboticOrder.Type.TRANSFER && order.source != null && order.target != null && visible(order)) {
                    float x0 = toScreenX(order.source.pos().getX() + 0.5), y0 = toScreenY(order.source.pos().getZ() + 0.5);
                    float x1 = toScreenX(order.target.pos().getX() + 0.5), y1 = toScreenY(order.target.pos().getZ() + 0.5);
                    if (segmentDistance(mx, my, x0, y0, x1, y1) < 4) {
                        return order;
                    }
                }
            }
        }
        if (LAYERS[Layer.ZONES.ordinal()]) {
            double wx = toWorldX(mx), wz = toWorldZ(my);
            for (RoboticOrder order : ClientRobotics.orders()) {
                if (order.type == RoboticOrder.Type.ZONE && order.min != null && order.max != null && visible(order) && wx >= order.min.getX() && wx < order.max.getX() + 1
                    && wz >= order.min.getZ() && wz < order.max.getZ() + 1) {
                    return order;
                }
            }
        }
        return null;
    }

    private static double segmentDistance(double px, double py, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0;
        double len = dx * dx + dy * dy;
        double t = len < 1e-6 ? 0 : Mth.clamp(((px - x0) * dx + (py - y0) * dy) / len, 0, 1);
        return Math.hypot(px - (x0 + t * dx), py - (y0 + t * dy));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (overFilters(mouseX, mouseY)) {
            return true;
        }
        if (filtersOpen) {
            filtersOpen = false;
        }
        if (mouseX < mapWidth() && mouseY >= height - BAR) {
            return true;
        }
        if (!inMap(mouseX, mouseY)) {
            return false;
        }
        if (button == 1) {
            //Clic droit : annuler l'outil en cours, ou deplacer la carte
            if (tool == Tool.TRANSFER && transferStarted) {
                transferSource = null;
                transferStarted = false;
                return true;
            }
            panning = true;
            panX = mouseX;
            panY = mouseY;
            return true;
        }
        if (button != 0) {
            return false;
        }
        Object hit = pick(mouseX, mouseY);
        switch (tool) {
            case SELECT -> {
                //Clic : on choisit au relachement ; glisser fait defiler la carte, meme depuis une zone ou un point
                pressHit = hit;
                pressPending = true;
                pressX = mouseX;
                pressY = mouseY;
                panning = true;
                panX = mouseX;
                panY = mouseY;
            }
            case ZONE -> {
                dragStartX = Math.floor(toWorldX(mouseX));
                dragStartZ = Math.floor(toWorldZ(mouseY));
                dragEndX = dragStartX;
                dragEndZ = dragStartZ;
            }
            case TRANSFER -> {
                if (!transferStarted) {
                    transferStarted = true;
                    transferSource = hit instanceof ClientRoboticsMap.Point point ? point : null;
                    transferFromNetwork = transferSource == null;
                } else if (hit instanceof ClientRoboticsMap.Point point) {
                    RoboticOrder draft = new RoboticOrder();
                    draft.amount = 64;
                    draft.source = transferFromNetwork || transferSource == null ? null : transferSource.endpoint();
                    draft.target = point.endpoint();
                    transferSource = null;
                    transferStarted = false;
                    minecraft.setScreen(new OrderEditScreen(this, draft));
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (panning) {
            if (pressPending && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) < 4) {
                return true;
            }
            pressPending = false;
            camX -= (mouseX - panX) / scale;
            camZ -= (mouseY - panY) / scale;
            panX = mouseX;
            panY = mouseY;
            return true;
        }
        if (tool == Tool.ZONE && !Double.isNaN(dragStartX)) {
            dragEndX = Math.floor(toWorldX(mouseX));
            dragEndZ = Math.floor(toWorldZ(mouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (panning) {
            panning = false;
            if (pressPending) {
                pressPending = false;
                selected = pressHit;
                sendOpen = false;
                if (selected instanceof ClientRailMap.MapAlert alert) {
                    selected = null;
                    centerOn(alert.pos().getX() + 0.5, alert.pos().getZ() + 0.5);
                }
            } else {
                //La carte a glisse : on ne suit plus le train
                follow = null;
            }
            pressHit = null;
            return true;
        }
        if (tool == Tool.ZONE && !Double.isNaN(dragStartX)) {
            int x0 = (int) Math.min(dragStartX, dragEndX), x1 = (int) Math.max(dragStartX, dragEndX);
            int z0 = (int) Math.min(dragStartZ, dragEndZ), z1 = (int) Math.max(dragStartZ, dragEndZ);
            dragStartX = Double.NaN;
            if (x0 == x1 && z0 == z1) {
                //Simple clic : le planificateur 3D s'ouvre la, la zone est a tracer sur le terrain
                int h = ClientRoboticsMap.height(x0, z0);
                ClientRobotics.planZoneAt(this, x0 + 0.5, h == Integer.MIN_VALUE ? (minecraft.player == null ? 64 : minecraft.player.getY()) : h + 1,
                      z0 + 0.5);
                return true;
            }
            //Hauteurs : le relief connu de la zone
            int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
            for (int x = x0; x <= x1; x += Math.max(1, (x1 - x0) / 16)) {
                for (int z = z0; z <= z1; z += Math.max(1, (z1 - z0) / 16)) {
                    int h = ClientRoboticsMap.height(x, z);
                    if (h != Integer.MIN_VALUE) {
                        low = Math.min(low, h);
                        high = Math.max(high, h);
                    }
                }
            }
            if (low == Integer.MAX_VALUE) {
                int y = minecraft.player == null ? 64 : minecraft.player.getBlockY();
                low = y - 1;
                high = y + 2;
            }
            ClientRobotics.drawNewZone(this, new BlockPos(x0, low, z0), new BlockPos(x1, high, z1));
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= mapWidth()) {
            listScroll = Math.max(0, listScroll - (int) Math.signum(delta));
            return true;
        }
        double wx = toWorldX(mouseX), wz = toWorldZ(mouseY);
        scale = Mth.clamp(scale * (delta > 0 ? 1.25F : 0.8F), MIN_SCALE, MAX_SCALE);
        //On garde le point sous la souris immobile
        camX += wx - toWorldX(mouseX);
        camZ += wz - toWorldZ(mouseY);
        return true;
    }

    // ------------------------------------------------------------------
    // Dessin
    // ------------------------------------------------------------------

    /** Couverture logistique : l'union des portees de tous les ports et relais (une seule surface, son contour seulement). */
    private static double[] coverX = new double[0], coverZ = new double[0];
    private static boolean[][] coverCells = new boolean[0][0];
    private static int coverHash;

    private void drawCoverage(GuiGraphics graphics) {
        List<double[]> rects = new ArrayList<>();
        int hash = 1;
        for (ClientRoboticsMap.Network network : ClientRoboticsMap.networks) {
            for (ClientRoboticsMap.Node node : network.nodes()) {
                double cx = node.pos().getX() + 0.5, cz = node.pos().getZ() + 0.5, c = node.coverage();
                rects.add(new double[]{cx - c, cz - c, cx + c, cz + c});
                hash = hash * 31 + node.pos().hashCode() * 7 + node.coverage();
            }
        }
        if (rects.isEmpty()) {
            return;
        }
        if (hash != coverHash || coverCells.length == 0) {
            //Compression des coordonnees : une grille dont chaque case est entierement dedans ou dehors
            coverHash = hash;
            TreeSet<Double> xs = new TreeSet<>(), zs = new TreeSet<>();
            for (double[] r : rects) {
                xs.add(r[0]);
                xs.add(r[2]);
                zs.add(r[1]);
                zs.add(r[3]);
            }
            coverX = xs.stream().mapToDouble(Double::doubleValue).toArray();
            coverZ = zs.stream().mapToDouble(Double::doubleValue).toArray();
            coverCells = new boolean[coverX.length - 1][coverZ.length - 1];
            for (int i = 0; i < coverX.length - 1; i++) {
                double mx = (coverX[i] + coverX[i + 1]) / 2;
                for (int j = 0; j < coverZ.length - 1; j++) {
                    double mz = (coverZ[j] + coverZ[j + 1]) / 2;
                    for (double[] r : rects) {
                        if (mx > r[0] && mx < r[2] && mz > r[1] && mz < r[3]) {
                            coverCells[i][j] = true;
                            break;
                        }
                    }
                }
            }
        }
        int fill = 0x2246C8E0, edge = 0xCC4FD0F0;
        int nx = coverCells.length, nz = nx == 0 ? 0 : coverCells[0].length;
        //Surface : des bandes par colonne (cases contigues fusionnees)
        for (int i = 0; i < nx; i++) {
            int j = 0;
            while (j < nz) {
                if (!coverCells[i][j]) {
                    j++;
                    continue;
                }
                int start = j;
                while (j < nz && coverCells[i][j]) {
                    j++;
                }
                rect(graphics, toScreenX(coverX[i]), toScreenY(coverZ[start]), toScreenX(coverX[i + 1]), toScreenY(coverZ[j]), fill);
            }
        }
        //Contour : les bords entre une case couverte et une case vide
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < nz; j++) {
                if (!coverCells[i][j]) {
                    continue;
                }
                float x0 = toScreenX(coverX[i]), x1 = toScreenX(coverX[i + 1]), y0 = toScreenY(coverZ[j]), y1 = toScreenY(coverZ[j + 1]);
                if (i == 0 || !coverCells[i - 1][j]) {
                    rect(graphics, x0, y0, x0 + 1.5F, y1, edge);
                }
                if (i == nx - 1 || !coverCells[i + 1][j]) {
                    rect(graphics, x1 - 1.5F, y0, x1, y1, edge);
                }
                if (j == 0 || !coverCells[i][j - 1]) {
                    rect(graphics, x0, y0, x1, y0 + 1.5F, edge);
                }
                if (j == nz - 1 || !coverCells[i][j + 1]) {
                    rect(graphics, x0, y1 - 1.5F, x1, y1, edge);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 0.20.5 : chemin de fer
    // ------------------------------------------------------------------

    private static int qualityColor(TrackQuality q) {
        return switch (q) {
            case WOOD -> 0xFF9A7448;
            case BASIC -> 0xFF8A9AA6;
            case ADVANCED -> 0xFFB8C4CC;
            case ELITE -> 0xFF6FD3E8;
            case ULTIMATE -> 0xFFB58AF0;
        };
    }

    private static int roleColor(StationLogistics.Role role) {
        return switch (role) {
            case DEPOT -> 0xFFD8B030;
            case SUPPLY -> 0xFFE5503A;
            case DEMAND -> 0xFF3F9AE4;
            case BOTH -> 0xFF9A6AE0;
            default -> 0xFFE0F5EA;
        };
    }

    private static int trainColor(ClientRailMap.MapTrain t) {
        if (!t.auto()) {
            return 0xFFB0B8C0;
        }
        return switch (t.status()) {
            case NO_PATH, NO_FUEL, DERAILED, NO_SCHEDULE -> 0xFFFF5A4A;
            case WAIT_SIGNAL, WAIT_TRAIN, DEST_FULL -> 0xFFFFB04A;
            default -> t.logistic() ? 0xFF4FA8FF : 0xFF5BE08F;
        };
    }

    private void drawRail(GuiGraphics graphics, int mouseX, int mouseY) {
        float width = Math.max(1, Math.min(3, scale * 0.35F));
        for (ClientRailMap.Segment s : ClientRailMap.segments) {
            float x0 = toScreenX(s.x0()), y0 = toScreenY(s.z0()), x1 = toScreenX(s.x1()), y1 = toScreenY(s.z1());
            if (Math.max(x0, x1) < 0 || Math.min(x0, x1) > mapWidth() || Math.max(y0, y1) < TOP || Math.min(y0, y1) > height) {
                continue;
            }
            boolean busy = ClientRailMap.busy.contains(s.anchor());
            line(graphics, x0, y0, x1, y1, width + 1, 0xC0000000);
            line(graphics, x0, y0, x1, y1, width, busy ? 0xFFE0604A : qualityColor(s.quality()));
        }
        //Signaux
        if (scale >= 1.5F) {
            for (int i = 0; i < ClientRailMap.signals.length && i < ClientRailMap.aspects.length; i++) {
                BlockPos p = BlockPos.of(ClientRailMap.signals[i]);
                float x = toScreenX(p.getX() + 0.5), y = toScreenY(p.getZ() + 0.5);
                int c = switch (ClientRailMap.aspects[i]) {
                    case 1 -> 0xFFFFB04A;
                    case 2 -> 0xFFFF4A3A;
                    case 3 -> 0xFF4A8AFF;
                    default -> 0xFF5BE08F;
                };
                graphics.fill((int) x - 2, (int) y - 2, (int) x + 2, (int) y + 2, 0xFF000000);
                graphics.fill((int) x - 1, (int) y - 1, (int) x + 1, (int) y + 1, c);
            }
        }
        float time = ticks + minecraft.getFrameTime();
        //0.20.27 : mer : liaisons des balises (bleu), routes restantes des navires (tirets clairs), balises (losanges jaunes)
        for (float[] xz : ClientRailMap.seaLanes) {
            for (int i = 0; i + 3 < xz.length; i += 2) {
                line(graphics, toScreenX(xz[i]), toScreenY(xz[i + 1]), toScreenX(xz[i + 2]), toScreenY(xz[i + 3]), 3, 0x9020304A);
                line(graphics, toScreenX(xz[i]), toScreenY(xz[i + 1]), toScreenX(xz[i + 2]), toScreenY(xz[i + 3]), 1.6F, 0xE04AB4F0);
            }
        }
        for (float[] xz : ClientRailMap.seaRoutes) {
            for (int i = 0; i + 3 < xz.length; i += 2) {
                dashedPlain(graphics, toScreenX(xz[i]), toScreenY(xz[i + 1]), toScreenX(xz[i + 2]), toScreenY(xz[i + 3]), 0xC0E8F4FF, time);
            }
        }
        for (long b : ClientRailMap.seaBeacons) {
            BlockPos p = BlockPos.of(b);
            float x = toScreenX(p.getX() + 0.5), y = toScreenY(p.getZ() + 0.5);
            quad(graphics, x, y - 5, x + 5, y, x, y + 5, x - 5, y, 0xFF000000);
            quad(graphics, x, y - 3.5F, x + 3.5F, y, x, y + 3.5F, x - 3.5F, y, (ticks / 10) % 2 == 0 ? 0xFFFFE04A : 0xFFE07A2A);
        }
        //Livraisons : trait de la gare fournisseuse a la gare demandeuse
        for (ClientRailMap.MapDelivery d : ClientRailMap.deliveries) {
            BlockPos a = BlockPos.of(d.delivery().from), b = BlockPos.of(d.delivery().to);
            dashed(graphics, toScreenX(a.getX() + 0.5), toScreenY(a.getZ() + 0.5), toScreenX(b.getX() + 0.5), toScreenY(b.getZ() + 0.5), 0x904FA8FF, time);
        }
        //Gares
        for (ClientRailMap.MapStation st : ClientRailMap.stations) {
            float x = toScreenX(st.pos().getX() + 0.5), y = toScreenY(st.pos().getZ() + 0.5);
            int c = roleColor(st.role());
            graphics.fill((int) x - 4, (int) y - 4, (int) x + 4, (int) y + 4, selected == st ? 0xFFFFFFFF : 0xFF000000);
            graphics.fill((int) x - 3, (int) y - 3, (int) x + 3, (int) y + 3, c);
            if (scale >= 2) {
                GuiDraw.text(graphics, font, RailText.station(st.name()), x + 6, y - 3, 0.6F, 0xFFE0F5EA, 120);
            }
        }
        //Trains
        for (ClientRailMap.MapTrain t : ClientRailMap.trains) {
            float[] xz = t.xz();
            int c = trainColor(t);
            boolean sel = selected instanceof ClientRailMap.MapTrain s && s.id().equals(t.id());
            float tw = Math.max(3, Math.min(7, scale * 0.9F));
            for (int i = 0; i + 3 < xz.length; i += 2) {
                line(graphics, toScreenX(xz[i]), toScreenY(xz[i + 1]), toScreenX(xz[i + 2]), toScreenY(xz[i + 3]), tw + 2, sel ? 0xFFFFFFFF : 0xFF000000);
            }
            for (int i = 0; i + 3 < xz.length; i += 2) {
                line(graphics, toScreenX(xz[i]), toScreenY(xz[i + 1]), toScreenX(xz[i + 2]), toScreenY(xz[i + 3]), tw, c);
            }
            if (xz.length >= 2) {
                float hx = toScreenX(xz[0]), hy = toScreenY(xz[1]);
                graphics.fill((int) hx - 3, (int) hy - 3, (int) hx + 3, (int) hy + 3, sel ? 0xFFFFFFFF : 0xFF000000);
                graphics.fill((int) hx - 2, (int) hy - 2, (int) hx + 2, (int) hy + 2, c);
                if (scale >= 1.5F) {
                    GuiDraw.text(graphics, font, Component.literal(t.name()), hx + 5, hy + 2, 0.55F, c, 90);
                }
            }
        }
        //Alertes du rail
        for (ClientRailMap.MapAlert alert : ClientRailMap.alerts) {
            float x = toScreenX(alert.pos().getX() + 0.5), y = toScreenY(alert.pos().getZ() + 0.5);
            if ((ticks / 8) % 2 == 0) {
                triangle(graphics, x, y - 10, 0xFFFFA040);
            }
        }
    }

    private int drawTrainSelection(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, ClientRailMap.MapTrain t) {
        graphics.fill(x, y + 2, x + 14, y + 14, trainColor(t));
        GuiDraw.text(graphics, font, Component.literal(t.name()), x + 19, y + 1, 0.75F, 0xFFE0F5EA, w - 20);
        GuiDraw.text(graphics, font, Component.translatable("rail.pioneers.status." + t.status().name().toLowerCase(Locale.ROOT)), x + 19, y + 9, 0.6F,
              trainColor(t), w - 20);
        y += 20;
        if (!t.dest().isEmpty()) {
            GuiDraw.text(graphics, font, Component.translatable("rail.pioneers.train.dest", RailText.station(t.dest())), x, y, 0.65F, 0xFFD0E0E8, w);
            y += 9;
        }
        GuiDraw.text(graphics, font, Component.translatable("rail.pioneers.train.speed", String.format(Locale.ROOT, "%.1f", t.speed()))
              .append(t.group().isEmpty() ? Component.empty() : Component.literal("   ").append(Component.translatable("rail.pioneers.train.in_group", t.group()))),
              x, y, 0.6F, 0xFF9FB4B0, w);
        y += 9;
        if (t.logistic()) {
            GuiDraw.text(graphics, font, Component.translatable("rail.pioneers.map.logistic"), x, y, 0.6F, 0xFF4FA8FF, w);
            y += 9;
        }
        y += 2;
        int bw = (w - 4) / 3;
        button(graphics, mouseX, mouseY, x, y, bw, 14, Component.translatable("rail.pioneers.map.sheet"), true,
              () -> PiNetwork.sendToServer(new PacketRail.TrainEdit(t.id(), PacketRail.TrainEdit.OPEN, 0, "", new CompoundTag())));
        boolean following = t.id().equals(follow);
        chip(graphics, mouseX, mouseY, x + bw + 2, y, bw, 14, Component.translatable("rail.pioneers.map.follow"), following, 0x2E9E78,
              () -> follow = following ? null : t.id());
        button(graphics, mouseX, mouseY, x + 2 * (bw + 2), y, bw, 14, Component.translatable("rail.pioneers.map.send"), t.auto() && t.temp() != Train.TempKind.DELIVERY,
              () -> sendOpen = !sendOpen);
        tooltip(mouseX, mouseY, x + 2 * (bw + 2), y, bw, 14, Component.translatable(t.auto() ? "rail.pioneers.train.send_tip" : "rail.pioneers.train.send_manual"));
        y += 17;
        if (sendOpen) {
            TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            ClientRailMap.stations.forEach(s -> names.add(s.name()));
            int shown = 0;
            for (String n : names) {
                if (shown++ >= 8) {
                    break;
                }
                boolean hover = over(mouseX, mouseY, x, y, w, 11);
                graphics.fill(x, y, x + w, y + 11, hover ? 0xFF2E3A46 : 0xFF1A222A);
                GuiDraw.text(graphics, font, RailText.station(n), x + 4, y + 2, 0.65F, 0xFFE0F5EA, w - 8);
                click(x, y, w, 11, () -> {
                    PiNetwork.sendToServer(new PacketRail.TrainEdit(t.id(), PacketRail.TrainEdit.SEND, 0, n, new CompoundTag()));
                    sendOpen = false;
                });
                y += 12;
            }
            if (names.isEmpty()) {
                GuiDraw.text(graphics, font, Component.translatable("rail.pioneers.train.no_stations"), x, y, 0.6F, 0xFF7E8E8A, w);
                y += 10;
            }
        }
        return y;
    }

    private enum RailList {TRAINS, STATIONS, DELIVERIES, ALERTS}

    private static RailList railList = RailList.TRAINS;

    private void drawRailTab(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h) {
        int bottom = y + h;
        RailList[] lists = RailList.values();
        int cw = (w - 3 * 2) / lists.length;
        for (int i = 0; i < lists.length; i++) {
            RailList l = lists[i];
            int n = switch (l) {
                case TRAINS -> ClientRailMap.trains.size();
                case STATIONS -> ClientRailMap.stations.size();
                case DELIVERIES -> ClientRailMap.deliveries.size();
                case ALERTS -> ClientRailMap.alerts.size();
            };
            chip(graphics, mouseX, mouseY, x + i * (cw + 2), y, cw, 13, Component.translatable("rail.pioneers.map.list." + l.name().toLowerCase(Locale.ROOT), n),
                  railList == l, l == RailList.ALERTS && n > 0 ? 0xB0602A : 0x2E9E78, () -> {
                      railList = l;
                      listScroll = 0;
                  });
        }
        y += 17;
        int row = 22, rows = Math.max(1, (bottom - y) / row);
        switch (railList) {
            case TRAINS -> {
                List<ClientRailMap.MapTrain> list = new ArrayList<>(ClientRailMap.trains);
                list.sort(Comparator.comparing(ClientRailMap.MapTrain::name, String.CASE_INSENSITIVE_ORDER));
                listScroll = Mth.clamp(listScroll, 0, Math.max(0, list.size() - rows));
                if (list.isEmpty()) {
                    GuiDraw.center(graphics, font, Component.translatable("rail.pioneers.map.no_trains"), x + w / 2F, y + 6, 0.65F, 0xFF7E8E8A, w);
                }
                for (int i = 0; i < rows && listScroll + i < list.size(); i++) {
                    ClientRailMap.MapTrain t = list.get(listScroll + i);
                    int ry = y + i * row;
                    boolean sel = selected instanceof ClientRailMap.MapTrain s && s.id().equals(t.id());
                    graphics.fill(x, ry, x + w, ry + row - 2, sel ? 0xFF22413A : over(mouseX, mouseY, x, ry, w, row - 2) ? 0xFF1E2A32 : 0xFF161E25);
                    graphics.fill(x, ry, x + 2, ry + row - 2, trainColor(t));
                    GuiDraw.text(graphics, font, Component.literal(t.name()), x + 6, ry + 2, 0.65F, 0xFFE0F5EA, w - 10);
                    Component sub = Component.translatable("rail.pioneers.status." + t.status().name().toLowerCase(Locale.ROOT));
                    if (!t.dest().isEmpty()) {
                        sub = sub.copy().append(" · ").append(RailText.station(t.dest()));
                    }
                    GuiDraw.text(graphics, font, sub, x + 6, ry + 11, 0.55F, 0xFF9FB4B0, w - 10);
                    click(x, ry, w, row - 2, () -> {
                        selected = t;
                        sendOpen = false;
                        centerOn(t.headX(), t.headZ());
                    });
                }
            }
            case STATIONS -> {
                List<ClientRailMap.MapStation> list = new ArrayList<>(ClientRailMap.stations);
                list.sort(Comparator.comparing(ClientRailMap.MapStation::name, String.CASE_INSENSITIVE_ORDER));
                listScroll = Mth.clamp(listScroll, 0, Math.max(0, list.size() - rows));
                if (list.isEmpty()) {
                    GuiDraw.center(graphics, font, Component.translatable("rail.pioneers.train.no_stations"), x + w / 2F, y + 6, 0.65F, 0xFF7E8E8A, w);
                }
                for (int i = 0; i < rows && listScroll + i < list.size(); i++) {
                    ClientRailMap.MapStation st = list.get(listScroll + i);
                    int ry = y + i * row;
                    graphics.fill(x, ry, x + w, ry + row - 2, selected == st ? 0xFF22413A : over(mouseX, mouseY, x, ry, w, row - 2) ? 0xFF1E2A32 : 0xFF161E25);
                    graphics.fill(x, ry, x + 2, ry + row - 2, roleColor(st.role()));
                    GuiDraw.text(graphics, font, RailText.station(st.name()), x + 6, ry + 2, 0.65F, 0xFFE0F5EA, w - 10);
                    GuiDraw.text(graphics, font, Component.translatable("rail.pioneers.station.role." + st.role().name().toLowerCase(Locale.ROOT)).append(" · ")
                          .append(Component.translatable(st.comingKey(), st.coming())), x + 6, ry + 11, 0.55F, 0xFF9FB4B0, w - 10);
                    click(x, ry, w, row - 2, () -> {
                        selected = st;
                        centerOn(st.pos().getX() + 0.5, st.pos().getZ() + 0.5);
                    });
                }
            }
            case DELIVERIES -> {
                List<ClientRailMap.MapDelivery> list = ClientRailMap.deliveries;
                listScroll = Mth.clamp(listScroll, 0, Math.max(0, list.size() - rows));
                if (list.isEmpty()) {
                    y += wrapped(graphics, Component.translatable("rail.pioneers.map.no_deliveries"), x, y + 2, 0.6F, 0xFF7E8E8A, w, 6);
                }
                for (int i = 0; i < rows && listScroll + i < list.size(); i++) {
                    ClientRailMap.MapDelivery d = list.get(listScroll + i);
                    int ry = y + i * row;
                    graphics.fill(x, ry, x + w, ry + row - 2, 0xFF161E25);
                    graphics.pose().pushPose();
                    graphics.pose().translate(x + 2, ry + 2, 0);
                    graphics.pose().scale(0.9F, 0.9F, 1);
                    graphics.renderItem(RailText.icon(d.delivery().cargo), 0, 0);
                    graphics.pose().popPose();
                    GuiDraw.text(graphics, font, RailText.name(d.delivery().cargo).copy().append(" × " + RailText.amount(d.delivery().amount)), x + 20, ry + 2, 0.6F,
                          0xFFE0F5EA, w - 22);
                    GuiDraw.text(graphics, font, Component.translatable("rail.pioneers.delivery.route", RailText.station(d.from()), RailText.station(d.to()))
                          .append(" · " + d.train()), x + 20, ry + 11, 0.55F, 0xFF9FB4B0, w - 22);
                    BlockPos to = BlockPos.of(d.delivery().to);
                    click(x, ry, w, row - 2, () -> centerOn(to.getX() + 0.5, to.getZ() + 0.5));
                }
            }
            case ALERTS -> {
                List<ClientRailMap.MapAlert> list = ClientRailMap.alerts;
                listScroll = Mth.clamp(listScroll, 0, Math.max(0, list.size() - rows));
                if (list.isEmpty()) {
                    GuiDraw.center(graphics, font, Component.translatable("robotics.pioneers.map.no_alerts"), x + w / 2F, y + 6, 0.65F, 0xFF55E07A, w);
                }
                for (int i = 0; i < rows && listScroll + i < list.size(); i++) {
                    ClientRailMap.MapAlert a = list.get(listScroll + i);
                    int ry = y + i * row;
                    graphics.fill(x, ry, x + w, ry + row - 2, 0xFF2A1C14);
                    graphics.fill(x, ry, x + 2, ry + row - 2, 0xFFFFA040);
                    GuiDraw.text(graphics, font, Component.translatable("rail.pioneers.alert." + a.type().name().toLowerCase(Locale.ROOT)), x + 6, ry + 2, 0.6F,
                          0xFFFFA040, w - 10);
                    GuiDraw.text(graphics, font, Component.literal(ClientRailMap.subject(a) + " · ").append(Component.translatable(
                          "robotics.pioneers.map.alerts.since", duration(a.age()))), x + 6, ry + 11, 0.55F, 0xFF9A9A9A, w - 10);
                    click(x, ry, w, row - 2, () -> centerOn(a.pos().getX() + 0.5, a.pos().getZ() + 0.5));
                }
            }
        }
    }

    private static void rect(GuiGraphics graphics, float x0, float y0, float x1, float y1, int color) {
        quad(graphics, x0, y0, x1, y0, x1, y1, x0, y1, color);
    }

    private static void outline(GuiGraphics graphics, float x0, float y0, float x1, float y1, int color) {
        rect(graphics, x0, y0, x1, y0 + 1, color);
        rect(graphics, x0, y1 - 1, x1, y1, color);
        rect(graphics, x0, y0, x0 + 1, y1, color);
        rect(graphics, x1 - 1, y0, x1, y1, color);
    }

    private static void quad(GuiGraphics graphics, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        Matrix4f matrix = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        float a = (color >>> 24) / 255F, r = (color >> 16 & 255) / 255F, g = (color >> 8 & 255) / 255F, b = (color & 255) / 255F;
        //Les deux sens : le type de rendu de l'interface elimine les faces arriere
        buffer.vertex(matrix, x0, y0, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x3, y3, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x2, y2, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y1, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x0, y0, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y1, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x2, y2, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x3, y3, 0).color(r, g, b, a).endVertex();
    }

    private static void line(GuiGraphics graphics, float x0, float y0, float x1, float y1, float width, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 0.01F) {
            return;
        }
        float nx = -dy / len * width / 2, ny = dx / len * width / 2;
        quad(graphics, x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny, color);
    }

    /** 0.20.27 : tirets qui defilent, sans fleche (routes des navires). */
    private static void dashedPlain(GuiGraphics graphics, float x0, float y0, float x1, float y1, int color, float time) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1) {
            return;
        }
        float step = 7, offset = (time * 0.5F) % step;
        for (float d = offset - step; d < len; d += step) {
            float s0 = Math.max(0, d), e = Math.min(len, d + 3.5F);
            if (e > s0) {
                line(graphics, x0 + dx * s0 / len, y0 + dy * s0 / len, x0 + dx * e / len, y0 + dy * e / len, 1.2F, color);
            }
        }
    }

    /** Trait en tirets qui defilent de la source vers la cible, et pointe de fleche. */
    private static void dashed(GuiGraphics graphics, float x0, float y0, float x1, float y1, int color, float time) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 2) {
            return;
        }
        float step = 8, offset = (time * 0.6F) % step;
        for (float d = offset; d < len; d += step) {
            float e = Math.min(len, d + 5);
            line(graphics, x0 + dx * d / len, y0 + dy * d / len, x0 + dx * e / len, y0 + dy * e / len, 1.6F, color);
        }
        float ux = dx / len, uy = dy / len;
        quad(graphics, x1, y1, x1 - ux * 7 - uy * 3.5F, y1 - uy * 7 + ux * 3.5F, x1 - ux * 5, y1 - uy * 5, x1 - ux * 7 + uy * 3.5F, y1 - uy * 7 - ux * 3.5F,
              color);
    }

    private static void ring(GuiGraphics graphics, float x, float y, float r, int color) {
        int segments = 16;
        for (int i = 0; i < segments; i++) {
            float a0 = i * Mth.TWO_PI / segments, a1 = (i + 1) * Mth.TWO_PI / segments;
            line(graphics, x + Mth.cos(a0) * r, y + Mth.sin(a0) * r, x + Mth.cos(a1) * r, y + Mth.sin(a1) * r, 1.2F, color);
        }
    }

    private static void triangle(GuiGraphics graphics, float x, float y, int color) {
        quad(graphics, x, y - 5, x + 5, y + 4, x, y + 4, x - 5, y + 4, color);
        GuiDraw.center(graphics, Minecraft.getInstance().font, Component.literal("!"), x, y - 1, 0.6F, 0xFF000000, 6);
    }
}

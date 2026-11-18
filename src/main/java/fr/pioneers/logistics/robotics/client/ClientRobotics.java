package fr.pioneers.logistics.robotics.client;

import fr.pioneers.Pioneers;
import fr.pioneers.client.PiKeys;
import fr.pioneers.core.network.PacketRobotics;
import fr.pioneers.core.network.PiNetwork;
import fr.pioneers.logistics.robotics.Endpoint;
import fr.pioneers.logistics.robotics.RobotTask;
import fr.pioneers.logistics.robotics.RoboticOrder;
import fr.pioneers.logistics.robotics.UnitKind;
import fr.pioneers.logistics.robotics.logistics.LogisticPoint;
import fr.pioneers.logistics.robotics.logistics.LogisticsRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Robotique cote client : copie des ordres et des hubs de la dimension (envoyee par le serveur), touche de la carte robotique (O),
 * reglages des points logistiques et visee d'un bloc pour un ordre en cours d'edition.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientRobotics {

    private ClientRobotics() {
    }

    /** Resume d'un hub pour le menu. */
    public record HubInfo(BlockPos center, UnitKind kind, int units, int capacity, int active, int range, int status) {}

    private static List<RoboticOrder> orders = List.of();
    private static List<HubInfo> hubs = List.of();
    private static long lastRequest;

    /** Visee en cours : l'ordre en edition et le champ a remplir (true : source). */
    @Nullable
    private static RoboticOrder picking;
    private static boolean pickingSource;
    @Nullable
    private static Screen pickingParent;

    public static List<RoboticOrder> orders() {
        return orders;
    }

    /** Ordres caches de la carte (le temps de la partie). */
    public static final Set<Integer> HIDDEN_ORDERS = new HashSet<>();

    public static List<HubInfo> hubs() {
        return hubs;
    }

    @Nullable
    public static HubInfo hub(@Nullable BlockPos center) {
        for (HubInfo info : hubs) {
            if (info.center().equals(center)) {
                return info;
            }
        }
        return null;
    }

    public static void receive(CompoundTag tag) {
        List<RoboticOrder> list = new ArrayList<>();
        for (Tag entry : tag.getList("orders", Tag.TAG_COMPOUND)) {
            list.add(RoboticOrder.load((CompoundTag) entry));
        }
        orders = list;
        List<HubInfo> hubList = new ArrayList<>();
        for (Tag entry : tag.getList("hubs", Tag.TAG_COMPOUND)) {
            CompoundTag hub = (CompoundTag) entry;
            hubList.add(new HubInfo(NbtUtils.readBlockPos(hub.getCompound("center")), UnitKind.values()[Math.min(UnitKind.values().length - 1, Math.max(0, hub.getByte("kind")))],
                  hub.getByte("units"), hub.getByte("capacity"), hub.getByte("active"), hub.getShort("range"), hub.getByte("status")));
        }
        hubs = hubList;
    }

    /** Demande l'etat au serveur (au plus une fois par seconde). */
    public static void request() {
        long now = System.currentTimeMillis();
        if (now - lastRequest > 1000) {
            lastRequest = now;
            PiNetwork.sendToServer(new PacketRobotics.Request());
        }
    }

    public static void save(RoboticOrder order) {
        PiNetwork.sendToServer(new PacketRobotics.Edit(PacketRobotics.Edit.SAVE, order.save(false)));
    }

    public static void delete(RoboticOrder order) {
        PiNetwork.sendToServer(new PacketRobotics.Edit(PacketRobotics.Edit.DELETE, order.save(false)));
    }

    public static void togglePause(RoboticOrder order) {
        PiNetwork.sendToServer(new PacketRobotics.Edit(PacketRobotics.Edit.TOGGLE_PAUSE, order.save(false)));
    }

    // ------------------------------------------------------------------
    // Ouverture des menus
    // ------------------------------------------------------------------

    /** La carte robotique (touche O). */
    public static void openMap() {
        request();
        Minecraft.getInstance().setScreen(new RoboticsMapScreen());
    }

    /** Reglages d'un point logistique (balise ou coffre), ouverts sur place. */
    public static void openPointEditor(LogisticPoint point) {
        Minecraft.getInstance().setScreen(new PointEditScreen(null, point.getPointPos(), point.endpoint().pos(), point.getConfig().copy(), point.displayIcon(),
              point.displayName().getString(), point.endpoint().side() != null));
    }

    public static void openNewZone(BlockPos a, BlockPos b) {
        openNewZone(null, a, b);
    }

    /** Nouvelle zone (planificateur de zone tenu en main, ou selection sur la carte) : tout se regle dans le planificateur 3D. */
    public static void openNewZone(@Nullable Screen parent, BlockPos a, BlockPos b) {
        Minecraft.getInstance().setScreen(new ZonePlannerScreen(parent, newZone(a, b), true));
    }

    public static void drawNewZone(Screen map, BlockPos a, BlockPos b) {
        openNewZone(map, a, b);
    }

    /** Planificateur 3D ouvert sur un point, sans zone : on la trace directement sur le terrain. */
    /** Terminal logistique : ouvre l'ecran de commande (les stocks arrivent du serveur). */
    public static void openTerminal(BlockPos pos) {
        Minecraft.getInstance().setScreen(new TerminalScreen(pos));
    }

    public static void terminalData(BlockPos pos, CompoundTag tag) {
        if (Minecraft.getInstance().screen instanceof TerminalScreen screen && screen.pos().equals(pos)) {
            screen.receive(tag);
        }
    }

    /** Ouvre le bon planificateur pour une zone (travaux ou zone de travail). */
    public static void openOrder(@Nullable Screen back, RoboticOrder order) {
        if (order.task == RobotTask.BUILDER) {
            openWorks(back, order);
        } else {
            Minecraft.getInstance().setScreen(new ZonePlannerScreen(back, order.copy(), false));
        }
    }

    public static void openWorks(@Nullable Screen back, @Nullable RoboticOrder order) {
        Minecraft.getInstance().setScreen(new WorksPlannerScreen(back, order));
    }

    /** Le plan de travaux demande est arrive. */
    public static void worksPlan(int order, CompoundTag tag) {
        if (Minecraft.getInstance().screen instanceof WorksPlannerScreen screen) {
            screen.receive(order, tag);
        }
    }

    /** Fiche d'une unite : ouvre l'interface, ou met a jour celle deja ouverte. */
    public static void unitInfo(CompoundTag tag) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof UnitScreen screen && screen.entityId() == tag.getInt("id")) {
            screen.update(tag);
        } else if (minecraft.screen == null) {
            minecraft.setScreen(new UnitScreen(tag));
        }
    }

    public static void planZoneAt(@Nullable Screen map, double x, double y, double z) {
        RoboticOrder order = new RoboticOrder();
        order.type = RoboticOrder.Type.ZONE;
        order.task = RobotTask.FARMER;
        request();
        ZonePlannerScreen planner = new ZonePlannerScreen(map, order, true);
        planner.focusOn(new Vec3(x, y, z));
        Minecraft.getInstance().setScreen(planner);
    }

    private static RoboticOrder newZone(BlockPos a, BlockPos b) {
        RoboticOrder order = new RoboticOrder();
        order.type = RoboticOrder.Type.ZONE;
        order.task = RobotTask.FARMER;
        order.min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        order.max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        request();
        return order;
    }

    /** Commence la visee d'un bloc pour un ordre en edition (le menu se ferme le temps de viser). */
    public static void startPicking(RoboticOrder draft, boolean source, @Nullable Screen parent) {
        picking = draft;
        pickingSource = source;
        pickingParent = parent;
        Minecraft.getInstance().setScreen(null);
    }

    public static boolean isPicking() {
        return picking != null;
    }

    // ------------------------------------------------------------------
    // Touche et visee
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            picking = null;
            return;
        }
        while (PiKeys.ROBOTICS.consumeClick()) {
            if (minecraft.screen != null) {
                continue;
            }
            BlockHitResult hit = minecraft.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK ? block : null;
            boolean sneaking = minecraft.player.isShiftKeyDown();
            if (picking != null) {
                RoboticOrder draft = picking;
                picking = null;
                if (!sneaking && hit != null) {
                    Endpoint endpoint = new Endpoint(hit.getBlockPos(), hit.getDirection());
                    if (pickingSource) {
                        draft.source = endpoint;
                    } else {
                        draft.target = endpoint;
                    }
                }
                minecraft.setScreen(new OrderEditScreen(pickingParent, draft));
                pickingParent = null;
            } else if (sneaking && hit != null) {
                //Nouvel ordre de transport vers la face visee
                RoboticOrder draft = new RoboticOrder();
                draft.type = RoboticOrder.Type.TRANSFER;
                draft.target = new Endpoint(hit.getBlockPos(), hit.getDirection());
                draft.amount = 64;
                request();
                minecraft.setScreen(new OrderEditScreen(null, draft));
            } else {
                openMap();
            }
        }
        if ((minecraft.screen instanceof RoboticsScreen && !(minecraft.screen instanceof RoboticsMapScreen) || minecraft.screen == null && RoboticsOverlay.holdingTool(minecraft.player))
            && minecraft.player.tickCount % 40 == 0) {
            request();
        }
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (picking == null || event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth(), h = event.getWindow().getGuiScaledHeight();
        String key = PiKeys.ROBOTICS.getTranslatedKeyMessage().getString();
        Component line = Component.translatable(pickingSource ? "robotics.pioneers.pick.source" : "robotics.pioneers.pick.target")
              .withStyle(pickingSource ? ChatFormatting.AQUA : ChatFormatting.GOLD);
        Component hint = Component.translatable("robotics.pioneers.pick.hint", key).withStyle(ChatFormatting.GRAY);
        //1.0.5-beta (lot 26, retour 16) : milieu du bord haut fixe sous la mire (utilitaire commun d'ancrage)
        int[] box = fr.pioneers.client.look.HudLayouts.pickPanel(w, h, 220, 25);
        int y = box[1] + 3, cx = box[0] + (box[2] - box[0]) / 2;
        graphics.fill(box[0], box[1], box[2], box[3], 0x90000000);
        graphics.drawCenteredString(minecraft.font, line, cx, y, 0xFFFFFF);
        graphics.drawCenteredString(minecraft.font, hint, cx, y + 11, 0xFFFFFF);
    }

    /**
     * 0.20.46 (audit) : monde client decharge (changement de dimension, deconnexion) : ses points logistiques quittent la copie client
     * (sinon l'ancien monde restait en memoire jusqu'a la deconnexion et la surcouche dessinait des points fantomes) et les tuiles de la
     * carte, propres a la dimension, sont liberees.
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            orders = List.of();
            hubs = List.of();
            LogisticsRegistry.clearClient();
            ClientRoboticsMap.clear();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        orders = List.of();
        hubs = List.of();
        picking = null;
        pickingParent = null;
        LogisticsRegistry.clearClient();
        ClientRoboticsMap.clear();
    }
}

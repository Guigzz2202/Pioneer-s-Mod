package fr.pioneers.client.rail;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.robotics.ResourcePickerScreen;
import fr.pioneers.client.robotics.RoboticsScreen;
import fr.pioneers.naval.ShipItem;
import fr.pioneers.naval.ShipKind;
import fr.pioneers.network.PacketRail;
import fr.pioneers.network.PiNetwork;
import fr.pioneers.rail.logistics.Cargo;
import fr.pioneers.rail.logistics.Delivery;
import fr.pioneers.rail.logistics.RailLibrary;
import fr.pioneers.rail.logistics.RailResources;
import fr.pioneers.rail.train.CarType;
import fr.pioneers.rail.train.RailItems;
import fr.pioneers.rail.train.Schedule;
import fr.pioneers.rail.train.Train;
import fr.pioneers.robotics.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fiche d'un train (0.20.0, refaite en 0.20.1 : adaptative, lisible, sans debordement). En-tete : nom et mode ; bandeau d'etat (etat,
 * vitesse, destination) ; deux colonnes qui defilent chacune : le convoi (vehicule, reserves, bouton d'inventaire ; conduire, deteler) et
 * l'horaire (arrets en cartes, conditions de depart ET / OU, valeurs saisies au clavier). Les listes de gares et de conditions s'ouvrent en
 * menu deroulant. La fiche se rafraichit chaque seconde ; chaque modification part aussitot au serveur, qui renvoie la fiche.
 * <p>
 * 0.20.5 : la colonne de droite a trois onglets : Horaire (groupe de trains, arrets temporaires en cours, arrets ; nom de gare saisi au
 * clavier pour les jokers), Interruptions (bibliotheque partagee : conditions, arrets, activation pour ce train) et Logistique (service du
 * repartiteur, livraison en cours, carburant, envoi a une gare). Conditions ajoutees : carburant, fluide, chargement, stocks, signal,
 * redstone, place libre. La fiche s'ouvre aussi depuis la carte (edition a distance).
 */
public class TrainScreen extends RoboticsScreen {

    private static final int PAD = 8, MODE_W = 62, ROW = 20, POP_ROW = 12, POP_ROWS = 8;

    /** {@code slots} : cases d'objets (+1000 si le vehicule a une reserve en jauge : il s'ouvre aussi) ; {@code sa}/{@code sc} : reserve. */
    private record CarView(CarType type, int tier, boolean flip, float fill, int water, int cap, int fuel, int slots, long sa, long sc) {

        boolean opens() {
            return slots > 0;
        }
    }

    private enum Popup {NONE, STATION, TYPE, GROUP, SEND}

    private enum Tab {SCHEDULE, INTERRUPTS, LOGISTICS}

    private UUID id;
    private String name = "";
    private boolean auto, canAuto;
    private Train.Status status = Train.Status.IDLE;
    private int stop, index;
    private float speed;
    private String dest = "";
    private Schedule schedule = new Schedule();
    private final List<CarView> cars = new ArrayList<>();
    private final List<String> stations = new ArrayList<>();
    //0.20.5
    private String group = "";
    private final List<String> groups = new ArrayList<>();
    private final Set<String> enabled = new LinkedHashSet<>();
    private final List<RailLibrary.Interrupt> library = new ArrayList<>();
    private boolean logistic;
    private int fuelPct = 100;
    private Train.TempKind tempKind = Train.TempKind.NONE;
    private String tempName = "";
    private int tempIndex;
    private final List<Schedule.Stop> temp = new ArrayList<>();
    @Nullable
    private CompoundTag delivery;
    private Tab tab = Tab.SCHEDULE;
    /** 0.20.11 : fiche d'un navire (machines et cales au lieu du convoi, pas d'interruptions ni de groupes). */
    private boolean naval, docked;
    /** 0.20.25 : fiche ouverte sur un navire : les textes du rail sont remplaces par ceux de la marine quand ils existent. */
    private static boolean navalText;

    /**
     * 0.20.25 : texte du train, ou celui du navire s'il existe ({@code naval.pioneers.ship...} au lieu de {@code rail.pioneers.train...},
     * {@code naval.pioneers...} au lieu de {@code rail.pioneers...}).
     */
    static MutableComponent tr(String key, Object... args) {
        if (navalText && key.startsWith("rail.pioneers.")) {
            String naval = key.startsWith("rail.pioneers.train.") ? "naval.pioneers.ship." + key.substring("rail.pioneers.train.".length())
                  : "naval.pioneers." + key.substring("rail.pioneers.".length());
            if (I18n.exists(naval)) {
                return Component.translatable(naval, args);
            }
        }
        return Component.translatable(key, args);
    }
    private int shipKind, shipTier, seats, engines;
    @Nullable
    private String expanded;

    private EditBox nameBox;
    /** Champ de texte flottant : nom de groupe, de gare, d'interruption, de signal. */
    private EditBox textBox;
    @Nullable
    private Consumer<String> textDone;
    private int convoyScroll, scheduleScroll, selected = -1, ticks;
    private int convoyHeight, scheduleHeight;
    //Zones des deux listes (pour la molette)
    private int lx, lw, rx, rw, listTop, listBottom;
    //Menu deroulant : sorte, cible (arrets ou conditions), ancre et defilement
    private Popup popup = Popup.NONE;
    private int popIndex, popX, popY, popW, popScroll;
    @Nullable
    private Supplier<List<Schedule.Stop>> popStops;
    @Nullable
    private Supplier<List<Schedule.Condition>> popConds;
    @Nullable
    private Runnable popCommit;

    public TrainScreen(CompoundTag data) {
        this(data, null);
    }

    public TrainScreen(CompoundTag data, @Nullable Screen parent) {
        super(tr(data.getBoolean("naval") ? "naval.pioneers.ship.title" : "rail.pioneers.train.title"), parent, 440, 262);
        read(data);
    }

    public UUID trainId() {
        return id;
    }

    private void read(CompoundTag t) {
        id = t.getUUID("id");
        name = t.getString("name");
        auto = t.getBoolean("auto");
        canAuto = t.getBoolean("canAuto");
        status = Train.Status.byId(t.getByte("status"));
        stop = t.getInt("stop");
        speed = t.getFloat("speed");
        index = t.getInt("index");
        dest = t.getString("dest");
        schedule = Schedule.load(t.getCompound("schedule"));
        cars.clear();
        for (Tag ct : t.getList("cars", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) ct;
            cars.add(new CarView(CarType.byId(c.getByte("type")), c.getByte("tier"), c.getBoolean("flip"), c.getFloat("fill"), c.getInt("water"),
                  c.getInt("cap"), c.getInt("fuel"), c.getInt("slots"), c.getLong("sa"), c.getLong("sc")));
        }
        stations.clear();
        for (Tag s : t.getList("stations", Tag.TAG_STRING)) {
            stations.add(s.getAsString());
        }
        group = t.getString("group");
        groups.clear();
        for (Tag s : t.getList("groups", Tag.TAG_STRING)) {
            groups.add(s.getAsString());
        }
        enabled.clear();
        for (Tag s : t.getList("inter", Tag.TAG_STRING)) {
            enabled.add(RailLibrary.key(s.getAsString()));
        }
        library.clear();
        for (Tag s : t.getList("library", Tag.TAG_COMPOUND)) {
            library.add(RailLibrary.Interrupt.load((CompoundTag) s));
        }
        logistic = t.getBoolean("logistic");
        fuelPct = t.getInt("fuelPct");
        tempKind = Train.TempKind.values()[Mth.clamp(t.getByte("tempKind"), 0, Train.TempKind.values().length - 1)];
        tempName = t.getString("tempName");
        tempIndex = t.getInt("tempIndex");
        temp.clear();
        temp.addAll(Schedule.loadStops(t.getList("temp", Tag.TAG_COMPOUND), 16));
        delivery = t.contains("delivery") ? t.getCompound("delivery") : null;
        naval = t.getBoolean("naval");
        navalText = naval;
        docked = t.getBoolean("docked");
        shipKind = t.getByte("kind");
        shipTier = t.getByte("tier");
        seats = t.getInt("seats");
        engines = 0;
        for (CarView c : cars) {
            if (c.type.locomotive || c.type == CarType.TENDER) {
                engines++;
            }
        }
        if (selected >= cars.size()) {
            selected = -1;
        }
        if (popup == Popup.STATION && (popStops == null || popIndex >= popStops.get().size())
              || popup == Popup.TYPE && (popConds == null || popIndex >= popConds.get().size())) {
            popup = Popup.NONE;
        }
    }

    public void update(CompoundTag data) {
        String typed = nameBox != null && nameBox.isFocused() ? nameBox.getValue() : null;
        read(data);
        if (nameBox != null) {
            nameBox.setValue(typed != null ? typed : name);
        }
    }

    @Override
    protected void init() {
        super.init();
        //0.20.31 : le champ du nom commence apres le titre (« Fiche du navire » le recouvrait)
        int nameX = left + Math.max(44, 8 + font.width(title) + 8);
        int nameW = Math.min(180, left + panelWidth - PAD - 2 * MODE_W - 2 - 8 - nameX);
        nameBox = new EditBox(font, nameX, top + 4, Math.max(60, nameW), 14, tr("rail.pioneers.train.name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(name);
        nameBox.setHint(tr("rail.pioneers.train.name").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(nameBox);
        textBox = new EditBox(font, 0, 0, 80, 12, Component.empty());
        textBox.setMaxLength(64);
        textBox.setVisible(false);
        addRenderableWidget(textBox);
    }

    @Override
    public void tick() {
        super.tick();
        nameBox.tick();
        textBox.tick();
        //Etat, vitesse et reserves a jour pendant que la fiche est ouverte
        if (++ticks % 20 == 0) {
            send(PacketRail.TrainEdit.REFRESH, index, "", null);
        }
    }

    private void send(int action, int value, String text, @Nullable CompoundTag data) {
        PiNetwork.sendToServer(new PacketRail.TrainEdit(id, action, value, text, data == null ? new CompoundTag() : data));
    }

    private void sendSchedule() {
        send(PacketRail.TrainEdit.SCHEDULE, Math.max(0, Math.min(stop, schedule.stops.size() - 1)), "", schedule.save());
    }

    private void sendInterrupt(String old, RailLibrary.Interrupt i) {
        send(PacketRail.TrainEdit.INTERRUPT_PUT, 0, old, i.save());
    }

    private void sendEnabled() {
        CompoundTag d = new CompoundTag();
        ListTag names = new ListTag();
        for (RailLibrary.Interrupt i : library) {
            if (enabled.contains(RailLibrary.key(i.name))) {
                names.add(StringTag.valueOf(i.name));
            }
        }
        d.put("names", names);
        send(PacketRail.TrainEdit.INTERRUPTS, 0, "", d);
    }

    // ------------------------------------------------------------------ saisie de texte

    private void startText(int x, int y, int w, String value, int max, Consumer<String> done) {
        finishText();
        textBox.setX(x);
        textBox.setY(y);
        textBox.setWidth(Math.max(40, w));
        textBox.setMaxLength(max);
        textBox.setValue(value);
        textBox.setVisible(true);
        textBox.setFocused(true);
        setFocused(textBox);
        textDone = done;
    }

    private void finishText() {
        if (textDone != null) {
            Consumer<String> d = textDone;
            textDone = null;
            d.accept(textBox.getValue().trim());
        }
        textBox.setVisible(false);
        textBox.setFocused(false);
    }

    private void cancelText() {
        textDone = null;
        textBox.setVisible(false);
        textBox.setFocused(false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (textDone != null && !(mx >= textBox.getX() && mx < textBox.getX() + textBox.getWidth() && my >= textBox.getY() && my < textBox.getY() + 12)) {
            finishText();
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Condition par indices (la fiche peut avoir ete rafraichie depuis le dessin). */
    @Nullable
    private static Schedule.Condition cond(Supplier<List<Schedule.Condition>> list, int k) {
        List<Schedule.Condition> l = list.get();
        return l != null && k >= 0 && k < l.size() ? l.get(k) : null;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (editing()) {
            return super.keyPressed(key, scan, mods);
        }
        if (textDone != null) {
            if (key == 257 || key == 335) {
                finishText();
            } else if (key == 256) {
                cancelText();
            } else {
                textBox.keyPressed(key, scan, mods);
            }
            return true;
        }
        if (popup != Popup.NONE && key == 256) {
            popup = Popup.NONE;
            return true;
        }
        if (nameBox.isFocused() && (key == 257 || key == 335)) {
            send(PacketRail.TrainEdit.NAME, 0, nameBox.getValue(), null);
            nameBox.setFocused(false);
            return true;
        }
        if (nameBox.isFocused() && key != 256) {
            return nameBox.keyPressed(key, scan, mods) || true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (textDone != null && !editing()) {
            return textBox.charTyped(c, mods);
        }
        return super.charTyped(c, mods);
    }

    @Override
    public void onClose() {
        commitEdit();
        finishText();
        if (!nameBox.getValue().equals(name)) {
            send(PacketRail.TrainEdit.NAME, 0, nameBox.getValue(), null);
        }
        super.onClose();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        super.mouseScrolled(mx, my, delta);
        if (textDone != null) {
            finishText();
        }
        int step = (int) (delta * 16);
        if (popup != Popup.NONE && mx >= popX && mx < popX + popW && my >= popY) {
            popScroll = Mth.clamp(popScroll - (int) Math.signum(delta), 0, Math.max(0, popItems().size() - POP_ROWS));
        } else if (mx < lx + lw + 4) {
            convoyScroll = Mth.clamp(convoyScroll - step, 0, Math.max(0, convoyHeight - (listBottom - listTop)));
        } else {
            scheduleScroll = Mth.clamp(scheduleScroll - step, 0, Math.max(0, scheduleHeight - (listBottom - listTop - 16)));
        }
        return true;
    }

    // ------------------------------------------------------------------ dessin

    @Override
    protected void drawContents(@NotNull GuiGraphics g, int mx, int my, float partial) {
        int w = panelWidth, h = panelHeight;
        // --- mode (en-tete, a droite)
        int mx0 = left + w - PAD - 2 * MODE_W - 2;
        chip(g, mx, my, mx0, top + 4, MODE_W, 14, tr("rail.pioneers.train.manual"), !auto, 0x4F6A80,
              () -> send(PacketRail.TrainEdit.MODE, 0, "", null));
        chip(g, mx, my, mx0 + MODE_W + 2, top + 4, MODE_W, 14, tr("rail.pioneers.train.auto"), auto, 0x2E9E78,
              () -> {
                  if (canAuto) {
                      send(PacketRail.TrainEdit.MODE, 1, "", null);
                  }
              });
        tooltip(mx, my, mx0 + MODE_W + 2, top + 4, MODE_W, 14, canAuto ? tr("rail.pioneers.train.auto_tip")
              : tr("rail.pioneers.train.cannot_auto"));
        // --- bandeau d'etat
        drawStatus(g, left + PAD, top + 22, w - 2 * PAD);
        // --- colonnes
        int bodyTop = top + 42;
        lx = left + PAD;
        lw = Mth.clamp((w - 2 * PAD - 8) * 2 / 5, 118, 150);
        rx = lx + lw + 8;
        rw = left + w - PAD - rx;
        listTop = bodyTop + 15;
        listBottom = top + h - PAD - 17;
        header(g, lx, bodyTop, lw, tr(naval ? "naval.pioneers.ship.decks" : "rail.pioneers.train.convoy"),
              Component.literal(Integer.toString(cars.size())));
        drawTabs(g, mx, my, rx, bodyTop, rw);
        drawConvoy(g, mx, my);
        switch (tab) {
            case SCHEDULE -> drawSchedule(g, mx, my);
            case INTERRUPTS -> drawInterrupts(g, mx, my);
            case LOGISTICS -> drawLogistics(g, mx, my);
        }
        if (popup != Popup.NONE) {
            drawPopup(g, mx, my);
        }
    }

    private void header(GuiGraphics g, int x, int y, int w, Component label, Component right) {
        GuiDraw.text(g, font, label.copy().withStyle(ChatFormatting.BOLD), x, y + 1, 0.75F, 0xFF7FE6C0, w - 30);
        GuiDraw.right(g, font, right, x + w, y + 2, 0.7F, D_FAINT);
        g.fill(x, y + 11, x + w, y + 12, D_LINE);
    }

    private void drawTabs(GuiGraphics g, int mx, int my, int x, int y, int w) {
        Tab[] tabs = naval ? new Tab[]{Tab.SCHEDULE, Tab.LOGISTICS} : Tab.values();
        int tw = (w - 2 * (tabs.length - 1)) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int tx = x + i * (tw + 2);
            Component label = switch (t) {
                case SCHEDULE -> tr("rail.pioneers.train.tab.schedule", schedule.stops.size());
                case INTERRUPTS -> tr("rail.pioneers.train.tab.interrupts", enabled.size());
                case LOGISTICS -> tr(logistic ? "rail.pioneers.train.tab.logistics_on" : "rail.pioneers.train.tab.logistics");
            };
            chip(g, mx, my, tx, y, tw, 11, label, tab == t, 0x2E6E5A, () -> {
                if (tab != t) {
                    finishText();
                    popup = Popup.NONE;
                    tab = t;
                    scheduleScroll = 0;
                }
            });
        }
        g.fill(x, y + 12, x + w, y + 13, D_LINE);
    }

    private void drawStatus(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 15, 0xFF0E1419);
        g.renderOutline(x, y, w, 15, D_LINE);
        int sc = statusColor();
        g.fill(x + 5, y + 5, x + 10, y + 10, sc);
        Component st = tr((naval ? "naval.pioneers.status." : "rail.pioneers.status.") + status.name().toLowerCase(Locale.ROOT))
              .withStyle(ChatFormatting.BOLD);
        int half = (w - 20) / 2;
        GuiDraw.text(g, font, st, x + 14, y + 4, 0.8F, sc, half);
        Component info = tr("rail.pioneers.train.speed", String.format(Locale.ROOT, "%.1f", speed));
        if (!dest.isEmpty()) {
            info = info.copy().append("   ").append(tr("rail.pioneers.train.dest", RailText.station(dest)));
        }
        if (!group.isEmpty()) {
            info = info.copy().append("   ").append(tr("rail.pioneers.train.in_group", group));
        }
        float iw = Math.min(font.width(info) * 0.75F, half);
        GuiDraw.text(g, font, info, x + w - 6 - iw, y + 4, 0.75F, D_DIM, half);
    }

    private void drawConvoy(GuiGraphics g, int mx, int my) {
        int x = lx, w = lw;
        beginClip(g, x, listTop, x + w, listBottom);
        int y = listTop - convoyScroll;
        for (int i = 0; i < cars.size(); i++) {
            CarView c = cars.get(i);
            boolean sel = selected == i, hover = over(mx, my, x, y, w, ROW);
            g.fill(x, y, x + w, y + ROW, sel ? 0xFF22413A : hover ? 0xFF1E2A32 : 0xFF161E25);
            if (i == index) {
                g.fill(x, y, x + 2, y + ROW, ACCENT);
            }
            ItemStack icon = naval ? ShipItem.stack(ShipKind.byId(shipKind), shipTier) : RailItems.stack(c.type, c.tier);
            g.renderItem(icon, x + 3, y + 2);
            int tx = x + 22, tw = w - 22 - (c.opens() ? 18 : 3);
            Component label = !naval ? icon.getHoverName() : i < engines ? tr("naval.pioneers.engine." + c.type.lower())
                  : tr("naval.pioneers.hold", i - engines + 1);
            GuiDraw.text(g, font, label, tx, y + 2, 0.75F, D_TEXT, tw);
            if (c.cap > 0) {
                int bw = Math.max(20, tw - 40);
                GuiDraw.bar(g, tx, y + 13, bw, 4, c.water / (double) Math.max(1, c.cap), 0xFF3F76E4);
                GuiDraw.text(g, font, tr("rail.pioneers.train.fuel", c.fuel), tx + bw + 3, y + 11, 0.7F, D_DIM, tw - bw - 3);
            } else if (c.sc > 0) {
                int bw = Math.max(20, tw - 28);
                GuiDraw.bar(g, tx, y + 13, bw, 4, c.sa / (double) c.sc, storeColor(c.type));
                GuiDraw.text(g, font, Component.literal(Math.round(100.0 * c.sa / c.sc) + " %"), tx + bw + 3, y + 11, 0.7F, D_DIM, tw - bw - 3);
            } else if (c.opens()) {
                int bw = Math.max(20, tw - 28);
                GuiDraw.bar(g, tx, y + 13, bw, 4, c.fill, 0xFFB07A3E);
                GuiDraw.text(g, font, Component.literal(Math.round(c.fill * 100) + " %"), tx + bw + 3, y + 11, 0.7F, D_DIM, tw - bw - 3);
            }
            int fi = i;
            click(x, y, w, ROW, () -> selected = selected == fi ? -1 : fi);
            if (c.opens()) {
                iconChip(g, mx, my, x + w - 17, y + 3, 14, 14, new ItemStack(Items.CHEST), false, 0x3F76E4,
                      () -> send(PacketRail.TrainEdit.INVENTORY, fi, "", null));
                tooltip(mx, my, x + w - 17, y + 3, 14, 14, tr("rail.pioneers.train.inventory"));
            }
            y += ROW + 2;
        }
        convoyHeight = y + convoyScroll - listTop;
        endClip(g);
        // --- boutons sous la liste
        int by = listBottom + 3, bw = (w - 3) / 2;
        if (naval) {
            //Ferry a quai : monter a bord
            boolean can = seats > 0 && docked;
            button(g, mx, my, x, by, w, 14, tr("naval.pioneers.ship.board"), can, () -> {
                send(PacketRail.TrainEdit.BOARD, 0, "", null);
                onClose();
            });
            tooltip(mx, my, x, by, w, 14, tr(seats > 0 ? "naval.pioneers.ship.board_tip" : "naval.pioneers.ship.no_seats"));
            return;
        }
        int loco = -1;
        for (int i = 0; i < cars.size(); i++) {
            if (cars.get(i).type.locomotive) {
                loco = loco < 0 || i == selected ? i : loco;
            }
        }
        int fl = loco;
        button(g, mx, my, x, by, bw, 14, tr("rail.pioneers.train.drive"), loco >= 0, () -> {
            send(PacketRail.TrainEdit.BOARD, fl, "", null);
            onClose();
        });
        boolean canCut = !auto && selected >= 0 && selected < cars.size() - 1 && speed < 0.05F;
        button(g, mx, my, x + w - bw, by, bw, 14, tr("rail.pioneers.train.decouple"), canCut, () -> {
            send(PacketRail.TrainEdit.DECOUPLE, selected, "", null);
            selected = -1;
        });
        tooltip(mx, my, x + w - bw, by, bw, 14, tr(canCut ? "rail.pioneers.train.decouple_ok" : "rail.pioneers.train.decouple_tip"));
    }

    // ------------------------------------------------------------------ onglet Horaire

    private void drawSchedule(GuiGraphics g, int mx, int my) {
        int x = rx, w = rw;
        beginClip(g, x, listTop, x + w, listBottom);
        int y = listTop - scheduleScroll;
        // --- groupe (pas pour les navires)
        if (!naval) {
            GuiDraw.text(g, font, tr("rail.pioneers.train.group"), x, y + 3, 0.72F, D_DIM, 50);
            int gx = x + 52, gw = Math.min(150, w - 52);
            dropdown(g, mx, my, gx, y, gw, 12, group.isEmpty() ? tr("rail.pioneers.train.group.none") : Component.literal(group),
                  group.isEmpty() ? D_DIM : D_TEXT, () -> open(Popup.GROUP, 0, gx, listTop - scheduleScroll + 13, gw, null, null, null));
            tooltip(mx, my, gx, y, gw, 12, tr("rail.pioneers.train.group_tip"));
            y += 16;
        }
        // --- arrets temporaires en cours
        if (tempKind != Train.TempKind.NONE && !temp.isEmpty()) {
            y = drawTemp(g, mx, my, x, y, w) + 4;
        }
        if (schedule.stops.isEmpty()) {
            y += wrapped(g, tr("rail.pioneers.train.no_stops"), x + 2, y + 2, 0.75F, D_DIM, w - 4, 6) + 6;
        }
        Supplier<List<Schedule.Stop>> stops = () -> schedule.stops;
        for (int i = 0; i < schedule.stops.size(); i++) {
            boolean current = tempKind == Train.TempKind.NONE && i == stop % Math.max(1, schedule.stops.size());
            y = drawStop(g, mx, my, x, y, w, stops, i, this::sendSchedule, current, true) + 4;
        }
        scheduleHeight = y + scheduleScroll - listTop;
        endClip(g);
        primary(g, mx, my, x, listBottom + 3, w, 14, tr("rail.pioneers.train.add_stop"), schedule.stops.size() < 32, () -> {
            schedule.stops.add(newStop());
            sendSchedule();
            scheduleScroll = Integer.MAX_VALUE / 2;
        });
        scheduleScroll = Mth.clamp(scheduleScroll, 0, Math.max(0, scheduleHeight - (listBottom - listTop)));
    }

    private Schedule.Stop newStop() {
        Schedule.Stop s = new Schedule.Stop();
        s.station = stations.isEmpty() ? "" : stations.get(0);
        Schedule.Condition c = new Schedule.Condition();
        c.type = Schedule.Type.INACTIVITY;
        c.value = 5;
        s.conditions.add(c);
        return s;
    }

    private int drawTemp(GuiGraphics g, int mx, int my, int x, int y, int w) {
        int h = 16 + temp.size() * 10 + 2;
        g.fill(x, y, x + w, y + h, 0xFF1F1A10);
        g.renderOutline(x, y, w, h, 0xFFB08A3A);
        Component title = switch (tempKind) {
            case INTERRUPT -> tr("rail.pioneers.train.temp.interrupt", tempName);
            case DELIVERY -> tr("rail.pioneers.train.temp.delivery");
            default -> tr("rail.pioneers.train.temp.order");
        };
        GuiDraw.text(g, font, title.copy().withStyle(ChatFormatting.BOLD), x + 4, y + 4, 0.72F, 0xFFFFC870, w - 60);
        button(g, mx, my, x + w - 52, y + 2, 50, 11, tr("rail.pioneers.train.temp.cancel"), true,
              () -> send(PacketRail.TrainEdit.CANCEL_TEMP, 0, "", null));
        tooltip(mx, my, x + w - 52, y + 2, 50, 11, tr(tempKind == Train.TempKind.DELIVERY ? "rail.pioneers.train.temp.cancel_delivery"
              : "rail.pioneers.train.temp.cancel_tip"));
        int cy = y + 16;
        for (int i = 0; i < temp.size(); i++) {
            boolean cur = i == tempIndex;
            GuiDraw.text(g, font, Component.literal((cur ? "▶ " : "   ") + (i + 1) + ". ").append(RailText.station(temp.get(i).station)), x + 6, cy, 0.7F,
                  cur ? D_TEXT : D_DIM, w - 10);
            cy += 10;
        }
        return y + h;
    }

    /**
     * Arret (carte) d'une liste : numero (horaire : clic = y aller), gare (menu deroulant ou saisie), monter / descendre / retirer,
     * conditions de depart.
     */
    private int drawStop(GuiGraphics g, int mx, int my, int x, int y, int w, Supplier<List<Schedule.Stop>> list, int i, Runnable commit, boolean current,
          boolean isSchedule) {
        Schedule.Stop s = list.get().get(i);
        int n = s.conditions.size();
        int height = 18 + n * 14 + 14;
        g.fill(x, y, x + w, y + height, 0xFF161E25);
        g.renderOutline(x, y, w, height, current && auto ? ACCENT : D_LINE);
        // --- numero (clic : y aller)
        int bx = x + 3, by = y + 3;
        boolean hoverN = over(mx, my, bx, by, 13, 12);
        g.fill(bx, by, bx + 13, by + 12, current ? (auto ? 0xFF2E9E78 : 0xFF3C4A57) : hoverN && isSchedule ? 0xFF2E3A46 : 0xFF222B34);
        GuiDraw.center(g, font, Component.literal(Integer.toString(i + 1)), bx + 6.5F, by + 3, 0.75F, 0xFFFFFFFF, 12);
        if (!current && isSchedule) {
            click(bx, by, 13, 12, () -> send(PacketRail.TrainEdit.SKIP, i, "", null));
            tooltip(mx, my, bx, by, 13, 12, tr("rail.pioneers.train.goto"));
        }
        // --- gare (menu deroulant)
        int sx = x + 19, sw = w - 19 - 3 * 13 - 4;
        boolean joker = Schedule.hasJoker(s.station);
        boolean known = joker || stations.stream().anyMatch(st -> st.equalsIgnoreCase(s.station));
        Component label = s.station.isEmpty() ? tr("rail.pioneers.train.pick_station")
              : RailText.station(s.station).copy().append(known ? "" : " (?)");
        int fy = y;
        dropdown(g, mx, my, sx, by, sw, 12, label, joker ? 0xFFFFC870 : known || s.station.isEmpty() ? D_TEXT : 0xFFFF8A7A,
              () -> open(Popup.STATION, i, sx, by + 13, sw, list, null, commit));
        if (!known && !s.station.isEmpty()) {
            tooltip(mx, my, sx, by, sw, 12, tr("rail.pioneers.train.no_station", s.station));
        } else if (joker) {
            tooltip(mx, my, sx, by, sw, 12, tr("rail.pioneers.train.joker_tip"));
        }
        int ax = x + w - 3 * 13 - 1;
        button(g, mx, my, ax, by, 12, 12, Component.literal("↑"), i > 0, () -> moveStop(list, i, -1, commit));
        button(g, mx, my, ax + 13, by, 12, 12, Component.literal("↓"), i < list.get().size() - 1, () -> moveStop(list, i, 1, commit));
        button(g, mx, my, ax + 26, by, 12, 12, Component.literal("✕"), true, () -> {
            if (i < list.get().size()) {
                list.get().remove(i);
                popup = Popup.NONE;
                commit.run();
            }
        });
        // --- conditions
        Supplier<List<Schedule.Condition>> conds = () -> i < list.get().size() ? list.get().get(i).conditions : List.of();
        int cy = fy + 18;
        for (int k = 0; k < n; k++) {
            drawCondition(g, mx, my, x + 6, cy, w - 9, conds, k, commit);
            cy += 14;
        }
        if (n < 8) {
            button(g, mx, my, x + 6, cy, Math.min(96, w - 12), 11, tr("rail.pioneers.train.add_condition"), true, () -> {
                if (i < list.get().size()) {
                    Schedule.Condition c = new Schedule.Condition();
                    c.type = Schedule.Type.TIME;
                    c.value = 10;
                    list.get().get(i).conditions.add(c);
                    commit.run();
                }
            });
        }
        return y + height;
    }

    private void moveStop(Supplier<List<Schedule.Stop>> list, int i, int d, Runnable commit) {
        List<Schedule.Stop> l = list.get();
        int j = i + d;
        if (i >= 0 && j >= 0 && i < l.size() && j < l.size()) {
            l.add(j, l.remove(i));
            popup = Popup.NONE;
            commit.run();
        }
    }

    private void drawCondition(GuiGraphics g, int mx, int my, int x, int y, int w, Supplier<List<Schedule.Condition>> list, int k, Runnable commit) {
        Schedule.Condition c = cond(list, k);
        if (c == null) {
            return;
        }
        int cx = x;
        if (k > 0) {
            chip(g, mx, my, cx, y, 22, 12, tr(c.or ? "rail.pioneers.train.cond.or" : "rail.pioneers.train.cond.and"), c.or, 0x8A5A20,
                  () -> {
                      Schedule.Condition cc = cond(list, k);
                      if (cc != null) {
                          cc.or = !cc.or;
                          commit.run();
                      }
                  });
            tooltip(mx, my, cx, y, 22, 12, tr("rail.pioneers.train.cond.link_tip"));
        } else {
            GuiDraw.center(g, font, tr("rail.pioneers.train.cond.if"), cx + 11, y + 3, 0.7F, D_FAINT, 22);
        }
        cx += 24;
        int end = x + w - 12;
        boolean res = c.type.hasResource(), named = c.type == Schedule.Type.SIGNAL, cmp = c.type.compares();
        int extra = (res ? 14 : 0) + (named ? 44 : 0) + (cmp ? 14 : 0);
        int tw = Mth.clamp((end - cx) - 50 - extra, 46, 96);
        if (!c.type.hasValue()) {
            tw = end - cx - 2;
        }
        int fcx = cx, ftw = tw;
        dropdown(g, mx, my, cx, y, tw, 12, tr("rail.pioneers.train.cond." + c.type.name().toLowerCase(Locale.ROOT)), D_TEXT,
              () -> open(Popup.TYPE, k, fcx, y + 13, Math.max(ftw, 120), null, list, commit));
        tooltip(mx, my, cx, y, tw, 12, tr("rail.pioneers.train.cond." + c.type.name().toLowerCase(Locale.ROOT) + ".tip"));
        cx += tw + 2;
        if (res) {
            ItemStack icon = conditionIcon(c);
            Set<Resource> families = c.type == Schedule.Type.ITEM ? Set.of(Resource.ITEMS) : c.type == Schedule.Type.FLUID
                  ? Set.of(Resource.FLUIDS, Resource.GAS) : Set.of(Resource.ITEMS, Resource.FLUIDS, Resource.GAS);
            iconChip(g, mx, my, cx, y, 12, 12, icon, false, 0x3F76E4, () -> minecraft.setScreen(new ResourcePickerScreen(this, families.iterator().next(),
                  families, c.type != Schedule.Type.ITEM, picked -> {
                      Schedule.Condition cc = cond(list, k);
                      Cargo cargo = RailResources.fromLogistic(picked);
                      if (cc == null) {
                          return;
                      }
                      if (cargo == null) {
                          //« N'importe lequel » de la famille
                          cc.item = null;
                          cc.kind = picked.type == Resource.GAS ? Schedule.GAS : picked.type == Resource.FLUIDS ? Schedule.FLUID : Schedule.ITEM;
                      } else {
                          cc.item = cargo.id();
                          cc.kind = cargo.kind() == Cargo.Kind.GAS ? Schedule.GAS : cargo.kind() == Cargo.Kind.FLUID ? Schedule.FLUID : Schedule.ITEM;
                      }
                      commit.run();
                  })));
            tooltip(mx, my, cx, y, 12, 12, c.item == null ? tr("rail.pioneers.train.pick_item") : conditionName(c));
            cx += 14;
        }
        if (named) {
            int nw = 42;
            boolean hover = over(mx, my, cx, y, nw, 12);
            g.fill(cx, y, cx + nw, y + 12, hover ? 0xFF2E3A46 : 0xFF0E1419);
            g.renderOutline(cx, y, nw, 12, hover ? 0xFF4A6A78 : 0xFF2F3B46);
            GuiDraw.text(g, font, c.text.isEmpty() ? tr("rail.pioneers.train.signal_name").withStyle(ChatFormatting.DARK_GRAY)
                  : Component.literal(c.text), cx + 2, y + 3, 0.7F, D_TEXT, nw - 4);
            int fx = cx;
            click(cx, y, nw, 12, () -> startText(fx, y, 90, c.text, 32, v -> {
                Schedule.Condition cc = cond(list, k);
                if (cc != null) {
                    cc.text = v;
                    commit.run();
                }
            }));
            tooltip(mx, my, cx, y, nw, 12, tr("rail.pioneers.train.signal_tip"));
            cx += nw + 2;
        }
        if (cmp) {
            String[] cmpS = {"≥", "≤", "="};
            chip(g, mx, my, cx, y, 12, 12, Component.literal(cmpS[Math.floorMod(c.compare, 3)]), false, 0, () -> {
                Schedule.Condition cc = cond(list, k);
                if (cc != null) {
                    cc.compare = (cc.compare + 1) % 3;
                    commit.run();
                }
            });
            cx += 14;
        }
        if (c.type.hasValue()) {
            int[] range = range(c.type);
            stepper(g, mx, my, cx, y, Math.max(34, end - cx - 2), c.value, range[0], range[1], range[2], c.type.unit(), v -> {
                Schedule.Condition cc = cond(list, k);
                if (cc != null) {
                    cc.value = v;
                    commit.run();
                }
            });
        }
        button(g, mx, my, end, y, 12, 12, Component.literal("✕"), true, () -> {
            List<Schedule.Condition> l = list.get();
            if (k < l.size()) {
                l.remove(k);
                popup = Popup.NONE;
                commit.run();
            }
        });
    }

    /** Bornes et grand pas d'une valeur de condition. */
    private static int[] range(Schedule.Type t) {
        return switch (t) {
            case ITEM, STATION_STOCK, NETWORK_STOCK -> new int[]{0, 10_000_000, 64};
            case FLUID -> new int[]{0, 100_000_000, 1000};
            case FUEL -> new int[]{0, 100, 10};
            case REDSTONE -> new int[]{0, 15, 5};
            case SIGNAL -> new int[]{-1_000_000, 1_000_000, 10};
            default -> new int[]{1, 3600, 10};
        };
    }

    private static Cargo conditionCargo(Schedule.Condition c) {
        Cargo.Kind kind = c.kind == Schedule.GAS ? Cargo.Kind.GAS : c.kind == Schedule.FLUID ? Cargo.Kind.FLUID : Cargo.Kind.ITEM;
        return new Cargo(kind, c.item);
    }

    private static ItemStack conditionIcon(Schedule.Condition c) {
        if (c.item == null) {
            return c.kind == Schedule.ITEM ? ItemStack.EMPTY : new ItemStack(c.kind == Schedule.GAS ? Items.GLASS_BOTTLE : Items.WATER_BUCKET);
        }
        return RailText.icon(conditionCargo(c));
    }

    private static Component conditionName(Schedule.Condition c) {
        return RailText.name(conditionCargo(c));
    }

    // ------------------------------------------------------------------ onglet Interruptions

    private void drawInterrupts(GuiGraphics g, int mx, int my) {
        int x = rx, w = rw;
        beginClip(g, x, listTop, x + w, listBottom);
        int y = listTop - scheduleScroll;
        y += wrapped(g, tr(group.isEmpty() ? "rail.pioneers.train.interrupts_hint" : "rail.pioneers.train.interrupts_hint_group", group),
              x, y, 0.7F, D_DIM, w, 4) + 4;
        if (library.isEmpty()) {
            y += wrapped(g, tr("rail.pioneers.train.no_interrupts"), x, y, 0.72F, D_FAINT, w, 4) + 4;
        }
        for (RailLibrary.Interrupt in : new ArrayList<>(library)) {
            y = drawInterrupt(g, mx, my, x, y, w, in) + 4;
        }
        scheduleHeight = y + scheduleScroll - listTop;
        endClip(g);
        primary(g, mx, my, x, listBottom + 3, w, 14, tr("rail.pioneers.train.add_interrupt"), library.size() < RailLibrary.MAX_INTERRUPTS,
              () -> {
                  String base = tr("rail.pioneers.train.interrupt_default").getString();
                  String n = base;
                  int k = 2;
                  while (find(n) != null) {
                      n = base + " " + k++;
                  }
                  RailLibrary.Interrupt in = new RailLibrary.Interrupt(n);
                  Schedule.Condition c = new Schedule.Condition();
                  c.type = Schedule.Type.FUEL;
                  c.compare = 1;
                  c.value = 20;
                  in.when.add(c);
                  Schedule.Stop st = new Schedule.Stop();
                  st.station = stations.isEmpty() ? "" : stations.get(0);
                  Schedule.Condition wait = new Schedule.Condition();
                  wait.type = Schedule.Type.FUEL;
                  wait.value = 100;
                  st.conditions.add(wait);
                  in.stops.add(st);
                  library.add(in);
                  enabled.add(RailLibrary.key(n));
                  expanded = RailLibrary.key(n);
                  sendInterrupt("", in);
                  sendEnabled();
                  scheduleScroll = Integer.MAX_VALUE / 2;
              });
        scheduleScroll = Mth.clamp(scheduleScroll, 0, Math.max(0, scheduleHeight - (listBottom - listTop)));
    }

    @Nullable
    private RailLibrary.Interrupt find(String n) {
        for (RailLibrary.Interrupt i : library) {
            if (RailLibrary.key(i.name).equals(RailLibrary.key(n))) {
                return i;
            }
        }
        return null;
    }

    private int drawInterrupt(GuiGraphics g, int mx, int my, int x, int y, int w, RailLibrary.Interrupt in) {
        String key = RailLibrary.key(in.name);
        boolean open = key.equals(expanded), on = enabled.contains(key);
        boolean running = tempKind == Train.TempKind.INTERRUPT && tempName.equalsIgnoreCase(in.name);
        int y0 = y;
        g.fill(x, y, x + w, y + 16, open ? 0xFF1C2A30 : 0xFF161E25);
        g.renderOutline(x, y, w, 16, running ? 0xFFB08A3A : D_LINE);
        toggle(g, mx, my, x + 4, y + 4, 22, Component.empty(), on, () -> {
            if (!enabled.remove(key)) {
                enabled.add(key);
            }
            sendEnabled();
        });
        tooltip(mx, my, x + 4, y + 2, 18, 12, tr(on ? "rail.pioneers.train.interrupt_on" : "rail.pioneers.train.interrupt_off"));
        Component title = Component.literal(in.name).withStyle(ChatFormatting.BOLD);
        GuiDraw.text(g, font, title, x + 26, y + 4, 0.75F, on ? D_TEXT : D_DIM, w - 26 - 40);
        GuiDraw.right(g, font, tr("rail.pioneers.train.stops", in.stops.size()), x + w - 16, y + 5, 0.65F, D_FAINT);
        button(g, mx, my, x + w - 14, y + 2, 12, 12, Component.literal(open ? "▾" : "▸"), true, () -> expanded = open ? null : key);
        click(x + 26, y, w - 26 - 16, 16, () -> expanded = open ? null : key);
        y += 18;
        if (!open) {
            return y;
        }
        Runnable commit = () -> sendInterrupt(in.name, in);
        // --- nom, dans une autre interruption, supprimer
        GuiDraw.text(g, font, tr("rail.pioneers.train.interrupt_name"), x + 4, y + 3, 0.7F, D_DIM, 40);
        int nx = x + 44, nw = Math.min(120, w - 44 - 60);
        boolean hover = over(mx, my, nx, y, nw, 12);
        g.fill(nx, y, nx + nw, y + 12, hover ? 0xFF2E3A46 : 0xFF0E1419);
        g.renderOutline(nx, y, nw, 12, hover ? 0xFF4A6A78 : 0xFF2F3B46);
        GuiDraw.text(g, font, Component.literal(in.name), nx + 3, y + 3, 0.72F, D_TEXT, nw - 6);
        int fy = y;
        click(nx, y, nw, 12, () -> startText(nx, fy, nw, in.name, RailLibrary.NAME, v -> {
            if (!v.isEmpty() && !v.equalsIgnoreCase(in.name) && find(v) == null) {
                String old = in.name;
                boolean was = enabled.remove(RailLibrary.key(old));
                in.name = v;
                if (was) {
                    enabled.add(RailLibrary.key(v));
                }
                expanded = RailLibrary.key(v);
                sendInterrupt(old, in);
            }
        }));
        button(g, mx, my, x + w - 56, y, 54, 12, tr("rail.pioneers.train.interrupt_delete"), true, () -> {
            library.remove(in);
            enabled.remove(key);
            send(PacketRail.TrainEdit.INTERRUPT_DEL, 0, in.name, null);
        });
        y += 15;
        toggle(g, mx, my, x + 4, y + 2, w - 8, tr("rail.pioneers.train.interrupt_inside"), in.inside, () -> {
            in.inside = !in.inside;
            commit.run();
        });
        tooltip(mx, my, x + 4, y, w - 8, 12, tr("rail.pioneers.train.interrupt_inside_tip"));
        y += 14;
        // --- quand
        GuiDraw.text(g, font, tr("rail.pioneers.train.interrupt_when").withStyle(ChatFormatting.BOLD), x + 4, y, 0.7F, 0xFFFFC870, w);
        y += 10;
        Supplier<List<Schedule.Condition>> when = () -> in.when;
        for (int k = 0; k < in.when.size(); k++) {
            drawCondition(g, mx, my, x + 6, y, w - 9, when, k, commit);
            y += 14;
        }
        if (in.when.size() < 8) {
            button(g, mx, my, x + 6, y, Math.min(96, w - 12), 11, tr("rail.pioneers.train.add_condition"), true, () -> {
                Schedule.Condition c = new Schedule.Condition();
                c.type = Schedule.Type.CARGO;
                in.when.add(c);
                commit.run();
            });
            y += 14;
        }
        // --- arrets
        GuiDraw.text(g, font, tr("rail.pioneers.train.interrupt_stops").withStyle(ChatFormatting.BOLD), x + 4, y, 0.7F, 0xFFFFC870, w);
        y += 10;
        Supplier<List<Schedule.Stop>> stops = () -> in.stops;
        for (int i = 0; i < in.stops.size(); i++) {
            y = drawStop(g, mx, my, x + 4, y, w - 6, stops, i, commit, false, false) + 3;
        }
        if (in.stops.size() < 8) {
            button(g, mx, my, x + 6, y, Math.min(110, w - 12), 11, tr("rail.pioneers.train.add_stop"), true, () -> {
                in.stops.add(newStop());
                commit.run();
            });
            y += 14;
        }
        g.fill(x, y0 + 16, x + 1, y, 0xFF2C3640);
        return y;
    }

    // ------------------------------------------------------------------ onglet Logistique

    private void drawLogistics(GuiGraphics g, int mx, int my) {
        int x = rx, w = rw;
        beginClip(g, x, listTop, x + w, listBottom);
        int y = listTop - scheduleScroll + 2;
        toggle(g, mx, my, x, y, w, tr("rail.pioneers.train.logistic").withStyle(ChatFormatting.BOLD), logistic,
              () -> send(PacketRail.TrainEdit.LOGISTIC, logistic ? 0 : 1, "", null));
        y += 12;
        y += wrapped(g, tr(naval ? "naval.pioneers.ship.logistic_tip" : "rail.pioneers.train.logistic_tip"), x, y, 0.7F, D_DIM, w, 6) + 6;
        // --- tache en cours
        GuiDraw.text(g, font, tr("rail.pioneers.train.task").withStyle(ChatFormatting.BOLD), x, y, 0.72F, 0xFF7FE6C0, w);
        g.fill(x, y + 8, x + w, y + 9, D_LINE);
        y += 12;
        if (delivery != null) {
            Delivery d = Delivery.load(delivery);
            g.fill(x, y, x + w, y + 46, 0xFF161E25);
            g.renderOutline(x, y, w, 46, 0xFF3F76E4);
            g.renderItem(RailText.icon(d.cargo), x + 4, y + 4);
            GuiDraw.text(g, font, RailText.name(d.cargo).copy().append(" × " + RailText.amount(d.amount) + RailText.unit(d.cargo.kind())), x + 24, y + 4, 0.75F,
                  D_TEXT, w - 28);
            GuiDraw.text(g, font, tr("rail.pioneers.delivery.route", RailText.station(delivery.getString("fromName")),
                  RailText.station(delivery.getString("toName"))), x + 24, y + 14, 0.7F, D_DIM, w - 28);
            GuiDraw.text(g, font, tr("rail.pioneers.delivery.state." + d.state.name().toLowerCase(Locale.ROOT)), x + 4, y + 26, 0.72F,
                  0xFFFFC870, w - 8);
            long carried = delivery.getLong("carried");
            GuiDraw.bar(g, x + 4, y + 37, w - 60, 4, carried / (double) Math.max(1, d.amount), 0xFF3F76E4);
            GuiDraw.text(g, font, Component.literal(RailText.amount(carried) + RailText.unit(d.cargo.kind())), x + w - 52, y + 35, 0.7F, D_DIM, 50);
            y += 50;
        } else {
            Component idle = tr(!logistic ? "rail.pioneers.train.task.none"
                  : status == Train.Status.AT_STATION && tempKind == Train.TempKind.NONE ? "rail.pioneers.train.task.waiting" : "rail.pioneers.train.task.to_depot");
            y += wrapped(g, idle, x, y, 0.72F, D_DIM, w, 4) + 6;
        }
        // --- carburant
        GuiDraw.text(g, font, tr("rail.pioneers.train.fuel_pct", fuelPct), x, y, 0.72F, D_TEXT, w);
        GuiDraw.bar(g, x, y + 10, w, 4, fuelPct / 100.0, fuelPct < 20 ? 0xFFE05A4A : 0xFFD8B030);
        y += 20;
        // --- envoyer
        int bw = Math.min(140, w);
        int fy = y;
        button(g, mx, my, x, y, bw, 14, tr("rail.pioneers.train.send"), auto && tempKind != Train.TempKind.DELIVERY,
              () -> open(Popup.SEND, 0, x, fy + 15, bw, null, null, null));
        tooltip(mx, my, x, y, bw, 14, tr(auto ? "rail.pioneers.train.send_tip" : "rail.pioneers.train.send_manual"));
        y += 18;
        scheduleHeight = y + scheduleScroll - listTop;
        endClip(g);
        scheduleScroll = Mth.clamp(scheduleScroll, 0, Math.max(0, scheduleHeight - (listBottom - listTop)));
    }

    // ------------------------------------------------------------------ menus deroulants

    /** Bouton de liste deroulante : libelle et fleche. */
    private void dropdown(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Component label, int color, Runnable action) {
        boolean hover = over(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? 0xFF2E3A46 : 0xFF222B34);
        g.renderOutline(x, y, w, h, hover ? 0xFF4A5A68 : 0xFF2F3B46);
        GuiDraw.text(g, font, label, x + 3, y + (h - 7) / 2F + 0.5F, 0.75F, color, w - 13);
        GuiDraw.text(g, font, Component.literal("▾"), x + w - 8, y + (h - 7) / 2F, 0.8F, D_DIM, 8);
        click(x, y, w, h, action);
    }

    private void open(Popup kind, int i, int x, int y, int w, @Nullable Supplier<List<Schedule.Stop>> stops, @Nullable Supplier<List<Schedule.Condition>> conds,
          @Nullable Runnable commit) {
        commitEdit();
        finishText();
        if (popup == kind && popIndex == i && popStops == stops && popConds == conds) {
            popup = Popup.NONE;
            return;
        }
        popup = kind;
        popIndex = i;
        popStops = stops;
        popConds = conds;
        popCommit = commit;
        popW = Math.min(w, panelWidth - 2 * PAD);
        popX = Math.min(x, left + panelWidth - PAD - popW);
        popY = y;
        popScroll = 0;
    }

    private List<Component> popItems() {
        List<Component> items = new ArrayList<>();
        switch (popup) {
            case STATION -> {
                items.add(tr("rail.pioneers.train.type_station").withStyle(ChatFormatting.ITALIC));
                stations.forEach(n -> items.add(RailText.station(n)));
            }
            case SEND -> stations.forEach(n -> items.add(RailText.station(n)));
            case GROUP -> {
                items.add(tr("rail.pioneers.train.group.none").withStyle(ChatFormatting.ITALIC));
                items.add(tr("rail.pioneers.train.group.new").withStyle(ChatFormatting.ITALIC));
                groups.forEach(n -> items.add(Component.literal(n)));
            }
            case TYPE -> {
                for (Schedule.Type t : Schedule.Type.values()) {
                    items.add(tr("rail.pioneers.train.cond." + t.name().toLowerCase(Locale.ROOT)));
                }
            }
            default -> {
            }
        }
        return items;
    }

    private boolean chosen(int idx) {
        return switch (popup) {
            case STATION -> idx > 0 && popStops != null && popIndex < popStops.get().size()
                  && popStops.get().get(popIndex).station.equalsIgnoreCase(stations.get(idx - 1));
            case GROUP -> idx == 0 ? group.isEmpty() : idx >= 2 && groups.get(idx - 2).equalsIgnoreCase(group);
            case TYPE -> {
                Schedule.Condition c = popConds == null ? null : cond(popConds, popIndex);
                yield c != null && c.type.ordinal() == idx;
            }
            default -> false;
        };
    }

    private void drawPopup(GuiGraphics g, int mx, int my) {
        List<Component> items = popItems();
        int rows = Math.max(1, Math.min(POP_ROWS, items.size()));
        int hgt = rows * POP_ROW + 4;
        int y = popY;
        if (y + hgt > top + panelHeight - 4) {
            //Pas la place dessous : au-dessus de l'ancre
            y = Math.max(top + 4, popY - 13 - hgt - 1);
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        //Un clic hors du menu le ferme (ajoute avant les lignes : elles gagnent le clic)
        click(0, 0, width, height, () -> popup = Popup.NONE);
        g.fill(popX - 1, y - 1, popX + popW + 1, y + hgt + 1, 0xFF3FD0A0);
        g.fill(popX, y, popX + popW, y + hgt, 0xF20B1016);
        if (items.isEmpty()) {
            GuiDraw.text(g, font, tr("rail.pioneers.train.no_stations"), popX + 4, y + 4, 0.75F, D_DIM, popW - 8);
        }
        int first = Mth.clamp(popScroll, 0, Math.max(0, items.size() - rows));
        for (int r = 0; r < rows && first + r < items.size(); r++) {
            int idx = first + r, ry = y + 2 + r * POP_ROW;
            boolean chosen = chosen(idx);
            boolean hover = over(mx, my, popX, ry, popW, POP_ROW);
            g.fill(popX + 1, ry, popX + popW - 1, ry + POP_ROW, hover ? 0xFF2E3A46 : chosen ? 0xFF1C3A33 : 0x00000000);
            GuiDraw.text(g, font, items.get(idx), popX + 4, ry + 3, 0.75F, chosen ? ACCENT : D_TEXT, popW - 10);
            click(popX, ry, popW, POP_ROW, () -> pick(idx));
        }
        if (items.size() > rows) {
            //Ascenseur
            int track = hgt - 4, knob = Math.max(8, track * rows / items.size());
            int ky = y + 2 + (track - knob) * first / Math.max(1, items.size() - rows);
            g.fill(popX + popW - 3, ky, popX + popW - 1, ky + knob, D_DIM);
        }
        g.pose().popPose();
    }

    private void pick(int idx) {
        Popup kind = popup;
        popup = Popup.NONE;
        switch (kind) {
            case STATION -> {
                Supplier<List<Schedule.Stop>> list = popStops;
                Runnable commit = popCommit;
                int i = popIndex;
                if (list == null || commit == null || i >= list.get().size()) {
                    return;
                }
                if (idx == 0) {
                    //Nom saisi (jokers {objet}, {fluide}, {gaz}, gare pas encore construite)
                    startText(popX, popY - 13, popW, list.get().get(i).station, 64, v -> {
                        if (i < list.get().size()) {
                            list.get().get(i).station = v;
                            list.get().get(i).target = 0;
                            commit.run();
                        }
                    });
                } else if (idx - 1 < stations.size()) {
                    list.get().get(i).station = stations.get(idx - 1);
                    list.get().get(i).target = 0;
                    commit.run();
                }
            }
            case SEND -> {
                if (idx < stations.size()) {
                    send(PacketRail.TrainEdit.SEND, 0, stations.get(idx), null);
                }
            }
            case GROUP -> {
                if (idx == 0) {
                    send(PacketRail.TrainEdit.GROUP, 0, "", null);
                } else if (idx == 1) {
                    startText(popX, popY - 13, popW, "", RailLibrary.NAME, v -> {
                        if (!v.isEmpty()) {
                            send(PacketRail.TrainEdit.GROUP, 0, v, null);
                        }
                    });
                } else if (idx - 2 < groups.size()) {
                    send(PacketRail.TrainEdit.GROUP, 0, groups.get(idx - 2), null);
                }
            }
            case TYPE -> {
                Schedule.Condition c = popConds == null ? null : cond(popConds, popIndex);
                if (c != null && popCommit != null) {
                    c.type = Schedule.Type.byId(idx);
                    defaults(c);
                    popCommit.run();
                }
            }
            default -> {
            }
        }
    }

    /** Valeurs de depart d'une condition qui change de sorte. */
    private static void defaults(Schedule.Condition c) {
        int[] r = range(c.type);
        switch (c.type) {
            case ITEM, STATION_STOCK, NETWORK_STOCK -> {
                c.value = 64;
                c.compare = 0;
            }
            case FLUID -> {
                c.value = 1000;
                c.compare = 0;
                if (c.kind == Schedule.ITEM) {
                    c.kind = Schedule.FLUID;
                    c.item = null;
                }
            }
            case FUEL -> {
                c.value = 20;
                c.compare = 1;
            }
            case REDSTONE, SIGNAL -> {
                c.value = 1;
                c.compare = 0;
            }
            default -> {
                if (c.value < r[0] || c.value > r[1]) {
                    c.value = 30;
                }
            }
        }
        if (c.type == Schedule.Type.ITEM && c.kind != Schedule.ITEM) {
            c.kind = Schedule.ITEM;
            c.item = null;
        }
        if (c.type != Schedule.Type.SIGNAL) {
            c.text = "";
        }
    }

    private static int storeColor(CarType type) {
        return switch (type.store) {
            case FLUID -> 0xFF3F76E4;
            case GAS -> 0xFF9A7AD8;
            case ENERGY -> 0xFFD8B030;
            case DATA -> 0xFF30C8A0;
            default -> 0xFF8AA0B0;
        };
    }

    private int statusColor() {
        return switch (status) {
            case RUNNING, AT_STATION -> 0xFF5BE08F;
            case WAIT_SIGNAL, WAIT_TRAIN, DEST_FULL -> 0xFFFFB04A;
            case NO_PATH, NO_FUEL, DERAILED, NO_SCHEDULE -> 0xFFFF6A5A;
            default -> D_TEXT;
        };
    }
}

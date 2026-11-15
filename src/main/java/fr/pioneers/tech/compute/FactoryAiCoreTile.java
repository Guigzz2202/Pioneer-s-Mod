package fr.pioneers.tech.compute;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.tech.computing.ComputerTile;
import fr.pioneers.tech.computing.LogicTile;
import fr.pioneers.tech.computing.Probes;
import fr.pioneers.tech.computing.ScreenBlock;
import fr.pioneers.tech.computing.ScreenTile;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.tech.fusion.FusionCore;
import fr.pioneers.tech.industry.IndustryEvents;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.nuclear.ReactorControl;
import fr.pioneers.tech.nuclear.ReactorView;
import fr.pioneers.core.registries.PiCompute;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Noyau d'usine (0.17.2) : une IA (modele « reseau de neurones » ou « fondation ») qui supervise les machines qu'on lui lie au configurateur
 * de liaison ({@value #LINKS_NEURAL} / {@value #LINKS_FOUNDATION} liaisons).
 * <ul>
 *     <li>donne son bonus aux multiblocs lies (comme une puce installee : -20 % / -30 % d'energie, +20 % / +40 % de vitesse) ;</li>
 *     <li>regule seul les reacteurs lies : fission, insere les barres pour tenir la puissance sous {@value #POWER_CAP}, arret preventif
 *     a 95 % de la temperature d'arret d'urgence ; fusion, coupe le chauffage quand le champ faiblit (avant la disruption) ;</li>
 *     <li>parle : messages courts dans le chat des joueurs proches (pannes, records, regulations, plaisanteries ; personnalite selon le
 *     modele) et sur les ecrans de supervision lies.</li>
 * </ul>
 * Energie : {@value #FE_NEURAL} FE/t (reseau), {@value #FE_FOUNDATION} FE/t (fondation).
 * <p>
 * Interface : 4 etat (0 sans modele, 1 sans energie, 2 en ligne), 5 modele, 6 liaisons, 7 liaisons max, 8 FE/t, 9 interventions,
 * 10 multiblocs supervises.
 */
public class FactoryAiCoreTile extends MachineTile {

    public static final int LINKS_NEURAL = 16, LINKS_FOUNDATION = 64;
    public static final long FE_NEURAL = 512, FE_FOUNDATION = 2_048, ENERGY_STORAGE = 1_000_000;
    public static final double POWER_CAP = 0.85, SCRAM_MARGIN = 0.95, FIELD_MIN = 0.4;
    /** Lot 8b (vraie v2) : puce du noyau, carte de l'etat, journal (ecran raccourci), bouton « Delier » dessous ; batterie a la place commune. */
    public static final int CHIP_X = 30, CHIP_Y = StandardLayout.TOP + 22, BATTERY_X = StandardLayout.BATTERY_X, BATTERY_Y = StandardLayout.BATTERY_Y;
    public static final StandardLayout.Rect CARD = new StandardLayout.Rect(52, StandardLayout.TOP, StandardLayout.WIDTH - 8 - 52, StandardLayout.GAUGE_H);
    /** Lot 8b : valeurs nommees aux indices d'avant. */
    public static final UiValues.Key MODEL = UiValues.Key.of("model"), LINKS = UiValues.Key.of("links"), MAX_LINKS = UiValues.Key.of("max_links"),
          INTERVENTIONS = UiValues.Key.of("interventions"), SUPERVISED = UiValues.Key.of("supervised");
    /** Portee des messages dans le chat (blocs). */
    public static final double CHAT_RANGE = 32;
    public static final int CLEAR_LINKS = 0;
    private static final int LOG = 6, SCREEN_LOG = 20;

    private final ItemSlot chip, battery;
    private final List<BlockPos> links = new ArrayList<>();
    /** Dernier etat vu de chaque multibloc lie, et records. */
    private final Map<BlockPos, Integer> lastStatus = new HashMap<>();
    private final Map<BlockPos, Double> records = new HashMap<>();
    private final Map<BlockPos, Long> lastRegulation = new HashMap<>();
    /** Derniers messages (composants JSON pour l'interface, texte pour les ecrans). */
    private final Deque<String> log = new ArrayDeque<>();
    private final Deque<String> screenLog = new ArrayDeque<>();
    private int state, clock, supervised, interventions;
    private boolean wasOnline;
    private long lastSpeech = Long.MIN_VALUE / 2, nextJoke;

    public FactoryAiCoreTile(BlockPos pos, BlockState state) {
        super(PiCompute.FACTORY_AI_CORE_TILE.get(), pos, state, ENERGY_STORAGE, (int) (FE_FOUNDATION * 8));
        chip = slot(new ItemSlot(() -> 1, s -> AiChips.level(s) >= AiChips.NEURAL, (s, a) -> true, (s, a) -> !a.external(), this::chipChanged,
              CHIP_X, CHIP_Y).ghost(() -> new ItemStack(AiChips.item(AiChips.NEURAL))));
        battery = batterySlot(BATTERY_X, BATTERY_Y);
        sides.set(SideType.ENERGY, RelativeSide.BOTTOM, SideMode.INPUT);
        sides.set(SideType.ENERGY, RelativeSide.BACK, SideMode.INPUT);
    }

    private void chipChanged() {
        setChanged();
        markSync();
    }

    public int model() {
        return AiChips.level(chip.getStack());
    }

    public int maxLinks() {
        return model() >= AiChips.FOUNDATION ? LINKS_FOUNDATION : LINKS_NEURAL;
    }

    public List<BlockPos> links() {
        return links;
    }

    /** Configurateur de liaison : lie une machine (meme dimension, 64 blocs au plus). */
    public boolean addLink(BlockPos target) {
        if (target.equals(worldPosition) || !LogicTile.reachable(level, worldPosition, target) || links.contains(target) || links.size() >= maxLinks()) {
            return false;
        }
        links.add(target.immutable());
        setChanged();
        if (state == 2) {
            say("link", name(target), links.size());
        }
        return true;
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        if (action - CUSTOM == CLEAR_LINKS) {
            links.clear();
            lastStatus.clear();
            records.clear();
            setChanged();
        }
    }

    // ------------------------------------------------------------------
    // Fonctionnement
    // ------------------------------------------------------------------

    @Override
    protected void tick() {
        discharge(battery, FE_FOUNDATION * 8);
        int model = model();
        long fe = model == AiChips.FOUNDATION ? FE_FOUNDATION : FE_NEURAL;
        if (model < AiChips.NEURAL) {
            state = 0;
        } else if (!canWork() || !use(fe)) {
            state = 1;
        } else {
            state = 2;
        }
        boolean online = state == 2;
        if (online != wasOnline) {
            wasOnline = online;
            if (online) {
                IndustryEvents.award(level, worldPosition, "factory_ai_online");
                say("online", links.size());
                nextJoke = level.getGameTime() + 2400 + level.random.nextInt(6000);
            } else if (model >= AiChips.NEURAL) {
                say("offline");
            }
            markSync();
        }
        markWorking(online);
        if (!online || ++clock % 20 != 0) {
            return;
        }
        supervise(model);
        if (nextJoke == 0) {
            nextJoke = level.getGameTime() + 2400 + level.random.nextInt(6000);
        }
        if (level.getGameTime() >= nextJoke) {
            nextJoke = level.getGameTime() + 6000 + level.random.nextInt(6000);
            if (allQuiet()) {
                say("idle");
            }
        }
    }

    /** Toutes les machines liees tournent sans panne ? */
    private boolean allQuiet() {
        for (int status : lastStatus.values()) {
            if (status != ControllerTile.WORKING && status != ControllerTile.IDLE) {
                return false;
            }
        }
        return true;
    }

    private void supervise(int model) {
        supervised = 0;
        List<ScreenTile> screens = new ArrayList<>();
        long now = level.getGameTime();
        for (BlockPos p : links) {
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockEntity t = level.getBlockEntity(p);
            if (t instanceof ScreenTile screen) {
                BlockPos master = ScreenBlock.master(level, p, screen.facing());
                if (level.getBlockEntity(master) instanceof ScreenTile m && !screens.contains(m)) {
                    screens.add(m);
                }
                continue;
            }
            if (!(t instanceof ControllerTile c)) {
                continue;
            }
            supervised++;
            c.supervise(model);
            int status = (int) c.guiValue(4);
            Integer before = lastStatus.put(p, status);
            if (before != null && before != status) {
                if (status == ControllerTile.UNFORMED) {
                    say("unformed", name(p));
                } else if (status == ControllerTile.OVERHEAT && !(c.computeCore() != null)) {
                    say("overheat", name(p));
                } else if (status == ControllerTile.NO_ENERGY) {
                    say("fault", name(p));
                } else if (status == ControllerTile.WORKING && (before == ControllerTile.NO_ENERGY || before == ControllerTile.OVERHEAT)) {
                    say("recovered", name(p));
                }
            }
            regulate(p, c, now);
            //Records : puissance d'un reacteur, calcul servi par un centre de donnees
            double value = 0;
            String unit = "";
            ReactorControl r = c.reactor();
            if (r != null) {
                value = r.thermalPower() * 20 / 1_000_000.0;
                unit = "MW";
            } else if (c.computeCore() != null) {
                value = c.computeCore().served();
                unit = c.computeCore().quantum ? "QOPS" : "GOPS";
            }
            if (value > 0) {
                double record = records.getOrDefault(p, 0.0);
                if (value > record * 1.25 && value > 1) {
                    records.put(p, value);
                    if (record > 0) {
                        say("record", name(p), String.format(Locale.ROOT, "%.1f %s", value, unit));
                    }
                }
            }
        }
        if (!screens.isEmpty()) {
            List<String> lines = new ArrayList<>(screenLog);
            for (ScreenTile s : screens) {
                s.showText(lines);
            }
        }
    }

    /** Regulation des reacteurs lies. */
    private void regulate(BlockPos p, ControllerTile c, long now) {
        //Fusion : le coeur de fusion n'expose pas ReactorControl ; on coupe le chauffage puis on eteint proprement avant la disruption
        FusionCore f = c.fusion();
        if (f != null) {
            if (f.running() && f.disruptionRisk() > 0.5) {
                f.setHeating(false);
                f.shutdown();
                interventions++;
                lastRegulation.put(p, now);
                say("disruption_avoided", name(p));
            }
            return;
        }
        ReactorControl r = c.reactor();
        if (r == null || r.scrammed()) {
            return;
        }
        ReactorView view = c.reactorView();
        int mark = view == null ? 1 : view.mark();
        boolean quiet = now - lastRegulation.getOrDefault(p, Long.MIN_VALUE / 2) < 1200;
        if (mark >= 4) {
            //Fusion : le champ faiblit (helium ou energie de confinement) -> chauffage coupe avant la disruption
            if (view.coolant() < FIELD_MIN && view.power() > 0.01F) {
                r.scram();
                interventions++;
                lastRegulation.put(p, now);
                say("disruption_avoided", name(p));
            }
            return;
        }
        if (r.temperature() > r.scramTemperature() * SCRAM_MARGIN) {
            r.scram();
            interventions++;
            lastRegulation.put(p, now);
            say("preventive_scram", name(p), Math.round(r.temperature()));
            return;
        }
        double fraction = r.powerFraction();
        if (fraction > POWER_CAP && r.rods() < 100) {
            r.setRods(r.rods() + (fraction > 0.95 ? 5 : 2));
            interventions++;
            if (!quiet) {
                lastRegulation.put(p, now);
                say("regulate", name(p), Math.round(fraction * 100));
            }
        }
    }

    private Component name(BlockPos p) {
        return Probes.name(Probes.at(level, p));
    }

    // ------------------------------------------------------------------
    // Parole
    // ------------------------------------------------------------------

    /** Nombre de variantes de chaque message (cles {@code ai.pioneers.say.<modele>.<evenement>.<n>}). */
    public static final int VARIANTS = 3;

    /** Message de l'IA : chat des joueurs proches et ecrans lies (au plus un toutes les 3 s, sauf alerte). */
    private void say(String event, Object... args) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        long now = level.getGameTime();
        boolean alert = event.equals("preventive_scram") || event.equals("disruption_avoided") || event.equals("unformed");
        if (!alert && now - lastSpeech < 60) {
            return;
        }
        lastSpeech = now;
        int model = Math.max(AiChips.NEURAL, model());
        String key = "ai.pioneers.say." + AiChips.KEYS[model] + "." + event + "." + level.random.nextInt(VARIANTS);
        MutableComponent message = Component.translatable(key, args);
        MutableComponent line = Component.translatable("ai.pioneers.chat", Component.translatable("ai.pioneers.name." + AiChips.KEYS[model])
              .withStyle(model == AiChips.FOUNDATION ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA), message.copy().withStyle(alert
              ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(CHAT_RANGE))) {
            player.sendSystemMessage(line);
        }
        level.playSound(null, worldPosition, alert ? SoundEvents.NOTE_BLOCK_BIT.value() : SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS,
              0.5F, model == AiChips.FOUNDATION ? 0.8F : 1.4F);
        log.addLast(Component.Serializer.toJson(message));
        while (log.size() > LOG) {
            log.removeFirst();
        }
        //Ecrans de supervision : texte coupe a leur largeur
        String text = "> " + message.getString();
        while (!text.isEmpty()) {
            int cut = Math.min(text.length(), ComputerTile.SCREEN_COLS);
            screenLog.addLast(text.substring(0, cut));
            text = text.substring(cut);
        }
        while (screenLog.size() > SCREEN_LOG) {
            screenLog.removeFirst();
        }
        markSync();
        setChanged();
    }

    /** Derniers messages (client : interface). */
    public List<Component> messages() {
        List<Component> out = new ArrayList<>();
        for (String json : log) {
            Component c = Component.Serializer.fromJson(json);
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Interface, sauvegarde
    // ------------------------------------------------------------------

    @Override
    protected int machineValueCount() {
        return 7;
    }

    @Override
    protected long machineValue(int index) {
        return switch (index) {
            case 0 -> state;
            case 1 -> model();
            case 2 -> links.size();
            case 3 -> maxLinks();
            case 4 -> model() == AiChips.FOUNDATION ? FE_FOUNDATION : FE_NEURAL;
            case 5 -> interventions;
            default -> supervised;
        };
    }

    @Override
    public String guiTier() {
        return "factory_ai_core";
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        ListTag list = new ListTag();
        log.forEach(s -> list.add(StringTag.valueOf(s)));
        tag.put("log", list);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        if (tag.contains("log")) {
            log.clear();
            for (Tag t : tag.getList("log", Tag.TAG_STRING)) {
                log.addLast(t.getAsString());
            }
        }
    }

    @Override
    public void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        links.forEach(p -> list.add(LongTag.valueOf(p.asLong())));
        tag.put("links", list);
        ListTag said = new ListTag();
        log.forEach(s -> said.add(StringTag.valueOf(s)));
        tag.put("log", said);
        ListTag screen = new ListTag();
        screenLog.forEach(s -> screen.add(StringTag.valueOf(s)));
        tag.put("screenLog", screen);
        tag.putInt("interventions", interventions);
        tag.putBoolean("online", wasOnline);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        links.clear();
        for (Tag t : tag.getList("links", Tag.TAG_LONG)) {
            links.add(BlockPos.of(((LongTag) t).getAsLong()));
        }
        log.clear();
        for (Tag t : tag.getList("log", Tag.TAG_STRING)) {
            log.addLast(t.getAsString());
        }
        screenLog.clear();
        for (Tag t : tag.getList("screenLog", Tag.TAG_STRING)) {
            screenLog.addLast(t.getAsString());
        }
        interventions = tag.getInt("interventions");
        wasOnline = tag.getBoolean("online");
    }

    @Nullable
    public static FactoryAiCoreTile at(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FactoryAiCoreTile t ? t : null;
    }

    /** API Machine (lot 4) : cadre de l'interface standard v2 (inventaire DANS le cadre, onglets a droite) autour du corps propre d'avant. */
    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        List<StandardLayout.Part> out = new java.util.ArrayList<>(StandardLayout.serviceParts(true, new StandardLayout.Part("card", CARD)));
        out.removeIf(p -> p.role().equals("screen"));
        out.add(new StandardLayout.Part("screen", StandardLayout.shortScreen(StandardLayout.WIDTH)));
        out.add(new StandardLayout.Part("setting", StandardLayout.settingRow(StandardLayout.WIDTH)));
        return out;
    }

    @Override
    protected boolean namedValues() {
        return true;
    }

    @Override
    protected void declareValues(UiValues.Builder values) {
        UiValues.Key[] keys = {UiValues.STATE, MODEL, LINKS, MAX_LINKS, UiValues.FE_PER_TICK, INTERVENTIONS, SUPERVISED};
        for (int i = 0; i < keys.length; i++) {
            int index = i;
            values.add(keys[i], () -> machineValue(index));
        }
    }
}

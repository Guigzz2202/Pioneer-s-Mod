package fr.pioneers.tech.computing;

import fr.pioneers.core.Directions;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.data.DigitalNetwork;
import fr.pioneers.core.data.NetworkPlug;
import fr.pioneers.core.registries.PiComputing;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Armoire a relais et automate programmable (0.17.0) : machines liees par le configurateur de liaison (meme dimension, 64 blocs au plus),
 * regles evaluees a intervalle fixe tant que l'energie suffit, sorties redstone par face, commandes envoyees aux machines liees.
 * <p>
 * Interface : {@link ComputingMenu} (editeur de regles). Actions : {@link #UNLINK} + n delie la liaison n ; {@link #RULE} + regle x 64 +
 * champ modifie une regle (champs {@code F_*}).
 */
public class LogicTile extends MachineTile {

    public static final int UNLINK = 100, RULE = 1000, FIELDS = 64;
    public static final int F_ENABLE = 0, F_SOURCE_NEXT = 1, F_SOURCE_PREV = 2, F_KEY_NEXT = 3, F_KEY_PREV = 4, F_CMP = 5, F_THRESHOLD = 6,
          F_SET_CURRENT = 12, F_MODE = 13, F_DELAY = 14, F_ACTION_NEXT = 18, F_ACTION_PREV = 19, F_TARGET_NEXT = 20, F_TARGET_PREV = 21,
          F_LEVEL = 22, F_COMMAND_NEXT = 26, F_COMMAND_PREV = 27, F_CLEAR = 28;
    /** Pas du seuil (champs F_THRESHOLD..+5), du delai (F_DELAY..+3) et du niveau (F_LEVEL..+3). */
    public static final int[] THRESHOLD_STEPS = {-100, -10, -1, 1, 10, 100}, SMALL_STEPS = {-10, -1, 1, 10};
    /** Portee des liaisons (blocs). */
    public static final double RANGE = 64;

    /**
     * 0.20.46 : une machine est joignable a 64 blocs au plus, ou a n'importe quelle distance si les deux touchent le meme reseau
     * numerique (fibre optique).
     */
    /** 1.9 (MAJ RP) : facteur de portee du joueur qui relie (skill Robotique), pose par {@link DataLinkItem} le temps d'une liaison. */
    static double rangeFactor = 1.0;

    public static boolean reachable(@Nullable Level level, BlockPos from, BlockPos target) {
        double r = RANGE * rangeFactor;
        if (target.distSqr(from) <= r * r) {
            return true;
        }
        return level != null && DigitalNetwork.sameNetwork(level, from, target);
    }

    public final LogicKind kind;
    private final LogicRule[] rules;
    private final BlockPos[] links;
    private final int[] outputs = new int[6];
    private final int[] counters = new int[LogicKind.COUNTERS];
    private int clock;
    private boolean powered;
    // instantane des machines liees (serveur : derniere evaluation ; client : recu)
    private final List<List<ProbeValue>> snapshots = new ArrayList<>();
    private final List<Component> names = new ArrayList<>();
    private final List<List<String>> commandKeys = new ArrayList<>();

    public LogicTile(BlockPos pos, BlockState state) {
        this(pos, state, state.getBlock() instanceof LogicBlock block ? block.kind : LogicKind.RELAY);
    }

    private LogicTile(BlockPos pos, BlockState state, LogicKind kind) {
        super(PiComputing.LOGIC_TILE.get(), pos, state, kind.power * 400L, kind.power * 8);
        this.kind = kind;
        rules = new LogicRule[kind.rules];
        for (int i = 0; i < rules.length; i++) {
            rules[i] = new LogicRule();
        }
        links = new BlockPos[kind.links];
        for (int i = 0; i < kind.links; i++) {
            snapshots.add(List.of());
            names.add(Component.empty());
            commandKeys.add(List.of());
        }
    }

    @Override
    protected Set<SideMode> allowedModes() {
        return EnumSet.of(SideMode.NONE, SideMode.INPUT);
    }

    @Override
    protected SideMode initialMode() {
        return SideMode.INPUT;
    }

    // ------------------------------------------------------------------
    // Lecture (interface, ecrans, autres automates)
    // ------------------------------------------------------------------

    public LogicRule rule(int index) {
        return rules[index];
    }

    @Nullable
    public BlockPos link(int index) {
        return links[index];
    }

    public List<ProbeValue> snapshot(int index) {
        return snapshots.get(index);
    }

    public Component linkName(int index) {
        return names.get(index);
    }

    public List<String> commandKeys(int index) {
        return commandKeys.get(index);
    }

    public int output(Direction side) {
        return outputs[side.get3DDataValue()];
    }

    public int counter(int index) {
        return counters[index];
    }

    public boolean powered() {
        return powered;
    }

    /** Grandeurs internes (source = nombre de liaisons) : compteurs, horloge (secondes 0..59). */
    public List<ProbeValue> internal() {
        List<ProbeValue> out = new ArrayList<>();
        for (int i = 0; i < LogicKind.COUNTERS; i++) {
            out.add(new ProbeValue("counter_" + i, "counter", i, counters[i], "", 0, ""));
        }
        long time = level == null ? 0 : level.getGameTime();
        out.add(ProbeValue.of("clock", (time / 20) % 60, "s", 60));
        return out;
    }

    /** Valeurs d'une source (liaison n ou interne). */
    public List<ProbeValue> source(int source) {
        return source >= 0 && source < kind.links ? snapshots.get(source) : kind.advanced ? internal() : List.of();
    }

    public int sourceCount() {
        return kind.links + (kind.advanced ? 1 : 0);
    }

    /** Ce qu'un autre automate ou un ecran lit sur celui-ci. */
    void probe(List<ProbeValue> out) {
        out.add(new ProbeValue("active", "active", -1, powered ? 1 : 0, "", 0, "computing.pioneers.bool."));
        long cap = energy.getCapacity();
        if (cap > 0) {
            out.add(ProbeValue.of("energy", 100.0 * energy.getStored() / cap, "%", 100));
        }
        for (Direction d : Directions.ALL) {
            out.add(new ProbeValue("output_" + d.getName(), "output_" + d.getName(), -1, outputs[d.get3DDataValue()], "", 15, ""));
        }
        if (kind.advanced) {
            for (int i = 0; i < LogicKind.COUNTERS; i++) {
                out.add(new ProbeValue("counter_" + i, "counter", i, counters[i], "", 0, ""));
            }
        }
    }

    // ------------------------------------------------------------------
    // Liaisons
    // ------------------------------------------------------------------

    /** Lie une machine (premiere place libre) ; faux si deja liee, trop loin ou plus de place. */
    public boolean addLink(BlockPos target) {
        if (target.equals(worldPosition) || !reachable(level, worldPosition, target)) {
            return false;
        }
        for (BlockPos l : links) {
            if (target.equals(l)) {
                return false;
            }
        }
        for (int i = 0; i < links.length; i++) {
            if (links[i] == null) {
                links[i] = target.immutable();
                evaluate(false);
                setChanged();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Fonctionnement
    // ------------------------------------------------------------------

    @Override
    protected void tick() {
        boolean was = powered;
        powered = canWork() && use(kind.power);
        if (++clock >= kind.period || was != powered) {
            clock = 0;
            evaluate(powered);
        }
        markWorking(powered);
    }

    private void evaluate(boolean run) {
        for (int i = 0; i < links.length; i++) {
            BlockEntity tile = links[i] == null ? null : Probes.at(level, links[i]);
            snapshots.set(i, links[i] == null ? List.of() : Probes.read(tile));
            names.set(i, links[i] == null ? Component.empty() : Probes.name(tile));
            commandKeys.set(i, Probes.commands(tile).stream().map(Probes.Command::key).toList());
        }
        int[] next = new int[6];
        boolean unlatch = false;
        for (LogicRule r : rules) {
            if (!r.enabled || !run) {
                r.active = false;
                r.last = false;
                r.held = 0;
                continue;
            }
            ProbeValue v = ProbeValue.find(source(r.source), r.key);
            boolean cond = v != null && r.test(v.value());
            if (kind.advanced && r.delay > 0) {
                r.held = cond ? r.held + kind.period : 0;
                cond = r.held >= r.delay * 20;
            }
            boolean rising = cond && !r.last;
            r.last = cond;
            int mode = kind.advanced ? r.mode : LogicRule.DIRECT;
            boolean fire = switch (mode) {
                case LogicRule.PULSE -> rising;
                case LogicRule.LATCH -> r.latched |= cond;
                default -> cond;
            };
            boolean fireRise = fire && !r.active;
            r.active = fire;
            if (!fire) {
                continue;
            }
            switch (r.action) {
                case LogicRule.REDSTONE -> {
                    int face = Math.floorMod(r.target, 6);
                    next[face] = Math.max(next[face], Math.max(0, Math.min(15, r.level)));
                }
                case LogicRule.COMMAND -> {
                    if (r.target >= 0 && r.target < links.length && links[r.target] != null) {
                        Probes.command(Probes.at(level, links[r.target]), r.command, r.level);
                    }
                }
                case LogicRule.COUNT -> {
                    if (fireRise && kind.advanced) {
                        counters[Math.floorMod(r.target, LogicKind.COUNTERS)]++;
                    }
                }
                case LogicRule.COUNT_RESET -> {
                    if (fireRise && kind.advanced) {
                        counters[Math.floorMod(r.target, LogicKind.COUNTERS)] = 0;
                    }
                }
                default -> unlatch |= fireRise;
            }
        }
        if (unlatch) {
            for (LogicRule r : rules) {
                if (r.action != LogicRule.UNLATCH) {
                    r.latched = false;
                }
            }
        }
        if (!Arrays.equals(next, outputs)) {
            System.arraycopy(next, 0, outputs, 0, 6);
            if (level != null) {
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            }
        }
        //1.0.5-beta (lot 29) : envoi (etat client de 2,4 ko) et sauvegarde seulement si l'etat a change (avant : a chaque evaluation, 2 a 4
        //fois par seconde, meme quand rien ne bougeait chez les machines liees)
        if (watch.changed(this)) {
            markSync();
            setChanged();
        }
    }

    /** 1.0.5-beta (lot 29) : garde des envois periodiques (etat entier compare). */
    private final fr.pioneers.core.block.StateWatch watch = new fr.pioneers.core.block.StateWatch();

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return new ComputingMenu(id, inventory, this);
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        if (action >= UNLINK && action < UNLINK + links.length) {
            links[action - UNLINK] = null;
            for (LogicRule r : rules) {
                if (r.source == action - UNLINK) {
                    r.key = "";
                }
            }
        } else if (action >= RULE && action < RULE + rules.length * FIELDS) {
            edit(rules[(action - RULE) / FIELDS], (action - RULE) % FIELDS);
        } else {
            return;
        }
        evaluate(powered);
    }

    private void edit(LogicRule r, int field) {
        if (field >= F_THRESHOLD && field < F_THRESHOLD + 6) {
            r.threshold += THRESHOLD_STEPS[field - F_THRESHOLD];
            return;
        }
        if (field >= F_DELAY && field < F_DELAY + 4) {
            r.delay = Math.max(0, Math.min(3600, r.delay + SMALL_STEPS[field - F_DELAY]));
            return;
        }
        if (field >= F_LEVEL && field < F_LEVEL + 4) {
            r.level = clampLevel(r, r.level + SMALL_STEPS[field - F_LEVEL]);
            return;
        }
        switch (field) {
            case F_ENABLE -> r.enabled = !r.enabled;
            case F_SOURCE_NEXT, F_SOURCE_PREV -> {
                r.source = Math.floorMod(r.source + (field == F_SOURCE_NEXT ? 1 : -1), sourceCount());
                List<ProbeValue> values = source(r.source);
                r.key = values.isEmpty() ? "" : values.get(0).key();
            }
            case F_KEY_NEXT, F_KEY_PREV -> r.key = cycle(source(r.source).stream().map(ProbeValue::key).toList(), r.key, field == F_KEY_NEXT);
            case F_CMP -> r.comparator = (r.comparator + 1) % LogicRule.COMPARATORS;
            case F_SET_CURRENT -> {
                ProbeValue v = ProbeValue.find(source(r.source), r.key);
                if (v != null) {
                    r.threshold = Math.round(v.value() * 10) / 10.0;
                }
            }
            case F_MODE -> r.mode = kind.advanced ? (r.mode + 1) % LogicRule.MODES : LogicRule.DIRECT;
            case F_ACTION_NEXT, F_ACTION_PREV -> {
                int count = kind.advanced ? LogicRule.ACTIONS : 2;
                r.action = Math.floorMod(r.action + (field == F_ACTION_NEXT ? 1 : -1), count);
                r.target = 0;
                r.level = r.action == LogicRule.REDSTONE ? 15 : 0;
                r.command = r.action == LogicRule.COMMAND ? first(commandKeys.get(0)) : "";
            }
            case F_TARGET_NEXT, F_TARGET_PREV -> {
                int count = switch (r.action) {
                    case LogicRule.REDSTONE -> 6;
                    case LogicRule.COMMAND -> links.length;
                    case LogicRule.COUNT, LogicRule.COUNT_RESET -> LogicKind.COUNTERS;
                    default -> 1;
                };
                r.target = Math.floorMod(r.target + (field == F_TARGET_NEXT ? 1 : -1), count);
                if (r.action == LogicRule.COMMAND) {
                    r.command = first(commandKeys.get(r.target));
                }
            }
            case F_COMMAND_NEXT, F_COMMAND_PREV -> {
                if (r.action == LogicRule.COMMAND && r.target < links.length) {
                    r.command = cycle(commandKeys.get(r.target), r.command, field == F_COMMAND_NEXT);
                    r.level = clampLevel(r, r.level);
                }
            }
            case F_CLEAR -> r.clear();
            default -> {
            }
        }
    }

    private int clampLevel(LogicRule r, int value) {
        if (r.action == LogicRule.REDSTONE) {
            return Math.max(0, Math.min(15, value));
        }
        if (r.action == LogicRule.COMMAND) {
            for (Probes.Command c : Probes.REACTOR_COMMANDS) {
                if (c.key().equals(r.command)) {
                    return Math.max(c.min(), Math.min(c.max(), value));
                }
            }
        }
        return Math.max(0, value);
    }

    private static String first(List<String> keys) {
        return keys.isEmpty() ? "" : keys.get(0);
    }

    private static String cycle(List<String> keys, String current, boolean forward) {
        if (keys.isEmpty()) {
            return "";
        }
        int i = keys.indexOf(current);
        return keys.get(Math.floorMod((i < 0 ? (forward ? -1 : 0) : i) + (forward ? 1 : -1), keys.size()));
    }

    @Override
    public int guiHeight() {
        return 206;
    }

    // ------------------------------------------------------------------
    // Sauvegarde et synchronisation
    // ------------------------------------------------------------------

    private ListTag saveRules() {
        ListTag list = new ListTag();
        for (LogicRule r : rules) {
            list.add(r.save());
        }
        return list;
    }

    private void loadRules(CompoundTag tag) {
        ListTag list = tag.getList("rules", Tag.TAG_COMPOUND);
        for (int i = 0; i < rules.length; i++) {
            if (i < list.size()) {
                rules[i].load(list.getCompound(i));
            } else {
                rules[i].clear();
            }
        }
    }

    private ListTag saveLinks() {
        ListTag list = new ListTag();
        for (BlockPos l : links) {
            list.add(l == null ? new CompoundTag() : NbtUtils.writeBlockPos(l));
        }
        return list;
    }

    private void loadLinks(CompoundTag tag) {
        ListTag list = tag.getList("links", Tag.TAG_COMPOUND);
        for (int i = 0; i < links.length; i++) {
            CompoundTag c = i < list.size() ? list.getCompound(i) : new CompoundTag();
            links[i] = c.contains("X") ? NbtUtils.readBlockPos(c) : null;
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("rules", saveRules());
        tag.put("links", saveLinks());
        tag.putIntArray("out", outputs);
        tag.putIntArray("counters", counters);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        loadRules(tag);
        loadLinks(tag);
        int[] out = tag.getIntArray("out");
        System.arraycopy(out, 0, outputs, 0, Math.min(6, out.length));
        int[] c = tag.getIntArray("counters");
        System.arraycopy(c, 0, counters, 0, Math.min(counters.length, c.length));
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        tag.put("rules", saveRules());
        tag.put("links", saveLinks());
        tag.putIntArray("out", outputs);
        tag.putIntArray("counters", counters);
        tag.putBoolean("powered", powered);
        tag.putLong("energy", energy.getStored());
        ListTag snaps = new ListTag();
        for (int i = 0; i < links.length; i++) {
            CompoundTag s = new CompoundTag();
            s.put("values", ProbeValue.saveAll(snapshots.get(i)));
            s.putString("name", Component.Serializer.toJson(names.get(i)));
            ListTag cmds = new ListTag();
            commandKeys.get(i).forEach(k -> cmds.add(StringTag.valueOf(k)));
            s.put("cmds", cmds);
            snaps.add(s);
        }
        tag.put("snaps", snaps);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        loadRules(tag);
        loadLinks(tag);
        int[] out = tag.getIntArray("out");
        System.arraycopy(out, 0, outputs, 0, Math.min(6, out.length));
        int[] c = tag.getIntArray("counters");
        System.arraycopy(c, 0, counters, 0, Math.min(counters.length, c.length));
        powered = tag.getBoolean("powered");
        clientEnergy = tag.getLong("energy");
        ListTag snaps = tag.getList("snaps", Tag.TAG_COMPOUND);
        for (int i = 0; i < links.length && i < snaps.size(); i++) {
            CompoundTag s = snaps.getCompound(i);
            snapshots.set(i, ProbeValue.loadAll(s, "values"));
            Component name = Component.Serializer.fromJson(s.getString("name"));
            names.set(i, name == null ? Component.empty() : name);
            ListTag cmds = s.getList("cmds", Tag.TAG_STRING);
            List<String> keys = new ArrayList<>();
            for (int k = 0; k < cmds.size(); k++) {
                keys.add(cmds.getString(k));
            }
            commandKeys.set(i, keys);
        }
    }

    private long clientEnergy;

    /** Energie stockee (client : derniere recue). */
    public long energyStored() {
        return level != null && level.isClientSide ? clientEnergy : energy.getStored();
    }

    public long energyCapacity() {
        return energy.getCapacity();
    }

    /** 0.20.46 : la fibre optique se branche sur ce bloc (il fait partie du reseau numerique ; rien n'y entre). */
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap,
          @Nullable Direction side) {
        LazyOptional<T> plug = NetworkPlug.of(cap);
        return plug != null ? plug : super.getCapability(cap, side);
    }
}

package fr.pioneers.tech.computing;

import fr.pioneers.tech.computing.script.Compiler;
import fr.pioneers.tech.computing.script.ScriptError;
import fr.pioneers.tech.computing.script.Vm;
import fr.pioneers.core.Directions;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.data.NetworkPlug;
import fr.pioneers.core.network.PacketComputer;
import fr.pioneers.tech.computing.PiComputing;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Ordinateur (0.17.1) : execute un programme PioScript ({@link Vm}) par tranches de {@link #BUDGET} instructions par tick tant que
 * l'energie suffit (128 FE/t en marche). Disque interne {@code c:} de 32 Ko, deux lecteurs de disquette {@code a:} et {@code b:},
 * 4 machines liees (configurateur de liaison), 6 sorties redstone, console (200 lignes), texte envoye aux ecrans de supervision lies.
 * Le programme en cours est relance au chargement du monde (l'etat d'execution n'est pas sauve).
 */
public class ComputerTile extends MachineTile implements Vm.Host {

    public static final int POWER = 128, BUDGET = 2_000, LINKS = 4, CONSOLE = 200, LINE = 160, SCREEN_ROWS = 24, SCREEN_COLS = 64;
    public static final int STOP = 100, EJECT_A = 101, EJECT_B = 102, CLEAR = 103, UNLINK = 110;
    /** Etat affiche : arrete, en marche, en attente (sleep), attend une saisie, sans energie, en pause (redstone). */
    public static final int IDLE = 0, RUNNING = 1, SLEEPING = 2, INPUT = 3, NO_ENERGY = 4, PAUSED = 5;

    /** Ligne de console : sortie du programme, message systeme (cle de langue), erreur (cle + ligne), saisie. */
    public record ConsoleLine(int kind, String text, String[] args) {
        public static final int OUT = 0, INFO = 1, ERROR = 2, ECHO = 3;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putByte("k", (byte) kind);
            t.putString("t", text);
            if (args.length > 0) {
                ListTag l = new ListTag();
                for (String a : args) {
                    l.add(StringTag.valueOf(a));
                }
                t.put("a", l);
            }
            return t;
        }

        static ConsoleLine load(CompoundTag t) {
            ListTag l = t.getList("a", Tag.TAG_STRING);
            String[] args = new String[l.size()];
            for (int i = 0; i < args.length; i++) {
                args[i] = l.getString(i);
            }
            return new ConsoleLine(t.getByte("k"), t.getString("t"), args);
        }

        public Component component() {
            return switch (kind) {
                case INFO, ERROR -> Component.translatable(text, (Object[]) args);
                case ECHO -> Component.literal("> " + text);
                default -> Component.literal(text);
            };
        }
    }

    private final ItemStack[] drives = {ItemStack.EMPTY, ItemStack.EMPTY};
    private CompoundTag internal = new CompoundTag();
    private final BlockPos[] links = new BlockPos[LINKS];
    private final int[] outputs = new int[6];
    private final List<ConsoleLine> console = new ArrayList<>();
    private final List<String> screenText = new ArrayList<>();
    private boolean screenDirty;
    private String program = "";
    private boolean autorun;
    @Nullable
    private Vm vm;
    private int status;
    private boolean relaunch = true;
    // client
    private String prompt = "";
    private final List<List<String>> clientFiles = new ArrayList<>(List.of(List.of(), List.of(), List.of()));
    private final int[] clientUsed = new int[3];
    private final boolean[] clientDrive = new boolean[2];
    private final List<Component> clientLinks = new ArrayList<>(List.of(Component.empty(), Component.empty(), Component.empty(), Component.empty()));
    private long clientEnergy;
    /** 0.20.46 (audit) : console, fichiers et liaisons a renvoyer aux joueurs qui ont l'interface ouverte (et a eux seuls). */
    private boolean viewersDirty;

    public ComputerTile(BlockPos pos, BlockState state) {
        super(PiComputing.COMPUTER_TILE.get(), pos, state, POWER * 800L, POWER * 8);
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
    // Hote de la machine virtuelle
    // ------------------------------------------------------------------

    @Override
    public void print(String text) {
        for (String line : text.split("\n", -1)) {
            addLine(new ConsoleLine(ConsoleLine.OUT, line.length() > LINE ? line.substring(0, LINE) : line, new String[0]));
        }
    }

    /** 0.20.41 : aide de PioScript : ligne traduite chez le joueur. */
    @Override
    public void doc(String key, String... args) {
        String[] a = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            a[i] = args[i].length() > LINE ? args[i].substring(0, LINE) : args[i];
        }
        addLine(new ConsoleLine(ConsoleLine.INFO, key, a));
    }

    @Override
    public long now() {
        return level == null ? 0 : level.getGameTime();
    }

    private void addLine(ConsoleLine line) {
        console.add(line);
        while (console.size() > CONSOLE) {
            console.remove(0);
        }
        markSync();
    }

    private void info(String key, Object... args) {
        String[] a = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            a[i] = String.valueOf(args[i]);
        }
        addLine(new ConsoleLine(ConsoleLine.INFO, key, a));
    }

    private void error(ScriptError e) {
        String[] a = new String[e.args.length + 1];
        a[0] = String.valueOf(e.line);
        System.arraycopy(e.args, 0, a, 1, e.args.length);
        addLine(new ConsoleLine(ConsoleLine.ERROR, "computing.pioneers.script.error." + e.code, a));
    }

    // ------------------------------------------------------------------
    // Fichiers
    // ------------------------------------------------------------------

    /** Lecteur d'un chemin ({@code a:}, {@code b:}, sinon {@code c:}) : 0 = a, 1 = b, 2 = c. */
    public static int drive(String path) {
        if (path.length() > 2 && path.charAt(1) == ':') {
            return switch (Character.toLowerCase(path.charAt(0))) {
                case 'a' -> 0;
                case 'b' -> 1;
                case 'c' -> 2;
                default -> -1;
            };
        }
        return 2;
    }

    public static String fileName(String path) {
        return path.length() > 2 && path.charAt(1) == ':' ? path.substring(2) : path;
    }

    @Nullable
    CompoundTag files(int drive) {
        if (drive == 2) {
            return internal;
        }
        if (drive < 0 || drive > 1 || drives[drive].isEmpty()) {
            return null;
        }
        return DataDiskItem.files(drives[drive]);
    }

    int capacity(int drive) {
        return drive == 2 ? DiskFiles.INTERNAL_CAPACITY : DiskFiles.DISK_CAPACITY;
    }

    @Nullable
    public String readFile(String path) {
        CompoundTag f = files(drive(path));
        return f == null ? null : DiskFiles.read(f, fileName(path));
    }

    public boolean writeFile(String path, String text) {
        int d = drive(path);
        CompoundTag f = files(d);
        boolean ok = f != null && DiskFiles.write(f, fileName(path), text, capacity(d));
        if (ok) {
            setChanged();
            markSync();
        }
        return ok;
    }

    public boolean deleteFile(String path) {
        CompoundTag f = files(drive(path));
        boolean ok = f != null && DiskFiles.delete(f, fileName(path));
        if (ok) {
            setChanged();
            markSync();
        }
        return ok;
    }

    public List<String> listFiles(int drive) {
        CompoundTag f = files(drive);
        return f == null ? List.of() : DiskFiles.list(f);
    }

    public int freeSpace(int drive) {
        CompoundTag f = files(drive);
        return f == null ? 0 : capacity(drive) - DiskFiles.used(f);
    }

    /** Insere une disquette dans le premier lecteur libre ; renvoie le reste. */
    public ItemStack insertDisk(ItemStack stack) {
        for (int i = 0; i < 2; i++) {
            if (drives[i].isEmpty()) {
                //0.18.0 : les ordinateurs de vol vierges entrent par pile entiere (flash en serie)
                boolean flight = stack.getItem() instanceof FlightComputerItem f && !f.isProgrammed();
                drives[i] = stack.split(flight ? stack.getCount() : 1);
                setChanged();
                syncNow();
                return stack;
            }
        }
        return stack;
    }

    /** 0.18.0 : objet range dans un lecteur (0 = a, 1 = b). */
    public ItemStack driveStack(int drive) {
        return drive < 0 || drive > 1 ? ItemStack.EMPTY : drives[drive];
    }

    /** 0.18.0 : remplace l'objet d'un lecteur (ordinateur de vol programme). */
    public void replaceDrive(int drive, ItemStack stack) {
        drives[drive] = stack;
        setChanged();
        syncNow();
    }

    /** Nom du programme en cours (fichier lance). */
    public String programName() {
        return program;
    }

    public void ejectDisk(int drive, @Nullable Player player) {
        if (drive < 0 || drive > 1 || drives[drive].isEmpty()) {
            return;
        }
        ItemStack disk = drives[drive];
        drives[drive] = ItemStack.EMPTY;
        if (player != null && player.getInventory().add(disk)) {
            disk = ItemStack.EMPTY;
        }
        if (!disk.isEmpty() && level != null) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, disk);
        }
        setChanged();
        syncNow();
    }

    /** Disquettes a lacher quand le bloc est casse. */
    public List<ItemStack> drops() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack d : drives) {
            if (!d.isEmpty()) {
                out.add(d.copy());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Liaisons, redstone, ecran
    // ------------------------------------------------------------------

    public boolean addLink(BlockPos target) {
        if (target.equals(worldPosition) || !LogicTile.reachable(level, worldPosition, target)) {
            return false;
        }
        for (BlockPos l : links) {
            if (target.equals(l)) {
                return false;
            }
        }
        for (int i = 0; i < LINKS; i++) {
            if (links[i] == null) {
                links[i] = target.immutable();
                setChanged();
                markSync();
                return true;
            }
        }
        return false;
    }

    @Nullable
    public BlockPos link(int index) {
        return index >= 0 && index < LINKS ? links[index] : null;
    }

    public int output(Direction side) {
        return outputs[side.get3DDataValue()];
    }

    void setOutput(Direction side, int value) {
        int v = Math.max(0, Math.min(15, value));
        if (outputs[side.get3DDataValue()] != v) {
            outputs[side.get3DDataValue()] = v;
            if (level != null) {
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            }
            setChanged();
        }
    }

    int input(Direction side) {
        return level == null ? 0 : level.getSignal(worldPosition.relative(side), side);
    }

    List<String> screenText() {
        return screenText;
    }

    void screenChanged() {
        screenDirty = true;
    }

    private void pushScreens() {
        screenDirty = false;
        for (BlockPos l : links) {
            if (l != null && Probes.at(level, l) instanceof ScreenTile s) {
                BlockPos master = ScreenBlock.master(level, l, s.facing());
                if (level.getBlockEntity(master) instanceof ScreenTile m) {
                    m.showText(screenText);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Execution
    // ------------------------------------------------------------------

    public String program() {
        return program;
    }

    public int status() {
        return status;
    }

    /** Compile et lance un fichier. */
    public void launch(String path) {
        stopProgram(false);
        String source = readFile(path);
        program = path;
        if (source == null) {
            info("computing.pioneers.script.no_file", path);
            autorun = false;
            return;
        }
        try {
            vm = new Vm(Compiler.compile(source), this);
            ComputerLib.install(vm, this);
            autorun = true;
            info("computing.pioneers.script.started", path);
        } catch (ScriptError e) {
            error(e);
            vm = null;
            autorun = false;
        }
        setChanged();
        markSync();
    }

    public void stopProgram(boolean message) {
        if (vm != null) {
            vm.stop();
            vm = null;
            if (message) {
                info("computing.pioneers.script.stopped");
            }
        }
        autorun = false;
        status = IDLE;
        setChanged();
        markSync();
    }

    public void consoleInput(String line) {
        addLine(new ConsoleLine(ConsoleLine.ECHO, line, new String[0]));
        if (vm != null && vm.state() == Vm.State.INPUT) {
            vm.provideInput(line);
        }
    }

    @Override
    protected void tick() {
        if (relaunch) {
            relaunch = false;
            if (autorun && !program.isEmpty()) {
                info("computing.pioneers.script.restart");
                launch(program);
            }
        }
        int was = status;
        boolean working = false;
        if (vm == null) {
            status = IDLE;
        } else if (!canWork()) {
            status = PAUSED;
        } else if (!use(POWER)) {
            status = NO_ENERGY;
        } else {
            working = true;
            vm.run(BUDGET);
            switch (vm.state()) {
                case DONE -> {
                    info("computing.pioneers.script.finished", program);
                    vm = null;
                    autorun = false;
                    status = IDLE;
                }
                case ERROR -> {
                    error(vm.error());
                    vm = null;
                    autorun = false;
                    status = IDLE;
                }
                case SLEEPING -> status = SLEEPING;
                case INPUT -> status = INPUT;
                default -> status = RUNNING;
            }
        }
        if (screenDirty) {
            pushScreens();
        }
        if (was != status) {
            markSync();
            setChanged();
        }
        markWorking(working);
        if (viewersDirty && level != null && level.getGameTime() % 10 == 0) {
            viewersDirty = false;
            pushViewers();
        }
    }

    /** Etat visible change : mise a jour du bloc (etat leger) et, au plus toutes les 10 ticks, des interfaces ouvertes. */
    @Override
    protected void markSync() {
        super.markSync();
        viewersDirty = true;
    }

    @Override
    protected void syncNow() {
        super.syncNow();
        viewersDirty = false;
        pushViewers();
    }

    /** Console, fichiers et liaisons vers les joueurs qui ont l'interface de cet ordinateur ouverte. */
    private void pushViewers() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        CompoundTag tag = null;
        for (ServerPlayer player : server.players()) {
            if (player.containerMenu instanceof ComputerMenu menu && menu.getTile() == this) {
                if (tag == null) {
                    tag = new CompoundTag();
                    writeViewer(tag);
                }
                PacketComputer.sendState(player, worldPosition, tag);
            }
        }
    }

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        if (player instanceof ServerPlayer sp) {
            //0.20.46 (audit) : la console et les listes arrivent par un paquet a ce seul joueur (l'etat leger avec la mise a jour du bloc)
            syncNow();
            CompoundTag tag = new CompoundTag();
            writeViewer(tag);
            PacketComputer.sendState(sp, worldPosition, tag);
        }
        return new ComputerMenu(id, inventory, this);
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        switch (action) {
            case STOP -> stopProgram(true);
            case EJECT_A -> ejectDisk(0, player);
            case EJECT_B -> ejectDisk(1, player);
            case CLEAR -> {
                console.clear();
                markSync();
            }
            default -> {
                if (action >= UNLINK && action < UNLINK + LINKS) {
                    links[action - UNLINK] = null;
                    setChanged();
                    markSync();
                }
            }
        }
    }

    /** Paquet de l'interface (texte) : sauver, ouvrir, supprimer, lancer, saisie. */
    public void handlePacket(ServerPlayer player, int kind, String path, String text) {
        switch (kind) {
            case PacketComputer.SAVE -> {
                if (!writeFile(path, text)) {
                    info("computing.pioneers.script.save_failed", path);
                } else {
                    info("computing.pioneers.script.saved", path, text.length());
                }
            }
            case PacketComputer.OPEN -> {
                String content = readFile(path);
                if (content != null) {
                    PacketComputer.sendContent(player, worldPosition, path, content);
                }
            }
            case PacketComputer.DELETE -> {
                if (deleteFile(path)) {
                    info("computing.pioneers.script.deleted", path);
                }
            }
            case PacketComputer.RUN -> launch(path);
            case PacketComputer.INPUT -> consoleInput(text);
            default -> {
            }
        }
        syncNow();
    }

    // ------------------------------------------------------------------
    // Lecture cote client
    // ------------------------------------------------------------------

    public List<ConsoleLine> console() {
        return console;
    }

    public String prompt() {
        return prompt;
    }

    public List<String> clientFiles(int drive) {
        return clientFiles.get(drive);
    }

    public int clientUsed(int drive) {
        return clientUsed[drive];
    }

    public boolean hasDisk(int drive) {
        return level != null && level.isClientSide ? clientDrive[drive] : !drives[drive].isEmpty();
    }

    public Component linkName(int index) {
        return clientLinks.get(index);
    }

    public long energyStored() {
        return level != null && level.isClientSide ? clientEnergy : energy.getStored();
    }

    public long energyCapacity() {
        return energy.getCapacity();
    }

    /** Ce qu'un automate, un ecran ou un autre ordinateur lit sur celui-ci. */
    void probe(List<ProbeValue> out) {
        out.add(new ProbeValue("running", "running", -1, vm != null ? 1 : 0, "", 0, "computing.pioneers.bool."));
        long cap = energy.getCapacity();
        if (cap > 0) {
            out.add(ProbeValue.of("energy", 100.0 * energy.getStored() / cap, "%", 100));
        }
        for (Direction d : Directions.ALL) {
            out.add(new ProbeValue("output_" + d.getName(), "output_" + d.getName(), -1, outputs[d.get3DDataValue()], "", 15, ""));
        }
    }

    // ------------------------------------------------------------------
    // Sauvegarde et synchronisation
    // ------------------------------------------------------------------

    private ListTag saveLinks() {
        ListTag list = new ListTag();
        for (BlockPos l : links) {
            list.add(l == null ? new CompoundTag() : NbtUtils.writeBlockPos(l));
        }
        return list;
    }

    private void loadLinks(CompoundTag tag) {
        ListTag list = tag.getList("links", Tag.TAG_COMPOUND);
        for (int i = 0; i < LINKS; i++) {
            CompoundTag c = i < list.size() ? list.getCompound(i) : new CompoundTag();
            links[i] = c.contains("X") ? NbtUtils.readBlockPos(c) : null;
        }
    }

    private ListTag saveConsole(int last) {
        ListTag list = new ListTag();
        for (int i = Math.max(0, console.size() - last); i < console.size(); i++) {
            list.add(console.get(i).save());
        }
        return list;
    }

    private void loadConsole(CompoundTag tag) {
        console.clear();
        ListTag list = tag.getList("console", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            console.add(ConsoleLine.load(list.getCompound(i)));
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("internal", internal.copy());
        for (int i = 0; i < 2; i++) {
            tag.put("drive" + i, drives[i].save(new CompoundTag()));
        }
        tag.put("links", saveLinks());
        tag.putIntArray("out", outputs);
        tag.put("console", saveConsole(CONSOLE));
        tag.putString("program", program);
        tag.putBoolean("autorun", autorun);
        ListTag screen = new ListTag();
        screenText.forEach(s -> screen.add(StringTag.valueOf(s)));
        tag.put("screen", screen);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        internal = tag.getCompound("internal").copy();
        for (int i = 0; i < 2; i++) {
            drives[i] = ItemStack.of(tag.getCompound("drive" + i));
        }
        loadLinks(tag);
        int[] out = tag.getIntArray("out");
        Arrays.fill(outputs, 0);
        System.arraycopy(out, 0, outputs, 0, Math.min(6, out.length));
        loadConsole(tag);
        program = tag.getString("program");
        autorun = tag.getBoolean("autorun");
        screenText.clear();
        ListTag screen = tag.getList("screen", Tag.TAG_STRING);
        for (int i = 0; i < screen.size(); i++) {
            screenText.add(screen.getString(i));
        }
    }

    /**
     * Etat leger envoye a tous les joueurs proches avec la mise a jour du bloc. 0.20.46 (audit) : la console (100 lignes), les listes de
     * fichiers et les noms des liaisons ne partent plus qu'aux joueurs qui ont l'interface ouverte ({@link #writeViewer}).
     */
    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        tag.putString("program", program);
        tag.putInt("status", status);
        tag.putString("prompt", vm != null && vm.state() == Vm.State.INPUT ? vm.prompt() : "");
        tag.putLong("energy", energy.getStored());
        tag.putIntArray("out", outputs);
        tag.putBoolean("diskA", !drives[0].isEmpty());
        tag.putBoolean("diskB", !drives[1].isEmpty());
    }

    /** Console, fichiers et liaisons (interface ouverte). */
    void writeViewer(CompoundTag tag) {
        tag.put("console", saveConsole(100));
        for (int d = 0; d < 3; d++) {
            ListTag names = new ListTag();
            listFiles(d).forEach(n -> names.add(StringTag.valueOf(n)));
            tag.put("files" + d, names);
            CompoundTag f = files(d);
            tag.putInt("used" + d, f == null ? 0 : DiskFiles.used(f));
        }
        ListTag names = new ListTag();
        for (BlockPos l : links) {
            names.add(StringTag.valueOf(l == null ? "" : Component.Serializer.toJson(Probes.name(Probes.at(level, l)))));
        }
        tag.put("linkNames", names);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        program = tag.getString("program");
        status = tag.getInt("status");
        prompt = tag.getString("prompt");
        clientEnergy = tag.getLong("energy");
        int[] out = tag.getIntArray("out");
        System.arraycopy(out, 0, outputs, 0, Math.min(6, out.length));
        clientDrive[0] = tag.getBoolean("diskA");
        clientDrive[1] = tag.getBoolean("diskB");
    }

    /** Client : console, fichiers et liaisons recus (interface ouverte). */
    public void readViewer(CompoundTag tag) {
        loadConsole(tag);
        for (int d = 0; d < 3; d++) {
            ListTag names = tag.getList("files" + d, Tag.TAG_STRING);
            List<String> l = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                l.add(names.getString(i));
            }
            clientFiles.set(d, l);
            clientUsed[d] = tag.getInt("used" + d);
        }
        ListTag ln = tag.getList("linkNames", Tag.TAG_STRING);
        for (int i = 0; i < LINKS && i < ln.size(); i++) {
            String json = ln.getString(i);
            Component c = json.isEmpty() ? null : Component.Serializer.fromJson(json);
            clientLinks.set(i, c == null ? Component.empty() : c);
        }
    }

    /** Le client sait-il si une liaison existe (nom non vide) ? */
    public boolean clientLinked(int index) {
        return !clientLinks.get(index).getString().isEmpty();
    }

    @Nullable
    public BlockEntity linked(int index) {
        BlockPos p = link(index);
        return p == null ? null : Probes.at(level, p);
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

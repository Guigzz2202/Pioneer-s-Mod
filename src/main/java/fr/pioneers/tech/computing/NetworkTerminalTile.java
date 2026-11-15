package fr.pioneers.tech.computing;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.tech.compute.AiLabTile;
import fr.pioneers.tech.compute.FactoryAiCoreTile;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.data.DigitalNetwork;
import fr.pioneers.core.data.NetworkPlug;
import fr.pioneers.core.data.PiDataTypes;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.core.registries.PiComputing;
import fr.pioneers.space.research.ProbeDestination;
import fr.pioneers.space.research.ResearchNode;
import fr.pioneers.space.research.ResearchState;
import fr.pioneers.space.tile.TileEntityAnalyzer;
import fr.pioneers.space.tile.TileEntityBiomeAnalyzer;
import fr.pioneers.space.tile.TileEntityDataAntenna;
import fr.pioneers.space.tile.TileEntityDataBank;
import fr.pioneers.space.tile.TileEntityResearchTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.46 : terminal de réseau. Posé contre une fibre optique, il montre tout le réseau numérique : données en stock par type (banques
 * et fibre), calcul (offre et demande de GOPS / QOPS), machines branchées et leur rôle, recherche en cours et ce qui lui manque, et des
 * conseils (« que faire ensuite ? »). Il ne consomme rien. Lu toutes les secondes, envoyé au client par la synchro du bloc.
 */
public class NetworkTerminalTile extends MachineTile {

    /** Rôles des machines branchées (couleurs et libellés côté client). */
    public static final int OTHER = 0, BANK = 1, LAB = 2, PRODUCER = 3, COMPUTE = 4, AI = 5, SUPERVISION = 6, MULTIBLOCK = 7;
    /** Conseils (clés {@code computing.pioneers.terminal.tip.<n>}). */
    public static final int TIP_NOT_CONNECTED = 0, TIP_NO_BANK = 1, TIP_NO_COMPUTE = 2, TIP_COMPUTE_SHORT = 3, TIP_RESEARCH_DATA = 4,
          TIP_NO_LAB = 5, TIP_BANKS_FULL = 6, TIP_NO_PRODUCER = 7, TIP_ALL_GOOD = 8, TIP_NO_RESEARCH = 9;
    public static final int MAX_TYPES = 24, MAX_MACHINES = 48;

    // Vue (serveur : remplie par la mesure ; client : lue dans la synchro)
    public final List<Entry> stock = new ArrayList<>();
    public final List<Machine> machines = new ArrayList<>();
    public final List<Need> needs = new ArrayList<>();
    public final List<Integer> tips = new ArrayList<>();
    public long bankCapacity, stored, cables;
    public double supplyGops, supplyQops, demandGops, demandQops;
    public int banks, grids;
    public String research = "";

    public record Entry(String key, int color, long amount) {
    }

    public record Machine(String name, int role, String detail, int x, int y, int z) {
    }

    /** Besoin de la recherche en cours : type, Mo par unité, Mo en stock sur le réseau. */
    public record Need(String key, int color, long perUnit, long inStock) {
    }

    public NetworkTerminalTile(BlockPos pos, BlockState state) {
        super(PiComputing.NETWORK_TERMINAL_TILE.get(), pos, state, 0, 0);
    }

    @Override
    public int guiWidth() {
        return 236;
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public String guiTier() {
        return "steel";
    }

    @Override
    protected void tick() {
        if (!(level instanceof ServerLevel server) || server.getGameTime() % 20 != Math.floorMod(worldPosition.asLong(), 20)) {
            return;
        }
        measure(server);
        markWorking(grids > 0);
        markSync();
    }

    private void measure(ServerLevel server) {
        DigitalNetwork.Snapshot s = DigitalNetwork.scan(server, worldPosition);
        grids = s.grids;
        cables = s.cables;
        banks = s.banks.size();
        bankCapacity = s.bankCapacity;
        stored = s.totalStored();
        supplyGops = s.supplyGops;
        supplyQops = s.supplyQops;
        demandGops = s.demandGops;
        demandQops = s.demandQops;
        stock.clear();
        for (Map.Entry<DataType, Long> e : DigitalNetwork.sortedStock(s)) {
            if (stock.size() >= MAX_TYPES) {
                break;
            }
            stock.add(new Entry(e.getKey().getTranslationKey(), e.getKey().getColor(), e.getValue()));
        }
        machines.clear();
        boolean lab = false, producer = false;
        for (BlockPos p : s.machines) {
            if (p.equals(worldPosition)) {
                continue;
            }
            BlockEntity tile = server.isLoaded(p) ? server.getBlockEntity(p) : null;
            if (tile == null) {
                continue;
            }
            int role = role(tile);
            lab |= role == LAB;
            producer |= role == PRODUCER;
            if (machines.size() < MAX_MACHINES) {
                machines.add(new Machine(tile.getBlockState().getBlock().getDescriptionId(), role, detail(tile), p.getX(), p.getY(), p.getZ()));
            }
        }
        machines.sort((a, b) -> Integer.compare(a.role == OTHER ? 99 : a.role, b.role == OTHER ? 99 : b.role));
        needs.clear();
        research = "";
        ResearchNode active = ResearchState.get(server.getServer()).getActive();
        if (active != null) {
            research = active.getTranslationKey();
            for (Map.Entry<ProbeDestination, Long> cost : active.getUnitCost().entrySet()) {
                DataType type = PiDataTypes.of(cost.getKey());
                needs.add(new Need(type.getTranslationKey(), type.getColor(), cost.getValue(), s.stored(type)));
            }
        }
        tips.clear();
        if (grids == 0) {
            tips.add(TIP_NOT_CONNECTED);
        } else {
            if (banks == 0) {
                tips.add(TIP_NO_BANK);
            } else if (bankCapacity > 0 && stored >= bankCapacity * 0.95) {
                tips.add(TIP_BANKS_FULL);
            }
            if (demandGops > 0 && supplyGops <= 0 || demandQops > 0 && supplyQops <= 0) {
                tips.add(TIP_NO_COMPUTE);
            } else if (demandGops > supplyGops * 1.001 || demandQops > supplyQops * 1.001) {
                tips.add(TIP_COMPUTE_SHORT);
            }
            if (active == null) {
                tips.add(TIP_NO_RESEARCH);
            } else {
                if (!lab) {
                    tips.add(TIP_NO_LAB);
                }
                for (Need n : needs) {
                    if (n.inStock < n.perUnit) {
                        tips.add(TIP_RESEARCH_DATA);
                        break;
                    }
                }
            }
            if (!producer && stored == 0) {
                tips.add(TIP_NO_PRODUCER);
            }
            if (tips.isEmpty()) {
                tips.add(TIP_ALL_GOOD);
            }
        }
    }

    public static int role(BlockEntity tile) {
        if (tile instanceof TileEntityDataBank) {
            return BANK;
        }
        if (tile instanceof TileEntityResearchTable) {
            return LAB;
        }
        if (tile instanceof TileEntityDataAntenna || tile instanceof TileEntityAnalyzer || tile instanceof TileEntityBiomeAnalyzer) {
            return PRODUCER;
        }
        if (tile instanceof AiLabTile || tile instanceof FactoryAiCoreTile) {
            return AI;
        }
        if (tile instanceof LogicTile || tile instanceof ScreenTile || tile instanceof ComputerTile || tile instanceof NetworkTerminalTile) {
            return SUPERVISION;
        }
        if (tile instanceof ControllerTile c) {
            return c.computeCore() != null ? COMPUTE : MULTIBLOCK;
        }
        return OTHER;
    }

    private static String detail(BlockEntity tile) {
        if (tile instanceof TileEntityDataBank bank) {
            DataStack st = bank.getTank().getStack();
            return st.isEmpty() ? "" : st.getAmount() + "|" + st.getType().getTranslationKey();
        }
        if (tile instanceof ControllerTile c && c.computeCore() != null) {
            return Math.round(c.computeCore().capacity()) + (c.computeCore().quantum ? " QOPS" : " GOPS");
        }
        if (tile instanceof MachineTile m) {
            return m.getActive() ? "on" : "off";
        }
        return "";
    }

    // ------------------------------------------------------------------
    // Synchro client
    // ------------------------------------------------------------------

    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        CompoundTag t = new CompoundTag();
        t.putInt("g", grids);
        t.putLong("c", cables);
        t.putInt("b", banks);
        t.putLong("bc", bankCapacity);
        t.putLong("st", stored);
        t.putDouble("sg", supplyGops);
        t.putDouble("sq", supplyQops);
        t.putDouble("dg", demandGops);
        t.putDouble("dq", demandQops);
        t.putString("r", research);
        ListTag l = new ListTag();
        for (Entry e : stock) {
            CompoundTag c = new CompoundTag();
            c.putString("k", e.key);
            c.putInt("o", e.color);
            c.putLong("a", e.amount);
            l.add(c);
        }
        t.put("stock", l);
        ListTag m = new ListTag();
        for (Machine e : machines) {
            CompoundTag c = new CompoundTag();
            c.putString("n", e.name);
            c.putInt("r", e.role);
            c.putString("d", e.detail);
            c.putIntArray("p", new int[]{e.x, e.y, e.z});
            m.add(c);
        }
        t.put("machines", m);
        ListTag n = new ListTag();
        for (Need e : needs) {
            CompoundTag c = new CompoundTag();
            c.putString("k", e.key);
            c.putInt("o", e.color);
            c.putLong("u", e.perUnit);
            c.putLong("s", e.inStock);
            n.add(c);
        }
        t.put("needs", n);
        t.putIntArray("tips", tips.stream().mapToInt(Integer::intValue).toArray());
        tag.put("net", t);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        if (!tag.contains("net")) {
            return;
        }
        CompoundTag t = tag.getCompound("net");
        grids = t.getInt("g");
        cables = t.getLong("c");
        banks = t.getInt("b");
        bankCapacity = t.getLong("bc");
        stored = t.getLong("st");
        supplyGops = t.getDouble("sg");
        supplyQops = t.getDouble("sq");
        demandGops = t.getDouble("dg");
        demandQops = t.getDouble("dq");
        research = t.getString("r");
        stock.clear();
        for (Tag x : t.getList("stock", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            stock.add(new Entry(c.getString("k"), c.getInt("o"), c.getLong("a")));
        }
        machines.clear();
        for (Tag x : t.getList("machines", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            int[] p = c.getIntArray("p");
            machines.add(new Machine(c.getString("n"), c.getInt("r"), c.getString("d"), p.length > 0 ? p[0] : 0, p.length > 1 ? p[1] : 0,
                  p.length > 2 ? p[2] : 0));
        }
        needs.clear();
        for (Tag x : t.getList("needs", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            needs.add(new Need(c.getString("k"), c.getInt("o"), c.getLong("u"), c.getLong("s")));
        }
        tips.clear();
        for (int i : t.getIntArray("tips")) {
            tips.add(i);
        }
    }

    /** La fibre se branche sur le terminal (prise sans réservoir : rien n'y entre). */
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        LazyOptional<T> plug = NetworkPlug.of(cap);
        return plug != null ? plug : super.getCapability(cap, side);
    }

    /** API Machine (lot 4) : cadre de l'interface standard v2 (inventaire DANS le cadre, onglets a droite) autour du corps propre d'avant. */
    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    /** Lot 8b (vraie v2) : rangee des pages, puis l'ecran (zone de 236 x 152). */
    public static final StandardLayout.Rect PAGES = new StandardLayout.Rect(8, 18, 220, 13), SCREEN = new StandardLayout.Rect(8, 34, 220, 152 - 34 - 4);

    @Override
    public List<StandardLayout.Part> guiParts() {
        return List.of(new StandardLayout.Part("setting", PAGES), new StandardLayout.Part("card", SCREEN));
    }
}

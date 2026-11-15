package fr.pioneers.naval;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.DataMachineTile;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.machine.TieredMachineBlock;
import fr.pioneers.core.pressure.PressureVolume;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.data.BasicDataTank;
import fr.pioneers.rail.logistics.Cargo;
import fr.pioneers.rail.train.Car;
import fr.pioneers.rail.train.RailFuels;
import fr.pioneers.registries.PiNaval;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.11 : le port (MKI a MKIV), interface entre la terre et le navire amarre devant lui. Reserves de toutes les familles (9 cases, cuve,
 * reservoir de gaz, energie, donnees, air comprime), raccordees aux cables du mod par ses faces ; chaque tick, il charge ou decharge le navire
 * amarre (sens du joueur, ou consigne du repartiteur) et le ravitaille (combustible et assemblages pris dans ses cases, carburant dans sa
 * cuve, recharge des batteries depuis son energie). Toutes les deux secondes, il verifie son poste d'amarrage (eau devant le cote mer,
 * largeur et profondeur pour chaque tier) et s'inscrit dans {@link NavalWorld}.
 * <p>
 * <p>
 * 0.20.14 : port multibloc. La capitainerie (ce bloc) se pose dans une rangee de dalles de quai le long de l'eau ; les equipements de quai
 * ({@link HarborEquipment} : grue, bras de chargement, borne, mat, passerelle, poste de soutage) remplacent des dalles. Le navire s'amarre
 * centre sur le quai, qui doit etre au moins aussi long que lui ; chaque ressource ne passe que par son equipement (debit x nombre, 4 au
 * plus), qui s'anime pendant les transferts.
 * <p>
 * Interface : 4 sens (0 charger, 1 decharger), 5 navire a quai (0/1), 6 quantite passee, 7 etat du poste (ordinal de {@link Harbor.Berth}),
 * 8 plus grand tier accepte (-1 aucun), 9 donnees stockees, 10 capacite des donnees, 11 air (L x10), 12 volume d'air (L), 13 longueur des
 * bras, 14 equipements (masque, un bit par sorte), 15 largeur du bassin, 16 case fautive (0/1), 17-19 ses x, y, z (0.20.23).
 */
public class HarborTile extends DataMachineTile implements MultiblockPort, MultiblockCore.House.Holder {

    public static final int MODE = CUSTOM;
    public static final int[] ITEM_RATE = {16, 64, 256, 1024};

    private final int tier;
    private final List<ItemSlot> buffer = new ArrayList<>();
    private final FluidTank fluid;
    private final BasicGasTank gas;
    private final BasicDataTank data;
    private final PressureVolume air;
    private final LazyOptional<PressureVolume> airCap;
    private boolean unload, serving;
    private long moved;
    private float credit;
    private int ticks;
    private Direction waterSide = Direction.NORTH;
    private Harbor.Berth berth = Harbor.Berth.UNKNOWN;
    /** Lot 8a : cycle de vie commun des multiblocs maison (verification toutes les 2 s ou a la demande ; revalidee au chargement). */
    private final MultiblockCore.House house = new MultiblockCore.House(new MultiblockCore.House.Host() {
        @Override
        public net.minecraft.world.level.Level level() {
            return level;
        }

        @Override
        public BlockPos pos() {
            return worldPosition;
        }

        @Override
        public net.minecraft.core.Direction front() {
            return getBlockState().getValue(MachineBlock.FACING);
        }

        @Override
        public MultiblockCore.House.Survey survey() {
            if (!(level instanceof ServerLevel sl)) {
                return null;
            }
            Direction basin = getBlockState().getValue(MachineBlock.FACING).getOpposite();
            HarborStructure.Result r = HarborStructure.validate(sl, worldPosition, basin, Math.min(3, tier));
            List<BlockPos> cells = new ArrayList<>();
            for (HarborStructure.Part part : r.parts()) {
                cells.add(part.pos());
            }
            return r.formed() ? MultiblockCore.House.Survey.ok(cells, r)
                  : MultiblockCore.House.Survey.fail("gui.pioneers.harbor.berth." + r.berth().name().toLowerCase(java.util.Locale.ROOT), r.error(), r);
        }

        @Override
        public void formed(MultiblockCore.House.Survey survey, boolean first) {
            applySurvey((ServerLevel) level, (HarborStructure.Result) survey.data());
        }

        @Override
        public void unformed(MultiblockCore.House.Survey survey, boolean broken) {
            if (!survey.removal()) {
                applySurvey((ServerLevel) level, (HarborStructure.Result) survey.data());
            }
        }

        @Override
        public void houseChanged() {
            setChanged();
            markSync();
        }
    }, 40);
    private int maxTier = -1;
    private final HarborPoint point;
    //0.20.20 : bassin en U (longueur des bras, demi-largeur du bassin), portee de chaque equipement ; 0.20.23 : taille fixee par le tier
    private int quay = 0, halfWidth = 0;
    /** 0.20.23 : premiere case fautive du plan (piece de quai manquante ou case du bassin sans eau), ou null. */
    @Nullable
    private BlockPos missing;
    private final Map<Long, Float> reachAt = new HashMap<>();
    private final EnumMap<HarborEquipment, List<BlockPos>> equipment = new EnumMap<>(HarborEquipment.class);
    private final Set<Long> equipmentAt = new HashSet<>();
    /** 0.20.25 : interfaces du port reliees. */
    private final Set<Long> interfaces = new HashSet<>();
    private final int[] lastWork = new int[HarborEquipment.values().length];
    public static final int SHEET = CUSTOM + 1;

    public HarborTile(BlockPos pos, BlockState state) {
        super(PiNaval.HARBOR_TILE.get(), pos, state, energyCapacity(state), energyRate(state));
        tier = TieredMachineBlock.tierOf(state);
        int size = 1 << tier;
        for (int i = 0; i < 9; i++) {
            //Lot 6 : interface v2 (cases contre le bord droit, comme les postes du quai ; ORDRE inchange)
            buffer.add(slot(ItemSlot.of(this::setChanged, StandardLayout.POST_SLOTS_X + (i % 3) * 18, StandardLayout.POST_SLOTS_Y + (i / 3) * 18)));
        }
        fluid = fluidTank(new FluidTank(16_000 * size) {
            @Override
            protected void onContentsChanged() {
                setChanged();
                markSync();
            }
        });
        gas = gasTank(BasicGasTank.of(32_000L * size, this::setChanged));
        data = dataTank(BasicDataTank.create(4_000_000L * size, this::setChanged));
        dataDirections(true, true);
        air = new PressureVolume(250 * size, 10, 40 * size, this::setChanged);
        airCap = LazyOptional.of(() -> air);
        point = new HarborPoint(this);
    }

    private static long energyCapacity(BlockState state) {
        return 4_000_000L << 2 * TieredMachineBlock.tierOf(state);
    }

    private static int energyRate(BlockState state) {
        return 8_192 << 3 * TieredMachineBlock.tierOf(state);
    }

    @Override
    protected boolean producesEnergy() {
        return false;
    }

    @Override
    protected SideMode initialMode() {
        return SideMode.IO;
    }

    public int tier() {
        return tier;
    }

    public Direction waterSide() {
        return waterSide;
    }

    // ------------------------------------------------------------------ tick

    @Override
    protected void tick() {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        ticks++;
        NavalWorld w = NavalWorld.get(sl);
        house.tick();
        if (ticks % 20 == 0) {
            w.reportStock(worldPosition.asLong(), stock(), sl.getBestNeighborSignal(worldPosition));
            point.update(sl, w);
        }
        Ship ship = w.docked(worldPosition.asLong());
        //1.9 (MAJ RP, bloc 4) : le port ne sert pas un navire amarre de l'autre cote d'une frontiere de claim refusee
        if (ship != null && !ClanApi.canTransfer(sl, worldPosition, BlockPos.containing(ship.x, ship.y, ship.z))) {
            ship = null;
        }
        serving = ship != null;
        boolean worked = false;
        if (ship != null && canWork()) {
            NavalDispatcher.Plan plan = NavalDispatcher.plan(w, ship, worldPosition.asLong());
            boolean out = plan != null ? !plan.load() : unload;
            Cargo only = plan == null ? null : plan.cargo();
            int rate = ITEM_RATE[tier] * cranes();
            credit = Math.min(rate, credit + rate / 20F);
            long done = 0;
            for (Car hold : ship.holds) {
                HarborEquipment e = HarborEquipment.serving(hold.type);
                int n = e == null ? 0 : Math.min(4, count(e));
                if (n == 0) {
                    continue;
                }
                long d = transfer(sl, w, ship, hold, out, only, n);
                if (d > 0) {
                    lastWork[e.ordinal()] = ticks;
                    done += d;
                }
            }
            done += bunker(ship);
            if (done > 0) {
                moved += done;
                w.activity(ship);
                worked = true;
            }
        }
        if (ticks % 10 == 0) {
            animateEquipment(ship);
        }
        markWorking(worked);
    }

    /**
     * 0.20.23 : verification du multibloc, comme les autres : le plan {@link HarborLayout} du tier de la capitainerie (U de dalles a la taille
     * fixe de son navire, bassin derriere la facade) est compare au monde par {@link HarborStructure} ; la premiere case fautive est
     * affichee dans l'interface. Les equipements poses sur le plan sont relies et tournes vers l'eau meme si le U est incomplet.
     */
    private void applySurvey(ServerLevel sl, HarborStructure.Result r) {
        NavalWorld w = NavalWorld.get(sl);
        Direction basin = getBlockState().getValue(MachineBlock.FACING).getOpposite();
        int t = Math.min(3, tier);
        waterSide = basin;
        quay = HarborLayout.armLength(t);
        halfWidth = HarborLayout.halfWidth(t);
        if (r.berth() != Harbor.Berth.UNKNOWN) {
            Set<Long> before = new HashSet<>(equipmentAt);
            equipment.clear();
            equipmentAt.clear();
            reachAt.clear();
            for (HarborStructure.Part part : r.parts()) {
                part(sl, part.pos(), HarborLayout.facing(part.cell(), basin), HarborLayout.reach(part.cell(), t));
            }
            for (long l : before) {
                if (!equipmentAt.contains(l)) {
                    unlink(sl, BlockPos.of(l));
                }
            }
        }
        //0.20.25 : dalles reconnectees sur tout le plan (coins, murs sous le dessus)
        if (r.berth() != Harbor.Berth.UNKNOWN) {
            Set<Long> oldInterfaces = new HashSet<>(interfaces);
            interfaces.clear();
            for (HarborLayout.Cell cell : HarborLayout.cells(t)) {
                if (cell.role() != HarborLayout.Role.WATER) {
                    BlockPos top = HarborLayout.at(worldPosition, basin, cell.x(), cell.z());
                    for (int dy = 0; dy < HarborLayout.HEIGHT; dy++) {
                        BlockPos p = top.below(dy);
                        QuayBlock.refresh(sl, p);
                        //0.20.25 : interfaces du port reliees a cette capitainerie
                        if (sl.getBlockEntity(p) instanceof HarborInterfaceTile it) {
                            it.link(worldPosition);
                            interfaces.add(p.asLong());
                        }
                    }
                }
            }
            for (long l : oldInterfaces) {
                if (!interfaces.contains(l) && sl.isLoaded(BlockPos.of(l)) && sl.getBlockEntity(BlockPos.of(l)) instanceof HarborInterfaceTile it
                    && worldPosition.equals(it.master())) {
                    it.link(null);
                }
            }
        }
        int max = r.formed() ? t : -1;
        int waterY = r.waterY();
        berth = r.berth();
        maxTier = max;
        missing = r.error();
        double cx = worldPosition.getX() + 0.5, cz = worldPosition.getZ() + 0.5;
        Harbor h = w.registerHarbor(worldPosition, waterSide, tier);
        if (h.facing != waterSide || h.berth != berth || h.maxTier != max || h.waterY != waterY || h.quay != quay || h.cx != cx || h.cz != cz) {
            h.facing = waterSide;
            h.berth = berth;
            h.maxTier = max;
            h.waterY = waterY;
            h.quay = quay;
            h.cx = cx;
            h.cz = cz;
            w.setDirty();
        }
        markSync();
    }

    /** Une piece du U : tournee vers l'eau ; un equipement est releve et relie, avec sa portee (distance a l'axe du navire). */
    private void part(ServerLevel sl, BlockPos p, Direction facing, float reach) {
        BlockState st = sl.getBlockState(p);
        if (st.hasProperty(QuayBlock.FACING) && st.getValue(QuayBlock.FACING) != facing) {
            st = st.setValue(QuayBlock.FACING, facing);
            sl.setBlock(p, st, Block.UPDATE_CLIENTS);
        }
        if (st.getBlock() instanceof HarborEquipmentBlock b) {
            equipment.computeIfAbsent(b.kind, x -> new ArrayList<>()).add(p);
            equipmentAt.add(p.asLong());
            reachAt.put(p.asLong(), reach);
            if (sl.getBlockEntity(p) instanceof HarborEquipmentTile t) {
                t.link(worldPosition, tier);
            }
        }
    }

    private void unlink(ServerLevel sl, BlockPos p) {
        if (sl.isLoaded(p) && sl.getBlockEntity(p) instanceof HarborEquipmentTile t && worldPosition.equals(t.master())) {
            t.link(null, t.tier);
        }
    }

    // ------------------------------------------------------------------ transferts

    private long transfer(ServerLevel sl, NavalWorld w, Ship ship, Car hold, boolean out, @Nullable Cargo only, int count) {
        int rate = ITEM_RATE[tier] * count;
        long fluidRate = (250L << 2 * tier) * count;
        return switch (hold.type) {
            case BOXCAR -> items(hold, out, only);
            case TANK -> fluids(hold, out, (int) fluidRate, only);
            case GAS_TANK -> gases(hold, out, (int) (fluidRate * 2), only);
            case BATTERY -> power(hold, out, (long) rate * 8_192);
            case SERVER -> datas(hold, out, (long) rate * 1_000, only);
            case AIR -> airs(hold, out, rate * 4);
            case PASSENGER -> ticks % 20 == 0 ? passengers(sl, w, ship, out) : 0;
            default -> 0;
        };
    }

    private long items(Car hold, boolean out, @Nullable Cargo only) {
        int budget = (int) credit;
        if (budget <= 0) {
            return 0;
        }
        int done = 0;
        if (out) {
            for (int i = 0; i < hold.items.getSlots() && done < budget; i++) {
                ItemStack st = hold.items.getStackInSlot(i);
                if (st.isEmpty() || only != null && !only.covers(Cargo.item(Cargo.itemId(st)))) {
                    continue;
                }
                ItemStack offer = hold.items.extractItem(i, Math.min(budget - done, st.getCount()), true);
                ItemStack rest = offer.copy();
                for (ItemSlot s : buffer) {
                    rest = s.insert(rest, Action.EXECUTE, Automation.INTERNAL);
                    if (rest.isEmpty()) {
                        break;
                    }
                }
                int n = offer.getCount() - rest.getCount();
                if (n > 0) {
                    hold.items.extractItem(i, n, false);
                    done += n;
                }
            }
        } else {
            for (ItemSlot s : buffer) {
                if (s.isEmpty() || done >= budget) {
                    continue;
                }
                ItemStack st = s.getStack();
                if (only != null && !only.covers(Cargo.item(Cargo.itemId(st)))) {
                    continue;
                }
                ItemStack offer = st.copy();
                offer.setCount(Math.min(offer.getCount(), budget - done));
                ItemStack rest = offer.copy();
                for (int i = 0; i < hold.items.getSlots() && !rest.isEmpty(); i++) {
                    rest = hold.items.insertItem(i, rest, false);
                }
                int n = offer.getCount() - rest.getCount();
                if (n > 0) {
                    s.extract(n, Action.EXECUTE, Automation.INTERNAL);
                    done += n;
                }
            }
        }
        credit -= done;
        return done;
    }

    @Nullable
    private static ResourceLocation rl(String id) {
        return ResourceLocation.tryParse(id);
    }

    static String id(Fluid f) {
        ResourceLocation r = ForgeRegistries.FLUIDS.getKey(f);
        return r == null ? "" : r.toString();
    }

    static String id(Gas g) {
        ResourceLocation r = g.getRegistryName();
        return r == null ? "" : r.toString();
    }

    private long fluids(Car hold, boolean out, int rate, @Nullable Cargo only) {
        if (out) {
            if (hold.fluidAmount <= 0 || rl(hold.fluid) == null || only != null && !only.covers(Cargo.fluid(rl(hold.fluid)))) {
                return 0;
            }
            Fluid f = ForgeRegistries.FLUIDS.getValue(ResourceLocation.parse(hold.fluid));
            if (f == null) {
                return 0;
            }
            int n = fluid.fill(new FluidStack(f, Math.min(rate, hold.fluidAmount)), IFluidHandler.FluidAction.EXECUTE);
            hold.drainFluid(n, false);
            return n;
        }
        FluidStack have = fluid.getFluid();
        if (have.isEmpty() || rl(id(have.getFluid())) == null || only != null && !only.covers(Cargo.fluid(rl(id(have.getFluid()))))) {
            return 0;
        }
        int n = hold.fillFluid(id(have.getFluid()), Math.min(rate, have.getAmount()), false);
        if (n > 0) {
            fluid.drain(n, IFluidHandler.FluidAction.EXECUTE);
        }
        return n;
    }

    private long gases(Car hold, boolean out, int rate, @Nullable Cargo only) {
        if (out) {
            if (hold.gasAmount <= 0 || rl(hold.gas) == null || only != null && !only.covers(Cargo.gas(rl(hold.gas)))) {
                return 0;
            }
            Gas g = PioneersAPI.gas(ResourceLocation.parse(hold.gas));
            if (g == null) {
                return 0;
            }
            GasStack rest = gas.insert(GasStack.of(g, Math.min(rate, hold.gasAmount)), Action.EXECUTE, Automation.INTERNAL);
            int n = (int) (Math.min(rate, hold.gasAmount) - rest.getAmount());
            hold.drainGas(n, false);
            return n;
        }
        GasStack have = gas.getStack();
        if (have.isEmpty() || rl(id(have.getGas())) == null || only != null && !only.covers(Cargo.gas(rl(id(have.getGas()))))) {
            return 0;
        }
        int n = hold.fillGas(id(have.getGas()), (int) Math.min(rate, have.getAmount()), false);
        if (n > 0) {
            gas.extract(n, Action.EXECUTE, Automation.INTERNAL);
        }
        return n;
    }

    private long power(Car hold, boolean out, long rate) {
        if (out) {
            long n = Math.min(rate, Math.min(hold.energy, energy.getCapacity() - energy.getStored()));
            if (n > 0) {
                hold.energy -= n;
                hold.touched = true;
                energy.setStored(energy.getStored() + n);
            }
            return Math.max(0, n);
        }
        long n = Math.min(rate, Math.min(energy.getStored(), hold.energyCapacity() - hold.energy));
        if (n > 0) {
            hold.energy += n;
            hold.touched = true;
            energy.setStored(energy.getStored() - n);
        }
        return Math.max(0, n);
    }

    private long datas(Car hold, boolean out, long rate, @Nullable Cargo only) {
        if (out) {
            if (hold.data <= 0) {
                return 0;
            }
            DataType type = PioneersAPI.dataTypeRegistry().getValue(ResourceLocation.parse(hold.dataType));
            if (type == null || only != null && !only.covers(new Cargo(Cargo.Kind.DATA, rl(hold.dataType)))) {
                return 0;
            }
            long want = Math.min(rate, hold.data);
            DataStack rest = data.insert(DataStack.of(type, want), Action.EXECUTE, Automation.INTERNAL);
            long n = want - rest.getAmount();
            hold.drainData(n, false);
            return n;
        }
        DataStack have = data.getStack();
        if (have.isEmpty()) {
            return 0;
        }
        long n = hold.fillData(have.getType().getRegistryName().toString(), Math.min(rate, have.getAmount()), false);
        if (n > 0) {
            data.extract(n, Action.EXECUTE, Automation.INTERNAL);
        }
        return n;
    }

    private long airs(Car hold, boolean out, int rate) {
        if (out) {
            int n = (int) Math.floor(air.insertAir(Math.min(rate, hold.air), true));
            n = Math.max(0, Math.min(n, hold.air));
            if (n > 0) {
                air.insertAir(n, false);
                hold.air -= n;
                hold.touched = true;
            }
            return n;
        }
        int room = hold.airCapacity() - hold.air;
        int n = (int) Math.floor(air.extractAir(Math.min(rate, room), true));
        n = Math.max(0, Math.min(n, room));
        if (n > 0) {
            air.extractAir(n, false);
            hold.air += n;
            hold.touched = true;
        }
        return n;
    }

    /** Ferry : en dechargement, les villageois debarquent ; en chargement, ceux du quai embarquent. Les joueurs montent par la fiche. */
    private long passengers(ServerLevel sl, NavalWorld w, Ship ship, boolean out) {
        ShipEntity view = w.view(ship.id);
        if (view == null) {
            return 0;
        }
        long n = 0;
        if (out) {
            for (Entity e : List.copyOf(view.getPassengers())) {
                if (!(e instanceof Player)) {
                    e.stopRiding();
                    e.teleportTo(worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5);
                    n++;
                }
            }
            return n;
        }
        AABB around = new AABB(worldPosition).inflate(3, 1, 3);
        for (AbstractVillager v : sl.getEntitiesOfClass(AbstractVillager.class, around, v -> !v.isPassenger())) {
            if (view.getPassengers().size() >= ship.kind.seats(ship.tier)) {
                break;
            }
            if (v.startRiding(view, true)) {
                n++;
            }
        }
        return n;
    }

    /** Soutage : combustible, carburant, assemblages et recharge, pris dans les reserves du port. */
    private long bunker(Ship ship) {
        long done = 0;
        for (Car e : ship.engines) {
            HarborEquipment q = HarborEquipment.serving(e.type);
            if (q == null || count(q) == 0) {
                continue;
            }
            long before = done;
            switch (e.type) {
                case STEAM, TENDER -> {
                    for (ItemSlot s : buffer) {
                        ItemStack st = s.getStack();
                        if (st.isEmpty() || ForgeHooks.getBurnTime(st, RecipeType.SMELTING) <= 0) {
                            continue;
                        }
                        ItemStack offer = st.copy();
                        offer.setCount(Math.min(offer.getCount(), 4));
                        ItemStack rest = offer.copy();
                        for (int i = 0; i < e.items.getSlots() && !rest.isEmpty(); i++) {
                            rest = e.items.insertItem(i, rest, false);
                        }
                        int n = offer.getCount() - rest.getCount();
                        if (n > 0) {
                            s.extract(n, Action.EXECUTE, Automation.INTERNAL);
                            done += n;
                        }
                    }
                }
                case DIESEL -> {
                    FluidStack have = fluid.getFluid();
                    if (!have.isEmpty() && RailFuels.liquid(id(have.getFluid())) > 0) {
                        int n = e.fillFluid(id(have.getFluid()), Math.min(500 << 2 * tier, have.getAmount()), false);
                        if (n > 0) {
                            fluid.drain(n, IFluidHandler.FluidAction.EXECUTE);
                            done += n;
                        }
                    }
                }
                case ELECTRIC -> {
                    long n = Math.min(energy.getStored(), Math.min(e.energyCapacity() - e.energy, 32_768L << 2 * tier));
                    if (n > 0) {
                        e.energy += n;
                        energy.setStored(energy.getStored() - n);
                        done += 1;
                    }
                }
                case NUCLEAR -> {
                    //Assemblages uses repris, combustible neuf donne
                    ItemStack spent = e.items.getStackInSlot(1);
                    if (!spent.isEmpty()) {
                        ItemStack rest = spent.copy();
                        for (ItemSlot s : buffer) {
                            rest = s.insert(rest, Action.EXECUTE, Automation.INTERNAL);
                            if (rest.isEmpty()) {
                                break;
                            }
                        }
                        e.items.setStackInSlot(1, rest);
                    }
                    for (ItemSlot s : buffer) {
                        if (!s.isEmpty() && RailFuels.nuclear(s.getStack()) > 0) {
                            ItemStack rest = e.items.insertItem(0, s.getStack().copyWithCount(1), false);
                            if (rest.isEmpty()) {
                                s.extract(1, Action.EXECUTE, Automation.INTERNAL);
                                done++;
                            }
                        }
                    }
                }
                default -> {
                }
            }
            if (done > before) {
                lastWork[q.ordinal()] = ticks;
            }
        }
        return done;
    }

    // ------------------------------------------------------------------ quai et equipements (0.20.14)

    /** Nombre d'equipements de cette sorte dans le quai. */
    public int count(HarborEquipment e) {
        List<BlockPos> l = equipment.get(e);
        return l == null ? 0 : l.size();
    }

    private int cranes() {
        return Math.max(1, Math.min(4, count(HarborEquipment.CRANE)));
    }

    public boolean hasEquipment(BlockPos pos) {
        return equipmentAt.contains(pos.asLong());
    }

    /** Le navire a-t-il une cale ou une machine que cet equipement sert ? */
    private static boolean needs(Ship ship, HarborEquipment e) {
        for (Car c : ship.holds) {
            if (HarborEquipment.serving(c.type) == e) {
                return true;
            }
        }
        for (Car c : ship.engines) {
            if (HarborEquipment.serving(c.type) == e) {
                return true;
            }
        }
        return false;
    }

    /** Recopie dans chaque equipement l'etat de son animation : deploye vers le navire qu'il sert, au travail, distance et denivele. */
    private void animateEquipment(@Nullable Ship ship) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        Harbor h = NavalWorld.get(sl).harbor(worldPosition.asLong());
        float drop = ship == null || h == null ? 0
              : (float) (h.waterY + 0.875 + ShipKind.freeboard(ship.tier) / 16.0 - (worldPosition.getY() + 1));
        for (Map.Entry<HarborEquipment, List<BlockPos>> en : equipment.entrySet()) {
            boolean dep = ship != null && needs(ship, en.getKey());
            boolean work = dep && ticks - lastWork[en.getKey().ordinal()] < 40;
            for (BlockPos p : en.getValue()) {
                if (sl.isLoaded(p) && sl.getBlockEntity(p) instanceof HarborEquipmentTile t) {
                    t.animate(dep, work, reachAt.getOrDefault(p.asLong(), 1.5F), drop);
                }
            }
        }
    }

    // ------------------------------------------------------------------ stock et logistique

    Map<Cargo, Long> stock() {
        Map<Cargo, Long> out = new HashMap<>();
        for (ItemSlot s : buffer) {
            if (!s.isEmpty()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getStack().getItem());
                out.merge(Cargo.item(id), (long) s.getStack().getCount(), Long::sum);
            }
        }
        if (!fluid.getFluid().isEmpty() && rl(id(fluid.getFluid().getFluid())) != null) {
            out.merge(Cargo.fluid(rl(id(fluid.getFluid().getFluid()))), (long) fluid.getFluidAmount(), Long::sum);
        }
        if (!gas.getStack().isEmpty() && rl(id(gas.getStack().getGas())) != null) {
            out.merge(Cargo.gas(rl(id(gas.getStack().getGas()))), gas.getStack().getAmount(), Long::sum);
        }
        if (energy.getStored() > 0) {
            out.merge(Cargo.ENERGY, energy.getStored(), Long::sum);
        }
        if (!data.getStack().isEmpty()) {
            out.merge(new Cargo(Cargo.Kind.DATA, data.getStack().getType().getRegistryName()), data.getStored(), Long::sum);
        }
        if (air.getAir() >= 1) {
            out.merge(Cargo.AIR, (long) air.getAir(), Long::sum);
        }
        return out;
    }

    /** Reglages logistiques changes : le point des robots suit tout de suite. */
    public void refreshPoint() {
        if (level instanceof ServerLevel sl) {
            point.update(sl, NavalWorld.get(sl));
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        house.load();
    }

    @Override
    public MultiblockCore.House house() {
        return house;
    }

    @Override
    public void requestCheck() {
        house.requestCheck();
    }

    @Override
    public void setRemoved() {
        house.removed(true);
        point.remove(level);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        point.remove(level);
        super.onChunkUnloaded();
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        if (action == MODE) {
            unload = !unload;
            setChanged();
            markSync();
        } else if (action == SHEET && level instanceof ServerLevel sl) {
            NavalWorld.get(sl).registerHarbor(worldPosition, waterSide, tier);
            NavalNetworking.openHarbor(player, sl, worldPosition);
        }
    }

    /** Lot 6 : interface standard v2 (zone 152, inventaire dans le cadre, onglets a droite, Infos et Structure). */
    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    /** Lot 6 : jauges (fluide, gaz, energie), carte du poste, boutons (sens, fiche), 9 cases, ecran. */
    @Override
    public List<StandardLayout.Part> guiParts() {
        List<StandardLayout.Part> out = new ArrayList<>(StandardLayout.postParts(GAUGES, true, true));
        return out;
    }

    /** Lot 6 : jauges de l'interface (fluide, gaz, energie). */
    public static final int GAUGES = 3;

    /**
     * Lot 6 : valeurs NOMMEES (apres les 4 communes, memes indices qu'avant) : sens, navire servi, quantite passee, poste (Harbor.Berth),
     * tier maximal, donnees, contenance, air x10, volume, longueur du quai, equipements (masque), largeur du bassin, case fautive (drapeau, x,
     * y, z).
     */
    public static final UiValues.Key UNLOAD = UiValues.Key.of("unload"), SERVING = UiValues.Key.of("serving"), MOVED = UiValues.Key.of("moved"),
          BERTH = UiValues.Key.of("berth"), MAX_TIER = UiValues.Key.of("max_tier"), DATA_STORED = UiValues.Key.of("data_stored"),
          DATA_CAPACITY = UiValues.Key.of("data_capacity"), AIR_X10 = UiValues.Key.of("air_x10"), VOLUME = UiValues.Key.of("volume"),
          QUAY = UiValues.Key.of("quay"), BASIN = UiValues.Key.of("basin"), MISSING = UiValues.Key.of("missing"), MISSING_X = UiValues.Key.of("missing_x"),
          MISSING_Y = UiValues.Key.of("missing_y"), MISSING_Z = UiValues.Key.of("missing_z"), EQUIPMENT = UiValues.Key.of("equipment");

    @Override
    protected boolean namedValues() {
        return true;
    }

    @Override
    protected void declareValues(UiValues.Builder values) {
        UiValues.Key[] keys = {UNLOAD, SERVING, MOVED, BERTH, MAX_TIER, DATA_STORED, DATA_CAPACITY, AIR_X10, VOLUME, QUAY, EQUIPMENT, BASIN, MISSING,
              MISSING_X, MISSING_Y, MISSING_Z};
        for (int i = 0; i < keys.length; i++) {
            int k = i;
            values.add(keys[i], () -> machineValue(k));
        }
    }

    @Override
    protected int machineValueCount() {
        return 16;
    }

    @Override
    protected long machineValue(int index) {
        return switch (index) {
            case 0 -> unload ? 1 : 0;
            case 1 -> serving ? 1 : 0;
            case 2 -> moved;
            case 3 -> berth.ordinal();
            case 4 -> maxTier;
            case 5 -> data.getStored();
            case 6 -> data.getCapacity();
            case 7 -> Math.round(air.getAir() * 10);
            case 8 -> Math.round(air.getVolume());
            case 9 -> quay;
            case 11 -> quay > 0 ? 2L * halfWidth + 1 : 0;
            case 12 -> missing == null ? 0 : 1;
            case 13 -> missing == null ? 0 : missing.getX();
            case 14 -> missing == null ? 0 : missing.getY();
            case 15 -> missing == null ? 0 : missing.getZ();
            default -> {
                long mask = 0;
                for (HarborEquipment e : equipment.keySet()) {
                    mask |= 1L << e.ordinal();
                }
                yield mask;
            }
        };
    }

    @Override
    public String guiTier() {
        return TieredMachineBlock.TIERS[tier];
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == PioneersAPI.PRESSURE_HANDLER) {
            return airCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        airCap.invalidate();
    }

    public boolean unloads() {
        return unload;
    }

    public void setUnloads(boolean value) {
        if (unload != value) {
            unload = value;
            setChanged();
            markSync();
        }
    }

    @Override
    protected void writeClient(@NotNull CompoundTag tag) {
        super.writeClient(tag);
        house.write(tag);
        tag.putBoolean("unload", unload);
        tag.putByte("water", (byte) waterSide.get3DDataValue());
    }

    @Override
    protected void readClient(@NotNull CompoundTag tag) {
        super.readClient(tag);
        house.read(tag);
        unload = tag.getBoolean("unload");
        Direction d = Direction.from3DDataValue(tag.getByte("water"));
        waterSide = d.getAxis().isHorizontal() ? d : Direction.NORTH;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("unload", unload);
        tag.putLong("moved", moved);
        tag.put("air", air.serializeNBT());
        tag.putByte("water", (byte) waterSide.get3DDataValue());
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        unload = tag.getBoolean("unload");
        moved = tag.getLong("moved");
        if (tag.contains("air")) {
            air.deserializeNBT(tag.getCompound("air"));
        }
        if (tag.contains("water")) {
            Direction d = Direction.from3DDataValue(tag.getByte("water"));
            waterSide = d.getAxis().isHorizontal() ? d : Direction.NORTH;
        }
    }

    /**
     * Lot 8c : CONTRAT DE PORT COMMUN ({@link MultiblockPort#portHandler}) : un port commun pose dans la structure formee prete la capacite
     * interne de la maison ({@link MultiblockPort#capabilityOf}) ; les ports propres de la maison gardent leurs gestionnaires.
     */
    @Nullable
    @Override
    public Object portHandler(fr.pioneers.industry.multi.PortBlock.Type type, boolean output, BlockPos port) {
        return house.formed() ? MultiblockPort.capabilityOf(this, type) : null;
    }
}

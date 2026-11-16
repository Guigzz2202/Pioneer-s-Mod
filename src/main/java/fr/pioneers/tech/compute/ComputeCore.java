package fr.pioneers.tech.compute;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.IDataHandler;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.multiblock.MultiblockLogic;
import fr.pioneers.tech.bronze.steam.Steam;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.Directions;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.gas.PiGasTypes;
import fr.pioneers.core.heat.HeatExchange;
import fr.pioneers.core.data.PiDataTypes;
import fr.pioneers.tech.industry.IndustryEvents;
import fr.pioneers.tech.industry.IoTank;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.PartBlock;
import fr.pioneers.tech.industry.multi.Structure;
import fr.pioneers.core.registries.PiFluids;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.logistics.transport.logistic.LogisticCableBlock;
import fr.pioneers.logistics.transport.logistic.LogisticNetworks;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * Coeur d'un multibloc de calcul (0.17.2) : centre de donnees ({@code data_center}) ou ordinateur quantique ({@code quantum_computer}).
 * <ul>
 *     <li><b>Centre de donnees</b> : chaque baie ({@code server_rack}) offre {@value #RACK_GOPS} GOPS x 2^tier et consomme
 *     {@value #RACK_FE} FE/t x 4^tier a pleine charge (10 % a vide). Chaque FE consomme chauffe le corps du multibloc de
 *     {@value #HEAT_PER_FE} J ; au-dessus de la nominale, l'offre est bridee (0 a +20 %). Les groupes froids (1.0.4-beta : {@value #BUILT_IN_COOLERS}, integres au controleur) evacuent
 *     {@value #COOLER} J/t par K d'ecart x 2^tier chacun vers l'air ; le reservoir de fluide d'entree (eau, caloporteur, azote liquide)
 *     refroidit par immersion jusqu'a 20 K au-dessus de l'ambiante (vapeur ou caloporteur chaud dans le reservoir de sortie). Telemetrie :
 *     1 Mo par {@value #GOPS_PER_TELEMETRY} GOPS servis et par tick, poussee dans les cables de donnees qui touchent la structure.</li>
 *     <li><b>Ordinateur quantique</b> : chaque matrice ({@code qubit_array}) offre {@value #MATRIX_QOPS} QOPS x 2^tier une fois aux
 *     millikelvins ; il faut {@value #HE3_PER_S} mB/s d'helium 3 (gaz) et {@value #LHE_PER_S} mB/s d'helium liquide, et
 *     {@value #MATRIX_FE} FE/t x 4^tier par matrice. Mise en froid : {@value #COOL_TICKS} ticks ; sans fluide ni energie : decoherence
 *     (0 QOPS), remontee en {@value #WARM_TICKS} ticks.</li>
 * </ul>
 */
public final class ComputeCore implements IComputeHandler, ComputeView, MultiblockLogic {

    public static final double RACK_GOPS = 64, MATRIX_QOPS = 1, HEAT_PER_FE = 2, COOLER = 40, GOPS_PER_TELEMETRY = 256;
    /** 1.0.4-beta (lot 15) : groupes froids integres au controleur du centre de donnees (les deux qu'exigeait le toit). */
    public static final int BUILT_IN_COOLERS = 2;
    public static final long RACK_FE = 512, MATRIX_FE = 256;
    public static final int HE3_PER_S = 1, LHE_PER_S = 5, COOL_TICKS = 1200, WARM_TICKS = 200;
    /** Immersion : ecart a l'ambiante vise par le fluide d'entree (K). */
    private static final double IMMERSION_DT = 20;

    private final ControllerTile tile;
    public final boolean quantum;
    private int units, coolers, tier;
    private BlockPos min, max;
    @Nullable
    private Structure.Result structure;
    private final List<BlockPos> cables = new ArrayList<>();
    private boolean rescan = true;
    private int scan, clock;
    /** Offre courante, calcul servi (derniere repartition), charge. */
    private double capacity, served, throttle = 1, cold;
    private float load;
    private long power;
    private double telemetryCarry, telemetryRate;
    private boolean heOk, lheOk, energyOk, coherent;
    /** Vue client. */
    private int sentSignature = Integer.MIN_VALUE;
    private int syncCooldown;

    private ComputeCore(ControllerTile tile, boolean quantum) {
        this.tile = tile;
        this.quantum = quantum;
        this.min = this.max = tile.getBlockPos();
    }

    /** Coeur de calcul du controleur ({@code data_center}, {@code quantum_computer}), sinon null. */
    @Nullable
    public static ComputeCore create(ControllerTile tile) {
        String key = tile.machine.key;
        if ("data_center".equals(key)) {
            return new ComputeCore(tile, false);
        }
        if ("quantum_computer".equals(key)) {
            return new ComputeCore(tile, true);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Reservoirs
    // ------------------------------------------------------------------

    /** Fluide d'entree : eau, caloporteur ou azote liquide (centre de donnees), helium liquide (quantique). */
    public boolean acceptsFluid(FluidStack stack) {
        if (quantum) {
            return stack.getFluid().isSame(PiIndustry.fluid("liquid_helium"));
        }
        return ControllerTile.isCoolant(stack);
    }

    /** Gaz d'entree : helium 3 (quantique). */
    public boolean acceptsGas(GasStack stack) {
        return quantum && stack.getGas() == PiGasTypes.HELIUM_3.get();
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    public void onStructure(Structure.Result s, Level level) {
        boolean moved = !s.min().equals(min) || !s.max().equals(max);
        structure = s;
        //1.0.4-beta (lot 23) : plus de matrices de qubits (bloc retire) : l'ordinateur quantique compte son volume interieur
        units = Structure.units(tile.machine, s);
        min = s.min();
        max = s.max();
        tier = Math.max(0, Math.min(3, s.tier()));
        //1.0.4-beta (lot 15) : plus de groupe froid sur le toit du centre de donnees : le controleur porte l'equivalent des deux groupes
        //froids qu'exigeait le gabarit (ventilateurs vers l'air, alimentes par l'energie des baies) ; l'ordinateur quantique compte ses
        //refrigerateurs a dilution
        coolers = quantum ? 0 : BUILT_IN_COOLERS;
        if (quantum && s.members() != null) {
            for (BlockPos p : s.members()) {
                if (level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof PartBlock part && part.kind == PartBlock.Kind.DILUTION_REFRIGERATOR) {
                    coolers++;
                }
            }
        }
        if (moved) {
            rescan = true;
        }
    }

    /** Structure defaite : plus d'offre. */
    @Override
    public void onUnformed(Level level) {
        structure = null;
        capacity = served = 0;
        load = 0;
        power = 0;
        ComputeNetwork.unregister(level, tile.getBlockPos());
        //Rendus a eteindre chez le client
        tile.syncReactor();
    }

    /** Cables de donnees colles a la structure (faces exterieures seulement), cherches au plus toutes les 10 s. */
    private void scanCables(Level level) {
        rescan = false;
        scan = 200;
        cables.clear();
        Structure.Result s = structure;
        if (s == null || s.members() == null) {
            cables.addAll(ComputeNetwork.adjacentCables(level, tile.getBlockPos()));
            return;
        }
        Set<BlockPos> found = new HashSet<>();
        for (BlockPos p : s.members()) {
            boolean edge = p.getX() == min.getX() || p.getX() == max.getX() || p.getY() == min.getY() || p.getY() == max.getY() || p.getZ() == min.getZ()
                           || p.getZ() == max.getZ();
            if (!edge) {
                continue;
            }
            for (Direction d : Directions.ALL) {
                BlockPos n = p.relative(d);
                if (!s.members().contains(n) && !found.contains(n) && level.isLoaded(n)
                    && LogisticNetworks.isDataCable(level.getBlockState(n))) {
                    found.add(n.immutable());
                }
            }
        }
        for (BlockPos n : ComputeNetwork.adjacentCables(level, tile.getBlockPos())) {
            found.add(n);
        }
        cables.addAll(found);
    }

    // ------------------------------------------------------------------
    // Fonctionnement
    // ------------------------------------------------------------------

    /**
     * Un tick du multibloc forme.
     *
     * @param use     prend de l'energie a la reserve du controleur (faux si elle n'y est pas entierement)
     * @param enabled commande redstone
     *
     * @return l'etat du controleur ({@link ControllerTile#WORKING}...).
     */
    public int tick(LongPredicate use, boolean enabled) {
        Level level = tile.getLevel();
        if (level == null) {
            return ControllerTile.IDLE;
        }
        if (rescan || --scan <= 0) {
            scanCables(level);
        }
        clock++;
        int status = quantum ? tickQuantum(level, use, enabled) : tickDataCenter(level, use, enabled);
        ComputeNetwork.register(level, tile.getBlockPos(), this);
        syncView();
        return status;
    }

    private double energyFactor() {
        return AiChips.energyFactor(tile.aiLevel());
    }

    private int tickDataCenter(Level level, LongPredicate use, boolean enabled) {
        double nominal = Tiers.nominal(tier);
        double t = tile.body.getTemperature();
        throttle = t <= nominal ? 1 : Math.max(0, 1 - (t / nominal - 1) * 5);
        double full = units * RACK_GOPS * (1 << tier);
        long fe = enabled && units > 0 ? Math.round(units * RACK_FE * (1L << 2 * tier) * (0.1 + 0.9 * load) * energyFactor()) : 0;
        energyOk = fe > 0 && use.test(fe);
        power = energyOk ? fe : 0;
        capacity = energyOk ? full * throttle : 0;
        if (!energyOk) {
            served = 0;
            load = 0;
        }
        if (power > 0) {
            tile.body.addHeat(power * HEAT_PER_FE);
            //Groupes froids : ventilateurs vers l'air
            double dT = t - HeatExchange.AMBIENT;
            if (dT > 0 && coolers > 0) {
                double k = coolers * COOLER * (1 << tier);
                tile.body.addHeat(-Math.min(dT * k, dT * tile.body.getHeatCapacity() * 0.5));
            }
        }
        immersion();
        //Telemetrie : proportionnelle au calcul servi
        double made = served / GOPS_PER_TELEMETRY;
        telemetryRate = telemetryRate * 0.95 + made * 20 * 0.05;
        telemetryCarry = Math.min(telemetryCarry + made, 100_000);
        long whole = (long) telemetryCarry;
        if (whole > 0 && clock % 5 == 0) {
            telemetryCarry -= push(level, whole);
            //Rien ne prend la telemetrie : elle se perd (une seconde de reserve au plus)
            telemetryCarry = Math.min(telemetryCarry, made * 20 + 1);
        }
        if (served > 0 && clock % 20 == 0) {
            IndustryEvents.award(level, tile.getBlockPos(), "first_gops");
        }
        if (!enabled || units <= 0) {
            return ControllerTile.IDLE;
        }
        if (!energyOk) {
            return ControllerTile.NO_ENERGY;
        }
        return throttle < 1 ? ControllerTile.OVERHEAT : ControllerTile.WORKING;
    }

    /** Immersion : le fluide d'entree emporte la chaleur jusqu'a 20 K au-dessus de l'ambiante. */
    private void immersion() {
        if (tile.fluidInTanks().isEmpty() || !(tile.fluidInTanks().get(0) instanceof IoTank in) || in.isEmpty()) {
            return;
        }
        double setpoint = HeatExchange.AMBIENT + IMMERSION_DT;
        double t = tile.body.getTemperature();
        if (t <= setpoint) {
            return;
        }
        double want = (t - setpoint) * tile.body.getHeatCapacity() * 0.05;
        FluidStack c = in.getFluid();
        double perMb;
        FluidStack product = FluidStack.EMPTY;
        long steam = 0;
        if (c.getFluid().isSame(Fluids.WATER)) {
            //1.0 (0.19.12) : l'eau bout en vapeur, un gaz (reservoir de vapeur du controleur, ports de gaz)
            perMb = ControllerTile.WATER_J;
            steam = Math.round(Steam.STEAM_PER_WATER);
        } else if (c.getFluid().isSame(PiIndustry.fluid("coolant"))) {
            perMb = ControllerTile.COOLANT_J;
            product = new FluidStack(PiIndustry.fluid("hot_coolant"), 1);
        } else if (c.getFluid().isSame(PiFluids.LIQUID_NITROGEN.get())) {
            perMb = ControllerTile.NITROGEN_J;
        } else {
            return;
        }
        int mb = (int) Math.min(c.getAmount(), Math.ceil(want / perMb));
        IoTank out = !tile.fluidOutTanks().isEmpty() && tile.fluidOutTanks().get(0) instanceof IoTank o ? o : null;
        BasicGasTank vapour = tile.steamOutTank();
        if (!product.isEmpty()) {
            if (out == null || !out.isEmpty() && !out.getFluid().isFluidEqual(product)) {
                return;
            }
            mb = Math.min(mb, Math.max(0, out.getSpace() / product.getAmount()));
        } else if (steam > 0) {
            GasStack held = vapour.getStack();
            if (!held.isEmpty() && !held.isSameGas(PiIndustry.steam(1))) {
                return;
            }
            mb = (int) Math.min(mb, Math.max(0, (vapour.getCapacity() - held.getAmount()) / steam));
        }
        if (mb <= 0) {
            return;
        }
        in.drainInternal(mb, FluidAction.EXECUTE);
        tile.body.addHeat(-mb * perMb);
        if (!product.isEmpty()) {
            out.fillInternal(new FluidStack(product, product.getAmount() * mb), FluidAction.EXECUTE);
        } else if (steam > 0) {
            vapour.insert(PiIndustry.steam(steam * mb), Action.EXECUTE, Automation.INTERNAL);
        }
    }

    /**
     * Pousse la telemetrie dans les reseaux de cables touches ; renvoie la quantite placee. 1.11 : par la fiche du cable logistique (capacite
     * de donnees de sa face tournee vers la structure), comme une machine qui ejecte : le reseau la livre aux sorties de son canal de donnees.
     */
    private long push(Level level, long amount) {
        long left = amount;
        for (BlockPos cable : cables) {
            if (left <= 0) {
                break;
            }
            //1.9 (MAJ RP, bloc 4) : la telemetrie ne passe pas une frontiere de claim refusee
            if (!level.isLoaded(cable) || !ClanApi.canTransfer(level, tile.getBlockPos(), cable)) {
                continue;
            }
            BlockState state = level.getBlockState(cable);
            BlockEntity be = level.getBlockEntity(cable);
            if (!LogisticNetworks.isDataCable(state) || be == null) {
                continue;
            }
            for (Direction d : Directions.ALL) {
                if (left <= 0 || LogisticCableBlock.link(state, d) != LogisticCableBlock.Link.PLUG || !touchesStructure(cable.relative(d))) {
                    continue;
                }
                IDataHandler plug = be.getCapability(PioneersAPI.DATA_HANDLER, d).resolve().orElse(null);
                if (plug != null) {
                    left = plug.insertData(DataStack.of(PiDataTypes.TELEMETRY.get(), left), Action.EXECUTE).getAmount();
                }
            }
        }
        return amount - left;
    }

    /** Bloc de la structure (ou le controleur) : la fiche qui le touche porte la telemetrie. */
    private boolean touchesStructure(BlockPos pos) {
        Structure.Result s = structure;
        return pos.equals(tile.getBlockPos()) || s != null && s.members() != null && s.members().contains(pos);
    }

    private int tickQuantum(Level level, LongPredicate use, boolean enabled) {
        throttle = 1;
        if (clock % 20 == 0) {
            //Une fois par seconde : helium 3 et helium liquide
            heOk = false;
            lheOk = false;
            if (enabled && units > 0) {
                for (BasicGasTank gas : tile.gasInTanks()) {
                    GasStack g = gas.getStack();
                    if (!g.isEmpty() && g.getGas() == PiGasTypes.HELIUM_3.get() && g.getAmount() >= HE3_PER_S) {
                        gas.extract(HE3_PER_S, Action.EXECUTE, Automation.INTERNAL);
                        heOk = true;
                        break;
                    }
                }
                for (FluidTank fluid : tile.fluidInTanks()) {
                    if (fluid instanceof IoTank io && !io.isEmpty() && acceptsFluid(io.getFluid()) && io.getFluidAmount() >= LHE_PER_S) {
                        io.drainInternal(LHE_PER_S, FluidAction.EXECUTE);
                        lheOk = true;
                        break;
                    }
                }
            }
        }
        long fe = enabled && units > 0 ? Math.round(units * MATRIX_FE * (1L << 2 * tier) * energyFactor()) : 0;
        energyOk = fe > 0 && use.test(fe);
        power = energyOk ? fe : 0;
        boolean supplied = energyOk && heOk && lheOk;
        cold = supplied ? Math.min(1, cold + 1.0 / COOL_TICKS) : Math.max(0, cold - 1.0 / WARM_TICKS);
        boolean was = coherent;
        coherent = cold >= 1;
        capacity = coherent ? units * MATRIX_QOPS * (1 << tier) : 0;
        if (!coherent) {
            served = 0;
            load = 0;
        }
        if (coherent && !was) {
            IndustryEvents.award(level, tile.getBlockPos(), "quantum_coherence");
        }
        if (!enabled || units <= 0) {
            return ControllerTile.IDLE;
        }
        if (!energyOk) {
            return ControllerTile.NO_ENERGY;
        }
        return supplied ? ControllerTile.WORKING : ControllerTile.NO_FLUID;
    }

    // ------------------------------------------------------------------
    // Reseau de calcul
    // ------------------------------------------------------------------

    @Override
    public double supplyGops() {
        return quantum ? 0 : capacity;
    }

    @Override
    public double supplyQops() {
        return quantum ? capacity : 0;
    }

    @Override
    public void serve(double gops, double qops) {
        served = quantum ? qops : gops;
        load = capacity <= 0 ? 0 : (float) Math.min(1, served / capacity);
    }

    @Override
    public Collection<BlockPos> cables() {
        return cables;
    }

    @Nullable
    @Override
    public BlockPos boxMin() {
        return structure == null ? null : min;
    }

    @Nullable
    @Override
    public BlockPos boxMax() {
        return structure == null ? null : max;
    }

    // ------------------------------------------------------------------
    // Lecture (interface, noyau d'usine)
    // ------------------------------------------------------------------

    /** Offre courante (GOPS ou QOPS). */
    public double capacity() {
        return capacity;
    }

    /** Calcul servi (GOPS ou QOPS). */
    public double served() {
        return served;
    }

    /** Offre sans bridage (GOPS ou QOPS). */
    public double fullCapacity() {
        return units * (quantum ? MATRIX_QOPS : RACK_GOPS) * (1 << tier);
    }

    /** Temperature de la chambre de melange (K) : de l'ambiante aux 10 mK. */
    public double mixingKelvin() {
        double w = 1 - cold;
        return 0.010 + (HeatExchange.AMBIENT - 0.010) * w * w * w;
    }

    public boolean coherent() {
        return coherent;
    }

    public long power() {
        return power;
    }

    /** Valeurs d'interface (machineValue 61..70). */
    public long value(int index) {
        return switch (index) {
            case 61 -> quantum ? 2 : 1;
            case 62 -> Math.round(capacity * 10);
            case 63 -> Math.round(served * 10);
            case 64 -> Math.round(fullCapacity() * 10);
            case 65 -> Math.round(load * 1000);
            case 66 -> Math.round((quantum ? cold : throttle) * 1000);
            case 67 -> units | (long) coolers << 8;
            case 68 -> power;
            case 69 -> quantum ? Math.round(mixingKelvin() * 1000) : Math.round(telemetryRate * 10);
            case 70 -> (heOk ? 1 : 0) | (lheOk ? 2 : 0) | (energyOk ? 4 : 0);
            default -> 0;
        };
    }

    // ------------------------------------------------------------------
    // Vue (rendus)
    // ------------------------------------------------------------------

    @Override
    public int kind() {
        return quantum ? 2 : 1;
    }

    @Override
    public float load() {
        return load;
    }

    @Override
    public float heat() {
        if (quantum) {
            return (float) (1 - cold);
        }
        double nominal = Tiers.nominal(tier);
        return (float) Math.max(0, Math.min(1, (tile.body.getTemperature() - HeatExchange.AMBIENT) / (nominal * 1.2 - HeatExchange.AMBIENT)));
    }

    @Override
    public int units() {
        return units;
    }

    @Override
    public BlockPos min() {
        return min;
    }

    @Override
    public BlockPos max() {
        return max;
    }

    /** Renvoi de la vue au client quand elle change (au plus 4 fois par seconde). */
    private void syncView() {
        if (syncCooldown > 0) {
            syncCooldown--;
            return;
        }
        int sig = Math.round(load * 20) | Math.round(heat() * 20) << 6 | units << 12;
        if (sig != sentSignature) {
            sentSignature = sig;
            syncCooldown = 5;
            tile.syncReactor();
        }
    }

    public CompoundTag writeView() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("k", (byte) kind());
        tag.putFloat("l", load);
        tag.putFloat("h", heat());
        tag.putInt("u", units);
        tag.putLong("min", min.asLong());
        tag.putLong("max", max.asLong());
        return tag;
    }

    /** Vue lue cote client. */
    public record ClientView(int kind, float load, float heat, int units, BlockPos min, BlockPos max) implements ComputeView {

        public static ClientView read(CompoundTag tag) {
            return new ClientView(tag.getByte("k"), tag.getFloat("l"), tag.getFloat("h"), tag.getInt("u"), BlockPos.of(tag.getLong("min")),
                  BlockPos.of(tag.getLong("max")));
        }
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("cold", cold);
        tag.putDouble("telemetry", telemetryCarry);
        return tag;
    }

    public void load(CompoundTag tag) {
        cold = tag.getDouble("cold");
        telemetryCarry = tag.getDouble("telemetry");
        coherent = cold >= 1;
    }

    // ------------------------------------------------------------------
    // API Multibloc (lot 6) : coeur declare par la fiche (MultiblockLogic) ; memes cles, memes indices d'avant (61..70)
    // ------------------------------------------------------------------

    @Override
    public String saveKey() {
        return "compute";
    }

    @Override
    public int work(LongPredicate use, boolean enabled) {
        //Leur propre logique (offre de calcul, bridage, cryogenie)
        return tick(use, enabled);
    }

    @Override
    public boolean ownsFluids() {
        return true;
    }

    @Override
    public boolean ownsGases() {
        return true;
    }

    @Override
    public boolean acceptsGas(int tank, GasStack stack, @Nullable Level level) {
        return level == null || acceptsGas(stack);
    }

    @Override
    public int itemKinds() {
        return 1;
    }

    @Override
    public void writeClient(CompoundTag tag, boolean formed) {
        //0.17.2 : vue du calcul pour les rendus (absente si la structure n'est pas formee)
        if (formed) {
            tag.put("computeView", writeView());
        } else {
            tag.putBoolean("computeOff", true);
        }
    }
}

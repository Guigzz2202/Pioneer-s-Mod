package fr.pioneers.tech.industry.multi;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.api.machine.resource.ResourceRows;
import fr.pioneers.api.heat.IHeatHandler;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.ToolBay;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockLayout;
import fr.pioneers.api.multiblock.MultiblockLogic;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.api.multiblock.MultiblockValues;
import fr.pioneers.api.multiblock.StructureMatcher;
import fr.pioneers.api.pressure.IPressureHandler;
import fr.pioneers.tech.bronze.steam.Steam;
import fr.pioneers.tech.compute.AiChips;
import fr.pioneers.tech.compute.ComputeCore;
import fr.pioneers.tech.compute.ComputeView;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.gas.GasTankHandler;
import fr.pioneers.core.heat.HeatExchange;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.core.recipe.RecipeLock;
import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.core.recipe.RecipeSources;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.Sided;
import fr.pioneers.core.data.BasicDataTank;
import fr.pioneers.tech.fusion.FusionCore;
import fr.pioneers.world.geology.Deposits;
import fr.pioneers.tech.industry.Impurity;
import fr.pioneers.tech.industry.IndustryEvents;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.IoMigration;
import fr.pioneers.tech.industry.IoTank;
import fr.pioneers.tech.industry.TankSync;
import fr.pioneers.tech.industry.TankView;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.industry.recipe.RecipeMatch;
import fr.pioneers.tech.industry.recipe.Remanents;
import fr.pioneers.tech.nuclear.FissionCore;
import fr.pioneers.tech.nuclear.ReactorControl;
import fr.pioneers.tech.nuclear.ReactorView;
import fr.pioneers.core.registries.PiFluids;
import fr.pioneers.tech.industry.PiIndustry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Controleur d'un multibloc industriel. Une fois la structure formee ({@link Structure}) : tier = plus faible carcasse, parallelisme =
 * 2 operations par module (plafond du tier). Il lance des lots : une recette, {@code k} operations a la fois (entrees prises au depart),
 * 80 % du temps et 75 % de l'energie par operation d'une machine d'atelier du meme tier, tous les sous-produits.
 * <p>
 * Chaleur : chaque operation chauffe le multibloc ({@code heat} de la machine x 1,5 par tier x 4 ; 1.10.1, avant x 2 par tier) ; il perd de la chaleur a l'air (par son
 * enveloppe), par ses ports de chaleur (cables, radiateurs, tour de refroidissement) et en usant son caloporteur (eau -> vapeur, caloporteur
 * -> caloporteur chaud, azote liquide -> azote). Au-dela de la temperature nominale des carcasses il ralentit (jusqu'a 50 % a +20 %), puis
 * s'arrete ; a +35 % pendant 5 s une carcasse brule et la structure se defait.
 */
public class ControllerTile extends MachineTile implements RecipeMatch.Buffers, RecipeLockable, MultiblockPort {

    public static final int UNFORMED = 0, IDLE = 1, WORKING = 2, NO_ENERGY = 3, FULL = 4, NO_RECIPE = 5, OVERHEAT = 6, TIER = 7, NO_DEPOSIT = 8,
          EXHAUSTED = 9, TOO_DEEP = 10, NO_FLUID = 11, RESEARCH = 12, NO_TUBES = 13, MEGA_LOCKED = 14, MKV_CASINGS = 15;
    public static final double HEAT_SCALE = 4, AMBIENT_PER_SHELL = 0.2, CAPACITY_PER_BLOCK = 1_000;
    /** Caloporteurs : joules absorbes par mB. */
    public static final double WATER_J = 400, COOLANT_J = 1_200, NITROGEN_J = 3_000;
    /** Foreuse : duree d'une extraction (ticks, tier basique). */
    public static final int DRILL_TIME = 160;

    /** Reserve d'energie dont la capacite suit la structure formee. */
    private static final class Capacity implements LongSupplier {

        long value = Tiers.BASE_POWER * 200;

        @Override
        public long getAsLong() {
            return value;
        }
    }

    /** Corps thermique du multibloc (capacite selon le volume). */
    public final class Body implements IHeatHandler {

        double temperature = HeatExchange.AMBIENT;

        @Override
        public double getTemperature() {
            return temperature;
        }

        @Override
        public double getHeatCapacity() {
            //Lot 6 : capacite imposee par le coeur de la fiche (reacteur : la masse du coeur ; fusion defaite : la derniere masse)
            double own = unit == null ? -1 : unit.heatCapacity(lifecycle.structure() != null);
            if (own >= 0) {
                return own;
            }
            if (lifecycle.structure() == null) {
                return 10_000;
            }
            return Math.max(10_000, lifecycle.structure().volume() * CAPACITY_PER_BLOCK);
        }

        @Override
        public double getConductance() {
            return 60.0 * (1L << (2 * tier()));
        }

        @Override
        public void addHeat(double joules) {
            temperature = Math.max(0, temperature + joules / getHeatCapacity());
        }
    }

    public final IndustryMachine machine;
    /**
     * API Multibloc (lot 5) : disposition de l'interface v2 commune (grammaire des machines compactes : panneau 196, inventaire dans le cadre,
     * onglets a droite, carre du selecteur, onglet Structure) ; lot 6 : tous les controleurs (reacteurs, fusion et calcul compris, avec leurs
     * parties speciales).
     */
    public final MultiblockLayout v2;
    private final Capacity capacity;
    final List<ItemSlot> inputs = new ArrayList<>(), outputs = new ArrayList<>();
    /** 1.0.5-beta (lot 44, retour 62) : case « Outil » des remanents ({@link ToolBay}), DERNIERE case ; null sans remanent. */
    @Nullable
    final ItemSlot tool;
    final List<FluidTank> fluidIn = new ArrayList<>(), fluidOut = new ArrayList<>();
    final List<BasicGasTank> gasIn = new ArrayList<>(), gasOut = new ArrayList<>();
    /** 0.20.34 : reservoirs de donnees (synthetiseur), remplis par les cables de donnees du port de donnees ; un type par reservoir. */
    private final List<BasicDataTank> dataIn = new ArrayList<>();
    /** Contenance d'un reservoir de donnees (Mo). */
    public static final long DATA_TANK = 64_000_000L;
    final IoTank coolant, coolantOut;
    /** 1.0 (0.19.12) : vapeur d'eau (gaz) du caloporteur eau ; declaree en DERNIER (ordre de sauvegarde des reservoirs de gaz). */
    final BasicGasTank steamOut;
    /**
     * MAJ Recettes (lot 9a) : controleur relu d'un monde d'avant la MAJ dont l'io a change ({@link IoMigration}) : NBT a resauver et objets
     * restes sans place, rendus au premier tick.
     */
    private boolean ioMigrated;
    private final List<ItemStack> ioOverflow = new ArrayList<>();
    public final Body body = new Body();
    /**
     * API Multibloc (lot 6) : COEUR PROPRE declare par la fiche ({@code MultiblockSpec.process} : reacteur {@link FissionCore}, fusion
     * {@link FusionCore}, calcul {@link ComputeCore}, plate-forme {@link OffshoreRisers}), fabrique par {@link CoreLogics} ; null pour les
     * multiblocs a procede. Le controleur appelle ses crochets ({@link MultiblockLogic}) au lieu d'aiguiller sur le genre de la machine.
     */
    @Nullable
    final MultiblockLogic unit;
    /** Client : colonnes et tubes de la plate-forme, pour le rendu. */
    @Nullable
    private OffshoreRisers.View clientOffshore;
    @Nullable
    private ComputeView clientCompute;
    /** 0.17.2 : puce d'IA installee (tous les multiblocs) et bonus d'un noyau d'usine superviseur (niveau, fin de validite). */
    private ItemStack aiChip = ItemStack.EMPTY;
    public final MkvCoreSlot mkvCore = new MkvCoreSlot(this::setChanged);
    private int supervisedLevel, clientChip;
    private long supervisedUntil;
    /** 0.17.1 : copie client de la vue du reacteur (rendus du coeur). */
    @Nullable
    private ReactorView clientView;
    /**
     * API Multibloc (lot 5) : CYCLE DE VIE commun (verifications, formation, rupture, peinture, machine interieure, ports, reprise apres
     * chargement) : {@link MultiblockCore}.
     */
    final MultiblockCore lifecycle = new MultiblockCore(new MultiblockCore.Host() {
        @Override
        public Level level() {
            return level;
        }

        @Override
        public BlockPos pos() {
            return worldPosition;
        }

        @Override
        public IndustryStyle style() {
            return machine.style;
        }

        @Override
        public BigInterior.Kind interior() {
            return BigInterior.kind(machine.key);
        }

        @Override
        public int interiorTint(BigInterior.Kind kind) {
            return BigInterior.tint(machine.key, kind);
        }

        @Override
        public String machineKey() {
            return machine.key;
        }

        @Override
        public boolean removed() {
            return isRemoved();
        }

        @Override
        public void changed() {
            setChanged();
        }

        @Override
        public void markSync() {
            ControllerTile.this.markSync();
        }
    });
    int status = UNFORMED;
    private boolean unloading;
    /** 0.20.46 (audit) : envoi des reservoirs au client economise (interface ouverte : comme avant). */
    final TankSync tankSync = new TankSync();
    /**
     * 0.19.14 : machine interieure d'un seul tenant ({@link BigInterior}) : copie client (lot 5 : la vue du serveur, sauvee pour demasquer
     * les modules si la structure ne se reforme pas au chargement, est tenue par {@link MultiblockCore#big()}).
     */
    @Nullable
    private BigInterior.View clientBig;
    /** API Multibloc (lot 5) : PROCEDE du multibloc (meme base que les unites des machines compactes) : {@link MultiProcess}. */
    final MultiProcess process = new MultiProcess(this);
    /** API Multibloc (lot 5) : CHALEUR du multibloc (corps, caloporteur, ports de chaleur, surchauffe) : {@link ThermalUnit}. */
    final ThermalUnit thermal = new ThermalUnit(this);
    /** MAJ Recettes (lot 7) : recette verrouillee par le selecteur (case « Recette ») ; lot 5 : tier demande dans {@link MultiProcess}. */
    final RecipeLock lock = new RecipeLock();
    /** Air comprime de procede, rempli par les ports de pression. */
    final ProcessAir air = new ProcessAir(this);

    /** Plage de confort des fermes (K) : hors d'elle, elles tournent a 50 %. */
    public static final double FARM_MIN = 285, FARM_MAX = 310;
    /** Part maximale de l'energie d'une operation remplacee par de la chaleur. */
    public static final double HEAT_SHARE = 0.75;

    public ControllerTile(BlockPos pos, BlockState state) {
        this(pos, state, new Capacity());
    }

    private ControllerTile(BlockPos pos, BlockState state, Capacity capacity) {
        super(PiIndustry.CONTROLLER_TILE.get(), pos, state, capacity, Integer.MAX_VALUE, true);
        this.capacity = capacity;
        machine = ((ControllerBlock) state.getBlock()).machine;
        //Lot 6 : TOUS les controleurs a l'interface v2 (reacteurs, fusion et calcul compris)
        v2 = MultiblockLayout.of(machine);
        unit = CoreLogics.create(this);
        for (int i = 0; i < v2.inputs; i++) {
            int[] p = v2.itemIn.get(i);
            inputs.add(slot(new ItemSlot(() -> 64, this::acceptsItem, (s, a) -> true, (s, a) -> !a.external(), this::changed, p[0], p[1])));
        }
        for (int i = 0; i < v2.outputs; i++) {
            int[] p = v2.itemOut.get(i);
            outputs.add(slot(new ItemSlot(() -> 64, s -> true, (s, a) -> a == Automation.INTERNAL, (s, a) -> true, this::changed, p[0], p[1])));
        }
        boolean tank = machine.kind == IndustryMachine.Kind.TANK;
        if (tank) {
            //Reservoir : un seul stock, rempli et vide par tous ses ports ; contenance selon le volume interieur et le tier
            if (machine.fluidsIn > 0) {
                fluidIn.add(fluidTank(new FluidTank(64_000, f -> true) {
                    @Override
                    protected void onContentsChanged() {
                        tanksChanged();
                    }

                    @Override
                    public int fill(FluidStack resource, FluidAction action) {
                        //Reservoir rapetisse (structure reconstruite plus petite) : plus rien n'entre tant qu'il deborde
                        return getFluidAmount() >= getCapacity() ? 0 : super.fill(resource, action);
                    }
                }));
            }
            if (machine.gasesIn > 0) {
                gasIn.add(gasTank(BasicGasTank.of(256_000L, this::tanksChanged)));
            }
        } else {
            for (int i = 0; i < machine.fluidsIn; i++) {
                int k = i;   //1.0.6 : jauges partagees liquide ou gaz (ResourceRows)
                fluidIn.add(fluidTank(new IoTank(32_000, false, f -> acceptsFluid(f) && ResourceRows.fluidOk(machine, fluidIn, gasIn, k, f, lockedIfSet()),
                      this::tanksChanged)));
            }
        }
        coolant = (IoTank) fluidTank(new IoTank(32_000, false, ControllerTile::isCoolant, this::tanksChanged));
        for (int i = 0; i < (tank ? 0 : machine.fluidsOut); i++) {
            fluidOut.add(fluidTank(new IoTank(32_000, true, f -> true, this::tanksChanged)));
        }
        coolantOut = (IoTank) fluidTank(new IoTank(32_000, true, f -> true, this::tanksChanged));
        for (int i = 0; i < (tank ? 0 : machine.gasesIn); i++) {
            //0.17.2 : fusion : un gaz par reservoir (deuterium ; tritium ou helium 3)
            int gasSlot = i;
            gasIn.add(gasTank(BasicGasTank.input(4_096_000L, g -> unit != null && unit.ownsGases() ? unit.acceptsGas(gasSlot, g, level)
                  : level == null || lockAllowsGas(g, () -> IndustryRecipes.acceptsGas(level, machine, g))
                    && ResourceRows.gasOk(machine, fluidIn, gasIn, gasSlot, g, lockedIfSet()), this::tanksChanged)));
        }
        for (int i = 0; i < (tank ? 0 : machine.gasesOut); i++) {
            gasOut.add(gasTank(BasicGasTank.output(4_096_000L, this::tanksChanged)));
        }
        steamOut = gasTank(BasicGasTank.output(STEAM_TANK, this::tanksChanged));
        for (int i = 0; i < dataTanks(machine); i++) {
            dataIn.add(BasicDataTank.of(DATA_TANK, type -> level == null || IndustryRecipes.acceptsData(level, machine, type),
                  (type, a) -> true, (type, a) -> a != Automation.EXTERNAL, this::tanksChanged));
        }
        tool = v2.tool ? slot(ToolBay.slot(machine.key, this, this::changed, v2.toolX, v2.toolY)) : null;
    }

    /** 0.20.34 : machines qui consomment des donnees (nombre de reservoirs). */
    private static int dataTanks(IndustryMachine machine) {
        return "synthesizer".equals(machine.key) ? 3 : 0;
    }

    @Override
    public List<BasicDataTank> dataInputs() {
        return dataIn;
    }

    /**
     * 0.20.34 : entree des donnees par le port de donnees : un reservoir par type (celui qui en contient deja, sinon un vide), seulement les
     * types qui servent aux recettes de la machine. Rend le reste.
     */
    public DataStack insertData(DataStack stack, Action action) {
        return BasicDataTank.insertInto(dataIn, stack, action);
    }

    /** Reservoir de vapeur du caloporteur : 1 mB d'eau = 8 mB de vapeur, il suit le reservoir de caloporteur. */
    private static final long STEAM_TANK = 32_000L * (long) Steam.STEAM_PER_WATER;

    /** Reservoir : volume interieur de la structure formee (blocs). */
    private long interior() {
        Structure.Result s = lifecycle.structure();
        return s == null ? 1 : Math.max(1, (long) (s.width() - 2) * (s.height() - 2) * (s.depth() - 2));
    }

    private void resizeStore() {
        if (machine.kind != IndustryMachine.Kind.TANK) {
            return;
        }
        long scale = 1L << (2 * tier());
        if (!fluidIn.isEmpty()) {
            fluidIn.get(0).setCapacity((int) Math.min(Integer.MAX_VALUE, interior() * 64_000L * scale));
        }
        if (!gasIn.isEmpty()) {
            gasIn.get(0).setCapacity(Math.min(Long.MAX_VALUE / 4, interior() * 256_000L * scale));
        }
    }

    /** Signal du comparateur : remplissage d'un reservoir, sinon 15 en marche. */
    public int comparator() {
        int own = unit == null ? -1 : unit.comparator();
        if (own >= 0) {
            return own;
        }
        if (machine.kind == IndustryMachine.Kind.TANK) {
            double f = !fluidIn.isEmpty() ? fluidIn.get(0).getFluidAmount() / (double) Math.max(1, fluidIn.get(0).getCapacity())
                  : !gasIn.isEmpty() ? gasIn.get(0).getStack().getAmount() / (double) Math.max(1, gasIn.get(0).getCapacity()) : 0;
            return f <= 0 ? 0 : Math.min(15, 1 + (int) Math.floor(f * 14));
        }
        return status == WORKING ? 15 : 0;
    }

    // ------------------------------------------------------------------
    // Capacites des reservoirs (selon le tier forme)
    // ------------------------------------------------------------------

    /** Contenance d'un reservoir de fluide (mB) : 32 000 x 4 par tier. */
    public int tankCapacity() {
        return 32_000 << (2 * tier());
    }

    @Override
    protected boolean producesEnergy() {
        //Appele par le constructeur parent : le champ machine n'est pas encore pose
        //0.17.1 : le reacteur a fragments pousse son electricite par les ports d'energie
        //0.17.2 : le stellarator aussi (electricite directe)
        return getBlockState().getBlock() instanceof ControllerBlock b && (b.machine.isGenerator() || "fragment_reactor".equals(b.machine.key)
                                                                            || "stellarator".equals(b.machine.key));
    }

    /** L'interface s'ouvre aussi depuis les ports : portee selon la taille de la structure. */
    @Override
    public double guiReach() {
        Structure.Result s = lifecycle.structure();
        return s == null ? 8 : 8 + Math.max(s.width(), Math.max(s.height(), s.depth()));
    }

    private void changed() {
        process.dirty = true;
        setChanged();
    }

    private void tanksChanged() {
        process.dirty = true;
        setChanged();
        //0.20.46 (audit) : plus de markSync a chaque variation (un paquet toutes les 10 ticks par multibloc en marche) : voir tick()
        tankSync.changed();
    }

    private boolean acceptsItem(ItemStack stack) {
        if (unit != null && unit.ownsItems()) {
            //Lot 6 : le coeur de la fiche choisit (reacteur : combustible, cibles, couvertures ; fusion : billes de lithium)
            return unit.acceptsItem(stack);
        }
        //MAJ Recettes (lot 7) : verrouillee, la machine ne prend que les ingredients de sa recette ; lot 44 : jamais un remanent (case Outil)
        return (tool == null || !Remanents.isTool(machine.key, stack)) && (level == null || lockAllows(stack, () -> IndustryRecipes.accepts(level, machine, stack)));
    }

    private boolean acceptsFluid(FluidStack stack) {
        if (level == null) {
            return true;
        }
        if (unit != null && unit.ownsFluids()) {
            //Lot 6 : le coeur de la fiche choisit (sodium du MKII, helium liquide des bobines, eau d'immersion du calcul...)
            return unit.acceptsFluid(stack);
        }
        if (machine.kind == IndustryMachine.Kind.WELL) {
            //Injection : eau ou boue de forage (1.0 : la vapeur est un gaz, elle ne s'injecte plus)
            return stack.getFluid().is(FluidTags.WATER) || stack.getFluid().isSame(PiIndustry.fluid("drilling_mud"));
        }
        if (machine.kind == IndustryMachine.Kind.DRILL) {
            //Eau de forage
            return stack.getFluid().isSame(Fluids.WATER);
        }
        RecipeChoice locked = lockedChoice();
        if (locked != null) {
            //MAJ Recettes (lot 7) : seulement les fluides de la recette verrouillee (le caloporteur a son propre reservoir)
            return locked.takesFluid(stack);
        }
        for (IndustrialRecipe r : IndustryRecipes.of(level, machine)) {
            for (IndustrialRecipe.FluidIn in : r.fluids()) {
                if (in.test(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isCoolant(FluidStack stack) {
        Fluid f = stack.getFluid();
        return f.isSame(Fluids.WATER) || f.isSame(PiIndustry.fluid("coolant")) || f.isSame(PiFluids.LIQUID_NITROGEN.get());
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    @Override
    public void requestCheck() {
        lifecycle.requestCheck();
    }

    @Override
    public double drawFactor() {
        return ownerFactor("salvager.extra_draws");
    }

    @Override
    public int extraRolls() {
        return tier();
    }

    public boolean formed() {
        return lifecycle.structure() != null;
    }

    @Nullable
    public Structure.Result structure() {
        return lifecycle.structure();
    }

    public int tier() {
        return lifecycle.structure() == null ? 0 : mkvCore.tier(lifecycle.structure().tier(), machine.key);
    }

    /** Operations en parallele permises par la structure. */
    public int parallel() {
        if (lifecycle.structure() == null) {
            return 0;
        }
        //1.0 : le parallelisme ne depend que du tier (les modules sont obligatoires partout a l'interieur)
        if (machine.freeform) {
            //0.20.37 : seules les colonnes arrivees au fond travaillent
            OffshoreRisers risers = offshore();
            int down = risers == null ? lifecycle.structure().modules() : risers.complete().size();
            return Math.min(Tiers.parallelCap(tier()), Math.max(1, down * Structure.PER_MODULE));
        }
        return Tiers.parallelCap(tier());
    }

    /** Temperature nominale des carcasses du tier. 1.0.4-beta (lot 15) : plus de bobine de chauffe (+5 % chacune) ni de bloc d'ingenierie. */
    public double nominal() {
        return Tiers.nominal(tier());
    }

    /** 1.0.4-beta (lot 15) : faux pour un controleur sauve avant le lot (carcasses des pieces retirees remises au tier : PartMigration). */
    boolean partsMigrated = true;

    private void revalidate(boolean event) {
        partsMigrated = partsMigrated || PartMigration.run(this);
        Structure.Result result = StructureMatcher.match(level, worldPosition, facing(), machine);
        if (result.formed() && !lifecycle.formed() && level instanceof net.minecraft.server.level.ServerLevel sl && owner() != null) {
            //1.9 (MAJ RP) : un multibloc forme = une structure batie (une fois par jour et par controleur)
            net.minecraft.server.level.ServerPlayer p = sl.getServer().getPlayerList().getPlayer(owner());
            if (p != null && p.level() == sl && p.distanceToSqr(worldPosition.getCenter()) < 64 * 64) {
                fr.pioneers.core.profile.PioneerApi.gain(p, "building.structure", 1, worldPosition);
            }
        }
        lifecycle.apply(result, event, hooks);
    }

    /** Lot 5 : ce que la formation et la rupture changent au controleur lui-meme (ordre d'avant le lot 5). */
    private final MultiblockCore.Hooks hooks = new MultiblockCore.Hooks() {
        @Override
        public void formed(Structure.Result result) {
            capacity.value = Math.max(Tiers.BASE_POWER * 200, Math.round(parallel() * Tiers.power(tier()) * Tiers.INDUSTRIAL_ENERGY * 200));
            if ("gravity_compressor".equals(machine.key) || "synthesizer".equals(machine.key)) {
                //0.20.34 : recettes a energie totale (jusqu'a 1 GFE par operation) : reserve de 4 GFE
                capacity.value = Math.max(capacity.value, 4_000_000_000L);
            }
            if (unit != null && unit.reserve() > 0) {
                //Reacteur a fragments : reserve pour l'allumage du champ et la production ; 0.17.2 : fusion : chauffage, bobines, electricite
                capacity.value = Math.max(capacity.value, unit.reserve());
            }
            if (coolant.getCapacity() != tankCapacity()) {
                for (FluidTank t : getFluidTanks()) {
                    if (machine.kind != IndustryMachine.Kind.TANK || !fluidIn.contains(t)) {
                        t.setCapacity(tankCapacity());
                    }
                }
                steamOut.setCapacity((long) tankCapacity() * (long) Steam.STEAM_PER_WATER);
                markSync();
            }
            resizeStore();
            if (unit != null) {
                //Lot 6 : coeur de la fiche (reacteur, fusion, calcul, colonnes de la plate-forme)
                unit.onStructure(result, level);
            }
        }

        @Override
        public void firstFormed() {
            IndustryEvents.formed(level, worldPosition, machine, tier());
        }

        @Override
        public void unformed() {
            if (unit != null) {
                unit.onUnformed(level);
            }
        }

        @Override
        public void formChanged() {
            setChanged();
            if (unit != null && unit.syncOnFormChange()) {
                markSync();
            }
        }
    };

    /** 0.20.37 : client : colonnes et tubes de la plate-forme offshore (null ailleurs). */
    @Nullable
    public OffshoreRisers.View offshoreView() {
        return clientOffshore;
    }

    /** Serveur : structure formee (null sinon). */
    @Nullable
    Structure.Result structureResult() {
        return lifecycle.structure();
    }

    /** 0.20.37 : colonnes ou stock de tubes changes : sauvegarde et synchro du rendu. */
    void offshoreChanged() {
        setChanged();
        markSync();
    }

    /** 0.19.14 : machine interieure (client : copie recue ; serveur : vue de la structure formee ; null sinon). */
    @Nullable
    public BigInterior.View bigView() {
        return level != null && level.isClientSide ? clientBig : lifecycle.big();
    }

    @Override
    protected void markWorking(boolean working) {
        super.markWorking(working);
        BlockState state = getBlockState();
        if (state.hasProperty(ControllerBlock.ACTIVE)) {
            lifecycle.working(state.getValue(ControllerBlock.ACTIVE));
        }
    }

    // ------------------------------------------------------------------
    // Buffers
    // ------------------------------------------------------------------

    @Override
    public List<ItemSlot> itemInputs() {
        return inputs;
    }

    @Override
    public List<ItemSlot> itemOutputs() {
        return outputs;
    }

    @Override
    public List<ItemSlot> toolSlots() {
        return tool == null ? List.of() : List.of(tool);
    }

    @Override
    public void spill(ItemStack stack) {
        ioOverflow.add(stack.copy());   // 1.0.5-beta (lot 44) : outil rendu sans place : au sol au prochain tick (rien ne se perd)
        ioMigrated = true;
    }

    @Override
    public List<FluidTank> fluidInputs() {
        return fluidIn;
    }

    @Override
    public List<FluidTank> fluidOutputs() {
        return fluidOut;
    }

    @Override
    public List<BasicGasTank> gasInputs() {
        return gasIn;
    }

    @Override
    public List<BasicGasTank> gasOutputs() {
        return gasOut;
    }

    // ------------------------------------------------------------------
    // Fonctionnement
    // ------------------------------------------------------------------

    List<IndustrialRecipe> recipes() {
        return IndustryRecipes.of(level, machine);
    }

    int impurity(IndustrialRecipe r) {
        return Impurity.result(r.impurity(), tier(), r.tier(), true);
    }

    /** Chaleur produite par operation et par tick, par tier (x1,5 : 1 / 1,5 / 2,25 / 3,375). */
    private static final double[] HEAT_TIER = {1, 1.5, 2.25, 3.375};

    /**
     * Chaleur produite par operation (J/t). 1.10.1 (Guillaume : « la production de chaleur est egalement demente ») : x1,5 par tier au lieu
     * de x2, avec le parallelisme 4 / 8 / 16 / 32 : a pleine charge, un multibloc lent (broyeur, tamis, chaleur <= 60) tient sans
     * refroidissement jusqu'au MKIV ; aucun n'en demande en MKI et MKII ; au MKIV, le plus chaud (four a arc) demande ~320 mB/t d'eau,
     * contre ~7 300 avant (tableau : CHANGELOG 1.10.1, banc scratchpad lotA/heat).
     */
    public double heatPerOp() {
        return machine.heat * HEAT_TIER[tier()] * HEAT_SCALE;
    }

    /** Climat des fermes : 1 dans la plage de confort, 0,5 sinon (jamais d'arret) ; lot 5 : {@link ThermalUnit}. */
    public double climateFactor() {
        return thermal.climateFactor();
    }

    /** 0.17.2 : brule {@code n} carcasses de la structure (disruption d'un plasma de fusion) ; lot 5 : {@link ThermalUnit}. */
    public void scorchCasings(int n) {
        thermal.scorchCasings(n);
    }

    /** Temperature de travail (chaleur recue au lieu d'electricite) ; lot 5 : {@link ThermalUnit}. */
    double workTemperature(@Nullable IndustrialRecipe r) {
        return thermal.workTemperature(r);
    }

    /** Vitesse selon la temperature : 1 jusqu'a la nominale, 0,5 a +20 %. */
    public double thermalFactor() {
        double nominal = nominal();
        double t = body.temperature;
        if (t <= nominal) {
            return 1;
        }
        return Math.max(0.5, 1 - (t / nominal - 1) * 2.5);
    }

    @Override
    protected void tick() {
        if (ioMigrated) {
            ioMigrated = false;
            IoMigration.release(level, worldPosition, ioOverflow);
            setChanged();
        }
        if (tankSync.due(this, getFluidTanks(), getGasTanks())) {
            markSync();
        }
        if (level.getGameTime() % 100 == 17) {
            dropMissingLock();                                            // 1.11.1 : verrou d'une recette partie ailleurs
        }
        boolean[] event = new boolean[1];
        if (lifecycle.due(event)) {
            revalidate(event[0]);
        }
        if (lifecycle.structure() == null) {
            status = UNFORMED;
            if (unit != null) {
                //Reacteur defait en marche : la reaction continue ; tore defait : un plasma chaud se perd d'un coup
                unit.unformedTick();
            }
            HeatExchange.ambient(body, 20);
            markWorking(false);
            return;
        }
        thermal.tick();
        ports();
        if (unit != null) {
            //0.20.37 : la plate-forme descend elle-meme ses tubes
            unit.structureTick(level);
        }
        //Credit de chaleur : jamais plus que la chaleur du corps au-dessus de l'ambiante ; debits lisses pour l'interface
        thermal.heatCredit = Math.max(0, Math.min(thermal.heatCredit, Math.max(0, body.temperature - thermal.ambient) * body.getHeatCapacity()));
        thermal.heatInRate = thermal.heatInRate * 0.95 + thermal.tickHeatIn * 0.05;
        thermal.tickHeatIn = 0;
        thermal.savedRate *= 0.95;
        int was = status;
        boolean working = false;
        double nominal = nominal();
        if (unit != null && unit.drivesWork()) {
            //Lot 6 : coeur de la fiche (reacteur : barres, arret d'urgence, fusion du coeur ; fusion : plasma, bobines, disruptions ;
            //calcul : offre, bridage, cryogenie) ; jamais d'arret OVERHEAT commun
            status = unit.work(this::use, canWork());
            working = status == WORKING || status == OVERHEAT;
            if (lifecycle.structure() == null) {
                //La reaction a defait la structure (fusion du coeur)
                return;
            }
        } else if (status == OVERHEAT && body.temperature > nominal) {
            //Arret de securite tant que la temperature n'est pas redescendue sous la nominale
            working = false;
        } else if (body.temperature > nominal * 1.2) {
            status = OVERHEAT;
        } else {
            //Lot 5 : procede du multibloc (lots de recettes, generateur, puits, pompe, foreuse) : unite MultiProcess
            working = process.work();
        }
        if (was != status) {
            setChanged();
        }
        markWorking(working);
    }

    boolean allEmpty() {
        return inputs.stream().allMatch(ItemSlot::isEmpty) && fluidIn.stream().allMatch(FluidTank::isEmpty) && gasIn.stream().allMatch(t -> t.getStack().isEmpty());
    }

    // ------------------------------------------------------------------
    // Puits et pompes (age de la chimie)
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Foreuse
    // ------------------------------------------------------------------

    public Component depositName() {
        if (level == null || level.isClientSide) {
            return Component.empty();
        }
        Deposits.Deposit deposit = Deposits.at((ServerLevel) level, worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return deposit == null ? Component.empty() : Component.translatable(deposit.type().translationKey());
    }

    // ------------------------------------------------------------------
    // Chaleur et caloporteur
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Ports : chaleur a chaque tick, sorties poussees toutes les 10 ticks (0.20.40 : la vapeur passe par les cables a gaz, comme tout gaz)
    // ------------------------------------------------------------------

    private void ports() {
        Structure.Result s = lifecycle.structure();
        boolean push = level.getGameTime() % 10 == 0;
        for (BlockPos p : s.ports()) {
            //Ports lointains (plate-forme en forme libre) : jamais de chargement de chunk
            BlockEntity be = level.isLoaded(p) ? level.getBlockEntity(p) : null;
            if (!(be instanceof PortTile port)) {
                continue;
            }
            if (port.type == PortBlock.Type.HEAT) {
                thermal.heatPorts(port, s);
            } else if (push && port.isOutput()) {
                port.push(this, s);
            }
        }
    }

    // ------------------------------------------------------------------
    // Vues offertes aux ports
    // ------------------------------------------------------------------

    // 0.20.28 : ports intelligents (SmartPorts) : plus de case ni de reservoir vise ; l'entree range seule, la sortie ejecte seule

    /**
     * API Multibloc (lot 5) : contrat de port commun ({@link MultiblockPort}) : le gestionnaire prete par un port de ce type et de ce sens
     * (meme aiguillage que l'ancien {@code PortTile.getCapability}).
     */
    @Override
    public Object portHandler(PortBlock.Type type, boolean output, BlockPos port) {
        return switch (type) {
            case ITEM -> itemHandler(output);
            case FLUID -> fluidHandler(output, port);
            case GAS -> gasHandler(output);
            case ENERGY -> energyHandler(output);
            case PRESSURE -> pressureHandler();
            default -> heatHandler();
        };
    }

    /** Objets : sortie = les cases de sortie ; entree = rangement intelligent dans les cases d'entree. */
    public IItemHandler itemHandler(boolean output) {
        IItemHandler own = output || unit == null ? null : unit.inputItems();
        if (own != null) {
            //0.20.37 : plate-forme : les ports d'objets recoivent les tubes de production
            return own;
        }
        return output ? new ItemSlots(outputs, Automation.EXTERNAL) : ToolBay.ports(new SmartPorts.Items(inputs, this::itemKinds), tool);
    }

    /** Fluides : cuve = son reservoir dans les deux sens ; sortie = reservoirs de sortie (colonne : ceux de l'etage du port). */
    public IFluidHandler fluidHandler(boolean output, BlockPos port) {
        if (machine.kind == IndustryMachine.Kind.TANK) {
            return new TankView(fluidIn);
        }
        if (output) {
            return new TankView(outputFluids(port));
        }
        return new SmartPorts.Fluids(fluidIn, coolant, ControllerTile::isCoolant, this::heats);
    }

    /** Gaz : cuve = son reservoir ; sortie = gaz produits et vapeur du caloporteur ; entree = un reservoir par gaz. */
    public IGasHandler gasHandler(boolean output) {
        if (machine.kind == IndustryMachine.Kind.TANK) {
            return new GasTankHandler(gasIn, Automation.EXTERNAL);
        }
        return output ? new GasTankHandler(outputGases(), Automation.EXTERNAL) : new SmartPorts.Gases(gasIn);
    }

    /** Reservoirs de fluide ejectes par un port de sortie (cuve : le sien ; colonne : ceux de l'etage du port, caloporteur chaud en bas). */
    List<FluidTank> outputFluids(BlockPos port) {
        if (machine.kind == IndustryMachine.Kind.TANK) {
            return fluidIn;
        }
        if (machine.fractions) {
            return fractionTanks(port);
        }
        List<FluidTank> tanks = new ArrayList<>(fluidOut);
        tanks.add(coolantOut);
        return tanks;
    }

    /** Reservoirs de gaz ejectes par un port de sortie (la vapeur du caloporteur en dernier). */
    List<BasicGasTank> outputGases() {
        if (machine.kind == IndustryMachine.Kind.TANK) {
            return gasIn;
        }
        List<BasicGasTank> tanks = new ArrayList<>(gasOut);
        tanks.add(steamOut);
        return tanks;
    }

    SmartPorts.Outputs outputsFor(BlockPos port) {
        return new SmartPorts.Outputs(machine.kind == IndustryMachine.Kind.TANK ? List.of() : outputs, outputFluids(port), outputGases());
    }

    /** Ingredients-objets distincts des recettes ou entre l'objet (reacteurs : combustible et cibles ou couvertures). */
    private int itemKinds(ItemStack stack) {
        int own = unit == null ? -1 : unit.itemKinds();
        if (own > 0) {
            return own;
        }
        if (level == null) {
            return 1;
        }
        RecipeChoice locked = lockedChoice();
        if (locked != null) {
            //MAJ Recettes (lot 7) : le port garde des cases pour les autres ingredients de la recette verrouillee
            return Math.max(1, locked.items().size());
        }
        return IndustryRecipes.itemKinds(level, machine, stack);
    }

    /** La machine chauffe en travaillant : le refroidissement passe d'abord tant qu'il est a moins de moitie. */
    private boolean heats() {
        return machine.heat > 0 || unit != null && unit.heats();
    }

    public IEnergyStorage energyHandler(boolean output) {
        return Sided.energy(energy, output ? SideMode.OUTPUT : SideMode.INPUT);
    }

    @Override
    public boolean strictFluidOrder() {
        return machine.fractions;
    }

    @Override
    public boolean pairedOutputs() {
        return ResourceRows.pairedOut(machine);
    }

    /**
     * Colonne : reservoir de sortie servi par ce port de fluides selon sa hauteur (les ports de sortie de fluides sont classes par etage, du
     * haut vers le bas ; le i-eme etage sert le i-eme fluide produit, le dernier etage sert aussi les suivants et la vapeur du caloporteur).
     */
    List<FluidTank> fractionTanks(BlockPos port) {
        Structure.Result s = lifecycle.structure();
        List<Integer> levels = new ArrayList<>();
        if (s != null) {
            for (BlockPos p : s.ports()) {
                if (level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof PortBlock pb && pb.type == PortBlock.Type.FLUID
                    && level.getBlockEntity(p) instanceof PortTile pt
                    && pt.isOutput() && !levels.contains(p.getY())) {
                    levels.add(p.getY());
                }
            }
        }
        levels.sort(Comparator.reverseOrder());
        int rank = Math.max(0, levels.indexOf(port.getY()));
        List<FluidTank> tanks = new ArrayList<>();
        int last = Math.max(0, levels.size() - 1);
        for (int i = 0; i < fluidOut.size(); i++) {
            if (i == rank || (rank == last && i > rank)) {
                tanks.add(fluidOut.get(i));
            }
        }
        if (rank == last) {
            tanks.add(coolantOut);
        }
        return tanks;
    }

    IEnergyStorage refillEnergy() {
        return energy;
    }

    // ------------------------------------------------------------------
    // Interface : 4 etat, 5 erreur, 6 avancement x1000, 7 duree, 8 lot, 9 parallelisme, 10 modules, 11 tier, 12 temperature x10,
    // 13 nominale, 14 dimensions, 15 FE/t du lot, 16 bloc fautif, 17 reserve du gisement (foreuse)
    // ------------------------------------------------------------------

    @Override
    public int guiHeight() {
        return v2.height;
    }

    @Override
    public int guiWidth() {
        return v2.width;
    }

    /** Lot 5 : interface v2 : inventaire DANS le cadre. */
    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    /** Lot 5 : elements de l'ecran v2 (controle de la geometrie). */
    @Override
    public List<StandardLayout.Part> guiParts() {
        return v2.parts();   // lot 44 : case Outil comprise (MultiblockLayout.tool)
    }

    /** Lot 5 : valeurs nommees ({@link MultiblockValues}) aux indices d'avant, puis le decompte par role de l'onglet Structure. */
    @Override
    protected boolean namedValues() {
        return true;
    }

    @Override
    protected void declareValues(UiValues.Builder values) {
        for (int i = 0; i < MultiblockValues.BY_INDEX.length; i++) {
            int k = i;
            if (MultiblockValues.BY_INDEX[i] != null) {
                values.add(MultiblockValues.BY_INDEX[i], () -> machineValue(k));
            } else {
                values.legacy(4 + i, () -> machineValue(k));
            }
        }
        MultiblockValues.declareCensus(values, i -> lifecycle.census(i, () -> StructureMatcher.census(Structure.View.of(level), worldPosition, facing(), machine)));
        values.add(UiValues.SURPLUS, process::surplusValue);                     //1.0.4-beta (lot 5) : « surplus perdu » (MultiProcess)
    }

    @Override
    public String guiTier() {
        //Cote client, la structure n'est pas connue : le tier peint ; 1.0.4-beta (lot 26) : salle blanche = habillage clair (NbtRoles.guiTier)
        return NbtRoles.guiTier(machine.key, tier(), level != null && level.isClientSide ? getBlockState() : null);
    }

    @Override
    protected int machineValueCount() {
        //0.17.2 : 60 puce d'IA (tous), 61..70 calcul
        //1.0 : 71 blocs de paroi fonctionnels ; 1.0.4-beta (lot 15) : retires (index libre, toujours 0)
        //0.20.37 : 72..74 plate-forme offshore (colonnes, tubes)
        //MAJ Recettes (lot 7) : 75 tier demande par la recette bloquee (-1 : aucun)
        return 76;
    }

    /** Commandes du reacteur depuis l'interface. */
    public static final int REACTOR_RODS_DOWN10 = 0, REACTOR_RODS_DOWN1 = 1, REACTOR_RODS_UP1 = 2, REACTOR_RODS_UP10 = 3, REACTOR_SCRAM = 4,
          REACTOR_RESET = 5, REACTOR_AUTO = 6, REACTOR_PILOT = 7;
    /** 0.17.2 : reprendre la puce d'IA installee. */
    public static final int AI_EJECT = 40;
    /** 0.17.2 : commandes de la fusion (interface) : chauffage marche/arret, arret commande. */
    public static final int FUSION_HEAT = 10, FUSION_STOP = 11;

    /** 0.17.2 : commande du reacteur a fusion (null si ce multibloc n'en est pas un ou s'il n'est pas forme) : noyau d'usine, automate. */
    @Nullable
    public FusionCore fusion() {
        return unit instanceof FusionCore f && lifecycle.structure() != null ? f : null;
    }

    // ------------------------------------------------------------------
    // 0.17.2 : puce d'IA, noyau d'usine
    // ------------------------------------------------------------------

    /** Niveau d'IA effectif : la meilleure de la puce installee et du noyau d'usine superviseur. */
    public int aiLevel() {
        if (level != null && level.isClientSide) {
            return clientChip;
        }
        return Math.max(AiChips.level(aiChip), supervisor());
    }

    /** Niveau du noyau d'usine qui supervise ce multibloc (0 : aucun, ou supervision expiree). */
    public int supervisor() {
        return level != null && level.getGameTime() <= supervisedUntil ? supervisedLevel : 0;
    }

    /** Appele chaque seconde par un noyau d'usine lie (bonus valable 3 s). */
    public void supervise(int aiLevel) {
        if (level != null) {
            supervisedLevel = aiLevel;
            supervisedUntil = level.getGameTime() + 60;
        }
    }

    public ItemStack aiChip() {
        return aiChip;
    }

    /** Installe une puce (une seule) ; renvoie faux si une puce est deja la ou si ce n'est pas un modele. */
    public boolean installChip(ItemStack stack) {
        if (!aiChip.isEmpty() || !AiChips.isModel(stack)) {
            return false;
        }
        aiChip = stack.copyWithCount(1);
        setChanged();
        markSync();
        return true;
    }

    public ItemStack takeChip() {
        ItemStack chip = aiChip;
        aiChip = ItemStack.EMPTY;
        if (!chip.isEmpty()) {
            setChanged();
            markSync();
        }
        return chip;
    }

    /** 0.17.2 : coeur de calcul (centre de donnees, ordinateur quantique), null sinon (serveur). */
    @Nullable
    public ComputeCore computeCore() {
        return unit instanceof ComputeCore c ? c : null;
    }

    /** Lot 6 : coeur du reacteur a fission de la fiche (serveur), ou null. */
    @Nullable
    FissionCore fission() {
        return unit instanceof FissionCore f ? f : null;
    }

    /** Lot 6 : colonnes de la plate-forme offshore de la fiche (serveur), ou null. */
    @Nullable
    OffshoreRisers offshore() {
        return unit instanceof OffshoreRisers o ? o : null;
    }

    /** Lot 6 : le coeur propre declare par la fiche (reacteur, fusion, calcul, plate-forme), ou null. */
    @Nullable
    public MultiblockLogic logic() {
        return unit;
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        if (action - CUSTOM == AI_EJECT) {
            //0.17.2 : la puce d'IA revient au joueur
            ItemStack chip = takeChip();
            if (!chip.isEmpty() && !player.getInventory().add(chip)) {
                player.drop(chip, false);
            }
            return;
        }
        if (unit != null) {
            //Lot 6 : commandes du coeur de la fiche (reacteur : pilote, barres, arret d'urgence... ; fusion : chauffer, couper)
            unit.action(player, action - CUSTOM);
        }
    }

    @Override
    protected long machineValue(int index) {
        Structure.Result s = lifecycle.structure();
        return switch (index) {
            case 0 -> status;
            case 1 -> lifecycle.error().ordinal();
            case 2 -> process.duration <= 0 ? 0 : Math.round(Math.min(1, process.progress / process.duration) * 1000);
            case 3 -> process.duration;
            case 4 -> process.batchCount;
            case 5 -> parallel();
            case 6 -> s == null ? 0 : Structure.units(machine, s);
            case 7 -> tier();
            case 8 -> Math.round(body.temperature * 10);
            case 9 -> Math.round(nominal());
            case 10 -> s == null ? 0 : (long) s.width() << 16 | (long) s.height() << 8 | s.depth();
            case 11 -> process.batchPower;
            case 12 -> lifecycle.errorAt() == null ? Long.MIN_VALUE : lifecycle.errorAt().asLong();
            case 14 -> lifecycle.errorDetail();
            case 15 -> Math.round(thermal.heatInRate);
            case 16 -> Math.round(thermal.savedRate);
            case 17 -> Math.round(air.getPressure() * 10);
            case 18 -> penalty();
            case 19 -> process.batch == null ? 0 : Math.round(process.batch.pressure() * 10);
            //0.17.2 : puce d'IA (niveau installe | niveau du noyau superviseur << 4)
            case 60 -> AiChips.level(aiChip) | (long) supervisor() << 4;
            case 75 -> status == TIER ? process.tierNeeded : -1;
            case 13 -> level != null && !level.isClientSide && machine.kind == IndustryMachine.Kind.DRILL ? drillLeft() : 0;
            //Lot 6 : valeurs du coeur de la fiche, a leurs indices d'avant (reacteur 20..41, 58, 59 ; fusion 42..57 ; calcul 61..70 ;
            //plate-forme 72..74), nommees par MultiblockValues
            default -> unit == null ? 0 : unit.value(index);
        };
    }

    /** Frein en cours : 0 aucun, 1 pression de procede manquante, 2 trop froid, 3 trop chaud (fermes). */
    private long penalty() {
        if (process.batch != null && process.batch.pressure() > 0 && process.batchPressure < 1) {
            return 1;
        }
        if (thermal.isFarm() && lifecycle.structure() != null) {
            return body.temperature < FARM_MIN ? 2 : body.temperature > FARM_MAX ? 3 : 0;
        }
        return 0;
    }

    private long drillLeft() {
        Deposits.Deposit deposit = Deposits.at((ServerLevel) level, worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return fr.pioneers.space.mining.SpaceDrill.left(machine.key, (ServerLevel) level, worldPosition, deposit);
    }

    /** 0.17.0 : commande du reacteur (null si ce multibloc n'est pas un reacteur ou s'il n'est pas forme). */
    @Nullable
    public ReactorControl reactor() {
        return unit instanceof FissionCore f && lifecycle.structure() != null ? f : null;
    }

    /** 0.17.2 : etat d'un centre de donnees ou d'un ordinateur quantique pour les rendus (null sinon). */
    @Nullable
    public ComputeView computeView() {
        if (level != null && level.isClientSide) {
            return clientCompute;
        }
        return unit instanceof ComputeCore c && lifecycle.structure() != null ? c : null;
    }

    /** 0.17.1 : etat du reacteur pour les rendus du coeur (client et serveur ; null si ce n'est pas un reacteur forme). */
    @Nullable
    public ReactorView reactorView() {
        if (level != null && level.isClientSide) {
            return clientView;
        }
        //Lot 6 : vue du coeur de la fiche (fission ; fusion : mark 4 tokamak ou 5 stellarator)
        return unit != null && lifecycle.structure() != null ? unit.reactorView() : null;
    }

    /** 0.17.1 : la vue du reacteur a change (appele par le coeur, au plus 4 fois par seconde). */
    public void syncReactor() {
        markSync();
    }

    /** Client : boite de rendu = toute la structure d'un reacteur forme (le rendu du coeur dessine dedans). */
    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        ReactorView v = clientView;
        if (v != null) {
            return new AABB(v.min().getX(), v.min().getY(), v.min().getZ(), v.max().getX() + 1, v.max().getY() + 1, v.max().getZ() + 1).inflate(1);
        }
        OffshoreRisers.View ov = clientOffshore;
        if (ov != null && !ov.risers().isEmpty()) {
            //0.20.37 : plate-forme : du controleur aux colonnes, jusqu'au fond de la mer
            AABB box = new AABB(worldPosition);
            for (OffshoreRisers.Riser r : ov.risers()) {
                box = box.minmax(new AABB(r.pos.getX() - 1, r.pos.getY() - r.target - 2, r.pos.getZ() - 1, r.pos.getX() + 2, r.pos.getY() + 2,
                      r.pos.getZ() + 2));
            }
            return box;
        }
        AABB nbt = NbtAnims.renderBox(machine.key, worldPosition, getBlockState());
        if (nbt != null) {
            return nbt;
        }
        BigInterior.View bv = clientBig;
        if (bv != null) {
            return bv.renderBox();
        }
        ComputeView cv = clientCompute;
        if (cv != null) {
            //0.17.2 : rendus du calcul (allees de baies, lustre du refrigerateur) dans toute la structure
            return new AABB(cv.min().getX(), cv.min().getY(), cv.min().getZ(), cv.max().getX() + 1, cv.max().getY() + 1,
                  cv.max().getZ() + 1).inflate(2);
        }
        return super.getRenderBoundingBox();
    }

    public FluidTank coolantTank() {
        return coolant;
    }

    public FluidTank coolantOutTank() {
        return coolantOut;
    }

    /** 1.0 (0.19.12) : vapeur d'eau (gaz) du caloporteur. */
    public BasicGasTank steamOutTank() {
        return steamOut;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        unloading = true;
        if (unit != null && level != null && !level.isClientSide) {
            unit.chunkUnloaded(level);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            if (unit != null) {
                //0.20.37 : plate-forme : demande de tubes retiree ; cassee, les tubes remontent et tombent
                unit.removed(level, unloading);
            }
            //Lot 5 : cycle de vie commun (registre, depeinture, modules visibles ; ports liberes a la casse, pas au dechargement)
            lifecycle.removed(unloading);
        }
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        //Lot 6 : vues des rendus du coeur de la fiche (reacteur, fusion : reactorView ; calcul : computeView ; plate-forme : offshoreView)
        boolean offshoreView = unit instanceof OffshoreRisers;
        if (unit != null && !offshoreView) {
            unit.writeClient(tag, lifecycle.structure() != null);
        }
        if (lifecycle.big() != null) {
            tag.put("bigView", lifecycle.big().write());
        } else if (BigInterior.kind(machine.key) != null) {
            tag.putBoolean("bigOff", true);
        }
        tag.putByte("aiChip", (byte) AiChips.level(aiChip));
        GrowView.write(this, process, tag);
        lock.writeClient(tag);
        tag.putInt("tankCap", coolant.getCapacity());
        if (!dataIn.isEmpty()) {
            ListTag data = new ListTag();
            dataIn.forEach(t -> data.add(t.serializeNBT()));
            tag.put("dataTanks", data);
        }
        if (machine.kind == IndustryMachine.Kind.TANK) {
            tag.putInt("storeCap", fluidIn.isEmpty() ? 0 : fluidIn.get(0).getCapacity());
            tag.putLong("gasCap", gasIn.isEmpty() ? 0 : gasIn.get(0).getCapacity());
        }
        if (unit != null && offshoreView) {
            unit.writeClient(tag, lifecycle.structure() != null);
        }
        super.writeClient(tag);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        if (tag.contains("reactorView")) {
            clientView = FissionCore.ClientView.read(tag.getCompound("reactorView"), worldPosition);
        } else if (tag.contains("reactorOff")) {
            clientView = null;
        }
        if (tag.contains("computeView")) {
            clientCompute = ComputeCore.ClientView.read(tag.getCompound("computeView"));
        } else if (tag.contains("computeOff")) {
            clientCompute = null;
        }
        if (tag.contains("bigView")) {
            clientBig = BigInterior.View.read(tag.getCompound("bigView"));
        } else if (tag.contains("bigOff")) {
            clientBig = null;
        }
        if (tag.contains("aiChip")) {
            clientChip = tag.getByte("aiChip");
        }
        lock.readClient(tag);
        GrowView.read(process, tag, (long) fr.pioneers.core.RenderClock.now());   //1.0.5-beta (lot 29) : meme horloge que le rendu des plantes
        if (tag.contains("offshoreView")) {
            clientOffshore = OffshoreRisers.View.read(tag.getCompound("offshoreView"));
        }
        if (tag.contains("tankCap")) {
            for (FluidTank t : getFluidTanks()) {
                t.setCapacity(tag.getInt("tankCap"));
            }
            steamOut.setCapacity((long) tag.getInt("tankCap") * (long) Steam.STEAM_PER_WATER);
        }
        if (tag.contains("storeCap") && !fluidIn.isEmpty()) {
            fluidIn.get(0).setCapacity(tag.getInt("storeCap"));
        }
        if (tag.contains("gasCap") && !gasIn.isEmpty()) {
            gasIn.get(0).setCapacity(tag.getLong("gasCap"));
        }
        ListTag data = tag.getList("dataTanks", Tag.TAG_COMPOUND);
        for (int k = 0; k < Math.min(data.size(), dataIn.size()); k++) {
            dataIn.get(k).deserializeNBT(data.getCompound(k));
        }
        super.readClient(tag);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        tag.putLong("capacity", capacity.value);
        super.saveAdditional(tag);
        IoMigration.stamp(tag, ioOverflow);   // 1.0.5-beta (lot 39) : objets sans place gardes si sauve avant le premier tick
        tag.putBoolean(PartMigration.TAG, partsMigrated);
        tag.putDouble("temperature", body.temperature);
        tag.putInt("overheat", thermal.overheatTicks);
        tag.putDouble("heatCredit", thermal.heatCredit);
        tag.putDouble("air", air.stored);
        lock.save(tag);
        if (unit != null) {
            //Lot 6 : coeur de la fiche, sous sa cle d'avant (reactor, fusion, compute, offshore)
            tag.put(unit.saveKey(), unit.save());
        }
        if (!aiChip.isEmpty()) {
            tag.put("aiChip", aiChip.save(new CompoundTag()));
        }
        mkvCore.save(tag);
        if (lifecycle.big() != null) {
            tag.put("big", lifecycle.big().write());
        }
        tag.putInt("tankCap", coolant.getCapacity());
        if (machine.kind == IndustryMachine.Kind.TANK) {
            tag.putInt("storeCap", fluidIn.isEmpty() ? 0 : fluidIn.get(0).getCapacity());
            tag.putLong("gasCap", gasIn.isEmpty() ? 0 : gasIn.get(0).getCapacity());
        }
        //Lot 5 : lot en cours (cles d'avant le lot 5)
        process.save(tag);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        capacity.value = Math.max(capacity.value, tag.getLong("capacity"));
        //MAJ Recettes (lot 9a) : io d'avant remise en place (une seule fois) ; lot 39 de la 1.0.5 : objets sans place gardes a la sauvegarde
        CompoundTag read = IoMigration.reload(machine, tag, ioOverflow, true);
        ioMigrated = read != tag || !ioOverflow.isEmpty();
        tag = read;
        super.load(tag);
        ioMigrated |= ToolBay.move(machine.key, inputs, tool, ioOverflow);   // 1.0.5-beta (lot 44) : remanents d'avant -> case Outil
        partsMigrated = tag.getBoolean(PartMigration.TAG);
        body.temperature = tag.contains("temperature") ? tag.getDouble("temperature") : HeatExchange.AMBIENT;
        thermal.overheatTicks = tag.getInt("overheat");
        thermal.heatCredit = tag.getDouble("heatCredit");
        air.stored = Math.max(0, tag.getDouble("air"));
        lock.load(tag);
        if (unit != null && (!(unit instanceof OffshoreRisers) || tag.contains(unit.saveKey()))) {
            //Lot 6 : coeur de la fiche (plate-forme : seulement si sauvee ; les autres : toujours, comme avant)
            unit.load(tag.getCompound(unit.saveKey()));
        }
        aiChip = tag.contains("aiChip") ? ItemStack.of(tag.getCompound("aiChip")) : ItemStack.EMPTY;
        mkvCore.load(tag);
        lifecycle.loadBig(tag.contains("big") ? BigInterior.View.read(tag.getCompound("big")) : null);
        if (tag.contains("tankCap")) {
            for (FluidTank t : getFluidTanks()) {
                t.setCapacity(Math.max(t.getCapacity(), tag.getInt("tankCap")));
            }
            steamOut.setCapacity(Math.max(steamOut.getCapacity(), (long) tag.getInt("tankCap") * (long) Steam.STEAM_PER_WATER));
        }
        ListTag data = tag.getList("dataTanks", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(data.size(), dataIn.size()); i++) {
            dataIn.get(i).deserializeNBT(data.getCompound(i));
        }
        if (machine.kind == IndustryMachine.Kind.TANK) {
            if (tag.contains("storeCap") && !fluidIn.isEmpty()) {
                fluidIn.get(0).setCapacity(Math.max(64_000, tag.getInt("storeCap")));
            }
            if (tag.contains("gasCap") && !gasIn.isEmpty()) {
                gasIn.get(0).setCapacity(Math.max(256_000L, tag.getLong("gasCap")));
            }
        }
        process.load(tag);
        lifecycle.requestCheck();
    }

    // ------------------------------------------------------------------
    // MAJ Recettes (lot 7) : selecteur de recette
    // ------------------------------------------------------------------

    /** Multiblocs a recettes : pas de selecteur pour les reacteurs, la fusion, le calcul, les cuves, les foreuses, les puits et les pompes. */
    private boolean hasRecipes() {
        IndustryMachine.Kind k = machine.kind;
        return (unit == null || !unit.drivesWork()) && (k == IndustryMachine.Kind.PROCESS || k == IndustryMachine.Kind.GENERATOR);
    }

    @Override
    public String recipeSource() {
        return hasRecipes() ? RecipeSources.industrial(machine, true) : "";
    }

    @Override
    public RecipeLock recipeLock() {
        return lock;
    }

    @Override
    public void recipeLockChanged() {
        process.dirty = true;
        process.retry = 0;
        setChanged();
        syncNow();
    }

    @Override
    public int[] recipeSlot() {
        return new int[]{v2.recipeX, v2.recipeY};
    }

    @Override
    public int recipeTier() {
        return tier();
    }

    @Override
    public List<ItemStack> presentItems() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemSlot slot : inputs) {
            out.add(slot.getStack());
        }
        return ToolBay.present(out, tool);
    }

    @Override
    public List<FluidStack> presentFluids() {
        List<FluidStack> out = new ArrayList<>();
        for (FluidTank t : fluidIn) {
            out.add(t.getFluid());
        }
        return out;
    }

    @Override
    public List<GasStack> presentGases() {
        List<GasStack> out = new ArrayList<>();
        for (BasicGasTank t : gasIn) {
            out.add(t.getStack());
        }
        return out;
    }

    @Override
    public Level recipeLevel() {
        return level;
    }

    /** Heat handler expose par les ports de chaleur (vanne : l'echange est mene par le controleur). */
    public IHeatHandler heatHandler() {
        return thermal.valve;
    }

    /** Pressure handler expose par les ports de pression (entree seulement). */
    public IPressureHandler pressureHandler() {
        return air.inlet;
    }

    /** Nom de la piece fautive (interface). */
    @Nullable
    public BlockPos errorAt() {
        return lifecycle.errorAt();
    }

    /**
     * API Multibloc (lot 5) : BANC des multiblocs et GameTests seulement : pose sur la tile une structure formee (validee sur un monde
     * factice), sans monde ni peinture, pour l'aller-retour de sauvegarde d'un controleur forme.
     */
    public static void benchForm(ControllerTile tile, Structure.Result result) {
        tile.lifecycle.benchForm(result);
    }

    public static Component errorText(int error) {
        return Component.translatable("industry.pioneers.error." + Structure.Error.values()[Math.max(0, Math.min(error,
              Structure.Error.values().length - 1))].name().toLowerCase(Locale.ROOT));
    }

}

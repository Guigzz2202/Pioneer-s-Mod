package fr.pioneers.content.stabilizer;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.compat.mekanism.MekanismCompat;
import fr.pioneers.compat.mekanism.MekanismGasBridge;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.energy.EnergyBuffer;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.gas.PiGasTypes;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.registries.PiStabilizer;
import fr.pioneers.space.research.ResearchEffects;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Noyau du Stabilisateur spatial (socle) : pose au centre exact du globe de 11x11x11, il porte la structure, ses reservoirs et sa logique.
 * Son bloc fixe le {@link Regime}.
 * <ol>
 *     <li>Les ports d'alimentation (4 au centre des faces laterales, plus autant qu'on veut dans la paroi) forment une seule reserve.</li>
 *     <li>Allumage en tout ou rien : eteint, avec un noyau intact, du carburant et de la place en sortie, des que la reserve contient
 *     {@link #IGNITION_ENERGY_FE} FE (plus un tick de champ), elle est prelevee d'un coup, le noyau est consomme ({@link StabilizerCoreBlock#SPENT})
 *     et la sequence d'allumage dure {@link #IGNITION_TICKS} ticks.</li>
 *     <li>Stable : chaque tick, consommation du carburant et production ; le champ puise dans la reserve.</li>
 *     <li>SCRAM, reserve vide, carburant epuise, sortie pleine ou structure cassee : effondrement (il faut rallumer).</li>
 * </ol>
 * Interface : 4 regime, 5 etat, 6 debit, 7 formation (x1000), 8 charge d'allumage, 9 emetteurs alimentes, 10 energie du champ, 11 capacite du
 * champ, 12 production (mB/t x1000), 13 energie consommee, 14 forme, 15 energie par tick, 16 avancement (x1000), 17 entree par tick.
 */
public class StabilizerCoreTile extends MachineTile implements MultiblockPort, MultiblockCore.House.Holder {

    public static final int MIN_RATE = 1, MAX_RATE = 100;
    public static final long IGNITION_ENERGY_FE = 1_000_000_000L;
    public static final int EMITTERS = 6;
    public static final int IGNITION_TICKS = 200;
    public static final long GAS_INPUT_CAPACITY = MAX_RATE * 20L * 60, OUTPUT_CAPACITY = 64_000;
    public static final int FLUID_INPUT_CAPACITY = MAX_RATE * 10 * 20 * 60;
    public static final int ACTION_RATE_DOWN_10 = CUSTOM, ACTION_RATE_DOWN = CUSTOM + 1, ACTION_RATE_UP = CUSTOM + 2, ACTION_RATE_UP_10 = CUSTOM + 3,
          ACTION_SCRAM = CUSTOM + 4;
    /**
     * Lot 8c : interface en VRAIE v2 (196 de large, inventaire dans le cadre) : entree (lave ou antimatiere) et sortie a gauche, carte du
     * regime, reserve du champ a droite ; ecran d'etat raccourci ; debit et arret d'urgence sur la rangee de reglage (geometrie partagee avec
     * la vue {@code CosmicViews.CORE} et le banc). Avant : 176 x 100, inventaire hors du cadre.
     */
    public static final StandardLayout.Rect IN = new StandardLayout.Rect(8, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.GAUGE_H),
          OUT = new StandardLayout.Rect(24, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.GAUGE_H),
          FIELD = new StandardLayout.Rect(StandardLayout.WIDTH - 8 - StandardLayout.GAUGE_W, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.GAUGE_H),
          CARD = new StandardLayout.Rect(40, StandardLayout.TOP, StandardLayout.WIDTH - 8 - StandardLayout.GAUGE_W - 4 - 40, StandardLayout.GAUGE_H),
          RATE = StandardLayout.settingRow(StandardLayout.WIDTH);

    public final BasicGasTank antimatterTank, outputTank;
    public final FluidTank lavaTank;

    /**
     * Lot 8a : cycle de vie commun des multiblocs maison : le noyau tient la chambre formee (pieces peintes du regime, ports d'alimentation,
     * cases inscrites au registre des hotes : les pieces y retrouvent leur noyau) ; verification chaque seconde ou a la demande.
     */
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
        public Direction front() {
            return Direction.NORTH;
        }

        @Override
        public MultiblockCore.House.Survey survey() {
            return level instanceof ServerLevel server ? StabilizerStructure.survey(server, worldPosition) : null;
        }

        @Override
        public void formed(MultiblockCore.House.Survey survey, boolean first) {
            StabilizerStructure.attach((ServerLevel) level, StabilizerCoreTile.this, (StabilizerStructure.Result) survey.data(), first);
        }

        @Override
        public void unformed(MultiblockCore.House.Survey survey, boolean broken) {
            StabilizerStructure.release((ServerLevel) level, worldPosition);
            if (!survey.removal()) {
                setStructure(false, List.of());
            }
        }

        @Override
        public void houseChanged() {
            setChanged();
            syncNow();
        }
    }, 20);
    private final List<BlockPos> powerPorts = new ArrayList<>();
    private int rate = MIN_RATE;
    private StabilizerState state = StabilizerState.OFF;
    private int ignitionTicks;
    private long inputProgress;
    private long ignitionCharge;
    private double researchBonus;
    //Statistiques (interface)
    private double lastProduced;
    private long lastEnergyUsed;
    private int poweredEmitters;
    private long fieldEnergy, fieldCapacity;

    public StabilizerCoreTile(BlockPos pos, BlockState state) {
        super(PiStabilizer.CORE_TILE.get(), pos, state, 0, 0);
        antimatterTank = gasTank(BasicGasTank.input(GAS_INPUT_CAPACITY, StabilizerCoreTile::isAntimatter, this::tanksChanged));
        outputTank = gasTank(BasicGasTank.output(OUTPUT_CAPACITY, this::tanksChanged));
        lavaTank = fluidTank(new FluidTank(FLUID_INPUT_CAPACITY, fluid -> fluid.getFluid().is(getRegime() == Regime.STAR ? FluidTags.LAVA : FluidTags.WATER)) {
            @Override
            protected void onContentsChanged() {
                tanksChanged();
            }
        });
    }

    public static boolean isAntimatter(GasStack stack) {
        ResourceLocation id = stack.getGas().getRegistryName();
        return id != null && id.getPath().equals("antimatter") && (id.getNamespace().equals("mekanism") || id.getNamespace().equals("pioneers"));
    }

    private void tanksChanged() {
        setChanged();
        markSync();
    }

    public Regime getRegime() {
        return getBlockState().getBlock() instanceof StabilizerCoreBlock core ? core.regime : Regime.SINGULARITY;
    }

    /** Gaz produit par le regime. */
    @Nullable
    private Gas product() {
        return getRegime() == Regime.STAR ? PiGasTypes.STELLAR_PLASMA.get() : PiGasTypes.VOID_SINGULARITY.get();
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    /** Lot 8a : crochets du cycle commun (ports d'alimentation ; chambre defaite : l'astre s'eteint). */
    void setStructure(boolean formed, List<BlockPos> powerPorts) {
        this.powerPorts.clear();
        this.powerPorts.addAll(powerPorts);
        if (!formed) {
            collapse();
        }
    }

    public boolean isFormed() {
        return house.formed();
    }

    @Override
    public MultiblockCore.House house() {
        return house;
    }

    @Override
    public void requestCheck() {
        house.requestCheck();
    }

    /** Erreur au format d'avant ({@code cle|x y z}). */
    public String getError() {
        return house.legacyError();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        house.load();
    }

    /** Dechargement du chunk (ou arret du serveur) : ne rien relire du monde, sinon le serveur se bloque en rechargeant le chunk. */
    private boolean unloading;

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        //Noyau retire : les pieces redeviennent libres (crochet de rupture du cycle commun ; pas au dechargement)
        boolean broken = level instanceof ServerLevel && !unloading && level.isLoaded(worldPosition)
                         && !level.getBlockState(worldPosition).is(getBlockState().getBlock());
        if (broken && !house.formed() && level instanceof ServerLevel server) {
            StabilizerStructure.release(server, worldPosition);
        }
        house.removed(!broken);
    }

    // ------------------------------------------------------------------
    // Logique
    // ------------------------------------------------------------------

    @Override
    protected void tick() {
        house.tick();
        StabilizerState before = state;
        lastProduced = 0;
        lastEnergyUsed = 0;
        if (!isFormed()) {
            poweredEmitters = 0;
            fieldEnergy = fieldCapacity = 0;
            markWorking(false);
            return;
        }
        Regime regime = getRegime();
        long fieldUse = energyPerTick();
        List<EnergyBuffer> buffers = new ArrayList<>();
        long energy = 0, capacity = 0;
        for (BlockPos pos : powerPorts) {
            if (level.getBlockEntity(pos) instanceof StabilizerPartTile port && port.getEnergyBuffer() != null) {
                EnergyBuffer buffer = port.getEnergyBuffer();
                buffers.add(buffer);
                energy += buffer.getStored();
                capacity += buffer.getCapacity();
            }
        }
        //Tous les ports d'alimentation forment une seule reserve, qui tient le champ des 4 emetteurs
        boolean fieldHolds = energy >= fieldUse;
        poweredEmitters = fieldHolds ? EMITTERS : 0;
        fieldEnergy = energy;
        fieldCapacity = capacity;
        long needed = inputPerTick();
        switch (state) {
            case OFF -> {
                //Allumage en tout ou rien : la reserve doit contenir l'allumage et un tick de champ
                if (!isSpent() && energy >= IGNITION_ENERGY_FE + fieldUse && storedInput() >= needed && canWork()) {
                    drawPool(buffers, IGNITION_ENERGY_FE);
                    //Le noyau est consomme : l'astre nait a sa place
                    //Le noyau est detruit : eclats et bruit de casse, puis il disparait (l'astre prend sa place)
                    level.levelEvent(2001, worldPosition, Block.getId(getBlockState()));
                    level.setBlock(worldPosition, getBlockState().setValue(StabilizerCoreBlock.SPENT, true), Block.UPDATE_ALL);
                    state = StabilizerState.IGNITING;
                    ignitionTicks = 0;
                    setChanged();
                }
            }
            case IGNITING -> {
                if (!fieldHolds) {
                    collapse();
                } else {
                    drain(buffers, fieldUse);
                    if (++ignitionTicks >= IGNITION_TICKS) {
                        state = StabilizerState.STABLE;
                    }
                }
            }
            case STABLE -> {
                if (!fieldHolds) {
                    collapse();
                } else if (!hasOutputRoom(needed)) {
                    //Reservoir de sortie plein : l'astre reste capture (le champ consomme toujours), la production attend
                    drain(buffers, fieldUse);
                    lastProduced = 0;
                } else if (storedInput() < needed) {
                    collapse();
                } else {
                    drain(buffers, fieldUse);
                    consumeInput(needed);
                    inputProgress += needed;
                    long produced = inputProgress / regime.inputPerOutput;
                    if (produced > 0) {
                        //Theorie de la singularite : production en plus
                        double bonus = ResearchEffects.singularityBonus();
                        if (bonus > 0) {
                            researchBonus += produced * bonus;
                            long extra = (long) researchBonus;
                            researchBonus -= extra;
                            produced += extra;
                        }
                        Gas gas = product();
                        if (gas != null) {
                            outputTank.insert(GasStack.of(gas, produced), Action.EXECUTE, Automation.INTERNAL);
                        }
                        inputProgress %= regime.inputPerOutput;
                    }
                    lastProduced = needed / (double) regime.inputPerOutput;
                }
            }
        }
        if (state != StabilizerState.OFF) {
            setChanged();
        }
        if (before != state) {
            syncNow();
        } else if (state == StabilizerState.IGNITING && ignitionTicks % 10 == 0) {
            markSync();
        }
        markWorking(state != StabilizerState.OFF);
    }

    private void drain(List<EnergyBuffer> buffers, long amount) {
        drawPool(buffers, amount);
        lastEnergyUsed = amount;
    }

    /** Puise dans la reserve commune des ports d'alimentation, port apres port. */
    private static void drawPool(List<EnergyBuffer> buffers, long amount) {
        for (EnergyBuffer buffer : buffers) {
            if (amount <= 0) {
                return;
            }
            amount -= buffer.extract(amount, false);
        }
    }

    private long storedInput() {
        return getRegime().fluidInput() ? lavaTank.getFluidAmount() : antimatterTank.getStack().getAmount();
    }

    private void consumeInput(long amount) {
        if (getRegime().fluidInput()) {
            lavaTank.drain((int) amount, IFluidHandler.FluidAction.EXECUTE);
        } else {
            antimatterTank.extract(amount, Action.EXECUTE, Automation.INTERNAL);
        }
    }

    private boolean hasOutputRoom(long needed) {
        long room = outputTank.getCapacity() - outputTank.getStack().getAmount();
        return room >= (inputProgress + needed) / getRegime().inputPerOutput;
    }

    /** Effondrement (SCRAM, panne ou structure rompue). */
    public void collapse() {
        if (state != StabilizerState.OFF) {
            state = StabilizerState.OFF;
            ignitionTicks = 0;
            setChanged();
            syncNow();
        }
    }

    /** Noyau deja consomme (un astre l'a remplace) : il en faut un nouveau pour rallumer. */
    public boolean isSpent() {
        BlockState state = getBlockState();
        return state.hasProperty(StabilizerCoreBlock.SPENT) && state.getValue(StabilizerCoreBlock.SPENT);
    }

    public long energyPerTick() {
        return getRegime().energyPerRatePerTick * rate;
    }

    public long inputPerTick() {
        return getRegime().inputPerRatePerTick * rate;
    }

    public StabilizerState getState() {
        return state;
    }

    public int getRate() {
        return rate;
    }

    /** Avancement de la formation : 0 eteint, 0..1 pendant l'allumage, 1 stable. */
    public float getFormation() {
        return switch (state) {
            case OFF -> 0;
            case IGNITING -> Mth.clamp(ignitionTicks / (float) IGNITION_TICKS, 0, 1);
            case STABLE -> 1;
        };
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        switch (action) {
            case ACTION_RATE_DOWN_10 -> setRate(rate - 10);
            case ACTION_RATE_DOWN -> setRate(rate - 1);
            case ACTION_RATE_UP -> setRate(rate + 1);
            case ACTION_RATE_UP_10 -> setRate(rate + 10);
            case ACTION_SCRAM -> collapse();
            default -> {
            }
        }
    }

    private void setRate(int value) {
        value = Mth.clamp(value, MIN_RATE, MAX_RATE);
        if (value != rate) {
            rate = value;
            setChanged();
            syncNow();
        }
    }

    // ------------------------------------------------------------------
    // Interface et capacites
    // ------------------------------------------------------------------

    @Nullable
    @Override
    public SideConfig getSideConfig() {
        return null;
    }

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

    @Override
    public List<StandardLayout.Part> guiParts() {
        return List.of(StandardLayout.framed("gauge", IN), StandardLayout.framed("gauge", OUT), new StandardLayout.Part("card", CARD),
              StandardLayout.framed("gauge", FIELD), new StandardLayout.Part("screen", StandardLayout.shortScreen(StandardLayout.WIDTH)),
              new StandardLayout.Part("setting", RATE));
    }

    @NotNull
    @Override
    public Component getDisplayName() {
        return Component.translatable("stabilizer.pioneers.title");
    }

    /** Habillage a la couleur du regime (violet pour la singularite, orange pour l'etoile). */
    @Override
    public String guiTier() {
        return "space_stabilizer_" + getRegime().key();
    }

    /** L'interface s'ouvre depuis la paroi : le joueur peut etre a plus de 8 blocs du centre. */
    @Override
    public double guiReach() {
        return 14;
    }

    /** Lot 8a : valeurs d'interface NOMMEES (memes indices qu'avant : 4 et suivants, apres les 4 communes). */
    public static final UiValues.Key V_REGIME = UiValues.Key.of("regime"), V_STATE = UiValues.Key.of("state"), V_RATE = UiValues.Key.of("rate"),
          V_FORMATION_X1000 = UiValues.Key.of("formation_x1000"), V_IGNITION_CHARGE = UiValues.Key.of("ignition_charge"),
          V_EMITTERS = UiValues.Key.of("emitters"), V_FIELD_ENERGY = UiValues.Key.of("field_energy"),
          V_FIELD_CAPACITY = UiValues.Key.of("field_capacity"), V_PRODUCED_X1000 = UiValues.Key.of("produced_x1000"),
          V_FIELD_USE = UiValues.Key.of("field_use"), V_FORMED = UiValues.Key.of("formed"), V_ENERGY = UiValues.Key.of("core_energy"),
          V_PROGRESS_X1000 = UiValues.Key.of("progress_x1000"), V_CONSUMPTION = UiValues.Key.of("consumption");
    private static final UiValues.Key[] VALUES = {V_REGIME, V_STATE, V_RATE, V_FORMATION_X1000, V_IGNITION_CHARGE, V_EMITTERS, V_FIELD_ENERGY,
          V_FIELD_CAPACITY, V_PRODUCED_X1000, V_FIELD_USE, V_FORMED, V_ENERGY, V_PROGRESS_X1000, V_CONSUMPTION};

    @Override
    protected boolean namedValues() {
        return true;
    }

    @Override
    protected void declareValues(UiValues.Builder values) {
        for (int i = 0; i < VALUES.length; i++) {
            int k = i;
            values.add(VALUES[i], () -> machineValue(k));
        }
    }

    @Override
    protected int machineValueCount() {
        return 14;
    }

    @Override
    protected long machineValue(int index) {
        return switch (index) {
            case 0 -> getRegime().ordinal() | (isSpent() ? 0x100 : 0);
            case 1 -> state.ordinal();
            case 2 -> rate;
            case 3 -> Math.round(getFormation() * 1000);
            case 4 -> fieldEnergy;
            case 5 -> poweredEmitters;
            case 6 -> fieldEnergy;
            case 7 -> fieldCapacity;
            case 8 -> Math.round(lastProduced * 1000);
            case 9 -> lastEnergyUsed;
            case 10 -> isFormed() ? 1 : 0;
            case 11 -> energyPerTick();
            case 12 -> Math.round(inputProgress * 1000.0 / getRegime().inputPerOutput);
            default -> inputPerTick();
        };
    }

    /** Le noyau est a l'interieur : ses reservoirs s'atteignent par les ports de la paroi. */
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (side != null && (cap == ForgeCapabilities.FLUID_HANDLER || cap == ForgeCapabilities.ENERGY || cap == PioneersAPI.GAS_HANDLER
                             || MekanismCompat.LOADED && MekanismGasBridge.isMekanismGasCapability(cap))) {
            return LazyOptional.empty();
        }
        return super.getCapability(cap, side);
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(6);
    }

    // ------------------------------------------------------------------
    // Sauvegarde et synchronisation
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("rate", rate);
        tag.putInt("state", state.ordinal());
        tag.putInt("ignitionTicks", ignitionTicks);
        tag.putLong("inputProgress", inputProgress);
        tag.putLong("ignitionCharge", ignitionCharge);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        rate = Mth.clamp(tag.contains("rate") ? tag.getInt("rate") : MIN_RATE, MIN_RATE, MAX_RATE);
        state = StabilizerState.byIndex(tag.getInt("state"));
        ignitionTicks = tag.getInt("ignitionTicks");
        inputProgress = tag.getLong("inputProgress");
        ignitionCharge = tag.getLong("ignitionCharge");
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        house.write(tag);
        tag.putInt("rate", rate);
        tag.putInt("state", state.ordinal());
        tag.putInt("ignitionTicks", ignitionTicks);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        house.read(tag);
        rate = tag.getInt("rate");
        state = StabilizerState.byIndex(tag.getInt("state"));
        ignitionTicks = tag.getInt("ignitionTicks");
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

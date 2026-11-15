package fr.pioneers.space.content.absorber;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.compat.mekanism.MekanismCompat;
import fr.pioneers.core.compat.mekanism.MekanismGasBridge;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.registries.PiCosmicBlocks;
import fr.pioneers.core.registries.PiSounds;
import fr.pioneers.space.PiDimensions;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.event.SpaceEvent;
import fr.pioneers.space.event.SpaceEvents;
import fr.pioneers.space.research.ResearchEffects;
import fr.pioneers.space.research.ResearchNode;
import fr.pioneers.space.station.OrbitalStations;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Controleur de l'absorbeur d'energie solaire (socle) : pose dans une paroi laterale du caisson ({@link AbsorberStructure}), il porte la
 * logique, l'energie et le caloporteur. L'absorbeur ne fonctionne qu'en orbite d'une etoile (station orbitale de l'etoile) et convertit
 * son rayonnement en energie.
 * <ul>
 *     <li>Puissance : {@link #FE_PER_COLLECTOR} FE/t par capteur pres du Soleil, selon la luminosite de l'etoile ; triplee pendant une
 *         eruption.</li>
 *     <li>Chaleur : un caloporteur (ammoniac) l'evacue et ressort chaud ; sans lui, l'absorbeur chauffe et ferme ses volets d'urgence
 *         au-dela de {@link #MAX_TEMPERATURE} degres.</li>
 *     <li>L'energie sort par tous les ports ; le caloporteur entre et sort par les ports reglés a la cle.</li>
 * </ul>
 * Interface : 4 etat, 5 temperature (x10), 6 capteurs, 7 production (FE/t), 8 caloporteur (mB/t), 9 eruption (x100), 10 etoile (+1, 0 =
 * aucune), 11 volets fermes, 12 forme.
 */
public class AbsorberControllerTile extends MachineTile implements MultiblockPort, MultiblockCore.House.Holder {

    public static final long FE_PER_COLLECTOR = 2_000_000L;
    public static final int MAX_COLLECTORS = (AbsorberStructure.MAX_SIDE - 2) * (AbsorberStructure.MAX_SIDE - 2);
    /** Reserve : 200 ticks de production du plus grand absorbeur au Soleil. */
    public static final long ENERGY_CAPACITY_FE = FE_PER_COLLECTOR * MAX_COLLECTORS * 200L;
    public static final long COOLANT_CAPACITY = 256_000;
    public static final double MAX_TEMPERATURE = 1600, SAFE_TEMPERATURE = 600, AMBIENT = 20;
    /** Chaleur produite par FE capte. */
    public static final double HEAT_PER_FE = 1.25E-6;
    /** Chaleur evacuee par mB de caloporteur chauffe (ammoniac). */
    public static final double COOLANT_ENTHALPY = 10;
    public static final int ACTION_SHUTTERS = CUSTOM;
    public static final int GUI_HEIGHT = StandardLayout.ZONE;
    /**
     * Lot 8c : interface en VRAIE v2 (196 de large) : energie produite, caloporteur froid et chaud a gauche, carte de l'astre, thermometre a
     * droite ; ecran d'etat raccourci ; volets sur la rangee de reglage. Geometrie partagee avec la vue {@code CosmicViews.ABSORBER} et le banc.
     */
    public static final StandardLayout.Rect ENERGY = new StandardLayout.Rect(8, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.GAUGE_H),
          COLD = new StandardLayout.Rect(24, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.GAUGE_H),
          HOT = new StandardLayout.Rect(40, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.GAUGE_H),
          THERMO = new StandardLayout.Rect(StandardLayout.WIDTH - 8 - StandardLayout.GAUGE_W, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.GAUGE_H),
          CARD = new StandardLayout.Rect(56, StandardLayout.TOP, StandardLayout.WIDTH - 8 - StandardLayout.GAUGE_W - 4 - 56, StandardLayout.GAUGE_H),
          SHUTTERS = StandardLayout.settingRow(StandardLayout.WIDTH);

    /** FE/t par capteur au Soleil (configuration). */
    public static long fePerCollector() {
        return PiCommonConfig.get(PiCommonConfig.ABSORBER_FE_PER_COLLECTOR, FE_PER_COLLECTOR);
    }

    public enum Status {
        FORMING, NOT_STAR, NO_RESEARCH, NO_SKY, SHUTTERED, RUNNING, OVERHEAT, FULL;

        public static Status byIndex(int index) {
            return index >= 0 && index < values().length ? values()[index] : FORMING;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public final BasicGasTank coolantTank;
    public final BasicGasTank heatedTank;

    /** Lot 8a : cycle de vie commun des multiblocs maison (verification toutes les 2 s ou a la demande, revalidee au chargement). */
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
            return facing();
        }

        @Override
        public MultiblockCore.House.Survey survey() {
            return level instanceof ServerLevel server ? AbsorberStructure.survey(server, AbsorberControllerTile.this) : null;
        }

        @Override
        public void formed(MultiblockCore.House.Survey survey, boolean first) {
            AbsorberStructure.attach((ServerLevel) level, AbsorberControllerTile.this, (AbsorberStructure.Result) survey.data());
        }

        @Override
        public void unformed(MultiblockCore.House.Survey survey, boolean broken) {
            AbsorberStructure.release((ServerLevel) level, AbsorberControllerTile.this);
            if (!survey.removal()) {
                setStructure(false, null, null, 0);
            }
        }

        @Override
        public void houseChanged() {
            setChanged();
            syncNow();
        }
    }, 40);
    @Nullable
    private BlockPos min, max;
    private int collectors;
    private double temperature = AMBIENT;
    private boolean shutters;
    private Status status = Status.FORMING;
    private long lastProduced;
    private long lastCoolant;
    private float flareBoost = 1;
    private int starIndex = -1;
    private boolean skyAccess = true;
    private boolean lit;

    public AbsorberControllerTile(BlockPos pos, BlockState state) {
        super(PiCosmicBlocks.ABSORBER_CONTROLLER_TILE.get(), pos, state, ENERGY_CAPACITY_FE, Integer.MAX_VALUE);
        coolantTank = gasTank(BasicGasTank.input(COOLANT_CAPACITY, AbsorberControllerTile::isCoolant, this::changed));
        heatedTank = gasTank(BasicGasTank.output(COOLANT_CAPACITY, this::changed));
    }

    /** Caloporteur froid : un gaz qui se rechauffe en un autre (ammoniac). */
    public static boolean isCoolant(GasStack stack) {
        return stack.getGas().getHeated() != null;
    }

    private void changed() {
        setChanged();
        markSync();
    }

    @Override
    protected boolean producesEnergy() {
        return true;
    }

    /** Luminosite de l'etoile (Soleil = 1). */
    public static double luminosity(@Nullable Body star) {
        if (star == null) {
            return 0;
        }
        return switch (star) {
            case SUN -> 1.0;
            case CENTAURI_A -> 1.5;
            case CENTAURI_B -> 0.5;
            case PROXIMA -> 0.15;
            case TRAPPIST_1 -> 0.05;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    /** Lot 8a : crochets du cycle commun (bornes du caisson, capteurs ; defait : capteurs eteints). */
    void setStructure(boolean formed, @Nullable BlockPos min, @Nullable BlockPos max, int collectors) {
        boolean changed = this.collectors != collectors
                          || (min == null ? this.min != null : !min.equals(this.min));
        if (!formed && lit && level != null && this.min != null && this.max != null) {
            AbsorberStructure.lightCollectors(level, this.min, this.max, false);
            lit = false;
        }
        this.collectors = collectors;
        if (formed) {
            this.min = min;
            this.max = max;
        } else {
            status = Status.FORMING;
        }
        if (changed) {
            setChanged();
            syncNow();
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

    @Nullable
    public BlockPos getMin() {
        return min;
    }

    @Nullable
    public BlockPos getMax() {
        return max;
    }

    public BlockPos getCenter() {
        if (min == null || max == null) {
            return worldPosition;
        }
        return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            AbsorberStructure.register(server, worldPosition);
            house.load();
        }
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
        if (level instanceof ServerLevel server) {
            AbsorberStructure.unregister(server, worldPosition);
            boolean broken = !unloading && level.isLoaded(worldPosition) && !level.getBlockState(worldPosition).is(getBlockState().getBlock());
            house.removed(!broken);
        }
    }

    // ------------------------------------------------------------------
    // Logique
    // ------------------------------------------------------------------

    @Override
    protected void tick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        house.tick();
        lastProduced = 0;
        lastCoolant = 0;
        if (!isFormed() || min == null || max == null) {
            status = Status.FORMING;
            markWorking(false);
            return;
        }
        BlockPos center = getCenter();
        Body star = level.dimension() == PiDimensions.ORBIT ? OrbitalStations.bodyAt(center.getX() + 0.5) : null;
        starIndex = star == null ? -1 : star.ordinal();
        if (level.getGameTime() % 40 == 0) {
            skyAccess = level.canSeeSky(max.above());
        }
        double lum = star != null && star.isStar() ? luminosity(star) : 0;
        SpaceEvent flare = SpaceEvents.flareAt(server, center.getX() + 0.5);
        flareBoost = flare == null ? 1 : 1 + 2 * flare.intensity();
        double produced = 0;
        if (lum <= 0) {
            status = Status.NOT_STAR;
        } else if (!ResearchEffects.has(ResearchNode.SOLAR_ABSORPTION)) {
            status = Status.NO_RESEARCH;
        } else if (!skyAccess) {
            status = Status.NO_SKY;
        } else if (shutters) {
            status = Status.SHUTTERED;
        } else if (status == Status.OVERHEAT && temperature > SAFE_TEMPERATURE) {
            status = Status.OVERHEAT;
        } else {
            produced = fePerCollector() * collectors * lum * flareBoost;
            long wanted = (long) produced;
            long accepted = energy.insert(wanted, false);
            lastProduced = accepted;
            status = accepted >= wanted ? Status.RUNNING : Status.FULL;
        }
        //Chaleur : le caloporteur l'evacue, le rayonnement de l'absorbeur en dissipe un peu
        double heat = produced * HEAT_PER_FE;
        GasStack coolant = coolantTank.getStack();
        Gas hot = coolant.isEmpty() ? null : coolant.getGas().getHeated();
        if (heat > 0 && hot != null) {
            long needed = (long) Math.ceil(heat / COOLANT_ENTHALPY);
            GasStack heated = heatedTank.getStack();
            long room = heated.isEmpty() || heated.getGas().equals(hot) ? heatedTank.getCapacity() - heated.getAmount() : 0;
            long used = Math.min(Math.min(needed, coolant.getAmount()), room);
            if (used > 0) {
                coolantTank.extract(used, Action.EXECUTE, Automation.INTERNAL);
                heatedTank.insert(GasStack.of(hot, used), Action.EXECUTE, Automation.INTERNAL);
                heat -= used * COOLANT_ENTHALPY;
                lastCoolant = used;
            }
        }
        temperature += heat * 0.4 - (temperature - AMBIENT) * 0.004;
        temperature = Mth.clamp(temperature, AMBIENT, MAX_TEMPERATURE + 200);
        if (temperature >= MAX_TEMPERATURE && status != Status.OVERHEAT) {
            status = Status.OVERHEAT;
            level.playSound(null, center, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 2.0F, 0.6F);
        }
        if (status == Status.RUNNING && level.getGameTime() % 100 == 0) {
            level.playSound(null, center, PiSounds.ABSORBER_HUM.get(), SoundSource.BLOCKS, 1.2F, 1.0F);
        }
        boolean active = status == Status.RUNNING || status == Status.FULL;
        if (active != lit) {
            lit = active;
            AbsorberStructure.lightCollectors(level, min, max, active);
            syncNow();
        }
        markWorking(active);
        setChanged();
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        if (action == ACTION_SHUTTERS) {
            shutters = !shutters;
            setChanged();
            syncNow();
        }
    }

    // ------------------------------------------------------------------
    // Accesseurs (rendu, interface)
    // ------------------------------------------------------------------

    public Status getStatus() {
        return status;
    }

    public double getTemperature() {
        return temperature;
    }

    /** Absorbeur en marche (rendu des faisceaux). */
    public boolean isLit() {
        return lit;
    }

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    @Nullable
    @Override
    public SideConfig getSideConfig() {
        return null;
    }

    @Override
    public int guiHeight() {
        return GUI_HEIGHT;
    }

    /** Lot 8a : interface standard v2 (inventaire dans le cadre, onglets a droite, Infos et Structure) autour du corps propre d'avant. */
    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public java.util.List<StandardLayout.Part> guiParts() {
        return java.util.List.of(StandardLayout.framed("energy", ENERGY), StandardLayout.framed("gauge", COLD), StandardLayout.framed("gauge", HOT),
              new StandardLayout.Part("card", CARD), StandardLayout.framed("gauge", THERMO),
              new StandardLayout.Part("screen", StandardLayout.shortScreen(StandardLayout.WIDTH)), new StandardLayout.Part("setting", SHUTTERS));
    }

    @NotNull
    @Override
    public Component getDisplayName() {
        return Component.translatable("constants.pioneers.solar_absorber");
    }

    @Override
    public String guiTier() {
        return "solar_absorber";
    }

    /** L'interface s'ouvre depuis le caisson : le joueur peut etre loin du controleur. */
    @Override
    public double guiReach() {
        return 24;
    }

    /** Lot 8a : valeurs d'interface NOMMEES (memes indices qu'avant : 4 et suivants, apres les 4 communes). */
    public static final UiValues.Key V_STATUS = UiValues.Key.of("status"), V_TEMPERATURE_X10 = UiValues.Key.of("temperature_x10"),
          V_COLLECTORS = UiValues.Key.of("collectors"), V_OUTPUT = UiValues.Key.of("output"), V_COOLANT = UiValues.Key.of("coolant"),
          V_FLARE_X100 = UiValues.Key.of("flare_x100"), V_STAR = UiValues.Key.of("star"), V_SHUTTERS = UiValues.Key.of("shutters"),
          V_FORMED = UiValues.Key.of("formed");
    private static final UiValues.Key[] VALUES = {V_STATUS, V_TEMPERATURE_X10, V_COLLECTORS, V_OUTPUT, V_COOLANT, V_FLARE_X100, V_STAR, V_SHUTTERS,
          V_FORMED};

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
        return 9;
    }

    @Override
    protected long machineValue(int index) {
        return switch (index) {
            case 0 -> status.ordinal();
            case 1 -> Math.round(temperature * 10);
            case 2 -> collectors;
            case 3 -> lastProduced;
            case 4 -> lastCoolant;
            case 5 -> Math.round(flareBoost * 100);
            case 6 -> starIndex + 1;
            case 7 -> shutters ? 1 : 0;
            default -> isFormed() ? 1 : 0;
        };
    }

    /** Le controleur ne prend rien par ses faces : tout passe par les ports du caisson. */
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (side != null && (cap == ForgeCapabilities.ENERGY || cap == PioneersAPI.GAS_HANDLER || cap == ForgeCapabilities.ITEM_HANDLER
                             || cap == ForgeCapabilities.FLUID_HANDLER || MekanismCompat.LOADED && MekanismGasBridge.isMekanismGasCapability(cap))) {
            return LazyOptional.empty();
        }
        return super.getCapability(cap, side);
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        if (min == null || max == null) {
            return super.getRenderBoundingBox();
        }
        return new AABB(min.getX() - 48, min.getY() - 4, min.getZ() - 48, max.getX() + 49, max.getY() + 24, max.getZ() + 49);
    }

    // ------------------------------------------------------------------
    // Sauvegarde et synchronisation
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("temperature", temperature);
        tag.putBoolean("shutters", shutters);
        tag.putInt("status", status.ordinal());
        tag.putBoolean("lit", lit);
        if (min != null && max != null) {
            tag.putLong("min", min.asLong());
            tag.putLong("max", max.asLong());
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        temperature = tag.contains("temperature") ? tag.getDouble("temperature") : AMBIENT;
        shutters = tag.getBoolean("shutters");
        status = Status.byIndex(tag.getInt("status"));
        lit = tag.getBoolean("lit");
        if (tag.contains("min")) {
            min = BlockPos.of(tag.getLong("min"));
            max = BlockPos.of(tag.getLong("max"));
        }
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        house.write(tag);
        tag.putBoolean("lit", lit);
        tag.putBoolean("shutters", shutters);
        if (min != null && max != null) {
            tag.putLong("min", min.asLong());
            tag.putLong("max", max.asLong());
        }
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        house.read(tag);
        lit = tag.getBoolean("lit");
        shutters = tag.getBoolean("shutters");
        if (tag.contains("min")) {
            min = BlockPos.of(tag.getLong("min"));
            max = BlockPos.of(tag.getLong("max"));
        }
    }

    /**
     * Lot 8c : CONTRAT DE PORT COMMUN ({@link MultiblockPort#portHandler}) : un port commun pose dans la structure formee prete la capacite
     * interne de la maison ({@link MultiblockPort#capabilityOf}) ; les ports propres de la maison gardent leurs gestionnaires.
     */
    @Nullable
    @Override
    public Object portHandler(fr.pioneers.tech.industry.multi.PortBlock.Type type, boolean output, BlockPos port) {
        return house.formed() ? MultiblockPort.capabilityOf(this, type) : null;
    }
}

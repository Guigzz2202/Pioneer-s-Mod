package fr.pioneers.space.tile;

import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.Directions;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.space.environment.RoomScanner.Room;
import fr.pioneers.space.environment.RoomScanner;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * API Machine (lot 8c) : LOGIQUE DU DIFFUSEUR D'OXYGENE (code d'avant de {@link TileEntityOxygenDiffuser}, deplace) : reservoir d'oxygene et
 * batterie (declares par l'unite dans le meme ordre qu'avant), balayage de la piece (au changement signale par {@code OxygenRooms}, au plus
 * toutes les deux secondes, rebalayage de securite chaque minute), pressurisation, petites fuites, depressurisation. Cles sauvees :
 * {@code pressure}, {@code volume}, {@code sealed}.
 * <p>
 * Valeurs nommees (apres les 4 communes, memes indices qu'avant) : oxygene injecte, cible, volume de la piece, piece close, pressurisee.
 */
public final class OxygenDiffuserUnit extends MachineUnit {

    public static final UiValues.Key OXYGEN_MB = UiValues.Key.of("oxygen"), TARGET = UiValues.Key.of("target"), VOLUME = UiValues.Key.of("volume"),
          SEALED = UiValues.Key.of("sealed"), PRESSURIZED = UiValues.Key.of("pressurized");
    private static final int SCAN_INTERVAL = 40;
    /** 0.20.46 (audit) : rebalayage de securite (ticks), en plus des changements signales. */
    private static final int RESCAN = 1_200;
    private static final int[] TIER_VOLUME = TileEntityOxygenDiffuser.TIER_VOLUME;
    private static final long OXYGEN_PER_BLOCK = TileEntityOxygenDiffuser.OXYGEN_PER_BLOCK, FILL_RATE = TileEntityOxygenDiffuser.FILL_RATE,
          ENERGY_BASE_FE = TileEntityOxygenDiffuser.ENERGY_BASE_FE;

    final BasicGasTank oxygenTank;
    private final ItemSlot battery;
    private Room room = Room.EMPTY;
    /** Oxygene injecte dans la piece (mB). */
    private long pressure;
    private int volume;
    private boolean sealed;
    private boolean powered;
    private long savedPressure = -1;
    /** 0.20.46 (audit) : un bloc de la piece ou de sa paroi a change (rebalayage au prochain creneau). */
    private boolean dirty = true;
    private long lastScan;
    private int scannedTier = -1;
    /** Boite de la piece et des cases de depart (autour du diffuseur), pour ecarter vite les changements lointains. */
    private int minX, minY, minZ, maxX, maxY, maxZ;

    public OxygenDiffuserUnit(MachineTile.Access machine) {
        super(machine);
        oxygenTank = machine.gasTank(BasicGasTank.input(TileEntityOxygenDiffuser.TANK_CAPACITY, TileEntityOxygenDiffuser::isOxygen, () -> {
            machine.changed();
            machine.markSync();
        }));
        battery = machine.batterySlot(TileEntityOxygenDiffuser.BATTERY_X, TileEntityOxygenDiffuser.BATTERY_Y);
    }

    private static long getMaxEnergyUsage(int tier) {
        return TileEntityOxygenDiffuser.getMaxEnergyUsage(tier);
    }

    @Override
    public void tick() {
        machine.discharge(battery, getMaxEnergyUsage(machine.machineTier()) * 4);
        long now = level().getGameTime();
        if ((now + pos().asLong()) % SCAN_INTERVAL == 0 && (dirty || now - lastScan >= RESCAN || scannedTier != machine.machineTier())) {
            rescan();
        }
        powered = false;
        if (sealed && volume > 0 && machine.canWork() && machine.use(getEnergyUsage())) {
            powered = true;
            long target = getTarget();
            if (pressure < target) {
                pressure += oxygenTank.extract(Math.min(FILL_RATE << machine.machineTier(), target - pressure), Action.EXECUTE, Automation.INTERNAL).getAmount();
            }
            //Petites fuites : entretien d'environ 1 mB par seconde pour 200 blocs d'air
            if (now % 20 == 0 && pressure > 0) {
                pressure = Math.max(0, pressure - Math.max(1, volume / 200));
            }
        }
        machine.markWorking(isPressurized());
        //Sauvegarde seulement quand la pression change (pas a chaque tick)
        if (pressure != savedPressure) {
            savedPressure = pressure;
            machine.changed();
        }
    }

    private void rescan() {
        dirty = false;
        lastScan = level().getGameTime();
        scannedTier = machine.machineTier();
        Room scanned = RoomScanner.scan(level(), pos(), TIER_VOLUME[scannedTier]);
        boolean wasSealed = sealed;
        room = scanned;
        bounds(scanned);
        sealed = scanned.sealed() && scanned.volume() > 0;
        volume = sealed ? scanned.volume() : 0;
        if (!sealed && pressure > 0) {
            //Depressurisation : l'oxygene s'echappe d'un coup
            pressure = 0;
            if (wasSealed && level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, pos(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.6F);
                serverLevel.sendParticles(ParticleTypes.CLOUD, pos().getX() + 0.5, pos().getY() + 1.1, pos().getZ() + 0.5,
                      20, 0.6, 0.2, 0.6, 0.08);
            }
        }
        pressure = Math.min(pressure, getTarget());
    }

    private void bounds(Room scanned) {
        minX = pos().getX() - 1;
        minY = pos().getY() - 1;
        minZ = pos().getZ() - 1;
        maxX = pos().getX() + 1;
        maxY = pos().getY() + 1;
        maxZ = pos().getZ() + 1;
        for (LongIterator it = scanned.cells().iterator(); it.hasNext(); ) {
            long c = it.nextLong();
            int x = BlockPos.getX(c), y = BlockPos.getY(c), z = BlockPos.getZ(c);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
    }

    /** 0.20.46 (audit) : boite de la piece elargie de {@code margin}. */
    public boolean near(int x, int y, int z, int margin) {
        return x >= minX - margin && x <= maxX + margin && y >= minY - margin && y <= maxY + margin && z >= minZ - margin && z <= maxZ + margin;
    }

    /** 0.20.46 (audit) : la boite de la piece (elargie d'un bloc) croise-t-elle ces colonnes ? */
    public boolean nearColumns(int x0, int z0, int x1, int z1) {
        return x1 >= minX - 1 && x0 <= maxX + 1 && z1 >= minZ - 1 && z0 <= maxZ + 1;
    }

    /** 0.20.46 (audit) : a rebalayer au prochain creneau (porte, chunk charge ou decharge). */
    public void markDirty() {
        dirty = true;
    }

    /**
     * 0.20.46 (audit) : un bloc a change en {@code pos} (etat {@code now}). Seul compte un bloc de la piece ou qui la touche par une face
     * (ou une case de depart) : piece close, s'il change de cote (air / paroi) ; piece ouverte, s'il devient une paroi (il peut la fermer).
     */
    public void blockChanged(BlockPos pos, BlockState now) {
        if (dirty || !near(pos.getX(), pos.getY(), pos.getZ(), 1)) {
            return;
        }
        long key = pos.asLong();
        if (key == pos().asLong()) {
            return;                                               //le diffuseur lui-meme (hors de la piece ; tier suivi a part)
        }
        //Piece ouverte (trop grande pour le tier : seules les premieres cases sont connues) : un mur pose n'importe ou pres d'elle peut la fermer
        if (!room.sealed()) {
            if (!RoomScanner.isAirPassable(level(), pos, now)) {
                dirty = true;
            }
            return;
        }
        boolean inside = room.cells().contains(key);
        boolean touches = inside || pos.distManhattan(pos()) == 1;
        for (int i = 0; i < 6 && !touches; i++) {
            Direction d = Directions.ALL[i];
            touches = room.cells().contains(BlockPos.offset(key, d));
        }
        if (!touches) {
            return;
        }
        boolean passable = RoomScanner.isAirPassable(level(), pos, now);
        if (room.sealed() ? inside != passable : !passable) {
            dirty = true;
        }
    }

    public long getTarget() {
        return (long) volume * OXYGEN_PER_BLOCK;
    }

    /** La piece est close, alimentee et pressurisee a 95 % au moins. */
    public boolean isPressurized() {
        return sealed && powered && volume > 0 && pressure >= getTarget() * 0.95;
    }

    public Room getRoom() {
        return room;
    }

    public long getEnergyUsage() {
        return ENERGY_BASE_FE + volume / 8;
    }

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(OXYGEN_MB, () -> pressure).add(TARGET, this::getTarget).add(VOLUME, () -> volume).add(SEALED, () -> sealed ? 1 : 0)
              .add(PRESSURIZED, () -> isPressurized() ? 1 : 0);
    }

    @Override
    public void load(CompoundTag tag) {
        pressure = tag.getLong("pressure");
        volume = tag.getInt("volume");
        sealed = tag.getBoolean("sealed");
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putLong("pressure", pressure);
        tag.putInt("volume", volume);
        tag.putBoolean("sealed", sealed);
    }
}

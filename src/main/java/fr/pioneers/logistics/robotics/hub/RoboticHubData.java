package fr.pioneers.logistics.robotics.hub;

import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.energy.EnergyBuffer;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.core.registries.PiRobotics;
import fr.pioneers.logistics.robotics.ItemRobotUnit;
import fr.pioneers.logistics.robotics.ItemRoboticUpgrade;
import fr.pioneers.logistics.robotics.Job;
import fr.pioneers.logistics.robotics.RobotUnitEntity;
import fr.pioneers.logistics.robotics.RoboticUpgrade;
import fr.pioneers.logistics.robotics.UnitKind;
import fr.pioneers.logistics.robotics.UnitStats;
import fr.pioneers.logistics.robotics.UnitTier;
import fr.pioneers.logistics.robotics.logistics.ChargePads;
import fr.pioneers.logistics.robotics.logistics.LogisticsRegistry;
import fr.pioneers.logistics.robotics.logistics.NetworkNode;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Hub robotique (multibloc du socle, sans Mekanism) : un cube de 3 x 3 x 3 dont le boitier du coin bas (le maitre) garde ces donnees ; le
 * genre d'unites se choisit dans l'interface. Il abrite de 5 a 20 unites selon ses modules d'extension, recharge les unites
 * au repos, couvre une zone du reseau robotique (comme un relais) et fait sortir ses unites par ses portes de baie quand l'ordonnanceur
 * leur confie un travail. Ses cartes d'amelioration s'appliquent a toutes ses unites. Pas de reglage : ce sont les ordres, les zones et
 * les points logistiques qui disent quoi faire.
 */
public class RoboticHubData implements NetworkNode {

    public static final int UNIT_SLOTS = 20, UPGRADE_SLOTS = 4, BUFFER_SLOTS = 18, BASE_CAPACITY = 5, PER_MODULE = 5, MAX_MODULES = 3;
    public static final long IDLE_FE = 20, LAUNCH_FE = 1_000, DOCK_CHARGE_FE = 2_000, BAY_CHARGE_FE = 3_000;
    /** Charge minimale (part de la batterie) pour sortir. */
    public static final double LAUNCH_CHARGE = 0.5;

    /** Positions des emplacements dans l'interface (partagees avec l'ecran). */
    public static final int UNITS_X = 8, UNITS_Y = 18, UPGRADES_X = 103, UPGRADES_Y = 60, MODULE_X = 141, MODULE_Y = 60, BUFFER_X = 8, BUFFER_Y = 104,
          INVENTORY_OFFSET = 68;

    public enum Status {IDLE, WORKING, NO_UNITS, NO_ENERGY, RECALLING}

    /** Porte de baie : position et face exterieure. */
    public record Bay(BlockPos pos, Direction side) {

        /** Point de sortie (et de retour) des unites. */
        public Vec3 exit(UnitKind kind) {
            Vec3 out = Vec3.atBottomCenterOf(pos.relative(side));
            if (side == Direction.UP) {
                return out.add(0, 0.3, 0);
            }
            return kind.flying ? out.add(0, 0.25, 0) : out;
        }
    }

    /** Boitier maitre (coin bas) qui porte ces donnees. */
    @Nullable
    private final TileEntityRoboticCasing tile;
    public final EnergyBuffer energyContainer;
    private int kindIndex = UnitKind.ROBOT.ordinal();
    private boolean kindChanged;
    private int active;
    private int status;
    private long completed;
    private long lastEnergyUsed;
    private boolean formed;
    @Nullable
    private BlockPos min;

    private final List<ItemSlot> slots = new ArrayList<>();
    private final List<ItemSlot> unitSlots = new ArrayList<>();
    private final List<ItemSlot> upgradeSlots = new ArrayList<>();
    private final List<ItemSlot> buffer = new ArrayList<>();
    private final ItemSlot moduleSlot;
    /** Charge (FE) des unites au repos, emplacement par emplacement. */
    private final long[] charge = new long[UNIT_SLOTS];
    /** Unites dehors : identifiant d'entite -> emplacement. */
    private final Int2IntOpenHashMap out = new Int2IntOpenHashMap();
    private final List<Bay> bays = new ArrayList<>();
    private final ChargePads pads = new ChargePads();
    private boolean recalling;
    private long usedThisTick;

    /** Client : tiers des unites au repos (-1 : vide ou dehors), unites dehors. */
    private byte[] clientDocked = new byte[0];
    private boolean clientWorking;

    public RoboticHubData(@Nullable TileEntityRoboticCasing tile) {
        this.tile = tile;
        out.defaultReturnValue(-1);
        Arrays.fill(charge, -1);
        energyContainer = new EnergyBuffer(this::energyCapacityFE, Integer.MAX_VALUE, 0, this::markDirty);
        for (int i = 0; i < UNIT_SLOTS; i++) {
            int index = i;
            BiPredicate<ItemStack, Automation> canExtract = (stack, type) -> !out.containsValue(index);
            BiPredicate<ItemStack, Automation> canInsert = (stack, type) -> index < capacity();
            ItemSlot slot = new ItemSlot(() -> 1, this::acceptsUnit, canInsert, canExtract, this::markDirty, UNITS_X + (i % 5) * 18, UNITS_Y + (i / 5) * 18) {
                @Override
                public void setStack(@NotNull ItemStack stack) {
                    //Une unite qui arrive dans l'emplacement part de sa charge d'usine (pleine)
                    if (!ItemStack.isSameItem(stack, getStack())) {
                        charge[index] = -1;
                    }
                    super.setStack(stack);
                }
            };
            unitSlots.add(slot);
            slots.add(slot);
        }
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemSlot slot = ItemSlot.of(stack -> stack.getItem() instanceof ItemRoboticUpgrade card && card.upgrade.forHub, this::markDirty,
                  UPGRADES_X + (i % 2) * 18, UPGRADES_Y + (i / 2) * 18);
            upgradeSlots.add(slot);
            slots.add(slot);
        }
        moduleSlot = new ItemSlot(() -> MAX_MODULES, stack -> stack.is(PiRobotics.BAY_MODULE.get()), (stack, type) -> true, (stack, type) -> true,
              this::markDirty, MODULE_X, MODULE_Y);
        slots.add(moduleSlot);
        for (int i = 0; i < BUFFER_SLOTS; i++) {
            ItemSlot slot = ItemSlot.of(stack -> !(stack.getItem() instanceof ItemRobotUnit), this::markDirty, BUFFER_X + (i % 9) * 18,
                  BUFFER_Y + (i / 9) * 18);
            buffer.add(slot);
            slots.add(slot);
        }
    }

    public void markDirty() {
        if (tile != null) {
            tile.setChanged();
        }
    }

    /** Toutes les cases (interface) : unites, ameliorations, module, tampon. */
    public List<ItemSlot> getSlots() {
        return slots;
    }

    private boolean acceptsUnit(ItemStack stack) {
        return stack.getItem() instanceof ItemRobotUnit unit && unit.kind == getKind();
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    void setStructure(BlockPos min, List<Bay> bays) {
        this.min = min.immutable();
        this.bays.clear();
        this.bays.addAll(bays);
        formed = true;
    }

    /** Hub defait : plus de noeud, les unites dehors se dissolvent (elles sont toujours dans leur case). */
    void unform(Level world) {
        if (formed) {
            remove(world);
        }
        formed = false;
        bays.clear();
    }

    public boolean isFormed() {
        return formed && min != null;
    }

    void setClientFormed(boolean formed, @Nullable BlockPos min) {
        this.formed = formed;
        this.min = min == null ? null : min.immutable();
    }

    public BlockPos getMinPos() {
        return min != null ? min : tile != null ? tile.getBlockPos() : BlockPos.ZERO;
    }

    public BlockPos getMaxPos() {
        return getMinPos().offset(RoboticHubValidator.WIDTH - 1, RoboticHubValidator.HEIGHT - 1, RoboticHubValidator.WIDTH - 1);
    }

    /** Limites (null : pas forme). */
    @Nullable
    public AABB getBounds() {
        return isFormed() ? new AABB(getMinPos(), getMaxPos().offset(1, 1, 1)) : null;
    }

    /** Le hub contient-il quelque chose (unites, cartes, tampon) ? */
    public boolean hasContents() {
        return slots.stream().anyMatch(slot -> !slot.isEmpty()) || completed > 0;
    }

    /** Genre d'unites accueilli : se change dans l'interface, hub vide seulement. */
    public boolean canChangeKind() {
        return unitCount() == 0 && out.isEmpty();
    }

    /** Genre sauvegarde (cache du hub). */
    void restoreKind(int index) {
        kindIndex = Mth.clamp(index, 0, UnitKind.values().length - 1);
    }

    public void setKind(UnitKind kind) {
        if (canChangeKind() && kind.ordinal() != kindIndex) {
            kindIndex = kind.ordinal();
            kindChanged = true;
            markDirty();
        }
    }

    public UnitKind getKind() {
        return UnitKind.values()[Mth.clamp(kindIndex, 0, UnitKind.values().length - 1)];
    }

    public List<Bay> getBays() {
        return bays;
    }

    public BlockPos getCenter() {
        BlockPos min = getMinPos(), max = getMaxPos();
        return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2);
    }

    /** Places : 5, puis 5 de plus par module d'extension (3 au plus). */
    public int capacity() {
        return BASE_CAPACITY + PER_MODULE * Math.min(MAX_MODULES, moduleSlot == null ? 0 : moduleSlot.getCount());
    }

    public long energyCapacityFE() {
        return switch (getKind()) {
            case DRONE -> 2_000_000;
            case ROBOT -> 5_000_000;
            case ROVER -> 10_000_000;
            case TURTLE -> 4_000_000;
        };
    }

    // ------------------------------------------------------------------
    // Ameliorations
    // ------------------------------------------------------------------

    /** Cartes installees par amelioration (bornees). */
    public int[] cards() {
        int[] cards = new int[RoboticUpgrade.values().length];
        for (ItemSlot slot : upgradeSlots) {
            if (slot.getStack().getItem() instanceof ItemRoboticUpgrade card) {
                cards[card.upgrade.ordinal()] += slot.getCount();
            }
        }
        for (RoboticUpgrade upgrade : RoboticUpgrade.values()) {
            cards[upgrade.ordinal()] = Math.min(upgrade.max, cards[upgrade.ordinal()]);
        }
        return cards;
    }

    public UnitStats stats() {
        return UnitStats.of(cards());
    }

    /** Batterie pleine d'une unite de ce hub (selon son tier et les cartes d'autonomie). */
    public long batteryOf(UnitTier tier) {
        return Math.round(getKind().battery * tier.capacity * stats().battery());
    }

    /** Facteur d'encombrement : plus il y a d'unites dehors, plus chacune consomme. */
    public double crowdFactor() {
        return Math.min(2, 1 + 0.05 * Math.max(0, active - 1)) * PiCommonConfig.get(PiCommonConfig.ROBOTICS_ENERGY, 1.0);
    }

    // ------------------------------------------------------------------
    // Fonctionnement
    // ------------------------------------------------------------------

    /** Serveur, chaque tick du maitre ; renvoie vrai s'il faut renvoyer l'etat aux clients. */
    public boolean tick(Level world) {
        boolean needsPacket = false;
        if (!(world instanceof ServerLevel server)) {
            return needsPacket;
        }
        long now = server.getGameTime();
        //Unites disparues (dechargees, retirees) : on les considere rentrees
        out.int2IntEntrySet().removeIf(entry -> !(server.getEntity(entry.getIntKey()) instanceof RobotUnitEntity unit) || !unit.isAlive());
        if (now % 20 == 0) {
            pads.cleanup(server);
        }
        active = out.size();
        long idle = IDLE_FE;
        boolean powered = energyContainer.getStored() >= idle;
        LogisticsRegistry.get(server).registerNode(this, now);
        if (powered) {
            energyContainer.extract(idle, false);
            rechargeDocked();
        } else if (active > 0) {
            recalling = true;
        }
        lastEnergyUsed = powered ? idle + usedThisTick : 0;
        usedThisTick = 0;
        if (recalling && active == 0) {
            recalling = false;
        }
        Status current;
        if (!powered) {
            current = Status.NO_ENERGY;
        } else if (recalling) {
            current = Status.RECALLING;
        } else if (unitCount() == 0) {
            current = Status.NO_UNITS;
        } else {
            current = active > 0 ? Status.WORKING : Status.IDLE;
        }
        status = current.ordinal();
        byte[] docked = dockedTiers();
        boolean working = active > 0;
        if (!Arrays.equals(docked, clientDocked) || working != clientWorking || kindChanged) {
            kindChanged = false;
            clientDocked = docked;
            clientWorking = working;
            needsPacket = true;
        }
        return needsPacket;
    }

    /** Recharge des unites au repos. */
    private void rechargeDocked() {
        for (int i = 0; i < UNIT_SLOTS; i++) {
            UnitTier tier = tierAt(i);
            if (tier == null || out.containsValue(i)) {
                continue;
            }
            long max = batteryOf(tier);
            if (charge[i] < 0 || charge[i] > max) {
                charge[i] = Math.min(max, Math.max(charge[i], max));
                continue;
            }
            //Au repos : pleine en environ 2 s
            long need = Math.min(Math.max(DOCK_CHARGE_FE, max / 40), max - charge[i]);
            if (need <= 0) {
                continue;
            }
            if (energyContainer.getStored() >= need) {
                energyContainer.extract(need, false);
                charge[i] += need;
                usedThisTick += need;
            }
        }
    }

    public void remove(Level world) {
        if (world instanceof ServerLevel server) {
            LogisticsRegistry.get(server).unregisterNode(this);
        }
        out.clear();
        active = 0;
    }

    @Nullable
    public UnitTier tierAt(int slot) {
        if (slot < 0 || slot >= unitSlots.size()) {
            return null;
        }
        ItemStack stack = unitSlots.get(slot).getStack();
        return stack.getItem() instanceof ItemRobotUnit unit && unit.kind == getKind() ? unit.tier : null;
    }

    /** Charge d'une unite au repos (part de la batterie). */
    public double chargeRatio(int slot) {
        UnitTier tier = tierAt(slot);
        if (tier == null) {
            return 0;
        }
        long max = batteryOf(tier);
        return charge[slot] < 0 ? 1 : Math.min(1, charge[slot] / (double) Math.max(1, max));
    }

    /** Emplacements des unites pretes a sortir (au repos, chargees, dans les places ouvertes), du meilleur tier au moins bon. */
    public List<Integer> availableUnits() {
        List<Integer> list = new ArrayList<>();
        if (recalling || !isPowered()) {
            return list;
        }
        int capacity = capacity();
        for (int i = 0; i < Math.min(capacity, UNIT_SLOTS); i++) {
            if (tierAt(i) != null && !out.containsValue(i) && chargeRatio(i) >= LAUNCH_CHARGE) {
                list.add(i);
            }
        }
        list.sort((a, b) -> tierAt(b).ordinal() - tierAt(a).ordinal());
        return list;
    }

    public boolean isPowered() {
        return energyContainer.getStored() >= IDLE_FE + LAUNCH_FE;
    }

    /** Porte la plus proche d'un point. */
    @Nullable
    public Bay nearestBay(Vec3 target) {
        Bay best = null;
        double bestDist = Double.MAX_VALUE;
        boolean ground = !getKind().flying && bays.stream().anyMatch(bay -> bay.side() != Direction.UP);
        for (Bay bay : bays) {
            if (ground && bay.side() == Direction.UP) {
                //Une porte sur le toit ne sert qu'aux drones
                continue;
            }
            double dist = Vec3.atCenterOf(bay.pos()).distanceToSqr(target);
            if (dist < bestDist) {
                best = bay;
                bestDist = dist;
            }
        }
        return best;
    }

    /** Fait sortir l'unite d'un emplacement pour un travail ; null si impossible. */
    @Nullable
    public RobotUnitEntity launch(ServerLevel level, int slot, Job job) {
        UnitTier tier = tierAt(slot);
        if (tier == null || out.containsValue(slot) || recalling) {
            return null;
        }
        long launch = (long) (LAUNCH_FE * tier.energy);
        if (energyContainer.getStored() < launch) {
            return null;
        }
        Bay bay = nearestBay(job.anchor());
        if (bay == null) {
            return null;
        }
        RobotUnitEntity unit = PiRobotics.unitEntity(getKind()).create(level);
        if (unit == null) {
            return null;
        }
        Vec3 exit = bay.exit(getKind());
        unit.moveTo(exit.x, exit.y, exit.z, bay.side().getAxis().isHorizontal() ? bay.side().toYRot() : 0, 0);
        long battery = batteryOf(tier);
        unit.setup(bay.pos(), slot, tier, job.task, stats(), battery, charge[slot] < 0 ? battery : charge[slot]);
        unit.assign(job);
        ItemStack item = unitSlots.get(slot).getStack();
        if (item.hasCustomHoverName()) {
            //Le nom donne a l'unite suit l'objet dans le hub
            unit.setCustomName(item.getHoverName());
        }
        level.addFreshEntity(unit);
        out.put(unit.getId(), slot);
        active = out.size();
        energyContainer.extract(launch, false);
        openDoor(level, bay);
        return unit;
    }

    /** Animation de la porte de baie (et son bruit). */
    public static void openDoor(ServerLevel level, Bay bay) {
        level.blockEvent(bay.pos(), level.getBlockState(bay.pos()).getBlock(), 1, 0);
        level.playSound(null, bay.pos(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.35F, 1.6F);
    }

    /** Renomme l'unite d'une case (l'objet garde le nom pour les prochaines sorties). */
    public void renameSlot(int slot, @Nullable Component name) {
        if (slot < 0 || slot >= unitSlots.size()) {
            return;
        }
        ItemStack stack = unitSlots.get(slot).getStack();
        if (stack.isEmpty()) {
            return;
        }
        ItemStack copy = stack.copy();
        if (name == null) {
            copy.resetHoverName();
        } else {
            copy.setHoverName(name);
        }
        unitSlots.get(slot).setStack(copy);
        markDirty();
    }

    /** L'unite de cette case est-elle dehors ? */
    public boolean isOut(int slot) {
        return out.containsValue(slot);
    }

    public boolean isOut(RobotUnitEntity unit) {
        return out.get(unit.getId()) == unit.getSlot();
    }

    /** Une unite rentre : sa soute va dans le tampon (le surplus tombe devant la porte), sa batterie est gardee. */
    public void dock(ServerLevel level, RobotUnitEntity unit, boolean finished) {
        out.remove(unit.getId());
        pads.leave(unit);
        active = out.size();
        if (finished) {
            completed++;
        }
        if (unit.getSlot() >= 0 && unit.getSlot() < UNIT_SLOTS) {
            charge[unit.getSlot()] = unit.getBattery();
        }
        Bay bay = nearestBay(unit.position());
        for (ItemStack stack : unit.getCargo().items) {
            ItemStack rest = store(stack);
            if (!rest.isEmpty()) {
                Vec3 at = bay == null ? unit.position() : bay.exit(getKind());
                level.addFreshEntity(new ItemEntity(level, at.x, at.y + 0.3, at.z, rest));
            }
        }
        unit.getCargo().items.clear();
        if (bay != null) {
            openDoor(level, bay);
        }
        markDirty();
    }

    /** Range une pile dans le tampon ; renvoie le reste. */
    public ItemStack store(ItemStack stack) {
        ItemStack rest = stack;
        for (ItemSlot slot : buffer) {
            if (rest.isEmpty()) {
                break;
            }
            rest = slot.insert(rest, Action.EXECUTE, Automation.INTERNAL);
        }
        return rest;
    }

    public List<ItemSlot> getBuffer() {
        return buffer;
    }

    public void recallAll() {
        if (!out.isEmpty()) {
            recalling = true;
            markDirty();
        }
    }

    public boolean isRecalling() {
        return recalling;
    }

    // ------------------------------------------------------------------
    // Noeud du reseau : couverture et recharge aux portes
    // ------------------------------------------------------------------

    @Override
    public BlockPos nodeCenter() {
        return getCenter();
    }

    /** Couverture : celle du meilleur tier present (32, 48, 64, 96), au moins celle du genre. */
    @Override
    public int coverage() {
        int best = getKind().coverage;
        for (int i = 0; i < UNIT_SLOTS; i++) {
            UnitTier tier = tierAt(i);
            if (tier != null) {
                best = Math.max(best, tier.range);
            }
        }
        return best;
    }

    /** Un hub forme fait toujours partie du reseau (sans energie, il ne lance simplement plus d'unites). */
    @Override
    public boolean nodeValid() {
        return isFormed();
    }

    /** Deux places par porte : devant, et un peu a cote. */
    private List<Vec3> spots(UnitKind kind) {
        List<Vec3> list = new ArrayList<>();
        for (Bay bay : bays) {
            Vec3 exit = bay.exit(kind);
            list.add(exit);
            Direction side = bay.side().getAxis().isHorizontal() ? bay.side().getClockWise() : Direction.EAST;
            list.add(exit.add(side.getStepX() * 1.2, kind.flying ? 0.6 : 0, side.getStepZ() * 1.2));
        }
        return list;
    }

    @Nullable
    @Override
    public Vec3 chargeSpot(RobotUnitEntity unit) {
        return pads.reserve(unit, spots(unit.getKind()), 0);
    }

    @Override
    public long charge(RobotUnitEntity unit, long wanted) {
        //Recharge rapide : une batterie pleine en environ 3 s
        long give = Math.min(wanted, Math.max(BAY_CHARGE_FE, unit.getMaxBattery() / 60));
        if (give <= 0 || energyContainer.getStored() < give) {
            return 0;
        }
        energyContainer.extract(give, false);
        usedThisTick += give;
        return give;
    }

    @Override
    public void leave(RobotUnitEntity unit) {
        pads.leave(unit);
    }

    @Override
    public float crowd() {
        return pads.size() / (float) Math.max(1, bays.size() * 2);
    }

    @Override
    public Vec3 idleSpot(RobotUnitEntity unit) {
        Bay bay = nearestBay(unit.position());
        if (bay == null) {
            return Vec3.atCenterOf(getCenter());
        }
        double a = (unit.getId() * 0.618034 % 1) * Mth.TWO_PI;
        Vec3 exit = bay.exit(getKind());
        Vec3 out = Vec3.atLowerCornerOf(bay.side().getNormal()).scale(2.5);
        return exit.add(out.x + Math.cos(a) * 1.5, unit.getKind().flying ? 1.5 : 0, out.z + Math.sin(a) * 1.5);
    }

    // ------------------------------------------------------------------
    // Interface et rendu
    // ------------------------------------------------------------------

    public Status getStatus() {
        return Status.values()[Mth.clamp(status, 0, Status.values().length - 1)];
    }

    public int getActive() {
        return active;
    }

    /** Unites presentes (dehors comprises). */
    public int unitCount() {
        int count = 0;
        for (int i = 0; i < UNIT_SLOTS; i++) {
            if (tierAt(i) != null) {
                count++;
            }
        }
        return count;
    }

    public long getCompleted() {
        return completed;
    }

    void setCharges(long[] charges) {
        System.arraycopy(charges, 0, charge, 0, Math.min(charges.length, charge.length));
    }

    public long getLastEnergyUsed() {
        return lastEnergyUsed;
    }

    /** Ancienne portee (affichage) : la couverture du hub. */
    public int getRange() {
        return coverage();
    }

    /** Le point est-il couvert par ce hub ? */
    public boolean inRange(BlockPos pos) {
        return covers(pos);
    }

    /** Tiers des unites au repos, emplacement par emplacement (-1 : vide ou dehors). */
    private byte[] dockedTiers() {
        byte[] tiers = new byte[Math.min(capacity(), UNIT_SLOTS)];
        for (int i = 0; i < tiers.length; i++) {
            UnitTier tier = out.containsValue(i) ? null : tierAt(i);
            tiers[i] = (byte) (tier == null ? -1 : tier.ordinal());
        }
        return tiers;
    }

    public byte[] getClientDocked() {
        return clientDocked;
    }

    public boolean isClientWorking() {
        return clientWorking;
    }

    public void writeUpdateTag(CompoundTag tag) {
        tag.putByteArray("docked", clientDocked);
        tag.putBoolean("working", clientWorking);
        tag.putByte("kind", (byte) kindIndex);
    }

    public void readUpdateTag(CompoundTag tag) {
        clientDocked = tag.getByteArray("docked");
        clientWorking = tag.getBoolean("working");
        kindIndex = tag.getByte("kind");
    }

    // ------------------------------------------------------------------
    // Interface (valeurs synchronisees par le menu)
    // ------------------------------------------------------------------

    /** Cases dont l'unite est dehors (un bit par case). */
    public int outMask() {
        int mask = 0;
        for (int slot : out.values()) {
            if (slot >= 0 && slot < 31) {
                mask |= 1 << slot;
            }
        }
        return mask;
    }

    int kindIndex() {
        return kindIndex;
    }

    int statusIndex() {
        return status;
    }

    // ------------------------------------------------------------------
    // Sauvegarde (dans le boitier maitre)
    // ------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("inventory", ItemSlots.save(slots));
        tag.putLong("energy", energyContainer.getStored());
        tag.putByte("unitKind", (byte) kindIndex);
        tag.putLong("completed", completed);
        tag.putLongArray("charges", charge);
        return tag;
    }

    public void load(CompoundTag tag) {
        ItemSlots.load(slots, tag.getList("inventory", Tag.TAG_COMPOUND));
        kindIndex = Mth.clamp(tag.getByte("unitKind"), 0, UnitKind.values().length - 1);
        energyContainer.setStored(tag.getLong("energy"));
        completed = tag.getLong("completed");
        Arrays.fill(charge, -1);
        setCharges(tag.getLongArray("charges"));
    }

    /** Reprend le contenu d'un autre hub (deplace ou reconstruit ailleurs). */
    void takeFrom(RoboticHubData other) {
        load(other.save());
        other.clearContents();
    }

    void clearContents() {
        for (ItemSlot slot : slots) {
            slot.load(new CompoundTag());
        }
        energyContainer.setStored(0);
        completed = 0;
        Arrays.fill(charge, -1);
        markDirty();
    }

    @NotNull
    @Override
    public String toString() {
        return "RoboticHub[" + getKind() + " @ " + getMinPos() + "]";
    }
}

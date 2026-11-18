package fr.pioneers.logistics.pneumatic.network;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.pressure.PressureVolume;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Station de tubes pneumatiques (transport de voyageurs) : un nom (son adresse sur le reseau) et une reserve d'air comprime
 * ({@value #VOLUME} L, jusqu'a {@value #MAX_PRESSURE} bar). Un voyage part d'ici et coute {@value #AIR_PER_TRIP} L + {@value #AIR_PER_BLOCK} L
 * par bloc, a au moins {@value #MIN_PRESSURE} bar. {@code active} : une capsule vient de partir ou d'arriver (quelques secondes).
 * Les stations des versions precedentes (transport d'objets) gardent leur nom ; leurs objets en attente tombent au sol au premier tick.
 */
public class TubeStationTile extends PiBlockEntity {

    public static final int NAME_LENGTH = 24;
    public static final double AIR_PER_TRIP = 60, AIR_PER_BLOCK = 3, MIN_PRESSURE = 1, VOLUME = 800, MAX_PRESSURE = 8, CONDUCTANCE = 80;
    /** Duree (ticks) pendant laquelle la station reste allumee apres un depart ou une arrivee. */
    private static final int GLOW = 60;

    private final PressureVolume air = new PressureVolume(VOLUME, MAX_PRESSURE, CONDUCTANCE, this::setChanged);
    private final LazyOptional<PressureVolume> airCap = LazyOptional.of(() -> air);
    private String name = "";
    private int glow;
    /** Objets laisses par l'ancien transport d'objets (a lacher au sol). */
    private final List<ItemStack> legacy = new ArrayList<>();

    public TubeStationTile(BlockPos pos, BlockState state) {
        super(PiPneumaticNetwork.STATION_TILE.get(), pos, state);
    }

    public String name() {
        return name;
    }

    public PressureVolume air() {
        return air;
    }

    /** Air que coute un voyage de {@code blocks} blocs. */
    public static double cost(int blocks) {
        return AIR_PER_TRIP + AIR_PER_BLOCK * blocks;
    }

    /** Assez d'air (et de pression) pour un voyage de {@code blocks} blocs ? */
    public boolean canAfford(int blocks) {
        return air.getPressure() >= MIN_PRESSURE && air.getAir() >= cost(blocks);
    }

    /** Preleve l'air d'un voyage (verifie avant par {@link #canAfford}). */
    public void pay(int blocks) {
        air.extractAir(cost(blocks), false);
        light();
    }

    /** Allume la station quelques secondes (depart, arrivee). */
    public void light() {
        glow = GLOW;
        setChanged();
    }

    /** Nouveau nom (deja nettoye) : les autres stations le retrouveront a leur prochain parcours. */
    public void rename(String name) {
        if (!name.equals(this.name)) {
            this.name = name;
            if (level != null) {
                TubeRoutes.changed(level);
            }
            setChanged();
        }
    }

    /** Case devant la bouche (sortie des voyageurs). */
    public BlockPos mouth() {
        return worldPosition.relative(getBlockState().getValue(TubeStationBlock.FACING));
    }

    /** Bloc casse : les objets herites de l'ancien transport tombent au sol. */
    void dropLegacy() {
        if (level != null) {
            legacy.forEach(stack -> TubeStationBlock.drop(level, worldPosition, stack));
            legacy.clear();
        }
    }

    void tickServer() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (!legacy.isEmpty()) {
            legacy.forEach(stack -> TubeStationBlock.drop(server, mouth(), stack));
            legacy.clear();
            setChanged();
        }
        if (glow > 0) {
            glow--;
        }
        if (server.getGameTime() % 10 == 0) {
            boolean active = glow > 0;
            BlockState state = getBlockState();
            if (state.getValue(TubeStationBlock.ACTIVE) != active) {
                server.setBlock(worldPosition, state.setValue(TubeStationBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Comparateur : pression rapportee a la pression maximale. */
    public int comparator() {
        double ratio = air.ratio();
        return ratio <= 0 ? 0 : 1 + (int) Math.floor(ratio * 14);
    }

    // ------------------------------------------------------------------
    // Capacites et sauvegarde
    // ------------------------------------------------------------------

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == PioneersAPI.PRESSURE_HANDLER ? airCap.cast() : super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        airCap.invalidate();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("air", air.serializeNBT());
        tag.putString("name", name);
        if (!legacy.isEmpty()) {
            ListTag list = new ListTag();
            legacy.forEach(stack -> list.add(stack.save(new CompoundTag())));
            tag.put("legacy", list);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        air.deserializeNBT(tag.getCompound("air"));
        name = tag.getString("name");
        legacy.clear();
        for (Tag t : tag.getList("legacy", Tag.TAG_COMPOUND)) {
            legacy.add(ItemStack.of((CompoundTag) t));
        }
        //Ancien transport d'objets : file d'envoi, arrivees et capsules en route rendent leurs objets
        for (String key : new String[]{"outbox", "inbox"}) {
            for (Tag t : tag.getCompound(key).getList("Items", Tag.TAG_COMPOUND)) {
                ItemStack stack = ItemStack.of((CompoundTag) t);
                if (!stack.isEmpty()) {
                    legacy.add(stack);
                }
            }
        }
        for (Tag t : tag.getList("flights", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of(((CompoundTag) t).getCompound("stack"));
            if (!stack.isEmpty()) {
                legacy.add(stack);
            }
        }
    }
}

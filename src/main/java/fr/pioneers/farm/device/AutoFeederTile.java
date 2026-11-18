package fr.pioneers.farm.device;

import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.animal.AnimalSpecies;
import fr.pioneers.farm.animal.FarmSites;
import fr.pioneers.farm.kitchen.StationTiles;
import fr.pioneers.farm.ui.FarmLayout;
import fr.pioneers.farm.ui.FluidPair;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Distributeur automatique : 9 cases d'aliments (tout ce qu'une bete mange) et 8 000 mB d'eau. Toutes les 5 s, il remplit les mangeoires a
 * {@link #RADIUS} blocs (jusqu'a {@link #FEED_LEVEL} objets, {@link #FEED_STEP} par passage) et les abreuvoirs (jusqu'a {@link #WATER_LEVEL} mB,
 * 1 000 par passage). Entonnoirs et tuyaux : aliments par toutes les faces, eau par les tuyaux. La vis tourne et les grains tombent (rendu).
 * 1.0.5-beta (lot 41) : interface ({@link FarmDeviceTile}) : 9 cases d'aliments, cuve d'eau et sa paire « Vider ».
 */
public class AutoFeederTile extends FarmDeviceTile {

    public static final int RADIUS = 8, FEED_LEVEL = 128, FEED_STEP = 16, WATER_LEVEL = 3000, PERIOD = 100;
    /** 1.0.5-beta (lot 41) : 9 cases d'aliments, puis la paire « Vider » (seau d'eau ; avant : au clic). */
    public static final int FEED = 9, DRAIN_IN = 9, DRAIN_OUT = 10;
    /** Instant du dernier passage utile (synchronise : la vis tourne 3 s). */
    private long lastWork = -1000;
    private int served, feeders, troughs;

    public AutoFeederTile(BlockPos pos, BlockState state) {
        super(pos, state, 11, 8000);
    }

    @Override
    protected FarmLayout layout() {
        return FarmLayout.builder().tank(DRAIN_IN, DRAIN_OUT, -1, -1)
              .store(StationTiles.range(0, FEED), 3, "gui.pioneers.farm.slot.feed").build();
    }

    public long lastWork() {
        return lastWork;
    }

    public float fillLevel() {
        int n = 0;
        for (int i = 0; i < FEED; i++) {
            n += items.get(i).getCount();
        }
        return Math.min(1F, n / 576F);
    }

    @Override
    public void serverTick() {
        if (level == null || (level.getGameTime() + worldPosition.asLong()) % PERIOD != 0 || level.hasNeighborSignal(worldPosition)) {
            return;
        }
        if (tank != null && level.isRainingAt(worldPosition.above())) {
            tank.fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.EXECUTE);
        }
        ServerLevel server = (ServerLevel) level;
        int work = 0;
        List<BlockPos> fs = FarmSites.within(level, worldPosition, RADIUS, false), ts = FarmSites.within(level, worldPosition, RADIUS, true);
        if (fs.size() != feeders || ts.size() != troughs) {
            feeders = fs.size();
            troughs = ts.size();
            sync();
        }
        for (BlockPos p : fs) {
            //1.9 (MAJ RP, bloc 4) : pas de distribution a travers une frontiere de claim refusee
            if (!ClanApi.canTransfer(level, worldPosition, p)) {
                continue;
            }
            if (level.getBlockEntity(p) instanceof StationTiles.Feeder feeder && feeder.total() < FEED_LEVEL) {
                ItemStack moved = feed(feeder);
                if (!moved.isEmpty()) {
                    work++;
                    server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, moved), p.getX() + 0.5, p.getY() + 0.9, p.getZ() + 0.5, 6, 0.3, 0.05,
                          0.2, 0.02);
                }
            }
        }
        for (BlockPos p : ts) {
            BlockEntity be = ClanApi.canTransfer(level, worldPosition, p) ? level.getBlockEntity(p) : null;
            if (be == null || tank == null || tank.isEmpty()) {
                continue;
            }
            IFluidHandler h = be.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).orElse(null);
            if (h == null || h.getFluidInTank(0).getAmount() >= WATER_LEVEL) {
                continue;
            }
            int n = h.fill(tank.drain(1000, IFluidHandler.FluidAction.SIMULATE), IFluidHandler.FluidAction.EXECUTE);
            if (n > 0) {
                tank.drain(n, IFluidHandler.FluidAction.EXECUTE);
                work++;
                server.sendParticles(ParticleTypes.SPLASH, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 10, 0.4, 0.05, 0.3, 0.1);
            }
        }
        if (work > 0) {
            lastWork = level.getGameTime();
            served += work;
            sound(SoundEvents.GRINDSTONE_USE, 0.25F, 1.6F);
            sync();
        }
    }

    /** Verse jusqu'a {@link #FEED_STEP} aliments dans la mangeoire ; rend un exemple de ce qui est passe (particules). */
    private ItemStack feed(StationTiles.Feeder feeder) {
        IItemHandler h = feeder.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        if (h == null) {
            return ItemStack.EMPTY;
        }
        int left = FEED_STEP;
        ItemStack shown = ItemStack.EMPTY;
        for (int i = 0; i < FEED && left > 0; i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty()) {
                continue;
            }
            ItemStack offer = s.copyWithCount(Math.min(left, s.getCount()));
            for (int slot = 0; slot < h.getSlots() && !offer.isEmpty(); slot++) {
                offer = h.insertItem(slot, offer, false);
            }
            int moved = Math.min(left, s.getCount()) - offer.getCount();
            if (moved > 0) {
                shown = s.copyWithCount(1);
                s.shrink(moved);
                left -= moved;
            }
        }
        return shown;
    }



    @Override
    protected boolean canInsert(int slot, ItemStack stack, @Nullable Direction side) {
        return slot < FEED ? AnimalSpecies.anyFeed(stack) : slot == DRAIN_IN && FluidPair.drainable(stack);
    }

    @Override
    protected boolean canExtract(int slot, @Nullable Direction side) {
        return slot == DRAIN_OUT;
    }

    @Override
    protected int accent() {
        return 0xC8A050;
    }

    @Override
    protected void describe(FarmCard card, boolean sneaking) {
        int n = 0;
        for (int i = 0; i < FEED; i++) {
            n += items.get(i).getCount();
        }
        //1.6 : fiche synthetique
        card.add(FarmCard.Line.gauge(FarmCard.label("feed"), fillLevel(), Component.literal(String.valueOf(n)), 0xC8A050));
        int linked = feeders + troughs > 0 ? FarmCard.OK : FarmCard.WARN;
        card.add(FarmCard.Line.pair(FarmCard.label("feeders"), Component.literal(String.valueOf(feeders)), linked));
        card.add(FarmCard.Line.pair(FarmCard.label("troughs"), Component.literal(String.valueOf(troughs)), linked));
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putLong("work", lastWork);
        tag.putInt("served", served);
        tag.putByte("feeders", (byte) feeders);
        tag.putByte("troughs", (byte) troughs);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        lastWork = tag.contains("work") ? tag.getLong("work") : -1000;
        served = tag.getInt("served");
        feeders = tag.getByte("feeders");
        troughs = tag.getByte("troughs");
    }
}

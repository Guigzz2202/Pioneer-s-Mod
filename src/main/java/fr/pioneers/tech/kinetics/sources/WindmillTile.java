package fr.pioneers.tech.kinetics.sources;

import fr.pioneers.tech.kinetics.KineticEvents;
import fr.pioneers.tech.kinetics.KineticTile;
import fr.pioneers.tech.kinetics.KineticsConfig;
import fr.pioneers.tech.kinetics.PiKinetics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class WindmillTile extends KineticTile {

    public static final int MIN_RADIUS = 3, MAX_RADIUS = 7, MAX_SAILS = 8;
    /** Nombre d'ailes et leur type (1 toile, 2 planches) ; longueur (blocs). */
    private int sails, type, radius = 5;
    private boolean blocked;
    private double tau0, free = 1, wind;
    private int sentSails = -1, sentRadius = -1, sentType = -1;
    private boolean sentBlocked;

    public WindmillTile(BlockPos pos, BlockState state) {
        super(PiKinetics.WINDMILL_TILE.get(), pos, state);
    }

    public Direction facing() {
        return getBlockState().getValue(WindmillBlock.FACING);
    }

    public int sails() {
        return sails;
    }

    public int sailType() {
        return type;
    }

    public int radius() {
        return radius;
    }

    public boolean blocked() {
        return blocked;
    }

    boolean addSail(int kind) {
        if (sails >= MAX_SAILS || sails > 0 && kind != type) {
            return false;
        }
        type = kind;
        sails++;
        recheck();
        markClientsDirty();
        return true;
    }

    ItemStack removeSail() {
        if (sails == 0) {
            return ItemStack.EMPTY;
        }
        sails--;
        ItemStack stack = SailItem.stack(type);
        if (sails == 0) {
            type = 0;
        }
        recheck();
        markClientsDirty();
        return stack;
    }

    List<ItemStack> allSails() {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < sails; i++) {
            list.add(SailItem.stack(type));
        }
        return list;
    }

    void cycleRadius() {
        radius = radius >= MAX_RADIUS ? MIN_RADIUS : radius + 1;
        recheck();
        markClientsDirty();
    }

    @Override
    public double shaftFactor(Direction face) {
        return face == facing().getOpposite() ? 1 : 0;
    }

    @Override
    public double sourceTorque() {
        return blocked ? 0 : tau0;
    }

    @Override
    public double sourceFreeSpeed() {
        return free;
    }

    @Override
    public double inertia() {
        return 30 + sails * radius * radius * (type == 2 ? 2.5 : 1.2);
    }

    @Override
    public double maxTorque() {
        return 40_000;
    }

    @Override
    public double maxSpeed() {
        return 300;
    }

    @Override
    protected double renderRadius() {
        return radius + 0.5;
    }

    /** Le plan des ailes doit etre degage (air, plantes basses, eau exclue). */
    private void recheck() {
        if (level == null || sails == 0) {
            blocked = false;
            return;
        }
        Direction f = facing();
        Direction h = f.getClockWise();
        boolean hit = false;
        for (int a = -radius; a <= radius && !hit; a++) {
            for (int b = -radius; b <= radius; b++) {
                if ((a == 0 && b == 0) || a * a + b * b > (radius + 0.4) * (radius + 0.4)) {
                    continue;
                }
                BlockPos p = worldPosition.relative(h, a).relative(Direction.UP, b);
                if (!level.isLoaded(p)) {
                    continue;
                }
                BlockState s = level.getBlockState(p);
                if (!s.isAir() && !s.canBeReplaced()) {
                    hit = true;
                    break;
                }
            }
        }
        blocked = hit;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        long t = level.getGameTime();
        if (t % 40 == (worldPosition.asLong() & 31)) {
            recheck();
        }
        if (t % 10 == 0) {
            update(level);
        }
        //Orage : une aile de toile peut se dechirer
        if (type == 1 && sails > 0 && level.isThundering() && t % 20 == 0 && level.random.nextFloat() < 0.0015F * Math.abs((float) speed) / 30F) {
            sails--;
            if (sails == 0) {
                type = 0;
            }
            level.playSound(null, worldPosition, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.2F, 0.6F);
            markClientsDirty();
        }
        if (sails > 0 && !blocked && Math.abs(speed) > 1 && t % 200 == 0) {
            KineticEvents.award(level, worldPosition, "windmill");
        }
    }

    private void update(ServerLevel level) {
        if (sails == 0) {
            tau0 = 0;
            return;
        }
        wind = Wind.at(level, worldPosition);
        double rho = Wind.density(level);
        double cp = type == 2 ? 0.25 : 0.15;
        double arms = sails >= 8 ? 1.2 : sails >= 4 ? 1.0 : sails / 4.0;
        double power = 0.5 * rho * Math.PI * radius * radius * wind * wind * wind * cp * arms * KineticsConfig.sourcePower();
        double lambda = type == 2 ? 5 : 4;
        double freeRad = Math.max(0.05, lambda * wind / radius);
        free = freeRad * 60 / (2 * Math.PI);
        tau0 = wind < 0.3 ? 0 : 4 * power / freeRad;
    }

    @Override
    public void describe(List<Component> lines) {
        super.describe(lines);
        lines.add(Component.translatable("kinetics.pioneers.info.windmill", sails, radius, String.format(Locale.ROOT, "%.1f", wind),
              watts(tau0 * free * 2 * Math.PI / 60 / 4)).withStyle(ChatFormatting.AQUA));
        if (blocked) {
            lines.add(Component.translatable("kinetics.pioneers.windmill.blocked").withStyle(ChatFormatting.RED));
        }
    }

    @Override
    protected boolean clientDirty() {
        boolean dirty = sails != sentSails || radius != sentRadius || type != sentType || blocked != sentBlocked;
        sentSails = sails;
        sentRadius = radius;
        sentType = type;
        sentBlocked = blocked;
        return dirty;
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        tag.putInt("sails", sails);
        tag.putInt("type", type);
        tag.putInt("radius", radius);
        tag.putBoolean("blocked", blocked);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        sails = tag.getInt("sails");
        type = tag.getInt("type");
        radius = Math.max(MIN_RADIUS, tag.getInt("radius"));
        blocked = tag.getBoolean("blocked");
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        writeClient(tag);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        readClient(tag);
    }
}

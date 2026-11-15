package fr.pioneers.tile;

import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.registries.PiBlocks;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.event.SpaceEventType;
import fr.pioneers.space.event.SpaceEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * API Machine (lot 8c) : LOGIQUE DU COLLECTEUR DE FOUDRE (code d'avant de {@link TileEntityLightningCollector}, deplace) : case de recharge
 * (declaree par l'unite, seule case comme avant), mesure du mat et de la meteo chaque seconde, chance d'eclair, energie de l'eclair, sortie
 * de l'energie. Cle sauvee : {@code strikes}.
 * <p>
 * Valeurs nommees (apres les 4 communes, memes indices qu'avant) : hauteur du mat, ciel ouvert, meteo, eclairs captes, energie du dernier
 * eclair, production moyenne attendue (FE/t x100).
 */
public final class LightningUnit extends MachineUnit {

    public static final UiValues.Key MAST = UiValues.Key.of("mast"), SKY = UiValues.Key.of("sky"), WEATHER = UiValues.Key.of("weather"),
          STRIKES = UiValues.Key.of("strikes"), LAST_STRIKE = UiValues.Key.of("last_strike"), AVERAGE = UiValues.Key.of("average_x100");
    private static final int MIN_MAST = TileEntityLightningCollector.MIN_MAST, MAX_MAST = TileEntityLightningCollector.MAX_MAST;
    private static final int[] TIER_MAST = TileEntityLightningCollector.TIER_MAST;
    private static final long[] TIER_OUTPUT_FE = TileEntityLightningCollector.TIER_OUTPUT_FE;

    private final ItemSlot charge;
    /** Hauteur du mat (conducteurs), 0 si la structure est incomplete. */
    private int mast;
    private boolean openSky;
    /** 0 : calme, 1 : orage, 2 : atmosphere de geante, 3 : supertempete. */
    private int weather;
    private int strikes;
    private long lastStrikeEnergy;

    public LightningUnit(MachineTile.Access machine) {
        super(machine);
        charge = machine.slot(ItemSlot.of(stack -> stack.getCapability(ForgeCapabilities.ENERGY).map(IEnergyStorage::canReceive).orElse(false),
              machine::changed, TileEntityLightningCollector.CHARGE_X, TileEntityLightningCollector.CHARGE_Y).ghostIcon("ghost_battery"));
    }

    boolean isFormed() {
        return mast > 0;
    }

    private static long getStrikeEnergy(int mast, boolean superstorm) {
        return TileEntityLightningCollector.getStrikeEnergy(mast, superstorm);
    }

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (server.getGameTime() % 20 == 0) {
            validate(server);
        }
        if (isFormed() && openSky && machine.canWork()) {
            float chance = strikeChance();
            if (chance > 0 && server.random.nextFloat() < chance) {
                strike(server);
            }
        }
        machine.charge(charge, TIER_OUTPUT_FE[machine.machineTier()]);
        machine.pushEnergy(TIER_OUTPUT_FE[machine.machineTier()]);
        machine.markWorking(isFormed() && openSky && weather > 0);
    }

    /** Mesure le mat, verifie la pointe et la meteo. */
    private void validate(ServerLevel server) {
        int height = 0;
        BlockPos.MutableBlockPos pos = pos().mutable();
        for (int i = 0; i < MAX_MAST; i++) {
            pos.move(Direction.UP);
            if (!server.getBlockState(pos).is(PiBlocks.LIGHTNING_CONDUCTOR.getBlock())) {
                break;
            }
            height++;
        }
        BlockState top = server.getBlockState(pos().above(height + 1));
        boolean rod = top.is(Blocks.LIGHTNING_ROD) && top.getValue(LightningRodBlock.FACING) == Direction.UP;
        //1.0 : le tier borne la hauteur utile du mat (un mat plus haut marche, mais ne rapporte pas plus)
        mast = height >= MIN_MAST && rod ? Math.min(height, TIER_MAST[machine.machineTier()]) : 0;
        openSky = mast > 0 && server.canSeeSky(pos().above(height + 2));
        Body body = SpaceEvents.bodyAt(server, pos().getX());
        if (SpaceEvents.active(server, SpaceEventType.GIANT_STORM) != null) {
            weather = 3;
        } else if (body != null && body.isGasGiant()) {
            weather = 2;
        } else if (server.isThundering() && server.isRainingAt(pos().above(mast + 2))) {
            weather = 1;
        } else {
            weather = 0;
        }
    }

    /** Chance qu'un eclair frappe la pointe ce tick : un mat plus haut attire davantage la foudre. */
    private float strikeChance() {
        float base = switch (weather) {
            case 1 -> 0.004F;
            case 2 -> 0.0015F;
            case 3 -> 0.01F;
            default -> 0;
        };
        return base * (0.5F + mast / 8F);
    }

    private void strike(ServerLevel server) {
        BlockPos tip = pos().above(mast + 1);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(tip.above()));
            bolt.setVisualOnly(true);
            server.addFreshEntity(bolt);
        }
        long fe = getStrikeEnergy(mast, weather == 3);
        machine.energy().insert(fe, false);
        lastStrikeEnergy = fe;
        strikes++;
        machine.changed();
    }

    /** Production moyenne attendue (FE/t) dans la meteo actuelle. */
    public double getAverageOutput() {
        if (!isFormed() || !openSky) {
            return 0;
        }
        return getStrikeEnergy(mast, weather == 3) * (double) strikeChance();
    }

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(MAST, () -> mast).add(SKY, () -> openSky ? 1 : 0).add(WEATHER, () -> weather).add(STRIKES, () -> strikes)
              .add(LAST_STRIKE, () -> lastStrikeEnergy).add(AVERAGE, () -> Math.round(getAverageOutput() * 100));
    }

    @Override
    public void load(CompoundTag tag) {
        strikes = tag.getInt("strikes");
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt("strikes", strikes);
    }
}

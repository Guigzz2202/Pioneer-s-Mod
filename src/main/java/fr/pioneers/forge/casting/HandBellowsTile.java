package fr.pioneers.forge.casting;

import fr.pioneers.bronze.AirBlastReceiver;
import fr.pioneers.bronze.BronzeEvents;
import fr.pioneers.forge.ForgeBlock;
import fr.pioneers.forge.ForgeHeat;
import fr.pioneers.forge.ForgeTile;
import fr.pioneers.registries.PiForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Soufflet a main : une bouffee par clic ({@link #COOLDOWN} ticks au moins entre deux), livree au destinataire de
 * {@link ForgeHeat#airReceiver} devant la tuyere : le creuset d'argile prend {@link ForgeHeat#HAND_PUFF_C} qui retombe
 * ({@link ClayCrucibleTile#puff}) ; tout autre recepteur (foyer de forge, fourneau a creuset...) recoit l'air pendant {@link #PUFF_TICKS}
 * ticks. Recoit aussi l'air d'un soufflet mecanique ({@link AirBlastReceiver}) et le transmet tel quel (jamais a un autre soufflet a main).
 * Animation : evenement de bloc 1 = bouffee (heure de debut cote client, {@link #puffAge}). Evenement de progres {@code hand_bellows}.
 */
public class HandBellowsTile extends ForgeTile implements AirBlastReceiver {

    /** Duree d'une bouffee (ticks) : animation et air livre aux recepteurs autres que le creuset. */
    public static final int PUFF_TICKS = 8;
    /** Entre deux bouffees (ticks) : au plus 2,5 bouffees par seconde. */
    public static final int COOLDOWN = 8;
    /** Force de l'air d'une bouffee pour un recepteur ordinaire (0..1 par tick). */
    public static final double PUFF_STRENGTH = 0.6;

    private int puffing;
    private long lastPuff = -100;
    private double relay;
    // Client
    private long puffStart = -1000;

    public HandBellowsTile(BlockPos pos, BlockState state) {
        super(PiForge.HAND_BELLOWS_TILE.get(), pos, state);
    }

    private Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(ForgeBlock.FACING) ? s.getValue(ForgeBlock.FACING) : Direction.NORTH;
    }

    private AirBlastReceiver target() {
        if (level == null) {
            return null;
        }
        AirBlastReceiver r = ForgeHeat.airReceiver(level, worldPosition.relative(facing()));
        return r instanceof HandBellowsTile ? null : r;
    }

    @Override
    public InteractionResult use(Player player, InteractionHand hand, BlockHitResult hit) {
        if (level == null || hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).isEmpty() && player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            puff(server);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Une bouffee (cote serveur), si le soufflet s'est rouvert. */
    public void puff(ServerLevel level) {
        long now = level.getGameTime();
        if (now - lastPuff < COOLDOWN) {
            return;
        }
        lastPuff = now;
        AirBlastReceiver r = target();
        if (r instanceof ClayCrucibleTile crucible) {
            crucible.puff();
        } else if (r != null) {
            puffing = PUFF_TICKS;
        }
        level.blockEvent(worldPosition, getBlockState().getBlock(), 1, 0);
        level.playSound(null, worldPosition, SoundEvents.HORSE_BREATHE, SoundSource.BLOCKS, 0.5F, 0.7F + level.random.nextFloat() * 0.2F);
        BlockPos t = worldPosition.relative(facing());
        if (r != null) {
            level.sendParticles(ParticleTypes.FLAME, t.getX() + 0.5, t.getY() + 0.3, t.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.02);
            BronzeEvents.award(level, worldPosition, "hand_bellows");
        } else {
            level.sendParticles(ParticleTypes.POOF, t.getX() + 0.5, t.getY() + 0.15, t.getZ() + 0.5, 2, 0.1, 0.05, 0.1, 0.01);
        }
    }

    /** Air d'un soufflet mecanique tourne vers ce soufflet : transmis au feu devant. */
    @Override
    public void blast(double strength) {
        relay += strength;
    }

    @Override
    public void serverTick(ServerLevel level) {
        if (puffing > 0 || relay > 0) {
            AirBlastReceiver r = target();
            if (r != null) {
                if (puffing > 0) {
                    r.blast(PUFF_STRENGTH);
                }
                if (relay > 0) {
                    r.blast(Math.min(1.0, relay));
                }
            }
            if (relay > 0 && level.getGameTime() % 20 == 0) {
                level.blockEvent(worldPosition, getBlockState().getBlock(), 1, 0);
            }
            if (puffing > 0) {
                puffing--;
            }
            relay = 0;
        }
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == 1) {
            if (level != null && level.isClientSide) {
                puffStart = level.getGameTime();
            }
            return true;
        }
        return super.triggerEvent(id, param);
    }

    /** Age de la derniere bouffee (ticks, client) : l'animation dure {@link #PUFF_TICKS} x 2. */
    public float puffAge(float partial) {
        return level == null ? 1000F : level.getGameTime() - puffStart + partial;
    }
}

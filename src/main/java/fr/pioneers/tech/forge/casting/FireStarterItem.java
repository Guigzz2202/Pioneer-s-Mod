package fr.pioneers.tech.forge.casting;

import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.tech.forge.client.FireStarterClient;
import fr.pioneers.core.tooltip.GearTips;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.tech.mechanics.charcoal.CharcoalEvents;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allume-feu (3 batons + 2 fibres, 16 usages) : clic droit MAINTENU {@link #USE_TICKS} ticks sur un bloc (geste de l'archet, fumee qui monte)
 * ; au bout, allume ce qui est vise : fosse a charbon ({@link CharcoalEvents#ignitePit}), tuile {@link Ignitable} (foyer de forge), feu de
 * camp, bougie, sinon un feu sur la face visee (comme le briquet). Le regard qui quitte le bloc arrete le geste. Evenement de progres
 * {@code fire_lit}.
 * <p>
 * 1.10.1 (Guillaume : « ameliorer l'animation de l'allume feu ») : geste dessine par {@link FireStarterClient} (archet qui va et vient, fuseau
 * qui roule, images du modele choisies par le predicat {@code pioneers:drill}) ; cadence qui s'accelere ({@link #strokePhase}) ; fumee de plus
 * en plus dense et rapide, braises au-dela de 40 %, frottement sonore a chaque retour de l'archet et crepitement a la fin.
 */
public class FireStarterItem extends Item {

    /** Duree du geste (ticks). */
    public static final int USE_TICKS = 40;

    /** Cadence de l'archet : allers-retours par tick au debut et a la fin du geste. */
    public static final float STROKES_START = 1F / 12F, STROKES_END = 1F / 6F;

    public FireStarterItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack) {
        return 200;
    }

    @NotNull
    @Override
    public UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.CUSTOM;
    }

    @Override
    public void initializeClient(@NotNull Consumer<IClientItemExtensions> consumer) {
        consumer.accept(FireStarterClient.INSTANCE);
    }

    /**
     * Phase de l'archet (en allers-retours) apres t ticks de geste : la cadence passe lineairement de {@link #STROKES_START} a
     * {@link #STROKES_END} pendant {@link #USE_TICKS} ticks, puis reste a la plus rapide. Commun au client (images, bras) et au serveur (sons).
     */
    public static float strokePhase(float t) {
        float u = Math.max(0, Math.min(t, USE_TICKS));
        float phase = STROKES_START * u + (STROKES_END - STROKES_START) * u * u / (2F * USE_TICKS);
        return phase + STROKES_END * Math.max(0, t - USE_TICKS);
    }

    /** Progression du geste dans [0, 1]. */
    public static float progress(float t) {
        return Math.max(0, Math.min(1F, t / USE_TICKS));
    }

    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        player.startUsingItem(context.getHand());
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity entity, @NotNull ItemStack stack, int remaining) {
        if (!(entity instanceof Player player)) {
            entity.releaseUsingItem();
            return;
        }
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) {
            entity.releaseUsingItem();
            return;
        }
        int used = getUseDuration(stack) - remaining;
        float t = progress(used);
        Direction face = hit.getDirection();
        double x = hit.getLocation().x + face.getStepX() * 0.05, y = hit.getLocation().y + face.getStepY() * 0.05;
        double z = hit.getLocation().z + face.getStepZ() * 0.05;
        if (level.isClientSide) {
            //Fumee de plus en plus dense et rapide, braises au-dela de 40 %, grosses volutes a la fin
            if (level.random.nextFloat() < 0.25F + 0.75F * t) {
                level.addParticle(ParticleTypes.SMOKE, x + jitter(level), y, z + jitter(level), jitter(level) * 0.05, 0.008 + 0.045 * t,
                      jitter(level) * 0.05);
            }
            if (t > 0.5F && level.random.nextFloat() < 0.18F * t) {
                level.addParticle(ParticleTypes.LARGE_SMOKE, x + jitter(level), y + 0.05, z + jitter(level), 0, 0.02 + 0.03 * t, 0);
            }
            if (t > 0.4F && level.random.nextFloat() < 0.35F * t) {
                level.addParticle(ParticleTypes.SMALL_FLAME, x + jitter(level) * 0.6, y + 0.02, z + jitter(level) * 0.6, jitter(level) * 0.04,
                      0.01 + 0.03 * level.random.nextFloat(), jitter(level) * 0.04);
            }
        } else {
            //Frottement a chaque retour de l'archet (plus aigu et plus fort quand le bois chauffe), crepitement a la fin
            if ((int) (2 * strokePhase(used)) != (int) (2 * strokePhase(used - 1))) {
                level.playSound(null, x, y, z, SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, 0.35F + 0.25F * t,
                      1.1F + 0.5F * t + level.random.nextFloat() * 0.1F);
            }
            if (t > 0.6F && level.random.nextFloat() < 0.08F) {
                level.playSound(null, x, y, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.PLAYERS, 0.4F, 1.4F + level.random.nextFloat() * 0.3F);
            }
        }
        if (used >= USE_TICKS) {
            if (level instanceof ServerLevel server) {
                InteractionHand hand = player.getUsedItemHand();
                //Protection des terrains (spawn, mods de claims) : comme le briquet
                BlockPos at = hit.getBlockPos();
                boolean allowed = player.mayUseItemAt(at, hit.getDirection(), stack) && level.mayInteract(player, at)
                                  && player.mayUseItemAt(at.relative(hit.getDirection()), hit.getDirection(), stack);
                if (allowed && ignite(server, player, hit)) {
                    server.playSound(null, hit.getBlockPos(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1F, 0.9F + level.random.nextFloat() * 0.2F);
                    BronzeEvents.award(server, hit.getBlockPos(), "fire_lit");
                    stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
                }
            }
            entity.stopUsingItem();
        }
    }

    private static double jitter(Level level) {
        return (level.random.nextDouble() - 0.5) * 0.12;
    }

    /** Allume ce qui est vise ; vrai si quelque chose s'est allume. */
    public static boolean ignite(ServerLevel level, @Nullable Player player, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Boolean pit = CharcoalEvents.ignitePit(level, pos, player);
        if (pit != null) {
            return pit;
        }
        BlockState state = level.getBlockState(pos);
        BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        if (be instanceof Ignitable ignitable) {
            return ignitable.ignite(player);
        }
        if (CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), 11);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            return true;
        }
        BlockPos fire = pos.relative(hit.getDirection());
        if (BaseFireBlock.canBePlacedAt(level, fire, player == null ? hit.getDirection() : player.getDirection())) {
            level.setBlock(fire, BaseFireBlock.getState(level, fire), 11);
            level.gameEvent(player, GameEvent.BLOCK_PLACE, fire);
            return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("item.pioneers.fire_starter.desc");
        //1.0.4-beta (lot 28) : format commun des equipements : tier ; durabilite par l'infobulle commune (ToolProfiles.ToolClientEvents)
        GearTips.rank(tips, GearTips.MECHANICAL);
    }
}

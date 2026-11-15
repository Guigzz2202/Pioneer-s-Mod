package fr.pioneers.equipment.gear.electric;

import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.territory.Protection;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.tooltip.GearTips;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.3-beta (lot 9) : lance-flammes a hydrogene. Reservoir de {@value #HYDROGEN} mB (buffer de gaz, capsule) et batterie de
 * {@value #CAPACITY} FE (allumage, pompe). Clic droit maintenu : un jet de flammes en cone court ({@value #RANGE} blocs, demi-angle
 * {@value #CONE_DEGREES} degres), {@value #PER_TICK_H2} mB + {@value #PER_TICK_FE} FE par tick :
 * <ul>
 *     <li>les creatures du cone (en vue directe) prennent feu {@value #BURN_SECONDS} s et {@value #DAMAGE} points de degats de feu toutes les
 *     {@value #HIT_PERIOD} ticks ; un animal ou un objet d'un claim de clan ne l'est que si le joueur peut y agir ({@link ClanApi}) ;</li>
 *     <li>le bloc vise ne s'allume (feu pose devant une face d'un bloc INFLAMMABLE) que si la regle {@code mobGriefing} ET l'option
 *     {@code tools.flamethrowerIgnitesBlocks} le permettent, et si le joueur peut batir la (claims) ; jamais plus d'un feu par
 *     {@value #FIRE_PERIOD} ticks ; aucun bloc n'est casse ni change par le jet lui-meme ;</li>
 *     <li>sous l'eau : rien ne sort.</li>
 * </ul>
 */
public class FlamethrowerItem extends Item implements PoweredItem {

    public static final long CAPACITY = 200_000, HYDROGEN = 8_000, PER_TICK_H2 = 10, PER_TICK_FE = 40;
    public static final double RANGE = 6.0;
    public static final double CONE_DEGREES = 25;
    public static final float DAMAGE = 2.0F;
    public static final int BURN_SECONDS = 4, HIT_PERIOD = 5, FIRE_PERIOD = 10;
    private static final double CONE_COS = Math.cos(Math.toRadians(CONE_DEGREES));

    public FlamethrowerItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public long energyCapacity() {
        return CAPACITY;
    }

    @Override
    public long hydrogenCapacity() {
        return HYDROGEN;
    }

    public static boolean fueled(ItemStack stack) {
        return ElectricCharge.hydrogen(stack) >= PER_TICK_H2 && ElectricCharge.energy(stack) >= PER_TICK_FE;
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!fueled(stack)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(ElectricCharge.hydrogen(stack) < PER_TICK_H2 ? "electric.pioneers.flamethrower.empty"
                      : "electric.pioneers.flamethrower.no_energy"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 0.8F);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack) {
        return 72_000;
    }

    @NotNull
    @Override
    public UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity user, @NotNull ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server) || !(user instanceof Player player)) {
            return;
        }
        if (player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) {
            if (remaining % 10 == 0) {
                server.sendParticles(ParticleTypes.BUBBLE, player.getX(), player.getEyeY(), player.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
            }
            return;
        }
        if (!player.getAbilities().instabuild && !ElectricCharge.burn(stack, PER_TICK_H2, PER_TICK_FE)) {
            player.stopUsingItem();
            player.displayClientMessage(Component.translatable("electric.pioneers.flamethrower.empty"), true);
            return;
        }
        int age = getUseDuration(stack) - remaining;
        Vec3 eye = player.getEyePosition(), dir = player.getViewVector(1.0F);
        flames(server, player, eye, dir);
        if (age % 4 == 0) {
            server.playSound(null, player.blockPosition(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.4F);
            server.playSound(null, player.blockPosition(), SoundEvents.BLAZE_BURN, SoundSource.PLAYERS, 0.35F, 1.6F);
        }
        if (age % HIT_PERIOD == 0) {
            burnEntities(server, player, eye, dir);
        }
        if (age % FIRE_PERIOD == 0) {
            igniteBlock(server, player, eye, dir, stack);
        }
    }

    /** Particules du jet (vues de tous) : flammes rapides le long du regard, etalees en cone, et un peu de fumee. */
    private static void flames(ServerLevel level, Player player, Vec3 eye, Vec3 dir) {
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-4) {
            right = new Vec3(1, 0, 0);
        }
        right = right.normalize();
        Vec3 up = right.cross(dir).normalize();
        Vec3 start = eye.add(dir.scale(0.9)).add(right.scale(0.25)).add(up.scale(-0.25));
        var random = player.getRandom();
        for (int i = 0; i < 6; i++) {
            double spread = Math.tan(Math.toRadians(CONE_DEGREES)) * 0.8;
            Vec3 v = dir.add(right.scale((random.nextDouble() - 0.5) * spread)).add(up.scale((random.nextDouble() - 0.5) * spread)).normalize()
                  .scale(0.35 + random.nextDouble() * 0.25);
            level.sendParticles(ParticleTypes.FLAME, start.x, start.y, start.z, 0, v.x, v.y, v.z, 1.0);
        }
        if (random.nextInt(3) == 0) {
            Vec3 v = dir.scale(0.2);
            level.sendParticles(ParticleTypes.SMOKE, start.x, start.y, start.z, 0, v.x, v.y + 0.05, v.z, 1.0);
        }
    }

    /** Dans le cone : en vue directe, a portee. */
    public static boolean inCone(Vec3 eye, Vec3 dir, Entity e) {
        Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
        double d = to.length();
        return d <= RANGE + e.getBbWidth() * 0.5 && d > 1e-3 && to.scale(1 / d).dot(dir) >= CONE_COS;
    }

    public static DamageSource fire(Level level, Player player) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.IN_FIRE), player);
    }

    /** Creatures du cone : feu et degats de feu (claims respectes pour les animaux et les objets poses). */
    public static int burnEntities(ServerLevel level, Player player, Vec3 eye, Vec3 dir) {
        int n = 0;
        AABB box = player.getBoundingBox().inflate(RANGE + 1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !e.isSpectator())) {
            if (!inCone(eye, dir, e) || !player.hasLineOfSight(e)) {
                continue;
            }
            ClanApi.Action action = Protection.classify(e, true);
            if (action != null && !ClanApi.canModify(player, level, e.blockPosition(), action)) {
                continue;
            }
            if (!e.fireImmune()) {
                e.setSecondsOnFire(BURN_SECONDS);
            }
            e.hurt(fire(level, player), DAMAGE);
            n++;
        }
        return n;
    }

    /** Les blocs peuvent-ils prendre feu (regle mobGriefing et option de config) ? */
    public static boolean blocksAllowed(Level level) {
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && PiCommonConfig.get(PiCommonConfig.FLAMETHROWER_BLOCKS, true);
    }

    /** Feu pose devant la face visee d'un bloc inflammable, si tout le permet ; renvoie la position allumee ou null. */
    @Nullable
    public static BlockPos igniteBlock(ServerLevel level, Player player, Vec3 eye, Vec3 dir, ItemStack stack) {
        if (!blocksAllowed(level)) {
            return null;
        }
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(dir.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty() || !state.isFlammable(level, pos, face)) {
            return null;
        }
        BlockPos at = pos.relative(face);
        if (!level.isEmptyBlock(at) || !BaseFireBlock.canBePlacedAt(level, at, player.getDirection()) || !level.mayInteract(player, at)
            || !player.mayUseItemAt(at, face, stack) || !ClanApi.canModify(player, level, at, ClanApi.Action.PLACE)) {
            return null;
        }
        level.setBlock(at, BaseFireBlock.getState(level, at), 11);
        return at;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.pioneers.equipment.gear.client.ElectricClient.twoHanded(consumer));
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return ElectricCharge.provider(stack);
    }

    @Override
    public boolean overrideOtherStackedOnMe(@NotNull ItemStack self, @NotNull ItemStack other, @NotNull Slot slot, @NotNull ClickAction action,
          @NotNull Player player, @NotNull SlotAccess cursor) {
        return ElectricCharge.capsuleOnto(self, other, action, player, cursor);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBarVisible(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(@NotNull ItemStack stack) {
        return Math.round(13F * ElectricCharge.hydrogenRatio(stack));
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return ElectricCharge.energy(stack) < PER_TICK_FE ? 0xFF5050 : ElectricCharge.HYDROGEN_BAR;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.spec("electric.pioneers.flamethrower.use", PER_TICK_H2 * 20, PER_TICK_FE * 20, (int) RANGE);
        ElectricCharge.tooltip(stack, tooltip);
        GearTips.rank(tips, GearTips.MK1);
        tips.note("electric.pioneers.flamethrower.blocks");
        tips.hint("electric.pioneers.flamethrower.hint");
    }

    /** Duree d'un plein (secondes) : pour l'infobulle et les tests. */
    public static int fullSeconds() {
        return Mth.floor(Math.min(HYDROGEN / (double) PER_TICK_H2, CAPACITY / (double) PER_TICK_FE) / 20);
    }
}

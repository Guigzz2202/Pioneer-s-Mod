package fr.pioneers.equipment.gear.electric;

import fr.pioneers.Pioneers;
import fr.pioneers.core.tooltip.GearTips;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeTier;
import net.minecraftforge.common.IForgeShearable;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.3-beta (lot 9) : outils electriques Voltaiques (sans module) :
 * <ul>
 *     <li>{@link Kind#DRILL perceuse} : pioche + pelle (et le chemin de la pelle au clic droit) ;</li>
 *     <li>{@link Kind#CHAINSAW tronconneuse} : hache (ecorcer ; l'abattage de l'arbre entier de {@code nature/TreeFeller} s'applique) ;</li>
 *     <li>{@link Kind#TRIMMER taille-haie} : houe + cisailles (feuilles, laine, toiles, tondre les moutons ; labourer au clic droit).</li>
 * </ul>
 * Vitesse {@value #SPEED} tant qu'il reste de l'energie ({@value #PER_BLOCK} FE par bloc, 50 pour le taille-haie), lenteur du poing a sec ;
 * niveau de minage du diamant ; jamais casse. 250 000 FE, recharge 2 500 FE/t.
 */
public class VoltaicToolItem extends DiggerItem implements PoweredItem {

    public static final long CAPACITY = 250_000, PER_BLOCK = 100;
    public static final float SPEED = 10.0F;

    public enum Kind {
        DRILL("drill"), CHAINSAW("chainsaw"), TRIMMER("trimmer");

        public final String id;

        Kind(String id) {
            this.id = id;
        }
    }

    private static Tier tier;

    /** Materiau des outils Voltaiques : niveau du diamant, apres lui et avant la netherite (enregistre au premier appel). */
    public static synchronized Tier tier() {
        if (tier == null) {
            //1.0.6 : balise propre (vide) : la balise vanilla needs_diamond_tool privait les tiers de meme niveau classes dessous (voir PiTiers)
            tier = new ForgeTier(3, 0, SPEED, 2.0F, 0, BlockTags.create(Pioneers.rl("needs_voltaic_tool")), () -> Ingredient.EMPTY);
            TierSortingRegistry.registerTier(tier, Pioneers.rl("voltaic"), List.of(Tiers.DIAMOND), List.of(Tiers.NETHERITE));
        }
        return tier;
    }

    private final Kind kind;

    public VoltaicToolItem(Kind kind, Properties properties) {
        super(kind == Kind.CHAINSAW ? 6.0F : kind == Kind.DRILL ? 2.0F : 0.0F, kind == Kind.CHAINSAW ? -3.0F : -2.8F, tier(),
              kind == Kind.CHAINSAW ? BlockTags.MINEABLE_WITH_AXE : kind == Kind.TRIMMER ? BlockTags.MINEABLE_WITH_HOE : BlockTags.MINEABLE_WITH_PICKAXE,
              properties.stacksTo(1).rarity(Rarity.UNCOMMON));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public long energyCapacity() {
        return CAPACITY;
    }

    /** Energie par bloc mine. */
    public long cost() {
        return kind == Kind.TRIMMER ? PER_BLOCK / 2 : PER_BLOCK;
    }

    private static boolean shears(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.WOOL) || state.is(Blocks.COBWEB) || state.is(Blocks.VINE)
               || state.is(Blocks.GLOW_LICHEN) || state.is(Blocks.TRIPWIRE);
    }

    /** Le bloc est-il du ressort de l'outil (sans le niveau de minage) ? */
    public boolean handles(BlockState state) {
        return switch (kind) {
            case DRILL -> state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL);
            case CHAINSAW -> state.is(BlockTags.MINEABLE_WITH_AXE);
            case TRIMMER -> state.is(BlockTags.MINEABLE_WITH_HOE) || shears(state);
        };
    }

    @Override
    public boolean isCorrectToolForDrops(@NotNull ItemStack stack, @NotNull BlockState state) {
        if (kind == Kind.TRIMMER && state.is(Blocks.COBWEB)) {
            return true;
        }
        return handles(state) && TierSortingRegistry.isCorrectTierForDrops(getTier(), state);
    }

    @Override
    public float getDestroySpeed(@NotNull ItemStack stack, @NotNull BlockState state) {
        if (!handles(state)) {
            return 1.0F;
        }
        if (ElectricCharge.energy(stack) < cost()) {
            return 1.0F;
        }
        if (kind == Kind.TRIMMER && state.is(Blocks.COBWEB)) {
            return 15.0F * 1.5F;
        }
        return SPEED;
    }

    @Override
    public boolean mineBlock(@NotNull ItemStack stack, @NotNull Level level, @NotNull BlockState state, @NotNull BlockPos pos, @NotNull LivingEntity miner) {
        if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0.0F && handles(state)) {
            ElectricCharge.drain(stack, cost());
        }
        return true;
    }

    @Override
    public boolean hurtEnemy(@NotNull ItemStack stack, @NotNull LivingEntity target, @NotNull LivingEntity attacker) {
        return true;
    }

    /** Gestes : chemin (perceuse), ecorcer (tronconneuse), labourer (taille-haie). */
    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        return switch (kind) {
            case DRILL -> Items.DIAMOND_SHOVEL.useOn(context);
            case CHAINSAW -> Items.DIAMOND_AXE.useOn(context);
            case TRIMMER -> Items.DIAMOND_HOE.useOn(context);
        };
    }

    /** Taille-haie : tondre (moutons, champimeuh, golems de neige...) comme des cisailles, 50 FE. */
    @NotNull
    @Override
    public InteractionResult interactLivingEntity(@NotNull ItemStack stack, @NotNull Player player, @NotNull LivingEntity entity, @NotNull InteractionHand hand) {
        if (kind != Kind.TRIMMER || !(entity instanceof IForgeShearable target)) {
            return InteractionResult.PASS;
        }
        if (entity.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = entity.blockPosition();
        if (!target.isShearable(stack, entity.level(), pos) || !ElectricCharge.use(stack, cost())) {
            return InteractionResult.PASS;
        }
        List<ItemStack> drops = target.onSheared(player, stack, entity.level(), pos, 0);
        for (ItemStack d : drops) {
            ItemEntity e = entity.spawnAtLocation(d, 1.0F);
            if (e != null) {
                e.setDeltaMovement(e.getDeltaMovement().add((entity.getRandom().nextFloat() - entity.getRandom().nextFloat()) * 0.1F,
                      entity.getRandom().nextFloat() * 0.05F, (entity.getRandom().nextFloat() - entity.getRandom().nextFloat()) * 0.1F));
            }
        }
        entity.level().playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.2F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction action) {
        return switch (kind) {
            case DRILL -> ToolActions.DEFAULT_PICKAXE_ACTIONS.contains(action) || ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(action);
            case CHAINSAW -> ToolActions.DEFAULT_AXE_ACTIONS.contains(action);
            case TRIMMER -> ToolActions.DEFAULT_HOE_ACTIONS.contains(action) || ToolActions.DEFAULT_SHEARS_ACTIONS.contains(action);
        };
    }

    @Override
    public boolean isEnchantable(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return ElectricCharge.provider(stack);
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
        return Math.round(13F * ElectricCharge.ratio(stack));
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return ElectricCharge.ENERGY_BAR;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.spec("electric.pioneers.tool.use", (int) SPEED, cost());
        ElectricCharge.tooltip(stack, tooltip);
        GearTips.rank(tips, GearTips.MK1);
        if (ElectricCharge.energy(stack) < cost()) {
            tips.warn("electric.pioneers.tool.flat");
        }
        tips.hint("electric.pioneers.tool.hint." + kind.id);
    }
}

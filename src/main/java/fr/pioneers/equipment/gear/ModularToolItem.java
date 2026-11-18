package fr.pioneers.equipment.gear;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.15 : outil modulaire : pioche, hache, pelle, houe (Graviton et Stellaris) ou multi-outil (Neutronique et Singularite : tout ce que
 * minent les quatre, et leurs gestes : ecorcer, faire un chemin, labourer accroupi). Vitesse du tier tant qu'il reste de l'energie (250 FE
 * par bloc, plus avec le surcadencage), lenteur du poing sinon. Modules : excavation 3 x 3 / 5 x 5, surcadencage, fortune, toucher de soie,
 * fusion instantanee et teleporteur de butin ({@link GearLootModifier}), bras telescopique.
 */
public class ModularToolItem extends DiggerItem implements ModularGear {

    public static final long ENERGY_PER_BLOCK = 250;
    private static final UUID REACH_UUID = UUID.fromString("5b0e8a3e-6d5c-4d0f-9a43-3f6f0a1c2b21");
    private static final List<TagKey<Block>> MULTI = List.of(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_AXE,
          BlockTags.MINEABLE_WITH_SHOVEL, BlockTags.MINEABLE_WITH_HOE);
    /** Blocs casses par l'excavation en cours (pas d'excavation en chaine). */
    private static boolean excavating;

    private final ModularTier tier;
    private final GearKind kind;

    public ModularToolItem(ModularTier tier, GearKind kind, Properties properties) {
        super(kind == GearKind.AXE ? 6.0F : 1.0F, kind == GearKind.AXE ? -3.0F : -2.8F, tier.toolTier(), tag(kind),
              properties.stacksTo(1).fireResistant().rarity(tier.rarity));
        this.tier = tier;
        this.kind = kind;
    }

    private static TagKey<Block> tag(GearKind kind) {
        return switch (kind) {
            case AXE -> BlockTags.MINEABLE_WITH_AXE;
            case SHOVEL -> BlockTags.MINEABLE_WITH_SHOVEL;
            case HOE -> BlockTags.MINEABLE_WITH_HOE;
            default -> BlockTags.MINEABLE_WITH_PICKAXE;
        };
    }

    @Override
    public ModularTier tier() {
        return tier;
    }

    @Override
    public GearKind kind() {
        return kind;
    }

    /** Le bloc est-il du ressort de l'outil (sans le niveau de minage) ? */
    public boolean handles(BlockState state) {
        if (kind == GearKind.MULTITOOL) {
            for (TagKey<Block> t : MULTI) {
                if (state.is(t)) {
                    return true;
                }
            }
            return false;
        }
        return state.is(tag(kind));
    }

    @Override
    public boolean isCorrectToolForDrops(@NotNull ItemStack stack, @NotNull BlockState state) {
        return handles(state) && TierSortingRegistry.isCorrectTierForDrops(getTier(), state);
    }

    @Override
    public float getDestroySpeed(@NotNull ItemStack stack, @NotNull BlockState state) {
        if (!handles(state)) {
            return 1.0F;
        }
        if (Modules.energy(stack) < cost(stack)) {
            return 1.0F;
        }
        return tier.digSpeed * (1 + 0.4F * Modules.level(stack, GearModule.OVERCLOCK));
    }

    /** Energie par bloc mine (surcadencage : +50 % par niveau). */
    public static long cost(ItemStack stack) {
        return ENERGY_PER_BLOCK + ENERGY_PER_BLOCK * Modules.level(stack, GearModule.OVERCLOCK) / 2;
    }

    @Override
    public boolean mineBlock(@NotNull ItemStack stack, @NotNull Level level, @NotNull BlockState state, @NotNull BlockPos pos, @NotNull LivingEntity miner) {
        if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0.0F) {
            Modules.drain(stack, cost(stack));
        }
        return true;
    }

    @Override
    public boolean hurtEnemy(@NotNull ItemStack stack, @NotNull LivingEntity target, @NotNull LivingEntity attacker) {
        return true;
    }

    /** Excavation : les blocs voisins dans le plan de la face visee (3 x 3 ou 5 x 5), pas accroupi. */
    @Override
    public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) {
        int excavation = Modules.level(stack, GearModule.EXCAVATION);
        if (excavating || excavation <= 0 || player.isShiftKeyDown() || !(player instanceof ServerPlayer sp) || player.isCreative()) {
            return false;
        }
        Level level = player.level();
        BlockState origin = level.getBlockState(pos);
        if (!handles(origin)) {
            return false;
        }
        Direction face = face(player);
        float hardness = origin.getDestroySpeed(level, pos);
        int r = excavation;
        excavating = true;
        try {
            for (int a = -r; a <= r; a++) {
                for (int b = -r; b <= r; b++) {
                    if (a == 0 && b == 0) {
                        continue;
                    }
                    BlockPos p = switch (face.getAxis()) {
                        case X -> pos.offset(0, a, b);
                        case Y -> pos.offset(a, 0, b);
                        case Z -> pos.offset(a, b, 0);
                    };
                    BlockState st = level.getBlockState(p);
                    float h = st.getDestroySpeed(level, p);
                    if (st.isAir() || h < 0 || h > hardness * 1.5F + 0.5F || !handles(st) || !isCorrectToolForDrops(stack, st)
                        && st.requiresCorrectToolForDrops() || Modules.energy(stack) < cost(stack)) {
                        continue;
                    }
                    sp.gameMode.destroyBlock(p);
                }
            }
        } finally {
            excavating = false;
        }
        return false;
    }

    private static Direction face(Player player) {
        double reach = player.getBlockReach();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1).scale(reach));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getDirection() : Direction.UP;
    }

    /** Gestes de la hache, de la pelle et de la houe (le multi-outil : ecorcer, sinon chemin ; accroupi : labourer). */
    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        return switch (kind) {
            case AXE -> Items.NETHERITE_AXE.useOn(context);
            case SHOVEL -> Items.NETHERITE_SHOVEL.useOn(context);
            case HOE -> Items.NETHERITE_HOE.useOn(context);
            case MULTITOOL -> {
                if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
                    yield Items.NETHERITE_HOE.useOn(context);
                }
                InteractionResult r = Items.NETHERITE_AXE.useOn(context);
                yield r.consumesAction() ? r : Items.NETHERITE_SHOVEL.useOn(context);
            }
            default -> InteractionResult.PASS;
        };
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction action) {
        return switch (kind) {
            case PICKAXE -> ToolActions.DEFAULT_PICKAXE_ACTIONS.contains(action);
            case AXE -> ToolActions.DEFAULT_AXE_ACTIONS.contains(action);
            case SHOVEL -> ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(action);
            case HOE -> ToolActions.DEFAULT_HOE_ACTIONS.contains(action);
            default -> ToolActions.DEFAULT_PICKAXE_ACTIONS.contains(action) || ToolActions.DEFAULT_AXE_ACTIONS.contains(action)
                       || ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(action) || ToolActions.DEFAULT_HOE_ACTIONS.contains(action);
        };
    }

    // ------------------------------------------------------------------ fortune et toucher de soie

    @Override
    public int getEnchantmentLevel(ItemStack stack, Enchantment enchantment) {
        int own = super.getEnchantmentLevel(stack, enchantment);
        if (enchantment == Enchantments.SILK_TOUCH) {
            return Math.max(own, Modules.level(stack, GearModule.SILK_TOUCH));
        }
        if (enchantment == Enchantments.BLOCK_FORTUNE && Modules.level(stack, GearModule.SILK_TOUCH) <= 0) {
            return Math.max(own, Modules.level(stack, GearModule.FORTUNE));
        }
        return own;
    }

    @Override
    public Map<Enchantment, Integer> getAllEnchantments(ItemStack stack) {
        Map<Enchantment, Integer> map = new HashMap<>(super.getAllEnchantments(stack));
        for (Enchantment e : new Enchantment[]{Enchantments.SILK_TOUCH, Enchantments.BLOCK_FORTUNE}) {
            int level = getEnchantmentLevel(stack, e);
            if (level > 0) {
                map.put(e, level);
            }
        }
        return map;
    }

    @Override
    public boolean isEnchantable(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    // ------------------------------------------------------------------ portee, energie, infobulle

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) {
            return ImmutableMultimap.of();
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.putAll(getDefaultAttributeModifiers(slot));
        int reach = Modules.level(stack, GearModule.REACH);
        if (reach > 0) {
            b.put(ForgeMod.BLOCK_REACH.get(), new AttributeModifier(REACH_UUID, "Telescopic arm", 2.0 * reach, AttributeModifier.Operation.ADDITION));
        }
        return b.build();
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return Modules.provider(stack);
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
        return Modules.barWidth(stack);
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return Modules.barColor(stack);
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
        Modules.tooltip(stack, tooltip);
        if (kind == GearKind.MULTITOOL) {
            tips.hint("gear.pioneers.multitool_hint");
        }
    }
}

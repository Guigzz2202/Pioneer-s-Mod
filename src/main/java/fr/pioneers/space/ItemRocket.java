package fr.pioneers.space;

import fr.pioneers.client.space.RocketItemRenderer;
import fr.pioneers.common.PiLang;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.space.module.RocketCargo;
import fr.pioneers.space.module.RocketModule;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fusee de tier 1 sous forme d'objet : clic droit sur le pas de tir (ou le sol) pour la poser.
 */
public class ItemRocket extends Item {

    private final RocketTier tier;

    public ItemRocket(RocketTier tier) {
        super(new Item.Properties().stacksTo(1).rarity(tier.level <= 3 ? Rarity.RARE : Rarity.EPIC));
        this.tier = tier;
    }

    public RocketTier getTier() {
        return tier;
    }

    @NotNull
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        //La fusee ne se pose que sur un pas de tir : toujours au centre, sur le cœur
        BlockPos core = LaunchPadStructure.findCore(level, context.getClickedPos());
        if (core == null || !level.isClientSide && !LaunchPadStructure.validate(level, core)) {
            if (context.getPlayer() != null && !level.isClientSide) {
                context.getPlayer().displayClientMessage(PiLang.ROCKET_NEEDS_PAD.translateColored(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            //Le complexe doit etre assez grand pour cette fusee
            LaunchPadStructure.Size size = LaunchPadStructure.formation(level, core);
            if (size == null || size.maxTier < tier.level) {
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(Component.translatable("rocket.pioneers.pad_too_small",
                          tier.level).withStyle(ChatFormatting.RED), true);
                }
                return InteractionResult.FAIL;
            }
        }
        BlockPos pos = core.above();
        if (!level.isClientSide) {
            RocketEntity rocket = tier.getEntityType().create(level);
            if (rocket == null) {
                return InteractionResult.FAIL;
            }
            rocket.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, context.getHorizontalDirection().toYRot(), 0);
            if (!level.noCollision(rocket)) {
                return InteractionResult.FAIL;
            }
            rocket.setModules(getModules(context.getItemInHand()));
            rocket.getCargo().load(getCargoTag(context.getItemInHand()));
            level.addFreshEntity(rocket);
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Rendu 3D (maquette de la fusee) au lieu d'une icone plate. */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return RocketItemRenderer.INSTANCE;
            }
        });
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("description.pioneers." + tier.getName(), tier.fuelCapacity);
        List<ItemStack> modules = getModules(stack);
        tips.spec("item.pioneers.rocket.modules", modules.size(),
              RocketModule.slots(tier));
        for (ItemStack module : modules) {
            tips.spec(Component.literal("  • ").append(module.getHoverName()));
        }
        RocketCargo cargo = RocketCargo.read(getCargoTag(stack));
        if (!cargo.isEmpty()) {
            tips.note("item.pioneers.rocket.cargo_loaded");
        }
        tips.hint("item.pioneers.rocket.configure_hint");
    }

    public static final String NBT_MODULES = "Modules", NBT_CARGO = "Cargo";

    public static CompoundTag getCargoTag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(NBT_CARGO, Tag.TAG_COMPOUND) ? tag.getCompound(NBT_CARGO) : new CompoundTag();
    }

    /** Fusee repliee avec ses modules et sa cargaison. */
    public static ItemStack withContents(RocketTier tier, List<ItemStack> modules, RocketCargo cargo) {
        ItemStack stack = withModules(tier, modules);
        if (!cargo.isEmpty()) {
            stack.getOrCreateTag().put(NBT_CARGO, cargo.save());
        }
        return stack;
    }

    /** Modules embarques par une fusee (objet). */
    public static List<ItemStack> getModules(ItemStack stack) {
        List<ItemStack> list = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(NBT_MODULES, Tag.TAG_LIST)) {
            readModules(tag.getList(NBT_MODULES, Tag.TAG_COMPOUND), list);
        }
        return list;
    }

    public static ItemStack withModules(RocketTier tier, List<ItemStack> modules) {
        ItemStack stack = new ItemStack(tier.getItem());
        if (!modules.isEmpty()) {
            stack.getOrCreateTag().put(NBT_MODULES, writeModules(modules));
        }
        return stack;
    }

    public static ListTag writeModules(List<ItemStack> modules) {
        ListTag list = new ListTag();
        for (ItemStack module : modules) {
            if (!module.isEmpty()) {
                list.add(module.save(new CompoundTag()));
            }
        }
        return list;
    }

    public static void readModules(ListTag list, List<ItemStack> into) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack module = ItemStack.of(list.getCompound(i));
            if (!module.isEmpty()) {
                into.add(module);
            }
        }
    }
}

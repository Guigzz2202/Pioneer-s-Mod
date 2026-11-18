package fr.pioneers.logistics.rail.train;

import fr.pioneers.logistics.rail.client.CarItemRenderer;
import fr.pioneers.core.Texts;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.logistics.rail.RailWorld;
import fr.pioneers.logistics.rail.track.TrackBlock;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.0 : objet d'un vehicule : clic droit sur une voie pour le poser (dans le sens du regard) ; il s'attelle tout seul au train qu'il touche.
 */
public class RailCarItem extends Item {

    public final CarType type;
    public final int tier;

    public RailCarItem(CarType type, int tier, Properties properties) {
        super(properties);
        this.type = type;
        this.tier = tier;
    }

    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (!(level.getBlockState(pos).getBlock() instanceof TrackBlock)) {
            pos = pos.above();
            if (!(level.getBlockState(pos).getBlock() instanceof TrackBlock)) {
                return InteractionResult.PASS;
            }
        }
        if (level instanceof ServerLevel sl && ctx.getPlayer() instanceof ServerPlayer player) {
            if (RailWorld.get(sl).placeCar(pos, type, tier, ctx.getHorizontalDirection(), ctx.getItemInHand().getTagElement(RailItems.STORE_TAG))) {
                if (!player.getAbilities().instabuild) {
                    ctx.getItemInHand().shrink(1);
                }
            } else {
                player.displayClientMessage(Component.translatable("rail.pioneers.no_room"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void initializeClient(@NotNull Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return CarItemRenderer.instance();
            }
        });
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("item.pioneers." + type.baseId() + ".tooltip");
        if (type.locomotive && type != CarType.DRAISINE) {
            tips.spec("rail.pioneers.tip.speed", Math.round(type.maxSpeed(tier) / 0.8F));
            tips.spec("rail.pioneers.tip.haul", type.haul(tier));
        }
        if (type.slots(tier) > 0) {
            tips.spec(type == CarType.TENDER || type == CarType.STEAM ? "rail.pioneers.tip.fuel_slots" : "rail.pioneers.tip.slots", type.slots(tier));
        }
        if (type.water(tier) > 0) {
            tips.spec("rail.pioneers.tip.water", type.water(tier));
        }
        if (stack.getTagElement(RailItems.STORE_TAG) != null) {
            tips.state("rail.pioneers.tip.loaded");
        }
        //0.20.2 : reserves
        if (type.fluidCapacity(tier) > 0) {
            tips.spec("rail.pioneers.tip.fluid", Texts.format(type.fluidCapacity(tier)));
        }
        if (type.gasCapacity(tier) > 0) {
            tips.spec("rail.pioneers.tip.gas", Texts.format(type.gasCapacity(tier)));
        }
        if (type.energyCapacity(tier) > 0) {
            tips.spec("rail.pioneers.tip.energy", Texts.format(type.energyCapacity(tier)));
        }
        if (type.dataCapacity(tier) > 0) {
            tips.spec("rail.pioneers.tip.data", Texts.format(type.dataCapacity(tier)));
        }
        if (type.airCapacity(tier) > 0) {
            tips.spec("rail.pioneers.tip.air", Texts.format(type.airCapacity(tier)));
        }
        if (type == CarType.MAGLEV) {
            tips.note("rail.pioneers.tip.ultimate_only");
        }
    }
}

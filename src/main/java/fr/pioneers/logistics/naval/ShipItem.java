package fr.pioneers.logistics.naval;

import fr.pioneers.logistics.naval.client.ShipItemRenderer;
import fr.pioneers.core.Texts;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.core.registries.PiNaval;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.11 : objet d'un navire : clic droit sur l'eau pour le mettre a l'eau (dans le sens du regard). Pres d'un port libre qui l'accepte, il
 * s'amarre directement. Un navire ramasse garde son contenu, son nom et son horaire ({@link #CONTENTS}).
 */
public class ShipItem extends Item {

    public static final String CONTENTS = "ship";

    public final ShipKind kind;
    public final int tier;

    public ShipItem(ShipKind kind, int tier, Properties properties) {
        super(properties.stacksTo(1));
        this.kind = kind;
        this.tier = tier;
    }

    public static ItemStack stack(ShipKind kind, int tier) {
        Item item = PiNaval.SHIPS.get(kind.id(tier)).get();
        return new ItemStack(item);
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }
        BlockPos pos = hit.getBlockPos();
        if (!WaterChart.navigableFluid(level.getFluidState(pos))) {
            return InteractionResultHolder.pass(stack);
        }
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            if (level.getFluidState(pos).is(WaterChart.NAVIGABLE_HOT) && !ShipKind.heatproof(tier)) {
                sp.displayClientMessage(Component.translatable("naval.pioneers.ship.lava"), true);
                return InteractionResultHolder.fail(stack);
            }
            NavalWorld w = NavalWorld.get(sl);
            Ship s = Ship.fromContents(kind, tier, stack.getTagElement(CONTENTS));
            s.x = hit.getLocation().x;
            s.z = hit.getLocation().z;
            s.y = pos.getY() + 0.875;
            s.heading = -player.getYRot();
            w.launch(s);
            //Pres d'un port libre : amarre directement
            for (Harbor h : w.harbors()) {
                BlockPos hp = h.blockPos();
                if (h.accepts(tier) && h.occupant == null && hp.distToCenterSqr(s.x, hp.getY(), s.z) < 12 * 12) {
                    double[] b = h.berthPose(tier, s.heading);
                    s.x = b[0];
                    s.z = b[1];
                    s.heading = (float) b[2];
                    s.y = h.waterY + 0.875;
                    s.state = Ship.State.DOCKED;
                    s.harbor = h.pos;
                    s.arrivedAt = level.getGameTime();
                    s.lastActivity = s.arrivedAt;
                    h.occupant = s.id;
                    break;
                }
            }
            if (!sp.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void initializeClient(@NotNull Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return ShipItemRenderer.instance();
            }
        });
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("item.pioneers." + kind.base + ".tooltip");
        tips.spec("naval.pioneers.tip.propulsion." + tier);
        tips.spec("naval.pioneers.tip.size", ShipKind.length(tier), ShipKind.beam(tier), ShipKind.knots(tier));
        tips.spec("naval.pioneers.tip.hold." + kind.lower(), Texts.format(capacity(kind, tier)));
        if (ShipKind.heatproof(tier)) {
            tips.note("naval.pioneers.tip.lava");
        }
        CompoundTag t = stack.getTagElement(CONTENTS);
        if (t != null && !t.getString("name").isEmpty()) {
            tips.state(Component.literal("« " + t.getString("name") + " »"));
        }
    }

    /** Contenance totale de la cale (cases, mB, FE, L, Mo ou places). */
    public static long capacity(ShipKind kind, int tier) {
        int n = ShipKind.holds(tier);
        return switch (kind) {
            case FERRY -> kind.seats(tier);
            case CARGO -> (long) kind.hold.slots(tier) * n;
            case TANKER -> (long) kind.hold.fluidCapacity(tier) * n;
            case GAS_CARRIER -> (long) kind.hold.gasCapacity(tier) * n;
            case POWER_BARGE -> kind.hold.energyCapacity(tier) * n;
            case AIR_CARRIER -> (long) kind.hold.airCapacity(tier) * n;
            case DATA_SHIP -> kind.hold.dataCapacity(tier) * n;
        };
    }
}

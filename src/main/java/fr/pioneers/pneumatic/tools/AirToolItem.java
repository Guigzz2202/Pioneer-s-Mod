package fr.pioneers.pneumatic.tools;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.pressure.IPressureHandler;
import fr.pioneers.core.tooltip.GearTips;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.transport.logistic.LogisticCableTile;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Outil a air comprime : un reservoir (litres d'air a pression ambiante, donnee {@code air} de l'objet) que chaque usage vide. La barre
 * d'usure montre le remplissage. Recharge : Maj + clic droit sur un bloc qui contient de l'air comprime (compresseur, buffer, machine...),
 * voir {@link PneumaticEvents}.
 */
public abstract class AirToolItem extends Item {

    public static final String AIR = "air";
    private static final int BAR = 0x7FC8F0;

    private final double capacity;

    protected AirToolItem(double capacity, Properties properties) {
        super(properties.stacksTo(1));
        this.capacity = capacity;
    }

    /** Contenance du reservoir (litres). */
    public double capacity() {
        return capacity;
    }

    /** Litres consommes par usage (infobulle). */
    protected abstract double perUse();

    public static double air(ItemStack stack) {
        return stack.hasTag() ? stack.getOrCreateTag().getDouble(AIR) : 0;
    }

    public static void setAir(ItemStack stack, double liters) {
        stack.getOrCreateTag().putDouble(AIR, Math.max(0, liters));
    }

    /** Preleve {@code liters} si le reservoir les contient entierement. */
    public static boolean use(ItemStack stack, double liters) {
        double air = air(stack);
        if (air + 1e-6 < liters) {
            return false;
        }
        setAir(stack, air - liters);
        return true;
    }

    public static boolean has(ItemStack stack, double liters) {
        return air(stack) + 1e-6 >= liters;
    }

    /** Remplit l'outil depuis un bloc a air comprime ; renvoie les litres pris. */
    public double refill(ItemStack stack, @Nullable BlockEntity tile) {
        if (tile == null) {
            return 0;
        }
        IPressureHandler handler = tile.getCapability(PioneersAPI.PRESSURE_HANDLER, null).orElse(null);
        if (handler == null) {
            return 0;
        }
        double room = capacity - air(stack);
        if (room <= 0) {
            return 0;
        }
        double taken = handler.extractAir(room, false);
        if (taken > 0) {
            setAir(stack, air(stack) + taken);
        }
        return taken;
    }

    /** Un bloc peut-il servir a la recharge ? */
    public static boolean isAirSource(Level level, BlockPos pos) {
        BlockEntity tile = level.getBlockEntity(pos);
        return tile != null && !(tile instanceof LogisticCableTile) && tile.getCapability(PioneersAPI.PRESSURE_HANDLER, null).isPresent();
    }

    @Override
    public boolean isBarVisible(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(@NotNull ItemStack stack) {
        return Math.round(13F * (float) Mth.clamp(air(stack) / capacity, 0, 1));
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return BAR;
    }

    @Override
    public boolean shouldCauseReequipAnimation(@NotNull ItemStack oldStack, @NotNull ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        //1.0.4-beta (lot 28) : format commun des equipements (J:core/tooltip/GearTips) : usage, reserve d'air (L), tier MKI (compresseur)
        tips.stat("tooltip.pioneers.gear.use", Component.translatable(getDescriptionId() + ".use", String.format(Locale.ROOT, "%.0f", perUse())));
        GearTips.tank(tips, "tooltip.pioneers.gear.air", Math.round(air(stack)), Math.round(capacity), "l");
        GearTips.rank(tips, GearTips.MK1);
        tips.hint("tooltip.pioneers.air_tool.refill");
    }
}

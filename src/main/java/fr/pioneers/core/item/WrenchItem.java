package fr.pioneers.core.item;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cle a molette Pioneer's : regle les faces des cables (et tout ce qui reconnait les cles, via les tags {@code forge:tools/wrench} et
 * {@code forge:wrenches}). Le travail est fait par les blocs (voir {@code Wrenches.isWrench}) ; Maj + clic droit atteint aussi le bloc.
 * 0.20.46 (audit) : une seule classe pour toutes les cles (celle de l'age du bronze n'en differait que par son infobulle).
 */
public class WrenchItem extends Item {

    /** Infobulle de la cle des cables. */
    public static final String[] CABLES = {"item.pioneers.wrench.tooltip"};
    /** Infobulle de la cle de l'age du bronze (tuyaux de vapeur, soupapes, fiches techniques). */
    public static final String[] PIPES = {"tooltip.pioneers.wrench.pipes", "tooltip.pioneers.wrench.settings"};

    private final String[] tooltip;

    public WrenchItem(Properties properties) {
        this(properties, CABLES);
    }

    public WrenchItem(Properties properties, String[] tooltip) {
        super(properties);
        this.tooltip = tooltip;
    }

    @Override
    public boolean doesSneakBypassUse(@NotNull ItemStack stack, @NotNull LevelReader level, @NotNull BlockPos pos, @NotNull Player player) {
        return true;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        for (int i = 0; i < this.tooltip.length; i++) {
            if (i == 0) {
                tips.desc(this.tooltip[i]);
            } else {
                tips.hint(this.tooltip[i]);
            }
        }
    }
}

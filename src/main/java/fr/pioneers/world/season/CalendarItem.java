package fr.pioneers.world.season;

import fr.pioneers.core.block.DescribedBlockItem;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 1) : l'objet CALENDRIER : la date en infobulle ({@link CalendarBlock#appendHoverText}) et, tenu en main (l'une ou
 * l'autre), dans la barre d'action chaque seconde (« Automne, jour 3 sur 10 · Hiver dans 8 jours »). Pose sur un mur : le calendrier mural.
 */
public class CalendarItem extends DescribedBlockItem {

    public CalendarItem(CalendarBlock block, Properties properties) {
        super(block, properties, "description.pioneers.calendar");
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player player) || level.getGameTime() % 20 != 0) {
            return;
        }
        if (selected || player.getOffhandItem() == stack) {
            Component line = line(level);
            if (line != null) {
                player.displayClientMessage(line, true);
            }
        }
    }

    /** Ligne de la barre d'action (null si le calendrier n'est pas encore connu). */
    @Nullable
    public static Component line(Level level) {
        Seasons.Date d = Seasons.calendar(level);
        return d == null ? null : Component.translatable("season.pioneers.calendar.in_hand", SeasonTexts.dateLong(d), SeasonTexts.next(d));
    }
}

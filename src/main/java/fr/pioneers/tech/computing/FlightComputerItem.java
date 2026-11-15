package fr.pioneers.tech.computing;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Ordinateur de vol (0.18.0) : l'avionique des sondes et des fusees. Il se range dans un lecteur de l'ordinateur comme une disquette
 * (toute la pile d'un coup) ; un programme PioScript le programme avec {@code flight.flash("a")}, qui le change en ordinateur de vol
 * programme (NBT {@code program} = programme qui l'a flashe, {@code mission} = nom donne). Sans lui, ni module sonde ni coiffe de fusee.
 */
public class FlightComputerItem extends DataDiskItem {

    public static final String PROGRAM = "program", MISSION = "mission";
    private final boolean programmed;

    public FlightComputerItem(Properties properties, boolean programmed) {
        super(properties);
        this.programmed = programmed;
    }

    public boolean isProgrammed() {
        return programmed;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        if (!programmed) {
            tips.desc("computing.pioneers.flight.blank");
            return;
        }
        CompoundTag tag = stack.getTag();
        String program = tag == null ? "" : tag.getString(PROGRAM);
        String mission = tag == null ? "" : tag.getString(MISSION);
        tips.state("computing.pioneers.flight.programmed", program.isEmpty() ? "?" : program);
        if (!mission.isEmpty()) {
            tips.spec("computing.pioneers.flight.mission", mission);
        }
    }
}

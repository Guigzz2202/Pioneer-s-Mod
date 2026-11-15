package fr.pioneers.robotics;

import fr.pioneers.client.robotics.RobotUnitItemRenderer;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Unite robotique (drone, robot ou rover) d'un tier donne : a placer dans un hub de son genre. Rendu 3D (la maquette de l'unite).
 */
public class ItemRobotUnit extends Item {

    public final UnitKind kind;
    public final UnitTier tier;

    public ItemRobotUnit(UnitKind kind, UnitTier tier) {
        super(new Properties().stacksTo(16));
        this.kind = kind;
        this.tier = tier;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("robotics.pioneers.kind." + kind.id() + ".desc");
        tips.spec("robotics.pioneers.tooltip.stats",
              Math.round(kind.speed * tier.speed * 100), Math.round(kind.stacks * tier.capacity), Math.round(kind.fluid * tier.capacity),
              Math.round(kind.work * tier.work * 100));
        tips.spec("robotics.pioneers.tooltip.energy", Math.round(kind.energy * tier.energy), tier.range);
        if (kind != UnitKind.ROBOT) {
            //Rover, tortue et drone servent aussi les machines spatiales (base d'extraction, serre, baie de drones)
            tips.note("robotics.pioneers.kind." + kind.id() + ".machine");
        }
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return RobotUnitItemRenderer.INSTANCE;
            }
        });
    }
}

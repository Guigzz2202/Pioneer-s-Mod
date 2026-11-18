package fr.pioneers.equipment.gear;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.15 : module d'equipement (objet {@code module_<id>}) : s'installe sur un objet modulaire a l'etabli de modulation ; un objet par
 * niveau. Infobulle : effet, objets d'accueil, niveau maximal (et plafond par tier), tier minimal.
 */
public class ModuleItem extends Item {

    public final GearModule module;

    public ModuleItem(GearModule module, Properties properties) {
        super(properties.stacksTo(16));
        this.module = module;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.state(module.effect());
        tips.spec("gear.pioneers.module.fits", module.where());
        tips.spec("gear.pioneers.module.max", Modules.roman(module.maxLevel));
        if (module.minTier > 0) {
            tips.note("gear.pioneers.module.min_tier",
                  Component.translatable("gear.pioneers.tier." + ModularTier.values()[module.minTier].id()), ModularTier.values()[module.minTier].mk());
        }
        tips.hint("gear.pioneers.module.install");
    }
}

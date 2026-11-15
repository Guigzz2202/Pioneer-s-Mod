package fr.pioneers.naval;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 0.20.27 : objet de la balise maritime : se pose sur l'eau comme un nenuphar (ou sur un bloc plein). */
public class SeaBeaconItem extends PlaceOnWaterBlockItem {

    public SeaBeaconItem(Block block, Properties properties) {
        super(block, properties);
    }

    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext ctx) {
        //Sur un bloc plein (cote, rocher) : pose ordinaire
        return place(new BlockPlaceContext(ctx));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        Tooltips.of(tooltip).descOnShift("description.pioneers.sea_beacon");
    }
}

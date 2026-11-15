package fr.pioneers.content;

import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.tile.TileEntityDataBank;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bloc de la banque de donnees (socle) : a la pose, reprend le contenu porte par l'objet ({@code mekData.dataTank}, ecrit par la table de
 * butin, par les anciennes banques Mekanism et par les banques creatives de l'onglet).
 */
public class DataBankBlock extends MachineBlock {

    public DataBankBlock(Properties properties, Supplier<? extends BlockEntityType<?>> type, BiFunction<BlockPos, BlockState, BlockEntity> factory) {
        super(properties, type, factory);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        CompoundTag tag = stack.getTag();
        if (!level.isClientSide && tag != null && tag.getCompound("mekData").contains("dataTank", Tag.TAG_COMPOUND)
            && level.getBlockEntity(pos) instanceof TileEntityDataBank bank) {
            bank.loadFromItem(tag.getCompound("mekData").getCompound("dataTank"));
        }
    }
}

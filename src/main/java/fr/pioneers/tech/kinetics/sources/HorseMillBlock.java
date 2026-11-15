package fr.pioneers.tech.kinetics.sources;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.tech.kinetics.KineticBlock;
import fr.pioneers.tech.kinetics.KineticSpecs;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Manege a animaux : pivot vertical a quatre bras. Amenez un animal en laisse et faites clic droit sur le pivot : il est attele et tourne en
 * rond (rayon 3, la piste doit rester libre). Cheval x1 (environ 790 W), mule x0,7, ane x0,5, boeuf (vache) x0,6, chameau x1,2. Une auge a
 * moins de 4 blocs les nourrit (ble ou foin). Maj + clic droit main vide : deteler. L'arbre sort dessous et dessus.
 */
public class HorseMillBlock extends KineticBlock implements KineticSpecs.Specified {

    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

    public HorseMillBlock(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof HorseMillTile tile)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            int n = tile.releaseAll();
            player.displayClientMessage(Component.translatable("kinetics.pioneers.horse_mill.released", n), true);
            return InteractionResult.CONSUME;
        }
        int harnessed = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(10), m -> m.isLeashed() && m.getLeashHolder() == player)) {
            if (HorseMillTile.strength(mob) <= 0) {
                player.displayClientMessage(Component.translatable("kinetics.pioneers.horse_mill.refused", mob.getDisplayName())
                      .withStyle(ChatFormatting.YELLOW), true);
                continue;
            }
            if (tile.harness(mob)) {
                mob.dropLeash(true, !player.getAbilities().instabuild);
                harnessed++;
            }
        }
        if (harnessed == 0) {
            player.displayClientMessage(Component.translatable("kinetics.pioneers.horse_mill.help", tile.count(), HorseMillTile.MAX_ANIMALS), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void specs(List<Component> tooltip) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.spec("kinetics.pioneers.spec.horse_mill");
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new HorseMillTile(pos, state);
    }
}

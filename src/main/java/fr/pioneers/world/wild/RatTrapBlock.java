package fr.pioneers.world.wild;

import fr.pioneers.farm.FarmCard;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 7, specification §9) : le PIEGE A RAT. Planchette, ressort, arceau et pedale d'appat. Clic droit avec un APPAT (tag
 * {@code pioneers:rat_bait} : graines, fromage du mod) : le piege est ARME (appat visible, arceau tendu). Toutes les secondes, il attire les
 * rats a 8 blocs ({@link Rat#lureTo}) et ATTRAPE le premier qui touche l'appat ({@link Wild7Rules#trapCatches}, moins d'un bloc) : l'arceau
 * claque, le rat meurt (il lache ce qu'il avait vole), le piege est PRIS. Un seul rat par appat : clic droit pour le vider, puis il faut le
 * REARMER. Etats {@link #TRAP} : vide, arme aux graines, arme au fromage, pris.
 */
public class RatTrapBlock extends HorizontalDirectionalBlock {

    public enum Trap implements StringRepresentable {
        EMPTY, SEEDS, CHEESE, CAUGHT;

        @NotNull
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean armed() {
            return this == SEEDS || this == CHEESE;
        }
    }

    public static final EnumProperty<Trap> TRAP = EnumProperty.create("trap", Trap.class);
    static final VoxelShape SHAPE_NS = Block.box(4, 0, 1, 12, 3, 15), SHAPE_EW = Block.box(1, 0, 4, 15, 3, 12);

    public RatTrapBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TRAP, Trap.EMPTY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, TRAP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(@NotNull BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState other, @NotNull net.minecraft.world.level.LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos otherPos) {
        return dir == Direction.DOWN && !canSurvive(state, level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
              : super.updateShape(state, dir, other, level, pos, otherPos);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        Trap t = state.getValue(TRAP);
        ItemStack held = player.getItemInHand(hand);
        if (t == Trap.CAUGHT) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(TRAP, Trap.EMPTY), 3);
                level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.4F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (t == Trap.EMPTY && held.is(PiWild7.RAT_BAIT)) {
            if (!level.isClientSide) {
                boolean cheese = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(held.getItem()).getPath().contains("cheese");
                arm(level, pos, state, cheese ? Trap.CHEESE : Trap.SEEDS);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    /** Arme le piege avec un appat (graines ou fromage). */
    public static void arm(Level level, BlockPos pos, BlockState state, Trap bait) {
        level.setBlock(pos, state.setValue(TRAP, bait), 3);
        level.playSound(null, pos, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 0.6F, 1.3F);
        level.scheduleTick(pos, state.getBlock(), Wild7Rules.TRAP_PERIOD);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        if (state.getValue(TRAP).armed()) {
            level.scheduleTick(pos, this, Wild7Rules.TRAP_PERIOD);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (!state.getValue(TRAP).armed()) {
            return;
        }
        if (!work(level, pos, state)) {
            level.scheduleTick(pos, this, Wild7Rules.TRAP_PERIOD);
        }
    }

    /** Un passage du piege arme : attrape un rat sur l'appat, sinon attire ceux a 8 blocs. Rend vrai s'il a attrape un rat. */
    public static boolean work(ServerLevel level, BlockPos pos, BlockState state) {
        Vec3 c = Vec3.atBottomCenterOf(pos);
        List<Rat> rats = level.getEntitiesOfClass(Rat.class, new AABB(pos).inflate(Wild7Rules.TRAP_LURE), Rat::isAlive);
        Rat best = null;
        double bd = Double.MAX_VALUE;
        for (Rat r : rats) {
            double d = r.position().distanceTo(c);
            if (d < bd) {
                bd = d;
                best = r;
            }
        }
        if (best == null) {
            return false;
        }
        if (Wild7Rules.trapCatches(state.getValue(TRAP).armed(), bd)) {
            catchRat(level, pos, state, best);
            return true;
        }
        for (Rat r : rats) {
            r.lureTo(pos, Wild7Rules.TRAP_PERIOD * 3);
        }
        return false;
    }

    /** L'arceau claque sur le rat : il meurt (il lache son butin), le piege est pris. */
    public static void catchRat(ServerLevel level, BlockPos pos, BlockState state, Rat rat) {
        rat.hurt(level.damageSources().generic(), Float.MAX_VALUE);
        level.setBlock(pos, state.setValue(TRAP, Trap.CAUGHT), 3);
        level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.9F, 1.7F);
    }

    /** Fiche au regard (client et serveur). */
    public FarmCard card(BlockState state) {
        Trap t = state.getValue(TRAP);
        FarmCard c = FarmCard.of(new ItemStack(this), getName(), 0xC8A070);
        int colour = t == Trap.CAUGHT ? FarmCard.WARN : t.armed() ? FarmCard.OK : FarmCard.DIM;
        c.add(FarmCard.Line.pair(FarmCard.label("state"), Component.translatable("wild.pioneers.trap." + t.getSerializedName()), colour));
        c.add(FarmCard.Line.hint(Component.translatable(t == Trap.CAUGHT ? "wild.pioneers.trap.hint_empty" : t.armed()
              ? "wild.pioneers.trap.hint_armed" : "wild.pioneers.trap.hint_bait")));
        return c;
    }
}

package fr.pioneers.kinetics.parts;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.kinetics.AxisKineticBlock;
import fr.pioneers.kinetics.KineticMachineTile;
import fr.pioneers.kinetics.KineticTile;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import fr.pioneers.registries.PiKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Boites en ligne, entree par le bout negatif de l'axe, sortie par le bout positif :
 * <ul>
 *     <li>boite de vitesses : rapport de sortie choisi a la cle (x1, x2, x4, x1/2, x1/4) ; un signal redstone repasse en prise directe ;</li>
 *     <li>embrayage : un signal redstone desaccouple la sortie (deux reseaux) ;</li>
 *     <li>inverseur : un signal redstone inverse le sens de la sortie.</li>
 * </ul>
 * 1.0.2-beta (Guillaume : « ajouter une interface de type slider a la boite de vitesses ») : clic droit = interface standard de l'API Machine
 * (fiches {@code gearshift}, {@code clutch}, {@code reverser} ; logique {@link TransmissionUnit} portee par l'adaptateur cinetique) : vitesses
 * d'entree et de sortie, etat ; la boite de vitesses y a un CURSEUR des cinq rapports, de x1/4 a x4 ({@link #SLIDER}) ; la cle reste un
 * raccourci (rapport suivant). Rapport toujours dans l'etat du bloc ({@link #RATIO}, inchange).
 */
public class InlineBlock extends AxisKineticBlock {

    public enum Kind {
        GEARSHIFT, CLUTCH, REVERSER
    }

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty RATIO = IntegerProperty.create("ratio", 0, 4);
    /** Rapports (vitesse de sortie / vitesse d'entree). */
    public static final double[] RATIOS = {1, 2, 4, 0.5, 0.25};
    public static final String[] RATIO_NAMES = {"\u00d71", "\u00d72", "\u00d74", "\u00d7\u00bd", "\u00d7\u00bc"};
    /** 1.0.2-beta : rapports dans l'ordre du curseur (de x1/4 a x4) : indice de {@link #RATIOS} a chaque position. */
    public static final int[] SLIDER = {4, 3, 0, 1, 2};

    /** Position du curseur (0..4) du rapport d'indice {@code ratio}. */
    public static int sliderOf(int ratio) {
        for (int i = 0; i < SLIDER.length; i++) {
            if (SLIDER[i] == ratio) {
                return i;
            }
        }
        return 2;
    }

    public final Kind kind;

    public InlineBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(POWERED, false).setValue(RATIO, 0));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED, RATIO);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void onNeighbor(BlockState state, Level level, BlockPos pos, KineticTile tile) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected InteractionResult onWrench(BlockState state, Level level, BlockPos pos, Player player, KineticTile tile) {
        if (kind != Kind.GEARSHIFT) {
            return InteractionResult.PASS;
        }
        int next = (state.getValue(RATIO) + 1) % RATIOS.length;
        level.setBlock(pos, state.setValue(RATIO, next), Block.UPDATE_ALL);
        player.displayClientMessage(Component.translatable("kinetics.pioneers.gearshift.ratio", RATIO_NAMES[next]), true);
        return InteractionResult.CONSUME;
    }

    /** 1.0.2-beta : clic droit sans cle = interface standard (vitesses, etat ; curseur du rapport pour la boite de vitesses). */
    @Override
    protected InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof Tile tile) {
            NetworkHooks.openScreen(serverPlayer, tile, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Pose le rapport d'indice {@code ratio} (borne) ; vrai s'il change. */
    public static boolean setRatio(Level level, BlockPos pos, BlockState state, int ratio) {
        int r = Math.max(0, Math.min(RATIOS.length - 1, ratio));
        if (!(state.getBlock() instanceof InlineBlock block) || block.kind != Kind.GEARSHIFT || state.getValue(RATIO) == r) {
            return false;
        }
        level.setBlock(pos, state.setValue(RATIO, r), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof KineticTile tile) {
            tile.invalidateNetwork();
        }
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new Tile(pos, state);
    }

    /**
     * Tile commune aux trois boites (type {@code inline_box} inchange) ; 1.0.2-beta : adaptateur cinetique de l'API Machine, qui porte
     * {@link TransmissionUnit} (aucune cle de sauvegarde en plus : le rapport et le signal sont dans l'etat du bloc).
     */
    public static class Tile extends KineticMachineTile {

        private final TransmissionUnit unit;

        public Tile(BlockPos pos, BlockState state) {
            super(PiKinetics.INLINE_TILE.get(), pos, state);
            unit = carry(new TransmissionUnit(host, this));
        }

        public Kind kind() {
            return getBlockState().getBlock() instanceof InlineBlock block ? block.kind : Kind.CLUTCH;
        }

        /** Vitesse de sortie (tr/min, repere du monde : celle de l'arbre cote positif de l'axe). */
        public double outputSpeed() {
            return getSpeed() * shaftFactor(Direction.fromAxisAndDirection(axis(getBlockState()), Direction.AxisDirection.POSITIVE));
        }

        /** Couple transmis (N.m). */
        public double transmitted() {
            return torque;
        }

        /** Aucune donnee propre au client (rapport et signal dans l'etat du bloc, valeurs par le menu) : pas de paquet a chaque synchro. */
        @Override
        protected boolean ownClientData() {
            return false;
        }

        @Override
        public List<StandardLayout.Part> guiParts() {
            return TransmissionUnit.parts(kind() == Kind.GEARSHIFT);
        }

        @Override
        public double shaftFactor(Direction face) {
            BlockState state = getBlockState();
            if (face.getAxis() != axis(state)) {
                return 0;
            }
            if (face.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
                return 1;
            }
            boolean powered = state.getValue(POWERED);
            return switch (kind()) {
                case GEARSHIFT -> powered ? 1 : RATIOS[state.getValue(RATIO)];
                case CLUTCH -> powered ? 0 : 1;
                case REVERSER -> powered ? -1 : 1;
            };
        }

        @Override
        public double maxTorque() {
            return 8_000;
        }

        @Override
        public double maxSpeed() {
            return 400;
        }

        @Override
        public double efficiency() {
            return kind() == Kind.GEARSHIFT ? 0.95 : 0.99;
        }
    }
}

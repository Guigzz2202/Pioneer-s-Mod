package fr.pioneers.tech.kinetics.parts;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.tech.kinetics.AxisKineticBlock;
import fr.pioneers.tech.kinetics.KineticMachineTile;
import fr.pioneers.tech.kinetics.KineticTile;
import fr.pioneers.tech.kinetics.PiKinetics;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

/**
 * Regulateur a boules de Watt : les boules s'ecartent avec la vitesse ; au-dessus de la vitesse visee, il freine le reseau proportionnellement
 * a l'exces et emet un signal redstone (pour couper un embrayage, alerter...). Le premier asservissement du joueur.
 * <p>
 * 1.0.2-beta (Guillaume : « ajouter une interface de type slider au regulateur a boules ») : clic droit = interface standard de l'API Machine
 * (fiche {@code governor}, logique {@link GovernorUnit} portee par l'adaptateur cinetique) avec un CURSEUR de la vitesse visee, de
 * {@link GovernorUnit#MIN_RPM} a {@link GovernorUnit#MAX_RPM} tr/min (avant : 8 crans a la cle) ; la cle passe toujours au cran suivant de
 * {@link #TARGETS}.
 */
public class GovernorBlock extends AxisKineticBlock {

    public static final int[] TARGETS = {10, 15, 20, 30, 45, 60, 90, 120};

    public GovernorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isSignalSource(@NotNull BlockState state) {
        return true;
    }

    @Override
    public int getSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction direction) {
        return level.getBlockEntity(pos) instanceof Tile tile && tile.unit.limiting() ? 15 : 0;
    }

    @Override
    protected InteractionResult onWrench(BlockState state, Level level, BlockPos pos, Player player, KineticTile tile) {
        if (tile instanceof Tile governor) {
            governor.unit.nextNotch();
            player.displayClientMessage(Component.translatable("kinetics.pioneers.governor.target", governor.targetSpeed()), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** 1.0.2-beta : clic droit sans cle = interface standard (vitesse, vitesse visee au curseur, frein, signal). */
    @Override
    protected InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof Tile tile) {
            NetworkHooks.openScreen(serverPlayer, tile, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new Tile(pos, state);
    }

    /**
     * Tile du regulateur (type {@code governor} inchange) ; 1.0.2-beta : adaptateur cinetique de l'API Machine qui porte {@link GovernorUnit}
     * (vitesse visee, frein serre, signal ; cle de sauvegarde {@code rpm}, l'ancienne {@code target} (cran) est relue).
     */
    public static class Tile extends KineticMachineTile {

        final GovernorUnit unit;

        public Tile(BlockPos pos, BlockState state) {
            super(PiKinetics.GOVERNOR_TILE.get(), pos, state);
            unit = carry(new GovernorUnit(host, this));
        }

        public int targetSpeed() {
            return unit.target();
        }

        public GovernorUnit governor() {
            return unit;
        }

        @Override
        public double shaftFactor(Direction face) {
            return face.getAxis() == axis(getBlockState()) ? 1 : 0;
        }

        @Override
        public double governorStiffness() {
            return 400;
        }

        @Override
        public double governorTarget() {
            return targetSpeed();
        }

        @Override
        public double maxTorque() {
            return 8_000;
        }

        @Override
        public double maxSpeed() {
            return 300;
        }

        /** Couple vu par le noeud (N.m). */
        double torqueNow() {
            return torque;
        }

        @Override
        public void describe(List<Component> lines) {
            super.describe(lines);
            lines.add(Component.translatable("kinetics.pioneers.governor.target", targetSpeed()));
        }

        @Override
        public List<StandardLayout.Part> guiParts() {
            return GovernorUnit.parts();
        }
    }
}

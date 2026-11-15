package fr.pioneers.tech.industry.multi;

import fr.pioneers.core.common.block.Connections;
import fr.pioneers.core.Wrenches;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Port d'un multibloc industriel, pose dans son enveloppe : objets, fluides, gaz, energie, chaleur (entree ou sortie, change a la cle),
 * air comprime (entree seulement : pression de procede). 0.20.28 : on ne regle que le sens ; l'entree range seule ce qui arrive et la sortie
 * ejecte seule les produits ({@link SmartPorts}). Le port de fluide raccorde aussi les tuyaux de vapeur de l'age du bronze (1.0 : plus de port de vapeur). Port de
 * donnees (1.0) : branche les cables de donnees (calcul et telemetrie des centres de donnees, ordinateur quantique) ; tout cable colle a la
 * structure marche aussi, le port rend le branchement visible (prise du cable). Les sorties poussent d'elles-memes vers les
 * voisins hors de la structure. Chaleur : un port en sortie n'evacue que si le multibloc est le plus chaud ; un port en entree ne fait
 * entrer que la chaleur d'un voisin plus chaud (pose : en sortie, comme les anciens ports qui evacuaient).
 * <p>1.0.4-beta (lot 30) : pose a la place d'une tole, il en prend la peau a la formation ({@link Connections#ON_SHEET}, dessin
 * {@code client/model/IndustrialBodyModel}) ; ses plaques (connecteur, couleur du sens) restent visibles.
 */
public class PortBlock extends Block implements EntityBlock {

    public enum Type {
        ITEM, FLUID, GAS, ENERGY, HEAT, PRESSURE, DATA;

        public boolean directional() {
            return this != PRESSURE && this != DATA;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final BooleanProperty OUTPUT = BooleanProperty.create("output");

    public final Type type;

    public PortBlock(Type type, Properties properties) {
        super(properties);
        this.type = type;
        //Chaleur : pose en sortie (evacuation), comme les ports d'avant la 0.16.1
        registerDefaultState(stateDefinition.any().setValue(OUTPUT, type == Type.HEAT).setValue(IndustryStyle.TIER, 0)
              .setValue(IndustryStyle.STYLE, IndustryStyle.NEUTRAL).setValue(Connections.ON_SHEET, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OUTPUT, IndustryStyle.TIER, IndustryStyle.STYLE, Connections.ON_SHEET);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new PortTile(pos, state);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!type.directional() || !Wrenches.isWrench(player.getItemInHand(hand))) {
            //Clic droit sans cle : ouvre le controleur du multibloc
            BlockPos controller = level.isClientSide ? null : Structures.controllerAt(level, pos);
            if (controller == null && !level.isClientSide) {
                //1.9.2 : port d'un multibloc d'assemblage
                controller = Structures.hostAt(level, pos);
            }
            if (controller != null) {
                BlockState c = level.getBlockState(controller);
                return c.use(level, player, hand, hit.withPosition(controller));
            }
            return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            boolean output = !state.getValue(OUTPUT);
            level.setBlock(pos, state.setValue(OUTPUT, output), Block.UPDATE_ALL);
            Component message = Component.translatable(type == Type.HEAT ? (output ? "industry.pioneers.port.heat_output" : "industry.pioneers.port.heat_input")
                  : output ? "industry.pioneers.port.output" : "industry.pioneers.port.input");
            if (level.getBlockEntity(pos) instanceof PortTile port) {
                port.markHeatMode();
                port.refresh();
            }
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.5F, output ? 1.3F : 0.9F);
            player.displayClientMessage(message.copy().withStyle(output ? ChatFormatting.GOLD : ChatFormatting.AQUA), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

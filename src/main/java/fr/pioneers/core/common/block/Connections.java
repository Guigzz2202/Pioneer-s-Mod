package fr.pioneers.core.common.block;

import fr.pioneers.Pioneers;
import fr.pioneers.core.Directions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Textures connectees des blocs de structure (coques, vitrages, plaques) : six booleens {@code north..down} = « le voisin de ce cote
 * appartient a la meme famille » (tag {@code pioneers:connects/<famille>}). Le modele (multipart) ne dessine le cadre que vers les cotes
 * non connectes. Purement visuel : les structures comparent des blocs, pas des etats.
 * <p>1.0.4-beta (lot 23, « les casings et textures connectees sont cassees ») : l'etat n'etait calcule qu'a la pose par un joueur
 * ({@code getStateForPlacement}) et quand un voisin change ({@code updateShape}) ; tout bloc pose autrement (construction creative du plan
 * {@code Projections.build}, {@code PartMigration}, structure collee, blocs des lots 21 / 21b relus sans leurs booleens) gardait des
 * cotes non connectes face aux voisins poses AVANT lui : cadres d'un seul cote des joints. Desormais : chaque bloc connecte se recalcule a
 * la pose quelle qu'elle soit ({@link #heal}, appele par {@code onPlace}) et le client dessine toujours d'apres les voisins reels
 * ({@code client/model/ConnectedTexturesModel}), quel que soit l'etat sauve.
 */
public final class Connections {

    public static final TagKey<Block> GREENHOUSE = family("greenhouse");
    public static final TagKey<Block> OUTPOST = family("outpost");
    public static final TagKey<Block> LAUNCH_PAD = family("launch_pad");
    /** Multiblocs industriels (0.16.4) : carcasses, hublots, ports et controleurs ; le verre forme sa propre famille. */
    public static final TagKey<Block> INDUSTRIAL = family("industrial");
    public static final TagKey<Block> INDUSTRIAL_GLASS = family("industrial_glass");
    /** 1.0.4-beta (lot 21) : salle blanche (parois ; les vitres forment leur propre famille). */
    public static final TagKey<Block> CLEANROOM = family("cleanroom");
    public static final TagKey<Block> CLEANROOM_GLASS = family("cleanroom_glass");

    /**
     * 1.0.4-beta (lot 30, Guillaume : « on peut autoriser les ports sur les sheetmetals mais les ports doivent prendre une texture connectee
     * avec les sheetmetal ») : bloc de la famille industrielle qui a pris la PEAU D'UNE TOLE (port pose a la place d'une tole, peint a la
     * formation : {@code Structure.Result.sheetPorts}) : il ne compte plus dans sa famille ({@link #member}) ; les carcasses voisines
     * gardent leur cadre vers lui comme vers une tole.
     */
    public static final BooleanProperty ON_SHEET = BooleanProperty.create("on_sheet");

    private Connections() {
    }

    /** 1.0.4-beta (lot 30) : le voisin compte-t-il dans la famille (tag, sauf un bloc a la peau de tole {@link #ON_SHEET}) ? */
    public static boolean member(BlockState neighbor, TagKey<Block> family) {
        return neighbor.is(family) && !(neighbor.hasProperty(ON_SHEET) && neighbor.getValue(ON_SHEET));
    }

    /** 1.0.4-beta (lot 23) : bloc a textures connectees (six booleens) : sa famille ; lu par le rendu client et par {@link #heal}. */
    public interface Connected {

        TagKey<Block> connectFamily();

        /**
         * 1.0.4-beta (lot 26) : etat recalcule d'apres les voisins reels (six cotes de la famille ; un bloc peut y ajouter ses propres
         * proprietes de voisinage : cuve de fonderie, cuves en diagonale haute). Lu par {@link #heal} et le rendu client.
         */
        default BlockState connectAll(BlockState state, BlockGetter level, BlockPos pos) {
            return connect(state, level, pos, connectFamily());
        }
    }

    /**
     * 1.0.4-beta (lot 23) : a la pose (n'importe laquelle : {@code setBlock} d'un code, d'une structure, du plan creatif), recalcule les six
     * cotes d'apres les voisins reels. A appeler depuis {@code onPlace} d'un bloc {@link Connected}.
     * <p>1.0.6 (GameTests p104_21, p104_23, p104_26) : la remise a jour PREVIENT les voisins. {@code Level.setBlock} ne fait sa propre mise a
     * jour des voisins ({@code markAndNotifyBlock}) que si l'etat est encore celui qu'il a pose ; or on vient de le changer : avec
     * {@code UPDATE_KNOWN_SHAPE}, les voisins poses AVANT ce bloc gardaient leur cote non connecte vers lui (etat du serveur faux ; le rendu,
     * lui, lit les voisins reels). La pose relancee ne repasse pas ici (meme bloc).
     */
    public static void heal(BlockState state, Level level, BlockPos pos, BlockState old) {
        if (level.isClientSide || !(state.getBlock() instanceof Connected c) || old.is(state.getBlock()) || !state.hasProperty(property(Direction.UP))) {
            return;
        }
        BlockState next = c.connectAll(state, level, pos);
        if (next != state) {
            level.setBlock(pos, next, Block.UPDATE_ALL);
        }
    }

    /** 1.0.4-beta (lot 23) : l'etat tel qu'il doit etre d'apres les voisins reels (rendu client). */
    public static BlockState actual(BlockState state, BlockGetter level, BlockPos pos, TagKey<Block> family) {
        return state.getBlock() instanceof Connected c ? c.connectAll(state, level, pos) : connect(state, level, pos, family);
    }

    /** 1.0.4-beta (lot 21c) : famille d'un seul bloc (foyers, bobines : un bloc ne se raccorde qu'a ses jumeaux). */
    public static TagKey<Block> family(String name) {
        return TagKey.create(Registries.BLOCK, Pioneers.rl("connects/" + name));
    }

    public static BooleanProperty property(Direction side) {
        return PipeBlock.PROPERTY_BY_DIRECTION.get(side);
    }

    public static void addProperties(StateDefinition.Builder<Block, BlockState> builder) {
        for (Direction side : Directions.ALL) {
            builder.add(property(side));
        }
    }

    public static BlockState unconnected(BlockState state) {
        for (Direction side : Directions.ALL) {
            state = state.setValue(property(side), false);
        }
        return state;
    }

    /** Recalcule les six cotes (pose du bloc). */
    public static BlockState connect(BlockState state, BlockGetter level, BlockPos pos, TagKey<Block> family) {
        for (Direction side : Directions.ALL) {
            state = state.setValue(property(side), member(level.getBlockState(pos.relative(side)), family));
        }
        return state;
    }

    /** Un seul cote (voisin modifie). */
    public static BlockState update(BlockState state, Direction side, BlockState neighbor, TagKey<Block> family) {
        return state.setValue(property(side), member(neighbor, family));
    }
}

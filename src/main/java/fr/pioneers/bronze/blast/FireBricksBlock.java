package fr.pioneers.bronze.blast;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Briques refractaires : la maconnerie du haut fourneau ; elles rougeoient avec la chaleur du four ({@code heat} 0 a 3). 0.20.40 : le haut
 * fourneau forme peint aussi leur role ({@code part}) : socle a larmier, cuve, cerclage de fer, merlon de la couronne ({@link Part}).
 */
public class FireBricksBlock extends Block {

    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 3);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    /**
     * Role d'une brique dans le haut fourneau forme (NONE : brique libre ou cachee). 1.0.5-beta (lot 15, Guillaume : « texture finie pour le
     * four a coke ») : roles du four a coke forme, ajoutes EN FIN (noms sauves dans l'etat) : paroi ceinturee d'un tirant de fonte, angle a
     * cornieres, porte de defournement lutee, voute a chaperon de pierre, cheminee a conduit ouvert (peints par {@code CokeOvenTile}).
     * 1.0.5-beta (lot 23, Guillaume : « les renforts en acier du coke oven ne sont pas jolis ») : angles ORIENTES, ajoutes EN FIN
     * ({@code COKE_CORNER_NW} : faces exterieures au nord et a l'ouest, etc.) : le montant en cornieres d'acier n'est dessine qu'a l'arete
     * exterieure ; {@code COKE_CORNER} reste lu (anciennes sauvegardes, repeint au premier tick).
     */
    public enum Part implements StringRepresentable {
        NONE, PLINTH, STACK, BAND, CROWN, COKE_WALL, COKE_CORNER, COKE_DOOR, COKE_CAP, COKE_CHIMNEY,
        COKE_CORNER_NW, COKE_CORNER_NE, COKE_CORNER_SE, COKE_CORNER_SW;

        @NotNull
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public FireBricksBlock(Properties properties) {
        super(properties.lightLevel(s -> s.getValue(HEAT) * 4));
        registerDefaultState(defaultBlockState().setValue(HEAT, 0).setValue(PART, Part.NONE));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HEAT, PART);
    }
}

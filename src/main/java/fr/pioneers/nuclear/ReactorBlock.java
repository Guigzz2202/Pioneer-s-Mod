package fr.pioneers.nuclear;

import fr.pioneers.industry.multi.IndustryShape;
import java.util.Locale;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 1.0 (lot 3) : blocs fonctionnels des reacteurs a fission, qui remplacent les modules et les pieces signature. Chacun a sa place dans le
 * gabarit du reacteur ({@link IndustryShape#role}) et le coeur ({@link FissionCore}) les compte :
 * <ul>
 *     <li>canal de combustible (Gen I) et canal rapide (Gen II) : un assemblage charge chacun ;</li>
 *     <li>1.0.4-beta (lot 23) : barre de commande, pompe primaire et echangeur intermediaire (Gen II), injecteur de neutres (tokamak) RETIRES :
 *     integres au reacteur ({@link IndustryShape#builtInRods}, {@link IndustryShape#SFR_PUMPS}, {@link IndustryShape#builtInInjectors}) ;</li>
 *     <li>bobine de confinement (anneau de l'equateur), reflecteur en diamant (nervures de la sphere), collecteur de fragments (dalles) : Gen III ;</li>
 *     <li>fusion : chambre a vide (verre), bobines toroidales (tokamak) ou helicoidales (heliotron), solenoide central,
 *     couverture tritigene ou convertisseur direct (moitie basse de la chambre) ; modeles : tools/gc/nuclear/fusion_blocks.py.</li>
 * </ul>
 * {@link #ACTIVE} : lueur en marche (peinte par le coeur). Modeles : tools/gc/nuclear/reactor_blocks.py.
 */
public class ReactorBlock extends Block {

    public enum Kind {
        //1.0.4-beta (lot 23, demande de Guillaume) : barre de commande, pompe primaire a sodium, echangeur intermediaire et injecteur de neutres
        //retires (remap -> carcasse MKI, J:common/PiRemaps.p104Lot23Block) : integres aux reacteurs (IndustryShape.builtIn*)
        FUEL_CHANNEL, FAST_FUEL_CHANNEL, CONFINEMENT_COIL, DIAMOND_REFLECTOR, FRAGMENT_COLLECTOR,
        /** Lot 3 bis : fusion (tokamak : bobines toroidales, solenoide, couverture ; heliotron : bobines helicoidales, convertisseur ; chambre a vide). */
        TOROIDAL_COIL, SOLENOID_COIL, BREEDING_BLANKET, HELICAL_COIL, DIRECT_CONVERTER, VACUUM_CHAMBER;

        public String id() {
            return switch (this) {
                case HELICAL_COIL -> "stellarator_coil";
                default -> name().toLowerCase(Locale.ROOT);
            };
        }

        public boolean fuel() {
            return this == FUEL_CHANNEL || this == FAST_FUEL_CHANNEL;
        }
    }

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public final Kind kind;

    private ReactorBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    /** Le constructeur de Block appelle createBlockStateDefinition avant que kind soit pose : on lit le genre depuis un champ statique. */
    private static Kind building;

    public static ReactorBlock create(Kind kind, Properties properties) {
        building = kind;
        try {
            return new ReactorBlock(kind, properties);
        } finally {
            building = null;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }
}

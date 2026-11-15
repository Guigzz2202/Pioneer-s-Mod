package fr.pioneers.tech.industry.multi;

import fr.pioneers.tech.nuclear.ReactorBlock;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Piece signature d'un multibloc (0.16.1). Elle compte comme enveloppe ; certains gabarits l'exigent a un endroit precis ({@link Signature}).
 * Tournee vers la face cliquee a la pose ; {@link #ACTIVE} peint par le controleur quand la machine travaille (modele anime, particules).
 * 1.0.4-beta (lot 15, decision de Guillaume du 07/11/2026) : brûleur, tuyère, cheminée, torchère, évent, ventilateur, groupe froid, bobine de
 * chauffe et bloc d'ingénierie sont retirés : leur place est prise par la carcasse industrielle du tier (la cheminée : une carcasse au sommet,
 * {@code IndustryMachine#summitCasing}) ; les mondes existants les chargent en carcasse MKI ({@code PiRemaps.p104Lot15Block}) et le
 * controleur les remet au tier de la structure ({@link PartMigration}). Reste le réfrigérateur à dilution de l'ordinateur quantique.
 */
public class PartBlock extends DirectionalBlock {

    public enum Kind {
        /** 0.17.2 : ordinateur quantique (refrigerateur a dilution, le lustre). */
        DILUTION_REFRIGERATOR;

        public String id() {
            return "industrial_" + name().toLowerCase(Locale.ROOT);
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Piece qui eclaire en marche. */
        public boolean glows() {
            return true;
        }

        /** 0.19.13 : piece de toit (refrigerateur a dilution) : toujours tournee vers le haut. */
        public boolean roof() {
            return true;
        }

        /**
         * 0.19.13 : piece logee dans une niche de sa face exterieure (le corps de carcasse y laisse une ouverture) ; sinon (refrigerateur a
         * dilution) la piece se pose sur le toit, sans corps quand elle est seule au-dessus du sommet.
         */
        public boolean niche() {
            return false;
        }
    }

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public final Kind kind;

    public PartBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(ACTIVE, false).setValue(IndustryStyle.STYLE, IndustryStyle.NEUTRAL)
              .setValue(IndustryStyle.TIER, 0));
    }

    /** 0.19.13 : metier et tier peints par le controleur (corps de carcasse dessine par IndustrialBodyModel, comme les ports). */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE, IndustryStyle.STYLE, IndustryStyle.TIER);
    }

    /**
     * 1.0 : la piece se tourne vers le joueur (selon sa position par rapport au bloc, pas selon la face visee) : posee depuis l'exterieur
     * d'une paroi, elle regarde dehors ; posee depuis le toit, vers le haut.
     */
    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        //0.19.13 : une piece de toit regarde toujours le ciel ; les autres sont retournees vers l'exterieur a la formation (ControllerTile)
        return defaultBlockState().setValue(FACING, kind.roof() ? Direction.UP : towardPlacer(context));
    }

    /** Direction du bloc vers les yeux du joueur (axe dominant) ; sans joueur : la face cliquee. */
    public static Direction towardPlacer(BlockPlaceContext context) {
        if (context.getPlayer() == null) {
            return context.getClickedFace();
        }
        BlockPos pos = context.getClickedPos();
        double dx = context.getPlayer().getX() - (pos.getX() + 0.5), dy = context.getPlayer().getEyeY() - (pos.getY() + 0.5);
        double dz = context.getPlayer().getZ() - (pos.getZ() + 0.5);
        return Direction.getNearest(dx, dy, dz);
    }

    @Override
    public void animateTick(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (!state.getValue(ACTIVE)) {
            return;
        }
        //1.0.4-beta (lot 15) : seul reste le refrigerateur a dilution (flocons sous le lustre)
        switch (kind) {
            case DILUTION_REFRIGERATOR -> {
                if (random.nextInt(3) == 0) {
                    level.addParticle(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 0.4,
                          pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0, -0.03, 0);
                }
            }
        }
    }
}

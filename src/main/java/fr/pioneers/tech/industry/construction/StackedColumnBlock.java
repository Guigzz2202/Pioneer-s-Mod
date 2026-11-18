package fr.pioneers.tech.industry.construction;

import fr.pioneers.tech.industry.multi.ModuleBlock;
import fr.pioneers.logistics.transport.CableTier;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 22) : bloc de construction EMPILABLE, non plein, toujours vertical (rotor de turbine, train de forage, pompe a colonne ;
 * maquettes validees par Guillaume le 08/11/2026). Etat {@code part} = {@code single | bottom | middle | top} calcule d'apres les voisins du
 * dessus et du dessous de MEME ID (meme famille ET meme tier : la bande et le metal du tier changent d'un tier a l'autre, une colonne ne se
 * raccorde qu'a ses jumeaux) : seul = palier et accouplement (trepan et mandrin, crepine et tete) ; bas, milieu, haut d'une colonne continue.
 * Aucune integration dans les multiblocs (lot 22) : ce n'est pas une carcasse. Ressources : {@code tools/gc/industry/stacked_blocks.py}.
 * <p>
 * 1.0.4-beta (lot 25) : {@link #HIDDEN} (meme propriete que les modules) : dans un multibloc FORME dont la colonne tourne (train de forage de
 * la foreuse, pompes a colonne de la pompe a fluide, train en bronze de la foreuse a vapeur ; {@code GeneratedNbtModels}), le bloc n'est plus
 * dessine par le monde : le controleur le dessine, tournant en marche ({@code client/industry/big/NbtAnimations}). Les etats {@code part=}
 * des fichiers d'etat ne nomment pas {@code hidden} (comme les modules) : un seul modele par morceau.
 */
public class StackedColumnBlock extends Block {

    /** Morceau de la colonne. */
    public enum Part implements StringRepresentable {
        SINGLE, BOTTOM, MIDDLE, TOP;

        @NotNull
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Morceau d'apres la presence d'un jumeau dessous / dessus. */
        public static Part of(boolean below, boolean above) {
            return below ? above ? MIDDLE : TOP : above ? BOTTOM : SINGLE;
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** 1.0.4-beta (lot 25) : dessine par le controleur du multibloc forme (colonne tournante). */
    public static final BooleanProperty HIDDEN = ModuleBlock.HIDDEN;

    /** Famille du bloc (prefixe de l'id : turbine_rotor, drill_string, column_pump). */
    public final String family;
    /** Tier MK du bloc ; nul pour le train de forage en bronze (1.0.4-beta lot 23 : age du bronze, {@code drill_string_bronze}). */
    @Nullable
    public final CableTier tier;
    private final VoxelShape[] shapes;

    public StackedColumnBlock(String family, @Nullable CableTier tier, Properties properties) {
        super(properties);
        this.family = family;
        this.tier = tier;
        this.shapes = shapes(family);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.SINGLE).setValue(HIDDEN, false));
    }

    /** Formes de selection et de collision par morceau : l'arbre central, les paliers / brides aux extremites (en pixels de bloc). */
    private static VoxelShape[] shapes(String family) {
        VoxelShape[] out = new VoxelShape[Part.values().length];
        for (Part p : Part.values()) {
            boolean lo = p == Part.SINGLE || p == Part.BOTTOM, hi = p == Part.SINGLE || p == Part.TOP;
            VoxelShape s = switch (family) {
                //rotor : moyeu et aubes ramenes au bloc (les aubes du modele font 2,5 blocs de diametre depuis le lot 23 ; une selection de
                //40 px chevaucherait les voisins), palier en bas, accouplement en haut
                case "turbine_rotor" -> Shapes.or(Block.box(0.5, p == Part.MIDDLE ? 4 : lo ? 5 : 3, 0.5, 15.5, p == Part.MIDDLE ? 11 : lo ? 12 : 10, 15.5),
                      Block.box(6, 0, 6, 10, 16, 10), lo ? Block.box(2.5, 0, 2.5, 13.5, 4, 13.5) : Shapes.empty(),
                      hi ? Block.box(3.5, 12.5, 3.5, 12.5, 16, 12.5) : Shapes.empty());
                //train de forage : tige et spire de tariere (14 px), trepan en bas, mandrin en haut
                case "drill_string" -> Shapes.or(Block.box(1.5, lo ? 7 : 0, 1.5, 14.5, hi ? 9 : 16, 14.5), lo ? Block.box(3, 0, 3, 13, 7, 13) : Shapes.empty(),
                      hi ? Block.box(3.5, 9, 3.5, 12.5, 16, 12.5) : Shapes.empty());
                //pompe : corps octogonal, brides de 10 px
                default -> Shapes.or(Block.box(3.5, 0, 3.5, 12.5, 16, 12.5), Block.box(3, lo ? 5 : 0, 3, 13, lo ? 6.5 : 2, 13),
                      hi ? Block.box(3, 6, 3, 13, 12.5, 13) : Shapes.empty());
            };
            out[p.ordinal()] = s.optimize();
        }
        return out;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, HIDDEN);
    }

    /** 1.0.4-beta (lot 25) : masque = dessine par le controleur (colonne tournante). */
    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return state.getValue(HIDDEN) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    private boolean twin(BlockState state) {
        return state.is(this);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        return defaultBlockState().setValue(PART, Part.of(twin(context.getLevel().getBlockState(pos.below())),
              twin(context.getLevel().getBlockState(pos.above()))));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction side, @NotNull BlockState neighbor, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        if (side.getAxis() != Direction.Axis.Y) {
            return state;
        }
        Part p = state.getValue(PART);
        boolean below = p == Part.MIDDLE || p == Part.TOP, above = p == Part.MIDDLE || p == Part.BOTTOM;
        if (side == Direction.DOWN) {
            below = twin(neighbor);
        } else {
            above = twin(neighbor);
        }
        return state.setValue(PART, Part.of(below, above));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return shapes[state.getValue(PART).ordinal()];
    }

    @Override
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return true;
    }
}

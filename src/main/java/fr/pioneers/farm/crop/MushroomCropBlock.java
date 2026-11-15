package fr.pioneers.farm.crop;

import fr.pioneers.Pioneers;
import fr.pioneers.space.farm.HydroponicSubstrateBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 1.0.3-beta (lot 13, Guillaume : « ameliorer le farming des champignons, unifier avec le mod ») : champignon cultive (brun, rouge, de
 * Paris ; culture d'archetype {@code mushroom} de {@code tools/gc/farm2/crops.py}). Tout le reste vient de {@link FarmCropBlock} : genes,
 * stades, climat du biome (temperature, humidite, Resistance), recolte au clic, volees successives (repousse), soins, robots. Ce qui change :
 * <ul>
 * <li>sol : tag {@link #SOILS} (substrat a champignons, mycelium, podzol) ou substrat hydroponique beche ; jamais la terre labouree ;</li>
 * <li>lumiere : pousse a l'OMBRE, lumiere du lieu au plus {@link #MAX_LIGHT} (comme les champignons sauvages : caves, sous-bois, nuit),
 * serre comprise ; une plante en plein jour ne fane pas, elle attend ;</li>
 * <li>vitesse : substrat x{@link #SUBSTRATE_GROWTH} (toujours humide), mycelium et podzol x{@link #WILD_GROWTH}, hydroponique x1,5.</li>
 * </ul>
 */
public class MushroomCropBlock extends FarmCropBlock {

    public static final TagKey<Block> SOILS = BlockTags.create(Pioneers.rl("mushroom_soils"));
    public static final int MAX_LIGHT = 12;
    public static final float SUBSTRATE_GROWTH = 1.25F;
    public static final float WILD_GROWTH = 0.75F;
    private static final VoxelShape[] SHAPES = new VoxelShape[8];

    static {
        for (int i = 0; i < 8; i++) {
            SHAPES[i] = Block.box(3, 0, 3, 13, 2 + i, 13);
        }
    }

    public MushroomCropBlock(CropDef def, Properties properties) {
        super(def, properties);
    }

    /** Vrai pour un bloc ou pousse un champignon cultive. */
    public static boolean soil(BlockState state) {
        return state.is(SOILS) || HydroponicSubstrateBlock.isTilled(state);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return soil(state);
    }

    /** Pas de condition de lumiere pour tenir (celle de {@code CropBlock} demande au contraire de la lumiere) : le sol suffit. */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return soil(level.getBlockState(pos.below()));
    }

    /** A l'ombre : lumiere du lieu (ciel assombri par l'heure et la meteo, plus les torches) au plus {@link #MAX_LIGHT}. */
    public static boolean shaded(Level level, BlockPos pos) {
        return level.getRawBrightness(pos, level.getSkyDarken()) <= MAX_LIGHT;
    }

    @Override
    protected boolean lightOk(Level level, BlockPos pos, Climate.Status climate) {
        return shaded(level, pos);
    }

    @Override
    public float soilFactor(BlockGetter level, BlockPos pos) {
        BlockState soil = level.getBlockState(pos.below());
        if (HydroponicSubstrateBlock.isTilled(soil)) {
            return HydroponicSubstrateBlock.GROWTH;
        }
        return soil.getBlock() instanceof MushroomSubstrateBlock ? SUBSTRATE_GROWTH : WILD_GROWTH;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[getAge(state)];
    }
}

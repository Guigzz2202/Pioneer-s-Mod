package fr.pioneers.farm.crop;

import fr.pioneers.farm.PiFarm;
import fr.pioneers.farm.hemp.PiHemp;
import fr.pioneers.space.farm.HydroponicSubstrateBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Culture genetique (Agriculture 2.0). Age 0..7 ; la tuile porte les genes. Pousse (tick aleatoire) : lumiere, climat ({@link Climate}),
 * terre humide (x1, seche x0,5), Croissance ; riziere : eau a cote de la terre labouree. Climat hostile : la plante fane apres quelques
 * ticks (bloc {@code withered_crop}). Clic droit sur une plante mure : recolte sur place ; une culture a repousse revient a {@link CropDef#regrow},
 * une autre est replantee avec la premiere graine fille. Cultures hautes : a partir de l'age {@link #TALL_AGE}, un bloc invisible
 * ({@link CropTopBlock}) reserve la case du dessus (le modele dessine les deux blocs).
 * <p>
 * 1.0.5-beta (lot 1) : climat saisonnier ({@link Climate}) et DELAI DE GRACE : hors climat, la plante cesse aussitot de pousser et fane
 * apres {@link #GRACE_TICKS} (un jour de jeu) passes hors climat d'affilee ({@link CropTile#hostileSince}) ; revenue dans son climat, le
 * compte repart de zero. Une plante mure suit la meme regle (elle recoit donc aussi les ticks aleatoires).
 */
public class FarmCropBlock extends CropBlock implements EntityBlock {

    public static final int TALL_AGE = 3;
    /** 1.0.5 : delai de grace hors climat avant de faner (un jour de jeu, en temps du jour du monde). */
    public static final long GRACE_TICKS = 24_000L;
    /** Intervalle moyen entre deux ticks aleatoires d'un bloc (vitesse 3). */
    private static final float RANDOM_TICK_INTERVAL = 4096F / 3F;
    private static final VoxelShape[] SHAPES = new VoxelShape[8];
    private static final VoxelShape TALL_SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    static {
        for (int i = 0; i < 8; i++) {
            SHAPES[i] = Block.box(1, 0, 1, 15, 2 + i * 2, 15);
        }
    }

    private final CropDef def;

    public FarmCropBlock(CropDef def, Properties properties) {
        super(properties);
        this.def = def;
    }

    public CropDef def() {
        return def;
    }

    /** 1.12.3 (lot F3) : se seme sur la terre labouree ou sur le substrat hydroponique beche. */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return super.mayPlaceOn(state, level, pos) || HydroponicSubstrateBlock.isTilled(state);
    }

    /** Pousse sur le substrat hydroponique beche ({@link HydroponicSubstrateBlock#GROWTH} fois plus vite ; climat inchange). */
    public static boolean hydroponic(BlockGetter level, BlockPos pos) {
        return HydroponicSubstrateBlock.isTilled(level.getBlockState(pos.below()));
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return PiFarm.seedOf(def.id());
    }

    public Item produce() {
        return PiFarm.item(def.produce());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int age = getAge(state);
        return def.tall() && age >= TALL_AGE ? TALL_SHAPE : SHAPES[age];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CropTile(pos, state);
    }

    @Nullable
    public static CropTile tile(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CropTile t ? t : null;
    }

    public static Genes genes(BlockGetter level, BlockPos pos) {
        CropTile t = tile(level, pos);
        return t == null ? Genes.BASE : t.genes();
    }

    /** Terre humide ; 1.12.3 : le substrat hydroponique beche l'est toujours (comme une terre irriguee : meme climat). */
    public static boolean irrigated(BlockGetter level, BlockPos pos) {
        BlockState soil = level.getBlockState(pos.below());
        return soil.hasProperty(FarmBlock.MOISTURE) && soil.getValue(FarmBlock.MOISTURE) > 0 || HydroponicSubstrateBlock.isTilled(soil)
              || soil.getBlock() instanceof MushroomSubstrateBlock;          //1.0.3 (lot 13) : substrat a champignons toujours humide
    }

    /** Riziere : de l'eau touche la terre labouree ; 1.12.3 : le substrat hydroponique (gorge d'eau) suffit. */
    public static boolean flooded(LevelReader level, BlockPos pos) {
        BlockPos soil = pos.below();
        if (HydroponicSubstrateBlock.isTilled(level.getBlockState(soil))) {
            return true;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getFluidState(soil.relative(d)).is(FluidTags.WATER) || level.getFluidState(pos.relative(d)).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    public Climate.Status climate(Level level, BlockPos pos) {
        return Climate.evaluate(level, pos, def, genes(level, pos), irrigated(level, pos));
    }

    // ------------------------------------------------------------------
    // Pousse
    // ------------------------------------------------------------------

    /** 1.0.5 : toujours (une plante mure hors climat fane aussi apres le delai de grace). */
    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    /** 1.0.5 : vrai si le delai de grace est ecoule (hors climat depuis {@code since}, -1 = pas encore compte). */
    public static boolean graceOver(long since, long now) {
        return since >= 0 && now >= since && now - since >= GRACE_TICKS;
    }

    /** 1.0.5 : ticks restants avant de faner (le delai entier si le compte n'a pas commence). */
    public static long graceLeft(long since, long now) {
        return since < 0 || now < since ? GRACE_TICKS : Math.max(0, GRACE_TICKS - (now - since));
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isAreaLoaded(pos, 1)) {
            return;
        }
        CropTile tile = tile(level, pos);
        Genes genes = tile == null ? Genes.BASE : tile.genes();
        Climate.Status climate = Climate.evaluate(level, pos, def, genes, irrigated(level, pos));
        if (climate != Climate.Status.GREENHOUSE) {
            //1.0.5-beta (lot 3) : culture sous l'ancienne serre de verre (qui ne compte plus sur Terre) : message unique aux joueurs
            fr.pioneers.farm.greenhouse.GreenhouseNotice.check(level, pos);
        }
        if (tile != null) {
            tile.setClimate(climate);
            if (!climate.grows()) {
                //1.0.5 : delai de grace (horodatage ; temps du jour en arriere apres un /time : le compte repart)
                long now = level.getDayTime();
                if (tile.hostileSince() < 0 || now < tile.hostileSince()) {
                    tile.setHostileSince(now);
                } else if (graceOver(tile.hostileSince(), now)) {
                    wither(level, pos);
                }
                return;
            }
            tile.setHostileSince(-1);
        }
        if (!climate.grows() || isMaxAge(state) || !lightOk(level, pos, climate)) {
            return;
        }
        if (def.paddy() && !flooded(level, pos)) {
            return;
        }
        float soil = soilFactor(level, pos);
        float chance = Math.min(1F, growthChance(genes, climate) * soil);
        if (ForgeHooks.onCropsGrowPre(level, pos, state, random.nextFloat() < chance)) {
            if (grow(level, pos, state, getAge(state) + 1)) {
                ForgeHooks.onCropsGrowPost(level, pos, state);
            }
        }
    }

    /** Lumiere suffisante pour pousser (sous verre de serre : toujours) ; 1.0.3 (lot 13) : les champignons demandent au contraire l'ombre. */
    protected boolean lightOk(Level level, BlockPos pos, Climate.Status climate) {
        return climate == Climate.Status.GREENHOUSE || level.getRawBrightness(pos, 0) >= 9;
    }

    /** Facteur du sol : 1.12.3 : substrat hydroponique beche x1,5 (le climat, lui, est evalue comme sur une terre irriguee), humide x1, sec x0,5. */
    public float soilFactor(BlockGetter level, BlockPos pos) {
        return hydroponic(level, pos) ? HydroponicSubstrateBlock.GROWTH : irrigated(level, pos) ? 1F : 0.5F;
    }

    /** Chance qu'un tick aleatoire fasse gagner un age (pousse complete en {@link CropDef#minutes} au climat ideal, genes de base 5). */
    public float growthChance(Genes genes, Climate.Status climate) {
        float ticks = Math.max(1F, def.minutes()) * 1200F;
        return Math.min(1F, getMaxAge() * RANDOM_TICK_INTERVAL / ticks * genes.growthFactor() / 1.05F * climate.factor);
    }

    /** Passe a l'age donne (borne) ; une culture haute qui ne peut pas monter reste a l'age d'avant. */
    public boolean grow(Level level, BlockPos pos, BlockState state, int age) {
        age = Math.min(age, getMaxAge());
        if (age <= getAge(state)) {
            return false;
        }
        if (def.tall() && age >= TALL_AGE) {
            BlockPos above = pos.above();
            BlockState up = level.getBlockState(above);
            if (!(up.getBlock() instanceof CropTopBlock)) {
                if (!up.canBeReplaced()) {
                    if (getAge(state) >= TALL_AGE - 1) {
                        return false;
                    }
                    age = Math.min(age, TALL_AGE - 1);
                } else {
                    level.setBlock(above, PiFarm.CROP_TOP.get().defaultBlockState(), 3);
                }
            }
        }
        level.setBlock(pos, state.setValue(getAgeProperty(), age), 2);
        return true;
    }

    private void wither(ServerLevel level, BlockPos pos) {
        removeTop(level, pos);
        level.setBlock(pos, PiFarm.WITHERED_CROP.get().defaultBlockState(), 3);
        level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.8F, 0.6F);
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 6, 0.25, 0.2, 0.25, 0.01);
    }

    private static void removeTop(Level level, BlockPos pos) {
        if (level.getBlockState(pos.above()).getBlock() instanceof CropTopBlock) {
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    @Override
    public void growCrops(Level level, BlockPos pos, BlockState state) {
        grow(level, pos, state, getAge(state) + getBonemealAgeIncrease(level));
    }

    @Override
    protected int getBonemealAgeIncrease(Level level) {
        return 1 + level.random.nextInt(2);
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        if (climate(level, pos).grows()) {
            growCrops(level, pos, state);
        }
    }

    // ------------------------------------------------------------------
    // Soins et degats (ferme animee)
    // ------------------------------------------------------------------

    /** Fertilise la culture (compost, engrais, fertirrigation) ; {@code stages} stades gagnes si le climat le permet. Vrai si quelque chose a change. */
    public boolean fertilize(ServerLevel level, BlockPos pos, BlockState state, int stages) {
        CropTile tile = tile(level, pos);
        boolean changed = tile != null && tile.addCare(CropTile.FERTILIZED);
        if (stages > 0 && !isMaxAge(state) && climate(level, pos).grows()) {
            changed |= grow(level, pos, state, getAge(state) + stages);
        }
        if (changed) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 5, 0.3, 0.2, 0.3, 0.01);
        }
        return changed;
    }

    /** Pollinisation par les abeilles : drapeau et un tick de pousse. */
    public void pollinate(ServerLevel level, BlockPos pos, BlockState state) {
        CropTile tile = tile(level, pos);
        if (tile != null) {
            tile.addCare(CropTile.POLLINATED);
        }
        if (!isMaxAge(state)) {
            randomTick(state, level, pos, level.random);
        }
    }

    /**
     * Ravage (corbeau) : la plante perd {@code stages} stades ; trop jeune, elle disparait (graine mangee). Vrai si la plante a disparu.
     */
    public boolean ravage(ServerLevel level, BlockPos pos, BlockState state, int stages) {
        int age = getAge(state);
        level.levelEvent(2001, pos, Block.getId(state));
        if (age - stages < 0 || age <= 2) {
            removeTop(level, pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return true;
        }
        int next = def.tall() && age - stages < TALL_AGE ? Math.min(age - stages, TALL_AGE - 1) : age - stages;
        if (def.tall() && next < TALL_AGE) {
            removeTop(level, pos);
        }
        level.setBlock(pos, state.setValue(getAgeProperty(), next), 3);
        CropTile tile = tile(level, pos);
        if (tile != null) {
            tile.clearCare();
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Recolte
    // ------------------------------------------------------------------

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!isMaxAge(state)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            List<ItemStack> drops = harvest(server, pos, state, true);
            for (ItemStack stack : drops) {
                popResource(level, pos, stack);
            }
            //1.9 (MAJ RP) : XP d'Agriculture, Main verte ; mains rapides = cueillir aussi une voisine mure
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                fr.pioneers.core.profile.SkillHooks.cropHarvested(sp, server, pos, drops);
                if (fr.pioneers.core.profile.SkillHooks.quickHands(sp, "farming.hand_speed")) {
                    for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        BlockPos n = pos.relative(d);
                        BlockState ns = level.getBlockState(n);
                        if (ns.getBlock() == this && isMaxAge(ns)) {
                            for (ItemStack stack : harvest(server, n, ns, true)) {
                                popResource(level, n, stack);
                            }
                            break;
                        }
                    }
                }
            }
            level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1F, 0.9F + level.random.nextFloat() * 0.2F);
            level.levelEvent(2001, pos, Block.getId(state));
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Recolte d'une plante mure : recolte + graines filles. {@code replant} : la plante repart (repousse, ou premiere graine fille). */
    public List<ItemStack> harvest(ServerLevel level, BlockPos pos, BlockState state, boolean replant) {
        return harvest(level, pos, state, replant, false);
    }

    /** {@code best} : replante avec la meilleure graine fille (robots agriculteurs) plutot que la premiere. */
    public List<ItemStack> harvest(ServerLevel level, BlockPos pos, BlockState state, boolean replant, boolean best) {
        CropTile tile = tile(level, pos);
        Genes genes = tile == null ? Genes.BASE : tile.genes();
        RandomSource random = level.random;
        int care = tile == null ? 0 : tile.care();
        //Soins : engrais (recolte x1,25, mutations plus frequentes et plus souvent favorables), pollinisation (plus souvent favorables)
        boolean fed = (care & CropTile.FERTILIZED) != 0, pollen = (care & CropTile.POLLINATED) != 0;
        float mutate = fed ? 0.10F : 0, up = (fed ? 0.20F : 0) + (pollen ? 0.10F : 0);
        List<ItemStack> out = new ArrayList<>(produce(genes, random, fed ? 1.25F : 1F));
        if (tile != null) {
            tile.clearCare();
        }
        if (def.regrows()) {
            if (random.nextFloat() < 0.25F + 0.03F * genes.fertility()) {
                out.add(seedStack(genes.child(random, mutate, up)));
            }
            if (replant) {
                level.setBlock(pos, state.setValue(getAgeProperty(), def.regrow()), 3);
            }
            return out;
        }
        List<ItemStack> seeds = new ArrayList<>();
        int n = genes.seedCount(random);
        for (int i = 0; i < n; i++) {
            seeds.add(seedStack(genes.child(random, mutate, up)));
        }
        if (replant && !seeds.isEmpty()) {
            int pick = 0;
            if (best) {
                for (int i = 1; i < seeds.size(); i++) {
                    if (Genes.of(seeds.get(i)).score() > Genes.of(seeds.get(pick)).score()) {
                        pick = i;
                    }
                }
            }
            Genes next = Genes.of(seeds.remove(pick));
            removeTop(level, pos);
            level.setBlock(pos, state.setValue(getAgeProperty(), 0), 3);
            CropTile fresh = tile(level, pos);
            if (fresh != null) {
                fresh.setGenes(next);
                fresh.setHostileSince(-1);
            }
        }
        out.addAll(seeds);
        return out;
    }

    public List<ItemStack> produce(Genes genes, RandomSource random) {
        return produce(genes, random, 1F);
    }

    public List<ItemStack> produce(Genes genes, RandomSource random, float bonus) {
        float amount = def.average() * genes.yieldFactor() * bonus;
        int count = (int) amount + (random.nextFloat() < amount - (int) amount ? 1 : 0);
        count = Math.max(1, count);
        List<ItemStack> out = new ArrayList<>();
        Item item = produce();
        while (count > 0) {
            int n = Math.min(count, item.getMaxStackSize());
            out.add(new ItemStack(item, n));
            count -= n;
        }
        //1.0.4-beta (lot 6) : sous-produits de la recolte (tetes du chanvre), aussi pour les robots et la recolte a la faux
        out.addAll(PiHemp.harvestExtras(def.id(), genes.yieldFactor() * bonus, random));
        return out;
    }

    public ItemStack seedStack(Genes genes) {
        return genes.apply(new ItemStack(getBaseSeedId()));
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        Genes genes = be instanceof CropTile t ? t.genes() : Genes.BASE;
        RandomSource random = builder.getLevel().random;
        if (!isMaxAge(state)) {
            return List.of(seedStack(genes));
        }
        List<ItemStack> out = new ArrayList<>(produce(genes, random));
        int n = def.regrows() ? 1 : genes.seedCount(random);
        for (int i = 0; i < n; i++) {
            out.add(seedStack(genes.child(random)));
        }
        return out;
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return seedStack(genes(level, pos));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!newState.is(this) && def.tall()) {
            removeTop(level, pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}

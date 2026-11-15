package fr.pioneers.logistics.robotics;

import fr.pioneers.core.Directions;
import fr.pioneers.core.PiTags;
import fr.pioneers.farm.PiFarm;
import fr.pioneers.farm.animal.AnimalData;
import fr.pioneers.farm.animal.AnimalSpecies;
import fr.pioneers.farm.animal.PiAnimals;
import fr.pioneers.logistics.robotics.works.WorksData;
import fr.pioneers.logistics.robotics.works.WorksPlan;
import fr.pioneers.space.farm.HydroponicSubstrateBlock;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.StemGrownBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Recherche du travail d'une zone : parcours incremental des blocs (du haut vers le bas, {@link #SCAN_BUDGET} positions par tick),
 * ou releve des entites (objets au sol, animaux, monstres). Les travaux trouves attendent dans une file ; une cle reservee n'est
 * donnee qu'a une unite, et un travail impossible n'est retente qu'apres une minute.
 */
public final class ZoneScanner {

    private static final int SCAN_BUDGET = 1024, MAX_JOBS = 64;
    /**
     * 0.20.46 (audit) : taches recurrentes (recolte, abattage, peche) : 256 positions par tick, et une passe qui n'a rien trouve met le
     * balayage en veille {@link #EMPTY_PASS_SLEEP} ticks (avant : 1 024 positions par tick et par ordre, sans pause). Le terrassement et
     * les travaux (qui avancent couche par couche) gardent {@link #SCAN_BUDGET}.
     */
    private static final int RECURRING_BUDGET = 256, EMPTY_PASS_SLEEP = 100;
    /** Fin de la veille du balayage (temps du monde). */
    private long sleepUntil;

    private final ArrayDeque<Job> jobs = new ArrayDeque<>();
    private final LongOpenHashSet queued = new LongOpenHashSet();
    private final LongOpenHashSet claimed = new LongOpenHashSet();
    private final Long2LongOpenHashMap blacklist = new Long2LongOpenHashMap();
    private long cursor;
    /** Empreinte des reglages de la zone : on repart de zero s'ils changent. */
    private int signature;

    public void tick(ServerLevel level, RoboticOrder order) {
        int sig = (order.task.ordinal() * 31 + (order.min == null ? 0 : order.min.hashCode())) * 31 + (order.max == null ? 0 : order.max.hashCode());
        if (order.task == RobotTask.BUILDER) {
            sig = sig * 31 + WorksData.get(level).version(order.id);
        }
        if (sig != signature) {
            signature = sig;
            jobs.clear();
            queued.clear();
            cursor = 0;
            layerY = Integer.MAX_VALUE;
            worksPhase = 0;
            fields.clear();
            sleepUntil = 0;
        }
        long now = level.getGameTime();
        if (now % 200 == 0) {
            blacklist.long2LongEntrySet().removeIf(entry -> entry.getLongValue() < now);
        }
        if (jobs.size() >= MAX_JOBS / 2) {
            return;
        }
        AABB box = order.zoneBox(level.getMinBuildHeight());
        switch (order.task) {
            case COLLECTOR -> {
                if (now % 20 == 0) {
                    for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
                        enqueue(Job.entity(RobotTask.COLLECTOR, item.getId(), item.blockPosition()));
                    }
                }
            }
            case BREEDER -> {
                if (now % 40 == 0) {
                    scanBreeding(level, box, order.herd);
                }
            }
            case HUNTER -> {
                if (now % 20 == 0) {
                    scanHunting(level, box, order.herd, order.hunt);
                }
            }
            case APIARIST -> {
                if (now % 40 == 0) {
                    scanApiary(level, box);
                }
            }
            case LOGISTICS -> {
            }
            case BUILDER -> {
                WorksPlan plan = WorksData.get(level).plan(order.id);
                if (plan != null) {
                    scanWorks(level, plan);
                }
            }
            default -> scanBlocks(level, order, order.task, box);
        }
    }

    private void enqueue(Job job) {
        long key = job.key;
        if (jobs.size() < MAX_JOBS && !queued.contains(key) && !claimed.contains(key) && !blacklist.containsKey(key)) {
            jobs.add(job);
            queued.add(key);
        }
    }

    /** Terrassement : couche en cours (du haut vers le bas), et cette passe a-t-elle trouve des blocs ? */
    private int layerY = Integer.MAX_VALUE;
    private boolean layerFound;
    /** Minage : la derniere passe complete n'a rien trouve. */
    private boolean passFound, passEmpty;

    /**
     * Travail « en une fois » termine (terrassement creuse jusqu'en bas, minage sans plus aucun minerai) et plus rien en cours :
     * l'ordre peut disparaitre.
     */
    public boolean isFinished(RobotTask task, AABB box) {
        if (!jobs.isEmpty() || !claimed.isEmpty()) {
            return false;
        }
        return switch (task) {
            case EXCAVATOR -> layerY != Integer.MAX_VALUE && layerY < (int) box.minY;
            case MINER -> passEmpty;
            case BUILDER -> worksPhase == 2;
            default -> false;
        };
    }

    /**
     * Terrassement methodique : une couche a la fois, du haut vers le bas, en serpentin (rangee par rangee) ; on ne descend qu'une fois
     * la couche videe, donc chaque bloc vise est accessible par le dessus.
     */
    private void scanLayers(ServerLevel level, AABB box) {
        int x0 = (int) box.minX, z0 = (int) box.minZ, sx = (int) box.getXsize(), sz = (int) box.getZsize();
        int yTop = (int) box.maxY - 1, yBottom = (int) box.minY;
        if (layerY > yTop) {
            layerY = yTop;
            cursor = 0;
            layerFound = false;
        }
        if (layerY < yBottom) {
            //Tout est creuse : l'ordonnanceur retire l'ordre
            return;
        }
        int layer = sx * sz;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < SCAN_BUDGET && jobs.size() < MAX_JOBS; i++) {
            if (cursor >= layer) {
                //Fin de la passe : on descend si la couche est vide et que plus rien n'y est en cours
                cursor = 0;
                if (!layerFound && jobs.isEmpty() && claimed.isEmpty()) {
                    layerY--;
                }
                layerFound = false;
                return;
            }
            int index = (int) cursor++;
            int row = index / sx, col = index % sx;
            if ((row & 1) == 1) {
                col = sx - 1 - col;
            }
            pos.set(x0 + col, layerY, z0 + row);
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (isTarget(level, RobotTask.EXCAVATOR, pos)) {
                layerFound = true;
                enqueue(Job.block(RobotTask.EXCAVATOR, pos));
            }
        }
    }

    // ------------------------------------------------------------------
    // Travaux
    // ------------------------------------------------------------------

    /** Travaux : 0 = retraits (du haut vers le bas), 1 = poses (du bas vers le haut), 2 = termine. */
    private int worksPhase;
    /** Poses de la couche en cours, regroupees par objet et par voisinage (une unite pose tout un groupe d'un seul endroit). */
    private final List<Job> openGroups = new ArrayList<>();
    private static final int GROUP_SIZE = 16;
    private static final double GROUP_RADIUS = 3.5;

    /**
     * Travaux, couche par couche : on retire d'abord tout ce qui ne correspond pas au plan, du haut vers le bas (chaque couche videe
     * avant la suivante), puis on pose, du bas vers le haut (chaque bloc a son appui). Le plan est relu a chaque passe : un bloc remis
     * entre-temps est simplement retraite.
     */
    private void scanWorks(ServerLevel level, WorksPlan plan) {
        AABB box = plan.bounds();
        if (box == null || worksPhase == 2) {
            return;
        }
        int x0 = (int) Math.floor(box.minX), z0 = (int) Math.floor(box.minZ), sx = (int) box.getXsize(), sz = (int) box.getZsize();
        int yTop = (int) Math.floor(box.maxY) - 1, yBottom = (int) Math.floor(box.minY);
        boolean removing = worksPhase == 0;
        if (layerY == Integer.MAX_VALUE) {
            layerY = removing ? yTop : yBottom;
            cursor = 0;
            layerFound = false;
        }
        int layer = sx * sz;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < SCAN_BUDGET && jobs.size() < MAX_JOBS; i++) {
            if (cursor >= layer) {
                cursor = 0;
                flushGroups();
                if (!layerFound && jobs.isEmpty() && claimed.isEmpty()) {
                    layerY += removing ? -1 : 1;
                    if (removing && layerY < yBottom) {
                        worksPhase = 1;
                        layerY = Integer.MAX_VALUE;
                    } else if (!removing && layerY > yTop) {
                        worksPhase = 2;
                    }
                }
                layerFound = false;
                return;
            }
            int index = (int) cursor++;
            int row = index / sx, col = index % sx;
            if ((row & 1) == 1) {
                col = sx - 1 - col;
            }
            pos.set(x0 + col, layerY, z0 + row);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState target = plan.target(pos);
            if (target == null) {
                continue;
            }
            BlockState current = level.getBlockState(pos);
            if (WorksPlan.matches(current, target)) {
                continue;
            }
            if (removing) {
                if (needsRemoval(level, pos, current, target)) {
                    layerFound = true;
                    enqueue(Job.block(RobotTask.BUILDER, pos));
                }
            } else if (needsPlacement(current, target)) {
                ItemStack material = WorksPlan.requiredItem(level, pos, target, current);
                if (material.isEmpty() && !WorksPlan.isFreeHalf(target) && !WorksPlan.convertible(current, target)) {
                    continue;
                }
                layerFound = true;
                group(pos.immutable(), target, material);
            }
        }
    }

    /** Ce bloc doit-il partir (il gene le plan et une unite peut le retirer) ? */
    static boolean needsRemoval(ServerLevel level, BlockPos pos, BlockState current, BlockState target) {
        if (current.isAir() || current.getBlock() instanceof LiquidBlock || current.is(Blocks.BEDROCK)) {
            return false;
        }
        //Herbes, neige, fleurs... : la pose les remplace directement ; la terre se faconne sur place (chemin, terre labouree)
        if (!target.isAir() && current.canBeReplaced() || WorksPlan.convertible(current, target)) {
            return false;
        }
        return RoboticsWorld.canBreak(level, pos, current);
    }

    /** Faut-il poser ici (la place est libre ou remplacable) ? */
    static boolean needsPlacement(BlockState current, BlockState target) {
        return !target.isAir() && !(target.getBlock() instanceof LiquidBlock)
               && (current.isAir() || current.canBeReplaced() || WorksPlan.convertible(current, target));
    }

    private void group(BlockPos pos, BlockState target, ItemStack material) {
        for (Job job : openGroups) {
            if (ItemStack.isSameItemSameTags(job.material, material) && job.extra.size() + 1 < GROUP_SIZE && job.pos.closerThan(pos, GROUP_RADIUS)) {
                job.extra.add(pos);
                job.states.add(target);
                return;
            }
        }
        Job job = Job.block(RobotTask.BUILDER, pos);
        job.key = pos.asLong() ^ 0x2000_0000_0000_0000L;
        job.place = target;
        job.states.add(target);
        job.material = material;
        openGroups.add(job);
        if (openGroups.size() > 24) {
            enqueue(openGroups.remove(0));
        }
    }

    private void flushGroups() {
        for (Job job : openGroups) {
            enqueue(job);
        }
        openGroups.clear();
    }

    /** Positions d'un travail de pose (la premiere, puis les autres du groupe). */
    public static List<BlockPos> placements(Job job) {
        List<BlockPos> list = new ArrayList<>();
        list.add(job.pos);
        list.addAll(job.extra);
        return list;
    }

    /** Parcourt la zone par petits morceaux, du haut vers le bas. */
    /** Agriculture : cases vues en terre labouree (pour rebecher celles qui ont ete pietinees). */
    private final LongOpenHashSet fields = new LongOpenHashSet();

    private void scanBlocks(ServerLevel level, RoboticOrder order, RobotTask task, AABB box) {
        if (task == RobotTask.EXCAVATOR) {
            scanLayers(level, box);
            return;
        }
        int x0 = (int) box.minX, z0 = (int) box.minZ, sx = (int) box.getXsize(), sz = (int) box.getZsize();
        int yTop = (int) box.maxY - 1, yBottom = (int) box.minY;
        long layer = (long) sx * sz;
        long volume = layer * (yTop - yBottom + 1);
        if (volume <= 0 || level.getGameTime() < sleepUntil) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < RECURRING_BUDGET && jobs.size() < MAX_JOBS; i++) {
            if (cursor >= volume) {
                cursor = 0;
                passEmpty = !passFound && jobs.isEmpty() && claimed.isEmpty();
                if (!passFound) {
                    sleepUntil = level.getGameTime() + EMPTY_PASS_SLEEP;
                }
                passFound = false;
                return;
            }
            long index = cursor++;
            int y = yTop - (int) (index / layer);
            int inLayer = (int) (index % layer);
            pos.set(x0 + inLayer % sx, y, z0 + inLayer / sx);
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (task == RobotTask.FARMER && order.replant) {
                Job field = fieldJob(level, order, pos);
                if (field != null) {
                    passFound = true;
                    enqueue(field);
                    continue;
                }
            }
            if (isTarget(level, task, pos)) {
                passFound = true;
                Job job = Job.block(task, pos);
                if (task == RobotTask.LUMBERJACK) {
                    //Position figee : « pos » est reutilise par le balayage (le pied de l'arbre se deplacait avec lui)
                    collectTree(level, job.pos, job);
                }
                enqueue(job);
            }
        }
    }

    /**
     * Entretien du champ : terre pietinee (terre ou herbe la ou il y avait de la terre labouree, ou entre deux terres labourees) a
     * rebecher, terre labouree vide a semer. Null si rien a faire ici.
     */
    @Nullable
    private Job fieldJob(ServerLevel level, RoboticOrder order, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        //0.20.46 (audit) : le bloc du dessus n'est lu que pour la terre (avant : pour chaque position balayee)
        //1.12.3 (lot F3) : le substrat hydroponique beche se seme comme la terre labouree ; tasse, il se beche
        boolean farmland = HydroponicSubstrateBlock.isFarmSoil(state);
        boolean tillable = state.is(PiTags.Blocks.ROBOT_TILLABLE) || HydroponicSubstrateBlock.isPacked(state);
        if (!farmland && !tillable) {
            return null;
        }
        BlockState above = level.getBlockState(pos.above());
        boolean free = above.isAir() || above.canBeReplaced() && above.getFluidState().isEmpty() && !(above.getBlock() instanceof CropBlock);
        if (farmland) {
            fields.add(pos.asLong());
            if (above.isAir()) {
                ItemStack seed = seedFor(level, order, pos.above());
                if (!seed.isEmpty()) {
                    Job job = Job.block(RobotTask.FARMER, pos.above());
                    job.key = pos.asLong() ^ 0x1100_0000_0000_0000L;
                    job.farm = 2;
                    job.material = seed;
                    return job;
                }
            }
            return null;
        }
        if (tillable && free
            && (fields.contains(pos.asLong()) || farmlandAround(level, pos) >= 2)) {
            Job job = Job.block(RobotTask.FARMER, pos.immutable());
            job.key = pos.asLong() ^ 0x1200_0000_0000_0000L;
            job.farm = 1;
            job.material = seedFor(level, order, pos.above());
            return job;
        }
        return null;
    }

    private static int farmlandAround(ServerLevel level, BlockPos pos) {
        int count = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (HydroponicSubstrateBlock.isFarmSoil(level.getBlockState(pos.relative(side)))) {
                count++;
            }
        }
        return count;
    }

    /** Graine a semer ici : celle choisie pour la zone, sinon celle des cultures voisines, sinon des graines de ble. */
    public static ItemStack seedFor(ServerLevel level, RoboticOrder order, BlockPos crop) {
        ItemStack chosen = order.plantItem();
        if (!chosen.isEmpty()) {
            return chosen;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos near = crop.relative(side);
            BlockState state = level.getBlockState(near);
            if (state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock) {
                @SuppressWarnings("deprecation")
                ItemStack seed = state.getBlock().getCloneItemStack(level, near, state);
                if (!seed.isEmpty()) {
                    return seed.copyWithCount(1);
                }
            }
        }
        return new ItemStack(PiFarm.seedOf("wheat"));
    }

    /** Ce bloc est-il a traiter pour la tache ? */
    public static boolean isTarget(ServerLevel level, RobotTask task, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return switch (task) {
            case FARMER -> isRipe(level, pos, state);
            case LUMBERJACK -> state.is(BlockTags.LOGS) && !level.getBlockState(pos.below()).is(BlockTags.LOGS)
                               && level.getBlockState(pos.below()).is(BlockTags.DIRT);
            case MINER -> false;
            case EXCAVATOR -> RoboticsWorld.canBreak(level, pos, state) && !state.is(Blocks.BEDROCK);
            case FISHER -> state.getFluidState().is(Fluids.WATER) && state.getFluidState().isSource() && level.getBlockState(pos.above()).isAir()
                           && level.random.nextInt(4) == 0;
            default -> false;
        };
    }

    /**
     * Plantes en colonne : canne a sucre, cactus, bambou, algues. On garde le pied, on recolte le reste ; la recolte vise la deuxieme case
     * (algues : le sommet, pres de la surface).
     */
    public static boolean sameColumn(BlockState a, BlockState b) {
        if (isKelp(a)) {
            return isKelp(b);
        }
        return a.is(PiTags.Blocks.ROBOT_COLUMN_PLANTS) && b.is(a.getBlock());
    }

    public static boolean isColumnPlant(BlockState state) {
        return state.is(PiTags.Blocks.ROBOT_COLUMN_PLANTS) || isKelp(state);
    }

    private static boolean isKelp(BlockState state) {
        return state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT);
    }

    /**
     * Culture mure : ble, carottes, pommes de terre, betteraves, fleur-torche, citrouilles, melons, canne a sucre, cactus, bambou, algues,
     * baies (sucrees et lumineuses), verrues, cacao.
     */
    public static boolean isRipe(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (state.getBlock() instanceof StemGrownBlock) {
            return true;
        }
        if (isKelp(state)) {
            //Sommet d'une algue d'au moins deux cases
            return !isKelp(level.getBlockState(pos.above())) && isKelp(level.getBlockState(pos.below()));
        }
        if (isColumnPlant(state)) {
            //Deuxieme case d'une colonne : le pied reste
            return sameColumn(state, level.getBlockState(pos.below())) && !sameColumn(state, level.getBlockState(pos.below(2)));
        }
        if (state.getBlock() instanceof CaveVines) {
            return CaveVines.hasGlowBerries(state);
        }
        if (state.getBlock() instanceof SweetBerryBushBlock) {
            return state.getValue(SweetBerryBushBlock.AGE) >= 3;
        }
        if (state.getBlock() instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= 3;
        }
        if (state.getBlock() instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) >= 2;
        }
        return false;
    }

    /** Tronc entier a partir de sa base (rondins relies, 200 au plus), du haut vers le bas. */
    private static void collectTree(ServerLevel level, BlockPos root, Job job) {
        root = root.immutable();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        queue.add(root);
        seen.add(root.asLong());
        List<BlockPos> logs = new ArrayList<>();
        while (!queue.isEmpty() && logs.size() < 200) {
            BlockPos pos = queue.poll();
            logs.add(pos);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos next = pos.offset(dx, dy, dz);
                        if (next.getY() - root.getY() <= 32 && seen.add(next.asLong()) && level.getBlockState(next).is(BlockTags.LOGS)) {
                            queue.add(next);
                        }
                    }
                }
            }
        }
        logs.sort((a, b) -> b.getY() - a.getY());
        //Feuillage naturel autour du tronc (pas les feuilles posees par un joueur) : il donne les pousses a replanter
        List<BlockPos> leaves = new ArrayList<>();
        ArrayDeque<BlockPos> front = new ArrayDeque<>(logs);
        Map<Long, Integer> depth = new HashMap<>();
        for (BlockPos log : logs) {
            depth.put(log.asLong(), 0);
        }
        while (!front.isEmpty() && leaves.size() < 320) {
            BlockPos pos = front.poll();
            int d = depth.get(pos.asLong());
            if (d >= 6) {
                continue;
            }
            for (Direction dir : Directions.ALL) {
                BlockPos next = pos.relative(dir);
                if (depth.containsKey(next.asLong())) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.is(BlockTags.LEAVES) && !(state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT))) {
                    depth.put(next.asLong(), d + 1);
                    leaves.add(next);
                    front.add(next);
                }
            }
        }
        leaves.sort((a, b) -> b.getY() - a.getY());
        job.extra.addAll(leaves);
        job.extra.addAll(logs);
        job.woodName = ForgeRegistries.BLOCKS.getKey(level.getBlockState(root).getBlock()).getPath();
    }

    /**
     * Elevage : on nourrit toujours les couples d'adultes disponibles ; si une espece depasse la limite d'adultes, le surplus est
     * elimine (les plus vieux d'abord).
     */
    private void scanBreeding(ServerLevel level, AABB box, int limit) {
        //1.0.3-beta lot 8 : un troupeau = une ESPECE (les races sont des entites a part et s'accouplent entre elles)
        Map<Object, List<Animal>> herds = new HashMap<>();
        for (Animal animal : level.getEntitiesOfClass(Animal.class, box)) {
            if (animal.isAlive()) {
                herds.computeIfAbsent(herdKey(animal), k -> new ArrayList<>()).add(animal);
            }
        }
        for (List<Animal> herd : herds.values()) {
            List<Animal> adults = new ArrayList<>();
            for (Animal animal : herd) {
                if (animal.getAge() >= 0) {
                    adults.add(animal);
                }
            }
            if (adults.size() > limit) {
                adults.sort((a, b) -> Integer.compare(b.tickCount, a.tickCount));
                for (int i = 0; i < adults.size() - limit; i++) {
                    Job job = Job.entity(RobotTask.BREEDER, adults.get(i).getId(), adults.get(i).blockPosition());
                    job.cull = true;
                    job.key |= 0x2000_0000_0000_0000L;
                    enqueue(job);
                }
            }
            //1.3.0 : couples d'un male et d'une femelle en forme (especes d'elevage) ; les autres especes comme avant
            List<Animal> males = new ArrayList<>(), females = new ArrayList<>();
            for (Animal animal : herd) {
                if (animal.getAge() != 0 || animal.isInLove()) {
                    continue;
                }
                AnimalData data = AnimalSpecies.of(animal) == null ? null
                      : PiAnimals.get(animal);
                if (data != null && data.weak()) {
                    continue;
                }
                (data != null && data.male ? males : females).add(animal);
            }
            boolean sexed = !herd.isEmpty() && AnimalSpecies.of(herd.get(0)) != null;
            if (!sexed) {
                females.addAll(males);
                males.clear();
                for (int i = 0; i + 1 < females.size(); i += 2) {
                    males.add(females.get(i + 1));
                }
                List<Animal> firsts = new ArrayList<>();
                for (int i = 0; i < females.size(); i += 2) {
                    firsts.add(females.get(i));
                }
                females = firsts;
            }
            for (int i = 0; i < Math.min(males.size(), females.size()); i++) {
                Job job = Job.entity(RobotTask.BREEDER, females.get(i).getId(), females.get(i).blockPosition());
                job.partner = males.get(i).getId();
                enqueue(job);
            }
        }
    }

    /**
     * Chasse, selon les cibles choisies : les monstres, et/ou le surplus d'adultes des troupeaux au-dela de la limite (0 : tous les
     * adultes).
     */
    /** Troupeau d'un animal : son espece d'elevage (toutes races ; tags pioneers:cows...), sinon son type. */
    public static Object herdKey(Animal animal) {
        AnimalSpecies sp = AnimalSpecies.of(animal);
        return sp != null ? sp : animal.getType();
    }

    private void scanHunting(ServerLevel level, AABB box, int limit, int targets) {
        Map<Object, Integer> adults = new HashMap<>();
        boolean monsters = targets != RoboticOrder.HUNT_ANIMALS, animals = targets != RoboticOrder.HUNT_MONSTERS;
        //Les animaux des zones d'elevage (et les abeilles des ruchers) ne sont jamais chasses
        List<AABB> protectedZones = new ArrayList<>();
        for (RoboticOrder other : RoboticsOrders.get(level).all()) {
            if (other.type == RoboticOrder.Type.ZONE && other.min != null && other.max != null && other.task.protectsAnimals()) {
                protectedZones.add(new AABB(other.min, other.max.offset(1, 1, 1)).inflate(1));
            }
        }
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box, e -> monsters && e instanceof Enemy || animals && e instanceof Animal)) {
            if (entity instanceof Enemy && entity.isAlive()) {
                enqueue(Job.entity(RobotTask.HUNTER, entity.getId(), entity.blockPosition()));
            } else if (entity instanceof Animal animal && animal.getAge() >= 0) {
                if (protectedZones.stream().anyMatch(zone -> zone.contains(animal.position()))) {
                    continue;
                }
                int count = adults.merge(herdKey(animal), 1, Integer::sum);
                if (count > limit) {
                    enqueue(Job.entity(RobotTask.HUNTER, animal.getId(), animal.blockPosition()));
                }
            }
        }
    }

    /**
     * Rucher : chaque ruche ou nid de la zone accueille 3 abeilles ; tant qu'il en manque, les couples d'abeilles adultes sont nourris
     * (fleurs). Les ruches pleines de miel sont recoltees (rayons ou fioles, selon le reglage de la zone).
     */
    private void scanApiary(ServerLevel level, AABB box) {
        int hives = 0, bees = 0;
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ), max = BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive)) {
                continue;
            }
            hives++;
            bees += hive.getOccupantCount();
            if (honeyReady(level.getBlockState(pos))) {
                enqueue(Job.block(RobotTask.APIARIST, pos.immutable()));
            }
        }
        if (hives == 0) {
            return;
        }
        List<Bee> outside = level.getEntitiesOfClass(Bee.class, box.inflate(4),
              Bee::isAlive);
        bees += outside.size();
        int missing = hives * BeehiveBlockEntity.MAX_OCCUPANTS - bees;
        Bee first = null;
        for (Bee bee : outside) {
            if (missing <= 0) {
                break;
            }
            if (bee.getAge() != 0 || bee.isInLove() || bee.isAngry()) {
                continue;
            }
            if (first == null) {
                first = bee;
            } else {
                Job job = Job.entity(RobotTask.APIARIST, first.getId(), first.blockPosition());
                job.partner = bee.getId();
                enqueue(job);
                first = null;
                missing--;
            }
        }
    }

    /** Ruche ou nid plein de miel. */
    public static boolean honeyReady(BlockState state) {
        return state.getBlock() instanceof BeehiveBlock
               && state.getValue(BeehiveBlock.HONEY_LEVEL) >= BeehiveBlock.MAX_HONEY_LEVELS;
    }

    /** Prochain travail encore valide et que ce hub peut atteindre (reserve), ou null. */
    @Nullable
    public Job poll(ServerLevel level, Predicate<BlockPos> reachable) {
        int tries = jobs.size();
        while (tries-- > 0 && !jobs.isEmpty()) {
            Job job = jobs.poll();
            queued.remove(job.key);
            if (claimed.contains(job.key) || blacklist.get(job.key) > level.getGameTime() || !stillValid(level, job)) {
                continue;
            }
            if (job.pos != null && !reachable.test(job.pos)) {
                //Hors de portee de ce hub : un autre le prendra peut-etre
                jobs.add(job);
                queued.add(job.key);
                continue;
            }
            claimed.add(job.key);
            return job;
        }
        return null;
    }

    /** Travaux en file (pas encore confies). */
    public int backlog() {
        return jobs.size();
    }

    public void release(long key) {
        claimed.remove(key);
    }

    /** Travail remis a plus tard (materiau manquant...) : de nouveau propose apres {@code until}. */
    public void postpone(long key, long until) {
        claimed.remove(key);
        blacklist.put(key, until);
    }

    /** Travail impossible (inaccessible...) : on n'y revient pas avant une minute. */
    public void abandon(long key, long now) {
        claimed.remove(key);
        blacklist.put(key, now + 1200);
    }

    private static boolean worksValid(ServerLevel level, Job job) {
        if (job.place == null) {
            BlockState current = level.getBlockState(job.pos);
            return !current.isAir() && RoboticsWorld.canBreak(level, job.pos, current);
        }
        List<BlockPos> positions = placements(job);
        for (int i = 0; i < positions.size(); i++) {
            BlockState current = level.getBlockState(positions.get(i));
            BlockState target = job.states.get(i);
            if (!WorksPlan.matches(current, target) && needsPlacement(current, target)) {
                return true;
            }
        }
        return false;
    }

    /** Le travail est-il encore a faire (le monde a pu changer depuis la recherche) ? */
    private static boolean stillValid(ServerLevel level, Job job) {
        return switch (job.type) {
            case ENTITY -> {
                Entity entity = level.getEntity(job.entityId);
                yield entity != null && entity.isAlive();
            }
            case BLOCK -> job.pos != null && level.isLoaded(job.pos) && (job.task == RobotTask.BUILDER ? worksValid(level, job)
                                                                         : job.farm == 1 ? !HydroponicSubstrateBlock.isFarmSoil(level.getBlockState(job.pos))
                                                                         : job.farm == 2 ? level.getBlockState(job.pos).isAir()
                                                                                           && HydroponicSubstrateBlock.isFarmSoil(level.getBlockState(job.pos.below()))
                                                                         : job.task == RobotTask.APIARIST ? honeyReady(level.getBlockState(job.pos))
                                                                         : job.task == RobotTask.LUMBERJACK
                                                                         || job.task == RobotTask.FISHER && level.getFluidState(job.pos).isSource()
                                                                         || isTarget(level, job.task, job.pos));
            case TRIP -> true;
        };
    }
}

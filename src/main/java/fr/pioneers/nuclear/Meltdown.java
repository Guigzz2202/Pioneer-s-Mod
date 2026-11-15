package fr.pioneers.nuclear;

import fr.pioneers.Pioneers;
import fr.pioneers.industry.IndustryEvents;
import fr.pioneers.registries.PiNuclear;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Fusion du coeur (0.17.0). Au declenchement : explosion d'effet (degats, souffle, son), message aux joueurs proches, progres
 * {@code nuclear_meltdown}. Puis, etale sur plusieurs ticks : la structure disparait et son fond se remplit de {@code corium} (volume selon
 * le nombre d'assemblages), un cratere spherique de rayon 15 + N (au plus 25) se creuse (les blocs tres resistants restent, rien ne tombe),
 * la vegetation meurt dans la zone contaminee (rayon double), enfin la zone est inscrite dans {@link RadiationData} (demi-vie 72 000 ticks).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class Meltdown {

    /** Blocs traites par tick (cratere, vegetation). */
    private static final int BLOCKS_PER_TICK = 4_000, COLUMNS_PER_TICK = 400;
    public static final double HALF_LIFE = 72_000;
    private static final List<Task> TASKS = new ArrayList<>();

    private Meltdown() {
    }

    /** Rayon du cratere selon le nombre d'assemblages. */
    public static int radius(int assemblies) {
        return Math.min(25, 15 + assemblies);
    }

    public static void start(ServerLevel level, BlockPos min, BlockPos max, int assemblies, BlockPos controller) {
        start(level, min, max, assemblies, controller, false);
    }

    /**
     * 0.17.1 : {@code dispersal} = plasma du reacteur a fragments lache par son champ : pas de corium, la poussiere radioactive se disperse
     * (zone trois fois plus large que le cratere, plus intense).
     */
    public static void start(ServerLevel level, BlockPos min, BlockPos max, int assemblies, BlockPos controller, boolean dispersal) {
        Vec3 center = new Vec3((min.getX() + max.getX() + 1) / 2.0, (min.getY() + max.getY() + 1) / 2.0, (min.getZ() + max.getZ() + 1) / 2.0);
        int r = radius(assemblies);
        IndustryEvents.award(level, controller, "nuclear_meltdown");
        //Degats aux etres vivants : decroissants avec la distance, en plus de l'explosion d'effet
        AABB box = new AABB(center, center).inflate(r * 2);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            double d = e.position().distanceTo(center);
            if (d < r * 2) {
                e.hurt(level.damageSources().explosion(null, null), (float) (80 * (1 - d / (r * 2))));
            }
        }
        level.explode(null, center.x, center.y, center.z, 8.0F, Level.ExplosionInteraction.NONE);
        level.playSound(null, BlockPos.containing(center), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 8.0F, 0.5F);
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(center) < 384 * 384) {
                p.sendSystemMessage(Component.translatable(dispersal ? "nuclear.pioneers.dispersal" : "nuclear.pioneers.meltdown", (int) center.x,
                      (int) center.y, (int) center.z));
            }
        }
        TASKS.add(new Task(level, min, max, center, r, assemblies, dispersal));
    }

    /** 0.17.2 : rayon de l'explosion d'effet d'une disruption, rayon et intensite de la fuite de tritium, demi-vie (ticks). */
    public static final double DISRUPTION_RADIUS = 6, LEAK_RADIUS = 8, LEAK_STRENGTH = 3, LEAK_HALF_LIFE = 6_000;

    /**
     * 0.17.2 : disruption d'un plasma de fusion. Pas de fusion du coeur ni de corium : explosion d'effet moderee (rayon 6, aucun bloc casse),
     * degats aux etres vivants proches, message aux joueurs proches, progres {@code fusion_disruption}, petite fuite de tritium (zone faible
     * de rayon 8, demi-vie courte). Les carcasses brulees sont le fait du controleur.
     */
    public static void disruption(ServerLevel level, BlockPos min, BlockPos max, BlockPos controller, boolean tritium) {
        Vec3 center = new Vec3((min.getX() + max.getX() + 1) / 2.0, (min.getY() + max.getY() + 1) / 2.0, (min.getZ() + max.getZ() + 1) / 2.0);
        IndustryEvents.award(level, controller, "fusion_disruption");
        AABB box = new AABB(center, center).inflate(DISRUPTION_RADIUS * 2);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            double d = e.position().distanceTo(center);
            if (d < DISRUPTION_RADIUS * 2) {
                e.hurt(level.damageSources().explosion(null, null), (float) (12 * (1 - d / (DISRUPTION_RADIUS * 2))));
            }
        }
        level.explode(null, center.x, center.y, center.z, (float) (DISRUPTION_RADIUS / 2), Level.ExplosionInteraction.NONE);
        level.playSound(null, BlockPos.containing(center), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 4.0F, 0.6F);
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(center) < 96 * 96) {
                p.sendSystemMessage(Component.translatable("fusion.pioneers.disruption", (int) center.x, (int) center.y, (int) center.z));
            }
        }
        if (tritium) {
            RadiationData.get(level).addZone(center, LEAK_STRENGTH, LEAK_RADIUS, level.getGameTime(), LEAK_HALF_LIFE);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TASKS.clear();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || TASKS.isEmpty()) {
            return;
        }
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task task = it.next();
            if (task.level == level && task.step()) {
                it.remove();
            }
        }
    }

    /** Travail etale d'une fusion : 0 structure, 1 cratere puis corium, 2 vegetation, 3 zone. */
    private static final class Task {

        final ServerLevel level;
        final BlockPos min, max;
        final Vec3 center;
        final int radius, assemblies;
        final boolean dispersal;
        final RandomSource random;
        int phase, cursor;
        final List<BlockPos> crater = new ArrayList<>();

        Task(ServerLevel level, BlockPos min, BlockPos max, Vec3 center, int radius, int assemblies, boolean dispersal) {
            this.dispersal = dispersal;
            this.level = level;
            this.min = min;
            this.max = max;
            this.center = center;
            this.radius = radius;
            this.assemblies = assemblies;
            this.random = level.getRandom();
        }

        /** Un pas ; vrai quand tout est fini. */
        boolean step() {
            switch (phase) {
                case 0 -> {
                    structure();
                    prepareCrater();
                    phase = 1;
                }
                case 1 -> {
                    int end = Math.min(crater.size(), cursor + BLOCKS_PER_TICK);
                    for (; cursor < end; cursor++) {
                        blast(crater.get(cursor));
                    }
                    if (cursor >= crater.size()) {
                        crater.clear();
                        if (!dispersal) {
                            corium();
                        }
                        cursor = 0;
                        phase = 2;
                    }
                }
                case 2 -> {
                    int total = (int) (Math.PI * (dispersal ? 9 : 4) * radius * radius);
                    for (int i = 0; i < COLUMNS_PER_TICK && cursor < total; i++, cursor++) {
                        wither();
                    }
                    if (cursor >= total) {
                        phase = 3;
                    }
                }
                default -> {
                    RadiationData.get(level).addZone(center, (dispersal ? 40 : 20) + 4.0 * assemblies, radius * (dispersal ? 3 : 2),
                          level.getGameTime(), HALF_LIFE);
                    return true;
                }
            }
            return false;
        }

        /** La structure fond : tout disparait. */
        private void structure() {
            BlockState air = Blocks.AIR.defaultBlockState();
            for (BlockPos p : BlockPos.betweenClosed(min, max)) {
                if (level.isLoaded(p) && !level.getBlockState(p).isAir()) {
                    level.setBlock(p, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }

        /** Le corium coule au fond du cratere, sous l'emprise de la structure, du centre vers les bords, couche par couche. */
        private void corium() {
            List<BlockPos> columns = new ArrayList<>();
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    columns.add(new BlockPos(x, 0, z));
                }
            }
            columns.sort((a, b) -> Double.compare(horizontal(a), horizontal(b)));
            BlockState corium = PiNuclear.CORIUM.getBlock().defaultBlockState();
            int volume = Math.min(assemblies * 6, columns.size() * 2);
            int top = (int) Math.floor(center.y);
            for (int k = 0; k < volume; k++) {
                BlockPos column = columns.get(k % columns.size());
                BlockPos p = new BlockPos(column.getX(), top, column.getZ());
                int depth = 0;
                while (depth++ < radius + 4 && level.isLoaded(p.below()) && !level.isOutsideBuildHeight(p.below())
                       && level.getBlockState(p.below()).isAir()) {
                    p = p.below();
                }
                if (level.isLoaded(p) && level.getBlockState(p).isAir()) {
                    level.setBlock(p, corium, Block.UPDATE_ALL);
                }
            }
        }

        private double horizontal(BlockPos p) {
            double dx = p.getX() + 0.5 - center.x, dz = p.getZ() + 0.5 - center.z;
            return dx * dx + dz * dz;
        }

        private void prepareCrater() {
            int r = radius;
            BlockPos c = BlockPos.containing(center);
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        if (x * x + y * y + z * z <= r * r) {
                            crater.add(c.offset(x, y, z));
                        }
                    }
                }
            }
            //Du centre vers l'exterieur
            crater.sort((a, b) -> Double.compare(a.distToCenterSqr(center), b.distToCenterSqr(center)));
        }

        private void blast(BlockPos p) {
            if (!level.isLoaded(p) || level.isOutsideBuildHeight(p)) {
                return;
            }
            BlockState state = level.getBlockState(p);
            if (state.isAir() || state.getBlock() instanceof RadioactiveBlock || state.getDestroySpeed(level, p) < 0
                || state.getBlock().getExplosionResistance() >= 600) {
                return;
            }
            double d = Math.sqrt(p.distToCenterSqr(center));
            double inner = radius * 0.7;
            if (d > inner) {
                double chance = 1 - (d - inner) / (radius - inner);
                if (random.nextDouble() > chance * 0.9 + 0.05) {
                    return;
                }
            }
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            if (d > radius - 2 && random.nextInt(12) == 0 && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
                level.setBlock(p, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        /** Une colonne au hasard dans la zone contaminee : l'herbe meurt, les feuilles tombent. */
        private void wither() {
            double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * radius * (dispersal ? 3 : 2);
            int x = (int) Math.floor(center.x + Math.cos(a) * d), z = (int) Math.floor(center.z + Math.sin(a) * d);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                return;
            }
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z)).below();
            for (int i = 0; i < 8; i++, top = top.below()) {
                BlockState state = level.getBlockState(top);
                if (state.is(BlockTags.LEAVES)) {
                    if (random.nextInt(3) > 0) {
                        level.setBlock(top, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                } else if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
                    level.setBlock(top, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                    break;
                } else if (state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.FLOWERS)) {
                    level.setBlock(top, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                } else if (!state.isAir()) {
                    break;
                }
            }
        }
    }
}

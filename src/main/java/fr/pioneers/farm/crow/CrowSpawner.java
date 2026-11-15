package fr.pioneers.farm.crow;

import fr.pioneers.Pioneers;
import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.farm.crop.CropTile;
import fr.pioneers.farm.crop.FarmCropBlock;
import fr.pioneers.farm.device.DeviceSites;
import fr.pioneers.farm.device.PiFarmDevices;
import fr.pioneers.farm.device.ScarecrowBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Arrivee des corbeaux : toutes les {@link #PERIOD} ticks, pour chaque joueur du monde de surface, de jour et sans orage, s'il y a au moins
 * {@link #MIN_CROPS} cultures avancees (age >= 3) a 2 chunks, une chance de voir arriver une bande de 2 a 4 corbeaux, a ~28 blocs, en hauteur.
 * Reglage {@code farm.crows}.
 * <p>
 * 1.0.2-beta (Guillaume : « les corbeaux spawnent trop rarement, ils ne sont pas attires par les champs ») : toutes les 20 s au lieu de 30, et
 * la chance croit avec le champ ({@link #chance} : {@link #CHANCE} + {@link #PER_CROP} par culture, au plus {@link #MAX_CHANCE}) ; des champs
 * tout gardes par des epouvantails les attirent aussi (ils rodent au bord des zones : {@link Crow.EdgeGoal}) ; la bande arrive vers une
 * culture sans garde s'il y en a, sinon vers le champ.
 * <p>
 * 1.0.3-beta (Guillaume : « reduire LEGEREMENT le taux d'apparition ») : chaque terme de la chance x {@link #DAMPING} 0,7 (30 % de moins a
 * tout champ) : 0,21 + 0,0042 par culture, au plus 0,525 (avant 0,3 + 0,006, au plus 0,75) ; periode, seuil et plafond inchanges.
 * <p>
 * 1.0.3-beta (correctif, Guillaume : « toujours trop de corbeaux, ils spawnent en boucle au lieu d'avoir un quota : 1 corbeau toutes les 20
 * cases de plantation ») : QUOTA. Autour du joueur (32 blocs), {@link #quota} = cultures plantees (tout age) / {@link #PER_CROW} ; une bande
 * n'arrive que si les corbeaux deja la ({@link #COUNT_RADIUS} blocs autour du joueur, en route compris) sont sous le quota, et elle ne
 * fait que le completer (2 a 4, jamais au-dela). Moins de 20 plantations : aucun corbeau. Plafond absolu {@link #MAX_NEAR} : 6 (avant 8).
 * <p>
 * 1.0.5-beta (lot 4, spec §3 « population persistante, chance accrue dans une zone qui contient des champs ») : memes regles (periode,
 * seuil, quota, chance attenuee, reglage {@code farm.crows}), mais la bande ne nait plus de rien a 28 blocs : d'abord les corbeaux de la
 * zone a moins de {@link #RECRUIT_RADIUS} blocs du champ sont APPELES ({@link Crow#summon}) ; seul le manque arrive de loin, compte dans
 * le groupe des oiseaux de la zone (jamais au-dela de son plafond), a plus de 64 blocs de tout joueur et hors de sa vue, hors claim et loin
 * des villages ({@link #arrive}). Ces corbeaux restent ensuite dans la zone (persistants). Sans la faune persistante
 * ({@code fauna.enabled} faux) : bande a 28 blocs comme avant.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class CrowSpawner {

    /** MAX_NEAR : plafond absolu par champ (1.0.3-beta, Guillaume : « le quota doit passer a 6 » ; avant 8). */
    public static final int PERIOD = 400, MIN_CROPS = 6, MAX_NEAR = 6;
    /** 1.0.3-beta : un corbeau pour 20 cases plantees ; les corbeaux comptes a 80 blocs du joueur (bande en approche comprise). */
    public static final int PER_CROW = 20, COUNT_RADIUS = 80;
    /** 1.0.5-beta (lot 4) : corbeaux de la zone appeles vers le champ (blocs autour de la culture) ; arrivee de loin : 70 a 100 blocs. */
    public static final int RECRUIT_RADIUS = 128, ARRIVE_MIN = 70, ARRIVE_MAX = 100;

    /** Corbeaux admis autour d'un champ de {@code planted} cultures plantees (tout age). */
    public static int quota(int planted) {
        return Math.min(MAX_NEAR, planted / PER_CROW);
    }
    /** Reduction de la 1.0.3-beta, appliquee aux trois termes de la 1.0.2-beta (0,3 ; 0,006 ; 0,75). */
    public static final float DAMPING = 0.7F;
    public static final float CHANCE = 0.3F * DAMPING, PER_CROP = 0.006F * DAMPING, MAX_CHANCE = 0.75F * DAMPING;

    /** Chance d'une bande a chaque passage pour {@code crops} cultures avancees autour du joueur. */
    public static float chance(int crops) {
        return Math.min(MAX_CHANCE, CHANCE + PER_CROP * crops);
    }

    private CrowSpawner() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
            || level.getGameTime() % PERIOD != 0 || !PiCommonConfig.get(PiCommonConfig.FARM_CROWS, true)) {
            return;
        }
        if (!level.isDay() || level.isThundering()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            List<BlockPos> crops = new ArrayList<>(), open = new ArrayList<>();
            int[] planted = new int[1];
            scan(level, player.blockPosition(), 32, crops, open, planted);
            int room = quota(planted[0]) - level.getEntitiesOfClass(Crow.class, player.getBoundingBox().inflate(COUNT_RADIUS)).size();
            if (crops.size() < MIN_CROPS || room <= 0 || level.random.nextFloat() >= chance(crops.size())) {
                continue;
            }
            List<BlockPos> aim = open.isEmpty() ? crops : open;
            int flock = 2 + Math.min(2, crops.size() / 40 + level.random.nextInt(2));
            BlockPos field = aim.get(level.random.nextInt(aim.size()));
            if (!fr.pioneers.fauna.FaunaSettings.current().enabled()) {
                spawnFlock(level, field, Math.min(room, flock));
                continue;
            }
            int want = Math.min(room, flock), called = recruit(level, field, player, want);
            if (called < want) {
                arrive(level, field, want - called, level.random, null);
            }
        }
    }

    /** 1.0.5-beta (lot 4) : appelle vers le champ jusqu'a {@code n} corbeaux de la zone (pas deja autour du joueur) ; rend le nombre appele. */
    public static int recruit(ServerLevel level, BlockPos field, @Nullable ServerPlayer player, int n) {
        int called = 0;
        for (Crow c : level.getEntitiesOfClass(Crow.class, new net.minecraft.world.phys.AABB(field).inflate(RECRUIT_RADIUS))) {
            if (called >= n) {
                break;
            }
            if (player != null && c.distanceToSqr(player) < COUNT_RADIUS * COUNT_RADIUS) {
                continue;
            }
            if (c.summon(field)) {
                called++;
            }
        }
        return called;
    }

    /**
     * 1.0.5-beta (lot 4) : le manque arrive de loin, en vol, a {@link #ARRIVE_MIN}..{@link #ARRIVE_MAX} blocs du champ : place permise
     * par le repeuplement (joueurs, vue, claims, villages), groupe des oiseaux de la zone sous son plafond. Rend le nombre arrive.
     */
    public static int arrive(ServerLevel level, BlockPos field, int n, RandomSource r, @Nullable List<fr.pioneers.fauna.FaunaSpawner.Viewer> viewers) {
        fr.pioneers.fauna.FaunaSettings s = fr.pioneers.fauna.FaunaSettings.current();
        fr.pioneers.fauna.FaunaSpecies sp = fr.pioneers.fauna.FaunaRegistry.byId("pioneers:crow");
        if (sp == null) {
            return 0;
        }
        fr.pioneers.fauna.FaunaSpawner.Limits limits = viewers == null ? fr.pioneers.fauna.FaunaSpawner.Limits.repopulation(level, s)
              : new fr.pioneers.fauna.FaunaSpawner.Limits(viewers, s.playerDistance(), s.villageDistance(), true, 160);
        for (int t = 0; t < 16; t++) {
            double a = r.nextDouble() * Math.PI * 2, d = ARRIVE_MIN + r.nextDouble() * (ARRIVE_MAX - ARRIVE_MIN);
            int x = net.minecraft.util.Mth.floor(field.getX() + 0.5 + Math.cos(a) * d), z = net.minecraft.util.Mth.floor(field.getZ() + 0.5 + Math.sin(a) * d);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            long zone = fr.pioneers.fauna.FaunaRules.zoneKey(x, z);
            int birds = fr.pioneers.fauna.FaunaCounter.of(level).groups(zone)[fr.pioneers.fauna.FaunaGroup.BIRD.ordinal()];
            int room = Math.min(n, s.cap(fr.pioneers.fauna.FaunaGroup.BIRD) - birds);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 8 + r.nextInt(4);
            BlockPos at = new BlockPos(x, y, z);
            if (room <= 0 || !fr.pioneers.fauna.FaunaSpawner.allowed(level, at, sp, limits)) {
                continue;
            }
            int placed = 0;
            for (int i = 0; i < room; i++) {
                Crow crow = PiFarmDevices.CROW.get().create(level);
                if (crow == null) {
                    break;
                }
                crow.moveTo(x + 0.5 + r.nextGaussian() * 1.5, y + r.nextDouble() * 3, z + 0.5 + r.nextGaussian() * 1.5, r.nextFloat() * 360F, 0);
                if (level.noCollision(crow) && level.addFreshEntity(crow)) {
                    crow.summon(field);
                    if (placed++ == 0) {
                        crow.caw();
                    }
                }
            }
            return placed;
        }
        return 0;
    }

    private static void spawnFlock(ServerLevel level, BlockPos crop, int n) {
        RandomSource r = level.random;
        double a = r.nextDouble() * Math.PI * 2;
        double x = crop.getX() + 0.5 + Math.cos(a) * 28, z = crop.getZ() + 0.5 + Math.sin(a) * 28;
        BlockPos col = BlockPos.containing(x, 0, z);
        if (!level.isLoaded(col)) {
            return;
        }
        int y = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING, col.getX(), col.getZ()) + 10, crop.getY() + 12);
        for (int i = 0; i < n; i++) {
            Crow crow = PiFarmDevices.CROW.get().create(level);
            if (crow == null) {
                return;
            }
            crow.moveTo(x + r.nextGaussian() * 1.5, y + r.nextDouble() * 3, z + r.nextGaussian() * 1.5, r.nextFloat() * 360F, 0);
            if (level.noCollision(crow)) {
                level.addFreshEntity(crow);
                if (i == 0) {
                    crow.caw();
                }
            }
        }
    }

    /** Cultures avancees a {@code radius} blocs (tuiles des chunks charges) ; {@code open} : celles qu'aucun epouvantail ne protege. */
    static void scan(Level level, BlockPos center, int radius, List<BlockPos> crops, @Nullable List<BlockPos> open) {
        scan(level, center, radius, crops, open, null);
    }

    /** Idem ; {@code planted[0]} (si non nul) recoit le nombre de cultures plantees, tout age (base du quota). */
    static void scan(Level level, BlockPos center, int radius, List<BlockPos> crops, @Nullable List<BlockPos> open, @Nullable int[] planted) {
        int c0x = (center.getX() - radius) >> 4, c1x = (center.getX() + radius) >> 4, c0z = (center.getZ() - radius) >> 4,
              c1z = (center.getZ() + radius) >> 4;
        long r2 = (long) radius * radius;
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof CropTile)) {
                        continue;
                    }
                    BlockPos p = be.getBlockPos();
                    if (p.distSqr(center) > r2) {
                        continue;
                    }
                    BlockState s = be.getBlockState();
                    if (planted != null && s.getBlock() instanceof FarmCropBlock) {
                        planted[0]++;
                    }
                    if (s.getBlock() instanceof FarmCropBlock crop && crop.getAge(s) >= 3) {
                        crops.add(p);
                        if (open != null && !DeviceSites.scared(level, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, ScarecrowBlock.SCARE_RADIUS)) {
                            open.add(p);
                        }
                    }
                }
            }
        }
    }

    /** Une culture a piller pres de {@code center} (au hasard parmi les non protegees). */
    @Nullable
    static BlockPos findCrop(Level level, BlockPos center, int radius, RandomSource random) {
        List<BlockPos> crops = new ArrayList<>(), open = new ArrayList<>();
        scan(level, center, radius, crops, open);
        return open.isEmpty() ? null : open.get(random.nextInt(open.size()));
    }
}

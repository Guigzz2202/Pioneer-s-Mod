package fr.pioneers.ruins.sentinel;

import fr.pioneers.Pioneers;
import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.ruins.RuinApi;
import fr.pioneers.ruins.RuinSites;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 1.9 (MAJ RP, bloc 1) : facade des Sentinelles pour le reste du mod (ruines, generation, Console). Les entites sont dans ce paquet
 * ({@link SentinelEntity} et ses cinq roles), leurs chiffres dans {@code data/pioneers/sentinels/*.json} ({@link SentinelData}), le rendu
 * dans {@code client/ruins/sentinel}.
 */
public final class Sentinels {

    private Sentinels() {
    }

    /** Charge les registres des Sentinelles (appele par RuinsBootstrap). */
    public static void init(IEventBus modBus) {
        SentinelTypes.ENTITY_TYPES.register(modBus);
        SentinelTypes.LOOT_FUNCTIONS.register(modBus);
        modBus.addListener(SentinelTypes::attributes);
        MinecraftForge.EVENT_BUS.addListener(SentinelData::onReload);
    }

    /** Eveille les Sentinelles d'un site dans ce rayon (un bloc delabre casse, une alarme, un combat). Sans effet en Veille. */
    public static void wakeAround(ServerLevel level, BlockPos pos, double radius) {
        if (!PiCommonConfig.get(RpConfig.SENTINELS, true)) {
            return;
        }
        double r2 = radius * radius;
        for (SentinelEntity s : level.getEntitiesOfClass(SentinelEntity.class, new AABB(pos).inflate(radius),
              e -> e.isAlive() && e.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= r2)) {
            s.wakeUp(null);
        }
    }

    /** Veille : les Sentinelles du site se rendorment (celles qui combattaient regagnent d'abord leur poste). */
    public static void sleepSite(ServerLevel level, RuinSites.Site site) {
        for (SentinelEntity s : level.getEntitiesOfClass(SentinelEntity.class, site.box(SentinelData.rules().leash()),
              e -> e.isAlive() && e.site() == site.id)) {
            s.standbyNow();
        }
    }

    /**
     * Decision MAJ RP n° 21 : frapper une Sentinelle pendant la Veille rompt la Veille du site (alarme). Sinon, la Veille permettait de
     * tuer les gardiens sans risque, ce qui videait de sens le combat et la condition « site securise » de la restauration.
     */
    public static void breakStandby(ServerLevel level, RuinSites.Site site, BlockPos near) {
        site.standbyUntil = -1;
        RuinSites.get(level).changed();
        for (SentinelEntity e : level.getEntitiesOfClass(SentinelEntity.class, site.box(SentinelData.rules().leash()),
              x -> x.isAlive() && x.site() == site.id)) {
            e.standby = false;
        }
        level.playSound(null, near, fr.pioneers.core.profile.RpSounds.ALARM.get(), net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 1.0F);
        wakeSite(level, site.id, near);
    }

    /** Un combat eveille tout le site (boite du site + marge) ; sans site connu, les Sentinelles du meme site autour de ce point. */
    public static void wakeSite(ServerLevel level, long siteId, BlockPos near) {
        SentinelRules r = SentinelData.rules();
        RuinSites.Site s = siteId != 0 ? RuinApi.site(level, siteId) : null;
        AABB box;
        if (s != null) {
            if (s.standby(level.getGameTime())) {
                return;
            }
            box = s.box(r.common("site_wake_margin", 8));
        } else {
            box = new AABB(siteId != 0 ? BlockPos.of(siteId) : near).inflate(r.common("orphan_wake_radius", 24));
        }
        for (SentinelEntity e : level.getEntitiesOfClass(SentinelEntity.class, box, x -> x.isAlive() && x.site() == siteId)) {
            e.wakeUp(null);
        }
    }

    /** Sentinelles vivantes et chargees d'un site (restauration : le site est securise quand il n'en reste aucune). */
    public static int alive(ServerLevel level, RuinSites.Site site) {
        return level.getEntitiesOfClass(SentinelEntity.class, site.box(SentinelData.rules().leash()), e -> e.isAlive() && e.site() == site.id).size();
    }

    /**
     * Pose les Sentinelles endormies d'une ruine a la generation (nombre et types selon le tier : MKI 1 a 2, MKII 3 a 5, MKIII 5 a 8 + un
     * Contremaitre ; chiffres dans {@code data/pioneers/sentinels/spawn.json}). {@code posts} = cases libres au sol du site, deja melangees.
     */
    public static void spawnForSite(WorldGenLevel level, long site, int tier, List<BlockPos> posts, RandomSource random) {
        if (!PiCommonConfig.get(RpConfig.SENTINELS, true) || posts.isEmpty()) {
            return;
        }
        SentinelRules rules = SentinelData.rules();
        ServerLevel server = level.getLevel();
        Set<BlockPos> used = new HashSet<>();
        for (SentinelRole role : SentinelPlan.compose(rules, tier, random::nextInt)) {
            BlockPos post = pick(level, role, posts, used, rules);
            if (post == null) {
                continue;
            }
            EntityType<? extends SentinelEntity> type = SentinelTypes.type(role);
            SentinelEntity e = type.create(server);
            if (e == null) {
                continue;
            }
            e.setup(tier, site, post, random.nextFloat() * 360.0F);
            level.addFreshEntity(e);
        }
    }

    /** Premier poste libre ou le role tient (Patrouilleur : souleve de {@code patroller_lift} blocs). */
    private static BlockPos pick(WorldGenLevel level, SentinelRole role, List<BlockPos> posts, Set<BlockPos> used, SentinelRules rules) {
        BlockPos fallback = null;
        for (BlockPos p : posts) {
            if (used.contains(p)) {
                continue;
            }
            BlockPos at = p;
            if (role == SentinelRole.PATROLLER) {
                int lift = (int) Math.round(rules.patrollerLift());
                for (int k = lift; k >= 1; k--) {
                    if (free(level, role, p.above(k))) {
                        at = p.above(k);
                        break;
                    }
                }
            }
            if (free(level, role, at)) {
                used.add(p);
                return at;
            }
            if (fallback == null && role != SentinelRole.FOREMAN) {
                fallback = p;
            }
        }
        if (fallback != null) {
            used.add(fallback);
        }
        return fallback;
    }

    private static boolean free(WorldGenLevel level, SentinelRole role, BlockPos at) {
        double hw = role.width / 2.0;
        AABB box = new AABB(at.getX() + 0.5 - hw, at.getY() + 0.01, at.getZ() + 0.5 - hw, at.getX() + 0.5 + hw, at.getY() + role.height,
              at.getZ() + 0.5 + hw);
        try {
            return level.noCollision(box);
        } catch (RuntimeException ex) {
            //Case au bord de la region de generation : on ne peut pas verifier
            Pioneers.LOGGER.debug("Sentinelle : poste non verifie en {} ({})", at, ex.getMessage());
            return true;
        }
    }
}

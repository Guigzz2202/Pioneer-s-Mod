package fr.pioneers.fauna;

import fr.pioneers.clan.ClaimIndex;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.village.VillageData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 4) : mise en place d'une bande de la faune dans le monde (generation d'un troncon, repeuplement d'une zone, arrivee des
 * corbeaux). Lieu : colonne au hasard dans l'aire, hauteur selon le lieu de l'espece (sol, eau entre le fond et la surface, eau des
 * grottes sous la couche 50), regles d'apparition du TYPE ({@code SpawnPlacements}, celles du jeu : herbe et lumiere des animaux, eau des
 * poissons, plafonds locaux des poissons du mod...), regle propre de l'espece, place libre. Contraintes du repeuplement ({@link Limits}) :
 * a plus de {@link FaunaSettings#playerDistance} blocs de tout joueur et hors de sa vue, hors claim, loin des villages (sauf especes
 * « pres des constructions »).
 */
public final class FaunaSpawner {

    private FaunaSpawner() {
    }

    /** Vrai pendant nos propres appels aux regles d'apparition : {@link FaunaEvents} ne les refuse pas. */
    static boolean ours;

    /** Aire rectangulaire (blocs, bornes comprises). */
    public record Area(int x0, int z0, int x1, int z1) {

        public static Area zone(long key) {
            int x = FaunaRules.origin(FaunaRules.zoneX(key)), z = FaunaRules.origin(FaunaRules.zoneZ(key));
            return new Area(x, z, x + FaunaRules.ZONE_SIZE - 1, z + FaunaRules.ZONE_SIZE - 1);
        }

        public static Area chunk(int cx, int cz) {
            return new Area(cx << 4, cz << 4, (cx << 4) + 15, (cz << 4) + 15);
        }

        public int randomX(RandomSource r) {
            return x0 + r.nextInt(x1 - x0 + 1);
        }

        public int randomZ(RandomSource r) {
            return z0 + r.nextInt(z1 - z0 + 1);
        }

        public boolean contains(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }
    }

    /** Un regard : yeux et direction (joueurs ; GameTests : regard factice). */
    public record Viewer(Vec3 eye, Vec3 look) {

        public static List<Viewer> of(ServerLevel level) {
            List<Viewer> out = new ArrayList<>();
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator()) {
                    out.add(new Viewer(p.getEyePosition(), p.getLookAngle()));
                }
            }
            return out;
        }
    }

    /** Contraintes d'un lieu de repeuplement ; {@link #NONE} a la generation d'un troncon (comme le vanilla). */
    public record Limits(List<Viewer> viewers, int playerDistance, int villageDistance, boolean claims, int viewRange) {

        public static final Limits NONE = new Limits(List.of(), 0, -1, false, 0);

        public static Limits repopulation(ServerLevel level, FaunaSettings s) {
            return new Limits(Viewer.of(level), s.playerDistance(), s.villageDistance(), true, FaunaSpawner.viewRange(level));
        }
    }

    /** Portee de vue des joueurs (distance de vue du serveur, en blocs). */
    static int viewRange(ServerLevel level) {
        return level.getServer() == null ? 160 : Math.max(64, level.getServer().getPlayerList().getViewDistance() * 16 + 16);
    }

    // ------------------------------------------------------------------ lieux

    /** Le lieu convient-il au repeuplement de cette espece (joueurs, vue, claims, villages) ? */
    public static boolean allowed(ServerLevel level, BlockPos pos, FaunaSpecies s, Limits l) {
        double min2 = (double) l.playerDistance() * l.playerDistance();
        Vec3 c = Vec3.atBottomCenterOf(pos).add(0, 0.8, 0);
        for (Viewer v : l.viewers()) {
            if (v.eye().distanceToSqr(c) < min2 || visible(level, v, c, l.viewRange())) {
                return false;
            }
        }
        if (s.nearBuildings) {
            return true;
        }
        if (l.claims() && ClaimIndex.owner(ClanApi.dim(level), ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4)) != null) {
            return false;
        }
        return l.villageDistance() < 0 || !nearVillage(level, pos, l.villageDistance());
    }

    /** Vu par ce regard : a portee, dans le cone de {@link FaunaRules#VIEW_HALF_ANGLE} degres, et rien de plein entre les deux. */
    public static boolean visible(ServerLevel level, Viewer v, Vec3 target, int range) {
        Vec3 d = target.subtract(v.eye());
        double len = d.length();
        if (len > range || len < 1e-6) {
            return len <= range;
        }
        double cos = d.dot(v.look()) / (len * Math.max(1e-6, v.look().length()));
        if (cos < Math.cos(Math.toRadians(FaunaRules.VIEW_HALF_ANGLE))) {
            return false;
        }
        BlockHitResult hit = level.clip(new ClipContext(v.eye(), target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, null));
        return hit.getType() == HitResult.Type.MISS;
    }

    /** Un village a moins de {@code radius} blocs : un lieu de village (cloche, lits, postes de travail) ou un village connu du mod. */
    public static boolean nearVillage(ServerLevel level, BlockPos pos, int radius) {
        if (radius <= 0) {
            return false;
        }
        if (VillageData.get(level).nearest(pos, radius) != null) {
            return true;
        }
        return level.getPoiManager().getCountInRange(h -> h.is(PoiTypeTags.VILLAGE), pos, radius, PoiManager.Occupancy.ANY) > 0;
    }

    /** Hauteur de la bande dans la colonne (x, z) selon le lieu de l'espece ; null si la colonne ne convient pas. */
    @Nullable
    public static BlockPos spot(ServerLevel level, FaunaSpecies.Where where, EntityType<?> type, int x, int z, RandomSource r) {
        switch (where) {
            case LAND -> {
                int y = level.getHeight(SpawnPlacements.getHeightmapType(type), x, z);
                BlockPos p = new BlockPos(x, y, z);
                return level.getFluidState(p).isEmpty() && level.getFluidState(p.below()).isEmpty() ? p : null;
            }
            case WATER -> {
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
                if (top - floor < 2 || !level.getFluidState(new BlockPos(x, top - 1, z)).is(FluidTags.WATER)) {
                    return null;
                }
                BlockPos p = new BlockPos(x, floor + r.nextInt(top - floor - 1), z);
                return level.getFluidState(p).is(FluidTags.WATER) ? p : null;
            }
            default -> {
                int lo = level.getMinBuildHeight() + 4, hi = Math.min(fr.pioneers.season.SeasonClimate.UNDERGROUND_Y,
                      level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 4);
                for (int i = 0; i < 6 && hi > lo; i++) {
                    BlockPos p = new BlockPos(x, lo + r.nextInt(hi - lo), z);
                    if (level.getFluidState(p).is(FluidTags.WATER) && !level.canSeeSky(p)) {
                        return p;
                    }
                }
                return null;
            }
        }
    }

    /** Regles du type (celles du jeu) + regle de l'espece + place libre. */
    public static boolean fits(ServerLevel level, FaunaPools.Entry e, BlockPos pos, MobSpawnType reason, RandomSource r) {
        EntityType<?> type = e.type();
        boolean ok;
        ours = true;
        try {
            ok = SpawnPlacements.checkSpawnRules(type, level, reason, pos, r)
                 && NaturalSpawner.isSpawnPositionOk(SpawnPlacements.getPlacementType(type), level, pos, type);
        } finally {
            ours = false;
        }
        return ok && (e.species().rule == null || e.species().rule.test(level, pos))
               && level.noCollision(type.getAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
    }

    // ------------------------------------------------------------------ bandes

    /** Une bande d'une espece choisie (generation d'un troncon) : rend le nombre de betes posees. */
    public static int spawnBand(ServerLevel level, FaunaPools.Entry e, Area area, BlockPos anchor, int n, Limits limits, MobSpawnType reason,
          RandomSource r, @Nullable List<Entity> out) {
        return spawnBand(level, e, area, anchor, n, limits, reason, r, out, null);
    }

    /**
     * 1.0.5-beta (lot 10) : meme bande, chaque bete preparee par {@code prepare} AVANT son entree dans le monde (troupeau : marque posee
     * avant que {@link FaunaCounter#join} ne la compte).
     */
    public static int spawnBand(ServerLevel level, FaunaPools.Entry e, Area area, BlockPos anchor, int n, Limits limits, MobSpawnType reason,
          RandomSource r, @Nullable List<Entity> out, @Nullable java.util.function.Consumer<Entity> prepare) {
        int placed = 0;
        SpawnGroupData group = null;
        for (int i = 0; i < n * 4 && placed < n; i++) {
            BlockPos p = anchor;
            if (i > 0) {
                int x = Mth.clamp(anchor.getX() + r.nextInt(9) - 4, area.x0(), area.x1()), z = Mth.clamp(anchor.getZ() + r.nextInt(9) - 4, area.z0(),
                      area.z1());
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                p = e.where() == FaunaSpecies.Where.LAND ? spot(level, e.where(), e.type(), x, z, r)
                                                         : new BlockPos(x, anchor.getY() + r.nextInt(3) - 1, z);
                if (p == null || !allowed(level, p, e.species(), limits)) {
                    continue;
                }
            }
            if (!fits(level, e, p, reason, r)) {
                continue;
            }
            Entity ent = e.type().create(level);
            if (ent == null) {
                return placed;
            }
            ent.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, r.nextFloat() * 360F, 0);
            if (ent instanceof Mob mob) {
                if (!mob.checkSpawnObstruction(level)) {
                    ent.discard();
                    continue;
                }
                group = ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(p), reason, group, null);
                if (mob.isSpawnCancelled()) {
                    ent.discard();
                    continue;
                }
            }
            if (prepare != null) {
                prepare.accept(ent);
            }
            if (level.addFreshEntity(ent)) {
                placed++;
                if (out != null) {
                    out.add(ent);
                }
            }
        }
        return placed;
    }

    /**
     * Repeuple un groupe dans une aire : colonnes au hasard ({@link FaunaRules#SPAWN_TRIES}), espece tiree dans le biome de la colonne
     * (poids x saison ; corbeau x {@link FaunaRules#CROW_FIELD_BOOST} si {@code fields}), lieu permis, bande de {@link FaunaRules#groupSize}.
     * Rend les betes posees ({@code out} : la liste, si non nulle).
     */
    public static int repopulate(ServerLevel level, FaunaGroup g, Area area, Limits limits, int season, boolean fields, int count, int target,
          int cap, RandomSource r, @Nullable List<Entity> out) {
        if (count >= cap) {
            return 0;
        }
        for (int t = 0; t < FaunaRules.SPAWN_TRIES; t++) {
            int x = area.randomX(r), z = area.randomZ(r);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            Holder<Biome> biome = level.getBiome(new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z));
            List<FaunaPools.Entry> pool = FaunaPools.of(biome, g);
            FaunaPools.Entry e = pick(pool, season, fields, r);
            if (e == null) {
                continue;
            }
            BlockPos p = spot(level, e.where(), e.type(), x, z, r);
            if (p == null || !allowed(level, p, e.species(), limits) || !fits(level, e, p, MobSpawnType.NATURAL, r)) {
                continue;
            }
            int n = FaunaRules.groupSize(e.min(), e.max(), count, target, cap, r.nextDouble());
            if (e.species().cap > 0) {
                n = Math.min(n, e.species().cap - FaunaCounter.of(level).species(FaunaRules.zoneKey(x, z), e.species()));
            }
            if (n <= 0) {
                continue;
            }
            int placed = spawnBand(level, e, area, p, n, limits, MobSpawnType.NATURAL, r, out);
            if (placed > 0) {
                return placed;
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ reproduction sauvage lente (1.0.5-beta, lot 6)

    /**
     * Fait naitre jusqu'a {@code n} petits du groupe {@code g} dans la zone (parents dans l'aire), chacun pres d'un ADULTE de son espece (bete suivie par le compteur,
     * de la faune sauvage : ni apprivoisee, ni nommee, ni en laisse, ni persistante seulement) dont la place est permise : a plus de
     * {@code limits.playerDistance()} blocs de tout joueur et hors de sa vue, JAMAIS dans un claim (meme les especes « pres des
     * constructions »), sous le plafond propre de l'espece. Le petit vient du vrai {@code getBreedOffspring} de l'espece (race, sexe...).
     * Rend les petits poses ({@code out} : la liste, si non nulle).
     */
    public static int breed(ServerLevel level, long zone, Area area, FaunaGroup g, int n, Limits limits, RandomSource r,
          @Nullable List<Entity> out) {
        List<net.minecraft.world.entity.animal.Animal> parents = new ArrayList<>();
        for (Entity e : FaunaCounter.of(level).entities(zone)) {
            if (e instanceof net.minecraft.world.entity.animal.Animal a && !a.isBaby() && a.isAlive() && !a.hasCustomName() && !a.isLeashed()
                && !(a instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame())
                && !(a instanceof net.minecraft.world.entity.animal.horse.AbstractHorse h && h.isTamed()) && area.contains(a.getBlockX(), a.getBlockZ())
                && !FaunaCounter.herd(a)) {   //1.0.5-beta (lot 10) : les betes d'un troupeau ne se reproduisent pas (pas d'inflation)
                FaunaSpecies s = FaunaRegistry.of(e);
                if (s != null && s.group == g && !s.persistOnly) {
                    parents.add(a);
                }
            }
        }
        java.util.Collections.shuffle(parents, new java.util.Random(r.nextLong()));
        int born = 0;
        for (net.minecraft.world.entity.animal.Animal p : parents) {
            if (born >= n) {
                break;
            }
            FaunaSpecies s = FaunaRegistry.of(p);
            BlockPos pos = p.blockPosition();
            if (s == null || !birthAllowed(level, pos, limits)
                || s.cap > 0 && FaunaCounter.of(level).species(zone, s) >= s.cap) {
                continue;
            }
            net.minecraft.world.entity.AgeableMob baby = p.getBreedOffspring(level, p);
            if (baby == null) {
                continue;
            }
            baby.setAge(net.minecraft.world.entity.AgeableMob.BABY_START_AGE);
            double x = p.getX() + (r.nextDouble() - 0.5) * 1.5, z = p.getZ() + (r.nextDouble() - 0.5) * 1.5;
            baby.moveTo(x, p.getY(), z, r.nextFloat() * 360F, 0F);
            if (!level.noCollision(baby)) {
                baby.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0F);
            }
            if (level.addFreshEntity(baby)) {
                born++;
                if (out != null) {
                    out.add(baby);
                }
            }
        }
        return born;
    }

    /** Lieu d'une naissance sauvage : loin de tout regard ({@code playerDistance}, cone de vue), jamais dans un claim. */
    public static boolean birthAllowed(ServerLevel level, BlockPos pos, Limits l) {
        double min2 = (double) l.playerDistance() * l.playerDistance();
        Vec3 c = Vec3.atBottomCenterOf(pos).add(0, 0.8, 0);
        for (Viewer v : l.viewers()) {
            if (v.eye().distanceToSqr(c) < min2 || visible(level, v, c, l.viewRange())) {
                return false;
            }
        }
        return ClaimIndex.owner(ClanApi.dim(level), ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4)) == null;
    }

    /** Tirage d'une espece d'un lieu (poids x saison ; corbeau favorise pres des champs). */
    @Nullable
    public static FaunaPools.Entry pick(List<FaunaPools.Entry> pool, int season, boolean fields, RandomSource r) {
        if (pool.isEmpty()) {
            return null;
        }
        double[] w = new double[pool.size()];
        for (int i = 0; i < w.length; i++) {
            FaunaPools.Entry e = pool.get(i);
            w[i] = e.weight() * e.species().season(season) * (fields && e.species().id.equals("pioneers:crow") ? FaunaRules.CROW_FIELD_BOOST : 1);
            if (e.species().id.equals("pioneers:crow") && !crowsOn()) {
                w[i] = 0;
            }
        }
        int i = FaunaRules.pick(w, r.nextDouble());
        return i < 0 ? null : pool.get(i);
    }

    /** Reglage {@code farm.crows} : faux, plus aucun corbeau n'apparait (ni a la generation, ni au repeuplement, ni pres des champs). */
    static boolean crowsOn() {
        return fr.pioneers.common.PiCommonConfig.get(fr.pioneers.common.PiCommonConfig.FARM_CROWS, true);
    }
}

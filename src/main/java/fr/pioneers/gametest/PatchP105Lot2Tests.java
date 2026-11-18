package fr.pioneers.gametest;

import fr.pioneers.world.season.SeasonData;
import fr.pioneers.world.season.SeasonEvents;
import fr.pioneers.world.season.SeasonPrecipitation;
import fr.pioneers.world.season.Seasons.Season;
import fr.pioneers.world.weather.Precipitation;
import fr.pioneers.world.weather.PrecipitationType;
import fr.pioneers.world.weather.Precipitations;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 2 (GameTests ; specification « Le monde vivant » §1.3, demande de Guillaume « une vraie neige qui ne genere pas de
 * couche ») : dans le vrai serveur (mixins appliques par Forge) :
 * <ul>
 *     <li>les saisons rendent une VRAIE neige sans couche ni gel en plaine l'hiver, rien en ete (pluie vanilla), rien dans un biome enneige ;</li>
 *     <li>{@code isRainingAt} suit l'API : sous la pluie d'un fournisseur il pleut, sous sa neige non (feux, joueurs, eclairs) ;</li>
 *     <li>le vrai tick de meteo des troncons ({@code ServerLevel#tickChunk}, sous une pluie continue) remplit les chaudrons de neige poudreuse
 *     et ne pose ni couche ni glace dans la zone d'un fournisseur « neige sans couche » ;</li>
 *     <li>les reglages d'un fournisseur factice (comme celui d'un futur astre) sont tenus : couche et gel seulement s'il les demande.</li>
 * </ul>
 * Un fournisseur de test (priorite maximale) ne repond que dans la boite du test ; le temps, le calendrier et le fournisseur sont remis au
 * nettoyage.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot2Tests {

    private PatchP105Lot2Tests() {
    }

    /** Fournisseur de test : repond {@code answer} dans la boite du test, rien ailleurs ; retire au nettoyage. */
    private static AtomicReference<Precipitation> provider(Gt.Scope s, GameTestHelper h) {
        AtomicReference<Precipitation> answer = new AtomicReference<>();
        BlockPos a = h.absolutePos(BlockPos.ZERO), b = h.absolutePos(new BlockPos(Gt.SMALL_SIZE - 1, 0, Gt.SMALL_SIZE - 1));
        AABB box = new AABB(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()) + 1, a.getY() + 1,
              Math.max(a.getZ(), b.getZ()) + 1);
        Precipitations.Entry e = Precipitations.register(Level.OVERWORLD, Integer.MAX_VALUE,
              (level, pos, biome) -> box.contains(pos.getX() + 0.5, box.minY + 0.5, pos.getZ() + 0.5) ? answer.get() : null);
        s.onCleanup(() -> Precipitations.unregister(e));
        return answer;
    }

    /** Pluie continue sur le monde le temps du test ; temps d'avant remis au nettoyage. */
    private static void rain(Gt.Scope s, ServerLevel level) {
        ServerLevelData data = (ServerLevelData) level.getLevelData();
        boolean raining = data.isRaining(), thundering = data.isThundering();
        int clear = data.getClearWeatherTime(), rainTime = data.getRainTime(), thunderTime = data.getThunderTime();
        level.setWeatherParameters(0, 24_000, true, false);
        //1.0.6 : isRaining lit le niveau de pluie, qui monte de 0,01 par tick : sans lui, il ne « pleut » qu'apres 20 ticks
        float rainLevel = level.getRainLevel(1.0F);
        level.setRainLevel(1.0F);
        s.onCleanup(() -> {
            level.setRainLevel(rainLevel);
            data.setClearWeatherTime(clear);
            data.setRaining(raining);
            data.setRainTime(rainTime);
            data.setThundering(thundering);
            data.setThunderTime(thunderTime);
        });
    }

    private static void keepCalendar(Gt.Scope s) {
        long offset = SeasonData.get(s.server.overworld()).offset();
        s.onCleanup(() -> SeasonEvents.setOffset(s.server, offset));
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_2_snow", timeoutTicks = 40)
    public static void p105_2_neige_serveur(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        keepCalendar(s);
        ServerLevel level = s.level;
        BlockPos pos = h.absolutePos(new BlockPos(4, 2, 4));
        Holder<Biome> plains = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        Holder<Biome> snowy = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.SNOWY_PLAINS);
        Gt.check(Precipitations.has(Level.OVERWORLD), "les saisons sont inscrites pour l'Overworld");
        SeasonEvents.set(s.server, Season.WINTER, 5);
        Precipitation p = SeasonPrecipitation.INSTANCE.at(level, pos, plains);
        Gt.check(p != null && p.type() == PrecipitationType.SNOW, "plaine en hiver : neige (cote serveur)");
        Gt.check(!p.leavesLayer() && !p.freezesWater(), "neige d'hiver : ni couche ni gel");
        Gt.equal(Biome.Precipitation.SNOW, p.type().vanilla(), "le jeu la voit comme la neige vanilla");
        Gt.check(SeasonPrecipitation.INSTANCE.at(level, pos, snowy) == null, "biome enneige vanilla : inchange (vanilla)");
        SeasonEvents.set(s.server, Season.SUMMER, 5);
        Gt.check(SeasonPrecipitation.INSTANCE.at(level, pos, plains) == null, "plaine en ete : pluie vanilla");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_2_raining", timeoutTicks = 40)
    public static void p105_2_pluie_ou_neige(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = s.level;
        rain(s, level);
        AtomicReference<Precipitation> answer = provider(s, h);
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, h.absolutePos(new BlockPos(4, 0, 4)));
        Gt.check(level.canSeeSky(top), "case a ciel ouvert");
        answer.set(Precipitation.RAIN);
        Gt.check(level.isRainingAt(top), "sous la pluie du fournisseur : il pleut ici");
        answer.set(Precipitation.SNOW_NO_LAYER);
        Gt.check(!level.isRainingAt(top), "sous sa neige : il ne pleut pas (feux, joueurs, eclairs)");
        Gt.equal(PrecipitationType.SNOW, Precipitations.typeAt(level, top), "sorte de precipitation : neige");
        answer.set(Precipitation.NONE);
        Gt.check(!level.isRainingAt(top), "rien ne tombe");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_2_tick", timeoutTicks = 100)
    public static void p105_2_tick_meteo(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = s.level;
        rain(s, level);
        AtomicReference<Precipitation> answer = provider(s, h);
        answer.set(Precipitation.SNOW_NO_LAYER);
        //sol de gazon, 16 chaudrons et une mare dans la boite du test
        BlockPos o = h.absolutePos(new BlockPos(0, 1, 0));
        for (int x = 0; x < Gt.SMALL_SIZE; x++) {
            for (int z = 0; z < Gt.SMALL_SIZE; z++) {
                BlockPos g = o.offset(x, 0, z);
                level.setBlock(g, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                level.setBlock(g.above(), (x % 2 == 0 && z % 2 == 0 && x < 8 && z < 8) ? Blocks.CAULDRON.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            }
        }
        for (int x = 2; x < 5; x++) {
            level.setBlock(o.offset(x, 0, 7), Blocks.WATER.defaultBlockState(), 3);
            level.setBlock(o.offset(x, 1, 7), Blocks.AIR.defaultBlockState(), 3);
        }
        //tous les troncons sous la boite (elle peut chevaucher une limite de troncon)
        java.util.Set<LevelChunk> chunks = new java.util.LinkedHashSet<>();
        for (int x = 0; x < Gt.SMALL_SIZE; x += Gt.SMALL_SIZE - 1) {
            for (int z = 0; z < Gt.SMALL_SIZE; z += Gt.SMALL_SIZE - 1) {
                chunks.add(level.getChunkAt(o.offset(x, 0, z)));
            }
        }
        for (int i = 0; i < 120_000; i++) {
            for (LevelChunk chunk : chunks) {
                level.tickChunk(chunk, 0);
            }
        }
        int powder = 0, layers = 0, ice = 0;
        for (int x = 0; x < Gt.SMALL_SIZE; x++) {
            for (int z = 0; z < Gt.SMALL_SIZE; z++) {
                BlockPos g = o.offset(x, 0, z);
                powder += level.getBlockState(g.above()).is(Blocks.POWDER_SNOW_CAULDRON) ? 1 : 0;
                layers += level.getBlockState(g.above()).is(Blocks.SNOW) ? 1 : 0;
                ice += level.getBlockState(g).is(Blocks.ICE) ? 1 : 0;
                Gt.check(!level.getBlockState(g.above()).is(Blocks.WATER_CAULDRON), "aucun chaudron d'eau sous la neige");
            }
        }
        Gt.check(powder > 0, "vrai tick de meteo : chaudrons remplis de neige poudreuse (" + powder + ")");
        Gt.equal(0L, layers, "aucune couche de neige");
        Gt.equal(0L, ice, "aucune glace : la mare reste en eau");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_2_flags", timeoutTicks = 40)
    public static void p105_2_reglages_du_fournisseur(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = s.level;
        AtomicReference<Precipitation> answer = provider(s, h);
        BlockPos ground = h.absolutePos(new BlockPos(4, 1, 4)), air = ground.above(), water = h.absolutePos(new BlockPos(6, 1, 6));
        level.setBlock(ground, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        level.setBlock(air, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(water, Blocks.WATER.defaultBlockState(), 3);
        Biome biome = level.getBiome(air).value();
        answer.set(Precipitation.SNOW);
        Gt.check(biome.shouldSnow(level, air), "fournisseur « neige avec couche » : la couche tient (meme dans un biome tempere)");
        Gt.check(biome.shouldFreeze(level, water), "fournisseur « gele l'eau » : la glace se forme");
        answer.set(Precipitation.SNOW_NO_LAYER);
        Gt.check(!biome.shouldSnow(level, air), "fournisseur « neige sans couche » : aucune couche");
        Gt.check(!biome.shouldFreeze(level, water), "fournisseur « neige sans couche » : aucune glace");
        answer.set(new Precipitation(PrecipitationType.RAIN, false, true, 0.5F));
        Gt.check(!biome.shouldSnow(level, air) && biome.shouldFreeze(level, water), "pluie verglacante : pas de couche mais de la glace");
        s.done();
    }
}

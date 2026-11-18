package fr.pioneers.farm.crop;

import fr.pioneers.Pioneers;
import fr.pioneers.farm.greenhouse.GreenhouseRegistry;
import fr.pioneers.world.season.BiomeClimates;
import fr.pioneers.world.season.SeasonClimate;
import fr.pioneers.world.season.Seasons;
import fr.pioneers.space.SolarSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Climat d'une culture. Temperature et humidite du biome (temperature abaissee en altitude, comme la neige vanilla) ; l'irrigation (terre
 * humide) ajoute {@link #IRRIGATION} d'humidite. Dans la plage ideale : pousse normale ; dans la marge de tolerance (Resistance) : pousse
 * ralentie ; au-dela : rien ne pousse et la plante fane (apres un delai de grace, 1.0.5). Une culture SOUS SERRE est toujours en climat
 * ideal. 1.0.5-beta (lot 3, specification §2.2) : la serre est un MULTIBLOC (piece scellee de blocs de serre, regulateur) : registre des
 * serres formees {@link GreenhouseRegistry} consulte sans recalcul. L'ancienne regle (premier bloc au-dessus, sur 12 blocs, du tag
 * {@code pioneers:greenhouse_glass}, {@link #legacyGlass}) ne vaut plus que sur les AUTRES ASTRES que la Terre ({@link #legacyRule}) : les
 * serres spatiales sont inchangees, et la, seule la serre permet la culture.
 * <p>
 * 1.0.5-beta (lot 1, specification « Le monde vivant » §1.4 et §2.1 : le choix « climat par biome, pas de saisons » est leve) : dans
 * l'Overworld, la temperature et l'humidite recoivent le decalage de la SAISON ({@link SeasonClimate}, par famille de biome, fondu entre
 * deux saisons) ; sans acces au ciel et sous la couche 50, climat souterrain fixe (8 °C, humidite 0,5) sans saison. Tout passe par
 * {@link #place} (lecture du lieu, une fois) puis {@link #status} (pur, a une date donnee) : la fiche au regard en tire la prevision
 * ({@link #outlook}). Les autres dimensions n'ont ni saison ni regle souterraine.
 */
public final class Climate {

    public static final TagKey<Block> GREENHOUSE_GLASS = BlockTags.create(Pioneers.rl("greenhouse_glass"));
    public static final float IRRIGATION = 0.3F;
    public static final int GREENHOUSE_HEIGHT = 12;
    /**
     * 1.0.5 : temperature la plus chaude du vanilla (desert, savane, badlands). Une plage de culture qui monte jusque-la n'a pas de plafond :
     * l'ete y ajoute des degres (table de la specification) sans tuer les plantes de chaleur, qui etaient faites pour le haut de l'echelle.
     */
    public static final float OPEN_TOP = 2.0F;

    private Climate() {
    }

    public enum Status {
        IDEAL(1F), TOLERABLE(0.45F), HOSTILE(0F), GREENHOUSE(1F);

        public final float factor;

        Status(float factor) {
            this.factor = factor;
        }

        public boolean grows() {
            return factor > 0;
        }
    }

    /** Temperature de jeu au lieu, aujourd'hui (saison comprise dans l'Overworld). */
    public static float temperature(LevelReader level, BlockPos pos) {
        return place(level, pos).temperature(level instanceof Level l ? Seasons.now(l) : null);
    }

    /** Humidite au lieu (0 a 1), aujourd'hui (saison comprise dans l'Overworld). */
    public static float humidity(LevelReader level, BlockPos pos) {
        return place(level, pos).humidity(level instanceof Level l ? Seasons.now(l) : null);
    }

    /** Temperature de base du biome (altitude comprise, sans saison) : vanilla, inchangee. */
    public static float biomeTemperature(LevelReader level, BlockPos pos) {
        float t = level.getBiome(pos).value().getModifiedClimateSettings().temperature();
        if (pos.getY() > 80) {
            t -= (pos.getY() - 80) * 0.05F / 40F;
        }
        return t;
    }

    /** 1.0.5 : sans acces au ciel et sous la couche 50, dans une dimension a saisons. */
    public static boolean underground(LevelReader level, BlockPos pos) {
        return level instanceof Level l && Seasons.hasSeasons(l) && pos.getY() < SeasonClimate.UNDERGROUND_Y && !l.canSeeSky(pos);
    }

    /** 1.0.5 : ce qui fixe le climat du lieu a toute date (biome lu une seule fois). */
    public static SeasonClimate.Place place(LevelReader level, BlockPos pos) {
        if (underground(level, pos)) {
            return SeasonClimate.Place.underground();
        }
        Holder<Biome> biome = level.getBiome(pos);
        float h = biome.value().getModifiedClimateSettings().downfall();
        boolean seasonal = level instanceof Level l && Seasons.hasSeasons(l);
        BiomeClimates.Info info = seasonal ? BiomeClimates.of(biome) : new BiomeClimates.Info(SeasonClimate.Family.TEMPERATE, false);
        return new SeasonClimate.Place(biomeTemperature(level, pos), h, info.family(), info.savanna(), seasonal);
    }

    /**
     * 1.0.5-beta (lot 3) : la culture est-elle sous serre ? Dans une serre multibloc formee (registre, tout monde), ou, sur un autre astre que
     * la Terre seulement, sous l'ancienne serre de verre ({@link #legacyGlass}).
     */
    public static boolean greenhouse(LevelReader level, BlockPos pos) {
        if (!(level instanceof Level l)) {
            return false;
        }
        if (GreenhouseRegistry.covers(l, pos)) {
            return true;
        }
        return legacyRule(SolarSystem.fromDimension(l.dimension())) && legacyGlass(level, pos);
    }

    /**
     * 1.0.5-beta (lot 3) : l'ancienne regle (verre au-dessus) vaut-elle sur cet astre ? Seulement sur un astre qui n'est pas la Terre (bases
     * et astres : la ou rien ne pousse a l'air libre) ; jamais dans l'Overworld, le Nether ni l'End.
     */
    public static boolean legacyRule(@Nullable SolarSystem.Body body) {
        return body != null && body != SolarSystem.Body.EARTH;
    }

    /** Ancienne serre : premier bloc au-dessus (12 blocs) en verre de serre. Les blocs « transparents » des cultures hautes sont sautes. */
    public static boolean legacyGlass(LevelReader level, BlockPos pos) {
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int dy = 1; dy <= GREENHOUSE_HEIGHT; dy++) {
            p.move(0, 1, 0);
            BlockState state = level.getBlockState(p);
            if (state.isAir() || state.getBlock() instanceof CropTopBlock || state.getBlock() instanceof FarmCropBlock) {
                continue;
            }
            return state.is(GREENHOUSE_GLASS);
        }
        return false;
    }

    /** Distance a une plage (0 dedans). */
    static float outside(float v, float min, float max) {
        return v < min ? min - v : v > max ? v - max : 0;
    }

    public static Status evaluate(Level level, BlockPos pos, float tMin, float tMax, float hMin, float hMax, float tolerance,
          boolean irrigated) {
        if (greenhouse(level, pos)) {
            return Status.GREENHOUSE;
        }
        SolarSystem.Body body = SolarSystem.fromDimension(level.dimension());
        if (body != null && body != SolarSystem.Body.EARTH) {
            return Status.HOSTILE;
        }
        return status(place(level, pos), Seasons.now(level), tMin, tMax, hMin, hMax, tolerance, irrigated);
    }

    /** 1.0.5 : statut climatique (hors serre et hors astre) d'un lieu a une date (pur). */
    public static Status status(SeasonClimate.Place place, @Nullable Seasons.Date date, float tMin, float tMax, float hMin, float hMax,
          float tolerance, boolean irrigated) {
        float t = place.temperature(date), dry = place.humidity(date), h = dry + (irrigated ? IRRIGATION : 0);
        //L'irrigation ne rend pas un sol trop humide : la borne haute d'humidite se lit sans elle
        //1.0.5 : une plage qui monte jusqu'au haut de l'echelle vanilla (2,0 : desert, savane) n'a pas de plafond (ete du desert)
        float dt = outside(t, tMin, tMax >= OPEN_TOP ? Float.MAX_VALUE : tMax);
        float dh = Math.max(outside(Math.min(h, 1F), hMin, 2F), outside(dry, -1F, hMax));
        if (dt == 0 && dh == 0) {
            return Status.IDEAL;
        }
        return dt <= tolerance && dh <= tolerance ? Status.TOLERABLE : Status.HOSTILE;
    }

    /** 1.0.5 : pas de la prevision (jours) et horizon (une annee). */
    public static final double OUTLOOK_STEP = 0.25;

    /**
     * 1.0.5 : prevision de la fiche au regard. {@code now} : statut aujourd'hui ; {@code until} : saison a la fin de laquelle la plante cesse
     * de pousser (null : toute l'annee, ou deja hors climat) ; {@code inSeason} : hors climat aujourd'hui mais cultivable a une autre
     * saison (« hors saison ») ; {@code seasonal} : le lieu a des saisons.
     */
    public record Outlook(Status now, @Nullable Seasons.Season until, boolean inSeason, boolean seasonal) {
    }

    /** Prevision sur un an au pas de {@link #OUTLOOK_STEP} jour (calcul pur sur {@link #place}). */
    public static Outlook outlook(SeasonClimate.Place place, @Nullable Seasons.Date date, float tMin, float tMax, float hMin, float hMax,
          float tolerance, boolean irrigated) {
        Status now = status(place, date, tMin, tMax, hMin, hMax, tolerance, irrigated);
        if (date == null || !place.seasonal()) {
            return new Outlook(now, null, false, false);
        }
        int steps = (int) Math.round(date.yearLength() / OUTLOOK_STEP);
        Seasons.Date last = date;
        for (int i = 1; i <= steps; i++) {
            Seasons.Date d = date.plusDays(i * OUTLOOK_STEP);
            boolean grows = status(place, d, tMin, tMax, hMin, hMax, tolerance, irrigated).grows();
            if (now.grows() && !grows) {
                return new Outlook(now, SeasonClimate.endingSeason(last), false, true);
            }
            if (!now.grows() && grows) {
                return new Outlook(now, null, true, true);
            }
            last = d;
        }
        return new Outlook(now, null, false, true);
    }

    public static Status evaluate(Level level, BlockPos pos, CropDef def, Genes genes, boolean irrigated) {
        return evaluate(level, pos, def.tMin(), def.tMax(), def.hMin(), def.hMax(), genes.tolerance(), irrigated);
    }

    /** Zone climatique lisible d'une temperature (cle de langue {@code farm.pioneers.zone.<n>}). */
    public static int zone(float t) {
        return t < 0.15F ? 0 : t < 0.45F ? 1 : t < 0.9F ? 2 : t < 1.5F ? 3 : 4;
    }

    /**
     * 1.0.5-beta (lot 14) : humidite lisible (cle de langue {@code farm.pioneers.humidity_zone.<n>} : aride, seche, moderee, humide, tres
     * humide), sur l'humidite du lieu sans irrigation ({@link #humidity}). Seuils : 0,15 / 0,4 / 0,65 / 0,85 (desert 0, plaine 0,4, foret 0,8,
     * jungle et marais 0,9).
     */
    public static int humidityZone(float h) {
        return h < 0.15F ? 0 : h < 0.4F ? 1 : h < 0.65F ? 2 : h < 0.85F ? 3 : 4;
    }
}

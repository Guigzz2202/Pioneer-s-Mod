package fr.pioneers.gametest;

import fr.pioneers.farm.PiFarm;
import fr.pioneers.farm.crop.Climate;
import fr.pioneers.farm.crop.CropDef;
import fr.pioneers.farm.crop.CropTile;
import fr.pioneers.farm.crop.FarmCropBlock;
import fr.pioneers.farm.crop.Genes;
import fr.pioneers.world.season.CalendarBlock;
import fr.pioneers.world.season.PiSeasons;
import fr.pioneers.world.season.SeasonClimate;
import fr.pioneers.world.season.SeasonData;
import fr.pioneers.world.season.SeasonEvents;
import fr.pioneers.world.season.Seasons;
import fr.pioneers.world.season.Seasons.Season;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 1 (GameTests ; specification « Le monde vivant » §1 et §2.1) : passage d'une saison a l'autre (date, climat qui
 * change en douceur), delai de grace d'un jour hors climat (pas de fane avant, fane apres), culture souterraine sous la couche 50 insensible
 * a la saison (8 °C), comparateur du calendrier mural (1 a 4). Le calendrier du monde (decalage) et l'heure sont remis a la fin de chaque test.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot1Tests {

    private PatchP105Lot1Tests() {
    }

    /** Memorise le calendrier et l'heure, remis au nettoyage. */
    private static void keepCalendar(Gt.Scope s) {
        long offset = SeasonData.get(s.server.overworld()).offset(), time = s.level.getDayTime();
        s.onCleanup(() -> {
            s.level.setDayTime(time);
            SeasonEvents.setOffset(s.server, offset);
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_1_seasons", timeoutTicks = 40)
    public static void p105_1_saison_suivante(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        keepCalendar(s);
        ServerLevel level = s.level;
        int length = Seasons.serverLength();
        SeasonEvents.set(s.server, Season.SPRING, length);
        Seasons.Date d = Seasons.now(level);
        Gt.check(d != null, "date connue dans l'Overworld");
        Gt.equal(Season.SPRING, d.season(), "dernier jour du printemps");
        Gt.equal((long) length, (long) d.dayOfSeason(), "jour " + length);
        level.setDayTime(level.getDayTime() + Seasons.DAY);
        Seasons.Date next = Seasons.now(level);
        Gt.equal(Season.SUMMER, next.season(), "un jour plus tard : l'ete");
        Gt.equal(1L, (long) next.dayOfSeason(), "premier jour de l'ete");
        //climat : continu au changement de saison, et l'ete plus chaud que l'hiver (lieu a ciel ouvert)
        BlockPos open = h.absolutePos(new BlockPos(4, 2, 4));
        SeasonClimate.Place place = Climate.place(level, open);
        Gt.check(place.seasonal(), "lieu a ciel ouvert : climat saisonnier");
        Seasons.Date edge = Seasons.date(0, 0, length).plusDays(length);
        float before = place.temperature(edge.plusDays(-1e-4)), after = place.temperature(edge);
        Gt.check(Math.abs(before - after) < 1e-3F, "temperature continue au passage printemps -> ete");
        float summer = place.temperature(Seasons.date(0, 0, length).plusDays(1.5 * length));
        float winter = place.temperature(Seasons.date(0, 0, length).plusDays(3.5 * length));
        Gt.check(summer > winter, "l'ete est plus chaud que l'hiver");
        Gt.check(Math.abs(Climate.temperature(level, open) - place.temperature(next)) < 1e-4F, "Climate.temperature suit la date du serveur");
        s.done();
    }

    /** Premiere culture hors climat ici (saison changee au besoin). */
    private static CropDef hostileCrop(Gt.Scope s, BlockPos pos) {
        for (Season season : Season.values()) {
            SeasonEvents.set(s.server, season, Seasons.serverLength() / 2 + 1);
            for (var e : PiFarm.CROPS.entrySet()) {
                CropDef def = e.getValue().get().def();
                if (!(e.getValue().get() instanceof fr.pioneers.farm.crop.MushroomCropBlock) && Climate.evaluate(s.level, pos, def, Genes.BASE, true) == Climate.Status.HOSTILE) {
                    return def;
                }
            }
        }
        throw new net.minecraft.gametest.framework.GameTestAssertException("aucune culture hors climat ici, a aucune saison");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_1_grace", timeoutTicks = 40)
    public static void p105_1_delai_de_grace(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        keepCalendar(s);
        ServerLevel level = s.level;
        BlockPos soil = h.absolutePos(new BlockPos(4, 1, 4)), pos = soil.above();
        level.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 3);
        CropDef def = hostileCrop(s, pos);
        FarmCropBlock block = PiFarm.CROPS.get(def.id()).get();
        level.setBlock(pos, block.defaultBlockState(), 3);
        CropTile tile = FarmCropBlock.tile(level, pos);
        Gt.check(tile != null, "tuile de la culture");
        Gt.equal(-1L, tile.hostileSince(), "aucun compte au depart (monde existant : rien ne meurt au chargement)");
        long now = level.getDayTime();
        block.randomTick(level.getBlockState(pos), level, pos, level.random);
        Gt.check(level.getBlockState(pos).is(block), "premier tick hors climat : la plante tient");
        Gt.equal(now, tile.hostileSince(), "le compte part du premier tick hors climat");
        tile.setHostileSince(now - FarmCropBlock.GRACE_TICKS + 1);
        block.randomTick(level.getBlockState(pos), level, pos, level.random);
        Gt.check(level.getBlockState(pos).is(block), "un tick avant la fin du delai : toujours vivante");
        tile.setHostileSince(now - FarmCropBlock.GRACE_TICKS);
        block.randomTick(level.getBlockState(pos), level, pos, level.random);
        Gt.check(level.getBlockState(pos).is(PiFarm.WITHERED_CROP.get()), "un jour entier hors climat : fanee");
        Gt.check(!FarmCropBlock.graceOver(now, now + FarmCropBlock.GRACE_TICKS - 1) && FarmCropBlock.graceOver(now, now + FarmCropBlock.GRACE_TICKS),
              "regle du delai (pur)");
        Gt.check(!FarmCropBlock.graceOver(-1, now), "sans compte : jamais fanee");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_1_underground", timeoutTicks = 120)
    public static void p105_1_souterrain_sans_saison(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        keepCalendar(s);
        ServerLevel level = s.level;
        BlockPos cell = h.absolutePos(new BlockPos(4, 2, 4));
        Gt.check(cell.getY() < SeasonClimate.UNDERGROUND_Y, "les GameTests sont sous la couche 50 (monde plat)");
        //cave fermee : sol, murs et toit de pierre autour de la case
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    if (dx != 0 || dz != 0 || dy == -1 || dy == 2) {
                        level.setBlock(cell.offset(dx, dy, dz), Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }
        }
        level.setBlock(cell.above(), Blocks.AIR.defaultBlockState(), 3);
        float fixed = SeasonClimate.internal(SeasonClimate.UNDERGROUND_CELSIUS);
        h.succeedWhen(() -> {
            Gt.check(Climate.underground(level, cell), "case sans ciel sous la couche 50 : souterraine");
            SeasonEvents.set(s.server, Season.SUMMER, 5);
            float summer = Climate.temperature(level, cell), hs = Climate.humidity(level, cell);
            SeasonEvents.set(s.server, Season.WINTER, 5);
            float winter = Climate.temperature(level, cell);
            Gt.check(Math.abs(summer - fixed) < 1e-5F && Math.abs(winter - fixed) < 1e-5F, "8 °C en ete comme en hiver");
            Gt.check(Math.abs(hs - SeasonClimate.UNDERGROUND_HUMIDITY) < 1e-5F, "humidite 0,5");
            Gt.check(!Climate.place(level, cell).seasonal(), "aucune saison sous terre");
            s.done();
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_1_calendar", timeoutTicks = 60)
    public static void p105_1_comparateur_calendrier(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        keepCalendar(s);
        ServerLevel level = s.level;
        BlockPos wall = h.absolutePos(new BlockPos(4, 2, 5)), cal = wall.north(), comp = cal.north();
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(cal, PiSeasons.CALENDAR.getBlock().defaultBlockState().setValue(CalendarBlock.FACING, Direction.NORTH), 3);
        level.setBlock(comp.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(comp, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.SOUTH), 3);
        Gt.check(level.getBlockState(cal).canSurvive(level, cal), "calendrier accroche au mur");
        SeasonEvents.set(s.server, Season.SUMMER, 1);
        level.getBlockState(cal).tick(level, cal, level.random);
        Gt.equal(2L, (long) level.getBlockState(cal).getAnalogOutputSignal(level, cal), "ete : 2");
        SeasonEvents.set(s.server, Season.WINTER, 1);
        BlockState st = level.getBlockState(cal);
        st.tick(level, cal, level.random);
        Gt.equal(3L, (long) level.getBlockState(cal).getValue(CalendarBlock.SEASON), "page de l'hiver");
        Gt.equal(4L, (long) level.getBlockState(cal).getAnalogOutputSignal(level, cal), "hiver : 4");
        h.succeedWhen(() -> {
            Gt.check(level.getBlockEntity(comp) instanceof ComparatorBlockEntity c && c.getOutputSignal() == 4, "le comparateur lit 4 (hiver)");
            s.done();
        });
    }
}

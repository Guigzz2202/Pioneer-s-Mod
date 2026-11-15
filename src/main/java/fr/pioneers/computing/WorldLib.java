package fr.pioneers.computing;

import fr.pioneers.computing.script.ScriptError;
import fr.pioneers.computing.script.Value.PDict;
import fr.pioneers.computing.script.Value.PObject;
import fr.pioneers.computing.script.Value;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * 0.20.41 : modules {@code world} (heure du jour, jour, meteo, dimension, biome, lune) et {@code computer} (position, energie, programme,
 * bip) de PioScript. Documentation : tools/gc/computing/pioscript_doc.py.
 */
final class WorldLib {

    private WorldLib() {
    }

    private static Level level(ComputerTile tile) {
        Level l = tile.getLevel();
        if (l == null) {
            throw new ScriptError("internal", "level");
        }
        return l;
    }

    static PObject world(ComputerTile tile) {
        return new PObject("world")
              .fn("time", a -> level(tile).getDayTime() % 24_000L)
              .fn("day", a -> level(tile).getDayTime() / 24_000L)
              .fn("clock", a -> {
                  //0 tick = 6 h du matin
                  long t = (level(tile).getDayTime() % 24_000L + 6_000L) % 24_000L;
                  long minutes = t * 1440 / 24_000;
                  return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
              })
              .fn("is_day", a -> {
                  long t = level(tile).getDayTime() % 24_000L;
                  return t < 12_300 || t > 23_850;
              })
              .fn("moon", a -> (long) level(tile).getMoonPhase())
              .fn("weather", a -> {
                  Level l = level(tile);
                  BlockPos p = tile.getBlockPos();
                  if (l.isThundering() && l.isRainingAt(p.above())) {
                      return "thunder";
                  }
                  return l.isRaining() && l.getBiome(p).value().hasPrecipitation() ? "rain" : "clear";
              })
              .fn("dimension", a -> level(tile).dimension().location().toString())
              .fn("biome", a -> level(tile).getBiome(tile.getBlockPos()).unwrapKey().map(k -> k.location().toString()).orElse("?"));
    }

    static PObject computer(ComputerTile tile) {
        return new PObject("computer")
              .fn("position", a -> {
                  BlockPos p = tile.getBlockPos();
                  PDict d = new PDict();
                  d.put("x", (long) p.getX());
                  d.put("y", (long) p.getY());
                  d.put("z", (long) p.getZ());
                  return d;
              })
              .fn("energy", a -> tile.energyStored())
              .fn("energy_max", a -> tile.energyCapacity())
              .fn("program", a -> tile.programName())
              .fn("beep", a -> {
                  if (a.length > 1) {
                      throw new ScriptError("arity", "computer.beep", "0..1", a.length);
                  }
                  float pitch = (float) Math.max(0.5, Math.min(2, a.length > 0 ? Value.num(a[0]) : 1));
                  level(tile).playSound(null, tile.getBlockPos(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.6F, pitch);
                  return Value.NONE;
              });
    }
}

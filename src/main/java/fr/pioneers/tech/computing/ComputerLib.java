package fr.pioneers.tech.computing;

import fr.pioneers.tech.compute.ComputeNetwork;
import fr.pioneers.tech.computing.script.RecipeLockFns;
import fr.pioneers.tech.computing.script.ScriptError;
import fr.pioneers.tech.computing.script.Value.PDict;
import fr.pioneers.tech.computing.script.Value.PList;
import fr.pioneers.tech.computing.script.Value.PObject;
import fr.pioneers.tech.computing.script.Value;
import fr.pioneers.tech.computing.script.Vm;
import fr.pioneers.core.Directions;
import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.core.data.DigitalNetwork;
import fr.pioneers.tech.industry.multi.PortTile;
import fr.pioneers.tech.industry.multi.Structures;
import fr.pioneers.core.registries.PiComputing;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Bibliotheque d'un ordinateur (0.17.1), ajoutee aux fonctions de base de PioScript :
 * <ul>
 *     <li>{@code redstone.get(face)}, {@code redstone.set(face, niveau)}, {@code redstone.output(face)} — faces : nord, sud, est, ouest,
 *     haut, bas (ou north, south, east, west, up, down) ;</li>
 *     <li>{@code link(n)} (n = 1..4) : machine liee — {@code name()}, {@code connected()}, {@code keys()}, {@code value(cle)},
 *     {@code values()}, {@code commands()}, {@code command(cle, valeur)} ;</li>
 *     <li>{@code screen.print(texte)}, {@code screen.write(ligne, texte)}, {@code screen.clear()} : texte des ecrans de supervision lies ;</li>
 *     <li>{@code fs.list(lecteur)}, {@code fs.read(chemin)}, {@code fs.write(chemin, texte)}, {@code fs.append(chemin, texte)},
 *     {@code fs.exists(chemin)}, {@code fs.delete(chemin)}, {@code fs.free(lecteur)} — chemins {@code a:nom}, {@code b:nom}, {@code nom}
 *     (disque interne) ;</li>
 *     <li>0.17.2 : {@code compute.available()} (GOPS libres du reseau de calcul touche), {@code compute.quantum()} (QOPS libres),
 *     {@code compute.request(n)} (demande n GOPS, a renouveler au moins chaque seconde ; renvoie les GOPS accordes), {@code compute.total()}
 *     (calcul servi dans le monde) ;</li>
 *     <li>0.18.0 : {@code flight.check(lecteur)} ("none", "blank", "programmed"), {@code flight.flash(lecteur[, mission])} programme les
 *     ordinateurs de vol du lecteur (toute la pile ; renvoie le nombre programme) ;</li>
 *     <li>0.20.5 : {@code rail} (trains, gares, envois, limites, signaux, pont-bascule) : voir {@link RailLib}.</li>
 *     <li>0.20.41 : {@code redstone.inputs()}, {@code redstone.all(n)}, {@code links()}, {@code screen.rows/cols/center/bar},
 *     {@code fs.lines/size/rename/copy}, {@code world} (heure, jour, meteo, dimension, biome, lune), {@code computer} (position, energie,
 *     programme, bip).</li>
 * </ul>
 * Documentation de tout PioScript : tools/gc/computing/pioscript_doc.py (aide help(), bouton « ? » du terminal, disquette d'exemples).
 */
public final class ComputerLib {

    private ComputerLib() {
    }

    static Direction face(Object v) {
        String s = Value.str(v).toLowerCase(Locale.ROOT);
        return switch (s) {
            case "nord", "north", "n" -> Direction.NORTH;
            case "sud", "south", "s" -> Direction.SOUTH;
            case "est", "east", "e" -> Direction.EAST;
            case "ouest", "west", "o", "w" -> Direction.WEST;
            case "haut", "up", "dessus", "top" -> Direction.UP;
            case "bas", "down", "dessous", "bottom" -> Direction.DOWN;
            default -> throw new ScriptError("bad_side", s);
        };
    }

    private static void arity(String name, Object[] a, int n) {
        if (a.length != n) {
            throw new ScriptError("arity", name, n, a.length);
        }
    }

    static void install(Vm vm, ComputerTile tile) {
        PObject redstone = new PObject("redstone")
              .fn("get", a -> {
                  arity("redstone.get", a, 1);
                  return (long) tile.input(face(a[0]));
              })
              .fn("set", a -> {
                  arity("redstone.set", a, 2);
                  tile.setOutput(face(a[0]), (int) Value.integer(a[1]));
                  return Value.NONE;
              })
              .fn("output", a -> {
                  arity("redstone.output", a, 1);
                  return (long) tile.output(face(a[0]));
              })
              .fn("inputs", a -> {
                  PDict out = new PDict();
                  for (Direction d : Directions.ALL) {
                      out.put(d.getSerializedName(), (long) tile.input(d));
                  }
                  return out;
              })
              .fn("all", a -> {
                  arity("redstone.all", a, 1);
                  int level = (int) Value.integer(a[0]);
                  for (Direction d : Directions.ALL) {
                      tile.setOutput(d, level);
                  }
                  return Value.NONE;
              });
        vm.define("redstone", redstone);

        vm.define("link", new Value.Native("link", a -> {
            arity("link", a, 1);
            int n = (int) Value.integer(a[0]);
            if (n < 1 || n > ComputerTile.LINKS) {
                throw new ScriptError("bad_link", n);
            }
            return linkObject(tile, n - 1);
        }));
        vm.define("network", network(tile));
        vm.define("links", new Value.Native("links", a -> {
            PList out = new PList();
            for (int i = 0; i < ComputerTile.LINKS; i++) {
                if (tile.link(i) == null) {
                    continue;
                }
                BlockEntity t = tile.linked(i);
                PDict d = new PDict();
                d.put("n", (long) (i + 1));
                d.put("name", t == null ? "" : Probes.name(t).getString());
                d.put("connected", t != null);
                out.add(d);
            }
            return out;
        }));

        PObject screen = new PObject("screen")
              .fn("print", a -> {
                  arity("screen.print", a, 1);
                  List<String> text = tile.screenText();
                  for (String line : Value.str(a[0]).split("\n", -1)) {
                      text.add(clip(line));
                      while (text.size() > ComputerTile.SCREEN_ROWS) {
                          text.remove(0);
                      }
                  }
                  tile.screenChanged();
                  return Value.NONE;
              })
              .fn("write", a -> {
                  arity("screen.write", a, 2);
                  int row = (int) Value.integer(a[0]);
                  if (row < 1 || row > ComputerTile.SCREEN_ROWS) {
                      throw new ScriptError("bad_row", row, ComputerTile.SCREEN_ROWS);
                  }
                  List<String> text = tile.screenText();
                  while (text.size() < row) {
                      text.add("");
                  }
                  text.set(row - 1, clip(Value.str(a[1])));
                  tile.screenChanged();
                  return Value.NONE;
              })
              .fn("clear", a -> {
                  tile.screenText().clear();
                  tile.screenChanged();
                  return Value.NONE;
              })
              .fn("rows", a -> (long) ComputerTile.SCREEN_ROWS)
              .fn("cols", a -> (long) ComputerTile.SCREEN_COLS)
              .fn("center", a -> {
                  arity("screen.center", a, 2);
                  String t = clip(Value.str(a[1]));
                  int pad = (ComputerTile.SCREEN_COLS - t.length()) / 2;
                  writeRow(tile, (int) Value.integer(a[0]), " ".repeat(Math.max(0, pad)) + t);
                  return Value.NONE;
              })
              .fn("bar", a -> {
                  arity("screen.bar", a, 4);
                  double v = Value.num(a[2]), max = Value.num(a[3]);
                  double f = max <= 0 ? 0 : Math.max(0, Math.min(1, v / max));
                  String label = Value.str(a[1]);
                  if (label.length() > 18) {
                      label = label.substring(0, 18);
                  }
                  int width = Math.max(4, ComputerTile.SCREEN_COLS - label.length() - 10);
                  int full = (int) Math.round(f * width);
                  writeRow(tile, (int) Value.integer(a[0]), label + " [" + "█".repeat(full) + "░".repeat(width - full) + "] "
                        + String.format(Locale.ROOT, "%3d %%", Math.round(f * 100)));
                  return Value.NONE;
              });
        vm.define("screen", screen);

        PObject fs = new PObject("fs")
              .fn("list", a -> {
                  int d = a.length == 0 ? 2 : driveArg(a[0]);
                  PList out = new PList();
                  tile.listFiles(d).forEach(out::add);
                  return out;
              })
              .fn("read", a -> {
                  arity("fs.read", a, 1);
                  String text = tile.readFile(Value.str(a[0]));
                  if (text == null) {
                      throw new ScriptError("no_file", Value.str(a[0]));
                  }
                  return text;
              })
              .fn("write", a -> {
                  arity("fs.write", a, 2);
                  if (!tile.writeFile(Value.str(a[0]), Value.str(a[1]))) {
                      throw new ScriptError("write_failed", Value.str(a[0]));
                  }
                  return Value.NONE;
              })
              .fn("append", a -> {
                  arity("fs.append", a, 2);
                  String path = Value.str(a[0]);
                  String old = tile.readFile(path);
                  if (!tile.writeFile(path, (old == null ? "" : old) + Value.str(a[1]))) {
                      throw new ScriptError("write_failed", path);
                  }
                  return Value.NONE;
              })
              .fn("exists", a -> {
                  arity("fs.exists", a, 1);
                  return tile.readFile(Value.str(a[0])) != null;
              })
              .fn("delete", a -> {
                  arity("fs.delete", a, 1);
                  return tile.deleteFile(Value.str(a[0]));
              })
              .fn("free", a -> (long) tile.freeSpace(a.length == 0 ? 2 : driveArg(a[0])))
              .fn("lines", a -> {
                  arity("fs.lines", a, 1);
                  String text = tile.readFile(Value.str(a[0]));
                  PList out = new PList();
                  if (text != null && !text.isEmpty()) {
                      String[] parts = text.split("\n", -1);
                      int n = text.endsWith("\n") ? parts.length - 1 : parts.length;
                      for (int i = 0; i < n; i++) {
                          out.add(parts[i]);
                      }
                  }
                  return out;
              })
              .fn("size", a -> {
                  arity("fs.size", a, 1);
                  String text = tile.readFile(Value.str(a[0]));
                  return text == null ? -1L : (long) text.length();
              })
              .fn("copy", a -> {
                  arity("fs.copy", a, 2);
                  String text = tile.readFile(Value.str(a[0]));
                  if (text == null) {
                      throw new ScriptError("no_file", Value.str(a[0]));
                  }
                  if (!tile.writeFile(Value.str(a[1]), text)) {
                      throw new ScriptError("write_failed", Value.str(a[1]));
                  }
                  return Value.NONE;
              })
              .fn("rename", a -> {
                  arity("fs.rename", a, 2);
                  String from = Value.str(a[0]), to = Value.str(a[1]);
                  String text = tile.readFile(from);
                  if (text == null) {
                      throw new ScriptError("no_file", from);
                  }
                  if (from.equals(to)) {
                      return Value.NONE;
                  }
                  if (!tile.writeFile(to, text)) {
                      throw new ScriptError("write_failed", to);
                  }
                  tile.deleteFile(from);
                  return Value.NONE;
              });
        vm.define("fs", fs);

        //0.20.41 : le monde autour de l'ordinateur et l'ordinateur lui-meme
        vm.define("world", WorldLib.world(tile));
        vm.define("computer", WorldLib.computer(tile));

        //0.17.2 : reseau de calcul (cables de donnees ou centre de donnees colle a l'ordinateur)
        PObject compute = new PObject("compute")
              .fn("available", a -> round1(ComputeNetwork.available(tile.getLevel(), tile.getBlockPos())[0]))
              .fn("quantum", a -> round1(ComputeNetwork.available(tile.getLevel(), tile.getBlockPos())[1]))
              .fn("request", a -> {
                  arity("compute.request", a, 1);
                  return round1(ComputeNetwork.demand(tile.getLevel(), tile.getBlockPos(), Math.max(0, Value.num(a[0])), 0));
              })
              .fn("total", a -> round1(ComputeNetwork.totalGops(tile.getLevel())));
        vm.define("compute", compute);

        //0.18.0 : ordinateurs de vol (sondes, fusees) ranges dans un lecteur
        PObject flight = new PObject("flight")
              .fn("check", a -> {
                  arity("flight.check", a, 1);
                  ItemStack stack = tile.driveStack(driveArg(a[0]));
                  if (!(stack.getItem() instanceof FlightComputerItem f)) {
                      return "none";
                  }
                  return f.isProgrammed() ? "programmed" : "blank";
              })
              .fn("flash", a -> {
                  if (a.length < 1 || a.length > 2) {
                      throw new ScriptError("arity", "flight.flash", "1..2", a.length);
                  }
                  int d = driveArg(a[0]);
                  ItemStack stack = tile.driveStack(d);
                  if (!(stack.getItem() instanceof FlightComputerItem f) || d > 1) {
                      throw new ScriptError("no_flight_computer", Value.str(a[0]));
                  }
                  if (f.isProgrammed()) {
                      return 0L;
                  }
                  ItemStack out = new ItemStack(PiComputing.PROGRAMMED_FLIGHT_COMPUTER.get(), stack.getCount());
                  out.getOrCreateTag().putString(FlightComputerItem.PROGRAM, tile.programName());
                  if (a.length > 1) {
                      String mission = Value.str(a[1]);
                      out.getOrCreateTag().putString(FlightComputerItem.MISSION, mission.length() > 32 ? mission.substring(0, 32) : mission);
                  }
                  tile.replaceDrive(d, out);
                  return (long) out.getCount();
              });
        vm.define("flight", flight);

        //0.20.5 : chemin de fer
        RailLib.install(vm, tile);
        NavalLib.install(vm, tile);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static void writeRow(ComputerTile tile, int row, String text) {
        if (row < 1 || row > ComputerTile.SCREEN_ROWS) {
            throw new ScriptError("bad_row", row, ComputerTile.SCREEN_ROWS);
        }
        List<String> lines = tile.screenText();
        while (lines.size() < row) {
            lines.add("");
        }
        lines.set(row - 1, clip(text));
        tile.screenChanged();
    }

    private static String clip(String s) {
        return s.length() > ComputerTile.SCREEN_COLS ? s.substring(0, ComputerTile.SCREEN_COLS) : s;
    }

    private static int driveArg(Object v) {
        String s = Value.str(v).toLowerCase(Locale.ROOT).replace(":", "");
        return switch (s) {
            case "a" -> 0;
            case "b" -> 1;
            case "c", "" -> 2;
            default -> throw new ScriptError("bad_drive", s);
        };
    }

    private static PObject linkObject(ComputerTile tile, int index) {
        return machineObject(() -> tile.linked(index));
    }

    /** Machine sondable (liaison ou machine du reseau numerique). */
    private static PObject machineObject(Supplier<BlockEntity> target) {
        PObject o = new PObject("link");
        o.fn("connected", a -> target.get() != null)
              .fn("name", a -> {
                  BlockEntity t = target.get();
                  return t == null ? Value.NONE : Probes.name(t).getString();
              })
              .fn("keys", a -> {
                  PList out = new PList();
                  Probes.read(target.get()).forEach(v -> out.add(v.key()));
                  return out;
              })
              .fn("value", a -> {
                  arity("value", a, 1);
                  ProbeValue v = ProbeValue.find(Probes.read(target.get()), Value.str(a[0]));
                  return v == null ? Value.NONE : (Object) v.value();
              })
              .fn("values", a -> {
                  PDict out = new PDict();
                  Probes.read(target.get()).forEach(v -> out.put(v.key(), v.value()));
                  return out;
              })
              .fn("commands", a -> {
                  PList out = new PList();
                  Probes.commands(target.get()).forEach(c -> out.add(c.key()));
                  return out;
              })
              .fn("command", a -> {
                  if (a.length < 1 || a.length > 2) {
                      throw new ScriptError("arity", "command", "1..2", a.length);
                  }
                  return Probes.command(target.get(), Value.str(a[0]), a.length > 1 ? (int) Math.round(Value.num(a[1])) : 0);
              });
        //MAJ Recettes (lot 7) : selecteur de recette (m.lockRecipe(id), m.unlockRecipe(), m.lockedRecipe())
        RecipeLockFns.install(o, () -> recipeLockTarget(target.get()));
        return o;
    }

    /**
     * MAJ Recettes (lot 7) : la machine visee comme cible du selecteur de PioScript (un port de multibloc : son controleur), ou null (aussi
     * utilise par les GameTests).
     */
    @Nullable
    public static RecipeLockFns.Target recipeLockTarget(@Nullable BlockEntity t) {
        if (t instanceof PortTile && t.getLevel() != null) {
            BlockPos c = Structures.controllerAt(t.getLevel(), t.getBlockPos());
            t = c == null ? null : t.getLevel().getBlockEntity(c);
        }
        if (!(t instanceof RecipeLockable machine) || machine.recipeChoices().isEmpty()) {
            return null;
        }
        return new RecipeLockFns.Target() {
            @Override
            public boolean lockable() {
                return true;
            }

            @Override
            public String locked() {
                ResourceLocation id = machine.lockedRecipe();
                return id == null ? null : id.toString();
            }

            @Override
            public boolean lock(String id) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                return rl != null && machine.setLockedRecipe(rl);
            }

            @Override
            public void unlock() {
                machine.setLockedRecipe(null);
            }
        };
    }

    private static final String[] ROLES = {"other", "bank", "lab", "producer", "compute", "ai", "supervision", "multiblock"};

    /**
     * 0.20.46 : bibliotheque {@code network} : le reseau numerique touche par l'ordinateur (fibre optique), sans liaison ni limite de
     * distance : donnees en stock, calcul, machines branchees (sondes et commandes), recherche en cours.
     */
    static PObject network(ComputerTile tile) {
        Supplier<DigitalNetwork.Snapshot> scan = () -> tile.getLevel() == null
              ? new DigitalNetwork.Snapshot() : DigitalNetwork.scan(tile.getLevel(), tile.getBlockPos());
        return new PObject("network")
              .fn("connected", a -> scan.get().connected())
              .fn("data", a -> {
                  PDict out = new PDict();
                  scan.get().stock.forEach((type, amount) -> {
                      ResourceLocation id = type.getRegistryName();
                      out.put(id == null ? "?" : id.getPath(), amount);
                  });
                  return out;
              })
              .fn("stored", a -> scan.get().totalStored())
              .fn("capacity", a -> scan.get().bankCapacity)
              .fn("compute", a -> {
                  DigitalNetwork.Snapshot s = scan.get();
                  PDict out = new PDict();
                  out.put("gops", s.supplyGops);
                  out.put("qops", s.supplyQops);
                  out.put("gops_demand", s.demandGops);
                  out.put("qops_demand", s.demandQops);
                  return out;
              })
              .fn("machines", a -> {
                  DigitalNetwork.Snapshot s = scan.get();
                  PList out = new PList();
                  for (int i = 0; i < s.machines.size(); i++) {
                      BlockEntity t = DigitalNetwork.machine(tile.getLevel(), s, i);
                      PDict d = new PDict();
                      d.put("n", (long) (i + 1));
                      d.put("name", t == null ? "" : Probes.name(t).getString());
                      d.put("role", t == null ? "other" : ROLES[NetworkTerminalTile.role(t)]);
                      BlockPos p = s.machines.get(i);
                      d.put("x", (long) p.getX());
                      d.put("y", (long) p.getY());
                      d.put("z", (long) p.getZ());
                      out.add(d);
                  }
                  return out;
              })
              .fn("machine", a -> {
                  arity("network.machine", a, 1);
                  int n = (int) Value.integer(a[0]);
                  return machineObject(() -> {
                      DigitalNetwork.Snapshot s = scan.get();
                      return tile.getLevel() == null ? null : DigitalNetwork.machine(tile.getLevel(), s, n - 1);
                  });
              })
              .fn("find", a -> {
                  arity("network.find", a, 1);
                  String want = Value.str(a[0]).toLowerCase(Locale.ROOT);
                  DigitalNetwork.Snapshot s = scan.get();
                  for (int i = 0; i < s.machines.size(); i++) {
                      BlockEntity t = DigitalNetwork.machine(tile.getLevel(), s, i);
                      if (t != null && Probes.name(t).getString().toLowerCase(Locale.ROOT).contains(want)) {
                          return (long) (i + 1);
                      }
                  }
                  return Value.NONE;
              });
    }
}

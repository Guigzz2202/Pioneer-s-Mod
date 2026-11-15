package fr.pioneers.tech.computing.script;

import fr.pioneers.tech.computing.script.Value.Native;
import fr.pioneers.tech.computing.script.Value.PDict;
import fr.pioneers.tech.computing.script.Value.PList;
import fr.pioneers.tech.computing.script.Value.PObject;
import fr.pioneers.tech.computing.script.Value.PRange;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Fonctions natives de base PioScript (0.17.1) et methodes des chaines, listes et dictionnaires.
 * print, len, str, int, float, bool, abs, min, max, round, sum, sorted, list, dict, range, type, chr, ord, random, randint, sleep (secondes),
 * time (secondes depuis le lancement), input. 0.20.41 : enumerate, zip, any, all, reversed, divmod, hex, bin, repr, format, isinstance, help,
 * modules {@code math} ({@link MathLib}) et {@code json} ({@link JsonLib}), nouvelles methodes de chaines et {@code setdefault}. Toute la
 * documentation : tools/gc/computing/pioscript_doc.py ({@link GeneratedPioDoc}).
 */
final class Builtins {

    private Builtins() {
    }

    private static void def(Vm vm, String name, Value.Fn fn) {
        vm.define(name, new Native(name, fn));
    }

    private static void arity(String name, Object[] a, int min, int max) {
        if (a.length < min || a.length > max) {
            throw new ScriptError("arity", name, min == max ? String.valueOf(min) : min + ".." + max, a.length);
        }
    }

    static void install(Vm vm) {
        def(vm, "print", a -> {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < a.length; i++) {
                if (i > 0) {
                    b.append(' ');
                }
                b.append(Value.str(a[i]));
            }
            vm.host().print(b.toString());
            return Value.NONE;
        });
        def(vm, "len", a -> {
            arity("len", a, 1, 1);
            Object v = a[0];
            if (v instanceof String s) {
                return (long) s.length();
            }
            if (v instanceof PList l) {
                return (long) l.items.size();
            }
            if (v instanceof PDict d) {
                return (long) d.map.size();
            }
            if (v instanceof PRange r) {
                return r.length();
            }
            throw new ScriptError("no_len", Value.typeName(v));
        });
        def(vm, "str", a -> {
            arity("str", a, 0, 1);
            return a.length == 0 ? "" : Value.str(a[0]);
        });
        def(vm, "int", a -> {
            arity("int", a, 1, 1);
            Object v = a[0];
            if (v instanceof String s) {
                try {
                    return Long.parseLong(s.trim());
                } catch (NumberFormatException e) {
                    throw new ScriptError("convert", s, "int");
                }
            }
            double d = Value.num(v);
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new ScriptError("convert", Value.str(v), "int");
            }
            return (long) d;
        });
        def(vm, "float", a -> {
            arity("float", a, 1, 1);
            if (a[0] instanceof String s) {
                try {
                    return Double.parseDouble(s.trim());
                } catch (NumberFormatException e) {
                    throw new ScriptError("convert", s, "float");
                }
            }
            return Value.num(a[0]);
        });
        def(vm, "bool", a -> {
            arity("bool", a, 0, 1);
            return a.length > 0 && Value.truthy(a[0]);
        });
        def(vm, "abs", a -> {
            arity("abs", a, 1, 1);
            return a[0] instanceof Long l ? (Object) Math.abs(l) : (Object) Math.abs(Value.num(a[0]));
        });
        def(vm, "min", a -> extreme("min", a, -1));
        def(vm, "max", a -> extreme("max", a, 1));
        def(vm, "round", a -> {
            arity("round", a, 1, 2);
            double d = Value.num(a[0]);
            if (a.length == 1) {
                return Math.round(d);
            }
            double f = Math.pow(10, Math.max(-15, Math.min(15, Value.integer(a[1]))));
            return Math.round(d * f) / f;
        });
        def(vm, "sum", a -> {
            arity("sum", a, 1, 2);
            Object total = a.length > 1 ? a[1] : (Object) 0L;
            for (Object o : Ops.toList(a[0])) {
                total = Ops.binary(Code.ADD, total, o);
            }
            return total;
        });
        def(vm, "sorted", a -> {
            arity("sorted", a, 1, 2);
            List<Object> l = new ArrayList<>(Ops.toList(a[0]));
            l.sort(Ops::order);
            if (a.length > 1 && Value.truthy(a[1])) {
                Collections.reverse(l);
            }
            return new PList(l);
        });
        def(vm, "list", a -> {
            arity("list", a, 0, 1);
            return a.length == 0 ? new PList() : new PList(new ArrayList<>(Ops.toList(a[0])));
        });
        def(vm, "dict", a -> {
            arity("dict", a, 0, 0);
            return new PDict();
        });
        def(vm, "range", a -> {
            arity("range", a, 1, 3);
            long start = a.length > 1 ? Value.integer(a[0]) : 0, stop = Value.integer(a[a.length > 1 ? 1 : 0]);
            long step = a.length > 2 ? Value.integer(a[2]) : 1;
            if (step == 0) {
                throw new ScriptError("zero");
            }
            return new PRange(start, stop, step);
        });
        def(vm, "type", a -> {
            arity("type", a, 1, 1);
            return Value.typeName(a[0]);
        });
        def(vm, "chr", a -> {
            arity("chr", a, 1, 1);
            long c = Value.integer(a[0]);
            if (c < 0 || c > 0xFFFF) {
                throw new ScriptError("convert", c, "chr");
            }
            return String.valueOf((char) c);
        });
        def(vm, "ord", a -> {
            arity("ord", a, 1, 1);
            if (!(a[0] instanceof String s) || s.length() != 1) {
                throw new ScriptError("convert", Value.str(a[0]), "ord");
            }
            return (long) s.charAt(0);
        });
        def(vm, "random", a -> {
            arity("random", a, 0, 0);
            return vm.random().nextDouble();
        });
        def(vm, "randint", a -> {
            arity("randint", a, 2, 2);
            long lo = Value.integer(a[0]), hi = Value.integer(a[1]);
            if (hi < lo) {
                throw new ScriptError("range_empty");
            }
            return lo + (long) (vm.random().nextDouble() * (hi - lo + 1));
        });
        def(vm, "sleep", a -> {
            arity("sleep", a, 0, 1);
            double s = a.length > 0 ? Value.num(a[0]) : 0;
            return new Value.Yield((int) Math.max(1, Math.min(72_000, Math.round(s * 20))), false, "");
        });
        def(vm, "time", a -> {
            arity("time", a, 0, 0);
            return vm.elapsed();
        });
        def(vm, "input", a -> {
            arity("input", a, 0, 1);
            String p = a.length > 0 ? Value.str(a[0]) : "";
            if (!p.isEmpty()) {
                vm.host().print(p);
            }
            return new Value.Yield(0, true, p);
        });
        installMore(vm);
    }

    /** 0.20.41 : fonctions de base ajoutees (voir la documentation generee). */
    private static void installMore(Vm vm) {
        def(vm, "enumerate", a -> {
            arity("enumerate", a, 1, 2);
            long i = a.length > 1 ? Value.integer(a[1]) : 0;
            PList out = new PList();
            for (Object o : Ops.toList(a[0])) {
                PList pair = new PList();
                pair.add(i++);
                pair.add(o);
                out.add(pair);
            }
            return out;
        });
        def(vm, "zip", a -> {
            PList out = new PList();
            if (a.length == 0) {
                return out;
            }
            List<List<Object>> lists = new ArrayList<>();
            int n = Integer.MAX_VALUE;
            for (Object o : a) {
                List<Object> l = Ops.toList(o);
                lists.add(l);
                n = Math.min(n, l.size());
            }
            for (int i = 0; i < n; i++) {
                PList row = new PList();
                for (List<Object> l : lists) {
                    row.add(l.get(i));
                }
                out.add(row);
            }
            return out;
        });
        def(vm, "any", a -> {
            arity("any", a, 1, 1);
            for (Object o : Ops.toList(a[0])) {
                if (Value.truthy(o)) {
                    return true;
                }
            }
            return false;
        });
        def(vm, "all", a -> {
            arity("all", a, 1, 1);
            for (Object o : Ops.toList(a[0])) {
                if (!Value.truthy(o)) {
                    return false;
                }
            }
            return true;
        });
        def(vm, "reversed", a -> {
            arity("reversed", a, 1, 1);
            List<Object> l = new ArrayList<>(Ops.toList(a[0]));
            Collections.reverse(l);
            return new PList(l);
        });
        def(vm, "divmod", a -> {
            arity("divmod", a, 2, 2);
            PList out = new PList();
            out.add(Ops.binary(Code.FLOORDIV, a[0], a[1]));
            out.add(Ops.binary(Code.MOD, a[0], a[1]));
            return out;
        });
        def(vm, "hex", a -> {
            arity("hex", a, 1, 1);
            long v = Value.integer(a[0]);
            return (v < 0 ? "-0x" : "0x") + Long.toHexString(Math.abs(v));
        });
        def(vm, "bin", a -> {
            arity("bin", a, 1, 1);
            long v = Value.integer(a[0]);
            return (v < 0 ? "-0b" : "0b") + Long.toBinaryString(Math.abs(v));
        });
        def(vm, "repr", a -> {
            arity("repr", a, 1, 1);
            return Value.repr(a[0]);
        });
        def(vm, "format", a -> {
            arity("format", a, 1, 2);
            return Value.format(a[0], a.length > 1 ? Value.str(a[1]) : "");
        });
        def(vm, "isinstance", a -> {
            arity("isinstance", a, 2, 2);
            String type = Value.typeName(a[0]);
            List<Object> names = a[1] instanceof PList l ? l.items : List.of(a[1]);
            for (Object n : names) {
                String name = Value.str(n);
                if (name.equals(type) || name.equals("number") && Value.isNumber(a[0]) && !(a[0] instanceof Boolean)) {
                    return true;
                }
            }
            return false;
        });
        def(vm, "help", a -> {
            arity("help", a, 0, 1);
            help(vm.host(), a.length == 0 ? "" : Value.str(a[0]).trim());
            return Value.NONE;
        });
        vm.define("math", MathLib.module());
        vm.define("json", JsonLib.module());
    }

    /** help() : categories ; help("categorie") : ses entrees ; help("nom") : signature, description, exemple (textes traduits chez le joueur). */
    static void help(Vm.Host host, String what) {
        if (what.isEmpty()) {
            host.doc("pioscript.help.intro");
            for (String c : GeneratedPioDoc.CATEGORIES) {
                host.doc("pioscript.cat." + c + ".help", c);
            }
            return;
        }
        for (String c : GeneratedPioDoc.CATEGORIES) {
            if (c.equalsIgnoreCase(what)) {
                List<String> names = new ArrayList<>();
                for (String[] e : GeneratedPioDoc.ENTRIES) {
                    if (e[0].equals(c)) {
                        names.add(e[1]);
                    }
                }
                host.doc("pioscript.cat." + c + ".help", String.join(", ", names));
                return;
            }
        }
        String[] found = null;
        for (String[] e : GeneratedPioDoc.ENTRIES) {
            if (e[1].equals(what)) {
                found = e;
                break;
            }
        }
        if (found == null) {
            //Nom court : « sqrt » pour math.sqrt, « upper » pour str.upper
            for (String[] e : GeneratedPioDoc.ENTRIES) {
                if (e[1].endsWith("." + what)) {
                    found = e;
                    break;
                }
            }
        }
        if (found == null) {
            host.doc("pioscript.help.unknown", what);
            return;
        }
        host.doc("pioscript.doc." + found[1] + ".sig");
        host.doc("pioscript.doc." + found[1] + ".desc");
        if (!found[3].isEmpty()) {
            host.doc("pioscript.help.example", found[3]);
        }
    }

    private static Object extreme(String name, Object[] a, int sign) {
        List<Object> items = a.length == 1 ? Ops.toList(a[0]) : List.of(a);
        if (items.isEmpty()) {
            throw new ScriptError("empty", name);
        }
        Object best = items.get(0);
        for (Object o : items) {
            if (Ops.order(o, best) * sign > 0) {
                best = o;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Attributs et methodes
    // ------------------------------------------------------------------

    private static Native m(String name, Value.Fn fn) {
        return new Native(name, fn);
    }

    static Object attribute(Vm vm, Object obj, String name) {
        if (obj instanceof PObject o) {
            Object v = o.attrs.get(name);
            if (v == null) {
                throw new ScriptError("attribute", o.type, name);
            }
            return v;
        }
        if (obj instanceof String s) {
            return stringMethod(s, name);
        }
        if (obj instanceof PList l) {
            return listMethod(l, name);
        }
        if (obj instanceof PDict d) {
            return dictMethod(d, name);
        }
        throw new ScriptError("attribute", Value.typeName(obj), name);
    }

    private static Object stringMethod(String s, String name) {
        return switch (name) {
            case "upper" -> m(name, a -> s.toUpperCase());
            case "lower" -> m(name, a -> s.toLowerCase());
            case "strip" -> m(name, a -> s.strip());
            case "lstrip" -> m(name, a -> s.stripLeading());
            case "rstrip" -> m(name, a -> s.stripTrailing());
            case "split" -> m(name, a -> {
                PList out = new PList();
                if (a.length == 0) {
                    for (String part : s.trim().split("\\s+")) {
                        if (!part.isEmpty()) {
                            out.add(part);
                        }
                    }
                } else {
                    String sep = Value.str(a[0]);
                    if (sep.isEmpty()) {
                        throw new ScriptError("empty", "split");
                    }
                    int from = 0, at;
                    while ((at = s.indexOf(sep, from)) >= 0) {
                        out.add(s.substring(from, at));
                        from = at + sep.length();
                    }
                    out.add(s.substring(from));
                }
                return out;
            });
            case "join" -> m(name, a -> {
                arity("join", a, 1, 1);
                StringBuilder b = new StringBuilder();
                boolean first = true;
                for (Object o : Ops.toList(a[0])) {
                    if (!first) {
                        b.append(s);
                    }
                    first = false;
                    b.append(Value.str(o));
                    if (b.length() > Value.MAX_STRING) {
                        throw new ScriptError("too_big");
                    }
                }
                return b.toString();
            });
            case "replace" -> m(name, a -> {
                arity("replace", a, 2, 2);
                String r = s.replace(Value.str(a[0]), Value.str(a[1]));
                if (r.length() > Value.MAX_STRING) {
                    throw new ScriptError("too_big");
                }
                return r;
            });
            case "startswith" -> m(name, a -> s.startsWith(Value.str(a[0])));
            case "endswith" -> m(name, a -> s.endsWith(Value.str(a[0])));
            case "find" -> m(name, a -> (long) s.indexOf(Value.str(a[0])));
            case "count" -> m(name, a -> {
                String sub = Value.str(a[0]);
                if (sub.isEmpty()) {
                    return (long) s.length() + 1;
                }
                long n = 0;
                int from = 0, at;
                while ((at = s.indexOf(sub, from)) >= 0) {
                    n++;
                    from = at + sub.length();
                }
                return n;
            });
            case "isdigit" -> m(name, a -> !s.isEmpty() && s.chars().allMatch(Character::isDigit));
            //0.20.41
            case "isalpha" -> m(name, a -> !s.isEmpty() && s.chars().allMatch(Character::isLetter));
            case "isalnum" -> m(name, a -> !s.isEmpty() && s.chars().allMatch(Character::isLetterOrDigit));
            case "isspace" -> m(name, a -> !s.isEmpty() && s.chars().allMatch(Character::isWhitespace));
            case "isupper" -> m(name, a -> s.chars().anyMatch(Character::isLetter) && s.equals(s.toUpperCase()));
            case "islower" -> m(name, a -> s.chars().anyMatch(Character::isLetter) && s.equals(s.toLowerCase()));
            case "title" -> m(name, a -> {
                StringBuilder b = new StringBuilder(s.length());
                boolean start = true;
                for (char c : s.toCharArray()) {
                    b.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
                    start = !Character.isLetter(c);
                }
                return b.toString();
            });
            case "capitalize" -> m(name, a -> s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase());
            case "splitlines" -> m(name, a -> {
                PList out = new PList();
                if (!s.isEmpty()) {
                    for (String l : s.split("\r?\n", -1)) {
                        out.add(l);
                    }
                    if (s.endsWith("\n")) {
                        out.items.remove(out.items.size() - 1);
                    }
                }
                return out;
            });
            case "rfind" -> m(name, a -> (long) s.lastIndexOf(Value.str(a[0])));
            case "partition" -> m(name, a -> {
                arity("partition", a, 1, 1);
                String sep = Value.str(a[0]);
                if (sep.isEmpty()) {
                    throw new ScriptError("empty", "partition");
                }
                int at = s.indexOf(sep);
                PList out = new PList();
                out.add(at < 0 ? s : s.substring(0, at));
                out.add(at < 0 ? "" : sep);
                out.add(at < 0 ? "" : s.substring(at + sep.length()));
                return out;
            });
            case "ljust", "rjust", "center" -> m(name, a -> {
                arity(name, a, 1, 2);
                int w = (int) Math.min(Value.MAX_STRING, Value.integer(a[0]));
                String fill = a.length > 1 ? Value.str(a[1]) : " ";
                if (fill.length() != 1) {
                    throw new ScriptError("convert", fill, "char");
                }
                int pad = w - s.length();
                if (pad <= 0) {
                    return s;
                }
                return switch (name) {
                    case "ljust" -> s + fill.repeat(pad);
                    case "rjust" -> fill.repeat(pad) + s;
                    default -> fill.repeat(pad / 2) + s + fill.repeat(pad - pad / 2);
                };
            });
            case "zfill" -> m(name, a -> {
                int w = (int) Math.min(Value.MAX_STRING, Value.integer(a[0]));
                return s.length() >= w ? s : "0".repeat(w - s.length()) + s;
            });
            default -> throw new ScriptError("attribute", "str", name);
        };
    }

    private static Object listMethod(PList l, String name) {
        List<Object> items = l.items;
        return switch (name) {
            case "append" -> m(name, a -> {
                arity("append", a, 1, 1);
                l.add(a[0]);
                return Value.NONE;
            });
            case "extend" -> m(name, a -> {
                arity("extend", a, 1, 1);
                for (Object o : new ArrayList<>(Ops.toList(a[0]))) {
                    l.add(o);
                }
                return Value.NONE;
            });
            case "insert" -> m(name, a -> {
                arity("insert", a, 2, 2);
                if (items.size() >= Value.MAX_ITEMS) {
                    throw new ScriptError("too_big");
                }
                long i = Value.integer(a[0]);
                if (i < 0) {
                    i += items.size();
                }
                items.add((int) Math.max(0, Math.min(items.size(), i)), a[1]);
                return Value.NONE;
            });
            case "pop" -> m(name, a -> {
                arity("pop", a, 0, 1);
                if (items.isEmpty()) {
                    throw new ScriptError("empty", "pop");
                }
                long i = a.length > 0 ? Value.integer(a[0]) : -1;
                if (i < 0) {
                    i += items.size();
                }
                if (i < 0 || i >= items.size()) {
                    throw new ScriptError("index", i, items.size());
                }
                return items.remove((int) i);
            });
            case "remove" -> m(name, a -> {
                arity("remove", a, 1, 1);
                for (int i = 0; i < items.size(); i++) {
                    if (Value.equal(items.get(i), a[0])) {
                        items.remove(i);
                        return Value.NONE;
                    }
                }
                throw new ScriptError("not_found", Value.repr(a[0]));
            });
            case "index" -> m(name, a -> {
                arity("index", a, 1, 1);
                for (int i = 0; i < items.size(); i++) {
                    if (Value.equal(items.get(i), a[0])) {
                        return (long) i;
                    }
                }
                throw new ScriptError("not_found", Value.repr(a[0]));
            });
            case "count" -> m(name, a -> {
                arity("count", a, 1, 1);
                return items.stream().filter(o -> Value.equal(o, a[0])).count();
            });
            case "reverse" -> m(name, a -> {
                Collections.reverse(items);
                return Value.NONE;
            });
            case "sort" -> m(name, a -> {
                items.sort(Ops::order);
                if (a.length > 0 && Value.truthy(a[0])) {
                    Collections.reverse(items);
                }
                return Value.NONE;
            });
            case "clear" -> m(name, a -> {
                items.clear();
                return Value.NONE;
            });
            case "copy" -> m(name, a -> new PList(new ArrayList<>(items)));
            default -> throw new ScriptError("attribute", "list", name);
        };
    }

    private static Object dictMethod(PDict d, String name) {
        return switch (name) {
            case "keys" -> m(name, a -> new PList(new ArrayList<>(d.map.keySet())));
            case "values" -> m(name, a -> new PList(new ArrayList<>(d.map.values())));
            case "items" -> m(name, a -> {
                PList out = new PList();
                for (Map.Entry<Object, Object> e : d.map.entrySet()) {
                    PList pair = new PList();
                    pair.add(e.getKey());
                    pair.add(e.getValue());
                    out.add(pair);
                }
                return out;
            });
            case "get" -> m(name, a -> {
                arity("get", a, 1, 2);
                Object v = d.get(a[0]);
                return v != null ? v : a.length > 1 ? a[1] : Value.NONE;
            });
            case "pop" -> m(name, a -> {
                arity("pop", a, 1, 2);
                Object v = d.map.remove(PDict.key(a[0]));
                if (v == null) {
                    if (a.length > 1) {
                        return a[1];
                    }
                    throw new ScriptError("key", Value.repr(a[0]));
                }
                return v;
            });
            case "update" -> m(name, a -> {
                arity("update", a, 1, 1);
                if (!(a[0] instanceof PDict other)) {
                    throw new ScriptError("operand", "update", Value.typeName(a[0]), "dict");
                }
                for (Map.Entry<Object, Object> e : new ArrayList<>(other.map.entrySet())) {
                    d.put(e.getKey(), e.getValue());
                }
                return Value.NONE;
            });
            case "clear" -> m(name, a -> {
                d.map.clear();
                return Value.NONE;
            });
            case "setdefault" -> m(name, a -> {
                arity("setdefault", a, 1, 2);
                Object v = d.get(a[0]);
                if (v == null) {
                    v = a.length > 1 ? a[1] : Value.NONE;
                    d.put(a[0], v);
                }
                return v;
            });
            case "copy" -> m(name, a -> {
                PDict c = new PDict();
                c.map.putAll(d.map);
                return c;
            });
            default -> throw new ScriptError("attribute", "dict", name);
        };
    }
}

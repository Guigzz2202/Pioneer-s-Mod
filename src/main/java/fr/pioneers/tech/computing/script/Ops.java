package fr.pioneers.tech.computing.script;

import fr.pioneers.tech.computing.script.Value.Iter;
import fr.pioneers.tech.computing.script.Value.PDict;
import fr.pioneers.tech.computing.script.Value.PList;
import fr.pioneers.tech.computing.script.Value.PRange;
import java.util.ArrayList;
import java.util.List;

/** Operateurs PioScript (0.17.1) : arithmetique a la Python (entiers qui deviennent flottants en cas de debordement), comparaisons, indices. */
final class Ops {

    private Ops() {
    }

    static Object binary(int op, Object a, Object b) {
        if (op == Code.ADD) {
            if (a instanceof String x && b instanceof String y) {
                return cap(x.length() + y.length(), x + y);
            }
            if (a instanceof PList x && b instanceof PList y) {
                if (x.items.size() + y.items.size() > Value.MAX_ITEMS) {
                    throw new ScriptError("too_big");
                }
                List<Object> l = new ArrayList<>(x.items);
                l.addAll(y.items);
                return new PList(l);
            }
        }
        if (op == Code.MUL) {
            if (a instanceof String s && isInt(b) || b instanceof String && isInt(a)) {
                String s = a instanceof String x ? x : (String) b;
                long n = Math.max(0, Value.integer(a instanceof String ? b : a));
                if (s.length() * n > Value.MAX_STRING) {
                    throw new ScriptError("too_big");
                }
                return s.repeat((int) n);
            }
            if (a instanceof PList l && isInt(b) || b instanceof PList && isInt(a)) {
                PList src = a instanceof PList x ? x : (PList) b;
                long n = Math.max(0, Value.integer(a instanceof PList ? b : a));
                if (src.items.size() * n > Value.MAX_ITEMS) {
                    throw new ScriptError("too_big");
                }
                List<Object> out = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    out.addAll(src.items);
                }
                return new PList(out);
            }
        }
        if (!Value.isNumber(a) || !Value.isNumber(b)) {
            throw new ScriptError("operand", symbol(op), Value.typeName(a), Value.typeName(b));
        }
        boolean ints = isInt(a) && isInt(b);
        if (ints) {
            long x = Value.integer(a), y = Value.integer(b);
            switch (op) {
                case Code.ADD -> {
                    long r = x + y;
                    return ((x ^ r) & (y ^ r)) < 0 ? (Object) ((double) x + y) : (Object) r;
                }
                case Code.SUB -> {
                    long r = x - y;
                    return ((x ^ y) & (x ^ r)) < 0 ? (Object) ((double) x - y) : (Object) r;
                }
                case Code.MUL -> {
                    long hi = Math.multiplyHigh(x, y), r = x * y;
                    return (hi == 0 && r >= 0) || (hi == -1 && r < 0) ? (Object) r : (Object) ((double) x * y);
                }
                case Code.FLOORDIV -> {
                    if (y == 0) {
                        throw new ScriptError("zero");
                    }
                    return Math.floorDiv(x, y);
                }
                case Code.MOD -> {
                    if (y == 0) {
                        throw new ScriptError("zero");
                    }
                    return Math.floorMod(x, y);
                }
                case Code.POW -> {
                    if (y >= 0) {
                        double d = Math.pow(x, y);
                        if (Math.abs(d) < 9e18) {
                            //Exponentiation rapide (au plus 64 tours)
                            long r = 1, base = x, e = y;
                            while (e > 0) {
                                if ((e & 1) == 1) {
                                    r *= base;
                                }
                                e >>= 1;
                                if (e > 0) {
                                    base *= base;
                                }
                            }
                            return r;
                        }
                        return d;
                    }
                    return Math.pow(x, y);
                }
                default -> {
                }
            }
        }
        double x = Value.num(a), y = Value.num(b);
        return switch (op) {
            case Code.ADD -> x + y;
            case Code.SUB -> x - y;
            case Code.MUL -> x * y;
            case Code.DIV -> {
                if (y == 0) {
                    throw new ScriptError("zero");
                }
                yield x / y;
            }
            case Code.FLOORDIV -> {
                if (y == 0) {
                    throw new ScriptError("zero");
                }
                yield Math.floor(x / y);
            }
            case Code.MOD -> {
                if (y == 0) {
                    throw new ScriptError("zero");
                }
                yield x - Math.floor(x / y) * y;
            }
            case Code.POW -> Math.pow(x, y);
            default -> throw new ScriptError("internal", "binop");
        };
    }

    private static boolean isInt(Object v) {
        return v instanceof Long || v instanceof Boolean;
    }

    private static String cap(int length, String s) {
        if (length > Value.MAX_STRING) {
            throw new ScriptError("too_big");
        }
        return s;
    }

    private static String symbol(int op) {
        return switch (op) {
            case Code.ADD -> "+";
            case Code.SUB -> "-";
            case Code.MUL -> "*";
            case Code.DIV -> "/";
            case Code.FLOORDIV -> "//";
            case Code.MOD -> "%";
            default -> "**";
        };
    }

    static Object compare(int op, Object a, Object b) {
        return switch (op) {
            case Code.EQ -> Value.equal(a, b);
            case Code.NE -> !Value.equal(a, b);
            case Code.IS -> a == b || Value.isNumber(a) && Value.isNumber(b) && a.getClass() == b.getClass() && a.equals(b);
            case Code.IS_NOT -> !(a == b || Value.isNumber(a) && Value.isNumber(b) && a.getClass() == b.getClass() && a.equals(b));
            case Code.IN -> contains(b, a);
            case Code.NOT_IN -> !contains(b, a);
            default -> {
                int c = order(a, b);
                yield switch (op) {
                    case Code.LT -> c < 0;
                    case Code.LE -> c <= 0;
                    case Code.GT -> c > 0;
                    default -> c >= 0;
                };
            }
        };
    }

    static int order(Object a, Object b) {
        if (Value.isNumber(a) && Value.isNumber(b)) {
            return Double.compare(Value.num(a), Value.num(b));
        }
        if (a instanceof String x && b instanceof String y) {
            return x.compareTo(y);
        }
        if (a instanceof PList x && b instanceof PList y) {
            for (int i = 0; i < Math.min(x.items.size(), y.items.size()); i++) {
                int c = order(x.items.get(i), y.items.get(i));
                if (c != 0) {
                    return c;
                }
            }
            return Integer.compare(x.items.size(), y.items.size());
        }
        throw new ScriptError("order", Value.typeName(a), Value.typeName(b));
    }

    static boolean contains(Object container, Object v) {
        if (container instanceof String s) {
            if (!(v instanceof String x)) {
                throw new ScriptError("operand", "in", Value.typeName(v), "str");
            }
            return s.contains(x);
        }
        if (container instanceof PList l) {
            for (Object o : l.items) {
                if (Value.equal(o, v)) {
                    return true;
                }
            }
            return false;
        }
        if (container instanceof PDict d) {
            return d.map.containsKey(PDict.key(v));
        }
        if (container instanceof PRange r) {
            if (!Value.isNumber(v)) {
                return false;
            }
            double x = Value.num(v);
            if (x != Math.rint(x)) {
                return false;
            }
            long n = (long) x;
            boolean inside = r.step() > 0 ? n >= r.start() && n < r.stop() : n <= r.start() && n > r.stop();
            return inside && Math.floorMod(n - r.start(), r.step()) == 0;
        }
        throw new ScriptError("not_container", Value.typeName(container));
    }

    private static int index(long i, int size) {
        long j = i < 0 ? i + size : i;
        if (j < 0 || j >= size) {
            throw new ScriptError("index", i, size);
        }
        return (int) j;
    }

    static Object getItem(Object obj, Object key) {
        if (obj instanceof PList l) {
            return l.items.get(index(Value.integer(key), l.items.size()));
        }
        if (obj instanceof String s) {
            int i = index(Value.integer(key), s.length());
            return s.substring(i, i + 1);
        }
        if (obj instanceof PDict d) {
            Object v = d.get(key);
            if (v == null) {
                throw new ScriptError("key", Value.repr(key));
            }
            return v;
        }
        if (obj instanceof PRange r) {
            long n = r.length();
            long i = Value.integer(key);
            if (i < 0) {
                i += n;
            }
            if (i < 0 || i >= n) {
                throw new ScriptError("index", i, n);
            }
            return r.start() + i * r.step();
        }
        throw new ScriptError("not_subscriptable", Value.typeName(obj));
    }

    static void setItem(Object obj, Object key, Object value) {
        if (obj instanceof PList l) {
            l.items.set(index(Value.integer(key), l.items.size()), value);
        } else if (obj instanceof PDict d) {
            d.put(key, value);
        } else {
            throw new ScriptError("not_assignable", Value.typeName(obj));
        }
    }

    static Object slice(Object obj, Object a, Object b) {
        int size = obj instanceof PList l ? l.items.size() : obj instanceof String s ? s.length() : -1;
        if (size < 0) {
            throw new ScriptError("not_subscriptable", Value.typeName(obj));
        }
        int from = bound(a, 0, size), to = bound(b, size, size);
        if (to < from) {
            to = from;
        }
        if (obj instanceof PList l) {
            return new PList(new ArrayList<>(l.items.subList(from, to)));
        }
        return ((String) obj).substring(from, to);
    }

    private static int bound(Object v, int dflt, int size) {
        if (v == Value.NONE) {
            return dflt;
        }
        long i = Value.integer(v);
        if (i < 0) {
            i += size;
        }
        return (int) Math.max(0, Math.min(size, i));
    }

    static Iter iter(Object v) {
        if (v instanceof PList || v instanceof String || v instanceof PRange) {
            return new Iter(v);
        }
        if (v instanceof PDict d) {
            return new Iter(new PList(new ArrayList<>(d.map.keySet())));
        }
        throw new ScriptError("not_iterable", Value.typeName(v));
    }

    /** Element suivant, ou null a la fin. */
    static Object next(Iter it) {
        Object s = it.source;
        long i = it.index++;
        if (s instanceof PList l) {
            return i < l.items.size() ? l.items.get((int) i) : null;
        }
        if (s instanceof String str) {
            return i < str.length() ? str.substring((int) i, (int) i + 1) : null;
        }
        PRange r = (PRange) s;
        return i < r.length() ? (Object) (r.start() + i * r.step()) : null;
    }

    static List<Object> toList(Object v) {
        if (v instanceof PList l) {
            return l.items;
        }
        Iter it = iter(v);
        List<Object> out = new ArrayList<>();
        Object o;
        while ((o = next(it)) != null) {
            if (out.size() >= Value.MAX_ITEMS) {
                throw new ScriptError("too_big");
            }
            out.add(o);
        }
        return out;
    }
}

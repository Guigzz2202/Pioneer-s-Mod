package fr.pioneers.computing.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Valeurs PioScript (0.17.1) : entiers (Long), flottants (Double), chaines (String), booleens (Boolean), None ({@link #NONE}), listes
 * ({@link PList}), dictionnaires ({@link PDict}), intervalles ({@link PRange}), fonctions ({@link PFunction}), fonctions natives
 * ({@link Native}), modules et objets natifs ({@link PObject}). Fonctions utilitaires : verite, egalite, representation, conversions.
 */
public final class Value {

    /** Taille maximale d'une liste, d'un dictionnaire ou d'une chaine. */
    public static final int MAX_ITEMS = 16_384, MAX_STRING = 65_536;

    public static final Object NONE = new Object() {
        @Override
        public String toString() {
            return "None";
        }
    };

    private Value() {
    }

    public static final class PList {
        public final List<Object> items;

        public PList() {
            items = new ArrayList<>();
        }

        public PList(List<Object> items) {
            this.items = items;
        }

        public void add(Object v) {
            if (items.size() >= MAX_ITEMS) {
                throw new ScriptError("too_big");
            }
            items.add(v);
        }
    }

    public static final class PDict {
        public final Map<Object, Object> map = new LinkedHashMap<>();

        public void put(Object k, Object v) {
            if (map.size() >= MAX_ITEMS && !map.containsKey(key(k))) {
                throw new ScriptError("too_big");
            }
            map.put(key(k), v);
        }

        public Object get(Object k) {
            return map.get(key(k));
        }

        /** Cle normalisee : flottant entier -> entier ; seules les valeurs immuables sont des cles. */
        public static Object key(Object k) {
            if (k instanceof Double d && d == Math.rint(d) && Math.abs(d) < 9e15) {
                return d.longValue();
            }
            if (k instanceof Long || k instanceof String || k instanceof Boolean || k instanceof Double || k == NONE) {
                return k;
            }
            throw new ScriptError("bad_key", typeName(k));
        }
    }

    public record PRange(long start, long stop, long step) {
        public long length() {
            if (step > 0) {
                return Math.max(0, (stop - start + step - 1) / step);
            }
            return Math.max(0, (start - stop - step - 1) / -step);
        }
    }

    /** Prototype de fonction (constante du bytecode) : code et nombre de valeurs par defaut. */
    public record FnProto(Code code, int defaults) {
    }

    public record PFunction(Code code, Object[] defaults) {
    }

    /** Fonction native : recoit les arguments, renvoie une valeur (ou {@link Yield} pour suspendre le programme). */
    @FunctionalInterface
    public interface Fn {
        Object call(Object[] args);
    }

    public record Native(String name, Fn fn) {
    }

    /** Module ou objet natif : attributs (fonctions natives ou valeurs). */
    public static class PObject {
        public final String type;
        public final Map<String, Object> attrs = new LinkedHashMap<>();

        public PObject(String type) {
            this.type = type;
        }

        public PObject fn(String name, Fn fn) {
            attrs.put(name, new Native(type + "." + name, fn));
            return this;
        }
    }

    /** Demande de suspension d'une fonction native : dormir n ticks, ou attendre une ligne de la console. */
    public record Yield(int ticks, boolean input, String prompt) {
    }

    /** Iterateur interne (sur la pile pendant un for). */
    static final class Iter {
        final Object source;
        long index;

        Iter(Object source) {
            this.source = source;
        }
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    public static boolean truthy(Object v) {
        if (v == NONE || v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Long l) {
            return l != 0;
        }
        if (v instanceof Double d) {
            return d != 0;
        }
        if (v instanceof String s) {
            return !s.isEmpty();
        }
        if (v instanceof PList l) {
            return !l.items.isEmpty();
        }
        if (v instanceof PDict d) {
            return !d.map.isEmpty();
        }
        if (v instanceof PRange r) {
            return r.length() > 0;
        }
        return true;
    }

    public static boolean isNumber(Object v) {
        return v instanceof Long || v instanceof Double || v instanceof Boolean;
    }

    public static double num(Object v) {
        if (v instanceof Long l) {
            return l;
        }
        if (v instanceof Double d) {
            return d;
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        throw new ScriptError("not_number", typeName(v));
    }

    public static long integer(Object v) {
        if (v instanceof Long l) {
            return l;
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (v instanceof Double d && d == Math.rint(d)) {
            return d.longValue();
        }
        throw new ScriptError("not_integer", typeName(v));
    }

    public static boolean equal(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (isNumber(a) && isNumber(b)) {
            return num(a) == num(b);
        }
        if (a instanceof PList x && b instanceof PList y) {
            if (x.items.size() != y.items.size()) {
                return false;
            }
            for (int i = 0; i < x.items.size(); i++) {
                if (!equal(x.items.get(i), y.items.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof PDict x && b instanceof PDict y) {
            if (x.map.size() != y.map.size()) {
                return false;
            }
            for (Map.Entry<Object, Object> e : x.map.entrySet()) {
                if (!y.map.containsKey(e.getKey()) || !equal(e.getValue(), y.map.get(e.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        return a != null && a.equals(b);
    }

    public static String typeName(Object v) {
        if (v == NONE || v == null) {
            return "NoneType";
        }
        if (v instanceof Long) {
            return "int";
        }
        if (v instanceof Double) {
            return "float";
        }
        if (v instanceof String) {
            return "str";
        }
        if (v instanceof Boolean) {
            return "bool";
        }
        if (v instanceof PList) {
            return "list";
        }
        if (v instanceof PDict) {
            return "dict";
        }
        if (v instanceof PRange) {
            return "range";
        }
        if (v instanceof PFunction || v instanceof Native) {
            return "function";
        }
        if (v instanceof PObject o) {
            return o.type;
        }
        return "object";
    }

    /** str(v) */
    public static String str(Object v) {
        if (v instanceof String s) {
            return s;
        }
        return repr(v, 0);
    }

    public static String repr(Object v) {
        return repr(v, 0);
    }

    private static String repr(Object v, int depth) {
        if (depth > 16) {
            return "...";
        }
        if (v == NONE || v == null) {
            return "None";
        }
        if (v instanceof Boolean b) {
            return b ? "True" : "False";
        }
        if (v instanceof Long l) {
            return Long.toString(l);
        }
        if (v instanceof Double d) {
            return formatDouble(d);
        }
        if (v instanceof String s) {
            return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'";
        }
        if (v instanceof PList l) {
            StringBuilder b = new StringBuilder("[");
            for (int i = 0; i < l.items.size(); i++) {
                if (i > 0) {
                    b.append(", ");
                }
                b.append(repr(l.items.get(i), depth + 1));
                if (b.length() > MAX_STRING) {
                    break;
                }
            }
            return b.append(']').toString();
        }
        if (v instanceof PDict d) {
            StringBuilder b = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<Object, Object> e : d.map.entrySet()) {
                if (!first) {
                    b.append(", ");
                }
                first = false;
                b.append(repr(e.getKey(), depth + 1)).append(": ").append(repr(e.getValue(), depth + 1));
                if (b.length() > MAX_STRING) {
                    break;
                }
            }
            return b.append('}').toString();
        }
        if (v instanceof PRange r) {
            return "range(" + r.start + ", " + r.stop + (r.step != 1 ? ", " + r.step : "") + ")";
        }
        if (v instanceof PFunction f) {
            return "<function " + f.code().name + ">";
        }
        if (v instanceof Native n) {
            return "<built-in " + n.name() + ">";
        }
        if (v instanceof PObject o) {
            return "<" + o.type + ">";
        }
        return String.valueOf(v);
    }

    public static String formatDouble(double d) {
        if (Double.isNaN(d)) {
            return "nan";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "inf" : "-inf";
        }
        if (d == Math.rint(d) && Math.abs(d) < 1e16) {
            return String.format(Locale.ROOT, "%.1f", d);
        }
        String s = Double.toString(d);
        return s.replace("E", "e");
    }

    /** f-chaine : {x:.2f}, {x:d}, {x:5} (largeur), {x:>8}, {x:<8}. */
    public static String format(Object v, String spec) {
        if (spec.isEmpty()) {
            return str(v);
        }
        String align = "";
        int width = 0;
        String rest = spec;
        if (!rest.isEmpty() && (rest.charAt(0) == '<' || rest.charAt(0) == '>' || rest.charAt(0) == '^')) {
            align = rest.substring(0, 1);
            rest = rest.substring(1);
        }
        int i = 0;
        while (i < rest.length() && Character.isDigit(rest.charAt(i))) {
            i++;
        }
        if (i > 0) {
            width = Math.min(200, Integer.parseInt(rest.substring(0, i)));
        }
        rest = rest.substring(i);
        String body;
        if (rest.startsWith(".") && rest.endsWith("f")) {
            int prec = Math.min(12, Integer.parseInt(rest.substring(1, rest.length() - 1).isEmpty() ? "6" : rest.substring(1, rest.length() - 1)));
            body = String.format(Locale.ROOT, "%." + prec + "f", num(v));
        } else if (rest.equals("d")) {
            body = Long.toString(Math.round(num(v)));
        } else if (rest.equals("%")) {
            body = String.format(Locale.ROOT, "%.0f%%", num(v) * 100);
        } else if (rest.isEmpty()) {
            body = str(v);
        } else {
            throw new ScriptError("bad_format", spec);
        }
        if (body.length() < width) {
            String pad = " ".repeat(width - body.length());
            boolean right = align.equals(">") || align.isEmpty() && isNumber(v);
            body = align.equals("^") ? pad.substring(pad.length() / 2) + body + pad.substring(0, pad.length() / 2) : right ? pad + body : body + pad;
        }
        return body;
    }
}

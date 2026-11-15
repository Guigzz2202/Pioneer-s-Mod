package fr.pioneers.computing.script;

import fr.pioneers.computing.script.Value.PObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 0.20.41 : module {@code math} de PioScript (constantes pi, e, tau, inf ; racines, arrondis, trigonometrie, logarithmes, clamp, lerp,
 * moyenne, mediane...). Documentation : tools/gc/computing/pioscript_doc.py.
 */
final class MathLib {

    private MathLib() {
    }

    private interface D1 {
        double f(double x);
    }

    private static void arity(String name, Object[] a, int min, int max) {
        if (a.length < min || a.length > max) {
            throw new ScriptError("arity", name, min == max ? String.valueOf(min) : min + ".." + max, a.length);
        }
    }

    private static PObject one(PObject o, String name, D1 f) {
        return o.fn(name, a -> {
            arity("math." + name, a, 1, 1);
            return f.f(Value.num(a[0]));
        });
    }

    /** Entier si la valeur l'est exactement (floor, ceil, trunc renvoient des entiers comme en Python). */
    private static Object integral(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new ScriptError("convert", Value.formatDouble(d), "int");
        }
        return (long) d;
    }

    private static List<Double> numbers(String name, Object v) {
        List<Double> out = new ArrayList<>();
        for (Object o : Ops.toList(v)) {
            out.add(Value.num(o));
        }
        if (out.isEmpty()) {
            throw new ScriptError("empty", name);
        }
        return out;
    }

    static PObject module() {
        PObject m = new PObject("math");
        m.attrs.put("pi", Math.PI);
        m.attrs.put("e", Math.E);
        m.attrs.put("tau", Math.PI * 2);
        m.attrs.put("inf", Double.POSITIVE_INFINITY);
        one(m, "sqrt", x -> {
            if (x < 0) {
                throw new ScriptError("convert", Value.formatDouble(x), "sqrt");
            }
            return Math.sqrt(x);
        });
        one(m, "sin", Math::sin);
        one(m, "cos", Math::cos);
        one(m, "tan", Math::tan);
        one(m, "asin", Math::asin);
        one(m, "acos", Math::acos);
        one(m, "atan", Math::atan);
        one(m, "exp", Math::exp);
        one(m, "log10", Math::log10);
        one(m, "degrees", Math::toDegrees);
        one(m, "radians", Math::toRadians);
        m.fn("floor", a -> {
            arity("math.floor", a, 1, 1);
            return integral(Math.floor(Value.num(a[0])));
        });
        m.fn("ceil", a -> {
            arity("math.ceil", a, 1, 1);
            return integral(Math.ceil(Value.num(a[0])));
        });
        m.fn("trunc", a -> {
            arity("math.trunc", a, 1, 1);
            double d = Value.num(a[0]);
            return integral(d < 0 ? Math.ceil(d) : Math.floor(d));
        });
        m.fn("sign", a -> {
            arity("math.sign", a, 1, 1);
            return (long) Math.signum(Value.num(a[0]));
        });
        m.fn("isnan", a -> {
            arity("math.isnan", a, 1, 1);
            return Double.isNaN(Value.num(a[0]));
        });
        m.fn("pow", a -> {
            arity("math.pow", a, 2, 2);
            return Math.pow(Value.num(a[0]), Value.num(a[1]));
        });
        m.fn("atan2", a -> {
            arity("math.atan2", a, 2, 2);
            return Math.atan2(Value.num(a[0]), Value.num(a[1]));
        });
        m.fn("log", a -> {
            arity("math.log", a, 1, 2);
            double x = Value.num(a[0]);
            return a.length == 1 ? Math.log(x) : Math.log(x) / Math.log(Value.num(a[1]));
        });
        m.fn("hypot", a -> {
            double s = 0;
            for (Object o : a) {
                double v = Value.num(o);
                s += v * v;
            }
            return Math.sqrt(s);
        });
        m.fn("clamp", a -> {
            arity("math.clamp", a, 3, 3);
            Object lo = a[1], hi = a[2], x = a[0];
            if (Ops.order(x, lo) < 0) {
                return lo;
            }
            return Ops.order(x, hi) > 0 ? hi : x;
        });
        m.fn("lerp", a -> {
            arity("math.lerp", a, 3, 3);
            double x = Value.num(a[0]), y = Value.num(a[1]), t = Value.num(a[2]);
            return x + (y - x) * t;
        });
        m.fn("gcd", a -> {
            arity("math.gcd", a, 2, 2);
            long x = Math.abs(Value.integer(a[0])), y = Math.abs(Value.integer(a[1]));
            while (y != 0) {
                long t = x % y;
                x = y;
                y = t;
            }
            return x;
        });
        m.fn("mean", a -> {
            arity("math.mean", a, 1, 1);
            double s = 0;
            List<Double> l = numbers("math.mean", a[0]);
            for (double d : l) {
                s += d;
            }
            return s / l.size();
        });
        m.fn("median", a -> {
            arity("math.median", a, 1, 1);
            List<Double> l = numbers("math.median", a[0]);
            l.sort(Double::compare);
            int n = l.size();
            return n % 2 == 1 ? l.get(n / 2) : (l.get(n / 2 - 1) + l.get(n / 2)) / 2;
        });
        return m;
    }
}

package fr.pioneers.computing.script;

import fr.pioneers.computing.script.Value.Iter;
import fr.pioneers.computing.script.Value.Native;
import fr.pioneers.computing.script.Value.PDict;
import fr.pioneers.computing.script.Value.PFunction;
import fr.pioneers.computing.script.Value.PList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Machine virtuelle PioScript (0.17.1) : execute le bytecode d'un {@link Code} par tranches ({@link #run} : au plus n instructions), ce qui
 * permet de partager le temps du serveur entre les ordinateurs. Une fonction native peut suspendre le programme ({@link Value.Yield} :
 * sleep, input). Bornes : pile 1 024 valeurs, 100 appels imbriques, listes / chaines {@link Value#MAX_ITEMS} / {@link Value#MAX_STRING}.
 * Aucune reflexion, aucun acces au systeme : tout passe par les fonctions natives fournies par l'hote ({@link #define}).
 */
public final class Vm {

    public enum State { RUNNING, SLEEPING, INPUT, DONE, ERROR }

    /** Sortie et horloge de l'hote (l'ordinateur, ou le banc de tests). */
    public interface Host {
        void print(String text);

        /** Horloge en ticks (20 par seconde). */
        long now();

        /** 0.20.41 : ligne d'aide traduite chez le joueur (cle de langue + arguments) ; par defaut, la cle seule. */
        default void doc(String key, String... args) {
            print(args.length == 0 ? key : key + " " + String.join(" ", args));
        }
    }

    private static final int MAX_STACK = 1024, MAX_DEPTH = 100;

    private final Host host;
    private final Map<String, Object> globals = new HashMap<>();
    private final Map<String, Object> builtins = new HashMap<>();
    private final Object[] stack = new Object[MAX_STACK];
    private int sp;
    private final List<Frame> frames = new ArrayList<>();
    private State state = State.RUNNING;
    private long wakeAt, started;
    private String prompt = "";
    private ScriptError error;
    private final Random random = new Random();
    private long executed;

    private static final class Frame {
        final Code code;
        final Map<String, Object> locals;
        final int base;
        int pc;

        Frame(Code code, Map<String, Object> locals, int base) {
            this.code = code;
            this.locals = locals;
            this.base = base;
        }
    }

    public Vm(Code module, Host host) {
        this.host = host;
        this.started = host.now();
        frames.add(new Frame(module, globals, 0));
        Builtins.install(this);
    }

    /** Ajoute un nom global fourni par l'hote (module natif, fonction). */
    public void define(String name, Object value) {
        builtins.put(name, value);
    }

    public State state() {
        return state;
    }

    public String prompt() {
        return prompt;
    }

    public ScriptError error() {
        return error;
    }

    /** Instructions executees depuis le lancement. */
    public long executed() {
        return executed;
    }

    Host host() {
        return host;
    }

    Random random() {
        return random;
    }

    /** Secondes depuis le lancement. */
    double elapsed() {
        return (host.now() - started) / 20.0;
    }

    /** Ligne entree a la console pendant un input(). */
    public void provideInput(String line) {
        if (state == State.INPUT) {
            push(line);
            state = State.RUNNING;
        }
    }

    public void stop() {
        if (state != State.ERROR) {
            state = State.DONE;
        }
    }

    // ------------------------------------------------------------------
    // Execution
    // ------------------------------------------------------------------

    /** Execute au plus {@code budget} instructions ; renvoie le nombre execute. */
    public int run(int budget) {
        if (state == State.SLEEPING && host.now() >= wakeAt) {
            state = State.RUNNING;
        }
        int done = 0;
        while (state == State.RUNNING && done < budget) {
            Frame f = frames.get(frames.size() - 1);
            int pc = f.pc++;
            try {
                step(f, f.code.op(pc), f.code.arg(pc));
            } catch (ScriptError e) {
                if (e.line == 0) {
                    e.line = f.code.line(pc);
                }
                fail(e);
            } catch (StackOverflowError | ArithmeticException | IndexOutOfBoundsException | ClassCastException | NullPointerException e) {
                fail(new ScriptError(f.code.line(pc), "internal", e.getClass().getSimpleName()));
            }
            done++;
        }
        executed += done;
        return done;
    }

    private void fail(ScriptError e) {
        error = e;
        state = State.ERROR;
    }

    private void push(Object v) {
        if (sp >= MAX_STACK) {
            throw new ScriptError("stack");
        }
        stack[sp++] = v;
    }

    private Object pop() {
        Object v = stack[--sp];
        stack[sp] = null;
        return v;
    }

    private Object top() {
        return stack[sp - 1];
    }

    private void step(Frame f, int op, int arg) {
        switch (op) {
            case Code.CONST -> push(f.code.consts.get(arg));
            case Code.LOAD -> push(load(f, f.code.names.get(arg)));
            case Code.STORE -> {
                String n = f.code.names.get(arg);
                Object v = pop();
                if (f.locals == globals || f.code.globals.contains(n)) {
                    globals.put(n, v);
                } else {
                    f.locals.put(n, v);
                }
            }
            case Code.POP -> pop();
            case Code.DUP -> push(top());
            case Code.DUP2 -> {
                Object b = stack[sp - 1], a = stack[sp - 2];
                push(a);
                push(b);
            }
            case Code.ROT3 -> {
                Object c = pop(), b = pop(), a = pop();
                push(c);
                push(a);
                push(b);
            }
            case Code.BINOP -> {
                Object b = pop(), a = pop();
                push(Ops.binary(arg, a, b));
            }
            case Code.UNOP -> {
                Object a = pop();
                if (arg == Code.NEG) {
                    push(a instanceof Long l ? (Object) (-l) : a instanceof Boolean bo ? (Object) (bo ? -1L : 0L) : (Object) (-Value.num(a)));
                } else {
                    Value.num(a);
                    push(a instanceof Boolean bo ? (Object) (bo ? 1L : 0L) : a);
                }
            }
            case Code.NOT -> push(!Value.truthy(pop()));
            case Code.CMP -> {
                Object b = pop(), a = pop();
                push(Ops.compare(arg, a, b));
            }
            case Code.JMP -> f.pc = arg;
            case Code.JF -> {
                if (!Value.truthy(pop())) {
                    f.pc = arg;
                }
            }
            case Code.JF_KEEP -> {
                if (!Value.truthy(top())) {
                    f.pc = arg;
                } else {
                    pop();
                }
            }
            case Code.JT_KEEP -> {
                if (Value.truthy(top())) {
                    f.pc = arg;
                } else {
                    pop();
                }
            }
            case Code.CALL -> call(arg);
            case Code.RET -> {
                Object result = pop();
                frames.remove(frames.size() - 1);
                while (sp > f.base) {
                    pop();
                }
                if (frames.isEmpty()) {
                    state = State.DONE;
                    frames.add(f);
                    return;
                }
                push(result);
            }
            case Code.LIST -> {
                PList l = new PList();
                Object[] items = new Object[arg];
                for (int i = arg - 1; i >= 0; i--) {
                    items[i] = pop();
                }
                for (Object o : items) {
                    l.add(o);
                }
                push(l);
            }
            case Code.DICT -> {
                Object[] kv = new Object[arg * 2];
                for (int i = arg * 2 - 1; i >= 0; i--) {
                    kv[i] = pop();
                }
                PDict d = new PDict();
                for (int i = 0; i < arg; i++) {
                    d.put(kv[i * 2], kv[i * 2 + 1]);
                }
                push(d);
            }
            case Code.GETATTR -> push(Builtins.attribute(this, pop(), f.code.names.get(arg)));
            case Code.GETITEM -> {
                Object key = pop(), obj = pop();
                push(Ops.getItem(obj, key));
            }
            case Code.SETITEM -> {
                Object key = pop(), obj = pop(), value = pop();
                Ops.setItem(obj, key, value);
            }
            case Code.SLICE -> {
                Object b = pop(), a = pop(), obj = pop();
                push(Ops.slice(obj, a, b));
            }
            case Code.ITER -> push(Ops.iter(pop()));
            case Code.FOR -> {
                Iter it = (Iter) top();
                Object next = Ops.next(it);
                if (next == null) {
                    pop();
                    f.pc = arg;
                } else {
                    push(next);
                }
            }
            case Code.UNPACK -> {
                Object seq = pop();
                List<Object> items = Ops.toList(seq);
                if (items.size() != arg) {
                    throw new ScriptError("unpack", arg, items.size());
                }
                for (int i = items.size() - 1; i >= 0; i--) {
                    push(items.get(i));
                }
            }
            case Code.MAKEFN -> {
                Value.FnProto proto = (Value.FnProto) f.code.consts.get(arg);
                Object[] defaults = new Object[proto.defaults()];
                for (int i = defaults.length - 1; i >= 0; i--) {
                    defaults[i] = pop();
                }
                push(new PFunction(proto.code(), defaults));
            }
            case Code.CONCAT -> {
                String[] parts = new String[arg];
                int length = 0;
                for (int i = arg - 1; i >= 0; i--) {
                    parts[i] = Value.str(pop());
                    length += parts[i].length();
                }
                if (length > Value.MAX_STRING) {
                    throw new ScriptError("too_big");
                }
                push(String.join("", parts));
            }
            case Code.FORMAT -> push(Value.format(pop(), f.code.names.get(arg)));
            default -> throw new ScriptError("internal", "op " + op);
        }
    }

    private Object load(Frame f, String name) {
        if (f.locals != globals && !f.code.globals.contains(name)) {
            Object v = f.locals.get(name);
            if (v != null) {
                return v;
            }
        }
        Object v = globals.get(name);
        if (v != null) {
            return v;
        }
        v = builtins.get(name);
        if (v != null) {
            return v;
        }
        throw new ScriptError("name", name);
    }

    private void call(int argc) {
        Object[] args = new Object[argc];
        for (int i = argc - 1; i >= 0; i--) {
            args[i] = pop();
        }
        Object callee = pop();
        if (callee instanceof PFunction fn) {
            Code c = fn.code();
            int n = c.params.size(), d = fn.defaults().length;
            if (argc > n || argc < n - d) {
                throw new ScriptError("arity", c.name, n, argc);
            }
            if (frames.size() >= MAX_DEPTH) {
                throw new ScriptError("recursion");
            }
            Map<String, Object> locals = new HashMap<>();
            for (int i = 0; i < n; i++) {
                locals.put(c.params.get(i), i < argc ? args[i] : fn.defaults()[i - (n - d)]);
            }
            frames.add(new Frame(c, locals, sp));
            return;
        }
        if (callee instanceof Native nat) {
            Object result = nat.fn().call(args);
            if (result instanceof Value.Yield y) {
                if (y.input()) {
                    prompt = y.prompt();
                    state = State.INPUT;
                    return;
                }
                wakeAt = host.now() + Math.max(1, y.ticks());
                state = State.SLEEPING;
                push(Value.NONE);
                return;
            }
            push(result == null ? Value.NONE : result);
            return;
        }
        throw new ScriptError("not_callable", Value.typeName(callee));
    }

    // ------------------------------------------------------------------
    // Aides pour les fonctions natives
    // ------------------------------------------------------------------

}

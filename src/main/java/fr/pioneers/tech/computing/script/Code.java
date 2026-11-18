package fr.pioneers.tech.computing.script;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bytecode d'un module ou d'une fonction PioScript (0.17.1) : instructions (code operateur + argument), constantes, noms, ligne de chaque
 * instruction (messages d'erreur), parametres et noms declares {@code global}.
 */
public final class Code {

    // codes operateurs (argument entre parentheses)
    public static final int CONST = 0, LOAD = 1, STORE = 2, POP = 3, DUP = 4, BINOP = 5, UNOP = 6, CMP = 7, JMP = 8, JF = 9, JF_KEEP = 10,
          JT_KEEP = 11, CALL = 12, RET = 13, LIST = 14, DICT = 15, GETATTR = 16, GETITEM = 17, SETITEM = 18, ITER = 19, FOR = 20, UNPACK = 21,
          MAKEFN = 22, CONCAT = 23, SLICE = 24, DUP2 = 25, ROT3 = 26, FORMAT = 27, NOT = 28;
    // operateurs binaires (BINOP) et comparaisons (CMP)
    public static final int ADD = 0, SUB = 1, MUL = 2, DIV = 3, FLOORDIV = 4, MOD = 5, POW = 6;
    public static final int NEG = 0, POS = 1;
    public static final int EQ = 0, NE = 1, LT = 2, LE = 3, GT = 4, GE = 5, IN = 6, NOT_IN = 7, IS = 8, IS_NOT = 9;

    public final String name;
    public final List<String> params = new ArrayList<>();
    public final Set<String> globals = new HashSet<>();
    final List<Object> consts = new ArrayList<>();
    final List<String> names = new ArrayList<>();
    private int[] ops = new int[64];
    private int[] args = new int[64];
    private int[] lines = new int[64];
    private int size;

    public Code(String name) {
        this.name = name;
    }

    public int emit(int op, int arg, int line) {
        if (size == ops.length) {
            ops = Arrays.copyOf(ops, size * 2);
            args = Arrays.copyOf(args, size * 2);
            lines = Arrays.copyOf(lines, size * 2);
        }
        ops[size] = op;
        args[size] = arg;
        lines[size] = line;
        return size++;
    }

    public void patch(int at, int arg) {
        args[at] = arg;
    }

    public int here() {
        return size;
    }

    public int constant(Object value) {
        consts.add(value);
        return consts.size() - 1;
    }

    public int name(String n) {
        int i = names.indexOf(n);
        if (i >= 0) {
            return i;
        }
        names.add(n);
        return names.size() - 1;
    }

    int op(int pc) {
        return ops[pc];
    }

    int arg(int pc) {
        return args[pc];
    }

    int line(int pc) {
        return pc >= 0 && pc < size ? lines[pc] : 0;
    }

    int size() {
        return size;
    }
}

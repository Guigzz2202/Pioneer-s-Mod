package fr.pioneers.computing.script;

import java.util.ArrayList;
import java.util.List;

/**
 * Parseur et compilateur PioScript (0.17.1) : descente recursive sur les jetons du {@link Lexer}, bytecode emis directement dans un
 * {@link Code}. Sous-ensemble de Python : affectations (simples, multiples, indicees, augmentees), if / elif / else, while, for ... in,
 * def / return (parametres avec valeurs par defaut), break, continue, pass, global ; expressions : or, and, not, comparaisons (chainees),
 * in, is, operateurs arithmetiques, puissance, unaires, appels, attributs, indices et tranches, listes, dictionnaires, tuples (listes),
 * f-chaines ({@code {x}} et {@code {x:.2f}}), expression conditionnelle {@code a if c else b}.
 */
public final class Compiler {

    /** Taille maximale d'un programme (caracteres). */
    public static final int MAX_SOURCE = 65_536;

    private final List<Token> toks;
    private int p;
    private Code code;
    private final List<Loop> loops = new ArrayList<>();
    private boolean inFunction;

    private static final class Loop {
        final boolean isFor;
        final int start;
        final List<Integer> breaks = new ArrayList<>();

        Loop(boolean isFor, int start) {
            this.isFor = isFor;
            this.start = start;
        }
    }

    private Compiler(List<Token> toks) {
        this.toks = toks;
    }

    public static Code compile(String source) {
        if (source.length() > MAX_SOURCE) {
            throw new ScriptError("too_long");
        }
        Compiler c = new Compiler(Lexer.lex(source));
        c.code = new Code("<module>");
        while (c.peek().type() != Token.Type.EOF) {
            if (c.peek().type() == Token.Type.NEWLINE) {
                c.p++;
                continue;
            }
            c.statement();
        }
        c.emit(Code.CONST, c.code.constant(Value.NONE));
        c.emit(Code.RET, 0);
        return c.code;
    }

    // ------------------------------------------------------------------
    // Jetons
    // ------------------------------------------------------------------

    private Token peek() {
        return toks.get(p);
    }

    private Token peek(int k) {
        return toks.get(Math.min(toks.size() - 1, p + k));
    }

    private Token next() {
        return toks.get(p++);
    }

    private int line() {
        return peek().line();
    }

    private boolean acceptOp(String s) {
        if (peek().op(s)) {
            p++;
            return true;
        }
        return false;
    }

    private boolean acceptKw(String s) {
        if (peek().kw(s)) {
            p++;
            return true;
        }
        return false;
    }

    private void expectOp(String s) {
        if (!acceptOp(s)) {
            throw new ScriptError(line(), "expected", s, show(peek()));
        }
    }

    private String expectName() {
        Token t = next();
        if (t.type() != Token.Type.NAME) {
            throw new ScriptError(t.line(), "expected_name", show(t));
        }
        return t.text();
    }

    private static String show(Token t) {
        return switch (t.type()) {
            case NEWLINE -> "↵";
            case INDENT, DEDENT -> "indent";
            case EOF -> "EOF";
            default -> t.text();
        };
    }

    private void endOfStatement() {
        if (acceptOp(";")) {
            return;
        }
        Token t = peek();
        if (t.type() == Token.Type.NEWLINE) {
            p++;
        } else if (t.type() != Token.Type.EOF && t.type() != Token.Type.DEDENT) {
            throw new ScriptError(t.line(), "unexpected", show(t));
        }
    }

    private int emit(int op, int arg) {
        return code.emit(op, arg, toks.get(Math.max(0, p - 1)).line());
    }

    // ------------------------------------------------------------------
    // Instructions
    // ------------------------------------------------------------------

    private void statement() {
        Token t = peek();
        if (t.kw("if")) {
            ifStatement();
        } else if (t.kw("while")) {
            whileStatement();
        } else if (t.kw("for")) {
            forStatement();
        } else if (t.kw("def")) {
            defStatement();
        } else {
            simpleStatement();
            while (peek().type() != Token.Type.NEWLINE && peek().type() != Token.Type.EOF && peek().type() != Token.Type.DEDENT
                   && toks.get(p - 1).op(";")) {
                simpleStatement();
            }
        }
    }

    private void simpleStatement() {
        Token t = peek();
        if (acceptKw("pass")) {
            endOfStatement();
        } else if (acceptKw("break")) {
            Loop l = loop(t);
            if (l.isFor) {
                emit(Code.POP, 0);
            }
            l.breaks.add(emit(Code.JMP, -1));
            endOfStatement();
        } else if (acceptKw("continue")) {
            Loop l = loop(t);
            emit(Code.JMP, l.start);
            endOfStatement();
        } else if (acceptKw("return")) {
            if (!inFunction) {
                throw new ScriptError(t.line(), "return_outside");
            }
            if (peek().type() == Token.Type.NEWLINE || peek().op(";")) {
                emit(Code.CONST, code.constant(Value.NONE));
            } else {
                tupleExpression();
            }
            emit(Code.RET, 0);
            endOfStatement();
        } else if (acceptKw("global")) {
            do {
                code.globals.add(expectName());
            } while (acceptOp(","));
            endOfStatement();
        } else {
            assignmentOrExpression();
            endOfStatement();
        }
    }

    private Loop loop(Token t) {
        if (loops.isEmpty()) {
            throw new ScriptError(t.line(), "outside_loop", t.text());
        }
        return loops.get(loops.size() - 1);
    }

    /** Cible d'affectation analysee : nom, indice (objet et cle deja sur la pile) ou liste de noms. */
    private record Target(String name, boolean subscript, List<String> names) {
    }

    private void assignmentOrExpression() {
        int start = p;
        //Affectation : on cherche « = » ou un operateur augmente au niveau 0 de la ligne
        int depth = 0;
        String assign = null;
        for (int i = p; i < toks.size(); i++) {
            Token t = toks.get(i);
            if (t.type() == Token.Type.NEWLINE || t.type() == Token.Type.EOF || t.op(";")) {
                break;
            }
            if (t.op("(") || t.op("[") || t.op("{")) {
                depth++;
            } else if (t.op(")") || t.op("]") || t.op("}")) {
                depth--;
            } else if (depth == 0 && t.type() == Token.Type.OP && (t.text().equals("=") || t.text().length() >= 2 && t.text().endsWith("=")
                                                                   && !t.text().equals("==") && !t.text().equals("!=") && !t.text().equals("<=")
                                                                   && !t.text().equals(">="))) {
                assign = t.text();
                break;
            }
        }
        if (assign == null) {
            tupleExpression();
            emit(Code.POP, 0);
            return;
        }
        if (assign.equals("=")) {
            //Valeur d'abord, puis la cible : a = b = 1 accepte (plusieurs cibles)
            List<Integer> targets = new ArrayList<>();
            int q = p;
            targets.add(q);
            //Position de chaque cible : on repere les « = » de niveau 0
            depth = 0;
            int valueStart = -1;
            for (int i = p; i < toks.size(); i++) {
                Token t = toks.get(i);
                if (t.type() == Token.Type.NEWLINE || t.type() == Token.Type.EOF || t.op(";")) {
                    break;
                }
                if (t.op("(") || t.op("[") || t.op("{")) {
                    depth++;
                } else if (t.op(")") || t.op("]") || t.op("}")) {
                    depth--;
                } else if (depth == 0 && t.op("=")) {
                    valueStart = i + 1;
                    targets.add(i + 1);
                }
            }
            targets.remove(targets.size() - 1);
            int save = p;
            p = valueStart;
            tupleExpression();
            int end = p;
            for (int k = 0; k < targets.size(); k++) {
                if (k < targets.size() - 1) {
                    emit(Code.DUP, 0);
                }
                p = targets.get(k);
                storeTarget();
                expectOp("=");
            }
            p = end;
            if (save != start) {
                p = end;
            }
            return;
        }
        //Affectation augmentee : x += e, a[i] += e
        int op = switch (assign) {
            case "+=" -> Code.ADD;
            case "-=" -> Code.SUB;
            case "*=" -> Code.MUL;
            case "/=" -> Code.DIV;
            case "//=" -> Code.FLOORDIV;
            case "%=" -> Code.MOD;
            case "**=" -> Code.POW;
            default -> throw new ScriptError(line(), "unexpected", assign);
        };
        int l = line();
        if (peek().type() == Token.Type.NAME && peek(1).op(assign)) {
            String name = next().text();
            emit(Code.LOAD, code.name(name));
            p++;
            expression();
            emit(Code.BINOP, op);
            emit(Code.STORE, code.name(name));
            return;
        }
        //Indice : obj[cle] op= e
        postfixUntilLastSubscript(l);
        emit(Code.DUP2, 0);
        emit(Code.GETITEM, 0);
        expectOp(assign);
        expression();
        emit(Code.BINOP, op);
        emit(Code.ROT3, 0);
        emit(Code.SETITEM, 0);
    }

    /** Cible a la position courante, valeur sur la pile. */
    private void storeTarget() {
        Token t = peek();
        //Liste de noms (a, b = ...) ou [a, b]
        if (t.type() == Token.Type.NAME && (peek(1).op(",") ) || t.op("(") || t.op("[")) {
            if (t.op("(") || t.op("[")) {
                //tuple entre parentheses ou crochets : seulement des noms
                String close = t.op("(") ? ")" : "]";
                int save = p;
                p++;
                List<String> names = new ArrayList<>();
                boolean simple = true;
                while (!peek().op(close)) {
                    if (peek().type() != Token.Type.NAME) {
                        simple = false;
                        break;
                    }
                    names.add(next().text());
                    if (!acceptOp(",")) {
                        break;
                    }
                }
                if (simple && acceptOp(close) && peek().op("=")) {
                    unpackInto(names);
                    return;
                }
                p = save;
            } else {
                List<String> names = new ArrayList<>();
                do {
                    names.add(expectName());
                } while (acceptOp(",") && peek().type() == Token.Type.NAME);
                unpackInto(names);
                return;
            }
        }
        if (t.type() == Token.Type.NAME && peek(1).op("=")) {
            p++;
            emit(Code.STORE, code.name(t.text()));
            return;
        }
        //Indice : valeur deja sur la pile, puis objet et cle
        postfixUntilLastSubscript(t.line());
        emit(Code.SETITEM, 0);
    }

    private void unpackInto(List<String> names) {
        emit(Code.UNPACK, names.size());
        for (String n : names) {
            emit(Code.STORE, code.name(n));
        }
    }

    /** Analyse a[b][c] en laissant l'objet a[b] et la cle c sur la pile (la derniere indexation n'est pas faite). */
    private void postfixUntilLastSubscript(int l) {
        atom();
        while (true) {
            if (peek().op("[")) {
                //Est-ce le dernier indice avant la fin de la cible ?
                int save = p;
                int depth = 0, end = p;
                for (int i = p; i < toks.size(); i++) {
                    if (toks.get(i).op("[") || toks.get(i).op("(")) {
                        depth++;
                    } else if (toks.get(i).op("]") || toks.get(i).op(")")) {
                        depth--;
                        if (depth == 0) {
                            end = i;
                            break;
                        }
                    }
                }
                Token after = toks.get(end + 1);
                p++;
                expression();
                expectOp("]");
                if (after.type() == Token.Type.OP && (after.text().endsWith("=") && !after.text().equals("=="))) {
                    return;
                }
                emit(Code.GETITEM, 0);
                if (save < 0) {
                    break;
                }
            } else if (peek().op(".")) {
                p++;
                emit(Code.GETATTR, code.name(expectName()));
            } else if (peek().op("(")) {
                call();
            } else {
                throw new ScriptError(l, "bad_target");
            }
        }
    }

    private void block() {
        expectOp(":");
        if (peek().type() != Token.Type.NEWLINE) {
            simpleStatement();
            while (toks.get(p - 1).op(";") && peek().type() != Token.Type.NEWLINE && peek().type() != Token.Type.EOF) {
                simpleStatement();
            }
            return;
        }
        p++;
        if (peek().type() != Token.Type.INDENT) {
            throw new ScriptError(line(), "expected_block");
        }
        p++;
        while (peek().type() != Token.Type.DEDENT && peek().type() != Token.Type.EOF) {
            if (peek().type() == Token.Type.NEWLINE) {
                p++;
                continue;
            }
            statement();
        }
        if (peek().type() == Token.Type.DEDENT) {
            p++;
        }
    }

    private void ifStatement() {
        p++;
        expression();
        int jf = emit(Code.JF, -1);
        block();
        List<Integer> ends = new ArrayList<>();
        while (peek().kw("elif") || peek().kw("else")) {
            ends.add(emit(Code.JMP, -1));
            code.patch(jf, code.here());
            if (acceptKw("elif")) {
                expression();
                jf = emit(Code.JF, -1);
                block();
            } else {
                p++;
                jf = -1;
                block();
                break;
            }
        }
        if (jf >= 0) {
            code.patch(jf, code.here());
        }
        for (int e : ends) {
            code.patch(e, code.here());
        }
    }

    private void whileStatement() {
        p++;
        int start = code.here();
        expression();
        int jf = emit(Code.JF, -1);
        Loop l = new Loop(false, start);
        loops.add(l);
        block();
        loops.remove(loops.size() - 1);
        emit(Code.JMP, start);
        code.patch(jf, code.here());
        for (int b : l.breaks) {
            code.patch(b, code.here());
        }
    }

    private void forStatement() {
        p++;
        List<String> names = new ArrayList<>();
        boolean paren = acceptOp("(");
        do {
            names.add(expectName());
        } while (acceptOp(","));
        if (paren) {
            expectOp(")");
        }
        if (!acceptKw("in")) {
            throw new ScriptError(line(), "expected", "in", show(peek()));
        }
        tupleExpression();
        emit(Code.ITER, 0);
        int start = emit(Code.FOR, -1);
        if (names.size() == 1) {
            emit(Code.STORE, code.name(names.get(0)));
        } else {
            unpackInto(names);
        }
        Loop l = new Loop(true, start);
        loops.add(l);
        block();
        loops.remove(loops.size() - 1);
        emit(Code.JMP, start);
        int end = code.here();
        code.patch(start, end);
        for (int b : l.breaks) {
            code.patch(b, end);
        }
    }

    private void defStatement() {
        p++;
        int l = line();
        String name = expectName();
        Code fn = new Code(name);
        expectOp("(");
        List<Integer> defaults = new ArrayList<>();
        int firstDefault = -1;
        //Valeurs par defaut : constantes evaluees a la definition (empilees avant MAKEFN)
        while (!peek().op(")")) {
            fn.params.add(expectName());
            if (acceptOp("=")) {
                if (firstDefault < 0) {
                    firstDefault = fn.params.size() - 1;
                }
                expression();
                defaults.add(1);
            } else if (firstDefault >= 0) {
                throw new ScriptError(l, "default_order");
            }
            if (!acceptOp(",")) {
                break;
            }
        }
        expectOp(")");
        Code outer = code;
        List<Loop> outerLoops = new ArrayList<>(loops);
        boolean outerFn = inFunction;
        loops.clear();
        code = fn;
        inFunction = true;
        block();
        emit(Code.CONST, code.constant(Value.NONE));
        emit(Code.RET, 0);
        code = outer;
        inFunction = outerFn;
        loops.addAll(outerLoops);
        emit(Code.MAKEFN, code.constant(new Value.FnProto(fn, defaults.size())));
        emit(Code.STORE, code.name(name));
    }

    // ------------------------------------------------------------------
    // Expressions
    // ------------------------------------------------------------------

    /** Expression ou tuple (a, b) sans parentheses : un tuple devient une liste. */
    private void tupleExpression() {
        expression();
        if (peek().op(",")) {
            int n = 1;
            while (acceptOp(",")) {
                if (peek().type() == Token.Type.NEWLINE || peek().op("=") || peek().op(")") || peek().op(":")) {
                    break;
                }
                expression();
                n++;
            }
            emit(Code.LIST, n);
        }
    }

    private void expression() {
        orExpr();
        if (peek().kw("if")) {
            //a if cond else b : a est deja evalue ; on reorganise par sauts
            p++;
            int skipA = emit(Code.JMP, -1);
            int condStart = code.here();
            orExpr();
            int jf = emit(Code.JF, -1);
            int toEnd = emit(Code.JMP, -1);
            code.patch(jf, code.here());
            emit(Code.POP, 0);
            if (!acceptKw("else")) {
                throw new ScriptError(line(), "expected", "else", show(peek()));
            }
            expression();
            int end = emit(Code.JMP, -1);
            code.patch(skipA, condStart);
            code.patch(toEnd, code.here());
            code.patch(end, code.here());
        }
    }

    private void orExpr() {
        andExpr();
        List<Integer> jumps = new ArrayList<>();
        while (acceptKw("or")) {
            jumps.add(emit(Code.JT_KEEP, -1));
            andExpr();
        }
        for (int j : jumps) {
            code.patch(j, code.here());
        }
    }

    private void andExpr() {
        notExpr();
        List<Integer> jumps = new ArrayList<>();
        while (acceptKw("and")) {
            jumps.add(emit(Code.JF_KEEP, -1));
            notExpr();
        }
        for (int j : jumps) {
            code.patch(j, code.here());
        }
    }

    private void notExpr() {
        if (acceptKw("not")) {
            notExpr();
            emit(Code.NOT, 0);
            return;
        }
        comparison();
    }

    private int comparator() {
        Token t = peek();
        if (t.type() == Token.Type.OP) {
            int c = switch (t.text()) {
                case "==" -> Code.EQ;
                case "!=" -> Code.NE;
                case "<" -> Code.LT;
                case "<=" -> Code.LE;
                case ">" -> Code.GT;
                case ">=" -> Code.GE;
                default -> -1;
            };
            if (c >= 0) {
                p++;
            }
            return c;
        }
        if (t.kw("in")) {
            p++;
            return Code.IN;
        }
        if (t.kw("not") && peek(1).kw("in")) {
            p += 2;
            return Code.NOT_IN;
        }
        if (t.kw("is")) {
            p++;
            return acceptKw("not") ? Code.IS_NOT : Code.IS;
        }
        return -1;
    }

    private void comparison() {
        additive();
        int c = comparator();
        if (c < 0) {
            return;
        }
        additive();
        int c2 = comparator();
        if (c2 < 0) {
            emit(Code.CMP, c);
            return;
        }
        //Chaine a < b < c : (a < b) and (b < c), b evalue une fois
        List<Integer> fails = new ArrayList<>();
        while (true) {
            emit(Code.DUP, 0);
            emit(Code.ROT3, 0);
            emit(Code.CMP, c);
            fails.add(emit(Code.JF, -1));
            additive();
            c = c2;
            c2 = comparator();
            if (c2 < 0) {
                emit(Code.CMP, c);
                break;
            }
        }
        int end = emit(Code.JMP, -1);
        for (int f : fails) {
            code.patch(f, code.here());
        }
        emit(Code.POP, 0);
        emit(Code.CONST, code.constant(Boolean.FALSE));
        code.patch(end, code.here());
    }

    private void additive() {
        multiplicative();
        while (true) {
            if (acceptOp("+")) {
                multiplicative();
                emit(Code.BINOP, Code.ADD);
            } else if (acceptOp("-")) {
                multiplicative();
                emit(Code.BINOP, Code.SUB);
            } else {
                return;
            }
        }
    }

    private void multiplicative() {
        unary();
        while (true) {
            int op;
            if (acceptOp("*")) {
                op = Code.MUL;
            } else if (acceptOp("/")) {
                op = Code.DIV;
            } else if (acceptOp("//")) {
                op = Code.FLOORDIV;
            } else if (acceptOp("%")) {
                op = Code.MOD;
            } else {
                return;
            }
            unary();
            emit(Code.BINOP, op);
        }
    }

    private void unary() {
        if (acceptOp("-")) {
            unary();
            emit(Code.UNOP, Code.NEG);
        } else if (acceptOp("+")) {
            unary();
            emit(Code.UNOP, Code.POS);
        } else {
            power();
        }
    }

    private void power() {
        postfix();
        if (acceptOp("**")) {
            unary();
            emit(Code.BINOP, Code.POW);
        }
    }

    private void postfix() {
        atom();
        while (true) {
            if (peek().op("(")) {
                call();
            } else if (acceptOp(".")) {
                emit(Code.GETATTR, code.name(expectName()));
            } else if (acceptOp("[")) {
                subscript();
            } else {
                return;
            }
        }
    }

    private void subscript() {
        //Tranche a[i:j] ou indice a[i]
        boolean slice = false;
        if (peek().op(":")) {
            emit(Code.CONST, code.constant(Value.NONE));
            slice = true;
        } else {
            expression();
        }
        if (slice || peek().op(":")) {
            expectOp(":");
            if (peek().op("]")) {
                emit(Code.CONST, code.constant(Value.NONE));
            } else {
                expression();
            }
            expectOp("]");
            emit(Code.SLICE, 0);
            return;
        }
        expectOp("]");
        emit(Code.GETITEM, 0);
    }

    private void call() {
        expectOp("(");
        int n = 0;
        while (!peek().op(")")) {
            if (peek().type() == Token.Type.NAME && peek(1).op("=")) {
                throw new ScriptError(line(), "keyword_args");
            }
            expression();
            n++;
            if (!acceptOp(",")) {
                break;
            }
        }
        expectOp(")");
        emit(Code.CALL, n);
    }

    private void atom() {
        Token t = next();
        switch (t.type()) {
            case NUMBER -> {
                String s = t.text();
                Object v;
                try {
                    v = s.contains(".") || s.contains("e") || s.contains("E") ? (Object) Double.parseDouble(s) : (Object) Long.parseLong(s);
                } catch (NumberFormatException e) {
                    throw new ScriptError(t.line(), "number", s);
                }
                emit(Code.CONST, code.constant(v));
            }
            case STRING -> {
                StringBuilder b = new StringBuilder(t.text());
                //Chaines adjacentes concatenees
                while (peek().type() == Token.Type.STRING) {
                    b.append(next().text());
                }
                emit(Code.CONST, code.constant(b.toString()));
            }
            case FSTRING -> fstring(t);
            case NAME -> emit(Code.LOAD, code.name(t.text()));
            case KEYWORD -> {
                switch (t.text()) {
                    case "True" -> emit(Code.CONST, code.constant(Boolean.TRUE));
                    case "False" -> emit(Code.CONST, code.constant(Boolean.FALSE));
                    case "None" -> emit(Code.CONST, code.constant(Value.NONE));
                    default -> throw new ScriptError(t.line(), "unexpected", t.text());
                }
            }
            case OP -> {
                switch (t.text()) {
                    case "(" -> {
                        if (acceptOp(")")) {
                            emit(Code.LIST, 0);
                            return;
                        }
                        expression();
                        if (peek().op(",")) {
                            int n = 1;
                            while (acceptOp(",") && !peek().op(")")) {
                                expression();
                                n++;
                            }
                            emit(Code.LIST, n);
                        }
                        expectOp(")");
                    }
                    case "[" -> {
                        int n = 0;
                        while (!peek().op("]")) {
                            expression();
                            n++;
                            if (!acceptOp(",")) {
                                break;
                            }
                        }
                        expectOp("]");
                        emit(Code.LIST, n);
                    }
                    case "{" -> {
                        int n = 0;
                        while (!peek().op("}")) {
                            expression();
                            expectOp(":");
                            expression();
                            n++;
                            if (!acceptOp(",")) {
                                break;
                            }
                        }
                        expectOp("}");
                        emit(Code.DICT, n);
                    }
                    default -> throw new ScriptError(t.line(), "unexpected", t.text());
                }
            }
            default -> throw new ScriptError(t.line(), "unexpected", show(t));
        }
    }

    /** f-chaine : morceaux litteraux et expressions {expr} ou {expr:format}, concatenes. */
    private void fstring(Token t) {
        String s = t.text();
        int parts = 0;
        StringBuilder lit = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '{' && i + 1 < s.length() && s.charAt(i + 1) == '{') {
                lit.append('{');
                i += 2;
            } else if (c == '}' && i + 1 < s.length() && s.charAt(i + 1) == '}') {
                lit.append('}');
                i += 2;
            } else if (c == '{') {
                if (lit.length() > 0) {
                    emit(Code.CONST, code.constant(lit.toString()));
                    parts++;
                    lit.setLength(0);
                }
                int depth = 1, j = i + 1;
                while (j < s.length() && depth > 0) {
                    if (s.charAt(j) == '{') {
                        depth++;
                    } else if (s.charAt(j) == '}') {
                        depth--;
                    }
                    j++;
                }
                if (depth != 0) {
                    throw new ScriptError(t.line(), "fstring");
                }
                String inner = s.substring(i + 1, j - 1);
                String spec = "";
                int colon = topLevelColon(inner);
                if (colon >= 0) {
                    spec = inner.substring(colon + 1);
                    inner = inner.substring(0, colon);
                }
                List<Token> sub = Lexer.lex(inner);
                List<Token> fixed = new ArrayList<>();
                for (Token k : sub) {
                    if (k.type() != Token.Type.NEWLINE && k.type() != Token.Type.INDENT && k.type() != Token.Type.DEDENT) {
                        fixed.add(new Token(k.type(), k.text(), t.line(), k.col()));
                    }
                }
                Compiler c2 = new Compiler(fixed);
                c2.code = code;
                c2.expression();
                emit(Code.FORMAT, code.name(spec));
                parts++;
                i = j;
            } else {
                lit.append(c);
                i++;
            }
        }
        if (lit.length() > 0 || parts == 0) {
            emit(Code.CONST, code.constant(lit.toString()));
            parts++;
        }
        if (parts > 1) {
            emit(Code.CONCAT, parts);
        }
    }

    private static int topLevelColon(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ':' && depth == 0) {
                return i;
            } else if (c == '\'' || c == '"') {
                int j = s.indexOf(c, i + 1);
                if (j > i) {
                    i = j;
                }
            }
        }
        return -1;
    }
}

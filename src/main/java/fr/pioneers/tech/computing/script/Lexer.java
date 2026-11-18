package fr.pioneers.tech.computing.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lexer PioScript (0.17.1) : decoupe le texte en jetons, a la Python : indentation significative (INDENT / DEDENT), lignes logiques
 * continuees a l'interieur des parentheses, crochets et accolades, commentaires {@code #}, chaines simples ou doubles, f-chaines.
 */
public final class Lexer {

    public static final Set<String> KEYWORDS = Set.of("if", "elif", "else", "while", "for", "in", "def", "return", "break", "continue", "pass",
          "and", "or", "not", "True", "False", "None", "global", "is");
    private static final String[] OPS = {"**=", "//=", "==", "!=", "<=", ">=", "+=", "-=", "*=", "/=", "%=", "//", "**", "->", "+", "-", "*", "/",
          "%", "<", ">", "=", "(", ")", "[", "]", "{", "}", ",", ":", ".", ";"};

    private final String src;
    private int pos, line = 1, col = 1, depth;
    private final List<Token> out = new ArrayList<>();
    private final List<Integer> indents = new ArrayList<>(List.of(0));

    private Lexer(String src) {
        this.src = src.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
    }

    public static List<Token> lex(String src) {
        Lexer l = new Lexer(src);
        l.run();
        return l.out;
    }

    private void run() {
        boolean lineStart = true;
        while (pos < src.length()) {
            if (lineStart && depth == 0) {
                int indent = 0;
                int p = pos;
                while (p < src.length() && src.charAt(p) == ' ') {
                    indent++;
                    p++;
                }
                //Ligne vide ou commentaire : ignoree
                if (p >= src.length() || src.charAt(p) == '\n' || src.charAt(p) == '#') {
                    while (p < src.length() && src.charAt(p) != '\n') {
                        p++;
                    }
                    advanceTo(p);
                    if (pos < src.length()) {
                        advance();
                    }
                    continue;
                }
                advanceTo(p);
                int top = indents.get(indents.size() - 1);
                if (indent > top) {
                    indents.add(indent);
                    add(Token.Type.INDENT, "");
                } else {
                    while (indent < indents.get(indents.size() - 1)) {
                        indents.remove(indents.size() - 1);
                        add(Token.Type.DEDENT, "");
                    }
                    if (indent != indents.get(indents.size() - 1)) {
                        throw new ScriptError(line, "indent");
                    }
                }
                lineStart = false;
            }
            char c = src.charAt(pos);
            if (c == '\n') {
                if (depth == 0) {
                    add(Token.Type.NEWLINE, "");
                    lineStart = true;
                }
                advance();
            } else if (c == ' ') {
                advance();
            } else if (c == '\\' && pos + 1 < src.length() && src.charAt(pos + 1) == '\n') {
                advance();
                advance();
            } else if (c == '#') {
                while (pos < src.length() && src.charAt(pos) != '\n') {
                    advance();
                }
            } else if (Character.isDigit(c) || c == '.' && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1))) {
                number();
            } else if (Character.isLetter(c) || c == '_') {
                int start = pos, sl = line, sc = col;
                while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
                    advance();
                }
                String word = src.substring(start, pos);
                if ((word.equals("f") || word.equals("F")) && pos < src.length() && (src.charAt(pos) == '"' || src.charAt(pos) == '\'')) {
                    out.add(new Token(Token.Type.FSTRING, string(), sl, sc));
                } else {
                    out.add(new Token(KEYWORDS.contains(word) ? Token.Type.KEYWORD : Token.Type.NAME, word, sl, sc));
                }
            } else if (c == '"' || c == '\'') {
                int sl = line, sc = col;
                out.add(new Token(Token.Type.STRING, string(), sl, sc));
            } else {
                String op = null;
                for (String o : OPS) {
                    if (src.startsWith(o, pos)) {
                        op = o;
                        break;
                    }
                }
                if (op == null) {
                    throw new ScriptError(line, "char", c);
                }
                if (op.equals("(") || op.equals("[") || op.equals("{")) {
                    depth++;
                } else if (op.equals(")") || op.equals("]") || op.equals("}")) {
                    depth = Math.max(0, depth - 1);
                }
                add(Token.Type.OP, op);
                for (int i = 0; i < op.length(); i++) {
                    advance();
                }
            }
        }
        if (!out.isEmpty() && out.get(out.size() - 1).type() != Token.Type.NEWLINE) {
            add(Token.Type.NEWLINE, "");
        }
        while (indents.size() > 1) {
            indents.remove(indents.size() - 1);
            add(Token.Type.DEDENT, "");
        }
        add(Token.Type.EOF, "");
    }

    private void number() {
        int start = pos, sl = line, sc = col;
        boolean dot = false, exp = false;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (Character.isDigit(c) || c == '_') {
                advance();
            } else if (c == '.' && !dot && !exp) {
                dot = true;
                advance();
            } else if ((c == 'e' || c == 'E') && !exp) {
                exp = true;
                advance();
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    advance();
                }
            } else {
                break;
            }
        }
        out.add(new Token(Token.Type.NUMBER, src.substring(start, pos).replace("_", ""), sl, sc));
    }

    private String string() {
        char q = src.charAt(pos);
        int sl = line;
        boolean triple = src.startsWith("" + q + q + q, pos);
        int n = triple ? 3 : 1;
        for (int i = 0; i < n; i++) {
            advance();
        }
        StringBuilder b = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw new ScriptError(sl, "string");
            }
            char c = src.charAt(pos);
            if (triple ? src.startsWith("" + q + q + q, pos) : c == q) {
                for (int i = 0; i < n; i++) {
                    advance();
                }
                return b.toString();
            }
            if (c == '\n' && !triple) {
                throw new ScriptError(sl, "string");
            }
            if (c == '\\' && pos + 1 < src.length()) {
                advance();
                char e = src.charAt(pos);
                b.append(switch (e) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case '0' -> '\0';
                    default -> e;
                });
                advance();
                continue;
            }
            b.append(c);
            advance();
        }
    }

    private void add(Token.Type type, String text) {
        out.add(new Token(type, text, line, col));
    }

    private void advance() {
        if (src.charAt(pos) == '\n') {
            line++;
            col = 1;
        } else {
            col++;
        }
        pos++;
    }

    private void advanceTo(int p) {
        while (pos < p) {
            advance();
        }
    }
}

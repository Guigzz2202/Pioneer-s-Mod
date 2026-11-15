package fr.pioneers.tech.computing.script;

/** Jeton du lexer PioScript. */
public record Token(Type type, String text, int line, int col) {

    public enum Type { NAME, KEYWORD, NUMBER, STRING, FSTRING, OP, NEWLINE, INDENT, DEDENT, EOF }

    public boolean is(Type t, String s) {
        return type == t && text.equals(s);
    }

    public boolean op(String s) {
        return type == Type.OP && text.equals(s);
    }

    public boolean kw(String s) {
        return type == Type.KEYWORD && text.equals(s);
    }
}

package fr.pioneers.tech.computing.script;

/**
 * Erreur d'un programme PioScript (0.17.1) : code stable (cle de langue {@code computing.pioneers.script.error.<code>}), arguments, ligne
 * (1..n, 0 si inconnue). Le texte est traduit par l'interface ; le banc de tests compare le code.
 */
public class ScriptError extends RuntimeException {

    public final String code;
    public final String[] args;
    public int line;

    public ScriptError(int line, String code, Object... args) {
        super(code);
        this.code = code;
        this.line = line;
        this.args = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            this.args[i] = String.valueOf(args[i]);
        }
    }

    public ScriptError(String code, Object... args) {
        this(0, code, args);
    }

    @Override
    public String toString() {
        return "line " + line + ": " + code + (args.length > 0 ? " " + String.join(", ", args) : "");
    }
}

package fr.pioneers.tech.computing.script;

import fr.pioneers.tech.computing.script.Value.PDict;
import fr.pioneers.tech.computing.script.Value.PList;
import fr.pioneers.tech.computing.script.Value.PObject;
import java.util.Map;

/**
 * 0.20.41 : module {@code json} de PioScript : {@code json.dumps(valeur)} (nombres, textes, booleens, None, listes, dictionnaires ; cles
 * ecrites en texte) et {@code json.loads(texte)}, pour enregistrer un etat dans un fichier. Erreur « convert » sur un texte invalide.
 */
final class JsonLib {

    private JsonLib() {
    }

    static PObject module() {
        return new PObject("json")
              .fn("dumps", a -> {
                  if (a.length != 1) {
                      throw new ScriptError("arity", "json.dumps", 1, a.length);
                  }
                  StringBuilder b = new StringBuilder();
                  write(b, a[0], 0);
                  return b.toString();
              })
              .fn("loads", a -> {
                  if (a.length != 1) {
                      throw new ScriptError("arity", "json.loads", 1, a.length);
                  }
                  Parser p = new Parser(Value.str(a[0]));
                  p.ws();
                  Object v = p.value(0);
                  p.ws();
                  if (p.i != p.s.length()) {
                      throw p.bad();
                  }
                  return v;
              });
    }

    private static void write(StringBuilder b, Object v, int depth) {
        if (depth > 32 || b.length() > Value.MAX_STRING) {
            throw new ScriptError("too_big");
        }
        if (v == Value.NONE || v == null) {
            b.append("null");
        } else if (v instanceof Boolean x) {
            b.append(x ? "true" : "false");
        } else if (v instanceof Long l) {
            b.append(l);
        } else if (v instanceof Double d) {
            b.append(Double.isFinite(d) ? (d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf(d.longValue()) + ".0" : String.valueOf(d)) : "null");
        } else if (v instanceof String s) {
            quote(b, s);
        } else if (v instanceof PList l) {
            b.append('[');
            for (int i = 0; i < l.items.size(); i++) {
                if (i > 0) {
                    b.append(", ");
                }
                write(b, l.items.get(i), depth + 1);
            }
            b.append(']');
        } else if (v instanceof PDict d) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<Object, Object> e : d.map.entrySet()) {
                if (!first) {
                    b.append(", ");
                }
                first = false;
                quote(b, Value.str(e.getKey()));
                b.append(": ");
                write(b, e.getValue(), depth + 1);
            }
            b.append('}');
        } else {
            throw new ScriptError("convert", Value.typeName(v), "json");
        }
    }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        b.append('"');
    }

    private static final class Parser {

        final String s;
        int i;

        Parser(String s) {
            this.s = s;
        }

        ScriptError bad() {
            String near = s.substring(Math.min(i, s.length()), Math.min(s.length(), i + 12));
            return new ScriptError("convert", near.isEmpty() ? "…" : near, "json");
        }

        void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        boolean eat(String word) {
            if (s.startsWith(word, i)) {
                i += word.length();
                return true;
            }
            return false;
        }

        Object value(int depth) {
            if (depth > 32) {
                throw new ScriptError("too_big");
            }
            ws();
            if (i >= s.length()) {
                throw bad();
            }
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                PDict d = new PDict();
                ws();
                if (eat("}")) {
                    return d;
                }
                while (true) {
                    ws();
                    if (i >= s.length() || s.charAt(i) != '"') {
                        throw bad();
                    }
                    String k = string();
                    ws();
                    if (!eat(":")) {
                        throw bad();
                    }
                    d.put(k, value(depth + 1));
                    ws();
                    if (eat("}")) {
                        return d;
                    }
                    if (!eat(",")) {
                        throw bad();
                    }
                }
            }
            if (c == '[') {
                i++;
                PList l = new PList();
                ws();
                if (eat("]")) {
                    return l;
                }
                while (true) {
                    l.add(value(depth + 1));
                    ws();
                    if (eat("]")) {
                        return l;
                    }
                    if (!eat(",")) {
                        throw bad();
                    }
                }
            }
            if (c == '"') {
                return string();
            }
            if (eat("true")) {
                return true;
            }
            if (eat("false")) {
                return false;
            }
            if (eat("null")) {
                return Value.NONE;
            }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            String num = s.substring(start, i);
            if (num.isEmpty()) {
                throw bad();
            }
            try {
                if (num.contains(".") || num.contains("e") || num.contains("E")) {
                    return Double.parseDouble(num);
                }
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                i = start;
                throw bad();
            }
        }

        String string() {
            i++;
            StringBuilder b = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return b.toString();
                }
                if (c == '\\' && i < s.length()) {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> b.append('\n');
                        case 't' -> b.append('\t');
                        case 'r' -> b.append('\r');
                        case 'u' -> {
                            if (i + 4 > s.length()) {
                                throw bad();
                            }
                            try {
                                b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            } catch (NumberFormatException ex) {
                                throw bad();
                            }
                            i += 4;
                        }
                        default -> b.append(e);
                    }
                } else {
                    b.append(c);
                }
                if (b.length() > Value.MAX_STRING) {
                    throw new ScriptError("too_big");
                }
            }
            throw bad();
        }
    }
}

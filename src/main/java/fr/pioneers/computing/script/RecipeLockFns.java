package fr.pioneers.computing.script;

import fr.pioneers.computing.script.Value.PObject;
import java.util.function.Supplier;

/**
 * MAJ Recettes (lot 7) : methodes du selecteur de recette sur une machine de PioScript ({@code link(n)}, {@code network.machine(n)}) :
 * {@code m.lockRecipe(id)} (vrai si la recette est une recette de la machine : elle ne fait plus qu'elle), {@code m.unlockRecipe()} (vrai si
 * la machine a un selecteur), {@code m.lockedRecipe()} (identifiant ou None). Sans dependance a Minecraft : l'hote fournit la machine
 * ({@link Target}) ; le banc hors jeu les essaie sur une machine factice.
 */
public final class RecipeLockFns {

    /** La machine visee, vue de PioScript. */
    public interface Target {

        /** La machine est-elle la, chargee, et a-t-elle un selecteur de recette ? */
        boolean lockable();

        /** Recette verrouillee (identifiant complet), ou null. */
        String locked();

        /** Pose le verrou ; faux si la recette n'est pas une recette de la machine. */
        boolean lock(String id);

        /** Retire le verrou. */
        void unlock();
    }

    private RecipeLockFns() {
    }

    /** Ajoute les trois methodes a l'objet machine. */
    public static PObject install(PObject o, Supplier<Target> target) {
        return o.fn("lockRecipe", a -> {
                  if (a.length != 1) {
                      throw new ScriptError("arity", "lockRecipe", 1, a.length);
                  }
                  if (!(a[0] instanceof String id) || id.isBlank()) {
                      throw new ScriptError("convert", a[0] instanceof String ? "" : Value.typeName(a[0]), "str");
                  }
                  Target t = target.get();
                  return t != null && t.lockable() && t.lock(id.trim().contains(":") ? id.trim() : "pioneers:" + id.trim());
              })
              .fn("unlockRecipe", a -> {
                  if (a.length != 0) {
                      throw new ScriptError("arity", "unlockRecipe", 0, a.length);
                  }
                  Target t = target.get();
                  if (t == null || !t.lockable()) {
                      return false;
                  }
                  t.unlock();
                  return true;
              })
              .fn("lockedRecipe", a -> {
                  if (a.length != 0) {
                      throw new ScriptError("arity", "lockedRecipe", 0, a.length);
                  }
                  Target t = target.get();
                  String id = t == null || !t.lockable() ? null : t.locked();
                  return id == null ? Value.NONE : id;
              });
    }
}

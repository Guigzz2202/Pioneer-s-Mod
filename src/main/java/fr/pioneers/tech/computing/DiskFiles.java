package fr.pioneers.tech.computing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Systeme de fichiers d'un support (0.17.1) : disque interne de l'ordinateur (lecteur {@code c}) ou disquette (lecteurs {@code a} et
 * {@code b}). Les fichiers sont des textes ranges dans un {@link CompoundTag} (nom -> contenu) ; la place occupee compte les caracteres des
 * noms et des contenus. Noms : 1 a 32 caracteres parmi lettres, chiffres, {@code _ . -}.
 */
public final class DiskFiles {

    public static final int DISK_CAPACITY = 65_536, INTERNAL_CAPACITY = 32_768;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.\\-]{1,32}");

    private DiskFiles() {
    }

    public static boolean validName(String name) {
        return NAME.matcher(name).matches();
    }

    public static int used(CompoundTag files) {
        int n = 0;
        for (String k : files.getAllKeys()) {
            n += k.length() + files.getString(k).length();
        }
        return n;
    }

    public static List<String> list(CompoundTag files) {
        List<String> out = new ArrayList<>(files.getAllKeys());
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    @Nullable
    public static String read(CompoundTag files, String name) {
        return files.contains(name) ? files.getString(name) : null;
    }

    /** Ecrit (ou remplace) un fichier ; faux si le nom est invalide ou s'il n'y a pas la place. */
    public static boolean write(CompoundTag files, String name, String text, int capacity) {
        if (!validName(name)) {
            return false;
        }
        int used = used(files) - (files.contains(name) ? name.length() + files.getString(name).length() : 0);
        if (used + name.length() + text.length() > capacity) {
            return false;
        }
        files.putString(name, text);
        return true;
    }

    public static boolean delete(CompoundTag files, String name) {
        if (!files.contains(name)) {
            return false;
        }
        files.remove(name);
        return true;
    }
}

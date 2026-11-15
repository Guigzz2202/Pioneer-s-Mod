package fr.pioneers.industry;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * MAJ Recettes (lot 9a) : migration des cases et reservoirs des machines deja posees dont l'io a change pendant la MAJ (creuset electrique et
 * convertisseur L2-4, machine de coulee et coulee continue L4b-9, soudeuse a l'arc L6-4, machines refondues L8a-10).
 * <p>
 * Les cases d'objets sont sauvees par rang (NBT {@code items}, champ {@code Slot}) et les reservoirs par position ({@code fluids},
 * {@code gases}) : une entree ajoutee avant les sorties ferait glisser les sorties dans les entrees et la batterie dans une sortie. Chaque
 * case et chaque reservoir recoit ici un nom (entree i, sortie j, batterie, refroidissement, vapeur...) calcule depuis l'ancienne io
 * ({@link IndustryMachine#legacyIo()}, table generee par tools/gc/industry/io_legacy.py) et depuis l'io actuelle : le contenu va au meme nom
 * dans la nouvelle disposition. Une seule fois : la machine relue est sauvee avec {@code ioVersion} = {@link IndustryMachine#IO_VERSION}
 * ({@link #stamp}). Un contenu sans place (io qui retrecit : aucun cas aujourd'hui) va a la premiere case libre de la meme sorte, sinon
 * l'objet est rendu par la machine a son premier tick ({@code overflow}).
 * <p>
 * Dispositions : atelier = objets (entrees, sorties, batterie), fluides (entrees, sorties), gaz (entrees, sorties) ; controleur de multibloc =
 * objets (tampon d'entree de 6 ou 9 cases, tampon de sortie), fluides (entrees, refroidissement, sorties, refroidissement chaud), gaz
 * (entrees, sorties, vapeur). Les reservoirs de stockage ({@code Kind.TANK}) et les foreuses ont leur propre disposition.
 */
public final class IoMigration {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Cle NBT de la version des cases. */
    public static final String NBT = "ioVersion";

    private IoMigration() {
    }

    /** Disposition des cases (objets) et des reservoirs (fluides, gaz), un nom par position. */
    public record Layout(List<String> items, List<String> fluids, List<String> gases) {
    }

    /** Ecrit la version des cases dans le NBT d'une machine sauvee. */
    public static void stamp(CompoundTag tag) {
        tag.putInt(NBT, IndustryMachine.IO_VERSION);
    }

    /** Marque de version + 1.0.5-beta (lot 39) : objets restes sans place encore a rendre (machine sauvee avant son premier tick). */
    public static void stamp(CompoundTag tag, List<ItemStack> overflow) {
        stamp(tag);
        saveOverflow(tag, overflow);
    }

    /**
     * Lecture d'une machine (atelier ou controleur) : NBT sauve avec une ancienne io remis dans la disposition actuelle (objets sans place dans
     * {@code overflow}), sinon les objets sans place gardes a la sauvegarde ({@link #OVERFLOW}). La liste est toujours celle du NBT relu.
     */
    public static CompoundTag reload(IndustryMachine m, CompoundTag tag, List<ItemStack> overflow, boolean controller) {
        overflow.clear();
        if (needed(m, tag)) {
            return controller ? controller(m, tag, overflow) : atelier(m, tag, overflow);
        }
        loadOverflow(tag, overflow);
        return tag;
    }

    /**
     * Le NBT vient-il d'une machine sauvee avec son ancienne io (monde d'avant la MAJ) ? 1.0.3-beta (lot 5) : la version des cases du NBT est
     * comparee a celle de la machine ({@link IndustryMachine#legacyIoVersion()} : 1 pour les io changees par la MAJ Recettes, 2 pour le
     * generateur a combustion et la turbine a gaz, generateurs separes).
     */
    public static boolean needed(IndustryMachine machine, CompoundTag tag) {
        if (!tag.getBoolean("pioneers") || tag.getInt(NBT) >= IndustryMachine.IO_VERSION) {
            return false;
        }
        //1.0.4-beta (lot 11) : chaine d'anciennes io (legacyIo(version)) ; une base qui a absorbe des machines peut relire le NBT de l'une d'elles
        return machine.legacyIo(tag.getInt(NBT)) != null || !absorbed(machine).isEmpty();
    }

    // ------------------------------------------------------------------
    // 1.0.4-beta (lot 11) : fusion des machines (GeneratedFusions)
    // ------------------------------------------------------------------

    /** Machines absorbees par cette base (cles, ordre de la table). */
    public static List<String> absorbed(IndustryMachine base) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : new java.util.TreeMap<>(GeneratedFusions.BASE).entrySet()) {
            if (e.getValue().equals(base.key)) {
                out.add(e.getKey());
            }
        }
        //1.0.4-beta (lot 13) : machine supprimee dont le controleur est remappe vers celui-ci (centrale a cycle combine -> turbine a gaz) : son
        //NBT est reconnu par la forme comme celui d'une absorbee
        for (Map.Entry<String, String> e : new java.util.TreeMap<>(GeneratedFusions.RETIRED).entrySet()) {
            if (e.getValue().equals(base.key)) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** Une io candidate pour un NBT : la machine (cle) et son io a cette version des cases. */
    public record Candidate(String key, int[] io) {
    }

    /**
     * io dans laquelle ce NBT a ete ecrit : celle de la base a sa version des cases, ou celle d'une machine absorbee dont le bloc a ete
     * remappe vers la base (PiRemaps.p104Block). Le NBT ne nomme pas sa machine : on compare sa FORME (nombre de reservoirs de fluide et de
     * gaz, plus grand rang d'objet) a celle de chaque candidate ; a egalite, le dossier de la recette en cours ou verrouillee
     * ({@code industry/<cle>/...}) departage, puis la base passe d'abord. {@code controller} : disposition d'un controleur de multibloc.
     */
    public static Candidate source(IndustryMachine base, CompoundTag tag, boolean controller) {
        int version = tag.getInt(NBT);
        int[] own = base.legacyIo(version);
        List<Candidate> all = new ArrayList<>();
        all.add(new Candidate(base.key, own != null ? own : io(base)));
        for (String k : absorbed(base)) {
            int[] io = GeneratedFusions.io(k, version);
            if (io != null) {
                all.add(new Candidate(k, io));
            }
        }
        if (all.size() == 1) {
            return all.get(0);
        }
        int fluids = tag.getList("fluids", Tag.TAG_COMPOUND).size(), gases = tag.getList("gases", Tag.TAG_COMPOUND).size();
        int maxSlot = -1;
        ListTag items = tag.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            maxSlot = Math.max(maxSlot, items.getCompound(i).getShort("Slot"));
        }
        String hint = hint(tag);
        List<Candidate> fit = new ArrayList<>();
        for (Candidate c : all) {
            Layout l = controller ? controller(base, c.io()) : atelier(c.io());
            if (l.fluids().size() == fluids && l.gases().size() == gases && maxSlot < l.items().size()) {
                fit.add(c);
            }
        }
        if (fit.isEmpty()) {
            return all.get(0);
        }
        for (Candidate c : fit) {
            if (c.key().equals(hint)) {
                return c;
            }
        }
        return fit.get(0);
    }

    /** Cle de machine lue dans le dossier de la recette en cours, du lot en cours ou verrouillee ({@code pioneers:industry/<cle>/...}), ou "". */
    static String hint(CompoundTag tag) {
        for (String key : new String[]{"recipe", "batch", "lockedRecipe"}) {
            String id = tag.getString(key);
            int a = id.indexOf("industry/");
            if (a >= 0) {
                int b = id.indexOf('/', a + 9);
                if (b > a + 9) {
                    return id.substring(a + 9, b);
                }
            }
        }
        return "";
    }

    /**
     * NBT d'atelier (drapeau {@code pioneers}) d'une machine absorbee relu dans une machine du KIT qui l'a absorbee (broyeur, four) : contenu
     * remis par noms dans la disposition du kit {@code kitLayout} (KitProcess.layout). Null si la forme du NBT n'est celle d'aucune absorbee
     * (ancienne machine fondue sans table, 1.0.3-beta lot 5 : objets rendus comme avant).
     */
    @org.jetbrains.annotations.Nullable
    public static CompoundTag kit(IndustryMachine base, Layout kitLayout, CompoundTag tag, List<ItemStack> overflow) {
        Candidate c = source(base, tag, false);
        if (c.key().equals(base.key)) {
            return null;
        }
        Layout was = atelier(c.io());
        int fluids = tag.getList("fluids", Tag.TAG_COMPOUND).size(), gases = tag.getList("gases", Tag.TAG_COMPOUND).size();
        if (was.fluids().size() != fluids || was.gases().size() != gases) {
            return null;
        }
        LOGGER.info("Pioneer's : {} relu comme {} (fusion des machines) : contenu remis dans les cases du kit", c.key(), base.key);
        return remap(base, tag, was, kitLayout, overflow);
    }

    static int[] io(IndustryMachine m) {
        return new int[]{m.itemsIn, m.itemsOut, m.fluidsIn, m.fluidsOut, m.gasesIn, m.gasesOut};
    }

    private static void add(List<String> list, String name, int n) {
        for (int i = 0; i < n; i++) {
            list.add(name + i);
        }
    }

    /** Machine d'atelier : objets (entrees, sorties, batterie), fluides et gaz (entrees puis sorties). */
    public static Layout atelier(int[] io) {
        List<String> items = new ArrayList<>(), fluids = new ArrayList<>(), gases = new ArrayList<>();
        add(items, "in", io[0]);
        add(items, "out", io[1]);
        items.add("battery0");
        add(fluids, "in", io[2]);
        add(fluids, "out", io[3]);
        add(gases, "in", io[4]);
        add(gases, "out", io[5]);
        return new Layout(items, fluids, gases);
    }

    /** Cases tampon d'un controleur (comme ControllerLayout : 6, ou 9 pour trois sortes d'objets). */
    private static int buffer(int kinds) {
        return kinds == 0 ? 0 : kinds <= 2 ? 6 : 9;
    }

    /** Controleur de multibloc (meme ordre de declaration que ControllerTile). */
    public static Layout controller(IndustryMachine m, int[] io) {
        List<String> items = new ArrayList<>(), fluids = new ArrayList<>(), gases = new ArrayList<>();
        boolean drill = m.kind == IndustryMachine.Kind.DRILL, tank = m.kind == IndustryMachine.Kind.TANK;
        add(items, "in", drill ? 0 : buffer(io[0]));
        add(items, "out", drill ? 9 : buffer(io[1]));
        add(fluids, "in", tank ? Math.min(1, io[2]) : io[2]);
        fluids.add("coolant0");
        add(fluids, "out", tank ? 0 : io[3]);
        fluids.add("hot0");
        add(gases, "in", tank ? Math.min(1, io[4]) : io[4]);
        add(gases, "out", tank ? 0 : io[5]);
        gases.add("steam0");
        return new Layout(items, fluids, gases);
    }

    /**
     * NBT d'un atelier sauve avec une ancienne io (ou par une machine absorbee : {@link #source}), remis dans la disposition actuelle (copie ;
     * le NBT recu n'est pas touche).
     */
    public static CompoundTag atelier(IndustryMachine m, CompoundTag tag, List<ItemStack> overflow) {
        Candidate c = source(m, tag, false);
        if (!c.key().equals(m.key)) {
            LOGGER.info("Pioneer's : atelier {} relu comme {} (fusion des machines)", c.key(), m.key);
        }
        return remap(m, tag, atelier(c.io()), atelier(io(m)), overflow);
    }

    /** NBT d'un controleur de multibloc sauve avec une ancienne io (ou par une absorbee), remis dans la disposition actuelle (copie). */
    public static CompoundTag controller(IndustryMachine m, CompoundTag tag, List<ItemStack> overflow) {
        Candidate c = source(m, tag, true);
        if (!c.key().equals(m.key)) {
            LOGGER.info("Pioneer's : controleur {} relu comme {} (fusion des machines)", c.key(), m.key);
        }
        return remap(m, tag, controller(m, c.io()), controller(m, io(m)), overflow);
    }

    /** Sorte d'une case : son nom sans le numero (in, out, battery, coolant...). */
    private static String kind(String name) {
        int i = name.length();
        while (i > 0 && Character.isDigit(name.charAt(i - 1))) {
            i--;
        }
        return name.substring(0, i);
    }

    /** Place d'un nom dans la nouvelle disposition, sinon premiere place libre de la meme sorte, sinon -1. */
    private static int place(String name, List<String> now, boolean[] taken) {
        int j = now.indexOf(name);
        if (j >= 0 && !taken[j]) {
            return j;
        }
        for (int k = 0; k < now.size(); k++) {
            if (!taken[k] && kind(now.get(k)).equals(kind(name))) {
                return k;
            }
        }
        return -1;
    }

    static CompoundTag remap(IndustryMachine m, CompoundTag tag, Layout was, Layout now, List<ItemStack> overflow) {
        CompoundTag out = tag.copy();
        // Objets : rang dans le champ Slot
        ListTag items = tag.getList("items", Tag.TAG_COMPOUND);
        ListTag moved = new ListTag();
        boolean[] taken = new boolean[now.items().size()];
        List<CompoundTag> later = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            CompoundTag e = items.getCompound(i);
            int from = e.getShort("Slot");
            int to = from >= 0 && from < was.items().size() ? now.items().indexOf(was.items().get(from)) : -1;
            if (to >= 0 && !taken[to]) {
                taken[to] = true;
                CompoundTag c = e.copy();
                c.putShort("Slot", (short) to);
                moved.add(c);
            } else {
                later.add(e);
            }
        }
        for (CompoundTag e : later) {
            int from = e.getShort("Slot");
            String name = from >= 0 && from < was.items().size() ? was.items().get(from) : "out0";
            int to = place(name, now.items(), taken);
            if (to < 0 && kind(name).equals("in")) {
                //1.0.5-beta (lot 39) : io retrecie (creuset electrique 4 -> 3 entrees) : une entree sans place va a une case de SORTIE libre
                //(le joueur et les entonnoirs l'en retirent) avant d'etre posee au sol
                to = place("out", now.items(), taken);
            }
            if (to >= 0) {
                taken[to] = true;
                CompoundTag c = e.copy();
                c.putShort("Slot", (short) to);
                moved.add(c);
            } else {
                ItemStack stack = ItemStack.of(e);
                if (!stack.isEmpty()) {
                    overflow.add(stack);
                }
            }
        }
        out.put("items", moved);
        // Reservoirs : par position
        out.put("fluids", positional(m, "fluids", tag.getList("fluids", Tag.TAG_COMPOUND), was.fluids(), now.fluids()));
        out.put("gases", positional(m, "gases", tag.getList("gases", Tag.TAG_COMPOUND), was.gases(), now.gases()));
        stamp(out);
        return out;
    }

    /** 1.0.5-beta (lot 39) : cle des objets restes sans place, gardes si la machine est sauvee avant son premier tick (rien de perdu). */
    public static final String OVERFLOW = "ioOverflow";

    /** Garde les objets sans place encore a rendre (machine sauvee avant son premier tick). */
    public static void saveOverflow(CompoundTag tag, List<ItemStack> overflow) {
        if (overflow.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        for (ItemStack st : overflow) {
            list.add(st.save(new CompoundTag()));
        }
        tag.put(OVERFLOW, list);
    }

    /** Relit les objets sans place gardes par {@link #saveOverflow} ; vrai s'il y en a (a rendre au prochain tick). */
    public static boolean loadOverflow(CompoundTag tag, List<ItemStack> overflow) {
        ListTag list = tag.getList(OVERFLOW, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack st = ItemStack.of(list.getCompound(i));
            if (!st.isEmpty()) {
                overflow.add(st);
            }
        }
        return !list.isEmpty();
    }

    /** Rend au monde (au-dessus de la machine) les objets restes sans place apres la migration, au premier tick du serveur. */
    public static void release(Level level, BlockPos pos, List<ItemStack> overflow) {
        for (ItemStack stack : overflow) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
        }
        overflow.clear();
    }

    private static ListTag positional(IndustryMachine m, String what, ListTag saved, List<String> was, List<String> now) {
        CompoundTag[] slots = new CompoundTag[now.size()];
        boolean[] taken = new boolean[now.size()];
        List<Integer> later = new ArrayList<>();
        for (int i = 0; i < saved.size() && i < was.size(); i++) {
            int to = now.indexOf(was.get(i));
            if (to >= 0 && !taken[to]) {
                taken[to] = true;
                slots[to] = saved.getCompound(i).copy();
            } else {
                later.add(i);
            }
        }
        for (int i : later) {
            int to = place(was.get(i), now, taken);
            if (to >= 0) {
                taken[to] = true;
                slots[to] = saved.getCompound(i).copy();
            } else if (!saved.getCompound(i).isEmpty()) {
                LOGGER.warn("Pioneer's : reservoir {} {} de {} sans place apres la migration des cases : contenu perdu ({})", what, i, m.key,
                      saved.getCompound(i));
            }
        }
        ListTag list = new ListTag();
        for (CompoundTag c : slots) {
            list.add(c == null ? new CompoundTag() : c);
        }
        return list;
    }
}

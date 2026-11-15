package fr.pioneers.rail.train;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.0 : horaire d'un train (comme dans Factorio) : une liste d'arrets (nom de gare : toutes les gares de ce nom conviennent), chacun avec
 * ses conditions de depart. Les conditions se lisent de haut en bas ; « OU » coupe la liste en groupes, le train part des qu'un groupe a
 * toutes ses conditions vraies (« ET » lie plus fort que « OU »). Sans condition, le train repart aussitot arrete.
 * <p>
 * 0.20.5 : conditions de carburant, de fluide, de chargement, de stock (gare, reseau), de signal, de redstone et de place libre ; arret vers
 * une gare precise ({@link Stop#target}) ; jetons de ressource dans les noms de gare ({@link #token}, jokers des interruptions).
 */
public final class Schedule {

    public enum Type {
        /** Temps passe a quai (s). */
        TIME,
        /** Inactivite : aucun objet charge ni decharge depuis (s). */
        INACTIVITY,
        /** Tous les wagons de marchandises pleins. */
        FULL,
        /** Tous les wagons vides. */
        EMPTY,
        /** Nombre d'un objet dans le train compare a une valeur. */
        ITEM,
        /** Un joueur est a bord. */
        PASSENGER,
        /** Aucun joueur a bord. */
        NO_PASSENGER,
        //0.20.5
        /** Reserve d'energie de la locomotive (%), comparee a une valeur. */
        FUEL,
        /** Fluide ou gaz dans le train (mB). */
        FLUID,
        /** Le train porte quelque chose (fixe les jokers des interruptions). */
        CARGO,
        /** Stock des postes de la gare (objet, fluide ou gaz). */
        STATION_STOCK,
        /** Stock du reseau robotique de la gare. */
        NETWORK_STOCK,
        /** Signal nomme (ecrit par un ordinateur PioScript). */
        SIGNAL,
        /** Puissance de redstone recue par le panneau de gare. */
        REDSTONE,
        /** L'arret suivant a une place libre. */
        DEST_FREE;

        public static Type byId(int i) {
            return values()[Math.max(0, Math.min(values().length - 1, i))];
        }

        public boolean hasValue() {
            return this == TIME || this == INACTIVITY || this == ITEM || this == FUEL || this == FLUID || this == STATION_STOCK
                  || this == NETWORK_STOCK || this == SIGNAL || this == REDSTONE;
        }

        /** Comparaison reglable (>=, <=, =). */
        public boolean compares() {
            return this == ITEM || this == FUEL || this == FLUID || this == STATION_STOCK || this == NETWORK_STOCK || this == SIGNAL
                  || this == REDSTONE;
        }

        /** Ressource a choisir (objet ; fluide ou gaz pour FLUID ; les trois pour les stocks). */
        public boolean hasResource() {
            return this == ITEM || this == FLUID || this == STATION_STOCK || this == NETWORK_STOCK;
        }

        /** Seconde (temps), pour cent ou quantite : unite affichee. */
        public String unit() {
            return switch (this) {
                case TIME, INACTIVITY -> " s";
                case FUEL -> " %";
                case FLUID -> " mB";
                default -> "";
            };
        }
    }

    /** Famille de la ressource d'une condition : objet, fluide, gaz. */
    public static final int ITEM = 0, FLUID = 1, GAS = 2;

    public static final class Condition {

        public Type type = Type.TIME;
        /** Lien avec la condition precedente : vrai = OU, faux = ET. */
        public boolean or;
        public int value = 10;
        /** Comparaison (objet) : 0 >=, 1 <=, 2 =. */
        public int compare;
        @Nullable
        public ResourceLocation item;
        /** 0.20.5 : famille de {@link #item} ({@link #ITEM}, {@link #FLUID}, {@link #GAS}) ; nom du signal (SIGNAL). */
        public int kind;
        public String text = "";

        public Condition copy() {
            Condition c = new Condition();
            c.type = type;
            c.or = or;
            c.value = value;
            c.compare = compare;
            c.item = item;
            c.kind = kind;
            c.text = text;
            return c;
        }

        public boolean compare(long n) {
            return switch (compare) {
                case 1 -> n <= value;
                case 2 -> n == value;
                default -> n >= value;
            };
        }

        public Item itemOrAir() {
            return item == null ? Items.AIR : BuiltInRegistries.ITEM.get(item);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putByte("t", (byte) type.ordinal());
            t.putBoolean("or", or);
            t.putInt("v", value);
            t.putByte("c", (byte) compare);
            if (item != null) {
                t.putString("i", item.toString());
            }
            if (kind != 0) {
                t.putByte("k", (byte) kind);
            }
            if (!text.isEmpty()) {
                t.putString("s", text);
            }
            return t;
        }

        static Condition load(CompoundTag t) {
            Condition c = new Condition();
            c.type = Type.byId(t.getByte("t"));
            c.or = t.getBoolean("or");
            c.value = t.getInt("v");
            c.compare = Math.max(0, Math.min(2, t.getByte("c")));
            c.item = t.contains("i") ? ResourceLocation.tryParse(t.getString("i")) : null;
            c.kind = Math.max(0, Math.min(2, t.getByte("k")));
            String txt = t.getString("s");
            c.text = txt.length() > 32 ? txt.substring(0, 32) : txt;
            return c;
        }
    }

    public static final class Stop {

        public String station = "";
        public final List<Condition> conditions = new ArrayList<>();
        /** 0.20.5 : gare precise (position du panneau, livraisons) ; 0 : toutes les gares du nom. */
        public long target;

        public Stop copy() {
            Stop s = new Stop();
            s.station = station;
            s.target = target;
            for (Condition c : conditions) {
                s.conditions.add(c.copy());
            }
            return s;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("st", station);
            ListTag cs = new ListTag();
            for (Condition c : conditions) {
                cs.add(c.save());
            }
            t.put("c", cs);
            if (target != 0) {
                t.putLong("at", target);
            }
            return t;
        }

        static Stop load(CompoundTag c) {
            Stop stop = new Stop();
            String st = c.getString("st");
            stop.station = st.length() > 64 ? st.substring(0, 64) : st;
            stop.target = c.getLong("at");
            for (Tag ct : c.getList("c", Tag.TAG_COMPOUND)) {
                if (stop.conditions.size() < 8) {
                    stop.conditions.add(Condition.load((CompoundTag) ct));
                }
            }
            return stop;
        }
    }

    /** Arrets (et conditions) : liste sauvee. */
    public static ListTag saveStops(List<Stop> stops) {
        ListTag list = new ListTag();
        for (Stop s : stops) {
            list.add(s.save());
        }
        return list;
    }

    public static List<Stop> loadStops(ListTag list, int max) {
        List<Stop> out = new ArrayList<>();
        for (Tag t : list) {
            if (out.size() < max) {
                out.add(Stop.load((CompoundTag) t));
            }
        }
        return out;
    }

    public static ListTag saveConditions(List<Condition> conditions) {
        ListTag cs = new ListTag();
        for (Condition c : conditions) {
            cs.add(c.save());
        }
        return cs;
    }

    public static List<Condition> loadConditions(ListTag list) {
        List<Condition> out = new ArrayList<>();
        for (Tag ct : list) {
            if (out.size() < 8) {
                out.add(Condition.load((CompoundTag) ct));
            }
        }
        return out;
    }

    public final List<Stop> stops = new ArrayList<>();

    public Schedule copy() {
        Schedule s = new Schedule();
        for (Stop stop : stops) {
            s.stops.add(stop.copy());
        }
        return s;
    }

    public boolean isEmpty() {
        return stops.isEmpty();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("stops", saveStops(stops));
        return tag;
    }

    public static Schedule load(CompoundTag tag) {
        Schedule s = new Schedule();
        s.stops.addAll(loadStops(tag.getList("stops", Tag.TAG_COMPOUND), 32));
        return s;
    }

    /** Contexte d'evaluation des conditions d'un arret. */
    public interface Context {

        int secondsAtStop();

        int secondsIdle();

        boolean full();

        boolean empty();

        int count(Item item);

        boolean passenger();

        /** 0.20.5 : reserve d'energie de la locomotive (%). */
        default int fuel() {
            return 100;
        }

        /** Fluide ({@code gas} faux) ou gaz du train (mB) ; id null : tout fluide / tout gaz. */
        default long fluid(@Nullable ResourceLocation id, boolean gas) {
            return 0;
        }

        default boolean cargo() {
            return false;
        }

        /** Stock de la gare ou de son reseau robotique ({@code network}) pour une ressource. */
        default long stock(int kind, @Nullable ResourceLocation id, boolean network) {
            return 0;
        }

        default int signal(String name) {
            return 0;
        }

        default int redstone() {
            return 0;
        }

        default boolean destFree() {
            return true;
        }
    }

    public static boolean test(Condition c, Context ctx) {
        return switch (c.type) {
            case TIME -> ctx.secondsAtStop() >= c.value;
            case INACTIVITY -> ctx.secondsIdle() >= c.value;
            case FULL -> ctx.full();
            case EMPTY -> ctx.empty();
            case ITEM -> c.compare(ctx.count(c.itemOrAir()));
            case PASSENGER -> ctx.passenger();
            case NO_PASSENGER -> !ctx.passenger();
            case FUEL -> c.compare(ctx.fuel());
            case FLUID -> c.compare(ctx.fluid(c.item, c.kind == GAS));
            case CARGO -> ctx.cargo();
            case STATION_STOCK -> c.compare(ctx.stock(c.kind, c.item, false));
            case NETWORK_STOCK -> c.compare(ctx.stock(c.kind, c.item, true));
            case SIGNAL -> c.compare(ctx.signal(c.text));
            case REDSTONE -> c.compare(ctx.redstone());
            case DEST_FREE -> ctx.destFree();
        };
    }

    /** Conditions d'un arret remplies ? (groupes ET separes par OU ; liste vide : oui). */
    public static boolean ready(List<Condition> conditions, Context ctx) {
        if (conditions.isEmpty()) {
            return true;
        }
        boolean group = true;
        for (int i = 0; i < conditions.size(); i++) {
            Condition c = conditions.get(i);
            if (i > 0 && c.or) {
                if (group) {
                    return true;
                }
                group = true;
            }
            group &= test(c, ctx);
        }
        return group;
    }

    // ------------------------------------------------------------------ jetons de ressource (0.20.5)

    /** Jeton d'une ressource dans un nom de gare : {@code [i:id]} objet, {@code [f:id]} fluide, {@code [g:id]} gaz. */
    public static String token(int kind, ResourceLocation id) {
        return "[" + (kind == FLUID ? "f" : kind == GAS ? "g" : "i") + ":" + id + "]";
    }

    /** Jokers des interruptions remplaces par les ressources du train (null : laisse le joker). */
    public static String substitute(String name, @Nullable ResourceLocation item, @Nullable ResourceLocation fluid, @Nullable ResourceLocation gas) {
        String out = name;
        if (item != null) {
            out = out.replace("{objet}", token(ITEM, item)).replace("{item}", token(ITEM, item));
        }
        if (fluid != null) {
            out = out.replace("{fluide}", token(FLUID, fluid)).replace("{fluid}", token(FLUID, fluid));
        }
        if (gas != null) {
            out = out.replace("{gaz}", token(GAS, gas)).replace("{gas}", token(GAS, gas));
        }
        return out;
    }

    /** Le nom contient-il encore un joker ? */
    public static boolean hasJoker(String name) {
        return name.contains("{objet}") || name.contains("{item}") || name.contains("{fluide}") || name.contains("{fluid}") || name.contains("{gaz}")
              || name.contains("{gas}");
    }
}

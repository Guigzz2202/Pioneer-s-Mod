package fr.pioneers.gametest;

import it.unimi.dsi.fastutil.objects.AbstractObject2LongMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.6 : table des echeances des GameTests sure.
 * <p>
 * {@code GameTestInfo.tickInternal} parcourt sa table des echeances ({@code runAtTickTimeMap}, une {@code Object2LongOpenHashMap}) en
 * retirant les rappels faits. Or un rappel qui planifie a son tour ({@code runAfterDelay}, {@code onEachTick} et {@code failIfEver}, qui
 * inscrivent une echeance par tick restant) ajoute des entrees pendant ce parcours : la table se reorganise sous l'iterateur et le serveur
 * de tests plante (NullPointerException de fastutil, « this.wrapped is null »). Une cinquantaine de tests planifient ainsi depuis un rappel.
 * <p>
 * Remede : la table de chaque test est remplacee par {@link SafeMap}, dont le parcours se fait sur une copie ; le retrait d'un rappel fait se
 * fait dans la vraie table. Les echeances AJOUTEES pendant le parcours sont parcourues dans le meme passage, apres la copie (une echeance
 * due tourne donc dans le meme tick, comme les tests l'attendent : les repousser au tick suivant faisait echouer une vingtaine de tests,
 * runs 43793898982 et 43798426798). Pose par {@link Gt#begin} et, pour les tests qui ne l'appellent pas, au debut de chaque tick du serveur
 * de GameTests (jamais dans un serveur de jeu).
 * <p>
 * Pas de GameTest de garde : chaque test a sa propre serie (batch) et les series passent l'une apres l'autre ; un test de plus decale l'heure
 * du monde de toutes les series suivantes, et des tests sensibles a l'heure peuvent alors changer de resultat.
 */
@Mod.EventBusSubscriber(modid = "pioneers", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GtTickMap {

    @Nullable
    private static Field infoField, mapField, tickerField;

    private GtTickMap() {
    }

    /** Pose la table sure sur le test de ce helper (sans effet si elle y est deja). */
    public static void install(GameTestHelper h) {
        try {
            if (infoField == null) {
                infoField = field(GameTestHelper.class, GameTestInfo.class);
            }
            install((GameTestInfo) infoField.get(h));
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Banc ou version inattendue : on garde la table d'origine
        }
    }

    static void install(GameTestInfo info) throws ReflectiveOperationException {
        if (mapField == null) {
            mapField = field(GameTestInfo.class, Object2LongMap.class);
        }
        Object current = mapField.get(info);
        if (current instanceof SafeMap || !(current instanceof Object2LongMap<?>)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Object2LongMap<Runnable> old = (Object2LongMap<Runnable>) current;
        SafeMap safe = new SafeMap();
        safe.putAll(old);
        mapField.set(info, safe);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.getServer() instanceof GameTestServer)) {
            return;
        }
        try {
            if (tickerField == null) {
                tickerField = field(GameTestTicker.class, Collection.class);
            }
            for (Object info : (Collection<?>) tickerField.get(GameTestTicker.SINGLETON)) {
                install((GameTestInfo) info);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // idem : table d'origine
        }
    }

    /** Premier champ d'instance du type donne (noms officiels ou SRG : on ne depend pas du nom). */
    private static Field field(Class<?> owner, Class<?> type) throws NoSuchFieldException {
        for (Field f : owner.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && type.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                return f;
            }
        }
        throw new NoSuchFieldException(owner.getSimpleName() + " : " + type.getSimpleName());
    }

    /** Table des echeances parcourue sur une copie : on peut y ajouter pendant le parcours. */
    static final class SafeMap extends Object2LongOpenHashMap<Runnable> {

        /** Passages de rattrapage au plus (rappels qui s'ajoutent sans fin dans le meme tick). */
        private static final int MAX_PASSES = 64;

        @Override
        public FastEntrySet<Runnable> object2LongEntrySet() {
            return new Snapshot();
        }

        /** Entrees actuelles de la vraie table, celles deja vues de ce parcours exceptees. */
        private void collect(List<Object2LongMap.Entry<Runnable>> into, Set<Runnable> seen) {
            for (Object2LongMap.Entry<Runnable> e : super.object2LongEntrySet()) {
                if (seen.add(e.getKey())) {
                    into.add(new AbstractObject2LongMap.BasicEntry<>(e.getKey(), e.getLongValue()));
                }
            }
        }

        private final class Snapshot extends AbstractObjectSet<Object2LongMap.Entry<Runnable>> implements FastEntrySet<Runnable> {

            @Override
            public int size() {
                return SafeMap.this.size();
            }

            @Override
            public ObjectIterator<Object2LongMap.Entry<Runnable>> fastIterator() {
                return iterator();
            }

            @Override
            public ObjectIterator<Object2LongMap.Entry<Runnable>> iterator() {
                List<Object2LongMap.Entry<Runnable>> items = new ArrayList<>(SafeMap.this.size());
                Set<Runnable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
                collect(items, seen);
                return new ObjectIterator<>() {
                    private int next, passes;
                    @Nullable
                    private Object2LongMap.Entry<Runnable> last;

                    @Override
                    public boolean hasNext() {
                        //copie epuisee : les echeances ajoutees pendant ce parcours passent a leur tour (meme tick)
                        if (next >= items.size() && passes < MAX_PASSES) {
                            passes++;
                            collect(items, seen);
                        }
                        return next < items.size();
                    }

                    @Override
                    public Object2LongMap.Entry<Runnable> next() {
                        if (!hasNext()) {
                            throw new NoSuchElementException();
                        }
                        last = items.get(next++);
                        return last;
                    }

                    @Override
                    public void remove() {
                        if (last == null) {
                            throw new IllegalStateException();
                        }
                        SafeMap.this.removeLong(last.getKey());
                        last = null;
                    }
                };
            }
        }
    }
}

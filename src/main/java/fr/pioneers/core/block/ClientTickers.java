package fr.pioneers.core.block;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.0.5-beta (lot 29, retour 40, recensement {@code tools/perf/tickers.sh}) : un bloc ne donne un TICKER CLIENT que si sa tile fait quelque
 * chose a chaque tick du client. Avant, toutes les machines de l'API ({@code MachineBlock}) et tous les blocs de l'age du bronze
 * ({@code BronzeBlock}) en enregistraient un, meme quand le crochet client de leur tile etait vide : 379 types de blocs sur 440, chacun tenu
 * par le jeu a chaque tick du client (enveloppe, verifications, profileur) pour rien.
 * <p>
 * La classe de la tile se lit sur une tile d'essai creee une fois par type (jamais posee dans le monde) ; reponse gardee par type. En cas de
 * doute (tile impossible a creer), le ticker est garde.
 */
public final class ClientTickers {

    private ClientTickers() {
    }

    private static final Map<BlockEntityType<?>, Boolean> NEEDED = new ConcurrentHashMap<>();

    /** Vrai si la tile du type redefinit le crochet client {@code hook} (sans parametre) quelque part sous la classe {@code base}. */
    public static boolean needed(BlockEntityType<?> type, BlockState state, Class<?> base, String hook) {
        Boolean known = NEEDED.get(type);
        if (known != null) {
            return known;
        }
        boolean need = true;
        try {
            BlockEntity probe = type.create(BlockPos.ZERO, state);
            if (probe != null) {
                need = overrides(probe.getClass(), base, hook);
            }
        } catch (Throwable e) {
            need = true;
        }
        NEEDED.put(type, need);
        return need;
    }

    /** Vrai si une classe entre {@code c} (comprise) et {@code base} (exclue) declare {@code hook()}. */
    public static boolean overrides(Class<?> c, Class<?> base, String hook) {
        if (!base.isAssignableFrom(c)) {
            return true;                                             //autre famille de tile : on ne sait pas, ticker garde
        }
        for (Class<?> k = c; k != null && k != base; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (m.getName().equals(hook) && m.getParameterCount() == 0) {
                    return true;
                }
            }
        }
        return false;
    }
}

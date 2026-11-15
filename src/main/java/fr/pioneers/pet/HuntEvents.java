package fr.pioneers.pet;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

/**
 * 1.0.5-beta (lot 5) : CROCHETS de la chasse pour le lot 10 (evenement « troupeau », competence Chasse), PRETS mais non branches ici
 * (specification §5.4 et §6) :
 * <ul>
 *   <li>{@link #highlight} : vrai si le bouton « Chasse » de l'interface du chien doit etre mis en avant (troupeau en cours pres du maitre) ;
 *   lu par {@link PetMenu} a l'ouverture et a chaque tick du menu ;</li>
 *   <li>{@link #scentBonus} : bonus de flair du maitre (competence Chasse niveau 10 : +0,5 = +50 %), multiplie la portee de pistage ;</li>
 *   <li>{@link #endHunt} : fin de la chasse (fin du troupeau, ou rappel) : le chien repasse en autonome (s'il a une niche) ou suit ;</li>
 *   <li>{@link #onHuntStart} / {@link #onHuntEnd} : abonnes (le lot 10 y compte les troupeaux trouves, l'XP de pistage).</li>
 * </ul>
 * <p>
 * 1.0.5-beta (lot 10) : BRANCHES par {@code fr.pioneers.hunt.HerdEvents} (troupeau actif pres du maitre, competence Chasse) ; deux crochets
 * de plus : {@link #herdNear} (un troupeau est a portee de la niche : le chien autonome grogne puis va chercher son maitre) et {@link #trail}
 * (piste d'un troupeau au-dela du flair : le chien en mode Chasse la suit).
 */
public final class HuntEvents {

    private HuntEvents() {
    }

    public static volatile BiPredicate<TamableAnimal, Player> highlight = (dog, master) -> false;
    public static volatile ToDoubleFunction<Player> scentBonus = master -> 0.0;
    /** 1.0.5-beta (lot 10) : un troupeau actif est-il a portee de ce chien (autonome) ? */
    public static volatile java.util.function.Predicate<TamableAnimal> herdNear = dog -> false;
    /** 1.0.5-beta (lot 10) : bete d'un troupeau la plus proche a {@code range} blocs (piste), ou null. */
    public static volatile java.util.function.BiFunction<TamableAnimal, Double, net.minecraft.world.entity.LivingEntity> trail = (dog, range) -> null;
    public static final List<Consumer<TamableAnimal>> onHuntStart = new CopyOnWriteArrayList<>();
    public static final List<Consumer<TamableAnimal>> onHuntEnd = new CopyOnWriteArrayList<>();

    public static boolean highlighted(TamableAnimal dog, Player master) {
        try {
            return highlight.test(dog, master);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static double bonus(Player master) {
        return master == null ? 0.0 : Math.max(0.0, scentBonus.applyAsDouble(master));
    }

    public static boolean herdNearby(TamableAnimal dog) {
        try {
            return herdNear.test(dog);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @org.jetbrains.annotations.Nullable
    public static net.minecraft.world.entity.LivingEntity trailOf(TamableAnimal dog, double range) {
        try {
            return trail.apply(dog, range);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static void started(TamableAnimal dog) {
        onHuntStart.forEach(c -> c.accept(dog));
    }

    /** Fin de la chasse : autonome si le chien a une niche, sinon suivre. */
    public static void endHunt(TamableAnimal dog) {
        if (dog instanceof Pet p && p.mode() == PetMode.HUNT) {
            p.setMode(p.home() != null ? PetMode.AUTONOMOUS : PetMode.FOLLOW);
            onHuntEnd.forEach(c -> c.accept(dog));
        }
    }
}

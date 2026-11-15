package fr.pioneers.pet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 1.0.5-beta (lot 5) : etat serveur d'un chien ou d'un chat : minuteries des buts autonomes (visite au maitre, sieste, peche,
 * aboiement), poisson porte vers le panier (sauve), dernier controle de la niche. Les heures sont en temps de jeu du monde (ticks).
 */
public final class PetBrain {

    public long nextVisit, visitUntil, napUntil, nextFish, lastBark, nextHomeCheck, nextGuard;
    public int stuck;
    /** 1.0.5-beta (lot 10) : troupeau senti (chien autonome) : grognement fait, va chercher son maitre jusqu'a cette heure. */
    public long herdAlertUntil;
    public ItemStack carried = ItemStack.EMPTY;
    /**
     * 1.0.5-beta (lot 28, retour 35) : menace relevee par {@link PetCore#tick} toutes les {@link PetReact#GUARD_SCAN} ticks (predateur du
     * territoire en autonome, a {@link PetReact#DEFEND_RANGE} blocs du chien ou du maitre sinon) ; lue par le but de garde.
     */
    @org.jetbrains.annotations.Nullable
    public net.minecraft.world.entity.LivingEntity threat;
    /** 1.0.5-beta (lot 28) : prochains essais (heure du monde) : chasse aux rats, pistage, tirage de la sieste. */
    public long nextPrey, nextHunt, nextNapRoll;
    /** 1.0.5-beta (lot 28) : coin de peche retenu (eau, rive), reverifie a chaque sortie. */
    @org.jetbrains.annotations.Nullable
    public net.minecraft.core.BlockPos fishWater, fishBank;
    /** GameTests seulement : la prise reussit a coup sur. */
    public boolean testCatch;
    /** Vrai juste apres une conversion (race posee) : les stats de la race sont appliquees au tick suivant. */
    public boolean statsDue = true;

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("visit", nextVisit);
        t.putLong("fish", nextFish);
        if (!carried.isEmpty()) {
            t.put("carried", carried.save(new CompoundTag()));
        }
        return t;
    }

    public void load(CompoundTag t) {
        nextVisit = t.getLong("visit");
        nextFish = t.getLong("fish");
        carried = t.contains("carried") ? ItemStack.of(t.getCompound("carried")) : ItemStack.EMPTY;
    }
}

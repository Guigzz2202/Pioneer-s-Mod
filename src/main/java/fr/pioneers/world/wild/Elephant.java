package fr.pioneers.world.wild;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 8, specification §4 « Savane ») : l'ELEPHANT, en troupeaux familiaux. Il DEFEND SES PETITS : un joueur a moins de 5 blocs
 * d'un eléphanteau, ou toute bete (ou joueur) qui s'en prend a un petit, fait barrir et charger TOUT LE TROUPEAU a 24 blocs ; attaque, un
 * adulte appelle aussi le troupeau ({@link #defend}) : terrible (degats 10, recul fort), il poursuit jusqu'a 15 s ou 28 blocs. Peau epaisse
 * (armure 8), presque aucun recul. Grande bete : pas d'un bloc, boite de 1,9 x 2,9 blocs, il ne casse AUCUN bloc. Que des os au butin : pas
 * d'ivoire a recuperer (choix ethique de la specification). Robes : gris, poudre de terre rouge ; defenses plus courtes chez la femelle.
 */
public class Elephant extends WildAnimal {

    int anger;

    public Elephant(EntityType<? extends Animal> type, Level level) {
        super(type, level, Wild8Rules.ELEPHANT);
        setMaxUpStep(GeneratedWild8.STEP[kind - GeneratedWild8.FIRST]);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.45, true));
        goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        goalSelector.addGoal(4, new TemptGoal(this, 1.0, Ingredient.of(food()), false));
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(6, new WildGoals.Herd(this));
        goalSelector.addGoal(7, new Wild8Goals.Idle(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.75));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    @Override
    protected int pickVariant(ServerLevelAccessor level, RandomSource random) {
        return Wild8Rules.coat(kind, random.nextDouble());
    }

    /** Tout le troupeau (adultes a 24 blocs, celui-ci compris) charge {@code foe} ; rend le nombre de defenseurs. */
    public int defend(LivingEntity foe) {
        if (foe instanceof Elephant || foe instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return 0;
        }
        int n = 0;
        for (WildAnimal w : kin(Wild8Rules.HERD_DEFEND)) {
            if (w instanceof Elephant e && !e.isBaby()) {
                e.rage(foe);
                n++;
            }
        }
        if (n == 0 && !isBaby()) {
            rage(foe);
            n = 1;
        }
        return n;
    }

    void rage(LivingEntity foe) {
        if (getTarget() != foe) {
            playOwn("trumpet");
        }
        setTarget(foe);
        anger = Wild8Rules.ELEPHANT_RAGE;
        setAction(WildRules.CHARGE);
    }

    public boolean angry() {
        return anger > 0 && getTarget() != null;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker) {
            defend(attacker);                                     //petit ou adulte attaque : tout le troupeau
        }
        return hit;
    }

    @Override
    protected boolean fleesWhenHurt() {
        return isBaby();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (isBaby()) {
            setTarget(null);
            return;
        }
        if (anger <= 0 && (tickCount + getId()) % 10 == 0) {           //1.0.5-beta (lot 29) : releve etale selon la bete
            guardCalves();
        }
        if (anger > 0) {
            LivingEntity t = getTarget();
            if (--anger <= 0 || t == null || !t.isAlive() || distanceTo(t) > Wild8Rules.ELEPHANT_GIVE_UP
                || t instanceof Player p && (p.isCreative() || p.isSpectator())) {
                anger = 0;
                setTarget(null);
                setAction(WildRules.IDLE);
            }
        }
    }

    /** Un joueur trop pres d'un petit (5 blocs), ou une bete qui s'en prend a un petit : le troupeau charge. */
    void guardCalves() {
        List<WildAnimal> herd = kin(Wild8Rules.HERD_DEFEND);
        for (WildAnimal w : herd) {
            if (!w.isBaby()) {
                continue;
            }
            Player p = level().getNearestPlayer(w.getX(), w.getY(), w.getZ(), Wild8Rules.CALF_GUARD, WildAnimal::scary);
            if (p != null && Wild8Rules.elephantDefends(p.distanceTo(w), false, false)) {
                defend(p);
                return;
            }
            for (Mob m : level().getEntitiesOfClass(Mob.class, w.getBoundingBox().inflate(12), m -> m.getTarget() == w && m.isAlive())) {
                defend(m);
                return;
            }
        }
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        //1.0.6 (Guillaume, 17/11 : pas beaucoup trop forts, « un genre de bruit de verre casse ») : le pas du ravageur ralenti craquait
        //comme du verre ; pas sourd et feutre du renifleur (gros animal), bas et grave, un pas tous les deux blocs (nextStep)
        playSound(SoundEvents.SNIFFER_STEP, 0.22F, 0.72F);
    }

    @Override
    protected float nextStep() {
        return (int) moveDist + 2.2F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 420;
    }

    @Override
    protected float getSoundVolume() {
        return 1.3F;
    }
}

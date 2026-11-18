package fr.pioneers.world.wild;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.util.RandomSource;

/**
 * 1.0.5-beta (lot 7, specification §4, §7) : le PIC-VERT, oiseau des forets. Il vole d'arbre en arbre, s'ACCROCHE AUX TRONCS
 * ({@link Wild7Goals.Perch} : corps dresse contre l'ecorce, queue en appui) et TAMBOURINE par salves (un coup par tick, entendu a 48 blocs :
 * {@link #drumBeat}) ; au sol il picore les fourmis. S'envole a 8 blocs d'un joueur. Moins nombreux en hiver (saisons du socle, oiseaux x0,6
 * et poids d'hiver). Male a moustache rouge, femelle a moustache noire. Plumes.
 */
public class Woodpecker extends WildBird {

    public Woodpecker(EntityType<? extends Animal> type, Level level) {
        super(type, level, Wild7Rules.WOODPECKER);
    }

    @Override
    protected void addOwnGoals() {
        goalSelector.addGoal(3, new Wild7Goals.Perch(this));
        goalSelector.addGoal(6, new Wild7Goals.Idle(this));
    }

    @Override
    protected int pickVariant(ServerLevelAccessor level, RandomSource random) {
        return sex() == WildRules.FEMALE ? 1 : 0;
    }

    /** La robe suit le sexe : moustache rouge (male), noire (femelle). */
    @Override
    public String coat() {
        return GeneratedWild7.COATS[kind - GeneratedWild7.FIRST][sex() == WildRules.FEMALE ? 1 : 0];
    }

    /** Un coup de bec sur le tronc (son du bois, haut, porte loin). */
    public void drumBeat() {
        net.minecraft.sounds.SoundEvent s = PiWild.sound(kind, "drum");
        if (s != null) {
            playSound(s, GeneratedWild7.DRUM_VOLUME, 1.6F + random.nextFloat() * 0.3F);
        }
    }

    public boolean drumming() {
        return clinging() && action() == WildRules.DISPLAY;
    }
}

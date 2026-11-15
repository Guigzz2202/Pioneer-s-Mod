package fr.pioneers.wild;

import fr.pioneers.season.Seasons;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 6, specification §4, §7) : le CERF et la BICHE. Hardes de 3 a 6 qui fuient ensemble, tres vite, en bonds. Le cerf porte
 * des BOIS qui tombent en hiver et repoussent au printemps ({@link WildRules#antlerGrowth}, moment de la chute propre a chaque cerf) : a la
 * chute, une chance ({@link GeneratedWild#ANTLER_SHED_CHANCE}) de laisser un bois de cerf au sol, une fois par an ; tue alors qu'il les porte,
 * il en donne un (et peut-etre le second). Brame a l'automne. Faon tachete.
 */
public class Deer extends WildAnimal {

    long shedYear = Long.MIN_VALUE;

    public Deer(EntityType<? extends Animal> type, Level level) {
        super(type, level, WildRules.DEER);
    }

    /** Taille des bois (0 a 1) : 0 pour la biche et le faon ; selon la saison pour le cerf (1 sans calendrier). */
    public float antlers() {
        if (!isMale() || isBaby()) {
            return 0F;
        }
        Seasons.Date d = date();
        if (d == null) {
            return 1F;
        }
        return (float) WildRules.antlerGrowth(d.season().ordinal(), d.inSeason() / d.length(), seed());
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || (tickCount + getId()) % 100 != 0 || !isMale() || isBaby()) {
            return;
        }
        Seasons.Date d = date();
        if (d != null && d.season() != Seasons.Season.SPRING && antlers() <= 0F && shedYear != d.year()) {
            shedYear = d.year();
            Item antler = PiWild.item("deer_antler");
            if (antler != null && random.nextDouble() < GeneratedWild.ANTLER_SHED_CHANCE) {
                spawnAtLocation(antler);
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(@NotNull DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        Item antler = PiWild.item("deer_antler");
        if (antler != null && antlers() >= 0.8F) {
            spawnAtLocation(antler);
            if (random.nextDouble() < GeneratedWild.ANTLER_SECOND) {
                spawnAtLocation(antler);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("antler_shed_year", shedYear);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("antler_shed_year")) {
            shedYear = tag.getLong("antler_shed_year");
        }
    }
}

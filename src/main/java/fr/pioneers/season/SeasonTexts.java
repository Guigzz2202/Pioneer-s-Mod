package fr.pioneers.season;

import fr.pioneers.season.Seasons.Date;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** 1.0.5-beta (lot 1) : textes du calendrier (minimap, calendrier, commande, fiche au regard), cles {@code season.pioneers.*}. */
public final class SeasonTexts {

    private SeasonTexts() {
    }

    public static MutableComponent name(Seasons.Season s) {
        return Component.translatable(s.key());
    }

    /** « Automne, j. 3/10 ». */
    public static MutableComponent date(Date d) {
        return Component.translatable("season.pioneers.short", name(d.season()), d.dayOfSeason(), d.length());
    }

    /** « Automne, jour 3 sur 10 ». */
    public static MutableComponent dateLong(Date d) {
        return Component.translatable("season.pioneers.long", name(d.season()), d.dayOfSeason(), d.length());
    }

    /** « Hiver dans 8 jours » (ou « demain »). */
    public static MutableComponent next(Date d) {
        int n = d.daysToNext();
        return n <= 1 ? Component.translatable("season.pioneers.next_tomorrow", name(d.season().next()))
              : Component.translatable("season.pioneers.next_in", name(d.season().next()), n);
    }

    /** Duree restante lisible : « 18 h » (heures de jeu, arrondies au-dessus) ou « 1 j ». */
    public static MutableComponent duration(long ticks) {
        long hours = Math.max(1, (ticks + 999) / 1000);
        return hours >= 24 ? Component.translatable("season.pioneers.days", (hours + 23) / 24) : Component.translatable("season.pioneers.hours", hours);
    }
}

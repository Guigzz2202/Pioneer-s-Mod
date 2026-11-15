package fr.pioneers.client.guide;

import fr.pioneers.client.guide.GuideBook.Chapter;
import fr.pioneers.client.guide.GuideBook.Page;
import fr.pioneers.guide.JourneyProgress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.jetbrains.annotations.Nullable;

/**
 * Progres du joueur dans le Periple du guide (refonte du guide, 1.0.1-beta), lus dans les progres (advancements) que le serveur a envoyes
 * au client (un progres fait est toujours envoye).
 * <ul>
 *     <li>etape suivie : page d'un chapitre du groupe {@link #JOURNEY} qui porte un progres ({@link Page#advancement()}) ; 1.0.3-beta (lot 2) :
 *     progres {@code pioneers:journey/...} decerne par le serveur ({@code guide/JourneyTracker}) quand la condition de l'etape est remplie ;</li>
 *     <li>prochaine etape (1.0.3-beta, lot 2) : calcul commun {@link JourneyProgress} sur TOUTES les etapes du Periple dans l'ordre du guide
 *     (la premiere non faite, sauf une etape nettement depassee) ; avant : la page apres la derniere etape cochee de l'age en cours, qui
 *     sautait les etapes faites dans le desordre ;</li>
 *     <li>age en cours : celui de la prochaine etape (le dernier age quand tout est fait).</li>
 * </ul>
 * Meme calcul dans la maquette {@code tools/gc/guide/guide_screen_preview.py} (fonction {@code progress}) et dans le controle hors jeu
 * {@code tools/guide/check_guide.py}.
 */
public final class GuideProgress {

    /** Groupe du sommaire des chapitres du Periple (un chapitre par age). */
    public static final String JOURNEY = "journey";

    /** Un age : indice du chapitre, etapes cochees et suivies, page de la derniere etape cochee (-1 : aucune). */
    public record Age(int chapter, int done, int total, int last) {

        public boolean complete() {
            return total > 0 && done == total;
        }
    }

    /** Une etape du Periple dans l'ordre du guide : chapitre et page. */
    public record Step(int chapter, int page) {}

    static final GuideProgress EMPTY = new GuideProgress(Set.of(), List.of(), List.of(), -1, -1, 0, List.of());

    private final Set<String> done;
    private final List<Age> ages;
    private final List<Step> steps;
    private final List<Step> upcoming;
    private final int current, nextChapter, nextPage;

    private GuideProgress(Set<String> done, List<Age> ages, List<Step> steps, int current, int nextChapter, int nextPage, List<Step> upcoming) {
        this.done = done;
        this.ages = ages;
        this.steps = steps;
        this.current = current;
        this.nextChapter = nextChapter;
        this.nextPage = nextPage;
        this.upcoming = upcoming;
    }

    /** Progres du joueur, a appeler quand le guide est l'ecran ouvert, ou sans ecran ouvert (voir {@link #readDone()}). */
    public static GuideProgress read(List<Chapter> chapters) {
        return compute(chapters, readDone());
    }

    /** Calcul pur (sans le jeu) d'apres les identifiants des progres faits ; {@code max} prochaines etapes gardees pour l'affichage. */
    static GuideProgress compute(List<Chapter> chapters, Set<String> done) {
        List<Age> ages = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        for (int c = 0; c < chapters.size(); c++) {
            Chapter chapter = chapters.get(c);
            if (!JOURNEY.equals(chapter.group())) {
                continue;
            }
            int total = 0, count = 0, last = -1;
            for (int p = 0; p < chapter.pages().size(); p++) {
                String id = chapter.pages().get(p).advancement();
                if (!id.isEmpty()) {
                    total++;
                    steps.add(new Step(c, p));
                    if (done.contains(id)) {
                        count++;
                        last = p;
                    }
                }
            }
            ages.add(new Age(c, count, total, last));
        }
        if (ages.isEmpty()) {
            return new GuideProgress(done, ages, steps, -1, -1, 0, List.of());
        }
        IntPredicate isDone = i -> done.contains(chapters.get(steps.get(i).chapter()).pages().get(steps.get(i).page()).advancement());
        List<Step> upcoming = new ArrayList<>();
        for (int i : JourneyProgress.upcoming(steps.size(), isDone, 3)) {
            upcoming.add(steps.get(i));
        }
        int next = JourneyProgress.next(steps.size(), isDone);
        int nextChapter, nextPage;
        if (next >= 0) {
            nextChapter = steps.get(next).chapter();
            nextPage = steps.get(next).page();
        } else {                                               //tout est fait : la derniere etape
            nextChapter = steps.isEmpty() ? ages.get(ages.size() - 1).chapter() : steps.get(steps.size() - 1).chapter();
            nextPage = steps.isEmpty() ? 0 : steps.get(steps.size() - 1).page();
        }
        int current = ages.size() - 1;
        for (int i = 0; i < ages.size(); i++) {
            if (ages.get(i).chapter() == nextChapter) {
                current = i;
            }
        }
        return new GuideProgress(done, List.copyOf(ages), List.copyOf(steps), current, nextChapter, nextPage, List.copyOf(upcoming));
    }

    /**
     * Identifiants des progres faits : rejoue la liste du client par un auditeur provisoire (comme {@code EarthMusic.readProgress}).
     * Seulement quand le guide est l'ecran ouvert : l'ecran des progres de Minecraft est lui-meme l'auditeur quand il est ouvert.
     */
    private static Set<String> readDone() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        //1.0.3-beta (lot 2) : aussi sans ecran ouvert (affichage a l'ecran) ; jamais pendant l'ecran des progres de Minecraft (il est l'auditeur)
        if (connection == null || !(minecraft.screen == null || minecraft.screen instanceof GuideScreen)) {
            return Set.of();
        }
        Set<String> done = new HashSet<>();
        ClientAdvancements advancements = connection.getAdvancements();
        try {
            advancements.setListener(new ClientAdvancements.Listener() {
                @Override
                public void onUpdateAdvancementProgress(Advancement advancement, AdvancementProgress progress) {
                    if (progress.isDone()) {
                        done.add(advancement.getId().toString());
                    }
                }

                @Override
                public void onSelectedTabChanged(@Nullable Advancement advancement) {
                }

                @Override
                public void onAddAdvancementRoot(Advancement advancement) {
                }

                @Override
                public void onRemoveAdvancementRoot(Advancement advancement) {
                }

                @Override
                public void onAddAdvancementTask(Advancement advancement) {
                }

                @Override
                public void onRemoveAdvancementTask(Advancement advancement) {
                }

                @Override
                public void onAdvancementsCleared() {
                }
            });
        } finally {
            advancements.setListener(null);
        }
        return done;
    }

    /** Vrai si la page est une etape du Periple suivie par un progres. */
    public static boolean tracked(Chapter chapter, Page page) {
        return JOURNEY.equals(chapter.group()) && !page.advancement().isEmpty();
    }

    /** Vrai si le progres de la page est fait. */
    public boolean isDone(Page page) {
        return !page.advancement().isEmpty() && done.contains(page.advancement());
    }

    /** Les ages du Periple, dans l'ordre du guide. */
    public List<Age> ages() {
        return ages;
    }

    /** Indice (dans {@link #ages()}) de l'age en cours, -1 sans Periple. */
    public int current() {
        return current;
    }

    /** L'age du chapitre d'indice {@code chapter}, ou null s'il n'est pas du Periple. */
    @Nullable
    public Age age(int chapter) {
        for (Age age : ages) {
            if (age.chapter() == chapter) {
                return age;
            }
        }
        return null;
    }

    /** Les etapes du Periple dans l'ordre du guide. */
    public List<Step> steps() {
        return steps;
    }

    /** Les trois prochaines etapes au plus (la prochaine en tete), pour l'affichage a l'ecran. */
    public List<Step> upcoming() {
        return upcoming;
    }

    /** Identifiants des progres faits. */
    public Set<String> done() {
        return done;
    }

    /** Chapitre et page de la prochaine etape (-1 sans Periple). */
    public int nextChapter() {
        return nextChapter;
    }

    public int nextPage() {
        return nextPage;
    }
}

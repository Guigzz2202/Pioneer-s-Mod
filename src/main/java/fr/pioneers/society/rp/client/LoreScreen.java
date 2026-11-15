package fr.pioneers.society.rp.client;

import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 1.9 (MAJ RP) : lecture d'un ecrit sur le parchemin du Carnet : entree du Codex (titre, auteur, date en annees de l'Intendance, genre,
 * texte) ou note de parchemin (quelques lignes, sans auteur ni date). Le retour ramene a l'ecran d'origine.
 */
public class LoreScreen extends ParchmentScreen {

    @Nullable
    private final Screen parent;
    private final String id;
    private final boolean note;
    /** Arguments du texte (code de la Note de service d'une console). */
    private final Object[] args;

    public LoreScreen(@Nullable Screen parent, String id, Object... args) {
        super(Component.translatable("codex.pioneers." + id + ".title"));
        this.parent = parent;
        this.id = id;
        this.note = id.startsWith("note_");
        this.args = args;
    }

    @Override
    protected void drawContents(GuiGraphics g, int mx, int my, float pt) {
        int x = 40, w = PAGE_W - 80;
        //1.9.1 (audit des ecrans du bloc 2) : titre, auteur et genre bornes a la page (reduits puis coupes avec infobulle)
        if (note) {
            fitCentered(g, Component.translatable("codex.pioneers.chapter.11"), PAGE_W / 2, 24, w, DIM, 1F);
            Component t = Component.translatable("codex.pioneers." + id).withStyle(s -> s.withItalic(true));
            int h = Math.min(wrappedHeight(t, w), PAGE_H - 90);
            wrap(g, t, x, Math.max(40, (PAGE_H - h) / 2 - 10), w, INK, 1F, (PAGE_H - 90) / 10);
        } else {
            CodexBook.Entry e = CodexBook.get(id);
            fitCentered(g, Component.translatable("codex.pioneers." + id + ".title"), PAGE_W / 2, 16, w, INK, 1F);
            MutableComponent by = Component.translatable("codex.pioneers." + id + ".author").copy();
            if (e != null && e.date() != null) {
                by.append(" — ").append(Component.translatable("pioneer.pioneers.year", e.date()));
            }
            fitCentered(g, by, PAGE_W / 2, 28, w, DIM, 1F);
            if (e != null) {
                fitCentered(g, Component.translatable("codex.pioneers.genre." + e.genre()), PAGE_W / 2, 38, w, FAINT, 1F);
            }
            separator(g, x, 50, w);
            Component text = Component.translatable("codex.pioneers." + id + ".text", args);
            int top = 56, h = PAGE_H - 56 - 32;
            int content = wrappedHeight(text, w);
            scrollArea(0, x, top, w, h, content);
            beginClip(g, x, top, w, h);
            wrapped(g, text, x, top - scroll[0], w, TEXT);
            endClip(g);
        }
        button(g, mx, my, PAGE_W / 2 - 40, PAGE_H - 26, 80, 16, Component.translatable("pioneer.pioneers.back"), true, this::onClose);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}

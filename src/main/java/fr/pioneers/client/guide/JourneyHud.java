package fr.pioneers.client.guide;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.Pioneers;
import fr.pioneers.client.PiKeys;
import fr.pioneers.client.guide.GuideBook.Chapter;
import fr.pioneers.client.guide.GuideBook.Page;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.3-beta (lot 2, decision de Guillaume : « etapes du guide epinglees a l'ecran ») : la prochaine etape du Periple (1 a 3 etapes) affichee
 * PENDANT LE JEU, par defaut en haut au milieu : icone du but, titre de l'etape, objets reunis (objets de la page dans l'inventaire / objets
 * de la page), rappel des touches (J : sa page du guide ; Maj + J : sa recette dans JEI, si JEI est la). Discret : rien quand un ecran est
 * ouvert, interface cachee (F1) ou ecran de debogage (F3) ; sous les barres de boss et le bandeau du haut des messages du pionnier.
 * Quand une etape vient d'etre faite : « Etape faite » quelques secondes. Reglages : {@link JourneyHudConfig} (page « Affichage a l'ecran »
 * du guide). Progres relus chaque seconde ({@link GuideProgress#read}, sans ecran ouvert) ; calcul de la prochaine etape :
 * {@code guide/JourneyProgress}.
 */
public final class JourneyHud {

    static final int W = 190, ROW = 24, PAD = 4, HINT = 9, DONE_MS = 3500;
    static final int C_BG = 0xC8101820, C_EDGE = 0xFFFFD27A, C_TITLE = 0xFFFFFFFF, C_DIM = 0xFF9FB0C2, C_OK = 0xFF6BD48A, C_TRACK = 0xFF26323F,
          C_BAR = 0xFF55CCFF;

    private static GuideProgress progress = GuideProgress.EMPTY;
    private static Set<String> known;
    private static String doneTitle = "";
    private static long doneAt;
    private static int ticks, bossBottom;
    /** 1.0.5-beta (lot 26) : bas (px d'interface) du Periple pose en haut au centre au dernier rendu, 0 sinon (lu par le bandeau des messages). */
    private static int topCenterBottom;

    /** 1.0.5-beta (lot 26) : bas du Periple en haut au centre (0 : ailleurs ou masque) ; le bandeau des messages se pose dessous. */
    public static int topCenterBottom() {
        return topCenterBottom;
    }

    private JourneyHud() {
    }

    /** Enregistrement de l'affichage (bus MOD, client). */
    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {

        private Registration() {
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("journey", (gui, graphics, partialTick, width, height) -> render(graphics, width, height));
        }
    }

    /** Evenements du jeu (bus FORGE, client) : relecture des progres, touche, place des barres de boss. */
    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events {

        private Events() {
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                known = null;
                progress = GuideProgress.EMPTY;
                return;
            }
            if (mc.screen == null && ++ticks % 20 == 0) {
                refresh();
            }
            while (PiKeys.JOURNEY.consumeClick()) {
                if (mc.screen == null) {
                    openStep(Screen.hasShiftDown());
                }
            }
        }

        @SubscribeEvent
        public static void frame(RenderGuiEvent.Pre event) {
            bossBottom = 0;
        }

        @SubscribeEvent
        public static void boss(CustomizeGuiOverlayEvent.BossEventProgress event) {
            bossBottom = Math.max(bossBottom, event.getY() + event.getIncrement());
        }
    }

    /** Relit les progres ; une etape nouvellement faite (hors premiere lecture) declenche « Etape faite ». */
    static void refresh() {
        List<Chapter> chapters = GuideBook.chapters();
        GuideProgress p = GuideProgress.read(chapters);
        if (p.steps().isEmpty()) {
            return;
        }
        Set<String> done = new HashSet<>(p.done());
        if (known != null) {
            for (GuideProgress.Step s : p.steps()) {
                Page page = chapters.get(s.chapter()).pages().get(s.page());
                if (done.contains(page.advancement()) && !known.contains(page.advancement())) {
                    doneTitle = page.title();
                    doneAt = System.currentTimeMillis();
                }
            }
        }
        known = done;
        progress = p;
    }

    /** Touche J : la page de l'etape ; Maj + J : la recette de son but dans JEI (sinon la page). */
    static void openStep(boolean jei) {
        List<GuideProgress.Step> next = progress.upcoming();
        if (next.isEmpty()) {
            GuideScreen.open(null);
            return;
        }
        GuideProgress.Step s = next.get(0);
        Page page = GuideBook.chapters().get(s.chapter()).pages().get(s.page());
        ItemStack target = !page.goal().isEmpty() ? page.goal().get(0) : page.items().isEmpty() ? ItemStack.EMPTY : page.items().get(0);
        if (jei && GuideLinks.showInJei(target, false)) {
            return;
        }
        GuideScreen.openAt(s.chapter(), s.page());
    }

    /** Objets de la page presents dans l'inventaire, et nombre d'objets de la page (sans doublon). */
    static int[] gathered(Page page, Inventory inv) {
        Set<Item> want = new HashSet<>();
        for (ItemStack s : page.items()) {
            want.add(s.getItem());
        }
        Set<Item> have = new HashSet<>();
        for (List<ItemStack> part : List.of(inv.items, inv.armor, inv.offhand)) {
            for (ItemStack s : part) {
                if (!s.isEmpty() && want.contains(s.getItem())) {
                    have.add(s.getItem());
                }
            }
        }
        return new int[]{have.size(), want.size()};
    }

    static void render(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui || mc.options.renderDebug || !JourneyHudConfig.enabled()) {
            fr.pioneers.client.look.HudZones.clear("journey");
            topCenterBottom = 0;
            return;
        }
        List<Chapter> chapters = GuideBook.chapters();
        List<GuideProgress.Step> next = progress.upcoming();
        boolean showDone = System.currentTimeMillis() - doneAt < DONE_MS && !doneTitle.isEmpty();
        if (next.isEmpty() && !showDone) {
            fr.pioneers.client.look.HudZones.clear("journey");
            topCenterBottom = 0;
            return;
        }
        List<GuideProgress.Step> rows = new ArrayList<>(next.subList(0, Math.min(next.size(), JourneyHudConfig.steps())));
        float k = JourneyHudConfig.size().scale;
        int h = PAD * 2 + rows.size() * ROW + (rows.isEmpty() ? 0 : HINT) + (showDone ? 12 : 0);
        int sw = (int) (width / k), sh = (int) (height / k);
        //1.0.5-beta (lot 26, retour 16) : point d'ancrage commun (HudLayouts.journey) : en bas, le bord bas ne bouge plus quand une etape
        //s'ajoute ou disparait ; en haut au centre, seules les barres de boss le decalent (le bandeau des messages se pose sous le Periple)
        fr.pioneers.client.look.HudAnchor anchor = switch (JourneyHudConfig.position()) {
            case TOP_LEFT -> fr.pioneers.client.look.HudAnchor.TOP_LEFT;
            case TOP_RIGHT -> fr.pioneers.client.look.HudAnchor.TOP_RIGHT;
            case BOTTOM_LEFT -> fr.pioneers.client.look.HudAnchor.BOTTOM_LEFT;
            case BOTTOM_RIGHT -> fr.pioneers.client.look.HudAnchor.BOTTOM_RIGHT;
            default -> fr.pioneers.client.look.HudAnchor.TOP_CENTER;
        };
        int[] jb = fr.pioneers.client.look.HudLayouts.journey(anchor, sw, sh, W, h, (int) (bossBottom / k));
        int x = jb[0], y = jb[1];
        h = jb[3] - jb[1];
        topCenterBottom = anchor == fr.pioneers.client.look.HudAnchor.TOP_CENTER ? (int) Math.ceil((y + h) * k) : 0;
        //1.0.5-beta (lot 14) : place prise, evitee par la fiche au regard
        fr.pioneers.client.look.HudZones.mark("journey", (int) (x * k), (int) (y * k), (int) Math.ceil((x + W) * k), (int) Math.ceil((y + h) * k));
        Font font = mc.font;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.scale(k, k, 1);
        g.fill(x, y, x + W, y + h, C_BG);
        g.fill(x, y, x + 1, y + h, C_EDGE);
        int ry = y + PAD;
        Texts t = texts(mc, font, chapters, rows, showDone);
        if (showDone) {
            check(g, x + 6, ry + 1, C_OK);
            small(g, font, t.done(), x + 17, ry + 1, C_OK, 0.7F);
            ry += 12;
        }
        for (int i = 0; i < t.rows().size(); i++) {
            RowText r = t.rows().get(i);
            g.fill(x + 4, ry + 1, x + 22, ry + 19, i == 0 ? 0xFF3A4A5A : 0xFF222C36);
            g.renderItem(r.icon(), x + 5, ry + 2);
            small(g, font, r.head(), x + 26, ry + 1, C_DIM, 0.6F);
            small(g, font, r.title(), x + 26, ry + 7, i == 0 ? C_TITLE : C_DIM, i == 0 ? 0.8F : 0.7F);
            if (r.need() > 0) {
                int bx0 = x + 26, bx1 = x + W - 46, by = ry + 17;
                g.fill(bx0, by, bx1, by + 2, C_TRACK);
                g.fill(bx0, by, bx0 + (bx1 - bx0) * r.got() / r.need(), by + 2, r.got() == r.need() ? C_OK : C_BAR);
                small(g, font, r.count(), x + W - 42, ry + 15, r.got() == r.need() ? C_OK : C_DIM, 0.6F);
            }
            ry += ROW;
        }
        if (!t.rows().isEmpty()) {
            small(g, font, t.hint(), x + 6, ry, 0xFF7F8EA0, 0.55F);
        }
        pose.popPose();
    }

    /** Textes d'une ligne d'etape, deja coupes a la largeur. */
    private record RowText(ItemStack icon, String head, String title, int got, int need, String count) {
    }

    /** Textes du Periple pour un tick du client (cle : heure, progres, nombre d'etapes, « Etape faite », langue). */
    private record Texts(long tick, GuideProgress progress, int steps, boolean showDone, String doneTitle, String lang, String done,
                         List<RowText> rows, String hint) {
    }

    @org.jetbrains.annotations.Nullable
    private static Texts texts;

    /**
     * 1.0.5-beta (lot 29, reprise du §2 « client ») : les textes du Periple (traductions, coupes a la largeur par {@link #fit}, objets reunis
     * comptes dans l'inventaire) ne changent qu'au tick : ils sont calcules UNE FOIS PAR TICK DU CLIENT et non plus a chaque image (avant : 6
     * a 8 traductions, 7 coupes et 3 parcours de l'inventaire par image, soit 3 a 7 fois plus a 60 - 144 images/s). Dessin identique.
     */
    private static Texts texts(Minecraft mc, Font font, List<Chapter> chapters, List<GuideProgress.Step> rows, boolean showDone) {
        long tick = mc.level == null ? Long.MIN_VALUE : mc.level.getGameTime();
        String lang = mc.options.languageCode;
        Texts t = texts;
        if (t != null && t.tick() == tick && t.progress() == progress && t.steps() == rows.size() && t.showDone() == showDone
            && t.doneTitle().equals(doneTitle) && t.lang().equals(lang)) {
            return t;
        }
        String done = "";
        if (showDone) {
            done = fit(font, Component.translatable("hud.pioneers.journey.done").getString() + " : " + doneTitle, (W - 22) / 0.7F);
        }
        List<RowText> out = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            GuideProgress.Step s = rows.get(i);
            Chapter c = chapters.get(s.chapter());
            Page page = c.pages().get(s.page());
            ItemStack icon = !page.goal().isEmpty() ? page.goal().get(0) : page.items().isEmpty() ? c.icon() : page.items().get(0);
            String head = fit(font, (i == 0 ? Component.translatable("hud.pioneers.journey.next").getString() : "") + c.shortTitle(), (W - 30) / 0.6F);
            String title = fit(font, page.title(), (W - 30) / (i == 0 ? 0.8F : 0.7F));
            int[] n = gathered(page, mc.player.getInventory());
            String count = n[1] > 0 ? Component.translatable("hud.pioneers.journey.items", n[0], n[1]).getString() : "";
            out.add(new RowText(icon, head, title, n[0], n[1], count));
        }
        String hint = "";
        if (!rows.isEmpty()) {
            String key = PiKeys.JOURNEY.getTranslatedKeyMessage().getString();
            hint = fit(font, GuideLinks.jeiAvailable() ? Component.translatable("hud.pioneers.journey.keys_jei", key, key).getString()
                  : Component.translatable("hud.pioneers.journey.keys", key).getString(), (W - 10) / 0.55F);
        }
        t = new Texts(tick, progress, rows.size(), showDone, doneTitle, lang, done, out, hint);
        texts = t;
        return t;
    }

    private static void small(GuiGraphics g, Font font, String text, float x, float y, int color, float scale) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, true);
        pose.popPose();
    }

    private static String fit(Font font, String text, float max) {
        if (font.width(text) <= max) {
            return text;
        }
        String s = text;
        while (s.length() > 1 && font.width(s + "…") > max) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    /** Coche dessinee (8 x 7 px), comme celle du guide. */
    private static void check(GuiGraphics g, int x, int y, int color) {
        int[] dy = {3, 4, 5, 4, 3, 2, 1, 0};
        for (int dx = 0; dx < dy.length; dx++) {
            g.fill(x + dx, y + dy[dx], x + dx + 1, y + dy[dx] + 2, color);
        }
    }
}

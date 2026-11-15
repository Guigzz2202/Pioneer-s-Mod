package fr.pioneers.integration.jei;

import fr.pioneers.Pioneers;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.core.Texts;
import fr.pioneers.space.research.ResearchEffects;
import fr.pioneers.space.research.ResearchNode;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base des categories JEI du mod : titre, icone, taille, et une boite a outils commune pour que toutes les fenetres se ressemblent
 * (jauges de gaz et de fluide encadrees, emplacements de donnees, pied de page energie / duree, ligne de recherche requise).
 * <p>
 * 1.11.3 (lot E3) : CHARTE JEI ({@link JeiDraw}). La categorie dessine elle-meme le cadre (fond sombre, en-tete icone + nom + pastille, pied de
 * page a pastilles, badge MK en bas a droite) : les sous-classes ne decrivent que leur CONTENU, dans leurs coordonnees d'avant (0, 0 = coin du
 * contenu, {@link #cw()} x {@link #ch()}) : {@link #setContent}, {@link #extras}, {@link #drawContent}, {@link #contentTooltip} ; elles
 * choisissent leur badge ({@link #badge}), leur titre d'en-tete ({@link #headerTitle}) et leurs pastilles ({@link #chips}). Les cases de JEI
 * prennent le fond de la charte selon leur role, les fleches de JEI deviennent celles de la charte (avec la duree) : voir {@link JeiOffset}.
 */
abstract class PiCategory<T> implements IRecipeCategory<T> {

    /** Couleurs des textes sur le fond sombre de la charte (avant la 1.11.3 : texte sombre sur le fond gris de JEI). */
    protected static final int TEXT = JeiDraw.LIGHT, TEXT_LIGHT = JeiDraw.DIM, GOOD = JeiDraw.GOOD, WARN = JeiDraw.WARN;
    /** Hauteur d'une ligne de texte a l'echelle du pied de page. */
    protected static final int LINE = 9;
    protected static final float SMALL = 0.75F;
    protected final RecipeType<T> type;
    protected final IGuiHelper helper;
    private final Component title;
    private final IDrawable icon;
    private final int width, height;
    private int footLines = 1;
    private JeiDraw.Badge family = JeiDraw.Badge.of(JeiDraw.Family.GUIDE);

    protected PiCategory(IGuiHelper helper, RecipeType<T> type, String titleKey, ItemLike icon, int width, int height) {
        this.type = type;
        this.helper = helper;
        this.title = Component.translatable(titleKey);
        //1.0.3-beta (lot 6, Guillaume : « ne pas utiliser d'image de machine » pour les pages transverses) : pictogramme dessine
        //(tools/gc/icons/draw_jei.py -> GeneratedJeiIcons) ; les categories propres a une machine gardent la machine
        String name = JeiOrder.name(type);
        this.icon = GeneratedJeiIcons.NAMES.contains(name) ? new Drawn(Pioneers.rl("textures/gui/jei/" + name + ".png"))
                    : helper.createDrawableItemLike(icon);
        this.width = width;
        this.height = height;
    }

    /** Famille de la categorie : badge par defaut (watermark en bas a droite) et couleur de l'en-tete. */
    protected final void family(JeiDraw.Family f) {
        this.family = JeiDraw.Badge.of(f);
    }

    /** Nombre de lignes du pied de page (1 par defaut : pastilles et badge ; 2 pour les categories riches en conditions). */
    protected final void footerLines(int lines) {
        this.footLines = Math.max(1, lines);
    }

    @NotNull
    @Override
    public RecipeType<T> getRecipeType() {
        return type;
    }

    @NotNull
    @Override
    public Component getTitle() {
        return title;
    }

    @NotNull
    @Override
    public IDrawable getIcon() {
        return icon;
    }

    /** Largeur totale (contenu + marges de la charte). */
    @Override
    public int getWidth() {
        return width + 2 * JeiDraw.PAD;
    }

    /** Hauteur totale (en-tete, contenu, pied de page). */
    @Override
    public int getHeight() {
        return footTop() + footLines * JeiDraw.FOOT_LINE + JeiDraw.BOTTOM;
    }

    /** Largeur du contenu. */
    protected final int cw() {
        return width;
    }

    /** Hauteur du contenu. */
    protected final int ch() {
        return height;
    }

    /** Haut du pied de page. */
    private int footTop() {
        return JeiDraw.HEADER + JeiDraw.GAP + height + JeiDraw.GAP;
    }

    private static final int DX = JeiDraw.PAD, DY = JeiDraw.HEADER + JeiDraw.GAP;

    // ------------------------------------------------------------------ ce que decrivent les sous-classes

    /** Cases du contenu (coordonnees du contenu). */
    protected abstract void setContent(@NotNull IRecipeLayoutBuilder builder, @NotNull T recipe, @NotNull IFocusGroup focuses);

    /** Widgets du contenu (fleches, zones...), coordonnees du contenu. */
    protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull T recipe, @NotNull IFocusGroup focuses) {
    }

    /** Dessin du contenu (coordonnees du contenu). */
    protected void drawContent(@NotNull T recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
    }

    /** Infobulle d'une zone du contenu (coordonnees du contenu). */
    @NotNull
    protected List<Component> contentTooltip(@NotNull T recipe, @NotNull IRecipeSlotsView view, double mouseX, double mouseY) {
        return List.of();
    }

    /** Badge du pied de page (watermark) : tier MK de la recette, sinon la famille de la categorie. */
    protected JeiDraw.Badge badge(T recipe) {
        return family;
    }

    /** Titre de l'en-tete : le nom de la categorie (machine), ou celui de l'entree (productions, guides, reacteurs...). */
    protected Component headerTitle(T recipe) {
        return title;
    }

    /** Icone de l'en-tete : celle de la categorie, ou celle de l'entree (controleur du multibloc...). */
    protected IDrawable headerIcon(T recipe) {
        return icon;
    }

    /** Pastilles du pied de page (duree, energie, temperature, pression, conditions...). */
    protected List<JeiDraw.Chip> chips(T recipe) {
        return List.of();
    }

    // ------------------------------------------------------------------ charte (finales)

    @Override
    public final void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull T recipe, @NotNull IFocusGroup focuses) {
        setContent(new JeiOffset.Layout(builder, DX, DY), recipe, focuses);
    }

    @Override
    public final void createRecipeExtras(@NotNull IRecipeExtrasBuilder builder, @NotNull T recipe, @NotNull IFocusGroup focuses) {
        extras(new JeiOffset.Extras(builder, DX, DY), recipe, focuses);
    }

    private List<JeiDraw.Placed> placed(T recipe) {
        JeiDraw.Badge b = badge(recipe);
        int w = getWidth();
        return JeiDraw.layout(chips(recipe), JeiDraw.PAD, footTop(), w - 2 * JeiDraw.PAD, footLines, JeiDraw.badgeWidth(b) + 4);
    }

    @Override
    public final void draw(@NotNull T recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        int w = getWidth(), h = getHeight();
        JeiDraw.Badge b = badge(recipe);
        JeiDraw.frame(graphics, w, h, b.rgb(), footTop());
        JeiDraw.header(graphics, w, headerIcon(recipe), headerTitle(recipe), b.rgb());
        graphics.pose().pushPose();
        graphics.pose().translate(DX, DY, 0);
        drawContent(recipe, view, graphics, mouseX - DX, mouseY - DY);
        graphics.pose().popPose();
        JeiDraw.chips(graphics, placed(recipe));
        JeiDraw.badge(graphics, w - JeiDraw.PAD - JeiDraw.badgeWidth(b), h - JeiDraw.BOTTOM - JeiDraw.BADGE_H - 1, b);
    }

    @NotNull
    @Override
    public final List<Component> getTooltipStrings(@NotNull T recipe, @NotNull IRecipeSlotsView view, double mouseX, double mouseY) {
        if (mouseY >= footTop() - 1) {
            for (JeiDraw.Placed p : placed(recipe)) {
                if (p.contains(mouseX, mouseY)) {
                    List<Component> out = new ArrayList<>();
                    out.add(p.chip().text().copy().withStyle(s -> s.withColor(p.chip().color() & 0xFFFFFF)));
                    out.addAll(p.chip().tip());
                    return out;
                }
            }
            return List.of();
        }
        if (mouseY < JeiDraw.HEADER) {
            //Titre coupe : le nom complet au survol de l'en-tete
            Component t = headerTitle(recipe);
            return font().width(t) > getWidth() - 29 ? List.of(t) : List.of();
        }
        return contentTooltip(recipe, view, mouseX - DX, mouseY - DY);
    }

    // ------------------------------------------------------------------ emplacements

    /** Cadre de jauge de la charte (fluide, gaz), a la taille voulue. */
    /** 1.0.3-beta (lot 6) : icone dessinee d'une categorie transverse (texture 32 x 32 affichee a 16 x 16). */
    record Drawn(net.minecraft.resources.ResourceLocation texture) implements IDrawable {

        @Override
        public int getWidth() {
            return 16;
        }

        @Override
        public int getHeight() {
            return 16;
        }

        @Override
        public void draw(@NotNull GuiGraphics graphics, int x, int y) {
            graphics.blit(texture, x, y, 16, 16, 0, 0, 32, 32, 32, 32);
        }
    }

    protected record Frame(int width, int height) implements IDrawable {

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public void draw(@NotNull GuiGraphics graphics, int x, int y) {
            JeiDraw.gaugeFrame(graphics, x, y, width, height);
        }
    }

    /**
     * 1.0.5-beta (lot 44, retour 62) : case « OUTIL » d'une recette (matrice, forme d'extrusion, catalyseur... : lu dans la case Outil de la
     * machine) : fond des outils de la charte, chance du retour lisible sur la case, infobulle (case reservee ; rendu n fois sur 100, jamais
     * consomme, s'use, ou consomme par cette recette). Entree pour JEI (R / U inchanges).
     */
    protected static IRecipeSlotBuilder toolSlot(IRecipeLayoutBuilder builder, int x, int y, List<net.minecraft.world.item.ItemStack> stacks,
          boolean returned, float keep, boolean wear) {
        IRecipeSlotBuilder slot = builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, x, y)
              .setBackground(JeiDraw.slotBackground(mezz.jei.api.recipe.RecipeIngredientRole.CATALYST), -1, -1).addItemStacks(stacks);
        if (returned && keep < 1F) {
            chance(slot, keep);
        }
        slot.addRichTooltipCallback((view, tooltip) -> {
            tooltip.add(Component.translatable("jei.pioneers.tool.slot").withStyle(s -> s.withColor(fr.pioneers.client.gui.core.GuiMachine.TOOL_EDGE & 0xFFFFFF)));
            if (wear) {
                tooltip.add(Component.translatable("jei.pioneers.tool.wear").withStyle(net.minecraft.ChatFormatting.GRAY));
            } else if (!returned) {
                tooltip.add(Component.translatable("jei.pioneers.tool.used").withStyle(net.minecraft.ChatFormatting.GRAY));
            } else if (keep >= 1F) {
                tooltip.add(Component.translatable("jei.pioneers.tool.intact").withStyle(net.minecraft.ChatFormatting.GRAY));
            } else {
                int k = Math.round(keep * 100);
                tooltip.add(Component.translatable("jei.pioneers.tool.keep", k, 100 - k).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        });
        return slot;
    }

    /** Chance lisible sur la case (en haut a gauche, jaune). */
    protected static IRecipeSlotBuilder chance(IRecipeSlotBuilder slot, double chance) {
        return chance < 1 ? slot.setOverlay(new JeiDraw.Mark(JeiDraw.percent(chance), true), 0, 0) : slot;
    }

    /** Jauge de gaz de {@code w} x {@code h} (cadre compris), remplie selon {@code capacity}. */
    protected static IRecipeSlotBuilder gas(IRecipeLayoutBuilder builder, RecipeIngredientRole role, int x, int y, int w, int h, GasStack gas,
          long capacity) {
        return builder.addSlot(role, x + 1, y + 1)
              .setCustomRenderer(GasIngredient.TYPE, new GasIngredient.Renderer(Math.max(1, capacity), w - 2, h - 2))
              .addIngredient(GasIngredient.TYPE, gas)
              .setBackground(new Frame(w, h), -1, -1);
    }

    /** Jauge de fluide de {@code w} x {@code h} (cadre compris). */
    protected static IRecipeSlotBuilder fluid(IRecipeLayoutBuilder builder, RecipeIngredientRole role, int x, int y, int w, int h, Fluid fluid,
          long amount, long capacity) {
        return builder.addSlot(role, x + 1, y + 1)
              .setFluidRenderer(Math.max(1, capacity), false, w - 2, h - 2)
              .addFluidStack(fluid, amount)
              .setBackground(new Frame(w, h), -1, -1);
    }

    /** Emplacement de donnees (cristal teinte, quantite en surimpression). */
    protected static IRecipeSlotBuilder data(IRecipeLayoutBuilder builder, RecipeIngredientRole role, int x, int y, DataStack stack) {
        IRecipeSlotBuilder slot = builder.addSlot(role, x, y).addIngredient(DataIngredient.TYPE, stack);
        return role == RecipeIngredientRole.OUTPUT ? slot.setStandardSlotBackground() : slot.setStandardSlotBackground();
    }

    // ------------------------------------------------------------------ texte

    protected static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Texte a l'echelle {@code scale}. */
    protected static void text(GuiGraphics graphics, Component text, float x, float y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font(), text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    /** Texte centre sur {@code centerX}. */
    protected static void centered(GuiGraphics graphics, Component text, float centerX, float y, float scale, int color) {
        text(graphics, text, centerX - font().width(text) * scale / 2, y, scale, color);
    }

    /** Texte borne a {@code maxWidth} : reduit s'il deborde. */
    protected static void fitted(GuiGraphics graphics, Component text, float x, float y, float scale, int color, int maxWidth) {
        float width = font().width(text) * scale;
        text(graphics, text, x, y, width > maxWidth ? scale * maxWidth / width : scale, color);
    }

    /** Texte coupe en lignes de {@code maxWidth} au plus ; renvoie l'ordonnee sous la derniere ligne. */
    protected static float wrapped(GuiGraphics graphics, Component text, float x, float y, float scale, int color, int maxWidth) {
        for (FormattedCharSequence line : font().split(text, (int) (maxWidth / scale))) {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font(), line, 0, 0, color, false);
            graphics.pose().popPose();
            y += font().lineHeight * scale + 1;
        }
        return y;
    }

    /** Texte centre sur {@code centerX}, reduit s'il depasse {@code maxWidth}. */
    protected static void centeredFitted(GuiGraphics graphics, Component text, float centerX, float y, float scale, int color, int maxWidth) {
        float width = font().width(text) * scale;
        float s = width > maxWidth ? scale * maxWidth / width : scale;
        text(graphics, text, centerX - font().width(text) * s / 2, y, s, color);
    }

    /** Un paragraphe d'un bloc de notes : texte et couleur. */
    protected record Note(Component text, int color) {
    }

    /** Hauteur d'un bloc de notes coupe a {@code maxWidth}, a l'echelle {@code scale} (1 px entre deux paragraphes). */
    private static float blockHeight(List<Note> notes, float scale, int maxWidth) {
        float h = 0;
        for (Note note : notes) {
            h += lines(note.text(), scale, maxWidth) * (font().lineHeight * scale + 1) + 1;
        }
        return h;
    }

    /**
     * Bloc de paragraphes coupes a {@code maxWidth} a partir de {@code y} : l'echelle est reduite (jusqu'a 0,45) pour que tout tienne
     * au-dessus de {@code bottom} ; au pire les lignes qui depasseraient ne sont pas dessinees. Renvoie l'ordonnee sous le bloc.
     */
    protected static float block(GuiGraphics graphics, List<Note> notes, float x, float y, float scale, int maxWidth, float bottom) {
        float s = scale;
        while (s > 0.45F && y + blockHeight(notes, s, maxWidth) > bottom + 1) {
            s -= 0.05F;
        }
        for (Note note : notes) {
            for (FormattedCharSequence line : font().split(note.text(), (int) (maxWidth / s))) {
                if (y + font().lineHeight * s - 1 > bottom) {
                    return y;
                }
                graphics.pose().pushPose();
                graphics.pose().translate(x, y, 0);
                graphics.pose().scale(s, s, 1);
                graphics.drawString(font(), line, 0, 0, note.color(), false);
                graphics.pose().popPose();
                y += font().lineHeight * s + 1;
            }
            y += 1;
        }
        return y;
    }

    /** Nombre de lignes qu'occupera {@code text} coupe a {@code maxWidth}. */
    protected static int lines(Component text, float scale, int maxWidth) {
        return font().split(text, (int) (maxWidth / scale)).size();
    }

    protected static Component seconds(int ticks) {
        return Component.translatable("jei.pioneers.duration", String.format("%.1f", ticks / 20F));
    }

    protected static Component energy(long fe) {
        return Component.literal(Texts.energy(fe));
    }

    protected static Component mb(long amount) {
        return Component.translatable("jei.pioneers.mb", Texts.grouped(amount));
    }

    /** Quantite courte pour les legendes sous les jauges etroites : en seaux (B) des 1 000 mB, la valeur exacte reste dans l'infobulle. */
    protected static Component shortMb(long amount) {
        if (amount < 1000) {
            return Component.literal(amount + " mB");
        }
        double buckets = amount / 1000.0;
        String value = buckets >= 100 || buckets == Math.floor(buckets) ? Texts.grouped(Math.round(buckets))
                                                                        : String.format("%.1f", buckets);
        return Component.literal(value + " B");
    }

    /** Pied de page : energie totale et duree. */
    protected static void footer(GuiGraphics graphics, int y, long energyFE, int ticks, int maxWidth) {
        MutableComponent line = Component.empty();
        if (energyFE > 0) {
            line.append(Component.translatable("jei.pioneers.energy", energy(energyFE)));
        }
        if (ticks > 0) {
            if (energyFE > 0) {
                line.append("   ");
            }
            line.append(Component.translatable("jei.pioneers.time", seconds(ticks)));
        }
        fitted(graphics, line, 0, y, SMALL, TEXT, maxWidth);
    }

    /** Ligne de recherche requise : verte si elle est faite, orange sinon. */
    protected static void research(GuiGraphics graphics, @Nullable ResearchNode node, int y, int maxWidth) {
        if (node == null) {
            return;
        }
        boolean done = ResearchEffects.has(node);
        Component line = Component.translatable(done ? "jei.pioneers.research_done" : "jei.pioneers.research",
              Component.translatable(node.getTranslationKey()));
        fitted(graphics, line, 0, y, SMALL, done ? GOOD : WARN, maxWidth);
    }

}

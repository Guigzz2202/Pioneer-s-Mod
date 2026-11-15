package fr.pioneers.integration.jei;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawablesView;
import mezz.jei.api.gui.inputs.IJeiGuiEventListener;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.IPlaceable;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.IDrawableWidget;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.gui.widgets.IRecipeWidgetTooltipCallback;
import mezz.jei.api.gui.widgets.IScrollBoxWidget;
import mezz.jei.api.gui.widgets.IScrollGridWidget;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import mezz.jei.api.gui.widgets.ISlottedWidgetFactory;
import mezz.jei.api.gui.widgets.ITextWidget;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

/**
 * 1.11.3 (lot E3) : decalage du contenu d'une categorie sous l'en-tete de la charte ({@link JeiDraw}). Les categories gardent leurs
 * coordonnees d'avant (contenu a partir de 0, 0) : {@link PiCategory} leur passe ces enveloppes, qui ajoutent {@code (dx, dy)} a toute position
 * (cases, widgets, zones de souris) et remplacent au passage les fonds de case de JEI par ceux de la charte (selon le role) et les fleches de
 * JEI par {@link JeiDraw.Arrow} (avec la duree).
 */
@OnlyIn(Dist.CLIENT)
public final class JeiOffset {

    private JeiOffset() {
    }

    /** Proxy qui decale {@code setPosition} et se renvoie lui-meme a la place de l'objet enveloppe (appels en chaine). */
    @SuppressWarnings("unchecked")
    static <T> T shifted(Class<T> type, T target, int dx, int dy) {
        if (target == null) {
            return null;
        }
        InvocationHandler[] self = new InvocationHandler[1];
        Object[] proxy = new Object[1];
        self[0] = (p, method, args) -> {
            if ("setPosition".equals(method.getName()) && args != null && args.length >= 2 && args[0] instanceof Integer x && args[1] instanceof Integer y) {
                args = args.clone();
                args[0] = x + dx;
                args[1] = y + dy;
            }
            Object result = invoke(method, target, args);
            return result == target ? proxy[0] : result;
        };
        proxy[0] = Proxy.newProxyInstance(JeiOffset.class.getClassLoader(), new Class<?>[]{type}, self[0]);
        return (T) proxy[0];
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /** Case : position decalee, fond de JEI remplace par le fond de la charte du role. */
    static IRecipeSlotBuilder slot(IRecipeSlotBuilder target, RecipeIngredientRole role, int dx, int dy) {
        Object[] proxy = new Object[1];
        InvocationHandler handler = (p, method, args) -> {
            String name = method.getName();
            if ("setPosition".equals(name) && args != null && args.length >= 2) {
                args = args.clone();
                args[0] = (Integer) args[0] + dx;
                args[1] = (Integer) args[1] + dy;
            } else if ("setStandardSlotBackground".equals(name) || "setOutputSlotBackground".equals(name)) {
                target.setBackground(JeiDraw.slotBackground(role), -1, -1);
                return proxy[0];
            }
            Object result = invoke(method, target, args);
            return result == target ? proxy[0] : result;
        };
        proxy[0] = Proxy.newProxyInstance(JeiOffset.class.getClassLoader(), new Class<?>[]{IRecipeSlotBuilder.class}, handler);
        return (IRecipeSlotBuilder) proxy[0];
    }

    // ------------------------------------------------------------------ constructeur de mise en page

    /** Mise en page decalee : chaque case passe par {@link #slot}. */
    record Layout(IRecipeLayoutBuilder target, int dx, int dy) implements IRecipeLayoutBuilder {

        @NotNull
        @Override
        public IRecipeSlotBuilder addSlot(@NotNull RecipeIngredientRole role) {
            return slot(target.addSlot(role), role, dx, dy);
        }

        @NotNull
        @Override
        public IRecipeSlotBuilder addSlotToWidget(@NotNull RecipeIngredientRole role, @NotNull ISlottedWidgetFactory<?> factory) {
            return slot(target.addSlotToWidget(role, factory), role, dx, dy);
        }

        @NotNull
        @Override
        public IIngredientAcceptor<?> addInvisibleIngredients(@NotNull RecipeIngredientRole role) {
            return target.addInvisibleIngredients(role);
        }

        @Override
        public void moveRecipeTransferButton(int x, int y) {
            target.moveRecipeTransferButton(x + dx, y + dy);
        }

        @Override
        public void setShapeless() {
            target.setShapeless();
        }

        @Override
        public void setShapeless(int x, int y) {
            target.setShapeless(x + dx, y + dy);
        }

        @Override
        public void createFocusLink(@NotNull IIngredientAcceptor<?>... slots) {
            target.createFocusLink(slots);
        }
    }

    // ------------------------------------------------------------------ widgets

    /** Poignee de la fleche de la charte, rendue a la place des fleches de JEI. */
    record ArrowHandle(JeiDraw.Arrow arrow, int dx, int dy) implements IDrawableWidget {

        @NotNull
        @Override
        public IDrawableWidget setPosition(int x, int y) {
            arrow.place(x + dx, y + dy);
            return this;
        }

        @NotNull
        @Override
        public IDrawableWidget setPosition(int x, int y, int w, int h, @NotNull HorizontalAlignment horizontal, @NotNull VerticalAlignment vertical) {
            int px = switch (horizontal) {
                case LEFT -> x;
                case CENTER -> x + (w - JeiDraw.ARROW_W) / 2;
                case RIGHT -> x + w - JeiDraw.ARROW_W;
            };
            int py = switch (vertical) {
                case TOP -> y;
                case CENTER -> y + (h - JeiDraw.ARROW_H) / 2;
                case BOTTOM -> y + h - JeiDraw.ARROW_H;
            };
            return setPosition(px, py);
        }

        @Override
        public int getWidth() {
            return JeiDraw.ARROW_W;
        }

        @Override
        public int getHeight() {
            return JeiDraw.ARROW_H;
        }

        @NotNull
        @Override
        public IDrawableWidget setTooltip(@NotNull FormattedText text) {
            arrow.tooltip().add(text);
            return this;
        }

        @NotNull
        @Override
        public IDrawableWidget setTooltip(@NotNull Collection<? extends FormattedText> text) {
            arrow.tooltip().addAll(text);
            return this;
        }

        @NotNull
        @Override
        public IDrawableWidget setTooltip(@NotNull TooltipComponent component) {
            return this;
        }

        @NotNull
        @Override
        public IDrawableWidget setTooltip(@NotNull IRecipeWidgetTooltipCallback callback) {
            return this;
        }
    }

    /** Widget decale (sa position seulement : JEI lui passe deja des coordonnees relatives). */
    record Widget(IRecipeWidget target, int dx, int dy) implements IRecipeWidget {

        @NotNull
        @Override
        public ScreenPosition getPosition() {
            ScreenPosition p = target.getPosition();
            return new ScreenPosition(p.x() + dx, p.y() + dy);
        }

        @Override
        public ScreenRectangle getScreenRectangle() {
            ScreenRectangle r = target.getScreenRectangle();
            return r == null ? null : new ScreenRectangle(r.left() + dx, r.top() + dy, r.width(), r.height());
        }

        @Override
        public void draw(@NotNull GuiGraphics graphics, double mouseX, double mouseY) {
            target.draw(graphics, mouseX, mouseY);
        }

        @Override
        public void getTooltip(@NotNull ITooltipBuilder tooltip, double mouseX, double mouseY) {
            target.getTooltip(tooltip, mouseX, mouseY);
        }

        @Override
        public void tick() {
            target.tick();
        }
    }

    /** Ecouteur de souris decale : zone decalee, coordonnees rendues au repere du contenu. */
    record Listener(IJeiGuiEventListener target, int dx, int dy) implements IJeiGuiEventListener {

        @NotNull
        @Override
        public ScreenRectangle getArea() {
            ScreenRectangle r = target.getArea();
            return new ScreenRectangle(r.left() + dx, r.top() + dy, r.width(), r.height());
        }

        @Override
        public void mouseMoved(double x, double y) {
            target.mouseMoved(x - dx, y - dy);
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            return target.mouseClicked(x - dx, y - dy, button);
        }

        @Override
        public boolean mouseReleased(double x, double y, int button) {
            return target.mouseReleased(x - dx, y - dy, button);
        }

        @Override
        public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
            return target.mouseDragged(x - dx, y - dy, button, dragX, dragY);
        }

        @Override
        public boolean mouseScrolled(double x, double y, double delta) {
            return target.mouseScrolled(x - dx, y - dy, delta);
        }

        @Override
        public boolean keyPressed(double x, double y, int key, int scan, int modifiers) {
            return target.keyPressed(x - dx, y - dy, key, scan, modifiers);
        }
    }

    /** Gestionnaire d'entrees decale (JEI lui passe des coordonnees relatives a sa zone : seule la zone bouge). */
    record Input(IJeiInputHandler target, int dx, int dy) implements IJeiInputHandler {

        @NotNull
        @Override
        public ScreenRectangle getArea() {
            ScreenRectangle r = target.getArea();
            return new ScreenRectangle(r.left() + dx, r.top() + dy, r.width(), r.height());
        }

        @Override
        public boolean handleInput(double x, double y, @NotNull IJeiUserInput input) {
            return target.handleInput(x, y, input);
        }

        @Override
        public boolean handleMouseScrolled(double x, double y, double delta) {
            return target.handleMouseScrolled(x, y, delta);
        }

        @Override
        public boolean handleMouseDragged(double x, double y, @NotNull InputConstants.Key key, double dragX, double dragY) {
            return target.handleMouseDragged(x, y, key, dragX, dragY);
        }

        @Override
        public void handleMouseMoved(double x, double y) {
            target.handleMouseMoved(x, y);
        }
    }

    // ------------------------------------------------------------------ constructeur des extras

    /** Extras decales : fleches de la charte, widgets et zones de souris decales. */
    record Extras(IRecipeExtrasBuilder target, int dx, int dy) implements IRecipeExtrasBuilder {

        private IDrawableWidget arrow(int ticks) {
            JeiDraw.Arrow arrow = new JeiDraw.Arrow(ticks);
            arrow.place(dx, dy);
            target.addWidget(arrow);
            return new ArrowHandle(arrow, dx, dy);
        }

        @NotNull
        @Override
        public IRecipeSlotDrawablesView getRecipeSlots() {
            return target.getRecipeSlots();
        }

        @Override
        public void addDrawable(@NotNull IDrawable drawable, int x, int y) {
            target.addDrawable(drawable, x + dx, y + dy);
        }

        @NotNull
        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public IPlaceable<?> addDrawable(@NotNull IDrawable drawable) {
            return shifted(IPlaceable.class, (IPlaceable) target.addDrawable(drawable), dx, dy);
        }

        @NotNull
        @Override
        public IDrawableWidget addDrawableWidget(@NotNull IDrawable drawable) {
            return shifted(IDrawableWidget.class, target.addDrawableWidget(drawable), dx, dy);
        }

        @NotNull
        @Override
        public IDrawableWidget addTooltipArea(int x, int y, int w, int h) {
            return target.addTooltipArea(x + dx, y + dy, w, h);
        }

        @Override
        public void addWidget(@NotNull IRecipeWidget widget) {
            target.addWidget(new Widget(widget, dx, dy));
        }

        @Override
        public void addSlottedWidget(@NotNull ISlottedRecipeWidget widget, @NotNull List<IRecipeSlotDrawable> slots) {
            target.addSlottedWidget(widget, slots);
        }

        @Override
        public void addInputHandler(@NotNull IJeiInputHandler handler) {
            target.addInputHandler(new Input(handler, dx, dy));
        }

        @Override
        public void addGuiEventListener(@NotNull IJeiGuiEventListener listener) {
            target.addGuiEventListener(new Listener(listener, dx, dy));
        }

        @NotNull
        @Override
        public IScrollBoxWidget addScrollBoxWidget(int w, int h, int x, int y) {
            return target.addScrollBoxWidget(w, h, x + dx, y + dy);
        }

        @NotNull
        @Override
        public IScrollGridWidget addScrollGridWidget(@NotNull List<IRecipeSlotDrawable> slots, int columns, int rows) {
            return shifted(IScrollGridWidget.class, target.addScrollGridWidget(slots, columns, rows), dx, dy);
        }

        @NotNull
        @Override
        public IPlaceable<?> addRecipeArrow() {
            return arrow(0);
        }

        @NotNull
        @Override
        public IDrawableWidget addRecipeArrowWidget() {
            return arrow(0);
        }

        @NotNull
        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public IPlaceable<?> addRecipePlusSign() {
            return shifted(IPlaceable.class, (IPlaceable) target.addRecipePlusSign(), dx, dy);
        }

        @NotNull
        @Override
        public IDrawableWidget addRecipePlusSignWidget() {
            return shifted(IDrawableWidget.class, target.addRecipePlusSignWidget(), dx, dy);
        }

        @NotNull
        @Override
        public IPlaceable<?> addAnimatedRecipeArrow(int ticks) {
            return arrow(ticks);
        }

        @NotNull
        @Override
        public IDrawableWidget addAnimatedRecipeArrowWidget(int ticks) {
            return arrow(ticks);
        }

        @NotNull
        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public IPlaceable<?> addAnimatedRecipeFlame(int ticks) {
            return shifted(IPlaceable.class, (IPlaceable) target.addAnimatedRecipeFlame(ticks), dx, dy);
        }

        @NotNull
        @Override
        public IDrawableWidget addAnimatedRecipeFlameWidget(int ticks) {
            return shifted(IDrawableWidget.class, target.addAnimatedRecipeFlameWidget(ticks), dx, dy);
        }

        @NotNull
        @Override
        public ITextWidget addText(@NotNull List<FormattedText> text, int w, int h) {
            return shifted(ITextWidget.class, target.addText(text, w, h), dx, dy);
        }
    }
}

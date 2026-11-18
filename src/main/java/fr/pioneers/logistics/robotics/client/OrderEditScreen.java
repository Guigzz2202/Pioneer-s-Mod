package fr.pioneers.logistics.robotics.client;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.logistics.robotics.PiRobotics;
import fr.pioneers.logistics.robotics.Endpoint;
import fr.pioneers.logistics.robotics.LogisticResource;
import fr.pioneers.logistics.robotics.Resource;
import fr.pioneers.logistics.robotics.RoboticOrder;
import fr.pioneers.logistics.robotics.UnitKind;
import fr.pioneers.logistics.robotics.logistics.PointConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Ordre de transport, en une phrase : « transporter [quantite] [ressource] depuis [source] vers [cible] », une fois ou en continu. Les
 * deux cartes Source / Cible se designent en visant le bloc (le menu se ferme le temps de viser), la face se choisit d'un clic. Les
 * options (priorite, condition sur les stocks du reseau, unites) sont repliees tant qu'on n'en a pas besoin. Ce qui manque pour
 * enregistrer est dit en clair en bas.
 */
public class OrderEditScreen extends RoboticsScreen {

    private static boolean optionsOpen;
    private final RoboticOrder draft;
    private EditBox amountBox, thresholdBox;

    public OrderEditScreen(@Nullable Screen parent, RoboticOrder draft) {
        super(Component.translatable(draft.id > 0 ? "robotics.pioneers.edit.title_edit" : "robotics.pioneers.edit.title_new"), parent, 330,
              250);
        this.draft = draft;
        draft.type = RoboticOrder.Type.TRANSFER;
        dark = true;
        if (draft.condition != null || draft.priority != 0 || draft.kind >= 0 || draft.hub != null) {
            optionsOpen = true;
        }
    }

    /** Hauteur selon les options (depliees ou non), recentree. */
    private int pageScroll, contentHeight, scrollTop, scrollBottom;

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= scrollTop && mouseY < scrollBottom && contentHeight > scrollBottom - scrollTop) {
            pageScroll = Math.max(0, pageScroll - (int) (delta * 16));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void layout() {
        panelHeight = Math.min(optionsOpen ? 300 : 214, height - 8);
        top = (height - panelHeight) / 2;
    }

    @Override
    protected void init() {
        super.init();
        layout();
        amountBox = new EditBox(font, 0, 0, 46, 13, Component.translatable("robotics.pioneers.edit.amount"));
        amountBox.setMaxLength(12);
        amountBox.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        amountBox.setValue(Long.toString(draft.amount));
        amountBox.setResponder(text -> draft.amount = text.isEmpty() ? 0 : Long.parseLong(text));
        addRenderableWidget(amountBox);
        thresholdBox = new EditBox(font, 0, 0, 56, 13, Component.translatable("robotics.pioneers.point.threshold"));
        thresholdBox.setMaxLength(12);
        thresholdBox.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        thresholdBox.setValue(draft.condition == null ? "0" : Long.toString(draft.condition.threshold()));
        thresholdBox.setResponder(text -> {
            if (draft.condition != null) {
                draft.condition = new PointConfig.Condition(draft.condition.res(), draft.condition.below(), text.isEmpty() ? 0 : Long.parseLong(text));
            }
        });
        addRenderableWidget(thresholdBox);
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //1.0 : sur un petit ecran (options depliees), le contenu defile entre le titre et le pied
        int bodyTop = top + 18, bodyBottom = top + panelHeight - 28, room = bodyBottom - bodyTop;
        pageScroll = Math.max(0, Math.min(pageScroll, contentHeight - room + 4));
        scrollTop = bodyTop;
        scrollBottom = bodyBottom;
        beginClip(graphics, left + 1, bodyTop, left + panelWidth - 1, bodyBottom);
        int x = left + 10, w = panelWidth - 20, y = top + 22 - pageScroll;
        //1. Quoi : ressource, quantite, une fois / en continu
        y = section(graphics, x, y, w, Component.translatable("robotics.pioneers.edit.what"));
        darkSlot(graphics, x + 1, y + 1);
        ResourceIcon.draw(graphics, draft.res, x + 1, y + 1);
        clickBoth(x, y, 18, 18, this::pickResource);
        if (over(mouseX, mouseY, x, y, 18, 18)) {
            List<Component> lines = new ArrayList<>(resourceLines(draft.res));
            lines.add(Component.translatable("robotics.pioneers.edit.item.tooltip").withStyle(ChatFormatting.GRAY));
            tooltip(lines);
        }
        if (over(mouseX, mouseY, x, y, 18, 18)) {
            graphics.renderOutline(x, y, 18, 18, 0xFF7FE6C0);
        }
        GuiDraw.text(graphics, font, draft.res.name(), x + 22, y + 1, 0.75F, D_TEXT, 120);
        GuiDraw.text(graphics, font, Component.translatable(draft.res.isFamily() ? "robotics.pioneers.edit.family_hint"
                                                                                  : "robotics.pioneers.edit.change_hint"), x + 22, y + 10, 0.58F, D_FAINT,
              120);
        //Quantite
        int ax = x + 150;
        amountBox.setX(ax);
        amountBox.setY(y + 2);
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.symbol." + draft.res.type.name().toLowerCase(Locale.ROOT)), ax + 49,
              y + 5, 0.65F, D_DIM, Math.max(8, w - 92 - 150 - 51));
        //Mode
        int mx = x + w - 92;
        chip(graphics, mouseX, mouseY, mx, y + 2, 45, 13, Component.translatable("robotics.pioneers.edit.once"), !draft.recurring, 0x2E9E78,
              () -> draft.recurring = false);
        chip(graphics, mouseX, mouseY, mx + 47, y + 2, 45, 13, Component.translatable("robotics.pioneers.edit.recurring"), draft.recurring, 0x2E9E78,
              () -> draft.recurring = true);
        tooltip(mouseX, mouseY, mx, y + 2, 92, 13, Component.translatable("robotics.pioneers.edit.mode.tooltip"));
        y += 20;
        GuiDraw.text(graphics, font, Component.translatable(draft.recurring ? "robotics.pioneers.edit.amount.recurring"
                                                                            : "robotics.pioneers.edit.amount.once"), x + 150, y, 0.55F, D_FAINT, w - 150);
        //2. Ou : source -> cible
        y += 10;
        y = section(graphics, x, y, w, Component.translatable("robotics.pioneers.edit.where"));
        int cw = (w - 16) / 2;
        card(graphics, mouseX, mouseY, x, y, cw, draft.source, true);
        GuiDraw.center(graphics, font, Component.literal("➜"), x + cw + 8, y + 22, 1.2F, 0xFF7FE6C0, 16);
        card(graphics, mouseX, mouseY, x + cw + 16, y, cw, draft.target, false);
        y += 62;
        //3. Options repliables
        Component head = Component.translatable(optionsOpen ? "robotics.pioneers.edit.options_open" : "robotics.pioneers.edit.options_closed");
        GuiDraw.text(graphics, font, head, x, y, 0.7F, over(mouseX, mouseY, x, y - 2, w, 11) ? 0xFFFFFFFF : 0xFF7FE6C0, 90);
        click(x, y - 2, 100, 11, () -> {
            optionsOpen = !optionsOpen;
            layout();
        });
        if (!optionsOpen) {
            GuiDraw.text(graphics, font, summary(), x + 100, y + 1, 0.58F, D_FAINT, w - 100);
        }
        graphics.fill(x, y + 9, x + w, y + 10, D_LINE);
        y += 14;
        thresholdBox.visible = optionsOpen && draft.condition != null;
        if (optionsOpen) {
            y = drawOptions(graphics, mouseX, mouseY, x, y, w);
        }
        endClip(graphics);
        contentHeight = y + pageScroll - (top + 22);
        if (amountBox.getY() < bodyTop || amountBox.getY() + 13 > bodyBottom) {
            amountBox.visible = false;
        } else {
            amountBox.visible = true;
        }
        if (thresholdBox.visible && (thresholdBox.getY() < bodyTop || thresholdBox.getY() + 13 > bodyBottom)) {
            thresholdBox.visible = false;
        }
        if (contentHeight > room) {
            int track = room - 2, thumb = Math.max(10, track * room / contentHeight);
            int ty = bodyTop + 1 + (track - thumb) * pageScroll / Math.max(1, contentHeight - room + 4);
            graphics.fill(left + panelWidth - 4, ty, left + panelWidth - 2, ty + thumb, 0xFF4A5A68);
        }
        //Pied
        int by = top + panelHeight - 22;
        graphics.fill(left + 1, by - 5, left + panelWidth - 1, by - 4, D_LINE);
        Component problem = problem();
        if (draft.id > 0) {
            button(graphics, mouseX, mouseY, x, by, 16, 15, Component.literal("🗑"), true, () -> {
                ClientRobotics.delete(draft);
                close();
            });
            tooltip(mouseX, mouseY, x, by, 16, 15, Component.translatable("robotics.pioneers.orders.delete"));
        }
        if (problem != null) {
            GuiDraw.text(graphics, font, Component.literal("⚠ ").append(problem), x + (draft.id > 0 ? 20 : 0), by + 4, 0.62F, 0xFFFFB050,
                  w - 150 - (draft.id > 0 ? 20 : 0));
        }
        button(graphics, mouseX, mouseY, left + panelWidth - 146, by, 60, 15, Component.translatable("gui.cancel"), true, this::close);
        primary(graphics, mouseX, mouseY, left + panelWidth - 82, by, 72, 15, Component.translatable("robotics.pioneers.edit.save"), problem == null,
              this::save);
    }

    private void pickResource() {
        if (hasShiftDown() && !draft.res.type.isQuantity()) {
            draft.res = LogisticResource.any(draft.res.type);
            return;
        }
        minecraft.setScreen(new ResourcePickerScreen(this, draft.res.type, Set.of(), true, res -> draft.res = res));
    }

    /** Carte d'un bout du transport : bloc, coordonnees, face ; viser, reseau (source). */
    private void card(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, @Nullable Endpoint endpoint, boolean source) {
        int h = 56;
        boolean network = source && endpoint == null;
        graphics.fill(x, y, x + w, y + h, 0xFF101820);
        graphics.renderOutline(x, y, w, h, endpoint != null || network ? 0xFF2E5A4C : 0xFF5A3A2A);
        GuiDraw.text(graphics, font, Component.translatable(source ? "robotics.pioneers.edit.from" : "robotics.pioneers.edit.to")
              .withStyle(ChatFormatting.BOLD), x + 4, y + 3, 0.6F, 0xFF7FE6C0, w - 8);
        ItemStack icon = ItemStack.EMPTY;
        Component name, detail;
        if (network) {
            icon = new ItemStack(PiRobotics.RELAY);
            name = Component.translatable("robotics.pioneers.order.network");
            detail = Component.translatable("robotics.pioneers.edit.network.short");
        } else if (endpoint == null) {
            name = Component.translatable("robotics.pioneers.edit.none");
            detail = Component.translatable("robotics.pioneers.edit.aim.short");
        } else {
            if (minecraft.level != null && minecraft.level.isLoaded(endpoint.pos())) {
                icon = new ItemStack(minecraft.level.getBlockState(endpoint.pos()).getBlock());
            }
            name = blockName(endpoint.pos());
            detail = Component.literal(endpoint.pos().toShortString());
        }
        int tx = x + 4;
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, x + 4, y + 12);
            tx += 20;
        }
        GuiDraw.text(graphics, font, name, tx, y + 13, 0.7F, endpoint != null || network ? D_TEXT : 0xFFFFB080, x + w - tx - 4);
        GuiDraw.text(graphics, font, detail, tx, y + 22, 0.55F, D_FAINT, x + w - tx - 4);
        //Boutons : viser, face (bloc) ou reseau (source)
        int by = y + h - 17;
        button(graphics, mouseX, mouseY, x + 4, by, 40, 13, Component.translatable("robotics.pioneers.edit.aim"), true,
              () -> ClientRobotics.startPicking(draft, source, parent));
        tooltip(mouseX, mouseY, x + 4, by, 40, 13, Component.translatable("robotics.pioneers.edit.aim.tooltip"));
        int fx = x + 46;
        if (endpoint != null) {
            Component face = endpoint.side() == null ? Component.translatable("robotics.pioneers.point.side.auto") : side(endpoint.side());
            int fw = source ? w - 50 - 38 : w - 50;
            chip(graphics, mouseX, mouseY, fx, by, fw, 13, Component.literal("↻ ").append(face), endpoint.side() != null, 0x3A7FC0, () -> {
                Direction side = endpoint.side();
                Direction next = side == null ? Direction.DOWN : side.get3DDataValue() == 5 ? null : Direction.from3DDataValue(side.get3DDataValue() + 1);
                Endpoint turned = endpoint.withSide(next);
                if (source) {
                    draft.source = turned;
                } else {
                    draft.target = turned;
                }
            });
            tooltip(mouseX, mouseY, fx, by, fw, 13, Component.translatable("robotics.pioneers.edit.side.tooltip", face));
            fx += fw + 2;
        }
        if (source) {
            int nw = x + w - 4 - fx;
            chip(graphics, mouseX, mouseY, fx, by, nw, 13, Component.translatable("robotics.pioneers.edit.network"), network, 0x3A7FC0,
                  () -> draft.source = null);
            tooltip(mouseX, mouseY, fx, by, nw, 13, Component.translatable("robotics.pioneers.edit.network.tooltip"));
        }
    }

    private int drawOptions(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w) {
        //Priorite
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.edit.priority"), x, y + 3, 0.65F, D_DIM, 50);
        String[] priorities = {"low", "normal", "high"};
        for (int p = -1; p <= 1; p++) {
            int value = p;
            chip(graphics, mouseX, mouseY, x + 52 + (p + 1) * 46, y, 44, 13, Component.translatable("robotics.pioneers.priority." + priorities[p + 1]),
                  draft.priority == p, 0xC08A30, () -> draft.priority = value);
        }
        //Condition
        y += 17;
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.edit.condition"), x, y + 3, 0.65F, D_DIM, 50);
        PointConfig.Condition condition = draft.condition;
        String mode = condition == null ? "always" : condition.below() ? "below" : "above";
        chip(graphics, mouseX, mouseY, x + 52, y, 70, 13, Component.translatable("robotics.pioneers.point.condition." + mode), condition != null, 0x8A6FD0,
              () -> {
                  if (draft.condition == null) {
                      draft.condition = new PointConfig.Condition(draft.res.isAny() ? LogisticResource.any(Resource.ITEMS) : draft.res, true, 0);
                      thresholdBox.setValue("0");
                  } else if (draft.condition.below()) {
                      draft.condition = new PointConfig.Condition(draft.condition.res(), false, draft.condition.threshold());
                  } else {
                      draft.condition = null;
                  }
              });
        tooltip(mouseX, mouseY, x + 52, y, 70, 13, Component.translatable("robotics.pioneers.point.condition.tooltip"));
        if (condition != null) {
            thresholdBox.setX(x + 126);
            thresholdBox.setY(y);
            int sx = x + 188;
            darkSlot(graphics, sx, y - 2);
            if (!condition.res().isAny()) {
                ResourceIcon.draw(graphics, condition.res(), sx, y - 2);
            }
            click(sx - 1, y - 3, 18, 18, () -> minecraft.setScreen(new ResourcePickerScreen(this, condition.res().type, Set.of(), false,
                  res -> draft.condition = new PointConfig.Condition(res, draft.condition == null || draft.condition.below(),
                        draft.condition == null ? 0 : draft.condition.threshold()))));
            tooltip(mouseX, mouseY, sx - 1, y - 3, 18, 18, condition.res().isAny() ? Component.translatable("robotics.pioneers.edit.item.pick")
                                                                                    : condition.res().name());
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.edit.in_network"), sx + 21, y + 3, 0.6F, D_FAINT, x + w - sx - 21);
        }
        //Unites
        y += 20;
        return AssignControls.draw(this, graphics, mouseX, mouseY, x, y, Math.min(w, 200), draft);
    }

    /** Resume des options repliees. */
    private Component summary() {
        String[] priorities = {"low", "normal", "high"};
        Component units = draft.kind < 0 ? Component.translatable("robotics.pioneers.assign.auto")
                                         : Component.translatable("robotics.pioneers.assign." + UnitKind.values()[draft.kind].id());
        return Component.translatable("robotics.pioneers.edit.summary", Component.translatable("robotics.pioneers.priority." + priorities[draft.priority + 1]),
              units, draft.maxUnits);
    }

    /** Ce qui empeche d'enregistrer, en clair (null : tout va bien). */
    @Nullable
    private Component problem() {
        if (draft.target == null) {
            return Component.translatable("robotics.pioneers.edit.problem.target");
        }
        if (draft.source == null && draft.res.isFamily()) {
            return Component.translatable("robotics.pioneers.edit.problem.network_family");
        }
        if (draft.source != null && draft.source.pos().equals(draft.target.pos()) && draft.source.side() == draft.target.side()) {
            return Component.translatable("robotics.pioneers.edit.problem.same");
        }
        if (!draft.recurring && draft.amount <= 0) {
            return Component.translatable("robotics.pioneers.edit.problem.amount");
        }
        if (draft.condition != null && draft.condition.res().isAny()) {
            return Component.translatable("robotics.pioneers.edit.problem.condition");
        }
        return null;
    }

    private void save() {
        if (problem() == null) {
            ClientRobotics.save(draft);
            close();
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (editing()) {
            return super.keyPressed(key, scanCode, modifiers);
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && !amountBox.isFocused() && !thresholdBox.isFocused()) {
            save();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void close() {
        minecraft.setScreen(parent != null ? parent : new RoboticsMapScreen());
    }
}

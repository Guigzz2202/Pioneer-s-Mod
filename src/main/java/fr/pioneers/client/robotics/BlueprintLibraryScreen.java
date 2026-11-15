package fr.pioneers.client.robotics;

import fr.pioneers.Pioneers;
import fr.pioneers.client.gui.GuiDraw;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Bibliotheque de plans : la liste des plans ranges (vignette, nom, taille), avec recherche ; a droite le plan choisi (grande vignette,
 * nom modifiable, materiaux) et ses actions : utiliser (il devient la copie a coller), dupliquer, supprimer. La copie en cours peut
 * etre rangee d'ici.
 */
public class BlueprintLibraryScreen extends RoboticsScreen {

    private static final int W = 400, H = 252, LIST = 176, ROW = 24;

    private final Consumer<Blueprint> use;
    @Nullable
    private final Blueprint current;
    private EditBox search, name;
    @Nullable
    private String selected;
    private int scroll;
    private boolean confirmDelete;

    /**
     * @param current la copie en cours du planificateur (rangeable d'ici), ou null
     * @param use     le plan choisi devient la copie a coller
     */
    public BlueprintLibraryScreen(Screen parent, @Nullable Blueprint current, Consumer<Blueprint> use) {
        super(Component.translatable("robotics.pioneers.plans.title"), parent, W, H);
        this.current = current;
        this.use = use;
        this.dark = true;
        BlueprintLibrary.refresh();
        if (current != null && current.id() != null) {
            selected = current.id();
        }
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, left + 8, top + 20, LIST - 8, 12, Component.translatable("robotics.pioneers.picker.search"));
        search.setHint(Component.translatable("robotics.pioneers.picker.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(text -> scroll = 0);
        addRenderableWidget(search);
        name = new EditBox(font, 0, 0, 100, 12, Component.translatable("robotics.pioneers.plans.name"));
        name.setMaxLength(48);
        name.setVisible(false);
        addRenderableWidget(name);
        syncName();
    }

    private void syncName() {
        BlueprintLibrary.Entry entry = entry(selected);
        if (name != null) {
            name.setValue(entry == null ? "" : entry.name());
        }
    }

    @Nullable
    private static BlueprintLibrary.Entry entry(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (BlueprintLibrary.Entry entry : BlueprintLibrary.list()) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    private List<BlueprintLibrary.Entry> filtered() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<BlueprintLibrary.Entry> list = new ArrayList<>();
        for (BlueprintLibrary.Entry entry : BlueprintLibrary.list()) {
            if (query.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(query)) {
                list.add(entry);
            }
        }
        return list;
    }

    private void select(String id) {
        commitName();
        selected = id;
        confirmDelete = false;
        syncName();
    }

    /** Le nom saisi est enregistre (s'il a change). */
    private void commitName() {
        BlueprintLibrary.Entry entry = entry(selected);
        if (entry != null && name != null && !name.getValue().isBlank() && !name.getValue().trim().equals(entry.name())) {
            BlueprintLibrary.rename(entry.id(), name.getValue());
        }
    }

    /** Vignette ajustee dans un cadre, proportions gardees. */
    private void thumb(GuiGraphics graphics, @Nullable ResourceLocation texture, int sx, int sz, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF0E1419);
        graphics.renderOutline(x, y, w, h, D_LINE);
        if (texture == null) {
            return;
        }
        float scale = Math.min((w - 2) / (float) sx, (h - 2) / (float) sz);
        int dw = Math.max(1, Math.round(sx * scale)), dh = Math.max(1, Math.round(sz * scale));
        graphics.blit(texture, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, 0, 0, sx, sz, sx, sz);
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left + 8, y = top + 36;
        //Liste des plans
        List<BlueprintLibrary.Entry> list = filtered();
        int bottom = top + panelHeight - 30, rows = Math.max(1, (bottom - y) / ROW);
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - rows));
        if (list.isEmpty()) {
            wrapped(graphics, Component.translatable(BlueprintLibrary.list().isEmpty() ? "robotics.pioneers.plans.empty" : "robotics.pioneers.plans.no_match"),
                  x, y + 4, 0.6F, D_DIM, LIST - 8, 5);
        }
        for (int i = 0; i < rows && scroll + i < list.size(); i++) {
            BlueprintLibrary.Entry entry = list.get(scroll + i);
            int ry = y + i * ROW;
            boolean hover = over(mouseX, mouseY, x, ry, LIST - 8, ROW - 2);
            graphics.fill(x, ry, x + LIST - 8, ry + ROW - 2, entry.id().equals(selected) ? 0xFF24443A : hover ? 0xFF1E2E28 : 0xFF182420);
            thumb(graphics, BlueprintLibrary.thumbnail(entry.id()), Math.min(256, entry.sx()), Math.min(256, entry.sz()), x + 2, ry + 1, 20, 20);
            GuiDraw.text(graphics, font, Component.literal(entry.name()), x + 26, ry + 3, 0.7F, D_TEXT, LIST - 40);
            GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.plans.line", entry.sx(), entry.sy(), entry.sz(), entry.blocks()), x + 26, ry + 12,
                  0.55F, D_DIM, LIST - 40);
            String id = entry.id();
            click(x, ry, LIST - 8, ROW - 2, () -> select(id));
        }
        if (list.size() > rows) {
            int track = rows * ROW, thumb = Math.max(10, track * rows / list.size());
            int ty = y + (track - thumb) * scroll / Math.max(1, list.size() - rows);
            graphics.fill(x + LIST - 6, ty, x + LIST - 4, ty + thumb, 0xFF3C4A57);
        }
        //Ranger la copie en cours / ouvrir le dossier
        int by = top + panelHeight - 24;
        boolean canSave = current != null && current.id() == null;
        primary(graphics, mouseX, mouseY, x, by, LIST - 34, 16, Component.translatable("robotics.pioneers.plans.save_current"), canSave, () -> {
            Blueprint saved = BlueprintLibrary.save(current.withName(null, current.name().isBlank() ? defaultName() : current.name()));
            if (saved != null) {
                selected = saved.id();
                syncName();
                name.setFocused(true);
                setFocused(name);
                use.accept(saved);
            }
        });
        tooltip(mouseX, mouseY, x, by, LIST - 34, 16, Component.translatable(current == null ? "robotics.pioneers.plans.save_current.none"
                                                                                       : canSave ? "robotics.pioneers.plans.save_current.tooltip"
                                                                                                 : "robotics.pioneers.plans.save_current.saved"));
        button(graphics, mouseX, mouseY, x + LIST - 32, by, 24, 16, Component.literal("📂"), true, () -> {
            try {
                java.nio.file.Files.createDirectories(BlueprintLibrary.folder());
            } catch (java.io.IOException e) {
                Pioneers.LOGGER.warn("Pioneer's : dossier des plans impossible a creer", e);
            }
            Util.getPlatform().openFile(BlueprintLibrary.folder().toFile());
        });
        tooltip(mouseX, mouseY, x + LIST - 32, by, 24, 16, Component.translatable("robotics.pioneers.plans.folder"));
        //Plan choisi
        int px = left + LIST + 8, pw = panelWidth - LIST - 16, py = top + 20;
        graphics.fill(px - 5, top + 18, px - 4, top + panelHeight - 8, D_LINE);
        BlueprintLibrary.Entry entry = entry(selected);
        Blueprint print = entry == null ? null : BlueprintLibrary.get(entry.id());
        name.setVisible(print != null);
        if (print == null) {
            wrapped(graphics, Component.translatable("robotics.pioneers.plans.pick"), px, py + 4, 0.62F, D_DIM, pw, 6);
            return;
        }
        thumb(graphics, BlueprintLibrary.thumbnail(entry.id()), Math.min(256, print.sx()), Math.min(256, print.sz()), px, py, pw, 78);
        py += 82;
        name.setX(px);
        name.setY(py);
        name.setWidth(pw);
        py += 16;
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.plans.size", print.sx(), print.sy(), print.sz(), print.solidCount()), px, py, 0.62F,
              D_TEXT, pw);
        py += 10;
        if (!print.configs().isEmpty() || !print.zones().isEmpty()) {
            py += wrapped(graphics, Component.translatable("robotics.pioneers.works.paste_extras", print.configs().size(), print.zones().size()), px, py, 0.56F,
                  0xFFFFD98A, pw, 2) + 1;
        }
        //Materiaux (les plus nombreux)
        List<Map.Entry<Item, Integer>> materials = print.materials();
        int perRow = Math.max(1, pw / 19), maxShown = perRow * 2;
        for (int i = 0; i < Math.min(materials.size(), maxShown); i++) {
            int sx = px + (i % perRow) * 19, sy = py + (i / perRow) * 19;
            darkSlot(graphics, sx + 1, sy + 1);
            ItemStack stack = new ItemStack(materials.get(i).getKey());
            boolean more = i == maxShown - 1 && materials.size() > maxShown;
            if (more) {
                GuiDraw.center(graphics, font, Component.literal("+" + (materials.size() - maxShown + 1)), sx + 9, sy + 6, 0.6F, D_DIM, 16);
                continue;
            }
            graphics.renderItem(stack, sx + 1, sy + 1);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            GuiDraw.right(graphics, font, Component.literal(OrderTexts.compact(materials.get(i).getValue())), sx + 18, sy + 12, 0.5F, 0xFFFFFFFF);
            graphics.pose().popPose();
            if (over(mouseX, mouseY, sx + 1, sy + 1, 16, 16)) {
                tooltip(List.of(stack.getHoverName(), Component.literal("× " + materials.get(i).getValue()).withStyle(ChatFormatting.GRAY)));
            }
        }
        //Actions
        int ay = top + panelHeight - 24, bw = (pw - 4) / 3;
        String id = entry.id();
        primary(graphics, mouseX, mouseY, px, ay, bw, 16, Component.translatable("robotics.pioneers.plans.use"), true, () -> {
            commitName();
            Blueprint chosen = BlueprintLibrary.get(id);
            if (chosen != null) {
                use.accept(chosen);
                onClose();
            }
        });
        tooltip(mouseX, mouseY, px, ay, bw, 16, Component.translatable("robotics.pioneers.plans.use.tooltip"));
        button(graphics, mouseX, mouseY, px + bw + 2, ay, bw, 16, Component.translatable("robotics.pioneers.plans.duplicate"), true, () -> {
            commitName();
            Blueprint copy = BlueprintLibrary.duplicate(id, Component.translatable("robotics.pioneers.plans.copy_suffix").getString());
            if (copy != null) {
                select(copy.id());
            }
        });
        button(graphics, mouseX, mouseY, px + 2 * (bw + 2), ay, pw - 2 * (bw + 2), 16,
              Component.translatable(confirmDelete ? "robotics.pioneers.plans.delete_confirm" : "robotics.pioneers.plans.delete"), true, () -> {
                  if (confirmDelete) {
                      BlueprintLibrary.delete(id);
                      selected = null;
                      confirmDelete = false;
                      syncName();
                  } else {
                      confirmDelete = true;
                  }
              });
    }

    private static String defaultName() {
        return Component.translatable("robotics.pioneers.plans.default_name", BlueprintLibrary.list().size() + 1).getString();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < left + LIST) {
            scroll -= (int) Math.signum(delta);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (name.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            commitName();
            name.setFocused(false);
            setFocused(null);
            return true;
        }
        for (EditBox box : new EditBox[]{search, name}) {
            if (box.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
                return box.keyPressed(key, scan, modifiers) || box.canConsumeInput();
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void removed() {
        commitName();
        super.removed();
    }
}

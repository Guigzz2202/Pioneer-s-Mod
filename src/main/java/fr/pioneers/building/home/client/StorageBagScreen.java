package fr.pioneers.building.home.client;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.core.BigSlotsClient;
import fr.pioneers.client.gui.core.PiMenuScreen;
import fr.pioneers.farm.crop.GeneticSeedItem;
import fr.pioneers.building.home.bag.BagItem;
import fr.pioneers.building.home.bag.BagLedger;
import fr.pioneers.building.home.bag.BagStore;
import fr.pioneers.building.home.bag.PacketBag;
import fr.pioneers.building.home.bag.StorageBagMenu;
import fr.pioneers.core.network.PiNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 31, retour 42) : ecran d'un sac de RANGEMENT ({@link StorageBagMenu}) : barre d'outils (ramassage oui / non, filtre,
 * « Tout ranger »), grille des cases (une SORTE par case, nombre abrege en bas a droite : « 1,2k »), ligne d'etat (objets / capacite, sortes,
 * niveau), puis l'inventaire. Le contenu est relu dans le NBT du sac tenu (synchronise par l'inventaire) a chaque tick du client ou il change.
 * Gestes de la grille ({@link PacketBag}) : clic = une pile en main (pile tenue : la deposer ; clic droit avec une pile : un seul objet),
 * clic droit = CHOISIR la sorte (semis, pose, aquarium), Maj + clic = une pile vers l'inventaire ; mode filtre : clic = bloquer / debloquer
 * au ramassage (avec un objet en main : cet objet).
 */
public class StorageBagScreen extends PiMenuScreen<StorageBagMenu> {

    /** Filet d'accent (graines, pousses, poissons : comme {@link BagScreen}). */
    private static final int[] ACCENTS = {0xFFB07A44, 0xFF7A4A28, 0xFFB8A070, 0xFF8AA060, 0xFF6A9AB0};
    public static final int SELECTED = 0xFFF0C040, FILTERED = 0x90B02020, HOVER = 0x60FFFFFF;

    @Nullable
    private CompoundTag seen;
    private BagLedger view;
    private final List<ItemStack> icons = new ArrayList<>();
    private boolean filterMode;

    public StorageBagScreen(StorageBagMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, StorageBagMenu.WIDTH, StorageBagMenu.height(menu.kind, menu.tier));
        inventoryLabelY = menu.inventoryTop() - 11;
        view = new BagLedger(menu.kind.mode());
        refresh(true);
    }

    /** Relit le registre si le NBT du sac a change. */
    void refresh(boolean force) {
        ItemStack bag = menu.bag();
        CompoundTag tag = bag.getTag();
        if (!force && Objects.equals(tag, seen)) {
            return;
        }
        seen = tag == null ? null : tag.copy();
        view = BagStore.read(bag, menu.kind);
        icons.clear();
        for (BagLedger.Entry e : view.entries) {
            icons.add(BagStore.display(view, e));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh(false);
    }

    int cases() {
        return menu.kind.cases(menu.tier);
    }

    /** Case sous la souris (relative au panneau), -1 hors grille. */
    int caseAt(double mx, double my) {
        int x = (int) Math.floor(mx) - StorageBagMenu.GRID_X + 1, y = (int) Math.floor(my) - StorageBagMenu.GRID_TOP + 1;
        if (x < 0 || y < 0 || x >= StorageBagMenu.COLUMNS * StorageBagMenu.SLOT) {
            return -1;
        }
        int i = y / StorageBagMenu.SLOT * StorageBagMenu.COLUMNS + x / StorageBagMenu.SLOT;
        return i < cases() ? i : -1;
    }

    // ------------------------------------------------------------------ dessin

    @Override
    protected void drawPanel(GuiGraphics g, int x, int y) {
        super.drawPanel(g, x, y);
        g.fill(x, y, x + imageWidth, y + 1, ACCENTS[menu.kind.ordinal()]);
        int rows = StorageBagMenu.rows(menu.kind, menu.tier), w = StorageBagMenu.COLUMNS * StorageBagMenu.SLOT + 4;
        g.renderOutline(x + StorageBagMenu.GRID_X - 3, y + StorageBagMenu.GRID_TOP - 3, w, rows * StorageBagMenu.SLOT + 4,
              ACCENTS[menu.kind.ordinal()] & 0x80FFFFFF);
    }

    @Override
    protected void drawSlot(GuiGraphics g, Slot slot, int x, int y) {
        if (!slot.isActive()) {
            return;
        }
        super.drawSlot(g, slot, x, y);
        if (menu.lockedMenuSlot() >= 0 && slot.index == menu.lockedMenuSlot()) {
            g.fill(x, y, x + 16, y + 16, 0x90000000);
        }
    }

    @Override
    protected void drawBackground(GuiGraphics g, int mx, int my) {
        //barre d'outils
        int ty = StorageBagMenu.TOOL_Y, th = StorageBagMenu.TOOL_H;
        button(g, mx, my, 8, ty, 62, th, Component.translatable(view.pickup ? "home.pioneers.bag.gui.pickup_on" : "home.pioneers.bag.gui.pickup_off"), true,
              () -> send(StorageBagMenu.PICKUP, -1, "", 0));
        if (over(mx, my, 8, ty, 62, th)) {
            tooltip(List.of(Component.translatable("home.pioneers.bag.gui.pickup.tip")));
        }
        button(g, mx, my, 72, ty, 44, th, Component.translatable("home.pioneers.bag.gui.filter", view.filter.size()), true, () -> filterMode = !filterMode);
        if (filterMode) {
            g.renderOutline(71, ty - 1, 46, th + 2, ACCENT);
        }
        if (over(mx, my, 72, ty, 44, th)) {
            tooltip(filterTip());
        }
        button(g, mx, my, 118, ty, 50, th, Component.translatable("home.pioneers.bag.gui.store_all"), true, () -> send(StorageBagMenu.STORE_ALL, -1, "", 0));
        if (over(mx, my, 118, ty, 50, th)) {
            tooltip(List.of(Component.translatable("home.pioneers.bag.gui.store_all.tip")));
        }
        //grille
        int hover = caseAt(mx, my);
        for (int i = 0; i < cases(); i++) {
            int x = StorageBagMenu.GRID_X + i % StorageBagMenu.COLUMNS * StorageBagMenu.SLOT, y = StorageBagMenu.GRID_TOP + i / StorageBagMenu.COLUMNS * StorageBagMenu.SLOT;
            slot(g, x, y);
            if (i < icons.size() && !icons.get(i).isEmpty()) {
                g.renderItem(icons.get(i), x, y);
            }
        }
        //ligne d'etat
        int per = view.perCase(), cases = cases();
        GuiDraw.text(g, font, Component.translatable("home.pioneers.bag.gui.info", exact(view.total()), exact((long) per * cases), view.entries.size(), cases,
              BagItem.ROMAN[view.tier]), 8, StorageBagMenu.infoY(menu.kind, menu.tier) + 1, 0.7F, DIM, imageWidth - 16);
        if (hover >= 0) {
            caseTooltip(hover);
        }
    }

    @Override
    protected void drawForeground(GuiGraphics g, int mx, int my) {
        int hover = caseAt(mx, my);
        for (int i = 0; i < cases(); i++) {
            int x = StorageBagMenu.GRID_X + i % StorageBagMenu.COLUMNS * StorageBagMenu.SLOT, y = StorageBagMenu.GRID_TOP + i / StorageBagMenu.COLUMNS * StorageBagMenu.SLOT;
            if (i < view.entries.size()) {
                BagLedger.Entry e = view.entries.get(i);
                if (view.filter.contains(e.id)) {
                    g.fill(x, y, x + 16, y + 16, FILTERED);
                    g.fill(x + 1, y + 1, x + 3, y + 3, 0xFFFF6060);
                }
                String text = BigSlotsClient.shortCount(e.total);
                float scale = 0.75F;
                g.pose().pushPose();
                g.pose().translate(x + 17 - font.width(text) * scale, y + 17 - 7 * scale, 0);
                g.pose().scale(scale, scale, 1);
                g.drawString(font, text, 0, 0, 0xFFFFFF, true);
                g.pose().popPose();
                if (e.id.equals(view.selected)) {
                    g.renderOutline(x - 1, y - 1, 18, 18, SELECTED);
                }
            }
            if (i == hover) {
                g.fill(x, y, x + 16, y + 16, HOVER);
            }
        }
    }

    List<Component> filterTip() {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("home.pioneers.bag.gui.filter.tip"));
        int n = 0;
        for (String id : view.filter) {
            if (n++ >= 8) {
                out.add(Component.translatable("home.pioneers.bag.more", view.filter.size() - 8).withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            Item it = BagStore.item(id);
            out.add(Component.literal("× ").withStyle(ChatFormatting.RED).append(it == null ? Component.literal(id) : Component.translatable(it.getDescriptionId())));
        }
        return out;
    }

    void caseTooltip(int i) {
        if (i >= view.entries.size()) {
            if (filterMode && !menu.getCarried().isEmpty()) {
                tooltip(List.of(Component.translatable("home.pioneers.bag.gui.filter_carried")));
            }
            return;
        }
        BagLedger.Entry e = view.entries.get(i);
        ItemStack icon = icons.get(i);
        List<Component> lines = new ArrayList<>();
        lines.add(icon.isEmpty() ? Component.literal(e.id) : icon.getHoverName());
        lines.add(Component.translatable("home.pioneers.bag.gui.count", exact(e.total), exact(view.perCase())).withStyle(ChatFormatting.GRAY));
        if (view.mode == BagLedger.Mode.GENES && icon.getItem() instanceof GeneticSeedItem) {
            int[] byStars = new int[6];
            for (var v : e.variants.entrySet()) {
                byStars[BagLedger.stars(v.getKey())] += v.getValue();
            }
            for (int s = 5; s >= 1; s--) {
                if (byStars[s] > 0) {
                    lines.add(GeneticSeedItem.stars(s).append(Component.literal("  × " + exact(byStars[s])).withStyle(ChatFormatting.GRAY)));
                }
            }
        }
        if (view.mode == BagLedger.Mode.SIZE && e.size > 0) {
            lines.add(Component.translatable("home.pioneers.bag.biggest", e.size).withStyle(ChatFormatting.AQUA));
        }
        if (e.id.equals(view.selected)) {
            lines.add(Component.translatable("home.pioneers.bag.gui.chosen").withStyle(ChatFormatting.GOLD));
        }
        if (view.filter.contains(e.id)) {
            lines.add(Component.translatable("home.pioneers.bag.gui.blocked").withStyle(ChatFormatting.RED));
        }
        lines.add(Component.translatable(filterMode ? "home.pioneers.bag.gui.filter_hint" : "home.pioneers.bag.gui.case_hint").withStyle(ChatFormatting.DARK_GRAY));
        tooltip(lines);
    }

    /** Nombre exact avec separateur des milliers (espace en francais, comme {@code Texts.grouped} ; virgule en anglais americain). */
    static String exact(long n) {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        boolean fr = code != null && code.toLowerCase(Locale.ROOT).startsWith("fr");
        String s = String.format(Locale.ROOT, "%,d", n);
        return fr ? s.replace(',', ' ') : s;
    }

    // ------------------------------------------------------------------ gestes

    void send(int op, int index, String id, int arg) {
        PiNetwork.sendToServer(new PacketBag(op, index, id, arg));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int i = caseAt(mouseX - leftPos, mouseY - topPos);
        if (i >= 0 && (button == 0 || button == 1)) {
            ItemStack carried = menu.getCarried();
            boolean has = i < view.entries.size();
            String id = has ? view.entries.get(i).id : "";
            if (filterMode) {
                if (!carried.isEmpty()) {
                    send(StorageBagMenu.FILTER, -1, "", 0);
                } else if (has) {
                    send(StorageBagMenu.FILTER, i, id, 0);
                }
            } else if (!carried.isEmpty()) {
                send(StorageBagMenu.DEPOSIT, -1, "", button == 1 ? 1 : 0);
            } else if (has && Screen.hasShiftDown()) {
                send(StorageBagMenu.TO_INVENTORY, i, id, 0);
            } else if (has && button == 1) {
                send(StorageBagMenu.SELECT, i, id, 0);
            } else if (has) {
                send(StorageBagMenu.TAKE, i, id, 0);
            }
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BUNDLE_INSERT, 1.2F, 0.4F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

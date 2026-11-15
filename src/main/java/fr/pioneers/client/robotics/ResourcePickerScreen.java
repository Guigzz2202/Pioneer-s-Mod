package fr.pioneers.client.robotics;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.robotics.LogisticResource;
import fr.pioneers.robotics.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Choix d'une ressource : onglets objets, fluides, gaz, energie et donnees ; recherche par nom ; la premiere case vaut « n'importe
 * laquelle de la famille » quand c'est permis. Les objets de l'inventaire viennent en tete.
 */
public class ResourcePickerScreen extends RoboticsScreen {

    private static final int COLS = 12, ROWS = 7;
    private static final Map<Resource, List<LogisticResource>> CATALOG = new EnumMap<>(Resource.class);

    private final Consumer<LogisticResource> result;
    private final boolean allowAny;
    private final Set<Resource> families;
    private Resource tab;
    /** Onglet des tags d'objets (« tous les rondins », « tous les minerais »...). */
    private boolean tagsTab;
    /** Tags d'un objet precis (clic droit sur l'objet), ou null : tous les tags. */
    @Nullable
    private ItemStack tagsOf;
    private EditBox search;
    private List<LogisticResource> shown = List.of();
    private int scroll;

    /**
     * @param families familles proposees (toutes si vide)
     * @param allowAny proposer « n'importe laquelle » de la famille
     */
    public ResourcePickerScreen(Screen parent, @Nullable Resource initial, Set<Resource> families, boolean allowAny, Consumer<LogisticResource> result) {
        super(Component.translatable("robotics.pioneers.picker.title"), parent, COLS * 18 + 24, ROWS * 18 + 82);
        this.result = result;
        this.allowAny = allowAny;
        this.families = families.isEmpty() ? new LinkedHashSet<>(List.of(Resource.values())) : families;
        this.tab = initial != null && this.families.contains(initial) ? initial : this.families.iterator().next();
        dark = true;
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, left + 8, top + 38, panelWidth - 16, 14, Component.translatable("robotics.pioneers.picker.search"));
        search.setResponder(text -> refresh());
        addRenderableWidget(search);
        setInitialFocus(search);
        refresh();
    }

    private static List<LogisticResource> catalog(Resource type) {
        return CATALOG.computeIfAbsent(type, t -> {
            List<LogisticResource> list = new ArrayList<>();
            switch (t) {
                case ITEMS -> {
                    for (Item item : ForgeRegistries.ITEMS.getValues()) {
                        if (item != Items.AIR) {
                            list.add(LogisticResource.of(new ItemStack(item)));
                        }
                    }
                }
                case FLUIDS -> {
                    for (Fluid fluid : ForgeRegistries.FLUIDS.getValues()) {
                        if (fluid != Fluids.EMPTY && fluid.isSource(fluid.defaultFluidState())) {
                            list.add(LogisticResource.of(new FluidStack(fluid, 1)));
                        }
                    }
                }
                case GAS -> {
                    for (Gas gas : PioneersAPI.allGases()) {
                        list.add(LogisticResource.of(gas));
                    }
                }
                case DATA -> {
                    for (DataType data : PioneersAPI.dataTypeRegistry().getValues()) {
                        if (!data.isEmptyType()) {
                            list.add(LogisticResource.of(data));
                        }
                    }
                }
                case ENERGY -> list.add(LogisticResource.ENERGY);
                case PRESSURE -> list.add(LogisticResource.PRESSURE);
            }
            return list;
        });
    }

    private void refresh() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        Set<LogisticResource> found = new LinkedHashSet<>();
        if (allowAny && !tab.isQuantity() && query.isEmpty()) {
            found.add(LogisticResource.any(tab));
        }
        if (tab == Resource.ITEMS && (tagsTab || query.startsWith("#"))) {
            //Tags d'objets : « log », « forge:ores »... (ou ceux d'un objet precis)
            String wanted = query.startsWith("#") ? query.substring(1) : query;
            List<LogisticResource> tags = new ArrayList<>();
            if (tagsOf != null) {
                tagsOf.getTags().forEach(key -> {
                    if (key.location().toString().contains(wanted)) {
                        tags.add(LogisticResource.tag(key));
                    }
                });
            } else {
                ForgeRegistries.ITEMS.tags().getTagNames().forEach(key -> {
                    if (key.location().toString().contains(wanted) && !ForgeRegistries.ITEMS.tags().getTag(key).isEmpty()) {
                        tags.add(LogisticResource.tag(key));
                    }
                });
            }
            tags.sort(Comparator.comparing(res -> res.tagKey().location().toString()));
            shown = tags;
            scroll = 0;
            return;
        }
        if (tab == Resource.ITEMS && minecraft != null && minecraft.player != null) {
            for (ItemStack stack : minecraft.player.getInventory().items) {
                if (!stack.isEmpty()) {
                    LogisticResource res = LogisticResource.of(stack);
                    if (matches(res, query)) {
                        found.add(res);
                    }
                }
            }
        }
        for (LogisticResource res : catalog(tab)) {
            if (matches(res, query)) {
                found.add(res);
            }
        }
        shown = new ArrayList<>(found);
        scroll = 0;
    }

    private static boolean matches(LogisticResource res, String query) {
        if (query.isEmpty()) {
            return true;
        }
        if (res.name().getString().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        return res.type == Resource.ITEMS && !res.isAny() && ForgeRegistries.ITEMS.getKey(res.item().getItem()).toString().contains(query);
    }

    private boolean rightClick;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        rightClick = button == 1;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int rows = (shown.size() + COLS - 1) / COLS;
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, rows - ROWS));
        return true;
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //Onglets des familles
        boolean items = families.contains(Resource.ITEMS);
        int tx = left + 8, tw = (panelWidth - 16) / (families.size() + (items ? 1 : 0));
        for (Resource type : families) {
            chip(graphics, mouseX, mouseY, tx, top + 19, tw - 2, 14, Component.translatable(type.getTranslationKey()), tab == type && !tagsTab, 0x3A7FC0,
                  () -> {
                      tab = type;
                      tagsTab = false;
                      tagsOf = null;
                      refresh();
                  });
            tx += tw;
            if (type == Resource.ITEMS) {
                chip(graphics, mouseX, mouseY, tx, top + 19, tw - 2, 14, Component.translatable("robotics.pioneers.picker.tags"), tagsTab, 0x2E9E78,
                      () -> {
                          tab = Resource.ITEMS;
                          tagsTab = true;
                          tagsOf = null;
                          refresh();
                      });
                tooltip(mouseX, mouseY, tx, top + 19, tw - 2, 14, Component.translatable("robotics.pioneers.picker.tags.tooltip"));
                tx += tw;
            }
        }
        int x0 = left + 12, y0 = top + 58;
        for (int i = 0; i < COLS * ROWS; i++) {
            int index = scroll * COLS + i;
            int x = x0 + (i % COLS) * 18, y = y0 + (i / COLS) * 18;
            darkSlot(graphics, x, y);
            if (index >= shown.size()) {
                continue;
            }
            LogisticResource res = shown.get(index);
            ResourceIcon.draw(graphics, res, x, y);
            if (over(mouseX, mouseY, x, y, 16, 16)) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 250);
                graphics.fill(x, y, x + 16, y + 16, 0x60FFFFFF);
                graphics.pose().popPose();
                if (res.type == Resource.ITEMS && !res.isAny()) {
                    tooltip(mouseX, mouseY, x, y, 16, 16, res.name(),
                          Component.translatable("robotics.pioneers.picker.item_tags").withStyle(ChatFormatting.GRAY));
                } else if (over(mouseX, mouseY, x, y, 16, 16)) {
                    tooltip(resourceLines(res));
                }
            }
            clickBoth(x, y, 16, 16, () -> {
                if (rightClick && res.type == Resource.ITEMS && !res.isAny()) {
                    //Clic droit : les tags de cet objet
                    tagsTab = true;
                    tagsOf = res.item();
                    search.setValue("");
                    refresh();
                    return;
                }
                result.accept(res);
                minecraft.setScreen(parent);
            });
        }
        //Les deux textes du pied partagent la ligne : chacun garde sa part, sans se chevaucher
        int tagsW = 0;
        if (tagsTab && tagsOf != null) {
            Component tags = Component.translatable("robotics.pioneers.picker.tags_of", tagsOf.getHoverName());
            tagsW = (int) Math.min(font.width(tags) * 0.6F, panelWidth * 0.45F);
            GuiDraw.text(graphics, font, tags, left + panelWidth - 8 - tagsW, top + panelHeight - 14, 0.6F, 0xFF2E9E78, tagsW);
        }
        GuiDraw.text(graphics, font, Component.translatable(tab == Resource.ITEMS ? "robotics.pioneers.picker.hint_tags"
                                                                              : "robotics.pioneers.picker.hint", shown.size()), left + 8,
              top + panelHeight - 14, 0.6F, D_DIM, panelWidth - 16 - (tagsW > 0 ? tagsW + 6 : 0));
    }
}

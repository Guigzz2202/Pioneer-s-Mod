package fr.pioneers.logistics.rail.client;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.logistics.robotics.client.ResourcePickerScreen;
import fr.pioneers.logistics.robotics.client.RoboticsScreen;
import fr.pioneers.core.network.PacketRail;
import fr.pioneers.core.network.PiNetwork;
import fr.pioneers.logistics.rail.logistics.Cargo;
import fr.pioneers.logistics.rail.logistics.RailResources;
import fr.pioneers.logistics.rail.logistics.StationLogistics;
import fr.pioneers.logistics.robotics.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.0 (refaite en 0.20.1 : plus lisible, limite saisie au clavier) : fiche d'une gare : nom (les gares du meme nom se partagent les
 * trains : la moins chargee est choisie), limite de trains (sans limite, fermee, ou 1 a 64 trains en route ou a quai), trains attendus.
 * <p>
 * 0.20.5 : colonne Logistique : role (depot, fournit, demande, les deux), ressources et quantites, seuil de livraison, priorite, groupe de
 * trains autorise, commandes des terminaux ; stock du quai et reseau robotique. Bouton « + ressource » du nom : jeton de ressource (gares
 * nommees par ressource, jokers des interruptions).
 */
public class StationScreen extends RoboticsScreen {

    private static final int PAD = 8;

    private long pos;
    /** 0.20.11 : fiche d'un port (memes reglages ; textes de la marine quand ils existent). */
    private boolean harbor;
    private String name = "";
    private int limit, coming, here, same, posts;
    private boolean network;
    private StationLogistics logi = new StationLogistics();
    private final List<StationLogistics.Entry> stock = new ArrayList<>();
    private final List<String> groups = new ArrayList<>();
    private EditBox nameBox;
    private int scroll, contentHeight, listTop, listBottom;
    private boolean groupPopup;

    public StationScreen(CompoundTag data) {
        super(Component.translatable(data.getBoolean("harbor") ? "naval.pioneers.harbor.title" : "rail.pioneers.station.title"), null, 420, 240);
        harbor = data.getBoolean("harbor");
        read(data);
    }

    /** Texte de la gare, ou celui du port s'il existe ({@code naval.pioneers.harbor...} au lieu de {@code rail.pioneers.station...}). */
    private MutableComponent tr(String key, Object... args) {
        if (harbor) {
            String naval = key.startsWith("rail.pioneers.station.") ? "naval.pioneers.harbor." + key.substring("rail.pioneers.station.".length())
                  : "naval.pioneers." + key.substring("rail.pioneers.".length());
            if (I18n.exists(naval)) {
                return Component.translatable(naval, args);
            }
        }
        return Component.translatable(key, args);
    }

    public long pos() {
        return pos;
    }

    private void read(CompoundTag t) {
        pos = t.getLong("pos");
        name = t.getString("name");
        limit = t.getInt("limit");
        coming = t.getInt("coming");
        here = t.getInt("here");
        same = t.getInt("same");
        posts = t.getInt("posts");
        network = t.getBoolean("network");
        logi = StationLogistics.load(t.getCompound("logi"));
        stock.clear();
        for (Tag e : t.getList("stock", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            stock.add(new StationLogistics.Entry(Cargo.load(c), c.getLong("n")));
        }
        stock.sort((a, b) -> Long.compare(b.amount(), a.amount()));
        groups.clear();
        for (Tag g : t.getList("groups", Tag.TAG_STRING)) {
            groups.add(g.getAsString());
        }
    }

    public void update(CompoundTag data) {
        read(data);
        if (nameBox != null && !nameBox.isFocused()) {
            nameBox.setValue(name);
        }
    }

    @Override
    protected void init() {
        super.init();
        nameBox = new EditBox(font, left + PAD, top + 31, Math.min(200, panelWidth / 2 - PAD - 4) - 44, 14, tr("rail.pioneers.station.name"));
        nameBox.setMaxLength(64);
        nameBox.setValue(name);
        addRenderableWidget(nameBox);
    }

    private void send() {
        String n = nameBox.getValue().trim();
        PiNetwork.sendToServer(new PacketRail.StationEdit(BlockPos.of(pos), n.isEmpty() ? name : n, limit, new CompoundTag()));
    }

    private void sendLogistics() {
        String n = nameBox.getValue().trim();
        PiNetwork.sendToServer(new PacketRail.StationEdit(BlockPos.of(pos), n.isEmpty() ? name : n, limit, logi.save()));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (editing()) {
            return super.keyPressed(key, scan, mods);
        }
        if (groupPopup && key == 256) {
            groupPopup = false;
            return true;
        }
        if (nameBox.isFocused() && (key == 257 || key == 335)) {
            send();
            nameBox.setFocused(false);
            return true;
        }
        if (nameBox.isFocused() && key != 256) {
            return nameBox.keyPressed(key, scan, mods) || true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        commitEdit();
        if (!nameBox.getValue().trim().equals(name)) {
            send();
        }
        super.onClose();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        super.mouseScrolled(mx, my, delta);
        scroll = Mth.clamp(scroll - (int) (delta * 16), 0, Math.max(0, contentHeight - (listBottom - listTop)));
        return true;
    }

    @Override
    protected void drawContents(@NotNull GuiGraphics g, int mx, int my, float partial) {
        int lw = Math.min(200, panelWidth / 2 - PAD - 4);
        int x = left + PAD, bottom = top + panelHeight - 6;
        GuiDraw.text(g, font, tr("rail.pioneers.station.name"), x, top + 21, 0.75F, D_DIM, lw);
        //Jeton de ressource dans le nom
        button(g, mx, my, x + lw - 42, top + 31, 42, 14, tr("rail.pioneers.station.add_token"), true,
              () -> minecraft.setScreen(new ResourcePickerScreen(this, Resource.ITEMS, Set.of(Resource.ITEMS, Resource.FLUIDS, Resource.GAS), false, res -> {
                  Cargo c = RailResources.fromLogistic(res);
                  if (c != null && c.id() != null) {
                      String tok = c.token();
                      String now = nameBox.getValue().trim();
                      String next = (now.isEmpty() ? "" : now + " ") + tok;
                      if (next.length() <= 64) {
                          nameBox.setValue(next);
                          name = next;
                          send();
                      }
                  }
              })));
        tooltip(mx, my, x + lw - 42, top + 31, 42, 14, tr("rail.pioneers.station.add_token_tip"));
        if (!name.isEmpty() && name.contains("[")) {
            GuiDraw.text(g, font, RailText.station(name), x, top + 47, 0.7F, ACCENT, lw);
        }
        int y = top + 58;
        GuiDraw.text(g, font, tr("rail.pioneers.station.limit").withStyle(ChatFormatting.BOLD), x, y, 0.75F, 0xFF7FE6C0, lw);
        g.fill(x, y + 9, x + lw, y + 10, D_LINE);
        y += 14;
        chip(g, mx, my, x, y, 62, 14, tr("rail.pioneers.station.unlimited"), limit < 0, 0x2E9E78, () -> {
            limit = -1;
            send();
        });
        chip(g, mx, my, x + 64, y, 50, 14, tr("rail.pioneers.station.closed"), limit == 0, 0xB5413A, () -> {
            limit = 0;
            send();
        });
        int sx = x + 117, sw = lw - 117;
        if (limit > 0) {
            g.fill(sx - 1, y - 1, sx + sw + 1, y + 15, 0xFF2E9E78);
        }
        stepper(g, mx, my, sx, y + 1, sw, Math.max(1, limit), 1, 64, 8, "", v -> {
            limit = v;
            send();
        });
        y += 22;
        GuiDraw.text(g, font, tr("rail.pioneers.station.coming", coming, here).withStyle(ChatFormatting.BOLD), x, y, 0.8F, D_TEXT, lw);
        y += 11;
        if (same > 1) {
            y += wrapped(g, tr("rail.pioneers.station.same", same), x, y, 0.75F, ACCENT, lw, 2) + 2;
        }
        int lines = Math.max(0, (bottom - y) / 8);
        wrapped(g, tr("rail.pioneers.station.hint"), x, y + 1, 0.75F, D_FAINT, lw, lines);
        drawLogistics(g, mx, my, x + lw + 10, left + panelWidth - PAD);
    }

    // ------------------------------------------------------------------ logistique

    private void drawLogistics(GuiGraphics g, int mx, int my, int x0, int x1) {
        int w = x1 - x0;
        int y = top + 21;
        GuiDraw.text(g, font, tr("rail.pioneers.station.logistics").withStyle(ChatFormatting.BOLD), x0, y, 0.75F, 0xFF7FE6C0, w);
        g.fill(x0, y + 9, x1, y + 10, D_LINE);
        y += 13;
        StationLogistics.Role[] roles = StationLogistics.Role.values();
        int cw = (w - (roles.length - 1) * 2) / roles.length;
        int[] colors = {0x4F6A80, 0x8A6A20, 0xB5413A, 0x3F76E4, 0x6E4FB0};
        for (int i = 0; i < roles.length; i++) {
            StationLogistics.Role r = roles[i];
            int cx = x0 + i * (cw + 2);
            chip(g, mx, my, cx, y, cw, 14, tr("rail.pioneers.station.role." + r.name().toLowerCase(Locale.ROOT)), logi.role == r, colors[i], () -> {
                logi.role = r;
                sendLogistics();
            });
            tooltip(mx, my, cx, y, cw, 14, tr("rail.pioneers.station.role." + r.name().toLowerCase(Locale.ROOT) + ".tip"));
        }
        y += 18;
        listTop = y;
        listBottom = top + panelHeight - PAD - 24;
        beginClip(g, x0, listTop, x1, listBottom);
        int cy = y - scroll;
        if (logi.role == StationLogistics.Role.NONE || logi.role == StationLogistics.Role.DEPOT) {
            cy += wrapped(g, Component.translatable(logi.role == StationLogistics.Role.DEPOT ? "rail.pioneers.station.depot_hint" : "rail.pioneers.station.none_hint"),
                  x0, cy, 0.72F, D_DIM, w, 8) + 4;
        } else {
            Component what = Component.translatable(logi.role == StationLogistics.Role.SUPPLY ? "rail.pioneers.station.entries.supply"
                  : logi.role == StationLogistics.Role.DEMAND ? "rail.pioneers.station.entries.demand" : "rail.pioneers.station.entries.both");
            GuiDraw.text(g, font, what, x0, cy, 0.7F, D_DIM, w);
            cy += 10;
            for (int i = 0; i < logi.entries.size(); i++) {
                cy = drawEntry(g, mx, my, x0, cy, w, i) + 2;
            }
            if (logi.entries.size() < StationLogistics.MAX_ENTRIES) {
                button(g, mx, my, x0, cy, Math.min(110, w), 12, tr("rail.pioneers.station.add_resource"), true, () -> pickResource(-1));
                cy += 16;
            }
            // --- seuil, priorite, groupe, terminal
            GuiDraw.text(g, font, tr("rail.pioneers.station.threshold"), x0, cy + 2, 0.7F, D_DIM, 70);
            stepper(g, mx, my, x0 + 72, cy, Math.min(100, w - 72), (int) Math.min(Integer.MAX_VALUE, logi.threshold), 0, 1_000_000, 64, "", v -> {
                logi.threshold = v;
                sendLogistics();
            });
            tooltip(mx, my, x0, cy, 70, 12, tr("rail.pioneers.station.threshold_tip"));
            cy += 15;
            GuiDraw.text(g, font, tr("rail.pioneers.station.priority"), x0, cy + 2, 0.7F, D_DIM, 70);
            String[] pr = {"low", "normal", "high"};
            int pcw = (Math.min(w - 72, 132) - 4) / 3;
            for (int p = -1; p <= 1; p++) {
                int fp = p;
                chip(g, mx, my, x0 + 72 + (p + 1) * (pcw + 2), cy, pcw, 12, tr("rail.pioneers.station.priority." + pr[p + 1]), logi.priority == p,
                      0x2E9E78, () -> {
                          logi.priority = fp;
                          sendLogistics();
                      });
            }
            cy += 15;
            GuiDraw.text(g, font, tr("rail.pioneers.station.group"), x0, cy + 2, 0.7F, D_DIM, 70);
            int gx = x0 + 72, gw = Math.min(130, w - 72), gy = cy;
            boolean hover = over(mx, my, gx, cy, gw, 12);
            g.fill(gx, cy, gx + gw, cy + 12, hover ? 0xFF2E3A46 : 0xFF222B34);
            g.renderOutline(gx, cy, gw, 12, hover ? 0xFF4A5A68 : 0xFF2F3B46);
            GuiDraw.text(g, font, logi.group.isEmpty() ? tr("rail.pioneers.station.group.any") : Component.literal(logi.group), gx + 3, cy + 3,
                  0.72F, D_TEXT, gw - 12);
            GuiDraw.text(g, font, Component.literal("▾"), gx + gw - 8, cy + 2, 0.8F, D_DIM, 8);
            click(gx, cy, gw, 12, () -> groupPopup = !groupPopup);
            cy += 15;
            if (logi.role.demands()) {
                toggle(g, mx, my, x0, cy + 2, w, tr("rail.pioneers.station.terminal"), logi.terminal, () -> {
                    logi.terminal = !logi.terminal;
                    sendLogistics();
                });
                tooltip(mx, my, x0, cy, w, 12, tr("rail.pioneers.station.terminal_tip"));
                cy += 14;
            }
            if (groupPopup) {
                cy = drawGroupPopup(g, mx, my, gx, gy + 13, gw, cy);
            }
        }
        // --- stock du quai
        cy += 2;
        GuiDraw.text(g, font, tr("rail.pioneers.station.stock", posts).withStyle(ChatFormatting.BOLD), x0, cy, 0.7F, 0xFF7FE6C0, w);
        cy += 10;
        if (stock.isEmpty()) {
            GuiDraw.text(g, font, tr("rail.pioneers.station.stock.empty"), x0, cy, 0.7F, D_FAINT, w);
            cy += 10;
        } else {
            int col = 0, per = Math.max(1, w / 64);
            for (StationLogistics.Entry e : stock) {
                int ex = x0 + col * 64;
                g.pose().pushPose();
                g.pose().translate(ex, cy, 0);
                g.pose().scale(0.7F, 0.7F, 1);
                g.renderItem(RailText.icon(e.cargo()), 0, 0);
                g.pose().popPose();
                GuiDraw.text(g, font, Component.literal(RailText.amount(e.amount())), ex + 13, cy + 3, 0.7F, D_TEXT, 50);
                tooltip(mx, my, ex, cy, 62, 12, RailText.name(e.cargo()));
                if (++col >= per) {
                    col = 0;
                    cy += 13;
                }
            }
            if (col > 0) {
                cy += 13;
            }
        }
        contentHeight = cy + scroll - listTop;
        endClip(g);
        //Reseau robotique
        Component net = Component.translatable(network ? "rail.pioneers.station.network" : "rail.pioneers.station.no_network");
        wrapped(g, net, x0, listBottom + 4, 0.7F, network ? ACCENT : D_FAINT, w, 2);
    }

    private int drawEntry(GuiGraphics g, int mx, int my, int x, int y, int w, int i) {
        StationLogistics.Entry e = logi.entries.get(i);
        g.fill(x, y, x + w, y + 14, 0xFF161E25);
        iconChip(g, mx, my, x + 1, y + 1, 12, 12, RailText.icon(e.cargo()), false, 0x3F76E4, () -> pickResource(i));
        tooltip(mx, my, x + 1, y + 1, 12, 12, RailText.name(e.cargo()));
        GuiDraw.text(g, font, RailText.name(e.cargo()), x + 16, y + 4, 0.7F, D_TEXT, w - 16 - 112);
        int sw = 96;
        int max = e.cargo().kind() == Cargo.Kind.ITEM ? 100_000 : 2_000_000_000;
        int big = e.cargo().kind() == Cargo.Kind.ITEM ? 64 : 1000;
        stepper(g, mx, my, x + w - sw - 14, y + 1, sw, (int) Math.min(Integer.MAX_VALUE, e.amount()), 0, max, big, RailText.unit(e.cargo().kind()), v -> {
            if (i < logi.entries.size()) {
                logi.entries.set(i, new StationLogistics.Entry(logi.entries.get(i).cargo(), v));
                sendLogistics();
            }
        });
        button(g, mx, my, x + w - 12, y + 1, 12, 12, Component.literal("✕"), true, () -> {
            if (i < logi.entries.size()) {
                logi.entries.remove(i);
                sendLogistics();
            }
        });
        return y + 14;
    }

    private void pickResource(int index) {
        minecraft.setScreen(new ResourcePickerScreen(this, Resource.ITEMS, Set.of(), true, res -> {
            Cargo c = RailResources.fromLogistic(res);
            if (c == null || c.id() == null && c.kind() != Cargo.Kind.ENERGY && c.kind() != Cargo.Kind.AIR) {
                return;
            }
            long amount = switch (c.kind()) {
                case ITEM -> 640;
                case FLUID, GAS -> 16_000;
                case ENERGY -> 10_000_000;
                case DATA -> 100_000;
                case AIR -> 5_000;
            };
            if (index >= 0 && index < logi.entries.size()) {
                logi.entries.set(index, new StationLogistics.Entry(c, logi.entries.get(index).amount()));
            } else if (logi.entries.size() < StationLogistics.MAX_ENTRIES) {
                logi.entries.add(new StationLogistics.Entry(c, amount));
            }
            sendLogistics();
        }));
    }

    private int drawGroupPopup(GuiGraphics g, int mx, int my, int x, int y, int w, int cy) {
        List<String> items = new ArrayList<>();
        items.add("");
        items.addAll(groups);
        int rows = Math.min(8, items.size());
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(x - 1, y - 1, x + w + 1, y + rows * 12 + 3, 0xFF3FD0A0);
        g.fill(x, y, x + w, y + rows * 12 + 2, 0xF20B1016);
        for (int r = 0; r < rows; r++) {
            String gname = items.get(r);
            int ry = y + 1 + r * 12;
            boolean hover = over(mx, my, x, ry, w, 12);
            g.fill(x + 1, ry, x + w - 1, ry + 12, hover ? 0xFF2E3A46 : 0x00000000);
            GuiDraw.text(g, font, gname.isEmpty() ? tr("rail.pioneers.station.group.any") : Component.literal(gname), x + 4, ry + 3, 0.72F,
                  gname.equalsIgnoreCase(logi.group) ? ACCENT : D_TEXT, w - 8);
            click(x, ry, w, 12, () -> {
                logi.group = gname;
                groupPopup = false;
                sendLogistics();
            });
        }
        g.pose().popPose();
        return Math.max(cy, y + rows * 12 + 4 - 0);
    }
}

package fr.pioneers.world.pet.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.pioneers.client.PiKeys;
import fr.pioneers.core.network.PiNetwork;
import fr.pioneers.world.pet.PacketPetList;
import fr.pioneers.world.pet.PacketPetRecall;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * 1.0.5-beta (lot 5, specification §5.5) : la ROUE du rappel (touche Rappel maintenue) : une case par animal dont on est le maitre (oeuf
 * de son espece ou de sa race, nom, distance ou dimension), case visee par la direction de la souris depuis le centre ; relacher la touche
 * (ou cliquer) rappelle cet animal seul ; Echap ou relacher au centre : rien. Le jeu continue derriere (pas de pause).
 * <p>
 * 1.0.5-beta (lot 13, retour de Guillaume : « buggee ») : mise en page PURE {@link WheelLayout} (rayon adapte a l'ecran, cases qui ne se
 * chevauchent jamais, pages a la molette au lieu de 16 animaux au plus sans le dire), textes lisibles (nom a l'echelle 1, distance 0,75),
 * consigne en bas de l'ecran, animaux tries (les plus proches d'abord), liste redemandee si le serveur n'a pas repondu (demande trop
 * rapprochee de la precedente : roue vide pour toujours avant), touche relachee hors de la fenetre ou sur un bouton de souris prise en compte.
 * <p>
 * 1.0.5-beta (lot 21, « un ovale tout moche ») : vraie roue ronde (anneau a secteurs, moyeu, arc d'accent), dessinee par la liste pure
 * {@link WheelLayout#paint} que le banc trame aux vraies tailles de fenetre.
 * <p>
 * 1.0.5-beta (lot 33, retour 45 : « des fois vide, de la latence ») : ouverte des l'APPUI ({@link PetRecallClient#open}) avec la liste du
 * cache {@link WheelCache} (reprise a chaque nouvelle reception) ; issue au relachement par {@link WheelCache#decide} (appui bref au centre =
 * tous) ; mise en page et liste de dessin GARDEES tant que rien ne change (avant : refaites a chaque image).
 */
public class PetWheelScreen extends net.minecraft.client.gui.screens.Screen {

    /** Redemande de la liste (ticks) et nombre d'essais. */
    static final int RETRY_TICKS = 25, RETRIES = 4;

    private List<PacketPetList.Row> rows = new ArrayList<>();
    private boolean received;
    private int hovered = -1, page, ticks, asked, seen = Integer.MIN_VALUE;
    /** Heure de l'appui (millisecondes du client) : appui bref ou maintien. */
    private final long pressedAt;
    //1.0.5-beta (lot 33) : mise en page et liste de dessin gardees (cle : taille, nombre, version de la liste, page, case visee, etat)
    private WheelLayout layout;
    private int lw = -1, lh = -1, ln = -1, viewVersion;
    private List<Object> ops;
    private final int[] opsKey = new int[8];

    public PetWheelScreen(long pressedAt) {
        super(Component.translatable("pet.pioneers.recall.wheel"));
        this.pressedAt = pressedAt;
        refresh();
    }

    /** Reprend la liste du cache si elle a change (ouverture, reception), vue depuis la dimension du joueur et ses betes visibles. */
    void refresh() {
        WheelCache c = WheelCache.CLIENT;
        if (c.version() == seen) {
            return;
        }
        seen = c.version();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            rows = new ArrayList<>(c.rows());
            rows.sort(ORDER);
        } else {
            Map<UUID, Integer> live = new HashMap<>();
            for (Entity e : mc.level.entitiesForRendering()) {
                live.put(e.getUUID(), (int) Math.round(e.distanceTo(mc.player)));
            }
            rows = c.view(mc.level.dimension().location().toString(), id -> live.getOrDefault(id, -1));
        }
        received = c.known();
        viewVersion++;
        if (width > 0 && page * Math.max(1, layout().perPage) >= rows.size()) {
            page = 0;
        }
    }

    /** Les plus proches d'abord (meme dimension), puis les autres par dimension et par nom. */
    static final Comparator<PacketPetList.Row> ORDER = WheelCache.ORDER;

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static ItemStack icon(String type) {
        EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.tryParse(type));
        SpawnEggItem egg = SpawnEggItem.byId(t);
        return egg != null ? new ItemStack(egg) : new ItemStack(Items.LEAD);
    }

    WheelLayout layout() {
        if (layout == null || width != lw || height != lh || rows.size() != ln) {
            layout = WheelLayout.of(width, height, rows.size());
            lw = width;
            lh = height;
            ln = rows.size();
        }
        return layout;
    }

    @Override
    public void tick() {
        ticks++;
        refresh();
        //liste pas encore recue (demande refusee car trop proche de la precedente, paquet perdu) : on redemande
        if (!received && asked < RETRIES && ticks % RETRY_TICKS == 0) {
            asked++;
            PiNetwork.sendToServer(new PacketPetRecall(PacketPetRecall.LIST, new UUID(0, 0)));
        }
        //touche relachee sans que l'ecran le sache (fenetre quittee, touche sur un bouton de souris) : comme un relachement
        if (ticks > 2 && minecraft != null && !held(minecraft)) {
            choose();
        }
    }

    /** La touche Rappel est-elle encore enfoncee (clavier ou souris) ? */
    static boolean held(Minecraft mc) {
        InputConstants.Key key = PiKeys.PET_RECALL.getKey();
        long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return key.getValue() == InputConstants.UNKNOWN.getValue() || InputConstants.isKeyDown(window, key.getValue());
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        refresh();
        WheelLayout l = layout();
        page = Math.max(0, Math.min(l.pages - 1, page));
        hovered = sector(mx, my);
        int state = !received ? (asked >= RETRIES ? 1 : 2) : rows.isEmpty() ? 3 : 4;
        int[] key = {width, height, viewVersion, page, hovered, state, l.pages, rows.size()};
        if (ops == null || !java.util.Arrays.equals(key, opsKey)) {
            System.arraycopy(key, 0, opsKey, 0, key.length);
            ops = paint(l);
        }
        draw(g, font, ops);
        super.render(g, mx, my, pt);
    }

    /** Liste de dessin de l'etat courant (refaite seulement quand la cle de {@link #render} change). */
    private List<Object> paint(WheelLayout l) {
        int slots = l.slotsOn(page), first = page * l.perPage;
        List<WheelLayout.Cell> cells = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            PacketPetList.Row r = rows.get(first + i);
            Component where = r.distance() >= 0 ? Component.translatable("pet.pioneers.recall.distance", r.distance())
                  : Component.translatable("pet.pioneers.recall.dimension", dimName(r.dim()));
            cells.add(new WheelLayout.Cell(r.type(), name(r).getString(), where.getString(), r.loaded()));
        }
        Component sub;
        if (!received) {
            sub = Component.translatable(asked >= RETRIES ? "pet.pioneers.recall.no_answer" : "pet.pioneers.recall.loading");
        } else if (rows.isEmpty()) {
            sub = Component.translatable("pet.pioneers.recall.none");
        } else if (l.pages > 1) {
            sub = Component.translatable("pet.pioneers.recall.page", page + 1, l.pages);
        } else {
            sub = Component.translatable("pet.pioneers.recall.count", rows.size());
        }
        //consigne en bas de l'ecran (jamais sur la roue)
        Component hint = !received || rows.isEmpty() ? Component.translatable("pet.pioneers.recall.release_all")
              : hovered >= 0 ? Component.translatable("pet.pioneers.recall.release_name", name(rows.get(first + hovered)))
                : Component.translatable("pet.pioneers.recall.release_all");
        if (l.pages > 1) {
            hint = Component.translatable("pet.pioneers.recall.with_pages", hint);
        }
        //1.0.5-beta (lot 21) : la roue est dessinee par sa liste PURE (WheelLayout.paint), rejouee telle quelle (banc et planche : meme liste)
        return l.paint(cells, hovered, page, title.getString(), sub.getString(), hint.getString(), font::width);
    }

    /**
     * Rejoue la liste de dessin : quadrilateres dans le tampon {@code RenderType.gui()} (comme {@code GuiGraphics.fill}, coordonnees
     * flottantes : contour a la resolution de l'ecran), puis icones, puis textes.
     */
    static void draw(GuiGraphics g, net.minecraft.client.gui.Font font, List<Object> ops) {
        org.joml.Matrix4f m = g.pose().last().pose();
        com.mojang.blaze3d.vertex.VertexConsumer vc = g.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        for (Object o : ops) {
            if (o instanceof WheelLayout.Quad q) {
                int c = q.color(), a = c >>> 24 & 255, r = c >> 16 & 255, gr = c >> 8 & 255, b = c & 255;
                float[] p = q.xy();
                for (int i = 0; i < 4; i++) {
                    vc.vertex(m, p[2 * i], p[2 * i + 1], 0F).color(r, gr, b, a).endVertex();
                }
            }
        }
        g.flush();
        for (Object o : ops) {
            if (o instanceof WheelLayout.Icon ic) {
                g.pose().pushPose();
                g.pose().translate(ic.x(), ic.y(), 0);
                g.pose().scale(ic.scale(), ic.scale(), 1);
                g.renderItem(icon(ic.type()), 0, 0);
                g.pose().popPose();
            }
        }
        for (Object o : ops) {
            if (o instanceof WheelLayout.Text t) {
                g.pose().pushPose();
                g.pose().translate(t.x(), t.y(), 200);
                g.pose().scale(t.scale(), t.scale(), 1);
                g.drawString(font, t.text(), 0, 0, t.color(), false);
                g.pose().popPose();
            }
        }
    }

    static Component name(PacketPetList.Row r) {
        return r.name().isEmpty() ? Component.translatable("entity." + r.type().replace(':', '.')) : Component.literal(r.name());
    }

    int sector(double mx, double my) {
        WheelLayout l = layout();
        return received ? l.sector(mx, my, l.slotsOn(page)) : -1;
    }

    static Component dimName(String dim) {
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        String path = rl == null ? dim : rl.getPath();
        return Component.translatableWithFallback("pet.pioneers.dimension." + path, path);
    }

    void choose() {
        WheelLayout l = layout();
        int i = page * l.perPage + hovered;
        boolean onPet = hovered >= 0 && i < rows.size();
        //1.0.5-beta (lot 33) : appui bref au centre = tous (la roue s'ouvre maintenant a l'appui) ; maintien au centre = rien ; bete visee = elle
        int what = WheelCache.decide(Util.getMillis() - pressedAt, onPet ? hovered : -1);
        PetRecallClient.recall(what, onPet ? rows.get(i).id() : null);
        hovered = -1;
        if (minecraft != null && minecraft.screen == this) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        WheelLayout l = layout();
        if (l.pages > 1 && delta != 0) {
            page = Math.floorMod(page + (delta < 0 ? 1 : -1), l.pages);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (PiKeys.PET_RECALL.matches(key, scan)) {
            choose();
            return true;
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (PiKeys.PET_RECALL.matchesMouse(button)) {
            hovered = sector(mx, my);
            choose();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (PiKeys.PET_RECALL.matchesMouse(button)) {
            return true;
        }
        hovered = sector(mx, my);
        choose();
        return true;
    }

}

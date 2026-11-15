package fr.pioneers.client.minimap;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.robotics.RoboticsScreen;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * 1.10.3 : creation et edition d'un waypoint : nom, position (pas de 1, Maj : 10 ; « Ici » = position du joueur), couleur, pictogramme,
 * visible sur les cartes, rendu dans le monde (1.0.2 : par defaut / balise / point / invisible) ; supprimer ; se teleporter (1.0.4-beta lot 29 :
 * ADMINISTRATEUR = operateur de niveau 2, dans tous les modes de jeu ; la demande part au serveur, {@code core/profile/WaypointTeleport},
 * qui verifie lui-meme le droit et choisit une place sure ; avant : operateur en creatif, par la commande {@code /tp}).
 */
public final class WaypointEditScreen extends RoboticsScreen {

    @Nullable
    private final Waypoint original;
    private final Waypoint edit;
    private EditBox name;
    private boolean confirmDelete;

    /** {@code original} null : nouveau waypoint ({@code edit} est ajoute a l'enregistrement). */
    public WaypointEditScreen(@Nullable Screen parent, @Nullable Waypoint original, Waypoint edit) {
        super(Component.translatable(original == null ? "minimap.pioneers.edit.new" : "minimap.pioneers.edit.title"), parent, 268, 188);
        this.original = original;
        this.edit = edit;
    }

    @Override
    protected void init() {
        super.init();
        name = new EditBox(font, left + 60, top + 22, panelWidth - 68, 14, Component.translatable("minimap.pioneers.edit.name"));
        name.setMaxLength(48);
        name.setValue(edit.name);
        name.setResponder(v -> edit.name = v);
        addRenderableWidget(name);
        setInitialFocus(name);
    }

    @Override
    protected void drawContents(GuiGraphics g, int mx, int my, float partialTick) {
        int x = left + 8, w = panelWidth - 16;
        GuiDraw.text(g, font, Component.translatable("minimap.pioneers.edit.name"), x, top + 25, 0.75F, D_DIM, 50);
        int y = top + 42;
        // position
        GuiDraw.text(g, font, Component.literal("X"), x, y + 3, 0.75F, D_DIM, 10);
        stepper(g, mx, my, x + 8, y, 70, edit.x, -30_000_000, 30_000_000, 10, v -> edit.x = v);
        GuiDraw.text(g, font, Component.literal("Y"), x + 82, y + 3, 0.75F, D_DIM, 10);
        stepper(g, mx, my, x + 90, y, 60, edit.y, -2048, 4096, 10, v -> edit.y = v);
        GuiDraw.text(g, font, Component.literal("Z"), x + 154, y + 3, 0.75F, D_DIM, 10);
        stepper(g, mx, my, x + 162, y, 70, edit.z, -30_000_000, 30_000_000, 10, v -> edit.z = v);
        button(g, mx, my, x + w - 18, y, 18, 12, Component.translatable("minimap.pioneers.edit.here_short"), minecraft.player != null, () -> {
            edit.x = minecraft.player.getBlockX();
            edit.y = minecraft.player.getBlockY();
            edit.z = minecraft.player.getBlockZ();
        });
        tooltip(mx, my, x + w - 18, y, 18, 12, Component.translatable("minimap.pioneers.edit.here"));
        y += 18;
        // couleur
        y = section(g, x, y, w, Component.translatable("minimap.pioneers.edit.color"));
        for (int i = 0; i < Waypoints.PALETTE.length; i++) {
            int c = Waypoints.PALETTE[i];
            int cx = x + i * 20;
            boolean sel = (edit.color & 0xFFFFFF) == c;
            g.fill(cx, y, cx + 16, y + 12, sel ? 0xFFFFFFFF : 0xFF2C3640);
            g.fill(cx + 1, y + 1, cx + 15, y + 11, 0xFF000000 | c);
            int fc = c;
            click(cx, y, 16, 12, () -> edit.color = fc);
        }
        y += 18;
        // pictogramme
        y = section(g, x, y, w, Component.translatable("minimap.pioneers.edit.icon"));
        for (int i = 0; i < Icons.WAYPOINT.length; i++) {
            int ix = x + i * 20;
            boolean sel = edit.icon == i;
            boolean hover = over(mx, my, ix, y, 16, 14);
            g.fill(ix, y, ix + 16, y + 14, sel ? 0xFF2E9E78 : hover ? 0xFF2E3A46 : 0xFF1E262E);
            Icons.draw(g, Icons.waypoint(i), ix + 8, y + 7, edit.color, 1F);
            int fi = i;
            click(ix, y, 16, 14, () -> edit.icon = fi);
            tooltip(mx, my, ix, y, 16, 14, Component.translatable("minimap.pioneers.icon." + Icons.WAYPOINT[i]));
        }
        y += 20;
        toggle(g, mx, my, x, y, w, Component.translatable("minimap.pioneers.edit.visible"), edit.visible, () -> edit.visible = !edit.visible);
        y += 14;
        //1.0.2 : rendu dans le monde : Par defaut (reglage de la minimap) / Balise / Point / Invisible
        GuiDraw.text(g, font, Component.translatable("minimap.pioneers.edit.style"), x, y + 3, 0.7F, D_DIM, 64);
        String[] keys = {"default", "beacon", "point", "hidden"};
        int cw = (w - 66) / keys.length;
        for (int i = 0; i < keys.length; i++) {
            int fi = i, cx = x + 66 + i * cw;
            chip(g, mx, my, cx, y, cw - 2, 12, Component.translatable("minimap.pioneers.style." + keys[i]), edit.style == i, 0x3FD0A0, () -> {
                edit.style = fi;
                edit.beacon = fi != 3;
            });
            Component tip = i == 0
                  ? Component.translatable("minimap.pioneers.style.default.tip",
                        Component.translatable("minimap.pioneers.style." + MinimapConfig.beaconStyle().name().toLowerCase(Locale.ROOT)))
                  : Component.translatable("minimap.pioneers.style." + keys[i] + ".tip");
            tooltip(mx, my, cx, y, cw - 2, 12, tip);
        }
        y += 16;
        if (minecraft.player != null) {
            double d = edit.distanceTo(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
            GuiDraw.text(g, font, Component.translatable("minimap.pioneers.edit.distance", (int) Math.round(d), edit.dim), x, y, 0.7F, D_DIM, w);
        }
        // boutons du bas : supprimer, teleporter (a gauche) ; annuler, enregistrer (a droite)
        int by = top + panelHeight - 20;
        primary(g, mx, my, x + 184, by, 68, 14, Component.translatable("minimap.pioneers.edit.save"), !edit.name.isBlank(), this::save);
        button(g, mx, my, x + 124, by, 56, 14, Component.translatable("gui.cancel"), true, this::onClose);
        if (original != null) {
            button(g, mx, my, x, by, 56, 14, Component.translatable(confirmDelete ? "minimap.pioneers.edit.confirm_delete" : "minimap.pioneers.edit.delete"),
                  true, () -> {
                      if (confirmDelete) {
                          Waypoints.remove(original);
                          onClose();
                      } else {
                          confirmDelete = true;
                      }
                  });
        }
        if (canTeleport()) {
            //1.0.4-beta (lot 30) : grise en survie et en aventure, avec l'explication
            boolean mode = teleportMode(minecraft);
            button(g, mx, my, x + 60, by, 60, 14, Component.translatable("minimap.pioneers.edit.teleport"), mode, this::teleport);
            tooltip(mx, my, x + 60, by, 60, 14, Component.translatable(mode ? "minimap.pioneers.edit.teleport_help" : "minimap.pioneers.tp.survival"));
        }
    }

    /**
     * Bouton de teleportation montre : administrateur (operateur de niveau 2, 1.0.4-beta lot 29) ; actif seulement en creatif ou en
     * spectateur ({@link #teleportMode}, lot 30 : jamais en survie ni en aventure ; le serveur refuse aussi).
     */
    static boolean canTeleport(Minecraft mc) {
        return mc != null && mc.player != null && mc.player.hasPermissions(2);
    }

    /** 1.0.4-beta (lot 30) : mode de jeu qui permet la teleportation (creatif, spectateur ; meme regle que {@code WaypointTeleport.modeAllows}). */
    static boolean teleportMode(Minecraft mc) {
        return mc != null && mc.player != null && (mc.player.isCreative() || mc.player.isSpectator());
    }

    private boolean canTeleport() {
        return canTeleport(minecraft);
    }

    private void teleport() {
        teleportTo(minecraft, edit);
    }

    /** Demande au serveur la teleportation d'un administrateur a ce waypoint (le serveur verifie le droit, le mode et choisit une place sure). */
    static void teleportTo(Minecraft mc, Waypoint wp) {
        if (!canTeleport(mc) || !teleportMode(mc)) {
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putString("dim", wp.dim);
        t.putInt("x", wp.x);
        t.putInt("y", wp.y);
        t.putInt("z", wp.z);
        t.putString("name", wp.name);
        RpNet.toServer(RpActions.WAYPOINT_TP, t);
        mc.setScreen(null);
    }

    private void save() {
        if (edit.name.isBlank()) {
            return;
        }
        edit.name = edit.name.trim();
        if (original == null) {
            Waypoints.add(edit);
        } else {
            Waypoints.replace(original, edit);
        }
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (name != null && name.isFocused() && (key == 257 || key == 335)) {
            save();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}

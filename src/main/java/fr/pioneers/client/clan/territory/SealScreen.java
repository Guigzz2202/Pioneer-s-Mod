package fr.pioneers.client.clan.territory;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.territory.TerritoryNet;
import fr.pioneers.client.clan.ClanPaper;
import fr.pioneers.client.gui.TextFit;
import fr.pioneers.client.rp.ParchmentScreen;
import fr.pioneers.core.profile.RpNet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.9 (MAJ RP, bloc 4) : fiche du Sceau de fonction, sur la page de parchemin du Carnet. Les sept fonctions, chacune avec son embleme, son
 * nom, ce qu'elle donne, le niveau requis et le nombre deja designe ; une fonction fermee (niveau, maximum) est grisee et dit pourquoi. Sur
 * un bloc deja designe : sa fonction et le bouton pour lever le Sceau. Le serveur revoit tout.
 * <p>
 * 1.9.1 (retours de Guillaume : textes qui se superposaient) : l'etat (niveau requis, nombre) est mesure d'abord et le nom et l'effet
 * prennent la place qui reste ; tout texte passe par {@link TextFit} (resserre, puis coupe avec l'infobulle). L'infobulle d'une ligne donne
 * le nom, l'effet entier et la description.
 */
public class SealScreen extends ParchmentScreen {

    private static final int ROW = 27;
    private final long pos;
    private final String current;
    private final int level;
    private final ListTag options;

    public SealScreen(CompoundTag t) {
        super(Component.translatable("clan.pioneers.seal.title"));
        this.pos = t.getLong("pos");
        this.current = t.getString("current");
        this.level = t.getInt("level");
        this.options = t.getList("options", Tag.TAG_COMPOUND);
    }

    public static ResourceLocation emblem(String fn) {
        return Pioneers.rl("textures/gui/clan/seal_" + fn + ".png");
    }

    @Override
    protected void drawContents(GuiGraphics g, int mx, int my, float pt) {
        TextFit.Tips tips = ClanPaper.tips(this, mx, my);
        TextFit.center(g, font, title, 20, 12, PAGE_W - 40, INK, 1F, tips);
        if (!current.isEmpty()) {
            icon(g, emblem(current), PAGE_W / 2 - 16, 34, 32);
            TextFit.center(g, font, Component.translatable("clan.pioneers.seal.current", Component.translatable("clan.pioneers.function." + current)),
                  20, 74, PAGE_W - 40, TEXT, 1F, tips);
            //1.9.1 : description repliee au-dessus du bouton (au plus 8 lignes)
            TextFit.wrap(g, font, Component.translatable("clan.pioneers.function." + current + ".desc"), 60, 90, PAGE_W - 120, DIM, 1F, 8, tips,
                  true);
            ClanPaper.button(this, font, g, mx, my, PAGE_W / 2 - 60, 180, 120, 18, Component.translatable("clan.pioneers.seal.lift"), true, () -> {
                CompoundTag t = new CompoundTag();
                t.putLong("pos", pos);
                RpNet.toServer(TerritoryNet.SEAL_LIFT, t);
                onClose();
            });
            return;
        }
        TextFit.fit(g, font, Component.translatable("clan.pioneers.seal.pick", level), 20, 24, PAGE_W - 40, DIM, 0.75F, tips);
        int y = 34;
        for (int i = 0; i < options.size(); i++) {
            CompoundTag o = options.getCompound(i);
            String id = o.getString("id");
            int need = o.getInt("level"), max = o.getInt("max"), count = o.getInt("count");
            boolean open = level >= need && count < max;
            int x = 18, w = PAGE_W - 36;
            boolean hover = open && over(mx, my, x, y, w, ROW - 2);
            g.fill(x, y, x + w, y + ROW - 2, hover ? 0x30243660 : open ? 0x10243660 : 0x08000000);
            frame(g, x, y, w, ROW - 2, open ? INK_LIGHT : FAINT);
            icon(g, emblem(id), x + 3, y + 2, 21);
            //1.9.1 : l'etat d'abord (a droite, 90 px au plus), le nom et l'effet dans la place qui reste
            Component state = level < need ? Component.translatable("clan.pioneers.seal.locked", need)
                  : Component.translatable("clan.pioneers.seal.count", count, max);
            int sw = TextFit.right(g, font, state, x + w - 96, y + 8, 90, level < need ? MINUS : count >= max ? FAINT : PLUS, 1F, tips);
            int tw = w - 30 - 6 - sw - 8;
            Component fname = Component.translatable("clan.pioneers.function." + id), effect = Component.translatable(
                  "clan.pioneers.function." + id + ".effect");
            TextFit.fit(g, font, fname, x + 30, y + 3, tw, open ? INK : FAINT, 1F, tips);
            TextFit.fit(g, font, effect, x + 30, y + 14, tw, open ? DIM : FAINT, 0.7F, tips);
            List<Component> tip = new ArrayList<>();
            tip.add(fname);
            tip.addAll(TextFit.tipLines(font, effect));
            tip.addAll(TextFit.tipLines(font, Component.translatable("clan.pioneers.function." + id + ".desc")));
            tooltip(mx, my, x, y, w, ROW - 2, tip);
            if (open) {
                click(x, y, w, ROW - 2, () -> {
                    CompoundTag t = new CompoundTag();
                    t.putLong("pos", pos);
                    t.putString("fn", id);
                    RpNet.toServer(TerritoryNet.SEAL_SET, t);
                    onClose();
                });
            }
            y += ROW;
        }
    }
}

package fr.pioneers.client.rp;

import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.PioneerProfile;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import fr.pioneers.economy.LedgerLabels;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * 1.9 (MAJ RP, bloc 3) : onglet Bourse du Carnet (visible si l'economie est active) : solde en grand, vingt dernieres operations, virement
 * (destinataire et montant, verification par le serveur puis confirmation). Le serveur fait tout : l'onglet n'envoie que des demandes.
 * <p>
 * 1.0.5-beta (lot 16, retours de Guillaume : « Carnet plus lisible, moins verbeux, icones explicites, selecteur de joueur ») : solde en tuile
 * (icone, libelle court, montant ; la phrase d'explication passe en infobulle), virement en trois rangees a icones, destinataire choisi au
 * selecteur de joueur (joueurs CONNECTES : le serveur refuse un absent). Maquette : {@code T:rp/pioneer_ui_preview.py notebook_market}.
 */
final class LedgerTab implements NotebookScreen.Tab {

    private static final int PLUS = 0xFF2E7D32, MINUS = 0xFFB23A2E;
    @Nullable
    private static EditBox to, amount;
    /** 1.0.5-beta (lot 16) : tuile du solde, rangees du virement (coordonnees de la page). */
    public static final int L_X = 18, BAL_Y = 28, BAL_H = 34, SEND_Y = 72, TO_Y = 86, AMOUNT_Y = 108, RESULT_Y = 130, BTN_X = 96;
    /** Reponse du serveur a la verification (resultat, destinataire, montant) ou au virement. */
    @Nullable
    private static CompoundTag answer;

    @Override
    public String id() {
        return "market";
    }

    @Override
    public boolean available() {
        return PioneerApi.hasFeature("economy");
    }

    @Override
    public void opened(NotebookScreen s) {
        answer = null;
        to = s.tabWidget(new fr.pioneers.client.gui.PlayerPicker(s.font(), L_X + 14, TO_Y, LEFT_W - 14, 14,
              Component.translatable("economy.pioneers.ledger.to"), "pay", fr.pioneers.client.gui.PlayerPicker.Style.PAPER));
        amount = s.tabWidget(new EditBox(s.font(), L_X + 14, AMOUNT_Y, 70, 14, Component.translatable("economy.pioneers.ledger.amount")));
        amount.setMaxLength(10);
        amount.setFilter(x -> x.chars().allMatch(Character::isDigit));
        amount.setHint(Component.translatable("economy.pioneers.ledger.amount"));
    }

    /** Reponse BALANCE du serveur. */
    static void onServer(CompoundTag t) {
        answer = t;
        if (t.contains("done") && "OK".equals(t.getString("done"))) {
            RpClient.play(RpSounds.QUILL.get(), 1.0F);
            if (amount != null) {
                amount.setValue("");
            }
        }
    }

    @Override
    public void draw(NotebookScreen s, GuiGraphics g, int mx, int my, float pt) {
        PioneerProfile p = RpClient.profile();
        //solde : tuile (icone, libelle, montant) ; d'ou viennent les Shining : en infobulle
        g.fill(L_X, BAL_Y, L_X + LEFT_W, BAL_Y + BAL_H, 0x10243660);
        s.frame(g, L_X, BAL_Y, LEFT_W, BAL_H, ParchmentScreen.FAINT);
        s.icon(g, RpGui.stat("shining"), L_X + 5, BAL_Y + 9, 16);
        s.fit(g, Component.translatable("economy.pioneers.ledger.balance"), L_X + 26, BAL_Y + 5, LEFT_W - 30, ParchmentScreen.DIM, 0.75F);
        s.fit(g, Component.translatable("economy.pioneers.tokens", group(p.balance)), L_X + 26, BAL_Y + 15, LEFT_W - 30, ParchmentScreen.INK, 1.5F);
        s.tooltip(mx, my, L_X, BAL_Y, LEFT_W, BAL_H, Component.translatable("economy.pioneers.ledger.hint"));
        //virement : destinataire (selecteur), montant, verification puis confirmation
        s.icon(g, RpGui.stat("letter"), L_X, SEND_Y - 1, 10);
        s.fit(g, Component.translatable("economy.pioneers.ledger.send"), L_X + 14, SEND_Y, LEFT_W - 14, ParchmentScreen.INK);
        s.icon(g, RpGui.stat("player"), L_X, TO_Y + 3, 10);
        s.tooltip(mx, my, L_X, TO_Y + 3, 10, 10, Component.translatable("economy.pioneers.ledger.to.tip"));
        s.icon(g, RpGui.stat("shining"), L_X, AMOUNT_Y + 3, 10);
        long n = parse(amount == null ? "" : amount.getValue());
        String name = to == null ? "" : to.getValue().trim();
        boolean ready = n > 0 && !name.isEmpty();
        if (answer != null && answer.contains("check") && "OK".equals(answer.getString("check"))) {
            Component q = Component.translatable("economy.pioneers.ledger.confirm", group(answer.getLong("amount")), answer.getString("to"));
            s.wrap(g, q, L_X, RESULT_Y, LEFT_W, ParchmentScreen.INK, 1F, 4);
            s.button(g, mx, my, BTN_X, AMOUNT_Y - 1, 68, 16, Component.translatable("economy.pioneers.ledger.confirm_button"), true, () -> {
                CompoundTag t = new CompoundTag();
                t.putString("to", name);
                t.putLong("amount", answer.getLong("amount"));
                t.putBoolean("confirm", true);
                RpNet.toServer(RpActions.PAY, t);
                answer = null;
            });
        } else {
            s.button(g, mx, my, BTN_X, AMOUNT_Y - 1, 68, 16, Component.translatable("economy.pioneers.ledger.send_button"), ready, () -> {
                CompoundTag t = new CompoundTag();
                t.putString("to", name);
                t.putLong("amount", n);
                RpNet.toServer(RpActions.PAY, t);
            });
            if (answer != null) {
                String r = answer.contains("check") ? answer.getString("check") : answer.getString("done");
                boolean ok = "OK".equals(r);
                s.wrap(g, Component.translatable(ok ? "economy.pioneers.ledger.sent" : "economy.pioneers.result." + r.toLowerCase(Locale.ROOT)), L_X,
                      RESULT_Y, LEFT_W, ok ? PLUS : MINUS, 1F, 4);
            }
        }
        //operations
        int x0 = 182, y0 = 30, w = PARCH_W();
        s.icon(g, RpGui.stat("history"), x0, y0 - 1, 10);
        s.fit(g, Component.translatable("economy.pioneers.ledger.last"), x0 + 14, y0, w - 14, ParchmentScreen.INK);
        List<CompoundTag> lines = new ArrayList<>(p.ledger);
        //1.9.1 (audit des ecrans du bloc 2) : colonne des jours a la largeur du plus long jour affiche (44 au plus), libelle replie sur deux
        //lignes au plus, points a droite ; la liste defile a la molette quand elle depasse la page
        List<CompoundTag> shown = new ArrayList<>();
        for (int i = lines.size() - 1; i >= 0 && shown.size() < 20; i--) {
            shown.add(lines.get(i));
        }
        int dw = 0;
        for (CompoundTag t : shown) {
            dw = Math.max(dw, s.fitWidth(Component.translatable("pioneer.pioneers.day_short", t.getLong("d")), 44, 0.75F));
        }
        int top = y0 + 12, h = ParchmentScreen.PAGE_H - 10 - top;
        int textX = x0 + dw + 4;
        int content = 0;
        for (CompoundTag t : shown) {
            content += rowHeight(s, what(t), x0 + w - 4 - s.fitWidth(amount(t), AMOUNT_W, 0.75F) - textX);
        }
        s.scrollArea(LIST_SCROLL, x0, top, w, h, content);
        s.beginClip(g, x0, top, w, h);
        int y = top - s.scrollOf(LIST_SCROLL);
        for (CompoundTag t : shown) {
            Component what = what(t);
            int tw = x0 + w - 4 - s.fitWidth(amount(t), AMOUNT_W, 0.75F) - textX;
            int rh = rowHeight(s, what, tw);
            if (y + rh > top && y < top + h) {
                s.fit(g, Component.translatable("pioneer.pioneers.day_short", t.getLong("d")), x0, y, dw, ParchmentScreen.DIM, 0.75F);
                s.fitRight(g, amount(t), x0 + w, y, AMOUNT_W, t.getLong("a") >= 0 ? PLUS : MINUS, 0.75F);
                s.wrap(g, what, textX, y, tw, ParchmentScreen.TEXT, 0.75F, 2);
            }
            y += rh;
        }
        s.endClip(g);
        if (lines.isEmpty()) {
            s.wrap(g, Component.translatable("economy.pioneers.ledger.empty"), x0, top, w, ParchmentScreen.DIM, 0.75F, 3);
        }
    }

    /** 1.9.1 : largeur maximale de la colonne des points, indice de defilement de la liste. */
    private static final int AMOUNT_W = 60, LIST_SCROLL = 1;

    private static Component amount(CompoundTag t) {
        long a = t.getLong("a");
        return Component.literal((a >= 0 ? "+ " : "− ") + group(Math.abs(a)));
    }

    /**
     * Libelle d'une operation : sorte, joueur, objet. 1.0.3-beta (lot 13) : texte lisible par {@link LedgerLabels} (phrase par sorte, nom des
     * PNJ et anciennes cles traduits, partie omise quand c'est le joueur lui-meme), plus jamais une cle de langue brute.
     */
    private static Component what(CompoundTag t) {
        PioneerProfile prof = RpClient.profile();
        net.minecraft.client.player.LocalPlayer me = net.minecraft.client.Minecraft.getInstance().player;
        String self = prof.name != null && !prof.name.isBlank() ? prof.name : me == null ? "" : me.getGameProfile().getName();
        Component what = LedgerLabels.label(t.getString("k"), t.getString("p"), I18n::exists, self);
        if (!t.getString("i").isEmpty()) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(t.getString("i")));
            if (item != Items.AIR) {
                what = Component.translatable("economy.pioneers.ledger.item", what, item.getDescription(), t.getInt("n"));
            }
        }
        return what;
    }

    /** Hauteur d'une ligne : 9, ou 17 si le libelle (echelle 0,75) passe sur deux lignes. */
    private static int rowHeight(NotebookScreen s, Component c, int w) {
        return s.lines(c, w, 0.75F).size() > 1 ? 17 : 9;
    }

    private static int PARCH_W() {
        return 186;
    }

    /** 1.9.1 : largeur de la colonne de gauche (solde, virement). */
    private static final int LEFT_W = 146;

    static long parse(String s) {
        try {
            return s.isEmpty() ? 0 : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 12 345 678 (espaces fines entre les milliers). */
    static String group(long v) {
        String s = Long.toString(v);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) {
                b.append(' ');
            }
            b.append(s.charAt(i));
        }
        return b.toString();
    }
}

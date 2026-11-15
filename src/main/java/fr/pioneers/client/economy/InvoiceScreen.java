package fr.pioneers.client.economy;

import com.mojang.math.Axis;
import fr.pioneers.client.rp.ParchmentScreen;
import java.time.DateTimeException;
import java.time.ZoneId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * 1.0.4-beta (lot 7) : lecture d'une facture imprimee ({@code economy/contract/InvoiceItem}, clic droit) : en-tete « Facture n° », date
 * d'impression, prestataire et client, objet, ligne de la periode (debut -> fin, duree facturee, taux, montant), totaux (facture, paye, reste
 * du, prevu au contrat), tampon « Payee » / « Impayee » / « En cours », fuseau du serveur. Rien n'est demande au serveur : c'est un
 * instantane. Maquette : {@code tools/gc/economy/contracts_ui_preview.py} (planche de la facture, memes constantes).
 */
public class InvoiceScreen extends ParchmentScreen {

    public static final int MARGIN = 20, RIGHT = PAGE_W - 20, PARTY_Y = 50, OBJECT_Y = 80, TABLE_Y = 106, ROW_Y = 122, TOTAL_X = 222, TOTAL_Y = 144,
          TOTAL_STEP = 12, STAMP_X = 90, STAMP_Y = 168, FOOT_Y = 222;
    /** Colonnes de la ligne de la periode : periode, duree, taux, montant (aligne a droite). */
    public static final int COL_PERIOD = 20, COL_HOURS = 214, COL_RATE = 268, PERIOD_W = 188, HOURS_W = 52, RATE_W = 56, AMOUNT_W = 40;
    private final CompoundTag t;

    @javax.annotation.Nullable
    private final net.minecraft.client.gui.screens.Screen parent;

    public InvoiceScreen(CompoundTag t) {
        this(t, null);
    }

    /** 1.0.5-beta (lot 16) : lue depuis le bac a lettres : Echap y ramene. */
    public InvoiceScreen(CompoundTag t, @javax.annotation.Nullable net.minecraft.client.gui.screens.Screen parent) {
        super(Component.translatable("item.pioneers.contract_invoice"));
        this.t = t;
        this.parent = parent;
    }

    @Override
    public void onClose() {
        if (parent != null) {
            Minecraft.getInstance().setScreen(parent);
        } else {
            super.onClose();
        }
    }

    public static void open(CompoundTag t) {
        Minecraft.getInstance().setScreen(new InvoiceScreen(t));
    }

    /** 1.0.5-beta (lot 16) : lignes de la lettre sous l'objet. */
    public static final int LETTER_LINES = 1;

    private ZoneId zone() {
        try {
            return ZoneId.of(t.getString("zone"));
        } catch (DateTimeException e) {
            return ZoneId.systemDefault();
        }
    }

    /** Tampon : payee (rien du, periode finie), impayee (reste du apres une tentative), en cours (sinon). */
    public static String stamp(CompoundTag t) {
        String st = t.getString("state");
        boolean closed = "FINISHED".equals(st) || "TERMINATED".equals(st);
        if (t.getLong("owed") > 0 && t.getBoolean("short")) {
            return "unpaid";
        }
        if (closed && t.getLong("owed") <= 0) {
            return "paid";
        }
        return "open";
    }

    @Override
    protected void drawContents(GuiGraphics g, int mx, int my, float pt) {
        ZoneId z = zone();
        fit(g, Component.translatable("contract.pioneers.invoice.title"), MARGIN, 16, 180, INK, 2F);
        fitRight(g, Component.translatable("contract.pioneers.number", t.getInt("id")), RIGHT, 16, 120, INK, 1.5F);
        fitRight(g, Component.translatable("contract.pioneers.invoice.printed", ContractsTab.date(t.getLong("printed"), z, false)), RIGHT, 32, 200,
              DIM, 0.75F);
        separator(g, MARGIN, 42, RIGHT - MARGIN);
        //parties
        fit(g, Component.translatable("contract.pioneers.label.provider"), MARGIN, PARTY_Y, 160, DIM, 0.75F);
        fit(g, Component.literal(t.getString("pvn")), MARGIN, PARTY_Y + 9, 160, TEXT);
        fit(g, Component.translatable("contract.pioneers.label.client"), 200, PARTY_Y, 160, DIM, 0.75F);
        fit(g, Component.literal(t.getString("cln")), 200, PARTY_Y + 9, 160, TEXT);
        //objet
        fit(g, Component.translatable("contract.pioneers.invoice.object", t.getString("title")), MARGIN, OBJECT_Y, RIGHT - MARGIN, INK);
        //1.0.5-beta (lot 16) : debut de la lettre du contrat (en entier au survol) ; un contrat d'avant : sa description
        String letter = t.getString("body").isEmpty() ? t.getString("note") : t.getString("body");
        if (!letter.isEmpty()) {
            wrap(g, Component.literal(letter), MARGIN, OBJECT_Y + 11, RIGHT - MARGIN, DIM, 0.75F, LETTER_LINES);
        }
        //tableau
        g.fill(MARGIN, TABLE_Y, RIGHT, TABLE_Y + 12, 0x22243660);
        fit(g, Component.translatable("contract.pioneers.invoice.col.period"), COL_PERIOD + 3, TABLE_Y + 3, PERIOD_W, INK, 0.75F);
        fit(g, Component.translatable("contract.pioneers.invoice.col.hours"), COL_HOURS, TABLE_Y + 3, HOURS_W, INK, 0.75F);
        fit(g, Component.translatable("contract.pioneers.invoice.col.rate"), COL_RATE, TABLE_Y + 3, RATE_W, INK, 0.75F);
        fitRight(g, Component.translatable("contract.pioneers.invoice.col.amount"), RIGHT - 3, TABLE_Y + 3, AMOUNT_W, INK, 0.75F);
        //periode FACTUREE : du debut effectif au bout des minutes facturees (heures entieres en cours de route, a la minute a la fin)
        long from = t.getLong("eff"), to = from + t.getLong("bm") * 60_000L;
        fit(g, Component.translatable("contract.pioneers.invoice.period", ContractsTab.date(from, z, false), ContractsTab.date(to, z, false)),
              COL_PERIOD + 3, ROW_Y, PERIOD_W, TEXT, 0.75F);
        fit(g, ContractsTab.duration(t.getLong("bm")), COL_HOURS, ROW_Y, HOURS_W, TEXT, 0.75F);
        //1.0.4-beta (lot 29) : contrat au montant total : « Forfait » a la place du taux (un contrat d'avant le lot 29 garde son taux)
        Component rate = !t.getBoolean("hourly") && t.getLong("total") > 0 ? Component.translatable("contract.pioneers.invoice.flat")
              : Component.translatable("contract.pioneers.rate_value", ContractsTab.group(t.getLong("rate")));
        fit(g, rate, COL_RATE, ROW_Y, RATE_W, TEXT, 0.75F);
        fitRight(g, Component.literal(ContractsTab.group(t.getLong("billed"))), RIGHT - 3, ROW_Y, AMOUNT_W, TEXT, 0.75F);
        separator(g, MARGIN, ROW_Y + 12, RIGHT - MARGIN);
        //totaux
        int y = TOTAL_Y;
        y = total(g, "contract.pioneers.label.billed", t.getLong("billed"), y, TEXT, 1F);
        y = total(g, "contract.pioneers.label.paid", t.getLong("paid"), y, PLUS, 1F);
        separator(g, TOTAL_X, y - 2, RIGHT - TOTAL_X);
        y = total(g, "contract.pioneers.label.owed", t.getLong("owed"), y + 1, t.getLong("owed") > 0 ? MINUS : INK, 1F);
        total(g, "contract.pioneers.label.planned", t.getLong("planned"), y + 4, DIM, 0.75F);
        //tampon (le sens est aussi dans le mot)
        String st = stamp(t);
        int color = "paid".equals(st) ? PLUS : "unpaid".equals(st) ? MINUS : INK_LIGHT;
        g.pose().pushPose();
        g.pose().translate(STAMP_X, STAMP_Y, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-10));
        Component label = Component.translatable("contract.pioneers.invoice.stamp." + st);
        int w = Math.min(120, fitWidth(label, 110, 1.5F)) + 12;
        frame(g, -w / 2, -10, w, 22, color);
        frame(g, -w / 2 + 2, -8, w - 4, 18, color);
        fitCentered(g, label, 0, -4, 110, color, 1.5F);
        g.pose().popPose();
        //pied
        fit(g, Component.translatable("contract.pioneers.invoice.foot", t.getString("zone")), MARGIN, FOOT_Y, RIGHT - MARGIN, DIM, 0.75F);
    }

    private int total(GuiGraphics g, String label, long v, int y, int color, float k) {
        fit(g, Component.translatable(label), TOTAL_X, y, 70, DIM, k);
        fitRight(g, ContractsTab.tokens(v), RIGHT - 3, y, RIGHT - 3 - TOTAL_X - 72, color, k);
        return y + TOTAL_STEP;
    }
}

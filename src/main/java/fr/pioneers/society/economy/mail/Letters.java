package fr.pioneers.society.economy.mail;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 1.0.4-beta (lot 29) : les LETTRES (objet {@link LetterItem}, NBT {@code letter}) : sorte ({@link MailRules#KINDS}), expediteur (nom de
 * pionnier), numero et intitule du contrat (0 et vide pour un virement), formule du contrat ({@code total} / {@code hourly}), montant(s),
 * date d'envoi (ms, fuseau du serveur, et la meme date deja ecrite au format neutre pour l'infobulle). Les textes sont traduits chez le
 * lecteur : {@code mail.pioneers.kind.<sorte>} (objet), {@code mail.pioneers.body.<sorte>} (texte, arguments de {@link #bodyArgs}).
 */
public final class Letters {

    public static final String TAG = "letter";
    private static final DateTimeFormatter NEUTRAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Letters() {
    }

    /** Une lettre ; {@code a} et {@code b} : montants (sens selon la sorte, 0 = sans). */
    public static ItemStack make(String kind, String from, int number, String title, String mode, long a, long b, long now, ZoneId zone) {
        ItemStack s = new ItemStack(PiMail.LETTER.get());
        CompoundTag t = new CompoundTag();
        t.putString("k", kind);
        t.putString("f", from == null ? "" : from);
        t.putInt("n", number);
        t.putString("t", title == null ? "" : title);
        t.putString("m", mode == null ? "" : mode);
        t.putLong("a", a);
        t.putLong("b", b);
        t.putLong("d", now);
        t.putString("z", zone.getId());
        t.putString("ds", NEUTRAL.format(Instant.ofEpochMilli(now).atZone(zone)));
        s.getOrCreateTag().put(TAG, t);
        return s;
    }

    /** Virement recu (bouton Envoyer de la Bourse, {@code /pay}). */
    public static ItemStack transfer(String from, long amount, long now, ZoneId zone) {
        return make("transfer", from, 0, "", "", amount, 0, now, zone);
    }

    /**
     * 1.0.5-beta (lot 16) : corps d'une lettre de contrat ({@code w}, deja borne par {@code ContractRules.cleanBody}), periode ({@code s},
     * {@code e}) et signature ({@code sg}). Sans corps : rien.
     */
    public static void attachBody(ItemStack letter, String body, long start, long end, String signature) {
        CompoundTag t = data(letter);
        if (t == null || body == null || body.isEmpty()) {
            return;
        }
        t.putString("w", body);
        t.putLong("s", start);
        t.putLong("e", end);
        t.putString("sg", signature == null ? "" : signature);
    }

    /** 1.0.5-beta (lot 16) : lettre deja lue (marque {@code r}, posee a la lecture : clic droit ou bac a lettres). */
    public static boolean read(ItemStack stack) {
        CompoundTag t = data(stack);
        return t != null && t.getBoolean("r");
    }

    public static void markRead(ItemStack stack) {
        CompoundTag t = data(stack);
        if (t != null) {
            t.putBoolean("r", true);
        }
    }

    @Nullable
    public static CompoundTag data(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null && t.contains(TAG) ? t.getCompound(TAG) : null;
    }

    /** Chiffres groupes par trois (« 1 250 ») : meme ecriture que l'onglet Contrats ({@code ContractsTab.group}). */
    public static String group(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder(v < 0 ? "-" : "");
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) {
                b.append(' ');
            }
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    /** Montant en Shining (texte « 1 250 Shining » de la langue). */
    public static Component tokens(long v) {
        return Component.translatable("economy.pioneers.tokens", group(v));
    }

    /** Termes d'un contrat : « 480 Shining au total » ou « 10 Shining de l'heure ». */
    public static Component terms(String mode, long amount) {
        return Component.translatable("hourly".equals(mode) ? "contract.pioneers.terms.hourly" : "contract.pioneers.terms.total",
              group(amount));
    }

    /** Arguments du texte d'une lettre, dans l'ordre des {@code %s} de {@code mail.pioneers.body.<sorte>}. */
    public static Object[] bodyArgs(CompoundTag t) {
        String f = t.getString("f"), n = Integer.toString(t.getInt("n")), title = t.getString("t");
        long a = t.getLong("a"), b = t.getLong("b");
        return switch (t.getString("k")) {
            case "transfer" -> new Object[]{f, tokens(a)};
            case "contract_proposed" -> new Object[]{f, n, title, terms(t.getString("m"), a)};
            case "contract_withdrawn", "contract_accepted", "contract_refused" -> new Object[]{f, n, title};
            case "contract_expired", "contract_resumed" -> new Object[]{n, title};
            case "contract_suspended", "contract_unpaid" -> new Object[]{n, title, tokens(a)};
            case "contract_terminated" -> new Object[]{f, n, title, tokens(a), tokens(b)};
            case "contract_finished" -> new Object[]{n, title, tokens(a), tokens(b)};
            case "contract_paid" -> new Object[]{f, n, title, tokens(a)};
            //1.0.5-beta (lot 26) : documents du Carnet (« m » = type du document)
            case "document_received", "document_accepted", "document_refused" -> new Object[]{f, docType(t), n, title};
            case "document_letter" -> new Object[]{f, title};
            case "document_cancelled" -> new Object[]{f, docType(t), n, title, tokens(a)};
            case "document_settled", "document_overdue" -> new Object[]{docType(t), n, title, tokens(a)};
            default -> new Object[0];
        };
    }

    /** 1.0.5-beta (lot 26) : nom du type d'un document ({@code document.pioneers.type.<m>}). */
    public static Component docType(CompoundTag t) {
        return Component.translatable("document.pioneers.type." + t.getString("m"));
    }

    /** Sorte connue (sinon la lettre se lit comme une lettre vide). */
    public static boolean known(String kind) {
        return MailRules.KINDS.contains(kind);
    }
}

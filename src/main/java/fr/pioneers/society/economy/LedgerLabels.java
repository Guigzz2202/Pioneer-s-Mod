package fr.pioneers.society.economy;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * 1.0.3-beta (lot 13, Guillaume : « dans la bourse, quand je recois un paiement, le libelle prend une reference et non un vrai libelle, et les
 * dons au clan aussi ») : libelle LISIBLE d'une operation du compte ({@code PioneerProfile.ledger}, cles {@code k} sorte, {@code p} partie,
 * {@code i} objet, {@code n} nombre). Logique pure, commune au Carnet (client : {@code I18n::exists}) et a la commande {@code /pioneer ledger}
 * (serveur : {@link #KINDS}).
 * <p>Causes trouvees : sortes sans texte ({@code clan_donation}, {@code clan_payment}, {@code clan_dues}, {@code clan_charter},
 * {@code chunkload}, services de village) : le Carnet affichait la cle brute ; nom d'un PNJ sans nom propre ecrit cote serveur par
 * {@code Component.getString()} : sur un serveur dedie, la cle de langue ({@code entity.pioneers.peddler}...). Desormais : {@code .party}
 * = phrase par sorte (« Virement de %s », « Don au clan %s »), partie « #cle » ou JSON de composant traduite chez le joueur, ancienne partie
 * qui ressemble a une cle connue traduite aussi (anciens enregistrements), sorte inconnue en clair, partie egale au joueur lui-meme omise.
 */
public final class LedgerLabels {

    /** Sortes d'operation qui ont leur texte ({@code economy.pioneers.kind.<sorte>}, et {@code .party} quand une partie les suit). */
    public static final List<String> KINDS = List.of("buy", "sell", "pay_out", "pay_in", "stall_buy", "stall_sale", "stall_offline", "stall_refund",
          "sale_bin", "reconversion", "restoration", "restoration_cancel", "worker", "repair", "sharpen", "op", "amends", "emissary",
          "clan_donation", "clan_payment", "clan_dues", "clan_charter", "chunkload", "water", "energy", "workshop", "storage", "archives",
          "gametest", "contract_out", "contract_in", "document_out", "document_in");
    /** Sortes dont le texte prend la partie en argument ({@code economy.pioneers.kind.<sorte>.party}). */
    public static final List<String> PARTY = List.of("buy", "sell", "pay_out", "pay_in", "stall_buy", "stall_sale", "stall_refund", "repair",
          "sharpen", "op", "amends", "emissary", "clan_donation", "clan_payment", "clan_dues", "clan_charter", "water", "energy", "workshop",
          "storage", "archives", "contract_out", "contract_in", "document_out", "document_in");
    public static final String PREFIX = "economy.pioneers.kind.";
    private static final Pattern KEY = Pattern.compile("[a-z0-9_]+(\\.[a-z0-9_]+){2,}");

    private LedgerLabels() {
    }

    /** Cles connues du serveur (il n'a pas les fichiers de langue du mod). */
    public static boolean known(String key) {
        if (!key.startsWith(PREFIX)) {
            return false;
        }
        String rest = key.substring(PREFIX.length());
        return rest.endsWith(".party") ? PARTY.contains(rest.substring(0, rest.length() - 6)) : KINDS.contains(rest);
    }

    /**
     * Libelle d'une operation : sorte (avec sa partie), puis l'objet.
     *
     * @param has  vrai si une cle de langue existe chez le lecteur
     * @param self nom du joueur qui lit son compte (partie omise si c'est lui)
     */
    public static MutableComponent label(String kind, String party, Predicate<String> has, String self) {
        String k = kind == null ? "" : kind;
        String base = PREFIX + k;
        boolean text = has.test(base);
        MutableComponent what = text ? Component.translatable(base) : Component.translatable(PREFIX + "other", plain(k));
        String p = party == null ? "" : party.trim();
        if (p.isEmpty() || p.equals(self)) {
            return what;
        }
        Component who = party(p, has);
        if (text && has.test(base + ".party")) {
            return Component.translatable(base + ".party", who);
        }
        return Component.translatable("economy.pioneers.ledger.with", what, who);
    }

    /** Partie lisible : « #cle » ou JSON de composant (ecrits depuis le lot 13), ancienne cle brute connue, sinon le texte tel quel. */
    public static Component party(String p, Predicate<String> has) {
        if (p.startsWith("#")) {
            return Component.translatable(p.substring(1));
        }
        if (p.startsWith("{") || p.startsWith("[")) {
            try {
                Component c = Component.Serializer.fromJson(p);
                if (c != null) {
                    return c;
                }
            } catch (RuntimeException ignored) {
                //texte qui ressemble a du JSON : laisse tel quel
            }
        }
        if (KEY.matcher(p).matches()) {
            return has.test(p) ? Component.translatable(p) : Component.literal(plain(p.substring(p.lastIndexOf('.') + 1)));
        }
        return Component.literal(p);
    }

    /** Nom du partenaire tel qu'on l'ecrit dans le compte : texte simple, « #cle » d'un nom traduit, sinon JSON du composant. */
    public static String store(Component name) {
        if (name.getContents() instanceof TranslatableContents t && t.getArgs().length == 0 && name.getSiblings().isEmpty()) {
            return "#" + t.getKey();
        }
        if (name.getContents() instanceof LiteralContents && name.getSiblings().isEmpty()) {
            return name.getString();
        }
        boolean plain = name.getContents() instanceof LiteralContents || name.getContents() == net.minecraft.network.chat.ComponentContents.EMPTY;
        for (Component s : name.getSiblings()) {
            plain &= s.getContents() instanceof LiteralContents && s.getSiblings().isEmpty();
        }
        return plain ? name.getString() : Component.Serializer.toJson(name.copy().setStyle(net.minecraft.network.chat.Style.EMPTY));
    }

    /** « clan_dues » -> « clan dues » (sorte ou mot d'une cle inconnue). */
    static String plain(String s) {
        return s.replace('_', ' ').toLowerCase(Locale.ROOT).trim();
    }
}

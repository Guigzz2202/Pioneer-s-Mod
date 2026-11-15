package fr.pioneers.economy;

import fr.pioneers.Pioneers;
import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.PioneerProfile;
import fr.pioneers.core.profile.ProfileSync;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.core.profile.RpNet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : SEUL point d'entree de la monnaie (jetons de service, solde virtuel du profil). Quatre operations : {@link #balance},
 * {@link #credit}, {@link #debit}, {@link #transfer}, chacune avec un resultat explicite. Montants en jetons entiers (jamais negatifs, plafond
 * {@link #MAX}). Chaque operation inscrit le journal du joueur (200 lignes) et le journal global (5 000 lignes, operateurs). Un proprietaire
 * hors ligne est credite a sa prochaine connexion ({@link Pending}). Le client n'envoie jamais un montant : il demande une action.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class EconomyApi {

    public static final long MAX = 1_000_000_000L;
    public static final int GLOBAL_LINES = 5000;

    public enum Result {
        OK, INSUFFICIENT, CAP, ABSENT, DISABLED, INVALID;

        public boolean ok() {
            return this == OK;
        }
    }

    private EconomyApi() {
    }

    /** Interrupteur {@code economy} (configuration du serveur). */
    public static boolean enabled() {
        return PioneerApi.hasFeature("economy");
    }

    public static long balance(ServerPlayer player) {
        return PioneerCap.get(player).balance;
    }

    /** Ligne de journal : sorte (cle courte), partie (nom), objet (id ou ""), quantite. */
    public record Entry(String kind, String party, String item, int count) {

        public static Entry of(String kind) {
            return new Entry(kind, "", "", 0);
        }

        public static Entry of(String kind, String party) {
            return new Entry(kind, party, "", 0);
        }
    }

    /** Verifie un credit sans rien changer. */
    public static Result canCredit(ServerPlayer player, long amount) {
        if (!enabled()) {
            return Result.DISABLED;
        }
        if (amount < 0) {
            return Result.INVALID;
        }
        return PioneerCap.get(player).balance + amount > MAX ? Result.CAP : Result.OK;
    }

    /** Verifie un debit sans rien changer. */
    public static Result canDebit(ServerPlayer player, long amount) {
        if (!enabled()) {
            return Result.DISABLED;
        }
        if (amount < 0) {
            return Result.INVALID;
        }
        return PioneerCap.get(player).balance < amount ? Result.INSUFFICIENT : Result.OK;
    }

    public static Result credit(ServerPlayer player, long amount, Entry e) {
        Result r = canCredit(player, amount);
        if (r.ok() && amount > 0) {
            PioneerProfile p = PioneerCap.get(player);
            p.balance += amount;
            log(player, p, e, amount);
        } else if (r == Result.CAP) {
            player.displayClientMessage(Component.translatable("economy.pioneers.cap").withStyle(ChatFormatting.RED), false);
        }
        return r;
    }

    public static Result debit(ServerPlayer player, long amount, Entry e) {
        Result r = canDebit(player, amount);
        if (r.ok() && amount > 0) {
            PioneerProfile p = PioneerCap.get(player);
            p.balance -= amount;
            log(player, p, e, -amount);
        }
        return r;
    }

    /**
     * Virement entre deux joueurs connectes (bouton Envoyer de la Bourse, {@code /pay}) : montant positif, couvert, plafond quotidien du
     * donneur ({@code payDailyMax}), aucune commission. Tout est verifie avant toute ecriture.
     */
    public static Result transfer(ServerPlayer from, @Nullable ServerPlayer to, long amount) {
        if (!enabled()) {
            return Result.DISABLED;
        }
        if (to == null || to.hasDisconnected()) {
            return Result.ABSENT;
        }
        if (amount <= 0 || to == from) {
            return Result.INVALID;
        }
        PioneerProfile a = PioneerCap.get(from), b = PioneerCap.get(to);
        long day = PioneerApi.day(from.getServer());
        long today = a.payDay == day ? a.paidToday : 0;
        long cap = PiCommonConfig.get(RpConfig.PAY_DAILY_MAX, 50_000L);
        if (today + amount > cap) {
            return Result.CAP;
        }
        if (a.balance < amount) {
            return Result.INSUFFICIENT;
        }
        if (b.balance + amount > MAX) {
            return Result.CAP;
        }
        a.balance -= amount;
        b.balance += amount;
        a.payDay = day;
        a.paidToday = today + amount;
        log(from, a, Entry.of("pay_out", name(to)), -amount);
        log(to, b, Entry.of("pay_in", name(from)), amount);
        //1.0.4-beta (lot 29) : une lettre « Virement recu » dans la boite aux lettres du beneficiaire
        fr.pioneers.economy.mail.MailService.deliver(to.getServer(), to.getUUID(), fr.pioneers.economy.mail.Letters.transfer(name(from), amount,
              fr.pioneers.economy.contract.ContractService.now(), fr.pioneers.economy.contract.ContractService.zone()));
        return Result.OK;
    }

    /** Nom de pionnier (sinon pseudo) d'un joueur, pour les journaux et les messages. */
    public static String name(ServerPlayer player) {
        String n = PioneerCap.get(player).name;
        return n == null || n.isBlank() ? player.getGameProfile().getName() : n;
    }

    private static void log(ServerPlayer player, PioneerProfile p, Entry e, long signed) {
        CompoundTag t = new CompoundTag();
        t.putLong("d", PioneerApi.day(player.getServer()));
        t.putString("k", e.kind());
        t.putString("p", e.party());
        t.putString("i", e.item());
        t.putInt("n", e.count());
        t.putLong("a", signed);
        p.ledger.addLast(t);
        while (p.ledger.size() > PioneerProfile.LEDGER) {
            p.ledger.pollFirst();
        }
        p.touch();
        Ledger.get(player.getServer()).add(name(player), t);
        ProfileSync.full(player);
        CompoundTag pop = new CompoundTag();
        pop.putLong("a", signed);
        RpNet.send(player, RpActions.MONEY_TOAST, pop);
    }

    // ------------------------------------------------------------------ hors ligne
    /**
     * Credit d'un joueur peut-etre hors ligne (vente d'un etal) : applique tout de suite s'il est connecte, sinon mis de cote et applique a
     * sa prochaine connexion avec un message.
     */
    public static void creditLater(MinecraftServer server, UUID owner, long amount, Entry e) {
        if (amount <= 0) {
            return;
        }
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p != null && credit(p, amount, e).ok()) {
            return;
        }
        Pending.get(server).add(owner, amount);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !enabled()) {
            return;
        }
        Pending pending = Pending.get(player.getServer());
        long amount = pending.take(player.getUUID());
        if (amount > 0) {
            long room = Math.max(0, MAX - balance(player));
            long now = Math.min(room, amount);
            if (now > 0) {
                credit(player, now, Entry.of("stall_offline"));
                player.sendSystemMessage(Component.translatable("economy.pioneers.stall.offline", String.valueOf(now))
                      .withStyle(ChatFormatting.GOLD));
            }
            if (amount > now) {
                pending.add(player.getUUID(), amount - now);
            }
        }
    }

    /** Crediits en attente des proprietaires hors ligne (donnee de monde, Overworld). */
    public static final class Pending extends SavedData {

        private static final String NAME = "pioneers_pending_credits";
        private final Map<UUID, Long> credits = new HashMap<>();

        static Pending get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Pending::load, Pending::new, NAME);
        }

        void add(UUID who, long amount) {
            credits.merge(who, amount, (x, y) -> Math.min(Long.MAX_VALUE / 2, x + y));
            setDirty();
        }

        long take(UUID who) {
            Long v = credits.remove(who);
            if (v != null) {
                setDirty();
            }
            return v == null ? 0 : v;
        }

        public long peek(UUID who) {
            return credits.getOrDefault(who, 0L);
        }

        private static Pending load(CompoundTag tag) {
            Pending p = new Pending();
            for (Tag t : tag.getList("c", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                p.credits.put(c.getUUID("u"), c.getLong("a"));
            }
            return p;
        }

        @Override
        public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
            ListTag l = new ListTag();
            credits.forEach((u, a) -> {
                CompoundTag c = new CompoundTag();
                c.putUUID("u", u);
                c.putLong("a", a);
                l.add(c);
            });
            tag.put("c", l);
            return tag;
        }
    }

    /** Journal global des operations (5 000 lignes, pour les operateurs). */
    public static final class Ledger extends SavedData {

        private static final String NAME = "pioneers_ledger";
        private final Deque<CompoundTag> lines = new ArrayDeque<>();

        public static Ledger get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Ledger::load, Ledger::new, NAME);
        }

        void add(String who, CompoundTag line) {
            CompoundTag c = line.copy();
            c.putString("w", who);
            lines.addLast(c);
            while (lines.size() > GLOBAL_LINES) {
                lines.pollFirst();
            }
            setDirty();
        }

        public Deque<CompoundTag> lines() {
            return lines;
        }

        private static Ledger load(CompoundTag tag) {
            Ledger l = new Ledger();
            for (Tag t : tag.getList("l", Tag.TAG_COMPOUND)) {
                l.lines.add((CompoundTag) t);
            }
            return l;
        }

        @Override
        public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
            ListTag l = new ListTag();
            l.addAll(lines);
            tag.put("l", l);
            return tag;
        }
    }
}

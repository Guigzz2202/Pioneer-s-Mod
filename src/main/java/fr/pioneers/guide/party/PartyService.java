package fr.pioneers.guide.party;

import com.mojang.brigadier.CommandDispatcher;
import fr.pioneers.Pioneers;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.guide.JourneySteps;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.4-beta (lot 29, Guillaume : « Ajouter un joueur a la progression du guide -> un mode partie (aucun lien avec le clan) » ; reponse :
 * progression commune, invitation depuis le guide, une etape faite par un membre est validee pour tous, en entrant on fusionne les
 * progressions (union), en partant on garde ce qu'on a) : le MODE PARTIE cote serveur.
 * <ul>
 *     <li>inviter un joueur CONNECTE ({@link #invite}) ; il accepte ({@link #accept}, dans les {@link #INVITE_MINUTES} minutes) ou refuse
 *     ({@link #decline}) ; une partie se forme a la premiere acceptation, {@link #MAX_MEMBERS} membres au plus ; un joueur n'est que dans
 *     une partie ;</li>
 *     <li>a l'entree : UNION des etapes du Periple (progres {@code pioneers:journey/*}) de la partie et du nouveau venu ; chaque membre
 *     connecte recoit aussitot les etapes qui lui manquent, un membre absent les recoit a sa connexion ({@link #sync}) ;</li>
 *     <li>une etape obtenue par un membre ({@code AdvancementEarnEvent}) est ajoutee a la partie et decernee a tous ;</li>
 *     <li>en partant ({@link #leave}) on garde tout ; la partie est dissoute quand il ne reste qu'un membre ;</li>
 *     <li>RECOMPENSES d'etape : chacun recoit le progres de l'etape, donc sa recompense eventuelle, UNE fois (le jeu ne decerne jamais deux
 *     fois un progres) ; aujourd'hui les etapes du Periple n'en ont pas (ni bulle ni message : le HUD dit « Etape faite »).</li>
 * </ul>
 * Reseau : demande {@link RpActions#PARTY_REQUEST} (op view / invite / accept / decline / leave / sync ; le serveur ne lit que op et name),
 * vue {@link RpActions#PARTY_VIEW} (aussi poussee aux membres connectes apres un changement). Commandes de secours : {@code /party}.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class PartyService {

    public static final int MAX_MEMBERS = 8, INVITE_MINUTES = 10;
    /** Garde : pendant qu'une etape est partagee, les progres decernes aux autres ne repartent pas en partage. */
    private static boolean sharing;

    private PartyService() {
    }

    public static void init() {
        RpNet.onServer(RpActions.PARTY_REQUEST, PartyService::request);
    }

    // ------------------------------------------------------------------ etapes
    /** Etapes du Periple faites par ce joueur (ids des progres). */
    public static Set<String> done(ServerPlayer p) {
        Set<String> out = new LinkedHashSet<>();
        MinecraftServer server = p.getServer();
        if (server == null) {
            return out;
        }
        for (JourneySteps.Step s : JourneySteps.ALL) {
            Advancement adv = server.getAdvancements().getAdvancement(ResourceLocation.parse(s.advancement()));
            if (adv != null && p.getAdvancements().getOrStartProgress(adv).isDone()) {
                out.add(s.advancement());
            }
        }
        return out;
    }

    /** Decerne ces etapes au joueur (celles qu'il n'a pas) ; rend combien. */
    public static int award(ServerPlayer p, Set<String> steps) {
        MinecraftServer server = p.getServer();
        if (server == null) {
            return 0;
        }
        int n = 0;
        boolean was = sharing;
        sharing = true;
        try {
            for (String id : steps) {
                Advancement adv = server.getAdvancements().getAdvancement(ResourceLocation.parse(id));
                if (adv == null) {
                    continue;
                }
                AdvancementProgress pr = p.getAdvancements().getOrStartProgress(adv);
                if (pr.isDone()) {
                    continue;
                }
                for (String crit : pr.getRemainingCriteria()) {
                    p.getAdvancements().award(adv, crit);
                }
                n++;
            }
        } finally {
            sharing = was;
        }
        return n;
    }

    /** Le joueur et sa partie se mettent d'accord : ses etapes entrent dans la partie, celles de la partie lui sont decernees. */
    public static int sync(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null) {
            return 0;
        }
        PartyData data = PartyData.get(server);
        PartyData.Party party = data.partyOf(p.getUUID());
        if (party == null) {
            return 0;
        }
        if (party.steps.addAll(done(p))) {
            data.setDirty();
        }
        party.members.put(p.getUUID(), p.getGameProfile().getName());
        return award(p, party.steps);
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (sharing || !(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null) {
            return;
        }
        String id = event.getAdvancement().getId().toString();
        if (!event.getAdvancement().getId().getPath().startsWith("journey/") || JourneySteps.ALL.stream().noneMatch(s -> s.advancement().equals(id))) {
            return;
        }
        PartyData data = PartyData.get(p.getServer());
        PartyData.Party party = data.partyOf(p.getUUID());
        if (party == null) {
            return;
        }
        party.steps.add(id);
        data.setDirty();
        for (UUID u : party.members.keySet()) {
            ServerPlayer m = p.getServer().getPlayerList().getPlayer(u);
            if (m != null && m != p && award(m, Set.of(id)) > 0) {
                m.displayClientMessage(Component.translatable("party.pioneers.msg.shared", p.getGameProfile().getName(),
                      event.getAdvancement().getChatComponent()).withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null) {
            return;
        }
        int n = sync(p);
        if (n > 0) {
            p.sendSystemMessage(Component.translatable("party.pioneers.msg.caught_up", Integer.toString(n)).withStyle(ChatFormatting.GOLD));
        }
        PartyData.Invite inv = PartyData.get(p.getServer()).inviteOf(p.getUUID());
        if (inv != null && !expired(inv, now())) {
            p.sendSystemMessage(Component.translatable("party.pioneers.msg.invited", inv.fromName()).withStyle(ChatFormatting.GOLD));
        }
    }

    // ------------------------------------------------------------------ actions (chaque refus rend la cle courte de l'erreur)
    static long now() {
        return System.currentTimeMillis();
    }

    static boolean expired(PartyData.Invite inv, long now) {
        return now - inv.at() > INVITE_MINUTES * 60_000L;
    }

    /** {@code from} invite {@code to} (connecte) dans sa partie (elle se formera a l'acceptation s'il n'en a pas). */
    @Nullable
    public static String invite(ServerPlayer from, @Nullable ServerPlayer to, long now) {
        if (to == null) {
            return "unknown_player";
        }
        if (to == from) {
            return "self";
        }
        MinecraftServer server = from.getServer();
        PartyData data = PartyData.get(server);
        PartyData.Party mine = data.partyOf(from.getUUID()), theirs = data.partyOf(to.getUUID());
        if (mine != null && mine == theirs) {
            return "same_party";
        }
        if (theirs != null) {
            return "other_party";
        }
        if (mine != null && mine.members.size() >= MAX_MEMBERS) {
            return "full";
        }
        data.invite(to.getUUID(), new PartyData.Invite(from.getUUID(), from.getGameProfile().getName(), now));
        to.sendSystemMessage(Component.translatable("party.pioneers.msg.invited", from.getGameProfile().getName()).withStyle(ChatFormatting.GOLD));
        push(server, to.getUUID());
        return null;
    }

    /** {@code p} accepte l'invitation recue : il rejoint la partie de l'invitant (ou ils en forment une) ; union des etapes. */
    @Nullable
    public static String accept(ServerPlayer p, long now) {
        MinecraftServer server = p.getServer();
        PartyData data = PartyData.get(server);
        PartyData.Invite inv = data.inviteOf(p.getUUID());
        if (inv == null) {
            return "no_invite";
        }
        if (expired(inv, now)) {
            data.takeInvite(p.getUUID());
            return "expired";
        }
        if (data.partyOf(p.getUUID()) != null) {
            return "in_party";
        }
        PartyData.Party party = data.partyOf(inv.from());
        if (party != null && party.members.size() >= MAX_MEMBERS) {
            return "full";
        }
        data.takeInvite(p.getUUID());
        ServerPlayer inviter = server.getPlayerList().getPlayer(inv.from());
        if (party == null) {
            party = data.create(now);
            data.join(party, inv.from(), inv.fromName(), inviter == null ? Set.of() : done(inviter));
        }
        data.join(party, p.getUUID(), p.getGameProfile().getName(), done(p));
        for (UUID u : party.members.keySet()) {
            ServerPlayer m = server.getPlayerList().getPlayer(u);
            if (m != null) {
                sync(m);
                if (m != p) {
                    m.sendSystemMessage(Component.translatable("party.pioneers.msg.joined", p.getGameProfile().getName()).withStyle(ChatFormatting.GOLD));
                }
            }
        }
        pushAll(server, party);
        return null;
    }

    @Nullable
    public static String decline(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        PartyData.Invite inv = PartyData.get(server).takeInvite(p.getUUID());
        if (inv == null) {
            return "no_invite";
        }
        ServerPlayer from = server.getPlayerList().getPlayer(inv.from());
        if (from != null) {
            from.sendSystemMessage(Component.translatable("party.pioneers.msg.declined", p.getGameProfile().getName()).withStyle(ChatFormatting.RED));
        }
        return null;
    }

    /** {@code p} quitte sa partie : il garde toutes ses etapes ; la partie se dissout s'il ne reste qu'un membre. */
    @Nullable
    public static String leave(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        PartyData data = PartyData.get(server);
        PartyData.Party party = data.partyOf(p.getUUID());
        if (party == null) {
            return "not_in_party";
        }
        data.leave(p.getUUID());
        for (UUID u : party.members.keySet()) {
            ServerPlayer m = server.getPlayerList().getPlayer(u);
            if (m != null) {
                m.sendSystemMessage(Component.translatable("party.pioneers.msg.left", p.getGameProfile().getName()).withStyle(ChatFormatting.GOLD));
            }
        }
        pushAll(server, party);
        return null;
    }

    // ------------------------------------------------------------------ demandes du guide
    private static void request(ServerPlayer p, CompoundTag t) {
        String op = t.getString("op");
        long now = now();
        String err = switch (op) {
            case "view" -> null;
            case "invite" -> invite(p, find(p.getServer(), t.getString("name")), now);
            case "accept" -> accept(p, now);
            case "decline" -> decline(p);
            case "leave" -> leave(p);
            case "sync" -> PartyData.get(p.getServer()).partyOf(p.getUUID()) == null ? "not_in_party" : syncOk(p);
            default -> "unknown";
        };
        view(p, "view".equals(op) ? null : err == null ? "ok_" + op : err);
    }

    @Nullable
    private static String syncOk(ServerPlayer p) {
        sync(p);
        return null;
    }

    /** Joueur connecte par son nom de compte (sans tenir compte de la casse). */
    @Nullable
    static ServerPlayer find(MinecraftServer server, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            return null;
        }
        ServerPlayer p = server.getPlayerList().getPlayerByName(n);
        if (p != null) {
            return p;
        }
        for (ServerPlayer q : server.getPlayerList().getPlayers()) {
            if (q.getGameProfile().getName().equalsIgnoreCase(n)) {
                return q;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ vues
    public static CompoundTag viewTag(ServerPlayer p, @Nullable String msg) {
        MinecraftServer server = p.getServer();
        PartyData data = PartyData.get(server);
        CompoundTag t = new CompoundTag();
        PartyData.Party party = data.partyOf(p.getUUID());
        t.putBoolean("in", party != null);
        t.putInt("max", MAX_MEMBERS);
        t.putInt("total", JourneySteps.ALL.size());
        t.putInt("mine", done(p).size());
        if (party != null) {
            ListTag ms = new ListTag();
            party.members.forEach((u, n) -> {
                CompoundTag m = new CompoundTag();
                m.putString("n", n);
                m.putBoolean("on", server.getPlayerList().getPlayer(u) != null);
                m.putBoolean("me", u.equals(p.getUUID()));
                ms.add(m);
            });
            t.put("members", ms);
            t.putInt("shared", party.steps.size());
        }
        PartyData.Invite inv = data.inviteOf(p.getUUID());
        if (inv != null && !expired(inv, now())) {
            t.putString("inviteFrom", inv.fromName());
            PartyData.Party theirs = data.partyOf(inv.from());
            t.putInt("inviteSize", theirs == null ? 1 : theirs.members.size());
        }
        if (msg != null) {
            t.putString("msg", msg);
        }
        return t;
    }

    public static void view(ServerPlayer p, @Nullable String msg) {
        RpNet.send(p, RpActions.PARTY_VIEW, viewTag(p, msg));
    }

    private static void push(MinecraftServer server, UUID who) {
        ServerPlayer p = server.getPlayerList().getPlayer(who);
        if (p != null) {
            CompoundTag t = viewTag(p, null);
            t.putBoolean("push", true);
            RpNet.send(p, RpActions.PARTY_VIEW, t);
        }
    }

    private static void pushAll(MinecraftServer server, PartyData.Party party) {
        for (UUID u : party.members.keySet()) {
            push(server, u);
        }
    }

    // ------------------------------------------------------------------ commandes de secours
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("party")
              .executes(c -> info(c.getSource().getPlayerOrException()))
              .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                    .executes(c -> say(c.getSource().getPlayerOrException(),
                          invite(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player"), now()), "ok_invite"))))
              .then(Commands.literal("accept").executes(c -> say(c.getSource().getPlayerOrException(),
                    accept(c.getSource().getPlayerOrException(), now()), "ok_accept")))
              .then(Commands.literal("decline").executes(c -> say(c.getSource().getPlayerOrException(),
                    decline(c.getSource().getPlayerOrException()), "ok_decline")))
              .then(Commands.literal("leave").executes(c -> say(c.getSource().getPlayerOrException(),
                    leave(c.getSource().getPlayerOrException()), "ok_leave")))
              .then(Commands.literal("sync").executes(c -> {
                  ServerPlayer p = c.getSource().getPlayerOrException();
                  return say(p, PartyData.get(p.getServer()).partyOf(p.getUUID()) == null ? "not_in_party" : syncOk(p), "ok_sync");
              })));
    }

    private static int say(ServerPlayer p, @Nullable String err, String ok) {
        p.sendSystemMessage(Component.translatable(err == null ? "party.pioneers.result." + ok : "party.pioneers.error." + err)
              .withStyle(err == null ? ChatFormatting.GOLD : ChatFormatting.RED));
        return err == null ? 1 : 0;
    }

    private static int info(ServerPlayer p) {
        CompoundTag t = viewTag(p, null);
        if (!t.getBoolean("in")) {
            p.sendSystemMessage(Component.translatable("party.pioneers.alone").withStyle(ChatFormatting.GOLD));
        } else {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < t.getList("members", 10).size(); i++) {
                names.append(i > 0 ? ", " : "").append(t.getList("members", 10).getCompound(i).getString("n"));
            }
            p.sendSystemMessage(Component.translatable("party.pioneers.cmd.members", names.toString(), Integer.toString(t.getInt("shared")),
                  Integer.toString(t.getInt("total"))).withStyle(ChatFormatting.GOLD));
        }
        if (t.contains("inviteFrom")) {
            p.sendSystemMessage(Component.translatable("party.pioneers.msg.invited", t.getString("inviteFrom")).withStyle(ChatFormatting.GOLD));
        }
        return 1;
    }
}

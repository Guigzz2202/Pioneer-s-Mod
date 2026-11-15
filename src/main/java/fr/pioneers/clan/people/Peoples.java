package fr.pioneers.clan.people;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.Faction;
import fr.pioneers.clan.core.ClanClock;
import fr.pioneers.clan.core.ClanCore;
import fr.pioneers.clan.core.ClanMessages;
import fr.pioneers.clan.faction.FactionGov;
import fr.pioneers.clan.faction.FactionRules;
import fr.pioneers.clan.faction.FactionSaved;
import fr.pioneers.clan.faction.Factions;
import fr.pioneers.clan.rep.ClanRepRules;
import fr.pioneers.clan.rep.ClanReputation;
import fr.pioneers.clan.treasury.ClanAudit;
import fr.pioneers.clan.vote.ClanVotes;
import fr.pioneers.clan.vote.Proposal;
import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.core.profile.RpSounds;
import fr.pioneers.economy.EconomyApi;
import fr.pioneers.reputation.PeopleApi;
import fr.pioneers.reputation.ReputationApi;
import fr.pioneers.village.VillageData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 4) : les peuples de joueurs. Une faction de niveau 3, vieille de 30 jours, d'au moins 3 clans et 24 membres actifs, fait
 * SECESSION par la plus forte majorite de son regime federal (20 000 jetons du tresor federal detruits, 4 peuples de joueurs au plus) : un
 * peuple nait dans le registre dynamique ({@link PeopleApi.PlayerPeoples}) avec son nom (unique, filtre), une des huit teintes reservees, sa
 * banniere, sa devise, son salut et sa DOCTRINE (modele d'un peuple de villageois, changeable une fois par 60 jours). Prix du schisme : -60
 * chez les Restaurateurs pour chaque membre des clans fondateurs, une seule fois. Jauges de peuple (-300..+300 envers chaque autre peuple)
 * dans {@code Faction.extra}, nourries par la cascade (25 %) et les actes collectifs. Sous 2 clans ou 12 actifs : declin de 14 jours reels,
 * puis dissolution (villages rendus, nom reserve 30 jours). Les trois peuples de villageois ne changent jamais.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class Peoples {

    /** Cles de {@code Faction.extra} : brouillon de secession, fiche du peuple, jauges et plafond du jour. */
    public static final String DRAFT = "people.draft", META = "people.meta", REP = "people.rep", REP_DAY = "people.repDay",
          REP_TODAY = "people.repToday";

    private Peoples() {
    }

    // ------------------------------------------------------------------ lecture
    @Nullable
    public static PeopleApi.People people(MinecraftServer server, Faction f) {
        return f.people == null || f.people.isEmpty() ? null : PeopleApi.get(server, f.people);
    }

    /** Faction devenue ce peuple de joueurs, ou {@code null}. */
    @Nullable
    public static Faction factionOf(MinecraftServer server, String people) {
        for (Faction f : ClanData.get(server).factions()) {
            if (people.equals(f.people)) {
                return f;
            }
        }
        return null;
    }

    public static int count(MinecraftServer server) {
        return PeopleApi.PlayerPeoples.get(server).peoples.size();
    }

    public static int maxPeoples() {
        return PiCommonConfig.get(RpConfig.MAX_PLAYER_PEOPLES, 4);
    }

    /** Noms deja pris : peuples de joueurs vivants et noms encore reserves. */
    static List<String> taken(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        for (PeopleApi.People p : PeopleApi.PlayerPeoples.get(server).peoples.values()) {
            out.add(p.displayName());
        }
        out.addAll(FactionSaved.get(server).reserved.keySet());
        return out;
    }

    // ------------------------------------------------------------------ brouillon (Representant federal)
    /**
     * Prepare la secession : {@code field} = name, tint, doctrine, motto, salute, banner (banniere tenue en main). Seul le Representant federal
     * redige ; chaque valeur est verifiee tout de suite. Cle du refus ou {@code null}.
     */
    @Nullable
    public static String setDraft(ServerPlayer p, String field, String value) {
        MinecraftServer server = p.getServer();
        Faction f = Factions.ofPlayer(server, p.getUUID());
        if (f == null) {
            return "clan.pioneers.faction.error.none";
        }
        if (!p.getUUID().equals(f.representative)) {
            return "clan.pioneers.faction.error.representative_only";
        }
        if (f.people != null && !f.people.isEmpty()) {
            return "clan.pioneers.people.error.already";
        }
        FactionRules.Values v = FactionRules.get();
        CompoundTag d = f.extra.getCompound(DRAFT);
        String clean = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        switch (field) {
            case "name" -> {
                String err = FactionRules.peopleName(v, clean, ClanCore.rules() == null ? List.of() : ClanCore.rules().bannedWords(), taken(server));
                if (err != null) {
                    return err;
                }
                d.putString("name", clean);
            }
            case "tint" -> {
                if (v.tint(clean) == null) {
                    return "clan.pioneers.people.error.tint";
                }
                d.putString("tint", clean);
            }
            case "doctrine" -> {
                if (!v.doctrines().containsKey(clean)) {
                    return "clan.pioneers.people.error.doctrine";
                }
                d.putString("doctrine", clean);
            }
            case "motto", "salute" -> {
                int max = "motto".equals(field) ? v.mottoMax() : v.saluteMax();
                if (clean.codePointCount(0, clean.length()) > max || clean.chars().anyMatch(c -> c == '§' || Character.isISOControl(c))) {
                    return "clan.pioneers.people.error.text";
                }
                if (ClanCore.rules() != null && fr.pioneers.clan.core.ClanNames.banned(clean, ClanCore.rules())) {
                    return "clan.pioneers.error.name_banned";
                }
                d.putString(field, clean);
            }
            case "banner" -> {
                ItemStack held = p.getMainHandItem();
                if (!(held.getItem() instanceof BannerItem)) {
                    return "clan.pioneers.people.error.banner";
                }
                CompoundTag be = held.getTagElement("BlockEntityTag");
                ListTag patterns = be == null ? new ListTag() : be.getList("Patterns", Tag.TAG_COMPOUND).copy();
                while (patterns.size() > 6) {
                    patterns.remove(patterns.size() - 1);
                }
                d.put("banner", patterns);
            }
            default -> {
                return "clan.pioneers.error.invalid";
            }
        }
        f.extra.put(DRAFT, d);
        ClanData.get(server).changed();
        return null;
    }

    /** Premiere piece manquante du brouillon, ou {@code null}. */
    @Nullable
    static String draftMissing(CompoundTag d) {
        if (d.getString("name").isEmpty()) {
            return "clan.pioneers.people.error.draft_name";
        }
        if (d.getString("tint").isEmpty()) {
            return "clan.pioneers.people.error.draft_tint";
        }
        if (d.getString("doctrine").isEmpty()) {
            return "clan.pioneers.people.error.draft_doctrine";
        }
        return null;
    }

    // ------------------------------------------------------------------ secession
    /** Etat des conditions de la secession pour cette faction. */
    public static FactionRules.Secession conditions(MinecraftServer server, Faction f) {
        long now = ClanClock.now();
        return new FactionRules.Secession(Factions.level(server, f), now - f.founded, f.clans.size(), Factions.active(server, f, now), f.treasury,
              count(server), maxPeoples(), f.people != null && !f.people.isEmpty(), EconomyApi.enabled());
    }

    @Nullable
    static String checkSecession(MinecraftServer server, Faction f, @Nullable ServerPlayer actor, CompoundTag data) {
        String err = FactionRules.secession(FactionRules.get(), conditions(server, f));
        if (err != null) {
            return err;
        }
        CompoundTag d = data.contains("draft") ? data.getCompound("draft") : f.extra.getCompound(DRAFT);
        err = draftMissing(d);
        if (err == null) {
            err = FactionRules.peopleName(FactionRules.get(), d.getString("name"), ClanCore.rules() == null ? List.of() : ClanCore.rules().bannedWords(),
                  taken(server));
        }
        return err;
    }

    /** Effet de la secession acquise : tout est revu, les jetons detruits, le peuple inscrit au registre, le schisme paye. */
    static boolean applySecession(MinecraftServer server, Faction f, @Nullable UUID author, CompoundTag data) {
        if (checkSecession(server, f, null, data) != null) {
            return false;
        }
        FactionRules.Values v = FactionRules.get();
        CompoundTag d = data.contains("draft") ? data.getCompound("draft").copy() : f.extra.getCompound(DRAFT).copy();
        long cost = EconomyApi.enabled() ? v.secessionCost() : 0;
        if (cost > 0 && !Factions.debit(server, f, cost, "#clan.pioneers.people.reason.secession", Factions.character(server, author))) {
            return false;
        }
        FactionRules.Tint tint = v.tint(d.getString("tint"));
        String id;
        do {
            id = "pp_" + Integer.toHexString(0x10000000 + ThreadLocalRandom.current().nextInt(0x60000000));
        } while (PeopleApi.get(server, id) != null);
        String name = d.getString("name");
        PeopleApi.PlayerPeoples.get(server).put(new PeopleApi.People(id, d.getString("doctrine"), 0, tint.rgb(), tint.dye(), true, name));
        long now = ClanClock.now();
        f.people = id;
        f.doctrineChanged = now;
        f.declineUntil = 0;
        CompoundTag meta = new CompoundTag();
        meta.putString("name", name);
        meta.putString("tint", tint.id());
        meta.putString("motto", d.getString("motto"));
        meta.putString("salute", d.getString("salute"));
        meta.put("banner", d.getList("banner", Tag.TAG_COMPOUND));
        meta.putLong("founded", now);
        ListTag founders = new ListTag();
        for (UUID c : f.clans) {
            founders.add(net.minecraft.nbt.NbtUtils.createUUID(c));
        }
        meta.put("founders", founders);
        f.extra.put(META, meta);
        f.extra.remove(DRAFT);
        //jauges de depart : celles de la faction (continuite)
        CompoundTag start = f.extra.getCompound("rep").copy();
        start.remove(id);
        f.extra.put(REP, start);
        schism(server, f, id);
        Factions.log(server, f, "clan.pioneers.people.log.secession", name, "#clan.pioneers.doctrine." + d.getString("doctrine"));
        ClanAudit.write(server, "people", null, "secession de " + f.name + " (" + f.id + ") : peuple " + name + " (" + id + "), doctrine "
              + d.getString("doctrine"));
        Factions.announce(server, Component.translatable("clan.pioneers.people.seceded", f.name, Component.literal(name)
              .withStyle(s -> s.withColor(tint.rgb()))));
        Factions.tell(server, f, Component.translatable("clan.pioneers.people.schism_text").withStyle(ChatFormatting.ITALIC));
        for (Clan c : Factions.clans(server, f)) {
            for (UUID u : c.members.keySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(u);
                if (p != null) {
                    p.level().playSound(null, p.blockPosition(), RpSounds.SEAL.get(), SoundSource.PLAYERS, 1F, 0.7F);
                }
            }
        }
        ClanData.get(server).changed();
        return true;
    }

    /** Prix du schisme : -60 chez les Restaurateurs pour chaque membre des clans fondateurs, une seule fois (hors ligne : a la connexion). */
    static void schism(MinecraftServer server, Faction f, String people) {
        FactionSaved saved = FactionSaved.get(server);
        for (Clan c : Factions.clans(server, f)) {
            for (UUID u : c.members.keySet()) {
                if (saved.schismDone.contains(u)) {
                    continue;
                }
                ServerPlayer p = server.getPlayerList().getPlayer(u);
                if (p != null) {
                    paySchism(p);
                    saved.schismDone.add(u);
                } else {
                    saved.schisms.put(u, people);
                }
            }
        }
        saved.changed();
    }

    private static void paySchism(ServerPlayer p) {
        FactionRules.Values v = FactionRules.get();
        //cause « schism » : hors de la cascade (reputation.json, skip_cascade) ; une perte n'est jamais plafonnee
        ReputationApi.add(p, v.schismPeople(), v.schism(), "schism", true);
        ClanMessages.tell(p, "clan.pioneers.people.schism_paid", -v.schism());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !ClanCore.enabled()) {
            return;
        }
        FactionSaved saved = FactionSaved.get(p.getServer());
        if (saved.schisms.remove(p.getUUID()) != null && saved.schismDone.add(p.getUUID())) {
            paySchism(p);
            saved.changed();
        }
    }

    // ------------------------------------------------------------------ doctrine
    @Nullable
    static String checkDoctrine(MinecraftServer server, Faction f, @Nullable ServerPlayer actor, CompoundTag data) {
        PeopleApi.People pe = people(server, f);
        if (pe == null) {
            return "clan.pioneers.people.error.not_people";
        }
        String doctrine = data.getString("doctrine");
        if (!FactionRules.get().doctrines().containsKey(doctrine)) {
            return "clan.pioneers.people.error.doctrine";
        }
        if (doctrine.equals(pe.doctrine())) {
            return "clan.pioneers.people.error.same_doctrine";
        }
        return FactionRules.doctrineChangeAllowed(FactionRules.get(), f.doctrineChanged, ClanClock.now()) ? null
              : "clan.pioneers.people.error.doctrine_cooldown";
    }

    static boolean applyDoctrine(MinecraftServer server, Faction f, @Nullable UUID author, CompoundTag data) {
        if (checkDoctrine(server, f, null, data) != null) {
            return false;
        }
        PeopleApi.People pe = people(server, f);
        String doctrine = data.getString("doctrine");
        PeopleApi.PlayerPeoples.get(server).put(new PeopleApi.People(pe.id(), doctrine, 0, pe.color(), pe.dye(), true, pe.displayName()));
        f.doctrineChanged = ClanClock.now();
        Factions.log(server, f, "clan.pioneers.people.log.doctrine", "#clan.pioneers.doctrine." + doctrine);
        Factions.tell(server, f, Component.translatable("clan.pioneers.people.doctrine_now", Component.translatable("clan.pioneers.doctrine." + doctrine)));
        ClanData.get(server).changed();
        return true;
    }

    // ------------------------------------------------------------------ jauges de peuple
    /** Branche les jauges du peuple de joueurs dans la cascade de la reputation de clan (part de la faction devenue peuple). */
    static final ClanReputation.PeopleGauge GAUGE = new ClanReputation.PeopleGauge() {
        @Override
        public void apply(MinecraftServer server, Faction faction, String people, int delta, String cause) {
            add(server, faction, people, delta, cause, false);
        }

        @Override
        public int value(MinecraftServer server, Faction faction, String people) {
            return gauge(faction, people);
        }
    };

    public static int gauge(Faction f, String people) {
        return f.extra.getCompound(REP).getInt(people);
    }

    /**
     * Ajoute a la jauge du peuple envers {@code people} (gain plafonne par jour sauf {@code uncapped}, perte jamais ; aucune remontee par le
     * temps). Un peuple ne se juge pas lui-meme. Renvoie le changement effectif.
     */
    public static int add(MinecraftServer server, Faction f, String people, int delta, String cause, boolean uncapped) {
        if (delta == 0 || people.equals(f.people) || f.people == null || f.people.isEmpty()) {
            return 0;
        }
        ClanRepRules.Values v = ClanRepRules.get();
        long day = PioneerApi.day(server);
        if (!f.extra.contains(REP_DAY) || f.extra.getLong(REP_DAY) != day) {
            f.extra.putLong(REP_DAY, day);
            f.extra.put(REP_TODAY, new CompoundTag());
        }
        int d = delta;
        CompoundTag today = f.extra.getCompound(REP_TODAY);
        if (d > 0 && !uncapped) {
            d = ClanRepRules.capGain(d, today.getInt(people), v.dailyCap());
            if (d == 0) {
                return 0;
            }
            today.putInt(people, today.getInt(people) + d);
            f.extra.put(REP_TODAY, today);
        }
        CompoundTag rep = f.extra.getCompound(REP);
        int old = rep.getInt(people);
        int now = ClanRepRules.clamp(old + d, v.min(), v.max());
        if (now == old) {
            return 0;
        }
        rep.putInt(people, now);
        f.extra.put(REP, rep);
        String before = ReputationApi.tierOf(old).id(), after = ReputationApi.tierOf(now).id();
        if (!before.equals(after)) {
            Factions.log(server, f, "clan.pioneers.people.log.tier", people, "#reputation.pioneers.tier." + after);
            Factions.tell(server, f, Component.translatable(now > old ? "clan.pioneers.rep.tier_up" : "clan.pioneers.rep.tier_down",
                  ReputationApi.peopleName(server, people), Component.translatable("reputation.pioneers.tier." + after)));
        }
        ClanData.get(server).changed();
        return now - old;
    }

    // ------------------------------------------------------------------ declin et dissolution
    /** Une fois par minute : declin (sous 2 clans ou 12 membres actifs) et dissolution au bout de 14 jours reels. */
    public static void tickMinute(MinecraftServer server, Faction f, long now) {
        if (people(server, f) == null) {
            if (f.people != null && !f.people.isEmpty()) {
                //registre perdu (donnee retiree a la main) : on nettoie la faction
                f.people = "";
            }
            return;
        }
        FactionRules.Values v = FactionRules.get();
        boolean declining = FactionRules.declining(v, f.clans.size(), Factions.active(server, f, now));
        long until = FactionRules.declineUntil(v, declining, f.declineUntil, now);
        String name = people(server, f).displayName();
        if (until != f.declineUntil) {
            if (until > 0 && f.declineUntil == 0) {
                Factions.log(server, f, "clan.pioneers.people.log.decline", name);
                Factions.tell(server, f, Component.translatable("clan.pioneers.people.decline", name, Math.round(v.declineMs() / (double) FactionRules.DAY),
                      v.declineClans(), v.declineActive()));
            } else if (until == 0) {
                Factions.log(server, f, "clan.pioneers.people.log.recovered", name);
                Factions.tell(server, f, Component.translatable("clan.pioneers.people.recovered", name));
            }
            f.declineUntil = until;
            ClanData.get(server).changed();
        }
        if (f.declineUntil > 0 && now >= f.declineUntil) {
            dissolve(server, f, "clan.pioneers.people.reason.decline");
        }
    }

    /**
     * Dissout le peuple : villages rallies rendus a leur peuple d'origine, peuple retire du registre, nom reserve 30 jours, jauges effacees ;
     * les membres redeviennent Restaurateurs (sans recuperer les points du schisme). La faction demeure si elle a encore ses clans.
     */
    public static void dissolve(MinecraftServer server, Faction f, String reasonKey) {
        PeopleApi.People pe = people(server, f);
        String id = f.people;
        if (id == null || id.isEmpty()) {
            return;
        }
        Rallying.cancel(server, f);
        Rallying.returnAll(server, f);
        for (String t : List.of("secession", "doctrine", "rally")) {
            Proposal p = ClanVotes.openOfType(f.proposals, FactionGov.PREFIX + t);
            if (p != null) {
                ClanVotes.cancel(server, null, f, p.id(), "clan.pioneers.people.why.dissolved");
            }
        }
        String name = pe == null ? f.extra.getCompound(META).getString("name") : pe.displayName();
        PeopleApi.PlayerPeoples.get(server).remove(id);
        FactionSaved saved = FactionSaved.get(server);
        saved.reserved.put(FactionRules.fold(name), ClanClock.now() + FactionRules.get().nameReleaseMs());
        saved.changed();
        f.people = "";
        f.declineUntil = 0;
        for (String k : List.of(META, REP, REP_DAY, REP_TODAY, Rallying.RALLIED)) {
            f.extra.remove(k);
        }
        Factions.log(server, f, "clan.pioneers.people.log.dissolved", name, "#" + reasonKey);
        ClanAudit.write(server, "people", null, "peuple dissous : " + name + " (" + id + ") de " + f.name + " : " + reasonKey);
        Factions.announce(server, Component.translatable("clan.pioneers.people.dissolved", name, Component.translatable(reasonKey)));
        ClanData.get(server).changed();
    }

    // ------------------------------------------------------------------ annuaire des peuples
    /**
     * Annuaire des peuples (public) : les trois peuples de villageois et les peuples de joueurs : nom, couleur, doctrine, villages connus ;
     * pour un peuple de joueurs, sa faction (nom, etiquette), ses clans, son effectif, sa devise, son salut, ses villages rallies, son declin.
     * Aucun nom de joueur.
     */
    public static CompoundTag directory(MinecraftServer server) {
        CompoundTag out = new CompoundTag();
        ListTag list = new ListTag();
        VillageData villages = VillageData.get(server.overworld());
        for (PeopleApi.People p : PeopleApi.all(server)) {
            CompoundTag c = new CompoundTag();
            c.putString("id", p.id());
            c.putBoolean("player", p.player());
            c.putString("name", Component.Serializer.toJson(ReputationApi.peopleName(server, p.id())));
            c.putInt("color", p.color());
            c.putString("doctrine", p.doctrine());
            c.putString("model", PeopleApi.model(server, p.id()));
            int n = 0;
            for (VillageData.Village v : villages.all()) {
                if (p.id().equals(v.people)) {
                    n++;
                }
            }
            c.putInt("villages", n);
            if (p.villagers()) {
                c.putInt("share", p.share());
            } else {
                Faction f = factionOf(server, p.id());
                if (f != null) {
                    CompoundTag meta = f.extra.getCompound(META);
                    c.putString("faction", f.name);
                    c.putString("tag", f.tag);
                    c.putInt("clans", f.clans.size());
                    c.putInt("members", Factions.members(server, f));
                    c.putString("motto", meta.getString("motto"));
                    c.putString("salute", meta.getString("salute"));
                    c.putInt("rallied", Rallying.rallied(f).size());
                    c.putLong("founded", meta.getLong("founded"));
                    c.putLong("decline", f.declineUntil);
                    c.putString("regime", f.regime);
                }
            }
            list.add(c);
        }
        out.put("peoples", list);
        out.putInt("max", maxPeoples());
        return out;
    }

    // ------------------------------------------------------------------ branchements
    /** Branche secession, doctrine, ralliement et jauges (appele par {@code FactionGov.install}). */
    public static void install() {
        FactionGov.register("secession", Peoples::checkSecession, Peoples::applySecession);
        FactionGov.register("doctrine", Peoples::checkDoctrine, Peoples::applyDoctrine);
        FactionGov.register("rally", Rallying::check, Rallying::start);
        ClanReputation.peopleGauge = GAUGE;
    }
}

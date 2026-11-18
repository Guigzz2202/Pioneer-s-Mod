package fr.pioneers.society.clan.core;

import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.treasury.ClanFinance;
import fr.pioneers.society.clan.vote.ClanGov;
import fr.pioneers.society.clan.vote.ClanVotes;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.9 (MAJ RP, bloc 4) : actions reseau des clans. Le client n'envoie que des INTENTIONS (jamais un montant decide par lui sans
 * verification, jamais un identifiant de compte : les membres sont designes par leur jeton de clan) ; chaque action est revue ici contre le
 * role, le regime et l'etat du clan, par les memes fonctions que les commandes. Reponses : {@code CLAN_NOTICE} (resultat, cle de langue) puis
 * la fiche a jour ({@code CLAN_VIEW}).
 * <ul>
 * <li>{@code CLAN_REQUEST} {sheet, page} : fiche (voir {@link ClanViews}).</li>
 * <li>{@code CLAN_ACTION} {op, sheet, ...} : invite {name}, accept {clan}, decline, leave, kick {member}, assign {member, role},
 * role_edit {op, role, ...}, vote {proposal, choice}, tax {kind, value}, identity {field, value, color}, regime {regime}, heir {member},
 * counter {type}, donate {amount}, withdraw {amount}, bonus {member, amount}.</li>
 * <li>{@code CHARTER_EDIT} : voir {@link Charter#edit}.</li>
 * </ul>
 */
public final class ClanNetwork {

    private ClanNetwork() {
    }

    public static void init() {
        RpNet.onServer(RpActions.CLAN_REQUEST, ClanNetwork::request);
        RpNet.onServer(RpActions.CLAN_ACTION, ClanNetwork::action);
        RpNet.onServer(RpActions.CHARTER_EDIT, Charter::edit);
    }

    private static void request(ServerPlayer p, CompoundTag t) {
        String sheet = t.getString("sheet");
        //1.9 (MAJ RP, bloc 4) : fiches de l'onglet Clan hors fondations (statistiques, territoire, diplomatie, annuaires)
        if (fr.pioneers.society.clan.ui.ClanUiViews.SHEETS.contains(sheet)) {
            fr.pioneers.society.clan.ui.ClanUiViews.request(p, sheet, t);
            return;
        }
        if (!ClanViews.SHEETS.contains(sheet)) {
            sheet = "sheet";
        }
        RpNet.send(p, RpActions.CLAN_VIEW, ClanViews.sheet(p, sheet, Math.max(0, t.getInt("page"))));
    }

    private static void action(ServerPlayer p, CompoundTag t) {
        String err = run(p, t);
        CompoundTag n = new CompoundTag();
        n.putString("kind", err == null ? "ok" : "error");
        n.putString("op", t.getString("op"));
        if (err != null) {
            n.putString("key", err);
        }
        RpNet.send(p, RpActions.CLAN_NOTICE, n);
        String sheet = t.getString("sheet");
        //1.9 (MAJ RP, bloc 4) : la fiche a jour peut etre une fiche de l'onglet Clan hors fondations
        if (fr.pioneers.society.clan.ui.ClanUiViews.SHEETS.contains(sheet)) {
            fr.pioneers.society.clan.ui.ClanUiViews.send(p, sheet, t);
            return;
        }
        RpNet.send(p, RpActions.CLAN_VIEW, ClanViews.sheet(p, ClanViews.SHEETS.contains(sheet) ? sheet : "sheet"));
    }

    /** Execute une intention ; renvoie la cle du refus ou {@code null}. */
    @Nullable
    static String run(ServerPlayer p, CompoundTag t) {
        if (!ClanCore.enabled()) {
            return "clan.pioneers.error.disabled";
        }
        String op = t.getString("op");
        switch (op) {
            case "invite" -> {
                ServerPlayer target = p.getServer().getPlayerList().getPlayerByName(t.getString("name"));
                return target == null ? "clan.pioneers.error.no_player" : ClanCore.invite(p, target);
            }
            case "accept" -> {
                return ClanCore.accept(p, t.getString("clan").isEmpty() ? null : t.getString("clan"));
            }
            case "decline" -> {
                ClanCore.decline(p);
                return null;
            }
            case "leave" -> {
                return ClanCore.leave(p);
            }
            default -> {
                //1.9 (MAJ RP, bloc 4) : intentions de l'onglet Clan (traites, guerre, creneaux, invitation par personnage)
                if (fr.pioneers.society.clan.ui.ClanUiActions.handles(op)) {
                    return fr.pioneers.society.clan.ui.ClanUiActions.run(p, t);
                }
            }
        }
        Clan c = ClanData.get(p.getServer()).clanOf(p.getUUID());
        if (c == null) {
            return "clan.pioneers.error.no_clan";
        }
        switch (op) {
            case "kick" -> {
                UUID u = ClanViews.member(c, t.getString("member"));
                if (u == null) {
                    return "clan.pioneers.error.not_member";
                }
                CompoundTag d = new CompoundTag();
                d.putUUID("target", u);
                return gov(ClanGov.decide(p, c, "kick", d, t.getString("text")));
            }
            case "assign" -> {
                UUID u = ClanViews.member(c, t.getString("member"));
                if (u == null) {
                    return "clan.pioneers.error.not_member";
                }
                String err = ClanRoles.assign(c, p.getUUID(), u, t.getString("role"));
                if (err == null) {
                    roleChanged(p, c, u, t.getString("role"));
                }
                return err;
            }
            case "role_edit" -> {
                CompoundTag d = t.getCompound("data");
                return gov(ClanGov.decide(p, c, "roles", d, t.getString("text")));
            }
            case "vote" -> {
                String choice = t.getString("choice");
                if (!choice.equals("yes") && !choice.equals("no") && !choice.equals("abstain")) {
                    UUID u = ClanViews.member(c, choice);
                    if (u == null) {
                        return "clan.pioneers.error.vote_choice";
                    }
                    choice = u.toString();
                }
                return ClanVotes.vote(p, ClanVotes.Scope.CLAN, c.id, t.getString("proposal"), choice);
            }
            case "tax" -> {
                return gov(ClanFinance.setTax(p, c, t.getString("kind"), t.getInt("value")));
            }
            case "identity" -> {
                CompoundTag d = new CompoundTag();
                d.putString("field", t.getString("field"));
                d.putString("value", t.getString("value"));
                d.putInt("color", t.getInt("color"));
                return gov(ClanGov.decide(p, c, "identity", d, t.getString("text")));
            }
            case "regime" -> {
                return gov(ClanGov.changeRegime(p, c, t.getString("regime")));
            }
            case "heir" -> {
                String tok = t.getString("member");
                return ClanGov.setHeir(p, c, tok.isEmpty() ? null : ClanViews.member(c, tok));
            }
            case "counter" -> {
                return gov(ClanGov.counter(p, c, t.getString("type")));
            }
            case "donate" -> {
                return ClanFinance.donate(p, t.getLong("amount"));
            }
            case "withdraw" -> {
                return ClanFinance.withdraw(p, t.getLong("amount")).error();
            }
            case "bonus" -> {
                UUID u = ClanViews.member(c, t.getString("member"));
                return u == null ? "clan.pioneers.error.not_member" : ClanFinance.bonus(p, u, t.getLong("amount")).error();
            }
            default -> {
                return "clan.pioneers.error.invalid";
            }
        }
    }

    @Nullable
    private static String gov(ClanGov.Result r) {
        return r.ok() ? null : r.error();
    }

    /** Journal et message apres un changement de role (promotion ou retrogradation). */
    static void roleChanged(ServerPlayer actor, Clan c, UUID target, String role) {
        Clan.Role r = c.roles.get(role);
        String name = ClanMessages.character(actor.getServer(), c, target);
        c.log(fr.pioneers.core.profile.PioneerApi.day(actor.getServer()), "clan.pioneers.log.role_set", name, r == null ? role
              : ClanRoles.displayName(r), ClanMessages.character(actor));
        ServerPlayer tp = actor.getServer().getPlayerList().getPlayer(target);
        if (tp != null && r != null) {
            String disp = ClanRoles.displayName(r);
            ClanMessages.tell(tp, "clan.pioneers.role_now", ClanMessages.arg(disp));
        }
        ClanData.get(actor.getServer()).changed();
    }
}

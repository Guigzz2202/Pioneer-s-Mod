package fr.pioneers.economy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.pioneers.Pioneers;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.PioneerProfile;
import fr.pioneers.core.profile.ProfileSync;
import fr.pioneers.core.profile.Reconversion;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.ruins.Restoration;
import java.util.Iterator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 3) : branchements de la monnaie sur les blocs 1 et 2 (reconversion payee en jetons, part en argent des restaurations),
 * commandes {@code /pay}, {@code /pioneer money}, {@code /pioneer ledger}, et le virement depuis la Bourse du Carnet.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class EconomyHooks {

    private EconomyHooks() {
    }

    static void init() {
        Reconversion.tokens = new Reconversion.TokenPayer() {
            @Override
            public long balance(ServerPlayer player) {
                return EconomyApi.balance(player);
            }

            @Override
            public boolean debit(ServerPlayer player, long amount, String reason) {
                return EconomyApi.debit(player, amount, EconomyApi.Entry.of(reason)).ok();
            }
        };
        Restoration.tokens = new Restoration.Payment() {
            @Override
            public long price(List<Restoration.Need> items) {
                double centi = 0;
                for (Restoration.Need n : items) {
                    centi += Values.value(sample(n.id())) * (double) n.count();
                }
                return Math.max(1, Math.round(centi * Values.setting("restoration_share", 0.10) / 100.0));
            }

            @Override
            public boolean debit(ServerPlayer player, long amount, String reason) {
                return EconomyApi.debit(player, amount, EconomyApi.Entry.of(reason)).ok();
            }

            @Override
            public void refund(ServerPlayer player, long amount, String reason) {
                EconomyApi.credit(player, amount, EconomyApi.Entry.of(reason));
            }
        };
        RpNet.onServer(RpActions.PAY, EconomyHooks::payRequest);
    }

    /** Objet representatif d'une ligne de nomenclature (id ou #tag : premier objet du tag). */
    static ItemStack sample(String id) {
        if (id.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation(id.substring(1)));
            for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                return new ItemStack(h.value());
            }
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id)));
    }

    // ------------------------------------------------------------------ virement depuis la Bourse
    private static void payRequest(ServerPlayer player, CompoundTag t) {
        ServerPlayer to = player.getServer().getPlayerList().getPlayerByName(t.getString("to"));
        long amount = t.getLong("amount");
        boolean confirm = t.getBoolean("confirm");
        CompoundTag back = new CompoundTag();
        if (!confirm) {
            //premiere etape : le serveur verifie et renvoie destinataire et total a confirmer
            back.putString("check", check(player, to, amount));
            back.putString("to", to == null ? t.getString("to") : EconomyApi.name(to));
            back.putLong("amount", amount);
            RpNet.send(player, RpActions.BALANCE, back);
            return;
        }
        EconomyApi.Result r = EconomyApi.transfer(player, to, amount);
        back.putString("done", r.name());
        RpNet.send(player, RpActions.BALANCE, back);
        if (r.ok() && to != null) {
            to.sendSystemMessage(Component.translatable("economy.pioneers.pay.received", String.valueOf(amount), EconomyApi.name(player))
                  .withStyle(ChatFormatting.GOLD));
        }
    }

    private static String check(ServerPlayer from, ServerPlayer to, long amount) {
        if (to == null) {
            return EconomyApi.Result.ABSENT.name();
        }
        if (amount <= 0 || to == from) {
            return EconomyApi.Result.INVALID.name();
        }
        return EconomyApi.canDebit(from, amount).name();
    }

    // ------------------------------------------------------------------ commandes
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("pay").then(Commands.argument("player", EntityArgument.player())
              .then(Commands.argument("amount", LongArgumentType.longArg(1, EconomyApi.MAX)).executes(EconomyHooks::pay))));
        d.register(Commands.literal("pioneer").requires(s -> s.hasPermission(2))
              .then(Commands.literal("money").then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.literal("show").executes(c -> money(c, "show")))
                    .then(Commands.literal("set").then(Commands.argument("amount", LongArgumentType.longArg(0, EconomyApi.MAX))
                          .executes(c -> money(c, "set"))))
                    .then(Commands.literal("add").then(Commands.argument("amount", LongArgumentType.longArg(0, EconomyApi.MAX))
                          .executes(c -> money(c, "add"))))
                    .then(Commands.literal("take").then(Commands.argument("amount", LongArgumentType.longArg(0, EconomyApi.MAX))
                          .executes(c -> money(c, "take"))))))
              .then(Commands.literal("ledger").then(Commands.argument("player", EntityArgument.player()).executes(c -> ledger(c, 10))
                    .then(Commands.argument("n", LongArgumentType.longArg(1, 200)).executes(c -> ledger(c,
                          (int) LongArgumentType.getLong(c, "n")))))));
    }

    private static int pay(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer from = c.getSource().getPlayerOrException();
        ServerPlayer to = EntityArgument.getPlayer(c, "player");
        long amount = LongArgumentType.getLong(c, "amount");
        EconomyApi.Result r = EconomyApi.transfer(from, to, amount);
        if (r.ok()) {
            c.getSource().sendSuccess(() -> Component.translatable("economy.pioneers.pay.sent", String.valueOf(amount), EconomyApi.name(to)), false);
            to.sendSystemMessage(Component.translatable("economy.pioneers.pay.received", String.valueOf(amount), EconomyApi.name(from))
                  .withStyle(ChatFormatting.GOLD));
            return 1;
        }
        c.getSource().sendFailure(Component.translatable("economy.pioneers.result." + r.name().toLowerCase(java.util.Locale.ROOT)));
        return 0;
    }

    private static int money(CommandContext<CommandSourceStack> c, String op) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        PioneerProfile prof = PioneerCap.get(p);
        if (!"show".equals(op)) {
            long amount = LongArgumentType.getLong(c, "amount");
            long before = prof.balance;
            long after = switch (op) {
                case "set" -> amount;
                case "add" -> Math.min(EconomyApi.MAX, before + amount);
                default -> Math.max(0, before - amount);
            };
            //operation d'operateur : inscrite au journal comme les autres
            if (after > before) {
                prof.balance = before;
                EconomyApi.credit(p, after - before, EconomyApi.Entry.of("op", c.getSource().getTextName()));
            } else if (after < before) {
                EconomyApi.debit(p, before - after, EconomyApi.Entry.of("op", c.getSource().getTextName()));
            }
            ProfileSync.full(p);
        }
        long now = prof.balance;
        c.getSource().sendSuccess(() -> Component.translatable("economy.pioneers.balance_of", p.getGameProfile().getName(), String.valueOf(now)), true);
        return 1;
    }

    private static int ledger(CommandContext<CommandSourceStack> c, int n) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        PioneerProfile prof = PioneerCap.get(p);
        int skip = Math.max(0, prof.ledger.size() - n);
        Iterator<CompoundTag> it = prof.ledger.iterator();
        int i = 0;
        while (it.hasNext()) {
            CompoundTag t = it.next();
            if (i++ < skip) {
                continue;
            }
            long a = t.getLong("a");
            //1.0.3-beta (lot 13) : libelle lisible (LedgerLabels), traduit chez le lecteur ; la sorte brute reste entre crochets pour l'operateur
            Component what = LedgerLabels.label(t.getString("k"), t.getString("p"), LedgerLabels::known, "");
            c.getSource().sendSuccess(() -> Component.literal(String.format("J%d  ", t.getLong("d"))).append(what)
                  .append(String.format("  [%s]  %s x%d  %s%d", t.getString("k"), t.getString("i"), t.getInt("n"), a >= 0 ? "+" : "", a)), false);
        }
        return 1;
    }
}

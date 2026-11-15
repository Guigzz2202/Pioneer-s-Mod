package fr.pioneers.economy.bin;

import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.economy.EconomyApi;
import fr.pioneers.economy.EconomyBlocks;
import fr.pioneers.economy.Trade;
import fr.pioneers.economy.Values;
import fr.pioneers.reputation.ReputationApi;
import fr.pioneers.village.VillageData;
import fr.pioneers.village.Villagers;
import fr.pioneers.village.Villages;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.10.1 : bac de vente du village ({@link SaleBinBlock}) : 54 cases (un double coffre), un proprietaire (le joueur qui l'a pose, voir
 * {@link PiBlockEntity#owner()}), un village (le plus proche de la pose, cloche a moins de {@link SaleBinRules#CENTER_RADIUS} blocs).
 * <p>
 * A minuit (heure 18 000 du monde ; nuit sautee en dormant comprise ; chunk decharge a minuit : a son rechargement, une seule fois), le bac
 * vend ce qu'il peut ({@link SaleBinRules}) : les jetons vont au proprietaire (impot du clan preleve comme a une vente directe ; absent :
 * credit mis de cote jusqu'a sa connexion, {@link EconomyApi#creditLater}), la reputation du village monte (tous les villageois du registre,
 * {@link Villagers#changeVillage}) ; absent, ces points attendent sa prochaine visite au bac ou la nuit suivante (jamais plus d'une nuit de
 * points). Ce qui ne se vend pas reste dans le bac. Rien n'est vendu si le village refuse le proprietaire (affinite moyenne
 * &lt;= {@link SaleBinRules#REFUSE_AT}, palier Hostile du peuple).
 * <p>
 * Remplissage : le proprietaire (interface du coffre double), les entonnoirs, cables et robots (insertion seulement : rien ne ressort du
 * bac par une face).
 */
public class SaleBinTile extends PiBlockEntity implements WorldlyContainer {

    private static final int[] ALL = new int[SaleBinRules.SLOTS];

    static {
        for (int i = 0; i < ALL.length; i++) {
            ALL[i] = i;
        }
    }

    private final NonNullList<ItemStack> items = NonNullList.withSize(SaleBinRules.SLOTS, ItemStack.EMPTY);
    private String ownerName = "";
    /** Village du bac (identifiant = position de sa cloche), 0 inconnu. */
    private long village;
    /** Derniere nuit traitee (-1 : pas encore fixee : fixee au premier tick sans rien vendre). */
    private long lastNight = -1;
    /** Points de reputation d'une nuit ou le proprietaire etait absent. */
    private int pendingPoints;
    private int clock;
    private final LazyOptional<IItemHandler> handler = LazyOptional.of(() -> new InvWrapper(this) {
        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }
    });

    public SaleBinTile(BlockPos pos, BlockState state) {
        super(EconomyBlocks.SALE_BIN_TILE.get(), pos, state);
    }

    // ------------------------------------------------------------------ proprietaire et village
    public String ownerName() {
        return ownerName;
    }

    public long village() {
        return village;
    }

    void claim(ServerPlayer player, long villageId) {
        setOwner(player.getUUID());
        ownerName = EconomyApi.name(player);
        village = villageId;
        if (level != null) {
            lastNight = SaleBinRules.night(level.getDayTime());
        }
        setChanged();
    }

    public boolean isOwner(Player player) {
        return owner() != null && owner().equals(player.getUUID());
    }

    public boolean canManage(Player player) {
        return owner() == null || isOwner(player) || player.hasPermissions(2);
    }

    // ------------------------------------------------------------------ minuit
    void serverTick(ServerLevel level) {
        if (++clock < 20) {
            return;
        }
        clock = 0;
        long night = SaleBinRules.night(level.getDayTime());
        if (lastNight < 0 || night < lastNight) {
            //premier tick, ou heure du monde reculee (commande) : on repart de la nuit courante sans rien vendre
            lastNight = night;
            setChanged();
            return;
        }
        if (night > lastNight) {
            lastNight = night;
            midnight(level);
            setChanged();
        }
    }

    /** Resultat d'une nuit : objets vendus, jetons, points de reputation, objets restes, refus (cle de langue) ou null. */
    public record Night(int sold, long tokens, int points, int left, @Nullable String refusal) {
    }

    /** Vente de minuit (appelee aussi par le GameTest). */
    public Night midnight(ServerLevel level) {
        UUID ownerId = owner();
        if (ownerId == null || !EconomyApi.enabled()) {
            return new Night(0, 0, 0, count(), "economy.pioneers.disabled");
        }
        VillageData.Village v = villageOf(level);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        String refusal = null;
        if (v == null) {
            refusal = "economy.pioneers.bin.no_village";
        } else if (v.average(ownerId) <= SaleBinRules.REFUSE_AT || owner != null && ReputationApi.tier(owner, v.people).refuse()) {
            refusal = "economy.pioneers.bin.refused";
        }
        if (refusal != null) {
            if (owner != null) {
                owner.sendSystemMessage(Component.translatable(refusal).withStyle(ChatFormatting.RED));
            }
            return new Night(0, 0, 0, count(), refusal);
        }
        //devis de la nuit, sans rien changer
        double villagerBuys = Values.partner("villager", "buys", 0.5), share = SaleBinRules.share();
        int factor = SaleBinRules.quotaFactor();
        Map<Item, Integer> taken = new HashMap<>();
        int[] take = new int[items.size()];
        long cents = 0;
        int sold = 0;
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty() || !Trade.sellable(s) || !Values.peddlerBuys(s.getItem())) {
                continue;
            }
            Values.Entry e = Values.entry(s);
            long base = Values.value(s);
            if (e == null || base <= 0) {
                continue;
            }
            int n = SaleBinRules.room(Values.quota(e.quota()), factor, taken.getOrDefault(s.getItem(), 0), s.getCount());
            if (n <= 0) {
                continue;
            }
            take[i] = n;
            taken.merge(s.getItem(), n, Integer::sum);
            cents += SaleBinRules.unit(base, villagerBuys, share) * n;
            sold += n;
        }
        long tokens = cents / 100;
        if (sold == 0) {
            applyPending(level, v, owner);
            return new Night(0, 0, 0, count(), null);
        }
        if (owner != null && EconomyApi.canCredit(owner, tokens) == EconomyApi.Result.CAP) {
            owner.sendSystemMessage(Component.translatable("economy.pioneers.cap").withStyle(ChatFormatting.RED));
            return new Night(0, 0, 0, count(), "economy.pioneers.cap");
        }
        //modifications : retirer exactement ce qui est vendu, puis crediter
        String first = "";
        for (int i = 0; i < items.size(); i++) {
            if (take[i] > 0) {
                if (first.isEmpty()) {
                    first = BuiltInRegistries.ITEM.getKey(items.get(i).getItem()).toString();
                }
                items.get(i).shrink(take[i]);
            }
        }
        setChanged();
        EconomyApi.Entry entry = new EconomyApi.Entry("sale_bin", ownerName, first, sold);
        int points = SaleBinRules.points(tokens, SaleBinRules.perPoint(), SaleBinRules.maxPoints());
        if (owner != null) {
            fr.pioneers.clan.ClanTreasury.creditWithTax(owner, tokens, entry);
            Villages.deed(level, v, owner, tokens);
            pendingPoints = 0;
            if (points > 0) {
                Villagers.changeVillage(level, v, owner, points, "sale_bin");
            }
            int left = count();
            owner.sendSystemMessage(Component.translatable("economy.pioneers.bin.sold", String.valueOf(sold), String.valueOf(tokens),
                  String.valueOf(points)).withStyle(ChatFormatting.GOLD));
            if (left > 0) {
                owner.sendSystemMessage(Component.translatable("economy.pioneers.bin.left", String.valueOf(left)).withStyle(ChatFormatting.GRAY));
            }
        } else {
            EconomyApi.creditLater(level.getServer(), ownerId, tokens, entry);
            pendingPoints = Math.min(SaleBinRules.maxPoints(), Math.max(pendingPoints, points));
        }
        level.playSound(null, worldPosition, SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 0.6F, 1.0F);
        return new Night(sold, tokens, owner != null ? points : 0, count(), null);
    }

    /** Points d'une nuit d'absence, rendus quand le proprietaire est la (visite du bac, nuit suivante). */
    void applyPending(ServerLevel level, @Nullable VillageData.Village v, @Nullable ServerPlayer owner) {
        if (pendingPoints <= 0 || owner == null) {
            return;
        }
        if (v == null) {
            v = villageOf(level);
        }
        if (v != null) {
            Villagers.changeVillage(level, v, owner, pendingPoints, "sale_bin");
            owner.sendSystemMessage(Component.translatable("economy.pioneers.bin.pending", String.valueOf(pendingPoints))
                  .withStyle(ChatFormatting.GOLD));
        }
        pendingPoints = 0;
        setChanged();
    }

    @Nullable
    VillageData.Village villageOf(ServerLevel level) {
        VillageData.Village v = village != 0 ? VillageData.get(level).village(village) : null;
        if (v == null) {
            v = Villages.villageFor(level, worldPosition);
            if (v != null && horizontal(v.center) <= SaleBinRules.CENTER_RADIUS) {
                village = v.id;
                setChanged();
            } else {
                v = null;
            }
        }
        return v;
    }

    double horizontal(BlockPos p) {
        double dx = p.getX() - worldPosition.getX(), dz = p.getZ() - worldPosition.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Nombre d'objets dans le bac. */
    public int count() {
        int n = 0;
        for (ItemStack s : items) {
            n += s.getCount();
        }
        return n;
    }

    // ------------------------------------------------------------------ inventaire
    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @NotNull
    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @NotNull
    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack s = ContainerHelper.removeItem(items, slot, amount);
        if (!s.isEmpty()) {
            setChanged();
        }
        return s;
    }

    @NotNull
    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return Container.stillValidBlockEntity(this, player) && canManage(player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public int @NotNull [] getSlotsForFace(@NotNull Direction side) {
        return ALL;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, @NotNull ItemStack stack, @Nullable Direction side) {
        return true;
    }

    /** Rien ne ressort du bac par une face (entonnoir dessous, cables, robots) : il vend, il ne stocke pas. */
    @Override
    public boolean canTakeItemThroughFace(int slot, @NotNull ItemStack stack, @NotNull Direction side) {
        return false;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        //bac vide / garni : etat visuel seulement (UPDATE_CLIENTS, regle de l'audit 0.20.47)
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            boolean filled = !isEmpty();
            if (state.hasProperty(SaleBinBlock.FILLED) && state.getValue(SaleBinBlock.FILLED) != filled) {
                level.setBlock(worldPosition, state.setValue(SaleBinBlock.FILLED, filled), Block.UPDATE_CLIENTS);
            }
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return handler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        handler.invalidate();
    }

    // ------------------------------------------------------------------ sauvegarde
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putString("ownerName", ownerName);
        tag.putLong("village", village);
        tag.putLong("night", lastNight);
        tag.putInt("pending", pendingPoints);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        ownerName = tag.getString("ownerName");
        village = tag.getLong("village");
        lastNight = tag.contains("night") ? tag.getLong("night") : -1;
        pendingPoints = tag.getInt("pending");
    }
}

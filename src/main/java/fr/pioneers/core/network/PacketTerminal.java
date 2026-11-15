package fr.pioneers.core.network;

import fr.pioneers.logistics.robotics.client.ClientRobotics;
import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.logistics.robotics.LogisticResource;
import fr.pioneers.logistics.robotics.Resource;
import fr.pioneers.logistics.robotics.logistics.LogisticNetwork;
import fr.pioneers.logistics.robotics.logistics.LogisticTerminals;
import fr.pioneers.logistics.robotics.logistics.LogisticsRegistry;
import fr.pioneers.logistics.robotics.logistics.TileEntityLogisticTerminal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Terminal logistique : stocks du reseau et commandes (serveur -> client), commander et annuler (client -> serveur).
 */
public final class PacketTerminal {

    private static final int MAX_STOCK = 1500;

    private PacketTerminal() {
    }

    private static void send(ServerPlayer player, TileEntityLogisticTerminal terminal) {
        ServerLevel level = player.serverLevel();
        CompoundTag tag = new CompoundTag();
        LogisticNetwork network = LogisticsRegistry.get(level).networkAt(terminal.getBlockPos(), level.getGameTime());
        tag.putBoolean("covered", network != null);
        ListTag stock = new ListTag();
        if (network != null) {
            List<Map.Entry<LogisticResource, Long>> entries = new ArrayList<>(network.totals.entrySet());
            entries.removeIf(entry -> entry.getKey().type != Resource.ITEMS || entry.getKey().isAny() || entry.getValue() <= 0);
            entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < Math.min(MAX_STOCK, entries.size()); i++) {
                CompoundTag item = new CompoundTag();
                item.put("item", entries.get(i).getKey().item().copyWithCount(1).save(new CompoundTag()));
                item.putLong("count", entries.get(i).getValue());
                stock.add(item);
            }
            tag.putInt("hubs", network.hubs.size());
        }
        tag.put("stock", stock);
        tag.put("orders", terminal.ordersTag());
        //MAJ Recettes (lot 7) : machines a selecteur servies par le reseau, avec leur verrou (le terminal le regle a distance)
        ListTag machines = new ListTag();
        for (BlockPos p : LogisticTerminals.machines(terminal)) {
            if (level.getBlockEntity(p) instanceof RecipeLockable machine) {
                CompoundTag m = new CompoundTag();
                m.putLong("pos", p.asLong());
                m.put("item", new ItemStack(level.getBlockState(p).getBlock()).save(new CompoundTag()));
                m.putString("source", machine.recipeSource());
                m.putInt("tier", machine.recipeTier());
                ResourceLocation locked = machine.lockedRecipe();
                m.putString("lock", locked == null ? "" : locked.toString());
                machines.add(m);
            }
        }
        tag.put("machines", machines);
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Data(terminal.getBlockPos(), tag));
    }

    /** Client -> serveur : rafraichir (action 0), commander (1 : objet, quantite), annuler (2 : case). */
    public record Action(BlockPos pos, int action, ItemStack item, long count) {

        public static final int REFRESH = 0, ORDER = 1, CANCEL = 2, OPEN = 3;

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeVarInt(action);
            buffer.writeItem(item);
            buffer.writeVarLong(count);
        }

        public static Action decode(FriendlyByteBuf buffer) {
            return new Action(buffer.readBlockPos(), buffer.readVarInt(), buffer.readItem(), buffer.readVarLong());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            TileEntityLogisticTerminal terminal = PacketGuard.tile(player, pos, TileEntityLogisticTerminal.class);
            if (terminal == null || player.isSpectator()) {
                return;
            }
            switch (action) {
                case ORDER -> terminal.order(item, Math.max(1, Math.min(count, 64L * 27)));
                case CANCEL -> terminal.cancel((int) count);
                case OPEN -> {
                    player.openMenu(terminal);
                    return;
                }
                default -> {
                }
            }
            send(player, terminal);
        }
    }

    /** Serveur -> client : etat du terminal. */
    public record Data(BlockPos pos, CompoundTag tag) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeNbt(tag);
        }

        public static Data decode(FriendlyByteBuf buffer) {
            BlockPos pos = buffer.readBlockPos();
            //0.20.46 (audit) : serveur -> client, sans le plafond de 2 Mio (1 500 piles avec leur NBT : l'objet commande est renvoye tel quel,
            //le NBT doit rester pour reconnaitre l'objet precis)
            CompoundTag tag = buffer.readAnySizeNbt();
            return new Data(pos, tag == null ? new CompoundTag() : tag);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            BlockPos at = pos;
            CompoundTag data = tag;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRobotics.terminalData(at, data));
        }
    }
}

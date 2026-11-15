package fr.pioneers.logistics.robotics.client;

import fr.pioneers.logistics.robotics.Resource;
import fr.pioneers.logistics.robotics.RoboticOrder;
import fr.pioneers.logistics.robotics.UnitKind;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Textes et icones communs des ordres (carte, listes) et mise en forme des quantites.
 */
final class OrderTexts {

    private OrderTexts() {
    }

    static ItemStack icon(RoboticOrder order) {
        if (order.type == RoboticOrder.Type.ZONE) {
            return new ItemStack(order.task.tool);
        }
        return order.res.icon();
    }

    /** Premiere ligne : ce qui est fait, ou. */
    static Component title(RoboticOrder order) {
        if (order.type == RoboticOrder.Type.ZONE && !order.label.isEmpty()) {
            return Component.literal(order.label).append(Component.literal(" · ").append(Component.translatable(order.task.getTranslationKey()))
                  .withStyle(ChatFormatting.GRAY));
        }
        if (order.type == RoboticOrder.Type.ZONE) {
            int sx = order.max == null || order.min == null ? 0 : order.max.getX() - order.min.getX() + 1;
            int sz = order.max == null || order.min == null ? 0 : order.max.getZ() - order.min.getZ() + 1;
            return Component.translatable("robotics.pioneers.orders.zone_line", Component.translatable(order.task.getTranslationKey()), sx, sz,
                  order.anchor().toShortString());
        }
        return Component.translatable("robotics.pioneers.orders.transfer_line", order.res.name(),
              place(order.source == null ? null : order.source.pos()),
              order.target == null ? Component.literal("?") : RoboticsScreen.blockName(order.target.pos()));
    }

    private static Component place(@Nullable BlockPos pos) {
        return pos == null ? Component.translatable("robotics.pioneers.order.network") : RoboticsScreen.blockName(pos);
    }

    /** Etat, avec l'objet en cause s'il y en a un (« Pas de fournitures : Pierre taillee »). */
    static MutableComponent status(RoboticOrder order) {
        MutableComponent text = Component.translatable(order.status.getTranslationKey());
        if (!order.statusItem.isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(order.statusItem);
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            if (item != null) {
                text.append(" : ").append(item.getDescription());
            }
        }
        return text;
    }

    /** Pourquoi l'ordre en est la, et quoi faire. */
    static Component why(RoboticOrder order) {
        return Component.translatable(order.status.getTranslationKey() + ".hint");
    }

    /** Deuxieme ligne : etat, unites, avancement, affectation. */
    static Component detail(RoboticOrder order) {
        MutableComponent text = status(order);
        if (order.priority != 0) {
            text.append(" · ").append(Component.translatable(order.priority > 0 ? "robotics.pioneers.priority.high"
                                                                                 : "robotics.pioneers.priority.low"));
        }
        if (order.activeUnits > 0) {
            text.append(" · ").append(Component.translatable("robotics.pioneers.orders.units", order.activeUnits, order.maxUnits));
        }
        if (order.type == RoboticOrder.Type.TRANSFER) {
            if (!order.recurring) {
                text.append(" · ").append(Component.translatable("robotics.pioneers.orders.progress", compact(order.delivered),
                      amount(order.amount, order.res.type)));
            } else if (order.amount > 0) {
                text.append(" · ").append(Component.translatable("robotics.pioneers.orders.keep", amount(order.amount, order.res.type)));
            } else {
                text.append(" · ").append(Component.translatable("robotics.pioneers.orders.continuous"));
            }
        }
        UnitKind kind = order.getKind();
        text.append(" · ").append(kind == null ? Component.translatable("robotics.pioneers.assign.auto")
                                               : Component.translatable("robotics.pioneers.assign." + kind.id()));
        return text;
    }

    static int statusColor(RoboticOrder.Status status) {
        return switch (status) {
            case ACTIVE -> 0x55E07A;
            case WAITING, DONE, CONDITION, NOTHING_TO_DO, TARGET_OK -> 0x9AB8A8;
            case PAUSED, UNITS_BUSY, UNITS_CHARGING -> 0xE0C050;
            default -> 0xFF6A5A;
        };
    }

    /** 1 234 567 -> « 1,2 M ». */
    static String compact(long value) {
        if (value >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fG", value / 1e9);
        }
        if (value >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1e6);
        }
        if (value >= 10_000L) {
            return (value / 1000) + "k";
        }
        return Long.toString(value);
    }

    /** Quantite et unite de la famille : « 64 objets », « 4 000 mB », « 1,2M FE ». */
    static String amount(long value, Resource type) {
        return compact(value) + " " + I18n.get("robotics.pioneers.symbol." + type.name().toLowerCase(Locale.ROOT));
    }
}

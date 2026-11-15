package fr.pioneers.client.rail;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.core.PiMenuScreen;
import fr.pioneers.core.Texts;
import fr.pioneers.naval.ShipEntity;
import fr.pioneers.network.PacketRail;
import fr.pioneers.network.PiNetwork;
import fr.pioneers.rail.CarMenu;
import fr.pioneers.rail.train.CarType;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.0 : inventaire d'un vehicule, au style sombre du mod. 0.20.1 : vehicules a vapeur (locomotive, tender) : cases de combustible, jauge
 * d'eau et etat du foyer sous les cases ; bouton « Fiche du train » a droite du libelle de l'inventaire.
 */
public class CarScreen extends PiMenuScreen<CarMenu> {

    /** 0.20.25 : cale d'un navire : texte de la marine ({@code naval.pioneers.car...}) quand il existe. */
    private MutableComponent tr(String key, Object... args) {
        if (minecraft != null && minecraft.level != null && menu.entity(minecraft.level) instanceof ShipEntity && key.startsWith("rail.pioneers.")) {
            String naval = "naval.pioneers." + key.substring("rail.pioneers.".length());
            if (I18n.exists(naval)) {
                return Component.translatable(naval, args);
            }
        }
        return Component.translatable(key, args);
    }

    private static final int BTN_W = 74;

    public CarScreen(CarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, CarMenu.inventoryTop(menu.type, menu.tier, menu.rows) + 76 + 8);
    }

    @Override
    protected void drawBackground(@NotNull GuiGraphics g, int mx, int my) {
        if (!CarMenu.steam(menu.type)) {
            if (CarMenu.hasLine(menu.type, menu.tier)) {
                storeLine(g, mx, my);
            }
            return;
        }
        int y = CarMenu.SLOT_Y + menu.rows * 18 + 2;
        boolean loco = menu.type == CarType.STEAM;
        int w = loco ? 118 : 162;
        int water = menu.water(), cap = Math.max(1, menu.waterCapacity());
        g.fill(7, y, 7 + w, y + 12, SCREEN);
        g.renderOutline(7, y, w, 12, EDGE);
        int filled = (int) Math.round((w - 2) * Math.min(1, water / (double) cap));
        if (filled > 0) {
            g.fill(8, y + 1, 8 + filled, y + 11, 0xFF2F64C8);
            g.fill(8, y + 1, 8 + filled, y + 2, 0x60FFFFFF);
        }
        GuiDraw.center(g, font, tr("rail.pioneers.car.water", Texts.grouped(water),
              Texts.grouped(cap)), 7 + w / 2F, y + 3, 0.75F, TEXT, w - 6);
        if (over(mx, my, 7, y, w, 12)) {
            tooltip(List.of(tr("rail.pioneers.car.water_tip").withStyle(ChatFormatting.GRAY)));
        }
        if (loco) {
            boolean fire = menu.burning();
            int fx = 7 + w + 4, fw = 169 - fx;
            g.fill(fx, y, fx + fw, y + 12, fire ? 0xFFB8561E : 0xFF222B34);
            g.renderOutline(fx, y, fw, 12, fire ? 0xFFFFB070 : EDGE);
            GuiDraw.center(g, font, Component.translatable(fire ? "rail.pioneers.car.fire_on" : "rail.pioneers.car.fire_off"), fx + fw / 2F, y + 3, 0.75F,
                  fire ? 0xFFFFFFFF : DIM, fw - 4);
            if (over(mx, my, fx, y, fw, 12)) {
                tooltip(List.of(tr("rail.pioneers.car.fire_tip").withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    /** 0.20.2 : reserve d'une citerne, d'une batterie, d'un serveur, du wagon a air ou du carburant d'une locomotive. */
    private void storeLine(GuiGraphics g, int mx, int my) {
        int y = CarMenu.SLOT_Y + menu.rows * 18 + 2;
        int w = 162;
        long amount = menu.storeAmount(), cap = Math.max(1, menu.storeCapacity());
        CarType.Store store = menu.type.store;
        int color = switch (store) {
            case FLUID -> 0xFF2F64C8;
            case GAS -> 0xFF9A7AD8;
            case ENERGY -> 0xFFD8B030;
            case DATA -> 0xFF30C8A0;
            default -> 0xFF8AA0B0;
        };
        Component what = content(store, menu.storeContent());
        g.fill(7, y, 7 + w, y + 12, SCREEN);
        g.renderOutline(7, y, w, 12, EDGE);
        int filled = (int) Math.round((w - 2) * Math.min(1, amount / (double) cap));
        if (filled > 0) {
            g.fill(8, y + 1, 8 + filled, y + 11, color);
            g.fill(8, y + 1, 8 + filled, y + 2, 0x60FFFFFF);
        }
        String unit = switch (store) {
            case ENERGY -> " FE";
            case DATA -> " Mo";
            case AIR -> " L";
            default -> " mB";
        };
        Component text = Component.empty().append(what).append("  " + num(amount) + " / " + num(cap) + unit);
        GuiDraw.center(g, font, text, 7 + w / 2F, y + 3, 0.75F, TEXT, w - 6);
        if (over(mx, my, 7, y, w, 12)) {
            tooltip(List.of(what, tr("rail.pioneers.car.store_tip." + store.name().toLowerCase(Locale.ROOT))
                  .withStyle(ChatFormatting.GRAY)));
        }
    }

    private static String num(long v) {
        return Texts.grouped(v);
    }

    /** Nom du contenu (id brut du registre du client), ou « vide ». */
    private static Component content(CarType.Store store, int raw) {
        if (raw >= 0) {
            switch (store) {
                case FLUID -> {
                    Fluid f = ((ForgeRegistry<Fluid>)
                          ForgeRegistries.FLUIDS).getValue(raw);
                    if (f != null) {
                        return new FluidStack(f, 1).getDisplayName();
                    }
                }
                case GAS -> {
                    Gas gas = ((ForgeRegistry<Gas>) PioneersAPI.gasRegistry())
                          .getValue(raw);
                    if (gas != null) {
                        return gas.getName();
                    }
                }
                case DATA -> {
                    DataType type = ((ForgeRegistry<DataType>)
                          PioneersAPI.dataTypeRegistry()).getValue(raw);
                    if (type != null) {
                        return type.getName();
                    }
                }
                default -> {
                }
            }
        }
        return Component.translatable(store == CarType.Store.ENERGY ? "rail.pioneers.car.energy" : store == CarType.Store.AIR
              ? "rail.pioneers.car.air" : "rail.pioneers.car.empty");
    }

    @Override
    protected void drawForeground(@NotNull GuiGraphics g, int mx, int my) {
        int y = inventoryLabelY - 2;
        button(g, mx, my, imageWidth - 7 - BTN_W, y, BTN_W, 11, Component.translatable(menu.entity(minecraft.level) instanceof ShipEntity ? "naval.pioneers.ship.sheet" : "rail.pioneers.car.train_sheet"), true, () -> {
            //On ferme d'abord l'inventaire (le serveur aussi), puis on demande la fiche
            minecraft.player.closeContainer();
            PiNetwork.sendToServer(new PacketRail.TrainEdit(menu.train, PacketRail.TrainEdit.OPEN, menu.index, "", new CompoundTag()));
        });
    }
}

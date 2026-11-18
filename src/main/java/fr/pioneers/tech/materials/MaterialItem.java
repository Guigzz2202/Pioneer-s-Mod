package fr.pioneers.tech.materials;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Forme d'un materiau (poudre, vis, anneau...) : l'infobulle avancee (F3 + H) donne sa quantite de matiere en lingots. */
public class MaterialItem extends Item {

    /**
     * 1.0.3-beta (lot 2) : metaux dont le minerai brut se concasse en 2 et dont le concasse fond comme un lingot (144 mB) : fonderie de l'age
     * du bronze, creuset electrique MKI ({@code industry/recipes.py ore_melts}) ; l'infobulle le dit (« concasser double le metal »).
     */
    static final Set<String> DOUBLES = Set.of("iron", "copper", "tin", "lead", "zinc", "gold");

    private final GeneratedMaterials.Entry entry;

    public MaterialItem(GeneratedMaterials.Entry entry, Properties properties) {
        super(properties);
        this.entry = entry;
    }

    public GeneratedMaterials.Entry entry() {
        return entry;
    }

    /** 1.0 : le lingot d'or du mod remplace celui de Minecraft, y compris pour le troc des piglins. */
    @Override
    public boolean isPiglinCurrency(@NotNull ItemStack stack) {
        return "gold_ingot".equals(entry.id());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        // 1.0.3-beta (lot 2) : « concasser double le metal » (fonderie de l'age du bronze, creuset electrique, haut fourneau primitif)
        if (DOUBLES.contains(entry.material())) {
            if ("raw".equals(entry.form())) {
                tips.hint("tooltip.pioneers.raw_ore.crush");
            } else if ("crushed".equals(entry.form())) {
                tips.hint("tooltip.pioneers.crushed_ore.doubles");
            }
        }
        if (flag.isAdvanced()) {
            String amount = entry.uDen() == 1 ? String.valueOf(entry.uNum()) : entry.uNum() + "/" + entry.uDen();
            tips.spec("tooltip.pioneers.material_amount", amount);
        }
    }
}

package fr.pioneers.computing;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Disquette de donnees (0.17.1) : 64 Ko de fichiers texte (programmes PioScript, journaux) dans le NBT {@code files}. Se range dans un des
 * deux lecteurs de l'ordinateur (clic droit sur l'ordinateur, disquette en main). La disquette d'exemples arrive pre-remplie.
 */
public class DataDiskItem extends Item {

    public static final String FILES = "files";

    public DataDiskItem(Properties properties) {
        super(properties);
    }

    public static CompoundTag files(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(FILES)) {
            CompoundTag files = new CompoundTag();
            //Disquette d'exemples fabriquee (recette : seulement l'etiquette) : remplie a la premiere lecture
            if (tag.getString("label").equals("examples")) {
                ExamplePrograms.ALL.forEach(files::putString);
            }
            tag.put(FILES, files);
        }
        return tag.getCompound(FILES);
    }

    /** Disquette d'exemples (onglet creatif, recette « exemples »). */
    public static ItemStack examples(ItemStack disk) {
        disk.getOrCreateTag().putString("label", "examples");
        files(disk);
        return disk;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        CompoundTag tag = stack.getTag();
        CompoundTag files = tag != null ? tag.getCompound(FILES) : new CompoundTag();
        int used = DiskFiles.used(files);
        if (tag != null && tag.getString("label").equals("examples")) {
            tips.state("computing.pioneers.disk.examples");
        }
        tips.spec("computing.pioneers.disk.usage", files.getAllKeys().size(), used / 1024.0F,
              DiskFiles.DISK_CAPACITY / 1024);
        int shown = 0;
        for (String name : DiskFiles.list(files)) {
            if (shown++ >= 6) {
                tips.hint(Component.literal("…"));
                break;
            }
            tips.good(Component.literal("  " + name));
        }
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return false;
    }
}

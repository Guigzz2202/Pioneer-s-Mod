package fr.pioneers.tech.kinetics.machines;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.20.42 : ce que la perceuse mecanique tire de la roche sur laquelle elle est posee (la roche n'est pas entamee : on y perce sans fin).
 * Pierre et pierre taillee : pierre taillee ; ardoise des abimes : son pave ; les autres roches du sol (granite, diorite, andesite, tuf,
 * calcite, roches du Nether, pierre de l'End...) : elles-memes.
 */
public final class DrillProducts {

    /** Tours de l'arbre par bloc tire. */
    public static final float TURNS = 3;

    private DrillProducts() {
    }

    public static ItemStack of(BlockState rock) {
        if (rock.is(Blocks.STONE) || rock.is(Blocks.COBBLESTONE) || rock.is(Blocks.MOSSY_COBBLESTONE) || rock.is(Blocks.SMOOTH_STONE)) {
            return new ItemStack(Items.COBBLESTONE);
        }
        if (rock.is(Blocks.DEEPSLATE) || rock.is(Blocks.COBBLED_DEEPSLATE)) {
            return new ItemStack(Items.COBBLED_DEEPSLATE);
        }
        if (rock.is(Blocks.GRANITE) || rock.is(Blocks.DIORITE) || rock.is(Blocks.ANDESITE) || rock.is(Blocks.TUFF) || rock.is(Blocks.CALCITE)
            || rock.is(Blocks.NETHERRACK) || rock.is(Blocks.BLACKSTONE) || rock.is(Blocks.BASALT) || rock.is(Blocks.END_STONE)
            || rock.is(BlockTags.BASE_STONE_OVERWORLD) || rock.is(BlockTags.BASE_STONE_NETHER)) {
            ItemStack own = new ItemStack(rock.getBlock().asItem());
            return own.isEmpty() ? ItemStack.EMPTY : own;
        }
        return ItemStack.EMPTY;
    }
}

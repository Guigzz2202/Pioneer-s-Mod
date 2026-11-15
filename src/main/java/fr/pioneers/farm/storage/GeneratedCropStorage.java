package fr.pioneers.farm.storage;

import java.util.List;

/**
 * GENERE par tools/gc/farm2/crop_storage.py (table STORAGE) : NE PAS MODIFIER A LA MAIN. Blocs de stockage des recoltes (1.0.4-beta, lot 6) :
 * id du bloc, recolte stockee (9 = 1 bloc), facon.
 */
public final class GeneratedCropStorage {

    private GeneratedCropStorage() {
    }

    public record Entry(String id, String produce, CropStorageBlock.Style style) {
    }

    public static final List<Entry> ENTRIES = List.of(
          new Entry("carrot_crate", "minecraft:carrot", CropStorageBlock.Style.CRATE),
          new Entry("potato_crate", "minecraft:potato", CropStorageBlock.Style.CRATE),
          new Entry("beetroot_crate", "minecraft:beetroot", CropStorageBlock.Style.CRATE),
          new Entry("parsnip_crate", "pioneers:parsnip", CropStorageBlock.Style.CRATE),
          new Entry("radish_crate", "pioneers:radish", CropStorageBlock.Style.CRATE),
          new Entry("sweet_potato_crate", "pioneers:sweet_potato", CropStorageBlock.Style.CRATE),
          new Entry("onion_crate", "pioneers:onion", CropStorageBlock.Style.CRATE),
          new Entry("garlic_crate", "pioneers:garlic", CropStorageBlock.Style.CRATE),
          new Entry("leek_crate", "pioneers:leek", CropStorageBlock.Style.CRATE),
          new Entry("red_cabbage_crate", "pioneers:red_cabbage", CropStorageBlock.Style.CRATE),
          new Entry("cauliflower_crate", "pioneers:cauliflower", CropStorageBlock.Style.CRATE),
          new Entry("broccoli_crate", "pioneers:broccoli", CropStorageBlock.Style.CRATE),
          new Entry("artichoke_crate", "pioneers:artichoke", CropStorageBlock.Style.CRATE),
          new Entry("corn_crate", "pioneers:corn", CropStorageBlock.Style.CRATE),
          new Entry("apple_crate", "minecraft:apple", CropStorageBlock.Style.CRATE),
          new Entry("tomato_tray", "pioneers:tomato", CropStorageBlock.Style.TRAY),
          new Entry("bell_pepper_tray", "pioneers:bell_pepper", CropStorageBlock.Style.TRAY),
          new Entry("hot_pepper_tray", "pioneers:hot_pepper", CropStorageBlock.Style.TRAY),
          new Entry("eggplant_tray", "pioneers:eggplant", CropStorageBlock.Style.TRAY),
          new Entry("zucchini_tray", "pioneers:zucchini", CropStorageBlock.Style.TRAY),
          new Entry("cucumber_tray", "pioneers:cucumber", CropStorageBlock.Style.TRAY),
          new Entry("strawberry_tray", "pioneers:strawberry", CropStorageBlock.Style.TRAY),
          new Entry("blueberry_tray", "pioneers:blueberry", CropStorageBlock.Style.TRAY),
          new Entry("cranberry_tray", "pioneers:cranberry", CropStorageBlock.Style.TRAY),
          new Entry("grapes_tray", "pioneers:grapes", CropStorageBlock.Style.TRAY),
          new Entry("barley_sack", "pioneers:barley", CropStorageBlock.Style.SACK),
          new Entry("oats_sack", "pioneers:oats", CropStorageBlock.Style.SACK),
          new Entry("rye_sack", "pioneers:rye", CropStorageBlock.Style.SACK),
          new Entry("paddy_rice_sack", "pioneers:paddy_rice", CropStorageBlock.Style.SACK),
          new Entry("soybean_sack", "pioneers:soybean", CropStorageBlock.Style.SACK),
          new Entry("sunflower_seeds_sack", "pioneers:sunflower_seeds", CropStorageBlock.Style.SACK),
          new Entry("hops_sack", "pioneers:hops", CropStorageBlock.Style.SACK));
}

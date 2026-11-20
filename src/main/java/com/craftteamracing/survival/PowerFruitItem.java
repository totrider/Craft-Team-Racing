package com.craftteamracing.survival;

import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.level.block.Block;

/**
 * The Power Fruit: food, and a seed of sorts. Used on the underside of Power Palm fronds (or on a cluster that has room) it is pushed on as a hanging fruit (the cluster block); used
 * anywhere else it is eaten.
 */
public class PowerFruitItem extends ItemNameBlockItem {
    public PowerFruitItem(Block cluster, Properties properties) {
        super(cluster, properties);
    }
}

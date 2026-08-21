package com.mceteams.xii.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class PlaceListener implements Listener {
    private final MiningListener miningListener;

    public PlaceListener(MiningListener miningListener) {
        this.miningListener = miningListener;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        miningListener.addPlacedBlock(event.getBlock().getLocation());
    }
}

package com.hackathon.baritonesocket;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Starts/stops the socket server with the player session and drives the executor each tick. */
@Mod.EventBusSubscriber(modid = BaritoneSocketMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean sessionActive;

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean inWorld = mc.level != null && mc.player != null;
        if (inWorld && !sessionActive) {
            sessionActive = true;
            BaritoneSocketMod.get().onWorldJoined();
        } else if (!inWorld && sessionActive) {
            sessionActive = false;
            BaritoneSocketMod.get().onWorldLeft();
        }
        if (sessionActive) {
            BaritoneSocketMod.get().tick();
        }
    }
}

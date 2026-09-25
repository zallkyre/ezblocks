package dev.ezblocks.client;

import dev.ezblocks.EzBlocks;
import dev.ezblocks.client.screen.StructureBrowserScreen;
import dev.ezblocks.network.ModPayloads;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;

public class EzBlocksClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Payload codecs are already registered by the common entrypoint, which also
		// runs on the physical client. Only the receivers belong here.
		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.OpenBrowser.ID,
				(payload, context) -> context.client().execute(() ->
						Minecraft.getInstance().setScreenAndShow(new StructureBrowserScreen(
								payload.pos(),
								payload.structures(),
								payload.mode(),
								payload.structure(),
								payload.sizeX(),
								payload.sizeY(),
								payload.sizeZ()))));

		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.SyncState.ID,
				(payload, context) -> context.client().execute(() -> {
					StructureBrowserScreen browser = StructureBrowserScreen.active();

					if (browser != null) {
						browser.applySyncState(payload.mode(), payload.structure(), payload.sizeX(), payload.sizeY(),
								payload.sizeZ());
					}
				}));

		EzBlocks.LOGGER.info("EZ Blocks Mod client ready");
	}
}

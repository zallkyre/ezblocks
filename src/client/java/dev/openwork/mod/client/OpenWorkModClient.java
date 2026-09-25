package dev.openwork.mod.client;

import dev.openwork.mod.OpenWorkMod;
import dev.openwork.mod.client.screen.StructureBrowserScreen;
import dev.openwork.mod.network.ModPayloads;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;

public class OpenWorkModClient implements ClientModInitializer {
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

		OpenWorkMod.LOGGER.info("OpenWork Mod client ready");
	}
}

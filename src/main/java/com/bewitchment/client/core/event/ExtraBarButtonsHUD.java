package com.bewitchment.client.core.event;

import com.bewitchment.api.event.HotbarAction;
import com.bewitchment.common.core.net.NetworkHandler;
import com.bewitchment.common.core.net.messages.PlayerUsedAbilityMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class ExtraBarButtonsHUD {

	public static final ExtraBarButtonsHUD INSTANCE = new ExtraBarButtonsHUD();

	// TODO reset these when the user closes the game, either MP or SP
	int slotSelected = -1;
	boolean isInExtraBar = false;
	int selectedItemTemp = 0;
	List<HotbarAction> actions = new ArrayList<HotbarAction>();
	HotbarAction[] actionScroller = new HotbarAction[3];// 0: current, 1: prev, 2: next

	private ExtraBarButtonsHUD() {
	}

	private static void renderTextureAtIndex(double x, double y, int xIndex, int yIndex) {
		double rX = 0.25d * xIndex;
		double rY = 0.25d * yIndex;
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buff = tessellator.getBuffer();

		buff.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
		buff.pos(x, y + 16, 0).tex(rX, rY + 0.25d).endVertex();
		buff.pos(x + 16, y + 16, 0).tex(rX + 0.25d, rY + 0.25d).endVertex();
		buff.pos(x + 16, y, 0).tex(rX + 0.25d, rY).endVertex();
		buff.pos(x, y, 0).tex(rX, rY).endVertex();

		tessellator.draw();
	}

	@SubscribeEvent
	public void RMBHijacker(MouseEvent evt) {
		if (evt.isButtonstate() && slotSelected >= 0 && evt.getButton() == 1) {
			evt.setCanceled(true);
			NetworkHandler.HANDLER.sendToServer(new PlayerUsedAbilityMessage(actions.get(slotSelected).getName().toString()));
		}
	}

	@SubscribeEvent
	public void scrollWheelHijacker(MouseEvent evt) {
		int dir = evt.getDwheel() == 0 ? 0 : evt.getDwheel() > 0 ? -1 : 1;
		if (dir == 0 || (!Minecraft.getMinecraft().player.isSneaking() && !isInExtraBar))
			return;
		int curItm = Minecraft.getMinecraft().player.inventory.currentItem;
		int max = actions.size();
		if (Minecraft.getMinecraft().currentScreen == null) {// Don't mess with scroll wheels if a gui is open, only when playing
			refreshSelected();
			evt.setCanceled(isInExtraBar || (dir < 0 && slotSelected == 0) || (dir > 0 && curItm == 8 && max > 0));
			if (curItm == 8) {
				if (dir > 0) {
					if (curItm == 8 && max > 0) {
						isInExtraBar = true;
						slotSelected++;
						if (slotSelected >= max) {
							slotSelected = max - 1;
						}
					}
				} else if (dir < 0) {
					slotSelected--;
					if (slotSelected < 0) {
						isInExtraBar = false;
					}
				}
			}
			if (slotSelected < -1)
				slotSelected = -1;
			refreshSelected();
		}
	}

	private void refreshSelected() {
		if (slotSelected >= 0) {
			actionScroller[0] = actions.get(slotSelected);
		} else {
			actionScroller = new HotbarAction[3];
			if (actions.size() > 0) {
				actionScroller[0] = actions.get(0);
				if (actions.size() > 1) {
					actionScroller[2] = actions.get(1);
				}
			}
			return;
		}
		if (slotSelected > 0) {
			actionScroller[1] = actions.get(slotSelected - 1);
		} else {
			actionScroller[1] = null;
		}
		if (slotSelected + 1 < actions.size()) {
			actionScroller[2] = actions.get(slotSelected + 1);
		} else {
			actionScroller[2] = null;
		}
	}

	public void setList(List<HotbarAction> list) {
		this.actions = list;
		this.slotSelected = Math.min(slotSelected, list.size() - 1);
		refreshSelected();
	}

	@SubscribeEvent
	public void keybordSelectorCheck(KeyInputEvent evt) { // Used to keep it coherent when the player uses the keys 1-9 to pick the selected item
		if (Minecraft.getMinecraft().player.inventory.currentItem < 8) {
			slotSelected = -1;
			isInExtraBar = false;
			refreshSelected();
		}
	}

	@SubscribeEvent
	public void renderOverlay(RenderGameOverlayEvent.Post event) {
		if (event.getType() == RenderGameOverlayEvent.ElementType.HOTBAR && isInExtraBar) {
			Minecraft.getMinecraft().player.inventory.currentItem = selectedItemTemp;
		}
	}

	@SubscribeEvent
	public void renderOverlay(RenderGameOverlayEvent.Pre event) {
		if (event.getType() == RenderGameOverlayEvent.ElementType.HOTBAR) {
			Minecraft mc = Minecraft.getMinecraft();
			if (isInExtraBar) {
				selectedItemTemp = Minecraft.getMinecraft().player.inventory.currentItem;
				mc.player.inventory.currentItem = 11;// Render overlay to the right (increase to something like 100 to make it disappear, if we decide to use a custom selection indicator)
			}
			ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
			if (actionScroller[0] != null) {
				GlStateManager.pushMatrix();
				if (slotSelected < 0)
					GlStateManager.color(1, 1, 1, 0.4f);
				mc.getTextureManager().bindTexture(actionScroller[0].getIcon(mc.player));
				renderTextureAtIndex((sr.getScaledWidth() / 2) + 131.5, sr.getScaledHeight() - 19.5, actionScroller[0].getIconIndexX(mc.player), actionScroller[0].getIconIndexY(mc.player));
				GlStateManager.popMatrix();
			}
			if (actionScroller[2] != null) {
				GlStateManager.pushMatrix();
				GlStateManager.color(1, 1, 1, 0.4f);
				mc.getTextureManager().bindTexture(actionScroller[2].getIcon(mc.player));
				renderTextureAtIndex((sr.getScaledWidth() / 2) + 152.5, sr.getScaledHeight() - 19.5, actionScroller[2].getIconIndexX(mc.player), actionScroller[2].getIconIndexY(mc.player));
				GlStateManager.popMatrix();
			}

			if (actionScroller[1] != null) {
				GlStateManager.pushMatrix();
				GlStateManager.color(1, 1, 1, 0.4f);
				mc.getTextureManager().bindTexture(actionScroller[1].getIcon(mc.player));
				renderTextureAtIndex((sr.getScaledWidth() / 2) + 110.5, sr.getScaledHeight() - 19.5, actionScroller[1].getIconIndexX(mc.player), actionScroller[1].getIconIndexY(mc.player));
				GlStateManager.popMatrix();
			} else if (slotSelected < 0 && actions.size() > 0) {
				GlStateManager.pushMatrix();
				GlStateManager.color(1, 1, 1, mc.player.isSneaking() ? 1 : 0.2f);
				mc.getTextureManager().bindTexture(HotbarAction.DEFAULT_ICON_TEXTURE);
				renderTextureAtIndex((sr.getScaledWidth() / 2) + 106, sr.getScaledHeight() - 19.5, 3, 3);
				GlStateManager.popMatrix();
			}

		}
	}
}

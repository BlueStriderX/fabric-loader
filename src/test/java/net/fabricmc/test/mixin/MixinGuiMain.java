/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.test.mixin;

import org.schema.common.GraphicsInterface;
import org.schema.game.client.view.font.FontNode;
import org.schema.game.client.view.gui.GUIAnchor;
import org.schema.game.client.view.mainmenu.MainMenuGUI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MainMenuGUI.class, remap = false)
public abstract class MixinGuiMain extends GUIAnchor {
	public MixinGuiMain(GraphicsInterface a) {
		super(a);
	}

	public MixinGuiMain(GraphicsInterface a, float width, float height) {
		super(a, width, height);
	}

	@Inject(method = "draw()V", at = @At("RETURN"))
	public void draw(CallbackInfo info) {
		FontNode text = new FontNode(10, 10, getState());
		text.setString("Fabric Test Mod");
		text.getPos().x = -1.0f;
        text.getPos().y = getHeight() - 30.0f;
		// this.a.attach(text);
	}

}

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

package net.fabricmc.loader.game;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.entrypoint.EntrypointTransformer;

import java.nio.file.Path;
import java.util.List;

public interface GameProvider {
	String getGameId();
	String getGameName();
	String getEntrypoint();
	void populateEntrypointPatches(EntrypointTransformer transformer);
	Path getLaunchDirectory();
	boolean isObfuscated();
	boolean requiresUrlClassLoader();
	List<Path> getGameContextJars();
	boolean locateGame(EnvType envType, ClassLoader loader);
	void acceptArguments(String... arguments);
	void launch(ClassLoader loader);
}

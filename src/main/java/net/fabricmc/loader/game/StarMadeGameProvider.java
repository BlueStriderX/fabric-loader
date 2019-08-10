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

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.entrypoint.EntrypointTransformer;
import net.fabricmc.loader.entrypoint.patches.EntrypointPatchHookStarMade;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.util.Arguments;
import net.fabricmc.loader.util.FileSystemUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StarMadeGameProvider implements GameProvider {
	static class VersionData {
		public String id;
		public String build_time;
		public boolean stable;
	}

	private EnvType envType;
	private String entrypoint;
	private Arguments arguments;
	private Path gameJar;
	private VersionData versionData;
	private boolean hasModLoader = false;
	private boolean hookMainMenu = false;

	@Override
	public String getGameId() {
		if (versionData != null) {
			String id = versionData.id;

			if (id != null) {
				return "starmade-" + id.replaceAll("[^a-zA-Z0-9.]+", "-");
			}
		}

		return "starmade-unknown";
	}

	@Override
	public String getGameName() {
		if (versionData != null && versionData.id != null) {
			return "StarMade " + versionData.id;
		}
		
		return "StarMade";
	}

	@Override
	public String getEntrypoint() {
		return entrypoint;
	}

	@Override
	public Path getLaunchDirectory() {
		if (arguments == null) {
			return new File(".").toPath();
		}

		return FabricLauncherBase.getLaunchDirectory(arguments).toPath();
	}

	@Override
	public boolean isObfuscated() {
		return true; // generally yes...
	}

	@Override
	public boolean requiresUrlClassLoader() {
		return hasModLoader;
	}

	@Override
	public List<Path> getGameContextJars() {
		List<Path> list = new ArrayList<>();
		list.add(gameJar);
		return list;
	}

	@Override
	public boolean locateGame(EnvType envType, ClassLoader loader) {
		this.envType = envType;
		List<String> entrypointClasses = Lists.newArrayList("org.schema.game.common.Starter");

		Optional<GameProviderHelper.EntrypointResult> entrypointResult = GameProviderHelper.findFirstClass(loader, entrypointClasses);
		if (!entrypointResult.isPresent()) {
			return false;
		}

		entrypoint = entrypointResult.get().entrypointName;
		gameJar = entrypointResult.get().entrypointPath;

		try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(gameJar, false)) {
			Path versionJson = jarFs.get().getPath("version.txt");
			if (Files.exists(versionJson)) {
				versionData = new VersionData();
				String[] versionString = new String(Files.readAllBytes(versionJson), StandardCharsets.UTF_8).split("#");
				versionData.id = versionString[0];
				versionData.build_time = versionString[1];
			}
		} catch (IOException e) {
			// TODO: migrate to Logger
			e.printStackTrace();
		}
		return true;
	}

	@Override
	public void acceptArguments(String... argStrs) {
		this.arguments = new Arguments();
		arguments.parse(argStrs);

		FabricLauncherBase.processArgumentMap(arguments, envType);

		// Check if the main menu is going to be launched
		if(arguments.getExtraArgs().contains("-force")) {
			hookMainMenu = true;
		}
	}

	@Override
	public void launch(ClassLoader loader) {
		String targetClass = entrypoint;

		try {
			Class<?> c = ((ClassLoader) loader).loadClass(targetClass);
			Method m = c.getMethod("main", String[].class);
			m.invoke(null, (Object) arguments.toArray());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void populateEntrypointPatches(EntrypointTransformer transformer) {
		transformer.addEntrypointPatch(new EntrypointPatchHookStarMade(transformer, hookMainMenu));
	}
}

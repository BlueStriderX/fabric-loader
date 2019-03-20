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

package net.fabricmc.loader.entrypoint.patches;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.entrypoint.EntrypointPatch;
import net.fabricmc.loader.entrypoint.EntrypointTransformer;
import net.fabricmc.loader.launch.common.FabricLauncher;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;

public class EntrypointPatchHookStarMade extends EntrypointPatch {
	public EntrypointPatchHookStarMade(EntrypointTransformer transformer) {
		super(transformer);
	}

	private void finishEntrypoint(EnvType type, ListIterator<AbstractInsnNode> it) {
		it.add(new VarInsnNode(Opcodes.ALOAD, 0));
		it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/fabricmc/loader/entrypoint/hooks/Entrypoint" + (type == EnvType.CLIENT ? "Client" : "Server"), "start", "(Ljava/io/File;Ljava/lang/Object;)V", false));
	}

	@Override
	public void process(FabricLauncher launcher, Consumer<ClassNode> classEmitter) {
		EnvType type = launcher.getEnvironmentType();
		String entrypoint = launcher.getEntrypoint();

		if (!entrypoint.startsWith("org.schema.")) {
			return;
		}

		try {
			String gameEntrypoint = null;
			boolean serverHasFile = true;
			ClassNode mainClass = loadClass(launcher, entrypoint);

			if (mainClass == null) {
				throw new RuntimeException("Could not load main class " + entrypoint + "!");
			}

			if (type == EnvType.SERVER) {
				throw new RuntimeException("Server environment is unsupported at this time");
			}

			// Each server start will invoke Starter.getServerRunnable where a new server class instance is created.
			// Each client start will invoke Starter.startClient where a new client class instance is created. (Actual game play)
			//  - However, this does not work at the time of the main menu is launched.

			// Find the real method and inject our own.

			if (type == EnvType.SERVER) {
				MethodNode startServer = findMethod(mainClass, (method) -> method.name.equals("getServerRunnable") && method.desc.equals("(Z)Ljava/lang/Runnable;") && isPublicStatic(method.access));
				if (startServer == null) {
					throw new RuntimeException("Could not find initializeServer method in " + entrypoint + "!");
				}

				// look for invokespecial obfuscated_class_xxx.<init>(Lorg/schema/schine/network/client/HostPortLoginName;ZLobfuscated_class_yyy;)V
				MethodInsnNode newGameInsn = (MethodInsnNode) findInsn(startServer,
					(insn) -> insn.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) insn).desc.equals("(Z)V"),
					true
				);

				if (newGameInsn != null) {
					gameEntrypoint = newGameInsn.owner.replace('/', '.');
				}
			}

			// TODO: Find both server and client and hook into both
			if (gameEntrypoint == null) {
				MethodNode startClient = findMethod(mainClass, (method) -> method.name.equals("startClient") && method.desc.startsWith("(Lorg/schema/schine/network/client/HostPortLoginName;Z") && isPublicStatic(method.access));
				if (startClient == null) {
					throw new RuntimeException("Could not find initialize method in " + entrypoint + "!");
				}

				// look for invokespecial obfuscated_class_xxx.<init>(Lorg/schema/schine/network/client/HostPortLoginName;ZLobfuscated_class_yyy;)V
				MethodInsnNode newGameInsn = (MethodInsnNode) findInsn(startClient,
					(insn) -> insn.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) insn).desc.startsWith("(Lorg/schema/schine/network/client/HostPortLoginName;Z"),
					true
				);

				if (newGameInsn != null) {
					gameEntrypoint = newGameInsn.owner.replace('/', '.');
				}
			}

			if (gameEntrypoint == null) {
				throw new RuntimeException("Could not find game constructor in " + entrypoint + "!");
			}

			debug("Found game constructor: " + entrypoint + " -> " + gameEntrypoint);
			ClassNode gameClass = gameEntrypoint.equals(entrypoint) ? mainClass : loadClass(launcher, gameEntrypoint);
			if (gameClass == null) {
				throw new RuntimeException("Could not load game class " + gameEntrypoint + "!");
			}

			MethodNode gameMethod = null;
			MethodNode gameConstructor = null;
			int gameMethodQuality = 0;

			for (MethodNode gmCandidate : gameClass.methods) {
				if (gmCandidate.name.equals("<init>")) {
					gameConstructor = gmCandidate;
					if (gameMethodQuality < 1) {
						gameMethod = gmCandidate;
						gameMethodQuality = 1;
					}
				} else if (gmCandidate.name.equals("run")) {
					gameConstructor = gmCandidate;
					if (gameMethodQuality < 2) {
						gameMethod = gmCandidate;
						gameMethodQuality = 2;
					}
				}
			}

			if (gameMethod == null) {
				throw new RuntimeException("Could not find game constructor method in " + gameClass.name + "!");
			}

			boolean patched = false;
			debug("Patching game constructor " + gameMethod.name + gameMethod.desc);

			if (type == EnvType.SERVER) {
				ListIterator<AbstractInsnNode> it = gameMethod.instructions.iterator();
				// Server-side: first argument (or null!) is runDirectory, run at end of init
				moveBefore(it, Opcodes.RETURN);
				// runDirectory
				if (serverHasFile) {
					it.add(new VarInsnNode(Opcodes.ALOAD, 1));
				} else {
					it.add(new InsnNode(Opcodes.ACONST_NULL));
				}
				finishEntrypoint(type, it);
				patched = true;
			} else {
				ListIterator<AbstractInsnNode> it = gameMethod.instructions.iterator();
				// Client-side:
				// - if constructor, identify runDirectory field + location, run immediately after
				// - if non-constructor (init method), head

				if (gameConstructor == null) {
					throw new RuntimeException("Non-applet client-side, but could not find constructor?");
				}

				if(!gameMethod.name.equals("run"))
				{
					moveBefore(it, Opcodes.RETURN);
				}
				it.add(new InsnNode(Opcodes.ACONST_NULL));
				finishEntrypoint(type, it);

				patched = true;
			}

			if (!patched) {
				throw new RuntimeException("Game constructor patch not applied!");
			}

			if (gameClass != mainClass) {
				classEmitter.accept(gameClass);
			} else {
				classEmitter.accept(mainClass);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}

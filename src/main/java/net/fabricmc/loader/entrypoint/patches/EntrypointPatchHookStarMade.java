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

import com.google.common.base.Strings;

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
			String clientEntrypoint = null;
			String serverEntrypoint = null;
			String mainMenuEntrypoint = null;
			boolean serverHasFile = true;
			ClassNode mainClass = loadClass(launcher, entrypoint);

			if (mainClass == null) {
				throw new RuntimeException("Could not load main class " + entrypoint + "!");
			}

			if (type == EnvType.SERVER) {
				throw new RuntimeException("Server environment is unsupported at this time");
			}

			/* Lets not worry about server side mods for now.
			// Each server start will invoke Starter.getServerRunnable where a new server class instance is created.
			if (Strings.isNullOrEmpty(serverEntrypoint)) {
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
					serverEntrypoint = newGameInsn.owner.replace('/', '.');
				}

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
			}
			*/

			// Each client start will invoke Starter.startClient where a new client class instance is created. (Actual game play)
			if (Strings.isNullOrEmpty(clientEntrypoint)) {
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
					clientEntrypoint = newGameInsn.owner.replace('/', '.');
				} else {
					throw new RuntimeException("Could not find game constructor in " + entrypoint + "!");
				}

				debug("Found game constructor: " + entrypoint + " -> " + clientEntrypoint);
				ClassNode clientClass = clientEntrypoint.equals(entrypoint) ? mainClass : loadClass(launcher, clientEntrypoint);
				if (clientClass == null) {
					throw new RuntimeException("Could not load client runner " + clientEntrypoint + "!");
				}

				MethodNode gameMethod = clientClass.methods.stream()
					.filter(method -> method.name.equals("run")).findFirst()
					.orElseThrow(() -> new RuntimeException("Could not find game constructor method in " + clientClass.name + "!"));

				debug("Patching client runner " + gameMethod.name + gameMethod.desc);

				ListIterator<AbstractInsnNode> it = gameMethod.instructions.iterator();
				it.add(new InsnNode(Opcodes.ACONST_NULL));
				finishEntrypoint(type, it);

				classEmitter.accept(clientClass);
				debug("Patched client runner " + gameMethod.name + gameMethod.desc);
			}

			// Each client start will invoke Starter.startClient where a new client class instance is created. (Actual game play)
			if (Strings.isNullOrEmpty(mainMenuEntrypoint)) {
				MethodNode startMainMenu = findMethod(mainClass, (method) -> method.name.equals("startMainMenu") && method.desc.equals("()V") && isPublicStatic(method.access));
				if (startMainMenu == null) {
					throw new RuntimeException("Could not find initialize method in " + entrypoint + "!");
				}

				// Look for the first invokespecial class_xxx.<init>()V
				MethodInsnNode newGameInsn = (MethodInsnNode) findInsn(startMainMenu,
					(insn) -> insn.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) insn).name.equals("<init>") && ((MethodInsnNode) insn).desc.equals("()V"),
					false
				);

				if (newGameInsn != null) {
					mainMenuEntrypoint = newGameInsn.owner.replace('/', '.');
				} else {
					throw new RuntimeException("Could not find main menu constructor in " + entrypoint + "!");
				}

				debug("Found main menu constructor: " + entrypoint + " -> " + mainMenuEntrypoint);
				ClassNode mainMenuClass = mainMenuEntrypoint.equals(entrypoint) ? mainClass : loadClass(launcher, mainMenuEntrypoint);
				if (mainMenuClass == null) {
					throw new RuntimeException("Could not load client runner " + mainMenuEntrypoint + "!");
				}

				MethodNode gameConstructor = mainMenuClass.methods.stream()
					.filter(method -> method.name.equals("<init>")).findFirst()
					.orElseThrow(() -> new RuntimeException("Could not find game constructor method in " + mainMenuClass.name + "!"));

				debug("Patching main menu " + gameConstructor.name + gameConstructor.desc);

				ListIterator<AbstractInsnNode> it = gameConstructor.instructions.iterator();
				moveBefore(it, Opcodes.RETURN);

				it.add(new InsnNode(Opcodes.ACONST_NULL));
				finishEntrypoint(type, it);

				classEmitter.accept(mainMenuClass);
				debug("Patched main menu " + gameConstructor.name + gameConstructor.desc);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
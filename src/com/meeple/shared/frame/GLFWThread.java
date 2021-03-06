package com.meeple.shared.frame;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11C.glViewport;
import static org.lwjgl.system.MemoryStack.*;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL46;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;
import org.lwjgl.system.MemoryStack;

import com.meeple.shared.CollectionSuppliers;
import com.meeple.shared.Delta;
import com.meeple.shared.frame.component.FrameTimeManager;
import com.meeple.shared.frame.window.Window;
import com.meeple.shared.frame.wrapper.Wrapper;
import com.meeple.shared.frame.wrapper.WrapperImpl;

public class GLFWThread extends Thread {

	private static Logger logger = Logger.getLogger(GLFWThread.class);
	final Window window;
	AtomicInteger quit;
	FrameTimeManager frameTimeManager;
	Callback debugProc;
	Set<Runnable> runnables = new CollectionSuppliers.SetSupplier<Runnable>().get();

	public GLFWThread(Window window, AtomicInteger quit, FrameTimeManager frameTimeManager, boolean enableDebug, Runnable... runnables) {
		this.window = window;
		this.quit = quit;
		this.frameTimeManager = frameTimeManager;
		if (enableDebug) {
			window.events.postCreation.add(() -> {
				GL46.glEnable(GL46.GL_DEBUG_OUTPUT);
				GL46.glDebugMessageCallback(FrameUtils.defaultDebugMessage, 0);
			});
			window.events.postCreation.add(() -> {
				debugProc = GLUtil.setupDebugMessageCallback();
			});
			window.events.preCleanup.add(() -> {
				if (debugProc != null) {
					debugProc.free();
				}
			});
		}
		for (Runnable r : runnables) {
			this.runnables.add(r);
		}
		this.setName("GLFW Thread-" + window.getName());
	}

	@Override
	public void run() {
		FrameUtils.iterateRunnable(window.events.preCreation, false);
		glfwMakeContextCurrent(window.getID());
		window.capabilities = GL.createCapabilities();
		glfwSwapInterval(window.vSync ? 1 : 0);
		glClearColor(window.clearColour.x, window.clearColour.x, window.clearColour.z, window.clearColour.w);
		FrameUtils.iterateRunnable(window.events.postCreation, false);

		GL46.glDebugMessageCallback(FrameUtils.defaultDebugMessage, window.getID());
		//			return quit.get() > 0 && !window.shouldClose;

		Wrapper<Long> prev = new WrapperImpl<>();
		Delta delta = new Delta();
		while (quit.get() > 0 && !window.shouldClose && !Thread.currentThread().isInterrupted() && !window.hasClosed && !GLFW.glfwWindowShouldClose(window.getID())) {
			///Time management
			if (true) {
				long curr = System.nanoTime();
				delta.nanos = curr - prev.getWrappedOrDefault(curr);
				delta.seconds = FrameUtils.nanosToSeconds(delta.nanos);
				delta.totalNanos += delta.nanos;

				prev.setWrapped(curr);
			}
			FrameUtils.iterateRunnable(window.events.frameStart, false);
			glClearColor(window.clearColour.x, window.clearColour.y, window.clearColour.z, window.clearColour.w);
			FrameUtils.iterateRunnable(window.events.preClear, false);
			glClear(window.clearType);

			FrameUtils.iterateTickable(window.events.render, delta);
			try (MemoryStack stack = stackPush()) {
				glViewport(0, 0, window.frameBufferSizeX, window.frameBufferSizeY);
			}

			glfwSwapBuffers(window.getID());
			FrameUtils.iterateRunnable(window.events.frameEnd, false);

			frameTimeManager.run();
			if (runnables != null) {
				FrameUtils.iterateRunnable(runnables, false);
			}

		}

		logger.debug("Closing thread with name '" + Thread.currentThread().getName() + "'");

		FrameUtils.iterateRunnable(window.events.preCleanup, false);
		GLFW.glfwSetWindowShouldClose(window.getID(), true);
		window.shouldClose = true;

	}
}

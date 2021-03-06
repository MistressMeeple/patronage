package com.meeple.citybuild.client.render;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;
import org.joml.FrustumIntersection;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL46;

import com.meeple.citybuild.RayHelper;
import com.meeple.citybuild.client.CityBuilderMain;
import com.meeple.citybuild.client.render.WorldRenderer.MeshExt;
import com.meeple.citybuild.server.Entity;
import com.meeple.citybuild.server.LevelData;
import com.meeple.citybuild.server.LevelData.Chunk;
import com.meeple.citybuild.server.LevelData.Chunk.Tile;
import com.meeple.citybuild.server.WorldGenerator.TileTypes;
import com.meeple.citybuild.server.WorldGenerator.Tiles;
import com.meeple.shared.CollectionSuppliers;
import com.meeple.shared.Tickable;
import com.meeple.shared.frame.CursorHelper;
import com.meeple.shared.frame.CursorHelper.SpaceState;
import com.meeple.shared.frame.FrameUtils;
import com.meeple.shared.frame.OGL.KeyInputSystem;
import com.meeple.shared.frame.OGL.ShaderProgram;
import com.meeple.shared.frame.OGL.ShaderProgram.Attribute;
import com.meeple.shared.frame.OGL.ShaderProgram.GLDrawMode;
import com.meeple.shared.frame.OGL.ShaderProgram.GLShaderType;
import com.meeple.shared.frame.OGL.ShaderProgramSystem;
import com.meeple.shared.frame.OGL.UniformManager;
import com.meeple.shared.frame.camera.VPMatrixSystem;
import com.meeple.shared.frame.camera.VPMatrixSystem.ProjectionMatrixSystem;
import com.meeple.shared.frame.camera.VPMatrixSystem.ProjectionMatrixSystem.ProjectionMatrix;
import com.meeple.shared.frame.camera.VPMatrixSystem.VPMatrix;
import com.meeple.shared.frame.camera.VPMatrixSystem.ViewMatrixSystem.CameraSpringArm;
import com.meeple.shared.frame.nuklear.NkContextSingleton;
import com.meeple.shared.frame.wrapper.Wrapper;
import com.meeple.shared.frame.wrapper.WrapperImpl;

public class LevelRenderer {
	public static Logger logger = Logger.getLogger(LevelRenderer.class);

	static class CubeMesh {
		Attribute colourAttrib = new Attribute();
		Attribute translationAttrib = new Attribute();
	}

	public static boolean disableAlphaTest = false;

	public UniformManager<String[], Integer[]>.Uniform<VPMatrix> setupWorldProgram(ShaderProgram program, VPMatrixSystem VPMatrixSystem, VPMatrix vpMatrix) {
		UniformManager<String[], Integer[]>.Uniform<VPMatrix> u = ShaderProgramSystem.multiUpload.register(new String[] { "vpMatrix", "projectionMatrix", "viewMatrix" }, VPMatrixSystem);

		ShaderProgramSystem.addUniform(program, ShaderProgramSystem.multiUpload, u);
		ShaderProgramSystem.queueUniformUpload(program, ShaderProgramSystem.multiUpload, u, vpMatrix);

		program.shaderSources.put(GLShaderType.VertexShader, ShaderProgramSystem.loadShaderSourceFromFile(("resources/shaders/line3D.vert")));
		program.shaderSources.put(GLShaderType.FragmentShader, ShaderProgramSystem.loadShaderSourceFromFile(("resources/shaders/basic-alpha-discard-colour.frag")));

		try {
			ShaderProgramSystem.create(program);
		} catch (Exception err) {
			err.printStackTrace();
		}
		return u;
	}

	public UniformManager<String, Integer>.Uniform<ProjectionMatrix> setupUIProgram(ShaderProgram program, ProjectionMatrixSystem pSystem, ProjectionMatrix pMatrix) {

		UniformManager<String, Integer>.Uniform<ProjectionMatrix> u = ShaderProgramSystem.singleUpload.register("projectionMatrix", pSystem);
		ShaderProgramSystem.addUniform(program, ShaderProgramSystem.singleUpload, u);
		ShaderProgramSystem.queueUniformUpload(program, ShaderProgramSystem.singleUpload, u, pMatrix);

		program.shaderSources.put(GLShaderType.VertexShader, ShaderProgramSystem.loadShaderSourceFromFile(("resources/shaders/line2D-UI.vert")));
		program.shaderSources.put(GLShaderType.FragmentShader, ShaderProgramSystem.loadShaderSourceFromFile(("resources/shaders/basic-alpha-discard-colour.frag")));

		try {
			ShaderProgramSystem.create(program);
		} catch (Exception err) {
			// TODO Auto-generated catch block
			err.printStackTrace();
		}
		return u;

	}

	public UniformManager<String[], Integer[]>.Uniform<VPMatrix> setupMainProgram(ShaderProgram program, VPMatrixSystem VPMatrixSystem, VPMatrix vpMatrix) {
		UniformManager<String[], Integer[]>.Uniform<VPMatrix> u = ShaderProgramSystem.multiUpload.register(new String[] { "vpMatrix", "projectionMatrix", "viewMatrix" }, VPMatrixSystem);

		ShaderProgramSystem.addUniform(program, ShaderProgramSystem.multiUpload, u);
		ShaderProgramSystem.queueUniformUpload(program, ShaderProgramSystem.multiUpload, u, vpMatrix);

		program.shaderSources.put(GLShaderType.VertexShader, ShaderProgramSystem.loadShaderSourceFromFile(("resources/shaders/3D-unlit.vert")));
		program.shaderSources.put(GLShaderType.FragmentShader, ShaderProgramSystem.loadShaderSourceFromFile(("resources/shaders/basic-alpha-discard-colour.frag")));

		try {
			ShaderProgramSystem.create(program);
		} catch (Exception err) {

			err.printStackTrace();
		}
		return u;
	}

	public void setupLitProgram(ShaderProgram program, int maxLights, int maxMaterials) {
		String fragSource = ShaderProgramSystem.loadShaderSourceFromFile(("resources/shaders/lighting.frag"));
		fragSource = fragSource.replaceAll("\\{maxmats\\}", "" + maxMaterials);
		fragSource = fragSource.replaceAll("\\{maxlights\\}", maxLights + "");
		String vertSource = ShaderProgramSystem.loadShaderSourceFromFile(("resources/shaders/lighting.vert"));
		vertSource = vertSource.replaceAll("\\{maxlights\\}", maxLights + "");
		program.shaderSources.put(GLShaderType.VertexShader, vertSource);
		program.shaderSources.put(GLShaderType.FragmentShader, fragSource);
		try {
			ShaderProgramSystem.create(program);
		} catch (Exception err) {
			// TODO Auto-generated catch block
			err.printStackTrace();
		}

	}

	public void preRender(LevelData level, VPMatrix vp, ShaderProgram program) {
		FrustumIntersection fi = new FrustumIntersection(vp.cache);

		Set<Entry<Vector2i, Chunk>> set = level.chunks.entrySet();
		synchronized (level.chunks) {
			for (Iterator<Entry<Vector2i, Chunk>> i = set.iterator(); i.hasNext();) {
				Entry<Vector2i, Chunk> entry = i.next();
				Vector2i loc = entry.getKey();
				Chunk chunk = entry.getValue();
				Vector3f chunkPos = new Vector3f(loc.x * LevelData.fullChunkSize, loc.y * LevelData.fullChunkSize, 0);
				MeshExt m = baked.get(chunk);
				if (m == null || chunk.rebake.getAndSet(false)) {
					if (m != null) {
						m.mesh.singleFrameDiscard = true;
					}
					m = bakeChunk(chunkPos, chunk);
					ShaderProgramSystem.loadVAO(program, m.mesh);
					m.mesh.visible = false;
					baked.put(chunk, m);
				}
				switch (fi.intersectAab(chunkPos, chunkPos.add(LevelData.fullChunkSize, LevelData.fullChunkSize, 0, new Vector3f()))) {

					case FrustumIntersection.INSIDE:
					case FrustumIntersection.INTERSECT:
						m.mesh.visible = true;
						//render chunk
						break;
					case FrustumIntersection.OUTSIDE:
						m.mesh.visible = false;
						break;
					default:
						break;
				}

			}
		}
		drawAxis(program);

	}

	Map<Chunk, MeshExt> baked = new CollectionSuppliers.MapSupplier<Chunk, MeshExt>().get();
	Map<TileTypes, Map<String, MeshExt>> tileMeshes = new CollectionSuppliers.MapSupplier<TileTypes, Map<String, MeshExt>>().get();

	private void bakeTile(Tile tile) {
		switch (tile.type) {

		}
	}

	private MeshExt bakeChunk(Vector3f chunkPos, Chunk chunk) {
		MeshExt m = new MeshExt();

		WorldRenderer.setupDiscardMesh3D(m, 4);
		m.mesh.modelRenderType = GLDrawMode.TriangleFan;
		m.mesh.name = "chunk_" + (int) chunkPos.x + "_" + (int) chunkPos.y;
		m.mesh.renderCount = 0;

		m.positionAttrib.data.add(0f);
		m.positionAttrib.data.add(0f);
		m.positionAttrib.data.add(0f);

		m.positionAttrib.data.add(LevelData.tileSize);
		m.positionAttrib.data.add(0f);
		m.positionAttrib.data.add(0f);

		m.positionAttrib.data.add(LevelData.tileSize);
		m.positionAttrib.data.add(LevelData.tileSize);
		m.positionAttrib.data.add(0f);

		m.positionAttrib.data.add(0f);
		m.positionAttrib.data.add(LevelData.tileSize);
		m.positionAttrib.data.add(0f);

		//TODO bake chunk instead
		for (int x = 0; x < chunk.tiles.length; x++) {
			for (int y = 0; y < chunk.tiles[x].length; y++) {
				Vector3f tilePos = chunkPos.add(x * LevelData.tileSize, y * LevelData.tileSize, 0, new Vector3f());
				Vector4f colour = new Vector4f();
				Tile tile = chunk.tiles[x][y];
				if (tile == null) {
					chunk.tiles[x][y] = chunk.new Tile();
					tile = chunk.tiles[x][y];
				}
				if (tile.type == null) {
					tile.type = Tiles.Hole;
				}

				switch (tile.type) {
					case Hole:

						break;
					case Ground:

						colour = new Vector4f(0.1f, 1f, 0.1f, 1f);
						FrameUtils.appendToList(m.offsetAttrib.data, tilePos);
						m.colourAttrib.data.add(colour.x);
						m.colourAttrib.data.add(colour.y);
						m.colourAttrib.data.add(colour.z);
						m.colourAttrib.data.add(colour.w);
						m.mesh.renderCount += 1;
						break;

				}

			}
		}
		return m;
	}

	private void drawAxis(ShaderProgram program) {

		{

			Vector4f colour = new Vector4f(1, 0, 0, 1);
			MeshExt m = new MeshExt();
			WorldRenderer.setupDiscardMesh3D(m, 2);

			m.positionAttrib.data.add(0f);
			m.positionAttrib.data.add(0f);
			m.positionAttrib.data.add(0f);

			m.positionAttrib.data.add(100f);
			m.positionAttrib.data.add(0f);
			m.positionAttrib.data.add(0f);

			m.colourAttrib.data.add(colour.x);
			m.colourAttrib.data.add(colour.y);
			m.colourAttrib.data.add(colour.z);
			m.colourAttrib.data.add(colour.w);
			FrameUtils.appendToList(m.offsetAttrib.data, new Vector3f());
			m.mesh.name = "modelr";
			m.mesh.modelRenderType = GLDrawMode.Line;
			ShaderProgramSystem.loadVAO(program, m.mesh);
		}
		{

			Vector4f colour = new Vector4f(0, 1, 0, 1);
			MeshExt m = new MeshExt();
			WorldRenderer.setupDiscardMesh3D(m, 2);

			m.positionAttrib.data.add(0f);
			m.positionAttrib.data.add(0f);
			m.positionAttrib.data.add(0f);

			m.positionAttrib.data.add(0f);
			m.positionAttrib.data.add(100f);
			m.positionAttrib.data.add(0f);

			m.colourAttrib.data.add(colour.x);
			m.colourAttrib.data.add(colour.y);
			m.colourAttrib.data.add(colour.z);
			m.colourAttrib.data.add(colour.w);
			FrameUtils.appendToList(m.offsetAttrib.data, new Vector3f());
			m.mesh.name = "modelg";
			m.mesh.modelRenderType = GLDrawMode.Line;
			ShaderProgramSystem.loadVAO(program, m.mesh);
		}
		{

			Vector4f colour = new Vector4f(0, 0, 1, 1);
			MeshExt m = new MeshExt();
			WorldRenderer.setupDiscardMesh3D(m, 2);

			m.positionAttrib.data.add(0f);
			m.positionAttrib.data.add(0f);
			m.positionAttrib.data.add(0f);

			m.positionAttrib.data.add(0f);
			m.positionAttrib.data.add(0f);
			m.positionAttrib.data.add(100f);

			m.colourAttrib.data.add(colour.x);
			m.colourAttrib.data.add(colour.y);
			m.colourAttrib.data.add(colour.z);
			m.colourAttrib.data.add(colour.w);
			FrameUtils.appendToList(m.offsetAttrib.data, new Vector3f());
			m.mesh.name = "modelb";
			m.mesh.modelRenderType = GLDrawMode.Line;
			ShaderProgramSystem.loadVAO(program, m.mesh);
		}
	}

	public Tickable renderGame(CityBuilderMain cityBuilder, VPMatrix vpMatrix, Entity cameraAnchorEntity, ProjectionMatrix ortho, RayHelper rh, KeyInputSystem keyInput, NkContextSingleton nkContext) {

		//		ShaderProgram mainProgram = new ShaderProgram();
		ShaderProgram program = new ShaderProgram();
		ShaderProgram uiProgram = new ShaderProgram();

		VPMatrixSystem vpSystem = new VPMatrixSystem();

		Wrapper<UniformManager<String[], Integer[]>.Uniform<VPMatrix>> puW = new WrapperImpl<>();
		Wrapper<UniformManager<String, Integer>.Uniform<ProjectionMatrix>> uipuW = new WrapperImpl<>();

		vpMatrix.proj.getWrapped().window = cityBuilder.window;
		vpMatrix.proj.getWrapped().FOV = 90;
		vpMatrix.proj.getWrapped().nearPlane = 0.001f;
		vpMatrix.proj.getWrapped().farPlane = 10000f;
		vpMatrix.proj.getWrapped().orthoAspect = 10f;
		vpMatrix.proj.getWrapped().perspectiveOrOrtho = true;
		vpMatrix.proj.getWrapped().scale = 1f;

		ortho.window = cityBuilder.window;
		ortho.FOV = 90;
		ortho.nearPlane = 0.001f;
		ortho.farPlane = 10000f;
		ortho.orthoAspect = 10f;
		ortho.perspectiveOrOrtho = false;
		ortho.scale = 1f;
		CameraSpringArm arm = vpMatrix.view.getWrapped().springArm;
		cityBuilder.window.events.postCreation.add(() -> {

			puW.setWrapped(setupWorldProgram(program, vpSystem, vpMatrix));
			uipuW.setWrapped(setupUIProgram(uiProgram, vpSystem.projSystem, ortho));

			/*mpuW.setWrapped(levelRenderer.setupMainProgram(mainProgram, vpSystem, vpMatrix));
			ShaderProgramSystem.loadVAO(mainProgram, cube);*/

		});

		vpSystem.preMult(vpMatrix);

		cityBuilder.gameUI.init(cityBuilder.window, vpMatrix, ortho, rh);
		return (time) -> {
			vpSystem.preMult(vpMatrix);
			ShaderProgramSystem.queueUniformUpload(program, ShaderProgramSystem.multiUpload, puW.getWrapped(), vpMatrix);
			//TODO change line thickness
			GL46.glLineWidth(3f);
			GL46.glPointSize(3f);
			keyInput.tick(cityBuilder.window.mousePressTicks, cityBuilder.window.mousePressMap, time.nanos);
			keyInput.tick(cityBuilder.window.keyPressTicks, cityBuilder.window.keyPressMap, time.nanos);
			if (cityBuilder.level != null) {
				//TODO better testing for if mouse controls should be enabled. eg when over a gui

				cityBuilder.gameUI.handlePanningTick(cityBuilder.window, ortho, vpMatrix.view.getWrapped(), cameraAnchorEntity);
				cityBuilder.gameUI.handlePitchingTick(cityBuilder.window, ortho, arm);
				cityBuilder.gameUI.handleScrollingTick(arm);
				long mouseLeftClick = cityBuilder.window.mousePressTicks.getOrDefault(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0l);
				if (mouseLeftClick > 0) {
					Vector4f cursorRay = CursorHelper.getMouse(SpaceState.World_Space, cityBuilder.window, vpMatrix.proj.getWrapped(), vpMatrix.view.getWrapped());
					rh.update(new Vector3f(cursorRay.x, cursorRay.y, cursorRay.z), new Vector3f(vpMatrix.view.getWrapped().position), cityBuilder);

				}

				//TODO level clear colour
				cityBuilder.window.clearColour.set(0f, 0f, 0f, 0f);
				preRender(cityBuilder.level, vpMatrix, program);
				cityBuilder.gameUI.preRenderMouseUI(cityBuilder.window, ortho, uiProgram);

				//				MeshExt mesh = new MeshExt();
				//				bakeChunk(level.chunks.get(new Vector2i()), mesh);
				//				ShaderProgramSystem.loadVAO(program, mesh.mesh);

			}

			ShaderProgramSystem.render(program);
			ShaderProgramSystem.render(uiProgram);
			//this is the cube test rendering program
			//						ShaderProgramSystem.render(mainProgram);
			return false;
		};
	}
}

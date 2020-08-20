package com.meeple.backend.view;

import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL31.glUniformBlockBinding;

import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL46;

import com.meeple.shared.frame.OGL.GLContext;
import com.meeple.shared.frame.OGL.ShaderProgram;

public class VPMatrix {

	public final class CameraKey {
		/**
		 * Constructor is hidden. Use {@link VPMatrix#newCamera()} instead.
		 */
		private CameraKey() {
		}
	}

	public static final int Default_VP_Matrix_Binding_Point = 2;
	private int matrixBuffer;
	private int boundTo = Default_VP_Matrix_Binding_Point;

	private CameraKey activeCamera;
	private final Map<CameraKey, Matrix4f> cameras = new WeakHashMap<>();
	private transient final Map<CameraKey, Float> camerasSum = new WeakHashMap<>();
	private transient final Map<CameraKey, Vector3f> camerasPosition = new WeakHashMap<>();

	// limited use
	private Matrix4f projectionMatrix = new Matrix4f();
	// internal use only
	private Matrix4f viewProjectionMatrix = new Matrix4f();

	public Matrix4f getActiveCamera() {
		return cameras.get(activeCamera);
	}

	/**
	 * Returns the main camera matrix
	 * 
	 * @return matrix4f representing the camera
	 */
	public Matrix4f getCamera(CameraKey key) {
		return cameras.get(key);
	}

	/**
	 * Returns the main camera matrix with the additional operation of setting the
	 * camera as active
	 * 
	 * @return matrix4f representing the camera
	 */
	public Matrix4f getCamera(CameraKey key, boolean setActive) {
		if (setActive)
			activeCamera(key);
		return cameras.get(key);
	}

	/**
	 * Set the shapder programs binding point of a ViewProjection matrix
	 * 
	 * @param binding index to bind to
	 */
	public void setBindingPoint(int binding) {
		boundTo = binding;
	}

	/**
	 * Gets the shader programs binding point of the ViewProjection matrix
	 * 
	 * @return index that it is bound to
	 */
	public int getBindingPoint() {
		return boundTo;
	}

	public void setupBuffer(GLContext glContext) {

		this.matrixBuffer = glContext.genBuffer();

		glBindBuffer(GL46.GL_UNIFORM_BUFFER, matrixBuffer);
		glBufferData(
			GL_UNIFORM_BUFFER, 16 * 4 * 3, ShaderProgram.BufferUsage.DynamicDraw.getGLID());

		// binds the buffer to a binding index
		glBindBufferBase(GL_UNIFORM_BUFFER, boundTo, matrixBuffer);

	}

	public static void bindToProgram(int program, int bindingPoint) {
		glUseProgram(program);
		int actualIndex = GL46.glGetUniformBlockIndex(program, "Matrices");
		// binds the binding index to the interface block (by index)
		glUniformBlockBinding(program, actualIndex, VPMatrix.Default_VP_Matrix_Binding_Point);
	}

	public void setPerspective(float fov, float aspectRatio, float near, float far) {
		projectionMatrix.setPerspective(
			(float) Math.toRadians(fov), (float) aspectRatio, near, far);
		// NOTE invert either X or Y axis for my prefered coord system
		projectionMatrix.scale(-1, 1, 1);

	}

	public void activeCamera(CameraKey camera) {
		this.activeCamera = camera;
	}

	public Matrix4f getVPMatrix() {
		return viewProjectionMatrix;
	}

	public void upload() {
		Matrix4f activeCameraMatrix = cameras.get(activeCamera);
		Vector3f cameraPosition = getViewPosition(activeCameraMatrix, new Vector3f());
		projectionMatrix.mul(activeCameraMatrix, viewProjectionMatrix);
		writeVPFMatrix(matrixBuffer, projectionMatrix, activeCameraMatrix, cameraPosition, viewProjectionMatrix);
	}

	private static void writeVPFMatrix(int buffer, Matrix4f projection, Matrix4f view, Vector3f position, Matrix4f vp) {

		// no need to be in a program bidning, since this is shared between multiple
		// programs
		glBindBuffer(GL46.GL_UNIFORM_BUFFER, buffer);
		float[] store = new float[16];

		if (projection != null)
			glBufferSubData(GL46.GL_UNIFORM_BUFFER, 64 * 0, projection.get(store));
		if (view != null)
			glBufferSubData(GL46.GL_UNIFORM_BUFFER, 64 * 1, view.get(store));
		if (vp != null)
			glBufferSubData(GL46.GL_UNIFORM_BUFFER, 64 * 2, vp.get(store));
		if (position != null)
			glBufferSubData(GL46.GL_UNIFORM_BUFFER, 64 * 3, new float[] { position.x, position.y, position.z });
		glBindBuffer(GL46.GL_UNIFORM_BUFFER, 0);

	}

	public Vector3f getViewPosition(CameraKey camera) {
		Matrix4f cameraMatrix = cameras.get(camera);
		Float sum = sum(cameraMatrix);

		Float oldSum = camerasSum.get(camera);
		Vector3f position = new Vector3f();
		if (oldSum == null || Float.compare(oldSum, sum) != 0) {
			//recalculate
			getViewPosition(cameraMatrix, position);
			camerasSum.put(camera, sum);
			camerasPosition.put(camera, position);
		} else {
			position = camerasPosition.get(camera);
		}
		return position;

	}

	private static final float sum(Matrix4f matrix) {
		float sum = 0;
		float[] mat = matrix.get(new float[16]);
		for (float f : mat) {
			sum += f;
		}
		return sum;
	}

	private static Vector3f getViewPosition(Matrix4f view, Vector3f readInto) {
		Matrix4f clone2 = new Matrix4f(view);
		clone2.invert();
		clone2.getTranslation(readInto);
		return readInto;
	}

	public CameraKey newCamera() {
		CameraKey key = new CameraKey();
		cameras.put(key, new Matrix4f());
		if (activeCamera == null) {
			activeCamera = key;
		}
		return key;
	}
}

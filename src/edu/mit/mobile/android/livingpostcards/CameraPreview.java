package edu.mit.mobile.android.livingpostcards;

import java.io.IOException;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

/**
 * Camera preview, based on the Android example code.
 *
 * If the width isn't specified, will resize to keep the aspect ratio of the preview image. Sets the
 * preview size to the optimal size based on this view.
 *
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
	private static final String TAG = CameraPreview.class.getSimpleName();
	private final SurfaceHolder mHolder;
	private final Camera mCamera;

	@SuppressWarnings("deprecation")
	public CameraPreview(Context context, final Camera camera) {
		super(context);
		mCamera = camera;

		if (mCamera == null) {
			throw new NullPointerException("camera cannot be null");
		}

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		mHolder = getHolder();
		mHolder.addCallback(this);
		// deprecated setting, but required on Android versions prior to 3.0
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	public void surfaceCreated(SurfaceHolder holder) {
		// The Surface has been created, now tell the camera where to draw the preview.
		try {
			mCamera.setPreviewDisplay(holder);
			mCamera.startPreview();
		} catch (final IOException e) {
			Log.d(TAG, "Error setting camera preview: " + e.getMessage());
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		// empty. Take care of releasing the Camera preview in your activity.
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		if (mHolder.getSurface() == null) {
			// preview surface does not exist
			return;
		}

		// stop preview before making changes
		try {
			mCamera.stopPreview();
		} catch (final Exception e) {
			// ignore: tried to stop a non-existent preview
		}

		final Parameters p = mCamera.getParameters();

		final Camera.Size size = getBestPreviewSize(w, h, p);

		p.setPreviewSize(size.width, size.height);

		mCamera.setParameters(p);

		// start preview with new settings
		try {
			mCamera.setPreviewDisplay(mHolder);
			mCamera.startPreview();

		} catch (final Exception e) {
			Log.d(TAG, "Error starting camera preview: " + e.getMessage());
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

		// if width is specified exactly, we won't attempt to resize ourselves
		if (View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.EXACTLY) {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			return;
		}

		int w = View.MeasureSpec.getSize(widthMeasureSpec);
		final int h = View.MeasureSpec.getSize(heightMeasureSpec);

		final Parameters p = mCamera.getParameters();

		// the aspect ratio of the preview is often wrong. We need to use the aspect ratio of the
		// picture instead.
		final Camera.Size picSize = p.getPictureSize();

		// preserve aspect ratio
		w = (int) (((float) picSize.width / picSize.height) * h);

		setMeasuredDimension(w, h);
	}

	/***
	 * Copyright (c) 2008-2012 CommonsWare, LLC Licensed under the Apache License, Version 2.0 (the
	 * "License"); you may not use this file except in compliance with the License. You may obtain a
	 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required by
	 * applicable law or agreed to in writing, software distributed under the License is distributed
	 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	 * See the License for the specific language governing permissions and limitations under the
	 * License. From _The Busy Coder's Guide to Advanced Android Development_
	 * http://commonsware.com/AdvAndroid
	 */

	/**
	 * Finds the highest resolution preview size that fits within the given width and height.
	 *
	 * @param width
	 * @param height
	 * @param parameters
	 * @return
	 */
	public static Camera.Size getBestPreviewSize(int width, int height, Camera.Parameters parameters) {
		Camera.Size result = null;

		for (final Camera.Size size : parameters.getSupportedPreviewSizes()) {
			if (size.width <= width && size.height <= height) {
				if (result == null) {
					result = size;
				} else {
					final int resultArea = result.width * result.height;
					final int newArea = size.width * size.height;

					if (newArea > resultArea) {
						result = size;
					}
				}
			}
		}

		return result;
	}
}
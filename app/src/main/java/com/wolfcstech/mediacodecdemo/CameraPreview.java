package com.wolfcstech.mediacodecdemo;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

/**
 * Created by hanpfei0306 on 17-7-12.
 */

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback,
        Camera.PreviewCallback {
    private static final String TAG = "CameraPreview";

    private FrameListener mFrameListener;

    private SurfaceHolder mHolder;
    private Camera mCamera;
    private byte[] mPreviewBuffer = new byte[1280 * 960 * 3 / 2];
    private byte[] mPreviewRotationBuffer = new byte[1280 * 960 * 3 / 2];

    public CameraPreview(Context context, Camera camera) {
        super(context);
        mCamera = camera;

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
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.addCallbackBuffer(mPreviewBuffer);
        mCamera.setPreviewCallbackWithBuffer(this);
        mCamera.startPreview();
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null){
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.addCallbackBuffer(mPreviewBuffer);
            mCamera.setPreviewCallbackWithBuffer(this);
            mCamera.startPreview();
        } catch (Exception e){
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    public void setFrameListener(FrameListener frameListener) {
        mFrameListener = frameListener;
    }

    private byte[] rotateYUV420Degree90(byte[] data, int imageWidth, int imageHeight) {
        // Rotate the Y luma
        int i = 0;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = imageHeight - 1; y >= 0; y--) {
                mPreviewRotationBuffer[i] = data[y * imageWidth + x];
                i++;
            }
        }
        // Rotate the U and V color components
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            for (int y = 0; y < imageHeight / 2; y++) {
                mPreviewRotationBuffer[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i--;
                mPreviewRotationBuffer[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + (x - 1)];
                i--;
            }
        }
        return mPreviewRotationBuffer;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
//        Log.i(TAG, "onPreviewFrame, data length = " + data.length);
        camera.getParameters().getPreviewFormat();
        if (mFrameListener != null) {
            Camera.Size size = camera.getParameters().getPreviewSize();
            byte[] previewdata = rotateYUV420Degree90(data, size.width, size.height);
            mFrameListener.onFrame(previewdata, 0, previewdata.length, 0);
        }
        mCamera.addCallbackBuffer(mPreviewBuffer);
    }

    public interface FrameListener {
        void onFrame(byte[] buf, int offset, int length, int flag);
    }
}

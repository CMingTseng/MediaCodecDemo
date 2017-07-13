package com.wolfcstech.mediacodecdemo;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.widget.FrameLayout;

import java.io.IOException;
import java.nio.ByteBuffer;

public class CameraActivity extends Activity implements CameraPreview.FrameListener {
    private static final String TAG = "CameraActivity";

    static final int OUTPUT_WIDTH = 1280;
    static final int OUTPUT_HEIGHT = 960;

    String VIDEO_FORMAT = "video/avc";
    int VIDEO_FRAME_PER_SECOND = 30;
    int VIDEO_I_FRAME_INTERVAL = 10;
    int VIDEO_BITRATE = 3000 * 1000;

    private MediaCodec mEncoder;
    private MediaCodec mDecoder;

    private Camera mCamera;
    private CameraPreview mPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        createCodec();

        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        // Create an instance of Camera
        mCamera = getCameraInstance();

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        mPreview.setFrameListener(this);
        preview.addView(mPreview);
    }

    private void createCodec() {
        // video output dimension
        int mWidth = OUTPUT_WIDTH;
        int mHeight = OUTPUT_HEIGHT;

        // configure video output
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_FORMAT, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        format.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_PER_SECOND);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_I_FRAME_INTERVAL);

        try {
            mEncoder = MediaCodec.createEncoderByType(VIDEO_FORMAT);
        } catch (IOException e) {
            e.printStackTrace();
        }

        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        mEncoder.start();
    }

    /** Check if this device has a camera */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    /** A safe way to get an instance of the Camera object. */
    public Camera getCameraInstance() {
        Camera c = null;
        try {
            int numberOfCameras = Camera.getNumberOfCameras();
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            for (int i = 0; i < numberOfCameras; i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    c = Camera.open(i);
                }
            }

            setCameraDispalyOrientation(c, cameraInfo);

            Camera.Parameters parameters = c.getParameters();

//            List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
            int camWidth = 1280;
            int camHeight = 960;

            parameters.setFlashMode("off");
            parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            parameters.setPreviewFormat(ImageFormat.NV21);
            parameters.setRotation(Surface.ROTATION_0);
            parameters.setPictureSize(camWidth, camHeight);
            parameters.setPreviewSize(camWidth, camHeight);

            c.setDisplayOrientation(Surface.ROTATION_0);

            c.setParameters(parameters);
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    private void setCameraDispalyOrientation(Camera camera, Camera.CameraInfo cameraInfo) {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();

        int degree = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degree = 0;
                break;
            case Surface.ROTATION_90:
                degree = 90;
                break;
            case Surface.ROTATION_180:
                degree = 180;
                break;
            case Surface.ROTATION_270:
                degree = 270;
                break;
        }
        int result = (cameraInfo.orientation - degree + 360) % 360;
        result = (360 - result) % 360;
        camera.setDisplayOrientation(result);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCamera.release();
    }

    private void writeEncodedData(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo) {
//        if (frameListener != null)
//            frameListener.onFrame(outputBuffer, 0, length, flag);
    }

    @Override
    public void onFrame(byte[] buf, int offset, int length, int flag) {
        ByteBuffer[] inputBuffers = mEncoder.getInputBuffers();
        ByteBuffer[] outputBuffers = mEncoder.getOutputBuffers();

        int inputBufferIndex = mEncoder.dequeueInputBuffer(-1);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(buf, offset, length);
            mEncoder.queueInputBuffer(inputBufferIndex, 0, length, 0, 0);
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mEncoder.dequeueOutputBuffer(bufferInfo, 0);
        while (outputBufferIndex >= 0) {
            ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
            writeEncodedData(outputBuffer, bufferInfo);
            mEncoder.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = mEncoder.dequeueOutputBuffer(bufferInfo, 0);
        }
        Log.i(TAG, "onPreviewFrame, data length = " + length);
    }
}

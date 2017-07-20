package com.wolfcstech.mediacodecdemo;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CameraActivity extends Activity implements CameraPreview.FrameListener,
        SurfaceHolder.Callback, View.OnClickListener, TCPStreamReceiver.StreamReceivedListener {
    private static final String TAG = "CameraActivity";

    private static final String SAMPLE = Environment.getExternalStorageDirectory() + "/screen.mp4";

    static final int OUTPUT_WIDTH = 1280;
    static final int OUTPUT_HEIGHT = 960;

    String VIDEO_FORMAT = "video/avc";
    int VIDEO_FRAME_PER_SECOND = 30;
    int VIDEO_I_FRAME_INTERVAL = 10;
    int VIDEO_BITRATE = 3000 * 1000;

    private MediaCodec mEncoder;
    private MediaCodec mDecoder;
    private MediaExtractor mExtractor;
    private int mCount = 1;
    private long mTimeoutUs = 10000l;

    private Camera mCamera;
    private CameraPreview mPreview;
    private SurfaceView mDecodePreview;

    private StreamRequestTask mStreamRequestTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        createEncoder();

        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        // Create an instance of Camera
        mCamera = getCameraInstance();

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        mPreview.setFrameListener(this);
        preview.addView(mPreview);

        mDecodePreview = new SurfaceView(this);
        mDecodePreview.getHolder().addCallback(this);
        preview = (FrameLayout) findViewById(R.id.decode_preview);
        preview.addView(mDecodePreview);

        findViewById(R.id.button_capture).setOnClickListener(this);
    }

    private void createEncoder() {
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

    private void createDecoder(Surface surface) {
        try {
            mDecoder = MediaCodec.createDecoderByType(VIDEO_FORMAT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int mWidth = 1080;
        int mHeight = 800;
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(VIDEO_FORMAT, mWidth, mHeight);
        mDecoder.configure(mediaFormat, surface, null, 0);
        mDecoder.start();
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
            setCameraDisplayOrientation(c, cameraInfo);
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

            c.setParameters(parameters);
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    public void setCameraDisplayOrientation(Camera camera, Camera.CameraInfo cameraInfo) {
        int rotation = getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (cameraInfo.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (cameraInfo.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCamera.release();
    }

    public void decodeSample(byte[] data, int offset, int size, long presentationTimeUs, int flags) {
        int index = mDecoder.dequeueInputBuffer(mTimeoutUs);
        if (index >= 0) {
            ByteBuffer buffer;
            // since API 21 we have new API to use
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                buffer = mDecoder.getInputBuffers()[index];
                buffer.clear();
            } else {
                buffer = mDecoder.getInputBuffer(index);
            }
            if (buffer != null) {
                buffer.put(data, offset, size);
                mDecoder.queueInputBuffer(index, 0, size, presentationTimeUs, flags);
            }
        }
    }

    private void createMediaExtractorDecoder(Surface surface) {
        mExtractor = new MediaExtractor();
        try {
            mExtractor.setDataSource(SAMPLE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.i(TAG, "createMediaExtractorDecoder TrackCount = " + mExtractor.getTrackCount());
        for (int i = 0; i < mExtractor.getTrackCount(); i++) {
            MediaFormat format = mExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            Log.i(TAG, "createMediaExtractorDecoder mime = " + mime);
            if (mime.startsWith("video/")) {
                mExtractor.selectTrack(i);

                try {
                    mDecoder = MediaCodec.createDecoderByType(mime);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                mDecoder.configure(format, surface, null, 0);
                break;
            }
        }
        mDecoder.start();
    }

    private void createNetworkMediaExtractorDecoder(Surface surface, MediaDataSource dataSource) {
        mExtractor = new MediaExtractor();
        try {
            mExtractor.setDataSource(dataSource);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaCodec decoder = null;
        for (int i = 0; i < mExtractor.getTrackCount(); i++) {
            MediaFormat format = mExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                mExtractor.selectTrack(i);

                try {
                    decoder = MediaCodec.createDecoderByType(mime);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                decoder.configure(format, mDecodePreview.getHolder().getSurface(), null, 0);
                break;
            }
        }
        if (decoder != null) {
            decoder.start();
        }
        mDecoder = decoder;
    }

    private void decodeMediaExtractorSample() {
        ByteBuffer[] inputBuffers = mDecoder.getInputBuffers();
        ByteBuffer[] outputBuffers = mDecoder.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean isEOS = false;
        long startMs = System.currentTimeMillis();

        if (!isEOS) {
            int inIndex = mDecoder.dequeueInputBuffer(10000);
            if (inIndex >= 0) {
                ByteBuffer buffer = inputBuffers[inIndex];
                int sampleSize = mExtractor.readSampleData(buffer, 0);
                if (sampleSize < 0) {
                    // We shouldn't stop the playback at this point, just pass the EOS
                    // flag to decoder, we will get it again from the
                    // dequeueOutputBuffer
                    Log.i(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                    mDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    isEOS = true;
                } else {
                    mDecoder.queueInputBuffer(inIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);
                    mExtractor.advance();
                }
            }
        }

        int outIndex = mDecoder.dequeueOutputBuffer(info, 10000);
        if (outIndex >= 0) {
            ByteBuffer buffer = outputBuffers[outIndex];
//            Log.i(TAG, "We can't use this buffer but render it due to the API limit, " + buffer);
            mDecoder.releaseOutputBuffer(outIndex, true);
        }

        // All decoded frames have been rendered, we can stop playing now
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.i(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
        }

        if (isEOS) {
            mExtractor.release();
            mExtractor = new MediaExtractor();
            try {
                mExtractor.setDataSource(SAMPLE);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void writeEncodedData(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo) {
        if (mDecoder == null) {
            return;
        }
//        decodeMediaExtractorSample();

        // Ref http://blog.csdn.net/halleyzhang3/article/details/11473961
//        ByteBuffer[] inputBuffers = mDecoder.getInputBuffers();
//        int inputBufferIndex = mDecoder.dequeueInputBuffer(-1);
//        if (inputBufferIndex >= 0) {
//            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
//            inputBuffer.clear();
//            inputBuffer.put(outputBuffer);
//            mDecoder.queueInputBuffer(inputBufferIndex, 0, bufferInfo.size,
//                    mCount * 1000000 / VIDEO_FRAME_PER_SECOND, 0);
//            mCount++;
//        }
//
//        MediaCodec.BufferInfo decodeBufferInfo = new MediaCodec.BufferInfo();
//        int outputBufferIndex = mDecoder.dequeueOutputBuffer(decodeBufferInfo,0);
//        while (outputBufferIndex >= 0) {
//            mDecoder.releaseOutputBuffer(outputBufferIndex, true);
//            outputBufferIndex = mDecoder.dequeueOutputBuffer(decodeBufferInfo, 0);
//        }
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
    }

    @Override
    public void surfaceCreated(final SurfaceHolder holder) {
        createDecoder(holder.getSurface());
//        createMediaExtractorDecoder(holder.getSurface());


//        final NetworkMediaDataSource dataSource = new NetworkMediaDataSource();
//        mStreamRequestTask = new StreamRequestTask(dataSource);
//        mStreamRequestTask.execute();


        TCPStreamRequestTask task = new TCPStreamRequestTask();
        task.execute();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    @Override
    public void onClick(View v) {
        if (mStreamRequestTask != null) {
            mStreamRequestTask.stopStreamReceive();
            mStreamRequestTask = null;

            if (mExtractor != null) {
                mExtractor.release();
                mExtractor = null;
            }
            if (mDecoder != null) {
                mDecoder.stop();
                mDecoder.release();
                mDecoder = null;
            }
        } else {
            NetworkMediaDataSource dataSource = new NetworkMediaDataSource();
            mStreamRequestTask = new StreamRequestTask(dataSource);
            mStreamRequestTask.execute();
            createNetworkMediaExtractorDecoder(mDecodePreview.getHolder().getSurface(), dataSource);
        }
    }

    @Override
    public void onDataReceived(ByteBuffer byteBuffer) {
        int size = byteBuffer.limit() - byteBuffer.position();
        Log.i(TAG, "Receive stream data from video server " + size + " bytes this time");
        ByteBuffer[] inputBuffers = mDecoder.getInputBuffers();
        int inputBufferIndex = mDecoder.dequeueInputBuffer(-1);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(byteBuffer);
            mDecoder.queueInputBuffer(inputBufferIndex, 0, size,
                    mCount * 1000000 / VIDEO_FRAME_PER_SECOND, 0);
            mCount++;
        }

        mDecodePreview.postDelayed(new Runnable() {
            @Override
            public void run() {
                MediaCodec.BufferInfo decodeBufferInfo = new MediaCodec.BufferInfo();
                int outputBufferIndex = mDecoder.dequeueOutputBuffer(decodeBufferInfo, 0);
                while (outputBufferIndex >= 0) {
                    mDecoder.releaseOutputBuffer(outputBufferIndex, true);
                    outputBufferIndex = mDecoder.dequeueOutputBuffer(decodeBufferInfo, 0);
                }
            }
        }, 50);
    }

    private class TCPStreamRequestTask extends AsyncTask<Void, Void, Void> {
        private TCPStreamReceiver mStreamReceiver;

        public TCPStreamRequestTask() {
            mStreamReceiver = new TCPStreamReceiver();
            mStreamReceiver.setDataReceivedListener(CameraActivity.this);
        }

        @Override
        protected Void doInBackground(Void... params) {
//            mStreamReceiver.requestStreamData("10.240.252.17", 18960);
            mStreamReceiver.requestStreamData("10.242.24.174", 18960);
            return null;
        }

        public void stopStreamReceive() {
            mStreamReceiver.stop();
        }
    }

    private class StreamRequestTask extends AsyncTask<Void, Void, Void> {
        private StreamReceiver.StreamDataReceivedListener mDataReceivedListener;
        private StreamReceiver mStreamReceiver;

        public StreamRequestTask(StreamReceiver.StreamDataReceivedListener dataReceivedListener) {
            mDataReceivedListener = dataReceivedListener;
            mStreamReceiver = new StreamReceiver();
            mStreamReceiver.setDataReceivedListener(mDataReceivedListener);
        }

        @Override
        protected Void doInBackground(Void... params) {
            mStreamReceiver.requestStreamData("10.240.252.17", 18960);
            return null;
        }

        public void stopStreamReceive() {
            mStreamReceiver.stop();
        }
    }

    private class NetworkMediaDataSource extends MediaDataSource implements
            StreamReceiver.StreamDataReceivedListener {
        private long dataTotalLen = 0;

        private MessageDigest mMD5Digest;


        private byte[] mMediaData;

        private NetworkMediaDataSource() {
            mMediaData = new byte[5000000];

            try {
                mMD5Digest = MessageDigest.getInstance("MD5");

            } catch (NoSuchAlgorithmException e) {
            }
        }

        @Override
        public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
            int readLen;
            if (position >= getSize()) {
                readLen = -1;
            } else if (position < dataTotalLen && position + size >= dataTotalLen) {
                readLen = (int) (dataTotalLen - position);
            } else {
                readLen = size;
            }
            if (readLen > 0) {
                System.arraycopy(mMediaData, (int) position, buffer, offset, readLen);
            }
//            Log.i(TAG, "Packet read length = " + dataTotalLen + " position = " + position
//                    + " size " + size + " readLen = " + readLen);
            return readLen;
        }

        private char[] sHexChar = { '0', '1', '2', '3', '4', '5', '6', '7',
                '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

        public String toHexString(byte[] b) {
            StringBuilder sb = new StringBuilder(b.length * 2);
            for (int i = 0; i < b.length; i++) {
                sb.append(sHexChar[(b[i] & 0xf0) >>> 4]);
                sb.append(sHexChar[b[i] & 0x0f]);
            }
            return sb.toString();
        }

        @Override
        public long getSize() throws IOException {
            return 1967007;
        }

        @Override
        public void close() throws IOException {

        }

        private void testRead() {
            byte[] buffer = new byte[4096];
            int pos = 0;
            int readlen;
            MessageDigest mReadMD5Digest = null;
            try {
                mReadMD5Digest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
            }
            try {
                while ((readlen = readAt(pos, buffer, 0, 4096)) >= 0) {
                    mReadMD5Digest.update(buffer, 0, readlen);
                    pos += readlen;
                }
                byte[] digest = mReadMD5Digest.digest();
                String md5 = toHexString(digest);
                Log.i(TAG, "Read data md5 = " + md5);
            } catch (IOException e) {
            }

        }

        @Override
        public void onStreamDataReceived(int index, byte[] data, int offset, int size) {
            System.arraycopy(data, offset, mMediaData, (int) dataTotalLen, size);
            dataTotalLen += size;

            if (dataTotalLen % 700000 == 0) {
                Log.i(TAG, "Recv packet length = " + dataTotalLen + " index = " + index);
            }
            try {
                if (dataTotalLen >= getSize()) {
                    // || size != 1400
                    Log.i(TAG, "Recv packet length = " + dataTotalLen + " index = " + index);
                    mMD5Digest.update(mMediaData, 0, (int) dataTotalLen);
                    byte[] digest = mMD5Digest.digest();
                    String md5 = toHexString(digest);
                    Log.i(TAG, "Recv data md5 = " + md5);

                    createNetworkMediaExtractorDecoder(mDecodePreview.getHolder().getSurface(), this);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

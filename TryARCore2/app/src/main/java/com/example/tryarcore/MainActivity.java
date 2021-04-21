package com.example.tryarcore;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
//import com.google.ar.core.ImageFormat;
import android.graphics.ImageFormat;
import android.util.Size;

import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.math.Matrix;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;

import com.google.ar.sceneform.ux.ArFragment;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "position";

    ArFragment arFragment;
    private Handler mHandler = new Handler();
    private int i = 0;

    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        arFragment.setOnSessionInitializationListener(this::OnSessionInitialization);
        mHandler.removeCallbacks(badTimeUpdater);
        mHandler.postDelayed(badTimeUpdater, 100);
    }

    private void OnSessionInitialization(Session session){
        Size selectedSize = new Size(0, 0);
        CameraConfig selectedCameraConfig = session.getCameraConfig();
        Config config = new Config(session);

        CameraConfigFilter filter = new CameraConfigFilter(session);
        List<CameraConfig> cameraConfigsList = session.getSupportedCameraConfigs(filter);
        for (CameraConfig currentCameraConfig : cameraConfigsList) {
            Size cpuImageSize = currentCameraConfig.getImageSize();
            Size gpuTextureSize = currentCameraConfig.getTextureSize();
            Log.i(TAG, "CurrentCameraConfig CPU image size:" + cpuImageSize + " GPU texture size:" + gpuTextureSize);
            Log.i(TAG, "" + cpuImageSize.getWidth());
            if ((gpuTextureSize.equals(selectedCameraConfig.getTextureSize()) && (cpuImageSize.getWidth()) > selectedSize.getWidth())) {
                selectedSize = cpuImageSize;
                Log.i(TAG, "" + selectedSize);
                selectedCameraConfig = currentCameraConfig;
            }
        }

        Log.i(TAG, "Selected CameraConfig CPU image size:" + selectedCameraConfig.getImageSize() + " GPU texture size:" + selectedCameraConfig.getTextureSize());
        session.setCameraConfig(selectedCameraConfig);

        session.configure(config);
    }

    private Runnable badTimeUpdater = new Runnable() {

        @Override
        public void run() {

            Frame currentFrame = arFragment.getArSceneView().getArFrame();

            try{
                float[] f = currentFrame.getCamera().getImageIntrinsics().getFocalLength();
                float[] c = currentFrame.getCamera().getImageIntrinsics().getPrincipalPoint();
                //Log.i(TAG, Float.toString(c[0]) + " " + Float.toString(c[1]));
                //Log.i(TAG, Float.toString(f[0]) + " " +Float.toString(f[1]));
            } catch (NullPointerException e) {
                e.printStackTrace();
            }

            Pose pose = null;
            Image currentImage = null;
            try {
                if (currentFrame != null) {
                    currentImage = currentFrame.acquireCameraImage();

                    pose = currentFrame.getCamera().getPose();

                    float[] orientation = pose.getRotationQuaternion();
                    float[] position = pose.getTranslation();

                    File file_pic = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                            i + ".jpg");

                    byte[] data = imageToByteArray(currentImage);
                    writeFrame(file_pic, data);
                    currentImage.close();

                    File file_pose = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                            i + ".txt");

                    writeNewPose(file_pose, position, orientation);
                    i+=1;
                    Log.i(TAG,"" + i);
                }

            } catch (NotYetAvailableException e) {
                e.printStackTrace();
            }


            mHandler.postDelayed(this, 500);
        }



        private byte[] imageToByteArray(Image image) {
            byte[] data = null;
            if (image.getFormat() == ImageFormat.JPEG) {
                Image.Plane[] planes = image.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();
                data = new byte[buffer.capacity()];
                buffer.get(data);
                return data;
            } else if (image.getFormat() == ImageFormat.YUV_420_888) {
                data = NV21toJPEG(
                        YUV_420_888toNV21(image),
                        image.getWidth(), image.getHeight());
            }
            return data;
        }


        private byte[] YUV_420_888toNV21(Image image) {
            byte[] nv21;
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer vuBuffer = image.getPlanes()[2].getBuffer();

            int ySize = yBuffer.remaining();
            int vuSize = vuBuffer.remaining();

            nv21 = new byte[ySize + vuSize];

            yBuffer.get(nv21, 0, ySize);
            vuBuffer.get(nv21, ySize, vuSize);

            return nv21;
        }

        private byte[] NV21toJPEG(byte[] nv21, int width, int height) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
            yuv.compressToJpeg(new Rect(0, 0, width, height), 100, out);
            return out.toByteArray();
        }


        public void writeFrame(File fileName, byte[] data) {
            try {
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(fileName));
                bos.write(data);
                bos.flush();
                bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        private void writeNewPose(File file_pose, float[] cameraPosition, float[] cameraOrientation) {
            try{
                FileWriter writer = new FileWriter(file_pose);
                writer.append(Float.toString(cameraPosition[0]) + ' ' + Float.toString(cameraPosition[1]) + ' ' + Float.toString(cameraPosition[2]) + '\n');
                writer.append(Float.toString(cameraOrientation[3]) + ' ' + Float.toString(cameraOrientation[0]) + ' ' + Float.toString(cameraOrientation[1]) + ' ' + Float.toString(cameraOrientation[2]));
                writer.flush();
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }


    };
}
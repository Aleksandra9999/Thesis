package com.example.tryarcore;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Frame;
//import com.google.ar.core.ImageFormat;
import android.graphics.ImageFormat;

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
        mHandler.removeCallbacks(badTimeUpdater);
        mHandler.postDelayed(badTimeUpdater, 100);
    }

    private Runnable badTimeUpdater = new Runnable() {

        @Override
        public void run() {
            //Vector3 cameraPosition = arFragment.getArSceneView().getScene().getCamera().getWorldPosition();
            //Quaternion cameraOrientation = arFragment.getArSceneView().getScene().getCamera().getWorldRotation();

            Frame currentFrame = arFragment.getArSceneView().getArFrame();
            Camera cam = arFragment.getArSceneView().getScene().getCamera();


            try{
                float[] f = currentFrame.getCamera().getImageIntrinsics().getFocalLength();
                float[] c = currentFrame.getCamera().getImageIntrinsics().getPrincipalPoint();
                Log.i(TAG, Float.toString(f[0]*1920/640) +" 0.0 " + Float.toString(c[0]*1920/640) + "\n");
                Log.i(TAG,"0.0 " + Float.toString(f[1]*1080/480) + " " + Float.toString(c[1]*1080/480) + "\n");
                Log.i(TAG,"0.0 0.0 1.0");
            } catch (NullPointerException e) {
                e.printStackTrace();
            }

            Pose pose = null;
            Image currentImage = null;
            try {
                if (currentFrame != null) {
                    currentImage = currentFrame.acquireCameraImage();

                    pose = currentFrame.getAndroidSensorPose();
                    pose = normalizePose(pose);

                    float[] orientation = pose.getRotationQuaternion();
                    float[] position = pose.getTranslation();
                    //Log.i(TAG, Float.toString(orientation[0]));

                    float[] planeMatrix = new float[16];
                    pose.toMatrix(planeMatrix, 0);



                    File file_pic = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                            i + ".jpg");

                    byte[] data = imageToByteArray(currentImage);
                    writeFrame(file_pic, data);
                    currentImage.close();

                    File file_pose = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                            i + ".txt");

                    writeNewPose(file_pose, position, orientation);

                }

            } catch (NotYetAvailableException e) {
                e.printStackTrace();
            }


            //Log.i(TAG, cameraOrientation.toString());
            i+=1;

            mHandler.postDelayed(this, 500);
        }

        private Pose normalizePose(Pose pose) {
                Pose phoneRot = Pose.makeRotation(0.5f, -0.5f, 0.5f, -0.5f);
                Pose convertCoords = Pose.makeRotation(0.7071068f, 0f, 0f, 0.7071068f);
                return convertCoords.compose(pose.compose(phoneRot));
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

        private void writePose(File file_pose, Vector3 cameraPosition, Quaternion cameraOrientation) {
            try{
                FileWriter writer = new FileWriter(file_pose);
                writer.append(Float.toString(cameraPosition.x) + ' ' + Float.toString(cameraPosition.y) + ' ' + Float.toString(cameraPosition.z) + '\n');
                writer.append(Float.toString(cameraOrientation.w) + ' ' + Float.toString(cameraOrientation.x) + ' ' + Float.toString(cameraOrientation.y) + ' ' + Float.toString(cameraOrientation.z));
                writer.flush();
                writer.close();
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
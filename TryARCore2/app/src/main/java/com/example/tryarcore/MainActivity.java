package com.example.tryarcore;

import androidx.appcompat.app.AppCompatActivity;

import android.media.Image;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import com.google.ar.core.Frame;
//import com.google.ar.core.ImageFormat;
import android.graphics.ImageFormat;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;

import com.google.ar.sceneform.ux.ArFragment;

import java.io.File;
import java.io.FileOutputStream;
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
            Vector3 cameraPosition = arFragment.getArSceneView().getScene().getCamera().getWorldPosition();
            Quaternion cameraOrientation = arFragment.getArSceneView().getScene().getCamera().getWorldRotation();

            //camera = arFragment.getArSceneView().getScene().getCamera();

            Frame currentFrame = arFragment.getArSceneView().getArFrame();
            Image currentImage = null;
            try {
                if (currentFrame != null) {
                    currentImage = currentFrame.acquireCameraImage();

                    byte[] data = getJpegFromImage(currentImage);
                    currentImage.close();

                    File file = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                            "picture"+ i + ".jpg");
                    OutputStream os = null;
                    try {
                        os = new FileOutputStream(file);
                        os.write(data);
                        os.close();
                        i+=1;
                        Log.i(TAG, "Image saved");

                    } catch (IOException e) {
                        Log.w(TAG, "Cannot write to " + file, e);
                    } finally {
                        if (os != null) {
                            try {
                                os.close();
                            } catch (IOException e) {
                                // Ignore
                            }
                        }
                    }


                }

            } catch (NotYetAvailableException e) {
                e.printStackTrace();
            }

            Log.i(TAG, cameraPosition.toString());
            Log.i(TAG, cameraOrientation.toString());

            mHandler.postDelayed(this, 1000);
        }

        private byte[] getJpegFromImage(Image image) {
            Image.Plane[] planes = image.getPlanes();

            ByteBuffer buffer0 = planes[0].getBuffer();
            ByteBuffer buffer1 = planes[1].getBuffer();
            ByteBuffer buffer2 = planes[2].getBuffer();

            int offset = 0;

            int width = image.getWidth();
            int height = image.getHeight();

            Log.i(TAG, Integer.toString(width));
            Log.i(TAG, Integer.toString(height));


            byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
            byte[] rowData1 = new byte[planes[1].getRowStride()];
            byte[] rowData2 = new byte[planes[2].getRowStride()];

            int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;

            // loop via rows of u/v channels

            int offsetY = 0;

            int sizeY =  width * height * bytesPerPixel;
            int sizeUV = (width * height * bytesPerPixel) / 4;

            for (int row = 0; row < height ; row++) {

                // fill data for Y channel, two row
                {
                    int length = bytesPerPixel * width;
                    buffer0.get(data, offsetY, length);

                    if ( height - row != 1)
                        buffer0.position(buffer0.position()  +  planes[0].getRowStride() - length);

                    offsetY += length;
                }

                if (row >= height/2)
                    continue;

                {
                    int uvlength = planes[1].getRowStride();

                    if ( (height / 2 - row) == 1 ) {
                        uvlength = width / 2 - planes[1].getPixelStride() + 1;
                    }

                    buffer1.get(rowData1, 0, uvlength);
                    buffer2.get(rowData2, 0, uvlength);

                    // fill data for u/v channels
                    for (int col = 0; col < width / 2; ++col) {
                        // u channel
                        data[sizeY + (row * width)/2 + col] = rowData1[col * planes[1].getPixelStride()];

                        // v channel
                        data[sizeY + sizeUV + (row * width)/2 + col] = rowData2[col * planes[2].getPixelStride()];
                    }
                }

            }
            return data;
        }
    };
}
package com.example.tryarcore;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import com.google.ar.core.Anchor;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
//import com.google.ar.core.ImageFormat;
import android.graphics.ImageFormat;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.SceneView;
import com.google.ar.sceneform.assets.RenderableSource;

import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.protobuf.ByteString;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "position";

    private ModelRenderable modelRenderable;
    private String Model_URL = "";

    ArFragment arFragment;

    private static Button button_start;
    private static Button button_reset;

    private Handler mHandler = new Handler();
    private int i = 0;

    String buttonState= "Start";
    SceneView scene;
    ImageView d3_image;
    Node node;
    ManagedChannel channel;
    StreamDataServiceGrpc.StreamDataServiceStub stub; //StreamDataServiceBlockingStub stub;

    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        arFragment.setOnSessionInitializationListener(this::OnSessionInitialization);

        setUpModel();
        setUpPlane();
        //scene = (SceneView) findViewById(R.id.sceneView);
        //render(Uri.parse(Model_URL));

        d3_image = (ImageView) findViewById(R.id.imageView);

        button_start = (Button) findViewById(R.id.start);
        button_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String buttonText = button_start.getText().toString();

                if (buttonText.equals("Start")) buttonState = "Stop";
                else buttonState = "Start";

                button_start.setText(buttonState);
            }
        });
        //button_start.setElevation(5.0f);

        //button_reset = (Button) findViewById(R.id.reset);
        //button_reset.setOnClickListener(new View.OnClickListener() {
            //@Override
            //public void onClick(View v) {
            //}
        //});
        //button_reset.setElevation(3.0f);

        channel = ManagedChannelBuilder.forAddress("192.168.0.85", 9999).usePlaintext().build();
        stub = StreamDataServiceGrpc.newStub(channel) ;//newBlockingStub(channel);

        Log.i(TAG, "Channel created");

        mHandler.removeCallbacks(process);
        mHandler.postDelayed(process, 100);
    }


    private void render(Uri uri) {
        ModelRenderable.builder()
                .setSource(this, uri)
                .build()
                .thenAccept (
                        renderable -> {
                            modelRenderable = renderable;
                            node.setParent(scene.getScene());
                            node.setLocalPosition(new Vector3(0f, -2f, -7f));
                            node.setLocalScale(new Vector3(3f, 3f, 3f));
                            scene.getScene().addChild(node);
                        }
        )
            .exceptionally (throwable -> {
            Log.i("Model","cant load");
            Toast.makeText(MainActivity.this,"Model can't be Loaded", Toast.LENGTH_SHORT).show();
            return null;
        });

    }


    private void setUpModel() {
        ModelRenderable.builder()
                .setSource(this,
                        RenderableSource.builder().setSource(
                                this,
                                Uri.parse(Model_URL),
                                RenderableSource.SourceType.GLB)
                                .setScale(0.75f)
                                .setRecenterMode(RenderableSource.RecenterMode.ROOT)
                                .build())

                .setRegistryId(Model_URL)
                .build()
                .thenAccept(renderable -> modelRenderable = renderable)
                .exceptionally(throwable -> {
                    Log.i("Model","cant load");
                    Toast.makeText(MainActivity.this,"Model can't be Loaded", Toast.LENGTH_SHORT).show();
                    return null;
                });
    }

    private void setUpPlane(){
        arFragment.setOnTapArPlaneListener(((hitResult, plane, motionEvent) -> {
            Anchor anchor = hitResult.createAnchor();
            AnchorNode anchorNode = new AnchorNode(anchor);
            anchorNode.setParent(arFragment.getArSceneView().getScene());
            createModel(anchorNode);
        }));
    }

    private void createModel(AnchorNode anchorNode){
        TransformableNode node = new TransformableNode(arFragment.getTransformationSystem());
        node.setParent(anchorNode);
        node.setRenderable(modelRenderable);
        node.select();
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

    private Runnable process = new Runnable() {

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

            if (button_start.getText().toString().equals("Stop")) {
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


                        Data request = Data.newBuilder().setX(position[0]).setY(position[1]).setZ(position[2]).setQw(orientation[3]).setQx(orientation[0]).setQy(orientation[1]).setQz(orientation[2]).setImage(ByteString.copyFrom(data)).build();

                        Log.i(TAG, "Request created");

                        final Mesh[] mesh_responce = {null};
                        stub.data(request, new StreamObserver<Mesh>() {
                            @Override
                            public void onNext(Mesh response) {
                                mesh_responce[0] = response;
                            }
                            @Override
                            public void onError(Throwable throwable) {
                            }
                            @Override
                            public void onCompleted() {
                            }
                        });

                        if (mesh_responce[0] != null) {
                            Log.i(TAG, "Response taken");

                                Bitmap bitmap;
                                Mesh reply = mesh_responce[0];
                                Log.i(TAG,"Done");

                                ByteString responce = reply.getCount();
                                byte[] byteArr = responce.toByteArray();
                                bitmap = BitmapFactory.decodeByteArray(byteArr , 0, byteArr.length);
                                d3_image.setImageBitmap(bitmap);

                        }
                        //Mesh reply = stub.data(request);
                        //ByteString responce = reply.getCount();

                        //Log.i(TAG, responce.toString());

                        i += 1;
                        Log.i(TAG, "" + i);
                    }
                } catch (NotYetAvailableException e) {
                    e.printStackTrace();
                }
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
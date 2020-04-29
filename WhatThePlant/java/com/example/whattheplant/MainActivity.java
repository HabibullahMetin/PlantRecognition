package com.example.whattheplant;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.theartofdev.edmodo.cropper.CropImage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import static android.content.ContentValues.TAG;

public class MainActivity extends AppCompatActivity {

    private Camera mCamera;
    private CameraPreview mPreview;
    public String flashMode;
    public Camera.Parameters params;
    public Button flashButton;
    public Button captureButton;
    public Button galeryButton;

    private int requestFile = 100;
    private File imageFile;
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            imageFile = new File(getExternalCacheDir(), "image.jpeg");
            if (imageFile == null){
                Toast.makeText(MainActivity.this,"Dosya Hatası",Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(imageFile);
                fos.write(data);
                fos.close();
                cropImage();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        flashMode = "Off";

        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE},REQUEST_CAMERA_PERMISSION);
        }else{
            openCamera();
            openUtils();
        }
    }

    public void openCamera(){
        mCamera = getCameraInstance();

        mPreview = new CameraPreview(this, mCamera);
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(mPreview);

        params = mPreview.mCamera.getParameters();
        params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        params.setRotation(90);
        mPreview.mCamera.setParameters(params);
    }

    public  void openUtils(){
        captureButton = (Button) findViewById(R.id.takePhoto);
        captureButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        autoFocus();
                        mCamera.takePicture(null, null, mPicture);
                    }
                }
        );

        flashButton = (Button) findViewById(R.id.flashMode);
        flashButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        flashModeChange();
                        mPreview.mCamera.setParameters(params);
                    }
                }
        );

        galeryButton = (Button)findViewById(R.id.galery);
        galeryButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(Intent.ACTION_PICK);
                        intent.setType("image/*");
                        startActivityForResult(Intent.createChooser(intent, "Fotoğraf Şeç"), requestFile);
                    }
                }
        );

        mPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoFocus();
            }
        });
    }

    public void autoFocus(){
        mPreview.mCamera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                mPreview.mCamera = camera;
            }
        });
    }

    public Camera getCameraInstance(){
        Camera cam = null;
        try {
            cam = Camera.open();
        }
        catch (Exception e){
            Toast.makeText(this,"Kamera Bulunamadı",Toast.LENGTH_SHORT).show();
        }
        return cam;
    }

    public void flashModeChange(){
        if(flashMode == "Off"){
            flashMode = "On";
            params.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
            flashButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.flash_on));
        }else if(flashMode == "On"){
            flashMode = "Auto";
            params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
            flashButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.flash_auto));
        }else if(flashMode == "Auto"){
            flashMode = "Off";
            params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            flashButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.flash_off));
        }
    }

    public void cropImage(){
        try {
            Uri input = Uri.fromFile(imageFile);
            Uri output = Uri.fromFile(new File(this.getExternalCacheDir(), "imageCropped.jpeg"));
            CropImage.activity(input).setCropMenuCropButtonTitle("Tamam").setOutputUri(output).start(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                CropImage.ActivityResult result = CropImage.getActivityResult(data);
                Uri imageUri = result.getUri();
                Intent sonuc = new Intent(this, Classify.class);
                sonuc.putExtra("resID_uri", imageUri);
                startActivity(sonuc);
            }
        } else if (requestCode == requestFile) {
            if (resultCode == RESULT_OK) {
                Uri selectImage = data.getData();
                Uri output = Uri.fromFile(new File(this.getExternalCacheDir(), "imageCropped.jpeg"));
                CropImage.activity(selectImage).setCropMenuCropButtonTitle("Tamam").setOutputUri(output).start(this);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(MainActivity.this, "Kamera Kullanılamıyor", Toast.LENGTH_SHORT).show();
            finish();
        }
        if (grantResults[1] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(MainActivity.this, "Kayıt Kullanılamıyor", Toast.LENGTH_SHORT).show();
            finish();
        }
        openCamera();
        openUtils();
    }
}
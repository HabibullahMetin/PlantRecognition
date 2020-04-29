package com.example.whattheplant;

import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import static android.content.ContentValues.TAG;

public class Classify extends AppCompatActivity {
    // presets for rgb conversion
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;

    // options for model interpreter
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();
    // tflite graph
    private Interpreter tflite;
    // holds all the possible labels for model
    private List<String> labelList;
    // holds the selected image data as bytes
    private ByteBuffer imgData = null;
    // holds the probabilities of each label for non-quantized graphs
    private float[][] labelProbArray = null;
    // array that holds the labels with the highest probabilities
    private String topLables = null;
    // array that holds the highest probabilities
    private String topConfidence = null;

    public JSONObject feature;

    // selected classifier information received from extras
    private String choseModel;
    private String choseLabel;
    // input image dimensions for the Inception Model
    private int DIM_IMG_SIZE_X = 224;
    private int DIM_IMG_SIZE_Y = 224;
    private int DIM_PIXEL_SIZE = 3;
    private int FLOAT_TYPE_SIZE = 4;

    // int array to hold image data
    private int[] intValues;

    // activity elements
    private ImageView selected_image;
    private Button classify_button;
    private TextView turu;
    private TextView dogruluk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        choseModel = "whattheplant.tflite";
        choseLabel = "whattheplantlabel.txt";

        intValues = new int[DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];

        super.onCreate(savedInstanceState);

        //initilize graph and labels
        try{
            tflite = new Interpreter(loadModelFile(), tfliteOptions);
            labelList = loadLabelList();
        } catch (Exception ex){
            ex.printStackTrace();
        }

        imgData = ByteBuffer.allocateDirect(FLOAT_TYPE_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);
        imgData.order(ByteOrder.nativeOrder());

        labelProbArray = new float[1][labelList.size()];

        setContentView(R.layout.activity_classify);

        turu = findViewById(R.id.tur);

        // displays the probabilities of top labels
        dogruluk = findViewById(R.id.dogruluk);

        // initialize imageView that displays selected image to the user
        selected_image = findViewById(R.id.selected_image);

        // initialize array to hold top labels
        topLables = new String();
        // initialize array to hold top probabilities
        topConfidence = new String();

        // classify current dispalyed image
        classify_button = findViewById(R.id.classify_image);
        classify_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // get current bitmap from imageView
                Bitmap bitmap_orig = ((BitmapDrawable)selected_image.getDrawable()).getBitmap();
                // resize the bitmap to the required input size to the CNN
                Bitmap bitmap = getResizedBitmap(bitmap_orig, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y);
                // convert bitmap to byte array
                convertBitmapToByteBuffer(bitmap);
                // pass byte data to the graph
                tflite.run(imgData, labelProbArray);
                // display the results
                printTopKLabels();
            }
        });

        Uri uri = getIntent().getParcelableExtra("resID_uri");

        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            selected_image.setImageBitmap(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // loads tflite grapg from file
    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd(choseModel);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    // converts bitmap to byte array which is passed in the tflite graph
    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        File ds = new File(getExternalCacheDir(), "data.txt");

        if (imgData == null) {
            return;
        }
        imgData.rewind();
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(ds);

            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);

            BufferedWriter fos = new BufferedWriter(outputStreamWriter);

            int pixel = 0;
            for (int i = 0; i < DIM_IMG_SIZE_X; ++i) {
                for (int j = 0; j < DIM_IMG_SIZE_Y; ++j) {
                    final int val = intValues[pixel++];
                    imgData.putFloat((((val >> 16) & 0xFF)/255));
                    fos.write(((val >> 16) & 0xFF)+"\n");
                    imgData.putFloat((((val >> 8) & 0xFF)/255));
                    fos.write(((val >> 8) & 0xFF)+"\n");
                    imgData.putFloat((((val) & 0xFF)/255));
                    fos.write(((val) & 0xFF)+"\n");
                }
            }

            fos.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }

        bitmap.recycle();
    }

    // loads the labels from the label txt file in assets into a string array
    private List<String> loadLabelList() throws IOException {
        List<String> labelList = new ArrayList<String>();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(this.getAssets().open(choseLabel)));
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    // print the top labels and respective confidences
    public void printTopKLabels() {
        // add all results to priority queue
        float maxValue = 0;
        int index = 0;

        for (int i = 0; i < labelList.size(); ++i) {
            if(maxValue <= labelProbArray[0][i]){
                maxValue = labelProbArray[0][i];
                index = i;
            }
        }

        topLables = labelList.get(index);
        topConfidence = String.format("%.0f%%",labelProbArray[0][index]*100);

        turu.setText(topLables);
        dogruluk.setText(topConfidence);

        get_feature();
    }
    // resizes bitmap to given dimensions
    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        return resizedBitmap;
    }

    public String loadJSONFromAsset() {
        String json = null;
        try {
            InputStream is = getAssets().open("feature.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }

    public void get_feature(){
        try {
            JSONObject obj = new JSONObject(loadJSONFromAsset());
            JSONArray array = obj.getJSONArray("bitkiler");
            for(int i=0;i<array.length();i++){
                JSONObject bitki = array.getJSONObject(i);
                if(topLables.equals(bitki.getString("bitkiAdi"))){
                    feature = bitki;
                    break;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }finally {
            change_featurelabels();
        }
    }

    public void change_featurelabels(){
        TextView prop1 = (TextView)findViewById(R.id.ortamIsisi);
        TextView prop2 = (TextView)findViewById(R.id.yazSuMiktari);
        TextView prop3 = (TextView)findViewById(R.id.kisSuMiktari);
        TextView prop4 = (TextView)findViewById(R.id.gunesIsigi);
        TextView prop5 = (TextView)findViewById(R.id.toprakDegisimSuresi);
        TextView prop6 = (TextView)findViewById(R.id.bitkilerleTemasi);
        TextView prop7 = (TextView)findViewById(R.id.destekBesin);
        TextView prop8 = (TextView)findViewById(R.id.havaDegisimi);

        try {
            prop1.setText(feature.getString("ortamIsisi"));
            prop2.setText(feature.getString("yazSuMiktari"));
            prop3.setText(feature.getString("kisSuMiktari"));
            prop4.setText(feature.getString("gunesIsigi"));
            prop5.setText(feature.getString("toprakDegisimSuresi"));
            prop6.setText(feature.getString("bitkilerleTemasi"));
            prop7.setText(feature.getString("destekBesin"));
            prop8.setText(feature.getString("havaDegisimi"));

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
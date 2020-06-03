package com.example.whattheplant;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.provider.MediaStore;

import android.os.Bundle;
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
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;


public class Classify extends AppCompatActivity {
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;

    private final Interpreter.Options tfliteOptions = new Interpreter.Options();
    private Interpreter tflite;
    private List<String> labelList;
    private ByteBuffer imgData = null;
    private float[][] labelProbArray = null;
    private String topLables = null;
    private String topConfidence = null;

    Bitmap bitmap;

    public JSONObject feature;

    private String choseModel;
    private String choseLabel;

    private int DIM_IMG_SIZE_X = 224;
    private int DIM_IMG_SIZE_Y = 224;
    private int DIM_PIXEL_SIZE = 3;
    private int FLOAT_TYPE_SIZE = 4;

    private int[] intValues;

    private ImageView selected_image;
    private Button classify_button;
    private TextView turu;
    private TextView dogruluk;

    private PriorityQueue<Map.Entry<String, Float>> sortedLabels =
            new PriorityQueue<>(
                    1,
                    new Comparator<Map.Entry<String, Float>>() {
                        @Override
                        public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
                            return (o1.getValue()).compareTo(o2.getValue());
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        choseModel = "whattheplant.tflite";
        choseLabel = "whattheplantlabel.txt";

        intValues = new int[DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];
        super.onCreate(savedInstanceState);

        try{
            tflite = new Interpreter(loadModelFile(),tfliteOptions);
            labelList = loadLabelList();
        } catch (Exception ex){
            ex.printStackTrace();
        }

        imgData = ByteBuffer.allocateDirect(FLOAT_TYPE_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);
        imgData.order(ByteOrder.nativeOrder());

        labelProbArray = new float[1][labelList.size()];

        setContentView(R.layout.activity_classify);

        turu = findViewById(R.id.tur);
        dogruluk = findViewById(R.id.dogruluk);
        selected_image = findViewById(R.id.selected_image);

        topLables = new String();
        topConfidence = new String();

        classify_button = findViewById(R.id.classify_image);

        classify_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bitmap bitmap_orig = ((BitmapDrawable)selected_image.getDrawable()).getBitmap();

                Bitmap bitmaps = getResizedBitmap(bitmap_orig, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y);

                convertBitmapToByteBuffer(bitmaps);

                tflite.run(imgData, labelProbArray);

                printTopKLabels();

                getServerModel();
            }
        });

        Uri uri = getIntent().getParcelableExtra("resID_uri");

        try {
            bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            selected_image.setImageBitmap(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd(choseModel);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void convertBitmapToByteBuffer(Bitmap bitmap) {

        if (imgData == null) {
            return;
        }

        imgData.rewind();
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        int pixel = 0;
        for (int i = 0; i < DIM_IMG_SIZE_X; ++i) {
            for (int j = 0; j < DIM_IMG_SIZE_Y; ++j) {
                final int val = intValues[pixel++];
                imgData.putFloat((((val >> 16) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                imgData.putFloat((((val >> 8) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                imgData.putFloat((((val) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
            }
        }

        bitmap.recycle();
    }

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

    public void printTopKLabels() {
        for (int i = 0; i < labelList.size(); ++i) {
            sortedLabels.add(new AbstractMap.SimpleEntry<>(labelList.get(i), labelProbArray[0][i]));
            if (sortedLabels.size() > 1){
                sortedLabels.poll();
            }
        }

        Map.Entry<String, Float> label = sortedLabels.poll();
        topLables = label.getKey();
        topConfidence = String.format("%.0f%%",label.getValue()*100);

        turu.setText(topLables);
        dogruluk.setText(topConfidence);

        get_feature();
    }

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
        TextView prop1 = (TextView)findViewById(R.id.sicaklik);
        TextView prop2 = (TextView)findViewById(R.id.sulama);
        TextView prop3 = (TextView)findViewById(R.id.toprakTuru);
        TextView prop4 = (TextView)findViewById(R.id.gunesIsigi);
        TextView prop5 = (TextView)findViewById(R.id.saksiDegisim);
        TextView prop6 = (TextView)findViewById(R.id.destekBesin);


        try {
            prop1.setText(feature.getString("sicaklik"));
            prop2.setText(feature.getString("sulama"));
            prop3.setText(feature.getString("toprakTuru"));
            prop4.setText(feature.getString("gunesIsigi"));
            prop5.setText(feature.getString("saksiDegisim"));
            prop6.setText(feature.getString("destekBesin"));

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getServerModel(){
        String postUrl = "http://192.168.1.36:5000/";

        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);

        byte[] byteArray = stream.toByteArray();

        RequestBody postBodyImage = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "androidFlask.jpg", RequestBody.create(MediaType.parse("image/*jpg"), byteArray))
                .build();

        postRequest(postUrl, postBodyImage);
    }

    void postRequest(String postUrl, RequestBody postBody) {

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(postUrl)
                .post(postBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(Classify.this,"Sunucu Bulunamadı!!!",Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ResponseBody resB = response.body();
                        try {
                            Toast.makeText(Classify.this,"Server Cevap: "+resB.string(),Toast.LENGTH_SHORT).show();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
    }
}
package com.example.arsudokusolver;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.example.arsudokusolver.databinding.ActivityMainBinding;

import org.opencv.android.CameraActivity;

import java.io.File;
import java.io.IOException;

public class MainActivity extends CameraActivity {

    private static final int IMAGE_SELECTOR_CODE = 100;
    private static final int IMAGE_CAPTURE_CODE = 101;
    private static final int VIDEO_CAPTURE_CODE = 102;
    private ActivityMainBinding binding;

    private String mCurrentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        //OnClick Listeners
        binding.imageSelectorButton.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, IMAGE_SELECTOR_CODE);
        });
        binding.imageCaptureButton.setOnClickListener(v -> {
            try {
                dispatchTakePictureIntent();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        binding.videoCaptureButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, com.example.arsudokusolver.CameraActivity.class);
            startActivityForResult(intent, VIDEO_CAPTURE_CODE);
        });
    }

    private void dispatchTakePictureIntent() throws IOException {
        String fileName = System.currentTimeMillis() + "_photo";
        File storageDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File imageFile = File.createTempFile(fileName, ".jpeg", storageDirectory);
        mCurrentPhotoPath = imageFile.getAbsolutePath();

        Uri imageUri = FileProvider.getUriForFile(this, "com.example.arsudokusolver.fileprovider", imageFile);
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(takePictureIntent, IMAGE_CAPTURE_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            Bitmap output = null;
            if (requestCode == IMAGE_SELECTOR_CODE && data != null) {
                try {
                    Bitmap input = MediaStore.Images.Media.getBitmap(this.getContentResolver(), data.getData());
                    output = SudokuDNS.solve(getApplicationContext(), input);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (requestCode == IMAGE_CAPTURE_CODE) {
                Bitmap input = BitmapFactory.decodeFile(mCurrentPhotoPath);
                output = SudokuDNS.solve(getApplicationContext(), input);
            } else if (requestCode == VIDEO_CAPTURE_CODE) {
                byte[] byteArray = data.getByteArrayExtra("OUTPUT_IMAGE");
                if (byteArray != null && byteArray.length > 0) {
                    output = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
                }
            }

            //
            if (output == null) {
                Toast.makeText(getApplicationContext(), "No Valid Sudoku Detected!", Toast.LENGTH_SHORT).show();
                binding.imageView.setImageBitmap(null);
            } else {
                Toast.makeText(getApplicationContext(), "Sudoku Detected and Solved!", Toast.LENGTH_SHORT).show();
                binding.imageView.setImageBitmap(output);
            }
        }
    }
}
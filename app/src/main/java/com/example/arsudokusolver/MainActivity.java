package com.example.arsudokusolver;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.media.Image;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.arsudokusolver.databinding.ActivityMainBinding;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int SELECT_CODE = 100;
    private ActivityMainBinding binding;
    private Bitmap bitmap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        if (OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV Initialized!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "OpenCV not Initialized\nSomething went Wrong.", Toast.LENGTH_SHORT).show();
        }

        binding.selectButton.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, SELECT_CODE);
        });

        binding.solveButton.setOnClickListener(v -> {
            if (bitmap == null) {
                Toast.makeText(this, "Please select an Image!", Toast.LENGTH_SHORT).show();
            } else {
                Bitmap output = SudokuDNS.solve(getApplicationContext(), binding.linearLayout, bitmap);
                Toast.makeText(this, "Solved!", Toast.LENGTH_SHORT).show();
                binding.imageView.setImageBitmap(output);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SELECT_CODE && data != null) {
            try {
                bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), data.getData());
                binding.imageView.setImageBitmap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
package org.opencds.cqf.cql.evaluator.android.sample;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void openLibrary(View view) {
        Intent intent = new Intent(MainActivity.this, LibraryActivity.class);
        startActivity(intent);
    }

    public void openMeasure(View view) {
        Intent intent = new Intent(MainActivity.this, MeasureActivity.class);
        startActivity(intent);
    }
}
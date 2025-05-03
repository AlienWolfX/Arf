package com.alienwolfx.arf;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private MyHttpServer server;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Find the button
        Button startServerButton = findViewById(R.id.startServerButton);

        // Set an OnClickListener for the button
        startServerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start the server when button is clicked
                startServer();
            }
        });
    }

    private void startServer() {
        if (server == null) {
            try {
                server = new MyHttpServer(); // Initialize the server
                server.start(); // Start the server
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop(); // Stop the server when the activity is destroyed
        }
    }
}

    package com.alienwolfx.arf;

    import android.os.Bundle;
    import android.text.util.Linkify;
    import android.view.Menu;
    import android.view.MenuItem;
    import android.widget.Button;
    import android.widget.TextView;

    import androidx.appcompat.app.AlertDialog;
    import androidx.appcompat.app.AppCompatActivity;

    import java.io.IOException;

    public class MainActivity extends AppCompatActivity {

        private MyHttpServer server;
        private TextView statusText;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            statusText = findViewById(R.id.serverStatusText);

            Button startServerButton = findViewById(R.id.startServerButton);
            Button stopServerButton = findViewById(R.id.stopServerButton);

            startServerButton.setOnClickListener(v -> startServer());
            stopServerButton.setOnClickListener(v -> stopServer());
        }

        @Override
        public boolean onCreateOptionsMenu(Menu menu) {
            getMenuInflater().inflate(R.menu.main_menu, menu);
            return true;
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            if (item.getItemId() == R.id.action_about) {
                showAboutDialog();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        private void showAboutDialog() {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("About Me")
                    .setMessage("This app was created with ❤️ by AlienWolfX.\n\nVersion 1.0.0-Alpha\n\nVisit: https://github.com/AlienWolfX")
                    .setPositiveButton("OK", null)
                    .show();

            TextView messageView = (TextView) dialog.findViewById(android.R.id.message);
            if (messageView != null) {
                Linkify.addLinks(messageView, Linkify.WEB_URLS);
            }
        }

        private void startServer() {
            if (server == null) {
                try {
                    server = new MyHttpServer();
                    server.start();
                    statusText.setText("Server Active");
                } catch (IOException e) {
                    statusText.setText("Error starting server");
                    e.printStackTrace();
                }
            } else {
                statusText.setText("Server already running");
            }
        }

        private void stopServer() {
            if (server != null) {
                server.stop();
                server = null;
                statusText.setText("Server Inactive");
            }
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            stopServer();
        }
    }

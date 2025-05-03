package com.alienwolfx.arf;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import java.io.IOException;

public class HttpService extends Service {

    private MyHttpServer server;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            server = new MyHttpServer();
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

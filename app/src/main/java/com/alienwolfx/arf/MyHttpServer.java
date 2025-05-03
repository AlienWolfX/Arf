package com.alienwolfx.arf;

import android.os.Build;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class MyHttpServer extends NanoHTTPD {

    private Gson gson;

    public MyHttpServer() {
        super(8000);
        gson = new Gson(); // Initialize Gson
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if ("/hi".equals(uri)) {
            ResponseMessage responseMessage = new ResponseMessage("Hello World");
            String jsonResponse = gson.toJson(responseMessage); // Convert to JSON string
            return newFixedLengthResponse(Response.Status.OK, "application/json", jsonResponse);
        }
        else if ("/meow".equals(uri)) {
            ResponseMessage responseMessage = new ResponseMessage("Ugh");
            String jsonResponse = gson.toJson(responseMessage);
            return newFixedLengthResponse(Response.Status.OK, "application/json", jsonResponse);
        }
        else if ("/device_info".equals(uri)) {
            DeviceInfo deviceInfo = getDeviceInfo();
            String jsonResponse = gson.toJson(deviceInfo); // Convert device info to JSON
            return newFixedLengthResponse(Response.Status.OK, "application/json", jsonResponse);
        }
        else if ("/add".equals(uri) && Method.POST.equals(session.getMethod())) {
            try {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);

                String json = files.get("postData");

                AddRequest addRequest = gson.fromJson(json, AddRequest.class);

                int sum = addRequest.getA() + addRequest.getB();

                AddResponse addResponse = new AddResponse(sum);
                String jsonResponse = gson.toJson(addResponse);
                return newFixedLengthResponse(Response.Status.OK, "application/json", jsonResponse);
            } catch (Exception e) {
                e.printStackTrace();
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error parsing request");
            }
        }

        else {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found");
        }
    }

    private static class AddRequest {
        private int a;
        private int b;

        public int getA() { return a; }
        public int getB() { return b; }
    }

    private static class AddResponse {
        private int result;

        public AddResponse(int result) {
            this.result = result;
        }

        public int getResult() {
            return result;
        }
    }

    private DeviceInfo getDeviceInfo() {
        String model = Build.MODEL;
        String manufacturer = Build.MANUFACTURER;
        String version = Build.VERSION.RELEASE;
        String serial = Build.SERIAL;

        return new DeviceInfo(model, manufacturer, version, serial);
    }

    private static class DeviceInfo {
        private String model;
        private String manufacturer;
        private String version;
        private String serial;

        public DeviceInfo(String model, String manufacturer, String version, String serial) {
            this.model = model;
            this.manufacturer = manufacturer;
            this.version = version;
            this.serial = serial;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getManufacturer() {
            return manufacturer;
        }

        public void setManufacturer(String manufacturer) {
            this.manufacturer = manufacturer;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getSerial() {
            return serial;
        }

        public void setSerial(String serial) {
            this.serial = serial;
        }
    }

    private static class ResponseMessage {
        private String message;

        public ResponseMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}

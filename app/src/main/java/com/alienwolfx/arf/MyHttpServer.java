package com.alienwolfx.arf;

import android.telephony.SmsManager;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.content.ContentValues;
import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.telephony.TelephonyManager;
import android.net.TrafficStats;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import android.net.DnsResolver;
import android.net.Network;
import android.net.LinkProperties;
import java.util.stream.Collectors;

import fi.iki.elonen.NanoHTTPD;

public class MyHttpServer extends NanoHTTPD {

    private static final Gson GSON = new Gson();
    private static final Pattern API_PATTERN = Pattern.compile("/api/v1/(\\d{4})");
    private static final String ACTION_SMS_SENT = "SMS_SENT";
    private static final String ACTION_SMS_DELIVERED = "SMS_DELIVERED";
    private static final String MIME_TYPE_JSON = "application/json";
    private final Gson gson;
    private final Context context;
    private final ContentResolver contentResolver;
    private final ActivityManager activityManager;
    private final TelephonyManager telephonyManager;
    private final ConnectivityManager connectivityManager;

    public MyHttpServer(Context context) {
        super(8000);
        this.context = context;
        this.contentResolver = context.getContentResolver();
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        this.telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        gson = new Gson();
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Matcher matcher = API_PATTERN.matcher(uri);

        if (matcher.matches()) {
            int funcNo = Integer.parseInt(Objects.requireNonNull(matcher.group(1)));
            return handleApiRequest(funcNo, session);
        }

        ApiResponse errorResponse = new ApiResponse(false, "404 Not Found", null);
        String jsonResponse = gson.toJson(errorResponse);
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_TYPE_JSON, jsonResponse);
    }

    private Response handleApiRequest(int funcNo, IHTTPSession session) {
        switch (funcNo) {
            case 1000:
                return handleDeviceInfo();
            case 1001:
                return handleSystemResources();
            case 1002:
                return handleSmsMessages();
            case 1003:
                return handleSendSms(session);
            case 1004:
                return handleShellCommand(session);
            case 1005:
                return handleSimInfo();
            case 1006:
                return handleNetworkStats();
            default:
                ApiResponse errorResponse = new ApiResponse(false, "Function not found", null);
                String jsonResponse = gson.toJson(errorResponse);
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_TYPE_JSON, jsonResponse);
        }
    }

    private Response handleDeviceInfo() {
        DeviceInfo deviceInfo = getDeviceInfo();
        String jsonResponse = GSON.toJson(new ApiResponse(true, "Success", deviceInfo));
        return newFixedLengthResponse(Response.Status.OK, MIME_TYPE_JSON, jsonResponse);
    }

    private Response handleSystemResources() {
        SystemResources resources = getSystemResources();
        ApiResponse response = new ApiResponse(true, "Success", resources);
        String jsonResponse = gson.toJson(response);
        return newFixedLengthResponse(Response.Status.OK, MIME_TYPE_JSON, jsonResponse);
    }

    private Response handleSmsMessages() {
        List<SmsMessage> messages = getSmsMessages();
        ApiResponse response = new ApiResponse(true, "Success", messages);
        String jsonResponse = gson.toJson(response);
        return newFixedLengthResponse(Response.Status.OK, MIME_TYPE_JSON, jsonResponse);
    }

    private Response handleSendSms(IHTTPSession session) {
        if (!Method.POST.equals(session.getMethod())) {
            return createErrorResponse("Method not allowed", Response.Status.METHOD_NOT_ALLOWED);
        }

        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String json = files.get("postData");
            if (json == null) {
                return createErrorResponse("Missing request body", Response.Status.BAD_REQUEST);
            }

            SendSmsRequest request = gson.fromJson(json, SendSmsRequest.class);
            if (request == null || request.getPhoneNumber() == null || request.getMessage() == null) {
                return createErrorResponse("Invalid SMS request format", Response.Status.BAD_REQUEST);
            }

            try {
                SmsManager smsManager = SmsManager.getDefault();
                
                // Create pending intents for monitoring SMS status
                PendingIntent sentPI = PendingIntent.getBroadcast(context, 0, 
                    new Intent(ACTION_SMS_SENT), PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
                PendingIntent deliveredPI = PendingIntent.getBroadcast(context, 0,
                    new Intent(ACTION_SMS_DELIVERED), PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

                if (request.getMessage().length() > 160) {
                    // Handle long messages
                    ArrayList<String> parts = smsManager.divideMessage(request.getMessage());
                    
                    smsManager.sendMultipartTextMessage(
                        request.getPhoneNumber(),
                        null,
                        parts,
                            (ArrayList<PendingIntent>) Collections.nCopies(parts.size(), sentPI),
                            (ArrayList<PendingIntent>) Collections.nCopies(parts.size(), deliveredPI)
                    );
                } else {
                    smsManager.sendTextMessage(
                        request.getPhoneNumber(),
                        null,
                        request.getMessage(),
                        sentPI,
                        deliveredPI
                    );
                }

                // Save to sent folder manually for Android 4.4.4
                ContentValues values = new ContentValues();
                values.put("address", request.getPhoneNumber());
                values.put("body", request.getMessage());
                values.put("type", 2); // 2 = sent message
                values.put("read", 1);
                values.put("date", System.currentTimeMillis());

                try {
                    context.getContentResolver().insert(Uri.parse("content://sms/sent"), values);
                } catch (Exception e) {
                    Log.e("MyHttpServer", "Error message", e);
                }

                return createSuccessResponse("SMS sent successfully", null);
            } catch (SecurityException se) {
                return createErrorResponse("SMS permission denied: " + se.getMessage(), 
                                        Response.Status.FORBIDDEN);
            } catch (Exception e) {
                return createErrorResponse("Failed to send SMS: " + e.getMessage(), 
                                        Response.Status.INTERNAL_ERROR);
            }
        } catch (Exception e) {
            return createErrorResponse("Error processing request: " + e.getMessage(), 
                                     Response.Status.INTERNAL_ERROR);
        }
    }

    private Response handleShellCommand(IHTTPSession session) {
        if (!Method.POST.equals(session.getMethod())) {
            ApiResponse errorResponse = new ApiResponse(false, "Method not allowed", null);
            String jsonResponse = gson.toJson(errorResponse);
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_TYPE_JSON, jsonResponse);
        }

        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String json = files.get("postData");
            ShellCommandRequest request = gson.fromJson(json, ShellCommandRequest.class);

            Process process = Runtime.getRuntime().exec(request.getCommand());
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()), 8192);
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();
            
            ShellCommandResponse shellResponse = new ShellCommandResponse(
                output.toString(),
                process.exitValue()
            );
            
            ApiResponse response = new ApiResponse(true, "Command executed successfully", shellResponse);
            String jsonResponse = gson.toJson(response);
            return newFixedLengthResponse(Response.Status.OK, MIME_TYPE_JSON, jsonResponse);
        } catch (Exception e) {
            Log.e("MyHttpServer", "Error message", e);
            ApiResponse errorResponse = new ApiResponse(false, "Error executing command: " + e.getMessage(), null);
            String jsonResponse = gson.toJson(errorResponse);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_TYPE_JSON, jsonResponse);
        }
    }

    private Response handleSimInfo() {
        SimInfo simInfo = getSimInfo();
        ApiResponse response = new ApiResponse(true, "Success", simInfo);
        String jsonResponse = gson.toJson(response);
        return newFixedLengthResponse(Response.Status.OK, MIME_TYPE_JSON, jsonResponse);
    }

    private Response handleNetworkStats() {
        NetworkStats stats = getNetworkStats();
        ApiResponse response = new ApiResponse(true, "Success", stats);
        String jsonResponse = gson.toJson(response);
        return newFixedLengthResponse(Response.Status.OK, MIME_TYPE_JSON, jsonResponse);
    }

    private SystemResources getSystemResources() {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);

        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        long blockSize = stat.getBlockSizeLong();
        long totalBlocks = stat.getBlockCountLong();
        long availableBlocks = stat.getAvailableBlocksLong();

        long totalMemory = memoryInfo.totalMem / (1024 * 1024);
        long availableMemory = memoryInfo.availMem / (1024 * 1024);
        long totalStorage = (totalBlocks * blockSize) / (1024 * 1024);
        long availableStorage = (availableBlocks * blockSize) / (1024 * 1024);

        return new SystemResources(
            totalMemory,
            availableMemory,
            totalStorage,
            availableStorage
        );
    }

    private List<SmsMessage> getSmsMessages() {
        List<SmsMessage> messages = new ArrayList<>();
        try (Cursor cursor = contentResolver.query(
            Uri.parse("content://sms/inbox"),
            new String[]{"address", "body", "date"},
            null,
            null,
            "date DESC LIMIT 10"
        )) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                    String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                    long date = cursor.getLong(cursor.getColumnIndexOrThrow("date"));
                    messages.add(new SmsMessage(address, body, date));
                }
            }
        } catch (Exception e) {
            Log.e("MyHttpServer", "Error message", e);
        }
        return messages;
    }

    private DeviceInfo getDeviceInfo() {
        String model = Build.MODEL;
        String manufacturer = Build.MANUFACTURER;
        String version = Build.VERSION.RELEASE;
        String serial = "";
        
        // Handle serial number for different API levels
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                serial = Build.SERIAL;
            } else {
                serial = Build.SERIAL != null ? Build.SERIAL : "";
            }
        } catch (Exception e) {
            Log.e("MyHttpServer", "Error message", e);
        }
        
        String imei = "";
        String esn = "";
        
        try {
            if (telephonyManager != null) {
                // For Android 4.4.4, only use getDeviceId()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        imei = telephonyManager.getImei();
                    } catch (SecurityException se) {
                        imei = "";
                    }
                } else {
                    try {
                        imei = telephonyManager.getDeviceId();
                    } catch (SecurityException se) {
                        imei = "";
                    }
                }

                // Only try to get CDMA device ID on supported devices
                try {
                    if (telephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
                        esn = telephonyManager.getDeviceId();
                    }
                } catch (SecurityException se) {
                    esn = "";
                }
            }
        } catch (Exception e) {
            Log.e("MyHttpServer", "Error message", e);
        }

        return new DeviceInfo(model, manufacturer, version, serial, imei, esn);
    }

    private NetworkStats getNetworkStats() {
        // Get traffic statistics
        long mobileRxBytes = TrafficStats.getMobileRxBytes();
        long mobileTxBytes = TrafficStats.getMobileTxBytes();
        long totalRxBytes = TrafficStats.getTotalRxBytes();
        long totalTxBytes = TrafficStats.getTotalTxBytes();
        
        // Get network type and status
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        String networkType = (activeNetwork != null) ? activeNetwork.getTypeName() : "unknown";
        boolean isConnected = activeNetwork != null && activeNetwork.isConnected();
        
        // Get cellular network info
        String operator = "";
        String mcc = "";
        String mnc = "";
        int signalStrength = -1;
        
        try {
            operator = telephonyManager.getNetworkOperator();
            if (operator.length() >= 5) {
                mcc = operator.substring(0, 3);
                mnc = operator.substring(3);
            }
            
            List<CellInfo> cellInfos = telephonyManager.getAllCellInfo();
            if (cellInfos != null) {
                for (CellInfo info : cellInfos) {
                    if (info instanceof CellInfoLte && info.isRegistered()) {
                        CellSignalStrengthLte lteSignalStrength = ((CellInfoLte) info).getCellSignalStrength();
                        signalStrength = lteSignalStrength.getDbm();
                        break;
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e("MyHttpServer", "Error message", e);
        }

        // Get IP addresses
        List<String> ipAddresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            while (nets.hasMoreElements()) {
                NetworkInterface netint = nets.nextElement();
                Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        ipAddresses.add(inetAddress.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            Log.e("MyHttpServer", "Error message", e);
        }

        // Get DNS servers
        List<String> dnsServers = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Network network = connectivityManager.getActiveNetwork();
                if (network != null) {
                    DnsResolver dnsResolver = DnsResolver.getInstance();
                    LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
                    if (linkProperties != null) {
                        dnsServers.addAll(
                            linkProperties.getDnsServers()
                                .stream()
                                .map(InetAddress::getHostAddress)
                                .collect(Collectors.toList())
                        );
                    }
                }
            } catch (Exception e) {
                Log.e("MyHttpServer", "Error message", e);
            }
        } else {
            // Fallback for older Android versions
            try {
                String[] props = {
                    "net.dns1",
                    "net.dns2",
                    "net.dns3",
                    "net.dns4"
                };
                
                for (String prop : props) {
                    Process process = Runtime.getRuntime().exec("getprop " + prop);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String dns = reader.readLine();
                    if (dns != null && !dns.isEmpty()) {
                        dnsServers.add(dns);
                    }
                    reader.close();
                }
            } catch (Exception e) {
                Log.e("MyHttpServer", "Error message", e);
            }
        }

        return new NetworkStats(
            mobileRxBytes,
            mobileTxBytes,
            totalRxBytes,
            totalTxBytes,
            networkType,
            isConnected,
            operator,
            mcc,
            mnc,
            signalStrength,
            ipAddresses,
            dnsServers
        );
    }

    private static class ApiResponse {
        private final boolean success;
        private final String message;
        private final Object data;

        public ApiResponse(boolean success, String message, Object data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
    }

    private SimInfo getSimInfo() {
        String carrierName = "";
        String countryIso = "";
        String simOperator = "";
        int simState = -1;
        String simSerialNumber = "";
        
        try {
            if (telephonyManager != null) {
                // Get carrier name based on API level
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // Android P (API 28) and above
                    carrierName = telephonyManager.getSimCarrierIdName() != null ? telephonyManager.getSimCarrierIdName().toString() : "";
                } else {
                    carrierName = telephonyManager.getNetworkOperatorName();
                }
                
                countryIso = telephonyManager.getSimCountryIso();
                simOperator = telephonyManager.getSimOperator();
                simState = telephonyManager.getSimState();
                
                // Get SIM serial number based on API level
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android O (API 26) and above
                    simSerialNumber = telephonyManager.getSimSerialNumber();
                }
            }
        } catch (SecurityException e) {
            Log.e("MyHttpServer", "Error message", e);
        }
    
        return new SimInfo(carrierName, countryIso, simOperator, simState, simSerialNumber);
    }
    

    private static class SimInfo {
        private final String carrierName;
        private final String countryIso;
        private final String simOperator;
        private final int simState;
        private final String simSerialNumber;
    
        public SimInfo(String carrierName, String countryIso, String simOperator, 
                      int simState, String simSerialNumber) {
            this.carrierName = carrierName;
            this.countryIso = countryIso;
            this.simOperator = simOperator;
            this.simState = simState;
            this.simSerialNumber = simSerialNumber;
        }
    
        // Getters
        public String getCarrierName() { return carrierName; }
        public String getCountryIso() { return countryIso; }
        public String getSimOperator() { return simOperator; }
        public int getSimState() { return simState; }
        public String getSimSerialNumber() { return simSerialNumber; }
    }

    private static class DeviceInfo {
        private String model;
        private String manufacturer;
        private String version;
        private String serial;
        private String imei;
        private String esn;
    
        public DeviceInfo(String model, String manufacturer, String version, 
                         String serial, String imei, String esn) {
            this.model = model;
            this.manufacturer = manufacturer;
            this.version = version;
            this.serial = serial;
            this.imei = imei;
            this.esn = esn;
        }

        public String getImei() {
            return imei;
        }
    
        public void setImei(String imei) {
            this.imei = imei;
        }
    
        public String getEsn() {
            return esn;
        }
    
        public void setEsn(String esn) {
            this.esn = esn;
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

    private static class SystemResources {
        private final long totalMemory;
        private final long availableMemory;
        private final long totalStorage;
        private final long availableStorage;

        public SystemResources(long totalMemoryMB, long availableMemoryMB, long totalStorageMB, long availableStorageMB) {
            this.totalMemory = totalMemoryMB;
            this.availableMemory = availableMemoryMB;
            this.totalStorage = totalStorageMB;
            this.availableStorage = availableStorageMB;
        }
    }

    private static class SmsMessage {
        private final String address;
        private final String body;
        private final long timestamp;

        public SmsMessage(String address, String body, long timestamp) {
            this.address = address;
            this.body = body;
            this.timestamp = timestamp;
        }
    }

    private static class SendSmsRequest {
        private String phoneNumber;
        private String message;

        public String getPhoneNumber() { return phoneNumber; }
        public String getMessage() { return message; }
    }

    private static class ShellCommandRequest {
        private String command;
        public String getCommand() { return command; }
    }

    private static class ShellCommandResponse {
        private final String output;
        private final int exitCode;

        public ShellCommandResponse(String output, int exitCode) {
            this.output = output;
            this.exitCode = exitCode;
        }
    }

    private static class NetworkStats {
        private final long mobileRxBytes;
        private final long mobileTxBytes;
        private final long totalRxBytes;
        private final long totalTxBytes;
        private final String networkType;
        private final boolean connected;
        private final String operator;
        private final String mcc;
        private final String mnc;
        private final int signalStrength;
        private final List<String> ipAddresses;
        private final List<String> dnsServers;

        public NetworkStats(long mobileRxBytes, long mobileTxBytes, 
                           long totalRxBytes, long totalTxBytes,
                           String networkType, boolean connected,
                           String operator, String mcc, String mnc,
                           int signalStrength,
                           List<String> ipAddresses,
                           List<String> dnsServers) {
            this.mobileRxBytes = mobileRxBytes;
            this.mobileTxBytes = mobileTxBytes;
            this.totalRxBytes = totalRxBytes;
            this.totalTxBytes = totalTxBytes;
            this.networkType = networkType;
            this.connected = connected;
            this.operator = operator;
            this.mcc = mcc;
            this.mnc = mnc;
            this.signalStrength = signalStrength;
            this.ipAddresses = ipAddresses;
            this.dnsServers = dnsServers;
        }
    }

    private Response createErrorResponse(String message, Response.Status status) {
        ApiResponse errorResponse = new ApiResponse(false, message, null);
        String jsonResponse = gson.toJson(errorResponse);
        return newFixedLengthResponse(status, MIME_TYPE_JSON, jsonResponse);
    }

    private Response createSuccessResponse(String message, Object data) {
        ApiResponse response = new ApiResponse(true, message, data);
        String jsonResponse = gson.toJson(response);
        return newFixedLengthResponse(Response.Status.OK, MIME_TYPE_JSON, jsonResponse);
    }
}

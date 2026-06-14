package com.gnsslogger.rover;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputFilter;
import android.text.InputType;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String STM32_POINT_CSV_HEADER =
            "point,point_type,ubx_file,start_utc,end_utc,samples,lat_deg,lon_deg,antenna_height_m,antenna_hmsl_m,rod_height_m,point_height_m,point_hmsl_m,fix,carrier,sats,hacc_m,vacc_m\r\n";
    private static final int REQUEST_ATTACH_PHOTO = 42;
    private static final String PREFS_NAME = "rover_logger";
    private static final String PREF_LAST_HC05_ADDRESS = "last_hc05_address";
    private static final String PREF_PROJECT_HISTORY = "project_history";
    private static final String PREF_SOUND_ENABLED = "sound_enabled";
    private static final String PREF_VIBRATION_ENABLED = "vibration_enabled";
    private static final String PREF_POINT_TYPE = "point_type";
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID HM10_UART_SERVICE_UUID =
            UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID HM10_UART_CHAR_UUID =
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    private static final UUID NUS_SERVICE_UUID =
            UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID NUS_RX_UUID =
            UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID NUS_WRITE_UUID =
            UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<PointRecord> points = new ArrayList<>();

    private TextView bluetoothStatus;
    private TextView fixStatus;
    private TextView satStatus;
    private TextView latStatus;
    private TextView lonStatus;
    private TextView pointState;
    private TextView pointDetail;
    private TextView csvStatus;
    private TextView rodStatus;
    private TextView pointList;
    private TextView rawLog;
    private FieldMapView fieldMap;
    private Button connectButton;
    private Button pointCommandButton;
    private Button soundToggleButton;
    private Button vibrationToggleButton;
    private Button gcpTypeButton;
    private Button topoTypeButton;
    private Button clearSdButton;
    private LinearLayout bleDeviceButtons;
    private View pointCard;
    private AlertDialog connectionDialog;
    private AlertDialog sdClearDialog;
    private TextView connectionPromptMessage;
    private TextView sdClearMessage;
    private TextView pointStoredBanner;
    private int pointStoredBannerGeneration = 0;

    private Thread bluetoothThread;
    private volatile boolean bluetoothRunning;
    private BluetoothSocket activeSocket;
    private BluetoothGatt activeGatt;
    private String activeBleAddress = "";
    private BluetoothLeScanner bleScanner;
    private final StringBuilder bleLineBuffer = new StringBuilder();
    private BluetoothDevice bestBleCandidate;
    private BluetoothGattCharacteristic writeCharacteristic;
    private int bestBleRssi = -999;
    private final List<BleCandidate> bleCandidates = new ArrayList<>();
    private int bleCandidateIndex = -1;
    private boolean bleAutoConnectStarted = false;
    private boolean scanAnimationActive = false;
    private int scanAnimationTick = 0;
    private int scanAnimationGeneration = 0;
    private final List<BluetoothGattCharacteristic> notifyCharacteristics = new ArrayList<>();
    private int notifySetupIndex = 0;
    private int bleRxPackets = 0;
    private int bleRxBytes = 0;
    private boolean nmeaStreamStarted = false;
    private File sessionCsvFile;
    private Uri sessionCsvUri;
    private String sessionCsvName = "";
    private String projectName = "PROJECT";
    private String projectFolderName = "";
    private String rodHeightMeters = "2.000";
    private boolean rodPromptShown = false;
    private boolean rodHeightAcked = false;
    private boolean rodRetryActive = false;
    private boolean connectionPromptShown = false;
    private boolean connectionPromptContinuingOffline = false;
    private boolean pointCommandPending = false;
    private boolean pointToggleAcked = false;
    private String pendingBleCommandTag = "";
    private String pendingPointToggleSeq = "";
    private String stm32NextPointId = "";
    private boolean stm32PointActive = false;
    private int pointToggleSeq = 0;
    private int pointToggleRetryGeneration = 0;
    private String pendingDeletePointId = "";
    private int deleteRetryGeneration = 0;
    private boolean deleteAcked = false;
    private int sdClearGeneration = 0;
    private boolean sdClearPending = false;
    private boolean sdClearAcked = false;
    private String blockedDuplicatePointId = "";
    private String activePointId = null;
    private String currentGgaFixLabel = "";
    private PointRecord photoTargetPoint = null;
    private Uri pendingPhotoUri = null;
    private String pendingPhotoName = "";
    private int activeReads = 0;
    private double activeHdStdMeters = 0.0;
    private double activeVdStdMeters = 0.0;
    private double simLat = 37.752057611;
    private double simLon = -121.385391987;
    private int simPoint = 12;
    private ToneGenerator toneGenerator;
    private int averagingBlipGeneration = 0;
    private int averagingSpinnerIndex = 0;
    private boolean soundEnabled = true;
    private boolean vibrationEnabled = true;
    private String currentPointType = "GCP";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        soundEnabled = prefs.getBoolean(PREF_SOUND_ENABLED, true);
        vibrationEnabled = prefs.getBoolean(PREF_VIBRATION_ENABLED, true);
        currentPointType = normalizePointType(prefs.getString(PREF_POINT_TYPE, "GCP"));
        requestBluetoothPermissions();
        setContentView(buildUi());
        promptForProjectChoice();
        appendRaw("#APP_READY");
    }

    @Override
    protected void onDestroy() {
        bluetoothRunning = false;
        stopAveragingBlips();
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ATTACH_PHOTO && photoTargetPoint != null) {
            if (resultCode == RESULT_OK && pendingPhotoUri != null) {
                photoTargetPoint.photoName = pendingPhotoName;
                photoTargetPoint.photoUri = pendingPhotoUri.toString();
                renderPointList();
                saveProjectCache();
                if (fieldMap != null) {
                    fieldMap.invalidate();
                }
                if (csvStatus != null) {
                    csvStatus.setText("Photo saved -> Documents/RoverLogger/"
                            + projectFolderName + "/photos/" + pendingPhotoName);
                }
            } else if (csvStatus != null) {
                csvStatus.setText("Photo not saved");
            }
            photoTargetPoint = null;
            pendingPhotoUri = null;
            pendingPhotoName = "";
        }
    }

    private View buildUi() {
        FrameLayout screen = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 22, 22, 16);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setBackgroundColor(color("F4F6F8"));
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.addView(root);

        LinearLayout header = row();
        TextView title = label("Rover Logger", 24, true);
        bluetoothStatus = chip("SIM");
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(bluetoothStatus);
        root.addView(header);

        LinearLayout statusA = row();
        fixStatus = addStatus(statusA, "Fix", "NO DATA");
        satStatus = addStatus(statusA, "Sats", "--");
        root.addView(statusA);

        LinearLayout statusB = row();
        latStatus = addStatus(statusB, "Lat", "--");
        lonStatus = addStatus(statusB, "Lon", "--");
        root.addView(statusB);

        pointCard = card();
        LinearLayout pointLayout = new LinearLayout(this);
        pointLayout.setOrientation(LinearLayout.VERTICAL);
        pointLayout.setPadding(18, 18, 18, 18);
        ((LinearLayout) pointCard).addView(pointLayout);
        pointLayout.addView(label("Point State", 13, false));
        pointState = label("IDLE", 20, true);
        pointDetail = label("Waiting for #POINT_START", 13, false);
        pointLayout.addView(pointState);
        pointLayout.addView(pointDetail);
        root.addView(pointCard);

        csvStatus = label(sessionCsvFile == null
                && sessionCsvUri == null
                ? "Phone CSV: choose project"
                : "Phone CSV: Documents/RoverLogger/" + projectFolderName + "/" + sessionCsvName, 13, false);
        root.addView(csvStatus);
        rodStatus = label("Rod Height= --", 13, false);
        root.addView(rodStatus);

        LinearLayout pointTypeRow = row();
        TextView pointTypeLabel = label("Point Type", 13, false);
        gcpTypeButton = button("GCP");
        topoTypeButton = button("TOPO");
        gcpTypeButton.setOnClickListener(v -> setPointType("GCP"));
        topoTypeButton.setOnClickListener(v -> setPointType("TOPO"));
        pointTypeRow.addView(pointTypeLabel, new LinearLayout.LayoutParams(0, 52, 1.0f));
        pointTypeRow.addView(gcpTypeButton, new LinearLayout.LayoutParams(0, 52, 1.0f));
        pointTypeRow.addView(topoTypeButton, new LinearLayout.LayoutParams(0, 52, 1.0f));
        root.addView(pointTypeRow, new LinearLayout.LayoutParams(-1, 52));
        updatePointTypeButtons();

        bleDeviceButtons = new LinearLayout(this);
        bleDeviceButtons.setOrientation(LinearLayout.VERTICAL);
        bleDeviceButtons.setVisibility(View.GONE);

        LinearLayout mapHeader = new LinearLayout(this);
        mapHeader.setOrientation(LinearLayout.HORIZONTAL);
        mapHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView liveMapLabel = label("Live Map", 18, true);
        TextView zoomLabel = label("ZOOM", 9, true);
        zoomLabel.setGravity(Gravity.CENTER);
        zoomLabel.setTranslationX(22);
        zoomLabel.setTranslationY(10);
        mapHeader.addView(liveMapLabel, new LinearLayout.LayoutParams(0, -2, 1));
        mapHeader.addView(zoomLabel, new LinearLayout.LayoutParams(70, -2));
        root.addView(mapHeader);
        FrameLayout mapFrame = new FrameLayout(this);
        mapFrame.setClipChildren(false);
        mapFrame.setClipToPadding(false);
        fieldMap = new FieldMapView(this);
        fieldMap.setPointTapListener(this::showPointDetails);
        fieldMap.setBackgroundColor(Color.WHITE);
        mapFrame.addView(fieldMap, new FrameLayout.LayoutParams(-1, -1));
        ZoomWheelView zoomWheel = new ZoomWheelView(this, fieldMap);
        zoomWheel.setTranslationX(22);
        FrameLayout.LayoutParams zoomParams = new FrameLayout.LayoutParams(70, 190,
                Gravity.TOP | Gravity.RIGHT);
        zoomParams.setMargins(0, 14, 0, 0);
        mapFrame.addView(zoomWheel, zoomParams);
        root.addView(mapFrame, new LinearLayout.LayoutParams(-1, 360));
        fieldMap.setVibrationEnabled(vibrationEnabled);
        zoomWheel.setVibrationEnabled(vibrationEnabled);
        LinearLayout mapControls = new LinearLayout(this);
        mapControls.setOrientation(LinearLayout.HORIZONTAL);
        mapControls.setGravity(Gravity.CENTER_VERTICAL);
        Button recenterMap = button("Re-center Map");
        recenterMap.setOnClickListener(v -> fieldMap.recenter());
        soundToggleButton = button("");
        soundToggleButton.setOnClickListener(v -> {
            soundEnabled = !soundEnabled;
            saveFeedbackPrefs();
            updateFeedbackToggleButtons();
        });
        vibrationToggleButton = button("");
        vibrationToggleButton.setOnClickListener(v -> {
            vibrationEnabled = !vibrationEnabled;
            fieldMap.setVibrationEnabled(vibrationEnabled);
            zoomWheel.setVibrationEnabled(vibrationEnabled);
            saveFeedbackPrefs();
            updateFeedbackToggleButtons();
        });
        updateFeedbackToggleButtons();
        mapControls.addView(soundToggleButton, new LinearLayout.LayoutParams(0, 52, 1.0f));
        mapControls.addView(vibrationToggleButton, new LinearLayout.LayoutParams(0, 52, 1.0f));
        mapControls.addView(recenterMap, new LinearLayout.LayoutParams(0, 52, 1.4f));
        root.addView(mapControls, new LinearLayout.LayoutParams(-1, 52));

        TextView storedPointsTitle = label("Stored Points", 18, true);
        storedPointsTitle.setOnClickListener(v -> showStoredPointsDialog());
        root.addView(storedPointsTitle);

        pointList = label("", 14, false);
        pointList.setBackgroundColor(Color.WHITE);
        pointList.setPadding(14, 14, 14, 14);
        pointList.setMaxLines(7);
        pointList.setOnClickListener(v -> showStoredPointsDialog());
        root.addView(pointList, new LinearLayout.LayoutParams(-1, 150));

        LinearLayout clearRow = row();
        clearSdButton = button("Clear SD");
        clearSdButton.setTextSize(13);
        clearSdButton.setTextColor(Color.WHITE);
        clearSdButton.setBackgroundColor(color("DC2626"));
        clearSdButton.setOnClickListener(v -> confirmClearSdCard());
        clearRow.addView(clearSdButton, new LinearLayout.LayoutParams(190, 42));
        clearRow.addView(new View(this), new LinearLayout.LayoutParams(0, 42, 1.0f));
        root.addView(clearRow, new LinearLayout.LayoutParams(-1, 42));

        root.addView(label("Raw Stream", 18, true));
        rawLog = label("", 12, false);
        rawLog.setTextColor(color("E5E7EB"));
        rawLog.setBackgroundColor(color("111827"));
        rawLog.setPadding(14, 14, 14, 14);
        root.addView(rawLog, new LinearLayout.LayoutParams(-1, 72));

        screen.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        pointStoredBanner = label("POINT STORED", 22, true);
        pointStoredBanner.setTextColor(Color.WHITE);
        pointStoredBanner.setGravity(Gravity.CENTER);
        pointStoredBanner.setAlpha(0.0f);
        pointStoredBanner.setVisibility(View.GONE);
        pointStoredBanner.setPadding(28, 16, 28, 16);
        GradientDrawable pointStoredBg = new GradientDrawable();
        pointStoredBg.setColor(color("16A34A"));
        pointStoredBg.setCornerRadius(10);
        pointStoredBanner.setBackground(pointStoredBg);
        FrameLayout.LayoutParams bannerParams = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        bannerParams.setMargins(0, 118, 0, 0);
        screen.addView(pointStoredBanner, bannerParams);

        pointCommandButton = floatingPointButton("START\nPOINT");
        pointCommandButton.setOnClickListener(v -> {
            sendPointToggleCommand();
        });
        FrameLayout.LayoutParams pointFabParams = new FrameLayout.LayoutParams(148, 148,
                Gravity.BOTTOM | Gravity.RIGHT);
        pointFabParams.setMargins(0, 0, 24, 117);
        screen.addView(pointCommandButton, pointFabParams);

        return screen;
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31 && (
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED
                        || checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                        != PackageManager.PERMISSION_GRANTED
                        || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, 10);
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, 10);
        }
    }

    private void connectBluetooth() {
        closeBluetooth();

        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissions();
            setBluetoothStatus("PERMISSION");
            appendRaw("#BT_PERMISSION_REQUEST");
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            setBluetoothStatus("NO BT");
            appendRaw("#BT_NO_ADAPTER");
            return;
        }

        if (!adapter.isEnabled()) {
            setBluetoothStatus("BT OFF");
            appendRaw("#BT_OFF_ENABLE_BLUETOOTH");
            return;
        }

        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        BluetoothDevice hc05 = null;
        int bondedCount = 0;
        for (BluetoothDevice device : bonded) {
            bondedCount += 1;
            String name = device.getName();
            String address = device.getAddress();
            appendRaw("#BT_PAIRED," + (name == null ? "UNKNOWN" : name) + "," + address);
            if ("BE:76:AF:BF:A1:46".equalsIgnoreCase(address)
                    || (name != null && (name.toUpperCase().contains("HC-05")
                    || name.toUpperCase().contains("HC05")))) {
                hc05 = device;
                break;
            }
        }
        appendRaw("#BT_PAIRED_COUNT," + bondedCount);

        if (hc05 == null) {
            setBluetoothStatus("PAIR HC-05");
            appendRaw("#BT_HC05_NOT_FOUND");
            return;
        }

        appendRaw("#BT_SELECTED," + hc05.getName() + "," + hc05.getAddress());
        bluetoothRunning = false;
        BluetoothDevice target = hc05;
        bluetoothThread = new Thread(() -> readBluetooth(target), "hc05-reader");
        bluetoothThread.start();
    }

    private void connectBle() {
        closeBluetooth();
        showBleControls(true);

        if (Build.VERSION.SDK_INT >= 31 && (
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED
                        || checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                        != PackageManager.PERMISSION_GRANTED
                        || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED)) {
            requestBluetoothPermissions();
            setBluetoothStatus("PERMISSION");
            appendRaw("#BLE_PERMISSION_REQUEST");
            return;
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissions();
            setBluetoothStatus("LOCATION?");
            appendRaw("#BLE_NEEDS_LOCATION_PERMISSION");
            return;
        }

        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) {
            setBluetoothStatus("NO BT");
            appendRaw("#BLE_NO_ADAPTER");
            return;
        }
        if (!adapter.isEnabled()) {
            setBluetoothStatus("BT OFF");
            appendRaw("#BLE_OFF_ENABLE_BLUETOOTH");
            return;
        }

        bestBleCandidate = null;
        bestBleRssi = -999;
        bleCandidates.clear();
        bleCandidateIndex = -1;
        bleAutoConnectStarted = false;
        renderBleCandidates();
        bleRxPackets = 0;
        bleRxBytes = 0;
        nmeaStreamStarted = false;
        bleLineBuffer.setLength(0);

        String cachedAddress = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(PREF_LAST_HC05_ADDRESS, "");
        if (cachedAddress != null && !cachedAddress.isEmpty()) {
            try {
                BluetoothDevice cachedDevice = adapter.getRemoteDevice(cachedAddress);
                appendRaw("#BLE_DIRECT_TRY," + cachedAddress);
                connectBleDevice(cachedDevice, "DIRECT_CACHE");
                mainHandler.postDelayed(() -> {
                    if (!nmeaStreamStarted && bleRxPackets == 0 && activeGatt != null) {
                        appendRaw("#BLE_DIRECT_NO_RX_SCAN_FALLBACK");
                        try {
                            activeGatt.disconnect();
                            activeGatt.close();
                        } catch (Exception ignored) {
                        }
                        activeGatt = null;
                        startBleScan(adapter);
                    }
                }, 7000);
                return;
            } catch (IllegalArgumentException ex) {
                appendRaw("#BLE_DIRECT_BAD_ADDRESS," + cachedAddress);
            }
        }

        startBleScan(adapter);
    }

    private void startBleScan(BluetoothAdapter adapter) {
        bleScanner = adapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            setBluetoothStatus("NO SCANNER");
            appendRaw("#BLE_NO_SCANNER");
            return;
        }

        setBluetoothStatus("BLE SCAN");
        appendRaw("#BLE_SCAN_START");
        startScanAnimation();
        bestBleCandidate = null;
        bestBleRssi = -999;
        bleCandidates.clear();
        bleCandidateIndex = -1;
        bleAutoConnectStarted = false;
        renderBleCandidates();
        bleRxPackets = 0;
        bleRxBytes = 0;
        nmeaStreamStarted = false;
        bleLineBuffer.setLength(0);
        bleScanner.startScan(bleScanCallback);
        mainHandler.postDelayed(() -> {
            try {
                if (bleScanner != null) {
                    bleScanner.stopScan(bleScanCallback);
                }
            } catch (SecurityException ignored) {
            }
            if (activeGatt == null) {
                if (!bleCandidates.isEmpty()) {
                    bleCandidates.sort(Comparator.comparingInt((BleCandidate c) -> c.rssi).reversed());
                    connectBleCandidate(0, "SCAN_DONE");
                } else if (bestBleCandidate != null) {
                    connectBleDevice(bestBleCandidate, "BEST_UNKNOWN,rssi=" + bestBleRssi);
                } else {
                    stopScanAnimation();
                    setBluetoothStatus("BLE NOT FOUND");
                    appendRaw("#BLE_SCAN_TIMEOUT");
                }
            }
        }, 40000);
    }

    private final ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = null;
            try {
                name = device.getName();
            } catch (SecurityException ignored) {
            }
            if (name == null && result.getScanRecord() != null) {
                name = result.getScanRecord().getDeviceName();
            }
            String safeName = name == null ? "UNKNOWN" : name;
            if (!isHc05Name(safeName)) {
                return;
            }
            stopScanAnimation();
            appendRaw("#BLE_HC05_FOUND," + safeName + "," + device.getAddress()
                    + ",rssi=" + result.getRssi());
            saveLastHc05Address(device.getAddress());
            addBleCandidate(device, safeName, result.getRssi());

            if (result.getRssi() > bestBleRssi) {
                bestBleRssi = result.getRssi();
                bestBleCandidate = device;
            }

            if (!bleAutoConnectStarted) {
                bleAutoConnectStarted = true;
                appendRaw("#BLE_HC05_AUTO_CONNECT," + device.getAddress());
                try {
                    bleScanner.stopScan(this);
                } catch (SecurityException ignored) {
                }
                mainHandler.postDelayed(() -> {
                    bleCandidates.sort(Comparator.comparingInt((BleCandidate c) -> c.rssi).reversed());
                    int index = findBleCandidateIndex(device);
                    connectBleCandidate(index < 0 ? 0 : index, "AUTO_HC05");
                }, 500);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            stopScanAnimation();
            setBluetoothStatus("SCAN FAIL");
            appendRaw("#BLE_SCAN_FAIL," + errorCode);
        }
    };

    private final BluetoothGattCallback bleGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            appendRawOnMain("#BLE_STATE,status=" + status + ",state=" + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                setBluetoothStatus("BLE DISCOVER");
                try {
                    gatt.discoverServices();
                } catch (SecurityException ex) {
                    appendRawOnMain("#BLE_DISCOVER_SECURITY," + ex.getMessage());
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                setBluetoothStatus("BLE DISC");
                appendRawOnMain("#BLE_DISCONNECTED");
                rodRetryActive = false;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            appendRawOnMain("#BLE_SERVICES,status=" + status);
            BluetoothGattCharacteristic serialChar = findSerialCharacteristic(gatt);
            writeCharacteristic = findWriteCharacteristic(gatt);
            notifyCharacteristics.clear();
            collectNotifyCharacteristics(gatt);
            if (notifyCharacteristics.isEmpty() && serialChar != null) {
                notifyCharacteristics.add(serialChar);
            }

            if (notifyCharacteristics.isEmpty()) {
                setBluetoothStatus("NO UART");
                appendRawOnMain("#BLE_NO_UART_CHARACTERISTIC");
                return;
            }
            notifySetupIndex = 0;
            enableNextNotification(gatt);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            handleBleBytes(characteristic.getValue());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic,
                                            byte[] value) {
            handleBleBytes(value);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            appendRawOnMain("#BLE_WRITE_DONE,status=" + status);
            String tag = pendingBleCommandTag;
            pendingBleCommandTag = "";
            if ("POINT_TOGGLE".equals(tag) && status != BluetoothGatt.GATT_SUCCESS) {
                mainHandler.post(() -> {
                    pointCommandPending = false;
                    if (pointDetail != null) {
                        pointDetail.setText("BLE write failed");
                    }
                    renderPointState();
                });
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor,
                                      int status) {
            appendRawOnMain("#BLE_CCCD_DONE,status=" + status);
            enableNextNotification(gatt);
        }
    };

    private void addBleCandidate(BluetoothDevice device, String name, int rssi) {
        for (BleCandidate candidate : bleCandidates) {
            if (candidate.device.getAddress().equals(device.getAddress())) {
                candidate.name = name;
                candidate.rssi = rssi;
                renderBleCandidates();
                return;
            }
        }
        BleCandidate candidate = new BleCandidate();
        candidate.device = device;
        candidate.name = name;
        candidate.rssi = rssi;
        bleCandidates.add(candidate);
        renderBleCandidates();
    }

    private boolean isHc05Name(String name) {
        return "HC-05".equalsIgnoreCase(name) || "HC05".equalsIgnoreCase(name);
    }

    private void renderBleCandidates() {
        mainHandler.post(() -> {
            if (bleDeviceButtons == null) return;
            bleDeviceButtons.removeAllViews();
            if (bleCandidates.isEmpty()) return;

            bleCandidates.sort(Comparator.comparingInt((BleCandidate c) -> c.rssi).reversed());
            int count = Math.min(bleCandidates.size(), 8);
            for (int i = 0; i < count; i++) {
                BleCandidate candidate = bleCandidates.get(i);
                String name = candidate.name == null ? "UNKNOWN" : candidate.name;
                String address = candidate.device.getAddress();
                Button deviceButton = button(name + "  " + candidate.rssi + "dBm\n" + address);
                final int index = i;
                deviceButton.setOnClickListener(v -> {
                    appendRaw("#BLE_MANUAL_SELECT,idx=" + index + "," + name + "," + address);
                    try {
                        if (bleScanner != null) {
                            bleScanner.stopScan(bleScanCallback);
                        }
                    } catch (SecurityException ignored) {
                    }
                    if (activeGatt != null) {
                        try {
                            activeGatt.disconnect();
                            activeGatt.close();
                        } catch (Exception ignored) {
                        }
                        activeGatt = null;
                    }
                    connectBleCandidate(index, "MANUAL");
                });
                bleDeviceButtons.addView(deviceButton, new LinearLayout.LayoutParams(-1, 66));
            }
        });
    }

    private int findBleCandidateIndex(BluetoothDevice device) {
        for (int i = 0; i < bleCandidates.size(); i++) {
            if (bleCandidates.get(i).device.getAddress().equals(device.getAddress())) {
                return i;
            }
        }
        return -1;
    }

    private void connectBleCandidate(int index, String reason) {
        if (index < 0 || index >= bleCandidates.size()) {
            setBluetoothStatus("BLE NO RX");
            appendRaw("#BLE_NO_MORE_CANDIDATES");
            return;
        }
        bleCandidateIndex = index;
        BleCandidate candidate = bleCandidates.get(index);
        connectBleDevice(candidate.device, reason + ",idx=" + index
                + ",name=" + candidate.name
                + ",rssi=" + candidate.rssi);
    }

    private void connectBleDevice(BluetoothDevice device, String reason) {
        stopScanAnimation();
        setBluetoothStatus("BLE CONNECT");
        bleRxPackets = 0;
        bleRxBytes = 0;
        bleLineBuffer.setLength(0);
        writeCharacteristic = null;
        rodHeightAcked = false;
        rodRetryActive = false;
        activeBleAddress = device.getAddress();
        appendRaw("#BLE_SELECTED," + reason + "," + device.getAddress());
        activeGatt = device.connectGatt(MainActivity.this, false, bleGattCallback);
    }

    private void tryNextBleCandidate(BluetoothGatt gatt) {
        if (bleRxPackets > 0 || gatt != activeGatt) return;
        appendRaw("#BLE_NO_RX_TRY_NEXT");
        try {
            gatt.disconnect();
            gatt.close();
        } catch (Exception ignored) {
        }
        activeGatt = null;
        connectBleCandidate(bleCandidateIndex + 1, "NO_RX");
    }

    private BluetoothGattCharacteristic findSerialCharacteristic(BluetoothGatt gatt) {
        BluetoothGattService hm10 = gatt.getService(HM10_UART_SERVICE_UUID);
        if (hm10 != null) {
            BluetoothGattCharacteristic ch = hm10.getCharacteristic(HM10_UART_CHAR_UUID);
            if (ch != null) {
                appendRawOnMain("#BLE_UART,HM10_FFE1");
                return ch;
            }
        }

        BluetoothGattService nus = gatt.getService(NUS_SERVICE_UUID);
        if (nus != null) {
            BluetoothGattCharacteristic ch = nus.getCharacteristic(NUS_RX_UUID);
            if (ch != null) {
                appendRawOnMain("#BLE_UART,NORDIC_NUS");
                return ch;
            }
        }

        for (BluetoothGattService service : gatt.getServices()) {
            appendRawOnMain("#BLE_SERVICE," + service.getUuid());
            for (BluetoothGattCharacteristic ch : service.getCharacteristics()) {
                int props = ch.getProperties();
                appendRawOnMain("#BLE_CHAR," + ch.getUuid() + ",props=" + props);
                if ((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                        || (props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                    appendRawOnMain("#BLE_UART,FIRST_NOTIFY");
                    return ch;
                }
            }
        }
        return null;
    }

    private BluetoothGattCharacteristic findWriteCharacteristic(BluetoothGatt gatt) {
        BluetoothGattService hm10 = gatt.getService(HM10_UART_SERVICE_UUID);
        if (hm10 != null) {
            BluetoothGattCharacteristic ch = hm10.getCharacteristic(HM10_UART_CHAR_UUID);
            if (isWritable(ch)) {
                appendRawOnMain("#BLE_WRITE,HM10_FFE1");
                return ch;
            }
        }

        BluetoothGattService nus = gatt.getService(NUS_SERVICE_UUID);
        if (nus != null) {
            BluetoothGattCharacteristic ch = nus.getCharacteristic(NUS_WRITE_UUID);
            if (isWritable(ch)) {
                appendRawOnMain("#BLE_WRITE,NORDIC_NUS");
                return ch;
            }
        }

        for (BluetoothGattService service : gatt.getServices()) {
            for (BluetoothGattCharacteristic ch : service.getCharacteristics()) {
                if (isWritable(ch)) {
                    appendRawOnMain("#BLE_WRITE,FIRST_WRITE," + ch.getUuid());
                    return ch;
                }
            }
        }
        appendRawOnMain("#BLE_NO_WRITE_CHAR");
        return null;
    }

    private boolean isWritable(BluetoothGattCharacteristic ch) {
        if (ch == null) return false;
        int props = ch.getProperties();
        return (props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                || (props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
    }

    private void collectNotifyCharacteristics(BluetoothGatt gatt) {
        for (BluetoothGattService service : gatt.getServices()) {
            for (BluetoothGattCharacteristic ch : service.getCharacteristics()) {
                int props = ch.getProperties();
                if ((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                        || (props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                    if (!notifyCharacteristics.contains(ch)) {
                        notifyCharacteristics.add(ch);
                    }
                }
            }
        }
        appendRawOnMain("#BLE_NOTIFY_COUNT," + notifyCharacteristics.size());
    }

    private void enableNextNotification(BluetoothGatt gatt) {
        if (notifySetupIndex >= notifyCharacteristics.size()) {
            setBluetoothStatus("BLE OK");
            appendRawOnMain("#BLE_CONNECTED_UART");
            if (activeBleAddress != null && !activeBleAddress.isEmpty()) {
                saveLastHc05Address(activeBleAddress);
            }
            mainHandler.postDelayed(() -> requestStm32NextPoint(), 800);
            mainHandler.postDelayed(() -> sendPointTypeCommand(), 1200);
            if (rodPromptShown && !rodHeightAcked) {
                mainHandler.post(() -> startRodHeightRetry());
            }
            mainHandler.postDelayed(() -> tryNextBleCandidate(gatt), 5000);
            return;
        }
        BluetoothGattCharacteristic ch = notifyCharacteristics.get(notifySetupIndex);
        notifySetupIndex += 1;
        enableNotifications(gatt, ch);
    }

    private void saveLastHc05Address(String address) {
        if (address == null || address.isEmpty()) return;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_LAST_HC05_ADDRESS, address)
                .apply();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        appendRaw("#PERMISSIONS_RESULT");
    }

    private void enableNotifications(BluetoothGatt gatt, BluetoothGattCharacteristic ch) {
        try {
            appendRawOnMain("#BLE_NOTIFY_CHAR," + ch.getUuid());
            boolean ok = gatt.setCharacteristicNotification(ch, true);
            appendRawOnMain("#BLE_NOTIFY_SET," + ok);
            BluetoothGattDescriptor descriptor = ch.getDescriptor(CCCD_UUID);
            if (descriptor != null) {
                int props = ch.getProperties();
                if ((props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                        && (props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                } else {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                }
                boolean writeOk = gatt.writeDescriptor(descriptor);
                appendRawOnMain("#BLE_CCCD_WRITE," + writeOk);
            } else {
                appendRawOnMain("#BLE_NO_CCCD");
                enableNextNotification(gatt);
            }
        } catch (SecurityException ex) {
            setBluetoothStatus("BLE SEC ERR");
            appendRawOnMain("#BLE_NOTIFY_SECURITY," + sanitize(ex.getMessage()));
        }
    }

    private void startRodHeightRetry() {
        if (rodRetryActive) return;
        rodRetryActive = true;
        sendPendingRodHeight();
    }

    private void sendPendingRodHeight() {
        if (rodHeightAcked) {
            rodRetryActive = false;
            return;
        }
        if (rodHeightMeters == null || rodHeightMeters.isEmpty()) return;
        if (activeGatt == null || writeCharacteristic == null) {
            if (rodStatus != null) rodStatus.setText("Rod Height= " + rodHeightMeters + "M pending");
            appendRaw("#ROD_WRITE_PENDING");
            scheduleRodRetry();
            return;
        }

        int props = writeCharacteristic.getProperties();
        int writeType = ((props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
                ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        byte[] data = ("#ROD," + rodHeightMeters + "\r\n").getBytes(StandardCharsets.US_ASCII);

        try {
            writeCharacteristic.setWriteType(writeType);
            writeCharacteristic.setValue(data);
            boolean ok = activeGatt.writeCharacteristic(writeCharacteristic);
            appendRaw("#ROD_SEND," + rodHeightMeters + ",ok=" + ok);
            if (rodStatus != null) rodStatus.setText("Rod Height= " + rodHeightMeters + "M waiting");
            scheduleRodRetry();
        } catch (SecurityException ex) {
            appendRaw("#ROD_SEND_SECURITY," + sanitize(ex.getMessage()));
            if (rodStatus != null) rodStatus.setText("Rod Height send blocked");
            scheduleRodRetry();
        }
    }

    private void sendPointToggleCommand() {
        String requested = activePointId == null ? "START" : "STORE";
        if ("START".equals(requested)) {
            playStartButtonPressedSound();
        }
        pointToggleSeq = (pointToggleSeq + 1) & 0x7FFF;
        if (pointToggleSeq == 0) {
            pointToggleSeq = 1;
        }
        pendingPointToggleSeq = Integer.toString(pointToggleSeq);
        pointToggleAcked = false;
        pointToggleRetryGeneration += 1;
        if (sendPointToggleAttempt(pointToggleRetryGeneration)) {
            pointCommandPending = true;
            if (pointDetail != null) {
                pointDetail.setText("Command sent: " + requested + " point");
            }
            if (pointCommandButton != null) {
                pointCommandButton.setText("WAITING");
                setFloatingPointButtonColor(pointCommandButton, "DC2626");
                pointCommandButton.setEnabled(false);
                pulsePointCommandButton();
            }
            schedulePointToggleRetry(pointToggleRetryGeneration);
            schedulePointToggleTimeout(pointToggleRetryGeneration);
        } else if (pointDetail != null) {
            pointDetail.setText("BLE write not ready");
        }
    }

    private boolean sendPointToggleAttempt(int generation) {
        if (!pointCommandPending && generation != pointToggleRetryGeneration) {
            return false;
        }
        if (pendingPointToggleSeq == null || pendingPointToggleSeq.isEmpty()) {
            return false;
        }
        return sendBleAsciiCommand("#POINT_TOGGLE," + pendingPointToggleSeq + "\r\n",
                "POINT_TOGGLE");
    }

    private void requestStm32NextPoint() {
        sendBleAsciiCommand("#POINT_NEXT?\r\n", "POINT_NEXT");
    }

    private void sendPointTypeCommand() {
        sendBleAsciiCommand("#POINT_TYPE," + currentPointType + "\r\n", "POINT_TYPE");
    }

    private void schedulePointToggleRetry(int generation) {
        mainHandler.postDelayed(() -> {
            if (pointCommandPending
                    && !pointToggleAcked
                    && generation == pointToggleRetryGeneration) {
                appendRaw("#POINT_TOGGLE_RETRY," + pendingPointToggleSeq);
                pulsePointCommandButton();
                sendPointToggleAttempt(generation);
                schedulePointToggleRetry(generation);
            }
        }, 1200);
    }

    private void schedulePointToggleTimeout(int generation) {
        mainHandler.postDelayed(() -> {
            if (pointCommandPending && generation == pointToggleRetryGeneration) {
                pointCommandPending = false;
                pointToggleAcked = false;
                pendingPointToggleSeq = "";
                renderPointState();
                appendRaw("#POINT_TOGGLE_TIMEOUT");
            }
        }, 7000);
    }

    private boolean sendBleAsciiCommand(String command, String tag) {
        if (activeGatt == null || writeCharacteristic == null) {
            appendRaw("#" + tag + "_SEND_PENDING");
            return false;
        }

        int props = writeCharacteristic.getProperties();
        int writeType = ((props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
                ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        byte[] data = command.getBytes(StandardCharsets.US_ASCII);

        try {
            writeCharacteristic.setWriteType(writeType);
            writeCharacteristic.setValue(data);
            boolean ok = activeGatt.writeCharacteristic(writeCharacteristic);
            pendingBleCommandTag = ok ? tag : "";
            appendRaw("#" + tag + "_SEND,ok=" + ok);
            return ok;
        } catch (SecurityException ex) {
            appendRaw("#" + tag + "_SEND_SECURITY," + sanitize(ex.getMessage()));
            return false;
        }
    }

    private void scheduleRodRetry() {
        if (!rodRetryActive || rodHeightAcked) return;
        mainHandler.postDelayed(() -> {
            if (rodRetryActive && !rodHeightAcked) {
                sendPendingRodHeight();
            }
        }, 2000);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void handleBleBytes(byte[] value) {
        if (value == null || value.length == 0) return;
        mainHandler.post(() -> {
            bleRxPackets += 1;
            bleRxBytes += value.length;
            appendRaw("#BLE_RX,packets=" + bleRxPackets
                    + ",len=" + value.length
                    + ",total=" + bleRxBytes);
            for (byte b : value) {
                char ch = (char) (b & 0xff);
                if (ch == '\n' || ch == '\r') {
                    if (bleLineBuffer.length() > 0) {
                        String line = bleLineBuffer.toString();
                        bleLineBuffer.setLength(0);
                        feedLine(line);
                    }
                } else {
                    bleLineBuffer.append(ch);
                    if (bleLineBuffer.length() > 768) {
                        String line = bleLineBuffer.toString();
                        bleLineBuffer.setLength(0);
                        feedLine(line);
                    }
                }
            }
        });
    }

    private void readBluetooth(BluetoothDevice device) {
        bluetoothRunning = true;
        setBluetoothStatus("CONNECTING");
        BluetoothSocket socket = null;
        try {
            socket = connectSerialSocket(device);
            activeSocket = socket;
            setBluetoothStatus("CONNECTED");
            appendRawOnMain("#BT_CONNECTED," + device.getName());
            InputStream input = socket.getInputStream();
            StringBuilder lineBuffer = new StringBuilder();
            byte[] buffer = new byte[128];
            while (bluetoothRunning) {
                int count = input.read(buffer);
                if (count < 0) break;
                for (int i = 0; i < count; i++) {
                    char ch = (char) (buffer[i] & 0xff);
                    if (ch == '\n' || ch == '\r') {
                        if (lineBuffer.length() > 0) {
                            String incoming = lineBuffer.toString();
                            lineBuffer.setLength(0);
                            mainHandler.post(() -> feedLine(incoming));
                        }
                    } else {
                        lineBuffer.append(ch);
                    if (lineBuffer.length() > 768) {
                            String incoming = lineBuffer.toString();
                            lineBuffer.setLength(0);
                            mainHandler.post(() -> feedLine(incoming));
                        }
                    }
                }
            }
            appendRawOnMain("#BT_DISCONNECTED");
        } catch (IOException ex) {
            setBluetoothStatus("BT ERROR");
            appendRawOnMain("#BT_ERROR," + ex.getClass().getSimpleName() + "," + ex.getMessage());
        } finally {
            activeSocket = null;
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private BluetoothSocket connectSerialSocket(BluetoothDevice device) throws IOException {
        IOException lastError = null;

        for (int attempt = 0; attempt < 4; attempt++) {
            BluetoothSocket socket = null;
            try {
                if (attempt == 0) {
                    appendRawOnMain("#BT_CONNECT_TRY,INSECURE_CHANNEL_1");
                    socket = openInsecureChannelOneSocket(device);
                } else if (attempt == 1) {
                    appendRawOnMain("#BT_CONNECT_TRY,CHANNEL_1");
                    socket = openChannelOneSocket(device);
                } else if (attempt == 2) {
                    appendRawOnMain("#BT_CONNECT_TRY,INSECURE_UUID");
                    socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
                } else {
                    appendRawOnMain("#BT_CONNECT_TRY,SECURE_UUID");
                    socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                }

                socket.connect();
                appendRawOnMain("#BT_CONNECT_OK,ATTEMPT=" + attempt);
                return socket;
            } catch (IOException ex) {
                lastError = ex;
                appendRawOnMain("#BT_CONNECT_FAIL," + ex.getClass().getSimpleName()
                        + "," + sanitize(ex.getMessage()));
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        throw lastError == null ? new IOException("No Bluetooth socket attempt made") : lastError;
    }

    private BluetoothSocket openChannelOneSocket(BluetoothDevice device) throws IOException {
        try {
            Method method = device.getClass().getMethod("createRfcommSocket", int.class);
            return (BluetoothSocket) method.invoke(device, 1);
        } catch (Exception reflectionError) {
            throw new IOException("Could not create RFCOMM channel 1 socket", reflectionError);
        }
    }

    private BluetoothSocket openInsecureChannelOneSocket(BluetoothDevice device) throws IOException {
        try {
            Method method = device.getClass().getMethod("createInsecureRfcommSocket", int.class);
            return (BluetoothSocket) method.invoke(device, 1);
        } catch (Exception reflectionError) {
            throw new IOException("Could not create insecure RFCOMM channel 1 socket", reflectionError);
        }
    }

    private void feedLine(String line) {
        appendRaw(line);
        String clean = line.trim();
        if (clean.startsWith("$") && clean.contains("GGA")) {
            parseGga(clean);
        } else if (clean.startsWith("$") && clean.contains("VTG")) {
            parseVtg(clean);
        } else if (clean.startsWith("$") && clean.contains("RMC")) {
            parseRmc(clean);
        } else if (clean.startsWith("#POINT_START")) {
            String[] fields = clean.split(",");
            activePointId = fields.length > 1 ? fields[1] : "";
            activeReads = 0;
            activeHdStdMeters = 0.0;
            activeVdStdMeters = 0.0;
            pointCommandPending = false;
            pointToggleAcked = false;
            pendingPointToggleSeq = "";
            renderPointState();
            startAveragingBlips();
        } else if (clean.startsWith("#POINT_STATUS")) {
            String[] fields = clean.split(",");
            for (int i = 2; i < fields.length; i++) {
                if (fields[i].startsWith("READS=")) {
                    activeReads = parseInt(fields[i].substring(6), activeReads);
                } else if (fields[i].startsWith("HSTD=")) {
                    activeHdStdMeters = parseDouble(fields[i].substring(5), 0) / 1000.0;
                } else if (fields[i].startsWith("VSTD=")) {
                    activeVdStdMeters = parseDouble(fields[i].substring(5), 0) / 1000.0;
                }
            }
            renderPointState();
        } else if (clean.startsWith("#POINT_NEXT")) {
            parsePointNext(clean);
        } else if (clean.startsWith("#POINT_STORE")) {
            parsePointStore(clean);
        } else if (clean.startsWith("#POINT_CSV")) {
            parsePointCsv(clean);
        } else if (clean.startsWith("#POINT_DELETE_OK")) {
            parsePointDeleteOk(clean);
        } else if (clean.startsWith("#POINT_DELETE_ERR")) {
            appendRaw("#POINT_DELETE_ERR_SEEN");
            String[] fields = clean.split(",");
            String pointId = fields.length > 1 ? fields[1].trim() : pendingDeletePointId;
            String code = fields.length > 2 ? fields[2].trim() : "?";
            PointRecord point = findPointById(pointId);
            if (point != null && "4".equals(code)) {
                point.sdStatus = "missing";
                saveProjectCache();
            }
            deleteAcked = false;
            pendingDeletePointId = "";
            if (csvStatus != null) {
                csvStatus.setText(deleteErrorMessage(pointId, code));
            }
        } else if (clean.startsWith("#SD_CLEAR_OK")) {
            parseSdClearOk(clean);
        } else if (clean.startsWith("#SD_CLEAR_ERR")) {
            parseSdClearErr(clean);
        } else if (clean.startsWith("#SD_CLEAR_QUEUED")) {
            sdClearAcked = true;
            updateSdClearStatus("STM32 queued SD clear");
        } else if (clean.startsWith("#SD_CLEAR_START")) {
            updateSdClearStatus("STM32 clearing SD...\nThis can take several minutes.");
        } else if (clean.startsWith("#SD_CLEAR_PROGRESS")) {
            String[] fields = clean.split(",");
            String count = fields.length >= 2 ? fields[1].trim() : "?";
            updateSdClearStatus("STM32 clearing SD...\nDeleted " + count
                    + " file(s) so far.\nThis can take several minutes.");
        } else if (clean.startsWith("#POINT_TOGGLE_OK")) {
            String[] fields = clean.split(",");
            String seq = fields.length > 1 ? fields[1].trim() : pendingPointToggleSeq;
            if (pointCommandPending
                    && (pendingPointToggleSeq.isEmpty() || pendingPointToggleSeq.equals(seq))) {
                pointToggleAcked = true;
                if (pointDetail != null) {
                    pointDetail.setText("STM32 received command");
                }
            }
        } else if (clean.startsWith("#ROD_OK")) {
            String[] fields = clean.split(",");
            String value = normalizeRodHeight(fields.length > 1 ? fields[1] : rodHeightMeters);
            rodHeightAcked = true;
            rodRetryActive = false;
            if (rodStatus != null) rodStatus.setText("Rod Height= " + value + "M");
        } else if (clean.startsWith("#ROD_ERR")) {
            rodHeightAcked = false;
            rodRetryActive = true;
            if (rodStatus != null) rodStatus.setText("Rod Height update failed");
            scheduleRodRetry();
        }
    }

    private void parseGga(String line) {
        String[] parts = line.split("\\*")[0].split(",");
        if (parts.length < 10) return;
        Double lat = nmeaCoord(parts[2], parts[3]);
        Double lon = nmeaCoord(parts[4], parts[5]);
        String fix = fixName(parts[6]);
        String sats = parts[7];

        currentGgaFixLabel = fix;
        fixStatus.setText(fix);
        satStatus.setText(sats);
        latStatus.setText(lat == null ? "--" : String.format("%.9f", lat));
        lonStatus.setText(lon == null ? "--" : String.format("%.9f", lon));
        if (lat != null && lon != null && fieldMap != null) {
            fieldMap.setCurrent(lat, lon);
        }
        if (!nmeaStreamStarted) {
            nmeaStreamStarted = true;
            showBleControls(false);
            handleConnectionSuccessful();
            mainHandler.postDelayed(() -> {
                if (!rodPromptShown) {
                    promptForRodHeight();
                }
            }, 950);
        }
    }

    private void parseVtg(String line) {
        String[] parts = line.split("\\*")[0].split(",");
        if (parts.length > 1 && fieldMap != null) {
            Double course = parseNullableDouble(parts[1]);
            if (course != null) {
                fieldMap.setCourseDegrees(course);
            }
        }
    }

    private void parseRmc(String line) {
        String[] parts = line.split("\\*")[0].split(",");
        if (parts.length > 8 && fieldMap != null) {
            Double course = parseNullableDouble(parts[8]);
            if (course != null) {
                fieldMap.setCourseDegrees(course);
            }
        }
    }

    private void parsePointNext(String line) {
        String[] fields = line.split(",");
        if (fields.length < 2 || "ERR".equals(fields[1])) {
            stm32NextPointId = "";
            stm32PointActive = false;
            if (csvStatus != null) {
                csvStatus.setText("STM32 point status unavailable");
            }
            renderPointState();
            return;
        }

        stm32NextPointId = fields[1].trim();
        stm32PointActive = false;
        for (int i = 2; i < fields.length; i++) {
            if ("ACTIVE=1".equals(fields[i].trim())) {
                stm32PointActive = true;
            }
        }

        renderPointState();
        updatePointNumberStatus();
    }

    private void parsePointStore(String line) {
        String[] fields = line.split(",");
        if (fields.length < 5) return;
        PointRecord record = new PointRecord();
        record.id = fields[1].trim();
        if (pointIdExists(record.id)) {
            blockedDuplicatePointId = record.id;
            stopAveragingBlips();
            activePointId = null;
            activeReads = 0;
            pointCommandPending = false;
            pointToggleAcked = false;
            pendingPointToggleSeq = "";
            renderPointState();
            renderPointList();
            if (fieldMap != null) {
                fieldMap.setPoints(points);
            }
            if (csvStatus != null) {
                csvStatus.setText("Duplicate " + record.id
                        + " blocked. Open a new project or set next point number.");
            }
            appendRaw("#DUPLICATE_POINT_BLOCKED," + record.id);
            playTone(ToneGenerator.TONE_PROP_NACK, 220);
            return;
        }
        record.lat = parseDouble(fields[2], 0);
        record.lon = parseDouble(fields[3], 0);
        record.height = parseDouble(fields[4], 0);
        record.pointType = currentPointType;
        record.ggaFixLabel = currentGgaFixLabel;
        record.addedAnimationStartMs = System.currentTimeMillis();
        for (int i = 5; i < fields.length; i++) {
            if (fields[i].startsWith("READS=")) record.reads = parseInt(fields[i].substring(6), 0);
            if (fields[i].startsWith("FIX=")) record.fix = fields[i].substring(4);
            if (fields[i].startsWith("CARRIER=")) record.carrier = parseInt(fields[i].substring(8), -1);
            if (fields[i].startsWith("HACC=")) record.hacc = parseDouble(fields[i].substring(5), 0) / 1000.0;
            if (fields[i].startsWith("VACC=")) record.vacc = parseDouble(fields[i].substring(5), 0) / 1000.0;
            if (fields[i].startsWith("UBX=")) record.ubx = fields[i].substring(4);
            if (fields[i].startsWith("TYPE=")) record.pointType = normalizePointType(fields[i].substring(5));
        }
        record.csvRow = buildCsvRowForCache(record);
        points.add(record);
        stm32NextPointId = "";
        stopAveragingBlips();
        activePointId = null;
        activeReads = 0;
        pointCommandPending = false;
        pointToggleAcked = false;
        pendingPointToggleSeq = "";
        renderPointState();
        renderPointList();
        saveProjectCache();
        if (fieldMap != null) {
            fieldMap.setPoints(points);
        }
        showPointStoredBanner(record.id);
        playPointStoredSound();
        vibratePointStored();
    }

    private void simulateNmea() {
        simLat += (Math.random() - 0.5) * 0.000002;
        simLon += (Math.random() - 0.5) * 0.000002;
        feedLine(makeGga(simLat, simLon));
        if (activePointId != null) {
            activeReads += 1;
            feedLine("#POINT_STATUS," + activePointId + ",READS=" + activeReads);
        }
    }

    private void simulateStore() {
        if (activePointId == null) activePointId = "P" + pad3(simPoint);
        feedLine("#POINT_STORE," + activePointId + ","
                + String.format("%.9f", simLat) + ","
                + String.format("%.9f", simLon)
                + ",100.345,READS=" + Math.max(activeReads, 42)
                + ",FIX=RTK_FIXED,HACC=12,VACC=18");
        simPoint += 1;
    }

    private void renderPointState() {
        if (activePointId == null) {
            pointCard.setBackgroundColor(Color.WHITE);
            pointState.setText("IDLE");
            pointDetail.setText("Ready to start point");
            if (pointCommandButton != null) {
                pointCommandButton.setText(pointCommandPending ? "WAITING" : "START\nPOINT");
                setFloatingPointButtonColor(pointCommandButton,
                        pointCommandPending ? "DC2626" : "2563EB");
                pointCommandButton.setEnabled(!pointCommandPending);
                if (!pointCommandPending) {
                    pointCommandButton.setScaleX(1.0f);
                    pointCommandButton.setScaleY(1.0f);
                }
            }
        } else {
            pointCard.setBackgroundColor(color("FFF7ED"));
            pointState.setText("AVERAGING " + activePointId + " " + averagingSpinner());
            pointDetail.setText("Reads: " + activeReads
                    + "  \u0394H=" + String.format(Locale.US, "%.3f", activeHdStdMeters) + "m"
                    + "  \u0394V=" + String.format(Locale.US, "%.3f", activeVdStdMeters) + "m");
            if (pointCommandButton != null) {
                pointCommandButton.setText(pointCommandPending ? "WAITING" : "STORE\nPOINT");
                setFloatingPointButtonColor(pointCommandButton,
                        pointCommandPending ? "DC2626" : "16A34A");
                pointCommandButton.setEnabled(!pointCommandPending);
            }
        }
    }

    private void renderPointList() {
        if (pointList == null) return;
        pointList.setText(buildPointListText());
    }

    private String buildPointListText() {
        StringBuilder text = new StringBuilder();
        for (PointRecord p : points) {
            if (p.deleted) continue;
            text.append(p.id).append("  ").append(p.pointType).append("  ")
                    .append(String.format("%.9f", p.lat)).append(", ")
                    .append(String.format("%.9f", p.lon)).append("\n")
                    .append("H=").append(String.format("%.3f", p.height))
                    .append("m  Reads=").append(p.reads)
                    .append("  Fix=").append(p.fix).append("\n")
                    .append("HACC=").append(String.format("%.3f", p.hacc))
                    .append("m  VACC=").append(String.format("%.3f", p.vacc))
                    .append("m");
            if (p.ubx != null && !p.ubx.isEmpty()) {
                text.append("  ").append(p.ubx);
            }
            text.append("  ").append(pointFixLabel(p));
            if (p.photoName != null && !p.photoName.isEmpty()) {
                text.append("  PHOTO");
            }
            text.append("\n\n");
        }
        if (text.length() == 0) {
            return "No stored points";
        }
        return text.toString();
    }

    private void showStoredPointsDialog() {
        ScrollView scroll = new ScrollView(this);
        TextView fullList = label(buildPointListText(), 14, false);
        fullList.setTextColor(color("1F2933"));
        fullList.setPadding(24, 18, 24, 18);
        scroll.setBackgroundColor(Color.WHITE);
        scroll.addView(fullList, new ScrollView.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Stored Points")
                .setView(scroll)
                .setPositiveButton("Minimize", null)
                .show();
        addDialogButtonHaptics(dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(-1, -2);
        }
    }

    private void showPointDetails(PointRecord point) {
        StringBuilder message = new StringBuilder();
        message.append("Type: ").append(point.pointType).append("\n")
                .append("Fix: ").append(pointFixLabel(point)).append("\n")
                .append("Lat: ").append(String.format(Locale.US, "%.9f", point.lat)).append("\n")
                .append("Lon: ").append(String.format(Locale.US, "%.9f", point.lon)).append("\n")
                .append("Height: ").append(String.format(Locale.US, "%.3fm", point.height)).append("\n")
                .append("SD status: ").append(point.sdStatus).append("\n")
                .append("Reads: ").append(point.reads).append("\n")
                .append("HACC: ").append(String.format(Locale.US, "%.3fm", point.hacc)).append("\n")
                .append("VACC: ").append(String.format(Locale.US, "%.3fm", point.vacc));
        if (point.photoName != null && !point.photoName.isEmpty()) {
            message.append("\nPhoto: photos/").append(point.photoName);
        }

        LinearLayout detailLayout = new LinearLayout(this);
        detailLayout.setOrientation(LinearLayout.VERTICAL);
        detailLayout.setPadding(28, 12, 28, 0);
        if (point.photoUri != null && !point.photoUri.isEmpty()) {
            ImageView thumbnail = new ImageView(this);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try {
                Bitmap bitmap;
                if (Build.VERSION.SDK_INT >= 29) {
                    bitmap = getContentResolver().loadThumbnail(Uri.parse(point.photoUri),
                            new android.util.Size(520, 320), null);
                } else {
                    bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(),
                            Uri.parse(point.photoUri));
                }
                thumbnail.setImageBitmap(bitmap);
                detailLayout.addView(thumbnail, new LinearLayout.LayoutParams(-1, 320));
            } catch (Exception ex) {
                TextView missing = label("Photo thumbnail unavailable", 13, false);
                detailLayout.addView(missing);
            }
        }
        TextView details = label(message.toString(), 14, false);
        details.setPadding(0, 12, 0, 0);
        detailLayout.addView(details);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(point.id)
                .setView(detailLayout)
                .setPositiveButton("Take Photo", (d, which) -> takePhotoForPoint(point))
                .setNeutralButton("Delete Point", (d, which) -> confirmDeletePoint(point))
                .setNegativeButton("Close", null)
                .show();
        addDialogButtonHaptics(dialog);
    }

    private void confirmDeletePoint(PointRecord point) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Delete " + point.id + "?")
                .setMessage("This will mark the point deleted in the app, phone CSV, and STM32 SD CSV, and rename the SD UBX file.")
                .setPositiveButton("Delete", (d, which) -> requestDeletePoint(point))
                .setNegativeButton("Cancel", null)
                .show();
        addDialogButtonHaptics(dialog);
    }

    private void requestDeletePoint(PointRecord point) {
        if (point == null || point.id == null || point.id.isEmpty()) return;
        pendingDeletePointId = point.id;
        deleteAcked = false;
        deleteRetryGeneration += 1;
        appendRaw("#POINT_DELETE_REQUEST," + point.id);
        if (sendDeleteAttempt(deleteRetryGeneration)) {
            if (csvStatus != null) {
                csvStatus.setText("Delete requested for " + point.id);
            }
            scheduleDeleteRetry(deleteRetryGeneration);
            scheduleDeleteTimeout(deleteRetryGeneration);
        } else {
            pendingDeletePointId = "";
            if (csvStatus != null) {
                csvStatus.setText("Delete failed: BLE write not ready");
            }
        }
    }

    private boolean sendDeleteAttempt(int generation) {
        if (pendingDeletePointId == null || pendingDeletePointId.isEmpty()) {
            return false;
        }
        if (generation != deleteRetryGeneration) {
            return false;
        }
        PointRecord point = findPointById(pendingDeletePointId);
        String csvName = point == null ? "" : safeCache(point.csvName);
        String command = csvName.isEmpty()
                ? "#POINT_DELETE," + pendingDeletePointId + "\r\n"
                : "#POINT_DELETE," + pendingDeletePointId + "," + csvName + "\r\n";
        return sendBleAsciiCommand(command,
                "POINT_DELETE");
    }

    private void confirmClearSdCard() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Clear SD Card?")
                .setMessage("Are you sure? All SD card data will be lost! ROD.TXT will be kept.")
                .setPositiveButton("Clear SD Card", (d, which) -> requestClearSdCard())
                .setNegativeButton("Cancel", null)
                .show();
        addDialogButtonHaptics(dialog);
        Button positive = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setTextColor(color("DC2626"));
        }
    }

    private void requestClearSdCard() {
        appendRaw("#SD_CLEAR_REQUEST");
        sdClearGeneration += 1;
        sdClearPending = false;
        sdClearAcked = false;
        showSdClearStatusDialog("Sending SD clear command...\nThis can take several minutes.");
        if (sendSdClearAttempt(sdClearGeneration)) {
            sdClearPending = true;
            updateSdClearStatus("SD clear requested. Waiting for STM32...\nThis can take several minutes.");
            if (clearSdButton != null) {
                clearSdButton.setEnabled(false);
                clearSdButton.setText("Clearing SD...");
            }
            scheduleSdClearRetry(sdClearGeneration);
            scheduleSdClearTimeout(sdClearGeneration);
        } else {
            updateSdClearStatus("SD clear failed: BLE not ready");
            setSdClearDialogDone();
        }
    }

    private boolean sendSdClearAttempt(int generation) {
        if (generation != sdClearGeneration) {
            return false;
        }
        return sendBleAsciiCommand("#SD_CLEAR\r\n", "SD_CLEAR");
    }

    private void scheduleSdClearRetry(int generation) {
        mainHandler.postDelayed(() -> {
            if (sdClearPending
                    && !sdClearAcked
                    && generation == sdClearGeneration) {
                appendRaw("#SD_CLEAR_RETRY");
                sendSdClearAttempt(generation);
                updateSdClearStatus("Retrying SD clear command...\nWaiting for STM32 acknowledgement.");
                scheduleSdClearRetry(generation);
            }
        }, 1500);
    }

    private void scheduleSdClearTimeout(int generation) {
        mainHandler.postDelayed(() -> {
            if (sdClearPending && generation == sdClearGeneration) {
                sdClearPending = false;
                updateSdClearStatus("SD clear timeout waiting for STM32");
                setSdClearDialogDone();
                if (clearSdButton != null) {
                    clearSdButton.setEnabled(true);
                    clearSdButton.setText("Clear SD Card");
                }
            }
        }, 180000);
    }

    private void parseSdClearOk(String line) {
        sdClearPending = false;
        sdClearAcked = true;
        String[] fields = line.split(",");
        String count = fields.length >= 2 ? fields[1].trim() : "?";
        String logName = fields.length >= 3 ? fields[2].trim() : "";
        updateSdClearStatus("SD cleared: " + count + " file(s) deleted"
                + (logName.isEmpty() ? "" : "; new log " + logName));
        setSdClearDialogDone();
        if (clearSdButton != null) {
            clearSdButton.setEnabled(true);
            clearSdButton.setText("Clear SD Card");
        }
        stm32NextPointId = "";
        requestStm32NextPoint();
    }

    private void parseSdClearErr(String line) {
        sdClearPending = false;
        sdClearAcked = true;
        String[] fields = line.split(",");
        String code = fields.length >= 2 ? fields[1].trim() : "UNKNOWN";
        if ("ACTIVE".equals(code)) {
            updateSdClearStatus("SD clear blocked: store/finish active point first");
        } else {
            updateSdClearStatus("SD clear failed: " + code);
        }
        setSdClearDialogDone();
        if (clearSdButton != null) {
            clearSdButton.setEnabled(true);
            clearSdButton.setText("Clear SD Card");
        }
    }

    private void showSdClearStatusDialog(String message) {
        if (sdClearDialog != null && sdClearDialog.isShowing()) {
            updateSdClearStatus(message);
            return;
        }
        sdClearMessage = label(message, 16, false);
        sdClearMessage.setPadding(28, 20, 28, 20);
        sdClearDialog = new AlertDialog.Builder(this)
                .setTitle("Clear SD Card")
                .setView(sdClearMessage)
                .setPositiveButton("Hide", null)
                .setCancelable(true)
                .show();
        addDialogButtonHaptics(sdClearDialog);
    }

    private void updateSdClearStatus(String message) {
        if (csvStatus != null) {
            csvStatus.setText(message);
        }
        if (sdClearMessage != null) {
            sdClearMessage.setText(message);
        }
    }

    private void setSdClearDialogDone() {
        if (sdClearDialog == null) return;
        Button ok = sdClearDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (ok != null) {
            ok.setText("OK");
        }
        mainHandler.postDelayed(() -> {
            if (sdClearDialog != null && sdClearDialog.isShowing()) {
                sdClearDialog.dismiss();
            }
        }, 2500);
    }

    private void scheduleDeleteRetry(int generation) {
        mainHandler.postDelayed(() -> {
            if (!pendingDeletePointId.isEmpty()
                    && !deleteAcked
                    && generation == deleteRetryGeneration) {
                appendRaw("#POINT_DELETE_RETRY," + pendingDeletePointId);
                sendDeleteAttempt(generation);
                scheduleDeleteRetry(generation);
            }
        }, 1200);
    }

    private void scheduleDeleteTimeout(int generation) {
        mainHandler.postDelayed(() -> {
            if (!pendingDeletePointId.isEmpty()
                    && generation == deleteRetryGeneration) {
                appendRaw("#POINT_DELETE_TIMEOUT," + pendingDeletePointId);
                if (csvStatus != null) {
                    csvStatus.setText("Delete timeout waiting for STM32");
                }
                pendingDeletePointId = "";
                deleteAcked = false;
            }
        }, 7000);
    }

    private void takePhotoForPoint(PointRecord point) {
        try {
            photoTargetPoint = point;
            pendingPhotoName = point.id + ".jpg";
            pendingPhotoUri = createProjectPhotoUri(pendingPhotoName);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhotoUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_ATTACH_PHOTO);
        } catch (Exception ex) {
            photoTargetPoint = null;
            pendingPhotoUri = null;
            pendingPhotoName = "";
            if (csvStatus != null) {
                csvStatus.setText("Camera launch error");
            }
            appendRaw("#PHOTO_CAMERA_ERROR," + sanitize(ex.getMessage()));
        }
    }

    private Uri createProjectPhotoUri(String fileName) throws IOException {
        if (projectFolderName == null || projectFolderName.isEmpty()) {
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date());
            projectFolderName = projectName + "_" + stamp;
            saveProjectHistory(projectFolderName);
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOCUMENTS + "/RoverLogger/"
                            + projectFolderName + "/photos");
        }
        Uri outUri = getContentResolver().insert(
                MediaStore.Files.getContentUri("external"), values);
        if (outUri == null) {
            throw new IOException("Could not create camera photo file");
        }
        return outUri;
    }

    private void attachPhotoToPoint(PointRecord point, Uri sourceUri) {
        String mime = getContentResolver().getType(sourceUri);
        String ext = photoExtensionForMime(mime);
        String fileName = point.id + ext;
        try {
            copyPhotoToProject(sourceUri, fileName, mime == null ? "image/jpeg" : mime);
            point.photoName = fileName;
            renderPointList();
            saveProjectCache();
            if (fieldMap != null) {
                fieldMap.invalidate();
            }
            if (csvStatus != null) {
                csvStatus.setText("Photo saved -> Documents/RoverLogger/"
                        + projectFolderName + "/photos/" + fileName);
            }
        } catch (IOException ex) {
            if (csvStatus != null) {
                csvStatus.setText("Photo save error");
            }
            appendRaw("#PHOTO_SAVE_ERROR," + sanitize(ex.getMessage()));
        }
    }

    private void copyPhotoToProject(Uri sourceUri, String fileName, String mime) throws IOException {
        if (projectFolderName == null || projectFolderName.isEmpty()) {
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date());
            projectFolderName = projectName + "_" + stamp;
            saveProjectHistory(projectFolderName);
        }

        try (InputStream input = getContentResolver().openInputStream(sourceUri)) {
            if (input == null) {
                throw new IOException("Could not open selected photo");
            }
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/RoverLogger/"
                                + projectFolderName + "/photos");
                Uri outUri = getContentResolver().insert(
                        MediaStore.Files.getContentUri("external"), values);
                if (outUri == null) {
                    throw new IOException("Could not create photo file");
                }
                try (OutputStream output = getContentResolver().openOutputStream(outUri, "w")) {
                    copyStream(input, output);
                }
            } else {
                File root = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                        "RoverLogger/" + projectFolderName + "/photos");
                if (!root.exists() && !root.mkdirs()) {
                    throw new IOException("Could not create photo folder");
                }
                try (OutputStream output = new java.io.FileOutputStream(new File(root, fileName))) {
                    copyStream(input, output);
                }
            }
        }
    }

    private static void copyStream(InputStream input, OutputStream output) throws IOException {
        if (output == null) {
            throw new IOException("Could not open photo output");
        }
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) > 0) {
            output.write(buffer, 0, count);
        }
    }

    private void promptForProjectChoice() {
        List<String> history = loadProjectHistory();
        if (history.isEmpty()) {
            promptForProjectName();
            return;
        }

        List<String> choices = new ArrayList<>();
        choices.add("New Project");
        choices.addAll(history);
        new AlertDialog.Builder(this)
                .setTitle("PROJECT")
                .setItems(choices.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        promptForProjectName();
                    } else {
                        openExistingProject(choices.get(which));
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void openExistingProject(String folderName) {
        projectFolderName = sanitizeFileName(folderName);
        int split = projectFolderName.indexOf('_');
        projectName = split > 0 ? projectFolderName.substring(0, split) : projectFolderName;
        saveProjectHistory(projectFolderName);
        if (csvStatus != null) {
            csvStatus.setText("Phone CSV: Documents/RoverLogger/"
                    + projectFolderName + "/waiting for SD CSV");
        }
        loadProjectCache();
        maybeShowConnectionPrompt();
    }

    private void promptForProjectName() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        input.setText(projectName);
        input.selectAll();

        new AlertDialog.Builder(this)
                .setTitle("PROJECT NAME?")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    String entered = input.getText() == null ? "" : input.getText().toString();
                    projectName = sanitizeProjectName(entered);
                    String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                            .format(new Date());
                    projectFolderName = projectName + "_" + stamp;
                    saveProjectHistory(projectFolderName);
                    clearProjectPoints();
                    if (csvStatus != null) {
                        csvStatus.setText("Phone CSV: Documents/RoverLogger/"
                                + projectFolderName + "/waiting for SD CSV");
                    }
                    maybeShowConnectionPrompt();
                })
                .show();
    }

    private void maybeShowConnectionPrompt() {
        if (connectionPromptShown || nmeaStreamStarted) {
            return;
        }
        connectionPromptShown = true;
        mainHandler.postDelayed(() -> showConnectionPrompt(true), 250);
    }

    private void showConnectionPrompt(boolean autoStart) {
        connectionPromptContinuingOffline = false;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = 4;
        layout.setPadding(pad, pad, pad, 0);
        connectionPromptMessage = label("Connecting to rover...", 16, false);
        connectionPromptMessage.setPadding(0, 8, 0, 8);
        layout.addView(connectionPromptMessage);
        TextView connectionPromptHint = label("Please ensure rover is powered on and HC-05 is connected.", 13, false);
        connectionPromptHint.setTextColor(color("4B5563"));
        connectionPromptHint.setPadding(0, 0, 0, 8);
        layout.addView(connectionPromptHint);

        connectionDialog = new AlertDialog.Builder(this)
                .setTitle("Rover connection")
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton("Retry search", null)
                .setNegativeButton("Continue without connecting", null)
                .show();
        addDialogButtonHaptics(connectionDialog);

        Button retry = connectionDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        Button offline = connectionDialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE);
        if (retry != null) {
            retry.setOnClickListener(v -> {
                haptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                appendRaw("#CONNECT_PROMPT_RETRY");
                updateConnectionPrompt("Searching for rover HC-05...");
                connectBle();
            });
        }
        if (offline != null) {
            offline.setOnClickListener(v -> {
                haptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
                connectionPromptContinuingOffline = true;
                appendRaw("#CONNECT_PROMPT_OFFLINE");
                closeBluetooth();
                setBluetoothStatus("OFFLINE");
                connectionDialog.dismiss();
                connectionDialog = null;
                connectionPromptMessage = null;
            });
        }

        if (autoStart) {
            appendRaw("#CONNECT_PROMPT_AUTO_START");
            connectBle();
        }
    }

    private void updateConnectionPrompt(String message) {
        mainHandler.post(() -> {
            if (connectionPromptMessage != null) {
                connectionPromptMessage.setText(message);
            }
        });
    }

    private void handleConnectionSuccessful() {
        if (connectionPromptContinuingOffline) {
            return;
        }
        mainHandler.post(() -> {
            if (connectionPromptMessage != null) {
                connectionPromptMessage.setText("Connection successful");
            }
            if (connectionDialog != null && connectionDialog.isShowing()) {
                mainHandler.postDelayed(() -> {
                    if (connectionDialog != null && connectionDialog.isShowing()) {
                        connectionDialog.dismiss();
                    }
                    connectionDialog = null;
                    connectionPromptMessage = null;
                }, 900);
            }
        });
    }

    private List<String> loadProjectHistory() {
        String packed = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(PREF_PROJECT_HISTORY, "");
        List<String> history = new ArrayList<>();
        if (packed == null || packed.isEmpty()) {
            return history;
        }
        String[] entries = packed.split("\\|");
        for (String entry : entries) {
            String clean = sanitizeFileName(entry);
            if (!clean.isEmpty() && !history.contains(clean)) {
                history.add(clean);
            }
        }
        return history;
    }

    private void saveProjectHistory(String folderName) {
        String clean = sanitizeFileName(folderName);
        if (clean.isEmpty()) return;
        List<String> history = loadProjectHistory();
        history.remove(clean);
        history.add(0, clean);
        while (history.size() > 12) {
            history.remove(history.size() - 1);
        }
        StringBuilder packed = new StringBuilder();
        for (String item : history) {
            if (packed.length() > 0) packed.append('|');
            packed.append(item);
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_PROJECT_HISTORY, packed.toString())
                .apply();
    }

    private void clearProjectPoints() {
        points.clear();
        stopAveragingBlips();
        activePointId = null;
        activeReads = 0;
        renderPointState();
        renderPointList();
        if (fieldMap != null) {
            fieldMap.setPoints(points);
        }
    }

    private File projectCacheFile() throws IOException {
        String cleanProject = sanitizeFileName(projectFolderName);
        if (cleanProject.isEmpty()) {
            throw new IOException("No project selected");
        }
        File dir = new File(getFilesDir(), "project_cache");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create project cache");
        }
        return new File(dir, cleanProject + ".tsv");
    }

    private void saveProjectCache() {
        if (projectFolderName == null || projectFolderName.isEmpty()) return;
        try (FileWriter writer = new FileWriter(projectCacheFile(), false)) {
            for (PointRecord p : points) {
                writer.write(safeCache(p.id));
                writer.write('\t');
                writer.write(String.format(Locale.US, "%.12f", p.lat));
                writer.write('\t');
                writer.write(String.format(Locale.US, "%.12f", p.lon));
                writer.write('\t');
                writer.write(String.format(Locale.US, "%.4f", p.height));
                writer.write('\t');
                writer.write(Integer.toString(p.reads));
                writer.write('\t');
                writer.write(String.format(Locale.US, "%.4f", p.hacc));
                writer.write('\t');
                writer.write(String.format(Locale.US, "%.4f", p.vacc));
                writer.write('\t');
                writer.write(Integer.toString(p.carrier));
                writer.write('\t');
                writer.write(safeCache(p.ubx));
                writer.write('\t');
                writer.write(safeCache(p.fix));
                writer.write('\t');
                writer.write(safeCache(p.ggaFixLabel));
                writer.write('\t');
                writer.write(safeCache(p.photoName));
                writer.write('\t');
                writer.write(safeCache(p.photoUri));
                writer.write('\t');
                writer.write(p.deleted ? "1" : "0");
                writer.write('\t');
                writer.write(safeCache(p.csvRow));
                writer.write('\t');
                writer.write(safeCache(p.csvName));
                writer.write('\t');
                writer.write(safeCache(p.sdStatus));
                writer.write('\t');
                writer.write(safeCache(p.pointType));
                writer.write('\n');
            }
        } catch (IOException ex) {
            appendRaw("#PROJECT_CACHE_SAVE_ERROR," + sanitize(ex.getMessage()));
        }
    }

    private void loadProjectCache() {
        clearProjectPoints();
        try {
            File cache = projectCacheFile();
            if (!cache.exists()) {
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new java.io.FileInputStream(cache), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] fields = line.split("\t", -1);
                    if (fields.length < 13) continue;
                    PointRecord p = new PointRecord();
                    p.id = fields[0];
                    p.lat = parseDouble(fields[1], 0);
                    p.lon = parseDouble(fields[2], 0);
                    p.height = parseDouble(fields[3], 0);
                    p.reads = parseInt(fields[4], 0);
                    p.hacc = parseDouble(fields[5], 0);
                    p.vacc = parseDouble(fields[6], 0);
                    p.carrier = parseInt(fields[7], -1);
                    p.ubx = fields[8];
                    p.fix = fields[9];
                    p.ggaFixLabel = fields[10];
                    p.photoName = fields[11];
                    p.photoUri = fields[12];
                    if (fields.length > 13) {
                        p.deleted = "1".equals(fields[13]);
                    }
                    if (fields.length > 14) {
                        p.csvRow = fields[14];
                    }
                    if (fields.length > 15) {
                        p.csvName = fields[15];
                    }
                    if (fields.length > 16) {
                        p.sdStatus = fields[16].isEmpty() ? "not checked" : fields[16];
                    }
                    if (fields.length > 17) {
                        p.pointType = normalizePointType(fields[17]);
                    } else if (p.csvRow != null && !p.csvRow.isEmpty()) {
                        p.pointType = pointTypeFromCsvRow(p.csvRow, p.pointType);
                    }
                    p.csvRow = ensurePointTypeInCsvRow(p.csvRow, p.pointType);
                    points.add(p);
                }
            }
            renderPointList();
            if (fieldMap != null) {
                fieldMap.setPoints(points);
            }
            if (csvStatus != null && !points.isEmpty()) {
                csvStatus.setText("Phone CSV: loaded " + points.size()
                        + " cached point(s) from " + projectFolderName);
            }
            requestStm32NextPoint();
        } catch (IOException ex) {
            appendRaw("#PROJECT_CACHE_LOAD_ERROR," + sanitize(ex.getMessage()));
        }
    }

    private void promptForRodHeight() {
        rodPromptShown = true;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        input.setText(rodHeightMeters);
        input.selectAll();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("ROD HEIGHT?")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("OK", (d, which) -> {
                    String entered = input.getText() == null ? "" : input.getText().toString();
                    rodHeightMeters = normalizeRodHeight(entered);
                    rodHeightAcked = false;
                    rodRetryActive = false;
                    if (rodStatus != null) rodStatus.setText("Rod Height= " + rodHeightMeters + "M pending");
                    startRodHeightRetry();
                })
                .show();
        addDialogButtonHaptics(dialog);
    }

    private void createSessionCsv(String sdCsvName) throws IOException {
        try {
            if (projectFolderName == null || projectFolderName.isEmpty()) {
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                        .format(new Date());
                projectFolderName = projectName + "_" + stamp;
                saveProjectHistory(projectFolderName);
            }
            sessionCsvName = sanitizeFileName(sdCsvName);

            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, sessionCsvName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/RoverLogger/" + projectFolderName);
                sessionCsvUri = getContentResolver().insert(
                        MediaStore.Files.getContentUri("external"), values);
                if (sessionCsvUri == null) {
                    throw new IOException("Could not create MediaStore CSV");
                }
                writeCsvText(STM32_POINT_CSV_HEADER, false);
            } else {
                File root = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                        "RoverLogger/" + projectFolderName);
                if (!root.exists() && !root.mkdirs()) {
                    throw new IOException("Could not create CSV folder");
                }
                sessionCsvFile = new File(root, sessionCsvName);
                try (FileWriter writer = new FileWriter(sessionCsvFile, false)) {
                    writer.write(STM32_POINT_CSV_HEADER);
                }
            }
        } catch (IOException | RuntimeException ex) {
            sessionCsvFile = null;
            sessionCsvUri = null;
            throw ex;
        }
    }

    private void parsePointCsv(String line) {
        String[] fields = line.split(",", 3);
        if (fields.length < 3) return;
        String sdCsvName = fields[1].trim();
        String csvRow = fields[2];
        String pointId = csvPointId(csvRow);
        String phoneCsvRow = ensurePointTypeInCsvRow(csvRow, pointTypeForPointId(pointId));
        if (!blockedDuplicatePointId.isEmpty() && blockedDuplicatePointId.equals(pointId)) {
            appendRaw("#DUPLICATE_POINT_CSV_IGNORED," + pointId);
            blockedDuplicatePointId = "";
            if (csvStatus != null) {
                csvStatus.setText("Duplicate " + pointId
                        + " ignored. Phone CSV was not changed.");
            }
            return;
        }

        try {
            if (sessionCsvFile == null && sessionCsvUri == null) {
                createSessionCsv(sdCsvName);
            }
            writeCsvText(phoneCsvRow.endsWith("\r\n") ? phoneCsvRow : phoneCsvRow + "\r\n", true);
            updatePointFromCsvRow(sdCsvName, phoneCsvRow);
            if (csvStatus != null) {
                csvStatus.setText("Phone CSV: saved -> Documents/RoverLogger/"
                        + projectFolderName + "/" + sessionCsvName);
            }
        } catch (IOException ex) {
            if (csvStatus != null) {
                csvStatus.setText("Phone CSV: write error");
            }
            appendRaw("#CSV_WRITE_ERROR," + sanitize(ex.getMessage()));
        }
    }

    private void parsePointDeleteOk(String line) {
        String[] fields = line.split(",");
        if (fields.length < 2) return;
        String pointId = fields[1].trim();
        String deletedUbx = fields.length > 2 ? fields[2].trim() : "DEL_" + pointId + ".UBX";

        if (!pendingDeletePointId.isEmpty() && !pendingDeletePointId.equals(pointId)) {
            appendRaw("#POINT_DELETE_UNEXPECTED," + pointId);
            return;
        }

        deleteAcked = true;
        appendRaw("#POINT_DELETE_OK_SEEN," + pointId);
        int deletedCount = 0;
        for (PointRecord p : points) {
            if (p.id != null && p.id.equals(pointId)) {
                p.deleted = true;
                p.sdStatus = "deleted";
                p.ubx = deletedUbx;
                p.fix = "DELETED";
                p.ggaFixLabel = "DELETED";
                p.pointType = "DELETED";
                p.csvRow = deletedCsvRow(pointId, deletedUbx);
                deletedCount += 1;
            }
        }
        pendingDeletePointId = "";
        renderPointList();
        saveProjectCache();
        rewritePhoneCsv();
        if (fieldMap != null) {
            fieldMap.setPoints(points);
        }
        if (csvStatus != null) {
            csvStatus.setText("Deleted " + pointId + " across app/phone/STM32"
                    + (deletedCount > 1 ? " (" + deletedCount + " cached copies)" : ""));
        }
        showPointDeletedBanner(pointId);
        playPointStoredSound();
    }

    private void updatePointFromCsvRow(String sdCsvName, String csvRow) {
        String[] cols = csvRow.trim().split(",");
        if (cols.length < 14) return;
        String pointId = cols[0].trim();
        boolean hasPointType = csvRowHasPointType(cols);
        int offset = hasPointType ? 1 : 0;
        for (PointRecord p : points) {
            if (p.id.equals(pointId)) {
                p.csvName = sdCsvName;
                if (hasPointType) {
                    p.pointType = normalizePointType(cols[1].trim());
                }
                p.csvRow = ensurePointTypeInCsvRow(csvRow, p.pointType);
                if (csvRow.contains(",DELETED,") || csvRow.contains(",DELETED,")) {
                    p.deleted = true;
                    p.fix = "DELETED";
                    p.ggaFixLabel = "DELETED";
                    p.pointType = "DELETED";
                    renderPointList();
                    saveProjectCache();
                    if (fieldMap != null) {
                        fieldMap.setPoints(points);
                    }
                    return;
                }
                p.fix = cols[12 + offset].trim();
                p.carrier = parseInt(cols[13 + offset].trim(), p.carrier);
                if (p.carrier == 1) {
                    p.ggaFixLabel = "RTK FLOAT";
                } else if (p.carrier == 2) {
                    p.ggaFixLabel = "RTK FIX";
                } else if (p.ggaFixLabel == null || p.ggaFixLabel.isEmpty()) {
                    p.ggaFixLabel = fixName(p.fix);
                }
                renderPointList();
                saveProjectCache();
                if (fieldMap != null) {
                    fieldMap.invalidate();
                }
                return;
            }
        }
    }

    private void rewritePhoneCsv() {
        if (sessionCsvUri == null && sessionCsvFile == null) return;
        try {
            writeCsvText(STM32_POINT_CSV_HEADER, false);
            for (PointRecord p : points) {
                String row = p.deleted
                        ? deletedCsvRow(p.id, p.ubx)
                        : (p.csvRow == null || p.csvRow.isEmpty()
                                ? buildCsvRowForCache(p)
                                : ensurePointTypeInCsvRow(p.csvRow, p.pointType));
                writeCsvText(row.endsWith("\r\n") ? row : row + "\r\n", true);
            }
        } catch (IOException ex) {
            appendRaw("#CSV_REWRITE_ERROR," + sanitize(ex.getMessage()));
            if (csvStatus != null) {
                csvStatus.setText("Phone CSV rewrite error");
            }
        }
    }

    private PointRecord findPointById(String pointId) {
        if (pointId == null) return null;
        for (PointRecord p : points) {
            if (pointId.equals(p.id)) {
                return p;
            }
        }
        return null;
    }

    private boolean pointIdExists(String pointId) {
        if (pointId == null || pointId.isEmpty()) return false;
        for (PointRecord p : points) {
            if (pointId.equals(p.id)) {
                return true;
            }
        }
        return false;
    }

    private void updatePointNumberStatus() {
        if (csvStatus == null || stm32NextPointId == null || stm32NextPointId.isEmpty()) {
            return;
        }
        if (stm32PointActive) {
            csvStatus.setText("STM32 active point: " + stm32NextPointId);
            return;
        }

        String expected = expectedNextPointId();
        int stm32Number = pointNumber(stm32NextPointId);
        int expectedNumber = pointNumber(expected);
        if (stm32Number > 0 && expectedNumber > 0 && stm32Number < expectedNumber) {
            csvStatus.setText("Warning: STM32 next " + stm32NextPointId
                    + "; project expects " + expected);
        } else if (stm32Number > 0 && expectedNumber > 0 && stm32Number > expectedNumber) {
            csvStatus.setText("STM32 next " + stm32NextPointId
                    + "; project expected " + expected + " or newer");
        }
    }

    private String expectedNextPointId() {
        int max = 0;
        for (PointRecord p : points) {
            max = Math.max(max, pointNumber(p.id));
        }
        if (max >= 999) {
            return "P999";
        }
        return "P" + pad3(max + 1);
    }

    private int pointNumber(String pointId) {
        if (pointId == null || pointId.length() != 4 || pointId.charAt(0) != 'P') {
            return 0;
        }
        return parseInt(pointId.substring(1), 0);
    }

    private String csvPointId(String csvRow) {
        if (csvRow == null) return "";
        String[] cols = csvRow.trim().split(",", 2);
        return cols.length > 0 ? cols[0].trim() : "";
    }

    private static String deleteErrorMessage(String pointId, String code) {
        if ("4".equals(code)) {
            return "SD files not found for " + pointId
                    + ". Phone project still has cached point data.";
        }
        if ("18".equals(code)) {
            return "SD file limit error deleting " + pointId
                    + ". Update rover firmware/FatFS config.";
        }
        if ("9".equals(code)) {
            return "SD card write error deleting " + pointId + ".";
        }
        if ("11".equals(code)) {
            return "SD card access denied deleting " + pointId + ".";
        }
        return "Point delete failed on STM32: " + pointId + " code=" + code;
    }

    private static String deletedCsvRow(String pointId, String deletedUbx) {
        return pointId + ",DELETED," + deletedUbx
                + ",DELETED,DELETED,0,null,null,null,null,null,null,null,DELETED,0,0,null,null\r\n";
    }

    private static String buildCsvRowForCache(PointRecord p) {
        String ubx = p.ubx == null || p.ubx.isEmpty() ? p.id + ".UBX" : p.ubx;
        String pointType = normalizePointType(p.pointType);
        return p.id + "," + pointType + "," + ubx
                + ",UNKNOWN,UNKNOWN," + p.reads + ","
                + String.format(Locale.US, "%.9f", p.lat) + ","
                + String.format(Locale.US, "%.9f", p.lon)
                + ",null,null,null,"
                + String.format(Locale.US, "%.4f", p.height)
                + ",null," + p.fix + "," + p.carrier + ",0,"
                + String.format(Locale.US, "%.3f", p.hacc) + ","
                + String.format(Locale.US, "%.3f", p.vacc) + "\r\n";
    }

    private String pointTypeForPointId(String pointId) {
        PointRecord point = findPointById(pointId);
        return point == null ? currentPointType : point.pointType;
    }

    private static String ensurePointTypeInCsvRow(String csvRow, String pointType) {
        if (csvRow == null || csvRow.isEmpty()) return "";
        String clean = csvRow.endsWith("\r\n") ? csvRow.substring(0, csvRow.length() - 2)
                : csvRow.endsWith("\n") ? csvRow.substring(0, csvRow.length() - 1)
                : csvRow;
        String[] cols = clean.split(",", -1);
        if (cols.length < 2) return csvRow;
        if (csvRowHasPointType(cols)) {
            cols[1] = normalizePointType(cols[1]);
            return joinCsv(cols) + "\r\n";
        }

        String[] upgraded = new String[cols.length + 1];
        upgraded[0] = cols[0];
        upgraded[1] = normalizePointType(pointType);
        System.arraycopy(cols, 1, upgraded, 2, cols.length - 1);
        return joinCsv(upgraded) + "\r\n";
    }

    private static String pointTypeFromCsvRow(String csvRow, String fallback) {
        if (csvRow == null || csvRow.isEmpty()) return normalizePointType(fallback);
        String[] cols = csvRow.trim().split(",", -1);
        if (csvRowHasPointType(cols)) {
            return normalizePointType(cols[1]);
        }
        return normalizePointType(fallback);
    }

    private static boolean csvRowHasPointType(String[] cols) {
        return cols.length > 1 && isPointType(cols[1]);
    }

    private static boolean isPointType(String value) {
        if (value == null) return false;
        String clean = value.trim().toUpperCase(Locale.US);
        return "GCP".equals(clean) || "TOPO".equals(clean) || "DELETED".equals(clean);
    }

    private static String normalizePointType(String value) {
        if (value == null) return "GCP";
        String clean = value.trim().toUpperCase(Locale.US);
        if ("TOPO".equals(clean)) return "TOPO";
        if ("DELETED".equals(clean)) return "DELETED";
        return "GCP";
    }

    private static String joinCsv(String[] cols) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) out.append(',');
            out.append(cols[i]);
        }
        return out.toString();
    }

    private void writeCsvText(String text, boolean append) throws IOException {
        if (sessionCsvUri != null) {
            String mode = append ? "wa" : "w";
            try (OutputStream output = getContentResolver().openOutputStream(sessionCsvUri, mode);
                 OutputStreamWriter writer = new OutputStreamWriter(output)) {
                writer.write(text);
            }
        } else if (sessionCsvFile != null) {
            try (FileWriter writer = new FileWriter(sessionCsvFile, append)) {
                writer.write(text);
            }
        }
    }

    private static String sanitizeProjectName(String value) {
        String clean = sanitizeFileName(value).toUpperCase(Locale.US);
        if (clean.isEmpty()) return "PROJECT";
        return clean;
    }

    private static String sanitizeFileName(String value) {
        if (value == null) return "";
        String clean = value.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        clean = clean.replaceAll("_+", "_");
        while (clean.startsWith("_") || clean.startsWith(".")) {
            clean = clean.substring(1);
        }
        while (clean.endsWith("_") || clean.endsWith(".")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }

    private static String safeCache(String value) {
        if (value == null) return "";
        return value.replace('\t', ' ')
                .replace('\n', ' ')
                .replace('\r', ' ');
    }

    private static String normalizeRodHeight(String value) {
        double height = parseDouble(value, 2.0);
        if (height < 0.0 || height > 10.0) {
            height = 2.0;
        }
        return String.format(Locale.US, "%.3f", height);
    }

    private void appendRaw(String line) {
        rawLog.setText((line + "\n" + rawLog.getText()).substring(0,
                Math.min(5000, line.length() + 1 + rawLog.getText().length())));
    }

    private void appendRawOnMain(String line) {
        mainHandler.post(() -> appendRaw(line));
    }

    private void startScanAnimation() {
        scanAnimationActive = true;
        scanAnimationTick = 0;
        scanAnimationGeneration++;
        postScanAnimationFrame(scanAnimationGeneration);
    }

    private void stopScanAnimation() {
        scanAnimationActive = false;
        scanAnimationGeneration++;
    }

    private void postScanAnimationFrame(int generation) {
        if (!scanAnimationActive || generation != scanAnimationGeneration) return;
        int dots = (scanAnimationTick % 10) + 1;
        StringBuilder line = new StringBuilder("searching");
        for (int i = 0; i < dots; i++) {
            line.append('.');
        }
        appendRaw(line.toString());
        scanAnimationTick++;
        mainHandler.postDelayed(() -> postScanAnimationFrame(generation), 1000);
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    private static String photoExtensionForMime(String mime) {
        if (mime == null) return ".jpg";
        String lower = mime.toLowerCase(Locale.US);
        if (lower.contains("png")) return ".png";
        if (lower.contains("webp")) return ".webp";
        if (lower.contains("heic") || lower.contains("heif")) return ".heic";
        return ".jpg";
    }

    private void closeBluetooth() {
        bluetoothRunning = false;
        stopScanAnimation();
        if (bleScanner != null) {
            try {
                bleScanner.stopScan(bleScanCallback);
            } catch (Exception ignored) {
            }
        }
        if (activeGatt != null) {
            try {
                activeGatt.close();
            } catch (Exception ignored) {
            }
            activeGatt = null;
        }
        if (activeSocket != null) {
            try {
                activeSocket.close();
            } catch (IOException ignored) {
            }
            activeSocket = null;
        }
    }

    private void setBluetoothStatus(String status) {
        mainHandler.post(() -> {
            bluetoothStatus.setText(status);
            if (connectionPromptMessage != null && !connectionPromptContinuingOffline) {
                if ("BLE SCAN".equals(status)) {
                    connectionPromptMessage.setText("Searching for rover HC-05...");
                } else if ("BLE CONNECT".equals(status) || "CONNECTING".equals(status)
                        || "BLE DISCOVER".equals(status)) {
                    connectionPromptMessage.setText("Connecting to rover...");
                } else if ("BLE OK".equals(status) || "CONNECTED".equals(status)) {
                    connectionPromptMessage.setText("Connected. Waiting for GNSS data...");
                } else if ("BLE NOT FOUND".equals(status) || "BLE NO RX".equals(status)
                        || "BT ERROR".equals(status) || "NO UART".equals(status)) {
                    connectionPromptMessage.setText("Could not connect to rover. Check power, pairing, and HC-05 wiring.");
                } else if ("BT OFF".equals(status)) {
                    connectionPromptMessage.setText("Bluetooth is off. Turn Bluetooth on, then tap Retry.");
                } else if ("PERMISSION".equals(status) || "LOCATION?".equals(status)) {
                    connectionPromptMessage.setText("Bluetooth permission is needed. Grant permission, then tap Retry.");
                } else if ("NO BT".equals(status) || "NO SCANNER".equals(status)) {
                    connectionPromptMessage.setText("Bluetooth is not available on this device.");
                }
            }
        });
    }

    private void showBleControls(boolean show) {
        mainHandler.post(() -> {
            int visibility = show ? View.VISIBLE : View.GONE;
            if (connectButton != null) {
                connectButton.setVisibility(visibility);
            }
            if (bleDeviceButtons != null) {
                bleDeviceButtons.setVisibility(visibility);
            }
        });
    }

    private static Double nmeaCoord(String raw, String hemi) {
        if (raw == null || raw.isEmpty() || hemi == null || hemi.isEmpty()) return null;
        int dot = raw.indexOf('.');
        int degreeDigits = dot > 4 ? dot - 2 : 2;
        double degrees = parseDouble(raw.substring(0, degreeDigits), 0);
        double minutes = parseDouble(raw.substring(degreeDigits), 0);
        double value = degrees + minutes / 60.0;
        if ("S".equals(hemi) || "W".equals(hemi)) value *= -1.0;
        return value;
    }

    private static String fixName(String code) {
        if ("0".equals(code)) return "NO FIX";
        if ("1".equals(code)) return "GPS";
        if ("2".equals(code)) return "DGPS";
        if ("4".equals(code)) return "RTK FIX";
        if ("5".equals(code)) return "RTK FLOAT";
        return "FIX " + code;
    }

    private static String pointFixLabel(PointRecord p) {
        if (p == null) return "FIX ?";
        if (p.carrier == 2) return "RTK FIX";
        if (p.carrier == 1) return "RTK FLOAT";

        String gga = p.ggaFixLabel == null ? "" : p.ggaFixLabel.trim().toUpperCase(Locale.US);
        if (!gga.isEmpty() && !"NO DATA".equals(gga)) {
            return gga;
        }

        String fix = p.fix == null ? "" : p.fix.trim().toUpperCase(Locale.US);
        if (fix.isEmpty()) return "FIX ?";
        if (fix.contains("RTK") || fix.contains("GPS") || fix.contains("DGPS")) return fix;
        if ("0".equals(fix)) return "NOFIX";
        if ("1".equals(fix)) return "DR";
        if ("2".equals(fix)) return "2D";
        if ("3".equals(fix)) return "3D";
        if ("4".equals(fix)) return "GNSS+DR";
        if ("5".equals(fix)) return "TIME";
        return "FIX " + fix;
    }

    private static int pointFixColor(PointRecord p) {
        String label = pointFixLabel(p);
        if (label.contains("RTK FIX")) return Color.parseColor("#16A34A");
        if (label.contains("RTK FLOAT")) return Color.parseColor("#65A30D");
        if (label.contains("3D") || label.contains("DGPS") || label.contains("GPS")) {
            return Color.parseColor("#F97316");
        }
        return Color.parseColor("#DC2626");
    }

    private static String makeGga(double lat, double lon) {
        Coord la = toNmea(lat, true);
        Coord lo = toNmea(lon, false);
        return "$GNGGA,182411.00," + la.raw + "," + la.hemi + ","
                + lo.raw + "," + lo.hemi + ",4,18,0.7,102.345,M,-32.100,M,,";
    }

    private static Coord toNmea(double value, boolean lat) {
        String hemi = value < 0 ? (lat ? "S" : "W") : (lat ? "N" : "E");
        double abs = Math.abs(value);
        int deg = (int) Math.floor(abs);
        double min = (abs - deg) * 60.0;
        Coord coord = new Coord();
        coord.raw = String.format(lat ? "%02d%010.7f" : "%03d%010.7f", deg, min);
        coord.hemi = hemi;
        return coord;
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Double parseNullableDouble(String value) {
        try {
            if (value == null || value.isEmpty()) return null;
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String pad3(int value) {
        return String.format("%03d", value);
    }

    private static int color(String hex) {
        return Color.parseColor("#" + hex);
    }

    private TextView addStatus(LinearLayout row, String name, String value) {
        LinearLayout card = (LinearLayout) card();
        TextView caption = label(name, 13, false);
        TextView status = label(value, 18, true);
        card.addView(caption);
        card.addView(status);
        row.addView(card, new LinearLayout.LayoutParams(0, -2, 1));
        return status;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, 12);
        return row;
    }

    private View card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(14, 14, 14, 14);
        card.setBackgroundColor(Color.WHITE);
        return card;
    }

    private TextView label(String text, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(bold ? color("1F2933") : color("65737F"));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setPadding(0, 4, 0, 4);
        return view;
    }

    private TextView chip(String text) {
        TextView view = label(text, 13, true);
        view.setPadding(18, 8, 18, 8);
        view.setBackgroundColor(Color.WHITE);
        return view;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(color("1F2933"));
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(Color.WHITE);
        button.setPadding(8, 4, 8, 4);
        button.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                haptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
            }
            return false;
        });
        return button;
    }

    private Button primaryButton(String text) {
        Button button = button(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setBackgroundColor(color("2563EB"));
        return button;
    }

    private void addDialogButtonHaptics(AlertDialog dialog) {
        addDialogButtonHaptic(dialog, android.content.DialogInterface.BUTTON_POSITIVE);
        addDialogButtonHaptic(dialog, android.content.DialogInterface.BUTTON_NEGATIVE);
        addDialogButtonHaptic(dialog, android.content.DialogInterface.BUTTON_NEUTRAL);
    }

    private void addDialogButtonHaptic(AlertDialog dialog, int whichButton) {
        Button button = dialog.getButton(whichButton);
        if (button == null) return;
        button.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                haptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
            }
            return false;
        });
    }

    private Button floatingPointButton(String text) {
        Button button = button(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setLines(2);
        button.setGravity(Gravity.CENTER);
        setFloatingPointButtonColor(button, "2563EB");
        button.setElevation(12);
        return button;
    }

    private void setFloatingPointButtonColor(Button button, String hexColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color(hexColor));
        button.setBackground(bg);
    }

    private void updateFeedbackToggleButtons() {
        if (soundToggleButton != null) {
            soundToggleButton.setText(soundEnabled ? "Sound ON" : "Sound OFF");
            soundToggleButton.setTextColor(soundEnabled ? color("1F2933") : color("6B7280"));
        }
        if (vibrationToggleButton != null) {
            vibrationToggleButton.setText(vibrationEnabled ? "Vib ON" : "Vib OFF");
            vibrationToggleButton.setTextColor(vibrationEnabled ? color("1F2933") : color("6B7280"));
        }
    }

    private void showPointStoredBanner(String pointId) {
        String label = pointId == null || pointId.isEmpty()
                ? "POINT STORED"
                : "POINT STORED  " + pointId;
        showPointActionBanner(label, "16A34A");
    }

    private void showPointDeletedBanner(String pointId) {
        String label = pointId == null || pointId.isEmpty()
                ? "POINT DELETED"
                : "POINT DELETED  " + pointId;
        showPointActionBanner(label, "DC2626");
    }

    private void showPointActionBanner(String label, String hexColor) {
        if (pointStoredBanner == null) return;
        pointStoredBannerGeneration += 1;
        int generation = pointStoredBannerGeneration;
        pointStoredBanner.animate().cancel();
        pointStoredBanner.setText(label);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color(hexColor));
        bg.setCornerRadius(10);
        pointStoredBanner.setBackground(bg);
        pointStoredBanner.setVisibility(View.VISIBLE);
        pointStoredBanner.setAlpha(0.0f);
        pointStoredBanner.setScaleX(0.96f);
        pointStoredBanner.setScaleY(0.96f);
        pointStoredBanner.animate()
                .alpha(1.0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(140)
                .start();
        mainHandler.postDelayed(() -> {
            if (generation != pointStoredBannerGeneration || pointStoredBanner == null) {
                return;
            }
            pointStoredBanner.animate()
                    .alpha(0.0f)
                    .setDuration(280)
                    .withEndAction(() -> {
                        if (generation == pointStoredBannerGeneration
                                && pointStoredBanner != null) {
                            pointStoredBanner.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }, 1300);
    }

    private void saveFeedbackPrefs() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(PREF_SOUND_ENABLED, soundEnabled)
                .putBoolean(PREF_VIBRATION_ENABLED, vibrationEnabled)
                .apply();
    }

    private void setPointType(String pointType) {
        currentPointType = normalizePointType(pointType);
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(PREF_POINT_TYPE, currentPointType)
                .apply();
        updatePointTypeButtons();
        sendPointTypeCommand();
        if (csvStatus != null) {
            csvStatus.setText("Point Type= " + currentPointType);
        }
    }

    private void updatePointTypeButtons() {
        stylePointTypeButton(gcpTypeButton, "GCP".equals(currentPointType));
        stylePointTypeButton(topoTypeButton, "TOPO".equals(currentPointType));
    }

    private void stylePointTypeButton(Button button, boolean selected) {
        if (button == null) return;
        button.setTextColor(selected ? Color.WHITE : color("1F2933"));
        button.setBackgroundColor(selected ? color("2563EB") : Color.WHITE);
    }

    private void haptic(View view, int feedbackConstant) {
        if (vibrationEnabled && view != null) {
            view.performHapticFeedback(feedbackConstant);
        }
    }

    private void vibratePointStored() {
        if (!vibrationEnabled) return;
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(450,
                    VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(450);
        }
    }

    private void playStartPointSound() {
        playTone(ToneGenerator.TONE_PROP_BEEP, 80);
    }

    private void playStartButtonPressedSound() {
        playTone(ToneGenerator.TONE_PROP_BEEP2, 70);
    }

    private void startAveragingBlips() {
        averagingBlipGeneration += 1;
        averagingSpinnerIndex = 0;
        int generation = averagingBlipGeneration;
        renderPointState();
        playStartPointSound();
        launchObservationRipple();
        pulsePointCommandButton();
        scheduleAveragingSpinner(generation);
        scheduleAveragingBlip(generation);
    }

    private void stopAveragingBlips() {
        averagingBlipGeneration += 1;
        averagingSpinnerIndex = 0;
        if (pointCommandButton != null) {
            pointCommandButton.animate().cancel();
            pointCommandButton.setScaleX(1.0f);
            pointCommandButton.setScaleY(1.0f);
        }
        if (pointState != null) {
            pointState.animate().cancel();
            pointState.setScaleX(1.0f);
            pointState.setScaleY(1.0f);
        }
    }

    private void scheduleAveragingBlip(int generation) {
        mainHandler.postDelayed(() -> {
            if (generation != averagingBlipGeneration || activePointId == null) {
                return;
            }
            playStartPointSound();
            launchObservationRipple();
            pulsePointCommandButton();
            scheduleAveragingBlip(generation);
        }, 3000);
    }

    private void launchObservationRipple() {
        if (fieldMap != null) {
            fieldMap.launchObservationRipple();
        }
    }

    private void scheduleAveragingSpinner(int generation) {
        mainHandler.postDelayed(() -> {
            if (generation != averagingBlipGeneration || activePointId == null) {
                return;
            }
            averagingSpinnerIndex = (averagingSpinnerIndex + 1) % 4;
            renderPointState();
            scheduleAveragingSpinner(generation);
        }, 125);
    }

    private String averagingSpinner() {
        char[] frames = {'|', '/', '-', '\\'};
        return Character.toString(frames[averagingSpinnerIndex % frames.length]);
    }

    private void pulsePointCommandButton() {
        if (pointCommandButton == null) {
            return;
        }
        pointCommandButton.animate().cancel();
        pointCommandButton.setScaleX(1.0f);
        pointCommandButton.setScaleY(1.0f);
        pointCommandButton.animate()
                .scaleX(1.12f)
                .scaleY(1.12f)
                .setDuration(135)
                .withEndAction(() -> {
                    if (pointCommandButton != null) {
                        pointCommandButton.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(190)
                                .start();
                    }
                })
                .start();
        if (pointState != null && activePointId != null) {
            pointState.animate().cancel();
            pointState.setScaleX(1.0f);
            pointState.setScaleY(1.0f);
            pointState.animate()
                    .scaleX(1.08f)
                    .scaleY(1.08f)
                    .setDuration(135)
                    .withEndAction(() -> {
                        if (pointState != null) {
                            pointState.animate()
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .setDuration(190)
                                    .start();
                        }
                    })
                    .start();
        }
    }

    private void playPointStoredSound() {
        playTone(ToneGenerator.TONE_PROP_ACK, 180);
        mainHandler.postDelayed(() -> playTone(ToneGenerator.TONE_PROP_ACK, 180), 230);
    }

    private void playTone(int toneType, int durationMs) {
        if (soundEnabled && toneGenerator != null) {
            toneGenerator.startTone(toneType, durationMs);
        }
    }

    private static class Coord {
        String raw;
        String hemi;
    }

    private static class PointRecord {
        String id;
        double lat;
        double lon;
        double height;
        int reads;
        double hacc;
        double vacc;
        int carrier = -1;
        String ubx = "";
        String pointType = "GCP";
        String fix = "UNKNOWN";
        String ggaFixLabel = "";
        String photoName = "";
        String photoUri = "";
        boolean deleted = false;
        String csvRow = "";
        String csvName = "";
        String sdStatus = "not checked";
        long addedAnimationStartMs = 0;
    }

    private static class BoundedScrollView extends ScrollView {
        private float lastY = 0;
        private final int fixedHeightPx;

        BoundedScrollView(Activity activity, int fixedHeightPx) {
            super(activity);
            this.fixedHeightPx = fixedHeightPx;
            setVerticalScrollBarEnabled(true);
            setOverScrollMode(View.OVER_SCROLL_NEVER);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int exactHeightSpec = MeasureSpec.makeMeasureSpec(fixedHeightPx, MeasureSpec.EXACTLY);
            super.onMeasure(widthMeasureSpec, exactHeightSpec);
            setMeasuredDimension(getMeasuredWidth(), fixedHeightPx);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            handleScrollOwnership(event);
            return super.onInterceptTouchEvent(event);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            handleScrollOwnership(event);
            return super.onTouchEvent(event);
        }

        private void handleScrollOwnership(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastY = event.getY();
                    getParent().requestDisallowInterceptTouchEvent(canScrollVertically(-1)
                            || canScrollVertically(1));
                    break;
                case MotionEvent.ACTION_MOVE:
                    float y = event.getY();
                    float dy = y - lastY;
                    lastY = y;
                    boolean canScrollWithGesture = (dy < 0 && canScrollVertically(1))
                            || (dy > 0 && canScrollVertically(-1));
                    getParent().requestDisallowInterceptTouchEvent(canScrollWithGesture);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    break;
                default:
                    break;
            }
        }
    }

    private static class ZoomWheelView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final FieldMapView map;
        private float lastY = 0;
        private float accumulatedDy = 0;
        private int dragZoomSteps = 0;
        private boolean vibrationEnabled = true;

        ZoomWheelView(Activity activity, FieldMapView map) {
            super(activity);
            this.map = map;
            setBackgroundColor(Color.TRANSPARENT);
        }

        void setVibrationEnabled(boolean enabled) {
            vibrationEnabled = enabled;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float bodyLeft = w * 0.18f;
            float bodyRight = w * 0.82f;
            float bodyWidth = bodyRight - bodyLeft;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(191, 37, 99, 235));
            canvas.drawRoundRect(bodyLeft, 0, bodyRight, h, 8, 8, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setColor(Color.WHITE);
            canvas.drawLine(bodyLeft + bodyWidth * 0.20f, h * 0.50f,
                    bodyRight - bodyWidth * 0.20f, h * 0.50f, paint);
            paint.setStrokeWidth(3);
            for (int i = 1; i <= 5; i++) {
                float y = h * (0.30f + (i * 0.07f));
                float inset = (i == 3) ? 0.24f : 0.32f;
                canvas.drawLine(bodyLeft + bodyWidth * inset, y,
                        bodyRight - bodyWidth * inset, y, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFakeBoldText(true);
            paint.setTextSize(34);
            paint.setColor(Color.WHITE);
            canvas.drawText("+", w / 2f, h * 0.13f + 12, paint);
            canvas.drawText("-", w / 2f, h * 0.90f + 12, paint);
            paint.setFakeBoldText(false);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    lastY = event.getY();
                    accumulatedDy = 0;
                    dragZoomSteps = 0;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float y = event.getY();
                    accumulatedDy += y - lastY;
                    lastY = y;
                    while (accumulatedDy <= -24f) {
                        map.zoomBy(1.12f);
                        performThrottledZoomHaptic();
                        accumulatedDy += 24f;
                    }
                    while (accumulatedDy >= 24f) {
                        map.zoomBy(0.89f);
                        performThrottledZoomHaptic();
                        accumulatedDy -= 24f;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (event.getY() < getHeight() / 2f) {
                        map.zoomBy(1.25f);
                    } else {
                        map.zoomBy(0.80f);
                    }
                    if (vibrationEnabled) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                    getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return true;
            }
        }

        private void performThrottledZoomHaptic() {
            dragZoomSteps += 1;
            if (vibrationEnabled && dragZoomSteps % 3 == 0) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        }
    }

    private static class FieldMapView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path roverTriangle = new Path();
        private final RectF plot = new RectF();
        private final List<PointRecord> points = new ArrayList<>();
        private final List<Long> rippleStarts = new ArrayList<>();
        private long trianglePulseStartMs = 0;
        private boolean hasCurrent = false;
        private double currentLat = 0;
        private double currentLon = 0;
        private double headingRadians = Math.PI / 2.0;
        private float userScale = 1.0f;
        private float panX = 0;
        private float panY = 0;
        private float lastTouchX = 0;
        private float lastTouchY = 0;
        private float downX = 0;
        private float downY = 0;
        private boolean dragging = false;
        private boolean manualView = false;
        private boolean vibrationEnabled = true;
        private Bounds frozenBounds = null;
        private PointTapListener pointTapListener = null;

        FieldMapView(Activity activity) {
            super(activity);
            setPadding(18, 18, 18, 18);
        }

        void zoomBy(float factor) {
            zoomBy(factor, plot.centerX(), plot.centerY());
        }

        void setPointTapListener(PointTapListener listener) {
            pointTapListener = listener;
        }

        void setVibrationEnabled(boolean enabled) {
            vibrationEnabled = enabled;
        }

        void launchObservationRipple() {
            trianglePulseStartMs = System.currentTimeMillis();
            rippleStarts.add(System.currentTimeMillis());
            while (rippleStarts.size() > 4) {
                rippleStarts.remove(0);
            }
            invalidate();
        }

        private void zoomBy(float factor, float focusX, float focusY) {
            if (plot.width() <= 0 || plot.height() <= 0) {
                return;
            }
            enterManualView();
            float oldScale = userScale;
            float nextScale = clamp(userScale * factor, 1.0f, 8.0f);
            float cx = plot.centerX() + panX;
            float cy = plot.centerY() + panY;
            float ratio = nextScale / oldScale;
            panX += (cx - focusX) * (ratio - 1.0f);
            panY += (cy - focusY) * (ratio - 1.0f);
            userScale = nextScale;
            if (userScale <= 1.01f) {
                userScale = 1.0f;
                panX = 0;
                panY = 0;
                manualView = false;
                frozenBounds = null;
            }
            clampPan();
            invalidate();
        }

        void recenter() {
            manualView = false;
            frozenBounds = null;
            userScale = 1.0f;
            panX = 0;
            panY = 0;
            invalidate();
        }

        void setCurrent(double lat, double lon) {
            if (hasCurrent) {
                double cosLat = Math.cos(Math.toRadians(currentLat));
                double dx = metersX(currentLat, currentLon, cosLat, lat, lon);
                double dy = metersY(currentLat, lat);
                if (Math.hypot(dx, dy) > 0.05) {
                    setHeadingRadians(Math.atan2(dy, dx));
                }
            }
            currentLat = lat;
            currentLon = lon;
            hasCurrent = true;
            invalidate();
        }

        void setPoints(List<PointRecord> records) {
            points.clear();
            for (PointRecord record : records) {
                if (!record.deleted) {
                    points.add(record);
                }
            }
            invalidate();
        }

        void setCourseDegrees(double courseDegrees) {
            double mapRadians = Math.toRadians(90.0 - courseDegrees);
            setHeadingRadians(mapRadians);
            invalidate();
        }

        private void setHeadingRadians(double target) {
            double delta = Math.atan2(Math.sin(target - headingRadians),
                    Math.cos(target - headingRadians));
            headingRadians += delta * 0.35;
        }

        private void enterManualView() {
            if (!hasCurrent && points.isEmpty()) {
                return;
            }
            if (!manualView) {
                frozenBounds = computeBounds();
                manualView = true;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    downX = event.getX();
                    downY = event.getY();
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    dragging = true;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging && event.getPointerCount() == 1) {
                        enterManualView();
                        float x = event.getX();
                        float y = event.getY();
                        panX += x - lastTouchX;
                        panY += y - lastTouchY;
                        lastTouchX = x;
                        lastTouchY = y;
                        clampPan();
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    if (event.getPointerCount() <= 2) {
                        lastTouchX = event.getX(0);
                        lastTouchY = event.getY(0);
                        dragging = true;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (Math.hypot(event.getX() - downX, event.getY() - downY) < 18.0) {
                        PointRecord tapped = findTappedPoint(event.getX(), event.getY());
                        if (tapped != null && pointTapListener != null) {
                            if (vibrationEnabled) {
                                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                            }
                            pointTapListener.onPointTapped(tapped);
                        }
                    }
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return true;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            plot.set(18, 18, w - 18, h - 18);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawRect(0, 0, w, h, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setColor(Color.parseColor("#CBD5E1"));
            canvas.drawRoundRect(plot, 8, 8, paint);

            drawGrid(canvas);
            drawNorthArrow(canvas);

            if (!hasCurrent && points.isEmpty()) {
                paint.setStyle(Paint.Style.FILL);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(30);
                paint.setColor(Color.parseColor("#64748B"));
                canvas.drawText("Waiting for rover position", w / 2f, h / 2f, paint);
                return;
            }

            int plotClip = canvas.save();
            canvas.clipRect(plot);
            Bounds bounds = manualView && frozenBounds != null ? frozenBounds : computeBounds();
            List<RectF> labelRects = new ArrayList<>();
            List<RectF> markerRects = new ArrayList<>();
            if (hasCurrent) {
                float roverX = xFor(currentLat, currentLon, bounds);
                float roverY = yFor(currentLat, currentLon, bounds);
                markerRects.add(new RectF(roverX - 28, roverY - 28,
                        roverX + 28, roverY + 28));
            }
            for (PointRecord p : points) {
                if (p.deleted) continue;
                float x = xFor(p.lat, p.lon, bounds);
                float y = yFor(p.lat, p.lon, bounds);
                markerRects.add(new RectF(x - 16, y - 16, x + 16, y + 16));
                float pointScale = pointAddScale(p);
                float pointRadius = 10f * pointScale;
                float photoRadius = 15f * pointScale;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(pointFixColor(p));
                canvas.drawCircle(x, y, pointRadius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3 * pointScale);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(x, y, pointRadius, paint);
                if (p.photoName != null && !p.photoName.isEmpty()) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(3 * pointScale);
                    paint.setColor(Color.parseColor("#2563EB"));
                    canvas.drawCircle(x, y, photoRadius, paint);
                }
                drawPointLabel(canvas, labelRects, markerRects, p, x, y);
            }

            if (hasCurrent) {
                float x = xFor(currentLat, currentLon, bounds);
                float y = yFor(currentLat, currentLon, bounds);
                drawObservationRipples(canvas, x, y);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2);
                paint.setColor(Color.parseColor("#2563EB"));
                canvas.drawCircle(x, y, 22, paint);
                drawRoverTriangle(canvas, x, y);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(20);
            paint.setColor(Color.parseColor("#64748B"));
            String mode = manualView ? " manual" : "";
            canvas.drawText(String.format(Locale.US, "%.1fm span%s", bounds.spanMeters, mode),
                    plot.left + 12, plot.bottom - 14, paint);
            canvas.restoreToCount(plotClip);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setColor(Color.parseColor("#CBD5E1"));
            canvas.drawRoundRect(plot, 8, 8, paint);
        }

        private void drawObservationRipples(Canvas canvas, float x, float y) {
            long now = System.currentTimeMillis();
            boolean active = false;
            for (int i = rippleStarts.size() - 1; i >= 0; i--) {
                long ageMs = now - rippleStarts.get(i);
                if (ageMs < 0 || ageMs > 2400) {
                    rippleStarts.remove(i);
                    continue;
                }
                active = true;
                float progress = ageMs / 2400f;
                float eased = 1.0f - (float) Math.pow(1.0f - progress, 2.0);
                float alphaFade = 1.0f - progress;
                float maxRadius = Math.min(plot.width(), plot.height()) * 0.34f;

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb((int) (26 * alphaFade), 37, 99, 235));
                canvas.drawCircle(x, y, maxRadius * eased, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3);
                for (int ring = 0; ring < 3; ring++) {
                    float ringProgress = Math.max(0f, eased - ring * 0.18f);
                    if (ringProgress <= 0f) continue;
                    int alpha = (int) (70 * alphaFade * (1.0f - ring * 0.22f));
                    paint.setColor(Color.argb(alpha, 37, 99, 235));
                    canvas.drawCircle(x, y, maxRadius * ringProgress, paint);
                }
            }
            if (active) {
                postInvalidateDelayed(33);
            }
        }

        private float pointAddScale(PointRecord point) {
            if (point == null || point.addedAnimationStartMs <= 0) {
                return 1.0f;
            }
            long ageMs = System.currentTimeMillis() - point.addedAnimationStartMs;
            if (ageMs < 0 || ageMs > 2600) {
                point.addedAnimationStartMs = 0;
                return 1.0f;
            }
            float progress = ageMs / 2600f;
            float envelope = 1.0f - progress;
            float wave = (float) Math.sin(progress * Math.PI * 4.0f);
            postInvalidateDelayed(33);
            return 1.0f + 0.32f * envelope * Math.max(0f, wave);
        }

        private void drawGrid(Canvas canvas) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1);
            paint.setColor(Color.parseColor("#E2E8F0"));
            for (int i = 1; i < 4; i++) {
                float x = plot.left + (plot.width() * i / 4f);
                float y = plot.top + (plot.height() * i / 4f);
                canvas.drawLine(x, plot.top, x, plot.bottom, paint);
                canvas.drawLine(plot.left, y, plot.right, y, paint);
            }
        }

        private void drawNorthArrow(Canvas canvas) {
            float cx = plot.left + 34;
            float top = plot.top + 18;
            float bottom = plot.top + 66;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Color.parseColor("#334155"));
            canvas.drawLine(cx, bottom, cx, top + 12, paint);

            Path arrow = new Path();
            arrow.moveTo(cx, top);
            arrow.lineTo(cx - 10, top + 18);
            arrow.lineTo(cx + 10, top + 18);
            arrow.close();
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(arrow, paint);

            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFakeBoldText(true);
            paint.setTextSize(18);
            canvas.drawText("N", cx, bottom + 22, paint);
            paint.setFakeBoldText(false);
        }

        private void drawPointLabel(Canvas canvas, List<RectF> occupied,
                                    List<RectF> markerRects,
                                    PointRecord point, float markerX, float markerY) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(22);
            paint.setColor(Color.parseColor("#334155"));

            String full = point.id + " " + pointFixLabel(point);
            if (tryDrawLabel(canvas, occupied, markerRects, full, markerX, markerY)) {
                return;
            }
            tryDrawLabel(canvas, occupied, markerRects, point.id, markerX, markerY);
        }

        private boolean tryDrawLabel(Canvas canvas, List<RectF> occupied,
                                     List<RectF> markerRects,
                                     String text, float markerX, float markerY) {
            float textWidth = paint.measureText(text);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float textHeight = fm.descent - fm.ascent;
            float[][] offsets = new float[][]{
                    {14, -12},
                    {14, 24},
                    {-textWidth - 14, -12},
                    {-textWidth - 14, 24},
                    {14, -34},
                    {-textWidth - 14, -34},
                    {14, 48},
                    {-textWidth - 14, 48}
            };

            for (float[] offset : offsets) {
                float left = markerX + offset[0];
                float baseline = markerY + offset[1];
                RectF rect = new RectF(left - 4, baseline + fm.ascent - 4,
                        left + textWidth + 4, baseline + fm.descent + 4);
                if (rect.left < plot.left || rect.right > plot.right
                        || rect.top < plot.top || rect.bottom > plot.bottom) {
                    continue;
                }
                if (intersectsAny(rect, occupied) || intersectsAny(rect, markerRects)) {
                    continue;
                }
                canvas.drawText(text, left, baseline, paint);
                occupied.add(rect);
                return true;
            }
            return false;
        }

        private boolean intersectsAny(RectF rect, List<RectF> occupied) {
            for (RectF used : occupied) {
                if (RectF.intersects(rect, used)) {
                    return true;
                }
            }
            return false;
        }

        private PointRecord findTappedPoint(float tapX, float tapY) {
            if (points.isEmpty() || (!hasCurrent && points.isEmpty())) {
                return null;
            }
            if (!plot.contains(tapX, tapY)) {
                return null;
            }
            Bounds bounds = manualView && frozenBounds != null ? frozenBounds : computeBounds();
            PointRecord best = null;
            double bestDistance = 999999.0;
            for (PointRecord p : points) {
                if (p.deleted) continue;
                float x = xFor(p.lat, p.lon, bounds);
                float y = yFor(p.lat, p.lon, bounds);
                if (!plot.contains(x, y)) continue;
                double distance = Math.hypot(tapX - x, tapY - y);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = p;
                }
            }
            return bestDistance <= 38.0 ? best : null;
        }

        private void drawRoverTriangle(Canvas canvas, float x, float y) {
            float scale = trianglePulseScale();
            float nose = 18f * scale;
            float tail = 13f * scale;
            float wing = 12f * scale;
            double angle = headingRadians;
            double left = angle + 2.45;
            double right = angle - 2.45;

            roverTriangle.reset();
            roverTriangle.moveTo((float)(x + Math.cos(angle) * nose),
                    (float)(y - Math.sin(angle) * nose));
            roverTriangle.lineTo((float)(x + Math.cos(left) * tail),
                    (float)(y - Math.sin(left) * wing));
            roverTriangle.lineTo((float)(x + Math.cos(right) * tail),
                    (float)(y - Math.sin(right) * wing));
            roverTriangle.close();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#2563EB"));
            canvas.drawPath(roverTriangle, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3 * scale);
            paint.setColor(Color.WHITE);
            canvas.drawPath(roverTriangle, paint);
        }

        private float trianglePulseScale() {
            if (trianglePulseStartMs <= 0) {
                return 1.0f;
            }
            long ageMs = System.currentTimeMillis() - trianglePulseStartMs;
            if (ageMs < 0 || ageMs > 650) {
                return 1.0f;
            }
            float progress = ageMs / 650f;
            return 1.0f + 0.22f * (float) Math.sin(Math.PI * progress);
        }

        private Bounds computeBounds() {
            double refLat = hasCurrent ? currentLat : points.get(0).lat;
            double refLon = hasCurrent ? currentLon : points.get(0).lon;
            double cosLat = Math.cos(Math.toRadians(refLat));
            double minX = 0;
            double maxX = 0;
            double minY = 0;
            double maxY = 0;

            if (hasCurrent) {
                minX = maxX = 0;
                minY = maxY = 0;
            }

            for (PointRecord p : points) {
                if (p.deleted) continue;
                double x = metersX(refLat, refLon, cosLat, p.lat, p.lon);
                double y = metersY(refLat, p.lat);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }

            double span = Math.max(maxX - minX, maxY - minY);
            span = Math.max(span, 2.0);
            double pad = span * 0.25;

            Bounds b = new Bounds();
            b.refLat = refLat;
            b.refLon = refLon;
            b.cosLat = cosLat;
            b.minX = minX - pad;
            b.maxX = maxX + pad;
            b.minY = minY - pad;
            b.maxY = maxY + pad;
            b.spanMeters = Math.max(b.maxX - b.minX, b.maxY - b.minY);
            return b;
        }

        private float xFor(double lat, double lon, Bounds b) {
            double x = metersX(b.refLat, b.refLon, b.cosLat, lat, lon);
            float base = (float)(plot.left + ((x - b.minX) / (b.maxX - b.minX)) * plot.width());
            return plot.centerX() + ((base - plot.centerX()) * userScale) + panX;
        }

        private float yFor(double lat, double lon, Bounds b) {
            double y = metersY(b.refLat, lat);
            float base = (float)(plot.bottom - ((y - b.minY) / (b.maxY - b.minY)) * plot.height());
            return plot.centerY() + ((base - plot.centerY()) * userScale) + panY;
        }

        private static double metersX(double refLat, double refLon, double cosLat,
                                      double lat, double lon) {
            return (lon - refLon) * 111320.0 * cosLat;
        }

        private static double metersY(double refLat, double lat) {
            return (lat - refLat) * 111320.0;
        }

        private void clampPan() {
            float maxX = plot.width() * Math.max(0.0f, userScale - 1.0f) * 0.5f;
            float maxY = plot.height() * Math.max(0.0f, userScale - 1.0f) * 0.5f;
            panX = clamp(panX, -maxX, maxX);
            panY = clamp(panY, -maxY, maxY);
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private static class Bounds {
            double refLat;
            double refLon;
            double cosLat;
            double minX;
            double maxX;
            double minY;
            double maxY;
            double spanMeters;
        }
    }

    private interface PointTapListener {
        void onPointTapped(PointRecord point);
    }

    private static class BleCandidate {
        BluetoothDevice device;
        String name;
        int rssi;
    }
}

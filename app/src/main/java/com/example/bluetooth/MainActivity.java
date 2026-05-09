package com.example.bluetooth;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    Button btnScan, btnSend;
    ListView listDevices;
    EditText editMessage;
    TextView textReceived;

    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket bluetoothSocket;
    OutputStream outputStream;
    InputStream inputStream;

    ArrayAdapter<String> deviceAdapter;
    ArrayList<String> deviceList = new ArrayList<>();
    ArrayList<BluetoothDevice> devices = new ArrayList<>();

    private final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    Handler handler = new Handler(Looper.getMainLooper());
    boolean isReceiving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnScan = findViewById(R.id.btnScan);
        btnSend = findViewById(R.id.btnSend);
        listDevices = findViewById(R.id.listDevices);
        editMessage = findViewById(R.id.editMessage);
        textReceived = findViewById(R.id.textReceived);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        listDevices.setAdapter(deviceAdapter);

        // 扫描按钮
        btnScan.setOnClickListener(v -> startScan());

        // 点击列表中的设备进行连接
        listDevices.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = devices.get(position);
            connectDevice(device);
        });

        // 发送按钮
        btnSend.setOnClickListener(v -> {
            String msg = editMessage.getText().toString();
            sendData(msg);
        });
    }

    // 开始扫描
    private void startScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            }, 1);
            return;
        }

        deviceList.clear();
        devices.clear();
        deviceAdapter.notifyDataSetChanged();

        Toast.makeText(this, "开始扫描...", Toast.LENGTH_SHORT).show();
        bluetoothAdapter.startDiscovery();
    }

    // 广播接收器：发现新设备时自动添加到列表
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && !devices.contains(device)) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        devices.add(device);
                        String name = device.getName() != null ? device.getName() : "未知设备";
                        deviceList.add(name + "\n" + device.getAddress());
                        deviceAdapter.notifyDataSetChanged();
                    }
                }
            }
        }
    };

    // 连接设备（在子线程里执行）
    private void connectDevice(BluetoothDevice device) {
        Toast.makeText(this, "正在连接...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
                bluetoothAdapter.cancelDiscovery();
                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();
                handler.post(() -> Toast.makeText(this, "连接成功！", Toast.LENGTH_SHORT).show());
                startReceiving();
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(this, "连接失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // 持续接收数据
    private void startReceiving() {
        isReceiving = true;
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (isReceiving) {
                try {
                    int bytes = inputStream.read(buffer);
                    String received = new String(buffer, 0, bytes);
                    handler.post(() -> textReceived.append(received));
                } catch (Exception e) {
                    isReceiving = false;
                }
            }
        }).start();
    }

    // 发送数据
    private void sendData(String msg) {
        if (outputStream == null) {
            Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            outputStream.write(msg.getBytes());
            Toast.makeText(this, "已发送: " + msg, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "发送失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isReceiving = false;
        try {
            if (bluetoothSocket != null) bluetoothSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
package com.example.bluetooth;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;      //代表手机本身的蓝牙硬件，用于开启扫描
import android.bluetooth.BluetoothDevice;       //扫描到某个蓝牙设备，一个设备就是一个BlutoothDevice
import android.bluetooth.BluetoothSocket;       //代表与某个设备建立了连接通道，连上后通过它收发数据
import android.content.BroadcastReceiver;       //用来接收广播信息
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;            //用来过滤广播的信息，指定我们接收哪种广播
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

    /*-----------界面控件-------------*/
    Button btnScan, btnSend;
    ListView listDevices;
    EditText editMessage;
    TextView textReceived;

    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket bluetoothSocket;
    OutputStream outputStream;      // 写数据--发送
    InputStream inputStream;        // 读数据--接收

    ArrayAdapter<String> deviceAdapter;     // 负责把ArrayList里的数据显示到屏幕上的ListView
    ArrayList<String> deviceList = new ArrayList<>();       // ArrayList,可以动态增减的列表。存显示的文字
    ArrayList<BluetoothDevice> devices = new ArrayList<>();     // 存真正的设备对象(用于点击后连接)

    private final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    Handler handler = new Handler(Looper.getMainLooper());      // Android强制规定只有主线程可以修改界面，但是接收蓝牙数据必须在子线程做。Handler相当于传话员，Looper.getMainLooper()意思是“绑定到主线程”
    boolean isReceiving = false;        // 循环停止，子线程结束
    boolean isServer = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 将Java代码里的变量和界面上的控件绑定在一起
        btnScan = findViewById(R.id.btnScan);
        btnSend = findViewById(R.id.btnSend);
        Button btnServer = findViewById(R.id.btnServer);
        btnServer.setOnClickListener(v -> startServer());
        listDevices = findViewById(R.id.listDevices);
        editMessage = findViewById(R.id.editMessage);
        textReceived = findViewById(R.id.textReceived);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);       // 创建一个ArrayAdapter，数据来源是deviceList，显示样式是simple_list_item_1（每行一条文字）
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

    private void startServer() {
        Toast.makeText(this, "等待其他设备连接...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    handler.post(() -> Toast.makeText(this, "没有蓝牙权限", Toast.LENGTH_SHORT).show());
                    return;
                }
                android.bluetooth.BluetoothServerSocket serverSocket =
                        bluetoothAdapter.listenUsingRfcommWithServiceRecord("BLUETOOTH", uuid);
                BluetoothSocket socket = serverSocket.accept(); // 等待连接
                serverSocket.close();
                bluetoothSocket = socket;
                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();
                handler.post(() -> Toast.makeText(this, "连接成功！", Toast.LENGTH_SHORT).show());
                startReceiving();
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(this, "服务端启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
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

    // 持续接收数据（新子线程）
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
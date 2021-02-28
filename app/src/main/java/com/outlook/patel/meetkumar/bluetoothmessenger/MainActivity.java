package com.outlook.patel.meetkumar.bluetoothmessenger;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    Button listenBtn, listDevicesBtn, sendBtn;
    ListView deviceListView;
    TextView statusTextView, msgTextView;
    EditText msgEditText;

    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice[] btDevicesArray;

    SendReceive sendReceive;

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECEIVED = 5;

    int REQUEST_ENABLE_BLUETOOTH = 1;

    private static final String APP_NAME = "BT Messenger";
    private static final UUID uuid = UUID.fromString("e3a91a7b-eaed-4b82-b263-95ccaf59415b");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        findViewByIds();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BLUETOOTH);
        }
        
        implementListeners();
    }

    private void implementListeners() {
        listDevicesBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Set<BluetoothDevice> bluetoothDeviceSet = bluetoothAdapter.getBondedDevices();
                String[] btDeviceNames = new String[bluetoothDeviceSet.size()];
                btDevicesArray = new BluetoothDevice[bluetoothDeviceSet.size()];
                int index = 0;

                if (bluetoothDeviceSet.size() > 0) {
                    for (BluetoothDevice device:bluetoothDeviceSet) {
                        btDevicesArray[index] = device;
                        btDeviceNames[index] = device.getName();
                        index++;
                    }

                    ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.support_simple_spinner_dropdown_item, btDeviceNames);
                    deviceListView.setAdapter(arrayAdapter);
                }
            }
        });

        listenBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Server server = new Server();
                server.start();
            }
        });

        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Client client = new Client(btDevicesArray[position]);
                client.start();

                statusTextView.setText("Connecting");
            }
        });

        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String sendMsg = String.valueOf(msgEditText.getText());
                sendReceive.write(sendMsg.getBytes());

                msgEditText.setText("");
            }
        });
    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case STATE_LISTENING:
                    statusTextView.setText("Listening");
                    break;
                case STATE_CONNECTING:
                    statusTextView.setText("Connecting");
                    break;
                case STATE_CONNECTED:
                    statusTextView.setText("Connected");
                    break;
                case STATE_CONNECTION_FAILED:
                    statusTextView.setText("Connection Failed");
                    break;
                case STATE_MESSAGE_RECEIVED:
                    byte[] readBuf = (byte[]) msg.obj;
                    String receivedMsg = new String(readBuf, 0, msg.arg1);
                    msgTextView.setText(receivedMsg);
                    break;
            }
            return true;
        }
    });

    private void findViewByIds() {
        listenBtn = (Button) findViewById(R.id.listenBtn);
        listDevicesBtn = (Button) findViewById(R.id.listDevicesBtn);
        sendBtn = (Button) findViewById(R.id.sendBtn);

        deviceListView = (ListView) findViewById(R.id.deviceListView);

        statusTextView = (TextView) findViewById(R.id.statusTextView);
        msgTextView = (TextView) findViewById(R.id.msgTextView);

        msgEditText = (EditText) findViewById(R.id.msgEditText);
    }

    private class Server extends Thread {
        private BluetoothServerSocket serverSocket;

        public Server () {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, uuid);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            BluetoothSocket socket = null;
            
            while (socket == null) {
                try {
                    Message msg = Message.obtain();
                    msg.what = STATE_CONNECTING;
                    handler.sendMessage(msg);

                    socket = serverSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    Message msg = Message.obtain();
                    msg.what = STATE_CONNECTION_FAILED;
                    handler.sendMessage(msg);
                }

                if (socket != null) {
                    Message msg = Message.obtain();
                    msg.what = STATE_CONNECTED;
                    handler.sendMessage(msg);

                    sendReceive = new SendReceive(socket);
                    sendReceive.start();

                    break;
                }
            }
        }
    }

    private class Client extends Thread {
        private BluetoothDevice device;
        private BluetoothSocket socket;

        public Client (BluetoothDevice device) {
            this.device = device;

            try {
                socket = this.device.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            try {
                socket.connect();
                Message msg = Message.obtain();
                msg.what = STATE_CONNECTED;
                handler.sendMessage(msg);

                sendReceive = new SendReceive(socket);
                sendReceive.start();
            } catch (IOException e) {
                e.printStackTrace();
                Message msg = Message.obtain();
                msg.what = STATE_CONNECTION_FAILED;
                handler.sendMessage(msg);
            }
        }
    }

    private class SendReceive extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public SendReceive (BluetoothSocket socket) {
            bluetoothSocket = socket;
            InputStream tempIn = null;
            OutputStream tempOut = null;

            try {
                tempIn = bluetoothSocket.getInputStream();
                tempOut = bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            inputStream = tempIn;
            outputStream = tempOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    handler.obtainMessage(STATE_MESSAGE_RECEIVED, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
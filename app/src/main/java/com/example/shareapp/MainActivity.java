package com.example.shareapp;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 123;
    private static final int CHOOSE_FILE_RESULT_CODE = 54;
    private static final int TIME_OUT = 500;
    private TextView setConnectionStatus;
    private TextView wifiStatus;
    private Button switchWifi;
    private Button discoverNetwork;
    private Button shareFile;
    private ListView displayNetworkList;
    private final int PORT_NUMBER = 8888;
    public static final String TAG = "MainActivity";

    private Uri resultUri;


    private WifiManager wifiManager;
    WifiP2pManager manager;
    WifiP2pManager.Channel channel;
    BroadcastReceiver receiver;

    IntentFilter intentFilter;

    private List<WifiP2pDevice> peers = new ArrayList<>();
    private String[] deviceNameList;
    private WifiP2pDevice[] getDeviceList;

    private InetAddress groupOwnerAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        classInitialization();
        checkWifiConnectivity();
        discoverAvailableNetwork();
        handlingPeersListClick();
    }


    //This method establishes connection between thw selected network
    private void handlingPeersListClick() {
        displayNetworkList.setOnItemClickListener((parent, view, position, id) -> {
            final WifiP2pDevice wifiP2pDevice;
            wifiP2pDevice = getDeviceList[position];

            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = wifiP2pDevice.deviceAddress;

            //permission check
            checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, REQUEST_CODE);

            manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    wifiStatus.setText("Connecting to " + wifiP2pDevice.deviceName);

                }

                @Override
                public void onFailure(int reason) {

                    wifiStatus.setText("Attempted connection to " + wifiP2pDevice.deviceName + " failed because " + reason);

                }
            });
        });
    }

    private void discoverAvailableNetwork() {
        discoverNetwork.setOnClickListener(v -> {
            checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, REQUEST_CODE);


            manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    setConnectionStatus.setText(R.string.discovered_state);
                }

                @Override
                public void onFailure(int reason) {
                    setConnectionStatus.setText(R.string.connection_fail);
                }
            });

        });

    }

    //This is where all the required classes were initialize
    private void classInitialization() {

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);

        setConnectionStatus = findViewById(R.id.set_connectionStatus);
        switchWifi = findViewById(R.id.btn_switch);
        discoverNetwork = findViewById(R.id.btn_discover);
        displayNetworkList = findViewById(R.id.listView);
        wifiStatus = findViewById(R.id.wifiStatus);
        shareFile = findViewById(R.id.btn_shareFie);

        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

    }


    private void checkWifiConnectivity() {
        switchWifi.setOnClickListener(v -> {
            if (wifiManager.isWifiEnabled()) {
                Toast.makeText(getApplicationContext(), "Wifi is On", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getApplicationContext(), "Wifi is OFF", Toast.LENGTH_LONG).show();
            }
        });
    }

    public void setWifiStatus(String displayMessage) {
        wifiStatus.setText(displayMessage);
    }


    //This implementation gets the available peer/device and display in the list provided
    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peersList) {

            if (!peersList.getDeviceList().equals(peers)) {
                peers.clear();
                peers.addAll(peersList.getDeviceList());

                deviceNameList = new String[peersList.getDeviceList().size()];
                getDeviceList = new WifiP2pDevice[peersList.getDeviceList().size()];
                int index = 0;

                for (WifiP2pDevice device : peers) {

                    deviceNameList[index] = device.deviceName;
                    getDeviceList[index] = device;
                    index++;
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(),
                        android.R.layout.simple_list_item_1,
                        deviceNameList);

                displayNetworkList.setAdapter(adapter);
            }
            if (peers.size() == 0) {
                Toast.makeText(getApplicationContext(),
                        "No device found", Toast.LENGTH_SHORT).show();
                return;
            }

        }
    };


    //This implementation establishes connection between the client and the host
    WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {

             groupOwnerAddress = info.groupOwnerAddress;

            //If the share file button shows up in both client and host page,
            //I will have to move it inside the if statement so that either client or host could share


            if (info.groupFormed && info.isGroupOwner) {
                setConnectionStatus.setText("Connected as Host");
                ServerClass serverClass = new ServerClass();
                serverClass.start();
            } else if (info.groupFormed) {
                setConnectionStatus.setText("Connected as Client");
                shareFile.setVisibility(View.VISIBLE);
                if (shareFile.getVisibility() == View.VISIBLE) {
                    shareFile.setOnClickListener(v -> {
                        getContent.launch("*/*");

                    });
                }

            }

        }
    };

    ActivityResultLauncher<String> getContent = registerForActivityResult(new ActivityResultContracts.GetContent(),
            new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri result) {

                    resultUri = result;
                    ClientClass clientClass = new ClientClass(groupOwnerAddress);
                    clientClass.start();
                    TextView showUri = findViewById(R.id.filePath);
                    String filePath = resultUri.getPath();
                    String fileLastPath = resultUri.getLastPathSegment();
                    Log.d(TAG,"Display File Path => " + filePath);
                    Log.d(TAG,"Display File Last Path Segment => " + fileLastPath);
                    showUri.setText(resultUri.toString());

                }
            });


    //This Method is setup to cater for permission request
    private void checkPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
        } else {
            return;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_CODE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    discoverAvailableNetwork();
                    // Permission is granted. Continue the action or workflow
                    // in your app.
                } else {
                    Toast.makeText(this, "Allow permission for transfer purposes", Toast.LENGTH_LONG).show();
                    return;
                }
                // Other 'case' lines to check for other
                // permissions this app might request.
        }


    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    public class ServerClass extends Thread {
        Socket socket;
        ServerSocket serverSocket;

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(PORT_NUMBER);
                socket = serverSocket.accept();


                // This is to create a directory in the server phone(Host) to save sent file
                Log.d(TAG, "Server: connection done");
                final File f = new File(getApplicationContext().getExternalFilesDir("received"),
                        "wifip2pshared-" + System.currentTimeMillis()
                                + "file");
                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();
                Log.d(TAG, "server: copying files " + f.toString());
                InputStream inputstream = socket.getInputStream();
                copyFile(inputstream, new FileOutputStream(f));
                serverSocket.close();
//                return f.getAbsolutePath();

            } catch (IOException e) {
                e.printStackTrace();
                // return null;
            }
        }
    }

    public class ClientClass extends Thread {
        Socket socket;
        String hostAdd;

        public ClientClass(InetAddress hostAddress) {
            socket = new Socket();
            hostAdd = hostAddress.getHostAddress();
        }

        @Override
        public void run() {
            try {
                socket.connect(new InetSocketAddress(hostAdd, PORT_NUMBER), TIME_OUT);

                OutputStream outputStream = socket.getOutputStream();
                ContentResolver cr = getApplicationContext().getContentResolver();

                InputStream is = null;
                try {
                    is = cr.openInputStream(Uri.parse(resultUri.toString()));
                } catch (FileNotFoundException e) {
                    Log.d(TAG, e.toString());
                }

                copyFile(is, outputStream);
                Log.d(TAG, "Client: Data written");

            } catch (IOException e) {
                e.printStackTrace();
            }

            finally {
                if (socket != null) {
                    if (socket.isConnected()) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            // Give up
                            e.printStackTrace();
                        }
                    }
                }
            }

        }

    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d(TAG, e.toString());
            return false;
        }
        return true;
    }
}
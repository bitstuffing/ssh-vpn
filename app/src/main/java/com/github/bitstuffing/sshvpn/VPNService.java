package com.github.bitstuffing.sshvpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.DynamicPortForwarder;
import com.trilead.ssh2.Session;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class VPNService extends VpnService  {

    private static final String TAG = VPNService.class.getSimpleName();
    private static final String VPNSERVICE = "MyVPNService";
    private static final String VPNRUNNABLE = "MyVpnRunnable";

    private static final String LOCALHOST = "127.0.0.1";
    private static final String DNSSERVER = "1.1.1.1";

    //TODO get from settings page (configuration)
    private static final String USERNAME = "USERNAME";
    private static final String PASSWORD = "PASSWORD";
    private static final String HOST = "HOST_IP";
    private static final int PORT = 22;
    private static final int DYNAMIC_PORT = 9987;

    private Thread mThread;
    private ParcelFileDescriptor mInterface;
    private Builder builder;
    private Connection connection;

    public VPNService() { }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "on create...");
        this.builder = new Builder();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { //needed in the newer versions
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "messages")
                    .setContentText("Executing in background")
                    .setContentTitle("Service backend")
                    .setSmallIcon(R.drawable.ic_launcher_background);
            startModernForeground();
        }else{ //old way
            startForeground(101, new Notification());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startModernForeground(){
        String NOTIFICATION_CHANNEL_ID = "com.github.bitstuffing.sshvpn";
        String channelName = "VPNService Background Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                //.setSmallIcon(R.drawable.)
                .setContentTitle("App is running in background")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(2, notification);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind...");
        String action = intent.getAction();
        if (action != null && action.equals(SERVICE_INTERFACE)) {
            return super.onBind(intent);
        }
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "on startCommand...");
        this.mThread = new Thread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void run() {
                try {

                    connection = new Connection(HOST, PORT);
                    connection.connect();
                    boolean connected = connection.authenticateWithPassword(USERNAME,PASSWORD);
                    if(!connected){
                        Log.d(TAG,"NOT connected to "+HOST);
                        mThread.stop(); //TODO change that
                    }
                    Log.d(TAG,"Connected to "+HOST);

                    Log.d(TAG,"trying dynamic port forwading...");
                    //connection.createDynamicPortForwarder(new InetSocketAddress("127.0.0.1", 9987));
                    DynamicPortForwarder dpf = connection.createDynamicPortForwarder(DYNAMIC_PORT);
                    Session session = connection.openSession();
                    //session.execCommand("python3 -m proxy &"); //just remote notes, ignore this part

                    Log.d(TAG,"opening vpn...");
                    //a. Configure the TUN and get the interface.
                    while(mInterface==null){
                        Thread.sleep(1000);
                        Log.d(TAG,"TUN is null, so needs more...");
                        builder.setSession(VPNSERVICE)
                                //.addDnsServer(DNSSERVER)
                                .addAddress("192.168.43.2",24)
                                .addRoute("0.0.0.0", 0);
                        mInterface = builder.establish();
                    }
                    Log.d(TAG,"TUN is not null, continue...");
                    //b. Packets to be sent are queued in this input stream.
                    FileInputStream in = new FileInputStream(mInterface.getFileDescriptor());
                    //b. Packets received need to be written to this output stream.
                    FileOutputStream out = new FileOutputStream(mInterface.getFileDescriptor());
                    //c. The UDP channel can be used to pass/get ip package to/from server
                    DatagramChannel tunnel = DatagramChannel.open();
                    // Connect to the server, localhost is used for demonstration only.

                    tunnel.connect(new InetSocketAddress("127.0.0.1", DYNAMIC_PORT));

                    //d. Protect this socket, so package send by it will not be feedback to the vpn service.
                    protect(tunnel.socket());
                    //e. Use a loop to pass packets.
                    byte[] body = new byte[2048];
                    try {
                        while (true) {
                            //TODO here is the main logic
                            int n = in.read(body, 3, 2045);

                            Log.d(TAG, "write to stdin " + n);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.i(TAG, "IO exception :'( :" + e.toString());
                    }

                } catch (Exception e) {
                    // Catch any exception
                    e.printStackTrace();
                } finally {
                    try {
                        if (mInterface != null) {
                            mInterface.close();
                            mInterface = null;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

        }, VPNRUNNABLE);
        this.mThread.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        if (this.mThread != null) {
            this.mThread.interrupt();
        }
        super.onDestroy();
    }
}
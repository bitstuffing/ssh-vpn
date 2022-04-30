package com.github.bitstuffing.sshvpn.sshvpn;


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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.forward.DynamicPortForwardingTracker;
import org.apache.sshd.common.util.net.SshdSocketAddress;

public class VPNService extends VpnService {

    private static final String TAG = VPNService.class.getSimpleName();
    private static final String VPNSERVICE = "MyVPNService";
    private static final String VPNRUNNABLE = "MyVpnRunnable";

    private static final String LOCALHOST = "127.0.0.1";
    private static final String DNSSERVER = "1.1.1.1";

    //TODO get from settings flutter page (configuration)
    private static final String USERNAME = "USERNAME";
    private static final String PASSWORD = "PASSWORD";
    private static final String HOST = "HOST";
    private static final int PORT = 0;

    private Thread mThread;
    private ParcelFileDescriptor mInterface;
    private Builder builder;

    public VPNService() { }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "on create...");
        this.builder = new Builder();

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { //needed in the newer versions
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "messages")
                    .setContentText("Executing in background")
                    .setContentTitle("Flutter backend")
                    .setSmallIcon(R.drawable.launch_background);
            startModernForeground();
        }else{ //old way
            startForeground(101, new Notification());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startModernForeground(){
        String NOTIFICATION_CHANNEL_ID = "com.github.bitstuffing.sshvpn.sshvpn";
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
                    //a. Configure the TUN and get the interface.
                    while(mInterface==null){
                        Thread.sleep(1000);
                        Log.d(TAG,"TUN is null, so needs more...");
                        builder.setSession(VPNSERVICE)
                                .addDnsServer(DNSSERVER)
                                .addRoute("0.0.0.0", 0)
                                .addDisallowedApplication(getPackageName());
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

                    SshClient client = SshClient.setUpDefaultClient();
                    ClientSession session = null;
                    DynamicPortForwardingTracker tracker = null;

                    try{
                        client.start();
                        ConnectFuture cf = client.connect(USERNAME, HOST, PORT);
                        session = cf.verify().getSession();
                        session.addPasswordIdentity(PASSWORD);
                        session.auth().verify(TimeUnit.SECONDS.toMillis(10));

                        tracker = session.createDynamicPortForwardingTracker(new SshdSocketAddress(LOCALHOST, 0));
                        SshdSocketAddress boundaryAddress = tracker.getBoundAddress();
                        tunnel.connect(new InetSocketAddress(LOCALHOST, boundaryAddress.getPort()));


                    }catch(Exception e){
                        e.printStackTrace();
                    }

                    //tunnel.connect(new InetSocketAddress("127.0.0.1", 9987));

                    //d. Protect this socket, so package send by it will not be feedback to the vpn service.
                    protect(tunnel.socket());
                    //e. Use a loop to pass packets.
                    while (true) {
                        //TODO data traffic counters here:
                        //get packet with in
                        //put packet to tunnel
                        //get packet form tunnel
                        //return packet with out
                        //sleep is a must
                        Thread.sleep(100);
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
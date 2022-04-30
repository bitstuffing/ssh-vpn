package com.github.bitstuffing.sshvpn.sshvpn;


import android.content.Intent;
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

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "messages")
                    .setContentText("Executing in background")
                    .setContentTitle("Flutter backend")
                    .setSmallIcon(R.drawable.launch_background);

            startForeground(101, builder.build());
        }
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
                        Log.i(TAG,"TUN is null, so needs more...");
                        builder.setSession(VPNSERVICE)
                                .addAddress("192.168.43.1", 24) //TODO check this one
                                .addDnsServer(DNSSERVER)
                                .addRoute("0.0.0.0", 0)
                                .setBlocking(true)
                                .setMtu(1280)
                                .addDisallowedApplication(getPackageName());
                        mInterface = builder.establish();
                    }
                    Log.i(TAG,"TUN is not null, continue...");
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
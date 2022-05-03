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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;

public class VPNService extends VpnService  {

    private static final String TAG = VPNService.class.getSimpleName();
    private static final String VPNSERVICE = "MyVPNService";
    private static final String VPNRUNNABLE = "MyVpnRunnable";

    private static final String LOCALHOST = "127.0.0.1";
    private static final String DNSSERVER = "1.1.1.1";

    private static final int IP_PACKET_MAX_LENGTH = 65535;

    private byte []mResponseBuffer = new byte[IP_PACKET_MAX_LENGTH];

    //TODO get from settings page (configuration)
    private static final String USERNAME = "USERNAME";
    private static final String PASSWORD = "PASSWORD";
    private static final String HOST = "IP";
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

                    //START - SSH PART

                    connection = new Connection(HOST, PORT);
                    connection.connect();
                    boolean connected = connection.authenticateWithPassword(USERNAME,PASSWORD);
                    if(!connected){
                        Log.d(TAG,"NOT connected to "+HOST);
                        mThread.stop(); //TODO change that
                    }
                    Log.d(TAG,"Connected to "+HOST);

                    //Session session = connection.openSession(); //for remote commands only
                    //session.execCommand("python3 -m proxy &"); //just remote notes, ignore this part

                    Log.d(TAG,"trying dynamic port forwarding (SSH)...");
                    //connection.createDynamicPortForwarder(new InetSocketAddress("127.0.0.1", 9987));
                    DynamicPortForwarder dpf = connection.createDynamicPortForwarder(DYNAMIC_PORT);


                    //"END" - SSH PART

                    Log.d(TAG,"opening vpn...");

                    //c. The UDP channel can be used to pass/get ip package to/from server
                    DatagramChannel tunnel = DatagramChannel.open();

                    //d. Protect this socket, so package send by it will not be feedback to the vpn service.
                    protect(tunnel.socket());

                    // Connect to the server, localhost is used for demonstration only.
                    tunnel.connect(new InetSocketAddress("127.0.0.1", DYNAMIC_PORT));
                    tunnel.configureBlocking(false);

                    //HANDSHAKE
                    //a. Configure the TUN and get the interface.
                    while(mInterface==null){
                        Thread.sleep(1000);
                        Log.d(TAG,"TUN is null, so needs more...");
                        builder.setSession(VPNSERVICE)
                                .addDnsServer(DNSSERVER)
                                .addAddress("192.168.43.2",24)
                                .addRoute("0.0.0.0", 0);
                        mInterface = builder.establish();
                    }

                    Log.d(TAG,"TUN is not null, handshaked! so continue...");

                    // Packets to be sent are queued in this input stream.
                    FileInputStream in = new FileInputStream(mInterface.getFileDescriptor());
                    // Packets received need to be written to this output stream.
                    FileOutputStream out = new FileOutputStream(mInterface.getFileDescriptor());

                    // Use a loop to pass packets.
                    //START LOOP

                    ByteBuffer byteBuffer = ByteBuffer.allocate(IP_PACKET_MAX_LENGTH);
                    while (true) {
                        int read = in.read(byteBuffer.array());
                        if (read > 0) {
                            IPPacket.PACKET.setPacket(byteBuffer.array());
                            int protocol = IPPacket.PACKET.getProtocol();
                            switch (protocol) {
                                case IPPacket.TRANSPORT_PROTOCOL_TCP:
                                    // TODO implement at right way

                                    byteBuffer.limit(read);

                                    debugPacket(byteBuffer);

                                    tunnel.write(byteBuffer);
                                    byteBuffer.clear();

                                    break;
                                case IPPacket.TRANSPORT_PROTOCOL_UDP:
                                    DatagramSocket datagramSocket = new DatagramSocket();
                                    if (protect(datagramSocket)) {
                                        // Handle an IOException if anything goes wrong with a data transfer
                                        // done by the protected socket.
                                        try {
                                            DatagramPacket request = new DatagramPacket(IPPacket.PACKET.getPayload(),
                                                    IPPacket.PACKET.getPayload().length,
                                                    Inet4Address.getByAddress(IPPacket.PACKET.getDstIpAddress()),
                                                    IPPacket.PACKET.getDstPort());
                                            datagramSocket.send(request);

                                            DatagramPacket responsePacket = new DatagramPacket(mResponseBuffer, mResponseBuffer.length);
                                            datagramSocket.receive(responsePacket);
                                            IPPacket.PACKET.swapIpAddresses();
                                            byte []responseData = Arrays.copyOfRange(mResponseBuffer, 0, responsePacket.getLength());

                                            int ipHeader = IPPacket.PACKET.getIpHeaderLength();
                                            int transportHeader = IPPacket.PACKET.getTransportLayerHeaderLength();
                                            int headerLengths = ipHeader + transportHeader;
                                            IPPacket.PACKET.setTotalLength(headerLengths + responseData.length);
                                            IPPacket.PACKET.setPayload(headerLengths, responseData);
                                            /*
                                             * Before computing the checksum of the IP header:
                                             *
                                             * 1. Swap IP addresses.
                                             * 2. Calculate the total length.
                                             * 3. Identification (later)
                                             */
                                            IPPacket.PACKET.calculateIpHeaderCheckSum();

                                            IPPacket.PACKET.swapPortNumbers();
                                            IPPacket.PACKET.setUdpHeaderAndDataLength(transportHeader + responseData.length);
                                            /*
                                             * Before computing the checksum of the UDP header and data:
                                             * 1. Swap the port numbers.
                                             * 2. Set the response data (setPayload).
                                             * 3. Set the UDP header and data length.
                                             */
                                            IPPacket.PACKET.updateUdpCheckSum();

                                            out.write(IPPacket.PACKET.getPacket(), 0, IPPacket.PACKET.getTotalLength());
                                        } catch (IOException e) {
                                            Log.e(TAG, "", e);
                                        } finally {
                                            datagramSocket.close();
                                        }
                                    } else {
                                        throw new IllegalStateException("Failed to create a protected UDP socket");
                                    }
                                    break;
                            }
                        }
                    }

                    //END LOOP

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

    private void debugPacket(ByteBuffer packet) {
        /*
        for(int i = 0; i < length; ++i)
        {
            byte buffer = packet.get();

            Log.d(TAG, "byte:"+buffer);
        }*/


        int buffer = packet.get();
        int version;
        int headerlength;
        version = buffer >> 4;
        headerlength = buffer & 0x0F;
        headerlength *= 4;
        Log.d(TAG, "IP Version:" + version);
        Log.d(TAG, "Header Length:" + headerlength);

        String status = "";
        status += "Header Length:" + headerlength;

        buffer = packet.get();      //DSCP + EN
        buffer = packet.getChar();  //Total Length

        Log.d(TAG, "Total Length:" + buffer);

        buffer = packet.getChar();  //Identification
        buffer = packet.getChar();  //Flags + Fragment Offset
        buffer = packet.get();      //Time to Live
        buffer = packet.get();      //Protocol

        Log.d(TAG, "Protocol:" + buffer);

        status += "  Protocol:" + buffer;

        buffer = packet.getChar();  //Header checksum

        String sourceIP = "";
        buffer = packet.get();  //Source IP 1st Octet
        sourceIP += buffer;
        sourceIP += ".";

        buffer = packet.get();  //Source IP 2nd Octet
        sourceIP += buffer;
        sourceIP += ".";

        buffer = packet.get();  //Source IP 3rd Octet
        sourceIP += buffer;
        sourceIP += ".";

        buffer = packet.get();  //Source IP 4th Octet
        sourceIP += buffer;

        Log.d(TAG, "Source IP:" + sourceIP);

        status += "   Source IP:" + sourceIP;
    }
}
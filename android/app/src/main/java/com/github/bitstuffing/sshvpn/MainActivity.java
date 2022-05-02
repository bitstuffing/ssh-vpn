package com.github.bitstuffing.sshvpn;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.bitstuffing.sshvpn.service.VPNService;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugins.GeneratedPluginRegistrant;

public class MainActivity extends FlutterActivity {

    private Intent serviceIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GeneratedPluginRegistrant.registerWith(new FlutterEngine(this));

        this.serviceIntent = new Intent(MainActivity.this, VPNService.class);

        new MethodChannel(getFlutterEngine().getDartExecutor().getBinaryMessenger(), "com.github.bitstuffing.sshvpn")
                .setMethodCallHandler(new MethodChannel.MethodCallHandler() {
                    @Override
                    public void onMethodCall(@NonNull MethodCall methodCall, @NonNull MethodChannel.Result result) {
                        if(methodCall.method.equals("startService")) {
                            startService();
                            result.success("VPNService has started");
                        }else{
                            result.success("Other methodCall: '"+methodCall.method+"'");
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(this.serviceIntent);
    }

    private void startService() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(this.serviceIntent);
        } else {
            startService(this.serviceIntent);
        }
    }


}
package com.github.bitstuffing.sshvpn;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Bundle;

import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.google.android.material.snackbar.Snackbar;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.github.bitstuffing.sshvpn.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends AppCompatActivity {

    private static final int VPN_PERMISSION_CODE = 100;
    private static final String TAG = "MAIN";

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    private Intent serviceIntent;

    private ActivityResultLauncher<Intent> activityResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Log.i(TAG,"Allowed by user");
                        }else/* if(result.getResultCode()!=VPN_PERMISSION_CODE)*/{
                            Log.e(TAG,"not allowed by user, try again :'(");
                            Snackbar.make(binding.getRoot(), "You need to accept this permission, without it you're not able to run this app", Snackbar.LENGTH_LONG).setAction("Grant Permissions", null).show();
                        }
                    }
                });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent prepare = VpnService.prepare(this);
        if (prepare != null){
            //startActivityForResult(prepare, VPN_PERMISSION_CODE); //deprecated, use next line
            this.activityResultLauncher.launch(prepare);
        }


        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                //requestPower();
                buildServiceInstance();
                startService();
            }
        });
    }

    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.
                    startService();
                } else {
                    Snackbar.make(binding.getRoot(), "You need to accept this permission, without it you're not able to run this app", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                }
            });

    public void requestPower() {
        if (checkSelfPermission(Manifest.permission.BIND_VPN_SERVICE)!= PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.BIND_VPN_SERVICE)) {
                Log.d(TAG,"shouldShowRequestPermissionRationale passed");
            } else {
                requestPermissions(new String[]{Manifest.permission.BIND_VPN_SERVICE,}, 1);
            }
        }
    }


    private void buildServiceInstance() {
        grantPermission();
        this.serviceIntent = new Intent(getApplicationContext(), VPNService.class);
    }

    private void grantPermission() {
        this.serviceIntent = VpnService.prepare(getApplicationContext());
        this.requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        checkPermission(Manifest.permission.INTERNET, VPN_PERMISSION_CODE);
        Log.d(TAG,"Internet permission checked!");
    }

    private void startService() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(this.serviceIntent);
            Snackbar.make(binding.getRoot(), "Started foreground service", Snackbar.LENGTH_LONG).setAction("Action", null).show();
        } else {
            startService(this.serviceIntent);
        }
    }

    /*
     * Function to check and request permission.
     */
    public void checkPermission(String permission, int requestCode){
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED) {
            // Requesting the permission
            Log.d(TAG,"need to grant permissions");
            requestPermissions(new String[]{permission} , requestCode);
        } else {
            Snackbar.make(binding.getRoot(), "Permissions already granted!", Snackbar.LENGTH_LONG).setAction("Action", null).show();
        }
    }

    /*@Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions, grantResults);

        if (requestCode == VPN_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this, "VPN Permission Granted", Toast.LENGTH_SHORT) .show();
            } else {
                Toast.makeText(MainActivity.this, "VPN Permission Denied", Toast.LENGTH_SHORT) .show();
            }
        }
    }*/


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
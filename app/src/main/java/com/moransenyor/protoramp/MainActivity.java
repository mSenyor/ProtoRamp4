package com.moransenyor.protoramp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    ListView deviceList;
    private BluetoothAdapter bluetoothAdapter = null;

    public static String EXTRA_ADDRESS = "com.moransenyor.protoramp3.MainActivity.EXTRA_ADDRESS";

    SharedPreferences prefs;
    boolean userRiskAgree;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        userRiskAgree = prefs.getBoolean("user_risk_agree", false);



        deviceList = (ListView) findViewById(R.id.device_list_view);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(atRisk()){
            userRiskPrompt();
        }
        else{
            if(isAirplaneModeOn(getApplicationContext())){
                bluetoothPrompt();
            }
            else{
                airplanePrompt();
            }
        }



        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(atRisk()){
                    userRiskPrompt();
                }
                else {
                    //bluetoothPrompt();
                    pairedDevicesList();
                }
            }
        });
    }

    private void pairedDevicesList()
    {
        Set<BluetoothDevice> pairedDevices;

        pairedDevices = bluetoothAdapter.getBondedDevices();

        ArrayList list = new ArrayList();

        if (pairedDevices.size()>0)
        {
            for(BluetoothDevice bt : pairedDevices)
            {
                list.add(bt.getName() + "\n" + bt.getAddress()); //Get the device's name and the address
            }
        }
        else
        {
            Toast.makeText(getApplicationContext(), R.string.no_paired_bluetooth_devices_found_message_text, Toast.LENGTH_LONG).show();
        }

        final ArrayAdapter adapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1, list);
        deviceList.setAdapter(adapter);
        deviceList.setOnItemClickListener(myListClickListener); //Method called when the device from the list is clicked
    }

    private AdapterView.OnItemClickListener myListClickListener = new AdapterView.OnItemClickListener()
    {
        public void onItemClick (AdapterView av, View v, int arg2, long arg3)
        {
            if(atRisk()){
                userRiskPrompt();
            }
            else {
                // Get the device MAC address, the last 17 chars in the View
                String info = ((TextView) v).getText().toString();
                String address = info.substring(info.length() - 17);
                // Make an intent to start next activity.
                Intent i = new Intent(MainActivity.this, RampController4.class);
                //Change the activity.
                i.putExtra(EXTRA_ADDRESS, address); //this will be received at ledControl (class) Activity
                startActivity(i);
            }
        }
    };

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
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public static boolean isBluetoothOn(Context context) {

        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.BLUETOOTH_ON, 0) != 0;
    }

    public void bluetoothPrompt(){
        if(bluetoothAdapter==null) {
            Toast.makeText(getApplicationContext(), R.string.bluetooth_device_not_available_message_text, Toast.LENGTH_LONG).show();
            //finish apk
            MainActivity.super.finish();
        }
        else if(isBluetoothOn(getApplicationContext())){
            if(!userRiskAgree){
                // TODO: 11/02/17 - prompt...
            }
        }
        else{
                //Ask to the user turn the bluetooth on
            userRiskPrompt();
            Intent turnBTon = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(turnBTon, 1);
        }
    }



    public static boolean isAirplaneModeOn(Context context) {

        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    private void airplanePrompt(){
        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);

        // set title
        alertDialogBuilder.setTitle(getString(R.string.airplane_mode_dialog_title));

        // set dialog message
        alertDialogBuilder
                .setMessage(getString(R.string.airplane_mode_dialog_message))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.airplane_mode_dialog_confirm),new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // if this button is clicked, close
                        if (isAirplaneModeOn(getApplicationContext())) {
                            bluetoothPrompt();
                        }
                        else{
                            Toast.makeText(getApplicationContext(), R.string.please_turn_on_airplane_mode_message_text, Toast.LENGTH_SHORT).show();
                            airplanePrompt();
                        }
                    }
                })
                .setNegativeButton(getString(R.string.airplane_mode_dialog_deny),new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // if this button is clicked, just close
                        // the dialog box and do nothing
                        dialog.cancel();
                        MainActivity.super.finish();
                    }
                });


        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    public void userRiskPrompt(){
        if(atRisk()){
            Intent intent = new Intent(this, UserAgreement.class);
            startActivity(intent);
        }
    }

    public boolean atRisk(){
        return !(prefs.getBoolean("user_risk_agree", false));
    }
}

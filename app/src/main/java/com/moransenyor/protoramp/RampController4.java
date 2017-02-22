package com.moransenyor.protoramp;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.UUID;

public class RampController4 extends AppCompatActivity {

    TextView debug;

    SharedPreferences prefs;

    Button btnUp, btnBoomUp, btnBoomDown, btnDown;
    SeekBar speedAdjuster;
    TextView speedDisplay;
    long speedChange;
    boolean speedRampUp, speedBoomUp, speedBoomDown, speedRampDown, speedAny, userRiskAgree;

    private ProgressDialog progressDialog;

    BluetoothAdapter bluetoothAdapter = null;
    BluetoothSocket bluetoothSocket = null;
    private boolean isConnected = false;
    //long count = 0;
    String address = null;

    static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ramp_controller4);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        debug = (TextView) findViewById(R.id.debug_display);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        userRiskAgree = prefs.getBoolean("user_risk_agree", false);
        speedRampUp = prefs.getBoolean("ramp_up_switch", false);
        speedBoomUp = prefs.getBoolean("boom_up_switch", false);
        speedBoomDown = prefs.getBoolean("boom_down_switch", false);
        speedRampDown = prefs.getBoolean("ramp_down_switch", false);
        speedAny = (!speedRampUp&&!speedBoomUp&&!speedBoomDown&&!speedRampDown);



        //String syncConnPref = sharedPref.getString(SettingsActivity., "");


        Intent newint = getIntent();
        address = newint.getStringExtra(MainActivity.EXTRA_ADDRESS);

        btnUp = (Button) findViewById(R.id.controller_up);
        btnBoomUp = (Button) findViewById(R.id.controller_boom_up);
        btnBoomDown = (Button) findViewById(R.id.controller_boom_down);
        btnDown = (Button) findViewById(R.id.controller_down);

        speedAdjuster = (SeekBar) findViewById(R.id.controller_speed_adjuster);
        speedAdjuster.setProgress(50);
        speedDisplay = (TextView) findViewById(R.id.controller_speed_display);
        speedChange = myNano();

        new ConnectBT().execute();

        btnUp.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    rampUp(speedRampUp);
                    return true;
                }
                if(event.getAction() == MotionEvent.ACTION_UP) {
                    rampStop();
                    return true;
                }
                return false;
            }
        });

        btnBoomUp.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    boomUp(speedBoomUp);
                    return true;
                }
                if(event.getAction() == MotionEvent.ACTION_UP) {
                    rampStop();
                    return true;
                }
                return false;
            }
        });

        btnBoomDown.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    boomDown(speedBoomDown);
                    return true;
                }
                if(event.getAction() == MotionEvent.ACTION_UP) {
                    rampStop();
                    return true;
                }
                return false;
            }
        });

        btnDown.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    rampDown(speedRampDown);
                    return true;
                }
                if(event.getAction() == MotionEvent.ACTION_UP) {
                    rampStop();
                    return true;
                }
                return false;
            }
        });



        speedAdjuster.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser){
                    int speedMax = speedAdjuster.getMax();
                    float percent = 100 - (progress / (speedMax / 100));
                    String speedStr = String.valueOf(percent);
                    speedStr += "%";
                    speedDisplay.setText(speedStr);
                    try{
                        String num = String.valueOf(lineToCurve(progress));
                        if(num.length() < 5){
                            for (int i = num.length(); i < 5; i++) {
                                num = "0"+num;
                            }
                        }
                        if(passed(250)){
                            bluetoothSocket.getOutputStream().write(num.getBytes());
                            //msgSent(num);
                        }
                    }
                    catch (IOException e){
                        msg(getString(R.string.onProgressChanged_method_error_message));
                    }
                    if(isAny()){ //only update speed if someone uses it
                        // TODO: 11/02/17 - make this work fast enough 
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rampStop();
            }
        });
    }

    // safety overrides in case app interrupted
    @Override
    protected void onPause() {
        rampStop();
        super.onPause();
    }

    @Override
    protected void onStop() {
        Disconnect();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Disconnect();
        super.onDestroy();
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void>  // UI thread
    {
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute()
        {
            progressDialog = ProgressDialog.show(RampController4.this, getString(R.string.connecting_status_message_text), getString(R.string.please_wait_status_message_text));  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try
            {
                if (bluetoothSocket == null || !isConnected)
                {
                    bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice dispositivo = bluetoothAdapter.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    bluetoothSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(uuid);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    bluetoothSocket.connect();//start connection
                }
            }
            catch (IOException e)
            {
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);

            if (!ConnectSuccess)
            {
                msg(getString(R.string.connection_failed_status_message_text));
                finish();
            }
            else
            {
                msg(getString(R.string.connected_status_message_text));
                isConnected = true;
            }
            progressDialog.dismiss();
        }
    }
    public void Disconnect()
    {
        rampStop();
        if (bluetoothSocket!=null) //If the btSocket is busy
        {
            try
            {
                bluetoothSocket.close(); //close connection
            }
            catch (IOException e)
            { msg(getString(R.string.disconnect_method_error_message_text));}
        }
        finish(); //return to the first layout
    }

    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }

    private long myNano(){
        return System.nanoTime()/1000000;
    }
    private boolean passed(int mili) {
        return ((myNano()-speedChange) > mili);
    }

    private int lineToCurve(int num) {
        return (num*num)/1000;
    }

    /*private void msgSent(String num){
        count++;
        debug.setText(String.valueOf(count)+": "+num);
    }*/


    private void sendString(String string) {
        if (bluetoothSocket!=null)
        {
            try
            {
                bluetoothSocket.getOutputStream().write(string.getBytes());
                //msgSent();
            }
            catch (IOException e)
            {
                //msg("Error: RampController4.java class, sendString(String) method");
            }
        }
    }

    private String makeMessage(String msg, boolean speed) {
        if(speed){
            msg += "T";
        }
        else {
            msg += "F";
        }
        return msg;
    }

    // ramp controller functions

    private void rampUp(boolean speed){
        sendString(makeMessage("RMUP", speed));
    }

    private void boomUp(boolean speed){
        sendString(makeMessage("BMUP", speed));
    }

    private void boomDown(boolean speed){
        sendString(makeMessage("BMDN", speed));
    }

    private void rampDown(boolean speed){
        sendString(makeMessage("RMDN", speed));
    }

    private void rampStop(){
        sendString("STOPF");
    }
    /* ToDo - figure out how to make this not suck ass
    public void rampHalt() {
        // TODO: 10/02/17 agressive ramp stop function to use in emergency
        new ConnectBT().execute();
        rampStop();

    }*/

    private boolean isAny(){
        speedAny = (!(prefs.getBoolean("ramp_up_switch", false))
                &&!(prefs.getBoolean("boom_up_switch", false))
                &&!(prefs.getBoolean("boom_down_switch", false))
                &&!(prefs.getBoolean("ramp_down_switch", false)));
        return speedAny;
    }

}

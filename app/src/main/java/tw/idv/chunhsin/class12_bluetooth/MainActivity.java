package tw.idv.chunhsin.class12_bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.SwitchCompat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    Switch aSwitch;
    Button btnSearch,btnConn,btnSend,btnDiscoverable;
    TextView textView;
    EditText editText;
    Spinner spinner;
    ListView listView;
    ArrayAdapter<String> devAdp;


    BluetoothAdapter btAdt;
    final int BLUETOOTH_RQ=1;
    Handler handler;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        aSwitch = (Switch)findViewById(R.id.switch1);
        listView = (ListView)findViewById(R.id.listView);
        listView.setOnItemClickListener(lvListener);
        spinner = (Spinner)findViewById(R.id.spinner);
        spinner.setOnItemSelectedListener(spListener);
        textView = (TextView)findViewById(R.id.textView);
        editText = (EditText)findViewById(R.id.editText);

        btnSearch = (Button)findViewById(R.id.button);
        btnConn = (Button)findViewById(R.id.button2);
        btnSend = (Button)findViewById(R.id.button3);
        btnDiscoverable = (Button)findViewById(R.id.button4);

        aSwitch.setOnCheckedChangeListener(OnCheckedChangeListener);
        handler=new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                btnDiscoverable.setText(String.valueOf(msg.what));
                if(msg.what==0){
                    btnDiscoverable.setText("可被搜尋");
                }
            }
        };
        setBlueAdapter();
        registBroadcast();
    }

    void setBlueAdapter(){
        btAdt = BluetoothAdapter.getDefaultAdapter();
        if(btAdt==null){
            Toast.makeText(this, "這個裝置不支裝藍芽", Toast.LENGTH_SHORT).show();
            return;
        }
        if(btAdt.isEnabled()){
            aSwitch.setChecked(true);
        }else{
            aSwitch.setChecked(false);
        }
    }

    CompoundButton.OnCheckedChangeListener OnCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if(isChecked){
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent,BLUETOOTH_RQ);
            }else{
                //aSwitch.setChecked(isChecked);
                btAdt.disable();
                devAdp.clear();
                spinner.setAdapter(devAdp);
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, final int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==BLUETOOTH_RQ){
            if(resultCode==RESULT_OK){
                btAdt.enable();
                queryPairedDevice();
                aSwitch.setChecked(true);
                new Thread(new ReadTask()).start();//啟動Server接聽Client
            }else{
                btAdt.disable();
                aSwitch.setChecked(false);
                /*
                devAdp.clear();
                spinner.setAdapter(devAdp);
                */
            }
        }
        if(requestCode==DISCOVERABLE_RQ){
            if(resultCode!=RESULT_CANCELED){
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        for(int i=resultCode;i>=0;i--){
                            handler.sendEmptyMessage(i);
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }).start();
            }
        }
    }

    //2. 查詢本機已配對的藍牙裝置
    void queryPairedDevice(){
        Set<BluetoothDevice> devices=btAdt.getBondedDevices();
        devAdp = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item);
        devAdp.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        for(BluetoothDevice device:devices){
            devAdp.add(device.getName()+" - "+device.getAddress());
        }
        spinner.setAdapter(devAdp);
    }

    class MyReciver extends BroadcastReceiver {
        ArrayAdapter<String> lvAdt=new ArrayAdapter<String>(MainActivity.this,android.R.layout.simple_list_item_1);

        @Override
        public void onReceive(Context context, Intent intent) {
            String action=intent.getAction();
            if(action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)){
                Toast.makeText(MainActivity.this,"開始搜尋裝置",Toast.LENGTH_SHORT).show();
            }
            if(action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)){
                Toast.makeText(MainActivity.this,"結束搜尋裝置",Toast.LENGTH_SHORT).show();
                listView.setAdapter(lvAdt);
            }
            if(action.equals(BluetoothDevice.ACTION_FOUND)){
                BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                lvAdt.add(device.getName()+" - "+device.getAddress());
            }


        }
    }

    MyReciver mr;
    void registBroadcast(){
        mr=new MyReciver();
        IntentFilter filter=new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);

        registerReceiver(mr,filter);

    }

    @Override
    protected void onStop() {
        unregisterReceiver(mr);
        super.onStop();
    }

    //讓手機可被搜尋
    final int DISCOVERABLE_RQ=101;
    public void onDiscoverable(View view){
        Intent intent=new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        //設定可被搜尋的秒數，預設是120秒
        intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,120);
        startActivityForResult(intent,DISCOVERABLE_RQ); //這個 Intent 會把設定的秒數傳到 onActivityResult 的resultCode
    }

    public void onDiscovery(View view){
        if(btAdt==null){
            return;
        }
        btAdt.startDiscovery();
    }


    AdapterView.OnItemSelectedListener spListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            String strMac=((TextView)view).getText().toString();
            String mac=strMac.substring(strMac.length()-17,strMac.length());
            textView.setText(mac);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    };

    AdapterView.OnItemClickListener lvListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            String strMac=((TextView)view).getText().toString();
            String mac=strMac.substring(strMac.length()-17,strMac.length());
            textView.setText(mac);
        }
    };
    String strUUID="00001101-0000-1000-8000-00805F9B34FB";
    BluetoothSocket socket;
    boolean isConnect = false;
    public void onConnection(View v){
        //當Client的連線
        BluetoothDevice btDevice=btAdt.getRemoteDevice(textView.getText().toString());
        try {
            if(isConnect) {
                socket.close();
                isConnect=false;
                btnConn.setText("連線");
            }else{
                socket=btDevice.createRfcommSocketToServiceRecord(UUID.fromString(strUUID));
                socket.connect();
                if(socket.isConnected()){
                    new Thread(new Runnable() {
                        String msg;
                        @Override
                        public void run() {
                            try {
                                InputStream is = socket.getInputStream();
                                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                                while(socket.isConnected()){
                                    msg=br.readLine();
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(MainActivity.this,msg,Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                                br.close();
                                is.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                    isConnect=true;
                    btnConn.setText("斷線");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onSubmit(View v){
        if(socket==null){
            return;
        }
        try {
            OutputStream os=socket.getOutputStream();
            os.write((editText.getText().toString()+"\n").getBytes());
            os.flush();
//            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //當Server
    class ReadTask implements Runnable{
        String msg = "";
        @Override
        //次執行緒執行的方法
        public void run() {
            try {
                BluetoothServerSocket serverSocket=btAdt.listenUsingRfcommWithServiceRecord("Server", UUID.fromString(strUUID));
                BluetoothSocket socket=serverSocket.accept();

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,"有人連線進來了...",Toast.LENGTH_SHORT).show();
                    }
                });
                serverSocket.close();

                //接收Client的資料
                InputStream is=socket.getInputStream();
                BufferedReader br=new BufferedReader(new InputStreamReader(is));

                while(socket!=null){
                    msg=br.readLine();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this,msg,Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                br.close();
                is.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

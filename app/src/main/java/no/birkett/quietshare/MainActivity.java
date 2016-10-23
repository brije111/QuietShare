package no.birkett.quietshare;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;
import org.quietmodem.Quiet.*;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.Subscriptions;

public class MainActivity extends AppCompatActivity {

    private FrameReceiver receiver;
    private FrameTransmitter transmitter;
    private Thread receiverThread;
    private TextView receivedContent;
    private EditText sendMessage;
    private Spinner profileSpinner;
    private ArrayAdapter<String> spinnerArrayAdapter;
    private TextView receiveStatus;
    private Subscription frameSubscription = Subscriptions.empty();

    private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleSendClick();
            }
        });
        receivedContent = (TextView) findViewById(R.id.received_content);
        sendMessage = (EditText) findViewById(R.id.send_message);
        profileSpinner = (Spinner) findViewById(R.id.profile);
        receiveStatus = (TextView) findViewById(R.id.receive_status);
        setupProfileSpinner();
        setupTransmitter();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        frameSubscription.unsubscribe();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    subscribeToFrames();
                } else {
                    showMissingAudioPermissionToast();
                }
            }
        }
    }

    private void setupTransmitter() {
        FrameTransmitterConfig transmitterConfig;
        try {
            transmitterConfig = new FrameTransmitterConfig(
                    this,getProfile());
            transmitter = new FrameTransmitter(transmitterConfig);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ModemException e) {
            throw new RuntimeException(e);
        }
    }

    private void setupReceiver() {
        if (hasRecordAudioPersmission()) {
            subscribeToFrames();
        } else {
            requestPermission();
        };
    }

    private void subscribeToFrames() {
        frameSubscription.unsubscribe();
        frameSubscription = FrameReceiverObservable.create(this, getProfile()).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(buf -> {
            receivedContent.setText(new String(buf, Charset.forName("UTF-8")));
            Long time = System.currentTimeMillis() / 1000;
            String timestamp = time.toString();
            receiveStatus.setText("Received " + buf.length + " @" + timestamp);
        }, error-> {
            receiveStatus.setText("error " + error.toString());
        });
    }

    private void handleSendClick() {
        if (transmitter == null) {
            setupTransmitter();
        }
        send();
    }

    private void send() {
        String payload = sendMessage.getText().toString();
        try {
            transmitter.send(payload.getBytes());
        } catch (IOException e) {
            // our message might be too long or the transmit queue full
        }
    }

    boolean hasRecordAudioPersmission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
    }


    private void showMissingAudioPermissionToast() {
        Context context = getApplicationContext();
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, R.string.missing_audio_permission, duration);
        toast.show();
    }

    private ArrayList<String> getProfiles() {
        ArrayList<String> profiles = new ArrayList<>();
        try {
            String json = FrameTransmitterConfig.getDefaultProfiles(this);
            JSONObject jsonObject = new JSONObject(json);
            Iterator<String> iterator = jsonObject.keys();

            while(iterator.hasNext()) {
                profiles.add(iterator.next());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return profiles;
    }

    private void setupProfileSpinner() {
        final ArrayList<String> profiles = getProfiles();
        spinnerArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, profiles);
        profileSpinner.setAdapter(spinnerArrayAdapter);

        profileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                transmitter = null;
                setupReceiver();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

    }

    private String getProfile() {
        String profile = spinnerArrayAdapter.getItem(profileSpinner.getSelectedItemPosition());
        return profile;
    }
}

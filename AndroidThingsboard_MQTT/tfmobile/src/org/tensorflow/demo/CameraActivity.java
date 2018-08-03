/*
 * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.demo;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.pm.PackageManager;
import android.media.Image.Plane;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Toast;
import java.nio.ByteBuffer;
import java.util.List;

import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.tensorflow.demo.env.Logger;
import org.tensorflow.demo.R;
import android.view.View;
import android.widget.Toast;
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;




public abstract class CameraActivity extends Activity implements OnImageAvailableListener , MqttCallback {
  private static final Logger LOGGER = new Logger();

  private static final int PERMISSIONS_REQUEST = 1;

  private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
  private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;

  private boolean debug = false;

  private Handler handler;
  private HandlerThread handlerThread;
  private String LOGTAG ="logging";
  private String MQTTHOST="tcp://demo.thingsboard.io:1883";
  private String USERNAME="HH0sTAsk89kiYTezmXv4"; //access_token of device
  private String PASSWORD="";


  protected List<Classifier.Recognition> results;

  int value=1;
  float val;

  MqttAndroidClient mqttAndroidClient;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {

    //final List<Classifier.Recognition> results;

    LOGGER.d("onCreate " + this);
    super.onCreate(null);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    setContentView(R.layout.activity_camera);

///////////////////////////////////////MQTT///////////////////////////////////////////////////////

    String clientId = MqttClient.generateClientId();
    mqttAndroidClient = new MqttAndroidClient(this.getApplicationContext(),MQTTHOST,clientId);

    MqttConnectOptions options = new MqttConnectOptions();
    options.setUserName(USERNAME);
    options.setPassword(PASSWORD.toCharArray());

    try{

      Toast.makeText(getApplicationContext(), "InsideTry",
              Toast.LENGTH_SHORT).show();
      IMqttToken token = mqttAndroidClient.connect(options);
      token.setActionCallback(new IMqttActionListener() {
        @Override
        public void onSuccess(IMqttToken asyncActionToken) {
          Toast.makeText(CameraActivity.this, "Connected", Toast.LENGTH_SHORT).show();
          Toast.makeText(getApplicationContext(), "Success",
                  Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
          Toast.makeText(CameraActivity.this, "Failed to connect", Toast.LENGTH_SHORT).show();
          Toast.makeText(getApplicationContext(), "Failure",
                  Toast.LENGTH_SHORT).show();
        }
      });
    }
    catch (Exception e){}
    //////////////////////////////////////////////////////////////////////////////

    if (hasPermission()) {
      setFragment();
    } else {
      requestPermission();
    }
  }

  @Override
  public synchronized void onStart() {
    LOGGER.d("onStart " + this);
    super.onStart();
  }

  @Override
  public synchronized void onResume() {
    LOGGER.d("onResume " + this);
    super.onResume();

    handlerThread = new HandlerThread("inference");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
  }

  @Override
  public synchronized void onPause() {
    LOGGER.d("onPause " + this);

    if (!isFinishing()) {
      LOGGER.d("Requesting finish");
      finish();
    }

    handlerThread.quitSafely();
    try {
      handlerThread.join();
      handlerThread = null;
      handler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }

    super.onPause();
  }

  @Override
  public synchronized void onStop() {
    LOGGER.d("onStop " + this);
    super.onStop();
  }

  @Override
  public synchronized void onDestroy() {
    LOGGER.d("onDestroy " + this);
    super.onDestroy();
  }


  protected synchronized void runInBackground(final Runnable r) {

    if (results != null) {
       if(results.iterator().hasNext())
       {
         Classifier.Recognition xx = results.iterator().next();
         String label=xx.getTitle();
         //val = xx.getConfidence();
         publishMessage2(label);
       }
//      for (final Classifier.Recognition recog : results){
//        //canvas.drawText(recog.getTitle() + ": " + recog.getConfidence(), x, y, fgPaint);
//        // y += fgPaint.getTextSize() * 1.5f;
//        val=recog.getConfidence();
//        //publishMessage2(14);
//        publishMessage2(val);

    }

    if (handler != null) {
      handler.post(r);

    }
  }

  @Override
  public void onRequestPermissionsResult(
      final int requestCode, final String[] permissions, final int[] grantResults) {
    switch (requestCode) {
      case PERMISSIONS_REQUEST: {
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
          setFragment();
        } else {
          requestPermission();
        }
      }
    }
  }


  private boolean hasPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(PERMISSION_STORAGE) == PackageManager.PERMISSION_GRANTED;
    } else {
      return true;
    }
  }

  private void requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA) || shouldShowRequestPermissionRationale(PERMISSION_STORAGE)) {
        Toast.makeText(CameraActivity.this, "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
      }
      requestPermissions(new String[] {PERMISSION_CAMERA, PERMISSION_STORAGE}, PERMISSIONS_REQUEST);
    }
  }

  protected void setFragment() {
    final Fragment fragment =
        CameraConnectionFragment.newInstance(
            new CameraConnectionFragment.ConnectionCallback() {
              @Override
              public void onPreviewSizeChosen(final Size size, final int rotation) {
                CameraActivity.this.onPreviewSizeChosen(size, rotation);
              }
            },
            this,
            getLayoutId(),
            getDesiredPreviewFrameSize());

    getFragmentManager()
        .beginTransaction()
        .replace(R.id.container, fragment)
        .commit();
  }

  protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (int i = 0; i < planes.length; ++i) {
      final ByteBuffer buffer = planes[i].getBuffer();
      if (yuvBytes[i] == null) {
        LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
        yuvBytes[i] = new byte[buffer.capacity()];
      }
      buffer.get(yuvBytes[i]);
    }
  }

  public boolean isDebug() {
    return debug;
  }

  public void requestRender() {
    final OverlayView overlay = (OverlayView) findViewById(R.id.debug_overlay);
    if (overlay != null) {
      overlay.postInvalidate();
    }
  }

  public void addCallback(final OverlayView.DrawCallback callback) {
    final OverlayView overlay = (OverlayView) findViewById(R.id.debug_overlay);
    if (overlay != null) {
      overlay.addCallback(callback);
    }
  }

  public void onSetDebug(final boolean debug) {}



  public void publishMessage(View view)
  //public void publishMessage2(float prediction)
  {
    //final String publishMessage= "{\"Gauge Dial Readings\":\""+Float.toString(value)+"\"}";
    final String publishMessage= "{\"Gauge Dial Readings\":\"245\"}";
    final String publishTopic = "v1/devices/me/telemetry";
    value=value+1;

    try {
      mqttAndroidClient.publish(publishTopic,publishMessage.getBytes(),0,false);
      // Toast.makeText(getApplicationContext(), "Published to Thingsboard.",
      //         Toast.LENGTH_SHORT).show();

    } catch (MqttException e) {
      e.printStackTrace();
    }
  }

  //  public void publishMessage(View view)
  public void publishMessage2(String prediction)
  {
    //final String publishMessage= "{\"device\":\"234\"}";
    //final String publishMessage= "{\"Gauge Dial Readings\":\"945\"}";
    //final String publishMessage= "{\"Gauge Dial Readings\":\""+Float.toString(prediction)+"\"}";
    final String publishMessage= "{\"Gauge Dial Readings\":\""+prediction+"\"}";
    final String publishTopic = "v1/devices/me/telemetry";
    try {
      mqttAndroidClient.publish(publishTopic,publishMessage.getBytes(),0,false);
       Toast.makeText(getApplicationContext(), "Publishing to Thingsboard.",
               Toast.LENGTH_SHORT).show();

    } catch (MqttException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void connectionLost(Throwable cause) {}

  @Override
  public void messageArrived(String topic, MqttMessage message) throws Exception {
    Toast.makeText(CameraActivity.this, "Topic: "+topic+"\nMessage: "+message, Toast.LENGTH_LONG).show();
  }

  @Override
  public void deliveryComplete(IMqttDeliveryToken token) {}


  @Override
  public boolean onKeyDown(final int keyCode, final KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      debug = !debug;
      requestRender();
      onSetDebug(debug);
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  protected abstract void onPreviewSizeChosen(final Size size, final int rotation);
  protected abstract int getLayoutId();
  protected abstract Size getDesiredPreviewFrameSize();
}

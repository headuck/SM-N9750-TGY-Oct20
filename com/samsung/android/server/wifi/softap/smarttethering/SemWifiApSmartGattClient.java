package com.samsung.android.server.wifi.softap.smarttethering;

import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.FactoryTest;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.LocalLog;
import android.util.Log;
import android.util.Pair;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.iwc.IWCEventManager;
import com.android.server.wifi.rtt.RttServiceImpl;
import com.samsung.android.feature.SemCscFeature;
import com.samsung.android.net.wifi.SemWifiApContentProviderHelper;
import com.samsung.android.server.wifi.dqa.ReportIdKey;
import com.samsung.android.server.wifi.softap.SemWifiApBroadcastReceiver;
import com.sec.android.app.CscFeatureTagWifi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class SemWifiApSmartGattClient {
    private static final String ACTION_LOGOUT_ACCOUNTS_COMPLETE = "com.samsung.account.SAMSUNGACCOUNT_SIGNOUT_COMPLETED";
    private static final String CONFIG_OP_BRANDING = SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGOPBRANDING);
    private static final int CONNECTION_DELAY_SEC = 5000;
    private static final int ST_AUTH_FAILED = -3;
    private static final int ST_BONDING_FAILURE = -10;
    private static final int ST_BONDING_GOINGON = -5;
    private static final int ST_BOND_FAILED = -2;
    private static final int ST_CONNECTION_ALREADY_EXIST = -4;
    private static final int ST_DEVICE_NOT_FOUND = -1;
    private static final int ST_GATT_CONNECTING = 1;
    private static final int ST_GATT_FAILURE = -7;
    private static final int ST_MHS_ENABLING_FAILURE = -8;
    private static final int ST_MHS_GATT_CLIENT_TIMEOUT = -12;
    private static final int ST_MHS_GATT_SERVICE_NOT_FOUND = -13;
    private static final int ST_MHS_USERNAME_FAILED = -9;
    private static final int ST_MHS_WIFI_CONNECTION_TIMEOUT = -11;
    private static final int ST_WIFI_CONNECTED = 3;
    private static final int ST_WIFI_CONNECTING = 2;
    private static final int ST_WIFI_DISCONNECTED = 0;
    private static IntentFilter mSemWifiApSmartGattClientIntentFilter = new IntentFilter("android.net.wifi.SCAN_RESULTS");
    private final int DISCONNECT_GATT = 12;
    private final int DISCONNECT_HOTSPOT = 14;
    private final int GATT_CONNECTION_TIMEOUT = 45000;
    private final int GATT_TRANSACTION_TIMEOUT = 60000;
    private final int GENERATE_CONNECT_WIFI = 11;
    private final int MHS_CONNECTION_TIMEOUT = 25000;
    private String TAG = "SemWifiApSmartGattClient";
    private final int UPDATE_CONNECTION_FAILURES = 18;
    private final int UPDATE_CONNECTION_FAILURES_TIMER = 5000;
    private final int WAIT_FOR_MTU_CALLBACK = 19;
    private HashSet<String> bonedDevicesFromHotspotLive = new HashSet<>();
    private boolean isBondingGoingon = false;
    private boolean isShowingDisConnectNotification = false;
    private String mAESKey = null;
    private BleWorkHandler mBleWorkHandler = null;
    private HandlerThread mBleWorkThread = null;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGattService mBluetoothGattService;
    private boolean mBluetoothIsOn = false;
    private BluetoothGatt mConnectedGatt = null;
    private Context mContext;
    private long mDelayStartFrom = -1;
    HashMap<String, Integer> mFailedBLEConnections = new HashMap<>();
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        /* class com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartGattClient.C08101 */

        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            LocalLog localLog = SemWifiApSmartGattClient.this.mLocalLog;
            localLog.log(SemWifiApSmartGattClient.this.TAG + ":\tonConnectionStateChange " + SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getStatusDescription(status) + " " + SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getStateDescription(newState));
            String str = SemWifiApSmartGattClient.this.TAG;
            Log.d(str, "onConnectionStateChange: " + SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getStatusDescription(status) + " " + SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getStateDescription(newState));
            if (newState == 2) {
                String str2 = SemWifiApSmartGattClient.this.TAG;
                Log.d(str2, "device,connected" + gatt.getDevice());
                if (SemWifiApSmartGattClient.this.mBleWorkHandler != null) {
                    SemWifiApSmartGattClient.this.mBleWorkHandler.sendEmptyMessageDelayed(19, 300);
                }
            } else if (newState == 0) {
                String str3 = SemWifiApSmartGattClient.this.TAG;
                Log.d(str3, "device, disconnected" + gatt.getDevice());
                if (status != 0) {
                    String mBTaddr = gatt.getDevice().getAddress();
                    Integer count = SemWifiApSmartGattClient.this.mFailedBLEConnections.get(mBTaddr);
                    if (count == null) {
                        SemWifiApSmartGattClient semWifiApSmartGattClient = SemWifiApSmartGattClient.this;
                        semWifiApSmartGattClient.setConnectionState(SemWifiApSmartGattClient.ST_GATT_FAILURE, semWifiApSmartGattClient.mSmartAp_WiFi_MAC);
                        SemWifiApSmartGattClient.this.mFailedBLEConnections.remove(mBTaddr);
                        SemWifiApSmartGattClient.this.shutdownclient();
                    } else if (count.intValue() >= 3) {
                        SemWifiApSmartGattClient semWifiApSmartGattClient2 = SemWifiApSmartGattClient.this;
                        semWifiApSmartGattClient2.setConnectionState(SemWifiApSmartGattClient.ST_GATT_FAILURE, semWifiApSmartGattClient2.mSmartAp_WiFi_MAC);
                        SemWifiApSmartGattClient.this.mFailedBLEConnections.remove(mBTaddr);
                        SemWifiApSmartGattClient.this.shutdownclient();
                    } else if (count.intValue() < 3) {
                        SemWifiApSmartGattClient.this.shutdownclient_1();
                        gatt.refresh();
                        try {
                            Thread.sleep(1000);
                        } catch (Exception e) {
                        }
                        if (gatt.getDevice().getBondState() == 12) {
                            if (!SemWifiApSmartGattClient.this.tryToConnectToRemoteBLE(gatt.getDevice(), true)) {
                                SemWifiApSmartGattClient semWifiApSmartGattClient3 = SemWifiApSmartGattClient.this;
                                semWifiApSmartGattClient3.setConnectionState(SemWifiApSmartGattClient.ST_GATT_FAILURE, semWifiApSmartGattClient3.mSmartAp_WiFi_MAC);
                                SemWifiApSmartGattClient.this.mFailedBLEConnections.remove(mBTaddr);
                                SemWifiApSmartGattClient.this.shutdownclient();
                            }
                        } else if (!SemWifiApSmartGattClient.this.tryToConnectToRemoteBLE(gatt.getDevice(), false)) {
                            SemWifiApSmartGattClient semWifiApSmartGattClient4 = SemWifiApSmartGattClient.this;
                            semWifiApSmartGattClient4.setConnectionState(SemWifiApSmartGattClient.ST_GATT_FAILURE, semWifiApSmartGattClient4.mSmartAp_WiFi_MAC);
                            SemWifiApSmartGattClient.this.mFailedBLEConnections.remove(mBTaddr);
                            SemWifiApSmartGattClient.this.shutdownclient();
                        }
                    }
                } else {
                    if (gatt.getDevice() != null) {
                        SemWifiApSmartGattClient.this.mFailedBLEConnections.remove(gatt.getDevice().getAddress());
                    }
                    SemWifiApSmartGattClient.this.shutdownclient();
                }
            }
        }

        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (mtu == 512) {
                gatt.discoverServices();
                if (SemWifiApSmartGattClient.this.mBleWorkHandler != null && SemWifiApSmartGattClient.this.mBleWorkHandler.hasMessages(19)) {
                    SemWifiApSmartGattClient.this.mBleWorkHandler.removeMessages(19);
                }
            }
        }

        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            String str = SemWifiApSmartGattClient.this.TAG;
            Log.d(str, "onCharacteristicChanged:" + SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.lookup(characteristic.getUuid()));
            LocalLog localLog = SemWifiApSmartGattClient.this.mLocalLog;
            localLog.log(SemWifiApSmartGattClient.this.TAG + "\tonCharacteristicChanged:" + SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.lookup(characteristic.getUuid()));
            SemWifiApSmartUtil unused = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_MHS_ENABLED.equals(characteristic.getUuid()) && SemWifiApSmartGattClient.this.requestedToEnableMHS) {
                SemWifiApSmartGattClient.this.requestedToEnableMHS = false;
                BluetoothGattService bluetoothGattService = SemWifiApSmartGattClient.this.mBluetoothGattService;
                SemWifiApSmartUtil unused2 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                gatt.readCharacteristic(bluetoothGattService.getCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_MHS_STATUS_UUID));
            }
        }

        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            String str = SemWifiApSmartGattClient.this.TAG;
            Log.d(str, "onServicesDiscovered:" + SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getStatusDescription(status));
            boolean found = false;
            for (BluetoothGattService service : gatt.getServices()) {
                String str2 = SemWifiApSmartGattClient.this.TAG;
                Log.d(str2, "Service: " + SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.lookup(service.getUuid()));
                SemWifiApSmartUtil unused = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                if (SemWifiApSmartUtil.SERVICE_UUID.equals(service.getUuid())) {
                    Log.i(SemWifiApSmartGattClient.this.TAG, "Service: mSemWifiApSmartUtil.SERVICE_UUID");
                    LocalLog localLog = SemWifiApSmartGattClient.this.mLocalLog;
                    localLog.log(SemWifiApSmartGattClient.this.TAG + "\tService: mSemWifiApSmartUtil.SERVICE_UUID");
                    SemWifiApSmartGattClient.this.mBluetoothGattService = service;
                    found = true;
                    if (SemWifiApSmartGattClient.this.mAESKey != null) {
                        Log.i(SemWifiApSmartGattClient.this.TAG, "read CHARACTERISTIC_MHS_SIDE_GET_TIME");
                        BluetoothGattService bluetoothGattService = SemWifiApSmartGattClient.this.mBluetoothGattService;
                        SemWifiApSmartUtil unused2 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                        gatt.readCharacteristic(bluetoothGattService.getCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_MHS_SIDE_GET_TIME));
                    } else {
                        Log.i(SemWifiApSmartGattClient.this.TAG, "read CHARACTERISTIC_MHS_BOND_STATUS");
                        BluetoothGattService bluetoothGattService2 = SemWifiApSmartGattClient.this.mBluetoothGattService;
                        SemWifiApSmartUtil unused3 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                        gatt.readCharacteristic(bluetoothGattService2.getCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_MHS_BOND_STATUS));
                    }
                }
            }
            if (!found) {
                SemWifiApSmartGattClient semWifiApSmartGattClient = SemWifiApSmartGattClient.this;
                semWifiApSmartGattClient.setConnectionState(SemWifiApSmartGattClient.ST_MHS_GATT_SERVICE_NOT_FOUND, semWifiApSmartGattClient.mSmartAp_WiFi_MAC);
                if (SemWifiApSmartGattClient.this.mBleWorkHandler != null) {
                    SemWifiApSmartGattClient.this.mBleWorkHandler.sendEmptyMessage(12);
                }
            }
        }

        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            byte[] mDeviceNametemp;
            byte[] temp;
            byte[] temp2;
            byte[] temp3;
            super.onCharacteristicWrite(gatt, characteristic, status);
            String str = SemWifiApSmartGattClient.this.TAG;
            Log.d(str, "onCharacteristicWrite:" + SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.lookup(characteristic.getUuid()) + ",status:" + SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getStatusDescription(status));
            LocalLog localLog = SemWifiApSmartGattClient.this.mLocalLog;
            localLog.log(SemWifiApSmartGattClient.this.TAG + "\tonCharacteristicWrite:" + SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.lookup(characteristic.getUuid()) + ",status:" + SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getStatusDescription(status));
            SemWifiApSmartUtil unused = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_MHS_VER_UPDATE.equals(characteristic.getUuid())) {
                BluetoothGattService bluetoothGattService = SemWifiApSmartGattClient.this.mBluetoothGattService;
                SemWifiApSmartUtil unused2 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                BluetoothGattCharacteristic mtemp = bluetoothGattService.getCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_MHS_ENABLED);
                gatt.setCharacteristicNotification(mtemp, true);
                mtemp.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeCharacteristic(mtemp);
                return;
            }
            SemWifiApSmartUtil unused3 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_MHS_ENABLED.equals(characteristic.getUuid())) {
                BluetoothGattService bluetoothGattService2 = SemWifiApSmartGattClient.this.mBluetoothGattService;
                SemWifiApSmartUtil unused4 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                BluetoothGattCharacteristic mtemp2 = bluetoothGattService2.getCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_ENCRYPTED_AUTH_ID);
                String mDeviceName = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getHostNameFromDeviceName();
                String mDeviceWifiMAC = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getOwnWifiMac();
                String mFamilyGuid = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getGuid();
                if (mFamilyGuid == null) {
                    mFamilyGuid = "";
                }
                mFamilyGuid.length();
                if (mDeviceName == null) {
                    mDeviceNametemp = "".getBytes();
                } else {
                    mDeviceNametemp = mDeviceName.getBytes();
                }
                byte[] mDeviceWifiMACtemp = mDeviceWifiMAC.getBytes();
                byte[] mGuidBytes = mFamilyGuid.getBytes();
                if (SemWifiApSmartGattClient.this.mUserType == 1) {
                    Log.d(SemWifiApSmartGattClient.this.TAG, "Autheticating for same GUID");
                    LocalLog localLog2 = SemWifiApSmartGattClient.this.mLocalLog;
                    localLog2.log(SemWifiApSmartGattClient.this.TAG + ":\tAutheticating for same GUID");
                    if (SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getGuid() == null) {
                        temp3 = "".getBytes();
                    } else {
                        temp3 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getGuid().getBytes();
                    }
                    byte[] mdata = new byte[(temp3.length + 10 + mDeviceNametemp.length + mDeviceWifiMACtemp.length)];
                    mdata[0] = 1;
                    mdata[1] = (byte) temp3.length;
                    for (int i = 0; i < temp3.length; i++) {
                        mdata[i + 2] = temp3[i];
                    }
                    mdata[temp3.length + 2] = (byte) mDeviceNametemp.length;
                    for (int i2 = 0; i2 < mDeviceNametemp.length; i2++) {
                        mdata[i2 + 2 + temp3.length + 1] = mDeviceNametemp[i2];
                    }
                    mdata[temp3.length + 3 + mDeviceNametemp.length] = (byte) mDeviceWifiMACtemp.length;
                    for (int i3 = 0; i3 < mDeviceWifiMACtemp.length; i3++) {
                        mdata[i3 + 4 + temp3.length + mDeviceNametemp.length] = mDeviceWifiMACtemp[i3];
                    }
                    if (SemWifiApSmartGattClient.this.mAESKey != null) {
                        String str2 = SemWifiApSmartGattClient.this.TAG;
                        Log.d(str2, "Using AES:" + SemWifiApSmartGattClient.this.mAESKey);
                        byte[] mAESdata = AES.encrypt(new String(mdata), SemWifiApSmartGattClient.this.mAESKey).getBytes();
                        if (mAESdata == null) {
                            Log.e(SemWifiApSmartGattClient.this.TAG, " Encryption can't be null");
                        }
                        String str3 = SemWifiApSmartGattClient.this.TAG;
                        Log.d(str3, "Encrypted size is" + mAESdata.length + "," + new String(mAESdata));
                        mtemp2.setValue(mAESdata);
                    } else {
                        mtemp2.setValue(mdata);
                    }
                } else if (SemWifiApSmartGattClient.this.mUserType == 2) {
                    Log.d(SemWifiApSmartGattClient.this.TAG, "Autheticating for same family ID");
                    LocalLog localLog3 = SemWifiApSmartGattClient.this.mLocalLog;
                    localLog3.log(SemWifiApSmartGattClient.this.TAG + ":\tAutheticating for same family ID");
                    if (SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getFamilyID() == null) {
                        temp2 = "".getBytes();
                    } else {
                        temp2 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getFamilyID().getBytes();
                    }
                    byte[] mdata2 = new byte[(temp2.length + 10 + mDeviceNametemp.length + mDeviceWifiMACtemp.length + mGuidBytes.length)];
                    mdata2[0] = 2;
                    mdata2[1] = (byte) temp2.length;
                    for (int i4 = 0; i4 < temp2.length; i4++) {
                        mdata2[i4 + 2] = temp2[i4];
                    }
                    mdata2[temp2.length + 2] = (byte) mDeviceNametemp.length;
                    for (int i5 = 0; i5 < mDeviceNametemp.length; i5++) {
                        mdata2[i5 + 2 + temp2.length + 1] = mDeviceNametemp[i5];
                    }
                    mdata2[temp2.length + 3 + mDeviceNametemp.length] = (byte) mDeviceWifiMACtemp.length;
                    for (int i6 = 0; i6 < mDeviceWifiMACtemp.length; i6++) {
                        mdata2[i6 + 4 + temp2.length + mDeviceNametemp.length] = mDeviceWifiMACtemp[i6];
                    }
                    mdata2[temp2.length + 4 + mDeviceNametemp.length + mDeviceWifiMACtemp.length] = (byte) mGuidBytes.length;
                    for (int i7 = 0; i7 < mGuidBytes.length; i7++) {
                        mdata2[i7 + 5 + temp2.length + mDeviceNametemp.length + mDeviceWifiMACtemp.length] = mGuidBytes[i7];
                    }
                    mtemp2.setValue(mdata2);
                } else if (SemWifiApSmartGattClient.this.mUserType == 3) {
                    Log.d(SemWifiApSmartGattClient.this.TAG, "Autheticating for Allowed User");
                    LocalLog localLog4 = SemWifiApSmartGattClient.this.mLocalLog;
                    localLog4.log(SemWifiApSmartGattClient.this.TAG + ":\tAutheticating for Allowed User");
                    if (SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getD2DFamilyID() == null) {
                        temp = "".getBytes();
                    } else {
                        temp = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getD2DFamilyID().getBytes();
                    }
                    byte[] mdata3 = new byte[(temp.length + 10 + mDeviceNametemp.length + mDeviceWifiMACtemp.length)];
                    mdata3[0] = 3;
                    mdata3[1] = (byte) temp.length;
                    for (int i8 = 0; i8 < temp.length; i8++) {
                        mdata3[i8 + 2] = temp[i8];
                    }
                    mdata3[temp.length + 2] = (byte) mDeviceNametemp.length;
                    for (int i9 = 0; i9 < mDeviceNametemp.length; i9++) {
                        mdata3[i9 + 2 + temp.length + 1] = mDeviceNametemp[i9];
                    }
                    mdata3[temp.length + 3 + mDeviceNametemp.length] = (byte) mDeviceWifiMACtemp.length;
                    for (int i10 = 0; i10 < mDeviceWifiMACtemp.length; i10++) {
                        mdata3[i10 + 4 + temp.length + mDeviceNametemp.length] = mDeviceWifiMACtemp[i10];
                    }
                    mtemp2.setValue(mdata3);
                }
                String str4 = SemWifiApSmartGattClient.this.TAG;
                Log.d(str4, "Write Characterstic:" + mDeviceName);
                gatt.writeCharacteristic(mtemp2);
                return;
            }
            SemWifiApSmartUtil unused5 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_ENCRYPTED_AUTH_ID.equals(characteristic.getUuid())) {
                BluetoothGattService bluetoothGattService3 = SemWifiApSmartGattClient.this.mBluetoothGattService;
                SemWifiApSmartUtil unused6 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                gatt.readCharacteristic(bluetoothGattService3.getCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_AUTH_STATUS));
            }
        }

        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            String str = SemWifiApSmartGattClient.this.TAG;
            Log.d(str, "onCharacteristicRead:" + SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.lookup(characteristic.getUuid()) + ",status:" + SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getStatusDescription(status));
            LocalLog localLog = SemWifiApSmartGattClient.this.mLocalLog;
            localLog.log(SemWifiApSmartGattClient.this.TAG + ":\tonCharacteristicRead:" + SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.lookup(characteristic.getUuid()) + ",status:" + SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getStatusDescription(status));
            SemWifiApSmartUtil unused = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_MHS_SIDE_GET_TIME.equals(characteristic.getUuid())) {
                byte[] mbytes = characteristic.getValue();
                String str2 = SemWifiApSmartGattClient.this.TAG;
                Log.i(str2, "received mhs time:" + Arrays.toString(mbytes));
                if (mbytes != null) {
                    long mhs_time = Long.parseLong(new String(mbytes));
                    SemWifiApSmartGattClient semWifiApSmartGattClient = SemWifiApSmartGattClient.this;
                    semWifiApSmartGattClient.mAESKey = semWifiApSmartGattClient.mSemWifiApSmartUtil.getAESKey(mhs_time);
                    String str3 = SemWifiApSmartGattClient.this.TAG;
                    Log.d(str3, "received mhs_time" + mhs_time + ",mAESKey:" + SemWifiApSmartGattClient.this.mAESKey);
                }
                if (mbytes == null || SemWifiApSmartGattClient.this.mAESKey == null) {
                    Log.e(SemWifiApSmartGattClient.this.TAG, "Time mismatch ocured,need to establish 1.0 connection");
                    SemWifiApSmartGattClient.this.mTimeMismatchOccured = true;
                    if (gatt.getDevice().getBondState() != 10) {
                        BluetoothGattService bluetoothGattService = SemWifiApSmartGattClient.this.mBluetoothGattService;
                        SemWifiApSmartUtil unused2 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                        gatt.readCharacteristic(bluetoothGattService.getCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_MHS_BOND_STATUS));
                    } else if (SemWifiApSmartGattClient.this.mBluetoothAdapter == null || SemWifiApSmartGattClient.this.mBluetoothAdapter.getState() != 10) {
                        SemWifiApSmartGattClient.this.mPendingDeviceAddress = gatt.getDevice().getAddress();
                        gatt.getDevice().createBond(2);
                    } else {
                        String str4 = SemWifiApSmartGattClient.this.TAG;
                        Log.d(str4, "device is not bonded, enabling BT adapter,mBluetoothIsOn:" + SemWifiApSmartGattClient.this.mBluetoothIsOn);
                        LocalLog localLog2 = SemWifiApSmartGattClient.this.mLocalLog;
                        localLog2.log(SemWifiApSmartGattClient.this.TAG + ":\tdevice is not bonded, enabling BT adapter,mBluetoothIsOn:" + SemWifiApSmartGattClient.this.mBluetoothIsOn);
                        SemWifiApSmartGattClient.this.mBluetoothAdapter.enable();
                        SemWifiApSmartGattClient.this.mBluetoothIsOn = true;
                        SemWifiApSmartGattClient.this.mBluetoothDevice = gatt.getDevice();
                    }
                } else {
                    BluetoothGattService bluetoothGattService2 = SemWifiApSmartGattClient.this.mBluetoothGattService;
                    SemWifiApSmartUtil unused3 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                    BluetoothGattCharacteristic mtemp = bluetoothGattService2.getCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_MHS_VER_UPDATE);
                    SemWifiApSmartUtil unused4 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                    SemWifiApSmartUtil unused5 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                    mtemp.setValue(new byte[]{1, 1});
                    String str5 = SemWifiApSmartGattClient.this.TAG;
                    Log.d(str5, "Write Characterstic version:" + SemWifiApSmartGattClient.this.mversion);
                    gatt.writeCharacteristic(mtemp);
                }
            } else {
                SemWifiApSmartUtil unused6 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                if (SemWifiApSmartUtil.CHARACTERISTIC_MHS_BOND_STATUS.equals(characteristic.getUuid())) {
                    byte[] mbytes2 = characteristic.getValue();
                    if (mbytes2 == null || mbytes2.length <= 0 || mbytes2[0] != 1) {
                        BluetoothDevice mDevice = gatt.getDevice();
                        if (mDevice != null) {
                            Log.e(SemWifiApSmartGattClient.this.TAG, "device is not bonded at MHS side ,so removing the device");
                            LocalLog localLog3 = SemWifiApSmartGattClient.this.mLocalLog;
                            localLog3.log(SemWifiApSmartGattClient.this.TAG + ":\tdevice is not bonded at MHS side ,so removing the device");
                            mDevice.removeBond();
                        }
                        SemWifiApSmartGattClient semWifiApSmartGattClient2 = SemWifiApSmartGattClient.this;
                        semWifiApSmartGattClient2.setConnectionState(SemWifiApSmartGattClient.ST_BONDING_FAILURE, semWifiApSmartGattClient2.mSmartAp_WiFi_MAC);
                        if (SemWifiApSmartGattClient.this.mBleWorkHandler != null) {
                            SemWifiApSmartGattClient.this.mBleWorkHandler.sendEmptyMessage(12);
                            return;
                        }
                        return;
                    }
                    String str6 = SemWifiApSmartGattClient.this.TAG;
                    Log.d(str6, "Got bond status:" + ((int) mbytes2[0]));
                    LocalLog localLog4 = SemWifiApSmartGattClient.this.mLocalLog;
                    localLog4.log(SemWifiApSmartGattClient.this.TAG + ":\tGot bond status:" + ((int) mbytes2[0]));
                    BluetoothGattService bluetoothGattService3 = SemWifiApSmartGattClient.this.mBluetoothGattService;
                    SemWifiApSmartUtil unused7 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                    BluetoothGattCharacteristic mtemp2 = bluetoothGattService3.getCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_MHS_ENABLED);
                    gatt.setCharacteristicNotification(mtemp2, true);
                    mtemp2.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeCharacteristic(mtemp2);
                    return;
                }
                SemWifiApSmartUtil unused8 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                if (SemWifiApSmartUtil.CHARACTERISTIC_AUTH_STATUS.equals(characteristic.getUuid())) {
                    byte[] mbytes3 = characteristic.getValue();
                    if (SemWifiApSmartGattClient.this.mAESKey != null) {
                        String str7 = SemWifiApSmartGattClient.this.TAG;
                        Log.d(str7, "Using AES:" + SemWifiApSmartGattClient.this.mAESKey);
                        if (mbytes3 != null) {
                            mbytes3 = AES.decrypt(new String(mbytes3), SemWifiApSmartGattClient.this.mAESKey).getBytes();
                        }
                        if (mbytes3 == null) {
                            Log.e(SemWifiApSmartGattClient.this.TAG, " decryption can't be null");
                        }
                    }
                    if (mbytes3 == null || mbytes3.length <= 0 || mbytes3[0] != 1) {
                        SemWifiApSmartGattClient.this.requestedToEnableMHS = false;
                        Log.d(SemWifiApSmartGattClient.this.TAG, "Auth failed");
                        LocalLog localLog5 = SemWifiApSmartGattClient.this.mLocalLog;
                        localLog5.log(SemWifiApSmartGattClient.this.TAG + ":\tAuth failed");
                        SemWifiApSmartGattClient semWifiApSmartGattClient3 = SemWifiApSmartGattClient.this;
                        semWifiApSmartGattClient3.setConnectionState(-3, semWifiApSmartGattClient3.mSmartAp_WiFi_MAC);
                        if (SemWifiApSmartGattClient.this.mBleWorkHandler != null) {
                            SemWifiApSmartGattClient.this.mBleWorkHandler.sendEmptyMessage(12);
                        }
                        int i = SemWifiApSmartGattClient.this.mUserType;
                        SemWifiApSmartUtil unused9 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                        if (i == 2) {
                            Log.d(SemWifiApSmartGattClient.this.TAG, "Auth failed for family user");
                            LocalLog localLog6 = SemWifiApSmartGattClient.this.mLocalLog;
                            localLog6.log(SemWifiApSmartGattClient.this.TAG + ":\tAuth failed for family user");
                        }
                        if (SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getSamsungAccountCount() == 0) {
                            Log.d(SemWifiApSmartGattClient.this.TAG, "Allowed type, auth failed, so removing wifi mac");
                            LocalLog localLog7 = SemWifiApSmartGattClient.this.mLocalLog;
                            localLog7.log(SemWifiApSmartGattClient.this.TAG + ":\tAllowed type, auth failed, so removing wifi mac");
                            SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.removeWifiMACFromRegisteredList(new String(mbytes3, 1, 8));
                            String mD2DWifiAMC = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getD2DWifiMac();
                            if (mD2DWifiAMC == null || mD2DWifiAMC.isEmpty()) {
                                SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.putHashbasedonD2DFamilyid(-1);
                                SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.putD2DFamilyID(null);
                                Log.d(SemWifiApSmartGattClient.this.TAG, "Allowed type, no registered D2D MHS found, so removed D2DfamilyID");
                                LocalLog localLog8 = SemWifiApSmartGattClient.this.mLocalLog;
                                localLog8.log(SemWifiApSmartGattClient.this.TAG + ":\tAllowed type, no registered D2D MHS found, so removed D2DfamilyID");
                                Intent tintent = new Intent();
                                tintent.setAction("com.samsung.android.server.wifi.softap.smarttethering.d2dfamilyid");
                                SemWifiApSmartGattClient.this.mContext.sendBroadcast(tintent);
                                return;
                            }
                            return;
                        }
                        return;
                    }
                    String str8 = SemWifiApSmartGattClient.this.TAG;
                    Log.d(str8, "Got Auth status:" + ((int) mbytes3[0]));
                    LocalLog localLog9 = SemWifiApSmartGattClient.this.mLocalLog;
                    localLog9.log(SemWifiApSmartGattClient.this.TAG + ":\tGot Auth status:" + ((int) mbytes3[0]));
                    SemWifiApSmartGattClient.this.requestedToEnableMHS = true;
                    byte b = mbytes3[1];
                    SemWifiApSmartGattClient.this.mSSID = new String(mbytes3, 2, (int) b);
                    byte b2 = mbytes3[b + 2];
                    SemWifiApSmartGattClient.this.mPassword = null;
                    if (b2 != 0) {
                        SemWifiApSmartGattClient.this.mPassword = new String(mbytes3, b + 2 + 1, (int) b2);
                    }
                    byte b3 = mbytes3[b + 3 + b2];
                    SemWifiApSmartGattClient.this.mWPA3Mode = mbytes3[b + 3 + b2 + 1 + 8];
                    String str9 = SemWifiApSmartGattClient.this.TAG;
                    Log.d(str9, "mSSID:" + SemWifiApSmartGattClient.this.mSSID + "mhs_status:" + ((int) b3) + ",mWPA3Mode:" + SemWifiApSmartGattClient.this.mWPA3Mode + ",mSSIDlength:" + ((int) b) + ",mPasswordLength:" + ((int) b2) + ",mbytes.length:" + mbytes3.length);
                    String str10 = SemWifiApSmartGattClient.this.TAG;
                    StringBuilder sb = new StringBuilder();
                    sb.append("bytes:");
                    sb.append(Arrays.toString(mbytes3));
                    Log.d(str10, sb.toString());
                    LocalLog localLog10 = SemWifiApSmartGattClient.this.mLocalLog;
                    localLog10.log(SemWifiApSmartGattClient.this.TAG + ":\tmSSID:" + SemWifiApSmartGattClient.this.mSSID + "mhs_status:" + ((int) b3) + ",mWPA3Mode:" + SemWifiApSmartGattClient.this.mWPA3Mode + ",mSSIDlength:" + ((int) b) + ",mPasswordLength:" + ((int) b2) + ",mbytes.length:" + mbytes3.length);
                    if (b3 == 1) {
                        BluetoothGattService bluetoothGattService4 = SemWifiApSmartGattClient.this.mBluetoothGattService;
                        SemWifiApSmartUtil unused10 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                        gatt.readCharacteristic(bluetoothGattService4.getCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_MHS_STATUS_UUID));
                        return;
                    }
                    return;
                }
                SemWifiApSmartUtil unused11 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                if (SemWifiApSmartUtil.CHARACTERISTIC_MHS_STATUS_UUID.equals(characteristic.getUuid())) {
                    byte[] mStatus = characteristic.getValue();
                    if (mStatus != null) {
                        LocalLog localLog11 = SemWifiApSmartGattClient.this.mLocalLog;
                        localLog11.log(SemWifiApSmartGattClient.this.TAG + ":\tGot MHS status:" + Arrays.toString(mStatus));
                        String str11 = SemWifiApSmartGattClient.this.TAG;
                        Log.d(str11, "Got MHS status:" + Arrays.toString(mStatus));
                    } else if (SemWifiApSmartGattClient.this.mhs_read_status_retry > 0) {
                        LocalLog localLog12 = SemWifiApSmartGattClient.this.mLocalLog;
                        localLog12.log(SemWifiApSmartGattClient.this.TAG + ":\tmhs_read_status_retry");
                        Log.d(SemWifiApSmartGattClient.this.TAG, "mhs_read_status_retry");
                        SemWifiApSmartGattClient.access$3510(SemWifiApSmartGattClient.this);
                        BluetoothGattService bluetoothGattService5 = SemWifiApSmartGattClient.this.mBluetoothGattService;
                        SemWifiApSmartUtil unused12 = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                        gatt.readCharacteristic(bluetoothGattService5.getCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_MHS_STATUS_UUID));
                        return;
                    }
                    if (mStatus == null || mStatus[0] != 1) {
                        SemWifiApSmartGattClient semWifiApSmartGattClient4 = SemWifiApSmartGattClient.this;
                        semWifiApSmartGattClient4.setConnectionState(SemWifiApSmartGattClient.ST_MHS_ENABLING_FAILURE, semWifiApSmartGattClient4.mSmartAp_WiFi_MAC);
                    } else {
                        if (mStatus.length > 2) {
                            byte b4 = mStatus[1];
                            String channelStr = new String(mStatus, 2, (int) b4);
                            if (channelStr.isEmpty()) {
                                Log.d(SemWifiApSmartGattClient.this.TAG, "Got MHS channel: 0");
                            } else {
                                try {
                                    SemWifiApSmartGattClient.this.mMhsFreq = SemWifiApSmartGattClient.this.channelToFreq(Integer.parseInt(channelStr));
                                    String str12 = SemWifiApSmartGattClient.this.TAG;
                                    Log.d(str12, "Got MHS channel:" + SemWifiApSmartGattClient.this.mMhsFreq);
                                } catch (NumberFormatException e) {
                                    e.printStackTrace();
                                }
                            }
                            byte b5 = mStatus[b4 + 2];
                            byte b6 = mStatus[b4 + 3 + b5];
                            String clientmac = new String(mStatus, b4 + 2 + 1, (int) b5);
                            String mhsmac = new String(mStatus, b4 + 4 + b5, (int) b6);
                            String str13 = SemWifiApSmartGattClient.this.TAG;
                            Log.d(str13, "Got MHS channel:" + SemWifiApSmartGattClient.this.mMhsFreq + ",clientmac:" + clientmac + ",mhsmac:" + mhsmac);
                            LocalLog localLog13 = SemWifiApSmartGattClient.this.mLocalLog;
                            localLog13.log(SemWifiApSmartGattClient.this.TAG + "\tGot MHS channel:" + SemWifiApSmartGattClient.this.mMhsFreq + ",clientmac:" + clientmac + ",mhsmac:" + mhsmac);
                            synchronized (SemWifiApSmartGattClient.this.mSmartMHSList) {
                                if (!clientmac.equals("") && !mhsmac.equals("")) {
                                    Iterator<SmartMHSInfo> it = SemWifiApSmartGattClient.this.mSmartMHSList.iterator();
                                    while (true) {
                                        if (!it.hasNext()) {
                                            break;
                                        }
                                        SmartMHSInfo inf = it.next();
                                        if (clientmac.substring(9).toLowerCase().equals(inf.clientMAC)) {
                                            inf.MHS_MAC = mhsmac.toLowerCase();
                                            inf.state = 2;
                                            Log.e(SemWifiApSmartGattClient.this.TAG, "updated MHS MAC in Smart MHS list");
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        if (SemWifiApSmartGattClient.this.mBleWorkHandler != null) {
                            SemWifiApSmartGattClient.this.mBleWorkHandler.sendEmptyMessage(11);
                        }
                    }
                    if (SemWifiApSmartGattClient.this.mBleWorkHandler != null) {
                        SemWifiApSmartGattClient.this.mBleWorkHandler.sendEmptyMessage(12);
                    }
                }
            }
        }
    };
    private LocalLog mLocalLog;
    private int mMhsFreq = -1;
    private NotificationManager mNotificationManager;
    private String mPassword;
    private String mPendingDeviceAddress;
    private String mSSID;
    private WifiManager.SemWifiApSmartCallback mSemWifiApSmartCallbackImpl;
    private SemWifiApSmartClient mSemWifiApSmartClient;
    private SemWifiApSmartGattClientReceiver mSemWifiApSmartGattClientReceiver;
    private SemWifiApSmartUtil mSemWifiApSmartUtil;
    private String mSmartAp_BLE_MAC;
    private String mSmartAp_WiFi_MAC;
    List<SmartMHSInfo> mSmartMHSList = new ArrayList();
    private boolean mTimeMismatchOccured = false;
    private String mUserName;
    private int mUserType;
    private int mWPA3Mode = 0;
    private PowerManager.WakeLock mWakeLock;
    private int mhideSSID;
    private int mhs_read_status_retry = 2;
    private int mversion;
    private boolean mwaitingToConnect = false;
    private boolean requestedToEnableMHS;
    private WifiManager.SemWifiApSmartCallback tSemWifiApSmartCallback;
    private int tryingToRetry = -1;

    static /* synthetic */ int access$2110(SemWifiApSmartGattClient x0) {
        int i = x0.tryingToRetry;
        x0.tryingToRetry = i - 1;
        return i;
    }

    static /* synthetic */ int access$3510(SemWifiApSmartGattClient x0) {
        int i = x0.mhs_read_status_retry;
        x0.mhs_read_status_retry = i - 1;
        return i;
    }

    static {
        mSemWifiApSmartGattClientIntentFilter.addAction("android.bluetooth.device.action.BOND_STATE_CHANGED");
        mSemWifiApSmartGattClientIntentFilter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        mSemWifiApSmartGattClientIntentFilter.addAction("android.bluetooth.device.action.PAIRING_REQUEST");
        mSemWifiApSmartGattClientIntentFilter.addAction("android.net.wifi.STATE_CHANGE");
        mSemWifiApSmartGattClientIntentFilter.addAction(ACTION_LOGOUT_ACCOUNTS_COMPLETE);
        mSemWifiApSmartGattClientIntentFilter.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
        mSemWifiApSmartGattClientIntentFilter.setPriority(ReportIdKey.ID_PATTERN_MATCHED);
    }

    public SemWifiApSmartGattClient(Context context, SemWifiApSmartUtil semWifiApSmartUtil, LocalLog tLocalLog) {
        this.mContext = context;
        this.mSemWifiApSmartUtil = semWifiApSmartUtil;
        this.mLocalLog = tLocalLog;
        this.mSemWifiApSmartClient = WifiInjector.getInstance().getSemWifiApSmartClient();
        this.mSemWifiApSmartGattClientReceiver = new SemWifiApSmartGattClientReceiver();
        if (!FactoryTest.isFactoryBinary()) {
            this.mContext.registerReceiver(this.mSemWifiApSmartGattClientReceiver, mSemWifiApSmartGattClientIntentFilter);
        } else {
            Log.e(this.TAG, "This devices's binary is a factory binary");
        }
    }

    public void registerSemWifiApSmartCallback(WifiManager.SemWifiApSmartCallback callback) {
        this.tSemWifiApSmartCallback = callback;
    }

    public void handleBootCompleted() {
        Log.d(this.TAG, "handleBootCompleted");
        this.mBleWorkThread = new HandlerThread("SemWifiApSmartGattClientBleHandler");
        this.mBleWorkThread.start();
        this.mBleWorkHandler = new BleWorkHandler(this.mBleWorkThread.getLooper());
        String tlist = Settings.Secure.getString(this.mContext.getContentResolver(), "bonded_device_clientside");
        if (tlist != null) {
            for (String str : tlist.split("\n")) {
                this.bonedDevicesFromHotspotLive.add(str);
            }
            this.mLocalLog.log(this.TAG + ":\tbonded_device_clientside,booting time :" + tlist);
        }
    }

    class SemWifiApSmartGattClientReceiver extends BroadcastReceiver {
        SemWifiApSmartGattClientReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            int i;
            String action = intent.getAction();
            boolean z = false;
            if (action.equals(SemWifiApSmartGattClient.ACTION_LOGOUT_ACCOUNTS_COMPLETE)) {
                SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tLOGOUT_COMPLETE");
                Log.d(SemWifiApSmartGattClient.this.TAG, "shutdownClient due to LOGOUT /");
                SemWifiApSmartGattClient.this.mwaitingToConnect = false;
                SemWifiApSmartGattClient.this.mMhsFreq = -1;
                if (SemWifiApSmartGattClient.this.mBleWorkHandler != null) {
                    SemWifiApSmartGattClient.this.mBleWorkHandler.sendEmptyMessage(12);
                }
                Iterator<String> tempIt = SemWifiApSmartGattClient.this.bonedDevicesFromHotspotLive.iterator();
                while (tempIt != null && tempIt.hasNext()) {
                    BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(tempIt.next());
                    if (device != null && device.getBondState() == 12) {
                        device.removeBond();
                        Log.d(SemWifiApSmartGattClient.this.TAG, ":smarttethering.removeBond :" + device.getAddress());
                        SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tsmarttethering.removeBond :" + device.getAddress());
                    }
                }
                SemWifiApSmartGattClient.this.bonedDevicesFromHotspotLive.clear();
                Settings.Secure.putString(SemWifiApSmartGattClient.this.mContext.getContentResolver(), "bonded_device_clientside", null);
                return;
            }
            int i2 = 8;
            int i3 = 1;
            if (action.equals("android.bluetooth.device.action.PAIRING_REQUEST")) {
                BluetoothDevice device2 = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
                int mType = intent.getIntExtra("android.bluetooth.device.extra.PAIRING_VARIANT", Integer.MIN_VALUE);
                String.format(Locale.US, "%06d", Integer.valueOf(intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY", Integer.MIN_VALUE)));
                Log.d(SemWifiApSmartGattClient.this.TAG, "mType: " + mType + " ,device: " + device2 + " ,mPendingDeviceAddress: " + SemWifiApSmartGattClient.this.mPendingDeviceAddress);
                SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tmType: " + mType + " ,device: " + device2 + " ,mPendingDeviceAddress: " + SemWifiApSmartGattClient.this.mPendingDeviceAddress);
                if (SemWifiApSmartGattClient.this.mPendingDeviceAddress != null && device2.getAddress().equals(SemWifiApSmartGattClient.this.mPendingDeviceAddress) && mType == 2) {
                    Intent tintent = new Intent();
                    tintent.setAction("com.samsung.android.server.wifi.softap.smarttethering.collapseQuickPanel");
                    SemWifiApSmartGattClient.this.mContext.sendBroadcast(tintent);
                    abortBroadcast();
                    intent.setClassName("com.android.settings", "com.samsung.android.settings.wifi.mobileap.WifiApWarning");
                    intent.setFlags(268435456);
                    intent.setAction(SemWifiApBroadcastReceiver.WIFIAP_WARNING_DIALOG);
                    intent.putExtra(SemWifiApBroadcastReceiver.WIFIAP_WARNING_DIALOG_TYPE, 8);
                    SemWifiApSmartGattClient.this.mContext.startActivity(intent);
                    Log.d(SemWifiApSmartGattClient.this.TAG, "passkeyconfirm dialog");
                }
            } else if (intent.getAction().equals("android.bluetooth.device.action.BOND_STATE_CHANGED")) {
                BluetoothDevice device3 = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
                switch (intent.getIntExtra("android.bluetooth.device.extra.BOND_STATE", 10)) {
                    case 10:
                        SemWifiApSmartGattClient.this.isBondingGoingon = false;
                        if (SemWifiApSmartGattClient.this.mPendingDeviceAddress != null && device3.getAddress().equals(SemWifiApSmartGattClient.this.mPendingDeviceAddress)) {
                            SemWifiApSmartGattClient.this.mPendingDeviceAddress = null;
                            Log.d(SemWifiApSmartGattClient.this.TAG, " client Bonding is failed");
                            SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tclient Bonding is failed");
                            new Handler().postDelayed(new Runnable() {
                                /* class com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartGattClient.SemWifiApSmartGattClientReceiver.RunnableC08121 */

                                public void run() {
                                    SemWifiApSmartGattClient.this.setConnectionState(-2, SemWifiApSmartGattClient.this.mSmartAp_WiFi_MAC);
                                    if (SemWifiApSmartGattClient.this.mBleWorkHandler != null) {
                                        SemWifiApSmartGattClient.this.mBleWorkHandler.sendEmptyMessage(12);
                                    }
                                }
                            }, 6000);
                            return;
                        }
                        return;
                    case 11:
                        if (SemWifiApSmartGattClient.this.mPendingDeviceAddress != null && device3.getAddress().equals(SemWifiApSmartGattClient.this.mPendingDeviceAddress)) {
                            SemWifiApSmartGattClient.this.isBondingGoingon = true;
                            Log.d(SemWifiApSmartGattClient.this.TAG, " client Bonding is going on");
                            return;
                        }
                        return;
                    case 12:
                        Log.d(SemWifiApSmartGattClient.this.TAG, "client Bonding is done");
                        SemWifiApSmartGattClient.this.isBondingGoingon = false;
                        if (device3 != null && SemWifiApSmartGattClient.this.mPendingDeviceAddress != null && device3.getAddress().equals(SemWifiApSmartGattClient.this.mPendingDeviceAddress)) {
                            SemWifiApSmartGattClient.this.mPendingDeviceAddress = null;
                            SemWifiApSmartGattClient.this.bonedDevicesFromHotspotLive.add(device3.getAddress());
                            String tpString = "";
                            Iterator it = SemWifiApSmartGattClient.this.bonedDevicesFromHotspotLive.iterator();
                            while (it.hasNext()) {
                                tpString = tpString + ((String) it.next()) + "\n";
                            }
                            Settings.Secure.putString(SemWifiApSmartGattClient.this.mContext.getContentResolver(), "bonded_device_clientside", tpString);
                            SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\t\tAdding to bondedd devices :" + device3.getAddress());
                            Log.d(SemWifiApSmartGattClient.this.TAG, ":Adding to bondedd devices :" + tpString);
                            if (SemWifiApSmartGattClient.this.mTimeMismatchOccured) {
                                Log.d(SemWifiApSmartGattClient.this.TAG, "mTimeMismatchOccured is true after bonding");
                                SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\t mTimeMismatchOccured is true after bonding :");
                                SemWifiApSmartGattClient.this.mTimeMismatchOccured = false;
                                if (SemWifiApSmartGattClient.this.mConnectedGatt != null) {
                                    BluetoothGatt bluetoothGatt = SemWifiApSmartGattClient.this.mConnectedGatt;
                                    BluetoothGattService bluetoothGattService = SemWifiApSmartGattClient.this.mBluetoothGattService;
                                    SemWifiApSmartUtil unused = SemWifiApSmartGattClient.this.mSemWifiApSmartUtil;
                                    bluetoothGatt.readCharacteristic(bluetoothGattService.getCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_MHS_BOND_STATUS));
                                    return;
                                }
                                Log.d(SemWifiApSmartGattClient.this.TAG, "mTimeMismatchOccured is true after bonding, but gattconnection is null");
                                SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\t mTimeMismatchOccured is true after bonding, but gattconnection is null");
                                SemWifiApSmartGattClient.this.shutdownclient();
                                return;
                            } else if (!SemWifiApSmartGattClient.this.tryToConnectToRemoteBLE(device3, false)) {
                                SemWifiApSmartGattClient semWifiApSmartGattClient = SemWifiApSmartGattClient.this;
                                semWifiApSmartGattClient.setConnectionState(SemWifiApSmartGattClient.ST_GATT_FAILURE, semWifiApSmartGattClient.mSmartAp_WiFi_MAC);
                                SemWifiApSmartGattClient.this.mFailedBLEConnections.remove(device3.getAddress());
                                return;
                            } else {
                                return;
                            }
                        } else {
                            return;
                        }
                    default:
                        return;
                }
            } else if (action.equals("android.net.wifi.WIFI_STATE_CHANGED")) {
                int mWifiState = intent.getIntExtra("wifi_state", 4);
                if (mWifiState == 1 || mWifiState == 4) {
                    Log.d(SemWifiApSmartGattClient.this.TAG, "shutdownClient due to Wi-FI is OFF/");
                    SemWifiApContentProviderHelper.insert(SemWifiApSmartGattClient.this.mContext, "smart_tethering_GattClient_username", (String) null);
                    SemWifiApSmartGattClient.this.mwaitingToConnect = false;
                    SemWifiApSmartGattClient.this.mMhsFreq = -1;
                    if (SemWifiApSmartGattClient.this.mBleWorkHandler != null) {
                        SemWifiApSmartGattClient.this.mBleWorkHandler.sendEmptyMessage(12);
                    }
                    synchronized (SemWifiApSmartGattClient.this.mSmartMHSList) {
                        SemWifiApSmartGattClient.this.mSmartMHSList.clear();
                    }
                }
            } else if (!action.equals("android.net.wifi.SCAN_RESULTS")) {
                boolean isDisconnected = true;
                if (!action.equals("android.bluetooth.adapter.action.STATE_CHANGED") || !SemWifiApSmartGattClient.this.mBluetoothIsOn) {
                    if (action.equals("android.net.wifi.STATE_CHANGE")) {
                        WifiManager wifiManager = (WifiManager) SemWifiApSmartGattClient.this.mContext.getSystemService("wifi");
                        Settings.Secure.getInt(SemWifiApSmartGattClient.this.mContext.getContentResolver(), "wifi_client_smart_tethering_settings", 0);
                        NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra("networkInfo");
                        boolean isConnected = networkInfo != null && networkInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED;
                        if (networkInfo == null || networkInfo.getDetailedState() != NetworkInfo.DetailedState.DISCONNECTED) {
                            isDisconnected = false;
                        }
                        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                        String mBSSID = wifiInfo == null ? null : wifiInfo.getBSSID();
                        if (isConnected) {
                            if (wifiInfo.getSSID().equals("\"" + SemWifiApSmartGattClient.this.mSSID + "\"")) {
                                synchronized (SemWifiApSmartGattClient.this.mSmartMHSList) {
                                    Iterator<SmartMHSInfo> it2 = SemWifiApSmartGattClient.this.mSmartMHSList.iterator();
                                    while (true) {
                                        if (!it2.hasNext()) {
                                            break;
                                        }
                                        SmartMHSInfo inf = it2.next();
                                        if (mBSSID != null && inf.state == 2 && inf.MHS_MAC != null && inf.MHS_MAC.equalsIgnoreCase(mBSSID)) {
                                            inf.state = 3;
                                            SemWifiApSmartGattClient.this.invokeCallback(inf.clientMAC, 3);
                                            Log.e(SemWifiApSmartGattClient.this.TAG, "updated status to WIFI connected in the SmartMHSlist");
                                            SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tupdated status to WIFI connected in the SmartMHSlist");
                                            SemWifiApSmartGattClient.this.showSmartMHSInfo();
                                            break;
                                        }
                                    }
                                }
                                Log.d(SemWifiApSmartGattClient.this.TAG, "NETWORK_STATE_CHANGED_ACTION isConnected:" + isConnected + ", mSSID:" + SemWifiApSmartGattClient.this.mSSID + ", startPartialScan 1,6,11,149 one more time to update wifi list quickly");
                                SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tNETWORK_STATE_CHANGED_ACTION isConnected:" + isConnected + ", mSSID:" + SemWifiApSmartGattClient.this.mSSID + ", startPartialScan 1,6,11,149 one more time to update wifi list quickly");
                                int[] freqs = {2412, 2437, 2462, 5745};
                                if (!wifiManager.semStartPartialChannelScan(freqs)) {
                                    wifiManager.semStartPartialChannelScan(freqs);
                                }
                            }
                        } else if (isDisconnected) {
                            Log.d(SemWifiApSmartGattClient.this.TAG, "isDisconnected: true");
                            synchronized (SemWifiApSmartGattClient.this.mSmartMHSList) {
                                Iterator<SmartMHSInfo> it3 = SemWifiApSmartGattClient.this.mSmartMHSList.iterator();
                                while (true) {
                                    if (!it3.hasNext()) {
                                        break;
                                    }
                                    SmartMHSInfo inf2 = it3.next();
                                    if (inf2.state == 3) {
                                        inf2.state = 0;
                                        SemWifiApSmartGattClient.this.invokeCallback(inf2.clientMAC, 0);
                                        Log.e(SemWifiApSmartGattClient.this.TAG, "updated status to WIFI disconnected in the SmartMHSlist:" + inf2.MHS_MAC.substring(9));
                                        SemWifiApContentProviderHelper.insert(SemWifiApSmartGattClient.this.mContext, "smart_tethering_GattClient_username", (String) null);
                                        SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tupdated status to WIFI disconnected in the SmartMHSlist:" + inf2.MHS_MAC.substring(9));
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } else if (SemWifiApSmartGattClient.this.mBluetoothDevice != null && BluetoothAdapter.getDefaultAdapter().getState() == 12) {
                    Log.d(SemWifiApSmartGattClient.this.TAG, "ACTION_STATE_CHANGED mBluetoothIsOn " + SemWifiApSmartGattClient.this.mBluetoothIsOn);
                    SemWifiApSmartGattClient semWifiApSmartGattClient2 = SemWifiApSmartGattClient.this;
                    semWifiApSmartGattClient2.mPendingDeviceAddress = semWifiApSmartGattClient2.mBluetoothDevice.getAddress();
                    SemWifiApSmartGattClient.this.mBluetoothDevice.createBond(2);
                    SemWifiApSmartGattClient.this.mBluetoothIsOn = false;
                }
            } else if (SemWifiApSmartGattClient.this.mwaitingToConnect) {
                boolean scanUpdated = intent.getBooleanExtra("resultsUpdated", true);
                WifiManager wifiManager2 = (WifiManager) SemWifiApSmartGattClient.this.mContext.getSystemService("wifi");
                List<ScanResult> results = wifiManager2.getScanResults();
                Log.d(SemWifiApSmartGattClient.this.TAG, "SCAN_RESULTS_AVAILABLE_ACTION received, mwaitingToConnect:" + SemWifiApSmartGattClient.this.mwaitingToConnect + ", scanUpdated:" + scanUpdated);
                boolean canConnectNow = true;
                if ("JP".equals(SystemProperties.get("ro.csc.country_code")) && SemWifiApSmartGattClient.this.mDelayStartFrom > 0) {
                    long tGap = System.currentTimeMillis() - SemWifiApSmartGattClient.this.mDelayStartFrom;
                    Log.d(SemWifiApSmartGattClient.this.TAG, "SCAN_RESULTS_AVAILABLE_ACTION received, tGap:" + tGap + " for Japan");
                    if (tGap > RttServiceImpl.HAL_RANGING_TIMEOUT_MS) {
                        SemWifiApSmartGattClient.this.mDelayStartFrom = -1;
                        Log.d(SemWifiApSmartGattClient.this.TAG, "SCAN_RESULTS_AVAILABLE_ACTION received, keep going connection process for Japan");
                    } else {
                        canConnectNow = false;
                        Log.d(SemWifiApSmartGattClient.this.TAG, "SCAN_RESULTS_AVAILABLE_ACTION received, skip this scan results and wait more for Japan");
                    }
                }
                if (results != null && results.size() > 0 && canConnectNow) {
                    Log.d(SemWifiApSmartGattClient.this.TAG, "SCAN_RESULTS_AVAILABLE_ACTION received, results.size():" + results.size());
                    WifiInfo wifiInfo2 = wifiManager2.getConnectionInfo();
                    if (wifiInfo2 != null) {
                        if (wifiInfo2.getSSID().equals("\"" + SemWifiApSmartGattClient.this.mSSID + "\"")) {
                        }
                    }
                    Iterator<ScanResult> it4 = results.iterator();
                    while (true) {
                        if (!it4.hasNext()) {
                            break;
                        } else if (it4.next().SSID.equals(SemWifiApSmartGattClient.this.mSSID)) {
                            SemWifiApSmartGattClient.this.mwaitingToConnect = z;
                            SemWifiApSmartGattClient.this.mMhsFreq = -1;
                            List<WifiConfiguration> list = wifiManager2.getConfiguredNetworks();
                            Iterator<WifiConfiguration> it5 = list.iterator();
                            while (true) {
                                if (!it5.hasNext()) {
                                    break;
                                }
                                WifiConfiguration i4 = it5.next();
                                boolean wpa2_isSecure = i4.allowedKeyManagement.get(i3);
                                boolean wpa3_isSecure = i4.allowedKeyManagement.get(i2);
                                boolean isOpen = i4.allowedKeyManagement.get(0);
                                Log.d(SemWifiApSmartGattClient.this.TAG, "isOpen" + isOpen + ",wpa2_isSecure=" + wpa2_isSecure + ",wpa3_isSecure=" + wpa3_isSecure);
                                if ((SemWifiApSmartGattClient.this.mPassword != null || !isOpen) && (SemWifiApSmartGattClient.this.mPassword == null || !wpa2_isSecure || SemWifiApSmartGattClient.this.mWPA3Mode != 0)) {
                                    if (SemWifiApSmartGattClient.this.mPassword == null || !wpa3_isSecure) {
                                        i = 1;
                                        i3 = i;
                                        scanUpdated = scanUpdated;
                                        list = list;
                                        results = results;
                                        i2 = 8;
                                    } else if (SemWifiApSmartGattClient.this.mWPA3Mode != 1) {
                                        i = 1;
                                        i3 = i;
                                        scanUpdated = scanUpdated;
                                        list = list;
                                        results = results;
                                        i2 = 8;
                                    }
                                }
                                if (i4.SSID != null) {
                                    if (i4.SSID.equals("\"" + SemWifiApSmartGattClient.this.mSSID + "\"")) {
                                        wifiManager2.disconnect();
                                        Log.d(SemWifiApSmartGattClient.this.TAG, "Scan resullts Connecting to MHS:" + SemWifiApSmartGattClient.this.mSSID + ",i.networkId:" + i4.networkId);
                                        SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tScan resullts Connecting to MHS:" + SemWifiApSmartGattClient.this.mSSID + ",i.networkId:" + i4.networkId);
                                        wifiManager2.enableNetwork(i4.networkId, true);
                                        Log.d(SemWifiApSmartGattClient.this.TAG, "reconnect");
                                        wifiManager2.reconnect();
                                        break;
                                    }
                                }
                                i = 1;
                                i3 = i;
                                scanUpdated = scanUpdated;
                                list = list;
                                results = results;
                                i2 = 8;
                            }
                        } else {
                            results = results;
                            z = false;
                            i2 = 8;
                        }
                    }
                }
                if (SemWifiApSmartGattClient.this.mwaitingToConnect && SemWifiApSmartGattClient.this.tryingToRetry != 0) {
                    Log.d(SemWifiApSmartGattClient.this.TAG, "SCAN_RESULTS_AVAILABLE_ACTION doesn't have " + SemWifiApSmartGattClient.this.mSSID + " tryingToRetry : " + SemWifiApSmartGattClient.this.tryingToRetry);
                    SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tSCAN_RESULTS_AVAILABLE_ACTION doesn't have " + SemWifiApSmartGattClient.this.mSSID + ",starting partial scan tryingToRetry : " + SemWifiApSmartGattClient.this.tryingToRetry);
                    SemWifiApSmartGattClient.this.startPartialScanAfterSleep(50);
                    SemWifiApSmartGattClient.access$2110(SemWifiApSmartGattClient.this);
                }
            }
        }
    }

    public int getSmartApConnectedStatus(String mBSSID) {
        if (mBSSID == null) {
            return 0;
        }
        synchronized (this.mSmartMHSList) {
            for (SmartMHSInfo inf : this.mSmartMHSList) {
                if (inf.MHS_MAC != null && inf.MHS_MAC.equalsIgnoreCase(mBSSID)) {
                    String str = this.TAG;
                    Log.d(str, "getSmartApConnectedStatus mhs_mac " + mBSSID + ",::" + inf.state);
                    return inf.state;
                }
            }
            return 0;
        }
    }

    public int getSmartApConnectedStatusFromScanResult(String mClientMAC) {
        if (mClientMAC == null) {
            return 0;
        }
        synchronized (this.mSmartMHSList) {
            for (SmartMHSInfo inf : this.mSmartMHSList) {
                if (inf.clientMAC != null && inf.clientMAC.equalsIgnoreCase(mClientMAC)) {
                    String str = this.TAG;
                    Log.d(str, "getSmartApConnectedStatusFromScanResult client MAC:" + mClientMAC + ":" + inf.state);
                    return inf.state;
                }
            }
            return 0;
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:11:0x0035  */
    public boolean connectToSmartMHS(String address, int mUserType2, int mHidden, int mSecurity, String mSmartAp_WiFi_MAC2, String userName, int mversion2) {
        BluetoothDevice device;
        BluetoothDevice device2;
        if (this.mConnectedGatt != null) {
            Log.e(this.TAG, "mConnectedGatt is not null");
            return false;
        } else if (this.isBondingGoingon) {
            Log.e(this.TAG, "isBondingGoingon is true");
            return false;
        } else {
            for (SmartMHSInfo var : this.mSmartMHSList) {
                if (var.state == 1 || var.state == 2) {
                    Log.e(this.TAG, "Gatt connecting is going on, return:" + var.state);
                    return false;
                }
                while (r0.hasNext()) {
                }
            }
            this.mhs_read_status_retry = 2;
            this.mUserType = mUserType2;
            this.mhideSSID = mHidden;
            this.mUserName = userName;
            this.mversion = mversion2;
            Log.d(this.TAG, "connectToSmartMHS   mversion:" + mversion2 + ",address:" + address + ",mUserType:" + mUserType2 + ",mHidden:" + mHidden + ",mSecurity:" + mSecurity + ",mSmartAp_WiFi_MAC:" + mSmartAp_WiFi_MAC2);
            this.mLocalLog.log(this.TAG + ":\tconnectToSmartMHS   mversion:" + mversion2 + ",address:" + address + ",mUserType:" + mUserType2 + ",mHidden:" + mHidden + ",mSecurity:" + mSecurity + ",mSmartAp_WiFi_MAC:" + mSmartAp_WiFi_MAC2);
            this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothAdapter bluetoothAdapter = this.mBluetoothAdapter;
            if (bluetoothAdapter == null || address == null) {
                Log.e(this.TAG, "BluetoothAdapter not initialized or unspecified address.");
                return false;
            }
            device = bluetoothAdapter.getRemoteDevice(address);
            if (device == null) {
                Log.e(this.TAG, "Device not found. Unable to connect.");
                return false;
            }
            this.mSmartAp_WiFi_MAC = mSmartAp_WiFi_MAC2;
            this.mSmartAp_BLE_MAC = address;
            synchronized (this.mSmartMHSList) {
                try {
                    List<Integer> mIndexeList = new ArrayList<>();
                    int count = 0;
                    for (SmartMHSInfo var2 : this.mSmartMHSList) {
                        try {
                            if (var2.clientMAC.equals(mSmartAp_WiFi_MAC2) && var2.state != 3) {
                                mIndexeList.add(Integer.valueOf(count));
                            } else if (var2.state != 3) {
                                mIndexeList.add(Integer.valueOf(count));
                                this.mSemWifiApSmartClient.removeFromScanResults(2, var2.clientMAC);
                            }
                            count++;
                        } catch (Throwable th) {
                            th = th;
                            while (true) {
                                try {
                                    break;
                                } catch (Throwable th2) {
                                    th = th2;
                                }
                            }
                            throw th;
                        }
                    }
                    try {
                        for (Integer num : mIndexeList) {
                            this.mSmartMHSList.remove(num.intValue());
                        }
                    } catch (IndexOutOfBoundsException e) {
                    }
                } catch (Throwable th3) {
                    th = th3;
                    while (true) {
                        break;
                    }
                    throw th;
                }
            }
        }
        this.mSmartMHSList.add(new SmartMHSInfo(mSmartAp_WiFi_MAC2, null, System.currentTimeMillis(), 1));
        setConnectionState(1, mSmartAp_WiFi_MAC2);
        BleWorkHandler bleWorkHandler = this.mBleWorkHandler;
        if (bleWorkHandler != null && !bleWorkHandler.hasMessages(18)) {
            this.mBleWorkHandler.sendEmptyMessageDelayed(18, RttServiceImpl.HAL_RANGING_TIMEOUT_MS);
        }
        this.mAESKey = null;
        if (this.mSemWifiApSmartUtil.isEncryptionCanbeUsed(mversion2, mUserType2)) {
            this.mAESKey = this.mSemWifiApSmartUtil.getAESKey(System.currentTimeMillis());
        }
        if (mversion2 != 0 && this.mAESKey != null) {
            device2 = device;
        } else if (device.getBondState() == 10) {
            BluetoothAdapter bluetoothAdapter2 = this.mBluetoothAdapter;
            if (bluetoothAdapter2 == null || bluetoothAdapter2.getState() != 10) {
                Log.d(this.TAG, "device is not bonded:" + device.getBondState());
                this.mLocalLog.log(this.TAG + ":\tdevice is not bonded:" + device.getBondState());
                this.mPendingDeviceAddress = device.getAddress();
                device.createBond(2);
            } else {
                Log.d(this.TAG, "device is not bonded, enabling BT adapter,mBluetoothIsOn:" + this.mBluetoothIsOn);
                this.mLocalLog.log(this.TAG + ":\tdevice is not bonded, enabling BT adapter,mBluetoothIsOn:" + this.mBluetoothIsOn);
                this.mBluetoothAdapter.enable();
                this.mBluetoothIsOn = true;
                this.mBluetoothDevice = device;
            }
            return true;
        } else {
            device2 = device;
        }
        if (!tryToConnectToRemoteBLE(device2, false)) {
            setConnectionState(ST_GATT_FAILURE, mSmartAp_WiFi_MAC2);
            this.mFailedBLEConnections.remove(device2.getAddress());
        }
        return true;
    }

    /* access modifiers changed from: package-private */
    public class BleWorkHandler extends Handler {
        public BleWorkHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            Log.e(SemWifiApSmartGattClient.this.TAG, "Got message:" + msg.what);
            int i = msg.what;
            if (i == 11) {
                Log.e(SemWifiApSmartGattClient.this.TAG, "Got message: GENERATE_CONNECT_WIFI");
                SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tGot message: GENERATE_CONNECT_WIFI");
                WifiManager wifiManager = (WifiManager) SemWifiApSmartGattClient.this.mContext.getSystemService("wifi");
                List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
                if (SemWifiApSmartGattClient.this.mWPA3Mode == 1) {
                    Iterator<WifiConfiguration> it = list.iterator();
                    while (true) {
                        if (!it.hasNext()) {
                            break;
                        }
                        WifiConfiguration i2 = it.next();
                        boolean wpa3_isSecure = i2.allowedKeyManagement.get(8);
                        if (i2.SSID != null && wpa3_isSecure && SemWifiApSmartGattClient.this.removeDoubleQuotes(i2.SSID).equals(SemWifiApSmartGattClient.this.mSSID)) {
                            SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tremoving old WPA3");
                            Log.e(SemWifiApSmartGattClient.this.TAG, "removing old WPA3");
                            wifiManager.removeNetwork(i2.networkId);
                            break;
                        }
                    }
                } else if (SemWifiApSmartGattClient.this.mWPA3Mode == 2) {
                    Iterator<WifiConfiguration> it2 = list.iterator();
                    while (true) {
                        if (!it2.hasNext()) {
                            break;
                        }
                        WifiConfiguration i3 = it2.next();
                        if (i3.SSID != null && SemWifiApSmartGattClient.this.removeDoubleQuotes(i3.SSID).equals(SemWifiApSmartGattClient.this.mSSID)) {
                            boolean wpa3_isSecure2 = i3.allowedKeyManagement.get(8);
                            boolean wpa2_isSecure = i3.allowedKeyManagement.get(1);
                            if (wpa3_isSecure2) {
                                SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tremoving old WPA3, in WPA2/3");
                                Log.e(SemWifiApSmartGattClient.this.TAG, "removing old WPA3, in WPA2/3");
                                SemWifiApSmartGattClient.this.mWPA3Mode = 1;
                                wifiManager.removeNetwork(i3.networkId);
                                break;
                            } else if (wpa2_isSecure) {
                                SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\t WPA2, in WPA2/3");
                                Log.e(SemWifiApSmartGattClient.this.TAG, "WPA2, in WPA2/3");
                                SemWifiApSmartGattClient.this.mWPA3Mode = 0;
                                break;
                            }
                        }
                    }
                }
                if (SemWifiApSmartGattClient.this.mWPA3Mode == 2) {
                    SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tnot found any profile with same SSID, WPA2/3");
                    Log.e(SemWifiApSmartGattClient.this.TAG, "not found any profile with same SSID, WPA2/3");
                    SemWifiApSmartGattClient.this.mWPA3Mode = 1;
                }
                WifiConfiguration conf = new WifiConfiguration();
                conf.SSID = "\"" + SemWifiApSmartGattClient.this.mSSID + "\"";
                if (SemWifiApSmartGattClient.this.mPassword != null) {
                    conf.preSharedKey = "\"" + SemWifiApSmartGattClient.this.mPassword + "\"";
                    if (SemWifiApSmartGattClient.this.mWPA3Mode == 1) {
                        Log.d(SemWifiApSmartGattClient.this.TAG, "connect to WPA3 access Point");
                        SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tconnect to WPA3 access Point");
                        conf.allowedKeyManagement.set(8);
                        conf.requirePMF = true;
                    } else {
                        conf.allowedKeyManagement.set(1);
                    }
                } else {
                    conf.allowedKeyManagement.set(0);
                }
                if (SemWifiApSmartGattClient.this.mhideSSID == 1) {
                    conf.hiddenSSID = true;
                }
                conf.semSamsungSpecificFlags.set(1);
                if (SemWifiApSmartGattClient.this.mSemWifiApSmartUtil.getNetworkType() == 1) {
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    Log.d(SemWifiApSmartGattClient.this.TAG, "checking for Same SSID: " + wifiInfo.getSSID() + ",mSSID:\"" + SemWifiApSmartGattClient.this.mSSID + "\"");
                    if (wifiInfo.getSSID().equals("\"" + SemWifiApSmartGattClient.this.mSSID + "\"")) {
                        return;
                    }
                }
                if (SemWifiApSmartGattClient.this.mUserName == null) {
                    SemWifiApSmartGattClient semWifiApSmartGattClient = SemWifiApSmartGattClient.this;
                    semWifiApSmartGattClient.setConnectionState(-9, semWifiApSmartGattClient.mSmartAp_WiFi_MAC);
                    Log.e(SemWifiApSmartGattClient.this.TAG, "connecting to mUserName==null ST_MHS_USERNAME_FAILED");
                    return;
                }
                SemWifiApContentProviderHelper.insert(SemWifiApSmartGattClient.this.mContext, "smart_tethering_GattClient_username", SemWifiApSmartGattClient.this.mUserName);
                SemWifiApSmartGattClient semWifiApSmartGattClient2 = SemWifiApSmartGattClient.this;
                semWifiApSmartGattClient2.setConnectionState(2, semWifiApSmartGattClient2.mSmartAp_WiFi_MAC);
                if (SemWifiApSmartGattClient.this.mhideSSID == 1) {
                    Log.e(SemWifiApSmartGattClient.this.TAG, "connecting to hiddenSSID");
                    SemWifiApSmartGattClient.this.mwaitingToConnect = false;
                    SemWifiApSmartGattClient.this.mMhsFreq = -1;
                    wifiManager.connect(conf, new WifiManager.ActionListener() {
                        /* class com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartGattClient.BleWorkHandler.C08111 */

                        public void onSuccess() {
                            Log.i(SemWifiApSmartGattClient.this.TAG, "onSuccess");
                        }

                        public void onFailure(int reason) {
                            String str = SemWifiApSmartGattClient.this.TAG;
                            Log.i(str, "onFailure : " + reason);
                        }
                    });
                    return;
                }
                SemWifiApSmartGattClient.this.mwaitingToConnect = true;
                SemWifiApSmartGattClient.this.tryingToRetry = 10;
                int netId = wifiManager.addNetwork(conf);
                Log.d(SemWifiApSmartGattClient.this.TAG, "trying to Connect to: " + SemWifiApSmartGattClient.this.mSSID + ",netId:" + netId);
                SemWifiApSmartGattClient.this.mDelayStartFrom = -1;
                if ("JP".equals(SystemProperties.get("ro.csc.country_code"))) {
                    SemWifiApSmartGattClient.this.mDelayStartFrom = System.currentTimeMillis();
                    Log.d(SemWifiApSmartGattClient.this.TAG, "disableNetwork netId:" + netId + ", start delay " + 5000 + " for Japan from " + SemWifiApSmartGattClient.this.mDelayStartFrom);
                    wifiManager.disableNetwork(netId);
                }
                SemWifiApSmartGattClient.this.startPartialScanAfterSleep(0);
            } else if (i == 12) {
                SemWifiApSmartGattClient.this.shutdownclient();
            } else if (i == 14) {
                WifiManager twifiManager = (WifiManager) SemWifiApSmartGattClient.this.mContext.getSystemService("wifi");
                WifiInfo wifiInfo2 = twifiManager.getConnectionInfo();
                Log.d(SemWifiApSmartGattClient.this.TAG, " wifiInfo.getNetworkId(): " + wifiInfo2.getNetworkId() + ",wifiInfo.getBSSID():" + wifiInfo2.getBSSID() + ",status:" + SemWifiApSmartGattClient.this.getSmartApConnectedStatus(wifiInfo2.getBSSID()));
                if (!(wifiInfo2.getNetworkId() == -1 || wifiInfo2.getBSSID() == null || SemWifiApSmartGattClient.this.getSmartApConnectedStatus(wifiInfo2.getBSSID()) != 3)) {
                    Log.e(SemWifiApSmartGattClient.this.TAG, "Disconnecting Wifi as device is smartly connected and device is loggedout");
                    SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tDisconnecting Wifi as device is smartly connected and device is loggedout");
                    twifiManager.removeNetwork(wifiInfo2.getNetworkId());
                }
                SemWifiApSmartGattClient.this.mSmartMHSList.clear();
            } else if (i == 18) {
                int rcount = 0;
                synchronized (SemWifiApSmartGattClient.this.mSmartMHSList) {
                    List<Integer> mIndexeList = new ArrayList<>();
                    int count = 0;
                    for (SmartMHSInfo var : SemWifiApSmartGattClient.this.mSmartMHSList) {
                        if (!(var.state == SemWifiApSmartGattClient.ST_BONDING_FAILURE || var.state == -2)) {
                            if (var.state != SemWifiApSmartGattClient.ST_GATT_FAILURE) {
                                if (System.currentTimeMillis() - var.timestamp > 10000 && var.state < 0) {
                                    mIndexeList.add(Integer.valueOf(count));
                                    count++;
                                } else if (var.state == 1 && !SemWifiApSmartGattClient.this.isBondingGoingon && System.currentTimeMillis() - var.timestamp > 60000) {
                                    Log.e(SemWifiApSmartGattClient.this.TAG, " BLE transactions going on more than 60 sec, disconnecting gatt");
                                    SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tBLE transactions going on more than 60 sec, disconnecting gatt");
                                    var.state = SemWifiApSmartGattClient.ST_MHS_GATT_CLIENT_TIMEOUT;
                                    var.timestamp = System.currentTimeMillis();
                                    SemWifiApSmartGattClient.this.invokeCallback(var.clientMAC, SemWifiApSmartGattClient.ST_MHS_GATT_CLIENT_TIMEOUT);
                                    rcount++;
                                    if (SemWifiApSmartGattClient.this.mConnectedGatt != null) {
                                        SemWifiApSmartGattClient.this.shutdownclient();
                                    }
                                    count++;
                                } else if (var.state == 1 && System.currentTimeMillis() - var.timestamp > 45000) {
                                    if (SemWifiApSmartGattClient.this.mConnectedGatt != null) {
                                        Log.e(SemWifiApSmartGattClient.this.TAG, "mConnectedGatt is not null after 45 sec, so disconnecting gatt");
                                        SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tmConnectedGatt is not null after 45 sec, so disconnecting gatt");
                                        var.state = SemWifiApSmartGattClient.ST_MHS_GATT_CLIENT_TIMEOUT;
                                        var.timestamp = System.currentTimeMillis();
                                        SemWifiApSmartGattClient.this.invokeCallback(var.clientMAC, SemWifiApSmartGattClient.ST_MHS_GATT_CLIENT_TIMEOUT);
                                        rcount++;
                                        SemWifiApSmartGattClient.this.shutdownclient();
                                    } else if (SemWifiApSmartGattClient.this.mConnectedGatt == null) {
                                        Log.e(SemWifiApSmartGattClient.this.TAG, "mConnectedGatt is null after 45 sec,but state is Gatt Connecting ");
                                        SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tmConnectedGatt is null after 45 sec,but state is Gatt Connecting");
                                        var.state = SemWifiApSmartGattClient.ST_MHS_GATT_CLIENT_TIMEOUT;
                                        var.timestamp = System.currentTimeMillis();
                                        SemWifiApSmartGattClient.this.invokeCallback(var.clientMAC, SemWifiApSmartGattClient.ST_MHS_GATT_CLIENT_TIMEOUT);
                                        SemWifiApSmartGattClient.this.shutdownclient();
                                        rcount++;
                                    }
                                    count++;
                                } else if (var.state == 2 && System.currentTimeMillis() - var.timestamp > IWCEventManager.reconTimeThreshold) {
                                    SemWifiApSmartGattClient.this.mwaitingToConnect = false;
                                    Log.e(SemWifiApSmartGattClient.this.TAG, "Wifi connection timeout after 45 sec, so dont try to connect");
                                    SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tWifi connection timeout after 45 sec, so dont try to connect");
                                    var.state = SemWifiApSmartGattClient.ST_MHS_WIFI_CONNECTION_TIMEOUT;
                                    var.timestamp = System.currentTimeMillis();
                                    SemWifiApSmartGattClient.this.invokeCallback(var.clientMAC, SemWifiApSmartGattClient.ST_MHS_WIFI_CONNECTION_TIMEOUT);
                                    rcount++;
                                    count++;
                                } else if (var.state < 0 || var.state == 1 || var.state == 2) {
                                    rcount++;
                                    count++;
                                } else {
                                    count++;
                                }
                            }
                        }
                        if (!WifiInjector.getInstance().getSemWifiApSmartClient().getBLEPairingFailedHistory(var.clientMAC)) {
                            mIndexeList.add(Integer.valueOf(count));
                        } else {
                            rcount++;
                        }
                        count++;
                    }
                    try {
                        for (Integer num : mIndexeList) {
                            SemWifiApSmartGattClient.this.mSmartMHSList.remove(num.intValue());
                        }
                    } catch (IndexOutOfBoundsException e) {
                    }
                    SemWifiApSmartGattClient.this.showSmartMHSInfo();
                }
                if (rcount > 0) {
                    sendEmptyMessageDelayed(18, RttServiceImpl.HAL_RANGING_TIMEOUT_MS);
                }
            } else if (i == 19) {
                Log.d(SemWifiApSmartGattClient.this.TAG, "Device didn't get mtu callback so this device is using default value.");
                SemWifiApSmartGattClient.this.mLocalLog.log(SemWifiApSmartGattClient.this.TAG + ":\tDevice didn't get mtu callback so this device is using default value.");
                if (SemWifiApSmartGattClient.this.mConnectedGatt != null) {
                    SemWifiApSmartGattClient.this.mConnectedGatt.requestMtu(512);
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private int channelToFreq(int channel) {
        if (channel < 1 || channel > 165) {
            return -1;
        }
        return (channel <= 14 ? 2407 : 5000) + (channel * 5);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void startPartialScanAfterSleep(int sleepTime) {
        String str = this.TAG;
        Log.d(str, "startPartialScanAfterSleep() trying to semStartPartialChannelScan after sleep " + sleepTime + " mMhsFreq:" + this.mMhsFreq);
        LocalLog localLog = this.mLocalLog;
        localLog.log(this.TAG + ":\tstartPartialScanAfterSleep() trying to semStartPartialChannelScan after sleep " + sleepTime + " mMhsFreq:" + this.mMhsFreq);
        try {
            Thread.sleep((long) sleepTime);
        } catch (Exception e) {
        }
        int i = this.mMhsFreq;
        int[] freqs = {i};
        if (i == -1) {
            freqs = new int[]{2412, 2417, 2422, 2427, 2432, 2437, 2442, 2447, 2452, 2457, 2462, 5745};
        }
        WifiManager wifiManager = (WifiManager) this.mContext.getSystemService("wifi");
        if (wifiManager != null && !wifiManager.semStartPartialChannelScan(freqs)) {
            wifiManager.semStartPartialChannelScan(freqs);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setConnectionState(int state, String MhsMac) {
        String str = this.TAG;
        Log.d(str, "setConnectionState state " + state + "MhsMac" + MhsMac);
        LocalLog localLog = this.mLocalLog;
        localLog.log(this.TAG + ":\tsetConnectionState state :" + state + "MhsMac" + MhsMac);
        if (state == ST_BONDING_FAILURE || state == -2 || state == ST_GATT_FAILURE) {
            WifiInjector.getInstance().getSemWifiApSmartClient().setBLEPairingFailedHistory(MhsMac, new Pair<>(Long.valueOf(System.currentTimeMillis()), this.mSmartAp_BLE_MAC));
        }
        updateSmartMHSConnectionStatus(MhsMac, state);
        WifiManager.SemWifiApSmartCallback semWifiApSmartCallback = this.tSemWifiApSmartCallback;
        if (semWifiApSmartCallback != null) {
            try {
                semWifiApSmartCallback.onStateChanged(state, MhsMac);
            } catch (Exception e) {
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void shutdownclient_1() {
        Log.d(this.TAG, "shutdownclient_1");
        this.requestedToEnableMHS = false;
        BluetoothGatt bluetoothGatt = this.mConnectedGatt;
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
        }
        this.isBondingGoingon = false;
        this.mConnectedGatt = null;
        this.mTimeMismatchOccured = false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void shutdownclient() {
        Log.d(this.TAG, "shutdownclient");
        this.requestedToEnableMHS = false;
        BluetoothGatt bluetoothGatt = this.mConnectedGatt;
        if (bluetoothGatt != null) {
            this.mFailedBLEConnections.remove(bluetoothGatt.getDevice().getAddress());
            this.mConnectedGatt.close();
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
        }
        this.isBondingGoingon = false;
        this.mConnectedGatt = null;
        this.mTimeMismatchOccured = false;
    }

    /* access modifiers changed from: private */
    public static class SmartMHSInfo {
        public String MHS_MAC;
        public String clientMAC;
        public int state;
        public long timestamp;

        public SmartMHSInfo(String mClientMAC, String tMHS_MAC, long mTimeStamp, int mState) {
            this.clientMAC = mClientMAC;
            this.MHS_MAC = tMHS_MAC;
            this.timestamp = mTimeStamp;
            this.state = mState;
        }

        public String toString() {
            return String.format("clientMAC:" + this.clientMAC + ",MHS_MAC:" + this.MHS_MAC + ",timestamp:" + this.timestamp + ",state:" + this.state, new Object[0]);
        }
    }

    /* access modifiers changed from: package-private */
    public void showSmartMHSInfo() {
        synchronized (this.mSmartMHSList) {
            Iterator<SmartMHSInfo> it = this.mSmartMHSList.iterator();
            while (it.hasNext()) {
                String str = this.TAG;
                Log.d(str, "" + it.next());
            }
        }
    }

    private void updateSmartMHSConnectionStatus(String clientmac, int state) {
        synchronized (this.mSmartMHSList) {
            for (SmartMHSInfo inf : this.mSmartMHSList) {
                if (inf.clientMAC.equals(clientmac)) {
                    inf.state = state;
                    inf.timestamp = System.currentTimeMillis();
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void invokeCallback(String clientmac, int state) {
        String str = this.TAG;
        Log.d(str, "invokeCallback state " + state + "clientmac" + clientmac);
        LocalLog localLog = this.mLocalLog;
        localLog.log(this.TAG + ":\tsinvokeCallback state :" + state + "clientmac" + clientmac);
        WifiManager.SemWifiApSmartCallback semWifiApSmartCallback = this.tSemWifiApSmartCallback;
        if (semWifiApSmartCallback != null) {
            try {
                semWifiApSmartCallback.onStateChanged(state, clientmac);
            } catch (Exception e) {
            }
        }
    }

    public void factoryReset() {
        long ident = Binder.clearCallingIdentity();
        Log.d(this.TAG, "factoryReset is called");
        LocalLog localLog = this.mLocalLog;
        localLog.log(this.TAG + ":\tfactoryReset is called");
        try {
            Iterator<String> tempIt = this.bonedDevicesFromHotspotLive.iterator();
            if (BluetoothAdapter.getDefaultAdapter() != null) {
                while (tempIt != null && tempIt.hasNext()) {
                    BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(tempIt.next());
                    if (device != null && device.getBondState() == 12) {
                        device.removeBond();
                        String str = this.TAG;
                        Log.d(str, ":factoryReset smarttethering.removeBond :" + device.getAddress());
                        LocalLog localLog2 = this.mLocalLog;
                        localLog2.log(this.TAG + ":\tfactoryReset smarttethering.removeBond :" + device.getAddress());
                    }
                }
                this.bonedDevicesFromHotspotLive.clear();
                Settings.Secure.putString(this.mContext.getContentResolver(), "bonded_device_clientside", null);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean tryToConnectToRemoteBLE(BluetoothDevice mDevice, boolean autoConnect) {
        String mBTaddr = mDevice.getAddress();
        int count = 0;
        if (this.mFailedBLEConnections.containsKey(mBTaddr)) {
            count = this.mFailedBLEConnections.get(mBTaddr).intValue();
        }
        int count2 = count + 1;
        this.mFailedBLEConnections.put(mBTaddr, Integer.valueOf(count2));
        String str = this.TAG;
        Log.e(str, "Trying to create a new connection. attempt:" + count2);
        LocalLog localLog = this.mLocalLog;
        localLog.log(this.TAG + "\tTrying to create a new connection. attempt:" + count2);
        setConnectionState(1, this.mSmartAp_WiFi_MAC);
        this.mConnectedGatt = mDevice.connectGatt(this.mContext, autoConnect, this.mGattCallback, 2);
        if (this.mConnectedGatt == null) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
            String str2 = this.TAG;
            Log.e(str2, "mConnectedGatt = null, Trying to create a new connection. attempt:" + count2);
            LocalLog localLog2 = this.mLocalLog;
            localLog2.log(this.TAG + "\tmConnectedGatt = null, Trying to create a new connection. attempt:" + count2);
            setConnectionState(1, this.mSmartAp_WiFi_MAC);
            this.mConnectedGatt = mDevice.connectGatt(this.mContext, true, this.mGattCallback, 2);
            if (this.mConnectedGatt == null) {
                Log.e(this.TAG, " mConnectedGatt = null, returning false");
                LocalLog localLog3 = this.mLocalLog;
                localLog3.log(this.TAG + "\tmConnectedGatt = null, returning false");
                return false;
            }
        }
        return true;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private String removeDoubleQuotes(String string) {
        if (string == null) {
            return null;
        }
        int length = string.length();
        if (length > 1 && string.charAt(0) == '\"' && string.charAt(length - 1) == '\"') {
            return string.substring(1, length - 1);
        }
        return string;
    }
}

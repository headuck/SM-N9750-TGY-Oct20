package com.samsung.android.server.wifi.softap.smarttethering;

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
import android.database.ContentObserver;
import android.net.wifi.SemWifiApSmartWhiteList;
import android.os.FactoryTest;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.LocalLog;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.iwc.IWCEventManager;
import com.samsung.android.net.wifi.ISemWifiApSmartCallback;
import com.samsung.android.server.wifi.dqa.ReportIdKey;
import com.samsung.android.server.wifi.softap.SemWifiApBroadcastReceiver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class SemWifiApSmartD2DGattClient {
    private static final int ST_ADDED_TO_ALLOWED_LIST = 4;
    private static final int ST_BONDING_FAILURE = -6;
    private static final int ST_BONDING_GOINGON = -4;
    private static final int ST_BOND_FAILED = -2;
    private static final int ST_BT_PAIRING = 2;
    private static final int ST_CONNECTION_ALREADY_EXIST = -3;
    private static final int ST_DEVICE_NOT_FOUND = -1;
    private static final int ST_DISCONNECTED = 0;
    private static final int ST_GATT_CONNECTING = 1;
    private static final int ST_GATT_FAILURE = -5;
    private static final int ST_MHS_GATT_CLIENT_TIMEOUT = -7;
    private static final int ST_MHS_GATT_SERVICE_NOT_FOUND = -8;
    private static IntentFilter mSemWifiApSmartD2DGattClientIntentFilter = new IntentFilter("android.bluetooth.device.action.BOND_STATE_CHANGED");
    private final int DISCONNECT_GATT = 12;
    private final int DISPLAY_ADDED_TOAST = 14;
    private final int DISPLAY_FAILED_TO_ADD_TOAST = 15;
    private final String TAG = "SemWifiApSmartD2DGattClient";
    private final int UPDATE_CONNECTION_FAILURES = 13;
    private final int WAIT_FOR_MTU_CALLBACK = 16;
    private HashSet<String> bonedDevicesFromD2D = new HashSet<>();
    private boolean isBondingGoingon = false;
    private BleWorkHandler mBleWorkHandler = null;
    private HandlerThread mBleWorkThread = null;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGattService mBluetoothGattService;
    private boolean mBluetoothIsOn;
    List<ClientD2DInfo> mClientD2DList = new ArrayList();
    private BluetoothGatt mConnectedGatt = null;
    private Context mContext;
    private String mD2DClient_MAC;
    List<String> mD2DConnection = new ArrayList();
    HashMap<String, Integer> mFailedBLEConnections = new HashMap<>();
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        /* class com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartD2DGattClient.C08061 */

        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            LocalLog localLog = SemWifiApSmartD2DGattClient.this.mLocalLog;
            localLog.log("SemWifiApSmartD2DGattClient:\tonConnectionStateChange " + SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil.getStatusDescription(status) + " " + SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil.getStateDescription(newState));
            if (newState == 2) {
                Log.d("SemWifiApSmartD2DGattClient", "device,connected" + gatt.getDevice() + ",mRequestedToConnect:" + SemWifiApSmartD2DGattClient.this.mRequestedToConnect);
                if (SemWifiApSmartD2DGattClient.this.mRequestedToConnect) {
                    SemWifiApSmartD2DGattClient.this.mRequestedToConnect = false;
                    if (SemWifiApSmartD2DGattClient.this.mBleWorkHandler != null) {
                        SemWifiApSmartD2DGattClient.this.mBleWorkHandler.sendEmptyMessageDelayed(16, 300);
                    }
                }
            } else if (newState == 0) {
                Log.d("SemWifiApSmartD2DGattClient", "device, disconnected" + gatt.getDevice());
                if (status != 0) {
                    String mBTaddr = gatt.getDevice().getAddress();
                    int count = SemWifiApSmartD2DGattClient.this.mFailedBLEConnections.get(mBTaddr).intValue();
                    if (count >= 3) {
                        SemWifiApSmartD2DGattClient semWifiApSmartD2DGattClient = SemWifiApSmartD2DGattClient.this;
                        semWifiApSmartD2DGattClient.setConnectionState(SemWifiApSmartD2DGattClient.ST_GATT_FAILURE, semWifiApSmartD2DGattClient.mD2DClient_MAC);
                        SemWifiApSmartD2DGattClient.this.mFailedBLEConnections.remove(mBTaddr);
                        SemWifiApSmartD2DGattClient.this.shutdownclient();
                    } else if (count < 3) {
                        SemWifiApSmartD2DGattClient.this.shutdownclient_1();
                        gatt.refresh();
                        try {
                            Thread.sleep(1000);
                        } catch (Exception e) {
                        }
                        if (!SemWifiApSmartD2DGattClient.this.tryToConnectToRemoteBLE(gatt.getDevice(), true)) {
                            SemWifiApSmartD2DGattClient semWifiApSmartD2DGattClient2 = SemWifiApSmartD2DGattClient.this;
                            semWifiApSmartD2DGattClient2.setConnectionState(SemWifiApSmartD2DGattClient.ST_GATT_FAILURE, semWifiApSmartD2DGattClient2.mD2DClient_MAC);
                            SemWifiApSmartD2DGattClient.this.mFailedBLEConnections.remove(mBTaddr);
                            SemWifiApSmartD2DGattClient.this.shutdownclient();
                        }
                    }
                } else {
                    if (gatt.getDevice() != null) {
                        SemWifiApSmartD2DGattClient.this.mFailedBLEConnections.remove(gatt.getDevice().getAddress());
                    }
                    SemWifiApSmartD2DGattClient.this.shutdownclient();
                }
            }
        }

        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            boolean found = false;
            for (BluetoothGattService service : gatt.getServices()) {
                Log.d("SemWifiApSmartD2DGattClient", "Service: " + SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil.lookup(service.getUuid()));
                SemWifiApSmartUtil unused = SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil;
                if (SemWifiApSmartUtil.SERVICE_UUID.equals(service.getUuid())) {
                    SemWifiApSmartD2DGattClient.this.mBluetoothGattService = service;
                    found = true;
                    BluetoothGattService bluetoothGattService = SemWifiApSmartD2DGattClient.this.mBluetoothGattService;
                    SemWifiApSmartUtil unused2 = SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil;
                    gatt.readCharacteristic(bluetoothGattService.getCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_D2D_CLIENT_BOND_STATUS));
                }
            }
            if (!found) {
                SemWifiApSmartD2DGattClient semWifiApSmartD2DGattClient = SemWifiApSmartD2DGattClient.this;
                semWifiApSmartD2DGattClient.setConnectionState(SemWifiApSmartD2DGattClient.ST_MHS_GATT_SERVICE_NOT_FOUND, semWifiApSmartD2DGattClient.mD2DClient_MAC);
                if (SemWifiApSmartD2DGattClient.this.mBleWorkHandler != null) {
                    SemWifiApSmartD2DGattClient.this.mBleWorkHandler.sendEmptyMessage(12);
                }
            }
        }

        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            SemWifiApSmartUtil unused = SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_D2D_CLIENT_BOND_STATUS.equals(characteristic.getUuid())) {
                byte[] mbytes = characteristic.getValue();
                if (mbytes == null || mbytes.length <= 0 || mbytes[0] != 1) {
                    Log.d("SemWifiApSmartD2DGattClient", "remote device is not bonded");
                    SemWifiApSmartD2DGattClient.this.mLocalLog.log("SemWifiApSmartD2DGattClient:\tremote device is not bonded");
                    BluetoothDevice mDevice = gatt.getDevice();
                    if (mDevice != null) {
                        Log.e("SemWifiApSmartD2DGattClient", "device is not bonded at D2D Client side ,so removing the device");
                        mDevice.removeBond();
                    }
                    SemWifiApSmartD2DGattClient semWifiApSmartD2DGattClient = SemWifiApSmartD2DGattClient.this;
                    semWifiApSmartD2DGattClient.setConnectionState(SemWifiApSmartD2DGattClient.ST_BONDING_FAILURE, semWifiApSmartD2DGattClient.mD2DClient_MAC);
                    if (SemWifiApSmartD2DGattClient.this.mBleWorkHandler != null) {
                        SemWifiApSmartD2DGattClient.this.mBleWorkHandler.sendEmptyMessage(12);
                        return;
                    }
                    return;
                }
                Log.d("SemWifiApSmartD2DGattClient", "Got bond status:" + ((int) mbytes[0]));
                LocalLog localLog = SemWifiApSmartD2DGattClient.this.mLocalLog;
                localLog.log("SemWifiApSmartD2DGattClient:\tGot bond status:" + ((int) mbytes[0]));
                BluetoothGattService bluetoothGattService = SemWifiApSmartD2DGattClient.this.mBluetoothGattService;
                SemWifiApSmartUtil unused2 = SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil;
                BluetoothGattCharacteristic mtemp = bluetoothGattService.getCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_ACCEPT_INVITATION);
                gatt.setCharacteristicNotification(mtemp, true);
                mtemp.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeCharacteristic(mtemp);
                return;
            }
            SemWifiApSmartUtil unused3 = SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_CLIENT_MAC.equals(characteristic.getUuid())) {
                byte[] mbytes2 = characteristic.getValue();
                if (mbytes2 != null && mbytes2[0] == 1) {
                    String mClientMAC = new String(mbytes2, 1, 17);
                    String mDeviceName = new String(mbytes2, 19, (int) mbytes2[18]);
                    String mClientMAC2 = mClientMAC.toLowerCase();
                    Log.d("SemWifiApSmartD2DGattClient", "got client devicename and MAC" + mClientMAC2 + ":" + mDeviceName);
                    SemWifiApSmartWhiteList.getInstance().addWhiteList(mClientMAC2, mDeviceName);
                    if (SemWifiApSmartD2DGattClient.this.mBleWorkHandler != null) {
                        SemWifiApSmartD2DGattClient.this.mBleWorkHandler.sendEmptyMessageDelayed(14, IWCEventManager.autoDisconnectThreshold);
                    }
                } else if (mbytes2[0] == 0 && SemWifiApSmartD2DGattClient.this.mBleWorkHandler != null) {
                    SemWifiApSmartD2DGattClient.this.mBleWorkHandler.sendEmptyMessageDelayed(15, IWCEventManager.autoDisconnectThreshold);
                }
                if (SemWifiApSmartD2DGattClient.this.mBleWorkHandler != null) {
                    SemWifiApSmartD2DGattClient.this.mBleWorkHandler.sendEmptyMessage(12);
                }
            }
        }

        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d("SemWifiApSmartD2DGattClient", "requestedToAccept:" + SemWifiApSmartD2DGattClient.this.requestedToAccept + ",onCharacteristicChanged:" + SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil.lookup(characteristic.getUuid()));
            SemWifiApSmartUtil unused = SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_ACCEPT_INVITATION.equals(characteristic.getUuid()) && SemWifiApSmartD2DGattClient.this.requestedToAccept) {
                SemWifiApSmartD2DGattClient.this.requestedToAccept = false;
                BluetoothGattService bluetoothGattService = SemWifiApSmartD2DGattClient.this.mBluetoothGattService;
                SemWifiApSmartUtil unused2 = SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil;
                gatt.readCharacteristic(bluetoothGattService.getCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_CLIENT_MAC));
            }
        }

        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d("SemWifiApSmartD2DGattClient", "onCharacteristicWrite:" + SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil.lookup(characteristic.getUuid()));
            SemWifiApSmartUtil unused = SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_ACCEPT_INVITATION.equals(characteristic.getUuid())) {
                BluetoothGattService bluetoothGattService = SemWifiApSmartD2DGattClient.this.mBluetoothGattService;
                SemWifiApSmartUtil unused2 = SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil;
                BluetoothGattCharacteristic mtemp = bluetoothGattService.getCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_FAMILY_ID);
                byte[] mdata = new byte[150];
                String mFamilyId = SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil.getFamilyID();
                String mDeviceName = SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil.getDeviceName();
                String mWiFiMAC = SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil.getOwnWifiMac();
                if (mFamilyId == null || mFamilyId.equals("")) {
                    Log.e("SemWifiApSmartD2DGattClient", "family id is null shutting down");
                    if (SemWifiApSmartD2DGattClient.this.mBleWorkHandler != null) {
                        SemWifiApSmartD2DGattClient.this.mBleWorkHandler.sendEmptyMessage(12);
                        return;
                    }
                    return;
                }
                Log.d("SemWifiApSmartD2DGattClient", "sending familyid to client");
                byte[] tdata = mFamilyId.getBytes();
                byte[] tdeviceName = mDeviceName.getBytes();
                byte[] tWiFiMAC = mWiFiMAC.getBytes();
                mdata[0] = (byte) tdata.length;
                for (int i = 0; i < tdata.length; i++) {
                    mdata[i + 1] = tdata[i];
                }
                mdata[tdata.length + 1] = (byte) tdeviceName.length;
                for (int i2 = 0; i2 < tdeviceName.length; i2++) {
                    mdata[i2 + 2 + tdata.length] = tdeviceName[i2];
                }
                mdata[tdata.length + 2 + tdeviceName.length] = (byte) tWiFiMAC.length;
                for (int i3 = 0; i3 < tWiFiMAC.length; i3++) {
                    mdata[i3 + 3 + tdata.length + tdeviceName.length] = tWiFiMAC[i3];
                }
                mtemp.setValue(mdata);
                gatt.writeCharacteristic(mtemp);
                return;
            }
            SemWifiApSmartUtil unused3 = SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_FAMILY_ID.equals(characteristic.getUuid())) {
                SemWifiApSmartD2DGattClient.this.requestedToAccept = true;
            }
        }

        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (mtu == 512) {
                gatt.discoverServices();
                if (SemWifiApSmartD2DGattClient.this.mBleWorkHandler != null && SemWifiApSmartD2DGattClient.this.mBleWorkHandler.hasMessages(16)) {
                    SemWifiApSmartD2DGattClient.this.mBleWorkHandler.removeMessages(16);
                }
            }
        }
    };
    private LocalLog mLocalLog;
    private String mPendingDeviceAddress;
    private boolean mRequestedToConnect = false;
    private SemWifiApSmartD2DGattClientReceiver mSemWifiApSmartD2DGattClientReceiver;
    private SemWifiApSmartD2DMHS mSemWifiApSmartD2DMHS;
    private SemWifiApSmartUtil mSemWifiApSmartUtil;
    private ContentObserver mSemWifiApSmart_D2D_SwitchObserver = new ContentObserver(new Handler()) {
        /* class com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartD2DGattClient.C08072 */

        public void onChange(boolean selfChange) {
            Settings.Secure.getInt(SemWifiApSmartD2DGattClient.this.mContext.getContentResolver(), "wifi_ap_smart_d2d_mhs", 0);
        }
    };
    private String mSmartAp_BLE_MAC;
    private boolean requestedToAccept;
    private ISemWifiApSmartCallback tSemWifiApSmartCallback;

    public SemWifiApSmartD2DGattClient(Context context, SemWifiApSmartUtil semWifiApSmartUtil, LocalLog tLocalLog) {
        this.mContext = context;
        this.mSemWifiApSmartUtil = semWifiApSmartUtil;
        this.mLocalLog = tLocalLog;
        this.mSemWifiApSmartD2DGattClientReceiver = new SemWifiApSmartD2DGattClientReceiver();
        if (!FactoryTest.isFactoryBinary()) {
            this.mContext.registerReceiver(this.mSemWifiApSmartD2DGattClientReceiver, mSemWifiApSmartD2DGattClientIntentFilter);
            this.mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor("wifi_ap_smart_d2d_mhs"), false, this.mSemWifiApSmart_D2D_SwitchObserver);
            return;
        }
        Log.e("SemWifiApSmartD2DGattClient", "This devices's binary is a factory binary");
    }

    static {
        mSemWifiApSmartD2DGattClientIntentFilter.addAction("android.bluetooth.device.action.PAIRING_REQUEST");
        mSemWifiApSmartD2DGattClientIntentFilter.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
        mSemWifiApSmartD2DGattClientIntentFilter.setPriority(ReportIdKey.ID_PATTERN_MATCHED);
    }

    /* access modifiers changed from: package-private */
    public class BleWorkHandler extends Handler {
        public BleWorkHandler(Looper looper) {
            super(looper);
        }

        /* JADX WARNING: Code restructure failed: missing block: B:61:0x0250, code lost:
            if (r0.state != 2) goto L_0x0253;
         */
        public void handleMessage(Message msg) {
            List<ClientD2DInfo> list;
            boolean z;
            int rcount;
            boolean z2;
            Log.i("SemWifiApSmartD2DGattClient", "msg.what:" + msg.what);
            boolean z3 = true;
            switch (msg.what) {
                case 12:
                    SemWifiApSmartD2DGattClient.this.shutdownclient();
                    return;
                case 13:
                    int rcount2 = 0;
                    List<ClientD2DInfo> list2 = SemWifiApSmartD2DGattClient.this.mClientD2DList;
                    synchronized (list2) {
                        try {
                            List<Integer> mIndexeList = new ArrayList<>();
                            Iterator<ClientD2DInfo> it = SemWifiApSmartD2DGattClient.this.mClientD2DList.iterator();
                            int count = 0;
                            while (it.hasNext()) {
                                try {
                                    ClientD2DInfo var = it.next();
                                    if (var.state == SemWifiApSmartD2DGattClient.ST_BONDING_FAILURE || var.state == -2) {
                                        rcount = rcount2;
                                        z2 = z3;
                                        list = list2;
                                    } else if (var.state == SemWifiApSmartD2DGattClient.ST_GATT_FAILURE) {
                                        rcount = rcount2;
                                        z2 = z3;
                                        list = list2;
                                    } else {
                                        try {
                                            if (System.currentTimeMillis() - var.timestamp <= 10000 || var.state >= 0) {
                                                if (var.state == 2) {
                                                    list = list2;
                                                    try {
                                                        if (System.currentTimeMillis() - var.timestamp > 40000) {
                                                            Log.e("SemWifiApSmartD2DGattClient", " ST_BT_PAIRING after 30 sec, so cancelling bonding:" + var.clientMAC.substring(9) + ",isBondingGoingon:" + SemWifiApSmartD2DGattClient.this.isBondingGoingon);
                                                            SemWifiApSmartD2DGattClient.this.mLocalLog.log("SemWifiApSmartD2DGattClient:\tST_BT_PAIRING after 30 sec, so cancelling bonding:" + var.clientMAC.substring(9) + ",isBondingGoingon:" + SemWifiApSmartD2DGattClient.this.isBondingGoingon);
                                                            var.state = SemWifiApSmartD2DGattClient.ST_BONDING_FAILURE;
                                                            var.timestamp = System.currentTimeMillis();
                                                            SemWifiApSmartD2DGattClient.this.isBondingGoingon = false;
                                                            rcount2++;
                                                            try {
                                                                SemWifiApSmartD2DGattClient.this.invokeCallback(var.clientMAC, SemWifiApSmartD2DGattClient.ST_BONDING_FAILURE);
                                                                SemWifiApSmartD2DGattClient.this.mSemWifiApSmartD2DMHS.setBLEPairingFailedHistory(var.clientMAC, new Pair<>(Long.valueOf(System.currentTimeMillis()), SemWifiApSmartD2DGattClient.this.mSmartAp_BLE_MAC));
                                                                z = true;
                                                                count++;
                                                                list2 = list;
                                                                z3 = z;
                                                            } catch (Throwable th) {
                                                                th = th;
                                                                throw th;
                                                            }
                                                        }
                                                    } catch (Throwable th2) {
                                                        th = th2;
                                                        throw th;
                                                    }
                                                } else {
                                                    list = list2;
                                                }
                                                if (var.state == 1 && SemWifiApSmartD2DGattClient.this.mConnectedGatt == null) {
                                                    Log.e("SemWifiApSmartD2DGattClient", " ST_GATT_CONNECTING and mConnectgatt is null :" + var.clientMAC.substring(9));
                                                    SemWifiApSmartD2DGattClient.this.mLocalLog.log("SemWifiApSmartD2DGattClient:\tST_GATT_CONNECTING and mConnectgatt is null:" + var.clientMAC.substring(9));
                                                    var.state = 0;
                                                    var.timestamp = System.currentTimeMillis();
                                                    mIndexeList.add(Integer.valueOf(count));
                                                    SemWifiApSmartD2DGattClient.this.invokeCallback(var.clientMAC, 0);
                                                    z = true;
                                                } else if (var.state != 1 || System.currentTimeMillis() - var.timestamp <= 35000) {
                                                    if (var.state >= 0) {
                                                        z = true;
                                                        if (var.state != 1) {
                                                            break;
                                                        }
                                                    } else {
                                                        z = true;
                                                    }
                                                    rcount2++;
                                                    count++;
                                                    list2 = list;
                                                    z3 = z;
                                                } else {
                                                    Log.e("SemWifiApSmartD2DGattClient", "mConnectedGatt is not null after 40 sec, so disconnecting gatt");
                                                    SemWifiApSmartD2DGattClient.this.mLocalLog.log("SemWifiApSmartD2DGattClient:\tmConnectedGatt is not null after 40 sec, so disconnecting gatt");
                                                    var.state = SemWifiApSmartD2DGattClient.ST_MHS_GATT_CLIENT_TIMEOUT;
                                                    var.timestamp = System.currentTimeMillis();
                                                    SemWifiApSmartD2DGattClient.this.invokeCallback(var.clientMAC, SemWifiApSmartD2DGattClient.ST_MHS_GATT_CLIENT_TIMEOUT);
                                                    rcount2++;
                                                    if (SemWifiApSmartD2DGattClient.this.mConnectedGatt != null) {
                                                        SemWifiApSmartD2DGattClient.this.shutdownclient();
                                                    }
                                                    z = true;
                                                    count++;
                                                    list2 = list;
                                                    z3 = z;
                                                }
                                            } else {
                                                mIndexeList.add(Integer.valueOf(count));
                                                list = list2;
                                                z = true;
                                            }
                                            rcount2 = rcount2;
                                            count++;
                                            list2 = list;
                                            z3 = z;
                                        } catch (Throwable th3) {
                                            th = th3;
                                            list = list2;
                                            throw th;
                                        }
                                    }
                                    if (!WifiInjector.getInstance().getSemWifiApSmartD2DMHS().getBLEPairingFailedHistory(var.clientMAC)) {
                                        SemWifiApSmartD2DGattClient.this.mLocalLog.log("SemWifiApSmartD2DGattClient:\tUPDATE_CONNECTION_FAILURES: removing," + var.clientMAC.substring(9));
                                        Log.d("SemWifiApSmartD2DGattClient", "UPDATE_CONNECTION_FAILURES: removing, " + var.clientMAC.substring(9));
                                        mIndexeList.add(Integer.valueOf(count));
                                    } else {
                                        rcount++;
                                    }
                                    rcount2 = rcount;
                                    count++;
                                    list2 = list;
                                    z3 = z;
                                } catch (Throwable th4) {
                                    th = th4;
                                    list = list2;
                                    throw th;
                                }
                            }
                            try {
                                for (Integer num : mIndexeList) {
                                    SemWifiApSmartD2DGattClient.this.mClientD2DList.remove(num.intValue());
                                }
                                if (mIndexeList.size() > 0) {
                                    SemWifiApSmartD2DGattClient.this.mSemWifiApSmartUtil.sendClientScanResultUpdateIntent("D2D gattclient, update failures");
                                }
                            } catch (IndexOutOfBoundsException e) {
                            }
                            SemWifiApSmartD2DGattClient.this.showD2DClientInfo();
                            if (rcount2 > 0) {
                                sendEmptyMessageDelayed(13, 10000);
                                return;
                            }
                            return;
                        } catch (Throwable th5) {
                            th = th5;
                            list = list2;
                            throw th;
                        }
                    }
                case 14:
                    Toast.makeText(SemWifiApSmartD2DGattClient.this.mContext, 17042595, 1).show();
                    SemWifiApSmartD2DGattClient semWifiApSmartD2DGattClient = SemWifiApSmartD2DGattClient.this;
                    semWifiApSmartD2DGattClient.invokeCallback(semWifiApSmartD2DGattClient.mD2DClient_MAC, 4);
                    SemWifiApSmartD2DGattClient semWifiApSmartD2DGattClient2 = SemWifiApSmartD2DGattClient.this;
                    semWifiApSmartD2DGattClient2.setConnectionState(0, semWifiApSmartD2DGattClient2.mD2DClient_MAC);
                    SemWifiApSmartD2DGattClient semWifiApSmartD2DGattClient3 = SemWifiApSmartD2DGattClient.this;
                    semWifiApSmartD2DGattClient3.removeDuplicateClientMAC(semWifiApSmartD2DGattClient3.mD2DClient_MAC);
                    return;
                case 15:
                    Toast.makeText(SemWifiApSmartD2DGattClient.this.mContext, 17042596, 1).show();
                    SemWifiApSmartD2DGattClient semWifiApSmartD2DGattClient4 = SemWifiApSmartD2DGattClient.this;
                    semWifiApSmartD2DGattClient4.setConnectionState(0, semWifiApSmartD2DGattClient4.mD2DClient_MAC);
                    SemWifiApSmartD2DGattClient semWifiApSmartD2DGattClient5 = SemWifiApSmartD2DGattClient.this;
                    semWifiApSmartD2DGattClient5.removeDuplicateClientMAC(semWifiApSmartD2DGattClient5.mD2DClient_MAC);
                    return;
                case 16:
                    Log.d("SemWifiApSmartD2DGattClient", "Device didn't get mtu callback so this device is using default value.");
                    SemWifiApSmartD2DGattClient.this.mLocalLog.log("SemWifiApSmartD2DGattClient:\tDevice didn't get mtu callback so this device is using default value.");
                    if (SemWifiApSmartD2DGattClient.this.mConnectedGatt != null) {
                        SemWifiApSmartD2DGattClient.this.mConnectedGatt.requestMtu(512);
                        return;
                    }
                    return;
                default:
                    return;
            }
        }
    }

    public void handleBootCompleted() {
        Log.d("SemWifiApSmartD2DGattClient", "handleBootCompleted");
        this.mBleWorkThread = new HandlerThread("SemWifiApSmartD2DGattClientHandler");
        this.mBleWorkThread.start();
        this.mBleWorkHandler = new BleWorkHandler(this.mBleWorkThread.getLooper());
        String tlist = Settings.Secure.getString(this.mContext.getContentResolver(), "bonded_device_d2dMHSside");
        if (tlist != null) {
            for (String str : tlist.split("\n")) {
                this.bonedDevicesFromD2D.add(str);
            }
            this.mLocalLog.log("SemWifiApSmartD2DGattClient:\tbonded_device_clientside,booting time :" + tlist);
        }
    }

    class SemWifiApSmartD2DGattClientReceiver extends BroadcastReceiver {
        SemWifiApSmartD2DGattClientReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.bluetooth.device.action.PAIRING_REQUEST")) {
                BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
                int mType = intent.getIntExtra("android.bluetooth.device.extra.PAIRING_VARIANT", Integer.MIN_VALUE);
                String.format(Locale.US, "%06d", Integer.valueOf(intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY", Integer.MIN_VALUE)));
                Log.d("SemWifiApSmartD2DGattClient", "mType: " + mType + " ,device: " + device + " ,mPendingDeviceAddress: " + SemWifiApSmartD2DGattClient.this.mPendingDeviceAddress);
                SemWifiApSmartD2DGattClient.this.mLocalLog.log("SemWifiApSmartD2DGattClient:\tmType: " + mType + " ,device: " + device + " ,mPendingDeviceAddress: " + SemWifiApSmartD2DGattClient.this.mPendingDeviceAddress);
                if (SemWifiApSmartD2DGattClient.this.mPendingDeviceAddress != null && device.getAddress().equals(SemWifiApSmartD2DGattClient.this.mPendingDeviceAddress) && mType == 2) {
                    abortBroadcast();
                    intent.setClassName("com.android.settings", "com.samsung.android.settings.wifi.mobileap.WifiApWarning");
                    intent.setFlags(268435456);
                    intent.setAction(SemWifiApBroadcastReceiver.WIFIAP_WARNING_DIALOG);
                    intent.putExtra(SemWifiApBroadcastReceiver.WIFIAP_WARNING_DIALOG_TYPE, 8);
                    SemWifiApSmartD2DGattClient.this.mContext.startActivity(intent);
                    Log.d("SemWifiApSmartD2DGattClient", "passkeyconfirm dialog");
                }
            } else if (!action.equals("android.bluetooth.adapter.action.STATE_CHANGED") || !SemWifiApSmartD2DGattClient.this.mBluetoothIsOn) {
                if (intent.getAction().equals("android.bluetooth.device.action.BOND_STATE_CHANGED")) {
                    BluetoothDevice device2 = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
                    switch (intent.getIntExtra("android.bluetooth.device.extra.BOND_STATE", 10)) {
                        case 10:
                            SemWifiApSmartD2DGattClient.this.isBondingGoingon = false;
                            Log.i("SemWifiApSmartD2DGattClient", "BOND FAILED mPendingDeviceAddress:" + SemWifiApSmartD2DGattClient.this.mPendingDeviceAddress + ",device.getAddress():" + device2.getAddress());
                            if (SemWifiApSmartD2DGattClient.this.mPendingDeviceAddress != null && device2.getAddress().equals(SemWifiApSmartD2DGattClient.this.mPendingDeviceAddress)) {
                                SemWifiApSmartD2DGattClient.this.mPendingDeviceAddress = null;
                                Log.d("SemWifiApSmartD2DGattClient", " client Bonding is failed");
                                SemWifiApSmartD2DGattClient.this.mLocalLog.log("SemWifiApSmartD2DGattClient:\tclient Bonding is failed");
                                new Handler().postDelayed(new Runnable() {
                                    /* class com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartD2DGattClient.SemWifiApSmartD2DGattClientReceiver.RunnableC08081 */

                                    public void run() {
                                        SemWifiApSmartD2DGattClient.this.setConnectionState(-2, SemWifiApSmartD2DGattClient.this.mD2DClient_MAC);
                                    }
                                }, 6000);
                                if (SemWifiApSmartD2DGattClient.this.mBleWorkHandler != null) {
                                    SemWifiApSmartD2DGattClient.this.mBleWorkHandler.sendEmptyMessage(12);
                                    return;
                                }
                                return;
                            }
                            return;
                        case 11:
                            Log.d("SemWifiApSmartD2DGattClient", "BONDing gOING ON mPendingDeviceAddress:" + SemWifiApSmartD2DGattClient.this.mPendingDeviceAddress + ",device.getAddress():" + device2.getAddress());
                            if (SemWifiApSmartD2DGattClient.this.mPendingDeviceAddress != null && device2.getAddress().equals(SemWifiApSmartD2DGattClient.this.mPendingDeviceAddress)) {
                                SemWifiApSmartD2DGattClient.this.isBondingGoingon = true;
                                Log.d("SemWifiApSmartD2DGattClient", " client Bonding is going on");
                                SemWifiApSmartD2DGattClient.this.mLocalLog.log("SemWifiApSmartD2DGattClient:\tclient Bonding is going on");
                                return;
                            }
                            return;
                        case 12:
                            SemWifiApSmartD2DGattClient.this.isBondingGoingon = false;
                            Log.i("SemWifiApSmartD2DGattClient", "BONDED mPendingDeviceAddress:" + SemWifiApSmartD2DGattClient.this.mPendingDeviceAddress + ",device.getAddress():" + device2.getAddress());
                            if (SemWifiApSmartD2DGattClient.this.mPendingDeviceAddress != null && device2.getAddress().equals(SemWifiApSmartD2DGattClient.this.mPendingDeviceAddress)) {
                                SemWifiApSmartD2DGattClient.this.mPendingDeviceAddress = null;
                                Log.d("SemWifiApSmartD2DGattClient", "D2D MHS Bonding is done");
                                SemWifiApSmartD2DGattClient.this.bonedDevicesFromD2D.add(device2.getAddress());
                                String tpString = "";
                                Iterator it = SemWifiApSmartD2DGattClient.this.bonedDevicesFromD2D.iterator();
                                while (it.hasNext()) {
                                    tpString = tpString + ((String) it.next()) + "\n";
                                }
                                Settings.Secure.putString(SemWifiApSmartD2DGattClient.this.mContext.getContentResolver(), "bonded_device_d2dMHSside", tpString);
                                SemWifiApSmartD2DGattClient.this.mLocalLog.log("SemWifiApSmartD2DGattClient:\tAdding to bondedd devices :" + device2.getAddress());
                                Log.d("SemWifiApSmartD2DGattClient", ":Adding to bondedd devices :" + tpString);
                                SemWifiApSmartD2DGattClient.this.mLocalLog.log("SemWifiApSmartD2DGattClient\tTrying to create a D2D connection after bonding.");
                                SemWifiApSmartD2DGattClient semWifiApSmartD2DGattClient = SemWifiApSmartD2DGattClient.this;
                                semWifiApSmartD2DGattClient.setConnectionState(1, semWifiApSmartD2DGattClient.mD2DClient_MAC);
                                if (!SemWifiApSmartD2DGattClient.this.tryToConnectToRemoteBLE(device2, false)) {
                                    SemWifiApSmartD2DGattClient semWifiApSmartD2DGattClient2 = SemWifiApSmartD2DGattClient.this;
                                    semWifiApSmartD2DGattClient2.setConnectionState(SemWifiApSmartD2DGattClient.ST_GATT_FAILURE, semWifiApSmartD2DGattClient2.mD2DClient_MAC);
                                    SemWifiApSmartD2DGattClient.this.mFailedBLEConnections.remove(device2.getAddress());
                                    return;
                                }
                                return;
                            }
                            return;
                        default:
                            return;
                    }
                }
            } else if (SemWifiApSmartD2DGattClient.this.mBluetoothDevice != null && BluetoothAdapter.getDefaultAdapter().getState() == 12) {
                Log.d("SemWifiApSmartD2DGattClient", "ACTION_STATE_CHANGED mBluetoothIsOn " + SemWifiApSmartD2DGattClient.this.mBluetoothIsOn);
                SemWifiApSmartD2DGattClient semWifiApSmartD2DGattClient3 = SemWifiApSmartD2DGattClient.this;
                semWifiApSmartD2DGattClient3.mPendingDeviceAddress = semWifiApSmartD2DGattClient3.mBluetoothDevice.getAddress();
                SemWifiApSmartD2DGattClient.this.mBluetoothIsOn = false;
                SemWifiApSmartD2DGattClient.this.mBluetoothDevice.createBond(2);
            }
        }
    }

    /* JADX WARNING: Code restructure failed: missing block: B:26:0x0054, code lost:
        r19.mD2DClient_MAC = r21;
        r19.mSmartAp_BLE_MAC = r20;
        android.util.Log.d("SemWifiApSmartD2DGattClient", "  connectToD2DClient   address:" + r20 + ",clientMAC:" + r21);
        r19.mLocalLog.log("SemWifiApSmartD2DGattClient:\tconnectToD2DClient   address:" + r20 + ",clientMAC:" + r21);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:28:?, code lost:
        r19.tSemWifiApSmartCallback = r22;
        r19.mBluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
     */
    /* JADX WARNING: Code restructure failed: missing block: B:29:0x00a0, code lost:
        if (r19.mBluetoothAdapter == null) goto L_0x018f;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:30:0x00a2, code lost:
        if (r20 != null) goto L_0x00a6;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:32:0x00a6, code lost:
        r0 = r19.mBluetoothAdapter.getRemoteDevice(r20);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:33:0x00ad, code lost:
        if (r0 != null) goto L_0x00b8;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:34:0x00af, code lost:
        android.util.Log.e("SemWifiApSmartD2DGattClient", "Device not found. Unable to connect.");
     */
    /* JADX WARNING: Code restructure failed: missing block: B:36:0x00b7, code lost:
        return false;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:38:0x00ba, code lost:
        if (r19.mBleWorkHandler == null) goto L_0x00cd;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:40:0x00c4, code lost:
        if (r19.mBleWorkHandler.hasMessages(13) != false) goto L_0x00cd;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:41:0x00c6, code lost:
        r19.mBleWorkHandler.sendEmptyMessageDelayed(13, com.android.server.wifi.rtt.RttServiceImpl.HAL_RANGING_TIMEOUT_MS);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:43:0x00d4, code lost:
        if (r0.getBondState() != 10) goto L_0x0151;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:44:0x00d6, code lost:
        removeDuplicateClientMAC(r21);
        r11 = r19.mClientD2DList;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:45:0x00db, code lost:
        monitor-enter(r11);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:47:?, code lost:
        r19.mClientD2DList.add(new com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartD2DGattClient.ClientD2DInfo(r19, r21, java.lang.System.currentTimeMillis(), 2));
     */
    /* JADX WARNING: Code restructure failed: missing block: B:48:0x00f6, code lost:
        monitor-exit(r11);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:49:0x00f7, code lost:
        invokeCallback(r21, 2);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:50:0x00fc, code lost:
        if (r19.mBluetoothAdapter == null) goto L_0x0143;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:52:0x0106, code lost:
        if (r19.mBluetoothAdapter.getState() != 10) goto L_0x0143;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:53:0x0108, code lost:
        android.util.Log.d("SemWifiApSmartD2DGattClient", "device is not bonded, enabling BT adapter,mBluetoothIsOn:" + r19.mBluetoothIsOn);
        r19.mLocalLog.log("SemWifiApSmartD2DGattClient:\tdevice is not bonded, enabling BT adapter,mBluetoothIsOn:" + r19.mBluetoothIsOn);
        r19.mBluetoothAdapter.enable();
        r0 = true;
        r19.mBluetoothIsOn = true;
        r19.mBluetoothDevice = r0;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:54:0x0143, code lost:
        r0 = true;
        r19.mPendingDeviceAddress = r0.getAddress();
        r0.createBond(2);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:58:0x0151, code lost:
        r0 = true;
        removeDuplicateClientMAC(r21);
        r12 = r19.mClientD2DList;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:59:0x0157, code lost:
        monitor-enter(r12);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:61:?, code lost:
        r19.mClientD2DList.add(new com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartD2DGattClient.ClientD2DInfo(r19, r21, java.lang.System.currentTimeMillis(), 1));
     */
    /* JADX WARNING: Code restructure failed: missing block: B:62:0x0170, code lost:
        monitor-exit(r12);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:63:0x0171, code lost:
        invokeCallback(r21, 1);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:64:0x0179, code lost:
        if (tryToConnectToRemoteBLE(r0, false) != false) goto L_0x018a;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:65:0x017b, code lost:
        setConnectionState(com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartD2DGattClient.ST_GATT_FAILURE, r19.mD2DClient_MAC);
        r19.mFailedBLEConnections.remove(r0.getAddress());
     */
    /* JADX WARNING: Code restructure failed: missing block: B:67:0x018b, code lost:
        return r0;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:71:0x018f, code lost:
        android.util.Log.e("SemWifiApSmartD2DGattClient", "BluetoothAdapter not initialized or unspecified address.");
     */
    /* JADX WARNING: Code restructure failed: missing block: B:73:0x0197, code lost:
        return false;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:79:0x019e, code lost:
        r0 = th;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:84:0x01a5, code lost:
        r0 = th;
     */
    public boolean connectToSmartD2DClient(String bleaddress, String clientMAC, ISemWifiApSmartCallback bSemWifiApSmartCallback) {
        this.mSemWifiApSmartD2DMHS = WifiInjector.getInstance().getSemWifiApSmartD2DMHS();
        synchronized (this.mD2DConnection) {
            try {
                if (this.mConnectedGatt != null) {
                    Log.e("SemWifiApSmartD2DGattClient", "mConnectedGatt is not null");
                    return false;
                } else if (this.isBondingGoingon) {
                    Log.e("SemWifiApSmartD2DGattClient", "isBondingGoingon is true");
                    return false;
                } else {
                    synchronized (this.mClientD2DList) {
                        for (ClientD2DInfo var : this.mClientD2DList) {
                            if (var.state == 2) {
                                Log.e("SemWifiApSmartD2DGattClient", "BLE pairing is going on, return");
                                return false;
                            }
                        }
                    }
                }
            } catch (Throwable th) {
                th = th;
                throw th;
            }
        }
        while (true) {
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void shutdownclient_1() {
        Log.d("SemWifiApSmartD2DGattClient", "shutdownclient_1");
        BluetoothGatt bluetoothGatt = this.mConnectedGatt;
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
        }
        this.mRequestedToConnect = false;
        this.requestedToAccept = false;
        this.mConnectedGatt = null;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void shutdownclient() {
        Log.d("SemWifiApSmartD2DGattClient", "shutdownclient");
        BluetoothGatt bluetoothGatt = this.mConnectedGatt;
        if (bluetoothGatt != null) {
            this.mFailedBLEConnections.remove(bluetoothGatt.getDevice().getAddress());
            this.mConnectedGatt.close();
            try {
                Thread.sleep(6000);
            } catch (Exception e) {
            }
        }
        this.mRequestedToConnect = false;
        this.requestedToAccept = false;
        this.mConnectedGatt = null;
    }

    /* access modifiers changed from: private */
    public class ClientD2DInfo {
        public String clientMAC;
        public int state;
        public long timestamp;

        public ClientD2DInfo(String mClientMAC, long mTimeStamp, int mState) {
            this.clientMAC = mClientMAC;
            this.timestamp = mTimeStamp;
            this.state = mState;
        }

        public String toString() {
            return String.format("clientMAC:" + this.clientMAC + ",timestamp:" + this.timestamp + ",state:" + this.state, new Object[0]);
        }
    }

    public int getSmartD2DClientConnectedStatus(String clientmac) {
        if (clientmac == null) {
            return 0;
        }
        synchronized (this.mClientD2DList) {
            for (ClientD2DInfo inf : this.mClientD2DList) {
                if (inf.clientMAC != null && inf.clientMAC.equalsIgnoreCase(clientmac)) {
                    Log.d("SemWifiApSmartD2DGattClient", "getSmartD2DClientConnectedStatus clientmac:" + clientmac + ",:state:" + inf.state);
                    if (inf.state < 0 && System.currentTimeMillis() - inf.timestamp > 45000 && this.mBleWorkHandler != null && !this.mBleWorkHandler.hasMessages(13)) {
                        Log.d("SemWifiApSmartD2DGattClient", "getSmartD2DClientConnectedStatus, specialcase:" + inf.clientMAC);
                        LocalLog localLog = this.mLocalLog;
                        localLog.log("SemWifiApSmartD2DGattClient:\tgetSmartD2DClientConnectedStatus, specialcase:" + inf.clientMAC);
                        this.mBleWorkHandler.sendEmptyMessageDelayed(13, 10);
                    }
                    return inf.state;
                }
            }
            return 0;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setConnectionState(int state, String clientmac) {
        Log.d("SemWifiApSmartD2DGattClient", "setConnectionState state " + state + "clientmac" + clientmac);
        LocalLog localLog = this.mLocalLog;
        localLog.log("SemWifiApSmartD2DGattClient:\tsetConnectionState state :" + state + "clientmac" + clientmac);
        if (state == ST_BONDING_FAILURE || state == -2 || state == ST_GATT_FAILURE) {
            this.mSemWifiApSmartD2DMHS.setBLEPairingFailedHistory(clientmac, new Pair<>(Long.valueOf(System.currentTimeMillis()), this.mSmartAp_BLE_MAC));
        }
        updateClientD2DConnectionStatus(clientmac, state);
        ISemWifiApSmartCallback iSemWifiApSmartCallback = this.tSemWifiApSmartCallback;
        if (iSemWifiApSmartCallback != null) {
            try {
                iSemWifiApSmartCallback.onStateChanged(state, clientmac);
            } catch (Exception e) {
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void invokeCallback(String clientmac, int state) {
        Log.d("SemWifiApSmartD2DGattClient", "invokeCallback state " + state + "clientmac" + clientmac);
        LocalLog localLog = this.mLocalLog;
        localLog.log("SemWifiApSmartD2DGattClient:\tsinvokeCallback state :" + state + "clientmac" + clientmac);
        ISemWifiApSmartCallback iSemWifiApSmartCallback = this.tSemWifiApSmartCallback;
        if (iSemWifiApSmartCallback != null) {
            try {
                iSemWifiApSmartCallback.onStateChanged(state, clientmac);
            } catch (Exception e) {
            }
        }
    }

    private void updateClientD2DConnectionStatus(String clientmac, int state) {
        synchronized (this.mClientD2DList) {
            Iterator<ClientD2DInfo> it = this.mClientD2DList.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                ClientD2DInfo inf = it.next();
                if (inf.clientMAC.equalsIgnoreCase(clientmac)) {
                    inf.state = state;
                    Log.d("SemWifiApSmartD2DGattClient", "update state clientmac:" + clientmac + ",state:" + state);
                    inf.timestamp = System.currentTimeMillis();
                    break;
                }
            }
        }
    }

    /* access modifiers changed from: package-private */
    public void showD2DClientInfo() {
        synchronized (this.mClientD2DList) {
            Log.d("SemWifiApSmartD2DGattClient", "================================================");
            this.mLocalLog.log("SemWifiApSmartD2DGattClient:\t================================================");
            Log.d("SemWifiApSmartD2DGattClient", "showing D2D Client states");
            this.mLocalLog.log("SemWifiApSmartD2DGattClient:\tshowing D2D Client states");
            for (ClientD2DInfo var : this.mClientD2DList) {
                Log.d("SemWifiApSmartD2DGattClient", "" + var);
                LocalLog localLog = this.mLocalLog;
                localLog.log("SemWifiApSmartD2DGattClient:\t" + var);
            }
            Log.d("SemWifiApSmartD2DGattClient", "================================================");
            this.mLocalLog.log("SemWifiApSmartD2DGattClient:\t================================================");
        }
    }

    /* access modifiers changed from: package-private */
    public void removeDuplicateClientMAC(String clientmac) {
        synchronized (this.mClientD2DList) {
            int index = -1;
            int count = 0;
            Iterator<ClientD2DInfo> it = this.mClientD2DList.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                } else if (it.next().clientMAC.equalsIgnoreCase(clientmac)) {
                    index = count;
                    break;
                } else {
                    count++;
                }
            }
            if (index != -1) {
                this.mClientD2DList.remove(index);
            }
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
        Log.e("SemWifiApSmartD2DGattClient", "Trying to create a new connection. attempt:" + count2);
        LocalLog localLog = this.mLocalLog;
        localLog.log("SemWifiApSmartD2DGattClient\tTrying to create a new connection. attempt:" + count2);
        setConnectionState(1, this.mD2DClient_MAC);
        this.mConnectedGatt = mDevice.connectGatt(this.mContext, autoConnect, this.mGattCallback, 2);
        if (this.mConnectedGatt == null) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
            Log.e("SemWifiApSmartD2DGattClient", "mConnectedGatt = null, Trying to create a new connection. attempt:" + count2);
            LocalLog localLog2 = this.mLocalLog;
            localLog2.log("SemWifiApSmartD2DGattClient\tmConnectedGatt = null, Trying to create a new connection. attempt:" + count2);
            setConnectionState(1, this.mD2DClient_MAC);
            this.mConnectedGatt = mDevice.connectGatt(this.mContext, true, this.mGattCallback, 2);
            if (this.mConnectedGatt == null) {
                Log.e("SemWifiApSmartD2DGattClient", " mConnectedGatt = null, returning false");
                this.mLocalLog.log("SemWifiApSmartD2DGattClient\tmConnectedGatt = null, returning false");
                return false;
            }
        }
        this.mRequestedToConnect = true;
        return true;
    }
}

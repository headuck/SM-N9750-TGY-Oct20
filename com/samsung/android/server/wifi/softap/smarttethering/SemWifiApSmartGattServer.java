package com.samsung.android.server.wifi.softap.smarttethering;

import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.hardware.display.SemDeviceInfo;
import android.net.Uri;
import android.net.wifi.SemWifiApSmartWhiteList;
import android.net.wifi.WifiManager;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.FactoryTest;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.LocalLog;
import android.util.Log;
import android.widget.Toast;
import com.samsung.android.feature.SemCscFeature;
import com.samsung.android.net.wifi.SemWifiApContentProviderHelper;
import com.samsung.android.server.wifi.dqa.ReportIdKey;
import com.sec.android.app.CscFeatureTagSetting;
import com.sec.android.app.CscFeatureTagWifi;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class SemWifiApSmartGattServer {
    private static final String ACTION_LOGOUT_ACCOUNTS_COMPLETE = "com.samsung.account.SAMSUNGACCOUNT_SIGNOUT_COMPLETED";
    private static final int BLE_PACKET_SIZE_LIMIT_FOR_DEVICE_NAME = 34;
    public static final String CONFIGOPBRANDINGFORMOBILEAP = SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGOPBRANDINGFORMOBILEAP, "ALL");
    private static final String TAG = "SemWifiApSmartGattServer";
    private static final String WIFIAP_WARNING_CLASS = "com.samsung.android.settings.wifi.mobileap.WifiApWarning";
    private static final String WIFIAP_WARNING_DIALOG = "com.samsung.android.settings.wifi.mobileap.wifiapwarning";
    private static final String WIFIAP_WARNING_DIALOG_TYPE = "wifiap_warning_dialog_type";
    private static IntentFilter mSemWifiApSmartGattServerIntentFilter = new IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED");
    private final int COMMAND_ENABLE_HOTSPOT = 2;
    private final int DISPLAY_JOINED_NEW_FAMILYID_TOAST = 5;
    private final int DISPLAY_NO_UPDATE_FAMILYID_TOAST = 4;
    private final int SEND_NOTIFICATION = 8;
    private final int START_HOTSPOT_ENABLED_TIMEOUT_WITHOUT_CLIENT = 9;
    private final int START_HOTSPOT_ENABLED_TIME_WITHOUT_CLIENT = 60000;
    private final int START_HOTSPOT_ENABLING_TIME = 60000;
    private final int START_HOTSPOT_ENABLING_TIMEOUT = 1;
    private final int STORE_BONDED_ADDRESS = 6;
    private final int WAIT_ACCEPT_INVITATION = 7;
    private HashSet<String> bonedDevicesFromHotspotLive = new HashSet<>();
    private boolean isAutoHotspotServerSet;
    private boolean isJDMDevice = "in_house".contains("jdm");
    private boolean isMHSEnabledSmartly = false;
    private boolean isMHSEnabledViaIntent = false;
    private boolean isWaitingForAcceptStatus;
    private boolean isWaitingForMHSStatus;
    HashMap<String, Integer> mAuthDevices = new HashMap<>();
    private BleWorkHandler mBleWorkHandler = null;
    private HandlerThread mBleWorkThread = null;
    private boolean mBluetoothIsOn = false;
    private BluetoothManager mBluetoothManager;
    private String mBondingAddress;
    HashMap<String, ClientVer> mClientConnections = new HashMap<>();
    private Context mContext;
    private String mFamilyID;
    public BluetoothGattServer mGattServer;
    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        /* class com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartGattServer.C08131 */

        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            Log.d(SemWifiApSmartGattServer.TAG, "onConnectionStateChange " + SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.getStatusDescription(status) + " " + SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.getStateDescription(newState));
            if (newState == 2) {
                SemWifiApSmartGattServer.this.mAuthDevices.put(device.getAddress(), 0);
                SemWifiApSmartGattServer.this.mClientConnections.put(device.getAddress(), new ClientVer());
                Log.d(SemWifiApSmartGattServer.TAG, "connected device:" + device);
                SemWifiApSmartGattServer.this.mBondingAddress = device.getAddress();
                SemWifiApSmartGattServer.this.mVersion = 0;
                SemWifiApSmartGattServer.this.mUserType = -1;
                LocalLog localLog = SemWifiApSmartGattServer.this.mLocalLog;
                localLog.log("SemWifiApSmartGattServer:\tGattServer connected device:" + SemWifiApSmartGattServer.this.mBondingAddress);
            } else if (newState == 0) {
                SemWifiApSmartGattServer.this.mAuthDevices.remove(device.getAddress());
                SemWifiApSmartGattServer.this.mClientConnections.remove(device.getAddress());
                Log.d(SemWifiApSmartGattServer.TAG, "disconnected device:" + device);
                LocalLog localLog2 = SemWifiApSmartGattServer.this.mLocalLog;
                localLog2.log("SemWifiApSmartGattServer:\tGattServer disconnected device:" + device);
                SemWifiApSmartGattServer.this.mBondingAddress = null;
            }
        }

        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            Log.d(SemWifiApSmartGattServer.TAG, "onServiceAdded:" + service.getUuid());
            LocalLog localLog = SemWifiApSmartGattServer.this.mLocalLog;
            localLog.log("SemWifiApSmartGattServer:\tonServiceAdded:" + service.getUuid());
        }

        /* JADX WARNING: Code restructure failed: missing block: B:50:0x027f, code lost:
            if (r5 == 1) goto L_0x0294;
         */
        /* JADX WARNING: Code restructure failed: missing block: B:96:0x04ed, code lost:
            if (r5 == 1) goto L_0x04ef;
         */
        /* JADX WARNING: Removed duplicated region for block: B:82:0x041b  */
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            int i;
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            Log.d(SemWifiApSmartGattServer.TAG, "onCharacteristicReadRequest:: " + SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.lookup(characteristic.getUuid()));
            SemWifiApSmartUtil unused = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_D2D_CLIENT_BOND_STATUS.equals(characteristic.getUuid())) {
                byte[] temp = new byte[10];
                temp[0] = 0;
                if (device.getBondState() == 12) {
                    temp[0] = 1;
                }
                SemWifiApSmartGattServer.this.mGattServer.sendResponse(device, requestId, 0, 0, temp);
                Log.d(SemWifiApSmartGattServer.TAG, "Sent bond status " + ((int) temp[0]));
                return;
            }
            SemWifiApSmartUtil unused2 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_CLIENT_MAC.equals(characteristic.getUuid())) {
                byte[] temp2 = new byte[150];
                if (device.getBondState() == 12) {
                    byte[] tDeviceName = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.getDeviceName().getBytes();
                    byte[] tMac = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.getOwnWifiMac().getBytes();
                    temp2[0] = 0;
                    if (!SemWifiApSmartGattServer.this.isWaitingForAcceptStatus) {
                        temp2[0] = 1;
                    }
                    for (int i2 = 0; i2 < tMac.length; i2++) {
                        temp2[i2 + 1] = tMac[i2];
                    }
                    Log.d(SemWifiApSmartGattServer.TAG, "device length:" + tDeviceName.length);
                    temp2[18] = (byte) tDeviceName.length;
                    int i3 = 0;
                    while (i3 < 34 && i3 < tDeviceName.length) {
                        temp2[i3 + 19] = tDeviceName[i3];
                        i3++;
                    }
                }
                SemWifiApSmartGattServer.this.mGattServer.sendResponse(device, requestId, 0, 0, temp2);
                return;
            }
            SemWifiApSmartUtil unused3 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_MHS_SIDE_GET_TIME.equals(characteristic.getUuid())) {
                Long mtime = Long.valueOf(System.currentTimeMillis());
                byte[] temp3 = null;
                if (mtime == null) {
                    mtime = Long.valueOf(System.currentTimeMillis());
                }
                SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\tsystem time is :" + mtime);
                if (mtime != null) {
                    temp3 = ("" + mtime).getBytes();
                    Log.i(SemWifiApSmartGattServer.TAG, "sending mhs time:" + Arrays.toString(temp3));
                }
                SemWifiApSmartGattServer.this.mGattServer.sendResponse(device, requestId, 0, 0, temp3);
                Log.e(SemWifiApSmartGattServer.TAG, "Sent mhs time " + mtime);
                return;
            }
            SemWifiApSmartUtil unused4 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_MHS_BOND_STATUS.equals(characteristic.getUuid())) {
                byte[] temp4 = new byte[10];
                temp4[0] = 0;
                if (device.getBondState() == 12) {
                    temp4[0] = 1;
                    if (SemWifiApSmartGattServer.this.mBleWorkHandler != null) {
                        Message msg = new Message();
                        msg.what = 6;
                        msg.obj = device.getAddress();
                        if (SemWifiApSmartGattServer.this.mBleWorkHandler != null) {
                            SemWifiApSmartGattServer.this.mBleWorkHandler.sendMessage(msg);
                        }
                    }
                }
                SemWifiApSmartGattServer.this.mGattServer.sendResponse(device, requestId, 0, 0, temp4);
                Log.d(SemWifiApSmartGattServer.TAG, "Sent bond status " + ((int) temp4[0]));
                return;
            }
            SemWifiApSmartUtil unused5 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_AUTH_STATUS.equals(characteristic.getUuid())) {
                SemWifiApSmartGattServer semWifiApSmartGattServer = SemWifiApSmartGattServer.this;
                semWifiApSmartGattServer.mSSID = semWifiApSmartGattServer.mSemWifiApSmartUtil.getlegacySSID();
                SemWifiApSmartGattServer semWifiApSmartGattServer2 = SemWifiApSmartGattServer.this;
                semWifiApSmartGattServer2.mPassword = semWifiApSmartGattServer2.mSemWifiApSmartUtil.getlegacyPassword();
                byte[] temp5 = new byte[200];
                Arrays.fill(temp5, (byte) 0);
                SemWifiApSmartGattServer semWifiApSmartGattServer3 = SemWifiApSmartGattServer.this;
                semWifiApSmartGattServer3.mVersion = semWifiApSmartGattServer3.mClientConnections.get(device.getAddress()).mVersion;
                SemWifiApSmartGattServer semWifiApSmartGattServer4 = SemWifiApSmartGattServer.this;
                semWifiApSmartGattServer4.mUserType = semWifiApSmartGattServer4.mClientConnections.get(device.getAddress()).mUserType;
                String tAESKey = SemWifiApSmartGattServer.this.mClientConnections.get(device.getAddress()).mAESKey;
                int bonded_state = device.getBondState();
                if (bonded_state != 12) {
                    int i4 = SemWifiApSmartGattServer.this.mVersion;
                    SemWifiApSmartUtil unused6 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
                    if (i4 == 1) {
                        int i5 = SemWifiApSmartGattServer.this.mUserType;
                        SemWifiApSmartUtil unused7 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
                    }
                    Log.e(SemWifiApSmartGattServer.TAG, "client device is not bonded");
                    SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\tclient device is not bonded");
                    Log.d(SemWifiApSmartGattServer.TAG, "Sent Auth status " + ((int) temp5[0]) + ",mVersion:" + SemWifiApSmartGattServer.this.mVersion + ",mUserType:" + SemWifiApSmartGattServer.this.mUserType + ",bonded_state:" + bonded_state);
                    LocalLog localLog = SemWifiApSmartGattServer.this.mLocalLog;
                    StringBuilder sb = new StringBuilder();
                    sb.append("SemWifiApSmartGattServer:\tSent Auth status ");
                    sb.append((int) temp5[0]);
                    sb.append(",bonded_state:");
                    sb.append(bonded_state);
                    localLog.log(sb.toString());
                    i = SemWifiApSmartGattServer.this.mVersion;
                    SemWifiApSmartUtil unused8 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
                    if (i == 1) {
                        int i6 = SemWifiApSmartGattServer.this.mUserType;
                        SemWifiApSmartUtil unused9 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
                        if (i6 == 1) {
                            Log.d(SemWifiApSmartGattServer.TAG, "Using AES:" + tAESKey);
                            temp5 = AES.encrypt(new String(temp5), tAESKey).getBytes();
                            if (temp5 == null) {
                                Log.e(SemWifiApSmartGattServer.TAG, " Encryption can't be null");
                            }
                            Log.d(SemWifiApSmartGattServer.TAG, " Encryption length:" + temp5.length);
                        }
                    }
                    SemWifiApSmartGattServer.this.mGattServer.sendResponse(device, requestId, 0, 0, temp5);
                    return;
                }
                temp5[0] = (byte) SemWifiApSmartGattServer.this.mAuthDevices.get(device.getAddress()).intValue();
                if (temp5[0] == 1) {
                    int mSSIDLength = 0;
                    if (SemWifiApSmartGattServer.this.mSSID != null) {
                        mSSIDLength = SemWifiApSmartGattServer.this.mSSID.getBytes().length;
                    }
                    int mPasswordLength = 0;
                    if (SemWifiApSmartGattServer.this.mPassword != null) {
                        mPasswordLength = SemWifiApSmartGattServer.this.mPassword.getBytes().length;
                    }
                    String mWifiMAC = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.getOwnWifiMac().toLowerCase().substring(9);
                    Log.e(SemWifiApSmartGattServer.TAG, "mWifiMAC:" + mWifiMAC);
                    byte[] mWifiMACBytes = mWifiMAC.getBytes();
                    for (int t = 0; t < mWifiMACBytes.length; t++) {
                        temp5[mSSIDLength + 3 + mPasswordLength + 1 + t] = mWifiMACBytes[t];
                    }
                    temp5[1] = (byte) mSSIDLength;
                    for (int i7 = 0; i7 < mSSIDLength; i7++) {
                        temp5[i7 + 2] = SemWifiApSmartGattServer.this.mSSID.getBytes()[i7];
                    }
                    temp5[mSSIDLength + 2] = (byte) mPasswordLength;
                    for (int i8 = 0; i8 < mPasswordLength; i8++) {
                        temp5[i8 + 3 + mSSIDLength] = SemWifiApSmartGattServer.this.mPassword.getBytes()[i8];
                    }
                    temp5[mSSIDLength + 3 + mPasswordLength] = 0;
                    if (((WifiManager) SemWifiApSmartGattServer.this.mContext.getSystemService("wifi")).getWifiApState() == 13) {
                        temp5[mSSIDLength + 3 + mPasswordLength] = 1;
                    }
                    temp5[mSSIDLength + 3 + mPasswordLength + 1 + 8] = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.getSecurityType();
                } else if (temp5[0] == 0) {
                    String mWifiMAC2 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.getOwnWifiMac().toLowerCase().substring(9);
                    Log.e(SemWifiApSmartGattServer.TAG, "mWifiMAC:" + mWifiMAC2);
                    byte[] mWifiMACBytes2 = mWifiMAC2.getBytes();
                    for (int t2 = 0; t2 < mWifiMACBytes2.length; t2++) {
                        temp5[t2 + 1] = mWifiMACBytes2[t2];
                    }
                }
                Log.d(SemWifiApSmartGattServer.TAG, "Sent Auth status " + ((int) temp5[0]) + ",mVersion:" + SemWifiApSmartGattServer.this.mVersion + ",mUserType:" + SemWifiApSmartGattServer.this.mUserType + ",bonded_state:" + bonded_state);
                LocalLog localLog2 = SemWifiApSmartGattServer.this.mLocalLog;
                StringBuilder sb2 = new StringBuilder();
                sb2.append("SemWifiApSmartGattServer:\tSent Auth status ");
                sb2.append((int) temp5[0]);
                sb2.append(",bonded_state:");
                sb2.append(bonded_state);
                localLog2.log(sb2.toString());
                i = SemWifiApSmartGattServer.this.mVersion;
                SemWifiApSmartUtil unused82 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
                if (i == 1) {
                }
                SemWifiApSmartGattServer.this.mGattServer.sendResponse(device, requestId, 0, 0, temp5);
                return;
            }
            SemWifiApSmartUtil unused10 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_MHS_STATUS_UUID.equals(characteristic.getUuid())) {
                WifiManager wifiManager = (WifiManager) SemWifiApSmartGattServer.this.mContext.getSystemService("wifi");
                byte[] temp6 = new byte[50];
                temp6[0] = 0;
                int mhsChannel = 0;
                SemWifiApSmartGattServer semWifiApSmartGattServer5 = SemWifiApSmartGattServer.this;
                semWifiApSmartGattServer5.mVersion = semWifiApSmartGattServer5.mClientConnections.get(device.getAddress()).mVersion;
                SemWifiApSmartGattServer semWifiApSmartGattServer6 = SemWifiApSmartGattServer.this;
                semWifiApSmartGattServer6.mUserType = semWifiApSmartGattServer6.mClientConnections.get(device.getAddress()).mUserType;
                String str = SemWifiApSmartGattServer.this.mClientConnections.get(device.getAddress()).mAESKey;
                if (device.getBondState() != 12) {
                    int i9 = SemWifiApSmartGattServer.this.mVersion;
                    SemWifiApSmartUtil unused11 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
                    if (i9 == 1) {
                        int i10 = SemWifiApSmartGattServer.this.mUserType;
                        SemWifiApSmartUtil unused12 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
                    }
                    SemWifiApSmartGattServer.this.mGattServer.sendResponse(device, requestId, 0, 0, temp6);
                    Log.d(SemWifiApSmartGattServer.TAG, "Sent MHS status " + ((int) temp6[0]) + ", mhsChannel:" + mhsChannel);
                    SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\tSent MHS status " + ((int) temp6[0]) + ", mhsChannel:" + mhsChannel);
                }
                wifiManager.getWifiApState();
                if (SemWifiApSmartGattServer.this.isMHSEnabledViaIntent) {
                    temp6[0] = 1;
                    mhsChannel = wifiManager.semGetWifiApChannel();
                    int mhsChannelStrLength = 0;
                    if (mhsChannel >= 1) {
                        String mhsChannelStr = "" + mhsChannel;
                        mhsChannelStrLength = mhsChannelStr.length();
                        temp6[1] = (byte) mhsChannelStrLength;
                        for (int i11 = 0; i11 < mhsChannelStrLength; i11++) {
                            temp6[i11 + 2] = mhsChannelStr.getBytes()[i11];
                        }
                    } else {
                        temp6[1] = 0;
                    }
                    String clientMAC = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.getOwnWifiMac();
                    String mhsMAC = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.getMHSMacFromInterface();
                    int clientMACLength = 0;
                    int mhsMACLength = 0;
                    if (mhsMAC != null) {
                        mhsMACLength = mhsMAC.length();
                    }
                    if (clientMAC != null) {
                        clientMACLength = clientMAC.length();
                    }
                    temp6[mhsChannelStrLength + 2] = (byte) clientMACLength;
                    for (int i12 = 0; i12 < clientMACLength; i12++) {
                        temp6[mhsChannelStrLength + 2 + 1 + i12] = clientMAC.getBytes()[i12];
                    }
                    temp6[mhsChannelStrLength + 3 + clientMACLength] = (byte) mhsMACLength;
                    for (int i13 = 0; i13 < mhsMACLength; i13++) {
                        temp6[clientMACLength + 4 + mhsChannelStrLength + i13] = mhsMAC.getBytes()[i13];
                    }
                }
                SemWifiApSmartGattServer.this.mGattServer.sendResponse(device, requestId, 0, 0, temp6);
                Log.d(SemWifiApSmartGattServer.TAG, "Sent MHS status " + ((int) temp6[0]) + ", mhsChannel:" + mhsChannel);
                SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\tSent MHS status " + ((int) temp6[0]) + ", mhsChannel:" + mhsChannel);
            }
        }

        /* JADX WARNING: Code restructure failed: missing block: B:143:0x0480, code lost:
            if (r3 == 1) goto L_0x048d;
         */
        /* JADX WARNING: Removed duplicated region for block: B:116:0x03e9  */
        /* JADX WARNING: Removed duplicated region for block: B:118:0x03ec  */
        /* JADX WARNING: Removed duplicated region for block: B:124:0x03f9  */
        /* JADX WARNING: Removed duplicated region for block: B:130:0x040a  */
        /* JADX WARNING: Removed duplicated region for block: B:137:0x0431  */
        /* JADX WARNING: Removed duplicated region for block: B:140:0x0467  */
        /* JADX WARNING: Removed duplicated region for block: B:146:0x048f  */
        /* JADX WARNING: Removed duplicated region for block: B:194:0x07ef  */
        /* JADX WARNING: Removed duplicated region for block: B:87:0x02a4  */
        /* JADX WARNING: Removed duplicated region for block: B:92:0x02c1  */
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            byte[] temp;
            int rLength;
            boolean isValid;
            boolean isValid2;
            String mlistMAC;
            String mlistMAC2;
            String mlistMAC3;
            String mlistMAC4;
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            Log.d(SemWifiApSmartGattServer.TAG, "onCharacteristicWriteRequest:" + SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.lookup(characteristic.getUuid()));
            SemWifiApSmartUtil unused = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_FAMILY_ID.equals(characteristic.getUuid())) {
                boolean isValid3 = true;
                int rValueLength = value.length;
                byte b = 0;
                byte b2 = 0;
                if (rValueLength == 0) {
                    isValid3 = false;
                }
                if (isValid3) {
                    b = value[0];
                }
                if (isValid3 && rValueLength < b + 2) {
                    isValid3 = false;
                }
                if (isValid3) {
                    b2 = value[b + 1];
                }
                if (!isValid3 || rValueLength >= b2 + b + 17 + 2) {
                    isValid2 = isValid3;
                } else {
                    isValid2 = false;
                }
                Log.e(SemWifiApSmartGattServer.TAG, "family ID valid:" + isValid2 + ",rValueLength:" + rValueLength + ",rDeviceNamelength:" + ((int) b2));
                if (device.getBondState() == 12 && isValid2) {
                    String mD2DFamilyID = new String(value, 1, (int) value[0]);
                    String mD2DDeviceName = new String(value, value[0] + 2, (int) value[value[0] + 1]);
                    String mD2DDeviceWiFiMAC = new String(value, value[0] + value[value[0] + 1] + 3, 17);
                    long mD2DHashFamily = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.generateHashKey(mD2DFamilyID);
                    String mD2DDeviceWiFiMAC2 = mD2DDeviceWiFiMAC.toLowerCase();
                    String mlistMAC5 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.getD2DWifiMac();
                    SemWifiApSmartGattServer.this.isWaitingForAcceptStatus = false;
                    boolean isTrue = false;
                    if (!TextUtils.isEmpty(mlistMAC5)) {
                        isTrue = Arrays.asList(mlistMAC5.split("\n")).contains(mD2DDeviceWiFiMAC2.substring(9));
                    }
                    String mExisting = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.getD2DFamilyID();
                    long ident = Binder.clearCallingIdentity();
                    try {
                        if (SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.getSamsungAccountCount() != 0) {
                            try {
                                Log.d(SemWifiApSmartGattServer.TAG, " device logged in with samsung account, so D2DFamilyID will not be saved");
                                SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer\t:device logged in with samsung account, so D2DFamilyID will not be saved ");
                                SemWifiApSmartGattServer.this.isWaitingForAcceptStatus = true;
                                mlistMAC = mlistMAC5;
                            } catch (Throwable th) {
                                th = th;
                                Binder.restoreCallingIdentity(ident);
                                throw th;
                            }
                        } else {
                            if (mExisting != null) {
                                try {
                                    if (mExisting.isEmpty()) {
                                        mlistMAC2 = mlistMAC5;
                                    } else if (isTrue || !mExisting.equals(mD2DFamilyID)) {
                                        mlistMAC = mlistMAC5;
                                        try {
                                            if (!mExisting.equals(mD2DFamilyID)) {
                                                Intent intent = new Intent();
                                                intent.setClassName("com.android.settings", SemWifiApSmartGattServer.WIFIAP_WARNING_CLASS);
                                                intent.setFlags(268435456);
                                                intent.setAction("com.samsung.android.settings.wifi.mobileap.wifiapwarning");
                                                intent.putExtra("wifiap_warning_dialog_type", 43);
                                                intent.putExtra("mD2DFamilyID", mD2DFamilyID);
                                                intent.putExtra("mD2DDeviceName", mD2DDeviceName);
                                                intent.putExtra("mD2DHashFamily", mD2DHashFamily);
                                                intent.putExtra("mD2DDeviceWiFiMAC", mD2DDeviceWiFiMAC2);
                                                SemWifiApSmartGattServer.this.mContext.startActivity(intent);
                                                Log.d(SemWifiApSmartGattServer.TAG, "D2D Family dialog");
                                                SemWifiApSmartGattServer.this.isWaitingForAcceptStatus = true;
                                            } else if (SemWifiApSmartGattServer.this.mBleWorkHandler != null) {
                                                SemWifiApSmartGattServer.this.mBleWorkHandler.sendEmptyMessage(4);
                                            }
                                        } catch (Throwable th2) {
                                            th = th2;
                                            Binder.restoreCallingIdentity(ident);
                                            throw th;
                                        }
                                    } else {
                                        if (mlistMAC5 == null) {
                                            mlistMAC4 = mD2DDeviceWiFiMAC2.substring(9);
                                        } else {
                                            mlistMAC4 = mlistMAC5 + "\n" + mD2DDeviceWiFiMAC2.substring(9);
                                        }
                                        Log.d(SemWifiApSmartGattServer.TAG, "added D2D AutoHotspot MAC2:" + mlistMAC4);
                                        SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.putD2DWifiMac(mlistMAC4);
                                        if (SemWifiApSmartGattServer.this.mBleWorkHandler != null) {
                                            SemWifiApSmartGattServer.this.mBleWorkHandler.sendEmptyMessage(4);
                                        }
                                        Binder.restoreCallingIdentity(ident);
                                        if (!SemWifiApSmartGattServer.this.isWaitingForAcceptStatus && SemWifiApSmartGattServer.this.mBleWorkHandler != null) {
                                            SemWifiApSmartGattServer.this.mBleWorkHandler.sendEmptyMessageDelayed(7, 30000);
                                        } else if (SemWifiApSmartGattServer.this.mBleWorkHandler != null) {
                                            SemWifiApSmartGattServer.this.mBleWorkHandler.sendEmptyMessageDelayed(8, 100);
                                        }
                                    }
                                } catch (Throwable th3) {
                                    th = th3;
                                    Binder.restoreCallingIdentity(ident);
                                    throw th;
                                }
                            } else {
                                mlistMAC2 = mlistMAC5;
                            }
                            try {
                                SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.putD2DFamilyID(mD2DFamilyID);
                                SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.putHashbasedonD2DFamilyid(mD2DHashFamily);
                                Intent tintent = new Intent();
                                tintent.setAction("com.samsung.android.server.wifi.softap.smarttethering.d2dfamilyid");
                                SemWifiApSmartGattServer.this.mContext.sendBroadcast(tintent);
                                if (SemWifiApSmartGattServer.this.mBleWorkHandler != null) {
                                    try {
                                        SemWifiApSmartGattServer.this.mBleWorkHandler.sendEmptyMessage(5);
                                    } catch (Throwable th4) {
                                        th = th4;
                                        Binder.restoreCallingIdentity(ident);
                                        throw th;
                                    }
                                }
                                if (!isTrue) {
                                    if (mlistMAC2 == null) {
                                        mlistMAC3 = mD2DDeviceWiFiMAC2.substring(9);
                                    } else {
                                        mlistMAC3 = mlistMAC2 + "\n" + mD2DDeviceWiFiMAC2.substring(9);
                                    }
                                    try {
                                        Log.d(SemWifiApSmartGattServer.TAG, "added D2D AutoHotspot MAC1:" + mlistMAC3);
                                        SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.putD2DWifiMac(mlistMAC3);
                                    } catch (Throwable th5) {
                                        th = th5;
                                        Binder.restoreCallingIdentity(ident);
                                        throw th;
                                    }
                                }
                                Binder.restoreCallingIdentity(ident);
                                if (!SemWifiApSmartGattServer.this.isWaitingForAcceptStatus) {
                                }
                                if (SemWifiApSmartGattServer.this.mBleWorkHandler != null) {
                                }
                            } catch (Throwable th6) {
                                th = th6;
                                Binder.restoreCallingIdentity(ident);
                                throw th;
                            }
                        }
                        Binder.restoreCallingIdentity(ident);
                        if (!SemWifiApSmartGattServer.this.isWaitingForAcceptStatus) {
                        }
                        if (SemWifiApSmartGattServer.this.mBleWorkHandler != null) {
                        }
                    } catch (Throwable th7) {
                        th = th7;
                        Binder.restoreCallingIdentity(ident);
                        throw th;
                    }
                }
                SemWifiApSmartGattServer.this.mGattServer.sendResponse(device, requestId, 0, 0, value);
                return;
            }
            SemWifiApSmartUtil unused2 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_MHS_VER_UPDATE.equals(characteristic.getUuid())) {
                SemWifiApSmartGattServer.this.mClientConnections.get(device.getAddress()).mVersion = value[0];
                SemWifiApSmartGattServer.this.mClientConnections.get(device.getAddress()).mUserType = value[1];
                SemWifiApSmartGattServer.this.mClientConnections.get(device.getAddress()).mAESKey = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.getAESKey(System.currentTimeMillis());
                SemWifiApSmartGattServer.this.mGattServer.sendResponse(device, requestId, 0, 0, value);
                return;
            }
            SemWifiApSmartUtil unused3 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
            if (SemWifiApSmartUtil.CHARACTERISTIC_ENCRYPTED_AUTH_ID.equals(characteristic.getUuid())) {
                SemWifiApSmartGattServer semWifiApSmartGattServer = SemWifiApSmartGattServer.this;
                semWifiApSmartGattServer.mVersion = semWifiApSmartGattServer.mClientConnections.get(device.getAddress()).mVersion;
                SemWifiApSmartGattServer semWifiApSmartGattServer2 = SemWifiApSmartGattServer.this;
                semWifiApSmartGattServer2.mUserType = semWifiApSmartGattServer2.mClientConnections.get(device.getAddress()).mUserType;
                String tAESKey = SemWifiApSmartGattServer.this.mClientConnections.get(device.getAddress()).mAESKey;
                int i = SemWifiApSmartGattServer.this.mVersion;
                SemWifiApSmartUtil unused4 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
                if (i == 1) {
                    int i2 = SemWifiApSmartGattServer.this.mUserType;
                    SemWifiApSmartUtil unused5 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
                    if (i2 == 1) {
                        Log.d(SemWifiApSmartGattServer.TAG, "Using AES:" + tAESKey);
                        temp = AES.decrypt(new String(value), tAESKey).getBytes();
                        if (temp == null) {
                            Log.e(SemWifiApSmartGattServer.TAG, " decryption can't be null");
                        }
                        rLength = temp.length;
                        isValid = true;
                        byte b3 = 0;
                        byte b4 = 0;
                        byte b5 = 0;
                        if (rLength < 4) {
                            isValid = false;
                        }
                        if (isValid) {
                            b5 = temp[1];
                        }
                        if (isValid && rLength < b5 + 3) {
                            isValid = false;
                        }
                        if (isValid) {
                            b3 = temp[b5 + 2];
                        }
                        if (isValid && rLength < b3 + b5 + 4) {
                            isValid = false;
                        }
                        if (isValid) {
                            b4 = temp[b5 + 2 + 1 + b3];
                        }
                        if (isValid && rLength < b3 + b5 + 4 + b4) {
                            isValid = false;
                        }
                        if (Settings.Secure.getInt(SemWifiApSmartGattServer.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 0) == 0) {
                            SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\tAutoHotspot switch is OFF, so making auth 0");
                            Log.d(SemWifiApSmartGattServer.TAG, "AutoHotspot switch is OFF, so making auth 0");
                            SemWifiApSmartGattServer.this.mAuthDevices.put(device.getAddress(), 0);
                            isValid = false;
                        }
                        Log.e(SemWifiApSmartGattServer.TAG, "AuthID valid:" + isValid);
                        if (device.getBondState() != 12) {
                            int i3 = SemWifiApSmartGattServer.this.mVersion;
                            SemWifiApSmartUtil unused6 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
                            if (i3 == 1) {
                                int i4 = SemWifiApSmartGattServer.this.mUserType;
                                SemWifiApSmartUtil unused7 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
                            }
                            SemWifiApSmartGattServer.this.mGattServer.sendResponse(device, requestId, 0, 0, temp);
                            Log.d(SemWifiApSmartGattServer.TAG, "reveived Auth: " + ((int) temp[0]) + ",device.getBondState():" + device.getBondState());
                            SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer\treveived Auth: " + ((int) temp[0]) + ",device.getBondState():" + device.getBondState());
                            return;
                        }
                        if (!isValid) {
                            SemWifiApSmartGattServer.this.mUsertype = temp[0];
                            byte b6 = temp[1];
                            String mString = new String(temp, 2, (int) b6);
                            SemWifiApSmartGattServer.this.mAuthDevices.put(device.getAddress(), 0);
                            byte b7 = temp[b6 + 2];
                            new String(temp, b6 + 2 + 1, (int) b7);
                            byte b8 = temp[b6 + 2 + 1 + b7];
                            String mRemoteDeviceWifiMAC = new String(temp, b6 + 2 + 1 + b7 + 1, (int) b8);
                            int i5 = SemWifiApSmartGattServer.this.mUsertype;
                            SemWifiApSmartUtil unused8 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
                            if (i5 == 1) {
                                SemWifiApSmartGattServer semWifiApSmartGattServer3 = SemWifiApSmartGattServer.this;
                                semWifiApSmartGattServer3.mGuid = semWifiApSmartGattServer3.mSemWifiApSmartUtil.getGuid();
                                if (SemWifiApSmartGattServer.this.mGuid != null) {
                                    SemWifiApSmartGattServer.this.mAuthDevices.put(device.getAddress(), Integer.valueOf(SemWifiApSmartGattServer.this.mGuid.equals(mString) ? 1 : 0));
                                }
                                SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\t same user ,device:" + device.getAddress() + ",mGuid:" + SemWifiApSmartGattServer.this.mGuid + ",Remote mGuid:" + mString);
                                Log.d(SemWifiApSmartGattServer.TAG, "SAME_User device:" + device.getAddress() + ",mGuid:" + SemWifiApSmartGattServer.this.mGuid + ",Remote mGuid:" + mString);
                            } else {
                                int i6 = SemWifiApSmartGattServer.this.mUsertype;
                                SemWifiApSmartUtil unused9 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
                                if (i6 == 2) {
                                    SemWifiApSmartGattServer semWifiApSmartGattServer4 = SemWifiApSmartGattServer.this;
                                    semWifiApSmartGattServer4.mFamilyID = semWifiApSmartGattServer4.mSemWifiApSmartUtil.getFamilyID();
                                    byte b9 = temp[b6 + 2 + 1 + b7 + 1 + b8];
                                    String mRemoteGuid = new String(temp, b6 + 2 + 1 + b7 + 1 + b8 + 1, (int) b9);
                                    Log.i(SemWifiApSmartGattServer.TAG, "mRemoteGuidLength:" + mRemoteGuid + "," + ((int) b9));
                                    if (SemWifiApSmartGattServer.this.mFamilyID != null) {
                                        SemWifiApSmartGattServer.this.mAuthDevices.put(device.getAddress(), Integer.valueOf((!SemWifiApSmartGattServer.this.mFamilyID.equals(mString) || !SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.validateGuidInFamilyUsers(mRemoteGuid)) ? 0 : 1));
                                    }
                                    int val = Settings.Secure.getInt(SemWifiApSmartGattServer.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings_with_family", 0);
                                    SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\t same family   device:" + device.getAddress() + "Family:" + val + ",mFamilyID:" + SemWifiApSmartGattServer.this.mFamilyID + ",Remote family id:" + mString);
                                    if (val == 0) {
                                        SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\tfamily is not supported, so making auth 0");
                                        Log.d(SemWifiApSmartGattServer.TAG, "family is not supported, so making auth 0");
                                        SemWifiApSmartGattServer.this.mAuthDevices.put(device.getAddress(), 0);
                                    }
                                } else {
                                    int i7 = SemWifiApSmartGattServer.this.mUsertype;
                                    SemWifiApSmartUtil unused10 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
                                    if (i7 == 3) {
                                        SemWifiApSmartGattServer semWifiApSmartGattServer5 = SemWifiApSmartGattServer.this;
                                        semWifiApSmartGattServer5.mFamilyID = semWifiApSmartGattServer5.mSemWifiApSmartUtil.getFamilyID();
                                        boolean isFamilyIDSame = false;
                                        if (SemWifiApSmartGattServer.this.mFamilyID != null) {
                                            isFamilyIDSame = SemWifiApSmartGattServer.this.mFamilyID.equals(mString);
                                        }
                                        boolean isInWhiteList = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.verifyInSmartApWhiteList(mRemoteDeviceWifiMAC.toLowerCase());
                                        if (SemWifiApSmartGattServer.this.mFamilyID != null) {
                                            SemWifiApSmartGattServer.this.mAuthDevices.put(device.getAddress(), Integer.valueOf((!isInWhiteList || !isFamilyIDSame) ? 0 : 1));
                                        }
                                        int val2 = Settings.Secure.getInt(SemWifiApSmartGattServer.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings_with_family", 0);
                                        SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\t same allowed user   device:" + device.getAddress() + "Family:" + val2 + ",mFamilyID:" + SemWifiApSmartGattServer.this.mFamilyID + ",Remote family id:" + mString + ",isInWhiteList" + isInWhiteList);
                                        Log.d(SemWifiApSmartGattServer.TAG, " same allowed user   device:" + device.getAddress() + "Family:" + val2 + ",mFamilyID:" + SemWifiApSmartGattServer.this.mFamilyID + ",Remote family id:" + mString + ",isInWhiteList" + isInWhiteList);
                                        if (val2 == 0) {
                                            SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\tfamily is not supported, so making auth 0");
                                            Log.d(SemWifiApSmartGattServer.TAG, "family is not supported, so making auth 0");
                                            SemWifiApSmartGattServer.this.mAuthDevices.put(device.getAddress(), 0);
                                        }
                                    }
                                }
                            }
                            if (SemWifiApSmartGattServer.this.mAuthDevices.get(device.getAddress()).intValue() == 1) {
                                int state = ((WifiManager) SemWifiApSmartGattServer.this.mContext.getSystemService("wifi")).getWifiApState();
                                int interval = 0;
                                if (state == 10) {
                                    interval = ReportIdKey.ID_AUTOWIFI_ILLEGAL_STATE;
                                }
                                Log.d(SemWifiApSmartGattServer.TAG, "Enabling Hotspot state: " + state + " interval " + interval);
                                SemWifiApSmartGattServer.this.mSemWifiApSmartUtil.SetUserTypefromGattServer(SemWifiApSmartGattServer.this.mUsertype);
                                if (SemWifiApSmartGattServer.this.mBleWorkHandler != null) {
                                    SemWifiApSmartGattServer.this.mBleWorkHandler.sendEmptyMessageDelayed(2, (long) interval);
                                }
                            }
                        }
                        SemWifiApSmartGattServer.this.mGattServer.sendResponse(device, requestId, 0, 0, temp);
                        Log.d(SemWifiApSmartGattServer.TAG, "reveived Auth: " + ((int) temp[0]) + ",device.getBondState():" + device.getBondState());
                        SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer\treveived Auth: " + ((int) temp[0]) + ",device.getBondState():" + device.getBondState());
                        return;
                    }
                }
                temp = value;
                rLength = temp.length;
                isValid = true;
                byte b32 = 0;
                byte b42 = 0;
                byte b52 = 0;
                if (rLength < 4) {
                }
                if (isValid) {
                }
                isValid = false;
                if (isValid) {
                }
                isValid = false;
                if (isValid) {
                }
                isValid = false;
                if (Settings.Secure.getInt(SemWifiApSmartGattServer.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 0) == 0) {
                }
                Log.e(SemWifiApSmartGattServer.TAG, "AuthID valid:" + isValid);
                if (device.getBondState() != 12) {
                }
                if (!isValid) {
                }
                SemWifiApSmartGattServer.this.mGattServer.sendResponse(device, requestId, 0, 0, temp);
                Log.d(SemWifiApSmartGattServer.TAG, "reveived Auth: " + ((int) temp[0]) + ",device.getBondState():" + device.getBondState());
                SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer\treveived Auth: " + ((int) temp[0]) + ",device.getBondState():" + device.getBondState());
                return;
            }
            SemWifiApSmartUtil unused11 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
            if (!SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_MHS_ENABLED.equals(characteristic.getUuid())) {
                SemWifiApSmartUtil unused12 = SemWifiApSmartGattServer.this.mSemWifiApSmartUtil;
                if (SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_ACCEPT_INVITATION.equals(characteristic.getUuid()) && responseNeeded) {
                    SemWifiApSmartGattServer.this.mGattServer.sendResponse(device, requestId, 0, 0, value);
                }
            } else if (responseNeeded) {
                SemWifiApSmartGattServer.this.mGattServer.sendResponse(device, requestId, 0, 0, value);
            }
        }

        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
            Log.d(SemWifiApSmartGattServer.TAG, "Our gatt server onMtuChanged. " + mtu);
            LocalLog localLog = SemWifiApSmartGattServer.this.mLocalLog;
            localLog.log("SemWifiApSmartGattServer:\tOur gatt server onMtuChanged. " + mtu);
        }
    };
    public BluetoothGattService mGattService = null;
    private String mGuid;
    private boolean mIsNotClientConnected;
    private LocalLog mLocalLog;
    private NotificationManager mNotificationManager;
    private String mPassword;
    private Intent mPenditIntent;
    private String[] mProvisionApp;
    private String mSSID;
    private SemWifiApSmartGattServerBroadcastReceiver mSemWifiApSmartGattServerBroadcastReceiver;
    private SemWifiApSmartUtil mSemWifiApSmartUtil;
    private Set<String> mTempSynchronized = new HashSet();
    private final String mTetheringProvisionApp = SemCscFeature.getInstance().getString(CscFeatureTagSetting.TAG_CSCFEATURE_SETTING_CONFIGMOBILEHOTSPOTPROVISIONAPP);
    private int mUserType;
    private int mUsertype;
    private int mVersion;
    private WifiAwareManager mWifiAwareManager = null;
    private WifiP2pManager mWifiP2pManager = null;

    static {
        mSemWifiApSmartGattServerIntentFilter.addAction("com.samsung.android.net.wifi.WIFI_AP_STA_STATUS_CHANGED");
        mSemWifiApSmartGattServerIntentFilter.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
        mSemWifiApSmartGattServerIntentFilter.addAction("com.samsung.bluetooth.adapter.action.BLE_STATE_CHANGED");
        mSemWifiApSmartGattServerIntentFilter.addAction("android.bluetooth.device.action.PAIRING_REQUEST");
        mSemWifiApSmartGattServerIntentFilter.addAction(ACTION_LOGOUT_ACCOUNTS_COMPLETE);
        mSemWifiApSmartGattServerIntentFilter.addAction("android.bluetooth.device.action.BOND_STATE_CHANGED");
        mSemWifiApSmartGattServerIntentFilter.addAction("com.samsung.android.server.wifi.softap.smarttethering.AcceptPopUp");
        mSemWifiApSmartGattServerIntentFilter.addAction("com.samsung.android.net.wifi.WIFI_DIALOG_CANCEL_ACTION");
        mSemWifiApSmartGattServerIntentFilter.setPriority(ReportIdKey.ID_PATTERN_MATCHED);
    }

    public SemWifiApSmartGattServer(Context context, SemWifiApSmartUtil tSemWifiApSmartUtil, LocalLog obj) {
        this.mContext = context;
        this.mSemWifiApSmartUtil = tSemWifiApSmartUtil;
        this.mSemWifiApSmartGattServerBroadcastReceiver = new SemWifiApSmartGattServerBroadcastReceiver();
        if (!FactoryTest.isFactoryBinary()) {
            this.mContext.registerReceiver(this.mSemWifiApSmartGattServerBroadcastReceiver, mSemWifiApSmartGattServerIntentFilter);
        } else {
            Log.e(TAG, "This devices's binary is a factory binary");
        }
        this.mLocalLog = obj;
    }

    public void handleBootCompleted() {
        this.mBleWorkThread = new HandlerThread("SemWifiApSmartGattServerHandler");
        this.mBleWorkThread.start();
        this.mBleWorkHandler = new BleWorkHandler(this.mBleWorkThread.getLooper());
        String tlist = Settings.Secure.getString(this.mContext.getContentResolver(), "bonded_device_mhsside");
        if (tlist != null) {
            for (String str : tlist.split("\n")) {
                this.bonedDevicesFromHotspotLive.add(str);
            }
        }
    }

    class SemWifiApSmartGattServerBroadcastReceiver extends BroadcastReceiver {
        SemWifiApSmartGattServerBroadcastReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (intent.getAction().equals("com.samsung.android.server.wifi.softap.smarttethering.AcceptPopUp")) {
                if (SemWifiApSmartGattServer.this.isWaitingForAcceptStatus) {
                    Boolean isAccepted = Boolean.valueOf(intent.getBooleanExtra("accepted", false));
                    Log.d(SemWifiApSmartGattServer.TAG, "Accepted popup:" + isAccepted);
                    if (isAccepted.booleanValue()) {
                        SemWifiApSmartGattServer.this.isWaitingForAcceptStatus = false;
                    }
                    if (SemWifiApSmartGattServer.this.mBleWorkHandler != null) {
                        SemWifiApSmartGattServer.this.mBleWorkHandler.removeMessages(7);
                    }
                    SemWifiApSmartGattServer.this.notifyConnectedDevices(SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_ACCEPT_INVITATION);
                }
            } else if (intent.getAction().equals("android.bluetooth.device.action.BOND_STATE_CHANGED")) {
                BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
                switch (intent.getIntExtra("android.bluetooth.device.extra.BOND_STATE", 10)) {
                    case 10:
                        if (SemWifiApSmartGattServer.this.mBondingAddress != null && device.getAddress().equals(SemWifiApSmartGattServer.this.mBondingAddress)) {
                            SemWifiApSmartGattServer.this.mBondingAddress = null;
                            LocalLog localLog = SemWifiApSmartGattServer.this.mLocalLog;
                            localLog.log("SemWifiApSmartGattServer:\tBonding is failed:" + device.getAddress());
                            Log.d(SemWifiApSmartGattServer.TAG, "Bonding is failed");
                            return;
                        }
                        return;
                    case 11:
                        Log.d(SemWifiApSmartGattServer.TAG, "Bonding is going on");
                        LocalLog localLog2 = SemWifiApSmartGattServer.this.mLocalLog;
                        localLog2.log("SemWifiApSmartGattServer:\tBonding is goingon:" + device.getAddress());
                        return;
                    case 12:
                        Log.d(SemWifiApSmartGattServer.TAG, "Bonding is done,mBondingAddress" + SemWifiApSmartGattServer.this.mBondingAddress);
                        Log.d(SemWifiApSmartGattServer.TAG, "Bonding is done,device.getAddress()" + device.getAddress());
                        LocalLog localLog3 = SemWifiApSmartGattServer.this.mLocalLog;
                        localLog3.log("SemWifiApSmartGattServer:\tBonding is done,mBondingAddress" + SemWifiApSmartGattServer.this.mBondingAddress);
                        LocalLog localLog4 = SemWifiApSmartGattServer.this.mLocalLog;
                        localLog4.log("SemWifiApSmartGattServer:\tBonding is done,device.getAddress()" + device.getAddress());
                        return;
                    default:
                        return;
                }
            } else if (action.equals(SemWifiApSmartGattServer.ACTION_LOGOUT_ACCOUNTS_COMPLETE)) {
                ((WifiManager) SemWifiApSmartGattServer.this.mContext.getSystemService("wifi")).getWifiApState();
                new Message().what = 3;
                Iterator<String> tempIt = SemWifiApSmartGattServer.this.bonedDevicesFromHotspotLive.iterator();
                while (tempIt != null && tempIt.hasNext()) {
                    String tempDevice = tempIt.next();
                    BluetoothDevice device2 = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(tempDevice);
                    Log.d(SemWifiApSmartGattServer.TAG, "delete device " + tempDevice);
                    if (device2 != null && device2.getBondState() == 12) {
                        device2.removeBond();
                        Log.d(SemWifiApSmartGattServer.TAG, ":smarttethering remove device " + tempDevice);
                        LocalLog localLog5 = SemWifiApSmartGattServer.this.mLocalLog;
                        localLog5.log("SemWifiApSmartGattServer:\tsmarttethering remove device " + tempDevice);
                    }
                }
                SemWifiApSmartGattServer.this.bonedDevicesFromHotspotLive.clear();
                Settings.Secure.putString(SemWifiApSmartGattServer.this.mContext.getContentResolver(), "bonded_device_mhsside", null);
            } else if (!action.equals("android.bluetooth.adapter.action.STATE_CHANGED") || !SemWifiApSmartGattServer.this.mBluetoothIsOn) {
                if (action.equals("android.bluetooth.device.action.PAIRING_REQUEST")) {
                    BluetoothDevice device3 = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
                    int mType = intent.getIntExtra("android.bluetooth.device.extra.PAIRING_VARIANT", Integer.MIN_VALUE);
                    int mPasskey = intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY", Integer.MIN_VALUE);
                    String.format(Locale.US, "%06d", Integer.valueOf(mPasskey));
                    Log.d(SemWifiApSmartGattServer.TAG, "mType:" + mType + ",device:" + device3 + ",mBondingAddress:" + SemWifiApSmartGattServer.this.mBondingAddress + "isAutoHotspotServerSet :" + SemWifiApSmartGattServer.this.isAutoHotspotServerSet);
                    LocalLog localLog6 = SemWifiApSmartGattServer.this.mLocalLog;
                    localLog6.log("SemWifiApSmartGattServer\tACTION_PAIRING_REQUEST PAIRING Type:" + mType + ",device:" + device3 + ",mBondingAddress:" + SemWifiApSmartGattServer.this.mBondingAddress + "isAutoHotspotServerSet :" + SemWifiApSmartGattServer.this.isAutoHotspotServerSet);
                    if (SemWifiApSmartGattServer.this.isAutoHotspotServerSet && SemWifiApSmartGattServer.this.mBondingAddress != null && device3.getAddress().equals(SemWifiApSmartGattServer.this.mBondingAddress)) {
                        if (mType == 3) {
                            device3.setPairingConfirmation(true);
                            abortBroadcast();
                        } else if (mType == 2) {
                            abortBroadcast();
                            intent.setClassName("com.android.settings", SemWifiApSmartGattServer.WIFIAP_WARNING_CLASS);
                            intent.setFlags(268435456);
                            intent.setAction("com.samsung.android.settings.wifi.mobileap.wifiapwarning");
                            intent.putExtra("wifiap_warning_dialog_type", 8);
                            if (BluetoothAdapter.getDefaultAdapter() == null || BluetoothAdapter.getDefaultAdapter().getState() != 10) {
                                SemWifiApSmartGattServer.this.mContext.startActivity(intent);
                            } else {
                                BluetoothAdapter.getDefaultAdapter().enable();
                                SemWifiApSmartGattServer.this.mBluetoothIsOn = true;
                                SemWifiApSmartGattServer.this.mPenditIntent = intent;
                            }
                            Log.d(SemWifiApSmartGattServer.TAG, "passkeyconfirm dialog");
                            Intent tintent = new Intent();
                            tintent.setAction("com.samsung.android.server.wifi.softap.smarttethering.collapseQuickPanel");
                            SemWifiApSmartGattServer.this.mContext.sendBroadcast(tintent);
                        }
                    }
                } else if (action.equals("com.samsung.android.net.wifi.WIFI_AP_STA_STATUS_CHANGED")) {
                    int ClientNum = intent.getIntExtra("NUM", 0);
                    String event = intent.getStringExtra("EVENT");
                    WifiManager wifiManager = (WifiManager) SemWifiApSmartGattServer.this.mContext.getSystemService("wifi");
                    int state = wifiManager.getWifiApState();
                    Log.d(SemWifiApSmartGattServer.TAG, "event" + event + "Client Num" + ClientNum + "isMhsEnabledsmartly" + SemWifiApSmartGattServer.this.isMHSEnabledSmartly + "state" + state + "mAuthDevices size" + SemWifiApSmartGattServer.this.mAuthDevices.size());
                    if (state == 13 && SemWifiApSmartGattServer.this.isMHSEnabledSmartly && SemWifiApSmartGattServer.this.mIsNotClientConnected) {
                        Log.d(SemWifiApSmartGattServer.TAG, "Client is connected so remove START_HOTSPOT_ENABLED_TIMEOUT_WITHOUT_CLIENT");
                        SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\tClient is connected so remove START_HOTSPOT_ENABLED_TIMEOUT_WITHOUT_CLIENT");
                        if (SemWifiApSmartGattServer.this.mBleWorkHandler != null) {
                            SemWifiApSmartGattServer.this.mBleWorkHandler.removeMessages(9);
                        }
                        SemWifiApSmartGattServer.this.mIsNotClientConnected = false;
                    }
                    if (event != null && event.equals("sta_leave") && ClientNum == 0 && state == 13 && SemWifiApSmartGattServer.this.isMHSEnabledSmartly) {
                        Log.e(SemWifiApSmartGattServer.TAG, "Disabling Smart MHS");
                        SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\tGattServer sta_leave  ClientNum == 0 stopSoftAp");
                        wifiManager.semSetWifiApEnabled(null, false);
                    }
                } else if (action.equals("android.net.wifi.WIFI_AP_STATE_CHANGED")) {
                    int state2 = intent.getIntExtra("wifi_state", 0);
                    if (state2 == 13) {
                        if (SemWifiApSmartGattServer.this.isWaitingForMHSStatus) {
                            SemWifiApSmartGattServer.this.isWaitingForMHSStatus = false;
                            SemWifiApSmartGattServer.this.notifyConnectedDevices(SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_MHS_ENABLED);
                            if (SemWifiApSmartGattServer.this.mBleWorkHandler != null) {
                                SemWifiApSmartGattServer.this.mBleWorkHandler.removeMessages(1);
                            }
                        }
                        if (SemWifiApSmartGattServer.this.isMHSEnabledSmartly && SemWifiApSmartGattServer.this.mBleWorkHandler != null) {
                            if (SemWifiApSmartGattServer.this.mBleWorkHandler.hasMessages(9)) {
                                SemWifiApSmartGattServer.this.mBleWorkHandler.removeMessages(9);
                            }
                            SemWifiApSmartGattServer.this.mBleWorkHandler.sendEmptyMessageDelayed(9, 60000);
                            SemWifiApSmartGattServer.this.mIsNotClientConnected = true;
                        }
                        SemWifiApSmartGattServer.this.isMHSEnabledViaIntent = true;
                        Log.d(SemWifiApSmartGattServer.TAG, "Hotspot Enabled..");
                        SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\tHotspot Enabled.. ");
                    } else if (state2 == 11 || state2 == 14) {
                        if (SemWifiApSmartGattServer.this.isWaitingForMHSStatus) {
                            SemWifiApSmartGattServer.this.isWaitingForMHSStatus = false;
                            if (SemWifiApSmartGattServer.this.mBleWorkHandler != null) {
                                SemWifiApSmartGattServer.this.mBleWorkHandler.removeMessages(1);
                                SemWifiApSmartGattServer.this.mBleWorkHandler.removeMessages(9);
                            }
                            SemWifiApSmartGattServer.this.notifyConnectedDevices(SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_MHS_ENABLED);
                            LocalLog localLog7 = SemWifiApSmartGattServer.this.mLocalLog;
                            localLog7.log("SemWifiApSmartGattServer\tHotspot disabled. state " + state2 + " isWaitingForMHSStatus " + SemWifiApSmartGattServer.this.isWaitingForMHSStatus + " isMHSEnabledSmartly " + SemWifiApSmartGattServer.this.isMHSEnabledSmartly);
                        }
                        SemWifiApSmartGattServer.this.isMHSEnabledSmartly = false;
                        SemWifiApSmartGattServer.this.isMHSEnabledViaIntent = false;
                        SemWifiApSmartGattServer.this.mIsNotClientConnected = false;
                    }
                } else if (action.equals("com.samsung.android.net.wifi.WIFI_DIALOG_CANCEL_ACTION") && intent.getIntExtra("called_dialog", -1) == 2) {
                    Log.d(SemWifiApSmartGattServer.TAG, "Hotspot Enabled cancelled..");
                    SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\tHotspot Enabled cancelled.. ");
                    if (SemWifiApSmartGattServer.this.isWaitingForMHSStatus) {
                        SemWifiApSmartGattServer.this.isWaitingForMHSStatus = false;
                        if (SemWifiApSmartGattServer.this.mBleWorkHandler != null) {
                            SemWifiApSmartGattServer.this.mBleWorkHandler.removeMessages(1);
                            SemWifiApSmartGattServer.this.mBleWorkHandler.removeMessages(9);
                        }
                        SemWifiApSmartGattServer.this.notifyConnectedDevices(SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_MHS_ENABLED);
                        LocalLog localLog8 = SemWifiApSmartGattServer.this.mLocalLog;
                        localLog8.log("SemWifiApSmartGattServer\tHotspot enabling cancelled. isWaitingForMHSStatus " + SemWifiApSmartGattServer.this.isWaitingForMHSStatus + " isMHSEnabledSmartly " + SemWifiApSmartGattServer.this.isMHSEnabledSmartly);
                    }
                    SemWifiApSmartGattServer.this.isMHSEnabledSmartly = false;
                    SemWifiApSmartGattServer.this.isMHSEnabledViaIntent = false;
                    SemWifiApSmartGattServer.this.mIsNotClientConnected = false;
                }
            } else if (SemWifiApSmartGattServer.this.mPenditIntent != null && BluetoothAdapter.getDefaultAdapter().getState() == 12) {
                Log.d(SemWifiApSmartGattServer.TAG, "ACTION_STATE_CHANGED passkeyconfirm dialog, mBluetoothIsOn : " + SemWifiApSmartGattServer.this.mBluetoothIsOn);
                LocalLog localLog9 = SemWifiApSmartGattServer.this.mLocalLog;
                localLog9.log("SemWifiApSmartGattServer:\tACTION_STATE_CHANGED passkeyconfirm dialog, mBluetoothIsOn : " + SemWifiApSmartGattServer.this.mBluetoothIsOn);
                SemWifiApSmartGattServer.this.mContext.startActivity(SemWifiApSmartGattServer.this.mPenditIntent);
                SemWifiApSmartGattServer.this.mBluetoothIsOn = false;
            }
        }
    }

    /* access modifiers changed from: package-private */
    public class BleWorkHandler extends Handler {
        public BleWorkHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            Log.e(SemWifiApSmartGattServer.TAG, "Got message:" + msg.what);
            switch (msg.what) {
                case 1:
                    Log.i(SemWifiApSmartGattServer.TAG, "Got message START_HOTSPOT_ENABLING_TIMEOUT: isWaitingForMHSStatus " + SemWifiApSmartGattServer.this.isWaitingForMHSStatus);
                    SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\tGot message START_HOTSPOT_ENABLING_TIMEOUT: isWaitingForMHSStatus " + SemWifiApSmartGattServer.this.isWaitingForMHSStatus);
                    if (SemWifiApSmartGattServer.this.isWaitingForMHSStatus) {
                        SemWifiApSmartGattServer.this.isWaitingForMHSStatus = false;
                        SemWifiApSmartGattServer.this.notifyConnectedDevices(SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_MHS_ENABLED);
                        return;
                    }
                    return;
                case 2:
                    Log.i(SemWifiApSmartGattServer.TAG, "Got message COMMAND_ENABLE_HOTSPOT");
                    SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\tGot message COMMAND_ENABLE_HOTSPOT");
                    WifiManager wifiManager = (WifiManager) SemWifiApSmartGattServer.this.mContext.getSystemService("wifi");
                    int state = wifiManager.getWifiApState();
                    SemWifiApSmartGattServer semWifiApSmartGattServer = SemWifiApSmartGattServer.this;
                    semWifiApSmartGattServer.mSSID = semWifiApSmartGattServer.mSemWifiApSmartUtil.getlegacySSID();
                    SemWifiApSmartGattServer semWifiApSmartGattServer2 = SemWifiApSmartGattServer.this;
                    semWifiApSmartGattServer2.mPassword = semWifiApSmartGattServer2.mSemWifiApSmartUtil.getlegacyPassword();
                    if (state == 13 || state == 12) {
                        SemWifiApSmartGattServer.this.notifyConnectedDevices(SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_MHS_ENABLED);
                        SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\tMHS already Enabled");
                        return;
                    }
                    SemWifiApSmartGattServer.this.isWaitingForMHSStatus = true;
                    SemWifiApSmartGattServer.this.isMHSEnabledSmartly = true;
                    if (SemWifiApSmartGattServer.this.preProvisioning() || wifiManager.isWifiSharingLiteSupported()) {
                        Intent startDialogIntent = new Intent();
                        startDialogIntent.setClassName("com.android.settings", SemWifiApSmartGattServer.WIFIAP_WARNING_CLASS);
                        startDialogIntent.setFlags(268435456);
                        startDialogIntent.setAction("com.samsung.android.settings.wifi.mobileap.wifiapwarning");
                        startDialogIntent.putExtra("wifiap_warning_dialog_type", 5);
                        SemWifiApSmartGattServer.this.mContext.startActivity(startDialogIntent);
                        SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\tenableHotspot start wifiapwarning SoftAp state :" + state + ",mSSID:" + SemWifiApSmartGattServer.this.mSSID);
                    } else {
                        wifiManager.semSetWifiApEnabled(null, true);
                        SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer\tenableHotspot startSoftAp state :" + state + ",mSSID:" + SemWifiApSmartGattServer.this.mSSID);
                    }
                    if (SemWifiApSmartGattServer.this.mBleWorkHandler != null) {
                        SemWifiApSmartGattServer.this.mBleWorkHandler.sendEmptyMessageDelayed(1, 60000);
                        return;
                    }
                    return;
                case 3:
                default:
                    return;
                case 4:
                    Toast.makeText(SemWifiApSmartGattServer.this.mContext, 17042594, 1).show();
                    return;
                case 5:
                    Toast.makeText(SemWifiApSmartGattServer.this.mContext, 17042598, 1).show();
                    return;
                case 6:
                    String device = (String) msg.obj;
                    if (device != null && SemWifiApSmartGattServer.this.bonedDevicesFromHotspotLive.add(device)) {
                        String tpString = "";
                        Iterator it = SemWifiApSmartGattServer.this.bonedDevicesFromHotspotLive.iterator();
                        while (it.hasNext()) {
                            tpString = tpString + ((String) it.next()) + "\n";
                        }
                        Settings.Secure.putString(SemWifiApSmartGattServer.this.mContext.getContentResolver(), "bonded_device_mhsside", tpString);
                        Log.i(SemWifiApSmartGattServer.TAG, "Adding to bondedd devices:" + device);
                        SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\tAdding to bondedd devices :" + tpString);
                        return;
                    }
                    return;
                case 7:
                    if (SemWifiApSmartGattServer.this.isWaitingForAcceptStatus) {
                        SemWifiApSmartGattServer.this.notifyConnectedDevices(SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_ACCEPT_INVITATION);
                        return;
                    }
                    return;
                case 8:
                    SemWifiApSmartGattServer.this.notifyConnectedDevices(SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_ACCEPT_INVITATION);
                    return;
                case 9:
                    WifiManager mWifiManager = (WifiManager) SemWifiApSmartGattServer.this.mContext.getSystemService("wifi");
                    int mState = mWifiManager.getWifiApState();
                    Log.d(SemWifiApSmartGattServer.TAG, "isMhsEnabledsmartly" + SemWifiApSmartGattServer.this.isMHSEnabledSmartly + " mState" + mState);
                    if (mState == 13 && SemWifiApSmartGattServer.this.isMHSEnabledSmartly) {
                        Log.e(SemWifiApSmartGattServer.TAG, "Disabling Smart MHS");
                        SemWifiApSmartGattServer.this.mLocalLog.log("SemWifiApSmartGattServer:\tGattServer START_HOTSPOT_ENABLED_TIMEOUT_WITHOUT_CLIENT stopSoftAp");
                        mWifiManager.semSetWifiApEnabled(null, false);
                        return;
                    }
                    return;
            }
        }
    }

    public boolean isMHSEnabledSmart() {
        return this.isMHSEnabledSmartly;
    }

    public boolean setGattServer() {
        synchronized (this.mTempSynchronized) {
            if (this.mGattServer != null) {
                return true;
            }
            Log.d(TAG, "mGattServer is null");
            this.mBluetoothManager = (BluetoothManager) this.mContext.getSystemService("bluetooth");
            this.mGattServer = this.mBluetoothManager.openGattServer(this.mContext, this.mGattServerCallback, 2);
            if (this.mGattServer != null) {
                Log.d(TAG, "calling initGattServer");
                this.mLocalLog.log("SemWifiApSmartGattServer:\tcalling initGattServer");
                return initGattServer();
            }
            Log.d(TAG, "failed to set GattServer in  initGattServer");
            this.mLocalLog.log("SemWifiApSmartGattServer:\tfailed to set GattServer in  initGattServer");
            return false;
        }
    }

    public void removeGattServer() {
        synchronized (this.mTempSynchronized) {
            Log.d(TAG, "trying to close mGattServer and remove mGattService");
            this.mLocalLog.log("SemWifiApSmartGattServer:\ttrying to close mGattServer and remove mGattService");
            if (this.mGattServer != null) {
                if (this.mGattService != null) {
                    boolean ret = this.mGattServer.removeService(this.mGattService);
                    this.isAutoHotspotServerSet = false;
                    Log.d(TAG, "remove mGattService:" + ret);
                    LocalLog localLog = this.mLocalLog;
                    localLog.log("SemWifiApSmartGattServer:\tmGattService removed:" + ret);
                }
                this.mGattServer.close();
                Log.d(TAG, "close mGattServer:");
                this.mLocalLog.log("SemWifiApSmartGattServer:\tmGattServer closed:");
                this.mGattServer = null;
            }
        }
    }

    private boolean initGattServer() {
        if (this.mGattService == null) {
            Log.d(TAG, "Creating autoHotspot GattService");
            this.mLocalLog.log("SemWifiApSmartGattServer:\tCreating autoHotspot GattService");
            this.mGattService = new BluetoothGattService(SemWifiApSmartUtil.SERVICE_UUID, 0);
            BluetoothGattCharacteristic mhs_auth_status = new BluetoothGattCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_AUTH_STATUS, 2, 1);
            BluetoothGattCharacteristic mhs_status_characteristic = new BluetoothGattCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_MHS_STATUS_UUID, 2, 1);
            BluetoothGattCharacteristic mhs_bond_status = new BluetoothGattCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_MHS_BOND_STATUS, 2, 1);
            BluetoothGattCharacteristic auth_encrypted_status = new BluetoothGattCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_ENCRYPTED_AUTH_ID, 10, 17);
            BluetoothGattCharacteristic mhs_ver_update = new BluetoothGattCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_MHS_VER_UPDATE, 10, 17);
            BluetoothGattCharacteristic mhs_side_get_time = new BluetoothGattCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_MHS_SIDE_GET_TIME, 2, 1);
            BluetoothGattCharacteristic mNotifyMHSStatus = new BluetoothGattCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_MHS_ENABLED, 26, 17);
            this.mGattService.addCharacteristic(mhs_auth_status);
            this.mGattService.addCharacteristic(mhs_status_characteristic);
            this.mGattService.addCharacteristic(auth_encrypted_status);
            this.mGattService.addCharacteristic(mhs_bond_status);
            this.mGattService.addCharacteristic(mNotifyMHSStatus);
            this.mGattService.addCharacteristic(mhs_ver_update);
            this.mGattService.addCharacteristic(mhs_side_get_time);
            BluetoothGattCharacteristic read_client_devicename = new BluetoothGattCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_CLIENT_MAC, 2, 1);
            BluetoothGattCharacteristic read_client_bond_status = new BluetoothGattCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_D2D_CLIENT_BOND_STATUS, 2, 1);
            BluetoothGattCharacteristic send_family_id = new BluetoothGattCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_FAMILY_ID, 10, 17);
            BluetoothGattCharacteristic notify_accept_invitation = new BluetoothGattCharacteristic(SemWifiApSmartUtil.CHARACTERISTIC_NOTIFY_ACCEPT_INVITATION, 26, 17);
            this.mGattService.addCharacteristic(read_client_devicename);
            this.mGattService.addCharacteristic(send_family_id);
            this.mGattService.addCharacteristic(read_client_bond_status);
            this.mGattService.addCharacteristic(notify_accept_invitation);
        }
        BluetoothGattServer bluetoothGattServer = this.mGattServer;
        if (bluetoothGattServer != null) {
            boolean ret = bluetoothGattServer.addService(this.mGattService);
            if (ret) {
                this.isAutoHotspotServerSet = true;
                this.mLocalLog.log("SemWifiApSmartGattServer:\tGattServer Added Custom Server to GattServer");
                Log.d(TAG, "Added Custom Server to GattServer");
            } else {
                this.mLocalLog.log("SemWifiApSmartGattServer:\t failed to add GattServer Custom Server to GattServer");
                Log.d(TAG, "failed to add Custom Server to GattServer");
            }
            return ret;
        }
        this.mLocalLog.log("SemWifiApSmartGattServer:\tmGattServer is null in initGattServer");
        Log.d(TAG, "GattServer is null in initGattServer");
        return false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void notifyConnectedDevices(UUID mUUID) {
        for (Map.Entry m : this.mAuthDevices.entrySet()) {
            BluetoothGattServer bluetoothGattServer = this.mGattServer;
            if (!(bluetoothGattServer == null || bluetoothGattServer.getService(SemWifiApSmartUtil.SERVICE_UUID) == null)) {
                BluetoothGattCharacteristic readCharacteristic = this.mGattServer.getService(SemWifiApSmartUtil.SERVICE_UUID).getCharacteristic(mUUID);
                Random rand = new Random();
                readCharacteristic.setValue(new byte[]{(byte) rand.nextInt(10), (byte) rand.nextInt(10)});
                Log.d(TAG, "notifyConnectedDevices");
                this.mLocalLog.log("SemWifiApSmartGattServer:\tnotifyConnectedDevices");
                this.mGattServer.notifyCharacteristicChanged(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(m.getKey()), readCharacteristic, false);
            }
        }
    }

    /* access modifiers changed from: package-private */
    public boolean isProvisioningNeeded() {
        String[] strArr;
        if (!isProvisioningCheck()) {
            return false;
        }
        this.mProvisionApp = this.mContext.getResources().getStringArray(17236154);
        if ((("ATT".equals(CONFIGOPBRANDINGFORMOBILEAP) || "VZW".equals(CONFIGOPBRANDINGFORMOBILEAP) || "TMO".equals(CONFIGOPBRANDINGFORMOBILEAP) || "NEWCO".equals(CONFIGOPBRANDINGFORMOBILEAP)) && (SystemProperties.getBoolean("net.tethering.noprovisioning", false) || (strArr = this.mProvisionApp) == null || strArr.length != 2)) || TextUtils.isEmpty(this.mTetheringProvisionApp) || this.mProvisionApp.length != 2) {
            return false;
        }
        return true;
    }

    private boolean isProvisioningCheck() {
        if (SystemProperties.get("Provisioning.disable").equals("1")) {
            return false;
        }
        return true;
    }

    private boolean isP2pEnabled() {
        this.mWifiP2pManager = (WifiP2pManager) this.mContext.getSystemService("wifip2p");
        WifiP2pManager wifiP2pManager = this.mWifiP2pManager;
        if (wifiP2pManager == null) {
            return false;
        }
        return wifiP2pManager.isWifiP2pEnabled();
    }

    private boolean isP2pConnected() {
        if (this.mWifiP2pManager == null) {
            this.mWifiP2pManager = (WifiP2pManager) this.mContext.getSystemService("wifip2p");
        }
        WifiP2pManager wifiP2pManager = this.mWifiP2pManager;
        if (wifiP2pManager == null) {
            Log.i(TAG, "isP2pConnected() : mWifiP2pManager is null");
            return false;
        }
        boolean ret = wifiP2pManager.isWifiP2pConnected();
        Log.i(TAG, "isP2pConnected() : " + ret);
        return ret;
    }

    private boolean isNanEnabled() {
        if (this.mContext.getPackageManager().hasSystemFeature("android.hardware.wifi.aware")) {
            this.mWifiAwareManager = (WifiAwareManager) this.mContext.getSystemService("wifiaware");
        }
        WifiAwareManager wifiAwareManager = this.mWifiAwareManager;
        if (wifiAwareManager == null) {
            return false;
        }
        return wifiAwareManager.isEnabled();
    }

    private boolean isWirelessDexEnabled() {
        SemDeviceInfo info = ((DisplayManager) this.mContext.getSystemService("display")).semGetActiveDevice();
        if (info != null && info.isWirelessDexMode()) {
            return true;
        }
        Bundle extras = new Bundle(2);
        extras.putString("key", "wireless_dex_scan_device");
        extras.putString("def", "false");
        String ret = "false";
        try {
            Bundle result = this.mContext.getContentResolver().call(Uri.parse("content://com.sec.android.desktopmode.uiservice.SettingsProvider/settings"), "getSettings", (String) null, extras);
            if (result != null) {
                ret = result.getString("wireless_dex_scan_device");
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to get settings", e);
        }
        return ret.equals("true");
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean preProvisioning() {
        if (this.isJDMDevice) {
            Log.i(TAG, " JDM device");
            return true;
        } else if (SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_ENABLEWARNINGPOPUP4DATABATTERYUSAGE)) {
            Log.i(TAG, " Low battery: failed");
            return true;
        } else if (Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_display_on", 0) == 1) {
            Log.i(TAG, " SMARTVIEW_DISABLE: failed");
            this.mLocalLog.log("SemWifiApSmartGattServer:\tSMARTVIEW_DISABLE: failed");
            return true;
        } else if (isP2pConnected()) {
            Log.i(TAG, " isP2pConnected: failed");
            this.mLocalLog.log("SemWifiApSmartGattServer:\tisP2pConnected: failed");
            return true;
        } else if (isNanEnabled()) {
            Log.i(TAG, " isNanEnabled: failed");
            this.mLocalLog.log("SemWifiApSmartGattServer:\tisNanEnabled: failed");
            return true;
        } else if (isWirelessDexEnabled()) {
            Log.i(TAG, " WirelessDex: failed");
            this.mLocalLog.log("SemWifiApSmartGattServer:\t WirelessDex: failed");
            return true;
        } else if (isProvisioningNeeded()) {
            Log.i(TAG, " ProvisioningNeeded ");
            this.mLocalLog.log("SemWifiApSmartGattServer:\tProvisioningNeeded ");
            return true;
        } else {
            WifiManager wm = (WifiManager) this.mContext.getSystemService("wifi");
            if (!wm.isWifiSharingSupported() || wm.isWifiSharingLiteSupported() || Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_ap_first_time_wifi_sharing_dialog", 0) != 0) {
                TelephonyManager tm = (TelephonyManager) this.mContext.getSystemService("phone");
                boolean isRoaming = tm.isNetworkRoaming();
                String iso = tm.getNetworkCountryIso();
                if (!"VZW".equals(CONFIGOPBRANDINGFORMOBILEAP) || wm.getWifiApState() != 11 || !isRoaming || "us".equals(iso)) {
                    return false;
                }
                Log.i(TAG, "vzw roaming popup");
                this.mLocalLog.log("SemWifiApSmartGattServer:\tvzw roaming popup");
                return true;
            }
            Log.i(TAG, " show wifisharing fist popup");
            this.mLocalLog.log("SemWifiApSmartGattServer:\tshow wifisharing fist popup");
            return true;
        }
    }

    public void factoryReset() {
        Log.d(TAG, "network reset settings ");
        long ident = Binder.clearCallingIdentity();
        try {
            SemWifiApContentProviderHelper.insert(this.mContext, "smart_tethering_d2d_Wifimac", (String) null);
            this.mSemWifiApSmartUtil.putD2DFamilyID(null);
            this.mSemWifiApSmartUtil.putHashbasedonD2DFamilyid(-1);
            Intent tintent = new Intent();
            tintent.setAction("com.samsung.android.server.wifi.softap.smarttethering.d2dfamilyid");
            this.mContext.sendBroadcast(tintent);
            SemWifiApSmartWhiteList.getInstance().resetWhitelist();
            Iterator<String> tempIt = this.bonedDevicesFromHotspotLive.iterator();
            if (BluetoothAdapter.getDefaultAdapter() != null) {
                while (tempIt != null && tempIt.hasNext()) {
                    String tempDevice = tempIt.next();
                    BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(tempDevice);
                    Log.d(TAG, "delete device " + tempDevice);
                    if (device != null && device.getBondState() == 12) {
                        device.removeBond();
                        Log.d(TAG, ":smarttethering remove device " + tempDevice);
                        LocalLog localLog = this.mLocalLog;
                        localLog.log("SemWifiApSmartGattServer:\tsmarttethering remove device " + tempDevice);
                    }
                }
                this.bonedDevicesFromHotspotLive.clear();
                Settings.Secure.putString(this.mContext.getContentResolver(), "bonded_device_mhsside", null);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private static class ClientVer {
        public String mAESKey = "";
        public int mUserType = -1;
        public int mVersion = 0;

        public String toString() {
            return String.format("mVersion:" + this.mVersion + ",mUserType:" + this.mUserType + ",mAESKey:" + this.mAESKey, new Object[0]);
        }
    }
}

package com.samsung.android.server.wifi.softap.smarttethering;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.FactoryTest;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.LocalLog;
import android.util.Log;
import android.util.Pair;
import com.android.server.wifi.rtt.RttServiceImpl;
import com.samsung.android.emergencymode.SemEmergencyManager;
import com.samsung.android.net.wifi.SemWifiApBleScanResult;
import com.samsung.android.net.wifi.SemWifiApContentProviderHelper;
import com.samsung.android.net.wifi.SemWifiApMacInfo;
import com.samsung.android.server.wifi.WifiDevicePolicyManager;
import com.samsung.android.server.wifi.softap.SemWifiApBroadcastReceiver;
import com.samsung.android.server.wifi.softap.SemWifiApPowerSaveImpl;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SemWifiApSmartClient {
    private static final String ACTION_LOGOUT_ACCOUNTS_COMPLETE = "com.samsung.account.SAMSUNGACCOUNT_SIGNOUT_COMPLETED";
    private static String TAG = "SemWifiApSmartClient";
    private static IntentFilter mSemWifiApSmartClientIntentFilter = new IntentFilter("android.net.wifi.WIFI_STATE_CHANGED");
    private int CLIENT_BLE_UPDATE_SCAN_DATA_INTERVAL = 20000;
    private final int START_ADVERTISE = 10;
    private final int STOP_ADVERTISE = 11;
    private final int UPDATE_BLE_SCAN_RESULT = 13;
    private HashSet<BluetoothDevice> bonedDevicesFromHotspotLive = new HashSet<>();
    private boolean isAdvRunning;
    private boolean isJDMDevice = "in_house".contains("jdm");
    private boolean isStartAdvPending;
    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        /* class com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartClient.C08031 */

        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.d(SemWifiApSmartClient.TAG, "Client Advertise Started.");
        }

        public void onStartFailure(int errorCode) {
            String str = SemWifiApSmartClient.TAG;
            Log.e(str, "Client Advertise Failed: " + errorCode);
            LocalLog localLog = SemWifiApSmartClient.this.mLocalLog;
            localLog.log(SemWifiApSmartClient.TAG + ":Client Advertise Failed: " + errorCode);
        }
    };
    Map<String, Pair<Long, String>> mBLEPairingFailedHashMap = new ConcurrentHashMap();
    private BleWorkHandler mBleWorkHandler = null;
    private HandlerThread mBleWorkThread = null;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private Context mContext;
    private Long mHashBasedD2DFamilyID;
    private Long mHashBasedFamilyID;
    private Long mHashBasedGuid;
    private LocalLog mLocalLog;
    private boolean mNeedAdvertisement;
    private PowerManager mPowerManager;
    private int mScanningCount = 0;
    private List<SemWifiApBleScanResult> mSemWifiApBleScanResults = new ArrayList();
    private SemWifiApSmartClientReceiver mSemWifiApSmartClientReceiver;
    private SemWifiApSmartUtil mSemWifiApSmartUtil;
    private Set<String> mSmartMHSDevices = new HashSet();
    private WifiDevicePolicyManager mWifiDevicePolicyManager;
    HashMap<String, Integer> mlowBatteryHashMap = new HashMap<>();
    private WifiInfo mwifiInfo;
    List<ScanFilter> scanFilters = new ArrayList();

    static {
        mSemWifiApSmartClientIntentFilter.addAction("android.net.wifi.STATE_CHANGE");
        mSemWifiApSmartClientIntentFilter.addAction("com.samsung.bluetooth.adapter.action.BLE_STATE_CHANGED");
        mSemWifiApSmartClientIntentFilter.addAction("android.intent.action.AIRPLANE_MODE");
        mSemWifiApSmartClientIntentFilter.addAction("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED");
        mSemWifiApSmartClientIntentFilter.addAction(ACTION_LOGOUT_ACCOUNTS_COMPLETE);
        mSemWifiApSmartClientIntentFilter.addAction("android.net.wifi.STATE_CHANGE");
        mSemWifiApSmartClientIntentFilter.addAction("com.samsung.android.server.wifi.softap.smarttethering.changed");
        mSemWifiApSmartClientIntentFilter.addAction("com.samsung.android.server.wifi.softap.smarttethering.familyid");
        mSemWifiApSmartClientIntentFilter.addAction("com.samsung.android.server.wifi.softap.smarttethering.d2dfamilyid");
        mSemWifiApSmartClientIntentFilter.addAction("android.intent.action.SCREEN_OFF");
        mSemWifiApSmartClientIntentFilter.addAction(SemWifiApPowerSaveImpl.ACTION_SCREEN_OFF_BY_PROXIMITY);
    }

    public SemWifiApSmartClient(Context context, SemWifiApSmartUtil tSemWifiApSmartUtil, LocalLog tLocalLog) {
        this.mContext = context;
        this.mSemWifiApSmartUtil = tSemWifiApSmartUtil;
        this.mSemWifiApSmartClientReceiver = new SemWifiApSmartClientReceiver();
        this.mLocalLog = tLocalLog;
        if (!FactoryTest.isFactoryBinary()) {
            this.mContext.registerReceiver(this.mSemWifiApSmartClientReceiver, mSemWifiApSmartClientIntentFilter);
        } else {
            Log.e(TAG, "This devices's binary is a factory binary");
        }
    }

    public void sendEmptyMessage(int val) {
        BleWorkHandler bleWorkHandler = this.mBleWorkHandler;
        if (bleWorkHandler != null) {
            bleWorkHandler.sendEmptyMessageDelayed(val, 2000);
        } else if (bleWorkHandler == null && val == 10) {
            Log.d(TAG, "START_ADVERTISE with null bleworker");
            this.mNeedAdvertisement = true;
        } else if (this.mBleWorkHandler == null && val == 11) {
            Log.d(TAG, "STOP_ADVERTISE with null bleworker");
            this.mNeedAdvertisement = false;
        }
    }

    /* JADX WARNING: Code restructure failed: missing block: B:146:0x0599, code lost:
        r17 = r4;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:158:0x05c8, code lost:
        r7 = new com.samsung.android.net.wifi.SemWifiApBleScanResult(r9.mDevice, r9.mMHSdeviceType, r9.mBattery, r9.mNetworkType, 2, r2, r9.mUserName, r9.mSSID, r9.mhidden, r9.mSecurity, r9.mTimeStamp, r9.mBLERssi, r9.version);
        r1 = r6;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:160:?, code lost:
        r9.mDevice = r1;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:161:0x05cd, code lost:
        r3 = r49;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:163:?, code lost:
        r9.mBattery = r3;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:164:0x05d1, code lost:
        r4 = r57;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:166:?, code lost:
        r9.mMHSdeviceType = r4;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:167:0x05d5, code lost:
        r5 = r55;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:169:?, code lost:
        r9.mNetworkType = r5;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:170:0x05d9, code lost:
        r14 = r14;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:172:?, code lost:
        r9.mUserName = r14;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:173:0x05dd, code lost:
        r6 = r48;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:175:?, code lost:
        r9.mSSID = r6;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:176:0x05e1, code lost:
        r10 = r50;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:178:?, code lost:
        r9.mhidden = r10;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:179:0x05e5, code lost:
        r11 = r3;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:181:?, code lost:
        r9.mSecurity = r11;
        r9.mTimeStamp = java.lang.System.currentTimeMillis();
     */
    /* JADX WARNING: Code restructure failed: missing block: B:182:0x05ef, code lost:
        r12 = r5;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:184:?, code lost:
        r9.mBLERssi = r12;
        r9.version = r17[15];
     */
    /* JADX WARNING: Code restructure failed: missing block: B:185:0x05fb, code lost:
        r0 = th;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:187:0x0608, code lost:
        r0 = th;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:189:0x0617, code lost:
        r0 = th;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:191:0x0628, code lost:
        r0 = th;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:193:0x063a, code lost:
        r0 = th;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:195:0x064e, code lost:
        r0 = th;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:197:0x0664, code lost:
        r0 = th;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:199:0x067c, code lost:
        r0 = th;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:201:0x0696, code lost:
        r0 = th;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:203:0x06b2, code lost:
        r0 = th;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:205:0x06ca, code lost:
        r0 = th;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:207:0x06e0, code lost:
        r0 = th;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:209:0x06f5, code lost:
        r0 = th;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:211:0x070c, code lost:
        r0 = th;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:223:0x07ad, code lost:
        if (r7 == null) goto L_0x07ed;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:226:0x07b5, code lost:
        if (r7.mDevice.equalsIgnoreCase(r1) == false) goto L_0x07ed;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:228:0x07bd, code lost:
        if (r7.mSSID.equals(r6) == false) goto L_0x07ed;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:230:0x07c1, code lost:
        if (r7.mhidden != r10) goto L_0x07ed;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:232:0x07c5, code lost:
        if (r7.mSecurity != r11) goto L_0x07ed;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:234:0x07c9, code lost:
        if (r3 <= 15) goto L_0x07cf;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:236:0x07cd, code lost:
        if (r7.mBattery > 15) goto L_0x07d7;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:238:0x07d3, code lost:
        if (r7.mBattery > 15) goto L_0x07ed;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:239:0x07d5, code lost:
        if (r3 > 15) goto L_0x07ed;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:241:0x07e0, code lost:
        if (java.lang.Math.abs(r7.mBLERssi - r12) >= 10) goto L_0x07ed;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:242:0x07e2, code lost:
        if (r14 == null) goto L_?;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:244:0x07ea, code lost:
        if (r7.mUserName.equals(r14) == false) goto L_0x07ed;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:245:0x07ed, code lost:
        android.util.Log.d(com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartClient.TAG, "adv_ssid_length,srp_ssid_length:" + r25 + "," + r16);
        r0 = com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartClient.TAG;
        r8 = new java.lang.StringBuilder();
        r8.append("updated Scanresult data::");
        r8.append(java.util.Arrays.toString(r17));
        android.util.Log.d(r0, r8.toString());
        android.util.Log.d(com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartClient.TAG, " updated Smart MHS Device with version," + ((int) r17[15]) + ",Bt mac:" + r1 + ",mBattery:" + r3 + ",mNetwork:" + r5 + ",SSID:" + r6 + ",mMHS_MAC:" + r2 + ",mUserName" + r14 + ",Security:" + r11 + ",mhidden:" + r10 + ",timestamp:" + java.lang.System.currentTimeMillis() + ",mBLERssi:" + r12 + ",mMHSdeviceType:" + r4);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:248:?, code lost:
        r61.mLocalLog.log(com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartClient.TAG + ":\tupdated Smart MHS Device with version," + ((int) r17[15]) + ",Bt mac:" + r1 + ",mBattery:" + r3 + ",mNetwork:" + r5 + ",SSID:" + r6 + ",mMHS_MAC:" + r2 + ",mUserName:" + r14 + ",Security:" + r11 + ",mhidden:" + r10 + ",curTimestamp:" + java.lang.System.currentTimeMillis() + ",mBLERssi:" + r12 + ",mMHSdeviceType:" + r4);
        r0 = r61.mLocalLog;
        r1 = new java.lang.StringBuilder();
        r1.append(com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartClient.TAG);
        r1.append(":\tupdated Scanresult data::");
        r1.append(java.util.Arrays.toString(r17));
        r0.log(r1.toString());
     */
    /* JADX WARNING: Code restructure failed: missing block: B:249:0x0947, code lost:
        r0 = e;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:258:0x0972, code lost:
        r0 = e;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:276:?, code lost:
        return;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:277:?, code lost:
        return;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:281:?, code lost:
        return;
     */
    public void sendScanResultFromScanner(int userType, ScanResult result) {
        int mMHSdeviceType;
        int adv_ssid_length;
        long diff;
        int mhidden;
        int mhidden2;
        int mNetwork;
        int mSecurity;
        int mhidden3;
        int adv_ssid_length2;
        byte[] mpssid;
        int srp_ssid_length;
        String SSID;
        byte[] mScanResultData;
        SemWifiApBleScanResult oldres;
        String mUserName;
        int mBLERssi;
        int mSecurity2;
        int mhidden4;
        String BT_MAC;
        int mBLERssi2;
        int mSecurity3;
        String BT_MAC2;
        try {
            byte[] mScanResultData2 = result.getScanRecord().getManufacturerSpecificData(SemWifiApSmartUtil.MANUFACTURE_ID);
            int mNetwork2 = result.getRssi();
            int mMHSdeviceType2 = 1;
            int mBattery = 50;
            String mUserName2 = null;
            if (userType == 1) {
                mMHSdeviceType2 = 1;
                mUserName2 = this.mSemWifiApSmartUtil.getSameUserName();
            }
            if (userType != 2) {
                mMHSdeviceType = mMHSdeviceType2;
            } else if (this.mSemWifiApSmartUtil.getSamsungAccountCount() == 0) {
                mUserName2 = this.mContext.getString(17040041);
                mMHSdeviceType = 3;
            } else {
                mMHSdeviceType = 2;
                mUserName2 = this.mSemWifiApSmartUtil.getUserNameFromFamily(mScanResultData2);
            }
            int i = 12;
            while (true) {
                if (i >= 15) {
                    break;
                } else if (mScanResultData2[i] != 0) {
                    break;
                } else {
                    i++;
                }
            }
            if (i != 15) {
                String mMHS_MAC = (((((((Character.toString(Character.forDigit((mScanResultData2[12] & 240) >> 4, 16)) + Character.toString(Character.forDigit(mScanResultData2[12] & 15, 16))) + ":") + Character.toString(Character.forDigit((mScanResultData2[13] & 240) >> 4, 16))) + Character.toString(Character.forDigit(mScanResultData2[13] & 15, 16))) + ":") + Character.toString(Character.forDigit((mScanResultData2[14] & 240) >> 4, 16))) + Character.toString(Character.forDigit(mScanResultData2[14] & 15, 16))).toLowerCase();
                if (this.mSemWifiApSmartUtil.getSamsungAccountCount() == 0) {
                    String mD2DWifiAMC = SemWifiApContentProviderHelper.get(this.mContext, "smart_tethering_d2d_Wifimac");
                    if (TextUtils.isEmpty(mD2DWifiAMC) || !Arrays.asList(mD2DWifiAMC.split("\n")).contains(mMHS_MAC)) {
                        return;
                    }
                }
                String mBLE_MAC = result.getDevice().getAddress();
                Pair<Long, String> tpair = this.mBLEPairingFailedHashMap.get(mMHS_MAC);
                if (tpair != null) {
                    mhidden = 0;
                    adv_ssid_length = 0;
                    diff = System.currentTimeMillis() - ((Long) tpair.first).longValue();
                } else {
                    mhidden = 0;
                    adv_ssid_length = 0;
                    diff = -1;
                }
                if (tpair == null || mBLE_MAC == null) {
                    mhidden2 = mhidden;
                } else {
                    mhidden2 = mhidden;
                    if (tpair.second != null) {
                        if ((mBLE_MAC.equals(tpair.second) && diff < 60000) || (!mBLE_MAC.equals(tpair.second) && diff < RttServiceImpl.HAL_RANGING_TIMEOUT_MS)) {
                            Log.d(TAG, "new mBLE_MAC:" + mBLE_MAC + ",diff:" + diff + ",old BLE mac:" + ((String) tpair.second));
                            this.mLocalLog.log(TAG + ":\tnew mBLE_MAC:" + mBLE_MAC + ",diff:" + diff + ",old BLE mac:" + ((String) tpair.second));
                            return;
                        }
                    }
                }
                if (tpair != null) {
                    this.mBLEPairingFailedHashMap.remove(mMHS_MAC);
                }
                byte mBattery_nw_sec_byte = mScanResultData2[11];
                if ((mBattery_nw_sec_byte & SemWifiApSmartUtil.BLE_WIFI) == (mBattery_nw_sec_byte & -64)) {
                    mNetwork = 1;
                } else if ((mBattery_nw_sec_byte & -64) == (mBattery_nw_sec_byte & -64)) {
                    mNetwork = 2;
                } else {
                    mNetwork = 0;
                }
                if ((mBattery_nw_sec_byte & 4) == 4) {
                    mSecurity = 1;
                } else {
                    mSecurity = 0;
                }
                if ((mBattery_nw_sec_byte & 2) == 2) {
                    mhidden3 = 1;
                } else {
                    mhidden3 = mhidden2;
                }
                if ((mBattery_nw_sec_byte & 8) == (mBattery_nw_sec_byte & 56)) {
                    mBattery = 15;
                } else if ((mBattery_nw_sec_byte & SemWifiApSmartUtil.BLE_BATT_2) == (mBattery_nw_sec_byte & 56)) {
                    mBattery = 30;
                } else if ((mBattery_nw_sec_byte & SemWifiApSmartUtil.BLE_BATT_3) == (mBattery_nw_sec_byte & 56)) {
                    mBattery = 45;
                } else if ((mBattery_nw_sec_byte & SemWifiApSmartUtil.BLE_BATT_4) == (mBattery_nw_sec_byte & 56)) {
                    mBattery = 60;
                } else if ((mBattery_nw_sec_byte & SemWifiApSmartUtil.BLE_BATT_5) == (mBattery_nw_sec_byte & 56)) {
                    mBattery = 75;
                } else if ((mBattery_nw_sec_byte & SemWifiApSmartUtil.BLE_BATT_6) == (mBattery_nw_sec_byte & 56)) {
                    mBattery = 90;
                } else if ((mBattery_nw_sec_byte & 56) == (mBattery_nw_sec_byte & 56)) {
                    mBattery = 100;
                }
                byte[] mpssid2 = new byte[34];
                int tindex = 0;
                int adv_ssid_length3 = adv_ssid_length;
                int i2 = 17;
                while (true) {
                    if (i2 >= 24) {
                        break;
                    } else if (mScanResultData2[i2] == 0) {
                        break;
                    } else {
                        adv_ssid_length3++;
                        mpssid2[tindex] = mScanResultData2[i2];
                        i2++;
                        tindex++;
                        mBLE_MAC = mBLE_MAC;
                    }
                }
                int srp_ssid_length2 = 0;
                int i3 = 26;
                while (true) {
                    if (i3 >= 51) {
                        break;
                    } else if (mScanResultData2[i3] == 0) {
                        break;
                    } else {
                        mpssid2[tindex] = mScanResultData2[i3];
                        srp_ssid_length2++;
                        i3++;
                        tindex++;
                    }
                }
                new String(mScanResultData2, 17, adv_ssid_length3);
                new String(mScanResultData2, 26, srp_ssid_length2);
                String SSID2 = new String(mpssid2, 0, tindex);
                if (mBattery > 15) {
                    this.mlowBatteryHashMap.put(mMHS_MAC, 0);
                }
                if (this.mwifiInfo != null) {
                    String bssid2 = getBssid();
                    Integer batteryVal = this.mlowBatteryHashMap.get(mMHS_MAC);
                    mpssid = mpssid2;
                    if (mBattery > 15 || bssid2 == null || batteryVal == null || batteryVal.intValue() != 0 || islegacy(mMHS_MAC)) {
                        srp_ssid_length = srp_ssid_length2;
                        adv_ssid_length2 = adv_ssid_length3;
                        SSID = SSID2;
                    } else {
                        this.mlowBatteryHashMap.put(mMHS_MAC, 1);
                        Log.d(TAG, "Sending low battery intent");
                        Intent intent = new Intent();
                        srp_ssid_length = srp_ssid_length2;
                        intent.setClassName("com.android.settings", "com.samsung.android.settings.wifi.mobileap.WifiApWarning");
                        intent.setFlags(268435456);
                        intent.setAction(SemWifiApBroadcastReceiver.WIFIAP_WARNING_DIALOG);
                        SSID = SSID2;
                        intent.putExtra("st_ssid_name", SSID);
                        intent.putExtra("battery_info", mBattery);
                        adv_ssid_length2 = adv_ssid_length3;
                        intent.putExtra(SemWifiApBroadcastReceiver.WIFIAP_WARNING_DIALOG_TYPE, 42);
                        this.mContext.startActivity(intent);
                        this.mLocalLog.log(TAG + ":sending low battery intent mBattery :" + mBattery + "mMHS_MAC" + mMHS_MAC);
                    }
                } else {
                    mpssid = mpssid2;
                    srp_ssid_length = srp_ssid_length2;
                    adv_ssid_length2 = adv_ssid_length3;
                    SSID = SSID2;
                }
                if (mMHS_MAC == null || !this.mSmartMHSDevices.add(mMHS_MAC)) {
                    int mNetwork3 = mNetwork;
                    String SSID3 = SSID;
                    int mBattery2 = mBattery;
                    int mhidden5 = mhidden3;
                    String mUserName3 = mUserName2;
                    int mMHSdeviceType3 = mMHSdeviceType;
                    if (mMHS_MAC != null && !this.mSmartMHSDevices.add(mMHS_MAC)) {
                        String SSID4 = result.getDevice().getAddress();
                        SemWifiApBleScanResult oldres2 = null;
                        synchronized (this.mSemWifiApBleScanResults) {
                            try {
                                Iterator<SemWifiApBleScanResult> it = this.mSemWifiApBleScanResults.iterator();
                                while (true) {
                                    if (!it.hasNext()) {
                                        int mSecurity4 = mSecurity;
                                        byte[] mScanResultData3 = mScanResultData2;
                                        int mBLERssi3 = mNetwork2;
                                        String BT_MAC3 = SSID4;
                                        String SSID5 = SSID3;
                                        int mBattery3 = mBattery2;
                                        int mhidden6 = mhidden5;
                                        int mNetwork4 = mNetwork3;
                                        String mUserName4 = mUserName3;
                                        int mMHSdeviceType4 = mMHSdeviceType3;
                                        break;
                                    }
                                    try {
                                        SemWifiApBleScanResult res = it.next();
                                        if (res.mWifiMac.equalsIgnoreCase(mMHS_MAC)) {
                                            mUserName = mUserName3;
                                            if (mUserName != null) {
                                                try {
                                                    break;
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
                                            } else {
                                                mSecurity2 = mSecurity;
                                                mScanResultData = mScanResultData2;
                                                mBLERssi = mNetwork2;
                                                BT_MAC2 = SSID4;
                                                oldres = oldres2;
                                                BT_MAC = SSID3;
                                                mSecurity3 = mBattery2;
                                                mhidden4 = mhidden5;
                                                mBLERssi2 = mNetwork3;
                                            }
                                        } else {
                                            mSecurity2 = mSecurity;
                                            mScanResultData = mScanResultData2;
                                            mBLERssi = mNetwork2;
                                            BT_MAC2 = SSID4;
                                            oldres = oldres2;
                                            BT_MAC = SSID3;
                                            mSecurity3 = mBattery2;
                                            mhidden4 = mhidden5;
                                            mBLERssi2 = mNetwork3;
                                            mUserName = mUserName3;
                                        }
                                        mBattery2 = mSecurity3;
                                        mMHSdeviceType3 = mMHSdeviceType3;
                                        mNetwork3 = mBLERssi2;
                                        SSID3 = BT_MAC;
                                        mhidden5 = mhidden4;
                                        mSecurity = mSecurity2;
                                        mNetwork2 = mBLERssi;
                                        mUserName3 = mUserName;
                                        oldres2 = oldres;
                                        mScanResultData2 = mScanResultData;
                                        SSID4 = BT_MAC2;
                                    } catch (Throwable th3) {
                                        th = th3;
                                        while (true) {
                                            break;
                                        }
                                        throw th;
                                    }
                                }
                                try {
                                } catch (Throwable th4) {
                                    th = th4;
                                    while (true) {
                                        break;
                                    }
                                    throw th;
                                }
                            } catch (Throwable th5) {
                                th = th5;
                                while (true) {
                                    break;
                                }
                                throw th;
                            }
                        }
                    }
                } else {
                    String BT_MAC4 = result.getDevice().getAddress();
                    Log.d(TAG, " Smart MHS Device with version," + ((int) mScanResultData2[15]) + ", Bt mac:" + BT_MAC4 + ",mBattery:" + mBattery + ",mNetwork:" + mNetwork + ",SSID:" + SSID + ",mMHS_MAC:" + mMHS_MAC + ",mUserName:" + mUserName2 + ",Security:" + mSecurity + ",mhidden:" + mhidden3 + ",curTimestamp:" + System.currentTimeMillis() + ",mBLERssi:" + mNetwork2 + "mMHSdeviceType:" + mMHSdeviceType);
                    this.mLocalLog.log(TAG + ":\tSmart MHS Device with version," + ((int) mScanResultData2[15]) + ",Bt mac:" + BT_MAC4 + ",mBattery:" + mBattery + ",mNetwork:" + mNetwork + ",SSID:" + SSID + ",mMHS_MAC:" + mMHS_MAC + ",mUserName:" + mUserName2 + ",Security:" + mSecurity + ",mhidden:" + mhidden3 + ",curTimestamp:" + System.currentTimeMillis() + ",mBLERssi:" + mNetwork2 + "mMHSdeviceType:" + mMHSdeviceType);
                    if (mUserName2 != null) {
                        addScanResults(new SemWifiApBleScanResult(result.getDevice().getAddress(), mMHSdeviceType, mBattery, mNetwork, 2, mMHS_MAC, mUserName2, SSID, mhidden3, mSecurity, System.currentTimeMillis(), mNetwork2, mScanResultData2[15]));
                    }
                }
            }
        } catch (Exception e) {
            e = e;
            Log.e(TAG, "for log only, Exception occured");
            e.printStackTrace();
        }
    }

    public boolean setWifiApSmartClient(boolean enable) {
        if (enable) {
            BleWorkHandler bleWorkHandler = this.mBleWorkHandler;
            if (bleWorkHandler != null) {
                bleWorkHandler.removeMessages(11);
                this.mBleWorkHandler.removeMessages(10);
            }
            sendEmptyMessage(10);
            return true;
        }
        BleWorkHandler bleWorkHandler2 = this.mBleWorkHandler;
        if (bleWorkHandler2 != null) {
            bleWorkHandler2.removeMessages(11);
            this.mBleWorkHandler.removeMessages(10);
        }
        sendEmptyMessage(11);
        return true;
    }

    public void handleBootCompleted() {
        Log.d(TAG, "handleBootCompleted");
        LocalLog localLog = this.mLocalLog;
        localLog.log(TAG + ":\t handleBootCompleted");
        this.mBleWorkThread = new HandlerThread("SemWifiApSmartClientBleHandler");
        this.mBleWorkThread.start();
        this.mBleWorkHandler = new BleWorkHandler(this.mBleWorkThread.getLooper());
        if (this.mNeedAdvertisement && this.mBleWorkHandler != null) {
            Log.d(TAG, "need to advertise client packets");
            sendEmptyMessage(10);
        }
    }

    class SemWifiApSmartClientReceiver extends BroadcastReceiver {
        SemWifiApSmartClientReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            boolean isConnected = false;
            if (action.equals("android.intent.action.SCREEN_OFF") || action.equals(SemWifiApPowerSaveImpl.ACTION_SCREEN_OFF_BY_PROXIMITY)) {
                SemWifiApSmartClient.this.mLocalLog.log(SemWifiApSmartClient.TAG + ":\t LCD is oFF,so stop client advertize");
                Log.d(SemWifiApSmartClient.TAG, "LCD is OFF,so stop client advertize");
                SemWifiApSmartClient.this.setWifiApSmartClient(false);
                return;
            }
            boolean isAirplaneMode = true;
            if (action.equals("android.net.wifi.STATE_CHANGE")) {
                WifiManager wifiManager = (WifiManager) SemWifiApSmartClient.this.mContext.getSystemService("wifi");
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra("networkInfo");
                if (networkInfo != null && networkInfo.isConnected()) {
                    isConnected = true;
                }
                if (isConnected) {
                    SemWifiApSmartClient.this.mwifiInfo = wifiManager.getConnectionInfo();
                    SemWifiApSmartClient.this.mLocalLog.log(SemWifiApSmartClient.TAG + ":\t connected to wifi");
                    Log.d(SemWifiApSmartClient.TAG, "connected to wifi:");
                    return;
                }
                SemWifiApSmartClient.this.mwifiInfo = null;
            } else if (action.equals(SemWifiApSmartClient.ACTION_LOGOUT_ACCOUNTS_COMPLETE)) {
                SemWifiApSmartClient.this.mLocalLog.log(SemWifiApSmartClient.TAG + ":LOGOUT_COMPLETE");
                Log.d(SemWifiApSmartClient.TAG, "LOGOUT_COMPLETE");
                SemWifiApSmartClient.this.setWifiApSmartClient(false);
            } else if (action.equals("android.intent.action.AIRPLANE_MODE")) {
                if (Settings.Global.getInt(SemWifiApSmartClient.this.mContext.getContentResolver(), "airplane_mode_on", 0) == 0) {
                    isAirplaneMode = false;
                }
                Log.d(SemWifiApSmartClient.TAG, "isAirplaneMode:" + isAirplaneMode);
                SemWifiApSmartClient.this.mLocalLog.log(SemWifiApSmartClient.TAG + ":AIRPLANE_MODE" + isAirplaneMode);
                if (isAirplaneMode) {
                    SemWifiApSmartClient.this.setWifiApSmartClient(false);
                    Settings.Secure.putInt(SemWifiApSmartClient.this.mContext.getContentResolver(), "wifi_client_smart_tethering_settings", 0);
                }
            } else if (action.equals("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED")) {
                boolean emergencyMode = intent.getBooleanExtra("phoneinECMState", false);
                Log.d(SemWifiApSmartClient.TAG, "emergencyMode:" + emergencyMode);
                SemWifiApSmartClient.this.mLocalLog.log(SemWifiApSmartClient.TAG + ": EMERGENCY" + emergencyMode);
                if (emergencyMode) {
                    SemWifiApSmartClient.this.setWifiApSmartClient(false);
                    Settings.Secure.putInt(SemWifiApSmartClient.this.mContext.getContentResolver(), "wifi_client_smart_tethering_settings", 0);
                }
            } else if (action.equals("android.net.wifi.WIFI_STATE_CHANGED")) {
                int mWifiState = intent.getIntExtra("wifi_state", 4);
                if (mWifiState != 3 && mWifiState == 1) {
                    Log.d(SemWifiApSmartClient.TAG, "stopping adv due to Wi-FI is OFF/");
                    SemWifiApSmartClient.this.mLocalLog.log(SemWifiApSmartClient.TAG + ":\tstopping adv due to Wi-FI is OFF/");
                    SemWifiApSmartClient.this.clearLocalResults();
                    SemWifiApSmartClient.this.setWifiApSmartClient(false);
                    SemWifiApSmartClient.this.mBLEPairingFailedHashMap.clear();
                }
            } else if (action.equals("com.samsung.bluetooth.adapter.action.BLE_STATE_CHANGED")) {
                int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE);
                if (SemWifiApSmartClient.this.isAdvRunning) {
                    SemWifiApSmartClient.this.isStartAdvPending = false;
                }
                if (SemWifiApSmartClient.this.isStartAdvPending && state == 15) {
                    SemWifiApSmartClient.this.isStartAdvPending = false;
                    Log.d(SemWifiApSmartClient.TAG, "BLE state:" + state);
                    SemWifiApSmartClient.this.startWifiApSmartClientAdvertize();
                }
            } else if (action.equals("com.samsung.android.server.wifi.softap.smarttethering.familyid") || action.equals("com.samsung.android.server.wifi.softap.smarttethering.d2dfamilyid")) {
                int CST = Settings.Secure.getInt(SemWifiApSmartClient.this.mContext.getContentResolver(), "wifi_client_smart_tethering_settings", 0);
                Log.d(SemWifiApSmartClient.TAG, "familyid intent is received:cst," + CST);
                if (SemWifiApSmartClient.this.isAdvRunning || CST == 1) {
                    Log.d(SemWifiApSmartClient.TAG, "familyid intent is received, so restarting client advertizement,cst:" + CST);
                    SemWifiApSmartClient.this.mLocalLog.log(SemWifiApSmartClient.TAG + ":\tfamilyid intent is received, so restarting client advertizement,cst:" + CST);
                    if (SemWifiApSmartClient.this.mBleWorkHandler != null) {
                        SemWifiApSmartClient.this.mBleWorkHandler.sendEmptyMessage(11);
                    }
                    if (SemWifiApSmartClient.this.mBleWorkHandler != null) {
                        SemWifiApSmartClient.this.mBleWorkHandler.sendEmptyMessageDelayed(10, 1000);
                    }
                }
            }
        }
    }

    /* access modifiers changed from: package-private */
    public class BleWorkHandler extends Handler {
        public BleWorkHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            int num;
            int i = msg.what;
            if (i == 10) {
                int status = SemWifiApSmartClient.this.checkPreConditions();
                if (status == 0) {
                    Settings.Secure.putInt(SemWifiApSmartClient.this.mContext.getContentResolver(), "wifi_client_smart_tethering_settings", 1);
                    SemWifiApSmartClient.this.startWifiApSmartClientAdvertize();
                    sendEmptyMessageDelayed(13, (long) SemWifiApSmartClient.this.CLIENT_BLE_UPDATE_SCAN_DATA_INTERVAL);
                    return;
                }
                String str = SemWifiApSmartClient.TAG;
                Log.e(str, "checkPreConditions failed " + status);
                LocalLog localLog = SemWifiApSmartClient.this.mLocalLog;
                localLog.log(SemWifiApSmartClient.TAG + ":checkPreConditions failed " + status);
            } else if (i == 11) {
                Settings.Secure.putInt(SemWifiApSmartClient.this.mContext.getContentResolver(), "wifi_client_smart_tethering_settings", 0);
                SemWifiApSmartClient.this.stopWifiApSmartClientAdvertize();
            } else if (i == 13) {
                synchronized (SemWifiApSmartClient.this.mSemWifiApBleScanResults) {
                    num = SemWifiApSmartClient.this.mSemWifiApBleScanResults.size();
                }
                if (SemWifiApSmartClient.this.isAdvRunning || num != 0) {
                    SemWifiApSmartClient.this.updateLocalResults();
                    sendEmptyMessageDelayed(13, (long) SemWifiApSmartClient.this.CLIENT_BLE_UPDATE_SCAN_DATA_INTERVAL);
                    return;
                }
                String str2 = SemWifiApSmartClient.TAG;
                Log.e(str2, "Not updating BLE scan result sScan " + SemWifiApSmartClient.this.isAdvRunning + " size " + num);
                SemWifiApSmartClient.this.clearLocalResults();
                removeMessages(13);
            }
        }
    }

    class SortList implements Comparator<SemWifiApBleScanResult> {
        SortList() {
        }

        public int compare(SemWifiApBleScanResult m1, SemWifiApBleScanResult m2) {
            int bleRSSI1 = SemWifiApSmartClient.this.getRssiRoundOffValue(m1);
            int bleRSSI2 = SemWifiApSmartClient.this.getRssiRoundOffValue(m2);
            if (bleRSSI1 > bleRSSI2) {
                String str = SemWifiApSmartClient.TAG;
                Log.d(str, "compare() - bleRSSI1 > bleRSSI2, (" + bleRSSI1 + ">" + bleRSSI2 + ")");
                return -1;
            } else if (bleRSSI1 < bleRSSI2) {
                String str2 = SemWifiApSmartClient.TAG;
                Log.d(str2, "compare() - bleRSSI1 < bleRSSI2, (" + bleRSSI1 + "<" + bleRSSI2 + ")");
                return 1;
            } else {
                String str3 = SemWifiApSmartClient.TAG;
                Log.d(str3, "compare() - m1.mSSID < m2.mSSID, (" + m1.mSSID + " is compared with " + m2.mSSID + ")");
                return m1.mSSID.compareTo(m2.mSSID);
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private int getRssiRoundOffValue(SemWifiApBleScanResult ble) {
        int level;
        if (ble.mBLERssi >= -60) {
            level = -60;
        } else if (ble.mBLERssi >= -70) {
            level = -70;
        } else if (ble.mBLERssi >= -80) {
            level = -80;
        } else if (ble.mBLERssi >= -90) {
            level = -90;
        } else {
            level = -100;
        }
        String str = TAG;
        Log.d(str, "getRssiRoundOffValue() - SSID: " + ble.mSSID + "`s BLERssi value internally is set from " + ble.mBLERssi + " to " + level);
        return level;
    }

    /* JADX WARNING: Code restructure failed: missing block: B:21:0x009a, code lost:
        r1 = th;
     */
    public synchronized List<SemWifiApBleScanResult> getWifiApBleScanResults() {
        List<SemWifiApBleScanResult> list;
        synchronized (this.mSemWifiApBleScanResults) {
            List<SemWifiApBleScanResult> normalAccessPoints = new ArrayList<>();
            List<SemWifiApBleScanResult> lowBatteryAccessPoints = new ArrayList<>();
            SortList sortlist = new SortList();
            Log.i(TAG, "getWifiApBleScanResults() - BLE scan result sort based on Signal Strength and alphabetical order start.");
            Collections.sort(this.mSemWifiApBleScanResults, sortlist);
            for (SemWifiApBleScanResult semWifiApBleScanResult : this.mSemWifiApBleScanResults) {
                if (semWifiApBleScanResult.mBattery <= 15) {
                    String str = TAG;
                    Log.i(str, "getWifiApBleScanResults() - adding ble to lowBatteryAccessPoints, wifiMac: " + semWifiApBleScanResult.mWifiMac + ", SSID: " + semWifiApBleScanResult.mSSID);
                    lowBatteryAccessPoints.add(semWifiApBleScanResult);
                } else {
                    String str2 = TAG;
                    Log.i(str2, "getWifiApBleScanResults() - adding ble to normalAccessPoints, wifiMac: " + semWifiApBleScanResult.mWifiMac + ", SSID: " + semWifiApBleScanResult.mSSID);
                    normalAccessPoints.add(semWifiApBleScanResult);
                }
            }
            this.mSemWifiApBleScanResults.clear();
            this.mSemWifiApBleScanResults.addAll(normalAccessPoints);
            this.mSemWifiApBleScanResults.addAll(lowBatteryAccessPoints);
            list = this.mSemWifiApBleScanResults;
        }
        return list;
        while (true) {
        }
    }

    private byte[] getclientAdvManufactureData() {
        byte[] data = new byte[24];
        for (int i = 0; i < 24; i++) {
            data[i] = 0;
        }
        data[0] = 1;
        data[1] = 18;
        long mguid = this.mSemWifiApSmartUtil.getHashbasedonGuid();
        long familyID = this.mSemWifiApSmartUtil.getHashbasedonFamilyId();
        long mD2DFamilyID = this.mSemWifiApSmartUtil.getHashbasedonD2DFamilyid();
        if (mguid != -1) {
            byte[] guidBytes = SemWifiApSmartUtil.bytesFromLong(Long.valueOf(mguid));
            for (int i2 = 0; i2 < 4; i2++) {
                data[i2 + 2] = guidBytes[i2];
            }
        }
        if (familyID != -1) {
            byte[] familyBytes = SemWifiApSmartUtil.bytesFromLong(Long.valueOf(familyID));
            for (int i3 = 0; i3 < 4; i3++) {
                data[i3 + 2 + 4] = familyBytes[i3];
            }
        } else if (mguid == -1 && mD2DFamilyID != -1) {
            byte[] familyBytes2 = SemWifiApSmartUtil.bytesFromLong(Long.valueOf(mD2DFamilyID));
            for (int i4 = 0; i4 < 4; i4++) {
                data[i4 + 2 + 4] = familyBytes2[i4];
            }
        }
        data[10] = 1;
        return data;
    }

    private String getBssid() {
        WifiInfo wifiInfo = this.mwifiInfo;
        if (wifiInfo == null || wifiInfo.getBSSID() == null) {
            return null;
        }
        return this.mwifiInfo.getBSSID();
    }

    private boolean islegacy(String ScanResultMAC) {
        int state;
        WifiManager mWifiManager = (WifiManager) this.mContext.getSystemService("wifi");
        if (mWifiManager == null || ScanResultMAC == null || (state = mWifiManager.getSmartApConnectedStatusFromScanResult(ScanResultMAC)) != 3) {
            return true;
        }
        String str = TAG;
        Log.d(str, "islegacy state" + state + " ScanResultMAC " + ScanResultMAC);
        return false;
    }

    /* access modifiers changed from: package-private */
    public void clearLocalResults() {
        synchronized (this.mSemWifiApBleScanResults) {
            this.mSemWifiApBleScanResults.clear();
            this.mSmartMHSDevices.clear();
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateLocalResults() {
        synchronized (this.mSemWifiApBleScanResults) {
            List<Integer> mInt = new ArrayList<>();
            int i = 0;
            long curTime = System.currentTimeMillis();
            for (SemWifiApBleScanResult res : this.mSemWifiApBleScanResults) {
                if (curTime - res.mTimeStamp > 20000) {
                    mInt.add(Integer.valueOf(i));
                }
                i++;
            }
            try {
                for (Integer num : mInt) {
                    int j = num.intValue();
                    String mhsmac = this.mSemWifiApBleScanResults.get(j).mWifiMac;
                    if (mhsmac != null) {
                        String str = TAG;
                        Log.d(str, "removed BLE scan result data:" + mhsmac);
                        LocalLog localLog = this.mLocalLog;
                        localLog.log(TAG + ":\tremoved BLE scan result data:" + mhsmac);
                        this.mSmartMHSDevices.remove(mhsmac);
                    }
                    this.mSemWifiApBleScanResults.remove(j);
                }
            } catch (IndexOutOfBoundsException e) {
            }
        }
    }

    /* access modifiers changed from: package-private */
    public void setBLEPairingFailedHistory(String mhsmac, Pair<Long, String> mpair) {
        this.mBLEPairingFailedHashMap.put(mhsmac, mpair);
        String str = TAG;
        Log.d(str, "setBLEPairingFailedHistory:" + mhsmac + ",time:" + mpair.first + ",BLE mac:" + ((String) mpair.second));
        LocalLog localLog = this.mLocalLog;
        localLog.log(TAG + "\t:setBLEPairingFailedHistory:" + mhsmac + ",time:" + mpair.first + ",BLE mac:" + ((String) mpair.second));
    }

    /* access modifiers changed from: package-private */
    public boolean getBLEPairingFailedHistory(String mac) {
        boolean ret = false;
        if (this.mBLEPairingFailedHashMap.get(mac) != null) {
            ret = true;
        }
        String str = TAG;
        Log.d(str, "getBLEPairingFailedHistory:" + mac);
        LocalLog localLog = this.mLocalLog;
        localLog.log(TAG + "\t:getBLEPairingFailedHistory:" + mac);
        return ret;
    }

    /* access modifiers changed from: package-private */
    public void removeFromScanResults(int type, String address) {
        synchronized (this.mSemWifiApBleScanResults) {
            int i = 0;
            boolean found = false;
            Iterator<SemWifiApBleScanResult> it = this.mSemWifiApBleScanResults.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                SemWifiApBleScanResult res = it.next();
                i++;
                if (type != 1 || !res.mDevice.equalsIgnoreCase(address)) {
                    if (type == 2 && res.mWifiMac.equalsIgnoreCase(address)) {
                        found = true;
                        break;
                    }
                } else {
                    found = true;
                    break;
                }
            }
            if (found) {
                if (type == 2) {
                    try {
                        this.mSmartMHSDevices.remove(address);
                    } catch (Exception e) {
                    }
                }
                this.mSemWifiApBleScanResults.remove(i - 1);
                String str = TAG;
                StringBuilder sb = new StringBuilder();
                sb.append("removed at index:");
                sb.append(i - 1);
                sb.append(",mSemWifiApBleScanResults.size():");
                sb.append(this.mSemWifiApBleScanResults.size());
                Log.d(str, sb.toString());
                LocalLog localLog = this.mLocalLog;
                StringBuilder sb2 = new StringBuilder();
                sb2.append(TAG);
                sb2.append("\t:removed at index:");
                sb2.append(i - 1);
                sb2.append(",mSemWifiApBleScanResults.size():");
                sb2.append(this.mSemWifiApBleScanResults.size());
                localLog.log(sb2.toString());
            }
        }
    }

    /* access modifiers changed from: package-private */
    public String usernameFromScanResults(int type, String address) {
        String username;
        synchronized (this.mSemWifiApBleScanResults) {
            int i = 0;
            username = null;
            Iterator<SemWifiApBleScanResult> it = this.mSemWifiApBleScanResults.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                SemWifiApBleScanResult res = it.next();
                i++;
                if (type != 1 || !res.mDevice.equalsIgnoreCase(address)) {
                    if (type == 2 && res.mWifiMac.equalsIgnoreCase(address)) {
                        username = res.mUserName;
                        break;
                    }
                } else {
                    username = res.mUserName;
                    break;
                }
            }
            String str = TAG;
            Log.d(str, "usernameFromScanResults: " + username + " address: " + address);
        }
        return username;
    }

    private void addScanResults(SemWifiApBleScanResult o) {
        synchronized (this.mSemWifiApBleScanResults) {
            this.mSemWifiApBleScanResults.add(o);
        }
    }

    /* access modifiers changed from: package-private */
    public int checkPreConditions() {
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (this.mBluetoothAdapter == null) {
            Log.e(TAG, "mBluetoothAdapter==null");
            LocalLog localLog = this.mLocalLog;
            localLog.log(TAG + ":\t mBluetoothAdapter==null");
            return -7;
        } else if (this.isJDMDevice && SemWifiApMacInfo.getInstance().readWifiMacInfo() == null) {
            Log.e(TAG, "JDM MAC address is null");
            LocalLog localLog2 = this.mLocalLog;
            localLog2.log(TAG + ":\t JDM MAC address is null");
            return -6;
        } else if (!this.mSemWifiApSmartUtil.isPackageExists("com.sec.mhs.smarttethering")) {
            Log.e(TAG, "isPackageExists smarttethering == null");
            setWifiApSmartClient(false);
            return -1;
        } else if (((WifiManager) this.mContext.getSystemService("wifi")).getWifiState() != 3) {
            Log.e(TAG, "not starting scanning ,due to Wi-Fi is OFF");
            return -3;
        } else {
            this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothAdapter bluetoothAdapter = this.mBluetoothAdapter;
            if (bluetoothAdapter == null || !bluetoothAdapter.semIsBleEnabled()) {
                LocalLog localLog3 = this.mLocalLog;
                localLog3.log(TAG + ":\t  Preconditions BLE is OFF");
                Log.i(TAG, "Preconditions BLE is OFF");
                SemEmergencyManager em = SemEmergencyManager.getInstance(this.mContext);
                if (em == null || !em.isEmergencyMode()) {
                    boolean isAirplaneMode = Settings.Global.getInt(this.mContext.getContentResolver(), "airplane_mode_on", 0) != 0;
                    if (isAirplaneMode) {
                        String str = TAG;
                        Log.d(str, "getAirplaneMode: " + isAirplaneMode);
                        return -5;
                    } else if (this.mSemWifiApSmartUtil.isNearByAutohotspotEnabled()) {
                        return 0;
                    } else {
                        Log.d(TAG, "not isNearByAutohotspotEnabled");
                        LocalLog localLog4 = this.mLocalLog;
                        localLog4.log(TAG + ":\t not isNearByAutohotspotEnabled");
                        return -8;
                    }
                } else {
                    Log.i(TAG, "Do not setWifiApSmartClient in EmergencyMode");
                    return -4;
                }
            } else {
                Log.i(TAG, "Preconditions BLE is ON");
                return 0;
            }
        }
    }

    public void startWifiApSmartClientAdvertize() {
        if (FactoryTest.isFactoryBinary()) {
            Log.e(TAG, "This devices's binary is a factory binary");
            return;
        }
        int status = checkPreConditions();
        if (!this.isAdvRunning && status == 0) {
            this.mHashBasedGuid = Long.valueOf(this.mSemWifiApSmartUtil.getHashbasedonGuid());
            this.mHashBasedFamilyID = Long.valueOf(this.mSemWifiApSmartUtil.getHashbasedonFamilyId());
            this.mHashBasedD2DFamilyID = Long.valueOf(this.mSemWifiApSmartUtil.getHashbasedonD2DFamilyid());
            if (this.mHashBasedGuid.longValue() == -1 && this.mHashBasedD2DFamilyID.longValue() == -1) {
                Log.e(TAG, "mHashBasedGuid == null and mHashBasedD2DFamilyID is -1");
                return;
            }
            this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (this.mSemWifiApSmartUtil.isNearByAutohotspotEnabled() && !this.mBluetoothAdapter.semIsBleEnabled()) {
                this.mBluetoothAdapter.semSetStandAloneBleMode(true);
            }
            this.mBluetoothLeAdvertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
            if (this.mBluetoothLeAdvertiser == null) {
                this.isStartAdvPending = true;
                Log.e(TAG, "mBluetoothLeScanner == null, waiting for isStartAdvPending");
                return;
            }
            this.isAdvRunning = true;
            byte[] tClientData = getclientAdvManufactureData();
            String str = TAG;
            Log.d(str, ": Client startWifiApSmartClientAdvertize,mHashBasedGuid:" + this.mHashBasedGuid + ",mHashBasedFamilyID:" + this.mHashBasedFamilyID + ",mHashBasedD2DFamilyID:" + this.mHashBasedD2DFamilyID + "," + Arrays.toString(tClientData));
            LocalLog localLog = this.mLocalLog;
            localLog.log(TAG + ":\tClient startWifiApSmartClientAdvertize,mHashBasedGuid:" + this.mHashBasedGuid + ",mHashBasedFamilyID:" + this.mHashBasedFamilyID + ",mHashBasedD2DFamilyID:" + this.mHashBasedD2DFamilyID + "," + Arrays.toString(tClientData));
            AdvertiseSettings settings = new AdvertiseSettings.Builder().setAdvertiseMode(2).setConnectable(true).setTimeout(0).setTxPowerLevel(3).build();
            AdvertiseData.Builder builder = new AdvertiseData.Builder();
            SemWifiApSmartUtil semWifiApSmartUtil = this.mSemWifiApSmartUtil;
            this.mBluetoothLeAdvertiser.startAdvertising(settings, builder.addManufacturerData(SemWifiApSmartUtil.MANUFACTURE_ID, tClientData).build(), this.mAdvertiseCallback);
        }
    }

    public void stopWifiApSmartClientAdvertize() {
        AdvertiseCallback advertiseCallback;
        Log.d(TAG, "stopWifiApSmartClientAdvertize");
        if (this.isAdvRunning) {
            BluetoothLeAdvertiser bluetoothLeAdvertiser = this.mBluetoothLeAdvertiser;
            if (!(bluetoothLeAdvertiser == null || (advertiseCallback = this.mAdvertiseCallback) == null)) {
                bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
            }
            this.isAdvRunning = false;
            LocalLog localLog = this.mLocalLog;
            localLog.log(TAG + ":\tstopWifiApSmartClientAdvertize");
        }
    }
}

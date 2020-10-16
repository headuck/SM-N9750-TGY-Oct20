package com.samsung.android.server.wifi.softap.smarttethering;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.rtt.RttServiceImpl;
import com.samsung.android.emergencymode.SemEmergencyManager;
import com.samsung.android.net.wifi.SemWifiApBleScanResult;
import com.samsung.android.net.wifi.SemWifiApMacInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SemWifiApSmartD2DMHS {
    private static String TAG = "SemWifiApSmartD2DMHS";
    private static IntentFilter mSemWifiApSmartD2DMHSIntentFilter = new IntentFilter("android.net.wifi.WIFI_STATE_CHANGED");
    private final int CLIENT_BLE_UPDATE_SCAN_DATA_INTERVAL = 20000;
    private final int RESTART_ADVERTISE = 4;
    public final int START_ADVERTISE = 1;
    public final int STOP_ADVERTISE = 2;
    private final int UPDATE_BLE_SCAN_RESULT = 3;
    private boolean isAdvRunning;
    private boolean isJDMDevice = "in_house".contains("jdm");
    private boolean isStartAdvPending;
    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        /* class com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartD2DMHS.C08091 */

        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.d(SemWifiApSmartD2DMHS.TAG, "MHS D2D Advertise Started.");
        }

        public void onStartFailure(int errorCode) {
            String str = SemWifiApSmartD2DMHS.TAG;
            Log.e(str, "MHS D2D Advertise Failed: " + errorCode + ",restarting after 1 sec");
            LocalLog localLog = SemWifiApSmartD2DMHS.this.mLocalLog;
            localLog.log(SemWifiApSmartD2DMHS.TAG + ":\tMHS D2D Advertise Failed: " + errorCode + ",restarting after 1 sec");
            SemWifiApSmartD2DMHS.this.isAdvRunning = false;
            if (SemWifiApSmartD2DMHS.this.mBleWorkHandler != null) {
                SemWifiApSmartD2DMHS.this.mBleWorkHandler.sendEmptyMessageDelayed(4, 1000);
            }
        }
    };
    Map<String, Pair<Long, String>> mBLEPairingFailedHashMap = new ConcurrentHashMap();
    private BleWorkHandler mBleWorkHandler = null;
    private HandlerThread mBleWorkThread = null;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private Context mContext;
    private LocalLog mLocalLog;
    private List<SemWifiApBleScanResult> mSemWifiApBleScanResults = new ArrayList();
    private SemWifiApSmartD2DMHSReceiver mSemWifiApSmartD2DMHSReceiver;
    private SemWifiApSmartUtil mSemWifiApSmartUtil;
    private Set<String> mSmartD2DClientDevices = new HashSet();

    static {
        mSemWifiApSmartD2DMHSIntentFilter.addAction("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED");
        mSemWifiApSmartD2DMHSIntentFilter.addAction("android.intent.action.AIRPLANE_MODE");
        mSemWifiApSmartD2DMHSIntentFilter.addAction("com.samsung.bluetooth.adapter.action.BLE_STATE_CHANGED");
        mSemWifiApSmartD2DMHSIntentFilter.addAction("com.samsung.android.server.wifi.softap.smarttethering.startD2DMHS");
    }

    public SemWifiApSmartD2DMHS(Context context, SemWifiApSmartUtil tSemWifiApSmartUtil, LocalLog tLocalLog) {
        this.mSemWifiApSmartUtil = tSemWifiApSmartUtil;
        this.mLocalLog = tLocalLog;
        this.mContext = context;
        this.mSemWifiApSmartD2DMHSReceiver = new SemWifiApSmartD2DMHSReceiver();
        if (!FactoryTest.isFactoryBinary()) {
            this.mContext.registerReceiver(this.mSemWifiApSmartD2DMHSReceiver, mSemWifiApSmartD2DMHSIntentFilter);
        } else {
            Log.e(TAG, "This devices's binary is a factory binary");
        }
    }

    public void handleBootCompleted() {
        Log.d(TAG, "handleBootCompleted");
        LocalLog localLog = this.mLocalLog;
        localLog.log(TAG + ":\t handleBootCompleted");
        this.mBleWorkThread = new HandlerThread("SemWifiApSmartD2DMHSBleHandler");
        this.mBleWorkThread.start();
        this.mBleWorkHandler = new BleWorkHandler(this.mBleWorkThread.getLooper());
    }

    class SemWifiApSmartD2DMHSReceiver extends BroadcastReceiver {
        SemWifiApSmartD2DMHSReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            boolean isAirplaneMode = true;
            if (action.equals("com.samsung.android.server.wifi.softap.smarttethering.startD2DMHS")) {
                if (intent.getIntExtra("status", -1) == 1) {
                    SemWifiApSmartD2DMHS.this.sendEmptyMessage(1);
                } else {
                    SemWifiApSmartD2DMHS.this.sendEmptyMessage(2);
                }
            } else if (action.equals("android.intent.action.AIRPLANE_MODE")) {
                if (Settings.Global.getInt(SemWifiApSmartD2DMHS.this.mContext.getContentResolver(), "airplane_mode_on", 0) == 0) {
                    isAirplaneMode = false;
                }
                Log.d(SemWifiApSmartD2DMHS.TAG, "isAirplaneMode:" + isAirplaneMode);
                SemWifiApSmartD2DMHS.this.mLocalLog.log(SemWifiApSmartD2DMHS.TAG + ":AIRPLANE_MODE" + isAirplaneMode);
                if (isAirplaneMode) {
                    SemWifiApSmartD2DMHS.this.sendEmptyMessage(2);
                }
            } else if (action.equals("com.samsung.bluetooth.adapter.action.BLE_STATE_CHANGED")) {
                int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE);
                if (SemWifiApSmartD2DMHS.this.isAdvRunning) {
                    SemWifiApSmartD2DMHS.this.isStartAdvPending = false;
                }
                if (SemWifiApSmartD2DMHS.this.isStartAdvPending && state == 15) {
                    SemWifiApSmartD2DMHS.this.isStartAdvPending = false;
                    Log.d(SemWifiApSmartD2DMHS.TAG, "BLE is ON, starting advertizement");
                    SemWifiApSmartD2DMHS.this.mLocalLog.log(SemWifiApSmartD2DMHS.TAG + ":\t BLE is ON, starting advertizement");
                    SemWifiApSmartD2DMHS.this.sendEmptyMessage(1);
                }
            } else if (action.equals("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED")) {
                boolean emergencyMode = intent.getBooleanExtra("phoneinECMState", false);
                Log.d(SemWifiApSmartD2DMHS.TAG, "emergencyMode:" + emergencyMode);
                SemWifiApSmartD2DMHS.this.mLocalLog.log(SemWifiApSmartD2DMHS.TAG + ": EMERGENCY" + emergencyMode);
                if (emergencyMode) {
                    SemWifiApSmartD2DMHS.this.sendEmptyMessage(2);
                }
            }
        }
    }

    /* access modifiers changed from: package-private */
    public int checkPreConditions() {
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (this.mBluetoothAdapter == null) {
            Log.e(TAG, "mBluetoothAdapter==null");
            LocalLog localLog = this.mLocalLog;
            localLog.log(TAG + ":\t mBluetoothAdapter==null");
            return -5;
        } else if (this.isJDMDevice && SemWifiApMacInfo.getInstance().readWifiMacInfo() == null) {
            Log.e(TAG, "JDM MAC address is null");
            LocalLog localLog2 = this.mLocalLog;
            localLog2.log(TAG + ":\t JDM MAC address is null");
            return -4;
        } else if (!this.mSemWifiApSmartUtil.isPackageExists("com.sec.mhs.smarttethering")) {
            Log.e(TAG, "isPackageExists smarttethering == null");
            return -1;
        } else {
            this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothAdapter bluetoothAdapter = this.mBluetoothAdapter;
            if (bluetoothAdapter == null || !bluetoothAdapter.semIsBleEnabled()) {
                SemEmergencyManager em = SemEmergencyManager.getInstance(this.mContext);
                if (em == null || !em.isEmergencyMode()) {
                    boolean isAirplaneMode = Settings.Global.getInt(this.mContext.getContentResolver(), "airplane_mode_on", 0) != 0;
                    if (isAirplaneMode) {
                        String str = TAG;
                        Log.d(str, "getAirplaneMode: " + isAirplaneMode);
                        return -3;
                    } else if (this.mSemWifiApSmartUtil.isNearByAutohotspotEnabled()) {
                        return 0;
                    } else {
                        Log.d(TAG, "not isNearByAutohotspotEnabled");
                        LocalLog localLog3 = this.mLocalLog;
                        localLog3.log(TAG + ":\t not isNearByAutohotspotEnabled");
                        return -6;
                    }
                } else {
                    Log.i(TAG, "Do not setWifiApSmartClient in EmergencyMode");
                    return -2;
                }
            } else {
                Log.i(TAG, "Preconditions BLE is ON");
                LocalLog localLog4 = this.mLocalLog;
                localLog4.log(TAG + ":\t  Preconditions BLE is ON");
                return 0;
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
            if (i == 1) {
                int status = SemWifiApSmartD2DMHS.this.checkPreConditions();
                if (status == 0) {
                    SemWifiApSmartD2DMHS.this.clearLocalResults();
                    Settings.Secure.putInt(SemWifiApSmartD2DMHS.this.mContext.getContentResolver(), "wifi_ap_smart_d2d_mhs", 1);
                    SemWifiApSmartD2DMHS.this.startWifiApSmartD2DMHSAdvertize();
                    sendEmptyMessageDelayed(3, 20000);
                    return;
                }
                String str = SemWifiApSmartD2DMHS.TAG;
                Log.e(str, "checkPreConditions failed " + status);
                LocalLog localLog = SemWifiApSmartD2DMHS.this.mLocalLog;
                localLog.log(SemWifiApSmartD2DMHS.TAG + ":\tcheckPreConditions failed " + status);
            } else if (i == 2) {
                Settings.Secure.putInt(SemWifiApSmartD2DMHS.this.mContext.getContentResolver(), "wifi_ap_smart_d2d_mhs", 0);
                SemWifiApSmartD2DMHS.this.stopWifiApSmartD2DMHSAdvertize();
                SemWifiApSmartD2DMHS.this.clearLocalResults();
            } else if (i == 3) {
                synchronized (SemWifiApSmartD2DMHS.this.mSemWifiApBleScanResults) {
                    num = SemWifiApSmartD2DMHS.this.mSemWifiApBleScanResults.size();
                }
                if (SemWifiApSmartD2DMHS.this.isAdvRunning || num != 0) {
                    SemWifiApSmartD2DMHS.this.updateLocalResults();
                    sendEmptyMessageDelayed(3, 20000);
                    return;
                }
                String str2 = SemWifiApSmartD2DMHS.TAG;
                Log.e(str2, "Not updating BLE scan result isAdvRunning " + SemWifiApSmartD2DMHS.this.isAdvRunning + " ,size: " + num);
                SemWifiApSmartD2DMHS.this.clearLocalResults();
                removeMessages(3);
            } else if (i == 4 && Settings.Secure.getInt(SemWifiApSmartD2DMHS.this.mContext.getContentResolver(), "wifi_ap_smart_d2d_mhs", 0) == 1) {
                int status2 = SemWifiApSmartD2DMHS.this.checkPreConditions();
                if (status2 == 0) {
                    SemWifiApSmartD2DMHS.this.clearLocalResults();
                    SemWifiApSmartD2DMHS.this.startWifiApSmartD2DMHSAdvertize();
                    sendEmptyMessageDelayed(3, 20000);
                    return;
                }
                String str3 = SemWifiApSmartD2DMHS.TAG;
                Log.e(str3, "checkPreConditions failed " + status2);
                LocalLog localLog2 = SemWifiApSmartD2DMHS.this.mLocalLog;
                localLog2.log(SemWifiApSmartD2DMHS.TAG + ":\tcheckPreConditions failed " + status2);
            }
        }
    }

    public boolean semWifiApBleD2DMhsRole(boolean enable) {
        if (enable) {
            sendEmptyMessage(1);
        } else if (!enable) {
            sendEmptyMessage(2);
        }
        return true;
    }

    public void sendEmptyMessage(int val) {
        BleWorkHandler bleWorkHandler = this.mBleWorkHandler;
        if (bleWorkHandler != null) {
            bleWorkHandler.sendEmptyMessage(val);
        }
    }

    /* access modifiers changed from: package-private */
    /* JADX WARNING: Code restructure failed: missing block: B:68:0x041d, code lost:
        if (r10.equalsIgnoreCase(r12.mDevice) == false) goto L_0x0445;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:71:0x0425, code lost:
        if (r0.equalsIgnoreCase(r12.mSSID) == false) goto L_0x0445;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:72:0x0427, code lost:
        if (r14 != null) goto L_0x042b;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:73:0x0429, code lost:
        if (r4 != null) goto L_0x0445;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:74:0x042b, code lost:
        if (r14 == null) goto L_0x042f;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:75:0x042d, code lost:
        if (r4 == null) goto L_0x0445;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:76:0x042f, code lost:
        if (r14 == null) goto L_0x043d;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:77:0x0431, code lost:
        if (r4 == null) goto L_0x043d;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:79:0x0437, code lost:
        if (r14.equals(r4) != false) goto L_0x043a;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:82:0x0440, code lost:
        r0 = th;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:84:0x0445, code lost:
        r1.mSemWifiApSmartUtil.sendClientScanResultUpdateIntent("D2DMHS ScanResult event");
        r0 = com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartD2DMHS.TAG;
        r15 = new java.lang.StringBuilder();
     */
    /* JADX WARNING: Code restructure failed: missing block: B:86:?, code lost:
        r15.append("updating mClientDeviceName:");
        r15.append(r0);
        r15.append(",BT_MAC:");
        r15.append(r10);
        r15.append(",mD2D_ClientMAC");
        r15.append(r5);
        android.util.Log.d(r0, r15.toString());
        r1.mLocalLog.log(com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartD2DMHS.TAG + "\t updating mClientDeviceName:" + r0 + ",BT_MAC:" + r10 + ",mD2D_ClientMAC" + r5);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:87:0x049f, code lost:
        r12.mUserName = r4;
        r12.mDevice = r10;
        r12.mSSID = r0;
        r12.mTimeStamp = java.lang.System.currentTimeMillis();
     */
    public void sendScanResultFromScanner(ScanResult result) {
        long diff;
        String mScannedD2DfamilyId;
        SemWifiApSmartD2DMHS semWifiApSmartD2DMHS = this;
        ScanRecord sr = result.getScanRecord();
        byte[] mScanResultData = sr.getManufacturerSpecificData(SemWifiApSmartUtil.MANUFACTURE_ID);
        String mD2D_ClientMAC = semWifiApSmartD2DMHS.mSemWifiApSmartUtil.getActualMACFrom_mappedMAC((((((((((((((((Character.toString(Character.forDigit((mScanResultData[2] & 240) >> 4, 16)) + Character.toString(Character.forDigit(mScanResultData[2] & 15, 16))) + ":") + Character.toString(Character.forDigit((mScanResultData[3] & 240) >> 4, 16))) + Character.toString(Character.forDigit(mScanResultData[3] & 15, 16))) + ":") + Character.toString(Character.forDigit((mScanResultData[4] & 240) >> 4, 16))) + Character.toString(Character.forDigit(mScanResultData[4] & 15, 16))) + ":") + Character.toString(Character.forDigit((mScanResultData[5] & 240) >> 4, 16))) + Character.toString(Character.forDigit(mScanResultData[5] & 15, 16))) + ":") + Character.toString(Character.forDigit((mScanResultData[6] & 240) >> 4, 16))) + Character.toString(Character.forDigit(mScanResultData[6] & 15, 16))) + ":") + Character.toString(Character.forDigit((mScanResultData[7] & 240) >> 4, 16))) + Character.toString(Character.forDigit(mScanResultData[7] & 15, 16)));
        String mBLE_MAC = result.getDevice().getAddress();
        Pair<Long, String> tpair = semWifiApSmartD2DMHS.mBLEPairingFailedHashMap.get(mD2D_ClientMAC);
        if (tpair != null) {
            diff = System.currentTimeMillis() - ((Long) tpair.first).longValue();
        } else {
            diff = -1;
        }
        if (tpair == null || mBLE_MAC == null || tpair.second == null || ((!mBLE_MAC.equals(tpair.second) || diff >= 60000) && (mBLE_MAC.equals(tpair.second) || diff >= RttServiceImpl.HAL_RANGING_TIMEOUT_MS))) {
            if (tpair != null) {
                semWifiApSmartD2DMHS.mBLEPairingFailedHashMap.remove(mD2D_ClientMAC);
            }
            if (mScanResultData[8] == 1) {
                String mScannedD2DfamilyId2 = new String(mScanResultData, 11, 4);
                long familyID = semWifiApSmartD2DMHS.mSemWifiApSmartUtil.getHashbasedonFamilyId();
                if (familyID != -1) {
                    byte[] familyBytes = SemWifiApSmartUtil.bytesFromLong(Long.valueOf(familyID));
                    if (!new String(new byte[]{familyBytes[0], familyBytes[2], familyBytes[4], familyBytes[6]}, 0, 4).equals(mScannedD2DfamilyId2) || !SemWifiApSmartWhiteList.getInstance().isContains(mD2D_ClientMAC)) {
                        mScannedD2DfamilyId = mScannedD2DfamilyId2;
                    } else if (semWifiApSmartD2DMHS.mSmartD2DClientDevices.contains(mD2D_ClientMAC)) {
                        Log.i(TAG, "Same familyID");
                        semWifiApSmartD2DMHS.removeFromScanResults(mD2D_ClientMAC);
                        return;
                    } else {
                        return;
                    }
                } else {
                    mScannedD2DfamilyId = mScannedD2DfamilyId2;
                }
            } else {
                mScannedD2DfamilyId = null;
            }
            byte[] mtDevicename = new byte[50];
            int ntIndex = 0;
            int i = 15;
            while (i < 24 && i < mScanResultData.length && mScanResultData[i] != 0) {
                mtDevicename[ntIndex] = mScanResultData[i];
                i++;
                ntIndex++;
            }
            int i2 = 26;
            while (i2 < 51 && i2 < mScanResultData.length && mScanResultData[i2] != 0) {
                ntIndex++;
                mtDevicename[ntIndex] = mScanResultData[i2];
                i2++;
            }
            semWifiApSmartD2DMHS.mLocalLog.log(TAG + ":\tmScanResultData:" + Arrays.toString(mScanResultData));
            Log.d(TAG, "mScanResultData:" + Arrays.toString(mScanResultData));
            String mClientDeviceName = new String(mtDevicename, 0, ntIndex);
            if (mD2D_ClientMAC != null && semWifiApSmartD2DMHS.mSmartD2DClientDevices.add(mD2D_ClientMAC)) {
                String BT_MAC = result.getDevice().getAddress();
                SemWifiApBleScanResult temp = new SemWifiApBleScanResult(BT_MAC, 0, 0, 0, 2, mD2D_ClientMAC, mScannedD2DfamilyId, mClientDeviceName, 0, 0, System.currentTimeMillis(), 0, 0);
                Log.d(TAG, "adding mClientDeviceName:" + mClientDeviceName + ",BT_MAC:" + BT_MAC + ",mD2D_ClientMAC" + mD2D_ClientMAC);
                semWifiApSmartD2DMHS.mLocalLog.log(TAG + "\tadding mClientDeviceName:" + mClientDeviceName + ",BT_MAC:" + BT_MAC + ",mD2D_ClientMAC" + mD2D_ClientMAC);
                semWifiApSmartD2DMHS.addScanResults(temp);
            } else if (mD2D_ClientMAC != null && !semWifiApSmartD2DMHS.mSmartD2DClientDevices.add(mD2D_ClientMAC)) {
                String BT_MAC2 = result.getDevice().getAddress();
                synchronized (semWifiApSmartD2DMHS.mSemWifiApBleScanResults) {
                    try {
                        Iterator<SemWifiApBleScanResult> it = semWifiApSmartD2DMHS.mSemWifiApBleScanResults.iterator();
                        while (true) {
                            if (!it.hasNext()) {
                                break;
                            }
                            SemWifiApBleScanResult res = it.next();
                            String oldScannedString = res.mUserName;
                            if (res.mWifiMac.equalsIgnoreCase(mD2D_ClientMAC)) {
                                break;
                            }
                            semWifiApSmartD2DMHS = this;
                            sr = sr;
                        }
                    } catch (Throwable th) {
                        th = th;
                        throw th;
                    }
                }
            }
        } else {
            Log.d(TAG, "new mBLE_MAC:" + mBLE_MAC + ",diff:" + diff + ",old BLE mac:" + ((String) tpair.second));
            semWifiApSmartD2DMHS.mLocalLog.log(TAG + ":\tnew mBLE_MAC:" + mBLE_MAC + ",diff:" + diff + ",old BLE mac:" + ((String) tpair.second));
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void clearLocalResults() {
        synchronized (this.mSemWifiApBleScanResults) {
            this.mSemWifiApBleScanResults.clear();
            this.mSmartD2DClientDevices.clear();
            this.mSemWifiApSmartUtil.sendClientScanResultUpdateIntent("D2DMHS ClearLocalResults");
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
                    String clientmac = this.mSemWifiApBleScanResults.get(j).mWifiMac;
                    if (clientmac != null) {
                        String str = TAG;
                        Log.d(str, "removed:" + clientmac);
                        this.mSmartD2DClientDevices.remove(clientmac);
                    }
                    this.mSemWifiApBleScanResults.remove(j);
                }
                if (mInt.size() > 0) {
                    this.mSemWifiApSmartUtil.sendClientScanResultUpdateIntent("D2DMHS updateLocalResults");
                }
            } catch (IndexOutOfBoundsException e) {
            }
        }
    }

    private void removeFromScanResults(String mac) {
        synchronized (this.mSemWifiApBleScanResults) {
            int j = -1;
            int count = 0;
            for (SemWifiApBleScanResult res : this.mSemWifiApBleScanResults) {
                if (res.mWifiMac.equals(mac)) {
                    j = count;
                }
                count++;
            }
            if (j != -1) {
                this.mSmartD2DClientDevices.remove(mac);
                this.mSemWifiApBleScanResults.remove(j);
            }
        }
    }

    private void addScanResults(SemWifiApBleScanResult o) {
        synchronized (this.mSemWifiApBleScanResults) {
            this.mSemWifiApBleScanResults.add(o);
            this.mSemWifiApSmartUtil.sendClientScanResultUpdateIntent("D2D MHS addScanResults");
        }
    }

    /* JADX WARNING: Code restructure failed: missing block: B:13:0x000c, code lost:
        r1 = th;
     */
    public synchronized List<SemWifiApBleScanResult> getWifiApBleD2DScanResults() {
        List<SemWifiApBleScanResult> list;
        synchronized (this.mSemWifiApBleScanResults) {
            list = this.mSemWifiApBleScanResults;
        }
        return list;
        while (true) {
        }
    }

    private byte[] getmhsD2DManufactureData() {
        byte[] data = new byte[24];
        for (int i = 0; i < 24; i++) {
            data[i] = 0;
        }
        data[0] = 1;
        data[1] = 18;
        data[10] = 3;
        return data;
    }

    public void startWifiApSmartD2DMHSAdvertize() {
        if (FactoryTest.isFactoryBinary()) {
            Log.e(TAG, "This devices's binary is a factory binary");
            return;
        }
        int status = checkPreConditions();
        String str = TAG;
        Log.d(str, " startWifiApSmartD2DMHSAdvertize : status:" + status + ",isAdvRunning:" + this.isAdvRunning);
        if (!this.isAdvRunning && status == 0) {
            this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (this.mSemWifiApSmartUtil.isNearByAutohotspotEnabled() && !this.mBluetoothAdapter.semIsBleEnabled()) {
                this.mBluetoothAdapter.semSetStandAloneBleMode(true);
            }
            this.mBluetoothLeAdvertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
            if (this.mBluetoothLeAdvertiser == null) {
                this.isStartAdvPending = true;
                Log.e(TAG, "mBluetoothLeAdvertiser == null, waiting for isStartAdvPending");
                return;
            }
            this.isAdvRunning = true;
            WifiInjector.getInstance().getSemWifiApSmartBleScanner().startBleScanning();
            AdvertiseSettings settings = new AdvertiseSettings.Builder().setAdvertiseMode(2).setConnectable(true).setTimeout(0).setTxPowerLevel(3).build();
            AdvertiseData.Builder builder = new AdvertiseData.Builder();
            SemWifiApSmartUtil semWifiApSmartUtil = this.mSemWifiApSmartUtil;
            AdvertiseData data = builder.addManufacturerData(SemWifiApSmartUtil.MANUFACTURE_ID, getmhsD2DManufactureData()).build();
            LocalLog localLog = this.mLocalLog;
            localLog.log(TAG + ":\tstartWifiApSmartD2DMHSAdvertize" + Arrays.toString(getmhsD2DManufactureData()));
            String str2 = TAG;
            Log.d(str2, "Started startWifiApSmartD2DMHSAdvertize with " + Arrays.toString(getmhsD2DManufactureData()));
            this.mBluetoothLeAdvertiser.startAdvertising(settings, data, this.mAdvertiseCallback);
        }
    }

    /* access modifiers changed from: package-private */
    public void stopWifiApSmartD2DMHSAdvertize() {
        AdvertiseCallback advertiseCallback;
        if (this.isAdvRunning) {
            Log.d(TAG, "stopWifiApSmartD2DMHSAdvertize");
            BluetoothLeAdvertiser bluetoothLeAdvertiser = this.mBluetoothLeAdvertiser;
            if (!(bluetoothLeAdvertiser == null || (advertiseCallback = this.mAdvertiseCallback) == null)) {
                bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
            }
            this.isAdvRunning = false;
            this.mBLEPairingFailedHashMap.clear();
            LocalLog localLog = this.mLocalLog;
            localLog.log(TAG + ":stopWifiApSmartD2DMHSAdvertize");
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
}

package com.samsung.android.server.wifi.softap.smarttethering;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.wifi.SemWifiApSmartWhiteList;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Debug;
import android.os.FactoryTest;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.LocalLog;
import android.util.Log;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.iwc.IWCEventManager;
import com.android.server.wifi.rtt.RttServiceImpl;
import com.samsung.android.emergencymode.SemEmergencyManager;
import com.samsung.android.feature.SemCscFeature;
import com.samsung.android.net.wifi.SemWifiApContentProviderHelper;
import com.samsung.android.net.wifi.SemWifiApMacInfo;
import com.samsung.android.server.wifi.SemWifiConstants;
import com.samsung.android.server.wifi.softap.SemWifiApPowerSaveImpl;
import com.sec.android.app.CscFeatureTagWifi;
import java.util.ArrayList;
import java.util.List;

public class SemWifiApSmartBleScanner {
    private static final String ACTION_FAMILY_GROUP_ACCEPT_INVITE_PUSH = "com.samsung.android.mobileservice.social.ACTION_FAMILY_GROUP_ACCEPT_INVITE_PUSH";
    private static final String ACTION_FAMILY_GROUP_DELETE_PUSH = "com.samsung.android.mobileservice.social.ACTION_FAMILY_GROUP_DELETE_PUSH";
    private static final String ACTION_FAMILY_GROUP_FORCE_MEMBER_DELETE_PUSH = "com.samsung.android.mobileservice.social.ACTION_FAMILY_GROUP_FORCE_MEMBER_DELETE_PUSH";
    private static final String ACTION_FAMILY_GROUP_I_ACCEPT_INVITE_PUSH = "com.samsung.android.mobileservice.social.ACTION_FAMILY_GROUP_I_ACCEPT_INVITE_PUSH";
    private static final String ACTION_FAMILY_GROUP_I_CREATE_GROUP_PUSH = "com.samsung.android.mobileservice.social.ACTION_FAMILY_GROUP_I_CREATE_GROUP_PUSH";
    private static final String ACTION_FAMILY_GROUP_I_DELETE_PUSH = "com.samsung.android.mobileservice.social.ACTION_FAMILY_GROUP_I_DELETE_PUSH";
    private static final String ACTION_FAMILY_GROUP_I_FORCE_MEMBER_DELETE_PUSH = "com.samsung.android.mobileservice.social.ACTION_FAMILY_GROUP_I_FORCE_MEMBER_DELETE_PUSH";
    private static final String ACTION_FAMILY_GROUP_I_MEMBER_DELETE_PUSH = "com.samsung.android.mobileservice.social.ACTION_FAMILY_GROUP_I_MEMBER_DELETE_PUSH";
    private static final String ACTION_FAMILY_GROUP_MEMBER_DELETE_PUSH = "com.samsung.android.mobileservice.social.ACTION_FAMILY_GROUP_MEMBER_DELETE_PUSH";
    private static final String ACTION_LOGIN_ACCOUNTS_COMPLETE = "com.samsung.account.SAMSUNGACCOUNT_SIGNIN_COMPLETED";
    private static final String ACTION_LOGOUT_ACCOUNTS_COMPLETE = "com.samsung.account.SAMSUNGACCOUNT_SIGNOUT_COMPLETED";
    private static final String ACTION_NEARBY_SCANNING = "com.samsung.android.nearbyscanning";
    public static final int CHECK_GUID_STATUS_START_SCAN = 4;
    public static final int CHECK_TO_STOP_D2D_CLIENT_ADV = 6;
    public static final int CHECK_TO_STOP_MHS_ADV = 5;
    public static final String CONFIGOPBRANDINGFORMOBILEAP = SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGOPBRANDINGFORMOBILEAP, "ALL");
    public static final int START_SCAN = 2;
    public static final int STOP_SCAN = 3;
    public static final boolean SUPPORTMOBILEAPENHANCED_D2D = "d2d".equals("d2d");
    public static final boolean SUPPORTMOBILEAPENHANCED_LITE = "d2d".equals("lite");
    public static final boolean SUPPORTMOBILEAPENHANCED_WIFI_ONLY_LITE = "d2d".equals("wifi_only_lite");
    private static String TAG = "SemWifiApSmartBleScanner";
    private static IntentFilter mSemWifiApSmartBleScannerIntentFilter = new IntentFilter("com.samsung.bluetooth.adapter.action.BLE_STATE_CHANGED");
    private final int AIRPLANE_MODE_OFF_RESTART_INTERVAL;
    private final int D2D_CLIENT_ADV_STOP_INTERVAL;
    private boolean DBG;
    private final int FAMILYID_CHANGE_RESTART_INTERVAL;
    private final int HANDLE_BLE_SCAN_RESULT;
    private final int LOGIN_RESTART_INTERVAL;
    private final int LOGOUT_RESTART_INTERVAL;
    private final int MHS_ADV_STOP_INTERVAL;
    private final int SemWifiApScanInterval;
    private final int SemWifiApscanWindow_LCDOFF;
    private final int SemWifiApscanWindow_LCDON;
    private boolean isAutoHotspotBleSet;
    private boolean isJDMDevice;
    private boolean isLcdOn;
    private boolean isScanningRunning;
    private boolean isStartScanningPending;
    private long last_client_adv_time;
    private BleWorkHandler mBleWorkHandler;
    private HandlerThread mBleWorkThread;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    ScanFilter mClientD2dFilter;
    private Context mContext;
    ScanFilter mFamilyScanFilter;
    private boolean mFamilySharingSavedState;
    ScanFilter mGuidScanFilter;
    private LocalLog mLocalLog;
    ScanFilter mMhsD2dFilter;
    private SemWifiApSmartBleScannerCallback mSemWifiApSmartBleScannerCallback;
    private SemWifiApSmartBleScannerReceiver mSemWifiApSmartBleScannerReceiver;
    private SemWifiApSmartClient mSemWifiApSmartClient;
    private ContentObserver mSemWifiApSmartFamilySwitchObserver;
    private SemWifiApSmartGattServer mSemWifiApSmartGattServer;
    private SemWifiApSmartMHS mSemWifiApSmartMHS;
    private SemWifiApSmartUtil mSemWifiApSmartUtil;
    private ContentObserver mSemWifiApSmart_AutoHotSpot_SwitchObserver;
    private ContentObserver mSemWifiApSmart_Client_SwitchObserver;
    private ContentObserver mSemWifiApSmart_D2D_SwitchObserver;
    private int mSemWifiApscanWindow;
    private boolean mWifiEnabled;
    private boolean needToEnableAutoHotspot;
    List<ScanFilter> scanFilters;

    static {
        mSemWifiApSmartBleScannerIntentFilter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        mSemWifiApSmartBleScannerIntentFilter.addAction("android.intent.action.AIRPLANE_MODE");
        mSemWifiApSmartBleScannerIntentFilter.addAction("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED");
        mSemWifiApSmartBleScannerIntentFilter.addAction(ACTION_LOGOUT_ACCOUNTS_COMPLETE);
        mSemWifiApSmartBleScannerIntentFilter.addAction(ACTION_FAMILY_GROUP_ACCEPT_INVITE_PUSH);
        mSemWifiApSmartBleScannerIntentFilter.addAction(ACTION_FAMILY_GROUP_DELETE_PUSH);
        mSemWifiApSmartBleScannerIntentFilter.addAction(ACTION_FAMILY_GROUP_MEMBER_DELETE_PUSH);
        mSemWifiApSmartBleScannerIntentFilter.addAction(ACTION_FAMILY_GROUP_FORCE_MEMBER_DELETE_PUSH);
        mSemWifiApSmartBleScannerIntentFilter.addAction(ACTION_FAMILY_GROUP_I_ACCEPT_INVITE_PUSH);
        mSemWifiApSmartBleScannerIntentFilter.addAction(ACTION_FAMILY_GROUP_I_CREATE_GROUP_PUSH);
        mSemWifiApSmartBleScannerIntentFilter.addAction(ACTION_FAMILY_GROUP_I_DELETE_PUSH);
        mSemWifiApSmartBleScannerIntentFilter.addAction(ACTION_FAMILY_GROUP_I_FORCE_MEMBER_DELETE_PUSH);
        mSemWifiApSmartBleScannerIntentFilter.addAction(ACTION_FAMILY_GROUP_I_MEMBER_DELETE_PUSH);
        mSemWifiApSmartBleScannerIntentFilter.addAction(ACTION_LOGIN_ACCOUNTS_COMPLETE);
        mSemWifiApSmartBleScannerIntentFilter.addAction(ACTION_LOGOUT_ACCOUNTS_COMPLETE);
        mSemWifiApSmartBleScannerIntentFilter.addAction("com.samsung.android.server.wifi.softap.smarttethering.changed");
        mSemWifiApSmartBleScannerIntentFilter.addAction("com.samsung.android.server.wifi.softap.smarttethering.familyid");
        mSemWifiApSmartBleScannerIntentFilter.addAction("com.samsung.android.server.wifi.softap.smarttethering.d2dfamilyid");
        mSemWifiApSmartBleScannerIntentFilter.addAction("android.net.conn.INET_CONDITION_ACTION");
        mSemWifiApSmartBleScannerIntentFilter.addAction("android.intent.action.SCREEN_ON");
        mSemWifiApSmartBleScannerIntentFilter.addAction("android.intent.action.SCREEN_OFF");
        mSemWifiApSmartBleScannerIntentFilter.addAction(ACTION_NEARBY_SCANNING);
        mSemWifiApSmartBleScannerIntentFilter.addAction("android.intent.action.SIM_STATE_CHANGED");
        mSemWifiApSmartBleScannerIntentFilter.addAction("com.samsung.intent.action.SETTINGS_SOFT_RESET");
        mSemWifiApSmartBleScannerIntentFilter.addAction(SemWifiApPowerSaveImpl.ACTION_SCREEN_ON_BY_PROXIMITY);
        mSemWifiApSmartBleScannerIntentFilter.addAction(SemWifiApPowerSaveImpl.ACTION_SCREEN_OFF_BY_PROXIMITY);
        mSemWifiApSmartBleScannerIntentFilter.addAction("android.telephony.action.CARRIER_CONFIG_CHANGED");
    }

    public SemWifiApSmartBleScanner(Context context, SemWifiApSmartUtil tSemWifiApSmartUtil, LocalLog tLocalLog) {
        this.DBG = "eng".equals(Build.TYPE) || Debug.semIsProductDev();
        this.mBleWorkHandler = null;
        this.mBleWorkThread = null;
        this.SemWifiApScanInterval = 3120;
        this.SemWifiApscanWindow_LCDON = SemWifiConstants.ROUTER_OUI_TYPE;
        this.SemWifiApscanWindow_LCDOFF = 55;
        this.last_client_adv_time = 0;
        this.HANDLE_BLE_SCAN_RESULT = 1;
        this.mGuidScanFilter = null;
        this.mFamilyScanFilter = null;
        this.mMhsD2dFilter = null;
        this.mClientD2dFilter = null;
        this.LOGIN_RESTART_INTERVAL = 40000;
        this.LOGOUT_RESTART_INTERVAL = IWCEventManager.wifiOFFPending_MS;
        this.AIRPLANE_MODE_OFF_RESTART_INTERVAL = 2000;
        this.FAMILYID_CHANGE_RESTART_INTERVAL = 5000;
        this.MHS_ADV_STOP_INTERVAL = 60000;
        this.D2D_CLIENT_ADV_STOP_INTERVAL = 30000;
        this.isJDMDevice = "in_house".contains("jdm");
        this.scanFilters = new ArrayList();
        this.mWifiEnabled = false;
        this.needToEnableAutoHotspot = false;
        this.isAutoHotspotBleSet = false;
        this.mFamilySharingSavedState = false;
        this.mSemWifiApSmartFamilySwitchObserver = new ContentObserver(new Handler()) {
            /* class com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartBleScanner.C07991 */

            public void onChange(boolean selfChange) {
                int ST = Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 0);
                int val = Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings_with_family", 0);
                String str = SemWifiApSmartBleScanner.TAG;
                Log.i(str, "mSemWifiApSmartMHSObserver family is [" + val + "]");
                int count = SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.getSamsungAccountCount();
                if (val == 1 && count != 0) {
                    Settings.Secure.putInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "autohotspot_family_sharing_saved_state", 0);
                    SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.startSmartTetheringApk(true, true, null);
                }
                if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null && SemWifiApSmartBleScanner.this.isScanningRunning) {
                    LocalLog localLog = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog.log(SemWifiApSmartBleScanner.TAG + ":\t switch observer ST :" + ST + ",family: " + val + ",AC cnt:" + count + ",isScanningRunning:" + SemWifiApSmartBleScanner.this.isScanningRunning);
                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                    SemWifiApSmartBleScanner.this.mSemWifiApSmartMHS.sendEmptyMessage(11);
                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(2, 1000);
                }
            }
        };
        this.mSemWifiApSmart_AutoHotSpot_SwitchObserver = new ContentObserver(new Handler()) {
            /* class com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartBleScanner.C08002 */

            public void onChange(boolean selfChange) {
                int MST = Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 0);
                int CST = Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_client_smart_tethering_settings", 0);
                int D2D = Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_d2d_mhs", 0);
                boolean isNearByEnabled = Settings.System.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "nearby_scanning_enabled", 0) == 1;
                if (SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.getSamsungAccountCount() == 0 && MST == 1) {
                    Settings.Secure.putInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 0);
                    Log.w(SemWifiApSmartBleScanner.TAG, "mSemWifiApSmart_AutoHotSpot_SwitchObserver , not logged in but MST 1");
                    LocalLog localLog = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog.log(SemWifiApSmartBleScanner.TAG + ":\t mSemWifiApSmart_AutoHotSpot_SwitchObserver , not logged in but MST 1");
                    return;
                }
                String str = SemWifiApSmartBleScanner.TAG;
                Log.i(str, "mSemWifiApSmart_AutoHotSpot_SwitchObserver is [" + MST + "]");
                LocalLog localLog2 = SemWifiApSmartBleScanner.this.mLocalLog;
                localLog2.log(SemWifiApSmartBleScanner.TAG + ":\t mSemWifiApSmart_AutoHotSpot_SwitchObserver  MST :" + MST + ",CST: " + CST + ",D2D:" + D2D + ",isNearByEnabled:" + isNearByEnabled);
                if (MST == 1) {
                    Settings.Secure.putInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "autohotspot_saved_state", 0);
                    SemWifiApSmartBleScanner.this.needToEnableAutoHotspot = false;
                }
                if (SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.isNearByAutohotspotEnabled()) {
                    if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(2, 500);
                    }
                } else if (CST == 0 && MST == 0 && D2D == 0) {
                    if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                    }
                } else if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(2);
                }
            }
        };
        this.mSemWifiApSmart_Client_SwitchObserver = new ContentObserver(new Handler()) {
            /* class com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartBleScanner.C08013 */

            public void onChange(boolean selfChange) {
                boolean isNearByEnabled = false;
                int MST = Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 0);
                int CST = Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_client_smart_tethering_settings", 0);
                int D2D = Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_d2d_mhs", 0);
                if (Settings.System.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "nearby_scanning_enabled", 0) == 1) {
                    isNearByEnabled = true;
                }
                Log.i(SemWifiApSmartBleScanner.TAG, "mSemWifiApSmart_Client_SwitchObserver is [" + CST + "]");
                SemWifiApSmartBleScanner.this.mLocalLog.log(SemWifiApSmartBleScanner.TAG + ":\t mSemWifiApSmart_Client_SwitchObserver  MST :" + MST + ",CST: " + CST + ",D2D:" + D2D + ",isNearByEnabled:" + isNearByEnabled);
                if (SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.getSamsungAccountCount() != 0 || !isNearByEnabled) {
                    if (CST == 0 && MST == 0 && D2D == 0) {
                        if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                            SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                        }
                    } else if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(2);
                    }
                } else if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(2, 500);
                }
            }
        };
        this.mSemWifiApSmart_D2D_SwitchObserver = new ContentObserver(new Handler()) {
            /* class com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartBleScanner.C08024 */

            public void onChange(boolean selfChange) {
                boolean isNearByEnabled = false;
                int MST = Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 0);
                int CST = Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_client_smart_tethering_settings", 0);
                int D2D = Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_d2d_mhs", 0);
                if (Settings.System.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "nearby_scanning_enabled", 0) == 1) {
                    isNearByEnabled = true;
                }
                Log.i(SemWifiApSmartBleScanner.TAG, "mSemWifiApSmart_D2D_SwitchObserver is [" + D2D + "]");
                SemWifiApSmartBleScanner.this.mLocalLog.log(SemWifiApSmartBleScanner.TAG + ":\t mSemWifiApSmart_D2D_SwitchObserver  MST :" + MST + ",CST: " + CST + ",D2D:" + D2D + ",isNearByEnabled:" + isNearByEnabled);
                if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(2, 500);
                }
            }
        };
        this.mContext = context;
        this.mSemWifiApSmartUtil = tSemWifiApSmartUtil;
        this.mLocalLog = tLocalLog;
        this.mSemWifiApSmartGattServer = WifiInjector.getInstance().getSemWifiApSmartGattServer();
        this.mSemWifiApSmartClient = WifiInjector.getInstance().getSemWifiApSmartClient();
        this.mSemWifiApSmartMHS = WifiInjector.getInstance().getSemWifiApSmartMHS();
        this.mSemWifiApSmartBleScannerReceiver = new SemWifiApSmartBleScannerReceiver();
        if (!FactoryTest.isFactoryBinary()) {
            this.mContext.registerReceiver(this.mSemWifiApSmartBleScannerReceiver, mSemWifiApSmartBleScannerIntentFilter);
            this.mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor("wifi_ap_smart_tethering_settings_with_family"), false, this.mSemWifiApSmartFamilySwitchObserver);
            this.mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor("wifi_client_smart_tethering_settings"), false, this.mSemWifiApSmart_Client_SwitchObserver);
            this.mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor("wifi_ap_smart_tethering_settings"), false, this.mSemWifiApSmart_AutoHotSpot_SwitchObserver);
            this.mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor("wifi_ap_smart_d2d_mhs"), false, this.mSemWifiApSmart_D2D_SwitchObserver);
            return;
        }
        Log.e(TAG, "This devices's binary is a factory binary");
    }

    public void handleBootCompleted() {
        BluetoothAdapter bluetoothAdapter;
        Log.i(TAG, "handleBootCompleted");
        this.mLocalLog.log(TAG + ":\t handleBootCompleted");
        this.mBleWorkThread = new HandlerThread("SemWifiApSmartBleScanner");
        this.mBleWorkThread.start();
        this.mBleWorkHandler = new BleWorkHandler(this.mBleWorkThread.getLooper());
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (this.mBleWorkHandler != null) {
            boolean isAirplaneMode = false;
            if (Settings.Global.getInt(this.mContext.getContentResolver(), "airplane_mode_on", 0) != 0) {
                isAirplaneMode = true;
            }
            if (!isAirplaneMode || (bluetoothAdapter = this.mBluetoothAdapter) == null || bluetoothAdapter.semIsBleEnabled()) {
                BluetoothAdapter bluetoothAdapter2 = this.mBluetoothAdapter;
                if (bluetoothAdapter2 != null && !bluetoothAdapter2.semIsBleEnabled() && this.mSemWifiApSmartUtil.isNearByAutohotspotEnabled()) {
                    this.isAutoHotspotBleSet = true;
                    Log.i(TAG, "semSetStandAloneBleMode called");
                    this.mLocalLog.log(TAG + "semSetStandAloneBleMode called");
                    this.mBluetoothAdapter.semSetStandAloneBleMode(true);
                }
            } else {
                Log.i(TAG, " Airplane is ON and BT is not ON");
                this.mLocalLog.log(TAG + "Airplane is ON and BT is not ON");
            }
            if (this.mSemWifiApSmartUtil.getSamsungAccountCount() <= 0) {
                this.mBleWorkHandler.sendEmptyMessage(2);
            } else if (this.mSemWifiApSmartUtil.getHashbasedonGuid() == -1 && this.mSemWifiApSmartUtil.checkIfActiveNetworkHasInternet()) {
                this.needToEnableAutoHotspot = true;
                Log.i(TAG, "After BOOT Connected to Internet,Samsung account loggedin, but hashbased on Guid is -1");
                this.mLocalLog.log(TAG + ":\tAfter BOOT Connected to Internet,Samsung account loggedin, but hashbased on Guid is -1");
                BleWorkHandler bleWorkHandler = this.mBleWorkHandler;
                if (bleWorkHandler != null) {
                    bleWorkHandler.sendEmptyMessage(3);
                    this.mBleWorkHandler.sendEmptyMessageDelayed(4, 0);
                }
            } else if (this.mSemWifiApSmartUtil.getHashbasedonGuid() != -1) {
                this.mBleWorkHandler.sendEmptyMessage(2);
            }
        }
    }

    class SemWifiApSmartBleScannerReceiver extends BroadcastReceiver {
        SemWifiApSmartBleScannerReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            SemWifiApSmartWhiteList mSemWifiApSmartWhiteList;
            String[] macAddresses;
            String action = intent.getAction();
            if (action.equals("com.samsung.intent.action.SETTINGS_SOFT_RESET") && ("TMO".equals(SemWifiApSmartBleScanner.CONFIGOPBRANDINGFORMOBILEAP) || "NEWCO".equals(SemWifiApSmartBleScanner.CONFIGOPBRANDINGFORMOBILEAP))) {
                Log.i(SemWifiApSmartBleScanner.TAG, "Setting reset done,disabling autohotspot");
                LocalLog localLog = SemWifiApSmartBleScanner.this.mLocalLog;
                localLog.log(SemWifiApSmartBleScanner.TAG + ":\tSetting reset done,disabling autohotspot");
                Settings.Secure.putInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 0);
            } else if (action.equals("android.telephony.action.CARRIER_CONFIG_CHANGED")) {
                int subId = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1);
                TelephonyManager tm = (TelephonyManager) context.getSystemService("phone");
                String str = SemWifiApSmartBleScanner.TAG;
                Log.i(str, "subId:" + subId);
                if (subId != -1) {
                    boolean isSIMchanged = false;
                    long old_value = -1;
                    String old_sim_val = SemWifiApContentProviderHelper.get(SemWifiApSmartBleScanner.this.mContext, "smart_tethering_sim_value");
                    if (!TextUtils.isEmpty(old_sim_val)) {
                        old_value = Long.parseLong(old_sim_val);
                    }
                    long new_value = SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.generateHashKey(tm.getSimSerialNumber());
                    SemWifiApSmartBleScanner semWifiApSmartBleScanner = SemWifiApSmartBleScanner.this;
                    semWifiApSmartBleScanner.mFamilySharingSavedState = Settings.Secure.getInt(semWifiApSmartBleScanner.mContext.getContentResolver(), "autohotspot_family_sharing_saved_state", 0) == 1;
                    String str2 = SemWifiApSmartBleScanner.TAG;
                    Log.i(str2, "Carrier config ,vold_value:" + old_value + ",new_value:" + new_value + ",mFamilySharingSavedState:" + SemWifiApSmartBleScanner.this.mFamilySharingSavedState);
                    LocalLog localLog2 = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog2.log(SemWifiApSmartBleScanner.TAG + ":\tCarrier config ,old_value:" + old_value + ",new_value:" + new_value + ",mFamilySharingSavedState:" + SemWifiApSmartBleScanner.this.mFamilySharingSavedState);
                    if (old_value == -1) {
                        isSIMchanged = false;
                    } else if (old_value != new_value) {
                        isSIMchanged = true;
                    }
                    Context context2 = SemWifiApSmartBleScanner.this.mContext;
                    SemWifiApContentProviderHelper.insert(context2, "smart_tethering_sim_value", "" + new_value);
                    if (SemWifiApSmartBleScanner.this.mFamilySharingSavedState) {
                        if (Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 0) == 1 && !isSIMchanged) {
                            Log.i(SemWifiApSmartBleScanner.TAG, "SIM card inserted,trying to set familySharing");
                            LocalLog localLog3 = SemWifiApSmartBleScanner.this.mLocalLog;
                            localLog3.log(SemWifiApSmartBleScanner.TAG + ":\tSIM card inserted,trying to set familySharing");
                            Settings.Secure.putInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings_with_family", 1);
                            SemWifiApContentProviderHelper.insert(SemWifiApSmartBleScanner.this.mContext, "smart_tethering_family_sharing_service_registered", "1");
                        }
                        Settings.Secure.putInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "autohotspot_family_sharing_saved_state", 0);
                    }
                }
            } else if (action.equals("android.intent.action.SIM_STATE_CHANGED")) {
                Log.i(SemWifiApSmartBleScanner.TAG, "ABSENT:1,READY:5,LOADED:10");
                LocalLog localLog4 = SemWifiApSmartBleScanner.this.mLocalLog;
                localLog4.log(SemWifiApSmartBleScanner.TAG + ":\tABSENT:" + 1 + ",READY:" + 5 + ",LOADED:" + 10);
                TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService("phone");
                int simState = 0;
                if (telephonyManager.getPhoneCount() > 1) {
                    int simState1 = telephonyManager.getSimState(0);
                    int simState2 = telephonyManager.getSimState(1);
                    String str3 = SemWifiApSmartBleScanner.TAG;
                    Log.i(str3, "simState1:" + simState1 + ",simState2:" + simState2 + ",telephonyManager.getPhoneCount():" + telephonyManager.getPhoneCount());
                    LocalLog localLog5 = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog5.log(SemWifiApSmartBleScanner.TAG + ":\tsimState1:" + simState1 + ",simState2:" + simState2 + ",telephonyManager.getPhoneCount()" + telephonyManager.getPhoneCount());
                    if (simState1 == 5 || simState1 == 10 || simState2 == 10 || simState2 == 5) {
                        simState = 5;
                    }
                } else {
                    int simState3 = telephonyManager.getSimState();
                    String str4 = SemWifiApSmartBleScanner.TAG;
                    Log.i(str4, "simState:" + simState3);
                    LocalLog localLog6 = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog6.log(SemWifiApSmartBleScanner.TAG + ":\tsimState:" + simState3);
                    if (simState3 == 5 || simState3 == 10) {
                        simState = 5;
                    } else {
                        simState = 0;
                    }
                }
                if (simState == 0) {
                    int mst = Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 0);
                    int fShare = Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings_with_family", 0);
                    if (mst == 1) {
                        Settings.Secure.putInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "autohotspot_saved_state", 1);
                        Log.i(SemWifiApSmartBleScanner.TAG, "SIM card not ready,disabling AutoHotspot");
                        LocalLog localLog7 = SemWifiApSmartBleScanner.this.mLocalLog;
                        localLog7.log(SemWifiApSmartBleScanner.TAG + ":\tSIM card not ready,disabling AutoHotspot");
                        Settings.Secure.putInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 0);
                    }
                    if (fShare == 1) {
                        String str5 = SemWifiApSmartBleScanner.TAG;
                        Log.i(str5, "SIM card not ready,resetting familysharing agreement page,disabling familysharing,mst:" + mst);
                        LocalLog localLog8 = SemWifiApSmartBleScanner.this.mLocalLog;
                        localLog8.log(SemWifiApSmartBleScanner.TAG + ":\tSIM card not ready,resetting familysharing agreement page,disabling familysharing,mst:" + mst);
                        SemWifiApContentProviderHelper.insert(SemWifiApSmartBleScanner.this.mContext, "smart_tethering_family_sharing_service_registered", "0");
                        Settings.Secure.putInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings_with_family", 0);
                        Settings.Secure.putInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "autohotspot_family_sharing_saved_state", 1);
                    }
                } else if (simState == 5 && Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "autohotspot_saved_state", 0) == 1) {
                    Log.i(SemWifiApSmartBleScanner.TAG, "SIM card inserted,trying to set AutohotspotDB");
                    LocalLog localLog9 = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog9.log(SemWifiApSmartBleScanner.TAG + ":\tSIM card inserted,trying to set AutohotspotDB");
                    Settings.Secure.putInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "autohotspot_saved_state", 0);
                    SemWifiApSmartBleScanner.this.SetAutoHotspotSettingsDB();
                }
            } else if (action.equals("android.net.conn.INET_CONDITION_ACTION")) {
                if (SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.getSamsungAccountCount() > 0 && SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.getHashbasedonGuid() == -1) {
                    Log.w(SemWifiApSmartBleScanner.TAG, "Connected to Internet,Samsung account loggedin, but hashbased on Guid is -1");
                    LocalLog localLog10 = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog10.log(SemWifiApSmartBleScanner.TAG + ":\tConnected to Internet,Samsung account loggedin, but hashbased on Guid is -1");
                    if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(4, 0);
                    }
                }
            } else if (action.equals("android.net.wifi.WIFI_STATE_CHANGED")) {
                if (intent.getIntExtra("wifi_state", 4) == 3) {
                    SemWifiApSmartBleScanner.this.mWifiEnabled = true;
                    if (SemWifiApSmartBleScanner.this.isJDMDevice) {
                        Log.i(SemWifiApSmartBleScanner.TAG, " JDM Wi-Fi Enabled");
                        LocalLog localLog11 = SemWifiApSmartBleScanner.this.mLocalLog;
                        localLog11.log(SemWifiApSmartBleScanner.TAG + ":\t JDM Wi-Fi Enabled");
                        WifiManager wifiManager = (WifiManager) SemWifiApSmartBleScanner.this.mContext.getSystemService("wifi");
                        if (SemWifiApMacInfo.getInstance().readWifiMacInfo() == null && (macAddresses = wifiManager.getFactoryMacAddresses()) != null && macAddresses.length > 0) {
                            SemWifiApMacInfo.getInstance().writeWifiMacInfo(macAddresses[0]);
                        }
                        if (SemWifiApMacInfo.getInstance().readWifiMacInfo() != null && SemWifiApSmartBleScanner.this.mBleWorkHandler != null && !SemWifiApSmartBleScanner.this.isScanningRunning) {
                            Log.i(SemWifiApSmartBleScanner.TAG, " JDM Wi-Fi Enabled, starting scannnig");
                            LocalLog localLog12 = SemWifiApSmartBleScanner.this.mLocalLog;
                            localLog12.log(SemWifiApSmartBleScanner.TAG + ":\t JDM Wi-Fi Enabled, starting scannnig");
                            SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(2, 0);
                            return;
                        }
                        return;
                    }
                    return;
                }
                SemWifiApSmartBleScanner.this.mWifiEnabled = false;
            } else if (action.equals("com.samsung.bluetooth.adapter.action.BLE_STATE_CHANGED")) {
                int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE);
                String str6 = SemWifiApSmartBleScanner.TAG;
                Log.i(str6, "action BLE state:" + action + "::" + state);
                LocalLog localLog13 = SemWifiApSmartBleScanner.this.mLocalLog;
                localLog13.log(SemWifiApSmartBleScanner.TAG + ":\taction BLE state:" + action + "::" + state);
                if (SemWifiApSmartBleScanner.this.isScanningRunning) {
                    SemWifiApSmartBleScanner.this.isStartScanningPending = false;
                }
                if (state == 15) {
                    Log.i(SemWifiApSmartBleScanner.TAG, "STATE_BLE_ON");
                    LocalLog localLog14 = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog14.log(SemWifiApSmartBleScanner.TAG + ":\tSTATE_BLE_ON");
                    SemWifiApSmartBleScanner.this.isStartScanningPending = false;
                    if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(2);
                    }
                }
                if (state == 16) {
                    Log.i(SemWifiApSmartBleScanner.TAG, "BLE is turned off");
                    LocalLog localLog15 = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog15.log(SemWifiApSmartBleScanner.TAG + ":\tBLE is turned off");
                    SemWifiApSmartBleScanner.this.mSemWifiApSmartGattServer.removeGattServer();
                    SemWifiApSmartBleScanner.this.mSemWifiApSmartGattServer.mGattService = null;
                    if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                    }
                }
                if (state == 10) {
                    if (!(Settings.Global.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "airplane_mode_on", 0) != 0) && !BluetoothAdapter.getDefaultAdapter().semIsBleEnabled()) {
                        String str7 = SemWifiApSmartBleScanner.TAG;
                        Log.i(str7, "BLE is OFF, in BT OFF,so check for Enabling BLE: " + SemWifiApSmartBleScanner.this.isScanningRunning);
                        LocalLog localLog16 = SemWifiApSmartBleScanner.this.mLocalLog;
                        localLog16.log(SemWifiApSmartBleScanner.TAG + ":\tBLE is OFF, in BT OFF,socheck for  Enabling BLE: " + SemWifiApSmartBleScanner.this.isScanningRunning);
                        if (SemWifiApSmartBleScanner.this.isScanningRunning && SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                            SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                        }
                        if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                            SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(2, 500);
                        }
                    }
                }
            } else if (SemWifiApSmartBleScanner.this.isFamilyGroupAction(action)) {
                int val = Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 0);
                int isFamily = Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings_with_family", 0);
                String str8 = SemWifiApSmartBleScanner.TAG;
                Log.i(str8, "action : " + action + ", val : " + val);
                if (val == 1 && isFamily == 1 && SemWifiApSmartBleScanner.this.isScanningRunning) {
                    Log.e(SemWifiApSmartBleScanner.TAG, " Stopping backgroundScanning ,because of family group modification");
                    LocalLog localLog17 = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog17.log(SemWifiApSmartBleScanner.TAG + ":\tStopping backgroundScanning ,because of family group modification");
                    if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                    }
                    SemWifiApSmartBleScanner.this.mSemWifiApSmartMHS.sendEmptyMessage(11);
                    if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(2, 2000);
                    }
                }
                try {
                    Thread.sleep(300);
                } catch (Exception e) {
                }
                String receivedFamilyId = intent.getStringExtra("group_id");
                LocalLog localLog18 = SemWifiApSmartBleScanner.this.mLocalLog;
                localLog18.log(SemWifiApSmartBleScanner.TAG + ":\tstarting apk, ST:" + val + " isFamily " + isFamily + " receivedFamilyId:" + receivedFamilyId + " isScanRunning :" + SemWifiApSmartBleScanner.this.isScanningRunning + "action " + action);
                if (receivedFamilyId != null) {
                    String str9 = SemWifiApSmartBleScanner.TAG;
                    Log.i(str9, "receivedFamilyId:" + receivedFamilyId);
                }
                SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.startSmartTetheringApk(true, false, receivedFamilyId);
            } else if (action.equals(SemWifiApSmartBleScanner.ACTION_LOGIN_ACCOUNTS_COMPLETE)) {
                WifiManager wifiManager2 = (WifiManager) SemWifiApSmartBleScanner.this.mContext.getSystemService("wifi");
                LocalLog localLog19 = SemWifiApSmartBleScanner.this.mLocalLog;
                localLog19.log(SemWifiApSmartBleScanner.TAG + ":\t smartBleScanner LOGIN comp ,starting apk");
                Log.i(SemWifiApSmartBleScanner.TAG, "ACTION_LOGIN_ACCOUNTS_COMPLETE, starting apk ");
                SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.startSmartTetheringApk(false, false, null);
                SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.putHashbasedonD2DFamilyid(-1);
                SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.putD2DFamilyID(null);
                SemWifiApContentProviderHelper.insert(SemWifiApSmartBleScanner.this.mContext, "smart_tethering_d2d_Wifimac", (String) null);
                int mSimcheck = SemWifiApSmartBleScanner.this.mSemWifiApSmartMHS.checkPreConditions();
                SemWifiApSmartUtil unused = SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil;
                if (mSimcheck == SemWifiApSmartUtil.SIM_CARD_ERROR) {
                    Settings.Secure.putInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "autohotspot_saved_state", 1);
                    LocalLog localLog20 = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog20.log(SemWifiApSmartBleScanner.TAG + ":\t login completed, but SIM card is not present ");
                    Log.w(SemWifiApSmartBleScanner.TAG, "login completed, but SIM card is not present ");
                }
                SemWifiApSmartBleScanner.this.SetAutoHotspotSettingsDB();
                if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(4, 40000);
                }
            } else if (action.equals(SemWifiApSmartBleScanner.ACTION_LOGOUT_ACCOUNTS_COMPLETE)) {
                LocalLog localLog21 = SemWifiApSmartBleScanner.this.mLocalLog;
                localLog21.log(SemWifiApSmartBleScanner.TAG + ":\tACTION_LOGOUT_ACCOUNTS_COMPLETE");
                Log.i(SemWifiApSmartBleScanner.TAG, "ACTION_LOGOUT_ACCOUNTS_COMPLETE");
                SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.putHashbasedonGuid(-1L);
                SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.putHashbasedonFamilyId(-1L);
                Settings.Secure.putInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 0);
                SemWifiApContentProviderHelper.insert(SemWifiApSmartBleScanner.this.mContext, "smart_tethering_family_sharing_service_registered", "0");
                Settings.Secure.putInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings_with_family", 0);
                SemWifiApContentProviderHelper.insert(SemWifiApSmartBleScanner.this.mContext, "smart_tethering_guid", (String) null);
                SemWifiApContentProviderHelper.insert(SemWifiApSmartBleScanner.this.mContext, "smart_tethering_user_name", (String) null);
                SemWifiApContentProviderHelper.insert(SemWifiApSmartBleScanner.this.mContext, "smart_tethering_user_profile_name", (String) null);
                SemWifiApContentProviderHelper.insert(SemWifiApSmartBleScanner.this.mContext, "smart_tethering_family_user_names", (String) null);
                SemWifiApContentProviderHelper.insert(SemWifiApSmartBleScanner.this.mContext, "smart_tethering_family_guids", (String) null);
                SemWifiApContentProviderHelper.insert(SemWifiApSmartBleScanner.this.mContext, "smart_tethering_familyid", (String) null);
                SemWifiApContentProviderHelper.insert(SemWifiApSmartBleScanner.this.mContext, "smart_tethering_user_icon", (String) null);
                SemWifiApContentProviderHelper.insert(SemWifiApSmartBleScanner.this.mContext, "smart_tethering_AES_keys", (String) null);
                SemWifiApContentProviderHelper.insert(SemWifiApSmartBleScanner.this.mContext, "smart_tethering_family_count", (String) null);
                if (SemWifiApSmartBleScanner.SUPPORTMOBILEAPENHANCED_D2D && (mSemWifiApSmartWhiteList = SemWifiApSmartWhiteList.getInstance()) != null) {
                    mSemWifiApSmartWhiteList.resetWhitelist();
                }
                if (((SemWifiApSmartBleScanner) SemWifiApSmartBleScanner.this).mBleWorkHandler != null) {
                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                }
                SemWifiApSmartBleScanner.this.mSemWifiApSmartMHS.sendEmptyMessage(11);
                if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(2, 3000);
                }
            } else if (action.equals("android.intent.action.AIRPLANE_MODE")) {
                boolean isAirplaneMode = Settings.Global.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "airplane_mode_on", 0) != 0;
                String str10 = SemWifiApSmartBleScanner.TAG;
                Log.i(str10, "isAirplaneMode:" + isAirplaneMode);
                LocalLog localLog22 = SemWifiApSmartBleScanner.this.mLocalLog;
                localLog22.log(SemWifiApSmartBleScanner.TAG + ":\tisAirplaneMode: " + isAirplaneMode);
                if (isAirplaneMode) {
                    if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.removeMessages(2);
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                    }
                    SemWifiApSmartBleScanner.this.mSemWifiApSmartMHS.sendEmptyMessage(11);
                } else if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(2, 2000);
                }
            } else if (action.equals("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED")) {
                boolean emergencyMode = intent.getBooleanExtra("phoneinECMState", false);
                String str11 = SemWifiApSmartBleScanner.TAG;
                Log.i(str11, "SmartBleScanner, emergencyMode:" + emergencyMode);
                LocalLog localLog23 = SemWifiApSmartBleScanner.this.mLocalLog;
                localLog23.log(SemWifiApSmartBleScanner.TAG + ":\temergencyMode:  " + emergencyMode);
                if (emergencyMode) {
                    if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.removeMessages(2);
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                    }
                    SemWifiApSmartBleScanner.this.mSemWifiApSmartMHS.sendEmptyMessage(11);
                } else if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(2, 2000);
                }
            } else if (action.equals("com.samsung.android.server.wifi.softap.smarttethering.familyid")) {
                long familyId = intent.getLongExtra("familyHashID", -1);
                if (SemWifiApSmartBleScanner.SUPPORTMOBILEAPENHANCED_D2D && familyId == -1) {
                    LocalLog localLog24 = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog24.log(SemWifiApSmartBleScanner.TAG + ":\tfamily id is changed, reset white list");
                    Log.i(SemWifiApSmartBleScanner.TAG, "family id is changed, reset white list");
                    SemWifiApSmartWhiteList mSemWifiApSmartWhiteList2 = SemWifiApSmartWhiteList.getInstance();
                    if (mSemWifiApSmartWhiteList2 != null) {
                        mSemWifiApSmartWhiteList2.resetWhitelist();
                    }
                }
                if (((SemWifiApSmartBleScanner) SemWifiApSmartBleScanner.this).isScanningRunning) {
                    LocalLog localLog25 = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog25.log(SemWifiApSmartBleScanner.TAG + ":\tfamily id is changed, restarting scanning");
                    Log.i(SemWifiApSmartBleScanner.TAG, "family id is changed, restarting scanning");
                    if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                    }
                    SemWifiApSmartBleScanner.this.mSemWifiApSmartMHS.sendEmptyMessage(11);
                    if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(2, RttServiceImpl.HAL_RANGING_TIMEOUT_MS);
                    }
                }
            } else if (action.equals("com.samsung.android.server.wifi.softap.smarttethering.d2dfamilyid")) {
                if (SemWifiApSmartBleScanner.this.isScanningRunning) {
                    LocalLog localLog26 = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog26.log(SemWifiApSmartBleScanner.TAG + ":\t d2d family id is changed, restarting scanning");
                    Log.i(SemWifiApSmartBleScanner.TAG, "d2d family id is changed, restarting scanning");
                    WifiInjector.getInstance().getSemWifiApSmartClient().clearLocalResults();
                    if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                    }
                    if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(2, RttServiceImpl.HAL_RANGING_TIMEOUT_MS);
                    }
                }
            } else if (action.equals("android.intent.action.SCREEN_OFF") || action.equals(SemWifiApPowerSaveImpl.ACTION_SCREEN_OFF_BY_PROXIMITY)) {
                SemWifiApSmartBleScanner.this.isLcdOn = false;
                if (SemWifiApSmartBleScanner.this.isScanningRunning && SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                    LocalLog localLog27 = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog27.log(SemWifiApSmartBleScanner.TAG + ":\tScreen is OFF, restarting scanning");
                    Log.w(SemWifiApSmartBleScanner.TAG, "Screen is OFF, restarting scanning");
                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(2, 500);
                }
            } else if (action.equals("android.intent.action.SCREEN_ON") || action.equals(SemWifiApPowerSaveImpl.ACTION_SCREEN_ON_BY_PROXIMITY)) {
                SemWifiApSmartBleScanner.this.isLcdOn = true;
                if (SemWifiApSmartBleScanner.this.isScanningRunning && SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                    LocalLog localLog28 = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog28.log(SemWifiApSmartBleScanner.TAG + ":\tScreen is ON, restarting scanning");
                    Log.i(SemWifiApSmartBleScanner.TAG, "Screen is ON, restarting scanning");
                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(2, 500);
                }
            } else if (!action.equals(SemWifiApSmartBleScanner.ACTION_NEARBY_SCANNING)) {
            } else {
                if (SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.isNearByAutohotspotEnabled()) {
                    if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                        LocalLog localLog29 = SemWifiApSmartBleScanner.this.mLocalLog;
                        localLog29.log(SemWifiApSmartBleScanner.TAG + ":\t NearBy is toggelled, restarting scanning:");
                        Log.w(SemWifiApSmartBleScanner.TAG, "NearBy is toggelled, restarting scanning:");
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(2, 500);
                    }
                } else if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                    LocalLog localLog30 = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog30.log(SemWifiApSmartBleScanner.TAG + ":\t NearBy is toggelled, stop scanning:");
                    Log.w(SemWifiApSmartBleScanner.TAG, "NearBy is toggelled, stop scanning:");
                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessage(3);
                    SemWifiApSmartBleScanner.this.mBluetoothAdapter.semSetStandAloneBleMode(false);
                }
            }
        }
    }

    /* access modifiers changed from: private */
    public class SemWifiApSmartBleScannerCallback extends ScanCallback {
        private SemWifiApSmartBleScannerCallback() {
        }

        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Message msg = new Message();
            msg.what = 1;
            msg.obj = result;
            if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                SemWifiApSmartBleScanner.this.mBleWorkHandler.sendMessage(msg);
            }
        }

        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            SemWifiApSmartBleScanner.this.isScanningRunning = false;
            String str = SemWifiApSmartBleScanner.TAG;
            Log.e(str, "onScanFailed:" + errorCode);
            LocalLog localLog = SemWifiApSmartBleScanner.this.mLocalLog;
            localLog.log(SemWifiApSmartBleScanner.TAG + ":\tonScanFailed:" + errorCode);
        }
    }

    private ScanSettings buildScanSettings() {
        boolean isOn = ((PowerManager) this.mContext.getSystemService("power")).isInteractive();
        this.mSemWifiApscanWindow = 55;
        if (isOn && this.isLcdOn) {
            this.mSemWifiApscanWindow = SemWifiConstants.ROUTER_OUI_TYPE;
        }
        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setScanMode(100);
        builder.semSetCustomScanParams(3120, this.mSemWifiApscanWindow);
        return builder.build();
    }

    private byte[] getScanManufactureData() {
        byte[] data = new byte[24];
        for (int i = 0; i < 24; i++) {
            data[i] = 0;
        }
        Long mguid = Long.valueOf(this.mSemWifiApSmartUtil.getHashbasedonGuid());
        Long familyID = Long.valueOf(this.mSemWifiApSmartUtil.getHashbasedonFamilyId());
        long mD2DFamilyID = this.mSemWifiApSmartUtil.getHashbasedonD2DFamilyid();
        data[0] = 1;
        data[1] = 18;
        data[10] = 4;
        if (mguid.longValue() != -1) {
            byte[] guidBytes = SemWifiApSmartUtil.bytesFromLong(mguid);
            int i2 = 0;
            while (true) {
                SemWifiApSmartUtil semWifiApSmartUtil = this.mSemWifiApSmartUtil;
                if (i2 >= 4) {
                    break;
                }
                data[i2 + 2] = guidBytes[i2];
                i2++;
            }
        }
        if (familyID.longValue() != -1) {
            byte[] familyBytes = SemWifiApSmartUtil.bytesFromLong(familyID);
            int i3 = 0;
            while (true) {
                SemWifiApSmartUtil semWifiApSmartUtil2 = this.mSemWifiApSmartUtil;
                if (i3 >= 4) {
                    break;
                }
                data[i3 + 2 + 4] = familyBytes[i3];
                i3++;
            }
        } else if (mD2DFamilyID != -1) {
            byte[] familyBytes2 = SemWifiApSmartUtil.bytesFromLong(Long.valueOf(mD2DFamilyID));
            int i4 = 0;
            while (true) {
                SemWifiApSmartUtil semWifiApSmartUtil3 = this.mSemWifiApSmartUtil;
                if (i4 >= 4) {
                    break;
                }
                data[i4 + 2 + 4] = familyBytes2[i4];
                i4++;
            }
        }
        return data;
    }

    private byte[] getMHS_D2D_ScanManufactureData() {
        byte[] data = new byte[24];
        for (int i = 0; i < 24; i++) {
            data[i] = 0;
        }
        Long mguid = Long.valueOf(this.mSemWifiApSmartUtil.getHashbasedonGuid());
        Long familyID = Long.valueOf(this.mSemWifiApSmartUtil.getHashbasedonFamilyId());
        long mD2DFamilyID = this.mSemWifiApSmartUtil.getHashbasedonD2DFamilyid();
        data[0] = 1;
        data[1] = 18;
        data[10] = 3;
        if (mguid.longValue() != -1) {
            byte[] guidBytes = SemWifiApSmartUtil.bytesFromLong(mguid);
            int i2 = 0;
            while (true) {
                SemWifiApSmartUtil semWifiApSmartUtil = this.mSemWifiApSmartUtil;
                if (i2 >= 4) {
                    break;
                }
                data[i2 + 2] = guidBytes[i2];
                i2++;
            }
        }
        if (familyID.longValue() != -1) {
            byte[] familyBytes = SemWifiApSmartUtil.bytesFromLong(familyID);
            int i3 = 0;
            while (true) {
                SemWifiApSmartUtil semWifiApSmartUtil2 = this.mSemWifiApSmartUtil;
                if (i3 >= 4) {
                    break;
                }
                data[i3 + 2 + 4] = familyBytes[i3];
                i3++;
            }
        } else if (mD2DFamilyID != -1) {
            byte[] familyBytes2 = SemWifiApSmartUtil.bytesFromLong(Long.valueOf(mD2DFamilyID));
            int i4 = 0;
            while (true) {
                SemWifiApSmartUtil semWifiApSmartUtil3 = this.mSemWifiApSmartUtil;
                if (i4 >= 4) {
                    break;
                }
                data[i4 + 2 + 4] = familyBytes2[i4];
                i4++;
            }
        }
        return data;
    }

    private byte[] getGUIDMask() {
        SemWifiApSmartUtil semWifiApSmartUtil;
        SemWifiApSmartUtil semWifiApSmartUtil2 = this.mSemWifiApSmartUtil;
        byte[] data = new byte[24];
        int i = 0;
        while (true) {
            semWifiApSmartUtil = this.mSemWifiApSmartUtil;
            if (i >= 24) {
                break;
            }
            data[i] = 0;
            i++;
        }
        data[1] = -1;
        Long guid = Long.valueOf(semWifiApSmartUtil.getHashbasedonGuid());
        if (guid.longValue() != -1) {
            SemWifiApSmartUtil.bytesFromLong(guid);
            int i2 = 0;
            while (true) {
                SemWifiApSmartUtil semWifiApSmartUtil3 = this.mSemWifiApSmartUtil;
                if (i2 >= 4) {
                    break;
                }
                data[i2 + 2] = -1;
                i2++;
            }
        }
        return data;
    }

    private byte[] getFamilyIdMask() {
        SemWifiApSmartUtil semWifiApSmartUtil;
        SemWifiApSmartUtil semWifiApSmartUtil2 = this.mSemWifiApSmartUtil;
        byte[] data = new byte[24];
        int i = 0;
        while (true) {
            semWifiApSmartUtil = this.mSemWifiApSmartUtil;
            if (i >= 24) {
                break;
            }
            data[i] = 0;
            i++;
        }
        data[1] = -1;
        long familyID = semWifiApSmartUtil.getHashbasedonFamilyId();
        long mD2dfamilyID = this.mSemWifiApSmartUtil.getHashbasedonD2DFamilyid();
        if (familyID != -1) {
            SemWifiApSmartUtil.bytesFromLong(Long.valueOf(familyID));
            int i2 = 0;
            while (true) {
                SemWifiApSmartUtil semWifiApSmartUtil3 = this.mSemWifiApSmartUtil;
                if (i2 >= 4) {
                    break;
                }
                data[i2 + 2 + 4] = -1;
                i2++;
            }
        } else if (mD2dfamilyID != -1) {
            SemWifiApSmartUtil.bytesFromLong(Long.valueOf(mD2dfamilyID));
            int i3 = 0;
            while (true) {
                SemWifiApSmartUtil semWifiApSmartUtil4 = this.mSemWifiApSmartUtil;
                if (i3 >= 4) {
                    break;
                }
                data[i3 + 2 + 4] = -1;
                i3++;
            }
        }
        return data;
    }

    private byte[] getCientD2DMask() {
        SemWifiApSmartUtil semWifiApSmartUtil = this.mSemWifiApSmartUtil;
        byte[] data = new byte[24];
        int i = 0;
        while (true) {
            SemWifiApSmartUtil semWifiApSmartUtil2 = this.mSemWifiApSmartUtil;
            if (i < 24) {
                data[i] = 0;
                i++;
            } else {
                data[1] = -1;
                data[10] = -1;
                return data;
            }
        }
    }

    private byte[] getMHSD2DMask() {
        SemWifiApSmartUtil semWifiApSmartUtil = this.mSemWifiApSmartUtil;
        byte[] data = new byte[24];
        int i = 0;
        while (true) {
            SemWifiApSmartUtil semWifiApSmartUtil2 = this.mSemWifiApSmartUtil;
            if (i < 24) {
                data[i] = 0;
                i++;
            } else {
                data[1] = -1;
                data[10] = -1;
                return data;
            }
        }
    }

    private List<ScanFilter> buildScanFilters() {
        this.scanFilters.clear();
        Long mguid = Long.valueOf(this.mSemWifiApSmartUtil.getHashbasedonGuid());
        Long familyID = Long.valueOf(this.mSemWifiApSmartUtil.getHashbasedonFamilyId());
        long mD2DFamilyID = this.mSemWifiApSmartUtil.getHashbasedonD2DFamilyid();
        boolean isNearByEnabled = false;
        int D2D = Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_ap_smart_d2d_mhs", 0);
        int CST = Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_client_smart_tethering_settings", 0);
        if (Settings.System.getInt(this.mContext.getContentResolver(), "nearby_scanning_enabled", 0) == 1) {
            isNearByEnabled = true;
        }
        if (mguid.longValue() != -1) {
            if (D2D == 0) {
                ScanFilter.Builder builder = new ScanFilter.Builder();
                SemWifiApSmartUtil semWifiApSmartUtil = this.mSemWifiApSmartUtil;
                this.mGuidScanFilter = builder.setManufacturerData(SemWifiApSmartUtil.MANUFACTURE_ID, getScanManufactureData(), getGUIDMask()).build();
                this.scanFilters.add(this.mGuidScanFilter);
                Log.e(TAG, "mGuidScanFilter" + this.mGuidScanFilter);
                this.mLocalLog.log(TAG + ":\tmGuidScanFilter" + this.mGuidScanFilter);
            } else if (SUPPORTMOBILEAPENHANCED_D2D) {
                ScanFilter.Builder builder2 = new ScanFilter.Builder();
                SemWifiApSmartUtil semWifiApSmartUtil2 = this.mSemWifiApSmartUtil;
                this.mClientD2dFilter = builder2.setManufacturerData(SemWifiApSmartUtil.MANUFACTURE_ID, getScanManufactureData(), getCientD2DMask()).build();
                this.scanFilters.add(this.mClientD2dFilter);
                this.mMhsD2dFilter = null;
                Log.e(TAG, "mClientD2dFilter" + this.mClientD2dFilter);
                this.mLocalLog.log(TAG + ":\tmClientD2dFilter" + this.mClientD2dFilter);
            }
        } else if ((SUPPORTMOBILEAPENHANCED_WIFI_ONLY_LITE || SUPPORTMOBILEAPENHANCED_D2D) && this.mSemWifiApSmartUtil.getSamsungAccountCount() == 0 && isNearByEnabled && !SUPPORTMOBILEAPENHANCED_LITE) {
            this.mGuidScanFilter = null;
            this.mClientD2dFilter = null;
            ScanFilter.Builder builder3 = new ScanFilter.Builder();
            SemWifiApSmartUtil semWifiApSmartUtil3 = this.mSemWifiApSmartUtil;
            this.mMhsD2dFilter = builder3.setManufacturerData(SemWifiApSmartUtil.MANUFACTURE_ID, getMHS_D2D_ScanManufactureData(), getMHSD2DMask()).build();
            this.scanFilters.add(this.mMhsD2dFilter);
            Log.e(TAG, "mMhsD2dFilter:" + this.mMhsD2dFilter);
            this.mLocalLog.log(TAG + ":\tmMhsD2dFilter:" + this.mMhsD2dFilter);
        }
        if (familyID.longValue() != -1 && D2D == 0) {
            ScanFilter.Builder builder4 = new ScanFilter.Builder();
            SemWifiApSmartUtil semWifiApSmartUtil4 = this.mSemWifiApSmartUtil;
            this.mFamilyScanFilter = builder4.setManufacturerData(SemWifiApSmartUtil.MANUFACTURE_ID, getScanManufactureData(), getFamilyIdMask()).build();
            this.scanFilters.add(this.mFamilyScanFilter);
            Log.e(TAG, "mFamilyScanFilter" + this.mFamilyScanFilter);
            this.mLocalLog.log(TAG + ":\tmFamilyScanFilter" + this.mFamilyScanFilter);
        } else if (this.mSemWifiApSmartUtil.getSamsungAccountCount() != 0 || mD2DFamilyID == -1 || CST != 1 || (!SUPPORTMOBILEAPENHANCED_WIFI_ONLY_LITE && !SUPPORTMOBILEAPENHANCED_D2D)) {
            this.mFamilyScanFilter = null;
        } else {
            ScanFilter.Builder builder5 = new ScanFilter.Builder();
            SemWifiApSmartUtil semWifiApSmartUtil5 = this.mSemWifiApSmartUtil;
            this.mFamilyScanFilter = builder5.setManufacturerData(SemWifiApSmartUtil.MANUFACTURE_ID, getScanManufactureData(), getFamilyIdMask()).build();
            this.scanFilters.add(this.mFamilyScanFilter);
            Log.e(TAG, "mD2DFamilyScanFilter" + this.mFamilyScanFilter);
            this.mLocalLog.log(TAG + ":\tmD2DFamilyScanFilter" + this.mFamilyScanFilter);
        }
        return this.scanFilters;
    }

    /* access modifiers changed from: package-private */
    public void startBleScanning() {
        if (FactoryTest.isFactoryBinary()) {
            Log.e(TAG, "This devices's binary is a factory binary");
        } else if (!this.isScanningRunning) {
            int status = checkPreConditions();
            if (status < 0) {
                Log.e(TAG, "failed to start background scanner " + status);
                return;
            }
            long mHashBasedGuid = this.mSemWifiApSmartUtil.getHashbasedonGuid();
            long mHashBasedFamilyID = this.mSemWifiApSmartUtil.getHashbasedonFamilyId();
            boolean isNearByEnabled = false;
            int MST = Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 0);
            int CST = Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_client_smart_tethering_settings", 0);
            int D2D = Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_ap_smart_d2d_mhs", 0);
            long mD2DFamilyID = this.mSemWifiApSmartUtil.getHashbasedonD2DFamilyid();
            if (Settings.System.getInt(this.mContext.getContentResolver(), "nearby_scanning_enabled", 0) == 1) {
                isNearByEnabled = true;
            }
            if (MST == 0 && CST == 0 && D2D == 0 && !isNearByEnabled && mD2DFamilyID == -1) {
                Log.e(TAG, "not to start background scanner as there is no MST/CST/D2D enabled");
                this.mLocalLog.log(TAG + ":\t not to start background scanner as there is no MST/CST/D2D enabled");
                return;
            }
            this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (this.mBluetoothAdapter == null) {
                this.isStartScanningPending = true;
                Log.e(TAG, "mBluetoothAdapter == null, waiting for isStartScanningPending");
                return;
            }
            if (!this.isAutoHotspotBleSet && this.mSemWifiApSmartUtil.isNearByAutohotspotEnabled()) {
                this.isAutoHotspotBleSet = true;
                Log.e(TAG, "mBluetoothAdapter.semIsBleEnabled() " + this.mBluetoothAdapter.semIsBleEnabled());
                this.mBluetoothAdapter.semSetStandAloneBleMode(true);
            }
            this.mBluetoothLeScanner = this.mBluetoothAdapter.getBluetoothLeScanner();
            this.mSemWifiApSmartBleScannerCallback = new SemWifiApSmartBleScannerCallback();
            if (this.mBluetoothLeScanner == null) {
                this.isStartScanningPending = true;
                Log.e(TAG, "mBluetoothLeScanner == null, waiting for isStartScanningPending");
                return;
            }
            List<ScanFilter> tsf = buildScanFilters();
            if (tsf.size() == 0) {
                Log.e(TAG, " scanfilter size zero");
                this.mLocalLog.log(TAG + ":\t scanfilter size zero ");
            } else if (SUPPORTMOBILEAPENHANCED_LITE) {
                tsf.clear();
                if (!((PowerManager) this.mContext.getSystemService("power")).isInteractive()) {
                    return;
                }
                if (CST == 1) {
                    this.isScanningRunning = true;
                    this.mBluetoothLeScanner.startScan(tsf, buildScanSettings(), this.mSemWifiApSmartBleScannerCallback);
                    if (this.DBG) {
                        Log.i(TAG, "Started Lite scanning,mHashBasedGuid:" + mHashBasedGuid + ",mHashBasedFamilyID:" + mHashBasedFamilyID + ",mD2DFamilyID:" + mD2DFamilyID + ",mSemWifiApscanWindow:" + this.mSemWifiApscanWindow);
                        this.mLocalLog.log(TAG + ":\tStarted Lite scanning,mHashBasedGuid:" + mHashBasedGuid + ",mHashBasedFamilyID:" + mHashBasedFamilyID + ",mD2DFamilyID:" + mD2DFamilyID + ",mSemWifiApscanWindow:" + this.mSemWifiApscanWindow);
                        return;
                    }
                    Log.i(TAG, "Started Lite scanning");
                    this.mLocalLog.log(TAG + ":\tStarted Lite scanning");
                }
            } else {
                this.isScanningRunning = true;
                this.mBluetoothLeScanner.startScan(tsf, buildScanSettings(), this.mSemWifiApSmartBleScannerCallback);
                if (this.DBG) {
                    Log.i(TAG, "Started scanning,mHashBasedGuid:" + mHashBasedGuid + ",mHashBasedFamilyID:" + mHashBasedFamilyID + ",mD2DFamilyID:" + mD2DFamilyID + ",mSemWifiApscanWindow:" + this.mSemWifiApscanWindow);
                    this.mLocalLog.log(TAG + ":\tStarted scanning,mHashBasedGuid:" + mHashBasedGuid + ",mHashBasedFamilyID:" + mHashBasedFamilyID + ",mD2DFamilyID:" + mD2DFamilyID + ",mSemWifiApscanWindow:" + this.mSemWifiApscanWindow);
                    return;
                }
                Log.i(TAG, "Started scanning");
                this.mLocalLog.log(TAG + ":\tStarted scanning");
            }
        }
    }

    /* access modifiers changed from: package-private */
    public boolean isBackGroundScannRunning() {
        return this.isScanningRunning;
    }

    /* access modifiers changed from: package-private */
    public void sendMessagewithDelay(int what, int delay) {
        BleWorkHandler bleWorkHandler = this.mBleWorkHandler;
        if (bleWorkHandler != null) {
            bleWorkHandler.sendEmptyMessageDelayed(what, (long) delay);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void stopBleScanning() {
        SemWifiApSmartBleScannerCallback semWifiApSmartBleScannerCallback;
        if (this.isScanningRunning) {
            BluetoothLeScanner bluetoothLeScanner = this.mBluetoothLeScanner;
            if (!(bluetoothLeScanner == null || (semWifiApSmartBleScannerCallback = this.mSemWifiApSmartBleScannerCallback) == null)) {
                bluetoothLeScanner.stopScan(semWifiApSmartBleScannerCallback);
            }
            this.isScanningRunning = false;
            Log.i(TAG, "Stopped scanning");
            LocalLog localLog = this.mLocalLog;
            localLog.log(TAG + ":\tStopped scanning");
        }
    }

    /* access modifiers changed from: package-private */
    public class BleWorkHandler extends Handler {
        public BleWorkHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            SemWifiApSmartD2DMHS mSemWifiApSmartD2DMHS;
            switch (msg.what) {
                case 1:
                    ScanResult result = (ScanResult) msg.obj;
                    ScanRecord sr = result.getScanRecord();
                    byte[] mScanResultData = sr.getManufacturerSpecificData(SemWifiApSmartUtil.MANUFACTURE_ID);
                    if (SemWifiApSmartBleScanner.this.mGuidScanFilter == null || !SemWifiApSmartBleScanner.this.mGuidScanFilter.matches(result)) {
                        if (SemWifiApSmartBleScanner.this.mFamilyScanFilter == null || !SemWifiApSmartBleScanner.this.mFamilyScanFilter.matches(result)) {
                            if (SemWifiApSmartBleScanner.this.mMhsD2dFilter == null || !SemWifiApSmartBleScanner.this.mMhsD2dFilter.matches(result)) {
                                if (SemWifiApSmartBleScanner.this.mClientD2dFilter == null || !SemWifiApSmartBleScanner.this.mClientD2dFilter.matches(result)) {
                                    if (SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.getHashbasedonGuid() != -1 && !SemWifiApSmartBleScanner.SUPPORTMOBILEAPENHANCED_LITE) {
                                        Log.e(SemWifiApSmartBleScanner.TAG, "does not belong it to any filter ");
                                        LocalLog localLog = SemWifiApSmartBleScanner.this.mLocalLog;
                                        localLog.log(SemWifiApSmartBleScanner.TAG + ":\tdoes not belong it to any filter ");
                                        return;
                                    }
                                    return;
                                } else if (mScanResultData[10] == 4 && (mSemWifiApSmartD2DMHS = WifiInjector.getInstance().getSemWifiApSmartD2DMHS()) != null && mScanResultData.length > 25) {
                                    mSemWifiApSmartD2DMHS.sendScanResultFromScanner(result);
                                    return;
                                } else {
                                    return;
                                }
                            } else if (mScanResultData[10] == 3) {
                                SemWifiApSmartD2DClient mSemWifiApSmartD2DClient = WifiInjector.getInstance().getSemWifiApSmartD2DClient();
                                if (mSemWifiApSmartD2DClient != null && mSemWifiApSmartD2DClient.isAdvertising()) {
                                    SemWifiApSmartBleScanner.this.last_client_adv_time = System.currentTimeMillis();
                                }
                                if (mSemWifiApSmartD2DClient != null && !mSemWifiApSmartD2DClient.isAdvertising() && mSemWifiApSmartD2DClient.checkPreConditions() >= 0) {
                                    Log.e(SemWifiApSmartBleScanner.TAG, "starting D2D client adv ");
                                    LocalLog localLog2 = SemWifiApSmartBleScanner.this.mLocalLog;
                                    localLog2.log(SemWifiApSmartBleScanner.TAG + ":\tstarting D2D client adv ");
                                    SemWifiApSmartBleScanner.this.last_client_adv_time = System.currentTimeMillis();
                                    if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null && !SemWifiApSmartBleScanner.this.mBleWorkHandler.hasMessages(6)) {
                                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(6, 10000);
                                    }
                                    if (SemWifiApSmartBleScanner.this.registerAutoHotspotGattServer()) {
                                        mSemWifiApSmartD2DClient.sendEmptyMessage(1);
                                        return;
                                    }
                                    return;
                                }
                                return;
                            } else {
                                return;
                            }
                        } else if (mScanResultData[10] == 1) {
                            if (SemWifiApSmartBleScanner.this.mSemWifiApSmartMHS.isMHSAdvertizing()) {
                                SemWifiApSmartBleScanner.this.last_client_adv_time = System.currentTimeMillis();
                            }
                            if (Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 0) == 1 && !SemWifiApSmartBleScanner.this.mSemWifiApSmartMHS.isMHSAdvertizing()) {
                                Log.e(SemWifiApSmartBleScanner.TAG, "same family starting, MHS adv ");
                                LocalLog localLog3 = SemWifiApSmartBleScanner.this.mLocalLog;
                                localLog3.log(SemWifiApSmartBleScanner.TAG + ":\tsame family starting, MHS adv ");
                                SemWifiApSmartBleScanner.this.last_client_adv_time = System.currentTimeMillis();
                                if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null && !SemWifiApSmartBleScanner.this.mBleWorkHandler.hasMessages(5)) {
                                    SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(5, 10000);
                                }
                                if (SemWifiApSmartBleScanner.this.registerAutoHotspotGattServer()) {
                                    SemWifiApSmartBleScanner.this.mSemWifiApSmartMHS.sendEmptyMessage(10);
                                    return;
                                }
                                return;
                            }
                            return;
                        } else if (mScanResultData[10] != 2) {
                            return;
                        } else {
                            if (mScanResultData.length > 25) {
                                SemWifiApSmartBleScanner.this.mSemWifiApSmartClient.sendScanResultFromScanner(2, result);
                                return;
                            }
                            String str = SemWifiApSmartBleScanner.TAG;
                            Log.e(str, "MHS adv adv result is less than 25:" + sr);
                            LocalLog localLog4 = SemWifiApSmartBleScanner.this.mLocalLog;
                            localLog4.log(SemWifiApSmartBleScanner.TAG + ":\tMHS adv adv result is less than 25:" + sr);
                            return;
                        }
                    } else if (mScanResultData[10] == 1) {
                        if (SemWifiApSmartBleScanner.this.mSemWifiApSmartMHS.isMHSAdvertizing()) {
                            SemWifiApSmartBleScanner.this.last_client_adv_time = System.currentTimeMillis();
                        }
                        if (Settings.Secure.getInt(SemWifiApSmartBleScanner.this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 0) == 1 && !SemWifiApSmartBleScanner.this.mSemWifiApSmartMHS.isMHSAdvertizing()) {
                            Log.e(SemWifiApSmartBleScanner.TAG, "same guid starting, MHS adv ");
                            LocalLog localLog5 = SemWifiApSmartBleScanner.this.mLocalLog;
                            localLog5.log(SemWifiApSmartBleScanner.TAG + ":\tsame guid starting, MHS adv ");
                            SemWifiApSmartBleScanner.this.last_client_adv_time = System.currentTimeMillis();
                            if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null && !SemWifiApSmartBleScanner.this.mBleWorkHandler.hasMessages(5)) {
                                SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(5, 10000);
                            }
                            if (SemWifiApSmartBleScanner.this.registerAutoHotspotGattServer()) {
                                SemWifiApSmartBleScanner.this.mSemWifiApSmartMHS.sendEmptyMessage(10);
                                return;
                            }
                            return;
                        }
                        return;
                    } else if (mScanResultData[10] != 2) {
                        return;
                    } else {
                        if (mScanResultData.length > 25) {
                            SemWifiApSmartBleScanner.this.mSemWifiApSmartClient.sendScanResultFromScanner(1, result);
                            return;
                        }
                        String str2 = SemWifiApSmartBleScanner.TAG;
                        Log.e(str2, "MHS adv adv result is less than 25:" + sr);
                        LocalLog localLog6 = SemWifiApSmartBleScanner.this.mLocalLog;
                        localLog6.log(SemWifiApSmartBleScanner.TAG + ":\tMHS adv adv result is less than 25:" + sr);
                        return;
                    }
                case 2:
                    if (SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.getSamsungAccountCount() <= 0 || SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.getHashbasedonGuid() != -1) {
                        if (SemWifiApSmartBleScanner.this.needToEnableAutoHotspot) {
                            SemWifiApSmartBleScanner.this.needToEnableAutoHotspot = false;
                            SemWifiApSmartBleScanner.this.SetAutoHotspotSettingsDB();
                        }
                        SemWifiApSmartBleScanner.this.startBleScanning();
                        return;
                    }
                    SemWifiApSmartBleScanner.this.needToEnableAutoHotspot = true;
                    if (SemWifiApSmartBleScanner.this.mBleWorkHandler != null) {
                        SemWifiApSmartBleScanner.this.mBleWorkHandler.sendEmptyMessageDelayed(4, 0);
                        return;
                    }
                    return;
                case 3:
                    SemWifiApSmartBleScanner.this.stopBleScanning();
                    return;
                case 4:
                    if (SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.getHashbasedonGuid() != -1) {
                        SemWifiApSmartBleScanner.this.sendMessagewithDelay(2, 0);
                        return;
                    } else if (SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.getSamsungAccountCount() > 0 && !SemWifiApSmartBleScanner.this.mBleWorkHandler.hasMessages(4)) {
                        Log.e(SemWifiApSmartBleScanner.TAG, "could not get guid, try again");
                        LocalLog localLog7 = SemWifiApSmartBleScanner.this.mLocalLog;
                        localLog7.log(SemWifiApSmartBleScanner.TAG + ":\tcould not get guid, trying again");
                        SemWifiApSmartBleScanner.this.mSemWifiApSmartUtil.startSmartTetheringApk(false, false, null);
                        SemWifiApSmartBleScanner.this.sendMessagewithDelay(4, 40000);
                        return;
                    } else {
                        return;
                    }
                case 5:
                    Log.i(SemWifiApSmartBleScanner.TAG, "received CHECK_TO_STOP_MHS_ADV");
                    LocalLog localLog8 = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog8.log(SemWifiApSmartBleScanner.TAG + ":\treceived CHECK_TO_STOP_MHS_ADV");
                    if (System.currentTimeMillis() - SemWifiApSmartBleScanner.this.last_client_adv_time > 60000) {
                        Log.i(SemWifiApSmartBleScanner.TAG, "check and stop MHS Advertizement ,as there is no cient request");
                        LocalLog localLog9 = SemWifiApSmartBleScanner.this.mLocalLog;
                        localLog9.log(SemWifiApSmartBleScanner.TAG + ":\tcheck and stop MHS Advertizement ,as there is no cient request");
                        SemWifiApSmartBleScanner.this.mSemWifiApSmartMHS.sendEmptyMessage(11);
                        SemWifiApSmartBleScanner.this.unregisterAutoHotspotGattServer();
                        return;
                    } else if (SemWifiApSmartBleScanner.this.mSemWifiApSmartMHS.isMHSAdvertizing()) {
                        SemWifiApSmartBleScanner.this.sendMessagewithDelay(5, 20000);
                        return;
                    } else {
                        return;
                    }
                case 6:
                    SemWifiApSmartD2DClient tSemWifiApSmartD2DClient = WifiInjector.getInstance().getSemWifiApSmartD2DClient();
                    Log.i(SemWifiApSmartBleScanner.TAG, "received CHECK_TO_STOP_D2D_CLIENT_ADV");
                    LocalLog localLog10 = SemWifiApSmartBleScanner.this.mLocalLog;
                    localLog10.log(SemWifiApSmartBleScanner.TAG + ":\treceived CHECK_TO_STOP_D2D_CLIENT_ADV");
                    if (System.currentTimeMillis() - SemWifiApSmartBleScanner.this.last_client_adv_time > 30000) {
                        Log.i(SemWifiApSmartBleScanner.TAG, "check and stop D2D client Advertizement ,as there is no MHS D2D request");
                        LocalLog localLog11 = SemWifiApSmartBleScanner.this.mLocalLog;
                        localLog11.log(SemWifiApSmartBleScanner.TAG + ":\tcheck and stop D2D client Advertizement ,as there is no MHS D2D request");
                        if (tSemWifiApSmartD2DClient != null) {
                            tSemWifiApSmartD2DClient.sendEmptyMessage(2);
                        }
                        SemWifiApSmartBleScanner.this.unregisterAutoHotspotGattServer();
                        return;
                    } else if (tSemWifiApSmartD2DClient != null && tSemWifiApSmartD2DClient.isAdvertising()) {
                        SemWifiApSmartBleScanner.this.sendMessagewithDelay(6, 20000);
                        return;
                    } else {
                        return;
                    }
                default:
                    return;
            }
        }
    }

    /* access modifiers changed from: package-private */
    public int checkPreConditions() {
        if (this.isJDMDevice && SemWifiApMacInfo.getInstance().readWifiMacInfo() == null) {
            Log.e(TAG, "JDM MAC address is null");
            LocalLog localLog = this.mLocalLog;
            localLog.log(TAG + ":\t JDM MAC address is null");
            return -4;
        } else if (!this.mSemWifiApSmartUtil.isPackageExists("com.sec.mhs.smarttethering")) {
            Log.e(TAG, "isPackageExists smarttethering == null");
            stopBleScanning();
            return -1;
        } else {
            this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothAdapter bluetoothAdapter = this.mBluetoothAdapter;
            if (bluetoothAdapter == null || !bluetoothAdapter.semIsBleEnabled()) {
                Log.i(TAG, "Preconditions BLE is OFF");
                LocalLog localLog2 = this.mLocalLog;
                localLog2.log(TAG + ":\t  Preconditions BLE is OFF");
                if (!this.mSemWifiApSmartUtil.isNearByAutohotspotEnabled()) {
                    Log.i(TAG, "not isNearByAutohotspotEnabled");
                    LocalLog localLog3 = this.mLocalLog;
                    localLog3.log(TAG + ":\t not isNearByAutohotspotEnabled");
                    return -5;
                }
                SemEmergencyManager em = SemEmergencyManager.getInstance(this.mContext);
                if (em == null || !em.isEmergencyMode()) {
                    boolean isAirplaneMode = Settings.Global.getInt(this.mContext.getContentResolver(), "airplane_mode_on", 0) != 0;
                    if (!isAirplaneMode) {
                        return 0;
                    }
                    String str = TAG;
                    Log.i(str, "getAirplaneMode: " + isAirplaneMode);
                    return -3;
                }
                Log.i(TAG, "Do not setWifiApSmartClient in EmergencyMode");
                return -2;
            }
            Log.i(TAG, "Preconditions BLE is ON");
            return 0;
        }
    }

    /* access modifiers changed from: package-private */
    public synchronized boolean registerAutoHotspotGattServer() {
        if (BluetoothAdapter.getDefaultAdapter().semIsBleEnabled() && this.mSemWifiApSmartGattServer.mGattServer == null) {
            Log.i(TAG, "registerAutoHotspotGattServer");
            LocalLog localLog = this.mLocalLog;
            localLog.log(TAG + ":\tregisterAutoHotspotGattServer");
            boolean ret_val = this.mSemWifiApSmartGattServer.setGattServer();
            try {
                Thread.sleep(300);
            } catch (Exception e) {
            }
            return ret_val;
        } else if (!BluetoothAdapter.getDefaultAdapter().semIsBleEnabled() || this.mSemWifiApSmartGattServer.mGattServer == null) {
            return false;
        } else {
            BluetoothGattServer bluetoothGattServer = this.mSemWifiApSmartGattServer.mGattServer;
            SemWifiApSmartUtil semWifiApSmartUtil = this.mSemWifiApSmartUtil;
            if (bluetoothGattServer.getService(SemWifiApSmartUtil.SERVICE_UUID) != null) {
                return true;
            }
            Log.w(TAG, "AutoHotspot Service is not registered, registering again");
            LocalLog localLog2 = this.mLocalLog;
            localLog2.log(TAG + ":\tAutoHotspot Service is not registered, registering again");
            this.mSemWifiApSmartGattServer.mGattServer = null;
            boolean ret_val2 = this.mSemWifiApSmartGattServer.setGattServer();
            try {
                Thread.sleep(300);
            } catch (Exception e2) {
            }
            return ret_val2;
        }
    }

    /* access modifiers changed from: package-private */
    public synchronized void unregisterAutoHotspotGattServer() {
        Log.i(TAG, "unregisterAutoHotspotGattServer");
        LocalLog localLog = this.mLocalLog;
        localLog.log(TAG + ":\tunregisterAutoHotspotGattServer");
        this.mSemWifiApSmartGattServer.removeGattServer();
        try {
            Thread.sleep(100);
        } catch (Exception e) {
        }
    }

    /* JADX INFO: finally extract failed */
    public String getDumpLogs() {
        long ident = Binder.clearCallingIdentity();
        StringBuffer retValue = new StringBuffer();
        try {
            retValue.append("-- Auto Hotspot BleScanner --\n");
            retValue.append("checkPreConditions:" + checkPreConditions() + "\n");
            retValue.append("mGuidScanFilter:" + this.mGuidScanFilter + "\n");
            retValue.append("mFamilyScanFilter:" + this.mFamilyScanFilter + "\n");
            retValue.append("isBackGroundScanningRunning:" + this.isScanningRunning + "\n");
            Binder.restoreCallingIdentity(ident);
            return retValue.toString();
        } catch (Throwable th) {
            Binder.restoreCallingIdentity(ident);
            throw th;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isFamilyGroupAction(String action) {
        if (action.equals(ACTION_FAMILY_GROUP_ACCEPT_INVITE_PUSH) || action.equals(ACTION_FAMILY_GROUP_DELETE_PUSH) || action.equals(ACTION_FAMILY_GROUP_MEMBER_DELETE_PUSH) || action.equals(ACTION_FAMILY_GROUP_FORCE_MEMBER_DELETE_PUSH) || action.equals(ACTION_FAMILY_GROUP_I_ACCEPT_INVITE_PUSH) || action.equals(ACTION_FAMILY_GROUP_I_CREATE_GROUP_PUSH) || action.equals(ACTION_FAMILY_GROUP_I_DELETE_PUSH) || action.equals(ACTION_FAMILY_GROUP_I_FORCE_MEMBER_DELETE_PUSH) || action.equals(ACTION_FAMILY_GROUP_I_MEMBER_DELETE_PUSH)) {
            return true;
        }
        return false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void SetAutoHotspotSettingsDB() {
        WifiManager wifiManager = (WifiManager) this.mContext.getSystemService("wifi");
        if (!SUPPORTMOBILEAPENHANCED_WIFI_ONLY_LITE && !SUPPORTMOBILEAPENHANCED_LITE && !"ATT".equals(CONFIGOPBRANDINGFORMOBILEAP) && this.mSemWifiApSmartMHS.checkPreConditions() >= 0 && this.mSemWifiApSmartUtil.getSamsungAccountCount() > 0) {
            if ("TMO".equals(CONFIGOPBRANDINGFORMOBILEAP) || "NEWCO".equals(CONFIGOPBRANDINGFORMOBILEAP)) {
                WifiConfiguration mWifiConfig = wifiManager.getWifiApConfiguration();
                if (mWifiConfig == null || !mWifiConfig.allowedKeyManagement.get(4) || mWifiConfig.preSharedKey == null || !mWifiConfig.preSharedKey.equals("\tUSER#DEFINED#PWD#\n")) {
                    Settings.Secure.putInt(this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 1);
                } else {
                    LocalLog localLog = this.mLocalLog;
                    localLog.log(TAG + ":\t USER#DEFINED#PWD# is set, so not turning on AutoHotspot");
                    Log.i(TAG, "USER#DEFINED#PWD# is set, so not turning on AutoHotspot");
                    Settings.Secure.putInt(this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 0);
                }
            } else {
                Settings.Secure.putInt(this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 1);
            }
            if (!wifiManager.isWifiSharingLiteSupported()) {
                Settings.Secure.putInt(this.mContext.getContentResolver(), "wifi_ap_wifi_sharing", 1);
            }
        }
        int mst = Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_ap_smart_tethering_settings", 0);
        String str = TAG;
        Log.d(str, "SetAutoHotspotSettingsDB:" + mst);
        LocalLog localLog2 = this.mLocalLog;
        localLog2.log(TAG + ":\tSetAutoHotspotSettingsDB:" + mst);
    }
}

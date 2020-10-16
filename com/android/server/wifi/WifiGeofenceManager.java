package com.android.server.wifi;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.server.wifi.WifiGeofenceDBHelper;
import com.android.server.wifi.util.TelephonyUtil;
import com.samsung.android.location.SemGeofence;
import com.samsung.android.location.SemLocationManager;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class WifiGeofenceManager {
    private static final String TAG = "WifiGeofenceManager";
    private static final String WIFI_INTENT_ACTION_GEOFENCE_STATE = "com.sec.android.wifi.geofence.state";
    private static final String WIFI_INTENT_ACTION_GEOFENCE_TRIGGERED = "com.sec.android.wifi.GEOFENCE_TRIGGERED";
    private static final Object mGeofenceLock = new Object();
    private boolean DBG = true;
    private ConnectivityManager mConnectivityManager;
    private final Context mContext;
    private final WifiGeofenceDBHelper mGeofenceDBHelper;
    private final HashMap<Integer, WifiGeofenceDBHelper.WifiGeofenceData> mGeofenceDataList;
    private final WifiGeofenceLogManager mGeofenceLogManager;
    private final boolean mGeofenceManagerEnabled;
    private boolean mGeofenceStateByAnotherPackage;
    private boolean mInRange = true;
    private boolean mIntializedGeofence = false;
    public int mLastGeofenceState = -1;
    private final Looper mLooper;
    private NetworkInfo mNetworkInfo;
    private HashMap<String, Integer> mNotExistAPCheckMap = new HashMap<>();
    private SemLocationManager mSLocationManager;
    private int mScanInterval;
    private int mScanMaxInterval;
    private final Handler mStartGeofenceHandler;
    final Runnable mStartGeofenceThread = new Runnable() {
        /* class com.android.server.wifi.WifiGeofenceManager.RunnableC04572 */

        public void run() {
            try {
                WifiGeofenceManager.this.initGeofence();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };
    private TelephonyManager mTm;
    private boolean mTriggerStartLearning = false;
    private final WifiConfigManager mWifiConfigManager;
    private WifiConnectivityManager mWifiConnectivityManager;
    private ArrayList<WifiGeofenceStateListener> mWifiGeofenceListeners = new ArrayList<>();
    private final AtomicInteger mWifiState = new AtomicInteger(1);

    public interface WifiGeofenceStateListener {
        void onGeofenceStateChanged(int i, List<String> list);
    }

    public WifiGeofenceManager(Context context, Looper looper, WifiConfigManager wifiConfigManager) {
        this.mContext = context;
        this.mLooper = looper;
        this.mWifiConfigManager = wifiConfigManager;
        this.mGeofenceDBHelper = new WifiGeofenceDBHelper(this.mContext);
        this.mGeofenceLogManager = new WifiGeofenceLogManager(this.mLooper);
        this.mGeofenceDataList = this.mGeofenceDBHelper.select();
        this.mStartGeofenceHandler = new Handler();
        this.mGeofenceManagerEnabled = true;
        if (this.mGeofenceManagerEnabled) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WIFI_INTENT_ACTION_GEOFENCE_TRIGGERED);
            intentFilter.addAction("com.samsung.android.location.SERVICE_READY");
            intentFilter.addAction("android.intent.action.AIRPLANE_MODE");
            intentFilter.addAction("android.intent.action.SIM_STATE_CHANGED");
            this.mContext.registerReceiver(new BroadcastReceiver() {
                /* class com.android.server.wifi.WifiGeofenceManager.C04561 */

                public void onReceive(Context context, Intent intent) {
                    Log.d(WifiGeofenceManager.TAG, "BroadcastReceiver: " + intent.getAction());
                    if ("android.intent.action.AIRPLANE_MODE".equals(intent.getAction())) {
                        boolean isEnabled = false;
                        if (Settings.Global.getInt(WifiGeofenceManager.this.mContext.getContentResolver(), "airplane_mode_on", 0) == 1) {
                            isEnabled = true;
                        }
                        if (isEnabled) {
                            Log.d(WifiGeofenceManager.TAG, "Airplain mode enabled -> Set max interval to 128s");
                            WifiGeofenceManager.this.sendBroadcastForInOutRange(1);
                        } else if (WifiGeofenceManager.this.isGeofenceExit(true)) {
                            Log.d(WifiGeofenceManager.TAG, "Airplain mode disabled! But exit state -> Set max interval 1024s");
                            WifiGeofenceManager.this.sendBroadcastForInOutRange(2);
                        } else {
                            Log.d(WifiGeofenceManager.TAG, "Airplain mode disabled! Enter state -> Set max interval 128s");
                            WifiGeofenceManager.this.sendBroadcastForInOutRange(1);
                        }
                    } else if ("android.intent.action.SIM_STATE_CHANGED".equals(intent.getAction())) {
                        if (!WifiGeofenceManager.this.isSimCardReady()) {
                            Log.d(WifiGeofenceManager.TAG, "getSimState() is not SIM_STATE_READY! -> Set max interval to 128s");
                            WifiGeofenceManager.this.sendBroadcastForInOutRange(1);
                        } else if (WifiGeofenceManager.this.isGeofenceExit(true)) {
                            Log.d(WifiGeofenceManager.TAG, "getSimState() is SIM_STATE_READY! But exit state -> Set max interval 1024s");
                            WifiGeofenceManager.this.sendBroadcastForInOutRange(2);
                        } else {
                            Log.d(WifiGeofenceManager.TAG, "getSimState() is SIM_STATE_READY! Enter state -> Set max interval 128s");
                            WifiGeofenceManager.this.sendBroadcastForInOutRange(1);
                        }
                    } else if (WifiGeofenceManager.WIFI_INTENT_ACTION_GEOFENCE_TRIGGERED.equals(intent.getAction())) {
                        int id = intent.getIntExtra("id", -1);
                        int direction = intent.getIntExtra("transition", -1);
                        synchronized (WifiGeofenceManager.mGeofenceLock) {
                            WifiGeofenceDBHelper.WifiGeofenceData data = (WifiGeofenceDBHelper.WifiGeofenceData) WifiGeofenceManager.this.mGeofenceDataList.get(new Integer(id));
                            if (data == null) {
                                Log.d(WifiGeofenceManager.TAG, "WifiGeofenceData is null!");
                                return;
                            }
                            data.setIsGeofenceEnter(direction);
                            int isExit = WifiGeofenceManager.this.isGeofenceExit(true) ? 2 : 1;
                            if (WifiGeofenceManager.this.DBG) {
                                Log.d(WifiGeofenceManager.TAG, "id [" + id + "], direction [" + direction + "], Result [" + isExit + "]\n");
                            }
                            WifiGeofenceManager.this.addGeofenceIntentHistoricalDumpLog("                   [ id = " + id + ", direction = " + WifiGeofenceManager.this.syncGetGeofenceStateByName(direction) + "    Result : " + WifiGeofenceManager.this.syncGetGeofenceStateByName(isExit) + " ]");
                            if (direction != 1) {
                                if (direction != 0) {
                                    if (direction == 2) {
                                        if (isExit == 2) {
                                            if (WifiGeofenceManager.this.DBG) {
                                                Log.d(WifiGeofenceManager.TAG, "BroadcastReceiver() - All of AP are OUT. Increase scan max interval");
                                            }
                                            WifiGeofenceManager.this.sendBroadcastForInOutRange(2);
                                        } else {
                                            WifiGeofenceManager.this.sendBroadcastForInOutRange(1);
                                        }
                                    }
                                }
                            }
                            if (WifiGeofenceManager.this.DBG) {
                                Log.d(WifiGeofenceManager.TAG, "BroadcastReceiver() - configKey : " + data.getConfigKey() + " IN. Reduce scan max interval");
                            }
                            WifiGeofenceManager.this.sendBroadcastForInOutRange(direction);
                        }
                    } else if ("com.samsung.android.location.SERVICE_READY".equals(intent.getAction())) {
                        WifiGeofenceManager wifiGeofenceManager = WifiGeofenceManager.this;
                        wifiGeofenceManager.mSLocationManager = (SemLocationManager) wifiGeofenceManager.mContext.getSystemService("sec_location");
                        if (WifiGeofenceManager.this.mSLocationManager != null) {
                            if (WifiGeofenceManager.this.DBG) {
                                Log.d(WifiGeofenceManager.TAG, "mSLocationManager is ready");
                            }
                            WifiGeofenceManager.this.mGeofenceDBHelper.setSLocationManager(WifiGeofenceManager.this.mSLocationManager);
                            WifiGeofenceManager.this.addGeofenceIntentHistoricalDumpLog("syncGeofence ()  ,  mSLocationManager.ACTION_SERVICE_READY !! ");
                            WifiGeofenceManager.this.deleteNotExistAPFromSlocation();
                        }
                        if (WifiGeofenceManager.this.mWifiState.get() == 3 || WifiGeofenceManager.this.mGeofenceStateByAnotherPackage) {
                            WifiGeofenceManager.this.mStartGeofenceHandler.post(WifiGeofenceManager.this.mStartGeofenceThread);
                        }
                    }
                }
            }, intentFilter);
        }
    }

    private void sendBroadcastForGeofenceState(boolean state) {
        if (!this.DBG) {
            Intent intent = new Intent(WIFI_INTENT_ACTION_GEOFENCE_STATE);
            intent.putExtra("state", state);
            Context context = this.mContext;
            if (context != null) {
                try {
                    context.sendBroadcastAsUser(intent, UserHandle.ALL);
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Send broadcast before boot - action:" + intent.getAction());
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private String syncGetGeofenceStateByName(int direction) {
        if (direction == 0) {
            return "UNKNOWN";
        }
        if (direction == 1) {
            return "ENTER  ";
        }
        if (direction != 2) {
            return "[invalid geofence state]";
        }
        return "EXIT   ";
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void addGeofenceIntentHistoricalDumpLog(String log) {
        Message msg = Message.obtain();
        msg.obj = log;
        this.mGeofenceLogManager.sendMessage(msg);
    }

    /* access modifiers changed from: package-private */
    public void setScanInterval(int interval, int maxInterval) {
        if (!this.mGeofenceManagerEnabled) {
            Log.d(TAG, "setScanInterval() - GeofenceManager is disabled");
            return;
        }
        if (this.DBG) {
            Log.e(TAG, "setScanInterval interval : " + interval + ", maxInterval : " + maxInterval);
        }
        this.mScanInterval = interval;
        this.mScanMaxInterval = maxInterval;
    }

    /* access modifiers changed from: package-private */
    public void notifyWifiState(int wifiState) {
        if (!this.mGeofenceManagerEnabled) {
            Log.d(TAG, "setWifiState() - GeofenceManager is disabled");
        } else {
            this.mWifiState.set(wifiState);
        }
    }

    public String getGeofenceInformation() {
        if (!this.mGeofenceManagerEnabled) {
            Log.d(TAG, "getGeofenceInformation() - GeofenceManager is disabled");
            return "GeofenceManager is not supported";
        }
        int isExit = 1;
        if (isGeofenceExit(true)) {
            isExit = 2;
        }
        StringBuilder sbuf = new StringBuilder();
        sbuf.append("Current geofence state : ");
        sbuf.append(syncGetGeofenceStateByName(isExit));
        sbuf.append("\n");
        if (this.mIntializedGeofence) {
            synchronized (mGeofenceLock) {
                if (this.mGeofenceDataList.size() > 0) {
                    sbuf.append("called initGeofence()");
                    sbuf.append("\n");
                } else {
                    sbuf.append("called initGeofence(), saved GeofenceAP is 0");
                    sbuf.append("\n");
                }
            }
        } else {
            sbuf.append("called deinitGeofence()");
            sbuf.append("\n");
        }
        if (this.mTriggerStartLearning) {
            sbuf.append("mTriggerStartLearning");
            sbuf.append("\n");
        } else {
            sbuf.append("mTriggerStopLearning");
            sbuf.append("\n");
        }
        sbuf.append("Scan Interval (now/max):");
        sbuf.append(this.mScanInterval);
        sbuf.append("/");
        sbuf.append(this.mScanMaxInterval);
        sbuf.append("\n");
        sbuf.append("Geofence Details:\n");
        synchronized (mGeofenceLock) {
            for (Integer num : this.mGeofenceDataList.keySet()) {
                int locationId = num.intValue();
                WifiGeofenceDBHelper.WifiGeofenceData data = this.mGeofenceDataList.get(Integer.valueOf(locationId));
                sbuf.append("id:");
                sbuf.append(data.getLocationId());
                sbuf.append(" st:");
                sbuf.append(data.getGeofenceStateToString());
                sbuf.append(" key:");
                sbuf.append(data.getConfigKey());
                sbuf.append(" cellcount:");
                sbuf.append(data.getCellCount(locationId));
                sbuf.append("\n");
            }
        }
        return sbuf.toString();
    }

    private int addGeofence(WifiConfiguration currentConfig) {
        int locationId;
        if (!this.mGeofenceManagerEnabled) {
            Log.d(TAG, "addGeofence() - GeofenceManager is disabled");
            return -1;
        } else if (this.mSLocationManager == null || currentConfig == null) {
            return -1;
        } else {
            long nowMs = System.currentTimeMillis();
            if (this.mGeofenceDataList.size() >= 100) {
                int candidateId = this.mGeofenceDBHelper.getLocationIdFromOldTime();
                Log.d(TAG, "addGeofence() -  candidate id for delete : " + candidateId);
                if (candidateId < 0) {
                    return -1;
                }
                removeGeofence(this.mGeofenceDataList.get(Integer.valueOf(candidateId)).getConfigKey());
            }
            SemGeofence param = new SemGeofence(4, (String) null);
            int locationId2 = this.mSLocationManager.addGeofence(param);
            if (locationId2 < 0) {
                locationId = this.mSLocationManager.addGeofence(param);
            } else {
                locationId = locationId2;
            }
            addGeofenceIntentHistoricalDumpLog("addGeofence()    - [ locationId : " + locationId + " ], [ configKey : " + currentConfig.configKey() + " ]");
            if (this.DBG) {
                Log.d(TAG, "addGeofence() - [ configKey : " + currentConfig.configKey() + " ], [ locationId : " + locationId + " ]");
            }
            synchronized (mGeofenceLock) {
                if (locationId <= 0) {
                    return -1;
                }
                this.mGeofenceDBHelper.insert(locationId, currentConfig.networkId, currentConfig.configKey(), currentConfig.BSSID, nowMs);
                startGeofence(locationId);
                return locationId;
            }
        }
    }

    private void removeGeofence(String configKey) {
        WifiConfiguration conf;
        if (this.DBG) {
            Log.d(TAG, "removeGeofence() - Enter !!");
        }
        if (!this.mGeofenceManagerEnabled) {
            if (this.DBG) {
                Log.d(TAG, "removeGeofence() - GeofenceManager is disabled");
            }
        } else if (this.mSLocationManager != null && configKey != null) {
            int locationId = isFindLocationId(configKey);
            if (locationId > 0) {
                NetworkInfo networkInfo = this.mNetworkInfo;
                if (networkInfo != null && networkInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED && (conf = this.mWifiConfigManager.getConfiguredNetwork(configKey)) != null && conf.configKey().equals(configKey)) {
                    if (this.DBG) {
                        Log.d(TAG, "removeGeofence() - stopLearning(" + locationId + ")");
                    }
                    int mResult = this.mSLocationManager.stopLearning(locationId);
                    if (mResult < 0) {
                        if (this.DBG) {
                            Log.d(TAG, "removeGeofence()- id : " + locationId + ", stopLearning() - ERROR !!, mResult : " + mResult + "");
                        }
                        addGeofenceIntentHistoricalDumpLog("removeGeofence() - [ locationId : " + locationId + " ], ERROR !!, mResult : " + mResult);
                    }
                }
                if (this.mWifiState.get() == 3) {
                    stopGeofence(locationId);
                }
                synchronized (mGeofenceLock) {
                    this.mGeofenceDBHelper.delete(configKey);
                    int mResult2 = this.mSLocationManager.removeGeofence(locationId);
                    if (mResult2 < 0) {
                        if (this.DBG) {
                            Log.d(TAG, "removeGeofence()- id : " + locationId + ", mSLocationManager.removeGeofence() ERROR !!, mResult : " + mResult2 + "");
                        }
                        addGeofenceIntentHistoricalDumpLog("removeGeofence() - [ locationId : " + locationId + " ], mSLocationManager.removeGeofence ERROR !!, mResult : " + mResult2);
                    } else {
                        addGeofenceIntentHistoricalDumpLog("removeGeofence() - [ locationId : " + locationId + " ], [ configKey : " + configKey + " ]");
                        if (this.DBG) {
                            Log.e(TAG, "removeGeofence() - [ configKey : " + configKey + " ], [ locationId : " + locationId + " ]");
                        }
                    }
                }
            }
            if (this.DBG) {
                Log.d(TAG, "removeGeofence() - Exit !!");
            }
        }
    }

    public void initGeofence() {
        Log.i(TAG, "initGeofence");
        if (this.mIntializedGeofence) {
            Log.d(TAG, "init geofence, already initialized");
        } else if (this.mSLocationManager == null) {
            Log.e(TAG, "SLocation service is not ready");
        } else {
            synchronized (mGeofenceLock) {
                for (Integer num : this.mGeofenceDataList.keySet()) {
                    startGeofence(num.intValue());
                }
            }
            deleteNotExistAP();
            this.mIntializedGeofence = true;
        }
    }

    public void deinitGeofence() {
        if (this.DBG) {
            Log.i(TAG, "deinitGeofence");
        }
        if (!this.mGeofenceManagerEnabled) {
            Log.d(TAG, "deinitGeofence() - GeofenceManager is disabled");
        } else if (!this.mIntializedGeofence) {
            Log.d(TAG, "deinit geofence, alread deinitialized");
        } else {
            synchronized (mGeofenceLock) {
                for (Integer num : this.mGeofenceDataList.keySet()) {
                    stopGeofence(num.intValue());
                }
            }
            this.mIntializedGeofence = false;
        }
    }

    private void startGeofence(int locationId) {
        if (this.mSLocationManager == null) {
            Log.e(TAG, "SLocation service is not ready");
            return;
        }
        Intent intent = new Intent(WIFI_INTENT_ACTION_GEOFENCE_TRIGGERED);
        intent.putExtra("id", locationId);
        int mResult = this.mSLocationManager.startGeofenceMonitoring(locationId, PendingIntent.getBroadcast(this.mContext, locationId, intent, 0));
        if (mResult < 0) {
            SemLocationManager semLocationManager = this.mSLocationManager;
            if (mResult == -3) {
                this.mNotExistAPCheckMap.put(this.mGeofenceDataList.get(Integer.valueOf(locationId)).getConfigKey(), new Integer(locationId));
            }
            if (this.DBG) {
                Log.d(TAG, "startGeofence() - id : " + locationId + ", ERROR !!, mResult : " + mResult + "");
            }
            addGeofenceIntentHistoricalDumpLog("startGeofence()  - [ locationId : " + locationId + " ], ERROR !!, mResult : " + mResult);
            return;
        }
        if (this.DBG) {
            Log.d(TAG, "startGeofence() - id : " + locationId);
        }
        addGeofenceIntentHistoricalDumpLog("startGeofence()  - [ locationId : " + locationId + " ]");
    }

    private void stopGeofence(int locationId) {
        if (this.mSLocationManager == null) {
            Log.e(TAG, "SLocation service is not ready");
            return;
        }
        Intent intent = new Intent(WIFI_INTENT_ACTION_GEOFENCE_TRIGGERED);
        intent.putExtra("id", locationId);
        int mResult = this.mSLocationManager.stopGeofenceMonitoring(locationId, PendingIntent.getBroadcast(this.mContext, locationId, intent, 0));
        if (mResult < 0) {
            if (this.DBG) {
                Log.d(TAG, " stopGeofence() - id : " + locationId + ", ERROR !!, mResult : " + mResult + "");
            }
            addGeofenceIntentHistoricalDumpLog("stopGeofence()   - [ locationId : " + locationId + " ], ERROR !!, mResult : " + mResult);
            return;
        }
        if (this.DBG) {
            Log.d(TAG, " stopGeofence() - id : " + locationId);
        }
        addGeofenceIntentHistoricalDumpLog("stopGeofence()   - [ locationId : " + locationId + " ]");
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void sendBroadcastForInOutRange(int state) {
        checkAndSetTelephonyManagerInstance();
        if (this.DBG) {
            Log.d(TAG, "sendBroadcastForInOutRange() - state : " + state + " getNetworkType() : " + this.mTm.getNetworkType());
        }
        TelephonyManager telephonyManager = this.mTm;
        if (telephonyManager == null || telephonyManager.getNetworkType() > 0) {
            if (isGeofenceExit(false)) {
                sendGeofenceStateChangedEvent(2);
            } else {
                sendGeofenceStateChangedEvent(1);
            }
            if (state == 1 || state == 0) {
                this.mInRange = true;
                WifiConnectivityManager wifiConnectivityManager = this.mWifiConnectivityManager;
                if (wifiConnectivityManager != null) {
                    if (wifiConnectivityManager.getPeriodicSingleScanInterval() > 128000) {
                        this.mWifiConnectivityManager.resetPeriodicScanTime();
                        if (this.DBG) {
                            Log.d(TAG, "sendBroadcastForInOutRange - resetPeiodicScanTime() : " + this.mWifiConnectivityManager.getPeriodicSingleScanInterval());
                        }
                    }
                    setScanInterval(this.mWifiConnectivityManager.getPeriodicSingleScanInterval() / 1000, 128);
                }
                sendBroadcastForGeofenceState(true);
            } else if (state == 2) {
                this.mInRange = false;
                WifiConnectivityManager wifiConnectivityManager2 = this.mWifiConnectivityManager;
                if (wifiConnectivityManager2 != null) {
                    setScanInterval(wifiConnectivityManager2.getPeriodicSingleScanInterval() / 1000, 1024);
                }
                sendBroadcastForGeofenceState(false);
            }
        } else {
            sendBroadcastForGeofenceState(true);
            sendGeofenceStateChangedEvent(2);
        }
    }

    public boolean isValidAccessPointToUseGeofence(WifiInfo info, WifiConfiguration config) {
        int[] mIgnorableApMASK = {2861248};
        new int[1][0] = 660652;
        if (this.mConnectivityManager == null) {
            this.mConnectivityManager = (ConnectivityManager) this.mContext.getSystemService("connectivity");
        }
        if (!this.mGeofenceManagerEnabled) {
            Log.d(TAG, "isValidAccessPointToUseGeofence() - GeofenceManager is disabled");
            return false;
        } else if (info == null || config == null) {
            Log.d(TAG, "isValidAccessPointToUseGeofence() - info or config is null!");
            return false;
        } else if (config.semSamsungSpecificFlags.get(1)) {
            Log.d(TAG, "isValidAccessPointToUseGeofence() - Samsung mobile hotspot");
            return false;
        } else if (info.getMeteredHint()) {
            Log.d(TAG, "isValidAccessPointToUseGeofence() - Android Mobile Hotspot");
            return false;
        } else if (config.semIsVendorSpecificSsid) {
            Log.d(TAG, "isValidAccessPointToUseGeofence() - isVendorSpecificSsid is true");
            return false;
        } else if (config.isPasspoint()) {
            Log.d(TAG, "isValidAccessPointToUseGeofence() - PassPoint AP !!");
            return false;
        } else {
            for (int mask : mIgnorableApMASK) {
                if ((info.getIpAddress() & 16777215) == mask) {
                    Log.d(TAG, "isValidAccessPointToUseGeofence() - Masked Android Hotspot");
                    return false;
                }
            }
            return true;
        }
    }

    /* JADX WARNING: Code restructure failed: missing block: B:25:0x004e, code lost:
        if (r0 == false) goto L_0x005c;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:27:0x0052, code lost:
        if (r7.DBG == false) goto L_0x005b;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:28:0x0054, code lost:
        android.util.Log.d(com.android.server.wifi.WifiGeofenceManager.TAG, "isGeofenceExit : return true");
     */
    /* JADX WARNING: Code restructure failed: missing block: B:29:0x005b, code lost:
        return true;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:31:0x005e, code lost:
        if (r7.DBG == false) goto L_0x0067;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:32:0x0060, code lost:
        android.util.Log.d(com.android.server.wifi.WifiGeofenceManager.TAG, "isGeofenceExit : return false, Geofence DB is empty");
     */
    /* JADX WARNING: Code restructure failed: missing block: B:33:0x0067, code lost:
        return false;
     */
    public boolean isGeofenceExit(boolean unknownToEnter) {
        boolean mIsExit = false;
        synchronized (mGeofenceLock) {
            for (Integer locationId : this.mGeofenceDataList.keySet()) {
                int mIsGeofenceEnter = this.mGeofenceDataList.get(locationId).getIsGeofenceEnter();
                if (mIsGeofenceEnter == 1) {
                    if (this.DBG) {
                        Log.d(TAG, "isGeofenceExit : return false");
                    }
                    return false;
                } else if (unknownToEnter && mIsGeofenceEnter == 0) {
                    if (this.DBG) {
                        Log.d(TAG, "isGeofenceExit : return false");
                    }
                    return false;
                } else if (mIsGeofenceEnter == 2) {
                    mIsExit = true;
                }
            }
        }
    }

    public List<String> getGeofenceEnterKeys() {
        String key;
        List<String> configKeys = new ArrayList<>();
        synchronized (mGeofenceLock) {
            for (Integer locationId : this.mGeofenceDataList.keySet()) {
                if (this.mGeofenceDataList.get(locationId).getIsGeofenceEnter() == 1 && (key = this.mGeofenceDataList.get(locationId).getConfigKey()) != null) {
                    configKeys.add(key);
                }
            }
        }
        return configKeys;
    }

    public int getGeofenceCellCount(String configKey) {
        if (this.mSLocationManager == null) {
            return 0;
        }
        synchronized (mGeofenceLock) {
            for (Integer locationId : this.mGeofenceDataList.keySet()) {
                if (this.mGeofenceDataList.get(locationId).getConfigKey().equals(configKey)) {
                    return this.mSLocationManager.getCellCountForEventGeofence(locationId.intValue());
                }
            }
            return 0;
        }
    }

    /* JADX WARNING: Code restructure failed: missing block: B:17:0x0053, code lost:
        if (r6.DBG == false) goto L_?;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:18:0x0055, code lost:
        android.util.Log.d(com.android.server.wifi.WifiGeofenceManager.TAG, "isFindLocationId() - failed to find!");
     */
    /* JADX WARNING: Code restructure failed: missing block: B:24:?, code lost:
        return -1;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:25:?, code lost:
        return -1;
     */
    private int isFindLocationId(String configKey) {
        synchronized (mGeofenceLock) {
            for (Integer locationId : this.mGeofenceDataList.keySet()) {
                if (configKey.equals(this.mGeofenceDataList.get(locationId).getConfigKey())) {
                    if (this.DBG) {
                        Log.d(TAG, "isFindLocationId() - Location Id : " + locationId.intValue());
                    }
                    return locationId.intValue();
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isSimCardReady() {
        if ((!this.DBG || !SystemProperties.get("SimCheck.disable").equals("1")) && getSimState() != 5) {
            return false;
        }
        return true;
    }

    private int getSimState() {
        checkAndSetTelephonyManagerInstance();
        TelephonyManager telephonyManager = this.mTm;
        if (telephonyManager == null) {
            return 0;
        }
        int isMultiSim = TelephonyUtil.semGetMultiSimState(telephonyManager);
        if (this.DBG) {
            Log.d(TAG, "isMultiSim : " + isMultiSim);
        }
        if (isMultiSim < 4) {
            return 5;
        }
        return 0;
    }

    private void checkAndSetTelephonyManagerInstance() {
        if (this.mTm == null) {
            this.mTm = (TelephonyManager) this.mContext.getSystemService("phone");
        }
    }

    public void startGeofenceThread(List<WifiConfiguration> configs) {
        if (!this.mGeofenceManagerEnabled) {
            Log.d(TAG, "startGeofenceThread() - GeofenceManager is disabled");
        } else if (this.mSLocationManager != null && configs != null) {
            if (this.DBG) {
                Log.e(TAG, "ConnectModeState - enter !! - mGeofenceManagerEnabled !!");
            }
            synchronized (mGeofenceLock) {
                Iterator<Integer> keyIterator = this.mGeofenceDataList.keySet().iterator();
                while (keyIterator.hasNext()) {
                    Integer locationId = keyIterator.next();
                    String configKey = this.mGeofenceDataList.get(locationId).getConfigKey();
                    boolean matched = false;
                    Iterator<WifiConfiguration> it = configs.iterator();
                    while (true) {
                        if (!it.hasNext()) {
                            break;
                        } else if (configKey.equals(it.next().configKey())) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) {
                        if (this.DBG) {
                            Log.d(TAG, "delete config(Sync) - locationId : " + locationId + "  configKey() : " + configKey);
                        }
                        addGeofenceIntentHistoricalDumpLog("Delete config(sync)- [ locationId : " + locationId + " ], [ configKey : " + configKey + " ]");
                        this.mGeofenceDBHelper.delete(locationId.intValue());
                        keyIterator.remove();
                        int mResult = this.mSLocationManager.removeGeofence(locationId.intValue());
                        if (mResult < 0) {
                            if (this.DBG) {
                                Log.d(TAG, "delete config(Sync) - locationId : " + locationId + ", removeGeofence() ERROR !!, mResult : " + mResult);
                            }
                            addGeofenceIntentHistoricalDumpLog("Delete config(sync)- [ locationId : " + locationId + " ], removeGeofence ERROR !!, mResult : " + mResult);
                        } else {
                            if (this.DBG) {
                                Log.d(TAG, "delete config(Sync) - locationId : " + locationId + ", configKey : " + configKey + ", removeGeofence() Success !!");
                            }
                            addGeofenceIntentHistoricalDumpLog("Delete config(sync)- [ locationId : " + locationId + " ], [ configKey : " + configKey + " ]");
                        }
                    }
                }
            }
            this.mStartGeofenceHandler.post(this.mStartGeofenceThread);
        }
    }

    public void removeNetwork(WifiConfiguration config) {
        if (!this.mGeofenceManagerEnabled) {
            Log.d(TAG, "removeNetwork() - GeofenceManager is disabled");
        } else if (config == null) {
            Log.d(TAG, "ConnectModeState() - REMOVE_NETWORK - config is null");
        } else {
            removeGeofence(config.configKey());
            if (this.DBG) {
                Log.d(TAG, "ConnectModeState() - REMOVE_NETWORK - configKey : " + config.configKey() + " !!");
            }
            addGeofenceIntentHistoricalDumpLog("RemoveNetwork    - [ configKey : " + config.configKey() + " ]");
            if (isGeofenceExit(true)) {
                sendBroadcastForInOutRange(2);
            } else {
                sendBroadcastForInOutRange(1);
            }
        }
    }

    public void forgetNetwork(WifiConfiguration config) {
        if (!this.mGeofenceManagerEnabled) {
            Log.d(TAG, "forgetNetwork() - GeofenceManager is disabled");
        } else if (config == null) {
            Log.d(TAG, "ConnectModeState() - FORGET_NETWORK - config is null");
        } else {
            removeGeofence(config.configKey());
            if (this.DBG) {
                Log.d(TAG, "ConnectModeState() - FORGET_NETWORK - configKey : " + config.configKey() + " !!");
            }
            addGeofenceIntentHistoricalDumpLog("ForgetNetwork    - [ configKey : " + config.configKey() + " ]");
            if (isGeofenceExit(true)) {
                sendBroadcastForInOutRange(2);
            } else {
                sendBroadcastForInOutRange(1);
            }
        }
    }

    public void triggerStartLearning(WifiConfiguration currentConfig) {
        TelephonyManager telephonyManager;
        if (!this.mGeofenceManagerEnabled) {
            Log.d(TAG, "triggerStartLearning() - GeofenceManager is disabled");
        } else if (currentConfig == null) {
            Log.d(TAG, "triggerStartLearning - currentConfig is null");
        } else {
            checkAndSetTelephonyManagerInstance();
            if (!(this.mSLocationManager == null || (telephonyManager = this.mTm) == null || telephonyManager.getNetworkType() <= 0)) {
                int locationId = isFindLocationId(currentConfig.configKey());
                long nowMs = System.currentTimeMillis();
                int mResult = -100;
                if (locationId < 0) {
                    locationId = addGeofence(currentConfig);
                    if (locationId > 0) {
                        mResult = this.mSLocationManager.startLearning(locationId);
                        if (this.DBG) {
                            Log.d(TAG, "triggerStartLearning - new locationId, startLearning id : " + locationId);
                        }
                    } else if (this.DBG) {
                        Log.d(TAG, "triggerStartLearning - locationId < 0 !!");
                    }
                } else {
                    this.mGeofenceDBHelper.update(locationId, nowMs);
                    startGeofence(locationId);
                    mResult = this.mSLocationManager.startLearning(locationId);
                    if (this.DBG) {
                        Log.d(TAG, "triggerStartLearning - startLearning id : " + locationId);
                    }
                }
                if (locationId < 0) {
                    if (this.DBG) {
                        Log.d(TAG, "triggerStartLearning - locationId < 0 !!");
                    }
                } else if (mResult < 0) {
                    SemLocationManager semLocationManager = this.mSLocationManager;
                    if (mResult == -3) {
                        deleteNotExistAP();
                    }
                    if (this.DBG) {
                        Log.d(TAG, "triggerStartLearning - id : " + locationId + ", enter() - ERROR !!, mResult : " + mResult + "");
                    }
                    addGeofenceIntentHistoricalDumpLog("trigStartLearning- [ locationId : " + locationId + " ], enter() - ERROR !!, mResult : " + mResult);
                }
                if (this.DBG) {
                    Log.d(TAG, "triggerStartLearning - id : " + locationId + ",  startLearning Success !!");
                }
                addGeofenceIntentHistoricalDumpLog("trigStartLearning- [ locationId : " + locationId + " ], startLearning Success !!");
            }
            this.mTriggerStartLearning = true;
        }
    }

    public void triggerStopLearning(WifiConfiguration currentConfig) {
        if (!this.mGeofenceManagerEnabled) {
            Log.d(TAG, "triggerStopLearning() - GeofenceManager is disabled");
        } else if (currentConfig == null) {
            Log.d(TAG, "triggerStopLearning - currentConfig is null");
        } else {
            if (this.mSLocationManager != null) {
                int locationId = isFindLocationId(currentConfig.configKey());
                if (this.DBG) {
                    Log.d(TAG, "triggerStopLearning id : " + locationId);
                }
                if (locationId > 0) {
                    int mResult = this.mSLocationManager.stopLearning(locationId);
                    if (mResult < 0) {
                        if (this.DBG) {
                            Log.d(TAG, "triggerStopLearning - id : " + locationId + ", exit() - ERROR !!, mResult : " + mResult + "");
                        }
                        addGeofenceIntentHistoricalDumpLog("triggerStopLearning - [ locationId : " + locationId + " ], exit() - ERROR !!, mResult : " + mResult);
                    } else {
                        addGeofenceIntentHistoricalDumpLog("triggerStopLearning - [ locationId : " + locationId + " ], Success !! ");
                    }
                }
            }
            this.mTriggerStartLearning = false;
        }
    }

    /* access modifiers changed from: package-private */
    public boolean isInRange() {
        return this.mInRange;
    }

    /* access modifiers changed from: package-private */
    public boolean isSupported() {
        return this.mGeofenceManagerEnabled;
    }

    /* access modifiers changed from: package-private */
    public void deleteNotExistAP() {
        if (this.mNotExistAPCheckMap.size() != 0) {
            synchronized (mGeofenceLock) {
                for (Map.Entry<String, Integer> element : this.mNotExistAPCheckMap.entrySet()) {
                    this.mGeofenceDBHelper.delete(element.getKey());
                    this.mSLocationManager.removeGeofence(element.getValue().intValue());
                }
                this.mNotExistAPCheckMap.clear();
            }
        }
    }

    /* access modifiers changed from: package-private */
    public void deleteNotExistAPFromSlocation() {
        new ArrayList();
        List<Integer> mSlocationDbList = this.mSLocationManager.getGeofenceIdList((String) null);
        HashMap<Integer, String> toBeEraseFromGeofenceDB = new HashMap<>();
        synchronized (mGeofenceLock) {
            for (Integer num : this.mGeofenceDataList.keySet()) {
                int locationId = num.intValue();
                if (!mSlocationDbList.contains(new Integer(locationId))) {
                    toBeEraseFromGeofenceDB.put(new Integer(locationId), this.mGeofenceDataList.get(Integer.valueOf(locationId)).getConfigKey());
                }
            }
        }
        if (!toBeEraseFromGeofenceDB.isEmpty()) {
            for (Map.Entry<Integer, String> entry : toBeEraseFromGeofenceDB.entrySet()) {
                synchronized (mGeofenceLock) {
                    addGeofenceIntentHistoricalDumpLog("Delete " + entry.getValue() + " AP, since the SLocation db does not have this profile");
                    this.mGeofenceDBHelper.delete(entry.getValue());
                }
            }
        }
        toBeEraseFromGeofenceDB.clear();
    }

    public void enableVerboseLogging(int verbose) {
        if (verbose > 0) {
            this.DBG = true;
        } else {
            this.DBG = false;
        }
    }

    /* access modifiers changed from: package-private */
    public void register(WifiConnectivityManager wifiConnectivityManager) {
        this.mWifiConnectivityManager = wifiConnectivityManager;
    }

    /* access modifiers changed from: package-private */
    public void register(NetworkInfo networkInfo) {
        this.mNetworkInfo = networkInfo;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        this.mGeofenceLogManager.dump(fd, pw, args);
        this.mGeofenceDBHelper.dump(fd, pw, args);
    }

    public void setWifiGeofenceListener(WifiGeofenceStateListener listener) {
        this.mWifiGeofenceListeners.add(listener);
    }

    public void sendGeofenceStateChangedEvent(int state) {
        this.mLastGeofenceState = state;
        Iterator<WifiGeofenceStateListener> it = this.mWifiGeofenceListeners.iterator();
        while (it.hasNext()) {
            WifiGeofenceStateListener listener = it.next();
            if (listener != null) {
                listener.onGeofenceStateChanged(state, getGeofenceEnterKeys());
            }
        }
    }

    public int getCurrentGeofenceState() {
        return this.mLastGeofenceState;
    }

    public void setGeofenceStateByAnotherPackage(boolean enable) {
        this.mGeofenceStateByAnotherPackage = enable;
    }

    public boolean setLatitudeAndLongitude(WifiConfiguration config, double latitude, double longitude) {
        if (!this.mGeofenceManagerEnabled) {
            Log.d(TAG, "setLatitudeAndLongitude() - GeofenceManager is disabled");
            return false;
        } else if (config == null) {
            Log.d(TAG, "setLatitudeAndLongitude - config is null");
            return false;
        } else {
            int locationId = isFindLocationId(config.configKey());
            if (locationId < 0) {
                if (this.DBG) {
                    Log.d(TAG, "setLatitudeAndLongitude - locationId < 0 !");
                }
                return false;
            }
            this.mGeofenceDBHelper.update(locationId, latitude, longitude);
            return true;
        }
    }

    public String getLatitudeAndLongitude(WifiConfiguration config) {
        double latitude;
        double longitude;
        if (!this.mGeofenceManagerEnabled) {
            Log.d(TAG, "setLatitudeAndLongitude() - GeofenceManager is disabled");
            return null;
        } else if (config == null) {
            Log.d(TAG, "setLatitudeAndLongitude - config is null");
            return null;
        } else {
            int locationId = isFindLocationId(config.configKey());
            if (locationId < 0) {
                if (this.DBG) {
                    Log.d(TAG, "getLatitudeAndLongitude - locationId < 0 !!");
                }
                return null;
            }
            synchronized (mGeofenceLock) {
                latitude = this.mGeofenceDataList.get(Integer.valueOf(locationId)).getLatitude();
                longitude = this.mGeofenceDataList.get(Integer.valueOf(locationId)).getLongitude();
            }
            if (this.DBG) {
                Log.e(TAG, "getLatitudeAndLongitude - " + latitude + ":" + longitude);
            }
            return latitude + ":" + longitude;
        }
    }
}

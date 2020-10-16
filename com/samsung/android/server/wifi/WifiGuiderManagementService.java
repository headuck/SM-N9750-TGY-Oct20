package com.samsung.android.server.wifi;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import com.android.server.wifi.WifiInjector;
import com.samsung.android.emergencymode.SemEmergencyManager;
import com.samsung.android.net.wifi.wifiguider.IWifiGuiderService;
import com.samsung.android.server.wifi.dqa.SemWifiIssueDetector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class WifiGuiderManagementService {
    private static final String ACTION_DIAGNOSIS_RESULT_AVAILABLE = "com.samsung.android.net.wifi.wifiguider.DIAGNOSIS_RESULT_AVAILABLE";
    private static final String ACTION_ISSUE_DETECTOR = "issuedetector_report";
    private static final String ARGS_REPORT_IDS = "report_ids";
    private static final boolean DBG = Debug.semIsProductDev();
    private static final String SETTINGS_OBSERVER = "settings_observer";
    private static final String TAG = "WifiGuiderMgmtService";
    private static final String VERSION = "1.8";
    private static final String mDefaultPackage = "com.samsung.android.net.wifi.wifiguider";
    private static final String mDefaultServiceClass = "WifiGuiderService";
    private List<String> mCachedDiagResults = new ArrayList();
    private Object mConditionLock = new Object();
    private HashMap<String, Bundle> mConditions = new HashMap<>();
    private ServiceConnection mConnection = new ServiceConnection() {
        /* class com.samsung.android.server.wifi.WifiGuiderManagementService.ServiceConnectionC07394 */

        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(WifiGuiderManagementService.TAG, "WifiGuider service connected");
            WifiGuiderManagementService.this.mService = IWifiGuiderService.Stub.asInterface(service);
            if (WifiGuiderManagementService.this.mDiagnosisName != null) {
                WifiGuiderManagementService.this.updateCachedDiagResult();
                WifiGuiderManagementService wifiGuiderManagementService = WifiGuiderManagementService.this;
                wifiGuiderManagementService.startDiagnosis(wifiGuiderManagementService.mDiagnosisName);
                WifiGuiderManagementService.this.mDiagnosisName = null;
                return;
            }
            WifiGuiderManagementService.this.startDiagnosis("all");
        }

        public void onServiceDisconnected(ComponentName name) {
            Log.i(WifiGuiderManagementService.TAG, "WifiGuider service disconnected");
            WifiGuiderManagementService.this.mService = null;
        }
    };
    private final Context mContext;
    private String mCurrentServiceClass;
    private String mCurrentServicePackage;
    private String mDiagnosisName = null;
    private boolean mIgnoreStickBroadcastAction;
    private boolean mIsRegistered;
    private final SemWifiIssueDetector mIssueDetector;
    private List<ContentObserver> mObservers = new ArrayList();
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        /* class com.samsung.android.server.wifi.WifiGuiderManagementService.C07383 */

        public void onReceive(Context context, Intent intent) {
            String receivedAction = intent.getAction();
            if (WifiGuiderManagementService.this.mIgnoreStickBroadcastAction) {
                Log.d(WifiGuiderManagementService.TAG, "ignore sticky broadcast action " + receivedAction);
            } else if (WifiGuiderManagementService.this.isServiceRegistered()) {
                synchronized (WifiGuiderManagementService.this.mConditionLock) {
                    for (String action : WifiGuiderManagementService.this.mConditions.keySet()) {
                        if (action.equals(receivedAction)) {
                            Log.d(WifiGuiderManagementService.TAG, "received action " + action);
                            Bundle condition = (Bundle) WifiGuiderManagementService.this.mConditions.get(action);
                            if (!(condition == null || condition.size() == 0)) {
                                if (WifiGuiderManagementService.this.checkConditionInt("wifi_state", 4, condition, intent)) {
                                    if (WifiGuiderManagementService.this.checkConditionInt("previous_wifi_state", 4, condition, intent)) {
                                        if (WifiGuiderManagementService.this.checkConditionInt("wifi_state", 14, condition, intent)) {
                                            if (WifiGuiderManagementService.this.checkConditionInt("previous_wifi_state", 14, condition, intent)) {
                                                if (WifiGuiderManagementService.this.checkConditionInt("newRssi", -200, condition, intent)) {
                                                    if (WifiGuiderManagementService.this.checkConditionIntRange("newRssi", "Low", "High", condition, intent)) {
                                                        if (WifiGuiderManagementService.this.checkConditionInt("changeReason", -1, condition, intent)) {
                                                            if (!WifiGuiderManagementService.this.checkConditionString("bssid", condition, intent)) {
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            WifiGuiderManagementService.this.startWifiGuiderService(action);
                        }
                    }
                }
            }
        }
    };
    private Object mReportIdLock = new Object();
    private List<Integer> mReportIds = new ArrayList();
    private IWifiGuiderService mService;
    private ServiceHandler mServiceHandler;
    private HandlerThread mWgmsThread;

    public WifiGuiderManagementService(Context context, WifiInjector injector) {
        this.mContext = context;
        this.mConditions.clear();
        this.mWgmsThread = new HandlerThread(TAG);
        this.mWgmsThread.start();
        this.mServiceHandler = new ServiceHandler(this.mWgmsThread.getLooper());
        this.mIssueDetector = injector.getIssueDetector();
        SemWifiIssueDetector semWifiIssueDetector = this.mIssueDetector;
        if (semWifiIssueDetector != null) {
            semWifiIssueDetector.setExternalDiagnosticListener(new SemWifiIssueDetector.IExternalDiagnosticListener() {
                /* class com.samsung.android.server.wifi.WifiGuiderManagementService.C07361 */

                @Override // com.samsung.android.server.wifi.dqa.SemWifiIssueDetector.IExternalDiagnosticListener
                public void onReportAdded(int reportId) {
                    synchronized (WifiGuiderManagementService.this.mReportIdLock) {
                        if (WifiGuiderManagementService.this.mReportIds.contains(Integer.valueOf(reportId))) {
                            WifiGuiderManagementService.this.mServiceHandler.bindService();
                        }
                    }
                }
            });
        }
        registerDiagnosisCompleteIntent();
    }

    public String getVersion() {
        return VERSION;
    }

    public int registerService(String packageName, String className) {
        Log.i(TAG, "registerService " + className);
        this.mCurrentServicePackage = packageName;
        this.mCurrentServiceClass = className;
        if (this.mIsRegistered) {
            this.mContext.unregisterReceiver(this.mReceiver);
            unregisterObservers();
            this.mIsRegistered = false;
        }
        synchronized (this.mConditionLock) {
            this.mConditions.clear();
        }
        synchronized (this.mReportIdLock) {
            this.mReportIds.clear();
        }
        bindAndStartDiagnosis("registered");
        return 0;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isServiceRegistered() {
        if (this.mCurrentServicePackage != null && this.mCurrentServiceClass != null) {
            return true;
        }
        Log.e(TAG, "Wi-Fi guider service is not registered");
        return false;
    }

    public int registerAction(Bundle condition) {
        if (!isServiceRegistered()) {
            return -1;
        }
        if (condition == null) {
            Log.e(TAG, "condition is null");
            return -2;
        }
        String action = condition.getString("action");
        if (action == null) {
            Log.e(TAG, "There are no action parameter");
            return -4;
        }
        if (ACTION_ISSUE_DETECTOR.equals(action)) {
            synchronized (this.mReportIdLock) {
                this.mReportIds.clear();
                int[] reportIds = condition.getIntArray(ARGS_REPORT_IDS);
                if (reportIds != null) {
                    for (int reportId : reportIds) {
                        this.mReportIds.add(Integer.valueOf(reportId));
                    }
                }
            }
        } else if (SETTINGS_OBSERVER.equals(action)) {
            String dbName = condition.getString("global_db_name");
            if (dbName != null && dbName.length() > 0) {
                registerObserver(true, dbName);
            }
            String dbName2 = condition.getString("secure_db_name");
            if (dbName2 != null && dbName2.length() > 0) {
                registerObserver(false, dbName2);
            }
        } else {
            boolean needToRegister = true;
            synchronized (this.mConditionLock) {
                if (this.mConditions.containsKey(action) && this.mIsRegistered) {
                    needToRegister = false;
                }
                this.mConditions.put(action, condition);
            }
            if (needToRegister) {
                this.mIgnoreStickBroadcastAction = true;
                if (this.mIsRegistered) {
                    this.mContext.unregisterReceiver(this.mReceiver);
                    this.mIsRegistered = false;
                }
                IntentFilter intentFilter = new IntentFilter();
                for (String newAction : this.mConditions.keySet()) {
                    Log.d(TAG, "register action " + newAction);
                    intentFilter.addAction(newAction);
                }
                this.mContext.registerReceiver(this.mReceiver, intentFilter);
                this.mIsRegistered = true;
                this.mIgnoreStickBroadcastAction = false;
            }
        }
        Log.i(TAG, "registerAction success " + action);
        return 0;
    }

    private void registerObserver(boolean isGlobalDb, final String settingsDbName) {
        Uri uri;
        ContentObserver contentObserver = new ContentObserver(null) {
            /* class com.samsung.android.server.wifi.WifiGuiderManagementService.C07372 */

            public void onChange(boolean selfChange) {
                WifiGuiderManagementService wifiGuiderManagementService = WifiGuiderManagementService.this;
                wifiGuiderManagementService.startWifiGuiderService("observer:" + settingsDbName);
            }
        };
        ContentResolver contentResolver = this.mContext.getContentResolver();
        if (isGlobalDb) {
            uri = Settings.Global.getUriFor(settingsDbName);
        } else {
            uri = Settings.Secure.getUriFor(settingsDbName);
        }
        contentResolver.registerContentObserver(uri, false, contentObserver);
        Log.d(TAG, "register observer " + settingsDbName);
        this.mObservers.add(contentObserver);
    }

    private void unregisterObservers() {
        for (ContentObserver observer : this.mObservers) {
            this.mContext.getContentResolver().unregisterContentObserver(observer);
        }
        Log.d(TAG, "unregister observers");
        this.mObservers.clear();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean checkConditionIntRange(String key, String keyLow, String keyHigh, Bundle condition, Intent intent) {
        if (!condition.containsKey(key + keyLow)) {
            return true;
        }
        if (!condition.containsKey(key + keyHigh) || !intent.hasExtra(key)) {
            return true;
        }
        int lowValue = condition.getInt(key + keyLow, 0);
        int highValue = condition.getInt(key + keyHigh, 0);
        int value = intent.getIntExtra(key, 0);
        if (value < lowValue || value > highValue) {
            return false;
        }
        return true;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean checkConditionInt(String key, int defaultValue, Bundle condition, Intent intent) {
        if (!condition.containsKey(key) || !intent.hasExtra(key) || intent.getIntExtra(key, defaultValue) == condition.getInt(key)) {
            return true;
        }
        return false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean checkConditionString(String key, Bundle condition, Intent intent) {
        String regex;
        if (!condition.containsKey(key) || !intent.hasExtra(key) || (regex = condition.getString(key)) == null) {
            return true;
        }
        String extra = intent.getStringExtra(key);
        if (extra == null || !extra.matches(regex)) {
            return false;
        }
        return true;
    }

    private void bindAndStartDiagnosis(String diagnosisName) {
        if (isServiceRegistered()) {
            SemEmergencyManager em = SemEmergencyManager.getInstance(this.mContext);
            if (em != null && em.isEmergencyMode()) {
                Log.i(TAG, "Do not bindAndStartDiagnosis in EmergencyMode");
            } else if (this.mService == null) {
                Intent serviceIntent = new Intent();
                serviceIntent.setClassName(this.mCurrentServicePackage, this.mCurrentServiceClass);
                try {
                    this.mDiagnosisName = diagnosisName;
                    Log.i(TAG, "request bind service with diagName");
                    this.mContext.bindService(serviceIntent, this.mConnection, 1);
                } catch (Exception e) {
                }
            } else {
                startDiagnosis(diagnosisName);
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void bindAndStartDiagnosis() {
        if (isServiceRegistered()) {
            SemEmergencyManager em = SemEmergencyManager.getInstance(this.mContext);
            if (em != null && em.isEmergencyMode()) {
                Log.i(TAG, "Do not bindAndStartDiagnosis in EmergencyMode");
            } else if (this.mService == null) {
                Intent serviceIntent = new Intent();
                serviceIntent.setClassName(this.mCurrentServicePackage, this.mCurrentServiceClass);
                try {
                    Log.i(TAG, "request bind service");
                    this.mContext.bindService(serviceIntent, this.mConnection, 1);
                } catch (Exception e) {
                }
            } else {
                startDiagnosis();
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void unbindService() {
        if (isServiceRegistered() && this.mService != null) {
            Log.i(TAG, "request unbind service");
            try {
                this.mContext.unbindService(this.mConnection);
                this.mService = null;
            } catch (Exception e) {
            }
        }
    }

    private void startDiagnosis() {
        IWifiGuiderService iWifiGuiderService = this.mService;
        if (iWifiGuiderService != null) {
            try {
                iWifiGuiderService.runDiagnosis("all");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        this.mServiceHandler.unbindServiceDelay(60);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void startDiagnosis(String diagnosisName) {
        if (this.mService != null) {
            try {
                Log.d(TAG, "runDiagnosis(" + diagnosisName + ")");
                this.mService.runDiagnosis(diagnosisName);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        this.mServiceHandler.unbindServiceDelay(60);
    }

    public void updateConditions(Bundle bundle) {
        Log.d(TAG, "updateConditions");
        Message msg = Message.obtain();
        msg.obj = bundle;
        msg.what = 3;
        this.mServiceHandler.sendMessage(msg);
    }

    /* access modifiers changed from: package-private */
    public final class ServiceHandler extends Handler {
        static final int CMD_BIND_AND_START_DIAGNOSIS = 2;
        static final int CMD_RECEIVED_DIAGNOSIS_RESULT_AVAILABLE = 4;
        static final int CMD_SEC_COMMAND_ID_GUIDER_FEATURE_CONTROL = 3;
        static final int CMD_UNBIND = 1;

        ServiceHandler(Looper looper) {
            super(looper);
        }

        /* access modifiers changed from: package-private */
        public void unbindServiceDelay(int seconds) {
            removeMessages(1);
            sendEmptyMessageDelayed(1, ((long) seconds) * 1000);
        }

        /* access modifiers changed from: package-private */
        public void bindService() {
            removeMessages(1);
            sendEmptyMessage(2);
        }

        /* access modifiers changed from: package-private */
        public void receivedDiagnosisResultAvailable() {
            removeMessages(4);
            sendEmptyMessage(4);
        }

        public void handleMessage(Message msg) {
            int i = msg.what;
            if (i == 1) {
                WifiGuiderManagementService.this.unbindService();
            } else if (i == 2) {
                WifiGuiderManagementService.this.bindAndStartDiagnosis();
            } else if (i == 3) {
                WifiGuiderFeatureController.getInstance(WifiGuiderManagementService.this.mContext).updateConditions((Bundle) msg.obj);
            } else if (i == 4) {
                WifiGuiderManagementService.this.updateCachedDiagResult();
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void startWifiGuiderService(String action) {
        if (isServiceRegistered()) {
            if (!hasWifiGuiderPermission(this.mCurrentServicePackage)) {
                Log.i(TAG, this.mCurrentServicePackage + "does not have valid permission");
                return;
            }
            Intent serviceIntent = new Intent();
            serviceIntent.setClassName(this.mCurrentServicePackage, this.mCurrentServiceClass);
            serviceIntent.putExtra("action", action);
            try {
                this.mContext.startForegroundService(serviceIntent);
                if (DBG) {
                    Log.i(TAG, "start service with action " + action);
                }
            } catch (Exception e) {
                Log.e(TAG, "can't start service " + this.mCurrentServiceClass);
                if (DBG) {
                    Log.e(TAG, e.toString());
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void registerAndStartWifiGuiderService(String packageName) {
        if (!hasWifiGuiderPermission(packageName)) {
            Log.i(TAG, packageName + "does not have valid permission");
            return;
        }
        Intent serviceIntent = new Intent();
        serviceIntent.setClassName(packageName, packageName + "." + mDefaultServiceClass);
        serviceIntent.putExtra("requestToRegister", true);
        try {
            this.mContext.startForegroundService(serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "can't start service " + packageName);
            if (DBG) {
                Log.e(TAG, e.toString());
            }
        }
    }

    private boolean hasWifiGuiderPermission(String packageName) {
        return this.mContext.getPackageManager().checkPermission("com.samsung.permission.WIFI_DIAGNOSTICS_PROVIDER", packageName) == 0;
    }

    public void registerForBroadcastsForWifiGuider() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.PACKAGE_ADDED");
        intentFilter.addAction("android.intent.action.PACKAGE_REPLACED");
        intentFilter.addDataScheme("package");
        this.mContext.registerReceiverAsUser(new BroadcastReceiver() {
            /* class com.samsung.android.server.wifi.WifiGuiderManagementService.C07405 */

            /* JADX WARNING: Removed duplicated region for block: B:12:0x002b A[ADDED_TO_REGION] */
            /* JADX WARNING: Removed duplicated region for block: B:17:0x0040  */
            /* JADX WARNING: Removed duplicated region for block: B:20:? A[RETURN, SYNTHETIC] */
            public void onReceive(Context context, Intent intent) {
                char c;
                Uri uri;
                String pkgName;
                String action = intent.getAction();
                int hashCode = action.hashCode();
                if (hashCode != -810471698) {
                    if (hashCode == 1544582882 && action.equals("android.intent.action.PACKAGE_ADDED")) {
                        c = 0;
                        if ((c != 0 || c == 1) && (uri = intent.getData()) != null) {
                            pkgName = uri.getSchemeSpecificPart();
                            if (WifiGuiderManagementService.mDefaultPackage.equals(pkgName)) {
                                WifiGuiderManagementService.this.registerAndStartWifiGuiderService(pkgName);
                                return;
                            }
                            return;
                        }
                        return;
                    }
                } else if (action.equals("android.intent.action.PACKAGE_REPLACED")) {
                    c = 1;
                    if (c != 0) {
                    }
                    pkgName = uri.getSchemeSpecificPart();
                    if (WifiGuiderManagementService.mDefaultPackage.equals(pkgName)) {
                    }
                }
                c = 65535;
                if (c != 0) {
                }
                pkgName = uri.getSchemeSpecificPart();
                if (WifiGuiderManagementService.mDefaultPackage.equals(pkgName)) {
                }
            }
        }, UserHandle.ALL, intentFilter, null, null);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void updateCachedDiagResult() {
        if (this.mService != null) {
            try {
                this.mCachedDiagResults.clear();
                for (String result : this.mService.getDiagnosisResults()) {
                    this.mCachedDiagResults.add(result);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void registerDiagnosisCompleteIntent() {
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.samsung.android.server.wifi.WifiGuiderManagementService.C07416 */

            public void onReceive(Context context, Intent intent) {
                Log.i(WifiGuiderManagementService.TAG, "ACTION_DIAGNOSIS_RESULT_AVAILABLE");
                WifiGuiderManagementService.this.mServiceHandler.receivedDiagnosisResultAvailable();
            }
        }, new IntentFilter(ACTION_DIAGNOSIS_RESULT_AVAILABLE));
    }

    public synchronized List<String> getCachedDiagnosisResults() {
        List<String> result;
        result = new ArrayList<>();
        result.addAll(this.mCachedDiagResults);
        return result;
    }
}

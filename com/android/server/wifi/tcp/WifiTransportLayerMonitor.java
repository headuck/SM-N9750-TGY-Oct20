package com.android.server.wifi.tcp;

import android.app.ActivityManager;
import android.app.usage.IUsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;
import com.android.server.wifi.ClientModeImpl;
import com.android.server.wifi.WifiConnectivityMonitor;
import com.android.server.wifi.tcp.WifiApInfo;
import com.samsung.android.app.usage.IUsageStatsWatcher;
import com.samsung.android.feature.SemCscFeature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class WifiTransportLayerMonitor extends Handler {
    private static final boolean DBG = Debug.semIsProductDev();
    private static final String TAG = "WifiTransportLayerMonitor";
    private BroadcastReceiver mBroadcastReceiver;
    private final Context mContext;
    private WifiApInfo mCurrentWifiApInfo;
    private WifiPackageInfo mCurrentWifiPackageInfo;
    private PackageManager mPackageManager;
    private BroadcastReceiver mPackageReceiver;
    private PackageUpdateHandler mPackageUpdateHandler;
    private IUsageStatsManager mUsageStatsManager;
    private String mUsageStatsPackageName;
    private int mUsageStatsUid;
    private final IUsageStatsWatcher.Stub mUsageStatsWatcher = new IUsageStatsWatcher.Stub() {
        /* class com.android.server.wifi.tcp.WifiTransportLayerMonitor.C05764 */

        public void noteResumeComponent(ComponentName resumeComponentName, Intent intent) {
            if (resumeComponentName == null) {
                try {
                    if (WifiTransportLayerMonitor.DBG) {
                        Log.d(WifiTransportLayerMonitor.TAG, "resumeComponentName is null");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                resumeComponentName.getPackageName();
                String packageName = ActivityManager.getService().getFocusedStackInfo().topActivity.getPackageName();
                if (!WifiTransportLayerMonitor.this.mUsageStatsPackageName.equals(packageName)) {
                    if (WifiTransportLayerMonitor.DBG) {
                        Log.d(WifiTransportLayerMonitor.TAG, "IUsageStatsWatcher resume package changed: " + packageName);
                    }
                    ApplicationInfo appInfo = WifiTransportLayerMonitor.this.getPackageManager().getApplicationInfo(packageName, 128);
                    WifiTransportLayerMonitor.this.mUsageStatsUid = appInfo.uid;
                    WifiTransportLayerMonitor.this.mUsageStatsPackageName = packageName;
                    WifiTransportLayerMonitor.this.mCurrentWifiPackageInfo = WifiTransportLayerMonitor.this.getOrCreatePackageInfo(WifiTransportLayerMonitor.this.mUsageStatsUid, WifiTransportLayerMonitor.this.mUsageStatsPackageName);
                }
            }
        }

        public void notePauseComponent(ComponentName pauseComponentName) {
            if (pauseComponentName == null) {
                try {
                    if (WifiTransportLayerMonitor.DBG) {
                        Log.d(WifiTransportLayerMonitor.TAG, "pauseComponentName is null");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                pauseComponentName.getPackageName();
                String packageName = ActivityManager.getService().getFocusedStackInfo().topActivity.getPackageName();
                if (!WifiTransportLayerMonitor.this.mUsageStatsPackageName.equals(packageName)) {
                    if (WifiTransportLayerMonitor.DBG) {
                        Log.d(WifiTransportLayerMonitor.TAG, "IUsageStatsWatcher pause package changed: " + packageName);
                    }
                    ApplicationInfo appInfo = WifiTransportLayerMonitor.this.getPackageManager().getApplicationInfo(packageName, 128);
                    WifiTransportLayerMonitor.this.mUsageStatsUid = appInfo.uid;
                    WifiTransportLayerMonitor.this.mUsageStatsPackageName = packageName;
                    WifiTransportLayerMonitor.this.mCurrentWifiPackageInfo = WifiTransportLayerMonitor.this.getOrCreatePackageInfo(WifiTransportLayerMonitor.this.mUsageStatsUid, WifiTransportLayerMonitor.this.mUsageStatsPackageName);
                }
            }
        }

        public void noteStopComponent(ComponentName arg0) throws RemoteException {
        }
    };
    private HashMap<String, WifiApInfo> mWifiApInfoList;
    private HashMap<Integer, WifiPackageInfo> mWifiPackageInfoList;
    private final Object mWifiPackageInfoLock = new Object();
    private ArrayList<Integer> mWifiSwitchEnabledUidList = new ArrayList<>();
    private WifiTransportLayerFileManager mWifiTransportLayerFileManager = new WifiTransportLayerFileManager();

    public WifiTransportLayerMonitor(Looper looper, WifiConnectivityMonitor wifiConnectivityMonitor, ClientModeImpl cmi, Context context) {
        super(looper);
        this.mContext = context;
        HandlerThread networkStatsThread = new HandlerThread("NetworkStatsThread");
        networkStatsThread.start();
        this.mPackageUpdateHandler = new PackageUpdateHandler(networkStatsThread.getLooper());
        try {
            this.mUsageStatsUid = -1;
            this.mUsageStatsPackageName = "default";
            this.mUsageStatsManager = IUsageStatsManager.Stub.asInterface(ServiceManager.getService("usagestats"));
            this.mUsageStatsManager.registerUsageStatsWatcher(this.mUsageStatsWatcher);
        } catch (Exception e) {
            Log.w(TAG, "Exception occured while register UsageStatWatcher " + e);
            e.printStackTrace();
        }
        loadInfoFromFile();
        setupBroadcastReceiver();
        setAudioPlaybackCallback();
    }

    /* access modifiers changed from: private */
    public class PackageUpdateHandler extends Handler {
        private static final int MSG_CREATE_PACKAGE_INFO = 3;
        private static final int MSG_RUN_UPDATE_PACKAGE_INFO = 1;
        private static final int MSG_UPDATE_CATEGORY = 2;
        private static final int MSG_UPDATE_PACKAGE_INFO = 4;
        private final String TAG = "WifiTransportLayerMonitor.PackageUpdateHandler";

        public PackageUpdateHandler(Looper looper) {
            super(looper);
        }

        /* JADX INFO: Multiple debug info for r0v3 int: [D('uidCreate' int), D('uid' int)] */
        /* JADX INFO: Multiple debug info for r0v4 int: [D('uidUpdate' int), D('uidCreate' int)] */
        public void handleMessage(Message msg) {
            int i = msg.what;
            if (i == 1) {
                if (WifiTransportLayerMonitor.DBG) {
                    Log.d("WifiTransportLayerMonitor.PackageUpdateHandler", "MSG_RUN_UPDATE_PACKAGE_INFO");
                }
                updateMissingPackageInfo();
                updatePackageCategoryInfo();
            } else if (i == 2) {
                int uid = msg.arg1;
                String packageName = (String) msg.obj;
                if (WifiTransportLayerMonitor.DBG) {
                    Log.d("WifiTransportLayerMonitor.PackageUpdateHandler", "MSG_UPDATE_CATEGORY - " + packageName);
                }
                String category = WifiTransportLayerUtils.getApplicationCategory(WifiTransportLayerMonitor.this.mContext, packageName);
                if (!category.equals(WifiTransportLayerUtils.CATEGORY_PLAYSTORE_NONE)) {
                    WifiTransportLayerMonitor.this.updateWifiPackageInfoCategory(uid, category);
                    WifiTransportLayerMonitor.this.saveWifiPackageInfoList();
                }
            } else if (i == 3) {
                int uidCreate = msg.arg1;
                String packageNameCreate = (String) msg.obj;
                if (WifiTransportLayerMonitor.DBG) {
                    Log.d("WifiTransportLayerMonitor.PackageUpdateHandler", "MSG_CREATE_PACKAGE_INFO - " + uidCreate);
                }
                WifiTransportLayerMonitor.this.createWifiPackageInfo(uidCreate, packageNameCreate);
            } else if (i == 4) {
                int uidUpdate = msg.arg1;
                if (WifiTransportLayerMonitor.DBG) {
                    Log.d("WifiTransportLayerMonitor.PackageUpdateHandler", "MSG_UPDATE_PACKAGE_INFO - " + uidUpdate);
                }
                WifiPackageInfo info = WifiTransportLayerMonitor.this.getWifiPackageInfo(uidUpdate);
                if (info != null) {
                    info.updatePackageInfo(WifiTransportLayerMonitor.this.mContext);
                    WifiTransportLayerMonitor.this.updateWifiPackageInfo(info, true);
                }
            }
        }

        private void updateMissingPackageInfo() {
            ArrayList<WifiPackageInfo> updateList = new ArrayList<>();
            try {
                for (ApplicationInfo app : WifiTransportLayerMonitor.this.getPackageManager().getInstalledApplications(0)) {
                    if (WifiTransportLayerMonitor.this.mWifiPackageInfoList != null && !WifiTransportLayerMonitor.this.mWifiPackageInfoList.containsKey(Integer.valueOf(app.uid))) {
                        if (WifiTransportLayerMonitor.DBG) {
                            Log.d("WifiTransportLayerMonitor.PackageUpdateHandler", "updateMissingPackageInfo (add) - " + app.uid + ":" + app.packageName);
                        }
                        updateList.add(new WifiPackageInfo(WifiTransportLayerMonitor.this.mContext, app.uid, app.packageName));
                    }
                }
                WifiTransportLayerMonitor.this.updateWifiPackageInfoList(updateList);
            } catch (Exception e) {
                if (WifiTransportLayerMonitor.DBG) {
                    Log.d("WifiTransportLayerMonitor.PackageUpdateHandler", "updateMissingPackageInfo - Exception " + e);
                }
                e.printStackTrace();
            }
        }

        private void updatePackageCategoryInfo() {
            if (WifiTransportLayerMonitor.this.mWifiPackageInfoList != null && WifiTransportLayerMonitor.this.isCategoryUpdateable()) {
                try {
                    ArrayList<Integer> uidList = new ArrayList<>();
                    uidList.addAll(WifiTransportLayerMonitor.this.mWifiPackageInfoList.keySet());
                    Iterator<Integer> it = uidList.iterator();
                    while (it.hasNext()) {
                        int uid = it.next().intValue();
                        if (WifiTransportLayerUtils.CATEGORY_PLAYSTORE_NONE.equals(WifiTransportLayerMonitor.this.getWifiPackageInfoCategory(uid))) {
                            String packageName = WifiTransportLayerMonitor.this.getPackageName(uid);
                            String category = WifiTransportLayerUtils.getApplicationCategory(WifiTransportLayerMonitor.this.mContext, packageName);
                            if (WifiTransportLayerUtils.CATEGORY_PLAYSTORE_NONE.equals(category)) {
                                WifiTransportLayerMonitor.this.updateWifiPackageInfoCategoryFailHistory(uid);
                            } else {
                                if (WifiTransportLayerMonitor.DBG) {
                                    Log.d("WifiTransportLayerMonitor.PackageUpdateHandler", "updatePackageCategoryInfo - " + packageName + "-" + category);
                                }
                                WifiTransportLayerMonitor.this.updateWifiPackageInfoCategory(uid, category);
                            }
                        }
                    }
                    WifiTransportLayerMonitor.this.saveWifiPackageInfoList();
                } catch (Exception e) {
                    if (WifiTransportLayerMonitor.DBG) {
                        Log.d("WifiTransportLayerMonitor.PackageUpdateHandler", "updatePackageCategoryInfo - Exception " + e);
                    }
                    e.printStackTrace();
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private PackageManager getPackageManager() {
        if (this.mPackageManager == null) {
            this.mPackageManager = this.mContext.getPackageManager();
        }
        return this.mPackageManager;
    }

    private void setAudioPlaybackCallback() {
        if (DBG) {
            Log.d(TAG, "setAudioPlaybackCallback");
        }
        ((AudioManager) this.mContext.getSystemService("audio")).registerAudioPlaybackCallback(new AudioManager.AudioPlaybackCallback() {
            /* class com.android.server.wifi.tcp.WifiTransportLayerMonitor.C05731 */

            @Override // android.media.AudioManager.AudioPlaybackCallback
            public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
                if (configs != null) {
                    for (AudioPlaybackConfiguration config : configs) {
                        if (config.getAudioAttributes().getUsage() == 2) {
                            synchronized (WifiTransportLayerMonitor.this.mWifiPackageInfoLock) {
                                if (WifiTransportLayerMonitor.this.mWifiPackageInfoList != null && WifiTransportLayerMonitor.this.mWifiPackageInfoList.containsKey(Integer.valueOf(config.getClientUid())) && !((WifiPackageInfo) WifiTransportLayerMonitor.this.mWifiPackageInfoList.get(Integer.valueOf(config.getClientUid()))).isVoip()) {
                                    if (WifiTransportLayerMonitor.DBG) {
                                        Log.d(WifiTransportLayerMonitor.TAG, "onPlaybackConfigChanged - " + config.getClientUid() + " added");
                                    }
                                    ((WifiPackageInfo) WifiTransportLayerMonitor.this.mWifiPackageInfoList.get(Integer.valueOf(config.getClientUid()))).setIsVoip(true);
                                }
                            }
                        }
                    }
                }
                super.onPlaybackConfigChanged(configs);
            }
        }, this);
    }

    private void setupBroadcastReceiver() {
        if (DBG) {
            Log.d(TAG, "setupBroadcastReceiver");
        }
        this.mBroadcastReceiver = new BroadcastReceiver() {
            /* class com.android.server.wifi.tcp.WifiTransportLayerMonitor.C05742 */

            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (((action.hashCode() == 798292259 && action.equals("android.intent.action.BOOT_COMPLETED")) ? (char) 0 : 65535) == 0) {
                    if (WifiTransportLayerMonitor.DBG) {
                        Log.d(WifiTransportLayerMonitor.TAG, "ACTION_BOOT_COMPLETED");
                    }
                    WifiTransportLayerMonitor.this.mPackageUpdateHandler.sendEmptyMessage(1);
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.BOOT_COMPLETED");
        this.mContext.registerReceiver(this.mBroadcastReceiver, intentFilter);
        this.mPackageReceiver = new BroadcastReceiver() {
            /* class com.android.server.wifi.tcp.WifiTransportLayerMonitor.C05753 */

            /* JADX WARNING: Removed duplicated region for block: B:18:0x0040  */
            /* JADX WARNING: Removed duplicated region for block: B:53:0x00fe  */
            public void onReceive(Context context, Intent intent) {
                char c;
                Uri uri;
                String packageName;
                Uri uri2;
                String packageName2;
                Uri uri3;
                String packageName3;
                String action = intent.getAction();
                int hashCode = action.hashCode();
                if (hashCode != -810471698) {
                    if (hashCode != 525384130) {
                        if (hashCode == 1544582882 && action.equals("android.intent.action.PACKAGE_ADDED")) {
                            c = 0;
                            if (c == 0) {
                                if (c != 1) {
                                    if (c == 2 && intent.getData() != null && (uri3 = intent.getData()) != null && (packageName3 = uri3.getSchemeSpecificPart()) != null) {
                                        try {
                                            ApplicationInfo appInfo = WifiTransportLayerMonitor.this.getPackageManager().getApplicationInfo(packageName3, 128);
                                            if (WifiTransportLayerMonitor.this.mWifiPackageInfoList != null && WifiTransportLayerMonitor.this.mWifiPackageInfoList.containsKey(Integer.valueOf(appInfo.uid))) {
                                                WifiTransportLayerMonitor.this.mPackageUpdateHandler.sendMessage(WifiTransportLayerMonitor.this.obtainMessage(4, appInfo.uid, 0));
                                                if (WifiTransportLayerMonitor.DBG) {
                                                    Log.d(WifiTransportLayerMonitor.TAG, "ACTION_PACKAGE_REPLACED - updated");
                                                    return;
                                                }
                                                return;
                                            }
                                            return;
                                        } catch (PackageManager.NameNotFoundException e) {
                                            e.printStackTrace();
                                            return;
                                        }
                                    } else {
                                        return;
                                    }
                                } else if (intent.getBooleanExtra("android.intent.extra.REPLACING", false)) {
                                    if (WifiTransportLayerMonitor.DBG) {
                                        Log.d(WifiTransportLayerMonitor.TAG, "ACTION_PACKAGE_REMOVED - remove app before replace");
                                        return;
                                    }
                                    return;
                                } else if (intent.getData() != null && (uri2 = intent.getData()) != null && (packageName2 = uri2.getSchemeSpecificPart()) != null) {
                                    try {
                                        WifiTransportLayerMonitor.this.removeWifiPackageInfo(WifiTransportLayerMonitor.this.getPackageManager().getApplicationInfo(packageName2, 128).uid);
                                        if (WifiTransportLayerMonitor.DBG) {
                                            Log.d(WifiTransportLayerMonitor.TAG, "ACTION_PACKAGE_REMOVED - " + packageName2);
                                            return;
                                        }
                                        return;
                                    } catch (PackageManager.NameNotFoundException e2) {
                                        e2.printStackTrace();
                                        return;
                                    }
                                } else {
                                    return;
                                }
                            } else if (intent.getData() != null && (uri = intent.getData()) != null && (packageName = uri.getSchemeSpecificPart()) != null) {
                                try {
                                    ApplicationInfo appInfo2 = WifiTransportLayerMonitor.this.getPackageManager().getApplicationInfo(packageName, 128);
                                    if (WifiTransportLayerMonitor.this.mWifiPackageInfoList == null || !WifiTransportLayerMonitor.this.mWifiPackageInfoList.containsKey(Integer.valueOf(appInfo2.uid))) {
                                        WifiTransportLayerMonitor.this.mPackageUpdateHandler.sendMessage(WifiTransportLayerMonitor.this.obtainMessage(3, appInfo2.uid, 0, appInfo2.packageName));
                                        if (WifiTransportLayerMonitor.DBG) {
                                            Log.d(WifiTransportLayerMonitor.TAG, "ACTION_PACKAGE_ADDED - " + packageName);
                                            return;
                                        }
                                        return;
                                    } else if (WifiTransportLayerMonitor.DBG) {
                                        Log.d(WifiTransportLayerMonitor.TAG, "ACTION_PACKAGE_ADDED - exist");
                                        return;
                                    } else {
                                        return;
                                    }
                                } catch (PackageManager.NameNotFoundException e3) {
                                    e3.printStackTrace();
                                    return;
                                }
                            } else {
                                return;
                            }
                        }
                    } else if (action.equals("android.intent.action.PACKAGE_REMOVED")) {
                        c = 1;
                        if (c == 0) {
                        }
                    }
                } else if (action.equals("android.intent.action.PACKAGE_REPLACED")) {
                    c = 2;
                    if (c == 0) {
                    }
                }
                c = 65535;
                if (c == 0) {
                }
            }
        };
        IntentFilter packageFilter = new IntentFilter();
        packageFilter.addAction("android.intent.action.PACKAGE_ADDED");
        packageFilter.addAction("android.intent.action.PACKAGE_REMOVED");
        packageFilter.addAction("android.intent.action.PACKAGE_REPLACED");
        packageFilter.addDataScheme("package");
        this.mContext.registerReceiver(this.mPackageReceiver, packageFilter);
    }

    private void loadInfoFromFile() {
        HashMap<String, WifiApInfo> hashMap;
        if (DBG) {
            Log.d(TAG, "loadInfoFromFile");
        }
        synchronized (this.mWifiPackageInfoLock) {
            this.mWifiPackageInfoList = this.mWifiTransportLayerFileManager.loadWifiPackageInfoFromFile();
        }
        this.mWifiApInfoList = this.mWifiTransportLayerFileManager.loadWifiApInfoFromFile();
        if (DBG && (hashMap = this.mWifiApInfoList) != null && !hashMap.isEmpty()) {
            Iterator<WifiApInfo> it = this.mWifiApInfoList.values().iterator();
            while (it.hasNext()) {
                Log.d(TAG, "loadInfoFromFile - AP - " + it.next().toString());
            }
        }
        this.mWifiSwitchEnabledUidList = this.mWifiTransportLayerFileManager.loadSwitchEnabledUidListFromFile();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void saveWifiPackageInfoList() {
        if (DBG) {
            Log.d(TAG, "saveWifiPackageInfoList");
        }
        synchronized (this.mWifiPackageInfoLock) {
            this.mWifiTransportLayerFileManager.saveWifiPackageInfoToFile(this.mWifiPackageInfoList);
        }
    }

    private void saveWifiApInfoList() {
        if (DBG) {
            Log.d(TAG, "saveWifiApInfoList");
        }
        this.mWifiTransportLayerFileManager.saveWifiApInfoToFile(this.mWifiApInfoList);
    }

    private void saveWifiSwitchabledAppList() {
        if (DBG) {
            Log.d(TAG, "saveWifiSwitchableAppList");
        }
        this.mWifiTransportLayerFileManager.saveSwitchEnabledUidListToFile(this.mWifiSwitchEnabledUidList);
    }

    public int getUsageStatsCurrentUid() {
        return this.mUsageStatsUid;
    }

    public String getUsageStatsCurrentPackageName() {
        return this.mUsageStatsPackageName;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private String getWifiPackageInfoCategory(int uid) {
        synchronized (this.mWifiPackageInfoLock) {
            if (this.mWifiPackageInfoList == null || !this.mWifiPackageInfoList.containsKey(Integer.valueOf(uid))) {
                return null;
            }
            return this.mWifiPackageInfoList.get(Integer.valueOf(uid)).getCategory();
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateWifiPackageInfoCategory(int uid, String category) {
        if (DBG) {
            Log.d(TAG, "updateWifiPackageInfoCategory - " + uid + ", " + category);
        }
        synchronized (this.mWifiPackageInfoLock) {
            if (this.mWifiPackageInfoList != null && this.mWifiPackageInfoList.containsKey(Integer.valueOf(uid))) {
                this.mWifiPackageInfoList.get(Integer.valueOf(uid)).setCategory(category);
                if (this.mCurrentWifiPackageInfo.getUid() == uid) {
                    this.mCurrentWifiPackageInfo.setCategory(category);
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateWifiPackageInfoCategoryFailHistory(int uid) {
        if (DBG) {
            Log.d(TAG, "updateWifiPackageInfoCategoryFailHistory - " + uid);
        }
        synchronized (this.mWifiPackageInfoLock) {
            if (this.mWifiPackageInfoList != null && this.mWifiPackageInfoList.containsKey(Integer.valueOf(uid))) {
                this.mWifiPackageInfoList.get(Integer.valueOf(uid)).addCategoryUpdateFailCount();
                if (this.mWifiPackageInfoList.get(Integer.valueOf(uid)).getCategoryUpdateFailCount() > 3) {
                    this.mWifiPackageInfoList.get(Integer.valueOf(uid)).setCategory(WifiTransportLayerUtils.CATEGORY_PLAYSTORE_FAILED);
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateWifiPackageInfoList(ArrayList<WifiPackageInfo> list) {
        if (DBG) {
            Log.d(TAG, "updateWifiPackageInfoList");
        }
        if (list != null) {
            synchronized (this.mWifiPackageInfoLock) {
                Iterator<WifiPackageInfo> it = list.iterator();
                while (it.hasNext()) {
                    WifiPackageInfo info = it.next();
                    if (this.mWifiPackageInfoList != null && !this.mWifiPackageInfoList.containsKey(Integer.valueOf(info.getUid()))) {
                        this.mWifiPackageInfoList.put(Integer.valueOf(info.getUid()), info);
                    }
                }
            }
            saveWifiPackageInfoList();
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateWifiPackageInfo(WifiPackageInfo info, boolean force) {
        if (DBG) {
            Log.d(TAG, "updateWifiPackageInfo - " + info.getUid() + ", " + force);
        }
        synchronized (this.mWifiPackageInfoLock) {
            if (this.mWifiPackageInfoList != null) {
                if (this.mWifiPackageInfoList.containsKey(Integer.valueOf(info.getUid()))) {
                    if (force) {
                        this.mWifiPackageInfoList.remove(Integer.valueOf(info.getUid()));
                    } else {
                        return;
                    }
                }
                this.mWifiPackageInfoList.put(Integer.valueOf(info.getUid()), info);
                saveWifiPackageInfoList();
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private WifiPackageInfo getOrCreatePackageInfo(int uid, String packageName) {
        if (DBG) {
            Log.d(TAG, "getOrCreatePackageInfo - " + uid + " " + packageName);
        }
        WifiPackageInfo info = getWifiPackageInfo(uid);
        if (info == null) {
            Log.d(TAG, "getOrCreatePackageInfo - create new info");
            return createWifiPackageInfo(uid, packageName);
        } else if (!info.isSystemApp() && !info.getPackageName().equals(packageName)) {
            Log.d(TAG, "getOrCreatePackageInfo - invalid packageName");
            WifiPackageInfo info2 = createWifiPackageInfo(uid, packageName);
            updateWifiPackageInfo(info2, true);
            return info2;
        } else if (this.mPackageUpdateHandler == null || !info.getCategory().equals(WifiTransportLayerUtils.CATEGORY_PLAYSTORE_NONE)) {
            return info;
        } else {
            this.mPackageUpdateHandler.sendMessage(obtainMessage(2, uid, 0, packageName));
            return info;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private WifiPackageInfo createWifiPackageInfo(int uid, String packageName) {
        WifiPackageInfo info = new WifiPackageInfo(this.mContext, uid, packageName);
        updateWifiPackageInfo(info, true);
        if (this.mPackageUpdateHandler != null && info.getPackageName().equals(WifiTransportLayerUtils.CATEGORY_PLAYSTORE_NONE)) {
            this.mPackageUpdateHandler.sendMessage(obtainMessage(2, uid, 0, packageName));
        }
        return info;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void removeWifiPackageInfo(int uid) {
        if (DBG) {
            Log.d(TAG, "removeWifiPackageInfo - " + uid);
        }
        synchronized (this.mWifiPackageInfoLock) {
            if (this.mWifiPackageInfoList != null && this.mWifiPackageInfoList.containsKey(Integer.valueOf(uid))) {
                this.mWifiPackageInfoList.remove(Integer.valueOf(uid));
            }
        }
    }

    public WifiPackageInfo getWifiPackageInfo(int uid) {
        synchronized (this.mWifiPackageInfoLock) {
            if (this.mWifiPackageInfoList == null || !this.mWifiPackageInfoList.containsKey(Integer.valueOf(uid))) {
                return null;
            }
            return new WifiPackageInfo(this.mWifiPackageInfoList.get(Integer.valueOf(uid)));
        }
    }

    public boolean isSwitchableApp(int uid) {
        synchronized (this.mWifiPackageInfoLock) {
            if (this.mWifiPackageInfoList == null || !this.mWifiPackageInfoList.containsKey(Integer.valueOf(uid))) {
                return false;
            }
            return this.mWifiPackageInfoList.get(Integer.valueOf(uid)).isSwitchable();
        }
    }

    public String getPackageName(int uid) {
        synchronized (this.mWifiPackageInfoLock) {
            if (this.mWifiPackageInfoList == null || !this.mWifiPackageInfoList.containsKey(Integer.valueOf(uid))) {
                return "";
            }
            return this.mWifiPackageInfoList.get(Integer.valueOf(uid)).getPackageName();
        }
    }

    public WifiPackageInfo getCurrentPackageInfo() {
        try {
            String focusedPackageName = ActivityManager.getService().getFocusedStackInfo().topActivity.getPackageName();
            if (!this.mUsageStatsPackageName.equals(focusedPackageName)) {
                if (DBG) {
                    Log.d(TAG, "getCurrentPackageInfo package changed: " + focusedPackageName);
                }
                this.mUsageStatsUid = getPackageManager().getApplicationInfo(focusedPackageName, 128).uid;
                this.mUsageStatsPackageName = focusedPackageName;
                this.mCurrentWifiPackageInfo = getOrCreatePackageInfo(this.mUsageStatsUid, this.mUsageStatsPackageName);
            }
        } catch (PackageManager.NameNotFoundException e) {
            if (DBG) {
                Log.w(TAG, "getCurrentPackageInfo - NameNotFoundException");
            }
            e.printStackTrace();
        } catch (RemoteException e2) {
            if (DBG) {
                Log.w(TAG, "getCurrentPackageInfo - RemoteException");
            }
            e2.printStackTrace();
        } catch (Exception e3) {
            if (DBG) {
                Log.w(TAG, "getCurrentPackageInfo - Exception");
            }
            e3.printStackTrace();
        }
        return this.mCurrentWifiPackageInfo;
    }

    public WifiApInfo getWifiApInfo(String ssid) {
        if (DBG) {
            Log.d(TAG, "getWifiApInfo - " + ssid);
        }
        HashMap<String, WifiApInfo> hashMap = this.mWifiApInfoList;
        if (hashMap == null || !hashMap.containsKey(ssid)) {
            return null;
        }
        return this.mWifiApInfoList.get(ssid);
    }

    public void updateWifiApInfo(WifiApInfo info) {
        if (DBG) {
            Log.d(TAG, "updateWifiApInfo - " + info);
        }
        HashMap<String, WifiApInfo> hashMap = this.mWifiApInfoList;
        if (hashMap != null) {
            if (hashMap.containsKey(info.getSsid())) {
                this.mWifiApInfoList.remove(info.getSsid());
            }
            this.mWifiApInfoList.put(info.getSsid(), info);
            saveWifiApInfoList();
        }
    }

    public WifiApInfo setSsid(String ssid) {
        if (DBG) {
            Log.d(TAG, "setSsid");
        }
        this.mCurrentWifiApInfo = getWifiApInfo(ssid);
        if (this.mCurrentWifiApInfo == null) {
            this.mCurrentWifiApInfo = new WifiApInfo(ssid);
            updateWifiApInfo(this.mCurrentWifiApInfo);
        }
        return this.mCurrentWifiApInfo;
    }

    public WifiApInfo getCurrentWifiApInfo() {
        if (DBG) {
            Log.d(TAG, "getCurrentWifiApInfo");
        }
        return this.mCurrentWifiApInfo;
    }

    public void resetSwitchForIndivdiaulAppsDetectionCount(String packageName) {
        if (DBG) {
            Log.d(TAG, "resetSwitchForIndivdiaulAppsDetectionCount - " + packageName);
        }
        HashMap<String, WifiApInfo> hashMap = this.mWifiApInfoList;
        if (hashMap != null) {
            for (WifiApInfo info : hashMap.values()) {
                info.resetSwitchForIndivdiaulAppsDetectionCount(packageName);
            }
        }
        saveWifiApInfoList();
    }

    public boolean isSwitchEnabledApp(int uid) {
        return this.mWifiSwitchEnabledUidList.contains(Integer.valueOf(uid));
    }

    public void enableSwitchEnabledAppInfo(int uid) {
        updateSwitchEnabledAppInfo(uid, true);
    }

    private void updateSwitchEnabledAppInfo(int uid, boolean enable) {
        if (DBG) {
            Log.d(TAG, "updateSwitchEnabledAppInfo - " + uid + " " + enable);
        }
        if (enable) {
            ArrayList<Integer> arrayList = this.mWifiSwitchEnabledUidList;
            if (arrayList != null && !arrayList.contains(Integer.valueOf(uid))) {
                this.mWifiSwitchEnabledUidList.add(Integer.valueOf(uid));
                saveWifiSwitchabledAppList();
                return;
            }
            return;
        }
        ArrayList<Integer> arrayList2 = this.mWifiSwitchEnabledUidList;
        if (arrayList2 != null && arrayList2.contains(Integer.valueOf(uid))) {
            ArrayList<Integer> arrayList3 = this.mWifiSwitchEnabledUidList;
            arrayList3.remove(arrayList3.indexOf(Integer.valueOf(uid)));
            saveWifiSwitchabledAppList();
        }
        resetDetectionHistory(uid);
    }

    public void updateSwitchEnabledAppList(ArrayList<Integer> list) {
        if (DBG) {
            Log.d(TAG, "updateSwitchEnabledAppList - " + list);
        }
        if (list != null) {
            Iterator<Integer> it = this.mWifiSwitchEnabledUidList.iterator();
            while (it.hasNext()) {
                int uid = it.next().intValue();
                if (!list.contains(Integer.valueOf(uid))) {
                    if (DBG) {
                        Log.d(TAG, "updateSwitchEnabledAppList - delete " + uid);
                    }
                    resetDetectionHistory(uid);
                    resetSwitchForIndivdiaulAppsDetectionCount(getPackageName(uid));
                }
            }
            Iterator<Integer> it2 = list.iterator();
            while (it2.hasNext()) {
                int uid2 = it2.next().intValue();
                if (!this.mWifiSwitchEnabledUidList.contains(Integer.valueOf(uid2)) && DBG) {
                    Log.d(TAG, "updateSwitchEnabledAppList - insert " + uid2);
                }
            }
            this.mWifiSwitchEnabledUidList = list;
            saveWifiSwitchabledAppList();
            saveWifiApInfoList();
        }
    }

    public void addWifiPackageDetectedCount(int uid) {
        synchronized (this.mWifiPackageInfoLock) {
            if (this.mWifiPackageInfoList != null && this.mWifiPackageInfoList.containsKey(Integer.valueOf(uid))) {
                this.mWifiPackageInfoList.get(Integer.valueOf(uid)).setDetectedCount(this.mWifiPackageInfoList.get(Integer.valueOf(uid)).getDetectedCount() + 1);
                saveWifiPackageInfoList();
            }
        }
    }

    private void resetDetectionHistory(int uid) {
        synchronized (this.mWifiPackageInfoLock) {
            if (this.mWifiPackageInfoList != null && this.mWifiPackageInfoList.containsKey(Integer.valueOf(uid))) {
                this.mWifiPackageInfoList.get(Integer.valueOf(uid)).setDetectedCount(0);
            }
        }
        HashMap<String, WifiApInfo> hashMap = this.mWifiApInfoList;
        if (!(hashMap == null || hashMap.isEmpty())) {
            String packageName = getPackageName(uid);
            for (WifiApInfo apInfo : this.mWifiApInfoList.values()) {
                if (apInfo.getDetectedPackageList().containsKey(packageName)) {
                    apInfo.getDetectedPackageList().remove(packageName);
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isCategoryUpdateable() {
        if (DBG) {
            Log.d(TAG, "isCategoryUpdateable - " + isNetworkConnected() + ", " + getCountryCode());
        }
        return isNetworkConnected() && !"CN".equalsIgnoreCase(getCountryCode());
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) this.mContext.getSystemService("connectivity");
        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }

    private String getCountryCode() {
        try {
            String deviceCountryCode = SemCscFeature.getInstance().getString("CountryISO");
            if (deviceCountryCode != null) {
                return deviceCountryCode;
            }
            return " ";
        } catch (Exception e) {
            return " ";
        }
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n[SWITCH ENABLED PACKAGE INFO]\n");
        ArrayList<Integer> arrayList = this.mWifiSwitchEnabledUidList;
        if (arrayList != null && !arrayList.isEmpty()) {
            int index = 0;
            Iterator<Integer> it = this.mWifiSwitchEnabledUidList.iterator();
            while (it.hasNext()) {
                int uid = it.next().intValue();
                StringBuilder sb2 = new StringBuilder();
                sb2.append("[INDEX] ");
                int index2 = index + 1;
                sb2.append(index);
                sb.append(sb2.toString());
                sb.append(", [UID] " + uid);
                sb.append(", [PACKAGE] " + getPackageName(uid) + "\n");
                index = index2;
            }
        }
        sb.append("\n\n[AP INFO]\n");
        HashMap<String, WifiApInfo> hashMap = this.mWifiApInfoList;
        if (hashMap == null || hashMap.isEmpty()) {
            sb.append("EMTPY\n");
        } else {
            int index3 = 0;
            for (WifiApInfo info : this.mWifiApInfoList.values()) {
                if (info.getSwitchForIndivdiaulAppsDetectionCount() > 0) {
                    StringBuilder sb3 = new StringBuilder();
                    sb3.append("[INDEX] ");
                    int index4 = index3 + 1;
                    sb3.append(index3);
                    sb.append(sb3.toString());
                    sb.append(", [SSID] " + info.getSsid());
                    sb.append(", [ConnectionCount] " + info.getAccumulatedConnectionCount());
                    sb.append(", [ConnectionTime] " + info.getAccumulatedConnectionTime());
                    sb.append(", [DetectionCount] " + info.getSwitchForIndivdiaulAppsDetectionCount() + "\n");
                    HashMap<String, WifiApInfo.DetectedPackageInfo> detectedList = info.getDetectedPackageList();
                    if (detectedList != null && !detectedList.isEmpty()) {
                        for (WifiApInfo.DetectedPackageInfo packageInfo : detectedList.values()) {
                            sb.append("  [DetectedPackage] " + packageInfo.getPackageName());
                            sb.append(", [LastDetectedTime] " + packageInfo.getLastDetectedTime());
                            sb.append(", [DetectedCount] " + packageInfo.getDetectedCount());
                            sb.append(", [PackageNormalOperationTime] " + packageInfo.getPackageNormalOperationTime() + "\n");
                        }
                    }
                    index3 = index4;
                }
            }
        }
        sb.append("\n\n[PACKAGE INFO]\n");
        HashMap<Integer, WifiPackageInfo> hashMap2 = this.mWifiPackageInfoList;
        if (hashMap2 != null && !hashMap2.isEmpty()) {
            synchronized (this.mWifiPackageInfoLock) {
                int index5 = 0;
                int detectionMode = Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_switch_for_individual_apps_detection_mode", 0);
                for (WifiPackageInfo info2 : this.mWifiPackageInfoList.values()) {
                    if ((detectionMode == 0 && info2.isChatApp()) || (detectionMode == 1 && info2.isSwitchable())) {
                        StringBuilder sb4 = new StringBuilder();
                        sb4.append("[INDEX] ");
                        int index6 = index5 + 1;
                        sb4.append(index5);
                        sb.append(sb4.toString());
                        sb.append(", [UID] " + info2.getUid());
                        sb.append(", [PackageName] " + info2.getPackageName());
                        sb.append(", [Switchable] " + info2.isSwitchable());
                        sb.append(", [Category] " + info2.getCategory());
                        sb.append(", [DetectedCount] " + info2.getDetectedCount());
                        sb.append(", [BrowsingApp] " + info2.isBrowsingApp());
                        sb.append(", [ChatApp] " + info2.isChatApp());
                        sb.append(", [GamingApp] " + info2.isGamingApp());
                        sb.append(", [Launchable] " + info2.isLaunchable());
                        sb.append(", [SystemApp] " + info2.isSystemApp());
                        sb.append(", [Voip] " + info2.isVoip());
                        sb.append(", [UsagePattern] " + info2.getUsagePattern() + "\n");
                        index5 = index6;
                    }
                }
            }
        }
        return sb.toString();
    }
}

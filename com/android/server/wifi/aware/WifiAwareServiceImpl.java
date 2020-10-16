package com.android.server.wifi.aware;

import android.app.AppOpsManager;
import android.content.Context;
import android.database.ContentObserver;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.IWifiAwareDiscoverySessionCallback;
import android.net.wifi.aware.IWifiAwareEventCallback;
import android.net.wifi.aware.IWifiAwareMacAddressProvider;
import android.net.wifi.aware.IWifiAwareManager;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import com.android.server.wifi.Clock;
import com.android.server.wifi.FrameworkFacade;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.server.wifi.util.WifiPermissionsWrapper;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class WifiAwareServiceImpl extends IWifiAwareManager.Stub {
    private static final String TAG = "WifiAwareService";
    private static final boolean VDBG = true;
    private AppOpsManager mAppOps;
    private Context mContext;
    boolean mDbg = true;
    private final SparseArray<IBinder.DeathRecipient> mDeathRecipientsByClientId = new SparseArray<>();
    private List<String> mHistoricalDumpLogs = new ArrayList();
    private final Object mLock = new Object();
    private String mLogForSaving;
    private int mNextClientId = 1;
    private WifiAwareShellCommand mShellCommand;
    private WifiAwareStateManager mStateManager;
    private final SparseIntArray mUidByClientId = new SparseIntArray();
    private WifiPermissionsUtil mWifiPermissionsUtil;

    public WifiAwareServiceImpl(Context context) {
        this.mContext = context.getApplicationContext();
        this.mAppOps = (AppOpsManager) context.getSystemService("appops");
    }

    public int getMockableCallingUid() {
        return getCallingUid();
    }

    public void start(HandlerThread handlerThread, final WifiAwareStateManager awareStateManager, WifiAwareShellCommand awareShellCommand, WifiAwareMetrics awareMetrics, WifiPermissionsUtil wifiPermissionsUtil, WifiPermissionsWrapper permissionsWrapper, final FrameworkFacade frameworkFacade, final WifiAwareNativeManager wifiAwareNativeManager, final WifiAwareNativeApi wifiAwareNativeApi, final WifiAwareNativeCallback wifiAwareNativeCallback) {
        Log.i(TAG, "Starting Wi-Fi Aware service");
        this.mWifiPermissionsUtil = wifiPermissionsUtil;
        this.mStateManager = awareStateManager;
        this.mShellCommand = awareShellCommand;
        this.mStateManager.start(this.mContext, handlerThread.getLooper(), awareMetrics, wifiPermissionsUtil, permissionsWrapper, new Clock());
        frameworkFacade.registerContentObserver(this.mContext, Settings.Global.getUriFor("wifi_verbose_logging_enabled"), true, new ContentObserver(new Handler(handlerThread.getLooper())) {
            /* class com.android.server.wifi.aware.WifiAwareServiceImpl.C04941 */

            public void onChange(boolean selfChange) {
                WifiAwareServiceImpl wifiAwareServiceImpl = WifiAwareServiceImpl.this;
                wifiAwareServiceImpl.enableVerboseLogging(frameworkFacade.getIntegerSetting(wifiAwareServiceImpl.mContext, "wifi_verbose_logging_enabled", 0), awareStateManager, wifiAwareNativeManager, wifiAwareNativeApi, wifiAwareNativeCallback);
            }
        });
        enableVerboseLogging(frameworkFacade.getIntegerSetting(this.mContext, "wifi_verbose_logging_enabled", 0), awareStateManager, wifiAwareNativeManager, wifiAwareNativeApi, wifiAwareNativeCallback);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void enableVerboseLogging(int verbose, WifiAwareStateManager awareStateManager, WifiAwareNativeManager wifiAwareNativeManager, WifiAwareNativeApi wifiAwareNativeApi, WifiAwareNativeCallback wifiAwareNativeCallback) {
        if (verbose > 0) {
        }
        this.mDbg = true;
        awareStateManager.mDbg = true;
        if (awareStateManager.mDataPathMgr != null) {
            awareStateManager.mDataPathMgr.mDbg = true;
            WifiInjector.getInstance().getWifiMetrics().getWifiAwareMetrics().mDbg = true;
        }
        wifiAwareNativeCallback.mDbg = true;
        wifiAwareNativeManager.mDbg = true;
        wifiAwareNativeApi.mDbg = true;
    }

    public void startLate() {
        Log.i(TAG, "Late initialization of Wi-Fi Aware service");
        this.mStateManager.startLate();
    }

    public boolean isUsageEnabled() {
        enforceAccessPermission();
        return this.mStateManager.isUsageEnabled();
    }

    public boolean isUsageEnabledForSem(String callingPackage) {
        enforceAccessPermission();
        return this.mStateManager.isUsageEnabledForSem(callingPackage);
    }

    public boolean isAwareEnabled() {
        enforceAccessPermission();
        return this.mStateManager.isAwareEnabled();
    }

    public Characteristics getCharacteristics() {
        enforceAccessPermission();
        if (this.mStateManager.getCapabilities() == null) {
            return null;
        }
        return this.mStateManager.getCapabilities().toPublicCharacteristics();
    }

    public int getCountNdp(boolean max) {
        enforceAccessPermission();
        if (max) {
            return this.mStateManager.getCountMaxNdp();
        }
        return this.mStateManager.getCountNdp();
    }

    public void setClusterMergingEnabled(boolean enable) {
        enforceAccessPermission();
        this.mStateManager.setClusterMergingEnabled(enable);
    }

    /* JADX WARNING: Code restructure failed: missing block: B:31:0x00c2, code lost:
        r0 = th;
     */
    public void connect(final IBinder binder, String callingPackage, IWifiAwareEventCallback callback, ConfigRequest configRequest, boolean notifyOnIdentityChanged) {
        ConfigRequest configRequest2;
        final int clientId;
        enforceAccessPermission();
        enforceChangePermission();
        int uid = getMockableCallingUid();
        this.mAppOps.checkPackage(uid, callingPackage);
        if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        } else if (binder != null) {
            if (notifyOnIdentityChanged) {
                enforceLocationPermission(callingPackage, getMockableCallingUid());
            }
            if (configRequest != null) {
                enforceNetworkStackPermission();
                configRequest2 = configRequest;
            } else {
                configRequest2 = new ConfigRequest.Builder().build();
            }
            configRequest2.validate();
            int pid = getCallingPid();
            synchronized (this.mLock) {
                clientId = this.mNextClientId;
                this.mNextClientId = clientId + 1;
            }
            this.mLogForSaving = "connect: uid=" + uid + ", clientId=" + clientId + ", configRequest" + configRequest2 + ", notifyOnIdentityChanged=" + notifyOnIdentityChanged + ", callingPackage=" + callingPackage;
            if (this.mDbg) {
                Log.v(TAG, this.mLogForSaving);
            }
            saveLog(this.mLogForSaving);
            IBinder.DeathRecipient dr = new IBinder.DeathRecipient() {
                /* class com.android.server.wifi.aware.WifiAwareServiceImpl.C04952 */

                public void binderDied() {
                    WifiAwareServiceImpl wifiAwareServiceImpl = WifiAwareServiceImpl.this;
                    wifiAwareServiceImpl.mLogForSaving = "binderDied: clientId=" + clientId;
                    if (WifiAwareServiceImpl.this.mDbg) {
                        Log.v(WifiAwareServiceImpl.TAG, WifiAwareServiceImpl.this.mLogForSaving);
                    }
                    WifiAwareServiceImpl wifiAwareServiceImpl2 = WifiAwareServiceImpl.this;
                    wifiAwareServiceImpl2.saveLog(wifiAwareServiceImpl2.mLogForSaving);
                    binder.unlinkToDeath(this, 0);
                    synchronized (WifiAwareServiceImpl.this.mLock) {
                        WifiAwareServiceImpl.this.mDeathRecipientsByClientId.delete(clientId);
                        WifiAwareServiceImpl.this.mUidByClientId.delete(clientId);
                    }
                    WifiAwareServiceImpl.this.mStateManager.disconnect(clientId);
                }
            };
            try {
                binder.linkToDeath(dr, 0);
                synchronized (this.mLock) {
                    this.mDeathRecipientsByClientId.put(clientId, dr);
                    this.mUidByClientId.put(clientId, uid);
                }
                this.mStateManager.connect(clientId, uid, pid, callingPackage, callback, configRequest2, notifyOnIdentityChanged);
                return;
            } catch (RemoteException e) {
                Log.e(TAG, "Error on linkToDeath - " + e);
                try {
                    callback.onConnectFail(1);
                    return;
                } catch (RemoteException e2) {
                    Log.e(TAG, "Error on onConnectFail()");
                    return;
                }
            }
        } else {
            throw new IllegalArgumentException("Binder must not be null");
        }
        while (true) {
        }
    }

    public void disconnect(int clientId, IBinder binder) {
        enforceAccessPermission();
        enforceChangePermission();
        int uid = getMockableCallingUid();
        enforceClientValidity(uid, clientId);
        this.mLogForSaving = "disconnect: uid=" + uid + ", clientId=" + clientId;
        if (this.mDbg) {
            Log.v(TAG, this.mLogForSaving);
        }
        saveLog(this.mLogForSaving);
        if (binder != null) {
            synchronized (this.mLock) {
                IBinder.DeathRecipient dr = this.mDeathRecipientsByClientId.get(clientId);
                if (dr != null) {
                    binder.unlinkToDeath(dr, 0);
                    this.mDeathRecipientsByClientId.delete(clientId);
                }
                this.mUidByClientId.delete(clientId);
            }
            this.mStateManager.disconnect(clientId);
            return;
        }
        throw new IllegalArgumentException("Binder must not be null");
    }

    public void terminateSession(int clientId, int sessionId) {
        enforceAccessPermission();
        enforceChangePermission();
        int uid = getMockableCallingUid();
        enforceClientValidity(uid, clientId);
        this.mLogForSaving = "terminateSession: sessionId=" + sessionId + ", uid=" + uid + ", clientId=" + clientId;
        Log.v(TAG, this.mLogForSaving);
        saveLog(this.mLogForSaving);
        this.mStateManager.terminateSession(clientId, sessionId);
    }

    public void publish(String callingPackage, int clientId, PublishConfig publishConfig, IWifiAwareDiscoverySessionCallback callback) {
        enforceAccessPermission();
        enforceChangePermission();
        int uid = getMockableCallingUid();
        this.mAppOps.checkPackage(uid, callingPackage);
        enforceLocationPermission(callingPackage, getMockableCallingUid());
        if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        } else if (publishConfig != null) {
            publishConfig.assertValid(this.mStateManager.getCharacteristics(), this.mContext.getPackageManager().hasSystemFeature("android.hardware.wifi.rtt"));
            enforceClientValidity(uid, clientId);
            this.mLogForSaving = "publish: uid=" + uid + ", clientId=" + clientId + ", publishConfig=" + publishConfig + ", callback=" + callback + ", callingPackage=" + callingPackage;
            Log.v(TAG, this.mLogForSaving);
            saveLog(this.mLogForSaving);
            this.mStateManager.publish(clientId, publishConfig, callback);
        } else {
            throw new IllegalArgumentException("PublishConfig must not be null");
        }
    }

    public void updatePublish(int clientId, int sessionId, PublishConfig publishConfig) {
        enforceAccessPermission();
        enforceChangePermission();
        if (publishConfig != null) {
            publishConfig.assertValid(this.mStateManager.getCharacteristics(), this.mContext.getPackageManager().hasSystemFeature("android.hardware.wifi.rtt"));
            int uid = getMockableCallingUid();
            enforceClientValidity(uid, clientId);
            this.mLogForSaving = "updatePublish: uid=" + uid + ", clientId=" + clientId + ", sessionId=" + sessionId + ", config=" + publishConfig;
            Log.v(TAG, this.mLogForSaving);
            saveLog(this.mLogForSaving);
            this.mStateManager.updatePublish(clientId, sessionId, publishConfig);
            return;
        }
        throw new IllegalArgumentException("PublishConfig must not be null");
    }

    public void subscribe(String callingPackage, int clientId, SubscribeConfig subscribeConfig, IWifiAwareDiscoverySessionCallback callback) {
        enforceAccessPermission();
        enforceChangePermission();
        int uid = getMockableCallingUid();
        this.mAppOps.checkPackage(uid, callingPackage);
        enforceLocationPermission(callingPackage, getMockableCallingUid());
        if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        } else if (subscribeConfig != null) {
            subscribeConfig.assertValid(this.mStateManager.getCharacteristics(), this.mContext.getPackageManager().hasSystemFeature("android.hardware.wifi.rtt"));
            enforceClientValidity(uid, clientId);
            this.mLogForSaving = "subscribe: uid=" + uid + ", clientId=" + clientId + ", config=" + subscribeConfig + ", callback=" + callback + ", callingPackage=" + callingPackage;
            Log.v(TAG, this.mLogForSaving);
            saveLog(this.mLogForSaving);
            this.mStateManager.subscribe(clientId, subscribeConfig, callback);
        } else {
            throw new IllegalArgumentException("SubscribeConfig must not be null");
        }
    }

    public void updateSubscribe(int clientId, int sessionId, SubscribeConfig subscribeConfig) {
        enforceAccessPermission();
        enforceChangePermission();
        if (subscribeConfig != null) {
            subscribeConfig.assertValid(this.mStateManager.getCharacteristics(), this.mContext.getPackageManager().hasSystemFeature("android.hardware.wifi.rtt"));
            int uid = getMockableCallingUid();
            enforceClientValidity(uid, clientId);
            this.mLogForSaving = "updateSubscribe: uid=" + uid + ", clientId=" + clientId + ", sessionId=" + sessionId + ", config=" + subscribeConfig;
            Log.v(TAG, this.mLogForSaving);
            saveLog(this.mLogForSaving);
            this.mStateManager.updateSubscribe(clientId, sessionId, subscribeConfig);
            return;
        }
        throw new IllegalArgumentException("SubscribeConfig must not be null");
    }

    public void sendMessage(int clientId, int sessionId, int peerId, byte[] message, int messageId, int retryCount) {
        enforceAccessPermission();
        enforceChangePermission();
        if (retryCount != 0) {
            enforceNetworkStackPermission();
        }
        if (message != null && message.length > this.mStateManager.getCharacteristics().getMaxServiceSpecificInfoLength()) {
            throw new IllegalArgumentException("Message length longer than supported by device characteristics");
        } else if (retryCount < 0 || retryCount > DiscoverySession.getMaxSendRetryCount()) {
            throw new IllegalArgumentException("Invalid 'retryCount' must be non-negative and <= DiscoverySession.MAX_SEND_RETRY_COUNT");
        } else {
            int uid = getMockableCallingUid();
            enforceClientValidity(uid, clientId);
            this.mLogForSaving = "sendMessage: sessionId=" + sessionId + ", uid=" + uid + ", clientId=" + clientId + ", peerId=" + peerId + ", messageId=" + messageId + ", retryCount=" + retryCount;
            Log.v(TAG, this.mLogForSaving);
            saveLog(this.mLogForSaving);
            this.mStateManager.sendMessage(clientId, sessionId, peerId, message, messageId, retryCount);
        }
    }

    public void requestMacAddresses(int uid, List peerIds, IWifiAwareMacAddressProvider callback) {
        enforceNetworkStackPermission();
        this.mStateManager.requestMacAddresses(uid, peerIds, callback);
    }

    /* JADX DEBUG: Multi-variable search result rejected for r8v0, resolved type: com.android.server.wifi.aware.WifiAwareServiceImpl */
    /* JADX WARN: Multi-variable type inference failed */
    public void onShellCommand(FileDescriptor in, FileDescriptor out, FileDescriptor err, String[] args, ShellCallback callback, ResultReceiver resultReceiver) {
        this.mShellCommand.exec(this, in, out, err, args, callback, resultReceiver);
    }

    /* access modifiers changed from: protected */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (this.mContext.checkCallingOrSelfPermission("android.permission.DUMP") != 0) {
            pw.println("Permission Denial: can't dump WifiAwareService from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return;
        }
        pw.println("Wi-Fi Aware Service");
        synchronized (this.mLock) {
            pw.println("  mNextClientId: " + this.mNextClientId);
            pw.println("  mDeathRecipientsByClientId: " + this.mDeathRecipientsByClientId);
            pw.println("  mUidByClientId: " + this.mUidByClientId);
        }
        pw.println("Wi-Fi Aware api call history:");
        pw.println(this.mHistoricalDumpLogs.toString());
        this.mStateManager.dump(fd, pw, args);
    }

    private void enforceClientValidity(int uid, int clientId) {
        synchronized (this.mLock) {
            int uidIndex = this.mUidByClientId.indexOfKey(clientId);
            if (uidIndex < 0 || this.mUidByClientId.valueAt(uidIndex) != uid) {
                throw new SecurityException("Attempting to use invalid uid+clientId mapping: uid=" + uid + ", clientId=" + clientId);
            }
        }
    }

    private void enforceAccessPermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.ACCESS_WIFI_STATE", TAG);
    }

    private void enforceChangePermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.CHANGE_WIFI_STATE", TAG);
    }

    private void enforceLocationPermission(String callingPackage, int uid) {
        this.mWifiPermissionsUtil.enforceLocationPermission(callingPackage, uid);
    }

    private void enforceNetworkStackPermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.NETWORK_STACK", TAG);
    }

    private void addHistoricalDumpLog(String log) {
        if (this.mHistoricalDumpLogs.size() > 35) {
            this.mHistoricalDumpLogs.remove(0);
        }
        this.mHistoricalDumpLogs.add(log);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void saveLog(String log) {
        String currentTimeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
        addHistoricalDumpLog(currentTimeStamp + " WifiAwareManager." + log + "\n");
    }
}

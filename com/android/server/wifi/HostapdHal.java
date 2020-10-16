package com.android.server.wifi;

import android.content.Context;
import android.hardware.wifi.hostapd.V1_0.HostapdStatus;
import android.hardware.wifi.hostapd.V1_0.IHostapd;
import android.hardware.wifi.hostapd.V1_1.IHostapd;
import android.hardware.wifi.hostapd.V1_1.IHostapdCallback;
import android.hidl.manager.V1_0.IServiceManager;
import android.hidl.manager.V1_0.IServiceNotification;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.IHwBinder;
import android.os.IHwInterface;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.HostapdHal;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.util.NativeUtil;
import com.samsung.android.server.wifi.softap.SemWifiApMonitor;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import javax.annotation.concurrent.ThreadSafe;
import vendor.samsung.hardware.wifi.hostapd.V2_0.ISehHostapdCallback;
import vendor.samsung.hardware.wifi.hostapd.V2_1.ISehHostapd;

@ThreadSafe
public class HostapdHal {
    @VisibleForTesting
    public static final String HAL_INSTANCE_NAME = "default";
    private static final String TAG = "HostapdHal";
    private final int SamsungHotspotVSIE = 128;
    private final String SamsungOUI = "001632";
    private final List<IHostapd.AcsChannelRange> mAcsChannelRanges;
    private Context mContext;
    private WifiNative.HostapdDeathEventHandler mDeathEventHandler;
    private long mDeathRecipientCookie = 0;
    private final boolean mEnableAcs;
    private final boolean mEnableIeee80211AC;
    private final Handler mEventHandler;
    private HostapdDeathRecipient mHostapdDeathRecipient;
    private android.hardware.wifi.hostapd.V1_0.IHostapd mIHostapd;
    private ISehHostapd mISehHostapd;
    private IServiceManager mIServiceManager = null;
    private final Object mLock = new Object();
    private List<String> mMHSDumpLogs = new ArrayList();
    private SemWifiApMonitor mSemWifiApMonitor = null;
    private ServiceManagerDeathRecipient mServiceManagerDeathRecipient;
    private final IServiceNotification mServiceNotificationCallback = new IServiceNotification.Stub() {
        /* class com.android.server.wifi.HostapdHal.C03911 */

        public void onRegistration(String fqName, String name, boolean preexisting) {
            synchronized (HostapdHal.this.mLock) {
                if (HostapdHal.this.mVerboseLoggingEnabled) {
                    Log.i(HostapdHal.TAG, "IServiceNotification.onRegistration for: " + fqName + ", " + name + " preexisting=" + preexisting);
                }
                HostapdHal hostapdHal = HostapdHal.this;
                hostapdHal.addMHSDumpLog("HostapdHal  IServiceNotification.onRegistration for: " + fqName + ", " + name + " preexisting=" + preexisting);
                if (!HostapdHal.this.initHostapdService()) {
                    Log.e(HostapdHal.TAG, "initalizing IHostapd failed.");
                    HostapdHal.this.addMHSDumpLog("HostapdHal  initalizing IHostapd failed.");
                    HostapdHal.this.hostapdServiceDiedHandler(HostapdHal.this.mDeathRecipientCookie);
                } else {
                    HostapdHal.this.addMHSDumpLog("HostapdHal  Completed initialization of IHostapd.");
                    Log.i(HostapdHal.TAG, "Completed initialization of IHostapd.");
                }
            }
        }
    };
    private HashMap<String, WifiNative.SoftApListener> mSoftApListeners = new HashMap<>();
    private boolean mVerboseLoggingEnabled = false;
    private WifiManager mWifiManager;

    public void addMHSDumpLog(String log) {
        StringBuffer value = new StringBuffer();
        value.append(new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Long.valueOf(System.currentTimeMillis())) + " " + log + "\n");
        if (this.mMHSDumpLogs.size() > 100) {
            this.mMHSDumpLogs.remove(0);
        }
        this.mMHSDumpLogs.add(value.toString());
    }

    /* access modifiers changed from: private */
    public class ServiceManagerDeathRecipient implements IHwBinder.DeathRecipient {
        private ServiceManagerDeathRecipient() {
        }

        public void serviceDied(long cookie) {
            HostapdHal.this.mEventHandler.post(new Runnable(cookie) {
                /* class com.android.server.wifi.RunnableC0330x5db4cf5f */
                private final /* synthetic */ long f$1;

                {
                    this.f$1 = r2;
                }

                public final void run() {
                    HostapdHal.ServiceManagerDeathRecipient.this.lambda$serviceDied$0$HostapdHal$ServiceManagerDeathRecipient(this.f$1);
                }
            });
        }

        public /* synthetic */ void lambda$serviceDied$0$HostapdHal$ServiceManagerDeathRecipient(long cookie) {
            synchronized (HostapdHal.this.mLock) {
                Log.w(HostapdHal.TAG, "IServiceManager died: cookie=" + cookie);
                HostapdHal.this.hostapdServiceDiedHandler(HostapdHal.this.mDeathRecipientCookie);
                HostapdHal.this.mIServiceManager = null;
            }
        }
    }

    /* access modifiers changed from: private */
    public class HostapdDeathRecipient implements IHwBinder.DeathRecipient {
        private HostapdDeathRecipient() {
        }

        public void serviceDied(long cookie) {
            HostapdHal.this.mEventHandler.post(new Runnable(cookie) {
                /* class com.android.server.wifi.RunnableC0329x1d8f8432 */
                private final /* synthetic */ long f$1;

                {
                    this.f$1 = r2;
                }

                public final void run() {
                    HostapdHal.HostapdDeathRecipient.this.lambda$serviceDied$0$HostapdHal$HostapdDeathRecipient(this.f$1);
                }
            });
        }

        public /* synthetic */ void lambda$serviceDied$0$HostapdHal$HostapdDeathRecipient(long cookie) {
            synchronized (HostapdHal.this.mLock) {
                Log.w(HostapdHal.TAG, "IHostapd/IHostapd died: cookie=" + cookie);
                HostapdHal.this.hostapdServiceDiedHandler(cookie);
            }
        }
    }

    public HostapdHal(Context context, Looper looper) {
        this.mEventHandler = new Handler(looper);
        this.mContext = context;
        this.mEnableAcs = false;
        if ("in_house".equals("jdm")) {
            this.mEnableIeee80211AC = true;
        } else {
            this.mEnableIeee80211AC = false;
        }
        this.mAcsChannelRanges = toAcsChannelRanges(context.getResources().getString(17040007));
        this.mServiceManagerDeathRecipient = new ServiceManagerDeathRecipient();
        this.mHostapdDeathRecipient = new HostapdDeathRecipient();
        this.mSemWifiApMonitor = WifiInjector.getInstance().getWifiApMonitor();
    }

    /* access modifiers changed from: package-private */
    public void enableVerboseLogging(boolean enable) {
        synchronized (this.mLock) {
            this.mVerboseLoggingEnabled = enable;
        }
    }

    private boolean isV1_1() {
        synchronized (this.mLock) {
            boolean z = false;
            if (this.mIServiceManager == null) {
                Log.e(TAG, "isV1_1: called but mServiceManager is null!?");
                return false;
            }
            try {
                if (this.mIServiceManager.getTransport(IHostapd.kInterfaceName, "default") != 0) {
                    z = true;
                }
                return z;
            } catch (RemoteException e) {
                Log.e(TAG, "Exception while operating on IServiceManager: " + e);
                handleRemoteException(e, "getTransport");
                return false;
            }
        }
    }

    private boolean isSamsungV2_1() {
        synchronized (this.mLock) {
            boolean z = false;
            if (this.mIServiceManager == null) {
                Log.e(TAG, "isV1_1: called but mServiceManager is null!?");
                return false;
            }
            try {
                if (this.mIServiceManager.getTransport(ISehHostapd.kInterfaceName, "default") != 0) {
                    z = true;
                }
                return z;
            } catch (RemoteException e) {
                Log.e(TAG, "Exception while operating on IServiceManager: " + e);
                handleRemoteException(e, "getTransport");
                return false;
            }
        }
    }

    private boolean linkToServiceManagerDeath() {
        synchronized (this.mLock) {
            if (this.mIServiceManager == null) {
                return false;
            }
            try {
                if (this.mIServiceManager.linkToDeath(this.mServiceManagerDeathRecipient, 0)) {
                    return true;
                }
                Log.wtf(TAG, "Error on linkToDeath on IServiceManager");
                hostapdServiceDiedHandler(this.mDeathRecipientCookie);
                this.mIServiceManager = null;
                return false;
            } catch (RemoteException e) {
                Log.e(TAG, "IServiceManager.linkToDeath exception", e);
                this.mIServiceManager = null;
                return false;
            }
        }
    }

    public boolean initialize() {
        synchronized (this.mLock) {
            if (this.mVerboseLoggingEnabled) {
                Log.i(TAG, "Registering IHostapd service ready callback.");
            }
            this.mIHostapd = null;
            if (this.mIServiceManager != null) {
                return true;
            }
            try {
                this.mIServiceManager = getServiceManagerMockable();
                if (this.mIServiceManager == null) {
                    Log.e(TAG, "Failed to get HIDL Service Manager");
                    return false;
                } else if (!linkToServiceManagerDeath()) {
                    return false;
                } else {
                    if (this.mIServiceManager.registerForNotifications(android.hardware.wifi.hostapd.V1_0.IHostapd.kInterfaceName, "", this.mServiceNotificationCallback)) {
                        return true;
                    }
                    Log.e(TAG, "Failed to register for notifications to android.hardware.wifi.hostapd@1.0::IHostapd");
                    this.mIServiceManager = null;
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Exception while trying to register a listener for IHostapd service: " + e);
                hostapdServiceDiedHandler(this.mDeathRecipientCookie);
                this.mIServiceManager = null;
                return false;
            }
        }
    }

    private boolean linkToHostapdDeath() {
        synchronized (this.mLock) {
            if (this.mIHostapd == null) {
                return false;
            }
            try {
                android.hardware.wifi.hostapd.V1_0.IHostapd iHostapd = this.mIHostapd;
                HostapdDeathRecipient hostapdDeathRecipient = this.mHostapdDeathRecipient;
                long j = this.mDeathRecipientCookie + 1;
                this.mDeathRecipientCookie = j;
                if (iHostapd.linkToDeath(hostapdDeathRecipient, j)) {
                    return true;
                }
                Log.wtf(TAG, "Error on linkToDeath on IHostapd");
                hostapdServiceDiedHandler(this.mDeathRecipientCookie);
                return false;
            } catch (RemoteException e) {
                Log.e(TAG, "IHostapd.linkToDeath exception", e);
                return false;
            }
        }
    }

    private boolean registerCallback(IHostapdCallback callback) {
        synchronized (this.mLock) {
            try {
                IHostapd iHostapdV1_1 = getHostapdMockableV1_1();
                if (iHostapdV1_1 == null) {
                    return false;
                }
                return checkStatusAndLogFailure(iHostapdV1_1.registerCallback(callback), "registerCallback_1_1");
            } catch (RemoteException e) {
                handleRemoteException(e, "registerCallback_1_1");
                return false;
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean initHostapdService() {
        synchronized (this.mLock) {
            try {
                this.mIHostapd = getHostapdMockable();
                if (this.mIHostapd == null) {
                    Log.e(TAG, "Got null IHostapd service. Stopping hostapd HIDL startup");
                    return false;
                } else if (!linkToHostapdDeath()) {
                    this.mIHostapd = null;
                    return false;
                } else {
                    try {
                        addMHSDumpLog("HostapdHal  getting mISehHostapd service ");
                        this.mISehHostapd = getSehHostapdMockable();
                        if (this.mISehHostapd == null) {
                            addMHSDumpLog("HostapdHal  Got null mISehHostapd service. Stopping hostapd HIDL startup");
                            Log.e(TAG, "Got null mISehHostapd service. Stopping hostapd HIDL startup");
                            this.mIHostapd = null;
                            return false;
                        }
                        Log.i(TAG, "IsehHostapd. Initialization incomplete.");
                        return true;
                    } catch (RemoteException e) {
                        Log.e(TAG, "mISehHostapd.getService exception: " + e);
                        return false;
                    } catch (NoSuchElementException e2) {
                        Log.e(TAG, "mISehHostapd.getService exception: " + e2);
                        return false;
                    }
                }
            } catch (RemoteException e3) {
                Log.e(TAG, "IHostapd.getService exception: " + e3);
                return false;
            } catch (NoSuchElementException e4) {
                Log.e(TAG, "IHostapd.getService exception: " + e4);
                return false;
            }
        }
    }

    public boolean addAccessPoint(String ifaceName, WifiConfiguration config, WifiNative.SoftApListener listener) {
        String vendorIE;
        int i;
        synchronized (this.mLock) {
            try {
                IHostapd.IfaceParams ifaceParams = new IHostapd.IfaceParams();
                File file = new File("/data/misc/wifi_hostapd/hostapd.accept");
                ISehHostapd.SehParams mSehParams = new ISehHostapd.SehParams();
                boolean isGuestMode = Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_ap_multipassword_enabled", 0) == 1;
                if (isGuestMode) {
                    mSehParams.guestPskPassphrase = config.guestPreSharedKey;
                }
                if (Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_ap_11ax_mode_checked", 0) == 1) {
                    mSehParams.enable11ax = true;
                }
                mSehParams.v2_0.apIsolate = config.apIsolate == 1;
                mSehParams.v2_0.maxNumSta = config.maxclient;
                Log.d(TAG, "config.maxclient" + config.maxclient);
                if (config.vendorIE == 0) {
                    vendorIE = "DD05001632" + Integer.toHexString(128) + "00";
                } else if (config.vendorIE <= 0 || config.vendorIE >= 255) {
                    vendorIE = "";
                } else {
                    vendorIE = "DD05001632" + Integer.toHexString(config.vendorIE) + "00";
                }
                if (!isShipBinary()) {
                    Log.i(TAG, "Add Vendor specific IE DD040000F0FE");
                    vendorIE = vendorIE + "DD040000F0FE";
                }
                String vendorIE2 = vendorIE + "DD080050F21102000000";
                mSehParams.v2_0.vendorIe = vendorIE2;
                mSehParams.v2_0.pmf = config.requirePMF;
                mSehParams.v2_0.macAddressAcl = config.macaddrAcl;
                Log.w(TAG, "isGuestMode:" + isGuestMode + ":vendorIE = " + vendorIE2 + " apIsolate " + mSehParams.v2_0.apIsolate + " pmf " + mSehParams.v2_0.pmf + " macAddressAcl  " + mSehParams.v2_0.macAddressAcl + " maxNumSta " + mSehParams.v2_0.maxNumSta + ",hostapd.accept exist:" + file.exists());
                ifaceParams.ifaceName = ifaceName;
                ifaceParams.hwModeParams.enable80211N = true;
                ifaceParams.hwModeParams.enable80211AC = this.mEnableIeee80211AC;
                try {
                    ifaceParams.channelParams.band = getBand(config);
                    if (this.mEnableAcs) {
                        ifaceParams.channelParams.enableAcs = true;
                        ifaceParams.channelParams.acsShouldExcludeDfs = true;
                    } else {
                        if (ifaceParams.channelParams.band == 2) {
                            Log.d(TAG, "ACS is not supported on this device, using 2.4 GHz band.");
                            ifaceParams.channelParams.band = 0;
                        }
                        ifaceParams.channelParams.enableAcs = false;
                        ifaceParams.channelParams.channel = config.apChannel;
                    }
                    IHostapd.NetworkParams nwParams = new IHostapd.NetworkParams();
                    nwParams.ssid.addAll(NativeUtil.stringToByteArrayList(config.SSID));
                    nwParams.isHidden = config.hiddenSSID;
                    mSehParams.encryptionType = getEncryptionType(config);
                    String str = SystemProperties.get("mhs.wpa");
                    if (str != null && !str.equals("")) {
                        int num = Integer.parseInt(str);
                        Log.d(TAG, "debug WPA:" + num);
                        if (num == 0) {
                            mSehParams.encryptionType = 0;
                        }
                        if (num == 1) {
                            i = 2;
                            mSehParams.encryptionType = 2;
                        } else {
                            i = 2;
                        }
                        if (num == i) {
                            mSehParams.encryptionType = 4;
                        }
                        if (num == 3) {
                            mSehParams.encryptionType = 3;
                        }
                    }
                    nwParams.pskPassphrase = config.preSharedKey != null ? config.preSharedKey : "";
                    if (!checkHostapdAndLogFailure("addAccessPoint")) {
                        return false;
                    }
                    HostapdStatus status = null;
                    try {
                        if (file.exists()) {
                            readWhiteListFileToSendHostapd();
                        }
                        if (isV1_1()) {
                            IHostapd.IfaceParams ifaceParams1_1 = new IHostapd.IfaceParams();
                            ifaceParams1_1.V1_0 = ifaceParams;
                            if (this.mEnableAcs) {
                                ifaceParams1_1.channelParams.acsChannelRanges.addAll(this.mAcsChannelRanges);
                            }
                            if (isSamsungV2_1()) {
                                status = this.mISehHostapd.sehAddAccessPoint_2_1(ifaceParams1_1, nwParams, mSehParams);
                                Log.d(TAG, "sehAddAccessPoint2_1  with code=" + status.code);
                                addMHSDumpLog("HostapdHal  sehAddAccessPoint2_1  with code=" + status.code);
                            } else {
                                status = this.mISehHostapd.sehAddAccessPoint(ifaceParams1_1, nwParams, mSehParams.v2_0);
                                Log.d(TAG, "sehAddAccessPoint  with code=" + status.code);
                                addMHSDumpLog("HostapdHal  sehAddAccessPoint  with code=" + status.code);
                            }
                            if (status.code == 4) {
                                this.mIHostapd.removeAccessPoint(ifaceName);
                                if (isSamsungV2_1()) {
                                    HostapdStatus status2 = this.mISehHostapd.sehAddAccessPoint_2_1(ifaceParams1_1, nwParams, mSehParams);
                                    Log.d(TAG, "sehAddAccessPoint2_1  with code=" + status2.code);
                                    addMHSDumpLog("HostapdHal  sehAddAccessPoint2_1  with code=" + status2.code);
                                    status = status2;
                                } else {
                                    HostapdStatus status3 = this.mISehHostapd.sehAddAccessPoint(ifaceParams1_1, nwParams, mSehParams.v2_0);
                                    Log.d(TAG, "sehAddAccessPoint  with code=" + status3.code);
                                    addMHSDumpLog("HostapdHal  sehAddAccessPoint  with code=" + status3.code);
                                    status = status3;
                                }
                            }
                            if (!sehRegisterCallback(new SehHostapdCallback(ifaceName))) {
                                Log.e(TAG, "Callback failed. Initialization sehRegisterCallback.");
                                addMHSDumpLog("HostapdHal  Callback failed. Initialization sehRegisterCallback.");
                                return false;
                            }
                        }
                        if (!checkStatusAndLogFailure(status, "addAccessPoint")) {
                            return false;
                        }
                        try {
                            this.mSoftApListeners.put(ifaceName, listener);
                            return true;
                        } catch (NullPointerException e) {
                            e = e;
                            Log.e(TAG, "IHostapd.NullPointerExceptionaddAccessPoint failed with exception", e);
                            return false;
                        } catch (RemoteException e2) {
                            e = e2;
                            handleRemoteException(e, "addAccessPoint");
                            return false;
                        } catch (Throwable th) {
                            th = th;
                            throw th;
                        }
                    } catch (NullPointerException e3) {
                        e = e3;
                        Log.e(TAG, "IHostapd.NullPointerExceptionaddAccessPoint failed with exception", e);
                        return false;
                    } catch (RemoteException e4) {
                        e = e4;
                        handleRemoteException(e, "addAccessPoint");
                        return false;
                    }
                } catch (IllegalArgumentException e5) {
                    Log.e(TAG, "Unrecognized apBand " + config.apBand);
                    return false;
                }
            } catch (Throwable th2) {
                th = th2;
                throw th;
            }
        }
    }

    public boolean removeAccessPoint(String ifaceName) {
        synchronized (this.mLock) {
            if (!checkHostapdAndLogFailure("removeAccessPoint")) {
                return false;
            }
            try {
                if (!checkStatusAndLogFailure(this.mIHostapd.removeAccessPoint(ifaceName), "removeAccessPoint")) {
                    return false;
                }
                this.mSoftApListeners.remove(ifaceName);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, "removeAccessPoint");
                return false;
            }
        }
    }

    public boolean registerDeathHandler(WifiNative.HostapdDeathEventHandler handler) {
        if (this.mDeathEventHandler != null) {
            Log.e(TAG, "Death handler already present");
        }
        this.mDeathEventHandler = handler;
        return true;
    }

    public boolean deregisterDeathHandler() {
        if (this.mDeathEventHandler == null) {
            Log.e(TAG, "No Death handler present");
        }
        this.mDeathEventHandler = null;
        return true;
    }

    private void clearState() {
        synchronized (this.mLock) {
            this.mIHostapd = null;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void hostapdServiceDiedHandler(long cookie) {
        synchronized (this.mLock) {
            if (this.mDeathRecipientCookie != cookie) {
                Log.i(TAG, "Ignoring stale death recipient notification");
                return;
            }
            clearState();
            if (this.mDeathEventHandler != null) {
                this.mDeathEventHandler.onDeath();
            }
        }
    }

    public boolean isInitializationStarted() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mIServiceManager != null;
        }
        return z;
    }

    public boolean isInitializationComplete() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mIHostapd != null;
        }
        return z;
    }

    public boolean startDaemon() {
        synchronized (this.mLock) {
            try {
                addMHSDumpLog("HostapdHal  startDaemon");
                getHostapdMockable();
            } catch (RemoteException e) {
                Log.e(TAG, "Exception while trying to start hostapd: " + e);
                hostapdServiceDiedHandler(this.mDeathRecipientCookie);
                return false;
            } catch (NoSuchElementException e2) {
                Log.d(TAG, "Successfully triggered start of hostapd using HIDL");
            }
        }
        return true;
    }

    public void terminate() {
        synchronized (this.mLock) {
            if (checkHostapdAndLogFailure("terminate")) {
                try {
                    this.mIHostapd.terminate();
                } catch (RemoteException e) {
                    handleRemoteException(e, "terminate");
                }
            }
        }
    }

    /* access modifiers changed from: protected */
    @VisibleForTesting
    public IServiceManager getServiceManagerMockable() throws RemoteException {
        IServiceManager service;
        synchronized (this.mLock) {
            service = IServiceManager.getService();
        }
        return service;
    }

    /* access modifiers changed from: protected */
    @VisibleForTesting
    public android.hardware.wifi.hostapd.V1_0.IHostapd getHostapdMockable() throws RemoteException {
        android.hardware.wifi.hostapd.V1_0.IHostapd service;
        synchronized (this.mLock) {
            service = android.hardware.wifi.hostapd.V1_0.IHostapd.getService();
        }
        return service;
    }

    /* access modifiers changed from: protected */
    @VisibleForTesting
    public android.hardware.wifi.hostapd.V1_1.IHostapd getHostapdMockableV1_1() throws RemoteException {
        android.hardware.wifi.hostapd.V1_1.IHostapd castFrom;
        synchronized (this.mLock) {
            try {
                castFrom = android.hardware.wifi.hostapd.V1_1.IHostapd.castFrom((IHwInterface) this.mIHostapd);
            } catch (NoSuchElementException e) {
                Log.e(TAG, "Failed to get IHostapd", e);
                return null;
            } catch (Throwable th) {
                throw th;
            }
        }
        return castFrom;
    }

    /* access modifiers changed from: protected */
    @VisibleForTesting
    public ISehHostapd getSehHostapdMockable() throws RemoteException {
        ISehHostapd castFrom;
        synchronized (this.mLock) {
            try {
                castFrom = ISehHostapd.castFrom((IHwInterface) getHostapdMockableV1_1());
            } catch (NoSuchElementException e) {
                Log.e(TAG, "Failed to get ISehHostapd", e);
                return null;
            } catch (Throwable th) {
                throw th;
            }
        }
        return castFrom;
    }

    private static int getEncryptionType(WifiConfiguration localConfig) {
        int authType = localConfig.getAuthType();
        if (authType == 0) {
            return 0;
        }
        if (authType == 1) {
            return 1;
        }
        if (authType == 4) {
            return 2;
        }
        if (authType == 25) {
            return 4;
        }
        if (authType != 26) {
            return 0;
        }
        return 3;
    }

    private static int getBand(WifiConfiguration localConfig) {
        int bandType;
        int i = localConfig.apBand;
        if (i == -1) {
            bandType = 2;
        } else if (i == 0) {
            bandType = 0;
        } else if (i == 1) {
            bandType = 1;
        } else {
            throw new IllegalArgumentException();
        }
        if (localConfig.apChannel == 149) {
            return 1;
        }
        return bandType;
    }

    private List<IHostapd.AcsChannelRange> toAcsChannelRanges(String channelListStr) {
        ArrayList<IHostapd.AcsChannelRange> acsChannelRanges = new ArrayList<>();
        String[] channelRanges = channelListStr.split(",");
        for (String channelRange : channelRanges) {
            IHostapd.AcsChannelRange acsChannelRange = new IHostapd.AcsChannelRange();
            try {
                if (channelRange.contains("-")) {
                    String[] channels = channelRange.split("-");
                    if (channels.length != 2) {
                        Log.e(TAG, "Unrecognized channel range, length is " + channels.length);
                    } else {
                        int start = Integer.parseInt(channels[0]);
                        int end = Integer.parseInt(channels[1]);
                        if (start > end) {
                            Log.e(TAG, "Invalid channel range, from " + start + " to " + end);
                        } else {
                            acsChannelRange.start = start;
                            acsChannelRange.end = end;
                        }
                    }
                } else {
                    acsChannelRange.start = Integer.parseInt(channelRange);
                    acsChannelRange.end = acsChannelRange.start;
                }
                acsChannelRanges.add(acsChannelRange);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Malformed channel value detected: " + e);
            }
        }
        return acsChannelRanges;
    }

    private boolean checkHostapdAndLogFailure(String methodStr) {
        synchronized (this.mLock) {
            if (this.mIHostapd != null) {
                return true;
            }
            Log.e(TAG, "Can't call " + methodStr + ", IHostapd is null");
            return false;
        }
    }

    private boolean checkStatusAndLogFailure(HostapdStatus status, String methodStr) {
        synchronized (this.mLock) {
            if (status.code != 0) {
                Log.e(TAG, "IHostapd." + methodStr + " failed: " + status.code + ", " + status.debugMessage);
                return false;
            }
            if (this.mVerboseLoggingEnabled) {
                Log.d(TAG, "IHostapd." + methodStr + " succeeded");
            }
            return true;
        }
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        synchronized (this.mLock) {
            hostapdServiceDiedHandler(this.mDeathRecipientCookie);
            Log.e(TAG, "IHostapd." + methodStr + " failed with exception", e);
        }
    }

    private class HostapdCallback extends IHostapdCallback.Stub {
        private HostapdCallback() {
        }

        @Override // android.hardware.wifi.hostapd.V1_1.IHostapdCallback
        public void onFailure(String ifaceName) {
            Log.w(HostapdHal.TAG, "Failure on iface " + ifaceName);
            WifiNative.SoftApListener listener = (WifiNative.SoftApListener) HostapdHal.this.mSoftApListeners.get(ifaceName);
            if (listener != null) {
                listener.onFailure();
            }
        }
    }

    public class SehHostapdCallback extends ISehHostapdCallback.Stub {
        public String mInterface = null;

        SehHostapdCallback(String iface) {
            this.mInterface = iface;
        }

        @Override // android.hardware.wifi.hostapd.V1_1.IHostapdCallback
        public void onFailure(String ifaceName) {
            Log.w(HostapdHal.TAG, "Failure on iface " + ifaceName);
            WifiNative.SoftApListener listener = (WifiNative.SoftApListener) HostapdHal.this.mSoftApListeners.get(ifaceName);
            if (listener != null) {
                listener.onFailure();
            }
        }

        @Override // vendor.samsung.hardware.wifi.hostapd.V2_0.ISehHostapdCallback
        public void sehHostapdCallbackEvent(String str) {
            Log.w(HostapdHal.TAG, "hostapdCallbackEvent=  " + str);
            if (this.mInterface != null) {
                HostapdHal.this.mSemWifiApMonitor.hostapdCallbackEvent(this.mInterface, str);
            }
        }
    }

    public void readWhiteListFileToSendHostapd() {
        try {
            this.mISehHostapd.sehResetWhiteList();
        } catch (Exception e) {
            Log.i(TAG, "Exception" + e);
        }
        Log.d(TAG, "readWhiteListFileToSendHostapd");
        BufferedReader buf = null;
        try {
            BufferedReader buf2 = new BufferedReader(new FileReader("/data/misc/wifi_hostapd/hostapd.accept"), 64);
            while (true) {
                String bufReadLine = buf2.readLine();
                if (bufReadLine == null) {
                    try {
                        buf2.close();
                        return;
                    } catch (IOException e2) {
                        Log.i(TAG, "IOException" + e2);
                        return;
                    }
                } else if (bufReadLine.startsWith("#")) {
                    try {
                        this.mISehHostapd.sehAddWhiteList(bufReadLine.substring(1), buf2.readLine());
                    } catch (Exception e3) {
                        Log.i(TAG, "Exception" + e3);
                    }
                }
            }
        } catch (IOException e4) {
            Log.i(TAG, "IOException" + e4);
            if (0 != 0) {
                try {
                    buf.close();
                } catch (IOException e5) {
                    Log.i(TAG, "IOException" + e5);
                }
            }
        } catch (Throwable th) {
            if (0 != 0) {
                try {
                    buf.close();
                } catch (IOException e6) {
                    Log.i(TAG, "IOException" + e6);
                }
            }
            throw th;
        }
    }

    /* JADX WARNING: Code restructure failed: missing block: B:6:0x0068, code lost:
        if (r2 == 13) goto L_0x006a;
     */
    public String sendHostapdCommand(String command) {
        HostapdStatus mHostapdStatus = null;
        if (this.mISehHostapd == null) {
            return null;
        }
        try {
            Log.d(TAG, "hostapd command: " + command);
            this.mWifiManager = (WifiManager) this.mContext.getSystemService("wifi");
            int wifiApState = this.mWifiManager.getWifiApState();
            Log.d(TAG, "wifiApState " + wifiApState);
            addMHSDumpLog("HostapdHal  sendHostapdCommand:" + command + ",wifiApState:" + wifiApState);
            WifiManager wifiManager = this.mWifiManager;
            if (wifiApState != 12) {
                WifiManager wifiManager2 = this.mWifiManager;
            }
            Log.d(TAG, "hotspot enabling or enabled state");
            mHostapdStatus = this.mISehHostapd.sehSendCommand(command);
        } catch (RemoteException e) {
            Log.e(TAG, "mIHostapd exception: " + e);
        }
        if (mHostapdStatus == null || mHostapdStatus.code != 0) {
            return null;
        }
        return mHostapdStatus.debugMessage;
    }

    public boolean sehRegisterCallback(ISehHostapdCallback callback) {
        HostapdStatus mHostapdStatus = null;
        Log.d(TAG, "sehRegisterCallback ");
        ISehHostapd iSehHostapd = this.mISehHostapd;
        if (iSehHostapd == null) {
            Log.e(TAG, "mISehHostapd is null ");
            return false;
        }
        try {
            mHostapdStatus = iSehHostapd.sehRegisterCallback(callback);
        } catch (RemoteException e) {
            Log.e(TAG, "mIHostapd exception: " + e);
        }
        if (mHostapdStatus == null || mHostapdStatus.code != 0) {
            Log.e(TAG, "sehRegisterCallback failed ");
            return false;
        }
        Log.d(TAG, "sehRegisterCallback successful ");
        return true;
    }

    private boolean isShipBinary() {
        boolean result = isSepDevice() && !Debug.semIsProductDev();
        Log.i(TAG, "isShipBinary :" + result);
        return result;
    }

    private boolean isSepDevice() {
        int SEM_INT = 0;
        try {
            SEM_INT = Build.VERSION.class.getField("SEM_INT").getInt(Build.VERSION.class);
        } catch (IllegalAccessException | NoSuchFieldException e) {
        }
        return SEM_INT != 0;
    }

    public String getDumpLogs() {
        StringBuffer retValue = new StringBuffer();
        retValue.append("--HostapdHAL \n");
        retValue.append(this.mMHSDumpLogs.toString());
        return retValue.toString();
    }
}

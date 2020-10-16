package com.android.server.wifi.p2p;

import android.hardware.wifi.supplicant.V1_0.ISupplicant;
import android.hardware.wifi.supplicant.V1_0.ISupplicantIface;
import android.hardware.wifi.supplicant.V1_0.ISupplicantNetwork;
import android.hardware.wifi.supplicant.V1_0.ISupplicantP2pIface;
import android.hardware.wifi.supplicant.V1_0.ISupplicantP2pIfaceCallback;
import android.hardware.wifi.supplicant.V1_0.ISupplicantP2pNetwork;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatus;
import android.hardware.wifi.supplicant.V1_0.WpsConfigMethods;
import android.hardware.wifi.supplicant.V1_1.ISupplicant;
import android.hidl.manager.V1_0.IServiceManager;
import android.hidl.manager.V1_0.IServiceNotification;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.os.HidlSupport;
import android.os.IHwBinder;
import android.os.IHwInterface;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.util.ArrayUtils;
import com.android.server.wifi.WifiNetworkSelector;
import com.android.server.wifi.p2p.SupplicantP2pIfaceHal;
import com.android.server.wifi.util.NativeUtil;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicant;
import vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicantIface;
import vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicantP2pIface;
import vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicantP2pIfaceCallback;

public class SupplicantP2pIfaceHal {
    private static final int DEFAULT_GROUP_OWNER_INTENT = 6;
    private static final int DEFAULT_OPERATING_CLASS = 81;
    private static final int RESULT_NOT_VALID = -1;
    private static final String TAG = "SupplicantP2pIfaceHal";
    private static final Pattern WPS_DEVICE_TYPE_PATTERN = Pattern.compile("^(\\d{1,2})-([0-9a-fA-F]{8})-(\\d{1,2})$");
    private static boolean sVerboseLoggingEnabled = true;
    public final int P2P_GO_OPER_FREQ = -999;
    private SupplicantP2pIfaceCallback mCallback = null;
    private SupplicantP2pIfaceCallbackExt mCallbackExt = null;
    private ISupplicantIface mHidlSupplicantIface = null;
    private ISehSupplicant mISehSupplicant;
    private ISehSupplicantP2pIface mISehSupplicantP2pIface = null;
    private IServiceManager mIServiceManager = null;
    private ISupplicant mISupplicant = null;
    private ISupplicantP2pIface mISupplicantP2pIface = null;
    private Object mLock = new Object();
    private final WifiP2pMonitor mMonitor;
    private final IHwBinder.DeathRecipient mServiceManagerDeathRecipient = new IHwBinder.DeathRecipient() {
        /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$Wvwk6xCSAknWmsVUgpUqV_3NQiE */

        public final void serviceDied(long j) {
            SupplicantP2pIfaceHal.this.lambda$new$0$SupplicantP2pIfaceHal(j);
        }
    };
    private final IServiceNotification mServiceNotificationCallback = new IServiceNotification.Stub() {
        /* class com.android.server.wifi.p2p.SupplicantP2pIfaceHal.C05241 */

        public void onRegistration(String fqName, String name, boolean preexisting) {
            synchronized (SupplicantP2pIfaceHal.this.mLock) {
                if (SupplicantP2pIfaceHal.sVerboseLoggingEnabled) {
                    Log.i(SupplicantP2pIfaceHal.TAG, "IServiceNotification.onRegistration for: " + fqName + ", " + name + " preexisting=" + preexisting);
                }
                if (!SupplicantP2pIfaceHal.this.initSupplicantService()) {
                    Log.e(SupplicantP2pIfaceHal.TAG, "initalizing ISupplicant failed.");
                    SupplicantP2pIfaceHal.this.supplicantServiceDiedHandler();
                } else {
                    Log.i(SupplicantP2pIfaceHal.TAG, "Completed initialization of ISupplicant interfaces.");
                }
                if (!SupplicantP2pIfaceHal.this.initSupplicantExtService()) {
                    Log.e(SupplicantP2pIfaceHal.TAG, "initalizing ISehSupplicant failed.");
                    SupplicantP2pIfaceHal.this.supplicantExtServiceDiedHandler();
                } else {
                    Log.i(SupplicantP2pIfaceHal.TAG, "Completed initialization of ISehSupplicant.");
                }
            }
        }
    };
    private final IHwBinder.DeathRecipient mSupplicantDeathRecipient = new IHwBinder.DeathRecipient() {
        /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$AwvLtkH4UyCOhUYx__3ExZj_7jQ */

        public final void serviceDied(long j) {
            SupplicantP2pIfaceHal.this.lambda$new$1$SupplicantP2pIfaceHal(j);
        }
    };
    private final IHwBinder.DeathRecipient mSupplicantExtDeathRecipient = new IHwBinder.DeathRecipient() {
        /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$x1gylRyS5TJKypV7BSDBGlA_JgQ */

        public final void serviceDied(long j) {
            SupplicantP2pIfaceHal.this.lambda$new$2$SupplicantP2pIfaceHal(j);
        }
    };

    public /* synthetic */ void lambda$new$0$SupplicantP2pIfaceHal(long cookie) {
        Log.w(TAG, "IServiceManager died: cookie=" + cookie);
        synchronized (this.mLock) {
            supplicantServiceDiedHandler();
            this.mIServiceManager = null;
        }
    }

    public /* synthetic */ void lambda$new$1$SupplicantP2pIfaceHal(long cookie) {
        Log.w(TAG, "ISupplicant/ISupplicantP2pIface died: cookie=" + cookie);
        synchronized (this.mLock) {
            supplicantServiceDiedHandler();
        }
    }

    public /* synthetic */ void lambda$new$2$SupplicantP2pIfaceHal(long cookie) {
        Log.w(TAG, "ISehSupplicant died: cookie=" + cookie);
        synchronized (this.mLock) {
            supplicantExtServiceDiedHandler();
        }
    }

    public SupplicantP2pIfaceHal(WifiP2pMonitor monitor) {
        this.mMonitor = monitor;
    }

    private boolean linkToServiceManagerDeath() {
        IServiceManager iServiceManager = this.mIServiceManager;
        if (iServiceManager == null) {
            return false;
        }
        try {
            if (iServiceManager.linkToDeath(this.mServiceManagerDeathRecipient, 0)) {
                return true;
            }
            Log.wtf(TAG, "Error on linkToDeath on IServiceManager");
            supplicantServiceDiedHandler();
            this.mIServiceManager = null;
            return false;
        } catch (RemoteException e) {
            Log.e(TAG, "IServiceManager.linkToDeath exception", e);
            return false;
        }
    }

    public static void enableVerboseLogging(int verbose) {
        sVerboseLoggingEnabled = verbose > 0;
        SupplicantP2pIfaceCallback.enableVerboseLogging(verbose);
    }

    public boolean initialize() {
        if (sVerboseLoggingEnabled) {
            Log.i(TAG, "Registering ISupplicant service ready callback.");
        }
        synchronized (this.mLock) {
            if (this.mIServiceManager != null) {
                Log.i(TAG, "Supplicant HAL already initialized.");
                return true;
            }
            this.mISupplicant = null;
            this.mISupplicantP2pIface = null;
            this.mISehSupplicant = null;
            this.mISehSupplicantP2pIface = null;
            try {
                this.mIServiceManager = getServiceManagerMockable();
                if (this.mIServiceManager == null) {
                    Log.e(TAG, "Failed to get HIDL Service Manager");
                    return false;
                } else if (!linkToServiceManagerDeath()) {
                    return false;
                } else {
                    if (this.mIServiceManager.registerForNotifications(ISupplicant.kInterfaceName, "", this.mServiceNotificationCallback)) {
                        return true;
                    }
                    Log.e(TAG, "Failed to register for notifications to android.hardware.wifi.supplicant@1.0::ISupplicant");
                    this.mIServiceManager = null;
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Exception while trying to register a listener for ISupplicant service: " + e);
                supplicantServiceDiedHandler();
                return false;
            }
        }
    }

    private boolean linkToSupplicantDeath() {
        ISupplicant iSupplicant = this.mISupplicant;
        if (iSupplicant == null) {
            return false;
        }
        try {
            if (iSupplicant.linkToDeath(this.mSupplicantDeathRecipient, 0)) {
                return true;
            }
            Log.wtf(TAG, "Error on linkToDeath on ISupplicant");
            supplicantServiceDiedHandler();
            return false;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicant.linkToDeath exception", e);
            return false;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean initSupplicantService() {
        synchronized (this.mLock) {
            try {
                this.mISupplicant = getSupplicantMockable();
                if (this.mISupplicant == null) {
                    Log.e(TAG, "Got null ISupplicant service. Stopping supplicant HIDL startup");
                    return false;
                } else if (!linkToSupplicantDeath()) {
                    return false;
                } else {
                    return true;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicant.getService exception: " + e);
                return false;
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    private boolean linkToSupplicantExtDeath() {
        synchronized (this.mLock) {
            if (this.mISehSupplicant == null) {
                return false;
            }
            try {
                if (this.mISehSupplicant.linkToDeath(this.mSupplicantExtDeathRecipient, 0)) {
                    return true;
                }
                Log.wtf(TAG, "Error on linkToDeath on ISehSupplicant");
                supplicantExtServiceDiedHandler();
                return false;
            } catch (RemoteException e) {
                Log.e(TAG, "ISehSupplicant.linkToDeath exception", e);
                return false;
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean initSupplicantExtService() {
        synchronized (this.mLock) {
            try {
                this.mISehSupplicant = getSupplicantExtMockable();
                if (this.mISehSupplicant == null) {
                    Log.e(TAG, "Got null ISehSupplicant service. Stopping supplicant HIDL startup");
                    return false;
                } else if (!linkToSupplicantExtDeath()) {
                    return false;
                } else {
                    return true;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "ISehSupplicant.getService exception: " + e);
                return false;
            } catch (NoSuchElementException e2) {
                Log.e(TAG, "ISehSupplicant.getService exception: " + e2);
                return false;
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    private boolean linkToSupplicantP2pIfaceExtDeath() {
        ISehSupplicantP2pIface iSehSupplicantP2pIface = this.mISehSupplicantP2pIface;
        if (iSehSupplicantP2pIface == null) {
            return false;
        }
        try {
            if (iSehSupplicantP2pIface.linkToDeath(this.mSupplicantExtDeathRecipient, 0)) {
                return true;
            }
            Log.wtf(TAG, "Error on linkToDeath on ISehSupplicantP2pIface");
            supplicantExtServiceDiedHandler();
            return false;
        } catch (RemoteException e) {
            Log.e(TAG, "ISehSupplicantP2pIface.linkToDeath exception", e);
            return false;
        }
    }

    private boolean linkToSupplicantP2pIfaceDeath() {
        ISupplicantP2pIface iSupplicantP2pIface = this.mISupplicantP2pIface;
        if (iSupplicantP2pIface == null) {
            return false;
        }
        try {
            if (iSupplicantP2pIface.linkToDeath(this.mSupplicantDeathRecipient, 0)) {
                return true;
            }
            Log.wtf(TAG, "Error on linkToDeath on ISupplicantP2pIface");
            supplicantServiceDiedHandler();
            return false;
        } catch (RemoteException e) {
            Log.e(TAG, "ISupplicantP2pIface.linkToDeath exception", e);
            return false;
        }
    }

    public boolean setupIface(String ifaceName) {
        ISupplicantIface ifaceHwBinder;
        synchronized (this.mLock) {
            if (this.mISupplicantP2pIface != null) {
                return false;
            }
            if (isV1_1()) {
                ifaceHwBinder = addIfaceV1_1(ifaceName);
            } else {
                ifaceHwBinder = getIfaceV1_0(ifaceName);
            }
            if (ifaceHwBinder == null) {
                Log.e(TAG, "initSupplicantP2pIface got null iface");
                return false;
            }
            this.mISupplicantP2pIface = getP2pIfaceMockable(ifaceHwBinder);
            if (!linkToSupplicantP2pIfaceDeath()) {
                return false;
            }
            ISehSupplicantIface ifaceExtHwBinder = addIfaceExt(ifaceName);
            if (ifaceExtHwBinder == null) {
                Log.e(TAG, "setupIface got null ifaceExt");
                return false;
            }
            this.mISehSupplicantP2pIface = getP2pIfaceExtMockable(ifaceExtHwBinder);
            if (!linkToSupplicantP2pIfaceExtDeath()) {
                return false;
            }
            if (!(this.mISupplicantP2pIface == null || this.mMonitor == null)) {
                this.mCallback = new SupplicantP2pIfaceCallback(ifaceName, this.mMonitor);
                if (!registerCallback(this.mCallback)) {
                    Log.e(TAG, "Callback registration failed. Initialization incomplete.");
                    return false;
                } else if (this.mISehSupplicantP2pIface != null) {
                    this.mCallbackExt = new SupplicantP2pIfaceCallbackExt(ifaceName, this.mMonitor);
                    if (!registerCallbackExt(this.mCallbackExt)) {
                        Log.e(TAG, "Callback registration failed. Initialization incomplete.");
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private ISupplicantIface getIfaceV1_0(String ifaceName) {
        ArrayList<ISupplicant.IfaceInfo> supplicantIfaces = new ArrayList<>();
        try {
            this.mISupplicant.listInterfaces(new ISupplicant.listInterfacesCallback(supplicantIfaces) {
                /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$K2TE2C1dyZmtIfq7jcEAIuTdPQ */
                private final /* synthetic */ ArrayList f$0;

                {
                    this.f$0 = r1;
                }

                @Override // android.hardware.wifi.supplicant.V1_0.ISupplicant.listInterfacesCallback
                public final void onValues(SupplicantStatus supplicantStatus, ArrayList arrayList) {
                    SupplicantP2pIfaceHal.lambda$getIfaceV1_0$3(this.f$0, supplicantStatus, arrayList);
                }
            });
            if (supplicantIfaces.size() == 0) {
                Log.e(TAG, "Got zero HIDL supplicant ifaces. Stopping supplicant HIDL startup.");
                supplicantServiceDiedHandler();
                return null;
            }
            SupplicantResult<ISupplicantIface> supplicantIface = new SupplicantResult<>("getInterface()");
            Iterator<ISupplicant.IfaceInfo> it = supplicantIfaces.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                ISupplicant.IfaceInfo ifaceInfo = it.next();
                if (ifaceInfo.type == 1 && ifaceName.equals(ifaceInfo.name)) {
                    try {
                        this.mISupplicant.getInterface(ifaceInfo, new ISupplicant.getInterfaceCallback() {
                            /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$H5lhdEKqAucd783jDVWMUXMQkgE */

                            @Override // android.hardware.wifi.supplicant.V1_0.ISupplicant.getInterfaceCallback
                            public final void onValues(SupplicantStatus supplicantStatus, ISupplicantIface iSupplicantIface) {
                                SupplicantP2pIfaceHal.lambda$getIfaceV1_0$4(SupplicantP2pIfaceHal.SupplicantResult.this, supplicantStatus, iSupplicantIface);
                            }
                        });
                        break;
                    } catch (RemoteException e) {
                        Log.e(TAG, "ISupplicant.getInterface exception: " + e);
                        supplicantServiceDiedHandler();
                        return null;
                    }
                }
            }
            return supplicantIface.getResult();
        } catch (RemoteException e2) {
            Log.e(TAG, "ISupplicant.listInterfaces exception: " + e2);
            return null;
        }
    }

    static /* synthetic */ void lambda$getIfaceV1_0$3(ArrayList supplicantIfaces, SupplicantStatus status, ArrayList ifaces) {
        if (status.code != 0) {
            Log.e(TAG, "Getting Supplicant Interfaces failed: " + status.code);
            return;
        }
        supplicantIfaces.addAll(ifaces);
    }

    static /* synthetic */ void lambda$getIfaceV1_0$4(SupplicantResult supplicantIface, SupplicantStatus status, ISupplicantIface iface) {
        if (status.code != 0) {
            Log.e(TAG, "Failed to get ISupplicantIface " + status.code);
            return;
        }
        supplicantIface.setResult(status, iface);
    }

    private ISupplicantIface addIfaceV1_1(String ifaceName) {
        synchronized (this.mLock) {
            ISupplicant.IfaceInfo ifaceInfo = new ISupplicant.IfaceInfo();
            ifaceInfo.name = ifaceName;
            ifaceInfo.type = 1;
            SupplicantResult<ISupplicantIface> supplicantIface = new SupplicantResult<>("addInterface(" + ifaceInfo + ")");
            try {
                android.hardware.wifi.supplicant.V1_1.ISupplicant supplicant_v1_1 = getSupplicantMockableV1_1();
                if (supplicant_v1_1 == null) {
                    Log.e(TAG, "Can't call addIface: ISupplicantP2pIface is null");
                    return null;
                }
                supplicant_v1_1.addInterface(ifaceInfo, new ISupplicant.addInterfaceCallback() {
                    /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$Kzz9uaO3yHdyeFYHAFqAlp4Jug */

                    @Override // android.hardware.wifi.supplicant.V1_1.ISupplicant.addInterfaceCallback
                    public final void onValues(SupplicantStatus supplicantStatus, ISupplicantIface iSupplicantIface) {
                        SupplicantP2pIfaceHal.lambda$addIfaceV1_1$5(SupplicantP2pIfaceHal.SupplicantResult.this, supplicantStatus, iSupplicantIface);
                    }
                });
                return supplicantIface.getResult();
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicant.addInterface exception: " + e);
                supplicantServiceDiedHandler();
                return null;
            }
        }
    }

    static /* synthetic */ void lambda$addIfaceV1_1$5(SupplicantResult supplicantIface, SupplicantStatus status, ISupplicantIface iface) {
        if (status.code == 0 || status.code == 5) {
            supplicantIface.setResult(status, iface);
            return;
        }
        Log.e(TAG, "Failed to get ISupplicantIface " + status.code);
    }

    private ISehSupplicantIface addIfaceExt(String ifaceName) {
        synchronized (this.mLock) {
            ISehSupplicant.IfaceInfo ifaceInfo = new ISehSupplicant.IfaceInfo();
            ifaceInfo.name = ifaceName;
            ifaceInfo.type = 1;
            HidlSupport.Mutable<ISehSupplicantIface> supplicantIfaceExt = new HidlSupport.Mutable<>();
            try {
                if (getSupplicantExtMockable() == null) {
                    Log.e(TAG, "ISehSupplicant.addInterface getSupplicantExtMockable is null");
                    return null;
                }
                getSupplicantExtMockable().addInterface(ifaceInfo, new ISehSupplicant.addInterfaceCallback(supplicantIfaceExt) {
                    /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$VcRa1e4CavfDFbFrTwMz6ydwjM8 */
                    private final /* synthetic */ HidlSupport.Mutable f$0;

                    {
                        this.f$0 = r1;
                    }

                    @Override // vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicant.addInterfaceCallback
                    public final void onValues(SupplicantStatus supplicantStatus, ISehSupplicantIface iSehSupplicantIface) {
                        SupplicantP2pIfaceHal.lambda$addIfaceExt$6(this.f$0, supplicantStatus, iSehSupplicantIface);
                    }
                });
                return (ISehSupplicantIface) supplicantIfaceExt.value;
            } catch (RemoteException e) {
                Log.e(TAG, "ISehSupplicant.addInterface exception: " + e);
                return null;
            } catch (NoSuchElementException e2) {
                Log.e(TAG, "ISehSupplicant.addInterface exception: " + e2);
                return null;
            } catch (NullPointerException e3) {
                Log.e(TAG, "ISehSupplicant.addInterface exception: " + e3);
                return null;
            }
        }
    }

    static /* synthetic */ void lambda$addIfaceExt$6(HidlSupport.Mutable supplicantIfaceExt, SupplicantStatus status, ISehSupplicantIface iface) {
        if (status.code == 0 || status.code == 5) {
            supplicantIfaceExt.value = iface;
            return;
        }
        Log.e(TAG, "Failed to create ISehSupplicantIface " + status.code);
    }

    public boolean teardownIface(String ifaceName) {
        synchronized (this.mLock) {
            if (this.mISupplicantP2pIface == null) {
                return false;
            }
            if (!isV1_1()) {
                return true;
            }
            return removeIfaceV1_1(ifaceName);
        }
    }

    private boolean removeIfaceV1_1(String ifaceName) {
        synchronized (this.mLock) {
            try {
                android.hardware.wifi.supplicant.V1_1.ISupplicant supplicant_v1_1 = getSupplicantMockableV1_1();
                if (supplicant_v1_1 == null) {
                    Log.e(TAG, "Can't call removeIface: ISupplicantP2pIface is null");
                    return false;
                }
                ISupplicant.IfaceInfo ifaceInfo = new ISupplicant.IfaceInfo();
                ifaceInfo.name = ifaceName;
                ifaceInfo.type = 1;
                SupplicantStatus status = supplicant_v1_1.removeInterface(ifaceInfo);
                if (status.code != 0) {
                    Log.e(TAG, "Failed to remove iface " + status.code);
                    return false;
                }
                this.mCallback = null;
                this.mISupplicantP2pIface = null;
                this.mISehSupplicantP2pIface = null;
                return true;
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicant.removeInterface exception: " + e);
                supplicantServiceDiedHandler();
                return false;
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void supplicantServiceDiedHandler() {
        synchronized (this.mLock) {
            this.mISupplicant = null;
            this.mISupplicantP2pIface = null;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void supplicantExtServiceDiedHandler() {
        synchronized (this.mLock) {
            this.mISehSupplicant = null;
            this.mISehSupplicantP2pIface = null;
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
        return (this.mISupplicant == null || this.mISehSupplicant == null) ? false : true;
    }

    /* access modifiers changed from: protected */
    public IServiceManager getServiceManagerMockable() throws RemoteException {
        return IServiceManager.getService();
    }

    /* access modifiers changed from: protected */
    public android.hardware.wifi.supplicant.V1_0.ISupplicant getSupplicantMockable() throws RemoteException {
        try {
            return android.hardware.wifi.supplicant.V1_0.ISupplicant.getService();
        } catch (NoSuchElementException e) {
            Log.e(TAG, "Failed to get ISupplicant", e);
            return null;
        }
    }

    /* access modifiers changed from: protected */
    public android.hardware.wifi.supplicant.V1_1.ISupplicant getSupplicantMockableV1_1() throws RemoteException {
        android.hardware.wifi.supplicant.V1_1.ISupplicant castFrom;
        synchronized (this.mLock) {
            try {
                castFrom = android.hardware.wifi.supplicant.V1_1.ISupplicant.castFrom((IHwInterface) this.mISupplicant);
            } catch (NoSuchElementException e) {
                Log.e(TAG, "Failed to get ISupplicant", e);
                return null;
            } catch (Throwable th) {
                throw th;
            }
        }
        return castFrom;
    }

    /* access modifiers changed from: protected */
    public ISehSupplicant getSupplicantExtMockable() throws RemoteException, NoSuchElementException {
        ISehSupplicant service;
        synchronized (this.mLock) {
            service = ISehSupplicant.getService();
        }
        return service;
    }

    /* access modifiers changed from: protected */
    public ISehSupplicantP2pIface getP2pIfaceExtMockable(ISehSupplicantIface ifaceExt) {
        ISehSupplicantP2pIface asInterface;
        synchronized (this.mLock) {
            asInterface = ISehSupplicantP2pIface.asInterface(ifaceExt.asBinder());
        }
        return asInterface;
    }

    /* access modifiers changed from: protected */
    public ISupplicantP2pIface getP2pIfaceMockable(ISupplicantIface iface) {
        return ISupplicantP2pIface.asInterface(iface.asBinder());
    }

    /* access modifiers changed from: protected */
    public android.hardware.wifi.supplicant.V1_2.ISupplicantP2pIface getP2pIfaceMockableV1_2() {
        ISupplicantP2pIface iSupplicantP2pIface = this.mISupplicantP2pIface;
        if (iSupplicantP2pIface == null) {
            return null;
        }
        return android.hardware.wifi.supplicant.V1_2.ISupplicantP2pIface.castFrom((IHwInterface) iSupplicantP2pIface);
    }

    /* access modifiers changed from: protected */
    public ISupplicantP2pNetwork getP2pNetworkMockable(ISupplicantNetwork network) {
        return ISupplicantP2pNetwork.asInterface(network.asBinder());
    }

    private boolean isV1_1() {
        boolean z;
        synchronized (this.mLock) {
            z = false;
            try {
                if (getSupplicantMockableV1_1() != null) {
                    z = true;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicant.getService exception: " + e);
                supplicantServiceDiedHandler();
                return false;
            } catch (Throwable th) {
                throw th;
            }
        }
        return z;
    }

    protected static void logd(String s) {
        if (sVerboseLoggingEnabled) {
            Log.d(TAG, s);
        }
    }

    protected static void logCompletion(String operation, SupplicantStatus status) {
        if (status == null) {
            Log.w(TAG, operation + " failed: no status code returned.");
        } else if (status.code == 0) {
            logd(operation + " completed successfully.");
        } else {
            Log.w(TAG, operation + " failed: " + status.code + " (" + status.debugMessage + ")");
        }
    }

    private boolean checkSupplicantP2pIfaceAndLogFailure(String method) {
        if (this.mISupplicantP2pIface != null) {
            return true;
        }
        Log.e(TAG, "Can't call " + method + ": ISupplicantP2pIface is null");
        return false;
    }

    private boolean checkSupplicantP2pIfaceExtAndLogFailure(String method) {
        if (this.mISehSupplicantP2pIface != null) {
            return true;
        }
        Log.e(TAG, "Can't call " + method + ": ISehSupplicantP2pIface is null");
        return false;
    }

    private boolean checkSupplicantP2pIfaceAndLogFailureV1_2(String method) {
        if (getP2pIfaceMockableV1_2() != null) {
            return true;
        }
        Log.e(TAG, "Can't call " + method + ": ISupplicantP2pIface is null");
        return false;
    }

    private int wpsInfoToConfigMethod(int info) {
        if (info == 0) {
            return 0;
        }
        if (info == 1) {
            return 1;
        }
        if (info == 2 || info == 3) {
            return 2;
        }
        Log.e(TAG, "Unsupported WPS provision method: " + info);
        return -1;
    }

    public String getName() {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("getName")) {
                return null;
            }
            SupplicantResult<String> result = new SupplicantResult<>("getName()");
            try {
                this.mISupplicantP2pIface.getName(new ISupplicantIface.getNameCallback() {
                    /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$sIk1Ut2Ot1FRlHjkgPcDeVsC_Hc */

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantIface.getNameCallback
                    public final void onValues(SupplicantStatus supplicantStatus, String str) {
                        SupplicantP2pIfaceHal.SupplicantResult.this.setResult(supplicantStatus, str);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.getResult();
        }
    }

    public boolean registerCallback(ISupplicantP2pIfaceCallback receiver) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("registerCallback")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("registerCallback()");
            try {
                result.setResult(this.mISupplicantP2pIface.registerCallback(receiver));
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    private boolean registerCallbackExt(ISehSupplicantP2pIfaceCallback receiver) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceExtAndLogFailure("registerCallbackExt")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("registerCallbackExt()");
            try {
                result.setResult(this.mISehSupplicantP2pIface.sehRegisterCallback(receiver));
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantExtServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean find(int timeout) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("find")) {
                return false;
            }
            if (timeout < 0) {
                Log.e(TAG, "Invalid timeout value: " + timeout);
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("find(" + timeout + ")");
            try {
                result.setResult(this.mISupplicantP2pIface.find(timeout));
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean stopFind() {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("stopFind")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("stopFind()");
            try {
                result.setResult(this.mISupplicantP2pIface.stopFind());
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean flush() {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("flush")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("flush()");
            try {
                result.setResult(this.mISupplicantP2pIface.flush());
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean serviceFlush() {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("serviceFlush")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("serviceFlush()");
            try {
                result.setResult(this.mISupplicantP2pIface.flushServices());
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean setPowerSave(String groupIfName, boolean enable) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("setPowerSave")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("setPowerSave(" + groupIfName + ", " + enable + ")");
            try {
                result.setResult(this.mISupplicantP2pIface.setPowerSave(groupIfName, enable));
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean setGroupIdle(String groupIfName, int timeoutInSec) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("setGroupIdle")) {
                return false;
            }
            if (timeoutInSec < 0) {
                Log.e(TAG, "Invalid group timeout value " + timeoutInSec);
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("setGroupIdle(" + groupIfName + ", " + timeoutInSec + ")");
            try {
                result.setResult(this.mISupplicantP2pIface.setGroupIdle(groupIfName, timeoutInSec));
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean setSsidPostfix(String postfix) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("setSsidPostfix")) {
                return false;
            }
            if (postfix == null) {
                Log.e(TAG, "Invalid SSID postfix value (null).");
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("setSsidPostfix(" + postfix + ")");
            try {
                ISupplicantP2pIface iSupplicantP2pIface = this.mISupplicantP2pIface;
                result.setResult(iSupplicantP2pIface.setSsidPostfix(NativeUtil.decodeSsid("\"" + postfix + "\"")));
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            } catch (IllegalArgumentException e2) {
                Log.e(TAG, "Could not decode SSID.", e2);
                return false;
            }
            return result.isSuccess();
        }
    }

    public String connect(WifiP2pConfig config, boolean joinExistingGroup) {
        if (config == null) {
            return null;
        }
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("setSsidPostfix")) {
                return null;
            }
            if (config.deviceAddress == null) {
                Log.e(TAG, "Could not parse null mac address.");
                return null;
            } else if (config.wps.setup != 0 || TextUtils.isEmpty(config.wps.pin)) {
                try {
                    byte[] peerAddress = NativeUtil.macAddressToByteArray(config.deviceAddress);
                    int provisionMethod = wpsInfoToConfigMethod(config.wps.setup);
                    if (provisionMethod == -1) {
                        Log.e(TAG, "Invalid WPS config method: " + config.wps.setup);
                        return null;
                    }
                    String preSelectedPin = TextUtils.isEmpty(config.wps.pin) ? "" : config.wps.pin;
                    boolean persistent = config.netId == -2;
                    int goIntent = 0;
                    if (!joinExistingGroup) {
                        int groupOwnerIntent = config.groupOwnerIntent;
                        if (groupOwnerIntent < 0 || groupOwnerIntent > 15) {
                            groupOwnerIntent = 6;
                        }
                        goIntent = groupOwnerIntent;
                    }
                    SupplicantResult<String> result = new SupplicantResult<>("connect(" + config.deviceAddress + ")");
                    try {
                        this.mISupplicantP2pIface.connect(peerAddress, provisionMethod, preSelectedPin, joinExistingGroup, persistent, goIntent, new ISupplicantP2pIface.connectCallback() {
                            /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$CIdf5Fd_xyUSFmquRLJmninTEzE */

                            @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantP2pIface.connectCallback
                            public final void onValues(SupplicantStatus supplicantStatus, String str) {
                                SupplicantP2pIfaceHal.SupplicantResult.this.setResult(supplicantStatus, str);
                            }
                        });
                    } catch (RemoteException e) {
                        Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                        supplicantServiceDiedHandler();
                    }
                    return result.getResult();
                } catch (Exception e2) {
                    Log.e(TAG, "Could not parse peer mac address.", e2);
                    return null;
                }
            } else {
                Log.e(TAG, "Expected empty pin for PBC.");
                return null;
            }
        }
    }

    public boolean cancelConnect() {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("cancelConnect")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("cancelConnect()");
            try {
                result.setResult(this.mISupplicantP2pIface.cancelConnect());
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean provisionDiscovery(WifiP2pConfig config) {
        if (config == null) {
            return false;
        }
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("provisionDiscovery")) {
                return false;
            }
            int targetMethod = wpsInfoToConfigMethod(config.wps.setup);
            if (targetMethod == -1) {
                Log.e(TAG, "Unrecognized WPS configuration method: " + config.wps.setup);
                return false;
            }
            if (targetMethod == 1) {
                targetMethod = 2;
            } else if (targetMethod == 2) {
                targetMethod = 1;
            }
            if (config.deviceAddress == null) {
                Log.e(TAG, "Cannot parse null mac address.");
                return false;
            }
            try {
                byte[] macAddress = NativeUtil.macAddressToByteArray(config.deviceAddress);
                SupplicantResult<Void> result = new SupplicantResult<>("provisionDiscovery(" + config.deviceAddress + ", " + config.wps.setup + ")");
                try {
                    result.setResult(this.mISupplicantP2pIface.provisionDiscovery(macAddress, targetMethod));
                } catch (RemoteException e) {
                    Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                    supplicantServiceDiedHandler();
                }
                return result.isSuccess();
            } catch (Exception e2) {
                Log.e(TAG, "Could not parse peer mac address.", e2);
                return false;
            }
        }
    }

    public boolean invite(WifiP2pGroup group, String peerAddress) {
        if (TextUtils.isEmpty(peerAddress)) {
            return false;
        }
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("invite")) {
                return false;
            }
            if (group == null) {
                Log.e(TAG, "Cannot invite to null group.");
                return false;
            } else if (group.getOwner() == null) {
                Log.e(TAG, "Cannot invite to group with null owner.");
                return false;
            } else if (group.getOwner().deviceAddress == null) {
                Log.e(TAG, "Group owner has no mac address.");
                return false;
            } else {
                try {
                    byte[] ownerMacAddress = NativeUtil.macAddressToByteArray(group.getOwner().deviceAddress);
                    if (peerAddress == null) {
                        Log.e(TAG, "Cannot parse peer mac address.");
                        return false;
                    }
                    try {
                        byte[] peerMacAddress = NativeUtil.macAddressToByteArray(peerAddress);
                        SupplicantResult<Void> result = new SupplicantResult<>("invite(" + group.getInterface() + ", " + group.getOwner().deviceAddress + ", " + peerAddress + ")");
                        try {
                            result.setResult(this.mISupplicantP2pIface.invite(group.getInterface(), ownerMacAddress, peerMacAddress));
                        } catch (RemoteException e) {
                            Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                            supplicantServiceDiedHandler();
                        }
                        return result.isSuccess();
                    } catch (Exception e2) {
                        Log.e(TAG, "Peer mac address parse error.", e2);
                        return false;
                    }
                } catch (Exception e3) {
                    Log.e(TAG, "Group owner mac address parse error.", e3);
                    return false;
                }
            }
        }
    }

    public boolean reject(String peerAddress) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("reject")) {
                return false;
            }
            if (peerAddress == null) {
                Log.e(TAG, "Cannot parse rejected peer's mac address.");
                return false;
            }
            try {
                byte[] macAddress = NativeUtil.macAddressToByteArray(peerAddress);
                SupplicantResult<Void> result = new SupplicantResult<>("reject(" + peerAddress + ")");
                try {
                    result.setResult(this.mISupplicantP2pIface.reject(macAddress));
                } catch (RemoteException e) {
                    Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                    supplicantServiceDiedHandler();
                }
                return result.isSuccess();
            } catch (Exception e2) {
                Log.e(TAG, "Could not parse peer mac address.", e2);
                return false;
            }
        }
    }

    public String getDeviceAddress() {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("getDeviceAddress")) {
                return null;
            }
            SupplicantResult<String> result = new SupplicantResult<>("getDeviceAddress()");
            try {
                this.mISupplicantP2pIface.getDeviceAddress(new ISupplicantP2pIface.getDeviceAddressCallback() {
                    /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$t2ENcf1hC2ZTs2pdcn6bNgOwSpg */

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantP2pIface.getDeviceAddressCallback
                    public final void onValues(SupplicantStatus supplicantStatus, byte[] bArr) {
                        SupplicantP2pIfaceHal.lambda$getDeviceAddress$9(SupplicantP2pIfaceHal.SupplicantResult.this, supplicantStatus, bArr);
                    }
                });
                return result.getResult();
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
                return null;
            }
        }
    }

    static /* synthetic */ void lambda$getDeviceAddress$9(SupplicantResult result, SupplicantStatus status, byte[] address) {
        String parsedAddress = null;
        try {
            parsedAddress = NativeUtil.macAddressFromByteArray(address);
        } catch (Exception e) {
            Log.e(TAG, "Could not process reported address.", e);
        }
        result.setResult(status, parsedAddress);
    }

    public String getSsid(String address) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("getSsid")) {
                return null;
            }
            if (address == null) {
                Log.e(TAG, "Cannot parse peer mac address.");
                return null;
            }
            try {
                byte[] macAddress = NativeUtil.macAddressToByteArray(address);
                SupplicantResult<String> result = new SupplicantResult<>("getSsid(" + address + ")");
                try {
                    this.mISupplicantP2pIface.getSsid(macAddress, new ISupplicantP2pIface.getSsidCallback() {
                        /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$OEkcKbSwqnl3I5LEtRaEm6LRxgA */

                        @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantP2pIface.getSsidCallback
                        public final void onValues(SupplicantStatus supplicantStatus, ArrayList arrayList) {
                            SupplicantP2pIfaceHal.lambda$getSsid$10(SupplicantP2pIfaceHal.SupplicantResult.this, supplicantStatus, arrayList);
                        }
                    });
                    return result.getResult();
                } catch (RemoteException e) {
                    Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                    supplicantServiceDiedHandler();
                    return null;
                }
            } catch (Exception e2) {
                Log.e(TAG, "Could not parse mac address.", e2);
                return null;
            }
        }
    }

    static /* synthetic */ void lambda$getSsid$10(SupplicantResult result, SupplicantStatus status, ArrayList ssid) {
        String ssidString = null;
        if (ssid != null) {
            try {
                ssidString = NativeUtil.removeEnclosingQuotes(NativeUtil.encodeSsid(ssid));
            } catch (Exception e) {
                Log.e(TAG, "Could not encode SSID.", e);
            }
        }
        result.setResult(status, ssidString);
    }

    public boolean reinvoke(int networkId, String peerAddress) {
        if (TextUtils.isEmpty(peerAddress) || networkId < 0) {
            return false;
        }
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("reinvoke")) {
                return false;
            }
            if (peerAddress == null) {
                Log.e(TAG, "Cannot parse peer mac address.");
                return false;
            }
            try {
                byte[] macAddress = NativeUtil.macAddressToByteArray(peerAddress);
                SupplicantResult<Void> result = new SupplicantResult<>("reinvoke(" + networkId + ", " + peerAddress + ")");
                try {
                    result.setResult(this.mISupplicantP2pIface.reinvoke(networkId, macAddress));
                } catch (RemoteException e) {
                    Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                    supplicantServiceDiedHandler();
                }
                return result.isSuccess();
            } catch (Exception e2) {
                Log.e(TAG, "Could not parse mac address.", e2);
                return false;
            }
        }
    }

    public boolean groupAdd(int networkId, boolean isPersistent) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("groupAdd")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("groupAdd(" + networkId + ", " + isPersistent + ")");
            try {
                result.setResult(this.mISupplicantP2pIface.addGroup(isPersistent, networkId));
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean groupAdd(String networkName, String passphrase, boolean isPersistent, int freq, String peerAddress, boolean join) {
        ArrayList<Byte> ssid;
        byte[] macAddress;
        android.hardware.wifi.supplicant.V1_2.ISupplicantP2pIface ifaceV12;
        StringBuilder sb;
        synchronized (this.mLock) {
            try {
                if (!checkSupplicantP2pIfaceAndLogFailureV1_2("groupAdd_1_2")) {
                    return false;
                }
                try {
                    ssid = NativeUtil.decodeSsid("\"" + networkName + "\"");
                    try {
                        macAddress = NativeUtil.macAddressToByteArray(peerAddress);
                    } catch (Exception e) {
                        Log.e(TAG, "Could not parse mac address.", e);
                        return false;
                    }
                    try {
                        ifaceV12 = getP2pIfaceMockableV1_2();
                        sb = new StringBuilder();
                        sb.append("groupAdd(");
                        sb.append(networkName);
                        sb.append(", ");
                        sb.append(TextUtils.isEmpty(passphrase) ? "<Empty>" : "<Non-Empty>");
                        sb.append(", ");
                    } catch (Throwable th) {
                        e = th;
                        throw e;
                    }
                } catch (IllegalArgumentException e2) {
                    Log.e(TAG, "Illegal argument " + ((Object) null), e2);
                    return false;
                }
                try {
                    sb.append(isPersistent);
                    sb.append(", ");
                    try {
                        sb.append(freq);
                        sb.append(", ");
                    } catch (Throwable th2) {
                        e = th2;
                        throw e;
                    }
                    try {
                        sb.append(peerAddress);
                        sb.append(", ");
                    } catch (Throwable th3) {
                        e = th3;
                        throw e;
                    }
                } catch (Throwable th4) {
                    e = th4;
                    throw e;
                }
                try {
                    sb.append(join);
                    sb.append(")");
                    SupplicantResult<Void> result = new SupplicantResult<>(sb.toString());
                    try {
                        result.setResult(ifaceV12.addGroup_1_2(ssid, passphrase, isPersistent, freq, macAddress, join));
                    } catch (RemoteException e3) {
                        Log.e(TAG, "ISupplicantP2pIface exception: " + e3);
                        supplicantServiceDiedHandler();
                    }
                    return result.isSuccess();
                } catch (Throwable th5) {
                    e = th5;
                    throw e;
                }
            } catch (Throwable th6) {
                e = th6;
                throw e;
            }
        }
    }

    public boolean groupAdd(boolean isPersistent) {
        return groupAdd(-1, isPersistent);
    }

    public boolean groupRemove(String groupName) {
        if (TextUtils.isEmpty(groupName)) {
            return false;
        }
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("groupRemove")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("groupRemove(" + groupName + ")");
            try {
                result.setResult(this.mISupplicantP2pIface.removeGroup(groupName));
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public int getGroupCapability(String peerAddress) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("getGroupCapability")) {
                return -1;
            }
            if (peerAddress == null) {
                Log.e(TAG, "Cannot parse peer mac address.");
                return -1;
            }
            try {
                byte[] macAddress = NativeUtil.macAddressToByteArray(peerAddress);
                SupplicantResult<Integer> capability = new SupplicantResult<>("getGroupCapability(" + peerAddress + ")");
                try {
                    this.mISupplicantP2pIface.getGroupCapability(macAddress, new ISupplicantP2pIface.getGroupCapabilityCallback() {
                        /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$HUk3uCtdErNouA5yKtXpoZFSJ8 */

                        @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantP2pIface.getGroupCapabilityCallback
                        public final void onValues(SupplicantStatus supplicantStatus, int i) {
                            SupplicantP2pIfaceHal.SupplicantResult.this.setResult(supplicantStatus, Integer.valueOf(i));
                        }
                    });
                } catch (RemoteException e) {
                    Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                    supplicantServiceDiedHandler();
                }
                if (!capability.isSuccess()) {
                    return -1;
                }
                return capability.getResult().intValue();
            } catch (Exception e2) {
                Log.e(TAG, "Could not parse group address.", e2);
                return -1;
            }
        }
    }

    public boolean configureExtListen(boolean enable, int periodInMillis, int intervalInMillis) {
        if (enable && intervalInMillis < periodInMillis) {
            return false;
        }
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("configureExtListen")) {
                return false;
            }
            if (!enable) {
                periodInMillis = 0;
                intervalInMillis = 0;
            }
            if (periodInMillis >= 0) {
                if (intervalInMillis >= 0) {
                    SupplicantResult<Void> result = new SupplicantResult<>("configureExtListen(" + periodInMillis + ", " + intervalInMillis + ")");
                    try {
                        result.setResult(this.mISupplicantP2pIface.configureExtListen(periodInMillis, intervalInMillis));
                    } catch (RemoteException e) {
                        Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                        supplicantServiceDiedHandler();
                    }
                    return result.isSuccess();
                }
            }
            Log.e(TAG, "Invalid parameters supplied to configureExtListen: " + periodInMillis + ", " + intervalInMillis);
            return false;
        }
    }

    public boolean setListenChannel(int listenChannel, int operatingChannel) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("setListenChannel")) {
                return false;
            }
            if (listenChannel >= 1 && listenChannel <= 11) {
                SupplicantResult<Void> result = new SupplicantResult<>("setListenChannel(" + listenChannel + ", " + 81 + ")");
                try {
                    result.setResult(this.mISupplicantP2pIface.setListenChannel(listenChannel, 81));
                } catch (RemoteException e) {
                    Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                    supplicantServiceDiedHandler();
                }
                if (!result.isSuccess()) {
                    return false;
                }
            } else if (listenChannel != 0) {
                return false;
            }
            if (operatingChannel < 0 || operatingChannel > 165) {
                return false;
            }
            ArrayList<ISupplicantP2pIface.FreqRange> ranges = new ArrayList<>();
            if (operatingChannel >= 1 && operatingChannel <= 165) {
                int freq = (operatingChannel <= 14 ? 2407 : 5000) + (operatingChannel * 5);
                ISupplicantP2pIface.FreqRange range1 = new ISupplicantP2pIface.FreqRange();
                range1.min = 1000;
                range1.max = freq - 5;
                ISupplicantP2pIface.FreqRange range2 = new ISupplicantP2pIface.FreqRange();
                range2.min = freq + 5;
                range2.max = WifiNetworkSelector.MINIMUM_NETWORK_SELECTION_INTERVAL_MS;
                ranges.add(range1);
                ranges.add(range2);
            }
            SupplicantResult<Void> result2 = new SupplicantResult<>("setDisallowedFrequencies(" + ranges + ")");
            try {
                result2.setResult(this.mISupplicantP2pIface.setDisallowedFrequencies(ranges));
            } catch (RemoteException e2) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e2);
                supplicantServiceDiedHandler();
            }
            return result2.isSuccess();
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:15:0x0028  */
    public boolean serviceAdd(WifiP2pServiceInfo servInfo) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("serviceAdd")) {
                return false;
            }
            if (servInfo == null) {
                Log.e(TAG, "Null service info passed.");
                return false;
            }
            for (String s : servInfo.getSupplicantQueryList()) {
                if (s == null) {
                    Log.e(TAG, "Invalid service description (null).");
                    return false;
                }
                String[] data = s.split(" ");
                if (data.length < 3) {
                    Log.e(TAG, "Service specification invalid: " + s);
                    return false;
                }
                SupplicantResult<Void> result = null;
                try {
                    if ("upnp".equals(data[0])) {
                        try {
                            int version = Integer.parseInt(data[1], 16);
                            result = new SupplicantResult<>("addUpnpService(" + data[1] + ", " + data[2] + ")");
                            result.setResult(this.mISupplicantP2pIface.addUpnpService(version, data[2]));
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "UPnP Service specification invalid: " + s, e);
                            return false;
                        }
                    } else if (!"bonjour".equals(data[0])) {
                        return false;
                    } else {
                        if (!(data[1] == null || data[2] == null)) {
                            try {
                                ArrayList<Byte> request = NativeUtil.byteArrayToArrayList(NativeUtil.hexStringToByteArray(data[1]));
                                ArrayList<Byte> response = NativeUtil.byteArrayToArrayList(NativeUtil.hexStringToByteArray(data[2]));
                                result = new SupplicantResult<>("addBonjourService(" + data[1] + ", " + data[2] + ")");
                                result.setResult(this.mISupplicantP2pIface.addBonjourService(request, response));
                            } catch (Exception e2) {
                                Log.e(TAG, "Invalid bonjour service description.");
                                return false;
                            }
                        }
                    }
                } catch (RemoteException e3) {
                    Log.e(TAG, "ISupplicantP2pIface exception: " + e3);
                    supplicantServiceDiedHandler();
                }
                if (result == null || !result.isSuccess()) {
                    return false;
                }
                while (r1.hasNext()) {
                }
            }
            return true;
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:15:0x0028  */
    public boolean serviceRemove(WifiP2pServiceInfo servInfo) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("serviceRemove")) {
                return false;
            }
            if (servInfo == null) {
                Log.e(TAG, "Null service info passed.");
                return false;
            }
            for (String s : servInfo.getSupplicantQueryList()) {
                if (s == null) {
                    Log.e(TAG, "Invalid service description (null).");
                    return false;
                }
                String[] data = s.split(" ");
                if (data.length < 3) {
                    Log.e(TAG, "Service specification invalid: " + s);
                    return false;
                }
                SupplicantResult<Void> result = null;
                try {
                    if ("upnp".equals(data[0])) {
                        try {
                            int version = Integer.parseInt(data[1], 16);
                            result = new SupplicantResult<>("removeUpnpService(" + data[1] + ", " + data[2] + ")");
                            result.setResult(this.mISupplicantP2pIface.removeUpnpService(version, data[2]));
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "UPnP Service specification invalid: " + s, e);
                            return false;
                        }
                    } else if (!"bonjour".equals(data[0])) {
                        Log.e(TAG, "Unknown / unsupported P2P service requested: " + data[0]);
                        return false;
                    } else if (data[1] != null) {
                        try {
                            ArrayList<Byte> request = NativeUtil.byteArrayToArrayList(NativeUtil.hexStringToByteArray(data[1]));
                            result = new SupplicantResult<>("removeBonjourService(" + data[1] + ")");
                            result.setResult(this.mISupplicantP2pIface.removeBonjourService(request));
                        } catch (Exception e2) {
                            Log.e(TAG, "Invalid bonjour service description.");
                            return false;
                        }
                    }
                } catch (RemoteException e3) {
                    Log.e(TAG, "ISupplicantP2pIface exception: " + e3);
                    supplicantServiceDiedHandler();
                }
                if (result == null || !result.isSuccess()) {
                    return false;
                }
                while (r1.hasNext()) {
                }
            }
            return true;
        }
    }

    public String requestServiceDiscovery(String peerAddress, String query) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("requestServiceDiscovery")) {
                return null;
            }
            if (peerAddress == null) {
                Log.e(TAG, "Cannot parse peer mac address.");
                return null;
            }
            try {
                byte[] macAddress = NativeUtil.macAddressToByteArray(peerAddress);
                if (query == null) {
                    Log.e(TAG, "Cannot parse service discovery query: " + query);
                    return null;
                }
                try {
                    ArrayList<Byte> binQuery = NativeUtil.byteArrayToArrayList(NativeUtil.hexStringToByteArray(query));
                    SupplicantResult<Long> result = new SupplicantResult<>("requestServiceDiscovery(" + peerAddress + ", " + query + ")");
                    try {
                        this.mISupplicantP2pIface.requestServiceDiscovery(macAddress, binQuery, new ISupplicantP2pIface.requestServiceDiscoveryCallback() {
                            /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$KvLZ3IvDN0_X_oOGvHkXQxDYFg */

                            @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantP2pIface.requestServiceDiscoveryCallback
                            public final void onValues(SupplicantStatus supplicantStatus, long j) {
                                SupplicantP2pIfaceHal.SupplicantResult.this.setResult(supplicantStatus, new Long(j));
                            }
                        });
                    } catch (RemoteException e) {
                        Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                        supplicantServiceDiedHandler();
                    }
                    Long value = result.getResult();
                    if (value == null) {
                        return null;
                    }
                    return value.toString();
                } catch (Exception e2) {
                    Log.e(TAG, "Could not parse service query.", e2);
                    return null;
                }
            } catch (Exception e3) {
                Log.e(TAG, "Could not process peer MAC address.", e3);
                return null;
            }
        }
    }

    public boolean cancelServiceDiscovery(String identifier) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("cancelServiceDiscovery")) {
                return false;
            }
            if (identifier == null) {
                Log.e(TAG, "cancelServiceDiscovery requires a valid tag.");
                return false;
            }
            try {
                long id = Long.parseLong(identifier);
                SupplicantResult<Void> result = new SupplicantResult<>("cancelServiceDiscovery(" + identifier + ")");
                try {
                    result.setResult(this.mISupplicantP2pIface.cancelServiceDiscovery(id));
                } catch (RemoteException e) {
                    Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                    supplicantServiceDiedHandler();
                }
                return result.isSuccess();
            } catch (NumberFormatException e2) {
                Log.e(TAG, "Service discovery identifier invalid: " + identifier, e2);
                return false;
            }
        }
    }

    public boolean setMiracastMode(int mode) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("setMiracastMode")) {
                return false;
            }
            byte targetMode = 0;
            if (mode == 1) {
                targetMode = 1;
            } else if (mode == 2) {
                targetMode = 2;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("setMiracastMode(" + mode + ")");
            try {
                result.setResult(this.mISupplicantP2pIface.setMiracastMode(targetMode));
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean startWpsPbc(String groupIfName, String bssid) {
        if (TextUtils.isEmpty(groupIfName)) {
            Log.e(TAG, "Group name required when requesting WPS PBC. Got (" + groupIfName + ")");
            return false;
        }
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("startWpsPbc")) {
                return false;
            }
            try {
                byte[] macAddress = NativeUtil.macAddressToByteArray(bssid);
                SupplicantResult<Void> result = new SupplicantResult<>("startWpsPbc(" + groupIfName + ", " + bssid + ")");
                try {
                    result.setResult(this.mISupplicantP2pIface.startWpsPbc(groupIfName, macAddress));
                } catch (RemoteException e) {
                    Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                    supplicantServiceDiedHandler();
                }
                return result.isSuccess();
            } catch (Exception e2) {
                Log.e(TAG, "Could not parse BSSID.", e2);
                return false;
            }
        }
    }

    public boolean startWpsPinKeypad(String groupIfName, String pin) {
        if (TextUtils.isEmpty(groupIfName) || TextUtils.isEmpty(pin)) {
            return false;
        }
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("startWpsPinKeypad")) {
                return false;
            }
            if (groupIfName == null) {
                Log.e(TAG, "Group name required when requesting WPS KEYPAD.");
                return false;
            } else if (pin == null) {
                Log.e(TAG, "PIN required when requesting WPS KEYPAD.");
                return false;
            } else {
                SupplicantResult<Void> result = new SupplicantResult<>("startWpsPinKeypad(" + groupIfName + ", " + pin + ")");
                try {
                    result.setResult(this.mISupplicantP2pIface.startWpsPinKeypad(groupIfName, pin));
                } catch (RemoteException e) {
                    Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                    supplicantServiceDiedHandler();
                }
                return result.isSuccess();
            }
        }
    }

    public String startWpsPinDisplay(String groupIfName, String bssid) {
        if (TextUtils.isEmpty(groupIfName)) {
            return null;
        }
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("startWpsPinDisplay")) {
                return null;
            }
            if (groupIfName == null) {
                Log.e(TAG, "Group name required when requesting WPS KEYPAD.");
                return null;
            }
            try {
                byte[] macAddress = NativeUtil.macAddressToByteArray(bssid);
                SupplicantResult<String> result = new SupplicantResult<>("startWpsPinDisplay(" + groupIfName + ", " + bssid + ")");
                try {
                    this.mISupplicantP2pIface.startWpsPinDisplay(groupIfName, macAddress, new ISupplicantP2pIface.startWpsPinDisplayCallback() {
                        /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$BUqZ73u9LLSUuprhzeXu0esCpFQ */

                        @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantP2pIface.startWpsPinDisplayCallback
                        public final void onValues(SupplicantStatus supplicantStatus, String str) {
                            SupplicantP2pIfaceHal.SupplicantResult.this.setResult(supplicantStatus, str);
                        }
                    });
                } catch (RemoteException e) {
                    Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                    supplicantServiceDiedHandler();
                }
                return result.getResult();
            } catch (Exception e2) {
                Log.e(TAG, "Could not parse BSSID.", e2);
                return null;
            }
        }
    }

    public boolean cancelWps(String groupIfName) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("cancelWps")) {
                return false;
            }
            if (groupIfName == null) {
                Log.e(TAG, "Group name required when requesting WPS KEYPAD.");
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("cancelWps(" + groupIfName + ")");
            try {
                result.setResult(this.mISupplicantP2pIface.cancelWps(groupIfName));
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean enableWfd(boolean enable) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("enableWfd")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("enableWfd(" + enable + ")");
            try {
                result.setResult(this.mISupplicantP2pIface.enableWfd(enable));
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean setWfdDeviceInfo(String info) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("setWfdDeviceInfo")) {
                return false;
            }
            if (info == null) {
                Log.e(TAG, "Cannot parse null WFD info string.");
                return false;
            }
            try {
                byte[] wfdInfo = NativeUtil.hexStringToByteArray(info);
                SupplicantResult<Void> result = new SupplicantResult<>("setWfdDeviceInfo(" + info + ")");
                try {
                    result.setResult(this.mISupplicantP2pIface.setWfdDeviceInfo(wfdInfo));
                } catch (RemoteException e) {
                    Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                    supplicantServiceDiedHandler();
                }
                return result.isSuccess();
            } catch (Exception e2) {
                Log.e(TAG, "Could not parse WFD Device Info string.");
                return false;
            }
        }
    }

    public boolean removeNetwork(int networkId) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("removeNetwork")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("removeNetwork(" + networkId + ")");
            try {
                result.setResult(this.mISupplicantP2pIface.removeNetwork(networkId));
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    private List<Integer> listNetworks() {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("listNetworks")) {
                return null;
            }
            SupplicantResult<ArrayList> result = new SupplicantResult<>("listNetworks()");
            try {
                this.mISupplicantP2pIface.listNetworks(new ISupplicantIface.listNetworksCallback() {
                    /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$TwrQS_pVojWA3Xh2Bx7vpwkTF9Y */

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantIface.listNetworksCallback
                    public final void onValues(SupplicantStatus supplicantStatus, ArrayList arrayList) {
                        SupplicantP2pIfaceHal.SupplicantResult.this.setResult(supplicantStatus, arrayList);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.getResult();
        }
    }

    private ISupplicantP2pNetwork getNetwork(int networkId) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("getNetwork")) {
                return null;
            }
            SupplicantResult<ISupplicantNetwork> result = new SupplicantResult<>("getNetwork(" + networkId + ")");
            try {
                this.mISupplicantP2pIface.getNetwork(networkId, new ISupplicantIface.getNetworkCallback() {
                    /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$okkQ9oGkLp2fSYGvkikikoDcbg0 */

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantIface.getNetworkCallback
                    public final void onValues(SupplicantStatus supplicantStatus, ISupplicantNetwork iSupplicantNetwork) {
                        SupplicantP2pIfaceHal.SupplicantResult.this.setResult(supplicantStatus, iSupplicantNetwork);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            if (result.getResult() == null) {
                Log.e(TAG, "getNetwork got null network");
                return null;
            }
            return getP2pNetworkMockable(result.getResult());
        }
    }

    public boolean loadGroups(WifiP2pGroupList groups) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("loadGroups")) {
                return false;
            }
            List<Integer> networkIds = listNetworks();
            if (networkIds != null) {
                if (!networkIds.isEmpty()) {
                    for (Integer networkId : networkIds) {
                        ISupplicantP2pNetwork network = getNetwork(networkId.intValue());
                        if (network == null) {
                            Log.e(TAG, "Failed to retrieve network object for " + networkId);
                        } else {
                            SupplicantResult<Boolean> resultIsCurrent = new SupplicantResult<>("isCurrent(" + networkId + ")");
                            try {
                                network.isCurrent(new ISupplicantP2pNetwork.isCurrentCallback() {
                                    /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$vSlDwIydbHAxLnMv4smDBNKVEY */

                                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantP2pNetwork.isCurrentCallback
                                    public final void onValues(SupplicantStatus supplicantStatus, boolean z) {
                                        SupplicantP2pIfaceHal.SupplicantResult.this.setResult(supplicantStatus, Boolean.valueOf(z));
                                    }
                                });
                            } catch (RemoteException e) {
                                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                                supplicantServiceDiedHandler();
                            }
                            if (!resultIsCurrent.isSuccess() || resultIsCurrent.getResult().booleanValue()) {
                                Log.i(TAG, "Skipping current network");
                            } else {
                                WifiP2pGroup group = new WifiP2pGroup();
                                group.setNetworkId(networkId.intValue());
                                SupplicantResult<ArrayList> resultSsid = new SupplicantResult<>("getSsid(" + networkId + ")");
                                try {
                                    network.getSsid(new ISupplicantP2pNetwork.getSsidCallback() {
                                        /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$z3QsDt3OVR74pijv7U0__gLRDRw */

                                        @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantP2pNetwork.getSsidCallback
                                        public final void onValues(SupplicantStatus supplicantStatus, ArrayList arrayList) {
                                            SupplicantP2pIfaceHal.SupplicantResult.this.setResult(supplicantStatus, arrayList);
                                        }
                                    });
                                } catch (RemoteException e2) {
                                    Log.e(TAG, "ISupplicantP2pIface exception: " + e2);
                                    supplicantServiceDiedHandler();
                                }
                                if (resultSsid.isSuccess() && resultSsid.getResult() != null && !resultSsid.getResult().isEmpty()) {
                                    group.setNetworkName(NativeUtil.removeEnclosingQuotes(NativeUtil.encodeSsid(resultSsid.getResult())));
                                }
                                SupplicantResult<byte[]> resultBssid = new SupplicantResult<>("getBssid(" + networkId + ")");
                                try {
                                    network.getBssid(new ISupplicantP2pNetwork.getBssidCallback() {
                                        /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$W9I8XWKojjFDG__d7SOjFO7RYI */

                                        @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantP2pNetwork.getBssidCallback
                                        public final void onValues(SupplicantStatus supplicantStatus, byte[] bArr) {
                                            SupplicantP2pIfaceHal.SupplicantResult.this.setResult(supplicantStatus, bArr);
                                        }
                                    });
                                } catch (RemoteException e3) {
                                    Log.e(TAG, "ISupplicantP2pIface exception: " + e3);
                                    supplicantServiceDiedHandler();
                                }
                                if (resultBssid.isSuccess() && !ArrayUtils.isEmpty(resultBssid.getResult())) {
                                    WifiP2pDevice device = new WifiP2pDevice();
                                    device.deviceAddress = NativeUtil.macAddressFromByteArray(resultBssid.getResult());
                                    group.setOwner(device);
                                }
                                SupplicantResult<Boolean> resultIsGo = new SupplicantResult<>("isGo(" + networkId + ")");
                                try {
                                    network.isGo(new ISupplicantP2pNetwork.isGoCallback() {
                                        /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$EPlVFTpX9SOSocJPBJMlC6hy3Q */

                                        @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantP2pNetwork.isGoCallback
                                        public final void onValues(SupplicantStatus supplicantStatus, boolean z) {
                                            SupplicantP2pIfaceHal.SupplicantResult.this.setResult(supplicantStatus, Boolean.valueOf(z));
                                        }
                                    });
                                } catch (RemoteException e4) {
                                    Log.e(TAG, "ISupplicantP2pIface exception: " + e4);
                                    supplicantServiceDiedHandler();
                                }
                                if (resultIsGo.isSuccess()) {
                                    group.setIsGroupOwner(resultIsGo.getResult().booleanValue());
                                }
                                groups.add(group);
                            }
                        }
                    }
                    return true;
                }
            }
            return false;
        }
    }

    public boolean setWpsDeviceName(String name) {
        if (name == null) {
            return false;
        }
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("setWpsDeviceName")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("setWpsDeviceName(" + name + ")");
            try {
                result.setResult(this.mISupplicantP2pIface.setWpsDeviceName(name));
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean setWpsDeviceType(String typeStr) {
        try {
            Matcher match = WPS_DEVICE_TYPE_PATTERN.matcher(typeStr);
            if (match.find()) {
                if (match.groupCount() == 3) {
                    short categ = Short.parseShort(match.group(1));
                    byte[] oui = NativeUtil.hexStringToByteArray(match.group(2));
                    short subCateg = Short.parseShort(match.group(3));
                    byte[] bytes = new byte[8];
                    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
                    byteBuffer.putShort(categ);
                    byteBuffer.put(oui);
                    byteBuffer.putShort(subCateg);
                    synchronized (this.mLock) {
                        if (!checkSupplicantP2pIfaceAndLogFailure("setWpsDeviceType")) {
                            return false;
                        }
                        SupplicantResult<Void> result = new SupplicantResult<>("setWpsDeviceType(" + typeStr + ")");
                        try {
                            result.setResult(this.mISupplicantP2pIface.setWpsDeviceType(bytes));
                        } catch (RemoteException e) {
                            Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                            supplicantServiceDiedHandler();
                        }
                        return result.isSuccess();
                    }
                }
            }
            Log.e(TAG, "Malformed WPS device type " + typeStr);
            return false;
        } catch (IllegalArgumentException e2) {
            Log.e(TAG, "Illegal argument " + typeStr, e2);
            return false;
        }
    }

    public boolean setWpsConfigMethods(String configMethodsStr) {
        String[] configMethodsStrArr;
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("setWpsConfigMethods")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("setWpsConfigMethods(" + configMethodsStr + ")");
            short configMethodsMask = 0;
            for (String str : configMethodsStr.split("\\s+")) {
                configMethodsMask = (short) (stringToWpsConfigMethod(str) | configMethodsMask);
            }
            try {
                result.setResult(this.mISupplicantP2pIface.setWpsConfigMethods(configMethodsMask));
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public String getNfcHandoverRequest() {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("getNfcHandoverRequest")) {
                return null;
            }
            SupplicantResult<ArrayList> result = new SupplicantResult<>("getNfcHandoverRequest()");
            try {
                this.mISupplicantP2pIface.createNfcHandoverRequestMessage(new ISupplicantP2pIface.createNfcHandoverRequestMessageCallback() {
                    /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$n1LlLEHGzyC0D6yPoNdbIdzappg */

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantP2pIface.createNfcHandoverRequestMessageCallback
                    public final void onValues(SupplicantStatus supplicantStatus, ArrayList arrayList) {
                        SupplicantP2pIfaceHal.SupplicantResult.this.setResult(supplicantStatus, arrayList);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            if (!result.isSuccess()) {
                return null;
            }
            return NativeUtil.hexStringFromByteArray(NativeUtil.byteArrayFromArrayList(result.getResult()));
        }
    }

    public String getNfcHandoverSelect() {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("getNfcHandoverSelect")) {
                return null;
            }
            SupplicantResult<ArrayList> result = new SupplicantResult<>("getNfcHandoverSelect()");
            try {
                this.mISupplicantP2pIface.createNfcHandoverSelectMessage(new ISupplicantP2pIface.createNfcHandoverSelectMessageCallback() {
                    /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$TKoHcxWgs1kiEeHACYHFcslSVB8 */

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantP2pIface.createNfcHandoverSelectMessageCallback
                    public final void onValues(SupplicantStatus supplicantStatus, ArrayList arrayList) {
                        SupplicantP2pIfaceHal.SupplicantResult.this.setResult(supplicantStatus, arrayList);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            if (!result.isSuccess()) {
                return null;
            }
            return NativeUtil.hexStringFromByteArray(NativeUtil.byteArrayFromArrayList(result.getResult()));
        }
    }

    public boolean initiatorReportNfcHandover(String selectMessage) {
        if (selectMessage == null) {
            return false;
        }
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("initiatorReportNfcHandover")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("initiatorReportNfcHandover(" + selectMessage + ")");
            try {
                result.setResult(this.mISupplicantP2pIface.reportNfcHandoverInitiation(NativeUtil.byteArrayToArrayList(NativeUtil.hexStringToByteArray(selectMessage))));
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            } catch (IllegalArgumentException e2) {
                Log.e(TAG, "Illegal argument " + selectMessage, e2);
                return false;
            }
            return result.isSuccess();
        }
    }

    public boolean responderReportNfcHandover(String requestMessage) {
        if (requestMessage == null) {
            return false;
        }
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("responderReportNfcHandover")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("responderReportNfcHandover(" + requestMessage + ")");
            try {
                result.setResult(this.mISupplicantP2pIface.reportNfcHandoverResponse(NativeUtil.byteArrayToArrayList(NativeUtil.hexStringToByteArray(requestMessage))));
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            } catch (IllegalArgumentException e2) {
                Log.e(TAG, "Illegal argument " + requestMessage, e2);
                return false;
            }
            return result.isSuccess();
        }
    }

    public boolean setClientList(int networkId, String clientListStr) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("setClientList")) {
                return false;
            }
            if (TextUtils.isEmpty(clientListStr)) {
                Log.e(TAG, "Invalid client list");
                return false;
            }
            ISupplicantP2pNetwork network = getNetwork(networkId);
            if (network == null) {
                Log.e(TAG, "Invalid network id ");
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("setClientList(" + networkId + ", " + clientListStr + ")");
            try {
                ArrayList<byte[]> clients = new ArrayList<>();
                for (String clientStr : Arrays.asList(clientListStr.split("\\s+"))) {
                    clients.add(NativeUtil.macAddressToByteArray(clientStr));
                }
                result.setResult(network.setClientList(clients));
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            } catch (IllegalArgumentException e2) {
                Log.e(TAG, "Illegal argument " + clientListStr, e2);
                return false;
            }
            return result.isSuccess();
        }
    }

    public String getClientList(int networkId) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("getClientList")) {
                return null;
            }
            ISupplicantP2pNetwork network = getNetwork(networkId);
            if (network == null) {
                Log.e(TAG, "Invalid network id ");
                return null;
            }
            SupplicantResult<ArrayList> result = new SupplicantResult<>("getClientList(" + networkId + ")");
            try {
                network.getClientList(new ISupplicantP2pNetwork.getClientListCallback() {
                    /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$E8InkdRqAkkB4UeMEephTeoxv4M */

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantP2pNetwork.getClientListCallback
                    public final void onValues(SupplicantStatus supplicantStatus, ArrayList arrayList) {
                        SupplicantP2pIfaceHal.SupplicantResult.this.setResult(supplicantStatus, arrayList);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            if (!result.isSuccess()) {
                return null;
            }
            return (String) result.getResult().stream().map($$Lambda$22Qhg7RQJlXihi83tqGgsfFMs.INSTANCE).collect(Collectors.joining(" "));
        }
    }

    public boolean saveConfig() {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("saveConfig")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("saveConfig()");
            try {
                result.setResult(this.mISupplicantP2pIface.saveConfig());
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean setMacRandomization(boolean enable) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailureV1_2("setMacRandomization")) {
                return false;
            }
            android.hardware.wifi.supplicant.V1_2.ISupplicantP2pIface ifaceV12 = getP2pIfaceMockableV1_2();
            if (ifaceV12 == null) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("setMacRandomization(" + enable + ")");
            try {
                result.setResult(ifaceV12.setMacRandomization(enable));
            } catch (RemoteException e) {
                Log.e(TAG, "ISupplicantP2pIface exception: " + e);
                supplicantServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean removePersistentGroups() {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceAndLogFailure("loadGroups")) {
                return false;
            }
            List<Integer> networkIds = listNetworks();
            if (networkIds != null) {
                if (!networkIds.isEmpty()) {
                    for (Integer networkId : networkIds) {
                        if (!removeNetwork(networkId.intValue())) {
                            Log.e(TAG, "Failed to remove network object for " + networkId);
                        }
                    }
                    return true;
                }
            }
            return false;
        }
    }

    public boolean find(int timeout, int channelNum) {
        synchronized (this.mLock) {
            if (checkSupplicantP2pIfaceAndLogFailure("find")) {
                if (checkSupplicantP2pIfaceExtAndLogFailure("find")) {
                    if (timeout < 0) {
                        Log.e(TAG, "Invalid timeout value: " + timeout);
                        return false;
                    }
                    SupplicantResult<Void> result = new SupplicantResult<>("find(" + timeout + ", " + channelNum + ")");
                    if (channelNum == 0) {
                        try {
                            result.setResult(this.mISupplicantP2pIface.find(timeout));
                        } catch (RemoteException e) {
                            Log.e(TAG, "ISehSupplicantP2pIface exception: " + e);
                            supplicantExtServiceDiedHandler();
                        }
                    } else if (channelNum == 1611) {
                        this.mISupplicantP2pIface.flush();
                        result.setResult(this.mISehSupplicantP2pIface.sehFindSocialChannel());
                    } else {
                        if (!(channelNum == 1000 || channelNum == 6000)) {
                            if (channelNum != 11000) {
                                if (channelNum > 0 && channelNum <= 169) {
                                    this.mISupplicantP2pIface.flush();
                                    result.setResult(this.mISehSupplicantP2pIface.sehFindChannel(channelNum));
                                } else if (channelNum == -999) {
                                    this.mISupplicantP2pIface.flush();
                                    result.setResult(this.mISehSupplicantP2pIface.sehFindGoOperationFrequency(timeout));
                                } else if (channelNum != 99999) {
                                    return false;
                                } else {
                                    result.setResult(this.mISehSupplicantP2pIface.sehSearch(timeout));
                                }
                            }
                        }
                        result.setResult(this.mISehSupplicantP2pIface.sehFindChannel(channelNum / 1000));
                    }
                    return result.isSuccess();
                }
            }
            return false;
        }
    }

    public boolean groupAdd(boolean isPersistent, int freq) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceExtAndLogFailure("groupAdd")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("groupAdd(" + isPersistent + ", " + freq + ")");
            try {
                result.setResult(this.mISehSupplicantP2pIface.sehAddGroupSpecificFrequency(isPersistent, freq));
            } catch (RemoteException e) {
                Log.e(TAG, "ISehSupplicantP2pIface exception: " + e);
                supplicantExtServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean p2pRemoveClient(String peerAddress, boolean isIfaceAddr) {
        if (TextUtils.isEmpty(peerAddress)) {
            return false;
        }
        if (peerAddress == null) {
            Log.e(TAG, "Cannot parse peer mac address.");
            return false;
        }
        try {
            byte[] peerMacAddress = NativeUtil.macAddressToByteArray(peerAddress);
            synchronized (this.mLock) {
                if (!checkSupplicantP2pIfaceExtAndLogFailure("p2pRemoveClient")) {
                    return false;
                }
                SupplicantResult<Void> result = new SupplicantResult<>("p2pRemoveClient(" + peerAddress + ", " + isIfaceAddr + ")");
                try {
                    result.setResult(this.mISehSupplicantP2pIface.sehRemoveClient(peerMacAddress, isIfaceAddr));
                } catch (RemoteException e) {
                    Log.e(TAG, "ISehSupplicantP2pIface exception: " + e);
                    supplicantExtServiceDiedHandler();
                }
                return result.isSuccess();
            }
        } catch (Exception e2) {
            Log.e(TAG, "Peer mac address parse error.", e2);
            return false;
        }
    }

    public boolean p2pListen(int timeout) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceExtAndLogFailure("p2pListen")) {
                return false;
            }
            if (timeout < 0) {
                Log.e(TAG, "Invalid timeout value: " + timeout);
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("p2pListen(" + timeout + ")");
            try {
                result.setResult(this.mISehSupplicantP2pIface.sehListen(timeout));
            } catch (RemoteException e) {
                Log.e(TAG, "ISehSupplicantP2pIface exception: " + e);
                supplicantExtServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean p2pSet(String cmd, String param) {
        if (TextUtils.isEmpty(cmd) || TextUtils.isEmpty(param)) {
            return false;
        }
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceExtAndLogFailure("p2pSet")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("p2pSet(" + cmd + ", " + param + ")");
            try {
                result.setResult(this.mISehSupplicantP2pIface.sehSendCommand(cmd, param));
            } catch (RemoteException e) {
                Log.e(TAG, "ISehSupplicantP2pIface exception: " + e);
                supplicantExtServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public String p2pGetAttribute(String element, String peerAddress) {
        if (TextUtils.isEmpty(element) || TextUtils.isEmpty(peerAddress)) {
            return null;
        }
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceExtAndLogFailure("p2pGetAttribute")) {
                return null;
            }
            SupplicantResult<String> result = new SupplicantResult<>("p2pGetAttribute(" + element + ", " + peerAddress + ")");
            try {
                try {
                    this.mISehSupplicantP2pIface.sehGetPeerAttribute(element, NativeUtil.macAddressToByteArray(peerAddress), new ISehSupplicantP2pIface.sehGetPeerAttributeCallback() {
                        /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$0dehVRIJ3IT61FTHAhLEsylEV3k */

                        @Override // vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicantP2pIface.sehGetPeerAttributeCallback
                        public final void onValues(SupplicantStatus supplicantStatus, String str) {
                            SupplicantP2pIfaceHal.SupplicantResult.this.setResult(supplicantStatus, str);
                        }
                    });
                } catch (RemoteException e) {
                    Log.e(TAG, "ISehSupplicantP2pIface exception: " + e);
                    supplicantExtServiceDiedHandler();
                }
                if (!result.isSuccess()) {
                    return null;
                }
                return result.getResult();
            } catch (Exception e2) {
                Log.e(TAG, "Could not parse peer address.", e2);
                return null;
            }
        }
    }

    public String p2pGetPeerInfo(String peerAddress) {
        if (TextUtils.isEmpty(peerAddress)) {
            return null;
        }
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceExtAndLogFailure("p2pGetPeerInfo")) {
                return null;
            }
            SupplicantResult<String> result = new SupplicantResult<>("p2pGetPeerInfo(" + peerAddress + ")");
            try {
                this.mISehSupplicantP2pIface.sehGetPeerInfo(peerAddress, new ISehSupplicantP2pIface.sehGetPeerInfoCallback() {
                    /* class com.android.server.wifi.p2p.$$Lambda$SupplicantP2pIfaceHal$bGxyYhd8lKaxhzpgOXbCisbsLmU */

                    @Override // vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicantP2pIface.sehGetPeerInfoCallback
                    public final void onValues(SupplicantStatus supplicantStatus, String str) {
                        SupplicantP2pIfaceHal.SupplicantResult.this.setResult(supplicantStatus, str);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "ISehSupplicantP2pIface exception: " + e);
                supplicantExtServiceDiedHandler();
            }
            return result.getResult();
        }
    }

    public boolean startP2pListenOffloading(int channel, int period, int interval, int count) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceExtAndLogFailure("startP2pListenOffloading")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("startP2pListenOffloading(" + channel + ", " + period + ", " + interval + ", " + count + ")");
            try {
                result.setResult(this.mISehSupplicantP2pIface.sehStartListenOffloading(channel, period, interval, count));
            } catch (RemoteException e) {
                Log.e(TAG, "ISehSupplicantP2pIface exception: " + e);
                supplicantExtServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean stopP2pListenOffloading() {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceExtAndLogFailure("stopP2pListenOffloading")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("stopP2pListenOffloading()");
            try {
                result.setResult(this.mISehSupplicantP2pIface.sehStopListenOffloading());
            } catch (RemoteException e) {
                Log.e(TAG, "ISehSupplicantP2pIface exception: " + e);
                supplicantExtServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean setP2pNoa(String groupIfName, boolean enable, int noa_dur) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceExtAndLogFailure("setP2pNoa")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("setP2pNoa(" + groupIfName + ", " + enable + ", " + noa_dur + ")");
            try {
                result.setResult(this.mISehSupplicantP2pIface.sehSetNoa(groupIfName, enable, noa_dur));
            } catch (RemoteException e) {
                Log.e(TAG, "ISehSupplicantP2pIface exception: " + e);
                supplicantExtServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    public boolean setP2pIncBw(String groupIfName, boolean enable, int inc_bw) {
        synchronized (this.mLock) {
            if (!checkSupplicantP2pIfaceExtAndLogFailure("setP2pIncBw")) {
                return false;
            }
            SupplicantResult<Void> result = new SupplicantResult<>("setP2pIncBw(" + groupIfName + ", " + enable + ", " + inc_bw + ")");
            try {
                result.setResult(this.mISehSupplicantP2pIface.sehSetNoaBandwidth(groupIfName, enable, inc_bw));
            } catch (RemoteException e) {
                Log.e(TAG, "ISehSupplicantP2pIface exception: " + e);
                supplicantExtServiceDiedHandler();
            }
            return result.isSuccess();
        }
    }

    /* JADX INFO: Can't fix incorrect switch cases order, some code will duplicate */
    private static short stringToWpsConfigMethod(String configMethod) {
        char c;
        switch (configMethod.hashCode()) {
            case -1781962557:
                if (configMethod.equals("virtual_push_button")) {
                    c = '\t';
                    break;
                }
                c = 65535;
                break;
            case -1419358249:
                if (configMethod.equals("ethernet")) {
                    c = 1;
                    break;
                }
                c = 65535;
                break;
            case -1134657068:
                if (configMethod.equals("keypad")) {
                    c = '\b';
                    break;
                }
                c = 65535;
                break;
            case -614489202:
                if (configMethod.equals("virtual_display")) {
                    c = '\f';
                    break;
                }
                c = 65535;
                break;
            case -522593958:
                if (configMethod.equals("physical_display")) {
                    c = '\r';
                    break;
                }
                c = 65535;
                break;
            case -423872603:
                if (configMethod.equals("nfc_interface")) {
                    c = 6;
                    break;
                }
                c = 65535;
                break;
            case -416734217:
                if (configMethod.equals("push_button")) {
                    c = 7;
                    break;
                }
                c = 65535;
                break;
            case 3388229:
                if (configMethod.equals("p2ps")) {
                    c = 11;
                    break;
                }
                c = 65535;
                break;
            case 3599197:
                if (configMethod.equals("usba")) {
                    c = 0;
                    break;
                }
                c = 65535;
                break;
            case 102727412:
                if (configMethod.equals("label")) {
                    c = 2;
                    break;
                }
                c = 65535;
                break;
            case 179612103:
                if (configMethod.equals("ext_nfc_token")) {
                    c = 5;
                    break;
                }
                c = 65535;
                break;
            case 1146869903:
                if (configMethod.equals("physical_push_button")) {
                    c = '\n';
                    break;
                }
                c = 65535;
                break;
            case 1671764162:
                if (configMethod.equals("display")) {
                    c = 3;
                    break;
                }
                c = 65535;
                break;
            case 2010140181:
                if (configMethod.equals("int_nfc_token")) {
                    c = 4;
                    break;
                }
                c = 65535;
                break;
            default:
                c = 65535;
                break;
        }
        switch (c) {
            case 0:
                return 1;
            case 1:
                return 2;
            case 2:
                return 4;
            case 3:
                return 8;
            case 4:
                return 32;
            case 5:
                return 16;
            case 6:
                return 64;
            case 7:
                return WpsConfigMethods.PUSHBUTTON;
            case '\b':
                return 256;
            case '\t':
                return WpsConfigMethods.VIRT_PUSHBUTTON;
            case '\n':
                return WpsConfigMethods.PHY_PUSHBUTTON;
            case 11:
                return WpsConfigMethods.P2PS;
            case '\f':
                return WpsConfigMethods.VIRT_DISPLAY;
            case '\r':
                return WpsConfigMethods.PHY_DISPLAY;
            default:
                throw new IllegalArgumentException("Invalid WPS config method: " + configMethod);
        }
    }

    /* access modifiers changed from: private */
    public static class SupplicantResult<E> {
        private String mMethodName;
        private SupplicantStatus mStatus = null;
        private E mValue = null;

        SupplicantResult(String methodName) {
            this.mMethodName = methodName;
            SupplicantP2pIfaceHal.logd("entering " + this.mMethodName);
        }

        public void setResult(SupplicantStatus status, E value) {
            SupplicantP2pIfaceHal.logCompletion(this.mMethodName, status);
            SupplicantP2pIfaceHal.logd("leaving " + this.mMethodName + " with result = " + ((Object) value));
            this.mStatus = status;
            this.mValue = value;
        }

        public void setResult(SupplicantStatus status) {
            SupplicantP2pIfaceHal.logCompletion(this.mMethodName, status);
            SupplicantP2pIfaceHal.logd("leaving " + this.mMethodName);
            this.mStatus = status;
        }

        public boolean isSuccess() {
            SupplicantStatus supplicantStatus = this.mStatus;
            return supplicantStatus != null && (supplicantStatus.code == 0 || this.mStatus.code == 5);
        }

        public E getResult() {
            if (isSuccess()) {
                return this.mValue;
            }
            return null;
        }
    }
}

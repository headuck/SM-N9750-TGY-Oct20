package com.android.server.wifi;

import android.hardware.wifi.V1_0.IWifi;
import android.hardware.wifi.V1_0.IWifiApIface;
import android.hardware.wifi.V1_0.IWifiChip;
import android.hardware.wifi.V1_0.IWifiChipEventCallback;
import android.hardware.wifi.V1_0.IWifiEventCallback;
import android.hardware.wifi.V1_0.IWifiIface;
import android.hardware.wifi.V1_0.IWifiNanIface;
import android.hardware.wifi.V1_0.IWifiP2pIface;
import android.hardware.wifi.V1_0.IWifiRttController;
import android.hardware.wifi.V1_0.IWifiStaIface;
import android.hardware.wifi.V1_0.WifiDebugRingBufferStatus;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hidl.manager.V1_0.IServiceNotification;
import android.hidl.manager.V1_2.IServiceManager;
import android.os.Handler;
import android.os.HidlSupport;
import android.os.IHwBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.MutableBoolean;
import android.util.MutableInt;
import android.util.Pair;
import android.util.SparseArray;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.HalDeviceManager;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vendor.samsung.hardware.wifi.V2_0.ISehWifi;

public class HalDeviceManager {
    private static final int[] IFACE_TYPES_BY_PRIORITY = {1, 0, 2, 3};
    private static final int START_HAL_RETRY_INTERVAL_MS = 20;
    @VisibleForTesting
    public static final int START_HAL_RETRY_TIMES = 3;
    private static final String TAG = "HalDevMgr";
    private static final boolean VDBG = true;
    private final Clock mClock;
    private boolean mDbg = true;
    private final SparseArray<IWifiChipEventCallback.Stub> mDebugCallbacks = new SparseArray<>();
    private final IHwBinder.DeathRecipient mISehWifiDeathRecipient = new IHwBinder.DeathRecipient() {
        /* class com.android.server.wifi.$$Lambda$HalDeviceManager$noScTs3Ynk8rNxP5lvUv8ww_gg4 */

        public final void serviceDied(long j) {
            HalDeviceManager.this.lambda$new$3$HalDeviceManager(j);
        }
    };
    private final IHwBinder.DeathRecipient mIWifiDeathRecipient = new IHwBinder.DeathRecipient() {
        /* class com.android.server.wifi.$$Lambda$HalDeviceManager$jNAzj5YlVhwJm5NjZ6HiKskQStI */

        public final void serviceDied(long j) {
            HalDeviceManager.this.lambda$new$2$HalDeviceManager(j);
        }
    };
    private IWifiRttController mIWifiRttController;
    private final SparseArray<Map<InterfaceAvailableForRequestListenerProxy, Boolean>> mInterfaceAvailableForRequestListeners = new SparseArray<>();
    private final Map<Pair<String, Integer>, InterfaceCacheEntry> mInterfaceInfoCache = new HashMap();
    private boolean mIsReady;
    private boolean mIsVendorHalSupported = false;
    private final Object mLock = new Object();
    private final Set<ManagerStatusListenerProxy> mManagerStatusListeners = new HashSet();
    private final Set<InterfaceRttControllerLifecycleCallbackProxy> mRttControllerLifecycleCallbacks = new HashSet();
    private ISehWifi mSehWifi;
    private IServiceManager mServiceManager;
    private final IHwBinder.DeathRecipient mServiceManagerDeathRecipient = new IHwBinder.DeathRecipient() {
        /* class com.android.server.wifi.$$Lambda$HalDeviceManager$SeJCUxQL5U06WtkK8XwQet85g */

        public final void serviceDied(long j) {
            HalDeviceManager.this.lambda$new$1$HalDeviceManager(j);
        }
    };
    private final IServiceNotification mServiceNotificationCallback = new IServiceNotification.Stub() {
        /* class com.android.server.wifi.HalDeviceManager.C03891 */

        public void onRegistration(String fqName, String name, boolean preexisting) {
            Log.d(HalDeviceManager.TAG, "IWifi registration notification: fqName=" + fqName + ", name=" + name + ", preexisting=" + preexisting);
            synchronized (HalDeviceManager.this.mLock) {
                HalDeviceManager.this.initIWifiIfNecessary();
                HalDeviceManager.this.initISehWifiIfNeccessary();
            }
        }
    };
    private IWifi mWifi;
    private final WifiEventCallback mWifiEventCallback = new WifiEventCallback();

    public interface InterfaceAvailableForRequestListener {
        void onAvailabilityChanged(boolean z);
    }

    public interface InterfaceDestroyedListener {
        void onDestroyed(String str);
    }

    public interface InterfaceRttControllerLifecycleCallback {
        void onNewRttController(IWifiRttController iWifiRttController);

        void onRttControllerDestroyed();
    }

    public interface ManagerStatusListener {
        void onStatusChanged();
    }

    public HalDeviceManager(Clock clock) {
        this.mClock = clock;
        this.mInterfaceAvailableForRequestListeners.put(0, new HashMap());
        this.mInterfaceAvailableForRequestListeners.put(1, new HashMap());
        this.mInterfaceAvailableForRequestListeners.put(2, new HashMap());
        this.mInterfaceAvailableForRequestListeners.put(3, new HashMap());
    }

    /* access modifiers changed from: package-private */
    public void enableVerboseLogging(int verbose) {
        if (verbose > 0) {
            this.mDbg = true;
        } else {
            this.mDbg = false;
        }
        this.mDbg = true;
    }

    public void initialize() {
        initializeInternal();
    }

    public void registerStatusListener(ManagerStatusListener listener, Handler handler) {
        synchronized (this.mLock) {
            if (!this.mManagerStatusListeners.add(new ManagerStatusListenerProxy(listener, handler))) {
                Log.w(TAG, "registerStatusListener: duplicate registration ignored");
            }
        }
    }

    public boolean isSupported() {
        return this.mIsVendorHalSupported;
    }

    public boolean isReady() {
        return this.mIsReady;
    }

    public boolean isStarted() {
        return isWifiStarted();
    }

    public boolean start() {
        return startWifi();
    }

    public void stop() {
        stopWifi();
        this.mWifi = null;
    }

    public Set<Integer> getSupportedIfaceTypes() {
        return getSupportedIfaceTypesInternal(null);
    }

    public Set<Integer> getSupportedIfaceTypes(IWifiChip chip) {
        return getSupportedIfaceTypesInternal(chip);
    }

    public IWifiStaIface createStaIface(boolean lowPrioritySta, InterfaceDestroyedListener destroyedListener, Handler handler) {
        return (IWifiStaIface) createIface(0, lowPrioritySta, destroyedListener, handler);
    }

    public IWifiApIface createApIface(InterfaceDestroyedListener destroyedListener, Handler handler) {
        return (IWifiApIface) createIface(1, false, destroyedListener, handler);
    }

    public IWifiP2pIface createP2pIface(InterfaceDestroyedListener destroyedListener, Handler handler) {
        return (IWifiP2pIface) createIface(2, false, destroyedListener, handler);
    }

    public IWifiNanIface createNanIface(InterfaceDestroyedListener destroyedListener, Handler handler) {
        return (IWifiNanIface) createIface(3, false, destroyedListener, handler);
    }

    public boolean removeIface(IWifiIface iface) {
        boolean success = removeIfaceInternal(iface);
        dispatchAvailableForRequestListeners();
        return success;
    }

    public IWifiChip getChip(IWifiIface iface) {
        String name = getName(iface);
        int type = getType(iface);
        Log.d(TAG, "getChip: iface(name)=" + name);
        synchronized (this.mLock) {
            InterfaceCacheEntry cacheEntry = this.mInterfaceInfoCache.get(Pair.create(name, Integer.valueOf(type)));
            if (cacheEntry == null) {
                Log.e(TAG, "getChip: no entry for iface(name)=" + name);
                return null;
            }
            return cacheEntry.chip;
        }
    }

    public boolean registerDestroyedListener(IWifiIface iface, InterfaceDestroyedListener destroyedListener, Handler handler) {
        String name = getName(iface);
        int type = getType(iface);
        Log.d(TAG, "registerDestroyedListener: iface(name)=" + name);
        synchronized (this.mLock) {
            InterfaceCacheEntry cacheEntry = this.mInterfaceInfoCache.get(Pair.create(name, Integer.valueOf(type)));
            if (cacheEntry == null) {
                Log.e(TAG, "registerDestroyedListener: no entry for iface(name)=" + name);
                return false;
            }
            return cacheEntry.destroyedListeners.add(new InterfaceDestroyedListenerProxy(name, destroyedListener, handler));
        }
    }

    /* JADX WARNING: Code restructure failed: missing block: B:10:0x0062, code lost:
        r0 = getAllChipInfo();
     */
    /* JADX WARNING: Code restructure failed: missing block: B:11:0x0066, code lost:
        if (r0 != null) goto L_0x0070;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:12:0x0068, code lost:
        android.util.Log.e(com.android.server.wifi.HalDeviceManager.TAG, "registerInterfaceAvailableForRequestListener: no chip info found - but possibly registered pre-started - ignoring");
     */
    /* JADX WARNING: Code restructure failed: missing block: B:13:0x006f, code lost:
        return;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:14:0x0070, code lost:
        dispatchAvailableForRequestListenersForType(r6, r0);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:15:0x0073, code lost:
        return;
     */
    public void registerInterfaceAvailableForRequestListener(int ifaceType, InterfaceAvailableForRequestListener listener, Handler handler) {
        Log.d(TAG, "registerInterfaceAvailableForRequestListener: ifaceType=" + ifaceType + ", listener=" + listener + ", handler=" + handler);
        synchronized (this.mLock) {
            InterfaceAvailableForRequestListenerProxy proxy = new InterfaceAvailableForRequestListenerProxy(listener, handler);
            if (this.mInterfaceAvailableForRequestListeners.get(ifaceType).containsKey(proxy)) {
                Log.d(TAG, "registerInterfaceAvailableForRequestListener: dup listener skipped: " + listener);
                return;
            }
            this.mInterfaceAvailableForRequestListeners.get(ifaceType).put(proxy, null);
        }
    }

    public void unregisterInterfaceAvailableForRequestListener(int ifaceType, InterfaceAvailableForRequestListener listener) {
        Log.d(TAG, "unregisterInterfaceAvailableForRequestListener: ifaceType=" + ifaceType);
        synchronized (this.mLock) {
            this.mInterfaceAvailableForRequestListeners.get(ifaceType).remove(new InterfaceAvailableForRequestListenerProxy(listener, null));
        }
    }

    public void registerRttControllerLifecycleCallback(InterfaceRttControllerLifecycleCallback callback, Handler handler) {
        Log.d(TAG, "registerRttControllerLifecycleCallback: callback=" + callback + ", handler=" + handler);
        if (callback == null || handler == null) {
            Log.wtf(TAG, "registerRttControllerLifecycleCallback with nulls!? callback=" + callback + ", handler=" + handler);
            return;
        }
        synchronized (this.mLock) {
            InterfaceRttControllerLifecycleCallbackProxy proxy = new InterfaceRttControllerLifecycleCallbackProxy(callback, handler);
            if (!this.mRttControllerLifecycleCallbacks.add(proxy)) {
                Log.d(TAG, "registerRttControllerLifecycleCallback: registering an existing callback=" + callback);
                return;
            }
            if (this.mIWifiRttController == null) {
                this.mIWifiRttController = createRttControllerIfPossible();
            }
            if (this.mIWifiRttController != null) {
                proxy.onNewRttController(this.mIWifiRttController);
            }
        }
    }

    public static String getName(IWifiIface iface) {
        if (iface == null) {
            return "<null>";
        }
        HidlSupport.Mutable<String> nameResp = new HidlSupport.Mutable<>();
        try {
            iface.getName(new IWifiIface.getNameCallback(nameResp) {
                /* class com.android.server.wifi.$$Lambda$HalDeviceManager$bTmsDoAj9faJCBOTeT1Q3Ww5yNM */
                private final /* synthetic */ HidlSupport.Mutable f$0;

                {
                    this.f$0 = r1;
                }

                @Override // android.hardware.wifi.V1_0.IWifiIface.getNameCallback
                public final void onValues(WifiStatus wifiStatus, String str) {
                    HalDeviceManager.lambda$getName$0(this.f$0, wifiStatus, str);
                }
            });
        } catch (RemoteException e) {
            Log.e(TAG, "Exception on getName: " + e);
        }
        return (String) nameResp.value;
    }

    static /* synthetic */ void lambda$getName$0(HidlSupport.Mutable nameResp, WifiStatus status, String name) {
        if (status.code == 0) {
            nameResp.value = name;
            return;
        }
        Log.e(TAG, "Error on getName: " + statusString(status));
    }

    /* access modifiers changed from: private */
    public class InterfaceCacheEntry {
        public IWifiChip chip;
        public int chipId;
        public long creationTime;
        public Set<InterfaceDestroyedListenerProxy> destroyedListeners;
        public boolean isLowPriority;
        public String name;
        public int type;

        private InterfaceCacheEntry() {
            this.destroyedListeners = new HashSet();
        }

        public String toString() {
            return "{name=" + this.name + ", type=" + this.type + ", destroyedListeners.size()=" + this.destroyedListeners.size() + ", creationTime=" + this.creationTime + ", isLowPriority=" + this.isLowPriority + "}";
        }
    }

    /* access modifiers changed from: private */
    public class WifiIfaceInfo {
        public IWifiIface iface;
        public String name;

        private WifiIfaceInfo() {
        }
    }

    /* access modifiers changed from: private */
    public class WifiChipInfo {
        public ArrayList<IWifiChip.ChipMode> availableModes;
        public IWifiChip chip;
        public int chipId;
        public int currentModeId;
        public boolean currentModeIdValid;
        public WifiIfaceInfo[][] ifaces;

        private WifiChipInfo() {
            this.ifaces = new WifiIfaceInfo[HalDeviceManager.IFACE_TYPES_BY_PRIORITY.length][];
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{chipId=");
            sb.append(this.chipId);
            sb.append(", availableModes=");
            sb.append(this.availableModes);
            sb.append(", currentModeIdValid=");
            sb.append(this.currentModeIdValid);
            sb.append(", currentModeId=");
            sb.append(this.currentModeId);
            int[] iArr = HalDeviceManager.IFACE_TYPES_BY_PRIORITY;
            for (int type : iArr) {
                sb.append(", ifaces[" + type + "].length=");
                sb.append(this.ifaces[type].length);
            }
            sb.append(")");
            return sb.toString();
        }
    }

    /* access modifiers changed from: protected */
    public ISehWifi getSehWifiServiceMockable() {
        try {
            return ISehWifi.getService(true);
        } catch (RemoteException e) {
            Log.e(TAG, "Exception getting ISehWifi service: " + e);
            return null;
        }
    }

    /* access modifiers changed from: protected */
    public IWifi getWifiServiceMockable() {
        try {
            return IWifi.getService(true);
        } catch (RemoteException e) {
            Log.e(TAG, "Exception getting IWifi service: " + e);
            return null;
        }
    }

    /* access modifiers changed from: protected */
    public IServiceManager getServiceManagerMockable() {
        try {
            return IServiceManager.getService();
        } catch (RemoteException e) {
            Log.e(TAG, "Exception getting IServiceManager: " + e);
            return null;
        }
    }

    private void initializeInternal() {
        initIServiceManagerIfNecessary();
        if (this.mIsVendorHalSupported) {
            initIWifiIfNecessary();
            initISehWifiIfNeccessary();
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void teardownInternal() {
        managerStatusListenerDispatch();
        dispatchAllDestroyedListeners();
        this.mInterfaceAvailableForRequestListeners.get(0).clear();
        this.mInterfaceAvailableForRequestListeners.get(1).clear();
        this.mInterfaceAvailableForRequestListeners.get(2).clear();
        this.mInterfaceAvailableForRequestListeners.get(3).clear();
        this.mIWifiRttController = null;
        dispatchRttControllerLifecycleOnDestroyed();
        this.mRttControllerLifecycleCallbacks.clear();
    }

    public /* synthetic */ void lambda$new$1$HalDeviceManager(long cookie) {
        Log.wtf(TAG, "IServiceManager died: cookie=" + cookie);
        synchronized (this.mLock) {
            this.mServiceManager = null;
        }
    }

    private void initIServiceManagerIfNecessary() {
        if (this.mDbg) {
            Log.d(TAG, "initIServiceManagerIfNecessary");
        }
        synchronized (this.mLock) {
            if (this.mServiceManager == null) {
                this.mServiceManager = getServiceManagerMockable();
                if (this.mServiceManager == null) {
                    Log.wtf(TAG, "Failed to get IServiceManager instance");
                } else {
                    try {
                        if (!this.mServiceManager.linkToDeath(this.mServiceManagerDeathRecipient, 0)) {
                            Log.wtf(TAG, "Error on linkToDeath on IServiceManager");
                            this.mServiceManager = null;
                            return;
                        }
                        if (!this.mServiceManager.registerForNotifications(IWifi.kInterfaceName, "", this.mServiceNotificationCallback)) {
                            Log.wtf(TAG, "Failed to register a listener for IWifi service");
                            this.mServiceManager = null;
                        }
                        this.mIsVendorHalSupported = isSupportedInternal();
                    } catch (RemoteException e) {
                        Log.wtf(TAG, "Exception while operating on IServiceManager: " + e);
                        this.mServiceManager = null;
                    }
                }
            }
        }
    }

    private boolean isSupportedInternal() {
        Log.d(TAG, "isSupportedInternal");
        synchronized (this.mLock) {
            boolean z = false;
            if (this.mServiceManager == null) {
                Log.e(TAG, "isSupported: called but mServiceManager is null!?");
                return false;
            }
            try {
                if (!this.mServiceManager.listManifestByInterface(IWifi.kInterfaceName).isEmpty()) {
                    z = true;
                }
                return z;
            } catch (RemoteException e) {
                Log.wtf(TAG, "Exception while operating on IServiceManager: " + e);
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$new$2$HalDeviceManager(long cookie) {
        Log.e(TAG, "IWifi HAL service died! Have a listener for it ... cookie=" + cookie);
        synchronized (this.mLock) {
            this.mWifi = null;
            this.mIsReady = false;
            teardownInternal();
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void initIWifiIfNecessary() {
        if (this.mDbg) {
            Log.d(TAG, "initIWifiIfNecessary");
        }
        synchronized (this.mLock) {
            if (this.mWifi == null) {
                try {
                    this.mWifi = getWifiServiceMockable();
                    if (this.mWifi == null) {
                        Log.e(TAG, "IWifi not (yet) available - but have a listener for it ...");
                    } else if (!this.mWifi.linkToDeath(this.mIWifiDeathRecipient, 0)) {
                        Log.e(TAG, "Error on linkToDeath on IWifi - will retry later");
                    } else {
                        WifiStatus status = this.mWifi.registerEventCallback(this.mWifiEventCallback);
                        if (status.code != 0) {
                            Log.e(TAG, "IWifi.registerEventCallback failed: " + statusString(status));
                            this.mWifi = null;
                            return;
                        }
                        stopWifi();
                        this.mIsReady = true;
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Exception while operating on IWifi: " + e);
                }
            }
        }
    }

    public /* synthetic */ void lambda$new$3$HalDeviceManager(long cookie) {
        Log.e(TAG, "ISehWifi HAL service died! Have a listener for it ... cookie=" + cookie);
        synchronized (this.mLock) {
            this.mSehWifi = null;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void initISehWifiIfNeccessary() {
        if (this.mDbg) {
            Log.d(TAG, "initIWifiExtIfNecessary");
        }
        synchronized (this.mLock) {
            if (this.mSehWifi == null) {
                try {
                    this.mSehWifi = getSehWifiServiceMockable();
                    if (this.mSehWifi == null) {
                        Log.e(TAG, "ISehWifi not (yet) available - but have a listener for it ...");
                        return;
                    }
                    if (!this.mSehWifi.linkToDeath(this.mISehWifiDeathRecipient, 0)) {
                        Log.e(TAG, "Error on linkToDeath on ISehWifi - will retry later");
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Exception while operating on ISehWifi: " + e);
                }
            }
        }
    }

    public ISehWifi getSehWifiService() {
        return this.mSehWifi;
    }

    private void initIWifiChipDebugListeners() {
        Log.d(TAG, "initIWifiChipDebugListeners");
        synchronized (this.mLock) {
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                HidlSupport.Mutable<ArrayList<Integer>> chipIdsResp = new HidlSupport.Mutable<>();
                this.mWifi.getChipIds(new IWifi.getChipIdsCallback(statusOk, chipIdsResp) {
                    /* class com.android.server.wifi.$$Lambda$HalDeviceManager$FzgatNeVoiqJVqqcTO3qZDdYVS4 */
                    private final /* synthetic */ MutableBoolean f$0;
                    private final /* synthetic */ HidlSupport.Mutable f$1;

                    {
                        this.f$0 = r1;
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.V1_0.IWifi.getChipIdsCallback
                    public final void onValues(WifiStatus wifiStatus, ArrayList arrayList) {
                        HalDeviceManager.lambda$initIWifiChipDebugListeners$4(this.f$0, this.f$1, wifiStatus, arrayList);
                    }
                });
                if (statusOk.value) {
                    Log.d(TAG, "getChipIds=" + chipIdsResp.value);
                    if (((ArrayList) chipIdsResp.value).size() == 0) {
                        Log.e(TAG, "Should have at least 1 chip!");
                        return;
                    }
                    HidlSupport.Mutable<IWifiChip> chipResp = new HidlSupport.Mutable<>();
                    Iterator it = ((ArrayList) chipIdsResp.value).iterator();
                    while (it.hasNext()) {
                        Integer chipId = (Integer) it.next();
                        this.mWifi.getChip(chipId.intValue(), new IWifi.getChipCallback(statusOk, chipResp) {
                            /* class com.android.server.wifi.$$Lambda$HalDeviceManager$yVGai26z7ilHTxH6L5niRtg07P8 */
                            private final /* synthetic */ MutableBoolean f$0;
                            private final /* synthetic */ HidlSupport.Mutable f$1;

                            {
                                this.f$0 = r1;
                                this.f$1 = r2;
                            }

                            @Override // android.hardware.wifi.V1_0.IWifi.getChipCallback
                            public final void onValues(WifiStatus wifiStatus, IWifiChip iWifiChip) {
                                HalDeviceManager.lambda$initIWifiChipDebugListeners$5(this.f$0, this.f$1, wifiStatus, iWifiChip);
                            }
                        });
                        if (statusOk.value) {
                            IWifiChipEventCallback.Stub callback = new IWifiChipEventCallback.Stub() {
                                /* class com.android.server.wifi.HalDeviceManager.HwBinderC03902 */

                                @Override // android.hardware.wifi.V1_0.IWifiChipEventCallback
                                public void onChipReconfigured(int modeId) throws RemoteException {
                                    Log.d(HalDeviceManager.TAG, "onChipReconfigured: modeId=" + modeId);
                                }

                                @Override // android.hardware.wifi.V1_0.IWifiChipEventCallback
                                public void onChipReconfigureFailure(WifiStatus status) throws RemoteException {
                                    Log.d(HalDeviceManager.TAG, "onChipReconfigureFailure: status=" + HalDeviceManager.statusString(status));
                                }

                                @Override // android.hardware.wifi.V1_0.IWifiChipEventCallback
                                public void onIfaceAdded(int type, String name) throws RemoteException {
                                    Log.d(HalDeviceManager.TAG, "onIfaceAdded: type=" + type + ", name=" + name);
                                }

                                @Override // android.hardware.wifi.V1_0.IWifiChipEventCallback
                                public void onIfaceRemoved(int type, String name) throws RemoteException {
                                    Log.d(HalDeviceManager.TAG, "onIfaceRemoved: type=" + type + ", name=" + name);
                                }

                                @Override // android.hardware.wifi.V1_0.IWifiChipEventCallback
                                public void onDebugRingBufferDataAvailable(WifiDebugRingBufferStatus status, ArrayList<Byte> arrayList) throws RemoteException {
                                    Log.d(HalDeviceManager.TAG, "onDebugRingBufferDataAvailable");
                                }

                                @Override // android.hardware.wifi.V1_0.IWifiChipEventCallback
                                public void onDebugErrorAlert(int errorCode, ArrayList<Byte> arrayList) throws RemoteException {
                                    Log.d(HalDeviceManager.TAG, "onDebugErrorAlert");
                                }
                            };
                            this.mDebugCallbacks.put(chipId.intValue(), callback);
                            WifiStatus status = ((IWifiChip) chipResp.value).registerEventCallback(callback);
                            if (status.code != 0) {
                                Log.e(TAG, "registerEventCallback failed: " + statusString(status));
                            }
                        }
                    }
                }
            } catch (RemoteException e) {
                Log.e(TAG, "initIWifiChipDebugListeners: exception: " + e);
            }
        }
    }

    static /* synthetic */ void lambda$initIWifiChipDebugListeners$4(MutableBoolean statusOk, HidlSupport.Mutable chipIdsResp, WifiStatus status, ArrayList chipIds) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            chipIdsResp.value = chipIds;
            return;
        }
        Log.e(TAG, "getChipIds failed: " + statusString(status));
    }

    static /* synthetic */ void lambda$initIWifiChipDebugListeners$5(MutableBoolean statusOk, HidlSupport.Mutable chipResp, WifiStatus status, IWifiChip chip) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            chipResp.value = chip;
            return;
        }
        Log.e(TAG, "getChip failed: " + statusString(status));
    }

    /* JADX WARN: Type inference failed for: r10v0 */
    /* JADX WARN: Type inference failed for: r10v1, types: [int, boolean] */
    /* JADX WARN: Type inference failed for: r10v10 */
    private WifiChipInfo[] getAllChipInfo() {
        Log.d(TAG, "getAllChipInfo");
        synchronized (this.mLock) {
            WifiChipInfo[] wifiChipInfoArr = null;
            if (this.mWifi == null) {
                Log.e(TAG, "getAllChipInfo: called but mWifi is null!?");
                return null;
            }
            try {
                ?? r10 = 0;
                MutableBoolean statusOk = new MutableBoolean(false);
                HidlSupport.Mutable<ArrayList<Integer>> chipIdsResp = new HidlSupport.Mutable<>();
                this.mWifi.getChipIds(new IWifi.getChipIdsCallback(statusOk, chipIdsResp) {
                    /* class com.android.server.wifi.$$Lambda$HalDeviceManager$oV0zj57wyQrMevn_BdPhBTwDZhY */
                    private final /* synthetic */ MutableBoolean f$0;
                    private final /* synthetic */ HidlSupport.Mutable f$1;

                    {
                        this.f$0 = r1;
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.V1_0.IWifi.getChipIdsCallback
                    public final void onValues(WifiStatus wifiStatus, ArrayList arrayList) {
                        HalDeviceManager.lambda$getAllChipInfo$6(this.f$0, this.f$1, wifiStatus, arrayList);
                    }
                });
                if (!statusOk.value) {
                    return null;
                }
                Log.d(TAG, "getChipIds=" + chipIdsResp.value);
                if (((ArrayList) chipIdsResp.value).size() == 0) {
                    Log.e(TAG, "Should have at least 1 chip!");
                    return null;
                }
                WifiChipInfo[] chipsInfo = new WifiChipInfo[((ArrayList) chipIdsResp.value).size()];
                HidlSupport.Mutable<IWifiChip> chipResp = new HidlSupport.Mutable<>();
                HidlSupport.Mutable<ArrayList<String>> ifaceNamesResp = ((ArrayList) chipIdsResp.value).iterator();
                int chipInfoIndex = 0;
                while (ifaceNamesResp.hasNext()) {
                    Integer chipId = (Integer) ifaceNamesResp.next();
                    this.mWifi.getChip(chipId.intValue(), new IWifi.getChipCallback(statusOk, chipResp) {
                        /* class com.android.server.wifi.$$Lambda$HalDeviceManager$ZUYyxSyT0hYOkWCRHSzePknlIo0 */
                        private final /* synthetic */ MutableBoolean f$0;
                        private final /* synthetic */ HidlSupport.Mutable f$1;

                        {
                            this.f$0 = r1;
                            this.f$1 = r2;
                        }

                        @Override // android.hardware.wifi.V1_0.IWifi.getChipCallback
                        public final void onValues(WifiStatus wifiStatus, IWifiChip iWifiChip) {
                            HalDeviceManager.lambda$getAllChipInfo$7(this.f$0, this.f$1, wifiStatus, iWifiChip);
                        }
                    });
                    if (!statusOk.value) {
                        return wifiChipInfoArr;
                    }
                    HidlSupport.Mutable<ArrayList<IWifiChip.ChipMode>> availableModesResp = new HidlSupport.Mutable<>();
                    ((IWifiChip) chipResp.value).getAvailableModes(new IWifiChip.getAvailableModesCallback(statusOk, availableModesResp) {
                        /* class com.android.server.wifi.$$Lambda$HalDeviceManager$aTCTYHFoCRvUuzhQPn5Voq6cUFw */
                        private final /* synthetic */ MutableBoolean f$0;
                        private final /* synthetic */ HidlSupport.Mutable f$1;

                        {
                            this.f$0 = r1;
                            this.f$1 = r2;
                        }

                        @Override // android.hardware.wifi.V1_0.IWifiChip.getAvailableModesCallback
                        public final void onValues(WifiStatus wifiStatus, ArrayList arrayList) {
                            HalDeviceManager.lambda$getAllChipInfo$8(this.f$0, this.f$1, wifiStatus, arrayList);
                        }
                    });
                    if (!statusOk.value) {
                        return wifiChipInfoArr;
                    }
                    MutableBoolean currentModeValidResp = new MutableBoolean(r10);
                    MutableInt currentModeResp = new MutableInt(r10);
                    ((IWifiChip) chipResp.value).getMode(new IWifiChip.getModeCallback(statusOk, currentModeValidResp, currentModeResp) {
                        /* class com.android.server.wifi.$$Lambda$HalDeviceManager$QOM6V5ZTnXWwvLBR5woEK_9c */
                        private final /* synthetic */ MutableBoolean f$0;
                        private final /* synthetic */ MutableBoolean f$1;
                        private final /* synthetic */ MutableInt f$2;

                        {
                            this.f$0 = r1;
                            this.f$1 = r2;
                            this.f$2 = r3;
                        }

                        @Override // android.hardware.wifi.V1_0.IWifiChip.getModeCallback
                        public final void onValues(WifiStatus wifiStatus, int i) {
                            HalDeviceManager.lambda$getAllChipInfo$9(this.f$0, this.f$1, this.f$2, wifiStatus, i);
                        }
                    });
                    if (!statusOk.value) {
                        return wifiChipInfoArr;
                    }
                    HidlSupport.Mutable<ArrayList<String>> ifaceNamesResp2 = new HidlSupport.Mutable<>();
                    MutableInt ifaceIndex = new MutableInt(r10);
                    ((IWifiChip) chipResp.value).getStaIfaceNames(new IWifiChip.getStaIfaceNamesCallback(statusOk, ifaceNamesResp2) {
                        /* class com.android.server.wifi.$$Lambda$HalDeviceManager$W3qf_0tQXw4SlDmLzDZscYHrJQ */
                        private final /* synthetic */ MutableBoolean f$0;
                        private final /* synthetic */ HidlSupport.Mutable f$1;

                        {
                            this.f$0 = r1;
                            this.f$1 = r2;
                        }

                        @Override // android.hardware.wifi.V1_0.IWifiChip.getStaIfaceNamesCallback
                        public final void onValues(WifiStatus wifiStatus, ArrayList arrayList) {
                            HalDeviceManager.lambda$getAllChipInfo$10(this.f$0, this.f$1, wifiStatus, arrayList);
                        }
                    });
                    if (!statusOk.value) {
                        return wifiChipInfoArr;
                    }
                    WifiIfaceInfo[] staIfaces = new WifiIfaceInfo[((ArrayList) ifaceNamesResp2.value).size()];
                    Iterator it = ((ArrayList) ifaceNamesResp2.value).iterator();
                    while (it.hasNext()) {
                        String ifaceName = (String) it.next();
                        ((IWifiChip) chipResp.value).getStaIface(ifaceName, new IWifiChip.getStaIfaceCallback(statusOk, ifaceName, staIfaces, ifaceIndex) {
                            /* class com.android.server.wifi.$$Lambda$HalDeviceManager$HLPmFjXA6r19Ma_sML3KIFjYXI8 */
                            private final /* synthetic */ MutableBoolean f$1;
                            private final /* synthetic */ String f$2;
                            private final /* synthetic */ HalDeviceManager.WifiIfaceInfo[] f$3;
                            private final /* synthetic */ MutableInt f$4;

                            {
                                this.f$1 = r2;
                                this.f$2 = r3;
                                this.f$3 = r4;
                                this.f$4 = r5;
                            }

                            @Override // android.hardware.wifi.V1_0.IWifiChip.getStaIfaceCallback
                            public final void onValues(WifiStatus wifiStatus, IWifiStaIface iWifiStaIface) {
                                HalDeviceManager.this.lambda$getAllChipInfo$11$HalDeviceManager(this.f$1, this.f$2, this.f$3, this.f$4, wifiStatus, iWifiStaIface);
                            }
                        });
                        if (!statusOk.value) {
                            return null;
                        }
                        availableModesResp = availableModesResp;
                        ifaceNamesResp2 = ifaceNamesResp2;
                        chipIdsResp = chipIdsResp;
                        ifaceIndex = ifaceIndex;
                        ifaceNamesResp = ifaceNamesResp;
                        currentModeResp = currentModeResp;
                        currentModeValidResp = currentModeValidResp;
                        staIfaces = staIfaces;
                    }
                    HidlSupport.Mutable<ArrayList<String>> ifaceNamesResp3 = ifaceNamesResp2;
                    HidlSupport.Mutable<ArrayList<IWifiChip.ChipMode>> availableModesResp2 = availableModesResp;
                    ifaceIndex.value = 0;
                    ((IWifiChip) chipResp.value).getApIfaceNames(new IWifiChip.getApIfaceNamesCallback(statusOk, ifaceNamesResp3) {
                        /* class com.android.server.wifi.$$Lambda$HalDeviceManager$7IqRxcNtEnrXS9uVkc3w4xT9lgk */
                        private final /* synthetic */ MutableBoolean f$0;
                        private final /* synthetic */ HidlSupport.Mutable f$1;

                        {
                            this.f$0 = r1;
                            this.f$1 = r2;
                        }

                        @Override // android.hardware.wifi.V1_0.IWifiChip.getApIfaceNamesCallback
                        public final void onValues(WifiStatus wifiStatus, ArrayList arrayList) {
                            HalDeviceManager.lambda$getAllChipInfo$12(this.f$0, this.f$1, wifiStatus, arrayList);
                        }
                    });
                    if (!statusOk.value) {
                        return null;
                    }
                    WifiIfaceInfo[] apIfaces = new WifiIfaceInfo[((ArrayList) ifaceNamesResp3.value).size()];
                    Iterator it2 = ((ArrayList) ifaceNamesResp3.value).iterator();
                    while (it2.hasNext()) {
                        String ifaceName2 = (String) it2.next();
                        ((IWifiChip) chipResp.value).getApIface(ifaceName2, new IWifiChip.getApIfaceCallback(statusOk, ifaceName2, apIfaces, ifaceIndex) {
                            /* class com.android.server.wifi.$$Lambda$HalDeviceManager$LisNucJKN8TgUZ4F_hMe1s79mng */
                            private final /* synthetic */ MutableBoolean f$1;
                            private final /* synthetic */ String f$2;
                            private final /* synthetic */ HalDeviceManager.WifiIfaceInfo[] f$3;
                            private final /* synthetic */ MutableInt f$4;

                            {
                                this.f$1 = r2;
                                this.f$2 = r3;
                                this.f$3 = r4;
                                this.f$4 = r5;
                            }

                            @Override // android.hardware.wifi.V1_0.IWifiChip.getApIfaceCallback
                            public final void onValues(WifiStatus wifiStatus, IWifiApIface iWifiApIface) {
                                HalDeviceManager.this.lambda$getAllChipInfo$13$HalDeviceManager(this.f$1, this.f$2, this.f$3, this.f$4, wifiStatus, iWifiApIface);
                            }
                        });
                        if (!statusOk.value) {
                            return null;
                        }
                        availableModesResp2 = availableModesResp2;
                        chipsInfo = chipsInfo;
                        apIfaces = apIfaces;
                    }
                    ifaceIndex.value = 0;
                    ((IWifiChip) chipResp.value).getP2pIfaceNames(new IWifiChip.getP2pIfaceNamesCallback(statusOk, ifaceNamesResp3) {
                        /* class com.android.server.wifi.$$Lambda$HalDeviceManager$INj3cXuz7UCfJAOVdMEteizngtw */
                        private final /* synthetic */ MutableBoolean f$0;
                        private final /* synthetic */ HidlSupport.Mutable f$1;

                        {
                            this.f$0 = r1;
                            this.f$1 = r2;
                        }

                        @Override // android.hardware.wifi.V1_0.IWifiChip.getP2pIfaceNamesCallback
                        public final void onValues(WifiStatus wifiStatus, ArrayList arrayList) {
                            HalDeviceManager.lambda$getAllChipInfo$14(this.f$0, this.f$1, wifiStatus, arrayList);
                        }
                    });
                    if (!statusOk.value) {
                        return null;
                    }
                    WifiIfaceInfo[] p2pIfaces = new WifiIfaceInfo[((ArrayList) ifaceNamesResp3.value).size()];
                    Iterator it3 = ((ArrayList) ifaceNamesResp3.value).iterator();
                    while (it3.hasNext()) {
                        String ifaceName3 = (String) it3.next();
                        ((IWifiChip) chipResp.value).getP2pIface(ifaceName3, new IWifiChip.getP2pIfaceCallback(statusOk, ifaceName3, p2pIfaces, ifaceIndex) {
                            /* class com.android.server.wifi.$$Lambda$HalDeviceManager$ynHs4R12k_5_9Qxr5asWSHdsuE4 */
                            private final /* synthetic */ MutableBoolean f$1;
                            private final /* synthetic */ String f$2;
                            private final /* synthetic */ HalDeviceManager.WifiIfaceInfo[] f$3;
                            private final /* synthetic */ MutableInt f$4;

                            {
                                this.f$1 = r2;
                                this.f$2 = r3;
                                this.f$3 = r4;
                                this.f$4 = r5;
                            }

                            @Override // android.hardware.wifi.V1_0.IWifiChip.getP2pIfaceCallback
                            public final void onValues(WifiStatus wifiStatus, IWifiP2pIface iWifiP2pIface) {
                                HalDeviceManager.this.lambda$getAllChipInfo$15$HalDeviceManager(this.f$1, this.f$2, this.f$3, this.f$4, wifiStatus, iWifiP2pIface);
                            }
                        });
                        if (!statusOk.value) {
                            return null;
                        }
                        it3 = it3;
                        p2pIfaces = p2pIfaces;
                    }
                    ifaceIndex.value = 0;
                    ((IWifiChip) chipResp.value).getNanIfaceNames(new IWifiChip.getNanIfaceNamesCallback(statusOk, ifaceNamesResp3) {
                        /* class com.android.server.wifi.$$Lambda$HalDeviceManager$d3wDJSLIYr6Z1fiH2ZtAJWELMyY */
                        private final /* synthetic */ MutableBoolean f$0;
                        private final /* synthetic */ HidlSupport.Mutable f$1;

                        {
                            this.f$0 = r1;
                            this.f$1 = r2;
                        }

                        @Override // android.hardware.wifi.V1_0.IWifiChip.getNanIfaceNamesCallback
                        public final void onValues(WifiStatus wifiStatus, ArrayList arrayList) {
                            HalDeviceManager.lambda$getAllChipInfo$16(this.f$0, this.f$1, wifiStatus, arrayList);
                        }
                    });
                    if (!statusOk.value) {
                        return null;
                    }
                    WifiIfaceInfo[] nanIfaces = new WifiIfaceInfo[((ArrayList) ifaceNamesResp3.value).size()];
                    Iterator it4 = ((ArrayList) ifaceNamesResp3.value).iterator();
                    while (it4.hasNext()) {
                        String ifaceName4 = (String) it4.next();
                        ((IWifiChip) chipResp.value).getNanIface(ifaceName4, new IWifiChip.getNanIfaceCallback(statusOk, ifaceName4, nanIfaces, ifaceIndex) {
                            /* class com.android.server.wifi.$$Lambda$HalDeviceManager$OTxRCq8TAZZlX8UFhmqaHcpXJYQ */
                            private final /* synthetic */ MutableBoolean f$1;
                            private final /* synthetic */ String f$2;
                            private final /* synthetic */ HalDeviceManager.WifiIfaceInfo[] f$3;
                            private final /* synthetic */ MutableInt f$4;

                            {
                                this.f$1 = r2;
                                this.f$2 = r3;
                                this.f$3 = r4;
                                this.f$4 = r5;
                            }

                            @Override // android.hardware.wifi.V1_0.IWifiChip.getNanIfaceCallback
                            public final void onValues(WifiStatus wifiStatus, IWifiNanIface iWifiNanIface) {
                                HalDeviceManager.this.lambda$getAllChipInfo$17$HalDeviceManager(this.f$1, this.f$2, this.f$3, this.f$4, wifiStatus, iWifiNanIface);
                            }
                        });
                        if (!statusOk.value) {
                            return null;
                        }
                        it4 = it4;
                        ifaceNamesResp3 = ifaceNamesResp3;
                    }
                    WifiChipInfo chipInfo = new WifiChipInfo();
                    chipsInfo[chipInfoIndex] = chipInfo;
                    chipInfo.chip = (IWifiChip) chipResp.value;
                    chipInfo.chipId = chipId.intValue();
                    chipInfo.availableModes = (ArrayList) availableModesResp2.value;
                    chipInfo.currentModeIdValid = currentModeValidResp.value;
                    chipInfo.currentModeId = currentModeResp.value;
                    chipInfo.ifaces[0] = staIfaces;
                    chipInfo.ifaces[1] = apIfaces;
                    chipInfo.ifaces[2] = p2pIfaces;
                    chipInfo.ifaces[3] = nanIfaces;
                    chipInfoIndex++;
                    r10 = 0;
                    chipIdsResp = chipIdsResp;
                    chipsInfo = chipsInfo;
                    ifaceNamesResp = ifaceNamesResp;
                    wifiChipInfoArr = null;
                }
                return chipsInfo;
            } catch (RemoteException e) {
                Log.e(TAG, "getAllChipInfoAndValidateCache exception: " + e);
                return null;
            }
        }
    }

    static /* synthetic */ void lambda$getAllChipInfo$6(MutableBoolean statusOk, HidlSupport.Mutable chipIdsResp, WifiStatus status, ArrayList chipIds) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            chipIdsResp.value = chipIds;
            return;
        }
        Log.e(TAG, "getChipIds failed: " + statusString(status));
    }

    static /* synthetic */ void lambda$getAllChipInfo$7(MutableBoolean statusOk, HidlSupport.Mutable chipResp, WifiStatus status, IWifiChip chip) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            chipResp.value = chip;
            return;
        }
        Log.e(TAG, "getChip failed: " + statusString(status));
    }

    static /* synthetic */ void lambda$getAllChipInfo$8(MutableBoolean statusOk, HidlSupport.Mutable availableModesResp, WifiStatus status, ArrayList modes) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            availableModesResp.value = modes;
            return;
        }
        Log.e(TAG, "getAvailableModes failed: " + statusString(status));
    }

    static /* synthetic */ void lambda$getAllChipInfo$9(MutableBoolean statusOk, MutableBoolean currentModeValidResp, MutableInt currentModeResp, WifiStatus status, int modeId) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            currentModeValidResp.value = true;
            currentModeResp.value = modeId;
        } else if (status.code == 5) {
            statusOk.value = true;
        } else {
            Log.e(TAG, "getMode failed: " + statusString(status));
        }
    }

    static /* synthetic */ void lambda$getAllChipInfo$10(MutableBoolean statusOk, HidlSupport.Mutable ifaceNamesResp, WifiStatus status, ArrayList ifnames) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            ifaceNamesResp.value = ifnames;
            return;
        }
        Log.e(TAG, "getStaIfaceNames failed: " + statusString(status));
    }

    public /* synthetic */ void lambda$getAllChipInfo$11$HalDeviceManager(MutableBoolean statusOk, String ifaceName, WifiIfaceInfo[] staIfaces, MutableInt ifaceIndex, WifiStatus status, IWifiStaIface iface) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            WifiIfaceInfo ifaceInfo = new WifiIfaceInfo();
            ifaceInfo.name = ifaceName;
            ifaceInfo.iface = iface;
            int i = ifaceIndex.value;
            ifaceIndex.value = i + 1;
            staIfaces[i] = ifaceInfo;
            return;
        }
        Log.e(TAG, "getStaIface failed: " + statusString(status));
    }

    static /* synthetic */ void lambda$getAllChipInfo$12(MutableBoolean statusOk, HidlSupport.Mutable ifaceNamesResp, WifiStatus status, ArrayList ifnames) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            ifaceNamesResp.value = ifnames;
            return;
        }
        Log.e(TAG, "getApIfaceNames failed: " + statusString(status));
    }

    public /* synthetic */ void lambda$getAllChipInfo$13$HalDeviceManager(MutableBoolean statusOk, String ifaceName, WifiIfaceInfo[] apIfaces, MutableInt ifaceIndex, WifiStatus status, IWifiApIface iface) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            WifiIfaceInfo ifaceInfo = new WifiIfaceInfo();
            ifaceInfo.name = ifaceName;
            ifaceInfo.iface = iface;
            int i = ifaceIndex.value;
            ifaceIndex.value = i + 1;
            apIfaces[i] = ifaceInfo;
            return;
        }
        Log.e(TAG, "getApIface failed: " + statusString(status));
    }

    static /* synthetic */ void lambda$getAllChipInfo$14(MutableBoolean statusOk, HidlSupport.Mutable ifaceNamesResp, WifiStatus status, ArrayList ifnames) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            ifaceNamesResp.value = ifnames;
            return;
        }
        Log.e(TAG, "getP2pIfaceNames failed: " + statusString(status));
    }

    public /* synthetic */ void lambda$getAllChipInfo$15$HalDeviceManager(MutableBoolean statusOk, String ifaceName, WifiIfaceInfo[] p2pIfaces, MutableInt ifaceIndex, WifiStatus status, IWifiP2pIface iface) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            WifiIfaceInfo ifaceInfo = new WifiIfaceInfo();
            ifaceInfo.name = ifaceName;
            ifaceInfo.iface = iface;
            int i = ifaceIndex.value;
            ifaceIndex.value = i + 1;
            p2pIfaces[i] = ifaceInfo;
            return;
        }
        Log.e(TAG, "getP2pIface failed: " + statusString(status));
    }

    static /* synthetic */ void lambda$getAllChipInfo$16(MutableBoolean statusOk, HidlSupport.Mutable ifaceNamesResp, WifiStatus status, ArrayList ifnames) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            ifaceNamesResp.value = ifnames;
            return;
        }
        Log.e(TAG, "getNanIfaceNames failed: " + statusString(status));
    }

    public /* synthetic */ void lambda$getAllChipInfo$17$HalDeviceManager(MutableBoolean statusOk, String ifaceName, WifiIfaceInfo[] nanIfaces, MutableInt ifaceIndex, WifiStatus status, IWifiNanIface iface) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            WifiIfaceInfo ifaceInfo = new WifiIfaceInfo();
            ifaceInfo.name = ifaceName;
            ifaceInfo.iface = iface;
            int i = ifaceIndex.value;
            ifaceIndex.value = i + 1;
            nanIfaces[i] = ifaceInfo;
            return;
        }
        Log.e(TAG, "getNanIface failed: " + statusString(status));
    }

    private boolean validateInterfaceCache(WifiChipInfo[] chipInfos) {
        Log.d(TAG, "validateInterfaceCache");
        synchronized (this.mLock) {
            for (InterfaceCacheEntry entry : this.mInterfaceInfoCache.values()) {
                WifiChipInfo matchingChipInfo = null;
                int length = chipInfos.length;
                int i = 0;
                while (true) {
                    if (i >= length) {
                        break;
                    }
                    WifiChipInfo ci = chipInfos[i];
                    if (ci.chipId == entry.chipId) {
                        matchingChipInfo = ci;
                        break;
                    }
                    i++;
                }
                if (matchingChipInfo == null) {
                    Log.e(TAG, "validateInterfaceCache: no chip found for " + entry);
                    return false;
                }
                WifiIfaceInfo[] ifaceInfoList = matchingChipInfo.ifaces[entry.type];
                if (ifaceInfoList == null) {
                    Log.e(TAG, "validateInterfaceCache: invalid type on entry " + entry);
                    return false;
                }
                boolean matchFound = false;
                int length2 = ifaceInfoList.length;
                int i2 = 0;
                while (true) {
                    if (i2 >= length2) {
                        break;
                    } else if (ifaceInfoList[i2].name.equals(entry.name)) {
                        matchFound = true;
                        break;
                    } else {
                        i2++;
                    }
                }
                if (!matchFound) {
                    Log.e(TAG, "validateInterfaceCache: no interface found for " + entry);
                    return false;
                }
            }
            return true;
        }
    }

    private boolean isWifiStarted() {
        Log.d(TAG, "isWifiStart");
        synchronized (this.mLock) {
            try {
                if (this.mWifi == null) {
                    Log.w(TAG, "isWifiStarted called but mWifi is null!?");
                    return false;
                }
                return this.mWifi.isStarted();
            } catch (RemoteException e) {
                Log.e(TAG, "isWifiStarted exception: " + e);
                return false;
            }
        }
    }

    private boolean startWifi() {
        Log.d(TAG, "startWifi");
        initIWifiIfNecessary();
        synchronized (this.mLock) {
            try {
                if (this.mWifi == null) {
                    Log.w(TAG, "startWifi called but mWifi is null!?");
                    return false;
                }
                int triedCount = 0;
                while (triedCount <= 3) {
                    WifiStatus status = this.mWifi.start();
                    if (status.code == 0) {
                        initIWifiChipDebugListeners();
                        managerStatusListenerDispatch();
                        if (triedCount != 0) {
                            Log.d(TAG, "start IWifi succeeded after trying " + triedCount + " times");
                        }
                        return true;
                    } else if (status.code == 5) {
                        Log.e(TAG, "Cannot start IWifi: " + statusString(status) + ", Retrying...");
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                        }
                        triedCount++;
                    } else {
                        Log.e(TAG, "Cannot start IWifi: " + statusString(status));
                        return false;
                    }
                }
                Log.e(TAG, "Cannot start IWifi after trying " + triedCount + " times");
                return false;
            } catch (RemoteException e2) {
                Log.e(TAG, "startWifi exception: " + e2);
                return false;
            }
        }
    }

    private void stopWifi() {
        Log.d(TAG, "stopWifi");
        synchronized (this.mLock) {
            try {
                if (this.mWifi == null) {
                    Log.w(TAG, "stopWifi called but mWifi is null!?");
                } else {
                    WifiStatus status = this.mWifi.stop();
                    if (status.code != 0) {
                        Log.e(TAG, "Cannot stop IWifi: " + statusString(status));
                    }
                    teardownInternal();
                }
            } catch (RemoteException e) {
                Log.e(TAG, "stopWifi exception: " + e);
            }
        }
    }

    /* access modifiers changed from: private */
    public class WifiEventCallback extends IWifiEventCallback.Stub {
        private WifiEventCallback() {
        }

        @Override // android.hardware.wifi.V1_0.IWifiEventCallback
        public void onStart() throws RemoteException {
            Log.d(HalDeviceManager.TAG, "IWifiEventCallback.onStart");
        }

        @Override // android.hardware.wifi.V1_0.IWifiEventCallback
        public void onStop() throws RemoteException {
            Log.d(HalDeviceManager.TAG, "IWifiEventCallback.onStop");
        }

        @Override // android.hardware.wifi.V1_0.IWifiEventCallback
        public void onFailure(WifiStatus status) throws RemoteException {
            Log.e(HalDeviceManager.TAG, "IWifiEventCallback.onFailure: " + HalDeviceManager.statusString(status));
            HalDeviceManager.this.teardownInternal();
        }
    }

    private void managerStatusListenerDispatch() {
        synchronized (this.mLock) {
            for (ManagerStatusListenerProxy cb : this.mManagerStatusListeners) {
                cb.trigger();
            }
        }
    }

    /* access modifiers changed from: private */
    public class ManagerStatusListenerProxy extends ListenerProxy<ManagerStatusListener> {
        ManagerStatusListenerProxy(ManagerStatusListener statusListener, Handler handler) {
            super(statusListener, handler, "ManagerStatusListenerProxy");
        }

        /* access modifiers changed from: protected */
        @Override // com.android.server.wifi.HalDeviceManager.ListenerProxy
        public void action() {
            ((ManagerStatusListener) this.mListener).onStatusChanged();
        }
    }

    /* access modifiers changed from: package-private */
    public Set<Integer> getSupportedIfaceTypesInternal(IWifiChip chip) {
        Set<Integer> results = new HashSet<>();
        WifiChipInfo[] chipInfos = getAllChipInfo();
        if (chipInfos == null) {
            Log.e(TAG, "getSupportedIfaceTypesInternal: no chip info found");
            return results;
        }
        MutableInt chipIdIfProvided = new MutableInt(0);
        if (chip != null) {
            MutableBoolean statusOk = new MutableBoolean(false);
            try {
                chip.getId(new IWifiChip.getIdCallback(chipIdIfProvided, statusOk) {
                    /* class com.android.server.wifi.$$Lambda$HalDeviceManager$RvX7FGUhmxmqNliFXxQKKDHrRc */
                    private final /* synthetic */ MutableInt f$0;
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$0 = r1;
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.V1_0.IWifiChip.getIdCallback
                    public final void onValues(WifiStatus wifiStatus, int i) {
                        HalDeviceManager.lambda$getSupportedIfaceTypesInternal$18(this.f$0, this.f$1, wifiStatus, i);
                    }
                });
                if (!statusOk.value) {
                    return results;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "getSupportedIfaceTypesInternal IWifiChip.getId() exception: " + e);
                return results;
            }
        }
        for (WifiChipInfo wci : chipInfos) {
            if (chip == null || wci.chipId == chipIdIfProvided.value) {
                Iterator<IWifiChip.ChipMode> it = wci.availableModes.iterator();
                while (it.hasNext()) {
                    Iterator<IWifiChip.ChipIfaceCombination> it2 = it.next().availableCombinations.iterator();
                    while (it2.hasNext()) {
                        Iterator<IWifiChip.ChipIfaceCombinationLimit> it3 = it2.next().limits.iterator();
                        while (it3.hasNext()) {
                            Iterator<Integer> it4 = it3.next().types.iterator();
                            while (it4.hasNext()) {
                                results.add(Integer.valueOf(it4.next().intValue()));
                            }
                        }
                    }
                }
            }
        }
        return results;
    }

    static /* synthetic */ void lambda$getSupportedIfaceTypesInternal$18(MutableInt chipIdIfProvided, MutableBoolean statusOk, WifiStatus status, int id) {
        if (status.code == 0) {
            chipIdIfProvided.value = id;
            statusOk.value = true;
            return;
        }
        Log.e(TAG, "getSupportedIfaceTypesInternal: IWifiChip.getId() error: " + statusString(status));
        statusOk.value = false;
    }

    private IWifiIface createIface(int ifaceType, boolean lowPriority, InterfaceDestroyedListener destroyedListener, Handler handler) {
        if (this.mDbg) {
            Log.d(TAG, "createIface: ifaceType=" + ifaceType + ", lowPriority=" + lowPriority);
        }
        synchronized (this.mLock) {
            WifiChipInfo[] chipInfos = getAllChipInfo();
            if (chipInfos == null) {
                Log.e(TAG, "createIface: no chip info found");
                stopWifi();
                return null;
            } else if (!validateInterfaceCache(chipInfos)) {
                Log.e(TAG, "createIface: local cache is invalid!");
                stopWifi();
                return null;
            } else {
                IWifiIface iface = createIfaceIfPossible(chipInfos, ifaceType, lowPriority, destroyedListener, handler);
                if (iface == null || dispatchAvailableForRequestListeners()) {
                    return iface;
                }
                return null;
            }
        }
    }

    private IWifiIface createIfaceIfPossible(WifiChipInfo[] chipInfos, int ifaceType, boolean lowPriority, InterfaceDestroyedListener destroyedListener, Handler handler) {
        WifiChipInfo[] wifiChipInfoArr = chipInfos;
        Log.d(TAG, "createIfaceIfPossible: chipInfos=" + Arrays.deepToString(chipInfos) + ", ifaceType=" + ifaceType + ", lowPriority=" + lowPriority);
        synchronized (this.mLock) {
            try {
                int length = wifiChipInfoArr.length;
                IfaceCreationData currentProposal = null;
                int i = 0;
                while (i < length) {
                    WifiChipInfo chipInfo = wifiChipInfoArr[i];
                    Iterator<IWifiChip.ChipMode> it = chipInfo.availableModes.iterator();
                    while (it.hasNext()) {
                        IWifiChip.ChipMode chipMode = it.next();
                        Iterator<IWifiChip.ChipIfaceCombination> it2 = chipMode.availableCombinations.iterator();
                        while (it2.hasNext()) {
                            IWifiChip.ChipIfaceCombination chipIfaceCombo = it2.next();
                            int[][] expandedIfaceCombos = expandIfaceCombos(chipIfaceCombo);
                            Log.d(TAG, chipIfaceCombo + " expands to " + Arrays.deepToString(expandedIfaceCombos));
                            int length2 = expandedIfaceCombos.length;
                            int i2 = 0;
                            while (i2 < length2) {
                                currentProposal = canIfaceComboSupportRequest(chipInfo, chipMode, expandedIfaceCombos[i2], ifaceType, lowPriority);
                                if (compareIfaceCreationData(currentProposal, currentProposal)) {
                                    Log.d(TAG, "new proposal accepted");
                                } else {
                                    currentProposal = currentProposal;
                                }
                                i2++;
                                expandedIfaceCombos = expandedIfaceCombos;
                                chipIfaceCombo = chipIfaceCombo;
                                chipMode = chipMode;
                            }
                        }
                    }
                    i++;
                    wifiChipInfoArr = chipInfos;
                }
                if (currentProposal != null) {
                    IWifiIface iface = executeChipReconfiguration(currentProposal, ifaceType);
                    if (iface != null) {
                        InterfaceCacheEntry cacheEntry = new InterfaceCacheEntry();
                        cacheEntry.chip = currentProposal.chipInfo.chip;
                        cacheEntry.chipId = currentProposal.chipInfo.chipId;
                        cacheEntry.name = getName(iface);
                        cacheEntry.type = ifaceType;
                        if (destroyedListener != null) {
                            cacheEntry.destroyedListeners.add(new InterfaceDestroyedListenerProxy(cacheEntry.name, destroyedListener, handler));
                        }
                        cacheEntry.creationTime = this.mClock.getUptimeSinceBootMillis();
                        cacheEntry.isLowPriority = lowPriority;
                        if (this.mDbg) {
                            Log.d(TAG, "createIfaceIfPossible: added cacheEntry=" + cacheEntry);
                        }
                        this.mInterfaceInfoCache.put(Pair.create(cacheEntry.name, Integer.valueOf(cacheEntry.type)), cacheEntry);
                        return iface;
                    }
                }
                return null;
            } catch (Throwable th) {
                th = th;
                throw th;
            }
        }
    }

    private boolean isItPossibleToCreateIface(WifiChipInfo[] chipInfos, int ifaceType) {
        Log.d(TAG, "isItPossibleToCreateIface: chipInfos=" + Arrays.deepToString(chipInfos) + ", ifaceType=" + ifaceType);
        int length = chipInfos.length;
        for (int i = 0; i < length; i++) {
            WifiChipInfo chipInfo = chipInfos[i];
            Iterator<IWifiChip.ChipMode> it = chipInfo.availableModes.iterator();
            while (it.hasNext()) {
                IWifiChip.ChipMode chipMode = it.next();
                Iterator<IWifiChip.ChipIfaceCombination> it2 = chipMode.availableCombinations.iterator();
                while (true) {
                    if (it2.hasNext()) {
                        IWifiChip.ChipIfaceCombination chipIfaceCombo = it2.next();
                        int[][] expandedIfaceCombos = expandIfaceCombos(chipIfaceCombo);
                        Log.d(TAG, chipIfaceCombo + " expands to " + Arrays.deepToString(expandedIfaceCombos));
                        int length2 = expandedIfaceCombos.length;
                        int i2 = 0;
                        while (i2 < length2) {
                            if (canIfaceComboSupportRequest(chipInfo, chipMode, expandedIfaceCombos[i2], ifaceType, false) != null) {
                                return true;
                            }
                            i2++;
                            length2 = length2;
                            expandedIfaceCombos = expandedIfaceCombos;
                            chipIfaceCombo = chipIfaceCombo;
                        }
                    }
                }
            }
        }
        return false;
    }

    private int[][] expandIfaceCombos(IWifiChip.ChipIfaceCombination chipIfaceCombo) {
        int numOfCombos = 1;
        Iterator<IWifiChip.ChipIfaceCombinationLimit> it = chipIfaceCombo.limits.iterator();
        while (it.hasNext()) {
            IWifiChip.ChipIfaceCombinationLimit limit = it.next();
            for (int i = 0; i < limit.maxIfaces; i++) {
                numOfCombos *= limit.types.size();
            }
        }
        int[][] expandedIfaceCombos = (int[][]) Array.newInstance(int.class, numOfCombos, IFACE_TYPES_BY_PRIORITY.length);
        int span = numOfCombos;
        Iterator<IWifiChip.ChipIfaceCombinationLimit> it2 = chipIfaceCombo.limits.iterator();
        while (it2.hasNext()) {
            IWifiChip.ChipIfaceCombinationLimit limit2 = it2.next();
            for (int i2 = 0; i2 < limit2.maxIfaces; i2++) {
                span /= limit2.types.size();
                for (int k = 0; k < numOfCombos; k++) {
                    int[] iArr = expandedIfaceCombos[k];
                    int intValue = limit2.types.get((k / span) % limit2.types.size()).intValue();
                    iArr[intValue] = iArr[intValue] + 1;
                }
            }
        }
        return expandedIfaceCombos;
    }

    /* access modifiers changed from: private */
    public class IfaceCreationData {
        public WifiChipInfo chipInfo;
        public int chipModeId;
        public List<WifiIfaceInfo> interfacesToBeRemovedFirst;

        private IfaceCreationData() {
        }

        public String toString() {
            return "{chipInfo=" + this.chipInfo + ", chipModeId=" + this.chipModeId + ", interfacesToBeRemovedFirst=" + this.interfacesToBeRemovedFirst + ")";
        }
    }

    /* JADX DEBUG: Failed to insert an additional move for type inference into block B:48:0x00ae */
    /* JADX WARN: Type inference failed for: r8v0 */
    /* JADX WARN: Type inference failed for: r8v5, types: [com.android.server.wifi.HalDeviceManager$1, com.android.server.wifi.HalDeviceManager$IfaceCreationData] */
    /* JADX WARN: Type inference failed for: r8v7 */
    /* JADX WARN: Type inference failed for: r8v15 */
    /* JADX WARN: Type inference failed for: r8v16 */
    private IfaceCreationData canIfaceComboSupportRequest(WifiChipInfo chipInfo, IWifiChip.ChipMode chipMode, int[] chipIfaceCombo, int ifaceType, boolean lowPriority) {
        int[] iArr = chipIfaceCombo;
        Log.d(TAG, "canIfaceComboSupportRequest: chipInfo=" + chipInfo + ", chipMode=" + chipMode + ", chipIfaceCombo=" + iArr + ", ifaceType=" + ifaceType + ", lowPriority=" + lowPriority);
        ?? r8 = 0;
        if (iArr[ifaceType] == 0) {
            Log.d(TAG, "Requested type not supported by combo");
            return null;
        }
        int i = 0;
        int i2 = 0;
        if (chipInfo.currentModeIdValid && chipInfo.currentModeId != chipMode.f0id) {
            int[] iArr2 = IFACE_TYPES_BY_PRIORITY;
            int length = iArr2.length;
            while (i2 < length) {
                int type = iArr2[i2];
                if (chipInfo.ifaces[type].length != 0) {
                    if (lowPriority) {
                        Log.d(TAG, "Couldn't delete existing type " + type + " interfaces for a low priority request");
                        return r8;
                    } else if (!allowedToDeleteIfaceTypeForRequestedType(type, ifaceType, chipInfo.ifaces, chipInfo.ifaces[type].length)) {
                        Log.d(TAG, "Couldn't delete existing type " + type + " interfaces for requested type");
                        return null;
                    } else {
                        r8 = 0;
                    }
                }
                i2++;
                r8 = r8;
            }
            IfaceCreationData ifaceCreationData = new IfaceCreationData();
            ifaceCreationData.chipInfo = chipInfo;
            ifaceCreationData.chipModeId = chipMode.f0id;
            return ifaceCreationData;
        }
        List<WifiIfaceInfo> interfacesToBeRemovedFirst = new ArrayList<>();
        int[] iArr3 = IFACE_TYPES_BY_PRIORITY;
        int length2 = iArr3.length;
        while (i < length2) {
            int type2 = iArr3[i];
            int tooManyInterfaces = chipInfo.ifaces[type2].length - iArr[type2];
            if (type2 == ifaceType) {
                tooManyInterfaces++;
            }
            if (tooManyInterfaces > 0) {
                if (lowPriority) {
                    Log.d(TAG, "Couldn't delete existing type " + type2 + " interfaces for a low priority request");
                    return null;
                } else if (!allowedToDeleteIfaceTypeForRequestedType(type2, ifaceType, chipInfo.ifaces, tooManyInterfaces)) {
                    Log.d(TAG, "Would need to delete some higher priority interfaces");
                    return null;
                } else {
                    interfacesToBeRemovedFirst = selectInterfacesToDelete(tooManyInterfaces, chipInfo.ifaces[type2]);
                }
            }
            i++;
            iArr = chipIfaceCombo;
        }
        IfaceCreationData ifaceCreationData2 = new IfaceCreationData();
        ifaceCreationData2.chipInfo = chipInfo;
        ifaceCreationData2.chipModeId = chipMode.f0id;
        ifaceCreationData2.interfacesToBeRemovedFirst = interfacesToBeRemovedFirst;
        return ifaceCreationData2;
    }

    private boolean compareIfaceCreationData(IfaceCreationData val1, IfaceCreationData val2) {
        int numIfacesToDelete1;
        int numIfacesToDelete2;
        Log.d(TAG, "compareIfaceCreationData: val1=" + val1 + ", val2=" + val2);
        if (val1 == null) {
            return false;
        }
        if (val2 == null) {
            return true;
        }
        int[] iArr = IFACE_TYPES_BY_PRIORITY;
        for (int type : iArr) {
            if (!val1.chipInfo.currentModeIdValid || val1.chipInfo.currentModeId == val1.chipModeId) {
                numIfacesToDelete1 = val1.interfacesToBeRemovedFirst.size();
            } else {
                numIfacesToDelete1 = val1.chipInfo.ifaces[type].length;
            }
            if (!val2.chipInfo.currentModeIdValid || val2.chipInfo.currentModeId == val2.chipModeId) {
                numIfacesToDelete2 = val2.interfacesToBeRemovedFirst.size();
            } else {
                numIfacesToDelete2 = val2.chipInfo.ifaces[type].length;
            }
            if (numIfacesToDelete1 < numIfacesToDelete2) {
                Log.d(TAG, "decision based on type=" + type + ": " + numIfacesToDelete1 + " < " + numIfacesToDelete2);
                return true;
            }
        }
        Log.d(TAG, "proposals identical - flip a coin");
        return false;
    }

    private boolean allowedToDeleteIfaceTypeForRequestedType(int existingIfaceType, int requestedIfaceType, WifiIfaceInfo[][] currentIfaces, int numNecessaryInterfaces) {
        int numAvailableLowPriorityInterfaces = 0;
        for (InterfaceCacheEntry entry : this.mInterfaceInfoCache.values()) {
            if (entry.type == existingIfaceType && entry.isLowPriority) {
                numAvailableLowPriorityInterfaces++;
            }
        }
        if (numAvailableLowPriorityInterfaces >= numNecessaryInterfaces) {
            return true;
        }
        if (existingIfaceType == requestedIfaceType || currentIfaces[requestedIfaceType].length != 0) {
            return false;
        }
        if (currentIfaces[existingIfaceType].length > 1) {
            return true;
        }
        if (requestedIfaceType == 3) {
            if (existingIfaceType == 2) {
                return true;
            }
            return false;
        } else if (requestedIfaceType != 2 || existingIfaceType == 3) {
            return true;
        } else {
            return false;
        }
    }

    private List<WifiIfaceInfo> selectInterfacesToDelete(int excessInterfaces, WifiIfaceInfo[] interfaces) {
        Log.d(TAG, "selectInterfacesToDelete: excessInterfaces=" + excessInterfaces + ", interfaces=" + Arrays.toString(interfaces));
        boolean lookupError = false;
        LongSparseArray<WifiIfaceInfo> orderedListLowPriority = new LongSparseArray<>();
        LongSparseArray<WifiIfaceInfo> orderedList = new LongSparseArray<>();
        int length = interfaces.length;
        int i = 0;
        while (true) {
            if (i >= length) {
                break;
            }
            WifiIfaceInfo info = interfaces[i];
            InterfaceCacheEntry cacheEntry = this.mInterfaceInfoCache.get(Pair.create(info.name, Integer.valueOf(getType(info.iface))));
            if (cacheEntry == null) {
                Log.e(TAG, "selectInterfacesToDelete: can't find cache entry with name=" + info.name);
                lookupError = true;
                break;
            }
            if (cacheEntry.isLowPriority) {
                orderedListLowPriority.append(cacheEntry.creationTime, info);
            } else {
                orderedList.append(cacheEntry.creationTime, info);
            }
            i++;
        }
        if (lookupError) {
            Log.e(TAG, "selectInterfacesToDelete: falling back to arbitrary selection");
            return Arrays.asList((WifiIfaceInfo[]) Arrays.copyOf(interfaces, excessInterfaces));
        }
        List<WifiIfaceInfo> result = new ArrayList<>(excessInterfaces);
        for (int i2 = 0; i2 < excessInterfaces; i2++) {
            int lowPriorityNextIndex = (orderedListLowPriority.size() - i2) - 1;
            if (lowPriorityNextIndex >= 0) {
                result.add(orderedListLowPriority.valueAt(lowPriorityNextIndex));
            } else {
                result.add(orderedList.valueAt(((orderedList.size() - i2) + orderedListLowPriority.size()) - 1));
            }
        }
        return result;
    }

    /* JADX WARNING: Removed duplicated region for block: B:16:0x003e A[Catch:{ RemoteException -> 0x013a }] */
    /* JADX WARNING: Removed duplicated region for block: B:18:0x0056 A[Catch:{ RemoteException -> 0x013a }] */
    /* JADX WARNING: Removed duplicated region for block: B:30:0x00a0  */
    /* JADX WARNING: Removed duplicated region for block: B:36:0x00c5  */
    /* JADX WARNING: Removed duplicated region for block: B:44:0x00f5  */
    /* JADX WARNING: Removed duplicated region for block: B:47:0x010a  */
    /* JADX WARNING: Removed duplicated region for block: B:50:0x0132  */
    private IWifiIface executeChipReconfiguration(IfaceCreationData ifaceCreationData, int ifaceType) {
        boolean isModeConfigNeeded;
        HidlSupport.Mutable<WifiStatus> statusResp;
        if (this.mDbg) {
            Log.d(TAG, "executeChipReconfiguration: ifaceCreationData=" + ifaceCreationData + ", ifaceType=" + ifaceType);
        }
        synchronized (this.mLock) {
            try {
                if (ifaceCreationData.chipInfo.currentModeIdValid) {
                    if (ifaceCreationData.chipInfo.currentModeId == ifaceCreationData.chipModeId) {
                        isModeConfigNeeded = false;
                        if (this.mDbg) {
                            Log.d(TAG, "isModeConfigNeeded=" + isModeConfigNeeded);
                        }
                        if (!isModeConfigNeeded) {
                            WifiIfaceInfo[][] wifiIfaceInfoArr = ifaceCreationData.chipInfo.ifaces;
                            for (WifiIfaceInfo[] ifaceInfos : wifiIfaceInfoArr) {
                                for (WifiIfaceInfo ifaceInfo : ifaceInfos) {
                                    removeIfaceInternal(ifaceInfo.iface);
                                }
                            }
                            WifiStatus status = ifaceCreationData.chipInfo.chip.configureChip(ifaceCreationData.chipModeId);
                            updateRttControllerOnModeChange();
                            if (status.code != 0) {
                                Log.e(TAG, "executeChipReconfiguration: configureChip error: " + statusString(status));
                                return null;
                            }
                        } else {
                            for (WifiIfaceInfo ifaceInfo2 : ifaceCreationData.interfacesToBeRemovedFirst) {
                                removeIfaceInternal(ifaceInfo2.iface);
                            }
                        }
                        statusResp = new HidlSupport.Mutable<>();
                        HidlSupport.Mutable<IWifiIface> ifaceResp = new HidlSupport.Mutable<>();
                        if (ifaceType != 0) {
                            ifaceCreationData.chipInfo.chip.createStaIface(new IWifiChip.createStaIfaceCallback(statusResp, ifaceResp) {
                                /* class com.android.server.wifi.$$Lambda$HalDeviceManager$csull9RuGux3O9fMU2TmHd3K8YE */
                                private final /* synthetic */ HidlSupport.Mutable f$0;
                                private final /* synthetic */ HidlSupport.Mutable f$1;

                                {
                                    this.f$0 = r1;
                                    this.f$1 = r2;
                                }

                                @Override // android.hardware.wifi.V1_0.IWifiChip.createStaIfaceCallback
                                public final void onValues(WifiStatus wifiStatus, IWifiStaIface iWifiStaIface) {
                                    HalDeviceManager.lambda$executeChipReconfiguration$19(this.f$0, this.f$1, wifiStatus, iWifiStaIface);
                                }
                            });
                        } else if (ifaceType == 1) {
                            ifaceCreationData.chipInfo.chip.createApIface(new IWifiChip.createApIfaceCallback(statusResp, ifaceResp) {
                                /* class com.android.server.wifi.$$Lambda$HalDeviceManager$Sk1PB19thsUnVIURe7jAUQxhiGk */
                                private final /* synthetic */ HidlSupport.Mutable f$0;
                                private final /* synthetic */ HidlSupport.Mutable f$1;

                                {
                                    this.f$0 = r1;
                                    this.f$1 = r2;
                                }

                                @Override // android.hardware.wifi.V1_0.IWifiChip.createApIfaceCallback
                                public final void onValues(WifiStatus wifiStatus, IWifiApIface iWifiApIface) {
                                    HalDeviceManager.lambda$executeChipReconfiguration$20(this.f$0, this.f$1, wifiStatus, iWifiApIface);
                                }
                            });
                        } else if (ifaceType == 2) {
                            ifaceCreationData.chipInfo.chip.createP2pIface(new IWifiChip.createP2pIfaceCallback(statusResp, ifaceResp) {
                                /* class com.android.server.wifi.$$Lambda$HalDeviceManager$LydIQHqKB4e2ETtZbZ2Ps6wJmZg */
                                private final /* synthetic */ HidlSupport.Mutable f$0;
                                private final /* synthetic */ HidlSupport.Mutable f$1;

                                {
                                    this.f$0 = r1;
                                    this.f$1 = r2;
                                }

                                @Override // android.hardware.wifi.V1_0.IWifiChip.createP2pIfaceCallback
                                public final void onValues(WifiStatus wifiStatus, IWifiP2pIface iWifiP2pIface) {
                                    HalDeviceManager.lambda$executeChipReconfiguration$21(this.f$0, this.f$1, wifiStatus, iWifiP2pIface);
                                }
                            });
                        } else if (ifaceType == 3) {
                            ifaceCreationData.chipInfo.chip.createNanIface(new IWifiChip.createNanIfaceCallback(statusResp, ifaceResp) {
                                /* class com.android.server.wifi.$$Lambda$HalDeviceManager$rMUl3IrUZdoNcVrb1rqn8XExY0 */
                                private final /* synthetic */ HidlSupport.Mutable f$0;
                                private final /* synthetic */ HidlSupport.Mutable f$1;

                                {
                                    this.f$0 = r1;
                                    this.f$1 = r2;
                                }

                                @Override // android.hardware.wifi.V1_0.IWifiChip.createNanIfaceCallback
                                public final void onValues(WifiStatus wifiStatus, IWifiNanIface iWifiNanIface) {
                                    HalDeviceManager.lambda$executeChipReconfiguration$22(this.f$0, this.f$1, wifiStatus, iWifiNanIface);
                                }
                            });
                        }
                        if (((WifiStatus) statusResp.value).code == 0) {
                            Log.e(TAG, "executeChipReconfiguration: failed to create interface ifaceType=" + ifaceType + ": " + statusString((WifiStatus) statusResp.value));
                            return null;
                        }
                        return (IWifiIface) ifaceResp.value;
                    }
                }
                isModeConfigNeeded = true;
                if (this.mDbg) {
                }
                if (!isModeConfigNeeded) {
                }
                statusResp = new HidlSupport.Mutable<>();
                HidlSupport.Mutable<IWifiIface> ifaceResp2 = new HidlSupport.Mutable<>();
                if (ifaceType != 0) {
                }
                if (((WifiStatus) statusResp.value).code == 0) {
                }
            } catch (RemoteException e) {
                Log.e(TAG, "executeChipReconfiguration exception: " + e);
                return null;
            }
        }
    }

    static /* synthetic */ void lambda$executeChipReconfiguration$19(HidlSupport.Mutable statusResp, HidlSupport.Mutable ifaceResp, WifiStatus status, IWifiStaIface iface) {
        statusResp.value = status;
        ifaceResp.value = iface;
    }

    static /* synthetic */ void lambda$executeChipReconfiguration$20(HidlSupport.Mutable statusResp, HidlSupport.Mutable ifaceResp, WifiStatus status, IWifiApIface iface) {
        statusResp.value = status;
        ifaceResp.value = iface;
    }

    static /* synthetic */ void lambda$executeChipReconfiguration$21(HidlSupport.Mutable statusResp, HidlSupport.Mutable ifaceResp, WifiStatus status, IWifiP2pIface iface) {
        statusResp.value = status;
        ifaceResp.value = iface;
    }

    static /* synthetic */ void lambda$executeChipReconfiguration$22(HidlSupport.Mutable statusResp, HidlSupport.Mutable ifaceResp, WifiStatus status, IWifiNanIface iface) {
        statusResp.value = status;
        ifaceResp.value = iface;
    }

    private boolean removeIfaceInternal(IWifiIface iface) {
        String name = getName(iface);
        int type = getType(iface);
        if (this.mDbg) {
            Log.d(TAG, "removeIfaceInternal: iface(name)=" + name + ", type=" + type);
        }
        if (type == -1) {
            Log.e(TAG, "removeIfaceInternal: can't get type -- iface(name)=" + name);
            return false;
        }
        synchronized (this.mLock) {
            if (this.mWifi == null) {
                Log.e(TAG, "removeIfaceInternal: null IWifi -- iface(name)=" + name);
                return false;
            }
            IWifiChip chip = getChip(iface);
            if (chip == null) {
                Log.e(TAG, "removeIfaceInternal: null IWifiChip -- iface(name)=" + name);
                return false;
            } else if (name == null) {
                Log.e(TAG, "removeIfaceInternal: can't get name");
                return false;
            } else {
                WifiStatus status = null;
                if (type == 0) {
                    status = chip.removeStaIface(name);
                } else if (type == 1) {
                    status = chip.removeApIface(name);
                } else if (type == 2) {
                    status = chip.removeP2pIface(name);
                } else if (type != 3) {
                    try {
                        Log.wtf(TAG, "removeIfaceInternal: invalid type=" + type);
                        return false;
                    } catch (RemoteException e) {
                        Log.e(TAG, "IWifiChip.removeXxxIface exception: " + e);
                    }
                } else {
                    status = chip.removeNanIface(name);
                }
                dispatchDestroyedListeners(name, type);
                if (status != null && status.code == 0) {
                    return true;
                }
                Log.e(TAG, "IWifiChip.removeXxxIface failed: " + statusString(status));
                return false;
            }
        }
    }

    private boolean dispatchAvailableForRequestListeners() {
        Log.d(TAG, "dispatchAvailableForRequestListeners");
        synchronized (this.mLock) {
            WifiChipInfo[] chipInfos = getAllChipInfo();
            if (chipInfos == null) {
                Log.e(TAG, "dispatchAvailableForRequestListeners: no chip info found");
                stopWifi();
                return false;
            }
            Log.d(TAG, "dispatchAvailableForRequestListeners: chipInfos=" + Arrays.deepToString(chipInfos));
            int[] iArr = IFACE_TYPES_BY_PRIORITY;
            for (int ifaceType : iArr) {
                if (ifaceType == 2 || ifaceType == 3) {
                    Log.d(TAG, "Do not dispatch for iface type of p2p or nan");
                } else {
                    dispatchAvailableForRequestListenersForType(ifaceType, chipInfos);
                }
            }
            return true;
        }
    }

    private void dispatchAvailableForRequestListenersForType(int ifaceType, WifiChipInfo[] chipInfos) {
        Log.d(TAG, "dispatchAvailableForRequestListenersForType: ifaceType=" + ifaceType);
        synchronized (this.mLock) {
            Map<InterfaceAvailableForRequestListenerProxy, Boolean> listeners = this.mInterfaceAvailableForRequestListeners.get(ifaceType);
            if (listeners.size() != 0) {
                boolean isAvailable = isItPossibleToCreateIface(chipInfos, ifaceType);
                Log.d(TAG, "Interface available for: ifaceType=" + ifaceType + " = " + isAvailable);
                for (Map.Entry<InterfaceAvailableForRequestListenerProxy, Boolean> listenerEntry : listeners.entrySet()) {
                    if (listenerEntry.getValue() == null || listenerEntry.getValue().booleanValue() != isAvailable) {
                        Log.d(TAG, "Interface available listener dispatched: ifaceType=" + ifaceType + ", listener=" + listenerEntry.getKey());
                        listenerEntry.getKey().triggerWithArg(isAvailable);
                    }
                    listenerEntry.setValue(Boolean.valueOf(isAvailable));
                }
            }
        }
    }

    private void dispatchDestroyedListeners(String name, int type) {
        Log.d(TAG, "dispatchDestroyedListeners: iface(name)=" + name);
        synchronized (this.mLock) {
            InterfaceCacheEntry entry = this.mInterfaceInfoCache.get(Pair.create(name, Integer.valueOf(type)));
            if (entry == null) {
                Log.e(TAG, "dispatchDestroyedListeners: no cache entry for iface(name)=" + name);
                return;
            }
            for (InterfaceDestroyedListenerProxy listener : entry.destroyedListeners) {
                listener.trigger();
            }
            entry.destroyedListeners.clear();
            this.mInterfaceInfoCache.remove(Pair.create(name, Integer.valueOf(type)));
        }
    }

    private void dispatchAllDestroyedListeners() {
        Log.d(TAG, "dispatchAllDestroyedListeners");
        synchronized (this.mLock) {
            Iterator<Map.Entry<Pair<String, Integer>, InterfaceCacheEntry>> it = this.mInterfaceInfoCache.entrySet().iterator();
            while (it.hasNext()) {
                InterfaceCacheEntry entry = it.next().getValue();
                for (InterfaceDestroyedListenerProxy listener : entry.destroyedListeners) {
                    listener.trigger();
                }
                entry.destroyedListeners.clear();
                it.remove();
            }
        }
    }

    /* access modifiers changed from: private */
    public abstract class ListenerProxy<LISTENER> {
        private Handler mHandler;
        protected LISTENER mListener;

        public boolean equals(Object obj) {
            return this.mListener == ((ListenerProxy) obj).mListener;
        }

        public int hashCode() {
            return this.mListener.hashCode();
        }

        /* access modifiers changed from: package-private */
        public void trigger() {
            Handler handler = this.mHandler;
            if (handler != null) {
                handler.post(new Runnable() {
                    /* class com.android.server.wifi.RunnableC0327x8b9715b6 */

                    public final void run() {
                        HalDeviceManager.ListenerProxy.this.lambda$trigger$0$HalDeviceManager$ListenerProxy();
                    }
                });
            } else {
                lambda$trigger$0$HalDeviceManager$ListenerProxy();
            }
        }

        /* access modifiers changed from: package-private */
        public void triggerWithArg(boolean arg) {
            Handler handler = this.mHandler;
            if (handler != null) {
                handler.post(new Runnable(arg) {
                    /* class com.android.server.wifi.RunnableC0328x69305100 */
                    private final /* synthetic */ boolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    public final void run() {
                        HalDeviceManager.ListenerProxy.this.lambda$triggerWithArg$1$HalDeviceManager$ListenerProxy(this.f$1);
                    }
                });
            } else {
                lambda$triggerWithArg$1$HalDeviceManager$ListenerProxy(arg);
            }
        }

        /* access modifiers changed from: protected */
        /* renamed from: action */
        public void lambda$trigger$0$HalDeviceManager$ListenerProxy() {
        }

        /* access modifiers changed from: protected */
        /* renamed from: actionWithArg */
        public void lambda$triggerWithArg$1$HalDeviceManager$ListenerProxy(boolean arg) {
        }

        ListenerProxy(LISTENER listener, Handler handler, String tag) {
            this.mListener = listener;
            this.mHandler = handler;
        }
    }

    /* access modifiers changed from: private */
    public class InterfaceDestroyedListenerProxy extends ListenerProxy<InterfaceDestroyedListener> {
        private final String mIfaceName;

        InterfaceDestroyedListenerProxy(String ifaceName, InterfaceDestroyedListener destroyedListener, Handler handler) {
            super(destroyedListener, handler, "InterfaceDestroyedListenerProxy");
            this.mIfaceName = ifaceName;
        }

        /* access modifiers changed from: protected */
        @Override // com.android.server.wifi.HalDeviceManager.ListenerProxy
        public void action() {
            ((InterfaceDestroyedListener) this.mListener).onDestroyed(this.mIfaceName);
        }
    }

    /* access modifiers changed from: private */
    public class InterfaceAvailableForRequestListenerProxy extends ListenerProxy<InterfaceAvailableForRequestListener> {
        InterfaceAvailableForRequestListenerProxy(InterfaceAvailableForRequestListener destroyedListener, Handler handler) {
            super(destroyedListener, handler, "InterfaceAvailableForRequestListenerProxy");
        }

        /* access modifiers changed from: protected */
        @Override // com.android.server.wifi.HalDeviceManager.ListenerProxy
        public void actionWithArg(boolean isAvailable) {
            ((InterfaceAvailableForRequestListener) this.mListener).onAvailabilityChanged(isAvailable);
        }
    }

    /* access modifiers changed from: private */
    public class InterfaceRttControllerLifecycleCallbackProxy implements InterfaceRttControllerLifecycleCallback {
        private InterfaceRttControllerLifecycleCallback mCallback;
        private Handler mHandler;

        InterfaceRttControllerLifecycleCallbackProxy(InterfaceRttControllerLifecycleCallback callback, Handler handler) {
            this.mCallback = callback;
            this.mHandler = handler;
        }

        public boolean equals(Object obj) {
            return this.mCallback == ((InterfaceRttControllerLifecycleCallbackProxy) obj).mCallback;
        }

        public int hashCode() {
            return this.mCallback.hashCode();
        }

        /* renamed from: lambda$onNewRttController$0$HalDeviceManager$InterfaceRttControllerLifecycleCallbackProxy */
        public /* synthetic */ void mo2166x65fa2706(IWifiRttController controller) {
            this.mCallback.onNewRttController(controller);
        }

        @Override // com.android.server.wifi.HalDeviceManager.InterfaceRttControllerLifecycleCallback
        public void onNewRttController(IWifiRttController controller) {
            this.mHandler.post(new Runnable(controller) {
                /* class com.android.server.wifi.RunnableC0326x47a5a35d */
                private final /* synthetic */ IWifiRttController f$1;

                {
                    this.f$1 = r2;
                }

                public final void run() {
                    HalDeviceManager.InterfaceRttControllerLifecycleCallbackProxy.this.mo2166x65fa2706(this.f$1);
                }
            });
        }

        /* renamed from: lambda$onRttControllerDestroyed$1$HalDeviceManager$InterfaceRttControllerLifecycleCallbackProxy */
        public /* synthetic */ void mo2167xa613fe64() {
            this.mCallback.onRttControllerDestroyed();
        }

        @Override // com.android.server.wifi.HalDeviceManager.InterfaceRttControllerLifecycleCallback
        public void onRttControllerDestroyed() {
            this.mHandler.post(new Runnable() {
                /* class com.android.server.wifi.RunnableC0325xb8cc581d */

                public final void run() {
                    HalDeviceManager.InterfaceRttControllerLifecycleCallbackProxy.this.mo2167xa613fe64();
                }
            });
        }
    }

    private void dispatchRttControllerLifecycleOnNew() {
        Log.v(TAG, "dispatchRttControllerLifecycleOnNew: # cbs=" + this.mRttControllerLifecycleCallbacks.size());
        for (InterfaceRttControllerLifecycleCallbackProxy cbp : this.mRttControllerLifecycleCallbacks) {
            cbp.onNewRttController(this.mIWifiRttController);
        }
    }

    private void dispatchRttControllerLifecycleOnDestroyed() {
        for (InterfaceRttControllerLifecycleCallbackProxy cbp : this.mRttControllerLifecycleCallbacks) {
            cbp.onRttControllerDestroyed();
        }
    }

    private void updateRttControllerOnModeChange() {
        synchronized (this.mLock) {
            boolean controllerDestroyed = this.mIWifiRttController != null;
            this.mIWifiRttController = null;
            if (this.mRttControllerLifecycleCallbacks.size() == 0) {
                Log.d(TAG, "updateRttController: no one is interested in RTT controllers");
                return;
            }
            IWifiRttController newRttController = createRttControllerIfPossible();
            if (newRttController != null) {
                this.mIWifiRttController = newRttController;
                dispatchRttControllerLifecycleOnNew();
            } else if (controllerDestroyed) {
                dispatchRttControllerLifecycleOnDestroyed();
            }
        }
    }

    private IWifiRttController createRttControllerIfPossible() {
        synchronized (this.mLock) {
            if (!isWifiStarted()) {
                Log.d(TAG, "createRttControllerIfPossible: Wifi is not started");
                return null;
            }
            WifiChipInfo[] chipInfos = getAllChipInfo();
            if (chipInfos == null) {
                Log.d(TAG, "createRttControllerIfPossible: no chip info found - most likely chip not up yet");
                return null;
            }
            for (WifiChipInfo chipInfo : chipInfos) {
                if (!chipInfo.currentModeIdValid) {
                    Log.d(TAG, "createRttControllerIfPossible: chip not configured yet: " + chipInfo);
                } else {
                    HidlSupport.Mutable<IWifiRttController> rttResp = new HidlSupport.Mutable<>();
                    try {
                        chipInfo.chip.createRttController(null, new IWifiChip.createRttControllerCallback(rttResp) {
                            /* class com.android.server.wifi.$$Lambda$HalDeviceManager$QqmQshmXW8IANXptIlwDeQYupP8 */
                            private final /* synthetic */ HidlSupport.Mutable f$0;

                            {
                                this.f$0 = r1;
                            }

                            @Override // android.hardware.wifi.V1_0.IWifiChip.createRttControllerCallback
                            public final void onValues(WifiStatus wifiStatus, IWifiRttController iWifiRttController) {
                                HalDeviceManager.lambda$createRttControllerIfPossible$23(this.f$0, wifiStatus, iWifiRttController);
                            }
                        });
                    } catch (RemoteException e) {
                        Log.e(TAG, "IWifiChip.createRttController exception: " + e);
                    }
                    if (rttResp.value != null) {
                        return (IWifiRttController) rttResp.value;
                    }
                }
            }
            Log.w(TAG, "createRttControllerIfPossible: not available from any of the chips");
            return null;
        }
    }

    static /* synthetic */ void lambda$createRttControllerIfPossible$23(HidlSupport.Mutable rttResp, WifiStatus status, IWifiRttController rtt) {
        if (status.code == 0) {
            rttResp.value = rtt;
            return;
        }
        Log.e(TAG, "IWifiChip.createRttController failed: " + statusString(status));
    }

    /* access modifiers changed from: private */
    public static String statusString(WifiStatus status) {
        if (status == null) {
            return "status=null";
        }
        return status.code + " (" + status.description + ")";
    }

    private static int getType(IWifiIface iface) {
        MutableInt typeResp = new MutableInt(-1);
        try {
            iface.getType(new IWifiIface.getTypeCallback(typeResp) {
                /* class com.android.server.wifi.$$Lambda$HalDeviceManager$JoqknyvMzMZELG3uBlv8IAlpj1k */
                private final /* synthetic */ MutableInt f$0;

                {
                    this.f$0 = r1;
                }

                @Override // android.hardware.wifi.V1_0.IWifiIface.getTypeCallback
                public final void onValues(WifiStatus wifiStatus, int i) {
                    HalDeviceManager.lambda$getType$24(this.f$0, wifiStatus, i);
                }
            });
        } catch (RemoteException e) {
            Log.e(TAG, "Exception on getType: " + e);
        }
        return typeResp.value;
    }

    static /* synthetic */ void lambda$getType$24(MutableInt typeResp, WifiStatus status, int type) {
        if (status.code == 0) {
            typeResp.value = type;
            return;
        }
        Log.e(TAG, "Error on getType: " + statusString(status));
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("HalDeviceManager:");
        pw.println("  mServiceManager: " + this.mServiceManager);
        pw.println("  mWifi: " + this.mWifi);
        pw.println("  mManagerStatusListeners: " + this.mManagerStatusListeners);
        pw.println("  mInterfaceAvailableForRequestListeners: " + this.mInterfaceAvailableForRequestListeners);
        pw.println("  mInterfaceInfoCache: " + this.mInterfaceInfoCache);
    }
}

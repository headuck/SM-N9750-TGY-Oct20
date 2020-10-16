package com.samsung.android.server.wifi.share;

import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import com.samsung.android.mcf.CasterCallback;
import com.samsung.android.mcf.McfAdapter;
import com.samsung.android.mcf.McfCaster;
import com.samsung.android.mcf.McfDevice;
import com.samsung.android.mcf.discovery.McfAdvertiseCallback;
import com.samsung.android.mcf.discovery.McfAdvertiseData;
import com.samsung.android.mcf.discovery.McfDeviceDiscoverCallback;
import com.samsung.android.mcf.discovery.McfScanData;
import com.samsung.android.server.wifi.share.McfController;
import com.samsung.android.server.wifi.share.McfDataUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/* access modifiers changed from: package-private */
public class SemCasterManager {
    private static final int AUTHENTICATION_CASE = 0;
    private static final int AUTH_TYPE_ONE_WAY = 0;
    private static final int AUTH_TYPE_TWO_WAY = 1;
    private static final int DELAY_ADVERTISE = 2;
    private static final String DEVICE_NAME_ME = "-ME---";
    private static final int SEND_ADVERTISE_DIRECT = 0;
    private static final int SEND_PASSWORD_CASE = 1;
    private static final int STOP_ADVERTISE = 1;
    private static final boolean mFlagShowDataLog = true;
    private static boolean mHasMultipleConfigKey;
    private static boolean mIsAuthAdvertiseTriggered;
    private static boolean mIsSendPasswordAdvertiseTriggered;
    private static final Object mLock = new Object();
    private static JSONObject mMultipleConfigKeyJsonObject = new JSONObject();
    private final String TAG = "WifiProfileShare.McfCast";
    private AdvertiseHandler mAdvertiseHandler = new AdvertiseHandler();
    private ICasterCallback mCallback;
    private CasterCallback mCasterCallback = new CasterCallback() {
        /* class com.samsung.android.server.wifi.share.SemCasterManager.C07621 */

        public void onMcfServiceStateChanged(int status, int i1) {
            SemCasterManager.super.onMcfServiceStateChanged(status, i1);
            Log.v("WifiProfileShare.McfCast", "-ME--- onMcfServiceStateChanged, status : " + status + ", i1 : " + i1);
            if (SemCasterManager.this.mMcfCaster != null && 2 == status && 1 == i1) {
                SemCasterManager semCasterManager = SemCasterManager.this;
                semCasterManager.mIsNetworkEnabled = semCasterManager.mMcfCaster.isNetworkEnabled(1);
            }
        }
    };
    private byte[] mContentsByteForQos;
    private boolean mIsNetworkEnabled;
    private boolean mIsPasswordLowLatency;
    private boolean mIsRegisteredAdvData;
    private boolean mIsScanTriggered;
    private McfCaster mMcfCaster;
    private McfDeviceDiscoverCallback mPassMcfDeviceDiscoverCallback = new McfDeviceDiscoverCallback() {
        /* class com.samsung.android.server.wifi.share.SemCasterManager.C07643 */

        public void onDeviceDiscovered(McfDevice mcfDevice, int i) {
            if (mcfDevice == null) {
                Log.e("WifiProfileShare.McfCast", "-ME--- onDeviceDiscovered(pass) mcfDevice is null");
                return;
            }
            String deviceId = mcfDevice.getDeviceID();
            byte[] deliveredBytes = mcfDevice.getContentsByte();
            McfDataUtil.McfData deliveredMcfData = null;
            if (deliveredBytes != null) {
                deliveredMcfData = McfDataUtil.getMcfDataForPassword(deliveredBytes);
            }
            JSONObject jsonData = mcfDevice.getContentsJson();
            Log.v("WifiProfileShare.McfCast", deviceId + " received message auth:" + mcfDevice.getAdditionalAuthType() + " contact : " + mcfDevice.isInContact() + " json:" + jsonData + " content:" + deliveredMcfData);
            McfController.AdvertiseState state = McfController.AdvertiseState.NONE;
            if (jsonData != null) {
                if (jsonData.has("state")) {
                    try {
                        state = McfController.AdvertiseState.valueOf((String) jsonData.get("state"));
                    } catch (JSONException e) {
                        Log.e("WifiProfileShare.McfCast", "can not get state");
                    }
                }
            } else if (deliveredMcfData != null) {
                state = deliveredMcfData.isPasswordCancelData() ? McfController.AdvertiseState.CLOSE : McfController.AdvertiseState.DEVICE_DETECTED;
            } else {
                Log.e("WifiProfileShare.McfCast", deviceId + " content bytes is null");
                return;
            }
            Log.d("WifiProfileShare.McfCast", deviceId + " process state:" + state.name());
            int i2 = C07654.f58x6b45a697[state.ordinal()];
            if (i2 == 1) {
                McfDataUtil.McfData sharedData = SemCasterManager.this.isMatchedSharedData(mcfDevice, deliveredMcfData);
                if (sharedData != null) {
                    SemCasterManager.this.startAuthentication(mcfDevice, sharedData);
                }
            } else if (i2 == 2) {
                String userData = SemCasterManager.this.getContactInfo(mcfDevice);
                if (userData != null && SemCasterManager.this.getRequestData(userData) != null) {
                    SemCasterManager.this.sendPasswordData(false, userData);
                }
            } else if (i2 != 3) {
                if (i2 != 4) {
                    Log.e("WifiProfileShare.McfCast", deviceId + " unhandled state: " + state.name());
                    return;
                }
                SemCasterManager.this.closePasswordSession(deviceId, true);
            } else if (mcfDevice.getAdditionalAuthType() == 1) {
                McfDataUtil.McfData requestData = McfDataUtil.getMcfData(jsonData);
                if (SemCasterManager.this.isMatchedSharedData(mcfDevice, requestData) == null) {
                    Log.e("WifiProfileShare.McfCast", deviceId + " config not matched");
                    return;
                }
                String contactInfo = SemCasterManager.this.getContactInfo(mcfDevice);
                if (contactInfo == null) {
                    Log.e("WifiProfileShare.McfCast", deviceId + " contact info is null");
                    return;
                }
                SemCasterManager.this.showPasswordConfirmPopup(mcfDevice, contactInfo, requestData);
            } else {
                Log.e("WifiProfileShare.McfCast", deviceId + " failed to show confirm dialog, unauthorized");
            }
        }

        public void onDeviceRemoved(McfDevice mcfDevice, int i) {
            if (mcfDevice == null) {
                Log.e("WifiProfileShare.McfCast", "-ME---onDeviceRemoved(pass) McfDevice is null");
                return;
            }
            Log.d("WifiProfileShare.McfCast", mcfDevice.getDeviceID() + " onDeviceRemoved, dismiss dialog");
            byte[] userData = mcfDevice.getContactKey();
            if (userData != null) {
                SemCasterManager.this.mCallback.onSessionClosed(McfDataUtil.McfData.byteArrayToString(userData));
            }
            SemCasterManager.this.closePasswordSession(mcfDevice.getDeviceID(), true);
        }
    };
    private long mPasswordCasterStartAt;
    private final Map<String, RequestData> mPasswordRequestedDevices = new HashMap();
    private final ArrayList<String> mPasswordRequestingDevices = new ArrayList<>();
    private McfDeviceDiscoverCallback mQosMcfDeviceDiscoverCallback = new McfDeviceDiscoverCallback() {
        /* class com.samsung.android.server.wifi.share.SemCasterManager.C07632 */

        public void onDeviceDiscovered(McfDevice mcfDevice, int i) {
            if (mcfDevice == null) {
                Log.e("WifiProfileShare.McfCast", "-ME--- mQosMcfDeviceDiscoverCallback.onDeviceDiscovered mcfDevice is null");
                return;
            }
            Log.d("WifiProfileShare.McfCast", mcfDevice.getDeviceID() + " sent qos information");
        }

        public void onDeviceRemoved(McfDevice mcfDevice, int i) {
            if (mcfDevice == null) {
                Log.e("WifiProfileShare.McfCast", "-ME--- mQosMcfDeviceDiscoverCallback.onDeviceRemoved mcfDevice is null");
                return;
            }
            Log.d("WifiProfileShare.McfCast", mcfDevice.getDeviceID() + " onDeviceRemoved(qos)");
        }
    };
    private List<McfDataUtil.McfData> mSharedPasswordData = null;

    SemCasterManager() {
    }

    /* access modifiers changed from: package-private */
    public void openCaster(McfAdapter adapter) {
        if (adapter == null) {
            Log.e("WifiProfileShare.McfCast", "-ME--- openCaster, adapter is null");
            return;
        }
        try {
            this.mMcfCaster = adapter.getCaster(4, this.mCasterCallback);
            this.mAdvertiseHandler.setMcfCaster(this.mMcfCaster);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        if (this.mMcfCaster == null) {
            Log.e("WifiProfileShare.McfCast", "-ME--- openCaster, failed to open caster");
        }
    }

    /* access modifiers changed from: package-private */
    public void closeCaster(McfAdapter adapter) {
        if (this.mMcfCaster == null) {
            Log.d("WifiProfileShare.McfCast", "-ME--- closeCaster, already closed");
            return;
        }
        closeCaster();
        if (this.mIsNetworkEnabled) {
            this.mIsNetworkEnabled = false;
        }
        if (adapter != null) {
            try {
                adapter.closeCaster(4);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        this.mMcfCaster = null;
    }

    /* access modifiers changed from: package-private */
    public void closeCaster() {
        if (this.mMcfCaster == null) {
            Log.d("WifiProfileShare.McfCast", "-ME--- closeCaster, already closed");
            return;
        }
        if (this.mIsScanTriggered) {
            stopScanForPassword();
        }
        if (this.mIsRegisteredAdvData) {
            stopScanForQoS();
        }
        synchronized (mLock) {
            Iterator<String> it = this.mPasswordRequestingDevices.iterator();
            while (it.hasNext()) {
                closePasswordSession(it.next(), false);
            }
            this.mPasswordRequestingDevices.clear();
        }
        this.mContentsByteForQos = null;
    }

    /* access modifiers changed from: package-private */
    public boolean isOpened() {
        return this.mMcfCaster != null;
    }

    /* access modifiers changed from: package-private */
    public boolean isNetworkEnable() {
        McfCaster mcfCaster = this.mMcfCaster;
        if (mcfCaster != null) {
            this.mIsNetworkEnabled = mcfCaster.isNetworkEnabled(1);
        }
        Log.d("WifiProfileShare.McfCast", "-ME--- mIsNetworkEnabled :" + this.mIsNetworkEnabled);
        return this.mIsNetworkEnabled;
    }

    /* access modifiers changed from: package-private */
    public void startScanForQoS(McfDataUtil.McfData qosData) {
        Log.d("WifiProfileShare.McfCast", "-ME--- startScanForQoS qosData:" + qosData);
        if (qosData != null) {
            McfAdvertiseData advData = new McfAdvertiseData.Builder().setAdvertiseData(4, false, true).setByteContent(qosData.getByteArrayForSharing()).build();
            McfScanData scanData = new McfScanData.Builder().setScanData(4, false, true).build();
            this.mIsRegisteredAdvData = true;
            this.mMcfCaster.registerAdvertiseData(scanData, advData, this.mQosMcfDeviceDiscoverCallback);
        }
    }

    /* access modifiers changed from: package-private */
    public void stopScanForQoS() {
        Log.d("WifiProfileShare.McfCast", "-ME--- unregisterPilotScan !!");
        this.mIsRegisteredAdvData = false;
        McfCaster mcfCaster = this.mMcfCaster;
        if (mcfCaster != null) {
            mcfCaster.unregisterAdvertiseData(this.mQosMcfDeviceDiscoverCallback);
        }
    }

    /* access modifiers changed from: package-private */
    public boolean isEnabledQoSSharing() {
        return this.mIsRegisteredAdvData;
    }

    /* access modifiers changed from: package-private */
    public int updateQoSData(byte[] contents) {
        if (!this.mIsRegisteredAdvData) {
            Log.e("WifiProfileShare.McfCast", "-ME--- updateQoSData failed, not registered");
            return -1;
        } else if (contents == null) {
            Log.e("WifiProfileShare.McfCast", "updateQoSData failed, contents data is null");
            return -1;
        } else if (Arrays.equals(this.mContentsByteForQos, contents)) {
            return -1;
        } else {
            Log.d("WifiProfileShare.McfCast", "-ME--- updateQoSData, contents data is different from the previous data");
            this.mContentsByteForQos = contents;
            return this.mMcfCaster.updateAdvertiseData(new McfAdvertiseData.Builder().setAdvertiseData(4, false, true).setByteContent(contents).build(), this.mQosMcfDeviceDiscoverCallback);
        }
    }

    /* access modifiers changed from: package-private */
    public void setScanMode(boolean lowLatency) {
        this.mIsPasswordLowLatency = lowLatency;
    }

    /* access modifiers changed from: package-private */
    public void startScanForPassword(List<McfDataUtil.McfData> pwdDataInfos, ICasterCallback callback) {
        Log.d("WifiProfileShare.McfCast", "-ME--- startScanForPassword pwdData size:" + pwdDataInfos.size() + ", mIsPasswordLowLatency : " + this.mIsPasswordLowLatency);
        if (this.mMcfCaster != null) {
            this.mCallback = callback;
            this.mSharedPasswordData = pwdDataInfos;
            McfScanData.Builder builder = new McfScanData.Builder().setTimeout(0).setScanData(4, true, false);
            if (this.mIsPasswordLowLatency) {
                builder.setScanMode(3);
            }
            this.mIsScanTriggered = true;
            this.mMcfCaster.startScan(builder.build(), this.mPassMcfDeviceDiscoverCallback);
            this.mPasswordCasterStartAt = SystemClock.elapsedRealtime();
            if (pwdDataInfos.size() > 1) {
                mHasMultipleConfigKey = true;
                mMultipleConfigKeyJsonObject = makeJsonObjectForMultipleConfigKey(pwdDataInfos, this.mPasswordCasterStartAt);
                return;
            }
            mHasMultipleConfigKey = false;
        }
    }

    /* access modifiers changed from: package-private */
    public void updatePasswordDate(List<McfDataUtil.McfData> pwdDataInfos) {
        if (pwdDataInfos != null && pwdDataInfos.size() != 0) {
            Log.d("WifiProfileShare.McfCast", "-ME--- updatePasswordDate pwdData size:" + pwdDataInfos.size());
            this.mSharedPasswordData = pwdDataInfos;
            if (pwdDataInfos.size() > 1) {
                mHasMultipleConfigKey = true;
                mMultipleConfigKeyJsonObject = makeJsonObjectForMultipleConfigKey(pwdDataInfos, this.mPasswordCasterStartAt);
                return;
            }
            mHasMultipleConfigKey = false;
        }
    }

    private JSONObject makeJsonObjectForMultipleConfigKey(List<McfDataUtil.McfData> pwdDataInfos, long passwordCasterStartAt) {
        int roopCount = 0;
        JSONObject jsonObject = new JSONObject();
        for (McfDataUtil.McfData data : pwdDataInfos) {
            int roopCount2 = roopCount + 1;
            if (roopCount == 0) {
                jsonObject = data.getPasswordJsonData(McfController.AdvertiseState.AUTHENTICATION.name(), false, passwordCasterStartAt);
            } else {
                try {
                    jsonObject.put(McfDataUtil.McfData.JSON_CONFIGKEY_HOTSPOT, data.getConfigKey());
                } catch (JSONException e) {
                    Log.e("WifiProfileShare.McfCast", "JSONException occured");
                }
            }
            roopCount = roopCount2;
        }
        return jsonObject;
    }

    /* access modifiers changed from: package-private */
    public void stopScanForPassword() {
        Log.d("WifiProfileShare.McfCast", "-ME--- stopScan");
        this.mIsScanTriggered = false;
        McfCaster mcfCaster = this.mMcfCaster;
        if (mcfCaster != null) {
            mcfCaster.stopScan(this.mPassMcfDeviceDiscoverCallback);
        }
    }

    /* access modifiers changed from: package-private */
    public boolean isEnabledSharingPassword() {
        return this.mIsScanTriggered;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private RequestData getRequestData(String userData) {
        if (userData == null) {
            return null;
        }
        synchronized (mLock) {
            for (RequestData requester : this.mPasswordRequestedDevices.values()) {
                if (userData.equals(requester.contactKey)) {
                    return requester;
                }
            }
            return null;
        }
    }

    /* access modifiers changed from: package-private */
    public void sendPasswordData(boolean isAccept, String userData) {
        RequestData target = getRequestData(userData);
        if (target == null) {
            Log.e("WifiProfileShare.McfCast", "-ME--- sendPasswordData failed, can not found requester " + userData);
            return;
        }
        McfDataUtil.McfData sharePasswordData = null;
        if (isAccept) {
            Iterator<McfDataUtil.McfData> it = this.mSharedPasswordData.iterator();
            while (true) {
                if (it.hasNext()) {
                    McfDataUtil.McfData data = it.next();
                    String configKey = data.getConfigKey();
                    if (configKey != null && configKey.equals(target.configKey)) {
                        sharePasswordData = data;
                        break;
                    }
                } else {
                    break;
                }
            }
        } else {
            sharePasswordData = McfDataUtil.getMcfDataForCancelingPassword(target.configKey);
        }
        McfDevice mcfDevice = target.mcfDevice;
        if (mcfDevice == null) {
            Log.e("WifiProfileShare.McfCast", "-ME--- sendPasswordData failed, target device is null");
        } else if (sharePasswordData == null) {
            Log.e("WifiProfileShare.McfCast", mcfDevice.getDeviceID() + " sendPasswordData failed, not exist shared data");
        } else {
            this.mAdvertiseHandler.setDeviceData(mcfDevice, sharePasswordData, this.mPasswordCasterStartAt, false);
            if (mIsSendPasswordAdvertiseTriggered) {
                Log.d("WifiProfileShare.McfCast", "sendPassword,  send password advertise already started! delay start");
                this.mAdvertiseHandler.delayRestartAuthAdvertiseForMultipleSendPassword(true);
                sendAdvertiseMessage(0, 1, 1000);
                sendAdvertiseMessage(1, 1, 2000);
                return;
            }
            if (mIsAuthAdvertiseTriggered) {
                Log.d("WifiProfileShare.McfCast", "sendPassword,  auth advertise already started ! stop auth advertise!");
                this.mAdvertiseHandler.setRestartAuthAdvertise(true);
                sendAdvertiseMessage(1, 0, 0);
            }
            Log.d("WifiProfileShare.McfCast", "sendPassword,  start send password advertise ! ");
            sendAdvertiseMessage(0, 1, 0);
            sendAdvertiseMessage(1, 1, 1000);
        }
    }

    /* access modifiers changed from: package-private */
    public void clearUserRequestPasswordHistory() {
        synchronized (mLock) {
            if (!this.mPasswordRequestingDevices.isEmpty()) {
                this.mAdvertiseHandler.removeMessages(1);
                sendAdvertiseMessage(1, 0, 0);
            }
            this.mPasswordRequestingDevices.clear();
            this.mPasswordRequestedDevices.clear();
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void closePasswordSession(String deviceId, boolean removeRequestingDevice) {
        RequestData reqInfo;
        synchronized (mLock) {
            if (this.mPasswordRequestedDevices.containsKey(deviceId) && (reqInfo = this.mPasswordRequestedDevices.get(deviceId)) != null) {
                Log.v("WifiProfileShare.McfCast", deviceId + " closePasswordSession, close popup");
                this.mCallback.onSessionClosed(reqInfo.contactKey);
            }
            if (!this.mPasswordRequestingDevices.isEmpty()) {
                this.mAdvertiseHandler.removeMessages(1);
                sendAdvertiseMessage(1, 0, 0);
                if (removeRequestingDevice && this.mPasswordRequestingDevices.contains(deviceId)) {
                    this.mPasswordRequestingDevices.remove(deviceId);
                    Log.i("WifiProfileShare.McfCast", "delete " + deviceId + " PasswordRequestingDevice list, because caster received bye advertise from subscriber!");
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    /* JADX WARNING: Removed duplicated region for block: B:3:0x0010  */
    private McfDataUtil.McfData isMatchedSharedData(McfDevice mcfDevice, McfDataUtil.McfData request) {
        String deviceId = mcfDevice.getDeviceID();
        for (McfDataUtil.McfData shareData : this.mSharedPasswordData) {
            if (shareData.matches(request) || shareData.maybeRoaming(request) || TextUtils.equals(shareData.getConfigKey(), request.getConfigKey())) {
                Log.i("WifiProfileShare.McfCast", deviceId + " wants Wi-Fi profile");
                return shareData;
            }
            while (r1.hasNext()) {
            }
        }
        return null;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    /* JADX WARNING: Code restructure failed: missing block: B:19:0x0065, code lost:
        if (com.samsung.android.server.wifi.share.SemCasterManager.mIsSendPasswordAdvertiseTriggered == false) goto L_0x0083;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:20:0x0067, code lost:
        android.util.Log.d("WifiProfileShare.McfCast", "startAuthentication,  now send password advertising ! 3 seconds wait & start Advertise");
        r11.mAdvertiseHandler.setDeviceData(r12, r13, r11.mPasswordCasterStartAt, true);
        sendAdvertiseMessage(0, 0, 3000);
        sendAdvertiseMessage(1, 0, 18000);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:22:0x0085, code lost:
        if (com.samsung.android.server.wifi.share.SemCasterManager.mIsAuthAdvertiseTriggered == false) goto L_0x0095;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:23:0x0087, code lost:
        android.util.Log.v("WifiProfileShare.McfCast", "-ME--- startAuthentication already triggered advertise, delay advertise time");
        r11.mAdvertiseHandler.sendEmptyMessage(2);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:24:0x0095, code lost:
        r11.mAdvertiseHandler.setDeviceData(r12, r13, r11.mPasswordCasterStartAt, true);
        sendAdvertiseMessage(0, 0, 0);
        sendAdvertiseMessage(1, 0, 15000);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:25:0x00a9, code lost:
        android.util.Log.i("WifiProfileShare.McfCast", r0 + " found new requester, start authentication");
        r2 = com.samsung.android.server.wifi.share.SemCasterManager.mLock;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:26:0x00c1, code lost:
        monitor-enter(r2);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:28:?, code lost:
        r11.mPasswordRequestingDevices.add(r0);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:29:0x00c7, code lost:
        monitor-exit(r2);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:30:0x00c8, code lost:
        return;
     */
    private void startAuthentication(McfDevice mcfDevice, McfDataUtil.McfData mcfData) {
        String deviceId = mcfDevice.getDeviceID();
        if (mcfData == null) {
            Log.e("WifiProfileShare.McfCast", deviceId + " skip to start authentication, no data");
            return;
        }
        synchronized (mLock) {
            if (this.mPasswordRequestedDevices.containsKey(deviceId)) {
                Log.v("WifiProfileShare.McfCast", deviceId + " found requester but already confirmed");
            } else if (this.mPasswordRequestingDevices.contains(deviceId)) {
                Log.v("WifiProfileShare.McfCast", deviceId + " found requester but already triggered");
            }
        }
    }

    private void sendAdvertiseMessage(int what, int arg1, long delay) {
        Message msg = new Message();
        msg.what = what;
        msg.arg1 = arg1;
        if (delay != 0) {
            this.mAdvertiseHandler.sendMessageDelayed(msg, delay);
        } else {
            this.mAdvertiseHandler.sendMessage(msg);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private String getContactInfo(McfDevice mcfDevice) {
        byte[] contactInfo = mcfDevice.getContactKey();
        if (contactInfo != null) {
            return McfDataUtil.McfData.byteArrayToString(contactInfo);
        }
        Log.e("WifiProfileShare.McfCast", mcfDevice.getDeviceID() + " not exit contact key");
        return null;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void showPasswordConfirmPopup(McfDevice mcfDevice, String contactInfo, McfDataUtil.McfData mcfData) {
        String deviceId = mcfDevice.getDeviceID();
        if (contactInfo == null) {
            Log.e("WifiProfileShare.McfCast", deviceId + " user data is null");
            return;
        }
        synchronized (mLock) {
            if (!this.mPasswordRequestingDevices.contains(deviceId) && mcfDevice.isInContact() != 1) {
                Log.e("WifiProfileShare.McfCast", deviceId + " may not be friend (unauthorized)");
            } else if (this.mPasswordRequestedDevices.containsKey(deviceId)) {
                Log.v("WifiProfileShare.McfCast", deviceId + " skip already requested");
            } else {
                this.mPasswordRequestedDevices.put(deviceId, new RequestData(contactInfo, mcfData.getConfigKey(), mcfDevice));
                Log.v("WifiProfileShare.McfCast", deviceId + " showPasswordConfirmPopup" + " contact:" + contactInfo);
                this.mCallback.onPasswordRequested(mcfData, contactInfo);
            }
        }
    }

    /* renamed from: com.samsung.android.server.wifi.share.SemCasterManager$4 */
    static /* synthetic */ class C07654 {

        /* renamed from: $SwitchMap$com$samsung$android$server$wifi$share$McfController$AdvertiseState */
        static final /* synthetic */ int[] f58x6b45a697 = new int[McfController.AdvertiseState.values().length];

        static {
            try {
                f58x6b45a697[McfController.AdvertiseState.DEVICE_DETECTED.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                f58x6b45a697[McfController.AdvertiseState.GATT_CONNECTED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                f58x6b45a697[McfController.AdvertiseState.REQUEST.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                f58x6b45a697[McfController.AdvertiseState.CLOSE.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
        }
    }

    public static class AdvertiseHandler extends Handler {
        private static final String TAG = "WifiProfileShare.Handler";
        private DeviceData mAuthDeviceData = new DeviceData();
        private boolean mDelayRestartAuthAdvertise;
        private boolean mIsRestartAuthAdvertise;
        private McfAdvertiseCallback mMcfAdvertiseCallback = new McfAdvertiseCallback() {
            /* class com.samsung.android.server.wifi.share.SemCasterManager.AdvertiseHandler.C07661 */
        };
        private McfCaster mMcfCaster;
        private DeviceData mSendPasswordDeviceData = new DeviceData();

        /* access modifiers changed from: package-private */
        public void setDeviceData(McfDevice device, McfDataUtil.McfData data, long passwordCasterStartAt, boolean isAuthDeviceData) {
            Log.d(TAG, "set argument ");
            if (isAuthDeviceData) {
                this.mAuthDeviceData.setDeviceData(device, data, passwordCasterStartAt);
            } else {
                this.mSendPasswordDeviceData.setDeviceData(device, data, passwordCasterStartAt);
            }
        }

        /* access modifiers changed from: package-private */
        public void setMcfCaster(McfCaster mcfCaster) {
            this.mMcfCaster = mcfCaster;
        }

        /* access modifiers changed from: package-private */
        public void setRestartAuthAdvertise(boolean enable) {
            Log.d(TAG, "setRestartAuthAdvertise : " + enable);
            this.mIsRestartAuthAdvertise = enable;
        }

        /* access modifiers changed from: package-private */
        public void delayRestartAuthAdvertiseForMultipleSendPassword(boolean enable) {
            Log.d(TAG, "delayRestartAuthAdvertiseForMultipleSendPassword : " + enable);
            this.mDelayRestartAuthAdvertise = enable;
        }

        /* access modifiers changed from: package-private */
        public DeviceData getDeviceData(int isAuthDeviceData) {
            if (isAuthDeviceData == 0) {
                return this.mAuthDeviceData;
            }
            return this.mSendPasswordDeviceData;
        }

        public void handleMessage(Message msg) {
            DeviceData tempDeviceData;
            Log.d(TAG, "handleMessage msg.what : " + msg.what + ", msg.arg1 : " + msg.arg1);
            if (this.mMcfCaster == null) {
                Log.e(TAG, "MCFCaster is null !");
                return;
            }
            int i = msg.what;
            String str = "sendPassword ";
            if (i == 0) {
                if (msg.arg1 == 0) {
                    tempDeviceData = getDeviceData(0);
                } else {
                    tempDeviceData = getDeviceData(1);
                }
                McfDevice mTargetMcfDevice = tempDeviceData.getTargetMcfDevice();
                McfDataUtil.McfData mData = tempDeviceData.getMcfData();
                long mPasswordCasterStartAt = tempDeviceData.getPasswordCasterStartAt();
                McfAdvertiseData.Builder builder = new McfAdvertiseData.Builder().setAdvertiseData(4, true, false).setAccessPermission(1);
                if (msg.arg1 == 1) {
                    builder.setTargetDevice(mTargetMcfDevice);
                    String state = McfController.AdvertiseState.ACCEPT.name();
                    if (mData.isPasswordCancelData()) {
                        state = McfController.AdvertiseState.REJECT.name();
                    }
                    builder.setJsonContent(mData.getPasswordJsonData(state, !mData.isPasswordCancelData(), mPasswordCasterStartAt));
                    boolean unused = SemCasterManager.mIsSendPasswordAdvertiseTriggered = true;
                } else {
                    if (SemCasterManager.mHasMultipleConfigKey) {
                        builder.setJsonContent(SemCasterManager.mMultipleConfigKeyJsonObject);
                    } else {
                        builder.setJsonContent(mData.getPasswordJsonData(McfController.AdvertiseState.AUTHENTICATION.name(), false, mPasswordCasterStartAt));
                    }
                    boolean unused2 = SemCasterManager.mIsAuthAdvertiseTriggered = true;
                }
                if (msg.arg1 != 1) {
                    str = "auth, " + mTargetMcfDevice.getDeviceID() + " start authentication";
                }
                Log.d(TAG, str);
                this.mMcfCaster.startAdvertise(builder.build(), this.mMcfAdvertiseCallback);
            } else if (i == 1) {
                if (msg.arg1 != 1) {
                    str = "auth,  authentication timeout";
                }
                Log.v(TAG, str);
                if (SemCasterManager.mIsAuthAdvertiseTriggered || SemCasterManager.mIsSendPasswordAdvertiseTriggered) {
                    this.mMcfCaster.stopAdvertise(this.mMcfAdvertiseCallback);
                }
                boolean unused3 = SemCasterManager.mIsSendPasswordAdvertiseTriggered = false;
                boolean unused4 = SemCasterManager.mIsAuthAdvertiseTriggered = false;
                if (msg.arg1 == 1 && this.mIsRestartAuthAdvertise && !this.mDelayRestartAuthAdvertise) {
                    Log.v(TAG, "restart authentication advertise ! ");
                    Message msg1 = new Message();
                    msg1.what = 0;
                    msg1.arg1 = 0;
                    sendMessage(msg1);
                    boolean unused5 = SemCasterManager.mIsAuthAdvertiseTriggered = true;
                    Message msg2 = new Message();
                    msg2.what = 1;
                    msg2.arg1 = 0;
                    sendMessageDelayed(msg2, 15000);
                    setRestartAuthAdvertise(false);
                }
                delayRestartAuthAdvertiseForMultipleSendPassword(false);
            } else if (i == 2) {
                removeMessages(1);
                Message msg12 = new Message();
                msg12.what = 1;
                msg12.arg1 = 0;
                sendMessageDelayed(msg12, 15000);
            }
        }
    }

    /* access modifiers changed from: private */
    public static class DeviceData {
        private McfDataUtil.McfData mcfData;
        private long passwordCasterStartAt;
        private McfDevice targetMcfDevice;

        private DeviceData() {
        }

        /* access modifiers changed from: package-private */
        public void setDeviceData(McfDevice device, McfDataUtil.McfData data, long passwordCasterStartAt2) {
            this.targetMcfDevice = device;
            this.mcfData = data;
            this.passwordCasterStartAt = passwordCasterStartAt2;
        }

        /* access modifiers changed from: package-private */
        public McfDevice getTargetMcfDevice() {
            return this.targetMcfDevice;
        }

        /* access modifiers changed from: package-private */
        public McfDataUtil.McfData getMcfData() {
            return this.mcfData;
        }

        /* access modifiers changed from: package-private */
        public long getPasswordCasterStartAt() {
            return this.passwordCasterStartAt;
        }
    }

    /* access modifiers changed from: private */
    public static class RequestData {
        String configKey;
        String contactKey;
        McfDevice mcfDevice;

        RequestData(String contactKey2, String configKey2, McfDevice device) {
            this.contactKey = contactKey2;
            this.configKey = configKey2;
            this.mcfDevice = device;
        }
    }
}

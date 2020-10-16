package com.samsung.android.server.wifi;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import com.samsung.android.net.wifi.OpBrandingLoader;
import com.samsung.android.server.wifi.dqa.ReportIdKey;
import java.util.HashMap;
import java.util.Map;

public class WifiGuiderFeatureController {
    private static final String KEY_AUTO_WIFI_TURN_OFF_SCAN_COUNT = "autoiwifiTurnOffScanCount";
    private static final String KEY_AUTO_WIFI_TURN_OFF_SECONDS = "autowifiTurnOffSeconds";
    private static final String KEY_BSSID_COUNT = "autowifiMaxBssidCount";
    private static final String KEY_CELL_COUNT = "autowifiMaxCellCount";
    private static final String KEY_HOTSPOT20 = "hotspot20Enabled";
    private static final String KEY_MWIPS = "wifiMWipsEnabled";
    private static final String KEY_POWERSAVE = "powersaveEnabled";
    private static final String KEY_PROFILE_REQUEST = "wifiProfileRequest";
    private static final String KEY_PROFILE_SHARE = "wifiProfileShare";
    private static final String KEY_QOS_PROVIDER = "qosDeviceShare";
    private static final String KEY_RESET = "resetAll";
    private static final String KEY_RSSI24 = "rssi24threshold";
    private static final String KEY_RSSI5 = "rssi5threshold";
    private static final String KEY_SAFEMODE = "wifiSafeMode";
    private static final String KEY_SCORE_PROVIDER = "networkScoreProvider";
    public static final String TAG = "WifiGuiderFeatureController";
    private static final String[] mKeys = {KEY_RSSI24, KEY_RSSI5, KEY_CELL_COUNT, KEY_BSSID_COUNT, KEY_SAFEMODE, KEY_HOTSPOT20, KEY_POWERSAVE, KEY_AUTO_WIFI_TURN_OFF_SECONDS, KEY_AUTO_WIFI_TURN_OFF_SCAN_COUNT, KEY_MWIPS, KEY_QOS_PROVIDER, KEY_SCORE_PROVIDER, KEY_PROFILE_SHARE, KEY_PROFILE_REQUEST};
    private static WifiGuiderFeatureController sInstance;
    private final Context mContext;
    private final HashMap<String, Integer> mFeatureDefaultMap = new HashMap<>();
    private final HashMap<String, Integer> mFeatureMap = new HashMap<>();

    private WifiGuiderFeatureController(Context context) {
        this.mContext = context;
        initDefaultMap();
    }

    public static synchronized WifiGuiderFeatureController getInstance(Context context) {
        WifiGuiderFeatureController wifiGuiderFeatureController;
        synchronized (WifiGuiderFeatureController.class) {
            if (sInstance == null) {
                sInstance = new WifiGuiderFeatureController(context);
            }
            wifiGuiderFeatureController = sInstance;
        }
        return wifiGuiderFeatureController;
    }

    private void initDefaultMap() {
        this.mFeatureDefaultMap.put(KEY_RSSI24, -78);
        this.mFeatureDefaultMap.put(KEY_RSSI5, -75);
        this.mFeatureDefaultMap.put(KEY_CELL_COUNT, 100);
        this.mFeatureDefaultMap.put(KEY_BSSID_COUNT, 10);
        this.mFeatureDefaultMap.put(KEY_POWERSAVE, 0);
        this.mFeatureDefaultMap.put(KEY_SAFEMODE, 0);
        Log.d(TAG, "getPasspointDefaultValue():" + getPasspointDefaultValue());
        this.mFeatureDefaultMap.put(KEY_HOTSPOT20, Integer.valueOf(getPasspointDefaultValue()));
        this.mFeatureDefaultMap.put(KEY_AUTO_WIFI_TURN_OFF_SECONDS, Integer.valueOf((int) ReportIdKey.ID_DHCP_FAIL));
        this.mFeatureDefaultMap.put(KEY_AUTO_WIFI_TURN_OFF_SCAN_COUNT, 3);
        this.mFeatureDefaultMap.put(KEY_MWIPS, 1);
        this.mFeatureDefaultMap.put(KEY_QOS_PROVIDER, 1);
        this.mFeatureDefaultMap.put(KEY_SCORE_PROVIDER, 1);
        this.mFeatureDefaultMap.put(KEY_PROFILE_SHARE, 1);
        this.mFeatureDefaultMap.put(KEY_PROFILE_REQUEST, 1);
    }

    private void getFeatureFromDB() {
        String[] arr;
        String str = Settings.Global.getString(this.mContext.getContentResolver(), "wifi_guider_feature_control");
        if (!(str == null || str.isEmpty())) {
            Log.d(TAG, "getFeatureFromDB:" + str);
            for (String str2 : str.replace("{", "").replace("}", "").split(",")) {
                String[] values = str2.split("=");
                if (values.length == 2) {
                    String key = values[0].trim();
                    String value = values[1];
                    Log.d(TAG, key + "=" + value);
                    try {
                        this.mFeatureMap.put(key, Integer.valueOf(Integer.parseInt(value)));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private boolean isFeatureControlDBempty() {
        String str = Settings.Global.getString(this.mContext.getContentResolver(), "wifi_guider_feature_control");
        if (str == null || str.isEmpty()) {
            return true;
        }
        return false;
    }

    private boolean checkAndReset(Bundle bundle) {
        Log.d(TAG, "checkAndReset");
        if (!bundle.containsKey(KEY_RESET) || bundle.getInt(KEY_RESET) != 1) {
            return false;
        }
        Log.d(TAG, "KEY_RESET");
        for (Map.Entry entry : this.mFeatureMap.entrySet()) {
            String key = entry.getKey();
            int currentValue = entry.getValue().intValue();
            int defaultValue = this.mFeatureDefaultMap.get(key).intValue();
            Log.d(TAG, "key:" + key + ", currentValue:" + currentValue);
            char c = 65535;
            switch (key.hashCode()) {
                case -200000289:
                    if (key.equals(KEY_POWERSAVE)) {
                        c = 1;
                        break;
                    }
                    break;
                case 667407668:
                    if (key.equals(KEY_HOTSPOT20)) {
                        c = 2;
                        break;
                    }
                    break;
                case 797931060:
                    if (key.equals(KEY_MWIPS)) {
                        c = 3;
                        break;
                    }
                    break;
                case 934677765:
                    if (key.equals(KEY_SAFEMODE)) {
                        c = 0;
                        break;
                    }
                    break;
            }
            if (c == 0 || c == 1 || c == 2 || c == 3) {
                int dbValue = getValueFromDB(key);
                Log.d(TAG, "key:" + key + ", currentValue:" + currentValue + ", defaultValue:" + defaultValue + ", dbValue:" + dbValue);
                if (dbValue == currentValue) {
                    putValueIntoDB(key, defaultValue);
                }
            }
        }
        if (!isFeatureControlDBempty()) {
            Log.d(TAG, "try to clear guider global db and feature map");
            Settings.Global.putString(this.mContext.getContentResolver(), "wifi_guider_feature_control", "");
            this.mFeatureMap.clear();
        }
        return true;
    }

    private boolean checkAndPutIntoMap(Bundle bundle) {
        Log.d(TAG, "checkAndPutIntoMap");
        boolean isChanged = false;
        int i = 0;
        while (true) {
            String[] strArr = mKeys;
            if (i >= strArr.length) {
                return isChanged;
            }
            String key = strArr[i];
            if (bundle.containsKey(key)) {
                int inputValue = bundle.getInt(key);
                Log.d(TAG, "key:" + key + ", inputValue:" + inputValue);
                if (this.mFeatureMap.containsKey(key)) {
                    Log.d(TAG, key + " is exists, value:" + this.mFeatureMap.get(key));
                }
                if (!this.mFeatureMap.containsKey(key) || this.mFeatureMap.get(key).intValue() != inputValue) {
                    Log.d(TAG, "try to put " + key + " to " + inputValue + " into Map");
                    this.mFeatureMap.put(key, Integer.valueOf(inputValue));
                    isChanged = true;
                }
            }
            i++;
        }
    }

    private boolean checkAndPutIntoDB(Bundle bundle) {
        int currentValue;
        Log.d(TAG, "checkAndPutIntoDB");
        for (String key : bundle.keySet()) {
            Log.d(TAG, "key:" + key);
            char c = 65535;
            switch (key.hashCode()) {
                case -200000289:
                    if (key.equals(KEY_POWERSAVE)) {
                        c = 0;
                        break;
                    }
                    break;
                case 667407668:
                    if (key.equals(KEY_HOTSPOT20)) {
                        c = 2;
                        break;
                    }
                    break;
                case 797931060:
                    if (key.equals(KEY_MWIPS)) {
                        c = 3;
                        break;
                    }
                    break;
                case 934677765:
                    if (key.equals(KEY_SAFEMODE)) {
                        c = 1;
                        break;
                    }
                    break;
            }
            if (c == 0 || c == 1 || c == 2 || c == 3) {
                int inputValue = bundle.getInt(key);
                int defaultValue = this.mFeatureDefaultMap.get(key).intValue();
                int dbValue = getValueFromDB(key);
                if (this.mFeatureMap.containsKey(key)) {
                    Log.d(TAG, key + " is exists in Map");
                    currentValue = this.mFeatureMap.get(key).intValue();
                } else {
                    Log.d(TAG, "there is no " + key + " in Map");
                    currentValue = dbValue;
                }
                Log.d(TAG, "inputValue:" + inputValue + ", defaultValue:" + defaultValue + ", currentValue:" + currentValue + ", dbValue:" + dbValue);
                if (dbValue == currentValue && dbValue != inputValue) {
                    Log.d(TAG, "try to put " + key + ":" + inputValue + " into DB");
                    putValueIntoDB(key, inputValue);
                }
            }
        }
        return false;
    }

    /* JADX INFO: Can't fix incorrect switch cases order, some code will duplicate */
    private int getValueFromDB(String key) {
        boolean z;
        int defaultValue = this.mFeatureDefaultMap.get(key).intValue();
        switch (key.hashCode()) {
            case -200000289:
                if (key.equals(KEY_POWERSAVE)) {
                    z = false;
                    break;
                }
                z = true;
                break;
            case 667407668:
                if (key.equals(KEY_HOTSPOT20)) {
                    z = true;
                    break;
                }
                z = true;
                break;
            case 797931060:
                if (key.equals(KEY_MWIPS)) {
                    z = true;
                    break;
                }
                z = true;
                break;
            case 934677765:
                if (key.equals(KEY_SAFEMODE)) {
                    z = true;
                    break;
                }
                z = true;
                break;
            default:
                z = true;
                break;
        }
        if (!z) {
            return Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_adps_enable", defaultValue);
        }
        if (z) {
            return Settings.Global.getInt(this.mContext.getContentResolver(), "safe_wifi", defaultValue);
        }
        if (z) {
            return Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_hotspot20_enable", defaultValue);
        }
        if (!z) {
            return -1;
        }
        return Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_mwips", defaultValue);
    }

    /* JADX INFO: Can't fix incorrect switch cases order, some code will duplicate */
    private void putValueIntoDB(String key, int inputValue) {
        char c;
        Log.d(TAG, "putValueIntoDB " + key + ":" + inputValue);
        switch (key.hashCode()) {
            case -200000289:
                if (key.equals(KEY_POWERSAVE)) {
                    c = 0;
                    break;
                }
                c = 65535;
                break;
            case 667407668:
                if (key.equals(KEY_HOTSPOT20)) {
                    c = 2;
                    break;
                }
                c = 65535;
                break;
            case 797931060:
                if (key.equals(KEY_MWIPS)) {
                    c = 3;
                    break;
                }
                c = 65535;
                break;
            case 934677765:
                if (key.equals(KEY_SAFEMODE)) {
                    c = 1;
                    break;
                }
                c = 65535;
                break;
            default:
                c = 65535;
                break;
        }
        if (c == 0) {
            Settings.Secure.putInt(this.mContext.getContentResolver(), "wifi_adps_enable", inputValue);
        } else if (c == 1) {
            Settings.Global.putInt(this.mContext.getContentResolver(), "safe_wifi", inputValue);
        } else if (c == 2) {
            Settings.Secure.putInt(this.mContext.getContentResolver(), "wifi_hotspot20_enable", inputValue);
        } else if (c == 3) {
            Settings.Secure.putInt(this.mContext.getContentResolver(), "wifi_mwips", inputValue);
        }
    }

    public void updateConditions(Bundle bundle) {
        Log.d(TAG, "updateConditions");
        boolean isChanged = false;
        if (bundle == null) {
            Log.d(TAG, "bundle is null");
        } else if (!checkAndReset(bundle)) {
            if (checkAndPutIntoDB(bundle)) {
                isChanged = true;
            }
            if (checkAndPutIntoMap(bundle)) {
                isChanged = true;
            }
            if (isChanged) {
                putFeatureIntoDB();
            }
        }
    }

    private int getPasspointDefaultValue() {
        return isPasspointDefaultOff() ? 0 : 1;
    }

    private boolean isPasspointDefaultOff() {
        String cscFeature = OpBrandingLoader.getInstance().getMenuStatusForPasspoint();
        if (cscFeature == null || !cscFeature.contains("DEFAULT_OFF")) {
            return false;
        }
        return true;
    }

    private void putFeatureIntoDB() {
        Log.d(TAG, "putFeatureIntoDB:" + this.mFeatureMap.toString());
        Settings.Global.putString(this.mContext.getContentResolver(), "wifi_guider_feature_control", this.mFeatureMap.toString());
    }

    public int getRssi24Threshold() {
        return (this.mFeatureMap.containsKey(KEY_RSSI24) ? this.mFeatureMap : this.mFeatureDefaultMap).get(KEY_RSSI24).intValue();
    }

    public int getRssi5Threshold() {
        return (this.mFeatureMap.containsKey(KEY_RSSI5) ? this.mFeatureMap : this.mFeatureDefaultMap).get(KEY_RSSI5).intValue();
    }

    public int getAutoWifiCellCount() {
        return (this.mFeatureMap.containsKey(KEY_CELL_COUNT) ? this.mFeatureMap : this.mFeatureDefaultMap).get(KEY_CELL_COUNT).intValue();
    }

    public int getAutoWifiBssidCount() {
        return (this.mFeatureMap.containsKey(KEY_BSSID_COUNT) ? this.mFeatureMap : this.mFeatureDefaultMap).get(KEY_BSSID_COUNT).intValue();
    }

    public int getAutoWifiTurnOffDurationSeconds() {
        Integer num;
        if (this.mFeatureMap.containsKey(KEY_AUTO_WIFI_TURN_OFF_SECONDS)) {
            num = this.mFeatureMap.get(KEY_AUTO_WIFI_TURN_OFF_SECONDS);
        } else {
            num = this.mFeatureDefaultMap.get(KEY_AUTO_WIFI_TURN_OFF_SECONDS);
        }
        return num.intValue();
    }

    public int getAutoWifiTurnOffScanCount() {
        Integer num;
        if (this.mFeatureMap.containsKey(KEY_AUTO_WIFI_TURN_OFF_SCAN_COUNT)) {
            num = this.mFeatureMap.get(KEY_AUTO_WIFI_TURN_OFF_SCAN_COUNT);
        } else {
            num = this.mFeatureDefaultMap.get(KEY_AUTO_WIFI_TURN_OFF_SCAN_COUNT);
        }
        return num.intValue();
    }

    public boolean isMWipsEnabled() {
        return this.mFeatureMap.containsKey(KEY_MWIPS) ? this.mFeatureMap.get(KEY_MWIPS).intValue() == 1 : this.mFeatureDefaultMap.get(KEY_MWIPS).intValue() == 1;
    }

    public boolean isSupportQosProvider() {
        return this.mFeatureMap.containsKey(KEY_QOS_PROVIDER) ? this.mFeatureMap.get(KEY_QOS_PROVIDER).intValue() == 1 : this.mFeatureDefaultMap.get(KEY_QOS_PROVIDER).intValue() == 1;
    }

    public boolean isSupportSamsungNetworkScore() {
        return this.mFeatureMap.containsKey(KEY_SCORE_PROVIDER) ? this.mFeatureMap.get(KEY_SCORE_PROVIDER).intValue() == 1 : this.mFeatureDefaultMap.get(KEY_SCORE_PROVIDER).intValue() == 1;
    }

    public boolean isSupportWifiProfileShare() {
        return this.mFeatureMap.containsKey(KEY_PROFILE_SHARE) ? this.mFeatureMap.get(KEY_PROFILE_SHARE).intValue() == 1 : this.mFeatureDefaultMap.get(KEY_PROFILE_SHARE).intValue() == 1;
    }

    public boolean isSupportWifiProfileRequest() {
        return this.mFeatureMap.containsKey(KEY_PROFILE_REQUEST) ? this.mFeatureMap.get(KEY_PROFILE_REQUEST).intValue() == 1 : this.mFeatureDefaultMap.get(KEY_PROFILE_REQUEST).intValue() == 1;
    }
}

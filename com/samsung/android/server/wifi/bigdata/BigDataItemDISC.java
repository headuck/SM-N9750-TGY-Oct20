package com.samsung.android.server.wifi.bigdata;

import android.util.Log;

/* access modifiers changed from: package-private */
public class BigDataItemDISC extends BaseBigDataItem {
    private static final String[][] DISC = {new String[]{KEY_AP_SECURE_TYPE, ""}, new String[]{KEY_WPA_STATE, ""}, new String[]{KEY_AP_SCAN_COUNT_TOTAL, "0"}, new String[]{KEY_AP_SCAN_COUNT_SAME_CHANNEL, "0"}, new String[]{KEY_AP_DISCONNECT_REASON, "0"}, new String[]{KEY_AP_LOCALLY_GENERATED, "0"}, new String[]{"DUNO", "0"}, new String[]{KEY_AP_OUI, ""}, new String[]{KEY_AP_CHANNEL, ""}, new String[]{KEY_AP_BANDWIDTH, ""}, new String[]{KEY_AP_RSSI, ""}, new String[]{KEY_AP_DATA_RATE, ""}, new String[]{KEY_AP_80211MODE, ""}, new String[]{KEY_AP_ANTENNA, ""}, new String[]{KEY_AP_MU_MIMO, ""}, new String[]{KEY_AP_PASSPOINT, ""}, new String[]{KEY_AP_SNR, ""}, new String[]{KEY_AP_NOISE, ""}, new String[]{KEY_AP_AKM, ""}, new String[]{KEY_AP_ROAMING_COUNT, ""}, new String[]{KEY_AP_11KV, "0"}, new String[]{KEY_AP_11KV_IE, "0"}, new String[]{KEY_AP_ROAMING_FULLS_SCAN_COUNT, "0"}, new String[]{KEY_AP_ROAMING_PARTIAL_SCAN_COUNT, "0"}, new String[]{KEY_AP_ADPS_DISCONNECT, "0"}, new String[]{KEY_CHIPSET_OUIS, ""}};
    static final String KEY_ADPS_STATE = "adps";
    private static final String KEY_AP_11KV = "11KV";
    private static final String KEY_AP_11KV_IE = "KVIE";
    private static final String KEY_AP_80211MODE = "ap_mod";
    private static final String KEY_AP_ADPS_DISCONNECT = "adps_dis";
    private static final String KEY_AP_AKM = "ap_akm";
    private static final String KEY_AP_ANTENNA = "ap_ant";
    static final String KEY_AP_BANDWIDTH = "ap_bdw";
    static final String KEY_AP_BT_CONNECTION = "bt_cnt";
    static final String KEY_AP_CHANNEL = "ap_chn";
    static final String KEY_AP_CONN_DURATION = "apdr";
    private static final String KEY_AP_DATA_RATE = "ap_drt";
    static final String KEY_AP_DISCONNECT_REASON = "cn_rsn";
    static final String KEY_AP_INTERNAL_REASON = "cn_irs";
    static final String KEY_AP_INTERNAL_TYPE = "apwe";
    static final String KEY_AP_LOCALLY_GENERATED = "aplo";
    static final String KEY_AP_MAX_DATA_RATE = "max_drt";
    private static final String KEY_AP_MU_MIMO = "ap_mmo";
    private static final String KEY_AP_NOISE = "ap_nos";
    static final String KEY_AP_OUI = "ap_oui";
    private static final String KEY_AP_PASSPOINT = "ap_pas";
    private static final String KEY_AP_ROAMING_COUNT = "ap_rct";
    private static final String KEY_AP_ROAMING_FULLS_SCAN_COUNT = "rfs_cnt";
    private static final String KEY_AP_ROAMING_PARTIAL_SCAN_COUNT = "rps_cnt";
    static final String KEY_AP_ROAMING_TRIGGER = "cn_rom";
    static final String KEY_AP_RSSI = "ap_rsi";
    private static final String KEY_AP_SCAN_COUNT_SAME_CHANNEL = "ap_snt";
    private static final String KEY_AP_SCAN_COUNT_TOTAL = "ap_stc";
    static final String KEY_AP_SECURE_TYPE = "ap_sec";
    private static final String KEY_AP_SNR = "ap_snr";
    private static final String KEY_CHIPSET_OUIS = "chipset_ouis";
    private static final String KEY_VER = "bver";
    static final String KEY_WPA_STATE = "wpst";
    private static final String PARM_VERSION = "5";
    private int mMaxDataRate = 0;

    public BigDataItemDISC(String featureName) {
        super(featureName);
    }

    @Override // com.samsung.android.server.wifi.bigdata.BaseBigDataItem
    public String getJsonFormat() {
        WifiChipInfo wifiChipInfo = WifiChipInfo.getInstance();
        resetTime();
        return wifiChipInfo.toString() + "," + getKeyValueStrings(DISC) + "," + getKeyValueString(KEY_AP_ROAMING_TRIGGER, "0") + "," + getKeyValueString(KEY_AP_INTERNAL_REASON, "0") + "," + getKeyValueString(KEY_AP_MAX_DATA_RATE, "0") + "," + getKeyValueString(KEY_AP_BT_CONNECTION, "0") + "," + getKeyValueString(KEY_AP_INTERNAL_TYPE, "0") + "," + getKeyValueString(KEY_ADPS_STATE, "0") + "," + getKeyValueString(KEY_VER, PARM_VERSION) + "," + getDurationTimeKeyValueString(KEY_AP_CONN_DURATION);
    }

    @Override // com.samsung.android.server.wifi.bigdata.BaseBigDataItem
    public void addOrUpdateValue(String key, int value) {
        if (KEY_AP_MAX_DATA_RATE.equals(key)) {
            if (value < this.mMaxDataRate) {
                value = this.mMaxDataRate;
            } else {
                this.mMaxDataRate = value;
            }
        }
        super.addOrUpdateValue(key, value);
    }

    @Override // com.samsung.android.server.wifi.bigdata.BaseBigDataItem
    public void clearData() {
        this.mMaxDataRate = 0;
        super.clearData();
    }

    /* JADX WARNING: Removed duplicated region for block: B:10:0x0058  */
    /* JADX WARNING: Removed duplicated region for block: B:16:0x0067  */
    @Override // com.samsung.android.server.wifi.bigdata.BaseBigDataItem
    public boolean parseData(String data) {
        String[] array = getArray(data);
        if (array != null) {
            int length = array.length;
            String[][] strArr = DISC;
            if (length == strArr.length - 1) {
                String[] array2 = new String[strArr.length];
                System.arraycopy(array, 0, array2, 0, array.length - 1);
                String[][] strArr2 = DISC;
                array2[strArr2.length - 2] = "-1";
                array2[strArr2.length - 1] = array[array.length - 1];
                array = array2;
                if (array != null) {
                    int length2 = array.length;
                    String[][] strArr3 = DISC;
                    if (length2 == strArr3.length) {
                        putValues(strArr3, array);
                        return true;
                    }
                }
                if (this.mLogMessages) {
                    String str = this.TAG;
                    Log.e(str, "can't pase bigdata extra - data:" + data);
                }
                return false;
            }
        }
        if (array != null) {
            int length3 = array.length;
            String[][] strArr4 = DISC;
            if (length3 == strArr4.length - 3) {
                String[] array22 = new String[strArr4.length];
                System.arraycopy(array, 0, array22, 0, array.length - 1);
                String[][] strArr5 = DISC;
                array22[strArr5.length - 4] = "-1";
                array22[strArr5.length - 3] = "-1";
                array22[strArr5.length - 2] = "-1";
                array22[strArr5.length - 1] = array[array.length - 1];
                array = array22;
            }
        }
        if (array != null) {
        }
        if (this.mLogMessages) {
        }
        return false;
    }

    @Override // com.samsung.android.server.wifi.bigdata.BaseBigDataItem
    public boolean isAvailableLogging(int type) {
        if (type == 1) {
            return true;
        }
        return super.isAvailableLogging(type);
    }
}

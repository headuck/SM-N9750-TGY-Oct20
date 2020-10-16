package com.samsung.android.server.wifi;

import android.content.Context;
import android.os.Debug;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import com.android.server.wifi.Clock;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiNative;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class WifiRoamingAssistant {
    private static final boolean DEV = Debug.semIsProductDev();
    private static final String JTAG_RCL_LIST = "rcl_list";
    private static final int MAX_RCL_COUNT = 16;
    private static final int MAX_RETURN_CHANNEL_COUNT = 5;
    private static final String RCL_FILE_DISABLE = "Disable.rcl";
    private static final String RCL_FILE_NAME = "RCL.json";
    private static final String TAG = WifiRoamingAssistant.class.getSimpleName();
    private static final String VERSION = "1.0";
    private static WifiRoamingAssistant mInstance;
    private int mCachedFrequency;
    private String mCachedNetworkKey;
    private final Clock mClock = WifiInjector.getInstance().getClock();
    private ArrayList<String> mExceptionalNetworks = new ArrayList<>();
    private String mLastConnectedNetworkKey;
    private long mLastUpdatedTime;
    private int mRclEnabled = 1;
    private File mRclFile;
    private ConcurrentHashMap<String, RoamingChannelList> mRclHash = new ConcurrentHashMap<>();
    private WifiState mState;
    private final WifiNative mWifiNative = WifiInjector.getInstance().getWifiNative();

    public enum WifiState {
        CONNECTED,
        DISCONNECTED,
        ROAM
    }

    public static synchronized WifiRoamingAssistant init(Context context) {
        WifiRoamingAssistant wifiRoamingAssistant;
        synchronized (WifiRoamingAssistant.class) {
            if (mInstance == null) {
                mInstance = new WifiRoamingAssistant(context);
            }
            wifiRoamingAssistant = mInstance;
        }
        return wifiRoamingAssistant;
    }

    public static synchronized WifiRoamingAssistant getInstance() {
        WifiRoamingAssistant wifiRoamingAssistant;
        synchronized (WifiRoamingAssistant.class) {
            wifiRoamingAssistant = mInstance;
        }
        return wifiRoamingAssistant;
    }

    private WifiRoamingAssistant(Context context) {
        String rclPath = Environment.getDataDirectory() + "/misc/wifi/";
        this.mRclFile = new File(rclPath + RCL_FILE_NAME);
        if (new File(rclPath + RCL_FILE_DISABLE).exists()) {
            this.mRclEnabled = 0;
        }
        this.mExceptionalNetworks.add("ollehWiFi");
        this.mExceptionalNetworks.add("olleh GiGA WiFi");
        this.mExceptionalNetworks.add("KT GiGA WiFi");
        this.mExceptionalNetworks.add("KT WiFi");
        this.mExceptionalNetworks.add("T wifi zone");
        this.mExceptionalNetworks.add("U+zone");
        this.mExceptionalNetworks.add("U+zone_5G");
        this.mExceptionalNetworks.add("5G_U+zone");
        this.mExceptionalNetworks.add("0000docomo");
        this.mExceptionalNetworks.add("0001docomo");
        this.mExceptionalNetworks.add("iptime");
        Log.d(TAG, "Initiate Roaming Assistant version 1.0");
        if (DEV) {
            Log.d(TAG, " RCL path " + rclPath);
        }
        this.mState = WifiState.DISCONNECTED;
        this.mLastConnectedNetworkKey = null;
        this.mCachedNetworkKey = null;
        this.mCachedFrequency = 0;
        setState(WifiState.DISCONNECTED);
        readFile();
    }

    private void setState(WifiState stat) {
        if (DEV) {
            Log.d(TAG, String.format(Locale.ENGLISH, " mState is changed [ %s > %s ]", this.mState.name(), stat.name()));
        }
        this.mState = stat;
    }

    private void resetCache() {
        this.mCachedNetworkKey = null;
        this.mCachedFrequency = 0;
    }

    private void updateCache(String networkKey, int frequency) {
        this.mCachedNetworkKey = networkKey;
        this.mCachedFrequency = frequency;
    }

    private boolean isExceptionalNetwork(String networkKey) {
        Iterator<String> it = this.mExceptionalNetworks.iterator();
        while (it.hasNext()) {
            if (networkKey.contains(it.next())) {
                return true;
            }
        }
        return false;
    }

    private void updateHash(String networkKey, RoamingChannelList rcl) {
        if (this.mRclHash.get(networkKey) == null && this.mRclHash.size() >= 16) {
            RoamingChannelList del = null;
            for (Map.Entry<String, RoamingChannelList> entry : this.mRclHash.entrySet()) {
                RoamingChannelList tmp = entry.getValue();
                if (del == null || del.getLastUpdatedTime() > tmp.getLastUpdatedTime()) {
                    del = tmp;
                }
            }
            this.mRclHash.remove(del.getNetworkKey());
        }
        this.mRclHash.put(networkKey, rcl);
    }

    public void updateRcl(String networkKey, int frequency, boolean isConnected) {
        if (DEV) {
            Log.d(TAG, String.format(Locale.ENGLISH, " updateRCL[ %s ][ %d ][ %b ]", networkKey, Integer.valueOf(frequency), Boolean.valueOf(isConnected)));
        }
        long timeStamp = this.mClock.getWallClockMillis();
        if (isConnected) {
            if (networkKey != null && !isExceptionalNetwork(networkKey)) {
                if (this.mState == WifiState.DISCONNECTED) {
                    RoamingChannelList rcl = this.mRclHash.get(networkKey);
                    if (rcl != null) {
                        sendFrequentlyUsedChannels(rcl);
                    }
                    updateCache(networkKey, frequency);
                    this.mLastConnectedNetworkKey = networkKey;
                    setState(WifiState.CONNECTED);
                } else {
                    long diff = timeStamp - this.mLastUpdatedTime;
                    RoamingChannelList rcl2 = this.mRclHash.get(this.mCachedNetworkKey);
                    if (rcl2 == null) {
                        rcl2 = new RoamingChannelList(this.mCachedNetworkKey);
                    }
                    rcl2.update(timeStamp, diff, this.mCachedFrequency);
                    updateHash(this.mCachedNetworkKey, rcl2);
                    updateCache(networkKey, frequency);
                    setState(WifiState.ROAM);
                }
            } else {
                return;
            }
        } else if (this.mState != WifiState.DISCONNECTED) {
            RoamingChannelList rcl3 = this.mRclHash.get(this.mCachedNetworkKey);
            if (rcl3 != null) {
                rcl3.update(timeStamp, timeStamp - this.mLastUpdatedTime, this.mCachedFrequency);
                updateHash(this.mCachedNetworkKey, rcl3);
                resetCache();
                writeFile();
            }
            setState(WifiState.DISCONNECTED);
        }
        this.mLastUpdatedTime = timeStamp;
    }

    public void onDriverEventReceived(String ssid, ArrayList<Integer> channelList) {
        String str = this.mLastConnectedNetworkKey;
        if (str == null || !str.contains(ssid)) {
            if (DEV) {
                Log.d(TAG, String.format(Locale.ENGLISH, " Discard driver RCL event [ %s ][ %s ]", this.mLastConnectedNetworkKey, ssid));
            }
        } else if (!isExceptionalNetwork(this.mLastConnectedNetworkKey)) {
            RoamingChannelList rcl = this.mRclHash.get(this.mLastConnectedNetworkKey);
            if (rcl != null) {
                Iterator<Integer> it = channelList.iterator();
                while (it.hasNext()) {
                    rcl.updateHitCount(ieee80211_channel_to_frequency(it.next().intValue()));
                }
                updateHash(this.mLastConnectedNetworkKey, rcl);
                Log.d(TAG, "RCL updated by driver event");
                this.mLastConnectedNetworkKey = null;
            }
        } else if (DEV) {
            Log.d(TAG, " Discard driver RCL event - except network");
        }
    }

    public void forgetNetwork(String networkKey) {
        if (networkKey != null) {
            String str = TAG;
            Log.d(str, networkKey + " RCL removed - forget network");
            this.mRclHash.remove(networkKey);
            if (networkKey.equals(this.mCachedNetworkKey)) {
                setState(WifiState.DISCONNECTED);
            }
            writeFile();
        }
    }

    public void factoryReset() {
        Log.d(TAG, "RCL factory reset - Reset network settings");
        setState(WifiState.DISCONNECTED);
        this.mRclHash.clear();
        writeFile();
    }

    public List<Integer> getNetworkFrequencyList(String networkKey) {
        RoamingChannelList rcl;
        if (this.mState == WifiState.DISCONNECTED || (rcl = this.mRclHash.get(networkKey)) == null) {
            return null;
        }
        return rcl.getFrequencyList();
    }

    private void sendFrequentlyUsedChannels(RoamingChannelList rcl) {
        if (this.mRclEnabled == 0) {
            Log.d(TAG, "RCL is disabled, do not send RCL Command.");
            return;
        }
        List<Integer> list = rcl.getFrequentlyUsedChannel(5);
        if (!list.isEmpty()) {
            StringBuffer buf = new StringBuffer();
            buf.append(list.size());
            for (int i = 0; i < list.size(); i++) {
                buf.append(" ");
                buf.append(list.get(i));
            }
            String mInterfaceName = this.mWifiNative.getClientInterfaceName();
            if (this.mWifiNative.getNCHOMode(mInterfaceName) == 0) {
                String str = TAG;
                Log.d(str, "RCL - addRoamScanChannelsLegacy " + ((Object) buf));
                this.mWifiNative.addRoamScanChannelsLegacy(mInterfaceName, buf.toString());
                return;
            }
            String str2 = TAG;
            Log.d(str2, "RCL - addRoamScanChannels " + ((Object) buf));
            this.mWifiNative.addRoamScanChannels(mInterfaceName, buf.toString());
        }
    }

    /* JADX WARNING: Code restructure failed: missing block: B:23:0x0075, code lost:
        r2 = move-exception;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:24:0x0076, code lost:
        $closeResource(r1, r0);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:25:0x0079, code lost:
        throw r2;
     */
    private void writeFile() {
        if (this.mRclFile.exists()) {
            this.mRclFile.delete();
            if (DEV) {
                Log.d(TAG, " write RCL file - RCL file already exist, erase it");
            }
        } else if (DEV) {
            Log.d(TAG, " write RCL file");
        }
        if (this.mRclHash.size() != 0) {
            BufferedWriter bw = new BufferedWriter(new FileWriter(this.mRclFile));
            JSONObject jsonObj = new JSONObject();
            JSONArray rclList = new JSONArray();
            for (RoamingChannelList rcl : this.mRclHash.values()) {
                rclList.put(rcl.toJson());
            }
            jsonObj.put(JTAG_RCL_LIST, rclList);
            bw.write(jsonObj.toString());
            try {
                $closeResource(null, bw);
            } catch (IOException | JSONException e) {
                Log.e(TAG, "writeFile exception", e);
            }
        }
    }

    private static /* synthetic */ void $closeResource(Throwable x0, AutoCloseable x1) {
        if (x0 != null) {
            try {
                x1.close();
            } catch (Throwable th) {
                x0.addSuppressed(th);
            }
        } else {
            x1.close();
        }
    }

    /* JADX WARNING: Code restructure failed: missing block: B:28:0x0076, code lost:
        r2 = move-exception;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:29:0x0077, code lost:
        $closeResource(r1, r0);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:30:0x007a, code lost:
        throw r2;
     */
    private void readFile() {
        Log.d(TAG, "load RCL file");
        if (!this.mRclFile.exists()) {
            Log.w(TAG, "RCL file not exists..");
            return;
        }
        BufferedReader br = new BufferedReader(new FileReader(this.mRclFile));
        String fileData = getStreamData(br);
        if (fileData != null) {
            if (!TextUtils.isEmpty(fileData)) {
                JSONArray jsonArr = new JSONObject(fileData).optJSONArray(JTAG_RCL_LIST);
                if (jsonArr == null) {
                    try {
                        $closeResource(null, br);
                        return;
                    } catch (IOException | JSONException e) {
                        Log.e(TAG, "readFile exception", e);
                        return;
                    }
                } else {
                    for (int i = 0; i < jsonArr.length(); i++) {
                        RoamingChannelList rcl = RoamingChannelList.fromJson(jsonArr.optJSONObject(i));
                        if (!"".equals(rcl.getNetworkKey())) {
                            this.mRclHash.put(rcl.getNetworkKey(), rcl);
                        }
                    }
                    $closeResource(null, br);
                }
            }
        }
        Log.e(TAG, "File Data is null");
        $closeResource(null, br);
    }

    private String getStreamData(Reader is) {
        if (is == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(2048);
        try {
            char[] tmp = new char[2048];
            while (true) {
                int numRead = is.read(tmp);
                if (numRead <= 0) {
                    break;
                }
                sb.append(tmp, 0, numRead);
            }
        } catch (IOException e) {
            Log.e(TAG, "getStreamData exception", e);
        }
        return sb.toString();
    }

    private int ieee80211_channel_to_frequency(int chan) {
        if (chan <= 0 || chan > 196) {
            return 0;
        }
        if (chan == 14) {
            return 2484;
        }
        if (chan < 14) {
            return (chan * 5) + 2407;
        }
        if (chan >= 182) {
            return (chan * 5) + 4000;
        }
        return (chan * 5) + 5000;
    }
}

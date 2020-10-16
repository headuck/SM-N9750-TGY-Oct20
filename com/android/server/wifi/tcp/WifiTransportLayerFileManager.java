package com.android.server.wifi.tcp;

import android.os.Debug;
import android.util.Log;
import com.android.server.wifi.tcp.WifiApInfo;
import com.android.server.wifi.util.XmlUtil;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class WifiTransportLayerFileManager {
    private static final boolean DBG = Debug.semIsProductDev();
    public static final String FILE_TCP_MONITOR_AP_INFO = "/data/misc/wifi/TcpMonitorApInfo.json";
    public static final String FILE_TCP_MONITOR_PACKAGE_INFO = "/data/misc/wifi/TcpMonitorPackageInfo.json";
    public static final String FILE_TCP_SWITCHABLE_UID_INFO = "/data/misc/wifi/TcpMonitorSwitchEnabledUID.xml";
    private static final String TAG = "WifiTransportLayerFileManager";
    private final String TEXT_AP_ACCUMULATED_CONNECTION_COUNT = "AccumulatedConnectionCount";
    private final String TEXT_AP_ACCUMULATED_CONNECTION_TIME = "AccumulatedConnectionTime";
    private final String TEXT_AP_DATA = "Data";
    private final String TEXT_AP_DETECTED_LAST_TIME = "PackageLastDetectedTime";
    private final String TEXT_AP_DETECTED_PACKAGE_COUNT = "PackageDetectedCount";
    private final String TEXT_AP_DETECTED_PACKAGE_LIST = "DetectedPackageList";
    private final String TEXT_AP_DETECTED_PACKAGE_NAME = "PackageName";
    private final String TEXT_AP_DETECTED_PACKAGE_NORMAL_OPERATION_TIME = "PackageNormalOperationTime";
    private final String TEXT_AP_SSID = XmlUtil.WifiConfigurationXmlUtil.XML_TAG_SSID;
    private final String TEXT_AP_SWITCH_FOR_INDIVIDUAL_APPS_DETECTION_COUNT = "SwitchForIndividualAppsDetectionCount";
    private final String TEXT_BROWSING = "Browsing";
    private final String TEXT_CATEGORY = "Category";
    private final String TEXT_CATEGORY_UPDATE_FAIL_COUNT = "CategoryUpdateFailCount";
    private final String TEXT_CHATTING_APP = "ChattingApp";
    private final String TEXT_DATA = "Data";
    private final String TEXT_DATA_USAGE = "DataUsage";
    private final String TEXT_DETECTED_COUNT = "DetectedCount";
    private final String TEXT_GAME = "Game";
    private final String TEXT_INTERNET_PERMISSION = "InternetPermission";
    private final String TEXT_LAUNCHABLE = "Launchable";
    private final String TEXT_PACKAGE_NAME = "PackageName";
    private final String TEXT_SWITCHABLE = "Switchable";
    private final String TEXT_SYSTEM_APP = "SystemApp";
    private final String TEXT_UID = "UID";
    private final String TEXT_USAGE_PATTERN = "UsagePattern";
    private final String TEXT_VOIP = "VoIP";

    public HashMap<Integer, WifiPackageInfo> loadWifiPackageInfoFromFile() {
        Log.d(TAG, "loadWifiPackageInfoFromFile");
        return readWifiPackageInfoList();
    }

    public boolean saveWifiPackageInfoToFile(HashMap<Integer, WifiPackageInfo> info) {
        Log.d(TAG, "saveWifiPackageInfoToFile");
        return writeWifiPackageInfoList(info);
    }

    private HashMap<Integer, WifiPackageInfo> readWifiPackageInfoList() {
        Log.d(TAG, "readWifiPackageInfoList");
        HashMap<Integer, WifiPackageInfo> list = new HashMap<>();
        try {
            JSONObject object = readJSONObjectFromFile(FILE_TCP_MONITOR_PACKAGE_INFO);
            if (object != null) {
                JSONArray array = object.getJSONArray("Data");
                for (int index = 0; index < array.length(); index++) {
                    JSONObject item = array.getJSONObject(index);
                    WifiPackageInfo info = new WifiPackageInfo(item.getInt("UID"), item.getString("PackageName"), item.getString("Category"), item.getBoolean("ChattingApp"), item.getBoolean("VoIP"), item.getBoolean("Game"), item.getBoolean("Browsing"), item.getBoolean("SystemApp"), item.getBoolean("Launchable"), item.getBoolean("Switchable"), item.getInt("DetectedCount"), item.getInt("DataUsage"), item.getInt("UsagePattern"), item.getInt("CategoryUpdateFailCount"), item.getBoolean("InternetPermission"));
                    list.put(Integer.valueOf(info.getUid()), info);
                }
            }
        } catch (JSONException e) {
            if (DBG) {
                Log.w(TAG, "readWifiPackageInfoList - JSONException " + e);
            }
            e.printStackTrace();
        }
        return list;
    }

    private boolean writeWifiPackageInfoList(HashMap<Integer, WifiPackageInfo> list) {
        Log.d(TAG, "writeWifiPackageInfoList");
        if (list == null) {
            return false;
        }
        try {
            JSONArray array = new JSONArray();
            JSONObject finalObject = new JSONObject();
            if (list.isEmpty()) {
                return false;
            }
            for (WifiPackageInfo info : list.values()) {
                JSONObject object = new JSONObject();
                object.put("UID", info.getUid());
                object.put("PackageName", info.getPackageName());
                object.put("Category", info.getCategory());
                object.put("ChattingApp", info.isChatApp());
                object.put("VoIP", info.isVoip());
                object.put("Game", info.isGamingApp());
                object.put("Browsing", info.isBrowsingApp());
                object.put("SystemApp", info.isSystemApp());
                object.put("Launchable", info.isLaunchable());
                object.put("Switchable", info.isSwitchable());
                object.put("DetectedCount", info.getDetectedCount());
                object.put("DataUsage", info.getDataUsage());
                object.put("UsagePattern", info.getUsagePattern());
                object.put("CategoryUpdateFailCount", info.getCategoryUpdateFailCount());
                object.put("InternetPermission", info.hasInternetPermission());
                array.put(object);
            }
            finalObject.put("Data", array);
            return writeJSONObjectToFile(finalObject, FILE_TCP_MONITOR_PACKAGE_INFO);
        } catch (JSONException e) {
            if (DBG) {
                Log.w(TAG, "writeWifiPackageInfoList - JSONException " + e);
            }
            e.printStackTrace();
            return false;
        }
    }

    public ArrayList<Integer> loadSwitchEnabledUidListFromFile() {
        Log.d(TAG, "loadSwitchEnabledUidListFromFile");
        return readSwitchEnabledUidInfoList();
    }

    public boolean saveSwitchEnabledUidListToFile(ArrayList<Integer> info) {
        Log.d(TAG, "saveSwitchEnabledUidListToFile");
        return writeSwitchEnabledUidInfoList(info);
    }

    private ArrayList<Integer> readSwitchEnabledUidInfoList() {
        Log.d(TAG, "readSwitchEnabledUidInfoList");
        ArrayList<Integer> list = new ArrayList<>();
        try {
            BufferedReader bufReader = new BufferedReader(new FileReader(new File(FILE_TCP_SWITCHABLE_UID_INFO)));
            while (true) {
                String line = bufReader.readLine();
                if (line == null) {
                    break;
                } else if (line != null) {
                    list.add(Integer.valueOf(Integer.parseInt(line)));
                }
            }
        } catch (FileNotFoundException e) {
            if (DBG) {
                Log.w(TAG, "readSwitchEnabledUidInfoList - FileNotFoundException " + e);
            }
            e.printStackTrace();
        } catch (IOException e2) {
            if (DBG) {
                Log.w(TAG, "readSwitchEnabledUidInfoList - IOException " + e2);
            }
            e2.printStackTrace();
        } catch (Exception e3) {
            if (DBG) {
                Log.w(TAG, "readSwitchEnabledUidInfoList - Exception " + e3);
            }
            e3.printStackTrace();
        }
        return list;
    }

    private boolean writeSwitchEnabledUidInfoList(ArrayList<Integer> list) {
        StringBuilder sb;
        String data = "";
        if (list != null && !list.isEmpty()) {
            Iterator<Integer> it = list.iterator();
            while (it.hasNext()) {
                data = data + it.next().intValue() + "\n";
            }
        }
        Log.d(TAG, "writeSwitchEnabledUidInfoList - " + data);
        File fileUidBlocked = new File(FILE_TCP_SWITCHABLE_UID_INFO);
        FileWriter out = null;
        if (fileUidBlocked.exists()) {
            fileUidBlocked.delete();
        }
        try {
            fileUidBlocked.createNewFile();
            FileWriter out2 = new FileWriter(FILE_TCP_SWITCHABLE_UID_INFO);
            if (data != null) {
                Log.d(TAG, "setUidBlockedList: " + data);
                out2.write(data);
                out2.flush();
            }
            try {
                out2.close();
            } catch (IOException e) {
                e = e;
                if (DBG) {
                    sb = new StringBuilder();
                    sb.append("writeSwitchEnabledUidInfoList - IOException ");
                    sb.append(e);
                    Log.w(TAG, sb.toString());
                }
                e.printStackTrace();
                return false;
            }
        } catch (IOException e2) {
            Log.w(TAG, "setUidBlockedList: IOException:" + e2);
            e2.printStackTrace();
            if (0 != 0) {
                try {
                    out.close();
                } catch (IOException e3) {
                    e = e3;
                    if (DBG) {
                        sb = new StringBuilder();
                        sb.append("writeSwitchEnabledUidInfoList - IOException ");
                        sb.append(e);
                        Log.w(TAG, sb.toString());
                    }
                    e.printStackTrace();
                    return false;
                }
            }
        } catch (Throwable th) {
            if (0 != 0) {
                try {
                    out.close();
                } catch (IOException e4) {
                    if (DBG) {
                        Log.w(TAG, "writeSwitchEnabledUidInfoList - IOException " + e4);
                    }
                    e4.printStackTrace();
                }
            }
            throw th;
        }
        return false;
    }

    public HashMap<String, WifiApInfo> loadWifiApInfoFromFile() {
        Log.d(TAG, "loadWifiApInfoFromFile");
        HashMap<String, WifiApInfo> list = readWifiApInfoList();
        if (DBG) {
            Log.d(TAG, "loadWifiPackageInfoFromFile - " + list.size());
        }
        return list;
    }

    public boolean saveWifiApInfoToFile(HashMap<String, WifiApInfo> info) {
        Log.d(TAG, "saveWifiApInfoToFile");
        return writeWifiApInfoList(info);
    }

    /* JADX WARNING: Removed duplicated region for block: B:61:0x00fb  */
    private HashMap<String, WifiApInfo> readWifiApInfoList() {
        JSONArray arrayDetectionList;
        JSONArray array;
        JSONObject object;
        JSONArray array2;
        JSONObject object2;
        Log.d(TAG, "readWifiApInfoList");
        HashMap<String, WifiApInfo> list = new HashMap<>();
        JSONObject object3 = readJSONObjectFromFile(FILE_TCP_MONITOR_AP_INFO);
        if (object3 != null) {
            JSONArray array3 = null;
            try {
                array3 = object3.getJSONArray("Data");
            } catch (JSONException e) {
                if (DBG) {
                    Log.w(TAG, "readWifiApInfoList - JSONException " + e);
                }
                e.printStackTrace();
            }
            if (array3 != null) {
                int index = 0;
                while (index < array3.length()) {
                    JSONObject item = null;
                    try {
                        item = array3.getJSONObject(index);
                    } catch (JSONException e2) {
                        if (DBG) {
                            Log.w(TAG, "readWifiApInfoList - JSONException " + e2);
                        }
                        e2.printStackTrace();
                    }
                    HashMap<String, WifiApInfo.DetectedPackageInfo> detectionList = new HashMap<>();
                    try {
                        arrayDetectionList = item.getJSONArray("DetectedPackageList");
                    } catch (JSONException e3) {
                        if (DBG) {
                            Log.w(TAG, "readWifiApInfoList - JSONException " + e3);
                        }
                        e3.printStackTrace();
                        arrayDetectionList = null;
                    }
                    if (arrayDetectionList != null) {
                        int indexDetectionList = 0;
                        while (indexDetectionList < arrayDetectionList.length()) {
                            JSONObject packageItem = null;
                            try {
                                packageItem = arrayDetectionList.getJSONObject(indexDetectionList);
                            } catch (JSONException e4) {
                                if (DBG) {
                                    Log.w(TAG, "readWifiApInfoList - JSONException " + e4);
                                }
                                e4.printStackTrace();
                            }
                            try {
                                object2 = object3;
                                try {
                                    array2 = array3;
                                    try {
                                        detectionList.put(packageItem.getString("PackageName"), new WifiApInfo.DetectedPackageInfo(packageItem.getString("PackageName"), packageItem.getInt("PackageDetectedCount"), packageItem.getString("PackageLastDetectedTime"), packageItem.getInt("PackageNormalOperationTime")));
                                    } catch (JSONException e5) {
                                        e = e5;
                                    }
                                } catch (JSONException e6) {
                                    e = e6;
                                    array2 = array3;
                                    if (DBG) {
                                        Log.w(TAG, "readWifiApInfoList - JSONException " + e);
                                    }
                                    e.printStackTrace();
                                    indexDetectionList++;
                                    object3 = object2;
                                    array3 = array2;
                                }
                            } catch (JSONException e7) {
                                e = e7;
                                object2 = object3;
                                array2 = array3;
                                if (DBG) {
                                }
                                e.printStackTrace();
                                indexDetectionList++;
                                object3 = object2;
                                array3 = array2;
                            }
                            indexDetectionList++;
                            object3 = object2;
                            array3 = array2;
                        }
                        object = object3;
                        array = array3;
                    } else {
                        object = object3;
                        array = array3;
                    }
                    try {
                        try {
                            list.put(item.getString(XmlUtil.WifiConfigurationXmlUtil.XML_TAG_SSID), new WifiApInfo(item.getString(XmlUtil.WifiConfigurationXmlUtil.XML_TAG_SSID), item.getInt("AccumulatedConnectionCount"), item.getInt("AccumulatedConnectionTime"), item.getInt("SwitchForIndividualAppsDetectionCount"), detectionList));
                        } catch (JSONException e8) {
                            e = e8;
                        }
                    } catch (JSONException e9) {
                        e = e9;
                        if (DBG) {
                            Log.w(TAG, "readWifiApInfoList - JSONException " + e);
                        }
                        e.printStackTrace();
                        index++;
                        object3 = object;
                        array3 = array;
                    }
                    index++;
                    object3 = object;
                    array3 = array;
                }
            }
        }
        return list;
    }

    /* JADX WARNING: Removed duplicated region for block: B:28:0x00cc  */
    private boolean writeWifiApInfoList(HashMap<String, WifiApInfo> list) {
        Log.d(TAG, "writeWifiApInfoList");
        if (list != null) {
            try {
                if (!list.isEmpty()) {
                    JSONArray arrayAp = new JSONArray();
                    JSONObject finalObject = new JSONObject();
                    for (WifiApInfo info : list.values()) {
                        JSONObject objectAp = new JSONObject();
                        objectAp.put(XmlUtil.WifiConfigurationXmlUtil.XML_TAG_SSID, info.getSsid());
                        objectAp.put("AccumulatedConnectionCount", info.getAccumulatedConnectionCount());
                        objectAp.put("AccumulatedConnectionTime", info.getAccumulatedConnectionTime());
                        objectAp.put("SwitchForIndividualAppsDetectionCount", info.getSwitchForIndivdiaulAppsDetectionCount());
                        HashMap<String, WifiApInfo.DetectedPackageInfo> detectionList = info.getDetectedPackageList();
                        if (detectionList != null && !detectionList.isEmpty()) {
                            JSONArray arrayDetectedPackage = new JSONArray();
                            for (WifiApInfo.DetectedPackageInfo detectedPackageInfo : detectionList.values()) {
                                JSONObject objectPackage = new JSONObject();
                                objectPackage.put("PackageName", detectedPackageInfo.getPackageName());
                                objectPackage.put("PackageDetectedCount", detectedPackageInfo.getDetectedCount());
                                objectPackage.put("PackageLastDetectedTime", detectedPackageInfo.getLastDetectedTime());
                                objectPackage.put("PackageNormalOperationTime", detectedPackageInfo.getPackageNormalOperationTime());
                                arrayDetectedPackage.put(objectPackage);
                            }
                            objectAp.put("DetectedPackageList", arrayDetectedPackage);
                        }
                        arrayAp.put(objectAp);
                    }
                    finalObject.put("Data", arrayAp);
                    try {
                        return writeJSONObjectToFile(finalObject, FILE_TCP_MONITOR_AP_INFO);
                    } catch (JSONException e) {
                        e = e;
                        if (DBG) {
                            Log.w(TAG, "writeWifiApInfoList - JSONException " + e);
                        }
                        e.printStackTrace();
                        return false;
                    }
                }
            } catch (JSONException e2) {
                e = e2;
                if (DBG) {
                }
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    private JSONObject readJSONObjectFromFile(String filePath) {
        Log.d(TAG, "readJSONObjectFromFile");
        try {
            return new JSONObject(new BufferedReader(new FileReader(new File(filePath))).readLine());
        } catch (FileNotFoundException e) {
            if (DBG) {
                Log.w(TAG, "readJSONObjectFromFile - " + e);
            }
            e.printStackTrace();
            return null;
        } catch (IOException e2) {
            if (DBG) {
                Log.w(TAG, "readJSONObjectFromFile - " + e2);
            }
            e2.printStackTrace();
            return null;
        } catch (JSONException e3) {
            if (DBG) {
                Log.w(TAG, "readJSONObjectFromFile - " + e3);
            }
            e3.printStackTrace();
            return null;
        } catch (Exception e4) {
            if (DBG) {
                Log.w(TAG, "readJSONObjectFromFile - " + e4);
            }
            e4.printStackTrace();
            return null;
        }
    }

    private boolean writeJSONObjectToFile(JSONObject obj, String filePath) {
        if (DBG) {
            Log.d(TAG, "writeJSONObjectToFile");
        }
        if (obj == null) {
            return false;
        }
        File file = new File(filePath);
        if (file.exists()) {
            file.delete();
        }
        try {
            file.createNewFile();
            FileWriter out = new FileWriter(filePath);
            out.write(obj.toString());
            out.flush();
            out.close();
            return true;
        } catch (IOException e) {
            if (DBG) {
                Log.w(TAG, "writeJSONObjectToFile - " + e);
            }
            e.printStackTrace();
            return false;
        } catch (Exception e2) {
            if (DBG) {
                Log.w(TAG, "writeJSONObjectToFile - " + e2);
            }
            e2.printStackTrace();
            return false;
        }
    }
}

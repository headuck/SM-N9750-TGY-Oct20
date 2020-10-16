package com.android.server.wifi.tcp;

import android.content.Context;
import android.os.Debug;
import android.util.Log;

public class WifiPackageInfo {
    private static final boolean DBG = Debug.semIsProductDev();
    private static final String TAG = "WifiPackageInfo";
    public static final int WIFI_APPLICATION_CATEGORY_NONE_QUERY_MAX = 3;
    public static final int WIFI_DATA_USAGE_HIGH = 3;
    public static final int WIFI_DATA_USAGE_LOW = 1;
    public static final int WIFI_DATA_USAGE_MID = 2;
    public static final int WIFI_DATA_USAGE_NONE = 0;
    public static final int WIFI_USAGE_PATTERN_BROWSER = 3;
    public static final int WIFI_USAGE_PATTERN_CHAT = 2;
    public static final int WIFI_USAGE_PATTERN_NONE = 0;
    public static final int WIFI_USAGE_PATTERN_RADIO = 1;
    public static final int WIFI_USAGE_PATTERN_STREAMING = 4;
    private String mCategory = WifiTransportLayerUtils.CATEGORY_PLAYSTORE_NONE;
    private int mCategoryUpdateFailCount = 0;
    private int mDataUsage = 0;
    private int mDetectedCount = 0;
    private boolean mHasInternetPermission = false;
    private boolean mIsBrowsingApp = false;
    private boolean mIsChattingApp = false;
    private boolean mIsGamingApp = false;
    private boolean mIsLaunchableApp = false;
    private boolean mIsSwitchable = false;
    private boolean mIsSystemApp = false;
    private boolean mIsVoip = false;
    private final String mPackageName;
    private final int mUid;
    private int mUsagePattern = 0;

    public WifiPackageInfo(Context context, int uid, String packageName) {
        this.mPackageName = packageName;
        this.mUid = uid;
        this.mCategory = WifiTransportLayerUtils.CATEGORY_PLAYSTORE_NONE;
        this.mIsChattingApp = WifiTransportLayerUtils.isChatApp(packageName);
        this.mIsVoip = false;
        this.mIsGamingApp = WifiTransportLayerUtils.isSemGamePackage(packageName) || this.mCategory == WifiTransportLayerUtils.CATEGORY_PLAYSTORE_GAME;
        this.mIsBrowsingApp = WifiTransportLayerUtils.isBrowserApp(context, packageName);
        this.mIsSystemApp = WifiTransportLayerUtils.isSystemApp(context, packageName);
        if (this.mIsSystemApp) {
            this.mCategory = WifiTransportLayerUtils.CATEGORY_PLAYSTORE_SYSTEM;
        }
        this.mIsLaunchableApp = WifiTransportLayerUtils.isLauchablePackage(context, packageName);
        updateSwitchable();
        this.mDetectedCount = 0;
        this.mDataUsage = 0;
        this.mUsagePattern = 0;
        this.mCategoryUpdateFailCount = 0;
        this.mHasInternetPermission = WifiTransportLayerUtils.hasPermission(context, packageName, "android.permission.INTERNET");
        if (DBG) {
            Log.d(TAG, "CREATED - " + toString());
        }
    }

    public WifiPackageInfo(int uid, String packageName, String category, boolean chattingApp, boolean voip, boolean game, boolean browsing, boolean systemApp, boolean launchable, boolean switchable, int detectedCount, int dataUsage, int usagePattern, int categoryUpdateFailCount, boolean hasInternetPermission) {
        this.mUid = uid;
        this.mPackageName = packageName;
        this.mCategory = category;
        this.mIsChattingApp = chattingApp;
        this.mIsVoip = voip;
        this.mIsGamingApp = game;
        this.mIsBrowsingApp = browsing;
        this.mIsSystemApp = systemApp;
        this.mIsLaunchableApp = launchable;
        this.mIsSwitchable = switchable;
        this.mDetectedCount = detectedCount;
        this.mDataUsage = dataUsage;
        this.mUsagePattern = usagePattern;
        this.mCategoryUpdateFailCount = categoryUpdateFailCount;
        this.mHasInternetPermission = hasInternetPermission;
    }

    public WifiPackageInfo(WifiPackageInfo info) {
        this.mUid = info.getUid();
        this.mPackageName = info.getPackageName();
        this.mCategory = info.getCategory();
        this.mIsChattingApp = info.isChatApp();
        this.mIsVoip = info.isVoip();
        this.mIsGamingApp = info.isGamingApp();
        this.mIsBrowsingApp = info.isBrowsingApp();
        this.mIsSystemApp = info.isSystemApp();
        this.mIsLaunchableApp = info.isLaunchable();
        this.mIsSwitchable = info.isSwitchable();
        this.mDetectedCount = info.getDetectedCount();
        this.mDataUsage = info.getDataUsage();
        this.mUsagePattern = info.getUsagePattern();
        this.mCategoryUpdateFailCount = info.getCategoryUpdateFailCount();
        this.mHasInternetPermission = info.hasInternetPermission();
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("UID:");
        sb.append(this.mUid);
        sb.append(", PackageName:");
        sb.append(this.mPackageName);
        sb.append(", Category:");
        sb.append(this.mCategory);
        sb.append(", ChattingApp:");
        sb.append(this.mIsChattingApp);
        sb.append(", VoIP:");
        sb.append(this.mIsVoip);
        sb.append(", Game:");
        sb.append(this.mIsGamingApp);
        sb.append(", Browsing:");
        sb.append(this.mIsBrowsingApp);
        sb.append(", SystemApp:");
        sb.append(this.mIsSystemApp);
        sb.append(", Launchable:");
        sb.append(this.mIsLaunchableApp);
        sb.append(", Switchable:");
        sb.append(this.mIsSwitchable);
        sb.append(", DetectedCount:");
        sb.append(this.mDetectedCount);
        sb.append(", DataUsage:");
        sb.append(this.mDataUsage);
        sb.append(", UsagePattern:");
        sb.append(this.mUsagePattern);
        sb.append(", CategoryUpdateFailCount:");
        sb.append(this.mCategoryUpdateFailCount);
        sb.append(", HasInternetPermission:");
        sb.append(this.mHasInternetPermission);
        return sb.toString();
    }

    public int getUid() {
        return this.mUid;
    }

    public String getPackageName() {
        return this.mPackageName;
    }

    public boolean isChatApp() {
        return this.mIsChattingApp;
    }

    public boolean isVoip() {
        return this.mIsVoip;
    }

    public void setIsVoip(boolean isVoip) {
        this.mIsVoip = isVoip;
    }

    public boolean isGamingApp() {
        return this.mIsGamingApp;
    }

    public boolean isBrowsingApp() {
        return this.mIsBrowsingApp;
    }

    public boolean isSystemApp() {
        return this.mIsSystemApp;
    }

    public String getCategory() {
        return this.mCategory;
    }

    public void setCategory(String category) {
        this.mCategory = category;
        updateSwitchable();
    }

    public boolean isSwitchable() {
        return this.mIsSwitchable;
    }

    private void updateSwitchable() {
        this.mIsSwitchable = getSwitchable();
    }

    private boolean getSwitchable() {
        if (isChatApp()) {
            return true;
        }
        if (!isLaunchable() || isSystemApp() || isBrowsingApp() || isGamingApp() || isSkipCategory(getCategory())) {
            return false;
        }
        return true;
    }

    /* JADX INFO: Can't fix incorrect switch cases order, some code will duplicate */
    private boolean isSkipCategory(String category) {
        char c;
        switch (category.hashCode()) {
            case -1833998801:
                if (category.equals(WifiTransportLayerUtils.CATEGORY_PLAYSTORE_SYSTEM)) {
                    c = 5;
                    break;
                }
                c = 65535;
                break;
            case -201031457:
                if (category.equals(WifiTransportLayerUtils.CATEGORY_PLAYSTORE_AUTO_AND_VEHICLES)) {
                    c = 7;
                    break;
                }
                c = 65535;
                break;
            case -135275590:
                if (category.equals(WifiTransportLayerUtils.CATEGORY_PLAYSTORE_FINANCE)) {
                    c = 2;
                    break;
                }
                c = 65535;
                break;
            case 2180082:
                if (category.equals(WifiTransportLayerUtils.CATEGORY_PLAYSTORE_GAME)) {
                    c = 3;
                    break;
                }
                c = 65535;
                break;
            case 2402104:
                if (category.equals(WifiTransportLayerUtils.CATEGORY_PLAYSTORE_NONE)) {
                    c = 1;
                    break;
                }
                c = 65535;
                break;
            case 289768878:
                if (category.equals(WifiTransportLayerUtils.CATEGORY_PLAYSTORE_VIDEO_PLAYERS)) {
                    c = 6;
                    break;
                }
                c = 65535;
                break;
            case 1381037124:
                if (category.equals(WifiTransportLayerUtils.CATEGORY_PLAYSTORE_MAPS_AND_NAVIGATION)) {
                    c = 4;
                    break;
                }
                c = 65535;
                break;
            case 2066319421:
                if (category.equals(WifiTransportLayerUtils.CATEGORY_PLAYSTORE_FAILED)) {
                    c = 0;
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
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
                Log.d(TAG, "isSkipCategory - skip:" + category);
                return true;
            default:
                return false;
        }
    }

    public boolean isLaunchable() {
        return this.mIsLaunchableApp;
    }

    public int getDetectedCount() {
        return this.mDetectedCount;
    }

    public void setDetectedCount(int detectedCount) {
        this.mDetectedCount = detectedCount;
    }

    public int getDataUsage() {
        return this.mDataUsage;
    }

    public int getUsagePattern() {
        return this.mUsagePattern;
    }

    public void updatePackageInfo(Context context) {
        if (DBG) {
            Log.d(TAG, "updatePackageInfo");
        }
        if (!this.mIsChattingApp) {
            this.mIsChattingApp = WifiTransportLayerUtils.isChatApp(this.mPackageName);
        }
        boolean z = false;
        if (!this.mIsGamingApp) {
            this.mIsGamingApp = WifiTransportLayerUtils.isSemGamePackage(this.mPackageName) || this.mCategory == WifiTransportLayerUtils.CATEGORY_PLAYSTORE_GAME;
        }
        if (this.mIsBrowsingApp || WifiTransportLayerUtils.isBrowserApp(context, this.mPackageName)) {
            z = true;
        }
        this.mIsBrowsingApp = z;
        this.mIsSystemApp = WifiTransportLayerUtils.isSystemApp(context, this.mPackageName);
        if (this.mIsSystemApp) {
            this.mCategory = WifiTransportLayerUtils.CATEGORY_PLAYSTORE_SYSTEM;
        }
        this.mIsLaunchableApp = WifiTransportLayerUtils.isLauchablePackage(context, this.mPackageName);
        updateSwitchable();
        this.mHasInternetPermission = WifiTransportLayerUtils.hasPermission(context, this.mPackageName, "android.permission.INTERNET");
    }

    public int getCategoryUpdateFailCount() {
        return this.mCategoryUpdateFailCount;
    }

    public void addCategoryUpdateFailCount() {
        this.mCategoryUpdateFailCount++;
    }

    public boolean hasInternetPermission() {
        return this.mHasInternetPermission;
    }
}

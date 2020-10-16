package com.android.server.wifi.tcp;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Debug;
import android.util.Log;
import com.samsung.android.game.SemGameManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class WifiTransportLayerUtils {
    public static final String CATEGORY_PLAYSTORE_ART_AND_DESIGN = "ART_AND_DESIGN";
    public static final String CATEGORY_PLAYSTORE_AUTO_AND_VEHICLES = "AUTO_AND_VEHICLES";
    public static final String CATEGORY_PLAYSTORE_BEAUTY = "BEAUTY";
    public static final String CATEGORY_PLAYSTORE_BOOKS_AND_REFERENCE = "BOOKS_AND_REFERENCE";
    public static final String CATEGORY_PLAYSTORE_BUSINESS = "BUSINESS";
    public static final String CATEGORY_PLAYSTORE_COMICS = "COMICS";
    public static final String CATEGORY_PLAYSTORE_COMMUNICATION = "COMMUNICATION";
    public static final String CATEGORY_PLAYSTORE_DATING = "DATING";
    public static final String CATEGORY_PLAYSTORE_EDUCATION = "EDUCATION";
    public static final String CATEGORY_PLAYSTORE_ENTERTAINMENT = "ENTERTAINMENT";
    public static final String CATEGORY_PLAYSTORE_EVENTS = "EVENTS";
    public static final String CATEGORY_PLAYSTORE_FAILED = "FAILED";
    public static final String CATEGORY_PLAYSTORE_FINANCE = "FINANCE";
    public static final String CATEGORY_PLAYSTORE_FOOD_AND_DRINK = "FOOD_AND_DRINK";
    public static final String CATEGORY_PLAYSTORE_GAME = "GAME";
    public static final String CATEGORY_PLAYSTORE_HEALTH_AND_FITNESS = "HEALTH_AND_FITNESS";
    public static final String CATEGORY_PLAYSTORE_HOUSE_AND_HOME = "HOUSE_AND_HOME";
    public static final String CATEGORY_PLAYSTORE_LIBRARIES_AND_DEMO = "LIBRARIES_AND_DEMO";
    public static final String CATEGORY_PLAYSTORE_LIFESTYLE = "LIFESTYLE";
    public static final String CATEGORY_PLAYSTORE_MAPS_AND_NAVIGATION = "MAPS_AND_NAVIGATION";
    public static final String CATEGORY_PLAYSTORE_MEDICAL = "MEDICAL";
    public static final String CATEGORY_PLAYSTORE_MUSIC_AND_AUDIO = "MUSIC_AND_AUDIO";
    public static final String CATEGORY_PLAYSTORE_NEWS_AND_MAGAZINES = "NEWS_AND_MAGAZINES";
    public static final String CATEGORY_PLAYSTORE_NONE = "NONE";
    public static final String CATEGORY_PLAYSTORE_PARENTING = "PARENTING";
    public static final String CATEGORY_PLAYSTORE_PERSONALIZATION = "PERSONALIZATION";
    public static final String CATEGORY_PLAYSTORE_PHOTOGRAPHY = "PHOTOGRAPHY";
    public static final String CATEGORY_PLAYSTORE_PRODUCTIVITY = "PRODUCTIVITY";
    public static final String CATEGORY_PLAYSTORE_SHOPPING = "SHOPPING";
    public static final String CATEGORY_PLAYSTORE_SOCIAL = "SOCIAL";
    public static final String CATEGORY_PLAYSTORE_SPORTS = "SPORTS";
    public static final String CATEGORY_PLAYSTORE_SYSTEM = "SYSTEM";
    public static final String CATEGORY_PLAYSTORE_TOOLS = "TOOLS";
    public static final String CATEGORY_PLAYSTORE_TRAVEL_AND_LOCAL = "TRAVEL_AND_LOCAL";
    public static final String CATEGORY_PLAYSTORE_VIDEO_PLAYERS = "VIDEO_PLAYERS";
    public static final String CATEGORY_PLAYSTORE_WEATHER = "WEATHER";
    private static final String CATEGORY_TAG = "<a itemprop=\"genre\"";
    private static final int CATEGORY_TAG_LENGTH = 25;
    private static final String[] CHAT_APPS = {"com.whatsapp", "com.kakao.talk", "com.skype.raider", "com.facebook.orca", "com.viber.voip", "jp.naver.line.android", "com.snapchat.android", "com.tencent.mm", "com.imo.android.imoim"};
    private static final boolean DBG = Debug.semIsProductDev();
    private static final String END_TAG = "class=";
    private static final String GOOGLE_URL = "https://play.google.com/store/apps/details?id=";
    private static final int PLAYSTORE_CATEGORY_TIMEOUT = 4000;
    private static final String TAG = "WifiTransportLayerUtils";

    /* JADX WARNING: Removed duplicated region for block: B:176:0x0361  */
    /* JADX WARNING: Removed duplicated region for block: B:178:0x0366 A[SYNTHETIC, Splitter:B:178:0x0366] */
    /* JADX WARNING: Removed duplicated region for block: B:187:0x03a1  */
    /* JADX WARNING: Removed duplicated region for block: B:189:0x03a6 A[SYNTHETIC, Splitter:B:189:0x03a6] */
    /* JADX WARNING: Removed duplicated region for block: B:195:0x03bb  */
    /* JADX WARNING: Removed duplicated region for block: B:197:0x03c0 A[RETURN] */
    /* JADX WARNING: Removed duplicated region for block: B:201:0x03c9  */
    /* JADX WARNING: Removed duplicated region for block: B:203:0x03ce A[SYNTHETIC, Splitter:B:203:0x03ce] */
    public static String getApplicationCategory(Context context, String packageName) {
        String str;
        HttpURLConnection conn;
        String str2;
        String str3;
        String str4;
        Throwable th;
        String category;
        IOException e;
        StringBuilder sb;
        String str5;
        String str6;
        String str7;
        String category2;
        if (context == null) {
            str = TAG;
        } else if (packageName == null) {
            str = TAG;
        } else {
            BufferedReader br = null;
            HttpURLConnection conn2 = null;
            String category3 = CATEGORY_PLAYSTORE_NONE;
            try {
                try {
                    StringBuilder sb2 = new StringBuilder();
                    try {
                        sb2.append(GOOGLE_URL);
                        sb2.append(packageName);
                        sb2.append("&hl=en");
                        conn = (HttpURLConnection) new URL(sb2.toString()).openConnection();
                    } catch (IOException e2) {
                        str5 = packageName;
                        str7 = TAG;
                        str6 = "getApplicationCategory - ";
                        category2 = category3;
                        Log.w(str2, "getApplicationCategory - IOException " + str3);
                        if (conn2 != null) {
                        }
                        if (br != null) {
                        }
                        if (!category.equals(CATEGORY_PLAYSTORE_NONE)) {
                        }
                    } catch (Exception e3) {
                        str3 = packageName;
                        str2 = TAG;
                        str4 = "getApplicationCategory - ";
                        category = category3;
                        try {
                            Log.w(str2, "getApplicationCategory - Exception " + str3);
                            if (conn2 != null) {
                            }
                            if (br != null) {
                            }
                            if (!category.equals(CATEGORY_PLAYSTORE_NONE)) {
                            }
                        } catch (Throwable th2) {
                            th = th2;
                            conn = conn2;
                        }
                    } catch (Throwable th3) {
                        str3 = packageName;
                        str2 = TAG;
                        str4 = "getApplicationCategory - ";
                        th = th3;
                        conn = null;
                        if (conn != null) {
                        }
                        if (br != null) {
                        }
                        throw th;
                    }
                    try {
                        conn.setConnectTimeout(PLAYSTORE_CATEGORY_TIMEOUT);
                        conn.setReadTimeout(PLAYSTORE_CATEGORY_TIMEOUT);
                        InputStream is = conn.getInputStream();
                        if (conn.getResponseCode() == 200) {
                            try {
                                br = new BufferedReader(new InputStreamReader(is));
                                StringBuffer buffer = new StringBuffer();
                                while (true) {
                                    String line = br.readLine();
                                    if (line == null) {
                                        break;
                                    }
                                    buffer.append(line);
                                }
                                String data = buffer.toString();
                                int indexStart = data.indexOf(CATEGORY_TAG);
                                if (indexStart != -1) {
                                    int indexStart2 = indexStart + 25;
                                    String category4 = data.substring(indexStart2, data.indexOf(END_TAG, indexStart2));
                                    try {
                                        category3 = category4.contains(CATEGORY_PLAYSTORE_GAME) ? CATEGORY_PLAYSTORE_GAME : category4.contains(CATEGORY_PLAYSTORE_ART_AND_DESIGN) ? CATEGORY_PLAYSTORE_ART_AND_DESIGN : category4.contains(CATEGORY_PLAYSTORE_AUTO_AND_VEHICLES) ? CATEGORY_PLAYSTORE_AUTO_AND_VEHICLES : category4.contains(CATEGORY_PLAYSTORE_BEAUTY) ? CATEGORY_PLAYSTORE_BEAUTY : category4.contains(CATEGORY_PLAYSTORE_BOOKS_AND_REFERENCE) ? CATEGORY_PLAYSTORE_BOOKS_AND_REFERENCE : category4.contains(CATEGORY_PLAYSTORE_BUSINESS) ? CATEGORY_PLAYSTORE_BUSINESS : category4.contains(CATEGORY_PLAYSTORE_COMICS) ? CATEGORY_PLAYSTORE_COMICS : category4.contains(CATEGORY_PLAYSTORE_COMMUNICATION) ? CATEGORY_PLAYSTORE_COMMUNICATION : category4.contains(CATEGORY_PLAYSTORE_DATING) ? CATEGORY_PLAYSTORE_DATING : category4.contains(CATEGORY_PLAYSTORE_EDUCATION) ? CATEGORY_PLAYSTORE_EDUCATION : category4.contains(CATEGORY_PLAYSTORE_ENTERTAINMENT) ? CATEGORY_PLAYSTORE_ENTERTAINMENT : category4.contains(CATEGORY_PLAYSTORE_EVENTS) ? CATEGORY_PLAYSTORE_EVENTS : category4.contains(CATEGORY_PLAYSTORE_FINANCE) ? CATEGORY_PLAYSTORE_FINANCE : category4.contains(CATEGORY_PLAYSTORE_FOOD_AND_DRINK) ? CATEGORY_PLAYSTORE_FOOD_AND_DRINK : category4.contains(CATEGORY_PLAYSTORE_HEALTH_AND_FITNESS) ? CATEGORY_PLAYSTORE_HEALTH_AND_FITNESS : category4.contains(CATEGORY_PLAYSTORE_HOUSE_AND_HOME) ? CATEGORY_PLAYSTORE_HOUSE_AND_HOME : category4.contains(CATEGORY_PLAYSTORE_LIBRARIES_AND_DEMO) ? CATEGORY_PLAYSTORE_LIBRARIES_AND_DEMO : category4.contains(CATEGORY_PLAYSTORE_LIFESTYLE) ? CATEGORY_PLAYSTORE_LIFESTYLE : category4.contains(CATEGORY_PLAYSTORE_MAPS_AND_NAVIGATION) ? CATEGORY_PLAYSTORE_MAPS_AND_NAVIGATION : category4.contains(CATEGORY_PLAYSTORE_MEDICAL) ? CATEGORY_PLAYSTORE_MEDICAL : category4.contains(CATEGORY_PLAYSTORE_MUSIC_AND_AUDIO) ? CATEGORY_PLAYSTORE_MUSIC_AND_AUDIO : category4.contains(CATEGORY_PLAYSTORE_NEWS_AND_MAGAZINES) ? CATEGORY_PLAYSTORE_NEWS_AND_MAGAZINES : category4.contains(CATEGORY_PLAYSTORE_PARENTING) ? CATEGORY_PLAYSTORE_PARENTING : category4.contains(CATEGORY_PLAYSTORE_PERSONALIZATION) ? CATEGORY_PLAYSTORE_PERSONALIZATION : category4.contains(CATEGORY_PLAYSTORE_PHOTOGRAPHY) ? CATEGORY_PLAYSTORE_PHOTOGRAPHY : category4.contains(CATEGORY_PLAYSTORE_PRODUCTIVITY) ? CATEGORY_PLAYSTORE_PRODUCTIVITY : category4.contains(CATEGORY_PLAYSTORE_SHOPPING) ? CATEGORY_PLAYSTORE_SHOPPING : category4.contains(CATEGORY_PLAYSTORE_SOCIAL) ? CATEGORY_PLAYSTORE_SOCIAL : category4.contains(CATEGORY_PLAYSTORE_SPORTS) ? CATEGORY_PLAYSTORE_SPORTS : category4.contains(CATEGORY_PLAYSTORE_TOOLS) ? CATEGORY_PLAYSTORE_TOOLS : category4.contains(CATEGORY_PLAYSTORE_TRAVEL_AND_LOCAL) ? CATEGORY_PLAYSTORE_TRAVEL_AND_LOCAL : category4.contains(CATEGORY_PLAYSTORE_VIDEO_PLAYERS) ? CATEGORY_PLAYSTORE_VIDEO_PLAYERS : category4.contains(CATEGORY_PLAYSTORE_WEATHER) ? CATEGORY_PLAYSTORE_WEATHER : category4;
                                    } catch (IOException e4) {
                                        str5 = packageName;
                                        category2 = category4;
                                        conn2 = conn;
                                        str7 = TAG;
                                        str6 = "getApplicationCategory - ";
                                        Log.w(str2, "getApplicationCategory - IOException " + str3);
                                        if (conn2 != null) {
                                        }
                                        if (br != null) {
                                        }
                                        if (!category.equals(CATEGORY_PLAYSTORE_NONE)) {
                                        }
                                    } catch (Exception e5) {
                                        str3 = packageName;
                                        category = category4;
                                        conn2 = conn;
                                        str2 = TAG;
                                        str4 = "getApplicationCategory - ";
                                        Log.w(str2, "getApplicationCategory - Exception " + str3);
                                        if (conn2 != null) {
                                        }
                                        if (br != null) {
                                        }
                                        if (!category.equals(CATEGORY_PLAYSTORE_NONE)) {
                                        }
                                    } catch (Throwable th4) {
                                        str3 = packageName;
                                        str2 = TAG;
                                        str4 = "getApplicationCategory - ";
                                        th = th4;
                                        if (conn != null) {
                                        }
                                        if (br != null) {
                                        }
                                        throw th;
                                    }
                                }
                            } catch (IOException e6) {
                                str5 = packageName;
                                conn2 = conn;
                                category2 = category3;
                                str7 = TAG;
                                str6 = "getApplicationCategory - ";
                                Log.w(str2, "getApplicationCategory - IOException " + str3);
                                if (conn2 != null) {
                                    conn2.disconnect();
                                }
                                if (br != null) {
                                    try {
                                        br.close();
                                    } catch (IOException e7) {
                                        e = e7;
                                        sb = new StringBuilder();
                                    }
                                }
                                if (!category.equals(CATEGORY_PLAYSTORE_NONE)) {
                                }
                            } catch (Exception e8) {
                                str3 = packageName;
                                conn2 = conn;
                                category = category3;
                                str2 = TAG;
                                str4 = "getApplicationCategory - ";
                                Log.w(str2, "getApplicationCategory - Exception " + str3);
                                if (conn2 != null) {
                                    conn2.disconnect();
                                }
                                if (br != null) {
                                    try {
                                        br.close();
                                    } catch (IOException e9) {
                                        e = e9;
                                        sb = new StringBuilder();
                                    }
                                }
                                if (!category.equals(CATEGORY_PLAYSTORE_NONE)) {
                                }
                            } catch (Throwable th5) {
                                str3 = packageName;
                                th = th5;
                                str2 = TAG;
                                str4 = "getApplicationCategory - ";
                                if (conn != null) {
                                    conn.disconnect();
                                }
                                if (br != null) {
                                    try {
                                        br.close();
                                    } catch (IOException e10) {
                                        Log.w(str2, str4 + str3);
                                        e10.printStackTrace();
                                    }
                                }
                                throw th;
                            }
                        }
                        conn.disconnect();
                        if (br != null) {
                            try {
                                br.close();
                            } catch (IOException e11) {
                                Log.w(TAG, "getApplicationCategory - " + packageName);
                                e11.printStackTrace();
                            }
                        }
                        category = category3;
                    } catch (IOException e12) {
                        str5 = packageName;
                        str7 = TAG;
                        str6 = "getApplicationCategory - ";
                        conn2 = conn;
                        category2 = category3;
                        Log.w(str2, "getApplicationCategory - IOException " + str3);
                        if (conn2 != null) {
                        }
                        if (br != null) {
                        }
                        if (!category.equals(CATEGORY_PLAYSTORE_NONE)) {
                        }
                    } catch (Exception e13) {
                        str3 = packageName;
                        str2 = TAG;
                        str4 = "getApplicationCategory - ";
                        conn2 = conn;
                        category = category3;
                        Log.w(str2, "getApplicationCategory - Exception " + str3);
                        if (conn2 != null) {
                        }
                        if (br != null) {
                        }
                        if (!category.equals(CATEGORY_PLAYSTORE_NONE)) {
                        }
                    } catch (Throwable th6) {
                        str3 = packageName;
                        str2 = TAG;
                        str4 = "getApplicationCategory - ";
                        th = th6;
                        if (conn != null) {
                        }
                        if (br != null) {
                        }
                        throw th;
                    }
                } catch (IOException e14) {
                    str5 = packageName;
                    str6 = "getApplicationCategory - ";
                    str7 = TAG;
                    category2 = category3;
                    Log.w(str2, "getApplicationCategory - IOException " + str3);
                    if (conn2 != null) {
                    }
                    if (br != null) {
                    }
                    if (!category.equals(CATEGORY_PLAYSTORE_NONE)) {
                    }
                } catch (Exception e15) {
                    str3 = packageName;
                    str4 = "getApplicationCategory - ";
                    str2 = TAG;
                    category = category3;
                    Log.w(str2, "getApplicationCategory - Exception " + str3);
                    if (conn2 != null) {
                    }
                    if (br != null) {
                    }
                    if (!category.equals(CATEGORY_PLAYSTORE_NONE)) {
                    }
                } catch (Throwable th7) {
                    str3 = packageName;
                    str4 = "getApplicationCategory - ";
                    str2 = TAG;
                    th = th7;
                    conn = null;
                    if (conn != null) {
                    }
                    if (br != null) {
                    }
                    throw th;
                }
            } catch (IOException e16) {
                str5 = packageName;
                str6 = "getApplicationCategory - ";
                str7 = TAG;
                category2 = category3;
                Log.w(str2, "getApplicationCategory - IOException " + str3);
                if (conn2 != null) {
                }
                if (br != null) {
                }
                if (!category.equals(CATEGORY_PLAYSTORE_NONE)) {
                }
            } catch (Exception e17) {
                str3 = packageName;
                str4 = "getApplicationCategory - ";
                str2 = TAG;
                category = category3;
                Log.w(str2, "getApplicationCategory - Exception " + str3);
                if (conn2 != null) {
                }
                if (br != null) {
                }
                if (!category.equals(CATEGORY_PLAYSTORE_NONE)) {
                }
            } catch (Throwable th8) {
                str3 = packageName;
                str4 = "getApplicationCategory - ";
                str2 = TAG;
                th = th8;
                conn = null;
                if (conn != null) {
                }
                if (br != null) {
                }
                throw th;
            }
            if (!category.equals(CATEGORY_PLAYSTORE_NONE)) {
                return getFrameworkApplicationCategory(context, packageName);
            }
            return category;
        }
        Log.w(str, "getApplicationCategory - null params");
        return null;
        sb.append(str4);
        sb.append(str3);
        Log.w(str2, sb.toString());
        e.printStackTrace();
        if (!category.equals(CATEGORY_PLAYSTORE_NONE)) {
        }
    }

    private static String getFrameworkApplicationCategory(Context context, String packageName) {
        int category = -1;
        try {
            category = context.getPackageManager().getApplicationInfo(packageName, 128).category;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "getApplicationCategory - NameNotFoundException " + e);
            e.printStackTrace();
        } catch (Exception e2) {
            Log.w(TAG, "getApplicationCategory - Exception " + e2);
            e2.printStackTrace();
        }
        switch (category) {
            case 0:
                return CATEGORY_PLAYSTORE_GAME;
            case 1:
                return CATEGORY_PLAYSTORE_MUSIC_AND_AUDIO;
            case 2:
                return CATEGORY_PLAYSTORE_VIDEO_PLAYERS;
            case 3:
                return CATEGORY_PLAYSTORE_PHOTOGRAPHY;
            case 4:
                return CATEGORY_PLAYSTORE_SOCIAL;
            case 5:
                return CATEGORY_PLAYSTORE_NEWS_AND_MAGAZINES;
            case 6:
                return CATEGORY_PLAYSTORE_MAPS_AND_NAVIGATION;
            case 7:
                return CATEGORY_PLAYSTORE_PRODUCTIVITY;
            default:
                return CATEGORY_PLAYSTORE_NONE;
        }
    }

    public static boolean isSemGamePackage(String packageName) {
        if (!SemGameManager.isAvailable() || !SemGameManager.isGamePackage(packageName)) {
            return false;
        }
        return true;
    }

    public static boolean isAudioCommunicationMode(Context context) {
        try {
            AudioManager am = (AudioManager) context.getSystemService("audio");
            if (am.getMode() == 3) {
                if (!DBG) {
                    return true;
                }
                Log.d(TAG, "isAudioCommunicationMode - true");
                return true;
            } else if (!DBG) {
                return false;
            } else {
                Log.d(TAG, "isAudioCommunicationMode - false - " + am.getMode());
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "isAudioCommunicationMode - " + e);
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isLauchablePackage(Context context, String packageName) {
        if (context.getPackageManager().getLaunchIntentForPackage(packageName) != null) {
            return true;
        }
        return false;
    }

    public static ArrayList<String> getBrowserPackageNameList(Context context) {
        ArrayList<String> result = new ArrayList<>();
        try {
            Intent intent = new Intent("android.intent.action.VIEW");
            intent.setData(Uri.parse("http://www.google.com"));
            for (ResolveInfo info : context.getPackageManager().queryIntentActivities(intent, ISupplicantStaNetwork.KeyMgmtMask.SUITE_B_192)) {
                result.add(info.activityInfo.packageName);
            }
        } catch (Exception e) {
            Log.w(TAG, "getBrowserPackageNameList - Exception " + e);
            e.printStackTrace();
        }
        return result;
    }

    public static boolean isBrowserApp(Context context, String packageName) {
        try {
            Intent intent = new Intent("android.intent.action.VIEW");
            intent.setData(Uri.parse("http://www.google.com"));
            for (ResolveInfo info : context.getPackageManager().queryIntentActivities(intent, ISupplicantStaNetwork.KeyMgmtMask.SUITE_B_192)) {
                if (info.activityInfo.packageName.equals(packageName)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.w(TAG, "isBrowserApp - Exception " + e);
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isChatApp(String packageName) {
        for (String chatApp : CHAT_APPS) {
            if (chatApp.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSystemApp(Context context, String packageName) {
        try {
            if (context.getPackageManager().getApplicationInfo(packageName, 128).isSystemApp()) {
                return true;
            }
            return false;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "isSystemApp - NameNotFoundException " + e);
            e.printStackTrace();
            return false;
        }
    }

    public static boolean hasPermission(Context context, String packageName, String permission) {
        return context.getPackageManager().checkPermission(permission, packageName) == 0;
    }
}

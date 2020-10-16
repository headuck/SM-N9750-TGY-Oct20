package com.android.server.wifi;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.util.Log;
import com.android.internal.notification.SystemNotificationChannels;

public class ConnectToNetworkNotificationBuilder {
    public static final String ACTION_CONNECT_TO_NETWORK = "com.android.server.wifi.ConnectToNetworkNotification.CONNECT_TO_NETWORK";
    public static final String ACTION_PICK_WIFI_NETWORK = "com.android.server.wifi.ConnectToNetworkNotification.PICK_WIFI_NETWORK";
    public static final String ACTION_PICK_WIFI_NETWORK_AFTER_CONNECT_FAILURE = "com.android.server.wifi.ConnectToNetworkNotification.PICK_NETWORK_AFTER_FAILURE";
    public static final String ACTION_USER_DISMISSED_NOTIFICATION = "com.android.server.wifi.ConnectToNetworkNotification.USER_DISMISSED_NOTIFICATION";
    public static final String AVAILABLE_NETWORK_NOTIFIER_TAG = "com.android.server.wifi.ConnectToNetworkNotification.AVAILABLE_NETWORK_NOTIFIER_TAG";
    private Context mContext;
    private FrameworkFacade mFrameworkFacade;
    private Resources mResources;

    public ConnectToNetworkNotificationBuilder(Context context, FrameworkFacade framework) {
        this.mContext = context;
        this.mResources = context.getResources();
        this.mFrameworkFacade = framework;
    }

    /* JADX WARNING: Removed duplicated region for block: B:13:0x0028  */
    /* JADX WARNING: Removed duplicated region for block: B:17:0x004b  */
    public Notification createConnectToAvailableNetworkNotification(String notifierTag, ScanResult network) {
        char c;
        CharSequence title;
        int hashCode = notifierTag.hashCode();
        if (hashCode != 594918769) {
            if (hashCode == 2017428693 && notifierTag.equals(OpenNetworkNotifier.TAG)) {
                c = 0;
                if (c == 0) {
                    title = this.mContext.getText(17042630);
                } else if (c != 1) {
                    Log.wtf("ConnectToNetworkNotificationBuilder", "Unknown network notifier." + notifierTag);
                    return null;
                } else {
                    title = this.mContext.getText(17042627);
                }
                return createNotificationBuilder(title, network.SSID, notifierTag).setContentIntent(getPrivateBroadcast(ACTION_PICK_WIFI_NETWORK, notifierTag)).addAction(new Notification.Action.Builder((Icon) null, this.mResources.getText(17042626), getPrivateBroadcast(ACTION_CONNECT_TO_NETWORK, notifierTag)).build()).addAction(new Notification.Action.Builder((Icon) null, this.mResources.getText(17042625), getPrivateBroadcast(ACTION_PICK_WIFI_NETWORK, notifierTag)).build()).build();
            }
        } else if (notifierTag.equals(CarrierNetworkNotifier.TAG)) {
            c = 1;
            if (c == 0) {
            }
            return createNotificationBuilder(title, network.SSID, notifierTag).setContentIntent(getPrivateBroadcast(ACTION_PICK_WIFI_NETWORK, notifierTag)).addAction(new Notification.Action.Builder((Icon) null, this.mResources.getText(17042626), getPrivateBroadcast(ACTION_CONNECT_TO_NETWORK, notifierTag)).build()).addAction(new Notification.Action.Builder((Icon) null, this.mResources.getText(17042625), getPrivateBroadcast(ACTION_PICK_WIFI_NETWORK, notifierTag)).build()).build();
        }
        c = 65535;
        if (c == 0) {
        }
        return createNotificationBuilder(title, network.SSID, notifierTag).setContentIntent(getPrivateBroadcast(ACTION_PICK_WIFI_NETWORK, notifierTag)).addAction(new Notification.Action.Builder((Icon) null, this.mResources.getText(17042626), getPrivateBroadcast(ACTION_CONNECT_TO_NETWORK, notifierTag)).build()).addAction(new Notification.Action.Builder((Icon) null, this.mResources.getText(17042625), getPrivateBroadcast(ACTION_PICK_WIFI_NETWORK, notifierTag)).build()).build();
    }

    public Notification createOpenNetworkAvailableNotification(String notifierTag, int numNetworks) {
        new Notification.Action.Builder((Icon) null, this.mResources.getText(17042626), getPrivateBroadcast(ACTION_CONNECT_TO_NETWORK, notifierTag)).build();
        return createNotificationBuilder(this.mContext.getResources().getQuantityText(18153512, numNetworks), this.mContext.getResources().getQuantityText(18153513, numNetworks), notifierTag).addAction(new Notification.Action.Builder((Icon) null, this.mResources.getText(17042625), getPrivateBroadcast(ACTION_PICK_WIFI_NETWORK, notifierTag)).build()).setContentIntent(getPrivateBroadcast(ACTION_PICK_WIFI_NETWORK, notifierTag)).setAutoCancel(true).build();
    }

    public Notification createVZWOpenNetworkAvailableNotification(String notifierTag) {
        new Notification.Action.Builder((Icon) null, this.mResources.getText(17042626), getPrivateBroadcast(ACTION_CONNECT_TO_NETWORK, notifierTag)).build();
        return createNotificationBuilder(this.mContext.getText(17042681), this.mContext.getText(17042682), notifierTag).addAction(new Notification.Action.Builder((Icon) null, this.mResources.getText(17042625), getPrivateBroadcast(ACTION_PICK_WIFI_NETWORK, notifierTag)).build()).setContentIntent(getPrivateBroadcast(ACTION_PICK_WIFI_NETWORK, notifierTag)).setSmallIcon(17304253).setAutoCancel(true).build();
    }

    public Notification createNetworkConnectingNotification(String notifierTag, ScanResult network) {
        return createNotificationBuilder(this.mContext.getText(17042632), network.SSID, notifierTag).setProgress(0, 0, true).build();
    }

    public Notification createNetworkConnectedNotification(String notifierTag, ScanResult network) {
        return createNotificationBuilder(this.mContext.getText(17042631), network.SSID, notifierTag).build();
    }

    public Notification createNetworkFailedNotification(String notifierTag) {
        return createNotificationBuilder(this.mContext.getText(17042633), this.mContext.getText(17042628), notifierTag).setContentIntent(getPrivateBroadcast(ACTION_PICK_WIFI_NETWORK_AFTER_CONNECT_FAILURE, notifierTag)).setAutoCancel(true).build();
    }

    /* JADX WARNING: Removed duplicated region for block: B:12:0x0028  */
    /* JADX WARNING: Removed duplicated region for block: B:15:0x002d A[RETURN] */
    private int getNotifierRequestCode(String notifierTag) {
        char c;
        int hashCode = notifierTag.hashCode();
        if (hashCode != 594918769) {
            if (hashCode == 2017428693 && notifierTag.equals(OpenNetworkNotifier.TAG)) {
                c = 0;
                if (c == 0) {
                    return 1;
                }
                if (c != 1) {
                    return 0;
                }
                return 2;
            }
        } else if (notifierTag.equals(CarrierNetworkNotifier.TAG)) {
            c = 1;
            if (c == 0) {
            }
        }
        c = 65535;
        if (c == 0) {
        }
    }

    private Notification.Builder createNotificationBuilder(CharSequence title, CharSequence content, String extraData) {
        return this.mFrameworkFacade.makeNotificationBuilder(this.mContext, SystemNotificationChannels.NETWORK_AVAILABLE).setSmallIcon(17304154).setTicker(title).setContentTitle(title).setContentText(content).setDeleteIntent(getPrivateBroadcast(ACTION_USER_DISMISSED_NOTIFICATION, extraData)).setShowWhen(false).setLocalOnly(true).setColor(this.mResources.getColor(17170460, this.mContext.getTheme()));
    }

    public Notification createHotspot20ConnectedNotification(String notifierTag, WifiConfiguration wifiConfiguration, String venueName) {
        String title;
        String message;
        if (venueName == null) {
            title = String.format(this.mContext.getResources().getString(17042646), wifiConfiguration.SSID);
        } else {
            title = String.format(this.mContext.getResources().getString(17042647), wifiConfiguration.SSID, venueName);
        }
        if (wifiConfiguration.providerFriendlyName != null) {
            message = String.format(this.mContext.getResources().getString(17042644, wifiConfiguration.providerFriendlyName), new Object[0]);
        } else {
            message = String.format(this.mContext.getResources().getString(17042645, wifiConfiguration.providerFriendlyName), new Object[0]);
        }
        Intent intent = new Intent();
        intent.setClassName("com.android.settings", "com.android.settings.Settings$ConfigureWifiSettingsActivity");
        intent.addFlags(268468224);
        return createNotificationBuilder(title, message, notifierTag).setSmallIcon(17302392).setContentIntent(this.mFrameworkFacade.getActivity(this.mContext, 0, intent, 134217728)).setAutoCancel(true).build();
    }

    private PendingIntent getPrivateBroadcast(String action, String extraData) {
        Intent intent = new Intent(action).setPackage("android");
        int requestCode = 0;
        if (extraData != null) {
            intent.putExtra(AVAILABLE_NETWORK_NOTIFIER_TAG, extraData);
            requestCode = getNotifierRequestCode(extraData);
        }
        return this.mFrameworkFacade.getBroadcast(this.mContext, requestCode, intent, 134217728);
    }
}

package com.samsung.android.server.wifi.share;

import com.samsung.android.server.wifi.share.McfDataUtil;

/* access modifiers changed from: package-private */
public interface ISubscriberCallback {
    void onFailedPasswordDelivery();

    void onFoundDevicesForPassword(boolean z);

    void onPasswordDelivered(McfDataUtil.McfData mcfData);

    void onQoSDataDelivered(McfDataUtil.McfData mcfData);
}

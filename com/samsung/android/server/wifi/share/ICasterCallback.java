package com.samsung.android.server.wifi.share;

import com.samsung.android.server.wifi.share.McfDataUtil;

/* access modifiers changed from: package-private */
public interface ICasterCallback {
    void onPasswordRequested(McfDataUtil.McfData mcfData, String str);

    void onSessionClosed(String str);
}

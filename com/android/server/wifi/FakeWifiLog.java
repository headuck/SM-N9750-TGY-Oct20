package com.android.server.wifi;

import com.android.server.wifi.WifiLog;

public class FakeWifiLog implements WifiLog {
    private static final DummyLogMessage sDummyLogMessage = new DummyLogMessage();

    @Override // com.android.server.wifi.WifiLog
    public WifiLog.LogMessage err(String format) {
        return sDummyLogMessage;
    }

    @Override // com.android.server.wifi.WifiLog
    public WifiLog.LogMessage warn(String format) {
        return sDummyLogMessage;
    }

    @Override // com.android.server.wifi.WifiLog
    public WifiLog.LogMessage info(String format) {
        return sDummyLogMessage;
    }

    @Override // com.android.server.wifi.WifiLog
    public WifiLog.LogMessage trace(String format) {
        return sDummyLogMessage;
    }

    @Override // com.android.server.wifi.WifiLog
    public WifiLog.LogMessage trace(String format, int numFramesToIgnore) {
        return sDummyLogMessage;
    }

    @Override // com.android.server.wifi.WifiLog
    public WifiLog.LogMessage dump(String format) {
        return sDummyLogMessage;
    }

    @Override // com.android.server.wifi.WifiLog
    /* renamed from: eC */
    public void mo2087eC(String msg) {
    }

    @Override // com.android.server.wifi.WifiLog
    /* renamed from: wC */
    public void mo2097wC(String msg) {
    }

    @Override // com.android.server.wifi.WifiLog
    /* renamed from: iC */
    public void mo2090iC(String msg) {
    }

    @Override // com.android.server.wifi.WifiLog
    /* renamed from: tC */
    public void mo2092tC(String msg) {
    }

    @Override // com.android.server.wifi.WifiLog
    /* renamed from: e */
    public void mo2086e(String msg) {
    }

    @Override // com.android.server.wifi.WifiLog
    /* renamed from: w */
    public void mo2096w(String msg) {
    }

    @Override // com.android.server.wifi.WifiLog
    /* renamed from: i */
    public void mo2089i(String msg) {
    }

    @Override // com.android.server.wifi.WifiLog
    /* renamed from: d */
    public void mo2084d(String msg) {
    }

    @Override // com.android.server.wifi.WifiLog
    /* renamed from: v */
    public void mo2095v(String msg) {
    }
}

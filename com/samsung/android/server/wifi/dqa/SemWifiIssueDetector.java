package com.samsung.android.server.wifi.dqa;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.util.Log;
import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class SemWifiIssueDetector extends Handler {
    private static final int MAX_LIST_SIZE = (ActivityManager.isLowRamDeviceStatic() ? 100 : 500);
    public static final String TAG = "WifiIssueDetector";
    private final WifiIssueDetectorAdapter mAdapter;
    private final Context mContext;
    private final String mFilePath = "/data/misc/wifi/issue_detector.conf";
    private final List<Integer> mIssueReport = new ArrayList();
    private IExternalDiagnosticListener mListener;
    private Object mLogLock = new Object();
    private final List<ReportData> mLogs = new ArrayList();
    private final List<WifiIssuePattern> mPatterns = new ArrayList();

    public interface IExternalDiagnosticListener {
        void onReportAdded(int i);
    }

    public interface WifiIssueDetectorAdapter {
        void sendBigData(Bundle bundle);
    }

    public SemWifiIssueDetector(Context context, Looper workerLooper, WifiIssueDetectorAdapter adapter) {
        super(workerLooper);
        this.mContext = context;
        this.mAdapter = adapter;
        WifiIssuePattern pattern = new PatternWifiDisconnect();
        this.mPatterns.add(pattern);
        this.mIssueReport.addAll(pattern.getAssociatedKeys());
        WifiIssuePattern pattern2 = new PatternWifiConnecting();
        this.mPatterns.add(pattern2);
        this.mIssueReport.addAll(pattern2.getAssociatedKeys());
    }

    public void captureBugReport(int reportId, Bundle report) {
        sendMessage(obtainMessage(0, reportId, 0, report));
    }

    public void handleMessage(Message msg) {
        int reportId = msg.arg1;
        Bundle data = (Bundle) msg.obj;
        if (data != null) {
            report(reportId, data);
        }
    }

    private void report(int reportId, Bundle data) {
        ReportData reportData;
        if (reportId > 0) {
            if (data.containsKey(WifiIssuePattern.KEY_HUMAN_READABLE_TIME)) {
                data.remove(WifiIssuePattern.KEY_HUMAN_READABLE_TIME);
            }
            if (data.containsKey("time")) {
                long time = ((Long) WifiIssuePattern.getValue(data, "time", (Object) 0L)).longValue();
                data.remove("time");
                reportData = new ReportData(reportId, data, time);
            } else {
                reportData = new ReportData(reportId, data);
            }
            addLog(reportData);
            Log.d(TAG, "report " + reportData.toString());
            IExternalDiagnosticListener iExternalDiagnosticListener = this.mListener;
            if (iExternalDiagnosticListener != null) {
                iExternalDiagnosticListener.onReportAdded(reportId);
            }
            if (this.mIssueReport.contains(Integer.valueOf(reportId))) {
                attemptIssueDetection(reportId, reportData);
            }
        }
    }

    public void setExternalDiagnosticListener(IExternalDiagnosticListener listener) {
        this.mListener = listener;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("SemWifiIssueDetector:");
        synchronized (this.mLogLock) {
            for (ReportData data : this.mLogs) {
                pw.println(data.toString());
            }
        }
        pw.println("SemWifiIssueDetectorHistory:");
        readFile(pw);
    }

    public String getRawData(int size) {
        Throwable th;
        int counter = 0;
        StringBuffer sb = new StringBuffer();
        synchronized (this.mLogLock) {
            try {
                int i = this.mLogs.size() - 1;
                while (true) {
                    if (i < 0) {
                        break;
                    }
                    int counter2 = counter + 1;
                    if (counter > size) {
                        break;
                    }
                    try {
                        sb.append(this.mLogs.get(i).toString());
                        sb.append("\n");
                        i--;
                        counter = counter2;
                    } catch (Throwable th2) {
                        th = th2;
                        throw th;
                    }
                }
                return sb.toString();
            } catch (Throwable th3) {
                th = th3;
                throw th;
            }
        }
    }

    private void attemptIssueDetection(int reportId, ReportData reportData) {
        for (WifiIssuePattern pattern : this.mPatterns) {
            if (pattern.isAssociated(reportId, reportData)) {
                synchronized (this.mLogLock) {
                    if (pattern.matches(this.mLogs)) {
                        String patternId = pattern.getPatternId();
                        Log.i(TAG, "pattern matched! pid=" + patternId);
                        Bundle bigDataParams = pattern.getBigDataParams();
                        if (bigDataParams != null) {
                            bigDataParams.putString(ReportIdKey.KEY_PATTERN_ID, patternId);
                            this.mAdapter.sendBigData(bigDataParams);
                            sendBroadcastSecIssueDetected(bigDataParams.getString("feature"), patternId, bigDataParams.getInt(ReportIdKey.KEY_CATEGORY_ID, 0));
                            ReportData matchedReport = new ReportData(ReportIdKey.ID_PATTERN_MATCHED, (Bundle) bigDataParams.clone());
                            addLog(matchedReport);
                            writeLog(matchedReport.toString() + "\n");
                            IExternalDiagnosticListener iExternalDiagnosticListener = this.mListener;
                            if (iExternalDiagnosticListener != null) {
                                iExternalDiagnosticListener.onReportAdded(ReportIdKey.ID_PATTERN_MATCHED);
                            }
                        }
                    }
                }
            }
        }
    }

    private void sendBroadcastSecIssueDetected(String bigdataFeature, String patternId, int categoryId) {
        Intent intent = new Intent("com.samsung.android.net.wifi.ISSUE_DETECTED");
        intent.addFlags(67108864);
        intent.putExtra("bigdataFeature", bigdataFeature);
        intent.putExtra(ReportIdKey.KEY_PATTERN_ID, patternId);
        intent.putExtra(ReportIdKey.KEY_CATEGORY_ID, categoryId);
        sendBroadcastForCurrentUser(intent);
    }

    private void sendBroadcastForCurrentUser(Intent intent) {
        Context context = this.mContext;
        if (context != null) {
            try {
                context.sendBroadcastAsUser(intent, UserHandle.CURRENT);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Send broadcast before boot - action:" + intent.getAction());
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:21:0x0039 A[SYNTHETIC, Splitter:B:21:0x0039] */
    private synchronized void readFile(PrintWriter pw) {
        RandomAccessFile raf = null;
        try {
            if (!new File("/data/misc/wifi/issue_detector.conf").exists()) {
                try {
                    pw.println("not exist");
                } catch (Exception e) {
                } catch (Throwable th) {
                    th = th;
                    if (0 != 0) {
                    }
                    throw th;
                }
            }
            RandomAccessFile raf2 = new RandomAccessFile("/data/misc/wifi/issue_detector.conf", "r");
            while (true) {
                String line = raf2.readLine();
                if (line != null) {
                    pw.println(line);
                } else {
                    try {
                        break;
                    } catch (Exception e2) {
                    }
                }
            }
            raf2.close();
        } catch (Exception e3) {
            if (0 != 0) {
                raf.close();
            }
        } catch (Throwable th2) {
            th = th2;
            if (0 != 0) {
                try {
                    raf.close();
                } catch (Exception e4) {
                }
            }
            throw th;
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:20:0x0045 A[SYNTHETIC, Splitter:B:20:0x0045] */
    private synchronized void writeLog(String logContents) {
        File logFile = new File("/data/misc/wifi/issue_detector.conf");
        RandomAccessFile raf = null;
        try {
            if (!logFile.exists()) {
                try {
                    logFile.createNewFile();
                } catch (Exception e) {
                } catch (Throwable th) {
                    th = th;
                    if (0 != 0) {
                    }
                    throw th;
                }
            } else if (logFile.length() > 10000) {
                logFile.delete();
                logFile.createNewFile();
            }
            RandomAccessFile raf2 = new RandomAccessFile("/data/misc/wifi/issue_detector.conf", "rw");
            raf2.seek(raf2.length());
            raf2.writeBytes(logContents);
            try {
                raf2.close();
            } catch (Exception e2) {
            }
        } catch (Exception e3) {
            if (0 != 0) {
                raf.close();
            }
        } catch (Throwable th2) {
            th = th2;
            if (0 != 0) {
                try {
                    raf.close();
                } catch (Exception e4) {
                }
            }
            throw th;
        }
    }

    private void addLog(ReportData data) {
        synchronized (this.mLogLock) {
            if (this.mLogs.size() > MAX_LIST_SIZE) {
                this.mLogs.remove(0);
            }
            this.mLogs.add(data);
        }
    }
}

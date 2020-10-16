package com.android.server.wifi.iwc.rlengine;

import android.util.Log;
import com.android.server.wifi.hotspot2.anqp.NAIRealmData;
import com.android.server.wifi.iwc.IWCLogFile;
import com.android.server.wifi.iwc.RewardEvent;
import com.android.server.wifi.tcp.WifiTransportLayerUtils;
import java.util.Iterator;

public class RFLearningTop {
    private static final String TAG = "IWCMonitor.RFLearningTop";
    private IWCLogFile IWCLog;
    private String currentAP;
    private int currentState;
    public RFLInterface intf = new RFLInterface();
    private int lastAction;
    private QTableContainer qTables;
    public RewardManager rwManager;

    public RFLearningTop(IWCLogFile logFile) {
        this.qTables = new QTableContainer(logFile);
        this.rwManager = new RewardManager(logFile);
        this.IWCLog = logFile;
        this.currentAP = null;
        this.lastAction = 0;
        this.currentState = 2;
    }

    public void setDefaultQAI() {
        synchronized (this) {
            this.qTables.setDefaultQAI(this.intf);
        }
    }

    public void setDefaultQAI(int forced_qai) {
        synchronized (this) {
            this.qTables.setDefaultQAI(forced_qai);
        }
    }

    public void removeNonSSQtables() {
        this.qTables.removeNonSSQtables();
    }

    public void setCurrentState(int currentState2) {
        this.currentState = currentState2;
    }

    public void algorithmStep() {
        QTableContainer qTableContainer = this.qTables;
        if (qTableContainer == null) {
            Log.e(TAG, "algorithmStep - QTable Container is null");
            return;
        }
        qTableContainer.manageApList();
        printApLists();
        this.currentAP = this.intf.currentApBssid_IN;
        String str = this.currentAP;
        if (str != null) {
            if (this.qTables.findTable(str) == -1) {
                this.qTables.createTable(this.currentAP, this.intf);
            }
            String logValue = "" + String.format("Q-Table: [", new Object[0]);
            int tableIdx = this.qTables.findTable(this.currentAP);
            if (tableIdx == -1) {
                Log.e(TAG, "algorithmStep - FindTable returned -1");
                return;
            }
            QTable tempTable = this.qTables.qTableList.get(tableIdx);
            for (int i = 0; i < tempTable.numStates; i++) {
                for (int j = 0; j < tempTable.numActions; j++) {
                    logValue = logValue + String.format(" %f", Float.valueOf(tempTable.qTable[i][j]));
                }
                logValue = logValue + String.format(NAIRealmData.NAI_REALM_STRING_SEPARATOR, new Object[0]);
            }
            this.currentState = this.qTables.getTableStateCB(this.currentAP);
            String logValue2 = (logValue + String.format(" ] >> ", new Object[0])) + String.format("Action Taken: %d, New State: %d", Integer.valueOf(this.lastAction), Integer.valueOf(this.currentState));
            IWCLogFile iWCLogFile = this.IWCLog;
            if (iWCLogFile != null) {
                iWCLogFile.writeToLogFile("Q-Table Action Taken", logValue2);
            }
        }
    }

    public synchronized boolean rebase() {
        int tableIdx = this.qTables.findTable(this.intf.currentApBssid_IN);
        if (tableIdx == -1) {
            Log.e(TAG, "updateTable - findTable returned -1");
            return false;
        }
        this.qTables.updateApListAccessTime(this.intf.currentApBssid_IN);
        this.qTables.qTableList.get(tableIdx).lastUpdateTime = System.currentTimeMillis();
        this.qTables.setDefaultQAI(this.intf);
        this.qTables.rebaseQTables();
        return true;
    }

    public void updateDebugIntent(RewardEvent re, String currentBssid_IN, boolean switchFlag) {
        int index = this.qTables.findTable(currentBssid_IN);
        QTable qtable = this.qTables.qTableList.get(index);
        this.rwManager.updateDebugIntent(this.intf.mContext, this.rwManager.getEventTypeString(re, switchFlag), currentBssid_IN, index, qtable);
    }

    public synchronized boolean updateTable(RewardEvent eventType, long timestamp) {
        boolean isRebaseRequired = false;
        if (this.intf.edgeFlag || this.intf.snsOptionChanged) {
            if (this.qTables == null) {
                Log.e(TAG, "updateTable - QTable Container is null");
                return false;
            }
            this.qTables.manageApList();
            printApLists();
            this.currentAP = this.intf.currentApBssid_IN;
            if (this.intf.currentApBssid_IN != null && this.qTables.findTable(this.intf.currentApBssid_IN) == -1) {
                this.qTables.addCandidateList(this.intf.currentApBssid_IN);
                this.qTables.createTable(this.intf.currentApBssid_IN, this.intf);
            }
            int currentQAI = this.currentState;
            int SSReach = this.rwManager.applyRewards(eventType, this.qTables, this.intf, timestamp);
            int tableIdx = this.qTables.findTable(this.intf.currentApBssid_IN);
            if (tableIdx == -1) {
                Log.e(TAG, "updateTable - findTable returned -1");
                return false;
            }
            this.qTables.updateApListAccessTime(this.intf.currentApBssid_IN);
            int currentQAI2 = this.qTables.getTableStateCB(this.intf.currentApBssid_IN);
            QTable tempTable = this.qTables.qTableList.get(tableIdx);
            if (SSReach == 1 || (tempTable.getSteadyState() && currentQAI != currentQAI2)) {
                isRebaseRequired = true;
            }
            String strEvent = this.rwManager.getEventTypeString(eventType, this.intf.switchFlag);
            if (!strEvent.equals(WifiTransportLayerUtils.CATEGORY_PLAYSTORE_NONE)) {
                this.rwManager.sendDebugIntent(this.intf.mContext, strEvent, this.intf.currentApBssid_IN, tableIdx, tempTable);
            }
            this.qTables.recordApActivity(this.currentAP, eventType);
            this.intf.mBdTracking.setOUIInfo(this.intf.currentApBssid_IN);
            this.intf.mBdTracking.setStateInfo(this.intf.switchFlag);
            this.intf.mBdTracking.setQAIInfo(currentQAI, currentQAI2);
            String valueEL = "";
            Iterator<Integer> it = tempTable.eventBuffer.iterator();
            while (it.hasNext()) {
                valueEL = valueEL + String.format(" %d", it.next());
            }
            String valueQT = String.format("%.2f %.2f %.2f", Float.valueOf(tempTable.qTable[0][0]), Float.valueOf(tempTable.qTable[1][0]), Float.valueOf(tempTable.qTable[2][0]));
            this.intf.mBdTracking.setEVInfo(valueEL);
            this.intf.mBdTracking.setQTableValueInfo(valueQT);
            if (this.intf.mBdTracking.getIdInfo() == 2 && this.intf.currentApBssid_IN != null) {
                if (this.qTables.candidateApList != null && this.qTables.candidateApList.get(this.intf.currentApBssid_IN) != null) {
                    this.intf.mBdTracking.setSSTakenTimeInfo(System.currentTimeMillis() - this.qTables.candidateApList.get(this.intf.currentApBssid_IN).firstAdded);
                } else if (this.qTables.coreApList != null && this.qTables.coreApList.get(this.intf.currentApBssid_IN) != null) {
                    this.intf.mBdTracking.setSSTakenTimeInfo(System.currentTimeMillis() - this.qTables.coreApList.get(this.intf.currentApBssid_IN).firstAdded);
                } else if (!(this.qTables.probationApList == null || this.qTables.probationApList.get(this.intf.currentApBssid_IN) == null)) {
                    this.intf.mBdTracking.setSSTakenTimeInfo(System.currentTimeMillis() - this.qTables.probationApList.get(this.intf.currentApBssid_IN).firstAdded);
                }
            }
        }
        return isRebaseRequired;
    }

    public int removeQtableIfExist(String bssid) {
        QTableContainer qTableContainer = this.qTables;
        if (qTableContainer != null) {
            return qTableContainer.removeQtableIfExist(bssid);
        }
        Log.e(TAG, "removeQtableIfExist - QTable Container is null");
        return -1;
    }

    public void setCurrentAP(String bssid) {
        this.currentAP = bssid;
    }

    public int getCurrentState() {
        return this.currentState;
    }

    public float[][] getCurrentTableStates() {
        QTableContainer qTableContainer;
        int tableIndex;
        String str = this.currentAP;
        return (str == null || (qTableContainer = this.qTables) == null || (tableIndex = qTableContainer.findTable(str)) == -1) ? new float[][]{new float[]{-1.0f, -1.0f, -1.0f}, new float[]{-1.0f, -1.0f, -1.0f}, new float[]{-1.0f, -1.0f, -1.0f}} : this.qTables.qTableList.get(tableIndex).qTable;
    }

    public int getSteadyStateNum() {
        int res = 0;
        Iterator<QTable> it = this.qTables.qTableList.iterator();
        while (it.hasNext()) {
            if (it.next().getSteadyState()) {
                res++;
            }
        }
        return res;
    }

    public boolean getIsSteadyState(String bssid) {
        QTableContainer qTableContainer;
        int tableIndex;
        if (bssid == null || (qTableContainer = this.qTables) == null || (tableIndex = qTableContainer.findTable(bssid)) == -1) {
            return false;
        }
        return this.qTables.qTableList.get(tableIndex).getSteadyState();
    }

    public String getCurrentAP() {
        return this.currentAP;
    }

    public String getQTableStr() {
        QTableContainer qTableContainer;
        String tmpStr = "";
        String str = this.currentAP;
        if (str == null || (qTableContainer = this.qTables) == null) {
            return "null / null / null";
        }
        int tableIndex = qTableContainer.findTable(str);
        if (tableIndex == -1) {
            return tmpStr;
        }
        QTable tempTable = this.qTables.qTableList.get(tableIndex);
        for (int i = 0; i < tempTable.numStates; i++) {
            tmpStr = i == tempTable.numStates - 1 ? tmpStr + String.format(" %.2f", Float.valueOf(tempTable.qTable[i][0])) : tmpStr + String.format(" %.2f /", Float.valueOf(tempTable.qTable[i][0]));
        }
        return tmpStr;
    }

    public QTableContainer getQtables() {
        return this.qTables;
    }

    public void setQtables(QTableContainer qt) {
        if (qt != null) {
            this.qTables = qt;
        }
    }

    public void printApLists() {
        String value = "Candidate List: [";
        QTableContainer qTableContainer = this.qTables;
        if (qTableContainer == null) {
            Log.e(TAG, "PrintApLists - QTable Container is null");
            return;
        }
        for (String key : qTableContainer.candidateApList.keySet()) {
            value = value + String.format(" <%s;%s;%s> ", key, Integer.valueOf(this.qTables.candidateApList.get(key).activityScore), Long.valueOf(System.currentTimeMillis() - this.qTables.candidateApList.get(key).firstAdded));
        }
        String value2 = value + "], Core List: [";
        for (String key2 : this.qTables.coreApList.keySet()) {
            value2 = value2 + String.format(" <%s;%s> ", key2, Long.valueOf(this.qTables.coreApList.get(key2).lastAccessed));
        }
        String value3 = value2 + "], Probation List: [";
        for (String key3 : this.qTables.probationApList.keySet()) {
            value3 = value3 + String.format(" <%s;%s;%s> ", key3, Integer.valueOf(this.qTables.probationApList.get(key3).activityScore), Long.valueOf(System.currentTimeMillis() - this.qTables.probationApList.get(key3).firstAdded));
        }
        String value4 = value3 + "]";
        IWCLogFile iWCLogFile = this.IWCLog;
        if (iWCLogFile != null) {
            iWCLogFile.writeToLogFile("List of Known APs", value4);
        }
    }

    public int updateQAI() {
        QTableContainer qTableContainer = this.qTables;
        if (qTableContainer == null) {
            Log.e(TAG, "updateQAI - QTable Container is null");
            return -1;
        }
        if (qTableContainer.findTable(this.currentAP) != -1) {
            this.currentState = this.qTables.getTableStateCB(this.currentAP);
        }
        return this.currentState;
    }

    public void printTable() {
        if (this.qTables == null) {
            Log.e(TAG, "PrintTable - QTable Container is null");
            return;
        }
        for (int i = 0; i < this.qTables.qTableList.size(); i++) {
            QTable tempTable = this.qTables.qTableList.get(i);
            String value = (("" + String.format("< %s - %f %f %f >", this.qTables.qTableIndexList.get(i).bssid, Float.valueOf(tempTable.qTable[0][0]), Float.valueOf(tempTable.qTable[1][0]), Float.valueOf(tempTable.qTable[2][0]))) + String.format(" < isSteadyState - %d, mLastSNS - %d, mLastAGG - %d >", Integer.valueOf(tempTable.isSteadyState ? 1 : 0), Integer.valueOf(tempTable.mLastSNS), Integer.valueOf(tempTable.mLastAGG))) + String.format(" < EventBuffer ", new Object[0]);
            Iterator<Integer> it = tempTable.eventBuffer.iterator();
            while (it.hasNext()) {
                value = value + String.format(" %d", it.next());
            }
            String value2 = value + String.format(" >", new Object[0]);
            IWCLogFile iWCLogFile = this.IWCLog;
            if (iWCLogFile != null) {
                iWCLogFile.writeToLogFile("Qtable Dump", value2);
            }
        }
    }

    public void printCurrentTable(String bssid) {
        String value = "";
        if (this.qTables == null) {
            Log.e(TAG, "PrintCurrentTable - QTable Container is null");
            return;
        }
        for (int i = 0; i < this.qTables.qTableList.size(); i++) {
            if (this.qTables.qTableIndexList.get(i).bssid.equals(bssid)) {
                QTable tempTable = this.qTables.qTableList.get(i);
                String value2 = ((value + String.format("< %s - %f %f %f >", bssid, Float.valueOf(tempTable.qTable[0][0]), Float.valueOf(tempTable.qTable[1][0]), Float.valueOf(tempTable.qTable[2][0]))) + String.format(" < isSteadyState - %d, mLastSNS - %d, mLastAGG - %d >", Integer.valueOf(tempTable.isSteadyState ? 1 : 0), Integer.valueOf(tempTable.mLastSNS), Integer.valueOf(tempTable.mLastAGG))) + String.format(" < EventBuffer ", new Object[0]);
                Iterator<Integer> it = tempTable.eventBuffer.iterator();
                while (it.hasNext()) {
                    value2 = value2 + String.format(" %d", it.next());
                }
                value = value2 + String.format(" >", new Object[0]);
                IWCLogFile iWCLogFile = this.IWCLog;
                if (iWCLogFile != null) {
                    iWCLogFile.writeToLogFile("Qtable Dump(current bssid)", value);
                }
            }
        }
    }

    public void putBssidToConfigKey(String key, String bssid) {
        QTableContainer qTableContainer;
        if (key == null || (qTableContainer = this.qTables) == null) {
            Log.e(TAG, "QTable Container is null or " + key);
            return;
        }
        qTableContainer.putBssidToConfigKey(key, bssid);
    }

    public void removeConfigKey(String key) {
        QTableContainer qTableContainer;
        if (key == null || (qTableContainer = this.qTables) == null) {
            Log.e(TAG, "QTable Container is null or " + key);
            return;
        }
        qTableContainer.removeConfigKey(key);
    }
}

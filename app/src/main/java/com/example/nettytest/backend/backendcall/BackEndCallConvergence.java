package com.example.nettytest.backend.backendcall;

import java.util.ArrayList;

import com.alibaba.fastjson.*;
import com.example.nettytest.backend.backendphone.BackEndPhone;
import com.example.nettytest.backend.backendphone.BackEndPhoneManager;
import com.example.nettytest.pub.AlertConfig;
import com.example.nettytest.pub.HandlerMgr;
import com.example.nettytest.pub.LogWork;
import com.example.nettytest.pub.SystemSnap;
import com.example.nettytest.pub.UniqueIDManager;
import com.example.nettytest.pub.phonecall.CommonCall;
import com.example.nettytest.pub.protocol.AnswerReqPack;
import com.example.nettytest.pub.protocol.AnswerResPack;
import com.example.nettytest.pub.protocol.AnswerVideoReqPack;
import com.example.nettytest.pub.protocol.AnswerVideoResPack;
import com.example.nettytest.pub.protocol.CancelReqPack;
import com.example.nettytest.pub.protocol.CancelResPack;
import com.example.nettytest.pub.protocol.EndReqPack;
import com.example.nettytest.pub.protocol.EndResPack;
import com.example.nettytest.pub.protocol.InviteReqPack;
import com.example.nettytest.pub.protocol.InviteResPack;
import com.example.nettytest.pub.protocol.ProtocolPacket;
import com.example.nettytest.pub.protocol.StartVideoReqPack;
import com.example.nettytest.pub.protocol.StartVideoResPack;
import com.example.nettytest.pub.protocol.StopVideoReqPack;
import com.example.nettytest.pub.protocol.StopVideoResPack;
import com.example.nettytest.pub.protocol.UpdateReqPack;
import com.example.nettytest.pub.protocol.UpdateResPack;
import com.example.nettytest.pub.transaction.Transaction;
import com.example.nettytest.userinterface.CallLogMessage;
import com.example.nettytest.userinterface.PhoneParam;
import com.example.nettytest.userinterface.UserInterface;

public class BackEndCallConvergence {
    BackEndCall inviteCall;
    int autoAnswerTick;
    int autoAnswerTime;

    long answerTime;
    long startTime;

    boolean isVideoMode;

    private final String callerNum;
    private final String calleeNum;
    private String answerNum;
    private String enderNum;

    private final String inviteAreaId;

    ArrayList<BackEndCall> listenCallList;

    final static int BROAD_CAST_PORT_START = 19094;
    final static int BROAD_CAST_PORT_NUM = 10000;

    static int broadCastPort = BROAD_CAST_PORT_START;
    int curBroadCastPort =BROAD_CAST_PORT_START ;
    InviteReqPack backupInviteReqP; // backup for build new listen Call when Phone Set Listen flag


    public String GetCallerId(){
        return callerNum;
    }

    public BackEndCallConvergence(BackEndPhone caller, InviteReqPack pack) {

        BackEndCall listenCall;
        InviteReqPack invitePacket;
        InviteResPack inviteResP;

        String listenAreaId;
        boolean isTransfer = false;
        int result;

        inviteAreaId = caller.devInfo.areaId;
        
        if(pack.callType==CommonCall.CALL_TYPE_BROADCAST){
            listenAreaId = inviteAreaId;
            broadCastPort++;
            if(broadCastPort>=BROAD_CAST_PORT_START+BROAD_CAST_PORT_NUM){
                broadCastPort = BROAD_CAST_PORT_START;
            }
            curBroadCastPort = broadCastPort;
        }else{
            listenAreaId = HandlerMgr.GetForwardAreaId(caller.id);   
        }
        
        if(caller.devInfo.areaId.compareToIgnoreCase(listenAreaId)!=0){
            isTransfer = true;
        }
        
        ArrayList<BackEndPhone> listenDevices = HandlerMgr.GetBackEndListenDevices(listenAreaId,pack.callType);

        if(pack.callType>=CommonCall.ALERT_TYPE_BEGIN&&pack.callType<=CommonCall.ALERT_TYPE_ENDED){
            AlertConfig alertConfig = HandlerMgr.GetBackEndAlertConfig(listenAreaId,pack.callType);
            if(alertConfig==null){
                result = ProtocolPacket.STATUS_NOTSUPPORT;
            }else{
                pack.nameType = alertConfig.nameType;
                pack.voiceInfo = alertConfig.voiceInfo;
                pack.displayInfo = alertConfig.displayInfo;
                result = ProtocolPacket.STATUS_OK;
            }
        }else {
            if (listenDevices.size() > 0) {
                result = ProtocolPacket.STATUS_OK;
            } else {
                result = ProtocolPacket.STATUS_OK;
            }
        }

        inviteResP = new InviteResPack(result, pack);

        Transaction transaction = new Transaction(caller.id,pack, inviteResP,Transaction.TRANSCATION_DIRECTION_S2C);
        HandlerMgr.AddBackEndTrans(pack.msgID,transaction);

        callerNum = pack.caller;
        calleeNum = pack.callee;
        answerNum = "";
        enderNum = "";

        listenCallList = new ArrayList<>();

        autoAnswerTick = 0;
        autoAnswerTime = pack.autoAnswerTime;

        inviteCall = new BackEndCall(caller.id,pack);

        inviteCall.state = CommonCall.CALL_STATE_INCOMING;
        startTime = System.currentTimeMillis();
        answerTime = 0;
        isVideoMode = false;
        backupInviteReqP = new InviteReqPack();
        backupInviteReqP.Clone(pack);

        if(result==ProtocolPacket.STATUS_OK) {
            for (BackEndPhone phone : listenDevices) {
                if (caller.id.compareToIgnoreCase(phone.id) == 0)
                    continue;
                if (CheckForwardEnable(phone, pack.callType)) {
                    invitePacket = new InviteReqPack();
                    invitePacket.ExchangeCopyData(pack);
                    invitePacket.areaId = caller.devInfo.areaId;
                    invitePacket.areaName = caller.devInfo.areaName;
                    invitePacket.isTransfer = isTransfer;

                    if (invitePacket.callType == CommonCall.CALL_TYPE_BROADCAST) {
                        invitePacket.autoAnswerTime = invitePacket.autoAnswerTime / 2;
                        invitePacket.callerRtpPort = curBroadCastPort;
                    }
                    invitePacket.receiver = phone.id;
                    invitePacket.msgID = UniqueIDManager.GetUniqueID(phone.id, UniqueIDManager.MSG_UNIQUE_ID);

                    listenCall = new BackEndCall(phone.id, invitePacket);
                    listenCall.inviteReqMsgId = invitePacket.msgID;
                    transaction = new Transaction(phone.id, invitePacket, Transaction.TRANSCATION_DIRECTION_S2C);
                    HandlerMgr.AddBackEndTrans(invitePacket.msgID, transaction);
                    listenCallList.add(listenCall);
                }
            }
        }
    }

    public BackEndCallConvergence(BackEndPhone caller,BackEndPhone callee, InviteReqPack pack){
        InviteReqPack invitePacket;

        InviteResPack inviteResP = new InviteResPack(ProtocolPacket.STATUS_OK,pack);

        Transaction transaction = new Transaction(caller.id,pack, inviteResP,Transaction.TRANSCATION_DIRECTION_S2C);
        HandlerMgr.AddBackEndTrans(pack.msgID,transaction);

        inviteCall = new BackEndCall(caller.id,pack);
        autoAnswerTick = 0;
        autoAnswerTime = pack.autoAnswerTime;
        inviteAreaId = caller.devInfo.areaId;

        callerNum = pack.caller;
        calleeNum = pack.callee;
        answerNum = "";
        enderNum = "";

        listenCallList = new ArrayList<>();
        if(callee!=null) {
            invitePacket = new InviteReqPack();
            invitePacket.ExchangeCopyData(pack);
            invitePacket.receiver = callee.id;
            invitePacket.msgID = UniqueIDManager.GetUniqueID(callee.id, UniqueIDManager.MSG_UNIQUE_ID);
            transaction = new Transaction(callee.id, invitePacket, Transaction.TRANSCATION_DIRECTION_S2C);
            HandlerMgr.AddBackEndTrans(invitePacket.msgID, transaction);
            inviteCall.inviteReqMsgId = invitePacket.msgID;
        }
        inviteCall.state = CommonCall.CALL_STATE_INCOMING;

        startTime = System.currentTimeMillis();
        answerTime = 0;
        isVideoMode = false;
        backupInviteReqP = new InviteReqPack();
        backupInviteReqP.Clone(pack);
    }

    public int UpdateCallListen(String devId){
        boolean phoneMatched = false;
        boolean callMatched = false;
        BackEndCall listenCall;
        Transaction transaction;
        InviteReqPack invitePacket;
        BackEndPhone caller;
        boolean isTransfer = false;

        if(inviteCall.callType==CommonCall.CALL_TYPE_BROADCAST)
            return -1;
        if(calleeNum.compareToIgnoreCase(PhoneParam.CALL_SERVER_ID)!=0)
            return -1;
        if(inviteCall.state!=CommonCall.CALL_STATE_INCOMING)
            return -1;

        String listenAreaId = HandlerMgr.GetForwardAreaId(inviteCall.caller);
        ArrayList<BackEndPhone> listenDevices = HandlerMgr.GetBackEndListenDevices(listenAreaId,inviteCall.callType);
        for(BackEndPhone listenPhone:listenDevices){
            if(devId.compareToIgnoreCase(listenPhone.id)==0){
                phoneMatched = true;
                break;
            }
        }

        if(phoneMatched){
            for(BackEndCall call:listenCallList){
                if(call.callee.compareToIgnoreCase(devId)==0){
                    callMatched = true;
                    break;
                }
            }
            if(!callMatched){
                // build call
                caller = HandlerMgr.GetBackEndPhone(callerNum);
                invitePacket = new InviteReqPack();
                invitePacket.ExchangeCopyData(backupInviteReqP);
                invitePacket.areaId = caller.devInfo.areaId;
                invitePacket.areaName = caller.devInfo.areaName;
                if(caller.devInfo.areaId.compareToIgnoreCase(listenAreaId)!=0){
                    isTransfer = true;
                }
                invitePacket.isTransfer = isTransfer;

                invitePacket.receiver = devId;
                invitePacket.msgID = UniqueIDManager.GetUniqueID(devId, UniqueIDManager.MSG_UNIQUE_ID);
                listenCall = new BackEndCall(devId, invitePacket);
                listenCall.inviteReqMsgId = invitePacket.msgID;
                transaction = new Transaction(devId, invitePacket,Transaction.TRANSCATION_DIRECTION_S2C);
                HandlerMgr.AddBackEndTrans(invitePacket.msgID, transaction);
                listenCallList.add(listenCall);
            }
        }
        return 0;
    }

    InviteReqPack RestoreInviteReqPack(BackEndCall call){
        InviteReqPack  invitePacket= new InviteReqPack();
        BackEndPhone caller = HandlerMgr.GetBackEndPhone(callerNum);
        boolean isTransfer = false;
        String listenAreaId = HandlerMgr.GetForwardAreaId(inviteCall.caller);

        if(caller.devInfo.areaId.compareToIgnoreCase(listenAreaId)!=0){
            isTransfer = true;
        }

        invitePacket.areaId = caller.devInfo.areaId;
        invitePacket.areaName = caller.devInfo.areaName;
        invitePacket.isTransfer = isTransfer;
        return invitePacket;
    }
    
    public boolean RecvSingleEnd(EndReqPack endReqP){
        EndResPack endResP;
        Transaction trans;
        
        endResP = new EndResPack(ProtocolPacket.STATUS_OK,endReqP);
        trans = new Transaction(endReqP.endDevID, endReqP, endResP, Transaction.TRANSCATION_DIRECTION_S2C);
        HandlerMgr.AddBackEndTrans(endResP.msgID, trans);

         for(BackEndCall listenCall:listenCallList){
            if(listenCall.devID.compareToIgnoreCase(endReqP.endDevID)==0){
                listenCallList.remove(listenCall);
                break;
            }
         }
        return true;
    }

    public CallLogMessage CreateCallLog(){
        BackEndPhone caller;
        BackEndPhone callee;
        BackEndPhone answer;
        BackEndPhone ender;
        
        CallLogMessage log = new CallLogMessage();

        caller = HandlerMgr.GetBackEndPhone(callerNum);
        callee = HandlerMgr.GetBackEndPhone(calleeNum);
        answer = HandlerMgr.GetBackEndPhone(answerNum);
        ender = HandlerMgr.GetBackEndPhone(enderNum);

        log.callType = inviteCall.callType;
        log.callId = inviteCall.callID;
        log.areaId = inviteAreaId;

        log.callDirection = inviteCall.direct;

        log.callerNum= callerNum;
        if(caller!=null){
            log.callerName = caller.devInfo.deviceName;
            log.callerType = caller.type;
        }else{
            if(callerNum.compareToIgnoreCase(PhoneParam.CALL_SERVER_ID)==0){
                log.callerType = UserInterface.CALL_SERVER_DEVICE;
            }
        }

        log.calleeNum = calleeNum;
        if(callee!=null){
            log.calleeName = callee.devInfo.deviceName;
            log.calleeType = callee.type;
        }else{
            if(calleeNum.compareToIgnoreCase(PhoneParam.CALL_SERVER_ID)==0){
                log.calleeType = UserInterface.CALL_SERVER_DEVICE;
            }
        }

        log.answerNum = answerNum;
        if(answer!=null){
            log.answerName= answer.devInfo.deviceName;
            log.answerType = answer.type;
        }else{
            if(answerNum.compareToIgnoreCase(PhoneParam.CALL_SERVER_ID)==0){
                log.answerType = UserInterface.CALL_SERVER_DEVICE;
            }
        }

        log.enderNum= enderNum;
        if(ender!=null){
            log.enderName = ender.devInfo.deviceName;
            log.enderType = ender.type;
        }else{
            if(enderNum.compareToIgnoreCase(PhoneParam.CALL_SERVER_ID)==0){
                log.enderType = UserInterface.CALL_SERVER_DEVICE;
            }
        }

        if(!answerNum.isEmpty()){
            log.answerMode = UserInterface.CALL_ANSWER_MODE_STOP;
        }else if(callerNum.compareToIgnoreCase(enderNum)==0){
            log.answerMode = UserInterface.CALL_ANSWER_MODE_ANSWER;
        }else{
            log.answerMode = UserInterface.CALL_ANSWER_MODE_HANDLE;
        }

        log.startTime = startTime;
        log.answerTime = answerTime;
        log.endTime = System.currentTimeMillis();
        return log;
    }

    public boolean EndCall(EndReqPack endReqP){
        EndReqPack endReqForwardP;
        EndResPack endResP;
        Transaction trans;

        endResP = new EndResPack(ProtocolPacket.STATUS_OK,endReqP);
        enderNum = endReqP.endDevID;

        if(inviteCall.caller.compareToIgnoreCase(endReqP.sender)==0) {
            trans = new Transaction(inviteCall.caller, endReqP, endResP, Transaction.TRANSCATION_DIRECTION_S2C);
            HandlerMgr.AddBackEndTrans(endResP.msgID, trans);
        }else {
            endReqForwardP = new EndReqPack(endReqP,inviteCall.caller);
            trans = new Transaction(inviteCall.caller,endReqForwardP,Transaction.TRANSCATION_DIRECTION_S2C);
            HandlerMgr.AddBackEndTrans(endReqForwardP.msgID, trans);
        }

        if(inviteCall.state==CommonCall.CALL_STATE_CONNECTED){
            if(inviteCall.answer.compareToIgnoreCase(endReqP.sender)==0){
                trans = new Transaction(inviteCall.answer, endReqP, endResP, Transaction.TRANSCATION_DIRECTION_S2C);
                HandlerMgr.AddBackEndTrans(endResP.msgID, trans);
            }else {
                endReqForwardP = new EndReqPack(endReqP, inviteCall.answer);
                trans = new Transaction(inviteCall.answer, endReqForwardP, Transaction.TRANSCATION_DIRECTION_S2C);
                HandlerMgr.AddBackEndTrans(endReqForwardP.msgID, trans);
            }
        }else {
            if (inviteCall.callee.compareToIgnoreCase(PhoneParam.CALL_SERVER_ID) != 0) {
                if (inviteCall.callee.compareToIgnoreCase(endReqP.sender) == 0) {
                    trans = new Transaction(inviteCall.callee, endReqP, endResP, Transaction.TRANSCATION_DIRECTION_S2C);
                    HandlerMgr.AddBackEndTrans(endResP.msgID, trans);
                } else {
                    endReqForwardP = new EndReqPack(endReqP, inviteCall.callee);
                    trans = new Transaction(inviteCall.callee, endReqForwardP, Transaction.TRANSCATION_DIRECTION_S2C);
                    HandlerMgr.AddBackEndTrans(endReqForwardP.msgID, trans);
                }
            }
        }

        if(!inviteCall.inviteReqMsgId.isEmpty())
            HandlerMgr.RemoveBackEndTrans(inviteCall.inviteReqMsgId);

        for(CommonCall listenCall:listenCallList){
            if(listenCall.devID.compareToIgnoreCase(endReqP.sender)==0){
                trans = new Transaction(listenCall.devID,endReqP, endResP,Transaction.TRANSCATION_DIRECTION_S2C);
                HandlerMgr.AddBackEndTrans(endResP.msgID, trans);
            }else{
                endReqForwardP = new EndReqPack(endReqP,listenCall.devID);
                trans = new Transaction(listenCall.devID,endReqForwardP,Transaction.TRANSCATION_DIRECTION_S2C);
                HandlerMgr.AddBackEndTrans(endReqForwardP.msgID, trans);
            }
            if(!listenCall.inviteReqMsgId.isEmpty()){
                HandlerMgr.RemoveBackEndTrans(listenCall.inviteReqMsgId);
            }
        }

        listenCallList.clear();
        inviteCall.state = CommonCall.CALL_STATE_DISCONNECTED;
        return true;
    }

    public void UpdateCall(UpdateReqPack updateReqP){
        int status = ProtocolPacket.STATUS_NOTFOUND;
        String updateDevId = updateReqP.devId;
        Transaction trans;

        if(updateDevId.compareToIgnoreCase(inviteCall.caller)==0) {
            status = ProtocolPacket.STATUS_OK;
            inviteCall.callerWaitUpdateCount = 0;
        }
        if(updateDevId.compareToIgnoreCase(inviteCall.callee)==0) {
            status = ProtocolPacket.STATUS_OK;
            inviteCall.calleeWaitUpdateCount = 0;
        }
        if(updateDevId.compareToIgnoreCase(inviteCall.answer)==0) {
            status = ProtocolPacket.STATUS_OK;
            inviteCall.answerWaitUpdateCount = 0;
        }

        for(BackEndCall call:listenCallList){
            if(updateDevId.compareToIgnoreCase(call.devID)==0){
                status = ProtocolPacket.STATUS_OK;
                call.calleeWaitUpdateCount = 0;
                break;
            }
        }

        UpdateResPack updateResP = new UpdateResPack(status,updateReqP);
        trans = new Transaction(updateReqP.devId,updateReqP,updateResP,Transaction.TRANSCATION_DIRECTION_S2C);
        HandlerMgr.AddBackEndTrans(updateReqP.msgID, trans);
    }

    public void AnswerBroadCall(AnswerReqPack packet){
        AnswerReqPack answerForwareP;
        Transaction trans;
        AnswerResPack answerResP;

        if(packet.answerer.compareToIgnoreCase(PhoneParam.CALL_SERVER_ID)==0)
            inviteCall.answer = packet.answerer;

        if(packet.answerer.compareToIgnoreCase(PhoneParam.CALL_SERVER_ID)==0){
            inviteCall.state = CommonCall.CALL_STATE_CONNECTED;
            answerForwareP = new AnswerReqPack(packet,inviteCall.caller);
            trans = new Transaction(inviteCall.caller, answerForwareP,Transaction.TRANSCATION_DIRECTION_S2C);
            HandlerMgr.AddBackEndTrans(answerForwareP.msgID, trans);
        }else{
            answerResP = new AnswerResPack(ProtocolPacket.STATUS_OK,packet);
            trans = new Transaction(packet.answerer, packet, answerResP, Transaction.TRANSCATION_DIRECTION_S2C);
            HandlerMgr.AddBackEndTrans(answerResP.msgID, trans);
            if(PhoneParam.broadcallCastMode==PhoneParam.BROADCALL_USE_UNICAST){
                for(BackEndCall call:listenCallList){
                    if(call.devID.compareToIgnoreCase(packet.answerer)==0){
                        call.remoteRtpPort = packet.answererRtpPort;
                        call.remoteRtpAddress = packet.answererRtpIP;
                    }
                }
            }        
        }
        answerTime = System.currentTimeMillis();
    }

    public void AnswerCall(AnswerReqPack packet){
        AnswerResPack answerResP;
        AnswerReqPack answerForwareP;
        EndReqPack endP;
        Transaction trans;

        answerResP = new AnswerResPack(ProtocolPacket.STATUS_OK,packet);
        trans = new Transaction(packet.answerer, packet, answerResP, Transaction.TRANSCATION_DIRECTION_S2C);
        HandlerMgr.AddBackEndTrans(answerResP.msgID, trans);

        answerForwareP = new AnswerReqPack(packet,inviteCall.caller);
        trans = new Transaction(inviteCall.caller, answerForwareP,Transaction.TRANSCATION_DIRECTION_S2C);
        HandlerMgr.AddBackEndTrans(answerForwareP.msgID, trans);

        inviteCall.answer = packet.answerer;
        answerNum = packet.answerer;

        if(inviteCall.callee.compareToIgnoreCase(PhoneParam.CALL_SERVER_ID)!=0) {
            if (inviteCall.callee.compareToIgnoreCase(packet.answerer) != 0) {
                endP = new EndReqPack();

                endP.sender = PhoneParam.CALL_SERVER_ID;
                endP.receiver = inviteCall.callee;
                endP.msgID = UniqueIDManager.GetUniqueID(endP.sender, UniqueIDManager.MSG_UNIQUE_ID);

                endP.endDevID = packet.answerer;
                endP.callID = inviteCall.callID;
                endP.endReason = EndReqPack.END_FOR_OTHER_ANSWER;

                trans = new Transaction(inviteCall.callee, endP, Transaction.TRANSCATION_DIRECTION_S2C);
                HandlerMgr.AddBackEndTrans(endP.msgID, trans);

            }
        }

        // end call for listeners;
        for(CommonCall listenCall:listenCallList){
            if(listenCall.devID.compareToIgnoreCase(packet.answerer)!=0) {
                endP = new EndReqPack();

                endP.sender = PhoneParam.CALL_SERVER_ID;
                endP.receiver = listenCall.devID;
                endP.msgID = UniqueIDManager.GetUniqueID(listenCall.devID, UniqueIDManager.MSG_UNIQUE_ID);

                endP.endDevID = packet.answerer;
                endP.callID = listenCall.callID;
                endP.endReason = EndReqPack.END_FOR_OTHER_ANSWER;

                trans = new Transaction(listenCall.devID, endP, Transaction.TRANSCATION_DIRECTION_S2C);
                HandlerMgr.AddBackEndTrans(endP.msgID, trans);
            }
        }

        listenCallList.clear();
        inviteCall.state = CommonCall.CALL_STATE_CONNECTED;
        answerTime = System.currentTimeMillis();
    }

    public void StartVideo(StartVideoReqPack req){
        int status = ProtocolPacket.STATUS_OK;
        Transaction trans;
        String startVideoDevId;
        String forwareDevId;
        StartVideoReqPack forwardReq;

        if(isVideoMode){
            status = ProtocolPacket.STATUS_CONFILICT;
        }
        StartVideoResPack resP = new StartVideoResPack(status,req);
        startVideoDevId = req.startVideoDevId;

        trans = new Transaction(req.startVideoDevId, req, resP,Transaction.TRANSCATION_DIRECTION_S2C);
        HandlerMgr.AddBackEndTrans(req.msgID, trans);

        if(callerNum.compareToIgnoreCase(startVideoDevId)==0){
            forwareDevId = answerNum;
        }else{
            forwareDevId = callerNum;
        }
        forwardReq = new StartVideoReqPack(req,forwareDevId);
        trans = new Transaction(forwareDevId,forwardReq,Transaction.TRANSCATION_DIRECTION_S2C);
        HandlerMgr.AddBackEndTrans(forwardReq.msgID,trans);
    }
    public void StopVideo(StopVideoReqPack req){
        int status = ProtocolPacket.STATUS_OK;
        Transaction trans;
        String stopVideoDevId;
        String forwareDevId;
        StopVideoReqPack forwardReq;

        if(isVideoMode){
            status = ProtocolPacket.STATUS_CONFILICT;
        }
        StopVideoResPack resP = new StopVideoResPack(status,req);
        stopVideoDevId = req.stopVideoDevId;

        trans = new Transaction(req.stopVideoDevId, req, resP,Transaction.TRANSCATION_DIRECTION_S2C);
        HandlerMgr.AddBackEndTrans(req.msgID, trans);

        if(callerNum.compareToIgnoreCase(stopVideoDevId)==0){
            forwareDevId = answerNum;
        }else{
            forwareDevId = callerNum;
        }
        forwardReq = new StopVideoReqPack(req,forwareDevId);
        trans = new Transaction(forwareDevId,forwardReq,Transaction.TRANSCATION_DIRECTION_S2C);
        HandlerMgr.AddBackEndTrans(forwardReq.msgID,trans);
    }
    

    void AnswerVideo(AnswerVideoReqPack req){
        int status = ProtocolPacket.STATUS_OK;
        Transaction trans;
        String answerVideoDevId;
        String forwareDevId;
        AnswerVideoReqPack forwardReq;

        if(isVideoMode){
            status = ProtocolPacket.STATUS_CONFILICT;
        }
        AnswerVideoResPack resP = new AnswerVideoResPack(status,req);
        answerVideoDevId = req.answerDevId;

        trans = new Transaction(req.answerDevId, req, resP,Transaction.TRANSCATION_DIRECTION_S2C);
        HandlerMgr.AddBackEndTrans(req.msgID, trans);

        if(callerNum.compareToIgnoreCase(answerVideoDevId)==0){
            forwareDevId = answerNum;
        }else{
            forwareDevId = callerNum;
        }
        forwardReq = new AnswerVideoReqPack(req,forwareDevId);
        trans = new Transaction(forwareDevId,forwardReq,Transaction.TRANSCATION_DIRECTION_S2C);
        HandlerMgr.AddBackEndTrans(forwardReq.msgID,trans);
    }

    private void StopCall(int reason){
        EndReqPack endPack = new EndReqPack(inviteCall.callID);
        endPack.endReason = reason;
        HandlerMgr.PostBackEndPhoneMsg(BackEndPhoneManager.MSG_NEW_PACKET,endPack);
    }

    private void AutoAnswerCall(){
        HandlerMgr.PostBackEndPhoneMsg(BackEndPhoneManager.MSG_NEW_PACKET,BuildAutoAnswerPacket());
    }


    private AnswerReqPack BuildAutoAnswerPacket(){
        AnswerReqPack answerReqPack = new AnswerReqPack();

        answerReqPack.type = ProtocolPacket.ANSWER_REQ;
        answerReqPack.sender = PhoneParam.CALL_SERVER_ID;
        answerReqPack.receiver = inviteCall.caller;
        answerReqPack.msgID = UniqueIDManager.GetUniqueID(PhoneParam.CALL_SERVER_ID,UniqueIDManager.MSG_UNIQUE_ID);

        answerReqPack.answerer = PhoneParam.CALL_SERVER_ID;
        answerReqPack.callID = inviteCall.callID;

        if(inviteCall.callType==CommonCall.CALL_TYPE_BROADCAST){
            answerReqPack.answererRtpPort = PhoneParam.BROADCAST_CALL_RTP_PORT;
            if(PhoneParam.broadcallCastMode==PhoneParam.BROADCALL_USE_BROADCAST)
                answerReqPack.answererRtpIP = PhoneParam.BROAD_ADDRESS;
            else
                answerReqPack.answererRtpIP = PhoneParam.GetLocalAddress();
                answerReqPack.answererRtpPort = curBroadCastPort;
        }else{
            answerReqPack.answererRtpPort = PhoneParam.ANSWER_CALL_RTP_PORT;
            answerReqPack.answererRtpIP = PhoneParam.GetLocalAddress();
        }

        answerReqPack.codec = PhoneParam.callRtpCodec;
        answerReqPack.pTime = PhoneParam.callRtpPTime;
        answerReqPack.sample = PhoneParam.callRtpDataRate;

        return answerReqPack;
    }

    public void ProcessSecondTick(){
        inviteCall.callerWaitUpdateCount++;

        if(inviteCall.callee.compareToIgnoreCase(PhoneParam.CALL_SERVER_ID)!=0){
            inviteCall.calleeWaitUpdateCount++;
        }

        if(inviteCall.callType!=CommonCall.CALL_TYPE_BROADCAST&&!inviteCall.answer.isEmpty()){
            inviteCall.answerWaitUpdateCount++;
        }

        if(inviteCall.callerWaitUpdateCount>CommonCall.UPDATE_INTERVAL*2+5){
            LogWork.Print(LogWork.BACKEND_CALL_MODULE,LogWork.LOG_ERROR,"BackEnd End Call %s for Miss Update of Caller DEV %s ",inviteCall.callID,inviteCall.caller);
            StopCall(EndReqPack.END_FOR_CALLER_UPDATE_FAIL);
            return;
        }
        if(inviteCall.calleeWaitUpdateCount>CommonCall.UPDATE_INTERVAL*2+5){
            LogWork.Print(LogWork.BACKEND_CALL_MODULE,LogWork.LOG_ERROR,"BackEnd End Call %s for Miss Update of Callee DEV %s ",inviteCall.callID,inviteCall.callee);
            StopCall(EndReqPack.END_FOR_CALLEE_UPDATE_FAIL);
            return;
        }
        if(inviteCall.answerWaitUpdateCount>CommonCall.UPDATE_INTERVAL*2+5){
            LogWork.Print(LogWork.BACKEND_CALL_MODULE,LogWork.LOG_ERROR,"BackEnd End Call %s for Miss Update of Answer DEV %s ",inviteCall.callID,inviteCall.answer);
            StopCall(EndReqPack.END_FOR_ANSWER_UPDATE_FAIL);
            return;
        }

        for(int iTmp=listenCallList.size()-1;iTmp>=0;iTmp--){
            BackEndCall call = listenCallList.get(iTmp);
            call.calleeWaitUpdateCount++;
            if(call.calleeWaitUpdateCount>CommonCall.UPDATE_INTERVAL*2+5){
                 LogWork.Print(LogWork.BACKEND_CALL_MODULE,LogWork.LOG_ERROR,"BackEnd Remove Dev %s From Call %s for Update TimeOver",call.devID,inviteCall.callID);               
                listenCallList.remove(iTmp);
            }
        }

        if(listenCallList.size()<=0&&calleeNum.compareToIgnoreCase(PhoneParam.CALL_SERVER_ID)==0){
            if(inviteCall.state==CommonCall.CALL_STATE_INCOMING){
                LogWork.Print(LogWork.BACKEND_CALL_MODULE,LogWork.LOG_ERROR,"BackEnd End Call %s for no Listener in This Call ",inviteCall.callID);
                StopCall(EndReqPack.END_FOR_NO_LISTEN);
                return;
            }
        }

        if(inviteCall.callType==CommonCall.CALL_TYPE_BROADCAST){
            if(autoAnswerTime>=0){
                if(autoAnswerTick<=autoAnswerTime){
                    autoAnswerTick++;
                    if(autoAnswerTick>autoAnswerTime){
                        LogWork.Print(LogWork.BACKEND_CALL_MODULE,LogWork.LOG_DEBUG,"BackEnd Auto Answer Call %s After %s Seconds",inviteCall.callID,autoAnswerTime);
                        AutoAnswerCall();
                    }
                }
            }            
        }
    }

    public void InviteTimeOver(String devid){

        if(devid.compareToIgnoreCase(inviteCall.callee)==0){
            LogWork.Print(LogWork.BACKEND_CALL_MODULE,LogWork.LOG_DEBUG,"BackEnd Send Call End  When Invite Callee %s TimeOver for Call %s",devid,inviteCall.callID);
            StopCall(EndReqPack.END_FOR_INVITE_TIMEOVER);
        }
    }

    public void UpdateStatus(InviteResPack packet){
        if(packet.type == ProtocolPacket.CALL_RES ){
            if(packet.status == ProtocolPacket.STATUS_OK){
                for(BackEndCall listenCall:listenCallList){
                    if(listenCall.devID.compareToIgnoreCase(packet.sender)==0){
                        listenCall.state = CommonCall.CALL_STATE_RINGING;
//                        LogWork.Print(LogWork.BACKEND_CALL_MODULE, LogWork.LOG_ERROR, "BackEnd Set dev %s  for Call %s Ring State", packet.sender,inviteCall.callID);
                        break;
                    }
                }
            }else{
                if(inviteCall.type==CommonCall.CALL_TYPE_NORMAL||inviteCall.type == CommonCall.CALL_TYPE_ASSIST) {
                    if (packet.sender.compareToIgnoreCase(inviteCall.callee) == 0) {
                        LogWork.Print(LogWork.BACKEND_CALL_MODULE, LogWork.LOG_ERROR, "BackEnd End Call %s when Recv Call Res with %s from %s", inviteCall.callID, ProtocolPacket.GetResString(packet.status), inviteCall.callee);
                        StopCall(EndReqPack.END_FOR_CALLEE_REJECT);
                    }else{
                        for(BackEndCall listenCall:listenCallList){
                            if(listenCall.devID.compareToIgnoreCase(packet.sender)==0){
                                LogWork.Print(LogWork.BACKEND_CALL_MODULE, LogWork.LOG_ERROR, "BackEnd Remove dev %s From Listen List for Call %s When Recv %s", packet.sender,inviteCall.callID, ProtocolPacket.GetResString(packet.status));
                                listenCallList.remove(listenCall);
                                break;
                            }
                        }
                    }
                }else if(inviteCall.type==CommonCall.CALL_TYPE_BROADCAST){
                    for(BackEndCall listenCall:listenCallList){
                        if(listenCall.devID.compareToIgnoreCase(packet.sender)==0){
                            LogWork.Print(LogWork.BACKEND_CALL_MODULE, LogWork.LOG_ERROR, "BackEnd Remove dev %s From Listen List for Call %s When Recv %s", packet.sender,inviteCall.callID, ProtocolPacket.GetResString(packet.status));
                            listenCallList.remove(listenCall);
                            break;
                        }
                    }
                }
            }
        }
    }

    public boolean CheckAnswerEnable(BackEndPhone phone,BackEndPhone caller,String callid){
        boolean result = true;

        if(phone==null)
            return false;
        if(!phone.isReg)
            return false;

        if(callid.compareToIgnoreCase(inviteCall.callID)!=0){
            if(inviteCall.state==CommonCall.CALL_STATE_CONNECTED){
                if(inviteCall.caller.compareToIgnoreCase(phone.id)==0)
                    result=false;
                else if(inviteCall.answer.compareToIgnoreCase(phone.id)==0)
                    result = false;
                if(caller!=null) {
                    if (inviteCall.caller.compareToIgnoreCase(caller.id) == 0)
                        result = false;
                    else if (inviteCall.answer.compareToIgnoreCase(caller.id) == 0)
                        result = false;
                }
            }
        }else{
            if(inviteCall.state==CommonCall.CALL_STATE_CONNECTED){
                result = false;
            }
        }

        return result;
    }

    public boolean CheckInviteEnable(BackEndPhone phone){
        boolean result= true;
        if(!phone.isReg)
            result = false;
        else {
            switch (phone.type) {
                case BackEndPhone.BED_CALL_DEVICE:
                case BackEndPhone.DOOR_CALL_DEVICE:
                case BackEndPhone.NURSE_CALL_DEVICE:
                    if (inviteCall.caller.compareToIgnoreCase(phone.id) == 0){
                        LogWork.Print(LogWork.BACKEND_CALL_MODULE,LogWork.LOG_ERROR,"Dev %s is Caller In Call %s",phone.id,inviteCall.callID);
                        result = false;
                    }else if (inviteCall.callee.compareToIgnoreCase(phone.id) == 0){
                        LogWork.Print(LogWork.BACKEND_CALL_MODULE,LogWork.LOG_ERROR,"Dev %s is Callee In Call %s",phone.id,inviteCall.callID);
                        result = false;
                    }else {
                        for (CommonCall listenCall : listenCallList) {
                            if (listenCall.devID.compareToIgnoreCase(phone.id) == 0) {
                                LogWork.Print(LogWork.BACKEND_CALL_MODULE,LogWork.LOG_ERROR,"Dev %s is Listener In Call %s",phone.id,inviteCall.callID);
                                result = false;
                                break;
                            }
                        }
                    }
                    break;
                case BackEndPhone.EMER_CALL_DEVICE:
                    if (inviteCall.caller.compareToIgnoreCase(phone.id) == 0){
                        LogWork.Print(LogWork.BACKEND_CALL_MODULE,LogWork.LOG_ERROR,"EMER Dev %s is Caller In Call %s",phone.id,inviteCall.callID);
                        result = false;
                    }
                    break;
            }

            if(inviteCall.callType>=CommonCall.ALERT_TYPE_BEGIN&&inviteCall.callType<=CommonCall.ALERT_TYPE_ENDED)
                result = true;
        }
        return result;
    }

    public boolean CheckForwardEnable(BackEndPhone phone,int callType){
        boolean result = true;

        if(!phone.isReg)
            result = false;
        else{
            if(callType>=CommonCall.ALERT_TYPE_BEGIN&&callType<=CommonCall.ALERT_TYPE_ENDED){
                switch(phone.type){
                    case BackEndPhone.BED_CALL_DEVICE:
                        if(!phone.enableListen)
                            result = false;
                        break;
                    case BackEndPhone.EMER_CALL_DEVICE:
                        result = false;
                        break;
                    case BackEndPhone.CORRIDOR_CALL_DEVICE:
                    case BackEndPhone.DOOR_CALL_DEVICE:
                    case BackEndPhone.NURSE_CALL_DEVICE:
                    case BackEndPhone.TV_CALL_DEVICE:
                        break;
                }
            }else{
                switch(callType){
                    case CommonCall.CALL_TYPE_NORMAL:
                    case CommonCall.CALL_TYPE_EMERGENCY:
                    case CommonCall.CALL_TYPE_ASSIST:
                        switch(phone.type){
                            case BackEndPhone.BED_CALL_DEVICE:
                                if(!phone.enableListen)
                                    result = false;
                                break;
                            case BackEndPhone.EMER_CALL_DEVICE:
                                result = false;
                                break;
                            case BackEndPhone.CORRIDOR_CALL_DEVICE:
                            case BackEndPhone.DOOR_CALL_DEVICE:
                            case BackEndPhone.NURSE_CALL_DEVICE:
                            case BackEndPhone.TV_CALL_DEVICE:
                                break;
                        }
                        break;
                    case CommonCall.CALL_TYPE_BROADCAST:
                        switch(phone.type){
                            case BackEndPhone.BED_CALL_DEVICE:
                            case BackEndPhone.CORRIDOR_CALL_DEVICE:
                            case BackEndPhone.DOOR_CALL_DEVICE:
                                if (inviteCall.caller.compareToIgnoreCase(phone.id) == 0)
                                    result = false;
                                else if (inviteCall.callee.compareToIgnoreCase(phone.id) == 0)
                                    result = false;
                                else if(inviteCall.answer.compareToIgnoreCase(phone.id) == 0)
                                    result = false;
                                else {
                                    for (CommonCall listenCall : listenCallList) {
                                        if (listenCall.devID.compareToIgnoreCase(phone.id) == 0) {
                                            if(listenCall.state==CommonCall.CALL_STATE_CONNECTED){
                                                result = false;
                                                break;
                                            }
                                        }
                                    }
                                }
                                break;
                            case BackEndPhone.NURSE_CALL_DEVICE:
                            case BackEndPhone.TV_CALL_DEVICE:
                            case BackEndPhone.EMER_CALL_DEVICE:
                                result = false;
                                break;
                        }
                        break;
                }
            }
        }

        return result;
    }

    public boolean CheckBroadCastEnabled(BackEndPhone phone){
        boolean result = true;

        if(!phone.isReg)
            result = false;
        else {
            if (inviteCall.caller.compareToIgnoreCase(phone.id) == 0)
                result = false;
            else if (inviteCall.callee.compareToIgnoreCase(phone.id) == 0)
                result = false;
            else if (inviteCall.answer.compareToIgnoreCase(phone.id) == 0)
                result = false;
            else {
                for (CommonCall listenCall : listenCallList) {
                    if (listenCall.devID.compareToIgnoreCase(phone.id) == 0) {
                        result = false;
                        break;
                    }
                }
            }
        }

        return result;
    }


    public boolean CheckListenEnabled(BackEndPhone phone){
        boolean result = true;

        if(!phone.isReg)
            result = false;
        else {
            if (inviteCall.state==CommonCall.CALL_STATE_INCOMING||inviteCall.state==CommonCall.CALL_STATE_DIALING||inviteCall.state==CommonCall.CALL_STATE_RINGING)
                if(inviteCall.caller.compareToIgnoreCase(phone.id) == 0)
                    result = false;
        }

        return result;
    }

    public boolean CheckInvitedEnable(BackEndPhone phone) {
        boolean result = true;

        if(!phone.isReg)
            result = false;
        else{
            switch (phone.type) {
                case BackEndPhone.BED_CALL_DEVICE:
                    if (inviteCall.caller.compareToIgnoreCase(phone.id) == 0)
                        result = false;
                    else if (inviteCall.callee.compareToIgnoreCase(phone.id) == 0)
                        result = false;
                    else {
                        for (CommonCall listenCall : listenCallList) {
                            if (listenCall.devID.compareToIgnoreCase(phone.id) == 0) {
                                result = false;
                                break;
                            }
                        }
                    }
                    break;
                case BackEndPhone.NURSE_CALL_DEVICE:
                    break;
                case BackEndPhone.CORRIDOR_CALL_DEVICE:
                case BackEndPhone.DOOR_CALL_DEVICE:
                case BackEndPhone.TV_CALL_DEVICE:
                case BackEndPhone.EMER_CALL_DEVICE:
                    result = false;
                    break;
            }
        }

        return result;
    }

    public boolean CancelListen(String devId){
        boolean result = false;
        if(inviteCall.callType==CommonCall.CALL_TYPE_BROADCAST)
            return result;
        for(BackEndCall call:listenCallList){
            if(devId.compareToIgnoreCase(call.devID)==0){
                listenCallList.remove(call);

                CancelReqPack cancelReqP = new CancelReqPack(inviteCall.callID,PhoneParam.CALL_SERVER_ID,devId);
                cancelReqP.msgID = UniqueIDManager.GetUniqueID(devId,UniqueIDManager.MSG_UNIQUE_ID);

                Transaction transaction = new Transaction(devId,cancelReqP, Transaction.TRANSCATION_DIRECTION_S2C);
                HandlerMgr.AddBackEndTrans(cancelReqP.msgID,transaction);
                result = true;
                LogWork.Print(LogWork.BACKEND_CALL_MODULE,LogWork.LOG_DEBUG,"Cancel Dev %s in Call %s",devId,inviteCall.callID);
                break;
            }
        }

        if(answerNum.compareToIgnoreCase(devId)==0){
            StopCall(EndReqPack.END_FOR_SERVER_CANCEL);
        }
        return result;
    }

    public void RecvCancel(CancelReqPack cancelReqP){
        Transaction trans=null;
        
        for(BackEndCall call:listenCallList){
            if(cancelReqP.cancelDevID.compareToIgnoreCase(call.devID)==0){
                LogWork.Print(LogWork.BACKEND_CALL_MODULE,LogWork.LOG_DEBUG,"Recv Cancel From Dev %s in Call %s, and Remove it from Call",cancelReqP.cancelDevID,cancelReqP.callID);

                listenCallList.remove(call);
                CancelResPack cancelResP = new CancelResPack(ProtocolPacket.STATUS_OK,cancelReqP);
                trans = new Transaction(cancelReqP.sender,cancelReqP,cancelResP,Transaction.TRANSCATION_DIRECTION_S2C);
                HandlerMgr.AddBackEndTrans(cancelReqP.msgID,trans);
                break;
            }
        }

        if(trans==null){
            LogWork.Print(LogWork.BACKEND_CALL_MODULE,LogWork.LOG_DEBUG,"Recv Cancel From Dev %s in Call %s, But couldn't find this device",cancelReqP.cancelDevID,cancelReqP.callID);
            CancelResPack cancelResP = new CancelResPack(ProtocolPacket.STATUS_NOTFOUND,cancelReqP);
            trans = new Transaction(cancelReqP.sender,cancelReqP,cancelResP,Transaction.TRANSCATION_DIRECTION_S2C);
            HandlerMgr.AddBackEndTrans(cancelReqP.msgID,trans);
        }
        
    }
        

    public void Release(){
        if(listenCallList!=null)
            listenCallList.clear();
    }

    public byte[] MakeSnap(){
        byte[] data = null;

        JSONObject json = new JSONObject();

        try {
            json.put(SystemSnap.SNAP_CMD_TYPE_NAME, SystemSnap.SNAP_BACKEND_CALL_RES);
            json.put(SystemSnap.SNAP_CALLER_NAME,inviteCall.caller);
            json.put(SystemSnap.SNAP_CALLEE_NAME,inviteCall.callee);
            json.put(SystemSnap.SNAP_ANSWERER_NAME,inviteCall.answer);
            json.put(SystemSnap.SNAP_CALLID_NAME,inviteCall.callID);
            json.put(SystemSnap.SNAP_CALLSTATUS_NAME,inviteCall.state);

            JSONArray listener = new JSONArray();
            for(BackEndCall call:listenCallList){
                JSONObject listenCall = new JSONObject();
                listenCall.put(SystemSnap.SNAP_CALLSTATUS_NAME,call.state);
                listenCall.put(SystemSnap.SNAP_LISTENER_NAME,call.devID);
                listener.add(listenCall);
            }
            json.put(SystemSnap.SNAP_LISTENS_NAME,listener);
            data = json.toString().getBytes();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        json.clear();

        return data;
    }

}

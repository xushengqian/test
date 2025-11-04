package com.originate.call.client.freeswitch;

import java.util.HashMap;
import java.util.Map;

/**
 * Originate错误处理结果
 */
public class OriginateErrorResult {
    
    private String jobUuid;
    private String callUuid;
    private OriginateErrorType errorType;
    private String errorMessage;
    private String calledNumber;
    private String gateway;
    private boolean retryable;
    private String suggestedAction;
    private Map<String, String> details = new HashMap<>();
    
    public String getJobUuid() {
        return jobUuid;
    }
    
    public void setJobUuid(String jobUuid) {
        this.jobUuid = jobUuid;
    }
    
    public String getCallUuid() {
        return callUuid;
    }
    
    public void setCallUuid(String callUuid) {
        this.callUuid = callUuid;
    }
    
    public OriginateErrorType getErrorType() {
        return errorType;
    }
    
    public void setErrorType(OriginateErrorType errorType) {
        this.errorType = errorType;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getCalledNumber() {
        return calledNumber;
    }
    
    public void setCalledNumber(String calledNumber) {
        this.calledNumber = calledNumber;
    }
    
    public String getGateway() {
        return gateway;
    }
    
    public void setGateway(String gateway) {
        this.gateway = gateway;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
    
    public void setRetryable(boolean retryable) {
        this.retryable = retryable;
    }
    
    public String getSuggestedAction() {
        return suggestedAction;
    }
    
    public void setSuggestedAction(String suggestedAction) {
        this.suggestedAction = suggestedAction;
    }
    
    public Map<String, String> getDetails() {
        return details;
    }
    
    public void setDetails(Map<String, String> details) {
        this.details = details;
    }
    
    public void addDetail(String key, String value) {
        this.details.put(key, value);
    }
    
    @Override
    public String toString() {
        return "OriginateErrorResult{" +
                "jobUuid='" + jobUuid + '\'' +
                ", callUuid='" + callUuid + '\'' +
                ", errorType=" + errorType +
                ", errorMessage='" + errorMessage + '\'' +
                ", calledNumber='" + calledNumber + '\'' +
                ", gateway='" + gateway + '\'' +
                ", retryable=" + retryable +
                ", suggestedAction='" + suggestedAction + '\'' +
                ", details=" + details +
                '}';
    }
}

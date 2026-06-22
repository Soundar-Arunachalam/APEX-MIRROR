package com.hpe.upi.dashboard.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionEvent {
    private String txnId,rrn,payerVpa,payeeVpa,payerBank,payeeBank,amount,status,message,timestamp,dbSource,reversalReason;
    private boolean reversalInitiated;
    public String getTxnId(){return txnId;} public void setTxnId(String v){txnId=v;}
    public String getRrn(){return rrn;} public void setRrn(String v){rrn=v;}
    public String getPayerVpa(){return payerVpa;} public void setPayerVpa(String v){payerVpa=v;}
    public String getPayeeVpa(){return payeeVpa;} public void setPayeeVpa(String v){payeeVpa=v;}
    public String getPayerBank(){return payerBank;} public void setPayerBank(String v){payerBank=v;}
    public String getPayeeBank(){return payeeBank;} public void setPayeeBank(String v){payeeBank=v;}
    public String getAmount(){return amount;} public void setAmount(String v){amount=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getMessage(){return message;} public void setMessage(String v){message=v;}
    public String getTimestamp(){return timestamp;} public void setTimestamp(String v){timestamp=v;}
    public String getDbSource(){return dbSource;} public void setDbSource(String v){dbSource=v;}
    public String getReversalReason(){return reversalReason;} public void setReversalReason(String v){reversalReason=v;}
    public boolean isReversalInitiated(){return reversalInitiated;} public void setReversalInitiated(boolean v){reversalInitiated=v;}
}
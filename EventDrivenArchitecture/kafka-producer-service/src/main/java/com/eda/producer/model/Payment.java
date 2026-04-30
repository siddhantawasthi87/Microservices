package com.eda.producer.model;

public class Payment {

    private String paymentId;
    private String customerId;
    private Double totalAmount;
    private String status;

    public Payment() {
    }

    public Payment(String paymentId, String customerId, Double totalAmount, String status) {
        this.paymentId = paymentId;
        this.customerId = customerId;
        this.totalAmount = totalAmount;
        this.status = status;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}


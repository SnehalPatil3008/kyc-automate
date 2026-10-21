package com.kycautomate.decision.exception;
public class DecisionException extends RuntimeException {
 public final int status;
 public DecisionException(int status,String message) { super(message); this.status=status; }
}

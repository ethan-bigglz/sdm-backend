package com.example.sdm.exception;

/**
 * 수령인이 이미 동일한 종류의 상품 마스터를 소유하고 있을 때 발생하는 비즈니스 예외.
 */
public class DuplicateItemOwnershipException extends RuntimeException {
    public DuplicateItemOwnershipException(String message) {
        super(message);
    }
}

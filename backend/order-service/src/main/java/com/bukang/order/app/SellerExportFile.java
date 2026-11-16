package com.bukang.order.app;

// 내려받을 파일 (BUK-228). 이름은 확장자까지, 내용은 UTF-8 BOM이 붙은 CSV 바이트다
public record SellerExportFile(String fileName, byte[] content) {
}

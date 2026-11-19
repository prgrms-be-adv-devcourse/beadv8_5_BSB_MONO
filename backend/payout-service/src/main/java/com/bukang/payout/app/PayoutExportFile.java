package com.bukang.payout.app;

// 내려받을 파일. 이름은 확장자까지, 내용은 UTF-8 BOM이 붙은 CSV 바이트다
public record PayoutExportFile(String fileName, byte[] content) {
}

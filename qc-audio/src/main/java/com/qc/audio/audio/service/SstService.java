package com.qc.audio.audio.service;

public interface SstService {
    String sst(String model,
               String format,
               Integer sampleRate,
               String[] languageHints,
               java.io.File audioFile);
}

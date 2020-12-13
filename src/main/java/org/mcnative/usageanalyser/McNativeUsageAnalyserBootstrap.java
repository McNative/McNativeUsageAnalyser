package org.mcnative.usageanalyser;

public final class McNativeUsageAnalyserBootstrap {

    public static void main(String[] args) {
        McNativeUsageAnalyser analyser = new McNativeUsageAnalyser();
        Runtime.getRuntime().addShutdownHook(new Thread(analyser::onShutdown));
    }
}

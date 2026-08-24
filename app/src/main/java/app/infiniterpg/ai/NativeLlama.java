package app.infiniterpg.ai;

public final class NativeLlama {
    public interface TokenListener { void onToken(String chunk, float tokensPerSecond); }
    private static boolean available;
    static {
        try { System.loadLibrary("infinite_rpg_llama"); available = true; }
        catch (Throwable ignored) { available = false; }
    }
    private NativeLlama() {}
    public static boolean isAvailable() { return available; }
    public static native boolean load(String modelPath, int contextTokens, int threads);
    public static native String complete(String prompt, int maxTokens, float temperature, long seed);
    public static native String completeStreaming(String prompt, int maxTokens, float temperature, long seed, TokenListener listener);
    public static native void cancel();
    public static native void unload();
    public static native boolean isLoaded();
    public static native float lastTokensPerSecond();
    public static native String lastError();
}

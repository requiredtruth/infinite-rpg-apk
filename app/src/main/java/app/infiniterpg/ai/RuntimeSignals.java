package app.infiniterpg.ai;

public final class RuntimeSignals {
    private static volatile long gameBusyUntil;
    private static volatile boolean gameplayActive;
    private RuntimeSignals() {}
    public static void gameBusy(){gameBusyUntil=System.currentTimeMillis()+3000;}
    public static void setGameplayActive(boolean active){gameplayActive=active;if(active)gameBusy();}
    public static boolean isGameplayActive(){return gameplayActive;}
    public static boolean gameIsBusy(){return gameplayActive||System.currentTimeMillis()<gameBusyUntil;}
}

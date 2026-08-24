package app.infiniterpg;

import android.app.Application;
import app.infiniterpg.data.ContentRepository;

public final class InfiniteRpgApp extends Application {
    private static InfiniteRpgApp instance;
    private ContentRepository repository;

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        repository = new ContentRepository(this);
        repository.bootstrap();
    }

    public static InfiniteRpgApp get() { return instance; }
    public ContentRepository repo() { return repository; }
}

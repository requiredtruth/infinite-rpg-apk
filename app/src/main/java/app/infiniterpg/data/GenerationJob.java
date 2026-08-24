package app.infiniterpg.data;

public final class GenerationJob {
    public final long id;
    public final String type;
    public final String prompt;
    public final int priority;
    public final int attempts;

    public GenerationJob(long id, String type, String prompt, int priority, int attempts) {
        this.id = id; this.type = type; this.prompt = prompt; this.priority = priority; this.attempts = attempts;
    }
}

package app.infiniterpg.data;

public final class MobState {
    public final long id;public final String species,spawnKey;public float x,y;public int health;public final long bornAt;public long loveUntil,cooldownUntil;
    public int facing;public float walkPhase;public boolean walking;
    public MobState(long id,String species,float x,float y,int health,long bornAt,long loveUntil,long cooldownUntil,String spawnKey){this.id=id;this.species=species;this.x=x;this.y=y;this.health=health;this.bornAt=bornAt;this.loveUntil=loveUntil;this.cooldownUntil=cooldownUntil;this.spawnKey=spawnKey;}
    public boolean isBaby(long now){return now-bornAt<5*60*1000L;}
}

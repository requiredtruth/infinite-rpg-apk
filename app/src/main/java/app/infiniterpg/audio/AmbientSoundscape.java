package app.infiniterpg.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import app.infiniterpg.data.ContentRepository;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Lightweight procedural wind, birds, insects, and distant woodland life. */
public final class AmbientSoundscape {
    private static final int RATE=16000;private final ContentRepository repo;private final AtomicBoolean running=new AtomicBoolean();private Thread thread;
    public AmbientSoundscape(ContentRepository repo){this.repo=repo;}
    public void start(){if(running.getAndSet(true))return;thread=new Thread(this::render,"infinite-rpg-ambient");thread.start();}
    public void stop(){running.set(false);if(thread!=null)thread.interrupt();}
    private void render(){android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);AudioTrack track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()).setBufferSizeInBytes(Math.max(min,16384)).setTransferMode(AudioTrack.MODE_STREAM).build();track.play();Random rng=new Random(77114);double wind=0,phase=0;long frame=0;try{while(running.get()){boolean enabled=Boolean.parseBoolean(repo.getState("sounds_enabled","true"));int volume=integer(repo.getState("sounds_volume","58"),58);track.setVolume(enabled?Math.max(0,Math.min(100,volume))/100f*.48f:0);short[] pcm=new short[4096];for(int i=0;i<2048;i++,frame++){wind=wind*.995+(rng.nextDouble()*2-1)*.005;double s=wind*.10;long cycle=frame%(RATE*7L);if(cycle<3500){double t=cycle/(double)RATE;double env=Math.sin(Math.PI*Math.min(1,t/.22))*Math.exp(-t*1.7);phase+=2*Math.PI*(1700+650*Math.sin(t*22))/RATE;s+=Math.sin(phase)*env*.12;}long insect=frame%(RATE*11L);if(insect>RATE*8L&&insect<RATE*8L+1800){double t=(insect-RATE*8L)/(double)RATE;s+=Math.sin(t*2*Math.PI*4200)*Math.sin(t*2*Math.PI*29)*.035;}short value=(short)(Math.max(-.8,Math.min(.8,s))*Short.MAX_VALUE);pcm[i*2]=value;pcm[i*2+1]=(short)(value*.86);}track.write(pcm,0,pcm.length,AudioTrack.WRITE_BLOCKING);}}catch(Exception ignored){}finally{track.pause();track.flush();track.release();}}
    private int integer(String v,int fallback){try{return Integer.parseInt(v);}catch(Exception e){return fallback;}}
}

package app.infiniterpg.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import org.json.JSONArray;
import app.infiniterpg.data.ContentItem;
import app.infiniterpg.data.ContentRepository;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProceduralMusic {
    private static final int RATE=24000;
    private static final int[] DORIAN={0,2,3,5,7,9,10,12,14,15,17,19,21,22,24,26,27};
    private static final int[] AEOLIAN={0,2,3,5,7,8,10,12,14,15,17,19,20,22,24,26,27};
    private static final int[] MAJOR_PENT={0,2,4,7,9,12,14,16,19,21,24,26,28,31,33,36,38};
    private static final int[] MINOR_PENT={0,3,5,7,10,12,15,17,19,22,24,27,29,31,34,36,39};
    private final ContentRepository repo;private final AtomicBoolean running=new AtomicBoolean(false);private Thread thread;
    private final double[] phase={0,0,0,0,0,0,0,0,0,0,0,0};private final float[] echoL=new float[RATE],echoR=new float[RATE];private int echoAt;
    public ProceduralMusic(ContentRepository repo){this.repo=repo;}
    public void start(){if(running.getAndSet(true))return;thread=new Thread(this::render,"infinite-rpg-orchestra");thread.start();}
    public void stop(){running.set(false);if(thread!=null)thread.interrupt();}
    private void render(){
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()).setBufferSizeInBytes(Math.max(min,32768)).setTransferMode(AudioTrack.MODE_STREAM).build();
        track.setVolume(.34f);track.play();int step=0;ContentItem previous=null;List<ContentItem> songs=java.util.Collections.emptyList();long songsAt=0,controlAt=0;boolean enabled=true;int volume=62;
        try{while(running.get()){
            long now=System.currentTimeMillis();if(now-controlAt>1000){enabled=Boolean.parseBoolean(repo.getState("music_enabled","true"));volume=Math.max(0,Math.min(100,integer(repo.getState("music_volume","62"),62)));track.setVolume(volume/100f*.55f);controlAt=now;}
            if(!enabled){Thread.sleep(180);continue;}
            if(songs.isEmpty()||now-songsAt>60000){songs=repo.list("MUSIC",128);songsAt=now;}if(songs.isEmpty()){Thread.sleep(600);continue;}
            long offset=longValue(repo.getState("music_track_offset","0"),0);long selector=now/(5*60*1000L)+offset;ContentItem song=songs.get(Math.floorMod((int)selector,songs.size()));
            if(previous==null||previous.id!=song.id){previous=song;step=0;clearEcho();repo.putState("music_now",song.name);repo.putState("music_track_number",Integer.toString(Math.floorMod((int)selector,songs.size())+1));}
            short[] pcm=composeStep(song,step);track.write(pcm,0,pcm.length,AudioTrack.WRITE_BLOCKING);step=(step+1)%2048;
        }}catch(InterruptedException ignored){}finally{track.pause();track.flush();track.release();}
    }

    private short[] composeStep(ContentItem song,int step){
        int tempo=Math.max(48,Math.min(142,song.number("tempo",82))),root=song.number("root",50);double swing=Math.max(0,Math.min(.16,song.json.optDouble("swing",.04))),stepScale=(step&1)==0?1+swing:1-swing;int frames=(int)(RATE*60.0/tempo/2.0*stepScale);short[] pcm=new short[frames*2];int[] scale=scale(song.text("scale","dorian"));
        int section=(step/64)%8,local=step%64,movement=(step/512)%4;String form=song.text("form","ABACDBEC");char part=form.isEmpty()?'A':form.charAt(section%form.length());boolean alternate=part=='B'||part=='D',bridge=part=='C',finale=part=='E'||section==6;
        JSONArray chords=arr(song,alternate||part=='E'?"chords_b":"chords","[0,3,5,2,0,5,3,4]"),melody=arr(song,alternate?"melody_b":"melody","[7,-1,9,10,-1,9,7,5,3,-1,5,7,9,7,5,-1]"),counter=arr(song,"counter","[-1,-1,5,-1,-1,-1,7,-1]"),bass=arr(song,alternate?"bass_b":"bass","[0,-1,0,-1,3,-1,5,-1,0,-1,5,-1,3,-1,4,-1]"),rhythm=arr(song,alternate||finale?"rhythm_b":"rhythm","[1,0,0,1,0,0,1,0,1,0,0,1,0,1,0,0]");
        int chordDegree=chords.optInt((local/8+movement)%Math.max(1,chords.length()));int chordRoot=degree(scale,chordDegree),mel=melody.optInt(local%Math.max(1,melody.length()),-1),counterDegree=counter.optInt((local+section*3)%Math.max(1,counter.length()),-1),bassDegree=bass.optInt(local%Math.max(1,bass.length()),-1);if(bridge&&counterDegree>=0)mel=counterDegree+2;if(mel>=0&&local>=32)mel+=movement==0?1:movement==1?2:movement==2?-1:0;if(finale&&mel>=0&&local%8<6)mel+=2;boolean drum=rhythm.optInt(local%Math.max(1,rhythm.length()))>0&&!bridge;String lead=song.text("lead",song.text("wave","triangle")),ensemble=song.text("ensemble","strings"),percussion=song.text("percussion","handdrum");double echoSeconds=Math.max(.12,Math.min(.42,song.json.optDouble("echo",.23)));Random noise=new Random(song.id*991+step*37L);
        for(int i=0;i<frames;i++){
            double t=(double)i/frames,slow=(step+t)/64.0,sectionGain=section==0&&local<16?.58:bridge?.70:finale?1.04:.88;double left=0,right=0;
            int[] triad={chordRoot,degree(scale,chordDegree+2),degree(scale,chordDegree+4)};double padEnv=envelope(ensemble,t,false);for(int v=0;v<3;v++){double voice=timbre(advance(v,hz(root+triad[v])),ensemble)*padEnv*.082*sectionGain;double pan=.18+.31*v;left+=voice*(1-pan);right+=voice*pan;}
            int arpDegree=triad[Math.floorMod(local+section,3)]+12+(movement==2?2:0);double arp=timbre(advance(3,hz(root+arpDegree)),ensemble)*envelope(ensemble,t,true)*(bridge?.07:.13);left+=arp*(.67+.16*Math.sin(slow*6.28));right+=arp*(.67-.16*Math.sin(slow*6.28));
            if(mel>=0){double m=timbre(advance(4,hz(root+degree(scale,mel)+12)),lead)*envelope(lead,t,true)*(finale?.22:.18),pan=.5+.22*Math.sin((step+t)*.19);left+=m*(1-pan);right+=m*pan;}
            if(counterDegree>=0&&(alternate||finale)&&local%4==0){double q=timbre(advance(7,hz(root+degree(scale,counterDegree)+5)),"flute")*envelope("flute",t,true)*.095;left+=q*.72;right+=q*.28;}
            if(bassDegree>=0){double bEnv=Math.exp(-2.0*t)*Math.min(1,t*32),b=(Math.sin(advance(5,hz(root+degree(scale,bassDegree)-12)))*.82+triangle(phase[5])*.18)*bEnv*.16;left+=b;right+=b;}
            double drums=percussion(percussion,drum,local,t,noise);left+=drums;right+=drums*("brush".equals(percussion)?.55:.92);if(local>=60){double fill=(noise.nextDouble()*2-1)*Math.exp(-8*t)*.028*(local-59);left+=fill;right-=fill*.35;}
            int delay=(echoAt+RATE-(int)(RATE*echoSeconds))%RATE;float wetL=echoL[delay],wetR=echoR[delay];float dryL=(float)Math.tanh(left*.88),dryR=(float)Math.tanh(right*.88);echoL[echoAt]=dryL+wetR*.31f;echoR[echoAt]=dryR+wetL*.31f;echoAt=(echoAt+1)%RATE;double master=.72+.07*Math.sin(slow*Math.PI*2)+movement*.018;pcm[i*2]=(short)(clamp(dryL+wetL*.20f)*master*Short.MAX_VALUE);pcm[i*2+1]=(short)(clamp(dryR+wetR*.20f)*master*Short.MAX_VALUE);
        }return pcm;
    }

    private double timbre(double p,String instrument){if("flute".equals(instrument))return Math.sin(p)*.86+Math.sin(p*2)*.10+Math.sin(p*3)*.04;if("whistle".equals(instrument))return Math.sin(p)*.82+Math.sin(p*3)*.18;if("ocarina".equals(instrument))return Math.sin(p)*.91+triangle(p)*.09;if("violin".equals(instrument)||"strings".equals(instrument))return triangle(p)*.52+Math.sin(p)*.36+Math.sin(p*2)*.12;if("clarinet".equals(instrument))return Math.sin(p)*.72+Math.sin(p*3)*.28;if("reed".equals(instrument))return Math.tanh(1.35*Math.sin(p))*.62+Math.sin(p*2)*.18;if("brass".equals(instrument))return Math.tanh(1.8*Math.sin(p))*.62+Math.sin(p*2)*.26+Math.sin(p*3)*.12;if("bell".equals(instrument)||"chime".equals(instrument)||"celesta".equals(instrument))return Math.sin(p)*.60+Math.sin(p*2.01)*.25+Math.sin(p*3.97)*.15;if("organ".equals(instrument)||"choir".equals(instrument))return Math.sin(p)*.64+Math.sin(p*2)*.22+Math.sin(p*.5)*.14;if("accordion".equals(instrument))return triangle(p)*.45+Math.tanh(1.3*Math.sin(p))*.40+Math.sin(p*2)*.15;if("marimba".equals(instrument))return Math.sin(p)*.72+Math.sin(p*3)*.20+Math.sin(p*5)*.08;if("guitar".equals(instrument)||"harp".equals(instrument)||"pizzicato".equals(instrument))return triangle(p)*.58+Math.sin(p)*.32+Math.sin(p*2)*.10;return wave(p,instrument);}
    private double envelope(String instrument,double t,boolean articulated){double attack=Math.min(1,t*(articulated?34:12)),release=Math.min(1,(1-t)*(articulated?5:3));if("bell".equals(instrument)||"chime".equals(instrument)||"celesta".equals(instrument))return attack*Math.exp(-3.5*t);if("marimba".equals(instrument)||"harp".equals(instrument)||"guitar".equals(instrument)||"pizzicato".equals(instrument))return attack*Math.exp(-4.6*t);return attack*release;}
    private double percussion(String style,boolean hit,int step,double t,Random noise){if("none".equals(style))return 0;double n=noise.nextDouble()*2-1;if("clockwork".equals(style)){double tick=Math.sin(advance(8,2600-900*t))*Math.exp(-24*t)*(step%2==0?.045:.025);return tick+n*Math.exp(-30*t)*.008;}if("brush".equals(style))return n*Math.exp(-10*t)*(hit?.038:.012);if("march".equals(style)){double kick=hit?Math.sin(advance(6,82-42*t))*Math.exp(-11*t)*.16:0,snare=(step%8==4||step%8==7)?n*Math.exp(-16*t)*.08:0;return kick+snare;}if("waltz".equals(style)){double low=hit?Math.sin(advance(6,70-32*t))*Math.exp(-12*t)*(step%6==0?.15:.07):0;return low+n*Math.exp(-20*t)*.018;}if("orchestral".equals(style)){double timp=hit?Math.sin(advance(6,76-30*t))*Math.exp(-8*t)*.17:0,cymbal=step%32==0?n*Math.exp(-5*t)*.055:0;return timp+cymbal;}double hand=hit?Math.sin(advance(6,105-58*t))*Math.exp(-13*t)*.14:0,shaker=n*Math.exp(-18*t)*("festival".equals(style)?.032:.018);return hand+shaker;}

    private JSONArray arr(ContentItem song,String key,String fallback){JSONArray a=song.json.optJSONArray(key);if(a!=null)return a;try{return new JSONArray(fallback);}catch(Exception e){return new JSONArray();}}
    private int[] scale(String name){if("aeolian".equals(name))return AEOLIAN;if("major_pentatonic".equals(name))return MAJOR_PENT;if("minor_pentatonic".equals(name))return MINOR_PENT;return DORIAN;}
    private int degree(int[] scale,int degree){return scale[Math.floorMod(degree,scale.length)];}
    private double hz(double midi){return 440*Math.pow(2,(midi-69)/12.0);}
    private double advance(int voice,double hz){phase[voice]+=2*Math.PI*hz/RATE;return phase[voice];}
    private double triangle(double p){return (2/Math.PI)*Math.asin(Math.sin(p));}
    private double wave(double p,String type){if("sine".equals(type))return Math.sin(p);if("soft_square".equals(type))return Math.tanh(1.7*Math.sin(p));return triangle(p);}
    private float clamp(float x){return Math.max(-.98f,Math.min(.98f,x));}
    private int integer(String value,int fallback){try{return Integer.parseInt(value);}catch(Exception e){return fallback;}}
    private long longValue(String value,long fallback){try{return Long.parseLong(value);}catch(Exception e){return fallback;}}
    private void clearEcho(){java.util.Arrays.fill(echoL,0);java.util.Arrays.fill(echoR,0);}
}

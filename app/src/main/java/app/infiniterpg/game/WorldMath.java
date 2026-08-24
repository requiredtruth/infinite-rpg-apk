package app.infiniterpg.game;

public final class WorldMath {
    private WorldMath(){}
    public static long hash(long seed,int x,int y){long h=seed^(x*0x9E3779B97F4A7C15L)^(y*0xC2B2AE3D27D4EB4FL);h^=h>>>30;h*=0xBF58476D1CE4E5B9L;h^=h>>>27;h*=0x94D049BB133111EBL;return h^(h>>>31);}
    private static double at(long seed,int x,int y){return ((hash(seed,x,y)>>>11)&0x1fffff)/(double)0x1fffff;}
    private static double smooth(double t){return t*t*(3-2*t);}
    public static double noise(long seed,double x,double y){int x0=(int)Math.floor(x),y0=(int)Math.floor(y);double tx=smooth(x-x0),ty=smooth(y-y0);double a=at(seed,x0,y0),b=at(seed,x0+1,y0),c=at(seed,x0,y0+1),d=at(seed,x0+1,y0+1);return lerp(lerp(a,b,tx),lerp(c,d,tx),ty);}
    public static double fractal(long seed,double x,double y){double value=0,weight=.55,total=0,scale=.055;for(int i=0;i<4;i++){value+=noise(seed+i*991,x*scale,y*scale)*weight;total+=weight;weight*=.5;scale*=2;}return value/total;}
    private static double lerp(double a,double b,double t){return a+(b-a)*t;}
}

package io.asv.collectorvision;

/** RGB separable antialiased resizing compatible with Pillow 12.3's 8-bit path.
 * Own identity model: rectangular Pillow BILINEAR, not Diego's OpenCV resize.
 * Algorithm reference and license attribution: ../../../../../../PILLOW-LICENSE.txt.
 */
public final class OwnIdentityPreprocess {
    public static final double[][] ROIS = {{0,0,1,1},{.08,.12,.92,.55},
        {.06,.59,.94,.90},{.04,.49,.96,.64},{.04,.88,.96,.99}};
    private static final int PRECISION = 22;

    private record Kernel(int[] start, int[][] weights) {}

    private static double filter(double x, boolean cubic) {
        x = Math.abs(x);
        if (!cubic) return Math.max(0, 1-x);
        if (x < 1) return (1.5*x-2.5)*x*x+1;
        if (x < 2) return ((x-5)*x+8)*x*(-.5)+2;
        return 0;
    }

    private static Kernel kernel(int in, int out, boolean cubic) {
        int[] start = new int[out];
        int[][] weights = new int[out][];
        double scale = (double) in/out, fs = Math.max(1, scale), support = (cubic ? 2 : 1)*fs;
        for (int p=0; p<out; p++) {
            double center = (p+.5)*scale;
            int lo = Math.max(0, (int)(center-support+.5));
            int hi = Math.min(in, (int)(center+support+.5));
            double[] raw = new double[hi-lo];
            double sum = 0;
            for (int k=0; k<raw.length; k++) { raw[k]=filter((lo+k-center+.5)*(1/fs),cubic); sum+=raw[k]; }
            weights[p] = new int[raw.length]; start[p]=lo;
            for (int k=0; k<raw.length; k++) {
                double value=raw[k]/sum*(1<<PRECISION);
                weights[p][k]=(int)(value+(value<0 ? -.5 : .5));
            }
        }
        return new Kernel(start,weights);
    }

    private static int clip(int x) { return Math.max(0,Math.min(255,x>>PRECISION)); }

    public static byte[] resize(byte[] rgb, int width, int height, int outW, int outH, boolean cubic) {
        if (width<1 || height<1 || outW<1 || outH<1 || rgb.length!=width*height*3) throw new IllegalArgumentException("RGB dimensions");
        Kernel horizontal=kernel(width,outW,cubic), vertical=kernel(height,outH,cubic);
        byte[] tmp=new byte[outW*height*3], result=new byte[outW*outH*3];
        for(int y=0;y<height;y++) for(int x=0;x<outW;x++) for(int c=0;c<3;c++) {
            int sum=1<<(PRECISION-1);
            for(int k=0;k<horizontal.weights[x].length;k++)
                sum+=(rgb[(y*width+horizontal.start[x]+k)*3+c]&255)*horizontal.weights[x][k];
            tmp[(y*outW+x)*3+c]=(byte)clip(sum);
        }
        for(int y=0;y<outH;y++) for(int x=0;x<outW;x++) for(int c=0;c<3;c++) {
            int sum=1<<(PRECISION-1);
            for(int k=0;k<vertical.weights[y].length;k++)
                sum+=(tmp[((vertical.start[y]+k)*outW+x)*3+c]&255)*vertical.weights[y][k];
            result[(y*outW+x)*3+c]=(byte)clip(sum);
        }
        return result;
    }

    // The production warp is fixed. Kernels are immutable and safe to share.
    private static final Kernel FIXED_X = kernel(640,320,false);
    private static final Kernel FIXED_Y = kernel(896,448,false);

    /** Same integer rounding as viewReference; fuse RGB extraction with horizontal filtering. */
    public static byte[] view(int[] argb,int w,int h) {
        if(w<1||h<1||argb.length!=w*h) throw new IllegalArgumentException("ARGB dimensions");
        Kernel hx=w==640 ? FIXED_X : kernel(w,320,false);
        Kernel vy=h==896 ? FIXED_Y : kernel(h,448,false);
        byte[] tmp=new byte[320*h*3], result=new byte[320*448*3];
        for(int y=0;y<h;y++) for(int x=0;x<320;x++) {
            int r=1<<(PRECISION-1),g=r,b=r;
            int[] weights=hx.weights[x]; int start=y*w+hx.start[x];
            for(int k=0;k<weights.length;k++) {
                int v=argb[start+k], weight=weights[k];
                r+=((v>>16)&255)*weight; g+=((v>>8)&255)*weight; b+=(v&255)*weight;
            }
            int dst=(y*320+x)*3;
            tmp[dst]=(byte)clip(r);tmp[dst+1]=(byte)clip(g);tmp[dst+2]=(byte)clip(b);
        }
        for(int y=0;y<448;y++) {
            int[] weights=vy.weights[y]; int start=vy.start[y]*320*3;
            for(int x=0;x<320;x++) {
                int r=1<<(PRECISION-1),g=r,b=r;
                for(int k=0;k<weights.length;k++) {
                    int src=start+k*320*3+x*3,weight=weights[k];
                    r+=(tmp[src]&255)*weight;g+=(tmp[src+1]&255)*weight;b+=(tmp[src+2]&255)*weight;
                }
                int dst=(y*320+x)*3;
                result[dst]=(byte)clip(r);result[dst+1]=(byte)clip(g);result[dst+2]=(byte)clip(b);
            }
        }
        return result;
    }

    /** Frozen reference retained for byte-exact regression tests. */
    public static byte[] viewReference(int[] argb,int w,int h) {
        if(w<1||h<1||argb.length!=w*h) throw new IllegalArgumentException("ARGB dimensions");
        byte[] rgb=new byte[w*h*3];
        for(int i=0;i<argb.length;i++){int v=argb[i];rgb[3*i]=(byte)(v>>16);rgb[3*i+1]=(byte)(v>>8);rgb[3*i+2]=(byte)v;}
        return resize(rgb,w,h,320,448,false);
    }
}

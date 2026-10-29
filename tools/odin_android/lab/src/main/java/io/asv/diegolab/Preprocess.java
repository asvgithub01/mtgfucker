package io.asv.odinlab;

/** RGB separable antialiased resizing compatible with Pillow 12.3's 8-bit path.
 * No Android Bitmap scaling: that would change the benchmark's bicubic policy.
 * Algorithm reference and license attribution: ../NOTICE.md.
 */
public final class Preprocess {
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

    public static byte[] resize(byte[] rgb, int width, int height, int size, boolean cubic) {
        if (width<1 || height<1 || size<1 || rgb.length!=width*height*3) throw new IllegalArgumentException("RGB dimensions");
        Kernel horizontal=kernel(width,size,cubic), vertical=kernel(height,size,cubic);
        byte[] tmp=new byte[size*height*3], result=new byte[size*size*3];
        for(int y=0;y<height;y++) for(int x=0;x<size;x++) for(int c=0;c<3;c++) {
            int sum=1<<(PRECISION-1);
            for(int k=0;k<horizontal.weights[x].length;k++)
                sum+=(rgb[(y*width+horizontal.start[x]+k)*3+c]&255)*horizontal.weights[x][k];
            tmp[(y*size+x)*3+c]=(byte)clip(sum);
        }
        for(int y=0;y<size;y++) for(int x=0;x<size;x++) for(int c=0;c<3;c++) {
            int sum=1<<(PRECISION-1);
            for(int k=0;k<vertical.weights[y].length;k++)
                sum+=(tmp[((vertical.start[y]+k)*size+x)*3+c]&255)*vertical.weights[y][k];
            result[(y*size+x)*3+c]=(byte)clip(sum);
        }
        return result;
    }

    /** Full-frame bilinear AA, bit-exact with view(...,0,false,size,false).
     * Fuse RGB channels; avoid a full-resolution RGB copy and repeated address math.
     * Each channel keeps the original integer accumulation order and intermediate rounding.
     */
    public static byte[] corneliusExact(int[] argb,int width,int height,int size) {
        if(width<1||height<1||size<1||argb.length!=width*height)throw new IllegalArgumentException("ARGB dimensions");
        Kernel horizontal=kernel(width,size,false),vertical=kernel(height,size,false);
        int[] tmp=new int[size*height];byte[] result=new byte[size*size*3];
        for(int y=0;y<height;y++)for(int x=0;x<size;x++){
            int r=1<<(PRECISION-1),g=r,b=r;int[] weights=horizontal.weights[x];int offset=y*width+horizontal.start[x];
            for(int k=0;k<weights.length;k++){int pixel=argb[offset+k],weight=weights[k];r+=((pixel>>>16)&255)*weight;g+=((pixel>>>8)&255)*weight;b+=(pixel&255)*weight;}
            tmp[y*size+x]=(clip(r)<<16)|(clip(g)<<8)|clip(b);
        }
        for(int y=0;y<size;y++){int[] weights=vertical.weights[y];int offset=vertical.start[y]*size;
            for(int x=0;x<size;x++){
                int r=1<<(PRECISION-1),g=r,b=r;
                for(int k=0;k<weights.length;k++){int pixel=tmp[offset+k*size+x],weight=weights[k];r+=((pixel>>>16)&255)*weight;g+=((pixel>>>8)&255)*weight;b+=(pixel&255)*weight;}
                int i=(y*size+x)*3;result[i]=(byte)clip(r);result[i+1]=(byte)clip(g);result[i+2]=(byte)clip(b);
            }
        }
        return result;
    }

    public static byte[] view(int[] argb,int width,int height,int roi,boolean rotated,int size,boolean cubic) {
        double[] r=ROIS[roi];
        int left=(int)Math.rint(r[0]*width),top=(int)Math.rint(r[1]*height);
        int w=(int)Math.rint(r[2]*width)-left,h=(int)Math.rint(r[3]*height)-top;
        byte[] rgb=new byte[w*h*3];
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            int sx=x+left,sy=y+top;
            if(rotated){sx=width-1-sx;sy=height-1-sy;}
            int color=argb[sy*width+sx],p=(y*w+x)*3;
            rgb[p]=(byte)(color>>16);rgb[p+1]=(byte)(color>>8);rgb[p+2]=(byte)color;
        }
        return resize(rgb,w,h,size,cubic);
    }

    public static byte[] diegoView(int[] argb,int w,int h,boolean rotate) {
        return diegoView(argb,w,h,rotate,false);
    }
    public static byte[] diegoView(int[] argb,int w,int h,boolean rotate,boolean exact) {
        byte[] rgb=new byte[w*h*3];
        for(int i=0;i<w*h;i++){int v=argb[rotate?w*h-1-i:i];rgb[3*i]=(byte)(v>>16);rgb[3*i+1]=(byte)(v>>8);rgb[3*i+2]=(byte)v;}
        org.opencv.core.Mat a=new org.opencv.core.Mat(h,w,org.opencv.core.CvType.CV_8UC3), b=new org.opencv.core.Mat();
        try { a.put(0,0,rgb);org.opencv.imgproc.Imgproc.resize(a,b,new org.opencv.core.Size(320,448),0,0,exact?org.opencv.imgproc.Imgproc.INTER_LINEAR_EXACT:org.opencv.imgproc.Imgproc.INTER_LINEAR);
            byte[] out=new byte[320*448*3];b.get(0,0,out);return out;
        } finally {a.release();b.release();}
    }

    public static float[] diegoTensor(byte[] rgb) {
        int n=rgb.length/3;float[] out=new float[rgb.length];
        float[] mean={.485f,.456f,.406f},std={.229f,.224f,.225f};
        for(int c=0;c<3;c++)for(int i=0;i<n;i++)out[c*n+i]=((rgb[i*3+c]&255)-mean[c]*255f)/(std[c]*255f);
        return out;
    }

    public static float[] tensor(byte[] rgb) {
        int n=rgb.length/3;float[] out=new float[rgb.length];
        float[] mean={.485f,.456f,.406f},std={.229f,.224f,.225f};
        for(int c=0;c<3;c++) for(int i=0;i<n;i++) out[c*n+i]=((rgb[i*3+c]&255)/255f-mean[c])/std[c];
        return out;
    }
}

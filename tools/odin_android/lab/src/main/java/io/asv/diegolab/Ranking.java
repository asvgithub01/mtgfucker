package io.asv.odinlab;

import java.util.Arrays;

public final class Ranking {
    public static final float[] WEIGHTS={.2f,.4f,.2f,.1f,.1f};
    public record Result(Integer[] indices, float[] scores, int[] orientations) {}
    public static void normalize(float[] a) {
        double ss=0;for(float v:a){if(!Float.isFinite(v))throw new IllegalArgumentException("Nonfinite");ss+=v*v;}
        if(ss<1e-16)throw new IllegalArgumentException("Zero embedding");
        double norm=Math.sqrt(ss);for(int i=0;i<a.length;i++)a[i]/=norm;
    }
    public static Result rank(float[] gallery,int count,int regions,int dim,float[][] query) {
        if(gallery.length!=count*regions*dim || query.length!=2*regions)throw new IllegalArgumentException("Shape");
        float[] scores=new float[count];int[] angles=new int[count];Integer[] order=new Integer[count];
        for(int n=0;n<count;n++) {
            float best=Float.NEGATIVE_INFINITY;
            for(int o=0;o<2;o++) {
                float score=0;
                for(int r=0;r<regions;r++) {
                    float dot=0;int base=(n*regions+r)*dim;
                    for(int d=0;d<dim;d++)dot+=gallery[base+d]*query[o*regions+r][d];
                    score+=dot*(regions==1 ? 1 : WEIGHTS[r]);
                }
                if(score>best){best=score;angles[n]=o*180;}
            }
            scores[n]=best;order[n]=n;
        }
        Arrays.sort(order,(a,b)->Float.compare(scores[b],scores[a]));
        return new Result(order,scores,angles);
    }
}

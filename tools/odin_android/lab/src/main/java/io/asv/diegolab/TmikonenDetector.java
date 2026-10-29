package io.asv.odinlab;
import java.util.*;
import org.opencv.core.*;
import org.opencv.imgproc.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

/** Native Android OpenCV + Java/JTS port, not a Python runtime. */
final class TmikonenDetector {
 static final class Result{final List<Polygon> quads=new ArrayList<>();int geometryRejected;int rawCandidates;}
 static void contours(Mat channel,List<MatOfPoint> out,boolean adaptive){Mat binary=new Mat(),hierarchy=new Mat();try{if(adaptive)Imgproc.adaptiveThreshold(channel,binary,255,Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,1+2*(Math.min(channel.rows(),channel.cols())/20),10);else Imgproc.threshold(channel,binary,70,255,Imgproc.THRESH_BINARY);Imgproc.findContours(binary,out,hierarchy,Imgproc.RETR_TREE,Imgproc.CHAIN_APPROX_SIMPLE);}finally{binary.release();hierarchy.release();}}
 static Result detect(Mat bgr,String mode,boolean rejectFrame){
  long deadline=System.nanoTime()+30_000_000_000L;Mat lab=new Mat(),adjusted=new Mat(),gray=new Mat();List<Mat> channels=new ArrayList<>();List<MatOfPoint> contours=new ArrayList<>();CLAHE clahe=Imgproc.createCLAHE(2,new Size(8,8));Result result=new Result();
  try{
   Imgproc.cvtColor(bgr,lab,Imgproc.COLOR_BGR2Lab);Core.split(lab,channels);clahe.apply(channels.get(0),channels.get(0));Core.merge(channels,lab);Imgproc.cvtColor(lab,adjusted,Imgproc.COLOR_Lab2BGR);for(Mat m:channels)m.release();channels.clear();Imgproc.cvtColor(adjusted,gray,Imgproc.COLOR_BGR2GRAY);
   contours(gray,contours,false);
   if(mode.equals("all")){List<MatOfPoint> extra=new ArrayList<>();contours(gray,extra,true);contours.addAll(extra);Core.split(adjusted,channels);
    for(Mat ch:channels){Mat bin=new Mat(),hierarchy=new Mat();try{clahe.apply(ch,ch);Imgproc.threshold(ch,bin,110,255,Imgproc.THRESH_BINARY);extra=new ArrayList<>();Imgproc.findContours(bin,extra,hierarchy,Imgproc.RETR_TREE,Imgproc.CHAIN_APPROX_SIMPLE);contours.addAll(extra);}finally{bin.release();hierarchy.release();}}
   }
   contours.sort((a,b)->Double.compare(Imgproc.contourArea(b),Imgproc.contourArea(a)));double max=.01,area=(double)bgr.rows()*bgr.cols();List<Polygon> candidates=new ArrayList<>();
   for(MatOfPoint contour:contours){if(Thread.currentThread().isInterrupted()||System.nanoTime()>deadline)throw new IllegalStateException("Tmikonen exceeded 30 s; incomplete run rejected");Rect box=Imgproc.boundingRect(contour);if(rejectFrame&&(box.x==0||box.y==0||box.x+box.width==bgr.cols()||box.y+box.height==bgr.rows()))continue;
    MatOfInt indices=new MatOfInt();try{Imgproc.convexHull(contour,indices);Point[] cp=contour.toArray();int[] ix=indices.toArray();if(ix.length<3){break;}Coordinate[] coords=new Coordinate[ix.length];for(int i=0;i<ix.length;i++)coords[i]=new Coordinate(cp[ix[i]].x,cp[ix[i]].y);Polygon hull=TmikonenGeometry.poly(coords);
     if(hull.getArea()<.1*max||hull.getArea()<area/1000)break;
     Polygon quad=TmikonenGeometry.bound(hull);double diff=TmikonenGeometry.cornerDiff(hull,quad),factor=TmikonenGeometry.formFactor(quad);
     if(.1*max<quad.getArea()&&quad.getArea()<area*.99&&diff<.35&&factor>.25&&factor<.33){if(max<.1)max=quad.getArea();candidates.add(quad);}
    }catch(IllegalArgumentException|org.locationtech.jts.geom.TopologyException e){result.geometryRejected++;}finally{indices.release();}
   }
   result.rawCandidates=candidates.size();candidates.sort((a,b)->Double.compare(b.getArea(),a.getArea()));for(Polygon p:candidates){boolean keep=true;for(Polygon q:result.quads)if(TmikonenGeometry.iou(p,q)>=.5){keep=false;break;}if(keep)result.quads.add(p);}return result;
  }finally{lab.release();adjusted.release();gray.release();for(Mat m:channels)m.release();for(MatOfPoint m:contours)m.release();clahe.collectGarbage();clahe.clear();}
 }
}

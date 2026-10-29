package io.asv.odinlab;
import java.util.*;
import org.locationtech.jts.geom.*;

/** Java/JTS translation of Timo Ikonen's polygon routines, upstream 7872893.
 * No pHash recognition. See THIRD_PARTY.md for provenance and deviations. */
final class TmikonenGeometry {
 static final GeometryFactory GF=new GeometryFactory();
 static Polygon poly(Coordinate[] points){Coordinate[] closed=Arrays.copyOf(points,points.length+1);closed[points.length]=new Coordinate(points[0]);return GF.createPolygon(closed);}
 static Coordinate[] points(Polygon p){Coordinate[] a=p.getExteriorRing().getCoordinates();return Arrays.copyOf(a,a.length-1);}
 static Coordinate[] order(Coordinate[] points){Coordinate[] p=points.clone();double x=0,y=0;for(Coordinate c:p){x+=c.x/p.length;y+=c.y/p.length;}final double cx=x,cy=y;Arrays.sort(p,Comparator.comparingDouble(c->Math.atan2(c.y-cy,c.x-cx)));return p;}
 static Coordinate intersect(Coordinate a,Coordinate b,Coordinate c,Coordinate d){double den=(a.x-b.x)*(c.y-d.y)-(a.y-b.y)*(c.x-d.x);if(den==0)return null;double ab=a.x*b.y-a.y*b.x,cd=c.x*d.y-c.y*d.x;return new Coordinate((ab*(c.x-d.x)-(a.x-b.x)*cd)/den,(ab*(c.y-d.y)-(a.y-b.y)*cd)/den);}
 static Polygon simplify(Polygon polygon){ArrayList<Coordinate> p=new ArrayList<>(Arrays.asList(points(polygon)));
  while(p.size()>4){int n=p.size(),k=0;double total=0,min=Double.POSITIVE_INFINITY;for(int i=0;i<n;i++){double len=p.get(i).distance(p.get((i+1)%n));total+=len;if(len<min){min=len;k=i;}}
   if(min>=.15*total)break;Coordinate cross=intersect(p.get((k+n-1)%n),p.get(k),p.get((k+1)%n),p.get((k+2)%n));if(cross==null||!Double.isFinite(cross.x)||!Double.isFinite(cross.y))throw new IllegalArgumentException("Parallel simplified edges");p.set(k,cross);p.remove((k+1)%n);
  }return poly(p.toArray(new Coordinate[0]));
 }
 static Polygon bound(Polygon hull){Coordinate[] p=order(points(simplify(hull)));int n=p.length;if(n>32)throw new IllegalArgumentException("Polygon complexity >32");double x=0,y=0;for(Coordinate c:p){x+=c.x/n;y+=c.y/n;}Coordinate[] small=new Coordinate[n];for(int i=0;i<n;i++)small[i]=new Coordinate(x+.9999*(p[i].x-x),y+.9999*(p[i].y-y));Polygon shrunk=poly(small),best=null;double min=Double.POSITIVE_INFINITY;
  // Upstream product(n,repeat=4) rejects unordered indices: equivalent combinations.
  for(int i=0;i<n-3;i++)for(int j=i+1;j<n-2;j++)for(int k=j+1;k<n-1;k++)for(int l=k+1;l<n;l++){
   Coordinate[] q={intersect(p[i],p[(i+1)%n],p[j],p[(j+1)%n]),intersect(p[j],p[(j+1)%n],p[k],p[(k+1)%n]),intersect(p[k],p[(k+1)%n],p[l],p[(l+1)%n]),intersect(p[l],p[(l+1)%n],p[i],p[(i+1)%n])};boolean valid=true;for(Coordinate c:q)if(c==null||!Double.isFinite(c.x)||!Double.isFinite(c.y))valid=false;if(!valid)continue;Polygon quad=poly(order(q));double area=quad.getArea();if(area<min&&quad.isValid()&&quad.contains(shrunk)){min=area;best=quad;}
  }if(best==null)throw new IllegalArgumentException("No enclosing quadrilateral");return best;
 }
 static double cornerDiff(Polygon hull,Polygon quad){Coordinate[] q=points(quad);double x=0,y=0;for(Coordinate c:q){x+=c.x/4;y+=c.y/4;}double ha=0,qa=0;
  for(Coordinate c:q){double ix=x+.9*(c.x-x),iy=y+.9*(c.y-y);LineString line=GF.createLineString(new Coordinate[]{new Coordinate(ix+c.y-y,iy-c.x+x),new Coordinate(ix-c.y+y,iy+c.x-x)});Coordinate[] cut=quad.intersection(line).getCoordinates();if(cut.length!=2)throw new IllegalArgumentException("Corner line intersection");Polygon triangle=poly(new Coordinate[]{cut[0],cut[1],c});qa+=triangle.getArea();ha+=triangle.intersection(hull).getArea();}
  if(qa==0)throw new IllegalArgumentException("Zero corner area");return 1-ha/qa;
 }
 static double formFactor(Polygon p){Coordinate[] q=points(p);double min=Double.POSITIVE_INFINITY;for(int i=0;i<q.length;i++)min=Math.min(min,q[i].distance(q[(i+1)%q.length]));return p.getArea()/(p.getLength()*min);}
 static double iou(Polygon a,Polygon b){double area=a.intersection(b).getArea();return area/Math.max(1e-9,a.getArea()+b.getArea()-area);}
}

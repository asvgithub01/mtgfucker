package io.asv.odinlab;
import org.junit.Test;
import static org.junit.Assert.*;
import org.locationtech.jts.geom.*;
public class TmikonenGeometryTest {
 private Polygon polygon(double... xy){Coordinate[] p=new Coordinate[xy.length/2];for(int i=0;i<p.length;i++)p[i]=new Coordinate(xy[2*i],xy[2*i+1]);return TmikonenGeometry.poly(p);}
 @Test public void intersection(){Coordinate p=TmikonenGeometry.intersect(new Coordinate(0,0),new Coordinate(2,2),new Coordinate(0,2),new Coordinate(2,0));assertEquals(1,p.x,1e-9);assertEquals(1,p.y,1e-9);}
 @Test public void parallel(){assertNull(TmikonenGeometry.intersect(new Coordinate(0,0),new Coordinate(1,0),new Coordinate(0,1),new Coordinate(1,1)));}
 @Test public void rectangle(){Polygon p=polygon(0,0,63,0,63,88,0,88),q=TmikonenGeometry.bound(p);assertEquals(p.getArea(),q.getArea(),1e-9);assertEquals(0,TmikonenGeometry.cornerDiff(p,q),1e-9);assertEquals(88./302,TmikonenGeometry.formFactor(q),1e-9);}
 @Test public void roundedRectangle(){Polygon p=polygon(2,0,61,0,63,2,63,86,61,88,2,88,0,86,0,2),q=TmikonenGeometry.bound(p);assertEquals(63*88,q.getArea(),1e-6);assertTrue(q.covers(p));assertTrue(TmikonenGeometry.cornerDiff(p,q)>0);}
 @Test public void overlap(){Polygon a=polygon(0,0,10,0,10,10,0,10),b=polygon(5,0,15,0,15,10,5,10);assertEquals(1./3,TmikonenGeometry.iou(a,b),1e-9);}
 @Test public void ordering(){Coordinate[] q=TmikonenGeometry.order(new Coordinate[]{new Coordinate(10,10),new Coordinate(0,0),new Coordinate(0,10),new Coordinate(10,0)});assertEquals(0,q[0].x,0);assertEquals(0,q[0].y,0);assertTrue(TmikonenGeometry.poly(q).isValid());}
}

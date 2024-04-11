/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.analysis;
import java.util.*;
import org.jlab.geom.prim.Line3D;
import org.jlab.rec.cvt.hit.Strip;
/**
 *
 * @author veronique
 */


class Point {
    double x, y;
    Point(double x, double y) {
        this.x = x;
        this.y = y;
    }
    double y() {
        return this.y;
    }
}

class Circle {
    Point center;
    double radius;

    Circle(Point center, double radius) {
        this.center = center;
        this.radius = radius;
    }

    boolean contains(Point point, double tolerance) {
        double distance = Math.abs(Math.sqrt(Math.pow(point.x - center.x, 2) + Math.pow(point.y - center.y, 2))-radius);
        
        return distance <= tolerance;
    }
}

public class RansacCircleFitting {


   
    static boolean areCollinear(Point a, Point b, Point c) {
        return Math.abs((a.x*(b.y-c.y) + b.x*(c.y-a.y) + c.x*(a.y-b.y))) < 1e-9;
    }

    static Circle calculateCircle(Point a, Point b, Point c) {
        if(areCollinear(a,b,c)) return null;
        
        double x1 = a.x, y1 = a.y;
        double x2 = b.x, y2 = b.y;
        double x3 = c.x, y3 = c.y;

        double A = x1*(y2-y3) - y1*(x2-x3) + x2*y3 - x3*y2;
        double B = (x1*x1 + y1*y1)*(y3-y2) + (x2*x2 + y2*y2)*(y1-y3) + (x3*x3 + y3*y3)*(y2-y1);
        double C = (x1*x1 + y1*y1)*(x2-x3) + (x2*x2 + y2*y2)*(x3-x1) + (x3*x3 + y3*y3)*(x1-x2);
        double D = (x1*x1 + y1*y1)*(x3*y2 - x2*y3) + (x2*x2 + y2*y2)*(x1*y3 - x3*y1) + (x3*x3 + y3*y3)*(x2*y1 - x1*y2);

        double centerX = -B / (2*A);
        double centerY = -C / (2*A);
        double radius = Math.sqrt((B*B + C*C - 4*A*D) / (4*A*A));

        return new Circle(new Point(centerX, centerY), radius);
    }

    public static Circle fitCircleRANSAC(List<Point> points, int numIterations, double tolerance) {
        Circle bestCircle = null;
        int bestInliers = 0;
        if(points.size()<3) return null;
        
        Random random = new Random();

        for (int i = 0; i < numIterations; i++) {
            Point p1 = points.get(random.nextInt(points.size()));
            Point p2 = points.get(random.nextInt(points.size()));
            Point p3 = points.get(random.nextInt(points.size()));
            
            Circle currentCircle = calculateCircle(p1,p2,p3);
            if(currentCircle==null) continue;
            int currentInliers = 0;

            for (Point point : points) {
                if (currentCircle.contains(point, tolerance)) {
                    currentInliers++;
                }
            }

            if (currentInliers > bestInliers) {
                bestCircle = currentCircle;
                bestInliers = currentInliers;
            }
        }

        return bestCircle;
    }
    
    
    public static List<Double> residuals(Circle circle, List<Point> points) {
        List<Double> residuals = new ArrayList<>();
        double x0 = circle.center.x;
        double y0 = circle.center.y;
        double r0 = circle.radius;
        
        for(Point point : points) {
            double r = Math.sqrt((point.x-x0)*(point.x-x0)+(point.y-y0)*(point.y-y0));
            double resi = (r-r0);
            residuals.add(resi);
        }
        return residuals;
    }
    
    public static double circleDocaToLine(Circle circle, Strip strip) {
        Line3D line = strip.getLine();
        double lineDirectionX = line.end().x() - line.origin().x();
        double lineDirectionY = line.end().y() - line.origin().y();
        double lineDirectionZ = line.end().z() - line.origin().z();

        double centerToP1X = circle.center.x - line.origin().x();
        double centerToP1Y = circle.center.y - line.origin().y();
        double centerToP1Z = -line.origin().z();

        double crossProductX = lineDirectionY * centerToP1Z - lineDirectionZ * centerToP1Y;
        double crossProductY = lineDirectionZ * centerToP1X - lineDirectionX * centerToP1Z;
        double crossProductZ = lineDirectionX * centerToP1Y - lineDirectionY * centerToP1X;

        double crossMagnitude = Math.sqrt(crossProductX * crossProductX + crossProductY * crossProductY + crossProductZ * crossProductZ);
        double lineDirectionMagnitude = Math.sqrt(lineDirectionX * lineDirectionX + lineDirectionY * lineDirectionY + lineDirectionZ * lineDirectionZ);

        return crossMagnitude / lineDirectionMagnitude;
    }
    
    public static void main(String[] args) {
        List<Point> points = new ArrayList<>();
        points.add(new Point(1, 2));
        points.add(new Point(3, 4));
        points.add(new Point(5, 6));
        points.add(new Point(7, 8));
        points.add(new Point(9, 10));
        points.add(new Point(11, 12));
        points.add(new Point(13, 14));
        points.add(new Point(15, 16));
        points.add(new Point(17, 18));
        points.add(new Point(19, 20));
        points.add(new Point(21, 22));
        points.add(new Point(23, 24));
        points.add(new Point(25, 26));
        points.add(new Point(27, 28));
        points.add(new Point(29, 30));
        points.add(new Point(31, 32));
        points.add(new Point(33, 34));
        points.add(new Point(35, 36));
        points.add(new Point(37, 38));
        points.add(new Point(39, 40));

        
    }
}

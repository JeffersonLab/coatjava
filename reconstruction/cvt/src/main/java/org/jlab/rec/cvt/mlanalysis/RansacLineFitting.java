/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.mlanalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 *
 * @author veronique
 */

class Pointl {
    double x, y;
    Pointl(double x, double y) {
        this.x = x;
        this.y = y;
    }
}

class Line {
    double slope, intercept;

    Line(double slope, double intercept) {
        this.slope = slope;
        this.intercept = intercept;
    }

    boolean contains(Pointl point, double tolerance) {
        double expectedY = slope * point.x + intercept;
        double distance = Math.abs(expectedY - point.y);
        return distance <= tolerance;
    }
}

public class RansacLineFitting {

    public static Line fitLineRANSAC(List<Pointl> points, int numIterations, double tolerance) {
        Line bestLine = null;
        int bestInliers = 0;

        Random random = new Random();
        if(points.size()<3) return null;
        
        for (int i = 0; i < numIterations; i++) {
            
            Pointl p1 = points.get(random.nextInt(points.size()));
            Pointl p2 = points.get(random.nextInt(points.size()));

            double slope = (p2.y - p1.y) / (p2.x - p1.x);
            double intercept = p1.y - slope * p1.x;

            Line currentLine = new Line(slope, intercept);
            int currentInliers = 0;

            for (Pointl point : points) {
                if (currentLine.contains(point, tolerance)) {
                    currentInliers++;
                }
            }

            if (currentInliers > bestInliers) {
                bestLine = currentLine;
                bestInliers = currentInliers;
            }
        }

        return bestLine;
    }

    public static void main(String[] args) {
        List<Pointl> points = new ArrayList<>();
        points.add(new Pointl(1, 2));
        points.add(new Pointl(2, 3));
        points.add(new Pointl(3, 4));
        points.add(new Pointl(4, 5));
        points.add(new Pointl(5, 6));
        points.add(new Pointl(6, 7));
        points.add(new Pointl(7, 8));
        points.add(new Pointl(8, 9));
        points.add(new Pointl(9, 10));
        points.add(new Pointl(10, 11));
        points.add(new Pointl(11, 12));
        points.add(new Pointl(12, 13));
        points.add(new Pointl(13, 14));
        points.add(new Pointl(14, 15));

        int numIterations = 1000;
        double tolerance = 0.1; // Adjust this tolerance as needed

        Line bestLine = fitLineRANSAC(points, numIterations, tolerance);

        if (bestLine != null) {
            System.out.println("Best Fitted Line:");
            System.out.println("Slope: " + bestLine.slope);
            System.out.println("Intercept: " + bestLine.intercept);

            System.out.println("Points within the line:");
            for (Pointl point : points) {
                if (bestLine.contains(point, tolerance)) {
                    System.out.println("(" + point.x + ", " + point.y + ")");
                }
            }
        } else {
            System.out.println("No line found.");
        }
    }
}

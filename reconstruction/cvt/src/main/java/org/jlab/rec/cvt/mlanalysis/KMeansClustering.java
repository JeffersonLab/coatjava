/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.mlanalysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
/**
 *
 * @author ziegler
 */
import java.util.List;
import org.jlab.io.base.DataBank;

class DataObject {
   double t;
   int l;
   int id;
   
   DataObject(int id, int l, double t) {
       this.t = t;
       this.l = l;
       this.id = id;
   }
}

class Cluster {
   List<DataObject> objects;
   DataObject centroid;

   Cluster(DataObject initialCentroid) {
       this.centroid = initialCentroid;
       this.objects = new ArrayList<>();
   }

   void clearObjects() {
       objects.clear();
   }

   void calculateCentroid() {
       if (objects.isEmpty()) {
           return;
       }

       double sumT = 0.0;
       for (DataObject obj : objects) {
           sumT += obj.t;
       }
       centroid = new DataObject(0,0,sumT / objects.size());
   }
}

public class KMeansClustering {

   public static List<DataObject> getDatahits(DataBank bank) {
        List<DataObject> dataPoints = new ArrayList<>();
        for(int i =0; i<bank.rows(); i++) { 
            float t = bank.getFloat("time", i);
            int l = bank.getInt("layer", i);
            int id = bank.getInt("ID", i);
            dataPoints.add(new DataObject(id,l,t));
        }
       return dataPoints;
   }
   public static void main(String[] args) {
       int numDataPoints = 100; // Number of data points
       int maxIterations = 100; // Maximum number of iterations

       List<DataObject> dataPoints = generateData(numDataPoints);

       int optimalNumClusters = determineOptimalNumClusters(dataPoints);
       System.out.println("Optimal number of clusters: " + optimalNumClusters);

       List<Cluster> clusters = performKMeans(dataPoints, optimalNumClusters, maxIterations);

       for (int i = 0; i < clusters.size(); i++) {
           Cluster cluster = clusters.get(i);
           System.out.println("Cluster " + (i + 1) + " (Centroid t: " + cluster.centroid.t + ")");
           System.out.println("Elements:");
           for (DataObject obj : cluster.objects) {
               System.out.println("id " + obj.id + " layer " + obj.l + "  t: " + obj.t);
           }
       }
   }

   private static List<DataObject> generateData(int numDataPoints) {
       List<DataObject> dataPoints = new ArrayList<>();
       Random random = new Random();

       for (int i = 0; i < numDataPoints; i++) {
           double t = random.nextDouble() * 100; // Generating random t values
           int l = (int) (random.nextDouble() * 5) + 1;
           dataPoints.add(new DataObject(i+1,l, t));
       }

       return dataPoints;
   }

   public static int determineOptimalNumClusters(List<DataObject> dataPoints) {
       int maxClusters = Math.min(dataPoints.size(), 10); // Maximum number of clusters to consider

       double[] silhouetteScores = new double[maxClusters];

       for (int k = 2; k <= maxClusters; k++) {
           List<Cluster> clusters = performKMeans(dataPoints, k, 10); // Use a small number of iterations
           silhouetteScores[k - 2] = calculateSilhouetteScore(clusters);
       }

       int optimalNumClusters = 2;
       double maxSilhouetteScore = silhouetteScores[0];
       for (int k = 2; k <= maxClusters; k++) {
           if (silhouetteScores[k - 2] > maxSilhouetteScore) {
               optimalNumClusters = k;
               maxSilhouetteScore = silhouetteScores[k - 2];
           }
       }

       return optimalNumClusters;
   }

   
    public static List<Cluster> performKMeans(List<DataObject> dataPoints, int k, int maxIterations) {
       List<Cluster> clusters = initializeClusters(dataPoints, k);
       Map<DataObject, Cluster> assignment = new HashMap<>();

       for (int iteration = 0; iteration < maxIterations; iteration++) {
           assignDataPointsToClusters(dataPoints, clusters, assignment);
           recomputeCentroids(clusters);

           // Check for convergence
           boolean converged = true;
           for (Cluster cluster : clusters) {
               if (!cluster.centroid.equals(cluster.centroid)) {
                   converged = false;
                   break;
               }
           }
           if (converged) {
               break;
           }
       }

       return clusters;
   }
   private static List<Cluster> initializeClusters(List<DataObject> dataPoints, int k) {
       List<Cluster> clusters = new ArrayList<>();
       Random random = new Random();

       for (int i = 0; i < k; i++) {
           DataObject initialCentroid = dataPoints.get(random.nextInt(dataPoints.size()));
           clusters.add(new Cluster(initialCentroid));
       }

       return clusters;
   }

   private static void assignDataPointsToClusters(List<DataObject> dataPoints, List<Cluster> clusters, Map<DataObject, Cluster> assignment) {
       for (DataObject obj : dataPoints) {
           double minDistance = Double.MAX_VALUE;
           Cluster closestCluster = null;

           for (Cluster cluster : clusters) {
               double distance = Math.abs(cluster.centroid.t - obj.t);

               if (distance < minDistance) {
                   minDistance = distance;
                   closestCluster = cluster;
               }
           }

           assignment.put(obj, closestCluster);
       }

       for (Cluster cluster : clusters) {
           cluster.clearObjects();
       }

       for (Map.Entry<DataObject, Cluster> entry : assignment.entrySet()) {
           entry.getValue().objects.add(entry.getKey());
       }
   }

   private static void recomputeCentroids(List<Cluster> clusters) {
       for (Cluster cluster : clusters) {
           cluster.calculateCentroid();
       }
   }
   private static double calculateSilhouetteScore(List<Cluster> clusters) {
       //Silhouette score:
       double totalSilhouetteScore = 0.0;
       int numClusters = clusters.size();

       for (Cluster cluster : clusters) {
           for (DataObject point : cluster.objects) {
               double a = calculateAverageDistance(point, cluster.objects);

               double b = Double.MAX_VALUE;
               for (Cluster otherCluster : clusters) {
                   if (otherCluster != cluster) {
                       double avgDistanceToOtherCluster = calculateAverageDistance(point, otherCluster.objects);
                       b = Math.min(b, avgDistanceToOtherCluster);
                   }
               }

               double silhouetteCoefficient = (b - a) / Math.max(a, b);
               totalSilhouetteScore += silhouetteCoefficient;
           }
       }

       return totalSilhouetteScore / (numClusters * clusters.get(0).objects.size());
   }
   private static double calculateDistance(DataObject p1, DataObject p2) {
       // Calculate the distance between two data points
       return Math.abs(p1.t - p2.t);
   }
   private static double calculateAverageDistance(DataObject point, List<DataObject> cluster) {
       // Calculate the average distance of a data point to other data points in a cluster
       double totalDistance = 0.0;
       for (DataObject obj : cluster) {
           totalDistance += calculateDistance(point, obj);
       }
       return totalDistance / cluster.size();
   }

}
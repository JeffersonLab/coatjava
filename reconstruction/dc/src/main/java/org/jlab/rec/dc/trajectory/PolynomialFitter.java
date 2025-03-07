/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.dc.trajectory;

/**
 *
 * @author ziegler
 */
public class PolynomialFitter {

    public static void main(String[] args) {
        // Example data points (x, y)
        double[] x = {1, 2, 3, 4, 5};
        double[] y = {2.5, 3.1, 4.8, 6.2, 8.9};

        // Fit a second-order polynomial (ax^2 + bx + c)
        double[] coefficients = fitSecondOrderPolynomial(x, y);

        // Print the coefficients a, b, c of the polynomial
        System.out.println("Fitted Polynomial: y = " + coefficients[0] + "x^2 + " + coefficients[1] + "x + " + coefficients[2]);
    }

    public static double[] fitSecondOrderPolynomial(double[] x, double[] y) {
        int n = x.length;

        // Step 1: Create the normal equation matrix (X'X) and vector (X'y)
        double[][] X = new double[n][3];
        double[] XtY = new double[3];

        // Fill matrix X and vector XtY
        for (int i = 0; i < n; i++) {
            X[i][0] = x[i] * x[i]; // x^2
            X[i][1] = x[i];        // x
            X[i][2] = 1;           // constant term
            XtY[0] += X[i][0] * y[i];
            XtY[1] += X[i][1] * y[i];
            XtY[2] += y[i];
        }

        // Step 2: Solve the normal equations X'X * coeff = X'y using Gaussian elimination
        double[][] XtX = new double[3][3];

        // Compute XtX = X'X
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                XtX[i][j] = 0;
                for (int k = 0; k < n; k++) {
                    XtX[i][j] += X[k][i] * X[k][j];
                }
            }
        }

        // Solve the system of equations XtX * coeff = XtY using Gaussian elimination
        return solveGaussian(XtX, XtY);
    }

    public static double[] solveGaussian(double[][] matrix, double[] vector) {
        int n = matrix.length;
        double[] result = new double[n];
        // Forward elimination
        for (int i = 0; i < n; i++) {
            // Find the row with the maximum element in column i
            int maxRow = i;
            for (int j = i + 1; j < n; j++) {
                if (Math.abs(matrix[j][i]) > Math.abs(matrix[maxRow][i])) {
                    maxRow = j;
                }
            }

            // Swap rows
            double[] temp = matrix[i];
            matrix[i] = matrix[maxRow];
            matrix[maxRow] = temp;

            double tempValue = vector[i];
            vector[i] = vector[maxRow];
            vector[maxRow] = tempValue;

            // Eliminate column i
            for (int j = i + 1; j < n; j++) {
                double factor = matrix[j][i] / matrix[i][i];
                for (int k = i; k < n; k++) {
                    matrix[j][k] -= factor * matrix[i][k];
                }
                vector[j] -= factor * vector[i];
            }
        }

        // Back substitution
        for (int i = n - 1; i >= 0; i--) {
            result[i] = vector[i] / matrix[i][i];
            for (int j = i - 1; j >= 0; j--) {
                vector[j] -= matrix[j][i] * result[i];
            }
        }
        return result;
    }
}

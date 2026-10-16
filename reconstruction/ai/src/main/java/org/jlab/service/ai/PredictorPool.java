package org.jlab.service.ai;

import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.ZooModel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PredictorPool <T,U> {
    
        final BlockingQueue<Predictor<T,U>> pool;

        public PredictorPool(int size, ZooModel<T,U> model) {
            pool = new ArrayBlockingQueue<>(size);
            for (int i=0; i<size; i++) {
                try {
                    pool.add(model.newPredictor());
                } catch (Exception e) {
                    Logger.getLogger(PredictorPool.class.getName()).log(Level.WARNING, "Failed to create predictor", e);
                }
            }
        }

        public Predictor<T,U> take() throws InterruptedException {
            return pool.take();
        }

        public void put(Predictor<T,U> p) throws InterruptedException {
            if (p!=null) pool.put(p);
        }

        public void shutdownAll() {
            for (Predictor p: pool) {
                try { p.close(); }
                catch (Exception ignored) {}
            }
        }
}

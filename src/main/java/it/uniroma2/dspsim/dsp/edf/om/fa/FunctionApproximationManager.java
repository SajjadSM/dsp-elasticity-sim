package it.uniroma2.dspsim.dsp.edf.om.fa;

import it.uniroma2.dspsim.dsp.edf.om.RewardBasedOM;
import it.uniroma2.dspsim.dsp.edf.om.fa.features.Feature;
import it.uniroma2.dspsim.dsp.edf.om.rl.Action;
import it.uniroma2.dspsim.dsp.edf.om.rl.states.State;

import java.util.ArrayList;
import java.util.List;

public class FunctionApproximationManager {

    private List<Feature> features;

    public FunctionApproximationManager() {
        this.features = new ArrayList<>();
    }

    public double evaluateQ(State state, Action action, RewardBasedOM om) {
        double q = 0.0;
        for (Feature f : this.features) {
            q += f.evaluate(state, action, om);
        }
        return q;
    }

    public void addFeature(Feature feature) {
        this.features.add(feature);
    }

    public void removeFeature(Feature feature) {
        this.features.remove(feature);
    }

    public Feature removeFeature(int position) {
        return this.features.remove(position);
    }

    public List<Feature> getFeatures() {
        return features;
    }
}
package it.uniroma2.dspsim.dsp.edf.om;

import it.uniroma2.dspsim.dsp.Operator;
import it.uniroma2.dspsim.dsp.edf.om.rl.Action;
import it.uniroma2.dspsim.dsp.edf.om.rl.action_selection.ActionSelectionPolicy;
import it.uniroma2.dspsim.dsp.edf.om.rl.action_selection.ActionSelectionPolicyType;
import it.uniroma2.dspsim.dsp.edf.om.rl.action_selection.factory.ActionSelectionPolicyFactory;
import it.uniroma2.dspsim.dsp.edf.om.rl.states.State;
import it.uniroma2.dspsim.dsp.edf.om.rl.states.factory.StateFactory;
import it.uniroma2.dspsim.dsp.edf.om.rl.utils.ActionIterator;
import it.uniroma2.dspsim.dsp.edf.om.rl.utils.StateUtils;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.activations.impl.ActivationReLU;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.Arrays;

/**
 * Deep Q Learning variant.
 * This OM uses V function as objective function.
 * It computes s' (post-decision state) state from s (current state) and a (valid action) for each valid action a.
 * Then computes V(s') through its neural network and add c(a) (action cost) to V(s') in order to obtain Q(s,a)
 * of current state and action couple.
 * if action.getDelta() != 0 c(a) = reconfiguration's weight else c(a) = 0
 * To chose best action it selects min Q(s,a) for each a
 * In learning step phase it subtracts c(a) from Q(s,a) to obtain V(s) as label to train the neural network
 */
public class DeepVLearningOM extends DeepLearningOM {

    // this asp is used to select action in learning step
    private ActionSelectionPolicy greedyASP;

    public DeepVLearningOM(Operator operator) {
        super(operator);

        this.greedyASP = ActionSelectionPolicyFactory.getPolicy(ActionSelectionPolicyType.GREEDY, this);
    }

    @Override
    protected void learningStep(State oldState, Action action, State currentState, double reward) {
        // get post decision state from old state and action
        State pdState = StateUtils.computePostDecisionState(oldState, action, this);

        // unknown cost
        double cU = reward - computeActionCost(action) - StateUtils.computeDeploymentCostNormalized(pdState, this);
        Action greedyAction = this.greedyASP.selectAction(currentState);
        double newQ = getQ(currentState, greedyAction).getDouble(0);
        newQ = gamma * newQ + cU;

        INDArray v = getV(pdState);
        v.put(0, 0, newQ);

        // get post decision input array
        INDArray trainingInput = buildInput(pdState);

        // training step
        this.learn(trainingInput, v);

        // decrement gamma if necessary
        decrementGamma();
    }

    private INDArray getV(State state) {
        INDArray input = buildInput(state);
        return this.network.output(input);
    }

    private INDArray getQ(State state, Action action) {
        State postDecisionState = StateUtils.computePostDecisionState(state, action, this);
        INDArray v = getV(postDecisionState);
        v.put(0, 0, v.getDouble(0) + computeActionCost(action) + StateUtils.computeDeploymentCostNormalized(postDecisionState, this));
        return v;
    }

    private double computeActionCost(Action action) {
        if (action.getDelta() != 0)
            return this.getwReconf();
        else
            return 0;
    }

    private INDArray buildInput(State state) {
        //State indexedState = getIndexedState(state);
        return state.arrayRepresentation(this.stateFeatures);
    }

    /**
     * DEEP LEARNING OM
     */

    @Override
    protected int computeOutputLayerNodesNumber() { return 1; }

    @Override
    protected int computeInputLayerNodesNumber() {
        return this.stateFeatures;
    }


    @Override
    protected MultiLayerConfiguration buildNeuralNetwork() {
        return new NeuralNetConfiguration.Builder()
                .weightInit(WeightInit.XAVIER)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .updater(new Sgd(0.1))
                .list(
                        new DenseLayer.Builder()
                                .nIn(this.inputLayerNodesNumber)
                                .nOut(this.inputLayerNodesNumber * 2)
                                .activation(Activation.RELU)
                                .build(),
                        new DenseLayer.Builder()
                                .nIn(this.inputLayerNodesNumber * 2)
                                .nOut(this.inputLayerNodesNumber / 2)
                                .activation(Activation.RELU)
                                .build(),
                        new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                                .nIn(this.inputLayerNodesNumber / 2)
                                .nOut(this.outputLayerNodesNumber)
                                .activation(Activation.IDENTITY)
                                .build()
                )
                .pretrain(false)
                .backprop(true)
                .build();
    }

    /**
     * ACTION SELECTION POLICY CALLBACK INTERFACE
     */

    @Override
    public double evaluateAction(State s, Action a) {
        // return network Q-function prediction associated to action a in state s
        INDArray networkOutput = getQ(s, a);
        return networkOutput.getDouble(0);
    }
}

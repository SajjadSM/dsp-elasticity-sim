package it.uniroma2.dspsim.dsp.edf.om;

import it.uniroma2.dspsim.Configuration;
import it.uniroma2.dspsim.ConfigurationKeys;
import it.uniroma2.dspsim.dsp.Operator;
import it.uniroma2.dspsim.dsp.edf.om.rl.Action;
import it.uniroma2.dspsim.dsp.edf.om.rl.action_selection.ActionSelectionPolicy;
import it.uniroma2.dspsim.dsp.edf.om.rl.action_selection.ActionSelectionPolicyCallback;
import it.uniroma2.dspsim.dsp.edf.om.rl.action_selection.ActionSelectionPolicyType;
import it.uniroma2.dspsim.dsp.edf.om.rl.action_selection.concrete.EpsilonGreedyActionSelectionPolicy;
import it.uniroma2.dspsim.dsp.edf.om.rl.action_selection.factory.ActionSelectionPolicyFactory;
import it.uniroma2.dspsim.dsp.edf.om.rl.states.State;
import it.uniroma2.dspsim.dsp.edf.om.rl.states.factory.StateFactory;
import it.uniroma2.dspsim.dsp.edf.om.rl.utils.ActionIterator;
import it.uniroma2.dspsim.dsp.edf.om.rl.utils.StateIterator;
import it.uniroma2.dspsim.dsp.edf.om.rl.utils.StateUtils;
import it.uniroma2.dspsim.infrastructure.ComputingInfrastructure;
import it.uniroma2.dspsim.stats.Statistics;
import it.uniroma2.dspsim.stats.metrics.RealValuedMetric;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public abstract class BaseTBValueIterationOM extends DynamicProgrammingOM implements ActionSelectionPolicyCallback {

    private static final String STAT_TBVI_TIME = "OM - TBVI Elapsed Time (seconds)";
    private static final String STAT_TBVI_TRAJECTORY_LENGTH = "OM - TBVI Trajectory Length";
    private static final String STAT_TBVI_TRAJECTORIES_COMPUTED = "OM - TBVI Trajectories Computed";
    private static final String STAT_TBVI_SAMPLES_SEEN = "OM - TBVI Samples Seen";

    private Random rng;

    protected int statesCount;
    protected int actionsCount;

    // tbvi parameters
    protected long tbviIterations;
    protected long tbviMillis;
    protected long tbviTrajectoryLength;

    public BaseTBValueIterationOM(Operator operator) {
        super(operator);

        this.actionsCount = computeActionsCount();
        this.statesCount = computeStatesCount();

        Configuration configuration = Configuration.getInstance();

        this.tbviIterations = configuration.getLong(ConfigurationKeys.TBVI_EXEC_ITERATIONS_KEY, 300000L);
        this.tbviMillis = configuration.getLong(ConfigurationKeys.TBVI_EXEC_SECONDS_KEY, 60L) * 1000;
        this.tbviTrajectoryLength = configuration.getLong(ConfigurationKeys.TBVI_TRAJECTORY_LENGTH_KEY, 512L);

        this.rng = new Random();
    }

    protected void tbvi(long iterations, long millis, long trajectoryLength) {
        ActionSelectionPolicy epsGreedyASP = ActionSelectionPolicyFactory.getPolicy(ActionSelectionPolicyType.EPSILON_GREEDY, this);
        ((EpsilonGreedyActionSelectionPolicy) epsGreedyASP).setEpsilon(0.1);
        ((EpsilonGreedyActionSelectionPolicy) epsGreedyASP).setEpsilonDecaySteps(-1);
        // get initial state
        State state = null;
        // current trajectory length
        long tl = 0L;

        // trajectory counter
        long trajectoriesComputed = 0;

        // elapsed millis
        long elapsedMillis = 0L;

        // iteration counter
        long iterationCompleted = 0L;

        while ((iterations <= 0L || iterationCompleted < iterations) && (millis <= 0L || elapsedMillis < millis)) {
            if (iterationCompleted % 1000 == 0)
                System.out.println("TBVI: " + iterationCompleted + " iteration completed");

            long startIteration = System.currentTimeMillis();

            if (trajectoryLength > 0 && tl % trajectoryLength == 0)
                tl = 0L;
            if (tl == 0L) {
                resetTrajectoryData();
                // start new trajectory
                state = randomState();

                trajectoriesComputed++;
            }

            Action action = epsGreedyASP.selectAction(state);


            state = tbviIteration(state, action);

            tl++;

            elapsedMillis += (System.currentTimeMillis() - startIteration);

            iterationCompleted++;
        }

        // store metrics
        storeTBVIExecutionMetrics(elapsedMillis, trajectoryLength, trajectoriesComputed);
    }

    protected State tbviIteration(State s, Action a) {
        double oldQ = computeQ(s, a);
        double newQ = evaluateNewQ(s, a);

        double delta = newQ - oldQ;

        learn(delta, newQ, s, a);

        return sampleNextState(s, a);
    }

    protected int computeStatesCount() {
        StateIterator stateIterator = new StateIterator(getStateRepresentation(), this.operator.getMaxParallelism(),
                ComputingInfrastructure.getInfrastructure(), getInputRateLevels());

        int count = 0;
        while (stateIterator.hasNext()) {
            stateIterator.next();
            count++;
        }
        return count;
    }

    protected int computeActionsCount() {
        ActionIterator actionIterator = new ActionIterator();

        int count = 0;
        while (actionIterator.hasNext()) {
            actionIterator.next();
            count++;
        }
        return count;
    }

    private State randomState() {

        int randomIndex = rng.nextInt(this.statesCount);
        StateIterator stateIterator = new StateIterator(getStateRepresentation(), this.operator.getMaxParallelism(),
                ComputingInfrastructure.getInfrastructure(), getInputRateLevels());

        State s = null;
        while (stateIterator.hasNext()) {
            s = stateIterator.next();
            if (s.getIndex() == randomIndex)
                return s;
        }
        return s;
    }

    private State sampleNextState(State s, Action a) {
        // TODO view sampling
        State pds = StateUtils.computePostDecisionState(s, a, this);
        List<Double> pArray = new ArrayList<>();
        for (int l : this.getpMatrix().getColLabels(s.getLambda())) {
            int times = (int) Math.floor(100 * this.getpMatrix().getValue(s.getLambda(), l));
            times = times > 0 ? times : 1;
            for (int i = 0; i < times; i++) {
                pArray.add((double) l);
            }
        }
        double[] lambdas = new double[pArray.size()];
        for (int i = 0; i < lambdas.length; i++) {
            lambdas[i] = pArray.get(i);
        }
        INDArray lambdaArray = Nd4j.create(lambdas);
        Nd4j.shuffle(lambdaArray, 0);
        int nextLambda = lambdaArray.getInt(new Random().nextInt(lambdaArray.length()));
        return StateFactory.createState(getStateRepresentation(), -1, pds.getActualDeployment(), nextLambda,
                getInputRateLevels() - 1, this.operator.getMaxParallelism());
    }

    private double evaluateNewQ(State s, Action a) {
        double cost = 0.0;
        // compute reconfiguration cost
        if (a.getDelta() != 0)
            cost += this.getwReconf();
        // from s,a compute pds
        State pds = StateUtils.computePostDecisionState(s, a, this);
        // compute deployment cost using pds wighted on wRes
        cost += StateUtils.computeDeploymentCostNormalized(pds, this) * this.getwResources();
        // for each lambda level with p != 0 in s.getLambda() row
        Set<Integer> possibleLambdas = getpMatrix().getColLabels(s.getLambda());
        for (int lambda : possibleLambdas) {
            // change pds.lambda to lambda
            pds.setLambda(lambda);
            // get Q(s, a) using the greedy action selection policy
            // from post decision state with lambda ad pds.lambda
            Action greedyAction = getActionSelectionPolicy().selectAction(pds);
            double q = computeQ(pds, greedyAction);
            // get transition probability from s.lambda to lambda level
            double p = this.getpMatrix().getValue(s.getLambda(), lambda);
            // compute slo violation cost
            double sloCost = StateUtils.computeSLOCost(pds, this) * this.getwSLO();

            cost += p * (sloCost + getGamma() * q);
        }

        return cost;
    }

    @Override
    public boolean validateAction(State s, Action a) {
        return s.validateAction(a);
    }

    /**
     * Store TBVI execution metrics
     */
    private void storeTBVIExecutionMetrics(long elapsedMillis, long trajectoryLength, long trajectoriesComputed) {
        Statistics statistics = Statistics.getInstance();

        statistics.registerMetricIfNotExists(new RealValuedMetric(getOperatorMetricName(STAT_TBVI_TIME)));
        statistics.registerMetricIfNotExists(new RealValuedMetric(getOperatorMetricName(STAT_TBVI_TRAJECTORY_LENGTH)));
        statistics.registerMetricIfNotExists(new RealValuedMetric(getOperatorMetricName(STAT_TBVI_TRAJECTORIES_COMPUTED)));
        statistics.registerMetricIfNotExists(new RealValuedMetric(getOperatorMetricName(STAT_TBVI_SAMPLES_SEEN)));

        statistics.updateMetric(getOperatorMetricName(STAT_TBVI_TIME), (double) elapsedMillis / (double) 1000);
        statistics.updateMetric(getOperatorMetricName(STAT_TBVI_TRAJECTORY_LENGTH), (double) trajectoryLength);
        statistics.updateMetric(getOperatorMetricName(STAT_TBVI_TRAJECTORIES_COMPUTED), (double) trajectoriesComputed);
        statistics.updateMetric(getOperatorMetricName(STAT_TBVI_SAMPLES_SEEN), (double) (trajectoriesComputed * trajectoryLength));

        // print same results
        System.out.println("Elapsed time: " + elapsedMillis / 1000 + " seconds");
        System.out.println("Trajectory length: " + trajectoryLength);
        System.out.println("Trajectories computed: " + trajectoriesComputed);
        System.out.println("Samples: " + trajectoriesComputed * trajectoryLength);
    }


    /**
     * ABSTRACT METHODS
     */

    protected abstract void resetTrajectoryData();
    protected abstract double computeQ(State s, Action a);
    protected abstract void learn(double tbviDelta, double newQ, State state, Action action);
}

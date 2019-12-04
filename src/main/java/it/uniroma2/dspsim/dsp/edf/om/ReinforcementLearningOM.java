package it.uniroma2.dspsim.dsp.edf.om;

import it.uniroma2.dspsim.Configuration;
import it.uniroma2.dspsim.ConfigurationKeys;
import it.uniroma2.dspsim.dsp.Operator;
import it.uniroma2.dspsim.dsp.Reconfiguration;
import it.uniroma2.dspsim.dsp.edf.om.rl.Action;
import it.uniroma2.dspsim.dsp.edf.om.rl.action_selection.ActionSelectionPolicy;
import it.uniroma2.dspsim.dsp.edf.om.rl.action_selection.ActionSelectionPolicyCallback;
import it.uniroma2.dspsim.dsp.edf.om.rl.action_selection.factory.ActionSelectionPolicyFactory;
import it.uniroma2.dspsim.dsp.edf.om.rl.action_selection.ActionSelectionPolicyType;
import it.uniroma2.dspsim.dsp.edf.om.rl.states.State;
import it.uniroma2.dspsim.dsp.edf.om.rl.states.StateType;
import it.uniroma2.dspsim.dsp.edf.om.rl.states.factory.StateFactory;
import it.uniroma2.dspsim.infrastructure.ComputingInfrastructure;
import it.uniroma2.dspsim.infrastructure.NodeType;
import it.uniroma2.dspsim.stats.metrics.CountMetric;
import it.uniroma2.dspsim.stats.metrics.IncrementalMeanMetric;
import it.uniroma2.dspsim.stats.metrics.MeanMetric;
import it.uniroma2.dspsim.stats.metrics.RealValuedCountMetric;
import it.uniroma2.dspsim.stats.Statistics;
import it.uniroma2.dspsim.stats.samplers.StepSampler;
import it.uniroma2.dspsim.stats.samplers.TimeLogSampler;
import it.uniroma2.dspsim.utils.MathUtils;

public abstract class ReinforcementLearningOM extends RewardBasedOM implements ActionSelectionPolicyCallback {

    protected ReinforcementLearningOM(Operator operator) {
        super(operator);
    }

    @Override
    public Reconfiguration pickReconfiguration(OMMonitoringInfo monitoringInfo) {
        // compute new state
        State currentState = computeNewState(monitoringInfo);

        // learning step
        if (lastChosenAction != null) {
            // compute reconfiguration's cost and use it as reward
            double reward = computeCost(lastChosenAction, currentState, monitoringInfo.getInputRate());
            // learning step
            learningStep(lastState, lastChosenAction, currentState, reward);
            // update mean reward statistic
            Statistics.getInstance().updateMetric(getOperatorMetricName(STAT_GET_REWARD_COUNTER), 1);
            Statistics.getInstance().updateMetric(getOperatorMetricName(STAT_REWARD_SUM), reward);
            Statistics.getInstance().updateMetric(STAT_GET_REWARD_COUNTER, 1);
            Statistics.getInstance().updateMetric(STAT_REWARD_SUM, reward);
            Statistics.getInstance().updateMetric(getOperatorMetricName(STAT_REWARD_INCREMENTAL_MEAN), reward);
            Statistics.getInstance().updateMetric(STAT_REWARD_INCREMENTAL_MEAN, reward);
        }

        // pick new action
        lastChosenAction = this.getActionSelectionPolicy().selectAction(currentState);

        // update state
        lastState = currentState;

        // construct reconfiguration from action
        return action2reconfiguration(lastChosenAction);
    }

    @Override
    protected ActionSelectionPolicy initActionSelectionPolicy() {
        // action selection policy
        ActionSelectionPolicyType aspType = ActionSelectionPolicyType.fromString(
        Configuration.getInstance().getString(ConfigurationKeys.ASP_TYPE_KEY, "e-greedy"));
        return  ActionSelectionPolicyFactory.getPolicy(aspType, this);
    }

    /**
     * ACTION SELECTION POLICY CALLBACK INTERFACE
     */

    @Override
    public boolean validateAction(State s, Action a) {
        return s.validateAction(a);
    }

    /**
     * ABSTRACT METHODS
     */
    protected abstract void learningStep(State oldState, Action action, State currentState, double reward);
}

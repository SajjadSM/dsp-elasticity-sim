package it.uniroma2.dspsim.dsp.edf.om;

import it.uniroma2.dspsim.dsp.Operator;
import it.uniroma2.dspsim.dsp.Reconfiguration;
import it.uniroma2.dspsim.dsp.edf.om.request.BasicOMRequest;
import it.uniroma2.dspsim.dsp.edf.om.request.OMRequest;
import it.uniroma2.dspsim.dsp.edf.om.rl.Action;

import java.util.*;

public class EnsembleLearningOM extends OperatorManager {

	OperatorManager qLearning;
	OperatorManager qLearningPDS;
	OperatorManager modelBased;
	int numberOfReconfigs = 0;

	public EnsembleLearningOM(Operator operator) {
		super(operator);

		qLearning = new QLearningOM(operator);
		qLearningPDS = new QLearningPDSOM(operator);
		modelBased = new ModelBasedRLOM(operator);
	}

	@Override
	public OMRequest pickReconfigurationRequest(OMMonitoringInfo monitoringInfo) {

		Action qAction = qLearning.chooseAction(monitoringInfo);
		Action pdsAction = qLearningPDS.chooseAction(monitoringInfo);
		Action modelBasedAction = modelBased.chooseAction(monitoringInfo);

		Action[] actions = {
				modelBasedAction,
				pdsAction,
				qAction,
		};

		int[] actionsDelta = {
				modelBasedAction.getDelta(),
				pdsAction.getDelta(),
				qAction.getDelta(),
		};

		int majority = findMajority(actionsDelta);

		Action chosenAction = modelBasedAction;
		for (int i = 0; i < actions.length; i++) {
			if (actionsDelta[i] == majority) {
				chosenAction = actions[i];
				break;
			}
		}


		Reconfiguration qReq = qLearning.pickReconfigurationRequest(monitoringInfo, chosenAction).getRequestedReconfiguration();
		Reconfiguration faqReq = qLearningPDS.pickReconfigurationRequest(monitoringInfo, chosenAction).getRequestedReconfiguration();
		Reconfiguration modelBasedReq = modelBased.pickReconfigurationRequest(monitoringInfo, chosenAction).getRequestedReconfiguration();

		return new BasicOMRequest(qReq);

	}

	// @Override
	// public OMRequest pickReconfigurationRequest(OMMonitoringInfo monitoringInfo)
	// {

	// Reconfiguration qReq =
	// qLearning.pickReconfigurationRequest(monitoringInfo).getRequestedReconfiguration();
	// Reconfiguration faqReq =
	// faqLearning.pickReconfigurationRequest(monitoringInfo).getRequestedReconfiguration();
	// Reconfiguration modelBasedReq =
	// modelBased.pickReconfigurationRequest(monitoringInfo).getRequestedReconfiguration();

	// Reconfiguration[] reConfigs = {
	// qReq,
	// faqReq,
	// modelBasedReq
	// };

	// int[] directions = {
	// qReq.getScalingDirection(),
	// faqReq.getScalingDirection(),
	// modelBasedReq.getScalingDirection()
	// };

	// int majority = findMajority(directions);

	// Reconfiguration ChosenConfig = faqReq;

	// for (int i = 0; i < directions.length; i++) {
	// if (directions[i] == majority) {
	// ChosenConfig = reConfigs[i];
	// break;
	// }
	// }

	// return new BasicOMRequest(reConfigs[majority]);

	// }

	public static int findMajority(int[] nums) {
		int count = 0, candidate = -1;

		// Finding majority candidate
		for (int index = 0; index < nums.length; index++) {
			if (count == 0) {
				candidate = nums[index];
				count = 1;
			} else {
				if (nums[index] == candidate)
					count++;
				else
					count--;
			}
		}
		count = 0;
		for (int index = 0; index < nums.length; index++) {
			if (nums[index] == candidate)
				count++;
		}
		if (count > (nums.length / 2))
			return candidate;
		return nums[0];

	}
}

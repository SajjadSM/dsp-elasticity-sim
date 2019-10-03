package it.uniroma2.dspsim.dsp.edf;

import it.uniroma2.dspsim.dsp.Operator;
import it.uniroma2.dspsim.dsp.Reconfiguration;

public class DoNothingOM extends OperatorManager {

	public DoNothingOM(Operator operator) {
		super(operator);
	}

	@Override
	public Reconfiguration pickReconfiguration(OMMonitoringInfo monitoringInfo) {
		return Reconfiguration.doNothing();
	}
}

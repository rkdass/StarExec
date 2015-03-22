package org.starexec.data.to.compare;

import java.util.Comparator;

import org.starexec.data.to.Benchmark;
import org.starexec.data.to.pipelines.JoblineStage;

/**
 * A comparator for jobline stages that will arrange stages by increasing stage number
 * @author Eric
 *
 */

public class JoblineStageComparator implements Comparator<JoblineStage> {

	@Override
	public int compare(JoblineStage arg0, JoblineStage arg1) {
		return arg0.getStageNumber().compareTo(arg1.getStageNumber());
	}

}